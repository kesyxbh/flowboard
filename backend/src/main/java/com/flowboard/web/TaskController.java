package com.flowboard.web;

import com.flowboard.security.AuthUser;
import com.flowboard.service.TaskService;
import com.flowboard.web.dto.BulkCreateTasksRequest;
import com.flowboard.web.dto.CreateTaskRequest;
import com.flowboard.web.dto.MoveTaskRequest;
import com.flowboard.web.dto.TaskResponse;
import com.flowboard.web.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@AuthenticationPrincipal AuthUser user,
                               @Valid @RequestBody CreateTaskRequest request) {
        return taskService.createTask(user.id(), request);
    }

    /** Several cards at the end of one column, all or nothing. Used to add AI-suggested sub-tasks in one click. */
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TaskResponse> createBulk(@AuthenticationPrincipal AuthUser user,
                                         @Valid @RequestBody BulkCreateTasksRequest request) {
        return taskService.createTasks(user.id(), request);
    }

    @PatchMapping("/{taskId}")
    public TaskResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long taskId,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.updateTask(user.id(), taskId, request);
    }

    @PatchMapping("/{taskId}/move")
    public TaskResponse move(@AuthenticationPrincipal AuthUser user, @PathVariable Long taskId,
                             @Valid @RequestBody MoveTaskRequest request) {
        return taskService.moveTask(user.id(), taskId, request);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long taskId) {
        taskService.deleteTask(user.id(), taskId);
    }
}
