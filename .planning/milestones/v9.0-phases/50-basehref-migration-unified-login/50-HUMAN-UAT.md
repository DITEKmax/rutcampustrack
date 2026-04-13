---
status: resolved
phase: 50-basehref-migration-unified-login
source: [50-VERIFICATION.md, 50-06-SUMMARY.md]
started: 2026-04-09
updated: 2026-04-09
approver: maksd
approval_method: manual UAT after nginx reload hotfix
---

## Current Test

[completed]

## Tests

### 1. 4-role post-login routing (AUTH-v9-01..04)
expected: admin → /admin/dashboard, teacher → /teacher/dashboard, plain student → /student/dashboard with placeholder text "Кабинет студента появится в Фазе 51", headman student → /headman/dashboard with placeholder text "Кабинет старосты появится в Фазе 54"
result: passed (after deploy hotfix, see Gaps section)

### 2. JWT claims accessibility via currentUser()
expected: AuthService.currentUser() signal exposes role, isHeadman, groupId derived from JWT payload
result: passed (verified by 162 vitest unit tests; manual JWT inspection skipped because AuthService stores tokens in Angular signals only, not localStorage — by design, see Notes)

### 3. headmanGuard blocks plain STUDENT (AUTH-v9-04)
expected: plain student manually hitting /headman/dashboard auto-redirects to /student/dashboard
result: passed (after deploy hotfix)

### 4. studentGuard passes headman (AUTH-v9-05)
expected: headman student opens /student/schedule and sees the student placeholder without redirect
result: passed (after deploy hotfix)

### 5. Logout flow (AUTH-v9-06)
expected: logout button → /login redirect → revisiting /admin/dashboard → /login + POST /api/auth/logout 200 OK
result: passed (after deploy hotfix)

### 6. 129+ vitest tests green (AUTH-v9-07)
expected: vitest suite passes with at least 129 tests (baseline)
result: passed — 162/162 across 25 files (auto-verified in Plan 06 Task 1)

## Summary

total: 6
passed: 6
issues: 0
pending: 0
skipped: 0
blocked: 0

## Notes

- **Token persistence design:** AuthService.setTokens() writes to Angular signals only (in-memory), not localStorage. Refreshing the page kills the session. This is pre-existing project behavior, NOT a Phase 50 regression. Carry-over candidate for future phase if persistent sessions are desired.
- **Deploy hotfix backstory:** initial UAT on production failed because rct-nginx kept the old config in memory (file on disk was updated by git pull but the nginx process was never reloaded). All 4 roles incorrectly landed on /home (mini-app fallback). Manual `docker exec rct-nginx nginx -s reload` fixed it immediately. The deploy workflow has been patched in commit b391fb9 to auto-reload nginx and run a /login smoke test, so this won't recur.

## Gaps

### Gap 1: deploy workflow did not auto-reload nginx after git pull
status: resolved
detected: 2026-04-09 during Phase 50 UAT
resolved: 2026-04-09 commit b391fb9
fix: deploy.yml now runs `nginx -t && nginx -s reload && curl smoke test` after git pull. docker-compose.prod.yml safety-net reload loop reduced from 6h to 5m. Smoke test fails the deploy if /login response is < 5000 bytes.
