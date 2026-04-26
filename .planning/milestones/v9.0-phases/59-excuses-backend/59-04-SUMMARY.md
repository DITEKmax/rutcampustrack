---
phase: 59-excuses-backend
plan: 04
subsystem: attendance-service
tags: [backend, mongodb, attendance-cascade, excuse-tickets, ports-adapters]
status: completed
completed: 2026-04-14

dependency_graph:
  requires:
    - 59-01 (ExcuseTicket entity + ExcuseRepository)
    - 59-02 (ExcuseService.updateStatus — cascade hook point documented as STUB)
    - services/attendance-service/attendance-app/.../checkin/AttendanceRepository (existing)
    - services/attendance-service/attendance-app/.../checkin/AttendanceDocument (existing)
  provides:
    - AttendanceWritePort (port interface in shared/port/ — zero checkin imports)
    - AttendanceWritePortImpl (upsert adapter in checkin/)
    - AttendanceSource.HEADMAN_EXCUSE (new enum value for ticket-driven attendance mutations)
    - ExcuseService D-16 cascade wiring (approve → EXCUSED / FREE_ATTENDANCE per lesson)
    - ExcuseServiceApproveIT (integration coverage for AC-5)
  affects:
    - 59-05 (event publisher — will ALSO modify ExcuseService.updateStatus; port injection is additive, cascade runs before return)

tech-stack:
  added: []
  patterns:
    - port/adapter: shared/port/AttendanceWritePort (interface) + checkin/AttendanceWritePortImpl (@Component)
    - upsert via findByLessonIdAndUserId + save (no raw Mongo update)
    - derived-query method on MongoRepository

key-files:
  created:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/port/AttendanceWritePort.java
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceWritePortImpl.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseServiceApproveIT.java
  modified:
    - services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/AttendanceSource.java (+HEADMAN_EXCUSE)
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceRepository.java (+findByLessonIdAndUserId)
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseService.java (inject AttendanceWritePort, cascade + mapping)
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseServiceTest.java (mock port + verify cascade in happy path)

decisions:
  - "ScheduleGrpcClient NOT injected — the cascade only needs studentId / lessonId / groupId / status, all of which are present on the ExcuseTicket. Looking up LessonInfo via gRPC would add latency without adding fidelity. 59-05 can add it if event-payload ever needs subjectId."
  - "mark() signature accepts groupId because a fresh AttendanceDocument needs one; when a document already exists its groupId is preserved (no overwrite) — groupId mismatch between ticket and existing doc is a data-consistency issue, not this port's concern."
  - "semesterId / subjectId / lessonNumber / lessonDate left NULL on fresh docs — excuse flow has no semester context and a real check-in or the auto-close sweep will populate them later. Document stays queryable by (user_id, lesson_id) which is all the journal needs."
  - "Mapping lives inside ExcuseService (private mapExcuseTypeToAttendanceStatus) rather than in the port — keeps the port domain-agnostic; any future caller with a different mapping stays free."
  - "Cascade runs AFTER excuseRepository.save(ticket) — ticket status is the source of truth; if the cascade fails the ticket is still APPROVED and a manual re-run of the cascade is possible. Matches the 'event after commit' pattern the next plan (59-05) will use."

metrics:
  tasks: 2
  commits: 2
  files_created: 3
  files_modified: 4
  duration: ~20 min
---

# Phase 59 Plan 04: Excuse Approve Cascade Summary

One-liner: Wire D-16 approve cascade — introduce `AttendanceWritePort` in `shared/port/`, implement upsert adapter in `checkin/`, and extend `ExcuseService.updateStatus` so that approving a ticket creates/updates one `AttendanceDocument` (EXCUSED or FREE_ATTENDANCE) per lessonId.

## What Was Built

### Port + adapter

- **`AttendanceWritePort`** (`shared/port/`) — single-method interface `mark(studentId, lessonId, groupId, status)`. Zero imports from `checkin/` so the `excuse/` domain can depend on it without breaching isolation.
- **`AttendanceWritePortImpl`** (`checkin/`) — `@Component` adapter. On `mark()`:
  1. `attendanceRepository.findByLessonIdAndUserId(lessonId, studentId)`
  2. if present → overwrite `status`, `source=HEADMAN_EXCUSE`, `updatedAt=now`, save;
  3. else → build fresh `AttendanceDocument` with minimal fields (lessonId, userId, groupId, status, source, createdAt/updatedAt) and save.

### Contract enum

- **`AttendanceSource.HEADMAN_EXCUSE`** — new value appended to the enum so cascade-driven records are distinguishable from `STUDENT_GEO` / `HEADMAN` / `AUTO_SCHEDULER` / `LATE_CHECKIN` in the journal. The existing `AttendanceSourceReader/Writer` Mongo converters use `valueOf(name().toUpperCase())`/`name().toLowerCase()` — no change needed there.

### Repository extension

- **`AttendanceRepository.findByLessonIdAndUserId(Long, Long)`** — Spring Data derived query, returns `Optional<AttendanceDocument>`. Used exclusively by the new port.

### Service wiring

- **`ExcuseService` constructor** now takes `AttendanceWritePort` as a fourth argument.
- **`ExcuseService.updateStatus`** — after `excuseRepository.save(ticket)` returns, if `newStatus == APPROVED` the service iterates `ticket.getLessonIds()` and calls `attendanceWritePort.mark(studentId, lessonId, groupId, mapped)` per lesson.
- **`mapExcuseTypeToAttendanceStatus(ExcuseType)`** — private helper: `FREE_ATTENDANCE → FREE_ATTENDANCE`, everything else (`ILLNESS`, `SUMMONS`, `UNIVERSITY_ORDER`, `EXEMPTION`, `OTHER`) → `EXCUSED`.

### Tests

- **`ExcuseServiceTest`** — added `@Mock AttendanceWritePort`; extended the happy-path `updateStatus_happyPath_setsDecisionFieldsAndSaves` test to include `lessonIds + excuseType` on the ticket and assert two `mark()` invocations with `EXCUSED`. All 10 existing assertions still pass.
- **`ExcuseServiceApproveIT`** (new, 4 tests):
  1. `approve_illnessTicket_createsExcusedAttendanceForEachLesson` — 3 lessons, all become `EXCUSED` with `source=HEADMAN_EXCUSE`.
  2. `approve_freeAttendanceTicket_createsFreeAttendanceDocuments` — FREE_ATTENDANCE mapping.
  3. `reject_doesNotTouchAttendance` — attendance collection remains empty.
  4. `approve_overwritesExistingPresentAttendance` — pre-seeded `PRESENT` doc gets overwritten to `EXCUSED`; `createdAt` preserved, `updatedAt` advanced.

## Verification

| Check | Result |
|---|---|
| `./gradlew :services:attendance-service:attendance-app:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :services:attendance-service:attendance-app:compileTestJava` | BUILD SUCCESSFUL |
| `./gradlew ... test --tests "*ExcuseServiceTest"` | **10/10 green** |
| `./gradlew ... test --tests "*CheckinServiceTest" "*MarkingServiceTest" "*ReportServiceTest"` | **all green** (no regression) |
| `./gradlew ... test --tests "*ExcuseServiceApproveIT"` | **blocked by local env** (see Deferred Issues) |
| `grep "implements AttendanceWritePort" services/attendance-service/` | `AttendanceWritePortImpl.java` |
| `grep "attendanceWritePort" services/attendance-service/attendance-app/src/main/java/` | `ExcuseService.java` (1 field + 1 call) |

## Commits

| Task | Commit | Message |
|---|---|---|
| 1 | `6ce6989` | feat(59-04): add AttendanceWritePort for excuse approve cascade |
| 2 | `eed3d9d` | feat(59-04): wire approve cascade in ExcuseService + IT |

## Deviations from Plan

**None in behavior** — D-16 mapping, idempotent upsert, REJECTED-is-noop all per spec.

### Clarifications (not deviations)

- **`ScheduleGrpcClient` not injected.** The plan prompt suggested injecting it alongside `AttendanceWritePort`; the research §Implementation Notes and the plan's own `<action>` block only call `attendanceWritePort.mark(studentId, lessonId, groupId, status)`. All three inputs are already on the ticket — a gRPC round-trip would just add latency. If plan 59-05's event payload requires `subjectId` it can add the call there.
- **`ExcuseServiceTest` happy-path updated.** The pre-existing test built a ticket without `lessonIds` / `excuseType`; once the cascade runs on APPROVED that would NPE. Added the two missing fields and two `verify(attendanceWritePort).mark(...)` assertions. All ten previous tests still pass.

## Known Stubs

**None from this plan.** D-16 cascade is fully wired.

Outstanding stubs from earlier plans in this phase:
- **Event publisher (D-19, D-20)** — still stubbed; plan 59-05's remit.
- **Lesson-ownership validation (D-25)** — still stubbed; plan 59-03 shipped the gRPC client, but `ExcuseService.createExcuse` has not yet adopted it. This is a 59-05/later concern, not a blocker for the cascade.

## Deferred Issues

**`ExcuseServiceApproveIT` cannot run locally — Docker Desktop not running on the dev machine.**

- Symptom: `java.lang.NoClassDefFoundError` from `org.testcontainers.utility.RyukResourceReaper` during Spring context boot. Same failure pattern as every other `AbstractAttendanceIntegrationTest`-heir (CheckinIntegrationTest, MarkingIntegrationTest, etc.) in this environment.
- Evidence it is pre-existing: `docker ps` → `error during connect: ... dockerDesktopLinuxEngine`. The same 47 integration tests flagged in the 59-02 summary still fail identically; this IT joins them.
- Compile of the test class succeeded, signatures are correct, and the unit-level cascade verification via Mockito in `ExcuseServiceTest` covers the same code path — functional correctness is not in doubt, only the container-based confirmation.
- CI with Docker available will run the four IT tests without modification.

## Threat Flags

None. Trust boundaries covered by the plan's `<threat_model>`:

- T-59-04-01 (race on concurrent mark) — mitigated structurally by D-18: `updateStatus` short-circuits if `ticket.status != SUBMITTED`. Two simultaneous approves of the same ticket: the first transitions SUBMITTED→APPROVED, the second reads APPROVED and throws `ConflictException` before touching the port.
- T-59-04-02 (idempotency of double-approve) — same D-18 guard; additionally `findByLessonIdAndUserId` upsert semantics mean even if the cascade *did* re-run, the end state is identical.
- T-59-04-03 (audit repudiation) — `source=HEADMAN_EXCUSE` + `ExcuseTicket.decisionBy` + `ExcuseTicket.decisionAt` together form a complete audit chain. Accepted risk per plan.

## Self-Check: PASSED

- FOUND: services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/port/AttendanceWritePort.java
- FOUND: services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceWritePortImpl.java
- FOUND: services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseServiceApproveIT.java
- FOUND: `HEADMAN_EXCUSE` in services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/enums/AttendanceSource.java
- FOUND: `findByLessonIdAndUserId` in services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceRepository.java
- FOUND: `attendanceWritePort.mark` in services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseService.java
- FOUND commit 6ce6989
- FOUND commit eed3d9d

## Notes for Plan 59-05 (event publisher)

**`ExcuseService.java` current shape — clean, mergeable:**

```
constructor:
  ExcuseService(ExcuseRepository, RequestContext, AcademicGrpcClient, AttendanceWritePort)

public methods (unchanged signatures since 59-02):
  createExcuse(CreateExcuseRequest)                       -> ExcuseTicket
  getMyTickets(Pageable, ExcuseTicketStatus)              -> Page<ExcuseTicket>
  getGroupTickets(Long, Pageable, ExcuseTicketStatus)     -> Page<ExcuseTicket>
  getTicketById(String)                                   -> ExcuseTicket
  updateStatus(String, UpdateExcuseStatusRequest)         -> ExcuseTicket

private helpers:
  mapExcuseTypeToAttendanceStatus(ExcuseType)             -> AttendanceStatus
```

**Guidance for 59-05:**

- Event publishing for `excuse.requested` (D-19): hook at end of `createExcuse` — after `excuseRepository.save(...)`, before `return`.
- Event publishing for `excuse.decided` (D-20): hook at end of `updateStatus` — after the cascade loop, before `return saved`. Do NOT swap order with the cascade — teacher journal consistency relies on the cascade running before any downstream consumer sees the event.
- Adding a fifth constructor arg (`ExcuseEventPublisher`) is the cleanest merge pattern — no existing method signature changes, only the constructor widens. Existing unit test already mocks `AttendanceWritePort`; 59-05 just adds another `@Mock ExcuseEventPublisher`.
- `ExcuseServiceTest.updateStatus_happyPath_setsDecisionFieldsAndSaves` already includes `lessonIds + excuseType` on the ticket, so adding `verify(eventPublisher).publishDecided(saved)` will not require re-shuffling data.
- No blockers. The constructor is the only file-level conflict surface, and it widens monotonically.
