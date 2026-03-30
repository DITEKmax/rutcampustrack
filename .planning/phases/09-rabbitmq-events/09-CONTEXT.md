# Phase 9: RabbitMQ Events - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Academic Service publishes typed domain events (group.updated, semester.archived, homework.published, homework.updated) to the `rut-uit.events` fanout exchange after the database transaction commits. No events are published for rolled-back transactions. Downstream consumers (Schedule, Attendance, Notification services) are NOT part of this phase -- only the publishing side.

</domain>

<decisions>
## Implementation Decisions

### Event Schemas
- **D-01:** Minimal payloads -- entity ID + timestamp + action context only. Consumers call gRPC to get full data if needed. Keeps events small and avoids stale snapshots.
- **D-02:** Create all missing JSON Schema files in `event-schemas/` following the existing `homework.published.json` pattern: `group.updated.json`, `semester.archived.json`, `homework.updated.json`.
- **D-03:** Envelope structure: `event_type` (string), `event_id` (UUID), `occurred_at` (ISO datetime), `payload` (object with entity-specific fields).

### Publishing Wiring
- **D-04:** Spring ApplicationEvent bridge pattern. Service methods publish Spring `ApplicationEvent` subclasses via `ApplicationEventPublisher`. A separate `@TransactionalEventListener(AFTER_COMMIT)` listener class catches them and forwards to `RabbitTemplate`.
- **D-05:** Services are decoupled from RabbitMQ -- they only know about domain event classes, not messaging infrastructure.
- **D-06:** All event infrastructure lives in a single `ru.rutcampustrack.academic.event` package: base event class, concrete event classes, listener, RabbitMQ config.

### Exchange Configuration
- **D-07:** Durable fanout exchange `rut-uit.events`, not auto-delete. Declared as a `@Bean` in `RabbitConfig`.
- **D-08:** `Jackson2JsonMessageConverter` using the shared Spring-managed `ObjectMapper` (already configured with `JavaTimeModule`). No dedicated ObjectMapper for events.
- **D-09:** Messages serialized as JSON with content-type header set by the converter.

### Test Strategy
- **D-10:** Testcontainers RabbitMQ for integration tests. Real broker, not mocked. Consistent with Phase 8's Testcontainers approach.
- **D-11:** Test pattern: bind a temporary queue to `rut-uit.events` exchange, perform mutation via service method, assert message arrives in queue with correct `event_type` and `payload`.
- **D-12:** New `AbstractAcademicEventIntegrationTest` base class with PostgreSQL + RabbitMQ containers. No Redis container -- keeps startup fast and concerns separated. Excludes Redis autoconfiguration, includes RabbitMQ.

### Claude's Discretion
- Base event class design (abstract class vs interface vs record)
- Exact field naming in event payload classes
- Whether to use a shared `DomainEventEnvelope` wrapper or individual message classes per event type
- RabbitTemplate error handling strategy (fire-and-forget vs retry)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Event Schemas (existing + to create)
- `event-schemas/homework.published.json` -- existing schema, reference pattern for all events
- `event-schemas/group.updated.json` -- TO CREATE in this phase
- `event-schemas/semester.archived.json` -- TO CREATE in this phase
- `event-schemas/homework.updated.json` -- TO CREATE in this phase

### Service Methods (event trigger points)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java` -- transferStudent triggers group.updated
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java` -- updateGroup/deleteGroup trigger group.updated
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java` -- deactivation triggers semester.archived
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java` -- create triggers homework.published, update triggers homework.updated

### Build & Config
- `services/academic-service/academic-app/build.gradle.kts` -- spring-boot-starter-amqp already present
- `services/academic-service/academic-app/src/main/resources/application.yml` -- spring.rabbitmq.host already configured
- `docker-compose.yml` -- RabbitMQ container config (port 5672, management 15672)

### Architecture
- `docs/architecture.md` -- overall architecture with RabbitMQ fanout exchange design
- `docs/phases-plan.md` -- phase 9 description
- `.planning/ROADMAP.md` -- Phase 9 success criteria (3 items)

### Test Infrastructure
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicIntegrationTest.java` -- existing base (excludes RabbitMQ + Redis)
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicCacheIntegrationTest.java` -- Phase 8 base (excludes RabbitMQ, includes Redis)

### Prior Decisions
- `.planning/STATE.md` -- "RabbitMQ events must use @TransactionalEventListener(AFTER_COMMIT)"

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `spring-boot-starter-amqp` -- already in build.gradle.kts, no new dependency needed
- `spring.rabbitmq.host: rabbitmq` -- already configured in application.yml
- `ApplicationEventPublisher` -- standard Spring, no additional dependency
- Shared `ObjectMapper` with `JavaTimeModule` -- available for Jackson2JsonMessageConverter

### Established Patterns
- `@TransactionalEventListener(AFTER_COMMIT)` -- decided in v2.0 research phase, not yet implemented
- AbstractAcademicIntegrationTest excludes `RabbitAutoConfiguration` -- event tests need a new base class that includes it
- Phase 8 Testcontainers pattern (separate base class per infrastructure need) -- follow the same approach for RabbitMQ

### Integration Points
- Service methods in UserService, GroupService, SemesterService, HomeworkService -- inject `ApplicationEventPublisher` and publish events after mutations
- `RabbitConfig` bean -- new config class declaring the fanout exchange and message converter
- `@TransactionalEventListener` class -- bridges Spring events to RabbitMQ

</code_context>

<specifics>
## Specific Ideas

No specific requirements -- open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None -- discussion stayed within phase scope

</deferred>

---

*Phase: 09-rabbitmq-events*
*Context gathered: 2026-03-31*
