---
phase: 50-basehref-migration-unified-login
plan: 06
subsystem: web-panel
tags: [vitest, ng-build, regression, uat, phase-gate, web-panel]
status: awaiting-human-uat

# Dependency graph
requires:
  - phase: 50
    plan: 01
    provides: "AuthUser + resolveDashboardFor + JWT claim parsing"
  - phase: 50
    plan: 02
    provides: "studentGuard, headmanGuard, guestGuard"
  - phase: 50
    plan: 03
    provides: "Placeholder components + /student/*, /headman/* routes + guestGuard on /login"
  - phase: 50
    plan: 04
    provides: "login.component + role.guard using resolveDashboardFor"
  - phase: 50
    plan: 05
    provides: "angular.json baseHref / + prod nginx catch-all + landing /login footer"
provides:
  - "Phase 50 regression gate closed at the automated level (162 vitest tests green, prod ng build success)"
  - "Filled per-task verification map in 50-VALIDATION.md (status: draft -> ready)"
  - "Phase 50 ready for human UAT checkpoint covering the 6 ROADMAP success criteria"
affects:
  - "Phase 50 /gsd-verify-work / /gsd-complete-phase (unblocked once human UAT approves)"
  - "Phase 51-55 Student/Headman web cabinets (inherit the single-SPA baseHref / model validated here)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Phase-gate regression plan: one automated task (full suite + prod build + VALIDATION fill) followed by one blocking human checkpoint"
    - "Automated verification records stored as execution evidence inside VALIDATION.md for future audit"
    - "Worktree-stale fallback: run the real test commands in the main repo checkout (with installed node_modules) and commit only the planning artefacts in the worktree"

key-files:
  created:
    - .planning/phases/50-basehref-migration-unified-login/50-06-SUMMARY.md
  modified:
    - .planning/phases/50-basehref-migration-unified-login/50-VALIDATION.md

key-decisions:
  - "Ran vitest + ng build directly in the main repo checkout rather than base-syncing the entire web-panel source tree into the worktree. The main repo is already at HEAD 2887a71 with all Plans 01-05 landed; synchronising source into the worktree would duplicate commits without value. The worktree only carries the planning artefacts (.planning/phases/50-*) which this plan actually edits."
  - "Created this SUMMARY.md with explicit status: awaiting-human-uat so that the file persists the automated completion while the ROADMAP marking + final sign-off are deferred until after the human checkpoint resolves. No STATE/ROADMAP updates made in this pass — per orchestrator instructions."

requirements-completed-automated: [AUTH-v9-07]
requirements-pending-human-uat: [AUTH-v9-01, AUTH-v9-02, AUTH-v9-03, AUTH-v9-04, AUTH-v9-05, AUTH-v9-06, INFRA-v9-04]

# Metrics
duration: ~15min
completed: 2026-04-09 (automated portion)
human-uat-completion: pending
---

# Phase 50 Plan 06: Full Vitest Regression + Production Build + Human UAT Gate Summary

**Ran the full vitest suite and a production Angular build against the final state of Phase 50 code, filled in the per-task verification map in `50-VALIDATION.md`, and paused at the blocking human-verify checkpoint for the 6 ROADMAP success criteria. Automated portion complete; awaiting human UAT sign-off to finalize Phase 50.**

## Status: AWAITING HUMAN UAT

Task 1 (automated) — **COMPLETE**
Task 2 (human-verify) — **BLOCKED ON HUMAN**

The human operator must work through the 6 ROADMAP success criteria (replicated in the Plan 50-06 `<how-to-verify>` section) against the deployed environment and respond either `approved` (phase 50 may close) or with a description of failures (executor will be re-spawned to fix).

## Performance

- **Duration (automated portion):** ~15 min
- **Started:** 2026-04-09T03:00:00Z
- **Automated portion completed:** 2026-04-09T03:15:00Z
- **Human UAT:** pending
- **Tasks:** 2 (1 automated, 1 human-verify)
- **Files modified:** 1 (`50-VALIDATION.md`)
- **Files created:** 1 (this SUMMARY)

## Accomplishments (Automated Portion)

### 1. Full vitest regression — GREEN

- Command: `node node_modules/vitest/dist/cli.js run`
- Location: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/web-panel/` (main repo, HEAD `2887a71`)
- Result: **162 passed / 162 total across 25 test files**. Exit code 0.
- Duration: 34.11s (jsdom cold-start dominates environment phase)
- Phase-50 surface coverage:
  - `auth.service.spec.ts` — 21 tests
  - `auth.guard.spec.ts` — 9 tests (roleGuard fallback covered for all 4 roles via `resolveDashboardFor`)
  - `student.guard.spec.ts` — 5 tests
  - `headman.guard.spec.ts` — 5 tests
  - `guest.guard.spec.ts` — 5 tests
  - `login.component.spec.ts` — 12 tests (includes new STUDENT + headman redirects via `navigateByUrl`)
  - `sidebar.component.spec.ts` — 5 tests (includes STUDENT no-nav-items D-06 check)
- **AUTH-v9-07 requirement** (129+ tests green) **closed at the automated level** — 162 is 33 above the floor.

### 2. Production Angular build — GREEN

- Command: `node node_modules/@angular/cli/bin/ng.js build --configuration production`
- Location: same as vitest
- Result: **Application bundle generation complete in 6.867 seconds**. Exit code 0.
- Two non-blocking warnings (neither caused by Phase 50 work):
  1. `TS-998113: All imports are unused` for `journal-cell.component.ts:8` — pre-existing, already noted in Plan 05 Issues Encountered as deferred cleanup candidate
  2. `bundle initial exceeded maximum budget`: 652.04 kB vs 500.00 kB budget — pre-existing from Phase 39/40 shell, tracked separately
- BaseHref verification in emitted bundle:
  - `dist/browser/index.html:6` = `<base href="/">` (1 match)
  - `<base href="/admin/">` = 0 matches
- **INFRA-v9-04 requirement** (baseHref migrated to /) **closed at build level**.

### 3. VALIDATION.md filled

- Frontmatter status: `draft` -> `ready`
- Frontmatter `nyquist_compliant`: `false` -> `true`
- Frontmatter `wave_0_complete`: `false` -> `true`
- Per-Task Verification Map: placeholder row replaced with 13 filled rows (2 per plan for plans 01-04, 2 rows for plan 05, 2 rows for plan 06)
- Added "Plan 06 Task 1 Execution Evidence" subsection with verbatim test counts and build timings
- Manual-Only Verifications table: confirmed landing grep results (`ruttrack.site/admin` = 0, `href="/login"` = 4) with 2026-04-09 timestamps
- Wave 0 Requirements checklist: all boxes checked
- Validation Sign-Off: all 6 bullets checked, approval line reads `pending — ждёт human UAT checkpoint (Task 2 of Plan 06)`

## Task Commits

Each task was committed atomically with `--no-verify` (parallel executor convention).

0. **Base sync (pre-Task 1):** `chore(50-06): base-sync phase 50 planning artefacts from main` — `b9cb7cd`
   _(Worktree was at stale `d6df3c2` without any `.planning/phases/50-*` directory. This commit copies all 15 phase-50 planning files — plans, summaries, context, research, validation, discussion log — from main HEAD so Plan 06 can edit VALIDATION and create SUMMARY inside the worktree tree. Orchestrator cherry-pick will no-op the already-present files on main.)_
1. **Task 1 (automated):** `feat(50-06): fill per-task verification map + record regression evidence (AUTH-v9-07)` — `cae2edb`
2. **Task 2 (human-verify):** **NOT COMMITTED YET** — pending human UAT approval. Final SUMMARY-finalize commit will land after the human checkpoint resolves.

## Files Created/Modified

### This plan:
- `.planning/phases/50-basehref-migration-unified-login/50-VALIDATION.md` — filled per-task map, status -> ready, nyquist_compliant -> true, evidence subsection added
- `.planning/phases/50-basehref-migration-unified-login/50-06-SUMMARY.md` — this file (status: awaiting-human-uat)

### Not touched by this plan (final state exists in main repo from Plans 01-05):
- `frontends/web-panel/src/app/core/auth/auth.service.ts` (Plan 01)
- `frontends/web-panel/src/app/core/auth/auth.service.spec.ts` (Plan 01)
- `frontends/web-panel/src/app/core/auth/student.guard.ts`, `.spec.ts` (Plan 02)
- `frontends/web-panel/src/app/core/auth/headman.guard.ts`, `.spec.ts` (Plan 02)
- `frontends/web-panel/src/app/core/auth/guest.guard.ts`, `.spec.ts` (Plan 02)
- `frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts` (Plan 03)
- `frontends/web-panel/src/app/features/headman/headman-placeholder/headman-placeholder.component.ts` (Plan 03)
- `frontends/web-panel/src/app/app.routes.ts` (Plan 03)
- `frontends/web-panel/src/app/features/login/login.component.ts`, `.spec.ts` (Plan 04)
- `frontends/web-panel/src/app/core/auth/role.guard.ts` (Plan 04)
- `frontends/web-panel/src/app/core/auth/auth.guard.spec.ts` (Plan 04)
- `frontends/web-panel/angular.json` (Plan 05 — baseHref `/admin/` -> `/`)
- `nginx/conf.d/default.conf` (Plan 05 — removed `/admin/` block, added catch-all `/`)
- `frontends/landing/dist/index.html` (Plan 05 — footer link `/admin/` -> `/login`)

## Decisions Made

- **Run tests in the main repo, not the worktree.** The worktree base is `d6df3c24` (deployment snapshot) and has no `node_modules`, no Plans 01-05 code changes. Base-syncing the entire web-panel tree into the worktree would duplicate tens of source files and inflate the cherry-pick diff without providing any additional verification value (the code in main is already at HEAD 2887a71 — the final Phase 50 state — since Plans 01-05 were cherry-picked/merged by the orchestrator). I ran `npm ci` in main (971 packages) then executed vitest + ng build there directly. The worktree only carries `.planning/phases/50-*` edits, which are the artefacts this plan actually produces.
- **Partial SUMMARY.md with `status: awaiting-human-uat`.** Orchestrator instructions explicitly permit creating a partial SUMMARY before the human checkpoint if the plan structure allows it. The frontmatter status field makes the awaiting-human-verification state machine-readable. The final SUMMARY-finalize commit (flipping status, marking requirements fully complete) will happen in a continuation pass after the human checkpoint resolves.
- **No STATE.md, no ROADMAP.md updates in this pass.** Prompt explicitly says "Do NOT update STATE.md or ROADMAP.md" — those live at the phase-completion level and depend on the human sign-off.

## Deviations from Plan

**None.** The plan is executed exactly as written for the automated portion. Task 1 action steps (vitest run, ng build, VALIDATION map fill, evidence recording) are all done; acceptance criteria met:

- `cd frontends/web-panel && npx vitest run` -> exit 0 ✓ (162 passed)
- `cd frontends/web-panel && npx ng build --configuration production` -> exit 0 ✓ (6.867s)
- `grep -c 'base href="/"' dist/browser/index.html` -> 1 ✓
- `grep -c 'base href="/admin/"' dist/browser/index.html` -> 0 ✓
- `grep -c "nyquist_compliant: true" 50-VALIDATION.md` -> 2 ✓ (frontmatter + checkbox, exceeds the criterion of 1)
- `grep -c "^| 01-T1" 50-VALIDATION.md` -> 1 ✓
- `grep -c "^| 06-T2" 50-VALIDATION.md` -> 1 ✓
- All 13 verification-map rows present ✓
- Total regression tests = 162 >= 146 ✓

Task 2 is the blocking human checkpoint — deferred by design, not a deviation.

## Issues Encountered

1. **Worktree has no `.planning/phases/50-*` directory.** Handled via base-sync commit `b9cb7cd` that copies the phase 50 planning tree from main HEAD into the worktree.
2. **Worktree has no `node_modules`, main repo's `node_modules/` is empty.** Ran `npm ci` in main repo (971 packages installed in ~24s). Both vitest and ng-build work normally thereafter.
3. **Jsdom + chart.js `getContext()` warnings in subject-chart.component.spec.ts.** Pre-existing — tests still pass despite the console noise. Logged for eventual canvas-polyfill hygiene work, not a Phase 50 regression.
4. **Intermediate plan summaries show varying test counts (131 / 142 / 146 / 147 / 157) vs the final 162.** This is a book-keeping artefact: each plan ran in a separate worktree with different base-sync timings and different partial views of the other plans' work. The authoritative number is the **end-of-phase count in main HEAD: 162**, which is what AUTH-v9-07 asks for.

## User Setup Required (for human UAT to proceed)

The human operator must have the production environment deployed with Phase 50 changes before running the UAT checklist. Typical steps (documented in Plan 06 `<how-to-verify>` preamble):

```bash
# 1. Rebuild web-panel nginx image with the new baseHref + nginx config
docker compose build rct-web-panel-nginx rct-nginx

# 2. Recreate containers with new images
docker compose up -d --force-recreate rct-web-panel-nginx rct-nginx

# 3. Confirm all containers healthy
docker compose ps
```

If deploying to prod via GitHub Actions, merge the phase 50 commits to main and let the deploy workflow handle image build + container restart.

## Known Stubs

Inherited from Plan 03 and explicitly out of scope for Phase 50:

| File | Stub | Resolved In |
|------|------|-------------|
| `frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts` | Text "Кабинет студента появится в Фазе 51" | Phase 51-53 |
| `frontends/web-panel/src/app/features/headman/headman-placeholder/headman-placeholder.component.ts` | Text "Кабинет старосты появится в Фазе 54" | Phase 54-55 |

These are intentional placeholder components used to verify the guard/route flow. They do not block Phase 50 success criteria — criteria 1, 3, and 4 check *routing* behaviour, not content.

## Human UAT Checklist (Replicated from Plan 06 for Checkpoint Visibility)

The human operator must verify all 6 ROADMAP success criteria on the deployed environment:

1. **4-role post-login routing:** Login as `admin` -> `/admin/dashboard`; `teacher` -> `/teacher/dashboard`; plain `student` -> `/student/dashboard` (placeholder "Кабинет студента появится в Фазе 51"); headman student -> `/headman/dashboard` (placeholder "Кабинет старосты появится в Фазе 54").
2. **AuthService.currentUser() signal exposes JWT claims:** On a logged-in page, verify `currentUser()` has `id`, `role`, `isHeadman`, `groupId` — observable via Angular DevTools or `atob(localStorage.getItem('accessToken').split('.')[1])`.
3. **headmanGuard blocks plain STUDENT:** As plain student, navigate to `/headman/dashboard` -> URL auto-changes to `/student/dashboard` (no headman placeholder shown).
4. **studentGuard lets headman through:** As headman, navigate to `/student/schedule` -> page opens with the "Кабинет студента" placeholder.
5. **Logout flow:** From any dashboard, click logout -> redirect to `/login`, tokens cleared, `POST /api/auth/logout` observed in Network tab with 200 OK.
6. **129+ vitest tests green:** Already verified in Task 1 — **162 passed**. Human only needs to visually confirm this count from VALIDATION.md or this SUMMARY.

Additional smoke checks after deploy (Plan 05 Task 2 manual):

```bash
curl -I https://ruttrack.site/         # expect 301 Moved Permanently, Location: /login
curl -I https://ruttrack.site/login    # expect 200 OK, text/html
curl -I https://ruttrack.site/api/health  # expect gateway proxy (not web-panel)
curl -I https://ruttrack.site/admin/dashboard  # expect 200 OK (SPA fallback)
```

Landing footer visual check: open `https://ruttrack.site/presentation/`, scroll to footer, confirm "Войти" button links to `/login` (not "Панель администратора" linking to `/admin/`).

## Next Phase Readiness (after human UAT approved)

- **/gsd-verify-work:** may be run to cross-check SUMMARY claims against filesystem/commits
- **/gsd-complete-phase:** may be run to finalize STATE.md, update ROADMAP.md (5/6 -> 6/6, status Complete, completion date 2026-04-09), mark all 8 Phase 50 requirements complete, and commit the metadata. Only after human UAT says `approved`.
- **Phase 51 (Student Web Cabinet — Shell + Schedule + Check-in):** unblocked by Phase 50 completion. `/student/dashboard`, `/student/schedule` routes exist (placeholders); Phase 51 replaces placeholder components with real shells.

## Self-Check: PASSED (automated portion)

- Files created/modified verified in git status and filesystem:
  - FOUND: `.planning/phases/50-basehref-migration-unified-login/50-VALIDATION.md` (modified)
  - FOUND: `.planning/phases/50-basehref-migration-unified-login/50-06-SUMMARY.md` (this file, being created)
- Commits verified via `git log --oneline -3`:
  - FOUND: `b9cb7cd` chore(50-06): base-sync phase 50 planning artefacts from main
  - FOUND: `cae2edb` feat(50-06): fill per-task verification map + record regression evidence (AUTH-v9-07)
- Automated acceptance criteria (Task 1): all 8 grep/build/vitest checks pass — see Deviations section
- Plan 06 Task 2 acceptance criterion ("human sign-off"): **BLOCKED ON HUMAN**, self-check not applicable
- This SUMMARY will be self-checked a second time after the human checkpoint resolves and the FINAL commit lands.

---
*Phase: 50-basehref-migration-unified-login*
*Plan: 06*
*Automated portion completed: 2026-04-09*
*Phase completion: pending human UAT*
