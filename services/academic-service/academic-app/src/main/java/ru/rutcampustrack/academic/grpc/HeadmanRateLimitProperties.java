package ru.rutcampustrack.academic.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * M16 G7 — конфиг для rate-limit на gRPC isHeadman вызовы.
 *
 * <p>Permin (default 300, было 120 в M06 G8b): legacy старосты с активным
 * bulk-mark (отметка всей группы 30 студентов) делают burst внутренних
 * isHeadman проверок. M15 staging показал что 120 не хватает — старосты
 * упирались в лимит и retry'или. Bumped до 300 = ~5 req/sec sustained,
 * с запасом × 6 (~50 burst checks/sec при bulk-mark группы).
 *
 * <p>Window 60s — стандартное минутное окно. Можно tunable через env
 * (`ACADEMIC_HEADMAN_RL_PER_MINUTE`) для quick mitigation в случае abuse.
 */
@ConfigurationProperties(prefix = "academic.headman-rate-limit")
public record HeadmanRateLimitProperties(
        int perMinute,
        int windowSeconds
) {
    public HeadmanRateLimitProperties {
        if (perMinute <= 0) {
            throw new IllegalArgumentException("perMinute must be > 0");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be > 0");
        }
    }
}
