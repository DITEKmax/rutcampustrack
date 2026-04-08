---
status: partial
phase: 49-nginx-routing-landing-dead-link-fix
source: [49-VERIFICATION.md]
started: 2026-04-09T00:35:00Z
updated: 2026-04-09T00:35:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Root redirects to /login on live host
expected: `curl -sI https://ruttrack.site/` returns `HTTP/2 301` with `Location: /login` — React PWA is NOT served at root
result: [pending]

### 2. /presentation/ serves landing HTML
expected: `curl -sI https://ruttrack.site/presentation/` returns `HTTP/2 200`; HTML body contains landing `<title>RutCampusTrack</title>`
result: [pending]

### 3. /app/ serves React PWA
expected: `curl -sI https://ruttrack.site/app/` returns `HTTP/2 200`; HTML body contains PWA `<title>RutTrack</title>`; static assets resolve with trailing-slash prefix stripping
result: [pending]

### 4. nginx -t syntax test passes on Docker-enabled host
expected: `docker run --rm -v $(pwd)/nginx/conf.d:/etc/nginx/conf.d:ro -v $(pwd)/nginx/nginx.conf:/etc/nginx/nginx.conf:ro nginx:1.27-alpine nginx -t` prints `nginx: configuration file /etc/nginx/nginx.conf test is successful` (SSL cert errors tolerated)
result: [pending]

### 5. All 4 frontend Docker images rebuild
expected: `docker compose -f docker-compose.prod.yml build pwa-nginx mini-app-nginx web-panel-nginx landing-nginx` exits 0 with no ERROR lines
result: [pending]

### 6. GitHub Actions deploy.yml run succeeds
expected: `gh run list --workflow=deploy.yml --limit 1` shows `status: success` after push to main; landing image pushed to GHCR
result: [pending]

### 7. Landing Telegram buttons navigate to /login (not t.me/)
expected: Clicking each of the three "Открыть в Telegram"/"Открыть" buttons on the rendered landing navigates to `/login` (404 until Phase 50 ships is acceptable); NONE navigate to `https://t.me/`
result: [pending]

### 8. Landing "Мобильная версия (PWA)" button navigates to /app/
expected: Clicking the PWA CTA button navigates to `/app/` and serves the React PWA — no ping-pong to /login
result: [pending]

## Summary

total: 8
passed: 0
issues: 0
pending: 8
skipped: 0
blocked: 0

## Gaps
