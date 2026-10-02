param([Parameter(Mandatory=$true)][ValidatePattern('^[a-f0-9]{64}$')][string]$ManifestSha256,[ValidatePattern('^r[1-9][0-9]*$')][string]$Attempt='r1')
$ErrorActionPreference='Stop'
$root='C:/Users/maksd/IntelliJIDEA/rutcampustrack';$expected='7e1190a0fddfd95ae272075ab1b0589f6afcb5e2'
$runner="$root/.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/runner.ps1"
$manifest="$PSScriptRoot/build-manifest.json"
if((Get-FileHash -LiteralPath $manifest).Hash.ToLowerInvariant() -ne $ManifestSha256){throw 'Pinned manifest changed'}
$runRoot=Join-Path (Split-Path $runner) 'runs';$before=@(Get-ChildItem -LiteralPath $runRoot -Directory|ForEach-Object FullName)
& $runner -UnionRepo "$root/.agent/worktrees/v2-runtime-build-r2" -ExpectedUnionRevision $expected -BuildManifestPath $manifest -BuildManifestSha256 $ManifestSha256 -DevTlsCertificatePath "$env:LOCALAPPDATA/RutCampusTrack/certs/localhost.pem" -DevTlsPrivateKeyPath "$env:LOCALAPPDATA/RutCampusTrack/certs/localhost-key.pem" -DevTlsTrustCaPath "$env:LOCALAPPDATA/mkcert/rootCA.pem" -ManualAcceptance -IncludeNotifications -RequireUiScope -ManualAcceptanceTimeoutSeconds 1200 -TeacherExportBffImageId sha256:89862ecaac29e25493f0c5bed4d1d85751968fb60a13adca4e935e8a1fbd898a
$native=$LASTEXITCODE;$ok=$?
$new=@(Get-ChildItem -LiteralPath $runRoot -Directory|Where-Object{$_.FullName -notin $before});if($new.Count -ne 1){throw 'One exact owned run required'}
$reportPath=Join-Path $new[0].FullName 'report.json';$report=Get-Content -LiteralPath $reportPath -Raw|ConvertFrom-Json
if($report.unionRevision -ne $expected){throw 'Final source changed'}
Copy-Item -LiteralPath $reportPath -Destination "$PSScriptRoot/stand-report-$Attempt.json"
$result=[ordered]@{runId=$report.runId;reportPath=$reportPath;status=$report.status;runtime=$report.runtime;cleanup=$report.cleanup.status;invocationSucceeded=$ok;lastNativeExit=$native}
[IO.File]::WriteAllText("$PSScriptRoot/runner-exit-$Attempt.json",($result|ConvertTo-Json -Depth 4),[Text.UTF8Encoding]::new($false));$result|ConvertTo-Json -Compress
if($report.status -eq 'PASS' -and $report.runtime -eq 'PASS' -and $report.cleanup.status -eq 'PASS'){exit 0}else{exit 1}
