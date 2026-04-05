---
status: partial
phase: 25-bot-reminder-lifecycle
source: [25-VERIFICATION.md]
started: 2026-04-05T00:00:00Z
updated: 2026-04-05T00:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Timer firing in live deployment
expected: Deploy bot container with TZ=Europe/Moscow, trigger lesson.started. At midpoint, student receives second reminder message. New message_id appears in Redis via `redis-cli LRANGE reminder:msgs:{lesson_id}:{user_id} 0 -1`.
result: [pending]

### 2. Check-in cleanup (live)
expected: After midpoint reminder, send attendance.marked (status=present). Both initial check-in message and midpoint reminder are deleted from Telegram. Redis key is gone.
result: [pending]

### 3. Lesson close cleanup (live)
expected: Send lesson.closed for an active lesson with stored message_ids. All reminder messages disappear from all student chats. Redis keys removed. No stale timer reminders fire.
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
