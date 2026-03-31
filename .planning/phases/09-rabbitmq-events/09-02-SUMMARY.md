---
phase: 09-rabbitmq-events
plan: "02"
subsystem: academic-service
tags: [rabbitmq, events, testcontainers, integration-tests, spring-amqp]
dependency_graph:
  requires: [phase-09-01-event-infrastructure]
  provides: [EVENT-01-verified, EVENT-02-verified, EVENT-03-verified]
  affects: []
tech_stack:
  added: []
  patterns: [Testcontainers RabbitMQ, @MockitoBean RequestContext, named non-auto-delete queues for multi-receive]
key_files:
  created:
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicEventIntegrationTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIntegrationTest.java
  modified:
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicEventIntegrationTest.java
decisions:
  - "RabbitMQ container credentials (guest/guest) must be overridden in @DynamicPropertySource -- app.yml uses rct_user/rct_dev_pass which causes AuthenticationFailureException against the container"
  - "Named non-auto-delete queues required for tests that call rabbitTemplate.receive() multiple times -- anonymous autoDelete queues are deleted after first consumer disconnects, causing NOT_FOUND error on second receive()"
  - "StudentGroupHistoryRepository cleanup needed in @AfterEach before user deletion -- FK constraint student_group_history.user_id -> users(id) prevents direct deleteById(userId)"
  - "@MockitoBean RequestContext replaces request-scoped bean -- no active request scope needed in SpringBootTest context; Mockito stubbing in @BeforeEach sets up role/headman/userId/groupId per test"
metrics:
  duration_seconds: 1043
  completed_date: "2026-03-31"
  tasks_completed: 2
  files_created: 2
  files_modified: 1
---

# Phase 09 Plan 02: RabbitMQ Event Integration Tests Summary

**One-liner:** Integration tests proving end-to-end event publishing pipeline using Testcontainers RabbitMQ -- service method to @TransactionalEventListener(AFTER_COMMIT) to real broker verified for all 6 event scenarios.

## What Was Built

Two test files providing complete integration test coverage for the event publishing infrastructure created in Plan 01.

### Task 1: AbstractAcademicEventIntegrationTest

Created `/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicEventIntegrationTest.java`:

- Static `PostgreSQLContainer` + `RabbitMQContainer` (shared across test classes for speed)
- `@DynamicPropertySource` overrides PostgreSQL connection, RabbitMQ host/port/username/password
- Does NOT exclude `RabbitAutoConfiguration` -- full AMQP stack is needed for event pipeline tests
- Excludes Redis autoconfiguration (no Redis container needed -- cache is bypassed)
- Sets `grpc.server.port=-1` to disable Netty port binding (matches Phase 07 convention)

### Task 2: EventIntegrationTest

Created `/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIntegrationTest.java`:

**6 test methods (all pass):**

1. `updateGroup_publishesGroupUpdatedEvent()` (EVENT-01) -- verifies GroupService.updateGroup publishes group.updated with correct group_id in nested payload
2. `deleteGroup_publishesGroupUpdatedEvent()` (EVENT-01) -- verifies GroupService.deleteGroup publishes group.updated
3. `transferStudent_publishesGroupUpdatedEventForBothGroups()` (EVENT-01) -- verifies UserService.transferStudent publishes TWO group.updated events, one for each group
4. `activateSemester_publishesSemesterArchivedEvent()` (EVENT-02) -- verifies SemesterService.activateSemester publishes semester.archived for the deactivated semester
5. `createHomework_publishesHomeworkPublishedEvent()` (EVENT-03) -- verifies HomeworkService.createHomework publishes homework.published with full nested payload
6. `updateHomework_publishesHomeworkUpdatedEvent()` (EVENT-03) -- verifies HomeworkService.updateHomework publishes homework.updated

**Key implementation patterns:**
- `bindTempQueue()` helper declares a named, non-exclusive, non-auto-delete queue and binds it to `rut-uit.events` fanout exchange via `RabbitAdmin`
- Tests parse JSON with `ObjectMapper` and assert nested payload fields: `root.get("payload").get("field")`
- UUID format assertion: `matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")`
- No `@Transactional` on any test method (AFTER_COMMIT listener requires the test not to be inside a transaction)
- `@MockitoBean RequestContext` replaces the request-scoped bean with a Mockito mock stubbed in `@BeforeEach`
- `@AfterEach` cleans up in FK-safe order: homeworks -> student_group_history -> users -> groups -> subjects -> semester

## Decisions Made

1. **RabbitMQ container credentials override**: Added `spring.rabbitmq.username` (`RABBITMQ::getAdminUsername`) and `spring.rabbitmq.password` (`RABBITMQ::getAdminPassword`) to `@DynamicPropertySource`. The `application.yml` configures `rct_user`/`rct_dev_pass` but the Testcontainers `RabbitMQContainer` uses `guest`/`guest` by default.

2. **Named non-auto-delete queues**: Changed from anonymous `admin.declareQueue()` (which creates exclusive auto-delete queues) to named `new Queue(name, false, false, false)`. Auto-delete queues are deleted after the first consumer (first `receive()` call) disconnects, causing `NOT_FOUND` error on subsequent `receive()` calls.

3. **StudentGroupHistoryRepository cleanup**: Added explicit cleanup of `student_group_history` records before deleting the test user in `@AfterEach`, since the table has `user_id BIGINT NOT NULL REFERENCES users(id)` without `ON DELETE CASCADE`.

4. **@MockitoBean over @MockBean**: Used `@MockitoBean` (non-deprecated in Spring Boot 3.4) instead of the deprecated `@MockBean` to avoid compiler warnings.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Missing RabbitMQ credential override in AbstractAcademicEventIntegrationTest**

- **Found during:** Task 2 test execution
- **Issue:** `AmqpAuthenticationException` on all 6 tests. The `@DynamicPropertySource` overrode `host` and `port` but not `username`/`password`. Spring used `application.yml` credentials (`rct_user`/`rct_dev_pass`) which don't exist in the container.
- **Fix:** Added `spring.rabbitmq.username` and `spring.rabbitmq.password` overrides using `RABBITMQ::getAdminUsername` and `RABBITMQ::getAdminPassword`.
- **Files modified:** `AbstractAcademicEventIntegrationTest.java`
- **Commit:** d11c329 (included in Task 2 commit)

**2. [Rule 1 - Bug] Auto-delete queue deleted after first receive() call**

- **Found during:** Task 2 test execution (transferStudent and activateSemester tests)
- **Issue:** `NOT_FOUND - no queue 'test.events.xxx'` error on second `rabbitTemplate.receive()` call. Anonymous queues created by `admin.declareQueue()` are auto-delete, so they're deleted when the first consumer disconnects.
- **Fix:** Changed to named queue with `new Queue(name, false, false, false)` (durable=false, exclusive=false, autoDelete=false).
- **Files modified:** `EventIntegrationTest.java`
- **Commit:** d11c329

**3. [Rule 1 - Bug] FK constraint violation in @AfterEach when test user has student_group_history**

- **Found during:** Task 2 test execution (transferStudent test cleanup)
- **Issue:** `DataIntegrityViolationException` when trying to `deleteById(testUser.getId())` after `transferStudent` test. The transfer creates `StudentGroupHistory` records with `user_id` FK to `users`.
- **Fix:** Added `studentGroupHistoryRepository.deleteAll(...)` before `userRepository.deleteById()` in `@AfterEach`.
- **Files modified:** `EventIntegrationTest.java`
- **Commit:** d11c329

### Out-of-scope Discoveries (Deferred)

**Pre-existing test failures from Plan 01:** `EntityMappingIntegrationTest`, `RestApiIntegrationTest`, `AcademicGrpcIntegrationTest`, `CacheIntegrationTest` (44 failures total) fail with `UnsatisfiedDependencyException` because `DomainEventListener` (added in Plan 01) requires `ConnectionFactory`, but these test base classes exclude `RabbitAutoConfiguration`. This was already failing before Plan 02 was started. Documented in `deferred-items.md`.

## Known Stubs

None -- all tests are fully wired to production service methods and real Testcontainers brokers.

## Verification Results

- `./gradlew :services:academic-service:academic-app:test --tests "*EventIntegrationTest*"` -- 6/6 tests PASSED
- EventIntegrationTest uses real RabbitMQ via Testcontainers, not mocks
- No test method has `@Transactional` annotation
- Each test verifies `event_type`, `event_id` (UUID regex), `occurred_at`, and entity-specific `payload` fields under nested `payload` object
- RequestContext is mocked via `@MockitoBean` with concrete stubbing in `@BeforeEach`

## Self-Check: PASSED

Files verified present:
- FOUND: services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicEventIntegrationTest.java
- FOUND: services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIntegrationTest.java

Commits verified:
- 62e1b76 -- test(09-02): add AbstractAcademicEventIntegrationTest base class with PostgreSQL + RabbitMQ Testcontainers
- d11c329 -- test(09-02): add EventIntegrationTest -- all 6 event integration tests pass
