---
phase: 23
slug: bot-telegram-commands
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-05
---

# Phase 23 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | pytest 8.x (Python bot) + JUnit 5 (Java services) |
| **Config file** | `services/notification-bot/pytest.ini` (or pyproject.toml) / existing Gradle test config |
| **Quick run command** | `cd services/notification-bot && python -m pytest tests/ -x -q` |
| **Full suite command** | `cd services/notification-bot && python -m pytest tests/ -v` + `./gradlew :services:auth-service:test :services:academic-service:academic-app:test` |
| **Estimated runtime** | ~15 seconds (Python) + ~30 seconds (Java) |

---

## Sampling Rate

- **After every task commit:** Run `cd services/notification-bot && python -m pytest tests/ -x -q`
- **After every plan wave:** Run full suite (both Python and Java)
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 23-01-01 | 01 | 0 | BOT-01 | — | N/A | unit | `python -m pytest tests/test_academic_grpc.py -x` | ❌ W0 | ⬜ pending |
| 23-01-02 | 01 | 1 | BOT-01 | — | N/A | unit | `./gradlew :services:academic-service:academic-app:test` | ✅ | ⬜ pending |
| 23-02-01 | 02 | 1 | BOT-02 | — | N/A | unit | `./gradlew :services:auth-service:test` | ✅ | ⬜ pending |
| 23-02-02 | 02 | 2 | BOT-02 | — | N/A | unit | `python -m pytest tests/test_login.py -x` | ❌ W0 | ⬜ pending |
| 23-03-01 | 03 | 2 | BOT-03 | — | N/A | unit | `python -m pytest tests/test_status.py -x` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `tests/test_handlers/test_start.py` — stubs for BOT-01 (/start handler)
- [ ] `tests/test_handlers/test_login.py` — stubs for BOT-02 (/login FSM flow)
- [ ] `tests/test_handlers/test_status.py` — stubs for BOT-03 (/status handler)
- [ ] `tests/conftest.py` — shared fixtures (mock bot, mock gRPC clients, mock Redis)

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Telegram message delivery | BOT-01, BOT-02, BOT-03 | Requires live Telegram Bot API | Send /start, /login, /status to test bot in Telegram |
| OTP flow end-to-end | BOT-02 | Requires running Auth Service | Start all services, send /login, enter OTP code |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
