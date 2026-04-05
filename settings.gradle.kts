rootProject.name = "rutcampustrack"

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
