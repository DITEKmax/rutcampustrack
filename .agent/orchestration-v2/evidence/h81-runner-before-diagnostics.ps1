#requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$UnionRepo,
    [string]$ExpectedUnionRevision = '',
    [string]$BuildManifestPath = '',
    [string]$BuildManifestSha256 = '',
    [switch]$ValidateOnly,
    [switch]$NetworkProof,
    [string]$NetworkProofSubnet = '',
    [string]$NetworkProofEvidencePath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:taskRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$script:unionRoot = $null
$script:preflightReportPath = Join-Path $script:taskRoot 'preflight-failure.json'
$script:runnerPath = Join-Path $script:taskRoot 'runner.ps1'
$script:probePath = Join-Path $script:taskRoot 'probe.mjs'
$script:pwaDistPath = $null
$script:acceptedBrowserRoot = Join-Path $script:taskRoot '..\browser-runtime'
$script:acceptedLiveRoot = Join-Path $script:taskRoot '..\runtime-live'
$script:seedAcademicPath = Join-Path $script:acceptedLiveRoot 'seed-academic.sql'
$script:seedSchedulePath = Join-Path $script:acceptedBrowserRoot 'seed-schedule.sql'
$script:validatedEdgeSources = $null
$script:validatedSourceAnchor = $null
$script:artifactSnapshotRoot = $null
$script:artifactSnapshot = $null
$script:runId = $null
$script:runDir = $null
$script:keysDir = $null
$script:networkName = $null
$script:subnet = $null
$script:ownedContainers = [System.Collections.Generic.List[string]]::new()
$script:ownedContainerNames = [System.Collections.Generic.List[string]]::new()
$script:services = [ordered]@{}
$script:runtimeSecrets = @()
$script:lastAuthLoginAt = $null
$script:authPacingRecords = [System.Collections.Generic.List[object]]::new()
$script:ownedNetwork = $null
$script:currentPhase = 'source-validation'
$script:processExitCode = 0
$script:sourceValidationMessage = $null
$script:report = [ordered]@{
    schema = 'rct.student-requests-runtime.v1'
    status = 'NOT_RUN'
    runtime = 'NOT_RUN'
    scope = 'S3 Requests real Nginx -> Gateway -> Mobile BFF -> Attendance harness'
    runId = $null
    unionRevision = $null
    source = [ordered]@{}
    sourceValidation = [ordered]@{}
    images = [ordered]@{}
    jars = [ordered]@{}
    ports = [ordered]@{
        edge = 18514
        gatewayDiagnostics = 18515
        bffDiagnostics = 18516
        attendanceDiagnostics = 18517
        authDiagnostics = 18518
        scheduleDiagnostics = 18519
        academicDiagnostics = 18520
    }
    commands = [System.Collections.Generic.List[object]]::new()
    probeDiagnosticsHistory = [System.Collections.Generic.List[object]]::new()
    environment = [ordered]@{
        hostOs = [Environment]::OSVersion.VersionString
        powershell = $PSVersionTable.PSVersion.ToString()
        taskRoot = $script:taskRoot
        unionRepo = $script:unionRoot
        pwaDist = $script:pwaDistPath
        edgeOrigin = 'https://127.0.0.1:18514'
        runtimeMode = if ($ValidateOnly) { 'ValidateOnly' } else { 'full-runtime' }
        secrets = 'redacted; no ambient credentials read'
    }
    phases = [System.Collections.Generic.List[object]]::new()
    network = [ordered]@{}
    fixture = [ordered]@{}
    assertions = [ordered]@{}
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
    limitations = @(
        'This leaf does not claim runtime PASS; a full invocation records Docker, edge, I1 and I2 evidence for the root-owned runtime lease.',
        'BFF exposes actuator metrics but no Prometheus registry; I2 uses /actuator/metrics/http.server.requests with an exact URI tag.',
        'The accepted Academic and Schedule SQL seeds are read-only references outside this scope.'
    )
}

function Assert-That {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Test-ReportHasFailure {
    if ($script:report -is [System.Collections.IDictionary]) {
        return @($script:report.Keys) -contains 'failure'
    }
    return $script:report.PSObject.Properties.Name -contains 'failure'
}

function Add-PhaseDiagnostic {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][ValidateSet('START', 'PASS', 'FAIL', 'NOT_RUN')][string]$Status,
        [string]$Message = ''
    )
    $script:report.phases.Add([ordered]@{
            name = $Name
            status = $Status
            message = Protect-ReportText $Message
            at = [DateTimeOffset]::UtcNow.ToString('o')
        })
}

function Add-CommandEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$ArgumentList = @(),
        [Parameter(Mandatory = $true)][string]$Purpose,
        [Parameter(Mandatory = $true)][int]$ExitCode,
        [switch]$AllowFailure
    )
    $displayParts = @($FilePath) + @($ArgumentList | ForEach-Object {
            $value = [string]$_
            if ($value -match '\s') { '"' + $value.Replace('"', '\"') + '"' } else { $value }
        })
    $display = Protect-ReportText ($displayParts -join ' ')
    $script:report.commands.Add([ordered]@{
            at = [DateTimeOffset]::UtcNow.ToString('o')
            phase = $script:currentPhase
            command = $display
            purpose = $Purpose
            exitCode = $ExitCode
            allowFailure = [bool]$AllowFailure
            secrets = 'redacted'
        })
}

function Protect-ReportText {
    param([AllowNull()][string]$Text)
    if ($null -eq $Text) { return $null }
    $safe = $Text
    foreach ($secret in @($script:runtimeSecrets)) {
        if (-not [string]::IsNullOrEmpty($secret)) { $safe = $safe.Replace($secret, '<redacted>') }
    }
    $safe = [regex]::Replace($safe, '(?i)Bearer\s+[A-Za-z0-9._~+/=-]+', 'Bearer <redacted>')
    $safe = [regex]::Replace($safe, '(?i)("?(?:password|token|secret|authorization|cookie|set-cookie|accessToken|refreshToken)"?\s*[:=]\s*)"(?:\\.|[^"\\])*"', '$1"<redacted>"')
    $safe = [regex]::Replace($safe, '(?i)("?(?:password|token|secret|authorization|cookie|set-cookie|accessToken|refreshToken)"?\s*[:=]\s*)(?!")([^,;\s}\]]+)', '$1<redacted>')
    return $safe
}

function Get-Sha256Hex {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-Sha256BytesHex {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function Get-Sha256Text {
    param([Parameter(Mandatory = $true)][string]$Text)
    $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
    $hash = [Security.Cryptography.SHA256]::HashData($bytes)
    return [Convert]::ToHexString($hash).ToLowerInvariant()
}

function Invoke-ExternalSafe {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$ArgumentList = @(),
        [string]$Purpose = 'external command',
        [int]$TimeoutSeconds = 0,
        [switch]$AllowFailure
    )
    if ($TimeoutSeconds -gt 0) {
        $startInfo = [Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = $FilePath
        $startInfo.UseShellExecute = $false
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        foreach ($argument in $ArgumentList) { $null = $startInfo.ArgumentList.Add([string]$argument) }
        $process = [Diagnostics.Process]::new()
        $process.StartInfo = $startInfo
        $started = $false
        $stdoutTask = $null
        $stderrTask = $null
        $drainWaitMilliseconds = [Math]::Max(1000, [Math]::Min(5000, $TimeoutSeconds * 1000))
        try {
            $null = $process.Start()
            $started = $true
            # Start both drains before waiting for process completion. A child
            # may fill either redirected pipe while it is still running.
            $stdoutTask = $process.StandardOutput.ReadToEndAsync()
            $stderrTask = $process.StandardError.ReadToEndAsync()
            if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
                try { $process.Kill($true) } catch { }
                try { $null = $process.WaitForExit($drainWaitMilliseconds) } catch { }
                if ($null -ne $stdoutTask -and -not $stdoutTask.IsCompleted) { $null = $stdoutTask.Wait($drainWaitMilliseconds) }
                if ($null -ne $stderrTask -and -not $stderrTask.IsCompleted) { $null = $stderrTask.Wait($drainWaitMilliseconds) }
                if ($null -ne $stdoutTask -and -not $stdoutTask.IsCompleted) { try { $process.StandardOutput.Close() } catch { }; $null = $stdoutTask.Wait(250) }
                if ($null -ne $stderrTask -and -not $stderrTask.IsCompleted) { try { $process.StandardError.Close() } catch { }; $null = $stderrTask.Wait(250) }
                Add-CommandEvidence -FilePath $FilePath -ArgumentList $ArgumentList -Purpose $Purpose -ExitCode 124 -AllowFailure:$AllowFailure
                if (-not $AllowFailure) { throw "$Purpose timed out after $TimeoutSeconds seconds" }
                return [pscustomobject]@{ ExitCode = 124; Output = 'external command timed out'; Stdout = ''; Stderr = '' }
            }
            $stdoutDrained = $stdoutTask.Wait($drainWaitMilliseconds)
            $stderrDrained = $stderrTask.Wait($drainWaitMilliseconds)
            if (-not ($stdoutDrained -and $stderrDrained)) {
                if (-not $stdoutDrained) { try { $process.StandardOutput.Close() } catch { }; $null = $stdoutTask.Wait(250) }
                if (-not $stderrDrained) { try { $process.StandardError.Close() } catch { }; $null = $stderrTask.Wait(250) }
                try { if (-not $process.HasExited) { $process.Kill($true) } } catch { }
                Add-CommandEvidence -FilePath $FilePath -ArgumentList $ArgumentList -Purpose $Purpose -ExitCode 124 -AllowFailure:$AllowFailure
                if (-not $AllowFailure) { throw "$Purpose output drain timed out after $drainWaitMilliseconds milliseconds" }
                return [pscustomobject]@{ ExitCode = 124; Output = 'external command output drain timed out'; Stdout = ''; Stderr = '' }
            }
            $stdout = [string]$stdoutTask.GetAwaiter().GetResult()
            $stderr = [string]$stderrTask.GetAwaiter().GetResult()
            $output = @($stdout, $stderr) -join [Environment]::NewLine
            $exitCode = $process.ExitCode
        } finally {
            if ($started) {
                try {
                    if (-not $process.HasExited) { $process.Kill($true) }
                } catch { }
                try { $null = $process.WaitForExit($drainWaitMilliseconds) } catch { }
            }
            $process.Dispose()
        }
    } else {
        $output = @(& $FilePath @ArgumentList 2>&1) -join [Environment]::NewLine
        $exitCode = $LASTEXITCODE
    }
    Add-CommandEvidence -FilePath $FilePath -ArgumentList $ArgumentList -Purpose $Purpose -ExitCode $exitCode -AllowFailure:$AllowFailure
    if (-not $AllowFailure -and $exitCode -ne 0) {
        throw "$Purpose failed with exit code $exitCode"
    }
    if ($TimeoutSeconds -gt 0) {
        return [pscustomobject]@{ ExitCode = $exitCode; Output = ($output -join [Environment]::NewLine); Stdout = [string]$stdout; Stderr = [string]$stderr }
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Output = ($output -join [Environment]::NewLine) }
}

function Invoke-DockerSafe {
    param(
        [Parameter(Mandatory = $true)][string[]]$DockerArgs,
        [string]$Purpose = 'Docker command',
        [switch]$AllowFailure
    )
    return Invoke-ExternalSafe -FilePath 'docker' -ArgumentList $DockerArgs -Purpose $Purpose -AllowFailure:$AllowFailure
}

function Write-ReportFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    $json = $script:report | ConvertTo-Json -Depth 12
    [IO.File]::WriteAllText($Path, (Protect-ReportText $json), [Text.Encoding]::UTF8)
}

function Get-UnionRevision {
    $result = Invoke-ExternalSafe -FilePath 'git' -ArgumentList @('-c', "safe.directory=$($script:unionRoot)", '-C', $script:unionRoot, 'rev-parse', 'HEAD') -Purpose 'resolve union revision'
    $revision = $result.Output.Trim()
    Assert-That ($revision -match '^[0-9a-f]{40}$') 'union revision must be a full SHA-1'
    return $revision
}

function Get-UnionTrackedStatus {
    $result = Invoke-ExternalSafe -FilePath 'git' -ArgumentList @(
        '-c', "safe.directory=$($script:unionRoot)", '-C', $script:unionRoot,
        'status', '--porcelain=v1', '--untracked-files=all'
    ) -Purpose 'verify union tracked source is clean'
    return [string]$result.Output
}

function Get-UnionTrackedBlobHash {
    param([Parameter(Mandatory = $true)][string]$RelativePath)
    Assert-That ($RelativePath -notmatch '[\\:]' -and -not $RelativePath.StartsWith('/') -and $RelativePath -notmatch '(^|/)\.\.(/|$)') "invalid tracked source path: $RelativePath"
    $result = Invoke-ExternalSafe -FilePath 'git' -ArgumentList @(
        '-c', "safe.directory=$($script:unionRoot)", '-C', $script:unionRoot,
        'rev-parse', "$ExpectedUnionRevision`:$RelativePath"
    ) -Purpose "resolve exact revision blob for $RelativePath"
    $blob = $result.Output.Trim()
    Assert-That ($blob -match '^[0-9a-f]{40}$') "tracked source blob is not a full SHA-1: $RelativePath"
    return $blob
}

function Get-GitBlobHashForBytes {
    param(
        [Parameter(Mandatory = $true)][byte[]]$Bytes,
        [Parameter(Mandatory = $true)][string]$Context,
        [string]$RelativePathForGit = ''
    )
    Assert-That ($null -ne $script:unionRoot) "$Context requires a resolved UnionRepo"
    if (-not [string]::IsNullOrWhiteSpace($RelativePathForGit)) {
        Assert-That ($RelativePathForGit -notmatch '[\\:]' -and -not $RelativePathForGit.StartsWith('/') -and $RelativePathForGit -notmatch '(^|/)\.\.(/|$)') "$Context has an invalid Git path"
    }
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'git'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    # Feed the exact cached bytes to Git. --path applies the same clean
    # filter used by the tracked source blob (including Windows CRLF
    # normalization), so the resulting object id is comparable to HEAD:path.
    $gitArguments = [System.Collections.Generic.List[string]]::new()
    foreach ($argument in @('-c', "safe.directory=$($script:unionRoot)", '-C', $script:unionRoot, 'hash-object')) {
        [void]$gitArguments.Add([string]$argument)
    }
    if (-not [string]::IsNullOrWhiteSpace($RelativePathForGit)) { [void]$gitArguments.Add("--path=$RelativePathForGit") }
    [void]$gitArguments.Add('--stdin')
    foreach ($argument in $gitArguments) {
        $null = $startInfo.ArgumentList.Add([string]$argument)
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $stdout = ''
    $stderr = ''
    $exitCode = 1
    try {
        $null = $process.Start()
        $process.StandardInput.BaseStream.Write($Bytes, 0, $Bytes.Length)
        $process.StandardInput.Close()
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        $exitCode = $process.ExitCode
    } finally {
        $process.Dispose()
    }
    Add-CommandEvidence -FilePath 'git' -ArgumentList $gitArguments.ToArray() -Purpose $Context -ExitCode $exitCode
    Assert-That ($exitCode -eq 0) "$Context failed with exit code $exitCode"
    Assert-That ([string]::IsNullOrWhiteSpace($stderr)) "$Context wrote unexpected diagnostics"
    $blob = ([string]$stdout).Trim()
    Assert-That ($blob -match '^[0-9a-f]{40}$') "$Context returned an invalid Git blob id"
    return $blob
}

function Assert-UnionSourceStability {
    param([Parameter(Mandatory = $true)][string]$Context)
    Assert-That ($null -ne $script:unionRoot) "$Context requires a resolved UnionRepo"
    Assert-That ($ExpectedUnionRevision -match '^[0-9a-f]{40}$') "$Context requires the exact expected union revision pin"
    $revision = Get-UnionRevision
    Assert-That ($revision -ceq $ExpectedUnionRevision) "$Context found a changed union revision"
    $status = Get-UnionTrackedStatus
    Assert-That ([string]::IsNullOrWhiteSpace($status)) "$Context found a dirty or untracked union worktree"
    if ($null -ne $script:validatedSourceAnchor) {
        Assert-That ($script:validatedSourceAnchor.revision -ceq $revision) "$Context does not match the validated source revision"
        Assert-That ($script:validatedSourceAnchor.trackedStatus -eq '') "$Context does not match the validated clean source state"
        foreach ($edge in @(
                [pscustomobject]@{ Name = 'nginx'; Path = $script:validatedEdgeSources.nginxPath; Sha256 = $script:validatedSourceAnchor.nginxSha256; Blob = $script:validatedSourceAnchor.nginxBlob },
                [pscustomobject]@{ Name = 'default'; Path = $script:validatedEdgeSources.defaultPath; Sha256 = $script:validatedSourceAnchor.defaultSha256; Blob = $script:validatedSourceAnchor.defaultBlob }
            )) {
            Assert-That (Test-Path -LiteralPath $edge.Path -PathType Leaf) "$Context source file disappeared: $($edge.Name)"
            Assert-That ((Get-Sha256Hex -Path $edge.Path) -ceq $edge.Sha256) "$Context source bytes changed: $($edge.Name)"
            $relativePath = if ($edge.Name -eq 'nginx') { 'tests/e2e/infra/nginx/nginx.conf' } else { 'tests/e2e/infra/nginx/default.conf' }
            $blob = Get-UnionTrackedBlobHash -RelativePath $relativePath
            Assert-That ($blob -ceq $edge.Blob) "$Context tracked source blob changed: $($edge.Name)"
        }
    }
    return $revision
}

function Get-JarSpec {
    return @(
        [ordered]@{ Name = 'auth'; Relative = 'services/auth-service/auth-app/build/libs'; Pattern = 'auth-app-*.jar' },
        [ordered]@{ Name = 'academic'; Relative = 'services/academic-service/academic-app/build/libs'; Pattern = 'academic-app-*.jar' },
        [ordered]@{ Name = 'schedule'; Relative = 'services/schedule-service/schedule-app/build/libs'; Pattern = 'schedule-app-*.jar' },
        [ordered]@{ Name = 'attendance'; Relative = 'services/attendance-service/attendance-app/build/libs'; Pattern = 'attendance-app-*.jar' },
        [ordered]@{ Name = 'mobileBff'; Relative = 'services/mobile-bff/mobile-bff-app/build/libs'; Pattern = 'mobile-bff-app-*.jar' },
        [ordered]@{ Name = 'gateway'; Relative = 'services/api-gateway/build/libs'; Pattern = 'api-gateway-*.jar' }
    )
}

function Get-PwaDistManifest {
    param([Parameter(Mandatory = $true)][string]$Directory)
    $root = [IO.Path]::GetFullPath($Directory)
    Assert-That (Test-Path -LiteralPath $root -PathType Container) "built PWA dist directory is missing: $root"
    $rootItem = Get-Item -LiteralPath $root -Force
    Assert-That (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'built PWA dist directory is a reparse point'
    foreach ($required in @('index.html', 'sw.js', 'sw-assets.js')) {
        Assert-That (Test-Path -LiteralPath (Join-Path $root $required) -PathType Leaf) "built PWA dist is missing required file: $required"
    }
    $directories = [System.Collections.Generic.Stack[string]]::new()
    $directories.Push($root)
    $fileItems = [System.Collections.Generic.List[IO.FileInfo]]::new()
    while ($directories.Count -gt 0) {
        $currentDirectory = $directories.Pop()
        foreach ($child in @(Get-ChildItem -LiteralPath $currentDirectory -Force)) {
            Assert-That (($child.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "built PWA dist contains a reparse point: $($child.FullName)"
            if ($child.PSIsContainer) { $directories.Push($child.FullName) } else { $fileItems.Add([IO.FileInfo]$child) }
        }
    }
    $files = @(
        $fileItems | ForEach-Object {
            [ordered]@{
                relativePath = [IO.Path]::GetRelativePath($root, $_.FullName).Replace('\', '/')
                bytes = [int64]$_.Length
                sha256 = Get-Sha256Hex -Path $_.FullName
            }
        }
    )
    Assert-That ($files.Count -gt 0) 'built PWA dist contains no files'
    $files = @(Sort-CanonicalPathObjects -Items $files)
    $manifestText = Convert-PwaFilesToCanonicalText -Files $files
    return [ordered]@{
        path = $root
        fileCount = $files.Count
        manifestSha256 = Get-Sha256Text -Text $manifestText
        files = $files
    }
}

function Assert-PwaManifestIdentity {
    param(
        [Parameter(Mandatory = $true)][object]$Expected,
        [Parameter(Mandatory = $true)][object]$Actual,
        [Parameter(Mandatory = $true)][string]$Context
    )
    Assert-That ([int]$Actual.fileCount -eq [int]$Expected.fileCount) "$Context file count changed"
    Assert-That ([string]$Actual.manifestSha256 -ceq [string]$Expected.manifestSha256) "$Context manifest digest changed"
    $expectedFiles = @(Sort-CanonicalPathObjects -Items @($Expected.files))
    $actualFiles = @(Sort-CanonicalPathObjects -Items @($Actual.files))
    Assert-That ($actualFiles.Count -eq $expectedFiles.Count) "$Context file set changed"
    for ($index = 0; $index -lt $expectedFiles.Count; $index++) {
        Assert-That ($actualFiles[$index].relativePath -ceq $expectedFiles[$index].relativePath) "$Context relative path changed: $($expectedFiles[$index].relativePath)"
        Assert-That ([int64]$actualFiles[$index].bytes -eq [int64]$expectedFiles[$index].bytes) "$Context byte count changed: $($expectedFiles[$index].relativePath)"
        Assert-That ([string]$actualFiles[$index].sha256 -ceq [string]$expectedFiles[$index].sha256) "$Context digest changed: $($expectedFiles[$index].relativePath)"
    }
}

function Read-VerifiedArtifactBytes {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][int64]$ExpectedBytes,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256,
        [Parameter(Mandatory = $true)][string]$Context
    )
    Assert-That ($ExpectedBytes -ge 0 -and $ExpectedBytes -le [int32]::MaxValue) "$Context has an unsupported byte count"
    Assert-Sha256Value -Value $ExpectedSha256 -Context "$Context expected SHA-256" | Out-Null
    $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    Assert-That (-not $item.PSIsContainer) "$Context source is not a file"
    Assert-That (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "$Context source is a reparse point"
    Assert-That ([int64]$item.Length -eq $ExpectedBytes) "$Context source size changed before read"
    $stream = $null
    try {
        # FileShare.Read prevents a writer/delete from changing the opened file
        # while the bytes are consumed. The post-read path check catches a
        # replacement that happened before or immediately after this handle.
        $stream = [IO.File]::Open($item.FullName, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
        Assert-That ([int64]$stream.Length -eq $ExpectedBytes) "$Context source size changed at read boundary"
        $bytes = [byte[]]::new([int]$ExpectedBytes)
        $offset = 0
        while ($offset -lt $bytes.Length) {
            $read = $stream.Read($bytes, $offset, $bytes.Length - $offset)
            Assert-That ($read -gt 0) "$Context source ended before the manifest byte count"
            $offset += $read
        }
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
    }
    $actualSha = Get-Sha256BytesHex -Bytes $bytes
    Assert-That ($actualSha -ceq $ExpectedSha256.ToLowerInvariant()) "$Context source bytes do not match the trusted artifact manifest"
    $after = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    Assert-That (-not $after.PSIsContainer) "$Context source changed to a directory after read"
    Assert-That (($after.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "$Context source became a reparse point after read"
    Assert-That ([int64]$after.Length -eq $ExpectedBytes) "$Context source size changed during read"
    Assert-That ((Get-Sha256Hex -Path $after.FullName) -ceq $ExpectedSha256.ToLowerInvariant()) "$Context source changed during read"
    return [pscustomobject]@{ Bytes = $bytes; BytesCount = [int64]$bytes.Length; Sha256 = $actualSha; SourcePath = $after.FullName }
}

function Copy-VerifiedArtifactFile {
    param(
        [Parameter(Mandatory = $true)][string]$SourceRoot,
        [Parameter(Mandatory = $true)][string]$SourceRelativePath,
        [Parameter(Mandatory = $true)][string]$DestinationRoot,
        [Parameter(Mandatory = $true)][string]$DestinationRelativePath,
        [Parameter(Mandatory = $true)][int64]$ExpectedBytes,
        [Parameter(Mandatory = $true)][string]$ExpectedSha256,
        [Parameter(Mandatory = $true)][string]$Context
    )
    $source = Assert-AbsolutePathConfined -Root $SourceRoot -RelativePath $SourceRelativePath -Context "$Context source path"
    $destination = Assert-AbsolutePathConfined -Root $DestinationRoot -RelativePath $DestinationRelativePath -Context "$Context destination path"
    Assert-That (-not (Test-Path -LiteralPath $destination.FullPath)) "$Context destination already exists"
    $parent = Split-Path -Parent $destination.FullPath
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
    $null = Assert-AbsolutePathConfined -Root $DestinationRoot -RelativePath $DestinationRelativePath -Context "$Context destination path after directory creation"
    $read = Read-VerifiedArtifactBytes -Path $source.FullPath -ExpectedBytes $ExpectedBytes -ExpectedSha256 $ExpectedSha256 -Context $Context
    [IO.File]::WriteAllBytes($destination.FullPath, $read.Bytes)
    $destinationItem = Get-Item -LiteralPath $destination.FullPath -Force -ErrorAction Stop
    Assert-That (-not $destinationItem.PSIsContainer) "$Context owned copy is not a file"
    Assert-That (($destinationItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "$Context owned copy is a reparse point"
    Assert-That ([int64]$destinationItem.Length -eq $ExpectedBytes) "$Context owned copy size is wrong"
    Assert-That ((Get-Sha256Hex -Path $destinationItem.FullName) -ceq $ExpectedSha256.ToLowerInvariant()) "$Context owned copy bytes are wrong"
    return [pscustomobject]@{
        Path = $destinationItem.FullName
        File = $destinationItem.Name
        Bytes = [int64]$destinationItem.Length
        Sha256 = $ExpectedSha256.ToLowerInvariant()
        SourcePath = $read.SourcePath
    }
}

function Assert-OwnedArtifactPath {
    Assert-That ($script:runDir -and $script:artifactSnapshotRoot) 'owned artifact snapshot was not initialized'
    $resolvedRun = [IO.Path]::GetFullPath($script:runDir).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $resolvedArtifacts = [IO.Path]::GetFullPath($script:artifactSnapshotRoot).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    Assert-That ($resolvedArtifacts.StartsWith("$resolvedRun$([IO.Path]::DirectorySeparatorChar)", [StringComparison]::OrdinalIgnoreCase)) 'artifact snapshot escaped the owned run directory'
    Assert-That ([IO.Path]::GetFileName($resolvedArtifacts) -eq 'artifacts') 'cleanup target is not the exact owned artifact directory'
    $runItem = Get-Item -LiteralPath $resolvedRun -Force
    Assert-That (($runItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'owned run directory is a reparse point'
    Assert-That (Test-Path -LiteralPath $resolvedArtifacts -PathType Container) 'owned artifact directory is missing'
    $root = Get-Item -LiteralPath $resolvedArtifacts -Force
    Assert-That (($root.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'owned artifact directory is a reparse point'
    $directories = [System.Collections.Generic.Stack[string]]::new()
    $directories.Push($resolvedArtifacts)
    while ($directories.Count -gt 0) {
        $current = $directories.Pop()
        foreach ($child in @(Get-ChildItem -LiteralPath $current -Force)) {
            Assert-That (($child.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "owned artifact snapshot contains a reparse point: $($child.FullName)"
            if ($child.PSIsContainer) { $directories.Push($child.FullName) }
        }
    }
    return $resolvedArtifacts
}

function New-VerifiedArtifactSnapshot {
    Assert-That ($script:runDir -and $script:report.jars -and $script:report.source.pwaDist) 'trusted artifacts are missing before snapshot creation'
    $artifactRoot = [IO.Path]::GetFullPath((Join-Path $script:runDir 'artifacts'))
    $script:artifactSnapshotRoot = $artifactRoot
    Assert-That (-not (Test-Path -LiteralPath $artifactRoot)) 'owned artifact snapshot path already exists'
    New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
    $jarRoot = Join-Path $artifactRoot 'jars'
    $pwaRoot = Join-Path $artifactRoot 'pwa'
    New-Item -ItemType Directory -Path $jarRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $pwaRoot -Force | Out-Null
    Assert-OwnedArtifactPath | Out-Null

    $jarCopies = [ordered]@{}
    foreach ($spec in @(Get-JarSpec)) {
        $trusted = $script:report.jars[$spec.Name]
        Assert-That ($null -ne $trusted) "trusted jar entry is missing: $($spec.Name)"
        $sourceRelative = [string]$trusted.sourceRelativePath
        Assert-That (-not [string]::IsNullOrWhiteSpace($sourceRelative)) "trusted jar source path is missing: $($spec.Name)"
        $sourceDirectory = Assert-AbsolutePathConfined -Root $script:unionRoot -RelativePath $spec.Relative.Replace('\', '/') -Context "trusted $($spec.Name) jar directory"
        $directoryItem = Get-Item -LiteralPath $sourceDirectory.FullPath -Force
        Assert-That (($directoryItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "trusted $($spec.Name) jar directory is a reparse point"
        $executableJars = @(Get-ChildItem -LiteralPath $sourceDirectory.FullPath -File | Where-Object {
                $_.Name -notlike '*-plain.jar' -and $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar'
            })
        Assert-That ($executableJars.Count -eq 1 -and $executableJars[0].FullName -eq [string]$trusted.path) "trusted $($spec.Name) jar changed before snapshot"
        $copy = Copy-VerifiedArtifactFile -SourceRoot $script:unionRoot -SourceRelativePath $sourceRelative -DestinationRoot $jarRoot -DestinationRelativePath ([string]$trusted.file) -ExpectedBytes ([int64]$trusted.bytes) -ExpectedSha256 ([string]$trusted.sha256) -Context "snapshot $($spec.Name) jar"
        $jarCopies[$spec.Name] = [ordered]@{
            path = $copy.Path
            file = $copy.File
            bytes = $copy.Bytes
            sha256 = $copy.Sha256
            sourcePath = [string]$trusted.path
            sourceRelativePath = $sourceRelative
        }
    }

    $trustedPwa = $script:report.source.pwaDist
    $currentPwa = Get-PwaDistManifest -Directory $script:pwaDistPath
    Assert-PwaManifestIdentity -Expected $trustedPwa -Actual $currentPwa -Context 'trusted PWA source before snapshot'
    foreach ($file in @(Sort-CanonicalPathObjects -Items @($trustedPwa.files))) {
        $copy = Copy-VerifiedArtifactFile -SourceRoot $script:pwaDistPath -SourceRelativePath ([string]$file.relativePath) -DestinationRoot $pwaRoot -DestinationRelativePath ([string]$file.relativePath) -ExpectedBytes ([int64]$file.bytes) -ExpectedSha256 ([string]$file.sha256) -Context "snapshot PWA $($file.relativePath)"
    }
    $copiedPwa = Get-PwaDistManifest -Directory $pwaRoot
    Assert-PwaManifestIdentity -Expected $trustedPwa -Actual $copiedPwa -Context 'owned PWA snapshot'

    $script:artifactSnapshot = [pscustomobject]@{ Root = $artifactRoot; Jars = $jarCopies; PwaDist = $copiedPwa }
    $script:report.source.runtimeArtifacts = [ordered]@{
        root = $artifactRoot
        jars = @($jarCopies.GetEnumerator() | ForEach-Object { [ordered]@{ name = $_.Key; path = $_.Value.path; bytes = $_.Value.bytes; sha256 = $_.Value.sha256 } })
        pwaDist = $copiedPwa
        trustedManifestSha256 = [string]$script:report.source.buildManifest.actualSha256
    }
    return $script:artifactSnapshot
}

function Sort-CanonicalPathObjects {
    param([Parameter(Mandatory = $true)][object[]]$Items)
    $sorted = [System.Collections.Generic.List[object]]::new()
    foreach ($item in $Items) { $sorted.Add($item) }
    $sorted.Sort([System.Comparison[object]]{
            param($left, $right)
            [String]::CompareOrdinal([string]$left.relativePath, [string]$right.relativePath)
        })
    return @($sorted)
}

function Convert-PwaFilesToCanonicalText {
    param([Parameter(Mandatory = $true)][object[]]$Files)
    # The digest is SHA-256 over UTF-8 bytes of this compact JSON array. Each
    # entry is sorted by relativePath before this function is called and uses
    # the fixed lexical property order bytes, relativePath, sha256.
    $canonicalFiles = @($Files | ForEach-Object {
            [ordered]@{ bytes = [int64]$_.bytes; relativePath = [string]$_.relativePath; sha256 = [string]$_.sha256 }
        })
    return ConvertTo-Json -InputObject ([object[]]$canonicalFiles) -Depth 6 -Compress
}

function Get-JsonPropertyValue {
    param(
        [Parameter(Mandatory = $true)][object]$Object,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Context
    )
    Assert-That ($Object -is [pscustomobject]) "$Context must be a JSON object"
    $property = $Object.PSObject.Properties[$Name]
    Assert-That ($null -ne $property) "$Context is missing property '$Name'"
    return $property.Value
}

function Assert-JsonProperties {
    param(
        [Parameter(Mandatory = $true)][object]$Object,
        [Parameter(Mandatory = $true)][string[]]$Expected,
        [Parameter(Mandatory = $true)][string]$Context
    )
    Assert-That ($Object -is [pscustomobject]) "$Context must be a JSON object"
    $actual = @($Object.PSObject.Properties | ForEach-Object { [string]$_.Name })
    Assert-That ($actual.Count -eq $Expected.Count) "$Context has unexpected property count"
    foreach ($name in $actual) {
        Assert-That ($Expected -ccontains $name) "$Context has unexpected property '$name'"
    }
}

function Assert-JsonString {
    param([Parameter(Mandatory = $true)][object]$Value, [Parameter(Mandatory = $true)][string]$Context)
    Assert-That ($Value -is [string] -and -not [string]::IsNullOrWhiteSpace([string]$Value)) "$Context must be a non-empty string"
    return [string]$Value
}

function Assert-JsonInteger {
    param([Parameter(Mandatory = $true)][object]$Value, [Parameter(Mandatory = $true)][string]$Context, [switch]$NonNegative)
    Assert-That ($Value -is [int] -or $Value -is [long] -or $Value -is [decimal]) "$Context must be a JSON integer"
    $integer = [int64]$Value
    Assert-That ($integer -eq $Value) "$Context must be an exact integer"
    if ($NonNegative) { Assert-That ($integer -ge 0) "$Context must be non-negative" }
    return $integer
}

function Assert-JsonBoolean {
    param([Parameter(Mandatory = $true)][object]$Value, [Parameter(Mandatory = $true)][string]$Context)
    Assert-That ($Value -is [bool]) "$Context must be a JSON boolean"
    return [bool]$Value
}

function Assert-Sha256Value {
    param([Parameter(Mandatory = $true)][object]$Value, [Parameter(Mandatory = $true)][string]$Context)
    $sha = Assert-JsonString -Value $Value -Context $Context
    Assert-That ($sha -match '^[0-9a-fA-F]{64}$') "$Context must be a 64-character SHA-256 value"
    return $sha.ToLowerInvariant()
}

function Assert-AbsolutePathConfined {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][object]$RelativePath,
        [Parameter(Mandatory = $true)][string]$Context
    )
    $relative = Assert-JsonString -Value $RelativePath -Context $Context
    Assert-That (-not $relative.Contains('\')) "$Context must use '/' separators"
    Assert-That (-not $relative.StartsWith('/') -and $relative -notmatch '^[A-Za-z]:') "$Context must be relative"
    $parts = $relative.Split('/')
    Assert-That (@($parts | Where-Object { [string]::IsNullOrEmpty($_) -or $_ -eq '.' -or $_ -eq '..' }).Count -eq 0) "$Context contains an escaping path segment"
    $rootFull = [IO.Path]::GetFullPath($Root).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    Assert-That (Test-Path -LiteralPath $rootFull -PathType Container) "$Context artifact root is missing"
    $rootItem = Get-Item -LiteralPath $rootFull -Force
    Assert-That (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "$Context artifact root is a reparse point"
    $ancestor = $rootFull
    foreach ($part in $parts) {
        $ancestor = Join-Path $ancestor $part
        if (Test-Path -LiteralPath $ancestor) {
            $ancestorItem = Get-Item -LiteralPath $ancestor -Force
            Assert-That (($ancestorItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "$Context contains a reparse ancestor: $ancestor"
        }
    }
    $combined = [IO.Path]::GetFullPath((Join-Path $rootFull ($relative.Replace('/', [IO.Path]::DirectorySeparatorChar))))
    $prefix = "$rootFull$([IO.Path]::DirectorySeparatorChar)"
    Assert-That ($combined.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) "$Context escapes its artifact root"
    return [pscustomobject]@{ Relative = $relative; FullPath = $combined }
}

function Assert-JsonTimestamp {
    param([Parameter(Mandatory = $true)][object]$Value, [Parameter(Mandatory = $true)][string]$Context)
    $text = Assert-JsonString -Value $Value -Context $Context
    try {
        $parsed = [DateTimeOffset]::Parse($text, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::RoundtripKind)
    } catch {
        throw "$Context must be an ISO-8601 timestamp"
    }
    return $parsed.ToUniversalTime().ToString('o')
}

function Assert-CanonicalPathList {
    param([Parameter(Mandatory = $true)][object[]]$Items, [Parameter(Mandatory = $true)][string]$Context)
    $previous = $null
    foreach ($item in $Items) {
        $path = [string]$item.relativePath
        if ($null -ne $previous) {
            Assert-That ([String]::CompareOrdinal($previous, $path) -lt 0) "$Context must be strictly sorted by relativePath and contain no duplicates"
        }
        $previous = $path
    }
}

function Convert-TrustedManifestToCanonicalObject {
    param([Parameter(Mandatory = $true)][object]$Manifest)
    $source = Get-JsonPropertyValue -Object $Manifest -Name 'source' -Context 'manifest'
    $producer = Get-JsonPropertyValue -Object $Manifest -Name 'producer' -Context 'manifest'
    $artifacts = Get-JsonPropertyValue -Object $Manifest -Name 'artifacts' -Context 'manifest'
    $commands = @(
        Get-JsonPropertyValue -Object $producer -Name 'commands' -Context 'manifest.producer' |
            ForEach-Object {
                [ordered]@{
                    command = [string](Get-JsonPropertyValue -Object $_ -Name 'command' -Context 'manifest.producer.commands[]')
                    exitCode = [int64](Get-JsonPropertyValue -Object $_ -Name 'exitCode' -Context 'manifest.producer.commands[]')
                    finishedAt = [string](Get-JsonPropertyValue -Object $_ -Name 'finishedAt' -Context 'manifest.producer.commands[]')
                    startedAt = [string](Get-JsonPropertyValue -Object $_ -Name 'startedAt' -Context 'manifest.producer.commands[]')
                }
            }
    )
    $environment = Get-JsonPropertyValue -Object $producer -Name 'environment' -Context 'manifest.producer'
    $jars = @(
        Get-JsonPropertyValue -Object $artifacts -Name 'jars' -Context 'manifest.artifacts' |
            ForEach-Object {
                [ordered]@{
                    bytes = [int64](Get-JsonPropertyValue -Object $_ -Name 'bytes' -Context 'manifest.artifacts.jars[]')
                    name = [string](Get-JsonPropertyValue -Object $_ -Name 'name' -Context 'manifest.artifacts.jars[]')
                    relativePath = [string](Get-JsonPropertyValue -Object $_ -Name 'relativePath' -Context 'manifest.artifacts.jars[]')
                    sha256 = [string](Get-JsonPropertyValue -Object $_ -Name 'sha256' -Context 'manifest.artifacts.jars[]')
                }
            }
    )
    $pwa = Get-JsonPropertyValue -Object $artifacts -Name 'pwaDist' -Context 'manifest.artifacts'
    $pwaFiles = @(
        Get-JsonPropertyValue -Object $pwa -Name 'files' -Context 'manifest.artifacts.pwaDist' |
            ForEach-Object {
                [ordered]@{
                    bytes = [int64](Get-JsonPropertyValue -Object $_ -Name 'bytes' -Context 'manifest.artifacts.pwaDist.files[]')
                    relativePath = [string](Get-JsonPropertyValue -Object $_ -Name 'relativePath' -Context 'manifest.artifacts.pwaDist.files[]')
                    sha256 = [string](Get-JsonPropertyValue -Object $_ -Name 'sha256' -Context 'manifest.artifacts.pwaDist.files[]')
                }
            }
    )
    return [ordered]@{
        artifacts = [ordered]@{
            jars = $jars
            pwaDist = [ordered]@{
                files = $pwaFiles
                manifestSha256 = [string](Get-JsonPropertyValue -Object $pwa -Name 'manifestSha256' -Context 'manifest.artifacts.pwaDist')
                relativeRoot = [string](Get-JsonPropertyValue -Object $pwa -Name 'relativeRoot' -Context 'manifest.artifacts.pwaDist')
            }
        }
        producer = [ordered]@{
            commands = $commands
            environment = [ordered]@{
                gradle = [string](Get-JsonPropertyValue -Object $environment -Name 'gradle' -Context 'manifest.producer.environment')
                hostOs = [string](Get-JsonPropertyValue -Object $environment -Name 'hostOs' -Context 'manifest.producer.environment')
                java = [string](Get-JsonPropertyValue -Object $environment -Name 'java' -Context 'manifest.producer.environment')
                node = [string](Get-JsonPropertyValue -Object $environment -Name 'node' -Context 'manifest.producer.environment')
                powershell = [string](Get-JsonPropertyValue -Object $environment -Name 'powershell' -Context 'manifest.producer.environment')
            }
        }
        schemaVersion = [int](Get-JsonPropertyValue -Object $Manifest -Name 'schemaVersion' -Context 'manifest')
        source = [ordered]@{
            absoluteRepo = [string](Get-JsonPropertyValue -Object $source -Name 'absoluteRepo' -Context 'manifest.source')
            cleanAfterBuild = [bool](Get-JsonPropertyValue -Object $source -Name 'cleanAfterBuild' -Context 'manifest.source')
            cleanBeforeBuild = [bool](Get-JsonPropertyValue -Object $source -Name 'cleanBeforeBuild' -Context 'manifest.source')
            revision = [string](Get-JsonPropertyValue -Object $source -Name 'revision' -Context 'manifest.source')
        }
    }
}

function Resolve-TrustedBuildArtifacts {
    Assert-That (-not [string]::IsNullOrWhiteSpace($BuildManifestPath)) 'trusted build manifest path is required'
    Assert-That ($BuildManifestSha256 -match '^[0-9a-fA-F]{64}$') 'trusted build manifest SHA-256 pin is required'
    Assert-That (Test-Path -LiteralPath $BuildManifestPath -PathType Leaf) "trusted build manifest is missing: $BuildManifestPath"
    $manifestItem = Get-Item -LiteralPath $BuildManifestPath -Force
    Assert-That (($manifestItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'trusted build manifest is a reparse point'
    $manifestPath = $manifestItem.FullName
    # Read exactly one immutable byte buffer. The pin and JSON are both
    # derived from this buffer, so a file swap cannot split hash and parse.
    $bytes = [IO.File]::ReadAllBytes($manifestPath)
    $manifestHash = Get-Sha256BytesHex -Bytes $bytes
    $pinnedHash = $BuildManifestSha256.ToLowerInvariant()
    Assert-That ($manifestHash -eq $pinnedHash) "trusted build manifest SHA-256 does not match the main pin: $manifestPath"
    $script:report.source.buildManifest = [ordered]@{
        path = $manifestPath
        pinnedSha256 = $pinnedHash
        actualSha256 = $manifestHash
        schemaVersion = $null
        sourceRevision = $null
        sourceCleanBeforeBuild = $false
        sourceCleanAfterBuild = $false
        producerCommands = @()
        producerEnvironment = @{}
    }

    Assert-That ($bytes.Length -ge 2 -and -not ($bytes.Length -ge 3 -and $bytes[0] -eq 0xef -and $bytes[1] -eq 0xbb -and $bytes[2] -eq 0xbf)) 'trusted build manifest must be UTF-8 without a BOM'
    $utf8 = [Text.UTF8Encoding]::new($false, $true)
    try { $manifestText = $utf8.GetString($bytes) } catch { throw 'trusted build manifest is not valid UTF-8' }
    Assert-That (-not $manifestText.Contains("`r") -and -not $manifestText.Contains("`n")) 'trusted build manifest must not contain CR/LF; canonical UTF-8 JSON is compact and has no trailing newline'
    try { $manifest = $manifestText | ConvertFrom-Json -Depth 20 } catch { throw "trusted build manifest is not valid JSON: $($_.Exception.Message)" }
    Assert-JsonProperties -Object $manifest -Expected @('artifacts', 'producer', 'schemaVersion', 'source') -Context 'manifest'
    Assert-That ((Assert-JsonInteger -Value (Get-JsonPropertyValue -Object $manifest -Name 'schemaVersion' -Context 'manifest') -Context 'manifest.schemaVersion') -eq 1) 'trusted build manifest schemaVersion must be 1'

    $source = Get-JsonPropertyValue -Object $manifest -Name 'source' -Context 'manifest'
    Assert-JsonProperties -Object $source -Expected @('absoluteRepo', 'cleanAfterBuild', 'cleanBeforeBuild', 'revision') -Context 'manifest.source'
    $manifestRepo = Assert-JsonString -Value (Get-JsonPropertyValue -Object $source -Name 'absoluteRepo' -Context 'manifest.source') -Context 'manifest.source.absoluteRepo'
    Assert-That ([IO.Path]::IsPathFullyQualified($manifestRepo)) 'trusted build manifest source.absoluteRepo must be absolute'
    $manifestRepoFull = [IO.Path]::GetFullPath($manifestRepo).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $unionFull = [IO.Path]::GetFullPath($script:unionRoot).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    Assert-That ($manifestRepoFull.Equals($unionFull, [StringComparison]::OrdinalIgnoreCase)) 'trusted build manifest source.absoluteRepo does not match UnionRepo'
    $manifestRevision = Assert-JsonString -Value (Get-JsonPropertyValue -Object $source -Name 'revision' -Context 'manifest.source') -Context 'manifest.source.revision'
    Assert-That ($manifestRevision -match '^[0-9a-f]{40}$') 'trusted build manifest source.revision must be a full lowercase SHA-1'
    Assert-That ($manifestRevision -eq $script:report.unionRevision) 'trusted build manifest revision does not match the exact union revision'
    Assert-That (Assert-JsonBoolean -Value (Get-JsonPropertyValue -Object $source -Name 'cleanBeforeBuild' -Context 'manifest.source') -Context 'manifest.source.cleanBeforeBuild') 'trusted build manifest source was not clean before build'
    Assert-That (Assert-JsonBoolean -Value (Get-JsonPropertyValue -Object $source -Name 'cleanAfterBuild' -Context 'manifest.source') -Context 'manifest.source.cleanAfterBuild') 'trusted build manifest source was not clean after build'

    $producer = Get-JsonPropertyValue -Object $manifest -Name 'producer' -Context 'manifest'
    Assert-JsonProperties -Object $producer -Expected @('commands', 'environment') -Context 'manifest.producer'
    $commandsValue = Get-JsonPropertyValue -Object $producer -Name 'commands' -Context 'manifest.producer'
    $commands = @($commandsValue)
    Assert-That ($commands.Count -gt 0) 'trusted build manifest must record at least one build command'
    $commandReport = [System.Collections.Generic.List[object]]::new()
    foreach ($command in $commands) {
        Assert-JsonProperties -Object $command -Expected @('command', 'exitCode', 'finishedAt', 'startedAt') -Context 'manifest.producer.commands[]'
        $commandText = Assert-JsonString -Value (Get-JsonPropertyValue -Object $command -Name 'command' -Context 'manifest.producer.commands[]') -Context 'manifest.producer.commands[].command'
        Assert-That ($commandText -notmatch '(?i)(password|token|secret|authorization|cookie|credential)') 'trusted build manifest command contains a secret-like marker'
        $exitCode = Assert-JsonInteger -Value (Get-JsonPropertyValue -Object $command -Name 'exitCode' -Context 'manifest.producer.commands[]') -Context 'manifest.producer.commands[].exitCode'
        Assert-That ($exitCode -eq 0) 'trusted build manifest contains a failed build command'
        $startedAt = Assert-JsonTimestamp -Value (Get-JsonPropertyValue -Object $command -Name 'startedAt' -Context 'manifest.producer.commands[]') -Context 'manifest.producer.commands[].startedAt'
        $finishedAt = Assert-JsonTimestamp -Value (Get-JsonPropertyValue -Object $command -Name 'finishedAt' -Context 'manifest.producer.commands[]') -Context 'manifest.producer.commands[].finishedAt'
        Assert-That ([DateTimeOffset]::Parse($finishedAt) -ge [DateTimeOffset]::Parse($startedAt)) 'trusted build manifest command finished before it started'
        $commandReport.Add([ordered]@{ command = $commandText; exitCode = $exitCode; startedAt = $startedAt; finishedAt = $finishedAt })
    }
    $environment = Get-JsonPropertyValue -Object $producer -Name 'environment' -Context 'manifest.producer'
    Assert-JsonProperties -Object $environment -Expected @('gradle', 'hostOs', 'java', 'node', 'powershell') -Context 'manifest.producer.environment'
    $environmentReport = [ordered]@{}
    foreach ($name in @('gradle', 'hostOs', 'java', 'node', 'powershell')) {
        $value = Assert-JsonString -Value (Get-JsonPropertyValue -Object $environment -Name $name -Context 'manifest.producer.environment') -Context "manifest.producer.environment.$name"
        Assert-That ($value -notmatch '(?i)(password|token|secret|authorization|cookie|credential)') "trusted build environment $name contains a secret-like marker"
        $environmentReport[$name] = $value
    }

    $artifacts = Get-JsonPropertyValue -Object $manifest -Name 'artifacts' -Context 'manifest'
    Assert-JsonProperties -Object $artifacts -Expected @('jars', 'pwaDist') -Context 'manifest.artifacts'
    $jarEntries = @((Get-JsonPropertyValue -Object $artifacts -Name 'jars' -Context 'manifest.artifacts'))
    $specs = @(Get-JarSpec)
    Assert-That ($jarEntries.Count -eq $specs.Count) 'trusted build manifest must contain exactly six jar artifacts'
    $expectedNames = @($specs | ForEach-Object { [string]$_.Name })
    $seenNames = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $trustedJars = [ordered]@{}
    foreach ($entry in $jarEntries) {
        Assert-JsonProperties -Object $entry -Expected @('bytes', 'name', 'relativePath', 'sha256') -Context 'manifest.artifacts.jars[]'
        $name = Assert-JsonString -Value (Get-JsonPropertyValue -Object $entry -Name 'name' -Context 'manifest.artifacts.jars[]') -Context 'manifest.artifacts.jars[].name'
        Assert-That ($expectedNames -ccontains $name) "trusted build manifest has an unexpected jar role: $name"
        Assert-That ($seenNames.Add($name)) "trusted build manifest repeats jar role: $name"
        $spec = $specs | Where-Object { $_.Name -ceq $name } | Select-Object -First 1
        $relative = Assert-AbsolutePathConfined -Root $script:unionRoot -RelativePath (Get-JsonPropertyValue -Object $entry -Name 'relativePath' -Context 'manifest.artifacts.jars[]') -Context "manifest.artifacts.jars[$name].relativePath"
        $expectedDirectory = $spec.Relative.Replace('\', '/')
        $fileName = [IO.Path]::GetFileName($relative.Relative)
        $actualDirectory = ([IO.Path]::GetDirectoryName($relative.Relative)).Replace('\', '/')
        Assert-That ($actualDirectory -ceq $expectedDirectory -and $fileName -like $spec.Pattern) "trusted jar path is outside the expected $name bootJar directory"
        Assert-That ($fileName -notlike '*-plain.jar' -and $fileName -notlike '*-sources.jar' -and $fileName -notlike '*-javadoc.jar') "trusted jar $name is not an executable bootJar"
        $jarDirectory = Join-Path $script:unionRoot $spec.Relative
        Assert-That (Test-Path -LiteralPath $jarDirectory -PathType Container) "trusted jar directory is missing: $($spec.Relative)"
        $executableJars = @(Get-ChildItem -LiteralPath $jarDirectory -File | Where-Object {
                $_.Name -notlike '*-plain.jar' -and $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar'
            })
        Assert-That ($executableJars.Count -eq 1 -and $executableJars[0].FullName -eq $relative.FullPath) "trusted jar directory does not contain exactly the manifest jar: $name"
        Assert-That (Test-Path -LiteralPath $relative.FullPath -PathType Leaf) "trusted jar file is missing: $($relative.Relative)"
        $jarItem = Get-Item -LiteralPath $relative.FullPath -Force
        Assert-That (($jarItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "trusted jar is a reparse point: $($relative.Relative)"
        $bytesValue = Assert-JsonInteger -Value (Get-JsonPropertyValue -Object $entry -Name 'bytes' -Context 'manifest.artifacts.jars[]') -Context "manifest.artifacts.jars[$name].bytes" -NonNegative
        $shaValue = Assert-Sha256Value -Value (Get-JsonPropertyValue -Object $entry -Name 'sha256' -Context 'manifest.artifacts.jars[]') -Context "manifest.artifacts.jars[$name].sha256"
        Assert-That ([int64]$jarItem.Length -eq $bytesValue) "trusted jar size changed after the successful build: $name"
        $actualJarSha = Get-Sha256Hex -Path $relative.FullPath
        Assert-That ($actualJarSha -eq $shaValue) "trusted jar hash changed after the successful build: $name"
        $trustedJars[$name] = [ordered]@{ path = $relative.FullPath; sourceRelativePath = $relative.Relative; file = $fileName; sha256 = $actualJarSha; bytes = [int64]$jarItem.Length }
    }
    Assert-That ($seenNames.Count -eq $specs.Count) 'trusted build manifest omitted one or more expected jar roles'
    Assert-CanonicalPathList -Items @($jarEntries | ForEach-Object { [pscustomobject]@{ relativePath = [string](Get-JsonPropertyValue -Object $_ -Name 'relativePath' -Context 'manifest.artifacts.jars[]') } }) -Context 'manifest.artifacts.jars'

    $pwa = Get-JsonPropertyValue -Object $artifacts -Name 'pwaDist' -Context 'manifest.artifacts'
    Assert-JsonProperties -Object $pwa -Expected @('files', 'manifestSha256', 'relativeRoot') -Context 'manifest.artifacts.pwaDist'
    $pwaRoot = Assert-JsonString -Value (Get-JsonPropertyValue -Object $pwa -Name 'relativeRoot' -Context 'manifest.artifacts.pwaDist') -Context 'manifest.artifacts.pwaDist.relativeRoot'
    Assert-That ($pwaRoot -ceq 'frontends/pwa-vue/dist') 'trusted PWA relativeRoot must be frontends/pwa-vue/dist'
    $pwaEntries = @((Get-JsonPropertyValue -Object $pwa -Name 'files' -Context 'manifest.artifacts.pwaDist'))
    Assert-That ($pwaEntries.Count -gt 0) 'trusted build manifest PWA file list is empty'
    $pwaNormalized = [System.Collections.Generic.List[object]]::new()
    foreach ($entry in $pwaEntries) {
        Assert-JsonProperties -Object $entry -Expected @('bytes', 'relativePath', 'sha256') -Context 'manifest.artifacts.pwaDist.files[]'
        $relative = Assert-AbsolutePathConfined -Root $script:pwaDistPath -RelativePath (Get-JsonPropertyValue -Object $entry -Name 'relativePath' -Context 'manifest.artifacts.pwaDist.files[]') -Context 'manifest.artifacts.pwaDist.files[].relativePath'
        Assert-That (Test-Path -LiteralPath $relative.FullPath -PathType Leaf) "trusted PWA file is missing: $($relative.Relative)"
        $fileItem = Get-Item -LiteralPath $relative.FullPath -Force
        Assert-That (($fileItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "trusted PWA file is a reparse point: $($relative.Relative)"
        $bytesValue = Assert-JsonInteger -Value (Get-JsonPropertyValue -Object $entry -Name 'bytes' -Context 'manifest.artifacts.pwaDist.files[]') -Context "manifest.artifacts.pwaDist.files[$($relative.Relative)].bytes" -NonNegative
        $shaValue = Assert-Sha256Value -Value (Get-JsonPropertyValue -Object $entry -Name 'sha256' -Context 'manifest.artifacts.pwaDist.files[]') -Context "manifest.artifacts.pwaDist.files[$($relative.Relative)].sha256"
        Assert-That ([int64]$fileItem.Length -eq $bytesValue) "trusted PWA file size changed after the successful build: $($relative.Relative)"
        $actualSha = Get-Sha256Hex -Path $relative.FullPath
        Assert-That ($actualSha -eq $shaValue) "trusted PWA file hash changed after the successful build: $($relative.Relative)"
        $pwaNormalized.Add([pscustomobject]@{ relativePath = $relative.Relative; bytes = $bytesValue; sha256 = $actualSha })
    }
    Assert-CanonicalPathList -Items @($pwaNormalized) -Context 'manifest.artifacts.pwaDist.files'
    foreach ($required in @('index.html', 'sw.js', 'sw-assets.js')) {
        Assert-That (@($pwaNormalized | Where-Object { $_.relativePath -ceq $required }).Count -eq 1) "trusted PWA file list is missing required file: $required"
    }
    $pwaCanonicalText = Convert-PwaFilesToCanonicalText -Files @($pwaNormalized)
    $pwaDigest = Assert-Sha256Value -Value (Get-JsonPropertyValue -Object $pwa -Name 'manifestSha256' -Context 'manifest.artifacts.pwaDist') -Context 'manifest.artifacts.pwaDist.manifestSha256'
    Assert-That ((Get-Sha256Text -Text $pwaCanonicalText) -eq $pwaDigest) 'trusted PWA file list digest does not match canonical UTF-8 serialization'
    $currentPwa = Get-PwaDistManifest -Directory $script:pwaDistPath
    $currentPwaFiles = @(Sort-CanonicalPathObjects -Items @($currentPwa.files))
    Assert-That ($currentPwaFiles.Count -eq $pwaNormalized.Count) 'PWA file set changed after the successful build'
    for ($index = 0; $index -lt $currentPwaFiles.Count; $index++) {
        Assert-That ($currentPwaFiles[$index].relativePath -ceq $pwaNormalized[$index].relativePath) 'PWA relative file path does not match trusted build manifest'
        Assert-That ([int64]$currentPwaFiles[$index].bytes -eq [int64]$pwaNormalized[$index].bytes) "PWA file size does not match trusted build manifest: $($pwaNormalized[$index].relativePath)"
        Assert-That ($currentPwaFiles[$index].sha256 -eq $pwaNormalized[$index].sha256) "PWA file hash does not match trusted build manifest: $($pwaNormalized[$index].relativePath)"
    }
    Assert-That ($currentPwa.manifestSha256 -eq $pwaDigest) 'current PWA file list digest does not match trusted build manifest'

    $revisionAfterArtifacts = Get-UnionRevision
    Assert-That ($revisionAfterArtifacts -eq $ExpectedUnionRevision) 'union revision changed while trusted artifacts were being verified'

    $canonicalObject = Convert-TrustedManifestToCanonicalObject -Manifest $manifest
    $canonicalText = $canonicalObject | ConvertTo-Json -Depth 20 -Compress
    Assert-That ($manifestText -ceq $canonicalText) 'trusted build manifest is not canonical compact UTF-8 JSON (lexically ordered keys, sorted artifact arrays, no whitespace)'
    $script:report.source.buildManifest.schemaVersion = 1
    $script:report.source.buildManifest.sourceRevision = $manifestRevision
    $script:report.source.buildManifest.sourceCleanBeforeBuild = $true
    $script:report.source.buildManifest.sourceCleanAfterBuild = $true
    $script:report.source.buildManifest.currentRevisionAfterArtifactCheck = $revisionAfterArtifacts
    $script:report.source.buildManifest.producerCommands = @($commandReport)
    $script:report.source.buildManifest.producerEnvironment = $environmentReport
    return [pscustomobject]@{ Jars = $trustedJars; PwaDist = $currentPwa }
}

function Resolve-EdgeSources {
    $nginxPath = Join-Path $script:unionRoot 'tests\e2e\infra\nginx\nginx.conf'
    $defaultPath = Join-Path $script:unionRoot 'tests\e2e\infra\nginx\default.conf'
    Assert-That (Test-Path -LiteralPath $nginxPath -PathType Leaf) 'accepted e2e nginx.conf is missing from union'
    Assert-That (Test-Path -LiteralPath $defaultPath -PathType Leaf) 'accepted e2e default.conf is missing from union'
    # Read each accepted edge source once into an immutable string. Runtime
    # config generation receives these exact validated bytes; it never
    # re-resolves or re-reads the source paths after resources are allocated.
    $nginxBytes = [IO.File]::ReadAllBytes($nginxPath)
    $defaultBytes = [IO.File]::ReadAllBytes($defaultPath)
    $utf8 = [Text.UTF8Encoding]::new($false, $true)
    try {
        $nginx = $utf8.GetString($nginxBytes)
        $default = $utf8.GetString($defaultBytes)
    } catch {
        throw 'accepted e2e edge sources are not valid UTF-8'
    }
    $expectedNginxBlob = Get-UnionTrackedBlobHash -RelativePath 'tests/e2e/infra/nginx/nginx.conf'
    $expectedDefaultBlob = Get-UnionTrackedBlobHash -RelativePath 'tests/e2e/infra/nginx/default.conf'
    $actualNginxBlob = Get-GitBlobHashForBytes -Bytes $nginxBytes -RelativePathForGit 'tests/e2e/infra/nginx/nginx.conf' -Context 'bind cached nginx bytes to expected revision blob'
    $actualDefaultBlob = Get-GitBlobHashForBytes -Bytes $defaultBytes -RelativePathForGit 'tests/e2e/infra/nginx/default.conf' -Context 'bind cached default.conf bytes to expected revision blob'
    Assert-That ($actualNginxBlob -ceq $expectedNginxBlob) 'cached nginx bytes do not match the expected revision blob'
    Assert-That ($actualDefaultBlob -ceq $expectedDefaultBlob) 'cached default.conf bytes do not match the expected revision blob'
    Assert-That ($nginx -match '(?m)^\s*client_max_body_size\s+2m;') 'accepted Nginx global 2m cap is missing'
    Assert-That ($nginx -match '(?s)map\s+\$uri\s+\$student_requests_cache_control.*?student/requests.*?no-store') 'accepted Requests no-store map is missing'
    Assert-That ($default -match '(?s)location\s*=\s*/api/v1/student/requests/excuse\s*\{.*?client_max_body_size\s+24m;.*?proxy_set_header\s+Forwarded\s+"";') 'accepted Requests location or canonical headers are missing'
    Assert-That ($default -notmatch '(?s)location\s*=\s*/api/v1/student/requests/excuse\s*\{.*?proxy_request_buffering\s+off;') 'accepted Requests location unexpectedly disables request buffering'
    Assert-That ($default -match 'listen\s+443\s+ssl;') 'accepted TLS listener is missing'
    return [pscustomobject]@{
        nginxPath = $nginxPath
        nginxSha256 = Get-Sha256BytesHex -Bytes $nginxBytes
        nginxBlob = $expectedNginxBlob
        defaultPath = $defaultPath
        defaultSha256 = Get-Sha256BytesHex -Bytes $defaultBytes
        defaultBlob = $expectedDefaultBlob
        globalMaxBodySize = '2m'
        requestsMaxBodySize = '24m'
        requestBuffering = 'on (inherited default)'
        cacheControl = 'no-store for /api/v1/student/requests and descendants'
        trustedForwardHeaders = 'X-Forwarded-For/Proto/Host/Port overwritten; Forwarded empty'
        nginxText = $nginx
        defaultText = $default
    }
}

function Test-PowerShellSource {
    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($script:runnerPath, [ref]$tokens, [ref]$errors) | Out-Null
    Assert-That ($errors.Count -eq 0) "PowerShell parser reported $($errors.Count) errors"
}

function Test-SourceContract {
    Assert-That (Test-Path -LiteralPath $script:probePath -PathType Leaf) 'probe.mjs is missing'
    Assert-That (Test-Path -LiteralPath $script:seedAcademicPath -PathType Leaf) 'accepted Academic seed is missing'
    Assert-That (Test-Path -LiteralPath $script:seedSchedulePath -PathType Leaf) 'accepted Schedule seed is missing'
    Assert-That (Test-Path -LiteralPath (Join-Path $script:unionRoot 'settings.gradle.kts') -PathType Leaf) 'union Gradle settings are missing'
    Assert-That ((Get-Command node -ErrorAction SilentlyContinue) -ne $null) 'node CLI is required for source checks'
    Test-PowerShellSource
    Assert-That ($ExpectedUnionRevision -match '^[0-9a-f]{40}$') 'expected union revision pin is required'
    $script:report.unionRevision = Get-UnionRevision
    Assert-That ($script:report.unionRevision -eq $ExpectedUnionRevision) 'union revision does not match the main expected revision pin'
    $unionStatusBeforeSources = Get-UnionTrackedStatus
    Assert-That ([string]::IsNullOrWhiteSpace($unionStatusBeforeSources)) 'union worktree is dirty or contains untracked files before source validation'
    $edge = Resolve-EdgeSources
    $script:validatedEdgeSources = $edge
    $script:report.source = [ordered]@{
        nginxPath = $edge.nginxPath
        nginxSha256 = $edge.nginxSha256
        nginxBlob = $edge.nginxBlob
        defaultPath = $edge.defaultPath
        defaultSha256 = $edge.defaultSha256
        defaultBlob = $edge.defaultBlob
        globalMaxBodySize = $edge.globalMaxBodySize
        requestsMaxBodySize = $edge.requestsMaxBodySize
        requestBuffering = $edge.requestBuffering
        cacheControl = $edge.cacheControl
        trustedForwardHeaders = $edge.trustedForwardHeaders
    }
    $script:report.source.acceptedAcademicSeed = [ordered]@{ path = $script:seedAcademicPath; sha256 = Get-Sha256Hex -Path $script:seedAcademicPath }
    $script:report.source.acceptedScheduleSeed = [ordered]@{ path = $script:seedSchedulePath; sha256 = Get-Sha256Hex -Path $script:seedSchedulePath }
    $probe = Get-Content -LiteralPath $script:probePath -Raw
    foreach ($marker in @('https.request', 'Transfer-Encoding', 'Idempotency-Key', 'Cache-Control', 'makeDeterministicPdf', 'makeDeterministicPng', 'validatePng', 'requestError', 'api/auth/session', 'assertRequestDetailContract', 'canonicalAttachmentDescriptor')) {
        Assert-That ($probe.Contains($marker)) "probe marker is missing: $marker"
    }
    Assert-That ($probe -notmatch '\bfetch\s*\(') 'probe must use Node https.request rather than fetch'
    $script:report.source.pinnedImages = Get-PinnedImages
    $trusted = Resolve-TrustedBuildArtifacts
    $script:report.jars = $trusted.Jars
    $script:report.source.pwaDist = $trusted.PwaDist
    $script:validatedSourceAnchor = [ordered]@{
        revision = $script:report.unionRevision
        trackedStatus = ([string]$unionStatusBeforeSources).Trim()
        nginxSha256 = $edge.nginxSha256
        defaultSha256 = $edge.defaultSha256
        nginxBlob = $edge.nginxBlob
        defaultBlob = $edge.defaultBlob
    }
    $script:report.source.sourceAnchor = [ordered]@{
        revision = $script:validatedSourceAnchor.revision
        trackedStatus = 'clean'
        nginxBlob = $script:validatedSourceAnchor.nginxBlob
        nginxSha256 = $script:validatedSourceAnchor.nginxSha256
        defaultBlob = $script:validatedSourceAnchor.defaultBlob
        defaultSha256 = $script:validatedSourceAnchor.defaultSha256
    }
    Assert-UnionSourceStability -Context 'source validation completion' | Out-Null
    return $script:report
}

function New-LocalTlsCertificate {
    param([Parameter(Mandatory = $true)][string]$KeyPath, [Parameter(Mandatory = $true)][string]$CertificatePath)
    $rsa = [Security.Cryptography.RSA]::Create(2048)
    try {
        $request = [Security.Cryptography.X509Certificates.CertificateRequest]::new(
            'CN=127.0.0.1', $rsa, [Security.Cryptography.HashAlgorithmName]::SHA256,
            [Security.Cryptography.RSASignaturePadding]::Pkcs1)
        $san = [Security.Cryptography.X509Certificates.SubjectAlternativeNameBuilder]::new()
        $san.AddIpAddress([Net.IPAddress]::Parse('127.0.0.1'))
        $request.CertificateExtensions.Add($san.Build())
        $certificate = $request.CreateSelfSigned([DateTimeOffset]::UtcNow.AddMinutes(-1), [DateTimeOffset]::UtcNow.AddHours(4))
        [IO.File]::WriteAllText($CertificatePath, $certificate.ExportCertificatePem(), [Text.Encoding]::ASCII)
        [IO.File]::WriteAllText($KeyPath, $rsa.ExportPkcs8PrivateKeyPem(), [Text.Encoding]::ASCII)
    } finally { $rsa.Dispose() }
}

function New-RandomSecret {
    param([int]$Bytes = 32)
    return [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes($Bytes)).Replace('+', '-').Replace('/', '_').TrimEnd('=')
}

function Get-PinnedImages {
    return [ordered]@{
        nginx = 'nginx@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10'
        temurin = 'eclipse-temurin:21-jre@sha256:ad0cdd9782db550ca7dde6939a16fd850d04e683d37d3cff79d84a5848ba6a5a'
        postgres = 'postgres:16@sha256:93d55776e04376e19adb2733e3ccebb4392ee7dd86d8ff238503b30fe719c84f'
        redis = 'redis:7@sha256:8b81dd37ff027bec4e516d41acfbe9fe2460070dc6d4a4570a2ac5b9d59df065'
        rabbitmq = 'rabbitmq:3.13@sha256:606d8c0d6b3c18d1da9afc53bc7cdb2a8d5486df91b5a9830e9e07626c9ae281'
        mongo = 'mongo:7@sha256:45d9c9b48aa1b56b5e3a9f906763fe432f376abb3bc2832438022b6d2534e4fe'
    }
}

function Assert-PinnedImagesPresent {
    $images = Get-PinnedImages
    foreach ($entry in $images.GetEnumerator()) {
        $result = Invoke-DockerSafe -DockerArgs @('image', 'inspect', $entry.Value) -Purpose "inspect pinned $($entry.Key) image" -AllowFailure
        Assert-That ($result.ExitCode -eq 0) "pinned $($entry.Key) image is missing or has a different digest"
    }
    $script:report.images = $images
}

function Invoke-ValidateOnlyChecks {
    $script:currentPhase = 'validate-only-prerequisites'
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'START' -Message 'source paths, Node checks and pinned image presence; no resources are created'
    Assert-That ((Get-Command docker -ErrorAction SilentlyContinue) -ne $null) 'docker CLI is required for ValidateOnly image prerequisite checks'
    Assert-That ((Get-Command node -ErrorAction SilentlyContinue) -ne $null) 'node CLI is required for ValidateOnly source checks'
    Assert-That ($null -eq $script:runDir -and $script:ownedContainers.Count -eq 0 -and $null -eq $script:ownedNetwork) 'ValidateOnly started owned resources before its prerequisite gate'

    $syntax = Invoke-ExternalSafe -FilePath 'node' -ArgumentList @('--check', $script:probePath) -Purpose 'validate Requests Node probe syntax' -AllowFailure
    Assert-That ($syntax.ExitCode -eq 0) 'Node probe syntax check failed'
    $selfTest = Invoke-ExternalSafe -FilePath 'node' -ArgumentList @($script:probePath, '--self-test') -Purpose 'run deterministic Requests source self-test' -AllowFailure
    Assert-That ($selfTest.ExitCode -eq 0) 'deterministic Requests source self-test failed'
    $selfLines = @($selfTest.Output -split '\r?\n' | Where-Object { $_.Trim().Length -gt 0 })
    Assert-That ($selfLines.Count -gt 0) 'deterministic Requests source self-test returned no result'
    $selfJson = $null
    try { $selfJson = $selfLines[-1] | ConvertFrom-Json -Depth 12 } catch { throw 'deterministic Requests source self-test did not return JSON' }
    Assert-That ($selfJson.status -eq 'PASS' -and $selfJson.mode -eq 'source-self-test') 'deterministic Requests source self-test did not report PASS'
    $script:report.source.probeSelfTest = $selfJson

    Assert-PinnedImagesPresent
    $script:report.sourceValidation = [ordered]@{
        nodeSyntax = 'PASS'
        deterministicSelfTest = 'PASS'
        pinnedImages = 'PASS'
        resourcesCreated = $false
    }
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'Node syntax/self-test, trusted build manifest artifacts and pinned image inspections passed without creating runtime resources'
}

function Convert-IPv4ToUInt32 {
    param([string]$Address)
    $parts = $Address.Split('.')
    Assert-That ($parts.Count -eq 4) "invalid IPv4 address: $Address"
    [uint64]$value = 0
    foreach ($part in $parts) {
        $octet = [int]$part
        Assert-That ($octet -ge 0 -and $octet -le 255) "invalid IPv4 octet: $Address"
        $value = ($value -shl 8) -bor [uint64]$octet
    }
    return $value
}

function Test-CidrOverlap {
    param([string]$Candidate, [string]$Existing)
    $octet = '(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])'
    $prefix = '(?:0|[1-9]|[12][0-9]|3[0-2])'
    $cidrPattern = "^(?<address>$octet(?:\.$octet){3})/(?<prefix>$prefix)$"
    $candidateMatch = [regex]::Match(([string]$Candidate).Trim(), $cidrPattern)
    $existingMatch = [regex]::Match(([string]$Existing).Trim(), $cidrPattern)
    Assert-That $candidateMatch.Success "invalid candidate subnet: $Candidate"
    Assert-That $existingMatch.Success "invalid existing Docker subnet: $Existing"
    $existingAddress = Convert-IPv4ToUInt32 $existingMatch.Groups['address'].Value
    $candidateAddress = Convert-IPv4ToUInt32 $candidateMatch.Groups['address'].Value
    $existingPrefix = [int]$existingMatch.Groups['prefix'].Value
    $candidatePrefix = [int]$candidateMatch.Groups['prefix'].Value
    # PowerShell's shift operator promotes an all-ones UInt32/UInt64 mask to
    # signed Int32 and can produce -1 before the cast. Use exact UInt64 block
    # arithmetic instead; every IPv4 value and 2^32 are exactly representable.
    [uint64]$existingBlock = [math]::Pow(2, 32 - $existingPrefix)
    [uint64]$candidateBlock = [math]::Pow(2, 32 - $candidatePrefix)
    [uint64]$existingNetwork = [math]::Floor([double]$existingAddress / $existingBlock) * $existingBlock
    [uint64]$candidateNetwork = [math]::Floor([double]$candidateAddress / $candidateBlock) * $candidateBlock
    [uint64]$existingEnd = $existingNetwork + $existingBlock - 1
    [uint64]$candidateEnd = $candidateNetwork + $candidateBlock - 1
    return ($candidateNetwork -le $existingEnd -and $existingNetwork -le $candidateEnd)
}

function Get-DeterministicNetworkPlan {
    param([Parameter(Mandatory = $true)][string]$Subnet)
    $octet = '(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])'
    $match = [regex]::Match(([string]$Subnet).Trim(), "^(?<address>$octet(?:\.$octet){3})/(?<prefix>24)$")
    Assert-That $match.Success "Requests runtime subnet must be an IPv4 /24: $Subnet"
    $address = $match.Groups['address'].Value
    $parts = $address.Split('.')
    Assert-That ([int]$parts[3] -eq 0) "Requests runtime subnet must be aligned to a /24 network boundary: $Subnet"
    $prefix = "$($parts[0]).$($parts[1]).$($parts[2])"
    $gateway = "$prefix.1"
    $edgeIp = "$prefix.10"
    $dynamicIpRange = "$prefix.128/25"
    Assert-That (Test-CidrOverlap -Candidate "$edgeIp/32" -Existing $Subnet) 'edge IP is outside the selected Docker subnet'
    Assert-That (Test-CidrOverlap -Candidate "$gateway/32" -Existing $Subnet) 'Docker gateway is outside the selected subnet'
    Assert-That (-not (Test-CidrOverlap -Candidate "$edgeIp/32" -Existing $dynamicIpRange)) 'edge IP overlaps the dynamic Docker IP range'
    Assert-That (-not (Test-CidrOverlap -Candidate "$gateway/32" -Existing $dynamicIpRange)) 'Docker gateway overlaps the dynamic Docker IP range'
    Assert-That (Test-CidrOverlap -Candidate $dynamicIpRange -Existing $Subnet) 'dynamic Docker IP range is outside the selected subnet'
    return [ordered]@{
        subnet = $Subnet
        gateway = $gateway
        edgeIp = $edgeIp
        dynamicIpRange = $dynamicIpRange
        staticEdgeExcludedFromDynamicRange = $true
        dockerIpAllocation = 'dynamic containers use --ip-range; edge --ip is assigned only when its container starts'
    }
}

function Resolve-AmbiguousOwnedNetwork {
    param([Parameter(Mandatory = $true)][string]$Name)
    $format = '{{.Id}}|{{.Name}}|{{index .Labels "rct.runtime-owner"}}|{{index .Labels "rct.runtime-run"}}'
    $inspect = Invoke-DockerSafe -DockerArgs @('network', 'inspect', '--format', $format, $Name) -Purpose "recover ambiguous owned network $Name" -AllowFailure
    if ($inspect.ExitCode -ne 0) {
        if ([string]$inspect.Output -match '(?i)(no such object|no such network|not found)') { return $null }
        throw "ambiguous owned network recovery inspect failed for $Name with exit code $($inspect.ExitCode)"
    }
    $lines = @($inspect.Output -split '\r?\n' | Where-Object { $_ -match '\S' })
    Assert-That ($lines.Count -eq 1) "ambiguous owned network recovery returned an unexpected result for $Name"
    $parts = $lines[0].Trim().Split('|')
    Assert-That ($parts.Count -eq 4) "ambiguous owned network recovery metadata is incomplete for $Name"
    $networkId = $parts[0].Trim()
    $actualName = $parts[1].Trim()
    $expectedRun = [string]$script:runId
    Assert-That ($networkId -match '^[0-9a-fA-F]{12,64}$') "ambiguous owned network recovery returned an invalid id for $Name"
    if ($actualName -cne $Name -or $parts[2] -cne 'student-requests-gate' -or $parts[3] -cne $expectedRun) {
        throw "refusing to adopt ambiguous network $Name with a foreign name or ownership label"
    }
    return $networkId
}

function New-RequestsOwnedNetwork {
    param([Parameter(Mandatory = $true)][System.Collections.IDictionary]$NetworkPlan)
    $networkArgs = @(
        'network', 'create', '--driver', 'bridge', '--subnet', [string]$NetworkPlan.subnet,
        '--gateway', [string]$NetworkPlan.gateway, '--ip-range', [string]$NetworkPlan.dynamicIpRange,
        '--label', 'rct.runtime-owner=student-requests-gate', '--label', "rct.runtime-run=$($script:runId)", $script:networkName
    )
    $networkResult = $null
    $createException = $null
    try {
        # Docker can create the network and still fail while reporting the
        # result (for example, an interrupted client). Keep the original
        # failure visible, then recover only an exact name with both labels.
        $networkResult = Invoke-DockerSafe -DockerArgs $networkArgs -Purpose 'create collision-checked owned Docker network with edge-excluding IPAM range' -AllowFailure
    } catch {
        $createException = $_.Exception.Message
    }
    $networkId = $null
    $createFailure = $null
    $recoveryRequired = $false
    if ($null -ne $createException) {
        $createFailure = $createException
        $recoveryRequired = $true
    } elseif ($null -eq $networkResult -or $networkResult.ExitCode -ne 0) {
        $createFailure = if ($null -eq $networkResult) { 'Docker network create returned no result' } else { "Docker network create failed with exit code $($networkResult.ExitCode)" }
        $recoveryRequired = $true
    } else {
        $outputLines = @($networkResult.Output -split '\r?\n' | Where-Object { $_ -match '\S' })
        $networkId = ($outputLines | Where-Object { $_ -match '^[0-9a-fA-F]{12,64}$' } | Select-Object -First 1)
        if ([string]::IsNullOrWhiteSpace($networkId)) {
            $createFailure = if ($outputLines.Count -gt 0) { 'Docker returned a malformed owned network id' } else { 'Docker did not return the owned network id' }
            $recoveryRequired = $true
        }
    }
    if ($recoveryRequired) {
        try {
            $networkId = Resolve-AmbiguousOwnedNetwork -Name $script:networkName
        } catch {
            throw "network create failed: $createFailure; ambiguous recovery refused: $($_.Exception.Message)"
        }
        if ($null -eq $networkId) { throw $createFailure }
        $script:report.network.createRecovery = [ordered]@{
            status = 'RECOVERED'
            originalFailure = Protect-ReportText $createFailure
            exactName = $script:networkName
            ownershipLabels = 'rct.runtime-owner=student-requests-gate; rct.runtime-run=<runId>'
        }
    } else {
        $script:report.network.createRecovery = [ordered]@{ status = 'NOT_NEEDED' }
    }
    Assert-That (-not [string]::IsNullOrWhiteSpace($networkId)) 'Docker did not return or recover the owned network id'
    $networkId = $networkId.Trim()
    Assert-That ($networkId -match '^[0-9a-fA-F]{12,64}$') 'Docker returned an invalid owned network id'
    # Register immediately after validating the create/recovery ID. Every
    # subsequent runtime operation can therefore reach owned cleanup.
    $script:ownedNetwork = $networkId
    $script:report.network.id = $script:ownedNetwork
    $script:report.network.name = $script:networkName
    $script:report.network.subnet = [string]$NetworkPlan.subnet
    $script:report.network.gateway = [string]$NetworkPlan.gateway
    $script:report.network.dynamicIpRange = [string]$NetworkPlan.dynamicIpRange
    $script:report.network.nginxIp = [string]$NetworkPlan.edgeIp
    $script:report.network.staticEdgeExcludedFromDynamicRange = [bool]$NetworkPlan.staticEdgeExcludedFromDynamicRange
    return [pscustomobject]@{ Id = $script:ownedNetwork; Plan = $NetworkPlan; DockerArgs = $networkArgs }
}

function Get-DockerNetworkSubnets {
    $list = Invoke-DockerSafe -DockerArgs @('network', 'ls', '-q') -Purpose 'list Docker networks'
    $subnets = [System.Collections.Generic.List[string]]::new()
    $cidrPattern = '^(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])(?:\.(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}/(?:0|[1-9]|[12][0-9]|3[0-2])$'
    foreach ($networkId in ($list.Output -split '\r?\n' | Where-Object { $_ -match '\S' })) {
        $inspect = Invoke-DockerSafe -DockerArgs @('network', 'inspect', $networkId.Trim(), '--format', '{{range .IPAM.Config}}{{println .Subnet}}{{end}}') -Purpose 'inspect Docker network subnets'
        foreach ($line in ($inspect.Output -split '\r?\n')) {
            $subnet = $line.Trim()
            if ([string]::IsNullOrWhiteSpace($subnet)) { continue }
            Assert-That ($subnet -match $cidrPattern) "Docker returned an invalid network subnet: $subnet"
            [void]$subnets.Add($subnet)
        }
    }
    return @($subnets)
}

function Select-DisposableSubnet {
    $candidates = @('172.30.185.0/24', '172.30.186.0/24')
    $existing = Get-DockerNetworkSubnets
    $script:report.network = [ordered]@{
        candidates = $candidates
        existingSubnets = @($existing)
        collisionChecked = $true
    }
    foreach ($candidate in $candidates) {
        if (-not ($existing | Where-Object { Test-CidrOverlap -Candidate $candidate -Existing $_ })) { return $candidate }
    }
    throw 'both disposable Requests runtime subnets overlap an existing Docker network'
}

function New-RunIdentity {
    $suffix = (New-RandomSecret -Bytes 6).ToLowerInvariant()
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff')
    $script:runId = "${stamp}-${suffix}"
    $script:runDir = Join-Path $script:taskRoot (Join-Path 'runs' $script:runId)
    $script:keysDir = Join-Path $script:runDir 'keys'
    $script:artifactSnapshotRoot = Join-Path $script:runDir 'artifacts'
    New-Item -ItemType Directory -Path $script:keysDir -Force | Out-Null
    $script:networkName = "rct-requests-gate-$($script:runId)"
    $script:report.runId = $script:runId
}

function Convert-EdgeToRuntimeConfig {
    param(
        [Parameter(Mandatory = $true)][string]$NginxPath,
        [Parameter(Mandatory = $true)][string]$DefaultPath,
        [AllowNull()][string]$NginxContent = $null,
        [AllowNull()][string]$DefaultContent = $null
    )
    $hasCachedContent = $PSBoundParameters.ContainsKey('NginxContent') -and $PSBoundParameters.ContainsKey('DefaultContent')
    if ($hasCachedContent) {
        Assert-That ($null -ne $NginxContent -and $null -ne $DefaultContent) 'cached edge source content must be non-null'
        $nginx = $NginxContent
        $default = $DefaultContent
    } else {
        # This fallback keeps the helper independently testable. Production
        # always passes the validated, read-once source strings from
        # $script:validatedEdgeSources below.
        $nginx = [IO.File]::ReadAllText($NginxPath)
        $default = [IO.File]::ReadAllText($DefaultPath)
    }
    $lineEnding = if ($default.Contains("`r`n")) { "`r`n" } else { "`n" }
    $diagnosticFormat = "${lineEnding}    log_format requests_gate '`$request `$status `$upstream_addr `$upstream_status';${lineEnding}    access_log /var/log/nginx/requests-gate.log requests_gate;${lineEnding}"
    $nginx = $nginx.Replace('    access_log /var/log/nginx/access.log main;', "    access_log /var/log/nginx/access.log main;$diagnosticFormat")
    $staticRootLocation = @(
        '    location = / {',
        '        root /usr/share/nginx/html;',
        '        index index.html;',
        '        try_files /index.html =200;',
        '    }'
    ) -join $lineEnding
    $staticLocation = @(
        '    location / {',
        '        root /usr/share/nginx/html;',
        '        index index.html;',
        '        try_files $uri $uri/ /index.html;',
        '    }'
    ) -join $lineEnding
    $uiLocations = @(
        [ordered]@{
            Name = 'presentation'
            Route = '/presentation/'
            Upstream = 'landing-nginx'
            Replacement = @(
                '    location /presentation/ {',
                '        return 404;',
                '    }'
            ) -join $lineEnding
        },
        [ordered]@{
            Name = 'app'
            Route = '/app/'
            Upstream = 'pwa-nginx'
            Replacement = @(
                '    location /app/ {',
                '        rewrite ^/app/(.*)$ /$1 break;',
                '        root /usr/share/nginx/html;',
                '        index index.html;',
                '        try_files $uri $uri/ /index.html;',
                '    }'
            ) -join $lineEnding
        },
        [ordered]@{
            Name = 'mini-app'
            Route = '/mini-app/'
            Upstream = 'mini-app-nginx'
            Replacement = @(
                '    location /mini-app/ {',
                '        return 404;',
                '    }'
            ) -join $lineEnding
        }
    )
    foreach ($uiLocation in $uiLocations) {
        $locationPattern = '(?s)    location\s+' + [regex]::Escape($uiLocation.Route) + '\s*\{\s*proxy_pass\s+http://' + [regex]::Escape($uiLocation.Upstream) + ':80/;\s*proxy_set_header\s+Host\s+\$host;\s*\}'
        $locationMatches = [regex]::Matches($default, $locationPattern)
        Assert-That ($locationMatches.Count -eq 1) "accepted Nginx $($uiLocation.Name) location is missing or has unexpected directives"
        $locationMatch = $locationMatches[0]
        $default = $default.Remove($locationMatch.Index, $locationMatch.Length).Insert($locationMatch.Index, $uiLocation.Replacement)
    }
    $rootReplacements = @(
        [ordered]@{
            Name = 'root redirect'
            Pattern = '(?s)    location\s*=\s*/\s*\{\s*return\s+301\s+/login;\s*\}'
            Replacement = $staticRootLocation
        },
        [ordered]@{
            Name = 'web-panel root'
            Pattern = '(?s)    location\s+/\s*\{\s*proxy_pass http://web-panel-nginx:80/;.*?\n    \}'
            Replacement = $staticLocation
        }
    )
    foreach ($rootReplacement in $rootReplacements) {
        $rootMatches = [regex]::Matches($default, $rootReplacement.Pattern)
        Assert-That ($rootMatches.Count -eq 1) "accepted Nginx $($rootReplacement.Name) location is missing or has unexpected directives"
        $rootMatch = $rootMatches[0]
        $default = $default.Remove($rootMatch.Index, $rootMatch.Length).Insert($rootMatch.Index, $rootReplacement.Replacement)
    }
    return [ordered]@{ Nginx = $nginx; Default = $default }
}

function Register-OwnedContainer {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Id
    )
    $normalizedId = $Id.Trim()
    Assert-That ($normalizedId -match '^[0-9a-fA-F]{12,64}$') "Docker returned an invalid id for owned $Name container"
    if (-not $script:ownedContainers.Contains($normalizedId)) { $script:ownedContainers.Add($normalizedId) }
    if (-not $script:ownedContainerNames.Contains($Name)) { $script:ownedContainerNames.Add($Name) }
}

function Resolve-AmbiguousOwnedContainer {
    param([Parameter(Mandatory = $true)][string]$Name)
    $format = '{{.Id}}|{{.Name}}|{{index .Config.Labels "rct.runtime-owner"}}|{{index .Config.Labels "rct.runtime-run"}}'
    $inspect = Invoke-DockerSafe -DockerArgs @('container', 'inspect', '--format', $format, $Name) -Purpose "recover ambiguous owned $Name create" -AllowFailure
    if ($inspect.ExitCode -ne 0) {
        if ([string]$inspect.Output -match '(?i)(no such object|no such container|not found)') { return $null }
        throw "ambiguous owned container recovery inspect failed for $Name with exit code $($inspect.ExitCode)"
    }
    $lines = @($inspect.Output -split '\r?\n' | Where-Object { $_ -match '\S' })
    Assert-That ($lines.Count -eq 1) "ambiguous owned container recovery returned an unexpected result for $Name"
    $parts = $lines[0].Trim().Split('|')
    Assert-That ($parts.Count -eq 4) "ambiguous owned container recovery metadata is incomplete for $Name"
    $actualName = $parts[1].Trim().TrimStart('/')
    $expectedRun = [string]$script:runId
    if ($actualName -cne $Name -or $parts[2] -cne 'student-requests-gate' -or $parts[3] -cne $expectedRun) {
        throw "refusing to adopt ambiguous container $Name with a foreign name or ownership label"
    }
    Assert-That ($parts[0] -match '^[0-9a-fA-F]{12,64}$') "ambiguous owned container recovery returned an invalid id for $Name"
    return $parts[0].Trim()
}

function Start-OwnedContainer {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Image,
        [string[]]$Aliases = @(),
        [string[]]$Publish = @(),
        [hashtable]$Environment = @{},
        [hashtable[]]$Mounts = @(),
        [string]$Ip = $null,
        [string]$Entrypoint = $null,
        [string[]]$Command = @(),
        [switch]$CreateOnly
    )
    $dockerArgs = [System.Collections.Generic.List[string]]::new()
    [void]$dockerArgs.Add('create')
    # Docker defaults --pull to missing. Make every owned create fail closed
    # when a pinned image is absent instead of reaching the registry implicitly.
    [void]$dockerArgs.Add('--pull=never')
    foreach ($value in @('--name', $Name, '--label', 'rct.runtime-owner=student-requests-gate', '--label', "rct.runtime-run=$($script:runId)", '--network', $script:networkName)) { [void]$dockerArgs.Add([string]$value) }
    foreach ($alias in $Aliases) { [void]$dockerArgs.Add('--network-alias'); [void]$dockerArgs.Add([string]$alias) }
    if ($Ip) { [void]$dockerArgs.Add('--ip'); [void]$dockerArgs.Add([string]$Ip) }
    foreach ($mapping in $Publish) { [void]$dockerArgs.Add('--publish'); [void]$dockerArgs.Add([string]$mapping) }
    foreach ($entry in $Environment.GetEnumerator()) { [void]$dockerArgs.Add('--env'); [void]$dockerArgs.Add("$($entry.Key)=$($entry.Value)") }
    foreach ($mount in $Mounts) {
        $mode = if ($mount.ContainsKey('ReadOnly') -and -not [bool]$mount.ReadOnly) { '' } else { ',readonly' }
        [void]$dockerArgs.Add('--mount')
        [void]$dockerArgs.Add("type=bind,source=$($mount.Source),target=$($mount.Target)$mode")
    }
    if (-not [string]::IsNullOrWhiteSpace($Entrypoint)) {
        [void]$dockerArgs.Add('--entrypoint')
        [void]$dockerArgs.Add($Entrypoint)
    }
    [void]$dockerArgs.Add($Image)
    foreach ($argument in $Command) { [void]$dockerArgs.Add([string]$argument) }

    # Docker can create the container and still fail while reporting the
    # result (for example, an interrupted client). Recover only an exact name
    # with both ownership labels; a foreign match is never adopted or deleted.
    $createResult = $null
    $createException = $null
    try {
        $createResult = Invoke-DockerSafe -DockerArgs $dockerArgs.ToArray() -Purpose "create owned $Name container" -AllowFailure
    } catch {
        $createException = $_.Exception.Message
    }
    $id = $null
    if ($null -ne $createException -or ($null -ne $createResult -and $createResult.ExitCode -ne 0)) {
        $id = Resolve-AmbiguousOwnedContainer -Name $Name
        if ($null -eq $id) {
            $failure = if ($null -ne $createException) { $createException } else { "create owned $Name container failed with exit code $($createResult.ExitCode)" }
            throw $failure
        }
    } else {
        $id = ($createResult.Output -split '\r?\n' | Where-Object { $_ -match '^[0-9a-fA-F]{12,64}$' } | Select-Object -First 1)
        if ([string]::IsNullOrWhiteSpace($id)) { $id = Resolve-AmbiguousOwnedContainer -Name $Name }
        Assert-That (-not [string]::IsNullOrWhiteSpace($id)) "Docker did not return an id for owned $Name container"
    }

    # Register immediately after create/recovery, before any start operation.
    # A start failure therefore remains visible to the owned cleanup ledger.
    Register-OwnedContainer -Name $Name -Id $id
    if ($CreateOnly) { return [pscustomobject]@{ Id = $id.Trim(); Name = $Name } }

    $startResult = Invoke-DockerSafe -DockerArgs @('start', $id.Trim()) -Purpose "start owned $Name container" -AllowFailure
    Assert-That ($startResult.ExitCode -eq 0) "starting owned $Name container failed with exit code $($startResult.ExitCode)"
    return [pscustomobject]@{ Id = $id.Trim(); Name = $Name }
}

function Invoke-OwnedDockerExec {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][string[]]$Command,
        [string]$Purpose = 'owned container command',
        [switch]$AllowFailure
    )
    return Invoke-DockerSafe -DockerArgs (@('exec', $ContainerId) + $Command) -Purpose $Purpose -AllowFailure:$AllowFailure
}

function Wait-OwnedExec {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][string[]]$Command,
        [string]$Purpose = 'wait for owned container',
        [int]$TimeoutSeconds = 180
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $result = Invoke-OwnedDockerExec -ContainerId $ContainerId -Command $Command -Purpose $Purpose -AllowFailure
        if ($result.ExitCode -eq 0) { return }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$Purpose timed out"
}

function Wait-HostHttp {
    param([Parameter(Mandatory = $true)][int]$Port, [string]$Path = '/actuator/health', [int]$TimeoutSeconds = 180)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port$Path" -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) { return }
        } catch { }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "HTTP service on 127.0.0.1:$Port$Path did not become ready"
}

function Read-PsqlScalar {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][ValidateSet('academic_db', 'schedule_db')][string]$Database,
        [Parameter(Mandatory = $true)][string]$Sql,
        [string]$Purpose = 'read PostgreSQL fixture'
    )
    $result = Invoke-OwnedDockerExec -ContainerId $ContainerId -Command @('psql', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1', '-U', 'rct_user', '-d', $Database, '-c', $Sql) -Purpose $Purpose
    return (($result.Output -split '\r?\n' | Where-Object { $_ -match '\S' } | Select-Object -Last 1).Trim())
}

function Invoke-Psql {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][ValidateSet('academic_db', 'schedule_db')][string]$Database,
        [Parameter(Mandatory = $true)][string]$Sql,
        [string]$Purpose = 'write PostgreSQL fixture'
    )
    return Invoke-OwnedDockerExec -ContainerId $ContainerId -Command @('psql', '-q', '-v', 'ON_ERROR_STOP=1', '-U', 'rct_user', '-d', $Database, '-c', $Sql) -Purpose $Purpose
}

function Invoke-PsqlFile {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][ValidateSet('academic_db', 'schedule_db')][string]$Database,
        [Parameter(Mandatory = $true)][string]$SourcePath,
        [hashtable]$Variables = @{},
        [string]$Purpose = 'apply PostgreSQL seed'
    )
    $remotePath = "/tmp/rct-$([IO.Path]::GetFileName($SourcePath))"
    $null = Invoke-DockerSafe -DockerArgs @('cp', $SourcePath, "${ContainerId}:$remotePath") -Purpose "copy $Purpose"
    $args = @('exec', $ContainerId, 'psql', '-q', '-v', 'ON_ERROR_STOP=1', '-U', 'rct_user', '-d', $Database)
    foreach ($entry in $Variables.GetEnumerator()) { $args += @('-v', "$($entry.Key)=$($entry.Value)") }
    $args += @('-f', $remotePath)
    return Invoke-DockerSafe -DockerArgs $args -Purpose $Purpose
}

function Read-MongoJson {
    param([Parameter(Mandatory = $true)][string]$ContainerId, [Parameter(Mandatory = $true)][string]$JavaScript, [string]$Purpose = 'read Mongo fixture')
    $result = Invoke-OwnedDockerExec -ContainerId $ContainerId -Command @('mongosh', '--quiet', '--host', 'mongo:27017', '--eval', $JavaScript) -Purpose $Purpose
    return (($result.Output -split '\r?\n' | Where-Object { $_ -match '^\s*\{' } | Select-Object -Last 1).Trim())
}

function Get-ActuatorRequestCount {
    param([Parameter(Mandatory = $true)][int]$Port)
    $tag = [Uri]::EscapeDataString('uri:/api/v1/student/requests/excuse')
    try {
        $response = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$Port/actuator/metrics/http.server.requests?tag=$tag" -TimeoutSec 10
        $measurement = @($response.measurements | Where-Object { $_.statistic -eq 'COUNT' } | Select-Object -First 1)
        Assert-That ($measurement.Count -eq 1) "actuator counter is missing on port $Port"
        return [int64]$measurement[0].value
    } catch {
        throw "actuator request counter unavailable on port $Port"
    }
}

function Assert-HostPortsFree {
    foreach ($port in @(
            [int]$script:report.ports.edge,
            [int]$script:report.ports.gatewayDiagnostics,
            [int]$script:report.ports.bffDiagnostics,
            [int]$script:report.ports.attendanceDiagnostics,
            [int]$script:report.ports.authDiagnostics,
            [int]$script:report.ports.scheduleDiagnostics,
            [int]$script:report.ports.academicDiagnostics
        )) {
        $occupied = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
        Assert-That ($occupied.Count -eq 0) "required local port $port is occupied; refusing to touch an existing process"
    }
}

function Assert-OwnedKeysPath {
    Assert-That ($script:runDir -and $script:keysDir) 'owned run directory was not initialized'
    $resolvedRun = [IO.Path]::GetFullPath($script:runDir).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $resolvedKeys = [IO.Path]::GetFullPath($script:keysDir).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    Assert-That ($resolvedKeys.StartsWith("$resolvedRun$([IO.Path]::DirectorySeparatorChar)", [StringComparison]::OrdinalIgnoreCase)) 'keys path escaped the owned run directory'
    Assert-That ([IO.Path]::GetFileName($resolvedKeys) -eq 'keys') 'cleanup target is not the exact owned keys directory'
    $runItem = Get-Item -LiteralPath $resolvedRun -Force
    $keysItem = Get-Item -LiteralPath $resolvedKeys -Force
    Assert-That (($runItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'owned run directory is a reparse point'
    Assert-That (($keysItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'owned keys directory is a reparse point'
}

function New-RuntimeConfig {
    Assert-That ($null -ne $script:validatedEdgeSources) 'validated edge sources are missing before runtime config generation'
    Assert-That ($null -ne $script:artifactSnapshot) 'verified artifact snapshot is missing before runtime config generation'
    $edge = $script:validatedEdgeSources
    $config = Convert-EdgeToRuntimeConfig -NginxPath $edge.nginxPath -DefaultPath $edge.defaultPath -NginxContent $edge.nginxText -DefaultContent $edge.defaultText
    $configDir = Join-Path $script:runDir 'nginx'
    New-Item -ItemType Directory -Path $configDir -Force | Out-Null
    $certPath = Join-Path $script:keysDir 'server.crt'
    $keyPath = Join-Path $script:keysDir 'server.key'
    New-LocalTlsCertificate -KeyPath $keyPath -CertificatePath $certPath
    [IO.File]::WriteAllText((Join-Path $configDir 'nginx.conf'), $config.Nginx, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $configDir 'default.conf'), $config.Default, [Text.UTF8Encoding]::new($false))
    $pwaManifest = $script:artifactSnapshot.PwaDist
    $script:report.source.runtimeNginx = [ordered]@{
        configSha256 = Get-Sha256Hex -Path (Join-Path $configDir 'nginx.conf')
        defaultSha256 = Get-Sha256Hex -Path (Join-Path $configDir 'default.conf')
        certificate = 'ephemeral self-signed certificate for 127.0.0.1'
        diagnosticAccessLog = 'request/status/upstream address/upstream status only'
    }
    $script:report.source.runtimePwaDist = $pwaManifest
    return [ordered]@{ ConfigDir = $configDir; CertPath = $certPath; KeyPath = $keyPath; PwaDistPath = $pwaManifest.path }
}

function Start-Infrastructure {
    param([Parameter(Mandatory = $true)][hashtable]$Secrets)
    $script:infra = [ordered]@{}
    $script:infra.pgAcademic = Start-OwnedContainer -Name "rct-requests-$($script:runId)-pg-academic" -Image $script:images.postgres -Aliases @('postgres-academic') -Environment @{
        POSTGRES_DB = 'academic_db'; POSTGRES_USER = 'rct_user'; POSTGRES_PASSWORD = $Secrets.dbAcademic; TZ = 'Europe/Moscow'
    } -Command @('postgres')
    $script:infra.pgSchedule = Start-OwnedContainer -Name "rct-requests-$($script:runId)-pg-schedule" -Image $script:images.postgres -Aliases @('postgres-schedule') -Environment @{
        POSTGRES_DB = 'schedule_db'; POSTGRES_USER = 'rct_user'; POSTGRES_PASSWORD = $Secrets.dbSchedule; TZ = 'Europe/Moscow'
    } -Command @('postgres')
    $script:infra.redis = Start-OwnedContainer -Name "rct-requests-$($script:runId)-redis" -Image $script:images.redis -Aliases @('redis') -Command @('redis-server', '--requirepass', $Secrets.redis)
    $script:infra.rabbit = Start-OwnedContainer -Name "rct-requests-$($script:runId)-rabbit" -Image $script:images.rabbitmq -Aliases @('rabbitmq') -Environment @{
        RABBITMQ_DEFAULT_USER = 'rct_user'; RABBITMQ_DEFAULT_PASS = $Secrets.rabbit
    }
    $script:infra.mongo = Start-OwnedContainer -Name "rct-requests-$($script:runId)-mongo" -Image $script:images.mongo -Aliases @('mongo', 'mongo-attendance') -Command @('mongod', '--replSet', 'rs0', '--bind_ip_all')
    $script:report.assertions.infrastructure = [ordered]@{ fiveContainers = $true; internalPorts = 'postgres5432/redis6379/rabbit5672/mongo27017'; timezone = 'Europe/Moscow' }
    Wait-OwnedExec -ContainerId $script:infra.pgAcademic.Id -Command @('pg_isready', '-U', 'rct_user', '-d', 'academic_db') -Purpose 'wait for academic PostgreSQL'
    Wait-OwnedExec -ContainerId $script:infra.pgSchedule.Id -Command @('pg_isready', '-U', 'rct_user', '-d', 'schedule_db') -Purpose 'wait for schedule PostgreSQL'
    Wait-OwnedExec -ContainerId $script:infra.redis.Id -Command @('redis-cli', '--no-auth-warning', '-a', $Secrets.redis, 'ping') -Purpose 'wait for Redis'
    Wait-OwnedExec -ContainerId $script:infra.rabbit.Id -Command @('rabbitmq-diagnostics', '-q', 'ping') -Purpose 'wait for RabbitMQ'
    Wait-OwnedExec -ContainerId $script:infra.mongo.Id -Command @('mongosh', '--quiet', '--eval', "db.adminCommand({ping:1}).ok") -Purpose 'wait for MongoDB'
    $init = "try { rs.status() } catch (e) { rs.initiate({_id:'rs0',members:[{_id:0,host:'mongo:27017'}]}) }"
    $null = Invoke-OwnedDockerExec -ContainerId $script:infra.mongo.Id -Command @('mongosh', '--quiet', '--eval', $init) -Purpose 'initialize Mongo rs0 with container-resolvable host'
    Wait-OwnedExec -ContainerId $script:infra.mongo.Id -Command @('mongosh', '--quiet', '--eval', "db.hello().isWritablePrimary ? quit(0) : quit(1)") -Purpose 'wait for Mongo rs0 primary'
}

function Start-JavaService {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Jar,
        [Parameter(Mandatory = $true)][hashtable]$Environment,
        [Parameter(Mandatory = $true)][string[]]$Aliases,
        [string[]]$Publish = @(),
        [string[]]$Arguments = @(),
        [hashtable[]]$ExtraMounts = @(),
        [string]$Image = $script:images.temurin
    )
    $mount = @{ Source = $Jar; Target = '/opt/rct/app.jar' }
    return Start-OwnedContainer -Name "rct-requests-$($script:runId)-$Name" -Image $Image -Aliases $Aliases -Publish $Publish -Mounts (@($mount) + $ExtraMounts) -Environment $Environment -Command (@('java', '-XX:+UseContainerSupport', '-jar', '/opt/rct/app.jar') + $Arguments)
}

function Start-BackendServices {
    param([Parameter(Mandatory = $true)][hashtable]$Secrets, [Parameter(Mandatory = $true)][hashtable]$Config)
    Assert-That ($null -ne $script:artifactSnapshot) 'verified artifact snapshot is missing before backend startup'
    $jars = $script:artifactSnapshot.Jars
    $common = @{
        SPRING_PROFILES_ACTIVE = 'prod'; TZ = 'Europe/Moscow';
        RUTCAMPUSTRACK_SECURITY_LEGACY_HEADERS_ENABLED = 'false'; JAVA_TOOL_OPTIONS = '-Djava.security.egd=file:/dev/./urandom'
        # Runtime-only observability: prod source keeps its restricted exposure;
        # this disposable runner publishes the exact metrics endpoint needed by I2.
        MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = 'health,info,metrics'
        MANAGEMENT_ENDPOINT_METRICS_ACCESS = 'unrestricted'
    }
    # Keep a pre-created edge reservation in the service map while dynamic
    # backend containers are allocated on the same disposable network.
    if ($null -eq $script:services) { $script:services = [ordered]@{} }
    Add-PhaseDiagnostic -Name 'academic-service' -Status 'START' -Message "Academic HTTP readiness on 127.0.0.1:$([int]$script:report.ports.academicDiagnostics)"
    try {
        $script:services.academic = Start-JavaService -Name 'academic' -Jar $jars.academic.path -Aliases @('academic-service') -Publish @("127.0.0.1:$([int]$script:report.ports.academicDiagnostics):9091") -Environment ($common + @{
                POSTGRES_ACADEMIC_PASSWORD = $Secrets.dbAcademic; SPRING_DATASOURCE_URL = 'jdbc:postgresql://postgres-academic:5432/academic_db'; SPRING_DATASOURCE_USERNAME = 'rct_user'; REDIS_PASSWORD = $Secrets.redis; RABBITMQ_USER = 'rct_user'; RABBITMQ_PASSWORD = $Secrets.rabbit; SPRING_RABBITMQ_USERNAME = 'rct_user'; SPRING_RABBITMQ_PASSWORD = $Secrets.rabbit; GRPC_SECRET = $Secrets.grpc
            }) -Arguments @('--server.port=9091', '--server.address=0.0.0.0', '--grpc.server.port=19091', '--grpc.server.address=0.0.0.0')
        Wait-HostHttp -Port ([int]$script:report.ports.academicDiagnostics) -Path '/actuator/health'
        Add-PhaseDiagnostic -Name 'academic-service' -Status 'PASS' -Message 'Academic actuator health returned an HTTP 2xx response before schema inspection'
    } catch {
        Add-PhaseDiagnostic -Name 'academic-service' -Status 'FAIL' -Message $_.Exception.Message
        throw
    }
    $academicSchema = Read-PsqlScalar -ContainerId $script:infra.pgAcademic.Id -Database academic_db -Purpose 'verify Academic V24-V26 schema' -Sql @"
SELECT CASE WHEN
 to_regclass('public.flyway_schema_history') IS NOT NULL
 AND EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '24' AND success)
 AND EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '25' AND success)
 AND EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '26' AND success)
 AND NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE NOT success)
 THEN 'true' ELSE 'false' END
"@
    Assert-That ($academicSchema -eq 'true') 'Academic V24-V26 migrations did not complete'
    $null = Invoke-PsqlFile -ContainerId $script:infra.pgAcademic.Id -Database academic_db -SourcePath $script:seedAcademicPath -Purpose 'apply accepted Academic dynamic seed'
    $teacherSql = @"
INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at)
SELECT id, 'teacher', 'active', group_id, NOW(), NOW() FROM users WHERE login = 'student' AND group_id IS NOT NULL
ON CONFLICT (user_id, role) DO UPDATE SET status = EXCLUDED.status, group_id = EXCLUDED.group_id, updated_at = NOW();
"@
    $null = Invoke-Psql -ContainerId $script:infra.pgAcademic.Id -Database academic_db -Sql $teacherSql -Purpose 'create same-group teacher fixture grant'
    $fixture = Read-PsqlScalar -ContainerId $script:infra.pgAcademic.Id -Database academic_db -Purpose 'resolve dynamic Academic fixture ids' -Sql @"
SELECT u.id || '|' || u.group_id || '|' || u.roles_version || '|' || semester.id || '|' || hw.id || '|' || hw.subject_id || '|' || hw.binding_id
FROM users u JOIN user_role_grants grants ON grants.user_id = u.id AND grants.role = 'student' AND grants.status = 'active' AND grants.group_id = u.group_id
JOIN semesters semester ON semester.is_active
JOIN homeworks hw ON hw.group_id = u.group_id AND hw.semester_id = semester.id AND hw.title = 'Runtime homework golden path' AND hw.actor_id = u.id AND hw.publication_state = 'ACTIVE' AND octet_length(hw.payload_hash) = 32
WHERE u.login = 'student';
"@
    $parts = $fixture.Split('|')
    Assert-That ($parts.Count -eq 7) 'dynamic Academic fixture did not return seven ids'
    $studentId = [long]$parts[0]; $groupId = [long]$parts[1]; $rolesVersion = [long]$parts[2]; $semesterId = [long]$parts[3]; $homeworkId = [long]$parts[4]; $subjectId = [long]$parts[5]; $bindingId = [long]$parts[6]
    foreach ($value in @($studentId, $groupId, $rolesVersion, $semesterId, $homeworkId, $subjectId, $bindingId)) { Assert-That ($value -gt 0) 'dynamic Academic fixture id is not positive' }
    $assignmentSql = @"
INSERT INTO subject_lesson_types (subject_id, lesson_type) VALUES ($subjectId, 'lecture') ON CONFLICT (subject_id, lesson_type) DO NOTHING;
INSERT INTO assignments (teacher_id, subject_id, group_id, semester_id, lesson_type, valid_from)
SELECT $studentId, $subjectId, $groupId, $semesterId, 'lecture', CURRENT_DATE - 1
WHERE NOT EXISTS (SELECT 1 FROM assignments WHERE teacher_id = $studentId AND subject_id = $subjectId AND group_id = $groupId AND semester_id = $semesterId AND lesson_type = 'lecture' AND valid_from = CURRENT_DATE - 1);
"@
    $null = Invoke-Psql -ContainerId $script:infra.pgAcademic.Id -Database academic_db -Sql $assignmentSql -Purpose 'create dynamic Academic assignment provenance'
    $assignmentId = [long](Read-PsqlScalar -ContainerId $script:infra.pgAcademic.Id -Database academic_db -Purpose 'resolve dynamic assignment id' -Sql "SELECT id FROM assignments WHERE teacher_id = $studentId AND subject_id = $subjectId AND group_id = $groupId AND semester_id = $semesterId AND lesson_type = 'lecture' ORDER BY id DESC LIMIT 1;")
    Assert-That ($assignmentId -gt 0) 'dynamic assignment id is not positive'
    $script:report.fixture = [ordered]@{ login = 'student'; studentId = "$studentId"; groupId = "$groupId"; rolesVersion = "$rolesVersion"; semesterId = "$semesterId"; homeworkId = "$homeworkId"; subjectId = "$subjectId"; bindingId = "$bindingId"; assignmentId = "$assignmentId" }

    $script:services.schedule = Start-JavaService -Name 'schedule' -Jar $jars.schedule.path -Aliases @('schedule-service') -Publish @('127.0.0.1:18519:9092') -Environment ($common + @{
            POSTGRES_SCHEDULE_PASSWORD = $Secrets.dbSchedule; SPRING_DATASOURCE_URL = 'jdbc:postgresql://postgres-schedule:5432/schedule_db'; SPRING_DATASOURCE_USERNAME = 'rct_user'; RABBITMQ_USER = 'rct_user'; RABBITMQ_PASSWORD = $Secrets.rabbit; SPRING_RABBITMQ_USERNAME = 'rct_user'; SPRING_RABBITMQ_PASSWORD = $Secrets.rabbit; GRPC_SECRET = $Secrets.grpc
        }) -Arguments @('--server.port=9092', '--server.address=0.0.0.0', '--grpc.server.port=19092', '--grpc.server.address=0.0.0.0')
    Wait-HostHttp -Port 18519 -Path '/actuator/health'
    $null = Invoke-PsqlFile -ContainerId $script:infra.pgSchedule.Id -Database schedule_db -SourcePath $script:seedSchedulePath -Variables @{ student_id = $studentId; group_id = $groupId; semester_id = $semesterId; subject_id = $subjectId; homework_id = $homeworkId; teacher_id = $studentId; assignment_id = $assignmentId; binding_id = $bindingId } -Purpose 'apply accepted Schedule V17 dynamic seed'

    $script:services.auth = Start-JavaService -Name 'auth' -Jar $jars.auth.path -Aliases @('auth-service') -Publish @('127.0.0.1:18518:9090') -ExtraMounts @(@{ Source = $script:keysDir; Target = '/keys'; ReadOnly = $false }) -Environment ($common + @{
            POSTGRES_ACADEMIC_PASSWORD = $Secrets.dbAcademic; REDIS_PASSWORD = $Secrets.redis; SPRING_RABBITMQ_HOST = 'rabbitmq'; SPRING_RABBITMQ_PORT = '5672'; SPRING_RABBITMQ_USERNAME = 'rct_user'; SPRING_RABBITMQ_PASSWORD = $Secrets.rabbit; JWT_KEY_DIR = '/keys'; INTERNAL_ISSUER_SECRET = $Secrets.internalIssuer; TMA_BOT_TOKEN = $Secrets.tma
        })
    # Auth owns the ephemeral RSA key directory inside the container. It is not
    # mounted from a host secret or reused across runs.
    Wait-HostHttp -Port ([int]$script:report.ports.authDiagnostics) -Path '/actuator/health'
    Add-PhaseDiagnostic -Name 'auth-service' -Status 'PASS' -Message 'Auth readiness returned an HTTP 2xx actuator health response'

    $script:services.attendance = Start-JavaService -Name 'attendance' -Jar $jars.attendance.path -Aliases @('attendance-service') -Publish @('127.0.0.1:18517:9093') -Environment ($common + @{
            SPRING_DATA_MONGODB_URI = 'mongodb://mongo:27017/attendance_db?replicaSet=rs0'; REDIS_PASSWORD = $Secrets.redis; RABBITMQ_USER = 'rct_user'; RABBITMQ_PASSWORD = $Secrets.rabbit; SPRING_RABBITMQ_USERNAME = 'rct_user'; SPRING_RABBITMQ_PASSWORD = $Secrets.rabbit; GRPC_SECRET = $Secrets.grpc; ATTENDANCE_GRPC_PORT = '19093'
        }) -Arguments @('--server.port=9093', '--server.address=0.0.0.0')
    Wait-HostHttp -Port 18517 -Path '/actuator/health'

    $script:services.bff = Start-JavaService -Name 'bff' -Jar $jars.mobileBff.path -Aliases @('mobile-bff') -Publish @('127.0.0.1:18516:9080') -Environment ($common + @{
            MOBILE_BFF_PORT = '9080'; ATTENDANCE_GRPC_ADDRESS = 'static://attendance-service:19093'; SCHEDULE_GRPC_ADDRESS = 'static://schedule-service:19092'; ACADEMIC_GRPC_ADDRESS = 'static://academic-service:19091'; GRPC_SECRET = $Secrets.grpc
        }) -Arguments @('--server.port=9080', '--server.address=0.0.0.0')
    Wait-HostHttp -Port 18516 -Path '/actuator/health'

    $script:services.gateway = Start-JavaService -Name 'gateway' -Jar $jars.gateway.path -Aliases @('api-gateway') -Publish @('127.0.0.1:18515:8080') -Environment ($common + @{
            AUTH_SERVICE_URL = 'http://auth-service:9090'; ACADEMIC_SERVICE_URL = 'http://academic-service:9091'; SCHEDULE_SERVICE_URL = 'http://schedule-service:9092'; ATTENDANCE_SERVICE_URL = 'http://attendance-service:9093'; MOBILE_BFF_URL = 'http://mobile-bff:9080'; REDIS_HOST = 'redis'; REDIS_PORT = '6379'; REDIS_PASSWORD = $Secrets.redis; INTERNAL_ISSUER_SECRET = $Secrets.internalIssuer; GATEWAY_TRUSTED_PROXY_ADDRESSES = $Config.NginxIp; CORS_ALLOWED_ORIGIN = 'https://127.0.0.1:18514'
        }) -Arguments @('--server.port=8080', '--server.address=0.0.0.0')
    Wait-HostHttp -Port 18515 -Path '/actuator/health'
}

function Assert-EdgeIpReservation {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][string]$ExpectedIp
    )
    $inspect = Invoke-DockerSafe -DockerArgs @('inspect', '--format', '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}', $ContainerId) -Purpose 'verify reserved edge IPv4 address' -AllowFailure
    Assert-That ($inspect.ExitCode -eq 0) "edge IP reservation inspect failed for $ContainerId"
    $actualIp = ([string]$inspect.Output).Trim()
    Assert-That ($actualIp -eq $ExpectedIp) "edge IP reservation is not deterministic: expected $ExpectedIp, got $actualIp"
    return [ordered]@{
        state = 'running'
        expected = $ExpectedIp
        actual = $actualIp
        operationalAddressVerified = $true
        verified = $true
    }
}

function Start-Edge {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Config,
        [switch]$ReserveOnly,
        [switch]$StartReserved
    )
    Assert-That (-not ($ReserveOnly -and $StartReserved)) 'edge reservation cannot be both create-only and start-reserved'
    $edgeIp = $Config.NginxIp
    if (-not $StartReserved) {
        $script:services.edge = Start-OwnedContainer -Name "rct-requests-$($script:runId)-nginx" -Image $script:images.nginx -Aliases @('edge-nginx') -Ip $edgeIp -Publish @('127.0.0.1:18514:443') -Mounts @(
            @{ Source = (Join-Path $Config.ConfigDir 'nginx.conf'); Target = '/etc/nginx/nginx.conf'; ReadOnly = $true },
            @{ Source = (Join-Path $Config.ConfigDir 'default.conf'); Target = '/etc/nginx/conf.d/default.conf'; ReadOnly = $true },
            @{ Source = $Config.CertPath; Target = '/etc/nginx/certs/server.crt'; ReadOnly = $true },
            @{ Source = $Config.KeyPath; Target = '/etc/nginx/certs/server.key'; ReadOnly = $true },
            @{ Source = $Config.PwaDistPath; Target = '/usr/share/nginx/html'; ReadOnly = $true }
        ) -Command @('nginx', '-g', 'daemon off;') -CreateOnly:$ReserveOnly
        if ($ReserveOnly) {
            # docker create prepares the endpoint but does not assign an
            # operational container address. The network's --ip-range is the
            # collision guard; inspect is intentionally deferred until start.
            $script:report.assertions.edgeReservation = [ordered]@{
                state = 'created'
                requested = $edgeIp
                operationalAddressVerified = $false
                staticEdgeExcludedFromDynamicRange = $true
                strategy = 'network-ip-range-exclusion'
            }
            return
        }
    }
    Assert-That ($null -ne $script:services.edge) 'reserved edge container is missing before start'
    if ($StartReserved) {
        $start = Invoke-DockerSafe -DockerArgs @('start', $script:services.edge.Id) -Purpose 'start reserved Nginx edge container' -AllowFailure
        Assert-That ($start.ExitCode -eq 0) 'reserved Nginx edge container failed to start'
        $reservation = Assert-EdgeIpReservation -ContainerId $script:services.edge.Id -ExpectedIp $edgeIp
        $script:report.assertions.edgeReservation = $reservation
    } else {
        $reservation = Assert-EdgeIpReservation -ContainerId $script:services.edge.Id -ExpectedIp $edgeIp
    }
    $test = Invoke-OwnedDockerExec -ContainerId $script:services.edge.Id -Command @('nginx', '-t') -Purpose 'validate owned Nginx runtime config' -AllowFailure
    Assert-That ($test.ExitCode -eq 0) 'owned Nginx runtime config failed nginx -t'
    $health = Invoke-RequestsProbe -ExtraArguments @('--health-only')
    Assert-That ($health.status -eq 'PASS' -and $health.mode -eq 'health-only') 'HTTPS edge health probe did not pass with the generated CA'
    $script:report.assertions.edge = [ordered]@{ tls = $true; edgePort = 18514; trustedNginxIp = $edgeIp; trustedGatewayPeer = $edgeIp; reservationBeforeDynamic = $true; defaultRequestBuffering = 'on'; canonicalForwarded = 'empty' }
}

function Limit-EdgeDiagnosticText {
    param(
        [AllowNull()][string]$Text,
        [int]$MaxCharacters = 12000
    )
    if ($null -eq $Text) { return $null }
    $safe = Protect-ReportText $Text
    if ($safe.Length -le $MaxCharacters) { return $safe }
    return $safe.Substring(0, $MaxCharacters) + '<truncated>'
}

function Capture-EdgeFailureDiagnostics {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerId,
        [Parameter(Mandatory = $true)][string]$FailureMessage
    )
    $capture = [ordered]@{
        schema = 'rct.edge-failure.v1'
        status = 'START'
        failure = Limit-EdgeDiagnosticText -Text $FailureMessage -MaxCharacters 4000
        state = $null
        logs = $null
    }
    try {
        $state = Invoke-DockerSafe -DockerArgs @('inspect', '--format', '{{json .State}}', $ContainerId) -Purpose 'capture failed edge container state' -AllowFailure
        if ($state.ExitCode -eq 0) {
            try {
                $stateJson = [string]$state.Output | ConvertFrom-Json -Depth 8
                $capture.state = [ordered]@{
                    status = Limit-EdgeDiagnosticText -Text ([string]$stateJson.Status) -MaxCharacters 200
                    exitCode = $stateJson.ExitCode
                    oomKilled = [bool]$stateJson.OOMKilled
                    error = Limit-EdgeDiagnosticText -Text ([string]$stateJson.Error) -MaxCharacters 4000
                    captureExitCode = $state.ExitCode
                }
            } catch {
                $capture.state = [ordered]@{
                    status = 'unavailable'
                    exitCode = $null
                    oomKilled = $null
                    error = Limit-EdgeDiagnosticText -Text $_.Exception.Message -MaxCharacters 4000
                    captureExitCode = $state.ExitCode
                }
            }
        } else {
            $capture.state = [ordered]@{
                status = 'unavailable'
                exitCode = $null
                oomKilled = $null
                error = Limit-EdgeDiagnosticText -Text ([string]$state.Output) -MaxCharacters 4000
                captureExitCode = $state.ExitCode
            }
        }
    } catch {
        $capture.state = [ordered]@{
            status = 'unavailable'
            exitCode = $null
            oomKilled = $null
            error = Limit-EdgeDiagnosticText -Text $_.Exception.Message -MaxCharacters 4000
            captureExitCode = $null
        }
    }
    try {
        $logs = Invoke-DockerSafe -DockerArgs @('logs', '--tail', '80', '--timestamps', $ContainerId) -Purpose 'capture recent failed edge logs' -AllowFailure
        $capture.logs = [ordered]@{
            tail = 80
            exitCode = $logs.ExitCode
            text = Limit-EdgeDiagnosticText -Text ([string]$logs.Output)
        }
    } catch {
        $capture.logs = [ordered]@{
            tail = 80
            exitCode = $null
            text = Limit-EdgeDiagnosticText -Text $_.Exception.Message -MaxCharacters 4000
        }
    }
    $capture.status = 'CAPTURED'
    $script:report.edgeFailureDiagnostics = $capture
}

function Get-EdgeFailureContainerId {
    if ($script:services -is [System.Collections.IDictionary] -and $script:services.Contains('edge') -and $null -ne $script:services.edge) {
        return [string]$script:services.edge.Id
    }
    $owned = [string[]]$script:ownedContainers.ToArray()
    if ($owned.Count -gt 0) { return $owned[$owned.Count - 1] }
    return $null
}

function Get-MongoSnapshot {
    param([Parameter(Mandatory = $true)][long]$StudentId, [string]$RequestId = '')
    $rid = if ([string]::IsNullOrWhiteSpace($RequestId)) {
        'null'
    } else {
        Assert-That ($RequestId -match '^[0-9a-fA-F]{24}$') 'Mongo request id must be exactly 24 hexadecimal characters'
        "'$RequestId'"
    }
    $scriptText = @"
const d=db.getSiblingDB('attendance_db'),sid=$StudentId,rid=$rid;
const reqFilter=rid===null?{}:{_id:new ObjectId(rid),student_id:sid};
const attFilter=rid===null?{}:{request_id:rid};
const outFilter=rid===null?{event_type:'excuse.requested'}:{event_type:'excuse.requested',payload:{'`$regex':rid}};
function exactJsonInteger(value,context){
  if(typeof value==='number'){
    if(!Number.isSafeInteger(value))throw new Error(context+' must be an exact safe JSON integer');
    return value;
  }
  if(value===null||typeof value!=='object'||value._bsontype!=='Long'||typeof value.toString!=='function'||typeof value.toNumber!=='function'){
    throw new Error(context+' must be a native BSON Long or safe JSON integer');
  }
  const decimal=value.toString();
  if(!/^-?\d+$/.test(decimal))throw new Error(context+' BSON Long has an invalid decimal representation');
  let exact;
  try{exact=BigInt(decimal);}catch(e){throw new Error(context+' BSON Long cannot be represented exactly');}
  const number=value.toNumber();
  if(!Number.isSafeInteger(number)||BigInt(number)!==exact)throw new Error(context+' BSON Long is outside the safe JSON integer range');
  return number;
}
function nonNegativeJsonInteger(value,context){
  const integer=exactJsonInteger(value,context);
  if(integer<0)throw new Error(context+' must be non-negative');
  return integer;
}
print(JSON.stringify({
  excuseTickets:d.excuse_tickets.countDocuments({student_id:sid}),
  receipts:d.student_request_receipts.countDocuments({student_id:sid,command_kind:'EXCUSE'}),
  attachments:d.request_attachments.countDocuments({owner_student_id:sid}),
  outboxRequested:d.attendance_outbox.countDocuments({event_type:'excuse.requested'}),
  requestTickets:d.excuse_tickets.countDocuments(reqFilter),
  requestTicketStatus:rid===null?null:(d.excuse_tickets.find(reqFilter,{_id:1,status:1}).sort({_id:1}).toArray().map(x=>x.status)[0]??null),
  requestAttachments:d.request_attachments.find(attFilter,{_id:1,name:1,type:1,size:1,sha256:1,state:1,uploaded_at:1,expires_at:1,expired_at:1,request_id:1,position:1}).sort({position:1}).toArray().map(x=>({id:String(x._id),name:x.name,content_type:x.type,size:nonNegativeJsonInteger(x.size,'request_attachments.size'),sha256:x.sha256,state:x.state,uploaded_at:x.uploaded_at==null?null:new Date(x.uploaded_at).toISOString(),expires_at:x.expires_at==null?null:new Date(x.expires_at).toISOString(),expired_at:x.expired_at==null?null:new Date(x.expired_at).toISOString(),request_id:x.request_id})),
  requestOutbox:d.attendance_outbox.find(outFilter,{_id:1,event_type:1,payload:1,status:1}).toArray().map(x=>{const envelope=JSON.parse(x.payload),payload=envelope.payload||{};return {id:String(x._id),event_type:x.event_type,status:x.status,payloadTicketId:payload.ticket_id==null?null:String(payload.ticket_id),payloadAttachments:(payload.attachments||[]).map(a=>({id:a.id,name:a.name,content_type:a.content_type,size:nonNegativeJsonInteger(a.size,'outbox.payload.attachments[].size'),sha256:a.sha256,state:a.state,uploaded_at:a.uploaded_at,expires_at:a.expires_at,expired_at:a.expired_at==null?null:a.expired_at}))}})
}));
"@
    $json = Read-MongoJson -ContainerId $script:infra.mongo.Id -JavaScript $scriptText -Purpose 'capture redacted Mongo Requests snapshot'
    try { return ($json | ConvertFrom-Json -Depth 8) } catch { throw 'Mongo snapshot was not valid JSON' }
}

function Get-ExactObjectProperty {
    param(
        [Parameter(Mandatory = $true)][object]$Object,
        [Parameter(Mandatory = $true)][string]$Name
    )
    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Convert-AttachmentTimestamp {
    param([AllowNull()][object]$Value, [Parameter(Mandatory = $true)][string]$Context)
    if ($Value -is [DateTimeOffset]) {
        $parsed = [DateTimeOffset]$Value
    } elseif ($Value -is [DateTime]) {
        $parsed = [DateTimeOffset]$Value
    } else {
        Assert-That ($Value -is [string] -and -not [string]::IsNullOrWhiteSpace([string]$Value)) "$Context must be a non-empty ISO-8601 timestamp"
        try {
            $parsed = [DateTimeOffset]::Parse([string]$Value, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::RoundtripKind)
        } catch {
            throw "$Context must be an ISO-8601 timestamp"
        }
    }
    return $parsed.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ', [Globalization.CultureInfo]::InvariantCulture)
}

function Convert-AttachmentDescriptorToCanonicalObject {
    param(
        [AllowNull()][object]$Item,
        [Parameter(Mandatory = $true)][string]$Context,
        [ValidateSet('Api', 'Mongo')][string]$Format = 'Mongo'
    )
    Assert-That ($null -ne $Item) "$Context must be an attachment descriptor"
    $contentTypeName = if ($Format -eq 'Api') { 'contentType' } else { 'content_type' }
    $sizeName = if ($Format -eq 'Api') { 'sizeBytes' } else { 'size' }
    $uploadedAtName = if ($Format -eq 'Api') { 'uploadedAt' } else { 'uploaded_at' }
    $expiresAtName = if ($Format -eq 'Api') { 'expiresAt' } else { 'expires_at' }
    $expiredAtName = if ($Format -eq 'Api') { 'expiredAt' } else { 'expired_at' }
    $id = Get-ExactObjectProperty -Object $Item -Name 'id'
    $name = Get-ExactObjectProperty -Object $Item -Name 'name'
    $contentType = Get-ExactObjectProperty -Object $Item -Name $contentTypeName
    $size = Get-ExactObjectProperty -Object $Item -Name $sizeName
    $sha256 = Get-ExactObjectProperty -Object $Item -Name 'sha256'
    $state = Get-ExactObjectProperty -Object $Item -Name 'state'
    $uploadedAt = Get-ExactObjectProperty -Object $Item -Name $uploadedAtName
    $expiresAt = Get-ExactObjectProperty -Object $Item -Name $expiresAtName
    $expiredAt = Get-ExactObjectProperty -Object $Item -Name $expiredAtName
    if ($Format -eq 'Api') {
        foreach ($alias in @('content_type', 'size', 'uploaded_at', 'expires_at', 'expired_at')) {
            Assert-That ($null -eq $Item.PSObject.Properties[$alias]) "$Context has non-public API field '$alias'"
        }
    }
    Assert-That ($id -is [string] -and -not [string]::IsNullOrWhiteSpace([string]$id)) "$Context.id must be a non-empty string"
    Assert-That ($name -is [string] -and -not [string]::IsNullOrWhiteSpace([string]$name)) "$Context.name must be a non-empty string"
    Assert-That ($contentType -is [string] -and -not [string]::IsNullOrWhiteSpace([string]$contentType)) "$Context.content_type must be a non-empty string"
    $sizeValue = Assert-JsonInteger -Value $size -Context "$Context.size" -NonNegative
    $digestValue = Assert-Sha256Value -Value $sha256 -Context "$Context.sha256"
    $stateIsActive = if ($Format -eq 'Api') {
        $state -is [string] -and ([string]$state) -ceq 'ACTIVE'
    } else {
        $state -is [string] -and (([string]$state) -ceq 'ACTIVE' -or ([string]$state) -ceq 'active')
    }
    Assert-That $stateIsActive "$Context.state must be ACTIVE for a newly submitted request"
    $canonicalUploadedAt = Convert-AttachmentTimestamp -Value $uploadedAt -Context "$Context.uploaded_at"
    $canonicalExpiresAt = Convert-AttachmentTimestamp -Value $expiresAt -Context "$Context.expires_at"
    Assert-That ([DateTimeOffset]::Parse($canonicalUploadedAt) -lt [DateTimeOffset]::Parse($canonicalExpiresAt)) "$Context.uploaded_at must be before expires_at for a newly submitted request"
    Assert-That ($null -eq $expiredAt) "$Context.expired_at must be null or absent for a newly submitted request"
    $canonicalExpiredAt = $null
    return [ordered]@{
        id = [string]$id
        name = [string]$name
        content_type = [string]$contentType
        size = $sizeValue
        sha256 = $digestValue
        state = ([string]$state).ToLowerInvariant()
        uploaded_at = $canonicalUploadedAt
        expires_at = $canonicalExpiresAt
        expired_at = $canonicalExpiredAt
    }
}

function Convert-AttachmentDescriptorsToCanonicalText {
    param(
        [Parameter(Mandatory = $true)][object[]]$Items,
        [Parameter(Mandatory = $true)][string]$Context,
        [ValidateSet('Api', 'Mongo')][string]$Format = 'Mongo'
    )
    $index = 0
    $canonical = @($Items | ForEach-Object {
            $itemContext = "$Context[$index]"
            $index++
            Convert-AttachmentDescriptorToCanonicalObject -Item $_ -Context $itemContext -Format $Format
        })
    $canonical = @($canonical | Sort-Object -Property id)
    return ConvertTo-Json -InputObject ([object[]]$canonical) -Depth 10 -Compress
}

function Assert-I1MongoDelta {
    param([Parameter(Mandatory = $true)]$Before, [Parameter(Mandatory = $true)]$After, [Parameter(Mandatory = $true)]$Probe)
    Assert-That ([int64]$After.excuseTickets -eq ([int64]$Before.excuseTickets + 1)) 'I1 must create exactly one excuse ticket'
    Assert-That ([int64]$After.receipts -eq ([int64]$Before.receipts + 1)) 'I1 must create exactly one EXCUSE receipt'
    Assert-That ([int64]$After.attachments -eq ([int64]$Before.attachments + 2)) 'I1 must create exactly two attachment documents'
    Assert-That ([int64]$After.outboxRequested -eq ([int64]$Before.outboxRequested + 1)) 'I1 must create exactly one excuse.requested outbox event'
    Assert-That ([int64]$After.requestTickets -eq 1) 'I1 request id must identify exactly one ticket'
    Assert-That ($After.requestTicketStatus -ceq 'submitted') 'I1 persisted request ticket must retain the lowercase submitted Mongo state (API maps it to PENDING)'
    Assert-That (@($After.requestAttachments).Count -eq 2) 'I1 request id must identify exactly two attachments'
    foreach ($attachment in @($After.requestAttachments)) {
        Assert-That ([string]$attachment.request_id -ceq [string]$Probe.i1.requestId) 'I1 Mongo attachment request_id must retain the submitted request id string'
    }
    $expected = Convert-AttachmentDescriptorsToCanonicalText -Items @($Probe.i1.files) -Context 'I1 probe API attachments' -Format Api
    $actualDocuments = Convert-AttachmentDescriptorsToCanonicalText -Items @($After.requestAttachments) -Context 'I1 Mongo request attachments' -Format Mongo
    Assert-That ($actualDocuments -ceq $expected) 'I1 Mongo attachment descriptors do not exactly match the probe response contract'
    $ticketEvents = @($After.requestOutbox | Where-Object { $_.payloadTicketId -ceq [string]$Probe.i1.requestId })
    Assert-That ($ticketEvents.Count -eq 1) 'I1 must persist exactly one expected outbox event for the ticket id'
    $actualOutbox = Convert-AttachmentDescriptorsToCanonicalText -Items @($ticketEvents[0].payloadAttachments) -Context 'I1 outbox payload attachments' -Format Mongo
    Assert-That ($actualOutbox -ceq $expected) 'I1 outbox attachment descriptors do not exactly match the request metadata contract'
}

function Assert-I2MongoUnchanged {
    param([Parameter(Mandatory = $true)]$Before, [Parameter(Mandatory = $true)]$After)
    foreach ($field in @('excuseTickets', 'receipts', 'attachments', 'outboxRequested')) {
        Assert-That ([int64]$After.$field -eq [int64]$Before.$field) "I2 changed Mongo/outbox counter $field"
    }
    Assert-That ($After.requestTicketStatus -ceq $Before.requestTicketStatus) 'I2 changed the persisted request ticket status'
    Assert-That (@($After.requestAttachments).Count -eq @($Before.requestAttachments).Count) 'I2 changed request attachment documents'
}

function Wait-AuthLoginPacing {
    param(
        [AllowNull()][object]$LastLoginAt,
        [scriptblock]$Now = { [DateTimeOffset]::UtcNow },
        [scriptblock]$Sleep = { param([int]$Milliseconds) Start-Sleep -Milliseconds $Milliseconds }
    )
    if ($null -eq $LastLoginAt) { return 0 }
    $last = [DateTimeOffset]$LastLoginAt
    $currentTime = [DateTimeOffset](& $Now)
    $remaining = [int][math]::Ceiling(13000 - ($currentTime - $last).TotalMilliseconds)
    if ($remaining -gt 0) {
        & $Sleep $remaining
        return $remaining
    }
    return 0
}

function Invoke-RequestsProbe {
    param([string[]]$ExtraArguments = @())
    $probeMode = if ($ExtraArguments -contains '--i1-only') { 'i1' } elseif ($ExtraArguments -contains '--i2-only') { 'i2' } elseif ($ExtraArguments -contains '--health-only') { 'health-only' } else { 'runtime' }
    $pacingWaitMilliseconds = 0
    $pacingRecord = $null
    if (-not ($ExtraArguments -contains '--health-only')) {
        $pacingWaitMilliseconds = Wait-AuthLoginPacing -LastLoginAt $script:lastAuthLoginAt
        $loginAt = [DateTimeOffset]::UtcNow
        $script:lastAuthLoginAt = $loginAt
        $pacingRecord = [ordered]@{
            mode = $probeMode
            waitMilliseconds = $pacingWaitMilliseconds
            loginAt = $loginAt.ToString('o')
        }
        # Each probe receives its own record. Later probes never rewrite an
        # earlier I1 record (the report consumes the probe-local value).
        $script:authPacingRecords.Add($pacingRecord)
    }
    $args = @($script:probePath, '--origin', 'https://127.0.0.1:18514', '--ca', (Join-Path $script:keysDir 'server.crt'), '--login', 'student', '--password', 'password') + $ExtraArguments
    $result = Invoke-ExternalSafe -FilePath 'node' -ArgumentList $args -Purpose 'Requests Node HTTPS probe' -TimeoutSeconds 300 -AllowFailure
    $typedStdout = if ($result.PSObject.Properties.Name -contains 'Stdout') { [string]$result.Stdout } else { [string]$result.Output }
    $typedStderr = if ($result.PSObject.Properties.Name -contains 'Stderr') { [string]$result.Stderr } else { '' }
    $probeDiagnostic = [ordered]@{
        mode = $probeMode
        exitCode = $result.ExitCode
        stderr = Limit-EdgeDiagnosticText -Text $typedStderr -MaxCharacters 12000
    }
    $script:report.probeDiagnostics = $probeDiagnostic
    if (-not ($script:report -is [System.Collections.IDictionary] -and @($script:report.Keys) -contains 'probeDiagnosticsHistory')) {
        $script:report.probeDiagnosticsHistory = [System.Collections.Generic.List[object]]::new()
    }
    while ($script:report.probeDiagnosticsHistory.Count -ge 8) { $script:report.probeDiagnosticsHistory.RemoveAt(0) }
    $script:report.probeDiagnosticsHistory.Add($probeDiagnostic)
    $safeOutput = Protect-ReportText $typedStdout
    Assert-That ($result.ExitCode -eq 0) 'Requests Node HTTPS probe failed; raw probe output was withheld'
    try { $probe = ($safeOutput | ConvertFrom-Json -Depth 12) } catch { throw 'Requests probe did not return valid redacted JSON' }
    if ($null -ne $pacingRecord) {
        $probe | Add-Member -NotePropertyName authPacingWaitMilliseconds -NotePropertyValue $pacingWaitMilliseconds
        $probe | Add-Member -NotePropertyName authPacing -NotePropertyValue ([pscustomobject]$pacingRecord)
    }
    return $probe
}

function Get-NginxDiagnosticEvidence {
    $edgeId = $script:services.edge.Id
    $result = Invoke-OwnedDockerExec -ContainerId $edgeId -Command @('sh', '-c', 'cat /var/log/nginx/requests-gate.log 2>/dev/null || true') -Purpose 'read bounded Nginx diagnostic access log'
    $lines = @($result.Output -split '\r?\n' | Where-Object { $_ -match '/api/v1/student/requests/excuse' -and $_ -match '\s413\s' })
    Assert-That ($lines.Count -ge 2) 'Nginx diagnostic log did not show both I2 413 responses'
    $upstreamed = @($lines | Where-Object { $_ -notmatch '\s-\s-\s*$' })
    Assert-That ($upstreamed.Count -eq 0) 'I2 413 request reached an upstream according to Nginx diagnostic log'
    return [ordered]@{ i2_413Lines = $lines.Count; upstreamMarker = 'absent on both 413 lines' }
}

function Remove-OwnedDockerResource {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('container', 'network')][string]$Kind,
        [Parameter(Mandatory = $true)][string]$Identity
    )
    $expectedLabels = "student-requests-gate|$($script:runId)"
    $format = if ($Kind -eq 'container') {
        '{{index .Config.Labels "rct.runtime-owner"}}|{{index .Config.Labels "rct.runtime-run"}}'
    } else {
        '{{index .Labels "rct.runtime-owner"}}|{{index .Labels "rct.runtime-run"}}'
    }
    $inspect = Invoke-DockerSafe -DockerArgs @($Kind, 'inspect', '--format', $format, $Identity) -Purpose "inspect owned $Kind before cleanup" -AllowFailure
    if ($inspect.ExitCode -ne 0) {
        if ([string]$inspect.Output -match '(?i)(no such object|no such container|no such network|not found)') {
            return [ordered]@{ removed = $false; verifiedAbsent = $true; alreadyAbsent = $true }
        }
        throw "owned $Kind inspection failed for $Identity with exit code $($inspect.ExitCode)"
    }
    Assert-That ($inspect.Output.Trim() -eq $expectedLabels) "refusing to remove $Kind $Identity with non-owned labels"
    $removeArgs = if ($Kind -eq 'container') { @('rm', '-f', $Identity) } else { @('network', 'rm', $Identity) }
    $removed = Invoke-DockerSafe -DockerArgs $removeArgs -Purpose "remove owned runtime $Kind" -AllowFailure
    Assert-That ($removed.ExitCode -eq 0) "removing owned $Kind $Identity failed with exit code $($removed.ExitCode)"
    $verify = Invoke-DockerSafe -DockerArgs @($Kind, 'inspect', $Identity) -Purpose "verify owned $Kind removal" -AllowFailure
    if ($verify.ExitCode -eq 0) { throw "owned $Kind $Identity still exists after cleanup" }
    if ([string]$verify.Output -notmatch '(?i)(no such object|no such container|no such network|not found)') {
        throw "unable to verify owned $Kind $Identity removal (exit code $($verify.ExitCode))"
    }
    return [ordered]@{ removed = $true; verifiedAbsent = $true; alreadyAbsent = $false }
}

function Remove-OwnedResources {
    $errors = [System.Collections.Generic.List[string]]::new()
    $removedContainers = [System.Collections.Generic.List[string]]::new()
    $verifiedContainers = [System.Collections.Generic.List[string]]::new()
    $script:report.cleanup.status = 'RUNNING'
    $script:report.cleanup.ownedContainers = @($script:ownedContainers.ToArray())
    $script:report.cleanup.ownedNetwork = $script:ownedNetwork
    $script:report.cleanup.artifactRoot = $script:artifactSnapshotRoot

    $containerIds = [string[]]$script:ownedContainers.ToArray()
    [array]::Reverse($containerIds)
    foreach ($id in $containerIds) {
        try {
            $result = Remove-OwnedDockerResource -Kind 'container' -Identity $id
            if ($result.removed) { $removedContainers.Add($id) }
            if ($result.verifiedAbsent) { $verifiedContainers.Add($id) }
        } catch {
            $errors.Add("container ${id}: $($_.Exception.Message)")
        }
    }

    if ($script:ownedNetwork) {
        try {
            $networkResult = Remove-OwnedDockerResource -Kind 'network' -Identity $script:ownedNetwork
            $script:report.cleanup.networkRemoved = [bool]$networkResult.removed
            $script:report.cleanup.networkVerifiedAbsent = [bool]$networkResult.verifiedAbsent
        } catch {
            $errors.Add("network $($script:ownedNetwork): $($_.Exception.Message)")
        }
    }

    if ($script:artifactSnapshotRoot) {
        try {
            if (Test-Path -LiteralPath $script:artifactSnapshotRoot) {
                $null = Assert-OwnedArtifactPath
                Remove-Item -LiteralPath $script:artifactSnapshotRoot -Recurse -Force
                Assert-That (-not (Test-Path -LiteralPath $script:artifactSnapshotRoot)) 'owned artifact directory still exists after cleanup'
                $script:report.cleanup.artifactRemoved = $true
                $script:report.cleanup.artifactVerifiedAbsent = $true
            } else {
                $script:report.cleanup.artifactVerifiedAbsent = $true
            }
        } catch {
            $errors.Add("artifacts: $($_.Exception.Message)")
        }
    }

    if ($script:keysDir -and (Test-Path -LiteralPath $script:keysDir)) {
        try {
            Assert-OwnedKeysPath
            Remove-Item -LiteralPath $script:keysDir -Recurse -Force
            Assert-That (-not (Test-Path -LiteralPath $script:keysDir)) 'owned keys directory still exists after cleanup'
            $script:report.cleanup.keysRemoved = $true
        } catch {
            $errors.Add("keys: $($_.Exception.Message)")
        }
    }

    $script:report.cleanup.removedContainers = @($removedContainers)
    $script:report.cleanup.verifiedAbsentContainers = @($verifiedContainers)
    $script:report.cleanup.errors = @($errors)
    if ($errors.Count -gt 0) {
        $script:report.cleanup.status = 'FAIL'
        throw ('owned resource cleanup failed: ' + ($errors -join '; '))
    }
    $script:report.cleanup.status = 'PASS'
}

function Invoke-RequestsNetworkProof {
    param(
        [Parameter(Mandatory = $true)][string]$Subnet,
        [string]$EvidencePath = ''
    )
    $script:processExitCode = 0
    $script:runId = "proof-$([guid]::NewGuid().ToString('N').Substring(0, 12))"
    $script:networkName = "rct-requests-$($script:runId)-network"
    $script:runDir = $null
    $script:keysDir = $null
    $script:currentPhase = 'network-proof'
    # Proof reports have their own identity and evidence path. This metadata is
    # initialized before the first Docker command; the historical H32 report
    # is retained verbatim and is never rewritten by a later proof.
    $script:report.runId = $script:runId
    $script:report.environment.runtimeMode = 'network-proof'
    $script:report.source = [ordered]@{
        mode = 'network-proof'
        sourceAnchor = 'requests-runtime/runner.ps1 -NetworkProof production branch'
        runnerPath = $script:runnerPath
        runnerSha256 = Get-Sha256Hex -Path $script:runnerPath
    }
    if ([string]::IsNullOrWhiteSpace($EvidencePath)) { $EvidencePath = Join-Path $script:taskRoot 'r11-docker-network-proof.json' }
    $script:report.scope = 'R11 Docker IPAM and owned-launch proof; product services are NOT_RUN'
    $script:report.status = 'NOT_RUN'
    $script:report.runtime = 'NOT_RUN'
    $script:report.networkProof = [ordered]@{
        status = 'NOT_RUN'
        subnet = $Subnet
        image = $null
        dynamicAddress = $null
        edgeAddress = $null
        maxNetworks = 1
        maxContainersExpected = 3
        maxContainersWorstCase = 4
        hostPorts = @()
        productServicesStarted = $false
        pullPolicy = '--pull=never'
        invalidExecStartFailure = $false
        invalidExecContainerRegistered = $false
    }
    $reportPath = if ([string]::IsNullOrWhiteSpace($EvidencePath)) {
        Join-Path $script:taskRoot 'r10-docker-network-proof.json'
    } else {
        $EvidencePath
    }
    $reportPath = [IO.Path]::GetFullPath($reportPath)
    $proofFailure = $null
    $cleanupFailure = $null
    try {
        $script:currentPhase = 'network-proof-preflight'
        Add-PhaseDiagnostic -Name $script:currentPhase -Status 'START' -Message 'verify one existing pinned local image before creating the proof network'
        $script:images = Get-PinnedImages
        $proofImage = [string]$script:images.nginx
        $script:report.networkProof.image = $proofImage
        $imageInspect = Invoke-DockerSafe -DockerArgs @('image', 'inspect', $proofImage) -Purpose 'verify existing pinned nginx image for network proof' -AllowFailure
        Assert-That ($imageInspect.ExitCode -eq 0) 'pinned nginx proof image is not present locally; refusing any pull'
        Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'the pinned nginx image exists locally; no image pull is permitted'

        $script:currentPhase = 'network-proof-create'
        $networkPlan = Get-DeterministicNetworkPlan -Subnet $Subnet
        $existingSubnets = @(Get-DockerNetworkSubnets)
        $script:report.network.existingSubnets = @($existingSubnets)
        $script:report.network.collisionChecked = $true
        Assert-That (-not (@($existingSubnets | Where-Object { Test-CidrOverlap -Candidate $networkPlan.subnet -Existing $_ })).Count) 'proof subnet overlaps an existing Docker network; refusing network create'
        $null = New-RequestsOwnedNetwork -NetworkPlan $networkPlan
        $ipam = Invoke-DockerSafe -DockerArgs @('network', 'inspect', '--format', '{{json .IPAM.Config}}', $script:ownedNetwork) -Purpose 'verify actual proof network IPAM'
        $ipamConfig = @($ipam.Output.Trim() | ConvertFrom-Json -Depth 8)
        Assert-That ($ipamConfig.Count -eq 1) 'proof network returned no single IPAM configuration'
        Assert-That ([string]$ipamConfig[0].Subnet -eq [string]$networkPlan.subnet) 'proof network subnet differs from the validated /24'
        Assert-That ([string]$ipamConfig[0].Gateway -eq [string]$networkPlan.gateway) 'proof network gateway differs from the validated .1 address'
        Assert-That ([string]$ipamConfig[0].IPRange -eq [string]$networkPlan.dynamicIpRange) 'proof network dynamic IP range differs from the edge-excluding /25'
        $networkMeta = Invoke-DockerSafe -DockerArgs @('network', 'inspect', '--format', '{{.Name}}|{{index .Labels "rct.runtime-owner"}}|{{index .Labels "rct.runtime-run"}}', $script:ownedNetwork) -Purpose 'verify owned proof network labels'
        $networkMetaParts = $networkMeta.Output.Trim().Split('|')
        Assert-That ($networkMetaParts.Count -eq 3 -and $networkMetaParts[0] -eq $script:networkName -and $networkMetaParts[1] -eq 'student-requests-gate' -and $networkMetaParts[2] -eq $script:runId) 'proof network name or ownership labels are not exact'
        Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'production network helper created the exact gateway and edge-excluding IPAM range'

        $script:currentPhase = 'network-proof-launch'
        $edge = Start-OwnedContainer -Name "rct-requests-$($script:runId)-nginx" -Image $proofImage -Ip $networkPlan.edgeIp -Command @('nginx', '-g', 'daemon off;') -CreateOnly
        $dynamic = Start-OwnedContainer -Name "rct-requests-$($script:runId)-dynamic" -Image $proofImage -Command @('nginx', '-g', 'daemon off;')
        $dynamicInspect = Invoke-DockerSafe -DockerArgs @('inspect', '--format', '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}', $dynamic.Id) -Purpose 'verify dynamic proof container address'
        $dynamicIp = ([string]$dynamicInspect.Output).Trim()
        $dynamicPrefix = ([string]$networkPlan.subnet -split '/')[0].Split('.')[0..2] -join '.'
        $dynamicParts = $dynamicIp.Split('.')
        Assert-That ($dynamicParts.Count -eq 4 -and ($dynamicParts[0..2] -join '.') -eq $dynamicPrefix) 'dynamic proof container escaped the selected /24'
        $dynamicOctet = [int]$dynamicParts[3]
        Assert-That ($dynamicOctet -ge 128 -and $dynamicOctet -le 254) 'dynamic proof container was allocated outside the explicit .128/25 range'
        Assert-That ($dynamicIp -ne $networkPlan.edgeIp) 'dynamic proof container received the static edge address'
        $edgeStart = Invoke-DockerSafe -DockerArgs @('start', $edge.Id) -Purpose 'start static edge proof container'
        Assert-That ($edgeStart.ExitCode -eq 0) 'static edge proof container failed to start'
        $edgeReservation = Assert-EdgeIpReservation -ContainerId $edge.Id -ExpectedIp $networkPlan.edgeIp
        $edgeMeta = Invoke-DockerSafe -DockerArgs @('inspect', '--format', '{{.Name}}|{{index .Config.Labels "rct.runtime-owner"}}|{{index .Config.Labels "rct.runtime-run"}}', $edge.Id) -Purpose 'verify static edge proof container labels'
        $edgeMetaParts = $edgeMeta.Output.Trim().Split('|')
        Assert-That ($edgeMetaParts.Count -eq 3 -and $edgeMetaParts[0].TrimStart([char]'/') -eq $edge.Name -and $edgeMetaParts[1] -eq 'student-requests-gate' -and $edgeMetaParts[2] -eq $script:runId) 'static edge proof container name or ownership labels are not exact'
        $dynamicMeta = Invoke-DockerSafe -DockerArgs @('inspect', '--format', '{{.Name}}|{{index .Config.Labels "rct.runtime-owner"}}|{{index .Config.Labels "rct.runtime-run"}}', $dynamic.Id) -Purpose 'verify dynamic proof container labels'
        $dynamicMetaParts = $dynamicMeta.Output.Trim().Split('|')
        Assert-That ($dynamicMetaParts.Count -eq 3 -and $dynamicMetaParts[0].TrimStart([char]'/') -eq $dynamic.Name -and $dynamicMetaParts[1] -eq 'student-requests-gate' -and $dynamicMetaParts[2] -eq $script:runId) 'dynamic proof container name or ownership labels are not exact'
        $script:report.networkProof.dynamicAddress = $dynamicIp
        $script:report.networkProof.edgeAddress = $edgeReservation.actual
        Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'dynamic allocation stayed in .128/25 and static .10 became operational only after start'

        # Exercise the production create/register/start path with a deliberately
        # invalid executable. Docker create succeeds, the ID is registered before
        # start, and the OCI runtime must reject the missing entrypoint. The
        # resulting stopped container remains in the owned cleanup ledger.
        $script:currentPhase = 'network-proof-start-failure'
        $invalidExecName = "rct-requests-$($script:runId)-invalid-exec"
        $invalidExecRejected = $false
        try {
            $null = Start-OwnedContainer -Name $invalidExecName -Image $proofImage -Entrypoint '/rct-proof/missing-executable'
        } catch {
            $invalidExecRejected = $true
        }
        Assert-That $invalidExecRejected 'invalid executable proof container unexpectedly started'
        $invalidExecCreateCommands = @($script:report.commands | Where-Object { $_.purpose -eq "create owned $invalidExecName container" })
        $invalidExecStartCommands = @($script:report.commands | Where-Object { $_.purpose -eq "start owned $invalidExecName container" })
        Assert-That ($invalidExecCreateCommands.Count -eq 1 -and $invalidExecCreateCommands[0].exitCode -eq 0) 'invalid executable proof did not create the owned container successfully'
        Assert-That ($invalidExecStartCommands.Count -eq 1 -and $invalidExecStartCommands[0].exitCode -ne 0) 'invalid executable proof did not fail at the docker start boundary'
        Assert-That (@($script:ownedContainerNames | Where-Object { $_ -ceq $invalidExecName }).Count -eq 1) 'invalid executable proof did not register the container before start'
        $script:report.networkProof.invalidExecStartFailure = $true
        $script:report.networkProof.invalidExecContainerRegistered = $true
        Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'invalid executable failed at docker start after create and immediate ownership registration'

        $script:currentPhase = 'network-proof-pull-policy'
        $missingImage = 'nginx@sha256:' + (('0' * 64) -join '')
        $missingName = "rct-requests-$($script:runId)-missing"
        $missingImageInspect = Invoke-DockerSafe -DockerArgs @('image', 'inspect', $missingImage) -Purpose 'verify missing-image proof reference is absent' -AllowFailure
        Assert-That ($missingImageInspect.ExitCode -ne 0) 'missing-image proof reference unexpectedly exists locally'
        $missingBefore = Invoke-DockerSafe -DockerArgs @('ps', '-a', '--filter', "name=^$missingName`$", '--format', '{{.ID}}') -Purpose 'verify missing-image proof name is unused' -AllowFailure
        Assert-That ($missingBefore.ExitCode -eq 0 -and [string]::IsNullOrWhiteSpace($missingBefore.Output)) 'missing-image proof name is already occupied'
        $missingRejected = $false
        try {
            $null = Start-OwnedContainer -Name $missingName -Image $missingImage -CreateOnly
        } catch {
            $missingRejected = $true
        }
        Assert-That $missingRejected 'missing local image unexpectedly succeeded with pull disabled'
        $missingAfter = Invoke-DockerSafe -DockerArgs @('ps', '-a', '--filter', "name=^$missingName`$", '--format', '{{.ID}}') -Purpose 'verify missing-image create left no container' -AllowFailure
        Assert-That ($missingAfter.ExitCode -eq 0 -and [string]::IsNullOrWhiteSpace($missingAfter.Output)) 'missing-image create left a container behind'
        $script:report.networkProof.missingImageAbsent = $true
        $script:report.networkProof.missingContainerAbsent = $true
        $launchCommands = @($script:report.commands | Where-Object { $_.command -match '\bdocker create\b' })
        Assert-That ($launchCommands.Count -ge 4) 'proof did not exercise create, invalid-start and missing-image launcher calls'
        Assert-That (@($launchCommands | Where-Object { $_.command -notmatch '(^|\s)--pull=never(\s|$)' }).Count -eq 0) 'an owned create command omitted --pull=never'
        $script:report.networkProof.pullPolicyVerified = $true
        Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'create/start and missing-image paths all carried --pull=never and never requested a download'
        $script:report.networkProof.status = 'PASS'
        $script:report.status = 'PASS'
    } catch {
        $proofFailure = $_.Exception.Message
        $script:report.networkProof.status = 'FAIL'
        $script:report.status = 'FAIL'
        $script:report.failure = [ordered]@{ message = Protect-ReportText $proofFailure }
        try { Add-PhaseDiagnostic -Name $script:currentPhase -Status 'FAIL' -Message $proofFailure } catch { }
    } finally {
        $script:currentPhase = 'network-proof-cleanup'
        try {
            Add-PhaseDiagnostic -Name $script:currentPhase -Status 'START' -Message 'remove only proof-owned containers and network, then verify absence'
            $expectedContainerCount = $script:ownedContainers.Count
            Remove-OwnedResources
            Assert-That ($script:report.cleanup.verifiedAbsentContainers.Count -eq $expectedContainerCount) 'proof cleanup did not verify every owned container absent'
            if ($null -ne $script:ownedNetwork) { Assert-That ([bool]$script:report.cleanup.networkVerifiedAbsent) 'proof cleanup did not verify the owned network absent' }
            Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'proof-owned resources were removed and verified absent'
        } catch {
            $cleanupFailure = $_.Exception.Message
            $script:report.status = 'FAIL'
            $script:report.networkProof.status = 'FAIL'
            $script:report.cleanup.status = 'FAIL'
            $cleanupMessage = Protect-ReportText $cleanupFailure
            if ($script:report.PSObject.Properties.Name -contains 'failure') {
                $script:report.failure.message = "$($script:report.failure.message); $cleanupMessage"
            } else {
                $script:report.failure = [ordered]@{ message = $cleanupMessage }
            }
            try { Add-PhaseDiagnostic -Name $script:currentPhase -Status 'FAIL' -Message $cleanupMessage } catch { }
        }
        try {
            $reportParent = Split-Path -Parent $reportPath
            if ($reportParent -and -not (Test-Path -LiteralPath $reportParent)) { New-Item -ItemType Directory -Path $reportParent -Force | Out-Null }
            Write-ReportFile -Path $reportPath
        } catch {
            $script:processExitCode = 1
            $script:report.status = 'FAIL'
            $script:report.networkProof.status = 'FAIL'
            $reportFailure = Protect-ReportText "proof evidence write failed: $($_.Exception.Message)"
            if ($script:report.PSObject.Properties.Name -contains 'failure') {
                $script:report.failure.message = "$($script:report.failure.message); $reportFailure"
            } else {
                $script:report.failure = [ordered]@{ message = $reportFailure }
            }
        }
    }
    if ($null -ne $proofFailure -or $null -ne $cleanupFailure) { $script:processExitCode = 1 }
    return $script:processExitCode
}

function Invoke-FullRuntime {
    $script:currentPhase = 'runtime-preflight'
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'START' -Message 'owned ports, pinned images and collision-checked disposable network'
    # Pin and re-check the exact clean source state before creating any run
    # directory, network or container. A dirty/swap fixture therefore fails
    # without a resource leak, while the edge bytes used later remain the
    # validated read-once strings.
    Assert-UnionSourceStability -Context 'runtime resource preflight' | Out-Null
    New-RunIdentity
    $null = New-VerifiedArtifactSnapshot
    Assert-HostPortsFree
    $script:images = Get-PinnedImages
    Assert-PinnedImagesPresent
    $script:subnet = Select-DisposableSubnet
    $networkPlan = Get-DeterministicNetworkPlan -Subnet $script:subnet
    $null = New-RequestsOwnedNetwork -NetworkPlan $networkPlan

    $script:runtimeSecrets = @(
        (New-RandomSecret), (New-RandomSecret), (New-RandomSecret), (New-RandomSecret), (New-RandomSecret), (New-RandomSecret),
        'password'
    )
    $secrets = @{
        dbAcademic = $script:runtimeSecrets[0]; dbSchedule = $script:runtimeSecrets[1]; redis = $script:runtimeSecrets[2]; rabbit = $script:runtimeSecrets[3]; grpc = $script:runtimeSecrets[4]; internalIssuer = $script:runtimeSecrets[5]; tma = New-RandomSecret
    }
    $script:runtimeSecrets += $secrets.tma
    Assert-UnionSourceStability -Context 'runtime config preflight' | Out-Null
    $edgeConfig = New-RuntimeConfig
    $edgeConfig.NginxIp = $networkPlan.edgeIp
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'owned network and source artifacts are ready'
    $script:currentPhase = 'edge-reservation'
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'START' -Message 'prepare the static edge IPv4 before dynamic backend allocation; operational address is verified after start'
    Assert-UnionSourceStability -Context 'edge reservation preflight' | Out-Null
    Start-Edge -Config $edgeConfig -ReserveOnly
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'static edge endpoint was created before backend startup and excluded from the dynamic IPAM range'
    $script:currentPhase = 'infrastructure'
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'START' -Message 'five disposable infrastructure containers and Mongo rs0'
    Assert-UnionSourceStability -Context 'infrastructure start preflight' | Out-Null
    Start-Infrastructure -Secrets $secrets
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'infrastructure readiness passed'
    $script:currentPhase = 'backend-services'
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'START' -Message 'six Java services with runtime-only actuator diagnostics'
    Assert-UnionSourceStability -Context 'backend start preflight' | Out-Null
    Start-BackendServices -Secrets $secrets -Config $edgeConfig
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'backend service readiness and disposable fixture passed'
    $script:currentPhase = 'edge'
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'START' -Message 'owned Nginx TLS edge with actual PWA dist'
    Assert-UnionSourceStability -Context 'edge start preflight' | Out-Null
    Start-Edge -Config $edgeConfig -StartReserved
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'Nginx syntax and HTTPS health passed'

    $script:currentPhase = 'i1'
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'START' -Message 'I1 create/download/replay/Mongo delta'
    $studentId = [long]$script:report.fixture.studentId
    $baseline = Get-MongoSnapshot -StudentId $studentId
    $probeI1 = Invoke-RequestsProbe -ExtraArguments @('--i1-only')
    Assert-That ($probeI1.status -eq 'PASS' -and $probeI1.i1.status -eq 'PASS') 'I1 probe did not pass'
    $afterI1 = Get-MongoSnapshot -StudentId $studentId -RequestId $probeI1.i1.requestId
    Assert-I1MongoDelta -Before $baseline -After $afterI1 -Probe $probeI1
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'I1 exact files, replay identity, attachment hashes and ticket_id outbox evidence passed'
    $script:currentPhase = 'i2'
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'START' -Message 'I2 fixed/chunked 413/no-store/no-side-effect gates'
    $gatewayBefore = Get-ActuatorRequestCount -Port 18515
    $bffBefore = Get-ActuatorRequestCount -Port 18516
    $probeI2 = Invoke-RequestsProbe -ExtraArguments @('--i2-only')
    Assert-That ($probeI2.status -eq 'PASS' -and $probeI2.i2.status -eq 'PASS') 'I2 probe did not pass'
    $gatewayAfter = Get-ActuatorRequestCount -Port 18515
    $bffAfter = Get-ActuatorRequestCount -Port 18516
    Assert-That ($gatewayAfter -eq $gatewayBefore) 'I2 changed the Gateway exact URI request counter'
    Assert-That ($bffAfter -eq $bffBefore) 'I2 changed the BFF exact URI request counter'
    $afterI2 = Get-MongoSnapshot -StudentId $studentId -RequestId $probeI1.i1.requestId
    Assert-I2MongoUnchanged -Before $afterI1 -After $afterI2
    $nginxEvidence = Get-NginxDiagnosticEvidence
    Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'I2 edge rejection, actuator counters, Mongo and outbox invariants passed'
    $script:report.runtime = 'PASS'
    $script:report.status = 'PASS'
    $script:report.assertions.i1 = [ordered]@{
        status = 'PASS'; exactFiles = 2; eachFileBytes = 10485760; totalMultipartBelowCap = $true; idempotencyReplaySameId = $true
        requestId = $probeI1.i1.requestId; session = $probeI1.i1.session; detail = $probeI1.i1.detail; authPacingWaitMilliseconds = $probeI1.authPacingWaitMilliseconds; authPacing = $probeI1.authPacing
        files = @($probeI1.i1.files | ForEach-Object { [ordered]@{ name = $_.name; bytes = $_.sizeBytes; sha256 = $_.sha256 } });
        mongo = [ordered]@{ excuseTicketsDelta = 1; receiptsDelta = 1; attachmentDocumentsDelta = 2; requestedOutboxDelta = 1; persistedTicketStatus = $afterI1.requestTicketStatus }
    }
    $script:report.assertions.i2 = [ordered]@{
        status = 'PASS'; session = $probeI2.i2.session; authPacingWaitMilliseconds = $probeI2.authPacingWaitMilliseconds; authPacing = $probeI2.authPacing; fixed = $probeI2.i2.fixed; chunked = $probeI2.i2.chunked; bodyBytes = $probeI2.i2.bodyBytes
        noStore = $true; gatewayUriCounterBefore = $gatewayBefore; gatewayUriCounterAfter = $gatewayAfter; bffUriCounterBefore = $bffBefore; bffUriCounterAfter = $bffAfter
        mongoUnchanged = $true; nginx = $nginxEvidence
    }
}

if ($NetworkProof) {
    if ([string]::IsNullOrWhiteSpace($NetworkProofSubnet)) { Write-Error 'NetworkProofSubnet is required with -NetworkProof'; exit 1 }
    $proofEvidencePath = if ([string]::IsNullOrWhiteSpace($NetworkProofEvidencePath)) { Join-Path $script:taskRoot 'r11-docker-network-proof.json' } else { [IO.Path]::GetFullPath($NetworkProofEvidencePath) }
    $script:processExitCode = Invoke-RequestsNetworkProof -Subnet $NetworkProofSubnet -EvidencePath $proofEvidencePath
    if ($script:processExitCode -eq 0) { Write-Output "NETWORK_PROOF_PASS: evidence written to $proofEvidencePath" }
    if ($script:processExitCode -ne 0) { exit $script:processExitCode }
    exit 0
}

try {
    # Resolve user-supplied UnionRepo only after the report boundary exists so
    # invalid paths still produce redacted preflight diagnostics.
    $script:unionRoot = (Resolve-Path -LiteralPath $UnionRepo -ErrorAction Stop).Path
    $script:pwaDistPath = Join-Path $script:unionRoot 'frontends\pwa-vue\dist'
    $script:report.environment.unionRepo = $script:unionRoot
    $script:report.environment.pwaDist = $script:pwaDistPath
    Add-PhaseDiagnostic -Name 'source-validation' -Status 'START' -Message 'source paths and frozen edge contract'
    $null = Test-SourceContract
    Add-PhaseDiagnostic -Name 'source-validation' -Status 'PASS' -Message 'PowerShell source, accepted edge/seed paths, trusted build manifest artifacts and actual PWA dist are present'
    if ($ValidateOnly) {
        Invoke-ValidateOnlyChecks
        $script:report.runtime = 'NOT_RUN'
        $script:report.status = 'SOURCE_VALID'
        $script:sourceValidationMessage = 'SOURCE_VALID: Requests runtime sources, pinned images and deterministic checks passed; runtime NOT_RUN.'
    }
    else {
        Invoke-FullRuntime
    }
} catch {
    $script:processExitCode = 1
    $script:report.status = 'FAIL'
    $script:report.runtime = if ($ValidateOnly) { 'NOT_RUN' } else { 'FAIL' }
    $originalFailure = Protect-ReportText $_.Exception.Message
    $script:report.failure = [ordered]@{ message = $originalFailure }
    if ($script:currentPhase -in @('edge-reservation', 'edge')) {
        try {
            $edgeFailureContainerId = Get-EdgeFailureContainerId
            if (-not [string]::IsNullOrWhiteSpace($edgeFailureContainerId)) {
                Capture-EdgeFailureDiagnostics -ContainerId $edgeFailureContainerId -FailureMessage $originalFailure
            } else {
                $script:report.edgeFailureDiagnostics = [ordered]@{
                    schema = 'rct.edge-failure.v1'
                    status = 'NO_OWNED_CONTAINER'
                    failure = $originalFailure
                    state = $null
                    logs = $null
                }
            }
        } catch {
            $script:report.edgeFailureDiagnostics = [ordered]@{
                schema = 'rct.edge-failure.v1'
                status = 'CAPTURE_FAILED'
                failure = $originalFailure
                captureError = Limit-EdgeDiagnosticText -Text $_.Exception.Message -MaxCharacters 4000
                state = $null
                logs = $null
            }
        }
    }
    try { Add-PhaseDiagnostic -Name $script:currentPhase -Status 'FAIL' -Message $_.Exception.Message } catch { }
    Write-Error $script:report.failure.message
} finally {
    if ($ValidateOnly) {
        try {
            Write-ReportFile -Path (Join-Path $script:taskRoot 'source-validation.json')
        } catch {
            $script:processExitCode = 1
            $script:report.status = 'FAIL'
            $script:report.failure = [ordered]@{ message = Protect-ReportText "source-validation report write failed: $($_.Exception.Message)" }
        }
    } elseif ($script:runDir) {
        $script:currentPhase = 'cleanup'
        Add-PhaseDiagnostic -Name $script:currentPhase -Status 'START' -Message 'verify labels, remove owned containers/network/keys, and verify absence'
        try {
            Remove-OwnedResources
            Add-PhaseDiagnostic -Name $script:currentPhase -Status 'PASS' -Message 'all owned resources removed and verified absent'
        } catch {
            $script:processExitCode = 1
            $script:report.status = 'FAIL'
            $script:report.runtime = 'FAIL'
            $script:report.cleanup.status = 'FAIL'
            $message = Protect-ReportText $_.Exception.Message
            if (Test-ReportHasFailure) {
                $script:report.failure.message = "$($script:report.failure.message); $message"
            } else {
                $script:report.failure = [ordered]@{ message = $message }
            }
            Add-PhaseDiagnostic -Name $script:currentPhase -Status 'FAIL' -Message $message
        }
        try {
            if (-not (Test-Path -LiteralPath $script:runDir)) { New-Item -ItemType Directory -Path $script:runDir -Force | Out-Null }
            Write-ReportFile -Path (Join-Path $script:runDir 'report.json')
        } catch {
            $script:processExitCode = 1
            $script:report.status = 'FAIL'
            $script:report.cleanup.status = 'FAIL'
            $message = Protect-ReportText "runtime report write failed: $($_.Exception.Message)"
            if (Test-ReportHasFailure) {
                $script:report.failure.message = "$($script:report.failure.message); $message"
            } else {
                $script:report.failure = [ordered]@{ message = $message }
            }
        }
    } else {
        # Full-mode source/preflight failures happen before New-RunIdentity and
        # therefore have no owned run directory. Persist redacted diagnostics
        # without creating runtime resources.
        try {
            Write-ReportFile -Path $script:preflightReportPath
        } catch {
            $script:processExitCode = 1
            $script:report.status = 'FAIL'
            $script:report.runtime = 'FAIL'
            $message = Protect-ReportText "preflight report write failed: $($_.Exception.Message)"
            if (Test-ReportHasFailure) {
                $script:report.failure.message = "$($script:report.failure.message); $message"
            } else {
                $script:report.failure = [ordered]@{ message = $message }
            }
        }
    }
}

if ($script:processExitCode -eq 0 -and $script:sourceValidationMessage) { Write-Output $script:sourceValidationMessage }
if ($script:processExitCode -ne 0) { exit $script:processExitCode }
