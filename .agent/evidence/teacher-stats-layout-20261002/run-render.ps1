$ErrorActionPreference = 'Stop'
$image = 'sha256:2064a6a89efabc0356fd7d9d3d4a703bdba7ff834a444df42d29b84be27eacc2'
$name = 'rct-teacher-stats-20261002-r1'
$inputPath = (Resolve-Path (Join-Path $PSScriptRoot 'samples')).Path
$outputPath = Join-Path $PSScriptRoot 'render-r1'
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$command = @'
set -e
soffice -env:UserInstallation=file:///tmp/lo-teacher-stats --headless --convert-to pdf --outdir /out /input/teacher-stats-students.docx /input/teacher-stats-groups.docx
for scope in students groups; do
  pdftoppm -r 150 -png /out/teacher-stats-$scope.pdf /out/$scope-page
  pdftotext -layout /out/teacher-stats-$scope.pdf /out/$scope-layout.txt
  pdfinfo /out/teacher-stats-$scope.pdf > /out/$scope-pdfinfo.txt
done
'@
$startedAt = (Get-Date).ToString('o')
docker run --rm --name $name --label rct.owner=g_backend_acceptance_1002 --label rct.scope=teacher-stats-layout-20261002-r1 --network none --read-only --memory 512m --cpus 1 --tmpfs /tmp:size=256m --mount "type=bind,source=$inputPath,target=/input,readonly" --mount "type=bind,source=$outputPath,target=/out" --entrypoint sh $image -c $command *> (Join-Path $PSScriptRoot 'render-r1.log')
$renderExit = $LASTEXITCODE
[ordered]@{source=(git -C (Join-Path $PSScriptRoot '../../..') rev-parse HEAD).Trim();containerName=$name;image=$image;startedAt=$startedAt;finishedAt=(Get-Date).ToString('o');exitCode=$renderExit;command=$command;limits='--rm/no-network/read-only/512MiB/1CPU/tmpfs256MiB; own syntheticinput readonly';scopes=@('STUDENTS80','GROUPS60')} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'render-r1-exit.json') -Encoding utf8
Get-Content -LiteralPath (Join-Path $PSScriptRoot 'render-r1.log')
exit $renderExit
