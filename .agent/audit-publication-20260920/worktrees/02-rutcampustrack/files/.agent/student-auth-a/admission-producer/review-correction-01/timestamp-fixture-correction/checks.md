# Checks

Environment: Windows PowerShell, shared checkout
`C:\Users\maksd\.codex\worktrees\1456\rutcampustrack`; revision
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`; no Gradle, Docker or product
runtime was run.

1. **Pre-edit source guard — PASS, exit code 0.**
   Command: `Get-FileHash -Algorithm SHA256 -LiteralPath
   services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java`
   plus `Get-Item ... | Select-Object Length` before edit. Evidence:
   `FA8FB0AA20074D494CCCC5B89D014275C3EB9582C723E3DE086EF665F5B162F9`, 27566
   bytes, matching the assigned precondition.

2. **Post-edit source hash — PASS, exit code 0.**
   Command: `Get-FileHash -Algorithm SHA256 -LiteralPath
   services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java`
   plus `Get-Item ... | Select-Object Length`. Evidence:
   `FB13C6300FB7EDB776135BCE4C3FE7276E8FCB220411C75B277A9EAEB6621389`, 28120
   bytes.

3. **Helper call count — PASS, exit code 0.**
   Command: `rg -c '^\s*updateGrantStatus\('
   `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java`.
   Output: `4`, covering the four call sites; the declaration is not counted by
   the anchored call-site pattern.

4. **Single safe SQL writer — PASS, exit code 0.**
   Command: `rg -c 'UPDATE user_role_grants SET status = \?, updated_at = \? WHERE id = \? AND user_id = \?'
   <target>`. Output: `1`.

5. **Per-grant timestamp source — PASS, exit code 0.**
   Command: `rg -c 'grant\.createdAt\(\)\.plusSeconds\(1\)' <target>`.
   Output: `1`.

6. **Direct PostgreSQL invariant query — PASS, exit code 0.**
   Command: `rg -c 'SELECT updated_at >= created_at' <target>`. Output: `1`;
   this query executes after the helper update and before the caller's
   admission assertion.

7. **Stale grant timestamp absence — PASS, expected absence, exit code 1.**
   Command: `rg -c 'Timestamp\.from\(now\.plusSeconds\(1\)\)' <target>`.
   No match was returned. The remaining `now.plusSeconds(1)` usages are
   revoke/event times and do not update grants.

8. **Scoped whitespace/diff check — PASS, exit code 0.**
   Command: `git diff --check -- <target>`. The target was already untracked in
   `HEAD`, so Git reports no tracked diff; the source was additionally scanned
   with `rg -n '[ \t]+$' <target>` (expected no match, exit code 1).

9. **Scope status check — PASS, exit code 0.**
   Command: `git status --short --untracked-files=all | Select-String -Pattern
   'InternalSessionAdmissionIT|timestamp-fixture-correction'`. The target and
   evidence are the only paths from this leaf; pre-existing dirty paths remain
   present.

10. **HEAD membership diagnostic — recorded, expected exit code 1.**
    Command: `git cat-file -e HEAD:<target>`. The target is an existing dirty
    untracked file rather than a committed baseline path, so this expectedly
    returned exit code 1. The exact pre-edit hash guard above is the applicable
    baseline protection.
