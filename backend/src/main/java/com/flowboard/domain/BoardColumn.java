package com.flowboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// Called BoardColumn (not Column) to avoid a clash with the jakarta.persistence.Column annotation and the SQL
// keyword COLUMN.
@Entity
@Table(name = "board_column", indexes = @Index(name = "idx_board_column_board", columnList = "board_id"))
public class BoardColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Column(nullable = false, length = 100)
    private String name;

    // Position of the column on the board, left to right, starting at 0.
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    protected BoardColumn() {
        // required by JPA
    }

    public BoardColumn(Board board, String name, int orderIndex) {
        this.board = board;
        this.name = name;
        this.orderIndex = orderIndex;
    }

    public Long getId() {
        return id;
    }

    public Board getBoard() {
        return board;
    }

    public String getName() {
        return name;
    }

    public int getOrderIndex() {
        return orderIndex;
    }
}
