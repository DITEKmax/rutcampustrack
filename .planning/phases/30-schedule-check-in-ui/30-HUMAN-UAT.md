---
status: partial
phase: 30-schedule-check-in-ui
source: [30-VERIFICATION.md]
started: 2026-04-06T12:48:00Z
updated: 2026-04-06T12:48:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Full-stack schedule rendering
expected: Today's lessons appear with correct data from schedule-service and subject names resolved from academic-service
result: [pending]

### 2. Touch gesture navigation
expected: Day tabs switch without page reload; week arrows change the displayed week range; swipe on tab strip changes week
result: [pending]

### 3. GPS check-in end-to-end
expected: Browser GPS prompt appears, spinner shows on button, check-in submits, success toast slides up and auto-dismisses after 3s
result: [pending]

### 4. Real-time STOMP attendance updates
expected: The other browser's lesson card attendance count updates in real-time via STOMP WebSocket
result: [pending]

### 5. CheckInScreen empty state
expected: "Сейчас нет активных пар" heading shown, next lesson hint displayed if applicable
result: [pending]

### 6. Offline behavior
expected: CheckInButton disabled, OfflineStaleNotice appears with relative timestamp, cached schedule data still visible
result: [pending]

## Summary

total: 6
passed: 0
issues: 0
pending: 6
skipped: 0
blocked: 0

## Gaps
