# Requirements: RutCampusTrack v5.0

**Defined:** 2026-04-04
**Core Value:** Real-time notification delivery via WebSocket (web panel) and Telegram bot — both consuming RabbitMQ events from existing services.

## v5.0 Requirements

Requirements for Notification Service milestone. Each maps to roadmap phases.

### Infrastructure

- [ ] **INFRA-01**: Both notification services have dedicated RabbitMQ queues with DLQ bound to fanout exchange
- [ ] **INFRA-02**: Docker-compose includes notification-web and notification-bot containers with health checks
- [ ] **INFRA-03**: Redis key namespace `reminder:msgs:{lesson_id}:{user_id}` documented and available for bot

### WebSocket (Notification Web)

- [ ] **WS-01**: User can connect to STOMP WebSocket endpoint with JWT authentication at handshake
- [ ] **WS-02**: User receives real-time push when a lesson starts for their group
- [ ] **WS-03**: User receives real-time push when a lesson is cancelled for their group
- [ ] **WS-04**: User receives real-time push when homework is published/updated for their group
- [ ] **WS-05**: Headman receives real-time push when a student requests an excuse in their group
- [ ] **WS-06**: Headman receives real-time push when a student requests late check-in in their group
- [ ] **WS-07**: WebSocket messages are routed only to users of the relevant group (privacy)

### Bot Commands

- [ ] **BOT-01**: Student can link Telegram account via /start and receive initial credentials if set
- [ ] **BOT-02**: Student can authenticate via /login using OTP flow through Auth Service
- [ ] **BOT-03**: Student can check attendance status via /status command

### Bot Notifications

- [ ] **NOTIF-01**: Student receives Telegram message with inline check-in button when lesson starts
- [ ] **NOTIF-02**: Student receives reminder at lesson midpoint if not yet checked in
- [ ] **NOTIF-03**: Student receives final reminder near lesson end if not yet checked in
- [ ] **NOTIF-04**: All reminder messages are deleted from Telegram when lesson closes
- [ ] **NOTIF-05**: Reminder messages are deleted immediately when student checks in (via attendance.marked)
- [ ] **NOTIF-06**: Student receives Telegram notification when lesson is cancelled
- [ ] **NOTIF-07**: Student receives Telegram notification when homework is published or updated
- [ ] **NOTIF-08**: Headman receives Telegram notification when student requests excuse
- [ ] **NOTIF-09**: Headman receives Telegram notification when student requests late check-in

### Bot Infrastructure

- [ ] **BINFRA-01**: Bot uses gRPC client to resolve group members with telegram_ids for broadcasting
- [ ] **BINFRA-02**: Bot uses throttled send queue to respect Telegram rate limits (30 msg/sec)
- [ ] **BINFRA-03**: Bot uses aio-pika connect_robust with watchdog for reliable RabbitMQ consumption

## v5.1 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Notification Preferences

- **PREF-01**: User can mute specific notification types
- **PREF-02**: User can select preferred notification channel (Telegram only, WebSocket only, both)

### Notification History

- **HIST-01**: User can view notification history in web panel

### Excuse Actions

- **EXCACT-01**: Headman can approve/reject excuse ticket via inline Telegram buttons
- **EXCACT-02**: Bot maintains service account JWT for REST calls to Attendance Service

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Notification history database | Both services are stateless event forwarders; Telegram keeps chat history |
| Read receipts / delivery guarantees | Telegram has no programmatic read receipts; best-effort is sufficient for 500-5000 users |
| Approve/reject inline buttons for excuse | Requires bot JWT + REST calls to Attendance Service — complexity deferred to v5.1 |
| Notification preferences (mute/channel) | Requires preferences store + filter logic in both services — defer to v5.1 |
| Mobile native push notifications | Web-first approach (Telegram + WebSocket); no native app planned |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-01 | Phase 20 | Pending |
| INFRA-02 | Phase 20 | Pending |
| INFRA-03 | Phase 20 | Pending |
| WS-01 | Phase 21 | Pending |
| WS-02 | Phase 21 | Pending |
| WS-03 | Phase 21 | Pending |
| WS-04 | Phase 21 | Pending |
| WS-05 | Phase 21 | Pending |
| WS-06 | Phase 21 | Pending |
| WS-07 | Phase 21 | Pending |
| BINFRA-01 | Phase 22 | Pending |
| BINFRA-02 | Phase 22 | Pending |
| BINFRA-03 | Phase 22 | Pending |
| BOT-01 | Phase 23 | Pending |
| BOT-02 | Phase 23 | Pending |
| BOT-03 | Phase 23 | Pending |
| NOTIF-01 | Phase 24 | Pending |
| NOTIF-06 | Phase 24 | Pending |
| NOTIF-07 | Phase 24 | Pending |
| NOTIF-08 | Phase 24 | Pending |
| NOTIF-09 | Phase 24 | Pending |
| NOTIF-02 | Phase 25 | Pending |
| NOTIF-03 | Phase 25 | Pending |
| NOTIF-04 | Phase 25 | Pending |
| NOTIF-05 | Phase 25 | Pending |

**Coverage:**
- v5.0 requirements: 25 total
- Mapped to phases: 25
- Unmapped: 0

---
*Requirements defined: 2026-04-04*
*Last updated: 2026-04-04 after roadmap creation*
