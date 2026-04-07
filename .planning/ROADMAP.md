# Roadmap — RutCampusTrack

## Milestones

- ✅ **v1.0 Auth Service + API Gateway** — Phases 1.1-1.4 (shipped 2026-03-30)
- ✅ **v2.0 Academic Service** — Phases 5-9 (shipped 2026-03-31)
- ✅ **v3.0 Schedule Service** — Phases 10-14 (shipped 2026-04-04)
- ✅ **v4.0 Attendance Service MVP** — Phases 15-19 (shipped 2026-04-04)
- ✅ **v5.0 Notification Service (Web + Bot)** — Phases 20-26 (shipped 2026-04-05)
- ✅ **v6.0 PWA + Web Push** — Phases 27-32 (shipped 2026-04-06)
- ✅ **v7.0 Frontends — Mini App, Web Panel, Landing** — Phases 33-40 (shipped 2026-04-07)
- 🚧 **v8.0 CI/CD, Deployment & Documentation** — Phases 41-48 (in progress)

## Phases

<details>
<summary>✅ v1.0 Auth Service + API Gateway (Phases 1.1-1.4) — SHIPPED 2026-03-30</summary>

- [x] Phase 1.1: Auth Service Core — JWT + Login (1/1 plan) — completed 2026-03-28
- [x] Phase 1.2: OTP Flow + Change Password (1/1 plan) — completed 2026-03-29
- [x] Phase 1.3: API Gateway JWT Filter + Routing (1/1 plan) — completed 2026-03-30
- [x] Phase 1.4: Seed Data + Integration Testing (1/1 plan) — completed 2026-03-30

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

<details>
<summary>✅ v2.0 Academic Service (Phases 5-9) — SHIPPED 2026-03-31</summary>

- [x] Phase 5: Entity and Repository Foundation (2/2 plans) — completed 2026-03-30
- [x] Phase 6: REST API + HATEOAS (4/4 plans) — completed 2026-03-30
- [x] Phase 7: gRPC Server (2/2 plans) — completed 2026-03-30
- [x] Phase 8: Redis Caching (2/2 plans) — completed 2026-03-31
- [x] Phase 9: RabbitMQ Events (2/2 plans) — completed 2026-03-31

Full details: `.planning/milestones/v2.0-ROADMAP.md`

</details>

<details>
<summary>✅ v3.0 Schedule Service (Phases 10-14) — SHIPPED 2026-04-04</summary>

- [x] Phase 10: Foundation (2/2 plans) — completed 2026-04-01
- [x] Phase 11: REST API + gRPC Client (3/3 plans) — completed 2026-04-01
- [x] Phase 12: Lesson Auto-Generation (2/2 plans) — completed 2026-04-01
- [x] Phase 13: Status Transitions + RabbitMQ Events (2/2 plans) — completed 2026-04-03
- [x] Phase 14: gRPC Server (2/2 plans) — completed 2026-04-04

Full details: `.planning/milestones/v3.0-ROADMAP.md`

</details>

<details>
<summary>✅ v4.0 Attendance Service MVP (Phases 15-19) — SHIPPED 2026-04-04</summary>

- [x] Phase 15: Infrastructure Foundation (2/2 plans) — completed 2026-04-04
- [x] Phase 16: Event Consumers (2/2 plans) — completed 2026-04-04
- [x] Phase 17: Write Path — Geo-Checkin + Manual Marking (3/3 plans) — completed 2026-04-04
- [x] Phase 18: Read Path — Reports (4/4 plans) — completed 2026-04-04
- [x] Phase 19: Report Security & Routing Fix (1/1 plan) — completed 2026-04-04

Full details: `.planning/milestones/v4.0-ROADMAP.md`

</details>

<details>
<summary>✅ v5.0 Notification Service (Web + Bot) (Phases 20-26) — SHIPPED 2026-04-05</summary>

- [x] Phase 20: Shared Infrastructure (3/3 plans) — completed 2026-04-04
- [x] Phase 21: Notification Web — WebSocket Core (2/2 plans) — completed 2026-04-05
- [x] Phase 22: Bot Infrastructure Layer (3/3 plans) — completed 2026-04-05
- [x] Phase 23: Bot Telegram Commands (3/3 plans) — completed 2026-04-05
- [x] Phase 24: Bot Event Notifications (2/2 plans) — completed 2026-04-05
- [x] Phase 25: Bot Reminder Lifecycle (2/2 plans) — completed 2026-04-05
- [x] Phase 26: Notification Deployment Hardening (1/1 plan) — completed 2026-04-05

Full details: `.planning/milestones/v5.0-ROADMAP.md`

</details>

<details>
<summary>✅ v6.0 PWA + Web Push (Phases 27-32) — SHIPPED 2026-04-06</summary>

- [x] Phase 27: Web Push Backend (3/3 plans) — completed 2026-04-05
- [x] Phase 28: API Gateway CORS + nginx (2/2 plans) — completed 2026-04-06
- [x] Phase 29: PWA Scaffold + Auth (3/3 plans) — completed 2026-04-06
- [x] Phase 30: Schedule + Check-in UI (2/2 plans) — completed 2026-04-06
- [x] Phase 31: Push Frontend + End-to-End Integration (2/2 plans) — completed 2026-04-06
- [x] Phase 32: Stats + Homework (2/2 plans) — completed 2026-04-06

Full details: `.planning/milestones/v6.0-ROADMAP.md`

</details>

<details>
<summary>✅ v7.0 Frontends — Mini App, Web Panel, Landing (Phases 33-40) — SHIPPED 2026-04-07</summary>

- [x] Phase 33: Infrastructure (2/2 plans) — completed 2026-04-06
- [x] Phase 34: Auth Service TMA (1/1 plan) — completed 2026-04-06
- [x] Phase 35: Landing Page (1/1 plan) — completed 2026-04-06
- [x] Phase 36: Mini App Scaffold + Auth (2/2 plans) — completed 2026-04-07
- [x] Phase 37: Mini App Features (2/2 plans) — completed 2026-04-07
- [x] Phase 38: Web Panel Scaffold + Auth (3/3 plans) — completed 2026-04-07
- [x] Phase 39: Web Panel Teacher (2/2 plans) — completed 2026-04-07
- [x] Phase 40: Web Panel Admin (3/3 plans) — completed 2026-04-07

Full details: `.planning/milestones/v7.0-ROADMAP.md`

</details>

### v8.0 CI/CD, Deployment & Documentation (In Progress)

**Milestone Goal:** Production-ready deployment pipeline — multi-stage Dockerfiles, docker-compose.prod.yml, SSL termination, GitHub Actions CI/CD, unified Swagger UI, and a complete project README.

- [x] **Phase 41: Actuator Standardization** - Add health/info endpoints to all 4 Java services (completed 2026-04-07)
- [ ] **Phase 42: Multi-Stage Dockerfiles** - Optimized build+runtime images for all services and frontends
- [x] **Phase 43: docker-compose.prod.yml** - Production compose with prod profile, secrets, Actuator healthchecks (completed 2026-04-07)
- [x] **Phase 44: Nginx Reverse Proxy + SSL** - SSL termination and path-based routing via Let's Encrypt (completed 2026-04-07)
- [x] **Phase 45: GitHub Actions CI** - Build, test, and lint pipeline for all services on push/PR (completed 2026-04-07)
- [ ] **Phase 46: GitHub Actions Deploy** - GHCR image push and SSH-based VPS deploy on merge to main
- [ ] **Phase 47: Unified Swagger UI** - Aggregated API docs at Gateway with springdoc webflux-ui
- [ ] **Phase 48: README** - Full project README with architecture, setup, API summary, deploy guide

## Phase Details

### Phase 41: Actuator Standardization
**Goal**: All Java backend services expose health and info endpoints for compose healthchecks and basic monitoring
**Depends on**: Nothing (config-only addition)
**Requirements**: MON-01, MON-02
**Success Criteria** (what must be TRUE):
  1. GET /actuator/health returns 200 with status UP on auth, academic, schedule, and attendance services
  2. GET /actuator/info returns 200 on all 4 services
  3. Sensitive actuator endpoints (env, heapdump, beans) return 404 in production profile
  4. No other actuator endpoints are exposed beyond health and info
**Plans:** 1/1 plans complete
Plans:
- [x] 41-01-PLAN.md — Add Actuator dependency, config, prod override, SecurityFilterChain fix, and integration tests for all 4 Java services

### Phase 42: Multi-Stage Dockerfiles
**Goal**: All services have optimized multi-stage Dockerfiles producing minimal production images
**Depends on**: Nothing (parallel with Phase 41)
**Requirements**: DOCK-01, DOCK-02, DOCK-03, DOCK-04
**Success Criteria** (what must be TRUE):
  1. All 5 Java services build via multi-stage Dockerfile with layered JARs (build stage + runtime stage)
  2. notification-web Dockerfile uses the same multi-stage pattern as other Java services
  3. notification-bot Dockerfile uses python:3.12-slim (not Alpine) and grpcio installs without error
  4. All 4 frontend Dockerfiles produce nginx containers with optimized static asset builds
  5. docker build completes successfully for every service image with no manual intervention
**Plans:** 3 plans
Plans:
- [ ] 42-01-PLAN.md — Root .dockerignore + multi-stage Dockerfiles for 5 standalone Java services
- [ ] 42-02-PLAN.md — Upgrade notification-web to multi-stage + harden notification-bot
- [ ] 42-03-PLAN.md — Frontend Dockerfiles: pwa, mini-app, web-panel, landing

### Phase 43: docker-compose.prod.yml
**Goal**: A production-ready compose file runs the entire system with the Spring production profile, no exposed database ports, and container-level healthchecks
**Depends on**: Phase 42
**Requirements**: DOCK-05, DOCK-06, DOCK-07, MON-03
**Success Criteria** (what must be TRUE):
  1. docker compose -f docker-compose.prod.yml up starts all services with SPRING_PROFILES_ACTIVE=prod
  2. No database ports (5432, 27017, 6379, 5672) are exposed to the host machine
  3. All backend service containers have Actuator-based healthchecks (depends_on healthy)
  4. .env.prod file provides all secrets (DB passwords, RSA keys, bot token) and is gitignored
**Plans:** 1/1 plans complete
Plans:
- [x] 43-01-PLAN.md — Gitignore + env template + missing prod profiles + docker-compose.prod.yml with all 17 services


### Phase 44: Nginx Reverse Proxy + SSL
**Goal**: A single nginx container terminates SSL and routes all external traffic to the correct backend service or frontend container
**Depends on**: Phase 43
**Requirements**: NET-01, NET-02, NET-03, NET-04, NET-05
**Success Criteria** (what must be TRUE):
  1. All HTTP traffic on port 80 redirects to HTTPS (301)
  2. HTTPS requests on port 443 are routed to the correct backend service by path prefix
  3. HTTPS requests route to all 4 frontend containers by path (PWA, Mini App, Web Panel, Landing)
  4. A valid Let's Encrypt certificate is installed and browser shows the padlock
  5. Certbot auto-renewal runs on schedule without manual intervention
**Plans:** 2/2 plans complete
Plans:
- [x] 44-01-PLAN.md — Nginx config files + docker-compose nginx/certbot services + api-gateway port fix
- [x] 44-02-PLAN.md — init-letsencrypt.sh bootstrap script + gitignore for generated files

### Phase 45: GitHub Actions CI
**Goal**: Every push and pull request triggers automated build, test, and lint checks for all services
**Depends on**: Phase 42
**Requirements**: CI-01, CI-02, CI-03, CI-04
**Success Criteria** (what must be TRUE):
  1. A push to any branch triggers the CI workflow and runs all Java service tests via Gradle
  2. The CI workflow runs Python notification-bot linting and tests
  3. The CI workflow builds and tests all 3 frontends (PWA, Mini App, Web Panel)
  4. Gradle build cache is restored between runs, reducing Java build time on cache hit
  5. A failing test causes the CI workflow to fail and blocks PR merge
**Plans:** 1/1 plans complete
Plans:
- [x] 45-01-PLAN.md — CI workflow with Java/Python/Frontend jobs + ruff config

### Phase 46: GitHub Actions Deploy
**Goal**: Merging to main automatically pushes images to GHCR and deploys the updated stack to the VPS
**Depends on**: Phase 45, Phase 43, Phase 44
**Requirements**: CI-05, CI-06, CI-07
**Success Criteria** (what must be TRUE):
  1. A merge to main triggers the deploy workflow and pushes all service images to GitHub Container Registry
  2. The deploy workflow connects to the VPS via SSH and runs docker compose pull + up -d
  3. GitHub Secrets store all sensitive values (RSA keys base64-encoded, DB passwords, SSH key, bot token)
  4. The running system on the VPS reflects the code from the latest main commit after deploy completes
**Plans:** 3 plans
Plans:
- [ ] 42-01-PLAN.md — Root .dockerignore + multi-stage Dockerfiles for 5 standalone Java services
- [ ] 42-02-PLAN.md — Upgrade notification-web to multi-stage + harden notification-bot
- [ ] 42-03-PLAN.md — Frontend Dockerfiles: pwa, mini-app, web-panel, landing

### Phase 47: Unified Swagger UI
**Goal**: A single Swagger UI at the API Gateway aggregates OpenAPI specs from all REST services into one browsable interface
**Depends on**: Phase 41
**Requirements**: DOC-01, DOC-02, DOC-03
**Success Criteria** (what must be TRUE):
  1. Navigating to the Gateway's Swagger UI URL shows a unified interface with specs from all REST services
  2. API operations from auth, academic, schedule, and attendance services are all browsable and executable
  3. springdoc version is 2.8.6 across all services with no version conflicts
**Plans:** 3 plans
Plans:
- [ ] 42-01-PLAN.md — Root .dockerignore + multi-stage Dockerfiles for 5 standalone Java services
- [ ] 42-02-PLAN.md — Upgrade notification-web to multi-stage + harden notification-bot
- [ ] 42-03-PLAN.md — Frontend Dockerfiles: pwa, mini-app, web-panel, landing

### Phase 48: README
**Goal**: The project repository has a complete README that communicates architecture, setup, API surface, and deployment to a developer reading it for the first time
**Depends on**: Phase 47
**Requirements**: DOC-04
**Success Criteria** (what must be TRUE):
  1. A developer can understand the system architecture (services, ports, databases, communication patterns) from the README alone
  2. A developer can follow the README setup instructions to run the system locally with docker compose
  3. The README links to the live Swagger UI and summarizes key API endpoints by role
  4. The README contains a complete deploy guide (VPS setup, GitHub Secrets, first certbot run, compose up)
**Plans:** 3 plans
Plans:
- [ ] 42-01-PLAN.md — Root .dockerignore + multi-stage Dockerfiles for 5 standalone Java services
- [ ] 42-02-PLAN.md — Upgrade notification-web to multi-stage + harden notification-bot
- [ ] 42-03-PLAN.md — Frontend Dockerfiles: pwa, mini-app, web-panel, landing

## Progress

**Execution Order:**
Phases execute in numeric order: 41 → 42 → 43 → 44 → 45 → 46 → 47 → 48
(Note: 42 and 45 can proceed in parallel as both depend only on Dockerfiles existing)

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1.1-1.4 | v1.0 | 4/4 | Complete | 2026-03-30 |
| 5-9 | v2.0 | 12/12 | Complete | 2026-03-31 |
| 10-14 | v3.0 | 10/10 | Complete | 2026-04-04 |
| 15-19 | v4.0 | 12/12 | Complete | 2026-04-04 |
| 20-26 | v5.0 | 16/16 | Complete | 2026-04-05 |
| 27-32 | v6.0 | 14/14 | Complete | 2026-04-06 |
| 33-40 | v7.0 | 16/16 | Complete | 2026-04-07 |
| 41. Actuator Standardization | v8.0 | 1/1 | Complete   | 2026-04-07 |
| 42. Multi-Stage Dockerfiles | v8.0 | 0/TBD | Not started | - |
| 43. docker-compose.prod.yml | v8.0 | 1/1 | Complete   | 2026-04-07 |
| 44. Nginx Reverse Proxy + SSL | v8.0 | 2/2 | Complete   | 2026-04-07 |
| 45. GitHub Actions CI | v8.0 | 1/1 | Complete    | 2026-04-07 |
| 46. GitHub Actions Deploy | v8.0 | 0/TBD | Not started | - |
| 47. Unified Swagger UI | v8.0 | 0/TBD | Not started | - |
| 48. README | v8.0 | 0/TBD | Not started | - |
