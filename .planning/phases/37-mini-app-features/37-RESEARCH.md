# Phase 37: Mini App Features — Research

**Researched:** 2026-04-07
**Domain:** React Telegram Mini App feature implementation — schedule view, geo check-in with MainButton + haptic feedback, attendance stats with red zone, homework list, BackButton navigation
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TMA-06 | Today's schedule view with lessons, times, rooms, status badges | PWA `SchedulePage.tsx` + `LessonCard.tsx` patterns directly portable; same API `GET /schedule/groups/{groupId}/lessons` |
| TMA-07 | Geo check-in via MainButton with GPS capture and haptic feedback | `mainButton.setParams()` + `mainButton.onClick()` + `hapticFeedback.notificationOccurred('success')` from `@telegram-apps/sdk-react`; GPS via `navigator.geolocation.getCurrentPosition` (same as PWA) |
| TMA-08 | Attendance stats per subject with red zone indicators | `GET /attendance/reports/student/stats` returns `StudentStatsResponse` with `subjects[].percentage` and `overall.percentage`; red zone threshold = percentage < threshold |
| TMA-09 | Homework list with completion toggle | `GET /academic/homeworks?groupId=&semesterId=` list; `POST /academic/homeworks/{id}/complete` and `DELETE /academic/homeworks/{id}/complete` toggle; optimistic update via TanStack Query |
| TMA-10 | Telegram theme support (dark/light mode) | `TelegramThemeProvider` already built in Phase 36; Tailwind CSS vars already wired — no new work beyond using them consistently in Phase 37 components |
| TMA-11 | BackButton navigation integration | `backButton.show()` / `backButton.hide()` + `backButton.onClick()` from `@telegram-apps/sdk-react`; register in page-level `useEffect`, unsubscribe on unmount |
</phase_requirements>

---

## Summary

Phase 37 fills in the feature pages of the Mini App scaffold established in Phase 36. The scaffold already provides: auth, routing (BrowserRouter + Routes), TelegramThemeProvider, QueryClient, Axios with 401 re-auth interceptor, and vitest test infrastructure. Phase 37 adds four feature pages (Schedule, CheckIn, Stats, Homework) and wires BackButton navigation.

Most API patterns are already proven in the PWA — `SchedulePage.tsx`, `LessonCard.tsx`, `StatusBadge.tsx`, `CheckInButton.tsx`, `CheckInScreen.tsx`, `WeekDayTabs.tsx` can all be copied and adapted. The Mini App version removes STOMP (deferred per STATE.md) and adds MainButton + haptic feedback for check-in as the key differentiator.

The homework feature requires knowing the current semester ID to query `/academic/homeworks`. No dedicated "current semester" endpoint exists — the approach is to call `GET /academic/semesters` (paginated) and find the entry with `active: true`. This should be cached long-term (TanStack Query `staleTime: 24h`).

**Primary recommendation:** Build four feature pages in `features/schedule/`, `features/checkin/`, `features/stats/`, `features/homework/`, following the PWA feature-module pattern. Replace PWA's floating CheckIn button with Telegram MainButton for check-in action. Wire BackButton in a `useBackButton` custom hook that calls `navigate(-1)`.

---

## Standard Stack

Phase 37 uses the stack already installed by Phase 36 — no new dependencies required.

### Already Installed (from Phase 36)
[VERIFIED: `frontends/mini-app/package.json`]

| Library | Version | Used For |
|---------|---------|----------|
| react + react-dom | ^19.1.0 | UI framework |
| react-router | ^7.14.0 | Client-side routing, `useNavigate` |
| @tanstack/react-query | ^5.96.2 | Server state — schedule, stats, homework |
| axios | ^1.14.0 | HTTP client with auth interceptors |
| @telegram-apps/sdk-react | ^3.3.9 | `mainButton`, `backButton`, `hapticFeedback`, `useSignal` |
| @telegram-apps/sdk | ^3.11.8 | Core SDK (peer dep) |
| tailwindcss | ^4.1.4 | Styling — Telegram theme via CSS vars |
| motion | ^12.38.0 | Page transitions, list animations |
| @phosphor-icons/react | ^2.1.10 | Icons — bold/fill weight for mobile |
| vitest + @testing-library/react | ^3.1.3 / ^16.3.0 | Tests |

**No new npm installs needed for Phase 37.**

---

## Architecture Patterns

### Feature Module Structure (follow PWA convention)

```
frontends/mini-app/src/
├── features/
│   ├── auth/               ← Phase 36 (complete)
│   ├── schedule/           ← Phase 37 (new)
│   │   ├── SchedulePage.tsx
│   │   ├── LessonCard.tsx
│   │   ├── StatusBadge.tsx
│   │   ├── WeekDayTabs.tsx
│   │   ├── api.ts
│   │   ├── types.ts
│   │   └── __tests__/
│   ├── checkin/            ← Phase 37 (new)
│   │   ├── CheckInPage.tsx     (selected lesson + MainButton trigger)
│   │   ├── api.ts
│   │   ├── types.ts
│   │   └── __tests__/
│   ├── stats/              ← Phase 37 (new)
│   │   ├── StatsPage.tsx
│   │   ├── SubjectStatsCard.tsx
│   │   ├── api.ts
│   │   ├── types.ts
│   │   └── __tests__/
│   └── homework/           ← Phase 37 (new)
│       ├── HomeworkPage.tsx
│       ├── HomeworkItem.tsx
│       ├── api.ts
│       ├── types.ts
│       └── __tests__/
├── shared/
│   ├── components/         ← Phase 36 (DevModeBanner, etc.)
│   ├── hooks/
│   │   ├── useBackButton.ts    ← Phase 37 (new) — BackButton hook
│   │   └── useMainButton.ts    ← Phase 37 (new) — MainButton hook
│   ├── lib/                ← Phase 36 (axios, queryClient, mockWebApp)
│   └── providers/          ← Phase 36 (TelegramThemeProvider)
└── App.tsx                 ← Phase 37: replace placeholder routes with real pages
```

### Pattern 1: BackButton Integration (TMA-11)

**What:** Show Telegram native BackButton on all non-root pages; hide on root page.
**When to use:** Every page that is NOT the default landing tab.

```typescript
// Source: ohld/tma-llms-txt (SDK 3.x pattern) + Phase 36 sdk imports
// shared/hooks/useBackButton.ts
import { useEffect } from 'react'
import { backButton } from '@telegram-apps/sdk-react'
import { useNavigate } from 'react-router'

export function useBackButton() {
  const navigate = useNavigate()

  useEffect(() => {
    if (!backButton.show.isAvailable()) return
    backButton.show()

    const unsubscribe = backButton.onClick(() => {
      navigate(-1)
    })

    return () => {
      unsubscribe()
      if (backButton.hide.isAvailable()) backButton.hide()
    }
  }, [navigate])
}
```

[VERIFIED: `@telegram-apps/sdk-react` exports `backButton` from `@telegram-apps/sdk`. SDK 3.x pattern confirmed from ohld/tma-llms-txt developer guide and Phase 36 imports `backButton` is available.]

### Pattern 2: MainButton for Check-in (TMA-07)

**What:** Use the native Telegram MainButton (bottom of Telegram UI) as the check-in trigger. The button is configured imperatively via `mainButton.setParams()`.
**When to use:** `CheckInPage` — when a ACTIVE lesson exists, enable the button; otherwise show as disabled.

```typescript
// Source: ohld/tma-llms-txt SDK 3.x reference
// shared/hooks/useMainButton.ts
import { useEffect } from 'react'
import { mainButton } from '@telegram-apps/sdk-react'

interface UseMainButtonOptions {
  text: string
  isEnabled: boolean
  isVisible: boolean
  onClick: () => void
}

export function useMainButton({ text, isEnabled, isVisible, onClick }: UseMainButtonOptions) {
  useEffect(() => {
    if (!mainButton.setParams.isAvailable()) return

    mainButton.setParams({ text, isEnabled, isVisible })
    const unsubscribe = mainButton.onClick(onClick)

    return () => {
      unsubscribe()
      // hide button when component unmounts
      mainButton.setParams({ isVisible: false })
    }
  }, [text, isEnabled, isVisible, onClick])
}
```

### Pattern 3: Haptic Feedback on Check-in (TMA-07)

**What:** Fire haptic feedback after check-in success/failure to provide native mobile feel.

```typescript
// Source: ohld/tma-llms-txt SDK 3.x reference
import { hapticFeedback } from '@telegram-apps/sdk-react'

// On check-in success:
if (hapticFeedback.notificationOccurred.isAvailable()) {
  hapticFeedback.notificationOccurred('success')
}

// On check-in failure:
if (hapticFeedback.notificationOccurred.isAvailable()) {
  hapticFeedback.notificationOccurred('error')
}

// On button tap (selection feedback):
if (hapticFeedback.impactOccurred.isAvailable()) {
  hapticFeedback.impactOccurred('medium')
}
```

[VERIFIED: `hapticFeedback.impactOccurred(style)` and `hapticFeedback.notificationOccurred(type)` confirmed from SDK docs. Always check `.isAvailable()` before calling — SDK 3.x pattern uses availability checks.]

[ASSUMED: The `.isAvailable()` guard pattern on `hapticFeedback` methods. Phase 36 uses this pattern for `viewport.mount.isAvailable()` and `mainButton.setParams.isAvailable()` — consistent with SDK 3.x approach.]

### Pattern 4: Schedule API (TMA-06)

**What:** Fetch today's lessons for the authenticated student's group.
**API:** `GET /api/schedule/groups/{groupId}/lessons?dateFrom=YYYY-MM-DD&dateTo=YYYY-MM-DD&size=100`
**Returns:** HAL response with `_embedded.lessonResponseList` array.

[VERIFIED: `frontends/pwa/src/features/schedule/api.ts` — proven working pattern with this endpoint.]

For the Mini App, simplify to **today-only view** (not full week navigation) to suit the Telegram UI pattern. Use `dateFrom=today&dateTo=today`. The PWA's week navigation adds complexity that doesn't fit the mini-app UX.

```typescript
// features/schedule/api.ts
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'
import type { LessonResponse } from './types'

export function useTodaySchedule(groupId: number) {
  const today = new Date().toISOString().split('T')[0] // 'YYYY-MM-DD'
  return useQuery<LessonResponse[]>({
    queryKey: ['schedule', groupId, today],
    queryFn: async () => {
      const { data } = await apiClient.get(`/schedule/groups/${groupId}/lessons`, {
        params: { dateFrom: today, dateTo: today, size: 50 },
      })
      return (data._embedded?.lessonResponseList ?? [])
        .sort((a: LessonResponse, b: LessonResponse) => a.lessonNumber - b.lessonNumber)
    },
    staleTime: 10 * 60 * 1000, // 10 min
    enabled: !!groupId,
  })
}
```

### Pattern 5: Attendance Stats API (TMA-08)

**What:** Fetch per-subject attendance stats for the current student.
**API:** `GET /api/attendance/reports/student/stats` — no query params, JWT identifies the student.
**Returns:** `StudentStatsResponse` with `subjects[]` and `overall`.

[VERIFIED: `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ReportApi.java` — `@GetMapping("/student/stats")` with no path variables.]

```typescript
// features/stats/types.ts
export interface SubjectStats {
  subjectId: number
  subjectName: string
  total: number
  attended: number
  absent: number
  excused: number
  percentage: number
}

export interface OverallStats {
  total: number
  attended: number
  absent: number
  excused: number
  percentage: number
}

export interface StudentStatsResponse {
  subjects: SubjectStats[]
  overall: OverallStats
}
```

Red zone threshold: compare `percentage` against a configurable threshold. Default 60% (CLAUDE.md: "Порог красной зоны: глобальный (admin) → группа (headman) → предмет (headman)"). For Phase 37, use the backend-provided threshold if returned, or hardcode 60% as default since the phase is read-only display.

[ASSUMED: The backend `StudentStatsResponse` does not currently return the threshold value in the stats endpoint — the threshold logic is in Phase 37 frontend only (hardcoded 60% default). If the backend returns a threshold field in future, the frontend should use it.]

### Pattern 6: Homework API (TMA-09)

**What:** List group homeworks for current semester; toggle completion per student.
**Requires:** Current semester ID — fetched from `GET /api/academic/semesters` filtering by `active: true`.
**List API:** `GET /api/academic/homeworks?groupId={groupId}&semesterId={semesterId}&size=50`
**Complete:** `POST /api/academic/homeworks/{id}/complete` → 204
**Uncomplete:** `DELETE /api/academic/homeworks/{id}/complete` → 204

[VERIFIED: `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/HomeworkApi.java` and `SemesterApi.java`]

No dedicated "GET /semesters/current" endpoint exists — must list all and find `active: true`.

```typescript
// features/homework/api.ts
export function useActiveSemester() {
  return useQuery({
    queryKey: ['semester', 'active'],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/semesters', {
        params: { size: 20 },
      })
      const semesters: SemesterResponse[] = data._embedded?.semesterResponseList ?? []
      return semesters.find(s => s.active) ?? null
    },
    staleTime: 24 * 60 * 60 * 1000, // 24h — semesters change rarely
  })
}

export function useHomeworkList(groupId: number, semesterId: number | undefined) {
  return useQuery({
    queryKey: ['homeworks', groupId, semesterId],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/homeworks', {
        params: { groupId, semesterId, size: 50 },
      })
      return data._embedded?.homeworkResponseList ?? []
    },
    staleTime: 5 * 60 * 1000,
    enabled: !!groupId && !!semesterId,
  })
}
```

**Optimistic completion toggle** — same pattern as PWA (invalidate query on settle, optimistically toggle `completed` flag):

```typescript
export function useToggleHomework(groupId: number, semesterId: number | undefined) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, completed }: { id: number; completed: boolean }) => {
      if (completed) {
        await apiClient.delete(`/academic/homeworks/${id}/complete`)
      } else {
        await apiClient.post(`/academic/homeworks/${id}/complete`)
      }
    },
    onMutate: async ({ id, completed }) => {
      await queryClient.cancelQueries({ queryKey: ['homeworks', groupId, semesterId] })
      const previous = queryClient.getQueryData(['homeworks', groupId, semesterId])
      queryClient.setQueryData(['homeworks', groupId, semesterId], (old: HomeworkItem[]) =>
        old.map(hw => hw.id === id ? { ...hw, completed: !completed } : hw)
      )
      return { previous }
    },
    onError: (_err, _vars, context) => {
      queryClient.setQueryData(['homeworks', groupId, semesterId], context?.previous)
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['homeworks', groupId, semesterId] })
    },
  })
}
```

### Pattern 7: App.tsx Routing Update

Phase 37 replaces the `HomePage` placeholder with real routes:

```tsx
// App.tsx — Phase 37 update
<Routes>
  <Route path="/" element={<SchedulePage />} />
  <Route path="/checkin/:lessonId" element={<CheckInPage />} />
  <Route path="/stats" element={<StatsPage />} />
  <Route path="/homework" element={<HomeworkPage />} />
</Routes>
```

Plus a bottom navigation tab bar (or equivalent) to switch between Schedule / Stats / Homework. BackButton shows on `/checkin/:lessonId` (detail pages), hides on root tabs.

### Anti-Patterns to Avoid

- **Mounting SDK components multiple times:** `mainButton.mount()` is already called by Phase 36 `init()`. Do NOT call `mount()` again in feature components — just call `mainButton.setParams()` and `mainButton.onClick()`.
- **Forgetting to unsubscribe:** `mainButton.onClick()` and `backButton.onClick()` return unsubscribe functions. Always call them in `useEffect` cleanup or memory/event leaks occur.
- **Calling SDK methods without availability check in tests:** The test setup in `src/test/setup.ts` mocks the SDK module. Keep feature components mockable by wrapping SDK calls in `if (x.isAvailable())` guards — tests can inject mock implementations.
- **Fetching homeworks without semesterId:** The `listHomeworks` endpoint requires `semesterId`. Without it, the call returns 400. Always gate the query with `enabled: !!semesterId`.
- **STOMP in Mini App:** STOMP is explicitly deferred (STATE.md). Do NOT import `StompProvider` from PWA. The Mini App check-in shows only local state feedback (no live STOMP attendance count).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Optimistic toggle state | Custom local state | TanStack Query `onMutate` + `setQueryData` | Race condition handling, error rollback built-in |
| GPS acquisition | Custom wrapper | `navigator.geolocation.getCurrentPosition` (direct) | Browser standard — same as PWA `CheckInButton.tsx` |
| Theme color mapping | Custom Telegram theme reader | `TelegramThemeProvider` already built in Phase 36 | Already implemented — just use CSS vars |
| Status badge rendering | Custom logic | Copy `StatusBadge.tsx` from PWA `features/schedule/` | Same status values, same display logic |
| Lesson time formatting | Custom date parser | Copy time formatting from PWA `LessonCard.tsx` | Already handles `HH:mm:ss` → `HH:mm` display |

---

## Common Pitfalls

### Pitfall 1: MainButton Not Visible in Dev Mode
**What goes wrong:** `mainButton.setParams({ isVisible: true })` silently does nothing in browser dev mode.
**Why it happens:** The MainButton is a Telegram WebView UI element — not a DOM element. In dev mode with the SDK mock (`VITE_TMA_DEV=true`), the mock does not render a visual button.
**How to avoid:** For check-in in dev mode, also render a fallback DOM button gated by `import.meta.env.VITE_TMA_DEV === 'true'`. This lets dev testing proceed without Telegram.
**Warning signs:** No button appears on CheckInPage during dev, but check-in logic is wired.

### Pitfall 2: mainButton.onClick Fires Stale Closure
**What goes wrong:** `mainButton.onClick(callback)` captures stale values if re-registered on every render without cleanup.
**Why it happens:** Each call to `mainButton.onClick()` adds a new listener. Without returning the unsubscribe function from `useEffect`, multiple listeners accumulate.
**How to avoid:** Always use `useEffect` with the unsubscribe in cleanup. The `useMainButton` hook pattern above handles this correctly.

### Pitfall 3: Homework Query 400 on Missing semesterId
**What goes wrong:** `GET /academic/homeworks` returns 400 if `semesterId` is not provided.
**Why it happens:** The backend requires both `groupId` and `semesterId` as non-optional params.
**How to avoid:** Use `enabled: !!groupId && !!semesterId` in the query options. Show a loading state while `useActiveSemester` resolves.

### Pitfall 4: Schedule Shows Empty for Teacher Role
**What goes wrong:** The schedule API returns lessons for a `groupId` — teachers don't have `groupId` in the JWT.
**Why it happens:** `AuthUser.groupId` is optional (from Phase 36 types). Teachers have `role: 'TEACHER'`, `groupId: undefined`.
**How to avoid:** Gate the schedule page render on `user?.role === 'STUDENT'`. Show a "Расписание доступно только для студентов" message for teachers.

### Pitfall 5: BackButton Show/Hide Conflicts Between Pages
**What goes wrong:** Navigating between pages leaves BackButton visible when it should be hidden (or vice versa).
**Why it happens:** If `backButton.hide()` is not called in `useEffect` cleanup, the previous page's BackButton state persists.
**How to avoid:** The `useBackButton` hook above always hides in cleanup. Only call `useBackButton()` on pages that should show it (detail pages, not root tabs).

---

## API Reference (Backend Endpoints)

### Schedule
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/api/schedule/groups/{groupId}/lessons` | JWT | params: `dateFrom`, `dateTo`, `size` — returns HAL embedded list |

### Check-in
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/api/attendance/checkin` | JWT | body: `{ lat, lng }` — 201 success, 403/404/409/422/429 errors |

### Attendance Stats
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/api/attendance/reports/student/stats` | JWT | No params — JWT identifies student. Returns `{ subjects, overall }` |

### Homework
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/api/academic/semesters` | JWT | params: `size=20` — find item with `active: true` |
| GET | `/api/academic/homeworks` | JWT | params: `groupId`, `semesterId`, `size=50` — HAL embedded list |
| POST | `/api/academic/homeworks/{id}/complete` | JWT | STUDENT role — 204 on success, 409 if already done |
| DELETE | `/api/academic/homeworks/{id}/complete` | JWT | STUDENT role — 204 on success |

All API responses use HAL+JSON (`_embedded`, `_links`). [VERIFIED: attendance-api-contract, academic-api-contract source files]

---

## Reusable PWA Assets (Copy and Adapt)

These files from `frontends/pwa/src/` can be copied to the mini-app with minimal changes:

| PWA Source | Mini-App Target | Changes Required |
|-----------|-----------------|-----------------|
| `features/schedule/types.ts` | `features/schedule/types.ts` | Copy as-is |
| `features/schedule/api.ts` | `features/schedule/api.ts` | Simplify to today-only; remove `usePrefetchSubjects` or keep |
| `features/schedule/StatusBadge.tsx` | `features/schedule/StatusBadge.tsx` | Copy as-is |
| `features/schedule/LessonCard.tsx` | `features/schedule/LessonCard.tsx` | Remove CheckIn button; add tap → navigate to CheckInPage |
| `features/schedule/WeekDayTabs.tsx` | — | Skip — Mini App uses today-only view |
| `features/checkin/api.ts` | `features/checkin/api.ts` | Copy as-is (same endpoint) |
| `features/checkin/types.ts` | `features/checkin/types.ts` | Copy as-is |

[VERIFIED: All listed files confirmed to exist in `frontends/pwa/src/` directory]

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `window.Telegram.WebApp.MainButton` (legacy direct access) | `mainButton` from `@telegram-apps/sdk-react` 3.x | Type-safe, availability-guarded, works in test environments |
| `window.Telegram.WebApp.HapticFeedback` | `hapticFeedback` from `@telegram-apps/sdk-react` | Same as above |
| STOMP for real-time check-in count | Deferred (STATE.md) | Not applicable in Phase 37 — no real-time needed |
| `useHapticFeedback` from `@vkruglikov/react-telegram-web-app` | `hapticFeedback` from official `@telegram-apps/sdk-react` | Official SDK is already installed — don't use community wrappers |

---

## Project Constraints (from CLAUDE.md)

These apply only where mini-app interacts with backend contracts (no Java in this phase):

| Constraint | Applies in Phase 37? | How |
|------------|----------------------|-----|
| Contract-first (controller implements interface) | No — frontend only | Not applicable |
| No Lombok in contract modules | No — frontend only | Not applicable |
| Enum UPPER_CASE in Java | Indirectly — TypeScript types | `LessonStatus: 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'CANCELLED'` matches Java enums |
| AttendanceStatus lowercase in DB | Indirectly — API responses | Backend returns lowercase strings: `'present'`, `'absent'`, etc. |
| Icons: Phosphor bold/fill for mobile | YES | All new icons must use `weight="bold"` or `weight="fill"` from `@phosphor-icons/react` |
| Motion for animations | YES | React Mini App animations use Motion (`motion/react`) |
| Feature-based folder structure | YES | Each domain in its own `features/` subfolder |
| Avoid Tailwind v3 patterns | YES | This is Tailwind v4 — use `@tailwindcss/vite` plugin, no `tailwind.config.js` |

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 3.1.3 |
| Config file | `frontends/mini-app/vitest.config.ts` |
| Quick run command | `npm run test` (in `frontends/mini-app/`) |
| Full suite command | `npm run test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TMA-06 | `useTodaySchedule` returns sorted lessons for today | unit | `npm run test -- features/schedule` | ❌ Wave 0 |
| TMA-07 | `useMainButton` registers onClick and cleans up on unmount | unit | `npm run test -- features/checkin` | ❌ Wave 0 |
| TMA-07 | GPS capture calls `navigator.geolocation.getCurrentPosition` | unit | `npm run test -- features/checkin` | ❌ Wave 0 |
| TMA-07 | Haptic success/error feedback called after mutation settles | unit | `npm run test -- features/checkin` | ❌ Wave 0 |
| TMA-08 | `useStudentStats` fetches `/attendance/reports/student/stats` | unit | `npm run test -- features/stats` | ❌ Wave 0 |
| TMA-08 | Red zone indicator shown when `percentage < threshold` | unit | `npm run test -- features/stats` | ❌ Wave 0 |
| TMA-09 | `useHomeworkList` queries with groupId + semesterId | unit | `npm run test -- features/homework` | ❌ Wave 0 |
| TMA-09 | Toggle fires POST (uncompleted) or DELETE (completed) | unit | `npm run test -- features/homework` | ❌ Wave 0 |
| TMA-10 | TelegramThemeProvider already tested in Phase 36 | — | already passing | ✅ |
| TMA-11 | `useBackButton` calls `backButton.show()` on mount, `backButton.hide()` on unmount | unit | `npm run test -- shared/hooks` | ❌ Wave 0 |

All SDK calls must be mocked in tests using the global mock in `src/test/setup.ts` (already mocks `@telegram-apps/sdk-react`).

### Wave 0 Gaps

- [ ] `src/features/schedule/__tests__/schedule.test.ts` — covers TMA-06
- [ ] `src/features/checkin/__tests__/checkin.test.ts` — covers TMA-07
- [ ] `src/features/stats/__tests__/stats.test.ts` — covers TMA-08
- [ ] `src/features/homework/__tests__/homework.test.ts` — covers TMA-09
- [ ] `src/shared/hooks/__tests__/useBackButton.test.ts` — covers TMA-11

The existing `src/test/setup.ts` already mocks `@telegram-apps/sdk-react` globally — new feature tests inherit this mock automatically.

---

## Environment Availability

Step 2.6: SKIPPED (no new external dependencies — all deps installed in Phase 36, backend services from existing microservices)

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `mainButton.setParams.isAvailable()` and `backButton.show.isAvailable()` are the correct availability guards in SDK 3.x (not just `mainButton.mount.isAvailable()`) | Code Examples | If SDK 3.x uses different availability guards, the dev fallback pattern needs adjustment; low risk since Phase 36 uses the same `.isAvailable()` pattern on `viewport.mount` |
| A2 | Backend `StudentStatsResponse` does NOT return a red zone threshold — Mini App hardcodes 60% | Pattern 5 / Stats | If backend starts returning threshold, frontend should consume it; low impact as 60% matches business rule default |
| A3 | Today-only schedule view (not week navigation) is appropriate for Mini App UX | Architecture | If users need week navigation in Mini App too, the pattern is more complex; planner should note this as a scope decision |
| A4 | `hapticFeedback.notificationOccurred` is available in `@telegram-apps/sdk-react` 3.3.9 (same as `@telegram-apps/sdk` 3.11.8) | Pattern 3 | SDK re-exports from core package; if naming changed in 3.x, adjust import; risk mitigated by Phase 36 already using same import style |

---

## Open Questions

1. **Bottom navigation pattern**
   - What we know: Phase 37 adds 3 main sections (Schedule, Stats, Homework). Check-in is a sub-page.
   - What's unclear: Should navigation be a bottom tab bar (like PWA) or a list-based home page?
   - Recommendation: Bottom tab bar — aligns with Telegram Mini App UX norms. Three tabs: Расписание / Статистика / ДЗ. Check-in accessed by tapping a lesson.

2. **Red zone threshold value**
   - What we know: CLAUDE.md says "Порог красной зоны: глобальный (admin) → группа (headman) → предмет (headman)". Backend doesn't return threshold in the stats endpoint.
   - What's unclear: Should Phase 37 show a fixed 60% threshold or skip showing threshold until backend supports it?
   - Recommendation: Use hardcoded 60% for Phase 37. Mark as deferred in plan comments.

3. **Check-in page vs. per-lesson check-in**
   - What we know: PWA shows a check-in button inside each LessonCard when `status === 'ACTIVE'`. The TMA phase description says "check in via MainButton."
   - What's unclear: Is the MainButton shown globally on the Schedule page (check in for the currently active lesson), or on a dedicated CheckIn detail page?
   - Recommendation: Navigate to a `CheckInPage` when tapping an ACTIVE lesson card. The MainButton activates on that page. This keeps the MainButton context clear and avoids confusing behavior when multiple lessons could be active.

---

## Sources

### Primary (HIGH confidence)
- `frontends/mini-app/` source files (Phase 36 output) — verified architecture, SDK usage patterns, test setup
- `services/attendance-service/attendance-api-contract/` — endpoint signatures, response DTOs
- `services/academic-service/academic-api-contract/` — HomeworkApi, SemesterApi endpoints
- `frontends/pwa/src/features/` — schedule, checkin patterns proven in production

### Secondary (MEDIUM confidence)
- ohld/tma-llms-txt raw GitHub — SDK 3.x API signatures for mainButton, backButton, hapticFeedback [fetched 2026-04-07]
- docs.telegram-mini-apps.com/platform/haptic-feedback — haptic feedback types confirmed [fetched 2026-04-07]

### Tertiary (LOW confidence — training knowledge)
- `@telegram-apps/sdk-react` 3.x `isAvailable()` guard pattern — inferred from Phase 36 codebase pattern + SDK general design

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — package.json verified in project, no new deps needed
- Architecture: HIGH — follows established PWA patterns, APIs confirmed from Java contracts
- Pitfalls: HIGH — directly derived from verified code patterns and API shapes
- SDK haptic/button patterns: MEDIUM — confirmed from developer guide, `.isAvailable()` guards assumed

**Research date:** 2026-04-07
**Valid until:** 2026-05-07 (30 days — stable stack, no fast-moving dependencies)
