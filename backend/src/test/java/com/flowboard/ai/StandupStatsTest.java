package com.flowboard.ai;

import com.flowboard.domain.Priority;
import com.flowboard.web.dto.BoardDetailResponse;
import com.flowboard.web.dto.ColumnResponse;
import com.flowboard.web.dto.StandupResponse.Stats;
import com.flowboard.web.dto.TaskResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The facts of the stand-up briefing are computed in plain Java; these tests pin the rules down. */
class StandupStatsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private static TaskResponse task(long id, String title, String due) {
        return new TaskResponse(id, 1L, title, null, null, Priority.MEDIUM, due == null ? null : LocalDate.parse(due), 0);
    }

    private static BoardDetailResponse board(ColumnResponse... columns) {
        return new BoardDetailResponse(1L, "B", List.of(columns));
    }

    private static ColumnResponse column(long id, String name, TaskResponse... tasks) {
        return new ColumnResponse(id, 1L, name, 0, List.of(tasks));
    }

    @Test
    void overdueAndDueSoonAreSortedByDateAndIgnoreFinishedColumns() {
        Stats stats = StandupService.computeStats(board(
                column(1, "To do",
                        task(1, "Due in three days", "2026-10-17"),
                        task(2, "Overdue yesterday", "2026-10-13"),
                        task(3, "Due today", "2026-10-14"),
                        task(4, "Due in four days", "2026-10-18"),
                        task(5, "No due date", null)),
                column(2, "Done",
                        task(6, "Old finished card", "2026-01-01"))), TODAY);

        assertThat(stats.totalTasks()).isEqualTo(6);
        assertThat(stats.doneTasks()).isEqualTo(1);
        assertThat(stats.overdue()).extracting(r -> r.title()).containsExactly("Overdue yesterday");
        assertThat(stats.dueSoon()).extracting(r -> r.title()).containsExactly("Due today", "Due in three days");
    }

    @Test
    void anEmptyBoardHasEmptyStats() {
        Stats stats = StandupService.computeStats(board(column(1, "To do")), TODAY);

        assertThat(stats.totalTasks()).isZero();
        assertThat(stats.doneTasks()).isZero();
        assertThat(stats.overdue()).isEmpty();
        assertThat(stats.dueSoon()).isEmpty();
    }

    @Test
    void finishedColumnsAreRecognisedByName() {
        assertThat(StandupService.isDoneColumn("Done")).isTrue();
        assertThat(StandupService.isDoneColumn("done!")).isTrue();
        assertThat(StandupService.isDoneColumn("Completed - October")).isTrue();
        assertThat(StandupService.isDoneColumn("Shipped")).isTrue();
        // "already completed" in Chinese (U+5DF2 U+5B8C U+6210), built from code points to keep this file ASCII
        assertThat(StandupService.isDoneColumn(new String(new int[] {0x5DF2, 0x5B8C, 0x6210}, 0, 3))).isTrue();
        assertThat(StandupService.isDoneColumn("Doing")).isFalse();
        assertThat(StandupService.isDoneColumn("Undone")).isFalse();
        assertThat(StandupService.isDoneColumn("To do")).isFalse();
        assertThat(StandupService.isDoneColumn(null)).isFalse();
    }
}
