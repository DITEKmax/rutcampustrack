# Phase 22: Bot Infrastructure Layer - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-05
**Phase:** 22-bot-infrastructure-layer
**Areas discussed:** Watchdog strategy, Throttled send queue, gRPC client caching, Redis client setup

---

## Watchdog Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Wrapper coroutine | Wraps consumer task — on exit/exception: log + sleep + restart | ✓ |
| Channel close callback | Register on_close callback, trigger re-creation via asyncio.Event | |
| Periodic health probe | Separate coroutine pings RabbitMQ every 10s | |

**User's choice:** Wrapper coroutine
**Notes:** Simple, proven pattern for long-running async consumers.

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed 5s delay | Simple fixed delay between retries | ✓ |
| Exponential backoff | 1s → 2s → 4s → ... → 60s max | |
| Immediate + cap | First immediate, then 5s, 10s, cap 30s | |

**User's choice:** Fixed 5s delay
**Notes:** Single-instance VPS, RabbitMQ restarts are rare.

| Option | Description | Selected |
|--------|-------------|----------|
| Retry forever | Docker restart is last safety net | ✓ |
| Max 10 retries then exit | Exit process after 10 failures | |

**User's choice:** Retry forever

| Option | Description | Selected |
|--------|-------------|----------|
| INFO per event | Log event_type for every message | ✓ |
| DEBUG per event | Only errors at INFO+ | |
| Count-based logging | Every 10th at INFO | |

**User's choice:** INFO per event
**Notes:** Low-traffic system, visibility matters.

| Option | Description | Selected |
|--------|-------------|----------|
| Report DOWN during reconnect | Docker healthcheck detects and can restart | |
| Stay UP during retries | Only DOWN if watchdog itself crashes | ✓ |

**User's choice:** Stay UP during retries
**Notes:** Avoids unnecessary Docker restarts during brief RabbitMQ blips.

| Option | Description | Selected |
|--------|-------------|----------|
| prefetch_count=10 | Up to 10 unacknowledged messages | ✓ |
| prefetch_count=1 | Strict sequential | |
| No prefetch limit | Let RabbitMQ push freely | |

**User's choice:** prefetch_count=10

---

## Throttled Send Queue

| Option | Description | Selected |
|--------|-------------|----------|
| asyncio.Queue + token bucket | Queue accepts requests, worker drains with token bucket (30/sec) | ✓ |
| asyncio.Semaphore + sleep | Semaphore limits concurrent sends, sleep(1/30) after each | |
| Fixed delay per message | Sequential asyncio.sleep(0.035) between sends | |

**User's choice:** asyncio.Queue + token bucket

| Option | Description | Selected |
|--------|-------------|----------|
| Retry 3x with backoff, then skip | 429: respect Retry-After. Others: 1s/2s/4s backoff. After 3: log and skip | ✓ |
| Retry forever on 429, skip others | Always retry rate limit, skip other errors | |
| No retry, log and skip | Any failure logged and skipped | |

**User's choice:** Retry 3x with backoff, then log and skip

| Option | Description | Selected |
|--------|-------------|----------|
| Single global queue | One queue, one worker for all handlers | ✓ |
| Per-priority queues | High (lesson.started) and normal priority | |

**User's choice:** Single global queue

| Option | Description | Selected |
|--------|-------------|----------|
| Simple counters in logs | Queue depth every 60s, totals on shutdown | ✓ |
| No metrics | Just individual send errors | |

**User's choice:** Simple counters in logs

---

## gRPC Client Caching

| Option | Description | Selected |
|--------|-------------|----------|
| In-memory cache with 5min TTL | Dict keyed by group_id, invalidate on group.updated event | ✓ |
| No cache, call every time | Always fresh, ~50-100 calls/day is negligible | |
| Cache forever, invalidate on event | Most efficient but stale data risk | |

**User's choice:** In-memory cache with 5-minute TTL

| Option | Description | Selected |
|--------|-------------|----------|
| Persistent channel | grpc.aio channel created once at startup | ✓ |
| Connect per call | New channel for each call | |

**User's choice:** Persistent channel

---

## Redis Client Setup

| Option | Description | Selected |
|--------|-------------|----------|
| redis.asyncio (redis-py) with hiredis | Official library, C parser speedup | ✓ |
| redis.asyncio without hiredis | Pure Python parser | |

**User's choice:** redis.asyncio with hiredis

| Option | Description | Selected |
|--------|-------------|----------|
| Log and continue | Redis down → bot continues, reminders degrade gracefully | ✓ |
| Retry then fail the event | Retry 3x, nack to DLQ | |
| Circuit breaker | Stop trying after N failures for 30s | |

**User's choice:** Log and continue

| Option | Description | Selected |
|--------|-------------|----------|
| Default pool | from_url() creates pool (max 10 connections) | ✓ |
| Single connection | single_connection_client=True | |

**User's choice:** Default pool

---

## Claude's Discretion

- Token bucket implementation details
- gRPC client wrapper class structure
- Redis helper class design
- Test structure and fixtures
- Signal handling and graceful shutdown

## Deferred Ideas

None — discussion stayed within phase scope.
