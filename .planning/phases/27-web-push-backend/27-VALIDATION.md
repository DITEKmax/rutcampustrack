---
phase: 27
slug: web-push-backend
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-05
---

# Phase 27 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito (Spring Boot Test) |
| **Config file** | None — convention-based; tests in `src/test/java/` |
| **Quick run command** | `.\gradlew :services:notification-service:notification-app:test --tests "ru.rutcampustrack.notification.push.*" -x integrationTest` |
| **Full suite command** | `.\gradlew :services:notification-service:notification-app:test` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `.\gradlew :services:notification-service:notification-app:test --tests "ru.rutcampustrack.notification.push.*" -x integrationTest`
- **After every plan wave:** Run `.\gradlew :services:notification-service:notification-app:test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 27-01-01 | 01 | 1 | PUSH-01 | — | VAPID key returned as Base64 | unit | `.\gradlew :services:notification-service:notification-app:test --tests "*.PushControllerTest.getVapidPublicKey*"` | ❌ W0 | ⬜ pending |
| 27-01-02 | 01 | 1 | PUSH-02 | — | Subscription stored in MongoDB | integration | `.\gradlew :services:notification-service:notification-app:test --tests "*.PushServiceTest.subscribe*"` | ❌ W0 | ⬜ pending |
| 27-01-03 | 01 | 1 | PUSH-03 | — | Subscription removed from MongoDB | integration | `.\gradlew :services:notification-service:notification-app:test --tests "*.PushServiceTest.unsubscribe*"` | ❌ W0 | ⬜ pending |
| 27-02-01 | 02 | 2 | PUSH-04 | — | lesson.started triggers push async | unit | `.\gradlew :services:notification-service:notification-app:test --tests "*.EventConsumerTest.lessonStarted_triggersPush*"` | ❌ W0 | ⬜ pending |
| 27-02-02 | 02 | 2 | PUSH-05 | — | lesson.cancelled triggers push async | unit | Same as PUSH-04 class | ❌ W0 | ⬜ pending |
| 27-02-03 | 02 | 2 | PUSH-06 | — | homework.published triggers push async | unit | Same as PUSH-04 class | ❌ W0 | ⬜ pending |
| 27-02-04 | 02 | 2 | PUSH-07 | — | HTTP 410 → subscription deleted | unit | `.\gradlew :services:notification-service:notification-app:test --tests "*.PushServiceTest.send410*"` | ❌ W0 | ⬜ pending |
| 27-03-01 | 03 | 1 | INFRA-02 | — | Gateway routes /api/push/** | manual | `curl -H "Authorization: Bearer $JWT" http://localhost:8080/api/push/vapid-public-key` | manual-only | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/PushControllerTest.java` — stubs for PUSH-01, role enforcement
- [ ] `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/PushServiceTest.java` — stubs for PUSH-02, PUSH-03, PUSH-07
- [ ] `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/event/EventConsumerTest.java` — extend existing; add PUSH-04/05/06 push trigger assertions

*Existing infrastructure covers framework — only test files need creation.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Gateway routes /api/push/** to notification-web | INFRA-02 | Requires running Gateway + notification-web containers | `curl -H "Authorization: Bearer $JWT" http://localhost:8080/api/push/vapid-public-key` — expect 200 with Base64 key |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
