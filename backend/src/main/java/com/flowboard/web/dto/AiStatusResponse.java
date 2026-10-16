package com.flowboard.web.dto;

/**
 * @param configured true when a real LLM API key is set; when false every AI endpoint answers 503
 * @param model      the model name that would be used
 */
public record AiStatusResponse(boolean configured, String model) {
}
