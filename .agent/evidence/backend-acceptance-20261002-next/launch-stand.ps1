param([Parameter(Mandatory=$true)][ValidatePattern('^[a-f0-9]{64}$')][string]$ManifestSha256)
$ErrorActionPreference='Stop'
$root='C:/Users/maksd/IntelliJIDEA/rutcampustrack'
$runner="$root/.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/runner.ps1"
$manifestPath="$PSScriptRoot/homework-build-manifest-2ca6fc7e.json"
if((Get-FileHash -LiteralPath $manifestPath).Hash.ToLowerInvariant() -ne $ManifestSha256){throw 'Actual manifest pin changed'}
$runRoot=Join-Path (Split-Path $runner) 'runs'
$existingRuns=@(Get-ChildItem -LiteralPath $runRoot -Directory|ForEach-Object FullName)
& $runner -UnionRepo "$root/.agent/worktrees/v2-runtime-build-r3" -ExpectedUnionRevision 2ca6fc7e4301b6547a323b58155135d932080003 -BuildManifestPath $manifestPath -BuildManifestSha256 $ManifestSha256 -DevTlsCertificatePath "$env:LOCALAPPDATA/RutCampusTrack/certs/localhost.pem" -DevTlsPrivateKeyPath "$env:LOCALAPPDATA/RutCampusTrack/certs/localhost-key.pem" -DevTlsTrustCaPath "$env:LOCALAPPDATA/mkcert/rootCA.pem" -ManualAcceptance -IncludeNotifications -RequireUiScope -ManualAcceptanceTimeoutSeconds 1200 -TeacherExportBffImageId sha256:89862ecaac29e25493f0c5bed4d1d85751968fb60a13adca4e935e8a1fbd898a
$invocationSucceeded=$?
$lastNativeExit=$LASTEXITCODE
$newRuns=@(Get-ChildItem -LiteralPath $runRoot -Directory|Where-Object {$_.FullName -notin $existingRuns})
if($newRuns.Count -ne 1){throw 'Runner ended without one exact new owned run directory'}
$reportPath=Join-Path $newRuns[0].FullName 'report.json'
if(-not (Test-Path -LiteralPath $reportPath)){throw 'Runner ended without final report'}
$report=Get-Content -LiteralPath $reportPath -Raw|ConvertFrom-Json
if($report.unionRevision -ne '2ca6fc7e4301b6547a323b58155135d932080003'){throw 'Final report source is not owned frozen revision'}
Copy-Item -LiteralPath $reportPath -Destination "$PSScriptRoot/homework-stand-report.json"
$result=[ordered]@{runId=$report.runId;reportPath=$reportPath;status=$report.status;runtime=$report.runtime;cleanup=$report.cleanup.status;invocationSucceeded=$invocationSucceeded;lastNativeExit=$lastNativeExit}
[IO.File]::WriteAllText("$PSScriptRoot/homework-runner-exit.json",($result|ConvertTo-Json -Depth 4),[Text.UTF8Encoding]::new($false))
$result|ConvertTo-Json -Compress
if($report.status -eq 'PASS' -and $report.runtime -eq 'PASS' -and $report.cleanup.status -eq 'PASS'){exit 0}
exit 1
