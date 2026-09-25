package ru.rutcampustrack.gateway.security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.rutcampustrack.gateway.clientip.TrustedClientIpResolver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/** Fail-closed public request budget, applied before ticket redemption reaches Auth. */
@Component
public final class ReportDownloadAttemptRateLimiter {

    static final int MAX_ATTEMPTS_PER_CLIENT = 20;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private static final String REDIS_PREFIX = "report_download_attempt:";
    private static final String INCREMENT_WITH_LIMIT_SCRIPT = """
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local maximum = tonumber(ARGV[1])
            if current >= maximum then
                return 0
            end
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return count
            """;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> incrementWithLimitScript =
            new DefaultRedisScript<>(INCREMENT_WITH_LIMIT_SCRIPT, Long.class);

    public ReportDownloadAttemptRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> tryAcquire(String clientIp) {
        String canonicalIp = TrustedClientIpResolver.canonicalizeLiteral(clientIp)
                .orElse(TrustedClientIpResolver.UNKNOWN);
        String key = REDIS_PREFIX + digest(canonicalIp);
        return redisTemplate.execute(incrementWithLimitScript, List.of(key),
                        Integer.toString(MAX_ATTEMPTS_PER_CLIENT), Long.toString(WINDOW.toMillis()))
                .next()
                .switchIfEmpty(Mono.error(new AttemptBudgetUnavailableException()))
                .map(count -> count > 0)
                .onErrorMap(error -> error instanceof AttemptBudgetUnavailableException
                        ? error : new AttemptBudgetUnavailableException(error));
    }

    private static String digest(String clientIp) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(clientIp.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static final class AttemptBudgetUnavailableException extends RuntimeException {
        AttemptBudgetUnavailableException() {
            super("Report download attempt budget is unavailable");
        }

        AttemptBudgetUnavailableException(Throwable cause) {
            super("Report download attempt budget is unavailable", cause);
        }
    }
}
