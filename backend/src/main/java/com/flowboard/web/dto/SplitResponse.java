package com.flowboard.web.dto;

import java.util.List;

/**
 * A preview of how a card could be split. Nothing has been created yet: the client shows the suggestions and,
 * when the user confirms, creates the chosen ones with POST /api/tasks/bulk in the given column.
 *
 * @param taskId      the card that was analysed
 * @param columnId    the column of that card, where the sub-tasks should be created
 * @param subtasks    validated suggestions, at most 6
 * @param explanation one sentence for the UI
 */
public record SplitResponse(Long taskId, Long columnId, List<SubtaskSuggestion> subtasks, String explanation) {
}
