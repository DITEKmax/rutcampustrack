$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$indexPath = Join-Path $repoRoot 'docs\INDEX.md'
$archiveRelativePath = 'docs/archive/transfer-20260905/INDEX.md'
$archivePath = Join-Path $repoRoot ($archiveRelativePath -replace '/', '\\')
if (-not (Test-Path -LiteralPath $archivePath -PathType Leaf)) { throw 'The preserved INDEX.md is missing.' }
$prefix = @"
<!--
Materials transfer addition: 2026-09-05.
This dated addition supersedes only the navigation statement in the previous INDEX.md dated 2026-04-27.
The exact prior version is preserved at $archiveRelativePath.
Source provenance registry: docs/sources/manifest.yaml.
-->

## Materials transfer registry -- 2026-09-05

docs/sources/manifest.yaml lists every material and its status. Archive records are evidence, not active instructions or adopted product decisions. Open owner decisions remain blocked in the registry and transfer report.

---

"@
$prefixBytes = [System.Text.UTF8Encoding]::new($false).GetBytes($prefix)
$originalBytes = [System.IO.File]::ReadAllBytes($archivePath)
$result = [byte[]]::new($prefixBytes.Length + $originalBytes.Length)
[System.Buffer]::BlockCopy($prefixBytes, 0, $result, 0, $prefixBytes.Length)
[System.Buffer]::BlockCopy($originalBytes, 0, $result, $prefixBytes.Length, $originalBytes.Length)
[System.IO.File]::WriteAllBytes($indexPath, $result)
