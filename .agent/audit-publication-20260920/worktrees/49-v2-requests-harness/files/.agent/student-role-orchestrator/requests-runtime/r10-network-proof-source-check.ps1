#requires -Version 7.4
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-That {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

$runnerPath = Join-Path $PSScriptRoot 'runner.ps1'
$proofPath = Join-Path $PSScriptRoot 'r10-docker-network-proof.ps1'
$runner = Get-Content -LiteralPath $runnerPath -Raw
$proof = Get-Content -LiteralPath $proofPath -Raw
$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($runnerPath, [ref]$tokens, [ref]$errors) | Out-Null
Assert-That ($errors.Count -eq 0) 'runner.ps1 has PowerShell parser errors'
$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($proofPath, [ref]$tokens, [ref]$errors) | Out-Null
Assert-That ($errors.Count -eq 0) 'r10 proof wrapper has PowerShell parser errors'

Assert-That ($runner -match 'function New-RequestsOwnedNetwork') 'production network creation helper is missing'
Assert-That ($runner -match 'New-RequestsOwnedNetwork -NetworkPlan \$networkPlan') 'full runtime does not call the shared production network helper'
Assert-That ($runner -match "'network', 'create', '--driver', 'bridge'") 'shared network helper does not emit docker network create'
Assert-That ($runner -match '--gateway.*\[string\]\$NetworkPlan\.gateway') 'shared network helper does not pass the validated gateway'
Assert-That ($runner -match '--ip-range.*\[string\]\$NetworkPlan\.dynamicIpRange') 'shared network helper does not pass the edge-excluding IP range'
Assert-That ($runner -match 'function Invoke-RequestsNetworkProof') 'runner proof branch is missing'
Assert-That ($runner -match '\[switch\]\$NetworkProof') 'runner proof switch is missing'
Assert-That ($runner -match 'Invoke-RequestsNetworkProof -Subnet \$NetworkProofSubnet') 'runner proof switch does not invoke the proof function'
Assert-That ($runner -match 'Get-DockerNetworkSubnets') 'proof does not inspect existing Docker subnets before create'
Assert-That ($runner -match 'missing-image proof reference is absent') 'proof does not verify the missing image reference before launch'
Assert-That ($runner -match 'missing-image create left no container') 'proof does not verify missing-image create left no container'
Assert-That ($proof -match "runner\.ps1") 'proof wrapper does not resolve runner.ps1'
Assert-That ($proof -match "'-NetworkProof'") 'proof wrapper does not select the runner proof branch'
Assert-That ($proof -notmatch '(?im)^\s*docker\b') 'proof wrapper copied a Docker command instead of invoking production logic'
Assert-That ($runner -match 'maxNetworks = 1') 'proof resource bound is missing'
Assert-That ($runner -match 'maxContainersExpected = 3') 'proof expected container bound is missing'
Assert-That ($runner -match 'maxContainersWorstCase = 4') 'proof worst-case container bound is missing'
Assert-That ($runner -match '\[string\]\$Entrypoint') 'owned launcher has no controlled entrypoint parameter for start-failure proof'
Assert-That ($runner -match "'--entrypoint'") 'owned launcher does not emit the controlled entrypoint argument'
Assert-That ($runner -match 'invalid-exec') 'proof does not exercise an invalid-executable start failure'
Assert-That ($runner -match 'invalidExecStartFailure = \$true') 'proof does not record the invalid-executable start failure'
Assert-That ($runner -match 'invalidExecContainerRegistered = \$true') 'proof does not record registration before invalid-executable start failure'
Assert-That ($runner -match 'Remove-OwnedResources') 'proof does not use owned cleanup'
Assert-That ($runner -match '\$script:report\.runId = \$script:runId') 'proof report runId is not initialized'
Assert-That ($runner -match '\$script:report\.environment\.runtimeMode = ''network-proof''') 'proof report runtimeMode is not network-proof'
Assert-That ($runner -match 'sourceAnchor = .requests-runtime/runner\.ps1 -NetworkProof production branch') 'proof report source anchor is missing'
Assert-That ($proof -match 'r11-docker-network-proof\.json') 'new proof wrapper still defaults to the historical H32 report path'
$launcher = [regex]::Match($runner, '(?s)function Start-OwnedContainer\s*\{.*?\n\}')
Assert-That $launcher.Success 'Start-OwnedContainer function is missing'
Assert-That ($launcher.Value -match "docker|Invoke-DockerSafe") 'Start-OwnedContainer does not invoke the actual Docker helper'
Assert-That ($launcher.Value.IndexOf('Register-OwnedContainer', [StringComparison]::Ordinal) -lt $launcher.Value.IndexOf("@('start'", [StringComparison]::Ordinal)) 'Start-OwnedContainer does not register before start'

Write-Output 'R10 network proof source check: PASS (wrapper invokes runner production network helper and launcher; no copied Docker command; bounds and owned cleanup are declared)'
