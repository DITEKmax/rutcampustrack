$ErrorActionPreference = 'Stop'
$repo = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack'
Set-Location -LiteralPath $repo
$out = Join-Path $repo '.agent/audit-publication-20260920'
$private = Join-Path $env:TEMP 'rct-audit-publish-20260920'
New-Item -ItemType Directory -Path $private -Force | Out-Null
$rootHead = (git rev-parse HEAD).Trim()
$remoteBase = '87784165874e2da6fc261abc1c01584e24624289'
$excludePattern = '(^|/)(node_modules|\.git|\.gradle|build|dist|coverage|__pycache__|\.pytest_cache|trivy-cache|backend-artifacts|keys|certs|certificates|logs)(/|$)|\.(jar|db|pfx|p12|jks|keystore|key|pem|log|zip|exe|dll|pyc)$|(^|/)(\.env[^/]*|credentials[^/]*|secrets[^/]*)$'
$excluded = [System.Collections.Generic.List[object]]::new()
$worktrees = [System.Collections.Generic.List[object]]::new()
$record = $null
foreach($line in (git worktree list --porcelain)) {
  if($line.StartsWith('worktree ')) { $record = [ordered]@{path=$line.Substring(9);head='';branch=''}; $worktrees.Add($record) }
  elseif($line.StartsWith('HEAD ')) { $record.head=$line.Substring(5) }
  elseif($line.StartsWith('branch ')) { $record.branch=$line.Substring(7) }
}
$heads = @{}
$i=0
$inventory = [System.Collections.Generic.List[object]]::new()
foreach($wt in $worktrees) {
  if($wt.path -eq $repo){continue}
  $i++
  $id = '{0:D2}-{1}' -f $i,($wt.path.Split('/')[-1] -replace '[^a-zA-Z0-9_-]','_')
  $destRoot=Join-Path $out "worktrees/$id"
  New-Item -ItemType Directory -Path $destRoot -Force | Out-Null
  # Store committed source differences once per revision. Binary source assets stay in existing root archive.
  if(!$heads.ContainsKey($wt.head)) {
    $patchName="committed/$($wt.head).patch"
    New-Item -ItemType Directory -Path (Join-Path $out 'committed') -Force | Out-Null
    git -c core.quotePath=false diff --no-ext-diff --output="$(Join-Path $out $patchName)" $rootHead $wt.head -- services frontends proto docs AGENTS.md tests ':!*.pem' ':!*.key' ':!*.p12' ':!*.pfx' ':!*.jks' ':!*.env' ':!**/dist/**' ':!**/build/**'
    if($LASTEXITCODE -ne 0){throw "committed diff failed: $id"}
    $heads[$wt.head]=$patchName
  }
  git -C $wt.path -c core.quotePath=false status --short | Set-Content (Join-Path $destRoot 'status.txt')
  $tracked=@(git -C $wt.path -c core.quotePath=false diff --name-only HEAD)
  $untracked=@(git -C $wt.path -c core.quotePath=false ls-files --others --exclude-standard)
  $files=@($tracked + $untracked | Sort-Object -Unique)
  foreach($rel in $files) {
    if($rel -match '^\.agent/worktrees/' -or $rel -match $excludePattern) { $excluded.Add(@{worktree=$id;path=$rel;reason='runtime/cache/key-material exclusion'});continue }
    $src=Join-Path $wt.path $rel
    if(!(Test-Path -LiteralPath $src -PathType Leaf)){ $inventory.Add(@{worktree=$id;path=$rel;state='deleted'});continue }
    $file=Get-Item -LiteralPath $src
    if(($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $file.Length -gt 20000000) { $excluded.Add(@{worktree=$id;path=$rel;reason='link or >20MB artifact'});continue }
    $target=Join-Path $destRoot "files/$rel"
    New-Item -ItemType Directory -Path (Split-Path $target) -Force|Out-Null
    Copy-Item -LiteralPath $src -Destination $target
    $inventory.Add(@{worktree=$id;path=$rel;state=if($rel -in $tracked){'tracked-change'}else{'untracked'};sha256=(Get-FileHash $src).Hash})
  }
  @{id=$id;path=$wt.path;head=$wt.head;branch=$wt.branch;committedDiffFromRoot=$heads[$wt.head]}|ConvertTo-Json|Set-Content (Join-Path $destRoot 'identity.json')
}
$inventory|ConvertTo-Json -Depth 6|Set-Content (Join-Path $out 'worktree-file-inventory.json')
$worktrees|ConvertTo-Json -Depth 5|Set-Content (Join-Path $out 'worktrees.json')
git log --all --format='%H %ad %s' --date=iso-strict "$remoteBase.." | Set-Content (Join-Path $out 'local-commit-history.txt')
$candidates=@(git -c core.quotePath=false ls-files;git -c core.quotePath=false ls-files --others --exclude-standard)|Sort-Object -Unique
$include=[System.Collections.Generic.List[string]]::new()
foreach($rel in $candidates){
  if($rel -match '^\.agent/worktrees/' -or $rel -match $excludePattern){$excluded.Add(@{worktree='root';path=$rel;reason='runtime/cache/key-material exclusion'});continue}
  $src=Join-Path $repo $rel
  if(!(Test-Path -LiteralPath $src -PathType Leaf)){continue}
  $file=Get-Item -LiteralPath $src
  if(($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $file.Length -gt 20000000){$excluded.Add(@{worktree='root';path=$rel;reason='link or >20MB artifact'});continue}
  $include.Add($rel)
}
$excluded|ConvertTo-Json -Depth 5|Set-Content (Join-Path $out 'excluded-files.json')
$include.Add('.agent/audit-publication-20260920/excluded-files.json')
[IO.File]::WriteAllText((Join-Path $private 'include-paths.txt'),(($include|Sort-Object -Unique)-join "`n")+"`n",[Text.UTF8Encoding]::new($false))
@{rootHead=$rootHead;remoteBase=$remoteBase;worktrees=$worktrees.Count;distinctRevisionPatches=$heads.Count;capturedWorktreeFiles=$inventory.Count;candidateFiles=$include.Count;excluded=$excluded.Count;privateDir=$private}|ConvertTo-Json|Set-Content (Join-Path $out 'preparation.json')
Get-Content (Join-Path $out 'preparation.json')
