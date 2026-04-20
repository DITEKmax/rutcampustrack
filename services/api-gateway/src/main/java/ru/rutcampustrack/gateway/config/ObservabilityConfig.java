package ru.rutcampustrack.gateway.config;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.rutcampustrack.shared.observability.PublicKeyHealthIndicator;

/**
 * M04 Группа 4 — регистрация health-indicators специфичных для api-gateway.
 *
 * <p>{@code publicKeyHealthIndicator} (KI-4 из M03b) — сигналит DOWN если
 * RSA public key из auth-service не подгружен. Без ключа validator не
 * сможет проверить ни одного входящего JWT → docker compose рестартит
 * контейнер либо load-balancer исключает из ротации.
 *
 * <p>Имя bean'а определяет имя поля в {@code /actuator/health}:
 * {@code components.publicKey.status}.
 */
@Configuration
public class ObservabilityConfig {

    @Bean("publicKey")
    public HealthIndicator publicKeyHealthIndicator(PublicKeyConfig publicKeyConfig) {
        return new PublicKeyHealthIndicator(publicKeyConfig::isReady);
    }
}
