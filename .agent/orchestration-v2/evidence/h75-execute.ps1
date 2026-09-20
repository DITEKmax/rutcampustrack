param([Parameter(Mandatory=$true)][string]$ExpectedTestHash)
$ErrorActionPreference = 'Stop'
$rootPath = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack'
$treePath = Join-Path $rootPath '.agent/worktrees/v2-assignment-authority'
$evidencePath = Join-Path $rootPath '.agent/orchestration-v2/evidence'
$testPath = Join-Path $treePath 'services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentAuthorityIT.java'
$subjectPath = Join-Path $treePath 'services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java'
$actualTestHash = (Get-FileHash $testPath).Hash
if ($actualTestHash -ne $ExpectedTestHash) { throw 'Frozen test hash mismatch' }
$subjectHash = (Get-FileHash $subjectPath).Hash
if ($subjectHash -ne '377BF9B1E2F6E2B88529DD362177F0C47318D7A6C31561C4E516FA89491AFFA3') { throw 'Production baseline changed before test-only class verification' }
if (Test-Path (Join-Path $evidencePath 'h75-context.json')) { throw 'H75 evidence already exists; refuse overwrite' }
$context = [ordered]@{ start = (Get-Date).ToUniversalTime().ToString('o'); identity = (& whoami); base_revision = (& git -C $treePath rev-parse HEAD); testHash = $actualTestHash; subjectServiceHash = $subjectHash; command = '.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.assignment.AssignmentAuthorityIT --no-daemon --no-parallel --max-workers=1 --no-problems-report'; testcontainers_reuse_enable = 'false' }
$env:TESTCONTAINERS_REUSE_ENABLE = 'false'
Push-Location $treePath
try {
    & .\gradlew.bat :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.assignment.AssignmentAuthorityIT --no-daemon --no-parallel --max-workers=1 --no-problems-report 1> (Join-Path $evidencePath 'h75-stdout.log') 2> (Join-Path $evidencePath 'h75-stderr.log')
    $checkExit = $LASTEXITCODE
    $context.exit_code = $checkExit
} finally {
    Pop-Location
    $context.end = (Get-Date).ToUniversalTime().ToString('o')
    $context | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $evidencePath 'h75-context.json')
}
$xmlPath = Join-Path $treePath 'services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.assignment.AssignmentAuthorityIT.xml'
if (Test-Path $xmlPath) { Copy-Item -LiteralPath $xmlPath -Destination (Join-Path $evidencePath 'h75-AssignmentAuthorityIT.xml') }
$context | ConvertTo-Json -Depth 5
exit $checkExit

