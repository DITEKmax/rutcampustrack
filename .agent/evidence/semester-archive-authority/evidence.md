# JS-ADMIN-10/18 — Academic semester archive authority

## Goal and context

Implement the Academic source of truth for reversible semester archive and restore, without exposing an archive/restore success path before all three write domains provide durable fence receipts. The parent-approved owner decision is `docs/product/decisions/2026-09-29-semester-archive-write-lock.md`. Canonical process rules are `.agent/orchestration-v2/RULES.md`, SHA-256 `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`.

Main baseline is `ee4818042b52c2a5312a5054aa8340db07c2a2bb`; this worktree started from the previously committed proto dependency `0856af960c27c3491a4376b8ca7c0c79fdc269cf`. That separate `proto/academic.proto` commit defines `GetSemesterState` and also includes the ranking dependency `rank_subjects = 14` requested by root.

## Required behavior and implementation

- Persist `archived`, `archive_transition` (`NONE`, `ARCHIVING`, `RESTORING`), and monotonic lifecycle `state_version` separately from `active`. Migration `V39__semester_archive_state.sql` enforces legal combinations; V38 remains reserved by the parallel transfer scope and must be reconciled before a combined migration rollout.
- Serialize activation/archive/restore through the existing transaction-scoped PostgreSQL advisory lock plus pessimistic row locks. Transition start increments the version; finalization requires the exact version, and completion replay accepts only that same version. Archived or transitioning rows reject edits and activation.
- Archiving an active semester deactivates it without activating a replacement. Restore leaves it inactive; a later explicit activation is allowed. The existing `SemesterArchivedEvent` is emitted only for a real active-to-inactive transition and remains a historical active-status signal, not the source of archived truth.
- `GetSemesterState` reads an uncached row by positive ID and returns id, active, archived, state_version, transition, and `write_blocked = archived || transition != NONE`. Invalid IDs return `INVALID_ARGUMENT`; absent IDs return `NOT_FOUND`. It uses the existing internal gRPC authentication path.
- Do not expose public archive/restore endpoints or synthesize participant acknowledgements. The three real fence domains are Schedule, Attendance (including marks and request decisions), and Academic Homework. Archive completion must wait for all durable fences; central write blocking must remain true throughout restore until receipts arrive.

## Acceptance evidence

`SemesterArchiveStateIT` uses PostgreSQL/Testcontainers and the in-process gRPC API. It observes initial, ARCHIVING, archived, RESTORING, and restored state; validates write blocking, missing/invalid IDs, outbox event count and payload, replay and stale-version handling, archived edit/activation rejection, post-restore edits and explicit activation, and concurrent archive/activation serialization with at most one active semester.

## Checks and runtime

Runtime: OpenJDK 21.0.10, Gradle 8.12, Docker 28.5.2, PostgreSQL 16.13 (Testcontainers). `git diff --check` exited **0** after the fixture correction.

The exact targeted command was run twice, only for these methods:

```powershell
.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.integration.SemesterArchiveStateIT.getSemesterState_reportsTransitionsReplayAndWriteBlock --tests ru.rutcampustrack.academic.integration.SemesterArchiveStateIT.archiveAndActivation_areSerializedAndKeepAtMostOneActiveSemester --no-daemon --no-parallel --max-workers=1 --system-prop=org.gradle.java.compile-classpath-packaging=true --no-problems-report --console=plain
```

- Attempt 1, session `37005`, exited **1**: the lifecycle/read test passed; the race test stopped before racing because both fixtures used `[9990-01-01, 9990-06-30]`, violating PostgreSQL exclusion constraint `semesters_no_overlap`. XML identified the fixture collision.
- Correction: the existing fixture helper now allocates the next distinct year per fixture. No product behavior or assertion was weakened.
- Root approved one repeat of the same two methods. Attempt 2, session `25913`, exited **0**, `BUILD SUCCESSFUL`; XML reports `tests=2`, `skipped=0`, `failures=0`, `errors=0`.
- Runtime XML: `services/academic-service/academic-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.academic.integration.SemesterArchiveStateIT.xml`. Captured Gradle logs are locally available as `targeted-gradle-attempt1.log` and `targeted-gradle-attempt2.log` in this evidence directory.

## Diff inventory

- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/enums/SemesterTransition.java`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/semester/SemesterResponse.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Semester.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/SemesterRepository.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterAssembler.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java` — semester RPC scope only; ranking serializer unchanged.
- `services/academic-service/academic-app/src/main/resources/db/migration/V39__semester_archive_state.sql`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/SemesterArchiveStateIT.java`

## Limits and handoff

This is the Academic authority subpackage, not full archival completion. No public mutation endpoint, durable participant fence/receipt, final delete, password, cascade preview, UI, or transfer/Attendance/Schedule/Homework implementation is included. All three participant guards and real acknowledgements remain required before any successful end-to-end archive or restore can be reported. Preserve old `.agent/evidence/semester-activation-ac39/` worktree evidence; it is outside this package.
