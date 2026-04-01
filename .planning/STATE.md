---
gsd_state_version: 1.0
milestone: v3.0
milestone_name: Schedule Service
status: executing
stopped_at: Completed 10-01-PLAN.md
last_updated: "2026-04-01T19:51:30Z"
last_activity: 2026-04-01 -- Phase 10 plan 01 complete
progress:
  total_phases: 5
  completed_phases: 0
  total_plans: 2
  completed_plans: 1
  percent: 10
---

# Project State

## Current Milestone

v3.0 Schedule Service — IN PROGRESS

## Current Position

Phase: 10 (foundation) — EXECUTING
Plan: 2 of 2
Status: Plan 01 complete, Plan 02 next
Last activity: 2026-04-01 -- Phase 10 plan 01 complete

Progress: [=.........] 10% (0/5 phases, 1/2 plans in phase 10)

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-03-31)

**Core value:** Schedule Service with full lesson lifecycle — the scheduling backbone for Attendance Service.
**Current focus:** Phase 10 — foundation

## Phase Map

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 10 | Foundation | LSSN-03, CRON-04 | Plan 01 complete |
| 11 | REST API + gRPC Client | TMPL-01..05, LSSN-04..07, VIEW-01..02 | Not started |
| 12 | Lesson Generation | LSSN-01, LSSN-02 | Not started |
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
- Week parity relative to semester start: `weeksSinceStart = WEEKS.between(semesterStart.with(MONDAY), lessonDate.with(MONDAY))` — NOT ISO week modulo
- gRPC client deadline: always `.withDeadlineAfter(3s)` on AcademicGrpcClient calls

**Plan 01 decisions:**
- No gRPC starters added — deferred to Phase 14 per D-10; only port placeholder
- No @Convert annotations on entity fields — autoApply=true converters handle enums
- No @ManyToOne associations — FK columns as Long IDs per project convention

### Pending Todos

- Verify `grpc.server.port` value in `academic-app/application.yml` before hardcoding gRPC client address in Phase 11 (research notes 19091 as convention — confirm)

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-01T19:51:30Z
Stopped at: Completed 10-01-PLAN.md
Resume file: .planning/phases/10-foundation/10-01-SUMMARY.md
Next action: Execute 10-02-PLAN.md
