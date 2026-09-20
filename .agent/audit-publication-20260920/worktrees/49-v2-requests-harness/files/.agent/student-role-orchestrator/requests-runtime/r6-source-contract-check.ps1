#requires -Version 7.4
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$runnerPath = Join-Path $PSScriptRoot 'runner.ps1'
$evidencePath = Join-Path $PSScriptRoot 'R3-EVIDENCE.md'
$source = Get-Content -LiteralPath $runnerPath -Raw
$probePath = Join-Path $PSScriptRoot 'probe.mjs'
$probe = Get-Content -LiteralPath $probePath -Raw

function Assert-Check {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

$mongo = [regex]::Match($source, '(?s)function Get-MongoSnapshot\s*\{.*?\n\}')
Assert-Check $mongo.Success 'Get-MongoSnapshot function was not found'
Assert-Check ($mongo.Value -match 'Assert-That \(\$RequestId -match ''\^\[0-9a-fA-F\]\{24\}\$''') 'Mongo request id is not validated as 24 hexadecimal characters'
Assert-Check ($source -match 'Read-MongoJson -ContainerId \$script:infra\.mongo\.Id') 'Mongo snapshot query is not emitted through the owned read-only helper'
Assert-Check ($mongo.Value -match 'requestTickets') 'Mongo snapshot does not include the request ticket delta query'
Assert-Check ($mongo.Value -match 'requestTicketStatus') 'Mongo snapshot does not include the persisted request ticket status mapping'
Assert-Check ($mongo.Value -match "find\(reqFilter,\{_id:1,status:1\}\)") 'Mongo snapshot does not project the persisted request ticket status'
Assert-Check ($mongo.Value -match 'requestAttachments') 'Mongo snapshot does not include attachment linkage evidence'
Assert-Check ($mongo.Value -match 'requestOutbox') 'Mongo snapshot does not include outbox linkage evidence'

$academicStart = $source.IndexOf("Add-PhaseDiagnostic -Name 'academic-service' -Status 'START'")
$academicWait = $source.IndexOf("Wait-HostHttp -Port ([int]`$script:report.ports.academicDiagnostics)")
$academicSchema = $source.IndexOf('verify Academic V24-V26 schema')
Assert-Check ($academicStart -ge 0 -and $academicWait -gt $academicStart -and $academicSchema -gt $academicWait) 'Academic HTTP readiness is not before Flyway/schema inspection'
Assert-Check ($source -match "academicDiagnostics = 18520") 'Academic disposable diagnostics port is missing'
Assert-Check ($source -match "Add-PhaseDiagnostic -Name 'academic-service' -Status 'FAIL'") 'Academic readiness failure is not recorded as a failed phase'

Assert-Check ($source -match '\$script:preflightReportPath = Join-Path \$script:taskRoot ''preflight-failure\.json''') 'full preflight report path is missing'
Assert-Check ($source -match '\} else \{\s*# Full-mode source/preflight failures') 'full preflight failures have no no-run-dir report branch'
Assert-Check ($source -match 'Write-ReportFile -Path \$script:preflightReportPath') 'full preflight failures do not persist diagnostics'
Assert-Check ($source -notmatch 'Resolve-BootJars') 'stale filesystem jar resolver remains in the runner'
Assert-Check ($source -match 'Resolve-TrustedBuildArtifacts') 'trusted build manifest reader is not wired into source validation'
Assert-Check ($source -match 'ExpectedUnionRevision') 'main expected union revision pin is not required'
Assert-Check ($source -match 'BuildManifestSha256') 'main-pinned build manifest SHA is not required'
Assert-Check ($source -match 'manifestText -ceq \$canonicalText') 'canonical UTF-8 build manifest serialization is not enforced'
Assert-Check ($source -match 'exactly six jar artifacts') 'trusted build manifest does not enforce six jars'
Assert-Check ($source -match 'PWA file set changed after the successful build') 'trusted PWA file set is not compared with current dist'
Assert-Check ($source -match 'Convert-PwaFilesToCanonicalText') 'trusted PWA file list has no explicit canonical digest serialization'
Assert-Check ($source -match 'Sort-CanonicalPathObjects') 'trusted PWA file list is not sorted with an ordinal comparator'
Assert-Check ($source -match 'currentPwa\.manifestSha256') 'current PWA file list digest is not checked against the trusted manifest'
Assert-Check ($source -match '\$bytes = \[IO\.File\]::ReadAllBytes\(\$manifestPath\)') 'trusted manifest is not read into one byte buffer'
Assert-Check ($source -notmatch 'Get-Sha256Hex -Path \$manifestPath') 'trusted manifest hash still uses a separate path read'
Assert-Check ($source -match 'function New-VerifiedArtifactSnapshot') 'verified task-owned artifact snapshot helper is missing'
Assert-Check ($source -match 'Read-VerifiedArtifactBytes') 'artifact snapshot does not use a guarded single-read byte helper'
Assert-Check ($source -match 'Copy-VerifiedArtifactFile') 'artifact snapshot does not copy and verify consumed artifact bytes'
Assert-Check ($source -match '\$script:artifactSnapshot\.Jars') 'backend startup does not consume the owned jar snapshot'
Assert-Check ($source -match '\$script:artifactSnapshot\.PwaDist') 'edge runtime config does not consume the owned PWA snapshot'
Assert-Check ($source -match 'artifactVerifiedAbsent') 'artifact snapshot cleanup verification is missing'
Assert-Check ($source -match 'Get-GitBlobHashForBytes') 'cached edge source bytes are not bound to a Git revision blob'
Assert-Check ($source -match 'RelativePathForGit') 'cached edge blob binding does not apply the tracked path clean filter'
Assert-Check ($source -match 'cached nginx bytes do not match the expected revision blob') 'cached nginx blob/bytes equality is not fail-closed'
Assert-Check ($source -match 'cached default\.conf bytes do not match the expected revision blob') 'cached default.conf blob/bytes equality is not fail-closed'
Assert-Check ($probe -match 'function httpsFixedOversize') 'fixed oversize probe has no response-aware transport handler'
Assert-Check ($probe -match 'statusCode === 0') 'MOCKTRANSPORT no-response error case is missing'
Assert-Check ($probe -match 'fixedEarlyEpipe413') 'fixed 413 early-close pure evidence is missing'
Assert-Check ($probe -match 'httpsFixedOversize\(origin') 'runtime I2 is not wired to the response-aware fixed request'

$runtimeConfig = [regex]::Match($source, '(?s)function New-RuntimeConfig\s*\{.*?\n\}')
Assert-Check $runtimeConfig.Success 'New-RuntimeConfig function was not found'
Assert-Check ($runtimeConfig.Value -notmatch 'Resolve-EdgeSources') 'New-RuntimeConfig re-resolves edge sources after the validation boundary'
Assert-Check ($runtimeConfig.Value -match 'validatedEdgeSources') 'New-RuntimeConfig does not use the immutable validated edge source cache'
Assert-Check ($source -match 'function Assert-UnionSourceStability') 'exact UnionRepo source stability guard is missing'
Assert-Check ($source -match 'function Get-UnionTrackedBlobHash') 'exact revision tracked source blob pin is missing'
Assert-Check ($source -match 'Get-Sha256Hex -Path \$edge\.Path') 'between-phase edge source byte swap is not guarded'
Assert-Check ($source -match "Assert-UnionSourceStability -Context 'edge start preflight'") 'edge start has no final source stability guard'
$resourceGuard = $source.IndexOf("Assert-UnionSourceStability -Context 'runtime resource preflight'", [StringComparison]::Ordinal)
$runIdentity = if ($resourceGuard -ge 0) { $source.IndexOf('New-RunIdentity', $resourceGuard, [StringComparison]::Ordinal) } else { -1 }
Assert-Check ($resourceGuard -ge 0 -and $runIdentity -gt $resourceGuard) 'runtime resource creation is not preceded by the exact source stability guard'
Assert-Check ($source -match 'Get-ExactObjectProperty') 'attachment projection does not use exact field names'
Assert-Check ($source -match "-Context 'I1 probe API attachments' -Format Api") 'I1 Mongo delta does not validate the public API attachment shape separately'
Assert-Check ($source -match "-Context 'I1 Mongo request attachments' -Format Mongo") 'I1 Mongo delta does not validate stored attachment shape separately'
Assert-Check ($source -match "-Context 'I1 outbox payload attachments' -Format Mongo") 'I1 outbox delta does not validate stored payload shape separately'
Assert-Check ($source -match '\$After\.requestTicketStatus -ceq ''SUBMITTED''') 'I1 delta does not verify the persisted SUBMITTED ticket state behind API PENDING'
Assert-Check ($probe -match 'const contentType = item\.contentType') 'probe accepts no strict public contentType field'
Assert-Check ($probe -match 'const size = item\.sizeBytes') 'probe accepts no strict public sizeBytes field'
Assert-Check ($probe -match '(?m)^\s*state,\s*$') 'probe does not preserve the exact public ACTIVE state in serialized output'
Assert-Check ($probe -notmatch 'state:\s*state\.toLowerCase\(\)') 'probe lowercases the public attachment state'
Assert-Check ($probe -notmatch "\['content_type', 'contentType'\]") 'probe still accepts snake_case content type aliases as API data'
Assert-Check ($probe -notmatch "\['sizeBytes', 'size'\]") 'probe still accepts generic size aliases as API data'
Assert-Check ($probe -match "\['content_type', 'size', 'uploaded_at', 'expires_at', 'expired_at'\]") 'probe does not reject extra snake_case attachment aliases beside API fields'
Assert-Check ($probe -match "state === 'ACTIVE'") 'probe does not require the exact API ACTIVE attachment state'
Assert-Check ($probe -match 'selectedLesson.*not part of StudentRequestApiModels.Detail') 'probe does not guard against the removed selectedLesson pseudo-field'

$transform = [regex]::Match($source, '(?s)function Convert-EdgeToRuntimeConfig\s*\{.*?\n\}')
Assert-Check $transform.Success 'Convert-EdgeToRuntimeConfig function was not found'
Assert-Check ($transform.Value -match 'location = /') 'runtime edge transform does not replace the exact root location'
Assert-Check ($transform.Value -match 'try_files /index\.html =200') 'runtime exact root does not serve the copied PWA index'
Assert-Check ($transform.Value -match 'web-panel-nginx:80') 'runtime edge transform has no source prefix match for the existing panel route'

Assert-Check (Test-Path -LiteralPath $evidencePath -PathType Leaf) 'R6 evidence file is missing'
Write-Output 'R6 source contract check: PASS (Mongo query helper and runtime evidence gates, Academic readiness ordering, preflight diagnostics, and trusted provenance gates present)'
