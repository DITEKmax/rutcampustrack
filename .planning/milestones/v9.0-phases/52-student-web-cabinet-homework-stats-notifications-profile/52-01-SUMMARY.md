---
phase: 52-student-web-cabinet-homework-stats-notifications-profile
plan: "01"
subsystem: web-panel/student-shared-layer
tags: [angular, routing, stomp, notifications, badge, api]
dependency_graph:
  requires: []
  provides:
    - StudentNotificationBadgeService (singleton badge counter)
    - StudentStompService.onAnyEvent$ (all STOMP envelopes)
    - StudentApiService homework methods (getHomeworks, mark/unmark, getActiveSemesterId)
    - AuthApi.changePassword
    - HomeworkItem + NotificationItem interfaces
    - 4 student routes (homework, stats, notifications, profile)
    - 4 sidebar nav items with bell badge overlay
  affects:
    - frontends/web-panel routing tree
    - frontends/web-panel sidebar
tech_stack:
  added: []
  patterns:
    - Angular signal-based singleton badge counter
    - STOMP envelope fan-out with type-based badge increment
    - Lazy-loaded Angular routes for student cabinet pages
key_files:
  created:
    - frontends/web-panel/src/app/features/student/shared/student-notification-badge.service.ts
    - frontends/web-panel/src/app/features/student/homework/student-homework.component.ts
    - frontends/web-panel/src/app/features/student/stats/student-stats.component.ts
    - frontends/web-panel/src/app/features/student/notifications/student-notifications.component.ts
    - frontends/web-panel/src/app/features/student/profile/student-profile.component.ts
  modified:
    - frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts
    - frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts
    - frontends/web-panel/src/app/features/student/shared/student-api.service.ts
    - frontends/web-panel/src/app/core/auth/auth.api.ts
    - frontends/web-panel/src/app/app.routes.ts
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.html
    - frontends/web-panel/src/app/layout/sidebar/sidebar.component.css
decisions:
  - Placeholder components created for homework/stats/notifications/profile to satisfy Angular's compile-time lazy import resolution; full implementations delivered in plans 02-04
metrics:
  duration: ~25min
  completed: 2026-04-09
  tasks_completed: 2
  files_changed: 13
---

# Phase 52 Plan 01: Shared Foundation — Routes, Sidebar, Services Summary

**One-liner:** Shared Angular layer for Phase 52 student cabinet: 4 lazy routes, 4 sidebar items with STOMP-driven bell badge, HomeworkItem/NotificationItem types, API service homework methods, and AuthApi.changePassword.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Types + API extensions + notification badge service | f09ac65 | student-schedule.types.ts, student-stomp.service.ts, student-api.service.ts, auth.api.ts, student-notification-badge.service.ts |
| 2 | Routes + sidebar nav items + bell badge wiring | e31fc58 | app.routes.ts, sidebar.component.ts, sidebar.component.html, sidebar.component.css, 4 placeholder components |

## What Was Built

### StudentNotificationBadgeService
Singleton (`providedIn: 'root'`) with a private `signal(0)` counter, `readonly unreadCount` (asReadonly signal), `increment()`, and `reset()` methods. Wired into SidebarComponent to drive the bell badge, and into StudentStompService to count STORED_TYPES events.

### StudentStompService extensions
- Module-level `STORED_TYPES` constant: `['lesson.started', 'lesson.cancelled', 'homework.published', 'homework.updated', 'attendance.marked']`
- `onAnySubject` (private Subject) + `onAnyEvent$` (public Observable) — emits ALL STOMP envelopes
- `badgeService.increment()` called for each envelope whose `type` is in `STORED_TYPES`
- `markedSubject.next()` still called exclusively for `attendance.marked` (backward compat)

### StudentApiService extensions
- `getHomeworks(groupId, semesterId)` — GET `/api/academic/homeworks?groupId=&semesterId=&size=50`, unwraps `homeworkResponseList`
- `markHomeworkComplete(id)` — POST `/api/academic/homeworks/{id}/complete`
- `unmarkHomeworkComplete(id)` — DELETE `/api/academic/homeworks/{id}/complete`
- `getActiveSemesterId()` — GET `/api/academic/semesters?size=100`, finds `active=true`, returns id or null

### AuthApi extension
- `changePassword(currentPassword, newPassword)` — POST `/api/auth/change-password`

### Type declarations
- `HomeworkItem` interface added to `student-schedule.types.ts` (id, title, description, link, subjectId, groupId, semesterId, publishedBy, completed, createdAt)
- `NotificationItem` interface added (id, type, payload, receivedAt, read)

### Routing
4 new lazy routes registered in `/student` children array before the default redirect:
- `homework` → `StudentHomeworkComponent`
- `stats` → `StudentStatsComponent`
- `notifications` → `StudentNotificationsComponent`
- `profile` → `StudentProfileComponent`

### Sidebar
- 4 new STUDENT nav items: Домашние задания (ph-notebook), Статистика (ph-chart-bar), Уведомления (ph-bell), Профиль (ph-user-circle)
- `StudentNotificationBadgeService` injected; `unreadCount` signal exposed
- Bell icon wrapped in `position: relative` span; `notification-badge` span conditionally rendered when `unreadCount() > 0`, shows count or `9+`
- `.notification-badge` CSS added to `sidebar.component.css` (absolute positioned, accent background, 16px height)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Created placeholder components for lazy routes**
- **Found during:** Task 2 build verification
- **Issue:** Angular's compiler resolves lazy `import()` paths at build time. The 4 new route targets (`student-homework`, `student-stats`, `student-notifications`, `student-profile`) did not exist, causing 4 `TS2307: Cannot find module` errors.
- **Fix:** Created minimal standalone placeholder components (`template: '<p>... в разработке</p>'`) in each directory. Plans 02-04 will replace these with full implementations.
- **Files modified:** Created 4 new `.component.ts` files in `homework/`, `stats/`, `notifications/`, `profile/`
- **Commit:** e31fc58

## Known Stubs

| File | Description |
|------|-------------|
| `features/student/homework/student-homework.component.ts` | Placeholder — full implementation in Plan 02 |
| `features/student/stats/student-stats.component.ts` | Placeholder — full implementation in Plan 02 |
| `features/student/notifications/student-notifications.component.ts` | Placeholder — full implementation in Plan 03 |
| `features/student/profile/student-profile.component.ts` | Placeholder — full implementation in Plan 04 |

These stubs are intentional — they exist solely to satisfy the Angular compiler for the route registrations. Plans 02-04 will replace them with complete page components.

## Threat Surface Scan

No new network endpoints, auth paths, or file access patterns introduced beyond what is in the plan's threat model. The `changePassword` method in `AuthApi` is covered by T-52-03 (accept disposition — Bearer token auth interceptor handles it). `onAnyEvent$` is covered by T-52-02.

## Self-Check: PASSED

All files exist on disk. Both commits (f09ac65, e31fc58) verified in git log.
