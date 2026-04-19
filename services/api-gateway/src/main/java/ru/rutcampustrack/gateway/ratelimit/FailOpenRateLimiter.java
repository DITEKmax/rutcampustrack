package ru.rutcampustrack.gateway.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.AbstractRateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * M03a NEW-9: fail-open обёртка над {@link RedisRateLimiter}.
 *
 * <p>При недоступности Redis (connection refused, timeout, system error) возвращает
 * {@code allowed=true} с WARN-логом — запрос проходит. Нормальные 429-отказы от Lua-скрипта
 * Redis идут без изменений.</p>
 *
 * <p>Регистрируется как {@code @Primary} — Spring Cloud Gateway
 * {@code RequestRateLimiterGatewayFilterFactory} по-умолчанию резолвит {@code RateLimiter}
 * в контексте, поэтому все роуты с фильтром {@code RequestRateLimiter} получают fail-open.
 * Именные ссылки через {@code rate-limiter: "#{@redisRateLimiter}"} в application.yml
 * всё ещё работают с underlying реализацией (без fail-open — для тестов CRITICAL-path).</p>
 */
public class FailOpenRateLimiter extends AbstractRateLimiter<RedisRateLimiter.Config> {

    private static final Logger log = LoggerFactory.getLogger(FailOpenRateLimiter.class);
    private static final String CONFIGURATION_PROPERTY_NAME = "redis-rate-limiter";

    private final RedisRateLimiter delegate;

    public FailOpenRateLimiter(RedisRateLimiter delegate, ConfigurationService configurationService) {
        super(RedisRateLimiter.Config.class, CONFIGURATION_PROPERTY_NAME, configurationService);
        this.delegate = delegate;
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        return delegate.isAllowed(routeId, id)
                .onErrorResume(this::isRedisUnavailable, ex -> {
                    log.warn("Rate-limiter fail-open: Redis unavailable for route={} key={}: {}",
                            routeId, id, ex.toString());
                    return Mono.just(new Response(true, Map.of("X-RateLimit-FailOpen", "true")));
                });
    }

    @Override
    public Map<String, RedisRateLimiter.Config> getConfig() {
        return delegate.getConfig();
    }

    private boolean isRedisUnavailable(Throwable ex) {
        return ex instanceof RedisConnectionFailureException
                || ex instanceof QueryTimeoutException
                || ex instanceof RedisSystemException
                || ex instanceof io.lettuce.core.RedisConnectionException
                || ex instanceof io.lettuce.core.RedisCommandTimeoutException;
    }
}
