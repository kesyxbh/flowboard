package com.flowboard.web.dto;

import java.util.List;

/** A whole board: columns sorted left to right, and inside each column the cards sorted top to bottom. */
public record BoardDetailResponse(Long id, String name, List<ColumnResponse> columns) {
}
