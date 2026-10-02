$ErrorActionPreference='Stop'
$root='C:/Users/maksd/IntelliJIDEA/rutcampustrack'
$runtimeRoot="$root/.agent/worktrees/v2-runtime-build-r2"
$revision='7e1190a0fddfd95ae272075ab1b0589f6afcb5e2';$baselineRevision='24e9233526f652efdd17cf59d889d1d2d90aad4a'
$baselinePath=Join-Path (Split-Path $PSScriptRoot) 'build-manifest.json'
$baselinePin='82699fc2441ddcb097c0fbd0317831c0252a72f5f94810a9fef665304c010e64'
if((Get-FileHash -LiteralPath $baselinePath).Hash.ToLowerInvariant() -ne $baselinePin){throw 'Accepted reuse manifest changed'}
if((& git -C $runtimeRoot rev-parse HEAD).Trim() -ne $revision -or (& git -C $runtimeRoot status --porcelain=v1 --untracked-files=all)){throw 'Runtime holder source changed'}
$manifest=Get-Content -LiteralPath $baselinePath -Raw|ConvertFrom-Json -AsHashtable
$closure=@('services/mobile-bff','services/api-gateway','services/document-renderer-service','services/auth-service/auth-api-contract','services/shared','frontends/pwa-vue','proto','build.gradle.kts','settings.gradle.kts','gradle',':(exclude)services/shared/shared-outbox')
$diff=@(& git -C $runtimeRoot diff --name-only $baselineRevision $revision -- @closure)
if($LASTEXITCODE -ne 0 -or $diff.Count){throw 'Reused runtime dependency closure changed'}
$build=Get-Content -LiteralPath "$PSScriptRoot/build-exit.json" -Raw|ConvertFrom-Json
if($build.revision -ne $revision -or $build.exitCode -ne 0){throw 'Successful pinned build missing'}
$changed=@('academic','schedule','attendance','auth','notification');$inventory=@()
foreach($entry in $manifest.artifacts.jars){
 $file=Get-Item -LiteralPath (Join-Path $runtimeRoot $entry.relativePath);$sha=(Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()
 if($entry.name -notin $changed -and ($sha -ne $entry.sha256 -or $file.Length -ne $entry.bytes)){throw 'Accepted reuse JAR bytes changed'}
 $entry.sha256=$sha;$entry.bytes=[long]$file.Length
 $inventory+=@([ordered]@{name=$entry.name;sha256=$sha;bytes=$file.Length;mode=$(if($entry.name -in $changed){'rebuilt currentsource+sharedoutbox'}else{'exact reused bytes+runtimeclosure'})})
}
$distRoot=Join-Path $runtimeRoot $manifest.artifacts.pwaDist.relativeRoot
foreach($entry in $manifest.artifacts.pwaDist.files){$file=Get-Item -LiteralPath (Join-Path $distRoot $entry.relativePath);if($file.Length -ne $entry.bytes -or (Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant() -ne $entry.sha256){throw 'Accepted PWA bytes changed'}}
$actual=@(Get-ChildItem -LiteralPath $distRoot -Recurse -File|ForEach-Object {($_.FullName.Substring($distRoot.Length+1)).Replace('\','/')})
if(@(Compare-Object @($manifest.artifacts.pwaDist.files.relativePath) $actual).Count){throw 'PWA file set changed'}
$manifest.source.revision=$revision;$manifest.source.absoluteRepo=$runtimeRoot;$manifest.source.cleanBeforeBuild=$true;$manifest.source.cleanAfterBuild=$true
$manifest.producer.commands=@([ordered]@{command=$build.command;exitCode=0;startedAt=$build.startedAt;finishedAt=$build.finishedAt})
$output="$PSScriptRoot/build-manifest.json"
[IO.File]::WriteAllText($output,($manifest|ConvertTo-Json -Depth 20 -Compress),[Text.UTF8Encoding]::new($false))
& "$PSScriptRoot/canonicalize-manifest.ps1"|Out-Null
$result=[ordered]@{revision=$revision;reuseBaseline=$baselineRevision;baselineManifestSha256=$baselinePin;runtimeDependencyClosure=$closure;closureDiff=$diff;sharedOutboxConsumers=$changed;jars=$inventory;pwaFilesVerified=$actual.Count;manifestSha256=(Get-FileHash -LiteralPath $output).Hash.ToLowerInvariant()}
[IO.File]::WriteAllText("$PSScriptRoot/artifact-verification.json",($result|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false));$result|ConvertTo-Json -Depth 8
