[CmdletBinding()]
param(
    [ValidateSet('pwa', 'tma')]
    [string]$Surface = 'pwa',
    [string]$DevTlsCertificate = "$env:LOCALAPPDATA/RutCampusTrack/certs/localhost.pem",
    [string]$DevTlsPrivateKey = "$env:LOCALAPPDATA/RutCampusTrack/certs/localhost-key.pem"
)

$ErrorActionPreference = 'Stop'
foreach ($tlsPath in @($DevTlsCertificate, $DevTlsPrivateKey)) {
    if (-not (Test-Path -LiteralPath $tlsPath -PathType Leaf)) {
        throw 'Existing local TLS files are required; pass the certificate and key paths explicitly if needed.'
    }
}
if ($env:NODE_TLS_REJECT_UNAUTHORIZED -eq '0') {
    throw 'TLS verification must be enabled for the local development preview.'
}

$settings = @{
    RCT_DEV_TLS_CERT = [IO.Path]::GetFullPath($DevTlsCertificate)
    RCT_DEV_TLS_KEY = [IO.Path]::GetFullPath($DevTlsPrivateKey)
    VITE_API_PROXY_TARGET = 'https://127.0.0.1:18514'
    VITE_PUBLIC_BASE = $(if ($Surface -eq 'pwa') { '/app/' } else { '/mini-app/' })
    NODE_OPTIONS = (($env:NODE_OPTIONS, '--use-system-ca' | Where-Object { $_ }) -join ' ')
}
$previous = @{}
foreach ($name in $settings.Keys) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

Push-Location -LiteralPath (Join-Path $PSScriptRoot '../frontends')
try {
    foreach ($name in $settings.Keys) {
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }
    & npm.cmd run "dev:$Surface"
    if ($LASTEXITCODE -ne 0) { throw "Vue development server exited with code $LASTEXITCODE" }
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    Pop-Location
}
