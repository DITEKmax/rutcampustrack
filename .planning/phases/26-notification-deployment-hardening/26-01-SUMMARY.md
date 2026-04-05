---
phase: 26-notification-deployment-hardening
plan: 01
subsystem: infra
tags: [docker-compose, notification-bot, notification-web, jwt, deployment]

requires:
  - phase: 20-notification-web-foundation
    provides: notification-web service with JWT validation
  - phase: 24-bot-event-notifications
    provides: lesson_closed handler with reminder_scheduler dependency

provides:
  - None-safe lesson_closed handler that does not crash on None reminder_scheduler
  - JWT public key volume mount for notification-web in docker-compose
  - Complete environment variable configuration for notification-bot in docker-compose

affects: [notification-web, notification-bot, docker-compose, deployment]

tech-stack:
  added: []
  patterns: [None-guard pattern for optional dependencies, named volume sharing for JWT keys]

key-files:
  created: []
  modified:
    - services/notification-bot/bot/notifications/lesson_closed.py
    - services/notification-bot/tests/test_lesson_closed.py
    - docker-compose.yml

key-decisions:
  - "jwt-keys named volume mounted :ro in notification-web — service cannot write/corrupt keys"
  - "None guard logs warning instead of silently skipping — observable in logs for debugging"

patterns-established:
  - "None-guard pattern: optional dependencies checked before use with logger.warning fallback"

requirements-completed: [INFRA-02]

duration: 3min
completed: 2026-04-05
---

# Phase 26 Plan 01: Notification Deployment Hardening Summary

**Fixed 3 deployment gaps: None-guard in lesson_closed.py, JWT key volume for notification-web, 6 missing env vars for notification-bot**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-05T16:09:52Z
- **Completed:** 2026-04-05T16:12:23Z
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments
- Fixed BROKEN-01: lesson_closed handler no longer crashes with AttributeError when reminder_scheduler is None
- Fixed MISSING-02: notification-web now has JWT_PUBLIC_KEY_PATH env var and /keys:ro volume mount from jwt-keys named volume
- Fixed MISSING-01: notification-bot now has all 6 missing config environment variables (SCHEDULE_GRPC_HOST, SCHEDULE_GRPC_PORT, AUTH_SERVICE_HOST, AUTH_SERVICE_PORT, API_GATEWAY_URL, MINI_APP_URL)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add None guard to lesson_closed.py and add test (BROKEN-01)** - `0e63653` (fix)
2. **Task 2: Add JWT public key volume mount to notification-web (MISSING-02)** - `8ec91b4` (chore)
3. **Task 3: Add 6 missing environment variables to notification-bot (MISSING-01)** - `edae1ae` (chore)

## Files Created/Modified
- `services/notification-bot/bot/notifications/lesson_closed.py` - Added None guard around reminder_scheduler.cancel_lesson() with warning log
- `services/notification-bot/tests/test_lesson_closed.py` - Added test_lesson_closed_handles_none_reminder_scheduler (109 total tests pass)
- `docker-compose.yml` - Added JWT_PUBLIC_KEY_PATH + jwt-keys volume to notification-web; added 6 env vars to notification-bot; declared jwt-keys named volume

## Decisions Made
- jwt-keys volume mounted :ro in notification-web — prevents the service from writing/corrupting keys (only public key shared, not private)
- None guard logs warning (not silent skip) — makes the condition observable in logs for debugging

## Deviations from Plan

None - plan executed exactly as written.

## Threat Flags

None found - all changes match the plan's threat model (T-26-01 jwt-keys :ro, T-26-03 None guard).

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 3 deployment gaps from the v5.0 milestone audit are now closed
- Both notification services have complete docker-compose configuration
- Ready for production deployment or further integration testing

---
*Phase: 26-notification-deployment-hardening*
*Completed: 2026-04-05*
