# Roadmap — RutCampusTrack

## Milestones

- ✅ **v1.0 Auth Service + API Gateway** — Phases 1.1-1.4 (shipped 2026-03-30)
- ✅ **v2.0 Academic Service** — Phases 5-9 (shipped 2026-03-31)
- ✅ **v3.0 Schedule Service** — Phases 10-14 (shipped 2026-04-04)
- ✅ **v4.0 Attendance Service MVP** — Phases 15-19 (shipped 2026-04-04)
- 🚧 **v5.0 Notification Service (Web + Bot)** — Phases 20-25 (in progress)

## Phases

<details>
<summary>✅ v1.0 Auth Service + API Gateway (Phases 1.1-1.4) — SHIPPED 2026-03-30</summary>

- [x] Phase 1.1: Auth Service Core — JWT + Login (1/1 plan) — completed 2026-03-28
- [x] Phase 1.2: OTP Flow + Change Password (1/1 plan) — completed 2026-03-29
- [x] Phase 1.3: API Gateway JWT Filter + Routing (1/1 plan) — completed 2026-03-30
- [x] Phase 1.4: Seed Data + Integration Testing (1/1 plan) — completed 2026-03-30

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

<details>
<summary>✅ v2.0 Academic Service (Phases 5-9) — SHIPPED 2026-03-31</summary>

- [x] Phase 5: Entity and Repository Foundation (2/2 plans) — completed 2026-03-30
- [x] Phase 6: REST API + HATEOAS (4/4 plans) — completed 2026-03-30
- [x] Phase 7: gRPC Server (2/2 plans) — completed 2026-03-30
- [x] Phase 8: Redis Caching (2/2 plans) — completed 2026-03-31
- [x] Phase 9: RabbitMQ Events (2/2 plans) — completed 2026-03-31

Full details: `.planning/milestones/v2.0-ROADMAP.md`

</details>

<details>
<summary>✅ v3.0 Schedule Service (Phases 10-14) — SHIPPED 2026-04-04</summary>

- [x] Phase 10: Foundation (2/2 plans) — completed 2026-04-01
- [x] Phase 11: REST API + gRPC Client (3/3 plans) — completed 2026-04-01
- [x] Phase 12: Lesson Auto-Generation (2/2 plans) — completed 2026-04-01
- [x] Phase 13: Status Transitions + RabbitMQ Events (2/2 plans) — completed 2026-04-03
- [x] Phase 14: gRPC Server (2/2 plans) — completed 2026-04-04

Full details: `.planning/milestones/v3.0-ROADMAP.md`

</details>

<details>
<summary>✅ v4.0 Attendance Service MVP (Phases 15-19) — SHIPPED 2026-04-04</summary>

- [x] Phase 15: Infrastructure Foundation (2/2 plans) — completed 2026-04-04
- [x] Phase 16: Event Consumers (2/2 plans) — completed 2026-04-04
- [x] Phase 17: Write Path — Geo-Checkin + Manual Marking (3/3 plans) — completed 2026-04-04
- [x] Phase 18: Read Path — Reports (4/4 plans) — completed 2026-04-04
- [x] Phase 19: Report Security & Routing Fix (1/1 plan) — completed 2026-04-04

Full details: `.planning/milestones/v4.0-ROADMAP.md`

</details>

### 🚧 v5.0 Notification Service (Web + Bot) (In Progress)

**Milestone Goal:** Real-time push notifications via WebSocket (web panel), Web Push (PWA background) and Telegram bot — all three channels consuming RabbitMQ events from existing services. Students receive lesson start buttons and reminders in Telegram; web panel and PWA users receive live WebSocket pushes; PWA users receive Web Push when app is closed. Bot reminder messages are fully cleaned up on lesson close or student checkin.

- [ ] **Phase 20: Shared Infrastructure** — Two durable RabbitMQ queues with DLQ bound to fanout exchange, docker-compose containers, Redis key namespace
- [ ] **Phase 21: Notification Web — WebSocket Core** — STOMP endpoint with JWT auth, group session registry, and all 5 event types pushed to correct group topics
- [ ] **Phase 22: Bot Infrastructure Layer** — aio-pika consumer with watchdog, gRPC client for Academic Service, Redis async client, throttled send queue
- [ ] **Phase 23: Bot Telegram Commands** — /start account linking, /login OTP flow, /status attendance check
- [ ] **Phase 24: Bot Event Notifications** — lesson.started fan-out with inline button, lesson.cancelled, homework published/updated, headman excuse and late-checkin alerts
- [ ] **Phase 25: Bot Reminder Lifecycle** — midpoint and end-of-lesson reminders, full message cleanup on lesson.closed, immediate cleanup on attendance.marked

## Phase Details

### Phase 20: Shared Infrastructure
**Goal**: Both notification services are connected to the event stream — each has a dedicated durable RabbitMQ queue with DLQ bound to the existing fanout exchange, both containers are defined in docker-compose with health checks, and the Redis reminder key namespace is documented and accessible
**Depends on**: Phase 19
**Requirements**: INFRA-01, INFRA-02, INFRA-03
**Success Criteria** (what must be TRUE):
  1. A message published to the rut-uit.events fanout exchange is independently received in both notification-web.events and notification-bot.events queues simultaneously
  2. Stopping notification-web or notification-bot does not affect the other service's queue — messages accumulate in the offline service's queue and are delivered on reconnect
  3. A message that fails processing in either queue is routed to its DLQ rather than silently dropped or infinitely requeued
  4. docker-compose up starts notification-web and notification-bot containers with health checks, and both correctly declare depends_on redis and rabbitmq
**Plans:** 2/3 plans executed
Plans:
- [x] 20-01-PLAN.md — notification-web RabbitConfig + Actuator + EventConsumer + tests
- [x] 20-02-PLAN.md — notification-bot Python skeleton + aio-pika consumer + health endpoint
- [ ] 20-03-PLAN.md — docker-compose containers for both services

### Phase 21: Notification Web — WebSocket Core
**Goal**: Web panel users can receive real-time event pushes over WebSocket — authenticated at handshake via JWT, routed exclusively to their group's topic, receiving structured messages for all 5 event types
**Depends on**: Phase 20
**Requirements**: WS-01, WS-02, WS-03, WS-04, WS-05, WS-06, WS-07
**Success Criteria** (what must be TRUE):
  1. A client can connect to the STOMP WebSocket endpoint by providing a valid JWT at handshake — an invalid or missing token is rejected before the connection is established
  2. When a lesson.started event arrives for group 42, connected clients of group 42 receive a WebSocket push and clients of group 43 receive nothing
  3. A client whose JWT expires while connected continues to receive pushes — group_id and user_id were extracted from JWT claims into session attributes at handshake and are not re-validated
  4. When a lesson is cancelled for a group, all connected group members receive the cancellation push in real time
  5. When homework is published for a group, all connected group members receive the homework push; when an excuse is requested, only the headman's session receives the excuse push
**Plans**: TBD
**UI hint**: yes

### Phase 22: Bot Infrastructure Layer
**Goal**: The notification bot has all three infrastructure clients operational and tested in isolation — aio-pika consumer reconnects reliably after RabbitMQ restart (watchdog), the Academic gRPC client resolves group members asynchronously, Redis stores and retrieves reminder message_ids as lists, and all outgoing messages pass through the throttled send queue
**Depends on**: Phase 20
**Requirements**: BINFRA-01, BINFRA-02, BINFRA-03
**Success Criteria** (what must be TRUE):
  1. After a RabbitMQ container restart, the bot resumes consuming messages within 60 seconds — the watchdog coroutine detects the dead channel and re-establishes the consumer without manual intervention
  2. A call to GetGroupMembers via the gRPC async client returns telegram_ids for all students in a group without blocking the asyncio event loop
  3. Reminder message_ids are stored in Redis as a list (RPUSH) — retrieving all ids for a lesson+user returns every id in insertion order (LRANGE key 0 -1)
  4. When 50 messages are submitted to the throttled send queue simultaneously, they are delivered sequentially at a rate that does not exceed Telegram's 30 msg/sec global limit — no 429 errors are raised
**Plans**: TBD

### Phase 23: Bot Telegram Commands
**Goal**: Students can link their Telegram account to the system and authenticate via OTP, and can check their current attendance status — all three bot commands work end-to-end through the existing Auth Service
**Depends on**: Phase 22
**Requirements**: BOT-01, BOT-02, BOT-03
**Success Criteria** (what must be TRUE):
  1. A student who sends /start to the bot and has an existing account linked to their telegram_id receives a greeting with their credentials; a new user receives instructions to contact their headman
  2. A student can authenticate via /login by completing the OTP flow — the bot requests an OTP from Auth Service, the student enters the code, and the bot confirms successful login
  3. A student who sends /status receives their current lesson information and attendance status for that lesson
**Plans**: TBD

### Phase 24: Bot Event Notifications
**Goal**: Students receive Telegram notifications with inline check-in buttons when lessons start, plain notifications when lessons are cancelled or homework is published/updated, and headmen receive Telegram notifications when students request excuses or late check-ins
**Depends on**: Phase 22
**Requirements**: NOTIF-01, NOTIF-06, NOTIF-07, NOTIF-08, NOTIF-09
**Success Criteria** (what must be TRUE):
  1. When a lesson.started event arrives, every student in the group receives a Telegram message containing an inline button that opens the Mini App check-in flow — the message_id for each student is stored in Redis via RPUSH under reminder:msgs:{lesson_id}:{user_id}
  2. When a lesson.cancelled event arrives, every student in the group receives a plain text cancellation notification via the throttled send queue
  3. When a homework.published or homework.updated event arrives, every student in the group receives a Telegram notification about the new or changed assignment
  4. When an excuse.requested event arrives, the headman of the affected group receives a Telegram notification; same for late_checkin.requested
**Plans**: TBD

### Phase 25: Bot Reminder Lifecycle
**Goal**: Students who have not checked in receive two follow-up reminder messages (at lesson midpoint and near lesson end), all reminder messages are deleted from Telegram when the lesson closes, and a student's reminders are immediately deleted the moment they check in — implementing the full reminder lifecycle from CLAUDE.md
**Depends on**: Phase 24
**Requirements**: NOTIF-02, NOTIF-03, NOTIF-04, NOTIF-05
**Success Criteria** (what must be TRUE):
  1. A student who has not checked in receives a second reminder Telegram message at approximately the lesson midpoint — the new message_id is appended to their Redis list
  2. A student who has not checked in receives a third and final reminder near lesson end — the third message_id is appended to their Redis list
  3. When a lesson.closed event arrives, every reminder message stored in Redis for that lesson is deleted from Telegram (bot.delete_message) and the Redis keys are removed — students see no lingering reminder messages in their chat
  4. When an attendance.marked event arrives with status=present for a student, all reminder messages for that student and that lesson are immediately deleted from Telegram and the Redis key is cleared — students do not receive further reminders after checking in
**Plans**: TBD

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1.1 Auth Service Core | v1.0 | 1/1 | Complete | 2026-03-28 |
| 1.2 OTP + Change Password | v1.0 | 1/1 | Complete | 2026-03-29 |
| 1.3 Gateway JWT Filter | v1.0 | 1/1 | Complete | 2026-03-30 |
| 1.4 Seed Data + Integration Tests | v1.0 | 1/1 | Complete | 2026-03-30 |
| 5. Entity and Repository Foundation | v2.0 | 2/2 | Complete | 2026-03-30 |
| 6. REST API + HATEOAS | v2.0 | 4/4 | Complete | 2026-03-30 |
| 7. gRPC Server | v2.0 | 2/2 | Complete | 2026-03-30 |
| 8. Redis Caching | v2.0 | 2/2 | Complete | 2026-03-31 |
| 9. RabbitMQ Events | v2.0 | 2/2 | Complete | 2026-03-31 |
| 10. Foundation | v3.0 | 2/2 | Complete | 2026-04-01 |
| 11. REST API + gRPC Client | v3.0 | 3/3 | Complete | 2026-04-01 |
| 12. Lesson Auto-Generation | v3.0 | 2/2 | Complete | 2026-04-01 |
| 13. Status Transitions + RabbitMQ Events | v3.0 | 2/2 | Complete | 2026-04-03 |
| 14. gRPC Server | v3.0 | 2/2 | Complete | 2026-04-04 |
| 15. Infrastructure Foundation | v4.0 | 2/2 | Complete | 2026-04-04 |
| 16. Event Consumers | v4.0 | 2/2 | Complete | 2026-04-04 |
| 17. Write Path — Geo-Checkin + Manual Marking | v4.0 | 3/3 | Complete | 2026-04-04 |
| 18. Read Path — Reports | v4.0 | 4/4 | Complete | 2026-04-04 |
| 19. Report Security & Routing Fix | v4.0 | 1/1 | Complete | 2026-04-04 |
| 20. Shared Infrastructure | v5.0 | 2/3 | In Progress|  |
| 21. Notification Web — WebSocket Core | v5.0 | 0/? | Not started | - |
| 22. Bot Infrastructure Layer | v5.0 | 0/? | Not started | - |
| 23. Bot Telegram Commands | v5.0 | 0/? | Not started | - |
| 24. Bot Event Notifications | v5.0 | 0/? | Not started | - |
| 25. Bot Reminder Lifecycle | v5.0 | 0/? | Not started | - |
