# Phase 16: Event Consumers - Research

**Researched:** 2026-04-04
**Domain:** Spring AMQP event processing, MongoDB bulk operations, Spring Data MongoDB BulkOperations API
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Call `AcademicGrpcClient.getGroupMembers(groupId)` and `ScheduleGrpcClient.getLessonById(lessonId)` to resolve denormalization fields. `semester_id` from `SemesterCacheService`.
- **D-02:** Use MongoDB `bulkWrite` with `UpdateOneModel` per student, `upsert=true`, `$setOnInsert` for all fields. Single round-trip, atomic batch, race-safe.
- **D-03:** Auto-absent docs get `status=ABSENT`, `source=AUTO_SCHEDULER`, `marked_by=null`, `created_at=now`, `updated_at=now`.
- **D-04:** Cancellation uses `updateMany({lesson_id: X}, {$set: {status: "cancelled", updated_at: now}})`. Only updates existing docs — no inserts.
- **D-05:** Cancellation overwrites any current status including auto-absent. If `lesson.closed` fired first, `lesson.cancelled` arriving later makes all docs `cancelled`. Correct because cancelled lessons are excluded from statistics.
- **D-06:** On gRPC failure, let exception propagate. Spring AMQP nacks, RabbitMQ routes to DLQ. No retry logic.
- **D-07:** All handlers are naturally idempotent. No event_id dedup tracking needed.
- **D-08:** `lesson.started` is a no-op with debug log. Keep stub case in switch for future extensibility.
- **D-09:** `semester.archived` calls `SemesterCacheService.refresh()`. Already built in Phase 15.

### Claude's Discretion

- Whether to extract event handler logic into a separate service class or keep in EventConsumer
- MongoTemplate vs. BulkOperations API for the bulkWrite
- Exact error logging format and DLQ message enrichment
- Test structure: how many integration tests, which scenarios to cover

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MARK-03 | Auto-absent assigns status=absent to all unmarked students on lesson.closed event | D-01 + D-02: bulkWrite with upsert enables writing only to students with no record |
| MARK-04 | Auto-absent uses $setOnInsert to prevent overwriting existing checkins (race-safe) | D-02: upsert=true + $setOnInsert semantics — if doc exists, insert fields are skipped |
| MARK-05 | lesson.cancelled consumer updates existing attendance docs to status=cancelled | D-04: updateMany with $set on existing docs, no upsert |
</phase_requirements>

---

## Summary

Phase 16 fills in four stub methods in an already-wired `EventConsumer.java`. The infrastructure (queue, DLQ, Jackson converter, gRPC clients, SemesterCacheService) was all built in Phase 15. The only new logic is:

1. `handleLessonClosed` — fetch group members via gRPC, fetch lesson details via gRPC, execute a MongoDB bulkWrite with `$setOnInsert` (upsert) semantics per student.
2. `handleLessonCancelled` — execute a single MongoDB `updateMany` setting status to CANCELLED on all existing docs for the lesson.
3. `handleSemesterArchived` — one-liner: call `SemesterCacheService.refresh()`.
4. `handleLessonStarted` — already correct as no-op with debug log per D-08.

The critical design question from STATE.md ("Critical Design Decision (unresolved before Phase 16)") is already resolved by D-01: use `SemesterCacheService.getActiveSemesterId()` — the service was built in Phase 15 exactly for this purpose.

The only discretionary architectural question is whether to extract the two substantive handlers into a dedicated service class. Given the modest scope and the existing codebase pattern of keeping related logic in services, extracting to a `LessonEventService` is recommended.

**Primary recommendation:** Extract handler logic to `LessonEventService`, implement `bulkWrite` via Spring Data `BulkOperations` API (not raw `UpdateOneModel`), use `MongoTemplate.updateMulti` for cancellation.

---

## Standard Stack

### Core (already in build.gradle.kts — no new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-amqp | 3.4.x (BOM) | `@RabbitListener`, message converter | Already present; Phase 15 infrastructure |
| spring-boot-starter-data-mongodb | 3.4.x (BOM) | `MongoTemplate`, `BulkOperations` | Already present; provides bulkWrite API |
| grpc-client-spring-boot-starter | 3.1.0.RELEASE | gRPC stubs for Schedule/Academic | Already present |

No new dependencies are needed for this phase. All required libraries are already declared.

### Key APIs to Use

| API | Class | Method | Purpose |
|-----|-------|--------|---------|
| Bulk upsert | `MongoTemplate.bulkOps()` | `BulkOperations.upsert(Query, Update)` | MARK-03 + MARK-04 auto-absent |
| Multi-update | `MongoTemplate.updateMulti()` | single call | MARK-05 cancellation |
| gRPC | `AcademicGrpcClient` | `getGroupMembers(groupId)` | Student list for auto-absent |
| gRPC | `ScheduleGrpcClient` | `getLessonById(lessonId)` | Denormalization data |
| Cache | `SemesterCacheService` | `getActiveSemesterId()` | semester_id for docs |

---

## Architecture Patterns

### Recommended Class Structure

```
event/
├── EventConsumer.java          (already exists — thin router)
└── LessonEventService.java     (NEW — contains both handler implementations)
```

`EventConsumer` delegates to `LessonEventService`:
- `EventConsumer.handleLessonClosed(envelope)` -> `lessonEventService.processLessonClosed(lessonId, groupId)`
- `EventConsumer.handleLessonCancelled(envelope)` -> `lessonEventService.processLessonCancelled(lessonId)`
- `EventConsumer.handleSemesterArchived(envelope)` -> `semesterCacheService.refresh()`

This separation keeps `EventConsumer` as a routing layer (matching existing pattern with `SemesterCacheService` already injected separately) and makes `LessonEventService` independently unit-testable.

### Pattern 1: MongoDB BulkOperations with $setOnInsert (MARK-03 + MARK-04)

Spring Data `BulkOperations` maps to MongoDB driver `bulkWrite`. The `upsert(Query, Update)` method issues `UpdateOne` with `upsert=true`. Fields placed in `$setOnInsert` only apply when a new document is inserted — existing documents are untouched.

**Confidence:** HIGH — verified against Spring Data MongoDB 4.x documentation.

```java
// Source: Spring Data MongoDB docs — BulkOperations
BulkOperations bulkOps = mongoTemplate.bulkOps(
    BulkOperations.BulkMode.UNORDERED, AttendanceDocument.class);

Instant now = Instant.now();
for (StudentInfo student : members.getStudentsList()) {
    Query filter = Query.query(
        Criteria.where("lesson_id").is(lessonId)
                .and("user_id").is(student.getUserId())
    );
    Update insert = new Update()
        .setOnInsert("lesson_id",     lessonId)
        .setOnInsert("user_id",       student.getUserId())
        .setOnInsert("group_id",      groupId)
        .setOnInsert("subject_id",    lesson.getSubjectId())
        .setOnInsert("semester_id",   semesterId)
        .setOnInsert("lesson_number", lesson.getLessonNumber())
        .setOnInsert("lesson_date",   LocalDate.parse(lesson.getDate()))
        .setOnInsert("status",        AttendanceStatus.ABSENT)
        .setOnInsert("source",        AttendanceSource.AUTO_SCHEDULER)
        .setOnInsert("marked_by",     null)
        .setOnInsert("created_at",    now)
        .setOnInsert("updated_at",    now);
    bulkOps.upsert(filter, insert);
}
bulkOps.execute();
```

**Why UNORDERED:** All operations are independent (different lesson_id+user_id pairs). UNORDERED allows parallel execution and does not abort on a single duplicate-key error.

**Idempotency:** If the event is replayed from DLQ, existing absent docs already have their status — `$setOnInsert` is a no-op for them. Students who checked in keep their status (D-07 confirmed).

### Pattern 2: updateMany for Cancellation (MARK-05)

Single `updateMulti` call — no upsert, only existing records updated.

```java
// Source: Spring Data MongoDB docs — MongoTemplate.updateMulti
Query filter = Query.query(Criteria.where("lesson_id").is(lessonId));
Update update = new Update()
    .set("status",     AttendanceStatus.CANCELLED)
    .set("updated_at", Instant.now());
mongoTemplate.updateMulti(filter, update, AttendanceDocument.class);
```

**If no documents exist** for that lesson: `updateMulti` affects 0 documents — no error, no inserts. Correct per D-04.

**Idempotency:** Re-running sets status to CANCELLED again — idempotent by definition of `$set`.

### Pattern 3: Envelope Payload Extraction

Events arrive as `Map<String, Object>`. The `payload` field is a nested `Map<String, Object>`. Integer values from JSON arrive as `Integer` (not `Long`) due to Jackson default type mapping, so casting requires widening.

```java
// Extract payload map from envelope
@SuppressWarnings("unchecked")
Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");

// JSON integers arrive as Integer — must widen to Long
Long lessonId = ((Number) payload.get("lesson_id")).longValue();
Long groupId  = ((Number) payload.get("group_id")).longValue();
```

**Confidence:** HIGH — verified by observing Jackson2JsonMessageConverter behavior with `Map<String, Object>` target type. JSON numbers without decimals deserialize to `Integer` when target is `Object`. Use `((Number) value).longValue()` as the safe extraction pattern.

### Pattern 4: Error Propagation to DLQ (per D-06)

No try/catch in handler methods. Any `StatusRuntimeException` from gRPC clients becomes an `AcademicServiceUnavailableException` / `ScheduleServiceUnavailableException` (unchecked). Spring AMQP's default error handler nacks the message, RabbitMQ routes it to the DLQ via the `x-dead-letter-exchange` / `x-dead-letter-routing-key` arguments already set on `attendance-service.events` in `RabbitConfig.java`.

```java
// CORRECT — let exceptions propagate
private void handleLessonClosed(Map<String, Object> envelope) {
    Map<String, Object> payload = extractPayload(envelope);
    Long lessonId = ((Number) payload.get("lesson_id")).longValue();
    Long groupId  = ((Number) payload.get("group_id")).longValue();
    lessonEventService.processLessonClosed(lessonId, groupId);
    // If processLessonClosed throws, Spring AMQP nacks and DLQ receives the message
}
```

### Anti-Patterns to Avoid

- **Using `@Transactional` on RabbitMQ listeners:** MongoDB + RabbitMQ do not share a transaction manager. The annotation has no effect and may mislead readers.
- **Using `BulkMode.ORDERED` for auto-absent:** Ordered mode stops on first error. A single duplicate-key edge case would abort the entire batch. Use UNORDERED.
- **Casting payload integers directly to `Long`:** JSON numbers arrive as `Integer`. `(Long) payload.get("lesson_id")` throws `ClassCastException`. Always use `((Number) value).longValue()`.
- **Calling `getLessonById` before confirming group_id in payload:** The `lesson.closed` event payload includes `group_id` — use it directly. No need to call gRPC just to get the group_id. Call gRPC only for fields NOT in the event payload (lesson_number, lesson_date, subject_id).
- **Blocking on `SemesterCacheService` if null:** If startup failed to load semester, `getActiveSemesterId()` returns `null`. Auto-absent doc will have `null` semester_id. Acceptable per D-01 (cache-based, best-effort), but log a warning when null is detected.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Bulk upsert with $setOnInsert | Custom loop with `save()` per student | `MongoTemplate.bulkOps().upsert()` | Single round-trip, race-safe, UNORDERED handles partial failures |
| Multi-document status update | Loop with `findById` + `save()` | `MongoTemplate.updateMulti()` | Single MongoDB operation, consistent, no N+1 |
| Retry logic for failed events | Manual retry counter in handler | DLQ infrastructure (already built) | DLQ provides reliable replay with manual intervention |
| Event deduplication | Store processed event_id set | `$setOnInsert` idempotency | Document-level idempotency eliminates need for separate dedup store |

**Key insight:** MongoDB's `$setOnInsert` semantics are the correct primitive for race-safe upsert in event-driven systems. Using `save()` would require read-modify-write cycles that create TOCTOU races.

---

## Common Pitfalls

### Pitfall 1: Integer vs Long from Jackson Deserialization
**What goes wrong:** `ClassCastException: Integer cannot be cast to Long` when extracting `lesson_id` / `group_id` / `user_id` from the payload map.
**Why it happens:** `Jackson2JsonMessageConverter` with `Map<String, Object>` target type: JSON number without decimal point -> Java `Integer` (if fits in int range) or `Long`. For IDs like `lesson_id=42`, it's an `Integer`.
**How to avoid:** Always use `((Number) payload.get("field_name")).longValue()` for all numeric ID fields extracted from the envelope.
**Warning signs:** `ClassCastException` in RabbitMQ consumer listener logs; messages routed to DLQ on first publish.

### Pitfall 2: GetLessonsByGroup returns cancelled lessons (from STATE.md Known Tech Debt)
**What goes wrong:** Auto-absent processes lessons with status `cancelled` as if they were closed.
**Why it happens:** Known tech debt in Schedule Service — `GetLessonsByGroup` does not filter by status server-side.
**How to avoid:** This phase does NOT call `getLessonsByGroup` — it calls `getLessonById` in response to a `lesson.closed` event. The event itself is only emitted when a lesson transitions to CLOSED status. No client-side filtering needed for Phase 16's use case.
**Warning signs:** Only relevant in Phase 18 (reports) when calling `getLessonsByGroup`. Not a concern here.

### Pitfall 3: SemesterCacheService returns null at startup
**What goes wrong:** Auto-absent documents written with `semester_id=null`, breaking report queries in Phase 18.
**Why it happens:** If Academic Service is unavailable at Attendance Service startup, `SemesterCacheService.init()` catches the exception and leaves `activeSemesterId=null`.
**How to avoid:** Log a warning in `LessonEventService` when `semesterCacheService.getActiveSemesterId()` returns null. Accept the null — it's a design trade-off per D-01. Report queries will need to handle null semester_id gracefully (Phase 18 concern).
**Warning signs:** `activeSemesterId=null` in logs during auto-absent processing.

### Pitfall 4: Empty student list from getGroupMembers
**What goes wrong:** `bulkOps.execute()` called with zero operations, which Spring Data may reject or produce an empty result depending on version.
**Why it happens:** Group genuinely has no members, or group is not found.
**How to avoid:** Guard with an early return if `members.getStudentsList().isEmpty()`. Log at INFO level.
**Warning signs:** Exception from `bulkOps.execute()` with message about empty bulk operations.

### Pitfall 5: BulkOperations bean scope
**What goes wrong:** `BulkOperations` instance reused across multiple invocations accumulates operations from previous calls.
**Why it happens:** `mongoTemplate.bulkOps()` creates a new stateful instance each call. If the reference is cached (e.g., as a field), previous un-executed operations pile up.
**How to avoid:** Always call `mongoTemplate.bulkOps()` fresh at the start of each handler invocation. Never store `BulkOperations` as a field.

---

## Code Examples

### Full handleLessonClosed flow

```java
// Source: Spring Data MongoDB BulkOperations + design decisions D-01/D-02/D-03
public void processLessonClosed(Long lessonId, Long groupId) {
    LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
    GroupMembersResponse members = academicGrpcClient.getGroupMembers(groupId);

    if (members.getStudentsList().isEmpty()) {
        log.info("lesson.closed: no students in group {} for lesson {}, skipping", groupId, lessonId);
        return;
    }

    Long semesterId = semesterCacheService.getActiveSemesterId();
    if (semesterId == null) {
        log.warn("lesson.closed: semesterId is null (cache miss at startup) for lesson {}", lessonId);
    }

    Instant now = Instant.now();
    BulkOperations bulkOps = mongoTemplate.bulkOps(
        BulkOperations.BulkMode.UNORDERED, AttendanceDocument.class);

    for (StudentInfo student : members.getStudentsList()) {
        Query filter = Query.query(
            Criteria.where("lesson_id").is(lessonId)
                    .and("user_id").is(student.getUserId()));
        Update insert = new Update()
            .setOnInsert("lesson_id",     lessonId)
            .setOnInsert("user_id",       student.getUserId())
            .setOnInsert("group_id",      groupId)
            .setOnInsert("subject_id",    lesson.getSubjectId())
            .setOnInsert("semester_id",   semesterId)
            .setOnInsert("lesson_number", lesson.getLessonNumber())
            .setOnInsert("lesson_date",   LocalDate.parse(lesson.getDate()))
            .setOnInsert("status",        AttendanceStatus.ABSENT)
            .setOnInsert("source",        AttendanceSource.AUTO_SCHEDULER)
            .setOnInsert("marked_by",     null)
            .setOnInsert("created_at",    now)
            .setOnInsert("updated_at",    now);
        bulkOps.upsert(filter, insert);
    }

    bulkOps.execute();
    log.info("lesson.closed: lessonId={}, processed {} students for auto-absent", lessonId,
        members.getStudentsCount());
}
```

### Full handleLessonCancelled flow

```java
// Source: Spring Data MongoDB — MongoTemplate.updateMulti
public void processLessonCancelled(Long lessonId) {
    Query filter = Query.query(Criteria.where("lesson_id").is(lessonId));
    Update update = new Update()
        .set("status",     AttendanceStatus.CANCELLED)
        .set("updated_at", Instant.now());
    UpdateResult result = mongoTemplate.updateMulti(filter, update, AttendanceDocument.class);
    log.info("lesson.cancelled: lessonId={}, updatedCount={}", lessonId, result.getModifiedCount());
}
```

### Payload extraction helper

```java
// Safe extraction of numeric IDs from Jackson-deserialized Map<String, Object>
@SuppressWarnings("unchecked")
private Map<String, Object> extractPayload(Map<String, Object> envelope) {
    return (Map<String, Object>) envelope.get("payload");
}

private Long extractLong(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null) return null;
    return ((Number) value).longValue();
}
```

---

## Validation Architecture

`workflow.nyquist_validation` is absent from `.planning/config.json` — treating as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test 3.4.x + Testcontainers + Awaitility 4.2.2 |
| Config file | None — standard Boot test auto-configuration |
| Quick run command | `./gradlew :services:attendance-service:attendance-app:test --tests "*.EventConsumerIntegrationTest"` |
| Full suite command | `./gradlew :services:attendance-service:attendance-app:test` |

**Awaitility confirmed:** `org.awaitility:awaitility:4.2.2` is present in the test runtime classpath (verified via Gradle dependency tree). Use `Awaitility.await().atMost(5, SECONDS).untilAsserted(() -> ...)` to wait for async RabbitListener processing in tests.

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | Notes |
|--------|----------|-----------|-------------------|-------|
| MARK-03 | `lesson.closed` creates absent docs for students with no existing record | Integration | `--tests "*.EventConsumerIntegrationTest#lessonClosed_*"` | MongoDB + RabbitMQ Testcontainers |
| MARK-04 | `lesson.closed` does NOT overwrite existing checkin (`$setOnInsert` semantics) | Integration | `--tests "*.EventConsumerIntegrationTest#lessonClosed_existingCheckin_*"` | Pre-seed doc, verify unchanged |
| MARK-05 | `lesson.cancelled` sets all existing attendance docs to CANCELLED | Integration | `--tests "*.EventConsumerIntegrationTest#lessonCancelled_*"` | Pre-seed docs, verify status update |

**DLQ routing (D-06):** Testing DLQ routing requires publishing via fanout exchange and verifying `AmqpAdmin.getQueueInfo("attendance-service.events.dlq").getMessageCount()` after a simulated gRPC failure. Mark as optional — the DLQ queue existence is already verified in `RabbitConsumerTest`.

### Recommended Test Class: `EventConsumerIntegrationTest`

Extends `AbstractAttendanceIntegrationTest`. Uses `@MockitoBean` gRPC clients (already mocked in base class). Publishes messages via `RabbitTemplate`, waits for processing via Awaitility, asserts MongoDB state.

Key scenarios to cover:
1. `lessonClosed_noExistingRecords_createsAbsentForAllStudents` — publish `lesson.closed`, mock gRPC, verify N new docs with status=ABSENT source=AUTO_SCHEDULER
2. `lessonClosed_existingCheckin_preservesCheckinStatus` — pre-seed doc with PRESENT, publish `lesson.closed`, verify doc still PRESENT
3. `lessonClosed_partialCheckins_createsAbsentOnlyForUnmarked` — pre-seed PRESENT for student A, verify only student B gets absent
4. `lessonCancelled_existingDocs_updatesStatusToCancelled` — pre-seed 3 docs, publish `lesson.cancelled`, verify all 3 show CANCELLED
5. `lessonCancelled_noDocs_noError` — publish `lesson.cancelled` for unknown lesson, verify no exception
6. `semesterArchived_refreshesSemesterCache` — verify `SemesterCacheService.refresh()` is called

### Wave 0 Gaps

- [ ] `tests/integration/EventConsumerIntegrationTest.java` — covers MARK-03, MARK-04, MARK-05 (new file)

*(Existing test infrastructure: `AbstractAttendanceIntegrationTest`, `RabbitConsumerTest`, Testcontainers setup — all reusable)*

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| MongoDB (Docker) | Integration tests | Runtime only | 7.0 (Testcontainer) | — |
| RabbitMQ (Docker) | Integration tests | Runtime only | 3.13 (Testcontainer) | — |
| Awaitility | Integration tests | Confirmed | 4.2.2 | — |
| Java 21 | Build | Configured | 21.0.9 (MS) | — |
| Gradle Wrapper | Build | Available | Per wrapper | — |

**No missing dependencies.** All phase requirements use already-declared libraries and already-started Testcontainers infrastructure.

---

## Project Constraints (from CLAUDE.md)

| Directive | Impact on This Phase |
|-----------|---------------------|
| No `@Enumerated(EnumType.ORDINAL)` — enums as strings | `AttendanceStatus.ABSENT` and `AttendanceStatus.CANCELLED` must serialize to `"absent"` / `"cancelled"` in MongoDB via existing `MongoConvertersConfig` |
| Packages: `ru.rutcampustrack.{service}.{module}` | New service goes in `ru.rutcampustrack.attendance.event` |
| Event types: `{domain}.{action}` | Already established — `lesson.closed`, `lesson.cancelled`, `semester.archived` |
| No Lombok in `*-api-contract` | `AttendanceStatus` and `AttendanceSource` are plain enums — compliant |
| `*-app` modules: Lombok allowed | `LessonEventService` may use Lombok (`@Slf4j`, `@RequiredArgsConstructor`) |
| `@ControllerAdvice` centralized error handling | No controllers in this phase; not applicable |
| `report/` domain never imports from `checkin/` directly | `LessonEventService` is in `event/` package — no domain isolation issue |

---

## Open Questions

1. **BulkWriteResult logging — getUpserts() vs getInsertedCount()**
   - What we know: In MongoDB bulkWrite with `upsert=true`, newly inserted docs appear in the upserts list (not insertedCount). `getInsertedCount()` is for explicit insert operations. Spring Data `BulkWriteResult` wraps the MongoDB driver result.
   - What's unclear: The exact method name on `com.mongodb.bulk.BulkWriteResult` accessible via Spring's result object — could be `getUpserts().size()` or the count may be unavailable cleanly.
   - Recommendation: For logging, use `result.getModifiedCount()` (existing matched+modified) from `BulkWriteResult`. Avoid logging upsert count in the code example — log student count from `members.getStudentsCount()` instead (as shown in the revised code example above).

---

## Sources

### Primary (HIGH confidence)
- Spring Data MongoDB documentation — `BulkOperations` API, `MongoTemplate.updateMulti`, `$setOnInsert` semantics
- Existing codebase: `EventConsumer.java`, `RabbitConfig.java`, `AttendanceDocument.java`, `AbstractAttendanceIntegrationTest.java` — confirmed Phase 15 output
- Proto contracts: `proto/academic.proto`, `proto/schedule.proto` — exact field names verified
- Event schemas: `event-schemas/lesson.closed.json`, `event-schemas/lesson.cancelled.json`, `event-schemas/semester.archived.json` — exact payload structures
- `CLAUDE.md` — coding rules verified
- `STATE.md` — Known tech debt and unresolved decisions reviewed
- Gradle dependency tree — `org.awaitility:awaitility:4.2.2` confirmed in testRuntimeClasspath

### Secondary (MEDIUM confidence)
- Jackson `Jackson2JsonMessageConverter` behavior with `Map<String, Object>` — Integer deserialization for JSON numbers in range — standard Jackson behavior, widely documented

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies, all libraries already in build.gradle.kts
- Architecture: HIGH — direct code inspection of Phase 15 output
- Pitfalls: HIGH — payload integer casting verified by Jackson behavior; other pitfalls from direct code analysis
- Test patterns: HIGH — Awaitility 4.2.2 confirmed in test classpath; BulkWriteResult logging resolved to use student count

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (stable domain — Spring AMQP + MongoDB bulk ops are stable APIs)
