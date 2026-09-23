# JS-TEACHER-06 implementation evidence

Revision baseline: `17467aa67c4d08338b96ae22d2f848a843f3910c` in
`headman-assistants-delivery-20260922`. Canonical root RULES SHA supplied by
the coordinator: `1FF4F775373990D2FC1CDEB2DC525AB826929161BB0A4B679F69FE956428B39A`.
The pre-existing worktree-local `.agent/orchestration-v2/RULES.md` has a
different hash and remains untouched as foreign work.

## Scope and observed behavior

The package adds a signed teacher-read path from PWA/TMA through
`GET /api/v1/teacher/stats` to the attendance aggregation RPC. The BFF resolves
the complete selected-semester lesson range for every group in the actor's
current active teacher authority. Attendance rechecks that current authority
for every concrete lesson, so a historical schedule lesson assigned to another
teacher is readable when the actor is active in the same group; a foreign or
revoked group is denied. The selected report semester is never used as the
sole source of current group access.

The response includes `subjectOptions` for every readable group/subject found
in the concrete schedule batch, with server-resolved subject names and lesson
types. The stats aggregation loads dated historical rosters, excludes
cancelled and non-`CLOSED` lessons, and returns server metrics in the canonical
order `%+`, `%(+ и у)`, `%у`, `%н`. `CLOSED` is the schedule-owned Moscow
end-time/grace transition, so a calendar-date check cannot turn an unfinished
current-day lesson into `ABSENT`. Empty results retain the requested semester
and subject options when the schedule batch contained them.

The same current group read gate is used by teacher journal and concrete lesson
reads. `/teacher/day` remains the personal schedule and still filters by the
actor's assigned teacher. Excuse, ticket and attachment reads retain their
own-lesson assignment gate; no mutation authority changed.

The shared Vue screen has the two accepted scopes, server-side sort/filter
parameters, group-row drill-down to students, loading/empty/error states and a
generation-bound `TeacherApi`. It consumes server `subjectOptions` so a group
can expose subjects and types beyond the actor's own historical assignment
rows. `TeacherFeatureOwner` reaches the screen from both PWA and TMA teacher
surfaces. Safe URL context persists semester/scope/group/subject/types/search/
sort across reload; protected result data is not persisted and generation or
dispose guards suppress stale writes/errors. Export formats, graphs, student
detail and cross-group subject identity remain outside JS-TEACHER-06; the
JS-TEACHER-07 export scope remains open.

The final UI authority cache is populated only from the initial unfiltered
`scope=groups` stats response for the current API/session generation. The same
response supplies the full current `subjectOptions` cache used by group,
subject, and lesson-type selectors. Historical assignments and filtered
student/group responses do not define or shrink selectable current authority.
Reload retains a saved group only when that initial server response confirms it;
it retains a saved subject for the confirmed group until the matching student
stats response loads. This closes the reviewed A→B group switch case where the
second response otherwise hid a foreign-to-the-teacher subject in the same
currently authorized group.

## Changed inventory (exact)

Added:

- `.agent/teacher-stats-delivery-20260923/contract.md`
- `.agent/teacher-stats-delivery-20260923/implementation-evidence.md`
- `frontends/mobile-core/src/features/teacher/TeacherStatsScreen.vue`
- `frontends/mobile-core/src/features/teacher/teacher-stats-route.ts`
- `frontends/mobile-core/src/features/teacher/teacher-stats-screen.pcss`

Modified:

- `proto/teacher_reads.proto`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/TeacherAcademicReadGrpcService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceReadPortImpl.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClient.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/port/AttendanceReadPort.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcServiceTest.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/ReportServiceTest.java`
- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/TeacherApi.java`
- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/TeacherApiModels.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherApiController.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacade.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacadeTest.java`
- `frontends/mobile-core/src/features/teacher/TeacherHomeScreen.vue`
- `frontends/mobile-core/src/features/teacher/teacher-client.ts`
- `frontends/mobile-core/src/index.ts`
- `frontends/mobile-core/src/shared/components/TeacherFeatureOwner.vue`

Deleted: none.

## Final A/M/D inventory and scoped commit plan

The exact candidate inventory is 24 paths: 5 added, 19 modified, and 0
deleted. The scoped commit must contain only the paths below; no build output,
foreign work, or unrelated WT state is included.

Added (A):

- `.agent/teacher-stats-delivery-20260923/contract.md`
- `.agent/teacher-stats-delivery-20260923/implementation-evidence.md`
- `frontends/mobile-core/src/features/teacher/TeacherStatsScreen.vue`
- `frontends/mobile-core/src/features/teacher/teacher-stats-route.ts`
- `frontends/mobile-core/src/features/teacher/teacher-stats-screen.pcss`

Modified (M):

- `proto/teacher_reads.proto`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/TeacherAcademicReadGrpcService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceReadPortImpl.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClient.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/port/AttendanceReadPort.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcServiceTest.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/ReportServiceTest.java`
- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/TeacherApi.java`
- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/TeacherApiModels.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherApiController.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacade.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacadeTest.java`
- `frontends/mobile-core/src/features/teacher/TeacherHomeScreen.vue`
- `frontends/mobile-core/src/features/teacher/teacher-client.ts`
- `frontends/mobile-core/src/index.ts`
- `frontends/mobile-core/src/shared/components/TeacherFeatureOwner.vue`

Deleted (D): none.

After Sol recheck and explicit integration handoff, stage exactly this A/M/D
set, inspect staged name-status, and create one scoped local commit from this
WT. Main integration and any conflict resolution remain a separate root-owned
operation; no push or deploy is part of this candidate.

Foreign work: no foreign files were staged or reverted. PWA/TMA `App.vue`
already render the shared `TeacherFeatureOwner`; the teacher-only owner/index
hunks are the bounded integration point used by both surfaces.

## Checks

| Check | Command / handle | Exit | Evidence |
|---|---|---:|---|
| mobile-core types | `npm run typecheck --workspace @rct/mobile-core` from `frontends` | 0 | `tsc -p tsconfig.json --noEmit` |
| PWA host types | `npm run typecheck --workspace @rct/pwa-vue` from `frontends` | 0 | `vue-tsc -p tsconfig.json --noEmit` |
| TMA host types | `npm run typecheck --workspace @rct/tma-vue` from `frontends` | 0 | `vue-tsc -p tsconfig.json --noEmit` |
| final PWA Vue template/type check after authority-cache correction | `npm run typecheck --workspace @rct/pwa-vue` from `frontends` | 0 | `vue-tsc -p tsconfig.json --noEmit`; verifies the modified shared `.vue` screen through its host |
| whitespace | `git diff --check` | 0 | no diagnostics on the source diff; rerun after this evidence update was also clean |
| first correction batch | Gradle handle `93210` | 1 | Existing foreign-group test fixture still defaulted to own current authority; no product failure. Corrected fixture to return a foreign active group. |
| targeted backend/BFF batch | Gradle handle `69343` | 0 | 8 attendance selectors, Academic generated Java compile, BFF journal selector and BFF compile. |
| BFF historical-owner assertion | Gradle handle `14764` | 0 | `TeacherReadFacadeTest.journalDiscoversConcreteLessonsOnTwoDatesForOneContext` passes with concrete lessons assigned to another teacher. |
| subject-options correction batch | Gradle handle `67672` | 0 | This rerun was required by a real source correction after `69343`/`14764`: the BFF now sends the complete selected-group subject batch, Attendance filters the selected subject for aggregation while returning options for all readable subjects, and the formula test asserts the additional same-group subject option. |

The successful Gradle batch used one native invocation with
`--no-daemon --no-parallel --max-workers=1 --no-problems-report` and these
attendance selectors:

- `ReportServiceTest.teacherStatsUsesClosedLessonsAndServerSideMetricCounts`
- `ReportServiceTest.teacherStatsFutureOnlyKeepsRequestedSemesterAndReturnsNoData`
- `ReportServiceTest.teacherStatsRejectsLessonOutsideSelectedStudentScope`
- `ReportServiceTest.teacherStatsKeepsHistoricalScheduleLessonForCurrentGroupAuthority`
- `ReportServiceTest.teacherStatsAppliesServerFilterBeforeReturningRows`
- `ReportServiceTest.authorizeTeacherWithHistoricalDifferentSubjectAllowsCurrentGroup`
- `ReportServiceTest.authorizeTeacherWithDifferentGroupDeniesLesson`
- `TeacherAttendanceReadGrpcServiceTest.teacherStatsMapsScheduleDependencyFailureToUnavailable`

Result XML evidence from handle `69343`:

- `services/attendance-service/attendance-app/build/test-results/test/TEST-ru.rutcampustrack.attendance.report.ReportServiceTest.xml`: 7 tests, 0 skipped, 0 failures, 0 errors.
- `services/attendance-service/attendance-app/build/test-results/test/TEST-ru.rutcampustrack.attendance.grpc.TeacherAttendanceReadGrpcServiceTest.xml`: 1 test, 0 skipped, 0 failures, 0 errors.
- `services/mobile-bff/mobile-bff-app/build/test-results/test/TEST-ru.rutcampustrack.mobilebff.teacher.TeacherReadFacadeTest.xml`: 1 test, 0 skipped, 0 failures, 0 errors for the later historical-owner assertion (handle `14764`).

Handle `67672` completed with exit 0 using the same bounded selectors after the
subject-options source correction described above; it is not a handle-reference
mismatch. The resulting XML remained 7 passing
`ReportServiceTest` cases, 1 passing `TeacherAttendanceReadGrpcServiceTest`
case, and 1 passing `TeacherReadFacadeTest` case; there were no skips,
failures, or errors.

Runtime evidence: no live Docker or gateway acceptance in this WT. Handles
`69343`, `14764`, and `67672` terminated with exit 0; no owned process or
container is held. The root-owned runtime harness is checking the browser flow
and signed-session denial separately; as of this evidence revision it has not
reported a result, so no production-like runtime pass is claimed. The shared UI
flow selectors are Home button `Статистика`, group-name button in the stats
table, selects labelled `Группа` and `Предмет`, journal button `Журнал ·
{type}`, journal heading `Журнал группы`, and back button `← Назад`.

Final UI authority-cache correction verification: `npm run typecheck
--workspace @rct/pwa-vue` from `frontends` exited 0 and ran `vue-tsc -p
tsconfig.json --noEmit`; `git diff --check` exited 0 (Git emitted only the
existing LF→CRLF conversion notices). Independent Sol recheck for A→B selector
retention and foreign-subject/reload: PASS, no blocking findings (review
verdict delivered by root; this is a source-review result, not a test-process
exit code). Source is frozen at this point. No Gradle rerun was needed because
no backend source changed after the successful targeted batch.

## Limits

`subjectOptions` are derived from the concrete selected-semester schedule
batch. If that batch contains no lessons, the server returns no fabricated
subject rows. The contract intentionally leaves JS-TEACHER-07 export formats,
graphs, student detail and cross-group subject identity open. The package has
not claimed production runtime acceptance, deployment or a full backend suite.
