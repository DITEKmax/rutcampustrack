# Isolated one-scenario wrapper. Run only after root source review + heavy lease.
$ErrorActionPreference = 'Stop'
$taskRoot = (Get-Location).Path
$evidenceRoot = Join-Path $taskRoot '.agent/evidence/bot-pending-delivery-20261002'
$taskLabel = 'bot-pending-delivery-20261002'
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$runOutput = Join-Path $evidenceRoot ('restart-r1-' + $suffix)
if (Test-Path -LiteralPath $runOutput) { throw 'Evidence run already exists; no reuse of markers/attempts' }
$python = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack/services/notification-bot/.venv/Scripts/python.exe'
$owned = @()
$probeExit = $null
$cleanup = @()
New-Item -ItemType Directory -Path $runOutput | Out-Null
try {
    foreach ($image in @('rabbitmq:3.13-management-alpine', 'redis:7-alpine')) {
        docker image inspect $image --format '{{.Id}}'
        if ($LASTEXITCODE -ne 0) { throw "Cached image missing: $image (no pull/install)" }
    }
    $rabbit = docker run -d --pull=never --label "rct.task=$taskLabel" --name "rct-botpending-rabbit-$suffix" -p '127.0.0.1::5672' rabbitmq:3.13-management-alpine
    if ($LASTEXITCODE -ne 0) { throw 'Rabbit startup failed' }
    $owned += $rabbit.Trim()
    $cache = docker run -d --pull=never --label "rct.task=$taskLabel" --name "rct-botpending-redis-$suffix" -p '127.0.0.1::6379' redis:7-alpine
    if ($LASTEXITCODE -ne 0) { throw 'Redis startup failed' }
    $owned += $cache.Trim()
    $rabbitMapping = docker port $rabbit 5672/tcp
    if ($LASTEXITCODE -ne 0 -or $rabbitMapping -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Unexpected Rabbit mapping' }
    $rabbitPort = [int]$Matches[1]
    $redisMapping = docker port $cache 6379/tcp
    if ($LASTEXITCODE -ne 0 -or $redisMapping -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Unexpected Redis mapping' }
    $redisPort = [int]$Matches[1]
    @{ task=$taskLabel; rabbit=$rabbit.Trim(); redis=$cache.Trim(); rabbitPort=$rabbitPort; redisPort=$redisPort; revision=(git rev-parse HEAD) } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runOutput 'resources.json')
    Write-Output "OWNED Rabbit=$rabbit Redis=$cache localhost AMQP=$rabbitPort Redis=$redisPort"
    $ready = $false
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        docker exec --user rabbitmq $rabbit rabbitmq-diagnostics -q ping *> (Join-Path $runOutput 'rabbit-readiness.log')
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'Rabbit readiness timeout; preserve failure log' }
    docker exec $cache redis-cli ping
    if ($LASTEXITCODE -ne 0) { throw 'Redis readiness failed' }
    & $python (Join-Path $evidenceRoot 'restart_probe.py') --root $taskRoot --output $runOutput --rabbit-port $rabbitPort --redis-port $redisPort *> (Join-Path $runOutput 'probe.log')
    $probeExit = $LASTEXITCODE
    Get-Content -LiteralPath (Join-Path $runOutput 'probe.log') -Tail 30
    if ($probeExit -ne 0) { throw "Probe failed exit=$probeExit" }
    # Both owned child processes are terminal before authoritative broker counters.
    $queueJson = docker exec --user rabbitmq $rabbit rabbitmqctl -q list_queues name messages_ready messages_unacknowledged --formatter=json
    if ($LASTEXITCODE -ne 0) { throw 'Broker queue counters failed' }
    $queueJson | Set-Content -LiteralPath (Join-Path $runOutput 'broker-final-queues.json')
    $queueRows = $queueJson | ConvertFrom-Json
    foreach ($name in @('notification-bot.events', 'notification-bot.events.dlq')) {
        $row = @($queueRows | Where-Object { $_.name -eq $name })
        if ($row.Count -ne 1 -or $row[0].messages_ready -ne 0 -or $row[0].messages_unacknowledged -ne 0) { throw "Original still pending in $name" }
    }
    Write-Output 'Broker authoritative counters: main and DLQ ready=0/unacknowledged=0 after both child processes terminal'

}
finally {
    foreach ($id in $owned) {
        $actualLabel = docker inspect $id --format '{{index .Config.Labels "rct.task"}}'
        if ($LASTEXITCODE -eq 0 -and $actualLabel.Trim() -eq $taskLabel) {
            docker logs $id *> (Join-Path $runOutput ($id.Substring(0,12) + '-container.log'))
            # Exact owned synthetic containers/anonymous volumes only. No broad cleanup.
            docker rm -f -v $id
            $removeExit = $LASTEXITCODE
            docker inspect $id *> $null
            $absent = $LASTEXITCODE -ne 0
            $cleanup += @{ id=$id; removeExit=$removeExit; absent=$absent }
        }
        else { $cleanup += @{ id=$id; removeExit=$null; absent=$false; error='Ownership mismatch; not removed' } }
    }
    $cleanup | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $runOutput 'cleanup.json')
    if (@($cleanup | Where-Object { -not $_.absent -or $_.removeExit -ne 0 }).Count -gt 0) { throw 'Exact cleanup failed' }
}
exit $probeExit
