[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Initialize','Check','Start','Status','Stop')][string]$Action,
    [string]$PrivateDirectory = "$env:LOCALAPPDATA/RutCampusTrack/local-stand",
    [string]$BackendManifest,
    [string]$BackendManifestSha256,
    [string]$FrontendManifest,
    [string]$FrontendManifestSha256,
    [string]$TelegramTokenPath,
    [string]$DevTlsCertificate = "$env:LOCALAPPDATA/RutCampusTrack/certs/localhost.pem",
    [string]$DevTlsPrivateKey = "$env:LOCALAPPDATA/RutCampusTrack/certs/localhost-key.pem"
)
$ErrorActionPreference = 'Stop'
$project = 'rct-local-persistent'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$compose = Join-Path $repo 'infra/local-stand/compose.yml'
$private = [IO.Path]::GetFullPath($PrivateDirectory)
$allowedPrivateRoot = [IO.Path]::GetFullPath("$env:LOCALAPPDATA/RutCampusTrack")
# Private settings, provider tokens and generated credentials never go into Git.
if (-not $private.StartsWith($allowedPrivateRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'PRIVATE_DIRECTORY_MUST_BE_UNDER_LOCALAPPDATA_RUTCAMPUSTRACK'
}
$envFile = Join-Path $private 'stand.env'
$pinsFile = Join-Path $private 'artifact-pins.json'
$composeArgs = @('compose','--project-name',$project,'--env-file',$envFile,'-f',$compose)

function Assert-File([string]$Path) {
    if (-not $Path -or -not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw 'REQUIRED_INPUT_FILE_MISSING' }
    if ((Get-Item -LiteralPath $Path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'REPARSE_INPUT_REFUSED' }
}
function Read-PinnedJson([string]$Path, [string]$Hash) {
    Assert-File $Path
    if ($Hash -notmatch '^[a-fA-F0-9]{64}$' -or (Get-FileHash -LiteralPath $Path).Hash -ine $Hash) { throw 'ARTIFACT_MANIFEST_PIN_MISMATCH' }
    Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}
function Verify-Artifacts($Pins) {
    $backend = Read-PinnedJson $Pins.backend.path $Pins.backend.sha256
    if (@($backend.artifacts.jars).Count -ne 8) { throw 'EXACT_EIGHT_BACKEND_JARS_REQUIRED' }
    foreach ($jar in $backend.artifacts.jars) {
        $path = [IO.Path]::GetFullPath((Join-Path $backend.source.absoluteRepo $jar.relativePath))
        if (-not $path.StartsWith([IO.Path]::GetFullPath($backend.source.absoluteRepo) + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'JAR_PATH_ESCAPES_ARTIFACT_REPO' }
        Assert-File $path
        if ((Get-FileHash -LiteralPath $path).Hash -ine $jar.sha256) { throw 'BACKEND_ARTIFACT_BYTES_CHANGED' }
    }
    $frontend = Read-PinnedJson $Pins.frontend.path $Pins.frontend.sha256
    if ($frontend.sourceRevision -notmatch '^[a-f0-9]{40}$' -or $frontend.pwa.base -cne '/app/' -or $frontend.tma.base -cne '/mini-app/') { throw 'FRONTEND_REVISION_OR_ROUTE_CONTRACT_INVALID' }
    foreach ($name in @('pwa','tma')) {
        $bundle = $frontend.$name
        $root = [IO.Path]::GetFullPath($bundle.path)
        if (@($bundle.files).Count -lt 2) { throw 'FRONTEND_BUNDLE_MANIFEST_EMPTY' }
        foreach ($file in $bundle.files) {
            $path = [IO.Path]::GetFullPath((Join-Path $root $file.relativePath))
            if (-not $path.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'FRONTEND_ARTIFACT_PATH_ESCAPE' }
            Assert-File $path
            if ((Get-FileHash -LiteralPath $path).Hash -ine $file.sha256) { throw 'FRONTEND_ARTIFACT_BYTES_CHANGED' }
        }
        Assert-File (Join-Path $root 'index.html')
    }
    @{ backend = $backend; frontend = $frontend }
}
function Invoke-Compose([string[]]$Arguments) {
    # Never stream Compose interpolation, container environments or provider logs.
    # Compose gives ambient variables priority over its explicit env file. Remove
    # only the template's referenced names during this call so another stand
    # cannot silently change this stand's paths, credentials or subnet.
    if ('--profile' -in $Arguments -or '--remove-orphans' -in $Arguments -or 'notification-bot' -in $Arguments) { throw 'BOT_PROFILE_OR_ORPHAN_REMOVAL_REFUSED' }
    $names = @([regex]::Matches([IO.File]::ReadAllText($compose), '\$\{([A-Z0-9_]+)') | ForEach-Object { $_.Groups[1].Value }) + @('COMPOSE_PROFILES','COMPOSE_REMOVE_ORPHANS') | Sort-Object -Unique
    $saved = @{}
    foreach ($name in $names) {
        $saved[$name]=@{present=[Environment]::GetEnvironmentVariables('Process').Contains($name);value=[Environment]::GetEnvironmentVariable($name,'Process')}
        # On this .NET runtime SetEnvironmentVariable(name, null) leaves an
        # empty entry, which still overrides Compose's --env-file value.
        Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
    }
    try {
        [Environment]::SetEnvironmentVariable('COMPOSE_REMOVE_ORPHANS','false','Process')
        # Check the effective default service set before any lifecycle command.
        # Also catches profile selection added to the private env file itself.
        $rendered = @(& docker @composeArgs config --format json --no-env-resolution 2>&1)
        if ($LASTEXITCODE -ne 0) { throw 'COMPOSE_SCOPE_RENDER_FAILED_OUTPUT_WITHHELD' }
        $effective = ($rendered -join "`n") | ConvertFrom-Json
        if ($effective.services.'notification-bot') { throw 'BOT_SERVICE_IN_NORMAL_LIFECYCLE_REFUSED' }
        $result = @(& docker @composeArgs @Arguments 2>&1); $exitCode=$LASTEXITCODE
    }
    finally {
        foreach ($name in $names) {
            if ($saved[$name].present) { [Environment]::SetEnvironmentVariable($name,$saved[$name].value,'Process') }
            else { Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue }
        }
    }
    if ($exitCode -ne 0) { throw 'COMPOSE_OPERATION_FAILED_OUTPUT_WITHHELD' }
    $result
}
function Assert-ResourceOwnership {
    $ids = @(& docker ps -aq --filter "label=com.docker.compose.project=$project" 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'DOCKER_INVENTORY_FAILED' }
    foreach ($id in $ids) {
        $marker = & docker inspect --format '{{index .Config.Labels "io.rutcampustrack.local-stand"}}' $id 2>$null
        if ($LASTEXITCODE -ne 0 -or $marker -cne 'persistent') { throw 'PROJECT_CONTAINER_OWNERSHIP_REFUSED' }
    }
    foreach ($suffix in @('postgres-academic-data','postgres-schedule-data','mongo-data','mongo-config','redis-data','rabbitmq-data','jwt-keys')) {
        $name = "$project-$suffix"
        $names = @(& docker volume ls --format '{{.Name}}' --filter "name=^$name`$" 2>$null)
        if ($LASTEXITCODE -ne 0) { throw 'VOLUME_INVENTORY_FAILED' }
        if ($name -in $names) {
            $owner = & docker volume inspect --format '{{index .Labels "com.docker.compose.project"}}' $name 2>$null
            if ($LASTEXITCODE -ne 0 -or $owner -cne $project) { throw 'VOLUME_OWNERSHIP_REFUSED' }
        }
    }
    $networks = @(& docker network ls --format '{{.Name}}' 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'NETWORK_INVENTORY_FAILED' }
    if ("$project-private" -in $networks) {
        $owner = & docker network inspect --format '{{index .Labels "com.docker.compose.project"}}' "$project-private" 2>$null
        if ($LASTEXITCODE -ne 0 -or $owner -cne $project) { throw 'NETWORK_OWNERSHIP_REFUSED' }
    }
    # This fixed local subnet must not overlap an unrelated retained network.
    foreach ($network in $networks | Where-Object { $_ -ne "$project-private" }) {
        $subnets = @(& docker network inspect --format '{{range .IPAM.Config}}{{.Subnet}} {{end}}' $network 2>$null)
        if ($LASTEXITCODE -ne 0) { throw 'NETWORK_SUBNET_INSPECTION_FAILED' }
        foreach ($cidr in (($subnets -join ' ').Trim() -split '\s+')) {
            if ($cidr -notmatch '^(\d+\.\d+\.\d+\.\d+)/(\d+)$') { continue }
            $octets = $Matches[1].Split('.'); $bits=[int]$Matches[2]
            $start = ([uint64]$octets[0] -shl 24) + ([uint64]$octets[1] -shl 16) + ([uint64]$octets[2] -shl 8) + [uint64]$octets[3]
            $size = [uint64][Math]::Pow(2,32-$bits); $start = [uint64]([Math]::Floor($start/$size)*$size); $end=$start+$size-1
            $ourStart=[uint64]2887702016 # 172.30.214.0
            if ($start -le ($ourStart+255) -and $end -ge $ourStart) { throw 'LOCAL_SUBNET_COLLIDES_WITH_RETAINED_NETWORK' }
        }
    }
}
function Get-ValidatedConfig {
    Assert-File $envFile
    Assert-File $pinsFile
    $pins = Get-Content -LiteralPath $pinsFile -Raw | ConvertFrom-Json
    $artifacts = Verify-Artifacts $pins
    $config = (Invoke-Compose @('config','--format','json')) -join "`n" | ConvertFrom-Json
    if ($config.services.'notification-bot') { throw 'BOT_SERVICE_IN_NORMAL_CONFIG_REFUSED' }
    foreach ($service in $config.services.PSObject.Properties) {
        $value = $service.Value
        if ($value.build -or $value.tmpfs -or $value.pull_policy -cne 'never') { throw 'BUILD_PULL_OR_TMPFS_REFUSED' }
        foreach ($port in $value.ports) {
            if ($service.Name -cne 'nginx' -or $port.host_ip -cne '127.0.0.1' -or $port.published -ne '18514' -or $port.target -ne 443) { throw 'PUBLISHED_PORT_SCOPE_REFUSED' }
        }
        foreach ($mount in $value.volumes | Where-Object type -eq bind) { Assert-FileOrDirectory $mount.source }
    }
    foreach ($name in @('pwa','tma')) {
        $mount = @($config.services.nginx.volumes | Where-Object target -eq "/usr/share/nginx/$name")
        if ($mount.Count -ne 1 -or [IO.Path]::GetFullPath($mount[0].source) -ine [IO.Path]::GetFullPath($artifacts.frontend.$name.path)) { throw 'FRONTEND_MOUNT_PIN_MISMATCH' }
    }
    foreach ($jar in $artifacts.backend.artifacts.jars) {
        $path = [IO.Path]::GetFullPath((Join-Path $artifacts.backend.source.absoluteRepo $jar.relativePath))
        $mounts = @($config.services.PSObject.Properties.Value.volumes | Where-Object { $_.target -eq '/opt/rct/app.jar' -and [IO.Path]::GetFullPath($_.source) -ieq $path })
        if ($mounts.Count -ne 1) { throw 'BACKEND_MOUNT_PIN_MISMATCH' }
    }
    $network = $config.networks.private_net
    if ($network.name -cne "$project-private" -or $network.ipam.config[0].subnet -cne '172.30.214.0/24' -or
        $network.ipam.config[0].ip_range -cne '172.30.214.128/25' -or $config.services.nginx.networks.private_net.ipv4_address -cne '172.30.214.10' -or
        $config.services.'api-gateway'.environment.GATEWAY_TRUSTED_PROXY_ADDRESSES -cne '172.30.214.10') { throw 'NETWORK_AND_TRUSTED_PROXY_CONTRACT_CHANGED' }
    foreach ($certificate in @((Join-Path $private 'academic-tls/academic-server.crt'),(Join-Path $private 'schedule-tls/schedule-server.crt'),
        ($config.services.nginx.volumes | Where-Object target -eq '/etc/nginx/certs/server.crt').source)) { Assert-TlsValidity $certificate }
    Assert-ResourceOwnership
    $config
}
function Assert-FileOrDirectory([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path) -or ((Get-Item -LiteralPath $Path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'BIND_MOUNT_INPUT_MISSING_OR_REPARSE' }
}
function New-Secret {
    [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
}
function Write-PrivateFile([string]$Path, [string]$Content) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Content); $stream.Write($bytes) } finally { $stream.Dispose() }
}
function New-ServiceTls([string]$Name) {
    $target = Join-Path $private ($Name + '-tls')
    $null = New-Item -ItemType Directory -Path $target
    $rsa = [Security.Cryptography.RSA]::Create(3072)
    try {
        $request = [Security.Cryptography.X509Certificates.CertificateRequest]::new("CN=$Name-service", $rsa,
            [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1)
        $san = [Security.Cryptography.X509Certificates.SubjectAlternativeNameBuilder]::new()
        $san.AddDnsName("$Name-service"); $san.AddDnsName('localhost'); $san.AddIpAddress([Net.IPAddress]::Loopback)
        $request.CertificateExtensions.Add($san.Build())
        $request.CertificateExtensions.Add([Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new(
            [Security.Cryptography.X509Certificates.X509KeyUsageFlags]::DigitalSignature -bor [Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyEncipherment, $true))
        $certificate = $request.CreateSelfSigned([DateTimeOffset]::UtcNow.AddMinutes(-5), [DateTimeOffset]::UtcNow.AddDays(365))
        try {
            Write-PrivateFile (Join-Path $target "$Name-server.crt") $certificate.ExportCertificatePem()
            Write-PrivateFile (Join-Path $target "$Name-server.key") $rsa.ExportPkcs8PrivateKeyPem()
        } finally { $certificate.Dispose() }
    } finally { $rsa.Dispose() }
}
function Assert-TlsValidity([string]$Path) {
    Assert-File $Path
    $cert = [Security.Cryptography.X509Certificates.X509Certificate2]::CreateFromPem([IO.File]::ReadAllText($Path))
    try {
        if ($cert.NotBefore.ToUniversalTime() -gt [DateTime]::UtcNow -or $cert.NotAfter.ToUniversalTime() -le [DateTime]::UtcNow) { throw 'TLS_CERTIFICATE_EXPIRED_OR_NOT_YET_VALID_NO_AUTOROTATION' }
    } finally { $cert.Dispose() }
}

try {
    if ($Action -eq 'Initialize') {
        # Initialize once; an existing environment is never overwritten or rotated.
        if (Test-Path -LiteralPath $private) { throw 'PRIVATE_DIRECTORY_ALREADY_EXISTS_REUSE_CHECK_OR_START' }
        $pins = @{backend=@{path=$BackendManifest;sha256=$BackendManifestSha256};frontend=@{path=$FrontendManifest;sha256=$FrontendManifestSha256}}
        $artifacts = Verify-Artifacts $pins
        foreach ($path in @($DevTlsCertificate,$DevTlsPrivateKey,$TelegramTokenPath)) { Assert-File $path }
        Assert-TlsValidity $DevTlsCertificate
        $token = [IO.File]::ReadAllText($TelegramTokenPath).Trim()
        if ($token -notmatch '^\d+:[A-Za-z0-9_-]+$') { throw 'PRIVATE_BOT_TOKEN_FORMAT_INVALID' }
        $null = New-Item -ItemType Directory -Path $private
        $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        $null = & icacls $private '/inheritance:r' '/grant:r' "*${sid}:(OI)(CI)F" 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'PRIVATE_DIRECTORY_ACL_FAILED' }
        # The earlier run's gRPC certs were valid for only four hours. Create
        # this new local stand's service credentials once, with explicit leaf
        # trust and SAN authorities; keep and validate them on every restart.
        New-ServiceTls 'academic'
        New-ServiceTls 'schedule'
        $values = [ordered]@{}
        foreach ($key in @('POSTGRES_ACADEMIC_PASSWORD','POSTGRES_SCHEDULE_PASSWORD','MONGO_ROOT_PASSWORD','MONGO_PASSWORD','MONGO_NOTIFICATION_PASSWORD','REDIS_PASSWORD','RABBITMQ_PASSWORD','GRPC_SECRET','INTERNAL_ISSUER_SECRET','ACADEMIC_TO_SCHEDULE_SERVICE_TOKEN','SCHEDULE_TO_ACADEMIC_SERVICE_TOKEN','BOT_TO_NOTIFICATION_SERVICE_TOKEN','ALERT_WEBHOOK_SECRET','CAMPUS_MAP_USAGE_HMAC_KEY')) { $values[$key] = New-Secret }
        $ec = [Security.Cryptography.ECDsa]::Create([Security.Cryptography.ECCurve]::NamedCurves.nistP256)
        try { $ecParams = $ec.ExportParameters($true) } finally { $ec.Dispose() }
        $values.VAPID_PRIVATE_KEY = [Convert]::ToBase64String($ecParams.D).TrimEnd('=').Replace('+','-').Replace('/','_')
        $values.VAPID_PUBLIC_KEY = [Convert]::ToBase64String([byte[]](@(4) + $ecParams.Q.X + $ecParams.Q.Y)).TrimEnd('=').Replace('+','-').Replace('/','_')
        $values.VAPID_SUBJECT = 'mailto:local-test@localhost'
        $values.MONGO_ROOT_USER='root'; $values.MONGO_USER='rct_attendance'; $values.MONGO_NOTIFICATION_USER='rct_notification'; $values.RABBITMQ_USER='rct_user'
        $values.TMA_BOT_TOKEN=$token
        $values.CORS_ALLOWED_ORIGIN='https://localhost:18514,https://127.0.0.1:18514'
        $values.NOTIFICATION_WS_ALLOWED_ORIGINS=$values.CORS_ALLOWED_ORIGIN
        $values.MINI_APP_URL='https://localhost:18514/mini-app/'; $values.MINI_APP_WEB_URL=$values.MINI_APP_URL
        $values.GATEWAY_PRIVATE_SUBNET='172.30.214.0/24'; $values.GATEWAY_NETWORK_GATEWAY='172.30.214.1'; $values.GATEWAY_DYNAMIC_IP_RANGE='172.30.214.128/25'; $values.GATEWAY_NGINX_IPV4='172.30.214.10'
        $values.RCT_PRIVATE_DIR=$private; $values.RCT_ARTIFACT_REPO=$artifacts.backend.source.absoluteRepo
        $values.RCT_PWA_DIST=$artifacts.frontend.pwa.path; $values.RCT_TMA_DIST=$artifacts.frontend.tma.path
        $values.ACADEMIC_GRPC_TLS_DIR=Join-Path $private 'academic-tls'; $values.SCHEDULE_GRPC_TLS_DIR=Join-Path $private 'schedule-tls'
        $values.RCT_DEV_TLS_CERT=$DevTlsCertificate; $values.RCT_DEV_TLS_KEY=$DevTlsPrivateKey
        $values.RCT_JAVA_IMAGE='eclipse-temurin:21-jre@sha256:ad0cdd9782db550ca7dde6939a16fd850d04e683d37d3cff79d84a5848ba6a5a'
        $values.RCT_PG_IMAGE='postgres:16@sha256:93d55776e04376e19adb2733e3ccebb4392ee7dd86d8ff238503b30fe719c84f'
        $values.RCT_MONGO_IMAGE='mongo:7@sha256:45d9c9b48aa1b56b5e3a9f906763fe432f376abb3bc2832438022b6d2534e4fe'
        $values.RCT_REDIS_IMAGE='redis:7@sha256:8b81dd37ff027bec4e516d41acfbe9fe2460070dc6d4a4570a2ac5b9d59df065'
        $values.RCT_RABBIT_IMAGE='rabbitmq:3.13@sha256:606d8c0d6b3c18d1da9afc53bc7cdb2a8d5486df91b5a9830e9e07626c9ae281'
        $values.RCT_NGINX_IMAGE='nginx@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10'
        $values.RCT_BFF_IMAGE='sha256:89862ecaac29e25493f0c5bed4d1d85751968fb60a13adca4e935e8a1fbd898a'
        $values.RCT_RENDERER_IMAGE='sha256:2064a6a89efabc0356fd7d9d3d4a703bdba7ff834a444df42d29b84be27eacc2'
        $values.RCT_BOT_IMAGE='sha256:26b4ffd97efa28cc1058a69aa0bdcb4865dccb64c5566e8d5f45b458b4378678'
        $lines = foreach ($key in $values.Keys) {
            $value = ([string]$values[$key]).Replace('\','/')
            if ($value -match "[\r\n']") { throw 'ENV_VALUE_CANNOT_BE_ENCODED' }
            "$key='$value'"
        }
        Write-PrivateFile $envFile (($lines -join "`n") + "`n")
        Write-PrivateFile (Join-Path $private 'telegram.env') "BOT_TOKEN='$token'`n"
        Write-PrivateFile (Join-Path $private 'mongo-rs0.key') ([Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(96)) + "`n")
        Write-PrivateFile $pinsFile ($pins | ConvertTo-Json -Depth 4)
        Write-Output 'INITIALIZED_PRIVATE_SETTINGS; Bot not started; next Check then Start'
        exit 0
    }
    if ($Action -eq 'Status') {
        Assert-ResourceOwnership
        Invoke-Compose @('ps','--all','--format','table')
        exit 0
    }
    if ($Action -eq 'Stop') {
        Assert-File $envFile
        Assert-ResourceOwnership
        $null = Invoke-Compose @('stop','--timeout','30')
        Write-Output 'STOPPED; containers, named volumes and private keys retained'
        exit 0
    }
    $config = Get-ValidatedConfig
    if ($Action -eq 'Check') { Write-Output 'CHECK_PASS; manifests, paths, port scope and resource ownership verified; no Docker mutations'; exit 0 }
    $ownEdge = @(& docker ps -q --filter "label=com.docker.compose.project=$project" --filter 'label=com.docker.compose.service=nginx' 2>$null)
    if (-not $ownEdge.Count -and (Get-NetTCPConnection -State Listen -LocalPort 18514 -ErrorAction SilentlyContinue)) { throw 'LOCALHOST_18514_ALREADY_IN_USE' }
    $null = Invoke-Compose @('up','-d','--no-build','--pull','never','--wait','--wait-timeout','180','postgres-academic','postgres-schedule','mongo-attendance','redis','rabbitmq')
    $js = "try { const s=rs.status(); if(s.set!=='rs0') throw Error('RS_IDENTITY'); } catch(e) { if(e.code!==94 && e.codeName!=='NotYetInitialized') throw e; rs.initiate({_id:'rs0',members:[{_id:0,host:'mongo-attendance:27017'}]}); }"
    $null = Invoke-Compose @('exec','-T','mongo-attendance','sh','-ec','exec mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval "$1"','sh',$js)
    $primary = $false
    for ($i=0; $i -lt 40; $i++) {
        try { $null = Invoke-Compose @('exec','-T','mongo-attendance','sh','-ec','exec mongosh --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval "quit(db.hello().isWritablePrimary ? 0 : 1)"'); $primary=$true; break } catch { Start-Sleep -Seconds 2 }
    }
    if (-not $primary) { throw 'MONGO_RS0_PRIMARY_TIMEOUT' }
    $null = Invoke-Compose @('up','-d','--no-build','--pull','never','--wait','--wait-timeout','300')
    Write-Output 'STARTED https://localhost:18514/app/ https://localhost:18514/mini-app/; Bot not started'
} catch {
    # Messages below are our fixed codes; never forward Docker output or secrets.
    $code = if ($_.Exception.Message -cmatch '^[A-Z0-9_]+$') { $_.Exception.Message } else { 'LOCAL_STAND_FAILED_DETAILS_WITHHELD' }
    Write-Error $code
    exit 1
}
