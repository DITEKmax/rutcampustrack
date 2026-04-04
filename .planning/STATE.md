---
gsd_state_version: 1.0
milestone: v4.0
milestone_name: Attendance Service MVP
status: executing
stopped_at: Completed 18-03 (ReportService + ReportController)
last_updated: "2026-04-04T16:13:29.543Z"
last_activity: 2026-04-04
progress:
  total_phases: 4
  completed_phases: 3
  total_plans: 11
  completed_plans: 10
  percent: 0
---

# Project State

## Current Milestone

v4.0 Attendance Service MVP — Roadmap created, ready to plan Phase 15

## Current Position

Phase: 18 (read-path-reports) — EXECUTING
Plan: 3 of 4
Status: Ready to execute
Last activity: 2026-04-04

Progress: [░░░░░░░░░░] 0%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-04)

**Core value:** Attendance tracking backbone — Auth + Academic + Schedule shipped. Now building core: Attendance Service MVP (geo-checkin, manual marking, auto-absent, reports).
**Current focus:** Phase 18 — read-path-reports

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
- [Phase 16-event-consumers]: BulkMode.UNORDERED for auto-absent — one student error must not block rest of group
- [Phase 16-event-consumers]: No @Transactional on LessonEventService — MongoDB bulkOps and RabbitMQ have no shared transaction manager
- [Phase 16-event-consumers]: No try/catch in LessonEventService — exceptions propagate so AMQP nacks to DLQ on gRPC failure
- [Phase 16]: mongoTemplate.remove(new Query()) over dropCollection() — preserves MongoDB indexes between tests
- [Phase 16]: lenient() stubs in @BeforeEach for unit tests — avoids UnnecessaryStubbingException with Mockito strict mode
- [Phase 17-01]: testcontainers:redis BOM module does not exist — Redis Testcontainer uses GenericContainer from core testcontainers
- [Phase 17-01]: GeofenceService: volatile cache with 30m TTL, @PostConstruct try/catch, delegates to package-private GeoUtils Haversine
- [Phase 17-03]: @RequireRole(STUDENT) on marking controller — headman is a student with is_headman=true, headman-specific check in MarkingService
- [Phase 17-03]: @MockitoSpyBean used for AttendanceEventPublisher in integration tests — wraps real bean for verify() without mocking behavior
- [Phase 17-02]: @RequireRole is method-level only (ElementType.METHOD) — applied to checkin() method, not class level
- [Phase 17-02]: Integration test queue for INFRA-06 declared in @BeforeEach with purge — prevents message contamination between tests
- [Phase 18]: AttendanceReadPort in shared/port/ with zero checkin imports — domain isolation enforced
- [Phase 18]: getSubjectsByIds returns Map<Long,String> for simple id->name lookup in ReportService
- [Phase 18-01]: GetSubjectsByIds not cached — infrequent batch lookup for report generation, caching overhead not justified
- [Phase 18-read-path-reports]: ReportService uses AttendanceReadPort exclusively — zero imports from checkin/ package (domain isolation)
- [Phase 18-read-path-reports]: Left-join roster with ABSENT default: group members from gRPC, attendance from MongoDB, no record = ABSENT

### Known Tech Debt (from v3.0 audit)

- IllegalArgumentException → HTTP 500 in REST layer (missing handler in GlobalExceptionHandler)
- LSSN-03 idempotency: saveAll throws 409 on retry (no ON CONFLICT DO NOTHING)
- GetLessonsByGroup includes cancelled lessons — Phase 16 auto-absent MUST filter client-side: `.filter(l -> "closed".equals(l.getStatus()))`

### Critical Design Decision (unresolved before Phase 16)

- `semester_id` field required on every MongoDB attendance doc for report queries, but LessonResponse proto does not include it. Must decide: call GetActiveSemester gRPC per write, or cache on service startup. Resolve before Phase 16 plan begins.

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-04-04T16:13:29.539Z
Stopped at: Completed 18-03 (ReportService + ReportController)
Resume file: None
Next action: `/gsd:plan-phase 15`
