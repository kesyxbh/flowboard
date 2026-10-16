package com.flowboard.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * @param summary short stand-up briefing written by the model from the board data
 * @param stats   the facts the briefing was based on. They are computed by the server, not by the model, so the
 *                numbers shown next to the text are always correct
 */
public record StandupResponse(String summary, Stats stats) {

    public record Stats(int totalTasks, int doneTasks, List<TaskRef> overdue, List<TaskRef> dueSoon) {
    }

    public record TaskRef(Long id, String title, LocalDate dueDate, String columnName) {
    }
}
