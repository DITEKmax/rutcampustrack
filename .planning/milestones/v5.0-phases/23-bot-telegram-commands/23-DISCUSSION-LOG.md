# Phase 23: Bot Telegram Commands - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-05
**Phase:** 23-bot-telegram-commands
**Areas discussed:** Account linking (/start), OTP login flow (/login), /status command design, Error handling & edge cases

---

## Account Linking (/start)

### User Lookup by Telegram ID

| Option | Description | Selected |
|--------|-------------|----------|
| Add GetUserByTelegramId gRPC | New RPC in academic.proto — cleanest approach, direct lookup | ✓ |
| REST call to Auth Service | Call POST /auth/otp/request to check existence — hacky, rate limit side effects | |
| You decide | Claude picks the best technical approach | |

**User's choice:** Add GetUserByTelegramId gRPC (Recommended)

### Known User Greeting

| Option | Description | Selected |
|--------|-------------|----------|
| Name + group + login | Show display_name, group name, and login | |
| Name + group only | Show display_name and group name, no login | |
| Just name | Simple greeting with display_name only | |

**User's choice:** Custom — show login + initial_password if not null (first login), login + group if initial_password is null (already changed password)
**Notes:** Corrected mid-discussion. The /start greeting serves as the primary onboarding path for students.

### gRPC Response Fields

| Option | Description | Selected |
|--------|-------------|----------|
| Include initial_password in gRPC response | Add initial_password + password_changed fields to TelegramUserResponse. One call gets everything. | ✓ |
| Separate REST call | Bot calls Academic REST API separately. More network calls. | |

**User's choice:** Include in gRPC response (Recommended)

### Unknown User Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Contact headman message | "Your Telegram is not linked. Contact your headman." | ✓ |
| Self-registration with login | Ask user to enter student login, auto-link | |
| You decide | Claude picks the approach | |

**User's choice:** Contact headman message

---

## OTP Login Flow (/login)

### OTP Code Delivery

| Option | Description | Selected |
|--------|-------------|----------|
| Return OTP in response body | Modify Auth Service to return {"code": "123456"}. Simple, minimal change. | ✓ |
| Bot reads OTP from Redis directly | Bot connects to Auth Redis — couples bot to Auth internals | |
| Auth Service sends via bot API | Auth Service calls Telegram Bot API — coupling concern | |

**User's choice:** Return OTP in response body (Recommended)

### Conversation State Management

| Option | Description | Selected |
|--------|-------------|----------|
| Aiogram FSM | Aiogram 3 built-in FSM with MemoryStorage. States: waiting_for_code. | ✓ |
| Redis-based state | Store conversation state in Redis with TTL. Survives restart. | |
| You decide | Claude picks the best approach | |

**User's choice:** Aiogram FSM (Recommended)

### Auth Service Access Path

| Option | Description | Selected |
|--------|-------------|----------|
| Direct REST to Auth Service | Bot calls auth-service:9090 directly. Internal, no JWT needed. | ✓ |
| Through API Gateway | Bot calls gateway:8080/api/auth/otp/*. Consistent but unnecessary hop. | |

**User's choice:** Direct REST to Auth Service (Recommended)

---

## /status Command Design

### Attendance Data Access

| Option | Description | Selected |
|--------|-------------|----------|
| REST with user's JWT | Bot stores JWT from /login. Calls Attendance REST via Gateway with student's token. | ✓ |
| New internal gRPC on Attendance | Add gRPC endpoint. Clean but requires new proto + implementation. | |
| You decide | Claude picks the simplest approach | |

**User's choice:** REST with user's JWT (Recommended)

### Status Message Content

| Option | Description | Selected |
|--------|-------------|----------|
| Current lesson + status | Subject name, room, time, attendance status. No active lesson: "Нет активной пары." | ✓ |
| Current lesson + today's summary | Current lesson plus all today's lessons and statuses | |
| You decide | Claude picks the right level of detail | |

**User's choice:** Current lesson + status (Recommended)

### JWT Token Storage

| Option | Description | Selected |
|--------|-------------|----------|
| Redis with TTL | Store JWT pair in Redis keyed by telegram_id. Survives restart, auto-expires. | ✓ |
| In-memory dict | Simple dict, lost on restart. Simplest implementation. | |
| You decide | Claude picks the best approach | |

**User's choice:** Redis with TTL (Recommended)

---

## Error Handling & Edge Cases

### Error Message Style

| Option | Description | Selected |
|--------|-------------|----------|
| User-friendly Russian messages | Short Russian messages, no technical details | ✓ |
| Bilingual (Russian + English) | Russian primary with English fallback for technical errors | |
| You decide | Claude decides error message style | |

**User's choice:** User-friendly Russian messages (Recommended)

### Bot Language

| Option | Description | Selected |
|--------|-------------|----------|
| Russian only | All bot messages in Russian — target audience is RUT MIIT students | ✓ |
| English only | Keep everything in English for code consistency | |
| You decide | Claude picks based on project context | |

**User's choice:** Russian only

---

## Claude's Discretion

- Aiogram router/handler file organization
- Schedule gRPC client wrapper class
- aiohttp client session management
- FSM state class design and timeout handling
- Message formatting (plain text vs Markdown vs HTML)
- JWT auto-refresh on expiry vs ask to /login again

## Deferred Ideas

None — discussion stayed within phase scope.
