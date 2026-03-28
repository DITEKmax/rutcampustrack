# Coding Conventions

**Analysis Date:** 2026-03-28

## Naming Patterns

**Packages:**
- Root: `ru.rutcampustrack.{service}`
- Sub-packages by domain: `ru.rutcampustrack.{service}.{module}` (e.g., `ru.rutcampustrack.attendance.checkin`, `ru.rutcampustrack.attendance.report`)
- Contract modules: `ru.rutcampustrack.{service}.contract.enums`, `ru.rutcampustrack.{service}.contract.exception`
- Config: `ru.rutcampustrack.{service}.config`
- gRPC: `ru.rutcampustrack.{service}.grpc`

**Classes:**
- Application entry points: `{Service}Application` (e.g., `AcademicApplication`, `GatewayApplication`)
- Enums: PascalCase noun (e.g., `UserRole`, `AttendanceStatus`, `LessonStatus`)
- Exceptions: `{Description}Exception` (e.g., `ResourceNotFoundException`)
- Error responses: `ErrorResponse` (record)
- JPA converters: `{EnumName}Converter` nested inside `EnumConverters` class
- Abstract base converter: `LowercaseEnumConverter<E>`

**Enum Values:**
- In Java: `UPPER_SNAKE_CASE` (e.g., `UserRole.ADMIN`, `AttendanceStatus.FREE_ATTENDANCE`)
- In PostgreSQL: lowercase strings (e.g., `'admin'`, `'free_attendance'`)
- Conversion handled by `LowercaseEnumConverter` with `@Converter(autoApply = true)`
- NEVER use `@Enumerated(EnumType.ORDINAL)` -- only string-based conversion

**Database Columns:**
- `snake_case` for all column names (e.g., `display_name`, `telegram_id`, `is_headman`)
- Primary keys: `id` (BIGSERIAL)
- Timestamps: `created_at`, `updated_at`, `closed_at` (TIMESTAMPTZ)
- Booleans: `is_` prefix (e.g., `is_active`, `is_headman`, `is_geo_blocked`)
- Foreign keys: `{entity}_id` (e.g., `group_id`, `teacher_id`, `semester_id`)

**REST Endpoints:**
- Pattern: `/api/{service}/...` (routed through Gateway at port 8080)
- Gateway strips `/api` prefix before forwarding

**gRPC Services:**
- Proto package: `rutcampustrack.{service}`
- Java package: `ru.rutcampustrack.{service}.grpc`
- Service name: `{Service}GrpcService` (e.g., `AcademicGrpcService`, `ScheduleGrpcService`)
- RPC methods: PascalCase verbs (e.g., `GetGroup`, `GetActiveLesson`, `IsHeadman`)
- Messages: PascalCase (e.g., `GroupRequest`, `LessonResponse`)
- Proto fields: `snake_case` (e.g., `group_id`, `display_name`, `is_headman`)

**Event Types:**
- Pattern: `{domain}.{action}` in lowercase (e.g., `lesson.started`, `attendance.marked`, `excuse.requested`)
- Envelope: `event_type`, `event_id` (UUID), `occurred_at` (ISO-8601), `payload`

**Files:**
- Java source: PascalCase matching class name
- SQL migrations: `V{N}__{description}.sql` (Flyway convention, double underscore)
- Proto files: `{service}.proto` (lowercase)
- Event schemas: `{event_type_with_dots}.json` (e.g., `lesson.started.json`)
- YAML config: `application.yml` (never `.properties`)

## Code Style

**Formatting:**
- No dedicated formatter tool detected (no Checkstyle, Spotless, Prettier, or EditorConfig)
- Java compiler flag: `-parameters` enabled for all subprojects (preserves parameter names at runtime)
- Encoding: UTF-8 enforced globally

**Linting:**
- No static analysis tools detected (no Checkstyle, PMD, SpotBugs, or SonarQube config)

## Module Architecture (Contract-First)

**Contract modules (`*-api-contract`):**
- Gradle plugin: `java-library` (NOT Spring Boot)
- Contains: enums, DTOs, exception classes, REST controller interfaces, OpenAPI annotations
- Dependencies: `jakarta.validation-api`, `spring-web`, `spring-hateoas`, `swagger-annotations-jakarta`, `jackson-annotations`
- NO Lombok allowed in contracts
- Request DTOs: Java `record`
- Response DTOs: classes extending `RepresentationModel` (for HATEOAS)
- REST mappings (`@GetMapping`, `@PostMapping`, etc.) declared ONLY in contract interfaces

**App modules (`*-app`):**
- Gradle plugin: `java` + `org.springframework.boot` + `io.spring.dependency-management`
- Depends on: own `*-api-contract` module via `implementation(project(...))`
- Controllers: `implements` the contract interface
- Lombok: allowed for entities and internal classes (`compileOnly` + `annotationProcessor`)

## DTO Patterns

**Error Response (RFC 7807 Problem Details):**
```java
// File: services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/exception/ErrorResponse.java
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "...")
public record ErrorResponse(
    int status,
    String type,      // URI like "https://api.rutcampustrack.ru/problems/resource-not-found"
    String title,
    String detail,
    String instance,   // Request URI
    Instant timestamp,
    List<FieldError> fieldErrors
) {
    public record FieldError(String field, Object rejectedValue, String message) {}
}
```

**Custom Exceptions:**
```java
// File: services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/exception/ResourceNotFoundException.java
public class ResourceNotFoundException extends RuntimeException {
    private final String resourceName;
    private final String fieldName;
    private final Object fieldValue;
    // Constructor formats message: "{resource} с {field}={value} не найден"
    // Manual getters (no Lombok in contracts)
}
```

## Enum Conversion Pattern

**Abstract base converter:**
```java
// File: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/LowercaseEnumConverter.java
public abstract class LowercaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {
    // convertToDatabaseColumn: attribute.name().toLowerCase()
    // convertToEntityAttribute: Enum.valueOf(enumClass, dbData.toUpperCase())
}
```

**Concrete converters (Academic Service pattern -- preferred):**
```java
// File: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/EnumConverters.java
public class EnumConverters {
    @Converter(autoApply = true)
    public static class UserRoleConverter extends LowercaseEnumConverter<UserRole> {
        public UserRoleConverter() { super(UserRole.class); }
    }
    // Repeat for each enum...
}
```

**Note:** Schedule Service has a slightly different pattern -- inline `AttributeConverter` implementations instead of extending `LowercaseEnumConverter`. The Academic Service pattern (using the abstract base class) is preferred. New services should use the `LowercaseEnumConverter` approach.

## Error Handling

**Strategy:** `@ControllerAdvice` with centralized `GlobalExceptionHandler`
- Controllers only throw exceptions (e.g., `ResourceNotFoundException`)
- The handler catches exceptions and builds `ErrorResponse` records
- All errors follow RFC 7807 Problem Details format
- Validation errors (400) include `fieldErrors` array

**Pattern:** Controller throws -> ControllerAdvice catches -> returns ErrorResponse

## REST API Conventions

**HATEOAS Level 3:**
- Single resource: `EntityModel<T>` with `_links`
- Collection: `PagedModel<EntityModel<T>>` with pagination `_links`

**HTTP Methods:**
- PUT = full resource replacement (requires all fields)
- PATCH = partial update (separate DTO with nullable fields)

**OpenAPI:**
- Annotations (`@Operation`, `@ApiResponse`, `@Schema`) in contract interfaces, not in app controllers
- SpringDoc generates Swagger UI at `/swagger-ui.html` and OpenAPI spec at `/api-docs`

## Database Conventions

**PostgreSQL:**
- All string values stored in lowercase
- Custom ENUM types created via `CREATE TYPE` in migrations (e.g., `CREATE TYPE user_role AS ENUM ('admin', 'teacher', 'student')`)
- Primary keys: `BIGSERIAL` (maps to Java `Long`)
- Timestamps: `TIMESTAMPTZ` (always UTC)
- Soft delete: `status = 'archived'` for users -- never `DELETE`
- Hibernate: `ddl-auto: validate` (schema managed exclusively by Flyway)
- `open-in-view: false` (no lazy loading in view layer)

**MongoDB (Attendance Service):**
- No Flyway (schemaless)
- Uses `spring-boot-starter-data-mongodb`

**Indexes:**
- Named with `idx_{table}_{column}` pattern (e.g., `idx_users_role`, `idx_lessons_date`)
- Partial indexes used for active records (e.g., `WHERE status IN ('planned', 'active')`)
- Unique constraints on natural keys

**Migrations:**
- Path: `src/main/resources/db/migration/V{N}__{description}.sql`
- Current: `V1__baseline.sql` for academic and schedule services
- Each service has its own database and migrations

## Configuration Conventions

**Property Sources:**
- All config in `application.yml` (not `.properties`)
- Secrets via environment variables with dev defaults: `${VAR_NAME:default_value}`
- Example: `${POSTGRES_ACADEMIC_PASSWORD:rct_dev_pass}`

**Server Ports:**
- api-gateway: 8080
- auth-service: 9090
- academic-service: 9091
- schedule-service: 9092
- attendance-service: 9093
- notification-web: 9094

**Spring Application Names:**
- Pattern: `{service-name}` in kebab-case (e.g., `academic-service`, `auth-service`)

**Logging:**
- `ru.rutcampustrack: DEBUG` for all services
- Spring framework: `INFO` or default

**SpringDoc:**
- Consistent across all REST services: `/api-docs` and `/swagger-ui.html`

## Documentation Patterns

**Javadoc:**
- Used on public classes and interfaces in contract modules (in Russian)
- Includes `@code` and `@literal` for type parameters in examples
- Example format:
```java
/**
 * Универсальный JPA конвертер: Java Enum ↔ lowercase строка в PostgreSQL.
 * <p>
 * Использование: создать конкретный подкласс для каждого enum.
 * <pre>
 * {@literal @}Converter(autoApply = true)
 * public class UserRoleConverter extends LowercaseEnumConverter{@literal <}UserRole{@literal >} { ... }
 * </pre>
 */
```

**OpenAPI / Swagger:**
- `@Schema(description = "...")` on DTO fields with Russian descriptions
- `@Schema(example = "...")` for example values
- Place annotations in contract modules, not app modules

**SQL Comments:**
- Flyway migrations include descriptive comments at the top: `-- V1__baseline.sql` and `-- {Service} — начальная схема`
- Inline comments for table purposes

**Event Schemas:**
- JSON Schema (draft 2020-12) in `event-schemas/` directory
- Include `title`, `description`, required fields, and type constraints

## Import Organization

**No enforced order** (no import sorting tool detected). Follow this recommended order:
1. `java.*` / `jakarta.*`
2. Third-party libraries (`com.*`, `io.*`, `net.*`)
3. Spring framework (`org.springframework.*`)
4. Project imports (`ru.rutcampustrack.*`)

**Path Aliases:**
- Gradle project references: `project(":services:{service}:{module}")` (e.g., `project(":services:academic-service:academic-api-contract")`)

## Application Bootstrap Pattern

All Spring Boot applications follow an identical minimal pattern:
```java
@SpringBootApplication
public class {Service}Application {
    public static void main(String[] args) {
        SpringApplication.run({Service}Application.class, args);
    }
}
```

---

*Convention analysis: 2026-03-28*
