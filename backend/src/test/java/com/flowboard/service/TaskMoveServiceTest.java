package com.flowboard.service;

import com.flowboard.domain.Task;
import com.flowboard.domain.User;
import com.flowboard.repository.TaskRepository;
import com.flowboard.repository.UserRepository;
import com.flowboard.web.dto.CreateBoardRequest;
import com.flowboard.web.dto.CreateColumnRequest;
import com.flowboard.web.dto.CreateTaskRequest;
import com.flowboard.web.dto.MoveTaskRequest;
import com.flowboard.web.error.BadRequestException;
import com.flowboard.web.error.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the ordering algorithm of TaskService.moveTask directly, without HTTP. The class-level @Transactional
 * rolls every test back, so the tests cannot affect each other.
 *
 * <p>Fixture before every test: column "todo" = [a, b, c], column "doing" = [x].
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskMoveServiceTest {

    @Autowired
    UserRepository userRepository;
    @Autowired
    BoardService boardService;
    @Autowired
    TaskService taskService;
    @Autowired
    TaskRepository taskRepository;

    private Long userId;
    private Long boardId;
    private Long col1;
    private Long col2;
    private Long a;
    private Long b;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(new User("mover", "mover@example.com", "hash")).getId();
        boardId = boardService.createBoard(userId, new CreateBoardRequest("B")).id();
        col1 = boardService.addColumn(userId, boardId, new CreateColumnRequest("todo")).id();
        col2 = boardService.addColumn(userId, boardId, new CreateColumnRequest("doing")).id();
        a = createTask(userId, col1, "a");
        b = createTask(userId, col1, "b");
        createTask(userId, col1, "c");
        createTask(userId, col2, "x");
    }

    @Test
    void newTasksAreAppendedToTheEndOfTheirColumn() {
        assertThat(titles(col1)).containsExactly("a", "b", "c");
        assertThat(indexes(col1)).containsExactly(0, 1, 2);
    }

    @Test
    void reorderWithinSameColumn() {
        move(a, col1, 2);

        assertThat(titles(col1)).containsExactly("b", "c", "a");
        assertThat(indexes(col1)).containsExactly(0, 1, 2);
    }

    @Test
    void moveAcrossColumns_insertsAtPosition_andCompactsSourceColumn() {
        move(b, col2, 0);

        assertThat(titles(col2)).containsExactly("b", "x");
        assertThat(indexes(col2)).containsExactly(0, 1);
        assertThat(titles(col1)).containsExactly("a", "c");
        assertThat(indexes(col1)).containsExactly(0, 1); // no gap is left behind
    }

    @Test
    void outOfRangeIndex_isClampedToTheEnd() {
        move(a, col2, 99);

        assertThat(titles(col2)).containsExactly("x", "a");
        assertThat(indexes(col2)).containsExactly(0, 1);
        assertThat(titles(col1)).containsExactly("b", "c");
        assertThat(indexes(col1)).containsExactly(0, 1);
    }

    @Test
    void movingToTheCurrentPositionChangesNothing() {
        move(b, col1, 1);

        assertThat(titles(col1)).containsExactly("a", "b", "c");
        assertThat(indexes(col1)).containsExactly(0, 1, 2);
    }

    @Test
    void cannotTouchAnotherUsersTask() {
        Long otherUser = userRepository.save(new User("other", "other@example.com", "hash")).getId();

        assertThatThrownBy(() -> taskService.moveTask(otherUser, a, new MoveTaskRequest(col2, 0)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cannotMoveIntoAnotherBoardOfTheSameUser() {
        Long otherBoard = boardService.createBoard(userId, new CreateBoardRequest("B2")).id();
        Long foreignColumn = boardService.addColumn(userId, otherBoard, new CreateColumnRequest("elsewhere")).id();

        assertThatThrownBy(() -> move(a, foreignColumn, 0)).isInstanceOf(BadRequestException.class);
    }

    private Long createTask(Long owner, Long columnId, String title) {
        return taskService.createTask(owner, new CreateTaskRequest(columnId, title, null, null, null, null)).id();
    }

    private void move(Long taskId, Long targetColumnId, int orderIndex) {
        taskService.moveTask(userId, taskId, new MoveTaskRequest(targetColumnId, orderIndex));
    }

    private List<String> titles(Long columnId) {
        return taskRepository.findByBoardColumnIdOrderByOrderIndexAscIdAsc(columnId).stream()
                .map(Task::getTitle).toList();
    }

    private List<Integer> indexes(Long columnId) {
        return taskRepository.findByBoardColumnIdOrderByOrderIndexAscIdAsc(columnId).stream()
                .map(Task::getOrderIndex).toList();
    }
}
