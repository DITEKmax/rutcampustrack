---
gsd_state_version: 1.0
milestone: v5.0
milestone_name: Notification Service (Web + Bot)
status: verifying
stopped_at: Phase 23 context gathered
last_updated: "2026-04-05T11:40:53.265Z"
last_activity: 2026-04-05
progress:
  total_phases: 6
  completed_phases: 3
  total_plans: 8
  completed_plans: 8
  percent: 100
---

# Project State

## Current Milestone

v5.0 Notification Service (Web + Bot)

## Current Position

Phase: 22 (bot-infrastructure-layer)
Plan: 3 of 3
Status: Phase complete — ready for verification
Last activity: 2026-04-05

Progress: [░░░░░░░░░░] 0%

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-04)

**Core value:** Full backend microservice backbone shipped. Now delivering real-time notifications via WebSocket (web panel) and Telegram bot.
**Current focus:** Phase 20 — Shared Infrastructure

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
- [Phase 22]: asyncio.Queue + single worker with token bucket (30/s, 30 burst), retry [1,2,4]s backoff, duck-typed retry_after
- [Phase 22]: Bumped protobuf to 6.31.0 to match grpcio-tools 1.73.0 bundled gencode version
- [Phase 22]: ReminderRedisClient uses RPUSH/LRANGE list pattern with TTL for per-lesson-per-user reminder message tracking

### Research Flags (resolve before phase begins)

- Phase 21: Verify JwtAuthenticationFilter handles HTTP GET Upgrade: websocket — injects X-User-Id/X-Group-Id before WebSocket proxy forward
- Phase 22/23: Verify POST /auth/otp/request returns OTP code in response body (bot must deliver code to user)
- Phase 23: Decide how bot looks up user by telegram_id for /start — gRPC only has GetUserById(user_id), not by telegram_id

### Blockers/Concerns

None yet.

## Session Continuity

Last session: 2026-04-05T11:40:53.229Z
Stopped at: Phase 23 context gathered
Resume file: .planning/phases/23-bot-telegram-commands/23-CONTEXT.md
Next action: Execute 22-02 (Redis reminder client)
