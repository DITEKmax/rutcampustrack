---
phase: 20
slug: shared-infrastructure
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-05
---

# Phase 20 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (notification-web), pytest 7.x (notification-bot) |
| **Config file** | `services/notification-web/notification-web-app/build.gradle.kts`, `services/notification-bot/pytest.ini` (Wave 0 creates) |
| **Quick run command** | `./gradlew :services:notification-web:test` and `cd services/notification-bot && python -m pytest -x` |
| **Full suite command** | `./gradlew :services:notification-web:test && cd services/notification-bot && python -m pytest` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick run command for the affected service
- **After every plan wave:** Run full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 20-01-01 | 01 | 1 | INFRA-01 | integration | `docker compose up -d && docker compose exec rabbitmq rabbitmqctl list_queues` | ❌ W0 | ⬜ pending |
| 20-01-02 | 01 | 1 | INFRA-01 | unit | `./gradlew :services:notification-web:test --tests '*RabbitConfig*'` | ❌ W0 | ⬜ pending |
| 20-02-01 | 02 | 1 | INFRA-02 | integration | `cd services/notification-bot && python -m pytest tests/test_consumer.py` | ❌ W0 | ⬜ pending |
| 20-03-01 | 03 | 2 | INFRA-03 | integration | `docker compose up -d && docker compose ps --format json` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `services/notification-web/notification-web-app/src/test/java/.../RabbitConfigTest.java` — stubs for INFRA-01
- [ ] `services/notification-bot/tests/test_consumer.py` — stubs for INFRA-02
- [ ] `services/notification-bot/pytest.ini` — pytest config
- [ ] `pip install pytest aio-pika` — if no test framework detected in bot

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Queue independence on service stop | INFRA-01 | Requires stopping one container mid-run | 1. `docker compose up -d` 2. Publish message to exchange 3. `docker compose stop notification-web` 4. Publish another message 5. `docker compose start notification-web` 6. Verify both messages received |
| DLQ routing on processing failure | INFRA-01 | Requires simulating message rejection | 1. Publish malformed message to exchange 2. Check DLQ via RabbitMQ Management UI at port 15672 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
