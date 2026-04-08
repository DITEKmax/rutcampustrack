---
phase: 46
slug: github-actions-deploy
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-08
---

# Phase 46 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | GitHub Actions (YAML validation) + manual deploy verification |
| **Config file** | `.github/workflows/deploy.yml` |
| **Quick run command** | `yamllint .github/workflows/deploy.yml` |
| **Full suite command** | `act -n --workflows .github/workflows/deploy.yml` (dry-run) |
| **Estimated runtime** | ~5 seconds |

---

## Sampling Rate

- **After every task commit:** Run YAML lint check
- **After every plan wave:** Validate workflow syntax
- **Before `/gsd-verify-work`:** Full syntax + structure review
- **Max feedback latency:** 5 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 46-01-01 | 01 | 1 | CI-05 | — | GHCR push uses GITHUB_TOKEN, not PAT | file-check | `grep -q 'packages: write' .github/workflows/deploy.yml` | ❌ W0 | ⬜ pending |
| 46-01-02 | 01 | 1 | CI-06 | — | SSH key not leaked in logs | file-check | `grep -q 'ssh-action' .github/workflows/deploy.yml` | ❌ W0 | ⬜ pending |
| 46-01-03 | 01 | 1 | CI-07 | — | Secrets referenced, not hardcoded | file-check | `grep -q 'secrets\.' .github/workflows/deploy.yml` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `.github/workflows/deploy.yml` — workflow file to be created

*Existing infrastructure covers CI setup from Phase 45.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| GHCR images pushed successfully | CI-05 | Requires actual GitHub push | Push to main, check ghcr.io/maksd/rutcampustrack |
| VPS deploy via SSH | CI-06 | Requires live VPS | Verify containers running after deploy |
| Secrets configured in GitHub | CI-07 | GitHub UI operation | Check Settings > Secrets in repo |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 5s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
