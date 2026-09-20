# Review correction 01 evidence

## Request and reproduction

Fresh replacement leaf request: repair the independent Sol high finding in the
frozen admission producer. The finding was that admission could validate
authority against a time captured before a delayed snapshot and then derive
`iat`/TTL/`exp` from a reused or stale time reference. A delayed callback is the
deterministic reproduction: the clock starts at
`2026-09-10T10:00:00.500Z`, the single snapshot callback advances it to
`2026-09-10T10:00:05.250Z`, and signing must use the latter instant. The
pre-correction audit also found the remaining `snapshot.isLiveAt(requestNow)`
check, so the request-time value was still used beyond the snapshot command.

## Correction

`SessionAdmissionService` now names the command instant `initialNow`, passes it
only to the one `SnapshotCommand`, reads `freshNow` immediately after the
authoritative snapshot, and uses that value for liveness, original-access
expiry, whole-second `iat`, TTL calculation, and the strict expiry gate. The
old request-time liveness check was removed. After signing it samples
`returnNow` and rejects a session or token window that expired during signing;
the token is never placed in a response in those cases.

`SessionAdmissionServiceTest` adds no-sleep mutable-clock cases for session
expiry during the snapshot, internal expiry during signing, and session expiry
during signing. The first case verifies no signer call; the two signing-delay
cases verify one signer call and the exact typed denial.

`JwtTokenPurposeTest` now removes every mandatory access claim from a raw signed
token and expects rejection, accepts a raw signed token without optional
`group_id`, and explicitly rejects SUSPENDED, headman-flag, ACTIVE/read-only,
and terminal/read-write semantic mismatches. A focused auth run then exposed a
fixture assertion defect: JJWT exposes `Claims.getAudience()` as a singleton
`Set<String>`, while the test compared it with a `String`. The bounded follow-up
changed that assertion to `containsExactly(AUDIENCE)`, preserving strict
singleton semantics without changing production code.

Failure provenance from the focused auth run: 36 tests executed, 35 succeeded,
and `JwtTokenPurposeTest.sessionAccessTokenCarriesFrozenWireAndLegacyAccessIsNotAdmitted`
failed at line 144 on the String-vs-singleton-Set assertion. This correction
does not overwrite the existing `runtime-final` evidence; it records the new
source hash and the exact fixture correction for root's focused rerun.

The three other owned paths were audited and preserved byte-for-byte during
this correction. The tracked validator-test diff is foreign/orphan work in the
shared checkout and was not reverted.

## Source manifest

`manifest.json` records the exact five-path before/after hashes and byte counts,
including unchanged audited paths, plus the accepted 13-file Auth13 manifest
reference. `diff.md` describes the bounded source delta.

`SOURCE_READY_FOR_ROOT_AUDIT` is the highest status claimed by this leaf. No
runtime or test execution result is claimed here.
