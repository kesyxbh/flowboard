package com.flowboard.ai;

import com.flowboard.domain.Priority;
import com.flowboard.service.BoardService;
import com.flowboard.service.TaskService;
import com.flowboard.web.dto.BoardDetailResponse;
import com.flowboard.web.dto.ColumnResponse;
import com.flowboard.web.dto.CreateTaskRequest;
import com.flowboard.web.dto.QuickAddRequest;
import com.flowboard.web.dto.QuickAddResponse;
import com.flowboard.web.dto.TaskResponse;
import com.flowboard.web.error.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Quick add: turns one sentence ("fix the login bug by Friday, high priority, in Doing") into a card.
 *
 * <p>The model's only job is to fill a {@link TaskDraft}. It never creates anything itself: the draft is
 * validated and resolved against the real board in {@link #resolve}, and the card is then created through the
 * ordinary {@link TaskService}. Anything the model got wrong (an unknown column, an unreadable date) is dropped
 * or replaced by a default and reported back as a warning.
 */
@Service
public class TaskDraftService {

    private static final Logger log = LoggerFactory.getLogger(TaskDraftService.class);

    private final ChatClient chatClient;
    private final AiAvailability availability;
    private final AiTime time;
    private final BoardService boardService;
    private final TaskService taskService;

    public TaskDraftService(ChatClient.Builder chatClientBuilder, AiAvailability availability, AiTime time,
                            BoardService boardService, TaskService taskService) {
        this.chatClient = chatClientBuilder.build();
        this.availability = availability;
        this.time = time;
        this.boardService = boardService;
        this.taskService = taskService;
    }

    public QuickAddResponse quickAdd(Long userId, QuickAddRequest request) {
        availability.requireConfigured();
        // Throws NotFoundException (404) when the board does not exist or belongs to someone else.
        BoardDetailResponse board = boardService.getBoard(userId, request.boardId());
        if (board.columns().isEmpty()) {
            throw new BadRequestException("This board has no columns yet. Add a column first.");
        }

        TaskDraft raw;
        try {
            raw = chatClient.prompt()
                    .system(systemPrompt(board, time.today(request.timeZone()), time.zone(request.timeZone())))
                    .user(request.text())
                    .call()
                    .entity(TaskDraft.class);
        } catch (RuntimeException e) {
            log.error("LLM request failed while parsing a quick-add sentence", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI provider request failed. Check the API key, base URL and model name.");
        }

        Resolved resolved = resolve(raw, board);
        TaskResponse created = taskService.createTask(userId, new CreateTaskRequest(
                resolved.columnId(), resolved.title(), resolved.description(), null,
                resolved.priority(), resolved.dueDate()));
        return new QuickAddResponse(created, resolved.columnName(), explain(resolved), resolved.warnings());
    }

    /** The validated, ready-to-store version of a draft. */
    record Resolved(String title, String description, Priority priority, LocalDate dueDate,
                    Long columnId, String columnName, List<String> warnings) {
    }

    /**
     * Validates a model draft against a board. Public to the package so it can be tested without a model.
     * A missing title is the only fatal problem; everything else degrades to a default plus a warning.
     */
    static Resolved resolve(TaskDraft raw, BoardDetailResponse board) {
        List<String> warnings = new ArrayList<>();

        String title = raw == null || raw.title() == null ? "" : raw.title().trim();
        if (title.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not find a task title in that text. Try rephrasing it.");
        }
        if (title.length() > AiInput.TITLE_MAX) {
            title = AiInput.abbreviate(title, AiInput.TITLE_MAX);
            warnings.add("The title was shortened to " + AiInput.TITLE_MAX + " characters.");
        }

        String description = raw.description() == null || raw.description().isBlank() ? null : raw.description().trim();
        if (description != null && description.length() > AiInput.DESCRIPTION_MAX) {
            description = AiInput.abbreviate(description, AiInput.DESCRIPTION_MAX);
            warnings.add("The description was shortened to " + AiInput.DESCRIPTION_MAX + " characters.");
        }

        Priority priority = Priority.MEDIUM;
        try {
            Priority parsed = AiInput.parsePriority(raw.priority());
            if (parsed != null) {
                priority = parsed;
            }
        } catch (IllegalArgumentException e) {
            warnings.add("The priority \"" + raw.priority() + "\" is not LOW, MEDIUM or HIGH; MEDIUM was used.");
        }

        LocalDate dueDate = null;
        try {
            dueDate = AiInput.parseDate(raw.dueDate());
        } catch (IllegalArgumentException e) {
            warnings.add("The due date \"" + raw.dueDate() + "\" could not be read; the card has no due date.");
        }

        ColumnResponse column = board.columns().get(0);
        if (raw.column() != null && !raw.column().isBlank()) {
            String wanted = raw.column().trim();
            ColumnResponse match = board.columns().stream()
                    .filter(candidate -> candidate.name().trim().equalsIgnoreCase(wanted))
                    .findFirst()
                    .orElse(null);
            if (match != null) {
                column = match;
            } else {
                warnings.add("There is no column called \"" + wanted + "\"; the card was added to \""
                        + column.name() + "\" instead.");
            }
        }
        return new Resolved(title, description, priority, dueDate, column.id(), column.name(), List.copyOf(warnings));
    }

    /** One sentence built from the validated values, so it always matches what was stored. */
    static String explain(Resolved resolved) {
        StringBuilder text = new StringBuilder("Added \"").append(resolved.title()).append("\" to ")
                .append(resolved.columnName()).append(", priority ").append(resolved.priority());
        if (resolved.dueDate() != null) {
            text.append(", due ").append(resolved.dueDate());
        }
        return text.append('.').toString();
    }

    private String systemPrompt(BoardDetailResponse board, LocalDate today, ZoneId zone) {
        String columns = board.columns().stream().map(ColumnResponse::name).collect(Collectors.joining(", "));
        return """
                You turn one sentence into a task card for a kanban board. Respond only with the JSON object.

                Today is %s (%s), time zone %s. Resolve relative dates such as "tomorrow", "next Friday" or
                "end of the month" from this date.
                The board's columns, left to right: [%s].

                Fields (use null for anything the sentence does not say):
                - title: a short imperative title, at most 80 characters, without the date, priority or column words
                - description: extra detail from the sentence, or null
                - priority: one of LOW, MEDIUM, HIGH. "urgent", "asap" and "critical" mean HIGH
                - dueDate: an ISO date yyyy-MM-dd
                - column: the exact name of one of the columns above, only when the user named a column

                Do not invent details that are not in the sentence.
                """.formatted(today, AiTime.weekday(today), zone, columns);
    }
}
