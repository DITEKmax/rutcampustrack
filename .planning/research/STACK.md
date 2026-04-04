# Stack Research

**Domain:** Notification Service (Web + Bot) — real-time push via WebSocket and Telegram
**Researched:** 2026-04-04
**Confidence:** HIGH (versions verified against PyPI, official docs, existing monorepo)

---

## Context: What Already Exists

This is the v5.0 milestone on an existing Java 21 + Spring Boot 3.4.1 monorepo. The infrastructure is already running:

| Infrastructure | Details |
|----------------|---------|
| RabbitMQ fanout exchange | `rut-uit.events` (fanout, durable) — all 4 services already publish to it |
| Redis | `redis:7-alpine` — shared container, already used by Auth Service (OTP/JWT) and Attendance Service (dedup/rate-limit) |
| `spring-boot-starter-amqp` | Already declared in `notification-web/build.gradle.kts` |
| `spring-boot-starter-websocket` | Already declared in `notification-web/build.gradle.kts` |
| Python `requirements.txt` | Already has `aiogram==3.15.0`, `aio-pika==9.5.3`, `grpcio==1.69.0`, `grpcio-tools==1.69.0`, `protobuf==5.29.3` |

**Existing `notification-web/build.gradle.kts` is minimal** — missing Redis, JWT parsing, SpringDoc, and Lombok are needed additions.

**Existing `notification-bot/requirements.txt` has stale versions** — verified against PyPI April 2026.

---

## Notification Web (Java) — Required Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| `spring-boot-starter-websocket` | Managed by Boot 3.4.1 BOM (Spring WebSocket 6.2.x) | WebSocket endpoint + STOMP broker | Already in build.gradle.kts; Spring Boot 3.4 auto-configures with `@EnableWebSocketMessageBroker`; STOMP adds topic routing needed for group-based fan-out |
| `spring-boot-starter-amqp` | Managed by Boot 3.4.1 BOM (Spring AMQP 3.2.x) | RabbitMQ consumer for event ingestion | Already in build.gradle.kts; `@RabbitListener` on durable named queue bound to fanout exchange |
| `spring-boot-starter-data-redis` | Managed by Boot 3.4.1 BOM | Read `telegram_id` → `user_id` mapping (if persisted by bot) | Redis is the shared auth-layer store; notification-web may need to look up WebSocket session by group membership |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-starter-web` | Managed by Boot 3.4.1 BOM | Needed for REST health endpoint (`/actuator/health`) and error handling | Always needed alongside websocket starter |
| `io.jsonwebtoken:jjwt-api` | `0.12.6` | Parse JWT from WebSocket handshake query param to extract `user_id`, `group_id`, `role` | Auth Service already uses jjwt in this monorepo — use same version to stay consistent |
| `io.jsonwebtoken:jjwt-impl` | `0.12.6` | jjwt runtime (runtimeOnly) | Runtime companion to jjwt-api |
| `io.jsonwebtoken:jjwt-jackson` | `0.12.6` | Jackson-based JWT serialization (runtimeOnly) | Required when using Jackson (default in Spring Boot) |
| `org.springframework.boot:spring-boot-starter-aop` | Managed by Boot 3.4.1 BOM | `@RequireRole` pattern if any REST endpoints need role protection | Optional — only if REST endpoints beyond WebSocket are added |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | `2.7.0` | Swagger UI | Only if REST endpoints documented; match version used in other services |
| Lombok | Managed by Boot 3.4.1 BOM | Entity/config classes | `compileOnly` + `annotationProcessor`; already in build.gradle.kts |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `spring-boot-starter-test` | Unit + integration tests | Already in build.gradle.kts |
| `org.testcontainers:rabbitmq` | RabbitMQ integration test | BOM 1.20.4 — same as other services; test consumer wiring |
| `org.springframework.boot:spring-boot-testcontainers` | `@ServiceConnection` wiring | Boot 3.4.1 BOM — idiomatic approach |

---

## STOMP vs Raw WebSocket — Decision

**Use STOMP over WebSocket.**

Rationale:

1. **Group fan-out requires topic routing.** The Angular web panel needs to subscribe to notifications for a specific group. With STOMP, each client subscribes to `/topic/group/{groupId}` and the server sends to `SimpMessagingTemplate.convertAndSend("/topic/group/42", event)`. Raw WebSocket requires building this routing layer manually.

2. **Already provided by the starter.** `spring-boot-starter-websocket` includes both the raw handler API and the STOMP broker relay/in-memory broker. Using STOMP does not require any additional dependency.

3. **Java 21 virtual threads make scalability concerns irrelevant.** With `spring.threads.virtual.enabled=true`, each WebSocket connection costs a few KB rather than 1 MB from a platform thread. The Angular admin panel serves tens to hundreds of simultaneous connections — far below any threshold where raw WebSocket gains performance advantage.

4. **JWT auth on handshake is a solved pattern with STOMP.** A `HandshakeInterceptor` extracts the JWT from the query string (`?token=...`) and populates `HttpSession` attributes before the STOMP CONNECT frame. This prevents unauthenticated clients from consuming connection resources.

**Do NOT use SockJS.** SockJS provides fallback for environments without WebSocket (Flash, XHR-streaming). The Angular web panel targets modern browsers where WebSocket is universally available. SockJS adds unnecessary complexity.

### Key STOMP Configuration

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");     // in-memory broker
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")                // no SockJS
                .addInterceptors(jwtHandshakeInterceptor())
                .setAllowedOriginPatterns("*");    // tighten in production
    }
}
```

### JWT Auth on WebSocket Handshake

```java
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = ((ServletServerHttpRequest) request).getServletRequest().getQueryString();
        // parse ?token=<jwt>, validate signature with public key from Auth Service
        // populate attributes: userId, groupId, role
        // return false → 401, connection refused before any resource is allocated
        return true;
    }
}
```

### Sending to Group Topics

```java
@Component
public class NotificationPushService {

    private final SimpMessagingTemplate messagingTemplate;

    public void pushToGroup(Long groupId, Object payload) {
        messagingTemplate.convertAndSend("/topic/group/" + groupId, payload);
    }
}
```

### RabbitMQ Consumer → Push Bridge

```java
@Component
public class EventConsumer {

    @RabbitListener(queues = "notification-web.events")
    public void onEvent(Map<String, Object> envelope) {
        String eventType = (String) envelope.get("event_type");
        // extract groupId from payload, map to WebSocket topic
        // delegate to NotificationPushService
    }
}
```

### RabbitMQ Queue Configuration

Queue naming pattern follows the established convention from Attendance Service:

```java
@Bean
public Queue notificationWebQueue() {
    return QueueBuilder.durable("notification-web.events")
            .withArgument("x-dead-letter-exchange", "rut-uit.events.dlq")
            .withArgument("x-dead-letter-routing-key", "notification-web.events.dlq")
            .build();
}

@Bean
public Binding notificationWebQueueBinding(FanoutExchange eventsExchange, Queue notificationWebQueue) {
    return BindingBuilder.bind(notificationWebQueue).to(eventsExchange);
}
```

The fanout exchange `rut-uit.events` is already declared by every other service with identical parameters (`durable=true, autoDelete=false`). Declaring it again in notification-web is safe — Spring AMQP declares idempotently.

---

## Notification Bot (Python) — Required Stack

### Current vs Verified Versions

The existing `requirements.txt` uses stale pinned versions. Verified against PyPI on 2026-04-04:

| Package | Current Pin | Latest Stable | Recommended Pin | Reason |
|---------|-------------|---------------|-----------------|--------|
| `aiogram` | `3.15.0` | `3.27.0` | `==3.27.0` | Latest stable; Telegram Bot API 9.5 support; no breaking changes within 3.x |
| `aio-pika` | `9.5.3` | `9.6.2` | `==9.6.2` | Latest stable; `connect_robust` for auto-reconnect required for production |
| `aiohttp` | `3.11.11` | `3.11.x` | `==3.11.11` | Aiogram's transport; keep pinned, aiogram pulls it as transitive dependency |
| `pydantic` | `2.10.4` | `2.x` | `==2.10.4` | Stable; used for settings validation only; no need to upgrade |
| `pydantic-settings` | `2.7.1` | `2.x` | `==2.7.1` | Stable; upgrade is optional |
| `grpcio` | `1.69.0` | `1.80.0` | `==1.73.0` | See explanation below |
| `grpcio-tools` | `1.69.0` | `1.80.0` | `==1.73.0` | Must match grpcio exactly |
| `protobuf` | `5.29.3` | `6.x` | `==5.29.3` | See explanation below — do NOT upgrade to 6.x yet |

### grpcio Version Decision: Pin at 1.73.0

The latest grpcio/grpcio-tools is 1.80.0, but:

- `grpcio-tools` 1.73.0+ requires `protobuf >=6.30.0, <7.0.0` — a major version jump from the current `protobuf==5.29.3`.
- `protobuf` 6.x is a significant breaking change for generated Python stubs (new `protoc` output format).
- The Java services use `protoc:3.25.3` (protobuf 3.25.x). The Python-generated stubs from `grpcio-tools` 1.73.0+ with `protobuf` 6.x would generate code incompatible with what the Java server expects at the wire level in edge cases.
- **Safe upgrade path:** Pin `grpcio==1.73.0` and `grpcio-tools==1.73.0` which still support `protobuf>=5.26.1,<6.0.0` — compatible with the current `protobuf==5.29.3` pin.

This avoids the dependency hell reported in grpc/grpc issue #39012 (protobuf 6 + grpcio-tools conflict).

**Recommended `requirements.txt`:**

```
aiogram==3.27.0
aio-pika==9.6.2
aiohttp==3.11.11
pydantic==2.10.4
pydantic-settings==2.7.1
grpcio==1.73.0
grpcio-tools==1.73.0
protobuf==5.29.3
redis==5.2.1
```

**Add `redis==5.2.1`:** Not in current requirements.txt but needed for `reminder:msgs:{lesson_id}:{user_id}` storage. The `redis` package (aioredis successor) provides async `asyncio`-compatible client compatible with the existing `redis:7-alpine` container. Use `redis.asyncio` submodule.

### Core Technologies (Python Bot)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| `aiogram` | `3.27.0` | Telegram Bot framework | Async-native, FSM support, `InlineKeyboardMarkup` for Mini App buttons, `Bot.delete_message()` for reminder cleanup; the established choice for this monorepo |
| `aio-pika` | `9.6.2` | RabbitMQ async consumer | Built on `aiormq` for asyncio; `connect_robust` provides transparent reconnect with state recovery — critical because the bot must survive broker restarts without losing queue bindings |
| `grpcio` | `1.73.0` | gRPC runtime for Academic Service calls | Already scaffolded in existing requirements.txt; `grpc.aio` channel for non-blocking calls from asyncio event loop |
| `grpcio-tools` | `1.73.0` | Code generation from `.proto` files | Generates Python stubs from `proto/academic.proto`; run once at build time, not at runtime |
| `redis` | `5.2.1` | Reminder `message_id` storage | Async client (`redis.asyncio`) for storing/retrieving `message_id` per `{lesson_id}:{user_id}` for cleanup on lesson close |
| `pydantic-settings` | `2.7.1` | Configuration from `.env` / environment variables | Type-safe config model for `BOT_TOKEN`, `RABBITMQ_URL`, gRPC host/port, Redis URL |

### Proto Code Generation (Python Bot)

The `proto/academic.proto` file lives at the monorepo root. Generate Python stubs with:

```bash
python -m grpc_tools.protoc \
  -I /path/to/rutcampustrack/proto \
  --python_out=./generated \
  --grpc_python_out=./generated \
  academic.proto
```

This produces `academic_pb2.py` and `academic_pb2_grpc.py`. Commit these generated files into the bot's source tree — they are stable artifacts that only change when `academic.proto` changes.

**Use `grpc.aio` (async) channel, NOT the synchronous blocking stub:**

```python
import grpc
from generated import academic_pb2, academic_pb2_grpc

async def get_group_members(channel: grpc.aio.Channel, group_id: int):
    stub = academic_pb2_grpc.AcademicGrpcServiceStub(channel)
    response = await stub.GetGroupMembers(
        academic_pb2.GroupMembersRequest(group_id=group_id)
    )
    return response.students
```

Creating the channel at startup and reusing it avoids connection overhead per call:

```python
channel = grpc.aio.insecure_channel("academic-service:19091")
```

### aio-pika Consumer Pattern (Bot + asyncio event loop)

The bot runs a single `asyncio` event loop (Dispatcher + polling). The RabbitMQ consumer runs in the same loop using `aio-pika`:

```python
async def main():
    bot = Bot(token=settings.bot_token)
    dp = Dispatcher()

    # Start consuming in background — same event loop as bot
    connection = await aio_pika.connect_robust(settings.rabbitmq_url)
    channel = await connection.channel()
    exchange = await channel.declare_exchange(
        "rut-uit.events", aio_pika.ExchangeType.FANOUT, durable=True
    )
    queue = await channel.declare_queue(
        "notification-bot.events", durable=True
    )
    await queue.bind(exchange)

    async def on_message(message: aio_pika.IncomingMessage):
        async with message.process():     # auto-ack on success, nack on exception
            body = json.loads(message.body)
            await handle_event(body, bot)

    await queue.consume(on_message)

    # Start bot polling (blocks until stopped)
    await dp.start_polling(bot)
```

**Why `connect_robust`:** The bot is a long-running process. RabbitMQ may restart during broker maintenance. `connect_robust` transparently re-establishes the connection, re-declares the queue, and re-binds to the exchange — no manual reconnection logic required.

**Why `message.process()` context manager:** Delivers at-least-once semantics. If `handle_event` raises an exception, the message is nacked and requeued. Without this, a crash during Telegram delivery would silently lose the notification.

### Redis Pattern for Reminder message_ids

```python
import redis.asyncio as aioredis

redis_client = aioredis.from_url(settings.redis_url)

async def store_reminder(lesson_id: int, user_id: int, message_id: int):
    key = f"reminder:msgs:{lesson_id}:{user_id}"
    await redis_client.lpush(key, message_id)
    await redis_client.expire(key, 3600 * 6)   # TTL: 6 hours (lesson won't exceed this)

async def pop_reminders(lesson_id: int, user_id: int) -> list[int]:
    key = f"reminder:msgs:{lesson_id}:{user_id}"
    ids = await redis_client.lrange(key, 0, -1)
    await redis_client.delete(key)
    return [int(i) for i in ids]
```

This pattern stores up to 3 reminder `message_id` values per student per lesson. On `lesson.closed`, `pop_reminders` retrieves them all for deletion via `bot.delete_message()`.

---

## Dependency Management: pip (requirements.txt) over Poetry

**Decision: Keep `requirements.txt`.** Do not introduce Poetry.

Rationale:
- The bot already has a `requirements.txt` with explicit pins — this is the established pattern.
- The service is a single-container microservice with a fixed, small dependency set (8 packages). Poetry's value is dependency conflict resolution for complex trees — this does not apply here.
- Docker build simplicity: `pip install -r requirements.txt` is one line in a Dockerfile; Poetry in Docker requires either multi-stage build complexity or installing Poetry in the image.
- Solo developer context: Poetry adds cognitive overhead without commensurate benefit at this scale.

Add a `requirements-dev.txt` for development tools only (linters, test runners):

```
pytest==8.3.4
pytest-asyncio==0.24.0
mypy==1.13.0
ruff==0.8.6
```

---

## Version Compatibility Summary

| Dependency | Version | Source | Confidence |
|------------|---------|--------|------------|
| `spring-boot-starter-websocket` | Spring WebSocket 6.2.x (Boot BOM) | Spring Boot 3.4.1 BOM | HIGH |
| `spring-boot-starter-amqp` | Spring AMQP 3.2.x (Boot BOM) | Spring Boot 3.4.1 BOM | HIGH |
| `spring-boot-starter-data-redis` | Spring Data Redis 3.4.x (Boot BOM) | Spring Boot 3.4.1 BOM | HIGH |
| `jjwt-api/impl/jackson` | `0.12.6` | Match existing auth-service version | HIGH |
| `aiogram` | `3.27.0` | PyPI verified 2026-04-04 | HIGH |
| `aio-pika` | `9.6.2` | PyPI verified 2026-04-04 | HIGH |
| `grpcio` | `1.73.0` | PyPI verified; protobuf 5.x compatible | HIGH |
| `grpcio-tools` | `1.73.0` | PyPI verified; must match grpcio | HIGH |
| `protobuf` | `5.29.3` | Keep current; 6.x incompatible with 1.73.0 | HIGH |
| `redis` (Python) | `5.2.1` | PyPI; asyncio submodule for non-blocking ops | MEDIUM |
| STOMP broker | In-memory (`enableSimpleBroker`) | No external broker needed for single-instance | HIGH |

---

## Alternatives Considered

| Recommended | Alternative | Why Not |
|-------------|-------------|---------|
| STOMP over WebSocket | Raw WebSocket handler | Group fan-out via `/topic/group/{id}` requires topic routing layer; STOMP provides it for free; raw WebSocket requires building routing manually |
| In-memory STOMP broker | External STOMP broker (ActiveMQ, RabbitMQ STOMP plugin) | Single instance (VPS deploy); no horizontal scaling requirement in v5.0; external broker adds operational complexity without benefit |
| No SockJS | SockJS enabled | Angular web panel targets modern browsers where native WebSocket is universal; SockJS adds a JavaScript library dependency and fallback complexity for no benefit |
| `grpcio==1.73.0` | Latest `grpcio==1.80.0` | 1.80.0 requires protobuf 6.x; protobuf 6.x is a breaking change requiring regeneration and risks wire-level incompatibility with Java server; 1.73.0 works with existing `protobuf==5.29.3` |
| `grpc.aio` async channel | Blocking stub in thread | Bot runs in a single asyncio event loop; blocking stub would block the loop during gRPC calls, preventing Telegram message delivery during Academic Service queries |
| `connect_robust` | `connect` | `connect` does not reconnect on broker restart; `connect_robust` is production requirement for a long-running daemon |
| `redis` Python package | `aioredis` (deprecated) | `aioredis` merged into `redis>=4.2.0` as `redis.asyncio`; `aioredis` is unmaintained since 2022 |
| `requirements.txt` | Poetry | Bot has 8 stable dependencies; Poetry's value (dependency conflict resolution, lock file generation) is not worth the Docker complexity for this scope |

---

## What NOT to Add

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| SockJS (`withSockJS()`) | Targets modern browsers only; SockJS fallback unused and adds client-side library | Plain WebSocket endpoint without SockJS |
| External STOMP broker (RabbitMQ STOMP plugin) | Single-instance VPS; in-memory broker sufficient; external broker is needed only for horizontal scaling | `enableSimpleBroker("/topic")` |
| `grpcio==1.80.0` + `protobuf==6.x` | Breaks generated stub compatibility with `protobuf==5.29.3`; requires proto regeneration and Java `protoc` version alignment | `grpcio==1.73.0` + `protobuf==5.29.3` |
| `aioredis` | Unmaintained since 2022; merged into `redis` package | `redis==5.2.1` with `redis.asyncio` submodule |
| `grpc-server-spring-boot-starter` in notification-web | Notification Web exposes no gRPC server; adding it opens port 19094 unnecessarily | Only `grpc-client-spring-boot-starter` if gRPC client ever needed |
| `new ObjectMapper()` in notification-web `RabbitConfig` | Bypasses Spring Boot's registered Jackson modules (JavaTimeModule etc.), risks deserialization failures | Inject the Spring Boot-managed `ObjectMapper` bean (same pattern as Attendance Service `RabbitConfig.java`) |
| Spring Security (`spring-boot-starter-security`) | Other services use `@RequireRole` AOP instead of Spring Security; WebSocket JWT validation is done at handshake via `HandshakeInterceptor` | `HandshakeInterceptor` with manual JWT parse using jjwt |

---

## Complete Target `notification-web/build.gradle.kts`

```kotlin
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "ru.rutcampustrack"
version = "0.1.0"

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // JWT parsing at WebSocket handshake
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:rabbitmq")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

## Complete Target `notification-bot/requirements.txt`

```
aiogram==3.27.0
aio-pika==9.6.2
aiohttp==3.11.11
pydantic==2.10.4
pydantic-settings==2.7.1
grpcio==1.73.0
grpcio-tools==1.73.0
protobuf==5.29.3
redis==5.2.1
```

---

## Sources

- `services/notification-web/build.gradle.kts` — existing state (starting point)
- `services/notification-bot/requirements.txt` — existing state (stale versions corrected above)
- `services/attendance-service/attendance-app/src/main/java/.../config/RabbitConfig.java` — DLQ pattern, ObjectMapper injection pattern to replicate
- `services/attendance-service/attendance-app/build.gradle.kts` — jjwt and testcontainers BOM version already validated in monorepo
- `proto/academic.proto` — gRPC contract for Python stub generation (`GetGroupMembers`, `GetUserById`)
- `docker-compose.yml` — confirms `redis:7-alpine` container; confirms `rabbitmq:3.13-management-alpine`
- [aiogram PyPI](https://pypi.org/project/aiogram/) — `3.27.0` verified 2026-04-04 (HIGH)
- [aio-pika PyPI](https://pypi.org/project/aio-pika/) — `9.6.2` verified 2026-04-04 (HIGH)
- [grpcio PyPI](https://pypi.org/project/grpcio/) — `1.80.0` latest; `1.73.0` chosen for protobuf 5.x compat (HIGH)
- [grpcio-tools PyPI](https://pypi.org/project/grpcio-tools/) — `1.80.0` latest; `1.73.0` pinned to match grpcio (HIGH)
- [grpc/grpc issue #39012](https://github.com/grpc/grpc/issues/39012) — protobuf 6 + grpcio-tools conflict documented (HIGH)
- [WebSocket.org Spring Boot guide](https://websocket.org/guides/frameworks/spring-boot/) — STOMP vs raw WebSocket decision matrix (MEDIUM)
- [Spring Boot 3 WebSocket JWT auth](https://medium.com/@poojithairosha/spring-boot-3-authenticate-websocket-connections-with-jwt-tokens-2b4ff60532b6) — HandshakeInterceptor JWT pattern (MEDIUM)
- [aio-pika docs: connect_robust](https://docs.aio-pika.com/quick-start.html) — auto-reconnect semantics (HIGH)

---
*Stack research for: RutCampusTrack v5.0 Notification Service (Web + Bot) — new capabilities only*
*Researched: 2026-04-04*
