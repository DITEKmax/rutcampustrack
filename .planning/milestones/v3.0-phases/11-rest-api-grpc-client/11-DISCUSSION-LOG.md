# Phase 11: REST API + gRPC Client - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-01
**Phase:** 11-rest-api-grpc-client
**Areas discussed:** gRPC client setup, Template CRUD scope, Lesson operations, Schedule view response

---

## gRPC Client Setup

### Q1: How to add the gRPC client?

| Option | Description | Selected |
|--------|-------------|----------|
| grpc-client-spring-boot-starter | Same library as academic-service's gRPC server. Auto-configures channels, health checks, interceptors. | ✓ |
| Manual ManagedChannel | Raw gRPC channel construction. More control, fewer dependencies, but boilerplate. | |
| You decide | Claude picks based on codebase patterns. | |

**User's choice:** grpc-client-spring-boot-starter
**Notes:** None

### Q2: When Academic Service is unavailable?

| Option | Description | Selected |
|--------|-------------|----------|
| Reject with 503 | Template creation fails. FK integrity is non-negotiable. | ✓ |
| Allow with warning | Create template anyway, log warning. Risks orphaned references. | |
| Retry then reject | Retry once, then 503. Adds resilience for transient blips. | |

**User's choice:** Reject with 503
**Notes:** None

### Q3: How to test gRPC client?

| Option | Description | Selected |
|--------|-------------|----------|
| @MockitoBean the wrapper | Create AcademicGrpcClient wrapper, mock in tests. | ✓ |
| Embedded gRPC server | Real gRPC server with in-process transport. Higher fidelity. | |
| You decide | Claude picks. | |

**User's choice:** @MockitoBean the wrapper
**Notes:** None

### Q4: Proto stubs timing?

| Option | Description | Selected |
|--------|-------------|----------|
| Add in Phase 11 | Needs client stubs NOW. Phase 14 adds server stubs. | ✓ |
| Defer to Phase 14 | All proto infra at once. Phase 11 uses hand-written client. | |
| You decide | Claude picks. | |

**User's choice:** Add in Phase 11
**Notes:** None

### Q5: Which Academic Service RPCs needed?

| Option | Description | Selected |
|--------|-------------|----------|
| GetGroup | Validate group_id exists and active | ✓ |
| GetActiveSemester | Get semester date range, validate semester_id | ✓ |
| IsHeadman | Verify headman for specific group | ✓ |
| GetUserById | Validate teacher_id has TEACHER role | |

**User's choice:** GetGroup, GetActiveSemester, IsHeadman (not GetUserById)
**Notes:** teacher_id trusted — headman selects from known list

---

## Template CRUD Scope

### Q1: Which fields validated via gRPC?

| Option | Description | Selected |
|--------|-------------|----------|
| group_id + semester_id | Validate group (GetGroup) + semester (GetActiveSemester). teacher/subject trusted. | ✓ |
| All 4 FK fields | Maximum safety but 3-4 gRPC calls. subject_id needs new RPC. | |
| group_id only + IsHeadman | Minimal validation. | |

**User's choice:** group_id + semester_id
**Notes:** None

### Q2: Template deletion mode?

| Option | Description | Selected |
|--------|-------------|----------|
| Soft delete: is_active=false | Matches CLAUDE.md. Existing lessons remain. | ✓ |
| Hard delete + cascade | DELETE CASCADE. Destructive but clean. | |
| Soft delete + cancel future | is_active=false + cancel planned lessons. | |

**User's choice:** Soft delete: is_active=false
**Notes:** None

### Q3: Headman authorization check?

| Option | Description | Selected |
|--------|-------------|----------|
| IsHeadman gRPC call | Verify headman for THIS specific group. Extra call but proper auth. | ✓ |
| Trust X-Is-Headman header | Faster but doesn't check which group. | |
| Header + group_id match | Trust header, verify group_id match. No gRPC. | |

**User's choice:** IsHeadman gRPC call
**Notes:** None

### Q4: Template update (PUT) scope?

| Option | Description | Selected |
|--------|-------------|----------|
| All fields except group_id/semester_id | Re-validate ownership. Can't change group or semester. | ✓ |
| All fields updatable | Requires lesson regeneration (Phase 12 territory). | |
| Room and time only | Minimal — delete+recreate for other changes. | |

**User's choice:** All fields except group_id/semester_id
**Notes:** None

---

## Lesson Operations

### Q1: Who can cancel/restore?

| Option | Description | Selected |
|--------|-------------|----------|
| HEADMAN + ADMIN | Headman for own group, Admin for any group. | ✓ |
| HEADMAN only | Admin doesn't manage individual lessons. | |
| HEADMAN + ADMIN + TEACHER | Teachers cancel own lessons. More complex. | |

**User's choice:** HEADMAN + ADMIN
**Notes:** None

### Q2: Mass-cancel design?

| Option | Description | Selected |
|--------|-------------|----------|
| Date range for a group | POST with group_id, date_from, date_to, reason. Simple. | ✓ |
| List of lesson IDs | More granular, frontend selects. | |
| Date range + subject filter | Date range with optional subject_id. More flexible. | |

**User's choice:** Date range for a group
**Notes:** None

### Q3: Cancel reason required?

| Option | Description | Selected |
|--------|-------------|----------|
| Required | Always require reason. Audit trail. | ✓ |
| Optional | Simpler UX. | |
| Required for mass, optional for single | Differentiated. | |

**User's choice:** Required
**Notes:** None

### Q4: Restore behavior?

| Option | Description | Selected |
|--------|-------------|----------|
| Only cancelled -> planned | Clear cancel_reason. Active/closed cannot restore. | ✓ |
| Cancelled -> original status | Track pre-cancel status. More complex. | |
| Cancelled -> planned always | Also allow restoring closed lessons. | |

**User's choice:** Only cancelled -> planned
**Notes:** None

---

## Schedule View Response

### Q1: Enrichment with names?

| Option | Description | Selected |
|--------|-------------|----------|
| IDs only | Frontend resolves names. Keeps service decoupled. | ✓ |
| Enriched with names | gRPC calls to resolve. Couples to Academic availability. | |
| IDs + cached names | Local cache. Adds invalidation complexity. | |

**User's choice:** IDs only
**Notes:** None

### Q2: Response structure?

| Option | Description | Selected |
|--------|-------------|----------|
| Flat lesson list with HATEOAS | PagedModel<EntityModel<LessonResponse>>. Standard pattern. | ✓ |
| Grouped by date | Map<LocalDate, List<LessonResponse>>. Breaks HATEOAS. | |
| Grouped with HATEOAS wrapper | CollectionModel<EntityModel<DayScheduleResponse>>. Custom. | |

**User's choice:** Flat lesson list with HATEOAS
**Notes:** None

### Q3: Status filtering?

| Option | Description | Selected |
|--------|-------------|----------|
| Optional status filter | ?status=planned,active,closed. Exclude cancelled by default. | ✓ |
| Return all statuses | Always return everything. Frontend filters. | |
| Never return cancelled | Separate endpoint for headman. | |

**User's choice:** Optional status filter
**Notes:** None

---

## Claude's Discretion

- DTO structure for requests/responses
- Service layer organization
- HATEOAS link structure
- Exact endpoint paths
- PUT vs PATCH decision
- Pagination defaults

## Deferred Ideas

None — discussion stayed within phase scope.
