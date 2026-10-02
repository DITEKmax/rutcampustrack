param([Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9_-]+$')][string]$RunId,[Parameter(Mandatory=$true)][ValidatePattern('^[a-f0-9]{64}$')][string]$ManifestSha256)
$ErrorActionPreference='Stop'
$root='C:/Users/maksd/IntelliJIDEA/rutcampustrack';$expected='24e9233526f652efdd17cf59d889d1d2d90aad4a'
$runDir="$root/.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/runs/$RunId"
$ready=Get-Content -LiteralPath "$runDir/manual-acceptance-ready.json" -Raw|ConvertFrom-Json
if($ready.runId -ne $RunId -or $ready.sourceRevision -ne $expected -or $ready.buildManifestSha256 -ne $ManifestSha256 -or $ready.edgeOrigin -ne 'https://127.0.0.1:18514'){throw 'Ready source/run/manifest/origin mismatch'}
$result=[ordered]@{runId=$RunId;revision=$expected;status='FAIL';startedAt=(Get-Date -Format o)}
try{
 foreach($file in @('fixture.json','api.json','probe-exit.json')){if(Test-Path -LiteralPath "$PSScriptRoot/$file"){throw 'Evidence exists; root must assign new run folder instead of overwrite'}}
 node --check "$PSScriptRoot/probe.mjs";if($LASTEXITCODE -ne 0){throw 'Probe syntax failure'}
 $probeArgs=@('--origin',$ready.edgeOrigin,'--ca',"$env:LOCALAPPDATA/mkcert/rootCA.pem",'--revision',$expected,'--run-id',$RunId,'--fixture',"$PSScriptRoot/fixture.json",'--out',"$PSScriptRoot/api.json")
 $owned=@()
 foreach($name in @('pg-academic','pg-schedule','mongo','rabbit')){
  $parts=@((& docker inspect --format '{{.Id}}|{{index .Config.Labels "rct.runtime-owner"}}|{{index .Config.Labels "rct.runtime-run"}}|{{.State.Running}}|{{.State.Paused}}|{{.State.OOMKilled}}' "rct-requests-$RunId-$name").Split('|'))
  if($LASTEXITCODE -ne 0 -or $parts.Count -ne 6 -or $parts[0] -notmatch '^[a-f0-9]{64}$' -or $parts[1] -ne 'student-requests-gate' -or $parts[2] -ne $RunId -or $parts[3] -ne 'true' -or $parts[4] -ne 'false' -or $parts[5] -ne 'false'){throw "Owned resource mismatch $name"}
  $probeArgs+=@("--$name-id",$parts[0]);$owned+=@([ordered]@{name=$name;id=$parts[0]})
 }
 [IO.File]::WriteAllText("$PSScriptRoot/owned-resources.json",($owned|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
 node "$root/.agent/orchestration-v2/evidence/2026-09-27-delivery/transfer-assistant-ui-fixture.mjs" --origin $ready.edgeOrigin --ca "$env:LOCALAPPDATA/mkcert/rootCA.pem" --out "$PSScriptRoot/fixture.json" --scenario geo 2>&1|Tee-Object -FilePath "$PSScriptRoot/fixture.log"
 if($LASTEXITCODE -ne 0){throw 'Existing public fixture setup failed'}
 node "$PSScriptRoot/probe.mjs" @probeArgs 2>&1|Tee-Object -FilePath "$PSScriptRoot/probe.log"
 $result.probeExit=$LASTEXITCODE;$api=Get-Content -LiteralPath "$PSScriptRoot/api.json" -Raw|ConvertFrom-Json
 $result.status=$api.status;$result.steps=@($api.steps).Count;$result.checks=@($api.checks).Count;$result.failure=$api.failure
}catch{$result.failure=$_.Exception.Message}finally{
 $result.finishedAt=Get-Date -Format o
 [IO.File]::WriteAllText("$PSScriptRoot/probe-exit.json",($result|ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
 [IO.File]::WriteAllText("$runDir/manual-acceptance-owner-evidence.md","ONE_OFF actual Rabbit $($result.status); run$RunId; source$expected; evidence$PSScriptRoot/api.json. No fake ACK/participant/mark writes; component PASS not repeated.",[Text.UTF8Encoding]::new($false))
 [IO.File]::WriteAllText("$runDir/manual-acceptance.complete",$result.status,[Text.UTF8Encoding]::new($false))
 $result|ConvertTo-Json -Compress
}
if($result.status -eq 'PASS'){exit 0}else{exit 1}
