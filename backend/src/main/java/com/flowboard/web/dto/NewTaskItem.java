package com.flowboard.web.dto;

import com.flowboard.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** One card to create as part of a bulk request. Same rules as CreateTaskRequest, minus the column. */
public record NewTaskItem(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        Priority priority,      // defaults to MEDIUM when omitted
        LocalDate dueDate
) {
}
