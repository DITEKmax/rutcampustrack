---
phase: 23-bot-telegram-commands
plan: "02"
subsystem: notification-bot
tags: [python, grpc, redis, aiohttp, jwt, bot]
dependency_graph:
  requires: []
  provides:
    - JwtRedisClient (JWT token storage in Redis)
    - ScheduleGrpcClient (gRPC client for Schedule Service)
    - AuthHttpClient (HTTP client for Auth Service OTP)
    - AttendanceHttpClient (HTTP client for Attendance REST via Gateway)
  affects:
    - services/notification-bot/bot/config.py
tech_stack:
  added: []
  patterns:
    - Async gRPC client without caching (ScheduleGrpcClient — active lesson needs fresh data)
    - aiohttp.ClientSession per HTTP client with 10s timeout (fail-fast per T-23-06)
    - Redis JSON storage for JWT pairs with max(expires_in, default_ttl)
key_files:
  created:
    - services/notification-bot/bot/services/jwt_redis_client.py
    - services/notification-bot/bot/services/auth_http_client.py
    - services/notification-bot/bot/services/attendance_http_client.py
    - services/notification-bot/bot/grpc_client/schedule_client.py
    - services/notification-bot/bot/grpc_client/schedule_pb2.py
    - services/notification-bot/bot/grpc_client/schedule_pb2_grpc.py
    - services/notification-bot/tests/test_jwt_redis_client.py
    - services/notification-bot/tests/test_schedule_client.py
  modified:
    - services/notification-bot/bot/config.py
decisions:
  - AuthHttpClient uses 10s timeout (aiohttp.ClientTimeout total=10) per T-23-06 threat mitigation
  - schedule_pb2_grpc.py import fixed from bare `import schedule_pb2` to `from bot.grpc_client import schedule_pb2`
  - JwtRedisClient uses max(expires_in, default_ttl) — ensures refresh token TTL (7 days) wins over access token TTL (900s)
metrics:
  duration: "~4 minutes"
  completed: "2026-04-05"
  tasks_completed: 2
  files_created: 8
  files_modified: 1
  tests_added: 12
---

# Phase 23 Plan 02: Python Infrastructure Clients Summary

**One-liner:** JWT Redis storage, Schedule gRPC client, Auth and Attendance HTTP clients for bot commands with all 12 unit tests passing.

## What Was Built

Four new client classes enabling bot command handlers (Plan 03) to call external services without dealing with protocol details:

1. **JwtRedisClient** (`bot/services/jwt_redis_client.py`) — Stores JWT token pairs in Redis at `bot:jwt:{telegram_id}` as JSON. TTL uses `max(expires_in, 604800)` so refresh token lifetime (7 days) always wins over access token (900s). Graceful error swallowing on Redis failures.

2. **ScheduleGrpcClient** (`bot/grpc_client/schedule_client.py`) — Async gRPC client for Schedule Service. `get_active_lesson(group_id)` converts `NOT_FOUND` gRPC error to `None` return value; all other errors propagate. No caching — active lesson must always return current state.

3. **AuthHttpClient** (`bot/services/auth_http_client.py`) — aiohttp client for Auth Service OTP endpoints. Calls `auth-service:9090` directly (not through Gateway) per D-06. Has 10-second timeout per T-23-06 threat mitigation. `request_otp()` returns OTP code string; `verify_otp()` returns token dict.

4. **AttendanceHttpClient** (`bot/services/attendance_http_client.py`) — aiohttp client calling Attendance REST API via API Gateway with student JWT. Unwraps HATEOAS `_embedded.attendanceRecordEntryList`. Raises `TokenExpiredError` on 401 (distinct from generic HTTP errors).

**Config additions** (`bot/config.py`): Added `schedule_grpc_host/port`, `auth_service_host/port`, `api_gateway_url`, `jwt_key_prefix`, `jwt_ttl`.

**Schedule pb2 codegen**: Generated `schedule_pb2.py` and `schedule_pb2_grpc.py` from `proto/schedule.proto` using `grpcio-tools 1.73.0`. Fixed import in `schedule_pb2_grpc.py` from bare `import schedule_pb2` to `from bot.grpc_client import schedule_pb2`.

## Tests

| File | Tests | Result |
|------|-------|--------|
| test_jwt_redis_client.py | 8 | PASS |
| test_schedule_client.py | 4 | PASS |
| **Total** | **12** | **PASS** |

## Commits

| Task | Commit | Files |
|------|--------|-------|
| Task 1: Config + JwtRedisClient + schedule pb2 | 8818799 | config.py, jwt_redis_client.py, schedule_pb2.py, schedule_pb2_grpc.py, test_jwt_redis_client.py |
| Task 2: ScheduleGrpcClient + AuthHttpClient + AttendanceHttpClient | 1eeff85 | schedule_client.py, auth_http_client.py, attendance_http_client.py, test_schedule_client.py |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Functionality] AuthHttpClient timeout per T-23-06**
- **Found during:** Task 2
- **Issue:** The threat model (T-23-06) listed "Auth HTTP client no timeout" as a `mitigate` disposition. The plan's code snippet did not include a timeout.
- **Fix:** Added `timeout=aiohttp.ClientTimeout(total=10)` to `AuthHttpClient.start()` so auth calls fail fast (10 seconds) instead of hanging for aiohttp's default 300 seconds.
- **Files modified:** services/notification-bot/bot/services/auth_http_client.py
- **Commit:** 1eeff85

## Known Stubs

None — all four clients are fully wired to their respective services with no placeholder logic.

## Threat Flags

None — no new network endpoints or auth paths introduced beyond what the plan's threat model covers.

## Self-Check: PASSED

- services/notification-bot/bot/services/jwt_redis_client.py: FOUND
- services/notification-bot/bot/services/auth_http_client.py: FOUND
- services/notification-bot/bot/services/attendance_http_client.py: FOUND
- services/notification-bot/bot/grpc_client/schedule_client.py: FOUND
- services/notification-bot/bot/grpc_client/schedule_pb2.py: FOUND
- services/notification-bot/bot/grpc_client/schedule_pb2_grpc.py: FOUND
- services/notification-bot/tests/test_jwt_redis_client.py: FOUND
- services/notification-bot/tests/test_schedule_client.py: FOUND
- Commit 8818799: FOUND
- Commit 1eeff85: FOUND
- All 12 tests: PASS
