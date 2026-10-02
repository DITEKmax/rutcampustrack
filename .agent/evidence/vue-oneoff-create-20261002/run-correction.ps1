$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
Set-Location $taskRoot
$env:JAVA_HOME = 'C:/Users/maksd/.jdks/ms-21.0.10'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$env:TESTCONTAINERS_REUSE_ENABLE = 'false'
$arguments = @(':services:schedule-service:schedule-app:compileJava', ':services:schedule-service:schedule-app:compileTestJava',
  ':services:schedule-service:schedule-app:integrationTest', '--tests',
  'ru.rutcampustrack.schedule.oneoff.OneOffLessonControllerIT.create_rejectsForeignAuthorityAndHeadmanScope_atomically',
  '--system-prop=org.gradle.java.compile-classpath-packaging=true', '--no-problems-report', '--no-daemon', '--no-parallel', '--max-workers=1', '--console=plain')
$started = (Get-Date).ToString('o')
$revision = (git rev-parse HEAD).Trim()
& ./gradlew.bat @arguments *>&1 | Tee-Object -FilePath (Join-Path $PSScriptRoot 'correction-gradle.log')
$result = $LASTEXITCODE
@{ revision=$revision; command=('./gradlew.bat ' + ($arguments -join ' ')); startedAt=$started; finishedAt=(Get-Date).ToString('o'); exitCode=$result } |
  ConvertTo-Json -Depth 5 | Set-Content (Join-Path $PSScriptRoot 'correction-gradle-exit.json') -Encoding utf8
exit $result
