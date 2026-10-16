package com.flowboard.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param boardId  the board the card is added to
 * @param text     one sentence such as "fix the login bug by Friday, high priority, in Doing"
 * @param timeZone optional IANA zone such as "Europe/London", used to resolve words like "tomorrow"
 */
public record QuickAddRequest(
        @NotNull Long boardId,
        @NotBlank @Size(max = 500) String text,
        @Size(max = 64) String timeZone) {
}
