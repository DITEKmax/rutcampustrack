# H76/H77 combined L5A union verification
## Goal
S3 verify the new isolated integration of accepted source278 into target426, especially merged gRPC assignment/map/projection wiring and unchanged schema.
## Context/evidence
RULES SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A; LESSONS-L5A-UNION-IMPLEMENT.md and frozen lead CONTRACT9522C383/path-inventory15B218A3. Source-stage acceptance does not validate merged constructors. Integration author must finish/freeze before root execution.
## Relevant scope
Root sole executor in C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-integration-l5a base426 plus frozen46-path integration. Root evidence/h76-*/h77-*; author owns code but NO writes during checks. Existing426/H57 unchanged and separate from these tests.
## Required behavior
H76 single normal Gradle invocation academic-app:test with exact nine selectors: ru.rutcampustrack.academic.assignment.AssignmentAuthorityContractTest; ru.rutcampustrack.academic.assignment.AssignmentClosureContractTest; ru.rutcampustrack.academic.subject.SubjectAssignmentAuthorityContractTest; ru.rutcampustrack.academic.config.SubjectTypeUserTypeTest; ru.rutcampustrack.academic.grpc.AcademicAssignmentGrpcContractTest; ru.rutcampustrack.academic.semester.SemesterAssignmentLockContractTest; ru.rutcampustrack.academic.grpc.CampusMapGrpcReadTest; ru.rutcampustrack.academic.grpc.StudentProjectionGrpcServiceTest; ru.rutcampustrack.academic.grpc.StudentHomeworkGrpcIdentityInterceptorTest. Then in same invocation attendance-app:test selector ru.rutcampustrack.attendance.report.ReportServiceTest. Normal dependency compilation/proto generation required; no copied classes or init hacks.
H77 only after H76 PASS/lease: academic-app:integrationTest with exact six selectors ru.rutcampustrack.academic.assignment.AssignmentAuthorityIT; ru.rutcampustrack.academic.grpc.AcademicAssignmentGrpcIT; ru.rutcampustrack.academic.semester.SemesterAssignmentAuthorityIT; ru.rutcampustrack.academic.subject.SubjectAssignmentAuthorityIT; ru.rutcampustrack.academic.subject.SubjectServiceIT; ru.rutcampustrack.academic.integration.AcademicGrpcIT. H75 method/class covered by union, no extra repetition.
## Constraints
Single root heavy queue, no concurrent RequestsH71/Gradle/Docker. Flags --no-daemon --no-parallel --max-workers=1 --no-problems-report. Actual login:false/elevated known execution context, Java21/Gradle8.12, TESTCONTAINERS_REUSE_ENABLE=false forH77. Do not mutate source/migrations/global settings; no production resources. Failures stop stage for diagnosis; no silent retry.
## Existing patterns
Existing Gradle tasks/test fixtures, exact source blob/raw-hash freezes, root raw/context/exit/freshXML and exact owned container absence verification. Preserve original reports; one unique evidence namespace per execution.
## Acceptance criteria
All10 selected unit/consumer classes fresh nonzero tests/no failures/errors/skips, then all6PGclasses fresh nonzero/no failures/errors/skips. Source pre/post unchanged, only owned PG/Ryuk cleanup verified. Fresh FULL integration Sol mandatory afterwards; no production readiness/public runtime acceptance inferred.
## Verification
Root first checks exact46pathset/44sourceblob matches/2composite critical reads and unchanged target-only/proto/migrations. Save root freeze manifest of all46 raw hashes, command array, identity/start/end/exit, stdout/stderr, exactXML copies. Compare posthashes; inspect underlying failures not just Gradle summary. Runtime application build beyondtests separate.
## Do not
No automatic execution from this reserved packet: H76/H77 remain NOTSTARTED until root lease recorded. No heavy selfstart by author, skipped tests, scope expansion without concrete evidence, fixture weakening, source changes mid-run, foreigncleanup or E/main promotion.
