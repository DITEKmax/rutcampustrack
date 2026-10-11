#!/usr/bin/env pwsh
# Project-local interpreter/cache; no activation or global configuration.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
$savedEnvironment = @{}
foreach ($name in @('UV_CACHE_DIR', 'PRE_COMMIT_HOME', 'GOCACHE', 'GOMODCACHE')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    $commonGit = & git rev-parse --path-format=absolute --git-common-dir
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve common Git directory.' }
    $commonGit = [IO.Path]::GetFullPath($commonGit.Trim())
    $mainRoot = Split-Path -Parent $commonGit
    $toolRoot = Join-Path $mainRoot '.agent/tooling'
    $python = Join-Path $toolRoot 'pre-commit-env/Scripts/python.exe'
    $env:UV_CACHE_DIR = Join-Path $toolRoot 'uv-cache'
    $env:PRE_COMMIT_HOME = Join-Path $toolRoot 'pre-commit-cache'
    $env:GOCACHE = Join-Path $toolRoot 'go-build'
    $env:GOMODCACHE = Join-Path $toolRoot 'go-mod'
    if (!(Test-Path -LiteralPath $python)) {
        & uv venv --no-python-downloads (Join-Path $toolRoot 'pre-commit-env')
        if ($LASTEXITCODE -ne 0) { throw 'Local Python environment creation failed.' }
    }
    & uv pip install --python $python 'pre-commit==4.6.2'
    if ($LASTEXITCODE -ne 0) { throw 'Local pre-commit installation failed.' }
    $effectiveHooksPath = & git config --get core.hooksPath
    if ($LASTEXITCODE -eq 0) {
        $localHooksPath = & git config --local --get-all core.hooksPath
        if ($LASTEXITCODE -ne 0 -or @($localHooksPath).Count -ne 1) {
            throw 'Inherited or multiple core.hooksPath values require a separate decision.'
        }
        $defaultHooks = [IO.Path]::GetFullPath((Join-Path $commonGit 'hooks'))
        $hooksCandidate = if ([IO.Path]::IsPathRooted($localHooksPath)) { $localHooksPath } else { Join-Path $projectRoot $localHooksPath }
        $actualHooks = [IO.Path]::GetFullPath($hooksCandidate)
        if ($actualHooks -ne $defaultHooks) { throw 'Custom core.hooksPath preserved; installation stopped.' }
        & git config --local --unset-all core.hooksPath
        if ($LASTEXITCODE -ne 0) { throw 'Cannot remove default-equivalent local core.hooksPath.' }
        & git config --get core.hooksPath | Out-Null
        if ($LASTEXITCODE -eq 0) {
            & git config --local core.hooksPath $localHooksPath
            throw 'Inherited core.hooksPath preserved; installation stopped.'
        }
    }
    & $python -m pre_commit install
    if ($LASTEXITCODE -ne 0) { throw 'Git hook installation failed.' }
    $hook = Join-Path $commonGit 'hooks/pre-commit'
    $content = [IO.File]::ReadAllText($hook)
    if (!$content.Contains('# ID: 138fd403232d2ddd5efb44317e38bf03')) { throw 'Unexpected generated hook format.' }
    # Generated hook keeps its standard interpreter/arguments; only cache roots are persisted.
    $exports = foreach ($name in @('PRE_COMMIT_HOME', 'GOCACHE', 'GOMODCACHE')) {
        $value = [Environment]::GetEnvironmentVariable($name, 'Process').Replace('\', '/')
        if ($value.Contains("'")) { throw 'Unsupported apostrophe in tooling path; generated hook left intact.' }
        "export $name='$value'"
    }
    $content = $content.Replace('# start templated', (($exports -join "`n") + "`n`n# start templated"))
    [IO.File]::WriteAllText($hook, $content.Replace("`r`n", "`n"), [Text.UTF8Encoding]::new($false))
    & $python -m pre_commit install-hooks
    if ($LASTEXITCODE -ne 0) { throw 'Configured hook dependency provisioning failed.' }
    Write-Output "Installed $hook; shared cache $env:PRE_COMMIT_HOME"
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
    Pop-Location
}
