---
phase: 49-nginx-routing-landing-dead-link-fix
plan: 01
subsystem: infrastructure-nginx-reverse-proxy
tags: [nginx, reverse-proxy, routing, ssl, infra-v9]
requires:
  - rct-landing-nginx container (existing, unchanged)
  - rct-pwa-nginx container (existing, unchanged)
  - rct-web-panel-nginx container (existing, unchanged)
  - rct-mini-app-nginx container (existing, unchanged)
  - rct-api-gateway container (existing, unchanged)
provides:
  - "GET / → 301 /login (unified entry point — landing zone for Phase 50 /login resolver)"
  - "GET /presentation/ → landing HTML via rct-landing-nginx (LAND-v9-01)"
  - "GET /app/ → React PWA via rct-pwa-nginx (PWA off root)"
  - "Removal of deprecated /landing/ block"
affects:
  - Production routing on https://ruttrack.site
  - Phase 50 baseHref migration (depends on /login redirect being installed)
tech-stack:
  added: []
  patterns:
    - "nginx exact-match location modifier (location = /) — prevents path-confusion redirects"
    - "trailing-slash proxy_pass prefix-stripping (existing pattern, applied to new /presentation/ + /app/ blocks)"
key-files:
  created: []
  modified:
    - nginx/conf.d/default.conf
decisions:
  - "Use 'location = /' (exact-match) instead of 'location /' (prefix) to avoid catching /favicon.ico, /robots.txt, etc. — these now 404 cleanly instead of falling back to PWA"
  - "Insert /presentation/ and /app/ blocks above /admin/ for logical grouping (static frontends together)"
  - "Defer Docker-based 'nginx -t' syntax test to Phase 49 UAT — Docker daemon unavailable in worktree environment, manual brace-balance + grep verification used as substitute"
  - "Defer Docker-based frontend rebuild smoke test to Phase 49 UAT for the same reason — Task 2 downgraded to file-existence + git-clean check per plan's downgrade-mode clause"
metrics:
  duration_seconds: 230
  duration_human: "~4 minutes"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 1
  commits: 1
  completed_date: "2026-04-08"
---

# Phase 49 Plan 01: Nginx Reverse Proxy Routing Rewrite — Summary

**One-liner:** Rewrote nginx top-level reverse proxy so production root `/` redirects to `/login` (unified entry point), PWA moves to `/app/`, landing moves to `/presentation/`, and the deprecated `/landing/` block is removed — fixes #1 production UX bug where TEACHER/ADMIN users were dumped into the student PWA on first visit.

## What Changed

### nginx/conf.d/default.conf — 4 surgical edits inside the HTTPS server block

| Edit | Type     | Block                                                         | Lines (after) | Purpose                                                                          |
| ---- | -------- | ------------------------------------------------------------- | ------------- | -------------------------------------------------------------------------------- |
| 1    | DELETE   | `location /landing/`                                          | (removed)     | LAND-v9-01 — superseded by `/presentation/`                                      |
| 2    | INSERT   | `location /presentation/` → `rct-landing-nginx:80/`           | 65–69         | INFRA-v9-02 — landing accessible only by explicit link                           |
| 3    | INSERT   | `location /app/` → `rct-pwa-nginx:80/`                        | 71–75         | INFRA-v9-03 — React PWA off root                                                 |
| 4    | REPLACE  | `location /` (catch-all to PWA) → `location = /` → 301 /login | 130–135       | INFRA-v9-01 — unified entry point; Phase 50 makes `/login` resolve via Angular   |

**Diff size:** 1 file, +13 / -6 lines

**Untouched (verified clean):**
- HTTP→HTTPS redirect block (`server { listen 80 }`, lines 5–17) — unchanged
- All SSL config (`ssl_certificate`, `ssl_protocols TLSv1.2 TLSv1.3`, ciphers, dhparam)
- All security headers (HSTS, X-Frame-Options, CSP, Permissions-Policy, Referrer-Policy)
- `/api/ws/` WebSocket proxy (STOMP/SockJS for notification-web)
- `/api/` API Gateway proxy
- `/admin/`, `/mini-app/` reverse proxies (Phase 50 will modify `/admin/` separately)
- All 4 Swagger basic-auth blocks (`/swagger-ui.html`, `/swagger-ui/`, `/v3/api-docs`, `/openapi/`)

## Verification Results

### Offline grep checks (all passed)

| Check                                                                                | Expected | Actual | Status |
| ------------------------------------------------------------------------------------ | -------- | ------ | ------ |
| `grep -c "return 301 /login" nginx/conf.d/default.conf`                              | 1        | 1      | OK     |
| `grep -c "location = /" nginx/conf.d/default.conf`                                   | 1        | 1      | OK     |
| `grep -c "location /presentation/" nginx/conf.d/default.conf`                        | 1        | 1      | OK     |
| `grep -c "location /app/" nginx/conf.d/default.conf`                                 | 1        | 1      | OK     |
| `grep -c "location /landing/" nginx/conf.d/default.conf`                             | 0        | 0      | OK     |
| `grep -c "proxy_pass http://rct-landing-nginx:80/" nginx/conf.d/default.conf`        | 1        | 1      | OK     |
| `grep -c "proxy_pass http://rct-pwa-nginx:80" nginx/conf.d/default.conf`             | ≥1       | 1      | OK     |
| `grep -c "location /admin/" nginx/conf.d/default.conf`                               | 1        | 1      | OK     |
| `grep -c "location /mini-app/" nginx/conf.d/default.conf`                            | 1        | 1      | OK     |
| `grep -c "location /api/ws/" nginx/conf.d/default.conf`                              | 1        | 1      | OK     |
| `grep -c "Strict-Transport-Security" nginx/conf.d/default.conf`                      | 1        | 1      | OK     |
| `grep -c "ssl_protocols TLSv1.2 TLSv1.3" nginx/conf.d/default.conf`                  | 1        | 1      | OK     |
| `grep -c 'auth_basic "Dev Access"' nginx/conf.d/default.conf`                        | 4        | 4      | OK     |

### Brace balance (manual syntax sanity check)

```
Open braces: 15
Close braces: 15
```

Balanced. 2 server blocks × `{`/`}`, 13 location blocks × `{`/`}` = 15 each. Matches expected structure of HTTP-redirect server + HTTPS server with 13 locations (was 12 locations + root, now 13 locations + exact-match root = 15 total).

### Docker `nginx -t` syntax test — DEFERRED to Phase 49 UAT

**Reason:** Docker Desktop daemon unavailable in worktree execution environment.

```
$ docker run --rm -v "$(pwd)/nginx/conf.d:/etc/nginx/conf.d:ro" -v "$(pwd)/nginx/nginx.conf:/etc/nginx/nginx.conf:ro" nginx:1.27-alpine nginx -t
docker: error during connect: Head "http://%2F%2F.%2Fpipe%2FdockerDesktopLinuxEngine/_ping": open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
EXIT=127
```

**Compensating evidence:**
1. All edits follow IDENTICAL syntax patterns to the existing `/admin/` and `/mini-app/` blocks (which are known-good in production today).
2. Brace count balanced (15 open, 15 close).
3. All grep assertions on directives pass.
4. The `location = /` exact-match modifier is standard nginx since 0.7.12 (2008) — not a novel directive.
5. Phase 49 UAT will run the full Docker-based test on the production VM during deploy, which is the authoritative environment anyway.

**Action item for UAT:** Run the offline Docker test on the deploy host before merging, OR rely on the live `nginx -t` that runs implicitly when nginx container restarts after `docker compose up`.

### Frontend image rebuild smoke test — DEFERRED to Phase 49 UAT (downgrade mode)

**Reason:** Same Docker daemon unavailability. Plan's Task 2 explicitly defines a "downgrade mode" for this scenario.

**Downgrade-mode evidence (all passed):**
- `frontends/pwa/Dockerfile` — exists, unchanged (`git status --porcelain`: empty)
- `frontends/mini-app/Dockerfile` — exists, unchanged
- `frontends/web-panel/Dockerfile` — exists, unchanged
- `frontends/landing/Dockerfile` — exists, unchanged
- `frontends/pwa/nginx.conf` — exists, unchanged
- `frontends/mini-app/nginx.conf` — exists, unchanged
- `frontends/web-panel/nginx.conf` — exists, unchanged
- `frontends/landing/nginx.conf` — exists, unchanged
- `.github/workflows/*.yml` — unchanged (INFRA-v9-06 satisfied)

Since Task 1 modifies ONLY the top-level reverse-proxy config and does NOT touch any per-container nginx.conf, Dockerfile, or build artifact, the frontend images have zero reason to fail rebuild. The test will be re-run as part of the v9.0 deploy pipeline.

### Acceptance criteria for online (UAT) verification

These curl checks must run on https://ruttrack.site after deploy:

1. `curl -sI https://ruttrack.site/ | grep -E "^(HTTP|Location)"` → `HTTP/2 301` + `Location: /login`
2. `curl -sI https://ruttrack.site/presentation/ | head -1` → `HTTP/2 200`
3. `curl -s https://ruttrack.site/presentation/ | grep "<title>"` → landing title tag
4. `curl -sI https://ruttrack.site/app/ | head -1` → `HTTP/2 200`
5. `curl -s https://ruttrack.site/app/ | grep "<title>RutTrack"` → PWA title
6. `curl -sI https://ruttrack.site/landing/ | head -1` → `HTTP/2 404` (deprecated path)
7. `curl -sI https://ruttrack.site/admin/ | head -1` → `HTTP/2 200` (unchanged)
8. `curl -sI https://ruttrack.site/api/actuator/health | head -1` → `HTTP/2 200` (unchanged)

Note: `curl -sI https://ruttrack.site/login` will return `HTTP/2 404` until Phase 50 ships — this is the documented and acceptable temporary state per the phase boundary.

## Commits

| Hash      | Type | Message                                                          |
| --------- | ---- | ---------------------------------------------------------------- |
| `80ddd99` | fix  | rewrite nginx reverse proxy routing for /login entry point       |

(Task 2 produced no file changes — smoke test only — so no Task 2 commit. This matches the plan's `<files>` declaration: "no files modified".)

## Deviations from Plan

**None — plan executed exactly as written.**

The two "downgrades" (Task 1 nginx -t test deferred, Task 2 frontend rebuild deferred) are NOT deviations — they are explicit fallback paths defined in the plan itself for the case where Docker daemon is unavailable. Both fallbacks were exercised exactly as the plan specifies.

### Environment note (not a deviation)

The worktree was initially based on commit `d6df3c2` ("frontenf fix") instead of the expected base `f6a7c84` (v9.0 milestone init). This is a known Windows worktree creation issue. Per the worktree_branch_check protocol, the branch was reset to `f6a7c84` via `git reset --hard` before any work began. The reset was a clean fast-forward (worktree had no original commits), so no work was lost.

## Threat Flags

None. The plan's `<threat_model>` enumerates all surface changes (T-49-01 through T-49-07) and all are dispositioned as either `mitigate` (with the mitigation already implemented via `location = /` exact-match modifier) or `accept` (with documented rationale). No NEW security-relevant surface was introduced beyond what the plan anticipated.

## Known Stubs

None. Both new location blocks (`/presentation/`, `/app/`) are wired to live upstream containers that already exist and serve real content. The only "deferred" element is the `/login` target of the root redirect, which Phase 50 will resolve — this is a documented critical-path dependency, not a stub.

## Authentication Gates

None encountered. Pure config edit, no auth required, no external services accessed.

## Next Step

Plan **49-02** (landing HTML dead link fix — runs in PARALLEL with this plan via the wave-1 worktree split). After both 49-01 and 49-02 complete, the Phase 49 UAT will run the live curl tests above on the deploy host. **Phase 50** then ships the `/login` resolver via web-panel Angular `baseHref` migration, completing the unified login flow.

## Self-Check: PASSED

- Modified file exists at `nginx/conf.d/default.conf` (worktree path) — VERIFIED via Read
- Commit `80ddd99` exists in worktree branch — VERIFIED via `git log --oneline`
- All 13 grep assertions return expected counts — VERIFIED above
- Brace balance matches structure (15/15) — VERIFIED
- Zero collateral damage (no other files modified) — VERIFIED via `git status --porcelain`
- SUMMARY.md created at the requested path — VERIFIED via Write tool success
