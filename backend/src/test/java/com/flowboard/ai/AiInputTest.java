package com.flowboard.ai;

import com.flowboard.domain.Priority;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The checks applied to values that come from a language model. Plain unit tests: no Spring, no model. */
class AiInputTest {

    @Test
    void titleMustBePresentAndNotTooLong() {
        assertThat(AiInput.requireTitle("  Write tests  ")).isEqualTo("Write tests");
        assertThatThrownBy(() -> AiInput.requireTitle("   ")).hasMessageContaining("blank");
        assertThatThrownBy(() -> AiInput.requireTitle(null)).hasMessageContaining("blank");
        assertThatThrownBy(() -> AiInput.requireTitle("x".repeat(201))).hasMessageContaining("too long");
        assertThat(AiInput.requireTitle("x".repeat(200))).hasSize(200);
    }

    @Test
    void descriptionIsOptionalButLimited() {
        assertThat(AiInput.optionalDescription(null)).isNull();
        assertThat(AiInput.optionalDescription("   ")).isNull();
        assertThat(AiInput.optionalDescription(" details ")).isEqualTo("details");
        assertThatThrownBy(() -> AiInput.optionalDescription("x".repeat(2001))).hasMessageContaining("too long");
    }

    @Test
    void priorityIsCaseInsensitiveAndRejectsUnknownValues() {
        assertThat(AiInput.parsePriority("high")).isEqualTo(Priority.HIGH);
        assertThat(AiInput.parsePriority(" Medium ")).isEqualTo(Priority.MEDIUM);
        assertThat(AiInput.parsePriority(null)).isNull();
        assertThat(AiInput.parsePriority("")).isNull();
        assertThatThrownBy(() -> AiInput.parsePriority("urgent")).hasMessageContaining("LOW, MEDIUM or HIGH");
    }

    @Test
    void dateMustBeIso() {
        assertThat(AiInput.parseDate("2026-10-14")).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(AiInput.parseDate(null)).isNull();
        assertThatThrownBy(() -> AiInput.parseDate("14/10/2026")).hasMessageContaining("ISO date");
        assertThatThrownBy(() -> AiInput.parseDate("tomorrow")).hasMessageContaining("ISO date");
        assertThatThrownBy(() -> AiInput.parseDate("2026-02-30")).hasMessageContaining("ISO date");
    }

    @Test
    void abbreviateKeepsShortTextAndMarksCutText() {
        assertThat(AiInput.abbreviate("short", 10)).isEqualTo("short");
        String cut = AiInput.abbreviate("a".repeat(50), 10);
        assertThat(cut).hasSize(10).endsWith("...");
    }
}
