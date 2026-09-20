# Evidence and diff — paused

The repair follows the frozen `requests-domain-review-repair-packet.md` and preserved `requests-domain-review-1.md`. The owner additionally confirmed that `LessonEventService.processLessonCancelled` writes `AttendanceStatus.CANCELLED`; this repair therefore excludes CANCELLED from EXCUSED writes and does not modify lifecycle handling.

Current repair-only content hashes at pause:

- `StudentRequestService.java` patch hash: `161fc400568811084eae00a8f5f0548a30262c11`
- `StudentRequestModels.java` patch hash: `e4d49fa993f65f9da6b430be42201ac8679e6383`
- `StudentRequestDomainIT.java` patch hash: `4627b1d3918c2298f856ae92251d7ee6c9931594`
- combined tracked repair diff fragment (`excuse.decided.json`, `LateCheckinEventPublisher.java`) hash: `be05bbe964b4694ce847272cd3dbff2d2efe5dd0`

The checkout already contained the frozen 28-file implementation diff and its evidence. This repair modifies only task-owned studentrequest source/tests plus the affected late publisher and excuse terminal schema. The worktree is deliberately dirty and must be preserved.

Limitations: compile/test result not yet available; authorization test conversion and required concurrent Mongo evidence remain unfinished; no independent recheck has occurred.

## 2026-09-07 resumed repair — final source and runtime evidence

The paused limitations above are superseded by `checks.md` current evidence. Final repair-relevant SHA-256 values are:

- `StudentRequestService.java`: `CA5CA3D9F7598DEBFBEE82B18B1B7A8E7E6C9D8E0D7536F75AF7ADCB87D9856E`
- `StudentRequestModels.java`: `DEFCA9A5DC3D03F96A8E184B7040638CD04418DC014AB1DA01528EE7F09B25C2`
- `LateCheckinEventPublisher.java`: `94EFD22554A5FF3ACDEC684AC1B56C07F03259EF1E5CA914C5E84DFB17F0E50D`
- `StudentRequestDomainIT.java`: `BBD408F9E9A0D7F8EF6B51EC806BCE8D2C90FEBB0783C4BAD90C47090D95A91A`
- `StudentRequestServiceAuthorizationTest.java`: `7F96615F5A43BC8BD020BC51A4F3951340C3F9088363A56ABE8EF95CC373C322`
- `LateCheckinEventContractTest.java`: `6E0AE3179A52EE9482B87EF3A5180B6B1B563475CD018615D1D590C5C91C6480`
- `ExcuseEventPublisherTest.java`: `4CA61BBB52BA3F5193F653B542BA59F54D97C5BCDD20FBA849F54EC77C376E8E`

Reproduced defects and corrections:

1. Compile reproduced missing `LateCheckinResolutionReason` import in the canonical-wire publisher and replay calling owner detail without `Identity`. The publisher import and identity-threaded replay are now compiled.
2. The initial new budget test reproduced a `BadRequestException` because its idempotency keys were under the documented 16-character limit. Only the test fixtures were lengthened; the budget behavior was not weakened.
3. The first `test --tests StudentRequestDomainIT` command returned zero without running the repository's integration task. The stale XML exposed this; `integrationTest` is the accepted runtime command.
4. A 14/14 Mongo result used a test-only in-memory outbox. It was rejected. The IT now wires production `MongoOutboxStorage`, queries its task-owned Mongo collection after futures, and validates the persisted terminal payload schema.
5. The first durable run reproduced Mongo error 112 / `TransientTransactionError` from the test PRESENT writer at `PairWriteCoordinator.lock`. The helper now mirrors the existing `StudentCheckinService` four-attempt boundary for this observed label; it retains the barrier and final-PRESENT assertion.
6. The final 15/15 durable run includes a forced exception immediately after `MongoOutboxStorage.save`; `findPending(10)` is empty afterward. This proves the current installed runtime rolls back the durable outbox record for that transaction. No shared-outbox source was changed.

Runtime scenarios in the final Mongo XML: six concurrent budget submissions; same-key receipt; package rollback; overlapping packages; attachment persistence/expiry; owner projection; PRESENT preservation; non-refund; persisted limit 7/used 5; FREE→EXCUSED; cancel-vs-decision with one terminal late status and one persisted event; approval-vs-PRESENT with one persisted EXCUSE event and final PRESENT; and the production-outbox rollback probe.

Remaining limitation: this is bounded domain evidence only. Fresh independent Sol high recheck is pending; no public transport, bot, proto or lifecycle contract change was made.
