---
phase: 56
plan: "06"
subsystem: pwa-service-worker
tags: [workbox, runtime-caching, stale-while-revalidate, pwa, headman]
dependency_graph:
  requires: [56-01]
  provides: [PWA-HEAD-04]
  affects: [frontends/pwa/src/sw.ts]
tech_stack:
  added: [workbox-routing, workbox-strategies, workbox-expiration, workbox-cacheable-response]
  patterns: [StaleWhileRevalidate, registerRoute, CacheableResponsePlugin, ExpirationPlugin]
key_files:
  created:
    - frontends/pwa/src/sw-runtime-cache.ts
    - frontends/pwa/src/__tests__/sw-runtime-cache.test.ts
  modified:
    - frontends/pwa/src/sw.ts
    - frontends/pwa/src/shared/components/__tests__/BottomNav.test.tsx
decisions:
  - "Extracted isHeadmanApiRequest into sw-runtime-cache.ts (pure module) to allow unit testing without Service Worker context"
  - "Single named cache headman-api-cache-v1 covers all 8 headman GET endpoint patterns"
  - "CacheableResponsePlugin statuses:[200] ensures 404 from excuses/late-checkins never pollutes cache"
metrics:
  duration: "~15 minutes"
  completed: "2026-04-13"
  tasks_completed: 2
  tasks_total: 2
  files_created: 2
  files_modified: 2
---

# Phase 56 Plan 06: Workbox Runtime Caching for Headman GET Endpoints Summary

**One-liner:** Workbox StaleWhileRevalidate runtime cache (`headman-api-cache-v1`, 24h TTL, 100 entries, GET+200-only) appended to sw.ts via extracted `isHeadmanApiRequest` predicate, 12 unit tests green.

## What Was Built

### sw-runtime-cache.ts (new — pure module)

Exports `isHeadmanApiRequest(url: URL): boolean` — the URL matcher predicate that decides which requests enter the Workbox runtime cache. Kept separate from `sw.ts` so vitest can import it without needing a Service Worker execution context.

### sw.ts (extended — additive only)

Four Workbox imports added at top:
```
workbox-routing    → registerRoute
workbox-strategies → StaleWhileRevalidate
workbox-expiration → ExpirationPlugin
workbox-cacheable-response → CacheableResponsePlugin
```

`registerRoute` call appended at bottom (after existing `precacheAndRoute` + push handlers):
- Matcher: `request.method === 'GET' && isHeadmanApiRequest(url)`
- Strategy: `StaleWhileRevalidate`
- Cache name: `headman-api-cache-v1`
- Plugins: `CacheableResponsePlugin({ statuses: [200] })` + `ExpirationPlugin({ maxAgeSeconds: 86400, maxEntries: 100 })`

### Cache Configuration (operator reference)

| Setting | Value | Rationale |
|---------|-------|-----------|
| Cache name | `headman-api-cache-v1` | Single bucket for all headman endpoints; version suffix for future cache bust |
| TTL | 86400 s (24 hours) | Balances freshness with offline resilience; matches CONTEXT.md D-18 |
| Max entries | 100 | Prevents unbounded storage growth (T-56-23 mitigation) |
| Method filter | GET only | PUT/POST/PATCH/DELETE mutations bypass cache entirely (T-56-25 mitigation) |
| Status filter | 200 only | 404 responses from excuses/late-checkins never cached (T-56-24 mitigation) |

To inspect in browser: DevTools → Application → Cache Storage → `headman-api-cache-v1`

### Endpoints Covered (D-17)

| Endpoint pattern | Cached? | Notes |
|-----------------|---------|-------|
| `GET /api/academic/groups/:id/members` | YES | Group member list |
| `GET /api/academic/groups/:id/subjects` | YES | Group subjects list |
| `GET /api/academic/groups/:id/teachers` | YES | Group teachers list |
| `GET /api/academic/subjects*` | YES | Subject list + detail pages |
| `GET /api/academic/thresholds/resolve*` | YES | Resolved threshold per group+subject |
| `GET /api/attendance/reports/journal*` | YES | Journal data (SWR: fast load + background refresh) |
| `GET /api/attendance/excuses/pending*` | YES (200 only) | 404 not cached; graceful degradation preserved |
| `GET /api/attendance/late-checkins/pending*` | YES (200 only) | Same as excuses |

### Endpoints Deliberately Excluded

| Endpoint pattern | Reason |
|-----------------|--------|
| `PUT /api/attendance/lessons/:id/students/:id` | Mutation path — always network (D-18) |
| `POST /api/auth/*` | Auth operations must always reach network |
| `GET /api/academic/users/:id` | Student profile endpoint, not a headman group operation |
| `GET /api/schedule/*` | Schedule already handled separately (or via network) |

## Tests

File: `frontends/pwa/src/__tests__/sw-runtime-cache.test.ts`

12 test cases:
- 9 positive: all D-17 endpoint patterns return `true`
- 3 negative: `/api/auth/login`, `/api/attendance/lessons/123/students/456`, `/api/academic/users/1` return `false`

Full regression: **68 tests, 12 test files, all pass** (56 pre-plan + 12 new).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Broken JSDoc comment in sw-runtime-cache.ts**
- **Found during:** Task 1 — `npm run build`
- **Issue:** JSDoc comment contained `lessons/*/students/*` which caused TypeScript to parse `*/` as the comment-closing token, resulting in TS2304 error on the exposed code
- **Fix:** Replaced glob patterns with `:id` placeholder syntax in the comment
- **Files modified:** `frontends/pwa/src/sw-runtime-cache.ts`
- **Commit:** f09092c

**2. [Rule 1 - Bug] Pre-existing unused import in BottomNav.test.tsx blocking build**
- **Found during:** Task 1 — `npm run build` (confirmed pre-existing via `git stash` test)
- **Issue:** `import type { ReactNode } from 'react'` on line 4 of `BottomNav.test.tsx` triggered TS6133 under `noUnusedLocals: true`; `tsconfig.app.json` includes all `src/**`, so test files are checked at build time
- **Fix:** Removed the unused import line
- **Files modified:** `frontends/pwa/src/shared/components/__tests__/BottomNav.test.tsx`
- **Commit:** f09092c

## Future Hardening Note

**Cache eviction on logout (T-56-21 — deferred):** When a headman logs out, `headman-api-cache-v1` persists on-device until entries expire (max 24h). A future hardening step should call `caches.delete('headman-api-cache-v1')` from the logout handler in `AuthProvider.tsx`. This is out of scope for plan 56-06 per the plan's threat model disposition ("Mitigation baseline: cache TTL capped at 24h"). Tracked for a future security-hardening plan.

## Threat Surface Scan

No new network endpoints, auth paths, or schema changes introduced. All changes are confined to the Service Worker's client-side routing logic. Threat mitigations T-56-23, T-56-24, T-56-25 are implemented as designed (maxEntries, 200-only, GET-only).

## Self-Check: PASSED

- `frontends/pwa/src/sw-runtime-cache.ts` — exists
- `frontends/pwa/src/__tests__/sw-runtime-cache.test.ts` — exists
- `frontends/pwa/src/sw.ts` — extended (additive only, verified via git log)
- Commits `f09092c` and `fc84b94` — both exist on `main`
- `npx vitest run` — 68/68 pass
- `npm run build` — exits 0
