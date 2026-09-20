# Correction-01 diff and limitations

Intended source delta, limited to two untracked producer test fixtures:

1. `JwtTokenPurposeTest.java`: two positive signed-token fixtures now use
   `Instant.now().truncatedTo(ChronoUnit.SECONDS)` and retain
   `issuedAt.plusSeconds(60)`. Negative purpose and legacy-access assertions
   remain in place.
2. `SessionAdmissionServiceTest.java`: the default claims mock now returns
   `"10"` for `group_id`; `terminalGrantIsAdmittedReadOnly` explicitly returns
   null for that claim and keeps `EXPELLED`/read-only assertions.

The files were already untracked producer additions in the shared dirty
checkout, so ordinary `git diff` does not render their content. Exact
before/after SHA256, byte counts, and the source-17 drift classification are
the authoritative bounded diff evidence in `manifest.json` and `checks.md`.

Foreign work remains present and untouched: existing auth/shared edits,
Auth13 files, other role work, deletions, and untracked evidence/worktrees.
This leaf did not stage, commit, reset, revert, delete, or alter any of it.
The correction package does not claim full producer runtime acceptance or
independent review; those remain root-owned gates.
