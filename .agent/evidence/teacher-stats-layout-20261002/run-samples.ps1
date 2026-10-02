param([ValidateSet('r1', 'r2')][string]$Attempt = 'r1')
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$sourceRoot = Join-Path $repoRoot 'services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance'
$sourcePaths = @(
    (Join-Path $sourceRoot 'report/teacher/TeacherStatsDocxRenderer.java'),
    (Join-Path $sourceRoot 'report/teacher/TeacherStatsExportModel.java'),
    (Join-Path $sourceRoot 'exception/ReportExportUnavailableException.java'),
    (Join-Path $PSScriptRoot 'TeacherStatsSamples.java')
)
$springJar = 'C:/Users/maksd/.gradle/caches/modules-2/files-2.1/org.springframework/spring-context/6.2.19/e218e4c4ecad1e821905628b2c4ce8561d937baa/spring-context-6.2.19.jar'
$javacPath = 'C:/Users/maksd/.jdks/ms-21.0.10/bin/javac.exe'
$javaPath = 'C:/Users/maksd/.jdks/ms-21.0.10/bin/java.exe'
$classesPath = Join-Path $PSScriptRoot 'classes'
$samplesPath = Join-Path $PSScriptRoot 'samples'
New-Item -ItemType Directory -Force -Path $classesPath,$samplesPath | Out-Null
$startedAt = (Get-Date).ToString('o')
$compileCommand = 'javac -encoding UTF-8 --release 21 -cp <pinned cached spring-context> -d <own/classes> <exact 3 product sources> <own TeacherStatsSamples.java>'
& $javacPath -encoding UTF-8 --release 21 -cp $springJar -d $classesPath @sourcePaths *> (Join-Path $PSScriptRoot "compile-$Attempt.log")
$compileExit = $LASTEXITCODE
$sampleExit = $null
if ($compileExit -eq 0) {
    & $javaPath -cp "$classesPath;$springJar" TeacherStatsSamples $samplesPath *> (Join-Path $PSScriptRoot "samples-$Attempt.log")
    $sampleExit = $LASTEXITCODE
}
$result = [ordered]@{
    baseline = (git -C $repoRoot rev-parse HEAD).Trim()
    startedAt = $startedAt
    finishedAt = (Get-Date).ToString('o')
    compileExit = $compileExit
    sampleExit = $sampleExit
    compileCommand = $compileCommand
    sampleCommand = 'java -cp <own/classes;pinned cached spring-context> TeacherStatsSamples <own/samples>'
    javac = $javacPath
    java = $javaPath
    classpath = $springJar
    inputs = @(@($sourcePaths) + @($springJar) | ForEach-Object { [ordered]@{ path = $_; sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant() } })
}
$result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $PSScriptRoot "samples-$Attempt-exit.json") -Encoding utf8
Get-Content -LiteralPath (Join-Path $PSScriptRoot "compile-$Attempt.log")
if ($null -ne $sampleExit) { Get-Content -LiteralPath (Join-Path $PSScriptRoot "samples-$Attempt.log") }
if ($compileExit -ne 0) { exit $compileExit }
exit $sampleExit
