# Roadmap — RutCampusTrack

## Milestones

- ✅ **v1.0 Auth Service + API Gateway** — Phases 1.1-1.4 (shipped 2026-03-30)
- ✅ **v2.0 Academic Service** — Phases 5-9 (shipped 2026-03-31)
- ✅ **v3.0 Schedule Service** — Phases 10-14 (shipped 2026-04-04)
- 🚧 **v4.0 Attendance Service MVP** — Phases 15-18 (in progress)

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

<details>
<summary>✅ v3.0 Schedule Service (Phases 10-14) — SHIPPED 2026-04-04</summary>

- [x] Phase 10: Foundation (2/2 plans) — completed 2026-04-01
- [x] Phase 11: REST API + gRPC Client (3/3 plans) — completed 2026-04-01
- [x] Phase 12: Lesson Auto-Generation (2/2 plans) — completed 2026-04-01
- [x] Phase 13: Status Transitions + RabbitMQ Events (2/2 plans) — completed 2026-04-03
- [x] Phase 14: gRPC Server (2/2 plans) — completed 2026-04-04

Full details: `.planning/milestones/v3.0-ROADMAP.md`

</details>

### 🚧 v4.0 Attendance Service MVP (In Progress)

**Milestone Goal:** Core attendance tracking — students check in via geo, headman marks manually, system auto-absents on lesson close, basic reports for journal and stats.

- [x] **Phase 15: Infrastructure Foundation** - MongoDB indexes, enum converters, gRPC clients, RabbitMQ consumer queue (completed 2026-04-04)
  Plans:
  - [x] 15-01-PLAN.md — Build config, contract additions, MongoDB document + indexes + enum converters
  - [x] 15-02-PLAN.md — gRPC clients, RabbitMQ consumer + DLQ, security AOP, error handling, tests
- [x] **Phase 16: Event Consumers** - Auto-absent on lesson.closed, cancellation propagation, DLQ (completed 2026-04-04)
  Plans:
  - [x] 16-01-PLAN.md — LessonEventService business logic + EventConsumer wiring
  - [x] 16-02-PLAN.md — Integration tests + unit tests for event consumers
- [ ] **Phase 17: Write Path — Geo-Checkin + Manual Marking** - Haversine geofence, Redis dedup/rate-limit, manual marking, attendance.marked event
- [ ] **Phase 18: Read Path — Reports** - Lesson attendance view, journal grid, student stats, domain isolation

## Phase Details

### Phase 15: Infrastructure Foundation
**Goal**: Attendance Service starts up fully connected — MongoDB indexes created, enums serialized correctly, gRPC stubs wired to Schedule and Academic services, durable RabbitMQ consumer queue bound to the fanout exchange
**Depends on**: Phase 14
**Requirements**: INFRA-01, INFRA-02, INFRA-03, INFRA-04, INFRA-05
**Success Criteria** (what must be TRUE):
  1. Service starts and a unique compound index on {lesson_id, user_id} exists in MongoDB — inserting two identical documents throws DuplicateKeyException
  2. An AttendanceStatus enum value written to MongoDB is stored as a lowercase string (e.g., "present"), not "PRESENT"
  3. A gRPC call to Schedule Service (GetActiveLesson) and Academic Service (GetCampusGeofence) completes without error when both services are running
  4. A message published to the rut-uit.events fanout exchange is received by the Attendance Service consumer queue (durable, survives restart)
  5. @RequireRole(STUDENT) on a controller method rejects a request with X-User-Role: TEACHER with 403
**Plans**: 2 plans
**UI hint**: no

### Phase 16: Event Consumers
**Goal**: Attendance Service reacts correctly to lesson lifecycle events — auto-absents all unmarked students on lesson.closed without overwriting existing checkins, updates all attendance docs to cancelled on lesson.cancelled, with DLQ protecting against silent event loss
**Depends on**: Phase 15
**Requirements**: MARK-03, MARK-04, MARK-05
**Success Criteria** (what must be TRUE):
  1. When a lesson.closed event arrives, students with no existing attendance record receive status=absent in MongoDB
  2. A student who checked in before lesson.closed fires keeps their original status — auto-absent does not overwrite it ($setOnInsert semantics)
  3. When a lesson.cancelled event arrives, all existing attendance documents for that lesson are updated to status=cancelled
  4. A lesson.closed event that fails processing is routed to the DLQ instead of being silently dropped
**Plans**: 2 plans

### Phase 17: Write Path — Geo-Checkin + Manual Marking
**Goal**: Students can geo-check in for active lessons and headmen can manually set attendance status per student, with all write-path protections (geofence, time window, geo-block, Redis dedup, Redis rate limit) enforced and attendance.marked events published
**Depends on**: Phase 16
**Requirements**: CHKN-01, CHKN-02, CHKN-03, CHKN-04, CHKN-05, CHKN-06, CHKN-07, MARK-01, MARK-02, INFRA-06
**Success Criteria** (what must be TRUE):
  1. A student inside the campus geofence during an active lesson can check in and receives status=present in MongoDB
  2. A student outside the campus geofence (Haversine distance exceeds radius) receives a 422 rejection — no document is written
  3. A second identical checkin request within the same lesson returns 409 (MongoDB unique index) and the Redis dedup lock returns 409 within the 5-second TTL window
  4. A checkin attempt when the lesson has is_geo_blocked=true returns a 403 rejection
  5. A headman setting attendance status for a student in their group succeeds (autosave); the same headman cannot mark a student outside their group
  6. After a successful checkin or manual mark, an attendance.marked event is published to the RabbitMQ fanout exchange
**Plans**: TBD
**UI hint**: no

### Phase 18: Read Path — Reports
**Goal**: Headmen and teachers can view lesson attendance and the full journal grid, students can view their own attendance stats and record list, and the report domain never imports directly from the checkin domain
**Depends on**: Phase 17
**Requirements**: RPRT-01, RPRT-02, RPRT-03, RPRT-04, RPRT-05
**Success Criteria** (what must be TRUE):
  1. A headman can retrieve the attendance list for a lesson — all group members appear, each with their attendance status (or absent if no record exists)
  2. A headman or teacher can retrieve the journal grid for a group+subject — rows are students, columns are lesson dates, cells show status symbols
  3. A student can retrieve their own attendance stats per subject — percentage attended is correct and cancelled lessons are excluded from the denominator
  4. A student can retrieve their own raw attendance list, filterable by subject, showing individual lesson records
  5. An ArchUnit test asserts that no class in the report/ package imports any class from the checkin/ package directly — AttendanceReadPort is the only bridge
**Plans**: TBD

## Progress

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
| 10. Foundation | v3.0 | 2/2 | Complete | 2026-04-01 |
| 11. REST API + gRPC Client | v3.0 | 3/3 | Complete | 2026-04-01 |
| 12. Lesson Auto-Generation | v3.0 | 2/2 | Complete | 2026-04-01 |
| 13. Status Transitions + RabbitMQ Events | v3.0 | 2/2 | Complete | 2026-04-03 |
| 14. gRPC Server | v3.0 | 2/2 | Complete | 2026-04-04 |
| 15. Infrastructure Foundation | v4.0 | 2/2 | Complete    | 2026-04-04 |
| 16. Event Consumers | v4.0 | 2/2 | Complete    | 2026-04-04 |
| 17. Write Path — Geo-Checkin + Manual Marking | v4.0 | 0/? | Not started | - |
| 18. Read Path — Reports | v4.0 | 0/? | Not started | - |
