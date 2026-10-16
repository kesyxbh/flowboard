package com.flowboard.web.dto;

import com.flowboard.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Edit a card. The body is a complete snapshot of the editable fields (the edit form submits the whole card),
 * so null means "clear it" (description, assigneeId and dueDate can be removed), not "leave unchanged".
 */
public record UpdateTaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        Long assigneeId,
        @NotNull Priority priority,
        LocalDate dueDate
) {
}
