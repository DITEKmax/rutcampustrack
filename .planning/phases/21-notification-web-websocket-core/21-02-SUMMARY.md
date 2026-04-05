---
phase: 21-notification-web-websocket-core
plan: 02
subsystem: notification-web
tags: [websocket, stomp, rabbitmq, event-routing, simp-messaging-template]
dependency_graph:
  requires: [21-01]
  provides: [event-routing, websocket-push]
  affects: [notification-web]
tech_stack:
  added: []
  patterns: [SimpMessagingTemplate, RabbitMQ consumer, headman-only topic filtering, TDD]
key_files:
  created:
    - services/notification-web/src/test/java/ru/rutcampustrack/notification/event/EventConsumerTest.java
  modified:
    - services/notification-web/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java
decisions:
  - "HEADMAN_ONLY_EVENTS Set.of for O(1) lookup of headman-only event types"
  - "Number cast for group_id to handle both Integer and Long from RabbitMQ Jackson deserialization"
  - "Unknown event types forwarded to group topic (not dropped) — future-proof for new event types"
metrics:
  duration: ~20min
  completed: 2026-04-05
  tasks: 2
  files: 2
---

# Phase 21 Plan 02: EventConsumer RabbitMQ-to-WebSocket Routing Summary

RabbitMQ event consumer routing 5 event types to STOMP WebSocket topics via SimpMessagingTemplate, with headman-only filtering for excuse.requested and late_checkin.requested.

## What Was Built

Enhanced EventConsumer.java from a placeholder stub to a live event router that:
- Receives all events from `notification-web.events` RabbitMQ queue
- Routes `lesson.started`, `lesson.cancelled`, `homework.published` to `/topic/group/{groupId}`
- Routes `excuse.requested`, `late_checkin.requested` to `/topic/group/{groupId}/headman`
- Wraps events in `{type, payload}` envelope per D-06 before WebSocket delivery
- Guards against null `event_type`, null `payload`, and null `group_id` with appropriate logging

This completes the real-time push pipeline: RabbitMQ → EventConsumer → SimpMessagingTemplate → STOMP broker → WebSocket clients.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 (TDD RED) | Add failing tests for EventConsumer routing | 40240d2 | EventConsumerTest.java |
| 2 (TDD GREEN) | Implement EventConsumer with SimpMessagingTemplate | 5f4f450 | EventConsumer.java |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed ambiguous Mockito convertAndSend overload resolution**
- **Found during:** Task 1 (TDD RED compile)
- **Issue:** `verify(messagingTemplate, never()).convertAndSend(eq("/topic/group/42"), any())` and `verify(..., never()).convertAndSend(any(String.class), any())` were ambiguous — both `convertAndSend(D, Object)` and `convertAndSend(Object, MessagePostProcessor)` matched
- **Fix:** Changed `any()` to `any(Object.class)` to resolve ambiguity
- **Files modified:** EventConsumerTest.java
- **Commit:** 40240d2

## Known Stubs

None — EventConsumer routes live RabbitMQ events to WebSocket topics. No hardcoded or placeholder values.

## Threat Flags

None — no new network endpoints or auth paths introduced. EventConsumer is internal (RabbitMQ consumer). The destination topic `/topic/group/{groupId}/headman` relies on client-side subscription filtering only; server-side headman authorization of subscriptions is handled at the WebSocket handshake level (JwtHandshakeInterceptor from Plan 01).

## Self-Check: PASSED

- [x] services/notification-web/src/test/java/ru/rutcampustrack/notification/event/EventConsumerTest.java — FOUND
- [x] services/notification-web/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java — FOUND (modified)
- [x] Commit 40240d2 — FOUND
- [x] Commit 5f4f450 — FOUND
- [x] All 20 tests pass: RabbitConfigTest (5) + JwtHandshakeInterceptorTest (6) + EventConsumerTest (9) — BUILD SUCCESSFUL
