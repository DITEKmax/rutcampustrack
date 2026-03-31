# Phase 6: REST API + HATEOAS - Context

**Gathered:** 2026-03-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Full CRUD REST API for Academic Service: controllers, services, DTOs (request records + response RepresentationModel classes), HATEOAS assemblers, role-based authorization via RequestContext + custom annotations, pagination, and RFC 7807 error handling. Covers all 26 requirements: USER-01 through USER-08, GSEM-01 through GSEM-04, SUBJ-01 through SUBJ-03, ASST-01 through ASST-03, HW-01 through HW-03, THRSH-01 through THRSH-04, DASH-01. No gRPC, no Redis, no RabbitMQ events.

</domain>

<decisions>
## Implementation Decisions

### Authorization Model
- **D-01:** Use RequestContext pattern: a servlet Filter extracts X-User-Id, X-User-Role, X-Group-Id, X-Is-Headman headers into a RequestContext object (request-scoped bean or ThreadLocal)
- **D-02:** Custom `@RequireRole(UserRole.ADMIN)` annotation on controller methods, enforced via AOP interceptor. No Spring Security dependency
- **D-03:** Headman-assistant permission checks happen in the service layer, not at annotation level. Service calls `HeadmanAssistantRepository.findByStudentIdAndGroupId()` when role=STUDENT and is_headman=false to check delegated permissions

### API Contract Structure
- **D-04:** Group contract interfaces by domain: UserApi, GroupApi, SemesterApi, SubjectApi, AssignmentApi (teacher-subject-group), HomeworkApi, ThresholdApi, DashboardApi — 8 interfaces total
- **D-05:** URL paths by resource: `/api/academic/users`, `/api/academic/groups`, `/api/academic/semesters`, `/api/academic/subjects`, `/api/academic/assignments`, `/api/academic/homeworks`, `/api/academic/thresholds`, `/api/academic/dashboard`
- **D-06:** Student-specific endpoints: `GET /api/academic/users/me` (own profile), `GET /api/academic/groups/my/members` (group composition). ID resolved from X-User-Id header, not path variable
- **D-07:** Teacher-specific: `GET /api/academic/assignments/my` (own subjects+groups). ID from X-User-Id

### User Creation & Password
- **D-08:** `POST /api/academic/users` generates login (via PostgreSQL sequence) + random password. Returns plain password ONE TIME in the response. Stores BCrypt hash in `password_hash` column
- **D-09:** Academic Service owns password hashing (BCrypt). Auth Service only reads `password_hash` for verification. No gRPC call to Auth for hashing
- **D-10:** Response DTO for user creation includes `initialPassword` field. Regular GET /users/{id} response does NOT include password

### Semester Operations
- **D-11:** Semester activation: `PATCH /api/academic/semesters/{id}/activate`. Service atomically deactivates any currently active semester and activates the target one in a single @Transactional
- **D-12:** Semester deletion with confirmation: `DELETE /api/academic/semesters/{id}` with request body `{"confirmation": "exact semester name"}`. Service compares confirmation with actual semester name, returns 400 if mismatch

### Cascade Operations
- **D-13:** Headman revoke (`PATCH /api/academic/users/{id}` setting is_headman=false): single @Transactional that clears is_headman flag AND bulk-deactivates all headman_assistants for that user+group
- **D-14:** User soft-delete (status → archived): no cascade needed, @SQLRestriction handles filtering. Assistants of archived users become effectively invisible

### HATEOAS & Pagination (from CLAUDE.md — locked)
- All list endpoints return `PagedModel<EntityModel<T>>` with `_links` (self, next, prev)
- All item endpoints return `EntityModel<T>` with self link
- RepresentationModelAssembler per domain entity
- PUT = full update (all fields required), PATCH = partial update (separate DTO, nullable fields)

### Error Handling (from CLAUDE.md — locked)
- RFC 7807 Problem Details via `@ControllerAdvice`
- Controllers throw exceptions (ResourceNotFoundException, AccessDeniedException, etc.)
- GlobalExceptionHandler maps to appropriate HTTP status + Problem Details body

### Claude's Discretion
- DTO field naming and granularity within each domain
- Service layer internal structure (one service per domain vs split)
- Assembler implementation details
- Pagination default size and max limits
- Validation constraints on request DTOs

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Database Schema & Entities
- `docs/database-schema.md` — Full schema for all academic_db tables, constraints, indexes
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/` — All 11 JPA entities (Phase 5 output)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/` — All 11 repositories with query methods

### Contract Module (existing)
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/enums/` — UserRole, AccountStatus, SubjectType, AssistantPermission
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/exception/ErrorResponse.java` — RFC 7807 response record
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/exception/ResourceNotFoundException.java`

### Auth Service Pattern (reference)
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java` — Controller pattern (though Auth doesn't use contract-first)

### Build & Config
- `services/academic-service/academic-api-contract/build.gradle.kts` — Contract module dependencies
- `services/academic-service/academic-app/build.gradle.kts` — App module dependencies

### Business Rules
- `docs/job-stories.md` — Full job stories by role (JS-ADMIN-01 through JS-SYSTEM-10)
- `CLAUDE.md` — Coding conventions, contract-first rules, REST API rules, role definitions

### Prior Phase Context
- `.planning/phases/05-entity-and-repository-foundation/05-CONTEXT.md` — Entity decisions (soft delete, login generation, permission arrays)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ErrorResponse` record in academic-api-contract — reuse for RFC 7807
- `ResourceNotFoundException` — reuse in services
- All 11 repositories with domain-specific queries — ready for service layer
- `LowercaseEnumConverter` + `EnumConverters` — enum handling already in place
- `@SQLRestriction("status <> 'archived'")` on User entity — soft delete automatic

### Established Patterns
- Contract-first: interfaces in `*-api-contract`, implementations in `*-app`
- No Lombok in contract modules (records for request DTOs, classes for response DTOs)
- Lombok allowed in app module (entities, internal classes)
- Long FK fields, no JPA associations — joins done via repository queries
- Gateway strips `/api` prefix before forwarding to services

### Integration Points
- Gateway routes: need to add `/api/academic/**` route to API Gateway config (port 9091)
- Auth Service reads `users` table — columns id, login, password_hash, role, status, is_headman, group_id, telegram_id are shared contract
- RequestContext populated from Gateway-injected headers (X-User-Id, X-User-Role, X-Group-Id, X-Is-Headman)

</code_context>

<specifics>
## Specific Ideas

- `POST /users` response includes `initialPassword` — this is the ONLY time plain password is exposed. Separate `UserCreatedResponse` DTO
- Semester delete confirmation: `{"confirmation": "Осенний семестр 2025"}` — must match `semester.name` exactly (case-sensitive)
- Headman revoke cascade: use `HeadmanAssistantRepository.deactivateAllByHeadmanIdAndGroupId()` bulk query in same transaction
- Teacher search for assignment: headman searches by `employeeNumber` (tab. number) — need query in UserRepository

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 06-rest-api-hateoas*
*Context gathered: 2026-03-30*
