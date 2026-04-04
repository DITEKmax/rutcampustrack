---
gsd_state_version: 1.0
milestone: v4.0
milestone_name: Attendance Service MVP
status: verifying
stopped_at: Completed 15-02-PLAN.md
last_updated: "2026-04-04T09:06:13.801Z"
last_activity: 2026-04-04
progress:
  total_phases: 4
  completed_phases: 1
  total_plans: 2
  completed_plans: 2
  percent: 0
---

# Project State

## Current Milestone

v4.0 Attendance Service MVP — Roadmap created, ready to plan Phase 15

## Current Position

Phase: 16
Plan: Not started
Status: Phase complete — ready for verification
Last activity: 2026-04-04

Progress: [░░░░░░░░░░] 0%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-04)

**Core value:** Attendance tracking backbone — Auth + Academic + Schedule shipped. Now building core: Attendance Service MVP (geo-checkin, manual marking, auto-absent, reports).
**Current focus:** Phase 15 — infrastructure-foundation

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

- [Phase 15-infrastructure-foundation]: @WritingConverter/@ReadingConverter must import from org.springframework.data.convert (not .mongodb.core.convert) in Spring Data MongoDB 4.x
- [Phase 15-infrastructure-foundation]: grpc.server.port=-1 for attendance-service — pure gRPC consumer, no exposed gRPC server (D-08)
- [Phase 15-infrastructure-foundation]: MongoDB indexes created via @PostConstruct + ensureIndex (not auto-index-creation=true) — idempotent and explicit
- [Phase 15-infrastructure-foundation]: MongoCustomConversions in separate MongoConvertersConfig to avoid circular dependency in Spring 6.2
- [Phase 15-infrastructure-foundation]: RabbitConsumerTest uses AmqpAdmin queue checks — @MockitoSpyBean doesn't intercept @RabbitListener container calls

### Known Tech Debt (from v3.0 audit)

- IllegalArgumentException → HTTP 500 in REST layer (missing handler in GlobalExceptionHandler)
- LSSN-03 idempotency: saveAll throws 409 on retry (no ON CONFLICT DO NOTHING)
- GetLessonsByGroup includes cancelled lessons — Phase 16 auto-absent MUST filter client-side: `.filter(l -> "closed".equals(l.getStatus()))`

### Critical Design Decision (unresolved before Phase 16)

- `semester_id` field required on every MongoDB attendance doc for report queries, but LessonResponse proto does not include it. Must decide: call GetActiveSemester gRPC per write, or cache on service startup. Resolve before Phase 16 plan begins.

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-04T09:01:46.481Z
Stopped at: Completed 15-02-PLAN.md
Resume file: None
Next action: `/gsd:plan-phase 15`
