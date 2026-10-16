package com.flowboard.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.service.BoardService;
import com.flowboard.service.TaskService;
import com.flowboard.web.dto.AiChatRequest;
import com.flowboard.web.dto.AiChatResponse;
import com.flowboard.web.dto.BoardDetailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The board assistant: a chat endpoint where the model can read the signed-in user's boards and create cards
 * for them through {@link BoardAssistantTools}.
 *
 * <p>Conversation memory is a plain in-memory map. It is keyed by "userId:conversationId" so one user can never
 * read or continue another user's conversation, it keeps only the most recent messages, and it is lost on
 * restart. That is enough for a demo; a production system would persist it.
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /** Messages (user + assistant) kept per conversation. Even number: turns are trimmed in pairs. */
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_CONVERSATIONS = 500;
    /** Upper bound on tool calls in one turn, so a confused model cannot loop forever. */
    private static final int MAX_TOOL_CALLS_PER_TURN = 5;

    // Locale.ENGLISH is explicit: without it the weekday would follow the JVM's language (see AiTime.weekday).
    private static final DateTimeFormatter PROMPT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (EEEE)", Locale.ENGLISH);

    private final ChatClient chatClient;
    private final AiAvailability availability;
    private final AiTime time;
    private final BoardService boardService;
    private final TaskService taskService;
    private final ObjectMapper mapper;

    /** Least recently used conversations are dropped once there are more than MAX_CONVERSATIONS. */
    private final Map<String, List<Message>> conversations = Collections.synchronizedMap(
            new LinkedHashMap<String, List<Message>>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Message>> eldest) {
                    return size() > MAX_CONVERSATIONS;
                }
            });

    public AiChatService(ChatClient.Builder chatClientBuilder, AiAvailability availability, AiTime time,
                         BoardService boardService, TaskService taskService, ObjectMapper mapper) {
        this.chatClient = chatClientBuilder.build();
        this.availability = availability;
        this.time = time;
        this.boardService = boardService;
        this.taskService = taskService;
        this.mapper = mapper;
    }

    public AiChatResponse chat(Long userId, AiChatRequest request) {
        availability.requireConfigured();

        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? UUID.randomUUID().toString()
                : request.conversationId();
        List<Message> history = conversations.computeIfAbsent(userId + ":" + conversationId, key -> new ArrayList<>());
        List<Message> snapshot;
        synchronized (history) {
            snapshot = List.copyOf(history);
        }

        // Throws NotFoundException (404) when the board does not exist or belongs to someone else.
        BoardDetailResponse board = request.boardId() == null ? null : boardService.getBoard(userId, request.boardId());
        BoardAssistantTools tools =
                new BoardAssistantTools(userId, boardService, taskService, mapper, MAX_TOOL_CALLS_PER_TURN);

        String reply;
        try {
            reply = chatClient.prompt()
                    .system(systemPrompt(board, time.now(request.timeZone())))
                    .messages(snapshot)
                    .user(request.message())
                    .tools(tools)
                    .call()
                    .content();
        } catch (RuntimeException e) {
            log.error("LLM request failed for user {}", userId, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI provider request failed. Check the API key, base URL and model name.");
        }
        if (reply == null || reply.isBlank()) {
            reply = "The assistant returned an empty reply. Please try again.";
        }

        synchronized (history) {
            history.add(new UserMessage(request.message()));
            history.add(new AssistantMessage(reply));
            while (history.size() > MAX_HISTORY_MESSAGES) {
                history.remove(0);
            }
        }
        return new AiChatResponse(conversationId, reply, tools.calls());
    }

    // package-private so a test can look at the exact text the model receives
    String systemPrompt(BoardDetailResponse board, ZonedDateTime now) {
        String boardContext = board == null
                ? "No board is selected. Call listBoards, then getBoard, to find the board and its column ids."
                : "The user is looking at the board \"%s\" (id %d). Its columns, left to right: %s."
                        .formatted(board.name(), board.id(), board.columns().stream()
                                .map(column -> "\"%s\" (id %d)".formatted(column.name(), column.id()))
                                .collect(Collectors.joining(", ")));
        return """
                You are the assistant of FlowBoard, a kanban board where cards move through columns.
                Current local date and time: %s, time zone %s. Resolve relative dates such as "tomorrow"
                or "next Friday" from this.
                %s

                Rules:
                - Use the tools to read boards and to create cards. Never invent board, column or card ids.
                - Dates are ISO dates (yyyy-MM-dd) when you call tools. Priority is LOW, MEDIUM or HIGH.
                - Before calling createTask or splitTask, state exactly what you are about to create (column,
                  titles, priority, due date) and ask the user to confirm. Call the tool only after the user has
                  clearly confirmed.
                - To summarise a board, call getBoard and base the summary only on what it returns.
                - You act only for the signed-in user. Refuse to act for anyone else.
                - If a tool returns an error, explain it plainly and suggest an alternative.
                - Reply in the language the user writes in. Keep answers short.
                - Plain text only: the chat window does not render Markdown, so do not use asterisks, pound
                  signs or tables. Use short lines, or a list with a hyphen at the start of each line.
                """.formatted(now.format(PROMPT_TIME), now.getZone(), boardContext);
    }
}
