# Review correction 01 summary

`SOURCE_READY_FOR_ROOT_AUDIT` / `RELEASE` for the bounded Sol finding repair.
The producer now uses an initial clock value only for the single snapshot
command and a fresh post-snapshot clock value for liveness and internal JWT
time claims. A post-sign return-time guard prevents an expired session or token
window from reaching the DTO. Deterministic mutable-clock tests cover snapshot
expiry, internal expiry during signing, and session expiry during signing.
`JwtTokenPurposeTest` now proves every mandatory access claim is required and
that optional `group_id` may be omitted. Exact five-path hashes, static exits,
finding, correction, and limitations are recorded in this directory.

The focused auth failure was a test-only audience representation mismatch
(`Set<String>` versus `String`); `JwtTokenPurposeTest` now asserts the exact
singleton. Its updated hash is in `manifest.json`; root must rerun the focused
auth selector. Existing runtime-final evidence was not overwritten.

Heavy checks are `HEAVY NOT RUN/NOT ACQUIRED`; root must perform the runtime and
independent recheck gates after accepting this source handoff.
