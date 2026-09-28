# Recurring lesson cancel/restore — backend checkpoint

- Base: `3e1c8d86b37c8735137e790e5913124255f025cb`; branch `codex/recurring-lesson-restore-20260928`.
- Scope: canonical recurring cancel/restore, replacement-aware generation restore, archive homework bindings through the schedule outbox, and current-generation lesson listing. UI was assigned elsewhere and is untouched.
- Acceptance: cancel atomically advances lesson/occurrence revisions, appends `CANCELLED`, archives each exact active/pending binding and emits its event; ordinary restore retains the physical lesson and appends `RESTORED`; replacement restore creates one exact next generation using the committed teacher/template for that date; slot conflicts roll back; stale source replay cannot create another generation; listing returns only the canonical current generation.

## Changed files

- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java`
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/RecurringLessonLifecycleWriter.java`
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java`
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/HomeworkBindingArchivedEvent.java`
- `services/schedule-service/schedule-app/src/main/resources/db/migration/V20__canonical_lesson_restore_authority.sql`
- `event-schemas/homework.binding.archived.json`
- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonApiIT.java`
- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/AssignmentReplacementServiceIT.java`
- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/recurring/RecurringLifecycleGateTest.java`

V20 is a new local migration; V19 remains unchanged. Its identity guard retains the V19 replacement ledger checks and permits only a tuple-verified initial generation-1 pointer assignment without restore authority. Restore authority is append-only and validated against the canceled source/current pointer, revisions, committed replacement chain, target template/fence, target physical generation, and `RESTORED` lifecycle row.

## Verification

- `git diff --check` — exit 0.
- `:services:schedule-service:schedule-app:integrationTest` with the two selectors below and flags `--no-daemon --no-parallel --max-workers=1 --console=plain --system-prop=org.gradle.java.compile-classpath-packaging=true --no-problems-report` — exit 0, `BUILD SUCCESSFUL`; Testcontainers PostgreSQL 16.13, Java 21.0.10.
  - `cancelThenRestore_archivesExactHomeworkBindingAndKeepsCanonicalHistory`: tests=1, failures=0, errors=0, skipped=0. XML: `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\teacher-export-ui-20260924\services\schedule-service\schedule-app\build\test-results\integrationTest\TEST-ru.rutcampustrack.schedule.integration.LessonApiIT.xml`; timestamp `2026-09-28T21:48:02`; last write `2026-09-29T00:48:04.7589+03:00`.
  - `cancelledOccurrenceRestoresThroughExactReplacementAndConflictRollsBack`: tests=1, failures=0, errors=0, skipped=0. XML: `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\worktrees\teacher-export-ui-20260924\services\schedule-service\schedule-app\build\test-results\integrationTest\TEST-ru.rutcampustrack.schedule.integration.AssignmentReplacementServiceIT.xml`; timestamp `2026-09-28T21:47:57`; last write `2026-09-29T00:48:04.7641+03:00`.

The integration run applied all 20 migrations in an isolated PostgreSQL container and verified ordinary cancel/restore, exact homework archive event payload, replacement skip of canceled source, occupied-slot conflict rollback, successful replacement-aware restore, stale-source replay rejection, and canonical current-generation listing.

## Limits

No full suite, service deployment, production migration, or real browser/API runtime was run. Those remain root/UI acceptance work. Unrelated untracked `.agent` artifacts were preserved and are excluded from the scoped commit.
