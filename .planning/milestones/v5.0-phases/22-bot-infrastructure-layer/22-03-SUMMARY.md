---
phase: 22
plan: 3
subsystem: notification-bot
tags: [python, asyncio, telegram, rate-limiting, token-bucket, retry, queue]
dependency_graph:
  requires: []
  provides: [send_queue.TelegramSendQueue, send_queue.SendTask]
  affects: [notification-bot/bot/services]
tech_stack:
  added: []
  patterns: [token-bucket rate limiting, asyncio.Queue single-worker, exponential backoff retry, duck-typed TelegramRetryAfter]
key_files:
  created:
    - services/notification-bot/bot/services/send_queue.py
    - services/notification-bot/tests/test_send_queue.py
  modified: []
decisions:
  - D-06: asyncio.Queue + token bucket chosen over per-user queues (simpler, single-instance VPS)
  - D-07: single global queue with one worker — avoids Telegram per-chat rate complexity
  - D-08: retry 3x with backoff [1, 2, 4]s; duck-typed retry_after supports both real aiogram and test mocks
  - D-09: counters (total_sent, total_failed) logged on shutdown for observability
metrics:
  duration: "< 5 minutes"
  completed_date: "2026-04-05"
  tasks_completed: 1
  files_changed: 2
---

# Phase 22 Plan 03: Throttled Telegram Send Queue Summary

One-liner: asyncio token bucket send queue with 30/s burst, 3-attempt backoff retry, and duck-typed TelegramRetryAfter support.

## What Was Built

A `TelegramSendQueue` class in `bot/services/send_queue.py` that:

- Accepts `SendTask` items (wrapping a coroutine factory + optional user_id/chat_id)
- Processes tasks via a single asyncio worker coroutine (FIFO order guaranteed)
- Applies token-bucket rate limiting: 30 tokens/second refill, 30-token burst ceiling
- Retries failed sends up to 3 times with backoff delays `[1, 2, 4]` seconds
- Handles Telegram 429 errors duck-typed: any exception with a `retry_after` attribute triggers the appropriate sleep (supports both real `aiogram.exceptions.TelegramRetryAfter` and test mocks without importing aiogram in tests)
- Tracks `_total_sent` and `_total_failed` counters, logged to `INFO` on `shutdown()`

## Test Results

All 6 tests pass in 2.35s:

| Test | Result |
|------|--------|
| test_single_message_sent | PASSED |
| test_queue_processes_in_order | PASSED |
| test_rate_limit_token_bucket | PASSED |
| test_429_retry_after | PASSED |
| test_max_retries_skip | PASSED |
| test_shutdown_logs_totals | PASSED |

Key testing decisions:
- `asyncio.sleep` patched in retry/rate-limit tests so test suite runs in ~2s not ~60s
- `caplog` used to assert shutdown log contains `sent=2` and `failed=0`
- `FakeRetryAfter` exception class defined in test file — no aiogram import needed

## Commits

| Commit | Message |
|--------|---------|
| fd73888 | feat(notification-bot): add throttled Telegram send queue with token bucket rate limiting |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — no new network endpoints or auth paths introduced.

## Self-Check: PASSED

- `services/notification-bot/bot/services/send_queue.py` — FOUND
- `services/notification-bot/tests/test_send_queue.py` — FOUND
- Commit fd73888 — FOUND
