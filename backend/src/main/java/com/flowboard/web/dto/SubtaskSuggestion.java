package com.flowboard.web.dto;

/** One proposed sub-task. It is only a suggestion: nothing is stored until the client posts it for creation. */
public record SubtaskSuggestion(String title, String description) {
}
