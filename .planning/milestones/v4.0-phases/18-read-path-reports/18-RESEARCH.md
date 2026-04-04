# Phase 18: Read Path — Reports - Research

**Researched:** 2026-04-04
**Domain:** Spring Boot / MongoDB aggregation / ArchUnit / HATEOAS read-path reporting
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** Separate `/reports/*` prefix for all report endpoints. Clean separation from write path (`/attendance/*`).

**D-02:** Four endpoints in scope:
- `GET /reports/lesson/{lessonId}` (RPRT-01) — lesson attendance list, all group members with status
- `GET /reports/journal` (RPRT-02) — journal grid, query params: groupId, subjectId, dateFrom, dateTo
- `GET /reports/student/stats` (RPRT-03) — own attendance stats per subject
- `GET /reports/student/records` (RPRT-04) — own raw attendance list, filterable by subject

**D-03:** `GET /reports/semester/{id}/summary`, `GET /reports/group/{id}/top-skippers`, `GET /reports/export` are DEFERRED to v4.1+.

**D-04:** Lesson attendance (RPRT-01) returns ALL group members via `academicGrpcClient.getGroupMembers()` left-joined with attendance records. Students with no record appear as status=absent.

**D-05:** Authorization: role + group check for headman (requestContext.groupId must match lesson's groupId) AND teacher (verify via `academicGrpcClient.getTeacherSubjects()` that teacher teaches that subject for that group). Prevents data leakage.

**D-06:** Student endpoints (stats, records) use `requestContext.getUserId()` — no path param for userId (prevents enumeration).

**D-07:** Journal grid nested structure: top-level `dates[]` array + `students[]` array where each student has `records[]` with date, lessonNumber, status.

**D-08:** Status representation includes BOTH enum name and Russian symbol: `{ status: "present", symbol: "б" }`.

**D-09:** Student names resolved via gRPC: `academicGrpcClient.getGroupMembers()` returns member IDs + names.

**D-10:** "Attended" = PRESENT + EXCUSED + FREE_ATTENDANCE. Only ABSENT counts against the student.

**D-11:** CANCELLED lessons excluded from denominator entirely.

**D-12:** On-the-fly MongoDB aggregation at query time. No pre-materialized views.

**D-13:** Stats response per subject: `{ subjectId, subjectName, total, attended, absent, excused, percentage }`. Plus `overall` object. Subject names resolved via gRPC.

**D-14:** `AttendanceReadPort` interface in `shared/port/` package. Implementation `AttendanceReadPortImpl` in `checkin/` package.

**D-15:** Port methods: `findByLessonId(Long)`, `findByUserId(Long, Long semesterId)`, `findByGroupAndSubject(Long groupId, Long subjectId, LocalDate from, LocalDate to)`.

**D-16:** `AttendanceRecord` — read-only DTO in `shared/port/`. Fields: lessonId, userId, groupId, subjectId, lessonDate, lessonNumber, status, source.

**D-17:** ArchUnit test enforces no class in `report/` package imports any class from `checkin/` package.

### Claude's Discretion

- ReportService internal structure and method decomposition
- MongoDB aggregation pipeline specifics for stats computation
- Contract DTO naming and exact field names (ReportApi interfaces)
- Test structure: how many integration tests, which scenarios to prioritize
- Whether to use single ReportService or separate LessonReportService/JournalService/StatsService
- Pagination strategy for student records endpoint

### Deferred Ideas (OUT OF SCOPE)

- Semester summary endpoint (`GET /reports/semester/{id}/summary`) — admin dashboard feature, v4.1+
- Top-skippers endpoint (`GET /reports/group/{id}/top-skippers`) — v4.1+
- PDF/Excel export (`GET /reports/export?format=...`) — v4.1+
- Weekly trend charts for student stats — v4.1+
- Redis caching for stats queries — optimize later if scale demands it
- Subject rating (most/least skipped) — v4.1+
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| RPRT-01 | Headman/teacher can view lesson attendance (all students + their status for a lesson) | D-04 defines full roster left-join; D-05 defines auth; `getGroupMembers` + `getLessonById` gRPC calls already exist in AcademicGrpcClient/ScheduleGrpcClient |
| RPRT-02 | Headman/teacher can view journal (students x lesson dates grid for group+subject) | D-07/D-08/D-09 define grid shape; `getLessonsByGroup` already in ScheduleGrpcClient; `findByGroupAndSubject` port method covers MongoDB query |
| RPRT-03 | Student can view own attendance stats (% per subject, excluding cancelled) | D-10/D-11/D-12/D-13 define calculation rules; `findByUserId` port method covers MongoDB read; subject name resolution via AcademicGrpcClient.getTeacherSubjects is NOT needed here — need new gRPC call or use schedule data |
| RPRT-04 | Student can view own attendance list (raw records, filterable by subject) | `findByUserId` port method covers MongoDB read; pagination/filtering via query params |
| RPRT-05 | Report domain accesses checkin data only through AttendanceReadPort (domain isolation) | D-14..D-17 define full isolation architecture; ArchUnit `noClasses().that().resideInAPackage("..report..")` rule covers enforcement |
</phase_requirements>

---

## Summary

Phase 18 implements the read-path of the Attendance Service: four REST endpoints under `/reports/` providing attendance data to headmen, teachers, and students. All functionality lives in a new `report/` package within `attendance-app`. The critical architectural constraint (RPRT-05) is that the `report/` package never imports from `checkin/` directly — communication flows through an `AttendanceReadPort` interface in `shared/port/`, with the implementation living in `checkin/`.

The codebase is mature: MongoDB indexes for all report queries are already provisioned (Phase 15), the gRPC clients are established, the `RequestContext`/`RequireRole` security pattern is proven, and the `AbstractAttendanceIntegrationTest` base class provides Testcontainers infrastructure reuse. This phase is primarily additive — no existing code requires modification except adding `AttendanceReadPortImpl` in the `checkin/` package and adding `getTeacherSubjects()` to `AcademicGrpcClient`.

The one gap to address: `GetTeacherSubjects` exists in `academic.proto` but is NOT yet implemented in `AcademicGrpcClient.java`. Teacher authorization for RPRT-01 and RPRT-02 depends on this method. The plan must include adding it to the gRPC client.

**Primary recommendation:** Build `AttendanceReadPort` + `AttendanceReadPortImpl` first, then `ReportService`, then contract DTOs + `ReportApi` interface, then `ReportController`, then integration tests, then ArchUnit test.

---

## Standard Stack

### Core (already in build.gradle.kts — no new dependencies needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot Data MongoDB | 3.4.x (BOM) | MongoTemplate queries, criteria API | Already used for write path |
| Spring Boot HATEOAS | 3.4.x (BOM) | `EntityModel<T>`, `CollectionModel<T>`, `PagedModel<T>` | Project-wide pattern for all REST responses |
| Spring Boot Web | 3.4.x (BOM) | `@RestController`, `@RequestMapping`, `@RequestParam` | Project-wide |
| springdoc-openapi | 2.7.0 | `@Operation`, `@ApiResponse`, `@Parameter` Swagger annotations | Already in contract modules |

### New Dependency Required

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| ArchUnit JUnit5 | 1.3.0 | `noClasses().that().resideInAPackage(...).should().dependOnClassesThat()` | RPRT-05 enforcement — not currently in build.gradle.kts |

**Installation (add to `attendance-app/build.gradle.kts` testImplementation):**
```bash
# Verify current version
# As of research date: 1.3.0 is latest stable
```
```kotlin
testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
```

### No Alternatives to Consider

All other libraries are locked by existing project patterns. Do not introduce new libraries for this phase.

---

## Architecture Patterns

### Recommended Package Structure (additions to existing)

```
attendance-app/src/main/java/ru/rutcampustrack/attendance/
├── checkin/
│   ├── AttendanceDocument.java          (existing)
│   ├── AttendanceRepository.java        (existing)
│   ├── CheckinService.java              (existing)
│   ├── CheckinController.java           (existing)
│   └── AttendanceReadPortImpl.java      (NEW — implements shared/port/AttendanceReadPort)
├── shared/
│   └── port/
│       ├── AttendanceReadPort.java      (NEW — interface, no checkin imports)
│       └── AttendanceRecord.java        (NEW — read-only DTO, no checkin imports)
├── report/
│   ├── ReportService.java               (NEW)
│   └── ReportController.java            (NEW — implements ReportApi from contract)
└── grpc/
    └── AcademicGrpcClient.java          (MODIFY — add getTeacherSubjects())

attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/
├── api/
│   └── ReportApi.java                   (NEW — GET mappings, Swagger annotations)
└── dto/
    └── report/
        ├── LessonAttendanceResponse.java (NEW — extends RepresentationModel)
        ├── StudentAttendanceEntry.java   (NEW — plain class, no RepresentationModel)
        ├── JournalResponse.java          (NEW — extends RepresentationModel)
        ├── JournalStudentRow.java        (NEW — plain class)
        ├── JournalCell.java              (NEW — plain class, status + symbol)
        ├── StudentStatsResponse.java     (NEW — extends RepresentationModel)
        ├── SubjectStats.java             (NEW — plain class)
        ├── OverallStats.java             (NEW — plain class)
        ├── StudentRecordsResponse.java   (NEW — extends RepresentationModel for paged)
        └── AttendanceRecordEntry.java    (NEW — plain class)
```

### Pattern 1: AttendanceReadPort Isolation (RPRT-05)

**What:** An interface in `shared/port/` acts as the only bridge between `report/` (consumer) and `checkin/` (data owner). The interface and its read DTO contain zero imports from `checkin/`.

**When to use:** Any time `report/` needs attendance data.

```java
// shared/port/AttendanceReadPort.java
// Source: CONTEXT.md D-14, D-15, D-16
package ru.rutcampustrack.attendance.shared.port;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceReadPort {
    List<AttendanceRecord> findByLessonId(Long lessonId);
    List<AttendanceRecord> findByUserId(Long userId, Long semesterId);
    List<AttendanceRecord> findByGroupAndSubject(Long groupId, Long subjectId,
                                                  LocalDate from, LocalDate to);
}
```

```java
// shared/port/AttendanceRecord.java — read-only DTO, NO checkin imports
package ru.rutcampustrack.attendance.shared.port;

import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import java.time.LocalDate;

public record AttendanceRecord(
    Long lessonId,
    Long userId,
    Long groupId,
    Long subjectId,
    LocalDate lessonDate,
    Integer lessonNumber,
    AttendanceStatus status,
    AttendanceSource source
) {}
```

```java
// checkin/AttendanceReadPortImpl.java — lives in checkin/, has checkin imports
package ru.rutcampustrack.attendance.checkin;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import java.time.LocalDate;
import java.util.List;

@Component
public class AttendanceReadPortImpl implements AttendanceReadPort {

    private final MongoTemplate mongoTemplate;

    public AttendanceReadPortImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<AttendanceRecord> findByLessonId(Long lessonId) {
        Query q = Query.query(Criteria.where("lesson_id").is(lessonId));
        return mongoTemplate.find(q, AttendanceDocument.class)
                .stream().map(this::toRecord).toList();
    }

    @Override
    public List<AttendanceRecord> findByUserId(Long userId, Long semesterId) {
        Query q = Query.query(
            Criteria.where("user_id").is(userId).and("semester_id").is(semesterId));
        return mongoTemplate.find(q, AttendanceDocument.class)
                .stream().map(this::toRecord).toList();
    }

    @Override
    public List<AttendanceRecord> findByGroupAndSubject(Long groupId, Long subjectId,
                                                         LocalDate from, LocalDate to) {
        Query q = Query.query(
            Criteria.where("group_id").is(groupId)
                    .and("subject_id").is(subjectId)
                    .and("lesson_date").gte(from).lte(to));
        return mongoTemplate.find(q, AttendanceDocument.class)
                .stream().map(this::toRecord).toList();
    }

    private AttendanceRecord toRecord(AttendanceDocument doc) {
        return new AttendanceRecord(
            doc.getLessonId(), doc.getUserId(), doc.getGroupId(),
            doc.getSubjectId(), doc.getLessonDate(), doc.getLessonNumber(),
            doc.getStatus(), doc.getSource()
        );
    }
}
```

### Pattern 2: Authorization for Headman vs Teacher (D-05)

**What:** Two different roles can call RPRT-01 and RPRT-02. The check differs per role:
- STUDENT with isHeadman=true: `requestContext.getGroupId()` must equal lesson's groupId
- TEACHER: call `academicGrpcClient.getTeacherSubjects(userId, semesterId)` and verify `subjectId` is in the returned list for the correct `groupId`

**Critical gap:** `getTeacherSubjects()` is defined in `academic.proto` but NOT in `AcademicGrpcClient.java`. This method MUST be added.

```java
// grpc/AcademicGrpcClient.java — ADD this method
public TeacherSubjectsResponse getTeacherSubjects(Long teacherId, Long semesterId) {
    try {
        return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                .getTeacherSubjects(TeacherSubjectsRequest.newBuilder()
                        .setTeacherId(teacherId)
                        .setSemesterId(semesterId)
                        .build());
    } catch (StatusRuntimeException e) {
        throw new AcademicServiceUnavailableException("Academic Service unavailable: " + e.getStatus());
    }
}
```

**Authorization check in ReportService:**
```java
private void authorizeHeadmanOrTeacher(Long groupId, Long subjectId) {
    UserRole role = requestContext.getRole();
    if (role == UserRole.STUDENT) {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Только старосты могут просматривать журнал");
        }
        if (!requestContext.getGroupId().equals(groupId)) {
            throw new AccessDeniedException("Нельзя просматривать данные чужой группы");
        }
    } else if (role == UserRole.TEACHER) {
        Long semId = semesterCacheService.getActiveSemesterId();
        TeacherSubjectsResponse resp = academicGrpcClient.getTeacherSubjects(
            requestContext.getUserId(), semId);
        boolean teaches = resp.getSubjectsList().stream()
            .anyMatch(s -> s.getSubjectId() == subjectId && s.getGroupId() == groupId);
        if (!teaches) {
            throw new AccessDeniedException("Вы не ведёте данный предмет у данной группы");
        }
    } else {
        throw new AccessDeniedException("Доступ запрещён");
    }
}
```

### Pattern 3: Left-Join Roster for RPRT-01

**What:** Full group roster from gRPC, left-joined with attendance records from MongoDB. Missing records are presented as ABSENT.

```java
// ReportService: lesson attendance list
public LessonAttendanceResponse getLessonAttendance(Long lessonId) {
    LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
    authorizeHeadmanOrTeacher(lesson.getGroupId(), lesson.getSubjectId());

    // Full roster
    GroupMembersResponse members = academicGrpcClient.getGroupMembers(lesson.getGroupId());

    // Attendance records (may be partial)
    Map<Long, AttendanceRecord> recordsByUserId = attendanceReadPort.findByLessonId(lessonId)
        .stream().collect(Collectors.toMap(AttendanceRecord::userId, r -> r));

    // Left-join: every member appears
    List<StudentAttendanceEntry> entries = members.getStudentsList().stream()
        .map(student -> {
            AttendanceRecord rec = recordsByUserId.get(student.getUserId());
            AttendanceStatus status = rec != null ? rec.status() : AttendanceStatus.ABSENT;
            String symbol = statusSymbol(status);
            return new StudentAttendanceEntry(student.getUserId(),
                student.getDisplayName(), status, symbol);
        }).toList();

    return new LessonAttendanceResponse(lessonId, lesson.getGroupId(),
        lesson.getSubjectId(), lesson.getDate(), entries);
}
```

### Pattern 4: Stats Calculation (RPRT-03)

**What:** On-the-fly computation from raw attendance records. CANCELLED excluded from denominator.

```java
// In ReportService.getStudentStats()
List<AttendanceRecord> records = attendanceReadPort.findByUserId(
    requestContext.getUserId(), semesterCacheService.getActiveSemesterId());

// Group by subjectId, exclude CANCELLED
Map<Long, List<AttendanceRecord>> bySubject = records.stream()
    .filter(r -> r.status() != AttendanceStatus.CANCELLED)
    .collect(Collectors.groupingBy(AttendanceRecord::subjectId));

List<SubjectStats> subjectStats = bySubject.entrySet().stream().map(entry -> {
    Long subjectId = entry.getKey();
    List<AttendanceRecord> subjectRecords = entry.getValue();
    int total = subjectRecords.size();
    long attended = subjectRecords.stream()
        .filter(r -> r.status() == AttendanceStatus.PRESENT
                  || r.status() == AttendanceStatus.EXCUSED
                  || r.status() == AttendanceStatus.FREE_ATTENDANCE)
        .count();
    long absent = subjectRecords.stream()
        .filter(r -> r.status() == AttendanceStatus.ABSENT).count();
    long excused = subjectRecords.stream()
        .filter(r -> r.status() == AttendanceStatus.EXCUSED
                  || r.status() == AttendanceStatus.FREE_ATTENDANCE).count();
    double percentage = total == 0 ? 0.0 : (attended * 100.0) / total;
    // subjectName resolved via gRPC (see Subject Name Resolution below)
    return new SubjectStats(subjectId, "...", total, (int) attended,
        (int) absent, (int) excused, percentage);
}).toList();
```

### Pattern 5: Subject Name Resolution

**What:** Stats and records responses need subject names. The attendance documents store only `subjectId`. Two options:
1. Call `academicGrpcClient.getTeacherSubjects()` — returns subject names but only for teachers
2. Build a subject name map by calling `scheduleGrpcClient.getLessonsByGroup()` per group — returns lesson details including subjectId

**Recommended approach:** For STUDENT stats/records, use the existing attendance record data to find unique subjectIds, then resolve names by calling `schedulerGrpcClient.getLessonsByGroup()` with a wide date range (full semester), extract unique (subjectId, subjectName) pairs from `LessonResponse`. LessonResponse has `subject_id` but NOT `subject_name`.

**Problem identified:** Neither `LessonResponse` proto nor `GroupMembersResponse` proto includes `subject_name`. The subject name must come from Academic Service. The proto has `TeacherSubjectsResponse` which includes `subject_name` — but this is teacher-scoped. There is no generic `GetSubjectById` RPC in the current proto.

**Decision needed for planner:** Three options for resolving subject names in student stats (RPRT-03):
1. Add a `GetSubjectsByIds` RPC to `academic.proto` — clean but requires proto change
2. Use `TeacherSubjectInfo.subject_name` is only available via teacher's context — not usable for students
3. Return only `subjectId` and `null` for `subjectName` in MVP, defer name resolution to a future enhancement

**Recommendation:** Option 3 is safest for MVP — return `subjectId` and omit `subjectName` (or set to null/empty string). The planner should decide whether a proto change is in scope. If proto change is allowed, Option 1 is ideal.

### Pattern 6: ArchUnit Test (RPRT-05)

**What:** ArchUnit asserts the domain isolation rule at test time.

```java
// test/.../report/ReportDomainIsolationTest.java
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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

**Critical note:** ArchUnit's `@ArchTest` annotation requires JUnit 5 and the `archunit-junit5` artifact (not `archunit` alone). The test must be a plain JUnit 5 test (no `@SpringBootTest`) for performance.

### Pattern 7: Contract-First API Interface

**What:** `ReportApi` in `attendance-api-contract` holds all `@GetMapping`, `@Operation`, `@ApiResponse`. Controller `implements ReportApi` with no mappings of its own.

```java
// attendance-api-contract/.../contract/api/ReportApi.java
@Tag(name = "Reports", description = "Отчёты о посещаемости")
@RequestMapping("/reports")
public interface ReportApi {

    @Operation(summary = "Список посещаемости урока")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Список получен"),
        @ApiResponse(responseCode = "403", description = "Доступ запрещён",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Пара не найдена",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/lesson/{lessonId}")
    ResponseEntity<EntityModel<LessonAttendanceResponse>> getLessonAttendance(
        @PathVariable Long lessonId);

    @Operation(summary = "Журнал посещаемости (сетка)")
    @GetMapping("/journal")
    ResponseEntity<EntityModel<JournalResponse>> getJournal(
        @RequestParam Long groupId,
        @RequestParam Long subjectId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo);

    @Operation(summary = "Статистика посещаемости студента")
    @GetMapping("/student/stats")
    ResponseEntity<EntityModel<StudentStatsResponse>> getStudentStats();

    @Operation(summary = "Список записей посещаемости студента")
    @GetMapping("/student/records")
    ResponseEntity<CollectionModel<EntityModel<AttendanceRecordEntry>>> getStudentRecords(
        @RequestParam(required = false) Long subjectId);
}
```

### Anti-Patterns to Avoid

- **Direct `report/` → `checkin/` import:** Never import `AttendanceDocument`, `AttendanceRepository`, `CheckinService` in any `report/` class. Use `AttendanceReadPort` exclusively.
- **Lombok in contract module:** `attendance-api-contract` must NOT use Lombok. All response DTOs are plain classes with constructors and getters.
- **`@Enumerated` ordinal for status:** Never. Enum conversion is already handled by `MongoCustomConversions`.
- **Pagination on journal:** Bounded dataset (~600 docs max), per REQUIREMENTS.md Out of Scope. Do not add `Pageable` to journal endpoint.
- **Path param for student userId:** Student endpoints must use `requestContext.getUserId()` only (D-06), no `@PathVariable Long userId`.
- **`@Transactional` on ReportService:** MongoDB + no JPA — not applicable. No `@Transactional` needed for read-only operations.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Left-join roster with attendance | Custom nested loop with null checks | `stream().collect(Collectors.toMap(...))` + map lookup | Simple, readable, O(n) |
| MongoDB query by lesson_id | Repository method with `@Query` string | `MongoTemplate` + `Criteria` API | Consistent with existing MarkingService / LessonEventService patterns |
| Status-to-symbol mapping | Switch in controller or DTO | Static Map or method in ReportService | Centralized, testable |
| ArchUnit package check | Runtime reflection scan | ArchUnit `@ArchTest` | Correct tool; runs at test time not production |
| Stats percentage | External calculation library | Simple `(attended * 100.0) / total` | No rounding complexity at this scale |

**Key insight:** All query infrastructure (Testcontainers base class, MongoTemplate, gRPC clients with 3s deadlines, GlobalExceptionHandler, RequestContext) is already built and working. This phase adds NO new infrastructure — it adds service layer logic on top of existing infrastructure.

---

## Common Pitfalls

### Pitfall 1: Missing `getTeacherSubjects()` in AcademicGrpcClient
**What goes wrong:** Teacher authorization for RPRT-01 / RPRT-02 throws `UnsupportedOperationException` or a NullPointerException because the method doesn't exist yet.
**Why it happens:** `GetTeacherSubjects` is in `academic.proto` (and thus its generated stub has the method) but `AcademicGrpcClient.java` has no Java wrapper for it.
**How to avoid:** Add `getTeacherSubjects(Long teacherId, Long semesterId)` to `AcademicGrpcClient` as a Wave 0 / first task.
**Warning signs:** Compiler error in `ReportService` when calling `academicGrpcClient.getTeacherSubjects(...)`.

### Pitfall 2: `AttendanceRecord` DTO importing from `checkin/`
**What goes wrong:** The ArchUnit test fails because `AttendanceRecord` in `shared/port/` accidentally imports `AttendanceDocument` or `AttendanceStatus` from the wrong source.
**Why it happens:** `AttendanceStatus` and `AttendanceSource` live in `attendance-api-contract` (not in `checkin/`) — the enums are safe to use in `shared/port/`. But `AttendanceDocument` is in `checkin/` and must never appear in `shared/port/`.
**How to avoid:** `AttendanceRecord` uses only types from `attendance.contract.*` and `java.*`. The mapping to `AttendanceRecord` happens inside `AttendanceReadPortImpl` (which lives in `checkin/` and is allowed to import `AttendanceDocument`).
**Warning signs:** ArchUnit test fails on `AttendanceRecord.java` with `depends on class AttendanceDocument`.

### Pitfall 3: ArchUnit fails due to Spring Boot context
**What goes wrong:** `@SpringBootTest` on the ArchUnit test causes slow boot and unexpected failures.
**Why it happens:** ArchUnit tests are pure bytecode analysis — they need no Spring context at all.
**How to avoid:** `ReportDomainIsolationTest` must be annotated with only `@AnalyzeClasses` and use `@ArchTest` field injection. NO `@SpringBootTest`, no `@ExtendWith(SpringExtension.class)`.

### Pitfall 4: Null groupId in RequestContext for TEACHER role
**What goes wrong:** Teacher accesses `requestContext.getGroupId()` expecting a value, but the gateway sends null/0 for the `X-Group-Id` header for teachers.
**Why it happens:** Teachers are not associated with a single group. The `RequestContext.groupId` field is student-specific.
**How to avoid:** In `authorizeHeadmanOrTeacher()`, the teacher branch must NOT call `requestContext.getGroupId()`. It uses the `groupId` from the `LessonResponse` proto for the comparison with `TeacherSubjectInfo.group_id`.

### Pitfall 5: CANCELLED records in stats denominator
**What goes wrong:** Percentage calculation is wrong because cancelled lessons are counted.
**Why it happens:** Easy to forget the filter — records are fetched for a userId+semesterId without filtering status=CANCELLED.
**How to avoid:** Apply `.filter(r -> r.status() != AttendanceStatus.CANCELLED)` before grouping by subjectId. Cover this with a dedicated unit test.

### Pitfall 6: Journal query date range misses last day
**What goes wrong:** The RPRT-02 journal shows no records for `dateTo` (last day is excluded).
**Why it happens:** MongoDB `$lte` vs `$lt` confusion. `Criteria.where("lesson_date").gte(from).lte(to)` is inclusive on both ends — this is correct. But if the planner generates `lt(to)` instead, the last day is invisible.
**How to avoid:** Use `.gte(from).lte(to)` (both inclusive). Cover with an integration test that includes records on both boundary dates.

### Pitfall 7: `PagedModel` vs `CollectionModel` for student records
**What goes wrong:** Planner chooses `PagedModel` requiring a `PageImpl`, but the REQUIREMENTS.md explicitly says journal pagination is out of scope and bounded datasets are acceptable.
**Why it happens:** Habit of using `PagedModel` for list endpoints.
**How to avoid:** Per CONTEXT.md Claude's Discretion on pagination strategy — for MVP, `CollectionModel<EntityModel<AttendanceRecordEntry>>` is sufficient. Add `@RequestParam(required = false) Long subjectId` for subject filtering. No `Pageable` needed.

---

## Code Examples

### Verified Patterns from Existing Codebase

#### MongoTemplate Criteria Query (from MarkingService — write path)
```java
// Source: attendance-app/marking/MarkingService.java (existing)
Query filter = Query.query(
    Criteria.where("lesson_id").is(lessonId)
            .and("user_id").is(userId)
);
```

#### Group Members gRPC Call Pattern (from AcademicGrpcClient — existing)
```java
// Source: attendance-app/grpc/AcademicGrpcClient.java (existing)
GroupMembersResponse members = academicGrpcClient.getGroupMembers(groupId);
members.getStudentsList().stream()
    .map(s -> s.getUserId())  // long getUserId()
    .anyMatch(id -> id == userId);
```

#### EntityModel HATEOAS (from MarkingController — existing)
```java
// Source: attendance-app/marking/MarkingController.java (existing)
EntityModel<MarkResponse> model = EntityModel.of(response,
    linkTo(methodOn(MarkingController.class).mark(lessonId, userId, null)).withSelfRel());
return ResponseEntity.ok(model);
```

#### Integration Test: MockMvc GET with Header Auth
```java
// Pattern derived from MarkingIntegrationTest (existing)
mockMvc.perform(get("/reports/lesson/{lessonId}", LESSON_ID)
        .header("X-User-Id", HEADMAN_USER_ID.toString())
        .header("X-User-Role", "STUDENT")
        .header("X-Group-Id", GROUP_ID.toString())
        .header("X-Is-Headman", "true"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.entries").isArray())
    .andExpect(jsonPath("$.entries.length()").value(2)); // all group members
```

#### Status Symbol Mapping
```java
// Implement in ReportService or a helper — D-08
private static final Map<AttendanceStatus, String> STATUS_SYMBOLS = Map.of(
    AttendanceStatus.PRESENT,        "б",
    AttendanceStatus.ABSENT,         "н",
    AttendanceStatus.EXCUSED,        "у",
    AttendanceStatus.FREE_ATTENDANCE,"сп",
    AttendanceStatus.CANCELLED,      "--"
);
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@WritingConverter` from `.mongodb.core.convert` | `@WritingConverter` from `org.springframework.data.convert` | Spring Data MongoDB 4.x (Phase 15) | Existing MongoConvertersConfig is already correct |
| `grpc.server.port` positive value | `grpc.server.port: -1` (no gRPC server) | Phase 15 | Attendance service is pure gRPC consumer |
| `auto-index-creation: true` | `@PostConstruct` + `ensureIndex()` | Phase 15 | MongoConfig already creates all report indexes |

**Existing Indexes (already provisioned — Phase 15, MongoConfig.java):**
- `uniq_lesson_user` — unique on `{lesson_id, user_id}` — covers RPRT-01 lookups
- `idx_user_semester_date` — on `{user_id, semester_id, lesson_date DESC}` — covers RPRT-03, RPRT-04
- `idx_group_semester_subject` — on `{group_id, semester_id, subject_id}` — covers RPRT-01, RPRT-02
- `idx_lesson_id` — on `lesson_id` — covers RPRT-01

No new indexes needed.

---

## Open Questions

1. **Subject name resolution for student stats (RPRT-03)**
   - What we know: `LessonResponse` proto has `subject_id` but NOT `subject_name`. `TeacherSubjectsResponse` has `subject_name` but is teacher-scoped.
   - What's unclear: Whether adding a `GetSubjectsByIds` RPC to `academic.proto` is in scope for this phase.
   - Recommendation: Return `subjectId` and omit `subjectName` in the MVP stats response (set to null or empty). The frontend can resolve names client-side via the existing Academic Service API, or this is addressed in v4.1+.

2. **Teacher authorization for RPRT-02 journal — semesterId source**
   - What we know: `getTeacherSubjects(teacherId, semesterId)` requires a semesterId. `SemesterCacheService.getActiveSemesterId()` provides it.
   - What's unclear: If `SemesterCacheService` returns null (startup failure), teacher auth throws NPE.
   - Recommendation: Add null-check and throw `ServiceUnavailableException` or `BadRequestException` if semesterId is null. Pattern matches existing `MarkingService` usage of `semesterCacheService.getActiveSemesterId()`.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 18 has no new external dependencies. All infrastructure (MongoDB, Redis, RabbitMQ, gRPC targets) is already established from Phases 15-17. No new services, CLIs, or tools required.

---

## Validation Architecture

> `nyquist_validation` key is absent from `.planning/config.json` — treated as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers (existing) |
| Config file | None — `@SpringBootTest` with `@ActiveProfiles("test")` |
| Quick run command | `./gradlew :services:attendance-service:attendance-app:test --tests "*.report.*" --tests "*.ReportDomainIsolationTest"` |
| Full suite command | `./gradlew :services:attendance-service:attendance-app:test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| RPRT-01 | Lesson attendance: all group members + status, headman authorized | Integration | `--tests "*.ReportIntegrationTest.getLessonAttendance_*"` | ❌ Wave 0 |
| RPRT-01 | Lesson attendance: teacher authorized via getTeacherSubjects | Integration | `--tests "*.ReportIntegrationTest.getLessonAttendance_teacher_*"` | ❌ Wave 0 |
| RPRT-01 | Lesson attendance: student not headman returns 403 | Integration | `--tests "*.ReportIntegrationTest.getLessonAttendance_forbidden_*"` | ❌ Wave 0 |
| RPRT-02 | Journal grid: correct shape (dates[], students[], records[]) | Integration | `--tests "*.ReportIntegrationTest.getJournal_*"` | ❌ Wave 0 |
| RPRT-02 | Journal grid: status includes symbol field | Integration | `--tests "*.ReportIntegrationTest.getJournal_symbolsPresent"` | ❌ Wave 0 |
| RPRT-03 | Student stats: CANCELLED excluded from denominator | Unit | `--tests "*.ReportServiceTest.stats_cancelledExcluded"` | ❌ Wave 0 |
| RPRT-03 | Student stats: percentage correct for attended statuses | Unit | `--tests "*.ReportServiceTest.stats_percentageCalculation"` | ❌ Wave 0 |
| RPRT-03 | Student stats: overall aggregation correct | Unit | `--tests "*.ReportServiceTest.stats_overallAggregation"` | ❌ Wave 0 |
| RPRT-04 | Student records: filterable by subjectId | Integration | `--tests "*.ReportIntegrationTest.getStudentRecords_filteredBySubject"` | ❌ Wave 0 |
| RPRT-04 | Student records: uses requestContext.userId (no path param) | Integration | `--tests "*.ReportIntegrationTest.getStudentRecords_usesContextUserId"` | ❌ Wave 0 |
| RPRT-05 | No class in report/ imports checkin/ | ArchUnit | `--tests "*.ReportDomainIsolationTest"` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :services:attendance-service:attendance-app:test --tests "*.report.*" --tests "*.ReportDomainIsolationTest" -x integrationTest`
- **Per wave merge:** `./gradlew :services:attendance-service:attendance-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/.../report/ReportIntegrationTest.java` — covers RPRT-01, RPRT-02, RPRT-04 (extends `AbstractAttendanceIntegrationTest`)
- [ ] `src/test/.../report/ReportServiceTest.java` — covers RPRT-03 unit tests (pure Mockito, no Spring)
- [ ] `src/test/.../report/ReportDomainIsolationTest.java` — covers RPRT-05 (ArchUnit, no Spring context)
- [ ] `build.gradle.kts` testImplementation: `com.tngtech.archunit:archunit-junit5:1.3.0`

---

## Project Constraints (from CLAUDE.md)

All directives from `CLAUDE.md` that the planner must verify compliance against:

| Directive | Impact on Phase 18 |
|-----------|-------------------|
| Contract-first: controller `implements` interface from contract | `ReportController implements ReportApi` — mappings only in `ReportApi.java` in `attendance-api-contract` |
| No Lombok in `*-api-contract` modules | All response DTOs in `attendance-api-contract` must use plain Java (constructors + getters) — no `@Data`, no `@Value` |
| Lombok allowed in `*-app` | `ReportService` may use `@RequiredArgsConstructor`. `AttendanceReadPortImpl` may use Lombok. |
| Request DTO = Java `record`. Response DTO = class (for HATEOAS) | `AttendanceRecord` in `shared/port/` is a record (not a response DTO). Response DTOs (`LessonAttendanceResponse`, etc.) must be classes extending `RepresentationModel`. |
| HATEOAS Level 3 | All responses use `EntityModel<T>` or `CollectionModel<T>` with `_links.self` |
| RFC 7807 error responses | Use existing `ErrorResponse` record + `GlobalExceptionHandler` — no new error handling needed |
| `@ControllerAdvice` centralized | Controller throws exceptions only; `ReportService` throws `AccessDeniedException`, `ResourceNotFoundException` |
| Swagger annotations in contract interface | `@Operation`, `@ApiResponse`, `@Parameter` belong in `ReportApi.java`, not in `ReportController.java` |
| Package naming: `ru.rutcampustrack.{service}.{module}` | New packages: `ru.rutcampustrack.attendance.report`, `ru.rutcampustrack.attendance.shared.port` |
| REST paths: `/api/{service}/...` via Gateway | Gateway already routes `/api/reports/**` → attendance-service:9093 with `StripPrefix=1`. Internal endpoints are `/reports/...` |
| `report/` NEVER imports from `checkin/` directly | RPRT-05 enforced by ArchUnit test |
| `shared/port/` — interface `AttendanceReadPort` bridges domains | `AttendanceReadPortImpl` in `checkin/`, interface + DTO in `shared/port/` |

---

## Sources

### Primary (HIGH confidence)

- Existing source code (read directly):
  - `attendance-app/.../checkin/AttendanceDocument.java` — document shape, all fields verified
  - `attendance-app/.../checkin/AttendanceRepository.java` — minimal, confirms custom queries need MongoTemplate
  - `attendance-app/.../grpc/AcademicGrpcClient.java` — missing `getTeacherSubjects()` confirmed
  - `attendance-app/.../grpc/ScheduleGrpcClient.java` — `getLessonsByGroup()` and `getLessonById()` available
  - `attendance-app/.../marking/MarkingService.java` — MongoTemplate Criteria pattern, authorization pattern
  - `attendance-app/.../marking/MarkingController.java` — HATEOAS EntityModel pattern
  - `attendance-app/.../security/RequestContext.java` — fields: userId, role, groupId, headman
  - `attendance-app/.../security/RequireRole.java` — method-level only
  - `attendance-app/.../config/MongoConfig.java` — ALL four report indexes already exist
  - `attendance-app/.../exception/GlobalExceptionHandler.java` — existing exception types and mappings
  - `attendance-app/.../test/.../AbstractAttendanceIntegrationTest.java` — Testcontainers base class
  - `proto/academic.proto` — `GetTeacherSubjects` RPC confirmed in proto
  - `proto/schedule.proto` — `LessonResponse` fields confirmed (no subject_name)
  - `services/api-gateway/.../application.yml` — `/api/reports/**` route already configured
  - `attendance-api-contract/.../enums/AttendanceStatus.java` — PRESENT, ABSENT, EXCUSED, FREE_ATTENDANCE, CANCELLED
  - `attendance-app/build.gradle.kts` — ArchUnit NOT present; all other dependencies present
  - `.planning/REQUIREMENTS.md` — RPRT-01..05 acceptance criteria
  - `.planning/phases/18-read-path-reports/18-CONTEXT.md` — locked decisions D-01..D-17

### Secondary (MEDIUM confidence)

- ArchUnit documentation pattern for `@AnalyzeClasses` + `@ArchTest` — standard JUnit 5 usage, consistent across ArchUnit 1.x docs

### Tertiary (LOW confidence)

- Subject name resolution gap (no `GetSubjectsByIds` RPC) — inferred from proto examination; confirmed no such RPC exists. Mitigation: omit subjectName in MVP.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all dependencies examined directly in build.gradle.kts
- Architecture: HIGH — patterns inferred from existing checkin/marking code, all referenced directly
- Pitfalls: HIGH — derived from examining actual code gaps (missing getTeacherSubjects, ArchUnit not in deps)
- Subject name resolution: MEDIUM — identified gap from proto, recommended workaround

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (stable project, 30 days)
