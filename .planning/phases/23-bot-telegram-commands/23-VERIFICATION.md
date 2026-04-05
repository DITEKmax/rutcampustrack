---
phase: 23-bot-telegram-commands
verified: 2026-04-05T16:00:00Z
status: human_needed
score: 3/3 must-haves verified
gaps: []
human_verification:
  - test: "Send /start to the bot from a Telegram account linked to a student"
    expected: "Bot replies with greeting, login, and initial_password (first login) or login and group name (password changed)"
    why_human: "Requires live Telegram Bot API and running Academic Service with seeded data"
  - test: "Send /login, receive OTP code, enter it"
    expected: "Bot delivers OTP code, accepts it, confirms successful login"
    why_human: "Requires running Auth Service, Redis, and live Telegram message flow"
  - test: "Send /status during an active lesson after /login"
    expected: "Bot shows subject name, room, time range, and attendance status"
    why_human: "Requires Schedule Service, Attendance Service, API Gateway, and active lesson in database"
  - test: "Send /start from an unlinked Telegram account"
    expected: "Bot replies with instruction to contact headman"
    why_human: "Requires live Telegram Bot API"
---

# Phase 23: Bot Telegram Commands Verification Report

**Phase Goal:** Students can link their Telegram account to the system and authenticate via OTP, and can check their current attendance status -- all three bot commands work end-to-end through the existing Auth Service
**Verified:** 2026-04-05T16:00:00Z
**Status:** human_needed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A student who sends /start and has an existing account linked to their telegram_id receives a greeting with credentials; unknown user sees headman instructions | VERIFIED | `start.py` calls `academic_client.get_user_by_telegram_id()`, checks `response.found`, branches on `response.initial_password` for first-login vs returning user. Proto `UserByTelegramIdResponse` has all 11 fields. Java `AcademicGrpcServiceImpl.getUserByTelegramId()` queries DB via `fetchUserByTelegramId()` and returns full user data with group name lookup. |
| 2 | A student can authenticate via /login by completing the OTP flow -- bot requests OTP, student enters code, bot confirms login | VERIFIED | `login.py` implements FSM with `LoginStates.waiting_for_code`. `cmd_login` calls `auth_client.request_otp()` which hits `POST /auth/otp/request` (now returns `{"code":"..."}` via `OtpCodeResponse`). `process_otp_code` validates 6-digit format, calls `auth_client.verify_otp()`, saves JWT to Redis via `jwt_redis.save()`. OtpService returns `String`, AuthController returns `ResponseEntity<OtpCodeResponse>`. |
| 3 | A student who sends /status receives current lesson info and attendance status | VERIFIED | `status.py` checks JWT from Redis, looks up user via academic gRPC for group_id, calls `schedule_client.get_active_lesson(group_id)`, resolves subject name via `get_subjects_by_ids()`, fetches attendance records via `attendance_client.get_student_records()` with JWT, matches by `lessonId`, and formats Russian status labels. Handles no-JWT, no-lesson, token-expired cases. |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `proto/academic.proto` | GetUserByTelegramId RPC + messages | VERIFIED | Contains `rpc GetUserByTelegramId`, `UserByTelegramIdRequest` (telegram_id), `UserByTelegramIdResponse` (11 fields: found, user_id, login, display_name, role, group_id, group_name, is_headman, telegram_id, initial_password, password_changed) |
| `AcademicGrpcServiceImpl.java` | getUserByTelegramId implementation | VERIFIED | Lines 214-249: full implementation with found/not-found, group name lookup, null-safe fields, initial_password handling |
| `AcademicReadService.java` | fetchUserByTelegramId method | VERIFIED | `public Optional<User> fetchUserByTelegramId(Long telegramId)` delegates to `userRepository.findByTelegramId()` |
| `OtpCodeResponse.java` | OTP code response DTO | VERIFIED | `public record OtpCodeResponse(String code)` at auth-service dto package |
| `AuthController.java` | Returns OtpCodeResponse | VERIFIED | `ResponseEntity<OtpCodeResponse> requestOtp(...)` returns `new OtpCodeResponse(code)` |
| `OtpService.java` | Returns String code | VERIFIED | `public String requestOtp(OtpRequest request)` with `return code;` at line 82 |
| `bot/services/jwt_redis_client.py` | JWT token storage in Redis | VERIFIED | `class JwtRedisClient` with save/get/delete, key pattern `bot:jwt:{telegram_id}`, JSON storage, TTL with max(expires_in, default) |
| `bot/services/auth_http_client.py` | HTTP client for Auth OTP | VERIFIED | `class AuthHttpClient` with `request_otp()` and `verify_otp()`, 10s timeout, calls `/auth/otp/request` and `/auth/otp/verify` |
| `bot/services/attendance_http_client.py` | HTTP client for Attendance REST | VERIFIED | `class AttendanceHttpClient` with `get_student_records()`, HATEOAS unwrap, `TokenExpiredError` on 401, calls `/api/attendance/reports/student/records` |
| `bot/grpc_client/schedule_client.py` | gRPC client for Schedule | VERIFIED | `class ScheduleGrpcClient` with `get_active_lesson()`, NOT_FOUND -> None, no caching |
| `bot/grpc_client/academic_client.py` | get_user_by_telegram_id method | VERIFIED | New methods `get_user_by_telegram_id()` and `get_subjects_by_ids()` added, use proto stubs |
| `bot/handlers/start.py` | /start command handler | VERIFIED | `cmd_start` with account linking logic, first-login vs returning user branching |
| `bot/handlers/login.py` | /login command with FSM | VERIFIED | `LoginStates`, `cmd_login`, `process_otp_code` with full OTP flow and JWT storage |
| `bot/handlers/status.py` | /status command handler | VERIFIED | `cmd_status` with JWT check, user lookup, lesson lookup, subject resolve, attendance fetch, Russian labels |
| `bot/__main__.py` | Bot+Dispatcher integration | VERIFIED | `create_clients()` factory, dp[] DI for all 5 clients, 3 routers registered via `include_router()`, `dp.start_polling(bot, handle_signals=False)`, `asyncio.wait(FIRST_EXCEPTION)` co-supervision, graceful cleanup |
| `bot/handlers/__init__.py` | Router exports | VERIFIED | Exports `start_router`, `login_router`, `status_router` |
| `bot/config.py` | New config fields | VERIFIED | Has `schedule_grpc_host/port`, `auth_service_host/port`, `api_gateway_url`, `jwt_key_prefix`, `jwt_ttl` |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| start.py | AcademicGrpcClient | `academic_client` DI via dp[] | WIRED | Handler receives `academic_client` param, calls `get_user_by_telegram_id()`; injected in `__main__.py` via `dp["academic_client"]` |
| login.py | AuthHttpClient + JwtRedisClient | `auth_client` + `jwt_redis` DI | WIRED | Handler receives both via params, calls `request_otp()`, `verify_otp()`, `jwt_redis.save()` |
| status.py | ScheduleGrpcClient + AttendanceHttpClient + AcademicGrpcClient | DI via dp[] | WIRED | Handler receives `schedule_client`, `attendance_client`, `academic_client` and calls all three |
| __main__.py | Dispatcher + routers | `dp.include_router()` + `dp.start_polling()` | WIRED | All 3 routers registered, bot polling started as asyncio task |
| AcademicGrpcServiceImpl | UserRepository.findByTelegramId() | AcademicReadService.fetchUserByTelegramId() | WIRED | Line 217 calls fetchUserByTelegramId, which calls findByTelegramId |
| AuthController.requestOtp() | OtpService.requestOtp() | Returns String code wrapped in OtpCodeResponse | WIRED | Controller calls service, wraps in DTO, returns in ResponseEntity |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| start.py | `response` (UserByTelegramIdResponse) | academic_client -> gRPC -> AcademicGrpcServiceImpl -> UserRepository.findByTelegramId() -> PostgreSQL | Yes (DB query) | FLOWING |
| login.py | `code` from request_otp | auth_client -> HTTP -> AuthController -> OtpService -> Redis + return code | Yes (generated OTP) | FLOWING |
| login.py | `tokens` from verify_otp | auth_client -> HTTP -> AuthController -> OtpService.verifyOtp -> JWT generation | Yes (real JWT) | FLOWING |
| status.py | `lesson` from get_active_lesson | schedule_client -> gRPC -> ScheduleGrpcServiceImpl -> DB query | Yes (DB query) | FLOWING |
| status.py | `records` from get_student_records | attendance_client -> HTTP -> API Gateway -> Attendance Service -> MongoDB query | Yes (DB query) | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED (requires running Telegram Bot API, gRPC services, and databases -- no runnable entry points in offline mode)

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| BOT-01 | 23-01, 23-03 | Student can link Telegram account via /start and receive initial credentials if set | SATISFIED | start.py handler with full account linking logic; GetUserByTelegramId gRPC RPC implemented in Academic Service; proto contains all required fields |
| BOT-02 | 23-01, 23-02, 23-03 | Student can authenticate via /login using OTP flow through Auth Service | SATISFIED | login.py with FSM, AuthHttpClient calls OTP endpoints, OtpService returns code, JwtRedisClient stores tokens |
| BOT-03 | 23-02, 23-03 | Student can check attendance status via /status command | SATISFIED | status.py with full flow: JWT check -> user lookup -> active lesson -> subject resolve -> attendance fetch -> Russian formatting |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none found) | — | — | — | No TODOs, FIXMEs, placeholders, or stub implementations detected in any phase 23 files |

### Human Verification Required

### 1. /start with linked account (first login)

**Test:** Send /start from a Telegram account whose telegram_id is in the academic_db with initial_password set
**Expected:** Bot replies with greeting, login, and initial password
**Why human:** Requires live Telegram Bot API, running Academic Service, and seeded database

### 2. /start with unlinked account

**Test:** Send /start from a Telegram account not in the system
**Expected:** Bot replies: "Ваш Telegram не привязан к системе. Обратитесь к старосте вашей группы для привязки аккаунта."
**Why human:** Requires live Telegram Bot API

### 3. /login OTP flow end-to-end

**Test:** Send /login, receive OTP code in bot message, enter the code
**Expected:** Bot delivers OTP code, accepts entered code, confirms "Вы успешно вошли в систему!"
**Why human:** Requires running Auth Service with Redis, live Telegram message flow, and the full OTP pipeline

### 4. /status during active lesson

**Test:** After /login, send /status during an active lesson
**Expected:** Bot shows subject name, room, time range, and attendance status in Russian
**Why human:** Requires Schedule Service, Attendance Service, API Gateway all running with active lesson in DB

### Gaps Summary

No code-level gaps found. All artifacts exist, are substantive (no stubs or placeholders), are properly wired through dependency injection, and data flows from real sources (DB queries, gRPC calls, HTTP endpoints) through to user-visible output. All 50 Python tests and Java tests pass per the execution summaries.

The only remaining verification is live end-to-end testing with the full microservice stack and Telegram Bot API, which cannot be performed programmatically.

---

_Verified: 2026-04-05T16:00:00Z_
_Verifier: Claude (gsd-verifier)_
