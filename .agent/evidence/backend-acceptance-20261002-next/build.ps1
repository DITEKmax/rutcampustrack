$ErrorActionPreference='Stop'
$runtimeRoot='C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-runtime-build-r3'
$expected='2ca6fc7e4301b6547a323b58155135d932080003'
if((& git -C $runtimeRoot rev-parse HEAD).Trim() -ne $expected){throw 'Frozen runtime changed'}
if((& git -C $runtimeRoot status --porcelain --untracked-files=no).Count -gt 0){throw 'Runtime source dirty'}
$started=Get-Date -Format o
$buildArgs=@(':services:academic-service:academic-app:bootJar',':services:schedule-service:schedule-app:bootJar',':services:attendance-service:attendance-app:bootJar',':services:mobile-bff:mobile-bff-app:bootJar','--system-prop=org.gradle.java.compile-classpath-packaging=true','--no-problems-report','--no-daemon','--no-parallel','--max-workers=1')
Push-Location -LiteralPath $runtimeRoot
try{
 & .\gradlew.bat @buildArgs 2>&1 | Tee-Object -FilePath "$PSScriptRoot/homework-build.log"
 $buildExit=$LASTEXITCODE
}finally{Pop-Location}
$result=[ordered]@{revision=$expected;command='./gradlew.bat '+($buildArgs -join ' ');exitCode=$buildExit;startedAt=$started;finishedAt=(Get-Date -Format o);daemonMode='single-use --no-daemon; do not stop foreign daemons'}
[IO.File]::WriteAllText("$PSScriptRoot/homework-build-exit.json",($result|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
exit $buildExit
