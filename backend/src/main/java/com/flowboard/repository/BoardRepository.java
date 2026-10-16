package com.flowboard.repository;

import com.flowboard.domain.Board;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

    List<Board> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

    // Looking up by id AND owner means another user's board is simply "not found" for the caller,
    // so it cannot be read or modified by guessing ids.
    Optional<Board> findByIdAndOwnerId(Long id, Long ownerId);
}
