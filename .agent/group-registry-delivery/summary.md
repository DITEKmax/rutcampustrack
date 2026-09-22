# ADMIN group registry delivery

## Scope and contract

Candidate is based on `351817c04db7374cd39a63a642392eda854f3634` in the assigned
detached worktree. The compact contract is in `contract.md`; it records the
owner decision that `currentCourse` is always derived from the first digit of
the server-validated numeric code. The request has no independent course field
or default. Training duration is mandatory and positive; the server rejects a
course digit greater than that duration and unknown programme-type digits.

The additive routes are:

- `POST /api/academic/groups/registry` with
  `{alphabeticCode,numericCode,trainingDurationYears}`;
- `GET /api/academic/groups/registry?status=ACTIVE|DRAFT|ARCHIVED&search=&page=&size=`.

The response contains the server-built name, split code, derived course,
duration/provenance, server status/reason, student count, headman FIO, page
metadata and all three status counts. ACTIVE/DRAFT use active HEADMAN grants;
ARCHIVED uses the existing group lifecycle flag. The old create route uses the
same canonical writer and retains its legacy `name` conflict response,
including route-aware database-race mapping; registry create reports
`numericCode`. Active code pairs are unique while archived pairs remain
reusable. New and old successful writes retain the current-semester
`group_history_coverage` marker in the same transaction. V31 and the Java
fallback use one legacy predicate: only valid type-1 courses 1..4 and type-7
courses 1..2 derive duration; invalid or contradictory names remain nullable/
`LEGACY_UNKNOWN`.

## Exact inventory

### Added (13)

- `.agent/group-registry-delivery/contract.md`
- `.agent/group-registry-delivery/summary.md`
- `frontends/mobile-core/src/features/admin-groups/admin-groups-client.ts`
- `frontends/mobile-core/src/features/admin-groups/AdminGroupsScreen.vue`
- `frontends/mobile-core/src/features/admin-groups/admin-groups-screen.pcss`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/AdminGroupRegistryResponse.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/AdminGroupResponse.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/AdminGroupStatus.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/CreateAdminGroupRequest.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupCodeRules.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/GroupRegistryReadRepository.java`
- `services/academic-service/academic-app/src/main/resources/db/migration/V31__admin_group_registry.sql`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupRegistryIT.java`

The source/test inventory excluding this summary is 12 files. No files are
deleted.

### Modified (19)

- `frontends/mobile-core/src/features/admin-semester/AdminRoleNavigation.vue`
- `frontends/mobile-core/src/index.ts`
- `frontends/mobile-core/src/shared/session-owner.ts`
- `frontends/pwa-vue/src/App.vue`
- `frontends/pwa-vue/src/auth.ts`
- `frontends/tma-vue/src/App.vue`
- `frontends/tma-vue/src/tma-session.ts`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/GroupApi.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/GroupResponse.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupAssembler.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupController.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/GroupRepository.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandlerTest.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupPromotionServiceTest.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupServiceTest.java`

## Criteria and evidence

- Canonical create validates split Cyrillic/three-digit code, derives course
  server-side, rejects course/duration mismatch and duplicate active pairs, and
  writes history coverage atomically. `GroupCodeRules` is shared by create,
  PUT and promotion; promotion respects saved duration and archive keeps the
  synchronized split code/history.
- V31 backfills only the canonical valid legacy predicate, leaving course-0,
  over-duration and unknown-type combinations nullable/`LEGACY_UNKNOWN`;
  `GroupRegistryIT` exercises these real responses and archived pair reuse.
- Registry status, tab counts, search, stable page ordering, student count and
  headman FIO are one parameterized JDBC read projection in
  `GroupRegistryReadRepository`; page, total and tab counts run in one
  `REPEATABLE_READ` read-only service transaction.
- PUT `active=false` delegates to the existing `GroupArchivalService` after
  canonical synchronization. Constraint-race fields are route-aware: legacy
  group writes report `name`, registry writes report `numericCode`.
- ADMIN-only controller methods are present; PWA/TMA share the typed client and
  screen, and their clients are bound to the authenticated session generation.
- New group form has no headman field; the screen displays server course/status,
  switches to DRAFT, resets page/search and refetches after create.

## Checks

Executed from `...\.agent\worktrees\map-usage-delivery-20260922` unless noted:

| Command | Exit | Evidence |
|---|---:|---|
| `git diff --check` | 0 | no whitespace errors |
| `npm run typecheck --workspace @rct/mobile-core` from `frontends` | 0 | shared client/screen/session owner compile |
| `npm run typecheck --workspace @rct/pwa-vue` from `frontends` | 0 | PWA group route/client wiring compile |
| `npm run typecheck --workspace @rct/tma-vue` from `frontends` | 0 | TMA group route/client wiring compile |
| `.\\gradlew.bat :services:academic-service:academic-app:test --tests 'ru.rutcampustrack.academic.group.GroupServiceTest' --tests 'ru.rutcampustrack.academic.group.GroupPromotionServiceTest' :services:academic-service:academic-app:integrationTest --tests 'ru.rutcampustrack.academic.group.GroupRegistryIT.registryCreateCoverageStatusCountsAndLegacyDurationAreServerDerived' --no-daemon --no-parallel --max-workers=1 --no-problems-report` | 0 | session `77170`; unit selectors and one real PostgreSQL/Testcontainers registry IT, 38 tests completed, 0 failures/0 skips |
| `.\\gradlew.bat :services:academic-service:academic-app:test --tests 'ru.rutcampustrack.academic.group.GroupServiceTest' --tests 'ru.rutcampustrack.academic.group.GroupPromotionServiceTest' --tests 'ru.rutcampustrack.academic.exception.GlobalExceptionHandlerTest' :services:academic-service:academic-app:integrationTest --tests 'ru.rutcampustrack.academic.group.GroupRegistryIT.registryCreateCoverageStatusCountsAndLegacyDurationAreServerDerived' --no-daemon --no-parallel --max-workers=1 --no-problems-report` | 0 | session `5676`; current correction batch, 38 actionable tasks, selectors completed with 0 failures/0 skips |

The `77170` invocation is successful heavy evidence for the previous six-fix
batch. Earlier bounded attempts stopped on compile/test-fixture issues and were
corrected before that run; they are not acceptance evidence. The current
three-fix Sol recheck and the sufficient bounded heavy verification are complete.
The current verification used these selectors:

```text
:services:academic-service:academic-app:test
  --tests 'ru.rutcampustrack.academic.group.GroupServiceTest'
  --tests 'ru.rutcampustrack.academic.group.GroupPromotionServiceTest'
  --tests 'ru.rutcampustrack.academic.exception.GlobalExceptionHandlerTest'
:services:academic-service:academic-app:integrationTest
  --tests 'ru.rutcampustrack.academic.group.GroupRegistryIT.registryCreateCoverageStatusCountsAndLegacyDurationAreServerDerived'
```

The heavy lease has been released. Runtime/live ADMIN acceptance and gateway
deployment evidence remain outstanding.

## Limitations and boundaries

The package does not add promotion UI/workflow, delete/export actions, headman
assignment, roster management, password challenge, generated API artifacts,
lockfile/dependency changes, production migration or deployment. Existing
promotion/rename/archive writers receive only the bounded canonical
synchronization correction described above. MAIN and foreign worktree changes
are untouched; this candidate is not integrated or pushed.
