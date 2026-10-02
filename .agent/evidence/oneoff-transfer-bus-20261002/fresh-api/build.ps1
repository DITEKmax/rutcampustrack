$ErrorActionPreference='Stop'
$runtimeRoot='C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-runtime-build-r2'
$expected='7e1190a0fddfd95ae272075ab1b0589f6afcb5e2'
if((& git -C $runtimeRoot rev-parse HEAD).Trim() -ne $expected -or (& git -C $runtimeRoot status --porcelain=v1 --untracked-files=all)){throw 'Frozen clean runtime holder changed'}
$env:JAVA_HOME='C:/Users/maksd/.jdks/ms-21.0.10';$env:PATH="$env:JAVA_HOME/bin;$env:PATH"
$buildArgs=@(':services:academic-service:academic-app:bootJar',':services:schedule-service:schedule-app:bootJar',':services:attendance-service:attendance-app:bootJar',':services:auth-service:auth-app:bootJar',':services:notification-service:notification-app:bootJar','--system-prop=org.gradle.java.compile-classpath-packaging=true','--no-problems-report','--no-daemon','--no-parallel','--max-workers=1','--console=plain')
$started=Get-Date -Format o
Push-Location -LiteralPath $runtimeRoot
try{& ./gradlew.bat @buildArgs 2>&1|Tee-Object -FilePath "$PSScriptRoot/build.log";$buildExit=$LASTEXITCODE}finally{Pop-Location}
$result=[ordered]@{revision=$expected;command='./gradlew.bat '+($buildArgs -join ' ');exitCode=$buildExit;startedAt=$started;finishedAt=(Get-Date -Format o)}
[IO.File]::WriteAllText("$PSScriptRoot/build-exit.json",($result|ConvertTo-Json),[Text.UTF8Encoding]::new($false));exit $buildExit
