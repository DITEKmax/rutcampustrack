---
phase: 16
slug: event-consumers
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-04
---

# Phase 16 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test 3.4.x + Testcontainers + Awaitility 4.2.2 |
| **Config file** | None — standard Boot test auto-configuration |
| **Quick run command** | `.\gradlew :services:attendance-service:attendance-app:test --tests "*.EventConsumerIntegrationTest"` |
| **Full suite command** | `.\gradlew :services:attendance-service:attendance-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `.\gradlew :services:attendance-service:attendance-app:test --tests "*.EventConsumerIntegrationTest"`
- **After every plan wave:** Run `.\gradlew :services:attendance-service:attendance-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 16-01-01 | 01 | 1 | MARK-03 | integration | `--tests "*.EventConsumerIntegrationTest#lessonClosed_*"` | ❌ W0 | ⬜ pending |
| 16-01-02 | 01 | 1 | MARK-04 | integration | `--tests "*.EventConsumerIntegrationTest#lessonClosed_existingCheckin_*"` | ❌ W0 | ⬜ pending |
| 16-01-03 | 01 | 1 | MARK-05 | integration | `--tests "*.EventConsumerIntegrationTest#lessonCancelled_*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `EventConsumerIntegrationTest.java` — stubs for MARK-03, MARK-04, MARK-05
- [ ] Extend `AbstractAttendanceIntegrationTest` for Testcontainers setup

*Existing infrastructure: `AbstractAttendanceIntegrationTest`, `RabbitConsumerTest`, Testcontainers setup — all reusable.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| DLQ routing on gRPC failure | D-06 | Requires simulated infrastructure failure | Publish event, mock gRPC to throw, verify message count on DLQ queue |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
