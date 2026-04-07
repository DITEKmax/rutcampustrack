---
gsd_state_version: 1.0
milestone: v7.0
milestone_name: Frontends — Mini App, Web Panel, Landing
status: verifying
stopped_at: Phase 36 context gathered
last_updated: "2026-04-07T00:22:21.784Z"
last_activity: 2026-04-07
progress:
  total_phases: 8
  completed_phases: 4
  total_plans: 6
  completed_plans: 6
  percent: 100
---

# Project State

## Current Milestone

v7.0 Frontends — Mini App, Web Panel, Landing

## Current Position

Phase: 37
Plan: Not started
Status: Phase complete — ready for verification
Last activity: 2026-04-07

Progress: [██░░░░░░░░] 25%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-06)

**Core value:** Full-stack attendance tracking: 5 backend microservices + React PWA + Telegram Mini App + Angular Web Panel + Landing page
**Current focus:** Phase 35 — landing-page

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
- [Phase 35]: Tailwind CDN + GSAP CDN for zero-build static landing page; CSS-only hamburger; darkMode media for automatic OS preference

### Research Flags

- Phase 36: Telegram WebView viewport edge cases + initData flow — deeper research needed during planning
- Phase 39: CdkTable virtual scroll with 500+ rows — research optimal pattern during planning
- Phases 33, 34, 35, 40: standard patterns, skip research

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-06T23:18:42.536Z
Stopped at: Phase 36 context gathered
Resume file: .planning/phases/36-mini-app-scaffold-auth/36-CONTEXT.md
Next action: `/gsd-plan-phase 35`
