---
phase: 55
plan: 04
status: complete
---

# Plan 55-04 Summary — Headman Excuses & Late-Checkin Shells

## What Was Built

Two graceful-degradation shell components for the headman cabinet:

- **`HeadmanExcusesComponent`** (`/headman/excuses`) — fetches pending excuse tickets, handles 404/error via `catchError(() => of(null))`, shows `.page-empty` with D-07 text
- **`HeadmanLateCheckinComponent`** (`/headman/late-checkin`) — same pattern for late check-in requests

## Key Files

### Created
- `frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts`
- `frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.spec.ts`
- `frontends/web-panel/src/app/features/headman/late-checkin/headman-late-checkin.component.ts`
- `frontends/web-panel/src/app/features/headman/late-checkin/headman-late-checkin.component.spec.ts`

## Test Results

- 6 new tests: all PASSED
- Spec pattern: mock service returns `throwError(() => new Error('404'))` → component shows `.page-empty` with exact D-07 text

## Deviations

- Specs use `provideNoopAnimations()` instead of `provideAnimations()` — jsdom does not implement the Web Animations API (`element.animate`), so real animations fail in test environment. Noop animations are the correct approach for all animated component specs in this project.

## Self-Check: PASSED
