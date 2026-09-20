$ErrorActionPreference = 'Stop'
$path = Join-Path $PSScriptRoot 'inventory.json'
$inventory = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
foreach ($item in $inventory.items) {
    if ($item.source_id -eq 'repo-history') {
        $item.destination = $item.relative_path
        if ($item.status -eq 'duplicate') { $item.retained_address = "repo-history:$($item.relative_path)" }
    }
    if ($item.source_id -eq 'research' -and $null -ne $item.destination) {
        $item.destination = $item.destination -replace '^docs/research/reference-repository-research-contour/', 'docs/research/reference-repo/repository-research-contour/'
    }
}
$inventory | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $path -Encoding utf8
