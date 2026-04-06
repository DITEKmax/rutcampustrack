---
gsd_state_version: 1.0
milestone: v6.0
milestone_name: PWA + Web Push
status: verifying
stopped_at: Completed 32-01-PLAN.md
last_updated: "2026-04-06T17:11:05.428Z"
last_activity: 2026-04-06
progress:
  total_phases: 6
  completed_phases: 4
  total_plans: 14
  completed_plans: 10
  percent: 71
---

# Project State

## Current Milestone

v6.0 PWA + Web Push — Phases 27-31 complete, ready to plan Phase 32

## Current Position

Phase: 31 (push-frontend-end-to-end-integration) — COMPLETE
Plan: 2 of 2
Status: Phase complete — ready for verification
Last activity: 2026-04-06

Progress: [████████░░] 83% (5/6 phases)

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-05)

**Core value:** Student mobile client «RutTrack» (React PWA) with native push notifications — independent from Telegram, installable, offline-capable
**Current focus:** Phase 32 — next phase

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

Recent decisions relevant to v6.0:

- v5.0: STOMP in-memory broker (no external broker) — sufficient for single-instance VPS
- v6.0 planning: VAPID keys persist in Redis (no TTL) — never regenerated on restart to avoid invalidating subscriptions
- v6.0 planning: `injectManifest` strategy for vite-plugin-pwa — required for custom `push` event handler in Service Worker
- [Phase 28]: OPTIONS bypass before isPublicRoute; explicit CORS origins (not wildcard) with allow-credentials
- [Phase 28]: Added .gitignore exception for frontends/pwa/dist/ — placeholder files must be tracked
- [Phase 29]: BeforeInstallPromptEvent must be inside declare global block for TypeScript module visibility
- [Phase 32]: useThreshold returns null on 404 — no red zone indicators shown (D-06)
- [Phase 32]: HomeworkPage route deferred to Plan 02 — component does not exist yet

### Research Flags

- Phase 27: Verify BouncyCastle `bcprov-jdk18on` in Spring Boot executable JAR (signed JAR edge case)
- Phase 27: Add `MONGODB_URI` env var to notification-web in docker-compose (currently missing)
- Phase 29: vite-plugin-pwa 1.2.0 + Vite 8 peer dep — may need `--legacy-peer-deps`; fallback is Vite 7
- Phase 31: iOS push only works in standalone mode (A2HS installed) — guard before subscription attempt
- Phase 32: Physical iOS device required for geo check-in QA

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-06T17:11:05.421Z
Stopped at: Completed 32-01-PLAN.md
Resume file: None
Next action: Plan Phase 32
