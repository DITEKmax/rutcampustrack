---
phase: 28-api-gateway-cors-nginx
plan: 02
subsystem: infra
tags: [nginx, docker-compose, pwa, static-serving, cache-headers]

# Dependency graph
requires:
  - phase: none
    provides: standalone infrastructure setup
provides:
  - nginx container serving PWA static files on port 80
  - nginx.conf with PWA cache rules (no-cache sw.js/index.html, aggressive cache hashed assets)
  - SPA fallback via try_files
  - Placeholder dist/ files for verification
affects: [phase-29-pwa-build, frontends-pwa]

# Tech tracking
tech-stack:
  added: [nginx:1.27-alpine]
  patterns: [bind-mount nginx config, read-only volume mounts, SPA fallback routing]

key-files:
  created:
    - frontends/pwa/nginx.conf
    - frontends/pwa/dist/index.html
    - frontends/pwa/dist/sw.js
  modified:
    - docker-compose.yml
    - .gitignore

key-decisions:
  - "Added .gitignore exception for frontends/pwa/dist/ — placeholder files must be tracked"

patterns-established:
  - "nginx PWA serving: no-cache for sw.js and index.html, immutable cache for hashed assets"
  - "SPA fallback: try_files $uri $uri/ /index.html"

requirements-completed: [INFRA-03]

# Metrics
duration: 2min
completed: 2026-04-06
---

# Phase 28 Plan 02: PWA nginx Container Summary

**nginx:1.27-alpine container in docker-compose serving placeholder PWA files with no-cache headers for sw.js/index.html and aggressive caching for hashed assets**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-05T21:08:33Z
- **Completed:** 2026-04-05T21:10:24Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- nginx.conf with PWA-optimized cache rules: no-cache for service worker and entry point, 1-year immutable cache for hashed static assets
- SPA fallback routing via try_files to index.html
- pwa-nginx service in docker-compose with nginx:1.27-alpine on port 80, read-only bind mounts
- docker compose config validates successfully

## Task Commits

Each task was committed atomically:

1. **Task 1: Create nginx config + placeholder dist files** - `e404f3d` (chore)
2. **Task 2: Add pwa-nginx service to docker-compose.yml** - `321a07b` (chore)

## Files Created/Modified
- `frontends/pwa/nginx.conf` - nginx config with PWA cache rules and SPA fallback
- `frontends/pwa/dist/index.html` - Placeholder PWA entry point with RutTrack title
- `frontends/pwa/dist/sw.js` - Placeholder service worker for cache header testing
- `docker-compose.yml` - Added pwa-nginx service (nginx:1.27-alpine, port 80, bind mounts)
- `.gitignore` - Added exception for frontends/pwa/dist/ (placeholder files tracked)

## Decisions Made
- Added .gitignore exception for `frontends/pwa/dist/` because the global `dist/` ignore pattern blocked tracking placeholder files needed for container verification

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] .gitignore dist/ pattern blocked placeholder files**
- **Found during:** Task 1 (creating placeholder dist files)
- **Issue:** Global `dist/` pattern in .gitignore prevented git add of frontends/pwa/dist/ files
- **Fix:** Added `!frontends/pwa/dist/` exception line after `dist/` in .gitignore
- **Files modified:** .gitignore
- **Verification:** git add succeeded, files tracked
- **Committed in:** e404f3d (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary for placeholder files to be committed. No scope creep.

## Issues Encountered
None beyond the .gitignore deviation above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- nginx container ready to serve PWA build output from Phase 29
- Phase 29 React build pipeline will replace placeholder files in frontends/pwa/dist/
- vite-plugin-pwa will generate real sw.js to replace placeholder

---
*Phase: 28-api-gateway-cors-nginx*
*Completed: 2026-04-06*
