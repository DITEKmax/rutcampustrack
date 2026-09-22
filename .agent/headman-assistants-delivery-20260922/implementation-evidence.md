# HEADMAN assistant management — implementation evidence

## Frozen scope

- **Goal:** a HEADMAN can read the active students of the authenticated group,
  assign an active student as an assistant, change or revoke the assignment,
  and see the durable result after reload in PWA and TMA.
- **Backend:** preserve `POST/GET/PATCH/{id}/permissions/DELETE/{id}` under
  `/academic/assistants`; add the server-owned permission catalog and validate
  the selected student through an active durable STUDENT grant in the actor's
  group. The request `groupId` never becomes the authority source.
- **Authorization extension:** add signed-internal-context-only Academic
  `CheckAssistantPermission` with request `(group_id, permission)` and response
  `(allowed)`. The actor is taken from validated `x-internal-token` claims;
  durable active HEADMAN grants and active assistant rows are re-read on every
  call. No cache, synthetic token, or `IsHeadman` semantic change.
- **Consumers:** attendance marking uses `MARK_ATTENDANCE`; attendance
  excuses/requests use `MANAGE_EXCUSES`; group reports and headman weekly
  reports use `VIEW_STATS`; schedule cancel/restore use `CANCEL_LESSONS`.
  Geo/block operations and the admin dashboard remain outside this package.
- **Frontend:** generation-bound shared `HeadmanGroupApi` and a shared Vue
  screen mounted by the existing headman owner in PWA/TMA. Mutations wait for
  server ACK then refetch members, assistants, and catalog. No client-side
  role elevation or local persistence.

## Acceptance criteria

1. A non-headman, foreign-group request, non-active student grant, revoked
   assistant, disabled actor, or stale signed session is denied.
2. A valid headman can assign only an active student in the headman's current
   durable group; permission changes and revocation survive reload.
3. Assistant capabilities are checked independently and fail closed when the
   Academic RPC is unavailable. `MANAGE_HOMEWORK` remains its existing local
   consumer; it is not widened by the new RPC.
4. PWA and TMA cannot leak a prior generation's students, assistants, or
   capabilities after role/session invalidation.

## Verification plan

- Academic/attendance/schedule focused auth checks for signed actor, own vs
  foreign group, inactive/revoked grant, disabled account, lifecycle history,
  and distinct permission gates.
- One affected mobile-core/PWA/TMA typecheck after the final source diff.
- `git diff --check` and the bounded Gradle batch are recorded with their exit
  codes below. No full suite, deployment, or production migration was run.

## Constraints and known limits

- GroupService, GroupCodeRules, V31, GlobalExceptionHandler, admin dashboard,
  password operations, exports, and unrelated CRUD stay untouched.
- Legacy HTTP header mode can serve existing operations but cannot authorize the
  new assistant permission RPC and never creates a synthetic signed token.
- This package wires only the existing operations listed above; it does not
  claim additional assistant semantics for unrelated endpoints.

## Stable source inventory and evidence

Baseline is `5cb738678d4cd40e9f5cbc0ae39a39763e8f98c1`. The product diff is
frozen after the bounded Sol correction batch and successful final check; only
this evidence file may be updated for reporting. There are no deletions.

### Added (A)

- `.agent/headman-assistants-delivery-20260922/implementation-evidence.md`
- `frontends/mobile-core/src/features/headman-group/AssistantActionsScreen.vue`
- `frontends/mobile-core/src/features/headman-group/HeadmanGroupScreen.vue`
- `frontends/mobile-core/src/features/headman-group/assistant-actions-screen.pcss`
- `frontends/mobile-core/src/features/headman-group/headman-group-client.ts`
- `frontends/mobile-core/src/features/headman-group/headman-group-screen.pcss`
- `frontends/mobile-core/src/features/homework/AssistantHomeworkScreen.vue`
- `frontends/mobile-core/src/features/homework/assistant-homework-screen.pcss`
- `frontends/mobile-core/src/features/homework/headman-homework-client.ts`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/assistant/AssistantPermissionOption.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantPermissionAuthority.java`
- `services/academic-service/academic-app/src/main/resources/db/migration/V32__headman_assistant_active_history.sql`

### Modified (M)

- `frontends/mobile-core/src/features/headman-journal/HeadmanJournalScreen.vue`
- `frontends/mobile-core/src/features/headman-journal/headman-journal-client.ts`
- `frontends/mobile-core/src/features/headman-journal/headman-journal-screen.pcss`
- `frontends/mobile-core/src/features/headman-requests/HeadmanRequestsScreen.vue`
- `frontends/mobile-core/src/features/profile/MoreScreen.vue`
- `frontends/mobile-core/src/features/profile/profile-types.ts`
- `frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue`
- `frontends/mobile-core/src/index.ts`
- `frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue`
- `frontends/pwa-vue/src/App.vue`
- `frontends/pwa-vue/src/auth.ts`
- `frontends/tma-vue/src/App.vue`
- `frontends/tma-vue/src/tma-session.ts`
- `proto/academic.proto`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/AssistantApi.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantController.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/HeadmanAssistant.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptor.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/HeadmanAssistantRepository.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicGrpcIT.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/SecurityIdorIT.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClient.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilter.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/RequestContext.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/marking/MarkingServiceTest.java`
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/AcademicGrpcClient.java`
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java`
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/security/RequestContext.java`
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilter.java`
- `services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/DualModeUserContextFilter.java`

### Checks

- `npm ci --prefer-offline --no-audit --no-fund` from `frontends`: exit 0;
  existing lockfile and dependency versions were unchanged.
- `npm run typecheck` from `frontends`: exit 0 for `mobile-core`, `pwa-vue`,
  and `tma-vue`.
- `git diff --check`: exit 0. Git emitted only the existing LF/CRLF
  normalization warnings for touched working-copy files.
- Sol correction batch: PWA `App.vue` now passes the assistant homework API;
  `AssistantActionsScreen.vue` routes `requests` and `homework` by explicit
  surface; `HomeworkService` re-reads active durable HEADMAN/STUDENT grants in
  the request group and checks the active assistant's `MANAGE_HOMEWORK` grant
  before every create/update/delete. The existing unit fixture now supplies a
  durable headman grant; the real SecurityIdorIT fixture covers suspended
  headman, transferred student grant, and suspended student grant.
- Native bounded Gradle invocation: exit 0, terminal handle `34787`,
  `BUILD SUCCESSFUL in 2m 39s` (55 actionable tasks, 20 executed, 35
  up-to-date). It compiled the Academic test sources and the Attendance and
  Schedule consumers, then ran exactly the selected tests:

```text
.\gradlew.bat :services:academic-service:academic-app:test --tests 'ru.rutcampustrack.academic.homework.HomeworkServiceTest' :services:academic-service:academic-app:integrationTest --tests 'ru.rutcampustrack.academic.integration.AcademicGrpcIT.checkAssistantPermission_usesSignedIdentityAndFreshDurableGrant' --tests 'ru.rutcampustrack.academic.security.SecurityIdorIT.listAssistants_foreignGroupId_returns403' --tests 'ru.rutcampustrack.academic.security.SecurityIdorIT.assignAssistant_foreignGroupId_returns403' --tests 'ru.rutcampustrack.academic.security.SecurityIdorIT.updateAssistantPermissions_foreignAssistant_returns403' --tests 'ru.rutcampustrack.academic.security.SecurityIdorIT.revokeAssistant_foreignAssistant_returns403' --tests 'ru.rutcampustrack.academic.security.SecurityIdorIT.assignRevokeAssign_preservesHistoryAndCannotUpdateRevokedRow' --tests 'ru.rutcampustrack.academic.integration.RestApiIT.testHeadmanRevokeCascadesAssistants' :services:attendance-service:attendance-app:test --tests 'ru.rutcampustrack.attendance.marking.MarkingServiceTest.assistantWithOnlyMarkPermissionCannotReplaceExistingExcusedMark' :services:attendance-service:attendance-app:compileJava :services:schedule-service:schedule-app:compileJava --no-daemon --no-parallel --max-workers=1 --no-problems-report
```

The resulting XML has no skips, failures, or errors:

- `academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.integration.AcademicGrpcIT.xml`: 1/1 passed.
- `academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.security.SecurityIdorIT.xml`: 5/5 passed.
- `academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.integration.RestApiIT.xml`: 1/1 passed.
- `academic-app/build/test-results/test/TEST-ru.rutcampustrack.academic.homework.HomeworkServiceTest.xml`: 11/11 passed.
- `attendance-app/build/test-results/test/TEST-ru.rutcampustrack.attendance.marking.MarkingServiceTest.xml`: 1/1 passed.

The correction batch used the same bounded Gradle scope after two test-only
diagnoses. Handle `70722` stopped at compileTestJava because the new helper
called `andExpect` on a request builder; handle `1366` reached the real test and
reported 403 for the stale create call but 405 for the helper's PATCH. Reading
the existing `HomeworkApi` contract showed update is PUT. After those test-only
fixes, handle `88820` exited 0 (`BUILD SUCCESSFUL in 1m 14s`):

- `academic-app/build/test-results/test/TEST-ru.rutcampustrack.academic.homework.HomeworkServiceTest.xml`: 11/11 passed.
- `academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.security.SecurityIdorIT.xml`: 1/1 passed.

The final XML has no skips, failures, or errors. The selected real Spring MVC
scenario denied POST/PUT/DELETE for each stale authority state.

### Runtime and limits

The integration run exercised real PostgreSQL/Testcontainers, Spring MVC,
gRPC signed identity propagation, and the selected attendance authorization;
`docker ps` was empty after handle `88820`, so all owned test resources were
cleaned up and HEAVY was released. This is source/runtime evidence for the
bounded checks, not live PWA/TMA deployment evidence. PWA and TMA source wiring
and typecheck pass. The existing direct Vitest attempt for
`StudentFeatureOwner.test.ts` could not transform Vue SFCs because this
workspace has no Vitest config/plugin-vue for mobile-core; it ran 0 tests and
is recorded as harness-limited, with no test infrastructure change. Browser
acceptance through the deployed gateway, production migration execution, and
the full test suite remain pending.
