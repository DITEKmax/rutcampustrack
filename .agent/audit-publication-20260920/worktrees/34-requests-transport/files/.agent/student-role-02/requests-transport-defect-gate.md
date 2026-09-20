# Requests transport defect gate

07.09.2026, branch `codex/student-role-02-requests-transport`, base
`d3c31acb8cce53791a4981e5858a37d44fdc9a0e`. Scope stays within the frozen
Requests transport contract.

## Recorded defects and correction

1. **Missing decision schema (contract defect, S3).** The frozen contract
   requires a strict `event-schemas/excuse.decision.json`, while the checkout
   contained only `late_checkin.decision.json` (`Get-ChildItem event-schemas
   *decision*` reproduced the omission). Correction: add the schema with the
   version/source/actor/id/approval/comment envelope rules; tighten the late
   schema to the same trusted source and strict payload boundary.
2. **Unexpected Requests gRPC failures were mislabeled as dependency failures
   (transport defect).** `StudentRequestGrpcErrors.code` returned
   `DEPENDENCY_UNAVAILABLE` for an unmatched `RuntimeException`, and
   `grpcCode` then emitted `UNAVAILABLE`. Correction: classify unmatched
   failures as `UNSPECIFIED` and emit `INTERNAL`; known typed domain and
   dependency exceptions retain their existing mappings.
3. **Null Academic authority response could escape as an NPE (authz defect).**
   `StudentRequestService.requireDecisionAuthority` dereferenced
   `academicGrpcClient.isHeadman(...).getIsHeadman()` without checking a null
   response. Correction: fail closed as `AccessDeniedException`; bot authority
   already uses the same null-safe rule.
4. **Authenticated stale bot decisions were surfaced as retryable conflicts
   (event semantic defect).** A terminal request with a different trusted
   actor/outcome/comment threw `ConflictException` from the bot adapter, so the
   consumer could NACK instead of acknowledging the stale event with no
   mutation. Correction: catch only this adapter's terminal conflict in
   `EventConsumer`, log the stale event and ACK; validation and authority
   failures remain propagated to retry/DLQ.
5. **Bot decision comments were not bounded at the trusted adapter.** The
   public excuse submission limits comments to 1000 characters, but the bot
   decision path only validated the JSON type. Correction: enforce the same
   normalized `<=1000` bound before mutation, yielding typed invalid request.

6. **Decision-event retry/DLQ behavior was only asserted by source comments.**
   Root runtime evidence reproduced repeated listener failures from the
   unchanged `RabbitConsumerIT.publishToFanoutExchange_doesNotThrow()` fixture:
   it publishes a missing-`event_id` envelope, `IdempotencyGuard` fails closed,
   and no Attendance retry/requeue interceptor was present. This is a baseline
   transport defect surfaced by the new decision-event criterion, not a reason
   to weaken the guard or change lesson lifecycle logic. Correction: configure
   the Attendance listener with an explicit three-attempt retry policy for
   transient dependency/data-access failures and a reject-to-DLQ recoverer;
   malformed/unauthorized decision envelopes remain non-retryable and are
   rejected to the same DLQ. Add a real Rabbit integration check for both
   bounded transient retry and invalid-envelope DLQ termination.

7. **Retry integration test did not compile (test-only defect).** The first
   recheck of the new Rabbit test failed because AssertJ could not choose
   between `assertThat(IntPredicate)` and `assertThat(Predicate<T>)` for the
   nullable `x-death` header (`RabbitDecisionRetryIT.java:62`). Correction:
   make the header assertion explicitly object-typed; verification is the same
   focused Gradle test rerun after the correction.

8. **Event integration regression check exposed an existing fixture-isolation
   failure.** The first neighboring event integration batch failed only
   `EventConsumerIT.lessonClosed_existingCheckin_preservesCheckinStatus()` at
   line 132: `LessonEventService` received `null` group members and the bounded
   listener sent the message to DLQ. The same check is rerun in an isolated
   class to establish whether the failure is caused by cross-class reused
   Rabbit/Mongo state or by the new listener policy before any code correction.
   Isolated `EventConsumerIT` rerun passed; correction is limited to purging
   task-owned main/DLQ queues in that fixture before each test so stale
   cross-class deliveries cannot replace the current test's gRPC stubs.

9. **Production event consumer retained an unauthorized compatibility path.**
   Root review found both `studentRequestService == null` branches in
   `EventConsumer` calling the retired `LateCheckinService`/`ExcuseService`
   adapters. The packet explicitly forbids compatibility fallback. Correction:
   remove those branches and their obsolete fields; update the focused unit
   fixture to inject the required `StudentRequestService` mock, then rerun the
   consumer unit and Rabbit integration checks.

## Verification gate

Reproduce with the focused Java tests and schema checker listed in
`.agent/student-role-02/checks.json`; each correction is followed by the
focused recheck. No gateway/XFF or product-runtime scope is opened by these
bounded corrections.

## Remaining verification finding

10. **Combined integration classes still share fixture state (verification
    finding, unresolved).** The post-correction command
    `.\\gradlew.bat :services:attendance-service:attendance-app:integrationTest
    --tests "*EventConsumerIT" --tests "*RabbitDecisionRetryIT"
    --rerun-tasks --console=plain` reproduced exit `1`: 11 tests ran and
    `RabbitDecisionRetryIT.transientDecisionFailure_retriesThreeTimesThenDeadLetters`
    failed at line 93 with `WantedButNotInvoked`, while each class passes when
    run in isolation. This is consistent with reused Spring/Rabbit/Mongo
    fixture state across classes; no new correction is made after the root
    stop instruction. Root must decide whether to isolate the test context or
    accept the isolated evidence before integration.
