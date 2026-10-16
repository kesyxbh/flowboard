package com.flowboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The application's source of "now". Code that needs the current date asks for this Clock instead of calling
 * LocalDate.now() directly, so tests can replace it with a fixed clock and assert exact dates.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
