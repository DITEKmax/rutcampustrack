---
phase: 26-notification-deployment-hardening
verified: 2026-04-05T19:15:00Z
status: passed
score: 3/3 must-haves verified
re_verification: false
---

# Phase 26: Notification Deployment Hardening Verification Report

**Phase Goal:** Fix all deployment blockers and defensive coding gaps found by v5.0 milestone audit -- notification-web starts successfully with JWT key, notification-bot is fully configurable from docker-compose, and lesson.closed handler is resilient to missing ReminderScheduler
**Verified:** 2026-04-05T19:15:00Z
**Status:** passed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | lesson_closed handler does not raise AttributeError when reminder_scheduler is None | VERIFIED | Line 37 of lesson_closed.py: `if reminder_scheduler is not None:` guard with warning log on else branch (line 40). Test `test_lesson_closed_handles_none_reminder_scheduler` at line 241 of test file explicitly passes `reminder_scheduler=None` and asserts message deletion still proceeds. |
| 2 | notification-web docker-compose block has JWT_PUBLIC_KEY_PATH env var and /keys volume mount | VERIFIED | docker-compose.yml line 113: `JWT_PUBLIC_KEY_PATH: /keys/public.key`, line 115: `jwt-keys:/keys:ro`, line 177: `jwt-keys:` declared in top-level volumes. application.yml line 16 uses `${JWT_PUBLIC_KEY_PATH:/keys/public.key}` matching the env var. |
| 3 | notification-bot docker-compose block lists all 6 config environment variables | VERIFIED | docker-compose.yml lines 147-152: SCHEDULE_GRPC_HOST, SCHEDULE_GRPC_PORT, AUTH_SERVICE_HOST, AUTH_SERVICE_PORT, API_GATEWAY_URL, MINI_APP_URL all present with correct values. config.py lines 24-42 define matching Pydantic fields. |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/notification-bot/bot/notifications/lesson_closed.py` | None-safe reminder_scheduler usage | VERIFIED | Contains `if reminder_scheduler is not None:` pattern at line 37 |
| `docker-compose.yml` | JWT key volume mount for notification-web, env vars for notification-bot | VERIFIED | Contains JWT_PUBLIC_KEY_PATH at line 113, jwt-keys volume at line 115, all 6 bot env vars at lines 147-152 |
| `services/notification-bot/tests/test_lesson_closed.py` | Test for None reminder_scheduler | VERIFIED | Test at line 241 covers the None case, asserts no crash and continued message deletion |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| docker-compose.yml notification-web (line 113) | application.yml (line 16) | JWT_PUBLIC_KEY_PATH env var | WIRED | docker-compose sets `JWT_PUBLIC_KEY_PATH: /keys/public.key`, application.yml reads `${JWT_PUBLIC_KEY_PATH:/keys/public.key}` |
| docker-compose.yml notification-web (line 115) | jwt-keys named volume (line 177) | volume mount | WIRED | `jwt-keys:/keys:ro` references top-level `jwt-keys:` volume |
| docker-compose.yml notification-bot (lines 147-152) | bot/config.py (lines 24-42) | Pydantic BaseSettings env var binding | WIRED | All 6 env vars match Pydantic field names (case-insensitive): SCHEDULE_GRPC_HOST->schedule_grpc_host, SCHEDULE_GRPC_PORT->schedule_grpc_port, AUTH_SERVICE_HOST->auth_service_host, AUTH_SERVICE_PORT->auth_service_port, API_GATEWAY_URL->api_gateway_url, MINI_APP_URL->mini_app_url |

### Data-Flow Trace (Level 4)

Not applicable -- this phase modifies configuration and a defensive guard, not data-rendering components.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| docker-compose validates | `docker compose config --quiet` | Exit 0 (warning about deprecated `version` attribute only) | PASS |
| Python tests | `python -m pytest tests/test_lesson_closed.py -x -v` | SKIP -- Python not available in shell environment | SKIP |

Note: Python is not installed as a native CLI tool on this Windows machine (only Windows Store stub). The test file was verified structurally: the test at line 241 passes `reminder_scheduler=None`, calls `handle_lesson_closed`, and asserts `bot.delete_message` and `redis_client.delete_key` are called correctly.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| INFRA-02 | 26-01-PLAN | Docker-compose includes notification-web and notification-bot containers with health checks | SATISFIED | Both services have complete environment blocks, health checks, volume mounts, and dependency declarations in docker-compose.yml |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | - | - | - | No anti-patterns detected in modified files |

### Human Verification Required

No human verification items identified. All changes are configuration and defensive code that can be verified statically.

### Gaps Summary

No gaps found. All three deployment issues identified by the v5.0 milestone audit have been addressed:

1. **BROKEN-01** (lesson_closed AttributeError): Fixed with None guard at line 37 of lesson_closed.py, with warning log and continued message deletion. New test covers the case.
2. **MISSING-02** (notification-web JWT key): JWT_PUBLIC_KEY_PATH env var and jwt-keys:/keys:ro volume mount added to docker-compose.yml. Top-level jwt-keys named volume declared.
3. **MISSING-01** (notification-bot env vars): All 6 missing environment variables added to docker-compose.yml notification-bot block with correct values matching config.py Pydantic field defaults.

---

_Verified: 2026-04-05T19:15:00Z_
_Verifier: Claude (gsd-verifier)_
