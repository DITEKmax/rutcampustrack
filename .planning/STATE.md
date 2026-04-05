---
gsd_state_version: 1.0
milestone: v5.0
milestone_name: Notification Service (Web + Bot)
status: executing
stopped_at: Phase 21 context gathered
last_updated: "2026-04-05T09:48:14.119Z"
last_activity: 2026-04-05
progress:
  total_phases: 6
  completed_phases: 2
  total_plans: 5
  completed_plans: 5
  percent: 100
---

# Project State

## Current Milestone

v5.0 Notification Service (Web + Bot)

## Current Position

Phase: 22
Plan: Not started
Status: Executing Phase 21
Last activity: 2026-04-05

Progress: [░░░░░░░░░░] 0%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-04)

**Core value:** Full backend microservice backbone shipped. Now delivering real-time notifications via WebSocket (web panel) and Telegram bot.
**Current focus:** Phase 21 — notification-web-websocket-core

## Completed Milestones

| Version | Name | Phases | Plans | Shipped |
|---------|------|--------|-------|---------|
| v1.0 | Auth Service + API Gateway | 1.1-1.4 | 4 | 2026-03-30 |
| v2.0 | Academic Service | 5-9 | 12 | 2026-03-31 |
| v3.0 | Schedule Service | 10-14 | 10 | 2026-04-04 |
| v4.0 | Attendance Service MVP | 15-19 | 12 | 2026-04-04 |

## Accumulated Context

### Decisions

Recent decisions affecting v5.0:

- STOMP in-memory broker (no external broker needed for single-instance VPS)
- JWT claims extracted to WebSocket session attributes at handshake — not re-validated on expiry
- grpcio pinned at 1.73.0 (protobuf 5.x compatible — 1.80.x requires protobuf 6.x, breaking change)
- aio-pika consumer watchdog required from day one (silent consumer death after RabbitMQ restart)
- Redis RPUSH list (not SET string) for reminder message_ids — LRANGE retrieves all on lesson.closed
- [Phase 20]: Unit tests over Spring context tests for RabbitConfig — faster, no RabbitMQ mock needed
- [Phase 20]: grpcio pinned at 1.73.0 — protobuf 5.x compatible (1.80.x requires protobuf 6.x, breaking change)
- [Phase 20]: aio-pika connect_robust used for auto-reconnect on RabbitMQ restart (watchdog from day one)
- [Phase 20]: Health check validates both consumer task liveness and RabbitMQ connection state (Pitfall 4)
- [Phase 20]: Redis RPUSH list for reminder message_ids with TTL=86400 documented in config.py (D-07, D-08)

### Research Flags (resolve before phase begins)

- Phase 21: Verify JwtAuthenticationFilter handles HTTP GET Upgrade: websocket — injects X-User-Id/X-Group-Id before WebSocket proxy forward
- Phase 22/23: Verify POST /auth/otp/request returns OTP code in response body (bot must deliver code to user)
- Phase 23: Decide how bot looks up user by telegram_id for /start — gRPC only has GetUserById(user_id), not by telegram_id

### Blockers/Concerns

None yet.

## Session Continuity

Last session: 2026-04-04T22:41:34.654Z
Stopped at: Phase 21 context gathered
Resume file: .planning/phases/21-notification-web-websocket-core/21-CONTEXT.md
Next action: /gsd:plan-phase 20
