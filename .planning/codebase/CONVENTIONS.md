# Coding Conventions

**Analysis Date:** 2026-04-08

## Naming Patterns

**Files:**
- Java: `PascalCase.java` (classes, interfaces, enums). Example: `CheckinService.java`, `AttendanceStatus.java`
- Java test files: `*Test.java` or `*IntegrationTest.java`. Examples: `MarkingServiceTest.java`, `MarkingIntegrationTest.java`
- React/TypeScript: `PascalCase.tsx` for components, `camelCase.ts` for utilities/APIs. Examples: `AuthProvider.tsx`, `useStompCheckin.ts`, `api.ts`
- React test files: `__tests__/ComponentName.test.tsx`. Example: `frontends/pwa/src/features/auth/__tests__/AuthProvider.test.tsx`
- Angular: `kebab-case.component.ts`, `kebab-case.service.ts`, `kebab-case.spec.ts`. Examples: `journal-page.component.ts`, `journal-api.service.ts`, `journal-page.component.spec.ts`
- Angular test files: `*.spec.ts` co-located with source. Example: `auth.service.spec.ts` next to `auth.service.ts`

**Functions:**
- Java: `camelCase()`. Examples: `markAttendance()`, `handleGeofenceViolation()`, `getGroupMembers()`
- React hooks: `useXxx()` pattern (not decorators). Examples: `useAuth()`, `useStompCheckin()`
- Angular services: `camelCase()` methods. Example: `setTokens()`, `currentUser()` (computed signal)

**Variables:**
- Java: `camelCase`. Examples: `lessonId`, `headmanUserId`, `mongoTemplate`
- TypeScript/JavaScript: `camelCase`. Examples: `accessToken`, `isAuthenticated`, `selectedGroupId`
- Java constants: `UPPER_CASE_WITH_UNDERSCORES`. Example: `LESSON_ID = 42L` (in test data)

**Enums:**
- **Java:** `UPPER_CASE` naming in enum values. Example: `AttendanceStatus.PRESENT`, `AttendanceStatus.ABSENT`, `UserRole.ADMIN`
- **Database (PostgreSQL):** Stored as **lowercase strings** (`'present'`, `'absent'`, `'admin'`)
- **Conversion:** Via `LowercaseEnumConverter<E>` abstract class with concrete subclasses per enum, decorated with `@Converter(autoApply=true)`. File location: `services/*/src/main/java/ru/rutcampustrack/*/config/LowercaseEnumConverter.java`
- **Never use** `@Enumerated(EnumType.ORDINAL)` — always use string representation

**Types (TypeScript):**
- `PascalCase` for interfaces and type aliases. Examples: `AuthUser`, `LoginRequest`, `AuthContextValue`

## Code Style

**Formatting:**
- Java: UTF-8 encoding, 4-space indentation (configured in root `build.gradle.kts`)
- TypeScript/JavaScript: 2-space indentation (Tailwind CSS convention in `frontends/*/`)
- No explicit Spotless/Prettier config — IntelliJ defaults apply; follow existing code style in each module

**Linting:**
- Java: No explicit ESLint/Checkstyle rules detected; rely on IntelliJ inspections
- TypeScript: Vitest via VSCode lint-on-save

## Import Organization

**Order (Java):**
1. `java.*` imports
2. `jakarta.*` imports (Spring Boot 3.x)
3. `org.springframework.*` and `org.springframework.hateoas.*`
4. Third-party imports (`com.`, `net.devh.`, etc.)
5. Project imports (`ru.rutcampustrack.*`)
6. Static imports (if any)

**Order (TypeScript/React):**
1. React/framework imports (`react`, `@angular/...`, `vitest`)
2. Third-party UI/animation libraries (`motion/react`, `@phosphor-icons/react`, `@tanstack/react-query`)
3. Shared utilities (`@/shared/lib/...`)
4. Feature-specific imports
5. Types/interfaces (may be inline)

**Path Aliases:**
- React/PWA: `@/` points to `src/` (configured in `vitest.config.ts`)
- Angular web-panel: Not explicitly aliased; uses relative imports

## Error Handling

**Java Patterns:**
- Controllers **only throw exceptions**; never catch and return error responses
- `@RestControllerAdvice` class (`GlobalExceptionHandler`) catches exceptions and maps to RFC 7807 responses
- Custom exceptions extend either `RuntimeException` or are checked exceptions with descriptive names
  - Examples: `ResourceNotFoundException`, `AccessDeniedException`, `BadRequestException`, `ConflictException`, `GeofenceViolationException`, `GeofenceBlockedException`, `RateLimitException`
- File location: `services/*/src/main/java/ru/rutcampustrack/*/exception/GlobalExceptionHandler.java`

**Error Response Format (RFC 7807):**
- **Record class** in contract module: `ErrorResponse` with fields: `status`, `type`, `title`, `detail`, `instance`, `timestamp`, `fieldErrors`
- Example: `services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/exception/ErrorResponse.java`
- Centralized via `GlobalExceptionHandler` — all error responses conform to this shape

**TypeScript/React:**
- No global error handler detected; component-level error states (e.g., `error` signal in React, `error` signal in Angular)
- Example: `AuthProvider` tracks `isAuthenticated`, `user`, `accessToken` states; throws on `login()`/`logout()` failure

## Logging

**Framework:** Spring Boot default (SLF4J + Logback) in Java services; `console.log/console.error` in frontends

**Patterns:**
- No explicit logging convention detected; follow contextual approach — log at service boundaries, not in every method

## Comments

**When to Comment:**
- Class-level JavaDoc for public classes and interfaces (observed in contract modules)
- Method-level JavaDoc for public APIs
- Inline comments for non-obvious logic

**JSDoc/TSDoc:**
- React components: No JSDoc observed; code is self-documenting via TypeScript types
- Angular services: No JSDoc observed; clear method names + types suffice
- Example from codebase: `AuthUser` interface clearly documents shape

## Function Design

**Size:** Keep functions under 50 lines (heuristic); longer functions should be refactored

**Parameters:**
- Java: Use domain objects (records for requests, domain entities for internal calls). Example: `MarkRequest` record instead of raw parameters
- TypeScript: Use object parameters with interfaces. Example: `LoginRequest` type, `AuthContextValue` type

**Return Values:**
- Java: Use domain objects (`ResponseEntity<EntityModel<T>>` for REST, bare `T` for services)
- TypeScript: Promise-based async functions, signals for reactive state (Angular)

## Module Design

**Exports:**
- Java contract modules: Export only interfaces, records (request DTOs), class (response DTOs), enums, and exceptions
- Angular services: `providedIn: 'root'` for singleton services
- React hooks: Exported as named exports, not default exports

**Barrel Files:**
- React: No barrel files (`index.ts`) detected; imports are direct
- Angular: No barrel files detected; imports are direct (feature modules organize via standalone components)

## Contract-First Pattern (CRITICAL)

**Structure:**
Each service is split into two modules:
- `*-api-contract` — pure Java library (no Spring Boot)
- `*-app` — Spring Boot application

**Location examples:**
- `services/attendance-service/attendance-api-contract/` — contracts only
- `services/attendance-service/attendance-app/` — implementation

**Request DTOs:**
- **Type:** Java `record`
- **Location:** `attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/checkin/CheckinRequest.java`
- **Example:**
  ```java
  public record CheckinRequest(
    @NotNull
    @DecimalMin(value = "-90")
    @DecimalMax(value = "90")
    Double lat,
    
    @NotNull
    @DecimalMin(value = "-180")
    @DecimalMax(value = "180")
    Double lng
  ) {}
  ```
- **No Lombok** in contract modules

**Response DTOs:**
- **Type:** Plain Java class extending `RepresentationModel<T>` (HATEOAS)
- **Location:** `attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/checkin/CheckinResponse.java`
- **Example:**
  ```java
  public class CheckinResponse extends RepresentationModel<CheckinResponse> {
    private AttendanceStatus status;
    private Long lessonId;
    private Instant timestamp;
    
    public CheckinResponse() {}
    
    public CheckinResponse(AttendanceStatus status, Long lessonId, Instant timestamp) {
      this.status = status;
      this.lessonId = lessonId;
      this.timestamp = timestamp;
    }
    
    // Getters and setters (NO Lombok)
  }
  ```
- **No Lombok** in contract modules — hand-write getters/setters
- Lombok **allowed** only in `*-app` module (entities, internal classes)

**API Interfaces:**
- **Location:** `attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/CheckinApi.java`
- **Pattern:** Controller implements interface; all request mappings declared in interface
- **Example:**
  ```java
  @Tag(name = "Checkin", description = "Геоотметка студентов")
  @RequestMapping("/attendance")
  public interface CheckinApi {
    @Operation(summary = "...", description = "...")
    @ApiResponses({
      @ApiResponse(responseCode = "201", description = "..."),
      @ApiResponse(responseCode = "422", description = "...")
    })
    @PostMapping("/checkin")
    ResponseEntity<EntityModel<CheckinResponse>> checkin(@Valid @RequestBody CheckinRequest request);
  }
  ```
- **Swagger annotations in interface only:** `@Operation`, `@ApiResponse`, `@ApiResponses`, `@Tag`
- **Controller implements this interface** — no additional mappings

## REST API Conventions

**HATEOAS Level 3:**
- Response bodies wrap domain objects in `EntityModel<T>` or `PagedModel<EntityModel<T>>`
- Auto-generated `_links.self` via Spring HATEOAS
- Example response structure:
  ```json
  {
    "status": "PRESENT",
    "lessonId": 42,
    "userId": 99,
    "timestamp": "2026-04-01T10:30:00Z",
    "_links": {
      "self": {
        "href": "/api/attendance/checkins/99"
      }
    }
  }
  ```

**HTTP Methods:**
- `PUT` — full resource replacement (all fields required)
- `PATCH` — partial update (separate DTO with optional fields)
- `POST` — create new resource
- `GET` — fetch resource(s)
- `DELETE` — soft delete for users (set status='archived'), hard delete for transient records

**HTTP Status Codes:**
- `200 OK` — successful GET, PUT, PATCH
- `201 Created` — successful POST
- `204 No Content` — successful DELETE
- `400 Bad Request` — validation error
- `401 Unauthorized` — missing/invalid auth
- `403 Forbidden` — insufficient permissions
- `404 Not Found` — resource doesn't exist
- `409 Conflict` — duplicate key, business rule violation
- `422 Unprocessable Entity` — geofence violation, rate limit
- `429 Too Many Requests` — rate limit exceeded
- `500 Internal Server Error` — unexpected error

## Package Structure Conventions

**Java package naming:**
- `ru.rutcampustrack.{service}.{module}`
- Example: `ru.rutcampustrack.attendance.checkin`, `ru.rutcampustrack.attendance.report`

**REST endpoint paths:**
- `/api/{service}/...` through API Gateway
- Example: `/api/attendance/checkin`, `/api/attendance/lessons/{lessonId}/students/{userId}`

**gRPC service naming:**
- Package: `ru.rutcampustrack.{service}.grpc`
- Proto file location: `proto/rutcampustrack/{service}.proto`

**Event type naming:**
- Format: `{domain}.{action}`
- Examples: `lesson.started`, `attendance.marked`, `excuse.created`
- Published to RabbitMQ fanout exchange with routing key `event.{domain}.{action}`

## Domain Isolation Pattern (Attendance Service example)

**Structure:**
- `attendance/checkin/` — geofence checkin, marking, rate limiting
- `attendance/report/` — journal, statistics
- `attendance/shared/port/` — shared interfaces for cross-domain access

**Rule:**
- `report/` domain **NEVER imports directly from** `checkin/` domain
- Cross-domain access goes **only through** `shared/port/AttendanceReadPort` interface
- Enforced via ArchUnit test: `ReportDomainIsolationTest`
- File: `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/ReportDomainIsolationTest.java`

**Example ArchUnit test:**
```java
@AnalyzeClasses(packages = "ru.rutcampustrack.attendance")
class ReportDomainIsolationTest {
  @ArchTest
  static final ArchRule reportDoesNotImportCheckin =
    noClasses()
      .that().resideInAPackage("ru.rutcampustrack.attendance.report..")
      .should().dependOnClassesThat()
      .resideInAPackage("ru.rutcampustrack.attendance.checkin..");
}
```

## Database Conventions

**Enums in PostgreSQL:**
- **Storage:** lowercase strings (e.g., `'present'`, `'admin'`)
- **Conversion:** `LowercaseEnumConverter<E>` with `@Converter(autoApply=true)`
- **Never use:** `@Enumerated(EnumType.ORDINAL)`

**Migrations:**
- **Tool:** Flyway
- **Naming:** `V{N}__description.sql`. Examples: `V1__initial_schema.sql`, `V2__add_attendance_table.sql`
- **Location:** `services/*/src/main/resources/db/migration/`

**DDL Auto:**
- **Setting:** `ddl-auto: validate` in all services
- **Meaning:** Hibernate validates schema against entities; does NOT create/modify schema
- **Schema creation is via Flyway only**

**Soft Delete:**
- **Pattern:** `status='archived'` for users (never hard DELETE)
- **Column:** `status` (enum: `'active'`, `'archived'`)
- **In queries:** Always filter `status != 'archived'`

**Primary Keys:**
- **Type:** `BIGSERIAL` in PostgreSQL (Long in Java)
- **Example:** `user_id BIGSERIAL PRIMARY KEY`

**Timestamps:**
- **Type:** `TIMESTAMPTZ` (timezone-aware)
- **Semantics:** Always UTC
- **Example:** `created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP`

## Frontend Conventions

### Icons (Phosphor Icons)

**Package selection:**
- React (PWA, Mini App): `@phosphor-icons/react`
- Angular (web-panel): `@phosphor-icons/web` (web-components)

**Weight convention by context:**

| Context | Weight | Reasoning |
|---------|--------|-----------|
| Mobile (Mini App, PWA) | `bold`, `fill` | Larger, clearer on small screens |
| Desktop (web-panel) | `regular`, `light` | Elegant, not heavy |
| Dashboard cards, stats | `duotone` | Two-tone accent |
| Active/selected | `fill` | Emphasis |
| Inactive/disabled | `regular` | De-emphasis |

**Sizing:**
- Inline (tables, lists): 20px
- Navigation, buttons: 24px
- Hero sections, dashboard: 32px

**Color:**
- Default: `currentColor` (inherits from text)
- Status colors:
  - `present` / success → green
  - `absent` / error → red
  - `excused` / warning → yellow/orange
  - `pending` → gray

### Animations

**By platform:**

| Platform | Library | Usage |
|----------|---------|-------|
| React (PWA, Mini App) | `motion` (framer-motion) | Declarative animations, gestures, `AnimatePresence` for screen transitions, layout for lists |
| Angular (web-panel) | `@angular/animations` | Route transitions, subtle UI feedback |
| Landing | GSAP + ScrollTrigger | Scroll-driven animations, timeline sequences, parallax |

**Principle:** Animations must be **functional** (guide attention, show state changes, provide feedback), not purely decorative.

### React Component Structure

**File organization:**
- Feature-level: `src/features/{featureName}/`
- Components: `PascalCase.tsx`
- Hooks: `useXxx.ts`
- APIs: `api.ts`
- Types: `types.ts`
- Tests: `__tests__/{ComponentName}.test.tsx`

**State management:**
- Context API for shared auth state (example: `AuthProvider` in PWA)
- TanStack Query for server state (async data fetching)
- React hooks + signal-like patterns (useState, useCallback)

### Angular Component Structure

**File organization:**
- Feature folder: `src/app/features/{featureName}/`
- Component: `{name}/{name}.component.ts|html|spec.ts|css`
- Service: `{domain}/{name}.service.ts|spec.ts`
- Standalone components (no NgModule)

**State management:**
- Angular signals (computed, signal) for reactive state
- Dependency injection for services
- Material Design components + Tailwind for styling

---

*Convention analysis: 2026-04-08*
