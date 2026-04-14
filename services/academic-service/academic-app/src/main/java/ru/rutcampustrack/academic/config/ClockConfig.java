package ru.rutcampustrack.academic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * {@link Clock} bean: позволяет инжектить время как зависимость.
 * BUG-006-6 / план 58-06: {@code GroupArchivalService} и
 * {@code GroupPromotionService} используют {@code Clock} вместо
 * {@code OffsetDateTime.now()}, что делает поведение тестируемым
 * (юнит-тесты передают {@code Clock.fixed(...)}).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
