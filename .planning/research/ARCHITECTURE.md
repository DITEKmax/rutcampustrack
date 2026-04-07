# Architecture Research

**Domain:** CI/CD, Production Deployment & Documentation — v8.0 milestone for RutCampusTrack monorepo
**Researched:** 2026-04-07
**Confidence:** HIGH (codebase inspection + official docs + web verification)

---

## Standard Architecture

### System Overview — v8.0 Production Topology

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         GITHUB ACTIONS CI                               │
│                                                                         │
│  push to main                                                           │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────────────┐    │
│  │  Java build  │   │ Python lint  │   │  Frontend build + test   │    │
│  │  + test      │   │ + test       │   │  (pwa, mini-app,         │    │
│  │  (Gradle)    │   │  (pytest)    │   │   web-panel, vitest)     │    │
│  └──────┬───────┘   └──────┬───────┘   └────────────┬─────────────┘    │
│         └──────────────────┴──────────────────────────┘                 │
│                             │  all pass                                 │
│                             ▼                                           │
│                  ┌──────────────────────┐                               │
│                  │  Deploy job (SSH)    │                               │
│                  │  scp docker-compose  │                               │
│                  │  ssh: pull + up -d   │                               │
│                  └──────────────────────┘                               │
└─────────────────────────────────────────────────────────────────────────┘
                              │  SSH + docker pull
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        VPS (single server)                              │
│                                                                         │
│  INTERNET                                                               │
│  :80 (HTTP) ──────────────────────────────────────────────────────────┐ │
│  :443 (HTTPS) ────────────────────────────────────────────────────┐   │ │
│                                                                   │   │ │
│  ┌────────────────────────────────────────────────────────────────▼───▼┐│
│  │              nginx-proxy (edge, :80/:443)                           ││
│  │  Let's Encrypt certs from /etc/letsencrypt (certbot volume)        ││
│  │                                                                     ││
│  │  / ──────────────→ landing-nginx:80                                ││
│  │  /app ────────────→ pwa-nginx:80                                   ││
│  │  /panel ──────────→ web-panel-nginx:80                             ││
│  │  /miniapp ────────→ mini-app-nginx:80                              ││
│  │  /api/** ─────────→ api-gateway:8080                               ││
│  │  /docs ───────────→ api-gateway:8080/swagger-ui.html              ││
│  └─────────────────────────────────────────────────────────────────────┘│
│                                                                          │
│  PRIVATE DOCKER NETWORK (private_net)                                   │
│                                                                         │
│  ┌────────────────┐   ┌──────────────────────────────────────────────┐  │
│  │  api-gateway   │   │               BACKEND SERVICES               │  │
│  │  :8080         │   │                                              │  │
│  │  Actuator      │   │  auth-service      :9090  (Actuator health)  │  │
│  │  /actuator/    │   │  academic-service  :9091  (Actuator health)  │  │
│  │  health        │   │  schedule-service  :9092  (Actuator health)  │  │
│  │  Swagger agg.  │   │  attendance-service:9093  (Actuator health)  │  │
│  │  /swagger-ui   │   │  notification-web  :9094  (Actuator health)  │  │
│  └────────────────┘   └──────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │ pwa-nginx    │  │web-panel-nginx│  │landing-nginx │  mini-app-nginx  │
│  │ :80          │  │ :80          │  │ :80          │  :80             │
│  └──────────────┘  └──────────────┘  └──────────────┘                  │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  INFRA (private_net only, no public ports)                        │  │
│  │  postgres-academic  postgres-schedule  mongo-attendance           │  │
│  │  redis              rabbitmq                                      │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────┐  ┌──────────────────┐                                 │
│  │notification- │  │ notification-bot │                                 │
│  │ web :9094    │  │ (Python Aiogram) │                                 │
│  └──────────────┘  └──────────────────┘                                 │
└─────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | New vs Modified |
|-----------|----------------|-----------------|
| nginx-proxy (edge) | Single entry point, SSL termination, path-based routing to backends and frontends | NEW |
| api-gateway :8080 | JWT validation, CORS, routing, Swagger aggregation at /swagger-ui | MODIFIED (add springdoc-webflux-ui, aggregation config) |
| auth-service :9090 | JWT, OTP, TMA — Actuator /health only | MODIFIED (add Actuator config) |
| academic-service :9091 | Academic CRUD, gRPC server — Actuator /health only | MODIFIED (add Actuator config) |
| schedule-service :9092 | Schedule CRUD, cron — Actuator /health only | MODIFIED (add Actuator config) |
| attendance-service :9093 | Geo-checkin, reports — Actuator /health only | MODIFIED (add Actuator config) |
| notification-web :9094 | WebSocket/STOMP, Web Push — Actuator /health (already has basic config) | MODIFIED (standardize config) |
| notification-bot (Python) | Telegram bot — existing health at /health :8081 | UNCHANGED |
| pwa-nginx | Serves React PWA dist/ | UNCHANGED |
| web-panel-nginx | Serves Angular Web Panel dist/ | UNCHANGED |
| mini-app-nginx | Serves React Mini App dist/ | UNCHANGED |
| landing-nginx | Serves static landing dist/ | UNCHANGED |
| GitHub Actions CI | Build + test + lint + deploy pipeline | NEW |
| docker-compose.prod.yml | Production-ready compose with no dev ports, prod env vars | NEW |
| Dockerfiles (Java services) | Multi-stage: build stage (Gradle) + runtime stage (JRE) | NEW (5 new Dockerfiles) |
| certbot | Let's Encrypt certificate renewal via HTTP-01 challenge | NEW |

---

## Recommended Project Structure

```
rutcampustrack/
├── .github/
│   └── workflows/
│       ├── ci.yml                    # Build + test on PR and push to main
│       └── deploy.yml                # Deploy on push to main (after CI passes)
├── docker/
│   └── nginx-proxy/
│       └── nginx.conf                # Edge nginx: SSL + path routing
├── docker-compose.yml                # Existing dev compose (keep as-is)
├── docker-compose.prod.yml           # Production compose (new)
├── services/
│   ├── api-gateway/
│   │   └── Dockerfile                # NEW: multi-stage (gradle → JRE)
│   ├── auth-service/
│   │   └── Dockerfile                # NEW: multi-stage
│   ├── academic-service/
│   │   └── Dockerfile                # NEW: multi-stage (academic-app)
│   ├── schedule-service/
│   │   └── Dockerfile                # NEW: multi-stage (schedule-app)
│   ├── attendance-service/
│   │   └── Dockerfile                # NEW: multi-stage (attendance-app)
│   ├── notification-service/
│   │   └── notification-app/
│   │       └── Dockerfile            # EXISTING: already has single-stage, upgrade to multi-stage
│   └── notification-bot/
│       └── Dockerfile                # EXISTING: already correct
└── frontends/
    ├── pwa/                          # Existing nginx.conf + dist/
    ├── mini-app/                     # Existing nginx.conf + dist/
    ├── web-panel/                    # Existing nginx.conf + dist/
    └── landing/                      # Existing nginx.conf + dist/
```

### Structure Rationale

- **.github/workflows/**: Standard GitHub Actions location. Two files to separate CI concerns (build/test) from deployment.
- **docker/nginx-proxy/**: Edge nginx config separate from per-frontend nginx configs. This nginx handles SSL and routes; inner nginx containers handle SPA-specific caching rules.
- **docker-compose.prod.yml**: Separate from dev compose to avoid accidentally exposing dev ports (RabbitMQ management :15672) or using dev credentials in prod.
- **Dockerfiles at service root level**: Convention — Dockerfile lives next to the module it builds. For multi-module services (academic-app inside academic-service/), the Dockerfile goes in the `*-app/` directory to keep context minimal.

---

## Architectural Patterns

### Pattern 1: Multi-Stage Gradle Dockerfile

**What:** Two-stage build. Stage 1 (builder): full Gradle + JDK to compile and run tests. Stage 2 (runtime): minimal JRE only, copies the fat JAR.

**When to use:** All 5 Spring Boot services + api-gateway.

**Trade-offs:** Build stage is large (~700MB) but discarded. Runtime image is ~200MB. Cache invalidation: Gradle builds re-run when source files change (Docker layer cache). Building all services in one pass via root Gradle saves time because the proto stubs are shared.

**Monorepo complication:** Each service Dockerfile needs the full monorepo context to run `./gradlew :services:academic-service:academic-app:bootJar`. The Docker build context must be the monorepo root, not the service subdirectory.

```dockerfile
# services/academic-service/Dockerfile
# Build context: monorepo root (rutcampustrack/)

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
# Copy Gradle wrapper and build files first (layer cache for dependencies)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY proto/ proto/
COPY services/academic-service/ services/academic-service/
RUN chmod +x gradlew && \
    ./gradlew :services:academic-service:academic-app:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=builder /workspace/services/academic-service/academic-app/build/libs/*.jar app.jar
EXPOSE 9091
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Note for api-contract modules:** The `*-api-contract` modules are pure Java libraries — they are not separately Dockerized. They are compiled as part of the `*-app` build via `implementation(project(...))` in Gradle.

### Pattern 2: Edge Nginx as SSL Terminator + Path Router

**What:** A single nginx container (`nginx-proxy`) faces the internet on ports 80 and 443. It terminates SSL and routes traffic by URL path to internal containers on the private Docker network.

**When to use:** Single-VPS deployment. All frontends and the API gateway live on the same host.

**Trade-offs:** Consolidates SSL in one place. If nginx-proxy restarts, all traffic is interrupted temporarily. Acceptable for single-VPS portfolio project.

**Path routing vs subdomain routing:** Path-based (`/app`, `/panel`, `/api`) is simpler for a single VPS without DNS wildcard setup. Can be migrated to subdomains later by changing nginx config only.

```nginx
# docker/nginx-proxy/nginx.conf

upstream api_gateway { server api-gateway:8080; }
upstream pwa         { server pwa-nginx:80; }
upstream web_panel   { server web-panel-nginx:80; }
upstream mini_app    { server mini-app-nginx:80; }
upstream landing     { server landing-nginx:80; }

server {
    listen 80;
    server_name rut.example.com;
    # Certbot ACME challenge passthrough
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }
    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl;
    server_name rut.example.com;
    ssl_certificate     /etc/letsencrypt/live/rut.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/rut.example.com/privkey.pem;

    location /api/     { proxy_pass http://api_gateway; }
    location /docs     { proxy_pass http://api_gateway; }
    location /app/     { proxy_pass http://pwa; }
    location /panel/   { proxy_pass http://web_panel; }
    location /miniapp/ { proxy_pass http://mini_app; }
    location /         { proxy_pass http://landing; }
}
```

### Pattern 3: Swagger Aggregation at API Gateway

**What:** The API Gateway (Spring Cloud Gateway = WebFlux) hosts a unified Swagger UI that pulls `/api-docs` from each downstream service. Clients visit one URL (`/swagger-ui.html` on the gateway) and see all service APIs.

**When to use:** v8.0 documentation requirement. All 4 Java services already have `springdoc` configured with `/api-docs` path.

**Trade-offs:** Gateway requires `springdoc-openapi-starter-webflux-ui` (not `webmvc-ui` — Gateway is reactive). Services need to expose `/api-docs` through the gateway (add routes or allow internal access).

**Implementation steps:**
1. Add `springdoc-openapi-starter-webflux-ui:2.7.0` to `api-gateway/build.gradle.kts`
2. Configure `springdoc.swagger-ui.urls` in gateway `application.yml` pointing to each service's `/api/*/api-docs` path (routed through gateway)
3. Services: ensure their `/api-docs` endpoints are accessible through the gateway (add routes or bypass JWT filter for `/api-docs`)

```yaml
# api-gateway/src/main/resources/application.yml additions
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    urls:
      - name: Auth Service
        url: /api/auth/api-docs
      - name: Academic Service
        url: /api/academic/api-docs
      - name: Schedule Service
        url: /api/schedule/api-docs
      - name: Attendance Service
        url: /api/attendance/api-docs
      - name: Notification Service
        url: /api/ws/api-docs
  api-docs:
    enabled: false  # Gateway itself has no REST endpoints to document
```

### Pattern 4: GitHub Actions CI — Path-Filtered Parallel Jobs

**What:** Single `ci.yml` workflow with separate jobs for Java, Python, and each frontend. `paths` filters ensure the Python job only runs if `notification-bot/**` changes.

**When to use:** All PR and push-to-main events.

**Trade-offs:** Parallel jobs use multiple runners simultaneously (faster, uses more Actions minutes). `paths` filter avoids running the full Java build when only landing page HTML changed.

```yaml
# .github/workflows/ci.yml (structure)
jobs:
  java:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - name: Build and test
        run: ./gradlew build --no-daemon

  python:
    runs-on: ubuntu-latest
    if: contains(github.event.commits[0].modified, 'services/notification-bot')
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.12' }
      - run: pip install -r services/notification-bot/requirements.txt
      - run: pytest services/notification-bot/

  pwa:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontends/pwa
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22' }
      - run: npm ci && npm test

  mini-app:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontends/mini-app
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22' }
      - run: npm ci && npm test

  web-panel:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontends/web-panel
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22' }
      - run: npm ci && npm test
```

### Pattern 5: SSH Deploy via docker compose pull + up

**What:** After CI passes on `main`, a deploy job SSH-es to the VPS and runs `docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d`. No image registry needed for this scale — builds happen on VPS using `docker compose build`.

**When to use:** Single VPS, solo developer, simple pipeline.

**Trade-offs:** Build on VPS means deploy is slower (compiles Java on the server). Alternative (build images in CI, push to GHCR/DockerHub, pull on VPS) is faster but requires image registry setup. For portfolio project with a single small VPS, build-on-server is simpler to start.

**Simpler alternative (recommended for v8.0):** SSH to VPS, `git pull`, then `docker compose build && docker compose up -d`. No registry. Full source on VPS.

```yaml
# .github/workflows/deploy.yml (structure)
jobs:
  deploy:
    needs: [java, python, pwa, mini-app, web-panel]
    runs-on: ubuntu-latest
    steps:
      - name: Deploy via SSH
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          script: |
            cd /opt/rutcampustrack
            git pull origin main
            docker compose -f docker-compose.prod.yml build --no-cache
            docker compose -f docker-compose.prod.yml up -d
            docker image prune -f
```

### Pattern 6: Spring Boot Actuator — Health-Only Exposure

**What:** Expose only `/actuator/health` (not metrics, env, beans). Docker `HEALTHCHECK` hits this endpoint. Notification-web already has this pattern — apply consistently to all services.

**When to use:** All Spring Boot services. No Prometheus/Grafana required for v8.0 scope.

**Trade-offs:** Simple. No metrics scraping infrastructure. Enough for docker-compose healthchecks and operational monitoring via `docker ps`.

```yaml
# Standard addition to all service application.yml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

```dockerfile
# Standard addition to all Java service Dockerfiles
HEALTHCHECK --interval=30s --timeout=5s --retries=5 \
  CMD wget -qO- http://localhost:${PORT}/actuator/health || exit 1
```

---

## Data Flow

### CI/CD Pipeline Flow

```
Developer pushes to main
    ↓
GitHub Actions triggers ci.yml
    ↓ (parallel)
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  Java build  │  │ Python test  │  │  TS/vitest   │
│  ./gradlew   │  │  pytest      │  │  npm test    │
│  build       │  │              │  │  (3 jobs)    │
└──────┬───────┘  └──────┬───────┘  └──────┬───────┘
       └──────────────────┴──────────────────┘
                          ↓ all pass
                   deploy.yml triggers
                          ↓
              SSH into VPS → git pull → docker compose build → up -d
                          ↓
              Docker builds each service image on VPS
                          ↓
              New containers replace old ones (rolling, per service)
```

### Production Request Flow

```
Browser/client request (HTTPS)
    ↓
nginx-proxy :443 (SSL termination)
    ↓ path-based routing
/api/** → api-gateway:8080
    ↓ JWT filter → strip /api prefix
auth-service|academic-service|schedule-service|attendance-service|notification-web
    ↓ response
← api-gateway ← nginx-proxy ← client

/app/** → pwa-nginx:80 (React PWA static)
/panel/** → web-panel-nginx:80 (Angular static)
/miniapp/** → mini-app-nginx:80 (Mini App static)
/ → landing-nginx:80 (Landing static)
```

### Swagger Aggregation Flow

```
Developer opens /swagger-ui.html (via nginx-proxy → api-gateway)
    ↓
springdoc webflux-ui renders in browser
    ↓ (for each service)
Browser fetches /api/academic/api-docs → gateway → academic-service /api-docs
Browser fetches /api/auth/api-docs → gateway → auth-service /api-docs
Browser fetches /api/schedule/api-docs → gateway → schedule-service /api-docs
Browser fetches /api/attendance/api-docs → gateway → attendance-service /api-docs
    ↓
Unified Swagger UI with service selector dropdown
```

### SSL Certificate Renewal Flow

```
Certbot container (runs every 12h)
    ↓ checks certificate expiry
    ↓ if < 30 days remaining
HTTP-01 challenge: GET http://rut.example.com/.well-known/acme-challenge/<token>
    ↓ nginx-proxy serves /.well-known/ from shared certbot volume
Let's Encrypt CA validates
    ↓
New cert written to /etc/letsencrypt/live/ (shared volume with nginx-proxy)
nginx-proxy reloads: docker exec nginx nginx -s reload
```

---

## New vs Modified Components Summary

| Component | Status | What Changes |
|-----------|--------|-------------|
| `.github/workflows/ci.yml` | NEW | Parallel build/test jobs for Java, Python, 3 frontends |
| `.github/workflows/deploy.yml` | NEW | SSH to VPS, git pull, docker compose up |
| `docker-compose.prod.yml` | NEW | No dev ports, prod env vars from secrets, nginx-proxy + certbot added |
| `docker/nginx-proxy/nginx.conf` | NEW | Edge SSL termination, path routing to all backends/frontends |
| `services/api-gateway/Dockerfile` | NEW | Multi-stage: JDK builder + JRE runtime |
| `services/auth-service/Dockerfile` | NEW | Multi-stage: JDK builder + JRE runtime |
| `services/academic-service/Dockerfile` | NEW | Multi-stage: built from academic-app subproject |
| `services/schedule-service/Dockerfile` | NEW | Multi-stage: built from schedule-app subproject |
| `services/attendance-service/Dockerfile` | NEW | Multi-stage: built from attendance-app subproject |
| `services/notification-service/notification-app/Dockerfile` | MODIFIED | Upgrade from single-stage to multi-stage |
| `services/notification-bot/Dockerfile` | UNCHANGED | Already correct (Python slim + requirements install) |
| `services/api-gateway/build.gradle.kts` | MODIFIED | Add springdoc-openapi-starter-webflux-ui |
| `services/api-gateway/src/main/resources/application.yml` | MODIFIED | Add springdoc.swagger-ui.urls for aggregation; Actuator health |
| All `*-app/src/main/resources/application.yml` | MODIFIED | Standardize Actuator management config (health only) |
| `services/api-gateway/src/main/java/.../JwtAuthenticationFilter.java` | MODIFIED | Add /api-docs paths to PUBLIC_PATHS (for Swagger access) |
| `README.md` | MODIFIED | Full project documentation |

---

## Build Order

The following order minimizes blocking dependencies:

1. **Actuator standardization** — Add `management` config to all 4 service application.yml files that lack it. No code changes, pure config. Can be done in one commit across all services. Unblocks docker-compose healthcheck improvements.

2. **Multi-stage Dockerfiles** — Write Dockerfiles for the 5 Java services that lack them (api-gateway, auth, academic, schedule, attendance). Upgrade notification-web's existing Dockerfile. All follow the same pattern. Build context is monorepo root for all of them. Can be done in parallel.

3. **docker-compose.prod.yml** — Only after Dockerfiles exist. References the Dockerfiles' build contexts. Removes dev ports (:15672 for RabbitMQ management). Adds nginx-proxy and certbot services. All env vars come from `.env.prod` (gitignored) or GitHub Secrets.

4. **Edge nginx + SSL** — Write `docker/nginx-proxy/nginx.conf`. Requires knowing the domain name. Certbot container added to docker-compose.prod.yml. SSL setup is done manually once on VPS (first certbot run), then auto-renewed.

5. **Swagger aggregation at Gateway** — Add webflux-ui dependency + springdoc config to api-gateway. Add /api-docs routes or whitelist in JWT filter. Test that all 4 service specs are accessible through gateway.

6. **GitHub Actions CI** — Write ci.yml. Requires no VPS access — pure GitHub configuration. Test by pushing a branch and watching Actions tab.

7. **GitHub Actions Deploy** — Write deploy.yml. Requires VPS SSH key added to GitHub Secrets. Trigger: on CI success on main branch.

8. **README** — Final step. Describes architecture, setup, API summary, deploy guide. Written after everything else works.

**Critical dependency chain:** Dockerfiles → docker-compose.prod.yml → VPS setup → GitHub Actions deploy (deploy job needs working containers). Swagger and Actuator are independent of deploy flow.

---

## Integration Points

### New Component Interactions

| Boundary | Communication | Notes |
|----------|---------------|-------|
| nginx-proxy ↔ api-gateway | HTTP proxy (internal Docker network) | nginx passes `X-Forwarded-For`, `X-Forwarded-Proto` headers |
| nginx-proxy ↔ frontend nginx containers | HTTP proxy (internal Docker network) | pwa-nginx, web-panel-nginx, mini-app-nginx, landing-nginx remain unchanged |
| certbot ↔ nginx-proxy | Shared volume `/etc/letsencrypt` | nginx reads certs; certbot writes on renewal |
| certbot ↔ nginx-proxy webroot | Shared volume `/var/www/certbot` for ACME challenge | nginx serves `/.well-known/acme-challenge/` from this path |
| api-gateway ↔ service /api-docs | Internal HTTP fetch (gateway → service:port/api-docs) | Must add /api-docs to gateway routes AND to JWT filter PUBLIC_PATHS |
| GitHub Actions ↔ VPS | SSH (appleboy/ssh-action or similar) | SSH private key stored as GitHub Secret |
| Docker healthchecks ↔ Actuator /health | HTTP GET inside container | All Java services expose /actuator/health on their server port |

### Existing Interactions Unchanged

| Boundary | Communication | Status |
|----------|---------------|--------|
| api-gateway ↔ auth-service | HTTP proxy via Spring Cloud Gateway | Unchanged |
| services ↔ PostgreSQL/MongoDB/Redis | JDBC/MongoClient/RedisClient on private_net | Unchanged |
| services ↔ RabbitMQ | AMQP on private_net | Unchanged |
| services ↔ services (gRPC) | gRPC on private_net (ports 19090-19093) | Unchanged |
| notification-web ↔ clients | STOMP WebSocket via api-gateway | Unchanged |
| notification-bot ↔ RabbitMQ/Redis/gRPC | All internal network | Unchanged |

---

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Current (< 5k users) | Single VPS, single nginx-proxy, all containers on one host — sufficient |
| 5k-20k users | Add CDN (Cloudflare) in front of nginx-proxy for static assets and TLS offloading |
| 20k+ users | Separate frontend serving to CDN/object storage; add load balancer in front of api-gateway replicas |

At RUT MIIT student scale (500-5000), the single-VPS docker-compose architecture handles all load. The bottleneck would be the PostgreSQL databases before any other component.

---

## Anti-Patterns

### Anti-Pattern 1: Exposing /actuator/* Publicly

**What people do:** Set `include: "*"` on management endpoints and expose them through the edge nginx.

**Why it's wrong:** Exposes env vars, bean definitions, and heap dumps to the internet.

**Do this instead:** Only expose `health`. Keep `/actuator/**` blocked at the nginx-proxy level. It's only needed internally by Docker healthchecks, which run inside the container network.

### Anti-Pattern 2: Build Context = Service Subdirectory for Gradle Monorepo

**What people do:** Set Docker build context to `./services/academic-service/academic-app/` in docker-compose.

**Why it's wrong:** The `./gradlew` wrapper, `settings.gradle.kts`, `proto/` dir, and other api-contract modules are in the monorepo root. The build fails because `COPY gradlew .` finds nothing.

**Do this instead:** Set build context to the monorepo root (`.`) and use a context-specific `.dockerignore` to exclude large irrelevant directories (`node_modules`, `frontends/`, `build/`).

### Anti-Pattern 3: Single nginx.conf for All Frontends

**What people do:** Combine pwa, web-panel, mini-app, and landing into one nginx server block with path prefixes.

**Why it's wrong:** Each SPA needs `try_files $uri /index.html` only within its own path subtree. A single server block can't cleanly serve multiple SPAs with different base paths without router conflicts.

**Do this instead:** Keep the existing separate nginx containers per frontend. The edge nginx-proxy routes to each internal nginx by path prefix. The internal containers handle their own SPA routing rules.

### Anti-Pattern 4: Hard-coding Credentials in docker-compose.prod.yml

**What people do:** Copy dev credentials (`rct_dev_pass`) into the prod compose file and commit it.

**Why it's wrong:** Credentials in source control. Even if repo is private, this is a bad habit and a security risk.

**Do this instead:** All passwords, tokens, and keys use `${VAR:-}` syntax. On the VPS, create a `.env.prod` file (gitignored) with real values. In GitHub Actions deploy job, the `.env.prod` can be generated from GitHub Secrets before running compose up.

### Anti-Pattern 5: JWT Private Keys in Docker Image Layers

**What people do:** `COPY keys/ /app/keys/` in Dockerfile to embed RSA keys in the image.

**Why it's wrong:** Keys end up in Docker image layers, which can be extracted. Keys rotate differently from code.

**Do this instead:** Mount keys as a Docker volume (already done in existing docker-compose.yml with `jwt-keys` named volume). In prod, initialize the volume with keys separately from the compose file. Auth service reads from `${JWT_KEY_DIR}` env var.

---

## Sources

- [Integrate OpenAPI With Spring Cloud Gateway — Baeldung](https://www.baeldung.com/spring-cloud-gateway-integrate-openapi) (verified approach)
- [Springdoc-openapi Demos — official](https://springdoc.org/demos.html) (Spring Boot 3 + Gateway demo)
- [GitHub Actions VPS CI/CD 2025 — Webtrophy](https://www.webtrophy.dev/posts/github-actions-vps-deployment) (SSH deploy patterns)
- [Setup SSL with Docker, NGINX and Let's Encrypt](https://www.programonaut.com/setup-ssl-with-docker-nginx-and-lets-encrypt/) (certbot volume pattern)
- [Spring Boot Actuator — Production-ready Features](https://docs.spring.io/spring-boot/reference/actuator/index.html) (official)
- [Multi-Stage Dockerfiles for Spring Boot](https://medium.com/@office.yeon/dockerizing-with-multi-stage-builds-in-spring-boot-multi-module-project-1fd3aa886afc) (multi-module Gradle pattern)
- Existing codebase: `services/notification-service/notification-app/Dockerfile` (source of truth for current Java Dockerfile pattern)
- Existing codebase: `docker-compose.yml` (source of truth for current service topology, ports, networks, volumes)
- Existing codebase: `services/api-gateway/src/main/resources/application.yml` (source of truth for current gateway routing and CORS)
- Existing codebase: `services/api-gateway/build.gradle.kts` (confirms spring-boot-starter-actuator already present in gateway)

---
*Architecture research for: v8.0 CI/CD, Deployment & Documentation*
*Researched: 2026-04-07*
