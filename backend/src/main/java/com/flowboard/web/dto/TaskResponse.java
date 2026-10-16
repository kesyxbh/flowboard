package com.flowboard.web.dto;

import com.flowboard.domain.Priority;
import com.flowboard.domain.Task;

import java.time.LocalDate;

public record TaskResponse(
        Long id,
        Long columnId,
        String title,
        String description,
        Long assigneeId,
        Priority priority,
        LocalDate dueDate,
        int orderIndex
) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getBoardColumn().getId(),
                task.getTitle(),
                task.getDescription(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getPriority(),
                task.getDueDate(),
                task.getOrderIndex());
    }
}
