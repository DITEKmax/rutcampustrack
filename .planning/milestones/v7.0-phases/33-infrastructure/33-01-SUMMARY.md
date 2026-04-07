---
phase: 33-infrastructure
plan: "01"
subsystem: infrastructure
tags: [nginx, docker-compose, frontend, url-layout]
dependency_graph:
  requires: []
  provides: [mini-app-nginx, web-panel-nginx, landing-nginx, url-layout]
  affects: [docker-compose.yml, frontends/mini-app, frontends/web-panel, frontends/landing]
tech_stack:
  added: [nginx:1.27-alpine (mini-app, web-panel, landing containers)]
  patterns: [SPA nginx fallback, static nginx 404, placeholder dist pages]
key_files:
  created:
    - docs/url-layout.md
    - frontends/mini-app/nginx.conf
    - frontends/mini-app/dist/index.html
    - frontends/web-panel/nginx.conf
    - frontends/web-panel/dist/index.html
    - frontends/landing/nginx.conf
    - frontends/landing/dist/index.html
  modified:
    - docker-compose.yml
    - .gitignore
decisions:
  - "Mini App nginx: no Service Worker caching rule (no sw.js), SPA fallback to index.html"
  - "Web Panel nginx: added eot/ttf font assets for Angular"
  - "Landing nginx: =404 instead of SPA fallback — static site, no client-side routing"
  - ".gitignore: added dist/ exceptions for mini-app, web-panel, landing (mirrors pwa exception)"
metrics:
  duration: ~8 minutes
  completed: "2026-04-06"
  tasks_completed: 2
  files_created: 7
  files_modified: 2
---

# Phase 33 Plan 01: Frontend Infrastructure Scaffolding Summary

Three nginx containers scaffolded with URL layout doc, placeholder pages, and docker-compose service definitions for Mini App (port 3000), Web Panel (port 4200), and Landing (port 8081).

## What Was Built

- **docs/url-layout.md**: Port assignment reference for all 4 frontends (PWA 80, Mini App 3000, Web Panel 4200, Landing 8081), container names, Gateway CORS origins
- **3 nginx configs**: Mini App (React SPA, try_files fallback), Web Panel (Angular SPA, eot/ttf fonts), Landing (static HTML, =404)
- **3 placeholder index.html files**: Each in their respective `dist/` directory, ready for real builds in later phases
- **docker-compose.yml**: Three new service blocks — `mini-app-nginx`, `web-panel-nginx`, `landing-nginx` — all using nginx:1.27-alpine with read-only volume mounts

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | URL layout doc and placeholder pages | 946aa3e | docs/url-layout.md, 3x dist/index.html, .gitignore |
| 2 | nginx configs and docker-compose services | 30b83b2 | 3x nginx.conf, docker-compose.yml |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing] Added .gitignore exceptions for new dist/ directories**
- **Found during:** Task 1 — `git add` rejected dist/ files as ignored
- **Issue:** `.gitignore` had `dist/` globally ignored with only `!frontends/pwa/dist/` exception; the three new frontend dist/ directories were also ignored
- **Fix:** Added `!frontends/mini-app/dist/`, `!frontends/web-panel/dist/`, `!frontends/landing/dist/` to .gitignore, matching the existing PWA exception pattern
- **Files modified:** `.gitignore`
- **Commit:** 946aa3e

## Known Stubs

| File | Description |
|------|-------------|
| frontends/mini-app/dist/index.html | Placeholder — Phase 36 will replace with real Mini App build |
| frontends/web-panel/dist/index.html | Placeholder — Phase 38 will replace with real Web Panel build |
| frontends/landing/dist/index.html | Placeholder — Phase 35 will replace with real Landing build |

These stubs are intentional; they allow the nginx containers to start and serve content immediately. Each will be replaced when the respective frontend phase ships.

## Threat Flags

None. All nginx containers serve static read-only files; no user input processing, no dynamic content.

## Self-Check: PASSED

- [x] docs/url-layout.md exists and contains "Mini App", "3000", "5174", "4200", "8081", "rct-mini-app-nginx", "rct-web-panel-nginx", "rct-landing-nginx"
- [x] frontends/mini-app/nginx.conf exists with try_files SPA fallback
- [x] frontends/web-panel/nginx.conf exists with try_files SPA fallback
- [x] frontends/landing/nginx.conf exists with try_files =404
- [x] docker-compose.yml contains mini-app-nginx (3000:80), web-panel-nginx (4200:80), landing-nginx (8081:80)
- [x] docker-compose.yml has 4 total nginx:1.27-alpine entries
- [x] Commit 946aa3e exists
- [x] Commit 30b83b2 exists
