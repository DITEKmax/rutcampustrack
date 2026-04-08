---
phase: 49-nginx-routing-landing-dead-link-fix
verified: 2026-04-09T00:30:00Z
status: human_needed
score: 5/5 must-haves verified (offline) — 3 items require live-environment UAT
re_verification: false
human_verification:
  - test: "Visit https://ruttrack.site/ in a browser (or curl -sI) after deploy"
    expected: "HTTP/2 301 with Location: /login header — root no longer serves PWA content"
    why_human: "Live routing behavior cannot be verified without a running nginx reverse proxy terminating TLS; offline grep confirmed the config directives but actual 301 emission requires the deployed container"
  - test: "Visit https://ruttrack.site/presentation/ after deploy and confirm landing HTML renders"
    expected: "HTTP/2 200 with landing <title>RutCampusTrack</title>; no 404 or upstream errors"
    why_human: "Proxy hop through rct-landing-nginx upstream can only be verified with a live container network"
  - test: "Visit https://ruttrack.site/app/ after deploy and confirm React PWA loads"
    expected: "HTTP/2 200 with PWA <title>RutTrack</title>; static assets resolve correctly with the /app/ prefix stripped by nginx trailing-slash proxy_pass"
    why_human: "Prefix stripping + PWA SPA fallback behavior must be tested with the live PWA container"
  - test: "Run `docker run --rm -v $(pwd)/nginx/conf.d:/etc/nginx/conf.d:ro -v $(pwd)/nginx/nginx.conf:/etc/nginx/nginx.conf:ro nginx:1.27-alpine nginx -t` on a Docker-enabled host"
    expected: "Output contains 'nginx: configuration file /etc/nginx/nginx.conf test is successful' (SSL cert emit errors are tolerated — only syntax errors fail)"
    why_human: "Docker daemon was unavailable in executor worktree environment; the plan explicitly documented this as a fallback-to-UAT scenario. Verifier applied grep-based directive checks and manual brace-balance audit as compensating evidence, but a true nginx -t run on a host with Docker is the authoritative syntax validator"
  - test: "Run `docker compose -f docker-compose.prod.yml build pwa-nginx mini-app-nginx web-panel-nginx landing-nginx` on a Docker-enabled host"
    expected: "All 4 frontend images build successfully with exit code 0; no ERROR lines in output"
    why_human: "Same Docker daemon unavailability in executor worktree. Plan's explicit downgrade clause was exercised (verify Dockerfiles and per-container nginx.conf files are git-clean). Full rebuild smoke test must run before production deploy"
  - test: "After pushing to main, verify `.github/workflows/deploy.yml` Build and push landing step succeeds on GitHub Actions"
    expected: "gh run list --workflow=deploy.yml --limit 1 shows status: success; the landing Docker layer cache is invalidated by the dist/index.html content change and the new image is pushed to GHCR"
    why_human: "GitHub Actions CI run is external to this verification environment; must be observed after the merge commit lands on main"
  - test: "Click each of the three 'Открыть в Telegram' / 'Открыть' buttons on the deployed landing at /presentation/"
    expected: "Each click navigates the browser to /login (may 404 until Phase 50 ships — acceptable per phase boundary); NO button ever navigates to https://t.me/"
    why_human: "User-interaction test — offline grep confirmed href values but actual click behavior requires rendered DOM"
  - test: "Click the 'Мобильная версия (PWA)' button on the deployed landing"
    expected: "Navigates to /app/ and serves the React PWA — NOT the old 301 ping-pong to /login"
    why_human: "User-interaction test dependent on both Plan 01 nginx routing and Plan 02 landing href being deployed together"
---

# Phase 49: Nginx Routing + Landing Dead Link Fix — Verification Report

**Phase Goal:** Production-critical routing bugs are fixed — root URL no longer sends all users to the student PWA, landing is accessible at `/presentation/`, and three dead `https://t.me/` links in the landing are replaced with `/login`.

**Verified:** 2026-04-09 00:30:00 UTC
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Visiting `https://ruttrack.site/` redirects to `/login` (React PWA not served at root) | ? UNCERTAIN (offline VERIFIED) | `nginx/conf.d/default.conf:133-135` contains `location = /` with `return 301 /login;` — exact-match modifier ensures only literal `/` triggers; grep `return 301 /login` = 1, `location = /` = 1. Live HTTP behavior requires deployed container (routed to human UAT) |
| 2 | Visiting `https://ruttrack.site/presentation/` serves the landing HTML without errors | ? UNCERTAIN (offline VERIFIED) | `nginx/conf.d/default.conf:66-69` contains `location /presentation/ { proxy_pass http://rct-landing-nginx:80/; proxy_set_header Host $host; }` — identical syntax pattern to the known-good `/admin/` and `/mini-app/` blocks. Live proxy hop requires deploy (routed to human UAT) |
| 3 | Visiting `https://ruttrack.site/app/` serves the React PWA (moved off root) | ? UNCERTAIN (offline VERIFIED) | `nginx/conf.d/default.conf:72-75` contains `location /app/ { proxy_pass http://rct-pwa-nginx:80/; proxy_set_header Host $host; }` — trailing-slash prefix stripping applied, consistent with other frontend blocks. Live behavior requires deploy (routed to human UAT) |
| 4 | All "Открыть в Telegram" / "Войти" buttons on the landing navigate to `/login` — none go to `https://t.me/` | ✓ VERIFIED | `grep -c 't\.me/' frontends/landing/dist/index.html` = 0; `grep -c 'href="/login"' frontends/landing/dist/index.html` = 3 at lines 1029 (header "Открыть"), 1107 (hero CTA), 1306 (bottom CTA); icons + button text preserved per plan |
| 5 | GitHub Actions CI passes with no modification to `.github/workflows/*.yml`; all 4 frontend Docker images rebuild | ✓ VERIFIED (offline) / ? UNCERTAIN (runtime) | `git diff f6a7c84 HEAD -- .github/workflows/` is empty; `git status --porcelain .github/workflows/` is empty. All 4 Dockerfiles and per-container nginx.conf files are git-clean. Actual Docker rebuild and GitHub Actions run deferred to live CI/CD (human UAT) |

**Score:** 5/5 truths verified at code level; 4 of 5 truths require live-environment confirmation for observable end-to-end behavior (Truth #4 is the only one fully verifiable offline via grep).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `nginx/conf.d/default.conf` | Reverse proxy with `/ → 301 /login`, `/presentation/ → landing`, `/app/ → PWA`, no `/landing/` block | ✓ VERIFIED | gsd-tools `verify artifacts` returned `all_passed: true`. Contains required `return 301 /login`, `location /presentation/`, `location /app/`, `proxy_pass http://rct-landing-nginx:80/`, `proxy_pass http://rct-pwa-nginx:80/`. No `location /landing/` remains (grep = 0). Security headers + TLS hardening + Swagger basic-auth (4 blocks) preserved |
| `frontends/landing/dist/index.html` | 3× `href="/login"`, 1× `href="/app/"`, `og:url` → `/presentation/`, 0× `t.me/`, 0× bare `href="https://ruttrack.site/"` | ✓ VERIFIED | All 5 edits confirmed at lines 16 (og:url), 1029 (header), 1107 (hero), 1306 (bottom Telegram), 1310 (PWA). Footer `https://ruttrack.site/admin/` at line 1330 preserved. Three `ph-telegram-logo` icons preserved (Phase 57 handles copy/icons) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `nginx/conf.d/default.conf location = /` | `/login` | `return 301` | ✓ WIRED | Pattern `return 301 /login` present exactly once at line 134 inside `location = /` block at line 133. Exact-match modifier prevents path-confusion attacks (T-49-01 mitigated) |
| `nginx/conf.d/default.conf location /presentation/` | `rct-landing-nginx:80` | `proxy_pass` | ✓ WIRED | Pattern `proxy_pass http://rct-landing-nginx:80/` present at line 67; trailing slash enables prefix stripping |
| `nginx/conf.d/default.conf location /app/` | `rct-pwa-nginx:80` | `proxy_pass` | ✓ WIRED | Pattern `proxy_pass http://rct-pwa-nginx:80/` present at line 73; trailing slash enables prefix stripping |
| Landing header button | `/login` | anchor href | ✓ WIRED | Line 1029: `<a href="/login" class="btn btn--primary btn--sm" rel="noopener">` |
| Landing hero CTA button | `/login` | anchor href | ✓ WIRED | Line 1107: `<a href="/login" class="btn btn--primary" rel="noopener">` |
| Landing bottom CTA Telegram button | `/login` | anchor href | ✓ WIRED | Line 1306: `<a href="/login" class="btn btn--primary" rel="noopener">` |
| Landing "Мобильная версия (PWA)" button | `/app/` | anchor href | ✓ WIRED | Line 1310: `<a href="/app/" class="btn btn--secondary" rel="noopener">` — consistent with Plan 01's new `/app/` location block |
| Landing `og:url` meta | `/presentation/` | meta tag | ✓ WIRED | Line 16: `<meta property="og:url" content="https://ruttrack.site/presentation/" />` |

**Note:** `gsd-tools verify key-links` returned "Source file not found" for plan 01 and a frontmatter-parse warning for plan 02. This was a tool path resolution issue unrelated to plan correctness. All links were verified manually via `Grep` with explicit line-number evidence above.

### Data-Flow Trace (Level 4)

Not applicable — phase produces infrastructure config and static HTML. No dynamic data sources to trace.

### Behavioral Spot-Checks

Skipped — no runnable entry points. Phase produces nginx configuration and static HTML files; both must be deployed behind a live TLS-terminating nginx container to exercise behavior. The Docker-based `nginx -t` syntax test and `docker compose build` smoke test were deferred per the plan's explicit fallback clause (Docker daemon unavailable in executor worktree environment). Both are routed to human verification above.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| INFRA-v9-01 | 49-01 | Root URL redirects to `/login` instead of serving PWA | ✓ SATISFIED (code) / ? NEEDS HUMAN (runtime) | `location = / { return 301 /login; }` present in `nginx/conf.d/default.conf:133-135`; live 301 emission requires deploy |
| INFRA-v9-02 | 49-01 | `/presentation/` serves landing (moved from `/landing/`) | ✓ SATISFIED (code) / ? NEEDS HUMAN (runtime) | `location /presentation/` proxies to `rct-landing-nginx`; old `location /landing/` removed (grep = 0); live proxy hop requires deploy |
| INFRA-v9-03 | 49-01 | `/app/` serves React PWA (moved from `/`) | ✓ SATISFIED (code) / ? NEEDS HUMAN (runtime) | `location /app/` proxies to `rct-pwa-nginx`; live PWA SPA fallback requires deploy |
| INFRA-v9-05 | 49-02 | All internal landing links no longer contain `https://t.me/` | ✓ SATISFIED | `grep -c 't\.me/' frontends/landing/dist/index.html` = 0; 3 replacements installed as `href="/login"` at lines 1029, 1107, 1306 |
| INFRA-v9-06 | 49-02 | CI/CD pipeline continues passing with no workflow modifications | ✓ SATISFIED (offline) / ? NEEDS HUMAN (live CI run) | `git diff f6a7c84 HEAD -- .github/workflows/` empty; actual GitHub Actions run status deferred to next push |
| INFRA-v9-07 | 49-01 | All 4 frontend Docker images rebuild successfully | ✓ SATISFIED (offline — Dockerfiles unchanged) / ? NEEDS HUMAN (actual rebuild) | All 4 Dockerfiles and per-container nginx.conf files git-clean; actual `docker compose build` deferred per plan's fallback clause |
| LAND-v9-01 | 49-01 + 49-02 | Landing accessible at `/presentation/` only, NOT at root (old `/landing/` deprecated/removed) | ✓ SATISFIED (code) / ? NEEDS HUMAN (runtime) | Old `location /landing/` removed from nginx config; new `location /presentation/` installed; `og:url` updated from `/landing/` to `/presentation/` in landing HTML |
| LAND-v9-03 | 49-02 | All "Войти"/"Login" buttons link to `/login` (never to `https://t.me/`) | ✓ SATISFIED | All 3 Telegram buttons rewired to `/login`; no "Войти" text on the landing (buttons read "Открыть" and "Открыть в Telegram" but functionally satisfy the requirement — Phase 57 handles copy rewrites per LAND-v9-05) |

**Orphaned requirements:** None. All 8 requirement IDs from PLAN frontmatters are accounted for and map cleanly to REQUIREMENTS.md + ROADMAP.md phase 49 definition. REQUIREMENTS.md traceability table lists exactly the same 8 IDs for phase 49 (INFRA-v9-01, 02, 03, 05, 06, 07 + LAND-v9-01, 03) — no additional IDs expected.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | — | — | No anti-patterns detected |

Comprehensive scan of modified files:
- `nginx/conf.d/default.conf`: no TODO/FIXME/PLACEHOLDER comments, no empty return blocks, no hardcoded stubs. The only `return 301 /login` is the intentional redirect.
- `frontends/landing/dist/index.html`: no TODO/FIXME/PLACEHOLDER, no `t.me/` references, button copy preserved as documented (Phase 57 handles copy rewrites per LAND-v9-05).
- No collateral modifications: `.github/workflows/`, `nginx/nginx.conf`, `nginx/conf.d/http-only.conf`, all 4 `frontends/*/Dockerfile`, all 4 `frontends/*/nginx.conf` — all git-clean per `git diff f6a7c84 HEAD` showing only `frontends/landing/dist/index.html` (+5/-5 lines), `nginx/conf.d/default.conf` (+13/-6 lines), and the 4 PLAN/SUMMARY docs.

**Note on `/login` returning 404 until Phase 50:** The plan explicitly documents this as the acceptable phase boundary. `/login` itself is not a Phase 49 deliverable — Phase 49 only installs the redirect; Phase 50 (INFRA-v9-04 + AUTH-v9-01) migrates web-panel `baseHref` to `/` and resolves `/login`. This is NOT a stub, NOT an anti-pattern, and NOT a gap — it is a documented phase boundary.

### Deferred Items

Items not yet met but explicitly addressed in later milestone phases.

| # | Item | Addressed In | Evidence |
|---|------|--------------|----------|
| 1 | `/login` actually serves the Angular web-panel login form (HTTP/2 200 with form, not 404) | Phase 50 | Phase 50 goal: "Angular web-panel serves all roles from a single app with `baseHref: /`, exposing `/login` as the universal entry point with role-based post-login routing"; Success Criterion 1: "An ADMIN logging in at `/login` is routed to `/admin/dashboard`..."; Requirements INFRA-v9-04 + AUTH-v9-01 through AUTH-v9-07 |
| 2 | Landing describes all 4 roles (ADMIN, TEACHER, STUDENT, HEADMAN); button text rewritten to reflect unified login (currently still reads "Открыть в Telegram" even though href is `/login`) | Phase 57 | Phase 57 goal: "...landing...properly describes all 4 roles including HEADMAN"; Success Criterion 2: "The landing describes all four roles — ADMIN, TEACHER, STUDENT, and HEADMAN — each with their key capabilities listed"; Requirement LAND-v9-05. Phase 49 plan 02 explicitly notes: "The copy text ('Открыть в Telegram', 'Открыть') can stay — users understand 'open our system' semantically, and Phase 57 will refine the copy as part of LAND-v9-05" |

### Human Verification Required

See YAML frontmatter above. 8 items routed to human UAT:

1. **Root 301 redirect** — curl/browser test against deployed `https://ruttrack.site/`
2. **Presentation URL serves landing** — curl/browser test against deployed `https://ruttrack.site/presentation/`
3. **App URL serves PWA** — curl/browser test against deployed `https://ruttrack.site/app/`
4. **nginx -t syntax validation** — run Docker nginx -t on a host with Docker daemon available
5. **Docker image rebuild smoke test** — run `docker compose build` for the 4 frontend services
6. **GitHub Actions deploy.yml success** — verify the landing image rebuild on next push to main
7. **Landing Telegram button click behavior** — manual click-through on deployed landing
8. **Landing PWA button behavior** — verify it lands on `/app/` not ping-ponging to `/login`

### Gaps Summary

**No gaps.** All 5 observable truths are verified at the code level. All 8 requirement IDs from the PLAN frontmatters are satisfied at the code level and cross-referenced against REQUIREMENTS.md. No anti-patterns, no stubs, no collateral damage.

The phase is **routed to human_needed** (not `passed`) because:
1. The executor environment lacked a Docker daemon, so both the authoritative nginx -t syntax validation and the full Docker image rebuild smoke test were deferred per the plan's explicit fallback clause. The plan's Task 2 acceptance criteria explicitly defined a "downgrade mode" (file-existence + git-clean checks) for this scenario — downgrade mode was exercised cleanly.
2. The observable success criteria (routes serving expected content) fundamentally require a running nginx container terminating TLS against real upstream containers. Offline config verification is a necessary but not sufficient proxy for runtime behavior.
3. CI status (Success Criterion 5, part b) can only be observed after the next push to `main` triggers `.github/workflows/ci.yml` and `.github/workflows/deploy.yml`.

**Offline evidence is strong:** 13/13 grep assertions on `nginx/conf.d/default.conf` pass exactly, 16/16 grep assertions on `frontends/landing/dist/index.html` pass exactly, brace balance matches expected structure, all directives follow syntax patterns identical to known-good existing blocks (`/admin/`, `/mini-app/`), zero collateral modifications, zero anti-patterns, `location = /` exact-match modifier correctly mitigates T-49-01 path-confusion threat, `rel="noopener"` preserved on all rewritten anchors per plan.

**Recommendation:** This phase is cleared for merge and deploy. The human verification items above should be run in order during the Phase 49 UAT window (ideally together with Phase 50's deploy so that `/login` resolves to a real form rather than returning 404).

---

*Verified: 2026-04-09 00:30:00 UTC*
*Verifier: Claude (gsd-verifier)*
