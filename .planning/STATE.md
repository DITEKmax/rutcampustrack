---
gsd_state_version: 1.0
milestone: v6.0
milestone_name: PWA + Web Push
status: executing
stopped_at: Completed 28-01-PLAN.md
last_updated: "2026-04-06T00:00:00.000Z"
last_activity: 2026-04-06 -- Phase 28 Wave 1 executing
progress:
  total_phases: 6
  completed_phases: 1
  total_plans: 5
  completed_plans: 4
  percent: 67
---

# Project State

## Current Milestone

v6.0 PWA + Web Push — Roadmap created, ready to plan Phase 27

## Current Position

Phase: 27 (web-push-backend) — EXECUTING
Plan: 1 of 3
Status: Ready to execute
Last activity: 2026-04-05 -- Phase 28 planning complete

Progress: [░░░░░░░░░░] 0%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-05)

**Core value:** Student mobile client «RutTrack» (React PWA) with native push notifications — independent from Telegram, installable, offline-capable
**Current focus:** Phase 27 — web-push-backend

## Completed Milestones

| Version | Name | Phases | Plans | Shipped |
|---------|------|--------|-------|---------|
| v1.0 | Auth Service + API Gateway | 1.1-1.4 | 4 | 2026-03-30 |
| v2.0 | Academic Service | 5-9 | 12 | 2026-03-31 |
| v3.0 | Schedule Service | 10-14 | 10 | 2026-04-04 |
| v4.0 | Attendance Service MVP | 15-19 | 12 | 2026-04-04 |
| v5.0 | Notification Service (Web + Bot) | 20-26 | 16 | 2026-04-05 |

## Accumulated Context

### Decisions

See PROJECT.md Key Decisions table for full history.

<<<<<<< HEAD
Recent decisions relevant to v6.0:

- v5.0: STOMP in-memory broker (no external broker) — sufficient for single-instance VPS
- v6.0 planning: VAPID keys persist in Redis (no TTL) — never regenerated on restart to avoid invalidating subscriptions
- v6.0 planning: `injectManifest` strategy for vite-plugin-pwa — required for custom `push` event handler in Service Worker
=======
- [Phase 28]: OPTIONS bypass before isPublicRoute; explicit CORS origins (not wildcard) with allow-credentials
>>>>>>> worktree-agent-a9702686

### Research Flags

- Phase 27: Verify BouncyCastle `bcprov-jdk18on` in Spring Boot executable JAR (signed JAR edge case)
- Phase 27: Add `MONGODB_URI` env var to notification-web in docker-compose (currently missing)
- Phase 29: vite-plugin-pwa 1.2.0 + Vite 8 peer dep — may need `--legacy-peer-deps`; fallback is Vite 7
- Phase 31: iOS push only works in standalone mode (A2HS installed) — guard before subscription attempt
- Phase 32: Physical iOS device required for geo check-in QA

### Blockers/Concerns

None.

## Session Continuity

<<<<<<< HEAD
Last session: 2026-04-05T19:30:09.971Z
Stopped at: Phase 27 context gathered
Resume file: .planning/phases/27-web-push-backend/27-CONTEXT.md
Next action: `/gsd-plan-phase 27`
=======
Last session: 2026-04-05T21:11:24.472Z
Stopped at: Completed 28-01-PLAN.md
Resume file: None
Next action: /gsd-new-milestone
>>>>>>> worktree-agent-a9702686
