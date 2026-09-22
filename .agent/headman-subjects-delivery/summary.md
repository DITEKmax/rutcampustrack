# Headman subjects delivery summary

## Scope and user result

The shared headman screen now creates subjects for the authenticated group,
selects one or more lesson types, searches teachers through a paginated server
query, creates initial assignments, and adds another teacher to an existing
subject/type. PWA and TMA pass generation-bound subject clients into the shared
schedule navigation. Reload reads real subject and assignment identifiers from
Academic; the existing schedule constructor remains their consumer.

Teacher lookup returns only `id`, `fullName`, and `employeeNumber`, searches the
displayed full name or employee number, caps pages at 20, uses stable name/id
ordering, and requires an active durable `TEACHER` grant. Existing unpaged
`GET /academic/users/teachers` remains for compatibility.

## Criteria and limitations

- Headman context, active semester, canonical subject/assignment services, and
  server group checks remain authoritative.
- Multiple teachers for one lesson type are supported and are visible after
  refetch/reload.
- Assistant, ordinary student, teacher, offline, stale-generation, and
  invalidated-session writes fail visibly; no false success is shown.
- Assignment removal/replacement remains the existing typed `409` because the
  Academic close path has only future identity scaffolding while schedule owns
  the active fence. No local close or history-breaking replacement was added.
- No React migration, proto/config/lockfile change, hard delete, catalog, or
  production migration is included.

## Exact inventory

Modified (M):

- `frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue`
- `frontends/mobile-core/src/index.ts`
- `frontends/pwa-vue/src/App.vue`
- `frontends/pwa-vue/src/auth.ts`
- `frontends/tma-vue/src/App.vue`
- `frontends/tma-vue/src/tma-session.ts`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/UserApi.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserController.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserSearchIT.java`

Added (A):

- `.agent/headman-subjects-delivery/contract.md`
- `.agent/headman-subjects-delivery/summary.md`
- `frontends/mobile-core/src/features/headman-subjects/headman-subjects-client.ts`
- `frontends/mobile-core/src/features/headman-subjects/HeadmanSubjectsScreen.vue`
- `frontends/mobile-core/src/features/headman-subjects/headman-subjects-screen.pcss`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/user/TeacherLookupResponse.java`

Deleted (D): none. Generated `build/` outputs are untracked/ignored and are not
part of the delivery inventory.

## Checks and evidence

- Mobile-core typecheck after the Sol correction batch: exit `0`, command
  `frontends/node_modules/.bin/tsc.cmd -p frontends/mobile-core/tsconfig.json --noEmit`,
  session `433b10`.
- Lightweight client probe: exit `0`, existing `vitest` `v4.1.4` runner,
  temporary untracked probe removed after execution. Two tests passed: actual
  `teacherLookupResponseList` HAL decoding and generation rejection after a
  deferred `response.json()` body.
- PWA vue-tsc: exit `0`, prior evidence session `474505`; no rerun after the
  final shared-screen-only fix because adapter types were unchanged.
- TMA vue-tsc: exit `0`, prior evidence session `809001`; same no-rerun rule.
- Native Academic targeted command, assigned WT
  `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\map-usage-delivery-20260922`:

  `.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests "ru.rutcampustrack.academic.user.UserSearchIT.teacherLookup_usesFullNameActiveGrantAndSafePagedDto" --tests "ru.rutcampustrack.academic.subject.SubjectServiceIT.createSubject_withTwoTeachers_atomicInsert" --tests "ru.rutcampustrack.academic.subject.SubjectServiceIT.addTeacher_and_removeTeacher" --no-daemon --no-parallel --max-workers=1 --no-problems-report`

  exited `0`, session `97415`, `BUILD SUCCESSFUL` in 1m41s. XML evidence:
  `UserSearchIT`: 1 test, 0 skipped, 0 failures, 0 errors;
  `SubjectServiceIT`: 2 tests, 0 skipped, 0 failures, 0 errors. Java emitted
  existing `Specification.where` deprecation warnings only.
- `git diff --check`: exit `0`; only Git's CRLF normalization warnings were
  printed for existing modified Java/Vue files.
- No live runtime endpoint evidence or production data/migration execution.

## Review state

Sol FAIL3 correction batch was bounded to the two shared mobile-core files:
teacher HAL normalization now reads `teacherLookupResponseList`, successful
create/add mutations require a `loaded` reconciliation outcome before closing
their form or showing success, and response/problem JSON is awaited before the
post-body generation assertion and return/throw while 401 retry gates remain.
No backend source changed after the native integration PASS, so that PASS was
not repeated. Product source is frozen again for the scoped Sol recheck at this
inventory; runtime/live endpoint acceptance and integration into MAIN remain
outside this WT's current evidence.
