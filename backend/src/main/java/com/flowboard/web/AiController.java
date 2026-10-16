package com.flowboard.web;

import com.flowboard.ai.AiAvailability;
import com.flowboard.ai.AiChatService;
import com.flowboard.ai.StandupService;
import com.flowboard.ai.TaskDraftService;
import com.flowboard.ai.TaskSplitService;
import com.flowboard.security.AuthUser;
import com.flowboard.web.dto.AiChatRequest;
import com.flowboard.web.dto.AiChatResponse;
import com.flowboard.web.dto.AiStatusResponse;
import com.flowboard.web.dto.QuickAddRequest;
import com.flowboard.web.dto.QuickAddResponse;
import com.flowboard.web.dto.SplitResponse;
import com.flowboard.web.dto.StandupRequest;
import com.flowboard.web.dto.StandupResponse;
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

/**
 * All AI endpoints require a signed-in user (SecurityConfig protects everything that is not explicitly public),
 * which also keeps anonymous visitors from spending LLM tokens.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAvailability availability;
    private final AiChatService chatService;
    private final TaskDraftService draftService;
    private final TaskSplitService splitService;
    private final StandupService standupService;

    public AiController(AiAvailability availability, AiChatService chatService, TaskDraftService draftService,
                        TaskSplitService splitService, StandupService standupService) {
        this.availability = availability;
        this.chatService = chatService;
        this.draftService = draftService;
        this.splitService = splitService;
        this.standupService = standupService;
    }

    /** Lets the UI show "AI is not configured" up front instead of discovering it on the first click. */
    @GetMapping("/status")
    public AiStatusResponse status() {
        return new AiStatusResponse(availability.isConfigured(), availability.model());
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AiChatRequest request) {
        return chatService.chat(user.id(), request);
    }

    /** One sentence in, one card out. 201 because a card is created. */
    @PostMapping("/tasks/quick-add")
    @ResponseStatus(HttpStatus.CREATED)
    public QuickAddResponse quickAdd(@AuthenticationPrincipal AuthUser user,
                                     @Valid @RequestBody QuickAddRequest request) {
        return draftService.quickAdd(user.id(), request);
    }

    /** Suggests sub-tasks for a card. Creates nothing; the client creates the chosen ones via POST /api/tasks/bulk. */
    @PostMapping("/tasks/{taskId}/split")
    public SplitResponse split(@AuthenticationPrincipal AuthUser user, @PathVariable Long taskId) {
        return splitService.split(user.id(), taskId);
    }

    @PostMapping("/boards/{boardId}/standup")
    public StandupResponse standup(@AuthenticationPrincipal AuthUser user, @PathVariable Long boardId,
                                   @Valid @RequestBody(required = false) StandupRequest request) {
        return standupService.standup(user.id(), boardId, request);
    }
}
