package com.flowboard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract of the AI endpoints when NO model is available (no API key configured): they must be
 * protected, validate their input, and answer 503 with a clear message instead of failing in a confusing way.
 * Nothing here calls a language model; see AiFakeModelIntegrationTest for the paths that do.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=not-configured")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiEndpointsIntegrationTest extends ApiTestSupport {

    @Test
    void aiEndpointsRequireAuthentication() throws Exception {
        send(get("/api/ai/status"), null, null, 401);
        send(post("/api/ai/chat"), null, Map.of("message", "hello"), 401);
        send(post("/api/ai/tasks/quick-add"), null, Map.of("boardId", 1, "text", "x"), 401);
        send(post("/api/ai/tasks/1/split"), null, null, 401);
        send(post("/api/ai/boards/1/standup"), null, null, 401);
    }

    @Test
    void statusReportsThatAiIsNotConfigured() throws Exception {
        String token = registerAndGetToken();

        mvc.perform(get("/api/ai/status").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.model").isNotEmpty());
    }

    @Test
    void everyAiEndpointAnswers503WhenNoKeyIsConfigured() throws Exception {
        String token = registerAndGetToken();
        long boardId = createBoard(token, "B");
        long col = createColumn(token, boardId, "todo");
        long task = createTask(token, col, "card");

        expectNotConfigured(post("/api/ai/chat"), token, Map.of("message", "hello"));
        expectNotConfigured(post("/api/ai/tasks/quick-add"), token, Map.of("boardId", boardId, "text", "do it"));
        expectNotConfigured(post("/api/ai/tasks/" + task + "/split"), token, null);
        expectNotConfigured(post("/api/ai/boards/" + boardId + "/standup"), token, null);
    }

    @Test
    void blankInputIsRejectedBeforeAnythingElseHappens() throws Exception {
        String token = registerAndGetToken();

        send(post("/api/ai/chat"), token, Map.of("message", "   "), 400);
        send(post("/api/ai/tasks/quick-add"), token, Map.of("boardId", 1, "text", ""), 400);
        send(post("/api/ai/tasks/quick-add"), token, Map.of("text", "no board"), 400);
    }

    private void expectNotConfigured(MockHttpServletRequestBuilder request,
                                     String token, Object body) throws Exception {
        if (body != null) {
            request.contentType("application/json").content(json.writeValueAsBytes(body));
        }
        mvc.perform(request.header("Authorization", bearer(token)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("not configured")));
    }
}
