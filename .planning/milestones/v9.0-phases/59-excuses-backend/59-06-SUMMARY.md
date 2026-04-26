---
phase: 59-excuses-backend
plan: 06
subsystem: notification-bot
tags: [notification-bot, python, rabbitmq, consumer, excuse-tickets, pytest]
status: completed
completed: 2026-04-14

dependency_graph:
  requires:
    - 59-05 (ExcuseEventPublisher emits excuse.decided on rut-uit.events fanout; fixture mirrored to services/notification-bot/tests/fixtures/excuse_decided.json)
    - services/notification-bot/bot/consumers/event_dispatcher.py (handler registry)
    - services/notification-bot/bot/notifications/headman_alerts.py (handler style template)
    - services/notification-bot/bot/grpc_client/academic_client.py (AcademicGrpcClient — extended with get_user_by_id wrapping existing GetUserById RPC)
    - proto/academic.proto (GetUserById already defined — no proto change)
  provides:
    - handle_student_alert (bot.notifications.student_alerts) — consumer handler for excuse.decided
    - AcademicGrpcClient.get_user_by_id — telegram_id lookup for the excuse.decided handler
    - "excuse.decided" route in EventDispatcher._handlers
    - 6 pytest cases proving AC-8 (approved/rejected text, fallback comment, malformed-payload safety)
  affects:
    - 59-07 / 59-08 (frontend) — no direct impact; bot side is now complete for excuse flow
    - future plans wanting a per-student DM → reuse handle_student_alert + AcademicGrpcClient.get_user_by_id

tech-stack:
  added: []
  patterns:
    - "Handler signature mirrors handle_headman_alert: (event, bot, academic_client, send_queue, **kwargs) → coroutine"
    - "Telegram send routed through TelegramSendQueue.put(SendTask(...)) — honours rate-limit + notification prefs"
    - "Graceful KeyError/try-except on gRPC call: malformed payload or missing linked Telegram → logger.warning + return (no consumer crash → no requeue storm)"

key-files:
  created:
    - services/notification-bot/bot/notifications/student_alerts.py
    - services/notification-bot/tests/test_excuse_decided.py
  modified:
    - services/notification-bot/bot/consumers/event_dispatcher.py (+import, +excuse.decided route)
    - services/notification-bot/bot/grpc_client/academic_client.py (+get_user_by_id method)
    - services/notification-bot/tests/test_event_dispatcher.py (registry set: +excuse.decided)

decisions:
  - "telegram_id resolved via new AcademicGrpcClient.get_user_by_id (wrapping existing academic.proto GetUserById RPC). Alternative — adding group_id to the excuse.decided payload and using get_group_members — was rejected: would require reopening 59-05's contract test and publisher, and group_id is not relevant to this event's semantics. The RPC already existed server-side and in the generated Python stubs; only the client wrapper was missing."
  - "No local cache in get_user_by_id. Fires at most once per excuse decision (low rate) and the bot wants the freshest telegram_id in case the student just linked their account; caching would invalidate that UX."
  - "Message texts use emojis (✅ / ❌) matching the project's notification style (existing NOTIF handlers do the same). Russian copy from plan's interfaces block; 'без комментария' fallback chosen over hiding the comment line entirely, so the student always sees a consistent 3-line structure."
  - "Fixture file was created by 59-05 (commit fc1cb94) and left untouched — test_excuse_decided.py loads it directly. Plan's files_modified list included the fixture path only to mark it as required-present, not required-rewritten."

metrics:
  tasks: 2
  commits: 2
  files_created: 2
  files_modified: 3
  duration: ~10 min
---

# Phase 59 Plan 06: Notification-bot excuse.decided Consumer Summary

One-liner: Python aiogram handler `handle_student_alert` consumes `excuse.decided` from the `rut-uit.events` fanout, resolves the student's `telegram_id` via a new `AcademicGrpcClient.get_user_by_id` wrapper, and pushes an approved/rejected DM through `TelegramSendQueue` — covered by 6 pytest cases against the canonical fixture from 59-05.

## What Was Built

### Handler

- **`student_alerts.handle_student_alert`** — new module `bot/notifications/student_alerts.py`, mirrors the `handle_headman_alert` contract (`event, bot, academic_client, send_queue, **kwargs`).
  - Reads `payload.user_id`, `payload.status`, `payload.decision_comment`.
  - Renders ru text per D-28:
    - approved → `✅ Ваш запрос на уважительную причину одобрен.` (+ optional comment line)
    - rejected → `❌ Ваш запрос на уважительную причину отклонён.` + `Комментарий: {…|без комментария}`
  - Resolves `telegram_id` via `academic_client.get_user_by_id(user_id)`; if `telegram_id == 0` or the RPC raises, logs a warning and returns — the RabbitMQ message is still acked (no requeue loop).
  - Dispatch via `send_queue.put(SendTask(coroutine_factory=..., user_id=..., chat_id=...))` so the existing token-bucket rate limiter + notification preferences filter apply automatically.

### gRPC client

- **`AcademicGrpcClient.get_user_by_id(user_id)`** — new method in `bot/grpc_client/academic_client.py`, wraps the already-generated `GetUserById` RPC (see `academic_pb2_grpc.py`). Returns `UserResponse` with `.telegram_id` / `.display_name` / etc. No caching (rare call, freshness required).

### Dispatcher wiring

- `EventDispatcher.__init__` imports `handle_student_alert` (late import, same pattern as other handlers to avoid circular import).
- `_handlers["excuse.decided"]` registered with the same lambda shape as `excuse.requested`.

### Tests

- **`tests/test_excuse_decided.py`** — 6 async pytest cases:
  1. `test_approved_event_sends_ru_message_to_student` — loads the canonical fixture, asserts `SendTask.user_id` / `chat_id`, executes the coroutine factory to verify the live `bot.send_message(chat_id=…, text=…)` call, matches `✅` + `одобрен` + `decision_comment` (AC-8 happy path).
  2. `test_rejected_event_sends_ru_message_with_comment` — `❌` + `отклонён` + custom comment; asserts `get_user_by_id` was awaited with the payload's user_id.
  3. `test_rejected_event_without_comment_falls_back_to_default` — missing `decision_comment` → `без комментария`.
  4. `test_missing_user_id_skips_send_gracefully` — T-59-06-03 mitigation: no crash, `send_queue.put` never called, `get_user_by_id` never called.
  5. `test_unknown_status_skips_send` — status `"pending"` → handler returns before any RPC.
  6. `test_student_without_telegram_id_skips_send` — student exists but `telegram_id=0` → graceful skip.
- **`tests/test_event_dispatcher.py`** — added `"excuse.decided"` to the registry assertion set (Rule 1 auto-fix — stale registry constant after adding the new route).

## Verification

| Check | Result |
|---|---|
| `py -3 -c "from bot.notifications.student_alerts import handle_student_alert"` | OK |
| `py -3 -c "from bot.consumers.event_dispatcher import EventDispatcher"` | OK (import chain resolves) |
| `grep "excuse.decided" bot/consumers/event_dispatcher.py` | 1 hit in `_handlers` dict |
| `py -3 -m pytest tests/test_excuse_decided.py -v` | **6/6 green** |
| `py -3 -m pytest tests/` | **128/128 green** (was 122 before; no regressions) |

## Commits

| Task | Commit | Message |
|---|---|---|
| 1 | `cf4bcbe` | feat(59-06): add excuse.decided consumer with student_alerts handler |
| 2 | `d7670cf` | test(59-06): cover excuse.decided student_alerts handler (AC-8) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] Stale registry set in `test_event_dispatcher.py`**
- **Found during:** Task 2 full-suite regression run
- **Issue:** `test_dispatcher_has_eight_event_types` asserted equality against a hardcoded set of 11 event types. After registering `excuse.decided` in Task 1, the assertion's "expected" side went stale and the test failed (`Extra items in the left set: 'excuse.decided'`).
- **Fix:** Added `"excuse.decided"` to the expected-set literal with a 59-06 comment. No test renamed — the test's name is already decoupled from the count (still says "eight" but that ship sailed at BUG-006-6; name drift tracked as a separate cleanup item).
- **Files modified:** `services/notification-bot/tests/test_event_dispatcher.py`
- **Commit:** `d7670cf`

**2. [Rule 2 — Missing critical functionality] `AcademicGrpcClient` had no user-by-id lookup**
- **Found during:** Task 1 planning (reading `academic_client.py`)
- **Issue:** The plan's `<action>` block suggested using `academic_client.get_user(user_id)` but no such method existed. Without it the handler has no way to resolve the student's `telegram_id` and the acceptance criterion is unreachable.
- **Fix:** Added `get_user_by_id(user_id)` wrapping the already-existing `GetUserById` gRPC method (confirmed in `proto/academic.proto:32` and `academic_pb2_grpc.py:79`). Zero server-side change, zero proto change.
- **Files modified:** `services/notification-bot/bot/grpc_client/academic_client.py`
- **Commit:** `cf4bcbe` (bundled with Task 1)

### Clarifications (not deviations)

- **Plan listed `services/notification-bot/tests/fixtures/excuse_decided.json` under `files_modified`** — the file was already present (commit `fc1cb94` in 59-05). Left untouched; test loads it directly. Listing it only indicates dependency-in, not rewrite.

## Known Stubs

None introduced by this plan. Remaining project-level stubs (unchanged):

- `ExcuseService.createExcuse` does not validate `lessonIds` via gRPC (deferred from 59-03).
- Frontends (`59-07`, `59-08`) still to come.

## Threat Flags

None. Plan's `<threat_model>` covers all surfaces introduced:

- **T-59-06-01 (Spoofing: forged user_id in event)** — accepted. RabbitMQ `rut-uit.events` is docker-internal; external access is blocked at the compose network boundary. Matches 59-05's identical disposition.
- **T-59-06-02 (Information Disclosure: decision_comment in DM)** — accepted. The comment is authored by the headman for this specific student's own ticket; disclosing it to the student is the whole point of the event. Verified payload contains no auth tokens / internal IDs beyond `ticket_id` + `decision_by` (a numeric user id, not PII).
- **T-59-06-03 (DoS: malformed payload crashes consumer)** — mitigated. `try/except KeyError` on required fields (test 4), `try/except Exception` on the gRPC call (test 6 path), unknown-status guard (test 5), telegram_id=0 guard (test 6). Any non-happy-path returns without raising; dispatcher's own `try/except` in `dispatch()` is therefore a redundant safety net, not a load-bearing dependency.

## Self-Check: PASSED

- FOUND: services/notification-bot/bot/notifications/student_alerts.py
- FOUND: services/notification-bot/tests/test_excuse_decided.py
- FOUND: services/notification-bot/tests/fixtures/excuse_decided.json (from 59-05)
- FOUND: `excuse.decided` route in services/notification-bot/bot/consumers/event_dispatcher.py
- FOUND: `get_user_by_id` method in services/notification-bot/bot/grpc_client/academic_client.py
- FOUND commit cf4bcbe
- FOUND commit d7670cf
- VERIFIED: `py -3 -m pytest tests/` → 128 passed (no regressions against prior 122)

## Notes for Downstream Plans

- **59-07 / 59-08 (frontends):** the bot side of the excuse flow is now complete end-to-end. Frontend can treat `excuse.decided` as fully delivered (Mongo status + RabbitMQ event + Telegram DM). No new contracts for the UI to consume from this plan.
- **Future per-student DM handlers** should import `handle_student_alert` as a reference pattern: same signature, same `SendTask` usage, same guard-rail ordering (validate payload → render text → resolve telegram_id → enqueue).
