---
phase: 35
slug: landing-page
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 35 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Browser check (static HTML — no test framework needed) |
| **Config file** | none |
| **Quick run command** | `curl -s -o /dev/null -w "%{http_code}" http://localhost:8081` |
| **Full suite command** | `curl -s http://localhost:8081 \| grep -c '<section'` |
| **Estimated runtime** | ~2 seconds |

---

## Sampling Rate

- **After every task commit:** Run `curl -s -o /dev/null -w "%{http_code}" http://localhost:8081`
- **After every plan wave:** Run full suite command
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 2 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 35-01-01 | 01 | 1 | LAND-01 | — | N/A | manual | `curl -s http://localhost:8081 \| grep 'hero'` | ❌ W0 | ⬜ pending |
| 35-01-02 | 01 | 1 | LAND-02 | — | N/A | manual | `curl -s http://localhost:8081 \| grep 'role'` | ❌ W0 | ⬜ pending |
| 35-01-03 | 01 | 1 | LAND-03 | — | N/A | manual | `curl -s http://localhost:8081 \| grep 'viewport'` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements — static HTML served by nginx from Phase 33.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Responsive layout 360px-1440px | LAND-01 | Requires visual browser inspection | Open page, resize viewport from 360px to 1440px, verify no horizontal scroll or broken layout |
| Hero section visual quality | LAND-01 | Subjective visual check | Verify hero has headline, subheadline, CTA button |
| Feature highlights display | LAND-02 | Visual layout check | Verify 5 feature cards render with icons and text |
| Role overview section | LAND-02 | Visual content check | Verify 4 role cards (Student, Headman, Teacher, Admin) display |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 2s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
