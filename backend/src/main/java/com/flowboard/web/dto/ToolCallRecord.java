package com.flowboard.web.dto;

import java.util.Map;

/**
 * One tool invocation made by the assistant.
 *
 * @param name tool name, for example "createTask"
 * @param args the arguments the model supplied, by parameter name; parameters it left out are absent
 * @param ok   false when the tool refused or failed (for example the column does not exist)
 */
public record ToolCallRecord(String name, Map<String, Object> args, boolean ok) {
}
