package com.flowboard;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST API end to end: real HTTP layer (MockMvc), real Spring Security with JWT, real JPA on an in-memory
 * database. Every test registers its own users, so the tests are independent of each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BoardFlowIntegrationTest extends ApiTestSupport {

    // Non-ASCII fixtures, built from Unicode code points so that this source file stays pure ASCII. They prove that
    // text outside ASCII survives the whole round trip (the development machine's default charset is GBK):
    // BOARD = "product iteration board", TODO = "to do", DOING = "in progress" (all in Chinese).
    private static final String BOARD = text(0x4EA7, 0x54C1, 0x8FED, 0x4EE3, 0x770B, 0x677F);
    private static final String TODO = text(0x5F85, 0x529E);
    private static final String DOING = text(0x8FDB, 0x884C, 0x4E2D);

    private static String text(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    @Test
    void fullFlow_createBoardColumnsTasks_moveTask_thenReload() throws Exception {
        String token = registerAndGetToken();

        long boardId = createBoard(token, BOARD);
        long todo = createColumn(token, boardId, TODO);
        long doing = createColumn(token, boardId, DOING);
        long design = createTask(token, todo, "Design the ERD");
        long write = createTask(token, todo, "Write the API");

        // Move "Write the API" to the first position of the second column.
        send(patch("/api/tasks/" + write + "/move"), token, Map.of("targetColumnId", doing, "orderIndex", 0), 200);

        // Loading the board again is the same as reloading the page: the data must be stored and ordered.
        mvc.perform(get("/api/boards/" + boardId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(BOARD))
                .andExpect(jsonPath("$.columns.length()").value(2))
                .andExpect(jsonPath("$.columns[0].name").value(TODO))
                .andExpect(jsonPath("$.columns[0].tasks.length()").value(1))
                .andExpect(jsonPath("$.columns[0].tasks[0].id").value(design))
                .andExpect(jsonPath("$.columns[0].tasks[0].title").value("Design the ERD"))
                .andExpect(jsonPath("$.columns[1].name").value(DOING))
                .andExpect(jsonPath("$.columns[1].tasks[0].id").value(write))
                .andExpect(jsonPath("$.columns[1].tasks[0].orderIndex").value(0));
    }

    @Test
    void editTask_overwritesEditableFields_andMissingRequiredFieldIsRejected() throws Exception {
        String token = registerAndGetToken();
        long boardId = createBoard(token, "B");
        long col = createColumn(token, boardId, "todo");
        long task = createTask(token, col, "old");

        JsonNode updated = send(patch("/api/tasks/" + task), token,
                Map.of("title", "new title", "description", "details", "priority", "HIGH", "dueDate", "2026-12-31"),
                200);
        assertThat(updated.get("priority").asText()).isEqualTo("HIGH");
        assertThat(updated.get("dueDate").asText()).isEqualTo("2026-12-31");

        // priority is required: the body is a full snapshot of the editable fields
        send(patch("/api/tasks/" + task), token, Map.of("title", "x"), 400);
    }

    @Test
    void deleteTask_removesTheCard_andClosesTheGap() throws Exception {
        String token = registerAndGetToken();
        long boardId = createBoard(token, "B");
        long col = createColumn(token, boardId, "todo");
        long a = createTask(token, col, "a");
        long b = createTask(token, col, "b");
        long c = createTask(token, col, "c");

        mvc.perform(delete("/api/tasks/" + b).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        JsonNode tasks = getJson("/api/boards/" + boardId, token, 200).at("/columns/0/tasks");
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).get("id").asLong()).isEqualTo(a);
        assertThat(tasks.get(0).get("orderIndex").asInt()).isEqualTo(0);
        assertThat(tasks.get(1).get("id").asLong()).isEqualTo(c);
        assertThat(tasks.get(1).get("orderIndex").asInt()).isEqualTo(1); // was 2: no gap is left

        // deleting it again: it no longer exists
        mvc.perform(delete("/api/tasks/" + b).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void bulkCreate_appendsAllCardsInOrder_orNone() throws Exception {
        String token = registerAndGetToken();
        long boardId = createBoard(token, "B");
        long col = createColumn(token, boardId, "todo");
        createTask(token, col, "existing");

        JsonNode created = send(post("/api/tasks/bulk"), token, Map.of("columnId", col, "tasks", List.of(
                Map.of("title", "first"),
                Map.of("title", "second", "priority", "HIGH", "description", "details"),
                Map.of("title", "third", "dueDate", "2026-11-01"))), 201);

        assertThat(created).hasSize(3);
        assertThat(created.get(0).get("orderIndex").asInt()).isEqualTo(1); // after the existing card
        assertThat(created.get(1).get("priority").asText()).isEqualTo("HIGH");
        assertThat(created.get(2).get("dueDate").asText()).isEqualTo("2026-11-01");

        // one invalid item rejects the whole request, nothing is created
        send(post("/api/tasks/bulk"), token, Map.of("columnId", col, "tasks", List.of(
                Map.of("title", "fine"), Map.of("title", "   "))), 400);
        JsonNode tasks = getJson("/api/boards/" + boardId, token, 200).at("/columns/0/tasks");
        assertThat(tasks).hasSize(4); // existing + the 3 created above
    }

    @Test
    void me_returnsTheSignedInUser_andRequiresAToken() throws Exception {
        String token = registerAndGetToken();

        JsonNode me = getJson("/api/auth/me", token, 200);

        assertThat(me.get("email").asText()).endsWith("@example.com");
        assertThat(me.has("passwordHash")).isFalse();
        getJson("/api/auth/me", null, 401);
    }

    @Test
    void protectedEndpoints_requireValidToken_andAnswerWithAProblemBody() throws Exception {
        mvc.perform(get("/api/boards"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.instance").value("/api/boards"));
        mvc.perform(get("/api/boards").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_duplicateEmail_returns409_andLoginWorks() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> body = Map.of("username", "dup" + suffix, "email", "dup" + suffix + "@example.com",
                "password", "password123");
        send(post("/api/auth/register"), null, body, 201);
        // e-mail addresses are case-insensitive
        send(post("/api/auth/register"), null,
                Map.of("username", "other" + suffix, "email", "DUP" + suffix + "@Example.com",
                        "password", "password123"),
                409);

        JsonNode login = send(post("/api/auth/login"), null,
                Map.of("email", "dup" + suffix + "@example.com", "password", "password123"), 200);
        assertThat(login.get("token").asText()).isNotBlank();

        send(post("/api/auth/login"), null,
                Map.of("email", "dup" + suffix + "@example.com", "password", "wrong-password"), 401);
    }

    @Test
    void validation_errorsAreReportedPerField() throws Exception {
        String token = registerAndGetToken();

        JsonNode error = send(post("/api/boards"), token, Map.of("name", "   "), 400);

        assertThat(error.get("title").asText()).isEqualTo("Validation Failed");
        assertThat(error.at("/errors/name").isMissingNode()).isFalse();
    }

    @Test
    void boards_areIsolatedBetweenUsers() throws Exception {
        String alice = registerAndGetToken();
        String bob = registerAndGetToken();

        long boardId = createBoard(alice, "alice-board");
        long col = createColumn(alice, boardId, "todo");
        long task = createTask(alice, col, "secret");

        // Bob reading the board, adding a card to it, moving or deleting Alice's card: all answered as "not found".
        getJson("/api/boards/" + boardId, bob, 404);
        send(post("/api/tasks"), bob, Map.of("columnId", col, "title", "intruder"), 404);
        send(patch("/api/tasks/" + task + "/move"), bob, Map.of("targetColumnId", col, "orderIndex", 0), 404);
        send(delete("/api/tasks/" + task), bob, null, 404);
        send(post("/api/tasks/bulk"), bob, Map.of("columnId", col, "tasks",
                List.of(Map.of("title", "intruder"))), 404);

        // Each user only sees their own list.
        assertThat(getJson("/api/boards", bob, 200)).isEmpty();
        assertThat(getJson("/api/boards", alice, 200)).hasSize(1);
    }
}
