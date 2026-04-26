# Stack Research

**Domain:** CI/CD, Production Docker, SSL, Monitoring & API Documentation for microservice university attendance system
**Researched:** 2026-04-07
**Confidence:** HIGH (most claims verified against official docs or multiple credible sources)

---

## Context: What Already Exists (Do NOT Re-add)

This is an additive milestone. The following are confirmed present in the codebase — do not duplicate:

**Java backend (all services):**
- `springdoc-openapi-starter-webmvc-ui:2.7.0` — already on auth, academic, schedule, attendance, notification-web
- `spring-boot-starter-actuator` — already on api-gateway and notification-web only
- `spring-boot-starter-actuator` is MISSING from: auth-service, academic-app, schedule-app, attendance-app

**Docker:**
- Single-stage `FROM eclipse-temurin:21-jre-alpine` Dockerfile on notification-web (copy JAR only, no build stage)
- Single-stage `FROM python:3.12-slim` Dockerfile on notification-bot
- `docker-compose.yml` handles dev infra (Postgres×2, Mongo, Redis, RabbitMQ) + nginx containers for all 4 frontends
- No `docker-compose.prod.yml` exists
- No Dockerfile exists for: api-gateway, auth-service, academic-app, schedule-app, attendance-app

**nginx:**
- `nginx:1.27-alpine` containers for PWA (port 80), mini-app (port 3000), web-panel (port 4200), landing (port 8081)
- No single reverse proxy — services are on separate ports
- No SSL anywhere

**GitHub Actions:** No `.github/` directory exists — CI/CD is entirely absent.

---

## 1. CI/CD — GitHub Actions

### Core Workflow Actions

| Action | Version | Purpose | Why |
|--------|---------|---------|-----|
| `actions/checkout` | v4 | Clone repo | Standard; v4 uses git sparse-checkout for large repos |
| `actions/setup-java` | v4 | Install Temurin 21 | Official; supports `distribution: temurin`, `java-version: 21` |
| `gradle/actions/setup-gradle` | v4 | Gradle build + dependency cache | Official Gradle action; caches `~/.gradle` using build file hashes; replaces deprecated `gradle/gradle-build-action`; v4 is current stable (v6 exists but licensing changed for caching in v6) |
| `actions/setup-node` | v4 | Node.js for frontend builds | `node-version: 22` (LTS); built-in caching with `cache: npm` |
| `actions/setup-python` | v5 | Python 3.12 for bot tests | `python-version: 3.12`; built-in pip caching |
| `docker/setup-buildx-action` | v3 | Docker BuildKit multi-stage | Required for `--mount=type=cache` and layer caching in CI |
| `docker/login-action` | v3 | Authenticate to registry | Supports Docker Hub, GHCR, any registry |
| `docker/build-push-action` | v6 | Build and push Docker images | BuildKit-based; supports `cache-from`/`cache-to` for GitHub cache |
| `appleboy/ssh-action` | v1 | SSH into VPS and run deploy | Most widely used SSH action; runs `docker compose pull && docker compose up -d` on VPS |

### Workflow Structure

Two separate workflows (not one monolithic file):

**`ci.yml`** — runs on every push/PR to main:
1. Java build + test (`./gradlew build`) — all 5 backend services via monorepo root
2. Python lint + test (`pytest`) — notification-bot
3. Frontend build + test — PWA, mini-app, web-panel (`npm ci && npm test && npm run build`)

**`deploy.yml`** — runs on push to main (after CI passes, or manual trigger):
1. Build multi-stage Docker images for all Java services
2. Push to registry (Docker Hub or GHCR)
3. SSH into VPS → `docker compose -f docker-compose.prod.yml pull && up -d`

### GitHub Actions Free Tier Note

Public repositories: unlimited minutes at no cost (confirmed as free regardless of 2026 pricing changes). This project is a portfolio project — keep it public to avoid minute limits on private repos (2,000 min/month free on Free plan).

---

## 2. Multi-Stage Dockerfiles — Java Services

The current single-stage Dockerfiles (copy JAR, run) only work if the JAR is pre-built locally. For CI, multi-stage builds that compile inside Docker are cleaner and reproducible.

### Recommended Pattern: Two-Stage Gradle Build

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Gradle wrapper and build files first (layer cache)
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY services/auth-service/ services/auth-service/
# (repeat per service in that service's Dockerfile)

RUN ./gradlew :services:auth-service:bootJar --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /workspace/services/auth-service/build/libs/*.jar app.jar
USER appuser
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Key decisions:**
- JDK in builder, JRE in runtime — reduces final image from ~600MB to ~200MB
- Non-root user (`appuser`) — security baseline for production
- `-x test` in CI Dockerfile — tests already ran in `ci.yml`; don't double-run in Docker build
- `--no-daemon` — Gradle daemon wastes memory in containers

### Alternative: Pre-build JAR in CI, COPY only in Dockerfile

Valid approach: `ci.yml` runs `./gradlew build`, then Docker just copies `build/libs/*.jar`. Simpler Dockerfiles, faster Docker build, no Gradle in Docker. **Use this approach** — it avoids caching complexity inside BuildKit and the Gradle cache in CI is already handled by `gradle/actions/setup-gradle`.

The notification-web Dockerfile already follows this pattern. Standardize all Java services to match.

---

## 3. docker-compose.prod.yml

### What Changes from docker-compose.yml

| Concern | Dev (`docker-compose.yml`) | Prod (`docker-compose.prod.yml`) |
|---------|---------------------------|----------------------------------|
| Service builds | `build: context: ./services/...` | `image: registry/rct-auth:latest` (pre-built) |
| Port exposure | Services on separate ports (80, 3000, 4200, 8081, 15672) | Only port 80 and 443 exposed; all traffic via reverse proxy nginx |
| RabbitMQ management UI | Port 15672 exposed | Remove — internal only |
| Env vars | Hardcoded dev defaults | `${VAR}` with no defaults — fail fast if .env missing |
| Restart policy | `unless-stopped` (already set) | `unless-stopped` (keep) |
| Health checks | Already present | Keep; add `start_period` tuning |
| Volume mounts | Local `./dist` folders for nginx | Same (built artifacts copied in) |

### New Service: Reverse Proxy nginx

Add a single `nginx-proxy` container that:
- Listens on port 80 and 443
- Terminates SSL
- Routes by domain/path to backend services and frontends
- Replaces the separate per-frontend nginx containers on individual ports

All other nginx containers become internal (remove `ports:`, keep `expose:`).

---

## 4. SSL — nginx + Certbot (Let's Encrypt)

### Recommended Approach: Webroot with nginx

| Component | Technology | Version | Why |
|-----------|-----------|---------|-----|
| SSL certificates | Let's Encrypt via Certbot | `certbot/certbot:latest` Docker image | Free, auto-renew, 90-day certs; industry standard |
| Challenge method | Webroot | — | nginx stays running during renewal; safer than standalone (which requires stopping nginx) |
| nginx config | Two-phase startup | — | HTTP-only initially to pass ACME challenge, then add HTTPS after first cert issue |
| Auto-renewal | `certbot renew` via cron or systemd timer on VPS | — | Certbot Docker image + cron = simplest for single VPS |

### Certbot docker-compose service

```yaml
certbot:
  image: certbot/certbot:latest
  volumes:
    - certbot-certs:/etc/letsencrypt
    - certbot-webroot:/var/www/certbot
  # Run once to obtain cert, then exit; renewal via cron
  entrypoint: /bin/sh -c "trap exit TERM; while :; do certbot renew; sleep 12h & wait $${!}; done"
```

nginx mounts `certbot-certs` read-only for SSL certs and `certbot-webroot` for ACME challenge files.

**Do NOT use:** Traefik (adds complexity, magic config, harder to debug for solo dev), nginx-proxy-manager (GUI tool, not infrastructure-as-code), standalone challenge (requires nginx downtime).

---

## 5. Spring Boot Actuator — Missing Services

### Current State Gap

Actuator is present on: api-gateway, notification-web.
Actuator is MISSING on: auth-service, academic-app, schedule-app, attendance-app.

All 5 Java backend services need actuator for health checks (used in `docker-compose.yml` `healthcheck:` blocks and future monitoring).

### Dependency to Add (5 services missing it)

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

### Production Actuator Config (application-prod.yml or env override)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
      base-path: /actuator
  endpoint:
    health:
      show-details: never      # Never expose internals publicly
      probes:
        enabled: true          # /actuator/health/liveness + /actuator/health/readiness
  info:
    env:
      enabled: true
```

Only expose `health`, `info`, `metrics` — never expose `env`, `beans`, `heapdump`, `threaddump` publicly. The actuator port must NOT be routed through the public nginx (bind actuator to a separate management port or keep it internal-only).

### Management Port Isolation

```yaml
management:
  server:
    port: 9099   # Different from service port; not exposed in docker-compose ports
```

This keeps actuator entirely on the private Docker network — only accessible from other containers (e.g., healthcheck scripts, Prometheus scrape from within the private_net).

---

## 6. Swagger UI Aggregation via API Gateway

### How It Works

springdoc-openapi supports a gateway aggregation pattern where the API Gateway serves a single Swagger UI that loads OpenAPI specs from each downstream service.

Each service already exposes `/api-docs` (verified in academic-app application.yml: `springdoc.api-docs.path: /api-docs`).

The Gateway must proxy `/api-docs/{service}` → `http://{service}:{port}/api-docs`.

### Dependency for API Gateway

```kotlin
// API Gateway uses Spring Cloud Gateway (WebFlux) — needs webflux variant
implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.6")
```

Version 2.8.x is the current stable series (2.8.16 is latest as of 2026-02-27). Bump all services from 2.7.0 → 2.8.6 (or latest 2.8.x) for consistency. The 2.7→2.8 bump is non-breaking for this use case.

### Gateway application.yml addition

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    urls:
      - name: auth
        url: /api-docs/auth
      - name: academic
        url: /api-docs/academic
      - name: schedule
        url: /api-docs/schedule
      - name: attendance
        url: /api-docs/attendance
      - name: notification
        url: /api-docs/notification
  api-docs:
    enabled: false   # Gateway itself has no API endpoints to document
```

Add corresponding Gateway routes proxying `/api-docs/{service}` to each service's `/api-docs` endpoint (without StripPrefix).

### Version Alignment

| Service | Current | Target | Change |
|---------|---------|--------|--------|
| auth-service | 2.7.0 | 2.8.6 | Upgrade |
| academic-app | 2.7.0 | 2.8.6 | Upgrade |
| schedule-app | 2.7.0 | 2.8.6 | Upgrade |
| attendance-app | 2.7.0 | 2.8.6 | Upgrade |
| notification-app | 2.7.0 | 2.8.6 | Upgrade |
| api-gateway | — | 2.8.6 (webflux-ui) | New addition |

---

## 7. README Documentation

README is Markdown — no library dependencies. Key structural decision: what to include.

### Recommended README Structure

```
# RutCampusTrack

## Architecture diagram (ASCII or SVG embedded)
## Tech stack table
## Quick start (docker compose up)
## Services table (ports, responsibilities)
## API documentation (link to Swagger UI)
## Development setup (Java, Node, Python)
## Deployment guide (VPS, SSL, env vars)
## Testing (how to run each test suite)
```

**Do NOT:** Write the README as code (use the Write tool). Do not include implementation details that belong in `docs/architecture/architecture.md` — README links to it. Keep Quick Start under 10 commands.

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| CI/CD platform | GitHub Actions | Jenkins, GitLab CI, CircleCI | Free for public repos; zero infra to maintain; native GitHub integration |
| SSL management | Certbot/nginx | Traefik | Traefik adds a complex proxy layer; for solo VPS with static routes, nginx + certbot is simpler and debuggable |
| SSL management | Certbot/nginx | Nginx Proxy Manager | NPM is GUI-based, not infrastructure-as-code; harder to automate in CI |
| Docker registry | GHCR (GitHub Container Registry) | Docker Hub | GHCR is free for public repos and integrated with GitHub Actions auth; Docker Hub free tier rate-limits pulls |
| Actuator exposure | management.server.port isolation | Separate security filter chain | Port isolation is simpler and doesn't require Spring Security config changes |
| Swagger aggregation | springdoc webflux-ui at gateway | Separate docs service | Gateway is the natural aggregation point; no additional service needed |
| Gradle CI action | `gradle/actions/setup-gradle@v4` | `gradle/actions/setup-gradle@v6` | v6 changed caching to proprietary license; v4 is stable and fully open |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Kubernetes / Helm | Massive overkill for single VPS; solo developer | `docker compose` on VPS via SSH |
| Prometheus + Grafana stack | Adds 4+ containers; monitoring needs are basic for MVP | Actuator `/health` + `/metrics` endpoints readable directly |
| Jaeger / distributed tracing | Complex setup; no tracing requirements in v8.0 scope | Structured logging only |
| `management.endpoints.web.exposure.include: *` | Exposes sensitive endpoints (env, beans, heapdump) | Explicit list: health, info, metrics |
| `springdoc-openapi-starter-webmvc-ui` at gateway | Gateway is WebFlux (reactive); webmvc won't work | `springdoc-openapi-starter-webflux-ui` |
| Gradle `v6` caching action | Caching component moved to proprietary license in v6 | `gradle/actions/setup-gradle@v4` |
| Standalone certbot challenge | Requires nginx downtime for renewal | Webroot challenge (nginx stays up) |

---

## Version Compatibility

| Package | Version | Compatible With | Notes |
|---------|---------|-----------------|-------|
| `springdoc-openapi-starter-webflux-ui` | 2.8.6 | Spring Boot 3.4, Spring Cloud Gateway 2024.0.0 | WebFlux variant required for reactive Gateway |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.6 | Spring Boot 3.4, Java 21 | WebMVC variant for all non-Gateway services |
| `spring-boot-starter-actuator` | managed by Spring Boot 3.4 BOM | No explicit version needed | BOM already in all service builds |
| `actions/setup-java@v4` | v4 | ubuntu-latest runner, Temurin 21 | `distribution: temurin`, `java-version: 21` |
| `gradle/actions/setup-gradle@v4` | v4 | Gradle wrapper in repo | Caches `~/.gradle`; open source caching |
| `docker/build-push-action@v6` | v6 | `docker/setup-buildx-action@v3` | Must pair with buildx setup action |
| `appleboy/ssh-action@v1` | v1 | Any Linux VPS | Stable; IPv6 support added Jan 2026 |
| `certbot/certbot` | latest | nginx:1.27-alpine | Webroot challenge; mount shared volume |

---

## Sources

- [springdoc.org](https://springdoc.org/) — confirmed v2.8.16 latest stable, webflux-ui for Gateway (HIGH confidence)
- [springdoc CHANGELOG](https://github.com/springdoc/springdoc-openapi/blob/main/CHANGELOG.md) — 2.8.16 released 2026-02-27 (HIGH confidence)
- [Baeldung: Spring Cloud Gateway + OpenAPI](https://www.baeldung.com/spring-cloud-gateway-integrate-openapi) — aggregation pattern via springdoc.swagger-ui.urls (MEDIUM confidence — 403 on direct fetch, confirmed via WebSearch summary)
- [gradle/actions README](https://github.com/gradle/actions) — setup-gradle v4 vs v6 licensing difference (HIGH confidence)
- [Gradle Blog: GitHub Actions v6](https://blog.gradle.org/github-actions-for-gradle-v6) — caching proprietary license in v6 (HIGH confidence)
- [Spring Boot Actuator docs](https://docs.spring.io/spring-boot/reference/actuator/index.html) — endpoint exposure, management port config (HIGH confidence)
- [GitHub Actions billing docs](https://docs.github.com/billing/managing-billing-for-github-actions/about-billing-for-github-actions) — public repos unlimited free (HIGH confidence)
- [appleboy/ssh-action releases](https://github.com/appleboy/ssh-action/releases) — v1 stable, updated Jan 2026 (HIGH confidence)
- [Let's Encrypt Certbot Docker](https://community.letsencrypt.org/t/nginx-and-certbot-with-docker/214552) — webroot vs standalone pattern (MEDIUM confidence)
- [eclipse-temurin Docker Hub](https://hub.docker.com/_/eclipse-temurin) — 21-jdk-alpine (build), 21-jre-alpine (runtime) images (HIGH confidence)

---
*Stack research for: RutCampusTrack v8.0 — CI/CD, Deployment & Documentation*
*Researched: 2026-04-07*
