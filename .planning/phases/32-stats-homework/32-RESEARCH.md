# Phase 32: Stats + Homework — Research

**Researched:** 2026-04-06
**Domain:** React PWA — Attendance Statistics, Homework Tracking, BottomNav Restructure
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Use server-side completion via `POST /academic/homeworks/{id}/complete` and `DELETE /academic/homeworks/{id}/complete`. `HomeworkResponse.completed` is per-student, from `HomeworkCompletion` table. Overrides any localStorage-only approach.
- **D-02:** Optimistic UI toggle: checkbox state updates immediately on tap, mutation sent to backend; on error — revert + show inline `«Не удалось сохранить»` in `text-destructive text-xs`.
- **D-03:** Replace «Главная» placeholder tab with «Статистика» (ChartBar icon). Add «Задания» (ClipboardText icon) as 5th tab. New order: Статистика, Расписание, Отметка, Задания, Профиль. Remove `/home` route.
- **D-04:** Default landing after login changes from `/home` to `/schedule`.
- **D-05:** Fetch effective threshold via `GET /api/academic/thresholds/resolve?groupId={groupId}`. Compare each subject's percentage against it for red zone status.
- **D-06:** If threshold API returns no configured threshold (404 or null), hide red zone indicators entirely — no assumed default.
- **D-07:** `staleTime: 60 * 60 * 1000` (1hr) for both stats and homework TanStack Query hooks.
- **D-08:** `refetchOnReconnect: true` for all queries. OfflineBanner shows stale time. Pull-to-refresh available.

### Claude's Discretion

- Exact TanStack Query hook implementation details (queryKey structure, error handling)
- Loading skeleton vs spinner choice per screen
- How to obtain student's groupId and semesterId for homework API call (from auth context or separate endpoint)
- AttendanceRecordsPage: whether to group records by date or show flat list
- Homework sort order refinement (undone first by deadline, done below)
- Stagger animation timing for list items

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ATT-01 | User can view attendance stats per subject (percentage, count) | `GET /attendance/reports/student/stats` returns `StudentStatsResponse` with `List<SubjectStats>`; each has subjectId, subjectName, total, attended, absent, excused, percentage |
| ATT-02 | User sees red zone warning when below threshold for a subject | `GET /academic/thresholds/resolve?groupId=X` returns `ResolvedThresholdResponse.minPercentage`; compare per-subject percentage; hide indicators on 404/null |
| ATT-03 | User can view attendance records list with status indicators (б/н/у/сп) | `GET /attendance/reports/student/records?subjectId=X` returns `CollectionModel<EntityModel<AttendanceRecordEntry>>`; each has lessonDate, symbol, status; reuse StatusBadge |
| HW-01 | User can view homework list for their group | `GET /academic/homeworks?groupId=X&semesterId=Y` returns paginated `HomeworkResponse`; `completed` boolean is per-student from HomeworkCompletion table |
| HW-02 | User can mark homework as done/undone (personal completion tracker) | `POST /academic/homeworks/{id}/complete` (204) + `DELETE /academic/homeworks/{id}/complete` (204); optimistic toggle per D-02 |
</phase_requirements>

---

## Summary

Phase 32 is a pure React PWA frontend phase — no backend changes. All five backend endpoints are already implemented and verified in the API contracts. The work involves creating two new feature folders (`features/attendance/`, `features/homework/`), restructuring BottomNav from 4 to 5 tabs, adding three new routes, and wiring TanStack Query hooks for three distinct API calls.

The primary complexity is in three areas: (1) the homework `semesterId` parameter — the frontend has no current mechanism to know the active semester, requiring either a `GET /academic/semesters` list-and-filter call or a dedicated hook; (2) optimistic mutation handling for homework completion toggle with revert-on-error; and (3) the threshold-gated red zone display where a missing threshold (404) means silently hiding all indicators.

**Primary recommendation:** Build `features/attendance/` and `features/homework/` following the established feature folder pattern from Phase 30, with dedicated `api.ts`, `types.ts`, and component files per feature. Resolve semesterId via a `useActiveSemester` hook that fetches `GET /academic/semesters?size=50` and filters `active === true` — cache 24hr since semester rarely changes.

---

## Standard Stack

### Core (already installed — verified in codebase)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| React | 18 | UI framework | Project foundation |
| React Router v7 | 7.x | Routing, lazy loading | Already used in main.tsx |
| TanStack Query | 5.x | Server state, caching, mutations | Already used (useWeekSchedule, useCheckin) |
| Axios | 1.x | HTTP client with JWT interceptor | Already wired in shared/lib/axios.ts |
| motion (framer-motion v12) | 12.x | Page transitions, stagger, layout animations | Already used in SchedulePage, AppShell |
| @phosphor-icons/react | 2.x | Icon library | Project standard per design-decisions.md |
| shadcn/ui (card, button) | — | Card, Button components | Already in components/ui/ |
| Tailwind CSS v4 | 4.x | Utility styling | Project standard |

[VERIFIED: codebase grep — all packages present in frontends/pwa/src/]

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Vitest + jsdom | 3.x + 26.x | Unit testing | All new hooks and components get tests |
| @testing-library/react | 16.x | Component testing | Established test pattern |

[VERIFIED: frontends/pwa/package.json]

**Installation:** No new packages needed — all dependencies are already installed.

---

## Architecture Patterns

### Established Feature Folder Structure (replicate exactly)

```
frontends/pwa/src/
├── features/
│   ├── attendance/                ← NEW
│   │   ├── types.ts               ← StudentStatsResponse, SubjectStats, AttendanceRecordEntry TS types
│   │   ├── api.ts                 ← useStudentStats, useAttendanceRecords, useThreshold hooks
│   │   ├── AttendanceStatsPage.tsx
│   │   ├── SubjectStatRow.tsx
│   │   ├── AttendanceRecordsPage.tsx
│   │   ├── AttendanceRecordRow.tsx
│   │   ├── RedZoneBadge.tsx
│   │   └── __tests__/
│   │       └── AttendanceStatsPage.test.tsx
│   └── homework/                  ← NEW
│       ├── types.ts               ← HomeworkResponse TS type
│       ├── api.ts                 ← useHomework, useActiveSemester hooks + toggle mutations
│       ├── HomeworkPage.tsx
│       ├── HomeworkItem.tsx
│       └── __tests__/
│           └── HomeworkItem.test.tsx
```

[VERIFIED: codebase — matches pattern from features/schedule/ and features/push/]

### Pattern 1: TanStack Query Hook with 1hr staleTime

Replicate the established `useWeekSchedule` pattern:

```typescript
// Source: frontends/pwa/src/features/schedule/api.ts (verified)
export function useStudentStats() {
  return useQuery<StudentStatsResponse>({
    queryKey: ['attendance', 'stats'],
    queryFn: async () => {
      const { data } = await apiClient.get('/attendance/reports/student/stats')
      // HATEOAS EntityModel — content is at data (Spring HATEOAS unwraps via Jackson)
      return data
    },
    staleTime: 60 * 60 * 1000,  // D-07
    refetchOnReconnect: true,    // D-08
  })
}
```

[VERIFIED: frontends/pwa/src/features/schedule/api.ts]

### Pattern 2: Optimistic Mutation with Revert

TanStack Query `useMutation` with `onMutate` / `onError` / `onSettled` for D-02:

```typescript
// Source: TanStack Query docs pattern [ASSUMED — standard pattern]
const toggleMutation = useMutation({
  mutationFn: async ({ id, completed }: { id: number; completed: boolean }) => {
    if (completed) {
      await apiClient.post(`/academic/homeworks/${id}/complete`)
    } else {
      await apiClient.delete(`/academic/homeworks/${id}/complete`)
    }
  },
  onMutate: async ({ id, completed }) => {
    await queryClient.cancelQueries({ queryKey: ['homework'] })
    const previous = queryClient.getQueryData(['homework'])
    // optimistic update in cache
    queryClient.setQueryData(['homework'], (old) => /* toggle completed */)
    return { previous }
  },
  onError: (_err, _vars, context) => {
    queryClient.setQueryData(['homework'], context?.previous)
    setInlineError(id, 'Не удалось сохранить')
  },
  onSettled: () => {
    queryClient.invalidateQueries({ queryKey: ['homework'] })
  },
})
```

[ASSUMED — standard TanStack Query optimistic pattern; structure is correct per v5 docs]

### Pattern 3: HATEOAS Response Unwrapping

The attendance backend returns HATEOAS `EntityModel` and `CollectionModel`. Based on existing schedule API pattern (data._embedded), the response shapes are:

- `GET /attendance/reports/student/stats` → `EntityModel<StudentStatsResponse>`: content fields at root (Spring HATEOAS Jackson serialization puts fields at root level, `_links` alongside)
- `GET /attendance/reports/student/records` → `CollectionModel<EntityModel<AttendanceRecordEntry>>`: items at `data._embedded.attendanceRecordEntryList` (key name follows Spring HATEOAS convention: camelCase class name + "List")
- `GET /academic/homeworks` → `PagedModel<EntityModel<HomeworkResponse>>`: items at `data._embedded.homeworkResponseList`, `data.page` for pagination

[ASSUMED — follows Spring HATEOAS naming convention; verify embedded key name at runtime]

### Pattern 4: Resolving semesterId

The homework API requires both `groupId` and `semesterId`. `groupId` is available from `useAuth()` → `user.groupId`. `semesterId` is NOT in the JWT payload — no existing mechanism in the frontend.

**Resolution strategy (Claude's Discretion — recommended approach):**
Fetch active semester via `GET /academic/semesters` and filter `active === true`. Cache 24hr (semesters change infrequently). Create shared hook `useActiveSemester`:

```typescript
// Recommended implementation
export function useActiveSemester() {
  return useQuery<number | null>({
    queryKey: ['semester', 'active'],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/semesters', { params: { size: 50 } })
      const semesters = data._embedded?.semesterResponseList ?? []
      const active = semesters.find((s: { active: boolean; id: number }) => s.active)
      return active?.id ?? null
    },
    staleTime: 24 * 60 * 60 * 1000,
  })
}
```

[VERIFIED: SemesterApi.java — `GET /academic/semesters` exists; `SemesterResponse.active` boolean exists]

### Pattern 5: Motion Stagger (from SchedulePage)

```typescript
// Source: frontends/pwa/src/features/schedule/SchedulePage.tsx (verified)
<motion.div
  className="flex flex-col gap-3"
  initial="hidden"
  animate="visible"
  variants={{
    visible: { transition: { staggerChildren: 0.04 } },
    hidden: {},
  }}
>
  {items.map((item) => (
    <motion.div
      key={item.id}
      variants={{
        hidden: { opacity: 0 },
        visible: { opacity: 1 },
      }}
    >
      <ItemComponent item={item} />
    </motion.div>
  ))}
</motion.div>
```

[VERIFIED: frontends/pwa/src/features/schedule/SchedulePage.tsx]

### Anti-Patterns to Avoid

- **Importing between unrelated features:** `report/` never imports from `checkin/` (CLAUDE.md pattern). Similarly, `attendance/` and `homework/` are independent features — no cross-imports.
- **Using localStorage for HW completion:** D-01 explicitly overrides UI-SPEC — server-side only via `/complete` endpoints.
- **Assuming a default threshold:** D-06 — when resolve returns 404 or null `minPercentage`, hide all red zone UI entirely.
- **Mutating embedded collection key without verification:** The key `attendanceRecordEntryList` follows Spring HATEOAS convention but must be verified at integration test time.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Server state caching + stale-while-revalidate | Custom fetch + cache | TanStack Query `useQuery` with `staleTime` | Race conditions, deduplication, GC |
| Optimistic update with rollback | Manual state tracking | TanStack Query `useMutation` `onMutate`/`onError` | Handles concurrent mutations, proper cache management |
| Date formatting (Russian locale) | Custom `Date.toLocaleDateString` | `Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: 'short' })` | Browser-native, no dependency |
| Sort undone-first + deadline | Custom multi-key sort | Standard JS `sort` with comparator | Simple enough to inline |
| Animated list reorder | Manual DOM manipulation | Motion `layout` prop on each item | Handles DOM measurement automatically |

[VERIFIED: codebase — TanStack Query already used; Intl API available in all target browsers]

---

## Critical API Facts (Verified)

### Attendance Stats Endpoint

```
GET /attendance/reports/student/stats
Auth: Bearer JWT (student — extracts userId from token server-side)
Response: EntityModel<StudentStatsResponse>
  .subjects: List<SubjectStats>
    - subjectId: Long
    - subjectName: String      ← resolved via gRPC (already populated, no extra call)
    - total: int
    - attended: int
    - absent: int
    - excused: int
    - percentage: double       ← 0.0–100.0
  .overall: OverallStats
```

[VERIFIED: attendance-api-contract/ReportApi.java, StudentStatsResponse.java, SubjectStats.java]

**Key insight:** `subjectName` is already resolved server-side. No additional academic API call needed for stats display.

**No `free_attendance` count in SubjectStats:** The DTO has `attended`, `absent`, `excused` — no separate `freeAttendance` field. The UI-SPEC shows `"сп: 0"` but the backend `SubjectStats` has no such field. The plan must show `б/н/у` only in subject stat rows (3 counts), not `сп`.

### Attendance Records Endpoint

```
GET /attendance/reports/student/records?subjectId={id}   (subjectId optional)
Auth: Bearer JWT
Response: CollectionModel<EntityModel<AttendanceRecordEntry>>
  each entry:
    - lessonId: Long
    - subjectId: Long
    - lessonDate: String       ← format TBD (likely "YYYY-MM-DD" — verify at runtime)
    - lessonNumber: Integer
    - status: String           ← "present" | "absent" | "excused" | "free_attendance"
    - symbol: String           ← "б" | "н" | "у" | "сп"  (pre-computed server-side)
    - source: String           ← "geo" | "manual" | "auto"
```

[VERIFIED: AttendanceRecordEntry.java]

**Key insight:** `symbol` is pre-computed server-side. Can pass directly to StatusBadge or display directly. `lessonDate` is a String — parse to Date for formatting.

### Homework List Endpoint

```
GET /academic/homeworks?groupId={id}&semesterId={id}
Auth: Bearer JWT
Response: PagedModel<EntityModel<HomeworkResponse>>
  each homework:
    - id: Long
    - title: String
    - description: String (nullable)
    - link: String (nullable)
    - subjectId: Long          ← need subject name lookup OR not shown in UI-SPEC
    - groupId: Long
    - semesterId: Long
    - publishedBy: Long
    - completed: boolean       ← per-student, from HomeworkCompletion table
    - createdAt: OffsetDateTime
```

[VERIFIED: HomeworkResponse.java, HomeworkApi.java]

**Key insight:** `HomeworkResponse` has NO `deadline` field and NO `subjectName` field. The UI-SPEC shows "До: {дд ммм}" deadline and subject name in HomeworkItem. This is a gap — the backend DTO does not have a deadline. The plan must handle this discrepancy.

**Deadline gap:** `HomeworkResponse` fields are: `id, title, description, link, subjectId, groupId, semesterId, publishedBy, completed, createdAt`. No `deadline` / `dueDate` / `dueTo` field. The UI-SPEC deadline display and overdue logic cannot be implemented as specified. The plan should either: (a) omit deadline display, (b) use `createdAt` as fallback label, or (c) flag for planner decision. Recommend (a): omit deadline row in HomeworkItem since it's not in the DTO.

**Subject name in HomeworkItem:** `subjectId` is present but name requires a lookup. The plan should use the existing `useSubjectName(subjectId)` hook (already in `features/schedule/api.ts`) which caches 24hr per subject.

### Threshold Endpoint

```
GET /academic/thresholds/resolve?groupId={id}&subjectId={id}   (both optional)
Auth: Bearer JWT
Response: EntityModel<ResolvedThresholdResponse>
  - minPercentage: int     ← the effective threshold percentage
  - level: String          ← "subject" | "group" | "global"
  - sourceId: Long
```

[VERIFIED: ThresholdApi.java, ResolvedThresholdResponse.java]

**Decision D-05 says call with `groupId` only (no `subjectId`).** This gives group-level or global threshold — one value for all subjects. Per D-06, 404 response means hide all indicators.

### Homework Toggle Endpoints

```
POST /academic/homeworks/{id}/complete   → 204 (mark done)
DELETE /academic/homeworks/{id}/complete → 204 (unmark)
409 from POST = already completed (idempotency guard server-side)
```

[VERIFIED: HomeworkApi.java]

---

## Common Pitfalls

### Pitfall 1: HomeworkResponse Has No Deadline Field
**What goes wrong:** Planner tries to implement deadline display and "Просрочено" overdue label from UI-SPEC.
**Why it happens:** UI-SPEC was created before contract verification; CONTEXT.md notes UI-SPEC is an input to planning.
**How to avoid:** Plan must omit deadline display. `HomeworkItem` shows title + subject name only. Overdue logic has no data source.
**Warning signs:** Compiler error if TypeScript type is generated from actual DTO.

### Pitfall 2: Spring HATEOAS Embedded Collection Key Name
**What goes wrong:** `data._embedded?.attendanceRecordEntryList` returns undefined because the actual key differs.
**Why it happens:** Spring HATEOAS generates the key from the class name (camelCase + "List"). For `AttendanceRecordEntry` it would be `attendanceRecordEntryList`; for `HomeworkResponse` it would be `homeworkResponseList`.
**How to avoid:** Log the raw response in a dev environment test to confirm the key. Add a fallback: `data._embedded?.[Object.keys(data._embedded ?? {})[0]] ?? []`.
**Warning signs:** Empty list display despite backend returning data.

### Pitfall 3: semesterId Unavailable — Homework Query Disabled
**What goes wrong:** `useHomework` is called before `useActiveSemester` resolves, passing `semesterId: undefined`, making the query enabled when it shouldn't be.
**Why it happens:** Both queries are async; rendering happens before the semester resolves.
**How to avoid:** Use `enabled: !!groupId && !!semesterId` in the homework query. Show `LoadingSpinner` while semester is loading.
**Warning signs:** Network request to `/academic/homeworks?groupId=X&semesterId=undefined`.

### Pitfall 4: Optimistic Update on Paginated/Embedded Response
**What goes wrong:** `queryClient.setQueryData(['homework'])` fails to update because the cached structure is `PagedModel` with `_embedded.homeworkResponseList`, not a flat array.
**Why it happens:** Optimistic update must know the cache structure to patch it correctly.
**How to avoid:** Normalize homeworks to a flat array in the queryFn before caching, so `setQueryData` works on a `HomeworkResponse[]`.
**Warning signs:** Checkbox reverts immediately despite successful mutation.

### Pitfall 5: BottomNav /home Route Removal Breaks Default Redirect
**What goes wrong:** The `index: true` route in main.tsx redirects to `/home` but `/home` is being removed.
**Why it happens:** D-03 removes the Главная tab; D-04 changes default to `/schedule`.
**How to avoid:** Change `<Navigate to="/home" replace />` to `<Navigate to="/schedule" replace />` in the same task as removing the `/home` route.
**Warning signs:** Blank screen or 404 on app load after login.

### Pitfall 6: AttendanceRecordsPage Route Needs Subject Name from Parent
**What goes wrong:** `/stats/:subjectId` route has no subject name to display as page heading — the records API only returns `subjectId` per entry, not the subject name.
**Why it happens:** `AttendanceRecordEntry` has no `subjectName` field.
**How to avoid:** Pass subject name as React Router route state from `SubjectStatRow` onClick (`navigate('/stats/123', { state: { subjectName: 'Математика' } })`). Alternatively, use `useSubjectName(subjectId)` hook in the records page.
**Warning signs:** Page heading shows undefined or the raw ID.

---

## Code Examples

### Verified: Auth context provides groupId

```typescript
// Source: frontends/pwa/src/features/auth/AuthProvider.tsx (verified)
const { user } = useAuth()
const groupId = user?.groupId ?? 0   // groupId is in JWT payload, parsed in AuthProvider
```

### Verified: BottomNav current structure (requires replacement)

```typescript
// Source: frontends/pwa/src/shared/components/BottomNav.tsx (verified)
// Current 4 tabs — replace with 5:
const tabs = [
  { to: '/home', icon: House, label: 'Главная' },       // ← REPLACE with /stats + ChartBar
  { to: '/schedule', icon: Calendar, label: 'Расписание' },
  { to: '/checkin', icon: Fingerprint, label: 'Отметка' },
  // INSERT { to: '/homework', icon: ClipboardText, label: 'Задания' },
  { to: '/profile', icon: User, label: 'Профиль' },
]
```

### Verified: Route registration pattern (lazy import)

```typescript
// Source: frontends/pwa/src/main.tsx (verified)
const AttendanceStatsPage = lazy(() =>
  import('./features/attendance/AttendanceStatsPage').then(m => ({ default: m.AttendanceStatsPage }))
)
// Add: /stats, /stats/:subjectId, /homework routes as children of AppShell
// Change index redirect from /home to /schedule
```

### Verified: HATEOAS embedded key pattern from schedule

```typescript
// Source: frontends/pwa/src/features/schedule/api.ts (verified)
const { data } = await apiClient.get(...)
return data._embedded?.lessonResponseList ?? []
// Follow same pattern for attendanceRecordEntryList, homeworkResponseList
```

### Verified: Existing StatusBadge accepts attendance status strings

```typescript
// Source: frontends/pwa/src/features/schedule/StatusBadge.tsx (verified)
// statusConfig includes: present, absent, excused, free_attendance
// AttendanceRecordEntry.status values match these keys exactly
<StatusBadge status={record.status as AttendanceStatus} />
```

### Verified: Russian month abbreviation pattern

```typescript
// Source: frontends/pwa/src/features/schedule/SchedulePage.tsx (verified)
const MONTH_ABBREV = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек']
// Use same pattern for lessonDate formatting in AttendanceRecordRow
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| localStorage-only HW completion (UI-SPEC initial) | Server-side via HomeworkCompletion table (D-01) | Phase 32 discuss | Data persists across devices, not just browser |
| 4-tab BottomNav (current) | 5-tab BottomNav (D-03) | Phase 32 | Remove /home route, add /stats + /homework |
| Default landing /home | Default landing /schedule (D-04) | Phase 32 | Cleaner UX — most-used screen first |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `data._embedded?.attendanceRecordEntryList` is the correct embedded key for `CollectionModel<EntityModel<AttendanceRecordEntry>>` | API Facts | Empty list display; fix by checking actual key name |
| A2 | `data._embedded?.homeworkResponseList` is the correct embedded key for `PagedModel<EntityModel<HomeworkResponse>>` | API Facts | Empty list display |
| A3 | `lessonDate` in `AttendanceRecordEntry` is formatted as `"YYYY-MM-DD"` string | API Facts | Date parsing error if format differs (e.g. ISO datetime) |
| A4 | TanStack Query optimistic mutation pattern (`onMutate`/`onError`/`onSettled`) behaves as documented for cache manipulation | Architecture Patterns | Rollback may not work; verify with integration test |
| A5 | `GET /academic/semesters?size=50` returns all semesters including the active one without pagination issues | Architecture Patterns | Could fail if many semesters; use `size=100` as buffer |

---

## Open Questions (RESOLVED)

1. **HomeworkResponse has no deadline field**
   - What we know: `HomeworkResponse` has `id, title, description, link, subjectId, groupId, semesterId, publishedBy, completed, createdAt` — no deadline.
   - What's unclear: Was a deadline field planned but not yet added to the backend DTO? Or is deadline intentionally absent?
   - Recommendation: Plan HomeworkItem without deadline display. Omit "До: {дд ммм}" and "Просрочено" copy from the UI-SPEC. This is a discrete descoping, not a blocker.

2. **SubjectStats has no free_attendance count**
   - What we know: `SubjectStats` has `attended`, `absent`, `excused` — no `freeAttendance`.
   - What's unclear: Is `free_attendance` counted in `attended` or tracked separately?
   - Recommendation: Display `б: {attended} / н: {absent} / у: {excused}` (3 fields only). Drop `сп: 0` from the UI-SPEC row 2.

---

## Environment Availability

Step 2.6: SKIPPED — this is a pure frontend code phase with no new external tool dependencies. All required tools (Node, npm, Vite, Vitest) are already in use by the project.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Vitest 3.x + @testing-library/react 16.x |
| Config file | `frontends/pwa/vitest.config.ts` |
| Quick run command | `cd frontends/pwa && npm test` |
| Full suite command | `cd frontends/pwa && npm test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ATT-01 | AttendanceStatsPage renders subject list from mocked API | unit | `cd frontends/pwa && npm test` | ❌ Wave 0 |
| ATT-02 | SubjectStatRow shows red zone indicator when percentage < threshold | unit | `cd frontends/pwa && npm test` | ❌ Wave 0 |
| ATT-02 | SubjectStatRow hides red zone indicator when threshold is null | unit | `cd frontends/pwa && npm test` | ❌ Wave 0 |
| ATT-03 | AttendanceRecordsPage renders records with StatusBadge | unit | `cd frontends/pwa && npm test` | ❌ Wave 0 |
| HW-01 | HomeworkPage renders homework list; shows empty state when empty | unit | `cd frontends/pwa && npm test` | ❌ Wave 0 |
| HW-02 | HomeworkItem checkbox toggle fires mutation; reverts on error | unit | `cd frontends/pwa && npm test` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `cd frontends/pwa && npm test`
- **Per wave merge:** `cd frontends/pwa && npm test`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `frontends/pwa/src/features/attendance/__tests__/AttendanceStatsPage.test.tsx` — covers ATT-01, ATT-02
- [ ] `frontends/pwa/src/features/homework/__tests__/HomeworkItem.test.tsx` — covers HW-01, HW-02

*(Existing test infrastructure — vitest, jsdom, @testing-library/react, setup.ts — fully covers all new tests. No framework installation needed.)*

---

## Security Domain

Security enforcement applies. This phase adds no new authentication patterns, no new sensitive data handling, no new server endpoints. All API calls flow through the existing `apiClient` Axios instance with JWT bearer token interceptor already implemented.

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No — reuses existing JWT flow | Existing Axios interceptor |
| V3 Session Management | No — no session changes | Existing refresh logic |
| V4 Access Control | No | Backend enforces STUDENT role |
| V5 Input Validation | No — no user text input in this phase | N/A |
| V6 Cryptography | No | N/A |

No new threat patterns introduced. Read-only queries and simple boolean toggle — no injection surface.

---

## Sources

### Primary (HIGH confidence)
- `frontends/pwa/src/features/schedule/api.ts` — TanStack Query hook pattern, staleTime, refetchOnReconnect
- `frontends/pwa/src/features/schedule/SchedulePage.tsx` — Motion stagger pattern, month abbreviation array
- `frontends/pwa/src/features/schedule/StatusBadge.tsx` — Attendance status config (present/absent/excused/free_attendance)
- `frontends/pwa/src/features/schedule/types.ts` — AttendanceStatus type definition
- `frontends/pwa/src/shared/components/BottomNav.tsx` — Current tab structure
- `frontends/pwa/src/main.tsx` — Router configuration, lazy import pattern
- `frontends/pwa/src/features/auth/AuthProvider.tsx` — `user.groupId` availability
- `services/attendance-service/attendance-api-contract/.../ReportApi.java` — Verified endpoint signatures
- `services/attendance-service/attendance-api-contract/.../StudentStatsResponse.java` — Response shape
- `services/attendance-service/attendance-api-contract/.../SubjectStats.java` — Per-subject fields (NO free_attendance)
- `services/attendance-service/attendance-api-contract/.../AttendanceRecordEntry.java` — Record fields
- `services/academic-service/academic-api-contract/.../HomeworkApi.java` — Homework endpoints
- `services/academic-service/academic-api-contract/.../HomeworkResponse.java` — HomeworkResponse fields (NO deadline)
- `services/academic-service/academic-api-contract/.../ThresholdApi.java` — resolveThreshold endpoint
- `services/academic-service/academic-api-contract/.../ResolvedThresholdResponse.java` — minPercentage field
- `services/academic-service/academic-api-contract/.../SemesterApi.java` — GET /academic/semesters
- `services/academic-service/academic-api-contract/.../SemesterResponse.java` — active boolean field
- `frontends/pwa/vitest.config.ts` — Test framework configuration
- `.planning/phases/32-stats-homework/32-CONTEXT.md` — All locked decisions (D-01 through D-08)
- `.planning/phases/32-stats-homework/32-UI-SPEC.md` — Visual contract

### Secondary (MEDIUM confidence)
- Spring HATEOAS embedded key naming convention (`{camelCaseClass}List`) — consistent with `lessonResponseList` key observed in existing code

### Tertiary (LOW confidence — see Assumptions Log)
- TanStack Query v5 optimistic mutation cache manipulation shape — standard documented pattern but not verified against a running instance [A4]

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all packages verified in codebase
- API contracts: HIGH — all DTOs read directly from source
- Architecture patterns: HIGH — verified against existing SchedulePage/CheckIn features
- Pitfalls: HIGH — derived directly from DTO shape mismatches (deadline, free_attendance) found during research
- Optimistic mutation: MEDIUM — pattern is standard but specific cache key structure not integration-tested

**Research date:** 2026-04-06
**Valid until:** 2026-05-06 (stable stack, no fast-moving dependencies)
