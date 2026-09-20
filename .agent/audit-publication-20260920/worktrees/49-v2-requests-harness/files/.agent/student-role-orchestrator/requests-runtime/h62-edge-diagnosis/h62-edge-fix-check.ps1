#requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$UnionRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$runnerPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'runner.ps1'
$runnerText = Get-Content -LiteralPath $runnerPath -Raw -Encoding UTF8
$tokens = $null
$parseErrors = $null
$runnerAst = [System.Management.Automation.Language.Parser]::ParseFile($runnerPath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -ne 0) { throw "runner.ps1 parser errors: $($parseErrors.Count)" }

function Get-FunctionDefinitionText {
    param([Parameter(Mandatory = $true)][string]$Name)
    $definition = @($runnerAst.FindAll({
                param($node)
                $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $Name
            }, $true)) | Select-Object -First 1
    if ($null -eq $definition) { throw "runner function missing: $Name" }
    return $definition.Extent.Text
}

foreach ($name in @('Assert-That', 'Protect-ReportText', 'Get-Sha256Hex', 'Convert-EdgeToRuntimeConfig', 'New-LocalTlsCertificate', 'New-RuntimeConfig', 'Limit-EdgeDiagnosticText', 'Capture-EdgeFailureDiagnostics', 'Get-EdgeFailureContainerId', 'Add-PhaseDiagnostic', 'Test-ReportHasFailure')) {
    Invoke-Expression (Get-FunctionDefinitionText -Name $name)
}

$rulesPath = 'C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\orchestration-v2\RULES.md'
$packetPath = 'C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\orchestration-v2\REQUESTS-H62-EDGE-FIX.md'
$rulesSha256 = Get-Sha256Hex -Path $rulesPath
$packetSha256 = Get-Sha256Hex -Path $packetPath
if ($rulesSha256 -ne 'b256a175274987da9710d804b3c050a5dbcb47d8448cc03644d74168b52a437a') { throw "RULES.md hash changed: $rulesSha256" }
if ($packetSha256 -ne '25f8632b8a9debc63e8d2ee1bb79a4c2ceff57dec5214938c28ec8601da1418c') { throw "H62 edge fix packet hash changed: $packetSha256" }
$preservedFailureEvidencePath = Join-Path $PSScriptRoot 'h62-edge-fix-evidence.outer-failure.json'
if (-not (Test-Path -LiteralPath $preservedFailureEvidencePath -PathType Leaf)) { throw 'preserved pre-correction failure evidence is missing' }
$preservedFailureEvidence = Get-Content -LiteralPath $preservedFailureEvidencePath -Raw | ConvertFrom-Json
$preservedFailureEvidenceSha256 = Get-Sha256Hex -Path $preservedFailureEvidencePath
if ($preservedFailureEvidence.runnerSha256 -ne '13aafbf6f6f437ab75910abf2feae79d3d2e710443efe4f77c6816986445b99a') { throw "preserved pre-correction runner hash changed: $($preservedFailureEvidence.runnerSha256)" }

$script:runtimeSecrets = @('synthetic-secret')
$script:report = [ordered]@{ source = [ordered]@{} }
$resolvedUnionRoot = (Resolve-Path -LiteralPath $UnionRoot -ErrorAction Stop).Path
$nginxPath = Join-Path $resolvedUnionRoot 'tests\e2e\infra\nginx\nginx.conf'
$defaultPath = Join-Path $resolvedUnionRoot 'tests\e2e\infra\nginx\default.conf'
$nginxText = [IO.File]::ReadAllText($nginxPath, [Text.UTF8Encoding]::new($false, $true))
$defaultText = [IO.File]::ReadAllText($defaultPath, [Text.UTF8Encoding]::new($false, $true))

$converted = Convert-EdgeToRuntimeConfig -NginxPath $nginxPath -DefaultPath $defaultPath -NginxContent $nginxText -DefaultContent $defaultText
$runtimeDefault = [string]$converted.Default
$runtimeNginx = [string]$converted.Nginx

if ($runtimeNginx -notmatch '(?m)^\s*client_max_body_size\s+2m;') { throw 'global 2m body cap was not retained' }
if ($runtimeNginx -notmatch '(?s)map\s+\$uri\s+\$student_requests_cache_control.*?student/requests.*?no-store') { throw 'Requests no-store map was not retained' }
foreach ($pattern in @(
        'proxy_pass http://api-gateway:8080;',
        'proxy_set_header Host \$host;',
        'proxy_set_header X-Real-IP \$remote_addr;',
        'proxy_set_header X-Forwarded-For \$remote_addr;',
        'proxy_set_header X-Forwarded-Proto \$scheme;',
        'proxy_set_header X-Forwarded-Host \$host;',
        'proxy_set_header X-Forwarded-Port \$server_port;',
        'proxy_set_header Forwarded "";'
    )) {
    if ($runtimeDefault -notmatch $pattern) { throw "retained API forwarding guard missing: $pattern" }
}
if ($runtimeDefault -notmatch '(?s)location\s+/api/ws/.*?proxy_read_timeout\s+3600s;.*?proxy_buffering\s+off;') { throw 'WebSocket buffering/timeout guard was not retained' }
if ($runtimeDefault -notmatch '(?s)location\s*=\s*/api/v1/student/requests/excuse\s*\{.*?client_max_body_size\s+24m;') { throw 'Requests excuse 24m route cap was not retained' }
if ($runtimeDefault -notmatch '(?s)location\s+/presentation/\s*\{\s*return\s+404;\s*\}') { throw 'presentation was not converted to explicit local 404' }
if ($runtimeDefault -notmatch '(?s)location\s+/mini-app/\s*\{\s*return\s+404;\s*\}') { throw 'mini-app was not converted to explicit local 404' }
if ($runtimeDefault -notmatch '(?s)location\s+/app/\s*\{.*?rewrite\s+\^/app/\(\.\*\)\$\s+/\$1\s+break;.*?root\s+/usr/share/nginx/html;.*?try_files\s+\$uri\s+\$uri/\s+/index\.html;') { throw 'app was not converted to local mounted-PWA static handling' }
if ($runtimeDefault -notmatch '(?s)location\s*=\s*/\s*\{.*?root\s+/usr/share/nginx/html;.*?try_files\s+/index\.html\s+=200;') { throw 'root PWA static location was not retained' }
foreach ($missingUpstream in @('landing-nginx', 'pwa-nginx', 'mini-app-nginx', 'web-panel-nginx')) {
    if ($runtimeDefault -match [regex]::Escape($missingUpstream)) { throw "runtime config still references unavailable UI upstream: $missingUpstream" }
}
if ($runnerText -notmatch 'GATEWAY_TRUSTED_PROXY_ADDRESSES = \$Config\.NginxIp') { throw 'trusted Gateway peer is no longer tied to the edge IP' }

$unexpectedSource = $defaultText.Replace('landing-nginx', 'landing-unexpected')
$unexpectedRejected = $false
try {
    Convert-EdgeToRuntimeConfig -NginxPath $nginxPath -DefaultPath $defaultPath -NginxContent $nginxText -DefaultContent $unexpectedSource | Out-Null
} catch {
    $unexpectedRejected = $true
}
if (-not $unexpectedRejected) { throw 'converter accepted an unexpected presentation source block' }

$runtimeWriter = Get-FunctionDefinitionText -Name 'New-RuntimeConfig'
if (@([regex]::Matches($runtimeWriter, '\[Text\.UTF8Encoding\]::new\(\$false\)')).Count -ne 2) { throw 'production config writers do not explicitly use UTF8Encoding(false) twice' }
if ($runtimeWriter -match '\[Text\.Encoding\]::UTF8') { throw 'production config writer retained the BOM-producing Encoding.UTF8 overload' }
$diagnosticFunction = Get-FunctionDefinitionText -Name 'Capture-EdgeFailureDiagnostics'
foreach ($requiredDiagnosticPattern in @("'inspect', '--format', '{{json .State}}'", "'logs', '--tail', '80', '--timestamps'")) {
    if ($diagnosticFunction -notmatch [regex]::Escape($requiredDiagnosticPattern)) { throw "bounded edge diagnostic guard missing: $requiredDiagnosticPattern" }
}
if ($runnerText -notmatch '(?s)function Limit-EdgeDiagnosticText.*?Protect-ReportText') { throw 'bounded edge diagnostics are not passed through Protect-ReportText' }
if ($diagnosticFunction -match "'env'") { throw 'edge diagnostics must not inspect environment variables' }
if ($runnerText -notmatch 'Capture-EdgeFailureDiagnostics -ContainerId \$edgeFailureContainerId -FailureMessage \$originalFailure') { throw 'edge failure capture is not wired before finally cleanup' }
if ($runnerText -notmatch '(?s)\$script:report\.failure = \[ordered\]@\{ message = \$originalFailure \}.*?try \{\s*\$edgeFailureContainerId') { throw 'original edge failure is not assigned before fallible diagnostics selection' }
if ($runnerText -notmatch 'function Test-ReportHasFailure' -or $runnerText -notmatch '(?s)function Test-ReportHasFailure.*?\[System\.Collections\.IDictionary\].*?\.Keys.*?failure') { throw 'cleanup failure handling lacks dictionary-safe failure-key detection' }
if ($runnerText -notmatch 'Remove-OwnedResources') { throw 'owned cleanup call disappeared' }

# Exercise the actual production config and certificate helpers without Docker.
# The fixture is intentionally retained for the root-owned H67 nginx -t step.
$fixtureRoot = Join-Path $PSScriptRoot 'fixture'
$fixtureKeys = Join-Path $fixtureRoot 'keys'
$fixturePwa = Join-Path $fixtureRoot 'artifacts\pwa'
New-Item -ItemType Directory -Path $fixtureKeys,$fixturePwa -Force | Out-Null
$script:runDir = $fixtureRoot
$script:keysDir = $fixtureKeys
$script:artifactSnapshot = [ordered]@{
    PwaDist = [ordered]@{ path = $fixturePwa; fileCount = 0; manifestSha256 = 'pure-check'; files = @() }
}
$script:validatedEdgeSources = [ordered]@{
    nginxPath = $nginxPath
    defaultPath = $defaultPath
    nginxText = $nginxText
    defaultText = $defaultText
}
$script:report = [ordered]@{ source = [ordered]@{} }
$runtimeConfig = New-RuntimeConfig
$fixtureNginx = Join-Path $fixtureRoot 'nginx\nginx.conf'
$fixtureDefault = Join-Path $fixtureRoot 'nginx\default.conf'
$fixtureCertificate = Join-Path $fixtureKeys 'server.crt'
$fixtureKey = Join-Path $fixtureKeys 'server.key'
foreach ($fixturePath in @($fixtureNginx, $fixtureDefault, $fixtureCertificate, $fixtureKey)) {
    if (-not (Test-Path -LiteralPath $fixturePath -PathType Leaf)) { throw "production-generated fixture is missing: $fixturePath" }
}
foreach ($configPath in @($fixtureNginx, $fixtureDefault)) {
    $bytes = [IO.File]::ReadAllBytes($configPath)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) { throw "production-generated config has a UTF-8 BOM: $configPath" }
}
if ((Get-Item -LiteralPath $fixtureCertificate).Length -le 0 -or (Get-Item -LiteralPath $fixtureKey).Length -le 0) { throw 'ephemeral TLS fixture is empty' }

# The diagnostic helper must keep an original redacted failure even when both
# Docker evidence calls fail. This stub records the bounded argv only.
$script:diagnosticCalls = [System.Collections.Generic.List[object]]::new()
function Invoke-DockerSafe {
    param(
        [Parameter(Mandatory = $true)][string[]]$DockerArgs,
        [string]$Purpose = 'Docker command',
        [switch]$AllowFailure
    )
    $script:diagnosticCalls.Add([pscustomobject]@{ args = @($DockerArgs); purpose = $Purpose })
    throw "synthetic diagnostic transport failure: $Purpose synthetic-secret"
}
$script:report = [ordered]@{}
Capture-EdgeFailureDiagnostics -ContainerId 'abcdef123456' -FailureMessage 'original edge failure synthetic-secret'
if ($script:report.edgeFailureDiagnostics.failure -notmatch '<redacted>') { throw 'diagnostic failure did not pass the original message through Protect-ReportText' }
if (@($script:diagnosticCalls | Where-Object { $_.args -contains 'env' }).Count -ne 0) { throw 'diagnostic failure attempted an environment dump' }
if ($script:report.edgeFailureDiagnostics.status -ne 'CAPTURED') { throw 'diagnostic failure prevented bounded evidence from being recorded' }

# Execute the production outer catch/finally path extracted from the parsed
# runner AST. The failure is injected only at the existing diagnostics seam;
# cleanup is likewise injected at the existing owned-cleanup seam. This keeps
# the control-flow under test production-authored while avoiding Docker.
$outerTry = @($runnerAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.TryStatementAst] -and
            $null -ne $node.Finally -and
            $node.CatchClauses.Count -eq 1 -and
            $node.Extent.Text -match 'Capture-EdgeFailureDiagnostics'
        }, $true)) | Select-Object -First 1
if ($null -eq $outerTry) { throw 'production outer catch/finally AST was not found' }
$outerCatchText = $outerTry.CatchClauses[0].Body.Extent.Text
$outerFinallyText = $outerTry.Finally.Extent.Text
if ($outerCatchText -notmatch 'Capture-EdgeFailureDiagnostics' -or $outerCatchText -notmatch '\$script:report\.failure') { throw 'outer catch extraction missed diagnostic/original-failure handling' }
if ($outerFinallyText -notmatch 'Remove-OwnedResources' -or $outerFinallyText -notmatch 'cleanup') { throw 'outer finally extraction missed owned cleanup handling' }

function Invoke-ExtractedOuterFailureCase {
    param(
        [Parameter(Mandatory = $true)][string]$CaseName,
        [bool]$ThrowDiagnostic,
        [bool]$ThrowCleanup
    )
    $script:outerEvents = [System.Collections.Generic.List[string]]::new()
    $script:outerDiagnosticCalls = 0
    $script:outerCleanupCalls = 0
    $script:outerReportWrites = 0
    $script:outerCapturedFailure = $null
    $script:outerInvocationError = $null
    $script:outerThrowDiagnostic = $ThrowDiagnostic
    $script:outerThrowCleanup = $ThrowCleanup
    $script:processExitCode = 0
    $script:currentPhase = 'edge'
    $script:runDir = $fixtureRoot
    $script:taskRoot = $fixtureRoot
    $script:preflightReportPath = Join-Path $fixtureRoot 'preflight.json'
    $script:artifactSnapshotRoot = $fixtureRoot
    $script:ownedNetwork = $null
    $script:ownedContainers = [System.Collections.Generic.List[string]]::new()
    $script:services = [ordered]@{ edge = [pscustomobject]@{ Id = 'h62-fixture-edge' } }
    $script:ValidateOnly = $false
    $ValidateOnly = $false
    $script:report = [ordered]@{
        status = 'RUNNING'
        runtime = 'RUNNING'
        phases = [System.Collections.Generic.List[object]]::new()
        cleanup = [ordered]@{ status = 'PENDING' }
    }

    function Capture-EdgeFailureDiagnostics {
        param(
            [Parameter(Mandatory = $true)][string]$ContainerId,
            [Parameter(Mandatory = $true)][string]$FailureMessage
        )
        $script:outerEvents.Add('diagnostics')
        $script:outerDiagnosticCalls++
        $script:outerCapturedFailure = $FailureMessage
        if ($script:outerThrowDiagnostic) { throw 'injected diagnostics failure synthetic-secret' }
        $script:report.edgeFailureDiagnostics = [ordered]@{
            schema = 'rct.edge-failure.v1'
            status = 'CAPTURED'
            failure = $FailureMessage
        }
    }

    function Remove-OwnedResources {
        $script:outerEvents.Add('cleanup')
        $script:outerCleanupCalls++
        if ($script:outerThrowCleanup) { throw 'injected cleanup failure synthetic-secret' }
        $script:report.cleanup.status = 'PASS'
    }

    function Write-ReportFile {
        param([Parameter(Mandatory = $true)][string]$Path)
        $script:outerReportWrites++
    }

    $outerHarness = 'try { throw [System.Exception]::new(''original edge failure synthetic-secret'') } catch ' + $outerCatchText + ' finally ' + $outerFinallyText
    try {
        $null = Invoke-Expression $outerHarness
    } catch {
        $script:outerInvocationError = Protect-ReportText $_.Exception.Message
    }
    $failurePresent = if ($script:report -is [System.Collections.IDictionary]) {
        @($script:report.Keys) -contains 'failure'
    } else {
        $script:report.PSObject.Properties.Name -contains 'failure'
    }
    $failureMessage = if ($failurePresent) { [string]$script:report.failure.message } else { $null }
    $cleanupFailurePhase = @($script:report.phases | Where-Object { $_.name -eq 'cleanup' -and $_.status -eq 'FAIL' }).Count -gt 0
    $orderedEvents = @($script:outerEvents.ToArray())
    [pscustomobject]@{
        name = $CaseName
        diagnosticCalls = $script:outerDiagnosticCalls
        cleanupCalls = $script:outerCleanupCalls
        reportWrites = $script:outerReportWrites
        events = $orderedEvents
        diagnosticsBeforeCleanup = ($orderedEvents.Count -ge 2 -and $orderedEvents[0] -eq 'diagnostics' -and $orderedEvents[1] -eq 'cleanup')
        processExitCode = $script:processExitCode
        failurePresent = $failurePresent
        failureMessage = $failureMessage
        failureRedacted = ($null -ne $failureMessage -and $failureMessage -match '<redacted>')
        cleanupStatus = [string]$script:report.cleanup.status
        cleanupFailureEvidence = $cleanupFailurePhase
        invocationError = $script:outerInvocationError
    }
}

$diagnosticFailureCase = Invoke-ExtractedOuterFailureCase -CaseName 'diagnostics-failure' -ThrowDiagnostic:$true -ThrowCleanup:$false
$cleanupFailureCase = Invoke-ExtractedOuterFailureCase -CaseName 'cleanup-failure' -ThrowDiagnostic:$false -ThrowCleanup:$true
$diagnosticFailurePathPass = $diagnosticFailureCase.diagnosticCalls -eq 1 -and
    $diagnosticFailureCase.cleanupCalls -eq 1 -and
    $diagnosticFailureCase.diagnosticsBeforeCleanup -and
    $diagnosticFailureCase.processExitCode -eq 1 -and
    $diagnosticFailureCase.failurePresent -and
    $diagnosticFailureCase.failureRedacted
$cleanupFailurePathPass = $cleanupFailureCase.diagnosticCalls -eq 1 -and
    $cleanupFailureCase.cleanupCalls -eq 1 -and
    $cleanupFailureCase.diagnosticsBeforeCleanup -and
    $cleanupFailureCase.processExitCode -eq 1 -and
    $cleanupFailureCase.failurePresent -and
    $cleanupFailureCase.failureRedacted -and
    $cleanupFailureCase.cleanupStatus -eq 'FAIL' -and
    $cleanupFailureCase.cleanupFailureEvidence -and
    $cleanupFailureCase.failureMessage -match 'original edge failure' -and
    $cleanupFailureCase.failureMessage -match 'injected cleanup failure'
$outerFailurePathPass = $diagnosticFailurePathPass -and $cleanupFailurePathPass
$outerFailurePathReview = [ordered]@{
    status = if ($outerFailurePathPass) { 'PASS' } else { 'FAIL' }
    astExtracted = $true
    outerTryStartOffset = $outerTry.Extent.StartOffset
    outerTryEndOffset = $outerTry.Extent.EndOffset
    diagnosticFailure = $diagnosticFailureCase
    cleanupFailure = $cleanupFailureCase
    defect = if ($outerFailurePathPass) { $null } else { 'Current outer catch allows a diagnostics exception to escape before assigning report.failure; separately, cleanup failure handling checks PSObject.Properties on an OrderedDictionary and overwrites the original failure with cleanup-only text.' }
    boundedGuardProposal = if ($outerFailurePathPass) { $null } else { 'Guard Capture-EdgeFailureDiagnostics in the outer catch, record a redacted diagnostic-capture error, then always assign report.failure from the original exception; in cleanup failure handling use dictionary-safe failure-key detection (or initialize failure) before appending cleanup evidence.' }
}

$evidence = [ordered]@{
    schema = 'rct.student-requests-h62-edge-fix.v1'
    rulesSha256 = $rulesSha256
    packetSha256 = $packetSha256
    packetPath = $packetPath
    runnerPath = $runnerPath
    correction = [ordered]@{
        beforeRunnerSha256 = $preservedFailureEvidence.runnerSha256
        afterRunnerSha256 = Get-Sha256Hex -Path $runnerPath
        preservedFailureEvidencePath = $preservedFailureEvidencePath
        preservedFailureEvidenceSha256 = $preservedFailureEvidenceSha256
    }
    runnerSha256 = Get-Sha256Hex -Path $runnerPath
    unionRoot = $resolvedUnionRoot
    fixture = [ordered]@{
        root = $fixtureRoot
        nginxConfig = $fixtureNginx
        defaultConfig = $fixtureDefault
        certificate = $fixtureCertificate
        privateKey = $fixtureKey
        nginxSha256 = Get-Sha256Hex -Path $fixtureNginx
        defaultSha256 = Get-Sha256Hex -Path $fixtureDefault
        certificateBytes = (Get-Item -LiteralPath $fixtureCertificate).Length
        privateKeyBytes = (Get-Item -LiteralPath $fixtureKey).Length
        tlsContentsPrinted = $false
    }
    checks = [ordered]@{
        parser = 'PASS'
        converter = 'PASS'
        writerNoBom = 'PASS'
        apiContract = 'PASS'
        unexpectedSourceRejected = 'PASS'
        diagnosticsRedactedAndBounded = 'PASS'
        outerFailurePath = $outerFailurePathReview.status
        docker = 'NOT_RUN'
        gradle = 'NOT_RUN'
    }
    outerFailurePath = $outerFailurePathReview
}
$evidencePath = Join-Path $PSScriptRoot 'h62-edge-fix-evidence.json'
[IO.File]::WriteAllText($evidencePath, ($evidence | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
if (-not $outerFailurePathPass) {
    throw 'production outer catch/finally review failed: diagnostics failure did not retain the original edge failure; evidence records the bounded guard proposal'
}
Write-Output "H62 edge fix check: PASS (parser/converter/API guards/no-BOM production writer/negative source guard/diagnostic redaction PASS; Docker/Gradle NOT_RUN; fixture retained at $fixtureRoot; evidence $evidencePath)"
