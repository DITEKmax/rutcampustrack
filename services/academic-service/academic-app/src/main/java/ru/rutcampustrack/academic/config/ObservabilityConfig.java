package ru.rutcampustrack.academic.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.rutcampustrack.shared.observability.BusinessMetrics;

/**
 * M04 Группа 8 — регистрация {@link BusinessMetrics} для academic-service.
 * Используется {@code AcademicUserContextFilter} (KI-2 internal_jwt_fallback
 * counter).
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public BusinessMetrics businessMetrics(MeterRegistry registry) {
        return new BusinessMetrics(registry);
    }
}
