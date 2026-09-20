#requires -Version 7.4
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$runnerPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'runner.ps1'
$packetPath = 'C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\orchestration-v2\REQUESTS-H68-PROBE-DIAGNOSIS.md'
$rulesPath = 'C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\orchestration-v2\RULES.md'
$syntheticProbePath = Join-Path $PSScriptRoot 'synthetic-probe.mjs'
$runnerSha256 = (Get-FileHash -LiteralPath $runnerPath -Algorithm SHA256).Hash.ToLowerInvariant()
$packetSha256 = (Get-FileHash -LiteralPath $packetPath -Algorithm SHA256).Hash.ToLowerInvariant()
$rulesSha256 = (Get-FileHash -LiteralPath $rulesPath -Algorithm SHA256).Hash.ToLowerInvariant()
$syntheticProbeSha256 = (Get-FileHash -LiteralPath $syntheticProbePath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($runnerSha256 -ne '9a2d703548110a142597b02196f5483f0f61d7329dfbfed313e38f1afc0b4b9d') { throw "runner hash changed: $runnerSha256" }
if ($packetSha256 -ne 'd6065c1f4f5166f50607f346c22bb8d296394e1efc58b80e684018b070c60864') { throw "H68 packet hash changed: $packetSha256" }
if ($rulesSha256 -ne 'b256a175274987da9710d804b3c050a5dbcb47d8448cc03644d74168b52a437a') { throw "RULES hash changed: $rulesSha256" }

$runnerTokens = $null
$runnerParseErrors = $null
$runnerAst = [System.Management.Automation.Language.Parser]::ParseFile($runnerPath, [ref]$runnerTokens, [ref]$runnerParseErrors)
if ($runnerParseErrors.Count -ne 0) { throw "runner parser errors: $($runnerParseErrors.Count)" }
foreach ($name in @('Assert-That', 'Protect-ReportText', 'Add-CommandEvidence', 'Invoke-ExternalSafe', 'Invoke-RequestsProbe')) {
    $definition = @($runnerAst.FindAll({
                param($node)
                $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name
            }, $true)) | Select-Object -First 1
    if ($null -eq $definition) { throw "runner function missing: $name" }
    Invoke-Expression $definition.Extent.Text
}

$script:runtimeSecrets = @('synthetic-secret')
$script:currentPhase = 'h68-offline-diagnosis'
$script:report = [ordered]@{ commands = [System.Collections.Generic.List[object]]::new() }
$script:probePath = $syntheticProbePath
$script:keysDir = $PSScriptRoot
$script:lastAuthLoginAt = $null
$script:authPacingRecords = [System.Collections.Generic.List[object]]::new()

$directArgs = @(
    $syntheticProbePath,
    '--origin', 'https://127.0.0.1:18514',
    '--ca', (Join-Path $PSScriptRoot 'synthetic-ca.pem'),
    '--login', 'student',
    '--password', 'password',
    '--health-only'
)
$direct = Invoke-ExternalSafe -FilePath 'node' -ArgumentList $directArgs -Purpose 'H68 synthetic JSON stdout and stderr warning' -TimeoutSeconds 30 -AllowFailure
$safeDirect = Protect-ReportText $direct.Output
$directLines = @($safeDirect -split '\r?\n')
$directNonEmptyLines = @($directLines | Where-Object { $_ -ne '' })
$directParsed = $true
$directParseError = $null
try { $null = $safeDirect | ConvertFrom-Json -Depth 12 } catch { $directParsed = $false; $directParseError = Protect-ReportText $_.Exception.Message }

$probeParseFailure = $false
$probeError = $null
try {
    $null = Invoke-RequestsProbe -ExtraArguments @('--health-only')
} catch {
    $probeParseFailure = $true
    $probeError = Protect-ReportText $_.Exception.Message
}

$healthShape = '{"schema":"rct.student-requests-probe.v1","status":"PASS","mode":"health-only","statusCode":200}'
$healthSafe = Protect-ReportText $healthShape
$healthParsed = $true
try { $null = $healthSafe | ConvertFrom-Json -Depth 12 } catch { $healthParsed = $false }

$sensitiveShape = '{"schema":"rct.student-requests-probe.v1","token":"synthetic-secret","status":"PASS"}'
$sensitiveSafe = Protect-ReportText $sensitiveShape
$sensitiveParsed = $true
$sensitiveParseError = $null
try { $null = $sensitiveSafe | ConvertFrom-Json -Depth 12 } catch { $sensitiveParsed = $false; $sensitiveParseError = Protect-ReportText $_.Exception.Message }

$helperMergeConfirmed = $direct.ExitCode -eq 0 -and
    $direct.Output.GetType().FullName -eq 'System.String' -and
    $directNonEmptyLines.Count -eq 2 -and
    $directNonEmptyLines[0] -match '"schema":"rct\.student-requests-probe\.v1"' -and
    $directNonEmptyLines[1] -match 'DEP0123' -and
    @($direct.PSObject.Properties.Name) -contains 'Output' -and
    @($direct.PSObject.Properties.Name) -notcontains 'Stdout' -and
    @($direct.PSObject.Properties.Name) -notcontains 'Stderr' -and
    -not $directParsed
$probePathConfirmed = $probeParseFailure -and $probeError -eq 'Requests probe did not return valid redacted JSON'
$healthRedactionSafe = $healthParsed
$quotedRedactionRiskConfirmed = -not $sensitiveParsed -and $sensitiveSafe -match '"token":<redacted>' -and $sensitiveSafe -notmatch 'synthetic-secret'
$overall = $helperMergeConfirmed -and $probePathConfirmed -and $healthRedactionSafe -and $quotedRedactionRiskConfirmed

$evidence = [ordered]@{
    schema = 'rct.student-requests-h68-probe-diagnosis.v1'
    packetPath = $packetPath
    packetSha256 = $packetSha256
    rulesSha256 = $rulesSha256
    runnerPath = $runnerPath
    runnerSha256 = $runnerSha256
    syntheticProbePath = $syntheticProbePath
    syntheticProbeSha256 = $syntheticProbeSha256
    actualH68Cause = 'UNPROVEN; root H68 raw probe output remains withheld'
    reproduction = [ordered]@{
        command = 'actual AST-extracted Invoke-RequestsProbe -> actual Invoke-ExternalSafe timed branch -> node synthetic-probe.mjs'
        childExitCode = $direct.ExitCode
        helperOutputType = $direct.Output.GetType().FullName
        helperOutputLineCount = $directLines.Count
        helperOutputFirstLine = $directLines[0]
        helperOutputWarningLine = $directNonEmptyLines[1]
        helperOutputLastLine = $directLines[$directLines.Count - 1]
        helperReturnedTypedStdout = (@($direct.PSObject.Properties.Name) -contains 'Stdout')
        helperReturnedTypedStderr = (@($direct.PSObject.Properties.Name) -contains 'Stderr')
        combinedConvertFromJsonSucceeded = $directParsed
        combinedParseError = $directParseError
        invokeRequestsProbeParseFailure = $probeParseFailure
        invokeRequestsProbeError = $probeError
    }
    redaction = [ordered]@{
        healthShapeParseable = $healthParsed
        healthShape = $healthSafe
        sensitiveShapeSafeText = $sensitiveSafe
        sensitiveShapeParseable = $sensitiveParsed
        quotedRedactionRiskConfirmed = $quotedRedactionRiskConfirmed
        sensitiveParseError = $sensitiveParseError
    }
    checks = [ordered]@{
        parser = 'PASS'
        actualInvokeExternalSafeMerge = if ($helperMergeConfirmed) { 'PASS' } else { 'FAIL' }
        actualInvokeRequestsProbeParseFailure = if ($probePathConfirmed) { 'PASS' } else { 'FAIL' }
        healthNoSensitiveKeys = if ($healthRedactionSafe) { 'PASS' } else { 'FAIL' }
        quotedRedactionRisk = if ($quotedRedactionRiskConfirmed) { 'PASS' } else { 'FAIL' }
        docker = 'NOT_RUN'
        gradle = 'NOT_RUN'
        network = 'NOT_RUN'
    }
    minimalProposal = 'Preserve Invoke-ExternalSafe stdout/stderr as separate typed fields; parse only redacted stdout as strict JSON and retain separately redacted stderr in command/evidence diagnostics. Keep exit-code checks and strict ConvertFrom-Json. If redacted JSON must remain parseable, replace sensitive values with quoted JSON-safe strings or redact structured objects before serialization; health-only shape has no sensitive keys and parses unchanged.'
}
$evidencePath = Join-Path $PSScriptRoot 'h68-probe-evidence.json'
[IO.File]::WriteAllText($evidencePath, ($evidence | ConvertTo-Json -Depth 16), [Text.UTF8Encoding]::new($false))
if (-not $overall) { throw "H68 offline reproduction checks failed; evidence: $evidencePath" }
Write-Output "H68 probe diagnosis: PASS (actual production helper merge and Invoke-RequestsProbe parse failure reproduced; redaction cases recorded; Docker/Gradle/network NOT_RUN; evidence $evidencePath)"
