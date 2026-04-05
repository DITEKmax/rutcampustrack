# Phase 20: Shared Infrastructure - Research

**Researched:** 2026-04-05
**Domain:** RabbitMQ fanout consumer pattern (Spring AMQP + aio-pika), Docker Compose service definitions, Redis key namespace
**Confidence:** HIGH

## Summary

Phase 20 connects both notification services to the existing RabbitMQ event stream. The pattern is already proven in the Attendance Service — the core task is to replicate `RabbitConfig.java` twice (once for notification-web in Java, once structurally in aio-pika for notification-bot in Python) and add both containers to docker-compose with health checks.

The notification-web service already has `spring-boot-starter-amqp` in its build.gradle.kts and RabbitMQ connection config in application.yml. It needs: (1) a `RabbitConfig` class that declares its own queue/DLQ/exchange beans, (2) a placeholder `EventConsumer` class with `@RabbitListener`, and (3) `spring-boot-starter-actuator` for the health check endpoint. The notification-bot is a Python skeleton — it needs a package layout (`bot/` package), a minimal aio-pika consumer that connects and logs messages, an aiohttp `/health` endpoint, and a Dockerfile. Both containers are added to docker-compose.

Redis key namespace `reminder:msgs:{lesson_id}:{user_id}` (RPUSH list, TTL 86400) is a decision from CONTEXT.md, requires no new library — just documentation in application.yml config and a note in the bot config.

**Primary recommendation:** Copy `RabbitConfig.java` from Attendance Service verbatim, rename all bean names to `notificationWeb*` prefix, and update the queue/routing key strings to `notification-web.events` / `notification-web.events.dlq`. Do the same structurally in aio-pika for the bot.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Queue names: `notification-web.events`, `notification-bot.events`
- **D-02:** DLQ queues: `notification-web.events.dlq`, `notification-bot.events.dlq` — manual replay only via RabbitMQ Management UI, no auto-retry TTL loops
- **D-03:** Each service declares its own RabbitConfig — copy Attendance Service pattern, no shared RabbitMQ config module
- **D-04:** notification-web health check: Spring Actuator `/actuator/health`. Add `spring-boot-starter-actuator` dependency. Docker healthcheck: `curl http://localhost:9094/actuator/health`
- **D-05:** notification-bot health check: tiny aiohttp HTTP server on port 8081 with `/health` endpoint that checks RabbitMQ connection is alive. Docker healthcheck: `curl http://localhost:8081/health`
- **D-06:** Restart policy: `unless-stopped` for both containers
- **D-07:** Redis key: `reminder:msgs:{lesson_id}:{user_id}` as Redis list (RPUSH / LRANGE 0 -1 / DEL)
- **D-08:** TTL safety net: `EXPIRE 86400` (24 hours) on each key. Normal cleanup via events, TTL prevents leaks
- **D-09:** Bot package layout: `bot/__init__.py`, `bot/__main__.py`, `bot/config.py`, `bot/handlers/`, `bot/consumers/`, `bot/grpc_client/`, `bot/services/`
- **D-10:** Config via pydantic-settings with `.env` file
- **D-11:** grpcio pinned at 1.73.0 (protobuf 5.x compatible). Update requirements.txt from 1.69.0
- **D-12:** Phase 20 creates bot skeleton AND basic aio-pika consumer that connects, receives messages, and logs them

### Claude's Discretion

- Docker resource limits (memory, CPU) — not critical for dev compose
- Exact Dockerfile contents (multi-stage, base image choice)
- notification-web RabbitConfig bean naming convention (follow Attendance Service pattern)
- Bot `__main__.py` asyncio setup and signal handling

### Deferred Ideas (OUT OF SCOPE)

None.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFRA-01 | Both notification services have dedicated RabbitMQ queues with DLQ bound to fanout exchange | RabbitConfig.java pattern (Attendance Service) directly reusable — confirmed by reading source. Spring AMQP `QueueBuilder.durable().withArgument("x-dead-letter-exchange", ...)` is the correct approach. aio-pika `declare_queue` with `arguments={"x-dead-letter-exchange": ...}` mirrors this for Python. |
| INFRA-02 | Docker-compose includes notification-web and notification-bot containers with health checks | Existing docker-compose.yml pattern with `healthcheck.test`, `depends_on`, `restart: unless-stopped` already used for all other services. notification-web needs Actuator; notification-bot needs aiohttp /health. |
| INFRA-03 | Redis key namespace `reminder:msgs:{lesson_id}:{user_id}` documented and available for bot | Redis is already in docker-compose. Key namespace is a documentation + bot config task. No new library. Pydantic Settings in bot config.py documents the key format. |
</phase_requirements>

---

## Standard Stack

### Core (Java — notification-web)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-amqp | BOM-managed (Spring Boot 3.4) | RabbitMQ integration via Spring AMQP | Already in build.gradle.kts — no install needed |
| spring-boot-starter-actuator | BOM-managed (Spring Boot 3.4) | `/actuator/health` endpoint for Docker health check | Decision D-04 — must add to build.gradle.kts |
| Jackson2JsonMessageConverter | Spring AMQP (bundled) | JSON message deserialization | Project standard — inject shared ObjectMapper (has JavaTimeModule) |

### Core (Python — notification-bot)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| aio-pika | 9.5.3 (already in requirements.txt) | Async RabbitMQ client with `connect_robust` | Project decision — STATE.md mandates aio-pika watchdog |
| aiohttp | 3.11.11 (already in requirements.txt) | HTTP server for /health endpoint | Decision D-05. Already in requirements.txt |
| pydantic-settings | 2.7.1 (already in requirements.txt) | Type-safe env config | Decision D-10 |
| grpcio | **1.73.0** (bump from 1.69.0) | gRPC client for Academic Service | Decision D-11 — pinned for protobuf 5.x compat |
| grpcio-tools | **1.73.0** (bump from 1.69.0) | Proto compilation | Must stay in sync with grpcio |

**Installation (Java):**
```bash
# Only change: add to services/notification-web/build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

**Installation (Python — version bump only):**
```
# Change in services/notification-bot/requirements.txt
grpcio==1.73.0
grpcio-tools==1.73.0
```

No new Docker images needed — all infrastructure (Redis, RabbitMQ) already in docker-compose.

---

## Architecture Patterns

### Recommended Project Structure (notification-web additions)

```
services/notification-web/src/main/java/ru/rutcampustrack/notification/
├── NotificationWebApplication.java     (exists)
├── config/
│   └── RabbitConfig.java               (NEW — copy from Attendance Service)
└── event/
    └── EventConsumer.java              (NEW — placeholder @RabbitListener)
```

### Recommended Project Structure (notification-bot — new from scratch)

```
services/notification-bot/
├── bot/
│   ├── __init__.py
│   ├── __main__.py           ← asyncio entry point, starts consumer + health server
│   ├── config.py             ← Pydantic Settings (reads .env)
│   ├── handlers/             ← (empty package placeholder — Phase 23)
│   │   └── __init__.py
│   ├── consumers/
│   │   ├── __init__.py
│   │   └── event_consumer.py ← aio-pika connect_robust + queue binding + log loop
│   ├── grpc_client/          ← (empty package placeholder — Phase 22)
│   │   └── __init__.py
│   └── services/             ← (empty package placeholder — Phase 22+)
│       └── __init__.py
├── requirements.txt          (grpcio bump)
├── Dockerfile
└── .env.example              (exists — may need Redis vars added)
```

### Pattern 1: Java RabbitConfig — Fanout + DLQ (replicate for notification-web)

**What:** Each consuming service declares the fanout exchange (idempotent), its own queue with DLQ arguments, and a DLQ queue on a direct exchange. Bean names are service-prefixed to prevent test context collisions.

**When to use:** All Spring AMQP consumers in this project.

```java
// Source: services/attendance-service/attendance-app/.../config/RabbitConfig.java
// Replicate verbatim — change "attendance" → "notificationWeb" in bean names
//                      change "attendance-service.events" → "notification-web.events"

@Bean
public FanoutExchange notificationWebEventsExchange() {
    return new FanoutExchange("rut-uit.events", true, false);
}

@Bean
public DirectExchange notificationWebDlqExchange() {
    return new DirectExchange("rut-uit.events.dlq", true, false);
}

@Bean
public Queue notificationWebEventsQueue() {
    return QueueBuilder.durable("notification-web.events")
            .withArgument("x-dead-letter-exchange", "rut-uit.events.dlq")
            .withArgument("x-dead-letter-routing-key", "notification-web.events.dlq")
            .build();
}

@Bean
public Queue notificationWebDlqQueue() {
    return QueueBuilder.durable("notification-web.events.dlq").build();
}

@Bean
public Binding notificationWebQueueBinding(FanoutExchange notificationWebEventsExchange,
                                            Queue notificationWebEventsQueue) {
    return BindingBuilder.bind(notificationWebEventsQueue).to(notificationWebEventsExchange);
}

@Bean
public Binding notificationWebDlqBinding(DirectExchange notificationWebDlqExchange,
                                          Queue notificationWebDlqQueue) {
    return BindingBuilder.bind(notificationWebDlqQueue)
            .to(notificationWebDlqExchange)
            .with("notification-web.events.dlq");
}

@Bean
public Jackson2JsonMessageConverter notificationWebJacksonMessageConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
}

@Bean
public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                      Jackson2JsonMessageConverter notificationWebJacksonMessageConverter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(notificationWebJacksonMessageConverter);
    // Do NOT set channelTransacted=true
    return template;
}
```

### Pattern 2: Java EventConsumer placeholder

**What:** Minimal `@RabbitListener` that logs all received events. Pattern matches attendance EventConsumer — switches on `event_type`, logs, no processing logic in Phase 20.

```java
// Source: services/attendance-service/.../event/EventConsumer.java (adapted)
@Component
@Slf4j
@RequiredArgsConstructor
public class EventConsumer {

    @RabbitListener(queues = "notification-web.events",
                    containerFactory = "rabbitListenerContainerFactory")
    public void onEvent(Map<String, Object> envelope) {
        String eventType = (String) envelope.get("event_type");
        log.info("[notification-web] Received event: {}", eventType);
        // Phase 21+ will add actual routing
    }
}
```

Note: The `containerFactory` must use the `notificationWebJacksonMessageConverter`. Spring Boot auto-configures `SimpleRabbitListenerContainerFactory` but only picks up a single `MessageConverter` bean. Because multiple converters are declared (one per service in tests), the `@RabbitListener` may need to reference a custom container factory or use `@Bean SimpleRabbitListenerContainerFactory` that injects the correct converter. See Pitfall 2.

### Pattern 3: Python aio-pika consumer with connect_robust

**What:** `connect_robust` reconnects automatically after RabbitMQ restart. Watchdog concern from STATE.md is addressed by `connect_robust` itself (it retries on connection loss).

```python
# Source: aio-pika docs (MEDIUM confidence — verified by project STATE.md decision)
import asyncio
import aio_pika
import json
import logging

logger = logging.getLogger(__name__)

async def start_consumer(rabbitmq_url: str):
    connection = await aio_pika.connect_robust(rabbitmq_url)
    async with connection:
        channel = await connection.channel()

        # Declare fanout exchange (idempotent)
        exchange = await channel.declare_exchange(
            "rut-uit.events",
            aio_pika.ExchangeType.FANOUT,
            durable=True
        )

        # Declare DLQ exchange
        dlq_exchange = await channel.declare_exchange(
            "rut-uit.events.dlq",
            aio_pika.ExchangeType.DIRECT,
            durable=True
        )

        # Declare DLQ queue
        dlq_queue = await channel.declare_queue(
            "notification-bot.events.dlq",
            durable=True
        )
        await dlq_queue.bind(dlq_exchange, routing_key="notification-bot.events.dlq")

        # Declare main queue with DLQ arguments
        queue = await channel.declare_queue(
            "notification-bot.events",
            durable=True,
            arguments={
                "x-dead-letter-exchange": "rut-uit.events.dlq",
                "x-dead-letter-routing-key": "notification-bot.events.dlq"
            }
        )
        await queue.bind(exchange)

        async with queue.iterator() as queue_iter:
            async for message in queue_iter:
                async with message.process():
                    body = json.loads(message.body)
                    event_type = body.get("event_type", "unknown")
                    logger.info("[notification-bot] Received event: %s", event_type)
```

### Pattern 4: aiohttp /health endpoint alongside aio-pika consumer

**What:** Both coroutines run concurrently with `asyncio.gather`. Health check endpoint inspects whether the RabbitMQ connection is still open.

```python
# Simplified structure for __main__.py
async def health_handler(request):
    # check if consumer task is alive + connection open
    return web.Response(text='{"status":"UP"}', content_type='application/json')

async def main():
    app = web.Application()
    app.router.add_get('/health', health_handler)
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, '0.0.0.0', 8081)
    await site.start()
    await start_consumer(config.rabbitmq_url)

asyncio.run(main())
```

### Pattern 5: Docker Compose service addition

**What:** Follow exact pattern of existing services in docker-compose.yml.

```yaml
# Append to services section of docker-compose.yml

  notification-web:
    build:
      context: ./services/notification-web
      dockerfile: Dockerfile
    container_name: rct-notification-web
    environment:
      RABBITMQ_USER: ${RABBITMQ_USER:-rct_user}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-rct_dev_pass}
    expose:
      - "9094"
    networks:
      - private_net
    depends_on:
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9094/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s

  notification-bot:
    build:
      context: ./services/notification-bot
      dockerfile: Dockerfile
    container_name: rct-notification-bot
    environment:
      BOT_TOKEN: ${BOT_TOKEN:-placeholder}
      RABBITMQ_URL: amqp://${RABBITMQ_USER:-rct_user}:${RABBITMQ_PASSWORD:-rct_dev_pass}@rabbitmq:5672/
      ACADEMIC_GRPC_HOST: academic-service
      ACADEMIC_GRPC_PORT: 19091
      REDIS_HOST: redis
      REDIS_PORT: 6379
    networks:
      - private_net
    depends_on:
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 15s
```

Note: `start_period` is important for notification-web — Spring Boot startup typically takes 10-20 seconds. Without `start_period`, Docker may mark the container unhealthy before it finishes booting.

### Pattern 6: Redis key namespace (documentation only in Phase 20)

**What:** Key format decided in D-07/D-08. Phase 20 documents this in bot `config.py` as a constant — no data is written in this phase.

```python
# In bot/config.py
REMINDER_KEY_TEMPLATE = "reminder:msgs:{lesson_id}:{user_id}"
REMINDER_KEY_TTL = 86400  # seconds — safety net against event loss
```

### Anti-Patterns to Avoid

- **Creating a new ObjectMapper in Jackson2JsonMessageConverter:** Causes loss of JavaTimeModule registration. Always inject the Spring-managed `ObjectMapper` bean from `JacksonAutoConfiguration`.
- **Setting `channelTransacted=true` on RabbitTemplate:** Causes message loss when used with `AFTER_COMMIT` transaction synchronization. Confirmed pitfall from Attendance Service RabbitConfig comment.
- **Using `@Enumerated(EnumType.ORDINAL)` anywhere in new entities:** Project-wide rule from CLAUDE.md.
- **Auto-declaring queue without DLQ arguments on first startup:** If the queue is created once without `x-dead-letter-exchange` and then the config is updated, RabbitMQ will reject the redeclaration with a channel error. The queue must be deleted and recreated if arguments change.
- **Bot connecting without `connect_robust`:** Plain `connect` does not reconnect after RabbitMQ restart, causing silent consumer death (STATE.md concern).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| DLQ routing | Custom error queue logic | RabbitMQ `x-dead-letter-exchange` queue argument | Built into the broker — handles nack/reject/expiry automatically |
| RabbitMQ reconnection in Python | Manual retry loop with sleep | `aio-pika.connect_robust` | Handles TCP reconnection + channel recovery — battle-tested |
| Health check HTTP in Python | Flask/FastAPI server | `aiohttp.web` (already in requirements.txt) | Minimal footprint, async-native, already a dependency |
| Container health check | Custom script | Docker `healthcheck` directive + curl | Native Docker feature, composable with `depends_on: condition: service_healthy` |

**Key insight:** The entire RabbitMQ topology (fanout + DLQ) is declarative — both the broker and the Spring AMQP / aio-pika clients declare resources idempotently. If services restart in any order, the topology self-heals.

---

## Common Pitfalls

### Pitfall 1: Multiple Jackson2JsonMessageConverter beans with Spring's auto-configured container factory

**What goes wrong:** Spring Boot auto-configures one `SimpleRabbitListenerContainerFactory`. If there are multiple `MessageConverter` beans in context (e.g., `notificationWebJacksonMessageConverter` and potentially another from future services in tests), Spring may not know which to inject into the auto-configured factory, or it may use the wrong one.

**Why it happens:** The auto-configured `RabbitListenerContainerFactory` picks up the `MessageConverter` bean by type — but only if there is exactly one. Multiple beans cause no auto-wiring.

**How to avoid:** Explicitly declare a `SimpleRabbitListenerContainerFactory` bean in `RabbitConfig.java` that injects `notificationWebJacksonMessageConverter` directly:
```java
@Bean
public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ConnectionFactory connectionFactory,
        Jackson2JsonMessageConverter notificationWebJacksonMessageConverter) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(notificationWebJacksonMessageConverter);
    return factory;
}
```
Note: The Attendance Service does NOT do this (it relies on auto-config), and it works because attendance-app runs standalone. For notification-web, follow the same approach as Attendance Service — single converter bean in the app context means auto-config picks it up correctly.

**Warning signs:** `ClassCastException: LinkedHashMap cannot be cast to` in consumer — means the message is not being deserialized via Jackson.

### Pitfall 2: Queue argument mismatch on redeclaration

**What goes wrong:** RabbitMQ throws a channel-level error (`PRECONDITION_FAILED`) if a queue is re-declared with different arguments than when it was first created.

**Why it happens:** If development RabbitMQ had `notification-web.events` queue created without DLQ arguments (e.g., from a previous prototype), the new declaration with `x-dead-letter-exchange` will fail.

**How to avoid:** In development, delete the queues from the RabbitMQ Management UI (port 15672) before first run with the new config.

**Warning signs:** Spring application fails to start with `AmqpIOException: com.rabbitmq.client.ShutdownSignalException` mentioning `PRECONDITION_FAILED`.

### Pitfall 3: Docker health check `start_period` missing for Java service

**What goes wrong:** Docker marks notification-web as `unhealthy` before Spring Boot finishes initializing (~15-20 seconds), causing dependent services to fail their `depends_on: condition: service_healthy` check.

**Why it happens:** Spring Boot startup includes component scanning, Flyway, gRPC client connection setup — all take time. The first health check fires after `interval` (15s) which may be before startup completes.

**How to avoid:** Set `start_period: 30s` in the healthcheck config. Docker will not count failures during the start period.

**Warning signs:** Container enters `unhealthy` state immediately and never recovers despite the service running fine.

### Pitfall 4: Bot /health endpoint only checks if aiohttp is alive, not RabbitMQ

**What goes wrong:** Docker reports healthy, but the RabbitMQ consumer has silently died (connection closed without reconnect).

**Why it happens:** aiohttp can respond to HTTP while the aio-pika consumer coroutine has raised an exception.

**How to avoid:** Health handler checks that the consumer task is not done and the connection is not closed:
```python
async def health_handler(request):
    if consumer_task.done() or connection.is_closed:
        raise web.HTTPServiceUnavailable()
    return web.Response(text='{"status":"UP"}')
```

**Warning signs:** Bot appears healthy in Docker but no events are being logged.

### Pitfall 5: notification-web Dockerfile missing curl for health check

**What goes wrong:** Docker health check fails with `exec: "curl": executable file not found`.

**Why it happens:** Minimal JRE base images (e.g., `eclipse-temurin:21-jre-alpine`) do not include curl.

**How to avoid:** Either install curl in the Dockerfile (`RUN apk add --no-cache curl`) or use a wget-based health check test:
```yaml
test: ["CMD-SHELL", "wget -qO- http://localhost:9094/actuator/health || exit 1"]
```
Alpine-based images include wget but not curl.

---

## Code Examples

### Java: Full RabbitConfig for notification-web

Adapt from Attendance Service — change all `attendance` prefixes to `notificationWeb`, update queue/routing key strings:

```java
// Bean naming: notificationWebEventsExchange, notificationWebDlqExchange,
//              notificationWebEventsQueue, notificationWebDlqQueue,
//              notificationWebQueueBinding, notificationWebDlqBinding,
//              notificationWebJacksonMessageConverter
// Queue: "notification-web.events"
// DLQ queue: "notification-web.events.dlq"
// DLQ routing key: "notification-web.events.dlq"
```

### Java: application.yml additions for actuator

```yaml
# Add to services/notification-web/src/main/resources/application.yml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

### Python: Minimal config.py

```python
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    bot_token: str
    rabbitmq_url: str = "amqp://rct_user:rct_dev_pass@rabbitmq:5672/"
    redis_host: str = "redis"
    redis_port: int = 6379
    health_port: int = 8081

    # Redis key namespace (documented here, used in Phase 25)
    # Key: reminder:msgs:{lesson_id}:{user_id}
    # Type: Redis List (RPUSH to add, LRANGE 0 -1 to read, DEL to cleanup)
    # TTL: 86400 seconds (24 hours) — safety net
    reminder_key_ttl: int = 86400

    class Config:
        env_file = ".env"

config = Settings()
```

### Python: Minimal Dockerfile

```dockerfile
FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

CMD ["python", "-m", "bot"]
```

The `CMD ["python", "-m", "bot"]` convention invokes `bot/__main__.py` — standard Python package entry point.

---

## Runtime State Inventory

> This is a greenfield phase (new containers, new queues). No rename or migration involved.

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data | None — notification-web.events and notification-bot.events queues do not yet exist in RabbitMQ | Queue creation is automatic on first service startup (idempotent declare) |
| Live service config | None — no existing notification containers in docker-compose | Add service definitions |
| OS-registered state | None | None |
| Secrets/env vars | BOT_TOKEN in .env.example — not yet set | Developer must provide real token in `.env` for bot to connect to Telegram (not required for Phase 20 queue binding test) |
| Build artifacts | notification-bot has no Dockerfile yet | Create Dockerfile as part of this phase |

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Container build + compose | Confirmed (project uses docker compose up -d) | — | None — required |
| RabbitMQ (container) | Queue binding test | Available via docker-compose | rabbitmq:3.13-management-alpine | None |
| Redis (container) | Bot config namespace | Available via docker-compose | redis:7-alpine | None |
| curl / wget | Docker health check test | Alpine images may lack curl | — | Use wget fallback in healthcheck |
| Python 3.12 | Bot Dockerfile base image | Available in Docker Hub (python:3.12-slim) | 3.12 | python:3.11-slim (minor) |
| Java 21 JRE | notification-web Docker image | Available (eclipse-temurin:21-jre-alpine) | 21 | — |

**Missing dependencies with no fallback:** None — all are available or buildable from public images.

**Missing dependencies with fallback:** curl in Alpine — use wget in Docker healthcheck for notification-web, or `RUN apk add --no-cache curl` in Dockerfile.

---

## Validation Architecture

> `nyquist_validation` key absent from `.planning/config.json` — treating as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (notification-web) |
| Config file | `services/notification-web/build.gradle.kts` (testImplementation already present) |
| Quick run command | `./gradlew :services:notification-web:test` |
| Full suite command | `./gradlew :services:notification-web:test` |

Note: notification-bot is Python with no test framework set up in Phase 20 (skeleton only). Validation is manual: `docker compose up notification-bot` + check logs for "Received event".

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INFRA-01 | RabbitConfig declares queues and DLQ bindings correctly | Integration (Spring AMQP test with embedded broker) | `./gradlew :services:notification-web:test` | No — Wave 0 |
| INFRA-02 | Docker containers start with health checks passing | Manual / docker compose | `docker compose up -d notification-web notification-bot && docker compose ps` | N/A — manual |
| INFRA-03 | Redis key namespace documented in bot config | Manual inspection | — | N/A — docs/config |

### Sampling Rate

- **Per task commit:** `./gradlew :services:notification-web:test`
- **Per wave merge:** `./gradlew :services:notification-web:test`
- **Phase gate:** Full suite green + docker compose health checks passing before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `services/notification-web/src/test/java/ru/rutcampustrack/notification/config/RabbitConfigTest.java` — verifies queue/DLQ bean names and arguments (INFRA-01). Use `@SpringBootTest` with `spring-rabbit-test` or mock the `ConnectionFactory`.

---

## Sources

### Primary (HIGH confidence)

- `services/attendance-service/attendance-app/src/main/java/.../config/RabbitConfig.java` — Verified source of truth for the fanout + DLQ pattern in this project
- `services/attendance-service/attendance-app/src/main/java/.../event/EventConsumer.java` — Verified `@RabbitListener` pattern
- `docker-compose.yml` — Verified existing container/network/healthcheck patterns
- `services/notification-web/build.gradle.kts` — Verified existing dependencies
- `services/notification-web/src/main/resources/application.yml` — Verified existing RabbitMQ connection config
- `services/notification-bot/requirements.txt` — Verified current Python dependency versions
- `.planning/STATE.md` — grpcio pin decision (1.73.0), aio-pika watchdog requirement

### Secondary (MEDIUM confidence)

- aio-pika `connect_robust` + `declare_queue` with DLQ arguments — API based on aio-pika 9.x documentation, consistent with aio-pika 9.5.3 in requirements.txt
- Python `python -m bot` entry point convention — standard Python packaging behavior

### Tertiary (LOW confidence)

- None — all critical claims verified against project source files.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all dependencies already in project files, versions verified by reading source
- Architecture: HIGH — direct copy of proven Attendance Service pattern with name substitution
- Pitfalls: HIGH (Pitfall 1-3) / MEDIUM (Pitfall 4-5) — Pitfall 1-3 documented in existing Attendance RabbitConfig comments, Pitfall 4-5 derived from Docker healthcheck behavior

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (stable domain — Spring AMQP and aio-pika APIs are stable)
