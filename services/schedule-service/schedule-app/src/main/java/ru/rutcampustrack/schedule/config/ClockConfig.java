package ru.rutcampustrack.schedule.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.rutcampustrack.shared.web.time.EducationalTime;

/**
 * Provides a Clock bean for Moscow timezone. Inject Clock (not LocalTime.now()) into any service
 * that compares lesson times so tests can override with Clock.fixed(...) via @MockitoBean.
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
