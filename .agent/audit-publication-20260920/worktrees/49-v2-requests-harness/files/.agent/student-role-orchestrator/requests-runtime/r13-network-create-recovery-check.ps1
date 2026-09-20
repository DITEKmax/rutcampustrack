#requires -Version 7.4
[CmdletBinding()]
param([switch]$UseHeadRunner)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$runnerPath = Join-Path $PSScriptRoot 'runner.ps1'
$runnerSource = 'working-tree'
if ($UseHeadRunner) {
    $relativeRunner = '.agent/student-role-orchestrator/requests-runtime/runner.ps1'
    $gitArgs = @('-c', "safe.directory=$((Get-Location).Path)", '-C', (Get-Location).Path, 'show', "HEAD:$relativeRunner")
    $runnerText = (& git @gitArgs) -join [Environment]::NewLine
    if ($LASTEXITCODE -ne 0) { throw 'unable to read HEAD runner source for baseline regression' }
    $runnerSource = 'HEAD:runner.ps1'
} else {
    $runnerText = Get-Content -LiteralPath $runnerPath -Raw
}
$start = $runnerText.IndexOf('function Assert-That', [StringComparison]::Ordinal)
$end = $runnerText.IndexOf('function Invoke-FullRuntime', [StringComparison]::Ordinal)
if ($start -lt 0 -or $end -le $start) { throw 'network recovery function boundaries are missing from runner.ps1' }
. ([scriptblock]::Create($runnerText.Substring($start, $end - $start)))

$script:runId = 'network-recovery-run'
$script:networkName = 'rct-network-recovery-exact'
$script:networkPlan = [ordered]@{
    subnet = '172.30.185.0/24'
    gateway = '172.30.185.1'
    dynamicIpRange = '172.30.185.128/25'
    edgeIp = '172.30.185.10'
    staticEdgeExcludedFromDynamicRange = $true
}
$script:sourceSha256 = if ($UseHeadRunner) { Get-Sha256Text -Text $runnerText } else { (Get-FileHash -LiteralPath $runnerPath -Algorithm SHA256).Hash }
$script:caseMode = $null
$script:fakeNetwork = $null
$script:dockerCommands = [System.Collections.Generic.List[object]]::new()

function Initialize-FakeCase {
    param([Parameter(Mandatory = $true)][string]$Mode)

    $script:caseMode = $Mode
    $script:fakeNetwork = [ordered]@{
        exists = $false
        id = 'abc123def4567890'
        name = $script:networkName
        owner = 'student-requests-gate'
        run = $script:runId
    }
    $script:ownedNetwork = $null
    $script:runtimeSecrets = @()
    $script:ownedContainers = [System.Collections.Generic.List[string]]::new()
    $script:keysDir = $null
    $script:artifactSnapshotRoot = $null
    $script:dockerCommands = [System.Collections.Generic.List[object]]::new()
    $script:report = [ordered]@{
        network = [ordered]@{}
        cleanup = [ordered]@{
            status = 'NOT_RUN'
            ownedContainers = @()
            removedContainers = @()
            verifiedAbsentContainers = @()
            ownedNetwork = $null
            networkRemoved = $false
            networkVerifiedAbsent = $false
            keysRemoved = $false
            artifactRoot = $null
            artifactRemoved = $false
            artifactVerifiedAbsent = $false
            errors = @()
        }
    }
    if ($Mode -eq 'foreign-owner') { $script:fakeNetwork.owner = 'other-owner' }
    if ($Mode -eq 'foreign-run') { $script:fakeNetwork.run = 'other-run' }
    if ($Mode -eq 'foreign-name') { $script:fakeNetwork.name = 'rct-network-recovery-foreign' }
}

function Add-FakeNetwork {
    $script:fakeNetwork.exists = $true
}

function Invoke-DockerSafe {
    param(
        [Parameter(Mandatory = $true)][string[]]$DockerArgs,
        [string]$Purpose = 'Docker command',
        [switch]$AllowFailure
    )

    $script:dockerCommands.Add([ordered]@{ args = @($DockerArgs); purpose = $Purpose })
    if ($DockerArgs[0] -eq 'network' -and $DockerArgs[1] -eq 'create') {
        switch ($script:caseMode) {
            'success' { Add-FakeNetwork; return [pscustomobject]@{ ExitCode = 0; Output = $script:fakeNetwork.id } }
            'exception' { Add-FakeNetwork; throw 'injected Docker client exception after daemon-side create' }
            'nonzero' { Add-FakeNetwork; return [pscustomobject]@{ ExitCode = 17; Output = 'daemon-side create completed; client reported failure' } }
            'missing-id' { Add-FakeNetwork; return [pscustomobject]@{ ExitCode = 0; Output = '' } }
            'malformed-id' { Add-FakeNetwork; return [pscustomobject]@{ ExitCode = 0; Output = 'not-a-network-id' } }
            'absent' { return [pscustomobject]@{ ExitCode = 17; Output = 'daemon rejected network create' } }
            'foreign-owner' { Add-FakeNetwork; return [pscustomobject]@{ ExitCode = 17; Output = 'ambiguous foreign owner' } }
            'foreign-run' { Add-FakeNetwork; return [pscustomobject]@{ ExitCode = 17; Output = 'ambiguous foreign run' } }
            'foreign-name' { Add-FakeNetwork; return [pscustomobject]@{ ExitCode = 17; Output = 'ambiguous foreign name' } }
            default { throw "unknown network recovery mode: $($script:caseMode)" }
        }
    }
    if ($DockerArgs[0] -eq 'network' -and $DockerArgs[1] -eq 'inspect') {
        if (-not $script:fakeNetwork.exists) { return [pscustomobject]@{ ExitCode = 1; Output = 'Error: No such network' } }
        $format = [string]$DockerArgs[3]
        if ($format.Contains('{{.Id}}')) {
            return [pscustomobject]@{ ExitCode = 0; Output = "$($script:fakeNetwork.id)|$($script:fakeNetwork.name)|$($script:fakeNetwork.owner)|$($script:fakeNetwork.run)" }
        }
        return [pscustomobject]@{ ExitCode = 0; Output = "$($script:fakeNetwork.owner)|$($script:fakeNetwork.run)" }
    }
    if ($DockerArgs[0] -eq 'network' -and $DockerArgs[1] -eq 'rm') {
        if (-not $script:fakeNetwork.exists) { return [pscustomobject]@{ ExitCode = 1; Output = 'Error: No such network' } }
        $script:fakeNetwork.exists = $false
        return [pscustomobject]@{ ExitCode = 0; Output = $script:fakeNetwork.id }
    }
    throw "unexpected Docker command in network recovery test: $($DockerArgs -join ' ')"
}

function Invoke-NetworkRecoveryCase {
    param(
        [Parameter(Mandatory = $true)][string]$Mode,
        [Parameter(Mandatory = $true)][bool]$ExpectedSuccess,
        [Parameter(Mandatory = $true)][bool]$ExpectedExistingAfterFailure
    )

    Initialize-FakeCase -Mode $Mode
    $callSucceeded = $false
    $callError = $null
    try {
        $result = New-RequestsOwnedNetwork -NetworkPlan $script:networkPlan
        $callSucceeded = $true
    } catch {
        $callError = $_.Exception.Message
    }

    if ($ExpectedSuccess -and -not $callSucceeded) { throw "$Mode did not recover/create the owned network: $callError" }
    if (-not $ExpectedSuccess -and $callSucceeded) { throw "$Mode unexpectedly adopted a rejected network" }
    if ($ExpectedSuccess) {
        if ([string]::IsNullOrWhiteSpace([string]$script:ownedNetwork)) { throw "$Mode did not register the network before returning" }
        if ([string]$script:ownedNetwork -cne [string]$script:fakeNetwork.id) { throw "$Mode registered the wrong network id" }
        if ($Mode -eq 'success') {
            if ($script:report.network.createRecovery.status -ne 'NOT_NEEDED') { throw 'normal create unexpectedly reported ambiguous recovery' }
        } else {
            if ($script:report.network.createRecovery.status -ne 'RECOVERED' -or [string]::IsNullOrWhiteSpace([string]$script:report.network.createRecovery.originalFailure)) {
                throw "$Mode did not retain the original create failure in recovery evidence"
            }
        }
        Remove-OwnedResources
        if ($script:fakeNetwork.exists) { throw "$Mode leaked the recovered network after cleanup" }
        if ($script:report.cleanup.status -ne 'PASS' -or -not $script:report.cleanup.networkVerifiedAbsent) {
            throw "$Mode did not verify cleanup of the recovered network"
        }
    } else {
        if ($null -ne $script:ownedNetwork) { throw "$Mode registered a network that failed ownership validation" }
        if ($script:fakeNetwork.exists -ne $ExpectedExistingAfterFailure) { throw "$Mode changed a foreign/absent network state" }
        Remove-OwnedResources
        if ($script:fakeNetwork.exists -ne $ExpectedExistingAfterFailure) { throw "$Mode cleanup touched a foreign/absent network" }
    }

    $recovery = if ($script:report.network.Contains('createRecovery')) { $script:report.network.createRecovery } else { $null }
    return [ordered]@{
        mode = $Mode
        status = 'PASS'
        callSucceeded = $callSucceeded
        callError = $callError
        registeredId = $script:ownedNetwork
        networkExistsAfterCase = [bool]$script:fakeNetwork.exists
        cleanupStatus = $script:report.cleanup.status
        networkVerifiedAbsent = [bool]$script:report.cleanup.networkVerifiedAbsent
        recovery = $recovery
        commands = @($script:dockerCommands)
    }
}

$cases = @(
    [ordered]@{ mode = 'success'; expectedSuccess = $true; expectedExisting = $false }
    [ordered]@{ mode = 'exception'; expectedSuccess = $true; expectedExisting = $false }
    [ordered]@{ mode = 'nonzero'; expectedSuccess = $true; expectedExisting = $false }
    [ordered]@{ mode = 'missing-id'; expectedSuccess = $true; expectedExisting = $false }
    [ordered]@{ mode = 'malformed-id'; expectedSuccess = $true; expectedExisting = $false }
    [ordered]@{ mode = 'foreign-owner'; expectedSuccess = $false; expectedExisting = $true }
    [ordered]@{ mode = 'foreign-run'; expectedSuccess = $false; expectedExisting = $true }
    [ordered]@{ mode = 'foreign-name'; expectedSuccess = $false; expectedExisting = $true }
    [ordered]@{ mode = 'absent'; expectedSuccess = $false; expectedExisting = $false }
)

$results = [System.Collections.Generic.List[object]]::new()
$failures = [System.Collections.Generic.List[string]]::new()
foreach ($case in $cases) {
    try {
        $results.Add((Invoke-NetworkRecoveryCase -Mode $case.mode -ExpectedSuccess $case.expectedSuccess -ExpectedExistingAfterFailure $case.expectedExisting))
    } catch {
        $failures.Add("$($case.mode): $($_.Exception.Message)")
    }
}

$status = if ($failures.Count -eq 0) { 'PASS' } else { 'FAIL' }
[ordered]@{
    status = $status
    mode = 'production-helper-pure-injection'
    runnerSource = $runnerSource
    runnerSha256 = $script:sourceSha256
    cases = @($results)
    failures = @($failures)
} | ConvertTo-Json -Depth 12 -Compress
if ($failures.Count -gt 0) { exit 1 }
exit 0
