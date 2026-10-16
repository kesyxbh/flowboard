package com.flowboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Helpers shared by the HTTP-level tests: register a throw-away user, send JSON with a bearer token, and build
 * small fixtures (board, column, task). The concrete test classes carry @SpringBootTest and @AutoConfigureMockMvc.
 *
 * <p>Request bodies are written as UTF-8 bytes on purpose: the default charset of the development machine is
 * GBK, so this makes sure non-ASCII text survives the round trip regardless of the platform.
 */
abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    protected String registerAndGetToken() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode response = send(post("/api/auth/register"), null,
                Map.of("username", "user" + suffix, "email", "user" + suffix + "@example.com",
                        "password", "password123"),
                201);
        return response.get("token").asText();
    }

    protected JsonNode send(MockHttpServletRequestBuilder request, String token, Object body, int expectedStatus)
            throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        MvcResult result = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return content.isBlank() ? json.nullNode() : json.readTree(content);
    }

    protected JsonNode getJson(String url, String token, int expectedStatus) throws Exception {
        return send(get(url), token, null, expectedStatus);
    }

    protected long createBoard(String token, String name) throws Exception {
        return send(post("/api/boards"), token, Map.of("name", name), 201).get("id").asLong();
    }

    protected long createColumn(String token, long boardId, String name) throws Exception {
        return send(post("/api/boards/" + boardId + "/columns"), token, Map.of("name", name), 201)
                .get("id").asLong();
    }

    protected long createTask(String token, long columnId, String title) throws Exception {
        return send(post("/api/tasks"), token, Map.of("columnId", columnId, "title", title), 201)
                .get("id").asLong();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
