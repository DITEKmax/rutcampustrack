$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$archiveWorktree = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/map-usage-delivery-20260922'
$archiveEvidence = Join-Path $archiveWorktree '.agent/evidence/user-archive-lifecycle-20261002'
$archiveStamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$archiveLog = Join-Path $archiveEvidence ("academic-r2-$archiveStamp.log")
$archivePreviousReuse = [Environment]::GetEnvironmentVariable('TESTCONTAINERS_REUSE_ENABLE', 'Process')
[Environment]::SetEnvironmentVariable('TESTCONTAINERS_REUSE_ENABLE', 'false', 'Process')
$archiveArgs = @(
    ':services:academic-service:academic-app:compileTestJava',
    ':services:academic-service:academic-app:integrationTest',
    '--tests', 'ru.rutcampustrack.academic.integration.UserArchiveLifecycleIT',
    '--continue', '--no-daemon', '--no-parallel', '--max-workers=1', '--no-problems-report', '--console=plain',
    '--system-prop=org.gradle.java.compile-classpath-packaging=true'
)
Push-Location -LiteralPath $archiveWorktree
try {
    $archiveRevision = (& git rev-parse HEAD).Trim()
    Write-Output "Academic R2 revision=$archiveRevision log=$archiveLog"
    & .\gradlew.bat @archiveArgs 2>&1 | Tee-Object -FilePath $archiveLog
    $archiveExit = $LASTEXITCODE
    @{
        revision = $archiveRevision
        command = '.\gradlew.bat ' + ($archiveArgs -join ' ')
        exit_code = $archiveExit
        log_path = $archiveLog
        environment = 'Java 21; isolated PostgreSQL Testcontainers; TESTCONTAINERS_REUSE_ENABLE=false; no production migration'
        selected_cases = 4
        criteria = @('archive rollback/replay and no-rights restore', 'stale local impact and protected legacy routes',
            'group serialization and FK KEY SHARE compatibility')
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $archiveEvidence "academic-r2-$archiveStamp.json") -Encoding utf8
    Write-Output "Academic R2 exit=$archiveExit log=$archiveLog"
} finally {
    [Environment]::SetEnvironmentVariable('TESTCONTAINERS_REUSE_ENABLE', $archivePreviousReuse, 'Process')
    Pop-Location
}
exit $archiveExit
