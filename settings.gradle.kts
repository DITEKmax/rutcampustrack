rootProject.name = "rutcampustrack"

// === Shared modules (M01 — foundations для всех сервисов) ===
include("services:shared:shared-web")
include("services:shared:shared-events")
include("services:shared:shared-logback")
include("services:shared:shared-test-containers")

// === API Contracts (чистые модули без Spring Boot) ===
include("services:academic-service:academic-api-contract")
include("services:schedule-service:schedule-api-contract")
include("services:attendance-service:attendance-api-contract")

// === Spring Boot Applications ===
include("services:api-gateway")
include("services:auth-service")
include("services:academic-service:academic-app")
include("services:schedule-service:schedule-app")
include("services:attendance-service:attendance-app")
include("services:notification-service:notification-api-contract")
include("services:notification-service:notification-app")
