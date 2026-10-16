package com.flowboard.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param conversationId optional; omit it to start a new conversation, then send back the id from the response
 * @param message        what the user typed
 * @param boardId        optional; the board the user is looking at, so "add a task" needs no board name
 * @param timeZone       optional IANA zone such as "Europe/London", used to decide what "today" is
 */
public record AiChatRequest(
        @Size(max = 64) String conversationId,
        @NotBlank @Size(max = 2000) String message,
        Long boardId,
        @Size(max = 64) String timeZone) {
}
