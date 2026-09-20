$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestPath = Join-Path $repoRoot 'docs\sources\manifest.yaml'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$transferDate = Get-Date -Format 'yyyy-MM-dd'
foreach ($entry in $manifest.entries) {
    $entry.decision_owner = $null
    $entry.decision_date = $null
    $entry | Add-Member -NotePropertyName transfer_owner -NotePropertyValue 'root' -Force
    $entry | Add-Member -NotePropertyName transfer_date -NotePropertyValue $transferDate -Force
    if ($entry.source -eq 'kit:journal/DECISIONS.md') {
        $entry.decision_owner = 'project owner'
        $entry.decision_date = '2026-09-05'
    }
}
$manifest | ConvertTo-Json -Depth 14 | Set-Content -LiteralPath $manifestPath -Encoding utf8
