package com.flowboard.ai;

/**
 * What the model returns when it turns one sentence into a card (structured output). Every field is plain text
 * and untrusted: TaskDraftService validates it before anything is stored.
 *
 * @param title       short card title
 * @param description extra detail, or null
 * @param priority    LOW, MEDIUM or HIGH, or null when the sentence does not say
 * @param dueDate     ISO date yyyy-MM-dd, or null
 * @param column      name of the column the user wants the card in, or null
 */
public record TaskDraft(String title, String description, String priority, String dueDate, String column) {
}
