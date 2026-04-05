---
phase: 25-bot-reminder-lifecycle
plan: "01"
subsystem: notification-bot
tags: [python, asyncio, reminders, redis, telegram-bot]
dependency_graph:
  requires:
    - "24-bot-event-notifications"
  provides:
    - "ReminderScheduler: timed asyncio tasks for midpoint and near-end reminders"
  affects:
    - "services/notification-bot/bot/services/"
tech_stack:
  added: []
  patterns:
    - "asyncio.create_task + sleep for timed background tasks"
    - "In-memory dict[int, list[asyncio.Task]] for cancellable timer registry"
    - "Default-arg binding (s=student) to avoid late-binding closure bug"
key_files:
  created:
    - services/notification-bot/bot/services/reminder_scheduler.py
    - services/notification-bot/tests/test_reminder_scheduler.py
  modified: []
decisions:
  - "Use naive datetime.now() for delay calculation; TZ=Europe/Moscow env var required on Docker container to align with lesson time strings from Schedule Service"
  - "check get_message_ids before sending: empty list means student already checked in — skip silently"
  - "cancel_lesson is a no-op for unknown lesson_ids to support safe call from lesson.closed handler before any reminder scheduled"
metrics:
  duration_minutes: 3
  completed_date: "2026-04-05"
  tasks_completed: 1
  tasks_total: 1
  files_created: 2
  files_modified: 0
---

# Phase 25 Plan 01: ReminderScheduler — Timed Asyncio Task Engine Summary

**One-liner:** ReminderScheduler spawning two asyncio tasks per lesson (midpoint + 5-min-before-end) with Redis active-check skip and in-memory cancellation registry.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 (RED) | Failing tests for ReminderScheduler | a94655a | tests/test_reminder_scheduler.py |
| 1 (GREEN) | ReminderScheduler implementation | 60b6d54 | bot/services/reminder_scheduler.py |

## What Was Built

`reminder_scheduler.py` provides:

- `midpoint_delay_seconds(start_time, end_time) -> float` — calculates seconds from now to lesson midpoint; clamps to 0.0 if midpoint already passed
- `near_end_delay_seconds(end_time, offset_minutes=5) -> float` — calculates seconds from now to 5 min before end_time; clamps to 0.0
- `ReminderScheduler.schedule_reminders(lesson_id, group_id, start_time, end_time)` — spawns exactly 2 `asyncio.Task` objects and registers them in `self._timers[lesson_id]`
- `ReminderScheduler._send_reminder_after(delay, lesson_id, group_id, label)` — sleeps for delay, then fans out reminders only to students whose `get_message_ids` returns a non-empty list (i.e., still active, not yet checked in); queues `SendTask` via `send_queue.put`; stores new `message_id` via `redis_client.add_message_id`
- `ReminderScheduler.cancel_lesson(lesson_id)` — cancels all tasks for the lesson and removes from `_timers`; no-op for unknown lesson_ids

`test_reminder_scheduler.py` provides 8 unit tests:

1. `test_midpoint_delay_seconds_correct` — frozen now=09:00, start=09:00, end=10:30 → 2700s
2. `test_near_end_delay_seconds_correct` — frozen now=09:00, end=10:30, offset=5 → 5100s
3. `test_midpoint_delay_already_passed_returns_zero` — frozen now=10:00, midpoint=09:45 → 0.0
4. `test_schedule_reminders_creates_two_tasks` — `_timers[101]` has 2 `asyncio.Task` instances
5. `test_cancel_lesson_cancels_tasks` — both tasks cancelled, lesson_id removed from `_timers`
6. `test_send_reminder_after_skips_students_with_no_redis_key` — `send_queue.put` never called for empty-key student
7. `test_send_reminder_after_sends_and_stores_message_id` — coroutine_factory calls `bot.send_message` and `redis_client.add_message_id`
8. `test_cancel_lesson_nonexistent_is_noop` — `cancel_lesson(999)` does not raise

## Verification Results

```
cd services/notification-bot && python -m pytest tests/test_reminder_scheduler.py -x -v
8 passed in 1.90s

Full suite: 89 passed in 13.69s
```

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. `ReminderScheduler` sends the hardcoded reminder text "Напоминание: отметьтесь на паре!" which is intentional for this MVP phase. The text is generic and does not expose PII.

## Threat Flags

None. No new network endpoints, auth paths, or trust boundary crossings introduced. All interactions are with existing trusted internal components (Redis, Telegram Bot API via existing token).

## Self-Check

- [x] `services/notification-bot/bot/services/reminder_scheduler.py` exists
- [x] `services/notification-bot/tests/test_reminder_scheduler.py` exists
- [x] commit `a94655a` exists (RED: failing tests)
- [x] commit `60b6d54` exists (GREEN: implementation)
- [x] `from bot.services.reminder_scheduler import ReminderScheduler` succeeds
- [x] 8 tests pass deterministically
- [x] full suite 89 tests pass

## Self-Check: PASSED
