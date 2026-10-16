package com.flowboard.ai;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Decides what "today" is for a request. A model cannot know the date, so every prompt that involves relative
 * dates ("tomorrow", "next Friday") states it explicitly - and it must be the user's today, not the server's:
 * around midnight the two can be on different days.
 *
 * <p>The client sends its IANA time zone (the browser knows it). A missing or unknown zone falls back to the
 * server's default zone instead of failing the request.
 */
@Component
public class AiTime {

    private final Clock clock;

    public AiTime(Clock clock) {
        this.clock = clock;
    }

    public ZoneId zone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return clock.getZone();
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException e) {
            return clock.getZone();
        }
    }

    public ZonedDateTime now(String timeZone) {
        return ZonedDateTime.now(clock.withZone(zone(timeZone)));
    }

    public LocalDate today(String timeZone) {
        return LocalDate.now(clock.withZone(zone(timeZone)));
    }

    /**
     * The weekday name as it goes into a prompt. The locale is fixed to English on purpose: the default
     * formatting follows the JVM's language, so the same code would put "Wednesday" into the prompt on one
     * machine and a translated weekday on another.
     */
    public static String weekday(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
