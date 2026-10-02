param(
 [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9_-]+$')][string]$RunId,
 [Parameter(Mandatory=$true)][ValidatePattern('^[a-f0-9]{40}$')][string]$ExpectedUnionRevision,
 [Parameter(Mandatory=$true)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedManifestSha256
)
$ErrorActionPreference='Stop'
$root='C:/Users/maksd/IntelliJIDEA/rutcampustrack'
$runDir="$root/.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/runs/$RunId"
$ready=Get-Content -LiteralPath "$runDir/manual-acceptance-ready.json" -Raw|ConvertFrom-Json
if($ready.runId -ne $RunId -or $ready.sourceRevision -ne $ExpectedUnionRevision -or $ready.buildManifestSha256 -ne $ExpectedManifestSha256 -or $ready.edgeOrigin -ne 'https://127.0.0.1:18514'){throw 'Ready source/manifest/origin mismatch'}
if($ExpectedUnionRevision -eq '2948e60eb0097741b9feb95b66d086c9a84a9190'){throw 'Old runtime lacks DATE lifecycle; new root freeze and manifest required'}
$outcomes=[ordered]@{}
$result=[ordered]@{runId=$RunId;status='FAIL';startedAt=(Get-Date -Format o);scopes=$outcomes}
$ids=@{}
function Assert-OwnedAvailable([string]$name){
 $parts=@((& docker inspect --format '{{.Id}}|{{index .Config.Labels "rct.runtime-owner"}}|{{index .Config.Labels "rct.runtime-run"}}|{{.State.Running}}|{{.State.Paused}}|{{.State.OOMKilled}}' "rct-requests-$RunId-$name").Split('|'))
 if($LASTEXITCODE -ne 0 -or $parts.Count -ne 6 -or $parts[0] -notmatch '^[a-f0-9]{64}$' -or $parts[1] -ne 'student-requests-gate' -or $parts[2] -ne $RunId -or $parts[3] -ne 'true' -or $parts[4] -ne 'false' -or $parts[5] -ne 'false'){throw "Own resource unavailable $name"}
 if($ids.ContainsKey($name) -and $ids[$name] -ne $parts[0]){throw "Exact own resource changed $name"}
 $ids[$name]=$parts[0]
}
try{
 node --check "$PSScriptRoot/combined-b-a-probe.mjs"
 if($LASTEXITCODE -ne 0){throw 'Focused probe syntax failed'}
 foreach($name in @('pg-academic','pg-schedule','mongo','rabbit','attendance','bff')){Assert-OwnedAvailable $name}
 $common=@('--origin',$ready.edgeOrigin,'--ca',"$env:LOCALAPPDATA/mkcert/rootCA.pem",'--revision',$ready.sourceRevision,'--run-id',$RunId)
 foreach($name in @('pg-academic','pg-schedule','mongo','rabbit','attendance','bff')){$common+=@("--$name-id",$ids[$name])}
 foreach($label in @('date','a')){
  if(Test-Path -LiteralPath "$PSScriptRoot/homework-$label-fixture.json"){throw 'Prior new-batch evidence exists; root must assign fresh paths instead of overwriting'}
  node "$root/.agent/orchestration-v2/evidence/2026-09-27-delivery/transfer-assistant-ui-fixture.mjs" --origin $ready.edgeOrigin --ca "$env:LOCALAPPDATA/mkcert/rootCA.pem" --out "$PSScriptRoot/homework-$label-fixture.json" --scenario geo 2>&1|Tee-Object -FilePath "$PSScriptRoot/homework-$label-fixture.log"
  $fixtureExit=$LASTEXITCODE
  $outcomes["fixture-$label"]=[ordered]@{exitCode=$fixtureExit}
 }
 $fixtures=@{}
 foreach($label in @('date','a')){if($outcomes["fixture-$label"].exitCode -eq 0){$fixtures[$label]=Get-Content -LiteralPath "$PSScriptRoot/homework-$label-fixture.json" -Raw|ConvertFrom-Json}}
 $all=@($fixtures.Values)
 if(@($all.semesterId|Select-Object -Unique).Count -gt 1 -or @($all.groupId|Select-Object -Unique).Count -ne $all.Count -or @(@($all.headmanStudentId)+@($all.studentId)|Select-Object -Unique).Count -ne 2*$all.Count){throw 'Independent fixtures require original semester and disjoint groups/users'}
 $result.fixturePrerequisite=[ordered]@{originalSemester=$all[0].semesterId;groupIds=@($all.groupId);disjointUsers=$true}
 foreach($entry in @(@{scope='DATE-homework';label='date';fixture=$fixtures['date']},@{scope='A-only';label='a';fixture=$fixtures['a']})){
  if(-not $entry.fixture){$outcomes[$entry.scope]=[ordered]@{status='NOT_RUN';reason='Own fixture setup failed'};continue}
  foreach($name in @('pg-academic','pg-schedule','mongo','rabbit','attendance','bff')){Assert-OwnedAvailable $name}
  node "$PSScriptRoot/combined-b-a-probe.mjs" @common --scope $entry.scope --fixture "$PSScriptRoot/homework-$($entry.label)-fixture.json" --out "$PSScriptRoot/homework-$($entry.label)-api.json" 2>&1|Tee-Object -FilePath "$PSScriptRoot/homework-$($entry.label)-probe.log"
  $scopeExit=$LASTEXITCODE
  $evidence=Get-Content -LiteralPath "$PSScriptRoot/homework-$($entry.label)-api.json" -Raw|ConvertFrom-Json
  $outcomes[$entry.scope]=[ordered]@{exitCode=$scopeExit;status=$evidence.status;steps=@($evidence.steps).Count;checks=@($evidence.checks).Count;failure=$evidence.failure}
  [IO.File]::WriteAllText("$PSScriptRoot/homework-probe-progress.json",($result|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
  if(($null -ne $evidence.unpauseFailures -and @($evidence.unpauseFailures).Count -gt 0) -or ($null -ne $evidence.remainingOwnPauseIntents -and @($evidence.remainingOwnPauseIntents).Count -gt 0)){throw 'Genuine safety termination: own pause not cleared'}
  # DATE failure does not gate independent A inventory; A runs last.
 }
 if(@('DATE-homework','A-only'|Where-Object{$outcomes[$_].status -ne 'PASS'}).Count -eq 0){$result.status='PASS'}
 if($outcomes.Contains('A-only') -and $outcomes['A-only'].status -notin @('PASS','NOT_RUN')){
  foreach($name in @('pg-academic','pg-schedule','mongo','rabbit','attendance','bff')){Assert-OwnedAvailable $name}
  $until=(Get-Date).AddMinutes(5);$result.diagnosisHoldUntil=$until.ToString('o');
  [IO.File]::WriteAllText("$PSScriptRoot/homework-diagnosis-hold.json",($result|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false));
  $result|ConvertTo-Json -Compress -Depth 8;
  while((Get-Date) -lt $until -and -not(Test-Path -LiteralPath "$PSScriptRoot/homework-diagnosis-release")){Start-Sleep -Seconds 1}
 }
}catch{
 $result.failure=$_.Exception.Message
}finally{
 $result.finishedAt=Get-Date -Format o
 [IO.File]::WriteAllText("$PSScriptRoot/homework-probe-exit.json",($result|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
 $ownerText="Batch DATE-homework/A-only $($result.status)`nRun $RunId; frozen $ExpectedUnionRevision manifest $ExpectedManifestSha256`nEvidence $PSScriptRoot/homework-a-api.json; homework-date-api.json; homework-probe-exit.json. Prior failures and passed B/geo immutable. No auth values persisted.`n"
 [IO.File]::WriteAllText("$runDir/manual-acceptance-owner-evidence.md",$ownerText,[Text.UTF8Encoding]::new($false))
 [IO.File]::WriteAllText("$runDir/manual-acceptance.complete",$result.status,[Text.UTF8Encoding]::new($false))
 $result|ConvertTo-Json -Compress -Depth 8
}
if($result.status -eq 'PASS'){exit 0};exit 1
