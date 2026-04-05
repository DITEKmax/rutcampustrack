---
phase: 25-bot-reminder-lifecycle
plan: "02"
subsystem: notification-bot
tags: [python, asyncio, reminders, redis, telegram-bot, cleanup, event-dispatcher]
dependency_graph:
  requires:
    - "25-01: ReminderScheduler"
  provides:
    - "lesson_closed handler: deletes all reminder messages and clears Redis on lesson close"
    - "attendance_marked handler: deletes reminders for status=present students"
    - "EventDispatcher: 8-event routing with ReminderScheduler injection"
    - "Full reminder lifecycle: schedule -> send -> cleanup on close or check-in"
  affects:
    - "services/notification-bot/bot/notifications/"
    - "services/notification-bot/bot/consumers/"
    - "services/notification-bot/bot/__main__.py"
tech_stack:
  added: []
  patterns:
    - "Guard pattern: status != 'present' early return prevents absent/excused/free_attendance from triggering cleanup"
    - "cancel_lesson called before delete_message loop to prevent stale timer reminders from firing"
    - "TelegramBadRequest silently caught in both handlers for idempotent delete"
    - "Optional reminder_scheduler parameter in EventDispatcher for backward compatibility"
    - "Lazy import of lesson_closed and attendance_marked inside __init__ to avoid circular imports"
key_files:
  created:
    - services/notification-bot/bot/notifications/lesson_closed.py
    - services/notification-bot/bot/notifications/attendance_marked.py
    - services/notification-bot/tests/test_lesson_closed.py
    - services/notification-bot/tests/test_attendance_marked.py
  modified:
    - services/notification-bot/bot/consumers/event_dispatcher.py
    - services/notification-bot/bot/__main__.py
    - services/notification-bot/tests/test_event_dispatcher.py
decisions:
  - "reminder_scheduler is optional (default None) in EventDispatcher to preserve backward compatibility with existing tests that don't inject it"
  - "cancel_lesson is called synchronously before the async delete loop to ensure timer cancellation is immediate and ordering is guaranteed"
  - "attendance.marked guard checks status at the very top of payload processing — not after field validation — to short-circuit on absent/excused without unnecessary field reads"
  - "lesson.closed skips students with telegram_id=0 and those with empty message_ids independently to handle both 'never received reminder' and 'already cleaned by NOTIF-05' cases"
metrics:
  duration_minutes: 4
  completed_date: "2026-04-05"
  tasks_completed: 2
  tasks_total: 2
  files_created: 4
  files_modified: 3
---

# Phase 25 Plan 02: Cleanup Handlers and Full Lifecycle Wiring Summary

**One-liner:** lesson.closed and attendance.marked cleanup handlers plus EventDispatcher wiring completing the full reminder lifecycle: schedule on start, delete on close or check-in.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 (TDD) | lesson_closed and attendance_marked handlers with tests | 5df6c27 | bot/notifications/lesson_closed.py, bot/notifications/attendance_marked.py, tests/test_lesson_closed.py, tests/test_attendance_marked.py |
| 2 | Wire ReminderScheduler and new handlers into EventDispatcher and __main__.py | 5d884fd | bot/consumers/event_dispatcher.py, bot/__main__.py, tests/test_event_dispatcher.py |

## What Was Built

### lesson_closed.py (NOTIF-04)

`handle_lesson_closed(event, bot, academic_client, redis_client, reminder_scheduler)`:

1. Validates `lesson_id` and `group_id` — returns early if missing
2. Calls `reminder_scheduler.cancel_lesson(lesson_id)` synchronously before any async work
3. Fetches group members via `academic_client.get_group_members(group_id)`
4. For each student with `telegram_id != 0` and non-empty `get_message_ids`:
   - Calls `bot.delete_message(chat_id, message_id)` for each stored message_id
   - Catches `TelegramBadRequest` silently (idempotent)
   - Calls `redis_client.delete_key(lesson_id, user_id)` after all deletions

### attendance_marked.py (NOTIF-05)

`handle_attendance_marked(event, bot, academic_client, redis_client)`:

1. Guards `if status != "present": return` at the very top — absent/excused/free_attendance are no-ops
2. Validates `lesson_id`, `user_id`, `group_id` — returns early if missing
3. Finds the specific student in group members by `user_id`
4. Deletes all stored `message_ids` for that student, catches `TelegramBadRequest` silently
5. Calls `redis_client.delete_key(lesson_id, user_id)`

### EventDispatcher updates

- Added `reminder_scheduler=None` parameter (optional for backward compatibility)
- Registered 2 new event types: `"lesson.closed"` and `"attendance.marked"`
- Replaced `"lesson.started"` lambda with `_handle_lesson_started_with_scheduling` method
  - Calls `handle_lesson_started` first (sends initial check-in button)
  - Then calls `reminder_scheduler.schedule_reminders(lesson_id, group_id, start_time, end_time)` if scheduler is set
- Total handler count: 8 event types

### __main__.py updates

- Added `from bot.services.reminder_scheduler import ReminderScheduler`
- Instantiated `ReminderScheduler(bot, academic_client, send_queue, redis_client)` before EventDispatcher
- Passes `reminder_scheduler=reminder_scheduler` to EventDispatcher

### Test additions

- `test_lesson_closed.py`: 6 tests covering all behavior branches
- `test_attendance_marked.py`: 7 tests covering all status/edge cases
- `test_event_dispatcher.py`: 6 new tests for 8-event routing, scheduler injection, schedule_reminders call

## Verification Results

```
cd services/notification-bot && python -m pytest tests/ -x -v
108 passed in 12.67s
```

All pre-existing tests (89 from Phase 24 + 8 from Plan 25-01 = 97) plus 19 new tests = 108 total.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed noop_lesson_started signature in test**
- **Found during:** Task 2 test run
- **Issue:** `noop_lesson_started(**kwargs)` was called with positional `event` arg by dispatcher, causing `TypeError: takes 0 positional arguments but 1 was given`
- **Fix:** Changed to `noop_lesson_started(*args, **kwargs)` to accept both positional and keyword args
- **Files modified:** services/notification-bot/tests/test_event_dispatcher.py
- **Commit:** 5d884fd (fixed inline before commit)

## Known Stubs

None. All handlers are fully wired and functional. The reminder text "Напоминание: отметьтесь на паре!" (from ReminderScheduler, Plan 25-01) is intentional MVP text, not a stub.

## Threat Flags

None. No new network endpoints, auth paths, or trust boundary crossings introduced. lesson.closed and attendance.marked events arrive via the existing internal RabbitMQ consumer (T-25-04 and T-25-05 accepted in plan threat model). Telegram delete_message calls use the existing authenticated bot token.

## Self-Check

- [x] `services/notification-bot/bot/notifications/lesson_closed.py` exists
- [x] `services/notification-bot/bot/notifications/attendance_marked.py` exists
- [x] `services/notification-bot/tests/test_lesson_closed.py` exists (6 tests)
- [x] `services/notification-bot/tests/test_attendance_marked.py` exists (7 tests)
- [x] `services/notification-bot/bot/consumers/event_dispatcher.py` contains `"lesson.closed"` and `"attendance.marked"`
- [x] `services/notification-bot/bot/consumers/event_dispatcher.py` contains `reminder_scheduler` parameter
- [x] `services/notification-bot/bot/consumers/event_dispatcher.py` contains `schedule_reminders`
- [x] `services/notification-bot/bot/__main__.py` contains `from bot.services.reminder_scheduler import ReminderScheduler`
- [x] `services/notification-bot/bot/__main__.py` contains `ReminderScheduler(`
- [x] `services/notification-bot/bot/__main__.py` contains `reminder_scheduler=reminder_scheduler`
- [x] commit `5df6c27` exists (Task 1: cleanup handlers + tests)
- [x] commit `5d884fd` exists (Task 2: wiring + dispatcher tests)
- [x] 108 tests pass (full suite)

## Self-Check: PASSED
