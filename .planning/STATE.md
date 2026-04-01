---
gsd_state_version: 1.0
milestone: v3.0
milestone_name: Schedule Service
status: planning
stopped_at: Phase 10 context gathered
last_updated: "2026-04-01T19:25:14.774Z"
last_activity: 2026-03-31 — Roadmap created for v3.0
progress:
  total_phases: 5
  completed_phases: 0
  total_plans: 1
  completed_plans: 0
  percent: 0
---

# Project State

## Current Milestone

v3.0 Schedule Service — 🚧 IN PROGRESS

## Current Position

Phase: 10 — Foundation (not started)
Plan: —
Status: Roadmap created, ready to plan Phase 10
Last activity: 2026-03-31 — Roadmap created for v3.0

Progress: [░░░░░░░░░░] 0% (0/5 phases)

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-03-31)

**Core value:** Schedule Service with full lesson lifecycle — the scheduling backbone for Attendance Service.
**Current focus:** Phase 10 — Foundation (entities, repos, security, timezone, gRPC port)

## Phase Map

| Phase | Name | Requirements | Status |
|-------|------|--------------|--------|
| 10 | Foundation | LSSN-03, CRON-04 | Not started |
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

### Pending Todos

- Verify `grpc.server.port` value in `academic-app/application.yml` before hardcoding gRPC client address in Phase 11 (research notes 19091 as convention — confirm)
- Choose `@Profile("!test")` vs `@MockitoBean ScheduledAnnotationBeanPostProcessor` for scheduling test isolation — apply at Phase 10 uniformly

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-01T19:25:14.762Z
Stopped at: Phase 10 context gathered
Resume file: .planning/phases/10-foundation/10-CONTEXT.md
Next action: `/gsd:plan-phase 10`
