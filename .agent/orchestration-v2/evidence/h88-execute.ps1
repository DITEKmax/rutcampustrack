$ErrorActionPreference = 'Stop'
$rootPath = 'C:/Users/maksd/IntelliJIDEA/rutcampustrack'
$producerPath = Join-Path $rootPath '.agent/orchestration-v2/build-i6/producer.ps1'
$unionPath = Join-Path $rootPath '.agent/worktrees/v2-runtime-build-r3'
$evidencePath = Join-Path $rootPath '.agent/orchestration-v2/evidence'
$outputPath = Join-Path $rootPath '.agent/orchestration-v2/build-i6/h88-build'
$revision = '13e5fd1985b798bbb61bcc85969e6a74b1fc6837'
$expectedProducer = 'DB8A4B84E4D042F72F5E0912C2F270CA063AB4735228CB9299A45748DFAC7A83'
if ((Get-FileHash -LiteralPath $producerPath).Hash -ne $expectedProducer) { throw 'Producer freeze mismatch' }
if (Test-Path -LiteralPath (Join-Path $evidencePath 'h88-context.json')) { throw 'H88 already recorded' }
$manifest = Join-Path $outputPath 'requests-build-manifest.v1.json'
$runRoot = Join-Path $outputPath 'runs'
$context = [ordered]@{start=[DateTime]::UtcNow.ToString('o'); sourceRevision=$revision; producerHash=$expectedProducer; command=@('pwsh','-NoProfile','-File',$producerPath,'-UnionRepo',$unionPath,'-ExpectedRevision',$revision,'-ManifestPath',$manifest,'-RunsRoot',$runRoot); exitCode=$null}
$checkExit = 1
try {
    & pwsh -NoProfile -File $producerPath -UnionRepo $unionPath -ExpectedRevision $revision -ManifestPath $manifest -RunsRoot $runRoot 1> (Join-Path $evidencePath 'h88-stdout.log') 2> (Join-Path $evidencePath 'h88-stderr.log')
    $checkExit = $LASTEXITCODE
    $context.exitCode = $checkExit
} finally {
    $context.end = [DateTime]::UtcNow.ToString('o')
    if (Test-Path -LiteralPath $manifest) { $context.manifestHash = (Get-FileHash -LiteralPath $manifest).Hash }
    $context | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidencePath 'h88-context.json')
}
$context | ConvertTo-Json -Depth 5
exit $checkExit
