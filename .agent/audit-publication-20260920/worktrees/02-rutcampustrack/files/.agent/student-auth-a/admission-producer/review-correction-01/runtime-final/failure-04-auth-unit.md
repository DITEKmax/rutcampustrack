# Runtime failure 04

Status: `TEST FIXTURE FAIL / HEAVY RELEASE`.

The escalated Auth four-selector command compiled and executed 36 tests, then
exited `1` with one deterministic failure and 35 passes. PostgreSQL IT did not
run.

`JwtTokenPurposeTest.sessionAccessTokenCarriesFrozenWireAndLegacyAccessIsNotAdmitted`
expected `Claims.getAudience()` to equal the string `rutcampustrack`, while
JJWT 0.12.6 returned the correct singleton audience set `[rutcampustrack]`.
The token had already passed the strict parser. The bounded correction is to
assert the singleton collection value; no product change is justified.

All four generated XML files were copied byte-for-byte under
`failure-04-junit/`: `JwtTokenPurposeTest` 15 tests/1 failure; filter 5/0;
`SessionAdmissionServiceTest` 13/0; `AuthApiContractTest` 3/0. The exact log
and metadata are `command-02-escalated-auth-unit.log` and
`command-02-escalated-meta.json`.
