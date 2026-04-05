---
phase: 24-bot-event-notifications
verified: 2026-04-05T00:00:00Z
status: passed
score: 11/11 must-haves verified
---

# Phase 24: Bot Event Notifications — Verification Report

**Phase Goal:** Implement event notification handlers for the Telegram bot (lesson.started with check-in button, lesson.cancelled, homework.published, homework.updated, excuse.requested, late_checkin.requested). Wire EventDispatcher into the RabbitMQ consumer and __main__.py.
**Verified:** 2026-04-05
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | EventDispatcher routes lesson.started events to lesson_started handler | VERIFIED | `event_dispatcher.py` lines 44-51: `"lesson.started": lambda event: handle_lesson_started(...)` |
| 2 | EventDispatcher routes lesson.cancelled events to lesson_cancelled handler | VERIFIED | `event_dispatcher.py` lines 52-57: `"lesson.cancelled": lambda event: handle_lesson_cancelled(...)` |
| 3 | lesson.started handler sends InlineKeyboardMarkup with WebAppInfo button to all group students with telegram_id | VERIFIED | `lesson_started.py` lines 5, 53-64: imports and constructs `InlineKeyboardMarkup` with `WebAppInfo(url=f"{config.mini_app_url}/checkin?lesson_id={lesson_id}")` |
| 4 | lesson.started handler stores each sent message_id in Redis via ReminderRedisClient.add_message_id | VERIFIED | `lesson_started.py` line 78: `await redis_client.add_message_id(lesson_id, s.user_id, result.message_id)` inside `send_and_store` closure |
| 5 | lesson.cancelled handler sends plain text cancellation to all group students with telegram_id | VERIFIED | `lesson_cancelled.py` lines 45-47, 55-63: builds text with subject+date, enqueues SendTask per student |
| 6 | Students without telegram_id (telegram_id=0) are skipped | VERIFIED | `lesson_started.py` line 70: `if not student.telegram_id: continue`; same pattern in all 4 handlers |
| 7 | Subject names are resolved via academic_client.get_subjects_by_ids | VERIFIED | All relevant handlers call `academic_client.get_subjects_by_ids([subject_id])` with fallback on exception |
| 8 | homework.published event triggers notification to all group students | VERIFIED | `homework.py` lines 33-46, 53-66: publishes subject name + title fan-out to group |
| 9 | homework.updated event triggers notification to all group students | VERIFIED | `homework.py` lines 47-48: `text = f"Домашнее задание обновлено\n\n{title}"` fan-out to group |
| 10 | excuse.requested event triggers notification to headman only | VERIFIED | `headman_alerts.py` lines 37, 54-56: filters `is_headman=True`, builds excuse text, sends to headmen only |
| 11 | late_checkin.requested event triggers notification to headman only | VERIFIED | `headman_alerts.py` lines 37, 57-61: filters headmen, builds late_checkin text with optional date |

**Score:** 11/11 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/notification-bot/bot/consumers/event_dispatcher.py` | Event type to handler routing, exports EventDispatcher | VERIFIED | 101 lines, all 6 event types registered |
| `services/notification-bot/bot/notifications/lesson_started.py` | NOTIF-01 lesson.started handler, exports handle_lesson_started | VERIFIED | 87 lines, InlineKeyboardMarkup + WebAppInfo + Redis storage |
| `services/notification-bot/bot/notifications/lesson_cancelled.py` | NOTIF-06 lesson.cancelled handler, exports handle_lesson_cancelled | VERIFIED | 64 lines, plain text fan-out with optional cancel_reason |
| `services/notification-bot/bot/notifications/homework.py` | NOTIF-07 homework handlers, exports handle_homework | VERIFIED | 67 lines, handles both published and updated |
| `services/notification-bot/bot/notifications/headman_alerts.py` | NOTIF-08/09 headman alert handler, exports handle_headman_alert | VERIFIED | 76 lines, headman-only routing for excuse + late_checkin |
| `services/notification-bot/bot/notifications/__init__.py` | Package init | VERIFIED | Exists (empty file) |
| `services/notification-bot/tests/test_event_dispatcher.py` | Dispatcher routing tests | VERIFIED | 7 test functions |
| `services/notification-bot/tests/test_lesson_started.py` | NOTIF-01 tests | VERIFIED | 6 test functions |
| `services/notification-bot/tests/test_lesson_cancelled.py` | NOTIF-06 tests | VERIFIED | 7 test functions |
| `services/notification-bot/tests/test_homework_notifications.py` | NOTIF-07 tests | VERIFIED | 5 test functions |
| `services/notification-bot/tests/test_headman_alerts.py` | NOTIF-08/09 tests | VERIFIED | 6 test functions |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `event_dispatcher.py` | `lesson_started.py`, `lesson_cancelled.py` | `self._handlers["lesson.started"]` / `["lesson.cancelled"]` lambdas | VERIFIED | Both entries present at lines 44-57 |
| `lesson_started.py` | `ReminderRedisClient.add_message_id` | stores message_id after send_message | VERIFIED | `redis_client.add_message_id(lesson_id, s.user_id, result.message_id)` line 78 |
| `lesson_started.py` | `TelegramSendQueue.put` | enqueues SendTask per student | VERIFIED | `send_queue.put(SendTask(...))` line 80 |
| `event_consumer.py` | `EventDispatcher.dispatch` | consumer passes parsed event dict to dispatcher | VERIFIED | `await dispatcher.dispatch(body)` at line 65 |
| `__main__.py` | `EventDispatcher` | creates dispatcher with all 5 dependencies | VERIFIED | `EventDispatcher(bot=bot, academic_client=..., send_queue=..., redis_client=..., config=config)` lines 127-133 |
| `headman_alerts.py` | `academic_client.get_group_members` | filters is_headman=True from members list | VERIFIED | `[m for m in members if m.is_headman and m.telegram_id]` line 37 |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `lesson_started.py` | `members` | `academic_client.get_group_members(group_id)` | Yes — real gRPC call | FLOWING |
| `lesson_started.py` | `subject_name` | `academic_client.get_subjects_by_ids([subject_id])` | Yes — real gRPC call, fallback on exception | FLOWING |
| `lesson_started.py` | `message_id` | `bot.send_message(...)` return value | Yes — Telegram API result | FLOWING |
| `headman_alerts.py` | `headmen` | filtered from `get_group_members` result | Yes — derived from real gRPC data | FLOWING |
| `event_consumer.py` | `body` | `json.loads(message.body)` from RabbitMQ | Yes — real RabbitMQ message | FLOWING |

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| All 81 tests pass | `py -m pytest tests/ -x -v` | 81 passed in 12.63s | PASS |
| EventDispatcher test file has >= 3 tests | count test functions in test_event_dispatcher.py | 7 | PASS |
| Headman alerts test file has >= 4 tests | count test functions in test_headman_alerts.py | 6 | PASS |
| Lesson started test file has >= 3 tests | count test functions in test_lesson_started.py | 6 | PASS |
| Homework tests has >= 3 tests | count test functions in test_homework_notifications.py | 5 | PASS |
| Placeholder comment removed from event_consumer.py | grep "Phase 22+ will add actual event dispatching" | no output | PASS |
| Commits cd2a2de and fa09461 exist in git log | `git log --oneline -5` | both present | PASS |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| NOTIF-01 | Plan 24-01 | Students receive notification when lesson starts (with Mini App check-in button) | SATISFIED | `lesson_started.py` sends `InlineKeyboardMarkup` with `WebAppInfo` pointing to `{mini_app_url}/checkin?lesson_id={lesson_id}` to all students with telegram_id |
| NOTIF-06 | Plan 24-01 | Students receive notification when lesson is cancelled | SATISFIED | `lesson_cancelled.py` sends plain-text cancellation with subject name, date, and optional reason to group students |
| NOTIF-07 | Plan 24-02 | Students receive homework notifications (published + updated) | SATISFIED | `homework.py` handles both `homework.published` (with subject name) and `homework.updated` (title only) with fan-out to group |
| NOTIF-08 | Plan 24-02 | Headman receives excuse request alerts | SATISFIED | `headman_alerts.py` handles `excuse.requested` with `is_headman=True` filter; includes student name and excuse type |
| NOTIF-09 | Plan 24-02 | Headman receives late check-in request alerts | SATISFIED | `headman_alerts.py` handles `late_checkin.requested` with headman-only routing; includes student name and optional lesson date |

---

### Anti-Patterns Found

No anti-patterns found. Scanned all created/modified files for:
- TODO/FIXME/placeholder comments — none found
- Empty implementations (`return null`, `return {}`, etc.) — none found
- Hardcoded empty state that flows to rendering — none found
- The only comment resembling a stub (`"Phase 22+ will add actual event dispatching"`) was explicitly removed per acceptance criteria; confirmed absent

The one note: `bot/config.py` has `bot_token: str = "placeholder"` but this is an existing pre-phase default for a required secret, not a stub introduced by this phase.

---

### Threat Mitigations Verified

| Threat | Mitigation | Verified |
|--------|-----------|---------|
| T-24-02 (Tampering — payload fields) | `try/except KeyError` guard in each handler; logs warning and returns early | VERIFIED — present in all 4 handlers |
| T-24-03 (Info Disclosure — telegram_id fan-out) | `if not student.telegram_id: continue` in all fan-out handlers | VERIFIED |
| T-24-04 (DoS — group fan-out flooding) | All sends go through `TelegramSendQueue` with rate limiting | VERIFIED — SendTask enqueued, not sent directly |
| T-24-06 (Info Disclosure — headman alerts) | `headmen = [m for m in members if m.is_headman and m.telegram_id]` | VERIFIED — non-headmen never receive excuse/late_checkin messages |
| T-24-07 (Tampering — homework payload) | `try/except KeyError` on `group_id` and `title` | VERIFIED in homework.py and headman_alerts.py |
| T-24-08 (DoS — ack timing) | `except Exception` in `event_consumer.py` wrapping dispatch call | VERIFIED — message always acked via `async with message.process()` |

---

### Human Verification Required

None. All acceptance criteria are verifiable programmatically and confirmed passing.

---

## Gaps Summary

No gaps. All 11 observable truths verified. All 11 required artifacts exist and are substantive, wired, and data-flowing. All 5 requirements satisfied. 81/81 tests pass. No anti-patterns detected.

---

_Verified: 2026-04-05_
_Verifier: Claude (gsd-verifier)_
