---
phase: 30
slug: schedule-check-in-ui
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-06
---

# Phase 30 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 3.1.3 + @testing-library/react 16.3.0 |
| **Config file** | `frontends/pwa/vitest.config.ts` |
| **Quick run command** | `npm run test --prefix frontends/pwa` |
| **Full suite command** | `npm run test --prefix frontends/pwa` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `npm run test --prefix frontends/pwa`
- **After every plan wave:** Run `npm run test --prefix frontends/pwa`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 30-01-01 | 01 | 1 | SCHED-01 | — | N/A | unit | `npm run test --prefix frontends/pwa` | ❌ W0 | ⬜ pending |
| 30-01-02 | 01 | 1 | SCHED-02 | — | N/A | unit | `npm run test --prefix frontends/pwa` | ❌ W0 | ⬜ pending |
| 30-01-03 | 01 | 1 | SCHED-03 | — | N/A | unit | `npm run test --prefix frontends/pwa` | ❌ W0 | ⬜ pending |
| 30-02-01 | 02 | 1 | CHKIN-01 | T-30-01 | GPS coords validated server-side | unit | `npm run test --prefix frontends/pwa` | ❌ W0 | ⬜ pending |
| 30-02-02 | 02 | 1 | CHKIN-02 | T-30-01 | Error messages shown per HTTP code | unit | `npm run test --prefix frontends/pwa` | ❌ W0 | ⬜ pending |
| 30-02-03 | 02 | 1 | CHKIN-03 | T-30-02 | STOMP subscription enforced by backend JWT | unit | `npm run test --prefix frontends/pwa` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `frontends/pwa/src/features/schedule/__tests__/SchedulePage.test.tsx` — stubs for SCHED-01, SCHED-02
- [ ] `frontends/pwa/src/features/schedule/__tests__/OfflineStaleNotice.test.tsx` — stubs for SCHED-03
- [ ] `frontends/pwa/src/features/checkin/__tests__/CheckInButton.test.tsx` — stubs for CHKIN-01, CHKIN-02
- [ ] `frontends/pwa/src/features/checkin/__tests__/useStompCheckin.test.ts` — stubs for CHKIN-03
- [ ] Mock for `navigator.geolocation` in `src/test/setup.ts`
- [ ] Mock for `@stomp/stompjs` Client in test utilities

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| GPS permission prompt flow | CHKIN-01 | Browser permission API not mockable in JSDOM | Open PWA on mobile, tap "Отметиться", verify prompt appears |
| STOMP reconnect after 30s disconnect indicator | CHKIN-03 | Requires real WebSocket + timing | Disable WiFi for >30s, verify indicator, re-enable, verify refresh |
| Swipe gesture between days | SCHED-02 | Touch events unreliable in test env | On mobile device, swipe left/right on schedule, verify day changes |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
