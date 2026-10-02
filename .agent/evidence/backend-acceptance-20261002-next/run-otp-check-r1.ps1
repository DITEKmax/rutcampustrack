$ErrorActionPreference='Stop'
$PSNativeCommandUseErrorActionPreference=$false
$runtimeRoot='C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-runtime-build-r3'
$expected='2ca6fc7e4301b6547a323b58155135d932080003'
$testRelative='services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthOtpFlowIT.java'
$testHash='6fa4fe424412f3da04c3ddced16577a92a9d16deb9486801c6c7789acd15c7d1'
if((& git -C $runtimeRoot rev-parse HEAD).Trim() -ne $expected){throw 'Auth fixture baseline changed'}
if((Get-FileHash -LiteralPath (Join-Path $runtimeRoot $testRelative)).Hash.ToLowerInvariant() -ne $testHash){throw 'Scoped OTP test source changed'}
$changed=@(& git -C $runtimeRoot diff --name-only $expected)
if($changed.Count -ne 1 -or $changed[0] -ne $testRelative){throw 'Only assigned OTP test file may differ'}
if(Test-Path -LiteralPath "$PSScriptRoot/otp-check-exit.json"){throw 'Prior bounded run exists; root must assign fresh evidence for any justified retry'}
$started=Get-Date -Format o
$gradleArgs=@(':services:auth-service:auth-app:integrationTest','--tests','ru.rutcampustrack.auth.integration.AuthOtpFlowIT.passwordResetVerifyRejectsNaturallyExpiredAndConsumedChallengesWithoutMutation','--no-daemon','--no-parallel','--max-workers=1','--no-problems-report','--console=plain','--system-prop=org.gradle.java.compile-classpath-packaging=true')
Push-Location -LiteralPath $runtimeRoot
try{
 & .\gradlew.bat @gradleArgs 2>&1|Tee-Object -FilePath "$PSScriptRoot/otp-check.log"
 $checkExit=$LASTEXITCODE
}finally{Pop-Location}
$xmlRelative='services/auth-service/auth-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.auth.integration.AuthOtpFlowIT.xml'
$xmlPath=Join-Path $runtimeRoot $xmlRelative
if(Test-Path -LiteralPath $xmlPath){Copy-Item -LiteralPath $xmlPath -Destination "$PSScriptRoot/otp-AuthOtpFlowIT.xml"}
$result=[ordered]@{revision=$expected;testFile=$testRelative;testSourceSha256=$testHash;command='./gradlew.bat '+($gradleArgs -join ' ');exitCode=$checkExit;startedAt=$started;finishedAt=Get-Date -Format o;environment='Existing AuthOtpFlowIT real Spring random-port HTTP; fresh nonreuse PG/Redis/Rabbit + owned Testcontainers cleanup; OTP TTL120 unchanged';criteria=@('public challenge natural Redis expiry410/OTP_EXPIRED without ticket/session/credential mutation','public consumed challenge reverify410/OTP_EXPIRED without another ticket/session/credential mutation');log='otp-check.log';xml='otp-AuthOtpFlowIT.xml'}
[IO.File]::WriteAllText("$PSScriptRoot/otp-check-exit.json",($result|ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
$result|ConvertTo-Json -Depth 5
exit $checkExit
