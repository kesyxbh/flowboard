package com.flowboard.ai;

import com.flowboard.web.dto.SubtaskSuggestion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** What survives from a model's list of sub-task suggestions. No Spring and no model involved. */
class TaskSplitSanitizeTest {

    private static SubtaskSuggestion s(String title) {
        return new SubtaskSuggestion(title, null);
    }

    @Test
    void blankDuplicateAndParentTitlesAreDropped() {
        SplitDraft raw = new SplitDraft(List.of(
                s("Design the page"), s("   "), s("design the PAGE"), s("Launch the site"), s("Write the copy")));

        List<SubtaskSuggestion> clean = TaskSplitService.sanitize(raw, "Launch the site");

        assertThat(clean).extracting(SubtaskSuggestion::title).containsExactly("Design the page", "Write the copy");
    }

    @Test
    void atMostSixSuggestionsAreKept() {
        List<SubtaskSuggestion> many = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            many.add(s("Step " + i));
        }

        List<SubtaskSuggestion> clean = TaskSplitService.sanitize(new SplitDraft(many), "Parent");

        assertThat(clean).hasSize(TaskSplitService.MAX_SUBTASKS);
        assertThat(clean.get(0).title()).isEqualTo("Step 1");
    }

    @Test
    void textIsTrimmedAndLongValuesAreCut() {
        SplitDraft raw = new SplitDraft(List.of(
                new SubtaskSuggestion("  " + "t".repeat(250) + "  ", "  " + "d".repeat(2500)),
                new SubtaskSuggestion("Second", "   ")));

        List<SubtaskSuggestion> clean = TaskSplitService.sanitize(raw, "Parent");

        assertThat(clean.get(0).title()).hasSize(200);
        assertThat(clean.get(0).description()).hasSize(2000);
        assertThat(clean.get(1).description()).isNull();
    }

    @Test
    void aMissingOrEmptyResultGivesAnEmptyList() {
        assertThat(TaskSplitService.sanitize(null, "Parent")).isEmpty();
        assertThat(TaskSplitService.sanitize(new SplitDraft(null), "Parent")).isEmpty();
        assertThat(TaskSplitService.sanitize(new SplitDraft(new ArrayList<>()), "Parent")).isEmpty();
    }
}
