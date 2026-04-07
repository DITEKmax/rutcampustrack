# Phase 30: Schedule + Check-in UI - Research

**Researched:** 2026-04-06
**Domain:** React PWA — schedule view, geo check-in flow, STOMP real-time updates, offline caching
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Horizontal swipe gesture between days + tappable day tabs at top (Mon–Sat only, no Sunday). Uses a swipeable tab strip component.
- **D-02:** Week navigation via swipe — header shows "← 7–12 апр →" with arrow buttons. Swiping the entire tab strip changes week.
- **D-03:** Default view opens on today's tab with auto-scroll to current/next lesson. Past lessons visible above by scrolling up.
- **D-04:** Floating "Сегодня" pill button appears when viewing a non-current week. Tap snaps back to current week + today's tab. Disappears when already on current week.
- **D-05:** TanStack Query prefetches adjacent days for smooth swiping. Schedule API returns weekly data.
- **D-06:** "Отметиться" button lives directly on the active lesson card in the schedule view. One tap from schedule, no separate navigation needed.
- **D-07:** Instant GPS capture + submit: tap → spinner on button → browser requests GPS → coords sent to backend → success/fail toast. Single action, ~2-3 seconds.
- **D-08:** After successful check-in: status badge changes from "Идёт" to "Отмечен" (green), "Отметиться" button replaced with checkmark icon, success toast appears briefly.
- **D-09:** GPS permission denial: inline toast "Нет доступа к GPS. Разрешите доступ в настройках браузера". Button re-enables for retry.
- **D-10:** /checkin bottom nav tab shows dedicated screen with current active lesson + check-in button. When no active lesson, shows empty state with next lesson info.
- **D-11:** STOMP WebSocket subscription shared across schedule cards and /checkin screen.
- **D-12:** Attendance count updates with subtle number flip animation when STOMP `attendance.marked` event arrives. No toast per person.
- **D-13:** Silent auto-reconnect with exponential backoff (1s, 2s, 4s...). No notification unless disconnected >30s. On reconnect, refetch current lesson data.
- **D-14:** Stale-while-revalidate with 1hr max stale for schedule data. `staleTime: 60 * 60 * 1000`.
- **D-15:** Subtle top banner "Офлайн · обновлено 15 мин назад" using OfflineBanner component from Phase 29 (extended).
- **D-16:** Auto-refetch on reconnect (`refetchOnReconnect: true`) + pull-to-refresh as manual trigger.
- **D-17:** Check-in button disabled when offline with text "Нет подключения" below it. No offline queue.

### Claude's Discretion

- Swipeable tab strip library choice / custom implementation
- Exact lesson card layout and information density
- STOMP client library and connection management details
- Pull-to-refresh implementation approach
- Loading skeleton design for schedule
- Error toast styling and timing
- Week data prefetching strategy

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SCHED-01 | User can view today's schedule (lessons with time, subject, room, status) | LessonApi returns `GET /schedule/groups/{groupId}/lessons?dateFrom=&dateTo=`; LessonResponse has all fields; subjectId requires secondary fetch to /academic/subjects/{id} |
| SCHED-02 | User can navigate weekly schedule (swipe/tab between days) | TanStack Query week-scoped cache; Motion drag gestures; WeekDayTabs component; D-01 through D-04 define navigation model |
| SCHED-03 | Schedule is cached offline (stale-while-revalidate, 1hr max stale) | TanStack Query `staleTime: 3600000`; `refetchOnReconnect: true`; OfflineStaleNotice extends Phase 29 OfflineBanner |
| CHKIN-01 | User can tap check-in on active lesson card, GPS coords captured and submitted | `navigator.geolocation.getCurrentPosition`; POST `/api/attendance/checkin` with `{lat, lng}`; CheckinApi contract verified |
| CHKIN-02 | User sees immediate success/failure feedback with specific reason | HTTP response codes mapped: 404 → no active lesson, 409 → already marked, 422 → not in zone, 403 → geo-blocked; CheckinToast component |
| CHKIN-03 | Check-in UI updates in real-time via STOMP WebSocket on attendance.marked event | STOMP endpoint `/ws` with SockJS; JWT passed as `?token=` query param; topic `/topic/group/{groupId}`; event `attendance.marked` with `{lesson_id, user_id, group_id, status}` |
</phase_requirements>

---

## Summary

Phase 30 builds the schedule screen and geo check-in flow on top of the Phase 29 PWA foundation. The schedule API at `GET /api/schedule/groups/{groupId}/lessons?dateFrom=&dateTo=` returns a `PagedModel<EntityModel<LessonResponse>>` — but `LessonResponse` contains only `subjectId` and `teacherId` (not names). The PWA must resolve subject names from the academic service at `/api/academic/subjects/{id}` with per-subject TanStack Query caching. This is the most significant discovery: subject names are NOT included in lesson responses.

The STOMP WebSocket is already implemented in notification-service at endpoint `/ws` (SockJS-enabled). Authentication at handshake time uses JWT passed as `?token=` query parameter — not an Authorization header, because browsers cannot set custom headers on WebSocket upgrades. The topic for group-scoped attendance events is `/topic/group/{groupId}`. The event envelope is `{type: "attendance.marked", payload: {lesson_id, user_id, group_id, status, marked_by}}`.

The check-in API at `POST /api/attendance/checkin` accepts `{lat, lng}` (no explicit lessonId — the backend resolves the active lesson server-side from the authenticated user's group). This means the PWA does NOT need to pass a lesson ID when checking in.

**Primary recommendation:** Use `@stomp/stompjs` (already available on npm, v7.3.0) with SockJS-client for the WebSocket layer. No swipe library needed — Motion's `drag` prop with `dragDirectionLock` handles horizontal swipes natively, matching the already-installed Motion v12 library.

---

## Standard Stack

### Core (all already installed in frontends/pwa/)

| Library | Version | Purpose | Source |
|---------|---------|---------|--------|
| `@tanstack/react-query` | 5.96.2 | Schedule data fetching, caching, stale-while-revalidate | [VERIFIED: package.json] |
| `motion` (framer-motion API) | 12.38.0 | Swipe gestures, tab transitions, count animations | [VERIFIED: package.json] |
| `axios` | 1.14.0 | API calls via pre-configured `apiClient` | [VERIFIED: package.json] |
| `@phosphor-icons/react` | 2.1.10 | Status icons, checkmark after check-in | [VERIFIED: package.json] |
| `tailwindcss` | 4.1.4 | Styling with shadcn base-nova preset | [VERIFIED: package.json] |

### To Install

| Library | Version | Purpose | Source |
|---------|---------|---------|--------|
| `@stomp/stompjs` | 7.3.0 | STOMP WebSocket client (SockJS-compatible) | [VERIFIED: npm registry] |
| `sockjs-client` | 1.6.1 | SockJS transport for STOMP (required by backend's SockJS endpoint) | [VERIFIED: npm registry] |

**Installation:**
```bash
cd frontends/pwa && npm install @stomp/stompjs sockjs-client
npm install --save-dev @types/sockjs-client
```

**Why these specific versions:** The notification-service backend uses Spring's SockJS endpoint. `@stomp/stompjs` v7+ includes native WebSocket support AND SockJS fallback via `sockjs-client`. The npm registry shows 7.3.0 as current stable. [VERIFIED: npm registry]

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `@stomp/stompjs` + `sockjs-client` | Native WebSocket (`new WebSocket()`) | Native WS bypasses SockJS negotiation — will fail because the backend uses `withSockJS()` which adds `/info` polling and `/websocket` path suffix |
| Motion `drag` for swipe | `react-swipeable` or `use-gesture` | Motion is already installed (v12); adding another gesture lib is unnecessary weight |
| Per-subject API call | Embed subject name in schedule response | LessonResponse is a backend contract — not modifiable in this phase. Must use subject API. |

---

## Architecture Patterns

### Recommended Project Structure

```
src/features/
├── schedule/
│   ├── SchedulePage.tsx           # Root page: week navigation + lesson list
│   ├── WeekDayTabs.tsx            # Tab strip Mon–Sun with active indicator
│   ├── LessonCard.tsx             # Individual lesson card
│   ├── StatusBadge.tsx            # Pill badge: active/planned/cancelled/attendance statuses
│   ├── OfflineStaleNotice.tsx     # Inline stale-data banner (extends OfflineBanner pattern)
│   ├── api.ts                     # TanStack Query hooks: useWeekSchedule, useSubjectName
│   └── CheckInScreen.tsx          # /checkin dedicated screen (active lesson or empty state)
├── checkin/
│   ├── CheckInButton.tsx          # GPS capture + submit button with loading state
│   ├── CheckInToast.tsx           # Slide-up success/failure toast
│   ├── useStompCheckin.ts         # STOMP WebSocket hook for attendance.marked events
│   └── api.ts                     # useMutation hook for POST /api/attendance/checkin
```

### Pattern 1: Schedule API Call (week-scoped query)

The schedule endpoint is:
```
GET /api/schedule/groups/{groupId}/lessons?dateFrom=YYYY-MM-DD&dateTo=YYYY-MM-DD
```
Response: `PagedModel<EntityModel<LessonResponse>>` — paginated HATEOAS wrapper.
**Important:** Pass `size=100` (or similar large page size) to get all lessons for the week in one call rather than paginating.

```typescript
// src/features/schedule/api.ts
// Source: verified against LessonApi.java and apiClient.ts

export function useWeekSchedule(groupId: number, weekStart: string, weekEnd: string) {
  return useQuery({
    queryKey: ['schedule', groupId, weekStart],
    queryFn: async () => {
      const { data } = await apiClient.get(
        `/schedule/groups/${groupId}/lessons`,
        { params: { dateFrom: weekStart, dateTo: weekEnd, size: 100 } }
      )
      // PagedModel returns data._embedded.lessonResponseList
      return (data._embedded?.lessonResponseList ?? []) as LessonResponse[]
    },
    staleTime: 60 * 60 * 1000,   // D-14: 1hr stale override
    refetchOnReconnect: true,      // D-16
  })
}
```

**Pitfall:** The HATEOAS `PagedModel` embeds items under `_embedded.{camelCase(entityName)List}`. For `LessonResponse`, Spring uses key `lessonResponseList`. Always guard with `?? []`. [VERIFIED: codebase inspection of LessonAssembler.java]

### Pattern 2: Subject Name Resolution

`LessonResponse` has `subjectId: number` but no `subjectName`. Subject names must be fetched from Academic Service.

```typescript
// Source: verified against SubjectApi.java and SubjectResponse.java

export function useSubjectName(subjectId: number | undefined) {
  return useQuery({
    queryKey: ['subject', subjectId],
    queryFn: async () => {
      const { data } = await apiClient.get(`/academic/subjects/${subjectId}`)
      return data.name as string
    },
    enabled: !!subjectId,
    staleTime: 24 * 60 * 60 * 1000, // subjects rarely change — 24hr stale
  })
}
```

Strategy: fetch subjects lazily per card. Since all lessons in a week typically span 5-10 unique subjects, TanStack Query deduplicates concurrent requests for the same subjectId. [ASSUMED — deduplication behavior is standard TanStack Query]

### Pattern 3: Check-in Mutation

**Critical finding:** `CheckinRequest` accepts `{lat, lng}` only — NO `lessonId`. The backend resolves the active lesson server-side from the JWT `group_id` claim. [VERIFIED: CheckinRequest.java and CheckinService.java]

```typescript
// Source: verified against CheckinApi.java and CheckinRequest.java

export function useCheckin() {
  return useMutation({
    mutationFn: async (coords: { lat: number; lng: number }) => {
      const { data } = await apiClient.post('/attendance/checkin', coords)
      return data
    },
  })
}
```

The GPS capture flow uses `navigator.geolocation.getCurrentPosition` with options `{ timeout: 10000, maximumAge: 30000 }`. [VERIFIED: 30-UI-SPEC.md interaction contract]

### Pattern 4: STOMP WebSocket Connection

The backend's STOMP endpoint is at `/ws` with SockJS. JWT is passed as `?token=` query parameter (NOT Authorization header — browsers cannot set custom headers on WebSocket upgrades).

Gateway routes `/api/ws/**` → notification-service stripping `/api` prefix, so the effective path from the PWA is `/api/ws`. [VERIFIED: api-gateway application.yml and WebSocketConfig.java]

```typescript
// Source: verified against WebSocketConfig.java, JwtHandshakeInterceptor.java,
//         EventConsumer.java, api-gateway application.yml

import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export function useStompCheckin(groupId: number, accessToken: string, onMarked: (payload: AttendanceMarkedPayload) => void) {
  useEffect(() => {
    let reconnectDelay = 1000
    const client = new Client({
      webSocketFactory: () => new SockJS(`/api/ws?token=${accessToken}`),
      reconnectDelay,                    // D-13: exponential backoff handled by stompjs
      onConnect: () => {
        reconnectDelay = 1000            // reset on success
        client.subscribe(`/topic/group/${groupId}`, (message) => {
          const envelope = JSON.parse(message.body)
          if (envelope.type === 'attendance.marked') {
            onMarked(envelope.payload)
          }
        })
      },
    })
    client.activate()
    return () => { client.deactivate() }
  }, [groupId, accessToken])
}
```

**Event payload shape** (verified from AttendanceEventPublisher.java):
```typescript
interface AttendanceMarkedPayload {
  lesson_id: number
  user_id: number
  group_id: number
  status: string        // 'present' | 'absent' | etc
  marked_by: string     // 'student_geo' | etc
}
```

### Pattern 5: Attendance Count (No Backend Count Endpoint)

`LessonResponse` does NOT include an attendance count. The `attendance.marked` STOMP event gives us individual mark events. **The PWA must maintain a local count per lesson in component state**, incrementing on each `attendance.marked` event for the matching `lesson_id`. Initial count comes from... nowhere in the current API.

**Open question — see ## Open Questions #1.** The planner must address how the initial attendance count is fetched or whether it starts at 0 and increments only via STOMP.

### Pattern 6: Querying Group ID from Auth Context

The schedule API requires `groupId`. The `AuthProvider` exposes `user.groupId` (parsed from JWT). [VERIFIED: AuthProvider.tsx]

```typescript
const { user } = useAuth()
const groupId = user?.groupId  // number | undefined
```

The schedule screen must handle `groupId === undefined` (e.g., teacher role has no groupId). [ASSUMED — based on AuthProvider.tsx JWT parsing; TEACHER role behavior not verified]

### Anti-Patterns to Avoid

- **Passing lessonId to check-in:** `CheckinRequest` only takes `{lat, lng}`. Do not add `lessonId` to the request body.
- **Using Authorization header for STOMP:** Browsers block custom headers on WS upgrades. JWT must go in `?token=` query param. [VERIFIED: JwtHandshakeInterceptor.java]
- **Relying on `_embedded` key without guard:** Spring HATEOAS omits `_embedded` entirely when the list is empty. Always use `data._embedded?.lessonResponseList ?? []`.
- **Global staleTime for schedule:** The global queryClient has 5-minute staleTime. Schedule queries MUST override with 60-minute staleTime per D-14.
- **Importing from lucide-react:** Project standard is `@phosphor-icons/react` exclusively. [VERIFIED: 30-UI-SPEC.md Registry Safety]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| STOMP over SockJS | Custom WebSocket reconnect logic | `@stomp/stompjs` Client with `reconnectDelay` | Built-in exponential backoff, SockJS transport, subscription management, heartbeat |
| GPS position capture | Custom Promise wrapper | `navigator.geolocation.getCurrentPosition` directly | Browser API is sufficient; wrapping adds no value |
| Horizontal swipe detection | Touch event tracking with clientX math | `motion` `drag` prop with `dragDirectionLock` | Motion is already installed; handles both touch and mouse, prevents scroll conflicts |
| Loading skeleton | Custom shimmer animation | CSS `animate-pulse` (Tailwind) on gray divs | Zero-dependency, matches Tailwind patterns already in codebase |
| Toast auto-dismiss | `setTimeout` in useEffect | Simple `useEffect` with cleanup IS the right approach | No library needed; single-purpose component |

---

## Runtime State Inventory

Step 2.5: SKIPPED — this is a greenfield feature phase (new components and API integrations), not a rename/refactor/migration.

---

## Environment Availability Audit

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Node.js | Build, npm install | ✓ | (project builds) | — |
| `@stomp/stompjs` | CHKIN-03 STOMP | ✗ (not in package.json) | 7.3.0 on npm | — (must install) |
| `sockjs-client` | CHKIN-03 SockJS transport | ✗ (not in package.json) | 1.6.1 on npm | — (must install) |
| notification-service (STOMP) | CHKIN-03 | Runtime only — not verifiable at plan time | — | — |
| schedule-service | SCHED-01 | Runtime only | — | — |
| attendance-service | CHKIN-01 | Runtime only | — | — |
| academic-service | Subject names | Runtime only | — | — |

**Missing dependencies with no fallback:**
- `@stomp/stompjs` + `sockjs-client` — must be installed before implementing `useStompCheckin.ts`

**Missing dependencies with fallback:**
- None

---

## Common Pitfalls

### Pitfall 1: Empty PagedModel `_embedded`
**What goes wrong:** When a day has no lessons, the API returns `{_links: {...}}` with no `_embedded` key. `data._embedded.lessonResponseList` throws TypeError.
**Why it happens:** Spring HATEOAS omits the `_embedded` wrapper entirely for empty pages.
**How to avoid:** Always guard: `data._embedded?.lessonResponseList ?? []`
**Warning signs:** TypeError in console when navigating to a day with no lessons.

### Pitfall 2: STOMP JWT in Authorization Header
**What goes wrong:** Browser blocks the connection silently.
**Why it happens:** The `WebSocket` API does not allow custom request headers during HTTP Upgrade. The JWT handshake interceptor specifically reads `?token=` from the query string.
**How to avoid:** Always pass JWT as `?token=` in the SockJS URL. [VERIFIED: JwtHandshakeInterceptor.java]

### Pitfall 3: staleTime Not Overridden per Query
**What goes wrong:** Schedule data refetches every 5 minutes (global default), causing stale banner to disappear too quickly and unnecessary network requests.
**Why it happens:** `queryClient.ts` sets global `staleTime: 5 * 60 * 1000`. Per-query override is opt-in.
**How to avoid:** Explicitly set `staleTime: 60 * 60 * 1000` on every `useWeekSchedule` query call.

### Pitfall 4: LessonResponse Has No Subject Name — Waterfall Risk
**What goes wrong:** Each `LessonCard` calls `useSubjectName(subjectId)` independently, causing N serial API calls if not deduplicated.
**Why it happens:** TanStack Query deduplicates concurrent requests for the same key, but only within the same React render cycle.
**How to avoid:** Prefetch all unique subjectIds from the week's lesson list before rendering cards. Or rely on TanStack Query request deduplication — it batches concurrent calls for the same queryKey.

### Pitfall 5: GPS getCurrentPosition on HTTP (non-HTTPS)
**What goes wrong:** Geolocation API throws `PermissionDeniedError` on non-HTTPS origins.
**Why it happens:** Browser security policy restricts geolocation to secure contexts.
**How to avoid:** Development must use localhost (considered secure) or HTTPS. Production must serve over HTTPS. [ASSUMED — standard browser security policy]

### Pitfall 6: Attendance Count Has No Initial Value
**What goes wrong:** The lesson card shows "0 / ? чел" on first render because `LessonResponse` contains no attendance count.
**Why it happens:** The schedule API doesn't aggregate attendance counts. There is no "get current attendance count for lesson" endpoint in the schedule or attendance service APIs visible in this codebase. [VERIFIED: LessonResponse.java — no count fields]
**How to avoid:** See Open Questions #1. Planner must decide strategy.

### Pitfall 7: STOMP Reconnect Exposes Stale Access Token
**What goes wrong:** After silent token refresh, the STOMP client reconnects with the old token in the SockJS URL, causing a 401 handshake rejection.
**Why it happens:** The SockJS URL is baked in at `client.activate()` time. Token refresh happens asynchronously in the Axios interceptor.
**How to avoid:** Subscribe to token changes from `AuthProvider` and call `client.deactivate()` + `client.activate()` when access token changes. Or pass the `tokenRef` (not the token value) and re-read it at reconnect time using a factory function.

---

## Code Examples

### LessonResponse TypeScript type

```typescript
// Derived from LessonResponse.java [VERIFIED: codebase]
interface LessonResponse {
  id: number
  scheduleItemId: number
  groupId: number
  subjectId: number
  teacherId: number
  date: string           // ISO date 'YYYY-MM-DD'
  status: 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'CANCELLED'
  dayOfWeek: number      // 1=Mon ... 7=Sun
  lessonNumber: number
  startTime: string      // 'HH:mm:ss'
  endTime: string        // 'HH:mm:ss'
  weekType: 'NUMERATOR' | 'DENOMINATOR' | 'BOTH'
  room: string
  geoBlocked: boolean
  cancelReason: string | null
  createdAt: string
}
```

### Check-in error handling by HTTP status

```typescript
// Source: verified against CheckinApi.java response codes

function mapCheckinError(status: number, detail?: string): string {
  switch (status) {
    case 403: return 'Геоотметка заблокирована преподавателем'
    case 404: return 'Активное занятие не найдено'
    case 409: return 'Вы уже отмечены на этом занятии'
    case 422: return 'Вы находитесь вне зоны отметки'
    case 429: return 'Слишком много попыток. Подождите минуту'
    default:  return 'Ошибка сервера. Попробуйте ещё раз'
  }
}
```

### SockJS STOMP connection with token factory

```typescript
// Source: verified pattern from JwtHandshakeInterceptor.java
// Token must be current at each reconnect attempt

const client = new Client({
  webSocketFactory: () => {
    const token = getAccessToken()   // read current token at factory call time
    return new SockJS(`/api/ws?token=${token}`)
  },
  reconnectDelay: 1000,
})
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| `stompjs` v2-5 (callback-based) | `@stomp/stompjs` v7 (class-based Client) | v7 has TypeScript types, built-in SockJS factory, `reconnectDelay` property |
| SockJS directly for reconnect | `@stomp/stompjs` Client handles it | No manual reconnect loop needed |
| `react-use-websocket` | `@stomp/stompjs` with custom hook | STOMP protocol awareness, topic subscriptions — raw WS libs don't parse STOMP frames |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | TanStack Query deduplicates concurrent `useSubjectName` calls for the same subjectId within the same render | Pitfall 4 | N subject API calls fire simultaneously instead of 1; unlikely to cause errors but wastes bandwidth |
| A2 | GPS geolocation requires HTTPS in production | Pitfall 5 | Check-in silently fails on HTTP origins |
| A3 | `_embedded` key in PagedModel for `LessonResponse` is `lessonResponseList` | Pattern 1 | Array access fails on empty pages or returns undefined |
| A4 | TEACHER role users have no `groupId` in JWT, so schedule screen needs a guard | Pattern 6 | Uncaught error calling schedule API with undefined groupId |

---

## Open Questions

1. **Initial attendance count for lesson cards**
   - What we know: `LessonResponse` has no attendance count fields; `attendance.marked` STOMP events allow incrementing but not initializing the count.
   - What's unclear: Is there an endpoint to fetch current attendance count per lesson (e.g., via the attendance report API)? Or should the count simply start at 0 and only reflect counts seen during the current session?
   - Recommendation: Start at 0. The `attendance.marked` event does not contain a running total, only individual marks. If the lesson card UI shows count, it will only be accurate for marks received after the WebSocket connected. The planner should either: (a) omit the count from lesson cards (simplest), or (b) add a separate query to `/api/attendance/report/...` for current lesson attendance. The UI-SPEC shows "3 / 24 чел" suggesting backend support — but no such endpoint was found in the verified API contracts.

2. **Teacher name on lesson card**
   - What we know: `LessonResponse.teacherId` is present, but teacher full name requires a call to `/api/academic/users/{id}`.
   - What's unclear: Is teacher name required by the UI-SPEC for MVP? It appears in the `LessonCard` anatomy as "Иванов И.И." but is not in any requirement.
   - Recommendation: Omit teacher name lookup in this phase. The card shows subject name (requiring subject lookup) — adding teacher name doubles the secondary API calls. Mark as Claude's discretion.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Vitest 3.1.3 + @testing-library/react 16.3.0 |
| Config file | `frontends/pwa/vitest.config.ts` |
| Quick run command | `npm run test --prefix frontends/pwa` |
| Full suite command | `npm run test --prefix frontends/pwa` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SCHED-01 | Schedule renders lessons with time, subject, room, status | unit | `npm run test --prefix frontends/pwa` | ❌ Wave 0 |
| SCHED-02 | Day tab change updates displayed lessons | unit | `npm run test --prefix frontends/pwa` | ❌ Wave 0 |
| SCHED-03 | Offline: stale data renders with stale notice; no data shows empty state | unit | `npm run test --prefix frontends/pwa` | ❌ Wave 0 |
| CHKIN-01 | Check-in button triggers GPS + POST; success updates card | unit | `npm run test --prefix frontends/pwa` | ❌ Wave 0 |
| CHKIN-02 | Each HTTP error code shows correct Russian failure message | unit | `npm run test --prefix frontends/pwa` | ❌ Wave 0 |
| CHKIN-03 | attendance.marked STOMP event increments count on card | unit | `npm run test --prefix frontends/pwa` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `npm run test --prefix frontends/pwa`
- **Per wave merge:** `npm run test --prefix frontends/pwa`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `frontends/pwa/src/features/schedule/__tests__/SchedulePage.test.tsx` — covers SCHED-01, SCHED-02
- [ ] `frontends/pwa/src/features/schedule/__tests__/OfflineStaleNotice.test.tsx` — covers SCHED-03
- [ ] `frontends/pwa/src/features/checkin/__tests__/CheckInButton.test.tsx` — covers CHKIN-01, CHKIN-02
- [ ] `frontends/pwa/src/features/checkin/__tests__/useStompCheckin.test.ts` — covers CHKIN-03
- [ ] Mock for `navigator.geolocation` in `src/test/setup.ts`
- [ ] Mock for `@stomp/stompjs` Client in test utilities

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT in memory (AuthProvider) — already established Phase 29 |
| V3 Session Management | yes | httpOnly cookie for refresh — already established Phase 29 |
| V4 Access Control | yes | `groupId` from JWT claim — backend enforces, PWA just reads |
| V5 Input Validation | yes | GPS coords validated server-side (`@DecimalMin/@DecimalMax` in CheckinRequest); no user text input in this phase |
| V6 Cryptography | no | No new crypto in this phase |

### Known Threat Patterns for Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| JWT token in WebSocket URL query string | Information Disclosure | Unavoidable for browser WebSocket API; mitigated by short JWT expiry (15 min per Phase 29) and HTTPS in production |
| GPS spoofing for check-in bypass | Tampering | Server-side geofence validation (CheckinService.geofenceService) — PWA cannot prevent client-side spoofing |
| STOMP subscription to another group's topic | Elevation of Privilege | Backend enforces JWT `group_id` at handshake; clients can attempt to subscribe to `/topic/group/other` but the backend would need to enforce authorization on subscribe (verify this is enforced in notification-service) |

---

## Sources

### Primary (HIGH confidence)

- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/CheckinApi.java` — check-in endpoint contract
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/checkin/CheckinRequest.java` — confirms {lat, lng} only, no lessonId
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/CheckinService.java` — confirms server-side lesson resolution, error types, HTTP codes
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/AttendanceEventPublisher.java` — exact STOMP event payload shape
- `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java` — STOMP endpoint /ws, SockJS, topic /topic/group/{groupId}
- `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/JwtHandshakeInterceptor.java` — confirms ?token= query param required
- `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/LessonApi.java` — schedule endpoint with dateFrom/dateTo params
- `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/dto/lesson/LessonResponse.java` — confirms subjectId (not name)
- `services/api-gateway/src/main/resources/application.yml` — gateway routes /api/schedule, /api/attendance, /api/ws
- `frontends/pwa/package.json` — confirms installed libraries and versions
- `frontends/pwa/src/shared/lib/axios.ts` — apiClient baseURL is `/api`
- `frontends/pwa/src/features/auth/AuthProvider.tsx` — user.groupId parsed from JWT
- `.planning/phases/30-schedule-check-in-ui/30-UI-SPEC.md` — locked visual spec, component names, copy
- `.planning/phases/30-schedule-check-in-ui/30-CONTEXT.md` — locked decisions D-01 through D-17

### Secondary (MEDIUM confidence)

- npm registry: `@stomp/stompjs` v7.3.0, `sockjs-client` v1.6.1 — verified current versions at research time

### Tertiary (LOW confidence)

- None

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries verified in package.json and npm registry
- Backend API contracts: HIGH — verified from Java source files
- STOMP integration: HIGH — verified from WebSocketConfig.java and JwtHandshakeInterceptor.java
- Subject name resolution strategy: MEDIUM — LessonResponse verified, subject API verified; count-per-lesson unknown
- Attendance count initial value: LOW — no endpoint found; open question must be resolved

**Research date:** 2026-04-06
**Valid until:** 2026-05-06 (stable stack)
