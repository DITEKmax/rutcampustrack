# Phase 6: REST API + HATEOAS - Research

**Researched:** 2026-03-30
**Domain:** Spring Boot 3.4 REST API — Contract-first controllers, Spring HATEOAS, AOP authorization, RFC 7807 error handling, BCrypt password generation
**Confidence:** HIGH

## Summary

Phase 6 builds the full REST API layer for Academic Service on top of the Phase 5 entity and repository foundation. All infrastructure is already in place: 11 JPA entities, 11 repositories with domain-specific queries, `ErrorResponse` RFC 7807 record, `ResourceNotFoundException`, enum converters, Flyway schema, and the test harness (`AbstractAcademicIntegrationTest` with Testcontainers PostgreSQL). The app module already declares `spring-boot-starter-hateoas`, `spring-boot-starter-validation`, and `springdoc-openapi-starter-webmvc-ui` — no new dependencies are needed for this phase.

The primary challenge is the AOP-based authorization model (D-01 through D-03): there is no Spring Security, so access control lives in a request-scoped `RequestContext` populated from Gateway headers, custom `@RequireRole` annotation enforced by an AOP `@Around` advice, and in-service checks for headman-assistant delegation. The HATEOAS contract is also strict: all list endpoints must return `PagedModel<EntityModel<T>>`, all item endpoints `EntityModel<T>`, and `RepresentationModelAssembler` is the standard assembler pattern.

The 8 API interfaces (D-04) plus 8 controller implementations, 8+ service classes, 8+ assembler classes, and approximately 50 DTO types (request records + response RepresentationModel classes) constitute the bulk of the work. BCrypt password hashing in Academic Service (D-09) requires `spring-security-crypto` as a standalone dependency rather than the full Spring Security stack.

**Primary recommendation:** Implement each domain in a dedicated sub-package (`user/`, `group/`, `semester/`, `subject/`, `assignment/`, `homework/`, `threshold/`, `dashboard/`) — one API interface in `academic-api-contract`, one controller + service + assembler + DTOs in `academic-app`. Work domain-by-domain so each wave is independently testable.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Authorization Model**
- D-01: Use RequestContext pattern: a servlet Filter extracts X-User-Id, X-User-Role, X-Group-Id, X-Is-Headman headers into a RequestContext object (request-scoped bean or ThreadLocal)
- D-02: Custom `@RequireRole(UserRole.ADMIN)` annotation on controller methods, enforced via AOP interceptor. No Spring Security dependency
- D-03: Headman-assistant permission checks happen in the service layer, not at annotation level. Service calls `HeadmanAssistantRepository.findByStudentIdAndGroupId()` when role=STUDENT and is_headman=false to check delegated permissions

**API Contract Structure**
- D-04: Group contract interfaces by domain: UserApi, GroupApi, SemesterApi, SubjectApi, AssignmentApi, HomeworkApi, ThresholdApi, DashboardApi — 8 interfaces total
- D-05: URL paths by resource: `/api/academic/users`, `/api/academic/groups`, `/api/academic/semesters`, `/api/academic/subjects`, `/api/academic/assignments`, `/api/academic/homeworks`, `/api/academic/thresholds`, `/api/academic/dashboard`
- D-06: Student-specific endpoints: `GET /api/academic/users/me` (own profile), `GET /api/academic/groups/my/members` (group composition). ID resolved from X-User-Id header, not path variable
- D-07: Teacher-specific: `GET /api/academic/assignments/my` (own subjects+groups). ID from X-User-Id

**User Creation & Password**
- D-08: `POST /api/academic/users` generates login (via PostgreSQL sequence) + random password. Returns plain password ONE TIME in the response. Stores BCrypt hash in `password_hash` column
- D-09: Academic Service owns password hashing (BCrypt). Auth Service only reads `password_hash` for verification. No gRPC call to Auth for hashing
- D-10: Response DTO for user creation includes `initialPassword` field. Regular GET /users/{id} response does NOT include password

**Semester Operations**
- D-11: Semester activation: `PATCH /api/academic/semesters/{id}/activate`. Service atomically deactivates any currently active semester and activates the target one in a single @Transactional
- D-12: Semester deletion with confirmation: `DELETE /api/academic/semesters/{id}` with request body `{"confirmation": "exact semester name"}`. Service compares confirmation with actual semester name, returns 400 if mismatch

**Cascade Operations**
- D-13: Headman revoke (`PATCH /api/academic/users/{id}` setting is_headman=false): single @Transactional that clears is_headman flag AND bulk-deactivates all headman_assistants for that user+group via `HeadmanAssistantRepository.revokeAllByGroupId()`
- D-14: User soft-delete (status → archived): no cascade needed, @SQLRestriction handles filtering. Assistants of archived users become effectively invisible

**HATEOAS & Pagination (from CLAUDE.md — locked)**
- All list endpoints return `PagedModel<EntityModel<T>>` with `_links` (self, next, prev)
- All item endpoints return `EntityModel<T>` with self link
- RepresentationModelAssembler per domain entity
- PUT = full update (all fields required), PATCH = partial update (separate DTO, nullable fields)

**Error Handling (from CLAUDE.md — locked)**
- RFC 7807 Problem Details via `@ControllerAdvice`
- Controllers throw exceptions (ResourceNotFoundException, AccessDeniedException, etc.)
- GlobalExceptionHandler maps to appropriate HTTP status + Problem Details body

### Claude's Discretion
- DTO field naming and granularity within each domain
- Service layer internal structure (one service per domain vs split)
- Assembler implementation details
- Pagination default size and max limits
- Validation constraints on request DTOs

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| USER-01 | Admin can create user with auto-generated login and initial password | D-08/D-09: BCrypt + sequence login; `UserRepository.nextStudentLoginSeq()` / `nextTeacherLoginSeq()` already present; `UserCreatedResponse` DTO exposes `initialPassword` once |
| USER-02 | Admin can view, update, and soft-delete (archive) users | `@SQLRestriction` on User entity; `findByIdIncludingArchived()` for admin view; PUT for full update, PATCH for partial; status → 'archived' is soft-delete |
| USER-03 | Admin can assign headman flag to a student in a group | PATCH endpoint sets `is_headman=true` + `group_id` on User entity |
| USER-04 | Admin can revoke headman (auto-deactivates all assistants) | D-13: `HeadmanAssistantRepository.revokeAllByGroupId()` in same @Transactional |
| USER-05 | Admin can transfer student between groups with reason (history tracked) | Creates `StudentGroupHistory` record (left_at on old, joined_at on new); updates `user.group_id` |
| USER-06 | Student can view own profile | D-06: `GET /users/me` from X-User-Id header; role check: STUDENT only |
| USER-07 | Student can view group composition (members list) | D-06: `GET /groups/my/members` from X-Group-Id header; role check: STUDENT only |
| USER-08 | Teacher can view own assigned subjects and groups | D-07: `GET /assignments/my` from X-User-Id; role check: TEACHER only |
| GSEM-01 | Admin can CRUD groups | Standard CRUD; GroupApi interface; `GroupRepository` ready |
| GSEM-02 | Admin can CRUD semesters | Standard CRUD; SemesterApi interface; `SemesterRepository` ready |
| GSEM-03 | Admin can activate semester (only one active at a time) | D-11: `SemesterRepository.deactivateAllActive()` + save target as active in one @Transactional |
| GSEM-04 | Admin can delete semester with confirmation phrase guard | D-12: request body carries `{"confirmation": "..."}`, service compares to `semester.name` |
| SUBJ-01 | Headman can CRUD subjects with type | SubjectApi; `SubjectRepository`; SubjectType enum; headman role check |
| SUBJ-02 | Headman can assign teacher to subject+group (search by employee number) | D-03 assistant check; `UserRepository.findByEmployeeNumber()`; creates `TeacherSubjectGroup` |
| SUBJ-03 | Headman can remove teacher-subject-group assignment | `TeacherSubjectGroupRepository.deleteByTeacherIdAndSubjectIdAndGroupIdAndSemesterId()` |
| ASST-01 | Headman can assign assistant with granular permissions | Creates `HeadmanAssistant`; `permissions` stored as `String[]` in entity, mapped from `List<AssistantPermission>` in service |
| ASST-02 | Headman can revoke assistant | Sets `isActive=false`, `revokedAt=now()` on HeadmanAssistant |
| ASST-03 | Headman can update assistant permissions | PATCH on HeadmanAssistant permissions field |
| HW-01 | Headman can CRUD homeworks | HomeworkApi; `HomeworkRepository`; requires active semester context |
| HW-02 | Student can view group homeworks | Role check: STUDENT; filtered by `group_id` from X-Group-Id |
| HW-03 | Student can mark/unmark homework as completed | `HomeworkCompletionRepository`; toggle via POST (mark) + DELETE (unmark) |
| THRSH-01 | Admin can set global attendance threshold | `AttendanceThresholdRepository`; global = null group_id, null subject_id |
| THRSH-02 | Headman can set group-level threshold | group_id from X-Group-Id, null subject_id |
| THRSH-03 | Headman can set subject-level threshold | both group_id and subject_id non-null |
| THRSH-04 | System resolves threshold with most-specific-wins | Service queries all 3 levels and picks subject → group → global |
| DASH-01 | Admin can view summary statistics | Count queries on users by role + groups count; no complex aggregation needed |
</phase_requirements>

---

## Standard Stack

### Core (all already declared — no new dependencies needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-hateoas | via Spring Boot BOM (3.4.x → spring-hateoas 2.4.x) | `EntityModel`, `PagedModel`, `RepresentationModelAssembler`, `WebMvcLinkBuilder` | Spring standard for hypermedia REST |
| spring-boot-starter-web | via Spring Boot BOM | `@RestController`, `@ControllerAdvice`, `@RequestBody` | Spring MVC foundation |
| spring-boot-starter-validation | via Spring Boot BOM | `@Valid`, `@NotBlank`, `@NotNull`, `@Min/@Max` on request DTOs | Bean Validation 3.x |
| springdoc-openapi-starter-webmvc-ui | 2.7.0 | Swagger UI + OpenAPI 3 JSON | Already in build.gradle.kts |
| spring-hateoas | 2.4.1 | HATEOAS in api-contract module | Already in academic-api-contract/build.gradle.kts |

### New Dependency Required

| Library | Version | Purpose | Why Needed |
|---------|---------|---------|------------|
| spring-security-crypto | via Spring Boot BOM | `BCryptPasswordEncoder` for password hashing (D-09) | BCrypt without full Spring Security stack |

**Installation (add to academic-app/build.gradle.kts):**
```groovy
implementation("org.springframework.security:spring-security-crypto")
```

The `spring-security-crypto` artifact is included in the Spring Boot BOM, so no explicit version is needed.

### Alternatives Considered

| Standard Choice | Alternative | Why Standard Wins |
|-----------------|-------------|-------------------|
| AOP `@RequireRole` (D-02) | Spring Security `@PreAuthorize` | Decision locked; no Spring Security dependency allowed |
| `RepresentationModelAssembler` | Manual `EntityModel.of()` in controller | Assembler pattern keeps controllers thin and reusable for pagination |
| `PagedModel` via `PagedResourcesAssembler` | Custom paged response | Spring HATEOAS assembler handles `_links.self/next/prev` automatically |
| BCrypt from `spring-security-crypto` | Plain SHA-256 or Argon2 | BCrypt is the established choice; already used by Auth Service |

---

## Architecture Patterns

### Recommended Project Structure

```
services/academic-service/
  academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/
    api/
      UserApi.java              # @RequestMapping("/academic/users")
      GroupApi.java
      SemesterApi.java
      SubjectApi.java
      AssignmentApi.java
      HomeworkApi.java
      ThresholdApi.java
      DashboardApi.java
    dto/
      user/
        CreateUserRequest.java  # record (no Lombok)
        UpdateUserRequest.java  # record (PUT — all fields)
        PatchUserRequest.java   # record (PATCH — nullable fields)
        UserResponse.java       # extends RepresentationModel<UserResponse>
        UserCreatedResponse.java # extends RepresentationModel — has initialPassword
        TransferStudentRequest.java
      group/
        CreateGroupRequest.java
        UpdateGroupRequest.java
        GroupResponse.java
      semester/
        CreateSemesterRequest.java
        UpdateSemesterRequest.java
        SemesterResponse.java
        DeleteSemesterRequest.java  # {"confirmation": "..."}
      subject/
        CreateSubjectRequest.java
        UpdateSubjectRequest.java
        SubjectResponse.java
      assignment/
        AssignTeacherRequest.java
        AssignmentResponse.java
      homework/
        CreateHomeworkRequest.java
        UpdateHomeworkRequest.java
        HomeworkResponse.java
      assistant/
        AssignAssistantRequest.java
        UpdateAssistantPermissionsRequest.java
        AssistantResponse.java
      threshold/
        SetThresholdRequest.java
        ThresholdResponse.java
        ResolvedThresholdResponse.java
      dashboard/
        DashboardStatsResponse.java

  academic-app/src/main/java/ru/rutcampustrack/academic/
    security/
      RequestContext.java         # request-scoped bean holding userId, role, groupId, isHeadman
      RequestContextFilter.java   # OncePerRequestFilter — parses headers → RequestContext
      RequireRole.java            # @Target(METHOD) @Retention(RUNTIME) annotation
      RoleCheckAspect.java        # @Around("@annotation(requireRole)") — throws AccessDeniedException
    user/
      UserController.java
      UserService.java
      UserAssembler.java
    group/
      GroupController.java
      GroupService.java
      GroupAssembler.java
    semester/
      SemesterController.java
      SemesterService.java
      SemesterAssembler.java
    subject/
      SubjectController.java
      SubjectService.java
      SubjectAssembler.java
    assignment/
      AssignmentController.java
      AssignmentService.java
      AssignmentAssembler.java
    homework/
      HomeworkController.java
      HomeworkService.java
      HomeworkAssembler.java
    assistant/
      AssistantController.java
      AssistantService.java
      AssistantAssembler.java
    threshold/
      ThresholdController.java
      ThresholdService.java
      ThresholdAssembler.java
    dashboard/
      DashboardController.java
      DashboardService.java
    exception/
      GlobalExceptionHandler.java
      AccessDeniedException.java  # HTTP 403
      ConflictException.java      # HTTP 409
      BadRequestException.java    # HTTP 400 (confirmation mismatch etc.)
```

### Pattern 1: Contract-First Controller

The controller class in `academic-app` implements the interface from `academic-api-contract`. All `@RequestMapping`, `@GetMapping`, `@PostMapping` annotations and Swagger annotations live ONLY in the interface. The controller provides only the implementation.

```java
// In academic-api-contract: UserApi.java
@Tag(name = "Users", description = "User management")
@RequestMapping("/academic/users")
public interface UserApi {

    @Operation(summary = "Create user")
    @ApiResponse(responseCode = "201", description = "User created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @PostMapping
    ResponseEntity<EntityModel<UserCreatedResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request);

    @Operation(summary = "Get user by ID")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "404", description = "Not found")
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<UserResponse>> getUser(@PathVariable Long id);

    @Operation(summary = "Get own profile (student)")
    @GetMapping("/me")
    ResponseEntity<EntityModel<UserResponse>> getMe();

    // ... etc
}

// In academic-app: UserController.java
@RestController
public class UserController implements UserApi {
    // No @RequestMapping here — inherited from UserApi
    // Only @Override methods calling UserService
}
```

### Pattern 2: RequestContext + AOP Authorization

```java
// RequestContext.java — request-scoped Spring bean
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {
    private Long userId;
    private UserRole role;
    private Long groupId;
    private boolean isHeadman;
    // getters/setters
}

// RequestContextFilter.java
@Component
public class RequestContextFilter extends OncePerRequestFilter {
    private final RequestContext requestContext;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null) {
            requestContext.setUserId(Long.parseLong(userIdHeader));
            requestContext.setRole(UserRole.valueOf(
                request.getHeader("X-User-Role").toUpperCase()));
            String groupIdHeader = request.getHeader("X-Group-Id");
            if (groupIdHeader != null) {
                requestContext.setGroupId(Long.parseLong(groupIdHeader));
            }
            requestContext.setHeadman(
                Boolean.parseBoolean(request.getHeader("X-Is-Headman")));
        }
        chain.doFilter(request, response);
    }
}

// RequireRole.java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    UserRole[] value();
}

// RoleCheckAspect.java
@Aspect
@Component
public class RoleCheckAspect {
    private final RequestContext requestContext;

    @Around("@annotation(requireRole)")
    public Object checkRole(ProceedingJoinPoint pjp, RequireRole requireRole) throws Throwable {
        UserRole[] required = requireRole.value();
        UserRole actual = requestContext.getRole();
        boolean allowed = Arrays.asList(required).contains(actual);
        if (!allowed) {
            throw new AccessDeniedException("Required role: " + Arrays.toString(required));
        }
        return pjp.proceed();
    }
}
```

### Pattern 3: HATEOAS Assembler + PagedResourcesAssembler

```java
// UserAssembler.java
@Component
public class UserAssembler implements RepresentationModelAssembler<User, EntityModel<UserResponse>> {

    @Override
    public EntityModel<UserResponse> toModel(User entity) {
        UserResponse response = mapToResponse(entity);
        return EntityModel.of(response,
            linkTo(methodOn(UserController.class).getUser(entity.getId())).withSelfRel());
    }

    private UserResponse mapToResponse(User user) {
        // map fields from entity to response DTO
    }
}

// UserController list endpoint
@GetMapping
public ResponseEntity<PagedModel<EntityModel<UserResponse>>> listUsers(
        @PageableDefault(size = 20, max = 100) Pageable pageable,
        PagedResourcesAssembler<User> pagedAssembler) {
    Page<User> page = userRepository.findAll(pageable);
    return ResponseEntity.ok(pagedAssembler.toModel(page, userAssembler));
}
```

The `PagedResourcesAssembler` is auto-wired by Spring HATEOAS and automatically generates `self`, `next`, `prev`, `first`, `last` links.

### Pattern 4: BCrypt Password Generation (D-08/D-09)

```java
// UserService.java — user creation
@Service
public class UserService {
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public UserCreatedResponse createUser(CreateUserRequest request) {
        // Generate login via sequence
        String login = generateLogin(request.role());
        // Generate random plain password
        String plainPassword = generateRandomPassword();
        // Hash and store
        String hash = passwordEncoder.encode(plainPassword);
        User user = buildUser(request, login, hash);
        userRepository.save(user);
        // Return DTO with plainPassword ONCE
        return new UserCreatedResponse(user.getId(), login, plainPassword, ...);
    }

    private String generateLogin(UserRole role) {
        if (role == UserRole.STUDENT) {
            Long seq = userRepository.nextStudentLoginSeq();
            return String.format("student%05d", seq);
        } else {
            Long seq = userRepository.nextTeacherLoginSeq();
            return String.format("teacher%05d", seq);
        }
    }

    private String generateRandomPassword() {
        // SecureRandom-based 12-char alphanumeric
        return new SecureRandom().ints(12, 0, CHARSET.length())
            .mapToObj(i -> String.valueOf(CHARSET[i]))
            .collect(Collectors.joining());
    }
}
```

### Pattern 5: GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(404).body(new ErrorResponse(
            404, "https://api.rutcampustrack.ru/problems/resource-not-found",
            "Ресурс не найден", ex.getMessage(),
            request.getRequestURI(), Instant.now(), null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(403).body(new ErrorResponse(
            403, "https://api.rutcampustrack.ru/problems/forbidden",
            "Доступ запрещён", ex.getMessage(),
            request.getRequestURI(), Instant.now(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
            .getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(
                fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()))
            .toList();
        return ResponseEntity.status(400).body(new ErrorResponse(
            400, "https://api.rutcampustrack.ru/problems/validation-error",
            "Ошибка валидации", "Одно или более полей не прошли валидацию",
            request.getRequestURI(), Instant.now(), fieldErrors));
    }
}
```

### Pattern 6: Headman-Assistant Permission Check (D-03)

This check happens in the service layer. For endpoints requiring HEADMAN role, the service additionally verifies whether the calling student has been delegated the relevant permission via `HeadmanAssistant`:

```java
// In service method for headman-only operations
private void requireHeadmanOrAssistantPermission(Long groupId, AssistantPermission permission) {
    UserRole role = requestContext.getRole();
    if (role == UserRole.STUDENT && requestContext.isHeadman()) {
        return; // actual headman — allowed
    }
    if (role == UserRole.STUDENT && !requestContext.isHeadman()) {
        // Check if assistant with required permission
        headmanAssistantRepository
            .findByGroupIdAndStudentId(groupId, requestContext.getUserId())
            .filter(HeadmanAssistant::isActive)
            .filter(a -> Arrays.asList(a.getPermissions())
                .contains(permission.name().toLowerCase()))
            .orElseThrow(() -> new AccessDeniedException("Недостаточно прав"));
        return;
    }
    throw new AccessDeniedException("Недостаточно прав");
}
```

### Anti-Patterns to Avoid

- **Swagger annotations in controller class:** Mappings and `@Operation`/`@ApiResponse` go ONLY in the contract interface, per CLAUDE.md contract-first rule.
- **Lombok in academic-api-contract:** Request DTOs must be Java `record`, response DTOs must be classes without Lombok. Lombok only in `academic-app`.
- **JPA associations instead of FK fields:** All entities use plain `Long` FK fields with no `@ManyToOne`/`@OneToMany`. Joins are done via repository queries, never via JPA relationship traversal.
- **@Enumerated(EnumType.ORDINAL):** Forbidden. All enums use `LowercaseEnumConverter` with `autoApply=true`.
- **Spring Security for role enforcement:** Forbidden (D-02). Use `@RequireRole` + `RoleCheckAspect`.
- **Publishing events inside @Transactional (Phase 9 concern but worth noting):** Any future RabbitMQ publishing must use `@TransactionalEventListener(AFTER_COMMIT)` — not relevant for Phase 6 but do not introduce AMQP calls.
- **BCrypt in contract module:** `BCryptPasswordEncoder` lives in `academic-app`'s `UserService`, never in the contract.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HATEOAS pagination links | Custom paged wrapper with next/prev fields | `PagedResourcesAssembler.toModel(Page, assembler)` | Handles RFC 5988 links, page metadata, edge cases automatically |
| Request DTO validation | Manual null checks in controller/service | `@Valid` + Bean Validation annotations (`@NotBlank`, `@Min`, `@Size`) | Jakarta Validation integrates with `MethodArgumentNotValidException` → `GlobalExceptionHandler` |
| OpenAPI spec generation | Hand-authored YAML/JSON | `springdoc-openapi-starter-webmvc-ui` + annotations in contract interface | Auto-generates from annotations; already on classpath |
| BCrypt hashing | Custom password hash | `BCryptPasswordEncoder` from `spring-security-crypto` | BCrypt has correct work factor, salt included in hash — avoids rainbow table attacks |
| Pageable parameter parsing | Manual `page`/`size`/`sort` query param extraction | `Pageable` method parameter + `@PageableDefault` | Spring MVC resolves `?page=0&size=20&sort=name,asc` automatically |
| Entity-to-DTO mapping | MapStruct or ModelMapper | Manual mapping in Assembler `toModel()` | Project does not use MapStruct; manual mapping is explicit and keeps contract module clean |

**Key insight:** The Spring HATEOAS `RepresentationModelAssembler<T, D>` + `PagedResourcesAssembler<T>` pair does all the heavy lifting for HATEOAS compliance. The only custom code needed is the `toModel(T entity)` method that maps fields and adds links.

---

## Common Pitfalls

### Pitfall 1: Request-Scoped Bean in Singleton AOP Aspect

**What goes wrong:** `RoleCheckAspect` is a singleton Spring bean. If `RequestContext` is injected as a direct field without the CGLIB proxy wrapper, it captures the first instance created at startup (not the request-scoped instance), causing NPE or stale data.

**Why it happens:** Spring request-scoped beans must be accessed through a proxy. The `@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)` declaration on `RequestContext` is essential.

**How to avoid:** Declare `RequestContext` with `@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)`. Inject it normally into `RoleCheckAspect` and `RequestContextFilter` — Spring will use the proxy to delegate to the real request-scoped instance.

**Warning signs:** `NullPointerException` in `RoleCheckAspect` or all requests appear to have the same user ID.

### Pitfall 2: Gateway StripPrefix Removes Only `/api` — Not `/api/academic`

**What goes wrong:** The API Gateway route strips only prefix=1 (`/api`), so requests arrive at Academic Service as `/academic/users`. Controller mapping must start with `/academic/`, not `/users/` directly.

**Why it happens:** `StripPrefix=1` removes one path segment. The route predicate is `/api/academic/**` — stripping 1 prefix leaves `/academic/**`.

**How to avoid:** All `@RequestMapping` in contract interfaces must be `/academic/users`, `/academic/groups`, etc. (as specified in D-05). The `GET /api/academic/users` from the Gateway arrives at the service as `GET /academic/users`.

**Warning signs:** `404 Not Found` for every request even though the service is running.

### Pitfall 3: `DELETE` with Request Body

**What goes wrong:** Some HTTP clients and proxies discard the body of DELETE requests. The semester deletion confirmation (D-12) sends `{"confirmation": "..."}` in the body of a DELETE request.

**Why it happens:** RFC 7231 does not forbid a DELETE body, but it is semantically unusual.

**How to avoid:** Spring MVC handles `@RequestBody` on DELETE correctly. Mark the `@DeleteMapping` parameter as `@RequestBody DeleteSemesterRequest request`. If clients report missing body, they may need explicit `Content-Type: application/json` header.

**Warning signs:** Confirmation always fails; service receives null body on DELETE.

### Pitfall 4: AssistantPermission Array Conversion

**What goes wrong:** `HeadmanAssistant.permissions` is stored as `String[]` in the entity (using `@JdbcTypeCode(SqlTypes.ARRAY)`). Service code that compares against `AssistantPermission` enum must convert carefully — the stored strings are lowercase (e.g., `"mark_attendance"`), while `AssistantPermission.MARK_ATTENDANCE.name()` returns `"MARK_ATTENDANCE"`.

**Why it happens:** PostgreSQL array elements were seeded as lowercase in test data; enum names are UPPER_CASE.

**How to avoid:** When checking permissions in `requireHeadmanOrAssistantPermission()`, compare as `permission.name().toLowerCase()` against the stored `String[]` values. When creating/updating assistants, convert `List<AssistantPermission>` to `String[]` via `.stream().map(p -> p.name().toLowerCase()).toArray(String[]::new)`.

**Warning signs:** Permission check always fails even for valid assistants; `Arrays.asList(permissions).contains(...)` returns false.

### Pitfall 5: Semester EXCLUDE Constraint vs. Service-Level Deactivation

**What goes wrong:** The PostgreSQL EXCLUDE constraint (`EXCLUDE WHERE (is_active = true)`) prevents two active semesters at the DB level. The service's `@Transactional` deactivates all active semesters then activates the new one — but if the DB constraint fires on `INSERT` before `deactivateAllActive()` is committed, it will throw `DataIntegrityViolationException`.

**Why it happens:** The constraint fires on the INSERT statement level within the transaction. The order matters: call `semesterRepository.deactivateAllActive()` first and flush, then set target semester active and save.

**How to avoid:** In `SemesterService.activateSemester()`:
1. Call `semesterRepository.deactivateAllActive()` — this UPDATE runs first.
2. Call `entityManager.flush()` (or use `saveAndFlush`) to ensure the deactivation is visible before the activation INSERT/UPDATE.
3. Set target semester `isActive = true` and save.

**Warning signs:** `DataIntegrityViolationException` on semester activation even though only one was intended to be active.

### Pitfall 6: `@SQLRestriction` Causes findById to Return Empty for Archived Users

**What goes wrong:** Admin calling `GET /users/{id}` for an archived user gets a 404 because `userRepository.findById(id)` is filtered by `@SQLRestriction("status <> 'archived'")`.

**Why it happens:** `@SQLRestriction` is applied globally to all Hibernate queries on the entity.

**How to avoid:** For admin-facing user lookup, use `userRepository.findByIdIncludingArchived(id)` which uses a native query bypassing the restriction. Regular user-facing lookups correctly return empty for archived users.

**Warning signs:** Admin reports they can't view or manage archived users.

### Pitfall 7: `PagedModel` Link Generation Requires `@EnableHypermediaSupport` or Auto-Configuration

**What goes wrong:** `PagedResourcesAssembler` fails to inject, or `_links` are missing from paged responses.

**Why it happens:** Spring HATEOAS requires `@EnableHypermediaSupport(type = HAL)` or the auto-configuration from `spring-boot-starter-hateoas`. The starter enables it automatically, but only if the auto-configuration is not excluded.

**How to avoid:** Ensure `spring-boot-starter-hateoas` is on the classpath (already declared). Do NOT exclude `HypermediaAutoConfiguration` in test profiles. In `AbstractAcademicIntegrationTest`, only RabbitMQ and Redis are excluded — HATEOAS auto-configuration should remain active.

**Warning signs:** `PagedResourcesAssembler` is null at injection, or responses contain `content` array but no `_links`.

---

## Code Examples

### Setting Up `@Pageable` with `PagedResourcesAssembler`

```java
// Source: Spring HATEOAS docs — PagedResourcesAssembler
@GetMapping
public ResponseEntity<PagedModel<EntityModel<GroupResponse>>> listGroups(
        @RequestParam(required = false) Boolean active,
        Pageable pageable,
        PagedResourcesAssembler<Group> pagedAssembler) {

    Page<Group> page = (active != null)
        ? groupRepository.findByIsActive(active, pageable)
        : groupRepository.findAll(pageable);

    return ResponseEntity.ok(pagedAssembler.toModel(page, groupAssembler));
}
```

### Self Link in Single Item Response

```java
// Source: Spring HATEOAS docs — linkTo / methodOn
@Override
public EntityModel<GroupResponse> toModel(Group group) {
    return EntityModel.of(toResponse(group),
        linkTo(methodOn(GroupController.class).getGroup(group.getId())).withSelfRel(),
        linkTo(methodOn(GroupController.class).listGroups(null, Pageable.unpaged(), null))
            .withRel("all-groups"));
}
```

### Threshold Resolution Service Logic (THRSH-04)

```java
// Most-specific-wins: subject > group > global
public ResolvedThresholdResponse resolveThreshold(Long groupId, Long subjectId) {
    // Subject-level (most specific)
    Optional<AttendanceThreshold> subject =
        thresholdRepository.findByGroupIdAndSubjectId(groupId, subjectId);
    if (subject.isPresent()) {
        return new ResolvedThresholdResponse(subject.get().getThresholdPct(), "subject");
    }
    // Group-level
    Optional<AttendanceThreshold> group =
        thresholdRepository.findByGroupIdAndSubjectIdIsNull(groupId);
    if (group.isPresent()) {
        return new ResolvedThresholdResponse(group.get().getThresholdPct(), "group");
    }
    // Global (least specific)
    return thresholdRepository.findByGroupIdIsNullAndSubjectIdIsNull()
        .map(g -> new ResolvedThresholdResponse(g.getThresholdPct(), "global"))
        .orElseThrow(() -> new ResourceNotFoundException("AttendanceThreshold", "scope", "global"));
}
```

### Semester Activation (GSEM-03, avoiding EXCLUDE constraint race)

```java
@Transactional
public EntityModel<SemesterResponse> activateSemester(Long id) {
    Semester target = semesterRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", id));
    // 1. Deactivate all currently active (UPDATE runs immediately in transaction)
    semesterRepository.deactivateAllActive();
    semesterRepository.flush();  // force DB write before the next save
    // 2. Activate the target
    target.setActive(true);
    semesterRepository.save(target);
    return semesterAssembler.toModel(target);
}
```

### Dashboard Statistics (DASH-01)

```java
// DashboardService.java — uses JpaRepository count queries
public DashboardStatsResponse getStats() {
    long studentCount = userRepository.countByRole(UserRole.STUDENT);  // add to repo
    long teacherCount = userRepository.countByRole(UserRole.TEACHER);
    long groupCount   = groupRepository.countByIsActiveTrue();          // add to repo
    long adminCount   = userRepository.countByRole(UserRole.ADMIN);
    return new DashboardStatsResponse(studentCount, teacherCount, adminCount, groupCount);
}
```

Note: `countByRole(UserRole)` and `countByIsActiveTrue()` need to be added to `UserRepository` and `GroupRepository` (Spring Data derives them automatically from method names — no `@Query` needed).

---

## Environment Availability

Step 2.6: SKIPPED — Phase 6 is code-only changes building on top of Phase 5's infrastructure. All external services (PostgreSQL, Redis, RabbitMQ) were verified available in Phase 5. No new external dependencies are introduced.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) |
| Config file | none — configuration via `AbstractAcademicIntegrationTest` base class |
| Quick run command | `./gradlew :services:academic-service:academic-app:test --tests "*.UserControllerTest"` |
| Full suite command | `./gradlew :services:academic-service:academic-app:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| USER-01 | POST /academic/users creates user with login+initialPassword | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.UserControllerTest.createUser_*"` | Wave 0 |
| USER-02 | GET/PUT/PATCH/DELETE user (soft-delete = archive) | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.UserControllerTest"` | Wave 0 |
| USER-03/04 | PATCH headman assign/revoke; revoke cascades assistants | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.UserControllerTest.headman_*"` | Wave 0 |
| USER-05 | Student group transfer creates StudentGroupHistory | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.UserControllerTest.transfer_*"` | Wave 0 |
| USER-06/07 | GET /me and GET /groups/my/members from headers | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.UserControllerTest.me_*"` | Wave 0 |
| USER-08 | GET /assignments/my for teacher | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.AssignmentControllerTest.my_*"` | Wave 0 |
| GSEM-01/02 | CRUD groups and semesters | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.GroupControllerTest"` | Wave 0 |
| GSEM-03 | Activate semester deactivates previous atomically | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.SemesterControllerTest.activate_*"` | Wave 0 |
| GSEM-04 | Delete semester with correct/wrong confirmation | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.SemesterControllerTest.delete_*"` | Wave 0 |
| SUBJ-01..03 | CRUD subjects; assign/remove teacher | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.SubjectControllerTest"` | Wave 0 |
| ASST-01..03 | Assign/revoke/update assistant permissions | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.AssistantControllerTest"` | Wave 0 |
| HW-01..03 | CRUD homework; student view; mark/unmark completion | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.HomeworkControllerTest"` | Wave 0 |
| THRSH-01..04 | Set global/group/subject threshold; resolve most-specific | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.ThresholdControllerTest"` | Wave 0 |
| DASH-01 | GET /academic/dashboard returns stats counts | integration | `./gradlew :services:academic-service:academic-app:test --tests "*.DashboardControllerTest"` | Wave 0 |
| Role enforcement | STUDENT calling admin endpoint returns 403 | integration | Included in each controller test class (negative path) | Wave 0 |
| HATEOAS | All item responses have `_links.self`; paged responses have `_links.self/next/prev` | integration | Included in controller tests (assert JSON path) | Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :services:academic-service:academic-app:test --tests "*.<ControllerBeingImplemented>Test"`
- **Per wave merge:** `./gradlew :services:academic-service:academic-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

All test files are missing — the existing test infrastructure (`AbstractAcademicIntegrationTest`) provides the base class and Testcontainers setup. New test classes must be created in Wave 0:

- [ ] `tests/integration/UserControllerTest.java` — covers USER-01 through USER-08 + 403 enforcement
- [ ] `tests/integration/GroupControllerTest.java` — covers GSEM-01
- [ ] `tests/integration/SemesterControllerTest.java` — covers GSEM-02, GSEM-03, GSEM-04
- [ ] `tests/integration/SubjectControllerTest.java` — covers SUBJ-01, SUBJ-02, SUBJ-03
- [ ] `tests/integration/AssignmentControllerTest.java` — covers USER-08
- [ ] `tests/integration/AssistantControllerTest.java` — covers ASST-01, ASST-02, ASST-03
- [ ] `tests/integration/HomeworkControllerTest.java` — covers HW-01, HW-02, HW-03
- [ ] `tests/integration/ThresholdControllerTest.java` — covers THRSH-01 through THRSH-04
- [ ] `tests/integration/DashboardControllerTest.java` — covers DASH-01
- [ ] `tests/integration/RoleEnforcementTest.java` — cross-domain 403 verification
- Framework install: not needed — JUnit 5 + Spring Boot Test + Testcontainers already in `build.gradle.kts`

Test classes should extend `AbstractAcademicIntegrationTest` and use `MockMvc` (or `TestRestTemplate` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` already in the base class, so `TestRestTemplate` is available). Use `MockHttpServletRequest` headers to inject `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman` without actual JWT.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@EnableHypermediaSupport` manual setup | Spring Boot HATEOAS auto-configuration via starter | Spring Boot 2.x | Nothing to configure manually |
| `ResourceSupport` / `Resource<T>` | `RepresentationModel` / `EntityModel<T>` | Spring HATEOAS 1.0 (2019) | `Resource<T>` is removed; must use `EntityModel<T>` |
| `PagedResources` | `PagedModel` | Spring HATEOAS 1.0 (2019) | `PagedResources` is removed |
| `ControllerLinkBuilder` | `WebMvcLinkBuilder` (via `linkTo(methodOn(...))`) | Spring HATEOAS 1.0 (2019) | `ControllerLinkBuilder` is removed |

**Deprecated/outdated:**
- `Resource<T>`: Removed in spring-hateoas 1.0 — use `EntityModel<T>`
- `Resources<T>`: Removed — use `CollectionModel<T>` or `PagedModel<T>`
- `PagedResources<T>`: Removed — use `PagedModel<T>`
- `ControllerLinkBuilder`: Removed — use `WebMvcLinkBuilder`

---

## Open Questions

1. **Homework completion toggle endpoint design**
   - What we know: HW-03 requires mark (POST) and unmark (DELETE) — standard REST toggle
   - What's unclear: Whether `POST /homeworks/{id}/complete` + `DELETE /homeworks/{id}/complete` or a PATCH with a `completed: true/false` field is preferred
   - Recommendation: Use POST/DELETE symmetry (`POST /homeworks/{id}/complete`, `DELETE /homeworks/{id}/complete`) — cleaner REST semantics for a boolean state resource

2. **Semester context for homework/assignment queries**
   - What we know: `HomeworkRepository.findByGroupIdAndSubjectIdAndSemesterId()` requires a `semesterId`. Several contexts (HW-02, SUBJ-02) need the active semester.
   - What's unclear: Should the active semester be resolved automatically by the service, or should the client pass `?semesterId=` as a query parameter?
   - Recommendation: Service resolves active semester automatically (calls `semesterRepository.findByIsActiveTrue()`) — this reduces client complexity and is consistent with the system having exactly one active semester at a time.

3. **AssistantPermission check: headman vs. headman assistant for subject CRUD**
   - What we know: D-03 says assistant checks happen in service layer. SUBJ-01 says "headman can CRUD subjects" — it's unclear whether `MANAGE_HOMEWORK` or a specific permission covers subject management.
   - What's unclear: Which `AssistantPermission` values are checked for subject CRUD vs. homework CRUD vs. attendance marking.
   - Recommendation: Use `MANAGE_HOMEWORK` for HW CRUD (ASST domain), `MARK_ATTENDANCE` for attendance, `CANCEL_LESSONS` for lesson ops. For subject CRUD, the actual headman flag is required — assistants cannot manage subjects. This is a discretionary design choice.

---

## Project Constraints (from CLAUDE.md)

The planner MUST enforce these directives from CLAUDE.md in all generated tasks:

| Constraint | Directive |
|-----------|-----------|
| Contract-first | Controller in `academic-app` implements interface from `academic-api-contract`. Mappings ONLY in the interface. |
| No Lombok in contracts | Request DTOs = Java `record`. Response DTOs = class extending `RepresentationModel`. No Lombok in `*-api-contract` modules. |
| Lombok in app only | Entities and internal classes in `academic-app` may use Lombok. |
| Enum storage | No `@Enumerated(EnumType.ORDINAL)`. Always `LowercaseEnumConverter` with `autoApply=true`. |
| DB lowercase | PostgreSQL stores enum values as lowercase strings. |
| Flyway only | Schema changes via Flyway migrations. `ddl-auto: validate`. Hibernate never creates schema. |
| Soft delete | Users are archived (status='archived'), never DELETEd. `@SQLRestriction` handles filtering. |
| REST rules | HATEOAS Level 3: `EntityModel<T>`, `PagedModel<EntityModel<T>>`. RFC 7807 errors. `@ControllerAdvice` for error handling. |
| PUT vs PATCH | PUT = full update (all fields required). PATCH = partial (separate DTO, nullable fields). |
| Error handling | Controllers only throw exceptions. `GlobalExceptionHandler` maps to HTTP + Problem Details. |
| Naming | Packages: `ru.rutcampustrack.{service}.{module}`. REST paths: `/api/{service}/...` (through Gateway). |
| No gRPC, no Redis, no RabbitMQ in Phase 6 | Explicitly out of scope per CONTEXT.md domain boundary. |

---

## Sources

### Primary (HIGH confidence)
- Existing codebase — `academic-api-contract/build.gradle.kts` confirms spring-hateoas 2.4.1 is already declared
- Existing codebase — `academic-app/build.gradle.kts` confirms all Spring Boot starters including HATEOAS are declared
- Existing entities (Phase 5 output) — all 11 entity files read and verified
- Existing repositories (Phase 5 output) — all 11 repository files read and verified
- `AbstractAcademicIntegrationTest.java` — test infrastructure pattern confirmed
- `ErrorResponse.java` and `ResourceNotFoundException.java` — reusable assets confirmed
- `CLAUDE.md` — project conventions verified

### Secondary (MEDIUM confidence)
- Spring HATEOAS 2.x API knowledge — `EntityModel`, `PagedModel`, `RepresentationModelAssembler`, `WebMvcLinkBuilder` are stable and documented; knowledge from training data confirmed against existing contract module which already imports `spring-hateoas:2.4.1`
- `spring-security-crypto` as standalone BCrypt dependency — standard pattern for non-Security projects; BCryptPasswordEncoder is in this module

### Tertiary (LOW confidence)
- None — all critical claims are backed by the actual codebase or well-established Spring APIs

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified from existing build.gradle.kts files; only `spring-security-crypto` is a new addition (standard Maven artifact, no version needed due to BOM)
- Architecture: HIGH — contract-first pattern already established in project; HATEOAS patterns are locked in CLAUDE.md and contract module already imports spring-hateoas
- Pitfalls: HIGH — majority identified from reading the actual entity/repository code and the Phase 5 test file which already tests some edge cases

**Research date:** 2026-03-30
**Valid until:** 2026-06-30 (Spring Boot 3.4 / Spring HATEOAS 2.4 are stable; BCrypt API unchanged)
