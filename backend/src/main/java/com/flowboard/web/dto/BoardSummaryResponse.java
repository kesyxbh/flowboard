package com.flowboard.web.dto;

import com.flowboard.domain.Board;

import java.time.Instant;

public record BoardSummaryResponse(Long id, String name, Instant createdAt) {

    public static BoardSummaryResponse from(Board board) {
        return new BoardSummaryResponse(board.getId(), board.getName(), board.getCreatedAt());
    }
}
