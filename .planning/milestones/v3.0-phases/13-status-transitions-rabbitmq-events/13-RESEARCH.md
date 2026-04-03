# Phase 13: Status Transitions + RabbitMQ Events - Research

**Researched:** 2026-04-03
**Domain:** Spring Scheduling, RabbitMQ AMQP, Spring ApplicationEvent, PostgreSQL time comparisons
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01** Single `@Scheduled(fixedDelay = 60000)` method in `LessonStatusTransitionJob`. Not a cron expression — fixedDelay prevents overlap if a run takes longer than 1 minute.

**D-02** Within each tick: first transition all `planned→active` (WHERE status=PLANNED AND date+startTime <= now), then transition all `active→closed` (WHERE status=ACTIVE AND date+endTime+5min <= now). Both in one `@Transactional` method.

**D-03** Load entities via repository query, set status in Java, `saveAll()`. Required because each transition must publish a Spring `ApplicationEvent` per lesson for RabbitMQ forwarding.

**D-04** No separate catch-up job. The cron query is time-based (`<= now`), so after a restart ALL past-due lessons naturally match.

**D-05** No lookback limit. Any lesson with incorrect status that should have transitioned gets caught up regardless of age.

**D-06** `lesson.cancelled` event published directly in `LessonService.cancel()` and `massCancelLessons()` using `ApplicationEventPublisher`.

**D-07** `lesson.started` and `lesson.closed` events published from the cron job after each status transition.

**D-08** All events go to the same `rut-uit.events` fanout exchange.

**D-09** Full payload per event-schemas/*.json: `lesson.started` includes lesson_id, group_id, subject_id, teacher_id, lesson_number, start_time, end_time, room. `lesson.closed` includes lesson_id, group_id, subject_id. `lesson.cancelled` includes lesson_id, group_id, subject_id, date, cancel_reason.

**D-10** `@TransactionalEventListener(phase = AFTER_COMMIT)` pattern — identical to academic-service's `DomainEventListener`.

**D-11** Cron compares `ZonedDateTime.of(lesson.date, scheduleItem.startTime, ZoneId.of("Europe/Moscow"))` against `ZonedDateTime.now(clock)` where clock is an injected Moscow-zone `Clock` bean.

**D-12** `ClockConfig` bean provides `Clock.system(ZoneId.of("Europe/Moscow"))`. Integration tests inject `Clock.fixed(...)` via `@MockitoBean` for deterministic time control.

**Scope:** No gRPC server (Phase 14), no attendance integration (v4.0), no Redis caching.

### Claude's Discretion

- Package placement for `LessonStatusTransitionJob` (e.g., `ru.rutcampustrack.schedule.lesson` or `ru.rutcampustrack.schedule.cron`)
- DomainEvent subclass design: `LessonStartedEvent`, `LessonClosedEvent`, `LessonCancelledEvent` — record-based payloads or nested classes
- RabbitConfig for schedule-service — can reference same exchange declaration or just use exchange name string
- Repository query design: JPQL vs native SQL for fetching lessons by status + time criteria
- Whether cron fetches ScheduleItems separately or via JOIN in the lesson query
- Integration test strategy for the cron job (using Clock.fixed to simulate time progression)
- Logging strategy for transition counts per cron tick
- `closedAt` field population: set to `OffsetDateTime.now()` when transitioning to CLOSED

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CRON-01 | Cron transitions planned->active when current time >= lesson start_time (Moscow TZ) | D-01, D-02, D-11, D-12: fixedDelay job + Clock bean + ZonedDateTime comparison pattern verified |
| CRON-02 | Cron transitions active->closed when current time >= lesson end_time + 5 min (Moscow TZ) | D-02: same tick, second query uses endTime + 5 minutes offset |
| CRON-03 | Cron catches up missed transitions on service restart | D-04, D-05: time-based query naturally catches all past-due lessons on every tick |
| EVNT-01 | System publishes lesson.started event when lesson becomes active | D-07, D-09: ApplicationEventPublisher + LessonStartedEvent with full payload |
| EVNT-02 | System publishes lesson.closed event when lesson becomes closed | D-07, D-09: same pattern, LessonClosedEvent |
| EVNT-03 | System publishes lesson.cancelled event when lesson is cancelled | D-06, D-09: published from LessonService.cancel() and massCancelLessons() |
| EVNT-04 | Events use @TransactionalEventListener(AFTER_COMMIT) pattern | D-10: DomainEventListener with AFTER_COMMIT, identical to academic-service |
</phase_requirements>

---

## Summary

Phase 13 adds the runtime lifecycle of lessons: cron-driven status transitions and RabbitMQ event publishing. All technical infrastructure is already in place — the schedule-service already has `spring-boot-starter-amqp`, `ClockConfig`, `SchedulingConfig` (with `@Profile("!test")` guard), and `AbstractScheduleIntegrationTest` (with `@MockitoBean RabbitTemplate`). The work is pure extension of existing patterns.

The implementation has three work streams: (1) port the `DomainEvent`/`DomainEventListener`/`RabbitConfig` pattern from academic-service to schedule-service, (2) build `LessonStatusTransitionJob` with the two-phase query approach, and (3) wire `ApplicationEventPublisher` into `LessonService.cancel()` and `massCancelLessons()`. All three are low-risk because the reference implementation in academic-service is verified and complete.

The most complex design decision is the repository query for the cron job. Since `start_time` and `end_time` are stored as PostgreSQL `TIME` columns (Moscow-local time without timezone) and `date` is a `DATE` column, the comparison `date + start_time <= now_moscow` must be done correctly in JPQL or native SQL. The existing pattern in `LessonRepository` uses native queries with `status::text` casts — the same approach must be used for the time-based queries, using `(date + start_time)::timestamp` comparisons with a parameter bound to `LocalDateTime` in Moscow time.

**Primary recommendation:** Port academic-service event classes verbatim (different package only), add two new repository methods using native SQL for time comparisons, implement `LessonStatusTransitionJob` as a single `@Transactional` method that calls `publishEvent()` per lesson, then add `publishEvent()` calls to the two cancel methods. Integration tests inject `Clock.fixed(...)` via `@MockitoBean` and call the job method directly (bypassing `@Scheduled` since `@Profile("!test")` disables it).

---

## Standard Stack

### Core (all already in schedule-service build.gradle.kts — no new dependencies needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-amqp | Spring Boot 3.4 BOM | RabbitMQ via `RabbitTemplate`, `Jackson2JsonMessageConverter` | Already declared; academic-service proves pattern |
| spring-boot-starter-web | Spring Boot 3.4 BOM | `ApplicationEventPublisher` is part of Spring core | Already declared |
| spring-context (transitive) | Spring Boot 3.4 BOM | `@TransactionalEventListener`, `ApplicationEvent` | Transitive from boot starters |

### No New Dependencies Required

The schedule-service `build.gradle.kts` already declares `spring-boot-starter-amqp`. No additions needed for Phase 13.

**Version verification:** All libraries are governed by Spring Boot 3.4 BOM already in use. No npm or version check needed.

---

## Architecture Patterns

### Recommended Package Structure

```
services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/
├── config/
│   ├── ClockConfig.java              (EXISTS — Clock bean, Moscow TZ)
│   ├── SchedulingConfig.java         (EXISTS — @EnableScheduling, @Profile("!test"))
│   └── EnumConverters.java           (EXISTS)
├── event/                            (NEW package — mirrors academic-service pattern)
│   ├── DomainEvent.java              (NEW — port from academic-service verbatim)
│   ├── DomainEventListener.java      (NEW — port from academic-service verbatim)
│   ├── RabbitConfig.java             (NEW — port from academic-service verbatim)
│   ├── LessonStartedEvent.java       (NEW)
│   ├── LessonClosedEvent.java        (NEW)
│   └── LessonCancelledEvent.java     (NEW)
├── lesson/
│   ├── LessonStatusTransitionJob.java  (NEW — cron job)
│   ├── LessonService.java            (MODIFY — add publishEvent calls)
│   ├── LessonWithItem.java           (EXISTS)
│   ├── repository/
│   │   └── LessonRepository.java     (MODIFY — add 2 new query methods)
│   └── entity/
│       └── Lesson.java               (EXISTS — closedAt field already present)
└── item/
    └── repository/
        └── ScheduleItemRepository.java  (EXISTS — used by job for ScheduleItem lookup)
```

### Pattern 1: DomainEvent Port (EVNT-01..04)

Port academic-service event classes to `ru.rutcampustrack.schedule.event` package. Content is identical except the package declaration.

**DomainEvent base class** (port verbatim, change package only):
```java
// Source: services/academic-service/academic-app/.../event/DomainEvent.java
package ru.rutcampustrack.schedule.event;

@JsonIgnoreProperties({"source", "timestamp"})
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public abstract class DomainEvent extends ApplicationEvent {
    @JsonProperty("event_type") private final String eventType;
    @JsonProperty("event_id")   private final UUID eventId;
    @JsonProperty("occurred_at") private final OffsetDateTime occurredAt;
    @JsonProperty("payload")    private final Object payload;

    protected DomainEvent(Object source, String eventType, Object payload) {
        super(source);
        this.eventType = eventType;
        this.eventId   = UUID.randomUUID();
        this.occurredAt = OffsetDateTime.now();
        this.payload   = payload;
    }
    // getters ...
}
```

**DomainEventListener** (port verbatim, change package only):
```java
// Source: services/academic-service/academic-app/.../event/DomainEventListener.java
package ru.rutcampustrack.schedule.event;

@Component
public class DomainEventListener {
    private static final String EXCHANGE = "rut-uit.events";
    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDomainEvent(DomainEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE, "", event);
    }
}
```

**RabbitConfig** (port verbatim, change package and bean name):
```java
// Source: services/academic-service/academic-app/.../event/RabbitConfig.java
package ru.rutcampustrack.schedule.event;

@Configuration
public class RabbitConfig {
    @Bean
    public FanoutExchange scheduleEventsExchange() {   // rename bean to avoid clash
        return new FanoutExchange("rut-uit.events", true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
```

### Pattern 2: Event Subclasses (EVNT-01, EVNT-02, EVNT-03)

Following academic-service style (nested `Payload` record):

```java
// LessonStartedEvent.java — payload matches event-schemas/lesson.started.json
public class LessonStartedEvent extends DomainEvent {
    public record Payload(
        @JsonProperty("lesson_id")    Long lessonId,
        @JsonProperty("group_id")     Long groupId,
        @JsonProperty("subject_id")   Long subjectId,
        @JsonProperty("teacher_id")   Long teacherId,
        @JsonProperty("lesson_number") Short lessonNumber,
        @JsonProperty("start_time")   String startTime,   // LocalTime.toString() = "HH:mm"
        @JsonProperty("end_time")     String endTime,
        @JsonProperty("room")         String room
    ) {}

    public LessonStartedEvent(Object source, Long lessonId, Long groupId,
                               Long subjectId, Long teacherId, Short lessonNumber,
                               LocalTime startTime, LocalTime endTime, String room) {
        super(source, "lesson.started", new Payload(
            lessonId, groupId, subjectId, teacherId, lessonNumber,
            startTime.toString(), endTime.toString(), room));
    }
}

// LessonClosedEvent.java — payload matches event-schemas/lesson.closed.json
public class LessonClosedEvent extends DomainEvent {
    public record Payload(
        @JsonProperty("lesson_id")  Long lessonId,
        @JsonProperty("group_id")   Long groupId,
        @JsonProperty("subject_id") Long subjectId
    ) {}

    public LessonClosedEvent(Object source, Long lessonId, Long groupId, Long subjectId) {
        super(source, "lesson.closed", new Payload(lessonId, groupId, subjectId));
    }
}

// LessonCancelledEvent.java — payload matches event-schemas/lesson.cancelled.json
public class LessonCancelledEvent extends DomainEvent {
    public record Payload(
        @JsonProperty("lesson_id")     Long lessonId,
        @JsonProperty("group_id")      Long groupId,
        @JsonProperty("subject_id")    Long subjectId,
        @JsonProperty("date")          String date,          // LocalDate.toString() = "yyyy-MM-dd"
        @JsonProperty("cancel_reason") String cancelReason
    ) {}

    public LessonCancelledEvent(Object source, Long lessonId, Long groupId,
                                 Long subjectId, LocalDate date, String cancelReason) {
        super(source, "lesson.cancelled", new Payload(
            lessonId, groupId, subjectId, date.toString(), cancelReason));
    }
}
```

### Pattern 3: LessonStatusTransitionJob (CRON-01, CRON-02, CRON-03)

```java
// ru.rutcampustrack.schedule.lesson.LessonStatusTransitionJob
@Component
public class LessonStatusTransitionJob {

    private final LessonRepository lessonRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // Constructor injection...

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void runTransitions() {
        LocalDateTime nowMoscow = LocalDateTime.now(clock);  // Moscow wall-clock time

        // Phase 1: planned -> active (CRON-01, EVNT-01)
        List<Lesson> toActivate = lessonRepository.findPlannedDueForActivation(nowMoscow);
        for (Lesson lesson : toActivate) {
            lesson.setStatus(LessonStatus.ACTIVE);
            ScheduleItem item = scheduleItemRepository.findById(lesson.getScheduleItemId()).orElseThrow();
            eventPublisher.publishEvent(new LessonStartedEvent(this,
                lesson.getId(), item.getGroupId(), item.getSubjectId(),
                item.getTeacherId(), item.getLessonNumber(),
                item.getStartTime(), item.getEndTime(), item.getRoom()));
        }
        lessonRepository.saveAll(toActivate);

        // Phase 2: active -> closed (CRON-02, EVNT-02)
        List<Lesson> toClose = lessonRepository.findActiveDueForClosure(nowMoscow);
        for (Lesson lesson : toClose) {
            lesson.setStatus(LessonStatus.CLOSED);
            lesson.setClosedAt(OffsetDateTime.now(clock));
            ScheduleItem item = scheduleItemRepository.findById(lesson.getScheduleItemId()).orElseThrow();
            eventPublisher.publishEvent(new LessonClosedEvent(this,
                lesson.getId(), item.getGroupId(), item.getSubjectId()));
        }
        lessonRepository.saveAll(toClose);

        log.info("Cron tick: activated={}, closed={}", toActivate.size(), toClose.size());
    }
}
```

**Key:** `@TransactionalEventListener(AFTER_COMMIT)` in `DomainEventListener` ensures events only reach RabbitMQ after `saveAll()` commits. The `@Transactional` on `runTransitions()` provides the transaction boundary.

### Pattern 4: Repository Queries for Time Comparisons (CRON-01, CRON-02)

The critical design detail: `date` is a PostgreSQL `DATE` and `start_time`/`end_time` are PostgreSQL `TIME` columns (no timezone stored — they represent Moscow local times per `hibernate.jdbc.time_zone=Europe/Moscow`). The cron job receives a `LocalDateTime` in Moscow time. Native SQL is required (same precedent as all other queries in `LessonRepository`).

```java
// In LessonRepository:

/**
 * Finds PLANNED lessons whose (date + start_time) <= nowMoscow.
 * Both date and start_time are Moscow-local values (no TZ shift needed).
 * The :now parameter is bound as LocalDateTime (Moscow wall clock).
 */
@Query(value = """
    SELECT l.* FROM lessons l
    JOIN schedule_items si ON si.id = l.schedule_item_id
    WHERE l.status::text = 'planned'
      AND (l.date + si.start_time) <= :now
    ORDER BY l.date, si.start_time
    """, nativeQuery = true)
List<Lesson> findPlannedDueForActivation(@Param("now") LocalDateTime now);

/**
 * Finds ACTIVE lessons whose (date + end_time + 5 minutes) <= nowMoscow (CRON-02).
 */
@Query(value = """
    SELECT l.* FROM lessons l
    JOIN schedule_items si ON si.id = l.schedule_item_id
    WHERE l.status::text = 'active'
      AND (l.date + si.end_time + INTERVAL '5 minutes') <= :now
    ORDER BY l.date, si.end_time
    """, nativeQuery = true)
List<Lesson> findActiveDueForClosure(@Param("now") LocalDateTime now);
```

**Notes:**
- `(date + time)` in PostgreSQL produces a `timestamp without time zone` — correct for comparison with a Moscow `LocalDateTime`.
- The `JOIN schedule_items` in the native query avoids the N+1 problem of loading items separately per lesson. However, JPA `nativeQuery = true` returns `Lesson` entities only (not the joined `ScheduleItem` fields). The cron job still needs to load ScheduleItems for event payload construction, so do so via `scheduleItemRepository.findById()` inside the loop (the set is small per tick).
- Alternatively: a simpler approach using `findByStatusIn` + Java-side filtering works fine given the expected small batch size (few dozen at most per tick). Prefer the JOIN SQL approach for correctness under load.

### Pattern 5: Cancel Event Publishing in LessonService (EVNT-03)

Add `ApplicationEventPublisher` to `LessonService` constructor and inject it. After saving a cancelled lesson, publish the event:

```java
// In LessonService.cancelLesson():
lesson.setStatus(LessonStatus.CANCELLED);
lesson.setCancelReason(request.reason());
Lesson saved = lessonRepository.save(lesson);
eventPublisher.publishEvent(new LessonCancelledEvent(this,
    saved.getId(), item.getGroupId(), item.getSubjectId(),
    saved.getDate(), saved.getCancelReason()));
return new LessonWithItem(saved, lwi.scheduleItem());

// In LessonService.massCancelLessons():
for (Lesson l : toCancel) {
    l.setStatus(LessonStatus.CANCELLED);
    l.setCancelReason(request.reason());
}
lessonRepository.saveAll(toCancel);
// Publish one event per cancelled lesson
for (Lesson l : toCancel) {
    ScheduleItem item = itemMap.get(l.getScheduleItemId());
    eventPublisher.publishEvent(new LessonCancelledEvent(this,
        l.getId(), item.getGroupId(), item.getSubjectId(),
        l.getDate(), l.getCancelReason()));
}
```

Note: `massCancelLessons()` already loads `ScheduleItem` list into `items` and builds an `itemMap`. The event publishing loop uses that same map — no extra queries needed.

Wait — `massCancelLessons()` does NOT currently load a `Map<Long, ScheduleItem>`. It only loads `List<Long> itemIds`. The method needs access to `groupId` and `subjectId` for the event payload. Two options:
- Option A: Build a `Map<Long, ScheduleItem>` from the already-loaded `items` list (same pattern as `getLessonsForGroup`). This is clean and zero extra queries.
- Option B: Use `request.groupId()` for groupId (already available). For `subjectId`, a lookup is needed. Option A is cleaner.

**Recommendation:** Add `Map<Long, ScheduleItem> itemMap = items.stream().collect(Collectors.toMap(...))` to `massCancelLessons()` before the event publishing loop.

### Anti-Patterns to Avoid

- **Direct RabbitTemplate in services:** Services must use `ApplicationEventPublisher.publishEvent()` — never inject `RabbitTemplate` directly. `DomainEventListener` is the sole RabbitMQ bridge.
- **channelTransacted=true on RabbitTemplate:** Causes message loss with `AFTER_COMMIT` pattern. The academic-service `RabbitConfig` comment explicitly warns about this. Port the bean without setting `channelTransacted`.
- **`@Scheduled` firing in tests:** `SchedulingConfig` has `@Profile("!test")` — never remove this guard. Integration tests call the job method directly.
- **Querying without `status::text` cast:** All existing native queries use `status::text` cast. The new time-based queries also use `l.status::text = 'planned'` — this is mandatory for PostgreSQL enum vs varchar compatibility.
- **Using ZonedDateTime as JPA parameter:** JPA native queries do not bind `ZonedDateTime` well. Use `LocalDateTime` (Moscow wall-clock time extracted via `LocalDateTime.now(clock)`) as the bound parameter. The DB column `(date + start_time)` produces a timezone-naive timestamp, so the comparison works correctly.
- **New ObjectMapper in RabbitConfig:** Must inject the shared Spring Boot `ObjectMapper` bean (which has `JavaTimeModule` from auto-configuration). Creating `new ObjectMapper()` breaks date serialization and loses ISO-8601 format.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Event-after-commit guarantee | Custom flag/queue | `@TransactionalEventListener(AFTER_COMMIT)` | Spring handles rollback detection; proven in academic-service |
| RabbitMQ JSON serialization | Custom serializer | `Jackson2JsonMessageConverter(objectMapper)` | Shares project ObjectMapper config (JavaTimeModule etc.) |
| Cron overlap prevention | Synchronized methods | `fixedDelay` (not `fixedRate`) | fixedDelay waits for previous execution to complete before starting next |
| Timezone-correct time | `LocalTime.now()` | `LocalDateTime.now(clock)` with injected Clock | Clock is testable; LocalTime.now() hard-codes system TZ |

---

## Common Pitfalls

### Pitfall 1: channelTransacted=true with AFTER_COMMIT
**What goes wrong:** Setting `rabbitTemplate.setChannelTransacted(true)` causes messages to be enrolled in the JPA transaction. When `AFTER_COMMIT` fires, the Spring AMQP transaction is already committed — the message is never actually sent.
**Why it happens:** Developers assume "transacted = safe". The AFTER_COMMIT pattern is already outside the DB transaction.
**How to avoid:** Do NOT set `channelTransacted`. The academic-service `RabbitConfig` has an explicit comment about this.
**Warning signs:** Events missing from RabbitMQ even though DB changes committed.

### Pitfall 2: @Scheduled fires during integration tests
**What goes wrong:** Integration test sets up data, cron fires mid-test changing statuses, assertions fail intermittently.
**Why it happens:** Forgetting that `SchedulingConfig` has `@Profile("!test")` — which only works if the test uses `@ActiveProfiles("test")`.
**How to avoid:** `AbstractScheduleIntegrationTest` already has `@ActiveProfiles("test")`. All integration tests must extend it. The `LessonStatusTransitionJob` integration test calls `job.runTransitions()` directly.
**Warning signs:** Tests pass in isolation but fail when the full suite runs.

### Pitfall 3: N+1 queries in cron job for ScheduleItem
**What goes wrong:** The cron loads N lessons then issues N `findById` calls for ScheduleItems — one per lesson. Under load (hundreds of lessons per tick at semester start), this becomes a bottleneck.
**Why it happens:** Lesson entity stores only `scheduleItemId` (Long), not a `@ManyToOne`. This is project convention — correct for cross-service isolation, but means no JPA eager/lazy load.
**How to avoid:** For Phase 13, the per-tick batch is expected to be tiny (≤ 10 lessons per minute realistically). `findById` inside the loop is acceptable. Document the future optimization path (batch fetch by IDs) if load increases.
**Warning signs:** Slow cron ticks logged; high DB query count in test assertions.

### Pitfall 4: LocalDateTime vs ZonedDateTime mismatch in queries
**What goes wrong:** Passing `ZonedDateTime` as JPA query parameter for a native query against PostgreSQL `timestamp without time zone` columns causes JDBC type mismatch errors or silent TZ conversion.
**Why it happens:** `(date + start_time)` in PostgreSQL is `timestamp without time zone`. `ZonedDateTime` binding attempts to send timezone info that the column cannot accept.
**How to avoid:** Extract `LocalDateTime nowMoscow = LocalDateTime.now(clock)` (the clock is already in Moscow TZ, so `LocalDateTime.now(moscowClock)` gives Moscow wall time as a timezone-naive value — matching the DB column semantics).
**Warning signs:** `PSQLException: ERROR: cannot cast type timestamp with time zone to timestamp without time zone`.

### Pitfall 5: massCancelLessons event publishing missing ScheduleItem data
**What goes wrong:** `massCancelLessons()` uses `request.groupId()` for groupId (available), but needs `subjectId` which is on `ScheduleItem`. Publishing an event with null `subjectId` violates the event schema (`required` field).
**Why it happens:** The current method only stores `List<Long> itemIds` — it doesn't keep the full `ScheduleItem` objects.
**How to avoid:** Change `massCancelLessons()` to build a `Map<Long, ScheduleItem>` from the already-loaded `items` list before the save loop (zero extra queries).
**Warning signs:** `lesson.cancelled` events missing `subject_id` in payload.

---

## Code Examples

Verified from existing codebase:

### Existing Native Query Pattern (LessonRepository)
```java
// Source: LessonRepository.java (existing)
@Query(value = "SELECT * FROM lessons WHERE status::text IN :statuses AND date < :date",
        nativeQuery = true)
List<Lesson> findByStatusInAndDateBefore(
        @Param("statuses") List<String> statuses,
        @Param("date") LocalDate date);
```

### Existing ApplicationEvent Publishing (academic-service reference)
```java
// Source: academic-service GroupService (pattern to replicate)
applicationEventPublisher.publishEvent(new GroupUpdatedEvent(this, groupId));
// → DomainEventListener.onDomainEvent() fires AFTER_COMMIT
// → rabbitTemplate.convertAndSend("rut-uit.events", "", event)
```

### Clock Bean Usage Pattern (already in schedule-service)
```java
// Source: ClockConfig.java (existing)
@Bean
public Clock clock() {
    return Clock.system(ZoneId.of("Europe/Moscow"));
}

// Usage in cron job:
LocalDateTime nowMoscow = LocalDateTime.now(clock);

// In integration test:
@MockitoBean Clock clock;
// In @BeforeEach:
when(clock.getZone()).thenReturn(ZoneId.of("Europe/Moscow"));
when(clock.instant()).thenReturn(fixedInstant);
```

### PostgreSQL Date+Time Arithmetic
```sql
-- Produces timestamp without time zone — matches LocalDateTime parameter
SELECT (date + start_time) FROM schedule_items LIMIT 1;
-- Result type: timestamp without time zone

-- With interval offset (CRON-02):
SELECT (date + end_time + INTERVAL '5 minutes') FROM lessons l
JOIN schedule_items si ON si.id = l.schedule_item_id;
```

### ScheduleItem Fields Available for Event Payloads
From `ScheduleItem.java` (existing):
- `getGroupId()` → Long — for all three event types
- `getSubjectId()` → Long — for lesson.started, lesson.closed, lesson.cancelled
- `getTeacherId()` → Long — for lesson.started
- `getLessonNumber()` → Short — for lesson.started
- `getStartTime()` → LocalTime — for lesson.started
- `getEndTime()` → LocalTime — for lesson.started
- `getRoom()` → String — for lesson.started (optional field in schema)

---

## Environment Availability

Step 2.6: SKIPPED — Phase 13 is purely code changes extending existing schedule-service. No new external dependencies beyond RabbitMQ which is already in docker-compose.yml and already declared in build.gradle.kts.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL 16) |
| Config file | `services/schedule-service/schedule-app/src/test/resources/application-test.yml` |
| Quick run command | `./gradlew.bat :services:schedule-service:schedule-app:test --tests "ru.rutcampustrack.schedule.lesson.LessonStatusTransitionJobTest"` |
| Full suite command | `./gradlew.bat :services:schedule-service:schedule-app:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CRON-01 | planned→active when start_time <= now | integration | `...test --tests "*.LessonStatusTransitionJobTest#transitionsPlannedToActive"` | ❌ Wave 0 |
| CRON-02 | active→closed when end_time+5min <= now | integration | `...test --tests "*.LessonStatusTransitionJobTest#transitionsActiveToClosedAfterGrace"` | ❌ Wave 0 |
| CRON-03 | past-due lessons caught up on restart | integration | `...test --tests "*.LessonStatusTransitionJobTest#catchesUpMissedTransitionsOnRestart"` | ❌ Wave 0 |
| EVNT-01 | lesson.started published to RabbitMQ after activation | integration | `...test --tests "*.LessonStatusTransitionJobTest#publishesLessonStartedEvent"` | ❌ Wave 0 |
| EVNT-02 | lesson.closed published after closure | integration | `...test --tests "*.LessonStatusTransitionJobTest#publishesLessonClosedEvent"` | ❌ Wave 0 |
| EVNT-03 | lesson.cancelled published from cancel/massCancelLessons | integration | `...test --tests "*.LessonCancelEventTest#publishesCancelledEventOnCancel"` | ❌ Wave 0 |
| EVNT-04 | events only fire after DB commit (AFTER_COMMIT) | unit | `...test --tests "*.DomainEventListenerTest#listenerIsAfterCommit"` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew.bat :services:schedule-service:schedule-app:test --tests "ru.rutcampustrack.schedule.lesson.LessonStatusTransitionJobTest"`
- **Per wave merge:** `./gradlew.bat :services:schedule-service:schedule-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/.../lesson/LessonStatusTransitionJobTest.java` — covers CRON-01, CRON-02, CRON-03, EVNT-01, EVNT-02
- [ ] `src/test/.../integration/LessonCancelEventTest.java` — covers EVNT-03
- [ ] `src/test/.../event/DomainEventListenerTest.java` — covers EVNT-04 (unit test verifying `@TransactionalEventListener` annotation)

**Test strategy for cron job:**
- Extend `AbstractScheduleIntegrationTest` (gets `@MockitoBean RabbitTemplate` and Testcontainers Postgres for free)
- Add `@MockitoBean Clock clock` to inject deterministic time
- In `@BeforeEach`: configure `clock` to return a fixed instant, insert lessons with appropriate dates
- Call `job.runTransitions()` directly (not via scheduler — scheduler is disabled by `@Profile("!test")`)
- Assert on `lessonRepository.findAll()` for status changes
- Assert on `Mockito.verify(rabbitTemplate).convertAndSend(eq("rut-uit.events"), eq(""), any(LessonStartedEvent.class))`

---

## Open Questions

1. **ScheduleItem JOIN in native query vs separate findById calls**
   - What we know: The JOIN query returns only `Lesson` entities (nativeQuery = true with entity return type). ScheduleItem data is needed for event construction.
   - What's unclear: Whether loading ScheduleItems in a loop is acceptable or if a `Map` pre-load is better.
   - Recommendation: For Phase 13, loop `findById` is acceptable (tiny batch sizes). Pre-build an `itemId → ScheduleItem` map only if the same ScheduleItem could appear for multiple lessons in one tick (same recurring slot for different dates — yes, this happens). Use `scheduleItemRepository.findAllById(lesson ids → item ids)` to batch-load.

2. **Event ordering guarantee for massCancelLessons**
   - What we know: `saveAll()` commits all cancellations together, then `AFTER_COMMIT` fires. Events are published in the order the loop iterates.
   - What's unclear: Whether downstream consumers depend on event ordering (they shouldn't for cancelled events).
   - Recommendation: No action needed. Document that event ordering is best-effort.

---

## Sources

### Primary (HIGH confidence)

- `services/academic-service/academic-app/.../event/DomainEvent.java` — Reference implementation, read directly
- `services/academic-service/academic-app/.../event/DomainEventListener.java` — Reference implementation, read directly
- `services/academic-service/academic-app/.../event/RabbitConfig.java` — Reference implementation with pitfall comments, read directly
- `services/schedule-service/schedule-app/src/main/java/.../config/ClockConfig.java` — Existing Clock bean, read directly
- `services/schedule-service/schedule-app/src/main/java/.../config/SchedulingConfig.java` — Existing scheduling guard, read directly
- `services/schedule-service/schedule-app/src/main/java/.../lesson/LessonService.java` — Existing service to extend, read directly
- `services/schedule-service/schedule-app/src/main/java/.../lesson/repository/LessonRepository.java` — Existing native query patterns, read directly
- `services/schedule-service/schedule-app/build.gradle.kts` — Confirms spring-boot-starter-amqp already declared
- `event-schemas/lesson.started.json`, `lesson.closed.json`, `lesson.cancelled.json` — Payload contracts, read directly
- `services/schedule-service/schedule-app/src/test/.../AbstractScheduleIntegrationTest.java` — Test infrastructure, read directly

### Secondary (MEDIUM confidence)

- PostgreSQL `(date + time)` timestamp arithmetic — standard PostgreSQL behavior, consistent with V2 migration pattern (implicit casts) already in project

### Tertiary (LOW confidence)

- None — all findings backed by direct code inspection

---

## Project Constraints (from CLAUDE.md)

| Directive | Impact on Phase 13 |
|-----------|-------------------|
| Contract-first: controllers implement contract interface, mappings only in interface | Not applicable — no new REST endpoints in Phase 13 |
| No Lombok in `*-api-contract` modules | Not applicable — all new code is in `schedule-app` |
| Lombok allowed in `*-app` | `LessonStatusTransitionJob` may use `@Slf4j` for logger |
| Enums: UPPER_CASE in Java, lowercase in PG, via LowercaseEnumConverter with autoApply=true | Native queries must use `status::text = 'planned'` (string comparison, not enum) |
| Never use `@Enumerated(EnumType.ORDINAL)` | Not applicable — existing converters are already correct |
| DB values in lowercase | Event type strings: `"lesson.started"`, `"lesson.closed"`, `"lesson.cancelled"` (dot-separated, lowercase per CLAUDE.md naming convention `{domain}.{action}`) |
| Migrations via Flyway, `ddl-auto: validate` | No schema changes needed — `closed_at` column already exists in `lessons` table (verified in Lesson.java and database-schema.md) |
| Soft delete for users | Not applicable |
| `@ControllerAdvice` for errors | Not applicable — cron job errors logged, not returned to HTTP clients |
| Event types format: `{domain}.{action}` | `lesson.started`, `lesson.closed`, `lesson.cancelled` — compliant |

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all dependencies already present, verified in build.gradle.kts
- Architecture: HIGH — direct port of verified academic-service pattern; existing schedule-service code read in full
- Pitfalls: HIGH — pitfalls documented in academic-service RabbitConfig comments and cross-verified with existing code
- Repository queries: MEDIUM-HIGH — PostgreSQL date+time arithmetic is standard; the exact JPQL vs native approach requires testing (native chosen to match established project pattern)

**Research date:** 2026-04-03
**Valid until:** 2026-05-03 (stable domain — Spring Boot 3.4 + RabbitMQ patterns are stable)
