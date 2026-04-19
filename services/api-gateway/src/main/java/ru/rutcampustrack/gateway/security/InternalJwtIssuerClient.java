package ru.rutcampustrack.gateway.security;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * M03a token-exchange client — calls auth-service {@code POST /internal/issue-internal-jwt}
 * with shared secret, caches the signed Internal JWT per-user for ~4 min.
 *
 * Cache key: {@link CacheKey} (userId + role) — role change triggers re-issue.
 * Cache value: {@link IssuedToken} with expiry timestamp.
 *
 * On auth-service 5xx / timeout → {@link InternalIssuerUnavailableException}
 * (mapped to 503 upstream).
 */
@Component
public class InternalJwtIssuerClient {

    private static final Logger log = LoggerFactory.getLogger(InternalJwtIssuerClient.class);
    private static final String SECRET_HEADER = "X-Internal-Issuer-Secret";
    private static final String ISSUE_PATH = "/internal/issue-internal-jwt";

    private final InternalIssuerClientProperties properties;
    private final WebClient webClient;
    private final AsyncCache<CacheKey, IssuedToken> cache;

    public InternalJwtIssuerClient(InternalIssuerClientProperties properties) {
        this(properties, WebClient.builder()
                .baseUrl(properties.getAuthServiceUrl())
                .build());
    }

    InternalJwtIssuerClient(InternalIssuerClientProperties properties, WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getCacheMaxSize())
                .expireAfterWrite(Duration.ofSeconds(properties.getCacheTtlSeconds()))
                .buildAsync();
    }

    public Mono<String> issueFor(long userId, String role, Long groupId, boolean isHeadman) {
        CacheKey key = new CacheKey(userId, role);
        return Mono.fromFuture(cache.get(key, (k, executor) ->
                issueFromAuthService(userId, role, groupId, isHeadman)
                        .toFuture()))
                .map(IssuedToken::token);
    }

    /**
     * Test-facing — clears cache (use sparingly, mostly for deterministic tests).
     */
    public void invalidateAll() {
        cache.synchronous().invalidateAll();
    }

    /**
     * Test-facing — peek current size.
     */
    public long estimatedSize() {
        return cache.synchronous().estimatedSize();
    }

    private Mono<IssuedToken> issueFromAuthService(long userId, String role, Long groupId, boolean isHeadman) {
        IssueRequest request = new IssueRequest(userId, role, groupId, isHeadman);
        return webClient.post()
                .uri(ISSUE_PATH)
                .header(SECRET_HEADER, properties.getSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new InternalIssuerUnavailableException(
                                "auth-service returned " + resp.statusCode() + " for /internal/issue-internal-jwt: " + body))))
                .bodyToMono(IssueResponse.class)
                .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .doOnError(e -> log.warn("Failed to obtain Internal JWT from auth-service: {}", e.getMessage()))
                .map(r -> new IssuedToken(r.token(), r.expiresAt()))
                .onErrorMap(e -> e instanceof InternalIssuerUnavailableException
                        ? e
                        : new InternalIssuerUnavailableException("Internal issuer call failed: " + e.getMessage(), e));
    }

    record CacheKey(long userId, String role) {
        CacheKey {
            Objects.requireNonNull(role, "role");
        }
    }

    record IssuedToken(String token, Instant expiresAt) {
    }

    record IssueRequest(long userId, String role, Long groupId, boolean isHeadman) {
    }

    record IssueResponse(String token, Instant expiresAt) {
    }
}
