$ErrorActionPreference='Stop'
$runner='C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/runner.ps1'
$tokens=$null;$parseErrors=$null;$ast=[System.Management.Automation.Language.Parser]::ParseFile($runner,[ref]$tokens,[ref]$parseErrors)
if($parseErrors.Count){throw 'Existing runner syntax failure'}
foreach($name in @('Assert-That','Get-JsonPropertyValue','Assert-CanonicalPathList','Convert-TrustedManifestToCanonicalObject')){
 $definitions=@($ast.FindAll({param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name},$true))
 if($definitions.Count -ne 1){throw "One exact existing canonical utility required: $name"}
 . ([scriptblock]::Create($definitions[0].Extent.Text))
}
$path="$PSScriptRoot/build-manifest.json"
$manifest=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json -Depth 20 -DateKind String
Assert-CanonicalPathList -Items @($manifest.artifacts.jars) -Context 'jars'
Assert-CanonicalPathList -Items @($manifest.artifacts.pwaDist.files) -Context 'pwa'
$canonical=Convert-TrustedManifestToCanonicalObject -Manifest $manifest|ConvertTo-Json -Depth 20 -Compress
[IO.File]::WriteAllText($path,$canonical,[Text.UTF8Encoding]::new($false))
$roundtrip=$canonical|ConvertFrom-Json -Depth 20 -DateKind String
$expected=Convert-TrustedManifestToCanonicalObject -Manifest $roundtrip|ConvertTo-Json -Depth 20 -Compress
Assert-That ($canonical -ceq $expected) 'Existing exact canonical function roundtrip differs'
$result=[ordered]@{existingUtilitySource=$runner;sourceSha256=(Get-FileHash -LiteralPath $runner).Hash.ToLowerInvariant();canonicalBytesMatch=$true;manifestSha256=(Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()}
[IO.File]::WriteAllText("$PSScriptRoot/canonical-verification.json",($result|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
$result|ConvertTo-Json -Compress
