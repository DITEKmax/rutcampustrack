---
phase: 53
plan: "04"
subsystem: web-panel
tags: [angular, student, pwa, banner, localStorage, beforeinstallprompt, ios-fallback, animation]
dependency_graph:
  requires: [53-01]
  provides: []
  affects: [frontends/web-panel]
tech_stack:
  added: []
  patterns: [localStorage-signal-state, beforeinstallprompt-deferred, noop-animations-in-tests]
key_files:
  created:
    - frontends/web-panel/src/app/features/student/shared/student-banner.service.ts
    - frontends/web-panel/src/app/layout/shell/student-pwa-banner/student-pwa-banner.component.ts
    - frontends/web-panel/src/app/layout/shell/student-pwa-banner/student-pwa-banner.component.html
    - frontends/web-panel/src/app/layout/shell/student-pwa-banner/student-pwa-banner.component.css
  modified:
    - frontends/web-panel/src/app/layout/shell/student-pwa-banner/student-pwa-banner.component.spec.ts
    - frontends/web-panel/src/app/layout/shell/shell.component.ts
    - frontends/web-panel/src/app/layout/shell/shell.component.html
decisions:
  - "provideNoopAnimations() used in tests instead of provideAnimations() — jsdom lacks element.animate (Web Animations API)"
  - "StudentBannerService.init() is idempotent — safe to call from ngOnInit on every navigation"
  - "Banner self-manages visibility via shouldShow() signal — ShellComponent adds no extra conditions"
metrics:
  duration: "~20 min"
  completed: "2026-04-09"
  tasks_completed: 2
  tasks_total: 2
  files_created: 4
  files_modified: 3
  tests_before: 256
  tests_after: 262
---

# Phase 53 Plan 04: PWA Install Banner Summary

**One-liner:** StudentBannerService (localStorage pwa-banner-dismissed signal) + StudentPwaBannerComponent (beforeinstallprompt / iOS-fallback / bannerSlide animation / dismiss ×) integrated into ShellComponent between header and main — 262 tests passing.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | StudentBannerService + StudentPwaBannerComponent | b21fdab | student-banner.service.ts, student-pwa-banner.component.ts/html/css/spec.ts |
| 2 | Интеграция баннера в ShellComponent | f0f72c3 | shell.component.ts, shell.component.html |

## What Was Built

### StudentBannerService (`features/student/shared/student-banner.service.ts`)
- `shouldShow` — Angular `signal(false)` updated by `init()` and `dismiss()`
- `init()` — checks `user.role !== 'STUDENT'` → false; checks `localStorage['pwa-banner-dismissed'] === 'true'` → false; otherwise → true
- `dismiss()` — `localStorage.setItem('pwa-banner-dismissed', 'true')` + `shouldShow.set(false)`
- `SHOWN_KEY` tracks first display (analytics-ready, not used for gate logic)

### StudentPwaBannerComponent (`layout/shell/student-pwa-banner/`)
- `@if (bannerService.shouldShow())` — no DOM when hidden (STU-WEB-10 requirement)
- `bannerSlide` animation — `translateY(-100%)` → `translateY(0)` on enter, fade+slide on leave
- iOS detection via `navigator.userAgent` — shows "Открыть в Safari" link to `/app/`
- Chrome/Edge — listens for `beforeinstallprompt`, stores `deferredPrompt`, shows "Установить приложение" button
- Other browsers — generic "Открыть приложение" link to `/app/`
- `aria-label="Закрыть баннер"` on dismiss × button (accessibility)
- `ngOnDestroy` removes `beforeinstallprompt` listener (no memory leak)

### ShellComponent integration
- `StudentPwaBannerComponent` added to `imports[]`
- `<app-student-pwa-banner />` placed between `<app-header />` and `<main>`
- No extra visibility conditions in Shell — component manages itself

### Tests (7 new)
- `StudentPwaBannerComponent`: не рендерит при shouldShow=false, рендерит при shouldShow=true, dismiss вызывает service.dismiss()
- `StudentBannerService`: dismissed=true → false, TEACHER → false, STUDENT без флага → true, dismiss() устанавливает localStorage

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] provideAnimations() → provideNoopAnimations() in spec**
- **Found during:** Task 1 (GREEN phase — tests failed with `element.animate is not a function`)
- **Issue:** `provideAnimations()` triggers real Web Animations API which jsdom doesn't implement; causes `TypeError: element.animate is not a function` in two component render tests
- **Fix:** Replaced all three `provideAnimations()` calls with `provideNoopAnimations()` in the spec — animations are stubbed out in tests, which is standard practice for Angular component testing in jsdom
- **Files modified:** `student-pwa-banner.component.spec.ts`
- **Commits:** b21fdab

## Known Stubs

None — all plan deliverables are fully wired. `StudentBannerService.init()` reads real `localStorage` and real `AuthService.currentUser()` signal.

## Threat Flags

No new threat surface beyond what the plan's threat model covers. `pwa-banner-dismissed` localStorage key contains only the string `'true'` — no PII, no tokens.

## Self-Check: PASSED

- [x] `frontends/web-panel/src/app/features/student/shared/student-banner.service.ts` — exists, contains `StudentBannerService` and `pwa-banner-dismissed`
- [x] `frontends/web-panel/src/app/layout/shell/student-pwa-banner/student-pwa-banner.component.ts` — exists, contains `StudentPwaBannerComponent`, `beforeinstallprompt`, `promptHandler`, `bannerSlide`
- [x] `frontends/web-panel/src/app/layout/shell/student-pwa-banner/student-pwa-banner.component.html` — exists, contains `Закрыть баннер`, `@if (bannerService.shouldShow())`
- [x] `frontends/web-panel/src/app/layout/shell/shell.component.ts` — contains `StudentPwaBannerComponent` in imports[]
- [x] `frontends/web-panel/src/app/layout/shell/shell.component.html` — contains `<app-student-pwa-banner />`
- [x] Commit b21fdab — verified in git log
- [x] Commit f0f72c3 — verified in git log
- [x] 262 tests passing (39 test files)
