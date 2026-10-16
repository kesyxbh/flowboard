package com.flowboard.ai;

import com.flowboard.service.BoardService;
import com.flowboard.web.dto.BoardDetailResponse;
import com.flowboard.web.dto.ColumnResponse;
import com.flowboard.web.dto.StandupRequest;
import com.flowboard.web.dto.StandupResponse;
import com.flowboard.web.dto.StandupResponse.Stats;
import com.flowboard.web.dto.StandupResponse.TaskRef;
import com.flowboard.web.dto.TaskResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Stand-up summary: a short briefing of the board for a team meeting.
 *
 * <p>The numbers (how many cards, which are overdue, which are due soon) are computed here in plain Java and
 * returned next to the text. The model is only asked to phrase them: language models are good at wording and
 * unreliable at date arithmetic, so the facts never depend on the model.
 */
@Service
public class StandupService {

    private static final Logger log = LoggerFactory.getLogger(StandupService.class);

    /** A card due within this many days (including today) is reported as "due soon". */
    static final int DUE_SOON_DAYS = 3;

    // A column whose name contains one of these words holds finished work. This is a heuristic: the board has no
    // explicit "done" flag on columns, so the column's own name is the only signal. The English words must stand
    // alone ("Done", "Done!", "Done - April" match, "Undone" does not).
    private static final Pattern DONE_COLUMN = Pattern.compile(
            "(^|[^\\p{L}])(done|complete|completed|finished|closed|shipped|released)([^\\p{L}]|$)",
            Pattern.CASE_INSENSITIVE);

    // The Chinese word for "completed" (two characters: U+5B8C U+6210), built from code points so that this source
    // file stays pure ASCII. It is matched anywhere in the name because Chinese has no spaces between words
    // ("already completed" is one run of characters).
    private static final String CHINESE_DONE = new String(new int[] {0x5B8C, 0x6210}, 0, 2);

    private final ChatClient chatClient;
    private final AiAvailability availability;
    private final AiTime time;
    private final BoardService boardService;

    public StandupService(ChatClient.Builder chatClientBuilder, AiAvailability availability, AiTime time,
                          BoardService boardService) {
        this.chatClient = chatClientBuilder.build();
        this.availability = availability;
        this.time = time;
        this.boardService = boardService;
    }

    public StandupResponse standup(Long userId, Long boardId, StandupRequest request) {
        availability.requireConfigured();
        // Throws NotFoundException (404) when the board does not exist or belongs to someone else.
        BoardDetailResponse board = boardService.getBoard(userId, boardId);
        String timeZone = request == null ? null : request.timeZone();
        LocalDate today = time.today(timeZone);

        Stats stats = computeStats(board, today);
        String summary;
        try {
            summary = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(describe(board, stats, today))
                    .call()
                    .content();
        } catch (RuntimeException e) {
            log.error("LLM request failed while summarising board {}", boardId, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI provider request failed. Check the API key, base URL and model name.");
        }
        if (summary == null || summary.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The model returned an empty summary. Please try again.");
        }
        return new StandupResponse(summary.trim(), stats);
    }

    static boolean isDoneColumn(String columnName) {
        return columnName != null
                && (DONE_COLUMN.matcher(columnName).find() || columnName.contains(CHINESE_DONE));
    }

    /** The facts of the briefing. Package-private so it can be tested with plain objects, without a model. */
    static Stats computeStats(BoardDetailResponse board, LocalDate today) {
        int total = 0;
        int done = 0;
        List<TaskRef> overdue = new ArrayList<>();
        List<TaskRef> dueSoon = new ArrayList<>();
        LocalDate soonLimit = today.plusDays(DUE_SOON_DAYS);

        for (ColumnResponse column : board.columns()) {
            boolean finished = isDoneColumn(column.name());
            for (TaskResponse task : column.tasks()) {
                total++;
                if (finished) {
                    done++;
                    continue;
                }
                LocalDate due = task.dueDate();
                if (due == null) {
                    continue;
                }
                TaskRef ref = new TaskRef(task.id(), task.title(), due, column.name());
                if (due.isBefore(today)) {
                    overdue.add(ref);
                } else if (!due.isAfter(soonLimit)) {
                    dueSoon.add(ref);
                }
            }
        }
        Comparator<TaskRef> byDate = Comparator.comparing(TaskRef::dueDate).thenComparing(TaskRef::id);
        overdue.sort(byDate);
        dueSoon.sort(byDate);
        return new Stats(total, done, List.copyOf(overdue), List.copyOf(dueSoon));
    }

    private static String describe(BoardDetailResponse board, Stats stats, LocalDate today) {
        StringBuilder text = new StringBuilder();
        text.append("Board: ").append(board.name()).append('\n');
        text.append("Today: ").append(today).append(" (").append(AiTime.weekday(today)).append(")\n\n");
        text.append("Columns and cards:\n");
        for (ColumnResponse column : board.columns()) {
            text.append("- ").append(column.name()).append(isDoneColumn(column.name()) ? " [finished work]" : "")
                    .append(": ").append(column.tasks().size()).append(" cards\n");
            for (TaskResponse task : column.tasks()) {
                text.append("    * [").append(task.priority()).append("] ").append(task.title());
                if (task.dueDate() != null) {
                    text.append(" (due ").append(task.dueDate()).append(')');
                }
                text.append('\n');
            }
        }
        text.append("\nCards overdue: ").append(listOf(stats.overdue())).append('\n');
        text.append("Cards due within ").append(DUE_SOON_DAYS).append(" days: ").append(listOf(stats.dueSoon()));
        return text.toString();
    }

    private static String listOf(List<TaskRef> refs) {
        if (refs.isEmpty()) {
            return "none";
        }
        List<String> parts = new ArrayList<>();
        for (TaskRef ref : refs) {
            parts.add(ref.title() + " (due " + ref.dueDate() + ", in " + ref.columnName() + ")");
        }
        return String.join("; ", parts);
    }

    private static final String SYSTEM_PROMPT = """
            You write a short stand-up briefing for a team lead, from the board data you are given.

            Use exactly three short sections with these titles, in this order:
            Done
            In progress
            Risks

            Rules:
            - Use only the data you are given. Do not invent cards, people or dates.
            - The first column of the board is the backlog: its cards have not started.
            - Done: what is in the columns marked [finished work]; say "Nothing yet" when there is none.
            - In progress: the cards in the columns that are neither the first column nor marked
              [finished work], high priority first; say "Nothing yet" when there is none.
            - Risks: the overdue cards, the cards due soon, and any high priority card that has not started
              (a high priority card in the first column). Say "No risks" when there are none.
            - Plain text, no markdown symbols, at most 120 words.
            - Write in the language most card titles are written in; use English when unsure.
            """;
}
