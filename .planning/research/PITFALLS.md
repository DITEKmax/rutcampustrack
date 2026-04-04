# Pitfalls Research

**Domain:** Notification Service (WebSocket + Telegram Bot) — adding real-time push delivery to an existing microservice attendance system. Two independent consumers of the existing RabbitMQ fanout exchange: notification-web (Java/Spring WebSocket, port 9094) and notification-bot (Python/Aiogram 3).
**Researched:** 2026-04-04
**Confidence:** HIGH (Telegram rate limits verified against official Telegram Bot FAQ; WebSocket session pitfalls verified against Spring docs and github issues; aio-pika reconnect behavior verified against upstream GitHub issues; DLQ patterns verified against RabbitMQ official docs)

---

## Critical Pitfalls

### Pitfall 1: Telegram 20 msg/min Per-Chat Limit Causes 429 Burst at Lesson Start

**What goes wrong:**
When a `lesson.started` event fires, the bot fans out messages to every student in the group. For a group of 30 students, each receives a private message — 30 separate chat_ids. At 30 messages/second (global bot limit), this looks safe. However, if multiple groups start lessons at the same minute (e.g., 9:00 Monday), the bot sends 30 × N_groups messages in under 1 second. For 5 concurrent groups (150 students), you exceed the global 30 msg/sec limit almost immediately. More critically, if a reminder cycle and a lesson.started event overlap, the bot is sending to the SAME student twice within seconds — the per-chat 1 msg/sec soft limit triggers 429 errors for those students.

**Why it happens:**
Developers see "30 messages per second globally" and assume fan-out to 30 different users is safe. They miss two compounding factors: (1) the per-chat limit is ~1 msg/sec (soft), with bursts tolerated briefly before 429; (2) reminders at lesson midpoint/end create overlapping sends to the same chat_ids as the original lesson.started push. The RabbitMQ consumer processes the event immediately and fires all sends synchronously in a loop — no throttling.

**How to avoid:**
Implement a rate-limited send queue using `asyncio.Queue` + a single sender coroutine that enforces delay between sends to the same `chat_id`. Use per-chat tracking in Redis (`reminder:{chat_id}:last_send_ts`) to enforce 1-second minimum between sends to the same user. Respect `Retry-After` from 429 responses: read the header integer, store in Redis as `rate_limit:{chat_id}` with that TTL, skip sends to that chat until TTL expires.

```python
# Sender coroutine — single writer per chat_id slot
async def throttled_send(bot, chat_id, text, reply_markup=None):
    rate_key = f"rate_limit:{chat_id}"
    if await redis.exists(rate_key):
        return  # skip, will retry from queue
    try:
        msg = await bot.send_message(chat_id, text, reply_markup=reply_markup)
        return msg
    except TelegramRetryAfter as e:
        await redis.setex(rate_key, e.retry_after + 1, "1")
        raise  # re-enqueue
```

For lesson fan-out (N students): add all sends to an `asyncio.Queue`, process with a worker pool of max 5 coroutines, each sleeping 0.05s between sends. This spreads 30 sends over 1.5 seconds — within both global and per-chat limits.

**Warning signs:**
- Logs show `TelegramRetryAfter` exceptions during cron-aligned lesson start times (9:00, 10:30, 13:00)
- Students in same group report receiving reminder messages minutes apart (queue backlog)
- Bot response to `/status` command hangs because event loop is blocked waiting on rate-limited sends

**Phase to address:**
Bot RabbitMQ consumer + fan-out phase — implement throttled send queue before writing any fan-out logic. Never send directly from the consumer callback.

---

### Pitfall 2: Reminder message_id Not Stored Atomically — Cleanup Misses Messages

**What goes wrong:**
The reminder lifecycle is: lesson.started → send reminder 1 (store message_id in Redis) → send reminder 2 at midpoint → send reminder 3 near end → lesson.closed → delete all 3 messages via `deleteMessage`. If the Redis write for a message_id fails (network blip, Redis restart) after the Telegram send succeeds, the bot has no record of that message_id and cannot delete it. The student sees a stale "Please mark attendance" message in their Telegram chat long after the lesson ended.

Additionally, the reminder key structure `reminder:msgs:{lesson_id}:{user_id}` stores one message_id, but there are up to 3 reminders per student per lesson. If the key stores only the latest message_id (overwrite), the first two reminders are never deleted.

**Why it happens:**
Developers use `SET key value` (overwrite), not a list structure. They assume Redis writes always succeed after Telegram sends. There is no transactional guarantee between Telegram API call (which assigns message_id) and Redis write.

**How to avoid:**
Use Redis `RPUSH reminder:msgs:{lesson_id}:{user_id} {message_id}` to accumulate all message_ids for a student's reminders for a given lesson. Set TTL on the list key to `lesson_end_time + 10 minutes` so stale keys auto-expire. On lesson.closed, read the full list with `LRANGE`, attempt `deleteMessage` for each, ignore `MessageToDeleteNotFound` errors (already deleted or expired), then `DEL` the key.

Store message_id BEFORE acknowledging the RabbitMQ reminder event, but AFTER confirming Telegram returned a non-error response:

```python
result = await bot.send_message(chat_id, reminder_text, reply_markup=markup)
# Only store if send succeeded
await redis.rpush(f"reminder:msgs:{lesson_id}:{user_id}", result.message_id)
await redis.expireat(f"reminder:msgs:{lesson_id}:{user_id}", lesson_close_unix_ts + 600)
```

**Warning signs:**
- Students see "Отметьтесь" messages in Telegram after lessons end
- Redis key `reminder:msgs:{lesson_id}:{user_id}` contains only 1 element when 3 reminders were sent
- `deleteMessage` calls in lesson.closed handler return `Message not found` for some message_ids

**Phase to address:**
Reminder lifecycle phase — design the Redis data structure (list, not string) before implementing any reminder send. Write a test that sends 3 reminders and asserts the Redis list has 3 elements.

---

### Pitfall 3: aio-pika RobustConnection Silently Stops Consuming After RabbitMQ Restart

**What goes wrong:**
The notification-bot uses `aio-pika` with `connect_robust()` for automatic reconnection. After a RabbitMQ restart (Docker container restart, network blip), `RobustConnection` reconnects at the TCP level but the consumer channel and queue bindings are NOT always restored correctly in certain aio-pika versions. The bot reconnects successfully (logs show "connected"), but the queue consumer never resumes — the bot goes silent. New events are published to the queue, accumulate, and are never processed until the bot is manually restarted.

**Why it happens:**
This is a known aio-pika bug documented in multiple GitHub issues: after connection loss with certain error codes ("Connection was stuck"), `RobustChannel` only restores the connection, not the channel state (queues, consumers, QoS). The `connect_robust()` documentation implies full state recovery but the implementation has edge-case gaps depending on the error type and aio-pika version.

**How to avoid:**
Add explicit health monitoring for the consumer. After reconnect, verify the consumer is still running by checking the channel state. Use a watchdog coroutine that periodically checks consumer state and re-declares if needed:

```python
async def consumer_watchdog(connection, channel_holder, queue_name, callback):
    while True:
        await asyncio.sleep(30)
        try:
            if channel_holder[0].is_closed:
                logger.warning("Consumer channel closed, re-establishing")
                channel_holder[0] = await connection.channel()
                await channel_holder[0].set_qos(prefetch_count=10)
                queue = await channel_holder[0].declare_queue(queue_name, durable=True)
                await queue.consume(callback)
        except Exception as e:
            logger.error(f"Watchdog failed: {e}")
```

Pin aio-pika to a tested version in `requirements.txt` (e.g., `aio-pika==9.4.3`) and check the changelog for reconnect regression notes before upgrading.

**Warning signs:**
- Bot logs show "connected" after RabbitMQ restart but no events are processed
- RabbitMQ Management UI shows 0 consumers on `notification-bot.events` queue after restart
- Queue message count grows (messages accumulating, not consumed)
- Bot responds to `/start` and `/status` commands (Telegram polling still works) but never sends lesson notifications

**Phase to address:**
Bot infrastructure phase — add consumer watchdog from the start, do not rely on aio-pika's auto-recovery alone. Test by stopping and restarting RabbitMQ container while bot is running and verifying consumer resumes.

---

### Pitfall 4: WebSocket JWT Validation Only at HTTP Handshake — Token Expiry Mid-Session Creates Stale Sessions

**What goes wrong:**
The notification-web service validates the JWT token at WebSocket connection time (HTTP upgrade handshake). The Angular web panel connects at login and maintains the WebSocket session for hours. JWT access tokens in this system expire in 15 minutes. After 15 minutes, the WebSocket session remains open but the user's token is expired. If the server reads the token from session attributes to identify the user for routing notifications, the user continues receiving notifications correctly. However, if any logic re-validates the token (e.g., a reconnect triggered by network blip), the fresh validation fails with 401 and the client cannot reconnect until they call the token refresh endpoint — which the Angular client may not handle automatically.

**Why it happens:**
WebSocket sessions outlive JWT token validity by design. Developers validate at handshake (correct) but don't account for the connection lifecycle after expiry. The `HttpSession` associated with the handshake may also expire on the server side (default 30min) while the WebSocket connection stays alive, causing Spring to lose the user's authentication principal when the session is looked up.

**How to avoid:**
Extract user identity (user_id, group_id, role) from the JWT at handshake time and store directly in `WebSocketSession.getAttributes()`. Never read from `HttpSession` during message handling:

```java
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ..., Map<String, Object> attributes) {
        String token = extractToken(request); // from query param ?token=...
        Claims claims = jwtService.validateAndParse(token);
        attributes.put("user_id", claims.getSubject());
        attributes.put("group_id", claims.get("group_id", Long.class));
        return true;
    }
}
```

The Angular client should send the access token as a query parameter on WebSocket connect (since SockJS cannot send custom headers). The client must handle `CloseEvent` code 1008 (policy violation) and trigger token refresh + reconnect before re-connecting. Set a server-side STOMP heartbeat (e.g., 10s/10s) so dead connections are detected quickly.

**Warning signs:**
- Web panel notifications stop working after ~30 minutes without page reload
- Server logs show `NullPointerException` on session attribute lookup for long-lived WebSocket sessions
- Angular console shows WebSocket close code 1006 (abnormal closure) without clear cause
- Users who kept the tab open overnight miss all notifications

**Phase to address:**
WebSocket JWT auth phase — store all session data in WebSocket attributes at handshake, not in HttpSession. Add a test that simulates a 15-minute wait (mock clock) and verifies the session still routes messages correctly.

---

### Pitfall 5: Fan-out Routing Uses group_id From JWT But WebSocket Sessions Are Indexed by user_id

**What goes wrong:**
When a `lesson.started` event arrives in notification-web, the service needs to push to all WebSocket sessions belonging to students in the affected group. The naive implementation stores sessions in a `Map<Long, WebSocketSession>` keyed by `user_id`. To route to a group, the service must either: (a) call Academic Service gRPC to get all `user_id`s in the group, then look up each session — expensive, (b) maintain a secondary `Map<Long, Set<WebSocketSession>>` keyed by `group_id` — requires careful session lifecycle management, or (c) use STOMP user destinations with a group topic — requires STOMP not raw WebSocket.

The pitfall is implementing (a) on every event: a gRPC call to Academic Service for each `lesson.started` event. Under concurrent lessons, this creates a gRPC call storm. Alternatively, implementing (b) incorrectly — not removing sessions from the `group_id` map on disconnect — causes memory leaks and phantom sends to closed sessions.

**Why it happens:**
The system already has group-based routing logic in other services. Developers assume the same gRPC call pattern works here. WebSocket session maps are added to on connect but cleanup on disconnect is easy to miss (disconnect callbacks in raw WebSocket vs STOMP differ).

**How to avoid:**
Use an in-process `ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>>` keyed by `group_id`. On WebSocket connect (after JWT validation), add the session to the appropriate group set. Implement `afterConnectionClosed()` in the WebSocket handler to remove the session from ALL group sets. Use `CopyOnWriteArraySet` for thread safety during iteration.

```java
@Component
public class GroupSessionRegistry {
    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> sessions
        = new ConcurrentHashMap<>();

    public void register(WebSocketSession session, Long groupId) {
        sessions.computeIfAbsent(groupId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unregister(WebSocketSession session, Long groupId) {
        sessions.getOrDefault(groupId, new CopyOnWriteArraySet<>()).remove(session);
    }

    public void broadcastToGroup(Long groupId, String message) {
        sessions.getOrDefault(groupId, new CopyOnWriteArraySet<>()).forEach(s -> {
            if (s.isOpen()) {
                try { s.sendMessage(new TextMessage(message)); }
                catch (IOException e) { /* log, remove stale */ }
            }
        });
    }
}
```

This eliminates the gRPC call per event entirely. No external calls needed for routing — all routing is in-memory.

**Warning signs:**
- Academic Service gRPC metrics show a spike in `GetGroupMembers` calls at lesson transition times
- Memory usage of notification-web grows monotonically over hours (session leak in group map)
- Some students receive duplicate notifications (session registered twice after reconnect)

**Phase to address:**
WebSocket session management phase — implement `GroupSessionRegistry` before writing any event routing. Test disconnect cleanup with a test that connects, disconnects, and verifies the session count drops to zero.

---

### Pitfall 6: Python grpcio Blocking Calls Starve the Aiogram asyncio Event Loop

**What goes wrong:**
The notification-bot needs to call Academic Service gRPC (`GetGroupMembers`) to get the list of students to notify when a lesson event arrives. Using the synchronous `grpcio` stub (`AcademicServiceStub` with blocking channel) inside an `async def` handler blocks the asyncio event loop for the duration of the gRPC call (typically 50-200ms). During this block, the bot cannot process incoming Telegram updates, the aio-pika consumer cannot acknowledge messages, and Telegram's polling queue backs up. Under concurrent events (multiple lessons starting at the same minute), the event loop appears frozen.

**Why it happens:**
The `grpcio` library's standard stub is synchronous. Developers call it from `async def` functions without wrapping in `asyncio.to_thread()`. The function returns immediately to the caller (no syntax error) but runs synchronously, blocking the event loop. The bug is invisible in dev testing with one event; it manifests under concurrent load.

**How to avoid:**
Use `grpc.aio` (the async gRPC API) for the bot's gRPC client instead of the synchronous stub:

```python
import grpc.aio

async def get_group_members(group_id: int) -> list[int]:
    async with grpc.aio.insecure_channel("academic-service:19091") as channel:
        stub = AcademicServiceStub(channel)
        response = await stub.GetGroupMembers(GetGroupMembersRequest(group_id=group_id))
        return [m.user_id for m in response.members]
```

Alternatively, if the synchronous stub is used for simplicity, wrap all calls in `asyncio.to_thread()`:

```python
result = await asyncio.to_thread(sync_stub.GetGroupMembers, request)
```

Cache `GetGroupMembers` responses in Redis with a 5-minute TTL — group composition changes rarely, and caching eliminates gRPC calls on every lesson event.

**Warning signs:**
- Bot feels unresponsive to `/status` commands during lesson start times
- aio-pika consumer stops acknowledging messages for 100-500ms windows (visible in RabbitMQ Management as "unacked" count spike)
- `asyncio.get_event_loop().slow_callback_duration` warnings in Python logs
- Telegram polling shows gaps in received updates

**Phase to address:**
Bot gRPC client phase — use `grpc.aio` from the start, never synchronous stubs in async context. Add a test that sends 5 lesson events concurrently and verifies all are processed without ordering gaps.

---

### Pitfall 7: notification-web Declares Duplicate RabbitMQ Exchange With Mismatched Arguments

**What goes wrong:**
notification-web must declare the `rut-uit.events` fanout exchange in its `RabbitConfig` (same as Attendance Service, Schedule Service). If the exchange bean is declared with any different argument (e.g., `durable=false` instead of `durable=true`, or `autoDelete=true`) compared to the existing declaration, RabbitMQ throws `PRECONDITION_FAILED — inequivalent arg 'durable'` on startup. The service fails to start. This also breaks the existing services if the exchange gets corrupted during the mismatch.

**Why it happens:**
Each service must independently declare the exchange it uses (idempotent pattern — RabbitMQ ignores matching re-declarations). Copy-paste from a different tutorial declares `new FanoutExchange("rut-uit.events")` with default arguments that differ from the existing declaration.

**How to avoid:**
Copy the exact `FanoutExchange` bean declaration from `schedule-app`'s `RabbitConfig`:

```java
@Bean
public FanoutExchange eventsExchange() {
    return new FanoutExchange("rut-uit.events", true, false); // durable=true, autoDelete=false
}
```

Declare notification-web's own queue with a distinct name:

```java
@Bean
public Queue notificationWebQueue() {
    return QueueBuilder.durable("notification-web.events").build();
}

@Bean
public Binding notificationWebBinding(Queue notificationWebQueue, FanoutExchange eventsExchange) {
    return BindingBuilder.bind(notificationWebQueue).to(eventsExchange);
}
```

**Warning signs:**
- Spring Boot startup fails with `com.rabbitmq.client.ShutdownSignalException: channel error; protocol method: #method<channel.close>(reply-code=406, reply-text=PRECONDITION_FAILED...)`
- RabbitMQ Management UI shows the exchange with wrong `durable` flag
- Other services (Attendance, Schedule) also start failing after the exchange gets redeclared incorrectly

**Phase to address:**
Infrastructure / RabbitMQ setup phase — copy exchange declaration from existing services verbatim, add integration test that starts notification-web with a real RabbitMQ (Testcontainers) and verifies successful queue binding.

---

### Pitfall 8: No Dead Letter Queue — Failed Notification Events Are Silently Dropped

**What goes wrong:**
If the notification-web RabbitMQ consumer throws an exception (e.g., WebSocket send fails, JSON deserialization error for unknown event type), Spring AMQP's default behavior depends on configuration: with `defaultRequeueRejected=true` (Spring AMQP default), the message is requeued indefinitely, creating an infinite retry loop that hammers the CPU. With `nack()` and no DLQ configured, the message is dropped. Neither outcome is acceptable for audit or debugging.

Similarly, the Python bot consumer: if `aio-pika` message processing raises an exception and the message is not acknowledged, RabbitMQ redelivers it. If the exception is permanent (e.g., user's Telegram account blocked the bot), retrying forever wastes resources.

**Why it happens:**
Notification events feel less critical than attendance records — "if a push notification fails, it's not the end of the world." Developers skip DLQ configuration. But infinite requeue loops cause all subsequent events to be delayed by the stuck message. Unknown event types (e.g., a new event type added to the fanout before bot code is updated) cause constant requeue storms.

**How to avoid:**
Configure DLQ for both notification queues:

```java
// notification-web DLQ setup
@Bean
public Queue notificationWebQueue() {
    return QueueBuilder.durable("notification-web.events")
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", "notification-web.events.dlq")
        .withArgument("x-message-ttl", 60000) // 60s max in queue
        .build();
}

@Bean
public Queue notificationWebDlq() {
    return QueueBuilder.durable("notification-web.events.dlq").build();
}
```

For the Python bot, use a try/except in the consumer callback with explicit `nack(requeue=False)` for permanent failures and `nack(requeue=True)` only for transient errors (network timeouts). Unknown event types should be logged and acked (not retried) — they will never succeed:

```python
async def on_message(message: IncomingMessage):
    async with message.process(requeue=False):  # nack on exception, no requeue
        event = json.loads(message.body)
        handler = EVENT_HANDLERS.get(event.get("type"))
        if handler is None:
            logger.warning(f"Unknown event type: {event.get('type')}, acking")
            return  # ack and discard unknown types
        await handler(event)
```

**Warning signs:**
- RabbitMQ Management shows `notification-web.events` queue with non-zero `unacked` count that never drains
- CPU usage on notification-web spikes without corresponding notification volume
- Bot consumer logs show the same event being processed 10+ times in rapid succession
- New event types (added to other services) cause bot restart loops

**Phase to address:**
Infrastructure phase — configure DLQ alongside queue declaration, before writing any consumer logic. Never write a consumer without an associated DLQ.

---

### Pitfall 9: Aiogram 3 Shutdown Hook Not Triggered — RabbitMQ Messages Remain Unacked on SIGTERM

**What goes wrong:**
When Docker stops the bot container (`docker stop` → SIGTERM → SIGKILL after 10s), Aiogram 3's polling loop catches SIGTERM and begins shutdown. However, if a message is currently being processed by the aio-pika consumer (e.g., mid fan-out to 30 students), the consumer coroutine is cancelled. The RabbitMQ message never gets acked or nacked. RabbitMQ holds it as "unacked" until the connection closes (which happens when the process exits). After reconnect (bot restart), the message is redelivered and processed again — causing duplicate notifications to some students.

**Why it happens:**
There are known Aiogram 3 issues where shutdown events are silently skipped when using `dp.start_polling()` with certain signal handling combinations. The asyncio task for the aio-pika consumer is not coordinated with the bot's shutdown sequence.

**How to avoid:**
Register explicit shutdown handlers that drain the consumer before closing:

```python
@dp.shutdown()
async def on_shutdown(bot: Bot):
    logger.info("Bot shutting down — closing RabbitMQ consumer")
    if consumer_channel and not consumer_channel.is_closed:
        await consumer_channel.close()
    if rabbitmq_connection and not rabbitmq_connection.is_closed:
        await rabbitmq_connection.close()
    await bot.session.close()
```

Use `message.process()` context manager (aio-pika's recommended pattern) — it automatically nacks on exception and acks on clean exit, including cancellation:

```python
async with message.process():
    await process_event(event)
    # auto-acked on normal exit, auto-nacked (requeue=False) on exception
```

Test graceful shutdown: send a lesson event, immediately `docker stop` the bot container, restart, verify the event was not processed twice.

**Warning signs:**
- Students receive duplicate "Пара началась" messages after bot restarts
- RabbitMQ Management shows messages cycling between "ready" and "unacked" around bot restart windows
- Shutdown logs do not appear (Aiogram 3 shutdown hook not firing)

**Phase to address:**
Bot infrastructure phase — implement shutdown hooks before any consumer logic. Test with `docker stop` during active message processing.

---

### Pitfall 10: WebSocket Notification-Web Sends to All Group Sessions Including TEACHER Role

**What goes wrong:**
The `GroupSessionRegistry` routes all WebSocket messages for a `group_id` to every connected session that registered with that group. This includes headmen, teachers, and students. When a `lesson.started` event triggers a push notification with an inline "mark attendance" button reference, teachers receive a notification intended for students. When an `excuse.requested` event pushes "Student X requested excuse" to the group, all students in the group receive this message — it should go only to the headman.

**Why it happens:**
The WebSocket routing is group-based (simple `group_id` → sessions map) without role filtering. Event-to-recipient mapping requires knowing the recipient role. Developers implement the group broadcast first and plan to add role filtering later — it never happens.

**How to avoid:**
Store `role` and `is_headman` alongside `group_id` in WebSocket session attributes at handshake time. In `GroupSessionRegistry`, support filtered broadcast:

```java
public void broadcastToGroupFiltered(Long groupId, String message, Predicate<WebSocketSession> filter) {
    sessions.getOrDefault(groupId, new CopyOnWriteArraySet<>()).stream()
        .filter(s -> s.isOpen() && filter.test(s))
        .forEach(s -> sendSafe(s, message));
}

// Usage
registry.broadcastToGroupFiltered(groupId, lessonStartedMsg,
    s -> "STUDENT".equals(s.getAttributes().get("role")));

registry.broadcastToGroupFiltered(groupId, excuseRequestedMsg,
    s -> Boolean.TRUE.equals(s.getAttributes().get("is_headman")));
```

Define the recipient rule for each event type upfront in the event-to-push mapping table before implementing any fan-out.

**Warning signs:**
- Teachers receive "Отметьтесь на паре" (check-in reminder) push notifications
- All students in a group receive "Студент X запросил у.п." (excuse request) notifications intended for headman only
- Student push notifications contain headman-specific action buttons

**Phase to address:**
Event-to-push mapping phase — define recipient rules (STUDENT / HEADMAN / ALL) for every event type before implementing fan-out. Write a test for each event type that verifies messages reach the correct roles only.

---

## Technical Debt Patterns

Shortcuts that seem reasonable but create long-term problems.

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Synchronous gRPC stub (`BlockingStub`) in Python bot | Simpler code, no async complexity | Blocks asyncio event loop on every gRPC call; bot appears frozen under concurrent events | Never in async bot code; use `grpc.aio` or `asyncio.to_thread()` |
| No DLQ on notification queues | Simpler RabbitMQ config | Unknown event types cause infinite retry loops; permanent failures (blocked Telegram user) never drain | Never — DLQ is required even for "non-critical" push notifications |
| Single Redis key `SET reminder:{lesson_id}:{user_id}` (overwrites) | Simpler implementation | First 2 of 3 reminders are never cleaned up; stale messages persist in Telegram chat | Never — use `RPUSH` list from the start |
| Direct RabbitMQ message processing in Telegram send callback | Fewer moving parts | Rate limit backpressure blocks consumer; unacked messages accumulate; queue starves | Never — separate consumer (AMQP ack) from sender (Telegram API) |
| Storing full user list for each group in Redis for bot fan-out | Avoids gRPC call per event | Cache invalidation required when group members change (group.updated event); stale cache causes missed notifications | Acceptable with short TTL (5 min) AND cache invalidation on `group.updated` events |
| SockJS fallback enabled without sticky sessions | Better browser compatibility | SockJS HTTP fallback requests fail on multi-instance deployment without sticky sessions | Acceptable for single-instance VPS deployment (current target) |
| No heartbeat configuration on WebSocket | Zero config | Dead connections not detected; `GroupSessionRegistry` fills with stale sessions; broadcasts fail silently | Never — always configure STOMP heartbeat (10s/10s) |

---

## Integration Gotchas

Common mistakes when connecting to external services.

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Telegram `deleteMessage` | Call without checking if message is <48h old; ignore `MessageToDeleteNotFound` error | Always wrap in try/except; log but don't crash on `BadRequest: message to delete not found` — message already deleted or expired |
| Telegram `sendMessage` fan-out | Call directly in RabbitMQ consumer callback, await one-by-one | Use an `asyncio.Queue` + worker coroutines; consumer enqueues, workers send with rate limiting |
| aio-pika `connect_robust()` | Trust it to always recover consumers after reconnect | Add watchdog coroutine that checks `channel.is_closed` every 30s and re-declares consumer if needed |
| grpcio in Python bot | Use synchronous blocking stub inside `async def` | Use `grpc.aio` stub or `asyncio.to_thread()` for all gRPC calls |
| RabbitMQ `rut-uit.events` exchange | Declare with different arguments than existing declaration | Copy exact exchange bean from schedule-app: `durable=true, autoDelete=false` |
| Spring WebSocket JWT | Read token from HTTP session during WebSocket message handling | Store all claims in `WebSocketSession.getAttributes()` at handshake; never touch HttpSession after upgrade |
| Telegram OTP delivery (deferred from v1.0) | Implement OTP send inline in Auth Service REST handler | Auth Service publishes `otp.requested` event → bot consumer sends OTP via `bot.send_message(telegram_id, code)` |
| Bot `/login` command OTP flow | Store OTP state in Python dict (lost on restart) | Store OTP conversation state in Redis with TTL matching Auth Service OTP TTL (120s) |

---

## Performance Traps

Patterns that work at small scale but fail as usage grows.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Fan-out to 30 students via sequential `await bot.send_message()` in consumer callback | All 30 sends complete in ~3s; consumer ack delayed; queue backs up during lesson burst | Use asyncio.Queue + 5 parallel sender workers; consumer enqueues immediately and acks; workers drain the queue | First concurrent lesson start (2 groups × 30 students = 60 sends in 2 seconds) |
| `GetGroupMembers` gRPC called on every `lesson.started` event (no cache) | Academic Service gRPC handles 1 call/event × N concurrent lessons = gRPC call storm at 9:00 | Cache `GetGroupMembers` per `group_id` in Redis with 5-minute TTL; invalidate on `group.updated` event | 3+ concurrent lessons starting at the same minute |
| WebSocket `GroupSessionRegistry` never cleans up closed sessions | Memory grows ~100 bytes per session over lifetime; broadcasts iterate dead sessions and log IOExceptions | Implement `afterConnectionClosed()` to remove from registry; use `session.isOpen()` check before send and remove if closed | ~1000 cumulative connections (opens/closes over a work day) |
| Reminder scheduling via `asyncio.sleep()` inside coroutines (3 sleeps per lesson per student) | 30 students × 3 reminders = 90 sleeping coroutines per lesson; at 5 concurrent lessons = 450 pending coroutines | Use APScheduler or a single cron-like coroutine that reads pending reminders from Redis instead of sleeping inline | 10+ concurrent active lessons |
| `convertAndSendToUser` blocks the Spring message broker thread | Thread dumps show waiting threads in STOMP broker relay; WebSocket pushes queue up | Use async send or configure the SimpMessagingTemplate's outbound channel with a thread pool | ~50 concurrent WebSocket sessions receiving rapid pushes |

---

## Security Mistakes

Domain-specific security issues beyond general web security.

| Mistake | Risk | Prevention |
|---------|------|------------|
| JWT passed as WebSocket query parameter logged in access logs | Token exposure in log files; token replay | Suppress `?token=` query params from access logs; use short-lived WebSocket tokens (1-minute validity, only for WS connect) |
| Bot accepts any Telegram user with `/start` command and grants access | Unauthenticated Telegram users can probe the system | `/start` only links telegram_id to user if OTP is verified; no information disclosed until OTP flow completes |
| Bot stores `telegram_id → user_id` mapping without TTL | If a student changes Telegram account and does `/start` again, old mapping may persist and cause wrong-user notifications | Store mapping with expiry equal to refresh token lifetime; re-link on each `/login` |
| WebSocket sends `group_id` in push payload without verifying recipient belongs to group | Client could subscribe to arbitrary group WebSocket topics and receive other groups' notifications | Server routes based on `group_id` from JWT (validated at handshake), not from client subscription request |
| Bot sends OTP code in plain text Telegram message visible in chat history | Compromised Telegram account exposes OTP | OTP is short-lived (120s) and single-use; bot can delete the OTP message after sending using `delete_after` pattern |
| notification-bot exposes gRPC client without auth | Internal Academic Service gRPC called without credentials | All gRPC calls are on internal Docker network; no TLS needed for Docker-internal calls; add network-level isolation in docker-compose |

---

## UX Pitfalls

Common user experience mistakes in this domain.

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Bot sends 3 reminders to students who already marked attendance | Students who are `present` receive unnecessary "Отметьтесь" spam | Before each reminder, check Redis for existing attendance record; skip students already marked `present` via gRPC call to Attendance Service or Redis cache of recent checkins |
| Lesson start notification has no context (just "Пара началась") | Student doesn't know which lesson, where, or what to do | Include: subject name, room, time, inline button "Отметиться" opening Mini App with `?lesson_id=X` |
| Bot sends "Пара отменена" without specifying which lesson | Student has multiple lessons today; can't identify which is cancelled | Include: subject name, date, time slot in cancellation message |
| WebSocket push notification in web panel disappears immediately (no persistence) | Admin/teacher misses excuse request notification if not looking at panel | Show notifications in a sidebar list with timestamp; mark as "read" on click; persist in-memory for current session (not DB) |
| Bot reminder sent to student with `free_attendance` status | Student with free attendance exemption still receives "mark attendance" spam | Check student's lesson status before sending reminder; skip if `free_attendance` |

---

## "Looks Done But Isn't" Checklist

Things that appear complete but are missing critical pieces.

- [ ] **Rate limiting:** Bot sends messages without errors in dev (1 student) — verify with 30+ simultaneous sends that no 429 errors occur and all messages arrive; test during simulated lesson burst (3 concurrent groups).
- [ ] **Reminder cleanup:** Lesson closes and `deleteMessage` is called — verify the Redis list contains all 3 message_ids (not just the last one); verify all 3 are deleted in Telegram; verify `MessageToDeleteNotFound` is handled gracefully.
- [ ] **aio-pika reconnect:** Consumer starts and processes messages — restart RabbitMQ container, wait 30s, publish a test event, verify the event is processed (not silently dropped).
- [ ] **WebSocket session cleanup:** Sessions are added to `GroupSessionRegistry` on connect — connect 5 clients, disconnect 3, verify registry count is 2 (not 5); verify broadcast doesn't log errors for closed sessions.
- [ ] **JWT token in WebSocket:** User connects successfully with valid token — test that a user connected with a valid token at T=0 still receives notifications at T=20min (after JWT expiry) without server-side errors.
- [ ] **Role-filtered broadcasts:** `lesson.started` push is sent — verify teacher role sessions do NOT receive the student "Отметьтесь" push; verify `excuse.requested` push reaches ONLY `is_headman=true` sessions.
- [ ] **DLQ:** Consumer processes events correctly — send a malformed event JSON to the queue and verify it ends up in the DLQ (not requeued forever); verify the consumer continues processing subsequent valid events.
- [ ] **gRPC from Python:** `GetGroupMembers` is called in bot — verify using `asyncio.get_event_loop().slow_callback_duration` logging that gRPC calls do not produce "Executing <coroutine> took X.XXX seconds" warnings.
- [ ] **Duplicate queue declarations:** notification-web starts successfully — verify with RabbitMQ Management UI that `notification-web.events` queue exists AND is bound to `rut-uit.events` exchange; verify `notification-bot.events` exists separately.
- [ ] **Bot shutdown:** Bot container stops — run `docker stop notification-bot` while a lesson event is being processed; restart; verify the event was processed exactly once (not zero times, not twice).

---

## Recovery Strategies

When pitfalls occur despite prevention, how to recover.

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Students have stale "Отметьтесь" messages in Telegram after lesson closed | LOW | Run cleanup script: iterate all `reminder:msgs:{lesson_id}:*` keys for the closed lesson, call `deleteMessage` for each, ignore not-found errors |
| aio-pika consumer silently stopped; events accumulated in queue | LOW | Restart `notification-bot` container; messages in `notification-bot.events` queue will be redelivered; if messages are lesson.started events from hours ago, consider discarding them (they are no longer relevant) |
| Duplicate notifications sent (bot restarted mid-processing) | LOW | No automated recovery — duplicate Telegram messages cannot be unsent after send. Add idempotency key to events: check Redis `processed:{event_id}` before processing; set after processing with TTL 1h |
| notification-web crashes and misses events during downtime | MEDIUM | WebSocket push notifications are fire-and-forget; events that arrived during downtime are in `notification-web.events` queue and will be processed on restart; UI will catch up via next HTTP API call (schedule/attendance endpoints); no data loss in the DB |
| Wrong role receives sensitive push notification | MEDIUM | Identify affected sessions from logs; no automated recall possible in WebSocket. Fix routing bug, redeploy; audit logs for other role leakage |
| RabbitMQ exchange re-declared with wrong arguments — other services break | HIGH | Delete the exchange via RabbitMQ Management UI (caution: all bound queues lose messages); redeclare with correct arguments; restart all services in order: schedule → attendance → notification-web → notification-bot |

---

## Pitfall-to-Phase Mapping

How roadmap phases should address these pitfalls.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Telegram 429 burst at lesson start | Bot fan-out + rate limiting phase | Test: send 30 simultaneous messages, verify all delivered, no 429 errors and no event loop stall |
| Reminder message_id not stored (list vs string) | Reminder lifecycle phase | Test: send 3 reminders, assert Redis list has 3 elements; simulate lesson.closed, assert all 3 deleted |
| aio-pika silent consumer after reconnect | Bot infrastructure phase | Test: restart RabbitMQ container, publish event, verify consumer processes it |
| WebSocket JWT expiry mid-session | WebSocket JWT auth phase | Test: connect with valid token, advance mock clock 20min, assert notifications still route correctly |
| Group fan-out without role filtering | Event-to-push mapping phase | Test: connect STUDENT + TEACHER sessions for same group; send lesson.started; assert only STUDENT receives it |
| Python gRPC blocking event loop | Bot gRPC client phase | Test: 5 concurrent lesson events; assert no `slow_callback` warnings |
| RabbitMQ exchange argument mismatch | Infrastructure / queue declaration phase | Test: start with Testcontainers RabbitMQ, assert clean startup with no PRECONDITION_FAILED errors |
| No DLQ — unknown event types cause retry storm | Infrastructure phase | Test: publish event with unknown `type` field; assert it reaches DLQ, not requeued; consumer processes next valid event |
| SIGTERM causes unacked messages | Bot shutdown phase | Test: docker stop during active processing; verify exactly-once delivery on restart |
| Push to wrong role (TEACHER gets student notification) | Event-to-push mapping phase | Role filter test: one session per role, send event, assert only correct role receives it |

---

## Sources

- Telegram Bot API rate limits: [Telegram Bots FAQ](https://core.telegram.org/bots/faq), [Telegram limits reference](https://limits.tginfo.me/en)
- Telegram 429 retry patterns: [Fixing 429 Errors — telegramhpc.com](https://telegramhpc.com/news/574/), [gramio.dev rate limits](https://gramio.dev/rate-limits)
- aio-pika reconnect bugs: [RobustConnection silent consumer issue #577](https://github.com/mosquito/aio-pika/issues/577), [RobustConnection not reconnecting #202](https://github.com/mosquito/aio-pika/issues/202), [Consumer not resuming #563](https://github.com/mosquito/aio-pika/issues/563)
- Spring WebSocket JWT session pitfalls: [Spring Session WebSocket docs](https://docs.spring.io/spring-session/reference/guides/boot-websocket.html), [Spring Security WebSocket session timeout issue #8992](https://github.com/spring-projects/spring-security/issues/8992)
- Spring STOMP user destinations and multi-instance routing: [Spring Framework user-destination docs](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/user-destination.html), [convertAndSendToUser thread blocking issue #22295](https://github.com/spring-projects/spring-framework/issues/22295)
- grpc.aio blocking call pitfall: [gRPC Python AsyncIO API](https://grpc.github.io/grpc/python/grpc_asyncio.html), [asyncio blocking call fix](https://www.technetexperts.com/asyncio-blocking-call-concurrency-fix/)
- Aiogram 3 shutdown bugs: [shutdown event skipped issue #1410](https://github.com/aiogram/aiogram/issues/1410), [shutdown not executing discussion #1326](https://github.com/aiogram/aiogram/discussions/1326)
- RabbitMQ DLQ patterns: [RabbitMQ DLX official docs](https://www.rabbitmq.com/docs/dlx), [Scalable notification with DLQ — medium](https://ikabolo59.medium.com/building-a-scalable-notification-service-with-rabbitmq-part-2-c5dfb4da4659)
- RabbitMQ prefetch and slow consumer: [Consumer Prefetch — RabbitMQ docs](https://www.rabbitmq.com/docs/consumer-prefetch), [CloudAMQP prefetch optimization](https://www.cloudamqp.com/blog/how-to-optimize-the-rabbitmq-prefetch-count.html)
- WebSocket scaling pitfalls: [How to scale WebSocket — tsh.io](https://tsh.io/blog/how-to-scale-websocket), [SockJS sticky sessions Spring issue #17529](https://github.com/spring-projects/spring-framework/issues/17529)

---
*Pitfalls research for: Notification Service (WebSocket + Telegram Bot) — adding real-time push to existing RutCampusTrack microservice system*
*Researched: 2026-04-04*
