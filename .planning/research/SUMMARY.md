# Project Research Summary

**Project:** RutCampusTrack v5.0 — Notification Service (Web + Bot)
**Domain:** Real-time push delivery for university attendance system — WebSocket to Angular panel + Telegram bot
**Researched:** 2026-04-04
**Confidence:** HIGH

## Executive Summary

RutCampusTrack v5.0 adds two notification consumers to an already-operational microservice backbone. The infrastructure (RabbitMQ fanout exchange `rut-uit.events`, Redis, Academic Service gRPC `GetGroupMembers`, Auth Service OTP endpoints) is fully operational from phases 1-4. The two notification components — `notification-web` (Java Spring Boot WebSocket) and `notification-bot` (Python Aiogram 3) — are event-driven consumers only: they receive events from the fanout exchange and deliver them to clients. No existing service requires modification beyond minor docker-compose dependency changes and a possible small addition to the Auth Service OTP response (see Gaps section).

The recommended approach is STOMP over WebSocket for the Java service (in-memory broker, group-scoped topics via `SimpMessagingTemplate`, JWT validated at handshake via `JwtHandshakeInterceptor`) and Aiogram 3 with `aio-pika` for the Python bot (async consumer sharing the asyncio event loop, `connect_robust` for reconnection). Both services bind independent durable queues (`notification-web.events`, `notification-bot.events`) to the existing `rut-uit.events` fanout exchange. The bot additionally calls Academic Service gRPC for `GetGroupMembers` to resolve `telegram_id`s, uses Redis for reminder `message_id` tracking (key pattern `reminder:msgs:{lesson_id}:{user_id}` as a list), and calls Auth Service REST for the OTP `/login` flow. The web service routes purely by `group_id` extracted from the JWT at handshake — no gRPC needed at event-processing time.

The primary risks are Telegram rate limiting (30 msg/sec global, ~1 msg/sec per-chat soft limit) causing 429 errors during concurrent lesson fan-out, and an aio-pika known bug where `connect_robust` reconnects at the TCP level but fails to restore the channel consumer after RabbitMQ restart. Both require explicit countermeasures from day one: a throttled `asyncio.Queue`-based send queue for the bot and a consumer watchdog coroutine. A secondary risk is the reminder `message_id` structure — it must be a Redis list (`RPUSH`), not a string (`SET`), or the first two out of three reminders per student become un-deletable on `lesson.closed`.

---

## Key Findings

### Recommended Stack

The notification-web `build.gradle.kts` requires three additions beyond its minimal scaffold: `spring-boot-starter-data-redis` (for any future session metadata lookups), `io.jsonwebtoken:jjwt-api/impl/jackson:0.12.6` (JWT parsing at WebSocket handshake, matching auth-service's exact version), and Testcontainers RabbitMQ for integration tests. The STOMP broker is in-memory (`enableSimpleBroker("/topic")`) — no external STOMP broker is needed for single-instance VPS deployment.

The notification-bot `requirements.txt` has stale versions that must be updated. Aiogram upgrades from `3.15.0` to `3.27.0`. The grpcio/grpcio-tools versions must be pinned at `1.73.0`, not the latest `1.80.0` — the latest requires `protobuf>=6.x` which is a breaking change incompatible with the existing `protobuf==5.29.3` used by the Java services' `.proto` toolchain. A new `redis==5.2.1` dependency (the `aioredis` successor via `redis.asyncio`) must be added for reminder storage.

**Core technologies:**
- `spring-boot-starter-websocket` (Boot BOM): STOMP endpoint with group-topic routing — already declared in build.gradle.kts
- `spring-boot-starter-amqp` (Boot BOM): RabbitMQ consumer with DLQ support — already declared in build.gradle.kts
- `jjwt-api:0.12.6`: JWT parsing at WebSocket handshake — same version as auth-service for consistency
- `aiogram==3.27.0`: Telegram bot framework, async-native, inline keyboards for Mini App check-in button
- `aio-pika==9.6.2`: RabbitMQ async consumer, `connect_robust` for transparent auto-reconnect
- `grpcio==1.73.0` + `grpcio-tools==1.73.0`: gRPC async client (`grpc.aio`) for Academic Service; protobuf 5.x compatible
- `redis==5.2.1`: Async Redis client (`redis.asyncio`) for reminder `message_id` list storage

### Expected Features

The notification layer consumes 8 event types from the fanout exchange. The core engagement loop is: `lesson.started` → bot sends "Отметиться" inline button to each student + stores returned `message_id` in Redis → `attendance.marked` (status=present) → bot immediately deletes that student's reminder → `lesson.closed` → bot deletes all remaining reminders via Redis LRANGE. This cleanup lifecycle is a first-class business requirement from CLAUDE.md ("После пары — удалить сообщения"). The web service is a simpler parallel path: every event that reaches the bot also pushes a structured JSON message to the relevant STOMP group topic for the Angular web panel.

**Must have (v5.0 — table stakes):**
- RabbitMQ consumer infrastructure for both services — prerequisite for all notification features
- WebSocket endpoint `/ws` with JWT auth via `JwtHandshakeInterceptor` (credentials via query param)
- Group-based STOMP session routing with in-memory `GroupSessionRegistry` (`ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>>`)
- `lesson.started` / `lesson.cancelled` / `homework.published` → WebSocket push to group sessions
- `excuse.requested` / `late_checkin.requested` → WebSocket push to headman session (routed by `group_id`)
- Bot `/start` (account linking, initial credential delivery) + `/login` (OTP flow) + `/status` commands
- gRPC `GetGroupMembers` client in bot using `grpc.aio` channel (non-blocking async)
- `lesson.started` → bot sends inline "Отметиться" button to each student; stores `message_id` via Redis `RPUSH`
- `lesson.closed` → bot reads all `message_id`s via `LRANGE` and calls `bot.delete_message` for each
- `attendance.marked` (present) → bot immediately deletes that student's reminder messages
- `lesson.cancelled` + `homework.published` → bot broadcasts text notifications to group

**Should have (v5.1 differentiators):**
- 3-stage reminder lifecycle: stage 2 at lesson midpoint + stage 3 near lesson end (asyncio scheduling)
- `excuse.requested` → headman Telegram notification with approve/reject inline callback buttons (requires bot JWT auth + Attendance Service REST call)
- `late_checkin.requested` → plain text notification to headman via Telegram
- `homework.updated` → both channels (identical handler logic to `homework.published`)

**Defer (v5.2+):**
- Notification history / inbox (requires new MongoDB collection + REST API)
- Notification preferences / mute by event type (Redis hash per user)
- Admin broadcast announcements via bot
- WebSocket reconnect state recovery / event buffer per user

### Architecture Approach

Both notification services are pure event consumers with no REST API surface. Notification-web is a stateless WebSocket push relay: it binds a RabbitMQ queue, maps `event_type` to a `NotificationMessage` DTO, and delivers to the appropriate STOMP group topic via `SimpMessagingTemplate`. No database, no gRPC calls during event processing — all routing is by `group_id` already present in connected sessions. Notification-bot is more complex: it has three independent input channels (RabbitMQ events, Telegram long-polling, asyncio scheduled tasks) and calls three external services (Academic gRPC, Auth REST, Redis). The bot is the only component that bridges Telegram user identity to system identity.

**Major components:**
1. **notification-web** — `WebSocketConfig` (STOMP endpoint `/ws`, in-memory broker `/topic`); `JwtHandshakeInterceptor` (stores `user_id` + `group_id` in WebSocket session attributes at connect); `GroupSessionRegistry` (in-memory group-to-sessions map, thread-safe); `EventConsumer` (`@RabbitListener`); `EventToNotificationMapper` (event_type routing)
2. **notification-bot** — Aiogram 3 dispatcher + aio-pika consumer on shared asyncio event loop; `event_router.py` dispatches to per-event-type handlers; `lesson_started.py` calls gRPC + throttled send queue + Redis; `lesson_closed.py` reads Redis + deletes Telegram messages; `auth_client.py` handles OTP REST calls; consumer watchdog coroutine monitors channel health
3. **RabbitMQ queues (new)** — Two durable queues (`notification-web.events`, `notification-bot.events`) bound to existing `rut-uit.events` fanout exchange; DLQ configured for both
4. **Redis (shared)** — New key namespace `reminder:msgs:{lesson_id}:{user_id}` (Redis list via RPUSH); TTL = `lesson_end_time + 10 min`; same Redis instance as Auth and Academic Services — key namespacing prevents collision

### Critical Pitfalls

1. **Telegram 429 rate limit during lesson fan-out** — Multiple groups starting simultaneously exceeds 30 msg/sec global limit and ~1 msg/sec per-chat soft limit. Prevention: implement a throttled send queue (`asyncio.Queue` + worker pool of max 5 coroutines, 50ms sleep between sends) before writing any fan-out logic. Never call `bot.send_message()` directly from the RabbitMQ consumer callback.

2. **aio-pika silent consumer death after RabbitMQ restart** — `connect_robust` reconnects at TCP level but channel and queue consumer bindings are not reliably restored in certain aio-pika versions. The bot goes silent with no errors logged. Prevention: implement a consumer watchdog coroutine (`asyncio.sleep(30)` loop checking `channel.is_closed`) from day one. Test by restarting the RabbitMQ container and verifying consumer resumes within 60 seconds.

3. **Reminder `message_id` stored as string, not list** — Using Redis `SET` overwrites previous `message_id`s; only the last reminder can be deleted. Prevention: use `RPUSH` (list) not `SET` (string); `LRANGE key 0 -1` retrieves all message_ids on `lesson.closed`. Design the key structure before writing the first reminder send.

4. **WebSocket JWT token expiry invalidates session routing mid-connection** — Access tokens expire in 15 minutes but WebSocket sessions live for hours. Prevention: extract `user_id` and `group_id` from JWT claims into `WebSocketSession.getAttributes()` at handshake time; never re-read from `HttpSession` or re-validate JWT during message routing. The session attributes persist for the WebSocket lifetime regardless of JWT expiry.

5. **Synchronous gRPC stub blocks asyncio event loop** — Using standard `grpcio` blocking stub from `async def` handler freezes the event loop for 50-200ms per call. Under concurrent lesson events, the bot appears unresponsive. Prevention: always use `grpc.aio.insecure_channel` and `await stub.GetGroupMembers(...)`. Cache `GetGroupMembers` responses in Redis with 5-minute TTL.

6. **RabbitMQ exchange redeclared with mismatched arguments** — Re-declaring `rut-uit.events` with `durable=false` causes `PRECONDITION_FAILED` on startup and breaks all other services. Prevention: copy the exact `FanoutExchange("rut-uit.events", true, false)` bean declaration from `schedule-app`'s `RabbitConfig` — do not write it from scratch.

7. **No DLQ — failed events cause infinite requeue or silent drop** — Unknown `event_type`s (new events added to fanout before bot code is updated) cause constant requeue storms. Prevention: configure `x-dead-letter-exchange` on both notification queues at creation; filter unknown event types early and `nack` without requeue.

---

## Implications for Roadmap

All dependencies flow from RabbitMQ infrastructure → WebSocket layer → event handlers. The two notification components can develop in parallel after shared infrastructure is established. The Java web service is architecturally simpler and should be completed first to validate the shared exchange/queue setup. The bot has three sequential sub-phases: infrastructure clients, Telegram command handlers, then event handlers.

### Phase 1: Shared Infrastructure Setup
**Rationale:** Both services need queue declarations before any event processing can be tested. Without queue bindings to the fanout exchange, every event published is immediately discarded with no recovery. This is the lowest-risk, highest-leverage starting point.
**Delivers:** Two durable queues (`notification-web.events`, `notification-bot.events`) bound to `rut-uit.events` fanout with DLQ configured for each; updated `docker-compose.yml` with notification-bot `depends_on: [redis, rabbitmq, academic-service]`
**Addresses:** Prerequisite for all notification features in both services
**Avoids:** Pitfall 6 (exchange argument mismatch — copy from schedule-app verbatim); Pitfall 7 (DLQ configured from day one, not retrofitted)
**Research flag:** Standard patterns — copy exchange declaration from `schedule-app/RabbitConfig`; DLQ pattern from `attendance-service/RabbitConfig`

### Phase 2: Notification Web — WebSocket Core
**Rationale:** The Java service has no external calls during event processing (pure in-memory routing) and validates the Gateway WebSocket proxy route. Completing it first establishes the STOMP pattern and confirms queue/exchange wiring before tackling the more complex bot.
**Delivers:** `WebSocketConfig` (STOMP endpoint `/ws`, in-memory broker); `JwtHandshakeInterceptor` (JWT claims into session attributes); `GroupSessionRegistry` (`ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>>`); `EventConsumer` (`@RabbitListener`); `EventToNotificationMapper`; WebSocket push for all 5 event types (`lesson.started`, `lesson.cancelled`, `homework.published`, `excuse.requested`, `late_checkin.requested`)
**Uses:** `spring-boot-starter-websocket`, `jjwt-api:0.12.6`, in-memory STOMP broker; Testcontainers RabbitMQ for integration tests
**Avoids:** Pitfall 4 (store JWT claims in WebSocket attributes, not HttpSession); Pitfall 5 (GroupSessionRegistry keyed by `group_id`, not `user_id`; remove session in `afterConnectionClosed`)
**Research flag:** Verify that Spring Cloud Gateway `JwtAuthenticationFilter` handles HTTP GET upgrade requests (WebSocket) and injects `X-User-Id`/`X-Group-Id` before forwarding — read `api-gateway/.../filter/JwtAuthenticationFilter.java` before writing `JwtHandshakeInterceptor`

### Phase 3: Notification Bot — Infrastructure Layer
**Rationale:** The bot's three infrastructure clients (gRPC, Redis, aio-pika) and the throttled send queue must be built and tested before any event handler can be written correctly. The consumer watchdog is also here — adding it after the fact requires understanding the full consumer lifecycle.
**Delivers:** Python project structure; aio-pika consumer with consumer watchdog coroutine; `grpc.aio` channel + Academic stub (generated from `proto/academic.proto`); `redis.asyncio` client + reminder RPUSH/LRANGE/DEL helpers; `auth_client.py` (aiohttp wrapper for OTP endpoints); throttled send queue (`asyncio.Queue` + worker pool); `config.py` (pydantic-settings)
**Avoids:** Pitfall 2 (consumer watchdog from day one); Pitfall 5 (`grpc.aio` not synchronous stub); Pitfall 1 (throttled queue is the only send path — all handlers must use it)
**Research flag:** Verify `POST /auth/otp/request` returns OTP code in response body before building auth_client.py — check Auth Service OTP response DTO (`auth-service/.../dto/OtpResponse.java` or equivalent)

### Phase 4: Notification Bot — Telegram Command Handlers
**Rationale:** Telegram account linking (`/start`, `/login`) must work before event-driven notifications are meaningful — users with no `telegram_id` linked cannot receive any personalized messages. These handlers are also simpler (request/response) than event handlers and validate the `auth_client.py` integration.
**Delivers:** `/start` handler (account linking, initial credential delivery); `/login` handler (OTP flow with Aiogram FSM conversation state); `/status` handler (current lesson + student attendance status)
**Uses:** `auth_client.py` from Phase 3; Aiogram FSM for multi-step OTP conversation; `grpc.aio` channel for Academic Service lookup
**Research flag:** How to look up user by `telegram_id` in `/start` — current gRPC proto has `GetUserById(user_id: Long)`, not `GetUserByTelegramId`. Resolve approach (Auth Service REST endpoint vs new gRPC RPC) before Phase 4 begins

### Phase 5: Notification Bot — Event Handlers
**Rationale:** With infrastructure clients and throttled send queue from Phase 3, event handlers become straightforward composition. Lesson events are highest priority (core engagement loop), then cancellation and homework.
**Delivers:** `lesson_started.py` (gRPC GetGroupMembers + throttled fan-out + Redis RPUSH message_ids); `lesson_closed.py` (Redis LRANGE + `bot.delete_message` for all students + key DEL); `lesson_cancelled.py` (text fan-out); `homework_published.py` (text fan-out); `attendance_marked.py` (immediate Redis lookup + delete for that student)
**Avoids:** Pitfall 1 (throttled queue mandatory — no direct sends); Pitfall 3 (RPUSH list not SET string)
**Research flag:** No additional research needed — all patterns established in Phase 3

### Phase 6: Integration Verification
**Rationale:** End-to-end validation that events flow correctly through both channels with real infrastructure.
**Delivers:** Testcontainers integration test for notification-web (publish event to RabbitMQ → assert WebSocket STOMP message received); manual E2E for bot (lesson.started event → Telegram message sent + message_id in Redis → lesson.closed → Redis empty + message deleted); Gateway WebSocket proxy confirmed working; `/login` OTP flow verified end-to-end
**Research flag:** No additional research needed

### Phase Ordering Rationale

- Infrastructure first (Phase 1): fanout exchange queues must be bound before any event can be consumed; DLQ must be configured before any business logic runs to avoid silent drops
- Java web service before Python bot (Phase 2 before 3-5): web service is architecturally simpler (no external calls during event processing) and validates the shared exchange/queue wiring
- Bot infrastructure before bot handlers (Phase 3 before 4-5): gRPC client, Redis client, aio-pika consumer, and throttled send queue are prerequisites for every handler; building them first enables each handler to be tested in isolation
- Command handlers before event handlers (Phase 4 before 5): Telegram account linking must exist before personalized lesson notifications work; also validates auth_client.py before it is used in excuse callbacks (v5.1)
- Integration verification last (Phase 6): depends on all components being complete

### Research Flags

Phases needing a targeted code review before implementation:
- **Phase 2:** Read `api-gateway/.../filter/JwtAuthenticationFilter.java` — verify it handles HTTP upgrade requests (GET + `Upgrade: websocket`) and injects `X-User-Id`/`X-Group-Id` before the WebSocket proxy forward
- **Phase 3/4:** Read Auth Service OTP controller + response DTO — verify `POST /auth/otp/request` returns the OTP code in its response body; if not, the bot cannot deliver the code to the user via Telegram
- **Phase 4:** Decide how bot looks up user by `telegram_id` for `/start` — gRPC proto only has `GetUserById(user_id)`, not by telegram_id; this must be resolved (new endpoint or existing mechanism) before Phase 4

Phases with standard patterns (no additional research needed):
- **Phase 1:** Exchange/queue/DLQ declarations — copy from `schedule-app` and `attendance-service` verbatim
- **Phase 5:** All event handlers follow the same fan-out pattern; throttled queue and Redis list are pre-built in Phase 3
- **Phase 6:** Testcontainers + RabbitMQ integration test pattern already established in existing services

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Java versions from Boot 3.4.1 BOM (authoritative); Python versions verified against PyPI 2026-04-04; grpcio 1.73.0 pinned for protobuf 5.x compatibility documented in grpc/grpc#39012 |
| Features | HIGH | Derived from `event-schemas/*.json` (source code), `CLAUDE.md` business rules, `phases-plan.md` Phase 5 spec — all primary authoritative sources in this codebase |
| Architecture | HIGH | Based on full source inspection of existing services; API Gateway WebSocket route confirmed in `application.yml`; Academic proto `telegram_id` field confirmed in `academic.proto`; RabbitMQ fanout exchange and Redis container confirmed in `docker-compose.yml` |
| Pitfalls | HIGH | Telegram rate limits from official Telegram Bot FAQ; aio-pika reconnect bug from upstream GitHub issues; WebSocket session pitfalls from Spring docs; DLQ patterns from RabbitMQ official docs |

**Overall confidence:** HIGH

### Gaps to Address

- **Auth Service OTP response DTO:** Does `POST /auth/otp/request` return the OTP code in its response body? If not, the bot cannot send the code to the user as a Telegram message — the entire `/login` flow breaks. Must verify in `auth-service` source before Phase 3/4. If the response does not include the code, the simplest fix is adding `"code"` to the response record in Auth Service.

- **`telegram_id` user lookup for `/start`:** Current gRPC proto has `GetUserById(UserRequest{user_id: Long})` — no `telegram_id` parameter. The bot needs to look up a user by their Telegram chat ID when `/start` is issued. Options: (a) Auth Service adds a REST endpoint `GET /auth/user/by-telegram/{telegram_id}`, or (b) a new `GetUserByTelegramId` RPC is added to `academic.proto`. Resolve before Phase 4 begins.

- **Gateway WebSocket filter:** Spring Cloud Gateway's `JwtAuthenticationFilter` must handle HTTP GET requests with `Upgrade: websocket` header and inject `X-User-Id`/`X-Group-Id` before the proxy forward. If the filter skips non-POST/PUT requests, WebSocket handshakes will pass through without user identity headers. Verify before Phase 2 coding begins.

- **3-stage reminder scheduling (v5.1):** For v5.0, in-memory `asyncio.create_task` + `asyncio.sleep` scheduling is acceptable but tasks are lost on bot restart. If the bot restarts while a lesson is active, stages 2 and 3 will never fire for that lesson. Document this as a known limitation for v5.0; address in v5.1 with Redis-backed scheduled task tracking.

- **aio-pika version and reconnect fix:** STACK.md recommends `aio-pika==9.6.2` as latest stable. PITFALLS.md notes `9.4.3` was mentioned as a known-safe version for reconnect behavior. Verify the `9.6.2` changelog confirms the consumer restoration bug is fixed before finalizing `requirements.txt`.

---

## Sources

### Primary (HIGH confidence — verified from this repo)
- `.planning/PROJECT.md` — v5.0 active requirements, milestone context
- `docs/phases-plan.md` — Phase 5 detailed feature specification
- `event-schemas/*.json` — all 8 relevant event schemas with exact payload fields
- `CLAUDE.md` — business rules (reminder lifecycle, role definitions, attendance status codes)
- `proto/academic.proto` — confirmed `telegram_id` field in `StudentInfo`; `GetGroupMembers` RPC signature
- `services/api-gateway/src/main/resources/application.yml` — confirmed `/api/ws/**` route to notification-web:9094
- `services/notification-web/build.gradle.kts` — existing dependency baseline (starting point)
- `services/notification-bot/requirements.txt` — existing (stale) version baseline
- `services/attendance-service/attendance-app/src/main/java/.../config/RabbitConfig.java` — DLQ and ObjectMapper injection pattern to replicate
- `docker-compose.yml` — confirmed `redis:7-alpine` and `rabbitmq:3.13-management-alpine`
- `docs/phase-3-report.md` — Schedule Service RabbitMQ event timing (cron transitions)
- `docs/phase-4-report.md` — Attendance Service events published (`attendance.marked`, `excuse.requested`, `late_checkin.requested`)

### Secondary (MEDIUM confidence)
- Spring Cloud Gateway WebSocket proxy documentation — standard feature since Spring Cloud Gateway 2.x
- Aiogram 3.x long-polling and FSM patterns — official Aiogram docs, well-known patterns
- Spring Boot 3 WebSocket + JWT `HandshakeInterceptor` pattern — multiple consistent community sources

### Tertiary (LOW confidence — verify during implementation)
- Auth Service OTP response body includes code — assumed from flow design, not verified in source code
- aio-pika==9.6.2 consumer restoration bug fix status — verify changelog before finalizing requirements.txt

---
*Research completed: 2026-04-04*
*Ready for roadmap: yes*
