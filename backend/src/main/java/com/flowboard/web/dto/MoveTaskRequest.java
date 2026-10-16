package com.flowboard.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Move a card to the column targetColumnId so that it ends up at position orderIndex (0 = top).
 * orderIndex is the final position after the move; a value past the end means "last".
 * The frontend sends this when a card is dropped; the endpoint is the same for same-column and cross-column moves.
 */
public record MoveTaskRequest(
        @NotNull Long targetColumnId,
        @NotNull @PositiveOrZero Integer orderIndex
) {
}
