---
phase: 42-multi-stage-dockerfiles
plan: "03"
subsystem: frontends
tags: [docker, nginx, vite, angular, react, multi-stage-build]
dependency_graph:
  requires: []
  provides: [DOCK-04]
  affects: [frontends/pwa, frontends/mini-app, frontends/web-panel, frontends/landing]
tech_stack:
  added: [node:22-alpine, nginx:1.27-alpine]
  patterns: [multi-stage-docker-build, nginx-static-serving, vite-production-build, angular-browser-output]
key_files:
  created:
    - frontends/pwa/Dockerfile
    - frontends/mini-app/Dockerfile
    - frontends/web-panel/Dockerfile
    - frontends/landing/Dockerfile
  modified: []
key_decisions:
  - "web-panel COPY from dist/browser/ not dist/ — Angular @angular/build:application outputs browser bundle to browser/ subdirectory"
  - "landing uses single-stage nginx with no Node.js build step — pure static HTML in dist/"
  - "All 3 build-step frontends use npm ci for reproducible installs from package-lock.json"
metrics:
  duration_minutes: 8
  completed_date: "2026-04-07"
  tasks_completed: 2
  tasks_total: 2
  files_created: 4
  files_modified: 0
requirements: [DOCK-04]
---

# Phase 42 Plan 03: Frontend Dockerfiles Summary

**One-liner:** 4 frontend Dockerfiles — Vite multi-stage (pwa, mini-app), Angular multi-stage with dist/browser/ output (web-panel), and single-stage nginx (landing).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create Dockerfiles for pwa and mini-app (Vite/React) | 9a69136 | frontends/pwa/Dockerfile, frontends/mini-app/Dockerfile |
| 2 | Create Dockerfiles for web-panel (Angular) and landing (static HTML) | 8ddf3d3 | frontends/web-panel/Dockerfile, frontends/landing/Dockerfile |

## What Was Built

Three distinct Docker patterns implemented across 4 frontend services:

**Pattern C — Vite/React (pwa, mini-app):**
- `node:22-alpine` builder stage: `npm ci` then `npm run build` (tsc + vite build)
- `nginx:1.27-alpine` runtime: serves `dist/` with project nginx.conf for SPA routing

**Pattern C Variant — Angular (web-panel):**
- Same node:22-alpine builder pattern
- Critical: `COPY --from=builder /app/dist/browser` — Angular 19 with `@angular/build:application` outputs browser bundle to `dist/browser/`, not `dist/` directly

**Single-stage — Landing (static HTML):**
- No build stage at all — landing directory has no package.json
- Direct `nginx:1.27-alpine` image, `COPY dist /usr/share/nginx/html`

## Verification

All 4 `docker build` commands succeeded with exit code 0:

```
docker build -t rct-pwa-test frontends/pwa/        → PASS
docker build -t rct-miniapp-test frontends/mini-app/ → PASS
docker build -t rct-webpanel-test frontends/web-panel/ → PASS
docker build -t rct-landing-test frontends/landing/  → PASS
```

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. All Dockerfiles copy real build artifacts. No placeholder content.

## Threat Surface Scan

T-42-02 mitigated: All Node.js build stages (node_modules, source) stay in builder. Runtime images contain only nginx + compiled static HTML/JS/CSS. No source code in runtime layers.

## Self-Check: PASSED

Files created:
- frontends/pwa/Dockerfile — FOUND
- frontends/mini-app/Dockerfile — FOUND
- frontends/web-panel/Dockerfile — FOUND
- frontends/landing/Dockerfile — FOUND

Commits:
- 9a69136 — FOUND (pwa + mini-app Dockerfiles)
- 8ddf3d3 — FOUND (web-panel + landing Dockerfiles)
