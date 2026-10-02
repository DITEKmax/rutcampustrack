$ErrorActionPreference='Stop'
$root='C:/Users/maksd/IntelliJIDEA/rutcampustrack'
$runtimeRoot="$root/.agent/worktrees/v2-runtime-build-r3"
$acceptedPath="$PSScriptRoot/combined-build-manifest-2948e60e.json"
$acceptedPin='d8bdd51c6aee18077d43810175dd49e5ffe5391fff09d07443142e571856ed9b'
if((Get-FileHash $acceptedPath).Hash.ToLowerInvariant() -ne $acceptedPin){throw 'Reuse baseline manifest changed'}
$manifest=Get-Content $acceptedPath -Raw|ConvertFrom-Json -AsHashtable
$revision=(& git -C $runtimeRoot rev-parse HEAD).Trim()
if($revision -ne '2ca6fc7e4301b6547a323b58155135d932080003'){throw 'Frozen runtime revision changed'}
if((& git -C $runtimeRoot status --porcelain --untracked-files=no).Count -gt 0){throw 'Runtime source dirty'}
$build=Get-Content "$PSScriptRoot/homework-build-exit.json" -Raw|ConvertFrom-Json
if($build.revision -ne $revision -or $build.exitCode -ne 0 -or -not (Select-String -LiteralPath "$PSScriptRoot/homework-build.log" -Pattern 'BUILD SUCCESSFUL' -Quiet)){throw 'Successful build evidence missing'}
$unchangedScope=@('services/api-gateway','services/auth-service','services/notification-service','services/document-renderer-service','services/shared','frontends/pwa-vue','build.gradle.kts','settings.gradle.kts','gradle')
$sourceDiff=@(& git -C $runtimeRoot diff --name-only 2948e60e $revision -- @unchangedScope)
if($sourceDiff.Count -gt 0){throw 'Reuse service/shared source changed; root must reassess'}
$protoDiff=& git -C $runtimeRoot diff 2948e60e $revision -- proto/academic.proto proto/schedule.proto
[IO.File]::WriteAllText("$PSScriptRoot/homework-reuse-source-scope.txt",($protoDiff -join [Environment]::NewLine),[Text.UTF8Encoding]::new($false))
$changed=@('academic','schedule','attendance','mobileBff')
$inventory=[Collections.Generic.List[object]]::new()
foreach($entry in $manifest.artifacts.jars){
 $file=Get-Item -LiteralPath (Join-Path $runtimeRoot $entry.relativePath)
 $hash=(Get-FileHash $file.FullName).Hash.ToLowerInvariant()
 if($entry.name -notin $changed -and ($hash -ne $entry.sha256 -or $file.Length -ne $entry.bytes)){throw "Unchanged JAR mismatch $($entry.name)"}
 $entry.sha256=$hash;$entry.bytes=[long]$file.Length
 $inventory.Add([ordered]@{name=$entry.name;relativePath=$entry.relativePath;mode=$(if($entry.name -in $changed){'rebuilt'}else{'verified reuse'});bytes=$entry.bytes;sha256=$hash})
}
foreach($entry in $manifest.artifacts.pwaDist.files){
 $file=Get-Item -LiteralPath (Join-Path (Join-Path $runtimeRoot $manifest.artifacts.pwaDist.relativeRoot) $entry.relativePath)
 if((Get-FileHash $file.FullName).Hash.ToLowerInvariant() -ne $entry.sha256 -or $file.Length -ne $entry.bytes){throw "Unchanged PWA mismatch $($entry.relativePath)"}
}
$manifest.source.revision=$revision
$manifest.producer.commands=@([ordered]@{command=$build.command;exitCode=0;finishedAt=$build.finishedAt;startedAt=$build.startedAt})
$outputPath="$PSScriptRoot/homework-build-manifest-2ca6fc7e.json"
[IO.File]::WriteAllText($outputPath,($manifest|ConvertTo-Json -Depth 20 -Compress),[Text.UTF8Encoding]::new($false))
$report=[ordered]@{revision=$revision;reuseBaselineManifestSha256=$acceptedPin;unchangedServiceSourceDiff=@($sourceDiff);protoScope='Academic binding_mode16 and Schedule new fields/messages/RPCs additive; unchanged consumers reusable';jars=@($inventory);pwaFilesVerified=$manifest.artifacts.pwaDist.files.Count;manifestSha256=(Get-FileHash $outputPath).Hash.ToLowerInvariant();buildExit=0;buildLog='homework-build.log'}
[IO.File]::WriteAllText("$PSScriptRoot/homework-artifact-verification.json",($report|ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
$report|ConvertTo-Json -Depth 8
