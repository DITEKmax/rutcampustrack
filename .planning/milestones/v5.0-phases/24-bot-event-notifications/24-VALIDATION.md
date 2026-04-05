---
phase: 24
slug: bot-event-notifications
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-05
---

# Phase 24 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | pytest + pytest-asyncio |
| **Config file** | `services/notification-bot/pytest.ini` |
| **Quick run command** | `cd services/notification-bot && python -m pytest tests/ -x -q` |
| **Full suite command** | `cd services/notification-bot && python -m pytest tests/ -v` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd services/notification-bot && python -m pytest tests/ -x -q`
- **After every plan wave:** Run `cd services/notification-bot && python -m pytest tests/ -v`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 24-01-01 | 01 | 0 | DISPATCH | — | N/A | unit | `pytest tests/test_event_dispatcher.py -x` | No — Wave 0 | pending |
| 24-01-02 | 01 | 1 | NOTIF-01 | — | N/A | unit | `pytest tests/test_lesson_started.py -x` | No — Wave 0 | pending |
| 24-01-03 | 01 | 1 | NOTIF-06 | — | N/A | unit | `pytest tests/test_lesson_cancelled.py -x` | No — Wave 0 | pending |
| 24-01-04 | 01 | 1 | NOTIF-07 | — | N/A | unit | `pytest tests/test_homework_notifications.py -x` | No — Wave 0 | pending |
| 24-01-05 | 01 | 1 | NOTIF-08 | — | N/A | unit | `pytest tests/test_headman_alerts.py -x` | No — Wave 0 | pending |
| 24-01-06 | 01 | 1 | NOTIF-09 | — | N/A | unit | `pytest tests/test_headman_alerts.py -x` | No — Wave 0 | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `tests/test_event_dispatcher.py` — stubs for event routing dispatch
- [ ] `tests/test_lesson_started.py` — stubs for NOTIF-01
- [ ] `tests/test_lesson_cancelled.py` — stubs for NOTIF-06
- [ ] `tests/test_homework_notifications.py` — stubs for NOTIF-07
- [ ] `tests/test_headman_alerts.py` — stubs for NOTIF-08, NOTIF-09

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Inline button opens Mini App | NOTIF-01 | Requires Telegram client + Mini App (Phase 8) | Send test message with InlineKeyboardButton, verify web_app url in payload |

---

## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
