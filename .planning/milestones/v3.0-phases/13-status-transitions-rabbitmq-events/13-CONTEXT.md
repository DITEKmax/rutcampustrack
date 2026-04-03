# Phase 13: Status Transitions + RabbitMQ Events - Context

**Gathered:** 2026-04-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Cron-based lesson status transitions (planned→active→closed) and RabbitMQ event publishing (lesson.started, lesson.closed, lesson.cancelled). The cron runs every minute, compares lesson times against Moscow TZ current time, and transitions statuses. Each transition publishes a domain event via ApplicationEventPublisher → DomainEventListener → RabbitMQ fanout exchange.

No gRPC server (Phase 14), no attendance integration (v4.0), no Redis caching.

</domain>

<decisions>
## Implementation Decisions

### Cron Strategy
- **D-01:** Single `@Scheduled(fixedDelay = 60000)` method in a `LessonStatusTransitionJob` class. Not a cron expression — `fixedDelay` ensures no overlap if a run takes longer than 1 minute.
- **D-02:** Within each tick: first query transitions all `planned→active` (WHERE status=PLANNED AND date+startTime <= now), then transitions all `active→closed` (WHERE status=ACTIVE AND date+endTime+5min <= now). Both in one `@Transactional` method.
- **D-03:** Load entities via repository query, set status in Java, `saveAll()`. Required because each transition must publish a Spring `ApplicationEvent` per lesson for RabbitMQ forwarding.

### Missed Transitions Recovery (CRON-03)
- **D-04:** No separate catch-up job. The cron query is time-based (`<= now`), so after a restart ALL past-due lessons naturally match. Transitions fire in order (oldest first), events publish for each.
- **D-05:** No lookback limit. Any lesson with incorrect status that should have transitioned gets caught up regardless of how old it is. With 500-5000 students, even a week of backlog is manageable.

### Event Publishing
- **D-06:** `lesson.cancelled` event published directly in `LessonService.cancel()` (and mass-cancel) using `ApplicationEventPublisher`. This is a user-initiated action, not cron-triggered.
- **D-07:** `lesson.started` and `lesson.closed` events published from the cron job after each status transition. Same `ApplicationEventPublisher` → `DomainEventListener` pattern.
- **D-08:** All events go to the same `rut-uit.events` fanout exchange used by academic-service. Single exchange for all domain events across all services.
- **D-09:** Full payload per event-schemas/*.json definitions. `lesson.started` includes lesson_id, group_id, subject_id, teacher_id, lesson_number, start_time, end_time, room. `lesson.closed` includes lesson_id, group_id, subject_id. `lesson.cancelled` includes lesson_id, group_id, subject_id, date, cancel_reason.
- **D-10:** `@TransactionalEventListener(phase = AFTER_COMMIT)` pattern — identical to academic-service's `DomainEventListener`. Events only publish after DB commit succeeds (EVNT-04).

### Timezone Handling
- **D-11:** Cron compares `ZonedDateTime.of(lesson.date, scheduleItem.startTime, ZoneId.of("Europe/Moscow"))` against `ZonedDateTime.now(clock)` where clock is the injected Moscow-zone Clock bean.
- **D-12:** `ClockConfig` bean provides `Clock.system(ZoneId.of("Europe/Moscow"))` as a Spring `@Bean`. Cron injects `Clock`. Integration tests inject `Clock.fixed(...)` for deterministic time control.

### Claude's Discretion
- Package placement for `LessonStatusTransitionJob` (e.g., `ru.rutcampustrack.schedule.lesson` or `ru.rutcampustrack.schedule.cron`)
- DomainEvent subclass design: `LessonStartedEvent`, `LessonClosedEvent`, `LessonCancelledEvent` — record-based payloads or nested classes
- RabbitConfig for schedule-service — can reference same exchange declaration or just use exchange name string
- Repository query design: JPQL vs native SQL for fetching lessons by status + time criteria
- Whether cron fetches ScheduleItems separately or via JOIN in the lesson query
- Integration test strategy for the cron job (using Clock.fixed to simulate time progression)
- Logging strategy for transition counts per cron tick
- `closedAt` field population: set to `OffsetDateTime.now()` when transitioning to CLOSED

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Event Schemas (payload contracts)
- `event-schemas/lesson.started.json` — lesson.started event payload: lesson_id, group_id, subject_id, teacher_id, lesson_number, start_time, end_time, room
- `event-schemas/lesson.closed.json` — lesson.closed event payload: lesson_id, group_id, subject_id
- `event-schemas/lesson.cancelled.json` — lesson.cancelled event payload: lesson_id, group_id, subject_id, date, cancel_reason

### Existing Event Pattern (reference implementation)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEvent.java` — Abstract event base class with envelope fields (event_type, event_id, occurred_at, payload)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEventListener.java` — @TransactionalEventListener(AFTER_COMMIT) bridge to RabbitMQ
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/RabbitConfig.java` — FanoutExchange + Jackson2JsonMessageConverter + RabbitTemplate setup

### Schedule Service Code (to extend)
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/entity/Lesson.java` — Entity with status, date, closedAt, scheduleItemId
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java` — cancel/restore/mass-cancel methods (add event publishing here)
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java` — Repository to add status transition queries
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/entity/ScheduleItem.java` — startTime, endTime (LocalTime), lessonNumber, room fields
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/config/SchedulingConfig.java` — @EnableScheduling already in place
- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/AbstractScheduleIntegrationTest.java` — Base test class with @MockitoBean RabbitTemplate

### Architecture & Conventions
- `CLAUDE.md` — Coding rules, enum handling (lowercase in PG), contract-first, event types format ({domain}.{action})
- `docs/architecture.md` — Service map, RabbitMQ fanout exchange pattern, inter-service communication
- `docs/database-schema.md` — lessons table: status, date, closed_at columns; schedule_items: start_time, end_time

### Requirements
- `.planning/REQUIREMENTS.md` — CRON-01..03, EVNT-01..04 definitions

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DomainEvent` abstract class in academic-service: port to schedule-service with same envelope structure
- `DomainEventListener` in academic-service: port to schedule-service — identical pattern, just different package
- `RabbitConfig` in academic-service: port with same exchange name `rut-uit.events`
- `SchedulingConfig` in schedule-service: already enables `@Scheduled` with `@Profile("!test")`
- `AbstractScheduleIntegrationTest`: already mocks `RabbitTemplate`

### Established Patterns
- Services publish events via `ApplicationEventPublisher.publishEvent(domainEvent)` — no direct RabbitTemplate usage in services
- `@TransactionalEventListener(AFTER_COMMIT)` ensures events only fire after successful DB commit
- Fanout exchange `rut-uit.events` — all services share one exchange
- Jackson2JsonMessageConverter with Spring Boot's shared ObjectMapper (NOT cache-specific ObjectMapper)

### Integration Points
- `LessonService.cancel()` and `massCancelLessons()` — add `applicationEventPublisher.publishEvent(new LessonCancelledEvent(...))`
- `LessonRepository` — add query methods for status transition: find by status + date/time criteria
- New `LessonStatusTransitionJob` class — the cron job that drives planned→active→closed transitions

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. Follow academic-service event patterns exactly.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 13-status-transitions-rabbitmq-events*
*Context gathered: 2026-04-02*
