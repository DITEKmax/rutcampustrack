# Phase 17: Write Path — Geo-Checkin + Manual Marking - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md -- this log preserves the alternatives considered.

**Date:** 2026-04-04
**Phase:** 17-write-path-geo-checkin-manual-marking
**Areas discussed:** Geo-checkin API design, Manual marking flow, Redis dedup & rate limit, Event publishing

---

## Geo-Checkin API Design

### Endpoint Shape

| Option | Description | Selected |
|--------|-------------|----------|
| POST /api/attendance/checkin | Body: {lat, lng}. Service resolves active lesson via gRPC | ✓ |
| POST /api/attendance/lessons/{lessonId}/checkin | Lesson ID in URL. Client must know lesson | |

**User's choice:** POST /api/attendance/checkin
**Notes:** Clean -- student sends only coordinates, service resolves everything

### Time Window Enforcement

| Option | Description | Selected |
|--------|-------------|----------|
| Server-side from LessonResponse | Compute +/-5 min buffer, validate against server clock | ✓ |
| Schedule Service checks in gRPC | getActiveLesson already filters by window | |

**User's choice:** Server-side from LessonResponse
**Notes:** Single source of truth, no client manipulation

### Checkin Response

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal: {status, lesson_id, timestamp} | Lightweight for Mini App | |
| Full EntityModel with HATEOAS links | Consistent with project convention | |
| Compact EntityModel (hybrid) | status, lessonId, timestamp + _links. HATEOAS Level 3 without exposing full Mongo doc | ✓ |

**User's choice:** Compact EntityModel -- HATEOAS wrapper around minimal fields
**Notes:** User provided detailed rationale: (1) project already uses HATEOAS Level 3 throughout, (2) Mini App reads only what it needs from the response, (3) not exposing full AttendanceDocument which may change as excuses/late-checkin are added, (4) full resource comes later via GET endpoint

### Error Handling

| Option | Description | Selected |
|--------|-------------|----------|
| RFC 7807 Problem Details | 422/404/403/409/429 via ErrorResponse + GlobalExceptionHandler | ✓ |
| Custom error body | Reason enum (OUTSIDE_GEOFENCE etc.) for Mini App | |

**User's choice:** RFC 7807 Problem Details

### Geofence Architecture

| Option | Description | Selected |
|--------|-------------|----------|
| GeoUtils static utility in checkin/ | Pure math, no caching | |
| GeofenceService with cache + GeoUtils inside | 3-layer: gRPC client -> service (cache) -> utils (math) | ✓ |

**User's choice:** GeofenceService with internal GeoUtils
**Notes:** User provided detailed architecture breakdown: (1) AcademicGrpcClient = transport only, (2) GeofenceService = isWithinCampus() + cache (like SemesterCacheService), (3) GeoUtils = package-private pure math. No external geo libraries. In-memory volatile cache with TTL, not Redis.

### Request DTO

| Option | Description | Selected |
|--------|-------------|----------|
| CheckinRequest record in api-contract | Matches project rule: Request DTO = record | ✓ |
| @RequestParam | lat/lng as query params | |

**User's choice:** CheckinRequest record

### Group ID Source

| Option | Description | Selected |
|--------|-------------|----------|
| From RequestContext | Gateway already provides X-Group-Id | ✓ |
| gRPC to Academic Service | Query group by userId | |

**User's choice:** From RequestContext

---

## Manual Marking Flow

### Endpoint Shape

| Option | Description | Selected |
|--------|-------------|----------|
| PUT /api/attendance/lessons/{lessonId}/students/{userId} | Body: {status}. RESTful addressing | ✓ |
| PATCH /api/attendance/lessons/{lessonId}/students/{userId} | Partial update semantics | |
| POST /api/attendance/mark | Body: {lessonId, userId, status}. Simpler but less RESTful | |

**User's choice:** PUT with path params

### Authorization Check

| Option | Description | Selected |
|--------|-------------|----------|
| RequestContext.isHeadman() + groupId match | No extra gRPC call | ✓ |
| gRPC isHeadman() to Academic | More secure but +latency | |

**User's choice:** RequestContext

### Allowed Statuses

| Option | Description | Selected |
|--------|-------------|----------|
| PRESENT, ABSENT, EXCUSED, FREE_ATTENDANCE | All except CANCELLED (system-only) | ✓ |
| Only PRESENT and ABSENT | Minimal, EXCUSED via excuse tickets later | |

**User's choice:** All four non-system statuses

### Upsert vs Update

| Option | Description | Selected |
|--------|-------------|----------|
| Upsert | Create doc if not exists. Headman can mark before lesson.closed | ✓ |
| Update only | Doc must exist (from checkin or auto-absent). 404 if not | |

**User's choice:** Upsert

### Student Group Validation

| Option | Description | Selected |
|--------|-------------|----------|
| gRPC getGroupMembers | Verify userId in headman's group | ✓ |
| Just lesson.groupId == context.groupId | Only check lesson belongs to group | |

**User's choice:** gRPC getGroupMembers

### Headman Assistants

| Option | Description | Selected |
|--------|-------------|----------|
| Defer to v4.1+ | Only is_headman=true in this phase | ✓ |
| Implement now | Check headman_assistants via Academic gRPC | |

**User's choice:** Defer

### Mark Response

| Option | Description | Selected |
|--------|-------------|----------|
| Compact EntityModel | status, lessonId, userId, timestamp + _links. 200 OK | ✓ |
| 204 No Content | No body -- autosave fire-and-forget | |

**User's choice:** Compact EntityModel

---

## Redis Dedup & Rate Limit

### Key Schema

| Option | Description | Selected |
|--------|-------------|----------|
| Separate keys | dedup: attendance:dedup:{lessonId}:{userId} (5s), rate: attendance:rate:{userId} (60s INCR) | ✓ |
| Single composite key | attendance:checkin:{userId}:{lessonId} with compound value | |

**User's choice:** Separate keys

### Service Placement

| Option | Description | Selected |
|--------|-------------|----------|
| CheckinRateLimiter @Service | acquireDedup() + checkRateLimit(). Isolated from business code | ✓ |
| Inline in CheckinService | Less classes but mixed responsibilities | |

**User's choice:** CheckinRateLimiter @Service

### Rate Limit Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Geo-checkin only | Don't rate limit headman manual marking | ✓ |
| Both endpoints | Protect both paths | |

**User's choice:** Geo-checkin only

---

## Event Publishing

### When to Publish

| Option | Description | Selected |
|--------|-------------|----------|
| Both: checkin + manual mark | Per INFRA-06: after any successful attendance write | ✓ |
| Geo-checkin only | Only student-initiated events | |

**User's choice:** Both paths

### Sync vs Async

| Option | Description | Selected |
|--------|-------------|----------|
| Synchronous (same thread) | RabbitTemplate.convertAndSend() after MongoDB upsert. Fail-loud | ✓ |
| Async (@Async) | Non-blocking but fire-and-forget risk | |

**User's choice:** Synchronous

### Publisher Location

| Option | Description | Selected |
|--------|-------------|----------|
| AttendanceEventPublisher @Service | publishMarked(doc). Shared by both services | ✓ |
| Inline in each service | Less classes but duplication | |

**User's choice:** AttendanceEventPublisher @Service

---

## Claude's Discretion

- Internal structure of CheckinService / MarkingService
- MongoTemplate vs AttendanceRepository for upsert
- Test structure and scenario count
- Redis configuration bean design
- GeofenceService cache TTL exact value (10-60 min)

## Deferred Ideas

- Headman assistants -- v4.1+ (JS-HEADMAN-14)
- Late checkin flow -- separate phase (JS-STUDENT-06)
- Excuse tickets -- v4.1+ (JS-STUDENT-03/04)
- GET /api/attendance/checkins/{id} -- Phase 18 (Read Path)
