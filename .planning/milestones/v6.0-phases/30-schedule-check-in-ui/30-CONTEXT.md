# Phase 30: Schedule + Check-in UI - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Students can view their daily and weekly schedule and submit a geo check-in from an active lesson card. Phase delivers: schedule screen with day navigation, lesson cards with status badges, geo check-in flow, real-time attendance updates via STOMP WebSocket, and offline stale-while-revalidate caching. No attendance stats, no excuse tickets, no teacher views — those are later phases.

</domain>

<decisions>
## Implementation Decisions

### Schedule Navigation
- **D-01:** Horizontal swipe gesture between days + tappable day tabs at top (Mon–Sat only, no Sunday). Uses a swipeable tab strip component.
- **D-02:** Week navigation via swipe — header shows "← 7–12 апр →" with arrow buttons. Swiping the entire tab strip changes week.
- **D-03:** Default view opens on today's tab with auto-scroll to current/next lesson. Past lessons visible above by scrolling up.
- **D-04:** Floating "Сегодня" pill button appears when viewing a non-current week. Tap snaps back to current week + today's tab. Disappears when already on current week.
- **D-05:** TanStack Query prefetches adjacent days for smooth swiping. Schedule API returns weekly data.

### Check-in Flow
- **D-06:** "Отметиться" button lives directly on the active lesson card in the schedule view. One tap from schedule, no separate navigation needed.
- **D-07:** Instant GPS capture + submit: tap → spinner on button → browser requests GPS (permission prompt if needed) → coords sent to backend → success/fail toast. Single action, ~2-3 seconds.
- **D-08:** After successful check-in: status badge changes from "Идёт" to "Отмечен" (green), "Отметиться" button replaced with checkmark icon, success toast appears briefly.
- **D-09:** GPS permission denial: inline toast "Нет доступа к GPS. Разрешите доступ в настройках браузера". Button re-enables for retry after user fixes settings.
- **D-10:** /checkin bottom nav tab shows dedicated screen with the current active lesson + check-in button. When no active lesson, shows empty state: "Сейчас нет активных пар. Следующая: [Subject] в [time]".

### Real-time Updates
- **D-11:** STOMP WebSocket subscription shared across schedule cards and /checkin screen. Wherever an active lesson is displayed, it gets live updates.
- **D-12:** Attendance count (e.g. "12/25 отметились") updates with subtle number flip animation when STOMP `attendance.marked` event arrives. No toast per person — card just updates.
- **D-13:** Silent auto-reconnect with exponential backoff (1s, 2s, 4s...). No notification unless disconnected >30s, then subtle indicator. On reconnect, refetch current lesson data to catch missed events.

### Offline Data Strategy
- **D-14:** Stale-while-revalidate with 1hr max stale for schedule data. TanStack Query `staleTime: 60 * 60 * 1000` for schedule queries (overrides global 5min default).
- **D-15:** Subtle top banner "Офлайн · обновлено 15 мин назад" using OfflineBanner component from Phase 29. Data still usable, just flagged as stale.
- **D-16:** Auto-refetch on reconnect (TanStack Query `refetchOnReconnect: true`) + pull-to-refresh as manual trigger. Stale banner disappears after successful refetch.
- **D-17:** Check-in button disabled when offline with text "Нет подключения" below it. GPS check-in requires network — no offline queue.

### Claude's Discretion
- Swipeable tab strip library choice / custom implementation
- Exact lesson card layout and information density
- STOMP client library and connection management details
- Pull-to-refresh implementation approach
- Loading skeleton design for schedule
- Error toast styling and timing
- Week data prefetching strategy

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### UI Design Contract
- `.planning/phases/30-schedule-check-in-ui/30-UI-SPEC.md` — Visual and interaction contract: spacing, typography, color, copy, component registry. All visual decisions locked here.

### Backend APIs (schedule + attendance)
- `services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/api/ScheduleApi.java` — Schedule REST endpoints used by PWA
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/api/CheckInApi.java` — Check-in endpoint contract
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/dto/CheckInRequest.java` — GPS coords DTO
- `services/notification-web/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java` — STOMP endpoint config for real-time

### PWA foundation (Phase 29 output)
- `frontends/pwa/src/shared/components/AppShell.tsx` — App shell with BottomNav, Motion page transitions
- `frontends/pwa/src/shared/components/BottomNav.tsx` — 4-tab nav (Главная, Расписание, Отметка, Профиль)
- `frontends/pwa/src/shared/components/OfflineBanner.tsx` — Offline banner component to extend with stale time
- `frontends/pwa/src/shared/lib/axios.ts` — Axios instance with silent refresh interceptor
- `frontends/pwa/src/shared/lib/queryClient.ts` — TanStack Query client (global 5min staleTime)
- `frontends/pwa/src/shared/hooks/useNetworkStatus.ts` — Network status hook
- `frontends/pwa/src/main.tsx` — Router config with /schedule and /checkin placeholders

### Design system
- `docs/design-decisions.md` — Phosphor Icons, Motion, manifest, iOS onboarding, branding

### Requirements
- `.planning/REQUIREMENTS.md` §Schedule — SCHED-01, SCHED-02, SCHED-03
- `.planning/REQUIREMENTS.md` §Check-in — CHKIN-01, CHKIN-02, CHKIN-03

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **shadcn/ui components**: Button, Card, CardContent, Input, Label, Alert, AlertDescription, Separator — all installed
- **OfflineBanner**: Existing component, extend with stale time display
- **useNetworkStatus hook**: Returns online/offline status, use for disabling check-in
- **apiClient (Axios)**: Pre-configured with `withCredentials: true` and silent refresh interceptor
- **queryClient**: TanStack Query with global defaults, override staleTime per query
- **LoadingSpinner**: Existing spinner component for suspense fallbacks
- **Motion (framer-motion)**: Already used in AppShell for page transitions, extend for card animations

### Established Patterns
- **Route structure**: `createBrowserRouter` in main.tsx with lazy-loaded feature components under `<ProtectedRoute>` + `<AppShell>`
- **Feature folders**: `src/features/{domain}/` with components, hooks, API layer
- **Phosphor Icons**: bold weight for inactive, fill weight for active (BottomNav pattern)
- **Touch targets**: minimum 44px height/width (BottomNav pattern)
- **Tailwind**: shadcn base-nova preset with OKLCH color tokens in index.css

### Integration Points
- **Router**: Replace `<HomePlaceholder />` at `/schedule` and `/checkin` with real feature components
- **Gateway**: API calls go through `/api/schedule/**` and `/api/attendance/**` via apiClient
- **STOMP**: New WebSocket connection to notification-web service (port 9094) for `attendance.marked` events
- **Auth headers**: Gateway injects `X-User-Id`, `X-Group-Id` from JWT — used for schedule and check-in scoping

</code_context>

<specifics>
## Specific Ideas

- /checkin tab is a dedicated screen (not just a redirect to schedule) — shows active lesson with check-in button, or empty state with next lesson info when no active lesson
- Week navigation is important — students need to see next week's schedule for planning
- "Сегодня" floating pill for quick return from other weeks
- No per-person check-in toasts — just animate the counter to avoid noise in large groups

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 30-schedule-check-in-ui*
*Context gathered: 2026-04-06*
