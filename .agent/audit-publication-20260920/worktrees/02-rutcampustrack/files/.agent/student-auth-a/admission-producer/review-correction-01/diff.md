# Review correction 01 diff and limitations

The bounded source delta contains two changed paths:

1. `SessionAdmissionService.java`: renamed the command-only time to
   `initialNow`; moved the post-snapshot `freshNow` read directly after the
   authoritative snapshot identity check; changed liveness to use only
   `freshNow`; removed the old request-time liveness block; added a `returnNow`
   check after signing so an expired session or token window cannot produce a
   response. The existing strict whole-second `iat`/TTL/`exp` calculations and
   signer/response snapshot reuse remain intact.
2. `SessionAdmissionServiceTest.java`: added a mutable-clock, no-sleep case
   where the session expires during the snapshot and asserts `SESSION_REVOKED`,
   one initial snapshot command, and no internal signer call; added two
   mutable-clock signing-delay cases for internal-expiry and session-expiry
   denials, with one signer call each; added an expiry parameter to the existing
   snapshot fixture helper.
3. `JwtTokenPurposeTest.java`: added raw signed-token coverage for removal of
   each mandatory access claim, acceptance without optional `group_id`, and
   explicit semantic mismatches; extracted an omitted-claim helper while
   retaining the existing role/status/decimal/time matrix. A focused auth run
   then found the JJWT audience representation is `Set<String>`; the assertion
   now requires the exact singleton with `containsExactly(AUDIENCE)`.

`InternalSessionAdmissionIT.java` and `InternalJwtValidatorTest.java` were
audited under the assigned five-path ownership boundary and unchanged by this
leaf. Their exact current hashes are included in `manifest.json`; preserving
the existing validator-test orphan diff was required to keep foreign work
intact. The prior focused auth failure (36 total, 35 successful, one audience
assertion failure at line 144) is recorded in `evidence.md`; the existing
runtime-final artifact is untouched and root owns the focused rerun.

Limitations: source-stage static evidence only. Runtime, compilation, focused
unit/integration execution, downstream union compilation, and independent
post-correction Sol recheck are root-owned and remain open. No files were
staged, committed, pushed, reset, deleted, or reverted.
