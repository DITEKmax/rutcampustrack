#requires -Version 7.4
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Check {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

$script:pureRunId = [Guid]::NewGuid().ToString('N')
$script:pureOwnerToken = "rct-i6-pure-$($script:pureRunId)"
$script:pureWorkspaceRoot = [IO.Path]::GetFullPath($PSScriptRoot)

function Resolve-PurePathWithin {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Candidate
    )
    $rootFull = [IO.Path]::GetFullPath($Root)
    $candidateFull = [IO.Path]::GetFullPath($Candidate)
    $boundary = "$rootFull$([IO.Path]::DirectorySeparatorChar)"
    Assert-Check (($candidateFull -ceq $rootFull) -or $candidateFull.StartsWith($boundary, [StringComparison]::OrdinalIgnoreCase)) "path escapes cleanup bound: $candidateFull"
    return $candidateFull
}

function New-PureOwnedRoot {
    param(
        [Parameter(Mandatory = $true)][string]$Parent,
        [Parameter(Mandatory = $true)][string]$Name
    )
    $parentFull = Resolve-PurePathWithin -Root $script:pureWorkspaceRoot -Candidate $Parent
    $parentItem = Get-Item -LiteralPath $parentFull -Force -ErrorAction Stop
    Assert-Check ($parentItem.PSIsContainer) "owned fixture parent is not a directory: $parentFull"
    Assert-Check (($parentItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "owned fixture parent is a reparse point: $parentFull"
    $rootFull = Resolve-PurePathWithin -Root $parentFull -Candidate (Join-Path $parentFull $Name)
    Assert-Check (-not (Test-Path -LiteralPath $rootFull)) "refusing to reuse occupied pure fixture root: $rootFull"
    New-Item -ItemType Directory -Path $rootFull -ErrorAction Stop | Out-Null
    $markerPath = Join-Path $rootFull '.rct-i6-pure-owner'
    [IO.File]::WriteAllText($markerPath, $script:pureOwnerToken, [Text.UTF8Encoding]::new($false))
    return $rootFull
}

function Assert-PureOwnedRoot {
    param([Parameter(Mandatory = $true)][string]$Root)
    $rootFull = Resolve-PurePathWithin -Root $script:pureWorkspaceRoot -Candidate $Root
    Assert-Check ($rootFull -cne $script:pureWorkspaceRoot) "refusing to operate on workspace root: $rootFull"
    $rootItem = Get-Item -LiteralPath $rootFull -Force -ErrorAction Stop
    Assert-Check ($rootItem.PSIsContainer) "owned fixture root is not a directory: $rootFull"
    Assert-Check (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "owned fixture root is a reparse point: $rootFull"
    $markerPath = Join-Path $rootFull '.rct-i6-pure-owner'
    Assert-Check (Test-Path -LiteralPath $markerPath -PathType Leaf) "owned fixture marker is missing: $rootFull"
    Assert-Check ([IO.File]::ReadAllText($markerPath) -ceq $script:pureOwnerToken) "owned fixture marker belongs to another run: $rootFull"
    return $rootFull
}

function New-PureOwnedSubdirectory {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$RelativePath
    )
    $rootFull = Assert-PureOwnedRoot -Root $Root
    $current = $rootFull
    foreach ($segment in ($RelativePath -split '[\\/]')) {
        Assert-Check (-not [string]::IsNullOrWhiteSpace($segment) -and $segment -notin @('.', '..')) "invalid owned fixture child path: $RelativePath"
        $next = Resolve-PurePathWithin -Root $rootFull -Candidate (Join-Path $current $segment)
        if (Test-Path -LiteralPath $next) {
            $item = Get-Item -LiteralPath $next -Force -ErrorAction Stop
            Assert-Check ($item.PSIsContainer) "owned fixture child is not a directory: $next"
            Assert-Check (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "owned fixture child is a reparse point: $next"
        }
        else {
            New-Item -ItemType Directory -Path $next -ErrorAction Stop | Out-Null
        }
        $current = $next
    }
    return $current
}

function Remove-PureOwnedRoot {
    param([Parameter(Mandatory = $true)][string]$Root)
    $rootFull = Assert-PureOwnedRoot -Root $Root
    $descendants = @(Get-ChildItem -LiteralPath $rootFull -Force -Recurse -ErrorAction Stop | Where-Object {
            ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0
        })
    Assert-Check ($descendants.Count -eq 0) "refusing recursive cleanup with reparse descendants: $rootFull"
    Remove-Item -LiteralPath $rootFull -Recurse -Force -ErrorAction Stop
    Assert-Check (-not (Test-Path -LiteralPath $rootFull)) "owned fixture cleanup did not remove its root: $rootFull"
}

function Write-PureOwnedNewText {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text
    )
    $rootFull = Assert-PureOwnedRoot -Root $Root
    $targetFull = Resolve-PurePathWithin -Root $rootFull -Candidate $Path
    Assert-Check (-not (Test-Path -LiteralPath $targetFull)) "refusing to overwrite existing pure evidence log: $targetFull"
    $parentPath = [IO.Path]::GetDirectoryName($targetFull)
    $parentItem = Get-Item -LiteralPath $parentPath -Force -ErrorAction Stop
    Assert-Check ($parentItem.PSIsContainer) "pure evidence log parent is not a directory: $parentPath"
    Assert-Check (($parentItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "pure evidence log parent is a reparse point: $parentPath"
    [IO.File]::WriteAllText($targetFull, $Text, [Text.UTF8Encoding]::new($false))
}

function Get-FunctionDefinition {
    param(
        [Parameter(Mandatory = $true)][object]$Ast,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Path
    )
    $definition = @($Ast.FindAll({
                param($node)
                $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $Name
            }, $true)) | Select-Object -First 1
    Assert-Check ($null -ne $definition) "function missing in $Path`: $Name"
    return $definition
}

function Invoke-ProducerProcess {
    param(
        [string]$FaultPoint = '',
        [Parameter(Mandatory = $true)][string]$UnionRepo,
        [Parameter(Mandatory = $true)][string]$ManifestPath,
        [Parameter(Mandatory = $true)][string]$RunsRoot,
        [Parameter(Mandatory = $true)][string]$LogRoot,
        [Parameter(Mandatory = $true)][string]$LogPrefix
    )
    $pwsh = Get-Command pwsh -CommandType Application | Select-Object -First 1
    Assert-Check ($null -ne $pwsh) 'pwsh executable is unavailable for process-level publication harness'
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = [string]$pwsh.Source
    $startInfo.WorkingDirectory = $PSScriptRoot
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($argument in @(
            '-NoProfile', '-NonInteractive', '-File', $producerPath,
            '-UnionRepo', $UnionRepo, '-ExpectedRevision', ('a' * 40),
            '-ManifestPath', $ManifestPath, '-RunsRoot', $RunsRoot,
            '-PublicationHarness'
        )) {
        [void]$startInfo.ArgumentList.Add([string]$argument)
    }
    $startInfo.Environment['RCT_I6_FAULT_POINT'] = $FaultPoint
    $startInfo.Environment['RCT_I6_PUBLICATION_HARNESS'] = '1'
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        Assert-Check $process.Start() "failed to start producer process for $FaultPoint"
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        $exitCode = [int]$process.ExitCode
    }
    finally {
        $process.Dispose()
    }
    Write-PureOwnedNewText -Root $LogRoot -Path "$LogPrefix.stdout.log" -Text ([string]$stdout)
    Write-PureOwnedNewText -Root $LogRoot -Path "$LogPrefix.stderr.log" -Text ([string]$stderr)
    return [pscustomobject]@{
        ExitCode = $exitCode
        Stdout = [string]$stdout
        Stderr = [string]$stderr
    }
}

$producerPath = Join-Path $PSScriptRoot 'producer.ps1'
$runnerPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\worktrees\v2-requests-harness\.agent\student-role-orchestrator\requests-runtime\runner.ps1'))
$producerTokens = $null
$producerErrors = $null
$producerAst = [System.Management.Automation.Language.Parser]::ParseFile($producerPath, [ref]$producerTokens, [ref]$producerErrors)
Assert-Check ($producerErrors.Count -eq 0) "producer AST errors: $($producerErrors.Count)"
$runnerTokens = $null
$runnerErrors = $null
$runnerAst = [System.Management.Automation.Language.Parser]::ParseFile($runnerPath, [ref]$runnerTokens, [ref]$runnerErrors)
Assert-Check ($runnerErrors.Count -eq 0) "accepted runner AST errors: $($runnerErrors.Count)"

foreach ($name in @(
        'Assert-That', 'Get-Sha256BytesHex', 'Get-Sha256Hex', 'Get-Sha256Text',
        'Assert-NoReparsePath', 'Assert-PlannedOutputPaths', 'Get-PlannedOutputPaths',
        'Sort-CanonicalPathObjects', 'Convert-PwaFilesToCanonicalText',
        'Get-JarSpec', 'Assert-NoPreexistingBuildArtifacts', 'Get-CanonicalTimestamp',
        'Invoke-FaultInjection', 'Write-ManifestTemporary', 'Read-ManifestTemporary',
        'Get-ManifestHash', 'Move-ManifestNoOverwrite', 'Write-RunReport',
        'Record-ProducerFailure', 'Handle-ProducerException', 'Remove-OwnedManifestTemporary',
        'New-CanonicalManifestObject', 'Write-TrustedManifest'
    )) {
    Invoke-Expression (Get-FunctionDefinition -Ast $producerAst -Name $name -Path $producerPath).Extent.Text
}
foreach ($name in @(
        'Get-JsonPropertyValue', 'Assert-JsonProperties', 'Assert-JsonString',
        'Assert-JsonInteger', 'Assert-JsonBoolean', 'Assert-Sha256Value',
        'Convert-TrustedManifestToCanonicalObject'
    )) {
    Invoke-Expression (Get-FunctionDefinition -Ast $runnerAst -Name $name -Path $runnerPath).Extent.Text
}

$sourceText = Get-Content -LiteralPath $producerPath -Raw
$pureSourceText = Get-Content -LiteralPath $PSCommandPath -Raw
Assert-Check ($sourceText.Contains('--no-daemon --no-parallel --max-workers=1 --no-problems-report')) 'six Gradle commands do not pin the required one-worker flags'
Assert-Check ($sourceText.Contains('npm run build --workspace @rct/pwa-vue')) 'PWA build command is missing'
Assert-Check (-not $sourceText.Contains('npm ci')) 'producer must not install dependencies'
Assert-Check (-not $sourceText.Contains('git clean')) 'producer must not clean arbitrary worktree files'
Assert-Check (-not $sourceText.Contains('docker ')) 'producer must not create a runtime control plane'
Assert-Check ($sourceText.Contains('Assert-NoPreexistingBuildArtifacts -Repo')) 'stale artifact preflight is not wired before builds'
Assert-Check ($sourceText.Contains('$script:report.cleanAfterBuild = $true')) 'clean-after-build gate is missing'
Assert-Check ($sourceText.Contains('Write-TrustedManifest -Manifest $manifest')) 'manifest write call is missing'
Assert-Check ($sourceText.Contains('Assert-PlannedOutputPaths -Repo $script:unionRoot -ManifestPath $script:manifestPath -RunsRoot $script:runsRoot')) 'planned output ancestor preflight is missing'
Assert-Check ($sourceText.Contains('[switch]$PublicationHarness') -and $sourceText.Contains('RCT_I6_PUBLICATION_HARNESS') -and $sourceText.Contains('New-PublicationHarnessManifest')) 'bounded production publication harness is missing'
Assert-Check ($sourceText.Contains('$requestedManifestPath = $ManifestPath') -and $sourceText.Contains('$requestedRunsRoot = $RunsRoot')) 'public output path arguments are not snapshotted before internal state initialization'
Assert-Check ($sourceText.Contains('$script:report.manifestPath = $script:manifestPath')) 'failure report does not record the resolved manifest path'
Assert-Check (-not $sourceText.Contains('postmoveReadAllBytes')) 'manifest read fault point still uses a misleading post-commit name'
Assert-Check ($pureSourceText.Contains('New-PureOwnedRoot') -and $pureSourceText.Contains('Assert-PureOwnedRoot') -and $pureSourceText.Contains('Remove-PureOwnedRoot')) 'pure fixtures do not use run-owned bounded cleanup'
Assert-Check ($pureSourceText.Contains('Write-PureOwnedNewText -Root $LogRoot')) 'process logs do not use the run-scoped no-overwrite writer'
Assert-Check ($pureSourceText.Contains('process-case-manifestHash') -and $pureSourceText.Contains('foreign-sentinel')) 'occupied process fixture regression is missing'
foreach ($operation in @(
        @{ Name = 'temporary write'; Function = 'Write-ManifestTemporary'; Fault = "Invoke-FaultInjection -Name 'manifestTempWrite'"; Primitive = '[IO.File]::WriteAllBytes' },
        @{ Name = 'temporary read'; Function = 'Read-ManifestTemporary'; Fault = "Invoke-FaultInjection -Name 'manifestTempRead'"; Primitive = '[IO.File]::ReadAllBytes' },
        @{ Name = 'manifest hash'; Function = 'Get-ManifestHash'; Fault = "Invoke-FaultInjection -Name 'manifestHash'"; Primitive = 'Get-Sha256BytesHex' },
        @{ Name = 'run report'; Function = 'Write-RunReport'; Fault = "Invoke-FaultInjection -Name 'runreportWriteAllText'"; Primitive = '[IO.File]::WriteAllText' },
        @{ Name = 'manifest rename'; Function = 'Move-ManifestNoOverwrite'; Fault = "Invoke-FaultInjection -Name 'manifestRename'"; Primitive = '[IO.File]::Move' }
    )) {
    $operationDefinition = Get-FunctionDefinition -Ast $producerAst -Name $operation.Function -Path $producerPath
    Assert-Check ($operationDefinition.Extent.Text.Contains($operation.Fault) -and $operationDefinition.Extent.Text.Contains($operation.Primitive)) "actual $($operation.Name) fault point is not wired to its helper"
}
$jarIndex = $sourceText.IndexOf('$jarEntries = Get-BuiltJarEntries', [StringComparison]::Ordinal)
$pwaIndex = $sourceText.IndexOf('$pwaManifest = Get-PwaDistManifest', [StringComparison]::Ordinal)
$cleanAfterIndex = $sourceText.LastIndexOf('$script:report.cleanAfterBuild = $true', [StringComparison]::Ordinal)
$writeIndex = $sourceText.IndexOf('Write-TrustedManifest -Manifest $manifest', [StringComparison]::Ordinal)
Assert-Check ($jarIndex -ge 0 -and $pwaIndex -gt $jarIndex -and $cleanAfterIndex -gt $pwaIndex -and $writeIndex -gt $cleanAfterIndex) 'manifest emission is not ordered after artifact and clean-after gates'
$moveIndex = $sourceText.IndexOf('Move-ManifestNoOverwrite -TemporaryPath $temporary -DestinationPath $script:manifestPath', [StringComparison]::Ordinal)
Assert-Check ($moveIndex -gt 0) 'manifest publication does not use the explicit no-overwrite commit helper'
Assert-Check ($sourceText.Contains('[IO.File]::Move($TemporaryPath, $DestinationPath, $false)')) 'manifest publication does not use an atomic no-overwrite move'
Assert-Check ($sourceText.IndexOf('Move-Item', [StringComparison]::Ordinal) -lt 0) 'manifest publication still uses Move-Item'
$readAfterMove = @([regex]::Matches($sourceText, '\[IO\.File\]::ReadAllBytes\(') | Where-Object { $_.Index -gt $moveIndex })
Assert-Check ($readAfterMove.Count -eq 0) 'manifest bytes are read or hashed after the commit point'
$reportPrepareIndex = $sourceText.IndexOf('Write-RunReport -AllowOverwrite:$false', [StringComparison]::Ordinal)
Assert-Check ($reportPrepareIndex -gt 0 -and $reportPrepareIndex -lt $moveIndex) 'PASS run report is not prepared before publication'
$finallyIndex = $sourceText.LastIndexOf('finally {', [StringComparison]::Ordinal)
Assert-Check ($finallyIndex -gt $moveIndex -and $sourceText.Substring($finallyIndex).IndexOf('Write-RunReport', [StringComparison]::Ordinal) -lt 0) 'finally still performs fallible report publication after the commit point'
$commitGuard = $sourceText.IndexOf('if ($script:manifestCommitted)', [StringComparison]::Ordinal)
Assert-Check ($commitGuard -gt 0 -and $sourceText.Substring($commitGuard).Contains('$script:report.status = ''PASS''')) 'post-commit errors are not prevented from downgrading PASS'
Assert-Check ($sourceText.Contains('if (Handle-ProducerException -FailureMessage $failureMessage)') -and $sourceText.Contains('exit 1')) 'top-level failure handling does not preserve nonzero pre-commit errors'
Assert-Check (-not $sourceText.Contains('$jars[0].FullName')) 'stale JAR diagnostic indexes an empty array eagerly'
$plannedOutputIndex = $sourceText.IndexOf('Assert-PlannedOutputPaths -Repo $script:unionRoot -ManifestPath $script:manifestPath -RunsRoot $script:runsRoot', [StringComparison]::Ordinal)
$runsRootCreateIndex = $sourceText.IndexOf('New-Item -ItemType Directory -Path $script:runsRoot', [StringComparison]::Ordinal)
Assert-Check ($plannedOutputIndex -gt 0 -and $plannedOutputIndex -lt $runsRootCreateIndex) 'planned ancestor checks run after output directory creation'

$revision = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
$pwaFiles = @(
    [ordered]@{ bytes = 6; relativePath = 'sw-assets.js'; sha256 = ('b' * 64) },
    [ordered]@{ bytes = 8; relativePath = 'index.html'; sha256 = ('c' * 64) },
    [ordered]@{ bytes = 4; relativePath = 'sw.js'; sha256 = ('d' * 64) }
)
$sortedPwaFiles = @(Sort-CanonicalPathObjects -Items $pwaFiles)
$pwaDigest = Get-Sha256Text -Text (Convert-PwaFilesToCanonicalText -Files $sortedPwaFiles)
$pwa = [ordered]@{
    files = $pwaFiles
    manifestSha256 = $pwaDigest
    relativeRoot = 'frontends/pwa-vue/dist'
}
$jars = @(
    Get-JarSpec | ForEach-Object {
        [ordered]@{
            bytes = 100 + $_.Name.Length
            name = $_.Name
            relativePath = "$($_.Relative.Replace('\', '/'))/$($_.Name)-synthetic.jar"
            sha256 = (('e' + $_.Name.Length) * 32).Substring(0, 64)
        }
    }
)
$jars = @($jars | Sort-Object { $_.relativePath } -Descending)
$commands = @(
    [ordered]@{
        command = '.\gradlew.bat :services:auth-service:auth-app:bootJar --no-daemon --no-parallel --max-workers=1 --no-problems-report'
        exitCode = 0
        finishedAt = '2026-09-16 00:01:00.0000000+00:00'
        startedAt = '2026-09-16 00:00:00.0000000+00:00'
    }
)
$environment = [ordered]@{
    gradle = 'Gradle 8.14'
    hostOs = 'Microsoft Windows NT 10.0.26100.0'
    java = 'openjdk version "21.0.8"'
    node = 'v24.14.0'
    powershell = '7.6.5'
}
$canonicalObject = New-CanonicalManifestObject -AbsoluteRepo 'C:\fixture\rct-i6' -Revision $revision -CleanBeforeBuild $true -CleanAfterBuild $true -Commands $commands -Environment $environment -Jars $jars -PwaDist $pwa
$canonicalText = $canonicalObject | ConvertTo-Json -Depth 20 -Compress
Assert-Check (($canonicalObject.Keys -join ',') -ceq 'artifacts,producer,schemaVersion,source') 'manifest top-level key order differs from accepted runner'
Assert-Check (($canonicalObject.artifacts.pwaDist.Keys -join ',') -ceq 'files,manifestSha256,relativeRoot') 'PWA key order differs from accepted runner'
Assert-Check (($canonicalObject.producer.environment.Keys -join ',') -ceq 'gradle,hostOs,java,node,powershell') 'environment key order differs from accepted runner'
Assert-Check (($canonicalObject.source.Keys -join ',') -ceq 'absoluteRepo,cleanAfterBuild,cleanBeforeBuild,revision') 'source key order differs from accepted runner'
Assert-Check (-not $canonicalText.Contains("`r") -and -not $canonicalText.Contains("`n")) 'canonical manifest serializer emitted a line break'
$canonicalBytes = [Text.UTF8Encoding]::new($false).GetBytes($canonicalText)
Assert-Check (-not ($canonicalBytes.Length -ge 3 -and $canonicalBytes[0] -eq 0xef -and $canonicalBytes[1] -eq 0xbb -and $canonicalBytes[2] -eq 0xbf)) 'canonical manifest serializer emitted a BOM'
Assert-Check ((Get-Sha256Text -Text (Convert-PwaFilesToCanonicalText -Files $sortedPwaFiles)) -ceq $pwaDigest) 'PWA digest does not use accepted canonical serializer'
$timestamp = Get-CanonicalTimestamp -Value ([DateTimeOffset]::Parse('2026-09-16T00:00:00.0000000Z'))
$timestampWire = (([ordered]@{ timestamp = $timestamp } | ConvertTo-Json -Compress) | ConvertFrom-Json).timestamp
Assert-Check ($timestampWire -is [string]) 'canonical command timestamp was coerced to DateTime'
Assert-Check ($null -ne [DateTimeOffset]::Parse($timestampWire)) 'canonical command timestamp is not parseable'

$wireObject = $canonicalText | ConvertFrom-Json -Depth 20
$acceptedCanonicalText = (Convert-TrustedManifestToCanonicalObject -Manifest $wireObject) | ConvertTo-Json -Depth 20 -Compress
Assert-Check ($canonicalText -ceq $acceptedCanonicalText) 'producer canonical JSON differs from accepted Resolve-TrustedBuildArtifacts serializer'
Assert-Check ($wireObject.producer.commands[0].startedAt -is [string]) 'command timestamp was coerced to DateTime by PowerShell JSON reader'
$canonicalAgain = New-CanonicalManifestObject -AbsoluteRepo $wireObject.source.absoluteRepo -Revision $wireObject.source.revision -CleanBeforeBuild $wireObject.source.cleanBeforeBuild -CleanAfterBuild $wireObject.source.cleanAfterBuild -Commands @($wireObject.producer.commands) -Environment $wireObject.producer.environment -Jars @($wireObject.artifacts.jars) -PwaDist $wireObject.artifacts.pwaDist
Assert-Check (($canonicalAgain | ConvertTo-Json -Depth 20 -Compress) -ceq $canonicalText) 'canonical serializer is not deterministic'

$fixtureRoot = New-PureOwnedRoot -Parent $PSScriptRoot -Name "pure-stale-fixtures-$($script:pureRunId)"
try {
    New-PureOwnedSubdirectory -Root $fixtureRoot -RelativePath 'services/auth-service/auth-app/build/libs' | Out-Null
    $emptyLibsPass = $true
    try { Assert-NoPreexistingBuildArtifacts -Repo $fixtureRoot } catch { $emptyLibsPass = $false }
    Assert-Check $emptyLibsPass 'empty build/libs preflight was rejected'
    [IO.File]::WriteAllText((Join-Path $fixtureRoot 'services/auth-service/auth-app/build/libs/auth-app-stale.jar'), 'stale', [Text.UTF8Encoding]::new($false))
    $staleJarRejected = $false
    try { Assert-NoPreexistingBuildArtifacts -Repo $fixtureRoot } catch { $staleJarRejected = $true }
    Assert-Check $staleJarRejected 'stale executable JAR preflight was accepted'
}
finally {
    Remove-PureOwnedRoot -Root $fixtureRoot
}

$pwaFixtureRoot = New-PureOwnedRoot -Parent $PSScriptRoot -Name "pure-pwa-fixtures-$($script:pureRunId)"
try {
    New-PureOwnedSubdirectory -Root $pwaFixtureRoot -RelativePath 'frontends/pwa-vue/dist' | Out-Null
    [IO.File]::WriteAllText((Join-Path $pwaFixtureRoot 'frontends/pwa-vue/dist/index.html'), 'stale', [Text.UTF8Encoding]::new($false))
    $stalePwaRejected = $false
    try { Assert-NoPreexistingBuildArtifacts -Repo $pwaFixtureRoot } catch { $stalePwaRejected = $true }
    Assert-Check $stalePwaRejected 'stale PWA dist preflight was accepted'
}
finally {
    Remove-PureOwnedRoot -Root $pwaFixtureRoot
}

$junctionFixtureRoot = New-PureOwnedRoot -Parent $PSScriptRoot -Name "pure-junction-fixtures-$($script:pureRunId)"
try {
    foreach ($junctionCase in @(
            [ordered]@{ Name = 'missing-build'; Link = 'services/auth-service/auth-app/build' },
            [ordered]@{ Name = 'missing-dist'; Link = 'frontends/pwa-vue' }
        )) {
        $caseRoot = New-PureOwnedRoot -Parent $junctionFixtureRoot -Name $junctionCase.Name
        $targetRoot = New-PureOwnedSubdirectory -Root $junctionFixtureRoot -RelativePath "$($junctionCase.Name)-external"
        $linkPath = Join-Path $caseRoot $junctionCase.Link
        $linkParent = [IO.Path]::GetDirectoryName($linkPath)
        New-PureOwnedSubdirectory -Root $caseRoot -RelativePath ([IO.Path]::GetRelativePath($caseRoot, $linkParent)) | Out-Null
        $sentinelPath = Join-Path $targetRoot 'foreign-sentinel.txt'
        [IO.File]::WriteAllText($sentinelPath, 'junction target must remain untouched', [Text.UTF8Encoding]::new($false))
        $beforeHash = Get-Sha256Hex -Path $sentinelPath
        New-Item -ItemType Junction -Path $linkPath -Target $targetRoot | Out-Null
        $junctionRejected = $false
        try {
            Assert-PlannedOutputPaths -Repo $caseRoot -ManifestPath (Join-Path $junctionFixtureRoot "$($junctionCase.Name)-manifest.json") -RunsRoot (Join-Path $junctionFixtureRoot "$($junctionCase.Name)-runs")
        }
        catch {
            $junctionRejected = $true
        }
        Assert-Check $junctionRejected "missing-leaf $($junctionCase.Name) junction ancestor was accepted"
        Assert-Check ((Get-Sha256Hex -Path $sentinelPath) -ceq $beforeHash) "junction case $($junctionCase.Name) changed external sentinel"
        $linkItem = Get-Item -LiteralPath $linkPath -Force
        Assert-Check (($linkItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) "fixture $($junctionCase.Name) is not a junction"
        Remove-Item -LiteralPath $linkPath -Force
    }
}
finally {
    Remove-PureOwnedRoot -Root $junctionFixtureRoot
}

$processLogRoot = New-PureOwnedRoot -Parent $PSScriptRoot -Name "pure-process-logs-$($script:pureRunId)"
$processJunctionFixtureRoot = New-PureOwnedRoot -Parent $PSScriptRoot -Name "pure-process-junction-$($script:pureRunId)"
$processJunctionCaseRoot = New-PureOwnedRoot -Parent $processJunctionFixtureRoot -Name 'case'
$processJunctionTargetRoot = New-PureOwnedSubdirectory -Root $processJunctionFixtureRoot -RelativePath 'external-target'
$processJunctionLink = Join-Path $processJunctionCaseRoot 'services/auth-service/auth-app/build'
$processJunctionRuns = Join-Path $processJunctionCaseRoot 'runs'
$processJunctionManifest = Join-Path $processJunctionCaseRoot 'published-manifest.json'
try {
    New-PureOwnedSubdirectory -Root $processJunctionCaseRoot -RelativePath 'services/auth-service/auth-app' | Out-Null
    $processJunctionSentinel = Join-Path $processJunctionTargetRoot 'foreign-sentinel.txt'
    [IO.File]::WriteAllText($processJunctionSentinel, 'production preflight must not write through junction', [Text.UTF8Encoding]::new($false))
    $processJunctionBeforeHash = Get-Sha256Hex -Path $processJunctionSentinel
    New-Item -ItemType Junction -Path $processJunctionLink -Target $processJunctionTargetRoot | Out-Null
    $junctionProcessResult = Invoke-ProducerProcess -FaultPoint '' -UnionRepo $processJunctionCaseRoot -ManifestPath $processJunctionManifest -RunsRoot $processJunctionRuns -LogRoot $processLogRoot -LogPrefix (Join-Path $processLogRoot "process-junction-$($script:pureRunId)")
    Assert-Check ($junctionProcessResult.ExitCode -eq 1) "production junction preflight exited $($junctionProcessResult.ExitCode), expected 1"
    Assert-Check ($junctionProcessResult.Stderr -match 'reparse path') 'production junction preflight did not report the reparse ancestor'
    Assert-Check (-not (Test-Path -LiteralPath $processJunctionRuns)) 'production junction preflight created runs output before rejecting the path'
    Assert-Check (-not (Test-Path -LiteralPath $processJunctionManifest)) 'production junction preflight created a manifest before rejecting the path'
    Assert-Check ((Get-Sha256Hex -Path $processJunctionSentinel) -ceq $processJunctionBeforeHash) 'production junction preflight wrote through the external target'
    Remove-Item -LiteralPath $processJunctionLink -Force
}
finally {
    if (Test-Path -LiteralPath $processJunctionLink) { Remove-Item -LiteralPath $processJunctionLink -Force }
    Remove-PureOwnedRoot -Root $processJunctionFixtureRoot
}

$publicationFixtureRoot = New-PureOwnedRoot -Parent $PSScriptRoot -Name "pure-publication-fixtures-$($script:pureRunId)"
$processFixtureRoot = $null
$processFixtureCompleted = $false
function Initialize-PublicationFixture {
    param([Parameter(Mandatory = $true)][string]$Root)
    $script:runDir = Join-Path $Root 'run'
    $script:manifestPath = Join-Path $Root 'published-manifest.json'
    New-PureOwnedSubdirectory -Root $Root -RelativePath 'run' | Out-Null
    $script:manifestCommitted = $false
    $script:manifestTempPath = $null
    $script:manifestTempOwned = $false
    $script:preparedManifestSha256 = $null
    $script:faultInjection = ''
    $script:report = [ordered]@{
        schema = 'rct.i6-build-producer.v1'
        status = 'NOT_RUN'
        runId = [Guid]::NewGuid().ToString('N')
        startedAt = [DateTimeOffset]::UtcNow.ToString('o')
        finishedAt = $null
        unionRepo = $Root
        expectedRevision = $revision
        manifestPath = $script:manifestPath
        manifestSha256 = $null
        cleanBeforeBuild = $true
        cleanAfterBuild = $true
        environment = $environment
        commands = $commands
        artifacts = [ordered]@{ jars = $jars; pwaDist = $pwa }
        failure = $null
        limitations = @()
    }
}

try {
    # Successful publication is compared byte-for-byte with the accepted
    # consumer serializer and with the report's prepared hash.
    $successRoot = New-PureOwnedRoot -Parent $publicationFixtureRoot -Name 'success'
    Initialize-PublicationFixture -Root $successRoot
    Write-TrustedManifest -Manifest $canonicalObject
    Assert-Check $script:manifestCommitted 'successful publication did not reach the commit point'
    Assert-Check (Test-Path -LiteralPath $script:manifestPath -PathType Leaf) 'successful publication did not create the manifest'
    $publishedBytes = [IO.File]::ReadAllBytes($script:manifestPath)
    Assert-Check ((Get-Sha256BytesHex -Bytes $publishedBytes) -ceq (Get-Sha256Text -Text $canonicalText)) 'published manifest hash differs from canonical bytes'
    $publishedText = [Text.UTF8Encoding]::new($false).GetString($publishedBytes)
    Assert-Check ($publishedText -ceq $canonicalText) 'published manifest bytes differ from canonical serializer output'
    $publishedWire = $publishedText | ConvertFrom-Json -Depth 20
    $publishedAcceptedText = (Convert-TrustedManifestToCanonicalObject -Manifest $publishedWire) | ConvertTo-Json -Depth 20 -Compress
    Assert-Check ($publishedText -ceq $publishedAcceptedText) 'published manifest differs from accepted consumer serializer'
    $successReport = Get-Content -LiteralPath (Join-Path $script:runDir 'run-report.json') -Raw | ConvertFrom-Json -Depth 20
    Assert-Check ($successReport.status -ceq 'PASS') 'successful run report is not PASS'
    Assert-Check ($successReport.manifestSha256 -ceq (Get-Sha256Text -Text $canonicalText)) 'successful run report hash differs from published bytes'
    $postCommitHash = Get-Sha256BytesHex -Bytes $publishedBytes
    $postCommitResult = Handle-ProducerException -FailureMessage 'synthetic post-commit diagnostic'
    Assert-Check (-not $postCommitResult) 'post-commit diagnostic requested a failing process result'
    Assert-Check ($script:report.status -ceq 'PASS' -and $null -eq $script:report.failure) 'post-commit diagnostic downgraded committed success'
    Assert-Check ((Get-Sha256BytesHex -Bytes ([IO.File]::ReadAllBytes($script:manifestPath))) -ceq $postCommitHash) 'post-commit diagnostic changed the committed manifest'

    # Each injected fallible step is exercised through the production process,
    # including initialization, top-level catch/Handle-ProducerException and
    # exit 1. Direct helper checks above remain useful, but cannot substitute
    # for this process-level proof.
    $processFixtureRoot = New-PureOwnedRoot -Parent $PSScriptRoot -Name "pure-process-fixtures-$($script:pureRunId)"
    $processFixtureCompleted = $false
    $faultResults = [System.Collections.Generic.List[string]]::new()

    # An occupied path from an interrupted/foreign run is never reused. The
    # sentinel is intentionally kept in the current owned parent so its bytes
    # can be checked without touching any pre-existing workspace path.
    $occupiedCaseRoot = New-PureOwnedSubdirectory -Root $processFixtureRoot -RelativePath 'process-case-manifestHash'
    $occupiedUnionRoot = New-PureOwnedSubdirectory -Root $processFixtureRoot -RelativePath 'process-case-manifestHash/union'
    $occupiedSentinelPath = Join-Path $occupiedUnionRoot 'foreign-sentinel'
    [IO.File]::WriteAllText($occupiedSentinelPath, 'occupied process fixture must remain byte-identical', [Text.UTF8Encoding]::new($false))
    $occupiedSentinelHash = Get-Sha256Hex -Path $occupiedSentinelPath
    $occupiedRejected = $false
    try { New-PureOwnedRoot -Parent $processFixtureRoot -Name 'process-case-manifestHash' | Out-Null } catch { $occupiedRejected = $true }
    Assert-Check $occupiedRejected 'occupied process fixture path was reused'
    Assert-Check ((Get-Sha256Hex -Path $occupiedSentinelPath) -ceq $occupiedSentinelHash) 'occupied process fixture sentinel changed'

    $occupiedLogPath = Join-Path $processLogRoot 'foreign-sentinel.log'
    Write-PureOwnedNewText -Root $processLogRoot -Path $occupiedLogPath -Text 'occupied log must remain byte-identical'
    $occupiedLogHash = Get-Sha256Hex -Path $occupiedLogPath
    $occupiedLogRejected = $false
    try { Write-PureOwnedNewText -Root $processLogRoot -Path $occupiedLogPath -Text 'overwrite must be rejected' } catch { $occupiedLogRejected = $true }
    Assert-Check $occupiedLogRejected 'occupied process log path was overwritten'
    Assert-Check ((Get-Sha256Hex -Path $occupiedLogPath) -ceq $occupiedLogHash) 'occupied process log changed'

    foreach ($faultPoint in @('manifestTempWrite', 'manifestTempRead', 'manifestHash', 'runreportWriteAllText', 'manifestRename')) {
        $caseRoot = New-PureOwnedRoot -Parent $processFixtureRoot -Name "case-$faultPoint"
        $childUnionRoot = New-PureOwnedSubdirectory -Root $caseRoot -RelativePath 'union'
        $childRunsRoot = Join-Path $caseRoot 'runs'
        $childManifestPath = Join-Path $caseRoot 'published-manifest.json'
        $processLogPrefix = Join-Path $processLogRoot "process-fault-$faultPoint-$($script:pureRunId)"
        $processResult = Invoke-ProducerProcess -FaultPoint $faultPoint -UnionRepo $childUnionRoot -ManifestPath $childManifestPath -RunsRoot $childRunsRoot -LogRoot $processLogRoot -LogPrefix $processLogPrefix
        Assert-Check ($processResult.ExitCode -eq 1) "fault process did not exit 1: $faultPoint (exit $($processResult.ExitCode))"
        Assert-Check ($processResult.Stderr -match [regex]::Escape("injected I6 producer failure at $faultPoint")) "fault process stderr did not preserve original error: $faultPoint"
        Assert-Check (-not (Test-Path -LiteralPath $childManifestPath)) "fault process published a manifest: $faultPoint"
        $runDirectories = @(Get-ChildItem -LiteralPath $childRunsRoot -Directory -Force)
        Assert-Check ($runDirectories.Count -eq 1) "fault process run directory count is not one: $faultPoint"
        $childRunDir = $runDirectories[0].FullName
        $failureReportPath = Join-Path $childRunDir 'run-report.json'
        Assert-Check (Test-Path -LiteralPath $failureReportPath -PathType Leaf) "fault process did not emit a FAIL report: $faultPoint"
        $failureReport = Get-Content -LiteralPath $failureReportPath -Raw | ConvertFrom-Json -Depth 20
        Assert-Check ($failureReport.status -ceq 'FAIL') "fault report is not FAIL: $faultPoint"
        Assert-Check ($null -eq $failureReport.manifestSha256) "fault report retained a manifest hash: $faultPoint"
        Assert-Check ($failureReport.failure.message -ceq "injected I6 producer failure at $faultPoint") "fault report did not preserve the original error: $faultPoint"
        $temporaryManifestPath = Join-Path $childRunDir 'requests-build-manifest.v1.json.tmp'
        Assert-Check (-not (Test-Path -LiteralPath $temporaryManifestPath)) "owned temporary manifest was not removed: $faultPoint"
        $faultResults.Add("$faultPoint=PASS; childExit=$($processResult.ExitCode); originalError=$($failureReport.failure.message)")

        Remove-PureOwnedRoot -Root $caseRoot
    }
    $processFixtureCompleted = $true

    # A pre-existing destination remains byte-identical; no publication path
    # is allowed to overwrite a foreign file.
    $foreignRoot = New-PureOwnedRoot -Parent $publicationFixtureRoot -Name 'foreign-destination'
    Initialize-PublicationFixture -Root $foreignRoot
    $foreignBytes = [Text.UTF8Encoding]::new($false).GetBytes('foreign destination sentinel')
    [IO.File]::WriteAllBytes($script:manifestPath, $foreignBytes)
    $foreignHash = Get-Sha256BytesHex -Bytes $foreignBytes
    $foreignThrown = $false
    try {
        Write-TrustedManifest -Manifest $canonicalObject
    }
    catch {
        $foreignThrown = $true
    }
    Assert-Check $foreignThrown 'pre-existing manifest destination was accepted'
    Assert-Check ((Get-Sha256BytesHex -Bytes ([IO.File]::ReadAllBytes($script:manifestPath))) -ceq $foreignHash) 'pre-existing manifest destination was overwritten'
    Assert-Check (-not $script:manifestCommitted) 'pre-existing manifest destination crossed the commit point'
}
finally {
    Remove-PureOwnedRoot -Root $publicationFixtureRoot
    if ($processFixtureCompleted) {
        Remove-PureOwnedRoot -Root $processFixtureRoot
    }
    else {
        Write-Warning "preserving incomplete pure process fixture root: $processFixtureRoot"
    }
}

Write-Output ('I6 publication fault matrix: ' + ($faultResults -join ', '))
Write-Output 'I6 post-commit diagnostic: PASS (warning-only; committed manifest and PASS state remained authoritative)'
Write-Output "I6 process raw logs preserved under: $processLogRoot"
Write-Output 'I6 producer pure check: PASS (producer AST, accepted manifest serializer parity, canonical PWA digest, required build flags, empty/stale output handling, pre-build ancestor/junction confinement, process-level publication failures, no-overwrite destination protection, and report/hash semantics verified; no product build command executed)'
