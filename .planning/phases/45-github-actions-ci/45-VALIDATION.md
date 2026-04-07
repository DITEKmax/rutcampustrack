---
phase: 45
slug: github-actions-ci
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 45 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Java), pytest 8.x (Python), Vitest 3.x (Frontends) |
| **Config file** | `.github/workflows/ci.yml` (main deliverable) |
| **Quick run command** | `./gradlew :services:auth-service:test` |
| **Full suite command** | `./gradlew build` |
| **Estimated runtime** | ~120 seconds (local), ~5-8 min (CI) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew build` (verify no breakage)
- **After every plan wave:** Verify workflow YAML is valid with `actionlint` or manual review
- **Before `/gsd-verify-work`:** Push branch and confirm CI passes on GitHub
- **Max feedback latency:** 120 seconds (local build)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 45-01-01 | 01 | 1 | CI-01 | T-45-01 | No script injection in run steps | config | `grep -c 'run:' .github/workflows/ci.yml` | ❌ W0 | ⬜ pending |
| 45-01-02 | 01 | 1 | CI-04 | — | N/A | config | `grep -c 'actions/cache\|setup-gradle' .github/workflows/ci.yml` | ❌ W0 | ⬜ pending |
| 45-01-03 | 01 | 1 | CI-02 | — | N/A | config | `grep -c 'pytest\|ruff' .github/workflows/ci.yml` | ❌ W0 | ⬜ pending |
| 45-01-04 | 01 | 1 | CI-03 | — | N/A | config | `grep -c 'npm ci\|npm test\|npm run build' .github/workflows/ci.yml` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `.github/workflows/ci.yml` — the workflow file (main deliverable of this phase)
- [ ] Verify `package-lock.json` committed for all three frontends (needed for `npm ci`)
- [ ] Verify `gradlew` executable bit set (or add `chmod +x gradlew` step)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| CI triggers on push/PR | CI-01 | Requires GitHub runner | Push a branch, verify workflow runs |
| Gradle cache reduces build time | CI-04 | Requires two CI runs | Compare first and second run build times |
| Failing test blocks PR merge | CI-01 | Requires branch protection setup | Create PR with failing test, verify merge blocked |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
