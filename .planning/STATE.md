---
gsd_state_version: 1.0
milestone: v8.0
milestone_name: CI/CD, Deployment & Documentation
status: executing
stopped_at: Roadmap written — ROADMAP.md, STATE.md, REQUIREMENTS.md traceability updated
last_updated: "2026-04-07T16:01:25.719Z"
last_activity: 2026-04-07 -- Phase 41 planning complete
progress:
  total_phases: 8
  completed_phases: 0
  total_plans: 1
  completed_plans: 1
  percent: 13
---

# Project State

## Current Milestone

v8.0 CI/CD, Deployment & Documentation

## Current Position

Phase: 41 of 48 (Actuator Standardization)
Plan: 1 of 1 complete
Status: Phase 41 complete — ready for Phase 42
Last activity: 2026-04-07 -- Phase 41 Plan 01 executed (actuator standardization)

Progress: [█░░░░░░░░░] 13%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-07)

**Core value:** Full-stack attendance tracking: 5 backend microservices + React PWA + Telegram Mini App + Angular Web Panel + Landing page
**Current focus:** Phase 41 — Actuator Standardization

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

### Research Flags

- Phase 44 (SSL): Certbot bootstrap sequence needs deeper research during planning (2-phase first deploy)
- Phase 46 (Deploy): VPS user/SSH setup needs research during planning

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-07
Stopped at: Completed 41-01-PLAN.md (Actuator Standardization)
Next action: /gsd-plan-phase 42
