# Architecture Research

**Domain:** Notification Service (Web + Bot) — v5.0 integration into existing microservice backbone
**Researched:** 2026-04-04
**Confidence:** HIGH (based on full source inspection of existing codebase + official Spring/Aiogram docs)

---

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        DOCKER PRIVATE NETWORK                            │
│                                                                          │
│  Angular Web Panel ──► [API Gateway :8080] ──► /api/ws/** ──────────┐   │
│                         │  JWT filter injects                        │   │
│                         │  X-User-Id, X-Group-Id headers             │   │
│                         │                                            ▼   │
│                         │                            [Notification Web]  │
│                         │                              Java :9094        │
│                         │                              WebSocket /ws     │
│                         │                              STOMP handler     │
│                         │                              group rooms       │
│                         │                                    ▲           │
│                         │                                    │ push      │
│                                                              │           │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │                   RabbitMQ fanout exchange: rut-uit.events          │ │
│  │                                                                     │ │
│  │  Publishers:                     Consumers (new queues):            │ │
│  │  ├── Schedule Service            ├── notification-web.events        │ │
│  │  │    lesson.started/closed/     │     → WebSocket push to group    │ │
│  │  │    cancelled                  │                                  │ │
│  │  ├── Attendance Service          └── notification-bot.events        │ │
│  │  │    attendance.marked                → Telegram API messages      │ │
│  │  │    excuse.requested                 → 3-stage reminders          │ │
│  │  │    late_checkin.requested           → deleteMessage on close     │ │
│  │  └── Academic Service                                               │ │
│  │       homework.published/updated                                    │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  [Notification Bot]  Python/Aiogram :no-port (outbound only)            │
│    ├── aio-pika consumer  ──► notification-bot.events queue             │
│    ├── Telegram Bot API   ──► outbound HTTPS (not inbound)              │
│    ├── grpcio client      ──► Academic :19091 GetGroupMembers           │
│    ├── HTTP client        ──► Auth Service :9090 /auth/otp/*            │
│    └── Redis client       ──► reminder:msgs:{lesson_id}:{user_id}       │
│                                                                          │
│  [Auth Service :9090]  ──► Redis (OTP keys: otp:{telegram_id})          │
│  [Academic Service :9091]  ──► PostgreSQL + Redis cache                 │
│    gRPC server :19091 — GetGroupMembers, GetUserById                    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Implementation |
|-----------|----------------|----------------|
| Notification Web | WebSocket push to Angular web panel; group-scoped delivery | Java Spring Boot + spring-boot-starter-websocket + STOMP |
| Notification Bot | Telegram message delivery; reminder lifecycle; /start /login /status | Python Aiogram 3 + aio-pika |
| RabbitMQ queues (new) | Two dedicated queues bound to existing fanout exchange | `notification-web.events`, `notification-bot.events` |
| Redis (shared) | Bot stores reminder message_ids per lesson+user | Existing Redis instance, new key namespace `reminder:msgs:*` |
| API Gateway (modified) | Route `/api/ws/**` to Notification Web (already in application.yml) | Route already configured, no code change needed |
| Academic gRPC | GetGroupMembers returns telegram_id per student — already in proto | `StudentInfo.telegram_id` field already in academic.proto |

---

## Integration Points: New vs Modified

### New Components (build from scratch)

| Component | Type | What to Build |
|-----------|------|---------------|
| `notification-web` app | Java Spring Boot (scaffold exists) | WebSocket config, STOMP handler, RabbitMQ consumer, JWT extraction from handshake |
| `notification-bot` app | Python Aiogram 3 (scaffold exists, requirements.txt present) | Bot handlers, aio-pika consumer, gRPC client, Redis client, reminder state machine |
| `notification-web.events` queue | RabbitMQ config | Declare queue, bind to `rut-uit.events` fanout exchange |
| `notification-bot.events` queue | RabbitMQ config | Declare queue, bind to `rut-uit.events` fanout exchange |

### Modified Components (touch existing)

| Component | Change Required | Scope |
|-----------|----------------|-------|
| API Gateway `application.yml` | Route `/api/ws/**` already configured — no change | Already done in Phase 0/1 |
| `docker-compose.yml` | Add Redis dependency to notification-bot container | Add `depends_on: [redis, rabbitmq]` |
| `docker-compose.yml` | Expose gRPC port of Academic Service to notification-bot | Academic already exposes `:19091` internally |
| Auth Service | No change — OTP REST API already built (`/auth/otp/request`, `/auth/otp/verify`) | Bot calls via HTTP |

### Not Modified

- Auth Service business logic (OTP endpoints already exist and work)
- Academic Service gRPC server (GetGroupMembers already has `telegram_id` in StudentInfo)
- Existing RabbitMQ queues (fanout — adding new queues does not affect existing consumers)
- Schedule Service, Attendance Service (publish events already — no changes)

---

## Recommended Project Structure

### Notification Web (Java)

```
services/notification-web/
└── src/main/java/ru/rutcampustrack/notification/
    ├── NotificationWebApplication.java        ← exists (scaffold)
    ├── config/
    │   ├── WebSocketConfig.java               ← STOMP endpoint, /ws, message broker
    │   └── RabbitMqConfig.java                ← queue declaration, binding to fanout
    ├── websocket/
    │   ├── NotificationWebSocketHandler.java  ← (if using raw WS; use STOMP instead)
    │   └── SessionRegistry.java               ← group_id → Set<SimpSession>
    ├── consumer/
    │   └── EventConsumer.java                 ← @RabbitListener on notification-web.events
    ├── mapper/
    │   └── EventToNotificationMapper.java     ← event_type → WebSocket message DTO
    └── dto/
        ├── IncomingEvent.java                 ← record, deserializes RabbitMQ JSON
        └── NotificationMessage.java           ← record, sent to WebSocket client
```

**Structure rationale:**

- `config/` holds infrastructure wiring only (no business logic)
- `consumer/` is a single class — all event routing happens in `mapper/`
- `SessionRegistry` maintains group_id → active WebSocket sessions mapping in memory
- No `*-api-contract` module because Notification Web has no REST API contract to publish; WebSocket is its own protocol

### Notification Bot (Python)

```
services/notification-bot/
├── requirements.txt                           ← exists (complete)
└── src/
    ├── main.py                                ← entrypoint: start bot + aio-pika consumer
    ├── config.py                              ← pydantic-settings env config
    ├── bot/
    │   ├── handlers/
    │   │   ├── start.py                       ← /start command + OTP trigger
    │   │   ├── login.py                       ← /login OTP flow
    │   │   └── status.py                      ← /status attendance summary
    │   └── keyboards.py                       ← inline keyboard builders
    ├── consumer/
    │   ├── amqp.py                            ← aio-pika connection + queue binding
    │   └── event_router.py                    ← event_type → handler dispatch
    ├── handlers/                              ← event handlers (business logic)
    │   ├── lesson_started.py                  ← send to group + schedule 3 reminders
    │   ├── lesson_closed.py                   ← delete reminder messages via Redis
    │   ├── lesson_cancelled.py                ← notify group of cancellation
    │   ├── homework_published.py              ← notify group of new homework
    │   └── excuse_requested.py                ← forward to headman (out of scope v5.0)
    ├── grpc/
    │   ├── academic_client.py                 ← grpcio stub for AcademicGrpcService
    │   └── generated/                         ← auto-generated from academic.proto
    ├── redis_client.py                        ← aioredis wrapper for reminder message_ids
    └── auth_client.py                         ← aiohttp calls to Auth Service OTP endpoints
```

---

## Architectural Patterns

### Pattern 1: WebSocket Through Gateway (not direct)

**What:** Angular web panel connects to `ws://gateway:8080/api/ws` — the Gateway proxies the WebSocket upgrade to Notification Web on port 9094.

**Decision:** Go through Gateway, not direct.

**Rationale:**
- Gateway already has the route configured (`/api/ws/** → notification-web:9094`)
- JWT validation happens at Gateway before the connection is upgraded to WebSocket
- Gateway injects `X-User-Id` and `X-Group-Id` headers into the upgrade request — Notification Web reads these to register the session in the `SessionRegistry` without needing to parse JWT itself
- Direct connection (bypassing Gateway) would require Notification Web to independently validate JWT — duplicating RSA key management and adding complexity
- Spring Cloud Gateway supports WebSocket proxying natively (HTTP upgrade passthrough)

**Confidence:** HIGH — Spring Cloud Gateway WebSocket proxy is documented and stable since 2021.

**Implementation note:** The Angular client connects to `wss://gateway:8080/api/ws`. The Gateway strips the `/api` prefix (StripPrefix=1) and forwards to `ws://notification-web:9094/ws`. The STOMP handshake includes `Authorization: Bearer <jwt>` header, which the Gateway validates before proxying.

### Pattern 2: STOMP over WebSocket (not raw WebSocket)

**What:** Use STOMP protocol on top of WebSocket rather than raw WebSocket frames.

**Rationale:**
- Spring's `SimpMessagingTemplate` provides topic-based routing (`/topic/group.{group_id}`) without manual session tracking per connection
- Client subscribes to `/topic/group.42` — server pushes to all subscribers of that topic automatically
- Falls back naturally to SockJS for environments where WebSocket is blocked (though unlikely for Angular panel)
- `SessionRegistry` is still needed to track which user_id is in which group (for security check that user only subscribes to their own group topic)

**Alternative considered:** Raw `WebSocketHandler` — rejected because manual session management is error-prone and STOMP is already a dependency in build.gradle.kts.

### Pattern 3: Bot Uses Auth Service REST, Not Direct Redis

**What:** Notification Bot calls `POST http://auth-service:9090/auth/otp/request` and `POST /auth/otp/verify` via HTTP — it does NOT read/write OTP keys in Redis directly.

**Rationale:**
- Auth Service owns the OTP state machine (rate limiting: max 3 attempts/5min, 60s resend cooldown)
- Direct Redis access would bypass rate limiting and attempt tracking
- The Auth Service OTP endpoints are already built and accept `telegram_id` as input
- Keeps Auth as the single source of truth for authentication secrets

**Flow for /login command:**
```
User → /login → Bot → POST /auth/otp/request {telegram_id}
Auth Service → generates 6-digit code → stores otp:{telegram_id} in Redis → returns 200
Bot → "Enter the code I sent" (but wait — bot IS the delivery channel)
```

**Problem:** The `/auth/otp/request` endpoint was designed expecting the bot to RECEIVE a code from Auth and deliver it to the user via Telegram. The bot must call `/auth/otp/request`, get back the OTP code in the response body (or Auth Service must publish it as an event), then send it as a Telegram message.

**Recommended approach:** Auth Service returns the OTP code in the response to `/auth/otp/request` when the caller provides `telegram_id`. The bot reads the code from the HTTP response and sends it to the user via `bot.send_message(chat_id=telegram_id, text=f"Your code: {code}")`. This keeps the OTP transport logic in the bot (which knows Telegram) and Auth Service just manages the code lifecycle.

**Verify:** Check Auth Service OTP response DTO — if it returns the code, this pattern works without any Auth Service change.

### Pattern 4: Shared Redis Instance, New Key Namespace

**What:** Notification Bot connects to the same Redis instance used by Auth and Academic Services.

**Decision:** Share the existing Redis instance.

**Rationale:**
- Solo deployment on VPS — a second Redis container adds memory overhead with no benefit
- Key namespacing prevents collision: bot uses `reminder:msgs:{lesson_id}:{user_id}` → `[msg_id_1, msg_id_2, msg_id_3]`
- Auth uses `otp:{telegram_id}`, `refresh:{user_id}:{jti}` — completely separate namespace
- Academic uses `group:{id}:info`, `semester:active` — completely separate namespace
- Redis TTL on reminder keys: set to lesson `end_time + 10 minutes` to auto-clean if bot crashes before `lesson.closed` event

**Risk:** Redis becomes a cross-team coupling point. Acceptable for solo project; in team setting would justify a separate instance.

### Pattern 5: Bot Discovers Group Members via gRPC, Not REST

**What:** When `lesson.started` arrives with `group_id`, the bot calls `GetGroupMembers(group_id)` via gRPC to Academic Service to get a list of `{user_id, telegram_id}` pairs for broadcasting.

**Rationale:**
- `GetGroupMembers` is already implemented and exposed on Academic Service gRPC port (`:19091`)
- The `StudentInfo` message in `academic.proto` already includes `telegram_id` (line 64 in proto) — the data is there
- gRPC is faster than REST for internal calls and already set up in the project
- REST alternative would require calling through Gateway with a JWT — complex for a background consumer

**Python gRPC setup:** `grpcio` and `grpcio-tools` are already in `requirements.txt`. The bot generates Python stubs from `proto/academic.proto` using `python -m grpc_tools.protoc`.

---

## Data Flow

### Flow 1: Lesson Started — Full Notification Path

```
[Schedule Service @Scheduled cron]
    │ UPDATE lessons SET status='ACTIVE'
    │
    └─► RabbitMQ publish "lesson.started"
         │  payload: {lesson_id, group_id, subject_id, teacher_id, start_time, end_time}
         │
         ├─► notification-web.events queue
         │      EventConsumer.onMessage()
         │        → mapper: lesson.started → NotificationMessage{type=LESSON_STARTED, group_id}
         │        → SimpMessagingTemplate.convertAndSend("/topic/group.{group_id}", message)
         │        → all Angular clients subscribed to that topic receive push
         │
         └─► notification-bot.events queue
                lesson_started.handle(event)
                  → gRPC GetGroupMembers(group_id)
                  ← [{user_id, telegram_id, is_headman}, ...]
                  → for each student (not headman):
                       bot.send_message(telegram_id, "Пара началась!", reply_markup=checkin_button)
                       message_id stored → Redis RPUSH reminder:msgs:{lesson_id}:{user_id} [msg_id]
                  → schedule 3 reminders: at start, at midpoint, 5min before end
```

### Flow 2: Lesson Closed — Cleanup Path

```
[Schedule Service @Scheduled cron]
    └─► RabbitMQ publish "lesson.closed"
         payload: {lesson_id, group_id}
         │
         └─► notification-bot.events queue
                lesson_closed.handle(event)
                  → gRPC GetGroupMembers(group_id) — get telegram_ids
                  → for each student:
                       Redis LRANGE reminder:msgs:{lesson_id}:{user_id} 0 -1 → [msg_id_1, ...]
                       bot.delete_message(chat_id, msg_id) for each
                       Redis DEL reminder:msgs:{lesson_id}:{user_id}
                  → cancel scheduled reminder tasks for this lesson_id
```

### Flow 3: Bot /login Command — OTP Authentication

```
User in Telegram → /login
    │
    └─► Aiogram handler
          → POST http://auth-service:9090/auth/otp/request {telegram_id: chat_id}
          ← 200 {code: "481927", ttl_seconds: 120}  (Auth returns code directly)
          → bot.send_message(chat_id, f"Your OTP: {code}. Valid for 2 minutes.")
          → wait for next message from same user (conversation state)

User replies with "481927"
    └─► Aiogram handler
          → POST http://auth-service:9090/auth/otp/verify {telegram_id, code}
          ← 200 {accessToken, refreshToken}
          → bot stores tokens (or just confirms: "Linked! Use /status now.")
          → user is now authenticated for /status command
```

### Flow 4: Bot /start Command — Account Linking

```
User in Telegram → /start
    └─► Aiogram handler
          → POST http://auth-service:9090/auth/otp/request {telegram_id}
          ← if user exists with this telegram_id and has initial_password != null:
               Auth Service returns initial credentials in response (or via separate endpoint)
          → bot sends login + initial password to user
          → triggers OTP flow for linking
```

**Note:** The `/start` flow requires Auth Service to look up the user by `telegram_id` and return initial credentials. Verify whether Auth Service's current `GetUserById` gRPC call or a new REST endpoint is needed. The bot calling gRPC `GetUserById` with a `telegram_id` lookup is not in the current proto (UserRequest takes `user_id`, not `telegram_id`). Either: (a) add a `GetUserByTelegramId` RPC to academic.proto, or (b) Auth Service handles this via its own `users` table read (it already reads `academic_db`). Option (b) is simpler for v5.0.

---

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 0-500 users (current) | Single instance of each notification container. In-memory SessionRegistry in Notification Web. Shared Redis. |
| 500-5000 users | SessionRegistry moves to Redis pub/sub if multiple Notification Web instances needed. Bot reminders use Redis-backed task queue (Celery/APScheduler). |
| 5000+ users | Notification Web scales horizontally with Redis pub/sub bridge. Bot shards by group_id. Separate Redis instance per service. |

**First bottleneck:** Notification Web `SessionRegistry` (in-memory Map) breaks if multiple instances are deployed. Use Redis pub/sub as the inter-instance bridge. Not needed for current scope.

**Second bottleneck:** Bot `lesson.started` sends N Telegram messages synchronously per group. At 100+ students per group, Telegram rate limits (30 messages/sec) require async batching with delays. aio-pika + asyncio handles this naturally but scheduling logic needs rate-limit awareness.

---

## Anti-Patterns

### Anti-Pattern 1: Bot Writes OTP Codes Directly to Redis

**What people do:** Python bot generates a random code, writes `otp:{telegram_id}` to Redis directly — bypassing Auth Service.

**Why it's wrong:** Skips rate limiting (`otp_attempts:{telegram_id}` counter), resend cooldown (`otp_sent:{telegram_id}`), and attempt tracking. Auth Service's OTP security is entirely in those three Redis keys managed together as a state machine. Direct Redis write splits ownership of authentication state.

**Do this instead:** Bot calls `POST /auth/otp/request` via HTTP. Auth Service manages all OTP state in Redis atomically.

### Anti-Pattern 2: WebSocket Connection Bypasses Gateway

**What people do:** Angular connects directly to `ws://notification-web:9094/ws` (exposing port 9094 to the outside).

**Why it's wrong:** Bypasses JWT validation at Gateway. Notification Web would need its own JWT parsing (RSA public key management). Port 9094 would need to be open externally, breaking the "one external port" architecture principle.

**Do this instead:** Angular connects to `wss://gateway:8080/api/ws`. Gateway validates JWT, injects user headers, proxies WebSocket upgrade. Notification Web reads `X-User-Id` and `X-Group-Id` from upgrade request headers.

### Anti-Pattern 3: Bot Calls REST Through Gateway for Group Members

**What people do:** Bot makes HTTP calls to `http://api-gateway:8080/api/academic/groups/{id}/members` with a system JWT to get group members.

**Why it's wrong:** Requires generating/maintaining a system-level JWT for the bot. Adds Gateway as a dependency for internal service-to-service calls. gRPC is already set up and faster.

**Do this instead:** Bot calls Academic Service gRPC directly on `academic-service:19091` using `GetGroupMembers`. No JWT needed for gRPC (internal private network).

### Anti-Pattern 4: Separate Redis for Bot

**What people do:** Add a second Redis container for the bot's reminder storage.

**Why it's wrong:** Unnecessary resource overhead on solo VPS. Redis key namespacing is sufficient isolation. `reminder:msgs:*` keys never collide with Auth (`otp:*`, `refresh:*`) or Academic (`group:*`, `semester:*`, `campus:*`) key patterns.

**Do this instead:** Connect bot to existing Redis instance. Use `reminder:msgs:{lesson_id}:{user_id}` key pattern with TTL = lesson `end_time + 10 minutes`.

### Anti-Pattern 5: Notification Web Tracks Which Users Are Absent

**What people do:** Notification Web queries Attendance Service to determine who hasn't checked in, then sends targeted reminders.

**Why it's wrong:** Creates a dependency from Notification Web to Attendance Service, breaking the event-driven decoupling. Notification Web becomes stateful and database-dependent.

**Do this instead:** Attendance state is the bot's concern, not Notification Web's. For v5.0, the bot sends reminders to ALL group members on `lesson.started`. The bot does not need to know who has already checked in — sending a reminder to someone who already checked in is acceptable UX (they get confirmation, not a burden). If targeted reminders are needed later, Attendance Service can publish a `reminder.needed` event listing non-present student IDs.

---

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Telegram Bot API | Aiogram 3 long-polling (or webhook) outbound HTTPS | No inbound port needed for long-polling. Webhook requires public URL (VPS only). Use long-polling for dev, webhook for prod. |
| Auth Service REST | aiohttp POST to `http://auth-service:9090/auth/otp/*` | Bot calls directly within Docker network. No Gateway needed. |
| Academic Service gRPC | grpcio stub to `academic-service:19091` | Generate Python stubs from `proto/academic.proto`. Already in requirements.txt. |
| Redis | aioredis (redis-py asyncio) to `redis:6379` | Same instance as Auth + Academic. Namespace: `reminder:msgs:*`. |
| RabbitMQ | aio-pika (bot) + spring-boot-starter-amqp (web) | Bind both queues to existing `rut-uit.events` fanout exchange on startup. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| Angular ↔ Notification Web | WebSocket (STOMP) via Gateway | Gateway proxies upgrade. Angular subscribes to `/topic/group.{group_id}`. |
| RabbitMQ ↔ Notification Web | AMQP consume `notification-web.events` | Spring `@RabbitListener`. Declare queue + binding in `RabbitMqConfig`. |
| RabbitMQ ↔ Notification Bot | AMQP consume `notification-bot.events` | aio-pika asyncio consumer. Declare queue + binding on startup. |
| Notification Bot ↔ Academic Service | gRPC `GetGroupMembers` | Only RPC needed: find telegram_ids for a group_id. |
| Notification Bot ↔ Auth Service | HTTP REST `/auth/otp/request`, `/auth/otp/verify` | OTP flow for /login command. |
| Notification Bot ↔ Redis | Key-value store for `reminder:msgs:{lesson_id}:{user_id}` | List of Telegram message_ids to delete on `lesson.closed`. |

---

## Build Order (Dependencies)

The two notification components are largely independent of each other but depend on existing infrastructure:

```
Phase 1 (infrastructure):
  → Declare RabbitMQ queues (notification-web.events, notification-bot.events)
    bound to existing rut-uit.events fanout exchange
  → Update docker-compose.yml: add redis + academic-grpc deps to notification-bot

Phase 2 (Notification Web):
  → WebSocketConfig (STOMP endpoint, message broker)
  → RabbitMqConfig (queue declaration)
  → IncomingEvent + NotificationMessage DTOs
  → EventConsumer (@RabbitListener)
  → EventToNotificationMapper (event_type routing)
  → SessionRegistry (group_id → sessions)
  → Integration: verify Gateway proxy works for WebSocket upgrade

Phase 3 (Notification Bot — infrastructure):
  → Generate gRPC stubs from academic.proto
  → academic_client.py (gRPC channel + stub)
  → redis_client.py (aioredis wrapper)
  → auth_client.py (aiohttp wrapper for OTP endpoints)
  → amqp.py (aio-pika queue consumer)

Phase 4 (Notification Bot — bot handlers):
  → /start handler (account linking)
  → /login handler (OTP flow)
  → /status handler (attendance summary via gRPC or REST)

Phase 5 (Notification Bot — event handlers):
  → lesson_started.py (broadcast + Redis reminder storage + 3-stage scheduler)
  → lesson_closed.py (Redis lookup + deleteMessage + cleanup)
  → lesson_cancelled.py (broadcast cancellation)
  → homework_published.py (broadcast homework notification)

Phase 6 (Integration verification):
  → End-to-end: lesson.started event → WebSocket push arrives in Angular
  → End-to-end: lesson.started event → Telegram messages sent to group members
  → End-to-end: lesson.closed → reminder messages deleted
  → End-to-end: /login OTP flow completes
```

**Key dependency:** Phase 3 (Bot gRPC) requires that `academic.proto` is stable. It is — Academic Service gRPC is complete with `telegram_id` already in `StudentInfo`. No proto changes needed.

**Key dependency:** Bot's `/start` flow needs Auth Service to support user lookup by `telegram_id`. If Auth's `POST /auth/otp/request` does not return the OTP code in the response body, a small change is needed. Verify before Phase 4.

---

## Open Questions

1. **Does `POST /auth/otp/request` return the OTP code in its response body?**
   If not, the bot cannot send the code to the user. Auth Service may need to return it or publish an event. Investigate Auth Service OTP response DTO before building `/login` handler.

2. **How does the bot look up a user by `telegram_id` for /start?**
   Current gRPC proto has `GetUserById(UserRequest{user_id})` — takes a Long user_id, not telegram_id. Options: (a) add `GetUserByTelegramId` RPC to academic.proto, or (b) bot calls Auth REST which reads `academic_db.users WHERE telegram_id = ?`. Option (b) reuses existing Auth infrastructure with a new endpoint.

3. **3-stage reminder scheduling implementation?**
   The bot receives `lesson.started` with `start_time` and `end_time`. It needs to schedule tasks at t+0, t+midpoint, t+(end-5min). asyncio `asyncio.create_task(asyncio.sleep(...))` works but is lost on process restart. For v5.0 (dev VPS, single instance), in-memory scheduling is acceptable. Document this as a known limitation.

4. **WebSocket authentication: does Spring Cloud Gateway forward upgrade headers?**
   Gateway's `StripPrefix` filter operates on HTTP headers during the upgrade. The JWT filter must run before the upgrade is forwarded. Verify that `JwtAuthenticationFilter` handles WebSocket upgrade requests (HTTP GET with `Upgrade: websocket`) and injects `X-User-Id`/`X-Group-Id` before forwarding. If the filter skips non-standard HTTP methods, it needs to be extended.

---

## Sources

- `docs/architecture.md` — full system architecture (HIGH confidence, authoritative)
- `docs/phases-plan.md` — Phase 5 detailed spec (HIGH confidence, authoritative)
- `proto/academic.proto` — confirmed `telegram_id` in `StudentInfo` message (HIGH confidence, source code)
- `event-schemas/*.json` — confirmed all event payloads include `group_id` (HIGH confidence, source code)
- `services/api-gateway/src/main/resources/application.yml` — confirmed `/api/ws/**` route already configured (HIGH confidence, source code)
- `services/notification-web/build.gradle.kts` — confirmed `spring-boot-starter-websocket` + `spring-boot-starter-amqp` already declared (HIGH confidence, source code)
- `services/notification-bot/requirements.txt` — confirmed `aiogram==3.15.0`, `aio-pika==9.5.3`, `grpcio==1.69.0`, `pydantic-settings==2.7.1` (HIGH confidence, source code)
- Spring Cloud Gateway WebSocket proxying: documented in Spring Cloud Gateway reference (MEDIUM confidence, standard feature since 2.x)
- Aiogram 3.x long-polling vs webhook: Aiogram official docs (MEDIUM confidence, well-known pattern)

---

*Architecture research for: Notification Service v5.0 (Web + Bot)*
*Researched: 2026-04-04*
