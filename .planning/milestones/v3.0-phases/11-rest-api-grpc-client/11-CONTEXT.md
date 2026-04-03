# Phase 11: REST API + gRPC Client - Context

**Gathered:** 2026-04-01
**Status:** Ready for planning

<domain>
## Phase Boundary

CRUD schedule templates (HEADMAN/ADMIN), lesson operations (cancel/restore/mass-cancel/geo-block), schedule viewing (date range query with optional status filter), and gRPC client to Academic Service for FK validation (group, semester, headman authorization).

No lesson generation (Phase 12), no cron jobs (Phase 13), no gRPC server (Phase 14), no RabbitMQ events (Phase 13).

</domain>

<decisions>
## Implementation Decisions

### gRPC Client Setup
- **D-01:** Use `net.devh:grpc-client-spring-boot-starter` for the Academic Service gRPC client. Add `com.google.protobuf` Gradle plugin to schedule-app for compiling `academic.proto` client stubs. Phase 14 will add `schedule.proto` server stubs.
- **D-02:** Create an `AcademicGrpcClient` wrapper class that encapsulates all gRPC calls. Always use `.withDeadlineAfter(3, SECONDS)` on every call (pre-decided in STATE.md).
- **D-03:** When Academic Service is unavailable (gRPC failure), reject the operation with HTTP 503 Service Unavailable. FK integrity is non-negotiable — no orphan templates.
- **D-04:** In integration tests, `@MockitoBean` the `AcademicGrpcClient` wrapper. No embedded gRPC server needed. Matches the project pattern of mocking RabbitTemplate.
- **D-05:** RPCs needed as client: `GetGroup` (validate group_id), `GetActiveSemester` (validate semester_id), `IsHeadman` (authorize headman for specific group). `GetUserById` NOT needed — teacher_id is trusted (headman selects from known list).

### Template CRUD
- **D-06:** FK validation on create: validate `group_id` (GetGroup — exists and active) and `semester_id` (GetActiveSemester). `teacher_id` and `subject_id` are trusted — headman selects from known frontend lists.
- **D-07:** Template deletion is soft delete: set `is_active = false`. Existing generated lessons remain. Matches CLAUDE.md soft-delete convention.
- **D-08:** Headman authorization via `IsHeadman` gRPC call to verify headman belongs to the specific group. Prevents headman of Group A from modifying Group B's schedule. ADMIN bypasses this check.
- **D-09:** Template update (PUT) allows changing: teacher_id, subject_id, room, day_of_week, lesson_number, start_time, end_time, week_type. Does NOT allow changing group_id or semester_id (would invalidate generated lessons). Re-validates group+semester ownership on update.
- **D-10:** Who can CRUD templates: HEADMAN (for their own group) + ADMIN (for any group).

### Lesson Operations
- **D-11:** Cancel/restore permissions: HEADMAN (own group) + ADMIN (any group).
- **D-12:** Mass-cancel: `POST /schedule/lessons/mass-cancel` with `{group_id, date_from, date_to, reason}`. Cancels all lessons with status='planned' in the date range for the group.
- **D-13:** `cancel_reason` is REQUIRED on all cancel operations (single and mass). Provides audit trail.
- **D-14:** Restore: only `cancelled -> planned`. Clears `cancel_reason`. Active/closed lessons cannot be restored.
- **D-15:** Geo-block toggle: HEADMAN/ADMIN can set `is_geo_blocked = true/false` on a specific lesson.

### Schedule View
- **D-16:** Response contains IDs only (subject_id, teacher_id, group_id) — no name enrichment from Academic Service. Frontend resolves names separately. Keeps Schedule Service decoupled.
- **D-17:** Flat lesson list with HATEOAS: `GET /schedule/groups/{groupId}/lessons?dateFrom=...&dateTo=...` returns `PagedModel<EntityModel<LessonResponse>>`. Matches academic-service pattern.
- **D-18:** Optional status filter: `?status=planned,active,closed` to exclude cancelled by default. Frontend can include cancelled when needed (headman view).
- **D-19:** Any authenticated user can view schedule (VIEW-01). Role check: STUDENT, TEACHER, HEADMAN, ADMIN all have read access.

### Claude's Discretion
- DTO structure for CreateScheduleItemRequest, UpdateScheduleItemRequest, LessonResponse, ScheduleItemResponse
- Service layer organization (ScheduleItemService, LessonService — or combined)
- HATEOAS link structure (self, collection, cancel, restore actions)
- Error response specifics within RFC 7807 framework
- Exact endpoint paths under `/schedule/` prefix
- Whether PATCH or PUT for template updates (CLAUDE.md says PUT = full update, PATCH = partial)
- Pagination defaults and max page size for schedule view

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Database Schema
- `docs/database-schema.md` — schedule_db schema: schedule_items and lessons tables, constraints, indexes, enum types

### Architecture & Conventions
- `docs/architecture.md` — Service map, port assignments, inter-service communication
- `CLAUDE.md` — Coding rules: contract-first, enum handling, HATEOAS Level 3, RFC 7807, naming conventions, PUT vs PATCH

### Proto Contracts
- `proto/academic.proto` — Academic Service gRPC contract (GetGroup, GetActiveSemester, IsHeadman RPCs needed as client)
- `proto/schedule.proto` — Schedule Service gRPC contract (Phase 14 — server side, but read for LessonResponse message shape)

### Existing Patterns (reference implementations)
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/SubjectApi.java` — Contract-first API interface pattern with HATEOAS + Swagger
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectController.java` — Controller implementing contract interface
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java` — Service layer pattern
- `services/academic-service/academic-app/build.gradle.kts` — Protobuf plugin + gRPC dependency setup pattern

### Phase 10 Artifacts
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/security/` — Security infrastructure (UserContextFilter, RequireRole, RoleCheckAspect, RequestContext)
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/entity/ScheduleItem.java` — Entity to build service on
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/entity/Lesson.java` — Entity to build service on
- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/AbstractScheduleIntegrationTest.java` — Testcontainers base class for new tests

### Phase Plan
- `docs/phases-plan.md` — Original phase descriptions
- `docs/job-stories.md` — Business requirements and user stories

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Security layer (Phase 10): UserContextFilter, @RequireRole, RoleCheckAspect, RequestContext — ready for endpoint annotations
- JPA entities: ScheduleItem, Lesson with correct column mappings, repositories with finder methods
- GlobalExceptionHandler: RFC 7807 error responses already wired
- AbstractScheduleIntegrationTest: Testcontainers + PostgreSQL 16 base class
- EnumConverters: WeekType, LessonStatus converters with autoApply

### Established Patterns
- Contract-first: API interface in `schedule-api-contract`, controller implements in `schedule-app`
- HATEOAS: EntityModel<T>, PagedModel<EntityModel<T>> with PagedResourcesAssembler
- Service layer: thin controllers, business logic in @Service classes
- DTO: Request = Java record (in contract), Response = class extending RepresentationModel (for HATEOAS links)
- gRPC server pattern in academic-service: repos injected directly, not through REST service layer

### Integration Points
- `services/schedule-service/schedule-app/build.gradle.kts` — needs protobuf plugin + grpc-client-spring-boot-starter
- `services/schedule-service/schedule-app/src/main/resources/application.yml` — needs grpc.client.academic-service config
- API Gateway routing: needs `/api/schedule/**` route to schedule-service:9092
- HealthCheckController (Phase 10 placeholder): replace with actual /schedule/health or remove

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches following established patterns from academic-service.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 11-rest-api-grpc-client*
*Context gathered: 2026-04-01*
