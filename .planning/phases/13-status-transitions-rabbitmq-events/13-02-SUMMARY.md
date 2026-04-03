---
phase: 13-status-transitions-rabbitmq-events
plan: "02"
subsystem: schedule-service
tags: [cron, lesson-lifecycle, status-transitions, rabbitmq, integration-tests]
dependency_graph:
  requires: [event-infrastructure-schedule, cron-repository-queries]
  provides: [lesson-cron-job, lesson-transition-tests, cancel-event-tests]
  affects: [schedule-service, attendance-service-consumers]
tech_stack:
  added: [@Scheduled fixedDelay cron, Clock-based LocalDateTime.now(clock)]
  patterns: [two-phase cron transition, fixedDelay not fixedRate, MockitoBean Clock for deterministic time]
key_files:
  created:
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJobTest.java
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonCancelEventTest.java
  modified:
    - services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/AbstractScheduleIntegrationTest.java
decisions:
  - "fixedDelay = 60_000 over fixedRate — prevents tick overlap if previous run exceeds 1 min"
  - "Two-phase in single @Transactional: phase 1 saveAll activates lessons, phase 2 query sees them as ACTIVE and closes them — no separate catch-up needed (CRON-03)"
  - "Tests NOT @Transactional — allows commit so @TransactionalEventListener(AFTER_COMMIT) fires for event verification"
  - "(Object) argThat(...) cast disambiguates RabbitTemplate.convertAndSend overload (String,String,Object vs String,Object,MessagePostProcessor)"
  - "AbstractScheduleIntegrationTest.rabbitTemplate made protected for cross-package test access"
metrics:
  duration_minutes: 5
  tasks_completed: 2
  files_created: 3
  files_modified: 1
  completed_date: "2026-04-04"
requirements_satisfied: [CRON-01, CRON-02, CRON-03, EVNT-01, EVNT-02]
---

# Phase 13 Plan 02: LessonStatusTransitionJob + Cron Tests Summary

Cron job implementing two-phase lesson status transitions (planned->active->closed) with RabbitMQ event publishing, backed by 7 integration tests covering all CRON and EVNT requirements.

## What Was Built

### Task 1: LessonStatusTransitionJob cron component (commit 685d5e7)

`LessonStatusTransitionJob` in `ru.rutcampustrack.schedule.lesson`:

- `@Scheduled(fixedDelay = 60_000)` — fires every 60 seconds after previous run completes.
- `@Transactional` on `runTransitions()` — single DB transaction covering both phases.
- Phase 1 (CRON-01, EVNT-01): `lessonRepository.findPlannedDueForActivation(nowMoscow)` → sets `ACTIVE` → publishes `LessonStartedEvent` per lesson → `saveAll`.
- Phase 2 (CRON-02, EVNT-02): `lessonRepository.findActiveDueForClosure(nowMoscow)` → sets `CLOSED` + `closedAt = OffsetDateTime.now(clock)` → publishes `LessonClosedEvent` per lesson → `saveAll`.
- `LocalDateTime.now(clock)` uses the injected `Clock.system("Europe/Moscow")` bean for deterministic Moscow wall-clock time.
- No separate catch-up logic needed (CRON-03): the `<= nowMoscow` queries return ALL past-due lessons regardless of date, naturally catching missed transitions after restart.
- Key note: phase 1 `saveAll` commits within the transaction, so phase 2 query sees the newly-ACTIVE lessons and closes them in the same tick when both deadlines have passed.

### Task 2: Integration tests for cron transitions and cancel events (commit 4f5dec4)

**LessonStatusTransitionJobTest** (6 tests, `ru.rutcampustrack.schedule.lesson`):

1. `transitionsPlannedToActive` (CRON-01/EVNT-01): PLANNED → ACTIVE at clock 10:00, startTime 08:30. Verifies `LessonStartedEvent` forwarded to RabbitMQ.
2. `transitionsActiveToClosedAfterGrace` (CRON-02/EVNT-02): ACTIVE → CLOSED at clock 10:11, endTime 10:05 (grace = 10:10). Verifies `closedAt` set and `LessonClosedEvent` forwarded.
3. `doesNotTransitionActiveBeforeGracePeriod`: ACTIVE stays ACTIVE at 10:09 (before 10:10 deadline).
4. `catchesUpMissedTransitionsOnRestart` (CRON-03): 3 PLANNED lessons from Apr 1-3 with clock at 12:00 — all 3 transition PLANNED→ACTIVE→CLOSED in a single tick. Verifies `atLeast(3)` calls for each event type.
5. `doesNotTransitionFutureLesson`: Tomorrow's PLANNED lesson stays PLANNED; `never()` on RabbitMQ.
6. `doesNotTransitionCancelledLesson`: CANCELLED lesson at past start_time stays CANCELLED.

**LessonCancelEventTest** (1 test, `ru.rutcampustrack.schedule.integration`):

1. `publishesCancelledEventOnCancel` (EVNT-03): Calls `lessonService.cancelLesson()` with ADMIN role mock, verifies `LessonCancelledEvent` with `event_type="lesson.cancelled"` forwarded to RabbitMQ.

**AbstractScheduleIntegrationTest change**: `rabbitTemplate` field made `protected` to allow access from `LessonStatusTransitionJobTest` which is in a different package.

**Key test patterns**:
- `@MockitoBean Clock clock` replaces the real `ClockConfig` bean for deterministic time control per test.
- Tests are NOT `@Transactional` — transaction must commit for `@TransactionalEventListener(AFTER_COMMIT)` to fire RabbitMQ forward.
- `(Object) argThat(...)` cast resolves javac ambiguity between `convertAndSend(String, String, Object)` and `convertAndSend(String, Object, MessagePostProcessor)`.

## Verification

`./gradlew.bat :services:schedule-service:schedule-app:test` — all tests pass including existing suite.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] RabbitTemplate convertAndSend overload ambiguity**
- **Found during:** Task 2 (first compile attempt)
- **Issue:** Java compiler couldn't distinguish `convertAndSend(String, String, Object)` from `convertAndSend(String, Object, MessagePostProcessor)` when using Mockito `anyString()` and `argThat()` matchers
- **Fix:** Added `(Object)` explicit cast on the third argument to force compiler to select 3-String overload: `(Object) argThat(e -> e instanceof LessonStartedEvent)`
- **Files modified:** `LessonStatusTransitionJobTest.java`, `LessonCancelEventTest.java`

**2. [Rule 2 - Missing Critical] AbstractScheduleIntegrationTest rabbitTemplate visibility**
- **Found during:** Task 2 (first compile attempt)
- **Issue:** `rabbitTemplate` was package-private in `AbstractScheduleIntegrationTest` (package `integration`); `LessonStatusTransitionJobTest` is in package `lesson` — cross-package access error
- **Fix:** Added `protected` modifier to `rabbitTemplate` field
- **Files modified:** `AbstractScheduleIntegrationTest.java`
- **Commit:** 4f5dec4

## Known Stubs

None — cron job is fully implemented and all events are published. RabbitMQ message routing is verified via mocked `RabbitTemplate`.

## Self-Check: PASSED

Files verified:
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java` — FOUND
- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJobTest.java` — FOUND
- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonCancelEventTest.java` — FOUND

Commits verified:
- `685d5e7` — feat(13-02): create LessonStatusTransitionJob cron component
- `4f5dec4` — test(13-02): integration tests for cron transitions and cancel events
