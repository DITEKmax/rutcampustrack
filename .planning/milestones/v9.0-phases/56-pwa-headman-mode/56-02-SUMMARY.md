---
phase: 56
plan: "02"
subsystem: frontend-pwa
tags: [headman, react, tanstack-query, react-router, typescript, tdd, vitest]
dependency_graph:
  requires: [56-01]
  provides:
    - headmanApi.ts with 16 TanStack Query hooks for all headman endpoints
    - types.ts with 11 TypeScript contracts for headman domain
    - GroupHub component with 7 hub cards at /group route
    - 7 placeholder detail pages at /group/{overview,students,subjects,journal,excuses,late-checkin,stats}
    - 8 lazy-loaded routes registered in main.tsx under protected / parent
  affects:
    - frontends/pwa/src/main.tsx
tech_stack:
  added: []
  patterns:
    - TanStack Query useQuery with staleTime tiers (5min for journal/members, 24h for subjects/thresholds/teachers)
    - TanStack Query useMutation with cache invalidation on success
    - Graceful 404 degradation (retry: false + catch 404 → return []) for excuses/late-checkins
    - TDD RED→GREEN→ for GroupHub (failing test committed before implementation)
    - React lazy() + Suspense with LoadingSpinner fallback for all 8 new routes
    - AnimatePresence + motion stagger entrance (40ms delay per card, 200ms easeOut)
key_files:
  created:
    - frontends/pwa/src/features/headman/shared/types.ts
    - frontends/pwa/src/features/headman/shared/headmanApi.ts
    - frontends/pwa/src/features/headman/group-hub/GroupHub.tsx
    - frontends/pwa/src/features/headman/group-hub/GroupHub.test.tsx
    - frontends/pwa/src/features/headman/overview/Overview.tsx
    - frontends/pwa/src/features/headman/students/StudentsList.tsx
    - frontends/pwa/src/features/headman/subjects/SubjectsList.tsx
    - frontends/pwa/src/features/headman/journal/JournalPage.tsx
    - frontends/pwa/src/features/headman/excuses/ExcusesPage.tsx
    - frontends/pwa/src/features/headman/late-checkin/LateCheckinPage.tsx
    - frontends/pwa/src/features/headman/stats/StatsPage.tsx
  modified:
    - frontends/pwa/src/main.tsx
decisions:
  - "Placeholder detail pages are REPLACEMENTS not extensions — Wave 3 plans 56-03/04/05 will overwrite them entirely"
  - "usePendingExcuses + usePendingLateCheckins use retry: false with catch-404 pattern per D-10 graceful degradation"
  - "GroupHub uses AnimatePresence with 40ms stagger per card for visual entrance animation per UI-SPEC"
  - "No client-side route guards for /group/* — backend enforces authorization via RoleCheckAspect (per threat model T-56-04)"
metrics:
  duration_minutes: 20
  completed_date: "2026-04-13"
  tasks_completed: 3
  tasks_total: 3
  files_created: 11
  files_modified: 1
requirements:
  - PWA-HEAD-02
---

# Phase 56 Plan 02: GroupHub Shell — Headman API Contracts + Router Summary

**One-liner:** headmanApi.ts exports 16 typed TanStack Query hooks mirroring Angular's HeadmanApiService; GroupHub renders 7 navigation cards with AnimatePresence stagger; 8 lazy-loaded routes registered in main.tsx providing the complete /group/* navigation tree for Wave 3.

## What Was Built

### types.ts (frontends/pwa/src/features/headman/shared/types.ts)

All type contracts downstream Wave 3 plans import:

```typescript
GroupMember    { id, fullName, login, isHeadman?, isAssistant? }
Teacher        { id, fullName, login }
Subject        { id, name, teacherId?, teacherName? }
AttendanceStatus = 'present' | 'absent' | 'excused' | 'free_attendance' | 'cancelled'
JournalCell    { lessonId, studentId, studentName, date, status: AttendanceStatus }
ResolvedThreshold { subjectId, groupId, minPercentage, source: 'global'|'group'|'subject' }
Assistant      { id, studentId, fullName, permissions: string[] }
AssistantPermission = 'manage_students' | 'manage_subjects' | 'manage_excuses' | 'manage_stats'
PendingExcuse  { id, studentName, reason, createdAt }
PendingLateCheckin { id, studentName, lessonId, createdAt }
TodayLesson    { lessonId, subjectName, startsAt, endsAt, room? }
```

11 exports total. **These interfaces are stable — Wave 3 plans import them directly.**

### headmanApi.ts — Exported Hook Names (stable API surface for Wave 3)

All hooks available at `@/features/headman/shared/headmanApi`:

| Hook | Type | Endpoint | staleTime |
|------|------|----------|-----------|
| `useGroupMembers(page?, size?)` | useQuery→GroupMember[] | GET /academic/groups/my/members | 5 min |
| `useGroupSubjects(page?, size?)` | useQuery→Subject[] | GET /academic/subjects | 24 h |
| `useGroupTeachers(groupId)` | useQuery→Teacher[] | GET /academic/groups/{id}/teachers | 24 h |
| `useJournal(groupId, subjectId, dateFrom, dateTo)` | useQuery→JournalCell[] | GET /attendance/reports/journal | 5 min |
| `useResolveThreshold(groupId, subjectId)` | useQuery→ResolvedThreshold | GET /academic/thresholds/resolve | 24 h |
| `useTodayLesson(groupId)` | useQuery→TodayLesson\|null | GET /schedule/groups/{id}/lessons | 5 min |
| `usePendingExcuses(groupId)` | useQuery→PendingExcuse[] | GET /attendance/excuses/pending | 5 min, retry:false |
| `usePendingLateCheckins(groupId)` | useQuery→PendingLateCheckin[] | GET /attendance/late-checkins/pending | 5 min, retry:false |
| `useMarkAttendance()` | useMutation | PUT /attendance/lessons/{id}/students/{id} | invalidates ['journal'] |
| `useSetSubjectThreshold()` | useMutation | PUT /academic/thresholds/subject?subjectId= | invalidates ['threshold'] |
| `useCreateSubject()` | useMutation | POST /academic/subjects | invalidates ['groupSubjects'] |
| `useUpdateSubject()` | useMutation | PUT /academic/subjects/{id} | invalidates ['groupSubjects'] |
| `useDeleteSubject()` | useMutation | DELETE /academic/subjects/{id} | invalidates ['groupSubjects'] |
| `useCreateAssistant()` | useMutation | POST /academic/assistants | invalidates ['groupMembers'] |
| `useDeleteAssistant()` | useMutation | DELETE /academic/assistants/{id} | invalidates ['groupMembers'] |
| `mapHeadmanApiError(status)` | function→string | — | 403/404/422/429/default Russian |

### GroupHub.tsx (/group route)

7 hub cards in responsive grid (`grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6`):

| Route | Title | Phosphor Icon | Meta text |
|-------|-------|---------------|-----------|
| /group/overview | Обзор | ChartBar fill | "Члены группы: {N}" |
| /group/students | Студенты | Users fill | "{N} студентов в группе" |
| /group/subjects | Предметы | BookOpen fill | "{M} предметов" |
| /group/journal | Журнал | Table fill | "Отметить посещение" |
| /group/excuses | Пропуски | FileText fill | "Запросы отпусков" |
| /group/late-checkin | Запросы отметки | Clock fill | "Запросы опоздалой отметки" |
| /group/stats | Статистика | Percent fill | "Посещаемость группы" |

Each card: `min-h-[120px]`, `bg-[var(--bg-secondary)]`, `border border-[var(--border-subtle)]`, `rounded-lg p-4`, CaretRight 16px on right, motion entrance animation (opacity 0→1, scale 0.95→1, 200ms easeOut, 40ms stagger per card).

### Router Extension (main.tsx diff)

Before: 4 child routes (home, schedule, checkin, profile)
After: 12 child routes (+8 headman routes)

```
group              → GroupHub (lazy)
group/overview     → Overview (lazy, placeholder)
group/students     → StudentsList (lazy, placeholder)
group/subjects     → SubjectsList (lazy, placeholder)
group/journal      → JournalPage (lazy, placeholder)
group/excuses      → ExcusesPage (lazy, placeholder)
group/late-checkin → LateCheckinPage (lazy, placeholder)
group/stats        → StatsPage (lazy, placeholder)
```

### Placeholder Components

7 placeholder components exist at the paths listed above. Each contains:
- `<Link to="/group">` back button with ArrowLeft icon
- Page heading matching the Russian route title
- "Загрузка…" body text

**These placeholders WILL BE REPLACED (not extended) by Wave 3 plans 56-03/04/05.** They exist solely to make every /group/* route navigable and to unblock Wave 3 stub imports.

## Test Counts

| Before Plan 56-02 | After Plan 56-02 |
|-------------------|------------------|
| 68 tests, 12 files | 71 tests, 13 files |

New tests added: `GroupHub.test.tsx` — 3 tests (heading, 7 links with correct hrefs, Russian titles).

All 71 tests pass with 0 failures. No existing tests were deleted or modified.

## Frozen Directories Confirmation

No modifications made to:
- `features/home/` — untouched
- `features/schedule/` — untouched
- `features/checkin/` — untouched
- `features/profile/` — untouched
- `features/push/` — untouched
- `features/auth/` — untouched

## Threat Model Compliance

| Threat | Mitigation | Status |
|--------|-----------|--------|
| T-56-06: useMarkAttendance with fabricated lessonId/userId | Backend validates JWT headman rights over group/lesson | Client uses only API-returned lessonIds |
| T-56-07: Unbounded retries on 404 | `retry: false` on usePendingExcuses + usePendingLateCheckins | Implemented |
| T-56-08: XSS via API response text | React auto-escapes text content; no dangerouslySetInnerHTML used | No violation |
| T-56-04: Client-side route guard | No guard added — backend enforces via RoleCheckAspect | Intentional |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

7 placeholder detail pages (Overview, StudentsList, SubjectsList, JournalPage, ExcusesPage, LateCheckinPage, StatsPage) render static "Загрузка…" text with no data source wired. These are **intentional stubs** — Wave 3 plans 56-03/04/05 will replace these files entirely with real implementations. The GroupHub's navigation goal is achieved: all 7 routes are reachable and render a back link.

## Threat Surface Scan

No new network endpoints or auth paths introduced beyond what is documented in the plan's threat model. All headmanApi calls traverse the existing API Gateway with Bearer token. No new trust boundary crossings.

## Self-Check: PASSED

| Item | Status |
|------|--------|
| frontends/pwa/src/features/headman/shared/types.ts | FOUND |
| frontends/pwa/src/features/headman/shared/headmanApi.ts | FOUND |
| frontends/pwa/src/features/headman/group-hub/GroupHub.tsx | FOUND |
| frontends/pwa/src/features/headman/group-hub/GroupHub.test.tsx | FOUND |
| 7 placeholder detail page files | FOUND |
| frontends/pwa/src/main.tsx (8 new routes) | VERIFIED |
| Commit 954d630 (Task 1 — types + api) | VERIFIED |
| Commit cc31507 (Task 2 RED — failing test) | VERIFIED |
| Commit 6b16cdd (Task 2 GREEN — GroupHub impl) | VERIFIED |
| Commit a8774f2 (Task 3 — placeholders + router) | VERIFIED |
| npx vitest run: 71/71 pass | PASSED |
| npx tsc --noEmit: exits 0 | PASSED |
| npm run build: exits 0 | PASSED |
