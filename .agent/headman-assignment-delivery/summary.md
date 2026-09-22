# JS-ADMIN-06/07 delivery summary

## Baseline and scope

- Worktree: `.agent/worktrees/map-usage-delivery-20260922`.
- Branch: `codex/headman-assignment-20260922`.
- Baseline: `e21e9d331fe07d7b740129ecd7c6a6de3b0dd972`.
- WT was clean before branch creation; no reset/discard; MAIN and foreign WIP
  untouched.
- Risk: S3 (durable role uniqueness, lock ordering, historical assistants,
  concurrent ADMIN writes, legacy bypass).
- Contract: [contract.md](contract.md).

## Product result

Correction source is frozen for the scoped Sol recheck. ADMIN now has roster, preview and
CAS-protected assignment contracts; the canonical service locks involved users
in ascending order and then the group, synchronizes durable/legacy authority,
revokes assistants group-wide while retaining rows, and evicts caches after
commit. Legacy `PATCH isHeadman` true/false bridges into the same service before
the old target-user lock. Active STUDENT grant membership now controls
eligibility and derived HEADMAN scope even when the base user role is TEACHER
or its base account status is suspended; archived users and missing/non-active
STUDENT grants remain ineligible. Ordinary profile synchronization preserves
that grant, while suspending/revoking the STUDENT grant withdraws HEADMAN.
The mobile-core group registry now exposes the roster, preview and confirmation
panel with separate read/mutation lifecycles: stale responses cannot mutate a
newer panel, and an already-sent mutation is reconciled after commit or an
ambiguous outcome.

## Files

Frozen exact inventory (A/M/D): 10 added, 10 modified, 0 deleted.

Added:

- `.agent/headman-assignment-delivery/contract.md`
- `.agent/headman-assignment-delivery/summary.md`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/AssignHeadmanRequest.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/HeadmanCandidateResponse.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/HeadmanRosterResponse.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/HeadmanAssignmentPreviewResponse.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/HeadmanAssignmentResponse.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupHeadmanAssignmentService.java`
- `services/academic-service/academic-app/src/main/resources/db/migration/V33__unique_active_headman_per_group.sql`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupHeadmanAssignmentIT.java`

Modified:

- `frontends/mobile-core/src/features/admin-groups/AdminGroupsScreen.vue`
- `frontends/mobile-core/src/features/admin-groups/admin-groups-client.ts`
- `frontends/mobile-core/src/features/admin-groups/admin-groups-screen.pcss`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/GroupApi.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupController.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRepository.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRoleGrantWriter.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandlerTest.java`

Deleted: none. No App/global config/proto/generated/lock/AssistantService or
foreign worktree files are in this inventory.

## Criteria and checks

- `git diff --check`: PASS (exit 0; only Git CRLF conversion warnings).
- Absolute existing workspace `tsc.cmd --noEmit -p tsconfig.json` from
  `frontends/mobile-core`: PASS (exit 0).
- First bounded native invocation `28594` reached the academic test compile but
  exited 1 because `GroupHeadmanAssignmentIT` omitted the existing
  `GroupRegistryReadRepository` import; the two existing deprecation warnings
  were non-blocking. The correction was limited to that test import and did
  not change product source.
- Combined invocation `71596` compiled and ran the unit selector successfully,
  but the IT reported two fixture defects: generated login exceeded the V1
  `users.login VARCHAR(32)` limit, and the new assertion used first-name-first
  FIO while the existing registry contract is surname-first. Both corrections
  are test-only and were checked against `GroupRegistryIT` and the migration
  schema.
- `git diff --check`: PASS (exit 0; only Git CRLF conversion warnings).
- Absolute existing workspace `tsc.cmd --noEmit -p tsconfig.json` from
  `frontends/mobile-core`: PASS (exit 0).
- Native bounded invocation `32509` ran only
  `:services:academic-service:academic-app:integrationTest --tests
  ru.rutcampustrack.academic.group.GroupHeadmanAssignmentIT` with
  `--no-daemon --no-parallel --max-workers=1 --no-problems-report`: PASS,
  4 tests, 0 failures, 0 skipped, exit 0. The preceding unit selector
  `GlobalExceptionHandlerTest` in `71596` was 12 tests, 0 failures, 0 skipped.
- After the two Sol correction findings, native invocation `72371` ran only
  `GroupHeadmanAssignmentIT` and the existing
  `HistoricalMembershipIT.serviceCreatedEnrollmentAndTransferAreDatedAndComplete`
  selector: PASS, 6 tests total, 0 failures, 0 errors, 0 skipped, exit 0. The
  new multi-role/profile-sync case is included in the five GroupHeadman tests;
  the existing revoke/suspend path ran as the sixth test.
- Final residual correction invocation `82614` ran only
  `:services:academic-service:academic-app:integrationTest --tests
  ru.rutcampustrack.academic.group.GroupHeadmanAssignmentIT` with
  `--no-daemon --no-parallel --max-workers=1 --no-problems-report`: PASS,
  5 tests, 0 failures, 0 errors, 0 skipped, exit 0. The focused case covers
  suspended base TEACHER state with an active STUDENT grant, ordinary profile
  synchronization preserving HEADMAN, and later STUDENT suspension revoking
  it. This is the final residual revision; no unrelated selectors were rerun.

## Runtime evidence

Runtime evidence: session `32509` completed `BUILD SUCCESSFUL` in 1m 5s
(37 actionable tasks, 2 executed, 35 up-to-date). Testcontainers connected to
the local Docker engine and the academic application shut down cleanly. The
correction session `72371` completed `BUILD SUCCESSFUL` in 1m 33s (37
actionable tasks, 3 executed, 34 up-to-date), with the same clean Docker/
application shutdown. Final session `82614` completed `BUILD SUCCESSFUL` in
1m 20s (37 actionable tasks, 3 executed, 34 up-to-date); Testcontainers used
the local Docker engine and Spring/Hikari shut down cleanly. Earlier `28594`
and `71596` failures are retained as correction evidence; they are not PASS
claims.

## Limitations / open scope delta

`PATCH /academic/users/{id}` with `isHeadman` now bridges before the old target
lock and preserves existing admin-users behavior for the normal separate
operation. A request that combines an actual group move with headman mutation
is rejected to avoid ambiguous membership; resending the same current group is
allowed. `isHeadman=true` with a non-ACTIVE requested status remains rejected;
reactivating a suspended durable STUDENT grant is still the existing separate
role-update flow and is not claimed as legacy PATCH compatibility.

Migration V33 must fail clearly on pre-existing duplicate active headman grants;
it has not been run against production data. No production migration,
deployment, push, or full suite was performed. The source and focused runtime
checks are complete; live endpoint deployment and end-user UI acceptance remain
outside this evidence.
