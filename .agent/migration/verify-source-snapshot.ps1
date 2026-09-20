param(
    [string]$InventoryPath = (Join-Path $PSScriptRoot 'inventory.json'),
    [string]$OutputPath = (Join-Path $PSScriptRoot 'source-snapshot.json')
)

$ErrorActionPreference = 'Stop'
$startedAt = Get-Date -Format 'o'
$inventory = Get-Content -LiteralPath $InventoryPath -Raw | ConvertFrom-Json
$sources = [System.Collections.Generic.List[object]]::new()
foreach ($source in $inventory.source_summaries) {
    $files = 0; $bytes = [int64]0; $maxWriteUtc = [datetime]::MinValue
    $stack = [System.Collections.Generic.Stack[string]]::new(); $stack.Push($source.root)
    while ($stack.Count -gt 0) {
        $directory = $stack.Pop()
        foreach ($entry in (Get-ChildItem -LiteralPath $directory -Force)) {
            if ($source.source_id -eq 'repo-history' -and $directory -eq $source.root -and $entry.Name -notin @('docs', '.planning', 'CLAUDE.md')) { continue }
            if ($entry.PSIsContainer) {
                if ($entry.Name -in @('.git','node_modules','build','dist','cache','.cache','.pytest_cache','.idea','.vscode','tmp')) { continue }
                $stack.Push($entry.FullName); continue
            }
            $files++; $bytes += $entry.Length
            if ($entry.LastWriteTimeUtc -gt $maxWriteUtc) { $maxWriteUtc = $entry.LastWriteTimeUtc }
        }
    }
    $sources.Add([ordered]@{ source_id=$source.source_id; files=$files; bytes=$bytes; newest_file_write_utc=$maxWriteUtc.ToString('o'); matches_inventory=($files -eq $source.files -and $bytes -eq $source.bytes) })
}
[ordered]@{ started_at=$startedAt; finished_at=(Get-Date -Format 'o'); inventory_scan_finished_at=$inventory.scan_finished_at; sources=$sources; stable=(@($sources | Where-Object { -not $_.matches_inventory }).Count -eq 0) } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutputPath -Encoding utf8
