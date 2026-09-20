param(
    [switch]$Verify
)

Set-StrictMode -Version Latest

$correctionRoot = $PSScriptRoot
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $correctionRoot '..\..\..\..'))
$manifestPath = Join-Path $correctionRoot 'manifest.json'

function Get-HashedFile {
    param([string]$RelativePath)

    $fullPath = Join-Path $repoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "Required evidence file is absent: $RelativePath"
    }
    $item = Get-Item -LiteralPath $fullPath
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $fullPath).Hash
    return [ordered]@{
        path = $RelativePath
        bytes = [int64]$item.Length
        sha256 = $hash
    }
}

$sourceDefinitions = @(
    [ordered]@{ path = 'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthSessionController.java'; kind = 'production'; status = 'modified'; previousBytes = 9279; previousSha256 = '05198B75FE4FBAAE3EC196974DF081D5BAE53073ACDE3EFF5763180F91071E28' },
    [ordered]@{ path = 'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/AuthService.java'; kind = 'production'; status = 'modified'; previousBytes = 29997; previousSha256 = 'EB630E8528FF2D030C37D4C75AA001AD7F0A473AC2D4F1DFCFAEB51E9E1D4F17' },
    [ordered]@{ path = 'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java'; kind = 'production'; status = 'modified'; previousBytes = 11967; previousSha256 = 'BA58F785FF3FF5A438BFFFD78CC1FA76FC0219929E9F7EEBCEC6BAFDE4DCCC94' },
    [ordered]@{ path = 'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/TmaService.java'; kind = 'production'; status = 'modified'; previousBytes = 4966; previousSha256 = 'ECA95F16B49EAE37D14032CBAF37278BB6F7B017D4CC65C1AF0875AF77668140' },
    [ordered]@{ path = 'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/controller/AuthSessionControllerTest.java'; kind = 'test'; status = 'modified'; previousBytes = 13792; previousSha256 = '4A7FF34CB06C855883FCEF139045959C253A19A8B40E7D296CEA3816A57B902E' },
    [ordered]@{ path = 'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/OtpServiceTest.java'; kind = 'test'; status = 'modified'; previousBytes = 11662; previousSha256 = '49772316029E561FD97FE6BD28EEABBDDCD54916EA6788174BE7368DF33ED6CD' },
    [ordered]@{ path = 'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/LogoutLifecycleIT.java'; kind = 'test'; status = 'modified'; previousBytes = 9322; previousSha256 = '76B4EA927B4C2703833764D4B36593CBA65CDFB16A6E722457E9662B18D07C79' },
    [ordered]@{ path = 'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OtpIT.java'; kind = 'test'; status = 'modified'; previousBytes = 9112; previousSha256 = 'DD8AC12B26BD65F7ADE5F4521E102F7A8DD81BBB0ACCEC9BB2242F6B49FFB928' },
    [ordered]@{ path = 'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/AuthServiceRepositoryFailureTest.java'; kind = 'test'; status = 'added'; previousBytes = $null; previousSha256 = $null },
    [ordered]@{ path = 'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/TmaServiceRepositoryFailureTest.java'; kind = 'test'; status = 'added'; previousBytes = $null; previousSha256 = $null }
)

$xmlDefinitions = @(
    [ordered]@{ id = 'AuthSessionControllerTest'; suite = 'AuthSessionControllerTest'; path = '.agent/student-auth-a/stage2/correction-02/junit/unit/TEST-ru.rutcampustrack.auth.controller.AuthSessionControllerTest.xml'; phase = 'unit'; session = '93390' },
    [ordered]@{ id = 'AuthServiceRepositoryFailureTest'; suite = 'AuthServiceRepositoryFailureTest'; path = '.agent/student-auth-a/stage2/correction-02/junit/unit/TEST-ru.rutcampustrack.auth.service.AuthServiceRepositoryFailureTest.xml'; phase = 'unit'; session = '93390' },
    [ordered]@{ id = 'OtpServiceTest'; suite = 'OtpServiceTest'; path = '.agent/student-auth-a/stage2/correction-02/junit/unit/TEST-ru.rutcampustrack.auth.service.OtpServiceTest.xml'; phase = 'unit'; session = '93390' },
    [ordered]@{ id = 'TmaServiceRepositoryFailureTest'; suite = 'TmaServiceRepositoryFailureTest'; path = '.agent/student-auth-a/stage2/correction-02/junit/unit/TEST-ru.rutcampustrack.auth.service.TmaServiceRepositoryFailureTest.xml'; phase = 'unit'; session = '93390' },
    [ordered]@{ id = 'LogoutLifecycleIT'; suite = 'LogoutLifecycleIT'; path = '.agent/student-auth-a/stage2/correction-02/junit/affected/TEST-ru.rutcampustrack.auth.integration.LogoutLifecycleIT.xml'; phase = 'affected-pg-redis'; session = '67640' },
    [ordered]@{ id = 'OtpIT'; suite = 'OtpIT'; path = '.agent/student-auth-a/stage2/correction-02/junit/affected/TEST-ru.rutcampustrack.auth.integration.OtpIT.xml'; phase = 'affected-pg-redis'; session = '67640' },
    [ordered]@{ id = 'SessionAuthFlowIT'; suite = 'SessionAuthFlowIT'; path = '.agent/student-auth-a/stage2/correction-02/junit/affected/TEST-ru.rutcampustrack.auth.integration.SessionAuthFlowIT.xml'; phase = 'affected-pg-redis'; session = '67640' },
    [ordered]@{ id = 'OpenApiSnapshotIT'; suite = 'OpenApiSnapshotIT'; path = '.agent/student-auth-a/stage2/correction-02/junit/openapi/TEST-ru.rutcampustrack.auth.integration.OpenApiSnapshotIT.xml'; phase = 'openapi-compare'; session = '98527' }
)

$sourceRecords = foreach ($definition in $sourceDefinitions) {
    $record = Get-HashedFile $definition.path
    $record['kind'] = $definition.kind
    $record['status'] = $definition.status
    $record['previousBytes'] = $definition.previousBytes
    $record['previousSha256'] = $definition.previousSha256
    $record
}

$junitRecords = foreach ($definition in $xmlDefinitions) {
    $record = Get-HashedFile $definition.path
    $fullPath = Join-Path $repoRoot $definition.path
    $document = [xml][System.IO.File]::ReadAllText($fullPath)
    $suite = $document.testsuite
    $record['id'] = $definition.id
    $record['suite'] = $definition.suite
    $record['phase'] = $definition.phase
    $record['session'] = $definition.session
    $record['tests'] = [int]$suite.tests
    $record['skipped'] = [int]$suite.skipped
    $record['failures'] = [int]$suite.failures
    $record['errors'] = [int]$suite.errors
    $record
}

$commands = @(
    [ordered]@{ id = 'compile-source-defect'; command = '.\gradlew.bat :services:auth-service:auth-app:compileJava :services:auth-service:auth-app:compileTestJava --no-daemon --no-parallel --max-workers=1 --console=plain'; session = '60085'; exitCode = 1; result = 'compileTestJava failed on stale verifyNoInteractions import; corrected and recorded in failures/compile-01/failure.md' },
    [ordered]@{ id = 'compile-infrastructure-defect'; command = '.\gradlew.bat :services:auth-service:auth-app:compileJava :services:auth-service:auth-app:compileTestJava --no-daemon --no-parallel --max-workers=1 --console=plain'; session = '12186'; exitCode = 1; result = 'Java compilation completed; Gradle failed moving generated problems-report.html; stale generated report removed' },
    [ordered]@{ id = 'compile-green'; command = '.\gradlew.bat :services:auth-service:auth-app:compileJava :services:auth-service:auth-app:compileTestJava --no-daemon --no-parallel --max-workers=1 --console=plain'; session = '13664'; exitCode = 0; result = 'compileJava and compileTestJava passed' },
    [ordered]@{ id = 'focused-unit'; command = '.\gradlew.bat :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.controller.AuthSessionControllerTest --tests ru.rutcampustrack.auth.service.OtpServiceTest --tests ru.rutcampustrack.auth.service.AuthServiceRepositoryFailureTest --tests ru.rutcampustrack.auth.service.TmaServiceRepositoryFailureTest --no-daemon --no-parallel --max-workers=1 --console=plain'; session = '93390'; exitCode = 0; result = '24/24 focused unit tests passed' },
    [ordered]@{ id = 'affected-pg-redis'; command = '.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.LogoutLifecycleIT --tests ru.rutcampustrack.auth.integration.OtpIT --tests ru.rutcampustrack.auth.integration.SessionAuthFlowIT --no-daemon --no-parallel --max-workers=1 --console=plain'; session = '65967'; exitCode = 0; result = 'affected PostgreSQL/Redis integration suites passed; XML was later captured by the already-started session 67640' },
    [ordered]@{ id = 'openapi-compare'; command = '.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.OpenApiSnapshotIT --no-daemon --no-parallel --max-workers=1 --console=plain'; session = '98527'; exitCode = 0; result = 'property-free OpenAPI snapshot compare passed' },
    [ordered]@{ id = 'affected-xml-capture'; command = '.\gradlew.bat :services:auth-service:auth-app:integrationTest --tests ru.rutcampustrack.auth.integration.LogoutLifecycleIT --tests ru.rutcampustrack.auth.integration.OtpIT --tests ru.rutcampustrack.auth.integration.SessionAuthFlowIT --no-daemon --no-parallel --max-workers=1 --console=plain'; session = '67640'; exitCode = 0; result = 'same affected suites passed; run was already started before fast-path no-rerun instruction and produced the saved XML' }
)

$reviewFile = Get-HashedFile '.agent/student-auth-a/stage2/review-fail.md'
$oldManifest = Get-HashedFile '.agent/student-auth-a/stage2/final-manifest.json'

$manifest = [ordered]@{
    schema = 'student-auth-stage2-correction-02-manifest-v1'
    status = 'FINAL_CORRECTION_EVIDENCE_READY'
    baselineRevision = '8002b9ea4356b10779c5bb9a6d99746d32d78ae2'
    risk = 'S3'
    correctionBase = $oldManifest
    reviewFail = $reviewFile
    sourceDelta = $sourceRecords
    junit = $junitRecords
    commands = $commands
    correction = [ordered]@{
        defects = @(
            'best-effort Redis WS-ticket cleanup after durable logout/logout-all/password change',
            'cookie-only logout returns authoritative revoke result and cleans user tickets',
            'one atomic Redis OTP proof check/consume shared by direct and by-code routes',
            'UserRepository DataAccessException maps to AUTHORITY_UNAVAILABLE in AuthService, OtpService and TmaService'
        )
        scope = @(
            'AuthSessionController.java', 'AuthService.java', 'OtpService.java', 'TmaService.java',
            'AuthSessionControllerTest.java', 'OtpServiceTest.java', 'LogoutLifecycleIT.java', 'OtpIT.java',
            'AuthServiceRepositoryFailureTest.java', 'TmaServiceRepositoryFailureTest.java'
        )
        decision = 'bounded correction accepted by root; no Gateway/BFF/shared contract/SQL/V24/OpenAPI source redesign'
    }
    checks = [ordered]@{
        sourceCompile = 'session 13664 exit 0'
        focusedUnit = 'session 93390 exit 0; 24/24, skipped/failures/errors 0'
        affectedPgRedis = 'sessions 65967 and 67640 exit 0; LogoutLifecycleIT 3/3, OtpIT 9/9, SessionAuthFlowIT 8/8'
        openApiCompare = 'session 98527 exit 0; OpenApiSnapshotIT 1/1'
        gitDiffCheck = 'exit 0; LF/CRLF conversion warnings only'
        manifestVerify = 'generate-correction-manifest.ps1 -Verify; run after generation'
    }
    runtime = [ordered]@{
        environment = 'Windows PowerShell, shared checkout, Java 21/Gradle wrapper, DITEK-PK'
        containers = 'PostgreSQL/Redis Testcontainers used by affected integration run; root-observed Docker process state is stopped/empty after runs; no fast-path re-probe'
        durableEvidence = 'cookie-only logout ticket consume returned 404 after cleanup; mixed direct/by-code OTP race produced one 200, one 401 and one auth_sessions row delta'
    }
    limitations = @(
        'This manifest covers the four review defects and affected selectors; broader accepted Stage2 evidence remains in final-manifest.json.',
        'Gateway/BFF integration, full WS protocol/open-socket revalidation, frontend UI and genuine TMA host QA remain outside this bounded correction.',
        'The saved affected XML came from session 67640, which was already running before the fast-path no-rerun instruction; no further rerun is performed.',
        'Fresh independent Sol recheck of this stable correction diff is still required by the root workflow.',
        'No deploy, migration, destructive database action, commit, stage or reset was performed.'
    )
    generator = [ordered]@{ path = '.agent/student-auth-a/stage2/correction-02/generate-correction-manifest.ps1'; purpose = 'recompute source/XML/review/base hashes and counts'; verify = 'invoke with -Verify; no product/runtime command' }
}

if ($Verify) {
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "Manifest is absent: $manifestPath"
    }
    $existing = [System.IO.File]::ReadAllText($manifestPath) | ConvertFrom-Json
    foreach ($entry in $existing.sourceDelta) {
        $actual = Get-HashedFile $entry.path
        if ($actual.bytes -ne [int64]$entry.bytes -or $actual.sha256 -ne $entry.sha256) {
            throw "Source hash mismatch: $($entry.path)"
        }
    }
    foreach ($entry in $existing.junit) {
        $actual = Get-HashedFile $entry.path
        if ($actual.bytes -ne [int64]$entry.bytes -or $actual.sha256 -ne $entry.sha256) {
            throw "JUnit hash mismatch: $($entry.path)"
        }
        $fullPath = Join-Path $repoRoot $entry.path
        $suite = ([xml][System.IO.File]::ReadAllText($fullPath)).testsuite
        if ([int]$suite.tests -ne [int]$entry.tests -or
            [int]$suite.skipped -ne [int]$entry.skipped -or
            [int]$suite.failures -ne [int]$entry.failures -or
            [int]$suite.errors -ne [int]$entry.errors) {
            throw "JUnit result mismatch: $($entry.path)"
        }
        if ([int]$suite.skipped -ne 0 -or [int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0) {
            throw "Non-zero JUnit result: $($entry.path)"
        }
    }
    Write-Output 'MANIFEST_VERIFY PASS'
    exit 0
}

$json = $manifest | ConvertTo-Json -Depth 12
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($manifestPath, $json + [Environment]::NewLine, $utf8NoBom)
Write-Output "WROTE $manifestPath"
