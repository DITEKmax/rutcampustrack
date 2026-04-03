---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: verifying
stopped_at: Completed 14-02-PLAN.md
last_updated: "2026-04-03T21:51:05.080Z"
last_activity: 2026-04-03
progress:
  total_phases: 4
  completed_phases: 4
  total_plans: 9
  completed_plans: 9
  percent: 40
---

# Project State

## Current Milestone

v3.0 Schedule Service — IN PROGRESS

## Current Position

Phase: 14
Plan: Not started
Status: Phase complete — ready for verification
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
- [Phase 13-status-transitions-rabbitmq-events]: fixedDelay=60000 over fixedRate prevents cron tick overlap when run exceeds 1 min
- [Phase 13-status-transitions-rabbitmq-events]: Two-phase @Transactional cron: phase1 saveAll makes new ACTIVE lessons visible to phase2 query — no separate catch-up logic needed (CRON-03)
- [Phase 14]: gRPC server queries repositories directly without caching — real-time sensitive, infrequent calls from Attendance Service
- [Phase 14-grpc-server]: Test via direct method invocation with mock StreamObserver — no in-process gRPC channel needed per D-06

### Pending Todos

None.

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-03T21:47:08.549Z
Stopped at: Completed 14-02-PLAN.md
Resume file: None
Next action: Execute 12-02-PLAN.md
