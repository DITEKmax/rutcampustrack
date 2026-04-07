# Research Summary — v8.0 CI/CD, Deployment & Documentation

**Synthesized:** 2026-04-07
**Sources:** STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md
**Confidence:** HIGH

---

## Executive Summary

v8.0 is a production-hardening milestone. All functional features shipped through v7.0. What remains: multi-stage Dockerfiles, production compose, SSL termination, GitHub Actions CI/CD, unified Swagger UI at the Gateway, and a project README.

Critical path: Dockerfiles → docker-compose.prod.yml → nginx+SSL → deploy workflow. CI can run in parallel. Swagger and README come last.

---

## Stack Additions

| Component | Add To | Version | Why |
|-----------|--------|---------|-----|
| spring-boot-starter-actuator | auth, academic, schedule, attendance | (matches Spring Boot 3.4) | Health checks for compose + monitoring |
| springdoc-openapi-starter-webflux-ui | api-gateway | 2.8.6 | Gateway is WebFlux — needs webflux variant for Swagger aggregation |
| springdoc version bump | all services | 2.7.0 → 2.8.6 | Bug fixes, Spring Boot 3.4 compatibility |
| certbot | new container | latest | Let's Encrypt SSL issuance + renewal |
| nginx (reverse proxy) | new container | alpine | SSL termination, route to all services by path |
| GitHub Actions | .github/workflows/ | N/A | CI + deploy pipelines |

**Do NOT add:** Prometheus/Grafana (overkill for single VPS), Kubernetes, Traefik, springdoc v3.x (not stable).

---

## Feature Table Stakes

| Feature | Complexity | Notes |
|---------|-----------|-------|
| Multi-stage Dockerfiles (Java) | LOW | Build + runtime stages, layered JARs |
| docker-compose.prod.yml | MEDIUM | No DB host ports, prod profile, .env.prod |
| GitHub Actions CI (build+test+lint) | MEDIUM | Gradle cache, parallel jobs |
| Nginx reverse proxy | LOW | Path-based routing to existing containers |
| SSL/Let's Encrypt | MEDIUM | Certbot bootstrap requires 2-phase first deploy |
| Actuator /health | LOW | Config-only, 1 dependency per service |
| Unified Swagger UI | MEDIUM | Gateway webflux-ui + url aggregation |
| README | LOW | Architecture + setup + deploy instructions |

---

## Critical Pitfalls

1. **Actuator exposure** (CRITICAL) — Never expose `env`/`heapdump`. Prod: `health,info` only.
2. **Certbot bootstrap deadlock** — HTTP-only nginx first, then issue certs, then HTTPS config.
3. **Dev→prod port leak** — Remove all DB host port mappings (5432, 27017, 6379, 5672).
4. **RSA/SSH key corruption in Secrets** — Base64-encode PEM keys before storing.
5. **Python Alpine fails** — `grpcio` has no musl wheels. Use `python:3.12-slim`.
6. **150MB images** — Use Spring Boot layered JARs + Docker layer caching.

---

## Suggested Phase Order

| # | Phase | Dependencies | Complexity |
|---|-------|-------------|-----------|
| 41 | Actuator Standardization | None | LOW |
| 42 | Multi-Stage Dockerfiles | None | MEDIUM |
| 43 | docker-compose.prod.yml | Dockerfiles | MEDIUM |
| 44 | Nginx Reverse Proxy + SSL | Prod compose | MEDIUM |
| 45 | GitHub Actions CI | Dockerfiles | MEDIUM |
| 46 | GitHub Actions Deploy | CI + prod compose + SSL | MEDIUM |
| 47 | Unified Swagger UI | Actuator (springdoc bump) | MEDIUM |
| 48 | README | All above | LOW |

---

## Open Questions

- **Domain name** — Required for nginx config and certbot
- **GHCR vs build-on-VPS** — GHCR recommended for portfolio value
- **notification-web in Swagger** — WebSocket endpoints, not REST; likely exclude

---

## Research Flags

- **Phase 44 (SSL):** Certbot bootstrap sequence — needs deeper research during planning
- **Phase 46 (Deploy):** VPS user/SSH setup — needs research during planning
- **Phases 41, 42, 43, 45, 47, 48:** Standard patterns, skip research

---
*Research completed: 2026-04-07*
*Ready for requirements: yes*
