# Pitfalls Research

**Domain:** Schedule Service — lesson auto-generation, cron-based status transitions, gRPC server/client, RabbitMQ events added to existing microservice system
**Researched:** 2026-03-31
**Confidence:** HIGH (verified against actual codebase, schema V1__baseline.sql, academic service gRPC/event patterns, proto contracts)

---

## Critical Pitfalls

### Pitfall 1: @Scheduled Cron Fires Duplicate Transitions in Multi-Instance Deploy

**What goes wrong:**
Two Schedule Service instances run simultaneously (horizontal scaling or rolling restart). Both fire `@Scheduled` cron jobs at the same second. Both SELECT the same batch of `lessons WHERE status='planned' AND start_time <= NOW()`. Both UPDATE them to `status='active'`. Both publish `lesson.started` events. Notification services receive duplicate events and push duplicate "para started" messages to students for the same lesson.

**Why it happens:**
Spring `@Scheduled` is per-JVM — it does not know about other JVM instances. With two containers, every scheduled task fires twice. The UPDATE itself is idempotent (setting `active` on an already-`active` row is harmless), but the RabbitMQ publication happens after the UPDATE regardless.

**How to avoid:**
Use `SELECT ... FOR UPDATE SKIP LOCKED` when fetching lessons for status transition. Only the first instance acquires the lock; the second skips all rows already locked. Combine with a `transition_locked_at` guard: set a timestamp column when the row is claimed, skip rows where `transition_locked_at` is not null and is recent (< 2 minutes):

```sql
-- Claim planned lessons for ACTIVE transition atomically
SELECT id FROM lessons
WHERE status = 'planned'
  AND scheduled_start <= NOW()
FOR UPDATE SKIP LOCKED;
```

For a single-instance deploy (current Docker Compose setup), a simpler guard: add `WHERE status = 'planned'` to the UPDATE so already-transitioned rows are silently skipped, and only publish `lesson.started` for rows where the UPDATE actually changed rows (`getUpdateCount() > 0`). Use `@Modifying(clearAutomatically = true)` JPQL UPDATE and check return count.

**Warning signs:**
- Duplicate `lesson.started` events in RabbitMQ during rolling restart
- Students receive two identical push notifications for the same lesson start
- Log shows same `lesson_id` appearing in two separate cron execution log lines within same second

**Phase to address:**
Lesson status cron phase — implement `FOR UPDATE SKIP LOCKED` or row-count guard before writing the event publisher. Never publish without verifying the row was actually transitioned.

---

### Pitfall 2: Cron Timezone Mismatch — Lessons Transition at Wrong Clock Time

**What goes wrong:**
Schedule items store `start_time` and `end_time` as `TIME` (no timezone). The database stores `TIMESTAMPTZ` for `created_at` but lesson time slots are bare `TIME`. The JVM runs in UTC (Docker default). The university operates in Moscow Time (UTC+3). A lesson scheduled at `08:30` Moscow time fires the ACTIVE transition at `08:30 UTC = 11:30 Moscow` — three hours late. Students cannot check in.

**Why it happens:**
`TIME` columns in PostgreSQL have no timezone context. When Java compares `LocalTime.now()` (UTC) against `08:30` (intended as Moscow time) the comparison is correct numerically but semantically wrong. The cron job fires on schedule in UTC, queries `WHERE start_time <= CURRENT_TIME`, and CURRENT_TIME in PostgreSQL is also UTC — so the same bias exists end-to-end. Appears to work in local dev (developer in UTC+3 on Windows, JVM in local timezone) but breaks in Docker where JVM is UTC.

**How to avoid:**
Fix the timezone at the infrastructure layer, not scattered across code:
1. Set `TZ=Europe/Moscow` in `docker-compose.yml` for the `schedule-service` container (and PostgreSQL container).
2. Set `spring.jpa.properties.hibernate.jdbc.time_zone=Europe/Moscow` in `application.yml`.
3. Store `start_time` / `end_time` as `TIMETZ` instead of bare `TIME`, OR store them as `TIME` with a documented convention that "all TIME values are Moscow time" and enforce the timezone via JVM startup flag `-Duser.timezone=Europe/Moscow`.

The simplest approach for this project: set `TZ=Europe/Moscow` in Docker Compose and `-Duser.timezone=Europe/Moscow` in the Gradle `bootRun` task. Then all `LocalTime.now()` calls are automatically Moscow time.

**Warning signs:**
- Lessons go ACTIVE 3 hours after their scheduled time in production
- Local dev works correctly, Docker environment breaks time comparisons
- `SELECT NOW()` in PostgreSQL container returns UTC while application logs show Moscow time (or vice versa)

**Phase to address:**
Before writing any cron scheduler code — establish timezone configuration in `application.yml` and `docker-compose.yml`. Write a test that constructs a lesson with `start_time = LocalTime.now()` and asserts the cron fires immediately.

---

### Pitfall 3: Lesson Auto-Generation Produces Duplicate Rows on Retry

**What goes wrong:**
`generateLessonsForWeek(semesterId, groupId, weekStart)` is called. It generates 5 lessons and inserts them. A retry occurs (request timeout, user refreshes the page, or the headman calls the endpoint twice). The second call attempts to insert the same `(schedule_item_id, date)` pairs. PostgreSQL throws `duplicate key value violates unique constraint "lessons_schedule_item_id_date_key"`. The service returns a 500 error instead of a clean idempotent response.

**Why it happens:**
The UNIQUE constraint on `(schedule_item_id, date)` correctly prevents duplicates at the DB level, but the service does not handle the constraint violation as a no-op. It propagates as a `DataIntegrityViolationException` to the controller.

**How to avoid:**
Use `INSERT ... ON CONFLICT DO NOTHING` via a native query or Spring Data `@Modifying` + native SQL. For JPQL, check existence before insert:

```java
// Option A: Native upsert (preferred)
@Modifying
@Query(value = """
    INSERT INTO lessons (schedule_item_id, date, status)
    VALUES (:scheduleItemId, :date, 'planned')
    ON CONFLICT (schedule_item_id, date) DO NOTHING
    """, nativeQuery = true)
int insertLessonIfAbsent(Long scheduleItemId, LocalDate date);
```

```java
// Option B: Check before insert
if (!lessonRepository.existsByScheduleItemIdAndDate(scheduleItemId, date)) {
    lessonRepository.save(new Lesson(...));
}
// Note: race condition possible in multi-instance; ON CONFLICT DO NOTHING is safer
```

Return HTTP 200 (not 201) on second call to indicate "generation completed, lessons already exist."

**Warning signs:**
- `DataIntegrityViolationException: unique constraint "lessons_schedule_item_id_date_key"` on repeated generation calls
- Generation returns 500 when headman clicks the button twice
- Integration test: call generate twice, assert no exception on second call

**Phase to address:**
Lesson generation phase — make idempotency the first test written before any generation logic. A test that calls generate twice should always pass.

---

### Pitfall 4: Week Parity Calculation Off-by-One (ISO Week vs Academic Week)

**What goes wrong:**
The `week_type` column distinguishes `odd`/`even` weeks. The calculation uses Java `LocalDate.get(WeekFields.ISO.weekOfWeekBasedYear())`. ISO week 1 may be the last week of December (ISO weeks start Monday). The semester starts September 1 (which could be ISO week 35 or 36). "Week 1 of the semester" is not "ISO week 1." If the first semester week is ISO week 36 (even), then `schedule_items` created with `week_type=odd` never fire in the first week despite the headman configuring them as "first week."

**Why it happens:**
ISO week numbers are absolute (1-53 per year). Academic "odd/even" parity is relative to the semester start. A semester starting on an ISO-even week means ISO-even weeks are "odd" academically. Developers use `isoWeek % 2 == 0` for even, but the first semester week parity determines which direction parity goes.

**How to avoid:**
Calculate parity relative to the semester start date, not absolute ISO week number:

```java
public static boolean isOddWeek(LocalDate lessonDate, LocalDate semesterStart) {
    // Week 1 of semester = odd, week 2 = even, etc.
    long weeksSinceStart = ChronoUnit.WEEKS.between(
        semesterStart.with(DayOfWeek.MONDAY),
        lessonDate.with(DayOfWeek.MONDAY)
    );
    return (weeksSinceStart % 2) == 0; // 0-indexed: week 0 = odd
}
```

Store in a utility class `LessonDateUtils` and test exhaustively:
- Semester starts Monday Sept 1 → week 1 = odd
- Semester starts Wednesday Sept 3 → week 1 (anchor Monday Aug 31) = odd
- Sept 15 = week 3 = odd
- Sept 22 = week 4 = even

**Warning signs:**
- Lessons for `week_type=odd` schedule items appear on even calendar weeks
- Headman reports "половина пар не генерируется" (half of lessons not generating)
- Unit test: generate lessons for a known 4-week period, count odd/even lessons, verify 2 each

**Phase to address:**
Lesson generation phase — implement and unit-test `LessonDateUtils.isOddWeek()` before any generation loop is written. This is a pure function that can be 100% unit-tested without a database.

---

### Pitfall 5: gRPC Client to Academic Service — No Deadline Causes Hanging Threads

**What goes wrong:**
Schedule Service creates a schedule item. It calls `AcademicGrpcServiceBlockingStub.getGroup(request)` to validate the group exists. Academic Service is slow or temporarily down. The gRPC blocking stub waits indefinitely. The HTTP request thread is blocked. With 10 concurrent headman requests, 10 threads block. The Tomcat thread pool exhausts. All subsequent requests to Schedule Service queue or fail with "connection refused."

**Why it happens:**
gRPC stubs created without `.withDeadlineAfter(...)` have no timeout. Unlike HTTP clients (which have connection timeout defaults), gRPC stubs are indefinitely patient by default. This is documented but overlooked when first using gRPC.

**How to avoid:**
Always attach a deadline to every outgoing gRPC call:

```java
// In ScheduleService validation method:
GroupResponse group = academicGrpcStub
    .withDeadlineAfter(3, TimeUnit.SECONDS)
    .getGroup(GroupRequest.newBuilder().setGroupId(groupId).build());
```

Configure deadline as a constant or application property, not hardcoded per call site:

```java
@Value("${grpc.client.academic.deadline-seconds:3}")
private int academicDeadlineSeconds;

private GroupResponse callGetGroup(long groupId) {
    return academicGrpcStub
        .withDeadlineAfter(academicDeadlineSeconds, TimeUnit.SECONDS)
        .getGroup(GroupRequest.newBuilder().setGroupId(groupId).build());
}
```

Map `StatusRuntimeException` with `Status.DEADLINE_EXCEEDED` to HTTP 504 or a service-specific exception that returns 503.

**Warning signs:**
- Schedule Service stops responding when Academic Service is restarted
- Tomcat thread pool exhaustion log: `WARN o.a.t.util.net.NioEndpoint - Socket accept failed`
- gRPC call hangs more than 5 seconds in tests

**Phase to address:**
gRPC client phase — configure deadlines before writing any gRPC validation call. Add a Testcontainers integration test that simulates Academic Service delay and asserts Schedule Service returns within deadline.

---

### Pitfall 6: gRPC Client NOT_FOUND Treated as Internal Error

**What goes wrong:**
Headman creates a schedule item with a non-existent `subject_id`. Schedule Service calls `GetGroup`, gets a valid response, then calls `GetTeacherSubjects` which returns an empty list (subject not assigned to teacher). Alternatively, Academic Service's `GetGroup` is called with a group that was recently deleted and returns `NOT_FOUND` status. The `StatusRuntimeException` is not caught, propagates to the controller, and the user receives HTTP 500 instead of 404 or 422.

**Why it happens:**
gRPC exceptions are `StatusRuntimeException`, not standard Java exceptions. Developers catch `Exception` generically or let Spring's `@ControllerAdvice` handle it without a specific handler for `StatusRuntimeException`. The default Spring handler maps unexpected exceptions to 500.

**How to avoid:**
Add a `@ControllerAdvice` handler for `StatusRuntimeException` that maps gRPC status codes to HTTP status codes:

```java
@ExceptionHandler(StatusRuntimeException.class)
public ResponseEntity<ErrorResponse> handleGrpcStatus(StatusRuntimeException ex) {
    return switch (ex.getStatus().getCode()) {
        case NOT_FOUND -> ResponseEntity.status(404)
            .body(new ErrorResponse("Resource not found via gRPC: " + ex.getStatus().getDescription()));
        case UNAVAILABLE, DEADLINE_EXCEEDED -> ResponseEntity.status(503)
            .body(new ErrorResponse("Upstream service unavailable"));
        case INVALID_ARGUMENT -> ResponseEntity.status(422)
            .body(new ErrorResponse("Invalid argument: " + ex.getStatus().getDescription()));
        default -> ResponseEntity.status(500)
            .body(new ErrorResponse("Internal error"));
    };
}
```

Follow the same `GrpcExceptionAdvice` pattern already established in Academic Service's `GrpcExceptionAdvice.java`, but for the **client** side (HTTP REST `@ControllerAdvice`).

**Warning signs:**
- Creating a schedule item with invalid IDs returns HTTP 500 instead of 404/422
- Logs show unhandled `StatusRuntimeException` in controller layer
- No `@ExceptionHandler(StatusRuntimeException.class)` in any `@ControllerAdvice`

**Phase to address:**
gRPC client phase — write the `StatusRuntimeException` handler as the first step before any gRPC validation calls.

---

### Pitfall 7: RabbitMQ lesson.started Published for Cancelled Lessons

**What goes wrong:**
The cron job transitions `planned → active` for all lessons whose `start_time <= NOW()`. A lesson that was cancelled by the headman (status = `cancelled`) is accidentally included if the WHERE clause is:

```sql
WHERE status = 'planned' AND scheduled_start <= NOW()
```

But a lesson that was cancelled AFTER having been planned, then manually set back to `planned` by an "uncancel" operation, then cancelled again — its status is `cancelled`. The query correctly excludes it. However, if the cron fires during a concurrent cancel operation (rare race), the UPDATE might transition `cancelled → active`.

The more common mistake: publishing `lesson.started` for a lesson that the headman just cancelled in the same second the cron fires. The DB transaction for cancel and the cron UPDATE run concurrently. One wins. If the cron wins, a `lesson.started` event fires for a cancelled lesson. Notification services send "Para started" for a lesson that doesn't exist.

**Why it happens:**
The cron job's UPDATE is a bulk operation: `UPDATE lessons SET status='active' WHERE status='planned' AND ...`. If cancel runs `UPDATE lessons SET status='cancelled' WHERE id=? AND status='planned'` concurrently, one of the two UPDATEs wins. There is no explicit lock.

**How to avoid:**
Add explicit status guard in the cron UPDATE: only transition if `status = 'planned'` (already correct). The cancel operation also uses `WHERE status = 'planned'` guard. PostgreSQL's row-level locking ensures only one UPDATE wins. The losing UPDATE changes 0 rows.

After the cron UPDATE, check `affected rows count > 0` before publishing `lesson.started`. This is the critical guard:

```java
int affected = lessonRepository.transitionToActive(cutoffTime); // returns update count
if (affected > 0) {
    // fetch the lesson IDs that were actually transitioned and publish events
}
```

Never publish events based on "the lesson should be active by now" logic — only publish based on confirmed DB state change.

**Warning signs:**
- `lesson.started` events arriving for lessons with `is_geo_blocked=true` or `status=cancelled`
- Students receive push for a lesson the headman already cancelled
- No row-count check before event publishing in cron handler

**Phase to address:**
Lesson status cron phase — the row-count check must be in the cron handler before event publishing. Integration test: cancel a lesson, trigger cron manually, assert no `lesson.started` event published.

---

### Pitfall 8: @Scheduled Cron Misses Transitions During Service Restart / Downtime

**What goes wrong:**
Schedule Service restarts at 09:05. A lesson was supposed to go `planned → active` at 08:30 and `active → closed` at 10:05. The 08:30 cron did not fire (service was down). After restart, the next cron at 09:10 runs `WHERE status='planned' AND start_time <= NOW()`. It correctly finds the 08:30 lesson (still `planned`, past its start time) and transitions it to `active`. But it also fires `lesson.started` at 09:10, which is 40 minutes late. Notification services send "Para started" when the class is nearly over. Students see a stale notification.

**Why it happens:**
Catch-up crons faithfully execute missed transitions without knowing they are late. The `lesson.started` event is published regardless of how long the lesson has been in a "should have been active" state.

**How to avoid:**
Add a staleness guard before publishing `lesson.started`. Only publish if the lesson start time is within the acceptable notification window (e.g., within the last 10 minutes):

```java
lessons.stream()
    .filter(l -> l.getStartTime().isAfter(LocalTime.now().minusMinutes(10)))
    .forEach(l -> eventPublisher.publishEvent(new LessonStartedEvent(l)));
```

For `lesson.closed` transitions: always publish — the close event is less time-sensitive and must fire to trigger auto-absent generation in Attendance Service.

For `planned → active` that is already more than 10 minutes late: transition the status (so gRPC `GetActiveLesson` returns it correctly) but skip the `lesson.started` RabbitMQ event (notifications are useless 40 minutes late).

**Warning signs:**
- After a 5-minute restart, students receive "Para started" notifications for lessons that started before the restart
- RabbitMQ gets a burst of `lesson.started` events all at once after service restart
- No staleness check in cron transition logic

**Phase to address:**
Lesson status cron phase — define the notification staleness window as a config property. Distinguish "must always transition status" (correctness) from "should publish event" (timely notification).

---

### Pitfall 9: gRPC Port Not Configured — Schedule Service gRPC Server Conflicts

**What goes wrong:**
`grpc-spring-boot-starter` (net.devh) defaults to port 9090. Schedule Service HTTP runs on 9092. When the gRPC server starts, it tries to bind port 9090, which is already used by Auth Service in the same Docker network. The service starts successfully (different containers don't share ports), but Attendance Service is hardcoded to call `schedule-service:9090` — it connects to Auth Service instead, getting bizarre errors.

This is the same class of pitfall from Academic Service (Pitfall 3 in the existing PITFALLS.md) but must be repeated for Schedule Service with its own port number.

**How to avoid:**
Set the gRPC server port explicitly in `application.yml` before adding any `@GrpcService` bean:

```yaml
grpc:
  server:
    port: 19092   # convention: HTTP port + 10000
```

Set the gRPC client address for Academic Service in `application.yml`:

```yaml
grpc:
  client:
    academic-service:
      address: static://academic-service:19091
      negotiation-type: plaintext
```

Update `docker-compose.yml` to expose port `19092` for Schedule Service container.

**Warning signs:**
- Attendance Service `GetActiveLesson` calls return Auth Service responses
- `grpcurl schedule-service:9090 list` returns Academic proto methods instead of Schedule proto methods
- `application.yml` has no `grpc.server.port` property

**Phase to address:**
gRPC server setup phase — configure port as the very first step, before implementing any RPC handler.

---

### Pitfall 10: Lesson Generation Crossing Semester Boundaries

**What goes wrong:**
Auto-generation is triggered for a date range that extends beyond `semester.date_to`. Lessons are created for dates after the semester ends (e.g., January exams, holidays). These "lessons" are technically generated but represent phantom classes. The Attendance Service receives `lesson.started` events for them, generates absence records for students who are not in active academic period.

**Why it happens:**
The generation loop iterates `date = semesterStart; date <= requestedDateTo; date = date.plusDays(7)`. If `requestedDateTo` is not bounded by `semesterDateTo`, the loop escapes the semester.

**How to avoid:**
Always cap the generation range: `actualDateTo = min(requestedDateTo, semesterDateTo)`. The generation service must fetch the semester's `date_to` via gRPC `GetActiveSemester` (or from the `semester_id` passed on the schedule item) and enforce the cap:

```java
LocalDate cap = semesterDateTo;
LocalDate end = dateTo.isAfter(cap) ? cap : dateTo;
for (LocalDate d = dateFrom; !d.isAfter(end); d = d.plusWeeks(1)) {
    generateForDate(scheduleItem, d);
}
```

Also validate on schedule item creation: the `semester_id` must match the currently active semester; `start_time` / `end_time` must be plausible (start < end, duration >= 30 min, duration <= 4 hours).

**Warning signs:**
- Lessons exist in `schedule_db.lessons` with `date > semester.date_to`
- Attendance auto-absent records appear for dates in exam/holiday period
- Schedule item creation endpoint accepts any `semester_id` without validating against active semester

**Phase to address:**
Lesson generation phase — add semester boundary assertion as a pre-condition check. Integration test: generate lessons for a date range exceeding semester end; assert no lesson is created beyond `date_to`.

---

### Pitfall 11: @TransactionalEventListener AFTER_COMMIT Swallows lesson.started on Cron Thread

**What goes wrong:**
The existing Academic Service pattern uses `@TransactionalEventListener(phase = AFTER_COMMIT)` to publish RabbitMQ events safely after DB commit. When reused for the cron scheduler, this pattern breaks silently. The cron method runs with `@Scheduled` — which by default has no transaction. `ApplicationEventPublisher.publishEvent(new LessonStartedEvent(...))` fires, but `@TransactionalEventListener(AFTER_COMMIT)` only processes events when the current thread's transaction commits. Without a transaction, the event is silently dropped.

**Why it happens:**
`@TransactionalEventListener` with `phase = AFTER_COMMIT` requires an active transaction. If no transaction exists, Spring does not throw an error — it silently discards the event. This is documented behavior but easy to miss when the event publication code is copy-pasted from a service layer (which always runs in a transaction) to a cron handler (which doesn't).

**How to avoid:**
Wrap the cron body in `@Transactional`:

```java
@Scheduled(cron = "${schedule.cron.lesson-transitions}")
@Transactional
public void transitionLessonStatuses() {
    // DB updates + event publication — all within a transaction
    // AFTER_COMMIT listener fires when this method returns
}
```

Or use a dedicated `@Transactional` service method called from the cron:

```java
@Scheduled(cron = "...")
public void cronTick() {
    lessonTransitionService.runTransitions(); // @Transactional on this method
}
```

The key invariant: the transaction must exist on the thread when `publishEvent()` is called, so that `AFTER_COMMIT` fires. Test this by running the cron and verifying the event arrives in RabbitMQ.

**Warning signs:**
- Lesson statuses change correctly in DB but no `lesson.started` / `lesson.closed` events appear in RabbitMQ
- `@TransactionalEventListener` handler log never fires despite events being published
- Cron method has no `@Transactional` and calls `publishEvent()` directly

**Phase to address:**
Lesson status cron phase — add `@Transactional` to the cron method and add an integration test that verifies the event actually reaches RabbitMQ (not just that `publishEvent()` was called).

---

### Pitfall 12: GetActiveLesson gRPC Returns Wrong Lesson When Group Has Multiple Simultaneous Slots

**What goes wrong:**
`GetActiveLesson(group_id, timestamp)` queries `WHERE status='active' AND group_id = ?`. A group has two active lessons in the same time slot (data corruption from a bug, or a legitimate "potok" scenario where a subject spans two lesson slots). The query returns a list but the proto contract defines a single `LessonResponse`. The service arbitrarily picks the first result. The Attendance Service uses this lesson_id for check-in. One of the two lessons gets no attendance records.

**Why it happens:**
The UNIQUE constraint on `schedule_items` is `(group_id, day_of_week, lesson_number, week_type, semester_id)`. It prevents duplicate templates but does not prevent two different `schedule_items` from having overlapping time ranges. Lessons from both can be `active` simultaneously.

**How to avoid:**
`GetActiveLesson` must search by time window, not just `status='active'`. Query:

```sql
SELECT l.* FROM lessons l
JOIN schedule_items si ON l.schedule_item_id = si.id
WHERE si.group_id = :groupId
  AND l.status = 'active'
  AND si.start_time <= :currentTime
  AND si.end_time >= :currentTime
ORDER BY si.lesson_number ASC
LIMIT 1
```

The time window query makes it unambiguous which lesson is currently running, even if two happen to overlap (return the one with the earliest lesson_number). Return `NOT_FOUND` if no lesson matches (no active para right now).

**Warning signs:**
- Group with two back-to-back lessons (08:30-10:05 and 10:15-11:50) where second is active — `GetActiveLesson` at 10:30 returns wrong lesson if both are somehow `active`
- Check-in failures with "lesson not found" when para is clearly in progress
- `GetActiveLesson` not filtering by time window, only by `status='active'`

**Phase to address:**
gRPC server phase — use time-window query from the start. Add integration test: lesson is active, query at a time outside the window, assert NOT_FOUND.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Skip `FOR UPDATE SKIP LOCKED` for cron transitions (assume single instance) | Simpler query | Duplicate events on rolling restart or multi-instance | Acceptable for single-instance Docker Compose deploy; document the assumption explicitly |
| Hardcode lesson generation as "generate whole semester upfront" instead of lazy | Simpler code | Generates thousands of rows at semester start; large bulk inserts block the DB briefly | Acceptable if generation is done during off-hours (admin triggers it manually, not on schedule item create) |
| Skip deadline on gRPC stubs for Academic Service calls | Simpler code | Thread exhaustion if Academic Service is slow | Never — always add deadline |
| Publish events without checking affected row count | Simpler code | Duplicate/phantom events for already-transitioned lessons | Never — always gate publication on confirmed DB change |
| Use `LocalTime.now()` without timezone config | Works in dev | Lessons transition 3 hours late in production (Docker UTC vs Moscow time) | Never — set timezone before writing any time comparison |
| Skip idempotency on lesson generation (rely on UNIQUE constraint to raise exception) | Less code | 500 errors on retry, headman must know not to click twice | Never — use `ON CONFLICT DO NOTHING` |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| net.devh gRPC server | Not setting `grpc.server.port` — defaults to 9090 which is Auth Service's port | Set `grpc.server.port: 19092` in `application.yml` before any `@GrpcService` bean |
| net.devh gRPC client | No `withDeadlineAfter()` on blocking stubs | Always attach `.withDeadlineAfter(3, TimeUnit.SECONDS)` per call or on stub creation |
| gRPC client `StatusRuntimeException` | Uncaught exception propagates as HTTP 500 | Add `@ExceptionHandler(StatusRuntimeException.class)` in `@ControllerAdvice` |
| `@TransactionalEventListener(AFTER_COMMIT)` + `@Scheduled` | Event silently dropped if cron method has no transaction | Add `@Transactional` to the cron method or delegate to a `@Transactional` service |
| RabbitMQ fanout exchange | Schedule Service declaring a new exchange instead of reusing `rut-uit.events` | Reuse same `rut-uit.events` fanout exchange — it already exists from Academic Service; `FanoutExchange` bean declaration is idempotent but must use identical `durable=true, autoDelete=false` |
| RabbitMQ `channelTransacted=true` + `AFTER_COMMIT` | Setting `channelTransacted=true` on `RabbitTemplate` causes duplicate messages with `AFTER_COMMIT` | Do NOT set `channelTransacted=true` — follow same pattern as `academic-service/event/RabbitConfig.java` |
| gRPC + JPA thread context | `@GrpcService` methods run on gRPC thread pool, not Tomcat threads — Spring `RequestContextHolder` is empty | Never call `RequestContextHolder.getRequestAttributes()` from inside a gRPC service handler — use method parameters only (already the pattern in `AcademicGrpcServiceImpl`) |
| PostgreSQL `TIME` vs `TIMETZ` | `TIME` columns store no timezone info — JVM timezone assumption is invisible | Set `TZ=Europe/Moscow` at Docker container level and `spring.jpa.properties.hibernate.jdbc.time_zone=Europe/Moscow` |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Bulk lesson generation without batching (one INSERT per lesson) | Semester generation for 20 groups × 5 days × 18 weeks = 1800 individual INSERTs takes 5+ seconds | Use `saveAll()` with a batch size (Hibernate `hibernate.jdbc.batch_size=50`) or single native INSERT with VALUES list | At generation time (observable immediately for large semesters) |
| Cron fetches all lessons with status filter without date index | `SELECT * FROM lessons WHERE status IN ('planned','active')` full table scan after 10K+ rows | Partial index already defined: `idx_lessons_status WHERE status IN ('planned', 'active')` — use it; add `AND date = CURRENT_DATE` to further narrow | At 5000+ lessons (≈10 groups × 1 semester) |
| `GetLessonsByGroup` gRPC returns entire semester of lessons as repeated proto messages | Attendance Service calls this for 180-day semester = potentially 900+ lesson proto messages | Add `date_from`/`date_to` range filter (already in proto contract) and enforce max range of 30 days in service layer | At call time for full-semester queries |
| N+1: for each lesson fetched, load `schedule_item` separately | `SELECT schedule_item FROM schedule_items WHERE id=?` for each lesson in list endpoint | JOIN `lessons` with `schedule_items` in a single JPQL query for REST list endpoints | At 20+ lessons per page |
| Cron runs every minute checking all lessons | CPU + DB load every 60 seconds even when no transitions needed | Run cron every minute but narrow the query to today's date only: `AND date = CURRENT_DATE` | Always — no "break point," just unnecessary load |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Headman modifying schedule items of another group | Headman of group A creates schedule items for group B by passing a different `group_id` | In every HEADMAN endpoint, assert that `scheduleItem.groupId == X-Group-Id` header; return 403 if not |
| Skipping Academic Service validation on schedule item create | Invalid `subject_id`, `teacher_id`, `group_id` — phantom foreign keys in `schedule_db` | Always call `GetGroup` and `GetTeacherSubjects` gRPC before inserting `schedule_items`; treat NOT_FOUND as 422 |
| Cancel reason not sanitized | Very long strings stored in `cancel_reason VARCHAR(512)` — truncation silently corrupts | Validate `cancel_reason.length() <= 512` in contract DTO validation (`@Size(max = 512)`) |
| gRPC server accessible outside Docker network | Schedule gRPC on port 19092 exposed in Docker Compose `ports:` instead of `expose:` | Use `expose:` (not `ports:`) for gRPC ports — only HTTP port 9092 should be accessible through Gateway |

---

## "Looks Done But Isn't" Checklist

- [ ] **Cron timezone:** Lessons transition at correct Moscow time in Docker — verify by starting a lesson with `start_time = NOW() + 1 minute` and checking transition fires on time
- [ ] **Week parity:** Generate lessons for a 4-week period starting on an even-ISO-week semester start — verify odd/even lessons are correct relative to semester start, not ISO week number
- [ ] **Idempotent generation:** Call `generateLessons` twice for the same week — verify second call returns 200 with no exceptions and no duplicate rows in DB
- [ ] **AFTER_COMMIT + @Scheduled:** Trigger cron manually in integration test — verify `lesson.started` event actually appears in RabbitMQ (not just that `publishEvent()` was called by checking `@TransactionalEventListener` fires)
- [ ] **Deadline on gRPC stubs:** Verify `withDeadlineAfter()` is attached on every gRPC call — grep for `academicGrpcStub.get` without `withDeadlineAfter` in code
- [ ] **StatusRuntimeException handling:** Call an endpoint with an invalid `group_id` that makes Academic Service return NOT_FOUND — verify HTTP 404, not 500
- [ ] **gRPC server port:** `grpcurl schedule-service:19092 list` returns `rutcampustrack.schedule.ScheduleGrpcService` — not Auth Service methods
- [ ] **Cancelled lesson not transitioned:** Set a lesson to `cancelled`, trigger cron — verify lesson remains `cancelled` and no `lesson.started` event published
- [ ] **Semester boundary:** Generate lessons for a date range beyond `semester.date_to` — verify no lesson created after semester end
- [ ] **GetActiveLesson time window:** Query `GetActiveLesson` at a time 1 minute before `start_time` — verify NOT_FOUND returned (lesson is `planned`, not yet `active`)

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Timezone misconfiguration (lessons transitioning 3h late) | MEDIUM | 1. Set `TZ=Europe/Moscow` in Docker Compose. 2. Restart containers. 3. Manually re-trigger cron or run `UPDATE lessons SET status='active' WHERE ...` for missed transitions. |
| Duplicate `lesson.started` events published | LOW | 1. Events are already consumed idempotently by notification services (WebSocket push to same user twice is tolerable). 2. Add row-count guard and deploy. 3. No data corruption. |
| Ghost lessons beyond semester boundary | LOW | 1. `DELETE FROM lessons WHERE date > (SELECT date_to FROM ...)` in production. 2. Add boundary cap to generation code. |
| Week parity inverted for entire semester | HIGH | 1. All generated lessons have wrong parity — requires re-generation. 2. Fix `LessonDateUtils.isOddWeek()`. 3. `DELETE FROM lessons WHERE schedule_item_id IN (SELECT id FROM schedule_items WHERE semester_id=?)`. 4. Re-trigger generation. |
| gRPC stub deadlines missing — thread exhaustion | HIGH | 1. Restart Schedule Service (releases blocked threads). 2. Restore Academic Service. 3. Add deadlines and deploy. 4. Consider circuit breaker (Resilience4j) for future hardening. |
| `@TransactionalEventListener` events silently dropped | LOW | 1. No data corruption — just missed notifications. 2. Add `@Transactional` to cron method. 3. Deploy. 4. Manually transition any lessons that missed their events. |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Duplicate cron transitions (multi-instance) | Lesson status cron | Row-count guard tested; cron fires twice in test, assert one event published |
| Timezone mismatch | Before any cron code | Docker container TZ=Europe/Moscow set; `SELECT NOW()` in test container matches Moscow time |
| Duplicate lesson generation (no idempotency) | Lesson generation | Integration test: generate twice, assert no exception, no duplicate rows |
| Week parity off-by-one | Lesson generation | Unit test `LessonDateUtils.isOddWeek()` with known dates |
| gRPC deadline missing | gRPC client setup | `withDeadlineAfter()` present on all stub calls; grep check |
| gRPC NOT_FOUND unhandled | gRPC client setup | `StatusRuntimeException` handler in `@ControllerAdvice`; test with invalid group_id |
| lesson.started for cancelled lessons | Lesson status cron | Integration test: cancel lesson → trigger cron → assert no event |
| Catch-up cron sends stale notifications | Lesson status cron | Staleness check (10-min window); test: lesson 20 min overdue → status transitions, event skipped |
| gRPC server port conflict | gRPC server setup | `grpc.server.port: 19092` set before any `@GrpcService`; verified with grpcurl |
| Lessons beyond semester boundary | Lesson generation | Boundary cap enforced; integration test with out-of-range date |
| AFTER_COMMIT lost on cron thread | Lesson status cron | `@Transactional` on cron method; Testcontainers test verifies event in RabbitMQ |
| GetActiveLesson wrong lesson (time-window) | gRPC server | Time-window query; test at boundary times (1 min before start = NOT_FOUND) |

---

## Sources

- Direct codebase analysis: `services/schedule-service/schedule-app/src/main/resources/db/migration/V1__baseline.sql` — schema confirmed (TIME columns, UNIQUE constraints, partial index)
- Direct codebase analysis: `services/academic-service/academic-app/src/main/java/.../event/DomainEventListener.java` — `@TransactionalEventListener(AFTER_COMMIT)` pattern confirmed
- Direct codebase analysis: `services/academic-service/academic-app/src/main/java/.../event/RabbitConfig.java` — `channelTransacted=false` constraint documented in comments
- Direct codebase analysis: `services/academic-service/academic-app/src/main/java/.../grpc/AcademicGrpcServiceImpl.java` — gRPC thread isolation pattern (no RequestContext)
- Direct codebase analysis: `services/academic-service/academic-app/src/main/java/.../grpc/GrpcExceptionAdvice.java` — server-side gRPC exception handling pattern
- Direct codebase analysis: `services/academic-service/academic-app/src/main/resources/application.yml` — `grpc.server.port: 19091` convention confirmed
- Direct codebase analysis: `proto/schedule.proto` — `GetActiveLesson` single LessonResponse (not list) confirmed
- Direct codebase analysis: `docs/database-schema.md` — `TIME` (not `TIMETZ`) column types for `start_time`/`end_time` confirmed
- Spring Framework docs: `@TransactionalEventListener` behavior when no transaction is present (silently drops event)
- net.devh grpc-spring-boot-starter docs: default port 9090, `withDeadlineAfter` usage
- PostgreSQL docs: `SELECT FOR UPDATE SKIP LOCKED` for cron job mutual exclusion

---
*Pitfalls research for: Schedule Service (lesson auto-generation + cron transitions + gRPC server/client + RabbitMQ) added to existing RutCampusTrack microservice system*
*Researched: 2026-03-31*
