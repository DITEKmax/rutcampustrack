---
phase: 24-bot-event-notifications
plan: "01"
subsystem: notification-bot
tags: [python, aiogram, rabbitmq, telegram, redis, grpc, tdd]
dependency_graph:
  requires: []
  provides:
    - EventDispatcher (event routing pipeline)
    - handle_lesson_started (NOTIF-01)
    - handle_lesson_cancelled (NOTIF-06)
  affects:
    - services/notification-bot/bot/consumers/event_consumer.py (future integration point)
tech_stack:
  added: []
  patterns:
    - TDD (RED-GREEN per task)
    - Default-arg lambda binding for async closures (s=student avoids late-binding bug)
    - Threat T-24-02 mitigated: KeyError guard on required payload fields
    - Threat T-24-03 mitigated: telegram_id > 0 filter
key_files:
  created:
    - services/notification-bot/bot/consumers/event_dispatcher.py
    - services/notification-bot/bot/notifications/__init__.py
    - services/notification-bot/bot/notifications/lesson_started.py
    - services/notification-bot/bot/notifications/lesson_cancelled.py
    - services/notification-bot/tests/test_event_dispatcher.py
    - services/notification-bot/tests/test_lesson_started.py
    - services/notification-bot/tests/test_lesson_cancelled.py
  modified:
    - services/notification-bot/bot/config.py
decisions:
  - "Handler exceptions caught inside EventDispatcher.dispatch() for RabbitMQ ack safety"
  - "Lambda default-arg binding (s=student) used in lesson_started closures to avoid Python late-binding bug"
  - "T-24-02: required payload fields validated with KeyError guard; handler returns early with warning log"
  - "Notification handlers import at __init__ time inside EventDispatcher constructor to avoid circular imports"
metrics:
  duration_minutes: 25
  completed_date: "2026-04-05"
  tasks_completed: 2
  tasks_total: 2
  files_created: 7
  files_modified: 1
  tests_added: 20
  tests_total: 70
requirements_satisfied:
  - NOTIF-01
  - NOTIF-06
---

# Phase 24 Plan 01: EventDispatcher + lesson.started / lesson.cancelled Handlers Summary

**One-liner:** RabbitMQ event routing pipeline with inline WebApp check-in button (lesson.started) and plain-text cancellation fan-out (lesson.cancelled) for Telegram notification bot.

## What Was Built

### Config Update
Added `mini_app_url: str = "https://t.me/RutTrackBot/checkin"` to `Settings` in `bot/config.py`. Configurable via `.env` — used by `lesson_started` handler to build the check-in WebAppInfo URL.

### EventDispatcher (`bot/consumers/event_dispatcher.py`)
- Routes incoming event dicts by `event_type` string to registered async handlers
- Handlers for `lesson.started` and `lesson.cancelled` registered
- Placeholder comments for Plan 02 handlers (`homework.published`, `homework.updated`, `excuse.requested`, `late_checkin.requested`)
- Exception safety: all handler exceptions caught with `logger.exception` — never re-raised, so RabbitMQ messages are always acknowledged
- Unknown event types logged at DEBUG level and silently ignored

### lesson.started Handler (`bot/notifications/lesson_started.py`)
- Resolves subject name via `academic_client.get_subjects_by_ids([subject_id])`, fallback to `"Пара"` on any exception or empty response
- Builds message text: subject name, room, time range
- Builds `InlineKeyboardMarkup` with `WebAppInfo` button pointing to `{mini_app_url}/checkin?lesson_id={lesson_id}`
- Fetches group members via `academic_client.get_group_members(group_id)`
- Skips students where `telegram_id == 0` (unlinked accounts) — T-24-03
- Uses default-arg closure `async def send_and_store(s=student)` to avoid Python late-binding bug
- Enqueues `SendTask` per student via `send_queue.put()` — T-24-04 (rate limiting handled by TelegramSendQueue)
- After bot.send_message returns, calls `redis_client.add_message_id(lesson_id, user_id, message_id)` to enable later cleanup
- T-24-02: validates required payload fields (`lesson_id`, `group_id`, `subject_id`) — logs warning and returns early if missing

### lesson.cancelled Handler (`bot/notifications/lesson_cancelled.py`)
- Same subject resolution and group fan-out pattern as lesson_started
- Sends plain-text message: `"Отмена пары\n\n{subject_name}\nДата: {date}"`
- Appends `"\nПричина: {cancel_reason}"` only when `cancel_reason` is truthy
- T-24-02: validates required payload fields (`group_id`, `subject_id`)

## Test Results

```
70 passed in 12.70s
```

| Test file | Tests | Coverage |
|-----------|-------|----------|
| test_event_dispatcher.py | 7 | routing, unknown type, exception safety, config default |
| test_lesson_started.py | 6 | fan-out, WebAppInfo keyboard, Redis storage, subject resolution, gRPC fallback, missing fields |
| test_lesson_cancelled.py | 7 | fan-out, text content, cancel_reason (present/absent), subject resolution, gRPC fallback, missing fields |
| (existing) | 50 | unchanged, all pass |

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

### Additional Hardening Applied (Rule 2)

**T-24-02 mitigation applied proactively:** The threat register flagged payload field tampering as `mitigate`. Both handlers validate required fields with a `try/except KeyError` guard and return early with a `logger.warning` rather than letting `KeyError` propagate up to the dispatcher (where it would be caught but generate a noisier `logger.exception`). This is cleaner and matches the mitigation disposition.

## Known Stubs

None. Both handlers are fully wired to real dependencies (academic gRPC client, send_queue, redis_client). EventDispatcher constructor imports handlers at runtime to avoid circular imports.

## Self-Check: PASSED
