---
gsd_state_version: 1.0
milestone: v4.0
milestone_name: Attendance Service MVP
status: planning
stopped_at: Phase 15 context gathered
last_updated: "2026-04-03T23:10:06.452Z"
last_activity: 2026-04-04 — Roadmap created for v4.0 (4 phases, 23 requirements)
progress:
  total_phases: 4
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Current Milestone

v4.0 Attendance Service MVP — Roadmap created, ready to plan Phase 15

## Current Position

Phase: 15 of 18 (Infrastructure Foundation)
Plan: — (not yet planned)
Status: Ready to plan
Last activity: 2026-04-04 — Roadmap created for v4.0 (4 phases, 23 requirements)

Progress: [░░░░░░░░░░] 0%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-04)

**Core value:** Attendance tracking backbone — Auth + Academic + Schedule shipped. Now building core: Attendance Service MVP (geo-checkin, manual marking, auto-absent, reports).
**Current focus:** Phase 15 — Infrastructure Foundation

## Phase Map

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 15 | Infrastructure Foundation | INFRA-01..05 | Not started |
| 16 | Event Consumers | MARK-03..05 | Not started |
| 17 | Write Path — Geo-Checkin + Manual Marking | CHKN-01..07, MARK-01..02, INFRA-06 | Not started |
| 18 | Read Path — Reports | RPRT-01..05 | Not started |

## Accumulated Context

### Decisions

See `.planning/PROJECT.md` Key Decisions table for full list.

### Known Tech Debt (from v3.0 audit)

- IllegalArgumentException → HTTP 500 in REST layer (missing handler in GlobalExceptionHandler)
- LSSN-03 idempotency: saveAll throws 409 on retry (no ON CONFLICT DO NOTHING)
- GetLessonsByGroup includes cancelled lessons — Phase 16 auto-absent MUST filter client-side: `.filter(l -> "closed".equals(l.getStatus()))`

### Critical Design Decision (unresolved before Phase 16)

- `semester_id` field required on every MongoDB attendance doc for report queries, but LessonResponse proto does not include it. Must decide: call GetActiveSemester gRPC per write, or cache on service startup. Resolve before Phase 16 plan begins.

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-03T23:10:06.448Z
Stopped at: Phase 15 context gathered
Resume file: .planning/phases/15-infrastructure-foundation/15-CONTEXT.md
Next action: `/gsd:plan-phase 15`
