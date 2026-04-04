# Phase 18: Read Path -- Reports - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md -- this log preserves the alternatives considered.

**Date:** 2026-04-04
**Phase:** 18-read-path-reports
**Areas discussed:** Report API design, Journal grid shape, Stats calculation, Domain isolation pattern

---

## Report API Design

| Option | Description | Selected |
|--------|-------------|----------|
| Separate /reports/* prefix | Dedicated report routes, clean separation from write path | x |
| Under /attendance/* prefix | Single base path for all attendance operations | |
| Mixed approach | Split between /attendance and /reports | |

**User's choice:** Separate /reports/* prefix
**Notes:** Clean separation from write path (/attendance/*)

---

| Option | Description | Selected |
|--------|-------------|----------|
| GET /reports/lesson/{lessonId} (RPRT-01) | Lesson attendance list | x |
| GET /reports/journal (RPRT-02) | Journal grid | x |
| GET /reports/student/stats (RPRT-03) | Student attendance stats | x |
| GET /reports/student/records (RPRT-04) | Student raw attendance list | x |

**User's choice:** All 4 endpoints in scope
**Notes:** Extra endpoints from phases-plan.md (semester summary, top-skippers, export) deferred to v4.1+

---

| Option | Description | Selected |
|--------|-------------|----------|
| All group members | Full roster via gRPC, left-join with records, absent for missing | x |
| Only recorded students | Return only students with AttendanceDocument | |

**User's choice:** All group members
**Notes:** Matches success criteria #1

---

| Option | Description | Selected |
|--------|-------------|----------|
| Role + group check | Headman: groupId match. Teacher: verify teaches subject+group via gRPC | x |
| Role only | Any teacher can see any group | |
| Role + group for headman, role only for teacher | Middle ground | |

**User's choice:** Role + group check for both
**Notes:** Prevents data leakage

---

## Journal Grid Shape

| Option | Description | Selected |
|--------|-------------|----------|
| Nested: students[] with dates[] | Top-level dates array + students with records array | x |
| Flat rows with cell array | Positional cell array matching dates | |
| Map-based: date -> status | Map per student, flexible but no column order guarantee | |

**User's choice:** Nested structure
**Notes:** Clean for table rendering, frontend renders rows=students, cols=dates

---

| Option | Description | Selected |
|--------|-------------|----------|
| Russian symbols (б/н/у/сп) | Matches physical journal convention | |
| Enum names (present/absent/...) | Flexible for i18n | |
| Both | Return both status enum and symbol | x |

**User's choice:** Both
**Notes:** { status: "present", symbol: "б" }

---

| Option | Description | Selected |
|--------|-------------|----------|
| gRPC call to Academic | getGroupMembers() returns IDs + names, join in ReportService | x |
| Return IDs only, frontend resolves | Less coupling but more round-trips | |
| Denormalize names into MongoDB | Fast reads but stale names | |

**User's choice:** gRPC call to Academic
**Notes:** Consistent with RPRT-01 approach

---

## Stats Calculation

| Option | Description | Selected |
|--------|-------------|----------|
| present + excused + free_attendance | All three count as attended, only absent counts against | x |
| present only | Physical presence only | |
| Configurable | Admin-configurable | |

**User's choice:** present + excused + free_attendance
**Notes:** Matches CLAUDE.md status table

---

| Option | Description | Selected |
|--------|-------------|----------|
| On-the-fly aggregation | MongoDB aggregation at query time | x |
| Pre-materialized view | Stats collection updated on writes | |
| Cached aggregation | On-the-fly + Redis cache | |

**User's choice:** On-the-fly aggregation
**Notes:** Fine for current scale, optimize later

---

| Option | Description | Selected |
|--------|-------------|----------|
| Percentage + counts | subjectId, total, attended, absent, excused, percentage + overall | x |
| Percentage only | Minimal | |
| Detailed with breakdown | Include weekly trend | |

**User's choice:** Percentage + counts
**Notes:** Gives both percentage and raw numbers for UI flexibility

---

## Domain Isolation Pattern

| Option | Description | Selected |
|--------|-------------|----------|
| Query-oriented methods | findByLessonId, findByUserId, findByGroupAndSubject | x |
| Repository-style with Criteria | Single query(AttendanceQuery) method | |
| Direct MongoTemplate | No port, breaks RPRT-05 | |

**User's choice:** Query-oriented methods
**Notes:** Clean read interface with specific methods

---

| Option | Description | Selected |
|--------|-------------|----------|
| Interface in shared/port/, impl in checkin/ | Neutral ground for interface, impl where repository lives | x |
| Both in report/ | Simpler but impl needs checkin's repository | |
| Interface in contract module | Cross-module but overkill for internal use | |

**User's choice:** Interface in shared/port/, impl in checkin/
**Notes:** Matches CLAUDE.md package structure

---

| Option | Description | Selected |
|--------|-------------|----------|
| Relevant subset | lessonId, userId, groupId, subjectId, lessonDate, lessonNumber, status, source | x |
| All fields | Mirror AttendanceDocument | |
| Minimal | Only lessonId, userId, status | |

**User's choice:** Relevant subset
**Notes:** Omits id, markedBy, createdAt, updatedAt

---

## Claude's Discretion

- ReportService internal structure
- MongoDB aggregation pipeline specifics
- Contract DTO naming
- Test structure and count
- Pagination strategy for student records

## Deferred Ideas

- Semester summary, top-skippers, export (v4.1+)
- Weekly trend charts (v4.1+)
- Redis caching for stats (optimize later)
- Subject rating (v4.1+)
