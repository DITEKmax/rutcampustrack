# Current96 Schedule concurrency diagnostic — SOURCE PLAN ONLY

Producer96c7aee0a114e978ce3ed2976127a2a59c1a69e3; actual remote checkout1e7d036e82d7cf434db25b1c463a70cfda1d975d. Retained closed observation3e3a/08d4 remains immutable: Coverage38302629088/job114988989924 executed205 tests, one failure, zero skips, source-known AssertionFailedError at RecurringScheduleItemIT.java:894. It proves the CAP future returned a non-null captured Throwable. Its type, cause, SQLState and stage remain UNKNOWN. Current CI same case PASS neither diagnoses nor dismisses the Coverage failure. Academic Locale.ROOT49db is an unrelated source change; no causal product defect is claimed.

## Accepted invariant and fixture

Accepted teacher-replacement decision transfers future unheld lessons from D and preserves past attendance/history; archive decision blocks writes during archive protocols without a bypass. Existing implementation separates creation-cap narrowing from retention-cap preservation. This test expects CAP install to succeed, a later physical insert beyond the new creation cap to fail with DataAccessException, unchanged retention cap and no forbidden occurrence. No new cutoff or product rule is needed.

Test source155e mocks Academic authority/Clock, uses a synthetic PREPARED replacement tuple, seeds the recurring series through2026-02-09, narrows creation at2026-02-10 and attempts insertion on2026-02-16. Clock2026-02-09T09:00:00Z/Europe-Moscow is fixture time; deadlines need fresh real UTC. PostgreSQL16 Testcontainers/reuse=false, per-test TRUNCATE reset and real Flyway/retention triggers apply. Academic RPC is mocked, not real interservice acceptance; insert uses direct JDBC, not HTTP. Two executor threads/explicit transactions use separate connections. SchedulingConfig is excluded by test profile.

## Risk-ranked hypotheses — not native causes

H1: fixture lock-order inversion. CAP acquires assignment row FOR UPDATE at872, signals fenceLocked and awaits insertStarted; replacementService.install then joins that outer transaction and applyLocal119 requests the semester advisory lock. INSERT signals insertStarted BEFORE its SQL. Its BEFORE trigger a_schedule_semester_deletion_fence (V23:225) takes the same semester advisory lock (V23:124), even without an archive barrier, before the alphabetically later recurring fence trigger. The latest V25 recurring validator then takes that assignment row FOR UPDATE. A legal interleaving is CAP holds assignment A; INSERT signals and acquires semester S; CAP waits S; INSERT waits A. The latch does not order acquisition of S. PostgreSQL can detect this cycle; CAP victim is one possible outcome, not observed here. Production writer/service otherwise acquire semester before domain rows. The code comment explicitly states that order. No test or production fix is approved from this model alone.

H2: CAP awaitBarrier can return a captured AssertionError after5s timeout or interruption. Its future can then complete before the10s get timeout and fail isNull exactly at894. The observed frame does not distinguish this from H1. H3: another captured authority/constraint/transaction error. Fixture source defines valid mocked tuple, but no retained native exception proves it reached the DB stage. Scheduled background jobs are source-disabled; no source basis to assert scheduler interference. Coverage history/load changes are possible conditions, not evidence of causality; unit and integration tasks have separate test JVMs.

## Existing selected reproduction command — NOT RUN / NOT GRANTED

In a future separately granted, frozen96 isolated owned source/build namespace using retained pinned JDK21/Gradle8.12:

```sh
bash ./gradlew :services:schedule-service:schedule-app:integrationTest --tests 'ru.rutcampustrack.schedule.integration.RecurringScheduleItemIT.capNarrowingCommitIsFreshBeforeBlockedPhysicalInsert' --rerun --no-daemon --max-workers=1
```

This is the existing integration task, with global JaCoCo0.8.12 still active. Task-specific --rerun avoids stale selected outcomes. Do not add jacocoTestReport: it depends on both test and integrationTest and pulls the unit graph. Do not disable JaCoCo, relax5s/10s, alter assertions, fixtures or locale. This reproduces the selected Coverage test runtime/instrumentation; it does NOT recreate unit→full205-case ordering/resource history. One selected PASS is not a retrospective flake disposition. --offline is an optional resource choice only after dependency feasibility; missing dependencies are infrastructure, not a case counterexample.

Require EXECUTED integrationTest, freshly produced exact class/source identity and one selected JUnit result (zero skipped), because build filter permits no matching tests. CLI0 or UPTODATE alone is insufficient. Preserve actual failing class/metadata and source unchanged before any author change.

Root must choose ordering after Stage/client/stand needs and grant fresh capacity/foreground guard. Suggested bounded envelope, not authorization or actual capacity: one worker, own disk growth<=2GiB/shared growth<=4GiB/RSS<=4GiB, fresh filesystem floors4GiB, shared stop15GiB/reserve1GiB, native600s/outer900s/cleanup60s. Use existing reviewed guard/source-seal pattern; protect all old artifacts/data/keys, original five volumes/native Mongo157 and generated/shared source. Fresh test-owned PG16/container resources only; record exact owned births/containers/reap, no global prune or retained-data reset. Test finally shutdownNow does not itself await termination; guard closure must establish reap.

## Smallest missing witness

First prefer future unchanged selected task's standard private600 result/stdout. Under separate explicit authorization, project ONE exact failure in bounded RAM to a closed allowlisted captured Throwable class, SQLState (40P01 deadlock,55P03 lock unavailable,40001 serialization,235xx constraint when genuinely present), first source-known frame and exact source constant for barrier timeout/interruption. Never share raw XML/log/message/row IDs/request/credentials. A captured DataAccessException class alone does not prove deadlock; historical failure has no retained inner exception. A future reproduction witness describes that reproduction, not automatically the old failure.

If standard output does not expose the captured cause/stage, smallest additional future source proposal is a separately reviewed test-only closed observer around this existing CAP action: assignmentFenceAcquired/insertStartedSeen/installEntered plus allowlisted exception/SQLState. Preserve all original assertions and bounded waits. That observer is NOT authored or granted here. No second old log/XML read, native action, paused custom security runner or whole-suite replay occurred.

Only public source hashes/static graph controls and existing SAFE receipts are included. Root alone integrates disposition; mandatory Coverage remains failed/underlyingCauseUNKNOWN and no matrix/parent acceptance is promoted.
