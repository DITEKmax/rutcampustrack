---
gsd_state_version: 1.0
milestone: v4.0
milestone_name: Attendance Service MVP
status: requirements
stopped_at: Defining requirements
last_updated: "2026-04-04T18:00:00.000Z"
last_activity: 2026-04-04
progress:
  total_phases: 0
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Current Milestone

v4.0 Attendance Service MVP — Defining requirements

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Defining requirements
Last activity: 2026-04-04 — Milestone v4.0 started

Progress: [░░░░░░░░░░] 0%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-04)

**Core value:** Attendance tracking backbone — Auth + Academic + Schedule shipped. Now building core: Attendance Service MVP.
**Current focus:** Defining requirements for v4.0

## Phase Map

(To be defined by roadmapper)

## Accumulated Context

### Decisions

See `.planning/PROJECT.md` Key Decisions table for full list.

### Known Tech Debt (from v3.0 audit)

- IllegalArgumentException → HTTP 500 in REST layer (missing handler in GlobalExceptionHandler)
- LSSN-03 idempotency: saveAll throws 409 on retry, not silent dedup (no ON CONFLICT DO NOTHING)
- GetLessonsByGroup includes cancelled lessons (no caller filter control)

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-04
Stopped at: Defining requirements for v4.0
Resume file: None
Next action: Define requirements → create roadmap
