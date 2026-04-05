---
phase: 22
plan: "02"
subsystem: notification-bot
tags: [grpc, redis, caching, async, python]
dependency_graph:
  requires: [22-01]
  provides: [AcademicGrpcClient, ReminderRedisClient]
  affects: [notification-bot consumers, lesson reminder flow]
tech_stack:
  added: [redis[hiredis]==5.2.1]
  patterns: [in-memory TTL cache, RPUSH/LRANGE reminder list, graceful degradation]
key_files:
  created:
    - services/notification-bot/bot/grpc_client/academic_pb2.py
    - services/notification-bot/bot/grpc_client/academic_pb2_grpc.py
    - services/notification-bot/bot/grpc_client/academic_client.py
    - services/notification-bot/bot/services/redis_client.py
    - services/notification-bot/tests/test_academic_client.py
    - services/notification-bot/tests/test_redis_client.py
  modified:
    - services/notification-bot/requirements.txt
decisions:
  - "Bumped protobuf from 6.30.2 to 6.31.0 to match grpcio-tools 1.73.0 bundled gencode version"
  - "Used relative import fix in generated academic_pb2_grpc.py (from . import academic_pb2)"
  - "AcademicGrpcClient cache is a plain dict[int, tuple[float, list]] — no external dependency"
  - "ReminderRedisClient accepts optional redis_client parameter for test injection with fakeredis"
metrics:
  duration: "~15 minutes"
  completed_date: "2026-04-05"
  tasks_completed: 2
  files_changed: 7
---

# Phase 22 Plan 02: Bot Infrastructure Layer (gRPC + Redis Clients) Summary

**One-liner:** Async gRPC client for AcademicService with 5-minute in-memory cache, plus Redis RPUSH/LRANGE reminder client with graceful ConnectionError degradation — 11 unit tests all green.

## What Was Built

### Task 1: gRPC Async Client

Generated Python stubs from `proto/academic.proto` using grpcio-tools 1.73.0 and fixed the relative import in the generated `academic_pb2_grpc.py` for proper package-level use.

`AcademicGrpcClient` (`bot/grpc_client/academic_client.py`):
- Single channel created at startup (not `async with`) — long-lived connection
- `get_group_members(group_id)` — caches result in `dict[int, tuple[float, list]]` for 300 s
- `invalidate(group_id)` — evicts one group from cache (used when group membership changes)
- `close()` — graceful channel shutdown

### Task 2: Redis Reminder Client

`ReminderRedisClient` (`bot/services/redis_client.py`):
- `add_message_id(lesson_id, user_id, message_id)` — RPUSH + EXPIRE, swallows exceptions
- `get_message_ids(lesson_id, user_id)` — LRANGE 0 -1, casts strings to int, swallows exceptions
- `delete_key(lesson_id, user_id)` — DEL, swallows exceptions
- Accepts `redis_client=` for test injection (fakeredis)

### requirements.txt Updates

- Added `redis[hiredis]==5.2.1`
- Bumped `protobuf==6.30.2` → `protobuf==6.31.0` (grpcio-tools 1.73.0 generates gencode 6.31.0; runtime must be >= gencode version)

## Test Results

```
tests/test_academic_client.py::test_get_group_members        PASSED
tests/test_academic_client.py::test_cache_hit                PASSED
tests/test_academic_client.py::test_cache_expired            PASSED
tests/test_academic_client.py::test_cache_invalidate         PASSED
tests/test_academic_client.py::test_grpc_error_propagates    PASSED
tests/test_redis_client.py::test_add_and_get_order           PASSED
tests/test_redis_client.py::test_get_empty                   PASSED
tests/test_redis_client.py::test_delete_key                  PASSED
tests/test_redis_client.py::test_ttl_set                     PASSED
tests/test_redis_client.py::test_redis_down_add_graceful     PASSED
tests/test_redis_client.py::test_redis_down_get_graceful     PASSED

11 passed in 0.10s
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Bumped protobuf pin from 6.30.2 to 6.31.0**
- **Found during:** Task 1 test run
- **Issue:** grpcio-tools 1.73.0 bundles protoc that generates stubs embedding gencode version 6.31.0. The pinned runtime 6.30.2 is older than gencode 6.31.0, triggering `VersionError` on import (`Runtime version cannot be older than the linked gencode version`).
- **Fix:** Updated `requirements.txt` `protobuf==6.30.2` → `protobuf==6.31.0`. This is the minimum runtime version compatible with grpcio-tools 1.73.0 generated stubs.
- **Files modified:** `services/notification-bot/requirements.txt`
- **Commit:** 3e847f1

## Commits

| Hash | Message |
|------|---------|
| 3e847f1 | feat(notification-bot): add async gRPC and Redis clients with caching and graceful degradation |

## Self-Check: PASSED

- `bot/grpc_client/academic_pb2.py` — exists
- `bot/grpc_client/academic_pb2_grpc.py` — exists
- `bot/grpc_client/academic_client.py` — exists
- `bot/services/redis_client.py` — exists
- `tests/test_academic_client.py` — exists
- `tests/test_redis_client.py` — exists
- Commit 3e847f1 — verified via git log
- 11/11 tests passing
