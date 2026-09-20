package ru.rutcampustrack.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;
import ru.rutcampustrack.gateway.clientip.TrustedClientIpResolver;
import ru.rutcampustrack.gateway.filter.JwtAuthenticationFilter;

import java.util.Locale;

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

    /**
     * Ключ — канонический IP, вычисленный {@link TrustedClientIpResolver} по
     * raw peer и доверенному edge. До запуска глобального фильтра используется
     * только raw peer; клиентский X-Forwarded-For здесь никогда не разбирается.
     * {@code @Primary} — default для {@code RequestRateLimiterGatewayFilterFactory}
     * когда в route-конфиге не указан явный {@code key-resolver}.
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(resolveCanonicalIp(exchange));
    }

    /**
     * Ключ — user-id из внутреннего exchange attribute, поставленного после
     * успешной live admission в {@code InternalJwtIssuerFilter}. Если identity
     * отсутствует — fallback на IP; клиентские identity headers никогда не
     * являются источником ключа.
     */
    @Bean
    public KeyResolver userIdKeyResolver() {
        return exchange -> {
            String userId = exchange.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_USER_ID_ATTRIBUTE);
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            return Mono.just("ip:" + resolveCanonicalIp(exchange));
        };
    }

    /**
     * Ключ — login из заголовка {@code X-Login}.
     *
     * <p><b>Безопасность:</b> {@code JwtAuthenticationFilter} strip'ает
     * клиентский {@code X-Login}; {@link LoginBodyExtractionFilter} добавляет
     * его только после bounded body parsing на login route. Composite seam
     * остаётся dormant, пока отдельное policy decision не включит его.</p>
     */
    @Bean
    public KeyResolver loginKeyResolver() {
        return exchange -> {
            String login = exchange.getRequest().getHeaders().getFirst("X-Login");
            if (login != null && !login.isBlank()) {
                return Mono.just("login:" + login.strip().toLowerCase(Locale.ROOT));
            }
            return Mono.just("ip:" + resolveCanonicalIp(exchange));
        };
    }

    /**
     * Composite key IP+login для защиты /api/auth/login от брута: один IP+login =
     * одна корзина. Формат {@code "ip:<ip>:login:<login>"}. Fallback на IP при пустом login.
     */
    @Bean
    public KeyResolver ipLoginKeyResolver() {
        return exchange -> {
            String ip = resolveCanonicalIp(exchange);
            String login = exchange.getRequest().getHeaders().getFirst("X-Login");
            if (login == null || login.isBlank()) {
                return Mono.just("ip:" + ip);
            }
            return Mono.just("ip:" + ip + ":login:" + login.strip().toLowerCase(Locale.ROOT));
        };
    }

    private static String resolveCanonicalIp(org.springframework.web.server.ServerWebExchange exchange) {
        String normalized = exchange.getAttribute(TrustedClientIpResolver.CLIENT_IP_ATTRIBUTE);
        if (normalized != null && !normalized.isBlank()) {
            return normalized;
        }
        return TrustedClientIpResolver.canonicalizeRemoteAddress(exchange.getRequest().getRemoteAddress());
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
