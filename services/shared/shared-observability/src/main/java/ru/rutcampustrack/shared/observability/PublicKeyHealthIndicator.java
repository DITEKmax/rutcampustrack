package ru.rutcampustrack.shared.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * KI-4 (M03b backlog) — readiness gate для публичного ключа internal-JWT.
 * Если api-gateway не подгрузил RSA public key из auth-service, validator
 * не сможет проверить ни одного входящего запроса → health должен сигнализировать
 * {@code DOWN} → docker compose рестартит контейнер либо load-balancer
 * исключает из ротации.
 *
 * <p>Сервис регистрирует bean (обычно в api-gateway):
 * <pre>{@code
 * @Bean
 * HealthIndicator publicKeyHealthIndicator(PublicKeyProvider provider) {
 *     return new PublicKeyHealthIndicator(provider::isReady);
 * }
 * }</pre>
 *
 * <p>Дизайн: принимает {@link BooleanSupplier} вместо конкретного класса,
 * чтобы shared-модуль не зависел от {@code shared-security}. Сервис вяжет
 * через лямбду.
 */
public class PublicKeyHealthIndicator implements HealthIndicator {

    private final BooleanSupplier readinessProbe;
    private final String keyId;

    public PublicKeyHealthIndicator(BooleanSupplier readinessProbe) {
        this(readinessProbe, "internal-jwt");
    }

    public PublicKeyHealthIndicator(BooleanSupplier readinessProbe, String keyId) {
        this.readinessProbe = Objects.requireNonNull(readinessProbe, "readinessProbe");
        this.keyId = Objects.requireNonNull(keyId, "keyId");
    }

    @Override
    public Health health() {
        boolean ready;
        try {
            ready = readinessProbe.getAsBoolean();
        } catch (RuntimeException e) {
            return Health.down(e).withDetail("keyId", keyId).build();
        }
        return (ready ? Health.up() : Health.down())
                .withDetail("keyId", keyId)
                .withDetail("ready", ready)
                .build();
    }
}
