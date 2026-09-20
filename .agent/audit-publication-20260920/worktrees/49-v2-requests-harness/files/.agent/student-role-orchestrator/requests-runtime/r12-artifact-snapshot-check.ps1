#requires -Version 7.4
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Pure source-boundary check. It loads and exercises the production artifact
# snapshot, Java mount and edge transformation helpers against temporary files;
# no Docker, Gradle, server, browser or product runtime is used.
$runnerPath = Join-Path $PSScriptRoot 'runner.ps1'
$runnerText = Get-Content -LiteralPath $runnerPath -Raw
$tokens = $null
$errors = $null
$runnerAst = [System.Management.Automation.Language.Parser]::ParseFile($runnerPath, [ref]$tokens, [ref]$errors)
if ($errors.Count -ne 0) { throw "runner.ps1 parser errors: $($errors.Count)" }

function Assert-Check {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Get-FunctionDefinitionText {
    param([Parameter(Mandatory = $true)][string]$Name)
    $definition = @($runnerAst.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $Name }, $true)) | Select-Object -First 1
    if ($null -eq $definition) { throw "runner function missing: $Name" }
    return $definition.Extent.Text
}

foreach ($name in @(
        'Assert-That', 'Get-Sha256Hex', 'Get-Sha256BytesHex', 'Get-Sha256Text',
        'Invoke-ExternalSafe', 'Get-UnionTrackedBlobHash', 'Get-GitBlobHashForBytes', 'Resolve-EdgeSources',
        'Assert-JsonString', 'Assert-JsonInteger', 'Assert-Sha256Value',
        'Assert-AbsolutePathConfined', 'Sort-CanonicalPathObjects',
        'Convert-PwaFilesToCanonicalText', 'Get-JarSpec', 'Get-PwaDistManifest',
        'Assert-PwaManifestIdentity', 'Read-VerifiedArtifactBytes',
        'Copy-VerifiedArtifactFile', 'Assert-OwnedArtifactPath',
        'New-VerifiedArtifactSnapshot', 'Remove-OwnedResources', 'Start-JavaService', 'Start-Edge',
        'Convert-EdgeToRuntimeConfig')) {
    Invoke-Expression (Get-FunctionDefinitionText -Name $name)
}

$fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) "rct-r12-artifact-$([Guid]::NewGuid().ToString('N'))"
$fixtureUnionRoot = Join-Path $fixtureRoot 'union'
$runOne = Join-Path $fixtureRoot 'run-one'
$runTwo = Join-Path $fixtureRoot 'run-two'
$script:unionRoot = $fixtureUnionRoot
$script:pwaDistPath = Join-Path $fixtureUnionRoot 'frontends\pwa-vue\dist'
$script:runDir = $runOne
$script:artifactSnapshotRoot = $null
$script:artifactSnapshot = $null
$script:runId = 'r12-synthetic'
$script:report = [ordered]@{
    commands = [System.Collections.Generic.List[object]]::new()
    jars = [ordered]@{}
    source = [ordered]@{ pwaDist = $null; buildManifest = [ordered]@{ actualSha256 = ('d' * 64) } }
    assertions = [ordered]@{}
    cleanup = [ordered]@{
        status = 'NOT_RUN'; ownedContainers = @(); removedContainers = @(); verifiedAbsentContainers = @()
        ownedNetwork = $null; networkRemoved = $false; networkVerifiedAbsent = $false; keysRemoved = $false
        artifactRoot = $null; artifactRemoved = $false; artifactVerifiedAbsent = $false; errors = @()
    }
}

# The production helper records command evidence through this local pure-test
# sink. No shell or container launcher is replaced: the hash-object process in
# Get-GitBlobHashForBytes remains the production implementation under test.
function Add-CommandEvidence {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$ArgumentList = @(),
        [Parameter(Mandatory = $true)][string]$Purpose,
        [Parameter(Mandatory = $true)][int]$ExitCode,
        [switch]$AllowFailure
    )
    $script:report.commands.Add([pscustomobject]@{ FilePath = $FilePath; Purpose = $Purpose; ExitCode = $ExitCode })
}
$script:networkName = 'r12-synthetic-network'
$script:ownedContainers = [System.Collections.Generic.List[string]]::new()
$script:ownedNetwork = $null
$script:keysDir = $null
$script:images = [ordered]@{ nginx = 'synthetic-nginx'; temurin = 'synthetic-temurin' }
$script:services = [ordered]@{}
$script:mountCalls = [System.Collections.Generic.List[object]]::new()

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
    $script:mountCalls.Add([pscustomobject]@{ Name = $Name; Image = $Image; Mounts = @($Mounts) })
    return [pscustomobject]@{ Id = 'r12-synthetic-container'; Name = $Name }
}

try {
    New-Item -ItemType Directory -Path $fixtureUnionRoot -Force | Out-Null

    # Bind actual cached edge bytes to the committed revision blob. A changed
    # byte buffer must produce a different blob id, so the check cannot pass on
    # a separately re-read path hash.
    $acceptedSourceRoot = 'C:\Users\maksd\.codex\worktrees\6a61\rutcampustrack'
    Assert-Check (Test-Path -LiteralPath $acceptedSourceRoot -PathType Container) 'accepted E source root is unavailable for the edge source check'
    $script:unionRoot = $acceptedSourceRoot
    $ExpectedUnionRevision = ((& git -c "safe.directory=$acceptedSourceRoot" -C $acceptedSourceRoot rev-parse HEAD 2>$null).Trim())
    Assert-Check ($LASTEXITCODE -eq 0 -and $ExpectedUnionRevision -match '^[0-9a-f]{40}$') 'could not resolve the accepted E revision for the edge source check'
    $resolvedEdge = Resolve-EdgeSources
    Assert-Check ($resolvedEdge.nginxBlob -match '^[0-9a-f]{40}$' -and $resolvedEdge.defaultBlob -match '^[0-9a-f]{40}$') 'production edge source resolver did not return revision-bound blobs'
    Assert-Check ($resolvedEdge.nginxText.Contains('client_max_body_size 2m;') -and $resolvedEdge.defaultText.Contains('listen 443 ssl;')) 'production edge source resolver did not retain the accepted source contract'
    $script:unionRoot = $fixtureUnionRoot
    $edgeRelativePath = 'tests/e2e/infra/nginx/default.conf'
    $edgeSourcePath = Join-Path $acceptedSourceRoot ($edgeRelativePath.Replace('/', '\'))
    $edgeBytes = [IO.File]::ReadAllBytes($edgeSourcePath)
    $expectedEdgeBlob = ((& git -c "safe.directory=$acceptedSourceRoot" -C $acceptedSourceRoot rev-parse "HEAD:$edgeRelativePath" 2>$null).Trim())
    Assert-Check ($LASTEXITCODE -eq 0 -and $expectedEdgeBlob -match '^[0-9a-f]{40}$') 'could not resolve the committed edge source blob for the byte-binding check'
    $boundEdgeBlob = Get-GitBlobHashForBytes -Bytes $edgeBytes -RelativePathForGit $edgeRelativePath -Context 'R12 bind cached default.conf bytes to committed revision blob'
    Assert-Check ($boundEdgeBlob -ceq $expectedEdgeBlob) 'production edge byte binding did not match the committed revision blob'
    $changedEdgeBytes = [byte[]]$edgeBytes.Clone()
    $changedEdgeBytes[0] = [byte](($changedEdgeBytes[0] + 1) % 256)
    $changedEdgeBlob = Get-GitBlobHashForBytes -Bytes $changedEdgeBytes -RelativePathForGit $edgeRelativePath -Context 'R12 reject changed cached default.conf bytes'
    Assert-Check ($changedEdgeBlob -cne $expectedEdgeBlob) 'production edge byte binding accepted changed cached bytes'

    foreach ($spec in @(Get-JarSpec)) {
        $directory = Join-Path $fixtureUnionRoot $spec.Relative
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
        $jarName = $spec.Pattern.Replace('*', 'r12')
        $jarPath = Join-Path $directory $jarName
        [IO.File]::WriteAllText($jarPath, "trusted-$($spec.Name)", [Text.UTF8Encoding]::new($false))
        $script:report.jars[$spec.Name] = [ordered]@{
            path = $jarPath
            sourceRelativePath = "$($spec.Relative.Replace('\', '/'))/$jarName"
            file = $jarName
            bytes = [int64](Get-Item -LiteralPath $jarPath).Length
            sha256 = Get-Sha256Hex -Path $jarPath
        }
    }
    New-Item -ItemType Directory -Path (Join-Path $script:pwaDistPath 'assets') -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $script:pwaDistPath 'index.html'), '<!doctype html><html>r12</html>', [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $script:pwaDistPath 'sw.js'), 'self.__r12_sw=1;', [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $script:pwaDistPath 'sw-assets.js'), 'self.__r12_assets=1;', [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $script:pwaDistPath 'assets\app.js'), 'window.__r12=1;', [Text.UTF8Encoding]::new($false))
    $script:report.source.pwaDist = Get-PwaDistManifest -Directory $script:pwaDistPath

    $snapshot = New-VerifiedArtifactSnapshot
    Assert-Check ($snapshot.Jars.Count -eq 6) 'snapshot did not copy all six trusted jars'
    Assert-Check ($snapshot.PwaDist.fileCount -eq 4) 'snapshot did not copy the complete PWA file tree'
    Assert-Check ((Test-Path -LiteralPath $snapshot.PwaDist.path -PathType Container)) 'owned PWA snapshot directory is missing'
    Assert-Check ((Get-Sha256Hex -Path $snapshot.Jars.auth.path) -eq $script:report.jars.auth.sha256) 'owned auth jar copy does not match the trusted manifest'
    $expectedApp = @($script:report.source.pwaDist.files | Where-Object { $_.relativePath -ceq 'assets/app.js' })
    Assert-Check ($expectedApp.Count -eq 1) 'PWA fixture manifest did not contain the expected assets/app.js entry'
    Assert-Check ((Get-Sha256Hex -Path (Join-Path $snapshot.PwaDist.path 'assets\app.js')) -ceq [string]$expectedApp[0].sha256) 'owned PWA app.js copy does not match the trusted manifest'

    # A source swap after the freeze must not affect the bytes that production
    # mount helpers consume. The copied files remain trusted and are the only
    # paths handed to the actual Start-JavaService/Start-Edge helpers.
    $originalAuthBytes = [IO.File]::ReadAllBytes($script:report.jars.auth.path)
    $originalIndexBytes = [IO.File]::ReadAllBytes((Join-Path $script:pwaDistPath 'index.html'))
    [IO.File]::WriteAllText($script:report.jars.auth.path, 'swapped-after-snapshot', [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $script:pwaDistPath 'index.html'), 'swapped-after-snapshot', [Text.UTF8Encoding]::new($false))
    Assert-Check ((Get-Sha256BytesHex -Bytes (Get-Content -LiteralPath $snapshot.Jars.auth.path -AsByteStream)) -eq (Get-Sha256BytesHex -Bytes $originalAuthBytes)) 'source jar swap changed the owned jar copy'
    Assert-Check ((Get-Sha256BytesHex -Bytes (Get-Content -LiteralPath (Join-Path $snapshot.PwaDist.path 'index.html') -AsByteStream)) -eq (Get-Sha256BytesHex -Bytes $originalIndexBytes)) 'source PWA swap changed the owned PWA copy'

    $null = Start-JavaService -Name 'auth' -Jar $snapshot.Jars.auth.path -Environment @{} -Aliases @('auth-service')
    $edgeConfig = @{ NginxIp = '172.30.199.10'; ConfigDir = 'C:\r12'; CertPath = 'C:\r12\server.crt'; KeyPath = 'C:\r12\server.key'; PwaDistPath = $snapshot.PwaDist.path }
    $null = Start-Edge -Config $edgeConfig -ReserveOnly
    $jarMount = @($script:mountCalls | Where-Object { $_.Name -eq 'rct-requests-r12-synthetic-auth' })[0].Mounts | Where-Object { $_.Target -eq '/opt/rct/app.jar' }
    $pwaMount = @($script:mountCalls | Where-Object { $_.Name -eq 'rct-requests-r12-synthetic-nginx' })[0].Mounts | Where-Object { $_.Target -eq '/usr/share/nginx/html' }
    Assert-Check ($null -ne $jarMount -and $jarMount.Source -eq $snapshot.Jars.auth.path) 'production Java mount helper did not consume the owned jar copy'
    Assert-Check ($null -ne $pwaMount -and $pwaMount.Source -eq $snapshot.PwaDist.path) 'production edge mount helper did not consume the owned PWA copy'

    # A changed input before a fresh snapshot is rejected by the same
    # production helper at the manifest/byte boundary.
    $script:runDir = $runTwo
    $script:artifactSnapshotRoot = $null
    $script:artifactSnapshot = $null
    $pwaSwapRejected = $false
    try { New-VerifiedArtifactSnapshot | Out-Null } catch { $pwaSwapRejected = $true }
    Assert-Check $pwaSwapRejected 'PWA source swap was accepted by the production snapshot helper'

    [IO.File]::WriteAllBytes((Join-Path $script:pwaDistPath 'index.html'), $originalIndexBytes)
    $jarSwapRejected = $false
    try { New-VerifiedArtifactSnapshot | Out-Null } catch { $jarSwapRejected = $true }
    Assert-Check $jarSwapRejected 'jar source swap was accepted by the production snapshot helper'

    $sourceDefaultPath = Join-Path $fixtureUnionRoot 'tests\e2e\infra\nginx\default.conf'
    New-Item -ItemType Directory -Path (Split-Path -Parent $sourceDefaultPath) -Force | Out-Null
    $sourceDefault = @'
    location = / {
        return 301 /login;
    }
    location / {
        proxy_pass http://web-panel-nginx:80/;
        proxy_set_header Host $host;
    }
'@
    [IO.File]::WriteAllText($sourceDefaultPath, $sourceDefault, [Text.UTF8Encoding]::new($false))
    $transformed = Convert-EdgeToRuntimeConfig -NginxPath 'C:\r12\nginx.conf' -DefaultPath $sourceDefaultPath -NginxContent '    access_log /var/log/nginx/access.log main;' -DefaultContent $sourceDefault
    Assert-Check ($transformed.Default -notmatch 'return 301 /login') 'runtime edge transform retained the exact-root login redirect'
    Assert-Check ($transformed.Default -match '(?s)location\s*=\s*/\s*\{.*?root /usr/share/nginx/html;.*?try_files /index\.html =200;') 'runtime exact root does not serve the copied PWA index'
    Assert-Check ($transformed.Default -match '(?s)location\s+/\s*\{.*?root /usr/share/nginx/html;.*?try_files \$uri \$uri/ /index\.html;') 'runtime prefix location does not serve the copied PWA tree'
    Assert-Check ($transformed.Default -notmatch 'proxy_pass http://web-panel-nginx:80/') 'runtime edge transform retained the panel proxy'
    Assert-Check ($sourceDefault -match 'return 301 /login' -and $sourceDefault -match 'proxy_pass http://web-panel-nginx:80/') 'source E edge text was changed by runtime transformation'

    $backend = [regex]::Match($runnerText, '(?s)function Start-BackendServices\s*\{.*?\n\}')
    Assert-Check ($backend.Success -and $backend.Value -match '\$script:artifactSnapshot\.Jars') 'backend production callsite still resolves jar mounts from the source report'
    $runtime = [regex]::Match($runnerText, '(?s)function New-RuntimeConfig\s*\{.*?\n\}')
    Assert-Check ($runtime.Success -and $runtime.Value -match '\$script:artifactSnapshot\.PwaDist') 'edge production callsite still resolves PWA from the source tree'

    # Verify the actual cleanup helper removes only the exact owned snapshot
    # directory and records absence. No Docker resource is configured in this
    # pure fixture, so the cleanup branch stays entirely filesystem-scoped.
    $script:runDir = $runOne
    $script:artifactSnapshotRoot = $snapshot.Root
    Remove-OwnedResources
    Assert-Check ($script:report.cleanup.status -ceq 'PASS' -and $script:report.cleanup.artifactRemoved -and $script:report.cleanup.artifactVerifiedAbsent) 'owned artifact cleanup did not report verified absence'
    Assert-Check (-not (Test-Path -LiteralPath $snapshot.Root)) 'owned artifact cleanup left the snapshot directory behind'

    Write-Output 'R12 artifact/edge source check: PASS (production snapshot copied and verified six jars/full PWA, post-freeze jar/PWA swaps left mounts on owned copies, changed inputs failed closed, and exact-root runtime transform served copied index while source text stayed unchanged)'
} finally {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
}
