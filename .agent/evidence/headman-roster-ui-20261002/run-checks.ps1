$ErrorActionPreference = 'Stop'
$frontRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../frontends')).Path
$nodePath = 'C:/Users/maksd/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe'
$checks = @(
    @{name='mobile-types';args=@('node_modules/typescript/bin/tsc','-p','mobile-core/tsconfig.json','--noEmit')},
    @{name='pwa-types';args=@('node_modules/vue-tsc/bin/vue-tsc.js','-p','pwa-vue/tsconfig.json','--noEmit')},
    @{name='tma-types';args=@('node_modules/vue-tsc/bin/vue-tsc.js','-p','tma-vue/tsconfig.json','--noEmit')},
    @{name='targeted-tests';args=@('node_modules/vitest/vitest.mjs','run','--config','tma-vue/vite.config.ts','mobile-core/src/features/headman-group/headman-roster-download.test.ts','tma-vue/src/report-download-adapter.test.ts','-t','personal roster session boundary|accepts the roster format-only|keeps roster PNG','--maxWorkers','1','--no-file-parallelism')},
    @{name='scoped-lint';args=@('node_modules/eslint/bin/eslint.js','mobile-core/src/features/headman-group/HeadmanGroupScreen.vue','mobile-core/src/features/headman-group/headman-group-client.ts','mobile-core/src/features/headman-group/headman-roster-download.test.ts','mobile-core/src/shared/report-download-client.ts','tma-vue/src/report-download-adapter.ts','tma-vue/src/report-download-adapter.test.ts','--max-warnings=0')}
)
$results = @()
Push-Location $frontRoot
try {
    foreach ($check in $checks) {
        $startedAt = (Get-Date).ToString('o')
        $checkArgs = $check.args
        & $nodePath @checkArgs *> (Join-Path $PSScriptRoot ($check.name + '.log'))
        $trueExit = $LASTEXITCODE
        $results += [ordered]@{name=$check.name;command=(@($nodePath)+$checkArgs)-join ' ';startedAt=$startedAt;finishedAt=(Get-Date).ToString('o');exitCode=$trueExit}
        $results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'checks.json') -Encoding utf8
        Write-Output "$($check.name) exit=$trueExit"
    }
} finally { Pop-Location }
if (@($results | Where-Object exitCode -ne 0).Count -gt 0) { exit 1 }
exit 0
