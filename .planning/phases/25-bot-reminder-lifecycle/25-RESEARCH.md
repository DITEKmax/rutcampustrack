# Phase 25: Bot Reminder Lifecycle - Research

**Researched:** 2026-04-05
**Domain:** Python asyncio task scheduling, Aiogram 3 bot.delete_message, Redis list lifecycle management
**Confidence:** HIGH

## Summary

Phase 25 completes the reminder lifecycle for the notification-bot. Phase 24 established the foundation: `lesson.started` sends the initial check-in message and stores its `message_id` in Redis via `ReminderRedisClient` (RPUSH list at key `reminder:msgs:{lesson_id}:{user_id}`, TTL 86400s). Phase 25 adds three missing behaviors on top of that foundation: (1) scheduled second and third reminder messages (NOTIF-02, NOTIF-03), (2) cleanup of all reminders when `lesson.closed` arrives (NOTIF-04), and (3) immediate cleanup when `attendance.marked` arrives with `status=present` (NOTIF-05).

The core design question is: how to schedule the midpoint and near-end reminders. There is no `lesson.midpoint` RabbitMQ event — the bot must derive timing from `start_time` and `end_time` strings in the `lesson.started` payload (format: `"HH:MM"`) and spawn background `asyncio.Task` instances via `asyncio.create_task`. These tasks sleep until the target wall-clock time (using `asyncio.sleep` with a calculated delay), then check whether the lesson is still active (Redis key still exists for at least one student), and send the reminder message through the existing `TelegramSendQueue`. A per-lesson in-memory registry tracks active timer tasks so they can be cancelled if a `lesson.closed` event arrives before the timers fire.

The cleanup handlers (NOTIF-04, NOTIF-05) read the Redis list with `get_message_ids`, call `bot.delete_message` for each stored `message_id`, and then call `delete_key` to remove the Redis entry. Aiogram's `delete_message` raises `TelegramBadRequest` (message already deleted) — this must be caught and silently ignored.

**Primary recommendation:** Add a `LessonTimerRegistry` dict (`lesson_id -> list[asyncio.Task]`) to `EventDispatcher`, spawn two timed tasks per lesson in `handle_lesson_started` (or a new handler invoked from it), and add `handle_lesson_closed` + `handle_attendance_marked` handlers registered in `EventDispatcher._handlers`.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| NOTIF-02 | Student receives reminder at lesson midpoint if not yet checked in | `asyncio.create_task` sleeping until midpoint wall-clock time, then fan-out send; `message_id` appended to Redis list |
| NOTIF-03 | Student receives final reminder near lesson end if not yet checked in | Same pattern as NOTIF-02, but delay calculated to ~5 min before `end_time` |
| NOTIF-04 | All reminder messages deleted from Telegram when lesson closes | `lesson.closed` handler reads Redis `LRANGE`, calls `bot.delete_message` per id, then `DEL` key; cancel active timer tasks |
| NOTIF-05 | Reminder messages deleted immediately when student checks in | `attendance.marked` handler with `status==present` reads Redis list for `(lesson_id, user_id)`, deletes messages, removes key |
</phase_requirements>

## Standard Stack

### Core (already installed — zero new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| aiogram | 3.15.0 | `bot.delete_message(chat_id, message_id)` — deletes a Telegram message by id | Already in requirements.txt [VERIFIED: codebase] |
| redis[hiredis] | 5.2.1 | `ReminderRedisClient.get_message_ids` / `delete_key` — already implemented | Already in requirements.txt [VERIFIED: codebase] |
| asyncio (stdlib) | 3.11+ | `asyncio.create_task`, `asyncio.sleep`, `asyncio.CancelledError` — timed reminder scheduling | Python stdlib [ASSUMED] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| fakeredis[aioredis] | >=2.34.0 | In-process Redis mock for unit tests | All tests touching ReminderRedisClient [VERIFIED: requirements-test.txt] |
| pytest-asyncio | >=1.1.0 | `asyncio_mode = auto` — all test coroutines run without decorator | All async tests [VERIFIED: pytest.ini] |

### No New Dependencies

Phase 25 requires **zero new pip packages**. All timing, task management, and deletion APIs are available from installed libraries. [VERIFIED: codebase requirements.txt]

## Architecture Patterns

### Recommended Project Structure

```
bot/
  notifications/
    lesson_started.py      # Existing — no changes needed
    lesson_cancelled.py    # Existing — no changes needed
    homework.py            # Existing — no changes needed
    headman_alerts.py      # Existing — no changes needed
    lesson_closed.py       # NEW — NOTIF-04: delete all reminders on lesson close
    attendance_marked.py   # NEW — NOTIF-05: delete reminders on student check-in
  services/
    reminder_scheduler.py  # NEW — spawns and tracks timed asyncio tasks (NOTIF-02, NOTIF-03)
  consumers/
    event_dispatcher.py    # MODIFY — register new handlers + inject ReminderScheduler
tests/
  test_lesson_closed.py         # NEW — NOTIF-04
  test_attendance_marked.py     # NEW — NOTIF-05
  test_reminder_scheduler.py    # NEW — NOTIF-02, NOTIF-03
```

### Pattern 1: asyncio.create_task for Timed Reminders

**What:** When `lesson.started` is processed, calculate wall-clock delays to midpoint and near-end, then spawn two `asyncio.Task` instances that sleep and fire.

**When to use:** The bot has no `lesson.midpoint` event — timing must be self-managed from payload data. `asyncio.create_task` integrates cleanly with the existing single-process asyncio event loop.

**Delay calculation (NOTIF-02 midpoint):**
```python
# Source: Python stdlib datetime — [ASSUMED pattern, no external doc needed]
from datetime import datetime, date, timezone

def _parse_hhmm(s: str) -> datetime:
    """Parse 'HH:MM' into today's UTC-aware datetime."""
    h, m = map(int, s.split(":"))
    return datetime.now(tz=timezone.utc).replace(hour=h, minute=m, second=0, microsecond=0)

def midpoint_delay_seconds(start_time: str, end_time: str) -> float:
    start_dt = _parse_hhmm(start_time)
    end_dt = _parse_hhmm(end_time)
    midpoint = start_dt + (end_dt - start_dt) / 2
    now = datetime.now(tz=timezone.utc)
    return max(0.0, (midpoint - now).total_seconds())

def near_end_delay_seconds(end_time: str, offset_minutes: int = 5) -> float:
    end_dt = _parse_hhmm(end_time)
    near_end = end_dt - timedelta(minutes=offset_minutes)
    now = datetime.now(tz=timezone.utc)
    return max(0.0, (near_end - now).total_seconds())
```

**Important edge case:** `start_time`/`end_time` are local Moscow time (UTC+3), not UTC. The Schedule Service stores times as `TIME` in PostgreSQL without timezone. The delay calculation must use the same reference clock. Since the bot runs in a Docker container that may be UTC, use `datetime.now()` without tz (naive), or consistently use a UTC offset for Moscow. **This is an open question — see Open Questions #1.**

**Pattern 2: LessonTimerRegistry**

**What:** A dict in `ReminderScheduler` mapping `lesson_id -> list[asyncio.Task]` so tasks can be cancelled on `lesson.closed` before they fire.

```python
# Source: Python stdlib asyncio — [ASSUMED]
class ReminderScheduler:
    def __init__(self):
        self._timers: dict[int, list[asyncio.Task]] = {}

    def schedule(self, lesson_id: int, delay: float, coro) -> None:
        task = asyncio.create_task(self._run_after(delay, coro))
        self._timers.setdefault(lesson_id, []).append(task)

    async def _run_after(self, delay: float, coro) -> None:
        await asyncio.sleep(delay)
        await coro

    def cancel_lesson(self, lesson_id: int) -> None:
        for task in self._timers.pop(lesson_id, []):
            task.cancel()
```

**Pattern 3: delete_message with silent BadRequest handling**

**What:** Aiogram raises `TelegramBadRequest` if a message was already deleted (user deleted it manually, or duplicate event). Must be caught and ignored.

```python
# Source: aiogram 3.x exception hierarchy — [ASSUMED, verify in aiogram docs]
from aiogram.exceptions import TelegramBadRequest

async def delete_messages(bot, chat_id: int, message_ids: list[int]) -> None:
    for msg_id in message_ids:
        try:
            await bot.delete_message(chat_id=chat_id, message_id=msg_id)
        except TelegramBadRequest:
            pass  # Already deleted — not an error
        except Exception:
            logger.warning("Failed to delete message_id=%d for chat_id=%d", msg_id, chat_id)
```

**Pattern 4: attendance.marked — only act on status=present**

**What:** The `attendance.marked` event fires for all status changes (`present`, `absent`, `excused`, `free_attendance`). Only `present` (student self-checked-in) triggers immediate reminder deletion. `absent` is auto-scheduler, `excused`/`free_attendance` do not need cleanup — the lesson will close and NOTIF-04 will clean up.

```python
# NOTIF-05 guard
status = payload.get("status")
if status != "present":
    return  # Only clean up on actual student check-in
```

**Pattern 5: lesson.closed — fan-out delete via all stored keys**

**What:** `lesson.closed` payload only contains `lesson_id` and `group_id` (no `user_id`). The bot must discover which students have Redis keys for this lesson. Since each key is `reminder:msgs:{lesson_id}:{user_id}`, the handler must either:
- (a) Resolve group members via `academic_client.get_group_members(group_id)` and attempt `get_message_ids` for each, or
- (b) Use Redis SCAN pattern `reminder:msgs:{lesson_id}:*` to enumerate keys directly.

**Option (a) is preferred** — it avoids Redis SCAN complexity and stays consistent with the existing pattern. Group members are already cached in `AcademicGrpcClient` (5-minute TTL). [VERIFIED: `academic_client.py` has `_cache` with `_CACHE_TTL_SECONDS = 300`]

### Anti-Patterns to Avoid

- **Storing timers in EventDispatcher directly:** Keeps `EventDispatcher` focused on dispatch; move timer state to `ReminderScheduler`.
- **Using `asyncio.sleep` inside `handle_lesson_started` directly:** Blocks the dispatch loop. Always use `asyncio.create_task`.
- **Not cancelling timer tasks on lesson.closed:** If a lesson is closed before midpoint, the tasks will fire after close and send stale reminders.
- **Using Redis SCAN without cursor:** SCAN is iterative; must loop until cursor == 0. Option (a) is simpler.
- **Re-raising TelegramBadRequest:** Already-deleted messages are expected — idempotent delete must succeed silently.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Message deletion retry | Custom retry loop for delete_message | Catch `TelegramBadRequest`, ignore — no retry needed | Delete failures for non-existent messages are not transient |
| Time parsing | Custom regex for "HH:MM" | `str.split(":")` + `datetime.replace()` | Trivial, stdlib-only |
| Lesson tracking state | External DB or file store | In-memory `dict[int, list[Task]]` in `ReminderScheduler` | Bot is single-process; in-memory is sufficient and restarts are handled by lesson.started re-init |

**Key insight:** All infrastructure for this phase already exists. The work is wiring existing components (Redis client, send queue, academic client, bot) with new asyncio task scheduling and two new event handlers.

## Common Pitfalls

### Pitfall 1: Time zone mismatch in delay calculation

**What goes wrong:** `start_time` = `"09:00"` is Moscow time (UTC+3). If the bot computes `datetime.now(UTC).replace(hour=9, minute=0)`, the midpoint fires 3 hours late.
**Why it happens:** Schedule Service stores lesson times as `TIME` in PostgreSQL without timezone context. The event payload carries `"09:00"` as a naive time string.
**How to avoid:** Both bot and Schedule Service must agree on the reference clock. Since all services run in Docker on the same host, using naive `datetime.now()` (local system clock) is consistent IF the container timezone is configured. Alternatively, use a fixed UTC+3 offset.
**Warning signs:** Reminders arrive at wrong clock times during integration testing.

### Pitfall 2: Timer task leaks after bot restart

**What goes wrong:** If the bot restarts mid-lesson, the in-memory `ReminderScheduler._timers` is empty but Redis still has `lesson.started` data. No reminders fire for the restarted lesson.
**Why it happens:** `asyncio.Task` objects are in-process only.
**How to avoid:** This is acceptable behavior for this MVP — documented as known limitation. The 24h Redis TTL ensures eventual cleanup. The `lesson.closed` handler will still delete Redis keys when the lesson ends (triggered by RabbitMQ event, not the timer).

### Pitfall 3: attendance.marked fires for non-present statuses

**What goes wrong:** Auto-scheduler writes `absent` status via `attendance.marked`. If the handler deletes reminders on any status, students already marked absent lose their Redis keys and NOTIF-04 cleanup for those students fails silently.
**Why it happens:** `attendance.marked` covers all status transitions.
**How to avoid:** Guard with `if status != "present": return` at the top of the handler.

### Pitfall 4: TelegramBadRequest not imported from correct module

**What goes wrong:** `from aiogram.exceptions import TelegramBadRequest` — wrong import path in older aiogram versions.
**Why it happens:** Aiogram 3.x restructured exceptions.
**How to avoid:** [ASSUMED — verify import path against aiogram 3.15.0 installed version]
**Verified pattern:** Phase 24 already uses `from aiogram.exceptions import TelegramRetryAfter` in `send_queue.py` — same module, so `TelegramBadRequest` follows the same pattern. [VERIFIED: `send_queue.py` line 57]

### Pitfall 5: Concurrent delete_message for same message_id

**What goes wrong:** Both NOTIF-04 (lesson.closed) and NOTIF-05 (attendance.marked) attempt to delete the same message. The second call gets `TelegramBadRequest`.
**Why it happens:** Race condition if lesson closes right as student checks in.
**How to avoid:** `TelegramBadRequest` is already silently ignored per Pattern 3. Both handlers call `delete_key` — the second `DEL` on a non-existent key is a no-op in Redis.

### Pitfall 6: get_message_ids returns empty for student who already checked in

**What goes wrong:** NOTIF-04 iterates over group members, but student who checked in already had their key deleted (NOTIF-05 fired). `get_message_ids` returns `[]`, loop does nothing for that student — correct behavior, but needs explicit test.
**Why it happens:** Redis key already deleted by NOTIF-05.
**How to avoid:** Empty list → skip silently. No bug, but add a test case for this path.

## Code Examples

### lesson_closed.py handler skeleton

```python
# Source: project patterns from lesson_started.py + redis_client.py [VERIFIED: codebase]
async def handle_lesson_closed(
    event: dict,
    bot: Bot,
    academic_client,
    redis_client: ReminderRedisClient,
    reminder_scheduler,
) -> None:
    payload = event.get("payload", {})
    lesson_id = payload.get("lesson_id")
    group_id = payload.get("group_id")
    if lesson_id is None or group_id is None:
        logger.warning("lesson.closed missing required fields")
        return

    # Cancel pending reminder timers before they fire
    reminder_scheduler.cancel_lesson(lesson_id)

    # Resolve group members (cached — low latency)
    members = await academic_client.get_group_members(group_id)
    for student in members:
        if not student.telegram_id:
            continue
        message_ids = await redis_client.get_message_ids(lesson_id, student.user_id)
        for msg_id in message_ids:
            try:
                await bot.delete_message(chat_id=student.telegram_id, message_id=msg_id)
            except TelegramBadRequest:
                pass
        if message_ids:
            await redis_client.delete_key(lesson_id, student.user_id)
```

### attendance_marked.py handler skeleton

```python
# Source: project patterns from event schemas + redis_client.py [VERIFIED: codebase]
async def handle_attendance_marked(
    event: dict,
    bot: Bot,
    academic_client,
    redis_client: ReminderRedisClient,
) -> None:
    payload = event.get("payload", {})
    status = payload.get("status")
    if status != "present":
        return  # Only clean up on self check-in

    lesson_id = payload.get("lesson_id")
    user_id = payload.get("user_id")
    if lesson_id is None or user_id is None:
        return

    # Look up telegram_id for this user
    members = await academic_client.get_group_members(payload.get("group_id", 0))
    student = next((m for m in members if m.user_id == user_id), None)
    if student is None or not student.telegram_id:
        return

    message_ids = await redis_client.get_message_ids(lesson_id, user_id)
    for msg_id in message_ids:
        try:
            await bot.delete_message(chat_id=student.telegram_id, message_id=msg_id)
        except TelegramBadRequest:
            pass
    if message_ids:
        await redis_client.delete_key(lesson_id, user_id)
```

### ReminderScheduler timed send

```python
# Source: Python stdlib asyncio [ASSUMED pattern]
class ReminderScheduler:
    def __init__(self, bot, academic_client, send_queue, redis_client, config):
        self._bot = bot
        self._academic_client = academic_client
        self._send_queue = send_queue
        self._redis_client = redis_client
        self._config = config
        self._timers: dict[int, list[asyncio.Task]] = {}

    def schedule_reminders(self, lesson_id, group_id, start_time, end_time) -> None:
        mid_delay = midpoint_delay_seconds(start_time, end_time)
        end_delay = near_end_delay_seconds(end_time, offset_minutes=5)
        mid_task = asyncio.create_task(
            self._send_reminder_after(mid_delay, lesson_id, group_id, "reminder_mid")
        )
        end_task = asyncio.create_task(
            self._send_reminder_after(end_delay, lesson_id, group_id, "reminder_end")
        )
        self._timers[lesson_id] = [mid_task, end_task]

    async def _send_reminder_after(self, delay, lesson_id, group_id, label) -> None:
        await asyncio.sleep(delay)
        # Check at least one student still has a key (lesson not yet closed)
        members = await self._academic_client.get_group_members(group_id)
        for student in members:
            if not student.telegram_id:
                continue
            existing_ids = await self._redis_client.get_message_ids(lesson_id, student.user_id)
            if not existing_ids:
                continue  # Already checked in or lesson closed
            async def send_and_store(s=student):
                result = await self._bot.send_message(
                    chat_id=s.telegram_id,
                    text="Напоминание: отметьтесь на паре!"
                )
                await self._redis_client.add_message_id(lesson_id, s.user_id, result.message_id)
            await self._send_queue.put(SendTask(
                coroutine_factory=send_and_store,
                user_id=student.user_id,
                chat_id=student.telegram_id,
            ))

    def cancel_lesson(self, lesson_id: int) -> None:
        for task in self._timers.pop(lesson_id, []):
            task.cancel()
```

### EventDispatcher wiring (additions)

```python
# Source: existing event_dispatcher.py [VERIFIED: codebase]
# In __init__, add to self._handlers:
"lesson.closed": lambda event: handle_lesson_closed(
    event,
    bot=self._bot,
    academic_client=self._academic_client,
    redis_client=self._redis_client,
    reminder_scheduler=self._reminder_scheduler,
),
"attendance.marked": lambda event: handle_attendance_marked(
    event,
    bot=self._bot,
    academic_client=self._academic_client,
    redis_client=self._redis_client,
),
# lesson.started lambda must also call reminder_scheduler.schedule_reminders(...)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| External task queues (Celery, RQ) for delayed tasks | Pure asyncio.create_task + sleep | N/A for this codebase | No new dependencies; simpler for single-process bot |
| APScheduler | asyncio native scheduling | N/A | APScheduler unnecessary for 2 fixed-delay tasks per lesson |

**Deprecated/outdated:**
- `asyncio.ensure_future`: replaced by `asyncio.create_task` since Python 3.7; use `create_task`. [ASSUMED]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `start_time`/`end_time` strings ("HH:MM") in `lesson.started` payload represent local Moscow time (UTC+3), not UTC | Architecture Patterns, Pitfall 1 | Reminders fire at wrong wall-clock times |
| A2 | `TelegramBadRequest` is importable from `aiogram.exceptions` in aiogram 3.15.0 | Code Examples | ImportError at runtime; use try/except ImportError fallback like send_queue.py does |
| A3 | `asyncio.create_task` on sleeping tasks does not block the event loop or the RabbitMQ consumer | Architecture Patterns | If wrong, need a separate thread or process; extremely unlikely given asyncio design |
| A4 | "near lesson end" for NOTIF-03 means ~5 minutes before `end_time` (matching CLAUDE.md "3 reminders: start, middle, end") | Phase Requirements | Wrong timing; planner should confirm offset |
| A5 | `attendance.marked` with `status=absent` (auto-scheduler) should NOT trigger reminder deletion | Common Pitfalls | Students lose their Redis entries before lesson.closed cleanup runs |

## Open Questions

1. **Time zone for start_time/end_time**
   - What we know: `start_time` = `"09:00"` string, Schedule Service stores as PostgreSQL `TIME` without timezone
   - What's unclear: Is the Docker container clock set to UTC or Moscow time? Does the bot need a `TZ=Europe/Moscow` env var?
   - Recommendation: Add `TZ=Europe/Moscow` to notification-bot Docker container env and use naive `datetime.now()` for delay calculation. Confirm with planner.

2. **NOTIF-03 timing offset**
   - What we know: CLAUDE.md says "3 reminders: start, middle, end of lesson". "Near end" is not precisely defined.
   - What's unclear: Is it exactly at `end_time`, or 5 minutes before?
   - Recommendation: Use 5 minutes before `end_time` as the "near end" trigger. This avoids sending a reminder after the lesson is already closed.

3. **lesson.started handler modification vs. new handler**
   - What we know: `handle_lesson_started` currently sends messages and stores ids; it does not schedule timers.
   - What's unclear: Should timer scheduling happen inside `handle_lesson_started` (requires injecting `ReminderScheduler`) or in a separate step in `EventDispatcher.dispatch`?
   - Recommendation: Inject `ReminderScheduler` into `handle_lesson_started` as an optional parameter, or call `reminder_scheduler.schedule_reminders(...)` directly in the `EventDispatcher` lambda after the handler returns. Either approach works; the lambda approach is cleaner (no handler signature change).

## Environment Availability

Step 2.6: SKIPPED (no new external dependencies — all tools and services used by this phase are already operational from Phase 24).

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | pytest + pytest-asyncio |
| Config file | `services/notification-bot/pytest.ini` (`asyncio_mode = auto`) |
| Quick run command | `cd services/notification-bot && python -m pytest tests/ -x -q` |
| Full suite command | `cd services/notification-bot && python -m pytest tests/ -v` |
| Estimated runtime | ~15 seconds |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| NOTIF-02 | Student receives midpoint reminder if not yet checked in; message_id appended to Redis | unit | `pytest tests/test_reminder_scheduler.py -x` | No — Wave 0 |
| NOTIF-03 | Student receives near-end reminder if not yet checked in; message_id appended to Redis | unit | `pytest tests/test_reminder_scheduler.py -x` | No — Wave 0 |
| NOTIF-04 | lesson.closed deletes all reminder messages and removes Redis keys | unit | `pytest tests/test_lesson_closed.py -x` | No — Wave 0 |
| NOTIF-04 | lesson.closed cancels pending timer tasks before they fire | unit | `pytest tests/test_lesson_closed.py -x` | No — Wave 0 |
| NOTIF-05 | attendance.marked (present) deletes student reminders and clears Redis key | unit | `pytest tests/test_attendance_marked.py -x` | No — Wave 0 |
| NOTIF-05 | attendance.marked (absent) does NOT delete reminders | unit | `pytest tests/test_attendance_marked.py -x` | No — Wave 0 |

### Sampling Rate

- **Per task commit:** `cd services/notification-bot && python -m pytest tests/ -x -q`
- **Per wave merge:** `cd services/notification-bot && python -m pytest tests/ -v`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `tests/test_lesson_closed.py` — covers NOTIF-04
- [ ] `tests/test_attendance_marked.py` — covers NOTIF-05
- [ ] `tests/test_reminder_scheduler.py` — covers NOTIF-02 and NOTIF-03

*(Existing test infrastructure (pytest.ini, conftest.py, fakeredis fixture) covers all other needs — no new framework install required)*

## Security Domain

Security enforcement is not applicable to this phase — no new authentication flows, no new endpoints, no new data inputs from external actors. The bot only responds to internal RabbitMQ events (trusted bus) and calls Telegram Bot API with a pre-authenticated token. The `lesson.closed` and `attendance.marked` events are published by trusted internal services (Schedule Service and Attendance Service respectively).

## Sources

### Primary (HIGH confidence)
- `services/notification-bot/bot/` — [VERIFIED: codebase] — full implementation of Phase 22-24
- `services/notification-bot/bot/services/redis_client.py` — [VERIFIED: codebase] — `ReminderRedisClient` API: `add_message_id`, `get_message_ids`, `delete_key`
- `services/notification-bot/bot/consumers/event_dispatcher.py` — [VERIFIED: codebase] — dispatch pattern, handler injection
- `services/notification-bot/bot/notifications/lesson_started.py` — [VERIFIED: codebase] — send-and-store pattern with closure binding
- `services/notification-bot/bot/services/send_queue.py` — [VERIFIED: codebase] — `SendTask`, `TelegramSendQueue.put`, TelegramBadRequest handling pattern
- `event-schemas/lesson.closed.json` — [VERIFIED: codebase] — payload: `lesson_id`, `group_id`, `subject_id` (optional)
- `event-schemas/attendance.marked.json` — [VERIFIED: codebase] — payload: `lesson_id`, `user_id`, `group_id`, `status` (enum: present/absent/excused/free_attendance)
- `event-schemas/lesson.started.json` — [VERIFIED: codebase] — payload includes `start_time` and `end_time` as `"time"` format strings

### Secondary (MEDIUM confidence)
- Python asyncio documentation — `asyncio.create_task`, `asyncio.sleep`, `asyncio.CancelledError` behavior [ASSUMED — stdlib knowledge]

### Tertiary (LOW confidence)
- aiogram 3.15.0 exception hierarchy — `TelegramBadRequest` import path [ASSUMED — inferred from `TelegramRetryAfter` pattern in send_queue.py]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — zero new dependencies, all verified in codebase
- Architecture: HIGH — patterns directly derived from existing Phase 24 code
- Pitfalls: HIGH — derived from code analysis and event schema inspection
- Time zone handling: LOW — requires Docker container environment confirmation

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (stable stack — aiogram, redis, asyncio stdlib)
