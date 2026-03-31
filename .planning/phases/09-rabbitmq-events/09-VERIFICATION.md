---
phase: 09-rabbitmq-events
verified: 2026-03-31T04:00:00Z
status: passed
score: 7/7 must-haves verified
re_verification: false
---

# Phase 9: RabbitMQ Events Verification Report

**Phase Goal:** Qualifying mutations publish typed events to the fanout exchange after the database transaction commits — no events are published for rolled-back transactions.
**Verified:** 2026-03-31T04:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (from ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Adding or removing a student from a group publishes a `group.updated` event to `rut-uit.events` exchange containing the group ID and occurred_at timestamp | VERIFIED | `GroupService.updateGroup` and `deleteGroup` call `eventPublisher.publishEvent(new GroupUpdatedEvent(...))`. `UserService.transferStudent` publishes two events (old group + new group). Integration tests `updateGroup_publishesGroupUpdatedEvent`, `deleteGroup_publishesGroupUpdatedEvent`, and `transferStudent_publishesGroupUpdatedEventForBothGroups` all assert `event_type=group.updated`, UUID `event_id`, `occurred_at`, and `payload.group_id`. |
| 2 | Archiving (deactivating) a semester publishes a `semester.archived` event after the transaction commits; a transaction rollback produces no event | VERIFIED | `SemesterService.activateSemester` captures the previously-active semester and calls `eventPublisher.publishEvent(new SemesterArchivedEvent(...))` inside a `@Transactional` method. `DomainEventListener` uses `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` — fires only after commit. Integration test `activateSemester_publishesSemesterArchivedEvent` confirms event arrives with `event_type=semester.archived` and correct `payload.semester_id`. |
| 3 | Creating a homework item publishes `homework.published`; updating it publishes `homework.updated` — both events include a unique `event_id` UUID for idempotent downstream processing | VERIFIED | `HomeworkService.createHomework` publishes `HomeworkPublishedEvent`; `updateHomework` publishes `HomeworkUpdatedEvent`. `DomainEvent` base class generates `UUID.randomUUID()` for each event. Integration tests `createHomework_publishesHomeworkPublishedEvent` and `updateHomework_publishesHomeworkUpdatedEvent` assert `event_type`, UUID format `event_id`, nested `payload` fields including `homework_id`, `group_id`, `subject_id`, `title`, and `has_link`. |

**Score:** 3/3 success criteria verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEvent.java` | Abstract base event class extending ApplicationEvent with envelope fields | VERIFIED | `extends ApplicationEvent`, has `@JsonProperty("event_id")`, `@JsonProperty("event_type")`, `@JsonProperty("occurred_at")`, `@JsonProperty("payload")`, `UUID.randomUUID()`, `@JsonIgnoreProperties({"source","timestamp"})`, `@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/GroupUpdatedEvent.java` | GroupUpdatedEvent with nested Payload record | VERIFIED | `extends DomainEvent`, event_type `"group.updated"`, inner `record Payload(@JsonProperty("group_id") Long groupId)` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/SemesterArchivedEvent.java` | SemesterArchivedEvent with nested Payload record | VERIFIED | `extends DomainEvent`, event_type `"semester.archived"`, inner `record Payload(@JsonProperty("semester_id") Long semesterId)` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkPublishedEvent.java` | HomeworkPublishedEvent with nested Payload record | VERIFIED | `extends DomainEvent`, event_type `"homework.published"`, inner `record Payload` with `homework_id`, `group_id`, `subject_id`, `title`, `has_link` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkUpdatedEvent.java` | HomeworkUpdatedEvent with nested Payload record | VERIFIED | `extends DomainEvent`, event_type `"homework.updated"`, inner `record Payload` with `homework_id`, `group_id`, `title` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/RabbitConfig.java` | FanoutExchange and Jackson2JsonMessageConverter beans | VERIFIED | `FanoutExchange("rut-uit.events", true, false)`, `Jackson2JsonMessageConverter(objectMapper)`, `RabbitTemplate` without `channelTransacted` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEventListener.java` | TransactionalEventListener bridging Spring events to RabbitMQ | VERIFIED | `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, `rabbitTemplate.convertAndSend(EXCHANGE, "", event)` |
| `event-schemas/group.updated.json` | JSON Schema for group.updated event | VERIFIED | Contains `"const": "group.updated"`, `$schema`, nested `payload` object with `group_id` |
| `event-schemas/semester.archived.json` | JSON Schema for semester.archived event | VERIFIED | Contains `"const": "semester.archived"`, `$schema`, nested `payload` object with `semester_id` |
| `event-schemas/homework.updated.json` | JSON Schema for homework.updated event | VERIFIED | Contains `"const": "homework.updated"`, `$schema`, nested `payload` object with `homework_id`, `group_id`, `title` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicEventIntegrationTest.java` | Test base class with PostgreSQL + RabbitMQ Testcontainers, no Redis | VERIFIED | `RabbitMQContainer`, `RABBITMQ.getHost`, `RABBITMQ.getMappedPort(5672)`, `RABBITMQ::getAdminUsername`, `RABBITMQ::getAdminPassword`, excludes Redis autoconfiguration, does NOT exclude `RabbitAutoConfiguration`, sets `grpc.server.port=-1` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIntegrationTest.java` | Integration tests for all event types | VERIFIED | `extends AbstractAcademicEventIntegrationTest`, `@MockitoBean RequestContext`, 6 test methods, `rabbitTemplate.receive`, `rut-uit.events`, `root.get("payload")` for nested payload access, no `@Transactional` on test methods |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `GroupService.java` | `GroupUpdatedEvent` | `eventPublisher.publishEvent(new GroupUpdatedEvent(this, saved.getId()))` | WIRED | Lines 80 and 92 in GroupService.java — after `updateGroup` save and after `deleteGroup` delete |
| `UserService.java` | `GroupUpdatedEvent` | `eventPublisher.publishEvent(new GroupUpdatedEvent(this, oldGroupId))` + `publishEvent(new GroupUpdatedEvent(this, request.newGroupId()))` | WIRED | Lines 241-242 in UserService.java — after `transferStudent` save, two events published |
| `SemesterService.java` | `SemesterArchivedEvent` | `eventPublisher.publishEvent(new SemesterArchivedEvent(this, deactivated.getId()))` | WIRED | Lines 90-91 in SemesterService.java — inside `ifPresent` on `findByIsActiveTrue()` result |
| `HomeworkService.java` | `HomeworkPublishedEvent` / `HomeworkUpdatedEvent` | `eventPublisher.publishEvent(new HomeworkPublishedEvent(...))` / `eventPublisher.publishEvent(new HomeworkUpdatedEvent(...))` | WIRED | Lines 77-80 in `createHomework` and lines 117-119 in `updateHomework` |
| `DomainEventListener.java` | `RabbitTemplate` | `rabbitTemplate.convertAndSend("rut-uit.events", "", event)` | WIRED | Line 34 in DomainEventListener.java |
| `EventIntegrationTest.java` | `rut-uit.events` exchange | `rabbitTemplate.receive(queueName, 5000)` | WIRED | `bindTempQueue()` helper declares queue and binds to `rut-uit.events`; all 6 tests call `rabbitTemplate.receive` |

---

### Decoupling Verification (D-05)

Services (`GroupService`, `UserService`, `SemesterService`, `HomeworkService`) import only `org.springframework.context.ApplicationEventPublisher` — zero imports from `org.springframework.amqp`. AMQP coupling is isolated to `DomainEventListener` and `RabbitConfig` in the `event` package.

---

### Data-Flow Trace (Level 4)

Level 4 data-flow trace is not applicable for event publishing — there is no dynamic UI rendering. The integration tests in Plan 02 serve as the end-to-end pipeline verification: real service calls produce real RabbitMQ messages with real entity IDs from the database. All 6 tests passed per the summary.

---

### Behavioral Spot-Checks

Step 7b: Testcontainers tests require a running Docker environment and cannot be executed without starting containers. The integration test results are documented in the SUMMARY files.

| Behavior | Evidence | Status |
|----------|----------|--------|
| `updateGroup` publishes `group.updated` to RabbitMQ | `EventIntegrationTest.updateGroup_publishesGroupUpdatedEvent` — 6/6 tests PASSED per 09-02-SUMMARY.md | PASS (per summary) |
| `deleteGroup` publishes `group.updated` | `EventIntegrationTest.deleteGroup_publishesGroupUpdatedEvent` — 6/6 tests PASSED | PASS (per summary) |
| `transferStudent` publishes two `group.updated` events | `EventIntegrationTest.transferStudent_publishesGroupUpdatedEventForBothGroups` — 6/6 tests PASSED | PASS (per summary) |
| `activateSemester` publishes `semester.archived` | `EventIntegrationTest.activateSemester_publishesSemesterArchivedEvent` — 6/6 tests PASSED | PASS (per summary) |
| `createHomework` publishes `homework.published` with UUID `event_id` | `EventIntegrationTest.createHomework_publishesHomeworkPublishedEvent` — 6/6 tests PASSED | PASS (per summary) |
| `updateHomework` publishes `homework.updated` with UUID `event_id` | `EventIntegrationTest.updateHomework_publishesHomeworkUpdatedEvent` — 6/6 tests PASSED | PASS (per summary) |

Human verification recommended to confirm test suite passes in a clean environment (requires Docker).

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| EVENT-01 | 09-01-PLAN, 09-02-PLAN | `group.updated` published on group composition changes | SATISFIED | `GroupService.updateGroup`, `deleteGroup`, `UserService.transferStudent` all publish `GroupUpdatedEvent`. Three integration tests verify all three code paths produce messages with correct `event_type` and nested `payload.group_id`. |
| EVENT-02 | 09-01-PLAN, 09-02-PLAN | `semester.archived` published on semester deactivation | SATISFIED | `SemesterService.activateSemester` captures the previously active semester via `findByIsActiveTrue()` and publishes `SemesterArchivedEvent` inside the `@Transactional` method via `ifPresent`. Integration test verifies message arrives with `event_type=semester.archived` and correct `payload.semester_id`. |
| EVENT-03 | 09-01-PLAN, 09-02-PLAN | `homework.published` / `homework.updated` published on homework changes | SATISFIED | `HomeworkService.createHomework` publishes `HomeworkPublishedEvent`; `updateHomework` publishes `HomeworkUpdatedEvent`. Both events inherit `UUID.randomUUID()` event_id from `DomainEvent` base class. Integration tests verify both event types arrive with UUID `event_id` and full nested payload including entity-specific fields. |

All 3 requirements are satisfied. No orphaned requirements found — REQUIREMENTS.md traceability table maps EVENT-01, EVENT-02, EVENT-03 exclusively to Phase 9, and both plans in Phase 9 claim all three IDs.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| No anti-patterns found | — | — | — | — |

Checked all 7 files in the `event` package and all 4 modified service files for: TODO/FIXME, placeholder implementations, empty return stubs, hardcoded static returns, and AMQP imports in service classes. None found.

The deferred item documented in `deferred-items.md` (pre-existing test failures from other test base classes) was resolved in commit `6fcb189` by adding `@MockitoBean RabbitTemplate` to `AbstractAcademicIntegrationTest` and `AbstractAcademicCacheIntegrationTest`. This fix is out of Phase 9's primary scope but was applied before the phase was marked complete.

---

### Human Verification Required

#### 1. Full Test Suite Green Check

**Test:** Run `./gradlew.bat :services:academic-service:academic-app:test` in a Docker environment.
**Expected:** All tests pass including the 6 new `EventIntegrationTest` tests and all previously passing tests (`EntityMappingIntegrationTest`, `RestApiIntegrationTest`, `AcademicGrpcIntegrationTest`, `CacheIntegrationTest`).
**Why human:** Testcontainers requires Docker running; cannot be verified without container runtime. The fix commit (6fcb189) claims to address the pre-existing failures but the full suite result cannot be confirmed programmatically.

---

### Gaps Summary

No gaps found. All must-have artifacts exist, are substantive (contain required patterns), and are wired into the production code paths. The AFTER_COMMIT transactional guarantee is implemented correctly via `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` with no `channelTransacted=true` pitfall. Services are decoupled from AMQP. All three requirements (EVENT-01, EVENT-02, EVENT-03) are satisfied with both production code and integration test coverage.

---

_Verified: 2026-03-31T04:00:00Z_
_Verifier: Claude (gsd-verifier)_
