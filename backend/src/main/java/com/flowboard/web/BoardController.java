package com.flowboard.web;

import com.flowboard.security.AuthUser;
import com.flowboard.service.BoardService;
import com.flowboard.web.dto.BoardDetailResponse;
import com.flowboard.web.dto.BoardSummaryResponse;
import com.flowboard.web.dto.ColumnResponse;
import com.flowboard.web.dto.CreateBoardRequest;
import com.flowboard.web.dto.CreateColumnRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/boards")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardSummaryResponse create(@AuthenticationPrincipal AuthUser user,
                                       @Valid @RequestBody CreateBoardRequest request) {
        return boardService.createBoard(user.id(), request);
    }

    @GetMapping
    public List<BoardSummaryResponse> list(@AuthenticationPrincipal AuthUser user) {
        return boardService.listBoards(user.id());
    }

    @GetMapping("/{boardId}")
    public BoardDetailResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long boardId) {
        return boardService.getBoard(user.id(), boardId);
    }

    @PostMapping("/{boardId}/columns")
    @ResponseStatus(HttpStatus.CREATED)
    public ColumnResponse addColumn(@AuthenticationPrincipal AuthUser user, @PathVariable Long boardId,
                                    @Valid @RequestBody CreateColumnRequest request) {
        return boardService.addColumn(user.id(), boardId, request);
    }
}
