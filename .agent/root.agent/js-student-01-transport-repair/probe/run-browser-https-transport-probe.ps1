[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$probeRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$evidenceRoot = Join-Path $probeRoot 'evidence'
$runId = [DateTimeOffset]::Now.ToString('yyyyMMdd-HHmmss')
$evidenceDir = Join-Path $evidenceRoot $runId
$certificatePath = Join-Path $evidenceDir 'transport.crt'
$keyPath = Join-Path $evidenceDir 'transport.key'
$resultPath = Join-Path $evidenceDir 'result.json'
$scriptPath = Join-Path $PSScriptRoot 'browser-https-transport-probe.mjs'
$opensslPath = 'C:\Program Files\Git\usr\bin\openssl.exe'
$nodePath = 'C:\Users\maksd\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
$playwrightPath = 'C:\Users\maksd\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules\playwright\index.mjs'
$chromePath = 'C:\Program Files\Google\Chrome\Application\chrome.exe'

foreach ($path in @($opensslPath, $nodePath, $playwrightPath, $chromePath, $scriptPath)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Required probe dependency is missing: $path" }
}
foreach ($port in @(28445, 28446)) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
    try { $listener.Start() } finally { $listener.Stop() }
}
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$certOutput = @(& $opensslPath req -x509 -nodes -newkey rsa:2048 -days 1 -keyout $keyPath -out $certificatePath -subj '/CN=127.0.0.1' -addext 'subjectAltName=IP:127.0.0.1' 2>&1)
if ($LASTEXITCODE -ne 0) { throw "OpenSSL certificate generation failed with exit ${LASTEXITCODE}: $($certOutput -join [Environment]::NewLine)" }
$output = @(& $nodePath $scriptPath $certificatePath $keyPath $playwrightPath $chromePath 2>&1)
$exitCode = $LASTEXITCODE
$jsonLine = @($output | ForEach-Object { [string]$_ } | Where-Object { $_.TrimStart().StartsWith('{') } | Select-Object -Last 1)
if ($jsonLine.Count -ne 1) { throw "Probe did not return JSON evidence: $($output -join [Environment]::NewLine)" }
$jsonLine | Set-Content -LiteralPath $resultPath -Encoding utf8NoBOM
Remove-Item -LiteralPath $keyPath -Force
if ($exitCode -ne 0) { exit $exitCode }
