---
phase: 25-bot-reminder-lifecycle
verified: 2026-04-05T00:00:00Z
status: human_needed
score: 4/4 must-haves verified
human_verification:
  - test: "Deploy bot container with TZ=Europe/Moscow and trigger a real lesson.started event. Wait for midpoint of the lesson window, confirm a second Telegram message appears in the student chat."
    expected: "Student receives a second reminder message at approximately the lesson midpoint; the new message_id appears in Redis via redis-cli LRANGE reminder:msgs:{lesson_id}:{user_id} 0 -1"
    why_human: "Cannot verify asyncio.sleep-based timer firing in a unit-test context without live infrastructure; delay correctness depends on the Docker container TZ env var being set correctly"
  - test: "After the midpoint reminder, send an attendance.marked event with status=present for that student. Confirm in the student's Telegram chat that both the initial check-in message and the midpoint reminder are deleted."
    expected: "bot.delete_message is called for every message_id stored in Redis for that (lesson_id, user_id) pair; Redis key is gone after the event"
    why_human: "End-to-end deletion path requires live Telegram API and live Redis; unit tests mock both"
  - test: "Send a lesson.closed event for an active lesson that has students with stored reminder message_ids. Confirm all reminder messages disappear from student Telegram chats."
    expected: "All message_ids for all students in the group are deleted from Telegram; Redis keys are removed; no timer reminders fire after the close event"
    why_human: "Requires live Telegram API, live Redis, and live RabbitMQ consumer to verify full lesson.closed cleanup path end-to-end"
---

# Phase 25: Bot Reminder Lifecycle Verification Report

**Phase Goal:** Bot reminder lifecycle — midpoint and near-end reminders, cleanup on lesson close and check-in
**Verified:** 2026-04-05
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                                                                       | Status     | Evidence                                                                                                                  |
|----|---------------------------------------------------------------------------------------------------------------------------------------------|------------|---------------------------------------------------------------------------------------------------------------------------|
| 1  | A student who has not checked in receives a second reminder at lesson midpoint (new message_id stored in Redis)                             | VERIFIED   | `ReminderScheduler._send_reminder_after` calls `redis_client.get_message_ids`; skips on empty; sends + calls `add_message_id` on non-empty. Test 7 in `test_reminder_scheduler.py` confirms coroutine_factory invokes `bot.send_message` and `redis_client.add_message_id`. |
| 2  | A student who has not checked in receives a third reminder near lesson end (5 min before end_time; new message_id stored)                   | VERIFIED   | `near_end_delay_seconds` computes `end_dt - timedelta(minutes=5)`. `schedule_reminders` spawns two tasks — "mid" and "end" — both via `_send_reminder_after`. Test 2 confirms 5100 s delay for 09:00 start / 10:30 end. |
| 3  | When lesson.closed arrives, all reminder messages for that lesson are deleted from Telegram, Redis keys are removed, and pending timers cancelled | VERIFIED   | `handle_lesson_closed` calls `reminder_scheduler.cancel_lesson(lesson_id)` first, then loops over all group members, calls `bot.delete_message` for each stored `message_id`, then `redis_client.delete_key`. 6 tests in `test_lesson_closed.py` cover all branches including `TelegramBadRequest` suppression. |
| 4  | When attendance.marked arrives with status=present, that student's reminder messages are deleted and Redis key is cleared                   | VERIFIED   | `handle_attendance_marked` guards `if status != "present": return` at the top. For `status=present`, fetches student by `user_id`, deletes all stored `message_ids`, calls `redis_client.delete_key`. 7 tests cover present/absent/excused/empty-list/missing-user-id/not-in-group paths. |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact                                                                    | Expected                                                  | Status      | Details                                                                                         |
|-----------------------------------------------------------------------------|-----------------------------------------------------------|-------------|--------------------------------------------------------------------------------------------------|
| `services/notification-bot/bot/services/reminder_scheduler.py`              | ReminderScheduler class with schedule_reminders, cancel_lesson, _send_reminder_after | VERIFIED | 135 lines; all required methods present; imports and uses `redis_client`, `send_queue`, `Bot`. |
| `services/notification-bot/tests/test_reminder_scheduler.py`                | Unit tests for midpoint and near-end scheduling (min 80 lines) | VERIFIED | 223 lines; 8 test functions.                                                                   |
| `services/notification-bot/bot/notifications/lesson_closed.py`              | handle_lesson_closed handler                              | VERIFIED    | 59 lines; contains `async def handle_lesson_closed`, `reminder_scheduler.cancel_lesson`, `except TelegramBadRequest`, `await redis_client.delete_key`.  |
| `services/notification-bot/bot/notifications/attendance_marked.py`          | handle_attendance_marked handler                          | VERIFIED    | 63 lines; contains `async def handle_attendance_marked`, `if status != "present":`, `except TelegramBadRequest`, `await redis_client.delete_key`. |
| `services/notification-bot/bot/consumers/event_dispatcher.py`               | Updated dispatcher with 8 event types including lesson.closed and attendance.marked | VERIFIED | Contains `"lesson.closed"` and `"attendance.marked"` keys in `_handlers`; `reminder_scheduler` parameter; `schedule_reminders` call. |
| `services/notification-bot/bot/__main__.py`                                 | ReminderScheduler instantiation and injection             | VERIFIED    | Contains `from bot.services.reminder_scheduler import ReminderScheduler`, `reminder_scheduler = ReminderScheduler(...)`, `reminder_scheduler=reminder_scheduler` in EventDispatcher call. |
| `services/notification-bot/tests/test_lesson_closed.py`                     | Unit tests for NOTIF-04 (min 60 lines)                   | VERIFIED    | 238 lines; 6 test functions.                                                                   |
| `services/notification-bot/tests/test_attendance_marked.py`                 | Unit tests for NOTIF-05 (min 60 lines)                   | VERIFIED    | 198 lines; 7 test functions.                                                                   |

### Key Link Verification

| From                                                       | To                                                                       | Via                                              | Status   | Details                                                                                       |
|------------------------------------------------------------|--------------------------------------------------------------------------|--------------------------------------------------|----------|-----------------------------------------------------------------------------------------------|
| `bot/services/reminder_scheduler.py`                       | `bot.services.redis_client.ReminderRedisClient`                          | `redis_client.get_message_ids`, `redis_client.add_message_id` | WIRED | Lines 101-103 (`get_message_ids`), lines 113-115 (`add_message_id`) — both confirmed in implementation. |
| `bot/services/reminder_scheduler.py`                       | `bot.services.send_queue.TelegramSendQueue`                              | `send_queue.put(SendTask(...))`                  | WIRED    | Line 117: `await self._send_queue.put(SendTask(...))`.                                       |
| `bot/consumers/event_dispatcher.py`                        | `bot.notifications.lesson_closed.handle_lesson_closed`                  | `self._handlers['lesson.closed']` lambda         | WIRED    | Lines 78-84 in `event_dispatcher.py`: lambda calls `handle_lesson_closed` with full kwarg set. |
| `bot/consumers/event_dispatcher.py`                        | `bot.notifications.attendance_marked.handle_attendance_marked`          | `self._handlers['attendance.marked']` lambda     | WIRED    | Lines 85-90 in `event_dispatcher.py`: lambda calls `handle_attendance_marked`.               |
| `bot/__main__.py`                                          | `bot.services.reminder_scheduler.ReminderScheduler`                     | import and instantiation                         | WIRED    | Line 18: import. Lines 128-133: `ReminderScheduler(bot=..., academic_client=..., send_queue=..., redis_client=...)`. |
| `bot/consumers/event_dispatcher.py` (`lesson.started`)    | `bot.services.reminder_scheduler.ReminderScheduler.schedule_reminders`  | `_handle_lesson_started_with_scheduling` method  | WIRED    | Lines 109-118: `if self._reminder_scheduler is not None: ... self._reminder_scheduler.schedule_reminders(...)`. |

### Data-Flow Trace (Level 4)

| Artifact                     | Data Variable         | Source                                    | Produces Real Data            | Status    |
|------------------------------|-----------------------|-------------------------------------------|-------------------------------|-----------|
| `reminder_scheduler.py`      | `existing_ids`        | `redis_client.get_message_ids(lesson_id, student.user_id)` | Yes — reads live Redis key | FLOWING   |
| `lesson_closed.py`           | `message_ids`         | `redis_client.get_message_ids(lesson_id, student.user_id)` | Yes — reads live Redis key | FLOWING   |
| `attendance_marked.py`       | `message_ids`         | `redis_client.get_message_ids(lesson_id, user_id)` | Yes — reads live Redis key  | FLOWING   |
| `reminder_scheduler.py`      | `result.message_id`   | `bot.send_message(...)` return value       | Yes — live Telegram API response | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED for timer-firing paths (require live RabbitMQ + asyncio event loop running in Docker). Test suite acts as the equivalent.

| Behavior                             | Command                                                            | Result                | Status  |
|--------------------------------------|--------------------------------------------------------------------|-----------------------|---------|
| Full test suite (108 tests) passes   | `py -m pytest tests/ --tb=short`                                   | 108 passed in 12.62s  | PASS    |
| `test_reminder_scheduler.py` (8 tests) | `py -m pytest tests/test_reminder_scheduler.py`                  | 8 passed              | PASS    |
| `test_lesson_closed.py` (6 tests)    | `py -m pytest tests/test_lesson_closed.py`                         | 6 passed              | PASS    |
| `test_attendance_marked.py` (7 tests)| `py -m pytest tests/test_attendance_marked.py`                     | 7 passed              | PASS    |
| `test_event_dispatcher.py` (13 tests)| `py -m pytest tests/test_event_dispatcher.py`                      | 13 passed             | PASS    |
| Commits exist in git history         | `git show a94655a 60b6d54 5df6c27 5d884fd --name-only --oneline`   | All 4 commits found   | PASS    |

### Requirements Coverage

| Requirement | Source Plan  | Description                                                                         | Status      | Evidence                                                                                  |
|-------------|--------------|-------------------------------------------------------------------------------------|-------------|-------------------------------------------------------------------------------------------|
| NOTIF-02    | 25-01, 25-02 | Student receives reminder at lesson midpoint if not yet checked in                  | SATISFIED   | `midpoint_delay_seconds` + `_send_reminder_after` with Redis active-check; test 1 + test 7. |
| NOTIF-03    | 25-01, 25-02 | Student receives final reminder near lesson end if not yet checked in               | SATISFIED   | `near_end_delay_seconds` (offset=5 min) + second `asyncio.Task` in `schedule_reminders`; test 2. |
| NOTIF-04    | 25-02        | All reminder messages deleted from Telegram when lesson closes                      | SATISFIED   | `handle_lesson_closed` deletes all `message_ids` per student, then calls `delete_key`; 6 tests. |
| NOTIF-05    | 25-02        | Reminder messages deleted immediately when student checks in (attendance.marked)    | SATISFIED   | `handle_attendance_marked` guards on `status == "present"`, deletes messages, calls `delete_key`; 7 tests. |

**Note:** REQUIREMENTS.md still shows NOTIF-02 through NOTIF-05 with unchecked checkboxes (`- [ ]`). This is a documentation-only discrepancy — the implementation is complete and all tests pass. The REQUIREMENTS.md checkboxes should be updated to `- [x]` to reflect phase completion. This is informational and does not block the phase.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | — | No TODOs, FIXMEs, empty returns, or hardcoded stubs detected across all 5 phase files | — | — |

The hardcoded reminder text `"Напоминание: отметьтесь на паре!"` in `reminder_scheduler.py` is noted in both SUMMARY files as intentional MVP text, not a stub.

### Human Verification Required

All automated checks pass with 108/108 tests. Three behaviors require live infrastructure to fully verify:

#### 1. Midpoint and Near-End Timer Firing

**Test:** Start a lesson via a `lesson.started` RabbitMQ event with a short window (e.g., start_time = now, end_time = now + 10 min). Wait ~5 minutes and observe the student's Telegram chat.
**Expected:** Student receives a second message ("Напоминание: отметьтесь на паре!") at approximately the midpoint (~2.5 min in), and a third at 5 minutes before the end (~5 min in). Each new message_id is appended to Redis (`LRANGE reminder:msgs:{lesson_id}:{user_id} 0 -1` shows 3 entries after both fire).
**Why human:** asyncio.sleep-based timers cannot be end-to-end tested in CI without live infrastructure; correctness also depends on `TZ=Europe/Moscow` env var being set in the Docker container — this is documented as a design decision but cannot be verified programmatically.

#### 2. Check-In Cleanup (NOTIF-05 live path)

**Test:** After the initial `lesson.started` check-in button is sent (and at least one reminder has fired), send an `attendance.marked` event with `status=present` for that student via RabbitMQ.
**Expected:** All reminder messages (check-in button + any midpoint/near-end messages) disappear from the student's Telegram chat immediately. The Redis key `reminder:msgs:{lesson_id}:{user_id}` is deleted (`EXISTS` returns 0).
**Why human:** Requires live Telegram API and live Redis to verify message deletion; unit tests mock both.

#### 3. Lesson Close Cleanup (NOTIF-04 live path)

**Test:** Send a `lesson.closed` event for a lesson where multiple students still have stored `message_ids` in Redis.
**Expected:** All reminder messages for all students in the group disappear from their Telegram chats. All Redis keys for that lesson are deleted. Any pending timer tasks that had not yet fired do not fire after the close event.
**Why human:** Requires live Telegram API, live Redis, and live RabbitMQ consumer to confirm timer cancellation and multi-student deletion operate correctly together.

### Gaps Summary

No gaps found. All 4 roadmap success criteria are satisfied by the implementation:

1. Midpoint reminder path: `ReminderScheduler.schedule_reminders` spawns task → `_send_reminder_after("mid")` → Redis check → `send_queue.put` → `add_message_id`.
2. Near-end reminder path: same flow with `_send_reminder_after("end")` using `near_end_delay_seconds`.
3. Lesson close cleanup: `handle_lesson_closed` cancels timers, deletes all `message_ids` from Telegram, clears Redis keys.
4. Check-in cleanup: `handle_attendance_marked` guards on `status=present`, deletes per-student `message_ids` from Telegram, clears Redis key.

Status is `human_needed` (not `passed`) because end-to-end timer firing with live Telegram API requires human observation in a deployed environment.

---

_Verified: 2026-04-05_
_Verifier: Claude (gsd-verifier)_
