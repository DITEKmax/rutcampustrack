---
gsd_state_version: 1.0
milestone: v3.0
milestone_name: Schedule Service
status: in_progress
stopped_at: Completed 12-01-PLAN.md
last_updated: "2026-04-01T22:22:37Z"
last_activity: 2026-04-01
progress:
  total_phases: 5
  completed_phases: 3
  total_plans: 5
  completed_plans: 4
  percent: 40
---

# Project State

## Current Milestone

v3.0 Schedule Service — IN PROGRESS

## Current Position

Phase: 12 (lesson-auto-generation)
Plan: 01 COMPLETE — ready for Plan 02
Status: Plan 01 executed — lesson generation foundation built
Last activity: 2026-04-01

Progress: [████░░░░░░] 40% (4/10 plans)

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-03-31)

**Core value:** Schedule Service with full lesson lifecycle — the scheduling backbone for Attendance Service.
**Current focus:** Phase 12 — lesson-auto-generation

## Phase Map

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 10 | Foundation | LSSN-03, CRON-04 | Complete |
| 11 | REST API + gRPC Client | TMPL-01..05, LSSN-04..07, VIEW-01..02 | Complete |
| 12 | Lesson Generation | LSSN-01, LSSN-02 | Plan 01 done, Plan 02 pending |
| 13 | Events + Cron | CRON-01..03, EVNT-01..04 | Not started |
| 14 | gRPC Server | GRPC-01..03 | Not started |

## Accumulated Context

### Decisions

See `.planning/PROJECT.md` Key Decisions table for full list.

**v3.0 key pre-decisions (from research):**

- Eager lesson generation at template creation (not lazy per GET) — simpler, idempotent via ON CONFLICT DO NOTHING
- `@Profile("!test")` guard on SchedulingConfig — matches existing `@ActiveProfiles("test")` in abstract test base
- `grpc.server.port: 19092` — avoids Auth (9090) and Academic (19091) conflicts
- `TZ=Europe/Moscow` in docker-compose.yml + `hibernate.jdbc.time_zone=Europe/Moscow` + injected Clock bean
- Week parity relative to semester start: `weeksSinceStart = WEEKS.between(anchor, currentWeekMonday)` using previousOrSame(MONDAY) anchor
- gRPC client deadline: always `.withDeadlineAfter(3s)` on AcademicGrpcClient calls
- [Phase 12-01]: firstWeekType stored as String (not enum) in Semester entity to avoid cross-service enum coupling
- [Phase 12-01]: Java-level default "odd" on Semester.firstWeekType prevents null violations when tests create Semester without setting the field
- [Phase 12-01]: V6 migration adds implicit varchar cast for week_type enum (same pattern as V5)

### Pending Todos

None.

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-01T22:22:37Z
Stopped at: Completed 12-01-PLAN.md
Resume file: .planning/phases/12-lesson-auto-generation/12-01-SUMMARY.md
Next action: Execute 12-02-PLAN.md
