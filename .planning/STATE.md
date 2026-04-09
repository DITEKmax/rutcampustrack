---
gsd_state_version: 1.0
milestone: v9.0
milestone_name: Frontend Unification — Single Login & Role-Based Web Clients
status: executing
stopped_at: Phase 52 complete
last_updated: "2026-04-09T17:00:00.000Z"
last_activity: 2026-04-09 -- Phase 52 complete
progress:
  total_phases: 9
  completed_phases: 4
  total_plans: 16
  completed_plans: 16
  percent: 100
---

# Project State

## Current Milestone

v9.0 Frontend Unification — Single Login & Role-Based Web Clients

## Current Position

Phase: 52 (student-web-cabinet-homework-stats-notifications-profile) — COMPLETE
Plans: 4/4
Status: Phase 52 complete — ready for phase 53
Last activity: 2026-04-09 -- Phase 52 complete

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-08)

**Core value:** Full-stack attendance tracking: 5 backend microservices + React PWA + Telegram Mini App + Angular Web Panel + Landing page
**Current focus:** Phase 52 — student-web-cabinet-homework-stats-notifications-profile

## Roadmap Summary

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 49 | Nginx Routing + Landing Dead Link Fix | INFRA-v9-01..03, 05..07; LAND-v9-01, 03 | Not started |
| 50 | baseHref Migration + Unified /login | INFRA-v9-04; AUTH-v9-01..07 | Not started |
| 51 | Student Web Cabinet — Shell + Schedule + Check-in | STU-WEB-01..03 | Not started |
| 52 | Student Web Cabinet — Homework + Stats + Notifications + Profile | STU-WEB-04..06, 09 | Not started |
| 53 | Student Web Cabinet — Excuses + Late Check-in + PWA Install Banner | STU-WEB-07, 08, 10 | Not started |
| 54 | Headman Web Cabinet — Group Management + Subjects | HEAD-WEB-01..04 | Not started |
| 55 | Headman Web Cabinet — Attendance Management + Stats | HEAD-WEB-05..08 | Not started |
| 56 | PWA Headman Mode | PWA-HEAD-01..04 | Not started |
| 57 | Landing Presentation Mode + Documentation | LAND-v9-02, 04, 05; DOCS-v9-01..04 | Not started |

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
| v8.0 | CI/CD, Deployment & Documentation | 41-48 | 11 | 2026-04-08 |

## Accumulated Context

### Decisions

See PROJECT.md Key Decisions table for full history.

### Key v9.0 Architecture Decisions

- **HEADMAN model:** `is_headman` boolean JWT claim (JwtService.java:96). UserRole enum stays `{ADMIN, TEACHER, STUDENT}` across all 5 services — no enum extension.
- **Unified /login:** Single Angular web-panel app with `baseHref: /` (was `/admin/`). All roles served from one SPA; lazy-loaded feature routes per role.
- **headmanGuard:** `role === 'STUDENT' && isHeadman === true`. Headman also passes `studentGuard`.
- **WPAN-13 fix approach:** Extend `@RequireRole` AOP aspect in academic-service to allow headman-scoped assistant operations when `X-Is-Headman: true` AND target group matches `X-Group-Id`. No new UserRole enum value.
- **PWA stays separate:** React PWA in `frontends/pwa/` is NOT merged into Angular. HEADMAN features added as new `features/headman/` directory within existing React project.

### Blockers/Concerns

- **baseHref migration risk:** `/admin/` → `/` may break external links. Phase 50 must grep for `ruttrack.site/admin` references before editing.
- **129 web-panel vitest tests:** Must continue passing through Phase 50 refactor (AUTH-v9-07).
- **63 PWA vitest tests:** Must continue passing through Phase 56 HEADMAN feature addition (PWA-HEAD-03).
- **Excuse / late-checkin backend:** Publishers for `excuse.requested`, `late_checkin.requested` still deferred from v5.0. STU-WEB-07, 08, HEAD-WEB-06, 07 implement UI with graceful degradation.
- **CI/CD pipeline:** No modifications to `.github/workflows/*.yml` (INFRA-v9-06).

### Critical Path

Phases 49 and 50 are CRITICAL-PATH and must complete before Blocks B (51-53), C (54-55), and D (56). Phase 57 (docs) is always last.

## Session Continuity

Last session: 2026-04-09T14:20:35.978Z
Stopped at: Phase 52 UI-SPEC approved
Next action: /gsd-plan-phase 49
