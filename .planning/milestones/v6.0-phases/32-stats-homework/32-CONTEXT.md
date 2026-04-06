# Phase 32: Stats + Homework - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Students can review their attendance statistics per subject with red zone warnings, view individual attendance records, and view and track homework completion. Delivers: AttendanceStatsPage, AttendanceRecordsPage, HomeworkPage, BottomNav restructure. No excuse tickets, no teacher views, no headman marking — those are future phases.

</domain>

<decisions>
## Implementation Decisions

### Homework Completion Storage
- **D-01:** Use server-side completion via existing `POST /academic/homeworks/{id}/complete` and `DELETE /academic/homeworks/{id}/complete` endpoints. The `HomeworkResponse.completed` boolean is per-student, populated from `HomeworkCompletion` table. This overrides the UI-SPEC assumption of localStorage-only storage.
- **D-02:** Optimistic UI toggle: checkbox state updates immediately on tap, mutation sent to backend; on error — revert checkbox and show inline error text `«Не удалось сохранить»` in `text-destructive text-xs`.

### BottomNav Restructure
- **D-03:** Replace «Главная» placeholder tab with «Статистика» (ChartBar icon). Add «Задания» (ClipboardText icon) as 5th tab. New tab order: Статистика, Расписание, Отметка, Задания, Профиль. Remove `/home` route.
- **D-04:** Default landing after login changes from `/home` to `/schedule` (the most-used screen).

### Red Zone Threshold Source
- **D-05:** Fetch effective threshold via single `GET /api/academic/thresholds/resolve?groupId={groupId}` call (no subjectId — gets group-level default). Compare each subject's attendance percentage against this threshold to determine red zone status.
- **D-06:** If threshold API returns no configured threshold (404 or null), hide red zone indicators entirely — don't assume a default percentage.

### Offline Caching Strategy
- **D-07:** Same stale-while-revalidate pattern as Phase 30 schedule: `staleTime: 60 * 60 * 1000` (1hr) for both stats and homework TanStack Query hooks.
- **D-08:** `refetchOnReconnect: true` for all queries. OfflineBanner shows stale time when offline. Pull-to-refresh available on both screens.

### Claude's Discretion
- Exact TanStack Query hook implementation details (queryKey structure, error handling)
- Loading skeleton vs spinner choice per screen
- How to obtain student's groupId and semesterId for homework API call (from auth context or separate endpoint)
- AttendanceRecordsPage: whether to group records by date or show flat list
- Homework sort order refinement (undone first by deadline, done below)
- Stagger animation timing for list items

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### UI Design Contract
- `.planning/phases/32-stats-homework/32-UI-SPEC.md` — Visual and interaction contract: layouts, spacing, typography, color, icons, copywriting, animation. **Note: HW-02 localStorage approach is overridden by D-01/D-02 (server-side completion).**

### Attendance Backend API
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/ReportApi.java` — `GET /attendance/reports/student/stats` (ATT-01/02) and `GET /attendance/reports/student/records?subjectId=X` (ATT-03)
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/StudentStatsResponse.java` — Response with `List<SubjectStats>` + `OverallStats`
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/SubjectStats.java` — Per-subject: subjectId, subjectName, total, attended, absent, excused, percentage
- `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/AttendanceRecordEntry.java` — Per-record: lessonId, subjectId, lessonDate, lessonNumber, status, symbol, source

### Homework Backend API
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/HomeworkApi.java` — `GET /academic/homeworks?groupId=X&semesterId=Y` (HW-01), `POST /{id}/complete` and `DELETE /{id}/complete` (HW-02)
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/homework/HomeworkResponse.java` — Fields: id, title, description, link, subjectId, groupId, semesterId, publishedBy, completed, createdAt

### Threshold API
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/ThresholdApi.java` — `GET /academic/thresholds/resolve?groupId=X` returns effective threshold percentage
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/threshold/ResolvedThresholdResponse.java` — Response with resolved threshold value

### PWA Foundation (Phase 29-31 output)
- `frontends/pwa/src/shared/components/BottomNav.tsx` — Current 4-tab nav, needs restructure to 5 tabs
- `frontends/pwa/src/shared/components/AppShell.tsx` — App shell with Motion page transitions
- `frontends/pwa/src/shared/components/LoadingSpinner.tsx` — Reusable loading indicator
- `frontends/pwa/src/shared/components/OfflineBanner.tsx` — Offline stale-time banner
- `frontends/pwa/src/shared/lib/axios.ts` — Axios instance with JWT interceptor
- `frontends/pwa/src/shared/lib/queryClient.ts` — TanStack Query client (global staleTime)
- `frontends/pwa/src/features/schedule/StatusBadge.tsx` — Attendance status badge (б/н/у/сп colors)
- `frontends/pwa/src/features/schedule/types.ts` — LessonResponse, AttendanceStatus types
- `frontends/pwa/src/main.tsx` — Router config, needs new routes for /stats and /homework

### Design System
- `docs/design-decisions.md` — Phosphor Icons, Motion, branding

### Requirements
- `.planning/REQUIREMENTS.md` §Attendance — ATT-01, ATT-02, ATT-03
- `.planning/REQUIREMENTS.md` §Homework — HW-01, HW-02

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `StatusBadge`: Already supports attendance statuses (present/absent/excused/free_attendance) with correct colors — reuse directly for AttendanceRecordRow
- `Card, CardHeader, CardContent`: shadcn components — use for subject stat rows and homework items
- `LoadingSpinner`: Existing loading indicator — use for all page load states
- `OfflineBanner`: Shows stale time when offline — extend pattern for stats/homework
- `Button`: shadcn button — use for retry buttons in error states
- Axios instance with JWT interceptor: All API calls already authenticated
- TanStack Query client: Global config with 5min staleTime — override to 1hr for stats/homework

### Established Patterns
- Feature folder structure: `src/features/{domain}/` with components, hooks, API layer (Phase 29 D-08)
- TanStack Query hooks per feature: `useSchedule`, `useStompCheckin` — follow same pattern for `useStudentStats`, `useHomework`
- Route structure: React Router v7 with lazy loading, nested under AppShell (Phase 29 D-07)
- Motion stagger animation: Used in SchedulePage lesson list — same pattern for stats/homework lists

### Integration Points
- `BottomNav.tsx` — Replace Главная tab, add Задания tab
- `main.tsx` — Add `/stats`, `/stats/:subjectId`, `/homework` routes; change default redirect from `/home` to `/schedule`
- `AppShell.tsx` — No changes needed, wraps all routes already
- Gateway routing: `/api/attendance/**` and `/api/academic/**` routes already configured

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches following established PWA patterns.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 32-stats-homework*
*Context gathered: 2026-04-06*
