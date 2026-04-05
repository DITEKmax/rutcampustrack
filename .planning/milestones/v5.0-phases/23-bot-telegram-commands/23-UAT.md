---
status: complete
phase: 23-bot-telegram-commands
source: [23-01-SUMMARY.md, 23-02-SUMMARY.md, 23-03-SUMMARY.md]
started: 2026-04-05T15:40:00+03:00
updated: 2026-04-05T16:00:00+03:00
---

## Current Test

[testing complete]

## Tests

### 1. Automated Test Suite (Python)
expected: All 50 notification-bot pytest tests pass (27 existing + 15 handler tests + 8 jwt/schedule client tests).
result: issue — FIXED
reported: "49/50 passed. test_health_up_during_reconnect fails — test mocks only _consumer_task but health_handler now also checks _bot_task (added in Plan 03). Test not updated after bot integration."
severity: major
fix: "Added _bot_task mock (asyncio.sleep(9999)) to test_health_up_during_reconnect and proper cleanup in finally block. 50/50 now pass."

### 2. Automated Test Suite (Java — Academic gRPC)
expected: AcademicGrpcIntegrationTest passes, including GetUserByTelegramId found/not-found cases.
result: pass

### 3. Automated Test Suite (Java — Auth OTP)
expected: OtpIntegrationTest passes, including OTP code returned in response body.
result: pass

### 4. GetUserByTelegramId gRPC RPC
expected: proto/academic.proto contains rpc GetUserByTelegramId with UserByTelegramIdRequest and UserByTelegramIdResponse (11 fields). Implementation returns found=false for unknown telegram_id.
result: pass

### 5. Auth Service OTP code in response body
expected: AuthController.requestOtp() returns OtpCodeResponse(code) in body (not void). Bot can extract OTP code to deliver to user.
result: pass

### 6. Python service clients wired
expected: JwtRedisClient, ScheduleGrpcClient, AuthHttpClient, AttendanceHttpClient all exist in bot/services/ and bot/grpc_client/ with proper config, timeouts (10s on AuthHTTP), and error handling.
result: pass

### 7. /start handler — unknown user
expected: Sends "Ваш Telegram не привязан к системе. Обратитесь к старосте..." when telegram_id not found in academic DB.
result: pass

### 8. /start handler — first login
expected: Shows login + initial_password when user found and initial_password is set.
result: pass

### 9. /start handler — returning user
expected: Shows login + group_name when password already changed (initial_password empty).
result: pass

### 10. /login handler — OTP flow
expected: Sends OTP request to Auth Service, sets FSM state waiting_for_code. User enters 6-digit code, JWT saved to Redis. Already-authenticated users see "Вы уже вошли".
result: pass

### 11. /status handler — no active lesson
expected: "Нет активной пары." when no lesson is currently active for student's group.
result: pass

### 12. /status handler — with active lesson
expected: Shows subject name + attendance status in Russian. Handles present/absent/excused/free_attendance. On 401 deletes JWT and prompts /login.
result: pass

### 13. Bot+Dispatcher integration in __main__.py
expected: Bot and Dispatcher created, three routers registered, clients injected via dp[key]. Co-supervision with asyncio.wait(FIRST_EXCEPTION).
result: pass

### 14. Health check covers both tasks
expected: /health returns DOWN if either _consumer_task or _bot_task is dead. Returns UP when both alive.
result: pass

## Summary

total: 14
passed: 13
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- truth: "All 50 notification-bot pytest tests pass"
  status: failed
  reason: "User reported: 49/50 passed. test_health_up_during_reconnect fails — test mocks only _consumer_task but health_handler now also checks _bot_task (added in Plan 03). Test not updated after bot integration."
  severity: major
  test: 1
  artifacts: []
  missing: []
