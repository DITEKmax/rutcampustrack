---
phase: 20-shared-infrastructure
plan: "01"
subsystem: notification-web
tags: [rabbitmq, actuator, spring-amqp, notification-web]
dependency_graph:
  requires: []
  provides: [notification-web RabbitMQ queue binding, notification-web DLQ, actuator health endpoint]
  affects: [notification-web]
tech_stack:
  added: [spring-boot-starter-actuator]
  patterns: [fanout exchange + DLQ consumer pattern (mirrored from Attendance Service)]
key_files:
  created:
    - services/notification-web/src/main/java/ru/rutcampustrack/notification/config/RabbitConfig.java
    - services/notification-web/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java
    - services/notification-web/src/test/java/ru/rutcampustrack/notification/config/RabbitConfigTest.java
  modified:
    - services/notification-web/build.gradle.kts
    - services/notification-web/src/main/resources/application.yml
decisions:
  - "Unit tests over Spring context tests for RabbitConfig bean validation — faster, more reliable, no RabbitMQ mock needed"
metrics:
  duration: ~15min
  completed: "2026-04-05"
  tasks: 2
  files: 5
---

# Phase 20 Plan 01: Notification-Web RabbitMQ Infrastructure Summary

## One-liner

notification-web RabbitMQ fanout queue + DLQ wired with placeholder EventConsumer and actuator health endpoint, mirroring Attendance Service pattern with notification-web bean names.

## What Was Built

- `RabbitConfig.java` — 7 beans: FanoutExchange `rut-uit.events`, DirectExchange `rut-uit.events.dlq`, Queue `notification-web.events` (with DLQ dead-letter arguments), Queue `notification-web.events.dlq`, 2 Binding beans, Jackson2JsonMessageConverter
- `EventConsumer.java` — Placeholder `@RabbitListener` on `notification-web.events` that logs received event_type (Phase 21 adds WebSocket routing)
- `build.gradle.kts` — Added `spring-boot-starter-actuator` dependency
- `application.yml` — Added `management.endpoints.web.exposure.include: health` block
- `RabbitConfigTest.java` — 5 unit tests verifying queue names, DLQ arguments, exchange types, converter bean (pure unit tests, no Spring context required)

## Commits

| Task | Commit | Message |
|------|--------|---------|
| 1 | 200d746 | feat(20-01): add RabbitConfig, EventConsumer, and actuator to notification-web |
| 2 | 41881a9 | test(20-01): add RabbitConfigTest verifying queue names, DLQ args, exchange types, converter |

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

- `EventConsumer.onEvent()` logs the event but does not route to WebSocket — intentional placeholder. Phase 21 (notification-web WebSocket) will add actual routing logic.

## Self-Check: PASSED

- services/notification-web/src/main/java/ru/rutcampustrack/notification/config/RabbitConfig.java: FOUND
- services/notification-web/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java: FOUND
- services/notification-web/src/test/java/ru/rutcampustrack/notification/config/RabbitConfigTest.java: FOUND
- Commit 200d746: FOUND
- Commit 41881a9: FOUND
- All 5 RabbitConfigTest tests: PASS
- compileJava: BUILD SUCCESSFUL
