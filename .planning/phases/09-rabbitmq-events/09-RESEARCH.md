# Phase 9: RabbitMQ Events - Research

**Researched:** 2026-03-31
**Domain:** Spring AMQP, ApplicationEvent bridge, transactional event publishing
**Confidence:** HIGH

## Summary

Phase 9 adds the event publishing side to Academic Service. Three event types must be published to the `rut-uit.events` fanout exchange after database transactions commit: `group.updated`, `semester.archived`, `homework.published`, and `homework.updated`. The core pattern — Spring `ApplicationEvent` bridge with `@TransactionalEventListener(AFTER_COMMIT)` forwarding to `RabbitTemplate` — is locked in CONTEXT.md and is well-established in the Spring ecosystem.

All infrastructure dependencies are already present: `spring-boot-starter-amqp` is in `build.gradle.kts`, `spring.rabbitmq` is configured in `application.yml`, and the Testcontainers BOM at 1.20.4 includes the `rabbitmq` module. No new Gradle dependencies are required except adding `testImplementation("org.testcontainers:rabbitmq")` (version managed by BOM).

The only architectural risk is the interaction between `@TransactionalEventListener(AFTER_COMMIT)` and a **transacted** `RabbitTemplate`. The default `RabbitTemplate` bean from Spring Boot autoconfiguration is NOT transacted (`channelTransacted=false`), so AFTER_COMMIT delivery is safe: the message is published immediately after the commit with no transaction coordination attempt. This is the correct fire-and-forget approach for this project.

**Primary recommendation:** Use the Spring ApplicationEvent bridge pattern (D-04) with a non-transacted RabbitTemplate (the autoconfigured default), `@TransactionalEventListener(AFTER_COMMIT)`, and a dedicated `AbstractAcademicEventIntegrationTest` base class with PostgreSQL + RabbitMQ Testcontainers.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Minimal payloads — entity ID + timestamp + action context only. Consumers call gRPC to get full data if needed.
- **D-02:** Create all missing JSON Schema files in `event-schemas/`: `group.updated.json`, `semester.archived.json`, `homework.updated.json`. Pattern: existing `homework.published.json`.
- **D-03:** Envelope structure: `event_type` (string), `event_id` (UUID), `occurred_at` (ISO datetime), `payload` (object with entity-specific fields).
- **D-04:** Spring ApplicationEvent bridge pattern. Service methods publish Spring `ApplicationEvent` subclasses via `ApplicationEventPublisher`. A separate `@TransactionalEventListener(AFTER_COMMIT)` listener class catches them and forwards to `RabbitTemplate`.
- **D-05:** Services are decoupled from RabbitMQ — they only know about domain event classes, not messaging infrastructure.
- **D-06:** All event infrastructure lives in a single `ru.rutcampustrack.academic.event` package: base event class, concrete event classes, listener, RabbitMQ config.
- **D-07:** Durable fanout exchange `rut-uit.events`, not auto-delete. Declared as a `@Bean` in `RabbitConfig`.
- **D-08:** `Jackson2JsonMessageConverter` using the shared Spring-managed `ObjectMapper` (already configured with `JavaTimeModule`). No dedicated ObjectMapper for events.
- **D-09:** Messages serialized as JSON with content-type header set by the converter.
- **D-10:** Testcontainers RabbitMQ for integration tests. Real broker, not mocked.
- **D-11:** Test pattern: bind a temporary queue to `rut-uit.events` exchange, perform mutation via service method, assert message arrives in queue with correct `event_type` and `payload`.
- **D-12:** New `AbstractAcademicEventIntegrationTest` base class with PostgreSQL + RabbitMQ containers. No Redis container. Excludes Redis autoconfiguration, includes RabbitMQ.

### Claude's Discretion

- Base event class design (abstract class vs interface vs record)
- Exact field naming in event payload classes
- Whether to use a shared `DomainEventEnvelope` wrapper or individual message classes per event type
- RabbitTemplate error handling strategy (fire-and-forget vs retry)

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| EVENT-01 | `group.updated` published on group composition changes (add/remove student from group, updateGroup, deleteGroup) | Spring `ApplicationEventPublisher` injected into `GroupService` and `UserService.transferStudent`; `@TransactionalEventListener(AFTER_COMMIT)` publishes to fanout exchange |
| EVENT-02 | `semester.archived` published on semester deactivation, after transaction commit, not on rollback | `@TransactionalEventListener(AFTER_COMMIT)` guarantees publish only after commit; event fired from `SemesterService` deactivation path |
| EVENT-03 | `homework.published` on create, `homework.updated` on update — both include `event_id` UUID for idempotent downstream processing | Envelope structure (D-03) mandates `event_id` UUID; events fired from `HomeworkService.createHomework` and `HomeworkService.updateHomework` |
</phase_requirements>

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-boot-starter-amqp` | Managed by Spring Boot BOM (3.4.x) | RabbitTemplate, RabbitAdmin, AMQP autoconfiguration | Already present in `build.gradle.kts`; no change needed |
| `org.testcontainers:rabbitmq` | 1.20.4 (managed by testcontainers-bom) | `RabbitMQContainer` for integration tests | Matches project BOM; already declared in `dependencyManagement` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `Jackson2JsonMessageConverter` | From spring-amqp | JSON serialization of AMQP messages | Use in `RabbitConfig` for all outbound messages |
| `FanoutExchange` | From spring-amqp | Declares durable fanout exchange as a bean | Declared once in `RabbitConfig`; auto-created on connect |
| `ApplicationEventPublisher` | Spring Core | Publishes Spring events from service layer | Inject into services; they stay ignorant of AMQP |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Spring ApplicationEvent bridge | Direct `RabbitTemplate` injection in services | Rejected (D-05): couples domain logic to messaging; harder to test in isolation |
| `@TransactionalEventListener(AFTER_COMMIT)` | `@EventListener` | `@EventListener` fires inside transaction — publishes even on rollback; AFTER_COMMIT is required |
| Testcontainers RabbitMQ container | `MockitoBean` RabbitTemplate | Mock doesn't test real broker behavior; real container required by D-10 |

**Installation:**
```bash
# No new main dependencies. Test dependency only (version from BOM):
# testImplementation("org.testcontainers:rabbitmq")
```

**Version verification:** BOM `testcontainers:testcontainers-bom:1.20.4` already declared in `dependencyManagement`. The `rabbitmq` module at this version resolves to artifact `org.testcontainers:rabbitmq:1.20.4`.

---

## Architecture Patterns

### Recommended Project Structure
```
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/
├── DomainEvent.java          # Abstract base class: event_type, event_id (UUID), occurred_at
├── GroupUpdatedEvent.java    # Extends DomainEvent; payload: group_id, occurred_at
├── SemesterArchivedEvent.java # Extends DomainEvent; payload: semester_id, occurred_at
├── HomeworkPublishedEvent.java # Extends DomainEvent; payload: homework_id, group_id, subject_id, title, has_link
├── HomeworkUpdatedEvent.java   # Extends DomainEvent; payload: homework_id, group_id, title
├── RabbitConfig.java         # FanoutExchange bean, RabbitTemplate bean with Jackson2JsonMessageConverter
└── DomainEventListener.java  # @TransactionalEventListener(AFTER_COMMIT) — receives DomainEvent, sends to RabbitTemplate

event-schemas/
├── homework.published.json   # Existing
├── group.updated.json        # TO CREATE (D-02)
├── semester.archived.json    # TO CREATE (D-02)
└── homework.updated.json     # TO CREATE (D-02)

src/test/.../integration/
└── AbstractAcademicEventIntegrationTest.java  # PostgreSQL + RabbitMQ Testcontainers, no Redis (D-12)
```

### Pattern 1: Spring ApplicationEvent Bridge (Locked by D-04)

**What:** Service publishes a Spring `ApplicationEvent` subclass via `ApplicationEventPublisher`. A `@TransactionalEventListener(AFTER_COMMIT)` listener translates it to an AMQP message.

**When to use:** All event-triggering mutations in this phase.

**Example:**
```java
// Service (knows nothing about AMQP)
@Service
public class HomeworkService {
    private final ApplicationEventPublisher eventPublisher;
    // ...

    @Transactional
    public Homework createHomework(CreateHomeworkRequest request) {
        Homework saved = homeworkRepository.save(homework);
        eventPublisher.publishEvent(new HomeworkPublishedEvent(saved));
        return saved;
    }
}

// Listener (bridges Spring events to AMQP)
@Component
public class DomainEventListener {
    private final RabbitTemplate rabbitTemplate;
    private static final String EXCHANGE = "rut-uit.events";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDomainEvent(DomainEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE, "", event);
    }
}
```

### Pattern 2: RabbitConfig — Exchange + Converter Beans

**What:** Declares the durable fanout exchange and configures the `RabbitTemplate` to use `Jackson2JsonMessageConverter`.

**Example:**
```java
@Configuration
public class RabbitConfig {

    @Bean
    public FanoutExchange academicEventsExchange() {
        return new FanoutExchange("rut-uit.events", true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
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

Note: The `ObjectMapper` injected here is the Spring-managed bean already configured with `JavaTimeModule` (see `CacheConfig`). No new `ObjectMapper` should be created.

### Pattern 3: AbstractAcademicEventIntegrationTest Base Class (D-12)

**What:** Testcontainers base class with PostgreSQL + RabbitMQ. Excludes Redis autoconfiguration. Follows the same pattern as `AbstractAcademicCacheIntegrationTest`.

**Example:**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractAcademicEventIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final RabbitMQContainer RABBITMQ;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("academic_db")
                .withUsername("rct_user")
                .withPassword("rct_dev_pass");
        POSTGRES.start();

        RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        // Exclude Redis — not needed for event tests
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                      "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    }
}
```

### Pattern 4: Event Test — Bind Temp Queue, Assert Message (D-11)

**What:** Bind a temporary anonymous queue to `rut-uit.events` exchange, trigger mutation, assert message received.

**Example:**
```java
@Test
void createHomework_publishesHomeworkPublishedEvent() throws Exception {
    // Declare a temp queue bound to the fanout exchange
    AmqpAdmin admin = new RabbitAdmin(rabbitTemplate);
    Queue tempQueue = admin.declareQueue(); // anonymous, auto-delete
    admin.declareBinding(BindingBuilder.bind(tempQueue)
            .to(new FanoutExchange("rut-uit.events")));

    // Trigger mutation
    homeworkService.createHomework(request);

    // Assert message received
    Message message = rabbitTemplate.receive(tempQueue.getName(), 5000);
    assertThat(message).isNotNull();
    // Parse and assert event_type, payload fields
}
```

### Anti-Patterns to Avoid

- **`@EventListener` instead of `@TransactionalEventListener`:** Fires inside the transaction — message published even if the DB rolls back. Always use `@TransactionalEventListener(AFTER_COMMIT)`.
- **Direct `RabbitTemplate` injection into services:** Violates D-05. Services must only know `ApplicationEventPublisher`.
- **Creating a new `ObjectMapper` in `RabbitConfig`:** The shared `ObjectMapper` (with `JavaTimeModule`) is already available as a Spring bean. Constructing a new one would lose `JavaTimeModule` configuration and produce wrong date formats.
- **Transacted `RabbitTemplate` with `AFTER_COMMIT`:** The combination of `channelTransacted=true` and `AFTER_COMMIT` causes message loss because transaction synchronizations are cleared before the after-commit callback (spring-amqp issue #1309). The default Spring Boot autoconfigured template is NOT transacted — do not set `channelTransacted=true`.
- **`@TransactionalEventListener` method calling `@Transactional` code with REQUIRED propagation:** After commit the original transaction is "committed but still associated", so REQUIRED propagation re-participates in the committed transaction and new flushes are no-ops. Use `REQUIRES_NEW` if DB work is ever needed in the listener.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Transactional event publish-after-commit | Custom `TransactionSynchronizationAdapter` | `@TransactionalEventListener(AFTER_COMMIT)` | Built-in Spring mechanism since 4.2; handles all edge cases including nested transactions |
| AMQP JSON serialization | Manual `ObjectMapper.writeValueAsBytes()` + `MessageBuilder` | `Jackson2JsonMessageConverter` + `RabbitTemplate.convertAndSend` | Sets `content_type: application/json` header automatically; handles charset correctly |
| Exchange declaration | Raw AMQP channel operations | `FanoutExchange` bean + `RabbitAdmin` | Spring AMQP declares exchange on startup via `RabbitAdmin` autodiscovery of Exchange beans |
| UUID generation for `event_id` | Sequential counter, timestamp-based ID | `UUID.randomUUID()` | RFC-4122 compliance; truly unique; no DB sequence needed |
| Test message assertion | Manual AMQP polling loop | `RabbitTemplate.receive(queueName, timeoutMs)` | Built-in blocking receive with timeout; returns null on timeout (no spin-wait needed) |

**Key insight:** The entire publish pipeline (transaction coordination, JSON marshalling, exchange topology) is solved by Spring AMQP primitives. The phase is primarily wiring work, not algorithm work.

---

## Common Pitfalls

### Pitfall 1: Transacted RabbitTemplate + AFTER_COMMIT = Message Loss

**What goes wrong:** If `RabbitTemplate` is configured with `channelTransacted=true`, messages sent from `@TransactionalEventListener(AFTER_COMMIT)` are silently lost. Spring's JPA transaction manager clears synchronizations before calling `afterCompletion()` callbacks, so the template cannot register a new synchronization for the transacted send.

**Why it happens:** `AFTER_COMMIT` runs in a phase where synchronizations have already been cleared.

**How to avoid:** Use the default non-transacted `RabbitTemplate` (do NOT set `channelTransacted=true`). The default autoconfigured bean is safe.

**Warning signs:** Messages never appear in broker queues despite no exceptions in logs.

### Pitfall 2: Existing AbstractAcademicIntegrationTest Excludes RabbitMQ

**What goes wrong:** If event tests extend the existing base class, RabbitAutoConfiguration is excluded and no AMQP connection is available. The event listener fires but `RabbitTemplate.convertAndSend()` throws a connection exception.

**Why it happens:** `AbstractAcademicIntegrationTest.overrideProperties` lists `RabbitAutoConfiguration` in `spring.autoconfigure.exclude`.

**How to avoid:** Event tests MUST use `AbstractAcademicEventIntegrationTest` (new base class from D-12) which does NOT exclude `RabbitAutoConfiguration`.

**Warning signs:** `java.net.ConnectException: Connection refused` in test logs targeting `localhost:5672`.

### Pitfall 3: ObjectMapper Without JavaTimeModule for AMQP Converter

**What goes wrong:** If `Jackson2JsonMessageConverter` is constructed with `new ObjectMapper()` (not the Spring bean), `OffsetDateTime` fields in event payloads are serialized as arrays (`[2026,3,31,12,0,0,...]`) instead of ISO-8601 strings.

**Why it happens:** `JavaTimeModule` is not registered on a plain `ObjectMapper`.

**How to avoid:** Inject the `ObjectMapper` bean from Spring context into `Jackson2JsonMessageConverter`. The existing bean in `CacheConfig` is NOT suitable here (it has `NON_FINAL` default typing which would add `@class` fields to AMQP messages — use the application-level `ObjectMapper` from Spring Boot autoconfiguration which has `JavaTimeModule` and no default typing).

**Correct approach:** Let Spring Boot's `JacksonAutoConfiguration` provide the base `ObjectMapper`. Do not inject `CacheConfig`'s custom `ObjectMapper` into `RabbitConfig`.

**Warning signs:** `occurred_at` in received messages is a JSON array instead of a string.

### Pitfall 4: No fallbackExecution When Called Outside Transaction

**What goes wrong:** In tests that call service methods without a `@Transactional` wrapper, the `@TransactionalEventListener` never fires because there is no active transaction to attach to.

**Why it happens:** `@TransactionalEventListener` requires a transaction to be active. Without `fallbackExecution=true`, events published outside transactions are discarded.

**How to avoid:** All event-triggering service methods are already `@Transactional`, so tests calling them go through a transaction. Do not annotate test methods with `@Transactional` — that would wrap the test in a transaction that gets rolled back, meaning AFTER_COMMIT never fires.

**Warning signs:** `message = rabbitTemplate.receive(queue, 5000)` always returns null in tests.

### Pitfall 5: @Transactional on Test Method Prevents AFTER_COMMIT

**What goes wrong:** If the test method is annotated `@Transactional`, the Spring test transaction wraps the entire test. AFTER_COMMIT fires only when the test transaction commits — which typically happens after the test method completes, too late to assert.

**Why it happens:** Spring test `@Transactional` rolls back by default; AFTER_COMMIT never triggers on rollback.

**How to avoid:** Do NOT annotate event integration test methods with `@Transactional`. Call service methods directly; they manage their own transactions internally.

**Warning signs:** All event assertions fail despite correct service code.

---

## Code Examples

### Base Event Class Design (Claude's Discretion — Recommended)

An abstract class (not a record, not an interface) is recommended because:
- Records cannot be subclassed
- An abstract class allows `event_id` and `occurred_at` to be set in the constructor, enforcing the envelope (D-03)
- Concrete event subclasses carry their payload as constructor fields

```java
// Source: project design — follows D-03 envelope structure
public abstract class DomainEvent extends ApplicationEvent {
    private final String eventType;
    private final UUID eventId;
    private final OffsetDateTime occurredAt;

    protected DomainEvent(Object source, String eventType) {
        super(source);
        this.eventType = eventType;
        this.eventId = UUID.randomUUID();
        this.occurredAt = OffsetDateTime.now();
    }

    // getters for serialization
}
```

### Concrete Event Class

```java
// Source: project design — D-03 envelope, D-01 minimal payload
public class GroupUpdatedEvent extends DomainEvent {
    private final Long groupId;

    public GroupUpdatedEvent(Object source, Long groupId) {
        super(source, "group.updated");
        this.groupId = groupId;
    }
}
```

### Service Integration Point

```java
// GroupService.updateGroup — inject ApplicationEventPublisher, publish after save
@Caching(evict = { ... })
@Transactional
public Group updateGroup(Long id, UpdateGroupRequest request) {
    Group group = findGroupById(id);
    // ... update fields
    Group saved = groupRepository.save(group);
    eventPublisher.publishEvent(new GroupUpdatedEvent(this, saved.getId()));
    return saved;
}
```

### JSON Schema Pattern (follows existing homework.published.json)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "group.updated",
  "description": "Состав группы изменён. Генерируется Academic Service.",
  "type": "object",
  "required": ["event_type", "event_id", "occurred_at", "payload"],
  "properties": {
    "event_type": { "const": "group.updated" },
    "event_id": { "type": "string", "format": "uuid" },
    "occurred_at": { "type": "string", "format": "date-time" },
    "payload": {
      "type": "object",
      "required": ["group_id"],
      "properties": {
        "group_id": { "type": "integer" }
      }
    }
  }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@EventListener` + direct AMQP | `@TransactionalEventListener(AFTER_COMMIT)` | Spring 4.2 (2015) | Guarantees no publish on rollback |
| Manual `MessageBuilder` + channel | `RabbitTemplate.convertAndSend()` | Spring AMQP 1.x | One-liner publish with converter handling |
| `TransactionSynchronizationManager` manual sync | `@TransactionalEventListener` | Spring 4.2 | Declarative, no boilerplate |

**Deprecated/outdated:**
- `@EventListener` for post-commit messaging: Fires inside transaction — do not use for RabbitMQ publish
- `TransactionSynchronizationAdapter`: Manual; `@TransactionalEventListener` is the replacement

---

## Open Questions

1. **ObjectMapper injection into `Jackson2JsonMessageConverter`**
   - What we know: `CacheConfig` creates a custom `ObjectMapper` with `NON_FINAL` default typing (adds `@class` fields) — unsuitable for AMQP messages
   - What's unclear: Whether the autoconfigured Spring Boot `ObjectMapper` (from `JacksonAutoConfiguration`) has `JavaTimeModule` registered automatically
   - Recommendation: Spring Boot 3.x auto-configures `ObjectMapper` with `JavaTimeModule` when `spring-boot-starter-web` is on the classpath (it is). The `RabbitConfig` should inject the base `ObjectMapper` bean by using `@Qualifier` or by declaring the converter bean before `CacheConfig` customizes it. The safest approach: inject `ObjectMapper` (the autoconfigured one), not the `CacheConfig`-scoped one.

2. **`group.updated` trigger scope**
   - What we know: CONTEXT.md canonical refs list `transferStudent` in `UserService`, `updateGroup` and `deleteGroup` in `GroupService`
   - What's unclear: Does archiving a user (removing them from active group membership) also trigger `group.updated`?
   - Recommendation: Implement only the explicitly listed trigger points from canonical refs (UserService.transferStudent, GroupService.updateGroup/deleteGroup). Archiving changes user status, not group composition.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Testcontainers (RabbitMQ + PostgreSQL) | Yes | 28.5.2 | — |
| RabbitMQ (Docker image) | Integration tests | Yes (via Docker) | rabbitmq:3.13-management-alpine pulled at test time | — |
| `org.testcontainers:rabbitmq` | Test base class | Yes (in BOM 1.20.4) | 1.20.4 | — |
| `spring-boot-starter-amqp` | Main code | Yes (already in build.gradle.kts) | Spring Boot 3.4.x managed | — |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** None.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (via `spring-boot-starter-test`) |
| Config file | `src/test/resources/application-test.yml` (existing) |
| Quick run command | `./gradlew :services:academic-service:academic-app:test --tests "*EventIntegrationTest*"` |
| Full suite command | `./gradlew :services:academic-service:academic-app:test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EVENT-01 | `group.updated` published after `updateGroup`, `deleteGroup`, `transferStudent` | integration | `./gradlew :services:academic-service:academic-app:test --tests "*GroupEventIntegrationTest*"` | No — Wave 0 |
| EVENT-02 | `semester.archived` published after semester deactivation; NOT published on rollback | integration | `./gradlew :services:academic-service:academic-app:test --tests "*SemesterEventIntegrationTest*"` | No — Wave 0 |
| EVENT-03 | `homework.published` on create, `homework.updated` on update, both with UUID `event_id` | integration | `./gradlew :services:academic-service:academic-app:test --tests "*HomeworkEventIntegrationTest*"` | No — Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :services:academic-service:academic-app:test --tests "*EventIntegrationTest*"`
- **Per wave merge:** `./gradlew :services:academic-service:academic-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/.../integration/AbstractAcademicEventIntegrationTest.java` — shared base class for EVENT-01/02/03
- [ ] `src/test/.../integration/GroupEventIntegrationTest.java` — covers EVENT-01
- [ ] `src/test/.../integration/SemesterEventIntegrationTest.java` — covers EVENT-02
- [ ] `src/test/.../integration/HomeworkEventIntegrationTest.java` — covers EVENT-03
- [ ] `testImplementation("org.testcontainers:rabbitmq")` in `build.gradle.kts` — RabbitMQContainer class

---

## Sources

### Primary (HIGH confidence)
- Spring AMQP official docs (https://docs.spring.io/spring-amqp/reference/amqp/transactions.html) — RabbitTemplate transaction behavior, `channelTransacted` flag
- Testcontainers Java official (https://java.testcontainers.org/modules/rabbitmq/) — `RabbitMQContainer` class, artifact `org.testcontainers:rabbitmq:1.20.4`
- Spring Framework Javadoc (https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html) — `@TransactionalEventListener` semantics
- `build.gradle.kts` (existing file) — confirms `spring-boot-starter-amqp` present, `testcontainers-bom:1.20.4` managed
- `AbstractAcademicCacheIntegrationTest.java` (existing file) — confirmed pattern for new base class (D-12)
- `application.yml` (existing file) — confirms `spring.rabbitmq.host: rabbitmq` already configured
- `homework.published.json` (existing file) — confirmed envelope structure for new JSON schemas

### Secondary (MEDIUM confidence)
- Spring AMQP GitHub issue #1309 (https://github.com/spring-projects/spring-amqp/issues/1309) — documents transacted RabbitTemplate + AFTER_COMMIT message loss; verified: affects only `channelTransacted=true` scenarios
- ttulka blog (https://blog.ttulka.com/transactional-events-made-easy-with-spring/) — confirms ApplicationEvent bridge pattern with AFTER_COMMIT

### Tertiary (LOW confidence)
- None

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all dependencies verified in existing `build.gradle.kts` and Testcontainers artifact verified via Maven Central
- Architecture: HIGH — Spring ApplicationEvent bridge + AFTER_COMMIT is a locked decision (D-04), existing codebase patterns for base test classes are verified
- Pitfalls: HIGH — transacted RabbitTemplate issue verified against official spring-amqp GitHub issue; AFTER_COMMIT + test @Transactional pitfall is well-documented

**Research date:** 2026-03-31
**Valid until:** 2026-04-30 (Spring Boot 3.4/Spring AMQP APIs are stable)
