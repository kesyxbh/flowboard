package com.flowboard.repository;

import com.flowboard.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // The id is a tie-breaker so the order is deterministic even if two cards ever share an order_index.
    List<Task> findByBoardColumnIdOrderByOrderIndexAscIdAsc(Long columnId);

    // All cards of a board in one query (instead of one query per column), grouped by column in memory later.
    @Query("select t from Task t where t.boardColumn.board.id = :boardId order by t.orderIndex, t.id")
    List<Task> findByBoardId(@Param("boardId") Long boardId);

    // Returns -1 when the column is empty, so "+ 1" gives 0 for the first card.
    @Query("select coalesce(max(t.orderIndex), -1) from Task t where t.boardColumn.id = :columnId")
    int findMaxOrderIndex(@Param("columnId") Long columnId);

    // The task, but only if it sits on a board owned by the given user.
    @Query("select t from Task t where t.id = :taskId and t.boardColumn.board.owner.id = :userId")
    Optional<Task> findOwned(@Param("taskId") Long taskId, @Param("userId") Long userId);
}
