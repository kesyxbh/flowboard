package com.flowboard.repository;

import com.flowboard.domain.BoardColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {

    List<BoardColumn> findByBoardIdOrderByOrderIndexAscIdAsc(Long boardId);

    // Returns -1 when the board has no columns yet, so "+ 1" gives 0 for the first column.
    @Query("select coalesce(max(c.orderIndex), -1) from BoardColumn c where c.board.id = :boardId")
    int findMaxOrderIndex(@Param("boardId") Long boardId);

    // The column, but only if it belongs to a board owned by the given user.
    @Query("select c from BoardColumn c where c.id = :columnId and c.board.owner.id = :userId")
    Optional<BoardColumn> findOwned(@Param("columnId") Long columnId, @Param("userId") Long userId);
}
