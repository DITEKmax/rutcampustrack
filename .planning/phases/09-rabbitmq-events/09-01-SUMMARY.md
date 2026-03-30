---
phase: 09-rabbitmq-events
plan: "01"
subsystem: academic-service
tags: [rabbitmq, events, spring-amqp, transactional-events, domain-events]
dependency_graph:
  requires: [phase-08-redis-cache]
  provides: [group.updated, semester.archived, homework.published, homework.updated]
  affects: [notification-service, schedule-service, attendance-service]
tech_stack:
  added: [testcontainers:rabbitmq]
  patterns: [Spring ApplicationEvent bridge, @TransactionalEventListener(AFTER_COMMIT), Jackson2JsonMessageConverter]
key_files:
  created:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEvent.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/GroupUpdatedEvent.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/SemesterArchivedEvent.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkPublishedEvent.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkUpdatedEvent.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/RabbitConfig.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEventListener.java
    - event-schemas/group.updated.json
    - event-schemas/semester.archived.json
    - event-schemas/homework.updated.json
  modified:
    - services/academic-service/academic-app/build.gradle.kts
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java
decisions:
  - "SemesterRepository.findByIsActiveTrue() returns Optional<Semester> not List -- activateSemester captures Optional and publishes event via ifPresent"
  - "CacheConfig ObjectMapper is local variable only (not a Spring bean) -- no ambiguity when injecting shared ObjectMapper into Jackson2JsonMessageConverter"
  - "UserService constructor uses @Nullable CacheManager -- ApplicationEventPublisher added as last parameter without @Nullable (always available from Spring context)"
metrics:
  duration_seconds: 366
  completed_date: "2026-03-31"
  tasks_completed: 2
  files_created: 10
  files_modified: 5
---

# Phase 09 Plan 01: RabbitMQ Event Infrastructure Summary

**One-liner:** Spring ApplicationEvent bridge with @TransactionalEventListener(AFTER_COMMIT) publishes group.updated, semester.archived, homework.published, homework.updated to rut-uit.events fanout exchange after database transactions commit.

## What Was Built

Event publishing infrastructure for Academic Service, wiring four service classes to publish domain events to RabbitMQ after database transactions commit.

### Task 1: Event Infrastructure

Created the `ru.rutcampustrack.academic.event` package with 7 new files:

- **DomainEvent.java**: Abstract base class extending `ApplicationEvent`. Holds the event envelope (event_type, event_id UUID via `UUID.randomUUID()`, occurred_at `OffsetDateTime.now()`, payload as nested object). Annotated with `@JsonIgnoreProperties({"source", "timestamp"})` to suppress ApplicationEvent internal fields and `@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)` to prevent default typing interference.

- **GroupUpdatedEvent.java**: Extends DomainEvent, event_type="group.updated", inner `record Payload(Long groupId)`.

- **SemesterArchivedEvent.java**: Extends DomainEvent, event_type="semester.archived", inner `record Payload(Long semesterId)`.

- **HomeworkPublishedEvent.java**: Extends DomainEvent, event_type="homework.published", inner `record Payload(Long homeworkId, Long groupId, Long subjectId, String title, boolean hasLink)`.

- **HomeworkUpdatedEvent.java**: Extends DomainEvent, event_type="homework.updated", inner `record Payload(Long homeworkId, Long groupId, String title)`.

- **RabbitConfig.java**: Declares durable FanoutExchange("rut-uit.events", true, false), `Jackson2JsonMessageConverter` using the shared Spring-managed `ObjectMapper` (with JavaTimeModule), and `RabbitTemplate` without `channelTransacted=true` (per Pitfall 1 from research).

- **DomainEventListener.java**: `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` component that receives `DomainEvent` and calls `rabbitTemplate.convertAndSend(EXCHANGE, "", event)`.

Added `testImplementation("org.testcontainers:rabbitmq")` to `build.gradle.kts` (version managed by testcontainers-bom:1.20.4).

Created 3 JSON schemas in `event-schemas/` following the `homework.published.json` pattern: `group.updated.json`, `semester.archived.json`, `homework.updated.json`.

### Task 2: Service Wiring

Injected `ApplicationEventPublisher` (no AMQP imports) into 4 services:

- **GroupService**: publishes `GroupUpdatedEvent(this, saved.getId())` after `updateGroup`, and `GroupUpdatedEvent(this, id)` after `deleteGroup`.
- **UserService**: publishes `GroupUpdatedEvent` for both `oldGroupId` and `request.newGroupId()` after `transferStudent` (student moves between two groups).
- **SemesterService**: captures the currently active semester via `findByIsActiveTrue()` before `deactivateAllActive()`, then publishes `SemesterArchivedEvent` after `entityManager.flush()`.
- **HomeworkService**: publishes `HomeworkPublishedEvent` after `createHomework`, `HomeworkUpdatedEvent` after `updateHomework`.

## Decisions Made

1. **SemesterRepository returns Optional not List**: `findByIsActiveTrue()` returns `Optional<Semester>`. The plan assumed a `List`. Used `Optional.ifPresent()` to publish the event for the deactivated semester — correct behavior since only one active semester can exist at a time (enforced by `deactivateAllActive()` JPQL).

2. **CacheConfig ObjectMapper is not a Spring bean**: Verified that `CacheConfig` creates its local `ObjectMapper` as a local variable used only for `GenericJackson2JsonRedisSerializer`, not registered as a `@Bean`. No ambiguity in injecting the autoconfigured `ObjectMapper` into `jacksonMessageConverter`.

3. **ApplicationEventPublisher injection position**: Added as last constructor parameter in `UserService` (after `@Nullable CacheManager`) — `ApplicationEventPublisher` is always available from Spring context, so no `@Nullable` annotation needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SemesterRepository.findByIsActiveTrue() returns Optional, not List**

- **Found during:** Task 2 implementation
- **Issue:** Plan action specified `List<Semester> previouslyActive = semesterRepository.findByIsActiveTrue()` and a for-loop, but the repository method returns `Optional<Semester>`.
- **Fix:** Used `Optional<Semester> previouslyActive = semesterRepository.findByIsActiveTrue()` followed by `previouslyActive.ifPresent(deactivated -> eventPublisher.publishEvent(...))`. This is semantically equivalent and correct since the business rule enforces at most one active semester.
- **Files modified:** `SemesterService.java`
- **Commit:** 415c38a

## Known Stubs

None — all event publishing is fully wired to production code paths.

## Verification Results

- `./gradlew :services:academic-service:academic-app:compileJava` passed (BUILD SUCCESSFUL)
- All 5 event classes exist in `ru.rutcampustrack.academic.event` package
- DomainEvent serializes with envelope structure `{event_type, event_id, occurred_at, payload}`
- RabbitConfig declares `rut-uit.events` FanoutExchange as durable, non-auto-delete
- DomainEventListener uses `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
- All 3 new JSON schemas follow the homework.published.json pattern with nested payload
- Services inject `ApplicationEventPublisher`, not `RabbitTemplate`

## Self-Check: PASSED

Files verified present:
- FOUND: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEvent.java
- FOUND: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/RabbitConfig.java
- FOUND: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEventListener.java
- FOUND: event-schemas/group.updated.json
- FOUND: event-schemas/semester.archived.json
- FOUND: event-schemas/homework.updated.json

Commits verified:
- 98011b6 — feat(09-01): add RabbitMQ event infrastructure
- 415c38a — feat(09-01): wire ApplicationEventPublisher into services
