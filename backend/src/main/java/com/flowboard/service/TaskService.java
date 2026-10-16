package com.flowboard.service;

import com.flowboard.domain.BoardColumn;
import com.flowboard.domain.Priority;
import com.flowboard.domain.Task;
import com.flowboard.domain.User;
import com.flowboard.repository.BoardColumnRepository;
import com.flowboard.repository.TaskRepository;
import com.flowboard.web.dto.BulkCreateTasksRequest;
import com.flowboard.web.dto.CreateTaskRequest;
import com.flowboard.web.dto.MoveTaskRequest;
import com.flowboard.web.dto.NewTaskItem;
import com.flowboard.web.dto.TaskResponse;
import com.flowboard.web.dto.UpdateTaskRequest;
import com.flowboard.web.error.BadRequestException;
import com.flowboard.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * All card operations. Every method takes the id of the acting user and only touches cards that sit on a board
 * that user owns, so it does not matter whether the caller is a REST controller or an AI tool.
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final BoardColumnRepository columnRepository;

    public TaskService(TaskRepository taskRepository, BoardColumnRepository columnRepository) {
        this.taskRepository = taskRepository;
        this.columnRepository = columnRepository;
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long userId, Long taskId) {
        return TaskResponse.from(requireOwnedTask(userId, taskId));
    }

    @Transactional
    public TaskResponse createTask(Long userId, CreateTaskRequest request) {
        BoardColumn column = requireOwnedColumn(userId, request.columnId());
        User assignee = resolveAssignee(column, request.assigneeId());
        Priority priority = request.priority() == null ? Priority.MEDIUM : request.priority();
        // A new card is appended to the end of its column.
        int nextIndex = taskRepository.findMaxOrderIndex(column.getId()) + 1;

        Task task = taskRepository.save(new Task(column, request.title().trim(), request.description(),
                assignee, priority, request.dueDate(), nextIndex));
        return TaskResponse.from(task);
    }

    /** Creates several cards at the end of one column in a single transaction: either all exist afterwards or none. */
    @Transactional
    public List<TaskResponse> createTasks(Long userId, BulkCreateTasksRequest request) {
        BoardColumn column = requireOwnedColumn(userId, request.columnId());
        int nextIndex = taskRepository.findMaxOrderIndex(column.getId()) + 1;

        List<TaskResponse> created = new ArrayList<>();
        for (NewTaskItem item : request.tasks()) {
            Priority priority = item.priority() == null ? Priority.MEDIUM : item.priority();
            Task task = taskRepository.save(new Task(column, item.title().trim(), item.description(),
                    null, priority, item.dueDate(), nextIndex++));
            created.add(TaskResponse.from(task));
        }
        return created;
    }

    @Transactional
    public TaskResponse updateTask(Long userId, Long taskId, UpdateTaskRequest request) {
        Task task = requireOwnedTask(userId, taskId);
        User assignee = resolveAssignee(task.getBoardColumn(), request.assigneeId());
        task.update(request.title().trim(), request.description(), assignee, request.priority(), request.dueDate());
        return TaskResponse.from(task);
    }

    /** Deletes a card and closes the gap it leaves, so the column's order_index values stay 0..n-1. */
    @Transactional
    public void deleteTask(Long userId, Long taskId) {
        Task task = requireOwnedTask(userId, taskId);
        Long columnId = task.getBoardColumn().getId();
        taskRepository.delete(task);
        // Make sure the DELETE has been sent, so the query below no longer returns the card.
        taskRepository.flush();
        renumber(new ArrayList<>(taskRepository.findByBoardColumnIdOrderByOrderIndexAscIdAsc(columnId)));
    }

    /**
     * Moves a card to the target column so that it ends up at position orderIndex. The same code handles
     * reordering inside one column and moving to another column:
     * <ol>
     *   <li>Take the target column's cards in order and remove the moving card from that list (it is in it
     *       when the move stays inside one column).</li>
     *   <li>Insert the card at orderIndex; an index past the end means "last".</li>
     *   <li>Renumber the target column 0..n-1.</li>
     *   <li>If the card changed column, renumber the source column too, so it has no gap.</li>
     * </ol>
     * Everything happens in one transaction. Hibernate only writes the rows whose order_index really changed.
     */
    @Transactional
    public TaskResponse moveTask(Long userId, Long taskId, MoveTaskRequest request) {
        Task task = requireOwnedTask(userId, taskId);
        BoardColumn source = task.getBoardColumn();
        BoardColumn target = requireOwnedColumn(userId, request.targetColumnId());
        if (!source.getBoard().getId().equals(target.getBoard().getId())) {
            throw new BadRequestException("A task cannot be moved to a column of another board");
        }

        List<Task> targetTasks = new ArrayList<>(
                taskRepository.findByBoardColumnIdOrderByOrderIndexAscIdAsc(target.getId()));
        targetTasks.removeIf(other -> other.getId().equals(task.getId()));
        int position = Math.min(request.orderIndex(), targetTasks.size());
        targetTasks.add(position, task);
        task.moveTo(target, position);
        renumber(targetTasks);

        if (!source.getId().equals(target.getId())) {
            List<Task> sourceTasks = new ArrayList<>(
                    taskRepository.findByBoardColumnIdOrderByOrderIndexAscIdAsc(source.getId()));
            sourceTasks.removeIf(other -> other.getId().equals(task.getId()));
            renumber(sourceTasks);
        }
        return TaskResponse.from(task);
    }

    private static void renumber(List<Task> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setOrderIndex(i);
        }
    }

    // A board has no members yet, so the board owner is the only person a card can be assigned to.
    private User resolveAssignee(BoardColumn column, Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        User owner = column.getBoard().getOwner();
        if (!owner.getId().equals(assigneeId)) {
            throw new BadRequestException("Tasks can only be assigned to the board owner (boards have no members yet)");
        }
        return owner;
    }

    private BoardColumn requireOwnedColumn(Long userId, Long columnId) {
        return columnRepository.findOwned(columnId, userId)
                .orElseThrow(() -> new NotFoundException("Column not found"));
    }

    private Task requireOwnedTask(Long userId, Long taskId) {
        return taskRepository.findOwned(taskId, userId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
    }
}
