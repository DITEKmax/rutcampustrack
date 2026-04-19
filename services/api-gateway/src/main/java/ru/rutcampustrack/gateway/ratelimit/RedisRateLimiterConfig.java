package ru.rutcampustrack.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

/**
 * M03a Группа 9: {@link KeyResolver} бины для Spring Cloud Gateway RedisRateLimiter.
 *
 * <p>Каждый роут в application.yml выбирает key-resolver по имени через
 * {@code key-resolver: "#{@ipKeyResolver}"}. RedisRateLimiter-инстансы
 * конфигурируются в application.yml через args (replenish/burst) и резолвятся
 * через Spring Cloud Gateway autoconfiguration.</p>
 *
 * <p>Fail-open (NEW-9): при Redis недоступности {@link FailOpenRateLimiter}
 * оборачивает стандартный RedisRateLimiter и возвращает allowed=true с WARN.</p>
 */
@Configuration
public class RedisRateLimiterConfig {

    /** Ключ — IP клиента из X-Forwarded-For (первый) или RemoteAddr. */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(resolveIp(exchange.getRequest().getHeaders(),
                exchange.getRequest().getRemoteAddress() == null
                        ? "unknown"
                        : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()));
    }

    /**
     * Ключ — user-id из заголовка {@code X-User-Id}, поставленного
     * {@link ru.rutcampustrack.gateway.filter.JwtAuthenticationFilter} после валидации
     * внешнего JWT. Если header отсутствует (неаутентифицированный роут) — fallback на IP.
     */
    @Bean
    public KeyResolver userIdKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            return Mono.just("ip:" + resolveIp(exchange.getRequest().getHeaders(),
                    exchange.getRequest().getRemoteAddress() == null
                            ? "unknown"
                            : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()));
        };
    }

    /**
     * Ключ — login из заголовка {@code X-Login}.
     *
     * <p>Для роута {@code /api/auth/login} тело приходит через
     * {@code CacheRequestBody} + {@code JsonToHeadersGatewayFilter} (конфигурируется в
     * Группе 10). Если header отсутствует — fallback на IP, чтобы не падать.</p>
     */
    @Bean
    public KeyResolver loginKeyResolver() {
        return exchange -> {
            String login = exchange.getRequest().getHeaders().getFirst("X-Login");
            if (login != null && !login.isBlank()) {
                return Mono.just("login:" + login.toLowerCase());
            }
            return Mono.just("ip:" + resolveIp(exchange.getRequest().getHeaders(),
                    exchange.getRequest().getRemoteAddress() == null
                            ? "unknown"
                            : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()));
        };
    }

    /**
     * Composite key IP+login для защиты /api/auth/login от брута: один IP+login =
     * одна корзина. Формат {@code "ip:<ip>:login:<login>"}. Fallback на IP при пустом login.
     */
    @Bean
    public KeyResolver ipLoginKeyResolver() {
        return exchange -> {
            String ip = resolveIp(exchange.getRequest().getHeaders(),
                    exchange.getRequest().getRemoteAddress() == null
                            ? "unknown"
                            : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            String login = exchange.getRequest().getHeaders().getFirst("X-Login");
            if (login == null || login.isBlank()) {
                return Mono.just("ip:" + ip);
            }
            return Mono.just("ip:" + ip + ":login:" + login.toLowerCase());
        };
    }

    private static String resolveIp(HttpHeaders headers, String fallback) {
        String xff = headers.getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return fallback;
    }

    /**
     * Fail-open wrapper поверх стандартного {@link RedisRateLimiter}.
     * {@code @Primary} — {@code RequestRateLimiterGatewayFilterFactory} без явного
     * {@code rate-limiter: "#{@bean}"} берёт эту обёртку. Именная ссылка на
     * {@code redisRateLimiter} всё ещё доступна (autoconfig bean).
     */
    @Bean
    @Primary
    public FailOpenRateLimiter failOpenRateLimiter(RedisRateLimiter redisRateLimiter,
                                                   ConfigurationService configurationService) {
        return new FailOpenRateLimiter(redisRateLimiter, configurationService);
    }
}
