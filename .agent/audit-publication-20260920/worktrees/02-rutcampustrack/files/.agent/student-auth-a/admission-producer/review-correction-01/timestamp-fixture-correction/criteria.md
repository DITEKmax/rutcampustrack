# Acceptance criteria

1. Exactly four grant status mutation call sites use
   `updateGrantStatus(user, role, status)`.
2. The helper resolves the matching `RoleGrant` from `UserFixture` and derives
   `updated_at` from its own `createdAt().plusSeconds(1)`.
3. The persisted status is lower-case through
   `status.name().toLowerCase(Locale.ROOT)`.
4. After every helper update and before admission, a direct PostgreSQL boolean
   query asserts `updated_at >= created_at` for the exact grant (`id` plus
   `user_id`).
5. No grant mutation uses `Timestamp.from(now.plusSeconds(1))`; revoke/event
   timestamps retain their existing semantics.
6. Real authority, Spring constructor wiring, typed status/code mappings,
   no-store behavior and all existing test scenarios remain unchanged.
7. The leaf source diff is limited to the assigned test file, with new
   evidence only below this directory.
