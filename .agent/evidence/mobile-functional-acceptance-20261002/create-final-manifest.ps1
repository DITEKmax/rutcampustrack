$ErrorActionPreference = 'Stop'
$holder = (Resolve-Path "$PSScriptRoot/../../..").Path
$runner = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/runner.ps1'
$tokens = $null; $parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($runner, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Existing runner syntax failure' }
foreach ($name in @('Assert-That','Get-JsonPropertyValue','Assert-CanonicalPathList','Sort-CanonicalPathObjects','Convert-PwaFilesToCanonicalText','Convert-TrustedManifestToCanonicalObject')) {
    $definitions = @($ast.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name }, $true))
    if ($definitions.Count -ne 1) { throw "One existing canonical utility required: $name" }
    . ([scriptblock]::Create($definitions[0].Extent.Text))
}
function Get-DistPin([string]$relativeRoot) {
    $root = Join-Path $holder $relativeRoot
    $files = @(Get-ChildItem -LiteralPath $root -File -Recurse | ForEach-Object {
        [ordered]@{bytes=$_.Length; relativePath=[IO.Path]::GetRelativePath($root,$_.FullName).Replace('\','/'); sha256=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant()}
    })
    $files = @(Sort-CanonicalPathObjects -Items $files)
    Assert-CanonicalPathList -Items $files -Context $relativeRoot
    $text = Convert-PwaFilesToCanonicalText -Files $files
    $digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text))).ToLowerInvariant()
    return [ordered]@{files=$files; manifestSha256=$digest; relativeRoot=$relativeRoot}
}
$reuse = Get-Content "$PSScriptRoot/final-reuse-pins.json" -Raw | ConvertFrom-Json
Assert-That (@($reuse | Where-Object {-not $_.match}).Count -eq 0 -and $reuse.Count -eq 7) 'Seven accepted reusable JARs required'
$jars = @($reuse | ForEach-Object { [ordered]@{bytes=$_.bytes;name=$_.name;relativePath=$_.relativePath;sha256=$_.sha256} })
$schedulePath = 'services/schedule-service/schedule-app/build/libs/schedule-app-0.1.0.jar'
$scheduleFile = Get-Item -LiteralPath (Join-Path $holder $schedulePath)
$jars += [ordered]@{bytes=$scheduleFile.Length;name='schedule';relativePath=$schedulePath;sha256=(Get-FileHash -LiteralPath $scheduleFile.FullName).Hash.ToLowerInvariant()}
$jars = @(Sort-CanonicalPathObjects -Items $jars)
$commands = @()
foreach ($pair in @(@('build-invocation.json','backend-build-result.json'),@('schedule-build-invocation.json','schedule-build-result.json'),@('vue-build-r2-invocation.json','vue-build-r2-result.json'))) {
    $invocation = Get-Content (Join-Path $PSScriptRoot $pair[0]) -Raw | ConvertFrom-Json -DateKind String
    $result = Get-Content (Join-Path $PSScriptRoot $pair[1]) -Raw | ConvertFrom-Json -DateKind String
    Assert-That ($result.exitCode -eq 0) 'Accepted producer command must have terminal exit zero'
    $commands += [ordered]@{command=$invocation.command;exitCode=$result.exitCode;startedAt=$result.startedAt;finishedAt=$result.finishedAt}
}
$manifest = [ordered]@{
    artifacts=[ordered]@{jars=$jars;pwaDist=(Get-DistPin 'frontends/pwa-vue/dist')}
    producer=[ordered]@{commands=$commands;environment=[ordered]@{gradle='Gradle 8.12';hostOs=[Environment]::OSVersion.VersionString;java='openjdk version "21.0.10" 2026-01-20 LTS';node='v24.14.0';powershell=$PSVersionTable.PSVersion.ToString()}}
    schemaVersion=1
    source=[ordered]@{absoluteRepo=$holder.Replace('\','/');cleanAfterBuild=$false;cleanBeforeBuild=$true;revision=(git -C $holder rev-parse HEAD).Trim()}
}
# Own evidence is currently untracked. Root finalizes cleanAfterBuild only after
# ordinary checkout back to the frozen product revision and actual union status.
$object = $manifest | ConvertTo-Json -Depth 20 | ConvertFrom-Json -Depth 20 -DateKind String
$canonical = Convert-TrustedManifestToCanonicalObject -Manifest $object | ConvertTo-Json -Depth 20 -Compress
$path = "$PSScriptRoot/build-manifest.json"
[IO.File]::WriteAllText($path,$canonical,[Text.UTF8Encoding]::new($false))
$roundtrip = $canonical | ConvertFrom-Json -Depth 20 -DateKind String
Assert-That ($canonical -ceq (Convert-TrustedManifestToCanonicalObject -Manifest $roundtrip | ConvertTo-Json -Depth 20 -Compress)) 'Canonical roundtrip differs'
[IO.File]::WriteAllText("$PSScriptRoot/tma-artifacts.json",((Get-DistPin 'frontends/tma-vue/dist') | ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
$result = [ordered]@{exitCode=0;existingUtilitySource=$runner;sourceSha256=(Get-FileHash -LiteralPath $runner).Hash.ToLowerInvariant();manifestSha256=(Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant();canonicalBytesMatch=$true;cleanFinalizationPending=$true;schedule=$jars | Where-Object name -eq 'schedule';pwaFileCount=$manifest.artifacts.pwaDist.files.Count;tmaServedByRunner=$false}
[IO.File]::WriteAllText("$PSScriptRoot/canonical-verification.json",($result | ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
$result | ConvertTo-Json -Depth 8
