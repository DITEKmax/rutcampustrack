# Phase 7: gRPC Server - Context

**Gathered:** 2026-03-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement all 7 gRPC RPCs defined in `proto/academic.proto` on port 19091 using `grpc-spring-boot-starter`. Downstream consumers: Schedule Service, Attendance Service, Notification Bot. No new business logic — this is a gRPC facade over existing Phase 6 services and repositories.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
User skipped discussion — all implementation decisions delegated to Claude:
- Service reuse strategy: whether gRPC impl delegates to existing REST services or queries repositories directly
- Error mapping approach: interceptor vs per-method handling for domain exceptions to gRPC status codes
- Testing approach: grpc-spring-boot-starter test support vs raw channels
- Internal structure: single gRPC service class vs split

### Locked from Prior Phases
- **D-01:** gRPC port 19091 (not 9090 — conflicts with Auth Service)
- **D-02:** `grpc-spring-boot-starter` library (pre-planned in Phase 0, commented out in build.gradle.kts)
- **D-03:** No JPA associations — repository queries with Long FK IDs
- **D-04:** Soft delete via `@SQLRestriction` on User — GetGroupMembers automatically filters archived
- **D-05:** Proto file `proto/academic.proto` is the source of truth — 7 RPCs, message types fixed

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### gRPC Contract
- `proto/academic.proto` — 7 RPC definitions, all message types. This is the source of truth

### Existing Services (Phase 6 output)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentService.java`

### Repositories
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/` — all repositories

### Entities
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/` — all JPA entities

### Build Config
- `services/academic-service/academic-app/build.gradle.kts` — gRPC dep commented out, ready to uncomment

### Database Schema
- `docs/database-schema.md` — campus_settings table for GetCampusGeofence (lat, lng, radius_m)

### ROADMAP Phase Details
- `.planning/ROADMAP.md` — Phase 7 success criteria (5 items)

### Business Rules
- `CLAUDE.md` — Coding conventions, package structure

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- All 9 services from Phase 6 — contain business logic for all 7 RPCs
- All 11 repositories with domain-specific queries
- `CampusSettingRepository` — for GetCampusGeofence
- `SemesterService.getActiveSemester()` — likely already exists for GetActiveSemester
- `UserRepository` with soft-delete filtering — for GetGroupMembers

### Established Patterns
- Testcontainers PostgreSQL for integration tests (AbstractAcademicIntegrationTest base class)
- `@SQLRestriction("status <> 'archived'")` on User entity — auto-filtering
- Long FK fields, no JPA associations

### Integration Points
- `build.gradle.kts`: uncomment `net.devh:grpc-spring-boot-starter:3.1.0.RELEASE`
- `application.yml`: add `grpc.server.port: 19091`
- Proto compilation: need `protobuf` Gradle plugin to generate Java stubs from `proto/academic.proto`

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 07-grpc-server*
*Context gathered: 2026-03-30*
