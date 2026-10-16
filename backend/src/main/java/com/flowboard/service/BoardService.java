package com.flowboard.service;

import com.flowboard.domain.Board;
import com.flowboard.domain.BoardColumn;
import com.flowboard.repository.BoardColumnRepository;
import com.flowboard.repository.BoardRepository;
import com.flowboard.repository.TaskRepository;
import com.flowboard.repository.UserRepository;
import com.flowboard.web.dto.BoardDetailResponse;
import com.flowboard.web.dto.BoardSummaryResponse;
import com.flowboard.web.dto.ColumnResponse;
import com.flowboard.web.dto.CreateBoardRequest;
import com.flowboard.web.dto.CreateColumnRequest;
import com.flowboard.web.dto.TaskResponse;
import com.flowboard.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BoardService {

    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public BoardService(BoardRepository boardRepository, BoardColumnRepository columnRepository,
                        TaskRepository taskRepository, UserRepository userRepository) {
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BoardSummaryResponse createBoard(Long userId, CreateBoardRequest request) {
        // getReferenceById returns a lazy proxy: enough to set the foreign key, without loading the user row.
        Board board = boardRepository.save(new Board(request.name().trim(), userRepository.getReferenceById(userId)));
        return BoardSummaryResponse.from(board);
    }

    @Transactional(readOnly = true)
    public List<BoardSummaryResponse> listBoards(Long userId) {
        return boardRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(BoardSummaryResponse::from)
                .toList();
    }

    /** The whole board in a fixed number of queries (board, columns, tasks), however many cards it has. */
    @Transactional(readOnly = true)
    public BoardDetailResponse getBoard(Long userId, Long boardId) {
        Board board = requireOwnedBoard(userId, boardId);
        List<BoardColumn> columns = columnRepository.findByBoardIdOrderByOrderIndexAscIdAsc(boardId);
        Map<Long, List<TaskResponse>> tasksByColumn = taskRepository.findByBoardId(boardId).stream()
                .map(TaskResponse::from)
                .collect(Collectors.groupingBy(TaskResponse::columnId));

        List<ColumnResponse> columnResponses = columns.stream()
                .map(column -> ColumnResponse.from(column, tasksByColumn.getOrDefault(column.getId(), List.of())))
                .toList();
        return new BoardDetailResponse(board.getId(), board.getName(), columnResponses);
    }

    @Transactional
    public ColumnResponse addColumn(Long userId, Long boardId, CreateColumnRequest request) {
        Board board = requireOwnedBoard(userId, boardId);
        int nextIndex = columnRepository.findMaxOrderIndex(boardId) + 1;
        BoardColumn column = columnRepository.save(new BoardColumn(board, request.name().trim(), nextIndex));
        return ColumnResponse.from(column, List.of());
    }

    private Board requireOwnedBoard(Long userId, Long boardId) {
        return boardRepository.findByIdAndOwnerId(boardId, userId)
                .orElseThrow(() -> new NotFoundException("Board not found"));
    }
}
