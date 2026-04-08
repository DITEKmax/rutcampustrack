# Phase 43: docker-compose.prod.yml - Research

**Researched:** 2026-04-07
**Domain:** Docker Compose production configuration — Spring Boot profiles, secrets management, healthchecks
**Confidence:** HIGH

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DOCK-05 | docker-compose.prod.yml runs all services with production Spring profile | Set `SPRING_PROFILES_ACTIVE=prod` env var on all Java services; `application-prod.yml` exists in all 4 services + auth-service already has one |
| DOCK-06 | docker-compose.prod.yml exposes only ports 80/443 (no DB host ports) | Remove `ports:` from postgres, mongo, redis, rabbitmq; also remove RabbitMQ management UI port 15672; keep only port 80 for API gateway (443 is Phase 44) |
| DOCK-07 | Production secrets managed via .env.prod (gitignored) | .env.prod must be added to .gitignore; all secret env vars interpolated via `${VAR}` syntax |
| MON-03 | docker-compose.prod.yml uses Actuator healthchecks for service containers | All Java Spring Boot services get `healthcheck:` using `wget -qO- http://localhost:{port}/actuator/health`; depends_on with `condition: service_healthy` |
</phase_requirements>

## Summary

Phase 43 creates `docker-compose.prod.yml` at the repo root — a production-ready Compose file that starts the complete system with the Spring `prod` profile, no exposed infrastructure ports, Actuator-based healthchecks on all Java backends, and all secrets coming from `.env.prod`.

The base `docker-compose.yml` is a solid reference: all infrastructure services (postgres, mongo, redis, rabbitmq) already use `expose:` (not `ports:`) except RabbitMQ which exposes management UI on port 15672 — that must be removed in prod. The dev compose uses volume mounts for frontends (`./frontends/pwa/dist:/usr/share/nginx/html`); the prod compose must switch to pre-built Docker images using the Dockerfiles from Phase 42. All services need `image:` tags referencing GHCR images for the eventual CI/CD pipeline.

The key structural difference from dev to prod: dev mounts host-built artifacts into generic nginx images; prod builds self-contained images from Dockerfiles. All 6 Java services (api-gateway, auth, academic, schedule, attendance, notification-web) need `SPRING_PROFILES_ACTIVE=prod` and Actuator healthchecks. The notification-bot (Python) already has an HTTP health endpoint at `curl http://localhost:8081/health` and gets its config from env vars via pydantic-settings. Frontend containers are static nginx — no Spring profile, no Actuator.

**Primary recommendation:** Create docker-compose.prod.yml that (1) uses `build:` stanzas referencing existing Dockerfiles, (2) sets `SPRING_PROFILES_ACTIVE=prod` on all Java services, (3) removes all infrastructure `ports:` mappings, (4) adds Actuator healthchecks to all Java services, (5) reads secrets from `.env.prod`, and (6) exposes only port 80 on the api-gateway.

## Project Constraints (from CLAUDE.md)

- Java 21, Spring Boot 3.4, Gradle monorepo
- 5 Java services + api-gateway + notification-web (Java) + notification-bot (Python)
- `ddl-auto: validate` — Flyway runs migrations on startup; DB must be healthy before app starts
- All services already have `management:` blocks configured (MON-01/MON-02 complete from Phase 41)
- `application-prod.yml` exists for: auth-service, academic-app, schedule-app, attendance-app [VERIFIED: codebase read]
- `application-prod.yml` does NOT exist for: api-gateway, notification-web [VERIFIED: codebase — only `application.yml` found]
- notification-bot config comes from env vars via pydantic-settings `BaseSettings` with `env_file: ".env"` [VERIFIED: services/notification-bot/bot/config.py]

## Current State Audit

### What Exists Today [VERIFIED: codebase read]

| Component | Status |
|-----------|--------|
| `docker-compose.yml` | Exists — dev file with volume mounts for frontends, port 15672 for RabbitMQ management |
| `docker-compose.prod.yml` | Does NOT exist — must be created |
| `.env.prod` | Does NOT exist — must be created as a template only |
| `.env` in .gitignore | Partial — `.env` and `*.env.local` are gitignored; `.env.prod` is NOT yet gitignored |
| `application-prod.yml` (auth-service) | EXISTS — only contains actuator management block |
| `application-prod.yml` (academic, schedule, attendance) | EXISTS — only contains actuator management block |
| `application-prod.yml` (api-gateway) | MISSING — no prod profile exists |
| `application-prod.yml` (notification-web) | MISSING — no prod profile exists |
| Dockerfiles (all services) | Exist from Phase 42 |
| Frontend Dockerfiles | Exist from Phase 42 |
| Actuator healthchecks in dev compose | notification-web and notification-bot only |

### Services Inventory [VERIFIED: docker-compose.yml read + Dockerfiles]

| Service | Port | Dockerfile Location | Build Context | Spring Profile Needed |
|---------|------|--------------------|--------------|-----------------------|
| api-gateway | 8080 | services/api-gateway/Dockerfile | repo root | prod |
| auth-service | 9090 | services/auth-service/Dockerfile | repo root | prod |
| academic-app | 9091 | services/academic-service/academic-app/Dockerfile | repo root | prod |
| schedule-app | 9092 | services/schedule-service/schedule-app/Dockerfile | repo root | prod |
| attendance-app | 9093 | services/attendance-service/attendance-app/Dockerfile | repo root | prod |
| notification-web | 9094 | services/notification-service/notification-app/Dockerfile | repo root | prod |
| notification-bot | 8081 (health) | services/notification-bot/Dockerfile | service dir | N/A (Python) |
| pwa-nginx | 80 | frontends/pwa/Dockerfile | frontend dir | N/A (static) |
| mini-app-nginx | 80 | frontends/mini-app/Dockerfile | frontend dir | N/A (static) |
| web-panel-nginx | 80 | frontends/web-panel/Dockerfile | frontend dir | N/A (static) |
| landing-nginx | 80 | frontends/landing/Dockerfile | frontend dir | N/A (static) |
| postgres-academic | 5432 | image: postgres:16 | N/A | N/A |
| postgres-schedule | 5432 | image: postgres:16 | N/A | N/A |
| mongo-attendance | 27017 | image: mongo:7 | N/A | N/A |
| redis | 6379 | image: redis:7-alpine | N/A | N/A |
| rabbitmq | 5672 | image: rabbitmq:3.13-management-alpine | N/A | N/A |

### Infrastructure Port Status in dev compose [VERIFIED: docker-compose.yml read]

| Service | Dev config | Prod config needed |
|---------|-----------|-------------------|
| postgres-academic | `expose: 5432` (not host-exposed) | Same — no change |
| postgres-schedule | `expose: 5432` (not host-exposed) | Same — no change |
| mongo-attendance | `expose: 27017` (not host-exposed) | Same — no change |
| redis | `expose: 6379` (not host-exposed) | Same — no change |
| rabbitmq | `expose: 5672` + `ports: 15672:15672` | Remove the `ports:` mapping entirely |

### Secrets / Env Vars Inventory [VERIFIED: application.yml files + config.py + dev compose]

| Secret | Used By | Env Var Name |
|--------|---------|--------------|
| PostgreSQL academic password | academic-app, auth-service | `POSTGRES_ACADEMIC_PASSWORD` |
| PostgreSQL schedule password | schedule-app | `POSTGRES_SCHEDULE_PASSWORD` |
| RabbitMQ username | academic, schedule, attendance, notification-web, notification-bot | `RABBITMQ_USER` |
| RabbitMQ password | same | `RABBITMQ_PASSWORD` |
| JWT RSA private key dir | auth-service | `JWT_KEY_DIR` |
| JWT RSA public key path | notification-web | `JWT_PUBLIC_KEY_PATH` |
| Telegram Bot Token | auth-service (TMA), notification-bot | `TMA_BOT_TOKEN`, `BOT_TOKEN` |
| VAPID public key | notification-web | `VAPID_PUBLIC_KEY` |
| VAPID private key | notification-web | `VAPID_PRIVATE_KEY` |
| VAPID subject | notification-web | `VAPID_SUBJECT` |
| Mini App URL | notification-bot | `MINI_APP_URL` |

**Note:** MongoDB in dev compose has no auth (no MONGO_INITDB_ROOT_USERNAME). For Phase 43 this can remain unauthenticated (MongoDB password management is out of scope per REQUIREMENTS.md — Phase 43 only targets DOCK-05/06/07/MON-03). [ASSUMED: mongo auth is not required for this phase]

### JWT Key Volume [VERIFIED: docker-compose.yml]

The dev compose uses a named volume `jwt-keys` shared between auth-service (writes RSA keys) and notification-web (reads public key). Auth-service uses `JWT_KEY_DIR: /keys` and notification-web uses `JWT_PUBLIC_KEY_PATH: /keys/public.key`. This volume mount must be preserved in prod.

## Standard Stack

### Core
| Component | Version | Purpose | Notes |
|-----------|---------|---------|-------|
| Docker Compose file format | 3.9 | Orchestration | Same as dev compose — do not change |
| `expose:` vs `ports:` | — | Port visibility | `expose:` = container-to-container only; `ports:` = host-exposed. Infra should only use `expose:` |
| `.env.prod` | — | Secret injection | Standard Compose env_file or host-level env var file |
| `condition: service_healthy` | — | Startup ordering | Requires `healthcheck:` on the dependency service |
| `wget` healthcheck | — | Java service healthcheck | `eclipse-temurin:21-jre-alpine` has `wget` via Alpine busybox; no curl install needed |

**Installation:** No new dependencies — this phase writes a Compose YAML file only.

## Architecture Patterns

### Recommended Structure

```yaml
# docker-compose.prod.yml

version: "3.9"

services:
  # Infrastructure (same as dev, minus the rabbitmq ports:)
  postgres-academic:  ...  # healthcheck same as dev
  postgres-schedule:  ...  # healthcheck same as dev
  mongo-attendance:   ...  # healthcheck same as dev
  redis:              ...  # healthcheck same as dev
  rabbitmq:           ...  # healthcheck same as dev — NO ports: section

  # Java services — add SPRING_PROFILES_ACTIVE=prod + Actuator healthchecks
  api-gateway:        ...
  auth-service:       ...
  academic-service:   ...
  schedule-service:   ...
  attendance-service: ...
  notification-web:   ...

  # Python bot — existing healthcheck pattern unchanged
  notification-bot:   ...

  # Frontend nginx containers — built from Phase 42 Dockerfiles
  pwa-nginx:          ...
  mini-app-nginx:     ...
  web-panel-nginx:    ...
  landing-nginx:      ...

networks:
  private_net:
    driver: bridge

volumes:
  pg-academic-data:
  pg-schedule-data:
  mongo-data:
  redis-data:
  rabbitmq-data:
  jwt-keys:
```

### Pattern 1: Java Service with Spring prod Profile + Actuator Healthcheck

```yaml
# Source: derived from dev compose + Phase 41 actuator research + existing application.yml
auth-service:
  build:
    context: .
    dockerfile: services/auth-service/Dockerfile
  container_name: rct-auth-service
  environment:
    SPRING_PROFILES_ACTIVE: prod
    POSTGRES_ACADEMIC_PASSWORD: ${POSTGRES_ACADEMIC_PASSWORD}
    JWT_KEY_DIR: /keys
    TMA_BOT_TOKEN: ${TMA_BOT_TOKEN}
  volumes:
    - jwt-keys:/keys
  expose:
    - "9090"
  networks:
    - private_net
  depends_on:
    postgres-academic:
      condition: service_healthy
    redis:
      condition: service_healthy
  restart: unless-stopped
  healthcheck:
    test: ["CMD-SHELL", "wget -qO- http://localhost:9090/actuator/health || exit 1"]
    interval: 15s
    timeout: 5s
    retries: 5
    start_period: 60s
```

**Key notes:**
- `SPRING_PROFILES_ACTIVE: prod` activates `application-prod.yml` — MON-02 is satisfied [VERIFIED: application-prod.yml exists for all 4 target services from Phase 41]
- `start_period: 60s` is critical — Spring Boot with Flyway migrations + DB connections takes 30-60s on first start
- `wget -qO- URL || exit 1` is the correct pattern for Alpine wget (no `--spider` needed for JSON endpoints)
- No `ports:` — all Java services are accessed only through the api-gateway

### Pattern 2: API Gateway (sole public-facing service)

```yaml
api-gateway:
  build:
    context: .
    dockerfile: services/api-gateway/Dockerfile
  container_name: rct-api-gateway
  environment:
    SPRING_PROFILES_ACTIVE: prod
    AUTH_SERVICE_URL: http://auth-service:9090
  ports:
    - "80:8080"    # Only service with host ports in prod
  networks:
    - private_net
  depends_on:
    auth-service:
      condition: service_healthy
    academic-service:
      condition: service_healthy
    schedule-service:
      condition: service_healthy
    attendance-service:
      condition: service_healthy
    notification-web:
      condition: service_healthy
  restart: unless-stopped
  healthcheck:
    test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
    interval: 15s
    timeout: 5s
    retries: 5
    start_period: 30s
```

**Note on api-gateway prod profile:** api-gateway has no `application-prod.yml` today. It has no datasource, no Flyway — the prod profile only needs the actuator block. Two options:
- Option A: Create `services/api-gateway/src/main/resources/application-prod.yml` with just the management block (same pattern as other services)
- Option B: The existing `application.yml` already restricts actuator to `health,info` — a prod file is optional

Since DOCK-05 requires `SPRING_PROFILES_ACTIVE=prod`, the gateway will load both `application.yml` and `application-prod.yml`. If `application-prod.yml` doesn't exist, Spring Boot logs a warning but continues. Option A (create the file) is cleaner. The planner must include this as a task.

### Pattern 3: Frontend nginx (no Spring profile, no Actuator)

```yaml
pwa-nginx:
  build:
    context: ./frontends/pwa
    dockerfile: Dockerfile
  container_name: rct-pwa-nginx
  expose:
    - "80"
  networks:
    - private_net
  restart: unless-stopped
```

Frontend containers have no health endpoint. They should NOT use `depends_on:` with `condition: service_healthy` against the gateway — they are independent static servers. In Phase 44, nginx reverse proxy will route to them. For Phase 43 they are just reachable within the private network.

**No `ports:` on frontends** — DOCK-06 requires no exposed ports except 80/443.

### Pattern 4: notification-bot (Python) — unchanged from dev

```yaml
notification-bot:
  build:
    context: ./services/notification-bot
    dockerfile: Dockerfile
  container_name: rct-notification-bot
  environment:
    BOT_TOKEN: ${BOT_TOKEN}
    RABBITMQ_URL: amqp://${RABBITMQ_USER}:${RABBITMQ_PASSWORD}@rabbitmq:5672/
    ACADEMIC_GRPC_HOST: academic-service
    ACADEMIC_GRPC_PORT: "19091"
    REDIS_HOST: redis
    REDIS_PORT: "6379"
    HEALTH_PORT: "8081"
    SCHEDULE_GRPC_HOST: schedule-service
    SCHEDULE_GRPC_PORT: "19092"
    AUTH_SERVICE_HOST: auth-service
    AUTH_SERVICE_PORT: "9090"
    API_GATEWAY_URL: http://api-gateway:8080
    MINI_APP_URL: ${MINI_APP_URL}
    TZ: Europe/Moscow
  networks:
    - private_net
  depends_on:
    redis:
      condition: service_healthy
    rabbitmq:
      condition: service_healthy
  restart: unless-stopped
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8081/health"]
    interval: 15s
    timeout: 5s
    retries: 5
    start_period: 15s
```

**Note:** notification-bot reads `BOT_TOKEN` from environment (via pydantic BaseSettings). It does NOT use SPRING_PROFILES_ACTIVE. The existing healthcheck uses curl (installed in the Dockerfile via apt-get).

### Pattern 5: .env.prod Template

```bash
# .env.prod — NEVER commit to git. Copy from .env.prod.example and fill in real values.

# PostgreSQL
POSTGRES_ACADEMIC_PASSWORD=CHANGE_ME
POSTGRES_SCHEDULE_PASSWORD=CHANGE_ME

# RabbitMQ
RABBITMQ_USER=rct_user
RABBITMQ_PASSWORD=CHANGE_ME

# Telegram
BOT_TOKEN=CHANGE_ME
TMA_BOT_TOKEN=CHANGE_ME

# VAPID (Web Push) — generate with: npx web-push generate-vapid-keys
VAPID_PUBLIC_KEY=CHANGE_ME
VAPID_PRIVATE_KEY=CHANGE_ME
VAPID_SUBJECT=mailto:noreply@rut.ru

# Frontend / App URLs
MINI_APP_URL=https://t.me/RutTrackBot/checkin
```

**Note on JWT RSA keys:** The dev compose uses a named Docker volume `jwt-keys`. Auth-service generates keys on first start into that volume. In prod, the same volume approach works — no env var needed for the key itself, only `JWT_KEY_DIR=/keys` pointing to the volume mount. This is already hardcoded in the docker-compose.yml and can be replicated in prod without a secret env var.

### Pattern 6: .gitignore addition

```
# Production secrets
.env.prod
```

Currently `.gitignore` has `.env` and `*.env.local` but NOT `.env.prod`. [VERIFIED: .gitignore read] This must be added.

### Anti-Patterns to Avoid

- **Using `ports:` on infrastructure services (postgres, mongo, redis, rabbitmq):** Dev compose already avoids this (uses `expose:` only) except RabbitMQ management port 15672 — remove in prod.
- **Hardcoding passwords in docker-compose.prod.yml:** All secrets must reference `${VAR}` from `.env.prod`.
- **Setting `depends_on:` without `condition: service_healthy`:** Plain `depends_on: [postgres]` only waits for container start, not DB readiness. Flyway migrations fail if postgres hasn't finished init.
- **Short `start_period` for Java services:** Spring Boot with Flyway needs 30-60s before the actuator returns UP. A `start_period: 30s` minimum is required; 60s is safer.
- **Omitting `restart: unless-stopped`:** Without restart policy, crashed services don't recover.
- **Using `ports:` on frontend containers:** Frontends will be behind nginx reverse proxy (Phase 44). In Phase 43, they should only be accessible within `private_net` via `expose:`.
- **Using `env_file: .env.prod`** in the compose service directly:** Standard practice is to load the env file at the compose CLI level with `--env-file .env.prod` or use the default `.env` file name. In Compose v3, `env_file:` in a service stanza injects vars into the container but does NOT make them available for `${VAR}` interpolation in the YAML itself. Use `--env-file .env.prod` at the CLI, or name it `.env` in prod. The recommended approach: document that the operator runs `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d`. [ASSUMED: this is standard Docker Compose v3.9 behavior]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Service startup ordering | Custom shell `wait-for-it.sh` scripts | `depends_on` + `condition: service_healthy` | Native Compose feature, handles retry logic automatically |
| Health endpoint | Custom HTTP controller for /health | `/actuator/health` (Phase 41 complete) | Already implemented, returns `{"status":"UP"}` |
| Secret injection | Baking secrets into the image | `.env.prod` + `${VAR}` interpolation | Standard pattern; never bake secrets into layers |
| MongoDB authentication | Custom mongod.conf | Skip for Phase 43 (out of scope) | Phase 43 scope is the Compose file, not DB security hardening |

## Common Pitfalls

### Pitfall 1: `start_period` Too Short — Actuator Healthcheck Fails During DB Init
**What goes wrong:** Container is immediately marked unhealthy because Flyway is still running migrations; dependent services (api-gateway) never start.
**Why it happens:** Default `start_period` is 0s. Spring Boot + Flyway + PostgreSQL cold start takes 15-45s.
**How to avoid:** Set `start_period: 60s` on academic, schedule (have Flyway), `start_period: 45s` on auth and attendance. Set `start_period: 30s` on api-gateway and notification-web.
**Warning signs:** Compose reports services as `unhealthy` within the first 30 seconds and gives up before the service is actually ready.

### Pitfall 2: notification-web Missing `SPRING_PROFILES_ACTIVE=prod`
**What goes wrong:** notification-web starts with the default profile, which has `logging.level: DEBUG` and potentially no production actuator config.
**Why it happens:** notification-web currently has no `application-prod.yml` — so setting `SPRING_PROFILES_ACTIVE=prod` will cause Spring Boot to log a warning "No active profile set, falling back to 1 default profile" but won't fail. However, this leaves logging at DEBUG in production.
**How to avoid:** Create `services/notification-service/notification-app/src/main/resources/application-prod.yml` with `logging.level.root: INFO`. This is a required task in Phase 43.
**Warning signs:** Container log is flooded with DEBUG messages from RabbitMQ consumers and WebSocket traffic.

### Pitfall 3: api-gateway Has No `application-prod.yml`
**What goes wrong:** Same as Pitfall 2 — Spring Boot logs a warning but continues. No functional breakage since the gateway has no datasource or Flyway.
**Why it happens:** Phase 41 only added prod profiles to the 4 services with databases. api-gateway was excluded.
**How to avoid:** Create `services/api-gateway/src/main/resources/application-prod.yml` with at minimum `logging.level.root: INFO`. This is a required task in Phase 43.
**Warning signs:** Gateway log is flooded with Spring Cloud Gateway DEBUG routing output in production.

### Pitfall 4: `.env.prod` Accidentally Committed
**What goes wrong:** Real bot tokens, DB passwords, VAPID private keys pushed to GitHub.
**Why it happens:** `.env.prod` is not yet in `.gitignore` (currently `.env` and `*.env.local` are covered but not `.env.prod`). [VERIFIED: .gitignore read]
**How to avoid:** Add `.env.prod` to `.gitignore` as the FIRST task in the plan, before any other files are created.
**Warning signs:** `git status` shows `.env.prod` as untracked without a hint to add it.

### Pitfall 5: RabbitMQ Management Port 15672 Still Exposed
**What goes wrong:** RabbitMQ management console is accessible from the host in prod — an unauthenticated admin panel exposure risk.
**Why it happens:** The dev compose has `ports: - "15672:15672"` for convenience. This must be removed in the prod file (or in the base file with a comment).
**How to avoid:** The prod compose explicitly omits the `ports:` block under rabbitmq. Only `expose: - "5672"` remains.

### Pitfall 6: Frontend Containers Miss Volume Mounts — Use Dockerfiles Instead
**What goes wrong:** If prod compose replicates the dev pattern (`volumes: ./frontends/pwa/dist:/usr/share/nginx/html`) instead of using built images, the production server would need the dist/ artifacts pre-built on the host — defeating the purpose of containerization.
**Why it happens:** Copy-paste from dev compose without updating the build stanza.
**How to avoid:** The prod compose uses `build: context: ./frontends/pwa` (with the Dockerfile from Phase 42) instead of volume mounts.

### Pitfall 7: notification-web Depends on `redis` But redis Is Not in its Dev Compose depends_on Correctly
**What goes wrong:** Looking at dev compose, notification-web lists `redis` in its `depends_on` even though notification-web doesn't use Redis directly — it uses MongoDB. This is not wrong (extra depends_on is harmless) but the prod compose should mirror the actual dependencies accurately.
**How to avoid:** notification-web actually uses RabbitMQ and MongoDB. The prod depends_on should be: `rabbitmq: condition: service_healthy` and `mongo-attendance: condition: service_healthy`. The redis depends_on in dev is likely a copy-paste artifact and should be cleaned up in prod.

## Code Examples

### Complete notification-web service entry (prod)
```yaml
# Source: derived from dev compose + Phase 41 actuator research + application.yml
notification-web:
  build:
    context: .
    dockerfile: services/notification-service/notification-app/Dockerfile
  container_name: rct-notification-web
  environment:
    SPRING_PROFILES_ACTIVE: prod
    SPRING_RABBITMQ_HOST: rabbitmq
    SPRING_RABBITMQ_PORT: "5672"
    SPRING_RABBITMQ_USERNAME: ${RABBITMQ_USER}
    SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
    JWT_PUBLIC_KEY_PATH: /keys/public.key
    VAPID_PUBLIC_KEY: ${VAPID_PUBLIC_KEY}
    VAPID_PRIVATE_KEY: ${VAPID_PRIVATE_KEY}
    VAPID_SUBJECT: ${VAPID_SUBJECT}
    SPRING_DATA_MONGODB_URI: mongodb://mongo-attendance:27017/attendance_db
  volumes:
    - jwt-keys:/keys:ro
  expose:
    - "9094"
  networks:
    - private_net
  depends_on:
    rabbitmq:
      condition: service_healthy
    mongo-attendance:
      condition: service_healthy
  restart: unless-stopped
  healthcheck:
    test: ["CMD-SHELL", "wget -qO- http://localhost:9094/actuator/health || exit 1"]
    interval: 15s
    timeout: 5s
    retries: 5
    start_period: 30s
```

### academic-service (representative Java service with Flyway)
```yaml
# Source: derived from academic application.yml + dev compose pattern
academic-service:
  build:
    context: .
    dockerfile: services/academic-service/academic-app/Dockerfile
  container_name: rct-academic-service
  environment:
    SPRING_PROFILES_ACTIVE: prod
    POSTGRES_ACADEMIC_PASSWORD: ${POSTGRES_ACADEMIC_PASSWORD}
    RABBITMQ_USER: ${RABBITMQ_USER}
    RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
  expose:
    - "9091"
  networks:
    - private_net
  depends_on:
    postgres-academic:
      condition: service_healthy
    redis:
      condition: service_healthy
    rabbitmq:
      condition: service_healthy
  restart: unless-stopped
  healthcheck:
    test: ["CMD-SHELL", "wget -qO- http://localhost:9091/actuator/health || exit 1"]
    interval: 15s
    timeout: 5s
    retries: 5
    start_period: 60s
```

### attendance-service (MongoDB + Redis + no Flyway — shorter start_period)
```yaml
attendance-service:
  build:
    context: .
    dockerfile: services/attendance-service/attendance-app/Dockerfile
  container_name: rct-attendance-service
  environment:
    SPRING_PROFILES_ACTIVE: prod
    RABBITMQ_USER: ${RABBITMQ_USER}
    RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
  expose:
    - "9093"
  networks:
    - private_net
  depends_on:
    mongo-attendance:
      condition: service_healthy
    redis:
      condition: service_healthy
    rabbitmq:
      condition: service_healthy
  restart: unless-stopped
  healthcheck:
    test: ["CMD-SHELL", "wget -qO- http://localhost:9093/actuator/health || exit 1"]
    interval: 15s
    timeout: 5s
    retries: 5
    start_period: 45s
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `depends_on: [service]` | `depends_on: service: condition: service_healthy` | Compose v3.4+ | Compose waits for healthcheck PASS, not just container start |
| Secrets in compose file | `${VAR}` from .env file | Standard since Compose v2 | Clean separation of config and secrets |
| Single-stage Dockerfiles (copy pre-built JAR) | Multi-stage with Gradle inside Docker | Phase 42 | Self-contained builds, no host build artifacts needed |

**Deprecated/outdated:**
- `version: "3.x"` key in compose files: technically deprecated in favor of `version` being optional in current Compose v2 CLI, but still valid and projects commonly keep it. Keeping `version: "3.9"` is fine for compatibility. [ASSUMED: no breaking change for Docker Compose v2.x CLI]

## Missing application-prod.yml Files (Required Tasks for Phase 43)

Phase 43 sets `SPRING_PROFILES_ACTIVE=prod` on all Java services. Two services lack `application-prod.yml`:

| Service | Missing File | Minimum Content Needed |
|---------|-------------|------------------------|
| api-gateway | `services/api-gateway/src/main/resources/application-prod.yml` | `logging.level.root: INFO` (Spring Cloud Gateway debug logs are very noisy) |
| notification-web | `services/notification-service/notification-app/src/main/resources/application-prod.yml` | `logging.level.root: INFO` + management block (actuator already works in default profile) |

These files must be created in Phase 43 or the `SPRING_PROFILES_ACTIVE=prod` env var serves no useful purpose for those services.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Build images, run compose | Verified in Phase 42 | 28.5.2 | — |
| Docker Compose | Run docker-compose.prod.yml | Included with Docker Desktop | v2.x | — |

Step 2.6: All external dependencies confirmed available from Phase 42 research. This phase writes configuration files only.

## Validation Architecture

`workflow.nyquist_validation` is absent from config.json — treat as enabled.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | docker compose smoke test (manual CLI, no framework files) |
| Config file | none |
| Quick run command | `docker compose -f docker-compose.prod.yml --env-file .env.prod config` (validates YAML + interpolation) |
| Full suite command | `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d && docker compose -f docker-compose.prod.yml ps` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DOCK-05 | All Java services start with SPRING_PROFILES_ACTIVE=prod | smoke | `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d` then `docker compose -f docker-compose.prod.yml exec auth-service env \| grep SPRING_PROFILES` | No file needed |
| DOCK-06 | No DB ports exposed to host | smoke | `docker compose -f docker-compose.prod.yml --env-file .env.prod config \| grep -A5 "ports:"` — should only show api-gateway port 80 | No file needed |
| DOCK-07 | .env.prod is gitignored and contains all required secrets | smoke | `git check-ignore -v .env.prod` — should print `.gitignore:.env.prod` | No file needed |
| MON-03 | Actuator healthchecks configured on all backend containers | smoke | `docker compose -f docker-compose.prod.yml --env-file .env.prod config \| grep -A6 "healthcheck:"` — all 7 backend services should show wget/curl healthcheck | No file needed |

### Sampling Rate
- **Per task commit:** `docker compose -f docker-compose.prod.yml --env-file .env.prod config` (config validation, no containers started)
- **Per wave merge:** Full `up -d` + `ps` showing all services healthy
- **Phase gate:** `docker compose ps` shows all services as healthy or running before `/gsd-verify-work`

### Wave 0 Gaps
None — validation is pure CLI commands. No test framework infrastructure files needed.

## Security Domain

`security_enforcement` is absent from config — treat as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Auth is handled at application layer |
| V3 Session Management | no | No new session handling |
| V4 Access Control | yes | No DB ports exposed to host; only port 80 on gateway |
| V5 Input Validation | no | No new input paths |
| V6 Cryptography | yes | Secrets in .env.prod (not in image layers), RSA keys in named volume |

### Known Threat Patterns for Production Compose

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| DB port exposed to host (5432, 27017, 6379) | Information Disclosure / Tampering | Use `expose:` not `ports:` for all infrastructure |
| Secrets in docker-compose YAML | Information Disclosure | All secrets via `${VAR}` from .env.prod |
| RabbitMQ management UI publicly accessible | Tampering / Information Disclosure | Remove `ports: 15672:15672` from prod |
| .env.prod committed to git | Information Disclosure | Add to .gitignore before creating the file |
| JWT private key in image layer | Information Disclosure | Named volume `jwt-keys` — key generated at runtime, never baked in |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | MongoDB in prod does not require authentication (no MONGO_INITDB_ROOT_USERNAME) for Phase 43 scope | Current State Audit | Low risk — MongoDB auth is a separate security hardening concern outside DOCK-05/06/07 scope |
| A2 | Running `docker compose -f docker-compose.prod.yml --env-file .env.prod up` is the correct invocation (not `docker-compose` v1 CLI) | Architecture Patterns | Low — project uses Docker Desktop which bundles Compose v2; v1 `docker-compose` has same --env-file flag |
| A3 | notification-web depends_on `redis` in dev compose is a copy-paste artifact; actual dependencies are rabbitmq + mongo only | Architecture Patterns, Pitfall 7 | Low — extra depends_on is harmless; removing it improves clarity |
| A4 | `version: "3.9"` is still valid for Docker Compose v2.x CLI (not breaking despite formal deprecation of the `version` key) | State of the Art | Low — keeps backward compat with older docker-compose versions |

## Open Questions

1. **Should docker-compose.prod.yml use pre-built GHCR image tags or local `build:` stanzas?**
   - What we know: Phase 46 (CI/CD deploy) will push to GHCR and pull from GHCR on VPS. Phase 43 is for local prod testing, not VPS deploy.
   - What's unclear: Should Phase 43's compose use `build:` (builds locally) or `image: ghcr.io/...` (requires GHCR)?
   - Recommendation: Use `build:` stanzas in Phase 43 so the file works without CI. Add `image:` tags alongside `build:` so Phase 46 can override with pre-built images using `docker compose pull`. Pattern: `image: ghcr.io/maksd/rutcampustrack/auth-service:latest` + `build: ...` — Compose uses the local build if no image exists, pulls the image if it does.

2. **Should Phase 43 create `.env.prod` itself or only `.env.prod.example`?**
   - What we know: DOCK-07 says ".env.prod file provides all secrets... and is gitignored". The file must exist for the compose to work.
   - What's unclear: Creating a real `.env.prod` with placeholder values in the repo is safe only if gitignore is applied first.
   - Recommendation: Create `.env.prod.example` (with placeholder values) committed to git, and `.env.prod` gitignored. The plan should include a task that creates `.env.prod` from `.env.prod.example` and documents the required real values.

## Sources

### Primary (HIGH confidence)
- [VERIFIED: docker-compose.yml] — Full service inventory, port configuration, healthcheck patterns, volume configuration
- [VERIFIED: services/notification-bot/bot/config.py] — Python bot env var configuration via pydantic-settings
- [VERIFIED: services/notification-bot/.env.example] — Bot env var names
- [VERIFIED: services/*/application.yml + services/*/application-prod.yml] — Spring Boot env var names, profiles, management config
- [VERIFIED: .gitignore] — Confirmed `.env.prod` is not currently gitignored
- [VERIFIED: Phase 41 RESEARCH.md] — Actuator healthcheck patterns, service ports, SecurityFilterChain status
- [VERIFIED: Phase 42 RESEARCH.md] — Dockerfile locations, build contexts, service port map

### Secondary (MEDIUM confidence)
- [ASSUMED: Docker Compose v3.9 behavior] — `env_file:` in service stanza vs CLI `--env-file` distinction; standard documented behavior

## Metadata

**Confidence breakdown:**
- Service inventory and port map: HIGH — fully verified from codebase
- Secret/env var inventory: HIGH — verified from all application.yml files and bot config.py
- Healthcheck patterns: HIGH — verified from dev compose + Phase 41 actuator research
- .gitignore gap: HIGH — verified by reading .gitignore
- Missing application-prod.yml files: HIGH — verified by filesystem glob
- Docker Compose syntax patterns: MEDIUM — based on training knowledge of Compose v3.9; no Context7 verification done (YAML format is stable)

**Research date:** 2026-04-07
**Valid until:** 2026-05-07 (stable Docker Compose v3.9 format, no churn expected)
