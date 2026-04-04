# Feature Research

**Domain:** Real-time notification delivery for university attendance system — WebSocket push + Telegram bot
**Researched:** 2026-04-04
**Project:** RutCampusTrack v5.0 Notification Service (Web + Bot)
**Confidence:** HIGH — based on existing event schemas, RabbitMQ exchange contracts, proto files, phases-plan.md, and PROJECT.md active requirements.

---

## Context: What Already Exists (Must Not Re-Implement)

The notification layer consumes from an already-operational event bus. These are the sources it reads from:

| Event | Publisher | Payload Fields | Notification Audience |
|-------|-----------|---------------|----------------------|
| `lesson.started` | Schedule Service | lesson_id, group_id, subject_id, teacher_id, lesson_number, start_time, end_time, room | Students of that group |
| `lesson.closed` | Schedule Service | lesson_id, group_id, subject_id | Students of that group |
| `lesson.cancelled` | Schedule Service | lesson_id, group_id, subject_id, date, cancel_reason | Students of that group |
| `attendance.marked` | Attendance Service | lesson_id, user_id, group_id, status, marked_by | That specific student |
| `homework.published` | Academic Service | homework_id, group_id, subject_id, lesson_id, title, has_link | Students of that group |
| `homework.updated` | Academic Service | same as published | Students of that group |
| `excuse.requested` | Attendance Service | user_id, group_id, excuse_type, ticket_id, lesson_ids, has_attachments | Headman of that group |
| `late_checkin.requested` | Attendance Service | user_id, group_id, lesson_id, student_name, lesson_date | Headman of that group |

**Already operational infrastructure:**
- RabbitMQ fanout exchange `rut-uit.events` — all events land here
- Academic Service gRPC `GetGroupMembers(group_id)` — returns list of students with telegram_id
- Auth Service OTP flow — `/auth/otp/request` and `/auth/otp/verify` — JWT generation from Telegram
- Redis — available for reminder message_id storage
- API Gateway WebSocket routing: `/api/ws/**` → notification-web:9094

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features the notification system cannot ship without. Missing = core notification pipeline is broken.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **RabbitMQ consumer setup (both services)** | Without consuming events, neither service does anything. Two independent durable queues: `notification-web.events` and `notification-bot.events` must bind to the fanout exchange. | LOW | Same pattern as Attendance Service's `attendance-service.events`. Durable queue, DLQ. Each service receives a full copy of every event. Route by `event_type` field. |
| **WebSocket endpoint with JWT auth** | Web panel needs real-time push without polling. Gateway already routes `/api/ws/**`. Without the WebSocket endpoint, the Angular panel has no live feed. | MEDIUM | STOMP over SockJS is the Spring standard pattern. On handshake: extract JWT from query param or first message frame, parse user_id and group_id from claims. Maintain a session registry keyed by group_id for broadcast. |
| **Group-based WebSocket session registry** | Without routing by group_id, every connected client receives every notification — a privacy and noise violation. | MEDIUM | In-memory ConcurrentHashMap: group_id → Set of WebSocket sessions. On lesson.started → push only to sessions whose group_id matches. On connect/disconnect: add/remove from registry. |
| **lesson.started → WebSocket push to group** | Web panel users (headmen, teachers with the panel open) expect to see "class started" update live. | LOW | Map event → message object {type: "lesson.started", lesson_id, subject_id, lesson_number}. Push to all sessions in group_id. |
| **lesson.cancelled → WebSocket push to group** | Cancelled lessons must appear immediately in the schedule view without page refresh. | LOW | Same as lesson.started mapping but type = "lesson.cancelled". Include cancel_reason if present. |
| **homework.published → WebSocket push to group** | Students on the web panel should see new assignments immediately. | LOW | Push {type: "homework.published", homework_id, title, subject_id}. Route to group_id sessions. |
| **Telegram bot /start command** | Required for account linking. Without /start the bot has no user context. Sends the user's initial login + password if `initial_password` is set (one-time credential delivery). | MEDIUM | On /start: store telegram_id from update. Call Auth Service (or query Academic Service gRPC GetUserByTelegramId if available). If user found and `initial_password` not null → reply with credentials. If not linked → prompt to use /login. |
| **Telegram bot /login command (OTP flow)** | Students need to authenticate with the bot to receive personalized notifications. /login triggers OTP flow via Auth Service. | MEDIUM | /login → POST /auth/otp/request with {telegram_id}. Auth Service sends 6-digit code to bot via... wait: OTP delivery TO Telegram is the bot's job (not Auth Service pushing). Bot sends the OTP code itself after receiving it from Auth Service response or stores a pending state. Check: Auth Service OTP request returns the code for the bot to deliver, or fires a Telegram message independently. This is a v1.0 deferred decision — verify with Auth Service implementation. |
| **lesson.started → Telegram message with check-in button to group students** | Primary student-facing notification. Students see "Pair started, tap to check in" with inline button opening Mini App. Without this, students miss check-in window entirely. | MEDIUM | For each telegram_id in group (via GetGroupMembers gRPC, filtered to has telegram_id): send message with InlineKeyboardMarkup containing one button: "Отметиться" with URL = Mini App URL + ?lesson_id=X. Store returned message_id in Redis key `reminder:msgs:{lesson_id}:{user_id}` for later cleanup. |
| **lesson.closed → delete all reminder messages for that lesson** | Leaving stale reminder messages in Telegram chats is bad UX and was explicitly called out in CLAUDE.md. After lesson closes, all "Отметиться" messages must be deleted. | MEDIUM | On lesson.closed: for each student in group, retrieve message_id from Redis `reminder:msgs:{lesson_id}:{user_id}`. Call bot.delete_message(chat_id=telegram_id, message_id=...). Delete the Redis key. Handle "message not found" gracefully (student may have deleted manually). |
| **lesson.cancelled → Telegram notification to group** | Students need to know class is cancelled so they don't show up. | LOW | Send plain text message to each student in group: "Пара {subject} {date} отменена." Include cancel_reason if present. No inline buttons. |
| **homework.published → Telegram notification to group** | Students get homework assignments via Telegram; the bot is the primary notification channel for most students. | LOW | Send message with homework title and subject name. Include "Есть ссылка" indicator if has_link=true. No inline button needed (link is in the web panel). |
| **gRPC client for GetGroupMembers in bot** | Bot needs the list of telegram_ids for a group to broadcast messages. Without this, the bot cannot send lesson.started or homework notifications. | MEDIUM | Python grpcio client for Academic Service. Single RPC: `GetGroupMembers(group_id)` → list of users with telegram_id field. Filter out users where telegram_id is null (not linked). Cache result per group_id with short TTL (5 minutes) to avoid gRPC call on every lesson event. |

---

### Differentiators (Competitive Advantage)

Features that make this notification system substantially more useful than basic event forwarding.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **3-stage reminder lifecycle for lesson attendance** | Students who haven't checked in get reminded at lesson start, lesson midpoint, and lesson end — then messages are cleaned up. This is the core engagement loop: student doesn't miss the window because of a single notification. | HIGH | Stage 1 (lesson.started): send initial "Отметиться" message with button. Stage 2 (lesson mid-point): send second reminder only to users who are NOT yet marked present (requires querying Attendance Service or listening for attendance.marked events to track who has checked in). Stage 3 (lesson end): final reminder. lesson.closed: delete all. Redis stores all 3 message_ids per user per lesson. The mid-point trigger requires a scheduled task in the bot at the time = (start_time + end_time) / 2. |
| **attendance.marked → remove reminder for that specific student** | Once a student checks in, their reminder messages become noise. Removing them immediately after check-in is excellent UX and avoids "I already checked in, why am I still being reminded?" confusion. | MEDIUM | Consume `attendance.marked` events in the bot. When status=present and marked_by=student_geo or headman: delete all reminder message_ids for that user+lesson from Redis and call delete_message. This eliminates stage 2 and 3 reminders for already-marked students. |
| **excuse.requested → headman Telegram notification with approve/reject buttons** | Headman needs to action excuse tickets from within Telegram without opening the web panel. The event schema already includes ticket_id, lesson_ids, has_attachments. | MEDIUM | Send message to headman's telegram_id: "Студент X запросил у.п. [excuse_type]. [Прикреплены файлы]". InlineKeyboardMarkup with "Одобрить" and "Отклонить" buttons with callback_data containing ticket_id. Bot must handle callback_query: POST to Attendance Service REST API to approve/reject. Requires bot to maintain a JWT token (use OTP flow at startup or service account). |
| **late_checkin.requested → headman Telegram notification** | Headman is notified immediately when a student requests retroactive presence confirmation. | LOW | Send message to headman: "Студент {student_name} просит подтвердить присутствие на паре {lesson_date}." Include lesson_id for context. No approve/reject buttons in v5.0 (headman opens web panel to confirm — that workflow is Attendance Service v4.1). |
| **excuse.requested → WebSocket push to headman** | Headman with web panel open sees excuse requests immediately without page refresh. | LOW | In notification-web: when excuse.requested event arrives, look up headman's session by group_id (headman is connected with their group_id). Push {type: "excuse.requested", user_id, excuse_type, ticket_id}. |
| **late_checkin.requested → WebSocket push to headman** | Same as above but for late checkin requests. | LOW | Push {type: "late_checkin.requested", user_id, student_name, lesson_id, lesson_date} to headman session for that group. |
| **homework.updated → WebSocket push** | If headman edits an existing homework, students on web panel see the change live. | LOW | Same as homework.published mapping but type = "homework.updated". |

---

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Notification persistence / history API** | "Show all my notifications" — users expect an inbox | Requires a new database, schema, write path on every event. The notification services are stateless event forwarders by design; adding persistence turns them into a CRUD service with a new data store. | If needed, add a dedicated notification_log collection to MongoDB in a future milestone. For v5.0: WebSocket messages are fire-and-forget, Telegram messages are the persistence (Telegram keeps chat history). |
| **Notification preferences (mute / channel select)** | Users want to mute specific notification types | Requires a preferences store (new table or Redis hash), a REST API to set preferences, and filter logic in both services before every send. Significant scope for a system with 500-5000 users who will mostly want all notifications. | Defer to v5.1+. Document the future preference key pattern: `notif_prefs:{user_id}` in Redis as a hash of event_type → muted boolean. |
| **Delivery guarantees / read receipts** | "Did the user see the notification?" | Telegram does not provide programmatic read receipt data. WebSocket delivery is best-effort (client must be connected). True delivery guarantees require a persistent outbox pattern with retry queue. This is significant infrastructure for a university attendance system at 500-5000 users. | Accept best-effort delivery. RabbitMQ DLQ captures failed processing. Telegram's own "sent" checkmarks are sufficient user-visible confirmation. |
| **Mid-lesson check-in query to Attendance Service from bot** | Stage 2 reminder should only go to students who haven't checked in | Requires the bot to call Attendance Service REST API per student per lesson at the mid-point to filter already-marked students. High coupling between Bot and Attendance Service; introduces a new synchronous dependency from Python to Java. | Track attendance.marked events in Redis within the bot: when attendance.marked event arrives, delete that user's reminder message_ids immediately (the differentiator above). This achieves the same result without a polling query. |
| **Broadcast to ALL students (admin announcements)** | Admin wants to send arbitrary messages via the bot | Expands bot scope from reactive event consumer to proactive messaging platform. Requires a new API, admin auth in the bot, and rate limiting against Telegram's broadcast limits (30 msg/sec). | Out of scope for v5.0. If needed: add a dedicated admin announcement event type and handler in a future milestone. |
| **WebSocket reconnect state recovery** | "I was disconnected, what did I miss?" | Requires event log/buffer per user. The web panel can poll the relevant REST APIs (schedule, homework, attendance reports) on reconnect to get current state. That is the correct pattern for REST + WebSocket hybrid. | On reconnect: Angular panel re-fetches current schedule + attendance state via REST. WebSocket is only for incremental updates while connected. |
| **Inline message editing for reminders** | Edit the reminder message in place instead of sending new ones | Using editMessageText instead of sendMessage + deleteMessage seems cleaner. However: mid-lesson reminders are distinct messages (not edits) because Telegram suppresses notifications for edited messages — users would not see the second and third reminders. | Always send new messages for reminders. Delete old ones via deleteMessage on attendance.marked or lesson.closed. |
| **OTP delivery from Auth Service directly to Telegram** | Simpler if Auth Service calls Telegram API directly for OTP | Auth Service is a pure Java REST+Redis service with no Telegram dependency. Adding Telegram API calls to Auth Service violates service boundary (Auth should not know about Telegram). | Bot calls Auth Service's /auth/otp/request endpoint. Auth Service returns the OTP code (or a success status that the code was stored in Redis). Bot then sends the code as a Telegram message itself. This keeps Auth Service Telegram-free. |

---

## Feature Dependencies

```
[RabbitMQ consumer setup]
    └──required-by──> ALL notification features in both services

[WebSocket endpoint + JWT auth]
    └──required-by──> [Group-based session registry]
                          └──required-by──> ALL WebSocket push features

[lesson.started event]
    └──triggers──> [WebSocket push to group] (notification-web)
    └──triggers──> [Telegram message with check-in button] (notification-bot)
                       └──stores──> message_id in Redis per student
                       └──schedules──> stage 2 reminder at lesson midpoint
                       └──schedules──> stage 3 reminder at lesson end

[attendance.marked event]
    └──triggers──> [Delete reminder for that student] (notification-bot)
                       └──reads──> Redis message_ids
                       └──calls──> bot.delete_message

[lesson.closed event]
    └──triggers──> [Delete ALL reminders for that lesson] (notification-bot)
                       └──reads──> all Redis message_ids for lesson
                       └──calls──> bot.delete_message for each

[lesson.cancelled event]
    └──triggers──> [WebSocket push to group] (notification-web)
    └──triggers──> [Telegram message to group students] (notification-bot)

[homework.published event]
    └──triggers──> [WebSocket push to group] (notification-web)
    └──triggers──> [Telegram message to group students] (notification-bot)

[excuse.requested event]
    └──triggers──> [WebSocket push to headman session] (notification-web)
    └──triggers──> [Telegram message to headman with approve/reject buttons] (notification-bot)

[late_checkin.requested event]
    └──triggers──> [WebSocket push to headman session] (notification-web)
    └──triggers──> [Telegram plain text message to headman] (notification-bot)

[gRPC GetGroupMembers client] (Python bot only)
    └──required-by──> [lesson.started → Telegram broadcast]
    └──required-by──> [lesson.cancelled → Telegram broadcast]
    └──required-by──> [homework.published → Telegram broadcast]

[Telegram bot /start command]
    └──enables──> [telegram_id linked to user_id]
    └──required-for──> ALL bot message delivery (no telegram_id = no messages)

[Telegram bot /login command]
    └──enables──> [JWT for bot to call REST APIs]
    └──required-for──> [excuse approve/reject callback handler]

[Redis reminder storage]
    └──required-by──> [lesson.started → store message_ids]
    └──required-by──> [attendance.marked → delete reminder]
    └──required-by──> [lesson.closed → delete all reminders]
    └──required-by──> [stage 2 + stage 3 scheduled reminders]
```

### Dependency Notes

- **GetGroupMembers gRPC is only needed in the bot, not in notification-web.** Notification-web routes WebSocket messages by group_id already present in the JWT claims of connected sessions — no gRPC needed for the web service.
- **lesson.closed cleanup requires the message_ids stored by lesson.started.** If the Redis key expires or was never set (bot was down when lesson started), the cleanup step must handle missing keys gracefully — log and skip, do not crash.
- **The 3-stage reminder requires asyncio scheduling in Python.** When lesson.started fires, schedule a coroutine at (start_time + end_time) / 2 for stage 2, and at end_time - 5 minutes for stage 3. Use `asyncio.get_event_loop().call_later()` or equivalent. These tasks must be stored in memory per lesson_id so they can be cancelled if lesson.cancelled fires mid-lesson.
- **excuse approve/reject callback requires the bot to hold a JWT.** The bot must authenticate against Auth Service at startup (or use a service account). This is the only place the bot makes authenticated REST calls to another service. This dependency chain (bot → Auth Service → Academic DB) must be accounted for in startup order.
- **homework.updated is lower priority than homework.published** because the event schema fields are identical and the notification text differs only slightly. Both can be handled in the same consumer handler.

---

## MVP Definition

### Launch With (v5.0)

Minimum viable notification system — both channels delivering the core attendance events.

**Notification Web (Java):**
- [ ] RabbitMQ consumer on `notification-web.events` queue — without this, service is inert
- [ ] WebSocket endpoint `/ws` with JWT auth via query param on handshake
- [ ] Group-based session registry (ConcurrentHashMap: group_id → sessions)
- [ ] lesson.started → WebSocket push to group sessions
- [ ] lesson.cancelled → WebSocket push to group sessions
- [ ] homework.published → WebSocket push to group sessions
- [ ] excuse.requested → WebSocket push to headman session (headman has group_id matching group)
- [ ] late_checkin.requested → WebSocket push to headman session

**Notification Bot (Python):**
- [ ] RabbitMQ consumer on `notification-bot.events` queue (aio-pika)
- [ ] /start command — account linking, send initial credentials if initial_password set
- [ ] /login command — OTP flow triggering via Auth Service
- [ ] /status command — current lesson + student's own attendance status (calls APIs)
- [ ] gRPC client for GetGroupMembers (grpcio, Academic Service)
- [ ] lesson.started → send Telegram message with inline "Отметиться" button to group students; store message_ids in Redis
- [ ] lesson.closed → delete all reminder message_ids for that lesson from Redis + Telegram
- [ ] lesson.cancelled → send cancellation text to group students
- [ ] homework.published → send homework notification to group students
- [ ] attendance.marked (status=present) → delete reminder messages for that student immediately

**Shared infrastructure:**
- [ ] Redis key pattern `reminder:msgs:{lesson_id}:{user_id}` for message_id storage

### Add After Validation (v5.1)

- [ ] 3-stage reminder lifecycle (stage 2 at midpoint, stage 3 near end) — requires asyncio scheduling, adds significant complexity
- [ ] excuse.requested → headman Telegram notification with approve/reject callback buttons — requires bot JWT auth + REST call to Attendance Service
- [ ] late_checkin.requested → headman Telegram notification — low complexity, but deferred until excuse workflow is established
- [ ] homework.updated → both channels (identical to published, trivial once published works)
- [ ] Notification preferences (mute by event type) — Redis hash per user

### Future Consideration (v5.2+)

- [ ] Notification history / inbox — requires MongoDB collection, REST API
- [ ] Admin broadcast announcements via bot
- [ ] WebSocket reconnect state recovery (event buffer)
- [ ] Delivery confirmation tracking

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| RabbitMQ consumer infrastructure (both) | HIGH | LOW | P1 — everything else blocks on this |
| WebSocket endpoint + JWT auth | HIGH | MEDIUM | P1 — web panel real-time requires this |
| Group session registry | HIGH | MEDIUM | P1 — prerequisite for any WebSocket routing |
| lesson.started WebSocket push | HIGH | LOW | P1 — primary schedule event |
| lesson.cancelled WebSocket push | HIGH | LOW | P1 — schedule correctness |
| Bot /start + account linking | HIGH | MEDIUM | P1 — without linking, no personalized messages |
| Bot gRPC GetGroupMembers client | HIGH | MEDIUM | P1 — required for all group broadcasts |
| lesson.started → Telegram with check-in button | HIGH | MEDIUM | P1 — primary student engagement |
| lesson.started → store message_ids in Redis | HIGH | LOW | P1 — required for cleanup to work |
| lesson.closed → delete all Telegram reminders | HIGH | MEDIUM | P1 — UX requirement stated in CLAUDE.md |
| attendance.marked → delete student's reminder | HIGH | LOW | P1 — prevents "already checked in" reminder spam |
| lesson.cancelled → Telegram message | HIGH | LOW | P1 — students need to know |
| homework.published → Telegram message | MEDIUM | LOW | P1 — standard student expectation |
| Bot /login OTP flow | MEDIUM | MEDIUM | P1 — required for /status and excuse callbacks |
| Bot /status command | MEDIUM | MEDIUM | P1 — useful for student self-service |
| homework.published WebSocket push | MEDIUM | LOW | P1 — once lesson events work, same pattern |
| excuse.requested WebSocket push | MEDIUM | LOW | P2 — headman workflow enhancement |
| late_checkin.requested WebSocket push | MEDIUM | LOW | P2 — headman workflow enhancement |
| late_checkin.requested Telegram message to headman | MEDIUM | LOW | P2 — low complexity add-on |
| 3-stage reminder lifecycle | HIGH | HIGH | P2 — valuable but complex asyncio scheduling |
| excuse.requested → headman Telegram with buttons | HIGH | HIGH | P2 — requires bot auth + REST integration |
| Notification preferences / mute | MEDIUM | MEDIUM | P3 — defer until user feedback |
| Notification history / inbox | LOW | HIGH | P3 — Telegram IS the history |

**Priority key:**
- P1: Must have for v5.0 — notification system is not functional without these
- P2: High value but can ship in v5.1 iteration
- P3: Nice to have, defer to v5.2+ or later milestone

---

## Event × Service Mapping

Complete picture of which events each service processes and what action it takes:

| Event | notification-web action | notification-bot action |
|-------|------------------------|------------------------|
| `lesson.started` | Push to group sessions: {type, lesson_id, subject_id, lesson_number, start_time, end_time, room} | Send message with inline button to each student in group; store message_id in Redis |
| `lesson.closed` | Push to group sessions: {type, lesson_id} (optional — web panel already knows from schedule state) | Delete all reminder message_ids for lesson from Redis + Telegram |
| `lesson.cancelled` | Push to group sessions: {type, lesson_id, date, cancel_reason} | Send cancellation text to each student in group |
| `attendance.marked` | No action (web panel reads from Attendance Service REST on its own) | If status=present: delete reminder message_ids for that user+lesson |
| `homework.published` | Push to group sessions: {type, homework_id, title, subject_id} | Send homework text to each student in group |
| `homework.updated` | Push to group sessions: {type, homework_id, title, subject_id} | Send update text to each student in group (v5.1) |
| `excuse.requested` | Push to headman session: {type, user_id, excuse_type, ticket_id} | Send message to headman with Approve/Reject buttons (v5.1) |
| `late_checkin.requested` | Push to headman session: {type, user_id, student_name, lesson_id} | Send plain text to headman (v5.1) |
| `semester.archived` | No action (no WebSocket notification needed) | No action |
| `group.updated` | Invalidate session routing if group membership changed (optional) | Refresh GetGroupMembers cache (optional) |

---

## Offline Handling Decisions

| Scenario | WebSocket behavior | Telegram bot behavior |
|----------|-------------------|----------------------|
| Student not connected to web panel | Message is lost — best-effort only. REST APIs are canonical. | Telegram delivers when online (Telegram handles delivery) |
| Bot was down when lesson.started fired | lesson.started consumed when bot restarts (durable queue). But lesson may already be active or closed. | Must check: if lesson is already CLOSED when consuming lesson.started, skip sending reminder messages. Compare occurred_at with current time. |
| Student hasn't done /start (no telegram_id) | N/A | Skip silently. Log at DEBUG. |
| lesson.closed message_id missing from Redis | N/A | Log "no reminder found for user X lesson Y", skip deleteMessage call — do not crash |
| RabbitMQ message unprocessable | AMQP nack → DLQ | Same: nack → DLQ |

---

## Sources

- `.planning/PROJECT.md` — v5.0 active requirements list, target features, milestone context (HIGH confidence — primary source)
- `docs/phases-plan.md` Phase 5 section — detailed feature list for both services (HIGH confidence)
- `event-schemas/*.json` — all 10 event schemas with exact payload fields (HIGH confidence — contract files in codebase)
- `CLAUDE.md` — reminder lifecycle requirement ("3 напоминания об отметке: начало, середина, конец пары. После пары — удалить сообщения"), business rules (HIGH confidence)
- `docs/phase-4-report.md` — what Attendance Service publishes (attendance.marked) and when (HIGH confidence)
- `docs/phase-3-report.md` (referenced via phases-plan) — Schedule Service RabbitMQ event timing (CRON transitions) (HIGH confidence)

---

*Feature research for: Real-time notification delivery — WebSocket (Java) + Telegram bot (Python/Aiogram 3)*
*Researched: 2026-04-04*
