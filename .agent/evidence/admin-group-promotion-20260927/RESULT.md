# Admin promotion by completed spring cycle

Date: 2026-09-27
Worktree: `.agent/worktrees/admin-group-promotion-20260927`
Branch/base: `codex/admin-group-promotion-20260927` from `064faa6742931e5a0698ebc559eae800f8312c7b`

## Scope and acceptance criteria

Implement the accepted administrator group-promotion contract in the Academic service and existing `AdminGroupsScreen`: a completed typed `SPRING` semester ID defines a cycle; groups created after its `dateTo` are excluded; each group can be processed once per cycle across single and mass operations; preview returns server counts and group-level consequences; execute replans transactionally and rejects changed cycle, scope, or outcomes with `409`; ledger uniqueness is `(cycleSemesterId, groupId)`; historical group/student records remain intact; ADMIN-only routes reject other roles. No automatic trigger was added.

## Evidence and runtime behavior

- Existing API exposed bodyless preview/execute and had no cycle ledger; the previous service unit test explicitly expected a second execute to advance the same group again. The registry screen did not call the promotion endpoints.
- Owner decisions received through root: cycle is the latest completed typed spring semester; null `semesterType` is not inferred from its title; a group created after the spring `dateTo` is excluded; manual protected flow only.
- `GroupPromotionCycleIT` ran against PostgreSQL 16 via the existing Testcontainers setup. It proved TEACHER receives `403`; one-group preview reports the student count; single execute followed by mass preview lists that group as processed; post-cycle creation is skipped; adding a group after preview makes old execute return `409` without partial mutation; refreshed mass execute promotes/archives using the same row IDs and retains student history; replaying the completed mass request returns `409` and leaves one cycle row per group.
- The integration checks that an archived group's suffix year matches the cycle `dateTo` (`2026`); the promotion path calls the cycle-year overload while existing archive callers retain clock-based behavior.

## Checks

| Check | Command / evidence | Result |
|---|---|---|
| Restore locked frontend dependencies offline | From `frontends`: `npm ci --offline --no-audit --no-fund` | Exit 0; 222 packages; no lockfile change |
| TypeScript | From `frontends`: `npm run typecheck --workspace @rct/mobile-core` | Exit 0 |
| Client lint | From `frontends/mobile-core`: `../node_modules/.bin/eslint.cmd src/features/admin-groups/admin-groups-client.ts --max-warnings=0` | Exit 0 |
| Screen lint baseline comparison | `eslint AdminGroupsScreen.vue --format json`; HEAD screen had 99 warnings, current screen has 98, both 0 errors; warnings are in existing markup, with none in the new promotion panel/modal/row action | Exit 0; no new screen warnings |
| Academic unit + PostgreSQL integration | `./gradlew.bat :services:academic-service:academic-app:test --tests ru.rutcampustrack.academic.group.GroupPromotionServiceTest :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.group.GroupPromotionCycleIT --no-daemon --no-parallel --max-workers=1 --system-prop=org.gradle.java.compile-classpath-packaging=true --no-problems-report --console=plain` | Exit 0; `BUILD SUCCESSFUL`; 13 unit cases and 1 PostgreSQL IT, no failures |
| Unit XML | `services/academic-service/academic-app/build/test-results/test/TEST-ru.rutcampustrack.academic.group.GroupPromotionServiceTest.xml` | tests=13, skipped=0, failures=0, errors=0 |
| PostgreSQL IT XML | `services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.group.GroupPromotionCycleIT.xml` | tests=1, skipped=0, failures=0, errors=0 |
| Whitespace | `git diff --check` | Exit 0 |

Two earlier scoped Gradle attempts stopped at test compilation because the adapted unit helper used a non-existent DTO constructor and then lacked one matcher import. Both test-only defects were corrected; the final scoped batch above passed. No unrelated compiler warning was changed.

## Diff inventory

- `frontends/mobile-core/src/features/admin-groups/AdminGroupsScreen.vue`
- `frontends/mobile-core/src/features/admin-groups/admin-groups-client.ts`
- `frontends/mobile-core/src/features/admin-groups/admin-groups-screen.pcss`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/GroupApi.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/PromotionExecuteRequest.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/PromotionPreviewRequest.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/PromotionPreviewItem.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/PromotionSkippedItem.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/PromotionSummary.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/GroupPromotionCycleRecord.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupArchivalService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupController.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/GroupPromotionCycleRecordRepository.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/GroupRegistryReadRepository.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/GroupRepository.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/SemesterRepository.java`
- `services/academic-service/academic-app/src/main/resources/db/migration/V36__group_promotion_cycle_record.sql`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupPromotionServiceTest.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupPromotionCycleIT.java`

## Limitations

- No full Academic suite or PWA/TMA production build was run. The shared PWA/TMA build is intentionally deferred until the parallel teacher branch is integrated; UI was checked with typecheck and scoped lint only, without browser screenshot/runtime review.
- The migration was applied only by the disposable/reusable integration-test database, not to production or another environment.
- The automatic scheduler trigger remains undecided and unimplemented.

## Independent-review corrections

- The execution path now flushes each graduation archive before starting promotions, then flushes each promotion in descending source-course order. The PostgreSQL 16 integration case applies the adjacent `УИТ-111 → 211 → 311 → 411` chain and archives the former `411`; final IDs remain attached to their original rows, the cycle ledger contains four rows, and student history remains on the same group ID.
- A group with course 9 and stored duration 10 now appears as a blocking `unrepresentable_next_course` preview conflict; preview returns no promote/archive action instead of propagating `InvalidCodeException`. The create-duration policy is unchanged.
- The execution result now says only that N groups were skipped and lists each skipped group's server-provided reason, including groups created after cycle end.
- First correction run exited 1 before reaching the promotion endpoint because the new test fixture login exceeded the database's `VARCHAR(32)`. The fixture login was shortened; no product code changed in response to that setup failure. The targeted recheck then passed.

| Correction check | Command / evidence | Result |
|---|---|---|
| Mobile-core typecheck after UI correction | `npm run typecheck --workspace @rct/mobile-core` from `frontends` | Exit 0 |
| Admin group client and screen lint | Client: `../node_modules/.bin/eslint.cmd src/features/admin-groups/admin-groups-client.ts --max-warnings=0`; screen lint JSON | Client exit 0; screen 0 errors, 98 pre-existing warnings |
| Academic unit + PostgreSQL integration recheck | Same scoped Gradle command above; retained in `correction-gradle-recheck.log` | Exit 0; `BUILD SUCCESSFUL`; 14 unit cases and 2 PostgreSQL ITs, no failures |
| Unit XML after correction | `services/academic-service/academic-app/build/test-results/test/TEST-ru.rutcampustrack.academic.group.GroupPromotionServiceTest.xml` | tests=14, skipped=0, failures=0, errors=0 |
| PostgreSQL IT XML after correction | `services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.group.GroupPromotionCycleIT.xml` | tests=2, skipped=0, failures=0, errors=0 |
| Correction diff whitespace | `git diff --check` | Exit 0 |

Correction diff inventory: `GroupPromotionService.java`, `GroupPromotionCycleIT.java`, `GroupPromotionServiceTest.java`, and `AdminGroupsScreen.vue`; evidence adds this section and the two captured scoped Gradle logs.
