package com.flowboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs every AI path (HTTP endpoint, Spring AI client, tool calling, structured output, services, database)
 * against a FAKE OpenAI-compatible server instead of a real model. The fake model is scripted: it looks at the
 * system prompt to see which feature is calling, and answers with a prepared JSON or text.
 *
 * <p>What this proves: the wiring is right, the requests have the expected shape (tools offered, today's date in
 * the prompt, no user id anywhere), and everything the model returns is validated. What it cannot prove is how
 * a real model behaves - that needs a real key and a human looking at the answers.
 *
 * <p>The clock is fixed to Wednesday 2026-10-14 09:30 UTC, so the tests can assert exact dates.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiFakeModelIntegrationTest extends ApiTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Every request body the fake model received, in order. */
    private static final List<JsonNode> RECEIVED = new CopyOnWriteArrayList<>();

    // What the fake model should answer next. Tests set these before calling the API.
    private static volatile String toolArguments = "{}";
    private static volatile String quickAddJson = "{}";
    private static volatile String splitJson = "{}";
    private static volatile String standupText = "";

    private static HttpServer fakeModel;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-14T09:30:00Z"), ZoneOffset.UTC);
        }
    }

    @BeforeAll
    static void startFakeModel() throws IOException {
        fakeModel = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeModel.createContext("/v1/chat/completions", AiFakeModelIntegrationTest::answer);
        fakeModel.start();
    }

    @AfterAll
    static void stopFakeModel() {
        fakeModel.stop(0);
    }

    @DynamicPropertySource
    static void pointTheClientAtTheFakeModel(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + fakeModel.getAddress().getPort());
        registry.add("spring.ai.openai.api-key", () -> "test-key");
    }

    @BeforeEach
    void resetFake() {
        RECEIVED.clear();
        toolArguments = "{}";
        quickAddJson = "{}";
        splitJson = "{}";
        standupText = "";
    }

    // ---------------------------------------------------------------- the fake model

    private static void answer(HttpExchange exchange) throws IOException {
        JsonNode request = MAPPER.readTree(exchange.getRequestBody());
        RECEIVED.add(request);
        JsonNode messages = request.get("messages");
        JsonNode last = messages.get(messages.size() - 1);
        String lastText = last.path("content").toString();
        String system = messages.get(0).path("content").asText();

        if (lastText.contains("explode")) {
            reply(exchange, 401, Map.of("error", Map.of("message", "invalid api key")));
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        String finishReason = "stop";
        if (system.contains("You turn one sentence into a task card")) {
            message.put("content", quickAddJson);
        } else if (system.contains("You break one card of a kanban board")) {
            message.put("content", splitJson);
        } else if (system.contains("stand-up briefing")) {
            message.put("content", standupText);
        } else if ("tool".equals(last.path("role").asText())) {
            // second round of a tool conversation: the tool result is in, now answer the user
            message.put("content", "Done.");
        } else if (lastText.contains("create")) {
            message.put("content", null);
            message.put("tool_calls", List.of(Map.of(
                    "id", "call_1",
                    "type", "function",
                    "function", Map.of("name", "createTask", "arguments", toolArguments))));
            finishReason = "tool_calls";
        } else {
            message.put("content", "Anything else?");
        }
        reply(exchange, 200, Map.of(
                "id", "chatcmpl-test",
                "object", "chat.completion",
                "created", 1,
                "model", "fake-model",
                "choices", List.of(Map.of("index", 0, "message", message, "finish_reason", finishReason)),
                "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2)));
    }

    private static void reply(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(payload);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private JsonNode chat(String token, String conversationId, String message, Long boardId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("conversationId", conversationId);
        body.put("message", message);
        body.put("timeZone", "UTC");
        if (boardId != null) {
            body.put("boardId", boardId);
        }
        return send(post("/api/ai/chat"), token, body, 200);
    }

    private String systemPromptOf(JsonNode request) {
        return request.get("messages").get(0).get("content").asText();
    }

    // ---------------------------------------------------------------- chat assistant with tools

    @Test
    void theAssistantCreatesACardForTheSignedInUserThroughATool() throws Exception {
        String token = registerAndGetToken();
        long boardId = createBoard(token, "Sprint 12");
        long todo = createColumn(token, boardId, "To do");
        toolArguments = json.writeValueAsString(Map.of(
                "columnId", todo, "title", "Write release notes", "priority", "high", "dueDate", "2026-10-16"));

        JsonNode reply = chat(token, "conv-1", "please create a card for the release notes", boardId);

        assertThat(reply.get("reply").asText()).isEqualTo("Done.");
        assertThat(reply.get("toolCalls")).hasSize(1);
        assertThat(reply.at("/toolCalls/0/name").asText()).isEqualTo("createTask");
        assertThat(reply.at("/toolCalls/0/ok").asBoolean()).isTrue();
        assertThat(reply.at("/toolCalls/0/args/columnId").asLong()).isEqualTo(todo);

        // the card really exists, with the validated values
        JsonNode card = getJson("/api/boards/" + boardId, token, 200).at("/columns/0/tasks/0");
        assertThat(card.get("title").asText()).isEqualTo("Write release notes");
        assertThat(card.get("priority").asText()).isEqualTo("HIGH");
        assertThat(card.get("dueDate").asText()).isEqualTo("2026-10-16");

        // the model was offered exactly four tools, and none of them accepts a user id
        JsonNode firstRequest = RECEIVED.get(0);
        List<String> toolNames = new ArrayList<>();
        firstRequest.get("tools").forEach(tool -> toolNames.add(tool.at("/function/name").asText()));
        assertThat(toolNames).containsExactlyInAnyOrder("listBoards", "getBoard", "createTask", "splitTask");
        assertThat(firstRequest.get("tools").toString()).doesNotContain("userId");

        // the tool result that was sent back to the model shows the date as an ISO string, not as [2026,10,16]
        JsonNode secondRequest = RECEIVED.get(1);
        JsonNode toolMessage = secondRequest.get("messages").get(secondRequest.get("messages").size() - 1);
        assertThat(toolMessage.get("role").asText()).isEqualTo("tool");
        assertThat(toolMessage.get("content").asText()).contains("\"dueDate\":\"2026-10-16\"");

        // the system prompt carries the date, the weekday and the board the user is looking at
        String system = systemPromptOf(firstRequest);
        assertThat(system).contains("2026-10-14").contains("Wednesday");
        assertThat(system).contains("Sprint 12").contains("id " + boardId).contains("id " + todo);
    }

    @Test
    void aFailedToolCallIsReportedToTheModelAndToTheClient() throws Exception {
        String alice = registerAndGetToken();
        String bob = registerAndGetToken();
        long aliceBoard = createBoard(alice, "alice");
        long aliceColumn = createColumn(alice, aliceBoard, "To do");
        // Bob's assistant is told (by a prompt injection, say) to create a card in Alice's column.
        toolArguments = json.writeValueAsString(Map.of("columnId", aliceColumn, "title", "intruder"));

        JsonNode reply = chat(bob, "conv-bob", "please create it", null);

        assertThat(reply.at("/toolCalls/0/ok").asBoolean()).isFalse();
        JsonNode lastRequest = RECEIVED.get(RECEIVED.size() - 1);
        JsonNode toolMessage = lastRequest.get("messages").get(lastRequest.get("messages").size() - 1);
        assertThat(toolMessage.get("role").asText()).isEqualTo("tool");
        assertThat(toolMessage.get("content").asText()).contains("Column not found");
        // and nothing was created on Alice's board
        assertThat(getJson("/api/boards/" + aliceBoard, alice, 200).at("/columns/0/tasks")).isEmpty();
    }

    @Test
    void toolInputIsValidatedLikeApiInput() throws Exception {
        String token = registerAndGetToken();
        long boardId = createBoard(token, "B");
        long col = createColumn(token, boardId, "To do");
        toolArguments = json.writeValueAsString(Map.of("columnId", col, "title", "x".repeat(250)));

        JsonNode reply = chat(token, "conv-long", "please create it", boardId);

        assertThat(reply.at("/toolCalls/0/ok").asBoolean()).isFalse();
        assertThat(getJson("/api/boards/" + boardId, token, 200).at("/columns/0/tasks")).isEmpty();
    }

    @Test
    void conversationHistoryIsSentBackAndIsPrivateToTheUser() throws Exception {
        String alice = registerAndGetToken();
        String bob = registerAndGetToken();
        String sharedConversationId = "shared-conversation";

        chat(alice, sharedConversationId, "hello there", null);
        chat(alice, sharedConversationId, "thanks", null);
        assertThat(RECEIVED.get(1).get("messages").toString()).contains("hello there");

        // the same conversation id used by another user starts with an empty history
        chat(bob, sharedConversationId, "hi", null);
        assertThat(RECEIVED.get(2).get("messages").toString()).doesNotContain("hello there");
    }

    @Test
    void aProviderFailureBecomesA502WithoutLeakingProviderDetails() throws Exception {
        String token = registerAndGetToken();

        mvc.perform(post("/api/ai/chat")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(Map.of("message", "explode"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(
                        "The AI provider request failed. Check the API key, base URL and model name."));
    }

    @Test
    void chatAboutSomeoneElsesBoardIsRejected() throws Exception {
        String alice = registerAndGetToken();
        String bob = registerAndGetToken();
        long aliceBoard = createBoard(alice, "alice");

        Map<String, Object> body = Map.of("message", "hello", "boardId", aliceBoard);
        send(post("/api/ai/chat"), bob, body, 404);
        assertThat(RECEIVED).isEmpty(); // the model was never called
    }

    // ---------------------------------------------------------------- quick add (structured output)

    @Test
    void quickAddTurnsASentenceIntoAValidatedCardInTheNamedColumn() throws Exception {
        String token = registerAndGetToken();
        long boardId = createBoard(token, "Sprint 12");
        createColumn(token, boardId, "To do");
        long doing = createColumn(token, boardId, "Doing");
        quickAddJson = json.writeValueAsString(Map.of(
                "title", "Fix login bug", "priority", "high", "dueDate", "2026-10-16", "column", "doing"));

        JsonNode response = send(post("/api/ai/tasks/quick-add"), token, Map.of(
                "boardId", boardId, "text", "fix the login bug by Friday, high priority, in doing",
                "timeZone", "UTC"), 201);

        assertThat(response.at("/task/title").asText()).isEqualTo("Fix login bug");
        assertThat(response.at("/task/columnId").asLong()).isEqualTo(doing);
        assertThat(response.at("/task/priority").asText()).isEqualTo("HIGH");
        assertThat(response.at("/task/dueDate").asText()).isEqualTo("2026-10-16");
        assertThat(response.get("columnName").asText()).isEqualTo("Doing");
        assertThat(response.get("explanation").asText())
                .isEqualTo("Added \"Fix login bug\" to Doing, priority HIGH, due 2026-10-16.");
        assertThat(response.get("warnings")).isEmpty();

        // the prompt told the model today's date and the real column names
        String system = systemPromptOf(RECEIVED.get(0));
        assertThat(system).contains("2026-10-14").contains("Wednesday").contains("[To do, Doing]");
    }

    @Test
    void quickAddDropsWhatTheModelGotWrongAndReportsIt() throws Exception {
        String token = registerAndGetToken();
        long boardId = createBoard(token, "B");
        long first = createColumn(token, boardId, "To do");
        quickAddJson = json.writeValueAsString(Map.of(
                "title", "Plan the offsite", "priority", "URGENT-ISH", "dueDate", "next blue moon",
                "column", "Backlog"));

        JsonNode response = send(post("/api/ai/tasks/quick-add"), token,
                Map.of("boardId", boardId, "text", "plan the offsite"), 201);

        assertThat(response.at("/task/columnId").asLong()).isEqualTo(first);
        assertThat(response.at("/task/priority").asText()).isEqualTo("MEDIUM");
        assertThat(response.at("/task/dueDate").isNull()).isTrue();
        assertThat(response.get("warnings")).hasSize(3);
    }

    @Test
    void quickAddWithoutATitleIsRejectedAndCreatesNothing() throws Exception {
        String token = registerAndGetToken();
        long boardId = createBoard(token, "B");
        createColumn(token, boardId, "To do");
        quickAddJson = json.writeValueAsString(Map.of("title", "  "));

        send(post("/api/ai/tasks/quick-add"), token, Map.of("boardId", boardId, "text", "???"), 422);

        assertThat(getJson("/api/boards/" + boardId, token, 200).at("/columns/0/tasks")).isEmpty();
    }

    @Test
    void quickAddOnABoardWithoutColumnsIsABadRequest() throws Exception {
        String token = registerAndGetToken();
        long boardId = createBoard(token, "empty");

        send(post("/api/ai/tasks/quick-add"), token, Map.of("boardId", boardId, "text", "do something"), 400);
        assertThat(RECEIVED).isEmpty();
    }

    @Test
    void quickAddOnSomeoneElsesBoardIsNotFound() throws Exception {
        String alice = registerAndGetToken();
        String bob = registerAndGetToken();
        long aliceBoard = createBoard(alice, "alice");
        createColumn(alice, aliceBoard, "To do");

        send(post("/api/ai/tasks/quick-add"), bob, Map.of("boardId", aliceBoard, "text", "x"), 404);
        assertThat(RECEIVED).isEmpty();
    }

    // ---------------------------------------------------------------- split (preview) + bulk create

    @Test
    void splitReturnsAValidatedPreviewAndCreatesNothing() throws Exception {
        String token = registerAndGetToken();
        long boardId = createBoard(token, "B");
        long col = createColumn(token, boardId, "To do");
        long task = createTask(token, col, "Launch the website");
        splitJson = json.writeValueAsString(Map.of("subtasks", List.of(
                Map.of("title", "Write the copy", "description", "Landing page text"),
                Map.of("title", "  "),
                Map.of("title", "write the COPY"),                    // duplicate, different case
                Map.of("title", "Launch the website"),                // repeats the parent
                Map.of("title", "Set up hosting"))));

        JsonNode response = send(post("/api/ai/tasks/" + task + "/split"), token, null, 200);

        assertThat(response.get("taskId").asLong()).isEqualTo(task);
        assertThat(response.get("columnId").asLong()).isEqualTo(col);
        assertThat(response.get("subtasks")).hasSize(2);
        assertThat(response.at("/subtasks/0/title").asText()).isEqualTo("Write the copy");
        assertThat(response.at("/subtasks/1/title").asText()).isEqualTo("Set up hosting");
        assertThat(getJson("/api/boards/" + boardId, token, 200).at("/columns/0/tasks")).hasSize(1);

        // the user confirms: the client creates the chosen suggestions in one request
        JsonNode created = send(post("/api/tasks/bulk"), token, Map.of(
                "columnId", response.get("columnId").asLong(),
                "tasks", List.of(Map.of("title", "Write the copy"), Map.of("title", "Set up hosting"))), 201);
        assertThat(created).hasSize(2);
        assertThat(getJson("/api/boards/" + boardId, token, 200).at("/columns/0/tasks")).hasSize(3);
    }

    @Test
    void splitWithNoUsableSuggestionsIsA502() throws Exception {
        String token = registerAndGetToken();
        long boardId = createBoard(token, "B");
        long col = createColumn(token, boardId, "To do");
        long task = createTask(token, col, "Launch");
        splitJson = json.writeValueAsString(Map.of("subtasks", List.of(Map.of("title", " "))));

        send(post("/api/ai/tasks/" + task + "/split"), token, null, 502);
    }

    @Test
    void splitOfSomeoneElsesCardIsNotFound() throws Exception {
        String alice = registerAndGetToken();
        String bob = registerAndGetToken();
        long aliceBoard = createBoard(alice, "alice");
        long task = createTask(alice, createColumn(alice, aliceBoard, "To do"), "secret");

        send(post("/api/ai/tasks/" + task + "/split"), bob, null, 404);
        assertThat(RECEIVED).isEmpty();
    }

    // ---------------------------------------------------------------- stand-up

    @Test
    void standupReturnsServerComputedFactsNextToTheModelsText() throws Exception {
        String token = registerAndGetToken();
        long boardId = createBoard(token, "Sprint 12");
        long todo = createColumn(token, boardId, "To do");
        long done = createColumn(token, boardId, "Done");
        // today is 2026-10-14 (fixed clock)
        createTaskWithDue(token, todo, "Overdue card", "2026-10-10");
        createTaskWithDue(token, todo, "Due tomorrow", "2026-10-15");
        createTaskWithDue(token, todo, "Far away", "2026-12-01");
        createTaskWithDue(token, done, "Finished long ago", "2026-09-01"); // done column: never a risk
        standupText = "Done\nOne card.\n\nIn progress\nThree cards.\n\nRisks\nOne overdue card.";

        JsonNode response = send(post("/api/ai/boards/" + boardId + "/standup"), token,
                Map.of("timeZone", "UTC"), 200);

        assertThat(response.get("summary").asText()).startsWith("Done");
        assertThat(response.at("/stats/totalTasks").asInt()).isEqualTo(4);
        assertThat(response.at("/stats/doneTasks").asInt()).isEqualTo(1);
        assertThat(response.at("/stats/overdue")).hasSize(1);
        assertThat(response.at("/stats/overdue/0/title").asText()).isEqualTo("Overdue card");
        assertThat(response.at("/stats/dueSoon")).hasSize(1);
        assertThat(response.at("/stats/dueSoon/0/title").asText()).isEqualTo("Due tomorrow");

        // the model received the facts it is supposed to phrase
        String user = RECEIVED.get(0).get("messages").get(1).get("content").asText();
        assertThat(user).contains("Today: 2026-10-14").contains("Overdue card").contains("[finished work]");
    }

    @Test
    void standupBodyIsOptionalAndBoardOwnershipIsChecked() throws Exception {
        String alice = registerAndGetToken();
        String bob = registerAndGetToken();
        long aliceBoard = createBoard(alice, "alice");
        createColumn(alice, aliceBoard, "To do");
        standupText = "Done\nNothing yet.";

        send(post("/api/ai/boards/" + aliceBoard + "/standup"), alice, null, 200);
        send(post("/api/ai/boards/" + aliceBoard + "/standup"), bob, null, 404);
    }

    private long createTaskWithDue(String token, long columnId, String title, String dueDate) throws Exception {
        return send(post("/api/tasks"), token,
                Map.of("columnId", columnId, "title", title, "dueDate", dueDate), 201).get("id").asLong();
    }
}
