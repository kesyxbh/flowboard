package com.flowboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "task", indexes = @Index(name = "idx_task_column", columnList = "column_id"))
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "column_id", nullable = false)
    private BoardColumn boardColumn;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    // Stored as text ("HIGH"), not as the enum's ordinal, so reordering the enum can never change stored data.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Column(name = "due_date")
    private LocalDate dueDate;

    // Position of the card inside its column, top to bottom, starting at 0.
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Task() {
        // required by JPA
    }

    public Task(BoardColumn boardColumn, String title, String description, User assignee,
                Priority priority, LocalDate dueDate, int orderIndex) {
        this.boardColumn = boardColumn;
        this.title = title;
        this.description = description;
        this.assignee = assignee;
        this.priority = priority;
        this.dueDate = dueDate;
        this.orderIndex = orderIndex;
    }

    /** Edits the card: overwrites every editable field. Column and position change only through {@link #moveTo}. */
    public void update(String title, String description, User assignee, Priority priority, LocalDate dueDate) {
        this.title = title;
        this.description = description;
        this.assignee = assignee;
        this.priority = priority;
        this.dueDate = dueDate;
    }

    public void moveTo(BoardColumn boardColumn, int orderIndex) {
        this.boardColumn = boardColumn;
        this.orderIndex = orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Long getId() {
        return id;
    }

    public BoardColumn getBoardColumn() {
        return boardColumn;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public User getAssignee() {
        return assignee;
    }

    public Priority getPriority() {
        return priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public int getOrderIndex() {
        return orderIndex;
    }
}
