#requires -Version 7.4
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Execute only the deterministic Node source self-test. Its transport is a
# local EventEmitter stub, including Node-style undefined-header rejection;
# no network, server or port is opened.
$probePath = Join-Path $PSScriptRoot 'probe.mjs'
$probeText = Get-Content -LiteralPath $probePath -Raw
& node --check $probePath
if ($LASTEXITCODE -ne 0) { throw "probe syntax check failed with exit code $LASTEXITCODE" }
if ($probeText -notmatch 'headers-only') { throw 'headers-only fixed oversize caller is not documented in the probe self-test' }
if ($probeText -notmatch 'contentType: oversize\.contentType') { throw 'runtime fixed oversize callsite does not pass an explicit Content-Type' }
if ($probeText -notmatch 'value !== undefined && value !== null') { throw 'Node header validation regression guard is missing from the mock transport' }
if ($probeText -notmatch 'api/auth/session') { throw 'Auth session GET is missing from probe source' }
if ($probeText -notmatch 'assertStudentSessionContract') { throw 'CurrentSessionResponse session-shape validator is missing from probe source' }
if ($probeText -notmatch '\$\.userId|session\.userId') { throw 'Auth session validator does not read the top-level userId field' }
if ($probeText -match 'state:\s*state\.toLowerCase\(\)') { throw 'probe lowercases the public API attachment state before serialization' }
if ($probeText -notmatch 'apiAttachmentFixture:\s*detailEvidence\.attachments') { throw 'probe self-test does not expose the serialized API attachment fixture used by PowerShell assertions' }

$output = @(& node $probePath --self-test 2>&1)
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) { throw "probe self-test failed with exit code $exitCode`: $($output -join ' ')" }
$lines = @($output | ForEach-Object { [string]$_ } | Where-Object { $_.Trim().Length -gt 0 })
$result = $lines[-1] | ConvertFrom-Json -Depth 12
if ($result.status -ne 'PASS' -or $result.mode -ne 'source-self-test') { throw 'probe self-test did not report source-self-test PASS' }
if ($result.detailContract.corruptionRejected -ne $true) { throw 'detail corruption regression evidence is missing' }
if ($result.detailContract.nullableExpiredAtAccepted -ne $true) { throw 'nullable API expiredAt acceptance evidence is missing' }
if (@($result.apiAttachmentFixture).Count -ne 2 -or @($result.apiAttachmentFixture | Where-Object { $_.state -cne 'ACTIVE' }).Count -ne 0) { throw 'serialized Node API attachment fixture did not preserve exact ACTIVE state' }
foreach ($field in @('summary.status=PENDING', 'decision=null|absent', 'attachments[].contentType', 'attachments[].sizeBytes', 'attachments[].state=ACTIVE', 'attachments[].uploadedAt<expiresAt', 'attachments[].expiredAt=null|absent')) {
    if (@($result.detailContract.responsePayloadFields) -notcontains $field) { throw "strict I1 API lifecycle field evidence is missing: $field" }
}
if ($result.authSession.dto -ne 'CurrentSessionResponse' -or $result.authSession.valid.userId -ne '42' -or $result.authSession.valid.activeRole -ne 'STUDENT' -or $result.authSession.corruptionRejected -ne $true) { throw 'faithful CurrentSessionResponse fixture or corruption evidence is missing' }
if ($result.transport.fixedEarlyEpipe413 -ne $true -or $result.transport.noResponseErrorsFail -ne $true) { throw 'response-aware 413/transport evidence is incomplete' }

Write-Output "R8 probe callsite check: PASS (node --check exit=0; self-test exit=$exitCode; fixed header, detail corruption and response-aware transport gates observed)"
