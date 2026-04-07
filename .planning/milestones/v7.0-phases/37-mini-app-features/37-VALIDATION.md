---
phase: 37
slug: mini-app-features
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 37 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | vitest 3.1.x |
| **Config file** | `frontends/mini-app/vitest.config.ts` |
| **Quick run command** | `cd frontends/mini-app && npx vitest run --reporter=verbose` |
| **Full suite command** | `cd frontends/mini-app && npx vitest run --reporter=verbose` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd frontends/mini-app && npx vitest run --reporter=verbose`
- **After every plan wave:** Run `cd frontends/mini-app && npx vitest run --reporter=verbose`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 10 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 37-01-01 | 01 | 1 | TMA-06 | — | N/A | unit | `npx vitest run` | ❌ W0 | ⬜ pending |
| 37-01-02 | 01 | 1 | TMA-07 | — | N/A | unit | `npx vitest run` | ❌ W0 | ⬜ pending |
| 37-02-01 | 02 | 1 | TMA-08 | — | N/A | unit | `npx vitest run` | ❌ W0 | ⬜ pending |
| 37-02-02 | 02 | 1 | TMA-09 | — | N/A | unit | `npx vitest run` | ❌ W0 | ⬜ pending |
| 37-03-01 | 03 | 1 | TMA-10 | — | N/A | unit | `npx vitest run` | ❌ W0 | ⬜ pending |
| 37-03-02 | 03 | 1 | TMA-11 | — | N/A | unit | `npx vitest run` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Test stubs for TMA-06 through TMA-11 features
- [ ] Mock fixtures for Telegram SDK, API responses

*Existing vitest infrastructure from Phase 36 covers base setup.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| GPS check-in inside Telegram | TMA-07 | Requires real Telegram WebView + GPS | Open Mini App in Telegram, navigate to active lesson, tap MainButton, verify haptic feedback |
| Telegram theme adaptation | TMA-11 | Requires Telegram app dark/light mode switch | Toggle Telegram dark mode, verify Mini App follows theme |
| BackButton navigation | TMA-11 | Requires real Telegram WebView | Navigate to sub-page, tap BackButton, verify navigation back |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 10s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
