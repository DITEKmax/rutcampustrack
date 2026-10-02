$ErrorActionPreference='Stop'
$root='C:/Users/maksd/IntelliJIDEA/rutcampustrack'
$runtimeRoot="$root/.agent/worktrees/v2-runtime-build-r2"
$reuseRoot="$root/.agent/worktrees/v2-runtime-build-r3"
$baselinePath="$root/.agent/evidence/backend-acceptance-20261002-next/homework-build-manifest-2ca6fc7e.json"
$baselinePin='32125fa72e70ddccff2fc5cf7fcd984016eab9ebf28bfc5211c07b23a9ef1bc8'
$revision='24e9233526f652efdd17cf59d889d1d2d90aad4a'
if((Get-FileHash -LiteralPath $baselinePath).Hash.ToLowerInvariant() -ne $baselinePin){throw 'Accepted reuse manifest changed'}
if((& git -C $runtimeRoot rev-parse HEAD).Trim() -ne $revision -or (& git -C $runtimeRoot status --porcelain=v1 --untracked-files=all)){throw 'Runtime holder source changed'}
$manifest=Get-Content -LiteralPath $baselinePath -Raw|ConvertFrom-Json -AsHashtable
$unchanged=@('services/mobile-bff','services/notification-service','services/document-renderer-service','services/shared','frontends/pwa-vue','build.gradle.kts','settings.gradle.kts','gradle')
$diff=@(& git -C $runtimeRoot diff --name-only 2ca6fc7e $revision -- @unchanged)
if($diff.Count){throw 'Reuse source changed; root decision required'}
$build=Get-Content -LiteralPath "$PSScriptRoot/build-exit.json" -Raw|ConvertFrom-Json
if($build.revision -ne $revision -or $build.exitCode -ne 0){throw 'Successful pinned build missing'}
$changed=@('academic','schedule','attendance','auth','gateway')
$inventory=@()
function Copy-VerifiedReuse([string]$relative,[string]$sha,[long]$bytes){
 $source=Join-Path $reuseRoot $relative;$target=Join-Path $runtimeRoot $relative
 $resolved=[IO.Path]::GetFullPath($target);$allowed=[IO.Path]::GetFullPath($runtimeRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar
 if(-not $resolved.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase)){throw 'Artifact escaped assigned holder'}
 $file=Get-Item -LiteralPath $source
 if($file.Length -ne $bytes -or (Get-FileHash -LiteralPath $source).Hash.ToLowerInvariant() -ne $sha){throw "Reuse artifact not exact accepted bytes: $relative"}
 New-Item -ItemType Directory -Force (Split-Path $target)|Out-Null
 Copy-Item -LiteralPath $source -Destination $target
 if((Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant() -ne $sha){throw 'Copied artifact mismatch'}
}
foreach($entry in $manifest.artifacts.jars){
 if($entry.name -notin $changed){Copy-VerifiedReuse $entry.relativePath $entry.sha256 $entry.bytes}
 $file=Get-Item -LiteralPath (Join-Path $runtimeRoot $entry.relativePath);$sha=(Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()
 $entry.sha256=$sha;$entry.bytes=[long]$file.Length
 $inventory+=@([ordered]@{name=$entry.name;sha256=$sha;bytes=$file.Length;mode=$(if($entry.name -in $changed){'rebuilt'}else{'verified reuse'})})
}
foreach($entry in $manifest.artifacts.pwaDist.files){Copy-VerifiedReuse ($manifest.artifacts.pwaDist.relativeRoot+'/'+$entry.relativePath) $entry.sha256 $entry.bytes}
$manifest.source.revision=$revision;$manifest.source.absoluteRepo=$runtimeRoot;$manifest.source.cleanBeforeBuild=$true;$manifest.source.cleanAfterBuild=$true
$manifest.producer.commands=@([ordered]@{command=$build.command;exitCode=0;startedAt=$build.startedAt;finishedAt=$build.finishedAt})
$output="$PSScriptRoot/build-manifest.json"
[IO.File]::WriteAllText($output,($manifest|ConvertTo-Json -Depth 20 -Compress),[Text.UTF8Encoding]::new($false))
& "$PSScriptRoot/canonicalize-manifest.ps1" | Out-Null
$result=[ordered]@{revision=$revision;baselineManifestSha256=$baselinePin;unchangedSourceDiff=$diff;jars=$inventory;pwaFilesVerified=$manifest.artifacts.pwaDist.files.Count;manifestSha256=(Get-FileHash -LiteralPath $output).Hash.ToLowerInvariant()}
[IO.File]::WriteAllText("$PSScriptRoot/artifact-verification.json",($result|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false));$result|ConvertTo-Json -Depth 8
