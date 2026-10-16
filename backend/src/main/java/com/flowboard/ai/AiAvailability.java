package com.flowboard.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Answers one question: is a real LLM API key configured?
 *
 * <p>The application must start without a key. The Spring AI starter refuses to start when the key property is
 * empty, so application.yml supplies the placeholder "not-configured" when LLM_API_KEY is not set. The AI
 * endpoints therefore check this at request time and answer 503 instead.
 */
@Component
public class AiAvailability {

    static final String PLACEHOLDER_KEY = "not-configured";

    private final String apiKey;
    private final String model;

    public AiAvailability(@Value("${spring.ai.openai.api-key:}") String apiKey,
                          @Value("${spring.ai.openai.chat.options.model:}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !PLACEHOLDER_KEY.equals(apiKey);
    }

    public String model() {
        return model;
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI is not configured. Set the LLM_API_KEY environment variable and restart the backend.");
        }
    }
}
