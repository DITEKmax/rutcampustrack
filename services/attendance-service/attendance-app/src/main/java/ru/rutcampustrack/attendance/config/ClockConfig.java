package ru.rutcampustrack.attendance.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.rutcampustrack.shared.web.time.EducationalTime;

/**
 * Clock bean для Europe/Moscow timezone (04 P2-4, M08 Группа 4).
 *
 * <p>Инжектится вместо хардкода {@code ZoneId.of("Europe/Moscow") + Instant.now()} в хот-пэтах:
 *
 * <ul>
 *   <li>{@code CheckinService} — checkin window validation
 *   <li>{@code LateCheckinService} — 15-min deadline calculation
 *   <li>{@code ExcuseService} — approval/rejection timestamps
 * </ul>
 *
 * <p>Тесты делают {@code @MockitoBean Clock clock;
 * when(clock.instant()).thenReturn(Instant.parse("2026-04-22T10:00:00Z"));} или используют
 * {@code @TestConfiguration} с {@code Clock.fixed(...)} для детерминизма временных window'ов.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Moscow"));
    }

    @Bean
    public EducationalTime educationalTime(Clock realClock) {
        return new EducationalTime(realClock);
    }
}
