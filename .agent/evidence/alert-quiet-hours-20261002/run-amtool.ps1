$ErrorActionPreference='Stop'
$taskAlertRoot=(Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$taskAlertConfig=(Resolve-Path (Join-Path $taskAlertRoot 'infra/alertmanager/alertmanager.yml')).Path
$taskAlertImage='prom/alertmanager:v0.27.0@sha256:e13b6ed5cb929eeaee733479dce55e10eb3bc2e9c4586c705a4e8da41e5eacf5'
$taskAlertRevision=(& git -C $taskAlertRoot rev-parse HEAD).Trim()
$taskAlertStarted=[DateTime]::UtcNow.ToString('o')
# Native offline operations only; no Alertmanager server or network endpoint.
& docker run --pull never --rm --network none --name rct-alert-quiet-hours-check-1002 `
    --mount "type=bind,source=$taskAlertConfig,target=/etc/alertmanager/alertmanager.yml,readonly" `
    --entrypoint /bin/sh $taskAlertImage -ec '
      /bin/amtool --version
      /bin/amtool check-config /etc/alertmanager/alertmanager.yml
      /bin/amtool config routes test --config.file=/etc/alertmanager/alertmanager.yml --verify.receivers=notification-webhook --tree alertname=QuietHoursProbe severity=warning
      /bin/amtool config routes test --config.file=/etc/alertmanager/alertmanager.yml --verify.receivers=notification-webhook --tree alertname=QuietHoursProbe severity=critical
    ' *> (Join-Path $PSScriptRoot 'amtool.log')
$taskAlertExit=$LASTEXITCODE
$taskAlertOwned=@(& docker ps -a --filter 'name=rct-alert-quiet-hours-' --format '{{.ID}} {{.Names}}')
[ordered]@{revision=$taskAlertRevision;image=$taskAlertImage;startedAt=$taskAlertStarted;terminalAt=[DateTime]::UtcNow.ToString('o');exitCode=$taskAlertExit;ownedContainersRemaining=$taskAlertOwned;lease='root bounded cached amtool';network='none';configMount='readonly';limitation='routes test does not simulate wall clock'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'amtool-result.json') -Encoding utf8
Get-Content -LiteralPath (Join-Path $PSScriptRoot 'amtool.log')
Write-Output ('OwnedContainersRemaining='+$taskAlertOwned.Count)
exit $taskAlertExit
