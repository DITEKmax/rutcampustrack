---
gsd_state_version: 1.0
milestone: v7.0
milestone_name: Frontends — Mini App, Web Panel, Landing
status: verifying
stopped_at: Completed 34-auth-service-tma/34-01-PLAN.md
last_updated: "2026-04-06T22:16:44.494Z"
last_activity: 2026-04-06
progress:
  total_phases: 8
  completed_phases: 2
  total_plans: 3
  completed_plans: 3
  percent: 100
---

# Project State

## Current Milestone

v7.0 Frontends — Mini App, Web Panel, Landing

## Current Position

Phase: 34 (auth-service-tma) — EXECUTING
Plan: 1 of 1
Status: Phase complete — ready for verification
Last activity: 2026-04-06

Progress: [█░░░░░░░░░] 12%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-06)

**Core value:** Full-stack attendance tracking: 5 backend microservices + React PWA + Telegram Mini App + Angular Web Panel + Landing page
**Current focus:** Phase 34 — auth-service-tma

## Completed Milestones

| Version | Name | Phases | Plans | Shipped |
|---------|------|--------|-------|---------|
| v1.0 | Auth Service + API Gateway | 1.1-1.4 | 4 | 2026-03-30 |
| v2.0 | Academic Service | 5-9 | 12 | 2026-03-31 |
| v3.0 | Schedule Service | 10-14 | 10 | 2026-04-04 |
| v4.0 | Attendance Service MVP | 15-19 | 12 | 2026-04-04 |
| v5.0 | Notification Service (Web + Bot) | 20-26 | 16 | 2026-04-05 |
| v6.0 | PWA + Web Push | 27-32 | 14 | 2026-04-06 |

## Accumulated Context

### Decisions

See PROJECT.md Key Decisions table for full history.

Recent decisions affecting current work:

- v6.0: httpOnly cookie for refresh token — Mini App cannot use this pattern (WebView drops cookies); use localStorage + body-based refresh (AUTH-02) instead
- v6.0: OPTIONS bypass before isPublicRoute in Gateway — pattern established; Phase 33 must add new origins without breaking existing CORS
- v5.0: STOMP in-memory broker — Mini App TMA-12 (real-time) deferred to future; no STOMP work in v7.0
- [Phase 34-auth-service-tma]: MessageDigest.isEqual for constant-time HMAC comparison prevents timing oracle (T-34-03)
- [Phase 34-auth-service-tma]: TMA_BOT_TOKEN env var with dev fallback — bot token never hardcoded (T-34-05)
- [Phase 34-auth-service-tma]: refresh-body delegates to AuthService.refresh() — one-line, same Redis JTI rotation, no over-engineering

### Research Flags

- Phase 36: Telegram WebView viewport edge cases + initData flow — deeper research needed during planning
- Phase 39: CdkTable virtual scroll with 500+ rows — research optimal pattern during planning
- Phases 33, 34, 35, 40: standard patterns, skip research

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-06T22:16:44.490Z
Stopped at: Completed 34-auth-service-tma/34-01-PLAN.md
Resume file: None
Next action: `/gsd-plan-phase 34`
