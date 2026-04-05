# Phase 22: Bot Infrastructure Layer - Context

**Gathered:** 2026-04-05
**Status:** Ready for planning

<domain>
## Phase Boundary

All three infrastructure clients operational and tested in isolation for the Telegram notification bot. aio-pika consumer with watchdog coroutine reconnects reliably after RabbitMQ restart. Async gRPC client resolves group members with telegram_ids. Redis async client stores and retrieves reminder message_ids as lists. All outgoing Telegram messages pass through a global throttled send queue respecting Telegram's 30 msg/sec limit. No Telegram commands, no event dispatching logic, no reminder lifecycle — those are Phases 23-25.

</domain>

<decisions>
## Implementation Decisions

### Watchdog Strategy
- **D-01:** Wrapper coroutine pattern — a watchdog coroutine wraps the consumer task. If the consumer exits (exception or silent death from dead channel), log the error, wait 5s (fixed delay), and restart the consumer. The `__main__.py` loop becomes `while True: run consumer, on failure: log + sleep 5s + retry`.
- **D-02:** Retry forever — no max retry limit. The bot is a long-running daemon; Docker `unless-stopped` is the last safety net. Each retry attempt is logged at WARNING level.
- **D-03:** Health endpoint stays UP during reconnection attempts — only reports DOWN if the watchdog coroutine itself crashes. Avoids Docker restart during brief RabbitMQ blips.
- **D-04:** prefetch_count=10 on the aio-pika channel. Prevents memory buildup during event bursts while providing headroom for multiple lessons starting simultaneously.
- **D-05:** INFO-level logging for every received event (log event_type). Low-traffic system (~50-100 events/day) where visibility matters more than log volume.

### Throttled Send Queue
- **D-06:** asyncio.Queue + token bucket pattern. An `asyncio.Queue` accepts send requests from any coroutine. A single worker coroutine drains the queue using a token bucket (refill 30 tokens/sec). Callers `await queue.put(message)`, worker handles pacing and sending.
- **D-07:** Single global queue — one queue, one worker coroutine shared across all event handlers. Telegram rate limit is global (not per-chat), so a single queue naturally enforces it.
- **D-08:** Error handling: retry 3x with backoff (1s/2s/4s). On 429: respect `Retry-After` header. After 3 failures: log error with user_id/chat_id and skip. No DLQ — a missed Telegram message is not critical.
- **D-09:** Simple counters logged periodically — queue depth logged every 60s if non-empty, total sent/failed logged on shutdown. No external metrics system.

### gRPC Client
- **D-10:** Persistent `grpc.aio` channel created once at startup, reused for all calls. grpc.aio handles reconnection automatically. Standard pattern for long-running services.
- **D-11:** In-memory cache for `GetGroupMembers` results with 5-minute TTL. Group composition changes rarely. Dict keyed by `group_id`, value is `(timestamp, list[StudentInfo])`. Invalidated on `group.updated` RabbitMQ event if received.
- **D-12:** grpcio pinned at 1.73.0 (carried from Phase 20 — protobuf 5.x compatible).

### Redis Client
- **D-13:** Use `redis.asyncio` (redis-py) with `redis[hiredis]` for C parser speedup. Official library, aioredis merged into redis-py since v4.2+.
- **D-14:** Default connection pool via `redis.asyncio.from_url()` (max 10 connections). No tuning needed for single-instance bot.
- **D-15:** Error handling: log and continue. If Redis is down, bot continues sending Telegram messages — reminder message_id storage degrades gracefully (no cleanup on lesson close). No retry, no circuit breaker.

### Claude's Discretion
- Token bucket implementation details (stdlib vs third-party)
- Exact gRPC client wrapper class structure
- Redis helper class design (thin wrapper vs direct calls)
- Test structure and fixtures
- `__main__.py` signal handling and graceful shutdown

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing Bot Code (Phase 20 skeleton)
- `services/notification-bot/bot/__main__.py` — Entry point with health server and consumer startup (enhance with watchdog)
- `services/notification-bot/bot/consumers/event_consumer.py` — Basic aio-pika consumer (enhance with prefetch, watchdog integration)
- `services/notification-bot/bot/config.py` — Pydantic Settings with Redis key template and TTL
- `services/notification-bot/requirements.txt` — Current Python dependencies

### gRPC Proto Definition
- `proto/academic.proto` — `GetGroupMembers` RPC returning `StudentInfo` with `telegram_id` field

### Event Schemas
- `event-schemas/` — All JSON Schema files defining event envelope format and payloads

### Architecture & Decisions
- `docs/architecture.md` — Service topology, communication patterns
- `docs/phases-plan.md` §Фаза 6 — Notification Service detailed plan
- `.planning/STATE.md` §Accumulated Context — grpcio version, aio-pika watchdog, Redis RPUSH decision

### Prior Phase Context
- `.planning/phases/20-shared-infrastructure/20-CONTEXT.md` — Queue naming, DLQ strategy, Redis key namespace, bot package layout

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `event_consumer.py`: Basic aio-pika consumer with exchange/queue/DLQ declaration — enhance with prefetch and watchdog integration
- `config.py`: Pydantic Settings already has `redis_host`, `redis_port`, `academic_grpc_host`, `academic_grpc_port`, `reminder_key_template`, `reminder_key_ttl`
- `__main__.py`: Health server and consumer startup — enhance with watchdog wrapper loop
- `bot/grpc_client/`, `bot/services/`: Empty packages ready for gRPC client and send queue

### Established Patterns
- aio-pika `connect_robust` for auto-reconnect (Phase 20 decision)
- Pydantic Settings with `.env` file for config
- aiohttp health server on port 8081
- Event envelope: `{event_type, event_id, occurred_at, payload}`

### Integration Points
- `bot/consumers/event_consumer.py` → will dispatch to event handlers in Phase 24 (currently just logs)
- `bot/grpc_client/` → async client for `academic-service:19091`
- `bot/services/` → throttled send queue used by all future event handlers
- `requirements.txt` → add `redis[hiredis]`

</code_context>

<specifics>
## Specific Ideas

No specific requirements — follow established Python async patterns. Token bucket for rate limiting, grpc.aio for async gRPC, redis.asyncio for Redis.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 22-bot-infrastructure-layer*
*Context gathered: 2026-04-05*
