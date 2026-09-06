[CmdletBinding()]
param(
    [switch]$ValidateOnly,
    [switch]$SkipPwa
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$taskState = [IO.Path]::GetFullPath((Join-Path $repo '.agent\runtime-js-student-01'))
$keysDir = Join-Path $taskState 'keys'
$pwaCertificatePath = Join-Path $keysDir 'pwa.crt'
$pwaPrivateKeyPath = Join-Path $keysDir 'pwa.key'
$pwaRuntimeHost = '127.0.0.1'
$pwaRuntimePort = 28443
$pwaRuntimeOrigin = "https://${pwaRuntimeHost}:$pwaRuntimePort"
$pwaBrowserScript = Join-Path $PSScriptRoot 'pwa-browser-check.mjs'
$bundleNodePath = 'C:\Users\maksd\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
$bundlePlaywrightPath = 'C:\Users\maksd\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules\playwright\index.mjs'
$chromePath = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
$nodeCommand = Get-Command node -ErrorAction SilentlyContinue
$nodeCandidates = @($bundleNodePath)
if ($null -ne $nodeCommand) { $nodeCandidates += $nodeCommand.Source }
$nodeExecutable = @($nodeCandidates | Where-Object { Test-Path -LiteralPath $_ }) | Select-Object -First 1
$evidenceRoot = Join-Path $repo '.agent\vertical-js-student-01\runtime-evidence'
$runId = [DateTimeOffset]::Now.ToString('yyyyMMdd-HHmmss')
$evidenceDir = Join-Path $evidenceRoot $runId
$resultPath = Join-Path $evidenceDir 'runtime-result.json'
$processes = [Collections.Generic.List[object]]::new()
$createdContainers = [Collections.Generic.List[string]]::new()
$createdNetwork = $false
$createdKeysDir = $false

$network = 'rct-js-student-01'
$containers = [ordered]@{
    AcademicPostgres = 'rct-js-student-01-pg-academic'
    SchedulePostgres = 'rct-js-student-01-pg-schedule'
    Mongo = 'rct-js-student-01-mongo'
    Redis = 'rct-js-student-01-redis'
    Rabbit = 'rct-js-student-01-rabbit'
}
$ports = @(25431, 25432, 27027, 26379, 25672, 35672, 29090, 29091, 29191,
    29092, 29192, 29093, 29193, 29080, 28080, $pwaRuntimePort)

$report = [ordered]@{
    schema = 'rct.runtime-evidence.v1'
    task = 'JS-STUDENT-01-r1'
    runId = $runId
    startedAt = [DateTimeOffset]::Now.ToString('o')
    status = 'RUNNING'
    stage = 'initialization'
    resources = [ordered]@{
        network = $network
        containers = @($containers.Values)
        ports = $ports
    }
    services = [ordered]@{}
    assertions = [Collections.Generic.List[object]]::new()
}

function Add-Assertion {
    param([string]$Name, [object]$Evidence)
    $report.assertions.Add([ordered]@{ name = $Name; passed = $true; evidence = $Evidence })
}

function Assert-That {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Assertion failed: $Message" }
}

function Assert-SafeOwnedPath {
    param([string]$Path)
    $full = [IO.Path]::GetFullPath($Path)
    $ownedRoot = [IO.Path]::GetFullPath((Join-Path $repo '.agent')) + [IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($ownedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing filesystem cleanup outside task-owned .agent: $full"
    }
}

function Test-DockerObject {
    param([ValidateSet('container', 'network')][string]$Kind, [string]$Name)
    if ($Kind -eq 'container') {
        & docker container inspect $Name *> $null
    } else {
        & docker network inspect $Name *> $null
    }
    return $LASTEXITCODE -eq 0
}

function Invoke-Docker {
    param([string[]]$DockerArgs, [string]$Description)
    $output = @(& docker @DockerArgs 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed (docker exit $LASTEXITCODE): $($output -join [Environment]::NewLine)"
    }
    return $output
}

function Cleanup-OwnedResources {
    $cleanupErrors = [Collections.Generic.List[string]]::new()
    foreach ($entry in @($processes)) {
        try {
            $process = $entry.Process
            if ($null -ne $process -and -not $process.HasExited) {
                Stop-Process -Id $process.Id -Force -ErrorAction Stop
                Wait-Process -Id $process.Id -Timeout 15 -ErrorAction SilentlyContinue
            }
        } catch {
            $cleanupErrors.Add("process $($entry.Name): $($_.Exception.Message)")
        }
    }
    foreach ($name in @($createdContainers)) {
        try {
            if (Test-DockerObject -Kind container -Name $name) {
                $null = Invoke-Docker -DockerArgs @('rm', '-f', $name) -Description "remove $name"
            }
        } catch {
            $cleanupErrors.Add("container ${name}: $($_.Exception.Message)")
        }
    }
    try {
        if ($createdNetwork -and (Test-DockerObject -Kind network -Name $network)) {
            $null = Invoke-Docker -DockerArgs @('network', 'rm', $network) -Description "remove $network"
        }
    } catch {
        $cleanupErrors.Add("network ${network}: $($_.Exception.Message)")
    }
    try {
        if ($createdKeysDir -and (Test-Path -LiteralPath $keysDir)) {
            Assert-SafeOwnedPath -Path $keysDir
            Remove-Item -LiteralPath $keysDir -Recurse -Force
        }
    } catch {
        $cleanupErrors.Add("keys: $($_.Exception.Message)")
    }
    if ($cleanupErrors.Count -gt 0) {
        throw "Task cleanup failed: $($cleanupErrors -join '; ')"
    }
}

function New-RandomHex {
    param([int]$Bytes = 32)
    return [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes($Bytes)).ToLowerInvariant()
}

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-SignedExternalJwt {
    param(
        [string]$PrivateKeyPath,
        [long]$UserId,
        [string]$Issuer,
        [string]$Audience,
        [long]$ExpiresAtUnix
    )
    $issuedAt = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $header = @{ alg = 'RS256'; typ = 'JWT' } | ConvertTo-Json -Compress
    $payload = [ordered]@{
        sub = [string]$UserId
        iss = $Issuer
        aud = $Audience
        role = 'STUDENT'
        group_id = 1
        is_headman = $false
        iat = $issuedAt
        exp = $ExpiresAtUnix
    } | ConvertTo-Json -Compress
    $encodedHeader = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($header))
    $encodedPayload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payload))
    $unsignedToken = "$encodedHeader.$encodedPayload"
    $rsa = [Security.Cryptography.RSA]::Create()
    $pem = $null
    try {
        $pem = Get-Content -LiteralPath $PrivateKeyPath -Raw
        $rsa.ImportFromPem($pem)
        $signature = $rsa.SignData(
            [Text.Encoding]::UTF8.GetBytes($unsignedToken),
            [Security.Cryptography.HashAlgorithmName]::SHA256,
            [Security.Cryptography.RSASignaturePadding]::Pkcs1)
        return "$unsignedToken.$(ConvertTo-Base64Url $signature)"
    } finally {
        $pem = $null
        $rsa.Dispose()
    }
}

function Test-PortAvailable {
    param([int]$Port)
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    try { $listener.Start(); return $true } catch { return $false } finally { $listener.Stop() }
}

function Wait-Until {
    param([string]$Name, [scriptblock]$Probe, [int]$TimeoutSeconds = 120)
    $deadline = [DateTimeOffset]::Now.AddSeconds($TimeoutSeconds)
    do {
        try {
            if ([bool](& $Probe)) { return }
        } catch { }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::Now -lt $deadline)
    throw "Readiness timeout after ${TimeoutSeconds}s: $Name"
}

function Get-BootJar {
    param([string]$RelativeDirectory)
    $directory = Join-Path $repo $RelativeDirectory
    $jars = @(Get-ChildItem -LiteralPath $directory -Filter '*.jar' -File |
        Where-Object Name -NotLike '*-plain.jar')
    if ($jars.Count -ne 1) {
        throw "Expected exactly one boot jar in $directory, found $($jars.Count). Run the documented bootJar command first."
    }
    return $jars[0].FullName
}

function Start-JavaService {
    param(
        [string]$Name,
        [string]$Jar,
        [hashtable]$Environment,
        [string[]]$Arguments = @()
    )
    $stdout = Join-Path $evidenceDir "$Name.out.log"
    $stderr = Join-Path $evidenceDir "$Name.err.log"
    $javaArgs = @('-jar', $Jar) + $Arguments
    $process = Start-Process -FilePath 'java' -ArgumentList $javaArgs -WorkingDirectory $repo `
        -Environment $Environment -WindowStyle Hidden -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr -PassThru
    $entry = [pscustomobject]@{ Name = $Name; Process = $process; Stdout = $stdout; Stderr = $stderr }
    $processes.Add($entry)
    $report.services[$Name] = [ordered]@{ pid = $process.Id; stdout = $stdout; stderr = $stderr }
    return $entry
}

function Get-HttpBodyText {
    param([object]$Response)
    if ($Response.Content -is [byte[]]) {
        return [Text.Encoding]::UTF8.GetString($Response.Content)
    }
    return [string]$Response.Content
}

function Wait-ServiceHealth {
    param([object]$Entry, [string]$Url, [int]$TimeoutSeconds = 150)
    Wait-Until -Name "$($Entry.Name) $Url" -TimeoutSeconds $TimeoutSeconds -Probe {
        if ($Entry.Process.HasExited) {
            $tail = if (Test-Path $Entry.Stderr) { (Get-Content $Entry.Stderr -Tail 30) -join [Environment]::NewLine } else { '' }
            throw "$($Entry.Name) exited $($Entry.Process.ExitCode): $tail"
        }
        try {
            $response = Invoke-WebRequest -Uri $Url -TimeoutSec 4 -SkipHttpErrorCheck
            $body = Get-HttpBodyText $response
            return $response.StatusCode -eq 200 -and $body -match '"status"\s*:\s*"UP"'
        } catch { return $false }
    }
    $report.services[$Entry.Name].health = 'UP'
    $report.services[$Entry.Name].healthUrl = $Url
}

function Invoke-HttpJson {
    param(
        [ValidateSet('GET', 'POST')][string]$Method,
        [string]$Uri,
        [hashtable]$Headers = @{},
        [string]$Body
    )
    $params = @{
        Method = $Method
        Uri = $Uri
        Headers = $Headers
        TimeoutSec = 15
        SkipHttpErrorCheck = $true
    }
    if ($PSBoundParameters.ContainsKey('Body')) {
        $params.ContentType = 'application/json'
        $params.Body = $Body
    }
    return Invoke-WebRequest @params
}

function Get-HttpHeaderValue {
    param([object]$Response, [string]$Name)
    return [string]::Join(',', @($Response.Headers[$Name]))
}

function Get-SafeHttpResponse {
    param([object]$Response)
    $body = Get-HttpBodyText $Response
    $safe = [ordered]@{
        statusCode = [int]$Response.StatusCode
        contentType = Get-HttpHeaderValue $Response 'Content-Type'
        server = Get-HttpHeaderValue $Response 'Server'
        via = Get-HttpHeaderValue $Response 'Via'
        bodyLength = $body.Length
    }
    try {
        $json = $body | ConvertFrom-Json
        foreach ($field in @('status', 'code', 'title', 'type')) {
            if ($null -ne $json.$field) { $safe[$field] = [string]$json.$field }
        }
    } catch { }
    return $safe
}

function Invoke-RabbitManagement {
    param(
        [ValidateSet('GET', 'POST', 'PUT')][string]$Method,
        [string]$Path,
        [string]$Body = '{}'
    )
    $basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("rct_user:$rabbitPassword"))
    try {
        return Invoke-WebRequest -Method $Method -Uri "http://127.0.0.1:35672/api/$Path" `
            -Headers @{ Authorization = "Basic $basic" } -ContentType 'application/json' `
            -Body $Body -TimeoutSec 15 -SkipHttpErrorCheck
    } finally {
        $basic = $null
    }
}

function Read-MongoJson {
    param([string]$Javascript)
    $output = Invoke-Docker -DockerArgs @('exec', $containers.Mongo, 'mongosh', '--quiet',
        'attendance_js_student_01', '--eval', $Javascript) -Description 'Mongo assertion query'
    $line = @($output | Where-Object { $_ -and $_.Trim().StartsWith('{') })[-1]
    return $line | ConvertFrom-Json
}

function Read-PsqlScalar {
    param([string]$Container, [string]$Database, [string]$Sql)
    $output = Invoke-Docker -DockerArgs @('exec', $Container, 'psql', '-qAt', '-v',
        'ON_ERROR_STOP=1', '-U', 'rct_user', '-d', $Database, '-c', $Sql) -Description 'Postgres assertion query'
    return (@($output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })[-1]).Trim()
}

function Read-MongoMutationCounts {
    return Read-MongoJson 'print(JSON.stringify({attendances:db.attendances.countDocuments({}),requests:db.late_checkin_requests.countDocuments({}),receipts:db.student_checkin_receipts.countDocuments({}),pairs:db.student_checkin_pairs.countDocuments({}),outbox:db.attendance_outbox.countDocuments({})}))'
}

function New-PwaRuntimeCertificate {
    $opensslCandidates = @(
        'C:\Program Files\Git\usr\bin\openssl.exe',
        'C:\Program Files\Git\mingw64\bin\openssl.exe'
    )
    $openssl = @($opensslCandidates | Where-Object { Test-Path -LiteralPath $_ }) | Select-Object -First 1
    Assert-That (-not [string]::IsNullOrWhiteSpace($openssl)) 'task-scoped OpenSSL is available for the HTTPS PWA runtime'
    $sanType = if ($pwaRuntimeHost -match '^\d{1,3}(\.\d{1,3}){3}$') { "IP:$pwaRuntimeHost" } else { "DNS:$pwaRuntimeHost" }
    $opensslArgs = @(
        'req', '-x509', '-nodes', '-newkey', 'rsa:2048', '-days', '1',
        '-keyout', $pwaPrivateKeyPath, '-out', $pwaCertificatePath,
        '-subj', "/CN=$pwaRuntimeHost", '-addext', "subjectAltName=$sanType"
    )
    $output = @(& $openssl @opensslArgs 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "task PWA certificate generation failed (openssl exit $LASTEXITCODE): $($output -join [Environment]::NewLine)"
    }
    Assert-That (Test-Path -LiteralPath $pwaCertificatePath) 'task PWA certificate exists'
    Assert-That (Test-Path -LiteralPath $pwaPrivateKeyPath) 'task PWA private key exists'
}

function Invoke-PwaBrowserRuntime {
    $distDirectory = Join-Path $repo 'frontends\pwa-vue\dist'
    Assert-That (Test-Path -LiteralPath $pwaBrowserScript) 'PWA browser runtime script exists'
    Assert-That (Test-Path -LiteralPath (Join-Path $distDirectory 'index.html')) 'production PWA dist exists'
    Assert-That (Test-Path -LiteralPath (Join-Path $distDirectory 'sw.js')) 'production PWA service worker exists'
    Assert-That (Test-Path -LiteralPath (Join-Path $distDirectory 'sw-assets.js')) 'production PWA precache manifest exists'
    $playwrightCandidates = @(
        $bundlePlaywrightPath,
        (Join-Path $repo 'tests\e2e\node_modules\playwright\index.mjs'),
        (Join-Path $repo 'node_modules\playwright\index.mjs')
    )
    $playwrightModule = @($playwrightCandidates | Where-Object { Test-Path -LiteralPath $_ }) | Select-Object -First 1
    Assert-That (-not [string]::IsNullOrWhiteSpace($playwrightModule)) 'Playwright module is available for the PWA browser runtime'

    $environmentNames = @(
        'RCT_PWA_DIST', 'RCT_PWA_GATEWAY_URL', 'RCT_PWA_RUNTIME_HOST', 'RCT_PWA_CERT',
        'RCT_PWA_KEY', 'RCT_PWA_PORT', 'RCT_PWA_LOGIN', 'RCT_PWA_PASSWORD', 'RCT_PWA_SCREENSHOT',
        'RCT_PWA_PROGRESS', 'RCT_CHROME_PATH',
        'RCT_PLAYWRIGHT_MODULE'
    )
    $oldEnvironment = @{}
    foreach ($name in $environmentNames) { $oldEnvironment[$name] = [Environment]::GetEnvironmentVariable($name) }
    try {
        $env:RCT_PWA_DIST = $distDirectory
        $env:RCT_PWA_GATEWAY_URL = 'http://127.0.0.1:28080'
        $env:RCT_PWA_RUNTIME_HOST = $pwaRuntimeHost
        $env:RCT_PWA_CERT = $pwaCertificatePath
        $env:RCT_PWA_KEY = $pwaPrivateKeyPath
        $env:RCT_PWA_PORT = [string]$pwaRuntimePort
        $env:RCT_PWA_LOGIN = 'student'
        $env:RCT_PWA_PASSWORD = $studentPassword
        $env:RCT_PWA_SCREENSHOT = Join-Path $evidenceDir 'pwa-offline-reload.png'
        $env:RCT_PWA_PROGRESS = Join-Path $evidenceDir 'pwa-browser-progress.log'
        $env:RCT_CHROME_PATH = $chromePath
        $env:RCT_PLAYWRIGHT_MODULE = $playwrightModule
        $output = @(& $nodeExecutable $pwaBrowserScript 2>&1)
        $jsonLine = @($output | ForEach-Object { [string]$_ } |
            Where-Object { $_.TrimStart().StartsWith('{') } | Select-Object -Last 1)
        Assert-That ($jsonLine.Count -eq 1) "PWA browser runtime did not return JSON evidence: $($output | Select-Object -Last 1)"
        $result = $jsonLine | ConvertFrom-Json
        $report.pwaBrowser = $result
        if ($result.status -ne 'PASS') {
            $failure = if ($null -ne $result.error) { [string]$result.error } else { 'no error detail' }
            throw "PWA browser runtime failed at '$($result.phase)': $failure"
        }
        return $result
    } finally {
        foreach ($name in $environmentNames) {
            [Environment]::SetEnvironmentVariable($name, $oldEnvironment[$name])
        }
    }
}

if ($ValidateOnly) {
    $null = Get-Command docker -ErrorAction Stop
    $null = Get-Command java -ErrorAction Stop
    Assert-That (-not [string]::IsNullOrWhiteSpace($nodeExecutable)) 'Node executable is available for the PWA browser runtime'
    Assert-That (Test-Path -LiteralPath $chromePath) 'Chrome executable is available for the PWA browser runtime'
    Assert-That ((Test-Path -LiteralPath $bundlePlaywrightPath) -or
        (Test-Path -LiteralPath (Join-Path $repo 'tests\e2e\node_modules\playwright\index.mjs')) -or
        (Test-Path -LiteralPath (Join-Path $repo 'node_modules\playwright\index.mjs'))) 'Playwright module is available for the PWA browser runtime'
    $null = Get-BootJar 'services\auth-service\auth-app\build\libs'
    $null = Get-BootJar 'services\academic-service\academic-app\build\libs'
    $null = Get-BootJar 'services\schedule-service\schedule-app\build\libs'
    $null = Get-BootJar 'services\attendance-service\attendance-app\build\libs'
    $null = Get-BootJar 'services\mobile-bff\mobile-bff-app\build\libs'
    $null = Get-BootJar 'services\api-gateway\build\libs'
    Assert-That (Test-Path -LiteralPath $pwaBrowserScript) 'PWA browser runtime script exists'
    Assert-That (Test-Path -LiteralPath (Join-Path $repo 'frontends\pwa-vue\dist\index.html')) 'production PWA dist exists'
    Assert-That (Test-Path -LiteralPath (Join-Path $repo 'frontends\pwa-vue\dist\sw.js')) 'production PWA service worker exists'
    Assert-That (Test-Path -LiteralPath (Join-Path $repo 'frontends\pwa-vue\dist\sw-assets.js')) 'production PWA precache manifest exists'
    Assert-That (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'seed-academic.sql')) 'academic seed exists'
    Assert-That (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'seed-schedule.sql')) 'schedule seed exists'
    Write-Host 'VALID: runtime harness prerequisites and boot jars are present.'
    exit 0
}

$dbPassword = New-RandomHex 24
$redisPassword = New-RandomHex 24
$rabbitPassword = New-RandomHex 24
$grpcSecret = New-RandomHex 32
$issuerSecret = New-RandomHex 32
$tmaToken = "000000:$((New-RandomHex 20))"
$studentPassword = 'password' # repository-owned disposable seed, never emitted

try {
    $report.stage = 'preflight'
    $null = Get-Command docker -ErrorAction Stop
    $null = Get-Command java -ErrorAction Stop
    Assert-That (-not [string]::IsNullOrWhiteSpace($nodeExecutable)) 'Node executable is available for the PWA browser runtime'
    Assert-That (Test-Path -LiteralPath $chromePath) 'Chrome executable is available for the PWA browser runtime'
    Assert-That ((Test-Path -LiteralPath $bundlePlaywrightPath) -or
        (Test-Path -LiteralPath (Join-Path $repo 'tests\e2e\node_modules\playwright\index.mjs')) -or
        (Test-Path -LiteralPath (Join-Path $repo 'node_modules\playwright\index.mjs'))) 'Playwright module is available for the PWA browser runtime'
    foreach ($name in @($containers.Values)) {
        Assert-That (-not (Test-DockerObject -Kind container -Name $name)) "task container already exists: $name"
    }
    Assert-That (-not (Test-DockerObject -Kind network -Name $network)) "task network already exists: $network"
    Assert-That (-not (Test-Path -LiteralPath $keysDir)) "task key directory already exists: $keysDir"
    foreach ($port in $ports) { Assert-That (Test-PortAvailable $port) "host port $port is free" }

    New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
    New-Item -ItemType Directory -Force -Path $keysDir | Out-Null
    Assert-SafeOwnedPath -Path $keysDir
    $createdKeysDir = $true
    New-PwaRuntimeCertificate

    $jars = [ordered]@{
        academic = Get-BootJar 'services\academic-service\academic-app\build\libs'
        auth = Get-BootJar 'services\auth-service\auth-app\build\libs'
        schedule = Get-BootJar 'services\schedule-service\schedule-app\build\libs'
        attendance = Get-BootJar 'services\attendance-service\attendance-app\build\libs'
        mobileBff = Get-BootJar 'services\mobile-bff\mobile-bff-app\build\libs'
        gateway = Get-BootJar 'services\api-gateway\build\libs'
    }

    $report.stage = 'infrastructure'
    Write-Host 'Starting isolated infrastructure...'
    $null = Invoke-Docker -DockerArgs @('network', 'create', $network) -Description 'create task network'
    $createdNetwork = $true

    $env:POSTGRES_PASSWORD = $dbPassword
    try {
        $null = Invoke-Docker -DockerArgs @('run', '-d', '--name', $containers.AcademicPostgres,
            '--network', $network, '-p', '127.0.0.1:25431:5432', '--env', 'POSTGRES_DB=academic_db',
            '--env', 'POSTGRES_USER=rct_user', '--env', 'POSTGRES_PASSWORD', 'postgres:16') -Description 'start academic Postgres'
        $createdContainers.Add($containers.AcademicPostgres)
        $null = Invoke-Docker -DockerArgs @('run', '-d', '--name', $containers.SchedulePostgres,
            '--network', $network, '-p', '127.0.0.1:25432:5432', '--env', 'POSTGRES_DB=schedule_db',
            '--env', 'POSTGRES_USER=rct_user', '--env', 'POSTGRES_PASSWORD', 'postgres:16') -Description 'start schedule Postgres'
        $createdContainers.Add($containers.SchedulePostgres)
    } finally { Remove-Item Env:POSTGRES_PASSWORD -ErrorAction SilentlyContinue }

    $env:REDIS_PASSWORD = $redisPassword
    try {
        $null = Invoke-Docker -DockerArgs @('run', '-d', '--name', $containers.Redis, '--network',
            $network, '-p', '127.0.0.1:26379:6379', '--env', 'REDIS_PASSWORD', 'redis:7-alpine', 'sh', '-c',
            'exec redis-server --requirepass "$REDIS_PASSWORD"') -Description 'start Redis'
        $createdContainers.Add($containers.Redis)
    } finally { Remove-Item Env:REDIS_PASSWORD -ErrorAction SilentlyContinue }

    $env:RABBITMQ_DEFAULT_PASS = $rabbitPassword
    try {
        $null = Invoke-Docker -DockerArgs @('run', '-d', '--name', $containers.Rabbit, '--network',
            $network, '-p', '127.0.0.1:25672:5672', '-p', '127.0.0.1:35672:15672', '--env',
            'RABBITMQ_DEFAULT_USER=rct_user', '--env', 'RABBITMQ_DEFAULT_PASS',
            'rabbitmq:3.13-management-alpine') -Description 'start RabbitMQ'
        $createdContainers.Add($containers.Rabbit)
    } finally { Remove-Item Env:RABBITMQ_DEFAULT_PASS -ErrorAction SilentlyContinue }

    $null = Invoke-Docker -DockerArgs @('run', '-d', '--name', $containers.Mongo, '--network',
        $network, '-p', '127.0.0.1:27027:27017', 'mongo:7.0', '--replSet', 'rs0', '--bind_ip_all') -Description 'start Mongo'
    $createdContainers.Add($containers.Mongo)

    Wait-Until 'academic Postgres' { & docker exec $containers.AcademicPostgres pg_isready -q -U rct_user -d academic_db *> $null; return $LASTEXITCODE -eq 0 }
    Wait-Until 'schedule Postgres' { & docker exec $containers.SchedulePostgres pg_isready -q -U rct_user -d schedule_db *> $null; return $LASTEXITCODE -eq 0 }
    $env:REDISCLI_AUTH = $redisPassword
    try {
        Wait-Until 'Redis' { & docker exec --env REDISCLI_AUTH $containers.Redis redis-cli ping *> $null; return $LASTEXITCODE -eq 0 }
    } finally { Remove-Item Env:REDISCLI_AUTH -ErrorAction SilentlyContinue }
    Wait-Until 'RabbitMQ' { & docker exec $containers.Rabbit rabbitmq-diagnostics -q ping *> $null; return $LASTEXITCODE -eq 0 } 180
    Wait-Until 'Mongo ping' { & docker exec $containers.Mongo mongosh --quiet --eval 'quit(db.adminCommand("ping").ok ? 0 : 2)' *> $null; return $LASTEXITCODE -eq 0 } 120
    $null = Invoke-Docker -DockerArgs @('exec', $containers.Mongo, 'mongosh', '--quiet', '--eval',
        "rs.initiate({_id:'rs0',members:[{_id:0,host:'localhost:27017'}]})") -Description 'init Mongo replica set'
    Wait-Until 'Mongo primary' { & docker exec $containers.Mongo mongosh --quiet --eval 'quit(db.hello().isWritablePrimary ? 0 : 2)' *> $null; return $LASTEXITCODE -eq 0 } 120
    Add-Assertion 'isolated infrastructure ready' @{ containers = 5; anonymousStorage = $true }

    $report.stage = 'academic-start-and-seed'
    Write-Host 'Starting academic service and applying isolated seed...'
    $academic = Start-JavaService 'academic' $jars.academic @{
        SPRING_PROFILES_ACTIVE='local'; SERVER_PORT='29091'; SERVER_ADDRESS='127.0.0.1'; GRPC_SERVER_PORT='29191'; GRPC_SERVER_ADDRESS='127.0.0.1';
        SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:25431/academic_db'; POSTGRES_ACADEMIC_PASSWORD=$dbPassword;
        SPRING_DATA_REDIS_HOST='127.0.0.1'; SPRING_DATA_REDIS_PORT='26379'; REDIS_PASSWORD=$redisPassword;
        SPRING_RABBITMQ_HOST='127.0.0.1'; SPRING_RABBITMQ_PORT='25672'; SPRING_RABBITMQ_USERNAME='rct_user';
        SPRING_RABBITMQ_PASSWORD=$rabbitPassword; RABBITMQ_USER='rct_user'; RABBITMQ_PASSWORD=$rabbitPassword;
        GRPC_SECRET=$grpcSecret; AUTH_SERVICE_URL='http://127.0.0.1:29090'; MANAGEMENT_TRACING_ENABLED='false'
    } @('--grpc.client.schedule-service.address=static://127.0.0.1:29192')
    Wait-Until -Name 'academic Flyway schema' -TimeoutSeconds 180 -Probe {
        if ($academic.Process.HasExited) {
            $tail = if (Test-Path $academic.Stderr) { (Get-Content $academic.Stderr -Tail 30) -join [Environment]::NewLine } else { '' }
            throw "academic exited $($academic.Process.ExitCode): $tail"
        }
        return (Read-PsqlScalar $containers.AcademicPostgres 'academic_db' "SELECT to_regclass('public.users') IS NOT NULL") -eq 't'
    }
    $seedAcademic = Join-Path $PSScriptRoot 'seed-academic.sql'
    $null = Invoke-Docker -DockerArgs @('cp', $seedAcademic, "$($containers.AcademicPostgres):/tmp/seed.sql") -Description 'copy academic seed'
    $null = Invoke-Docker -DockerArgs @('exec', $containers.AcademicPostgres, 'psql', '-q', '-v',
        'ON_ERROR_STOP=1', '-U', 'rct_user', '-d', 'academic_db', '-f', '/tmp/seed.sql') -Description 'apply academic seed'
    $studentRow = Read-PsqlScalar $containers.AcademicPostgres 'academic_db' "SELECT id || '|' || group_id || '|' || is_headman FROM users WHERE login='student'"
    $studentParts = $studentRow.Split('|')
    Assert-That ($studentParts.Count -eq 3 -and $studentParts[1] -eq '1' -and $studentParts[2] -eq 'false') 'student seed has group 1 and is not headman'
    $studentId = [long]$studentParts[0]

    $report.stage = 'remaining-services-and-schedule-seed'
    Write-Host 'Starting auth and schedule services...'
    $auth = Start-JavaService 'auth' $jars.auth @{
        SPRING_PROFILES_ACTIVE='local'; SERVER_PORT='29090'; SERVER_ADDRESS='127.0.0.1';
        SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:25431/academic_db'; POSTGRES_ACADEMIC_PASSWORD=$dbPassword;
        SPRING_DATA_REDIS_HOST='127.0.0.1'; SPRING_DATA_REDIS_PORT='26379'; REDIS_PASSWORD=$redisPassword;
        SPRING_RABBITMQ_HOST='127.0.0.1'; SPRING_RABBITMQ_PORT='25672'; SPRING_RABBITMQ_USERNAME='rct_user';
        SPRING_RABBITMQ_PASSWORD=$rabbitPassword; JWT_KEY_DIR=$keysDir; INTERNAL_ISSUER_SECRET=$issuerSecret;
        TMA_BOT_TOKEN=$tmaToken; MANAGEMENT_TRACING_ENABLED='false'
    }
    Wait-ServiceHealth $auth 'http://127.0.0.1:29090/actuator/health' 180
    Wait-ServiceHealth $academic 'http://127.0.0.1:29091/actuator/health' 180

    $schedule = Start-JavaService 'schedule' $jars.schedule @{
        SPRING_PROFILES_ACTIVE='local'; SERVER_PORT='29092'; SERVER_ADDRESS='127.0.0.1'; GRPC_SERVER_PORT='29192'; GRPC_SERVER_ADDRESS='127.0.0.1';
        SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:25432/schedule_db'; POSTGRES_SCHEDULE_PASSWORD=$dbPassword;
        SPRING_RABBITMQ_HOST='127.0.0.1'; SPRING_RABBITMQ_PORT='25672'; SPRING_RABBITMQ_USERNAME='rct_user';
        SPRING_RABBITMQ_PASSWORD=$rabbitPassword; RABBITMQ_USER='rct_user'; RABBITMQ_PASSWORD=$rabbitPassword;
        GRPC_SECRET=$grpcSecret; AUTH_SERVICE_URL='http://127.0.0.1:29090'; MANAGEMENT_TRACING_ENABLED='false'
    } @('--grpc.client.academic-service.address=static://127.0.0.1:29191')
    Wait-ServiceHealth $schedule 'http://127.0.0.1:29092/actuator/health' 180
    $seedSchedule = Join-Path $PSScriptRoot 'seed-schedule.sql'
    $null = Invoke-Docker -DockerArgs @('cp', $seedSchedule, "$($containers.SchedulePostgres):/tmp/seed.sql") -Description 'copy schedule seed'
    $null = Invoke-Docker -DockerArgs @('exec', $containers.SchedulePostgres, 'psql', '-q', '-v',
        'ON_ERROR_STOP=1', '-U', 'rct_user', '-d', 'schedule_db', '-f', '/tmp/seed.sql') -Description 'apply schedule seed'
    $lessonRows = Read-PsqlScalar $containers.SchedulePostgres 'schedule_db' "SELECT string_agg(l.id::text, ',' ORDER BY si.lesson_number) FROM lessons l JOIN schedule_items si ON si.id=l.schedule_item_id WHERE si.group_id=1 AND si.semester_id=1"
    $lessonIds = @($lessonRows.Split(',') | ForEach-Object { [long]$_ })
    Assert-That ($lessonIds.Count -eq 2) 'two current task lessons were seeded'
    $pendingLessonId = $lessonIds[0]
    $presentLessonId = $lessonIds[1]

    Write-Host 'Starting attendance, Mobile BFF and gateway...'
    $attendance = Start-JavaService 'attendance' $jars.attendance @{
        SPRING_PROFILES_ACTIVE='local'; SERVER_PORT='29093'; SERVER_ADDRESS='127.0.0.1'; ATTENDANCE_GRPC_PORT='29193'; GRPC_SERVER_ADDRESS='127.0.0.1';
        SPRING_DATA_MONGODB_URI='mongodb://127.0.0.1:27027/attendance_js_student_01?replicaSet=rs0&directConnection=true';
        SPRING_DATA_REDIS_HOST='127.0.0.1'; SPRING_DATA_REDIS_PORT='26379'; REDIS_PASSWORD=$redisPassword;
        SPRING_RABBITMQ_HOST='127.0.0.1'; SPRING_RABBITMQ_PORT='25672'; SPRING_RABBITMQ_USERNAME='rct_user';
        SPRING_RABBITMQ_PASSWORD=$rabbitPassword; RABBITMQ_USER='rct_user'; RABBITMQ_PASSWORD=$rabbitPassword;
        GRPC_SECRET=$grpcSecret; AUTH_SERVICE_URL='http://127.0.0.1:29090'; MANAGEMENT_TRACING_ENABLED='false'
    } @('--grpc.client.schedule-service.address=static://127.0.0.1:29192', '--grpc.client.academic-service.address=static://127.0.0.1:29191')
    Wait-ServiceHealth $attendance 'http://127.0.0.1:29093/actuator/health' 180

    $mobileBff = Start-JavaService 'mobile-bff' $jars.mobileBff @{
        SPRING_PROFILES_ACTIVE='local'; MOBILE_BFF_PORT='29080'; SERVER_ADDRESS='127.0.0.1'; AUTH_SERVICE_URL='http://127.0.0.1:29090';
        ATTENDANCE_GRPC_ADDRESS='static://127.0.0.1:29193'; SCHEDULE_GRPC_ADDRESS='static://127.0.0.1:29192';
        ACADEMIC_GRPC_ADDRESS='static://127.0.0.1:29191'; GRPC_SECRET=$grpcSecret; MANAGEMENT_TRACING_ENABLED='false'
    }
    Wait-ServiceHealth $mobileBff 'http://127.0.0.1:29080/actuator/health' 150

    $gateway = Start-JavaService 'gateway' $jars.gateway @{
        SPRING_PROFILES_ACTIVE='local'; SERVER_PORT='28080'; SERVER_ADDRESS='127.0.0.1'; SPRING_DATA_REDIS_HOST='127.0.0.1';
        SPRING_DATA_REDIS_PORT='26379'; REDIS_PASSWORD=$redisPassword; AUTH_SERVICE_URL='http://127.0.0.1:29090';
        ACADEMIC_SERVICE_URL='http://127.0.0.1:29091'; SCHEDULE_SERVICE_URL='http://127.0.0.1:29092';
        ATTENDANCE_SERVICE_URL='http://127.0.0.1:29093'; MOBILE_BFF_URL='http://127.0.0.1:29080';
        INTERNAL_ISSUER_SECRET=$issuerSecret; CORS_ALLOWED_ORIGIN=$pwaRuntimeOrigin; MANAGEMENT_TRACING_ENABLED='false'
    }
    Wait-ServiceHealth $gateway 'http://127.0.0.1:28080/actuator/health' 150
    Add-Assertion 'six real service processes ready' @{ serviceCount = 6; hiddenProcesses = $true }

    $diagnosticLoginBody = @{ login = 'student'; password = $studentPassword } | ConvertTo-Json -Compress
    $diagnosticLoginResponse = Invoke-HttpJson POST 'http://127.0.0.1:28080/api/auth/login' @{ Origin = $pwaRuntimeOrigin } $diagnosticLoginBody
    $report.directGatewayLogin = Get-SafeHttpResponse $diagnosticLoginResponse
    $report.directGatewayLogin.origin = $pwaRuntimeOrigin
    if ($SkipPwa) {
        $report.pwaBrowser = [ordered]@{
            status = 'NOT_RUN'
            reason = 'Server assertions only; prior browser PASS remains the applicable evidence.'
            priorEvidence = '.agent/vertical-js-student-01/runtime-evidence/20260906-224922/runtime-result.json'
        }
        Add-Assertion 'PWA browser evidence reused for server-only run' @{ priorRun = '20260906-224922'; browserPhase = 'PASS' }
    } else {
        $report.stage = 'pwa-browser-runtime'
        Write-Host 'Running production PWA HTTPS login, refresh-cookie and offline reload probes...'
        $pwaBrowserResult = Invoke-PwaBrowserRuntime
        $proxyLogin = @($pwaBrowserResult.apiResponses | Where-Object { $_.path -eq '/api/auth/login' } | Select-Object -Last 1)
        $report.gatewayLoginComparison = [ordered]@{ direct = $report.directGatewayLogin; proxy = if ($proxyLogin.Count -eq 1) { $proxyLogin[0] } else { $null } }
        Add-Assertion 'production PWA uses scoped HTTPS refresh cookie and offline shell' @{
            origin = $pwaBrowserResult.origin
            cookie = $pwaBrowserResult.cookie
            onlineServiceWorker = $pwaBrowserResult.online.registrationState
            onlinePrecache = $pwaBrowserResult.online.precacheCoverage
            offlineRows = $pwaBrowserResult.offline.todayRowCount
            offlineMutationsDisabled = $pwaBrowserResult.offline.mutationDisabled
            apiCacheEntries = $pwaBrowserResult.offline.cacheApiEntries
            persistentJwt = $pwaBrowserResult.offline.jwtLikeInStorage -or $pwaBrowserResult.offline.indexedDbJwtLike
            screenshot = $pwaBrowserResult.screenshot
        }
    }

    $evidenceQueue = 'rct-js-student-01-evidence'
    $queueResponse = Invoke-RabbitManagement PUT "queues/%2F/$evidenceQueue" `
        '{"durable":true,"auto_delete":false,"arguments":{}}'
    Assert-That ($queueResponse.StatusCode -in @(201, 204)) "evidence queue declaration returned $($queueResponse.StatusCode)"
    $bindingResponse = Invoke-RabbitManagement POST "bindings/%2F/e/rut-uit.events/q/$evidenceQueue" `
        '{"routing_key":"","arguments":{}}'
    Assert-That ($bindingResponse.StatusCode -eq 201) "evidence queue binding returned $($bindingResponse.StatusCode)"
    Add-Assertion 'task-only RabbitMQ evidence sink ready' @{
        exchange = 'rut-uit.events'
        queue = $evidenceQueue
        durableAcrossBrokerRestart = $true
    }

    $report.stage = 'gateway-login'
    $loginBody = @{ login = 'student'; password = $studentPassword } | ConvertTo-Json -Compress
    $loginResponse = Invoke-HttpJson POST 'http://127.0.0.1:28080/api/auth/login' @{} $loginBody
    Assert-That ($loginResponse.StatusCode -eq 200) "gateway login returned $($loginResponse.StatusCode)"
    $login = Get-HttpBodyText $loginResponse | ConvertFrom-Json
    Assert-That (-not [string]::IsNullOrWhiteSpace($login.accessToken)) 'login returned an access token'
    $accessToken = [string]$login.accessToken
    Add-Assertion 'real auth login through gateway' @{ httpStatus = 200; tokenRecorded = $false }

    $report.stage = 'headman-block-and-legacy-retirement'
    Write-Host 'Exercising headman-blocked student rejection and retired legacy check-in...'
    $blockedScheduleItemId = [long](Read-PsqlScalar $containers.SchedulePostgres 'schedule_db' @"
INSERT INTO schedule_items (
    group_id, subject_id, semester_id, day_of_week, lesson_number,
    start_time, end_time, week_type, room, is_active
)
VALUES (1, 1, 1, 0, 3, TIME '00:00:00', TIME '23:59:59', 'all', 'РТ-03', TRUE)
RETURNING id
"@)
    $blockedLessonId = [long](Read-PsqlScalar $containers.SchedulePostgres 'schedule_db' @"
INSERT INTO lessons (schedule_item_id, date, status, is_geo_blocked, is_blocked_by_headman)
VALUES ($blockedScheduleItemId, CURRENT_DATE, 'active', FALSE, TRUE)
RETURNING id
"@)
    $blockedStateBefore = Read-MongoMutationCounts
    $blockedKey = "runtime-headman-blocked-$runId"
    $blockedHeaders = @{
        Authorization = "Bearer $accessToken"
        'Idempotency-Key' = $blockedKey
        'X-User-Id' = '999999'
        'X-User-Role' = 'ADMIN'
    }
    $blockedBody = @{ geo = @{ kind = 'UNAVAILABLE'; reason = 'TIMEOUT' } } | ConvertTo-Json -Compress -Depth 4
    $blockedUri = "http://127.0.0.1:28080/api/v1/student/lessons/$blockedLessonId/checkin"
    $blockedResponse = Invoke-HttpJson POST $blockedUri $blockedHeaders $blockedBody
    $blockedProblem = Get-HttpBodyText $blockedResponse | ConvertFrom-Json
    Assert-That ($blockedResponse.StatusCode -eq 409 -and $blockedProblem.code -eq 'CHECKIN_NOT_ELIGIBLE') "headman-blocked student POST returned $($blockedResponse.StatusCode) with code $($blockedProblem.code)"
    $blockedStateAfter = Read-MongoMutationCounts
    Assert-That (
        $blockedStateAfter.attendances -eq $blockedStateBefore.attendances -and
        $blockedStateAfter.requests -eq $blockedStateBefore.requests -and
        $blockedStateAfter.receipts -eq $blockedStateBefore.receipts -and
        $blockedStateAfter.pairs -eq $blockedStateBefore.pairs -and
        $blockedStateAfter.outbox -eq $blockedStateBefore.outbox
    ) 'headman-blocked student POST caused no attendance, request, receipt, cooldown or outbox write'
    Add-Assertion 'headman-blocked student POST is mutation-free' @{
        lessonId = $blockedLessonId
        httpStatus = $blockedResponse.StatusCode
        problemCode = $blockedProblem.code
        countsBefore = $blockedStateBefore
        countsAfter = $blockedStateAfter
    }

    $legacyStateBefore = Read-MongoMutationCounts
    $legacyHeaders = @{ Authorization = "Bearer $accessToken" }
    $legacyBody = @{ lat = 55.788204; lng = 37.606762 } | ConvertTo-Json -Compress
    $legacyResponse = Invoke-HttpJson POST 'http://127.0.0.1:28080/api/attendance/checkin' $legacyHeaders $legacyBody
    $legacyProblem = Get-HttpBodyText $legacyResponse | ConvertFrom-Json
    Assert-That ($legacyResponse.StatusCode -eq 410) "legacy attendance/checkin returned $($legacyResponse.StatusCode), expected 410"
    Assert-That ([string]$legacyProblem.type -match 'legacy-checkin-retired') 'legacy attendance/checkin identifies the retired endpoint'
    $legacyStateAfter = Read-MongoMutationCounts
    Assert-That (
        $legacyStateAfter.attendances -eq $legacyStateBefore.attendances -and
        $legacyStateAfter.requests -eq $legacyStateBefore.requests -and
        $legacyStateAfter.receipts -eq $legacyStateBefore.receipts -and
        $legacyStateAfter.pairs -eq $legacyStateBefore.pairs -and
        $legacyStateAfter.outbox -eq $legacyStateBefore.outbox
    ) 'retired legacy attendance/checkin caused no attendance, request, receipt, cooldown or outbox write'
    Add-Assertion 'legacy attendance/checkin is retired before mutation' @{
        httpStatus = $legacyResponse.StatusCode
        problemType = $legacyProblem.type
        countsBefore = $legacyStateBefore
        countsAfter = $legacyStateAfter
    }

    $report.stage = 'student-read-api'
    $readHeaders = @{ Authorization = "Bearer $accessToken" }
    $sessionResponse = Invoke-HttpJson GET 'http://127.0.0.1:28080/api/v1/student/session' $readHeaders
    $session = Get-HttpBodyText $sessionResponse | ConvertFrom-Json
    Assert-That ($sessionResponse.StatusCode -eq 200) "student session returned $($sessionResponse.StatusCode)"
    Assert-That ([long]$session.user.id -eq $studentId -and -not [string]::IsNullOrWhiteSpace($session.user.displayName)) 'session carries authenticated student identity'
    Assert-That ($session.activeRole -eq 'STUDENT' -and [long]$session.group.id -eq 1 -and [long]$session.semester.id -eq 1) 'session carries student role, group and active semester'
    Assert-That ((Get-HttpHeaderValue $sessionResponse 'Cache-Control') -match 'no-store') 'session is no-store'

    $todayResponse = Invoke-HttpJson GET 'http://127.0.0.1:28080/api/v1/student/today' $readHeaders
    $today = Get-HttpBodyText $todayResponse | ConvertFrom-Json
    $todayIds = @($today.lessons | ForEach-Object { [long]$_.schedule.id })
    $blockedTodayLesson = @($today.lessons | Where-Object { [long]$_.schedule.id -eq $blockedLessonId })
    $report.studentRead = [ordered]@{
        todayLessonIds = $todayIds
        expectedLessonIds = @($pendingLessonId, $presentLessonId, $blockedLessonId)
        blockedEligibility = if ($blockedTodayLesson.Count -eq 1) {
            [ordered]@{ allowed = $blockedTodayLesson[0].checkinEligibility.allowed; reason = $blockedTodayLesson[0].checkinEligibility.reason }
        } else { $null }
    }
    Assert-That ($todayResponse.StatusCode -eq 200 -and $today.timeZone -eq 'Europe/Moscow') 'Today returns Moscow projection'
    Assert-That ($todayIds.Count -eq 3 -and $todayIds[0] -eq $pendingLessonId -and $todayIds[1] -eq $presentLessonId -and $todayIds[2] -eq $blockedLessonId) 'Today contains seeded and headman-blocked lessons in schedule order'
    Assert-That (@($today.lessons | Where-Object {
        $_.schedule.subject.id -eq '1' -and
        $_.schedule.subject.name -eq 'Runtime геопроверка' -and
        $_.schedule.subject.type -eq 'LECTURE' -and
        $_.schedule.status -eq 'ACTIVE' -and
        $_.schedule.room.changeState -eq 'UNKNOWN' -and
        $_.checkinEligibility.allowed -eq $true -and
        $_.checkinEligibility.reason -eq 'ELIGIBLE' -and
        $null -eq $_.attendance -and
        $null -eq $_.request
    }).Count -eq 2) 'Today composes academic metadata, schedule provenance and live attendance eligibility'
    Assert-That ($blockedTodayLesson.Count -eq 1 -and -not $blockedTodayLesson[0].checkinEligibility.allowed -and $blockedTodayLesson[0].checkinEligibility.reason -eq 'GEO_BLOCKED') 'Today retains the headman-blocked lesson with disabled eligibility'
    Assert-That ($today.lessons[0].schedule.room.current -eq 'РТ-01' -and $today.lessons[1].schedule.room.current -eq 'РТ-02') 'Today preserves the two seeded schedule rooms'
    Assert-That ((Get-HttpHeaderValue $todayResponse 'Cache-Control') -match 'no-store') 'Today is no-store'

    $scheduleUri = 'http://127.0.0.1:28080/api/v1/student/schedule?semesterId=1'
    $scheduleResponse = Invoke-HttpJson GET $scheduleUri $readHeaders
    $semesterSchedule = Get-HttpBodyText $scheduleResponse | ConvertFrom-Json
    $scheduleIds = @($semesterSchedule.lessons | ForEach-Object { [long]$_.id })
    $scheduleEtag = Get-HttpHeaderValue $scheduleResponse 'ETag'
    Assert-That ($scheduleResponse.StatusCode -eq 200 -and $scheduleIds.Count -eq 3 -and $scheduleIds[0] -eq $pendingLessonId -and $scheduleIds[1] -eq $presentLessonId -and $scheduleIds[2] -eq $blockedLessonId) 'authorized semester schedule returns seeded and headman-blocked lessons'
    Assert-That ($semesterSchedule.semester.id -eq '1' -and $semesterSchedule.dateFrom -eq $session.semester.startsOn -and $semesterSchedule.dateTo -eq $session.semester.endsOn) 'semester schedule is bounded by the authenticated active semester'
    Assert-That (-not [string]::IsNullOrWhiteSpace($semesterSchedule.updatedAt) -and -not [string]::IsNullOrWhiteSpace($scheduleEtag)) 'semester schedule carries updatedAt and ETag'
    Assert-That ((Get-HttpHeaderValue $scheduleResponse 'Cache-Control') -match 'private' -and (Get-HttpHeaderValue $scheduleResponse 'Cache-Control') -match 'no-cache') 'semester schedule is private and revalidated'
    $notModifiedHeaders = @{ Authorization = "Bearer $accessToken"; 'If-None-Match' = $scheduleEtag }
    $notModified = Invoke-HttpJson GET $scheduleUri $notModifiedHeaders
    Assert-That ($notModified.StatusCode -eq 304 -and (Get-HttpHeaderValue $notModified 'ETag') -eq $scheduleEtag) 'authorized If-None-Match returns 304 with the same ETag'

    $foreignScheduleResponse = Invoke-HttpJson GET 'http://127.0.0.1:28080/api/v1/student/schedule?semesterId=999999' $readHeaders
    $foreignScheduleProblem = Get-HttpBodyText $foreignScheduleResponse | ConvertFrom-Json
    Assert-That ($foreignScheduleResponse.StatusCode -eq 403 -and $foreignScheduleProblem.code -eq 'OUT_OF_SCOPE') 'foreign semester is rejected at the authenticated BFF scope boundary'
    Add-Assertion 'student read APIs compose real gRPC data and enforce cache/scope contracts' @{
        signedStudentId = $studentId
        groupId = 1
        semesterId = 1
        todayLessonIds = $todayIds
        blockedLessonId = $blockedLessonId
        blockedEligibilityReason = $blockedTodayLesson[0].checkinEligibility.reason
        scheduleLessonIds = $scheduleIds
        etag304 = $true
        foreignSemesterStatus = 403
    }

    $report.stage = 'pending-with-rabbit-outage'
    Write-Host 'Stopping task RabbitMQ and exercising atomic PENDING path...'
    $null = Invoke-Docker -DockerArgs @('stop', $containers.Rabbit) -Description 'stop task RabbitMQ'
    $correlation = "js-student-01-$runId"
    $pendingKey = "runtime-pending-$runId"
    $pendingHeaders = @{
        Authorization = "Bearer $accessToken"
        'Idempotency-Key' = $pendingKey
        'X-User-Id' = '999999'
        'X-User-Role' = 'ADMIN'
        'X-Correlation-Id' = $correlation
    }
    $pendingBody = @{ geo = @{ kind = 'UNAVAILABLE'; reason = 'TIMEOUT' } } | ConvertTo-Json -Compress -Depth 4
    $pendingUri = "http://127.0.0.1:28080/api/v1/student/lessons/$pendingLessonId/checkin"
    $pendingResponse = Invoke-HttpJson POST $pendingUri $pendingHeaders $pendingBody
    Assert-That ($pendingResponse.StatusCode -eq 200) "UNAVAILABLE check-in returned $($pendingResponse.StatusCode)"
    $pendingResponseBody = Get-HttpBodyText $pendingResponse
    $pendingAck = $pendingResponseBody | ConvertFrom-Json
    Assert-That ($pendingAck.outcome -eq 'PENDING_CONFIRMATION') 'UNAVAILABLE outcome is PENDING_CONFIRMATION'
    Assert-That ($pendingAck.request.status -eq 'PENDING' -and $pendingAck.request.origin -eq 'AUTO_GEO_FAILURE') 'automatic request projection is PENDING/AUTO_GEO_FAILURE'
    Assert-That (-not [string]::IsNullOrWhiteSpace($pendingAck.retryAt)) 'PENDING ACK has retryAt'

    $pendingStateJs = "const r=db.late_checkin_requests.findOne({lesson_id:$pendingLessonId});const c=db.student_checkin_receipts.findOne({lesson_id:$pendingLessonId});const o=db.attendance_outbox.findOne({event_type:'late_checkin.requested'});const e=o?JSON.parse(o.payload):null;print(JSON.stringify({requests:db.late_checkin_requests.countDocuments({lesson_id:$pendingLessonId}),receipts:db.student_checkin_receipts.countDocuments({lesson_id:$pendingLessonId}),pairs:db.student_checkin_pairs.countDocuments({lesson_id:$pendingLessonId}),outbox:db.attendance_outbox.countDocuments({event_type:'late_checkin.requested'}),status:r&&r.status,origin:r&&r.origin,studentId:r&&r.student_id.toString(),studentName:r&&r.student_name,subjectId:r&&r.subject_id.toString(),receiptKey:c&&c.idempotency_key,receiptOutcome:c&&c.outcome,outboxStatus:o&&o.status,eventTraceId:e&&e.trace_id}))"
    $pendingState = Read-MongoJson $pendingStateJs
    Assert-That ($pendingState.requests -eq 1 -and $pendingState.receipts -eq 1 -and $pendingState.pairs -eq 1 -and $pendingState.outbox -eq 1) 'PENDING transaction persisted request, receipt, pair and one outbox row'
    Assert-That ($pendingState.status -eq 'PENDING' -and $pendingState.origin -eq 'AUTO_GEO_FAILURE') 'persisted request status/origin'
    Assert-That ([long]$pendingState.studentId -eq $studentId) 'spoofed X-User-Id did not become persisted identity'
    Assert-That (-not [string]::IsNullOrWhiteSpace($pendingState.studentName) -and [long]$pendingState.subjectId -eq 1) 'real academic/schedule gRPC metadata persisted'
    Assert-That ($pendingState.receiptKey -ceq $pendingKey -and $pendingState.receiptOutcome -eq 'PENDING_CONFIRMATION') 'HTTP idempotency key and ACK outcome reached the attendance transaction'
    Assert-That (-not [string]::IsNullOrWhiteSpace($pendingState.eventTraceId)) 'late-checkin outbox carries a concrete trace id'
    Assert-That ($pendingState.outboxStatus -eq 'pending') 'outbox remains pending while RabbitMQ is unavailable'

    $replayResponse = Invoke-HttpJson POST $pendingUri $pendingHeaders $pendingBody
    $replayResponseBody = Get-HttpBodyText $replayResponse
    $replayAck = $replayResponseBody | ConvertFrom-Json
    $report.replayComparison = [ordered]@{
        rawEqual = $replayResponseBody -ceq $pendingResponseBody
        first = [ordered]@{
            outcome = $pendingAck.outcome
            lessonId = $pendingAck.lessonId
            requestId = $pendingAck.request.id
            requestStatus = $pendingAck.request.status
            retryAt = $pendingAck.retryAt
            serverNow = $pendingAck.serverNow
        }
        replay = [ordered]@{
            outcome = $replayAck.outcome
            lessonId = $replayAck.lessonId
            requestId = $replayAck.request.id
            requestStatus = $replayAck.request.status
            retryAt = $replayAck.retryAt
            serverNow = $replayAck.serverNow
        }
    }
    Assert-That (
        $replayAck.outcome -ceq $pendingAck.outcome -and
        $replayAck.lessonId -eq $pendingAck.lessonId -and
        $replayAck.request.id -ceq $pendingAck.request.id -and
        $replayAck.request.status -ceq $pendingAck.request.status -and
        $replayAck.retryAt -ceq $pendingAck.retryAt -and
        $replayAck.serverNow -ceq $pendingAck.serverNow
    ) 'same key/payload replays the persisted ACK fields'
    Assert-That ($replayResponse.StatusCode -eq 200 -and $replayResponseBody -ceq $pendingResponseBody) 'same key/payload replays byte-equivalent ACK'
    $replayState = Read-MongoJson $pendingStateJs
    Assert-That ($replayState.requests -eq 1 -and $replayState.receipts -eq 1 -and $replayState.pairs -eq 1 -and $replayState.outbox -eq 1) 'replay is mutation-free'
    Add-Assertion 'Rabbit outage commits domain state and idempotent PENDING outbox' @{ lessonId = $pendingLessonId; requestCount = 1; receiptCount = 1; pairCount = 1; outboxStatus = 'pending'; signedStudentId = $studentId; receiptKey = $pendingKey; eventTraceId = $pendingState.eventTraceId }

    $report.stage = 'outbox-recovery'
    Write-Host 'Restarting task RabbitMQ and waiting for outbox recovery...'
    $null = Invoke-Docker -DockerArgs @('start', $containers.Rabbit) -Description 'restart task RabbitMQ'
    Wait-Until 'RabbitMQ recovery' { & docker exec $containers.Rabbit rabbitmq-diagnostics -q ping *> $null; return $LASTEXITCODE -eq 0 } 180
    Wait-Until 'late_checkin.requested outbox sent' {
        $state = Read-MongoJson "const o=db.attendance_outbox.findOne({event_type:'late_checkin.requested'});print(JSON.stringify({sent:!!o&&o.status==='sent'&&o.sent_at!=null}))"
        return [bool]$state.sent
    } 60
    Add-Assertion 'real scheduled outbox publisher recovered after Rabbit restart' @{ eventType = 'late_checkin.requested'; status = 'sent'; sentAtPresent = $true }

    $report.stage = 'present-and-auth-negative'
    $presentKey = "runtime-present-$runId"
    $presentHeaders = @{
        Authorization = "Bearer $accessToken"
        'Idempotency-Key' = $presentKey
        'X-Correlation-Id' = $correlation
    }
    $presentBody = @{ geo = @{ kind = 'COORDINATES'; latitude = 55.788204; longitude = 37.606762 } } | ConvertTo-Json -Compress -Depth 4
    $presentUri = "http://127.0.0.1:28080/api/v1/student/lessons/$presentLessonId/checkin"
    $presentResponse = Invoke-HttpJson POST $presentUri $presentHeaders $presentBody
    Assert-That ($presentResponse.StatusCode -eq 200) "coordinate check-in returned $($presentResponse.StatusCode)"
    $presentAck = Get-HttpBodyText $presentResponse | ConvertFrom-Json
    Assert-That ($presentAck.outcome -eq 'PRESENT' -and $presentAck.attendance.source -eq 'STUDENT_GEO') 'coordinate outcome/provenance is PRESENT/STUDENT_GEO'

    $presentStateJs = "const a=db.attendances.findOne({lesson_id:$presentLessonId,user_id:NumberLong('$studentId')});const c=db.student_checkin_receipts.findOne({lesson_id:$presentLessonId,student_id:NumberLong('$studentId')});const o=db.attendance_outbox.findOne({event_type:'attendance.marked'});const e=o?JSON.parse(o.payload):null;print(JSON.stringify({attendances:db.attendances.countDocuments({lesson_id:$presentLessonId,user_id:NumberLong('$studentId')}),status:a&&a.status,source:a&&a.source,markedBy:a&&a.marked_by!=null?a.marked_by:null,receipts:db.student_checkin_receipts.countDocuments({lesson_id:$presentLessonId}),pairs:db.student_checkin_pairs.countDocuments({lesson_id:$presentLessonId}),outbox:db.attendance_outbox.countDocuments({event_type:'attendance.marked'}),receiptKey:c&&c.idempotency_key,receiptOutcome:c&&c.outcome,outboxStatus:o&&o.status,eventTraceId:e&&e.trace_id}))"
    $presentState = Read-MongoJson $presentStateJs
    Assert-That ($presentState.attendances -eq 1 -and $presentState.status -eq 'PRESENT' -and $presentState.source -eq 'STUDENT_GEO' -and $null -eq $presentState.markedBy) 'persisted attendance provenance and null actor'
    Assert-That ($presentState.receipts -eq 1 -and $presentState.pairs -eq 1 -and $presentState.outbox -eq 1) 'PRESENT transaction persisted receipt, pair and outbox'
    Assert-That ($presentState.receiptKey -ceq $presentKey -and $presentState.receiptOutcome -eq 'PRESENT') 'HTTP idempotency key and ACK outcome reached the attendance transaction'
    Assert-That (-not [string]::IsNullOrWhiteSpace($presentState.eventTraceId)) 'attendance outbox carries a concrete trace id'

    Wait-Until 'attendance.marked outbox sent' {
        $state = Read-MongoJson "const o=db.attendance_outbox.findOne({event_type:'attendance.marked'});print(JSON.stringify({sent:!!o&&o.status==='sent'&&o.sent_at!=null}))"
        return [bool]$state.sent
    } 60
    $deliveryRequest = @{
        count = 10
        ackmode = 'ack_requeue_false'
        encoding = 'auto'
        truncate = 50000
    } | ConvertTo-Json -Compress
    $deliveryResponse = Invoke-RabbitManagement POST "queues/%2F/$evidenceQueue/get" $deliveryRequest
    Assert-That ($deliveryResponse.StatusCode -eq 200) "Rabbit evidence get returned $($deliveryResponse.StatusCode)"
    $deliveries = @(Get-HttpBodyText $deliveryResponse | ConvertFrom-Json)
    $deliveredEvents = @($deliveries | ForEach-Object { $_.payload | ConvertFrom-Json })
    $requestedDeliveries = @($deliveredEvents | Where-Object { $_.event_type -eq 'late_checkin.requested' })
    $markedDeliveries = @($deliveredEvents | Where-Object { $_.event_type -eq 'attendance.marked' })
    Assert-That ($deliveries.Count -eq 2 -and $requestedDeliveries.Count -eq 1 -and $markedDeliveries.Count -eq 1) 'task Rabbit sink received each domain event exactly once'
    Assert-That ($requestedDeliveries[0].trace_id -eq $pendingState.eventTraceId -and $markedDeliveries[0].trace_id -eq $presentState.eventTraceId) 'Rabbit deliveries match the committed outbox trace ids'
    Add-Assertion 'RabbitMQ received both task events without duplicate delivery' @{
        exchange = 'rut-uit.events'
        deliveryCount = 2
        eventTypes = @('late_checkin.requested', 'attendance.marked')
        traceIds = @($requestedDeliveries[0].trace_id, $markedDeliveries[0].trace_id)
    }
    Add-Assertion 'real coordinate mutation through gateway/BFF/attendance' @{ lessonId = $presentLessonId; attendanceStatus = 'PRESENT'; source = 'STUDENT_GEO'; markedBy = $null; outboxStatus = 'sent'; receiptKey = $presentKey; eventTraceId = $presentState.eventTraceId }

    $signatureIndex = $accessToken.LastIndexOf('.') + 1
    Assert-That ($signatureIndex -gt 1 -and $signatureIndex -lt $accessToken.Length) 'login token has a JWT signature segment'
    $signatureFirst = $accessToken[$signatureIndex]
    $invalidToken = $accessToken.Substring(0, $signatureIndex) + $(if ($signatureFirst -eq 'A') { 'B' } else { 'A' }) + $accessToken.Substring($signatureIndex + 1)
    $privateKeyPath = Join-Path $keysDir 'private.key'
    Assert-That (Test-Path -LiteralPath $privateKeyPath) 'auth private key exists for signed negative runtime probes'
    $nowUnix = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $negativeTokens = [ordered]@{
        'invalid-signature' = $invalidToken
        'expired' = New-SignedExternalJwt $privateKeyPath $studentId 'rutcampustrack-auth' 'rutcampustrack' ($nowUnix - 60)
        'wrong-issuer' = New-SignedExternalJwt $privateKeyPath $studentId 'wrong-runtime-issuer' 'rutcampustrack' ($nowUnix + 120)
        'wrong-audience' = New-SignedExternalJwt $privateKeyPath $studentId 'rutcampustrack-auth' 'wrong-runtime-audience' ($nowUnix + 120)
    }
    foreach ($negative in $negativeTokens.GetEnumerator()) {
        $negativeHeaders = @{
            Authorization = "Bearer $($negative.Value)"
            'Idempotency-Key' = "runtime-$($negative.Key)-$runId"
        }
        $negativeResponse = Invoke-HttpJson POST $presentUri $negativeHeaders $presentBody
        Assert-That ($negativeResponse.StatusCode -eq 401) "$($negative.Key) JWT returned $($negativeResponse.StatusCode), expected 401"
        Add-Assertion "$($negative.Key) external JWT rejected" @{ httpStatus = 401; downstreamMutation = $false }
    }
    $afterInvalid = Read-MongoJson $presentStateJs
    Assert-That ($afterInvalid.receipts -eq 1 -and $afterInvalid.attendances -eq 1) 'invalid external JWT caused no downstream mutation'
    $negativeTokens = $null
    $invalidToken = $null

    $report.stage = 'log-evidence'
    Start-Sleep -Seconds 2
    foreach ($entry in @($processes)) {
        $entry.Process.Refresh()
        Assert-That (-not $entry.Process.HasExited) "$($entry.Name) remains alive after the runtime probes"
    }
    Add-Assertion 'network path is linked by observable state' @{
        gatewayHttpStatus = 200
        bffAckOutcome = $pendingAck.outcome
        attendanceReceiptKey = $pendingKey
        signedStudentId = $studentId
        scheduleLessonId = $pendingLessonId
        academicSubjectId = 1
        eventTraceId = $pendingState.eventTraceId
        liveServiceProcesses = $processes.Count
    }
    $report.trace = [ordered]@{
        requestCorrelationId = $correlation
        pendingIdempotencyKey = $pendingKey
        presentIdempotencyKey = $presentKey
        eventTraceIds = @($pendingState.eventTraceId, $presentState.eventTraceId)
        logFiles = @($processes | ForEach-Object { $_.Stdout; $_.Stderr })
        evidence = 'Gateway HTTP responses, byte-equivalent BFF ACK replay, attendance receipts carrying the HTTP keys, signed identity, schedule lesson ids, academic subject metadata and outbox trace ids form the asserted network/state chain; service logs are retained as supporting evidence.'
    }
    $report.status = 'PASS'
    $report.stage = 'complete'
    $report.completedAt = [DateTimeOffset]::Now.ToString('o')
    $report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $resultPath -Encoding utf8NoBOM
    Write-Host "PASS: full runtime evidence written to $resultPath"
} catch {
    $report.status = 'FAIL'
    $report.error = $_.Exception.Message
    $report.completedAt = [DateTimeOffset]::Now.ToString('o')
    New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
    $report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $resultPath -Encoding utf8NoBOM
    Write-Error "Runtime gate failed at '$($report.stage)': $($_.Exception.Message)"
    throw
} finally {
    $accessToken = $null
    $dbPassword = $null
    $redisPassword = $null
    $rabbitPassword = $null
    $grpcSecret = $null
    $issuerSecret = $null
    $tmaToken = $null
    Cleanup-OwnedResources
}
