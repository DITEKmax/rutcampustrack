---
phase: 33
slug: infrastructure
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-06
---

# Phase 33 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | smoke tests (curl + docker compose) |
| **Config file** | none — no unit test framework needed |
| **Quick run command** | `curl -I http://localhost:PORT/` |
| **Full suite command** | `docker compose up -d && docker compose ps` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `curl -I http://localhost:PORT/`
- **After every plan wave:** Run `docker compose up -d && docker compose ps`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 33-01-01 | 01 | 1 | INFRA-01 | — | N/A | manual | read file | ✅ | ⬜ pending |
| 33-01-02 | 01 | 1 | INFRA-02 | — | N/A | smoke | `curl -s http://localhost:3000/` | ❌ W0 | ⬜ pending |
| 33-01-03 | 01 | 1 | INFRA-03 | — | N/A | smoke | `docker compose ps` | ❌ W0 | ⬜ pending |
| 33-01-04 | 01 | 1 | INFRA-04 | T-33-01 | Explicit origin list, no wildcard | smoke | `curl -i -X OPTIONS -H "Origin: http://localhost:5174" http://localhost:8080/api/auth/login` | ❌ W0 | ⬜ pending |
| 33-01-05 | 01 | 1 | INFRA-05 | T-33-02 | Exact path whitelist only | smoke | `curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/auth/tma` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. No unit test files needed — all verification is via smoke curl commands and docker compose status checks.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| URL layout document review | INFRA-01 | Document content, not code output | Read docs/url-layout.md and verify no routing conflicts |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
