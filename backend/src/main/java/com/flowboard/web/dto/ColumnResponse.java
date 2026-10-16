package com.flowboard.web.dto;

import com.flowboard.domain.BoardColumn;

import java.util.List;

public record ColumnResponse(Long id, Long boardId, String name, int orderIndex, List<TaskResponse> tasks) {

    public static ColumnResponse from(BoardColumn column, List<TaskResponse> tasks) {
        return new ColumnResponse(column.getId(), column.getBoard().getId(), column.getName(),
                column.getOrderIndex(), tasks);
    }
}
