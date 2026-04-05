---
status: partial
phase: 21-notification-web-websocket-core
source: [21-VERIFICATION.md]
started: 2026-04-05T00:00:00Z
updated: 2026-04-05T00:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Verify non-headman cannot receive headman-only events in practice
expected: A student (is_headman=false) who subscribes to /topic/group/{groupId}/headman should NOT receive excuse.requested or late_checkin.requested events
result: [pending]

### 2. Confirm group isolation works at the broker level for WS-02 SC-2
expected: Clients of group 43 receive no events when a lesson.started fires for group 42
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
