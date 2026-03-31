# Roadmap — RutCampusTrack

## Milestones

- ✅ **v1.0 Auth Service + API Gateway** — Phases 1.1-1.4 (shipped 2026-03-30)
- ✅ **v2.0 Academic Service** — Phases 5-9 (shipped 2026-03-31)
- 🚧 **v3.0 Schedule Service** — Phases 10-14 (in progress)

## Phases

<details>
<summary>✅ v1.0 Auth Service + API Gateway (Phases 1.1-1.4) — SHIPPED 2026-03-30</summary>

- [x] Phase 1.1: Auth Service Core — JWT + Login (1/1 plan) — completed 2026-03-28
- [x] Phase 1.2: OTP Flow + Change Password (1/1 plan) — completed 2026-03-29
- [x] Phase 1.3: API Gateway JWT Filter + Routing (1/1 plan) — completed 2026-03-30
- [x] Phase 1.4: Seed Data + Integration Testing (1/1 plan) — completed 2026-03-30

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

<details>
<summary>✅ v2.0 Academic Service (Phases 5-9) — SHIPPED 2026-03-31</summary>

- [x] Phase 5: Entity and Repository Foundation (2/2 plans) — completed 2026-03-30
- [x] Phase 6: REST API + HATEOAS (4/4 plans) — completed 2026-03-30
- [x] Phase 7: gRPC Server (2/2 plans) — completed 2026-03-30
- [x] Phase 8: Redis Caching (2/2 plans) — completed 2026-03-31
- [x] Phase 9: RabbitMQ Events (2/2 plans) — completed 2026-03-31

Full details: `.planning/milestones/v2.0-ROADMAP.md`

</details>

### 🚧 v3.0 Schedule Service

**Milestone Goal:** Full lesson lifecycle: schedule templates created by headmen → lessons auto-generated for every semester date → statuses transition automatically via cron (planned→active→closed) → RabbitMQ events published → gRPC server ready for Attendance Service.

- [ ] **Phase 10: Foundation** - Entities, repositories, security infrastructure, timezone config, gRPC port, Testcontainers base
- [ ] **Phase 11: REST API + gRPC Client** - Schedule template CRUD, lesson operations, schedule viewing, Academic Service validation
- [ ] **Phase 12: Lesson Generation** - Week parity algorithm, semester boundary cap, idempotent batch generation
- [ ] **Phase 13: Events + Cron** - RabbitMQ event infrastructure, AFTER_COMMIT wiring, status transition scheduler
- [ ] **Phase 14: gRPC Server** - Serve Attendance Service: GetActiveLesson, GetLessonById, GetLessonsByGroup

## Phase Details

### Phase 10: Foundation
**Goal**: Schedule Service compiles, connects to its database, enforces role-based security, and has the correct timezone and gRPC port configuration — all before any domain logic is written.
**Depends on**: Nothing (first v3.0 phase; schema already exists in V1 migration)
**Requirements**: LSSN-03, CRON-04
**Success Criteria** (what must be TRUE):
  1. `ScheduleItem` and `Lesson` JPA entities load without Hibernate validation errors against the live schedule_db schema, including correct mapping of TIME columns and PostgreSQL custom enums (week_type, lesson_status)
  2. The application starts with `grpc.server.port: 19092` — no port conflict with Auth Service (9090) or Academic Service (19091) — verified by checking the actuator or log output
  3. A Testcontainers integration test spins up a real PostgreSQL 16 container and the abstract base class is in place so all future phases inherit it without repetition
  4. The `TZ=Europe/Moscow` environment variable is set in docker-compose.yml for schedule-service and `spring.jpa.properties.hibernate.jdbc.time_zone=Europe/Moscow` is in application.yml; a Clock bean is wired so tests can inject `Clock.fixed(...)` for deterministic time assertions
  5. Security infrastructure (UserContextFilter, RequestContext, @RequireRole, RoleCheckAspect) is present and a smoke test confirms that a request without role headers receives 403
**Plans**: TBD

### Phase 11: REST API + gRPC Client
**Goal**: Headmen can fully manage schedule templates and individual lessons via REST, and any authenticated user can view the group schedule — with all inputs validated against Academic Service via gRPC before persisting.
**Depends on**: Phase 10
**Requirements**: TMPL-01, TMPL-02, TMPL-03, TMPL-04, TMPL-05, LSSN-04, LSSN-05, LSSN-06, LSSN-07, VIEW-01, VIEW-02
**Success Criteria** (what must be TRUE):
  1. Headman can POST a schedule template with subject, teacher, room, day-of-week, start/end time, and week parity; the system calls Academic Service gRPC to validate the subject and teacher before persisting — an invalid subject ID returns 422, not 500
  2. Headman can cancel a single lesson with a reason, restore a cancelled lesson, and mass-cancel lessons for a date range; a student calling the same cancel endpoint receives 403
  3. Headman can toggle `is_geo_blocked` on any specific lesson; the change is immediately reflected in the schedule view response
  4. Any authenticated user can GET the group schedule for a date range; the response includes lesson status, room, teacher name, and subject name for each lesson
  5. Academic Service gRPC client calls always carry `.withDeadlineAfter(3s)`; if Academic Service is unreachable the REST endpoint returns HTTP 503, not 500 or a hanging request
**Plans**: TBD
**UI hint**: yes

### Phase 12: Lesson Generation
**Goal**: When a schedule template is created, all lesson rows for the entire semester are generated automatically with correct week parity — and calling generation a second time is safe and produces no errors or duplicates.
**Depends on**: Phase 11
**Requirements**: LSSN-01, LSSN-02
**Success Criteria** (what must be TRUE):
  1. After creating a schedule template with week parity ODD, only dates falling on odd academic weeks (relative to semester start, not ISO week number) have lesson rows; EVEN parity produces the complementary set; ALL parity produces both
  2. After creating the same schedule template twice (or retrying a failed request), the total number of lesson rows in the database is identical to a single creation — no duplicate rows, no exception thrown to the caller
  3. Lessons are not generated past the semester's end date — a semester boundary cap is enforced regardless of the date range passed in
  4. `LessonDateUtils.isOddWeek()` is covered by unit tests with hand-calculated semester dates confirming correct parity for weeks 1 through at least 6
**Plans**: TBD

### Phase 13: Events + Cron
**Goal**: Lesson statuses transition automatically every minute and every qualifying transition publishes a RabbitMQ event that actually arrives in the exchange — with no silent event drops, no stale notifications after restart, and no events for cancelled lessons.
**Depends on**: Phase 12
**Requirements**: CRON-01, CRON-02, CRON-03, EVNT-01, EVNT-02, EVNT-03, EVNT-04
**Success Criteria** (what must be TRUE):
  1. A lesson with `start_time` equal to or earlier than `now()` (Moscow TZ) transitions from PLANNED to ACTIVE within one cron cycle (≤ 60 seconds); a `lesson.started` event is received by a RabbitMQ Testcontainers consumer in the integration test — not just that `publishEvent()` was called
  2. A lesson transitions from ACTIVE to CLOSED within one cron cycle after `end_time + 5 minutes`; a `lesson.closed` event is received by the RabbitMQ consumer
  3. A lesson that was cancelled before the cron runs does NOT receive a `lesson.started` event — the row-count guard prevents publishing on no-op updates
  4. When the service restarts and finds lessons whose transition window has already passed (missed transitions), the next cron run catches up and transitions them immediately — no lesson is stuck in PLANNED or ACTIVE after a restart
  5. All domain events (`lesson.started`, `lesson.closed`, `lesson.cancelled`) use `@TransactionalEventListener(AFTER_COMMIT)` — a transaction rollback produces no event (verified by forcing a rollback in an integration test and asserting the RabbitMQ queue remains empty)
**Plans**: TBD

### Phase 14: gRPC Server
**Goal**: Attendance Service can call all three Schedule gRPC RPCs and receive correct responses — with proper NOT_FOUND semantics and no dependency on the REST request context.
**Depends on**: Phase 13
**Requirements**: GRPC-01, GRPC-02, GRPC-03
**Success Criteria** (what must be TRUE):
  1. `GetActiveLesson` called for a group that has an ACTIVE lesson returns the lesson details; called when no lesson is active it returns `NOT_FOUND` gRPC status — verified by an in-process gRPC integration test with a seeded lesson in ACTIVE state
  2. `GetLessonById` with a valid lesson ID returns full lesson details; with an unknown ID returns `NOT_FOUND` status
  3. `GetLessonsByGroup` with a group ID and date range returns all (non-cancelled, non-archived) lessons in that window, in date order
  4. All three RPCs are covered by Testcontainers integration tests using the in-process gRPC server pattern (same approach as `AcademicGrpcServiceImpl` tests) — no RequestContext or role injection is needed or present in the gRPC service impl
**Plans**: TBD

## Progress

**Execution Order:** 10 → 11 → 12 → 13 → 14

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1.1 Auth Service Core | v1.0 | 1/1 | Complete | 2026-03-28 |
| 1.2 OTP + Change Password | v1.0 | 1/1 | Complete | 2026-03-29 |
| 1.3 Gateway JWT Filter | v1.0 | 1/1 | Complete | 2026-03-30 |
| 1.4 Seed Data + Integration Tests | v1.0 | 1/1 | Complete | 2026-03-30 |
| 5. Entity and Repository Foundation | v2.0 | 2/2 | Complete | 2026-03-30 |
| 6. REST API + HATEOAS | v2.0 | 4/4 | Complete | 2026-03-30 |
| 7. gRPC Server | v2.0 | 2/2 | Complete | 2026-03-30 |
| 8. Redis Caching | v2.0 | 2/2 | Complete | 2026-03-31 |
| 9. RabbitMQ Events | v2.0 | 2/2 | Complete | 2026-03-31 |
| 10. Foundation | v3.0 | 0/? | Not started | - |
| 11. REST API + gRPC Client | v3.0 | 0/? | Not started | - |
| 12. Lesson Generation | v3.0 | 0/? | Not started | - |
| 13. Events + Cron | v3.0 | 0/? | Not started | - |
| 14. gRPC Server | v3.0 | 0/? | Not started | - |
