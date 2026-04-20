package ru.rutcampustrack.attendance.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.rutcampustrack.shared.observability.BusinessMetrics;

/**
 * M04 Группа 8 — регистрация {@link BusinessMetrics} для attendance-service.
 * Используется в CheckinService, ExcuseService, LateCheckinService для
 * счётчиков {@code attendance.checkin}, {@code excuse.created},
 * {@code late_checkin.created}.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public BusinessMetrics businessMetrics(MeterRegistry registry) {
        return new BusinessMetrics(registry);
    }
}
