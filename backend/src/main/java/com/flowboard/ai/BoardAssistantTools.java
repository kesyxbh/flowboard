package com.flowboard.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.service.BoardService;
import com.flowboard.service.TaskService;
import com.flowboard.web.dto.BulkCreateTasksRequest;
import com.flowboard.web.dto.CreateTaskRequest;
import com.flowboard.web.dto.NewTaskItem;
import com.flowboard.web.dto.TaskResponse;
import com.flowboard.web.dto.ToolCallRecord;
import com.flowboard.web.error.BadRequestException;
import com.flowboard.web.error.ConflictException;
import com.flowboard.web.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The actions the board assistant may perform. This is a thin adapter over the existing services: every
 * business rule (ownership of boards and columns, ordering of cards) still lives in BoardService and
 * TaskService, so the model can never do anything a normal API client could not.
 *
 * <p>An instance is created for every chat request and is bound to the signed-in user. The user id comes from
 * the server side, never from the model: none of the tools has a userId parameter, so a prompt such as
 * "create this on user 7's board" cannot act on someone else's behalf. Ids that the model does supply (board,
 * column, task) are checked against that user by the services and answered with "not found" otherwise.
 *
 * <p>Every tool returns a map of the form {ok:true, data} or {ok:false, error}. Business failures are reported
 * to the model as data instead of exceptions, so it can explain them to the user.
 */
public class BoardAssistantTools {

    private static final Logger log = LoggerFactory.getLogger(BoardAssistantTools.class);

    static final int MAX_SUBTASKS = 8;

    private final Long userId;
    private final BoardService boardService;
    private final TaskService taskService;
    private final ObjectMapper mapper;
    private final int maxCalls;
    private final List<ToolCallRecord> calls = new CopyOnWriteArrayList<>();

    /**
     * @param mapper the application's ObjectMapper. Tool results are converted with it before they are handed to
     *               Spring AI, so dates reach the model as ISO strings ("2026-10-15") exactly as in the REST API.
     *               Left to itself, Spring AI writes a LocalDate as an array ([2026,10,15]).
     */
    public BoardAssistantTools(Long userId, BoardService boardService, TaskService taskService,
                               ObjectMapper mapper, int maxCalls) {
        this.userId = userId;
        this.boardService = boardService;
        this.taskService = taskService;
        this.mapper = mapper;
        this.maxCalls = maxCalls;
    }

    /** The tools invoked so far, in order. */
    public List<ToolCallRecord> calls() {
        return List.copyOf(calls);
    }

    @Tool(description = "List the signed-in user's boards (id and name), newest first.")
    public Map<String, Object> listBoards() {
        return invoke("listBoards", Map.of(), () -> boardService.listBoards(userId));
    }

    @Tool(description = "Get one board with its columns and cards. Each column has an id and a name; each card has "
            + "id, title, description, priority, dueDate and position. Use it to find column ids and to answer "
            + "questions about what is on the board.")
    public Map<String, Object> getBoard(
            @ToolParam(description = "Board id, from listBoards or from the board the user is looking at") Long boardId) {
        return invoke("getBoard", describe("boardId", boardId), () -> boardService.getBoard(userId, boardId));
    }

    @Tool(description = "Create one card at the end of a column. Only call this after the user has explicitly "
            + "confirmed the column, title, priority and due date.")
    public Map<String, Object> createTask(
            @ToolParam(description = "Column id, taken from getBoard") Long columnId,
            @ToolParam(description = "Short card title, at most 200 characters") String title,
            @ToolParam(required = false, description = "More detail, at most 2000 characters") String description,
            @ToolParam(required = false, description = "LOW, MEDIUM or HIGH; MEDIUM when omitted") String priority,
            @ToolParam(required = false, description = "Due date in ISO format, for example 2026-10-14") String dueDate) {
        Map<String, Object> args = describe("columnId", columnId, "title", title, "description", description,
                "priority", priority, "dueDate", dueDate);
        return invoke("createTask", args, () -> taskService.createTask(userId, new CreateTaskRequest(
                columnId,
                AiInput.requireTitle(title),
                AiInput.optionalDescription(description),
                null,
                AiInput.parsePriority(priority),
                AiInput.parseDate(dueDate))));
    }

    @Tool(description = "Split a card into smaller cards: creates one new card per title, at the end of the same "
            + "column as the given card. Only call this after the user has explicitly confirmed the list of "
            + "sub-task titles. The original card is not changed.")
    public Map<String, Object> splitTask(
            @ToolParam(description = "Id of the card to split, taken from getBoard") Long taskId,
            @ToolParam(description = "Titles of the new sub-task cards, 2 to 8 of them") List<String> subtaskTitles) {
        Map<String, Object> args = describe("taskId", taskId, "subtaskTitles", subtaskTitles);
        return invoke("splitTask", args, () -> {
            List<String> titles = cleanTitles(subtaskTitles);
            TaskResponse parent = taskService.getTask(userId, taskId);
            List<NewTaskItem> items = titles.stream()
                    .map(title -> new NewTaskItem(title, null, parent.priority(), null))
                    .toList();
            return taskService.createTasks(userId, new BulkCreateTasksRequest(parent.columnId(), items));
        });
    }

    /** Validates the title list: non-blank, de-duplicated, within the length limit, 2 to MAX_SUBTASKS entries. */
    private static List<String> cleanTitles(List<String> raw) {
        // Keyed by the lower-case title so "Write tests" and "write tests" count as the same card.
        Map<String, String> unique = new LinkedHashMap<>();
        if (raw != null) {
            for (String title : raw) {
                String clean = AiInput.requireTitle(title);
                unique.putIfAbsent(clean.toLowerCase(Locale.ROOT), clean);
            }
        }
        if (unique.size() < 2 || unique.size() > MAX_SUBTASKS) {
            throw new IllegalArgumentException("subtaskTitles must contain between 2 and " + MAX_SUBTASKS
                    + " different titles");
        }
        return List.copyOf(unique.values());
    }

    private Map<String, Object> invoke(String tool, Map<String, Object> args, Supplier<Object> action) {
        if (calls.size() >= maxCalls) {
            log.warn("AI tool call limit reached: user={} tool={}", userId, tool);
            return error("Tool call limit reached for this turn. Answer the user with what you already know.");
        }
        try {
            // A plain tree of maps, lists and strings: what the REST API would have returned as JSON.
            Object data = mapper.convertValue(action.get(), Object.class);
            record(tool, args, true);
            return Map.of("ok", true, "data", data);
        } catch (NotFoundException | BadRequestException | ConflictException | IllegalArgumentException e) {
            record(tool, args, false);
            return error(e.getMessage());
        }
    }

    private void record(String tool, Map<String, Object> args, boolean ok) {
        calls.add(new ToolCallRecord(tool, args, ok));
        log.info("AI tool call: user={} tool={} args={} ok={}", userId, tool, args, ok);
    }

    private static Map<String, Object> error(String message) {
        return Map.of("ok", false, "error", message);
    }

    /** Builds an ordered map from alternating names and values, skipping the ones the model left out. */
    private static Map<String, Object> describe(Object... namesAndValues) {
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            if (namesAndValues[i + 1] != null) {
                args.put(String.valueOf(namesAndValues[i]), namesAndValues[i + 1]);
            }
        }
        return args;
    }
}
