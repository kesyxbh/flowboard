package com.flowboard.ai;

import com.flowboard.web.dto.SubtaskSuggestion;

import java.util.List;

/** What the model returns when asked to split a card (structured output). Untrusted until validated. */
public record SplitDraft(List<SubtaskSuggestion> subtasks) {
}
