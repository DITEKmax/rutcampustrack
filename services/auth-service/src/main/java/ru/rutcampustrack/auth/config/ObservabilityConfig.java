package ru.rutcampustrack.auth.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.rutcampustrack.shared.observability.BusinessMetrics;

/**
 * M04 Группа 8 — регистрация bean'а {@link BusinessMetrics} для
 * auth-service. Используется в {@code AuthService.login}/{@code logout},
 * {@code OtpService.request}/{@code verify} для инкремента counter'ов
 * {@code auth.login}, {@code auth.logout}, {@code otp.request},
 * {@code otp.verify}.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public BusinessMetrics businessMetrics(MeterRegistry registry) {
        return new BusinessMetrics(registry);
    }
}
