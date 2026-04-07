---
gsd_state_version: 1.0
milestone: v8.0
milestone_name: CI/CD, Deployment & Documentation
status: executing
stopped_at: Completed 44-02-PLAN.md
last_updated: "2026-04-07T21:05:38.316Z"
last_activity: 2026-04-07
progress:
  total_phases: 8
  completed_phases: 4
  total_plans: 8
  completed_plans: 6
  percent: 75
---

# Project State

## Current Milestone

v8.0 CI/CD, Deployment & Documentation

## Current Position

Phase: 46
Plan: Not started
Status: Executing Phase 45
Last activity: 2026-04-07

Progress: [█░░░░░░░░░] 13%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-07)

**Core value:** Full-stack attendance tracking: 5 backend microservices + React PWA + Telegram Mini App + Angular Web Panel + Landing page
**Current focus:** Phase 45 — github-actions-ci

## Completed Milestones

| Version | Name | Phases | Plans | Shipped |
|---------|------|--------|-------|---------|
| v1.0 | Auth Service + API Gateway | 1.1-1.4 | 4 | 2026-03-30 |
| v2.0 | Academic Service | 5-9 | 12 | 2026-03-31 |
| v3.0 | Schedule Service | 10-14 | 10 | 2026-04-04 |
| v4.0 | Attendance Service MVP | 15-19 | 12 | 2026-04-04 |
| v5.0 | Notification Service (Web + Bot) | 20-26 | 16 | 2026-04-05 |
| v6.0 | PWA + Web Push | 27-32 | 14 | 2026-04-06 |
| v7.0 | Frontends — Mini App, Web Panel, Landing | 33-40 | 16 | 2026-04-07 |

## Accumulated Context

### Decisions

See PROJECT.md Key Decisions table for full history.

Recent decisions affecting v8.0:

- Use GHCR (not build-on-VPS) for portfolio value
- python:3.12-slim for notification-bot (Alpine has no grpcio musl wheels)
- springdoc-openapi-starter-webflux-ui for Gateway (WebFlux variant required)
- Actuator: expose only health and info in production profile (never env/heapdump)
- GlobalExceptionHandler must handle NoHandlerFoundException + NoResourceFoundException before generic Exception catch-all (Spring 6.2 behavior: these extend ServletException, not ErrorResponseException)
- Test profiles: disable health indicators for autoconfigure-excluded infra (management.health.redis/rabbit.enabled=false)
- [Phase 44]: Nginx reverse proxy with certbot sidecar for SSL termination; only nginx binds host ports
- [Phase 44]: Two-phase ACME bootstrap: staging cert test then production cert with --force-renewal

### Research Flags

- Phase 44 (SSL): Certbot bootstrap sequence needs deeper research during planning (2-phase first deploy)
- Phase 46 (Deploy): VPS user/SSH setup needs research during planning

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-07T20:19:05.742Z
Stopped at: Completed 44-02-PLAN.md
Next action: /gsd-plan-phase 42
