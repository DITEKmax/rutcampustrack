# Phase 3 (legacy 1.3): API Gateway JWT Filter + Routing — Research

**Researched:** 2026-03-29
**Domain:** Spring Cloud Gateway 4.x (reactive) + JJWT 0.12.6 + WebClient (public key fetch)
**Confidence:** HIGH

---

## Summary

Phase 1.3 adds JWT validation to the existing Spring Cloud Gateway skeleton. The gateway already has routes configured in `application.yml` (FR-8 is essentially done). The two remaining pieces are: (1) a `GlobalFilter` or `GatewayFilter` that validates Bearer tokens and injects user-context headers, and (2) a startup task that fetches the RSA public key from Auth Service and schedules an hourly refresh.

**Critical technical fact:** Spring Cloud Gateway runs on Project Reactor (reactive/non-blocking). It does **not** use the Servlet stack. Filters must implement `GlobalFilter` (or `GatewayFilter`) and work with `ServerWebExchange`, not `HttpServletRequest/HttpServletResponse`. The Auth Service filter (`JwtAuthenticationFilter extends OncePerRequestFilter`) is Servlet-based and cannot be reused here.

JJWT 0.12.6 is already declared in the Gateway's `build.gradle.kts` (jjwt-api, jjwt-impl, jjwt-jackson). The gateway needs Spring WebFlux's `WebClient` to fetch the public key from Auth Service; WebFlux is the transitive dependency of `spring-cloud-starter-gateway` — no extra dependency needed.

**Primary recommendation:** Implement `JwtAuthenticationFilter implements GlobalFilter, Ordered`. On startup use `@PostConstruct` + `WebClient` to fetch the PEM key from Auth Service, parse it with `java.security.KeyFactory`, store as `PublicKey`. Schedule hourly refresh with `@Scheduled`. Maintain a public routes whitelist as a `Set<String>`. Inject headers into `exchange.getRequest().mutate()`.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FR-7.1 | On startup, fetch public key from Auth Service (GET http://auth-service:9090/auth/public-key) | WebClient.create().get().retrieve() in @PostConstruct |
| FR-7.2 | Cache key, refresh every hour | AtomicReference<PublicKey> + @Scheduled(fixedRate=3600000) |
| FR-7.3 | For non-public routes: validate Bearer token, check signature and expiry | JJWT Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token) |
| FR-7.4 | Extract claims, inject headers: X-User-Id, X-User-Role, X-Group-Id, X-Is-Headman | exchange.getRequest().mutate().header(...).build() |
| FR-7.5 | Invalid/missing token → 401 Unauthorized | exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED) + setComplete() |
| FR-8.1 | /api/auth/** → auth-service:9090 | Already in application.yml (verified) |
| FR-8.2 | /api/academic/** → academic-service:9091 | Already in application.yml (verified) |
| FR-8.3 | /api/schedule/** → schedule-service:9092 | Already in application.yml (verified) |
| FR-8.4 | /api/attendance/**, /api/reports/** → attendance-service:9093 | Already in application.yml (verified) |
| FR-8.5 | /api/ws/** → notification-web:9094 | Already in application.yml (verified) |
| FR-9.1 | /api/auth/login, /api/auth/otp/**, /api/auth/public-key, /api/auth/refresh — no JWT required | Public routes whitelist checked before JWT validation |
</phase_requirements>

---

## Project Constraints (from CLAUDE.md)

### Mandatory Rules
- Request DTOs = Java `record`. No Lombok in contract modules (`*-api-contract`). Lombok allowed in `*-app` modules.
- Errors: RFC 7807 Problem Details (`ErrorResponse` record).
- `@ControllerAdvice` for centralized error handling — however in reactive Gateway, use `@ExceptionHandler` in a `@RestControllerAdvice` or write directly to the response in the filter (the latter is simpler and sufficient here).
- Package naming: `ru.rutcampustrack.gateway.{module}`
- REST paths use `/api/{service}/...` routed through Gateway; downstream services strip `/api` prefix.
- No separate `*-api-contract` module for Gateway (same exception applies as auth-service — Gateway is infrastructure, not a domain service).
- Enum values in PostgreSQL: lowercase. Gateway does not read the DB, so this does not apply.
- Logging: `ru.rutcampustrack: DEBUG` in application.yml.

### Existing Infrastructure State
- `services/api-gateway/build.gradle.kts`: `spring-cloud-starter-gateway`, `spring-boot-starter-actuator`, `jjwt-api:0.12.6`, `jjwt-impl:0.12.6`, `jjwt-jackson:0.12.6` — all already declared.
- `services/api-gateway/src/main/resources/application.yml`: all 5 route groups already configured (FR-8 complete).
- `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/GatewayApplication.java`: bare `@SpringBootApplication` only.
- No existing filter code in the gateway.
- Spring Cloud 2024.0.0 = Spring Cloud Gateway 4.2.x (reactive, on top of Spring WebFlux 6.2.x).

---

## Standard Stack

### Core (all already in build.gradle.kts)
| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| spring-cloud-starter-gateway | 4.2.x (managed by SC 2024.0.0) | Reactive gateway, routing, filter chain | Already declared |
| jjwt-api | 0.12.6 | JWT parser API | Already declared |
| jjwt-impl | 0.12.6 | JWT implementation (runtime) | Already declared |
| jjwt-jackson | 0.12.6 | JSON for JWT (runtime) | Already declared |
| spring-boot-starter-actuator | 3.4.1 | Health/metrics | Already declared |

### What Needs to Be Added
| Library | Version | Purpose | Action |
|---------|---------|---------|--------|
| spring-boot-starter-validation | 3.4.1 (managed) | @Valid if config beans need it | Add if config properties binding needed |

**WebFlux / WebClient** — already available as transitive dependency of `spring-cloud-starter-gateway`. No extra declaration needed.

**`@Scheduled` support** — requires `@EnableScheduling` on the application class or a `@Configuration` class. No extra dependency.

### No New Dependencies Needed
The gateway already has all required libraries. This phase is entirely about writing Java code, not adding dependencies.

---

## Architecture Patterns

### Recommended Package Structure

```
services/api-gateway/src/main/java/ru/rutcampustrack/gateway/
├── GatewayApplication.java              # Add @EnableScheduling here
├── config/
│   ├── GatewayProperties.java           # @ConfigurationProperties(prefix = "gateway")
│   └── PublicKeyConfig.java             # Holds AtomicReference<PublicKey>, startup fetch + scheduled refresh
├── filter/
│   └── JwtAuthenticationFilter.java     # GlobalFilter + Ordered
└── exception/
    └── GatewayExceptionHandler.java     # Optional: @RestControllerAdvice for reactive context
```

### Pattern 1: GlobalFilter (Reactive) — NOT OncePerRequestFilter

**What:** Spring Cloud Gateway filters are reactive. Implement `GlobalFilter` + `Ordered`.
**Why not GatewayFilter:** `GlobalFilter` applies to all routes — simpler for a security filter that runs on every request. Route-specific filters would require registering per-route in application.yml.

```java
// Source: Spring Cloud Gateway official docs — GlobalFilter interface
package ru.rutcampustrack.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.rutcampustrack.gateway.config.PublicKeyConfig;

import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/public-key"
    );

    // Prefix-based public routes (otp/**)
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/auth/otp/"
    );

    private final PublicKeyConfig publicKeyConfig;

    public JwtAuthenticationFilter(PublicKeyConfig publicKeyConfig) {
        this.publicKeyConfig = publicKeyConfig;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Check public routes — bypass JWT
        if (isPublicRoute(path)) {
            return chain.filter(exchange);
        }

        // Extract Bearer token
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKeyConfig.getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Inject user-context headers into downstream request
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", claims.getSubject())
                    .header("X-User-Role", claims.get("role", String.class))
                    .header("X-Group-Id", String.valueOf(claims.get("group_id")))
                    .header("X-Is-Headman", String.valueOf(claims.get("is_headman")))
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange);
        }
    }

    private boolean isPublicRoute(String path) {
        if (PUBLIC_PATHS.contains(path)) return true;
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // Run before routing filters (which are typically Ordered.LOWEST_PRECEDENCE)
        // Negative value = high priority
        return -100;
    }
}
```

### Pattern 2: Public Key Fetch + Scheduled Refresh

**What:** On startup, fetch the PEM public key from Auth Service via WebClient, parse it to `PublicKey`, store in `AtomicReference`. Refresh hourly with `@Scheduled`.
**Why `AtomicReference`:** The scheduled refresh runs on a different thread; `AtomicReference` provides safe publication without synchronization.

```java
// Source: Spring WebFlux WebClient docs + java.security.KeyFactory standard API
package ru.rutcampustrack.gateway.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PublicKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(PublicKeyConfig.class);

    @Value("${gateway.auth-service-url:http://auth-service:9090}")
    private String authServiceUrl;

    private final AtomicReference<PublicKey> publicKeyRef = new AtomicReference<>();
    private final WebClient webClient = WebClient.create();

    @PostConstruct
    public void init() {
        fetchAndCachePublicKey();
    }

    @Scheduled(fixedRate = 3_600_000)   // every hour
    public void refresh() {
        fetchAndCachePublicKey();
    }

    public PublicKey getPublicKey() {
        PublicKey key = publicKeyRef.get();
        if (key == null) {
            throw new IllegalStateException("Public key not yet loaded from Auth Service");
        }
        return key;
    }

    private void fetchAndCachePublicKey() {
        try {
            // Auth Service returns: { "publicKey": "-----BEGIN PUBLIC KEY-----\n...", "algorithm": "RS256" }
            PublicKeyResponse response = webClient.get()
                    .uri(authServiceUrl + "/auth/public-key")
                    .retrieve()
                    .bodyToMono(PublicKeyResponse.class)
                    .block();  // blocking is acceptable at startup/refresh (not on request path)

            if (response == null || response.publicKey() == null) {
                throw new IllegalStateException("Auth Service returned empty public key");
            }

            PublicKey key = parsePemPublicKey(response.publicKey());
            publicKeyRef.set(key);
            log.info("RSA public key fetched and cached from Auth Service");

        } catch (Exception e) {
            log.error("Failed to fetch public key from Auth Service: {}", e.getMessage());
            // Do not crash on refresh failure — keep existing key cached
        }
    }

    private PublicKey parsePemPublicKey(String pem) throws Exception {
        String stripped = pem
                .replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    // Inner record for JSON deserialization
    record PublicKeyResponse(String publicKey, String algorithm) {}
}
```

### Pattern 3: Enable Scheduling

**What:** `@EnableScheduling` must be present for `@Scheduled` to work.

```java
// Add to GatewayApplication.java
@SpringBootApplication
@EnableScheduling
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

### Pattern 4: 401 Response Body (Optional — RFC 7807)

The minimal 401 response (empty body with status 401) is sufficient per requirements. If a structured error body is desired, use `ServerHttpResponse.writeWith()`:

```java
// Structured 401 body (optional enhancement)
private Mono<Void> unauthorizedWithBody(ServerWebExchange exchange, String message) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    String body = """
            {"status":401,"title":"Unauthorized","detail":"%s"}
            """.formatted(message);
    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return response.writeWith(Mono.just(buffer));
}
```

### Pattern 5: application.yml Additions

```yaml
# Add to api-gateway application.yml
gateway:
  auth-service-url: ${AUTH_SERVICE_URL:http://auth-service:9090}

spring:
  main:
    web-application-type: reactive   # Explicit, though Spring Cloud Gateway sets this automatically
```

### Anti-Patterns to Avoid

- **DO NOT use `OncePerRequestFilter` or `HttpServletRequest`** — Gateway is reactive/WebFlux. Servlet types are not available.
- **DO NOT call `WebClient` synchronously on the filter path** — use `.block()` only at startup/scheduled refresh, never within `filter()` method.
- **DO NOT use Spring Security in the Gateway** — the current `build.gradle.kts` has no `spring-boot-starter-security`. Adding it would require configuring a full reactive SecurityFilterChain. The custom `GlobalFilter` approach is simpler and sufficient.
- **DO NOT call `Jwts.parser().verifyWith(...)` on every request with a freshly-fetched key** — use the cached `AtomicReference<PublicKey>`.
- **DO NOT use `JwtParserBuilder.setSigningKey()`** — removed in JJWT 0.12. Use `.verifyWith(publicKey)`.
- **DO NOT use `parseClaimsJws()`** — deprecated in JJWT 0.12. Use `parseSignedClaims()`.
- **DO NOT strip the `/api` prefix before checking public routes** — the gateway receives `/api/auth/login`, not `/auth/login`. The whitelist must use full paths with `/api/` prefix.
- **DO NOT forget to handle null `group_id`** — TEACHER and ADMIN users have `null` group_id in JWT claims. The header injection must handle null gracefully (omit header or send "null" string — downstream services must handle absent header).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JWT signature verification | Manual RSA signature check | JJWT `Jwts.parser().verifyWith(publicKey).build().parseSignedClaims()` | Algorithm confusion, key type enforcement, expiry check built-in |
| PEM key parsing | Custom Base64 + ASN.1 decoder | `java.security.KeyFactory` + `X509EncodedKeySpec` | Standard JDK API, handles PEM headers/whitespace stripping |
| HTTP client for key fetch | Raw `HttpURLConnection` or RestTemplate | `WebClient` (already available via Spring WebFlux) | Non-blocking, consistent with reactive stack |
| Thread-safe key caching | `synchronized` block or lock | `AtomicReference<PublicKey>` | Lock-free, single-writer pattern |

---

## Common Pitfalls

### Pitfall 1: Servlet API in Gateway Filter
**What goes wrong:** Code compiles but fails at runtime with `NoSuchMethodError` or filter never fires.
**Why it happens:** Copying filter code from Auth Service (`OncePerRequestFilter`, `HttpServletRequest`) into the Gateway which runs on Netty/WebFlux.
**How to avoid:** Always implement `GlobalFilter` (from `org.springframework.cloud.gateway.filter`) for Gateway filters. Use `ServerWebExchange` and `ServerHttpRequest`.
**Warning signs:** Import of `jakarta.servlet.*` in gateway code.

### Pitfall 2: Public Route Path Mismatch
**What goes wrong:** Requests to `/api/auth/login` get 401 because the whitelist has `/auth/login`.
**Why it happens:** Gateway receives requests at `/api/...` paths. The `StripPrefix=1` filter (which removes `/api`) runs AFTER the `GlobalFilter` has already decided to reject/allow the request.
**How to avoid:** Whitelist must use the FULL path as received by the gateway: `/api/auth/login`, `/api/auth/refresh`, `/api/auth/public-key`, `/api/auth/otp/`.
**Warning signs:** Login endpoint returns 401 during manual testing.

### Pitfall 3: `.block()` on the Request Path
**What goes wrong:** Gateway latency spikes, eventually deadlocks under load.
**Why it happens:** Using `WebClient.block()` inside the `filter()` method to validate tokens.
**How to avoid:** All network I/O must be async in the filter. The public key must already be cached when a request arrives. Use `AtomicReference` populated at startup.
**Warning signs:** `IllegalStateException: block()/blockFirst()/blockLast() are blocking` with reactor-netty error.

### Pitfall 4: Missing `@EnableScheduling`
**What goes wrong:** `@Scheduled(fixedRate=...)` on `refresh()` method never fires. Public key is never refreshed.
**Why it happens:** Spring Boot does not auto-enable `@Scheduled` processing without `@EnableScheduling`.
**How to avoid:** Add `@EnableScheduling` to `GatewayApplication` or a `@Configuration` class.
**Warning signs:** Log never shows "RSA public key fetched" more than once after startup.

### Pitfall 5: Gateway Startup Failure When Auth Service Is Down
**What goes wrong:** Gateway fails to start if Auth Service is unavailable at startup time.
**Why it happens:** `@PostConstruct` that throws on network failure causes the bean to fail initialization.
**How to avoid:** Catch all exceptions in `fetchAndCachePublicKey()` and log warning. At startup, retry or accept that requests will fail with 500 until the key is loaded. Include NFR-3: "Gateway retries public key fetch on startup failure" — implement retry with `WebClient` retry operators, or keep the `@Scheduled` refresh to recover automatically within 1 hour.
**Warning signs:** `BeanCreationException` during context startup.

### Pitfall 6: Null Claims for TEACHER/ADMIN
**What goes wrong:** Downstream service crashes on `X-Group-Id: null` string.
**Why it happens:** TEACHER and ADMIN users have no `group_id` claim (or claim is null). `String.valueOf(null)` returns the string `"null"`.
**How to avoid:** Check for null before injecting: if `group_id` is null, either skip the header or inject an empty string. Downstream services should treat absent `X-Group-Id` as "no group".
**Warning signs:** Downstream service NPE when parsing `X-Group-Id` header.

### Pitfall 7: WebClient Jackson Deserialization in Reactive Context
**What goes wrong:** `PublicKeyResponse` record deserializes as null fields.
**Why it happens:** Jackson needs the `jjwt-jackson` module OR `spring-boot-starter-webflux` auto-configures Jackson — the latter is present via Spring Cloud Gateway. But if the record constructor uses compact constructor, Jackson may fail without `@JsonProperty` or matching parameter names (requires `-parameters` flag).
**How to avoid:** The root `build.gradle.kts` already sets `-parameters` for all subprojects. Records with matching field names to JSON keys work without annotations.
**Warning signs:** `publicKey` field is null in deserialized `PublicKeyResponse`.

---

## Code Examples

### Null-Safe Header Injection

```java
// Handle null group_id (TEACHER, ADMIN have no group)
Object groupId = claims.get("group_id");
Object isHeadman = claims.get("is_headman");

ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
        .header("X-User-Id", claims.getSubject())
        .header("X-User-Role", claims.get("role", String.class));

if (groupId != null) {
    requestBuilder.header("X-Group-Id", String.valueOf(groupId));
}
if (isHeadman != null) {
    requestBuilder.header("X-Is-Headman", String.valueOf(isHeadman));
}
```

### Retry Logic at Startup (NFR-3)

```java
// In fetchAndCachePublicKey() — use WebClient retry for NFR-3
PublicKeyResponse response = webClient.get()
        .uri(authServiceUrl + "/auth/public-key")
        .retrieve()
        .bodyToMono(PublicKeyResponse.class)
        .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(5))
                .filter(e -> e instanceof WebClientException))
        .block(Duration.ofSeconds(30));
```

### application.yml Final State

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway

  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: http://auth-service:9090
          predicates:
            - Path=/api/auth/**
          filters:
            - StripPrefix=1

        - id: academic-service
          uri: http://academic-service:9091
          predicates:
            - Path=/api/academic/**
          filters:
            - StripPrefix=1

        - id: schedule-service
          uri: http://schedule-service:9092
          predicates:
            - Path=/api/schedule/**
          filters:
            - StripPrefix=1

        - id: attendance-service
          uri: http://attendance-service:9093
          predicates:
            - Path=/api/attendance/**, /api/reports/**
          filters:
            - StripPrefix=1

        - id: notification-web
          uri: http://notification-web:9094
          predicates:
            - Path=/api/ws/**
          filters:
            - StripPrefix=1

gateway:
  auth-service-url: ${AUTH_SERVICE_URL:http://auth-service:9090}

logging:
  level:
    org.springframework.cloud.gateway: DEBUG
    ru.rutcampustrack: DEBUG
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `spring-cloud-starter-gateway` used `Zuul` (blocking) | Spring Cloud Gateway (reactive, Netty-based) | Spring Cloud 2020 | All filters must be reactive (`GlobalFilter`, not `Filter`) |
| `Jwts.parserBuilder()` | `Jwts.parser()` returns `JwtParserBuilder` | JJWT 0.12.0 (2023) | `parserBuilder()` removed |
| `parseClaimsJws()` | `parseSignedClaims()` | JJWT 0.12.0 (2023) | Clearer naming; old method removed |
| `setSigningKey()` / `setVerifyWith()` | `.verifyWith(PublicKey)` | JJWT 0.12.0 (2023) | Type-safe API |
| `WebSecurityConfigurerAdapter` | `SecurityFilterChain` bean | Spring Security 6.0 | Adapter removed; not applicable here (no Spring Security in Gateway) |

---

## FR-8 Routing: Already Complete

The existing `application.yml` satisfies all FR-8 requirements:

| Requirement | Configuration Line | Status |
|-------------|--------------------|--------|
| FR-8.1: /api/auth/** → auth-service:9090 | `uri: http://auth-service:9090` + `Path=/api/auth/**` | Done |
| FR-8.2: /api/academic/** → academic-service:9091 | `uri: http://academic-service:9091` + `Path=/api/academic/**` | Done |
| FR-8.3: /api/schedule/** → schedule-service:9092 | `uri: http://schedule-service:9092` + `Path=/api/schedule/**` | Done |
| FR-8.4: /api/attendance/** + /api/reports/** → attendance-service:9093 | `Path=/api/attendance/**, /api/reports/**` | Done |
| FR-8.5: /api/ws/** → notification-web:9094 | `uri: http://notification-web:9094` + `Path=/api/ws/**` | Done |

The `StripPrefix=1` on each route removes the `/api` segment before forwarding — correct behavior since downstream services listen at `/auth/...`, `/academic/...`, etc.

**No changes to application.yml routing section are required.**

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 | Compilation | Verify (CLAUDE.md: ms-21.0.9) | 21 | — |
| Auth Service (port 9090) | FR-7.1 public key fetch | Via Docker Compose (phase 1.1 complete) | — | Key fetch fails gracefully, logged |
| Redis | Not needed by Gateway | N/A | N/A | N/A |
| Gradle | Build | gradlew.bat | 8.12 | — |

**Missing dependencies with no fallback:** None. Auth Service already implemented (Phase 1.1 complete).

**Note on startup ordering:** Gateway may start before Auth Service in Docker Compose. The retry logic (Pitfall 5) handles this. Alternatively, add `depends_on: auth-service` in `docker-compose.yml`.

---

## Validation Architecture

> `workflow.nyquist_validation` not set in `.planning/config.json` — treat as enabled.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (via `spring-boot-starter-test`) |
| Config file | None detected in api-gateway |
| Quick run command | `./gradlew.bat :services:api-gateway:test` |
| Full suite command | `./gradlew.bat :services:api-gateway:test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Notes |
|--------|----------|-----------|-------|
| FR-7.3 | Valid JWT passes filter, headers injected | Unit | Test `JwtAuthenticationFilter` with mock exchange |
| FR-7.5 | Missing/invalid token → 401 | Unit | Mock exchange with no/bad Authorization header |
| FR-7 (expiry) | Expired JWT → 401 | Unit | Generate token with past expiry using JJWT |
| FR-9.1 | Public routes bypass filter | Unit | Test with `/api/auth/login` path, no Authorization header |
| FR-7.1 | Gateway fetches public key on startup | Integration | Requires mock HTTP server or running Auth Service |

### Wave 0 Gaps
- [ ] `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java` — unit tests for filter (mock `ServerWebExchange`)
- [ ] `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/config/PublicKeyConfigTest.java` — unit tests for PEM parsing

---

## Open Questions

1. **Null group_id header behavior**
   - What we know: TEACHER and ADMIN users have no group_id. JWT claim will be null.
   - What's unclear: Should `X-Group-Id` header be omitted (absent) or sent as empty string?
   - Recommendation: Omit the header entirely if null. Downstream services should check for header presence, not just value.

2. **Startup retry policy when Auth Service unavailable**
   - What we know: NFR-3 says "Gateway retries public key fetch on startup failure."
   - What's unclear: How many retries, what delay?
   - Recommendation: 3 retries with 5-second delay (matching the pattern used in other services). If all retries fail, log ERROR but do not crash the gateway — the `@Scheduled` refresh will retry in 1 hour. Requests before key is loaded should return 503, not 401.

3. **JWT body in 401 responses**
   - What we know: Requirements say "Error responses for 401 Unauthorized".
   - What's unclear: Should the 401 include an RFC 7807 body, or is an empty 401 sufficient?
   - Recommendation: Empty 401 (status only) is sufficient for Phase 1.3. RFC 7807 body can be added in a later phase if clients need it.

---

## Sources

### Primary (HIGH confidence)
- Project codebase: `services/api-gateway/build.gradle.kts` — confirmed JJWT 0.12.6 already declared
- Project codebase: `services/api-gateway/src/main/resources/application.yml` — confirmed all FR-8 routes present
- Project codebase: `services/auth-service/src/main/java/.../service/JwtService.java` — confirmed JWT claims structure (sub=user_id, role, group_id, is_headman)
- Project codebase: `services/auth-service/src/main/java/.../dto/PublicKeyResponse.java` — confirmed response shape `record(String publicKey, String algorithm)`
- Spring Cloud Gateway docs — `GlobalFilter` interface, `ServerWebExchange.getRequest().mutate()`
- JJWT 0.12.x API — `Jwts.parser().verifyWith().build().parseSignedClaims()`
- Java standard library — `KeyFactory`, `X509EncodedKeySpec` for PEM parsing

### Secondary (MEDIUM confidence)
- Spring WebFlux `WebClient` docs — `.retrieve().bodyToMono()`, `.retryWhen(Retry.fixedDelay())`
- Spring Scheduling docs — `@EnableScheduling` + `@Scheduled(fixedRate=...)`

---

## Metadata

**Confidence breakdown:**
- FR-8 routing: HIGH — routes already in application.yml, verified from file
- JWT filter (GlobalFilter API): HIGH — standard Spring Cloud Gateway pattern, verified from official docs
- JJWT 0.12.6 API: HIGH — verified in Phase 1.1 research and existing JwtService.java in auth-service
- Public key fetch (WebClient): HIGH — standard Spring WebFlux pattern
- Null claim handling: MEDIUM — behavior depends on what downstream services expect (not yet implemented)

**Research date:** 2026-03-29
**Valid until:** 2026-04-29 (stable stack)
