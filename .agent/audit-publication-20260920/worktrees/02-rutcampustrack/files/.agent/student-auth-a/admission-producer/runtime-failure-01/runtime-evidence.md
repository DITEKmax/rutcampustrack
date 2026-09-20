# Runtime failure-01 evidence

Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
Source-hash artifact: `../source-hashes.json`, SHA256
`DF8896D704B015E75C7266BCC0E6AAB1F56BDCC98D03276E75878C1342DCDA98`, 5613
bytes.

Preguard was recorded as source `17/17`, Java/Gradle processes `0`, reserved
ports free, and Docker query empty. The shared-security command was:

```text
.\gradlew.bat :services:shared:shared-security:test --tests 'ru.rutcampustrack.shared.security.InternalJwtValidatorTest' --tests 'ru.rutcampustrack.shared.security.DualModeUserContextFilterTest' --no-daemon --no-parallel --max-workers=1 --console=plain
```

It exited `0` with `BUILD SUCCESSFUL in 59s`. `InternalJwtValidatorTest` was
8/0/0/0, XML SHA256
`4A2CB52F2DC2F98BF1343E623AAD7E4C29006E8E5F53A7BD0E3918F9E8F3CF65`, 1510
bytes. `DualModeUserContextFilterTest` was 8/0/0/0, XML SHA256
`C248B3DA9361E70D837FECF89E8234DD749D2B50592A7C4133E67F8ED4DF7F9F`, 2561
bytes.

The auth command was:

```text
.\gradlew.bat :services:auth-service:auth-app:test --tests 'ru.rutcampustrack.auth.service.JwtTokenPurposeTest' --tests 'ru.rutcampustrack.auth.config.JwtAuthenticationFilterPurposeTest' --tests 'ru.rutcampustrack.auth.session.SessionAdmissionServiceTest' --tests 'ru.rutcampustrack.auth.arch.AuthApiContractTest' --no-daemon --no-parallel --max-workers=1 --console=plain
```

It exited `1` with `BUILD FAILED in 1m16s`: 25 tests completed, 4 failed,
0 errors, 0 skipped. Passing suites: `AuthApiContractTest` 3/0/0/0,
SHA256 `387E14C7546CD3AB279E3001C6BFE4B37C52BBE1C2FB9CEFFE7F577CAEC28D5B`,
847 bytes; `JwtAuthenticationFilterPurposeTest` 5/0/0/0, SHA256
`4DE1C95E4F61EB2BD181F1D204766B82AF0540D55F0B030F6E316DD6093BBD63`, 1972
bytes.

The two `JwtTokenPurposeTest` failures were deterministic expired-fixture
failures: both fixed positive cases used `iat=2026-09-10T09:00:00Z` and
`exp=09:01:00Z`, while the run was at 18:39Z. The XML is
`BFD5510E1BA12C453A9A74B7B9A0B014928364DAC1FB3E70594795FC28EDF151`, 4707
bytes; suite count 10/2/0/0. Stack locations were lines 119 and 140 in
`JwtTokenPurposeTest`, both `io.jsonwebtoken.ExpiredJwtException` from
`JwtService.parseSignedToken`.

The two `SessionAdmissionServiceTest` failures were deterministic fixture
identity mismatches: the active setup omitted `group_id`, while the coherent
snapshot had group `10`, producing `SESSION_STATE_STALE` at
`requireIdentityMatch` line 204 (test lines 81 and 187). XML is
`066B5F440DAB9C3D377457DFA8B556EBF3F82C951D64F40366B99C53B609EB37`, 3045
bytes; suite count 7/2/0/0.

The batch stopped after command 2. Command 3 was **NOT RUN**:

```text
.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests 'ru.rutcampustrack.auth.integration.InternalSessionAdmissionIT' --no-daemon --no-parallel --max-workers=1 --console=plain
```

Postguard was recorded as source `17/17`, Java/Gradle processes `0`, and
reserved ports free. The first default-sandbox Docker cleanup query was
access-denied; the required escalated read-only query exited `0` with empty
output (`containers=0`). No Testcontainers command ran, so no cleanup action
was needed.

The six XML files under `junit/` are byte-for-byte copies of the current Gradle
reports. This batch is recorded as `HEAVY RELEASE` for evidence provenance;
command 2 is a failure and command 3 remains open for a future bounded
correction/runtime gate.
