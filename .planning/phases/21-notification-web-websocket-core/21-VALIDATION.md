---
phase: 21
slug: notification-web-websocket-core
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-05
---

# Phase 21 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ + Mockito (spring-boot-starter-test) |
| **Config file** | none — Spring Boot auto-detects |
| **Quick run command** | `.\gradlew :services:notification-web:test` |
| **Full suite command** | `.\gradlew :services:notification-web:test` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `.\gradlew :services:notification-web:test`
- **After every plan wave:** Run `.\gradlew :services:notification-web:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 21-01-01 | 01 | 1 | WS-01 | unit | `.\gradlew :services:notification-web:test --tests "*.JwtHandshakeInterceptorTest"` | ❌ W0 | ⬜ pending |
| 21-01-02 | 01 | 1 | WS-01 | unit | `.\gradlew :services:notification-web:test --tests "*.JwtHandshakeInterceptorTest"` | ❌ W0 | ⬜ pending |
| 21-02-01 | 02 | 1 | WS-02 | unit | `.\gradlew :services:notification-web:test --tests "*.EventConsumerTest"` | ❌ W0 | ⬜ pending |
| 21-02-02 | 02 | 1 | WS-03 | unit | `.\gradlew :services:notification-web:test --tests "*.EventConsumerTest"` | ❌ W0 | ⬜ pending |
| 21-02-03 | 02 | 1 | WS-04 | unit | `.\gradlew :services:notification-web:test --tests "*.EventConsumerTest"` | ❌ W0 | ⬜ pending |
| 21-02-04 | 02 | 1 | WS-05 | unit | `.\gradlew :services:notification-web:test --tests "*.EventConsumerTest"` | ❌ W0 | ⬜ pending |
| 21-02-05 | 02 | 1 | WS-06 | unit | `.\gradlew :services:notification-web:test --tests "*.EventConsumerTest"` | ❌ W0 | ⬜ pending |
| 21-02-06 | 02 | 1 | WS-07 | unit | `.\gradlew :services:notification-web:test --tests "*.EventConsumerTest"` | ❌ W0 | ⬜ pending |
| 21-manual | - | - | WS-01 | manual | n/a — JWT expiry mid-session; validated by spec (no re-validation after handshake) | manual-only | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `services/notification-web/src/test/java/ru/rutcampustrack/notification/websocket/JwtHandshakeInterceptorTest.java` — stubs for WS-01
- [ ] `services/notification-web/src/test/java/ru/rutcampustrack/notification/event/EventConsumerTest.java` — stubs for WS-02 through WS-07

*Existing `RabbitConfigTest.java` is sufficient for its scope — no changes needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| JWT expiry mid-session does NOT terminate delivery | WS-01 | Requires live session with expired token — validated by design (no re-validation after handshake) | Connect with valid JWT, wait for expiry, verify pushes continue |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
