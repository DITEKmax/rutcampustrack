# Feature Landscape

**Domain:** CI/CD, Docker Production Deployment, SSL, Observability, API Docs, README
**Project:** RutCampusTrack v8.0
**Researched:** 2026-04-07
**Confidence:** HIGH (well-established ecosystem with extensive official docs)

---

## Context: What Already Exists (Must Not Re-Implement)

- 5 Java microservices + Python Aiogram bot + API Gateway (Spring Cloud Gateway)
- 4 frontends: React PWA, React Mini App, Angular Web Panel, HTML Landing
- Dev docker-compose: PostgreSQL×2, MongoDB, Redis, RabbitMQ, nginx for 4 frontends
- Testcontainers integration tests for all Java services (227 vitest frontend tests)
- GitHub repo exists; no CI/CD, no production Dockerfiles, no prod compose, no SSL yet

---

## Table Stakes

Features expected in any production-shipped portfolio microservice project. Missing = incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Multi-stage Dockerfiles for each Java service | Container images are the unit of deployment; single-stage images ship JDK + build tools into prod | Medium | Build stage: `eclipse-temurin:21-jdk-alpine`; runtime stage: `eclipse-temurin:21-jre-alpine`; 60-70% image size reduction; dependency layer before code layer for cache hits; 7 targets: auth, academic, schedule, attendance, notification-web, api-gateway, notification-bot (Python) |
| docker-compose.prod.yml | Separates dev and prod configurations; dev compose has bind mounts and exposed debug ports | Medium | Override pattern: `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`; prod removes bind mounts, adds `restart: unless-stopped`, uses image refs from registry instead of `build:` context |
| Secrets and environment management for prod | Credentials must not exist in git repo | Low | `.env.prod` excluded from git (`.gitignore`); GitHub Actions secrets (SSH_HOST, SSH_USER, SSH_PRIVATE_KEY) for CI; no hardcoded passwords in any committed file |
| Nginx reverse proxy with SSL (Let's Encrypt) | HTTPS is mandatory for production; PWA Service Worker and Web Push API require TLS origin; Telegram Mini App requires HTTPS host | High | Certbot standalone or webroot challenge; Let's Encrypt free certs; auto-renew via compose service or cron; single nginx terminates SSL for all upstreams; port 80 → 301 redirect to 443 |
| Spring Boot Actuator /health endpoint | Production services must expose liveness probe for health-check in compose and future orchestration | Low | `spring-boot-starter-actuator` already a transitive dep; expose `/actuator/health` and `/actuator/info` only; management port 8081 not proxied through nginx externally; docker-compose `healthcheck:` uses it |
| GitHub Actions CI — Java build and test | Every Java project on GitHub has CI; without it, test regressions are invisible | Medium | `actions/setup-java` with Temurin 21; `./gradlew build` in monorepo root; Gradle cache on `~/.gradle/caches` and `~/.gradle/wrapper` keyed by build file hashes |
| Project README | First thing every reviewer reads; portfolio gate-check; employers and professors evaluate this before looking at code | Medium | Architecture diagram, service table with ports/tech, quick-start (docker compose up), deploy guide, API summary, tech stack badges |

---

## Differentiators

Features above table-stakes baseline that meaningfully improve the project for portfolio or production use.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| GitHub Actions SSH deploy to VPS | Closes dev-to-prod loop; one push ships everything; demonstrates complete DevOps lifecycle | Medium | `appleboy/ssh-action` or raw SSH with private key stored in GitHub secret; remote command: `docker compose pull && docker compose up -d`; deploy only on push to `main` after CI passes |
| Docker images pushed to GHCR with git SHA tag | Enables rollback to any previous commit; immutable image per commit | Low | Tag: `ghcr.io/user/service:${{ github.sha }}` plus `:latest`; GitHub Container Registry is free for public repos; login: `docker/login-action` with `registry: ghcr.io` |
| Unified Swagger UI via API Gateway | Single URL for all 5 services' API docs; portfolio showcase; removes need to know each service's port | Medium | `springdoc-openapi` on each service exposes `/v3/api-docs`; Gateway's `springdoc.swagger-ui.urls` config lists all upstream paths; existing HATEOAS + `@Operation` annotations already in api-contract modules surface automatically |
| Path-filtered CI per service (changed modules only) | Prevents 8-service rebuild on a bot-only change; critical for fast feedback loop | Medium | `dorny/paths-filter` action detects which `services/` subdirs changed; matrix strategy runs build/test only for affected service; note: start simple (build all) and add filtering when CI time exceeds 5 min |
| GitHub Actions — Python bot CI (lint + test) | Validates the Telegram bot is not silently broken; 108 pytest tests already exist | Low | `actions/setup-python` 3.11+; `ruff` for linting; `pytest` with `pytest-asyncio`; `pip cache` keyed by `requirements.txt` hash |
| GitHub Actions — frontend test jobs | 227 vitest tests across 3 frontends are already written; CI should run them on every push | Low | `actions/setup-node` 20+; `npm ci`; `npm test -- --run` (vitest non-watch mode detects CI automatically); node_modules cache by package-lock hash; separate parallel jobs for pwa, mini-app, web-panel |
| Actuator /metrics with Micrometer + Prometheus format | Enables future Grafana without any instrumentation code change; adds observability signal now | Low | Add `micrometer-registry-prometheus` dep; expose `/actuator/prometheus` on management port only (not through nginx); zero code change — pure config and dependency |
| Nginx security headers | Adds security signal; PWA requires `Strict-Transport-Security`; makes portfolio look production-grade | Low | `X-Frame-Options SAMEORIGIN`, `X-Content-Type-Options nosniff`, `Strict-Transport-Security max-age=31536000`, basic `Content-Security-Policy`; all in nginx.conf, no application code change |
| Docker Compose healthchecks | Services wait for dependencies to be healthy before starting; eliminates "database not ready" race condition on cold start | Low | `healthcheck:` on postgres, mongo, redis, rabbitmq; `depends_on: condition: service_healthy` on application services; uses existing `/actuator/health` for Java services |
| Gradle build cache in CI | Cuts Java CI build time by ~60% on incremental runs | Low | Cache action: `~/.gradle/caches`, `~/.gradle/wrapper`; restore-key fallback; already standard for any Gradle project on GitHub Actions |

---

## Anti-Features

Features to explicitly NOT build in v8.0.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Kubernetes / Helm | Massive operational overhead for a single VPS; out of scope per PROJECT.md | Stay with docker-compose; document as "K8s-ready" in README; mention stateless services |
| Prometheus + Grafana + Alertmanager stack | Adds 3 new containers; full monitoring is not the v8.0 goal; increases VPS RAM usage | Expose `/actuator/prometheus`; document "plug in Grafana here" in README; defer to future milestone |
| Docker Swarm mode | Adds complexity with no benefit for single-node VPS deploy | Use plain docker-compose with `restart: unless-stopped`; no swarm orchestration needed |
| Per-service Swagger UI instances accessible externally | Each service running its own publicly accessible UI wastes ports and fragments docs | Aggregate all specs in a single Gateway Swagger UI; service UIs available only on localhost management port if needed |
| Nginx Proxy Manager (GUI container) | Adds container overhead; config-as-code is more portfolio-appropriate and reproducible | Write nginx.conf files directly; version-control them in repo |
| Building Docker images on the VPS | Requires JDK + Node.js + Gradle on production server; slow; defeats CI/CD purpose | Build in GitHub Actions, push to GHCR, pull image on VPS |
| Committing `.env.prod` or any credentials | Security leak; disqualifying for a portfolio project | `.env.prod` in `.gitignore`; CI uses GitHub Actions secrets; document format in `.env.prod.example` |
| Monolithic single deploy job (all 7 services always redeploy) | Slow and risky; a Python bot change should not restart 5 Java services | Path-filter triggers; per-service deploy steps; only redeploy what changed |
| Testing inside the final runtime Docker image | JRE-only image has no test tooling; mixing test and runtime is an anti-pattern | Multi-stage: run tests in the JDK build stage; ship JRE-only runtime stage with no test artifacts |
| Self-hosted GitHub Actions runner | Requires VPS agent setup; adds operational complexity; GitHub-hosted runners are sufficient for this project | Use `ubuntu-latest` GitHub-hosted runners; free tier sufficient for solo project |
| Docker secrets (Swarm) for single-node compose | Docker secrets require Swarm mode; `.env.prod` file is sufficient and simpler for single VPS | `.env.prod` file on VPS server, not committed, with restricted permissions (`chmod 600`) |

---

## Feature Dependencies

```
Multi-stage Dockerfiles (all services)
  → docker-compose.prod.yml (references registry images, not build: context)
  → GHCR push in GitHub Actions CI (images must exist before prod compose can pull)
    → GitHub Actions SSH deploy (runs docker compose pull + up on VPS)

Nginx reverse proxy
  → SSL / Let's Encrypt (nginx must serve HTTP first for ACME challenge)
    → HTTPS is required before: PWA Web Push, Telegram Mini App, A2HS install

Spring Boot Actuator /health
  → Docker Compose healthchecks (healthcheck URL uses /actuator/health)
  → Actuator /metrics + Micrometer (same dependency, same config, extend later)

Unified Swagger UI (Gateway)
  → springdoc-openapi on each service (must be added to all 5 app build.gradle.kts)
  → Gateway springdoc config (swagger-ui.urls pointing to service /v3/api-docs paths)
  → Nginx proxy (Swagger UI served through nginx, not raw Gateway port)

GitHub Actions CI
  → Gradle build cache (configured in same workflow file)
  → GHCR image push (docker/build-push-action after tests pass)
  → SSH deploy (deploy job depends_on CI job success)

Path-filtered CI
  → Matrix deploy (only deploy changed service entries in matrix)
  Note: implement after basic "build all" CI is stable
```

---

## MVP Recommendation

Prioritize in this order for v8.0:

1. **Multi-stage Dockerfiles** (all 5 Java services + Python bot + frontend nginx configs) — foundational; nothing else works without this
2. **docker-compose.prod.yml** — production compose with restart policies, no bind mounts, image refs
3. **Nginx reverse proxy + Let's Encrypt SSL** — HTTPS required for existing PWA Web Push and Mini App host validation
4. **Spring Boot Actuator /health** — enables healthchecks in compose; one dep + two config lines per service
5. **GitHub Actions CI** — Java build + test with Gradle cache; Gradle monorepo builds all subprojects naturally
6. **GHCR image push** — tag with git SHA + latest; free for public repo
7. **GitHub Actions SSH deploy** — deploy job runs after CI passes; closes the loop
8. **Unified Swagger UI via Gateway** — add springdoc to all services; configure Gateway aggregation
9. **README** — architecture diagram, service table, quick-start, deploy guide

Defer within v8.0 scope (add once core works):
- Path-filtered per-service CI: start with "build all on push to main"; add dorny/paths-filter when CI time exceeds 5 min
- Python bot CI and frontend test CI: these are polish; add in later phases of v8.0
- Actuator /metrics + Micrometer: add the dep, expose nothing externally, document "Grafana hookup point" in README
- Nginx security headers: add after nginx config is stable

---

## Sources

- [GitHub Actions monorepo guide 2026 (Pockit)](https://pockit.tools/blog/github-actions-monorepo-runners-guide-2026/)
- [How to Handle Monorepos with GitHub Actions (OneUptime)](https://oneuptime.com/blog/post/2026-01-26-monorepos-github-actions/view)
- [Executing Gradle builds on GitHub Actions (official Gradle docs)](https://docs.gradle.org/current/userguide/github-actions.html)
- [Multi-stage Docker builds concepts (Docker docs)](https://docs.docker.com/get-started/docker-concepts/building-images/multi-stage-builds/)
- [9 Tips for Containerizing Spring Boot (Docker official blog)](https://www.docker.com/blog/9-tips-for-containerizing-your-spring-boot-code/)
- [Integrate OpenAPI with Spring Cloud Gateway (Baeldung)](https://www.baeldung.com/spring-cloud-gateway-integrate-openapi)
- [Spring Boot Actuator production-ready features (Spring docs)](https://docs.spring.io/spring-boot/reference/actuator/index.html)
- [Nginx + Certbot + docker-compose bootstrap (wmnnd/nginx-certbot)](https://github.com/wmnnd/nginx-certbot)
- [Docker Compose SSH Deployment action (GitHub Marketplace)](https://github.com/marketplace/actions/docker-compose-deployment-ssh)
- [Docker Compose secrets and environment variables best practices (Docker docs)](https://docs.docker.com/compose/how-tos/use-secrets/)
- [A README for your microservice GitHub repository (DZone)](https://dzone.com/articles/a-readme-for-your-microservice-github-repository)
- [Running Multiple Spring Boot Containers with NGINX (DEV Community)](https://dev.to/ankitdevcode/running-multiple-spring-boot-containers-with-nginx-reverse-proxy-docker-compose-4a69)
- [Setting up Vitest with GitHub Actions (Steve Kinney)](https://stevekinney.com/courses/testing/continuous-integration)

---

*Feature research for: RutCampusTrack v8.0 — CI/CD, Deployment & Documentation*
*Researched: 2026-04-07*
