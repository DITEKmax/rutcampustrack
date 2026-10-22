# Minimal runtime handoff

All commands below are proposed, NOT_RUN. Root must assign sole developer writer/worktree and heavy lease; no parallel full stands. Run from the assigned checkout, not shared `/workspace/rutcampustrack`. Pin baseline/current and record exact command/log/XML/data scope. Reuse prior sufficient PASS only at its original revision. No product rule questions are prerequisites for L01/L02.

## 1. L01 red-before: actual Rabbit publication fault

Smallest reusable harness: academic `AbstractAcademicEventIntegrationTest` (fresh postgres:16 + rabbitmq:3.13-management-alpine; real production sender and manual transaction flush). Keep scheduling off. Bind a unique durable test queue to production fanout exchange and receive only this test event_id. Use actual GroupService rename or accepted producer transaction; capture academic_outbox row ID, payload hash/event_id and domain commit before publish.

Add one focused integration test in developer worktree (suggested `OutboxBrokerConfirmIT`; does not exist yet):
1. Declare exchange and dedicated queue and validate a healthy real event roundtrip first only if necessary to prove the fault harness, using distinct event_id.
2. Create one synthetic producer operation and leave its outbox PENDING. Ensure no unrelated pending rows are marked SENT by fixture cleanup; IDs scoped to this operation.
3. Establish publisher connection; add AMQP blocked/unblocked listeners and wait on bounded latches. In disposable broker only, `rabbitmqctl set_disk_free_limit 1000GB` activates disk alarm (choose value above measured ephemeral disk free; do not touch any other broker). Wait for actual alarm and Connection.Blocked before initiating the target publish. If broker cannot provide deterministic blocked behavior, STOP this attempt and use a small AMQP upstream pause/drop proxy for this broker; no timing-only random kill loop.
4. Call actual OutboxPublisherJob.publishBatch inside TransactionTemplate. Under baseline non-confirmed sender this may return and DB commit SENT even though broker has not accepted message. Observe, do not assume.
5. Close exactly that disposable publisher connection using Rabbit admin/control command while alarm holds; reset disk free limit in finally; wait broker unblocked/reconnected. Queue control/read must use a separate connection. Record target event absent/present and original outbox state. A target SENT with no accepted message after recovery confirms the violation; an exception and PENDING is a passing safety outcome for that injected fault, not a reproduction.
6. Expected corrected behavior: scoped confirm times out/throws, original row remains PENDING; after unblocking same row/event_id sends and marks SENT; queue gets exact payload. Repeated consumer delivery has one durable effect, covered separately by existing receipt tests. Do not count publisher confirm as consumer processing proof.
7. Restore all alarm/connection settings only on owned broker, shut down owned containers, preserve sanitized receipt. Confirm persistent delivery/queue durability and routing independently; adding confirms alone does not solve unbound exchange.

Proposed exact task after this new test is written:
```bash
./gradlew :services:shared:shared-outbox:compileJava :services:academic-service:academic-app:compileJava :services:academic-service:academic-app:compileTestJava :services:academic-service:academic-app:integrationTest --tests '*OutboxBrokerConfirmIT' --no-daemon
```

Existing targeted broker/DB evidence candidates, run only where their separate risk is required:
```bash
./gradlew :services:academic-service:academic-app:integrationTest --tests '*GroupRenameEventIT' --no-daemon
./gradlew :services:attendance-service:attendance-app:integrationTest --tests '*OutboxAtomicityIT' --tests '*EventConsumerIT' --no-daemon
```
`RabbitConsumerIT.publishToFanoutExchange_doesNotThrow` is wiring only; do not use it as broker-confirm evidence. Existing shared OutboxPublisherJobTest establishes only handling of sender exceptions; a mock throwing by design is not red-before for broker loss.

If L01 confirmed, coherent patch boundary is three production senders + their effective connection confirm config. Auth pattern can be reused if Spring AMQP version is compatible. Review must cover timeout/nack/reconnect whole-batch retry and dedup. No mechanical channelTransacted or new transport architecture.

## 2. L02 red-before: genuine cache, authoritative active and user result

Do not inherit unchanged AbstractAttendanceIntegrationTest: it replaces SemesterCacheService with MockitoBean. Existing SemesterCacheServiceTest is useful but does not cover nonnull stale convergence.

Developer can first create a lightweight controlled actual gRPC-server/real-cache regression to isolate nonnull A→B behavior, then a user-path test on root shared synthetic stand for separate end-to-end risk. Do not claim lightweight server seam proves Academic activation or Rabbit processing.

Minimum stand fixture: full admin with active grant; plain student with active STUDENT grant and own group; A cached in one actual Attendance process, two nonoverlapping adjacent semesters A/B, distinct owned historical/current lessons and attendance. Educational clock Europe/Moscow. Warm A before the boundary, then advance controlled test clock to B start consistently in the involved services and activate B through accepted Academic operation. A historical still-active fixture plus current B is also possible if the harness documents why A persisted active across the boundary; do not create overlapping semester ranges. Preserve histories and old IDs.

Single-instance delivery-delay variant:
1. Warm real SemesterCacheService to A through gRPC; record active Academic A and instance identity.
2. Temporarily stop this owned attendance listener container (not purge queues/delete production bindings). Activate B through actual admin API. Verify Academic active B and original semester.archived event persisted PENDING/SENT with its original event_id. Durable queue must retain the event. This is delayed delivery, not an assertion that offline queue inherently drops it.
3. While listener remains paused but RPC dependencies healthy, call current B attendance projection/request-options as signed student and submit eligible B lesson with a fresh synthetic idempotency key. Record status/code, returned semester/options and any writes. Existing source suggests cache A suppresses B request options and/or selects old A for submission; record actual behavior and guards. A stale read must not be advertised as auth bypass.
4. Resume listener and await exact archived event; real cache must converge B and user operation should succeed with one durable request/event, without mutation of historical A or archived writes. Record bounded time and event claim. This delivered-event success closes recovery only; prolonged event-independent staleness remains a separate diagnostic.
5. Archive active B to no active. Verify request options/submissions return accepted typed empty/error, restore remains inactive; direct legacy report endpoints might return generic500 for missing active and need separate actual runtime classification. Do not require legacy hidden routes to replace the real PWA path.

Two-instance variant only if scale-out is in accepted scope: start A1/A2 with same owned queue and DB, force both caches warm A; switch authoritative active B; deliver one original refresh event; hit each instance directly through same valid synthetic actor token and compare results. Shared queue competing consumers may refresh one cache only. Assert a product result at both instances; no requirement for independent queue design predetermined.

Suggested new regression name `SemesterCacheConvergenceIT` (does not exist yet):
```bash
./gradlew :services:attendance-service:attendance-app:compileJava :services:attendance-service:attendance-app:compileTestJava :services:attendance-service:attendance-app:integrationTest --tests '*SemesterCacheConvergenceIT' --no-daemon
```
Existing separate authoritative producer/race and cache behavior:
```bash
./gradlew :services:academic-service:academic-app:integrationTest --tests '*SemesterArchiveStateIT' --no-daemon
./gradlew :services:attendance-service:attendance-app:test --tests '*SemesterCacheServiceTest' --no-daemon
```

## 3. Remaining independent checks by risk

- L07/P04/P05: reuse actual `AssignmentAuthorityIT.sameTeacherSameTypeOverlapRaceCommitsOneAndReturnsOne409`, `HomeworkStudentCompletionConcurrencyIT`, `HomeworkBindingArchiveIT`, `SemesterArchiveStateIT` and `UserArchiveLifecycleIT`; add only a concrete unsupported conflict. Parallel commands against DB need barrier/latches and query state/history/outbox before/after; no arbitrary blanket optimistic-lock handler.
- L05: actual SubjectController list subject responses as admin, active teacher, own-group student, page1/10/50; capture SQL count and elapsed plus types/active assignments. Growth2N is not a performance FAIL absent observed accepted-user impact; no guessed SLA. Preserve pagination/filter/history if optimizing.
- L06: RPC latency/count at batch1/10/50 and transfer interleaving; assert pending transfer denied or typed retry before mutable Attendance commit. Never remove getLessonById guard to make count green.
- P13: `CacheIT`/`RbacCacheIT` actual Redis, warm→mutation→read and rollback; two actors/groups and teacher/headman revoke. Inspect exact mutation eviction paths; CacheConfig builder alone does not establish afterCommit for every cache.
- P15: `GroupRegistryIT` already covers combined filter/count pagination and stable ties/unknown query. One user HTTP path gives permission evidence for limited/full admin; do not duplicate repository math with mock tests.
- P16/files: actual PWA/BFF schedule200→conditional304→mutation newETag; request attachment owner bytes/hash/MIME/UTF8 Cyrillic disposition/no-store and foreign owner deny; map final-delete synthetic shared content-addressed blob and storage failure+retry. Existing CampusMapAdminServiceTest is source evidence only; actual file storage roundtrip required for file failure risk.
- P18: disposable migrations only:
```bash
./gradlew :services:academic-service:academic-app:integrationTest --tests '*FlywayMigrationIT' --tests '*StudentFoundationMigrationIT' :services:schedule-service:schedule-app:integrationTest --tests '*FlywayMigrationIT' --tests '*StudentOccurrenceMigrationIT' --no-daemon
```
Root may split by measured lease capacity; this command is proposed, not an authorization to run full suites immediately.
- P19: actual allowed endpoint during Academic/Schedule/Mongo/Rabbit unavailability, typed failure no stale writes, then recovery and previously committed outbox/receipts. Probe and route assertions separate; no production fault.
- P20: synthetic storage recovery command, after root assigns images/projects/volume ownership and reads Docker cloud skill reference:
```bash
python3 scripts/test-recovery.py --work-dir /workspace/scratch/rct-validation/recovery-runtime --project-base rct-validation-recovery-20261011
```
This starts owned disposable source and restore Compose resources and records exact schema/data/sequences/Mongo indexes/files; no --cleanup unless owned resources have been recorded. Existing script handles safe cleanup when explicitly selected; never delete external targets.
`test-application-recovery.py capture` and `verify` require a real admitted pending transfer fixture, original request/history capture and restored applications with synthetic private token files. Use actual script args: capture `--operation-id --request-file --history-before --history-pointer --history-lesson-id --quiesced --bundle --output`; verify `--baseline --env-file --academic-container --schedule-container --mongo-container --target-project --schedule-url --history-url --actor-token-file --history-token-file --bundle --output`. Do not print token contents. Validator unit checks or --dry-run are never claimed as real application restore PASS. Offsite/RPO/RTO remains explicitly deferred by owner; no S3 setup.

## Safe next assignment

Assign L01/L02 developer with frozen isolated worktree, sole ownership of three senders/config only if reproduction warrants plus Attendance cache and focused test fixtures; public proto/shared contracts retain root sole owner. First output red-before evidence at baseline, then minimal coherent fix, sufficient targeted tests and independent review. Other rows remain UNKNOWN with these next checks for root integration, rather than delaying this priority packet for exhaustive scan.
