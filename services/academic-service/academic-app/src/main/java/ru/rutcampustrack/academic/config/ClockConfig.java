package ru.rutcampustrack.academic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * {@link Clock} bean: позволяет инжектить время как зависимость.
 * BUG-006-6 / план 58-06: {@code GroupArchivalService} и
 * {@code GroupPromotionService} используют {@code Clock} вместо
 * {@code OffsetDateTime.now()}, что делает поведение тестируемым
 * (юнит-тесты передают {@code Clock.fixed(...)}).
 *
 * <p>Phase 61 / D-03: HomeworkService валидирует {@code lesson_date >= today}
 * относительно московского календарного дня — домен «пары/расписание» всегда
 * оперирует {@code Europe/Moscow} (CLAUDE.md §«Временные метки»), поэтому
 * Clock настроен на этот ZoneId, а не на UTC.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Moscow"));
    }
}
