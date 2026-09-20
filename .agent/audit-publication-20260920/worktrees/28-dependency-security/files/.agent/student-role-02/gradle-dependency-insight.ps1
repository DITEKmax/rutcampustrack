$ErrorActionPreference = 'Continue'
$evidenceRoot = Join-Path (Get-Location) '.agent/student-role-02/runtime/dependency-insight'
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null

$checks = @(
    @{ name = 'spring-boot'; project = ':services:academic-service:academic-app'; dependency = 'org.springframework.boot:spring-boot' },
    @{ name = 'spring-data-commons'; project = ':services:academic-service:academic-app'; dependency = 'org.springframework.data:spring-data-commons' },
    @{ name = 'spring-hateoas'; project = ':services:academic-service:academic-app'; dependency = 'org.springframework.hateoas:spring-hateoas' },
    @{ name = 'spring-security-core'; project = ':services:auth-service:auth-app'; dependency = 'org.springframework.security:spring-security-core' },
    @{ name = 'spring-security-crypto'; project = ':services:auth-service:auth-app'; dependency = 'org.springframework.security:spring-security-crypto' },
    @{ name = 'spring-core'; project = ':services:academic-service:academic-app'; dependency = 'org.springframework:spring-core' },
    @{ name = 'spring-expression'; project = ':services:academic-service:academic-app'; dependency = 'org.springframework:spring-expression' },
    @{ name = 'spring-webmvc'; project = ':services:academic-service:academic-app'; dependency = 'org.springframework:spring-webmvc' },
    @{ name = 'jackson-core'; project = ':services:academic-service:academic-app'; dependency = 'com.fasterxml.jackson.core:jackson-core' },
    @{ name = 'jackson-databind'; project = ':services:academic-service:academic-app'; dependency = 'com.fasterxml.jackson.core:jackson-databind' },
    @{ name = 'protobuf-java'; project = ':services:academic-service:academic-app'; dependency = 'com.google.protobuf:protobuf-java' },
    @{ name = 'amqp-client'; project = ':services:academic-service:academic-app'; dependency = 'com.rabbitmq:amqp-client' },
    @{ name = 'grpc-netty'; project = ':services:academic-service:academic-app'; dependency = 'io.grpc:grpc-netty' },
    @{ name = 'grpc-netty-shaded'; project = ':services:academic-service:academic-app'; dependency = 'io.grpc:grpc-netty-shaded' },
    @{ name = 'micrometer-core'; project = ':services:api-gateway'; dependency = 'io.micrometer:micrometer-core' },
    @{ name = 'netty-codec'; project = ':services:api-gateway'; dependency = 'io.netty:netty-codec' },
    @{ name = 'netty-handler'; project = ':services:api-gateway'; dependency = 'io.netty:netty-handler' },
    @{ name = 'json-smart'; project = ':services:api-gateway'; dependency = 'net.minidev:json-smart' },
    @{ name = 'tomcat-embed-core'; project = ':services:academic-service:academic-app'; dependency = 'org.apache.tomcat.embed:tomcat-embed-core' },
    @{ name = 'postgresql'; project = ':services:academic-service:academic-app'; dependency = 'org.postgresql:postgresql' }
)

$results = @()
foreach ($check in $checks) {
    $safeName = $check.name -replace '[^a-zA-Z0-9.-]', '_'
    $outputFile = Join-Path $evidenceRoot "$safeName.txt"
    $arguments = @(
        "$($check.project):dependencyInsight",
        '--configuration', 'runtimeClasspath',
        '--dependency', $check.dependency,
        '--no-daemon', '--console=plain'
    )
    & .\gradlew.bat @arguments *> $outputFile
    $exitCode = $LASTEXITCODE
    $results += [ordered]@{
        name = $check.name
        project = $check.project
        dependency = $check.dependency
        command = ".\gradlew.bat $($arguments -join ' ')"
        exitCode = $exitCode
        output = ".agent/student-role-02/runtime/dependency-insight/$safeName.txt"
    }
}

$results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidenceRoot 'results.json') -Encoding utf8
if (($results | Where-Object { $_.exitCode -ne 0 }).Count -gt 0) { exit 1 }
exit 0
