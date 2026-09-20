$ErrorActionPreference = 'Stop'

$manifestPath = Join-Path $PSScriptRoot 'final-manifest.json'
$sourcePaths = @(
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/AuthApi.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/AuthSessionApi.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/InternalSessionAdmissionApi.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AccountHistoryEvent.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AccountHistoryPage.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthAdmissionRequest.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthAdmissionResponse.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthSessionSummary.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthSessionsPage.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/ChangePasswordRequest.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/CurrentSessionResponse.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/PasswordPolicyResponse.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/RoleGrantResponse.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/SelectActiveRoleRequest.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/SelectActiveRoleResponse.java',
  'services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/TokenResponse.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilter.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthSessionController.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/InternalSessionAdmissionController.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/exception/GlobalExceptionHandler.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/security/SessionPrincipal.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/AuthService.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/TmaService.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/ActiveRolePolicy.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/AuthSessionException.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/PasswordPolicy.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionAdmissionException.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionAdmissionService.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionLifecycleService.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcAuthSessionQueryAdapter.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/model/AuthMethod.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/model/AuthRole.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/model/RoleGrant.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/model/RoleStatus.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/model/SecurityEvent.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/model/SessionRevokeReason.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/model/SessionSnapshot.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/model/SessionState.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/port/AuthSessionQueryPort.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/port/CredentialSessionTransactionPort.java',
  'services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/port/SessionStatePort.java',
  'services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtClaims.java',
  'services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtValidator.java',
  'services/academic-service/academic-app/src/main/resources/db/migration/V24__auth_session_authority.sql',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/arch/AuthApiContractTest.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilterPurposeTest.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/controller/AuthSessionControllerTest.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AbstractIntegrationTest.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthIT.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/LogoutLifecycleIT.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OpenApiSnapshotIT.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OtpIT.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SessionAuthFlowIT.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/JwtTokenPurposeTest.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/OtpServiceTest.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/ActiveRolePolicyTest.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcAuthSessionQueryAdapterIT.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/PasswordPolicyTest.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionAdmissionServiceTest.java',
  'services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionLifecycleServiceTest.java',
  'services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/DualModeUserContextFilterTest.java',
  'services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/InternalJwtValidatorTest.java',
  'services/shared/shared-security/src/testFixtures/java/ru/rutcampustrack/shared/security/InternalJwtTestFactory.java'
)

function Get-FileRecord([string]$path) {
  if (!(Test-Path -LiteralPath $path)) { throw "missing artifact: $path" }
  $item = Get-Item -LiteralPath $path
  $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $path
  [pscustomobject]@{ bytes=[int64]$item.Length; sha256=$hash.Hash }
}

$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if (@($manifest.effectiveSources).Count -ne $sourcePaths.Count) {
  throw "source list count drift: manifest=$(@($manifest.effectiveSources).Count), script=$($sourcePaths.Count)"
}
foreach ($path in $sourcePaths) {
  $entry = @($manifest.effectiveSources | Where-Object { $_.path -eq $path })
  if ($entry.Count -ne 1) { throw "manifest source entry mismatch: $path" }
  $record = Get-FileRecord $path
  $entry[0].status = 'present'
  $entry[0].bytes = $record.bytes
  $entry[0].sha256 = $record.sha256
}

foreach ($entry in @($manifest.deletedTombstones)) {
  if (Test-Path -LiteralPath $entry.path) { throw "deleted tombstone exists: $($entry.path)" }
}
foreach ($entry in @($manifest.foundationReferences)) {
  $record = Get-FileRecord $entry.path
  $entry.bytes = $record.bytes
  $entry.sha256 = $record.sha256
}
foreach ($entry in @($manifest.finalEvidence)) {
  $record = Get-FileRecord $entry.path
  [xml]$xml = Get-Content -Raw -LiteralPath $entry.path
  if ([int]$xml.testsuite.skipped -ne 0 -or [int]$xml.testsuite.failures -ne 0 -or [int]$xml.testsuite.errors -ne 0) {
    throw "non-passing final XML: $($entry.path)"
  }
  $entry.bytes = $record.bytes
  $entry.sha256 = $record.sha256
  $entry.tests = [int]$xml.testsuite.tests
  $entry.skipped = [int]$xml.testsuite.skipped
  $entry.failures = [int]$xml.testsuite.failures
  $entry.errors = [int]$xml.testsuite.errors
}
$generated = @($manifest.generatedArtifacts)[0]
$generatedRecord = Get-FileRecord $generated.path
$generated.bytes = $generatedRecord.bytes
$generated.sha256 = $generatedRecord.sha256

$unit = @($manifest.finalEvidence | Where-Object { $_.provenance -match 'unit' })
$integration = @($manifest.finalEvidence | Where-Object { $_.provenance -notmatch 'unit' })
$unitGroups = @($unit | Group-Object uniqueKey)
$integrationGroups = @($integration | Group-Object uniqueKey)
$unitTests = ($unitGroups | ForEach-Object { [int]$_.Group[0].tests } | Measure-Object -Sum).Sum
$integrationTests = ($integrationGroups | ForEach-Object { [int]$_.Group[0].tests } | Measure-Object -Sum).Sum
if ($unitTests -ne 57 -or $integrationTests -ne 47 -or $unitGroups.Count -ne 6 -or $integrationGroups.Count -ne 8) {
  throw "final count drift: unit=$unitTests/$($unitGroups.Count), integration=$integrationTests/$($integrationGroups.Count)"
}
$manifest.effectiveSourceCount = $sourcePaths.Count
$manifest.checks.sourceHashSelfAudit = 'exit 0; reproducible script verified every listed source/reference/XML/generated artifact and unique counts 57 unit/47 integration'
$manifest.checks.gitDiffCheck = 'exit 0; no diff-check errors; pre-existing LF/CRLF warnings only'
$manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding utf8
$manifestRecord = Get-FileRecord $manifestPath
Write-Output "FINAL_MANIFEST_READY sources=$($sourcePaths.Count) tombstones=$(@($manifest.deletedTombstones).Count) unit=$unitTests/6 integration=$integrationTests/8 manifestBytes=$($manifestRecord.bytes) manifestSha256=$($manifestRecord.sha256)"
