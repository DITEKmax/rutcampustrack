# Gateway identity stripping r4 — root recheck evidence

Date: 2026-09-10 (Europe/Moscow). Revision under test:
8002b9ea4356b10779c5bb9a6d99746d32d78ae2 plus the released working tree.
Evidence-only follow-up: no product source, test source, or canonical manifest was
edited.

## Exact targeted checks

Two-unit selector (exit 0, BUILD SUCCESSFUL in 1m 6s):
.\\gradlew.bat :services/api-gateway:test --tests ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest --tests ru.rutcampustrack.gateway.security.InternalJwtIssuerFilterTest --no-daemon --no-parallel --max-workers=1 --console=plain --continue

Integration selector (exit 0, BUILD SUCCESSFUL in 1m 11s):
.\\gradlew.bat :services/api-gateway:integrationTest --tests ru.rutcampustrack.gateway.security.InternalJwtIssuerIT --no-daemon --no-parallel --max-workers=1 --console=plain --continue

| XML | Timestamp | Tests | Failures | Errors | Skipped | SHA-256 |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| TEST-ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest.xml | 2026-09-10T19:41:15 | 53 | 0 | 0 | 0 | 0870619787400817BD059DC325D0D8B6DE7E1A17F00FA02D32B51BB57EB5F51D |
| TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerFilterTest.xml | 2026-09-10T19:41:20 | 10 | 0 | 0 | 0 | ED26831CA38A67B5CBCF2390D0C9C3FCC943438E85D2188315EA6F4C652F436D |
| TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerIT.xml | 2026-09-10T19:42:34 | 6 | 0 | 0 | 0 | 9E0E5717C06C92C05E1F4B6B13A071559182245865E42BC1E2DCF229EC645B9E |

## XML copy verification

The three current module XML files were copied into
.agent/student-gateway-c/admission/pass-r4/. The source/copy SHA verification
exited 0 and returned equal=True for every pair.

Source/copy pairs:
- services/api-gateway/build/test-results/test/TEST-ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest.xml -> .agent/student-gateway-c/admission/pass-r4/TEST-ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest.xml: 0870619787400817BD059DC325D0D8B6DE7E1A17F00FA02D32B51BB57EB5F51D / 0870619787400817BD059DC325D0D8B6DE7E1A17F00FA02D32B51BB57EB5F51D, equal=True.
- services/api-gateway/build/test-results/test/TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerFilterTest.xml -> .agent/student-gateway-c/admission/pass-r4/TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerFilterTest.xml: ED26831CA38A67B5CBCF2390D0C9C3FCC943438E85D2188315EA6F4C652F436D / ED26831CA38A67B5CBCF2390D0C9C3FCC943438E85D2188315EA6F4C652F436D, equal=True.
- services/api-gateway/build/test-results/integrationTest/TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerIT.xml -> .agent/student-gateway-c/admission/pass-r4/TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerIT.xml: 9E0E5717C06C92C05E1F4B6B13A071559182245865E42BC1E2DCF229EC645B9E / 9E0E5717C06C92C05E1F4B6B13A071559182245865E42BC1E2DCF229EC645B9E, equal=True.

XML readback command exited 0:
services/api-gateway/build/test-results/test/TEST-ru.rutcampustrack.gateway.filter.JwtAuthenticationFilterTest.xml tests=53 failures=0 errors=0 skipped=0 timestamp=2026-09-10T19:41:15
services/api-gateway/build/test-results/test/TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerFilterTest.xml tests=10 failures=0 errors=0 skipped=0 timestamp=2026-09-10T19:41:20
services/api-gateway/build/test-results/integrationTest/TEST-ru.rutcampustrack.gateway.security.InternalJwtIssuerIT.xml tests=6 failures=0 errors=0 skipped=0 timestamp=2026-09-10T19:42:34

## Root postguard

Exact command:
$j=Get-Content .agent\\student-gateway-c\\admission\\canonical-changed-paths.json -Raw|ConvertFrom-Json;$bad=@();foreach($e in $j.stagePaths){$h=(Get-FileHash -Algorithm SHA256 -LiteralPath $e.path).Hash;if($h-ne$e.sha256){$bad+=$e.path}};"CANONICAL count=$($j.stagePaths.Count) mismatches=$($bad.Count)";$bad;$jp=Get-Process java,javaw -ErrorAction SilentlyContinue;$lp=Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue|Where-Object{$_.LocalPort-ge18500-and$_.LocalPort-le18541};"javaCount=$(@($jp).Count) listenerCount=$(@($lp).Count)";$jp|Select-Object Id,ProcessName,StartTime;$lp|Select-Object LocalPort,OwningProcess

Exit 0. Observed: CANONICAL count=15 mismatches=0; javaCount=0
listenerCount=0; noDocker. No deployment, production state, or network state
was changed.

## Pre-repair evidence boundary

admission/final-runtime-evidence.md contains the earlier 83/0/0/0 unit total and
6/0/0/0 IT result. Those results predate r4 and did not inject forged mixed-case
X-Group-Id/X-Is-Headman headers. Old evidence and PASS XML copies remain
unchanged and are not reused as proof of this repair.

This file records the post-repair targeted recheck and the three exact copies in
admission/pass-r4/. Static repair evidence remains in identity-strip-r4.md.
The recheck does not claim a full Gradle battery, production readiness,
deployment, scanner result, or independent review beyond the root-reported
targeted recheck.
