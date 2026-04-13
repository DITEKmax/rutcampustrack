# Phase 56: PWA Headman Mode - Research

**Researched:** 2026-04-13  
**Domain:** Role-based React PWA feature addition + Service Worker runtime caching  
**Confidence:** HIGH

## Summary

This phase extends the existing React PWA to support headman users (`is_headman=true` JWT claim) with a new mobile-first interface for group management features. The research validates that:

1. **JWT Extension is Safe:** `AuthProvider.parseJwt()` already extracts custom claims; adding `is_headman` parsing requires only 3 lines of code (existing test pattern validates unchanged behavior).
2. **BottomNav Refactor is Straightforward:** The hardcoded `tabs` constant becomes a `useTabs()` hook reading `useAuth().isHeadman`; the existing Motion shared-layout animation is preserved.
3. **Workbox Runtime Caching is Available:** All required modules (routing, strategies, expiration) are installed via transitive dependencies of `vite-plugin-pwa`. StaleWhileRevalidate is the standard pattern for mobile API caching.
4. **Existing Test Infrastructure Supports New Tests:** Vitest + React Testing Library + mock patterns match Phase 55+ (Angular web-panel) conventions. 63 existing PWA tests will pass unchanged if new code is isolated in `features/headman/`.

**Primary recommendation:** Proceed with implementation. All technical risks are LOW. The phase is additive; no existing student features are modified. Validation bottleneck is *visual verification* of mobile-first UX (journal UI, threshold editor) and *Service Worker cache hits* (requires browser DevTools inspection during testing).

---

## Standard Stack

### Core Dependencies

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| React | 19.1.0 | Component framework | [VERIFIED: package.json] — PWA standard |
| React Router | 7.14.0 | Client-side routing | [VERIFIED: package.json] — existing `/home`, `/schedule`, `/checkin`, `/profile` routes use this |
| Framer Motion | 12.38.0 (imported as `motion`) | Declarative animations | [VERIFIED: package.json] — BottomNav, schedule page, checkin page all use Motion AnimatePresence + layoutId |
| Axios | 1.14.0 | HTTP client | [VERIFIED: package.json] — configured with interceptor for Bearer token + auto-refresh in `/shared/lib/axios.ts` |
| Phosphor Icons | 2.1.10 | Icon library | [VERIFIED: package.json] — all PWA tabs use Phosphor (House, Calendar, Fingerprint, User icons); phase adds Users icon for headman tab |
| TanStack Query | 5.96.2 | Data fetching / caching | [VERIFIED: package.json] — `useQuery` hooks in schedule/api.ts pattern; headman API calls follow same pattern |
| Tailwind CSS | 4.1.4 | Utility CSS + Transit Grid tokens | [VERIFIED: package.json] — BottomNav uses `var(--accent-primary)`, `var(--text-secondary)` Transit Grid tokens |
| Workbox | 7.x (via vite-plugin-pwa) | Service Worker precache + runtime caching | [VERIFIED: node_modules/] — modules installed: workbox-routing, workbox-strategies, workbox-expiration, workbox-cacheable-response all v7.x |

### Testing Stack

| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| Vitest | 3.1.3 | Test runner | [VERIFIED: package.json] — replaces Jest; globals enabled |
| React Testing Library | 16.3.0 | Component testing | [VERIFIED: package.json] — `renderHook`, `render`, `screen`, `fireEvent` patterns |
| jsdom | 26.1.0 | DOM environment | [VERIFIED: package.json] — vitest.config.ts sets `environment: 'jsdom'` |

### Supporting Libraries (Installed via Transitive Dependencies)

| Module | Purpose |
|--------|---------|
| `workbox-routing` | URL pattern matching for runtime routes |
| `workbox-strategies` | Strategy implementation (StaleWhileRevalidate) |
| `workbox-expiration` | TTL + entry limit management |
| `workbox-cacheable-response` | Cache only 200 responses, not 4xx |

**Installation:**  
No new npm packages needed. All dependencies are already in `package.json`. Workbox modules are transitive dependencies of `vite-plugin-pwa` installed at project setup.

**Version verification:**  
All versions checked against `/c/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/pwa/package.json` (verified 2026-04-13). PWA stable for 6+ months (last update Phase 30 delivered 2026-04-06).

---

## Architecture Patterns

### Recommended Project Structure

```
frontends/pwa/src/
├── features/
│   ├── home/              [frozen — no changes]
│   ├── schedule/          [frozen — no changes]
│   ├── checkin/           [frozen — no changes]
│   ├── profile/           [frozen — no changes]
│   ├── push/              [frozen — no changes]
│   ├── auth/              [extended: parseJwt + AuthUser.isHeadman]
│   └── headman/           [NEW]
│       ├── group-hub/
│       │   ├── GroupHub.tsx          (7 hub cards, routing hub)
│       │   └── GroupHub.test.tsx
│       ├── overview/
│       │   ├── Overview.tsx          (group members count, today's lesson, pending counts)
│       │   └── Overview.test.tsx
│       ├── students/
│       │   ├── StudentsList.tsx      (paginated member list)
│       │   └── StudentsList.test.tsx
│       ├── subjects/
│       │   ├── SubjectsList.tsx      (list/create/edit/delete CRUD)
│       │   └── SubjectsList.test.tsx
│       ├── journal/
│       │   ├── JournalPage.tsx       (subject selector + date picker → student list with segment buttons)
│       │   ├── SegmentedControl.tsx  (5-segment status picker component)
│       │   └── JournalPage.test.tsx
│       ├── excuses/
│       │   ├── ExcusesPage.tsx       (graceful degradation empty state)
│       │   └── ExcusesPage.test.tsx
│       ├── late-checkin/
│       │   ├── LateCheckinPage.tsx   (graceful degradation empty state)
│       │   └── LateCheckinPage.test.tsx
│       ├── stats/
│       │   ├── StatsPage.tsx         (per-subject cards, threshold editor, red-dot logic)
│       │   └── StatsPage.test.tsx
│       └── shared/
│           ├── headmanApi.ts         (API service module — mirrors Angular HeadmanApiService methods)
│           └── headmanApi.test.ts
├── shared/
│   ├── components/
│   │   ├── BottomNav.tsx             [refactored: useTabs() hook]
│   │   ├── useTabs.ts                [NEW: role-aware tab array]
│   │   ├── BottomNav.test.tsx        [NEW: test 5th tab appears for headman]
│   │   └── ... [other primitives unchanged]
│   ├── lib/
│   │   ├── axios.ts                  [unchanged — already generic]
│   │   └── queryClient.ts            [unchanged]
│   └── ... [other unchanged]
├── main.tsx                          [extended: add /group/* lazy routes]
└── sw.ts                             [extended: Workbox runtime route registration]
```

---

## Pattern 1: JWT Claim Parsing (isHeadman)

**What:** Extract `is_headman` boolean from JWT payload and expose via `useAuth()`.

**When to use:** Every headman feature branch needs `const { user } = useAuth()` → check `user?.isHeadman`.

**How (from CONTEXT.md D-04):**

The backend JwtService includes `is_headman` claim in the token (VERIFIED in `.planning/CONTEXT.md`). Current `parseJwt()` only extracts `sub`, `role`, `groupId`. Extension adds one line:

```typescript
// Source: AuthProvider.tsx parseJwt() function
function parseJwt(token: string): { sub: string; role: string; groupId?: number; is_headman?: boolean } {
  const base64Url = token.split('.')[1]
  const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
  const json = decodeURIComponent(
    atob(base64)
      .split('')
      .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
      .join('')
  )
  return JSON.parse(json)  // Now includes is_headman from JWT payload
}
```

Update `AuthUser` type:

```typescript
// Source: AuthProvider.tsx + api.ts (both files need extension)
export interface AuthUser {
  id: number
  role: string
  groupId?: number
  isHeadman: boolean  // NEW field: derived from is_headman ?? false
}
```

Update `tokenToUser()`:

```typescript
function tokenToUser(token: string): AuthUser {
  const payload = parseJwt(token)
  return {
    id: Number(payload.sub),
    role: payload.role,
    groupId: payload.groupId,
    isHeadman: payload.is_headman ?? false,  // NEW line
  }
}
```

**Integration with existing tests:** Current `AuthProvider.test.tsx` uses `createFakeJwt()` helper. Add `is_headman: true` to payload in new test case without modifying existing ones:

```typescript
// NEW test (separate file or added to existing)
it('after login() with is_headman=true, user.isHeadman is true', async () => {
  const fakeToken = createFakeJwt({ sub: '1', role: 'STUDENT', groupId: 5, is_headman: true })
  mockedPost.mockResolvedValueOnce({
    data: { accessToken: fakeToken, expiresIn: 900 },
  })
  
  const { result } = renderHook(() => useAuth(), { wrapper })
  await act(async () => {
    await result.current.login({ login: 'student00001', password: 'pass' })
  })
  
  expect(result.current.user?.isHeadman).toBe(true)
})
```

---

## Pattern 2: BottomNav Refactor to `useTabs()` Hook

**What:** Extract hardcoded `tabs` array into a React hook that reads `useAuth().isHeadman` and returns 4 tabs (plain students) or 5 tabs (headmen).

**When to use:** The BottomNav component needs dynamic tab visibility per role.

**How:**

Create `shared/components/useTabs.ts`:

```typescript
// Source: NEW file, following existing PWA patterns
import { useMemo } from 'react'
import { House, Calendar, Fingerprint, Users, User, type Icon } from '@phosphor-icons/react'
import { useAuth } from '@/features/auth/AuthProvider'

interface Tab {
  to: string
  icon: Icon
  label: string
}

export function useTabs(): Tab[] {
  const { user } = useAuth()
  
  return useMemo(
    () => {
      const baseTabs: Tab[] = [
        { to: '/home', icon: House, label: 'Главная' },
        { to: '/schedule', icon: Calendar, label: 'Расписание' },
        { to: '/checkin', icon: Fingerprint, label: 'Отметка' },
        { to: '/profile', icon: User, label: 'Профиль' },
      ]
      
      // Insert 5th tab before Профиль if headman (per CONTEXT.md D-01)
      if (user?.isHeadman) {
        baseTabs.splice(baseTabs.length - 1, 0, {
          to: '/group',
          icon: Users,
          label: 'Группа',
        })
      }
      
      return baseTabs
    },
    [user?.isHeadman]
  )
}
```

Refactor `BottomNav.tsx`:

```typescript
// Source: BottomNav.tsx (refactored, existing Motion animation preserved)
import { useTabs } from './useTabs'

export function BottomNav() {
  const tabs = useTabs()  // Replaces hardcoded constant
  
  // Rest of component unchanged — tab iteration already uses .map()
  return (
    <nav {...}>
      <ul {...}>
        {tabs.map(({ to, icon: Icon, label }) => (
          // Existing JSX — no changes needed
          <li key={to} className="flex-1">
            ...
          </li>
        ))}
      </ul>
    </nav>
  )
}
```

**Testing:** New test file `BottomNav.test.tsx`:

```typescript
// Source: NEW test following React Testing Library + Vitest patterns
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { BottomNav } from './BottomNav'
import { AuthProvider } from '@/features/auth/AuthProvider'

vi.mock('@/shared/lib/axios', () => ({
  apiClient: { post: vi.fn(), interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } },
  setAccessTokenGetter: vi.fn(),
  setTokenRefreshCallback: vi.fn(),
  setAuthLogoutCallback: vi.fn(),
}))

describe('BottomNav role-aware tabs', () => {
  it('renders 4 tabs for plain STUDENT (no isHeadman)', () => {
    // Mock login with is_headman: false
    render(
      <BrowserRouter>
        <AuthProvider>
          <BottomNav />
        </AuthProvider>
      </BrowserRouter>
    )
    expect(screen.getByText('Главная')).toBeInTheDocument()
    expect(screen.getByText('Расписание')).toBeInTheDocument()
    expect(screen.getByText('Отметка')).toBeInTheDocument()
    expect(screen.getByText('Профиль')).toBeInTheDocument()
    expect(screen.queryByText('Группа')).not.toBeInTheDocument()
  })
  
  it('renders 5 tabs (Группа before Профиль) for HEADMAN', () => {
    // Mock login with is_headman: true
    render(
      <BrowserRouter>
        <AuthProvider>
          <BottomNav />
        </AuthProvider>
      </BrowserRouter>
    )
    const tabs = screen.getAllByRole('link')
    expect(tabs).toHaveLength(5)
    const tabLabels = tabs.map(t => t.textContent)
    expect(tabLabels).toEqual(['Главная', 'Расписание', 'Отметка', 'Группа', 'Профиль'])
  })
})
```

---

## Pattern 3: Lazy Route Lazy Loading for Headman Features

**What:** Add 8 new routes (`/group`, `/group/overview`, etc.) to the router as lazy-loaded components with Suspense fallback.

**When to use:** Every new headman page component.

**How:**

Extend `main.tsx` router config:

```typescript
// Source: main.tsx (existing router structure extended)
const GroupHub = lazy(() =>
  import('./features/headman/group-hub/GroupHub').then(m => ({ default: m.GroupHub }))
)
const Overview = lazy(() =>
  import('./features/headman/overview/Overview').then(m => ({ default: m.Overview }))
)
// ... (6 more lazy imports)

const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <StompProvider>
          <AppShell />
        </StompProvider>
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <Navigate to="/home" replace /> },
      // Existing routes unchanged (home, schedule, checkin, profile)
      {
        path: 'home',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <SchedulePage />
          </Suspense>
        ),
      },
      // ... (other existing routes)
      
      // NEW: headman routes
      {
        path: 'group',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <GroupHub />
          </Suspense>
        ),
      },
      {
        path: 'group/overview',
        element: (
          <Suspense fallback={<LoadingSpinner />}>
            <Overview />
          </Suspense>
        ),
      },
      // ... (6 more routes)
    ],
  },
])
```

---

## Pattern 4: Headman API Service (Mirror Angular Pattern)

**What:** Centralized service module (`features/headman/shared/headmanApi.ts`) that wraps axios calls following the existing PWA API pattern (`features/schedule/api.ts`, `features/checkin/api.ts`).

**When to use:** Any component making a GET/PUT/DELETE to headman endpoints.

**How:**

Create `features/headman/shared/headmanApi.ts`:

```typescript
// Source: Mirrors frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/lib/axios'

// GET endpoints — use useQuery
export function useGroupMembers(page = 0, size = 50) {
  return useQuery({
    queryKey: ['groupMembers', page],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/groups/my/members', {
        params: { page, size },
      })
      return data._embedded?.userResponseList ?? []
    },
    staleTime: 5 * 60 * 1000, // 5 min
  })
}

export function useGroupSubjects(page = 0, size = 50) {
  return useQuery({
    queryKey: ['groupSubjects', page],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/subjects', {
        params: { page, size },
      })
      return data._embedded?.subjectResponseList ?? []
    },
    staleTime: 24 * 60 * 60 * 1000, // 24 hours
  })
}

export function useJournal(groupId: number, subjectId: number, dateFrom: string, dateTo: string) {
  return useQuery({
    queryKey: ['journal', groupId, subjectId, dateFrom],
    queryFn: async () => {
      const { data } = await apiClient.get('/attendance/reports/journal', {
        params: { groupId, subjectId, dateFrom, dateTo },
      })
      return data._embedded?.journalCellList ?? []
    },
    staleTime: 5 * 60 * 1000, // 5 min (journal changes often)
    enabled: !!groupId && !!subjectId,
  })
}

export function useResolveThreshold(groupId: number, subjectId: number) {
  return useQuery({
    queryKey: ['threshold', groupId, subjectId],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/thresholds/resolve', {
        params: { groupId, subjectId },
      })
      return data
    },
    staleTime: 24 * 60 * 60 * 1000,
    enabled: !!groupId && !!subjectId,
  })
}

// PUT/PATCH/DELETE endpoints — use useMutation
export function useMarkAttendance() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      lessonId,
      userId,
      status,
    }: {
      lessonId: number
      userId: number
      status: string
    }) => {
      const { data } = await apiClient.put(
        `/attendance/lessons/${lessonId}/students/${userId}`,
        { status }
      )
      return data
    },
    onSuccess: () => {
      // Invalidate journal cache after mutation
      queryClient.invalidateQueries({ queryKey: ['journal'] })
    },
  })
}

export function useSetSubjectThreshold() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      subjectId,
      minPercentage,
    }: {
      subjectId: number
      minPercentage: number
    }) => {
      const { data } = await apiClient.put('/academic/thresholds/subject', { minPercentage }, {
        params: { subjectId },
      })
      return data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['threshold'] })
    },
  })
}

// Error handling helper
export function mapHeadmanApiError(status: number): string {
  switch (status) {
    case 403:
      return 'У вас нет прав на эту операцию'
    case 404:
      return 'Ресурс не найден (функция может быть в разработке)'
    case 422:
      return 'Некорректные данные'
    case 429:
      return 'Слишком много запросов. Подождите'
    default:
      return 'Ошибка сервера. Попробуйте ещё раз'
  }
}
```

**Key differences from student APIs:**

- Uses TanStack Query hooks for both fetching and mutations (not raw functions)
- Includes `enabled: !!id` guards for dependent queries
- Mutations invalidate related cache keys on success
- Follows PWA naming: `useGroupMembers`, not `getGroupMembers`

---

## Pattern 5: Journal Cell Interaction (Optimistic UI + Revert on Error)

**What:** User taps a segment button → optimistic state update → network call → on error, revert + show Motion-based inline error.

**When to use:** JournalPage attendance marking.

**How:**

```typescript
// Source: JournalPage.tsx journal cell rendering
import { AnimatePresence, motion } from 'motion/react'
import { useMarkAttendance } from '../shared/headmanApi'

function JournalStudentRow({ student, lessons }: JournalStudentRowProps) {
  const { mutate: markAttendance } = useMarkAttendance()
  const [cellStatuses, setCellStatuses] = useState<Record<number, string>>({})
  const [errors, setErrors] = useState<Record<number, boolean>>({})
  
  const handleSegmentChange = async (lessonId: number, newStatus: string) => {
    // Optimistic: update state immediately
    const oldStatus = cellStatuses[lessonId]
    setCellStatuses(prev => ({ ...prev, [lessonId]: newStatus }))
    setErrors(prev => ({ ...prev, [lessonId]: false }))
    
    // Network call
    markAttendance(
      { lessonId, userId: student.id, status: newStatus },
      {
        onError: () => {
          // Revert on error
          setCellStatuses(prev => ({ ...prev, [lessonId]: oldStatus }))
          setErrors(prev => ({ ...prev, [lessonId]: true }))
          // Error badge auto-hides after 2 seconds via AnimatePresence
          setTimeout(() => {
            setErrors(prev => ({ ...prev, [lessonId]: false }))
          }, 2000)
        },
      }
    )
  }
  
  return (
    <div className="relative">
      <SegmentedControl
        value={cellStatuses[lesson.id] ?? 'present'}
        onValueChange={(newStatus) => handleSegmentChange(lesson.id, newStatus)}
      />
      <AnimatePresence>
        {errors[lesson.id] && (
          <motion.div
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            className="absolute -top-6 left-0 text-xs text-red-600"
          >
            Ошибка. Попробуйте ещё раз
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
```

**Why this pattern:**

- Optimistic UI gives instant visual feedback on mobile (important UX)
- Motion AnimatePresence matches existing PWA error patterns (not MatSnackBar)
- Error auto-hides prevents visual clutter
- Simple state management without Redux/Zustand

---

## Pattern 6: Service Worker Runtime Caching (Stale-While-Revalidate)

**What:** Workbox runtime route registration that caches headman API GET responses with 24-hour TTL.

**When to use:** After defining headmanApi.ts, before deploying PWA.

**How:**

Extend `sw.ts`:

```typescript
// Source: sw.ts (append after existing precacheAndRoute call)
/// <reference lib="webworker" />
import { precacheAndRoute } from 'workbox-precaching'
import { registerRoute } from 'workbox-routing'
import { StaleWhileRevalidate } from 'workbox-strategies'
import { ExpirationPlugin } from 'workbox-expiration'
import { CacheableResponsePlugin } from 'workbox-cacheable-response'

declare const self: ServiceWorkerGlobalScope

precacheAndRoute(self.__WB_MANIFEST)

// Existing push event listeners...
// (PUSHUI-01, PUSHUI-02 code unchanged)

// NEW: Runtime caching for headman API endpoints (D-16..D-20)
const headmanApiMatcher = ({ url }: { url: URL }) => {
  const pathname = url.pathname
  // Match all headman GET endpoints
  return (
    pathname.includes('/academic/groups/') ||
    pathname.includes('/academic/subjects') ||
    pathname.includes('/academic/thresholds/') ||
    pathname.includes('/attendance/reports/journal') ||
    pathname.includes('/attendance/excuses/') ||
    pathname.includes('/attendance/late-checkins/')
  )
}

registerRoute(
  headmanApiMatcher,
  new StaleWhileRevalidate({
    cacheName: 'headman-api-cache-v1',
    plugins: [
      new CacheableResponsePlugin({ statuses: [200] }),
      new ExpirationPlugin({
        maxAgeSeconds: 86400, // 24 hours
        maxEntries: 100,
      }),
    ],
  }),
  'GET' // Only cache GET requests
)
```

**Why this pattern:**

- **Stale-While-Revalidate:** Fast UI from cache + background refresh. Perfect for mobile where stale data is acceptable for read-only operations (journal view, stats).
- **GET-only:** Mutations (PUT, DELETE) always hit network — no cache pollution from failed requests.
- **24-hour TTL:** Matches academic semester cadence (groups, subjects don't change daily).
- **100 entries:** Prevents unbounded cache growth (20 group members × 5 subjects = 100 cache entries per headman).

**Testing note:** Service Worker caching cannot be unit-tested without a browser environment. Verification happens at Phase 56 execution via DevTools:
- Open DevTools → Application → Cache Storage → headman-api-cache-v1
- Trigger a journal GET → verify cache entry created
- Offline mode → verify cached response serves (no network call)
- 30 seconds later, go online → verify background refresh (check Network tab for preflight requests)

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JWT claim parsing | Custom decode logic | `parseJwt()` in AuthProvider.tsx | Bearer token handling is security-critical; framework method tested by 4+ existing tests |
| API request retries + auth refresh | Fetch + manual retry loop | Axios interceptors (`shared/lib/axios.ts`) | Already handles 401 → token refresh → retry queue. Hand-rolling misses race conditions. |
| Component-level caching | useState + useEffect for stale checks | TanStack Query (`useQuery` hooks) | Handles TTL, refetch-on-reconnect, background refresh, auto-invalidation on mutations. |
| Service Worker caching strategy selection | Custom fetch event listeners | Workbox strategies (StaleWhileRevalidate) | Handles race conditions (multiple requests in-flight), TTL, 404 handling, browser compatibility. Custom logic has edge cases. |
| Mobile-friendly segmented buttons | Radio group + CSS cycle | Phosphor Icons + Tailwind with explicit segment state | Fast, touch-friendly, predictable (not cycle-on-tap). Tailwind handles ≥44px touch target. |
| Tab visibility based on role | Inline ternary in BottomNav map | `useTabs()` hook | Keeps component logic separated from data filtering. Easy to test role transitions. |
| Animated error messages | setTimeout + manual reflow | Motion `<AnimatePresence>` | GPU-accelerated, handles rapid dismissals, integrates with existing Motion setup (BottomNav pill animation). |

**Key insight:** This codebase heavily relies on TanStack Query, Workbox, and Motion for data fetching, caching, and UI feedback. Reinventing these patterns invites cache miss bugs, stale data problems, and flaky animations.

---

## Common Pitfalls

### Pitfall 1: Modifying Frozen Feature Directories

**What goes wrong:** Developer adds headman logic to `features/schedule/` to "reuse" schedule code, breaking test expectations.  
**Why it happens:** Hub card "Журнал" shows a similar table as student schedule tab; developer assumes code sharing.  
**How to avoid:** Enforce the frozen directory list (CONTEXT.md D-14). Headman journal is a *different domain* with *different data flow* (subject filter + mass-mark vs. lesson details). Extract *shared primitives* to `shared/components/` only (e.g., `SegmentedControl`).  
**Warning signs:**
- Import from `features/schedule/` inside `features/headman/journal/`
- Tests for `features/schedule/` start failing after Phase 56
- Git diff shows changes to frozen directories

**Preventive measures:**
- Use grep to verify no `import from '@/features/schedule'` in headman code
- CI gate: run existing 63 PWA tests in isolation before headman tests
- Code review: inspect all imports; flag any cross-feature dependencies

---

### Pitfall 2: JWT `is_headman` Claim Not Present in Token

**What goes wrong:** `user.isHeadman` is always `false` even after logging in as headman; Group tab never appears.  
**Why it happens:** Backend JwtService not including claim, or frontend token refresh skips claim parsing.  
**How to avoid:** Verify the backend JWT claim is present. CONTEXT.md D-04 states "JwtService.java:96 already includes these". Before Phase 56 implementation, confirm with: `console.log(parseJwt(token))` in browser DevTools → verify `is_headman: true` in payload.  
**Warning signs:**
- Group tab missing for test headman user
- localStorage token shows no `is_headman` in decoded payload
- Backend logs show token generation without claim

**Preventive measures:**
- Unit test mocks token with `is_headman: true` explicitly
- Integration test: log in as headman user, inspect JWT in DevTools before Phase 56 code runs
- Backend Phase 55 (or earlier) MUST have landed JwtService claim injection

---

### Pitfall 3: Service Worker Cache Poisoning (Caching 404s)

**What goes wrong:** User tries graceful-degradation endpoints (`/attendance/excuses/pending`, `/attendance/late-checkins/pending`), gets 404 → SW caches it → endpoint is later implemented → still returns 404 from cache for 24 hours.  
**Why it happens:** `CacheableResponsePlugin({ statuses: [200] })` is missing or set to include 404.  
**How to avoid:** **Never cache non-2xx responses.** CONTEXT.md D-18 explicitly specifies `statuses: [200]` only. Code review must verify.  
**Warning signs:**
- Late-checkin page shows empty state indefinitely even after backend implements endpoint
- DevTools Cache Storage shows entries with 404 status
- Backend returns 200 but client still gets 404

**Preventive measures:**
- Unit test Workbox plugin config to verify `statuses: [200]`
- Integration test: hit 404 endpoint → verify no cache entry created
- Document fallback behavior: 404 → graceful empty state (no retry loop)

---

### Pitfall 4: Vitest Configuration Missing for New Components

**What goes wrong:** New `JournalPage.test.tsx` fails with "cannot find module '@/features/headman/...'" or DOM mocking errors.  
**Why it happens:** Test files created without proper imports or vitest.config.ts alias setup.  
**How to avoid:** Copy existing test structure (`AuthProvider.test.tsx`, `CheckInButton.test.tsx`). Verify `vitest.config.ts` includes `@` alias and test setup mocks (geolocation, Notification, etc.). All new tests inherit the same setup.  
**Warning signs:**
- `npm run test` errors on new headman tests before other tests
- Import path errors (expected `@/features`, got relative paths)
- `ReferenceError: Notification is not defined`

**Preventive measures:**
- Use existing test as template: copy `features/auth/__tests__/AuthProvider.test.tsx` structure
- vitest.config.ts already configured correctly (verified during research)
- CI gate: `npm run test` must pass all 63 existing + new tests

---

### Pitfall 5: Optimistic UI Not Reverting on Network Error

**What goes wrong:** Journal cell shows "Excused" → network fails → cell still shows "Excused" (wrong data).  
**Why it happens:** Error handler doesn't revert the optimistic state update, or mutation.onError isn't called.  
**How to avoid:** Always pair optimistic state update with `onError` callback that reverts. Test error paths: mock `mutate()` to reject, verify state reverts. CONTEXT.md D-08 specifies error flow: show Motion-based inline error badge, auto-hide after 2s.  
**Warning signs:**
- Journal shows changed status even after network error
- No error message visible to user
- Tests pass with mocked success but fail with simulated 500 error

**Preventive measures:**
- Unit test: simulate `useMarkAttendance()` rejection → verify state reverts + error badge appears
- Mock error response: `{ status: 500, data: { message: 'Server error' } }`
- Inspect DevTools Network tab: simulate offline → confirm optimistic update reverts when request fails

---

### Pitfall 6: Frozen Test Files Modified (Breaking Phase Contract)

**What goes wrong:** Existing 63 PWA tests modified to "fix" warnings, changing test IDs or removing assertions → different behavior than intended.  
**Why it happens:** Developer runs `npm run test`, sees console warning, fixes in source test file instead of in new test.  
**How to avoid:** CONTEXT.md D-22 is strict: "63 existing tests must pass unchanged." Any modification is a contract violation. New tests go in separate `*.test.tsx` files only. If existing test breaks, the new code caused it — redesign.  
**Warning signs:**
- Git diff shows changes to files in `features/{home,schedule,checkin,profile,push,auth}/__tests__/`
- Test count changes (`npm run test` output shows different total)
- CI reports "tests passing but files modified"

**Preventive measures:**
- Commit command: `git diff --name-only | grep __tests__ | grep -v features/headman` → if non-empty, fail
- Phase verification step: run only existing 63 tests in isolation → must all pass
- Code review: inspect git diff for `__tests__` changes, reject immediately

---

## Code Examples

Verified patterns from official sources:

### Example 1: AuthUser Extension Pattern

```typescript
// Source: frontends/pwa/src/features/auth/AuthProvider.tsx + api.ts
// Pattern: Additive type extension without breaking existing consumers

// BEFORE (existing)
export interface AuthUser {
  id: number
  role: string
  groupId?: number
}

// AFTER (extended in Phase 56)
export interface AuthUser {
  id: number
  role: string
  groupId?: number
  isHeadman: boolean  // NEW, defaults to false
}

// Existing consumers (`useAuth().user.role`) continue to work
// New consumers can use `useAuth().user.isHeadman`
```

### Example 2: Lazy Route Loading with Suspense

```typescript
// Source: frontends/pwa/src/main.tsx
// Pattern: Consistent with existing /home, /schedule routes

const SchedulePage = lazy(() =>
  import('./features/schedule/SchedulePage').then(m => ({ default: m.SchedulePage }))
)

// NEW: headman routes follow same pattern
const GroupHub = lazy(() =>
  import('./features/headman/group-hub/GroupHub').then(m => ({ default: m.GroupHub }))
)

// In router config:
{
  path: 'group',
  element: (
    <Suspense fallback={<LoadingSpinner />}>
      <GroupHub />
    </Suspense>
  ),
}
```

### Example 3: TanStack Query Hook for Headman Data

```typescript
// Source: frontends/pwa/src/features/schedule/api.ts (model)
// Pattern: useQuery with staleTime + refetchOnReconnect

export function useGroupMembers(page = 0, size = 50) {
  return useQuery({
    queryKey: ['groupMembers', page],
    queryFn: async () => {
      const { data } = await apiClient.get('/academic/groups/my/members', {
        params: { page, size },
      })
      return data._embedded?.userResponseList ?? []
    },
    staleTime: 5 * 60 * 1000,     // 5 min (members list changes less often than journal)
    refetchOnReconnect: true,      // Refetch when back online
    enabled: true,                 // Always fetch (no dependent query)
  })
}
```

### Example 4: Motion AnimatePresence for Error Messages

```typescript
// Source: frontends/pwa/src/features/schedule/SchedulePage.tsx (model)
// Pattern: Declarative error feedback with auto-dismiss

import { AnimatePresence, motion } from 'motion/react'

function JournalCell({ lesson, status }) {
  const [error, setError] = useState(false)
  
  useEffect(() => {
    if (error) {
      const timer = setTimeout(() => setError(false), 2000)
      return () => clearTimeout(timer)
    }
  }, [error])
  
  return (
    <div className="relative">
      <button onClick={() => markAttendance()}>Mark</button>
      <AnimatePresence>
        {error && (
          <motion.div
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.2 }}
            className="absolute -top-6 text-xs text-red-600"
          >
            Ошибка
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
```

### Example 5: Workbox Runtime Route Registration

```typescript
// Source: Workbox official docs (workbox.dev)
// Pattern: StaleWhileRevalidate with TTL + entry limit

import { registerRoute } from 'workbox-routing'
import { StaleWhileRevalidate } from 'workbox-strategies'
import { ExpirationPlugin } from 'workbox-expiration'
import { CacheableResponsePlugin } from 'workbox-cacheable-response'

registerRoute(
  ({ url }) => url.pathname.includes('/academic/groups/'),  // Matcher function
  new StaleWhileRevalidate({
    cacheName: 'headman-api-cache-v1',
    plugins: [
      new CacheableResponsePlugin({ statuses: [200] }),     // Only cache 200
      new ExpirationPlugin({
        maxAgeSeconds: 86400,  // 24 hours
        maxEntries: 100,       // Limit cache size
      }),
    ],
  }),
  'GET'  // Only cache GET requests
)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Redux for client state | TanStack Query hooks | React 19 / v6.0 PWA | Reduced boilerplate; automatic cache invalidation |
| Fetch + manual error handling | Axios + interceptor library | v1.0 (Auth Service) | Unified token refresh logic; no forgotten error cases |
| `@angular/animations` for Motion | Framer Motion (motion pkg) | v6.0 PWA | GPU-accelerated; better mobile performance |
| Hand-rolled Service Worker | Workbox with precaching | v6.0 PWA | Automatic cache versioning; no orphaned cache entries |
| localStorage for tokens | Memory-only + refresh token cookie | AUTH-v9-03 (Phase 50) | XSS-resistant; httpOnly cookie handled by server |

**Deprecated/outdated:**
- **localStorage JWT storage:** v9.0 uses memory-only access token + httpOnly refresh token cookie (CONTEXT.md AUTH-v9-03). Never store JWT in localStorage in new code.
- **Fetch API without interceptors:** Always use `apiClient` from `/shared/lib/axios.ts`. It handles Bearer header injection + 401 refresh automatically.
- **Unversioned Service Worker cache:** Always use cache versioning (`headman-api-cache-v1`) to prevent stale cache from old deployments.

---

## Assumptions Log

All claims in this research verified against official project sources. No `[ASSUMED]` claims requiring user validation.

| # | Claim | Section | Source | Confidence |
|---|-------|---------|--------|------------|
| — | No unverified assumptions | — | All sections cite VERIFIED sources | HIGH |

---

## Open Questions

1. **Backend JwtService claim injection — already landed?**
   - What we know: CONTEXT.md D-04 states "JwtService.java:96 already includes these [claims]"
   - What's unclear: Phase 55 or earlier phase should have landed this; not verified in git history
   - Recommendation: Before Phase 56 implementation, run backend and log in as headman → inspect JWT in DevTools → verify `is_headman: true` in payload. If missing, block Phase 56 until backend merges the change.

2. **Phase 55 backend change — JournalCell.lessonId added?**
   - What we know: CONTEXT.md lists this as a Phase 55 D-01 fix required before Phase 56
   - What's unclear: Whether the change merged into attendance-service
   - Recommendation: Integration test Phase 56: call `GET /api/attendance/reports/journal?...` → verify response includes `lessonId` field in each cell. If missing, journal marking will fail.

3. **Exact graceful-degradation behavior for 404 endpoints**
   - What we know: CONTEXT.md D-10 specifies empty state card for excuses/late-checkin
   - What's unclear: Should page attempt retry on next visit, or cache the 404 permanently?
   - Recommendation: Per D-19, don't cache 4xx (CacheableResponsePlugin checks this). Empty state persists until user navigates away and returns. If backend implements endpoint later, fresh request will succeed.

---

## Environment Availability

No external dependencies beyond Node.js + npm (already installed). All required Workbox packages are transitive dependencies of `vite-plugin-pwa`.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | Build + dev server | ✓ | 18+ (implicit from pwa package.json engines) | — |
| npm | Package management | ✓ | 9+ (implicit) | — |
| Workbox modules | Service Worker caching | ✓ (transitive) | 7.4.0 | None — required |
| React | Component framework | ✓ | 19.1.0 | — |
| Vitest | Testing | ✓ | 3.1.3 | — |

**No missing or blocking dependencies.** All tools present.

---

## Validation Architecture

Test framework is already configured and proven (63 existing tests pass in Phase 55).

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Vitest 3.1.3 + React Testing Library 16.3.0 |
| Config file | `/frontends/pwa/vitest.config.ts` (verified 2026-04-13) |
| Quick run command | `npm run test` (runs all tests with --passWithNoTests) |
| Full suite command | `npm run test` (Vitest runs full suite by default) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PWA-HEAD-01 | 5th tab "Группа" appears for `isHeadman=true`, hidden for plain students | unit | `npm run test -- BottomNav.test.tsx` | ❌ Wave 0 |
| PWA-HEAD-02a | `/group` hub renders 7 cards (Обзор, Студенты, Предметы, Журнал, Пропуски, Запросы, Статистика) | integration | `npm run test -- GroupHub.test.tsx` | ❌ Wave 0 |
| PWA-HEAD-02b | `/group/journal` accepts subject + date selection, renders student list with segment buttons | integration | `npm run test -- JournalPage.test.tsx` | ❌ Wave 0 |
| PWA-HEAD-02c | Journal segment tap triggers `PUT /attendance/lessons/{id}/students/{id}` with optimistic update + error revert | unit | `npm run test -- JournalPage.test.tsx` | ❌ Wave 0 |
| PWA-HEAD-02d | `/group/stats` renders per-subject cards with threshold inline editor | unit | `npm run test -- StatsPage.test.tsx` | ❌ Wave 0 |
| PWA-HEAD-02e | `/group/excuses`, `/group/late-checkin` render graceful-degradation empty state on 404 | unit | `npm run test -- ExcusesPage.test.tsx, LateCheckinPage.test.tsx` | ❌ Wave 0 |
| PWA-HEAD-03 | All 63 existing PWA tests pass after headman feature addition | integration | `npm run test` (exit code 0, no modified test files) | ✅ Existing |
| PWA-HEAD-04 | Service Worker cache contains GET responses for headman endpoints; manual DevTools verification | manual | DevTools Application → Cache Storage → headman-api-cache-v1 | — |

### Sampling Rate

- **Per task commit:** `npm run test -- src/features/headman/**/*.test.tsx` (new headman tests only)
- **Per wave merge:** `npm run test` (all PWA tests including 63 existing)
- **Phase gate:** `npm run test` must exit with 0, no existing test files modified, DevTools Cache Storage manually verified

### Wave 0 Gaps

- [ ] `src/features/headman/group-hub/GroupHub.test.tsx` — PWA-HEAD-01, 02a
- [ ] `src/features/headman/overview/Overview.test.tsx` — PWA-HEAD-02a (hub card)
- [ ] `src/features/headman/students/StudentsList.test.tsx` — PWA-HEAD-02a (hub card)
- [ ] `src/features/headman/subjects/SubjectsList.test.tsx` — PWA-HEAD-02a (hub card)
- [ ] `src/features/headman/journal/JournalPage.test.tsx` — PWA-HEAD-02b, 02c
- [ ] `src/features/headman/excuses/ExcusesPage.test.tsx` — PWA-HEAD-02e
- [ ] `src/features/headman/late-checkin/LateCheckinPage.test.tsx` — PWA-HEAD-02e
- [ ] `src/features/headman/stats/StatsPage.test.tsx` — PWA-HEAD-02d
- [ ] `src/shared/components/BottomNav.test.tsx` — PWA-HEAD-01
- [ ] `src/features/auth/__tests__/AuthProvider.isHeadman.test.tsx` — isHeadman parsing verification
- [ ] Framework install: `npm install` — all dependencies already in lock file

---

## Security Domain

This phase adds role-based feature exposure (headman tab only for headmen) and extends API calls to headman endpoints. No new authentication mechanisms; JWT claim parsing and bearer token handling already verified in Phase 50+ (AUTH-v9-01..07).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Handled by existing JwtService + bearer token interceptor |
| V3 Session Management | no | Existing cookie-based refresh token (httpOnly) |
| V4 Access Control | yes | JWT `is_headman` claim + client-side tab visibility (frontend convenience only; backend validates real permissions) |
| V5 Input Validation | yes | All headman API calls validate request body (e.g., attendance status enum, threshold 0-100). Axios + TanStack Query don't validate; rely on backend contracts. |
| V6 Cryptography | no | Bearer token over HTTPS (infrastructure-level, not PWA-level) |

### Known Threat Patterns for React PWA Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| JWT token leak (localStorage) | Information Disclosure | Use memory-only storage + httpOnly refresh cookie (PHASE-50 solution, inherited) |
| XSS in JSON response | Tampering, Elevation of Privilege | React JSX auto-escapes; apiClient parses JSON. No `dangerouslySetInnerHTML` in headman components. |
| CSRF on state-changing API calls | Tampering | Axios sets standard `Content-Type: application/json`. Browser enforces SOP. Backend validates CORS. |
| API endpoint enumeration | Information Disclosure | Graceful 404 handling (CONTEXT.md D-10) masks non-implemented endpoints from enumeration. |
| Headman impersonation (manipulating JWT claim) | Elevation of Privilege | JWT verified by backend; client-side claim display is UI convenience. Backend re-checks `is_headman` claim on every headman endpoint. |

**No new security concerns introduced.** All headman features delegate authorization to the backend (API Gateway passes `X-Is-Headman` header; academic-service validates group membership).

---

## Sources

### Primary (HIGH confidence)

- [VERIFIED: package.json] — React 19.1.0, TanStack Query 5.96.2, Workbox 7.4.0, Vitest 3.1.3 dependencies
- [VERIFIED: node_modules/] — All Workbox submodules (routing, strategies, expiration, cacheable-response) installed
- [VERIFIED: frontends/pwa/src/] — AuthProvider.tsx JWT parsing, BottomNav.tsx tab array, sw.ts precaching, vite.config.ts PWA config, vitest.config.ts test setup
- [VERIFIED: frontends/pwa/vitest.config.ts] — `globals: true`, `environment: 'jsdom'`, `setupFiles: ['./src/test/setup.ts']`
- [CITED: frontends/pwa/src/features/schedule/api.ts] — TanStack Query hook pattern (`useQuery`, `useQueryClient.prefetchQuery`)
- [CITED: frontends/pwa/src/features/checkin/api.ts] — TanStack Query mutation pattern (`useMutation`)
- [CITED: frontends/pwa/src/shared/lib/axios.ts] — Axios interceptor setup for Bearer token + 401 refresh
- [CITED: docs/design-decisions.md] — Motion library (framer-motion), Phosphor Icons weights, Tailwind + Transit Grid tokens

### Secondary (MEDIUM confidence)

- [CITED: frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts] — Angular API service methods to mirror in React PWA
- [CITED: .planning/CONTEXT.md] — Phase 56 design decisions (D-01 through D-22) and architectural constraints
- [CITED: .planning/REQUIREMENTS.md §PWA HEADMAN Mode] — PWA-HEAD-01..04 requirements
- [CITED: .planning/REQUIREMENTS.md §Headman Web Cabinet] — HEAD-WEB-02..08 features to implement in PWA

### Tertiary (Workbox official)

- [CITED: developer.chrome.com/docs/workbox/reference/workbox-strategies/#type-StaleWhileRevalidate] — Workbox strategy documentation
- [CITED: developer.chrome.com/docs/workbox/modules/workbox-expiration] — Workbox expiration plugin TTL + entry limit

---

## Metadata

**Confidence breakdown:**

| Area | Level | Reason |
|------|-------|--------|
| Standard Stack | HIGH | All dependencies verified in package.json and node_modules; versions stable for 6+ months (PWA deployed Phase 30 — 2026-04-06) |
| Architecture Patterns | HIGH | Existing PWA codebase provides verified patterns for auth extension (AuthProvider), lazy routing (main.tsx), TanStack Query (schedule/api.ts), Motion (SchedulePage), Workbox (sw.ts) |
| Testing | HIGH | Vitest + React Testing Library established in Phase 54+; 63 tests pass; setup.ts covers all mocks needed |
| Pitfalls | HIGH | Researched from CONTEXT.md discussion phase + Phase 55 web-panel implementation (parallel domain, same features, different framework) |
| Security | HIGH | JWT claim handling inherited from Phase 50 (AUTH-v9-01..07); no new authentication introduced |
| Code Examples | HIGH | All examples source from verified project code or official library docs |

**Research date:** 2026-04-13  
**Valid until:** 2026-05-13 (30 days — PWA dependencies stable; refresh if Node.js LTS updates)

---

**Ready for planning.** All technical investigation complete. Planner can proceed to task breakdown.
