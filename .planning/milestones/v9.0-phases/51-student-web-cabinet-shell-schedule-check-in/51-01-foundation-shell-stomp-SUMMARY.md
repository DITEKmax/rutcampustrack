---
phase: 51
plan: 01
subsystem: frontend/web-panel
tags: [angular, stomp, sockjs, student-cabinet, foundation]
requires:
  - services/notification-service STOMP broker (ships in Phase 20)
  - services/schedule-service lessons endpoint (ships in Phase 13)
  - services/attendance-service student stats + checkin endpoints (ships in Phase 18)
  - services/academic-service subjects + thresholds endpoints (ships in Phase 8)
  - frontends/web-panel studentGuard (ships in Phase 50)
provides:
  - frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts (shared DTO types)
  - frontends/web-panel/src/app/features/student/shared/student-api.service.ts (StudentApiService)
  - frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts (SubjectCacheService)
  - frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts (StudentStompService)
  - frontends/web-panel/src/app/features/student/{dashboard,schedule,checkin}/*.component.ts (empty shells)
  - /student/dashboard, /student/schedule, /student/checkin routes
  - Sidebar STUDENT nav items (Главная / Расписание / Отметиться) + Студент role chip
affects:
  - frontends/web-panel/src/app/app.routes.ts (rewrote /student subtree)
  - frontends/web-panel/src/app/layout/sidebar/sidebar.component.{ts,html,spec.ts}
  - frontends/web-panel/package.json + package-lock.json
tech-stack:
  added:
    - "@stomp/stompjs ^7.3.0 (installed as ^7.1.0 range, resolved to 7.3.0 matching PWA)"
    - "sockjs-client ^1.6.1"
    - "@types/sockjs-client ^1.5.4 (devDep)"
  patterns:
    - HttpClient + inject() + Observable<T> (matches JournalApiService canonical)
    - HttpTestingController spec pattern (matches journal-api.service.spec.ts)
    - RxJS shareReplay(1) for per-id cache; catchError fallback
    - @stomp/stompjs Client with SockJS factory at /api/ws?token=... (mirrors PWA useStompCheckin)
    - RxJS Subject for push-style reactive stream
    - Angular @switch control-flow in templates (replaces ternary role chip)
key-files:
  created:
    - frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts
    - frontends/web-panel/src/app/features/student/shared/student-api.service.ts
    - frontends/web-panel/src/app/features/student/shared/student-api.service.spec.ts
    - frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts
    - frontends/web-panel/src/app/features/student/shared/subject-cache.service.spec.ts
    - frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts
    - frontends/web-panel/src/app/features/student/shared/student-stomp.service.spec.ts
    - frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts
    - frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts
    - frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts
  modified:
    - frontends/web-panel/package.json (+3 deps)
    - frontends/web-panel/package-lock.json
    - frontends/web-panel/src/app/app.routes.ts (/student subtree: real lazy imports, new /checkin child, title 'Главная')
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts (+1 primaryItem, +2 allNavItems)
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.html (@switch for role chip)
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts (inverted STUDENT render test)
  deleted:
    - frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts
decisions:
  - Locked @stomp/stompjs + sockjs-client + @types/sockjs-client to the same version range proven in Phase 30 PWA — no second transport stack to maintain
  - StudentStompService uses a plain Subject<AttendanceMarkedPayload> rather than BehaviorSubject — checkin flow has no 'current value' semantics; late subscribers should not replay stale events
  - SubjectCacheService caches the Observable<string> itself (with shareReplay(1)), not the resolved value — lets Angular HttpClient stay lazy and dedups concurrent subscribers without an extra in-memory map lookup
  - Empty-shell components carry data-testid="student-*-shell" markers so Plans 02-04 can replace the templates incrementally and keep e2e selectors stable
  - @switch over ternary in sidebar role chip — keeps adding the 4th role (HEADMAN, Phase 54+) to a single list instead of nested conditionals
metrics:
  duration: 43m
  completed: 2026-04-09
  files_created: 10
  files_modified: 6
  files_deleted: 1
  tests_added: 17
  tests_total_after: 179 (+17 from 162 baseline)
  requirements_closed: [STU-WEB-01, STU-WEB-02, STU-WEB-03]
---

# Phase 51 Plan 01: Foundation — Shell + STOMP Summary

**One-liner:** STOMP/SockJS toolchain installed, shared student domain layer (types + API + subject cache + STOMP client) built with 17 vitest specs, three empty-shell routes wired through `app.routes.ts`, and sidebar extended with Главная / Расписание / Отметиться plus "Студент" role chip — all behind the existing `studentGuard`.

## What Shipped

### Task 1 — STOMP toolchain + shared domain services (commit `c4c661f`)

Installed `@stomp/stompjs`, `sockjs-client`, `@types/sockjs-client` into web-panel, matching the exact versions already proven in the Phase 30 PWA. Created four files under `features/student/shared/`:

1. **`student-schedule.types.ts`** — canonical TS types mirroring backend DTOs verbatim:
   - `LessonResponse`, `LessonStatus`, `WeekType`, `AttendanceStatus`
   - `SubjectResponse`, `StudentStatsResponse`, `SubjectStats`, `OverallStats`
   - `ResolvedThresholdResponse`, `CheckinRequest`, `CheckinResponse`
   - `AttendanceMarkedPayload`, `StompEnvelope<T>`, `PagedResponse<T>`
   All field names byte-for-byte compatible with schedule-service / attendance-service / academic-service JSON bodies.
2. **`StudentApiService`** — `@Injectable({ providedIn: 'root' })` HttpClient wrapper:
   - `getWeekLessons(groupId, dateFrom, dateTo)` → `GET /api/schedule/groups/{id}/lessons?...&size=100`, unwraps `_embedded.lessonResponseList`
   - `getStudentStats()` → `GET /api/attendance/reports/student/stats`
   - `resolveGlobalThreshold()` → `GET /api/academic/thresholds/resolve` (no params)
   - `resolveGroupThreshold(groupId)` → same URL with `groupId` param
   - `checkin({lat, lng})` → `POST /api/attendance/checkin`
   Bearer token is added by the existing global `authInterceptor`.
3. **`SubjectCacheService`** — `shareReplay(1)` cache keyed by `subjectId`:
   - First subscribe hits `/api/academic/subjects/{id}` and maps `response.name`
   - Subsequent subscribes replay the cached emission (zero network)
   - `getName(0 | null | undefined)` returns `of('Предмет')` without network
   - HTTP errors fall back to `of('Предмет')` via `catchError`
4. **`StudentStompService`** — SockJS + @stomp/stompjs Client lifecycle:
   - `connect(groupId, getAccessToken)` builds a `Client` with `webSocketFactory: () => new SockJS('/api/ws?token=...')`, `reconnectDelay: 1000`, subscribes to `/topic/group/{groupId}` on connect, parses envelopes, emits `attendance.marked` payloads on `marked$`
   - `disconnect()` deactivates the client and nulls internal refs
   - Idempotent for repeated `connect()` calls with the same `groupId`
   - Hardened against T-51-01: no logging of token, URL, or headers anywhere

**Unit coverage:** 17 vitest specs (6 StudentApiService, 4 SubjectCacheService, 7 StudentStompService). STOMP spec uses `vi.mock('@stomp/stompjs')` and `vi.mock('sockjs-client')` to capture constructor args and simulate incoming frames without a real broker.

### Task 2 — Empty-shell components + route wiring (commit `3578441`)

Created three standalone OnPush components at:
- `features/student/dashboard/student-dashboard.component.ts`
- `features/student/schedule/student-schedule.component.ts`
- `features/student/checkin/student-checkin.component.ts`

Each ships only a `<section>` with a `data-testid` marker. Plans 02-04 will replace the templates.

Rewrote the `/student` block in `app.routes.ts`:
- `path: 'dashboard'` → `StudentDashboardComponent`, `title: 'Главная'`
- `path: 'schedule'` → `StudentScheduleComponent`, `title: 'Расписание'`
- **NEW:** `path: 'checkin'` → `StudentCheckinComponent`, `title: 'Отметиться'`
- Parent `canActivate: [studentGuard]` preserved from Phase 50
- `eyebrow: 'Студент'` preserved on every child

Deleted `features/student/student-placeholder/student-placeholder.component.ts`. `grep -rn student-placeholder src/` returns zero matches.

Angular prod build exits 0 (`npm run build`) — no TypeScript errors introduced. Pre-existing unrelated warnings (unused-import in `journal-cell.component.ts` and 152 kB bundle-budget overshoot) are out of scope for this plan and left untouched.

### Task 3 — Sidebar STUDENT nav + role chip (commit `df3b661`)

**`sidebar.component.ts`:**
- `primaryItems`: added `{ label: 'Главная', icon: 'ph-squares-four', route: '/student/dashboard', roles: ['STUDENT'] }`
- `allNavItems`: added `Расписание` (`ph-calendar-dots`) and `Отметиться` (`ph-map-pin`)
- `sectionLabel()` already returns `'Учёба'` for STUDENT (shipped Phase 50) — unchanged

**`sidebar.component.html`:** Replaced the hardcoded `user.role === 'ADMIN' ? 'Администратор' : 'Преподаватель'` ternary on line 79 with an Angular 19 `@switch` block covering ADMIN → Администратор, TEACHER → Преподаватель, STUDENT → Студент. Consistent with the file's existing `@for` / `@if` usage.

**`sidebar.component.spec.ts`:** Inverted the Phase 50 test `"renders no nav items for plain STUDENT role (placeholder phase — D-06)"` into `"renders filtered nav items for plain STUDENT role (Phase 51 — STU-WEB-01..03)"`. Asserts presence of Главная / Расписание / Отметиться / Студент and absence of Журнал посещаемости / Пользователи / Группы / Семестры / Статистика.

**Full web-panel vitest suite:** 179/179 passing (162 Phase 50 baseline + 17 new shared specs on top of the updated sidebar spec).

## Deviations from Plan

None — plan executed exactly as written. Task 1 auto-selected `@stomp/stompjs ^7.3.0` (the range `^7.1.0` from the plan resolves to 7.3.0, which is the exact version already shipped in the Phase 30 PWA — intentional alignment, not a deviation).

## Threat Model Follow-up

All seven STRIDE entries from the plan's `<threat_model>` block are honored:

- **T-51-01 (Info disclosure — STOMP token):** `StudentStompService.onStompError` logs only `frame.headers['message']`. `grep -rnE "console\.log.*(token|ws\?|accessToken)" frontends/web-panel/src/app/features/student` returns zero matches. The SockJS URL is constructed inline inside `webSocketFactory` and never stored in a field.
- **T-51-02 (Checkin tampering):** `StudentApiService.checkin()` sends only `{lat, lng}`; no client-side lesson selection.
- **T-51-03 (EoP — route bypass):** `/student` parent route still `canActivate: [studentGuard]`; children inherit. Three new children are all under the guarded parent.
- **T-51-04 (Supply chain):** Pinned versions match the proven PWA combination from Phase 30. Standard npm only; no private registries.
- **T-51-05 (Console logs):** No new `console.log` calls anywhere in the new code — only `console.error` in `onStompError` with a narrow header message.
- **T-51-06 (Repudiation):** Accepted — server-side attendance-service logs are the audit trail.
- **T-51-07 (DoS):** `reconnectDelay: 1000`, single Client per mount, torn down in `disconnect()`.

## Known Stubs

The three empty-shell components (`student-dashboard`, `student-schedule`, `student-checkin`) render only a `<section>` with a `data-testid` marker. These are **intentional stubs by design** — Plans 02 (schedule), 03 (checkin), and 04 (dashboard) in this same phase each fill one page without colliding on the foundation. This is the explicit Plan-01 contract (see plan `<objective>` and `<done>` criteria on each task).

## Verification

- `cd frontends/web-panel && npx vitest run src/app/features/student/shared` → 17/17 green (Task 1)
- `cd frontends/web-panel && npm run build` → exit 0, bundle complete (Task 2)
- `cd frontends/web-panel && npm test` → 179/179 green across 28 test files (Task 3 + regression)
- `grep -rn "student-placeholder" frontends/web-panel/src` → 0 matches
- `grep -rnE "console\.log.*(token|ws\?|accessToken)" frontends/web-panel/src/app/features/student` → 0 matches

## Commits

| # | Hash | Message |
|---|------|---------|
| 1 | `c4c661f` | feat(51-01): install STOMP toolchain + shared student domain services |
| 2 | `3578441` | feat(51-01): empty-shell student pages + real route wiring (STU-WEB-01..03) |
| 3 | `df3b661` | feat(51-01): extend sidebar with STUDENT nav items and role chip |

## Self-Check: PASSED

- FOUND: frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts
- FOUND: frontends/web-panel/src/app/features/student/shared/student-api.service.ts
- FOUND: frontends/web-panel/src/app/features/student/shared/student-api.service.spec.ts
- FOUND: frontends/web-panel/src/app/features/student/shared/subject-cache.service.ts
- FOUND: frontends/web-panel/src/app/features/student/shared/subject-cache.service.spec.ts
- FOUND: frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts
- FOUND: frontends/web-panel/src/app/features/student/shared/student-stomp.service.spec.ts
- FOUND: frontends/web-panel/src/app/features/student/dashboard/student-dashboard.component.ts
- FOUND: frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts
- FOUND: frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts
- FOUND: commit c4c661f
- FOUND: commit 3578441
- FOUND: commit df3b661
- FOUND: frontends/web-panel/src/app/features/student/student-placeholder/student-placeholder.component.ts NOT present (expected — deleted in Task 2)
