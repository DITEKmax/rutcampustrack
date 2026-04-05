---
phase: 25
slug: bot-reminder-lifecycle
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-05
---

# Phase 25 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | pytest + pytest-asyncio |
| **Config file** | `services/notification-bot/pytest.ini` (`asyncio_mode = auto`) |
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
| 25-01-01 | 01 | 1 | NOTIF-02 | — | N/A | unit | `pytest tests/test_reminder_scheduler.py -x` | ❌ TDD | ⬜ pending |
| 25-01-02 | 01 | 1 | NOTIF-03 | — | N/A | unit | `pytest tests/test_reminder_scheduler.py -x` | ❌ TDD | ⬜ pending |
| 25-02-01 | 02 | 2 | NOTIF-04 | — | N/A | unit | `pytest tests/test_lesson_closed.py -x` | ❌ TDD | ⬜ pending |
| 25-02-02 | 02 | 2 | NOTIF-05 | — | N/A | unit | `pytest tests/test_attendance_marked.py -x` | ❌ TDD | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Test files are co-created via TDD in each plan task rather than pre-staged in a separate Wave 0 plan:*
- Plan 01 Task 1 creates `tests/test_reminder_scheduler.py` (NOTIF-02, NOTIF-03)
- Plan 02 Task 1 creates `tests/test_lesson_closed.py` (NOTIF-04) and `tests/test_attendance_marked.py` (NOTIF-05)

*Existing infrastructure (pytest.ini, conftest.py, fakeredis fixture) covers all other needs.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Telegram messages actually deleted from chat | NOTIF-04, NOTIF-05 | Requires real Telegram API interaction | Send test reminders via bot, trigger lesson.closed or attendance.marked, verify messages disappear from chat |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (TDD — co-created in tasks)
- [x] No watch-mode flags
- [x] Feedback latency < 15s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-04-05
