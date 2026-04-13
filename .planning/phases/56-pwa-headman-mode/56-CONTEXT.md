# Phase 56: PWA Headman Mode - Context

**Gathered:** 2026-04-13
**Status:** Ready for planning

<domain>
## Phase Boundary

React PWA (`frontends/pwa/`) extended for HEADMAN users (`is_headman=true` in JWT):

- AuthProvider exposes `isHeadman: boolean` from JWT `is_headman` claim
- BottomNav renders a 5th tab "Группа" (only for headmen) — positioned before "Профиль"
- New `/group` route shows a hub screen with 7 cards linking to all headman features (HEAD-WEB-02..08)
- Mobile-first implementations of: Обзор, Студенты группы, Предметы CRUD, Журнал, Пропуски (graceful degradation), Запросы отметки (graceful degradation), Статистика + threshold
- Service Worker extended with runtime caching (stale-while-revalidate) for headman GET endpoints
- 63 existing student vitest tests remain untouched and pass unchanged

**Out of scope:**
- Any modification to existing student feature components (`features/{home,schedule,checkin,profile,push,auth}`)
- Backend excuse/late-checkin approval endpoints (not implemented — graceful degradation only)
- Landing HEADMAN section (Phase 57)
- New headman business capabilities beyond HEAD-WEB-02..08 parity
</domain>

<decisions>
## Implementation Decisions

### Navigation Structure

- **D-01:** Fifth BottomNav tab "Группа" with Phosphor `Users` icon (regular/fill weight variants,
  matching existing tabs). Positioned **before Профиль** (order: Главная → Расписание → Отметка →
  Группа → Профиль). Note: this differs from ROADMAP wording "after the four existing student
  tabs" — user explicitly chose this ordering for UX consistency; document in SUMMARY.

- **D-02:** Tab filtering — refactor the hardcoded `tabs` constant in
  `frontends/pwa/src/shared/components/BottomNav.tsx` into a `useTabs()` hook that reads
  `isHeadman` from `useAuth()` and returns 4 or 5 tabs. Hook lives in
  `shared/components/useTabs.ts`. BottomNav becomes `const tabs = useTabs()`. Keeps the
  map-based rendering clean and testable.

- **D-03:** Internal navigation within the "Группа" tab uses a **hub-and-detail** pattern:
  - `/group` — hub screen with 7 cards (Обзор, Студенты, Предметы, Журнал, Пропуски,
    Запросы отметки, Статистика)
  - `/group/overview`, `/group/students`, `/group/subjects`, `/group/journal`,
    `/group/excuses`, `/group/late-checkin`, `/group/stats` — each is a flat route with a
    header + back button (ArrowLeft icon) that navigates to `/group`
  - No nested tabs or iOS-style stack navigation — flat routes keep React Router config
    simple and consistent with existing `/home`, `/schedule`, `/checkin`, `/profile`

### Auth Extension

- **D-04:** Extend `frontends/pwa/src/features/auth/AuthProvider.tsx`:
  - `parseJwt` return type gains `is_headman?: boolean` field
  - `AuthUser` type (in `features/auth/api.ts` or similar) gains `isHeadman: boolean`
    (derived from `is_headman ?? false`)
  - `tokenToUser()` populates `isHeadman`
  - `useAuth()` exposes `isHeadman` via the same context
  - This is an **additive** change — existing `user.role` / `user.groupId` consumers unaffected

- **D-05:** `auth` feature directory IS in the "frozen" list (D-13), but AuthProvider.tsx is
  explicitly called out in ROADMAP notes as requiring extension. The extension is
  purely additive (new field on existing type, new parsing line) — existing `AuthProvider.test.tsx`
  must continue passing. Add a new test case `isHeadman is true when JWT has is_headman claim`
  in a separate test file `AuthProvider.isHeadman.test.tsx` to avoid modifying the original.

### Journal UX (Mobile-First)

- **D-06:** Two-step flow instead of Angular's matrix grid:
  1. **Selectors screen:** subject dropdown + date picker (single-day selection, defaults to today)
  2. **Student list screen:** vertical scrollable list of students in the group; each row shows
     student name + `SegmentedControl` with 5 segment-buttons: `[б][н][у][сп][—]`
     (present/absent/excused/free_attendance/cancelled). One tap sets the status immediately.

- **D-07:** Cell interaction — **always-visible segmented buttons** (NOT cycle-on-tap).
  Single tap = status set. Selected segment is visually highlighted (accent-primary bg + bold).
  This requires `lessonId` in the response (already addressed via Phase 55 D-01 backend fix).

- **D-08:** Optimistic UI on segment tap: update the local signal/state immediately, send
  `PUT /api/attendance/lessons/{lessonId}/students/{userId}` with `{ status }`, on error
  revert + show motion-based inline error badge (consistent with PWA's existing error patterns
  — do NOT introduce MatSnackBar; use Motion `<AnimatePresence>` with a toast-like element).

- **D-09:** Journal loads data via `GET /api/attendance/reports/journal?groupId=X&subjectId=Y&dateFrom=Z&dateTo=Z`
  (single day window when date selected). Extract rows per student from the returned `JournalCell`
  list; if no lessons for selected day → empty state "На эту дату занятий нет".

### Excuses + Late Check-in — Graceful Degradation

- **D-10:** Both pages (`/group/excuses`, `/group/late-checkin`) are thin shells. On mount
  call respective API (`getPendingExcuses()`, `getPendingLateCheckins()`). On 404/network error
  → render empty-state card: *"Функция находится в разработке. Заявки появятся здесь
  автоматически."* Same pattern as Angular web-panel (Phase 55 D-06/07) but using PWA's
  visual primitives.

### Stats + Red-Zone Threshold (Mobile)

- **D-11:** Stats screen renders a vertical list of subject cards. Each card shows:
  - Subject name (top)
  - Group average attendance % (large number)
  - Per-student mini-rows (name + % + red-dot if below threshold)
  - Inline threshold editor at card bottom: small `<input type="number" min="0" max="100">`
    with "%" suffix, labeled "Порог:", on blur → `PUT /api/academic/thresholds/subject`
  - No charts in this phase — text + colored dots only (keeps bundle size small)

- **D-12:** Data loading: mirror Angular approach —
  1. `getSubjects()` → list of group's subjects
  2. `forkJoin` / `Promise.all` per subject: `getJournal(groupId, subjectId, semesterStart, today)`
  3. Derive per-student attendance rate from cell statuses client-side
  4. `resolveThreshold(groupId, subjectId)` per subject to get effective threshold
  5. Render sorted by descending red-zone count (subjects needing attention first)

### Code Organization

- **D-13:** **New code location:** `frontends/pwa/src/features/headman/` — NEW directory.
  Subfolders: `group-hub/`, `overview/`, `students/`, `subjects/`, `journal/`, `excuses/`,
  `late-checkin/`, `stats/`, plus `shared/` for the `headmanApi.ts` service module.

- **D-14:** **Frozen directories (no modification):** `features/home/`, `features/schedule/`,
  `features/checkin/`, `features/profile/`, `features/push/`, `features/auth/`.
  Exception: `AuthProvider.tsx` / auth types extension per D-04 (additive only, verified by
  existing tests passing unchanged).
  `shared/`, `lib/`, `main.tsx`, `BottomNav.tsx`, `AppShell.tsx`, `sw.ts` are extensible.

- **D-15:** **Shared primitives** (`shared/components/`): add `SegmentedControl` component
  (5-segment attendance status picker) if reusable; otherwise keep in `features/headman/journal/`.
  Hooks like `useHeadmanApi` stay in `features/headman/shared/`.

### Service Worker Runtime Caching

- **D-16:** Workbox strategy: **Stale-While-Revalidate (SWR)** for all headman GET endpoints.
  Fast UI from cache + background refresh. Matches ROADMAP success criterion wording exactly.

- **D-17:** Endpoints covered (single URL pattern in `registerRoute`):
  - `GET /api/academic/groups/:id/members`
  - `GET /api/academic/groups/:id/subjects`
  - `GET /api/academic/subjects/*` (teachers/details)
  - `GET /api/attendance/reports/journal`
  - `GET /api/academic/thresholds/resolve`
  - `GET /api/attendance/excuses/pending`, `GET /api/attendance/late-checkins/pending`
    (these may 404 — cache the 404s? NO, per D-19)

- **D-18:** Cache details (ExpirationPlugin + CacheableResponsePlugin):
  - **TTL:** `maxAgeSeconds: 86400` (24 hours) — auto-rotation of stale entries
  - **maxEntries:** 100 per cache bucket — prevents unbounded growth
  - **Methods:** GET only — PUT/POST/PATCH/DELETE mutations always go to network (no caching)
  - **Statuses:** `CacheableResponsePlugin({ statuses: [200] })` — do NOT cache 4xx/5xx

- **D-19:** Cache naming: single named cache `headman-api-cache-v1` for all headman GET
  endpoints (simplifies ExpirationPlugin bookkeeping). Workbox `registerRoute` with a URL
  matcher function that returns true for the paths in D-17.

- **D-20:** SW code goes in `frontends/pwa/src/sw.ts` (existing file) — add `registerRoute`
  import from `workbox-routing`, `StaleWhileRevalidate` from `workbox-strategies`,
  `ExpirationPlugin` from `workbox-expiration`, `CacheableResponsePlugin` from
  `workbox-cacheable-response`. Append after the existing `precacheAndRoute` call.

### Testing

- **D-21:** New tests (all ADDED, not replacing):
  - `PWAHeadmanRole.test.tsx` — verify 5th tab renders when `isHeadman=true`, hidden for
    plain students (ROADMAP note requirement)
  - `AuthProvider.isHeadman.test.tsx` — verify JWT parsing exposes `isHeadman`
  - `headman/journal/JournalPage.test.tsx` — subject+date selection → segment tap →
    optimistic UI → error revert
  - `headman/stats/StatsPage.test.tsx` — per-subject threshold edit + red-dot logic
  - Graceful-degradation tests for excuses / late-checkin (404 → empty state renders)

- **D-22:** All 63 existing tests must pass unchanged. CI gate: `vitest run` exits with
  0 failures, no existing test files deleted or modified. If any existing test breaks
  during implementation, STOP and redesign — likely means a shared primitive was changed
  destructively.

### Claude's Discretion

- Exact visual styling of `SegmentedControl` (color palette flows from existing Transit Grid
  tokens; height/padding to match touch-target ≥44px)
- Whether to use Motion `AnimatePresence` for hub card entrance animations (consistent with
  existing PWA page transitions per design-decisions.md)
- Semester window for stats — default to current academic semester; fallback to last 90 days
  if semester endpoint unavailable
- Exact icon choice for each hub card (use Phosphor Icons matching the student tab style —
  e.g., ChartBar for stats, FileText for excuses)
- Whether to preload headman data on app startup (via React Query `prefetchQuery`) or lazy
  per-route — lazy is default unless measurable UX benefit
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements + Project Context
- `.planning/REQUIREMENTS.md` §PWA HEADMAN Mode (PWA-HEAD-01..04) — four requirements for this phase
- `.planning/REQUIREMENTS.md` §Headman Web Cabinet (HEAD-WEB-02..08) — feature parity source
- `.planning/ROADMAP.md` §Phase 56 — notes block with AuthProvider + BottomNav hints
- `CLAUDE.md` — coding rules, frontend design standards
- `docs/design-decisions.md` — PWA brandbook, Motion animation patterns, Transit Grid tokens

### Prior Phase Contexts (consistency anchors)
- `.planning/phases/55-headman-web-cabinet-attendance-management-stats/55-CONTEXT.md`
  — Angular web-panel decisions for same features; mirror business logic, NOT UI patterns
- `.planning/phases/51-student-web-cabinet-shell-schedule-check-in/51-UI-SPEC.md`
  — PWA visual language and Motion patterns

### PWA — Existing Code (extension targets)
- `frontends/pwa/src/features/auth/AuthProvider.tsx`
  — D-04 additive extension for `isHeadman`
- `frontends/pwa/src/features/auth/api.ts` — `AuthUser` type extension
- `frontends/pwa/src/shared/components/BottomNav.tsx`
  — D-02 refactor to `useTabs()` hook; add 5th tab
- `frontends/pwa/src/shared/components/AppShell.tsx` — outlet structure (unchanged)
- `frontends/pwa/src/main.tsx` — add `/group/*` lazy routes inside existing `/` parent
- `frontends/pwa/src/sw.ts` — D-20 extend with runtime caching
- `frontends/pwa/vite.config.ts` — VitePWA `injectManifest` strategy (no change expected)

### Backend — Attendance (HEAD-WEB-05 Journal)
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/MarkingApi.java`
  — `PUT /attendance/lessons/{lessonId}/students/{userId}`
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/JournalCell.java`
  — must include `lessonId` after Phase 55 D-01 fix (verify this landed before Phase 56 impl)
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java`

### Backend — Academic (HEAD-WEB-03/04/08)
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/ThresholdApi.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/threshold/SetThresholdRequest.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/threshold/ResolvedThresholdResponse.java`

### Angular Reference (business logic mirror)
- `frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts`
  — API method signatures to mirror in PWA `features/headman/shared/headmanApi.ts`

### Workbox Docs (SW strategies)
- `https://developer.chrome.com/docs/workbox/reference/workbox-strategies/#type-StaleWhileRevalidate`
- `https://developer.chrome.com/docs/workbox/modules/workbox-expiration`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AuthProvider` + `useAuth()` — context + JWT parser; ready for additive extension
- `BottomNav` tab array pattern — easy refactor target for `useTabs()` hook
- `AppShell` outlet — Router outlet works for any number of nested routes
- Motion + Phosphor Icons + Tailwind + Transit Grid design tokens — full visual toolkit
- TanStack Query (`shared/lib/queryClient.ts`) — for API calls with stale-while-revalidate
  client-side semantics complementing SW cache
- `features/schedule/` and `features/checkin/` — good PWA reference for list screens with
  Motion entrance animations and error states
- `LoadingSpinner` primitive — reuse for route suspense fallbacks

### Established Patterns
- Lazy route loading: `lazy(() => import('./features/X/Page').then(m => ({ default: m.Page })))`
  wrapped in `<Suspense fallback={<LoadingSpinner />}>`
- Axios client (`shared/lib/axios.ts`) with token getter/refresh/logout callbacks — all new
  headman API calls go through this
- CSS: Tailwind 4 + CSS vars from Transit Grid (`var(--accent-primary)`, `var(--text-secondary)`)
- Motion `layoutId` shared-layout animation — pattern for tab pill slide (preserve for 5th tab)

### Integration Points
- `main.tsx` router config — add 8 lazy-loaded child routes under `/` parent (hub + 7 details)
- `BottomNav.tabs` → `useTabs()` — single change point for role-aware tab rendering
- `AuthProvider.parseJwt` return type + `tokenToUser()` — extend both for `isHeadman`
- `sw.ts` — append Workbox runtime route registration; don't touch precache/push logic
- Phase 55 backend change (`JournalCell.lessonId`) — MUST be deployed before Phase 56 impl starts

### Key Gaps Confirmed
- No `features/headman/` exists yet (starting fresh — no risk of accidental modification)
- No shared API service for headman — `headmanApi.ts` will be new, mirroring Angular's
  `HeadmanApiService` method surface
- SW has NO runtime caching today — only precache + push. D-16..D-20 add from scratch.
- `useAuth().user` shape does NOT expose `isHeadman` today — D-04 adds it
- BottomNav `tabs` is a module-level const (line 24-29 of BottomNav.tsx) — simple refactor
  to hook
</code_context>

<specifics>
## Specific Ideas

- **User-chosen tab ordering:** 5th tab goes **before Профиль** (user said "перед Профилем").
  This is a deliberate deviation from ROADMAP wording ("after the four existing student tabs")
  — the rationale is grouping functional tabs together. Downstream agents should NOT correct
  this back to the end.
- **Always-visible segment buttons** for journal cells (not cycle-on-tap like Angular) — the
  user values immediate predictability on mobile over compactness.
- **SWR strategy for SW with recommended defaults** (24h TTL, 100 entries, GET-only, 200-only)
  — confirmed by user.
- **Feature coverage:** all 7 sections in the hub from first iteration (Обзор, Студенты,
  Предметы, Журнал, Пропуски, Запросы отметки, Статистика). Excuses + late-checkin render
  graceful-degradation empty states — no approval actions.
- **Strict test contract:** 63 existing tests must NOT be deleted or modified. New tests only.
  This is a HARD gate for Phase 56 acceptance.
</specifics>

<deferred>
## Deferred Ideas

- **Charts in stats** — mobile stats uses text + dots only this phase; line/bar charts
  (Chart.js or similar) can be a later phase when a library choice is made
- **Excuse/late-checkin approval flow UI** — blocked on backend endpoints (future milestone);
  current phase only ships graceful degradation
- **Bulk-mark-all / mass action** on journal — backlog item, not in HEAD-WEB-05 scope
- **Push notifications for headman events** (new excuse ticket arrived, late-checkin request
  pending) — belongs in a future Notification phase, not here
- **Offline mutation queue** (background sync for PUT /markAttendance when offline) — not in
  this phase; SW caching here is read-only per D-18
- **Headman web cabinet tests beyond PWA** — covered by Phase 55 separately

None — discussion stayed within phase scope.
</deferred>

---

*Phase: 56-pwa-headman-mode*
*Context gathered: 2026-04-13*
