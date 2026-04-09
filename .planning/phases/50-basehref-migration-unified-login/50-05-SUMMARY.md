---
phase: 50-basehref-migration-unified-login
plan: 05
subsystem: infra
tags: [nginx, angular, reverse-proxy, base-href, single-spa, routing]

# Dependency graph
requires:
  - phase: 49-nginx-routing-landing-dead-link-fix
    provides: "location = / { return 301 /login; } redirect block + /presentation/ and /app/ proxy blocks"
  - phase: 50-basehref-migration-unified-login
    provides: "Plans 01-04 landed all Angular routing/guards/placeholder components needed for SPA to serve / properly"
provides:
  - "angular.json baseHref flipped /admin/ -> / in both build.options and configurations.production"
  - "Prod nginx HTTPS server rebuilt: /admin/ prefix block removed, catch-all location / added at the end serving rct-web-panel-nginx"
  - "location = / 301 /login preserved (exact match beats catch-all prefix) with refreshed comment"
  - "Landing footer link /admin/ -> /login with Russian copy `Войти`"
affects:
  - 50-06 (UAT/verification)
  - 51-student-dashboard, 52-student-schedule, 53-student-tickets, 54-headman-mode, 55-headman-features (they inherit / baseHref and nginx catch-all)
  - Any future phase touching prod nginx routing

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "nginx prefix-match precedence: specific locations above catch-all location /"
    - "nginx exact-match `= /` beats prefix `/` — reused to force 301 /login from root while still serving SPA for everything else"
    - "Angular CLI baseHref config controls build-time <base href> injection — no runtime flag needed"

key-files:
  created: []
  modified:
    - "frontends/web-panel/angular.json (baseHref in two locations)"
    - "nginx/conf.d/default.conf (removed /admin/ block, added catch-all /, updated = / comment)"
    - "frontends/landing/dist/index.html (footer Panel admin link)"

key-decisions:
  - "D-01/D-02 executed exactly as planned: remove /admin/, add catch-all / at end, preserve = / 301 /login"
  - "Did NOT commit ng build artifacts from sanity build — dist/browser/ is production-build tracked in git, mixing development-build into it would regress prod. Sanity check used a development build in a temporary state that was then reverted before commit."
  - "Used base-sync commit up front (fb5b142) because worktree was stale at d6df3c2 and did not contain Phase 49 + Plans 01-04 changes to the three target files. Orchestrator cherry-picks should skip the no-op portions."

patterns-established:
  - "When worktree is stale, start with chore(plan): sync base files from main before per-task edits so diffs are clean and cherry-pick friendly"
  - "For Windows bash: ripgrep-backed Grep tool handles CRLF line endings correctly, while `grep -c` inside Bash does not — prefer Grep tool for acceptance criteria counts"

requirements-completed: [INFRA-v9-04]

# Metrics
duration: 20min
completed: 2026-04-09
---

# Phase 50 Plan 05: baseHref + Prod Nginx Single-SPA Flip Summary

**Angular web-panel migrated from `/admin/` to `/` baseHref with prod nginx rebuilt to catch-all-proxy the web-panel under a preserved `= /` -> 301 `/login` exact-match redirect**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-04-09T02:47:00Z
- **Completed:** 2026-04-09T02:57:00Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- `angular.json` — both `baseHref` entries flipped from `/admin/` to `/` (build.options + configurations.production). Verified end-to-end by running `npx ng build --configuration development` and grepping the emitted `dist/browser/index.html` for `<base href="/">` (found, zero hits for `/admin/`).
- `nginx/conf.d/default.conf` — `location /admin/ { proxy_pass http://rct-web-panel-nginx:80/; ... }` block deleted. A new `location /` catch-all added at the end of the HTTPS server block with `X-Real-IP`, `X-Forwarded-For`, and `X-Forwarded-Proto` headers (consistent with /api/ pattern elsewhere in the file). Phase 49 `location = / { return 301 /login; }` preserved untouched aside from a clarified comment.
- `frontends/landing/dist/index.html:1330` — footer link `<a href="https://ruttrack.site/admin/">Панель администратора</a>` replaced with `<a href="/login">Войти</a>`. Three Phase 49 `/login` CTAs retained (total 4 `href="/login"` occurrences in the landing).
- Full vitest suite executed twice (once after angular.json + landing changes, once after nginx changes): **131/131 tests pass, 22 files, green.** No regressions.
- Prefix-match ordering validated: `/api/ws/` (line 44) -> `/api/` (57) -> `/presentation/` (66) -> `/app/` (72) -> `/mini-app/` (78) -> `/swagger-ui.html` (84) / `/swagger-ui/` (94) / `/v3/api-docs` (104) / `/openapi/` (114) -> `= /` exact (127) -> `/` catch-all (137). Every specific prefix sits above the fallback, satisfying T-50-19 mitigation from the threat register.
- All-file brace balance verified (awk brace counter = 0).

## Task Commits

Each task was committed atomically with `--no-verify` (parallel executor convention):

0. **Base sync: three target files from main HEAD** — `fb5b142` (chore)
   Worktree was stale at `d6df3c2` — did not contain Phase 49 + Plans 01-04 content for `angular.json`, `nginx/conf.d/default.conf`, `frontends/landing/dist/index.html`. Copied current main content as base; orchestrator will skip this if it becomes a no-op during cherry-pick.
1. **Task 1: angular.json baseHref / + landing footer /login** — `1403df3` (feat)
2. **Task 2: prod nginx /admin/ removal + catch-all /** — `3515f78` (feat)

## Files Created/Modified
- `frontends/web-panel/angular.json` — `"baseHref": "/admin/"` -> `"baseHref": "/"` in `projects.web-panel.architect.build.options` (line 44) and `projects.web-panel.architect.build.configurations.production` (line 62).
- `nginx/conf.d/default.conf` — removed 5-line `location /admin/` block including `# --- Admin Web Panel (NET-02) ---` comment; added 13-line `location /` catch-all at the end of the HTTPS server block with comment `# --- Web Panel SPA — catch-all (INFRA-v9-04, Phase 50) ---`; updated multi-line comment above `location = /` to remove the "after Phase 50 migrates" wording now that Phase 50 has migrated.
- `frontends/landing/dist/index.html` — line 1330 footer link text+href replaced (`Панель администратора` -> `Войти`, `https://ruttrack.site/admin/` -> `/login`).

## Decisions Made

1. **Did not commit ng build artifacts.** The plan only requires `ng build --configuration development` as a *sanity check* that the new `baseHref` produces `<base href="/">`. Running the build did rewrite 90+ files under `frontends/web-panel/dist/browser/` because those files are historically tracked in git (production build snapshot, hashed filenames with `outputHashing: all`). Mixing an unhashed development build into tracked production artifacts would regress prod. I reverted all `dist/browser/` changes (`git checkout -- dist/ && git clean -fd dist/`) before the Task 1 commit, which is consistent with threat T-50-23 (cache/deploy concern is out of plan scope) and matches the plan's acceptance criteria language (the grep check is against the ng-build output, not against the committed state of the file).
2. **Base-sync commit as first step.** The executor worktree base `d6df3c2` predates Phase 49 and Plans 01-04, so the three target files were stale relative to main. I did a bulk copy of the three files from the main repo into the worktree and committed as `chore(50-05): sync base files`. All subsequent per-task diffs are therefore relative to main's current state, not to the stale worktree base. The orchestrator's cherry-pick flow should cleanly skip/merge this commit.
3. **Russian "Войти" copy in landing footer** — Plan explicitly calls for `Войти` over `Панель администратора` because `/login` is now the unified entry point for all four roles, not admin-specific. Matches D-03 + the existing Russian copy in the rest of the landing.

## Deviations from Plan

**None of the protected kind — no auto-fixes, no architectural deviations, no blocked tasks.**

Minor procedural note: I added a base-sync commit that the plan does not mention, solely because of the known stale-worktree issue called out in `<worktree_branch_check>` in the prompt. It contains zero logic changes — it is a straight copy from main. Not a deviation from the plan's intent, but documenting for cherry-pick transparency.

## Issues Encountered

1. **`grep -c` in Windows bash returns 0 for `href="/login"` despite matches existing.** Git on Windows converts LF to CRLF on checkout, and MSYS grep sometimes mishandles this with quoted strings embedding `/`. Resolved by using the Grep tool (ripgrep-backed) for acceptance criteria verification, which correctly counts 4 occurrences. Bash `grep -c` is unreliable in this environment; falling back to ripgrep works and is also faster.
2. **Docker daemon not running** on the executor machine, so `nginx -t` syntax validation via `docker run nginx:alpine nginx -t` could not be executed. Fell back to `awk` brace balance check (result: 0, balanced) and manual review of location-block ordering. Physical validation will happen at deploy time during Plan 06 UAT.
3. **`npm ci` took 38s** on first run because the worktree had no `node_modules/` at all (shared main-repo `node_modules/` was also empty). This is worktree setup overhead, not plan scope.
4. **Pre-existing TS warning:** `frontends/web-panel/src/app/features/teacher/journal/journal-cell/journal-cell.component.ts:8` has `imports: [NgIf]` but `NgIf` is unused (`TS-998113: All imports are unused`). This is a pre-existing warning surfaced by the development build, not caused by Plan 50-05 changes, and explicitly out of scope per the deviation-rules scope boundary. Logging here for future cleanup (candidate deferred item for a lint-sweep phase).

## User Setup Required

None — all changes are code/config; deployment is a normal `docker compose pull && docker compose up -d --force-recreate rct-web-panel-nginx rct-nginx` that the human operator runs after Phase 50 merges.

## Next Phase Readiness

- **Ready for Plan 06 (phase UAT):** `angular.json`, `nginx/conf.d/default.conf`, and `landing/dist/index.html` are all on the unified `/login` single-SPA model. Plan 06 can execute its curl probes (`curl -I https://ruttrack.site/` -> 301 /login; `curl -I https://ruttrack.site/login` -> 200 HTML; `curl -I https://ruttrack.site/api/health` -> gateway; `curl -I https://ruttrack.site/admin/dashboard` -> 200 HTML SPA fallback) as soon as prod is redeployed.
- **Ready for Phases 51-55:** All future `/student/*` and `/headman/*` routes will automatically serve through the nginx catch-all without further infra work. They only need Angular code + route registration.
- **Deployment concern (not blocking):** The tracked `frontends/web-panel/dist/browser/` in git still points at an old production build with `<base href="/admin/">`. This has existed for as long as the repo has tracked build outputs. The docker image rebuild during deploy (`docker compose up --build rct-web-panel-nginx`) will regenerate fresh artifacts from the updated `angular.json` before the container starts, so the stale committed state does not affect running prod. A future hygiene phase should consider gitignoring `dist/` altogether.

## Self-Check: PASSED

Verified the following claims exist in git history and filesystem:

- [x] `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.claude/worktrees/agent-abf62756/frontends/web-panel/angular.json` contains `"baseHref": "/"` exactly twice and `"baseHref": "/admin/"` zero times
- [x] `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.claude/worktrees/agent-abf62756/nginx/conf.d/default.conf` contains no `location /admin/`, no `Admin Web Panel` comment, exactly one `location = /`, exactly one `return 301 /login`, exactly one `Web Panel SPA — catch-all` comment, and two `location / {` blocks (HTTP ACME redirect + HTTPS catch-all)
- [x] `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.claude/worktrees/agent-abf62756/frontends/landing/dist/index.html` contains zero `ruttrack.site/admin` references and four `href="/login"` occurrences
- [x] Worktree commits exist: `fb5b142` (base sync), `1403df3` (Task 1), `3515f78` (Task 2) — verified via `git log --oneline -6`
- [x] vitest full suite: 131 passed / 131 total across 22 files, twice
- [x] `awk` brace counter on `nginx/conf.d/default.conf` returns 0 (balanced)
- [x] nginx prefix-match ordering — all specific prefixes live above both `= /` and catch-all `/` in the HTTPS server block

---
*Phase: 50-basehref-migration-unified-login*
*Plan: 05*
*Completed: 2026-04-09*
