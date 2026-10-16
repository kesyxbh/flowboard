package com.flowboard.ai;

import com.flowboard.service.TaskService;
import com.flowboard.web.dto.SplitResponse;
import com.flowboard.web.dto.SubtaskSuggestion;
import com.flowboard.web.dto.TaskResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Task splitting: asks the model for sub-tasks of a card and returns them as a PREVIEW. Nothing is created
 * here - the user looks at the suggestions first and creates the ones they want with POST /api/tasks/bulk.
 * Keeping the human in the loop is deliberate: a model suggestion should never change the board by itself.
 */
@Service
public class TaskSplitService {

    private static final Logger log = LoggerFactory.getLogger(TaskSplitService.class);

    static final int MAX_SUBTASKS = 6;

    private final ChatClient chatClient;
    private final AiAvailability availability;
    private final TaskService taskService;

    public TaskSplitService(ChatClient.Builder chatClientBuilder, AiAvailability availability, TaskService taskService) {
        this.chatClient = chatClientBuilder.build();
        this.availability = availability;
        this.taskService = taskService;
    }

    public SplitResponse split(Long userId, Long taskId) {
        availability.requireConfigured();
        // Throws NotFoundException (404) when the card does not exist or is on someone else's board.
        TaskResponse task = taskService.getTask(userId, taskId);

        SplitDraft raw;
        try {
            raw = chatClient.prompt()
                    .system(systemPrompt(task))
                    .user("Split this card into sub-tasks.")
                    .call()
                    .entity(SplitDraft.class);
        } catch (RuntimeException e) {
            log.error("LLM request failed while splitting task {}", taskId, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI provider request failed. Check the API key, base URL and model name.");
        }

        List<SubtaskSuggestion> subtasks = sanitize(raw, task.title());
        if (subtasks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The model did not return usable sub-tasks. Please try again.");
        }
        return new SplitResponse(task.id(), task.columnId(), subtasks,
                "Suggested " + subtasks.size() + " sub-tasks for \"" + task.title()
                        + "\". Nothing has been created yet.");
    }

    /**
     * Keeps only usable suggestions: non-blank titles within the length limit, no duplicates, nothing that merely
     * repeats the parent card, at most MAX_SUBTASKS. Package-private so it can be tested without a model.
     */
    static List<SubtaskSuggestion> sanitize(SplitDraft raw, String parentTitle) {
        Map<String, SubtaskSuggestion> unique = new LinkedHashMap<>();
        if (raw != null && raw.subtasks() != null) {
            for (SubtaskSuggestion candidate : raw.subtasks()) {
                if (candidate == null || candidate.title() == null || candidate.title().isBlank()) {
                    continue;
                }
                String title = AiInput.abbreviate(candidate.title().trim(), AiInput.TITLE_MAX);
                String key = title.toLowerCase(Locale.ROOT);
                if (key.equals(parentTitle.trim().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String description = candidate.description() == null || candidate.description().isBlank()
                        ? null
                        : AiInput.abbreviate(candidate.description().trim(), AiInput.DESCRIPTION_MAX);
                unique.putIfAbsent(key, new SubtaskSuggestion(title, description));
                if (unique.size() == MAX_SUBTASKS) {
                    break;
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private String systemPrompt(TaskResponse task) {
        return """
                You break one card of a kanban board into smaller cards. Respond only with the JSON object.

                The card:
                - title: %s
                - description: %s
                - priority: %s

                Produce 3 to 6 concrete sub-tasks that together complete the card.
                - Each title is a short imperative phrase of at most 80 characters.
                - Each sub-task can be finished on its own; order them in the sequence they would be done.
                - description is one short sentence, or null.
                - Do not repeat the card's own title and do not invent work that is unrelated to it.
                """.formatted(task.title(),
                task.description() == null ? "(none)" : task.description(),
                task.priority());
    }
}
