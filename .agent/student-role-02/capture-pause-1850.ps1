$ErrorActionPreference = 'Stop'
$repoRoot = (Get-Location).Path
$pauseRoot = Join-Path $repoRoot '.agent/student-role-02/pause-2026-09-07-1850Z'
New-Item -ItemType Directory -Path $pauseRoot -Force | Out-Null
$base = '8002b9ea4356b10779c5bb9a6d99746d32d78ae2'
$integrated = 'd3c31acb8cce53791a4981e5858a37d44fdc9a0e'
$bundles = @(
  @{name='integration'; baseline=$base},
  @{name='homework-ui'; baseline=$integrated},
  @{name='requests-domain'; baseline=$base},
  @{name='requests-transport'; baseline=$integrated},
  @{name='dependency-checks'; baseline=$integrated}
)
$reports = foreach ($bundle in $bundles) {
  $wt = Join-Path $repoRoot ('.agent/worktrees/student-role-02/' + $bundle.name)
  $out = Join-Path $pauseRoot $bundle.name
  New-Item -ItemType Directory -Path $out -Force | Out-Null
  $branch = (& git -C $wt branch --show-current).Trim()
  $head = (& git -C $wt rev-parse HEAD).Trim()
  $stat = @(& git -c core.quotePath=false -C $wt diff --numstat $bundle.baseline -- . ':!.agent' ':!.codex' ':!.agents')
  if ($LASTEXITCODE -ne 0) { throw "numstat failed: $($bundle.name)" }
  $newPaths = @(& git -c core.quotePath=false -C $wt ls-files --others --exclude-standard -- . ':!.agent' ':!.codex' ':!.agents')
  if ($LASTEXITCODE -ne 0) { throw "new path inventory failed: $($bundle.name)" }
  $rows = [Collections.Generic.List[object]]::new()
  foreach ($line in $stat) {
    if (-not $line) { continue }
    $parts = $line -split "`t", 3
    if ($parts.Count -ne 3) { throw "Unexpected numstat row" }
    $rows.Add([pscustomobject]@{path=$parts[2];kind='tracked';added=if($parts[0] -eq '-'){$null}else{[int]$parts[0]};removed=if($parts[1] -eq '-'){$null}else{[int]$parts[1]};sha256=$null})
  }
  foreach ($relative in $newPaths) {
    if (-not $relative) { continue }
    if ($relative -match '(^|/)(\.env[^/]*|[^/]+\.(pem|key|p12|jks))$') { throw "Unexpected key/environment path: snapshot requires classification" }
    $bytes = [IO.File]::ReadAllBytes((Join-Path $wt $relative))
    $binary = $bytes -contains [byte]0
    $lines = $null
    if (-not $binary) {
      $textValue = [Text.Encoding]::UTF8.GetString($bytes)
      $lines = [regex]::Matches($textValue, "`n").Count
      if ($textValue.Length -gt 0 -and -not $textValue.EndsWith("`n")) { $lines++ }
    }
    $rows.Add([pscustomobject]@{path=$relative;kind='untracked';added=$lines;removed=if($binary){$null}else{0};sha256=$null})
  }
  foreach ($row in $rows) {
    $source = Join-Path $wt $row.path
    if (Test-Path -LiteralPath $source -PathType Leaf) {
      $target = Join-Path "$out/files" $row.path
      New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
      Copy-Item -LiteralPath $source -Destination $target
      $row.sha256 = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
      if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $row.sha256) { throw "Snapshot hash mismatch" }
    }
  }
  & git -C $wt diff --binary $bundle.baseline -- . ':!.agent' ':!.codex' ':!.agents' | Set-Content -LiteralPath "$out/tracked.patch" -Encoding utf8
  $rows | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$out/files.json" -Encoding utf8
  $groups = foreach ($group in ($rows | Group-Object { if($_.path.StartsWith('frontends/')){'frontend'}elseif($_.path.StartsWith('docs/openapi/')){'openapi'}else{'backend-build-proto-tests'} })) {
    [pscustomobject]@{category=$group.Name;files=$group.Count;added=($group.Group | Measure-Object added -Sum).Sum;removed=($group.Group | Measure-Object removed -Sum).Sum}
  }
  [pscustomobject]@{name=$bundle.name;worktree=$wt;branch=$branch;head=$head;baseline=$bundle.baseline;files=$rows.Count;added=($rows | Measure-Object added -Sum).Sum;removed=($rows | Measure-Object removed -Sum).Sum;untracked=@($rows | Where-Object kind -eq untracked).Count;categories=@($groups);paths=@($rows.path)}
}
$baseline = Get-Content (Join-Path $repoRoot '.agent/student-role-02/baseline.json') -Raw | ConvertFrom-Json
$ownerRows = @($baseline.dirtyTracked | Where-Object { $_.state -eq 'present' -and $_.path -ne 'skills-lock.json' })
$ownerMismatches = @($ownerRows | Where-Object { (Get-FileHash -LiteralPath (Join-Path $repoRoot $_.path) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $_.sha256.ToLowerInvariant() })
$result = [pscustomobject]@{capturedAt=[DateTime]::UtcNow.ToString('o');status='PAUSED';mainHead=(& git rev-parse HEAD).Trim();mainBranch=(& git branch --show-current).Trim();ownerFilesChecked=$ownerRows.Count;ownerMismatches=@($ownerMismatches.path);skillsLockAbsent=(-not (Test-Path -LiteralPath (Join-Path $repoRoot 'skills-lock.json')));configSha256=(Get-FileHash -LiteralPath (Join-Path $repoRoot '.codex/config.toml') -Algorithm SHA256).Hash;bundles=@($reports)}
$result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath "$pauseRoot/inventory.json" -Encoding utf8
$result | Select-Object capturedAt,status,mainHead,mainBranch,ownerFilesChecked,ownerMismatches,skillsLockAbsent,configSha256 | ConvertTo-Json -Depth 4
$reports | Select-Object name,branch,files,added,removed,untracked,categories | ConvertTo-Json -Depth 5
