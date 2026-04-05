# Phase 24: Bot Event Notifications - Research

**Researched:** 2026-04-05
**Domain:** Python Aiogram 3 event-driven Telegram notifications, RabbitMQ consumer dispatch, inline keyboard with WebAppInfo
**Confidence:** HIGH

## Summary

Phase 24 adds event notification handlers to the existing notification-bot Python service. The bot already has a fully working RabbitMQ consumer (`event_consumer.py`), a throttled send queue (`send_queue.py`), a Redis reminder client (`redis_client.py`), and an Academic gRPC client (`academic_client.py`) with `get_group_members()` that returns `StudentInfo` objects including `telegram_id` and `is_headman` fields. The consumer currently only logs events and needs to be upgraded to dispatch them to handler functions.

The core work is: (1) replace the placeholder event consumer with a dispatcher that routes events by `event_type`, (2) implement 5 event handlers (lesson.started, lesson.cancelled, homework.published, homework.updated, excuse.requested / late_checkin.requested), (3) for lesson.started specifically, send messages with an inline keyboard button using `WebAppInfo` to open the Mini App check-in flow, and store `message_id` in Redis via the existing `ReminderRedisClient`.

**Primary recommendation:** Create an `EventDispatcher` class that maps event_type strings to async handler coroutines, inject existing infrastructure clients (academic_client, send_queue, redis_client, bot), and keep each handler as a standalone async function for testability.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| NOTIF-01 | Student receives Telegram message with inline check-in button when lesson starts | Aiogram `InlineKeyboardButton(web_app=WebAppInfo(url=...))` + Redis RPUSH for message_id storage via existing `ReminderRedisClient` |
| NOTIF-06 | Student receives Telegram notification when lesson is cancelled | Plain text via existing `TelegramSendQueue`, group member fan-out via `AcademicGrpcClient.get_group_members()` |
| NOTIF-07 | Student receives Telegram notification when homework is published or updated | Plain text via send queue, subject name resolution via `AcademicGrpcClient.get_subjects_by_ids()` |
| NOTIF-08 | Headman receives Telegram notification when student requests excuse | Filter `is_headman=True` from group members, send to headman's `telegram_id` |
| NOTIF-09 | Headman receives Telegram notification when student requests late check-in | Same pattern as NOTIF-08, different message text |
</phase_requirements>

## Standard Stack

### Core (already installed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| aiogram | 3.15.0 | Telegram Bot API — `InlineKeyboardMarkup`, `InlineKeyboardButton`, `WebAppInfo`, `Bot.send_message` | Already in requirements.txt [VERIFIED: codebase] |
| aio-pika | 9.5.3 | RabbitMQ async consumer | Already in requirements.txt [VERIFIED: codebase] |
| redis[hiredis] | 5.2.1 | Redis async client for reminder message storage | Already in requirements.txt [VERIFIED: codebase] |
| grpcio | 1.73.0 | gRPC async client for Academic Service | Already in requirements.txt [VERIFIED: codebase] |
| pydantic-settings | 2.6.1 | Config management | Already in requirements.txt [VERIFIED: codebase] |

### Supporting (already installed for tests)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| pytest | (existing) | Test runner with `asyncio_mode = auto` | All tests [VERIFIED: pytest.ini] |
| fakeredis | (existing) | In-process Redis mock | Redis interaction tests [VERIFIED: conftest.py] |

### No New Dependencies

This phase requires **zero new pip packages**. All needed aiogram types (`InlineKeyboardMarkup`, `InlineKeyboardButton`, `WebAppInfo`) are part of aiogram 3.15.0. [VERIFIED: aiogram docs]

## Architecture Patterns

### Recommended Project Structure

```
bot/
  consumers/
    event_consumer.py      # Existing — modify to use EventDispatcher
    event_dispatcher.py    # NEW — routes events to handlers
  handlers/
    __init__.py            # Update to export notification_router (optional)
    start.py               # Existing
    login.py               # Existing
    status.py              # Existing
  notifications/           # NEW — event notification handlers
    __init__.py
    lesson_started.py      # NOTIF-01: inline button + Redis store
    lesson_cancelled.py    # NOTIF-06: plain text fan-out
    homework.py            # NOTIF-07: published + updated
    headman_alerts.py      # NOTIF-08, NOTIF-09: excuse + late check-in
tests/
    test_event_dispatcher.py      # NEW
    test_lesson_started.py        # NEW
    test_lesson_cancelled.py      # NEW
    test_homework_notifications.py # NEW
    test_headman_alerts.py        # NEW
```

### Pattern 1: Event Dispatcher (Strategy Pattern)

**What:** A mapping of `event_type` string to async handler function. The consumer calls `dispatcher.dispatch(event)` instead of inline processing.
**When to use:** When the consumer receives multiple event types that need different handling logic.
**Example:**

```python
# Source: project convention extrapolated from notification-web EventConsumer.java
class EventDispatcher:
    def __init__(self, bot: Bot, academic_client, send_queue, redis_client, config):
        self._handlers: dict[str, Callable] = {
            "lesson.started": self._handle_lesson_started,
            "lesson.cancelled": self._handle_lesson_cancelled,
            "homework.published": self._handle_homework,
            "homework.updated": self._handle_homework,
            "excuse.requested": self._handle_headman_alert,
            "late_checkin.requested": self._handle_headman_alert,
        }
        # ... store dependencies

    async def dispatch(self, event: dict) -> None:
        event_type = event.get("event_type")
        handler = self._handlers.get(event_type)
        if handler:
            await handler(event)
        else:
            logger.debug("Unhandled event type: %s", event_type)
```

[ASSUMED — design pattern; specific to this codebase]

### Pattern 2: Group Fan-Out via gRPC

**What:** For group-targeted events, call `academic_client.get_group_members(group_id)` to get all students with telegram_ids, then enqueue a send task per student.
**When to use:** lesson.started, lesson.cancelled, homework events.
**Example:**

```python
# Source: existing AcademicGrpcClient [VERIFIED: codebase]
members = await academic_client.get_group_members(group_id)
for student in members:
    if student.telegram_id:
        await send_queue.put(SendTask(
            coroutine_factory=lambda s=student: bot.send_message(
                chat_id=s.telegram_id,
                text=message_text,
            ),
            user_id=student.user_id,
            chat_id=student.telegram_id,
        ))
```

### Pattern 3: Inline Button with WebAppInfo

**What:** For lesson.started, the message includes an inline keyboard button that opens the Telegram Mini App check-in flow.
**When to use:** NOTIF-01 only.
**Example:**

```python
# Source: aiogram 3 docs — InlineKeyboardButton with web_app
# [CITED: https://docs.aiogram.dev/en/latest/api/types/inline_keyboard_button.html]
from aiogram.types import InlineKeyboardMarkup, InlineKeyboardButton, WebAppInfo

keyboard = InlineKeyboardMarkup(inline_keyboard=[
    [InlineKeyboardButton(
        text="Otmetitsya",  # Russian: "Отметиться"
        web_app=WebAppInfo(url=f"{mini_app_url}/checkin?lesson_id={lesson_id}")
    )]
])

result = await bot.send_message(
    chat_id=telegram_id,
    text=message_text,
    reply_markup=keyboard,
)
# result.message_id is stored in Redis
await redis_client.add_message_id(lesson_id, user_id, result.message_id)
```

### Pattern 4: Headman-Only Notification

**What:** For excuse.requested and late_checkin.requested, find the headman in the group members list and send only to them.
**When to use:** NOTIF-08, NOTIF-09.
**Example:**

```python
# Source: StudentInfo proto has is_headman field [VERIFIED: academic_pb2.py]
members = await academic_client.get_group_members(group_id)
headmen = [m for m in members if m.is_headman and m.telegram_id]
for headman in headmen:
    await send_queue.put(SendTask(
        coroutine_factory=lambda h=headman: bot.send_message(
            chat_id=h.telegram_id,
            text=f"Студент {student_name} запросил у.п.",
        ),
        user_id=headman.user_id,
        chat_id=headman.telegram_id,
    ))
```

### Anti-Patterns to Avoid

- **Blocking gRPC in consumer callback:** The consumer runs in asyncio; all gRPC calls use `grpc.aio` which is already the case. Never use synchronous gRPC stubs.
- **Sending messages directly (bypassing send_queue):** All outgoing Telegram messages MUST go through `TelegramSendQueue` to respect rate limits (30 msg/sec). [VERIFIED: Phase 22 decision in STATE.md]
- **Catching all exceptions silently in handlers:** Log errors with `logger.exception()`, but do NOT re-raise — a failed notification should not nack the RabbitMQ message and cause infinite reprocessing. The message should be acked even if some sends fail.
- **Lambda closure bug:** When creating `coroutine_factory` lambdas in a loop, always bind loop variable with default argument (`lambda s=student: ...`) to avoid all lambdas capturing the last value.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Rate limiting | Custom counter | `TelegramSendQueue` (existing) | Token bucket with retry already implemented [VERIFIED: send_queue.py] |
| Group member lookup | Direct DB query | `AcademicGrpcClient.get_group_members()` with 5min cache | Already built and cached [VERIFIED: academic_client.py] |
| Redis message storage | Custom Redis code | `ReminderRedisClient` (existing) | RPUSH/LRANGE/DEL + TTL already implemented [VERIFIED: redis_client.py] |
| Inline keyboard builder | Manual dict construction | `aiogram.types.InlineKeyboardMarkup` + `InlineKeyboardButton` | Aiogram validates structure [CITED: aiogram docs] |
| Subject name resolution | Hardcoded IDs | `AcademicGrpcClient.get_subjects_by_ids()` | Already used in /status handler [VERIFIED: status.py] |

## Common Pitfalls

### Pitfall 1: Lambda Closure in Loop

**What goes wrong:** All send tasks capture the same `student` variable (last iteration value).
**Why it happens:** Python closures capture variables by reference, not value.
**How to avoid:** Use default argument: `lambda s=student: bot.send_message(chat_id=s.telegram_id, ...)`.
**Warning signs:** All messages go to the same user.

### Pitfall 2: Mini App URL Not Yet Available

**What goes wrong:** The Mini App (Phase 8) is not yet built, so the URL for `WebAppInfo` doesn't exist.
**Why it happens:** Phase 24 ships before Phase 8 (frontends).
**How to avoid:** Add `mini_app_url` to `Settings` in `config.py` with a placeholder default. The inline button will work structurally but point to a configurable URL. Tests mock this value.
**Warning signs:** Hardcoded URL in handler code.

### Pitfall 3: Bot.send_message Returns Message Object

**What goes wrong:** Forgetting to capture `result.message_id` from `bot.send_message()` return value for Redis storage.
**Why it happens:** Most send_queue tasks use fire-and-forget pattern.
**How to avoid:** For lesson.started specifically, the `coroutine_factory` must return the result so `message_id` can be extracted and stored in Redis. This requires a different pattern than plain fire-and-forget — the send + Redis store should be a single coroutine.
**Warning signs:** Redis keys are empty after lesson.started notifications.

### Pitfall 4: Students Without Telegram ID

**What goes wrong:** `bot.send_message(chat_id=0)` fails or sends to wrong user.
**Why it happens:** Not all students have linked their Telegram account via /start.
**How to avoid:** Filter `student.telegram_id > 0` (or `!= 0`) before creating send tasks. Proto int64 defaults to 0 when unset. [VERIFIED: proto default behavior]
**Warning signs:** Telegram API errors for chat_id=0.

### Pitfall 5: Consumer Ack Timing

**What goes wrong:** If the consumer nacks a message because one notification failed, ALL notifications for that event will be retried, causing duplicate messages.
**Why it happens:** The current consumer uses `async with message.process()` which auto-acks on success and nacks on exception.
**How to avoid:** Catch all exceptions inside the dispatch handler — never let them propagate to the consumer's message processing context. A partially failed fan-out (e.g., 3 of 30 sends failed) should still ack the RabbitMQ message.
**Warning signs:** Duplicate Telegram messages after partial failures.

### Pitfall 6: Subject Name Resolution for lesson.cancelled

**What goes wrong:** The lesson.cancelled event has `subject_id` but not `subject_name`. The message text needs the human-readable subject name.
**Why it happens:** Events carry IDs, not denormalized names.
**How to avoid:** Call `academic_client.get_subjects_by_ids([subject_id])` to resolve the name. Same pattern already used in `/status` handler. [VERIFIED: status.py line 60-64]
**Warning signs:** Messages showing "Subject #42" instead of "Математический анализ".

## Code Examples

### Example 1: Modified event_consumer.py with dispatch

```python
# Modified consumer loop — delegates to EventDispatcher
async with queue.iterator() as queue_iter:
    async for message in queue_iter:
        async with message.process():
            try:
                body = json.loads(message.body)
                event_type = body.get("event_type", "unknown")
                logger.info("[notification-bot] Received event: %s", event_type)
                await dispatcher.dispatch(body)
            except json.JSONDecodeError:
                logger.error("Failed to decode message body: %s", message.body[:200])
            except Exception:
                # Ack message even on handler failure — prevent infinite requeue
                logger.exception("Handler failed for event, acking anyway")
```

### Example 2: lesson.started handler with inline button + Redis

```python
# Source: aiogram types [CITED: docs.aiogram.dev]
async def handle_lesson_started(
    event: dict, bot: Bot, academic_client, send_queue, redis_client, config
) -> None:
    payload = event["payload"]
    lesson_id = payload["lesson_id"]
    group_id = payload["group_id"]
    subject_id = payload["subject_id"]
    
    # Resolve subject name
    subject_name = "Пара"
    try:
        resp = await academic_client.get_subjects_by_ids([subject_id])
        if resp.subjects:
            subject_name = resp.subjects[0].subject_name
    except Exception:
        logger.warning("Failed to resolve subject %d", subject_id)
    
    room = payload.get("room", "")
    time_range = f'{payload.get("start_time", "")} - {payload.get("end_time", "")}'
    
    text = f"Пара началась!\n\n{subject_name}\nАудитория: {room}\nВремя: {time_range}"
    
    keyboard = InlineKeyboardMarkup(inline_keyboard=[
        [InlineKeyboardButton(
            text="Отметиться",
            web_app=WebAppInfo(url=f"{config.mini_app_url}/checkin?lesson_id={lesson_id}")
        )]
    ])
    
    members = await academic_client.get_group_members(group_id)
    for student in members:
        if not student.telegram_id:
            continue
        
        async def send_and_store(s=student):
            result = await bot.send_message(
                chat_id=s.telegram_id,
                text=text,
                reply_markup=keyboard,
            )
            await redis_client.add_message_id(lesson_id, s.user_id, result.message_id)
        
        await send_queue.put(SendTask(
            coroutine_factory=send_and_store,
            user_id=student.user_id,
            chat_id=student.telegram_id,
        ))
```

### Example 3: Test pattern for event handlers

```python
# Source: existing test patterns [VERIFIED: test_start_handler.py]
from unittest.mock import AsyncMock, MagicMock

@pytest.mark.asyncio
async def test_lesson_started_sends_to_all_with_telegram():
    bot = MagicMock()
    sent_result = MagicMock()
    sent_result.message_id = 999
    bot.send_message = AsyncMock(return_value=sent_result)
    
    academic_client = MagicMock()
    student1 = MagicMock(user_id=1, telegram_id=111, is_headman=False)
    student2 = MagicMock(user_id=2, telegram_id=0, is_headman=False)  # no TG
    student3 = MagicMock(user_id=3, telegram_id=333, is_headman=True)
    academic_client.get_group_members = AsyncMock(return_value=[student1, student2, student3])
    academic_client.get_subjects_by_ids = AsyncMock(return_value=MagicMock(
        subjects=[MagicMock(subject_name="Математика")]
    ))
    
    redis_client = MagicMock()
    redis_client.add_message_id = AsyncMock()
    
    # ... invoke handler, verify:
    # - bot.send_message called 2 times (student1 + student3, NOT student2)
    # - redis_client.add_message_id called 2 times
    # - InlineKeyboardMarkup present in reply_markup
```

## Event Schema Summary

All event payloads used in this phase. [VERIFIED: event-schemas/ directory]

| Event Type | Key Payload Fields | Target | Message Type |
|------------|-------------------|--------|--------------|
| `lesson.started` | lesson_id, group_id, subject_id, start_time, end_time, room | All students in group | Inline button (WebAppInfo) + Redis store |
| `lesson.cancelled` | lesson_id, group_id, subject_id, date, cancel_reason? | All students in group | Plain text |
| `homework.published` | homework_id, group_id, subject_id, title, has_link? | All students in group | Plain text |
| `homework.updated` | homework_id, group_id, title | All students in group | Plain text |
| `excuse.requested` | user_id, group_id, excuse_type, ticket_id?, lesson_ids? | Headman only | Plain text |
| `late_checkin.requested` | user_id, group_id, lesson_id, student_name?, lesson_date? | Headman only | Plain text |

## Config Changes Required

Add to `bot/config.py` Settings:

```python
# Mini App URL for inline check-in button (Phase 8 will provide actual URL)
mini_app_url: str = "https://t.me/RutTrackBot/checkin"
```

[ASSUMED — exact Mini App URL format depends on Phase 8 deployment. Placeholder is fine for now.]

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | pytest + pytest-asyncio |
| Config file | `services/notification-bot/pytest.ini` |
| Quick run command | `cd services/notification-bot && python -m pytest tests/ -x -q` |
| Full suite command | `cd services/notification-bot && python -m pytest tests/ -v` |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| NOTIF-01 | lesson.started sends inline button + stores message_id in Redis | unit | `pytest tests/test_lesson_started.py -x` | No — Wave 0 |
| NOTIF-06 | lesson.cancelled sends plain notification to group | unit | `pytest tests/test_lesson_cancelled.py -x` | No — Wave 0 |
| NOTIF-07 | homework.published/updated sends notification to group | unit | `pytest tests/test_homework_notifications.py -x` | No — Wave 0 |
| NOTIF-08 | excuse.requested sends notification to headman | unit | `pytest tests/test_headman_alerts.py -x` | No — Wave 0 |
| NOTIF-09 | late_checkin.requested sends notification to headman | unit | `pytest tests/test_headman_alerts.py -x` | No — Wave 0 |
| DISPATCH | EventDispatcher routes events to correct handlers | unit | `pytest tests/test_event_dispatcher.py -x` | No — Wave 0 |

### Sampling Rate
- **Per task commit:** `cd services/notification-bot && python -m pytest tests/ -x -q`
- **Per wave merge:** `cd services/notification-bot && python -m pytest tests/ -v`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `tests/test_event_dispatcher.py` — covers event routing
- [ ] `tests/test_lesson_started.py` — covers NOTIF-01
- [ ] `tests/test_lesson_cancelled.py` — covers NOTIF-06
- [ ] `tests/test_homework_notifications.py` — covers NOTIF-07
- [ ] `tests/test_headman_alerts.py` — covers NOTIF-08, NOTIF-09

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Mini App URL format is `https://t.me/BotName/checkin?lesson_id=X` or similar configurable URL | Config Changes | LOW — URL is configurable via Settings, easy to change |
| A2 | EventDispatcher as a class with handler map is the preferred pattern | Architecture Patterns | LOW — alternative is if/elif chain, both work |
| A3 | homework.published and homework.updated can share same handler (different text) | Architecture Patterns | LOW — event schemas are very similar |

## Open Questions

1. **Mini App URL format**
   - What we know: The Mini App (Phase 8) will be a React app for check-in flow
   - What's unclear: Exact URL structure and how lesson_id is passed
   - Recommendation: Make it a config parameter (`mini_app_url`). Use placeholder. Phase 8 will finalize.

2. **Student name in headman alerts**
   - What we know: `excuse.requested` has `user_id` but `student_name` is optional in the schema. `late_checkin.requested` has optional `student_name`.
   - What's unclear: Whether the producing services always include `student_name`
   - Recommendation: If `student_name` is absent, resolve via `academic_client.get_user_by_id()` or use fallback text. But since the existing proto has `GetUserById` RPC, this is straightforward.

## Sources

### Primary (HIGH confidence)
- Codebase files: `event_consumer.py`, `send_queue.py`, `redis_client.py`, `academic_client.py`, `config.py`, `__main__.py` — all verified by direct read
- Event schemas: `event-schemas/*.json` — verified by direct read
- Proto definition: `academic_pb2.py` — `StudentInfo` has `user_id`, `display_name`, `is_headman`, `telegram_id` fields

### Secondary (MEDIUM confidence)
- [Aiogram 3.26.0 InlineKeyboardButton docs](https://docs.aiogram.dev/en/latest/api/types/inline_keyboard_button.html) — `web_app` parameter of type `WebAppInfo`
- [Aiogram WebAppInfo](https://docs.aiogram.dev/en/latest/api/types/web_app_info.html) — takes `url` string

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — zero new dependencies, all verified in codebase
- Architecture: HIGH — follows existing patterns from Phase 22-23, mirrors notification-web EventConsumer
- Pitfalls: HIGH — derived from actual codebase analysis and known Python/asyncio patterns
- Event schemas: HIGH — verified from `event-schemas/` directory

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (stable — no external dependency changes expected)
