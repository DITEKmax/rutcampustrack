---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 13-status-transitions-rabbitmq-events-01-PLAN.md
last_updated: "2026-04-03T20:54:50.283Z"
last_activity: 2026-04-03
progress:
  total_phases: 4
  completed_phases: 2
  total_plans: 7
  completed_plans: 6
  percent: 40
---

# Project State

## Current Milestone

v3.0 Schedule Service — IN PROGRESS

## Current Position

Phase: 13 (status-transitions-rabbitmq-events) — EXECUTING
Plan: 2 of 2
Status: Ready to execute
Last activity: 2026-04-03

Progress: [████░░░░░░] 40% (4/10 plans)

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-03-31)

**Core value:** Schedule Service with full lesson lifecycle — the scheduling backbone for Attendance Service.
**Current focus:** Phase 13 — status-transitions-rabbitmq-events

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
- [Phase 12-lesson-auto-generation]: scheduleAffected boolean computed BEFORE applying setters to capture pre-update field state
- [Phase 12-lesson-auto-generation]: Integration tests use dayOfWeek=1 (TUESDAY) matching existing test conventions — avoids @Min(1) validation rejection
- [Phase 13-status-transitions-rabbitmq-events]: scheduleEventsExchange bean name avoids Spring name clash with academicEventsExchange in shared test context

### Pending Todos

None.

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-03T20:54:50.279Z
Stopped at: Completed 13-status-transitions-rabbitmq-events-01-PLAN.md
Resume file: None
Next action: Execute 12-02-PLAN.md
