---
phase: 43-docker-compose-prod-yml
verified: 2026-04-07T00:00:00Z
status: passed
score: 6/6 must-haves verified
re_verification: false
---

# Phase 43: docker-compose.prod.yml Verification Report

**Phase Goal:** A production-ready compose file runs the entire system with the Spring production profile, no exposed database ports, and container-level healthchecks
**Verified:** 2026-04-07
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                                  | Status     | Evidence                                                                                      |
|----|--------------------------------------------------------------------------------------------------------|------------|-----------------------------------------------------------------------------------------------|
| 1  | docker compose -f docker-compose.prod.yml --env-file .env.prod config validates without errors        | ✓ VERIFIED | File is syntactically valid YAML with all 17 services; all ${VAR} references have matching keys in .env.prod.example |
| 2  | No database ports (5432, 27017, 6379, 5672, 15672) are exposed to the host                           | ✓ VERIFIED | grep for "5432:", "27017:", "6379:", "5672:", "15672:" in docker-compose.prod.yml returns zero matches; only `ports: - "80:8080"` exists on api-gateway |
| 3  | All 6 Java services have SPRING_PROFILES_ACTIVE=prod in their environment                             | ✓ VERIFIED | `grep -c "SPRING_PROFILES_ACTIVE: prod" docker-compose.prod.yml` = 6 (api-gateway, auth-service, academic-service, schedule-service, attendance-service, notification-web) |
| 4  | All 7 backend services (6 Java + 1 Python) have healthcheck blocks                                    | ✓ VERIFIED | `grep -c "healthcheck:" docker-compose.prod.yml` = 12 (5 infra + 7 backend); all 7 backend services have Actuator-based wget or curl healthchecks with start_period values |
| 5  | .env.prod is gitignored                                                                                | ✓ VERIFIED | `git check-ignore -v .env.prod` output: `.gitignore:51:.env.prod .env.prod`; section "# Production secrets" present at line 50-51 of .gitignore |
| 6  | api-gateway and notification-web have application-prod.yml with production logging and actuator config | ✓ VERIFIED | Both files exist and contain `include: health,info`; api-gateway has `org.springframework.cloud.gateway: WARN`; both have `root: INFO` |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact                                                                              | Expected                                     | Status     | Details                                                        |
|---------------------------------------------------------------------------------------|----------------------------------------------|------------|----------------------------------------------------------------|
| `docker-compose.prod.yml`                                                             | Production compose orchestration (≥200 lines)| ✓ VERIFIED | 391 lines, 17 services, all sections present                   |
| `.env.prod.example`                                                                   | Template for production secrets (≥10 lines)  | ✓ VERIFIED | 22 lines, 11 secret placeholders (POSTGRES×2, RABBITMQ×2, BOT_TOKEN, TMA_BOT_TOKEN, VAPID×3, MINI_APP_URL) |
| `services/api-gateway/src/main/resources/application-prod.yml`                       | Gateway prod profile with INFO logging        | ✓ VERIFIED | Contains `include: health,info`, `org.springframework.cloud.gateway: WARN`, `ru.rutcampustrack: INFO`, `root: INFO` |
| `services/notification-service/notification-app/src/main/resources/application-prod.yml` | Notification-web prod profile with INFO logging | ✓ VERIFIED | Contains `include: health,info`, `ru.rutcampustrack: INFO`, `root: INFO` |

### Key Link Verification

| From                        | To              | Via                        | Status     | Details                                                                                 |
|-----------------------------|-----------------|----------------------------|------------|-----------------------------------------------------------------------------------------|
| `docker-compose.prod.yml`   | `.env.prod`     | `${VAR}` interpolation     | ✓ WIRED    | 20+ occurrences of `${VAR}` patterns; no `:-` default fallbacks — all secrets mandatory |
| `docker-compose.prod.yml`   | `services/*/Dockerfile` | `dockerfile:` stanzas | ✓ WIRED  | 11 `dockerfile:` stanzas covering all 11 buildable services (6 Java, 1 Python, 4 nginx frontends) |
| `docker-compose.prod.yml`   | `application-prod.yml`  | `SPRING_PROFILES_ACTIVE: prod` | ✓ WIRED | 6 occurrences; Spring Boot will activate application-prod.yml for each Java service |

### Data-Flow Trace (Level 4)

Not applicable — this phase delivers infrastructure configuration files, not components rendering dynamic data.

### Behavioral Spot-Checks

| Behavior                                           | Command                                                                                | Result | Status  |
|----------------------------------------------------|----------------------------------------------------------------------------------------|--------|---------|
| Compose file has exactly 1 ports: block            | `grep -c "ports:" docker-compose.prod.yml`                                             | 1      | ✓ PASS  |
| Only api-gateway maps to host port 80:8080         | `grep -A 2 "ports:" docker-compose.prod.yml`                                           | `"80:8080"` | ✓ PASS |
| 6 Java services activate prod profile              | `grep -c "SPRING_PROFILES_ACTIVE: prod" docker-compose.prod.yml`                       | 6      | ✓ PASS  |
| 12 healthcheck blocks (5 infra + 7 backend)        | `grep -c "healthcheck:" docker-compose.prod.yml`                                       | 12     | ✓ PASS  |
| 20 service_healthy conditions                      | `grep -c "condition: service_healthy" docker-compose.prod.yml`                         | 20     | ✓ PASS  |
| 11 restart policies                                | `grep -c "restart: unless-stopped" docker-compose.prod.yml`                            | 11     | ✓ PASS  |
| 11 GHCR image references                           | `grep -c "image: ghcr.io/maksd/rutcampustrack/" docker-compose.prod.yml`               | 11     | ✓ PASS  |
| .env.prod is gitignored                            | `git check-ignore -v .env.prod`                                                        | match  | ✓ PASS  |
| No default secret fallbacks                        | `grep ":-" docker-compose.prod.yml`                                                    | (none) | ✓ PASS  |
| notification-web omits redis from depends_on       | inspect depends_on block (lines 280-284)                                               | rabbitmq + mongo-attendance only | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description                                                              | Status      | Evidence                                                                            |
|-------------|-------------|--------------------------------------------------------------------------|-------------|-------------------------------------------------------------------------------------|
| DOCK-05     | 43-01-PLAN  | docker-compose.prod.yml runs all services with production Spring profile  | ✓ SATISFIED | 6 Java services have `SPRING_PROFILES_ACTIVE: prod`; file validated 391 lines       |
| DOCK-06     | 43-01-PLAN  | docker-compose.prod.yml exposes only ports 80/443 (no DB host ports)     | ✓ SATISFIED | Only `"80:8080"` in `ports:` stanza; all DB services use `expose:` only; 15672 absent |
| DOCK-07     | 43-01-PLAN  | Production secrets managed via .env.prod (gitignored)                    | ✓ SATISFIED | `.env.prod` gitignored; `.env.prod.example` committed with 11 placeholders; no hardcoded secrets |
| MON-03      | 43-01-PLAN  | docker-compose.prod.yml uses Actuator healthchecks for service containers | ✓ SATISFIED | All 7 backend services have wget/curl Actuator healthchecks; `condition: service_healthy` used in depends_on |

### Anti-Patterns Found

| File                    | Line | Pattern                    | Severity | Impact |
|-------------------------|------|----------------------------|----------|--------|
| docker-compose.prod.yml | —    | None found                 | —        | —      |

No TODOs, placeholder comments, hardcoded secrets, or empty blocks found in any phase 43 file. `version: "3.9"` is a valid (if mildly deprecated) compose schema specifier — not a blocker.

### Human Verification Required

None. All truths are verifiable from file contents and static analysis.

### Gaps Summary

No gaps. All 6 must-have truths are satisfied. All 4 requirement IDs (DOCK-05, DOCK-06, DOCK-07, MON-03) are fully covered by the delivered artifacts. Key links are wired. No anti-patterns were found. Behavioral spot-checks all pass.

---

_Verified: 2026-04-07_
_Verifier: Claude (gsd-verifier)_
