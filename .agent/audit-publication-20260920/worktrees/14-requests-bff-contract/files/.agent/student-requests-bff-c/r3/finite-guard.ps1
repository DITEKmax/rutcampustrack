[CmdletBinding()]
param([string]$RepoRoot = (Get-Location).Path)
$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$sourceManifestPath = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-transport/.agent/student-role-02/diff.json'
$repairManifestPath = 'C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-isolation/.agent/student-requests-isolation-c/repair-manifest.json'
$exact5Path = Join-Path $RepoRoot '.agent/student-requests-bff-c/r3/exact5-manifest.json'
$expectedSourceManifestSha = '4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4'
$expectedRepairManifestSha = '7B64350F73272787BD383E89611B75BE10959B02B4A0342695E45C46769462F3'
$expectedRevision = 'd3c31acb8cce53791a4981e5858a37d44fdc9a0e'
function Get-Sha256([string]$Path) { (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToUpperInvariant() }
function Normalize-RepoPath([string]$Path) { $Path.Replace('/', '\').TrimStart('\') }
function Assert-Equal([string]$Name, [string]$Actual, [string]$Expected) {
  if ($Actual -ne $Expected) { throw "$Name mismatch: actual=$Actual expected=$Expected" }
}
Assert-Equal 'revision' ((git -C $RepoRoot rev-parse HEAD).Trim()) $expectedRevision
Assert-Equal 'sourceManifestSha256' (Get-Sha256 $sourceManifestPath) $expectedSourceManifestSha
Assert-Equal 'repairManifestSha256' (Get-Sha256 $repairManifestPath) $expectedRepairManifestSha
$source = Get-Content -LiteralPath $sourceManifestPath -Raw | ConvertFrom-Json
$repair = Get-Content -LiteralPath $repairManifestPath -Raw | ConvertFrom-Json
$exact5 = Get-Content -LiteralPath $exact5Path -Raw | ConvertFrom-Json
$sourceProducts = @($source.files | Where-Object { $_.path -notlike '.agent/*' -and $_.path -notlike '.agent\*' })
$acceptedRepairs = @($repair.productFiles)
$bffFiles = @($exact5.files)
Assert-Equal 'sourceProductPaths' ([string]$sourceProducts.Count) '82'
Assert-Equal 'acceptedRepairPaths' ([string]$acceptedRepairs.Count) '2'
Assert-Equal 'bffPaths' ([string]$bffFiles.Count) '5'
$repairPathSet = @{}
foreach ($entry in $acceptedRepairs) { $repairPathSet[(Normalize-RepoPath $entry.path)] = $entry.sha256.ToUpperInvariant() }
$bffPathSet = @{}
foreach ($entry in $bffFiles) { $bffPathSet[(Normalize-RepoPath $entry.relativePath)] = $entry.sha256.ToUpperInvariant() }
$mismatches = [System.Collections.Generic.List[string]]::new()
foreach ($entry in $sourceProducts) {
  $relative = Normalize-RepoPath $entry.path
  if ($repairPathSet.ContainsKey($relative) -or $bffPathSet.ContainsKey($relative)) { continue }
  $full = Join-Path $RepoRoot $relative
  if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { $mismatches.Add("missing:$relative"); continue }
  if ((Get-Sha256 $full) -ne $entry.sha256.ToUpperInvariant()) { $mismatches.Add("source:$relative") }
}
foreach ($entry in $acceptedRepairs) {
  $relative = Normalize-RepoPath $entry.path; $full = Join-Path $RepoRoot $relative
  if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { $mismatches.Add("missing-repair:$relative"); continue }
  if ((Get-Sha256 $full) -ne $repairPathSet[$relative]) { $mismatches.Add("repair:$relative") }
}
foreach ($entry in $bffFiles) {
  $relative = Normalize-RepoPath $entry.relativePath; $full = Join-Path $RepoRoot $relative
  if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { $mismatches.Add("missing-bff:$relative"); continue }
  if ((Get-Sha256 $full) -ne $bffPathSet[$relative]) { $mismatches.Add("bff:$relative") }
}
if ($mismatches.Count -ne 0) { throw "mismatches=$($mismatches.Count); $($mismatches -join ',')" }
Write-Output 'finite-guard=PASS'
Write-Output "revision=$expectedRevision"
Write-Output "sourceManifest=$sourceManifestPath sha256=$expectedSourceManifestSha"
Write-Output "acceptedRepairManifest=$repairManifestPath sha256=$expectedRepairManifestSha"
Write-Output 'productPaths=82'
Write-Output 'acceptedRepairPaths=2'
Write-Output 'bffPaths=5'
Write-Output 'mismatches=0'
exit 0
