---
phase: 23-bot-telegram-commands
plan: "01"
subsystem: academic-service, auth-service
tags: [grpc, otp, bot, telegram, java]
dependency_graph:
  requires: []
  provides:
    - GetUserByTelegramId gRPC RPC in academic.proto
    - OtpCodeResponse DTO and Auth Service OTP code return
  affects:
    - services/notification-bot (consumes both)
    - proto/academic.proto (extended)
tech_stack:
  added: []
  patterns:
    - gRPC found/not-found response pattern (no exception on missing)
    - OTP code returned in HTTP response body for bot delivery
key_files:
  created:
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/OtpCodeResponse.java
  modified:
    - proto/academic.proto
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicGrpcIntegrationTest.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/service/OtpService.java
    - services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java
    - services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/OtpIntegrationTest.java
decisions:
  - "GetUserByTelegramId uses found=false pattern (not gRPC NOT_FOUND status) — bot needs to handle unknown users gracefully without exception handling overhead"
  - "fetchUserByTelegramId not cached in AcademicReadService — bot /start needs fresh data to detect first-login vs returning user"
  - "OTP code returned directly in response body — bot is the caller, not end user; internal network only"
metrics:
  duration_minutes: 15
  completed_date: "2026-04-05"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 7
---

# Phase 23 Plan 01: Java-side Bot Infrastructure Summary

Java-side changes for bot commands: GetUserByTelegramId gRPC RPC added to Academic Service proto and implemented with found/not-found semantics; Auth Service OTP request now returns code in response body as `{"code":"123456"}` for bot delivery.

## Tasks Completed

| # | Task | Commit | Key Files |
|---|------|--------|-----------|
| 1 | Add GetUserByTelegramId RPC to academic.proto and implement in Java | a2fed25 | proto/academic.proto, AcademicGrpcServiceImpl.java, AcademicReadService.java, AcademicGrpcIntegrationTest.java |
| 2 | Modify Auth Service OTP request to return code in response body | 1b2c3f5 | OtpCodeResponse.java, OtpService.java, AuthController.java, OtpIntegrationTest.java |

## What Was Built

### Task 1: GetUserByTelegramId gRPC RPC

Added to `proto/academic.proto`:
- `UserByTelegramIdRequest` message with `int64 telegram_id`
- `UserByTelegramIdResponse` message with 11 fields: found, user_id, login, display_name, role, group_id, group_name, is_headman, telegram_id, initial_password, password_changed
- `rpc GetUserByTelegramId` in `AcademicGrpcService`

Implementation in `AcademicGrpcServiceImpl.java`:
- Returns `found=false` with empty response when no user has given telegram_id
- Returns full user data including group name lookup when user found
- Null-safe handling for optional fields (groupId, telegramId, initialPassword)

`AcademicReadService.fetchUserByTelegramId()` added as non-cached method (fresh data needed for bot /start first-login detection).

Two integration tests added covering found and not-found cases.

### Task 2: Auth Service OTP Response Body

- New `OtpCodeResponse(String code)` record in `ru.rutcampustrack.auth.dto`
- `OtpService.requestOtp()` changed from `void` to `String` return type
- `AuthController.requestOtp()` changed from `ResponseEntity<Void>` to `ResponseEntity<OtpCodeResponse>`
- Existing integration tests updated to assert code in response body and use `OtpCodeResponse.class`

## Verification

- `gradlew.bat :services:academic-service:academic-app:test --tests "*AcademicGrpc*"` — BUILD SUCCESSFUL (all 12+ tests pass)
- `gradlew.bat :services:auth-service:test --rerun-tasks` — BUILD SUCCESSFUL (all tests pass)
- `proto/academic.proto` contains `rpc GetUserByTelegramId (UserByTelegramIdRequest) returns (UserByTelegramIdResponse)`

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all fields wired to real data sources.

## Threat Flags

No new security surface beyond what was documented in the plan's threat model. `initial_password` is only accessible via internal gRPC (not exposed via REST). OTP code is only returned to the internal bot service on the Docker network.

## Self-Check: PASSED

- `proto/academic.proto` — FOUND with GetUserByTelegramId RPC and both message types
- `AcademicGrpcServiceImpl.java` — FOUND with getUserByTelegramId implementation
- `AcademicReadService.java` — FOUND with fetchUserByTelegramId method
- `OtpCodeResponse.java` — FOUND at services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/OtpCodeResponse.java
- Commit a2fed25 — FOUND (Task 1)
- Commit 1b2c3f5 — FOUND (Task 2)
