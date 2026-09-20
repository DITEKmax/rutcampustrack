#requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$UnionRepo,
    [Parameter(Mandatory = $true)]
    [string]$ExpectedRevision,
    [string]$ManifestPath = '',
    [string]$RunsRoot = '',
    [switch]$PublicationHarness
)

$requestedManifestPath = $ManifestPath
$requestedRunsRoot = $RunsRoot

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:taskRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$script:unionRoot = $null
$script:manifestPath = $null
$script:runsRoot = $null
$script:runId = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + [Guid]::NewGuid().ToString('N')
$script:runDir = $null
$script:manifestCommitted = $false
$script:manifestTempPath = $null
$script:manifestTempOwned = $false
$script:preparedManifestSha256 = $null
$script:faultInjection = [Environment]::GetEnvironmentVariable('RCT_I6_FAULT_POINT')
$script:manifestCommands = [System.Collections.Generic.List[object]]::new()
$script:commandEvidence = [System.Collections.Generic.List[object]]::new()
$script:report = [ordered]@{
    schema = 'rct.i6-build-producer.v1'
    status = 'NOT_RUN'
    runId = $script:runId
    startedAt = [DateTimeOffset]::UtcNow.ToString('o')
    finishedAt = $null
    unionRepo = $null
    expectedRevision = $ExpectedRevision
    manifestPath = $null
    manifestSha256 = $null
    cleanBeforeBuild = $null
    cleanAfterBuild = $null
    environment = [ordered]@{}
    commands = $script:commandEvidence
    artifacts = [ordered]@{}
    failure = $null
    limitations = @(
        'Build producer is external evidence for the exact clean integration worktree; it does not start services or Docker.',
        'A failed command stops the run and never emits a trusted build manifest.',
        'No dependency install or cache copy is performed by this producer.'
    )
}

function Assert-That {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Get-Sha256BytesHex {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function Get-Sha256Hex {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-Sha256Text {
    param([Parameter(Mandatory = $true)][string]$Text)
    return Get-Sha256BytesHex -Bytes ([Text.Encoding]::UTF8.GetBytes($Text))
}

function Assert-NoReparsePath {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Context
    )
    $full = [IO.Path]::GetFullPath($Path)
    $existing = $full
    while (-not [string]::IsNullOrWhiteSpace($existing)) {
        if (Test-Path -LiteralPath $existing) {
            $item = Get-Item -LiteralPath $existing -Force
            Assert-That (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "$Context contains a reparse path: $existing"
        }
        $parent = [IO.Path]::GetDirectoryName($existing)
        if ([string]::IsNullOrWhiteSpace($parent) -or $parent -ceq $existing) { break }
        $existing = $parent
    }
    return $full
}

function Assert-ConfinedToTaskRoot {
    param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)][string]$Context)
    $full = [IO.Path]::GetFullPath($Path)
    $root = $script:taskRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $prefix = $root + [IO.Path]::DirectorySeparatorChar
    Assert-That ($full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) "$Context is outside the producer root"
    return $full
}

function Get-PlannedOutputPaths {
    param([Parameter(Mandatory = $true)][string]$Repo)
    $paths = [System.Collections.Generic.List[string]]::new()
    foreach ($spec in @(Get-JarSpec)) {
        $paths.Add((Join-Path $Repo $spec.Relative))
    }
    $paths.Add((Join-Path $Repo 'frontends/pwa-vue/dist'))
    return @($paths)
}

function Assert-PlannedOutputPaths {
    param(
        [Parameter(Mandatory = $true)][string]$Repo,
        [Parameter(Mandatory = $true)][string]$ManifestPath,
        [Parameter(Mandatory = $true)][string]$RunsRoot
    )
    foreach ($path in @(Get-PlannedOutputPaths -Repo $Repo)) {
        Assert-NoReparsePath -Path $path -Context "planned build output $path" | Out-Null
    }
    Assert-NoReparsePath -Path $ManifestPath -Context 'planned manifest output' | Out-Null
    Assert-NoReparsePath -Path $RunsRoot -Context 'planned runs output' | Out-Null
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
    $canonicalFiles = @($Files | ForEach-Object {
            [ordered]@{
                bytes = [int64]$_.bytes
                relativePath = [string]$_.relativePath
                sha256 = [string]$_.sha256
            }
        })
    return ConvertTo-Json -InputObject ([object[]]$canonicalFiles) -Depth 6 -Compress
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

function Assert-NoPreexistingBuildArtifacts {
    param([Parameter(Mandatory = $true)][string]$Repo)
    foreach ($spec in @(Get-JarSpec)) {
        $directory = Join-Path $Repo $spec.Relative
        Assert-NoReparsePath -Path $directory -Context "preflight $($spec.Name) build directory" | Out-Null
        if (-not (Test-Path -LiteralPath $directory)) { continue }
        $jars = @(Get-ChildItem -LiteralPath $directory -File -Force | Where-Object { $_.Extension -ieq '.jar' })
        if ($jars.Count -ne 0) {
            $firstJar = $jars | Select-Object -First 1
            throw "preflight found stale $($spec.Name) JAR output: $($firstJar.FullName)"
        }
    }
    $dist = Join-Path $Repo 'frontends/pwa-vue/dist'
    Assert-NoReparsePath -Path $dist -Context 'preflight PWA dist' | Out-Null
    if (Test-Path -LiteralPath $dist) {
        $children = @(Get-ChildItem -LiteralPath $dist -Force)
        Assert-That ($children.Count -eq 0) "preflight found stale PWA dist content: $dist"
    }
}

function Get-PwaDistManifest {
    param([Parameter(Mandatory = $true)][string]$Directory)
    Assert-That (Test-Path -LiteralPath $Directory -PathType Container) "built PWA dist is missing: $Directory"
    Assert-NoReparsePath -Path $Directory -Context 'built PWA dist' | Out-Null
    $root = [IO.Path]::GetFullPath($Directory)
    $stack = [System.Collections.Generic.Stack[string]]::new()
    $stack.Push($root)
    $files = [System.Collections.Generic.List[object]]::new()
    while ($stack.Count -gt 0) {
        $current = $stack.Pop()
        foreach ($child in @(Get-ChildItem -LiteralPath $current -Force)) {
            Assert-That (($child.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) "PWA dist contains a reparse path: $($child.FullName)"
            if ($child.PSIsContainer) {
                $stack.Push($child.FullName)
            }
            else {
                $files.Add([ordered]@{
                        relativePath = [IO.Path]::GetRelativePath($root, $child.FullName).Replace('\', '/')
                        bytes = [int64]$child.Length
                        sha256 = Get-Sha256Hex -Path $child.FullName
                    })
            }
        }
    }
    Assert-That ($files.Count -gt 0) 'built PWA dist contains no files'
    $sorted = @(Sort-CanonicalPathObjects -Items @($files))
    foreach ($required in @('index.html', 'sw.js', 'sw-assets.js')) {
        Assert-That (@($sorted | Where-Object { $_.relativePath -ceq $required }).Count -eq 1) "built PWA dist is missing required file: $required"
    }
    $canonical = Convert-PwaFilesToCanonicalText -Files $sorted
    return [ordered]@{
        fileCount = $sorted.Count
        manifestSha256 = Get-Sha256Text -Text $canonical
        files = $sorted
    }
}

function Get-BuiltJarEntries {
    param([Parameter(Mandatory = $true)][string]$Repo)
    $entries = [System.Collections.Generic.List[object]]::new()
    foreach ($spec in @(Get-JarSpec)) {
        $directory = Join-Path $Repo $spec.Relative
        Assert-That (Test-Path -LiteralPath $directory -PathType Container) "built $($spec.Name) directory is missing: $($spec.Relative)"
        Assert-NoReparsePath -Path $directory -Context "built $($spec.Name) directory" | Out-Null
        $jarFiles = @(Get-ChildItem -LiteralPath $directory -File -Force | Where-Object { $_.Extension -ieq '.jar' })
        Assert-That ($jarFiles.Count -gt 0) "built $($spec.Name) has no JAR output"
        foreach ($jar in $jarFiles) {
            Assert-That ($jar.Name -like $spec.Pattern) "built $($spec.Name) has unexpected JAR name: $($jar.Name)"
        }
        $executable = @($jarFiles | Where-Object {
                $_.Name -notlike '*-plain.jar' -and
                $_.Name -notlike '*-sources.jar' -and
                $_.Name -notlike '*-javadoc.jar'
            })
        Assert-That ($executable.Count -eq 1) "built $($spec.Name) must contain exactly one executable bootJar"
        $jar = $executable[0]
        $entries.Add([ordered]@{
                bytes = [int64]$jar.Length
                name = [string]$spec.Name
                relativePath = [IO.Path]::GetRelativePath($Repo, $jar.FullName).Replace('\', '/')
                sha256 = Get-Sha256Hex -Path $jar.FullName
            })
    }
    return @(Sort-CanonicalPathObjects -Items @($entries))
}

function Get-FirstUsefulLine {
    param([string]$Stdout, [string]$Stderr, [string]$Pattern = '')
    $lines = @(([string]$Stdout -split "`r?`n") + ([string]$Stderr -split "`r?`n") | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if (-not [string]::IsNullOrWhiteSpace($Pattern)) {
        $match = $lines | Where-Object { $_ -match $Pattern } | Select-Object -First 1
        if ($null -ne $match) { return ([string]$match).Trim() }
    }
    Assert-That ($lines.Count -gt 0) 'version command returned no useful output'
    return ([string]$lines[0]).Trim()
}

function Get-CanonicalTimestamp {
    param([Parameter(Mandatory = $true)][DateTimeOffset]$Value)
    # PowerShell ConvertFrom-Json coerces RFC3339 T timestamps to DateTime.
    # The accepted runner requires command timestamps to remain JSON strings so
    # its canonical reserialization is byte-identical to producer output.
    return $Value.ToUniversalTime().ToString('yyyy-MM-dd HH:mm:ss.fffffff+00:00', [Globalization.CultureInfo]::InvariantCulture)
}

function Invoke-FaultInjection {
    param([Parameter(Mandatory = $true)][string]$Name)
    if (-not [string]::IsNullOrWhiteSpace($script:faultInjection) -and
        [string]::Equals([string]$script:faultInjection, $Name, [StringComparison]::OrdinalIgnoreCase)) {
        # Fault points model one failed operation. Clearing the point lets the
        # top-level failure path persist the original error report afterwards.
        $script:faultInjection = $null
        throw "injected I6 producer failure at $Name"
    }
}

function Write-ManifestTemporary {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][byte[]]$Bytes
    )
    Invoke-FaultInjection -Name 'manifestTempWrite'
    [IO.File]::WriteAllBytes($Path, $Bytes)
}

function Read-ManifestTemporary {
    param([Parameter(Mandatory = $true)][string]$Path)
    Invoke-FaultInjection -Name 'manifestTempRead'
    return [IO.File]::ReadAllBytes($Path)
}

function Get-ManifestHash {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)
    Invoke-FaultInjection -Name 'manifestHash'
    return Get-Sha256BytesHex -Bytes $Bytes
}

function Move-ManifestNoOverwrite {
    param(
        [Parameter(Mandatory = $true)][string]$TemporaryPath,
        [Parameter(Mandatory = $true)][string]$DestinationPath
    )
    Invoke-FaultInjection -Name 'manifestRename'
    [IO.File]::Move($TemporaryPath, $DestinationPath, $false)
}

function Write-RunReport {
    param([Parameter(Mandatory = $true)][bool]$AllowOverwrite)
    Assert-That ($null -ne $script:runDir) 'run report directory is not initialized'
    Assert-NoReparsePath -Path $script:runDir -Context 'run report directory' | Out-Null
    $reportPath = Join-Path $script:runDir 'run-report.json'
    if (-not $AllowOverwrite) {
        Assert-That (-not (Test-Path -LiteralPath $reportPath)) "run report already exists: $reportPath"
    }
    $script:report.finishedAt = [DateTimeOffset]::UtcNow.ToString('o')
    $json = $script:report | ConvertTo-Json -Depth 20
    Invoke-FaultInjection -Name 'runreportWriteAllText'
    [IO.File]::WriteAllText($reportPath, $json, [Text.UTF8Encoding]::new($false))
}

function Record-ProducerFailure {
    param([Parameter(Mandatory = $true)][string]$FailureMessage)
    $script:report.status = 'FAIL'
    $script:report.manifestSha256 = $null
    $script:report.failure = [ordered]@{ message = $FailureMessage }
    if ($null -ne $script:runDir) {
        try {
            Write-RunReport -AllowOverwrite:$true
        }
        catch {
            Write-Error -ErrorAction Continue "producer failure report write failed: $($_.Exception.Message)"
        }
    }
}

function Handle-ProducerException {
    param([Parameter(Mandatory = $true)][string]$FailureMessage)
    if ($script:manifestCommitted) {
        # The manifest and PASS report are already authoritative. A diagnostic
        # after the commit cannot turn that committed success into a failure.
        $script:report.status = 'PASS'
        $script:report.failure = $null
        Write-Warning "producer diagnostic after trusted manifest commit; manifest remains published: $FailureMessage"
        return $false
    }
    Write-Error -ErrorAction Continue $FailureMessage
    Record-ProducerFailure -FailureMessage $FailureMessage
    return $true
}

function Remove-OwnedManifestTemporary {
    if (-not $script:manifestTempOwned -or $null -eq $script:manifestTempPath -or $null -eq $script:runDir) { return }
    try {
        $candidate = [IO.Path]::GetFullPath($script:manifestTempPath)
        $expected = [IO.Path]::GetFullPath((Join-Path $script:runDir 'requests-build-manifest.v1.json.tmp'))
        Assert-That ($candidate -ceq $expected) 'manifest temporary path ownership check failed'
        if (-not (Test-Path -LiteralPath $candidate)) {
            $script:manifestTempPath = $null
            $script:manifestTempOwned = $false
            return
        }
        $item = Get-Item -LiteralPath $candidate -Force
        Assert-That (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'manifest temporary output is a reparse point'
        Assert-That (-not $item.PSIsContainer) 'manifest temporary output is not a file'
        if (-not [string]::IsNullOrWhiteSpace($script:preparedManifestSha256)) {
            Assert-That ((Get-Sha256Hex -Path $candidate) -ceq $script:preparedManifestSha256) 'manifest temporary hash ownership proof failed'
        }
        Remove-Item -LiteralPath $candidate -Force
        $script:manifestTempPath = $null
        $script:manifestTempOwned = $false
    }
    catch {
        Write-Warning "I6 owned temporary manifest cleanup skipped: $($_.Exception.Message)"
    }
}

function Invoke-CapturedCommand {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$ArgumentList = @(),
        [Parameter(Mandatory = $true)][string]$DisplayCommand,
        [Parameter(Mandatory = $true)][string]$Purpose,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$LogStem
    )
    $started = [DateTimeOffset]::UtcNow
    $stdout = ''
    $stderr = ''
    $exitCode = 1
    try {
        $startInfo = [Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = $FilePath
        $startInfo.WorkingDirectory = $WorkingDirectory
        $startInfo.UseShellExecute = $false
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        foreach ($argument in $ArgumentList) { [void]$startInfo.ArgumentList.Add([string]$argument) }
        $process = [Diagnostics.Process]::new()
        $process.StartInfo = $startInfo
        try {
            $null = $process.Start()
            $stdoutTask = $process.StandardOutput.ReadToEndAsync()
            $stderrTask = $process.StandardError.ReadToEndAsync()
            $process.WaitForExit()
            $stdout = $stdoutTask.GetAwaiter().GetResult()
            $stderr = $stderrTask.GetAwaiter().GetResult()
            $exitCode = $process.ExitCode
        }
        finally {
            $process.Dispose()
        }
    }
    catch {
        $stderr = $_.Exception.Message
        $exitCode = 1
    }
    $finished = [DateTimeOffset]::UtcNow
    $stdoutName = "$LogStem.stdout.log"
    $stderrName = "$LogStem.stderr.log"
    [IO.File]::WriteAllText((Join-Path $script:runDir $stdoutName), [string]$stdout, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $script:runDir $stderrName), [string]$stderr, [Text.UTF8Encoding]::new($false))
    $startedText = Get-CanonicalTimestamp -Value $started
    $finishedText = Get-CanonicalTimestamp -Value $finished
    $entry = [ordered]@{
        command = $DisplayCommand
        exitCode = [int64]$exitCode
        finishedAt = $finishedText
        startedAt = $startedText
    }
    $script:manifestCommands.Add($entry)
    $script:commandEvidence.Add([ordered]@{
            command = $DisplayCommand
            purpose = $Purpose
            exitCode = [int]$exitCode
            startedAt = $startedText
            finishedAt = $finishedText
            stdout = $stdoutName
            stderr = $stderrName
        })
    return [pscustomobject]@{
        ExitCode = [int]$exitCode
        Stdout = [string]$stdout
        Stderr = [string]$stderr
        Entry = $entry
    }
}

function Invoke-GitCaptured {
    param([Parameter(Mandatory = $true)][string[]]$GitArguments, [Parameter(Mandatory = $true)][string]$Purpose, [Parameter(Mandatory = $true)][string]$LogStem)
    $display = 'git ' + ($GitArguments -join ' ')
    return Invoke-CapturedCommand -FilePath 'git.exe' -ArgumentList $GitArguments -DisplayCommand $display -Purpose $Purpose -WorkingDirectory $script:unionRoot -LogStem $LogStem
}

function Assert-CleanExactRevision {
    $revisionResult = Invoke-GitCaptured -GitArguments @('-c', "safe.directory=$($script:unionRoot)", '-C', $script:unionRoot, 'rev-parse', 'HEAD') -Purpose 'resolve exact union revision' -LogStem '001-git-revision'
    Assert-That ($revisionResult.ExitCode -eq 0) "git rev-parse failed with exit code $($revisionResult.ExitCode)"
    $actualRevision = $revisionResult.Stdout.Trim()
    Assert-That ($actualRevision -cmatch '^[0-9a-f]{40}$') 'union revision must be lowercase full SHA-1'
    Assert-That ($actualRevision -ceq $ExpectedRevision) "union revision does not match expected exact revision: $actualRevision"

    $statusResult = Invoke-GitCaptured -GitArguments @('-c', "safe.directory=$($script:unionRoot)", '-C', $script:unionRoot, 'status', '--porcelain=v1', '--untracked-files=all') -Purpose 'verify clean union worktree' -LogStem '002-git-status'
    Assert-That ($statusResult.ExitCode -eq 0) "git status failed with exit code $($statusResult.ExitCode)"
    Assert-That ([string]::IsNullOrWhiteSpace($statusResult.Stdout)) 'union worktree has tracked or untracked changes'
    return $actualRevision
}

function Get-CanonicalEnvironment {
    param(
        [Parameter(Mandatory = $true)][string]$GradleVersion,
        [Parameter(Mandatory = $true)][string]$JavaVersion,
        [Parameter(Mandatory = $true)][string]$NodeVersion
    )
    $environment = [ordered]@{
        gradle = $GradleVersion
        hostOs = [Environment]::OSVersion.VersionString
        java = $JavaVersion
        node = $NodeVersion
        powershell = $PSVersionTable.PSVersion.ToString()
    }
    foreach ($name in @('gradle', 'hostOs', 'java', 'node', 'powershell')) {
        $value = [string]$environment[$name]
        Assert-That (-not [string]::IsNullOrWhiteSpace($value)) "producer environment $name is empty"
        Assert-That ($value -notmatch '(?i)(password|token|secret|authorization|cookie|credential)') "producer environment $name contains a secret-like marker"
    }
    return $environment
}

function New-PublicationHarnessManifest {
    param(
        [Parameter(Mandatory = $true)][string]$AbsoluteRepo,
        [Parameter(Mandatory = $true)][string]$Revision
    )
    $pwaFiles = @(
        [ordered]@{ bytes = 1; relativePath = 'index.html'; sha256 = ('a' * 64) },
        [ordered]@{ bytes = 1; relativePath = 'sw-assets.js'; sha256 = ('b' * 64) },
        [ordered]@{ bytes = 1; relativePath = 'sw.js'; sha256 = ('c' * 64) }
    )
    $sortedPwaFiles = @(Sort-CanonicalPathObjects -Items $pwaFiles)
    $pwa = [ordered]@{
        files = $sortedPwaFiles
        manifestSha256 = Get-Sha256Text -Text (Convert-PwaFilesToCanonicalText -Files $sortedPwaFiles)
        relativeRoot = 'frontends/pwa-vue/dist'
    }
    $jars = @(Get-JarSpec | ForEach-Object {
            [ordered]@{
                bytes = 1
                name = [string]$_.Name
                relativePath = "$($_.Relative.Replace('\', '/'))/$($_.Name)-harness.jar"
                sha256 = ('d' * 64)
            }
        })
    return [ordered]@{
        artifacts = [ordered]@{
            jars = $jars
            pwaDist = $pwa
        }
        producer = [ordered]@{
            commands = @(
                [ordered]@{
                    command = 'I6 publication harness'
                    exitCode = 0
                    finishedAt = '2026-09-19 00:00:01.0000000+00:00'
                    startedAt = '2026-09-19 00:00:00.0000000+00:00'
                }
            )
            environment = [ordered]@{
                gradle = 'publication-harness'
                hostOs = 'publication-harness'
                java = 'publication-harness'
                node = 'publication-harness'
                powershell = $PSVersionTable.PSVersion.ToString()
            }
        }
        schemaVersion = 1
        source = [ordered]@{
            absoluteRepo = [IO.Path]::GetFullPath($AbsoluteRepo)
            cleanAfterBuild = $true
            cleanBeforeBuild = $true
            revision = $Revision
        }
    }
}

function New-CanonicalManifestObject {
    param(
        [Parameter(Mandatory = $true)][string]$AbsoluteRepo,
        [Parameter(Mandatory = $true)][string]$Revision,
        [Parameter(Mandatory = $true)][bool]$CleanBeforeBuild,
        [Parameter(Mandatory = $true)][bool]$CleanAfterBuild,
        [Parameter(Mandatory = $true)][object[]]$Commands,
        [Parameter(Mandatory = $true)][object]$Environment,
        [Parameter(Mandatory = $true)][object[]]$Jars,
        [Parameter(Mandatory = $true)][object]$PwaDist
    )
    $canonicalCommands = @($Commands | ForEach-Object {
            [ordered]@{
                command = [string]$_.command
                exitCode = [int64]$_.exitCode
                finishedAt = [string]$_.finishedAt
                startedAt = [string]$_.startedAt
            }
        })
    $canonicalJars = @((Sort-CanonicalPathObjects -Items $Jars) | ForEach-Object {
            [ordered]@{
                bytes = [int64]$_.bytes
                name = [string]$_.name
                relativePath = [string]$_.relativePath
                sha256 = [string]$_.sha256
            }
        })
    $canonicalPwaFiles = @((Sort-CanonicalPathObjects -Items @($PwaDist.files)) | ForEach-Object {
            [ordered]@{
                bytes = [int64]$_.bytes
                relativePath = [string]$_.relativePath
                sha256 = [string]$_.sha256
            }
        })
    return [ordered]@{
        artifacts = [ordered]@{
            jars = $canonicalJars
            pwaDist = [ordered]@{
                files = $canonicalPwaFiles
                manifestSha256 = [string]$PwaDist.manifestSha256
                relativeRoot = 'frontends/pwa-vue/dist'
            }
        }
        producer = [ordered]@{
            commands = $canonicalCommands
            environment = [ordered]@{
                gradle = [string]$Environment.gradle
                hostOs = [string]$Environment.hostOs
                java = [string]$Environment.java
                node = [string]$Environment.node
                powershell = [string]$Environment.powershell
            }
        }
        schemaVersion = 1
        source = [ordered]@{
            absoluteRepo = [IO.Path]::GetFullPath($AbsoluteRepo)
            cleanAfterBuild = $CleanAfterBuild
            cleanBeforeBuild = $CleanBeforeBuild
            revision = $Revision
        }
    }
}

function Write-TrustedManifest {
    param([Parameter(Mandatory = $true)][object]$Manifest)
    $canonicalObject = New-CanonicalManifestObject -AbsoluteRepo $Manifest.source.absoluteRepo -Revision $Manifest.source.revision -CleanBeforeBuild ([bool]$Manifest.source.cleanBeforeBuild) -CleanAfterBuild ([bool]$Manifest.source.cleanAfterBuild) -Commands @($Manifest.producer.commands) -Environment $Manifest.producer.environment -Jars @($Manifest.artifacts.jars) -PwaDist $Manifest.artifacts.pwaDist
    $text = $canonicalObject | ConvertTo-Json -Depth 20 -Compress
    Assert-That (-not $text.Contains("`r") -and -not $text.Contains("`n")) 'canonical manifest contains a line break'
    Assert-That (-not $text.EndsWith("`r") -and -not $text.EndsWith("`n")) 'canonical manifest has a trailing newline'
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($text)
    Assert-That ($bytes.Length -ge 2 -and -not ($bytes.Length -ge 3 -and $bytes[0] -eq 0xef -and $bytes[1] -eq 0xbb -and $bytes[2] -eq 0xbf)) 'canonical manifest has a UTF-8 BOM'
    Assert-NoReparsePath -Path $script:manifestPath -Context 'manifest output before publication' | Out-Null
    Assert-That (-not (Test-Path -LiteralPath $script:manifestPath)) "manifest output already exists: $script:manifestPath"
    $temporary = Join-Path $script:runDir 'requests-build-manifest.v1.json.tmp'
    Assert-That (-not (Test-Path -LiteralPath $temporary)) "manifest temporary output already exists: $temporary"
    $script:manifestTempPath = $temporary
    $script:manifestTempOwned = $true
    Write-ManifestTemporary -Path $temporary -Bytes $bytes
    Assert-That (Test-Path -LiteralPath $temporary -PathType Leaf) 'temporary manifest was not written'
    $readback = Read-ManifestTemporary -Path $temporary
    $expectedHash = Get-ManifestHash -Bytes $bytes
    $readbackHash = Get-ManifestHash -Bytes $readback
    Assert-That ($readbackHash -eq $expectedHash) 'temporary manifest readback changed'
    $script:preparedManifestSha256 = $expectedHash

    # PASS is prepared before publication so the run report is complete before
    # the final no-overwrite move. No fallible report, read or hash operation
    # is allowed after the publication commit point.
    $script:report.manifestSha256 = $expectedHash
    $script:report.status = 'PASS'
    Write-RunReport -AllowOverwrite:$false
    Assert-NoReparsePath -Path $script:manifestPath -Context 'manifest output at publication' | Out-Null
    Assert-That (-not (Test-Path -LiteralPath $script:manifestPath)) "manifest output appeared before publication: $script:manifestPath"
    # COMMIT POINT: this no-overwrite move is the first publication operation.
    Move-ManifestNoOverwrite -TemporaryPath $temporary -DestinationPath $script:manifestPath
    $script:manifestCommitted = $true
    $script:manifestTempPath = $null
    $script:manifestTempOwned = $false
}

try {
    Assert-That ($ExpectedRevision -cmatch '^[0-9a-f]{40}$') 'ExpectedRevision must be lowercase full SHA-1'
    Assert-That ((-not $PublicationHarness) -or [Environment]::GetEnvironmentVariable('RCT_I6_PUBLICATION_HARNESS') -ceq '1') 'publication harness requires the explicit test environment marker'
    $script:unionRoot = (Resolve-Path -LiteralPath $UnionRepo -ErrorAction Stop).Path
    Assert-NoReparsePath -Path $script:unionRoot -Context 'union repository' | Out-Null
    $script:manifestPath = if ([string]::IsNullOrWhiteSpace($requestedManifestPath)) { Join-Path $script:taskRoot 'requests-build-manifest.v1.json' } else { [IO.Path]::GetFullPath($requestedManifestPath) }
    $script:runsRoot = if ([string]::IsNullOrWhiteSpace($requestedRunsRoot)) { Join-Path $script:taskRoot 'runs' } else { [IO.Path]::GetFullPath($requestedRunsRoot) }
    Assert-ConfinedToTaskRoot -Path $script:manifestPath -Context 'manifest path' | Out-Null
    Assert-ConfinedToTaskRoot -Path $script:runsRoot -Context 'runs path' | Out-Null
    Assert-NoReparsePath -Path $script:taskRoot -Context 'producer root' | Out-Null
    # Check every existing ancestor before creating run evidence or allowing a
    # build tool to create any output. Missing leaves are intentional here;
    # Assert-NoReparsePath still walks their existing parent chain.
    Assert-PlannedOutputPaths -Repo $script:unionRoot -ManifestPath $script:manifestPath -RunsRoot $script:runsRoot
    if (-not (Test-Path -LiteralPath $script:runsRoot)) { New-Item -ItemType Directory -Path $script:runsRoot -Force | Out-Null }
    Assert-NoReparsePath -Path $script:runsRoot -Context 'runs root' | Out-Null
    $script:runDir = Join-Path $script:runsRoot $script:runId
    Assert-NoReparsePath -Path $script:runDir -Context 'planned run evidence directory' | Out-Null
    New-Item -ItemType Directory -Path $script:runDir -Force | Out-Null
    Assert-NoReparsePath -Path $script:runDir -Context 'run evidence directory' | Out-Null
    $script:report.unionRepo = $script:unionRoot
    $script:report.manifestPath = $script:manifestPath

    Assert-That (-not (Test-Path -LiteralPath $script:manifestPath)) "manifest output already exists: $script:manifestPath"
    if ($PublicationHarness) {
        $script:report.cleanBeforeBuild = $true
        $script:report.cleanAfterBuild = $true
        $script:report.environment = [ordered]@{
            gradle = 'publication-harness'
            hostOs = 'publication-harness'
            java = 'publication-harness'
            node = 'publication-harness'
            powershell = $PSVersionTable.PSVersion.ToString()
        }
        $harnessManifest = New-PublicationHarnessManifest -AbsoluteRepo $script:unionRoot -Revision $ExpectedRevision
        $script:report.artifacts = $harnessManifest.artifacts
        Write-TrustedManifest -Manifest $harnessManifest
        Write-Output "I6_PUBLICATION_HARNESS_PASS: manifest=$script:manifestPath sha256=$($script:report.manifestSha256)"
        return
    }
    $actualRevision = Assert-CleanExactRevision
    Assert-NoPreexistingBuildArtifacts -Repo $script:unionRoot
    $script:report.cleanBeforeBuild = $true

    $gradleVersion = Invoke-CapturedCommand -FilePath 'cmd.exe' -ArgumentList @('/d', '/c', '.\gradlew.bat --version') -DisplayCommand '.\gradlew.bat --version' -Purpose 'resolve actual Gradle environment' -WorkingDirectory $script:unionRoot -LogStem '003-gradle-version'
    Assert-That ($gradleVersion.ExitCode -eq 0) "Gradle version command failed with exit code $($gradleVersion.ExitCode)"
    $javaVersion = Invoke-CapturedCommand -FilePath 'java.exe' -ArgumentList @('-version') -DisplayCommand 'java -version' -Purpose 'resolve actual Java environment' -WorkingDirectory $script:unionRoot -LogStem '004-java-version'
    Assert-That ($javaVersion.ExitCode -eq 0) "Java version command failed with exit code $($javaVersion.ExitCode)"
    $nodeVersion = Invoke-CapturedCommand -FilePath 'node.exe' -ArgumentList @('--version') -DisplayCommand 'node --version' -Purpose 'resolve actual Node environment' -WorkingDirectory $script:unionRoot -LogStem '005-node-version'
    Assert-That ($nodeVersion.ExitCode -eq 0) "Node version command failed with exit code $($nodeVersion.ExitCode)"
    $script:report.environment = Get-CanonicalEnvironment -GradleVersion (Get-FirstUsefulLine -Stdout $gradleVersion.Stdout -Stderr $gradleVersion.Stderr -Pattern '^Gradle ') -JavaVersion (Get-FirstUsefulLine -Stdout $javaVersion.Stdout -Stderr $javaVersion.Stderr -Pattern 'version "') -NodeVersion (Get-FirstUsefulLine -Stdout $nodeVersion.Stdout -Stderr $nodeVersion.Stderr)

    $gradleTasks = @(
        [ordered]@{ Name = 'auth'; Task = ':services:auth-service:auth-app:bootJar' },
        [ordered]@{ Name = 'academic'; Task = ':services:academic-service:academic-app:bootJar' },
        [ordered]@{ Name = 'schedule'; Task = ':services:schedule-service:schedule-app:bootJar' },
        [ordered]@{ Name = 'attendance'; Task = ':services:attendance-service:attendance-app:bootJar' },
        [ordered]@{ Name = 'mobile-bff'; Task = ':services:mobile-bff:mobile-bff-app:bootJar' },
        [ordered]@{ Name = 'api-gateway'; Task = ':services:api-gateway:bootJar' }
    )
    $taskNumber = 6
    foreach ($task in $gradleTasks) {
        $display = ".\gradlew.bat $($task.Task) --no-daemon --no-parallel --max-workers=1 --no-problems-report"
        $result = Invoke-CapturedCommand -FilePath 'cmd.exe' -ArgumentList @('/d', '/c', $display) -DisplayCommand $display -Purpose "build $($task.Name) executable bootJar" -WorkingDirectory $script:unionRoot -LogStem ('{0:D3}-gradle-{1}' -f $taskNumber, $task.Name)
        Assert-That ($result.ExitCode -eq 0) "Gradle $($task.Name) bootJar failed with exit code $($result.ExitCode)"
        $taskNumber++
    }
    $pwaDisplay = 'npm run build --workspace @rct/pwa-vue'
    $pwaResult = Invoke-CapturedCommand -FilePath 'cmd.exe' -ArgumentList @('/d', '/c', $pwaDisplay) -DisplayCommand $pwaDisplay -Purpose 'build PWA with vue-tsc and Vite' -WorkingDirectory (Join-Path $script:unionRoot 'frontends') -LogStem '012-npm-pwa-build'
    Assert-That ($pwaResult.ExitCode -eq 0) "PWA build failed with exit code $($pwaResult.ExitCode)"

    $postRevision = Assert-CleanExactRevision
    Assert-That ($postRevision -ceq $actualRevision) 'union revision changed during build'
    $jarEntries = Get-BuiltJarEntries -Repo $script:unionRoot
    $pwaManifest = Get-PwaDistManifest -Directory (Join-Path $script:unionRoot 'frontends/pwa-vue/dist')
    $script:report.cleanAfterBuild = $true
    $script:report.artifacts = [ordered]@{
        jars = $jarEntries
        pwaDist = $pwaManifest
    }
    $manifest = [ordered]@{
        artifacts = [ordered]@{
            jars = $jarEntries
            pwaDist = $pwaManifest
        }
        producer = [ordered]@{
            commands = @($script:manifestCommands)
            environment = $script:report.environment
        }
        schemaVersion = 1
        source = [ordered]@{
            absoluteRepo = $script:unionRoot
            cleanAfterBuild = $true
            cleanBeforeBuild = $true
            revision = $actualRevision
        }
    }
    $written = Write-TrustedManifest -Manifest $manifest
    Write-Output "I6_PRODUCER_PASS: manifest=$script:manifestPath sha256=$($script:report.manifestSha256) revision=$actualRevision"
}
catch {
    $failureMessage = $_.Exception.Message
    if (Handle-ProducerException -FailureMessage $failureMessage) {
        exit 1
    }
}
finally {
    if (-not $script:manifestCommitted) { Remove-OwnedManifestTemporary }
}
