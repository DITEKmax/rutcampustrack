# Phase 15: Infrastructure Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md -- this log preserves the alternatives considered.

**Date:** 2026-04-04
**Phase:** 15-infrastructure-foundation
**Areas discussed:** MongoDB document design, RabbitMQ consumer setup, gRPC client scope, Testing strategy, MongoDB collection naming, Error handling pattern, Configuration & profiles

---

## MongoDB Document Design - Denormalization

| Option | Description | Selected |
|--------|-------------|----------|
| Full denormalization | Store group_id, subject_id, semester_id, lesson_number, date directly on each attendance doc. Report queries are pure MongoDB aggregations with no gRPC calls at read time. | ✓ |
| Minimal + gRPC at read | Store only lesson_id + user_id + status. Reports call gRPC to Schedule/Academic to resolve group, subject, semester. Simpler writes, slower reads. | |
| Partial denormalization | Store group_id + subject_id (available from lesson events) but NOT semester_id. Resolve semester at report time only. | |

**User's choice:** Full denormalization (Recommended)
**Notes:** Reports should be pure MongoDB aggregations without cross-service calls.

## MongoDB Document Design - semester_id Resolution

| Option | Description | Selected |
|--------|-------------|----------|
| Cache on startup + refresh | Call GetActiveSemester on service start, cache in a @Service bean, refresh on semester.archived event. Simple, one gRPC call. | ✓ |
| Call per write | Call GetActiveSemester gRPC on every checkin/auto-absent write. Guaranteed fresh but adds latency. | |
| Add to LessonResponse proto | Extend schedule.proto to include semester_id in LessonResponse. Clean but requires Schedule Service change. | |

**User's choice:** Cache on startup + refresh (Recommended)
**Notes:** Semester changes are rare (twice a year), caching is safe.

## RabbitMQ Consumer Setup - DLQ Timing

| Option | Description | Selected |
|--------|-------------|----------|
| Declare now | Declare the DLQ exchange + queue + binding in Phase 15 RabbitConfig. Phase 16 just adds the error handler. | ✓ |
| Defer to Phase 16 | Phase 15 only declares the main consumer queue. Phase 16 adds DLQ when implementing error handling. | |

**User's choice:** Declare now (Recommended)
**Notes:** Clean separation: infra in 15, logic in 16.

## RabbitMQ Consumer Setup - Message Deserialization

| Option | Description | Selected |
|--------|-------------|----------|
| Generic envelope + routing | Single @RabbitListener receives all events as a generic envelope, routes by event_type field to typed handlers. | ✓ |
| Typed message classes | Define Java classes per event type, use Jackson type info for deserialization. | |
| You decide | Claude picks based on existing codebase patterns. | |

**User's choice:** Generic envelope + routing (Recommended)
**Notes:** Matches the fanout pattern where one queue receives everything.

## gRPC Client Scope

| Option | Description | Selected |
|--------|-------------|----------|
| All needed for v4.0 | Wire all RPCs that Phases 15-18 will use. Each is a one-liner wrapper. | ✓ |
| Only success criteria RPCs | Wire only GetActiveLesson + GetCampusGeofence + GetActiveSemester. Add remaining in later phases. | |
| You decide | Claude picks based on maintainability and test coverage. | |

**User's choice:** All needed for v4.0 (Recommended)
**Notes:** Wire everything upfront to avoid repeated infra work in later phases.

## gRPC Proto for Attendance

| Option | Description | Selected |
|--------|-------------|----------|
| Not now | No attendance.proto for v4.0. Attendance Service is a consumer only. | ✓ |
| Stub proto now | Create attendance.proto with placeholder service definition. | |

**User's choice:** Not now
**Notes:** Add proto if/when another service needs attendance data via gRPC.

## Testing - gRPC Client Tests

| Option | Description | Selected |
|--------|-------------|----------|
| Mock stubs with Mockito | Mock the blocking stubs in unit tests. Real gRPC integration tests deferred to E2E. | ✓ |
| Embedded gRPC server | Spin up an in-process gRPC server with fake implementations. More realistic but more setup. | |
| You decide | Claude picks based on existing test suites. | |

**User's choice:** Mock stubs with Mockito (Recommended)
**Notes:** Matches schedule-service test patterns.

## Testing - Testcontainers Scope

| Option | Description | Selected |
|--------|-------------|----------|
| MongoDB + RabbitMQ | Testcontainers for both. MongoDB for index verification, RabbitMQ for consumer queue binding verification. | ✓ |
| MongoDB only | Testcontainers for MongoDB. Test RabbitMQ with mock ConnectionFactory. | |
| You decide | Claude picks based on test complexity and success criteria. | |

**User's choice:** MongoDB + RabbitMQ (Recommended)
**Notes:** Redis not needed until Phase 17.

## MongoDB Collection Naming

| Option | Description | Selected |
|--------|-------------|----------|
| Single 'attendances' | One collection for all attendance records. Compound indexes handle query patterns. | ✓ |
| Separate per domain | checkin/ writes to 'checkins', report/ reads from 'attendance_reports'. | |

**User's choice:** Single 'attendances' (Recommended)
**Notes:** Matches MongoDB best practice for documents with the same structure.

## MongoDB Document _id Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| ObjectId | Default MongoDB ObjectId as _id. Unique constraint on {lesson_id, user_id} as separate compound index. | ✓ |
| Composite string key | Use _id = "lesson_{lessonId}_user_{userId}". Natural dedup via _id uniqueness. | |

**User's choice:** ObjectId (Recommended)
**Notes:** Simple, standard, more flexible for future changes.

## Error Handling Pattern

| Option | Description | Selected |
|--------|-------------|----------|
| Copy + extend from academic | RFC 7807 pattern with MongoDB-specific handlers: DuplicateKeyException -> 409, gRPC StatusRuntimeException -> 502/503. | ✓ |
| Minimal for Phase 15 | Only handle DuplicateKeyException and generic 500. Add more handlers later. | |
| You decide | Claude picks based on success criteria and existing patterns. | |

**User's choice:** Copy + extend from academic (Recommended)
**Notes:** Full error handling from the start.

## Configuration & Test Profiles

| Option | Description | Selected |
|--------|-------------|----------|
| @ActiveProfiles("test") + abstract base | Same pattern as schedule-service: abstract base test class with @Testcontainers, @DynamicPropertySource. | ✓ |
| Spring Boot @ServiceConnection | Use Spring Boot 3.4's @ServiceConnection for Testcontainers auto-config. Newer pattern not used elsewhere. | |
| You decide | Claude picks based on consistency with existing test setup. | |

**User's choice:** @ActiveProfiles("test") + abstract base (Recommended)
**Notes:** Consistency with existing test infrastructure in schedule-service.

---

## Claude's Discretion

- Package structure within attendance-app
- Specific MongoDB index definitions beyond unique {lesson_id, user_id}
- RabbitMQ queue and DLQ naming conventions
- Order of bean initialization for semester cache

## Deferred Ideas

None -- discussion stayed within phase scope.
