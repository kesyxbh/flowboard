package com.flowboard.web.dto;

import com.flowboard.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTaskRequest(
        @NotNull Long columnId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        Long assigneeId,
        Priority priority,      // defaults to MEDIUM when omitted
        LocalDate dueDate
) {
}
