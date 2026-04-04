# Phase 17: Write Path — Geo-Checkin + Manual Marking - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Students can geo-check in for active lessons via POST /api/attendance/checkin with {lat, lng}. Headmen can manually set attendance status per student via PUT /api/attendance/lessons/{lessonId}/students/{userId}. All write-path protections enforced: Haversine geofence, time window (5 min before -> lesson end + 5 min), geo-block flag, Redis dedup (5s TTL), Redis rate limit (3 req/min). Both paths publish attendance.marked events to RabbitMQ fanout exchange.

No read path, no reports, no excuse tickets, no headman assistants.

</domain>

<decisions>
## Implementation Decisions

### Geo-Checkin API
- **D-01:** Endpoint: `POST /api/attendance/checkin` with body `CheckinRequest(double lat, double lng)`. User ID and group ID from `RequestContext` (gateway headers). Service resolves active lesson via `scheduleGrpcClient.getActiveLesson(groupId, now)`.
- **D-02:** Time window validated server-side from `LessonResponse` start_time/end_time with +/-5 min buffer against server clock. Single source of truth.
- **D-03:** Response: 201 Created with `EntityModel<CheckinResponse>`. CheckinResponse is compact: `status`, `lessonId`, `timestamp` only. `_links`: self + lesson. Does NOT expose full AttendanceDocument shape — that comes later with `GET /api/attendance/checkins/{id}`.
- **D-04:** Error responses: RFC 7807 Problem Details via existing `ErrorResponse` record + `GlobalExceptionHandler`. HTTP codes: 422 outside geofence, 404 no active lesson, 403 geo-blocked, 409 duplicate (MongoDB unique index + Redis dedup), 429 rate limit exceeded.
- **D-05:** 409 for duplicate checkin (not 200 OK). MongoDB unique index `{lesson_id, user_id}` is the authoritative idempotency guard; Redis dedup is a fast-path optimization.
- **D-06:** `CheckinRequest` record in `attendance-api-contract`. `CheckinResponse` record in `attendance-api-contract`. `CheckinApi` interface in contract with `@PostMapping` and `@Operation` annotations.

### Geofence Architecture
- **D-07:** Three-layer design: `AcademicGrpcClient` (transport only) -> `GeofenceService` (orchestration + cache) -> `GeoUtils` (pure math).
- **D-08:** `GeofenceService` (`attendance/geofence/`) — `boolean isWithinCampus(double lat, double lng)`, `GeofenceResponse getCurrentGeofence()`. In-memory volatile cache with TTL (10-60 min). Same pattern as `SemesterCacheService` for rarely-changing data.
- **D-09:** `GeoUtils` (`attendance/geofence/`) — package-private static utility. `distanceMeters(lat1, lng1, lat2, lng2)` using Haversine formula, `isWithinRadius(...)`. No Spring, no network. No external geo libraries — pure math sufficient for single radius check.
- **D-10:** No Haversine logic in `AcademicGrpcClient` or controllers. Clean separation: gRPC client = transport, GeofenceService = business decision, GeoUtils = math.

### Manual Marking Flow
- **D-11:** Endpoint: `PUT /api/attendance/lessons/{lessonId}/students/{userId}` with body `{status}`. Autosave per click (MARK-02). Response: 200 OK with `EntityModel<MarkResponse>` (compact: status, lessonId, userId, timestamp + _links).
- **D-12:** Authorization via `RequestContext.isHeadman()` + `context.groupId` match against lesson's groupId. No extra gRPC call for headman check.
- **D-13:** Student membership validation via `academicGrpcClient.getGroupMembers(groupId)` — verify target userId exists in headman's group. Prevents marking students outside own group (MARK-01).
- **D-14:** Allowed statuses: PRESENT, ABSENT, EXCUSED, FREE_ATTENDANCE. CANCELLED is system-only (via lesson.cancelled event). Reject invalid status with 400.
- **D-15:** Upsert semantics — if no attendance document exists for (lessonId, userId), create one. Headman can mark students before lesson.closed fires. `source=HEADMAN`, `marked_by=headmanUserId`.
- **D-16:** Headman assistants deferred to v4.1+. Only `is_headman=true` check in this phase.

### Redis Dedup & Rate Limit
- **D-17:** Separate Redis keys: dedup = `attendance:dedup:{lessonId}:{userId}` (TTL 5s), rate limit = `attendance:rate:{userId}` (TTL 60s, INCR up to 3).
- **D-18:** `CheckinRateLimiter` @Service with `acquireDedup(lessonId, userId)` and `checkRateLimit(userId)`. Isolates Redis logic from business code.
- **D-19:** Rate limit and dedup apply ONLY to geo-checkin (`POST /checkin`), NOT to manual marking. Headman marks many students in rapid succession — rate limit would interfere.

### Event Publishing
- **D-20:** Both checkin and manual mark publish `attendance.marked` event to `rut-uit.events` fanout exchange (INFRA-06). Payload per `event-schemas/attendance.marked.json`: `{lesson_id, user_id, group_id, status, marked_by}`.
- **D-21:** Synchronous publishing — `RabbitTemplate.convertAndSend()` in the same thread after MongoDB upsert. If Rabbit unavailable, client gets error (fail-loud, not fire-and-forget).
- **D-22:** `AttendanceEventPublisher` @Service with `publishMarked(AttendanceDocument doc)`. Encapsulates envelope creation and RabbitTemplate. Both CheckinService and MarkingService call it.

### Claude's Discretion
- Exact CheckinService / MarkingService internal structure and method decomposition
- Whether to use MongoTemplate or AttendanceRepository for upsert operations
- Test structure: how many integration tests, which scenarios to prioritize
- Redis configuration bean structure
- Exact TTL value for GeofenceService cache (anywhere in 10-60 min range)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Event Schemas
- `event-schemas/attendance.marked.json` -- Payload: `{lesson_id, user_id, group_id, status, marked_by}`

### Proto Contracts (gRPC calls needed)
- `proto/schedule.proto` -- `ActiveLessonRequest(group_id, timestamp)` -> `LessonResponse` (id, group_id, subject_id, date, lesson_number, start_time, end_time, status, is_geo_blocked, room)
- `proto/academic.proto` -- `GetCampusGeofence()` -> `GeofenceResponse(lat, lng, radius_m)`, `GetGroupMembers(group_id)` -> `GroupMembersResponse`, `IsHeadman(user_id, group_id)` -> `HeadmanCheckResponse`

### Existing Code (Phase 15-16 output)
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/ScheduleGrpcClient.java` -- getActiveLesson, getLessonById already wired
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClient.java` -- getCampusGeofence, getGroupMembers, isHeadman already wired
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/RequestContext.java` -- userId, role, groupId, isHeadman from gateway headers
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceDocument.java` -- MongoDB document with all fields
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceRepository.java` -- MongoRepository
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/RabbitConfig.java` -- Fanout exchange + DLQ already declared
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/semester/SemesterCacheService.java` -- Pattern reference for GeofenceService cache design
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/AttendanceStatus.java` -- PRESENT, ABSENT, EXCUSED, FREE_ATTENDANCE, CANCELLED
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/AttendanceSource.java` -- STUDENT_GEO, HEADMAN, AUTO_SCHEDULER, LATE_CHECKIN

### Architecture & Conventions
- `docs/architecture.md` -- Service communication patterns, ports
- `docs/database-schema.md` -- MongoDB attendance_db schema
- `docs/phases-plan.md` -- Phase plan and dependencies
- `CLAUDE.md` -- Coding rules: contract-first, HATEOAS Level 3, enum conventions, REST patterns

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RequestContext` — request-scoped bean with userId, groupId, role, isHeadman from gateway headers. Ready to use in both checkin and marking flows
- `ScheduleGrpcClient.getActiveLesson(groupId, timestamp)` — returns LessonResponse with is_geo_blocked, start_time, end_time
- `AcademicGrpcClient.getCampusGeofence()` — returns GeofenceResponse(lat, lng, radius_m)
- `AcademicGrpcClient.getGroupMembers(groupId)` — for student membership validation in manual marking
- `SemesterCacheService` — pattern reference for in-memory cache with refresh (GeofenceService follows same design)
- `RabbitConfig` — fanout exchange `rut-uit.events` and DLQ already configured
- `AttendanceDocument` with @Builder — for creating new documents in upsert operations
- `GlobalExceptionHandler` — RFC 7807 already set up with DuplicateKeyException -> 409 mapping

### Established Patterns
- Contract-first: interface in `*-api-contract`, controller implements it. Request DTO = record, Response DTO = class for HATEOAS
- gRPC clients with 3-second deadline, StatusRuntimeException -> domain exception translation
- @RequireRole AOP for role-based access control
- Testcontainers for MongoDB + RabbitMQ in integration tests
- @MockitoBean for gRPC clients in integration tests

### Integration Points
- New `checkin/` package: CheckinController, CheckinService, CheckinRateLimiter
- New `geofence/` package: GeofenceService, GeoUtils
- New event publisher: AttendanceEventPublisher (shared between checkin and marking)
- API contract additions: CheckinRequest, CheckinResponse, MarkRequest, MarkResponse, CheckinApi, MarkingApi interfaces
- Redis dependency: spring-boot-starter-data-redis in build.gradle.kts (Testcontainers Redis for tests)

</code_context>

<specifics>
## Specific Ideas

- GeofenceService follows exactly the SemesterCacheService pattern: volatile in-memory cache, lazy load on first call, TTL-based refresh
- GeoUtils is package-private — only GeofenceService uses it, no need for public API
- CheckinResponse deliberately compact (status, lessonId, timestamp) — full AttendanceResponse comes later via GET endpoint
- Redis dedup is optimization layer; MongoDB unique index is the authoritative guard

</specifics>

<deferred>
## Deferred Ideas

- **Headman assistants** (JS-HEADMAN-14) — v4.1+, requires headman_assistants table in Academic Service
- **Late checkin flow** (JS-STUDENT-06, JS-HEADMAN-05) — separate phase, needs approval workflow
- **Excuse tickets** (JS-STUDENT-03, JS-STUDENT-04) — v4.1+, own phase
- **GET /api/attendance/checkins/{id}** — full AttendanceResponse with all document fields, deferred to Phase 18 (Read Path)

</deferred>

---

*Phase: 17-write-path-geo-checkin-manual-marking*
*Context gathered: 2026-04-04*
