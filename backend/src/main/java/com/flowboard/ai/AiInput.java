package com.flowboard.ai;

import com.flowboard.domain.Priority;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Checks values that came from a language model. They are untrusted input, exactly like values from a browser,
 * but they reach the services without passing through the controllers' Bean Validation - so the same limits
 * the REST API enforces (title 200 characters, description 2000, known priorities, ISO dates) are re-checked here.
 * A violation is reported with an IllegalArgumentException whose message is safe to show to the user or the model.
 */
final class AiInput {

    static final int TITLE_MAX = 200;
    static final int DESCRIPTION_MAX = 2000;

    private AiInput() {
    }

    /** A title that is present and within the length limit. */
    static String requireTitle(String raw) {
        String title = raw == null ? "" : raw.trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > TITLE_MAX) {
            throw new IllegalArgumentException("title is too long (maximum " + TITLE_MAX + " characters)");
        }
        return title;
    }

    /** A description within the length limit, or null when it is blank. */
    static String optionalDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String description = raw.trim();
        if (description.length() > DESCRIPTION_MAX) {
            throw new IllegalArgumentException("description is too long (maximum " + DESCRIPTION_MAX + " characters)");
        }
        return description;
    }

    /** LOW, MEDIUM or HIGH (any letter case), or null when absent. */
    static Priority parsePriority(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Priority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("priority must be LOW, MEDIUM or HIGH");
        }
    }

    /** An ISO date such as 2026-10-14, or null when absent. */
    static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("dueDate must be an ISO date such as 2026-10-14");
        }
    }

    /** Cuts text to at most max characters, ending with "..." when something was removed. */
    static String abbreviate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 3).trim() + "...";
    }
}
