package com.flowboard.web.dto;

import java.util.List;

/**
 * @param task        the card that was created
 * @param columnName  the column it was added to
 * @param explanation one sentence describing what was created; it is built from the validated values, not
 *                    written by the model, so it always matches what is stored
 * @param warnings    parts of the sentence that could not be used (an unknown column, an unreadable date, ...)
 */
public record QuickAddResponse(TaskResponse task, String columnName, String explanation, List<String> warnings) {
}
