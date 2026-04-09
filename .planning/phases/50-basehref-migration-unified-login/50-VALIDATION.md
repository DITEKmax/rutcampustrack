---
phase: 50
slug: basehref-migration-unified-login
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-09
---

# Phase 50 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | vitest 3.x |
| **Config file** | `frontends/web-panel/vitest.config.ts` |
| **Quick run command** | `cd frontends/web-panel && npx vitest run --reporter=dot` |
| **Full suite command** | `cd frontends/web-panel && npx vitest run` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd frontends/web-panel && npx vitest run --reporter=dot`
- **After every plan wave:** Run `cd frontends/web-panel && npx vitest run`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

*To be filled in by planner (each PLAN.md task gets a row here).*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Baseline (129 existing vitest tests) must pass before any new work.*

- [ ] `cd frontends/web-panel && npx vitest run` — 129 tests green (AUTH-v9-07 baseline)
- [ ] No new spec files needed in Wave 0 — infrastructure covers all phase requirements
- [ ] New specs added during execution:
  - `src/app/core/auth/student.guard.spec.ts`
  - `src/app/core/auth/headman.guard.spec.ts`
  - `src/app/core/auth/guest.guard.spec.ts`
  - `src/app/core/auth/auth.service.spec.ts` (extend existing)
  - `src/app/core/auth/role.guard.spec.ts` (extend existing)
  - `src/app/features/login/login.component.spec.ts` (extend existing)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Prod nginx routes `/` to web-panel after baseHref migration | INFRA-v9-04 | Requires live reverse-proxy container rebuild + restart; cannot be asserted in vitest | 1) Rebuild `rct-web-panel-nginx` image 2) Restart nginx reverse-proxy 3) `curl -I https://ruttrack.site/` → 301 `/login` 4) `curl -I https://ruttrack.site/login` → 200 + `Content-Type: text/html` 5) `curl -I https://ruttrack.site/admin/dashboard` → 200 + SPA fallback |
| Landing HTML CTA button points to `/login` | INFRA-v9-04 | Static HTML edit, no unit test framework for landing | 1) `grep -n 'ruttrack.site/admin' frontends/landing/dist/index.html` → 0 matches 2) `grep -n 'href="/login"' frontends/landing/dist/index.html` → ≥1 match |
| All 4 roles reach correct dashboard from `/login` E2E | AUTH-v9-01, AUTH-v9-02, AUTH-v9-03, AUTH-v9-04 | Requires running backend + real JWT; vitest covers unit paths but not full round-trip | 1) Login as `admin` → `/admin/dashboard` 2) Login as `teacher` → `/teacher/dashboard` 3) Login as `student` → `/student/dashboard` 4) Login as headman student → `/headman/dashboard` |
| Logout clears tokens and revokes on server | AUTH-v9-06 | Requires live backend token revoke endpoint | 1) Login 2) Click logout 3) Verify `AuthService.currentUser()` returns null 4) Verify backend `POST /api/auth/logout` called with valid token |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
