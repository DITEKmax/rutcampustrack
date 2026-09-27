# Student notification destinations — 2026-09-27

## Goal

An active online STUDENT in PWA or TMA opens the exact request, lesson, or homework item from a persisted notification, sees the current server-authorized entity, and can return or gets an explicit unavailable/retry state.

## Context/evidence

- Worktree: `.agent/worktrees/admin-group-promotion-20260927`, branch `codex/notification-student-routes-20260927`; frozen base `fb45dc0d137b269c35595d7e370e774c21018077`.
- Canonical project rules read at `.agent/orchestration-v2/RULES.md`, SHA-256 `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`; `CURRENT.md`, frontend and test instructions, `rutcampustrack-design`, and `rct-verification` were applied.
- Existing frontend patterns at the base: homework notification intent in PWA/TMA `App.vue` and `StudentFeatureOwner.vue`; `StudentApi.getRequest(id)`; request `mapDetail` and controller identity guards; `useAttendance` keyed to `scope.semesterId`; `findAttendanceLesson` exact-ID helper.
- Producer/history source was checked: `ExcuseEventPublisher` writes `ticket_id` and the student `user_id`; `LateCheckinEventPublisher` writes `request_id` and the student `user_id`; `HomeworkDueReminderEvent` nests `HomeworkNotificationItem`; `NotificationHistoryEventProcessor` maps those persisted event types and maps weekly digest separately.

## Relevant scope

Student notification parser/screen and one owner-bound intent in both PWA/TMA apps; exact request detail loading/rendering; exact current-semester attendance selection/focus; existing homework notification route; focused parser/controller tests. No change to shared navigation modules, backend, other role owners, configs, locks, or generated files.

Product inventory:

- `frontends/mobile-core/src/features/notifications/notifications-client.ts`
- `frontends/mobile-core/src/features/notifications/notifications-client.test.ts`
- `frontends/mobile-core/src/features/notifications/NotificationsScreen.vue`
- `frontends/mobile-core/src/features/requests/requests-controller.ts`
- `frontends/mobile-core/src/features/requests/requests-controller.test.ts`
- `frontends/mobile-core/src/features/requests/use-requests.ts`
- `frontends/mobile-core/src/features/requests/RequestsScreen.vue`
- `frontends/mobile-core/src/features/requests/RequestCard.vue`
- `frontends/mobile-core/src/features/attendance/AttendanceScreen.vue`
- `frontends/mobile-core/src/features/attendance/AttendanceLessonRow.vue`
- `frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue`
- `frontends/pwa-vue/src/App.vue`
- `frontends/tma-vue/src/App.vue`

## Required behavior

- Use a typed homework/request/lesson target union and one App intent bound to the current auth generation and owner key. Only active, online STUDENT can open a target; history remains available without actions for unsupported events/roles.
- EXCUSE request events use string `payload.ticket_id`; LATE_CHECKIN events use string `payload.request_id`. Load directly with `StudentApi.getRequest(id)`, verify returned `summary.id` and expected `summary.kind`, then render the existing `RequestCard` through its existing action/read-only/attachment guards. Notification payload reason/files are not rendered as authority.
- LESSON_STARTED, LESSON_CANCELLED, and ATTENDANCE_MARKED_BY_HEADMAN use a positive safe `lesson_id`. Resolve only through current-semester attendance, use the returned lesson date, select the day, and focus the exact lesson row. Missing IDs, no current semester, and 403/404 are explicit unavailable states; other fetch failures can retry. Do not alter attendance or request eligibility.
- HOMEWORK_DUE_REMINDER reads nested `payload.homework.homework_id/lesson_date` and uses the existing exact homework route. HOMEWORK_WEEKLY_DIGEST remains aggregate/no-action. Preserve existing published/updated homework guards and return behavior.
- Back, new intent, owner/session/role change, logout, and going offline invalidate late results and clear the intent.

## Constraints

No list-page traversal for request detail, no authority from notification text/attachments, no current-date fallback for missing lessons, no automatic attendance mutation, no navigation redesign, and no backend/config/generated changes. Preserve the three foreign untracked `.agent/notification-*homework*-2026-09-27.md` notes in this worktree.

## Existing patterns

`StudentFeatureOwner.vue` owns student data and navigation; the Apps own session generation and role gating. Request detail passes through `RequestsController.mapDetail` and current-context checks. Attendance uses the existing current-semester query and `findAttendanceLesson`. Target cards reuse `RequestCard`; route focus uses existing heading/lesson row elements and existing PCSS tokens.

## Acceptance criteria

- Persisted request event opens only the matching server detail ID and expected request kind; a mismatched ID/kind is unavailable, and late results cannot replace a newer target/session.
- Persisted lesson event opens only the matching ID in current-semester server attendance, selects its authoritative date, and focuses its row; unresolved old-semester/no-semester/403/404 is explicit unavailable.
- Due reminder opens its nested homework ID/date through the existing validated route; weekly digest has no action.
- PWA/TMA expose one online STUDENT-only target action; unsupported events remain history-only. Back and stale-result invalidation work through the existing owner.
- Existing request mutation/attachment guards and homework range/offline/focus rules remain in their current owners.

## Verification

Environment: Windows PowerShell, frontend workspace `frontends/`, branch above. Initial batch exit codes: mobile-core typecheck `0`; PWA typecheck `1`; TMA typecheck `1`; scoped ESLint `1`; focused tests `0`; diff-check `0`. PWA/TMA reported the same Vue discriminated-union narrowing issue plus the inferred route `Set` type. ESLint reported template indentation/newline warnings in the changed target sections. The request view was split into explicit status variants, the route set was typed `Set<string>`, and `npm exec -- eslint mobile-core/src/features/requests/RequestsScreen.vue mobile-core/src/shared/components/StudentFeatureOwner.vue mobile-core/src/features/attendance/AttendanceLessonRow.vue --fix` exited `0` to format the three affected Vue templates; then the whole scoped batch was repeated.

Final batch, all exit code `0`:

- `npm run typecheck --workspace @rct/mobile-core`
- `npm run typecheck --workspace @rct/pwa-vue`
- `npm run typecheck --workspace @rct/tma-vue`
- `npm exec -- eslint mobile-core/src/features/notifications/NotificationsScreen.vue mobile-core/src/features/notifications/notifications-client.ts mobile-core/src/features/notifications/notifications-client.test.ts mobile-core/src/features/requests/RequestCard.vue mobile-core/src/features/requests/RequestsScreen.vue mobile-core/src/features/requests/requests-controller.ts mobile-core/src/features/requests/requests-controller.test.ts mobile-core/src/features/requests/use-requests.ts mobile-core/src/features/attendance/AttendanceScreen.vue mobile-core/src/features/attendance/AttendanceLessonRow.vue mobile-core/src/shared/components/StudentFeatureOwner.vue pwa-vue/src/App.vue tma-vue/src/App.vue --max-warnings=0`
- `npm exec -- vitest run --root mobile-core src/features/notifications/notifications-client.test.ts src/features/requests/requests-controller.test.ts` — 2 files, 18 tests passed; covers supported persisted IDs, unsupported/aggregate payloads, direct request lookup without list traversal, identity/kind mismatch, and stale context.
- `git diff --check`

Runtime evidence: no live authenticated PWA/TMA session or server-backed browser run was available in this worktree. Vitest ran in its Node environment (`environment 0ms`); it is source-level evidence, not live runtime acceptance. No Gradle, Docker, full suite, or unrelated verification was run.

Terminal evidence note: the PowerShell profile emitted PSReadLine terminal-capability warnings; Git status also reported that the user's global ignore file was inaccessible. These environment messages did not affect explicit command exit codes and were not used to change product code.

## Do not

Do not expand into notification delivery/WebSocket work, non-student routes, root dock/navigation redesign, shared navigation files, backend changes, or a broad test campaign. Live API/UI acceptance remains a separate integration step.
