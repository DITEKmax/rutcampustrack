$ErrorActionPreference = 'Stop'
$workspace = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack'
$checkpoint = Join-Path $workspace '.agent/orchestration-v2/checkpoints/2026-09-16-safe-stop'
$names = @('v2-access-scope','v2-assignment-authority','v2-binding-proto','v2-integration-a2-l3','v2-map-read','v2-requests-contract','v2-requests-harness','v2-requests-ui','v2-runtime-build')
$records = @()
foreach ($name in $names) {
    $target = Join-Path $workspace ".agent/worktrees/$name"
    $gitArgs = @('-c', "safe.directory=$target", '-c', 'core.quotepath=false', '-C', $target)
    $head = & git @gitArgs rev-parse HEAD 2>$null
    if ($LASTEXITCODE -ne 0) { throw "Cannot read HEAD: $name" }
    $branch = & git @gitArgs branch --show-current 2>$null
    $status = @(& git @gitArgs status --porcelain=v1 -uall 2>$null)
    if ($LASTEXITCODE -ne 0) { throw "Cannot read status: $name" }
    $paths = @(& git @gitArgs ls-files -m -o --exclude-standard 2>$null)
    if ($LASTEXITCODE -ne 0) { throw "Cannot list files: $name" }
    $localEvidence = Join-Path $target '.agent'
    if (Test-Path -LiteralPath $localEvidence) {
        $paths += @(Get-ChildItem -LiteralPath $localEvidence -File -Recurse | Where-Object {
            $_.FullName -notmatch '[\\/](node_modules|build|\.gradle|worktrees|keys|secrets)[\\/]'
        } | ForEach-Object { [IO.Path]::GetRelativePath($target, $_.FullName).Replace('\','/') })
    }
    if ($name -in @('v2-integration-a2-l3','v2-runtime-build')) {
        $accepted = Join-Path $workspace '.agent/worktrees/v2-integration-a2-l3/.agent/integration-i1/i5-union-manifest.sha256'
        $paths += @(Get-Content -LiteralPath $accepted | ForEach-Object { ($_ -split '=',2)[0] })
    }
    $files = @()
    foreach ($relative in ($paths | Sort-Object -Unique)) {
        if ([string]::IsNullOrWhiteSpace($relative)) { continue }
        $full = [IO.Path]::GetFullPath((Join-Path $target $relative))
        if (-not $full.StartsWith([IO.Path]::GetFullPath($target) + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Path outside checkpoint scope' }
        if ($relative -match '(^|/)(\.env($|\.(?!.*example))|[^/]*\.(pem|key|p12|pfx)$|credentials[^/]*|secrets[^/]*)') {
            $files += [ordered]@{path=$relative;state='sensitive-name-not-read'}
            continue
        }
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { $files += [ordered]@{path=$relative;state='deleted-or-not-file'}; continue }
        $item = Get-Item -LiteralPath $full -Force
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { $files += [ordered]@{path=$relative;state='reparse-not-followed'}; continue }
        $files += [ordered]@{path=$relative;bytes=$item.Length;sha256=(Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash}
    }
    $records += [ordered]@{name=$name;path=$target;head=([string]$head).Trim();branch=([string]$branch).Trim();status=$status;files=$files}
}
$external = Join-Path $workspace '.agent/orchestration-v2/build-i6'
$externalFiles = @(Get-ChildItem -LiteralPath $external -File -Recurse | Where-Object { -not ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) } | ForEach-Object { [ordered]@{path=[IO.Path]::GetRelativePath($external,$_.FullName).Replace('\','/');bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash} })
$records += [ordered]@{name='build-i6-external';path=$external;head='external orchestration files';branch='N/A';status=@('STOP WIP not accepted');files=$externalFiles}
$snapshot = [ordered]@{capturedAt=(Get-Date -Format o);purpose='Owner STOP: files preserved in place; no commit, deployment or runtime check';worktrees=$records}
$output = Join-Path $checkpoint 'worktrees.json'
$snapshot | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $output -Encoding utf8
$verified = 0
$mismatches = @()
foreach ($record in $records) {
    foreach ($file in $record.files) {
        if (-not $file.Contains('sha256')) { continue }
        $actual = (Get-FileHash -LiteralPath (Join-Path $record.path $file.path) -Algorithm SHA256).Hash
        if ($actual -ne $file.sha256) { $mismatches += "$($record.name)/$($file.path)" }
        $verified++
    }
}
$verification = [ordered]@{verifiedAt=(Get-Date -Format o);worktrees=$records.Count;hashedFiles=$verified;mismatches=$mismatches;snapshotSha256=(Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash;note='Integrity check, not product tests. Files remain in original worktrees; this is not an external backup.'}
$verification | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $checkpoint 'checkpoint-verification.json') -Encoding utf8
$verification | ConvertTo-Json -Depth 5
if ($mismatches.Count) { exit 1 }
