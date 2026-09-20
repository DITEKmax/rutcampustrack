# L5A coverage source freeze — 2026-09-19

## Scope

This source-only continuation preserves the existing L5A WIP and adds the
approved H56 order correction, the remaining academic legacy/negative cases,
and the bounded Attendance consumer checks. Production, schema, migration,
proto, dependency, generated, and global configuration files were not changed
by this continuation.

## Criteria covered by source

- `SubjectAssignmentAuthorityIT` now compares `createdAssignmentIds` as an
  exact multiset while retaining row counts and database identity checks; its
  assignment summaries are checked in the canonical semester/type/teacher/date/
  id order.
- Foreign-group access targets a subject that actually belongs to a second
  fixture group while the trusted headman context remains its own group;
  non-headman creation is denied. Both cases retain a full subject/type/
  assignment/semester/outbox snapshot.
- Unreferenced subject deletion uses the Schedule boundary mock with a zero
  response; nonzero references and Schedule unavailability both fail closed
  and retain full snapshots.
- `AcademicAssignmentGrpcIT` covers positive and mixed-missing batches,
  nonpositive IDs, an empty batch, and a direct `AssignmentRepository.findById`
  composite lesson-type read.
- `AssignmentAuthorityIT` covers invalid start/end date bounds; the semester IT
  covers completed-semester name-only denial.
- `SubjectServiceIT` and `AcademicGrpcIT` no longer use TSG as assignment
  authority. They use canonical lesson types, immutable assignments, current
  valid dates, and active teacher grants; legacy `teacherIds` rejection and
  atomic rollback remain explicit. The archived-member fixture uses a unique
  login and leaves its role grant intact, avoiding a forbidden user deletion.
- `ReportServiceTest` covers teacher allow with exact teacher/active-semester
  Academic RPC arguments, empty response denial, and subject/group mismatch
  denial at the mocked Academic consumer boundary.

## Frozen source files and SHA256

```
services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityIT.java  F8D5429B58451C69A390EB957E4D7329628CDF362BFC965EF82468862EBC3700
services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentAuthorityIT.java  6F232141192091E8FD53CF54AFE25DE7B89AADDB9FD15177BFFD70BF660C4B7B
services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/AcademicAssignmentGrpcIT.java  F36065F4EE8BCC9D646853A67B831A32805BA31C29A2D81F47043B2E709B9054
services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterAssignmentAuthorityIT.java  3C35FDE3E8CFC29884E3B8575A210B1CA067CE939762267DFF1B530F470EE78B
services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectServiceIT.java  C43718E2B5172A356B3E70E7B31605796A73427CF5EC5320722E8966B01263E5
services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicGrpcIT.java  57AE1E3CEB13219413B66007070B9AA2308695BBC28351BA1A5D798009E6B2FF
services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/ReportServiceTest.java  C150E74E5272EFE8FFDFD76EA155016EECDF694E2E040B7D6CD1F73C6D43AFC6
services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAssignmentAuthorityIT.java  90175954C1176A3A831F2C21EC2E9879C505ECA9E6A82DE53C338601F92A2EC9
```

## Exact future selectors

Academic integration selectors:

```
ru.rutcampustrack.academic.subject.SubjectAssignmentAuthorityIT
ru.rutcampustrack.academic.assignment.AssignmentAuthorityIT
ru.rutcampustrack.academic.grpc.AcademicAssignmentGrpcIT
ru.rutcampustrack.academic.semester.SemesterAssignmentAuthorityIT
ru.rutcampustrack.academic.subject.SubjectServiceIT
ru.rutcampustrack.academic.integration.AcademicGrpcIT
```

Attendance selector:

```
ru.rutcampustrack.attendance.report.ReportServiceTest
```

## Checks and runtime limits

- `git diff --check`: exit code 0 after this source freeze; only pre-existing
  LF/CRLF conversion notices were emitted.
- Gradle compilation and tests: NOT RUN in this source phase.
- Docker/Testcontainers: NOT RUN in this source phase.
- Prior H56 raw log/XML/cleanup evidence remains under the existing
  `evidence/integration-tests-h56-2026-09-19.log`, `evidence/h56-xml/`, and
  `evidence/h56-owned-cleanup-2026-09-19.log`; this freeze does not claim the
  new source has runtime PASS.
- Runtime must use a later numbered heavy lease, process-local
  `TESTCONTAINERS_REUSE_ENABLE=false`, and task-owned cleanup. A failed first
  selector must stop the lease and preserve raw/XML evidence.

## Limits

The Attendance checks are consumer-boundary unit evidence and do not replace
the Academic producer integration selectors. L5B, clients, deployment, and
full L5A completion remain outside this packet.
