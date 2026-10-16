package com.flowboard.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Create several cards at the end of one column, all or nothing (one transaction). */
public record BulkCreateTasksRequest(
        @NotNull Long columnId,
        @NotEmpty @Size(max = 20) List<@Valid NewTaskItem> tasks
) {
}
