---
phase: 31
slug: push-frontend-end-to-end-integration
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-06
---

# Phase 31 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | vitest 3.1.x |
| **Config file** | frontends/pwa/vitest.config.ts |
| **Quick run command** | `cd frontends/pwa && npx vitest run` |
| **Full suite command** | `cd frontends/pwa && npx vitest run --reporter=verbose` |
| **Estimated runtime** | ~5 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd frontends/pwa && npx vitest run`
- **After every plan wave:** Run `cd frontends/pwa && npx vitest run --reporter=verbose`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 5 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 31-01-01 | 01 | 1 | PUSHUI-01 | — | Permission prompt only on explicit user gesture | unit | `npx vitest run` | ❌ W0 | ⬜ pending |
| 31-01-02 | 01 | 1 | PUSHUI-02 | — | lesson.started push opens /checkin | unit | `npx vitest run` | ❌ W0 | ⬜ pending |
| 31-01-03 | 01 | 1 | PUSHUI-03 | — | lesson.cancelled push opens /schedule | unit | `npx vitest run` | ❌ W0 | ⬜ pending |
| 31-01-04 | 01 | 1 | PUSHUI-04 | — | Foreground suppression via clients.matchAll | manual | N/A (SW context) | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `frontends/pwa/src/features/push/__tests__/` — test stubs for push subscription hooks
- [ ] Existing test infrastructure (vitest + jsdom + @testing-library) covers all phase requirements

*Existing infrastructure covers most phase requirements. Push-specific tests to be added with implementation.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Browser permission prompt appears on button tap | PUSHUI-01 | Requires real browser Notification API | Open PWA, go to Profile, tap "Enable notifications", verify browser prompt |
| Push notification delivered within 10s | PUSHUI-02 | Requires running backend + push service | Trigger lesson.started event, verify notification on device |
| Notification tap opens correct screen | PUSHUI-02, PUSHUI-03 | Requires real notification interaction | Tap notification, verify deep link navigation |
| Foreground suppression | PUSHUI-04 | Requires two browser contexts (SW + page) | Open PWA, trigger push while focused, verify no notification shown |
| iOS standalone guard | PUSHUI-01 | Requires physical iOS device | Open in Safari (not standalone), verify push option hidden/disabled |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 5s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
