# JS-ADMIN01/02/03 implementation evidence

Baseline: `368e99de779a72c33c831243dcf310fffb4422d1`.
Owner: `/root/admin_semester_delivery`; shared MAIN product writer.
Contract: [`contract.md`](contract.md). API proposal: [`api-extension-proposal.md`](api-extension-proposal.md).

## Scope and criteria

The package covers the real Academic user directory, one-person creation,
durable per-role grant/status changes, canonical student transfer with reason,
and shared PWA/TMA mounting. `user_role_grants` remains the authority source.
Only ACTIVE grants are selectable; inactive grants stay visible/read-only.
Independent active grants survive another role's revoke. Existing logins are
preserved; new logins reserve the identity digits and every collision suffix
inside the 32-character database limit.

The screen uses server pagination metadata, real IDs and role data, and guards
list/mutation writes by request revision, abort signal, and session generation.
Mutation success is followed by a server refetch. PWA and TMA create clients
through their generation-bound session owners. Legacy roster, homework and
mixed-role transfer reads use the active STUDENT grant path.

## Changed product inventory (scoped A/M/D)

Added (`A`):

- `.agent/admin-users-delivery/api-extension-proposal.md`
- `.agent/admin-users-delivery/implementation-evidence.md`
- `frontends/mobile-core/src/features/admin-users/AdminUsersScreen.vue`
- `frontends/mobile-core/src/features/admin-users/admin-users-client.ts`
- `frontends/mobile-core/src/features/admin-users/admin-users-screen.pcss`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/user/RoleGrantUpdateRequest.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/user/RoleGrantViewResponse.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/enums/RoleGrantStatus.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRoleGrantReader.java`
- `services/academic-service/academic-app/src/main/resources/db/migration/V29__admin_role_statuses.sql`

Modified (`M`):

- `frontends/mobile-core/src/features/admin-semester/AdminRoleNavigation.vue`
- `frontends/mobile-core/src/features/profile/profile-types.ts`
- `frontends/mobile-core/src/index.ts`
- `frontends/mobile-core/src/shared/session-owner.ts`
- `frontends/pwa-vue/src/App.vue`
- `frontends/pwa-vue/src/auth-client.ts`
- `frontends/pwa-vue/src/auth.ts`
- `frontends/tma-vue/src/App.vue`
- `frontends/tma-vue/src/tma-session.ts`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/UserApi.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/user/UserCreatedResponse.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/user/UserResponse.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkNotificationJob.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRepository.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRoleGrantWriter.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserAssembler.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserController.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/model/RoleStatus.java`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/ActiveRolePolicyTest.java`

Deleted (`D`): none.

Foreign WIP under `.agent/`, docs, configs, assets, and unrelated services was
left untouched and is excluded from this packet.

## Checks and exit codes

Passed before the final review freeze:

- `frontends/mobile-core`: `npm run typecheck` — exit `0`.
- `frontends/pwa-vue`: `npm run typecheck` — exit `0`.
- `frontends/tma-vue`: `npm run typecheck` — exit `0`.
- Scoped ESLint over changed Vue/TypeScript adapters and screens — exit `0`.
- `:services:academic-service:academic-app:compileJava --no-daemon` — exit `0`
  after the contract import/writer import corrections.
- Existing durable authority regression:
  `:services:auth-service:auth-app:integrationTest --tests
  ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthorityIT.staleAuthoritativeGrantVersionRejectsSessionAndLoginEvent
  --no-daemon` — exit `0`. It changed the real durable grant status and
  verified stale session creation was rejected with no session or LOGIN event.

The Gradle problems report was transiently locked by another process on one
compile attempt; the successful compile was rerun after that report collision.
No full suite or broad frontend build was run.

## Runtime evidence and limits

No new live UI/runtime acceptance is claimed here. The bounded backend compile
and named create/history/cache/filter checks are recorded below; genuine
Telegram-host TMA acceptance remains a separate runtime criterion. This
evidence does not claim full admin CRUD, archival/delete/export, bulk import,
password delivery, or production migration/deploy.

The final Java corrections after the successful bounded compile (durable
base-status alignment and Telegram identity mismatch rejection) are included
in the frozen diff and were covered by the named bounded compile/tests below.

## FAIL4 correction batch

Sol FAIL4 correction scope is now frozen for scoped recheck. Exact product
paths touched by this batch:

- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/user/CreateUserRequest.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRoleGrantReader.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserAssembler.java`
- `frontends/mobile-core/src/features/admin-users/AdminUsersScreen.vue`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceTelegramRequiredTest.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/HistoricalMembershipIT.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java`

The batch validates positive and role-specific create fields, closes terminal
STUDENT history at the canonical transition date, opens a separate interval
on reactivation, evicts `group_members` after commit for create/role changes,
and aligns role/status filter applicability in server projection, HTTP
validation, and the mobile filter UI. Frontend typecheck and scoped lint both
pass with exit `0`; `git diff --check` passes with exit `0`. The bounded Java
compile and focused create/history/cache/filter checks are reported below. No
full suite, runtime acceptance, commit, or push is claimed here.

## Bounded correction checks

The first native invocation, handle `50732`, compiled the affected Java sources
and ran `UserServiceListTest` (7 passing) plus `UserServiceTelegramRequiredTest`
(16 total, 2 Mockito `UnnecessaryStubbingException` failures). The two failures
were obsolete `nextTeacherLoginSeq()` and `nextStudentLoginSeq()` stubs in the
new acceptance tests; no product assertion failed and the integration selectors
did not start. The test-only stubs were removed.

Handle `5400` then ran only the two corrected unit methods and the two required
integration selectors. Both unit methods passed; the
`HistoricalMembershipIT.terminalRoleGrantClosesHistoryAndReactivationStartsCanonicalInterval`
selector passed (`tests=1`, `skipped=0`, `failures=0`). The cache selector
exposed a fixture defect: it used seeded group `1`, which has no managed
`group_history_coverage` marker required by the active Student creation
contract. The fixture was corrected to create a managed group through
`GroupService` and clean user, history, grant, coverage, and group rows in
dependency order.

Handle `10240` reran only
`CacheIT.createStudent_invalidatesGroupMembersAfterCommit`; Gradle exited `0`
(`BUILD SUCCESSFUL`, 37 actionable tasks, 2 executed, 35 up-to-date), and the
result XML reports `tests=1`, `skipped=0`, `failures=0`, `errors=0` at
`services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.integration.CacheIT.xml`.
The combined bounded evidence is limited to the named selectors; no full suite,
deploy, or live UI acceptance is claimed.

The final test-only correction is confined to
`services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceTelegramRequiredTest.java`
and the managed-group fixture in
`services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java`.
