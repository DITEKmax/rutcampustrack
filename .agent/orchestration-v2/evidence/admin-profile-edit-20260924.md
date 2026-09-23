# ADMIN user profile edit — 2026-09-24

## Goal and scope

Let ADMIN update an existing user's last name, first name, optional middle name, and employee number in the shared PWA/TMA admin-users screen. Use the existing `/api/academic/users/{id}` PATCH contract; role changes remain on their existing role endpoint.

Product source: `docs/wireframes/admin/132-admin-users.md`, sections 4.2–4.3. The existing `UserController.patchUser` already requires ADMIN. No authorization, route, generated type, or migration change was needed.

## Behavior and acceptance

- Open the inline profile editor from the selected user's card. Fetch that user's current server response before filling the editor; discard an obsolete response if the selection changes or the editor is cancelled.
- PATCH only changed profile properties. Do not send login, role, status, group, Telegram ID, or other fields. Refresh the server-backed user list after success and keep the server's actionable Problem Details error visible on failure, including employee-number conflicts.
- Last and first names are required and capped at 128 characters. Middle name is optional, capped at 128, and an empty string clears it to `null`; an omitted field is unchanged.
- Employee number is capped at 32 characters. Empty input clears it only when the account has no TEACHER role. The server checks durable TEACHER grants as well as the legacy base role, so mixed-role accounts cannot lose a required number.
- An unchanged employee number skips the duplicate pre-check. A changed non-empty value uses the existing native `existsByEmployeeNumber` query (including archived users); the existing database unique constraint and error mapping remain the concurrent-write backstop.
- Profile-only PATCH skips role-grant synchronization. Roles, group, login, and unrelated identity fields are not part of this editor.

## Changed files

- `frontends/mobile-core/src/features/admin-users/AdminUsersScreen.vue`
- `frontends/mobile-core/src/features/admin-users/admin-users-client.ts`
- `frontends/mobile-core/src/features/admin-users/admin-users-client.test.ts`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/user/PatchUserRequest.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServicePatchProfileTest.java`

## Verification and evidence

- `git diff --check` — exit 0.
- `npm run typecheck --workspace @rct/mobile-core` — exit 0.
- `npm run typecheck --workspace @rct/pwa-vue` — exit 0.
- `npm run typecheck --workspace @rct/tma-vue` — exit 0.
- Scoped ESLint for `AdminUsersScreen.vue`, `admin-users-client.ts`, and `admin-users-client.test.ts` — exit 0, no warnings.
- Focused Vitest `admin-users-client.test.ts` — exit 0, 2 tests passed. The initial test assertion expected validation to reject asynchronously; it was corrected to match the client's synchronous input validation and rerun successfully.
- Focused Gradle batch `academic-app:test --tests ru.rutcampustrack.academic.user.UserServicePatchProfileTest` plus `academic-api-contract:check` — exit 0, `BUILD SUCCESSFUL` (31s). The contract module has no test sources; its compile/check tasks were up to date. The first Gradle attempt exited 1 because the newly added test helper stubbed getters unused by rejection scenarios; stubs were narrowed per scenario and the focused batch passed. Existing deprecation warnings were emitted from `UserRepositorySearchIT`.
- Full `npm run lint --workspace @rct/mobile-core` — exit 1 from unrelated existing workspace findings (2 errors and 616 warnings); none were in the three scoped files, whose targeted lint passed. No unrelated files were changed.

## Runtime limits

No browser/PWA/TMA session or database-backed API runtime was run. Existing ADMIN endpoint authority, native archived-aware duplicate lookup, unique-constraint handling, and role grant writer were inspected; this batch does not claim end-to-end or production readiness. No secrets, environment files, or credentials were read.

## Working-tree preservation

Worktree branch: `codex/admin-profile-edit-20260924`, based on `f0b3bc62`. The pre-existing modified `.agent/orchestration-v2/RULES.md` and `LEAF-PACKET.md` were left untouched and unstaged. A separate one-file BFF wrapper fix was committed independently as `78fd9360`; it is not part of this ADMIN change set.
