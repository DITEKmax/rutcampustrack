$ErrorActionPreference = 'Stop'
$taskQrRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Set-Location -LiteralPath $taskQrRoot
$env:JAVA_HOME = 'C:/Users/maksd/.jdks/ms-21.0.10'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$env:TESTCONTAINERS_REUSE_ENABLE = 'false'
$taskQrRevision = (& git rev-parse HEAD).Trim()
$taskQrStart = [ordered]@{
    revision = $taskQrRevision; startedAt = [DateTime]::UtcNow.ToString('o')
    javaHome = $env:JAVA_HOME; testcontainersReuse = $false
    scope = 'Auth/Gateway compile first, QR HTTP+PG/Redis, one Gateway POST allowlist matrix'
    heavyLease = 'root authorized exclusive; source review PASS 9ffad58e'
}
$taskQrStart | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'gradle-r1-start.json') -Encoding utf8
# Fail fast: all requested compiles precede the expensive PG/Redis task.
& ./gradlew.bat `
    :services:auth-service:auth-app:compileJava `
    :services:auth-service:auth-app:compileTestJava `
    :services:api-gateway:compileJava `
    :services:api-gateway:compileTestJava `
    :services:auth-service:auth-app:integrationTest --tests '*QrLoginFlowIT' `
    :services:api-gateway:test --tests '*JwtAuthenticationFilterTest.qrGuestAdmissionIsLimitedToThreeExactPostRoutes' `
    --max-workers=1 --no-daemon --no-parallel --no-problems-report --console=plain `
    --system-prop=org.gradle.java.compile-classpath-packaging=true '-Dorg.gradle.jvmargs=-Xmx768m' `
    *> (Join-Path $PSScriptRoot 'gradle-r1.log')
$taskQrExit = $LASTEXITCODE
[ordered]@{ revision = $taskQrRevision; exitCode = $taskQrExit; terminalAt = [DateTime]::UtcNow.ToString('o') } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'gradle-r1-exit.json') -Encoding utf8
Get-Content -LiteralPath (Join-Path $PSScriptRoot 'gradle-r1.log') -Tail 45
exit $taskQrExit
