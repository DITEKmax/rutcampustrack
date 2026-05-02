rootProject.name = "rutcampustrack"

// === Shared modules (M01 — foundations для всех сервисов) ===
// M11 G0: shared-web split на shared-web-api (типы) + shared-web (Spring Boot starter beans).
include("services:shared:shared-web-api")
include("services:shared:shared-web")
include("services:shared:shared-events")
include("services:shared:shared-logback")
include("services:shared:shared-test-containers")

// === M02 shared modules ===
include("services:shared:shared-outbox")

// === M03a shared modules ===
include("services:shared:shared-security")

// === M04 shared modules ===
include("services:shared:shared-observability")

// === API Contracts (чистые модули без Spring Boot) ===
include("services:auth-service:auth-api-contract")
include("services:academic-service:academic-api-contract")
include("services:schedule-service:schedule-api-contract")
include("services:attendance-service:attendance-api-contract")

// === Spring Boot Applications ===
include("services:api-gateway")
include("services:auth-service:auth-app")
include("services:academic-service:academic-app")
include("services:schedule-service:schedule-app")
include("services:attendance-service:attendance-app")
include("services:document-renderer-service:document-renderer-app")
include("services:notification-service:notification-api-contract")
include("services:notification-service:notification-app")
