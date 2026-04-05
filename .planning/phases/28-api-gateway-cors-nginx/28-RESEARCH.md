# Phase 28: API Gateway CORS + nginx - Research

**Researched:** 2026-04-05
**Domain:** Spring Cloud Gateway CORS, nginx static serving, docker-compose
**Confidence:** HIGH

## Summary

Phase 28 is a pure infrastructure configuration phase with no new business logic. It has two independent concerns:

1. **CORS on the API Gateway** — add `spring.cloud.gateway.globalcors` plus `DedupeResponseHeader` default filter to `application.yml`. The JWT filter already handles OPTIONS pass-through correctly (OPTIONS is not in `PUBLIC_PATHS` but the CORS filter runs at a lower order). One critical flag: `add-to-simple-url-handler-mapping: true` is required for OPTIONS preflight requests to pass through when route predicates do not match the OPTIONS method natively.

2. **nginx static container** — add a new `pwa-nginx` service to `docker-compose.yml` using `nginx:alpine`, mount a minimal `nginx.conf` that sets `Cache-Control: no-cache` for `sw.js` and `index.html`, and serve a placeholder `index.html` from `frontends/pwa/`. No build step is needed for this phase (Phase 29 adds the React build pipeline).

**Primary recommendation:** Configure CORS in `application.yml` only (no Java code changes). Add nginx service with a bind-mount config. Both changes are YAML/config only — no Java code to write.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFRA-01 | API Gateway CORS configured for PWA origin | `spring.cloud.gateway.globalcors` + `add-to-simple-url-handler-mapping: true` + `DedupeResponseHeader` default filter |
| INFRA-03 | PWA served via nginx container in docker-compose | `nginx:alpine` container with bind-mount `nginx.conf`; serve `frontends/pwa/dist` (placeholder `index.html` for this phase) |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Spring Boot 3.4 + Spring Cloud 2024.0.0 (= Spring Cloud Gateway 4.3.x) [VERIFIED: build.gradle.kts]
- No Lombok in contract modules; Lombok allowed in `*-app` only
- REST paths: `/api/{service}/...` through Gateway
- Contract-first: controller implements interface; URL mappings only in the interface
- `docker-compose.yml` uses `private_net` bridge network for all services
- Notification-web rebuilt to `services/notification-service/notification-app` (from Phase 27)

## Standard Stack

### Core
| Library/Tool | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-cloud-starter-gateway | 4.3.x (via BOM 2024.0.0) | API Gateway routing, CORS, filters | Already in project [VERIFIED: build.gradle.kts] |
| nginx:alpine | latest stable (~1.27) | Serve static PWA build | Minimal image (~12 MB), official, standard for static PWA hosting [VERIFIED: Docker Hub] |

### Supporting
| Tool | Purpose |
|------|---------|
| `nginx:alpine` bind-mount config | Avoids rebuilding image for config changes during dev |
| `volumes` + `bind` in docker-compose | Maps `frontends/pwa/dist` into nginx container |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `globalcors` in YAML | `CorsWebFilter` Java bean | Java bean gives more control but is unnecessary complexity for a single origin |
| `nginx:alpine` bind-mount | Build-time COPY in Dockerfile | Dockerfile adds build step not needed until Phase 29; bind-mount is fine for dev |

**Version verification:**
```bash
# Spring Cloud Gateway version resolved from BOM 2024.0.0 — corresponds to SCG 4.3.x
# nginx:alpine — use latest tag; pin to nginx:1.27-alpine for reproducibility if desired
```

## Architecture Patterns

### Recommended Project Structure Changes

```
services/api-gateway/src/main/resources/
└── application.yml          # Add globalcors + default-filters sections

frontends/pwa/
├── dist/                    # Created in Phase 29 (React build output)
│   └── index.html           # Placeholder for Phase 28 verification
└── nginx.conf               # New — nginx config for Phase 28

docker-compose.yml           # Add pwa-nginx service
```

### Pattern 1: Spring Cloud Gateway globalcors

**What:** Global CORS configuration applied to all routes via `spring.cloud.gateway.globalcors`.
**When to use:** When a single frontend origin needs access to all backend routes through the gateway. Preferred over per-route CORS because it is centralized and reduces duplication.

```yaml
# Source: https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/cors-configuration.html
spring:
  cloud:
    gateway:
      globalcors:
        add-to-simple-url-handler-mapping: true   # Required for OPTIONS preflight
        cors-configurations:
          '[/**]':
            allowed-origins:
              - "http://localhost:5173"            # Vite dev server (Phase 29 onward)
              - "http://localhost:80"              # nginx container
            allowed-methods:
              - GET
              - POST
              - PUT
              - PATCH
              - DELETE
              - OPTIONS
            allowed-headers: "*"
            allow-credentials: true
            max-age: 3600
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE
```

**Critical flag — `add-to-simple-url-handler-mapping: true`:** Without this, OPTIONS preflight requests are handled by Spring's `SimpleUrlHandlerMapping` before they reach gateway routes. Setting this flag registers the CORS handler there too, ensuring preflight returns `200 OK` with CORS headers instead of `404`. [CITED: docs.spring.io/spring-cloud-gateway CORS page]

**`DedupeResponseHeader` default filter:** When `notification-web` or other backend services also set CORS headers (e.g., WebSocketConfig's CORS setup), both gateway and backend headers appear in the response. `RETAIN_UNIQUE` keeps one value, prevents browser rejection. [CITED: docs.spring.io DedupeResponseHeader factory page]

### Pattern 2: OPTIONS Passthrough in JwtAuthenticationFilter

**What:** The existing `JwtAuthenticationFilter` blocks requests without a `Bearer` token. OPTIONS preflight requests do not carry `Authorization` headers. The filter must pass OPTIONS through.

**Current state:** OPTIONS is NOT in `PUBLIC_PATHS` and NOT in `PUBLIC_PREFIXES`. This means a preflight to `/api/push/vapid-public-key` returns `401` before CORS headers are written, breaking the browser CORS handshake. [VERIFIED: JwtAuthenticationFilter.java L31-39]

**Fix required:** Add OPTIONS method check at the top of `filter()`:

```java
// Source: derived from Spring Security reactive CORS pattern
if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
    return chain.filter(exchange);
}
```

This must run before the JWT check. The `@Order(-100)` already runs the filter early, but OPTIONS must be let through at the logic level.

### Pattern 3: nginx Static Serving with PWA Cache Rules

**What:** `nginx:alpine` container serving `frontends/pwa/dist/` with specific cache headers.
**When to use:** Standard pattern for serving React/Vite PWA builds in containers.

```nginx
# Source: nginx official docs + PWA best practices
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # Service worker and entry point — must not be cached
    location ~* ^/(sw\.js|index\.html)$ {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        add_header Pragma "no-cache";
        expires 0;
    }

    # Static assets (JS/CSS with content hash) — cache aggressively
    location ~* \.(js|css|png|jpg|jpeg|svg|ico|woff|woff2)$ {
        add_header Cache-Control "public, max-age=31536000, immutable";
    }

    # SPA fallback — all routes go to index.html
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

**docker-compose service block:**

```yaml
pwa-nginx:
  image: nginx:1.27-alpine
  container_name: rct-pwa-nginx
  ports:
    - "80:80"
  volumes:
    - ./frontends/pwa/dist:/usr/share/nginx/html:ro
    - ./frontends/pwa/nginx.conf:/etc/nginx/conf.d/default.conf:ro
  networks:
    - private_net
  restart: unless-stopped
```

**Phase 28 placeholder:** `frontends/pwa/dist/index.html` is a minimal static HTML file created manually. No React build is required for this phase's success criterion (just verify nginx serves it at `http://localhost:80`).

### Pattern 4: Existing Route Verification — /api/push/**

**What:** The `notification-push` route already exists in `application.yml` (added in Phase 27):

```yaml
- id: notification-push
  uri: http://notification-web:9094
  predicates:
    - Path=/api/push/**
  filters:
    - StripPrefix=1
```

`StripPrefix=1` strips one path segment. `/api/push/vapid-public-key` → `/push/vapid-public-key`. The `PushApi` is mapped at `@RequestMapping("/push")` with `@GetMapping("/vapid-public-key")`, so the controller handles `/push/vapid-public-key`. This is correct. [VERIFIED: application.yml + PushApi.java]

**Success criterion wording:** "StripPrefix removes `/api/push` leaving `/vapid-public-key`" — this describes the visible effect from the outside, not the actual prefix count. The planner should verify the route works end-to-end, not change `StripPrefix=2`.

### Anti-Patterns to Avoid

- **Setting CORS in individual services AND gateway:** Causes duplicate headers. Backend services (notification-web WebSocketConfig already has CORS for WebSocket) — DedupeResponseHeader is the mitigation, not removing the backend CORS config (which is needed for direct WebSocket connections).
- **Missing `add-to-simple-url-handler-mapping: true`:** OPTIONS returns 404/401 instead of 200 with CORS headers, breaking preflight silently.
- **Forgetting OPTIONS in JwtAuthenticationFilter:** The filter runs at order -100 (before everything). OPTIONS requests from browsers carry no auth headers. Without a bypass, preflight returns 401 and the browser never sends the real request.
- **Caching sw.js or index.html:** Service workers fetched with stale cache break PWA updates. These two files MUST have `Cache-Control: no-cache`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| CORS response headers | Custom CORS filter bean | `spring.cloud.gateway.globalcors` YAML | Built-in gateway feature, handles OPTIONS, preflight, headers correctly |
| Header deduplication | Custom filter to strip duplicate headers | `DedupeResponseHeader` built-in filter | Already handles RETAIN_FIRST/LAST/UNIQUE strategies |
| nginx cache rules | Custom HTTP server | nginx `location` blocks with `add_header` | nginx handles this in 3 lines of config |

## Common Pitfalls

### Pitfall 1: OPTIONS Blocked by JwtAuthenticationFilter
**What goes wrong:** Browser sends OPTIONS preflight with no `Authorization` header. Gateway filter returns `401`. Browser never sends the real request. Network tab shows no CORS error — just a 401 on OPTIONS.
**Why it happens:** `JwtAuthenticationFilter` only allows specific PUBLIC_PATHS. OPTIONS is not in the list.
**How to avoid:** Add `HttpMethod.OPTIONS` bypass at the top of the filter method.
**Warning signs:** Preflight shows 401 in browser devtools Network tab; real request never fires.

### Pitfall 2: Duplicate Access-Control-Allow-Origin Headers
**What goes wrong:** Backend service returns its own CORS headers. Gateway adds another. Browser sees `Access-Control-Allow-Origin: http://localhost:5173, http://localhost:5173` and rejects it (multiple values are invalid per spec).
**Why it happens:** `notification-web`'s `WebSocketConfig` sets CORS origins for STOMP WebSocket, and these headers may bleed into HTTP responses.
**How to avoid:** Use `DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE` as a default filter.
**Warning signs:** Browser console: "The 'Access-Control-Allow-Origin' header contains multiple values".

### Pitfall 3: `add-to-simple-url-handler-mapping: false` (default)
**What goes wrong:** OPTIONS requests to routes whose predicates don't explicitly allow OPTIONS (e.g., `Path=/api/push/**` only has GET/POST defined) return `404` or fall through to an error handler before CORS headers are applied.
**Why it happens:** Spring's `SimpleUrlHandlerMapping` handles OPTIONS before gateway route matching unless this flag is set.
**How to avoid:** Always include `add-to-simple-url-handler-mapping: true` when configuring `globalcors`.
**Warning signs:** OPTIONS returns 404 even though GET to the same path works.

### Pitfall 4: nginx dist/ directory not created
**What goes wrong:** `docker compose up` fails or nginx serves empty directory, `http://localhost:80` returns 403.
**Why it happens:** `frontends/pwa/dist/` doesn't exist until Phase 29 builds the React app.
**How to avoid:** Create `frontends/pwa/dist/index.html` as a placeholder HTML file as part of Phase 28 Wave 0.
**Warning signs:** `docker compose up` nginx container exits with error, or curl returns 403.

### Pitfall 5: StripPrefix=2 on /api/push/** route
**What goes wrong:** Changing StripPrefix to 2 strips both `/api` and `/push`, sending `/vapid-public-key` to notification-web. The controller is mapped at `/push/vapid-public-key` so it returns 404.
**Why it happens:** Misreading the success criterion — "StripPrefix removes /api/push" describes the net effect from the gateway side, not `StripPrefix=2`.
**How to avoid:** Keep existing `StripPrefix=1`. Verify end-to-end with a curl through the gateway.
**Warning signs:** Gateway returns 404 for `/api/push/vapid-public-key`.

## Code Examples

### Complete updated application.yml (api-gateway)

```yaml
# Source: existing file + spring.cloud.gateway.globalcors docs
server:
  port: 8080

spring:
  application:
    name: api-gateway

  cloud:
    gateway:
      globalcors:
        add-to-simple-url-handler-mapping: true
        cors-configurations:
          '[/**]':
            allowed-origins:
              - "http://localhost:5173"
              - "http://localhost:80"
            allowed-methods:
              - GET
              - POST
              - PUT
              - PATCH
              - DELETE
              - OPTIONS
            allowed-headers: "*"
            allow-credentials: true
            max-age: 3600
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE
      routes:
        # ... existing routes unchanged ...
```

### JwtAuthenticationFilter OPTIONS bypass

```java
// Add as first check in filter() method, before isPublicRoute()
if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
    return chain.filter(exchange);
}
```

Import: `org.springframework.http.HttpMethod`

### frontends/pwa/dist/index.html (placeholder)

```html
<!DOCTYPE html>
<html lang="ru">
<head><meta charset="UTF-8"><title>RutTrack</title></head>
<body><h1>RutTrack PWA placeholder</h1></body>
</html>
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Per-route CORS via `metadata.cors` | Global CORS via `globalcors` | SCG 2.x → 3.x | Centralizes policy; still supported |
| Custom `CorsWebFilter` bean | YAML `globalcors` | SCG 3.0+ | No Java config needed for standard cases |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `notification-web`'s `WebSocketConfig` may set HTTP CORS headers that bleed into REST responses, requiring DedupeResponseHeader | Pitfall 2 | If it doesn't, DedupeResponseHeader is harmless but not required |
| A2 | `frontends/pwa/dist/` does not exist yet (Phase 29 creates it) | Pitfall 4 | If already exists, no action needed |
| A3 | nginx bind-mount approach is acceptable for dev; Phase 29 may switch to multi-stage Docker build | Pattern 3 | If prod-ready Docker required now, add Dockerfile |

## Open Questions

1. **allowed-origins scope for production**
   - What we know: Phase 28 uses `localhost:5173` and `localhost:80` for dev
   - What's unclear: Whether a production domain (e.g., `https://rut.ru`) should be added now
   - Recommendation: Dev-only origins for this phase; add production origin in Phase 9 (CI/CD)

2. **`allow-credentials: true` with wildcard origins**
   - What we know: `allow-credentials: true` is incompatible with `allowedOrigins: "*"`
   - What's unclear: Whether credentials (cookies for refresh token) will be sent from the PWA
   - Recommendation: Use explicit origins (as shown), not wildcard, when `allow-credentials: true` [VERIFIED: CORS spec — credentials + wildcard is rejected by browser]

## Environment Availability

| Dependency | Required By | Available | Fallback |
|------------|------------|-----------|----------|
| Docker / docker compose | nginx container | Assumed available (already used in project) | — |
| nginx:1.27-alpine | INFRA-03 | Pulled on `docker compose up` | — |
| `frontends/pwa/dist/` dir | nginx volume mount | Does not exist yet — must create placeholder | Create dir + index.html manually |

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (existing) |
| Config file | `services/api-gateway/src/test/` (existing) |
| Quick run command | `./gradlew :services:api-gateway:test` |
| Full suite command | `./gradlew :services:api-gateway:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INFRA-01 | OPTIONS preflight to any `/api/**` path returns 200 with `Access-Control-Allow-Origin` | unit | `./gradlew :services:api-gateway:test` | ❌ Wave 0 |
| INFRA-01 | OPTIONS request is NOT blocked by JwtAuthenticationFilter (new bypass code) | unit | `./gradlew :services:api-gateway:test` | ❌ Wave 0 |
| INFRA-01 | No duplicate `Access-Control-Allow-Origin` headers on response | unit | `./gradlew :services:api-gateway:test` (JwtAuthenticationFilterTest) | ❌ Wave 0 |
| INFRA-03 | nginx container starts and serves index.html at localhost:80 | smoke/manual | `curl -I http://localhost:80` after `docker compose up pwa-nginx` | manual |

### Sampling Rate
- **Per task commit:** `./gradlew :services:api-gateway:test`
- **Per wave merge:** `./gradlew :services:api-gateway:test`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `CorsFilterTest.java` in `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/` — covers INFRA-01 (OPTIONS passthrough, CORS headers present)
- [ ] Add OPTIONS test case to existing `JwtAuthenticationFilterTest.java` — verifies bypass works

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | yes | OPTIONS bypass must not weaken JWT checks on non-OPTIONS methods |
| V5 Input Validation | no | — |
| V6 Cryptography | no | — |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| CORS misconfiguration (wildcard with credentials) | Information Disclosure | Use explicit origins, not `*`, when `allow-credentials: true` |
| OPTIONS bypass weakening auth | Elevation of Privilege | Bypass only on `HttpMethod.OPTIONS` — not on any other method |
| Over-broad allowed-origins | Information Disclosure | List only known dev origins; add prod origin explicitly when deploying |

## Sources

### Primary (HIGH confidence)
- [Spring Cloud Gateway CORS Configuration](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/cors-configuration.html) — globalcors, add-to-simple-url-handler-mapping, OPTIONS handling
- [Spring Cloud Gateway DedupeResponseHeader](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/deduperesponseheader-factory.html) — RETAIN_UNIQUE strategy
- `services/api-gateway/src/main/resources/application.yml` — existing routes (verified in session)
- `services/api-gateway/src/main/java/.../JwtAuthenticationFilter.java` — OPTIONS gap (verified in session)
- `services/notification-service/notification-api-contract/src/main/java/.../PushApi.java` — route mapping (verified in session)

### Secondary (MEDIUM confidence)
- [Spring Cloud 2024.0.0 release](https://spring.io/blog/2024/12/03/spring-cloud-2024-0-0/) — confirms SCG 4.3.x in BOM 2024.0.0
- Docker Hub nginx — nginx:alpine image details

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — both Spring Cloud Gateway and nginx are well-documented, already in use in this project
- Architecture: HIGH — config-only phase; YAML patterns verified against official docs
- Pitfalls: HIGH — OPTIONS/CORS issues are well-known; filter code verified in session
- nginx config: HIGH — standard PWA nginx pattern with Cache-Control

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (stable stack, low churn)
