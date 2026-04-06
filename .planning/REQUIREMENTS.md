# Requirements: RutCampusTrack

**Defined:** 2026-04-05
**Core Value:** Student mobile client «RutTrack» (PWA) with native push notifications — independent from Telegram, installable, offline-capable

## v6.0 Requirements

Requirements for PWA + Web Push milestone. Each maps to roadmap phases.

### PWA Foundation

- [ ] **PWA-01**: User can log in with username and password (JWT stored in memory, refresh in httpOnly cookie)
- [ ] **PWA-02**: Access token auto-refreshes silently before 15-min expiry
- [ ] **PWA-03**: User can log out (clears tokens, invalidates refresh on server)
- [ ] **PWA-04**: PWA has manifest.json with name "RutTrack", standalone display, 192/512 icons
- [ ] **PWA-05**: Service Worker registers and caches app shell for offline loading
- [ ] **PWA-06**: Android users see A2HS prompt after first successful check-in
- [ ] **PWA-07**: iOS users see Safari install instructions when not in standalone mode

### Schedule

- [ ] **SCHED-01**: User can view today's schedule (lessons with time, subject, room, status)
- [ ] **SCHED-02**: User can navigate weekly schedule (swipe/tab between days)
- [ ] **SCHED-03**: Schedule is cached offline (stale-while-revalidate, 1hr max stale)

### Check-in

- [ ] **CHKIN-01**: User can tap check-in on active lesson card, GPS coords captured and submitted
- [ ] **CHKIN-02**: User sees immediate success/failure feedback with reason (not in zone, already marked, no active lesson)
- [ ] **CHKIN-03**: Check-in UI updates in real-time via STOMP WebSocket on attendance.marked event

### Web Push Backend

- [ ] **PUSH-01**: notification-web generates VAPID key pair and stores persistently
- [ ] **PUSH-02**: notification-web exposes POST /api/ws/push/subscribe to store PushSubscription
- [ ] **PUSH-03**: notification-web exposes DELETE /api/ws/push/subscribe to unsubscribe
- [ ] **PUSH-04**: notification-web sends Web Push for lesson.started events (async, non-blocking)
- [ ] **PUSH-05**: notification-web sends Web Push for lesson.cancelled events
- [ ] **PUSH-06**: notification-web sends Web Push for homework.published events
- [ ] **PUSH-07**: notification-web handles expired/invalid subscriptions (HTTP 410 → delete)

### Web Push Frontend

- [ ] **PUSHUI-01**: Service Worker handles push event and shows notification with action buttons
- [ ] **PUSHUI-02**: notificationclick opens PWA on relevant screen (check-in for lesson.started, schedule for cancelled)
- [ ] **PUSHUI-03**: Push permission requested via soft-ask pattern after demonstrated value (not on first load)
- [ ] **PUSHUI-04**: Foreground push suppressed when PWA window is focused (dedup with WebSocket)

### Attendance

- [x] **ATT-01**: User can view attendance stats per subject (percentage, count)
- [x] **ATT-02**: User sees red zone warning when below threshold for a subject
- [x] **ATT-03**: User can view attendance records list with status indicators (б/н/у/сп with colors)

### Homework

- [ ] **HW-01**: User can view homework list for their group
- [ ] **HW-02**: User can mark homework as done/undone (personal completion tracker)

### Infrastructure

- [ ] **INFRA-01**: API Gateway CORS configured for PWA origin
- [ ] **INFRA-02**: API Gateway route for /api/push/** to notification-web
- [ ] **INFRA-03**: PWA served via nginx container in docker-compose

## v6.1 Requirements

Deferred to next iteration after core PWA is validated.

### Headman Features

- **HEAD-01**: Headman can mark attendance for group from PWA
- **HEAD-02**: Headman can view group attendance summary

### Enhanced Push

- **EPUSH-01**: User can configure push notification preferences (mute by type)
- **EPUSH-02**: Push notifications for headman alerts (excuse/late-checkin)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Excuse ticket creation from PWA | Backend flow deferred (PROJECT.md) |
| Late check-in request from PWA | Backend flow deferred (PROJECT.md) |
| Offline check-in queue (background sync) | 5-min time window makes queued retries invalid |
| Web Push for TEACHER/ADMIN roles | Defer to web panel milestone (v8.0) |
| Foreground push notifications | STOMP WebSocket already handles in-app events |
| OTP login from PWA | Requires Telegram bot linked — chicken-and-egg UX |
| Telegram Mini App features | Separate milestone (v8.0) |
| Native mobile app | Web-first strategy |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| PUSH-01 | Phase 27 | Pending |
| PUSH-02 | Phase 27 | Pending |
| PUSH-03 | Phase 27 | Pending |
| PUSH-04 | Phase 27 | Pending |
| PUSH-05 | Phase 27 | Pending |
| PUSH-06 | Phase 27 | Pending |
| PUSH-07 | Phase 27 | Pending |
| INFRA-02 | Phase 27 | Pending |
| INFRA-01 | Phase 28 | Pending |
| INFRA-03 | Phase 28 | Pending |
| PWA-01 | Phase 29 | Pending |
| PWA-02 | Phase 29 | Pending |
| PWA-03 | Phase 29 | Pending |
| PWA-04 | Phase 29 | Pending |
| PWA-05 | Phase 29 | Pending |
| PWA-06 | Phase 29 | Pending |
| PWA-07 | Phase 29 | Pending |
| SCHED-01 | Phase 30 | Pending |
| SCHED-02 | Phase 30 | Pending |
| SCHED-03 | Phase 30 | Pending |
| CHKIN-01 | Phase 30 | Pending |
| CHKIN-02 | Phase 30 | Pending |
| CHKIN-03 | Phase 30 | Pending |
| PUSHUI-01 | Phase 31 | Pending |
| PUSHUI-02 | Phase 31 | Pending |
| PUSHUI-03 | Phase 31 | Pending |
| PUSHUI-04 | Phase 31 | Pending |
| ATT-01 | Phase 32 | Complete |
| ATT-02 | Phase 32 | Complete |
| ATT-03 | Phase 32 | Complete |
| HW-01 | Phase 32 | Pending |
| HW-02 | Phase 32 | Pending |

**Coverage:**
- v6.0 requirements: 32 total
- Mapped to phases: 32
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-05*
*Last updated: 2026-04-05 after roadmap creation (v6.0 Phases 27-32)*
