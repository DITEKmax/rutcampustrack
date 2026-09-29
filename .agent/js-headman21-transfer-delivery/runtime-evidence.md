# Runtime evidence — JS-HEADMAN21 lesson transfer

## Schedule PostgreSQL integration

- Command: `./gradlew.bat :services:schedule-service:schedule-app:integrationTest --tests ru.rutcampustrack.schedule.lesson.LessonTransferWriterIT --tests ru.rutcampustrack.schedule.migration.FlywayMigrationIT`
- Exit code: 0. Gradle reported `BUILD SUCCESSFUL`; 37 actionable tasks, 3 executed. JDK 21.0.10; Spring Boot 3.5.16; Testcontainers ran PostgreSQL 16 on Docker Desktop.
- Results: `LessonTransferWriterIT.requestReplayUsesCanonicalOutboxAndFixedReceiptsCompleteOnce` PASS; all 3 selected `FlywayMigrationIT` cases PASS, including fresh V1–V21 migration/checksum and retained V17 history guards.
- Observable check: MockMvc reloaded the committed target lesson and returned physical `revision=1`, `occurrenceRevision=2`; the test used that returned value for the next transfer. B→C completed after its Attendance receipt and both Academic batch ACKs.

## Academic PostgreSQL integration

- Command: `./gradlew.bat :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.homework.HomeworkBindingTransferIT --tests ru.rutcampustrack.academic.migration.FlywayMigrationIT`
- Exit code: 0. Gradle reported `BUILD SUCCESSFUL`; 37 actionable tasks, 5 executed. JDK 21.0.10; Testcontainers ran PostgreSQL on Docker Desktop.
- Results: `HomeworkBindingTransferIT.activeAndPendingPublicationFollowAThroughBToCAndAckIsDurable` PASS; all 3 selected `FlywayMigrationIT` cases PASS, including fresh migration/checksum and data preservation through V38.

## Failures observed and resolved

- Earlier Schedule integration attempt failed with PostgreSQL P0001 from `protect_lesson_homework_binding_transfer`: the writer moved bindings before recording their immutable batch. The writer now records the exact batch and publishes its outbox event before the binding update in the same transaction; the V21 guard was left unchanged. The successful Schedule run above exercises this path.
- The first B→C integration attempt failed on PostgreSQL check constraint `lessons_day_of_week_chk` (SQLSTATE 23514): Monday was encoded as 0 although physical lesson rows persist weekdays as 1–7. `LessonTransferWriter` now uses the persisted convention and rejects Sunday while allowing Monday through Saturday. The successful Schedule run above includes a Monday target.
- Earlier compile failures were in new transfer code: unreachable `SQLException` catch in `LessonTransferWriter`, then non-public canonical constructor on public `LessonTransferBatch`. Both were fixed before the successful integration runs.
- A prior default `test` task compiled integration tests but excludes `*IT`; that exit 0 was unit/compile-only evidence and is not counted as PG acceptance. The heavy target used `integrationTest` explicitly.

## Limits and unrelated output

These are service-local PostgreSQL integration checks plus a MockMvc reload; they do not prove the live combined Schedule→Attendance→Academic API flow or Attendance mark/attachment preservation. Root owns that runtime and the final cross-service review. The test context also emitted an Auth public-key lookup ERROR for `localhost:9999`; it is unrelated to this scenario and did not affect the in-memory-key-backed tests. No code change was made for it.
