# Phase 23: Bot Telegram Commands - Context

**Gathered:** 2026-04-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Three Telegram bot commands work end-to-end: /start links a Telegram account and shows credentials, /login authenticates via OTP flow through Auth Service, /status shows current lesson and attendance status. All messages are in Russian. Requires a new `GetUserByTelegramId` gRPC RPC in Academic Service and a minor Auth Service change (return OTP code in response body).

</domain>

<decisions>
## Implementation Decisions

### Account Linking (/start)
- **D-01:** Add new `GetUserByTelegramId` gRPC RPC to Academic Service's `academic.proto`. Response includes `user_id`, `login`, `display_name`, `role`, `group_id`, `group_name`, `is_headman`, `telegram_id`, `initial_password` (nullable), `password_changed` (bool).
- **D-02:** On /start with known telegram_id: if `initial_password` is not null (first login) — show login + initial_password. If `initial_password` is null (already changed password) — show login + group name.
- **D-03:** On /start with unknown telegram_id: "Ваш Telegram не привязан к системе. Обратитесь к старосте вашей группы для привязки аккаунта."

### OTP Login Flow (/login)
- **D-04:** Modify Auth Service `POST /auth/otp/request` to return OTP code in response body: `{"code": "123456"}` instead of empty 200. Bot delivers code to user in Telegram message.
- **D-05:** Use Aiogram 3 FSM (FiniteStateMachine) with MemoryStorage for multi-step /login conversation state. States: `waiting_for_code`. FSM state auto-clears on timeout or successful verify.
- **D-06:** Bot calls Auth Service directly at `auth-service:9090` (internal service-to-service), not through API Gateway. OTP endpoints are public (no JWT required).
- **D-07:** On successful OTP verify, store JWT pair (access + refresh tokens) in Redis keyed by `bot:jwt:{telegram_id}` with TTL matching token expiry. Survives bot restart.

### /status Command
- **D-08:** Bot calls Schedule Service gRPC `GetActiveLesson(group_id, timestamp)` to find current lesson. If no active lesson: "Нет активной пары."
- **D-09:** Bot calls Attendance Service REST `GET /api/attendance/reports/student/records` via API Gateway using the student's stored JWT to get attendance status for the current lesson.
- **D-10:** /status message shows: subject name, room, time range, attendance status (present/absent/not marked). Single current lesson only, no today summary.
- **D-11:** If user has no stored JWT (not logged in via /login), prompt them: "Сначала войдите через /login."

### Error Handling
- **D-12:** All bot messages in Russian — target audience is RUT MIIT students.
- **D-13:** User-friendly error messages without technical details. Examples: "Код неверный. Попробуйте ещё раз.", "Нет активной пары.", "Обратитесь к старосте.", "Слишком много попыток. Подождите."
- **D-14:** On service unavailability (gRPC/REST call fails): "Сервис временно недоступен. Попробуйте позже." Log full error details at WARNING level.

### Claude's Discretion
- Aiogram router/handler file organization within `bot/handlers/`
- Schedule gRPC client wrapper class (new, or extend existing academic_client pattern)
- aiohttp client session management for REST calls to Auth/Attendance
- Exact FSM state class design and timeout handling
- Message formatting (plain text vs Markdown vs HTML)
- /status response when JWT is expired (auto-refresh or ask to /login again)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Bot Existing Code (Phase 22)
- `services/notification-bot/bot/__main__.py` — Entry point with health server and watchdog
- `services/notification-bot/bot/config.py` — Pydantic Settings with all service connection params
- `services/notification-bot/bot/handlers/__init__.py` — Empty handlers package (target for new command handlers)
- `services/notification-bot/bot/grpc_client/academic_client.py` — Async gRPC client pattern (reuse for schedule client)
- `services/notification-bot/bot/services/redis_client.py` — Redis async client (reuse for JWT storage)
- `services/notification-bot/bot/services/send_queue.py` — Throttled send queue
- `services/notification-bot/requirements.txt` — Current dependencies (aiogram 3.15.0 included)

### gRPC Proto Definitions
- `proto/academic.proto` — Current Academic gRPC (add GetUserByTelegramId RPC here)
- `proto/schedule.proto` — Schedule gRPC with GetActiveLesson RPC

### Auth Service OTP Flow
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java` — OTP endpoints (modify requestOtp return)
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/OtpService.java` — OTP logic (requestOtp returns void → change to return code)
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/OtpRequest.java` — OTP request DTO (telegramId)
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/dto/OtpVerifyRequest.java` — OTP verify DTO (telegramId + code)

### Attendance Service REST API
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ReportApi.java` — GET /reports/student/records endpoint (used by /status)

### Architecture & Decisions
- `docs/architecture.md` — Service topology, communication patterns
- `docs/phases-plan.md` §Фаза 6 — Notification Service detailed plan
- `.planning/phases/22-bot-infrastructure-layer/22-CONTEXT.md` — Infrastructure decisions (watchdog, send queue, gRPC client patterns)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `academic_client.py`: Async gRPC client with 5-min cache — use same pattern for schedule gRPC client
- `redis_client.py`: Redis async client with RPUSH/LRANGE — extend for JWT token storage
- `send_queue.py`: Throttled send queue — all bot replies should go through this
- `config.py`: Pydantic Settings — add auth_service_url and schedule_grpc_host/port

### Established Patterns
- aio-pika `connect_robust` for auto-reconnect
- Pydantic Settings with `.env` file for config
- Event envelope: `{event_type, event_id, occurred_at, payload}`
- grpc.aio persistent channel with cache (academic_client pattern)

### Integration Points
- `bot/handlers/` — new command handlers register with Aiogram Router
- `bot/__main__.py` — add Aiogram Bot + Dispatcher startup alongside existing health server + watchdog
- `proto/academic.proto` — add GetUserByTelegramId RPC (Academic Service must implement)
- Auth Service `requestOtp()` — return OTP code in response body (minor Java change)
- Schedule gRPC — new client in `bot/grpc_client/` for GetActiveLesson
- Attendance REST — new aiohttp client for JWT-authenticated calls

</code_context>

<specifics>
## Specific Ideas

- /start first-login greeting shows initial_password so student can log into web panel — this is the primary onboarding path via Telegram
- JWT stored in Redis allows /status to work across bot restarts without re-login
- Direct Auth Service access (not through gateway) keeps OTP flow fast and avoids gateway auth complexity

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 23-bot-telegram-commands*
*Context gathered: 2026-04-05*
