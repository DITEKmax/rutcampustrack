---
phase: 24-bot-event-notifications
plan: "02"
subsystem: notification-bot
tags: [python, aiogram, rabbitmq, notifications, homework, headman]
dependency_graph:
  requires: ["24-01"]
  provides: ["homework-notifications", "headman-alert-notifications", "full-event-pipeline"]
  affects: ["event_dispatcher.py", "event_consumer.py", "__main__.py"]
tech_stack:
  added: []
  patterns: ["fan-out to group members", "headman-only routing", "dependency injection in __main__"]
key_files:
  created:
    - services/notification-bot/bot/notifications/homework.py
    - services/notification-bot/bot/notifications/headman_alerts.py
    - services/notification-bot/tests/test_homework_notifications.py
    - services/notification-bot/tests/test_headman_alerts.py
  modified:
    - services/notification-bot/bot/consumers/event_dispatcher.py
    - services/notification-bot/bot/consumers/event_consumer.py
    - services/notification-bot/bot/__main__.py
    - services/notification-bot/tests/test_consumer_watchdog.py
decisions:
  - "homework.updated handler shares handle_homework function with homework.published — event_type branch inside the function"
  - "student_name resolution: payload.get > member lookup by user_id > fallback string, no extra gRPC call needed"
  - "Handler exceptions in event_consumer.py caught at consumer level in addition to dispatcher level — defense in depth"
metrics:
  duration: "~25 minutes"
  completed: "2026-04-05"
  tasks_completed: 2
  files_changed: 8
---

# Phase 24 Plan 02: Homework + Headman Alerts + Full Pipeline Wiring Summary

Homework and headman alert notification handlers implemented with full test coverage, and the complete event notification pipeline wired from RabbitMQ through EventDispatcher to Telegram.

## What Was Built

### Task 1: Homework + Headman Alert Handlers

**`bot/notifications/homework.py`** — `handle_homework` function handles both `homework.published` and `homework.updated` events:
- `homework.published`: resolves subject name via gRPC, fans out to all group students with `telegram_id`, text includes subject name + title
- `homework.updated`: fans out to all group students, text indicates update + title
- Students without `telegram_id` are skipped
- Subject resolution failure falls back to "Предмет"

**`bot/notifications/headman_alerts.py`** — `handle_headman_alert` function handles `excuse.requested` and `late_checkin.requested` events:
- Filters members to headmen only (`is_headman=True and telegram_id`)
- Student name resolution: payload `student_name` field > member lookup by `user_id` > fallback `Студент #{user_id}`
- `excuse.requested`: includes student name and excuse type
- `late_checkin.requested`: includes student name, optionally lesson date
- Logs warning and returns early if no headman with telegram_id exists (T-24-06 compliant)

### Task 2: Full Pipeline Wiring

**`bot/consumers/event_dispatcher.py`** — added 4 new handler registrations: `homework.published`, `homework.updated`, `excuse.requested`, `late_checkin.requested`

**`bot/consumers/event_consumer.py`** — updated `start_consumer(rabbitmq_url, dispatcher=None)`:
- Calls `await dispatcher.dispatch(body)` when dispatcher is provided
- Removed placeholder comment "Phase 22+ will add actual event dispatching"
- Added broad `except Exception` to ensure message is always acked (T-24-08 compliant)

**`bot/__main__.py`** — full dependency injection:
- Creates `TelegramSendQueue`, calls `send_queue.start()`
- Creates `ReminderRedisClient` with config values
- Creates `EventDispatcher` with all 6 handlers registered
- Passes `dispatcher` to `run_with_watchdog` → `start_consumer`
- Cleanup: `await send_queue.shutdown()` and `await redis_client.close()` in `finally` block

## Test Results

```
81 passed in 12.78s
```

New tests added:
- `tests/test_homework_notifications.py`: 5 tests covering fan-out count, text content (subject + title), homework.updated text, subject fallback on gRPC error
- `tests/test_headman_alerts.py`: 6 tests covering headman-only routing, excuse text content, late_checkin with date, student_name from payload, student_name fallback from members, no headman with telegram skips send

All pre-existing 70 tests continue to pass.

## Commits

- `cd2a2de` feat(24-02): homework and headman alert notification handlers
- `fa09461` feat(24-02): wire EventDispatcher into consumer and __main__.py

## Deviations from Plan

None — plan executed exactly as written.

## Threat Mitigations Applied

| Threat | Mitigation |
|--------|-----------|
| T-24-06 (Info Disclosure) | headman_alerts.py filters `is_headman=True` before sending — non-headman students never see excuse/late_checkin messages |
| T-24-07 (Tampering) | Required fields `group_id`, `title`, `user_id` validated with `KeyError` catch before processing |
| T-24-08 (DoS — ack timing) | `except Exception` in consumer ensures message is acked even on handler failure |

## Known Stubs

None — all handlers are fully wired with real data from gRPC and send through TelegramSendQueue.

## Self-Check: PASSED

All created files verified present. Both commits verified in git log.
