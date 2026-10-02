$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$worktree = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/map-usage-delivery-20260922'
$evidenceDir = Join-Path $worktree '.agent/evidence/auth-durable-delivery-20261002'
$runStamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$logPath = Join-Path $evidenceDir ("auth-checks-$runStamp.log")
$gradleArgs = @(
    ':services:auth-service:auth-app:compileTestJava',
    ':services:auth-service:auth-app:test',
    '--tests', '*.AuthApiContractTest',
    '--tests', '*.PasswordChangedContractTest',
    ':services:auth-service:auth-app:integrationTest',
    '--tests', '*.JdbcSessionAuthorityIT.passwordChangeRechecksHashUpdatesFlagsRevokesAllAndAuditsAtomically',
    '--tests', '*.JdbcSessionAuthorityIT.notificationIntentRollsBackWithPasswordSessionsAndOneUseResetTicket',
    '--tests', '*.AuthOtpFlowIT.passwordResetIsPurposeBoundAtomicAndRevokesEverySession',
    '--tests', '*.AuthOtpFlowIT.passwordChangeIntentSurvivesBrokerFailureAndPublisherRestart',
    '--tests', '*.InternalSessionAdmissionIT.deletionConfirmationRequiresLiveAdminAndCurrentPasswordWithoutCreatingSessions',
    '--tests', '*.IntegrationTestNamingConventionIT',
    '--no-daemon', '--no-parallel', '--max-workers=1', '--no-problems-report', '--console=plain',
    '--system-prop=org.gradle.java.compile-classpath-packaging=true'
)
Push-Location -LiteralPath $worktree
try {
    $revision = (& git rev-parse HEAD).Trim()
    Write-Output "Auth check revision=$revision log=$logPath"
    & .\gradlew.bat @gradleArgs 2>&1 | Tee-Object -FilePath $logPath
    $runExit = $LASTEXITCODE
    @{
        revision = $revision
        command = '.\gradlew.bat ' + ($gradleArgs -join ' ')
        exit_code = $runExit
        environment = 'Java 21; assigned worktree; fresh non-reusable PostgreSQL/Redis/Rabbit Testcontainers; Academic V1..V42 migrations; heavy lease from root'
        log_path = $logPath
        criteria = @('credential+notification rollback', 'password/reset committed intent', 'broker retry with stable event_id', 'pending retention', 'live ADMIN current password confirmation FLOOR/BUILDING', 'auth contract and naming gates')
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceDir "checks-$runStamp.json") -Encoding utf8
    Write-Output "Auth check exit=$runExit log=$logPath"
} finally {
    Pop-Location
}
exit $runExit
