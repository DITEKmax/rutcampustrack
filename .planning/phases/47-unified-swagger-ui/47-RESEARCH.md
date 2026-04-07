# Phase 47: Unified Swagger UI - Research

**Researched:** 2026-04-08
**Domain:** springdoc-openapi / Spring Cloud Gateway Swagger aggregation
**Confidence:** HIGH

---

## Summary

All four REST services (auth, academic, schedule, attendance) already have `springdoc-openapi-starter-webmvc-ui:2.7.0` and expose their specs at `/api-docs`. The API Gateway is WebFlux-based (Spring Cloud Gateway) and currently has **no** springdoc dependency. The standard pattern for aggregating Swagger across a Spring Cloud Gateway is:

1. Add `springdoc-openapi-starter-webflux-ui` to the Gateway (WebFlux variant — the webmvc-ui does not work in a reactive runtime).
2. Configure `springdoc.swagger-ui.urls` in the Gateway pointing to each service's api-docs path via Gateway-proxied routes.
3. Add extra Gateway routes to proxy each service's `/v3/api-docs` (or custom path `/api-docs`) through the Gateway itself so the browser can fetch them from a single origin.
4. Whitelist the Swagger/OpenAPI paths in the JWT authentication filter so they load without a token.
5. Upgrade all services from `2.7.0` to `2.8.6` per the DOC-02 requirement (2.8.6 confirmed to exist on Maven Central; latest 2.x line is 2.8.16, but the requirement specifically pins 2.8.6).

The notification-web service also has `springdoc-openapi-starter-webmvc-ui:2.7.0` but is **not** listed in the success criteria for aggregation (not a REST API, it's a WebSocket/push service), so it is treated as upgrade-only, not aggregated.

**Primary recommendation:** Add `springdoc-openapi-starter-webflux-ui:2.8.6` to the API Gateway, add api-docs proxy routes to gateway `application.yml`, configure `swagger-ui.urls` with five entries (gateway + 4 services via proxied paths), whitelist swagger/openapi paths in `JwtAuthenticationFilter`, and upgrade all five service webmvc-ui deps from 2.7.0 to 2.8.6.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DOC-01 | Unified Swagger UI accessible at Gateway with aggregated specs from all services | Gateway springdoc-webflux-ui + swagger-ui.urls pointing to proxied api-docs paths |
| DOC-02 | springdoc upgraded to 2.8.6 across all services | Version 2.8.6 verified on Maven Central; all 5 services currently on 2.7.0 |
| DOC-03 | Gateway uses springdoc-openapi-starter-webflux-ui for Swagger aggregation | Mandatory — Spring Cloud Gateway is WebFlux; webmvc-ui would fail to start |
</phase_requirements>

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `springdoc-openapi-starter-webflux-ui` | 2.8.6 | Swagger UI + OpenAPI serving on the WebFlux Gateway | Required for reactive runtime; webmvc-ui causes class conflicts in Gateway |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.6 | OpenAPI spec exposure on each MVC service | Already in use on all 4 REST services + notification-web; upgrade from 2.7.0 |

**Version verification:** [VERIFIED: Maven Central] Version 2.8.6 exists in Maven Central. The latest 2.x release is 2.8.16; the requirement pins 2.8.6. The latest overall release is 3.0.2 (requires Spring Boot 3.5+, out of scope per requirements).

### Installation (Gateway — new dependency)

```kotlin
// services/api-gateway/build.gradle.kts
implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.6")
```

### Upgrade (4 REST services + notification-web — version bump only)

```kotlin
// All 5 services: change 2.7.0 → 2.8.6
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
```

---

## Architecture Patterns

### How springdoc aggregation works at a Gateway

The Gateway hosts the Swagger UI HTML itself. The UI needs to fetch the OpenAPI JSON specs from each service. Because browsers enforce same-origin policy, the UI can only fetch specs from the same origin as the UI page. This means:

- The Swagger UI page lives at `http://gateway:8080/swagger-ui.html`
- Each service spec must be accessible at paths **on the gateway** (e.g. `http://gateway:8080/openapi/auth-service`)
- The gateway must route those paths to the upstream services' api-docs endpoints

There are two approaches for the aggregation URL configuration:

**Approach A (recommended for this project) — Gateway routes proxy service api-docs:**
Add dedicated api-docs routes in `application.yml` that proxy each service's `/api-docs` through the gateway. Then configure `swagger-ui.urls` with these gateway-local paths.

**Approach B — Absolute upstream URLs (requires CORS on each service):**
Configure `swagger-ui.urls` with absolute HTTP URLs to each service directly (e.g. `http://academic-service:9091/api-docs`). Requires CORS enabled on each service. Not recommended: breaks same-origin, the browser can't reach internal docker network hostnames, and makes the "Try it out" feature send requests directly to internal service ports instead of through the gateway.

**Approach A is the correct pattern for this project.** [CITED: gleason.tech/2023-08-09-spring-cloud-gateway-swagger, erkndmrl medium article]

### Recommended Gateway application.yml Structure

```yaml
springdoc:
  enable-native-support: true
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    config-url: /v3/api-docs/swagger-config
    urls:
      - url: /openapi/auth-service
        name: Auth Service
      - url: /openapi/academic-service
        name: Academic Service
      - url: /openapi/schedule-service
        name: Schedule Service
      - url: /openapi/attendance-service
        name: Attendance Service
```

### Recommended Gateway Route additions

The gateway needs new routes to proxy each service's api-docs. These use `RewritePath` to strip the prefix:

```yaml
spring:
  cloud:
    gateway:
      routes:
        # ... existing routes unchanged ...

        # api-docs proxy routes (for Swagger aggregation)
        - id: auth-service-openapi
          uri: http://auth-service:9090
          predicates:
            - Path=/openapi/auth-service
          filters:
            - RewritePath=/openapi/auth-service, /api-docs

        - id: academic-service-openapi
          uri: http://academic-service:9091
          predicates:
            - Path=/openapi/academic-service
          filters:
            - RewritePath=/openapi/academic-service, /api-docs

        - id: schedule-service-openapi
          uri: http://schedule-service:9092
          predicates:
            - Path=/openapi/schedule-service
          filters:
            - RewritePath=/openapi/schedule-service, /api-docs

        - id: attendance-service-openapi
          uri: http://attendance-service:9093
          predicates:
            - Path=/openapi/attendance-service
          filters:
            - RewritePath=/openapi/attendance-service, /api-docs
```

**Why `/api-docs`?** All four services already configure `springdoc.api-docs.path: /api-docs`. [VERIFIED: codebase — all four application.yml files confirmed]

### JWT Filter: public paths for Swagger

The `JwtAuthenticationFilter` currently has a hardcoded `PUBLIC_PATHS` set. The following paths must be added to allow the Swagger UI and api-docs to load without a token:

```java
private static final Set<String> PUBLIC_PATHS = Set.of(
    "/api/auth/login",
    "/api/auth/refresh",
    "/api/auth/public-key",
    "/api/auth/tma",
    "/api/auth/refresh-body",
    // Swagger UI (Phase 47)
    "/swagger-ui.html"
);

private static final List<String> PUBLIC_PREFIXES = List.of(
    "/api/auth/otp/",
    // Swagger UI assets and API docs (Phase 47)
    "/swagger-ui/",
    "/v3/api-docs",
    "/openapi/"
);
```

[VERIFIED: codebase — JwtAuthenticationFilter.java, lines 32-41]

### OpenAPI bean in Gateway (optional but recommended)

A minimal `@Configuration` class in the Gateway to set a title on the Gateway's own spec:

```java
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("RutCampusTrack API")
                .description("Unified API documentation — aggregated from all microservices")
                .version("1.0"));
    }
}
```

This is optional for the aggregation to work, but sets a proper title instead of "OpenAPI definition".

### Nginx — no changes needed

The existing `nginx/conf.d/default.conf` has:

```nginx
location /api/ {
    proxy_pass http://rct-api-gateway:8080;
}
```

The Swagger UI will be at `/swagger-ui.html` and API docs at `/v3/api-docs*` and `/openapi/*` — all of these do **not** start with `/api/` and won't be caught by the nginx `/api/` rule. A separate nginx location block must be added for swagger paths, OR the Swagger UI can be accessed directly on gateway port 8080 in development. For production, nginx must proxy `/swagger-ui*`, `/v3/api-docs*`, and `/openapi/*` to the gateway.

**Pitfall:** Without nginx rules for `/swagger-ui.html`, navigating to `https://domain.com/swagger-ui.html` will hit the PWA catch-all rule (`location /`) and return the React app instead of the Swagger UI.

Nginx additions needed:

```nginx
# Swagger UI (Phase 47) — must come before the catch-all PWA rule
location /swagger-ui {
    proxy_pass http://rct-api-gateway:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
}

location /v3/api-docs {
    proxy_pass http://rct-api-gateway:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
}

location /openapi/ {
    proxy_pass http://rct-api-gateway:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

[VERIFIED: codebase — nginx/conf.d/default.conf confirms `/` catch-all for PWA]

### springdoc.enable-native-support

The `springdoc.enable-native-support: true` property is mentioned in several integration guides for Spring Cloud Gateway. [CITED: erkndmrl medium article] Without it, springdoc may fail to auto-configure properly in a reactive (WebFlux) context. It should be set in the Gateway's `application.yml`.

---

## Current State Inventory

| Service | springdoc dependency | Current version | api-docs path |
|---------|---------------------|----------------|---------------|
| auth-service | webmvc-ui | 2.7.0 | `/api-docs` |
| academic-app | webmvc-ui | 2.7.0 | `/api-docs` |
| schedule-app | webmvc-ui | 2.7.0 | `/api-docs` |
| attendance-app | webmvc-ui | 2.7.0 | `/api-docs` |
| notification-app | webmvc-ui | 2.7.0 | not configured (no application.yml springdoc block) |
| api-gateway | **none** | — | — |

[VERIFIED: codebase — all build.gradle.kts and application.yml files]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Swagger spec aggregation | Custom `/swagger-config.json` endpoint or SwaggerResource bean | `springdoc.swagger-ui.urls` config | springdoc handles the Swagger UI config endpoint natively |
| Reactive WebFlux compatibility | Manual adapters | `springdoc-openapi-starter-webflux-ui` | Designed for reactive; webmvc-ui will throw ClassNotFoundException in WebFlux |
| jwt filter for swagger paths | Complex path matching | Simple prefix whitelist in PUBLIC_PREFIXES | Already have the pattern; just extend it |

---

## Common Pitfalls

### Pitfall 1: Using webmvc-ui on the Gateway
**What goes wrong:** The Gateway starts on WebFlux (Spring Cloud Gateway is reactive). Adding `springdoc-openapi-starter-webmvc-ui` pulls in servlet-based autoconfiguration that conflicts with WebFlux, causing startup failure or ClassNotFoundException.
**Why it happens:** The two starters are mutually exclusive — webmvc vs webflux runtimes.
**How to avoid:** Use `springdoc-openapi-starter-webflux-ui` on the Gateway. Use `springdoc-openapi-starter-webmvc-ui` on all other Spring MVC services.
**Warning signs:** `NoSuchBeanDefinitionException` for `RequestMappingHandlerMapping` or `FilterRegistrationBean` at startup.

### Pitfall 2: swagger-ui.urls pointing to absolute internal Docker hostnames
**What goes wrong:** The browser fetches `http://academic-service:9091/api-docs` which is an internal Docker DNS name — unreachable from the browser. Spec fails to load with a network error.
**Why it happens:** `swagger-ui.urls` values are fetched by the **browser**, not the server. Docker network hostnames resolve only within the Docker network.
**How to avoid:** All `swagger-ui.urls` entries must be **relative paths** (no scheme/host) that the Gateway proxies to upstream services.

### Pitfall 3: Swagger UI blocked by JWT filter
**What goes wrong:** Browser navigates to `/swagger-ui.html` → Gateway's JwtAuthenticationFilter sees no Bearer token → returns 401 → blank page.
**Why it happens:** JWT filter currently only whitelists a fixed set of auth paths.
**How to avoid:** Add `/swagger-ui.html` to `PUBLIC_PATHS` and `/swagger-ui/`, `/v3/api-docs`, `/openapi/` to `PUBLIC_PREFIXES`.

### Pitfall 4: Nginx catch-all intercepts Swagger UI
**What goes wrong:** `https://domain.com/swagger-ui.html` hits the `location /` PWA rule in nginx and returns `index.html` of the React app.
**Why it happens:** nginx `/` is a catch-all that currently covers everything not matched by more specific `/api/`, `/landing/`, `/admin/`, `/mini-app/` rules.
**How to avoid:** Add explicit `location /swagger-ui`, `location /v3/api-docs`, and `location /openapi/` blocks before the `location /` block in nginx default.conf.

### Pitfall 5: StripPrefix on api-docs proxy routes
**What goes wrong:** If api-docs proxy routes use `StripPrefix=1` (same as the main service routes), the path `/openapi/auth-service` becomes `/auth-service` after stripping, which doesn't exist on the upstream.
**Why it happens:** StripPrefix strips the first path segment.
**How to avoid:** Use `RewritePath` filter instead of `StripPrefix` for api-docs proxy routes. `RewritePath=/openapi/auth-service, /api-docs` maps exactly to the configured api-docs path.

### Pitfall 6: "Try it out" sends requests to wrong base path
**What goes wrong:** After loading the aggregated spec, clicking "Try it out" → Execute sends requests to `/auth/...` instead of `/api/auth/...` because the service's OpenAPI spec declares `servers: [{url: "/"}]` and paths start with `/auth/`.
**Why it happens:** The upstream services configure their paths relative to their own root, not the gateway-prefixed path.
**How to avoid:** Each service should configure `springdoc.api-docs.path` and the `servers` block to reflect the gateway-prefixed URLs. Alternatively, add `@OpenAPIDefinition(servers = {@Server(url = "/api/auth")})` or configure via `application.yml`:
```yaml
springdoc:
  api-docs:
    path: /api-docs
  override-with-generic-response: false
```
And in gateway swagger-ui config add `url-prefix` support or use `servers` override in each service's `OpenAPI` bean. This is a known complexity — the simplest fix is ensuring each service's `@OpenAPIDefinition` declares the correct gateway-prefixed server URL.

---

## Code Examples

### Gateway build.gradle.kts addition
```kotlin
// Source: verified from existing service build.gradle.kts pattern + springdoc.org docs
implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.6")
```

### Gateway application.yml — full springdoc block
```yaml
# Source: pattern from erkndmrl medium + gleason.tech + codebase api-docs path verification
springdoc:
  enable-native-support: true
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    config-url: /v3/api-docs/swagger-config
    urls:
      - url: /openapi/auth-service
        name: Auth Service
      - url: /openapi/academic-service
        name: Academic Service
      - url: /openapi/schedule-service
        name: Schedule Service
      - url: /openapi/attendance-service
        name: Attendance Service
```

### Gateway application.yml — api-docs proxy routes addition
```yaml
# Source: derived from Spring Cloud Gateway docs + codebase route pattern
        - id: auth-service-openapi
          uri: http://auth-service:9090
          predicates:
            - Path=/openapi/auth-service
          filters:
            - RewritePath=/openapi/auth-service, /api-docs

        - id: academic-service-openapi
          uri: http://academic-service:9091
          predicates:
            - Path=/openapi/academic-service
          filters:
            - RewritePath=/openapi/academic-service, /api-docs

        - id: schedule-service-openapi
          uri: http://schedule-service:9092
          predicates:
            - Path=/openapi/schedule-service
          filters:
            - RewritePath=/openapi/schedule-service, /api-docs

        - id: attendance-service-openapi
          uri: http://attendance-service:9093
          predicates:
            - Path=/openapi/attendance-service
          filters:
            - RewritePath=/openapi/attendance-service, /api-docs
```

### JwtAuthenticationFilter whitelist additions
```java
// Source: codebase — services/api-gateway/src/.../filter/JwtAuthenticationFilter.java
private static final Set<String> PUBLIC_PATHS = Set.of(
    "/api/auth/login",
    "/api/auth/refresh",
    "/api/auth/public-key",
    "/api/auth/tma",
    "/api/auth/refresh-body",
    "/swagger-ui.html"
);

private static final List<String> PUBLIC_PREFIXES = List.of(
    "/api/auth/otp/",
    "/swagger-ui/",
    "/v3/api-docs",
    "/openapi/"
);
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| SpringFox (Swagger 2) | springdoc-openapi (OpenAPI 3) | 2021+ | springdoc required for WebFlux; SpringFox unmaintained |
| springdoc v1.x | springdoc v2.x (Spring Boot 3 compatible) | 2022 | v1.x requires Spring Boot 2; v2.x for Spring Boot 3 |
| springdoc 2.7.0 (current in project) | springdoc 2.8.6 (target per DOC-02) | 2024 | Minor version bump; no breaking changes in 2.x line |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `springdoc.enable-native-support: true` is required for proper Gateway autoconfiguration | Architecture Patterns | Swagger UI may still work without it; low risk — the property does not break anything if unnecessary |
| A2 | Notification-web service is NOT included in the Swagger aggregation (success criteria only lists auth, academic, schedule, attendance) | Summary | If notification-web REST endpoints need to be documented, one more route and URL entry is needed |
| A3 | The "Try it out" base path issue (Pitfall 6) is acceptable without server URL overrides — UI still shows correct operations | Pitfalls | If "Try it out" must work end-to-end, each service needs `@OpenAPIDefinition` with gateway-prefixed server URL |

---

## Open Questions

1. **Should "Try it out" (execute) work end-to-end through the Swagger UI?**
   - What we know: Swagger UI "Try it out" sends requests to the `servers[].url` defined in each service's OpenAPI spec. Currently no `@OpenAPIDefinition` is set, so default is the service's own URL.
   - What's unclear: Whether the planner wants "Try it out" to work via the gateway or just browsability (read-only docs).
   - Recommendation: Plan for read-only documentation first (DOC-01 doesn't mention execute). Add server URL overrides in a follow-up if needed.

2. **Should the Swagger UI be accessible in production via the domain (nginx)?**
   - What we know: nginx currently routes `/api/*` only; `/swagger-ui.html` would hit the PWA catch-all.
   - Recommendation: Include the nginx additions in this phase's plan. Without them, swagger is only accessible on the raw gateway port (8080), not through the HTTPS domain.

---

## Environment Availability

Step 2.6: SKIPPED (no external tools/CLIs needed — this is a pure Java/YAML configuration change)

---

## Validation Architecture

> `workflow.nyquist_validation` is absent from config.json — treated as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (via spring-boot-starter-test) |
| Config file | none — uses Gradle `useJUnitPlatform()` in root build |
| Quick run command | `./gradlew :services:api-gateway:test` |
| Full suite command | `./gradlew :services:api-gateway:test :services:auth-service:test :services:academic-service:academic-app:test :services:schedule-service:schedule-app:test :services:attendance-service:attendance-app:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DOC-01 | Gateway serves Swagger UI HTML at /swagger-ui.html | smoke / integration | Manual — requires running services | ❌ Wave 0 |
| DOC-02 | springdoc 2.8.6 is in classpath of all services | build verification | `./gradlew dependencies --configuration runtimeClasspath \| grep springdoc` | ❌ Wave 0 |
| DOC-03 | springdoc-webflux-ui is added to Gateway | unit | Check Gateway startup with test that loads ApplicationContext | ❌ Wave 0 |

### Wave 0 Gaps

- [ ] `services/api-gateway/src/test/.../GatewaySwaggerIntegrationTest.java` — verify /swagger-ui.html returns 200 and swagger-config returns urls for all 4 services
- [ ] Version assertion test or Gradle dependency verification step in CI

*(Existing gateway tests: `PublicKeyConfigTest`, `JwtAuthenticationFilterTest` — cover unrelated concerns)*

---

## Security Domain

> `security_enforcement` is not set in config.json (absent = enabled).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes — indirectly | JWT filter must NOT bypass non-swagger routes; whitelist must be precise |
| V3 Session Management | no | — |
| V4 Access Control | yes | Swagger UI should be readable without auth (documentation is public), but "Try it out" routes are still JWT-protected at the service level |
| V5 Input Validation | no | No user input accepted |
| V6 Cryptography | no | — |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Over-broad JWT whitelist | Elevation of privilege | Use specific paths/prefixes; never whitelist `/api/**` |
| Internal api-docs exposure in production | Information disclosure | The api-docs proxy routes expose schema details — acceptable for a portfolio project; in a real enterprise environment these would be restricted to internal network |

---

## Project Constraints (from CLAUDE.md)

| Directive | Impact on This Phase |
|-----------|---------------------|
| Contract-first: controllers implement interface from contract; mappings ONLY in interface | No change — springdoc reads `@Operation` from interfaces already |
| No Lombok in `*-api-contract` modules | Not affected — springdoc dependency is in `*-app` modules only |
| Packages: `ru.rutcampustrack.{service}.{module}` | OpenAPI config bean goes in `ru.rutcampustrack.gateway.config` |
| Spring Boot 3.4 + Java 21 | springdoc 2.8.6 supports Spring Boot 3.x; 3.0.x would require Spring Boot 3.5+ |
| `@Operation`, `@ApiResponse` annotations in contract interfaces already | springdoc will pick these up automatically; no changes needed to contract modules |

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: Maven Central] — springdoc-openapi-starter-webflux-ui 2.8.6 and webmvc-ui 2.8.6 both confirmed to exist
- [VERIFIED: codebase] — all 5 services confirmed at 2.7.0 with `/api-docs` path; api-gateway has no springdoc dep
- [VERIFIED: codebase] — JwtAuthenticationFilter PUBLIC_PATHS and PUBLIC_PREFIXES confirmed
- [VERIFIED: codebase] — nginx default.conf confirmed: `/api/` → gateway, `/` catch-all → PWA
- [CITED: springdoc.org] — webflux-ui artifact name confirmed; latest 2.x is 2.8.16

### Secondary (MEDIUM confidence)
- [CITED: gleason.tech/2023-08-09-spring-cloud-gateway-swagger] — Gateway route pattern with RewritePath for api-docs proxy + relative URLs in swagger-ui.urls
- [CITED: medium.com/@erkndmrl/swagger-3-with-spring-cloud-gateway] — `springdoc.enable-native-support: true` + `config-url` configuration for Gateway

### Tertiary (LOW confidence)
- [ASSUMED] — `springdoc.enable-native-support: true` is required; not officially documented for non-native-image use

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Maven Central confirmed, codebase confirmed
- Architecture: HIGH — pattern verified against multiple sources, codebase routes confirmed
- Pitfalls: HIGH — derived from real codebase analysis (nginx catch-all, JWT filter, StripPrefix vs RewritePath)

**Research date:** 2026-04-08
**Valid until:** 2026-05-08 (springdoc 2.x line is stable; no urgent changes expected)
