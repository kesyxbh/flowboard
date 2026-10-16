package com.flowboard.ai;

import com.flowboard.domain.Priority;
import com.flowboard.web.dto.BoardDetailResponse;
import com.flowboard.web.dto.ColumnResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** How a model's draft is validated against a real board. No Spring and no model involved. */
class TaskDraftResolveTest {

    private final BoardDetailResponse board = new BoardDetailResponse(7L, "Sprint", List.of(
            new ColumnResponse(11L, 7L, "To do", 0, List.of()),
            new ColumnResponse(12L, 7L, "Doing", 1, List.of())));

    @Test
    void aCompleteDraftIsAcceptedAsIs() {
        TaskDraftService.Resolved resolved = TaskDraftService.resolve(
                new TaskDraft("Fix login bug", "see ticket", "HIGH", "2026-10-16", "Doing"), board);

        assertThat(resolved.title()).isEqualTo("Fix login bug");
        assertThat(resolved.description()).isEqualTo("see ticket");
        assertThat(resolved.priority()).isEqualTo(Priority.HIGH);
        assertThat(resolved.dueDate()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(resolved.columnId()).isEqualTo(12L);
        assertThat(resolved.warnings()).isEmpty();
    }

    @Test
    void columnNamesAreMatchedIgnoringCaseAndSpaces() {
        TaskDraftService.Resolved resolved = TaskDraftService.resolve(
                new TaskDraft("x", null, null, null, "  dOiNg "), board);

        assertThat(resolved.columnId()).isEqualTo(12L);
        assertThat(resolved.warnings()).isEmpty();
    }

    @Test
    void missingOptionalFieldsUseDefaultsWithoutWarnings() {
        TaskDraftService.Resolved resolved = TaskDraftService.resolve(
                new TaskDraft("Just a title", null, null, null, null), board);

        assertThat(resolved.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(resolved.dueDate()).isNull();
        assertThat(resolved.columnId()).isEqualTo(11L); // the leftmost column
        assertThat(resolved.warnings()).isEmpty();
    }

    @Test
    void anUnknownColumnFallsBackToTheFirstOneWithAWarning() {
        TaskDraftService.Resolved resolved = TaskDraftService.resolve(
                new TaskDraft("x", null, null, null, "Backlog"), board);

        assertThat(resolved.columnId()).isEqualTo(11L);
        assertThat(resolved.warnings()).singleElement().asString().contains("Backlog").contains("To do");
    }

    @Test
    void anUnreadableDateAndAnUnknownPriorityAreDroppedWithWarnings() {
        TaskDraftService.Resolved resolved = TaskDraftService.resolve(
                new TaskDraft("x", null, "whenever", "next week", null), board);

        assertThat(resolved.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(resolved.dueDate()).isNull();
        assertThat(resolved.warnings()).hasSize(2);
    }

    @Test
    void aTooLongTitleIsShortenedWithAWarning() {
        TaskDraftService.Resolved resolved = TaskDraftService.resolve(
                new TaskDraft("t".repeat(300), null, null, null, null), board);

        assertThat(resolved.title()).hasSize(200).endsWith("...");
        assertThat(resolved.warnings()).hasSize(1);
    }

    @Test
    void aMissingTitleIsTheOnlyFatalProblem() {
        assertThatThrownBy(() -> TaskDraftService.resolve(new TaskDraft("  ", null, null, null, null), board))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> TaskDraftService.resolve(null, board))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void theExplanationIsBuiltFromTheValidatedValues() {
        TaskDraftService.Resolved resolved = TaskDraftService.resolve(
                new TaskDraft("Ship it", null, "low", "2026-11-01", "to do"), board);

        assertThat(TaskDraftService.explain(resolved))
                .isEqualTo("Added \"Ship it\" to To do, priority LOW, due 2026-11-01.");
    }
}
