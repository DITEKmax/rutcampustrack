# Delivery/data diagnostics

Revision: `3908fad9624f97aec8891fc79338ceb6b0616bf3` (HEAD read back; tracked product unchanged). Requested model/effort in packet; actual metadata UNKNOWN. Sole writer of this receipt directory. No child, Gradle, Docker or browser launched. Runtime for all rows NOT_RUN; scenario status UNKNOWN. This is a bounded source diagnostic, not final product acceptance.

## Priority handoff

### DD-L01 — broker acceptance before outbox SENT (hypothesis; no assigned severity)

Actual symbols:
- `services/shared/shared-outbox/src/main/java/ru/rutcampustrack/shared/outbox/OutboxPublisherJob.java#publishBatch` sends every record before marking any SENT. Any sender exception aborts and leaves entire batch retryable; duplicates after a partially successful batch are intentional at-least-once.
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/RabbitOutboxEventSender.java#send` (30), equivalent attendance sender (28), schedule sender (28): direct `RabbitTemplate.send`, without scoped confirms.
- Academic `event/RabbitConfig.java#rabbitTemplate` (182), attendance `config/RabbitConfig.java#rabbitTemplate` (126), schedule `event/RabbitConfig.java#rabbitTemplate` (80) construct ordinary RabbitTemplate; the reviewed default application files have no publisher-confirm configuration. Effective runtime overrides still need observation; source does not prove all deployed configurations.
- Auth `event/RabbitOutboxEventSender.java#send` uses invoke + `waitForConfirmsOrDie(5000)` and persistent messages; Auth application.yml line18 sets simple confirms. This is an existing implementation pattern, not proof of consumer effect or correct routing.

Accepted user connection: canonical JS-SYSTEM-10 explicitly requires guaranteed headman delivery for late_checkin.requested; JS-HEADMAN-16/17 group homework publication/update and JS-HEADMAN-09 cancellation depend on reliable events. Producer→fanout exchange `rut-uit.events`→service durable queues→durable consumer receipts must remain intact. Owner 2026-10-02 durable preferences requires defer/retry on unavailable authoritative preferences. Link to every particular event is to be resolved by root matrix, rather than treating all transport events as independent stories.

Existing tests, inspected but NOT_RUN here:
- shared-outbox OutboxPublisherJobTest: senderThrows/partialFailure leave PENDING; mocked sender, does not establish broker acceptance.
- Academic AbstractAcademicEventIntegrationTest has real PostgreSQL and Rabbit, production sender, manual TransactionTemplate flushOutbox, and scheduling disabled through OutboxTestConfig. GroupRenameEventIT actually reads event bytes/event_id after real publish. This is the smallest reusable fault harness.
- Attendance RabbitConsumerIT checks queues and no-throw send; its last method does not inspect delivery. EventConsumerIT publishes actual Rabbit messages and asserts actual Mongo effects and close/cancel convergence; downstream gRPC and SemesterCacheService are mocks.
- Attendance OutboxAtomicityIT verifies Mongo domain+outbox commit/rollback with real DB; independent of publisher acknowledgement.

Needed proof: bound queue exists; synthetic user operation commits domain+outbox; broker failure after client publish before broker acceptance; outbox must remain PENDING, recovery must deliver exact event_id and consumer durable effect once. A normal return alone, absent annotation, grep match or missing runtime run is not a DEFECT.

### DD-L02 — local semester cache convergence (hypothesis; no assigned severity)

Actual symbols:
- Attendance `semester/SemesterCacheService.java#getActiveSemesterId` (38) lazy refreshes null only; nonnull volatile ID has neither TTL nor independent reconciliation. `refresh` clears on authoritative NOT_FOUND; transport failure preserves old nonnull ID.
- Academic `semester/SemesterService.java#activateLocked` (253) atomically changes active semester and publishes `SemesterArchivedEvent` for previous active (322), so normal switching does invalidate through the current event route. Do not claim activation simply lacks an event.
- Attendance `event/EventConsumer.java#onEvent` (55) consumes `attendance-service.events`, claims in Mongo TransactionTemplate, dispatches semester.archived to cache.refresh (287). Durable queue outage alone should backlog/replay rather than lose the event.
- All attendance instances share that queue; one queue means competing consumers, whereas each SemesterCacheService is process-local. Two-instance invalidation is a distinct hypothesis if scale-out is accepted.
- `studentrequest/StudentRequestService.java#requireCurrentSemester` (1901) uses the cached ID for request options/submissions (218,243,310). Wrong cache can select old schedule or reject otherwise valid current lessons; immutable/archived writes have additional pair fences and must not be represented as an automatic auth bypass.
- `grpc/AttendanceStudentGrpcServiceImpl.java#projectionRequestOptions` (505) suppresses options when cached ID differs from requested semester. BFF StudentQueryService.attendance uses authoritative active semester before invoking the projection. User-observable candidate: current B attendance renders without eligible request options while instance remains cached A.

Existing tests, inspected but NOT_RUN:
- SemesterCacheServiceTest.archiveLastActiveSemesterClearsOldIdAndNextReadFindsNewActivation tests delivered refresh + authoritative absence + lazy activation. It does not test missed refresh or nonnull stale convergence.
- transportFailurePropagatesOnRefreshAndKeepsExistingCachePolicy explicitly preserves cached11 after UNAVAILABLE.
- SemesterArchiveStateIT.archiveAndActivation_areSerializedAndKeepAtMostOneActiveSemester uses actual PG; proves one active and one original archived event, not consumer cache convergence.
- AbstractAttendanceIntegrationTest @MockitoBean-replaces SemesterCacheService and AcademicGrpcClient. Reusing it unchanged cannot reproduce actual cache; the new fault test needs a real cache bean and real gRPC boundary or actual Academic service.

Owner semantics are already sufficient: JS-ADMIN-09 active count zero/one and atomic switching; 2026-09-29 archive clears active, no automatic replacement, restore stays inactive; 2026-10-09 archive date superset retains history. No new stale-cache policy is to be invented. Runtime should measure convergence to authoritative active or a typed dependency failure, with no stale write effect.

## Other dispositions (bounded)

L05: actual SubjectController.listSubjects maps each row through SubjectAssembler.toResponse, which makes lesson-type and published-active assignment repository reads. Scope: admin all, teacher active assignment/current Moscow dates, student own group. Query fan-out exists statically; user performance defect/latency threshold not established. Measure rows1/10/50, SQL count and exact response before batching; optional if no measured relevant impact. Do not introduce arbitrary SLA as owner requirement.

L06: actual Attendance ScheduleGrpcClient.getLessonsByIds deadline3s batch followed by getLessonById for every result; requireAttendanceMutationReady revalidates mutable writes. ScheduleGrpcServiceImpl.getLessonsByIds guards transfer state. Retain guards; measure and race transfer before changing batch strategy. Existing client test covers protected scoped denial/pending-transfer, no evidence of accepted optimization need.

L07/P04/P05: real DB test candidates are already substantial: AssignmentAuthorityIT sameTeacherSameTypeOverlapRaceCommitsOneAndReturnsOne409, UserArchiveLifecycleIT archive/helper race, SemesterArchiveStateIT archive/activation and mismatched receipts, HomeworkBindingArchiveIT terminal cancellation races, HomeworkStudentCompletionConcurrencyIT one target row and foreign-group denial. Methods are existing source evidence, not new runtime PASS. Prioritize a concrete uncovered conflict rather than a global optimistic handler or Serializable migration.

P13: CacheConfig has per-cache TTLs and limited polymorphic mapper; SubjectService.listSubjects is role scoped and rechecks teacher grant, roster intentionally uncached. CacheConfig RedisCacheManager does not call transactionAware in the reviewed builder. Do not globally claim every eviction is afterCommit based on research alone; mutation-specific eviction/rollback must be checked. RbacCacheIT/CacheIT are candidate existing checks.

P15: GroupRegistryReadRepository shares filter expressions for list/count and allowlists sort, adds stable id tie-breaker. GroupRegistryIT covers filters before pagination/count parity/stable ties and invalid query values. Reuse it, not generic JOIN FETCH.

P16: BFF StudentApiController schedule emits private/no-cache ETag and 304; request attachment sets UTF8 Content-Disposition and no-store. User bytes, MIME, Cyrillic filename and ownership need actual HTTP test. Campus-map file deletion current owner rule is final all versions but retain blobs referenced by other schemes, and retry storage failures without falsely successful deletion.

P18: FlywayMigrationIT in academic/schedule already has disposable fresh-chain/checksum/historical preservation cases; further StudentFoundationMigrationIT and StudentOccurrenceMigrationIT enforce histories and migration constraints. Never repair/clean production to get PASS.

P19: health/readiness exists per brief, but probes do not prove user route during outage/recovery. No new observed failure here; keep actual dependency outage and drain as next check.

P20: scripts/recovery.py inventories PostgreSQL schema/data/sequences, Mongo documents/indexes/options and regular files; verifies immutable checksummed bundle and fresh isolated targets, rejects unsafe tar entries/symlinks. scripts/test-recovery.py uses synthetic disposable targets; scripts/test-application-recovery.py additionally checks real restored transfer completion/participants/outbox/history/exact HTTP replay. Evidence validator tests alone are not application recovery proof. Owner offsite provider and RPO/RTO deferred to deploy by 2026-10-02; disposable recovery remains current acceptance.

## Questions and limitations

No new essential product question found. Single-instance missed refresh can be diagnosed now. Scale-out scope is an orchestration/acceptance decision for root: if already accepted, run two instances; if not, document two-instance result as optional risk instead of silently requiring production HA. No raw research original personally reopened; use contract source IDs and current repository symbols. Candidate story/backend-map links remain candidates until root accepts source resolution. No confirmed defects, no fabricated PASS, no heavy resource held.

Created: findings.md, scenarios.csv, next-checks.md in this directory; modified/deleted product/shared docs: none.
