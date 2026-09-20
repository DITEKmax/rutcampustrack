#requires -Version 7.4
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Synthetic-only source check for the Docker/IPAM boundary. It never starts
# Docker. The stub deliberately reports no operational address before
# `docker start`; the production function must defer inspect until then.
$runnerPath = Join-Path $PSScriptRoot 'runner.ps1'
$runnerText = Get-Content -LiteralPath $runnerPath -Raw
$tokens = $null
$errors = $null
$runnerAst = [System.Management.Automation.Language.Parser]::ParseFile($runnerPath, [ref]$tokens, [ref]$errors)
if ($errors.Count -ne 0) { throw "runner.ps1 parser errors: $($errors.Count)" }

function Get-FunctionDefinitionText {
    param([Parameter(Mandatory = $true)][string]$Name)
    $definition = @($runnerAst.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $Name }, $true)) | Select-Object -First 1
    if ($null -eq $definition) { throw "runner function missing: $Name" }
    return $definition.Extent.Text
}

foreach ($name in @('Assert-That', 'Convert-IPv4ToUInt32', 'Test-CidrOverlap', 'Get-DeterministicNetworkPlan', 'Assert-EdgeIpReservation', 'Register-OwnedContainer', 'Resolve-AmbiguousOwnedContainer', 'Start-OwnedContainer', 'Start-Edge')) {
    Invoke-Expression (Get-FunctionDefinitionText -Name $name)
}
$productionStartOwned = Get-FunctionDefinitionText -Name 'Start-OwnedContainer'

$script:edgeMode = 'normal'
$script:edgeOperational = $false
$script:preStartInspect = $false
$script:dockerCalls = [System.Collections.Generic.List[object]]::new()
$script:launcherMode = 'normal'
$script:ownedContainers = [System.Collections.Generic.List[string]]::new()
$script:ownedContainerNames = [System.Collections.Generic.List[string]]::new()
$script:services = [ordered]@{}
$script:images = [ordered]@{ nginx = 'synthetic-nginx@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' }
$script:runId = 'synthetic-edge'
$script:networkName = 'synthetic-network'
$script:report = [ordered]@{ assertions = [ordered]@{} }

function Invoke-DockerSafe {
    param(
        [Parameter(Mandatory = $true)][string[]]$DockerArgs,
        [string]$Purpose = 'Docker command',
        [switch]$AllowFailure
    )
    $script:dockerCalls.Add([pscustomobject]@{ args = @($DockerArgs); purpose = $Purpose })
    $verb = [string]$DockerArgs[0]
    if ($verb -eq 'create' -and $script:edgeMode -eq 'occupied') { throw 'synthetic Docker IPAM refused occupied edge address' }
    if ($verb -eq 'start') {
        if ($script:launcherMode -eq 'start-failure') { return [pscustomobject]@{ ExitCode = 23; Output = 'synthetic start failure' } }
        $script:edgeOperational = $true
        return [pscustomobject]@{ ExitCode = 0; Output = 'abcdef123456' }
    }
    if ($verb -eq 'container') {
        if ($script:launcherMode -eq 'create-recovery') { return [pscustomobject]@{ ExitCode = 0; Output = 'abcdef123456|/synthetic-recovered|student-requests-gate|synthetic-edge' } }
        if ($script:launcherMode -eq 'foreign-recovery') { return [pscustomobject]@{ ExitCode = 0; Output = 'abcdef123456|/synthetic-foreign|foreign-owner|foreign-run' } }
        return [pscustomobject]@{ ExitCode = 1; Output = 'No such container' }
    }
    if ($verb -eq 'inspect') {
        if (-not $script:edgeOperational) {
            $script:preStartInspect = $true
            return [pscustomobject]@{ ExitCode = 0; Output = '' }
        }
        return [pscustomobject]@{ ExitCode = 0; Output = '172.30.185.10' }
    }
    if ($verb -eq 'run' -or $verb -eq 'create') {
        if ($verb -eq 'create' -and ($script:launcherMode -eq 'create-recovery' -or $script:launcherMode -eq 'foreign-recovery')) {
            return [pscustomobject]@{ ExitCode = 23; Output = 'synthetic ambiguous create failure' }
        }
        $image = @($DockerArgs | Select-Object -Last 1)[0]
        if ([string]$image -eq 'missing:image') { throw 'synthetic missing image; pull is forbidden' }
        return [pscustomobject]@{ ExitCode = 0; Output = 'abcdef123456' }
    }
    return [pscustomobject]@{ ExitCode = 0; Output = '' }
}

function Invoke-OwnedDockerExec {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][string[]]$Command,
        [string]$Purpose = 'owned container command',
        [switch]$AllowFailure
    )
    return [pscustomobject]@{ ExitCode = 0; Output = '' }
}

function Invoke-RequestsProbe {
    param([string[]]$ExtraArguments = @())
    return [pscustomobject]@{ status = 'PASS'; mode = 'health-only' }
}

function Start-OwnedContainer {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Image,
        [string[]]$Aliases = @(),
        [string[]]$Publish = @(),
        [hashtable]$Environment = @{},
        [hashtable[]]$Mounts = @{},
        [string]$Ip = $null,
        [string[]]$Command = @(),
        [switch]$CreateOnly
    )
    if ($script:edgeMode -eq 'occupied') { throw 'synthetic Docker IPAM refused occupied edge address' }
    $script:ownedContainers.Add('abcdef123456')
    return [pscustomobject]@{ Id = 'abcdef123456'; Name = $Name }
}

$plan = Get-DeterministicNetworkPlan -Subnet '172.30.185.0/24'
if ($plan.gateway -ne '172.30.185.1' -or $plan.edgeIp -ne '172.30.185.10' -or $plan.dynamicIpRange -ne '172.30.185.128/25' -or -not $plan.staticEdgeExcludedFromDynamicRange) {
    throw 'deterministic subnet plan did not produce the expected gateway, edge IP and dynamic range'
}
$secondPlan = Get-DeterministicNetworkPlan -Subnet '172.30.186.0/24'
if ($secondPlan.gateway -ne '172.30.186.1' -or $secondPlan.edgeIp -ne '172.30.186.10' -or $secondPlan.dynamicIpRange -ne '172.30.186.128/25') { throw 'second disposable /24 did not receive an aligned edge IPAM plan' }
$invalidPlanRejected = $false
try { Get-DeterministicNetworkPlan -Subnet '172.30.185.0/23' | Out-Null } catch { $invalidPlanRejected = $true }
if (-not $invalidPlanRejected) { throw 'non-/24 subnet was accepted for the fixed edge allocation plan' }
$unalignedPlanRejected = $false
try { Get-DeterministicNetworkPlan -Subnet '172.30.185.1/24' | Out-Null } catch { $unalignedPlanRejected = $true }
if (-not $unalignedPlanRejected) { throw 'unaligned /24 subnet was accepted for the fixed edge allocation plan' }

$edgeConfig = @{ NginxIp = $plan.edgeIp; ConfigDir = 'C:\synthetic'; CertPath = 'C:\synthetic\server.crt'; KeyPath = 'C:\synthetic\server.key'; PwaDistPath = 'C:\synthetic\dist' }
$null = Start-Edge -Config $edgeConfig -ReserveOnly
if ($script:preStartInspect) { throw 'production edge reservation inspected an operational IP before docker start' }
if ($script:edgeOperational) { throw 'create-only edge preparation unexpectedly started the container' }
if ($script:ownedContainers.Count -ne 1 -or $script:report.assertions.edgeReservation.operationalAddressVerified -ne $false) { throw 'create-only edge preparation did not retain the created endpoint without claiming an IP' }

$startIndex = @($script:dockerCalls | Where-Object { $_.args[0] -eq 'start' }).Count
$inspectBeforeStart = $script:preStartInspect
$null = Start-Edge -Config $edgeConfig -StartReserved
$startCall = @($script:dockerCalls | Where-Object { $_.args[0] -eq 'start' }) | Select-Object -First 1
$inspectCall = @($script:dockerCalls | Where-Object { $_.args[0] -eq 'inspect' }) | Select-Object -First 1
if ($inspectBeforeStart -or $startIndex -ne 0 -or $null -eq $startCall -or $null -eq $inspectCall) { throw 'operational edge allocation did not start before inspect' }
if ($script:report.assertions.edgeReservation.operationalAddressVerified -ne $true -or $script:report.assertions.edgeReservation.actual -ne $plan.edgeIp) { throw 'post-start edge IP inspection did not verify the exact static address' }

$script:edgeMode = 'occupied'
$script:services = [ordered]@{}
$script:ownedContainers.Clear()
$script:dockerCalls.Clear()
$script:edgeOperational = $false
$occupiedRejected = $false
try { Start-Edge -Config $edgeConfig -ReserveOnly | Out-Null } catch { $occupiedRejected = $true }
if (-not $occupiedRejected -or $script:ownedContainers.Count -ne 0) { throw 'occupied edge allocation did not fail closed without an owned-resource leak' }

# Verify the actual network create callsite uses Docker's supported IPAM
# allocation pool. The edge address is outside that pool and does not use
# --aux-address, which would reserve the same address against static --ip.
if ($runnerText -notmatch 'Get-DeterministicNetworkPlan -Subnet \$script:subnet') { throw 'runtime does not validate the deterministic edge IPAM plan' }
if ($runnerText -notmatch ("'--gateway', " + '\[string\]\$NetworkPlan\.gateway')) { throw 'network create does not set the validated gateway explicitly' }
if ($runnerText -notmatch ("'--ip-range', " + '\[string\]\$NetworkPlan\.dynamicIpRange')) { throw 'network create does not set the edge-excluding dynamic IP range' }
if ($runnerText -match '--aux-address') { throw 'edge IP must not be reserved through --aux-address' }
$reserveCall = $runnerText.IndexOf('Start-Edge -Config $edgeConfig -ReserveOnly', [StringComparison]::Ordinal)
$infraCall = $runnerText.IndexOf('Start-Infrastructure -Secrets $secrets', [StringComparison]::Ordinal)
$backendCall = $runnerText.IndexOf('Start-BackendServices -Secrets $secrets -Config $edgeConfig', [StringComparison]::Ordinal)
$startReservedCall = $runnerText.IndexOf('Start-Edge -Config $edgeConfig -StartReserved', [StringComparison]::Ordinal)
if ($reserveCall -lt 0 -or $infraCall -le $reserveCall -or $backendCall -le $reserveCall -or $startReservedCall -le $backendCall) { throw 'edge endpoint preparation/start ordering is not before and after dynamic backend allocation as intended' }
if ($runnerText -notmatch 'GATEWAY_TRUSTED_PROXY_ADDRESSES = \$Config\.NginxIp') { throw 'Gateway trusted peer is not tied to the exact edge address' }

# Exercise the production launcher itself. Both owned verbs carry an explicit
# pull=never policy, and a missing image fails at the same argv boundary rather
# than triggering Docker's implicit pull behavior.
$script:edgeMode = 'normal'
$script:ownedContainers.Clear()
$script:dockerCalls.Clear()
Invoke-Expression $productionStartOwned
$null = Start-OwnedContainer -Name 'synthetic-create' -Image 'synthetic:image' -CreateOnly
$null = Start-OwnedContainer -Name 'synthetic-run' -Image 'synthetic:image'
$createCalls = @($script:dockerCalls | Where-Object { $_.args[0] -eq 'create' })
$startCalls = @($script:dockerCalls | Where-Object { $_.args[0] -eq 'start' })
if ($createCalls.Count -ne 2 -or $startCalls.Count -ne 1 -or @($createCalls | Where-Object { $_.args -notcontains '--pull=never' }).Count -ne 0) { throw 'owned create/start argv did not register before start with --pull=never' }
$script:launcherMode = 'start-failure'
$script:ownedContainers.Clear()
$script:ownedContainerNames.Clear()
$startRejected = $false
try { Start-OwnedContainer -Name 'synthetic-start-failure' -Image 'synthetic:image' -Entrypoint '/synthetic/missing-executable' | Out-Null } catch { $startRejected = $true }
$startFailureCreate = @($script:dockerCalls | Where-Object { $_.args[0] -eq 'create' -and $_.args -contains 'synthetic-start-failure' }) | Select-Object -Last 1
if (-not $startRejected -or $script:ownedContainers.Count -ne 1 -or $script:ownedContainers[0] -ne 'abcdef123456' -or $null -eq $startFailureCreate -or $startFailureCreate.args -notcontains '--entrypoint' -or $startFailureCreate.args -notcontains '/synthetic/missing-executable') { throw 'invalid-entrypoint start failure was not retained in the owned cleanup ledger' }
$script:launcherMode = 'create-recovery'
$script:ownedContainers.Clear()
$script:ownedContainerNames.Clear()
$recovered = Start-OwnedContainer -Name 'synthetic-recovered' -Image 'synthetic:image' -CreateOnly
if ($recovered.Id -ne 'abcdef123456' -or $script:ownedContainers.Count -ne 1) { throw 'ambiguous create recovery did not register the exact owned container' }
$script:launcherMode = 'foreign-recovery'
$script:ownedContainers.Clear()
$script:ownedContainerNames.Clear()
$foreignRejected = $false
try { Start-OwnedContainer -Name 'synthetic-foreign' -Image 'synthetic:image' -CreateOnly | Out-Null } catch { $foreignRejected = $true }
if (-not $foreignRejected -or $script:ownedContainers.Count -ne 0 -or @($script:dockerCalls | Where-Object { $_.args[0] -eq 'rm' }).Count -ne 0) { throw 'foreign ambiguous container was adopted or deleted' }
$script:launcherMode = 'normal'
$script:dockerCalls.Clear()
$missingRejected = $false
try { Start-OwnedContainer -Name 'synthetic-missing' -Image 'missing:image' | Out-Null } catch { $missingRejected = $true }
$missingCall = @($script:dockerCalls | Where-Object { $_.args[0] -eq 'create' }) | Select-Object -First 1
if (-not $missingRejected -or $null -eq $missingCall -or $missingCall.args -notcontains '--pull=never') { throw 'missing owned image did not fail closed with --pull=never' }

Write-Output 'R8 edge/runtime launch check: PASS (IPAM plan .128/25 excludes static .10 with gateway .1; aligned second /24 and invalid-boundary cases; create-only path makes no operational-IP claim; post-start inspect verifies .10; occupied edge fails closed; create registers before start and start-failure/recovery ownership cases fail closed; all owned create argv include --pull=never)'
