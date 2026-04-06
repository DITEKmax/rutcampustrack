---
phase: 32
slug: stats-homework
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-06
---

# Phase 32 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | vitest |
| **Config file** | `frontends/pwa/vitest.config.ts` (or vite.config.ts test section) |
| **Quick run command** | `cd frontends/pwa && npx vitest run --reporter=verbose` |
| **Full suite command** | `cd frontends/pwa && npx vitest run` |
| **Estimated runtime** | ~10 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd frontends/pwa && npx vitest run --reporter=verbose`
- **After every plan wave:** Run `cd frontends/pwa && npx vitest run`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 32-01-01 | 01 | 1 | ATT-01 | — | N/A | unit | `npx vitest run` | ❌ W0 | ⬜ pending |
| 32-01-02 | 01 | 1 | ATT-02 | — | N/A | unit | `npx vitest run` | ❌ W0 | ⬜ pending |
| 32-01-03 | 01 | 1 | ATT-03 | — | N/A | unit | `npx vitest run` | ❌ W0 | ⬜ pending |
| 32-02-01 | 02 | 1 | HW-01 | — | N/A | unit | `npx vitest run` | ❌ W0 | ⬜ pending |
| 32-02-02 | 02 | 1 | HW-02 | — | N/A | unit | `npx vitest run` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Vitest setup verified in `frontends/pwa/`
- [ ] Test utils / render helpers for React Testing Library

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Red zone visual indicator | ATT-02 | Visual styling verification | Open /stats, check subject with attendance below threshold has red border and badge |
| Homework checkbox optimistic toggle | HW-02 | Requires real API interaction | Tap checkbox, verify toggle, close/reopen app, verify persistence |
| BottomNav 5-tab layout | — | Visual layout verification | Check all 5 tabs render without overflow on 320px viewport |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
