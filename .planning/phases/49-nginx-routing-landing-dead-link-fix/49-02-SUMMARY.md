---
phase: 49-nginx-routing-landing-dead-link-fix
plan: 02
subsystem: frontend/landing
tags: [landing, nginx-routing, og-meta, link-fix, v9.0]
requirements:
  - INFRA-v9-05
  - INFRA-v9-06
  - LAND-v9-01
  - LAND-v9-03
dependency_graph:
  requires:
    - Phase 49 Plan 01 (nginx routing scaffold — establishes /login, /app/, /presentation/ locations)
  provides:
    - Landing HTML wired to unified /login entry point
    - Landing OpenGraph canonical URL pointing at /presentation/
    - Landing PWA button pointing at /app/ (avoids 301 ping-pong from /)
  affects:
    - frontends/landing/dist/index.html (sole modified file)
    - landing Docker image (rebuilds automatically on next deploy via unchanged deploy.yml)
tech_stack:
  added: []
  patterns:
    - Pure static-HTML href edits (no build pipeline)
    - Relative same-origin URLs for in-site navigation (/login, /app/)
    - Absolute canonical URL only in og:url meta (per OpenGraph spec)
key_files:
  created: []
  modified:
    - frontends/landing/dist/index.html
decisions:
  - Use relative /login and /app/ for in-page anchors; reserve absolute https://ruttrack.site/... only for og:url canonical
  - Preserve Telegram icons and "Открыть в Telegram" copy (Phase 57 will handle copy/icon rewrites)
  - Preserve footer https://ruttrack.site/admin/ link (still valid — Plan 01 keeps location /admin/)
  - Three separate href changes performed via two commits (Task 1 = three t.me/ links; Task 2 = og:url + PWA button)
metrics:
  duration: "1m 44s"
  completed_date: "2026-04-08"
  tasks_completed: 2
  files_modified: 1
  commits: 2
  lines_changed: 5
---

# Phase 49 Plan 02: Landing Dead Link Fix Summary

Replaced three dead `https://t.me/` links and one ping-pong `https://ruttrack.site/` PWA button with the new unified `/login` and `/app/` routes, and migrated the OpenGraph `og:url` canonical from `/landing/` to `/presentation/`. Single-file in-place edits — no build pipeline, no workflow changes, only `frontends/landing/dist/index.html` modified (5 line changes total).

## Tasks Completed

| Task | Name                                                                                              | Commit  | Files                                |
| ---- | ------------------------------------------------------------------------------------------------- | ------- | ------------------------------------ |
| 1    | Replace three dead t.me/ links with /login in landing HTML                                        | d6b193e | frontends/landing/dist/index.html    |
| 2    | Fix landing og:url meta + PWA "Мобильная версия" button + verify CI workflows untouched          | 1c0b6de | frontends/landing/dist/index.html    |

## The 5 Exact Edits

### Edit A — Header "Открыть" button (line 1029)

```diff
- <a href="https://t.me/" class="btn btn--primary btn--sm" rel="noopener">
+ <a href="/login" class="btn btn--primary btn--sm" rel="noopener">
```

Icon `<i class="ph-duotone ph-telegram-logo">`, button text "Открыть", `rel="noopener"`, and CSS classes preserved.

### Edit B — Hero CTA "Открыть в Telegram" button (line 1107)

```diff
- <a href="https://t.me/" class="btn btn--primary" rel="noopener">
+ <a href="/login" class="btn btn--primary" rel="noopener">
```

### Edit C — Bottom CTA "Открыть в Telegram" button (line 1306)

```diff
- <a href="https://t.me/" class="btn btn--primary" rel="noopener">
+ <a href="/login" class="btn btn--primary" rel="noopener">
```

Edits B and C share an identical source string and were applied simultaneously via `replace_all=true` (the only two such matches in the file after Edit A).

### Edit D — OpenGraph og:url meta (line 16)

```diff
- <meta property="og:url" content="https://ruttrack.site/landing/" />
+ <meta property="og:url" content="https://ruttrack.site/presentation/" />
```

Matches Plan 01's nginx routing — the landing now lives at `/presentation/`. Social-media crawlers will pick up the new canonical URL on next visit; the deprecated `/landing/` returns 404 after Plan 01 ships.

### Edit E — Bottom CTA "Мобильная версия (PWA)" button (line 1310)

```diff
- <a href="https://ruttrack.site/" class="btn btn--secondary" rel="noopener">
+ <a href="/app/" class="btn btn--secondary" rel="noopener">
```

Plan 01 makes `https://ruttrack.site/` a 301 redirect to `/login`. Without this fix, clicking "Мобильная версия (PWA)" would land the user on the login page (the opposite of what the button advertises). The new `/app/` path points directly to the PWA at its new location (per Plan 01's `location /app/` block).

## Verification Results

All grep checks from the plan's `<verification>` block passed:

| Check                                                                                                 | Expected | Actual |
| ----------------------------------------------------------------------------------------------------- | -------- | ------ |
| `grep -c 't\.me/' frontends/landing/dist/index.html`                                                  | 0        | 0      |
| `grep -c 'href="/login"' frontends/landing/dist/index.html`                                           | 3        | 3      |
| `grep -c 'content="https://ruttrack.site/presentation/"' frontends/landing/dist/index.html`           | 1        | 1      |
| `grep -c 'content="https://ruttrack.site/landing/"' frontends/landing/dist/index.html`                | 0        | 0      |
| `grep -c 'href="/app/"' frontends/landing/dist/index.html`                                            | 1        | 1      |
| `grep -c 'href="https://ruttrack.site/"' frontends/landing/dist/index.html`                           | 0        | 0      |
| `grep -c 'Мобильная версия' frontends/landing/dist/index.html`                                        | 1        | 1      |
| `grep -c 'Открыть в Telegram' frontends/landing/dist/index.html`                                      | 2        | 2      |
| `grep -c 'ph-telegram-logo' frontends/landing/dist/index.html`                                        | 3        | 3      |
| `grep -c 'Панель администратора' frontends/landing/dist/index.html`                                   | 1        | 1      |
| `grep -c 'href="https://ruttrack.site/admin/"' frontends/landing/dist/index.html`                     | 1        | 1      |
| `wc -l frontends/landing/dist/index.html`                                                             | 1488     | 1488   |
| `git status --porcelain .github/workflows/`                                                           | empty    | empty  |
| `git status --porcelain frontends/landing/Dockerfile frontends/landing/nginx.conf`                    | empty    | empty  |
| `git status --porcelain frontends/pwa frontends/web-panel frontends/mini-app`                         | empty    | empty  |
| `git diff --name-only HEAD~2 HEAD -- frontends/`                                                      | only landing/dist/index.html | only landing/dist/index.html |

`git diff --stat` totals: **1 file changed, 5 insertions(+), 5 deletions(-)** across the two commits — exactly five in-line href replacements as planned, no line additions or deletions.

## Confirmation: INFRA-v9-06 Satisfied

`.github/workflows/ci.yml` and `.github/workflows/deploy.yml` were NOT modified. The landing Docker image rebuild on next deploy is triggered automatically by the `dist/index.html` content change — the existing `Build and push landing` step in `deploy.yml` (lines 127-134) uses `docker/build-push-action@v7` with `context: frontends/landing`, so any change inside `frontends/landing/` automatically invalidates its layer cache and triggers a fresh image build with no workflow edit required.

## Confirmation: Container Configuration Untouched

`frontends/landing/Dockerfile` and `frontends/landing/nginx.conf` are unchanged. Only `frontends/landing/dist/index.html` was modified. The landing container's nginx serves static files via `try_files $uri $uri/ =404;` — no SSI or dynamic rendering — so the only thing that changes inside the container is the served HTML payload.

## Deviations from Plan

None — the plan executed exactly as written. All five string replacements landed verbatim, all acceptance criteria for both tasks passed on first verification, no auto-fixes (Rules 1-3) were needed, and no architectural decisions (Rule 4) arose. Pure static HTML edit, zero new attack surface.

## Threat Flags

None. All edits are href attribute values pointing to local paths (`/login`, `/app/`) or the same-origin canonical (`https://ruttrack.site/presentation/` in og:url). No new endpoints, no new auth paths, no new file access patterns. The threat model in the plan (T-49-08 through T-49-12) covers all scenarios; all five threats remain dispositioned `accept` with mitigation rationale unchanged.

## Expected Behaviour After Deploy

1. **Next push to main triggers `deploy.yml`** — the `Build and push landing` step rebuilds `ghcr.io/ditekmax/rutcampustrack/landing-nginx:${{ github.sha }}` automatically because the Docker layer that copies `dist/` is now invalidated.
2. **After deploy completes** — visiting `https://ruttrack.site/presentation/` (assuming Plan 01 has shipped) serves the new HTML with five corrected hrefs.
3. **`/login` returns 404 until Phase 50 ships** — Plan 01 only installs the nginx scaffold for `/login`; Phase 50 is what makes `/login` actually serve the Angular web-panel SPA after `baseHref` migration. This transient 404 is documented in T-49-11 (accepted risk, scoped to the Phase 49→50 gap).
4. **`/app/` works immediately after Plan 01 deploys** — the PWA container is already in production from v8.0, so the new `location /app/` block routes directly to it.
5. **Footer "Панель администратора"** — still works, unchanged: `/admin/` continues to serve the Angular web-panel until Phase 50 migrates it to `/`.
6. **Social-media link previews** — Facebook/Twitter/LinkedIn crawlers will refresh og:url on next visit (typically within hours). Stale previews showing `/landing/` will 404 instead of silently serving stale content (T-49-10 disposition).

## Next Step

Run Phase 49 UAT via curl commands once Plan 01 is also complete and deployed, then `/gsd-verify-phase 49` before `/gsd-plan-phase 50` (which will make `/login` resolve via the Angular `baseHref` migration).

## Self-Check: PASSED

- File `frontends/landing/dist/index.html` exists and contains all five expected edits (verified via grep counts above).
- Commit `d6b193e` exists in `git log` (Task 1).
- Commit `1c0b6de` exists in `git log` (Task 2).
- SUMMARY file path `.planning/phases/49-nginx-routing-landing-dead-link-fix/49-02-SUMMARY.md` resolved at write time.
- PLAN.md preserved alongside SUMMARY.md at `.planning/phases/49-nginx-routing-landing-dead-link-fix/49-02-PLAN.md`.
- No collateral damage: `.github/workflows/`, landing `Dockerfile`/`nginx.conf`, and other frontend directories are clean.
