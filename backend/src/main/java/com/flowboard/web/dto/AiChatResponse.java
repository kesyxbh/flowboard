package com.flowboard.web.dto;

import java.util.List;

/**
 * @param conversationId id to send back with the next message
 * @param reply          the assistant's answer
 * @param toolCalls      the tools the assistant called while producing this answer, in order, so the UI can show
 *                       that the system was really changed
 */
public record AiChatResponse(String conversationId, String reply, List<ToolCallRecord> toolCalls) {
}
