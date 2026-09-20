param(
    [Parameter(Mandatory=$true)][ValidateSet('H76','H77')][string]$Stage,
    [Parameter(Mandatory=$true)][string]$FreezeManifestPath,
    [Parameter(Mandatory=$true)][string]$ExpectedFreezeManifestHash
)
$ErrorActionPreference = 'Stop'
$rootPath = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack'
$treePath = Join-Path $rootPath '.agent/worktrees/v2-integration-l5a'
$evidenceRoot = Join-Path $rootPath '.agent/orchestration-v2/evidence'
$stagePrefix = $Stage.ToLowerInvariant()
if ((Get-FileHash $FreezeManifestPath).Hash -ne $ExpectedFreezeManifestHash) { throw 'Freeze manifest mismatch' }
$freeze = Get-Content $FreezeManifestPath -Raw | ConvertFrom-Json
foreach ($sourceRecord in $freeze.files) {
    if ((Get-FileHash (Join-Path $treePath $sourceRecord.path)).Hash -ne $sourceRecord.sha256) { throw "Source changed before check: $($sourceRecord.path)" }
}
$contextPath = Join-Path $evidenceRoot "$stagePrefix-context.json"
if (Test-Path $contextPath) { throw 'Stage already recorded; refuse overwrite' }
$academicTaskPrefix = ':services:academic-service:academic-app:'
$unitClasses = @(
 'ru.rutcampustrack.academic.assignment.AssignmentAuthorityContractTest',
 'ru.rutcampustrack.academic.assignment.AssignmentClosureContractTest',
 'ru.rutcampustrack.academic.subject.SubjectAssignmentAuthorityContractTest',
 'ru.rutcampustrack.academic.config.SubjectTypeUserTypeTest',
 'ru.rutcampustrack.academic.grpc.AcademicAssignmentGrpcContractTest',
 'ru.rutcampustrack.academic.semester.SemesterAssignmentLockContractTest',
 'ru.rutcampustrack.academic.grpc.CampusMapGrpcReadTest',
 'ru.rutcampustrack.academic.grpc.StudentProjectionGrpcServiceTest',
 'ru.rutcampustrack.academic.grpc.StudentHomeworkGrpcIdentityInterceptorTest'
)
$integrationClasses = @(
 'ru.rutcampustrack.academic.assignment.AssignmentAuthorityIT',
 'ru.rutcampustrack.academic.grpc.AcademicAssignmentGrpcIT',
 'ru.rutcampustrack.academic.semester.SemesterAssignmentAuthorityIT',
 'ru.rutcampustrack.academic.subject.SubjectAssignmentAuthorityIT',
 'ru.rutcampustrack.academic.subject.SubjectServiceIT',
 'ru.rutcampustrack.academic.integration.AcademicGrpcIT'
)
$taskName = if ($Stage -eq 'H76') { 'test' } else { 'integrationTest' }
$selectedClasses = if ($Stage -eq 'H76') { $unitClasses } else { $integrationClasses }
$gradleArguments = @($academicTaskPrefix + $taskName)
foreach ($className in $selectedClasses) { $gradleArguments += @('--tests',$className) }
$reportClass = 'ru.rutcampustrack.attendance.report.ReportServiceTest'
if ($Stage -eq 'H76') { $gradleArguments += @(':services:attendance-service:attendance-app:test','--tests',$reportClass) }
$gradleArguments += @('--no-daemon','--no-parallel','--max-workers=1','--no-problems-report')
$startTime = (Get-Date).ToUniversalTime()
$context = [ordered]@{stage=$Stage;start=$startTime.ToString('o');identity=(& whoami);head=(& git -C $treePath rev-parse HEAD);freezeManifestPath=$FreezeManifestPath;freezeManifestSha256=$ExpectedFreezeManifestHash;command=@('.\gradlew.bat')+$gradleArguments;testcontainersReuse='false';exitCode=$null}
$env:TESTCONTAINERS_REUSE_ENABLE = 'false'
Push-Location $treePath
try {
    & .\gradlew.bat @gradleArguments 1> (Join-Path $evidenceRoot "$stagePrefix-stdout.log") 2> (Join-Path $evidenceRoot "$stagePrefix-stderr.log")
    $checkExit = $LASTEXITCODE
    $context.exitCode = $checkExit
} finally {
    Pop-Location
    $context.end = (Get-Date).ToUniversalTime().ToString('o')
    $context.postHashMismatches = @(foreach ($sourceRecord in $freeze.files) {
        if ((Get-FileHash (Join-Path $treePath $sourceRecord.path)).Hash -ne $sourceRecord.sha256) { $sourceRecord.path }
    })
    $context | ConvertTo-Json -Depth 6 | Set-Content $contextPath
}
$xmlDestination = Join-Path $evidenceRoot "$stagePrefix-xml"
New-Item -ItemType Directory -Path $xmlDestination -ErrorAction Stop | Out-Null
$xmlRecords = @(foreach ($className in $selectedClasses) {
    $xmlPath = Join-Path $treePath "services/academic-service/academic-app/build/test-results/$taskName/TEST-$className.xml"
    $fresh = (Test-Path $xmlPath) -and (Get-Item $xmlPath).LastWriteTimeUtc -ge $startTime
    if ($fresh) { Copy-Item -LiteralPath $xmlPath -Destination $xmlDestination }
    [pscustomobject]@{className=$className;freshXml=$fresh}
})
if ($Stage -eq 'H76') {
    $xmlPath = Join-Path $treePath "services/attendance-service/attendance-app/build/test-results/test/TEST-$reportClass.xml"
    $fresh = (Test-Path $xmlPath) -and (Get-Item $xmlPath).LastWriteTimeUtc -ge $startTime
    if ($fresh) { Copy-Item -LiteralPath $xmlPath -Destination $xmlDestination }
    $xmlRecords += [pscustomobject]@{className=$reportClass;freshXml=$fresh}
}
$xmlRecords | ConvertTo-Json | Set-Content (Join-Path $evidenceRoot "$stagePrefix-xml-presence.json")
$context | ConvertTo-Json -Depth 6
exit $checkExit
