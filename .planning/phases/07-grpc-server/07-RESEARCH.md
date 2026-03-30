# Phase 7: gRPC Server - Research

**Researched:** 2026-03-30
**Domain:** gRPC server implementation with grpc-spring-boot-starter, Gradle protobuf compilation
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** gRPC port 19091 (not 9090 — conflicts with Auth Service)
- **D-02:** `grpc-spring-boot-starter` library (pre-planned in Phase 0, commented out in build.gradle.kts)
- **D-03:** No JPA associations — repository queries with Long FK IDs
- **D-04:** Soft delete via `@SQLRestriction` on User — GetGroupMembers automatically filters archived
- **D-05:** Proto file `proto/academic.proto` is the source of truth — 7 RPCs, message types fixed

### Claude's Discretion
- Service reuse strategy: whether gRPC impl delegates to existing REST services or queries repositories directly
- Error mapping approach: interceptor vs per-method handling for domain exceptions to gRPC status codes
- Testing approach: grpc-spring-boot-starter test support vs raw channels
- Internal structure: single gRPC service class vs split

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| GRPC-01 | GetGroup returns group info by ID | `GroupRepository.findById()` exists; map to `GroupResponse` proto message |
| GRPC-02 | GetGroupMembers returns active students in a group | `UserRepository.findByGroupId()` + `@SQLRestriction` auto-filters archived; map to `GroupMembersResponse` |
| GRPC-03 | GetTeacherSubjects returns teacher's subjects with groups | `TeacherSubjectGroupRepository.findByTeacherIdAndSemesterId()` + `SubjectRepository`; need JOIN-like logic across two repos |
| GRPC-04 | IsHeadman checks if user is headman of a group | `UserRepository.findById()` — check `isHeadman && groupId == request.groupId` |
| GRPC-05 | GetActiveSemester returns current active semester | `SemesterRepository.findByIsActiveTrue()` — already used in `AssignmentService` |
| GRPC-06 | GetCampusGeofence returns campus coordinates and radius | `CampusSettingRepository.findById(1L)` — single-row table seeded in V2 |
| GRPC-07 | GetUserById returns user info | `UserRepository.findById()` — note: uses `@SQLRestriction`, does NOT find archived users |
</phase_requirements>

---

## Summary

Phase 7 adds a gRPC server facade to the existing Academic Service. The proto contract (`proto/academic.proto`) is already written and defines 7 RPCs. All data access is already implemented in Phase 6 repositories. This phase is purely infrastructure plumbing: Gradle protobuf plugin setup, gRPC service implementation class, exception-to-status mapping, and integration tests using the in-process server approach.

The key challenge is the Gradle build configuration: the proto file lives at the monorepo root (`proto/academic.proto`), not in `src/main/proto`, so the protobuf plugin `sourceSets` must point to the root `proto/` directory. The `grpc-server-spring-boot-starter` (server-only variant) is the correct dependency — no client needed in Academic Service.

The `RequestContext` bean is request-scoped and will NOT be available in gRPC calls (gRPC runs outside the Servlet container). Any gRPC implementation method that delegates to an existing service using `RequestContext` must bypass that dependency — query the repository directly or create a dedicated gRPC delegate that does not rely on `RequestContext`.

**Primary recommendation:** Implement `AcademicGrpcServiceImpl` as a single `@GrpcService` class that queries repositories directly (no delegation to REST services). Add a `@GrpcAdvice` class for centralized exception-to-status mapping. Test with grpc-spring-boot-starter in-process server mode inside `AbstractAcademicIntegrationTest`.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| net.devh:grpc-server-spring-boot-starter | 3.1.0.RELEASE | Embeds Netty gRPC server, registers `@GrpcService` beans, manages lifecycle | Pre-decided in Phase 0; already commented-out in build.gradle.kts |
| com.google.protobuf:protobuf-java | managed by grpc-bom 1.63.0 | Generated message classes | Transitive from grpc-server-spring-boot-starter |
| io.grpc:grpc-stub | managed by grpc-bom 1.63.0 | `StreamObserver` and stub APIs | Transitive from grpc-server-spring-boot-starter |
| com.google.protobuf (protobuf-gradle-plugin) | 0.9.4 | Gradle plugin: runs `protoc` and `protoc-gen-grpc-java` at build time | Standard Google plugin for Java gRPC codegen |
| com.google.protobuf:protoc | 3.25.x (current stable) | Protobuf compiler binary | Must match protobuf-java runtime version |
| io.grpc:protoc-gen-grpc-java | 1.63.0 | gRPC Java code generator (produces `*ImplBase` and stubs) | Must match grpc-stub version |

### Supporting (test only)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| io.grpc:grpc-testing | 1.63.0 (managed) | `StreamRecorder` for unit tests | Optional — integration tests use `@GrpcClient` approach instead |

**Installation (build.gradle.kts changes):**
```kotlin
// Gradle plugin block — add to existing plugins {}
id("com.google.protobuf") version "0.9.4"

// Dependencies — uncomment/add
implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")

// Protobuf codegen configuration
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.63.0"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                id("grpc") { }
            }
        }
    }
}

// Point plugin at root proto/ directory (NOT src/main/proto)
sourceSets {
    main {
        proto {
            srcDir(rootProject.file("proto"))
        }
    }
}
```

**Version verification:**
- `net.devh:grpc-server-spring-boot-starter` latest confirmed: `3.1.0.RELEASE` (Maven Central, April 2024)
- gRPC BOM bundled version: `1.63.0`
- protobuf-gradle-plugin latest confirmed: `0.9.4`

---

## Architecture Patterns

### Recommended Project Structure

```
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/
├── grpc/                          # new package — gRPC layer only
│   ├── AcademicGrpcServiceImpl.java    # @GrpcService — the single implementation class
│   └── GrpcExceptionAdvice.java        # @GrpcAdvice — domain exception → gRPC status mapping
├── [existing packages unchanged]
```

Generated stubs land in `build/generated/source/proto/main/`:
```
build/generated/source/proto/main/
├── java/ru/rutcampustrack/academic/grpc/   # protobuf message classes
└── grpc/ru/rutcampustrack/academic/grpc/   # AcademicGrpcServiceGrpc.java (ImplBase)
```

### Pattern 1: @GrpcService Implementation

Extend the generated `ImplBase`, annotate with `@GrpcService`, inject repositories directly (no REST services — avoids `RequestContext` contamination).

```java
// Source: grpc-ecosystem/grpc-spring documentation
@GrpcService
public class AcademicGrpcServiceImpl
        extends AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SemesterRepository semesterRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherSubjectGroupRepository assignmentRepository;
    private final CampusSettingRepository campusSettingRepository;

    // Constructor injection

    @Override
    public void getGroup(GroupRequest request,
                         StreamObserver<GroupResponse> responseObserver) {
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", request.getGroupId()));
        responseObserver.onNext(GroupResponse.newBuilder()
                .setId(group.getId())
                .setName(group.getName())
                .setCode(group.getCode())
                .setIsActive(group.isActive())
                .build());
        responseObserver.onCompleted();
    }
}
```

### Pattern 2: @GrpcAdvice Exception Handler

Centralized mapping of domain exceptions to gRPC Status codes. Mirrors the pattern of `GlobalExceptionHandler` for REST.

```java
// Source: grpc-ecosystem/grpc-spring exception handling documentation
@GrpcAdvice
public class GrpcExceptionAdvice {

    @GrpcExceptionHandler(ResourceNotFoundException.class)
    public Status handleNotFound(ResourceNotFoundException e) {
        return Status.NOT_FOUND.withDescription(e.getMessage()).withCause(e);
    }

    @GrpcExceptionHandler(Exception.class)
    public Status handleUnknown(Exception e) {
        return Status.INTERNAL.withDescription("Internal server error").withCause(e);
    }
}
```

### Pattern 3: Integration Test with In-Process Server

```java
// Source: grpc-ecosystem/grpc-spring testing documentation
@SpringBootTest(properties = {
    "grpc.server.in-process-name=test",
    "grpc.server.port=-1"
})
@ActiveProfiles("test")
class AcademicGrpcIntegrationTest extends AbstractAcademicIntegrationTest {

    @GrpcClient("inProcess")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    @Test
    void getGroup_validId_returnsGroup() {
        GroupResponse response = stub.getGroup(
            GroupRequest.newBuilder().setGroupId(seedGroupId).build());
        assertThat(response.getName()).isEqualTo("IVT-21-1");
        assertThat(response.getCode()).isEqualTo("ivt-21-1");
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    void getGroup_invalidId_throwsNotFound() {
        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
            () -> stub.getGroup(GroupRequest.newBuilder().setGroupId(99999L).build()));
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
    }
}
```

Note: the in-process test requires adding to `application.yml` (or test properties):
```yaml
grpc:
  server:
    port: 19091
  client:
    inProcess:
      address: in-process:test
      negotiationType: plaintext
```

### Pattern 4: GetTeacherSubjects — Multi-Repository Aggregation

This RPC requires data from three tables: `teacher_subject_groups`, `subjects`, `groups`. The existing `TeacherSubjectGroupRepository` returns entities with FK IDs only (D-03: no JPA associations). The implementation must enrich each assignment with subject name/type and group name via secondary repository calls.

```java
@Override
public void getTeacherSubjects(TeacherSubjectsRequest request,
                                StreamObserver<TeacherSubjectsResponse> responseObserver) {
    List<TeacherSubjectGroup> assignments = assignmentRepository
        .findByTeacherIdAndSemesterId(request.getTeacherId(), request.getSemesterId());

    TeacherSubjectsResponse.Builder builder = TeacherSubjectsResponse.newBuilder();
    for (TeacherSubjectGroup a : assignments) {
        Subject subject = subjectRepository.findById(a.getSubjectId()).orElse(null);
        Group group = groupRepository.findById(a.getGroupId()).orElse(null);
        if (subject != null && group != null) {
            builder.addSubjects(TeacherSubjectInfo.newBuilder()
                .setSubjectId(a.getSubjectId())
                .setSubjectName(subject.getName())
                .setSubjectType(subject.getType().name().toLowerCase())
                .setGroupId(a.getGroupId())
                .setGroupName(group.getName())
                .build());
        }
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
}
```

### Anti-Patterns to Avoid

- **Delegating to REST services from gRPC:** `GroupService`, `UserService`, etc. all depend on `RequestContext` which is request-scoped (Servlet scope). Calling them from a gRPC service (which runs outside Servlet context) throws `ScopeNotActiveException` at runtime. Always inject repositories directly in `AcademicGrpcServiceImpl`.
- **Calling `responseObserver.onNext()` after `onError()`:** Once `onError` is called, the stream is closed. Any subsequent call throws. Use a simple return-after-onError or exception-handler-only pattern.
- **Proto file in `src/main/proto`:** The project stores proto files at the monorepo root `proto/`. The Gradle sourceSets must be configured to point there; omitting this means the plugin finds no `.proto` files and generates nothing.
- **Using `grpc-spring-boot-starter` (combined) instead of `grpc-server-spring-boot-starter`:** The combined artifact also brings a gRPC client — unnecessary overhead and additional configuration. Use the server-only artifact.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Netty server lifecycle | Custom Netty server setup | `grpc-server-spring-boot-starter` | Boot manages start/stop, port binding, thread pool |
| Exception-to-status mapping | try/catch in every RPC method | `@GrpcAdvice` + `@GrpcExceptionHandler` | One class handles all RPCs; consistent status codes |
| Proto Java codegen | Manual Java classes mirroring proto messages | `protobuf-gradle-plugin` + `protoc-gen-grpc-java` | Guaranteed binary compatibility with proto contract |
| In-process test channel | Raw `InProcessChannelBuilder` setup | `grpc.server.in-process-name` + `@GrpcClient` | Spring manages channel lifecycle, no manual teardown |

**Key insight:** The gRPC framework provides everything needed for server registration, transport, and error propagation. The only application code required is: extend `ImplBase`, call `responseObserver.onNext()` + `onCompleted()`, and map domain exceptions once in `@GrpcAdvice`.

---

## Common Pitfalls

### Pitfall 1: RequestContext in gRPC Context
**What goes wrong:** `AcademicGrpcServiceImpl` delegates to `GroupService.findGroupById()`, which injects `RequestContext`. At runtime, the gRPC call is not inside a Servlet request, so the Spring `request` scope proxy throws `ScopeNotActiveException`.
**Why it happens:** `RequestContext` is `@Scope("request")` — only active during an HTTP request. gRPC calls run in Netty threads with no active request scope.
**How to avoid:** Never call REST services from gRPC impl. Inject and call repositories directly.
**Warning signs:** `ScopeNotActiveException: No thread-bound request found` in logs during gRPC call.

### Pitfall 2: Proto source directory mismatch
**What goes wrong:** Gradle build completes but no Java files are generated. Running the service fails with `ClassNotFoundException: ru.rutcampustrack.academic.grpc.GroupRequest`.
**Why it happens:** Default protobuf plugin source dir is `src/main/proto`. This project stores `.proto` at `proto/` (monorepo root).
**How to avoid:** Add `sourceSets { main { proto { srcDir(rootProject.file("proto")) } } }` to `build.gradle.kts`.
**Warning signs:** `build/generated/source/proto/main/` directory is empty or absent after `./gradlew build`.

### Pitfall 3: Wrong grpc artifact (combined vs server-only)
**What goes wrong:** Using `grpc-spring-boot-starter` (combined) triggers gRPC client autoconfiguration that looks for `grpc.client.*` properties. Startup may log warnings or fail if no clients are configured.
**Why it happens:** The combined starter includes both server and client starters.
**How to avoid:** Use `grpc-server-spring-boot-starter` only.
**Warning signs:** Startup error about missing `grpc.client.*.address` configuration.

### Pitfall 4: GetUserById returns NOT_FOUND for archived users
**What goes wrong:** `UserRepository.findById()` applies `@SQLRestriction("status <> 'archived'")` so an archived user's ID returns empty.
**Why it happens:** The `@SQLRestriction` is applied automatically to all JPA-generated queries on the `User` entity.
**How to avoid:** For `GetUserById` RPC, decide whether archived users should be returned (downstream services may need them for historical data). If yes, use `UserRepository.findByIdIncludingArchived()` (native query that bypasses the restriction).
**Warning signs:** `GetUserById` returns `NOT_FOUND` for a valid ID that belongs to an archived account.

### Pitfall 5: Port conflict during tests
**What goes wrong:** Integration tests start the real gRPC server on port 19091. If multiple test classes run in parallel, the port is already bound.
**Why it happens:** `@SpringBootTest` with a fixed port starts the actual Netty server.
**How to avoid:** Use in-process server for tests: `grpc.server.in-process-name=test` + `grpc.server.port=-1`. In-process uses in-memory transport, no port needed.
**Warning signs:** `java.net.BindException: Address already in use` during test run.

### Pitfall 6: grpc-bom vs protobuf-gradle-plugin version mismatch
**What goes wrong:** Generated `*ImplBase` uses protobuf API from one version, runtime has a different version. `AbstractMethodError` or `NoSuchMethodError` at test time.
**Why it happens:** `protoc` version used for codegen must produce classes compatible with runtime `protobuf-java` version.
**How to avoid:** Use `protoc:3.25.3` for codegen and let the grpc-bom (1.63.0) manage runtime `protobuf-java`. These are compatible. Do not override `protobuf-java` manually.
**Warning signs:** `AbstractMethodError` or `NoClassDefFoundError` for `com.google.protobuf.*` classes at test startup.

---

## Code Examples

Verified patterns from official sources:

### application.yml gRPC server config
```yaml
# Source: grpc-ecosystem/grpc-spring configuration documentation
grpc:
  server:
    port: 19091
```

### application-test.yml for in-process testing
```yaml
# Source: grpc-ecosystem/grpc-spring testing documentation
grpc:
  server:
    in-process-name: test
    port: -1
  client:
    inProcess:
      address: in-process:test
      negotiationType: plaintext
```

### @GrpcAdvice for exception mapping
```java
// Source: grpc-ecosystem/grpc-spring exception handling documentation
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import io.grpc.Status;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;

@GrpcAdvice
public class GrpcExceptionAdvice {

    @GrpcExceptionHandler(ResourceNotFoundException.class)
    public Status handleNotFound(ResourceNotFoundException e) {
        return Status.NOT_FOUND.withDescription(e.getMessage()).withCause(e);
    }

    @GrpcExceptionHandler(Exception.class)
    public Status handleInternal(Exception e) {
        return Status.INTERNAL.withDescription("Internal server error").withCause(e);
    }
}
```

### @GrpcService class skeleton
```java
// Source: grpc-ecosystem/grpc-spring server documentation
import net.devh.boot.grpc.server.service.GrpcService;
import io.grpc.stub.StreamObserver;
import ru.rutcampustrack.academic.grpc.*;   // generated classes

@GrpcService
public class AcademicGrpcServiceImpl
        extends AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase {
    // Inject repositories — NOT services that use RequestContext
}
```

### SemesterResponse: LocalDate to String conversion
`Semester.dateFrom` and `dateTo` are `LocalDate`. Proto `SemesterResponse` uses `string date_from/date_to`. Use `LocalDate.toString()` which produces ISO-8601 format (`2026-02-01`).

```java
SemesterResponse.newBuilder()
    .setId(semester.getId())
    .setName(semester.getName())
    .setDateFrom(semester.getDateFrom().toString())   // ISO-8601
    .setDateTo(semester.getDateTo().toString())
    .build()
```

### Gradle sourceSets for root-level proto directory
```kotlin
// Source: protobuf-gradle-plugin documentation, adapted for monorepo layout
sourceSets {
    main {
        proto {
            srcDir(rootProject.file("proto"))
        }
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `yidongnan/grpc-spring-boot-starter` (original repo) | `grpc-ecosystem/grpc-spring` (maintained fork) | ~2023 | Same artifact `net.devh:*` coordinates; just repo ownership changed. No code change needed. |
| Manual `ServerBuilder` / pure gRPC-Java | `@GrpcService` annotation with Spring integration | 2019+ | Boot manages lifecycle, no boilerplate server setup |
| Per-method try/catch for status codes | `@GrpcAdvice` / `@GrpcExceptionHandler` | 3.x series | Centralized, DRY exception mapping |

**Deprecated/outdated:**
- `grpc-server-spring-boot-starter` from `io.github.lognet` (LogNet variant): Different group ID, different API style. This project uses `net.devh` which is the grpc-ecosystem maintained version.

---

## Open Questions

1. **GetUserById: archived user behavior**
   - What we know: `UserRepository.findById()` silently excludes archived users. `findByIdIncludingArchived()` bypasses the restriction.
   - What's unclear: Should downstream services (Schedule, Attendance) receive data for archived students via gRPC?
   - Recommendation: Use `findByIdIncludingArchived()` for `GetUserById` RPC — downstream services need the record for historical attendance data even after archival.

2. **GetTeacherSubjects: N+1 query concern**
   - What we know: For each assignment row, two additional lookups (subject, group) are needed.
   - What's unclear: Volume is bounded (teachers rarely have more than ~20 assignments per semester), so N+1 is acceptable.
   - Recommendation: Use simple per-assignment repository calls. No need for custom JPQL JOIN query unless performance testing proves otherwise.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 | Compilation | Configured in CLAUDE.md | ms-21.0.9 | — |
| Gradle | Build | Wrapper in repo | gradle-wrapper.jar present | — |
| Docker / Testcontainers PostgreSQL | Integration tests | Already used in Phase 5/6 tests | postgres:16 image | — |
| gRPC Maven Central artifacts | Build | Maven Central reachable | Confirmed above | — |
| `protoc` binary | Proto compilation | Downloaded by plugin at build time | No pre-install needed | — |

No missing dependencies — all required tools are either in the repo or downloaded automatically by Gradle.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers |
| Config file | No separate config — uses `@SpringBootTest` annotations |
| Quick run command | `./gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest*"` |
| Full suite command | `./gradlew :services:academic-service:academic-app:test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| GRPC-01 | GetGroup valid ID returns name/code/active; invalid → NOT_FOUND | integration | `./gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.getGroup*"` | Wave 0 |
| GRPC-02 | GetGroupMembers returns only active students (no archived) | integration | `./gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.getGroupMembers*"` | Wave 0 |
| GRPC-03 | GetTeacherSubjects returns subject+group info for teacher | integration | `./gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.getTeacherSubjects*"` | Wave 0 |
| GRPC-04 | IsHeadman returns true/false correctly | integration | `./gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.isHeadman*"` | Wave 0 |
| GRPC-05 | GetActiveSemester returns active semester; NOT_FOUND when none active | integration | `./gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.getActiveSemester*"` | Wave 0 |
| GRPC-06 | GetCampusGeofence returns lat/lng/radius from campus_settings | integration | `./gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.getCampusGeofence*"` | Wave 0 |
| GRPC-07 | GetUserById returns user info including display_name and telegram_id | integration | `./gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest.getUserById*"` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :services:academic-service:academic-app:test --tests "*GrpcIntegrationTest*"`
- **Per wave merge:** `./gradlew :services:academic-service:academic-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicGrpcIntegrationTest.java` — covers all 7 GRPC-* requirements
- `services/academic-service/academic-app/src/test/resources/application-test.yml` (if absent) — in-process server config for gRPC tests

---

## Sources

### Primary (HIGH confidence)
- grpc-ecosystem/grpc-spring official docs (server, exception-handling, testing, configuration pages) — fetched 2026-03-30
- Maven Central: `net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE` — version confirmed current
- `proto/academic.proto` — project file, read directly
- All Phase 6 repository and service files — read directly from project
- `AbstractAcademicIntegrationTest.java` — read directly, existing test base class

### Secondary (MEDIUM confidence)
- google/protobuf-gradle-plugin GitHub example (Kotlin DSL) — `0.9.4` plugin version
- grpc-ecosystem/grpc-spring GitHub README — artifact coordinates, version matrix

### Tertiary (LOW confidence)
- None

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified against Maven Central and official release notes
- Architecture: HIGH — patterns from official grpc-ecosystem documentation, anti-patterns derived from project-specific code inspection (RequestContext scope issue)
- Pitfalls: HIGH — RequestContext scope issue derived from direct code inspection (known category from similar scoping problems); proto directory pitfall derived from project structure inspection

**Research date:** 2026-03-30
**Valid until:** 2026-06-30 (grpc-spring-boot-starter moves slowly; Spring Boot 3.x stable)
