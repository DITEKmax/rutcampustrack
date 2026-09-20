# Diff

Only the assigned test source changed in the code scope.

Target:
`services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java`

- Before: `FA8FB0AA20074D494CCCC5B89D014275C3EB9582C723E3DE086EF665F5B162F9`,
  27566 bytes.
- After: `FB13C6300FB7EDB776135BCE4C3FE7276E8FCB220411C75B277A9EAEB6621389`,
  28120 bytes.
- Delta: +554 bytes.

Bounded source locations after the correction:

- Inline mutation at former stale-grant site: line 139 →
  `updateGrantStatus(user, AuthRole.STUDENT, RoleStatus.GRADUATED)`.
- Inline mutation at direct suspended test: line 168 →
  `updateGrantStatus(user, AuthRole.STUDENT, RoleStatus.SUSPENDED)`.
- HTTP suspended fixture: line 279 → the same helper with `suspendedUser`.
- HTTP stale fixture: line 284 → the same helper with `staleUser`.
- Shared writer and boolean assertion: lines 460–478.
- Exact fixture lookup: lines 480–488.

The helper adds `user_id` to the update predicate and reads
`SELECT updated_at >= created_at` by the exact grant id and fixture user id.
Status wire values remain lower-case. Existing `now.plusSeconds(1)` revoke and
security-event timestamps remain untouched. No production, contract, migration,
domain or mapping code changed.

`git diff --check -- <target>` returned exit code 0; because the source was
untracked at `HEAD`, the evidence also records direct whitespace scanning and
the exact source hashes.
