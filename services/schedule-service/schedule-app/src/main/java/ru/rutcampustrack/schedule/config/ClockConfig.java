package ru.rutcampustrack.schedule.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Provides a Clock bean for Moscow timezone.
 * Inject Clock (not LocalTime.now()) into any service that compares lesson times
 * so tests can override with Clock.fixed(...) via @MockitoBean.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Moscow"));
    }
}
