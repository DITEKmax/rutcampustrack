# Requirements: RutCampusTrack

**Defined:** 2026-04-07
**Core Value:** Full-stack attendance tracking: 5 backend microservices + React PWA + Telegram Mini App + Angular Web Panel + Landing page

## v8.0 Requirements

Requirements for CI/CD, Deployment & Documentation milestone. Each maps to roadmap phases.

### Docker & Infrastructure

- [ ] **DOCK-01**: All 5 Java services have multi-stage Dockerfiles (build + runtime with layered JARs)
- [ ] **DOCK-02**: notification-web Dockerfile upgraded to multi-stage build
- [ ] **DOCK-03**: notification-bot Dockerfile uses python:3.12-slim (not Alpine)
- [ ] **DOCK-04**: Frontend Dockerfiles produce nginx containers with optimized builds
- [ ] **DOCK-05**: docker-compose.prod.yml runs all services with production Spring profile
- [ ] **DOCK-06**: docker-compose.prod.yml exposes only ports 80/443 (no DB host ports)
- [ ] **DOCK-07**: Production secrets managed via .env.prod (gitignored)

### Monitoring

- [x] **MON-01**: spring-boot-starter-actuator added to auth, academic, schedule, attendance services
- [x] **MON-02**: Actuator exposes only health and info endpoints in production profile
- [ ] **MON-03**: docker-compose.prod.yml uses Actuator healthchecks for service containers

### CI/CD

- [ ] **CI-01**: GitHub Actions workflow builds and tests all Java services on push/PR
- [ ] **CI-02**: GitHub Actions workflow lints and tests Python notification-bot on push/PR
- [ ] **CI-03**: GitHub Actions workflow builds and tests all 3 frontends (PWA, Mini App, Web Panel) on push/PR
- [ ] **CI-04**: CI uses Gradle caching for faster Java builds
- [ ] **CI-05**: GitHub Actions deploy workflow pushes images to GHCR on merge to main
- [ ] **CI-06**: GitHub Actions deploy workflow deploys to VPS via SSH after image push
- [ ] **CI-07**: GitHub Secrets configured for RSA keys, DB passwords, SSH key, bot token

### Networking & SSL

- [x] **NET-01**: Single nginx reverse proxy terminates SSL and routes to all backend services
- [x] **NET-02**: Nginx routes to all 4 frontend containers by path
- [ ] **NET-03**: Let's Encrypt SSL certificate issued via certbot standalone
- [x] **NET-04**: Certbot auto-renewal configured (cron or container restart)
- [x] **NET-05**: HTTP→HTTPS redirect for all traffic

### Documentation

- [ ] **DOC-01**: Unified Swagger UI accessible at Gateway with aggregated specs from all services
- [ ] **DOC-02**: springdoc upgraded to 2.8.6 across all services
- [ ] **DOC-03**: Gateway uses springdoc-openapi-starter-webflux-ui for Swagger aggregation
- [ ] **DOC-04**: Project README with architecture overview, setup guide, API summary, deploy instructions

## Future Requirements

### Deferred from previous milestones

- **EXCUSE-01**: Excuse tickets create/submit/review flow with event publishing
- **LATE-01**: Late check-in ("forgot to mark") flow with event publishing
- **NOTIF-02**: Live timer testing for midpoint/near-end reminders
- **WS-07**: Live broker-level group isolation verification
- **WPAN-13**: Headman assistant management (blocked by backend role constraint)

### Deferred from v8.0 scope

- **OBS-01**: Prometheus metrics collection
- **OBS-02**: Grafana dashboards
- **CI-08**: Path-filtered CI (only build changed services)
- **CI-09**: Automated rollback on deploy failure

## Out of Scope

| Feature | Reason |
|---------|--------|
| Kubernetes | Single VPS deployment, overkill for current scale |
| Traefik | Nginx already used across project |
| Prometheus/Grafana | Overkill for single VPS MVP |
| springdoc v3.x | Not stable yet |
| Docker Swarm | Single-node deployment |
| Blue-green deployment | Complexity not justified for current scale |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| MON-01 | Phase 41 | Complete |
| MON-02 | Phase 41 | Complete |
| DOCK-01 | Phase 42 | Pending |
| DOCK-02 | Phase 42 | Pending |
| DOCK-03 | Phase 42 | Pending |
| DOCK-04 | Phase 42 | Pending |
| DOCK-05 | Phase 43 | Pending |
| DOCK-06 | Phase 43 | Pending |
| DOCK-07 | Phase 43 | Pending |
| MON-03 | Phase 43 | Pending |
| NET-01 | Phase 44 | Complete |
| NET-02 | Phase 44 | Complete |
| NET-03 | Phase 44 | Pending |
| NET-04 | Phase 44 | Complete |
| NET-05 | Phase 44 | Complete |
| CI-01 | Phase 45 | Pending |
| CI-02 | Phase 45 | Pending |
| CI-03 | Phase 45 | Pending |
| CI-04 | Phase 45 | Pending |
| CI-05 | Phase 46 | Pending |
| CI-06 | Phase 46 | Pending |
| CI-07 | Phase 46 | Pending |
| DOC-01 | Phase 47 | Pending |
| DOC-02 | Phase 47 | Pending |
| DOC-03 | Phase 47 | Pending |
| DOC-04 | Phase 48 | Pending |

**Coverage:**
- v8.0 requirements: 26 total
- Mapped to phases: 26
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-07*
*Last updated: 2026-04-07 — traceability populated after roadmap creation*
