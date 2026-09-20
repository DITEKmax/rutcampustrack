# Gateway C final recheck evidence

Date: 2026-09-10 (Europe/Moscow). Evidence-only follow-up; no product/test
source, canonical manifest, runtime configuration, Docker state, or network
state was changed. Baseline: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

## Root-approved checks

Command 1 exited `0`, `BUILD SUCCESSFUL in 1m 8s` (root tool start
`2026-09-10T19:07:56.605874Z`). Exact command:

```powershell
.\gradlew.bat :services/api-gateway:test --tests ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest --tests ru.rutcampustrack.gateway.security.InternalJwtIssuerClientTest --tests ru.rutcampustrack.gateway.security.InternalJwtIssuerFilterTest --tests ru.rutcampustrack.gateway.security.InternalIssuerClientPropertiesTest --tests ru.rutcampustrack.gateway.ratelimit.RedisRateLimiterConfigTest --no-daemon --no-parallel --max-workers=1 --console=plain --continue
```

| XML | Timestamp | Tests | Failures/errors/skipped | SHA-256 |
| --- | --- | ---: | --- | --- |
| `TEST-ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest.xml` | `2026-09-10T19:08:53` | 53 | 0/0/0 | `CBC21E997C7BB6EC5282ECB90D36B04A397864AED20C50AB2251CD788E1C2AAE` |
| `TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerClientTest.xml` | `2026-09-10T19:09:00` | 5 | 0/0/0 | `15D98AC8406C67CAF4B3E60892B1BE44EA7EBB3DDA6614EE1F359EC0674EC7D8` |
| `TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerFilterTest.xml` | `2026-09-10T19:08:58` | 10 | 0/0/0 | `D4605F9D9F4D19F0BC50155D59C9E72FBE42064C5238AC3E125D87302E0CF184` |
| `TEST-ru.rutcampustrack.gateway.security.InternalIssuerClientPropertiesTest.xml` | `2026-09-10T19:09:00` | 5 | 0/0/0 | `8FCB3D3D520E1994DA7C6F2759F13957F40CC47B079F14005C191BEEF6245AEB` |
| `TEST-ru.rutcampustrack.gateway.ratelimit.RedisRateLimiterConfigTest.xml` | `2026-09-10T19:09:00` | 10 | 0/0/0 | `E18A3D2627C09C733DA9EE6F3C5B41A46A9C09D60CC5EFC5E4C511F8FF22BA47` |
| **Total** | — | **83** | **0/0/0** | — |

Command 2 exited `0`, `BUILD SUCCESSFUL in 1m 5s`:

```powershell
.\gradlew.bat :services/api-gateway:integrationTest --tests ru.rutcampustrack.gateway.security.InternalJwtIssuerIT --no-daemon --no-parallel --max-workers=1 --console=plain --continue
```

| XML | Timestamp | Tests | Failures/errors/skipped | SHA-256 |
| --- | --- | ---: | --- | --- |
| `TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerIT.xml` | `2026-09-10T19:10:13` | 6 | 0/0/0 | `A6A76B7C4BE7204FEB525113F414F48349F63131821634FBAC2BD1320728F403` |

## PASS report copies

The six current PASS XMLs were copied with `Copy-Item` (each exit `0`) into
`.agent/student-gateway-c/admission/pass-final/`, preserving filenames. The
source and destination SHA-256 values matched for every pair:

| Filename | Source | Destination | SHA-256 |
| --- | --- | --- | --- |
| `TEST-ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest.xml` | `build/test-results/test/` | `admission/pass-final/` | `CBC21E997C7BB6EC5282ECB90D36B04A397864AED20C50AB2251CD788E1C2AAE` |
| `TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerClientTest.xml` | `build/test-results/test/` | `admission/pass-final/` | `15D98AC8406C67CAF4B3E60892B1BE44EA7EBB3DDA6614EE1F359EC0674EC7D8` |
| `TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerFilterTest.xml` | `build/test-results/test/` | `admission/pass-final/` | `D4605F9D9F4D19F0BC50155D59C9E72FBE42064C5238AC3E125D87302E0CF184` |
| `TEST-ru.rutcampustrack.gateway.security.InternalIssuerClientPropertiesTest.xml` | `build/test-results/test/` | `admission/pass-final/` | `8FCB3D3D520E1994DA7C6F2759F13957F40CC47B079F14005C191BEEF6245AEB` |
| `TEST-ru.rutcampustrack.gateway.ratelimit.RedisRateLimiterConfigTest.xml` | `build/test-results/test/` | `admission/pass-final/` | `E18A3D2627C09C733DA9EE6F3C5B41A46A9C09D60CC5EFC5E4C511F8FF22BA47` |
| `TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerIT.xml` | `build/test-results/integrationTest/` | `admission/pass-final/` | `A6A76B7C4BE7204FEB525113F414F48349F63131821634FBAC2BD1320728F403` |

Copy verification: `Get-FileHash -Algorithm SHA256` on all six source and
destination pairs, XML metadata readback, and line-count comparison; exit `0`,
6/6 hashes equal. No report was regenerated after copying.

## Failure history and cleanup

The pre-correction 81-test failure remains at
`.agent/student-gateway-c/admission/failure-81-r2/`; the contract and defect
details are in `null-group-contract-r2.md`. It records access `53/1/0/0`
(`group_id:null` accepted) and internal `8/1/0/0` (`NotAMockException` from
`verify(lambda)`). The final checks above are the root-owned correction
recheck: command 1 `83/0/0/0`, command 2 `6/0/0/0`.

Root's post-run PowerShell source/resource guard exited `0` and reported
`CANONICAL count=15 mismatches=0` and `javaCount=0 listenerCount=0` for ports
18500–18541. No Docker runtime was started. The guard command was:

```powershell
$j=Get-Content .agent\student-gateway-c\admission\canonical-changed-paths.json -Raw|ConvertFrom-Json;$bad=@();foreach($e in $j.stagePaths){$h=(Get-FileHash -Algorithm SHA256 -LiteralPath $e.path).Hash;if($h-ne$e.sha256){$bad+=$e.path}};"CANONICAL count=$($j.stagePaths.Count) mismatches=$($bad.Count)";$bad;$jp=Get-Process java,javaw -ErrorAction SilentlyContinue;$lp=Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue|Where-Object{$_.LocalPort-ge18500-and$_.LocalPort-le18541};"javaCount=$(@($jp).Count) listenerCount=$(@($lp).Count)";$jp|Select-Object Id,ProcessName,StartTime;$lp|Select-Object LocalPort,OwningProcess
```

This file and `pass-final/**` are the only follow-up evidence writes. Broader
deployment or production runtime evidence is outside this follow-up.
