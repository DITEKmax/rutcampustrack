---
phase: 36
slug: mini-app-scaffold-auth
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 36 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest ^3.1.3 + @testing-library/react ^16.3.0 |
| **Config file** | `frontends/mini-app/vitest.config.ts` — Wave 0 creates it |
| **Quick run command** | `npm test` (runs `vitest run --passWithNoTests`) |
| **Full suite command** | `npm test` |
| **Estimated runtime** | ~5 seconds |

---

## Sampling Rate

- **After every task commit:** Run `npm test`
- **After every plan wave:** Run `npm test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 10 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 36-01-01 | 01 | 1 | TMA-01 | — | N/A | smoke | `npm test -- App.test` | ❌ W0 | ⬜ pending |
| 36-01-02 | 01 | 1 | TMA-02 | T-36-01 | initDataRaw sent to /auth/tma on mount | unit | `npm test -- features/auth` | ❌ W0 | ⬜ pending |
| 36-01-03 | 01 | 1 | TMA-03 | T-36-02 | accessToken in React state, not localStorage | unit | `npm test -- features/auth` | ❌ W0 | ⬜ pending |
| 36-01-04 | 01 | 1 | TMA-04 | T-36-03 | 401 re-auth via initData, not refresh-body | unit | `npm test -- shared/lib/axios` | ❌ W0 | ⬜ pending |
| 36-01-05 | 01 | 1 | TMA-05 | T-36-04 | mock only when VITE_TMA_DEV=true | unit | `npm test -- DevModeBanner` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `frontends/mini-app/vitest.config.ts` — test framework config
- [ ] `frontends/mini-app/src/test/setup.ts` — window.Telegram mock + jest-dom
- [ ] `frontends/mini-app/src/features/auth/__tests__/AuthProvider.test.tsx` — covers TMA-02, TMA-03, TMA-04
- [ ] `frontends/mini-app/src/shared/components/__tests__/DevModeBanner.test.tsx` — covers TMA-05

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Mini App opens in Telegram WebView without blank screen | TMA-01 | Requires real Telegram client | Open Mini App in Telegram, verify content renders within 3 seconds |
| Telegram theme colors apply correctly | TMA-01 | Visual verification needed | Switch Telegram theme (dark/light), verify Mini App colors update |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 10s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
