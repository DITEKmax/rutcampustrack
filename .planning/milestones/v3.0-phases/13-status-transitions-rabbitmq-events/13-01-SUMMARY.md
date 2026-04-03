---
phase: 13-status-transitions-rabbitmq-events
plan: "01"
subsystem: schedule-service
tags: [rabbitmq, events, domain-events, lesson-lifecycle, infrastructure]
dependency_graph:
  requires: []
  provides: [event-infrastructure-schedule, lesson-cancelled-events, cron-repository-queries]
  affects: [schedule-service, plan-02-cron-job]
tech_stack:
  added: [RabbitMQ event publishing, TransactionalEventListener]
  patterns: [DomainEvent/DomainEventListener/RabbitConfig trinity, AFTER_COMMIT event bridge]
key_files:
  created:
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/DomainEvent.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/DomainEventListener.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/RabbitConfig.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/LessonStartedEvent.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/LessonClosedEvent.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/LessonCancelledEvent.java
  modified:
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java
    - services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java
decisions:
  - "scheduleEventsExchange() bean name avoids Spring name clash with academicEventsExchange in shared test context"
  - "@TransactionalEventListener(AFTER_COMMIT) ensures no event published on transaction rollback (EVNT-04)"
  - "LessonService.massCancelLessons() builds Map<Long, ScheduleItem> from already-loaded items list to avoid N+1 queries"
metrics:
  duration_minutes: 3
  tasks_completed: 2
  files_created: 6
  files_modified: 2
  completed_date: "2026-04-03"
requirements_satisfied: [EVNT-03, EVNT-04]
---

# Phase 13 Plan 01: Event Infrastructure + Lesson Cancel Events Summary

RabbitMQ event infrastructure ported from academic-service to schedule-service, with three lesson event subclasses and LessonCancelledEvent publishing wired into cancel/mass-cancel operations.

## What Was Built

### Task 1: Event infrastructure and lesson event subclasses (commit 1b99f13)

Six new files in `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/`:

- **DomainEvent.java** — Abstract ApplicationEvent base with envelope fields (event_type, event_id, occurred_at, payload). Identical to academic-service except package.
- **DomainEventListener.java** — `@TransactionalEventListener(phase = AFTER_COMMIT)` bridge that forwards events to `rut-uit.events` fanout exchange after DB transaction commits.
- **RabbitConfig.java** — Declares durable FanoutExchange, Jackson2JsonMessageConverter (using shared Spring ObjectMapper), RabbitTemplate. Bean named `scheduleEventsExchange()` to avoid name clash.
- **LessonStartedEvent.java** — 8-field payload matching `event-schemas/lesson.started.json` (lesson_id, group_id, subject_id, teacher_id, lesson_number, start_time, end_time, room).
- **LessonClosedEvent.java** — 3-field payload matching `event-schemas/lesson.closed.json` (lesson_id, group_id, subject_id).
- **LessonCancelledEvent.java** — 5-field payload matching `event-schemas/lesson.cancelled.json` (lesson_id, group_id, subject_id, date, cancel_reason).

### Task 2: Repository queries + LessonService event wiring (commit c75b994)

**LessonRepository.java** — Two new native query methods for cron job (Plan 02):
- `findPlannedDueForActivation(LocalDateTime now)` — finds PLANNED lessons where `(date + start_time) <= now`. Uses `status::text = 'planned'` cast pattern.
- `findActiveDueForClosure(LocalDateTime now)` — finds ACTIVE lessons where `(date + end_time + INTERVAL '5 minutes') <= now`.

**LessonService.java** — Three changes:
- Constructor now injects `ApplicationEventPublisher eventPublisher` (5th parameter).
- `cancelLesson()` publishes `LessonCancelledEvent` after `lessonRepository.save()`.
- `massCancelLessons()` builds `Map<Long, ScheduleItem> itemMap` from already-loaded items and publishes `LessonCancelledEvent` per cancelled lesson in a loop.

## Verification

`./gradlew.bat :services:schedule-service:schedule-app:compileJava` — BUILD SUCCESSFUL after each task.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all event publishing is fully wired. The `LessonStartedEvent` and `LessonClosedEvent` are created and will be published by the cron job (Plan 02).

## Self-Check: PASSED

Files verified:
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/DomainEvent.java` — FOUND
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/DomainEventListener.java` — FOUND
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/RabbitConfig.java` — FOUND
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/LessonStartedEvent.java` — FOUND
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/LessonClosedEvent.java` — FOUND
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/LessonCancelledEvent.java` — FOUND

Commits verified:
- `1b99f13` — FOUND (feat(13-01): port event infrastructure and create lesson event subclasses)
- `c75b994` — FOUND (feat(13-01): add repository queries and wire cancel event publishing in LessonService)
