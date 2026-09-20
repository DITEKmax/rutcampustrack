# Requests-domain review repair — paused checkpoint

Paused 2026-09-07 on owner request. Worktree HEAD remains `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`; no commit, reset, delete, deploy, or main integration was performed.

## Scope and frozen criteria

S3 bounded Terra repair of the frozen six Sol findings only: explicit immutable identity without servlet scope; decision self-guards plus persisted-group Academic revalidation; canonical terminal event wire/schema; persisted late budget; logical attachment expiry; EXCUSE priority for live non-PRESENT records while preserving PRESENT and CANCELLED. Public transport, bot, proto, UI, config/dependencies and lifecycle sources are out of scope.

## Progress

Implemented but not yet compiled/accepted:

- `StudentRequestModels.Identity` and identity-first request-domain entrypoints; `StudentRequestService` no longer imports or stores `RequestContext`.
- decision actor derives only from identity and is revalidated against the persisted request group through `AcademicGrpcClient.isHeadman`; self decisions reject before locks/writes.
- late cancellation maps `CANCELLED_BY_STUDENT` to canonical `student_cancelled`; excuse schema accepts terminal `cancelled` and null comment.
- options tests remaining budget; enforcement reads persisted limit with default five.
- detail projects an attachment as `EXPIRED` at the clock boundary without clearing bytes; existing download/sweeper ownership remains responsible for byte clearing.
- EXCUSE approval preserves PRESENT and CANCELLED; other live states, including FREE_ATTENDANCE, are written EXCUSED.
- Began converting the task-owned Mongo test to explicit identities. It is intentionally incomplete at pause and must be compiled before review.

## Remaining repair work

1. Finish compiling the explicit-identity test conversion and replace the authorization test's RequestContext fixture.
2. Add focused negative identity/self-decision tests, schema validation for actual emitted terminal outbox variants, budget 5/7/exhaustion tests and detail-before-download expiry test.
3. Add required real Mongo barrier tests: cancel vs decision with exactly one terminal outcome/outbox; approval vs pair-coordinated PRESENT writer; FREE_ATTENDANCE transition. Do not represent existing sequential tests as races.
4. Run only affected compile/focused/Mongo checks, record actual counts (focused 45 and Mongo 10 were previous totals; aggregate 55), stable source hash, then await independent Sol recheck.

## Runtime ownership

No task-owned Mongo container/runtime was started in this repair turn. The attempted `--no-daemon` Gradle compile had no observable final exit before the pause; no `java` process was visible through `Get-Process` afterward. A command-line inspection via `Get-CimInstance Win32_Process` was denied by the environment, so no process was stopped blindly.

## 2026-09-07 resumed repair — current state supersedes the paused state

S3 bounded repair remains frozen at worktree HEAD `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`; no commit, reset, deployment or main integration occurred. The initial 30-file product snapshot is preserved.

Completed: explicit immutable `Identity` has no servlet fixture in the authorization test; identity is carried into idempotency replay; self approval/rejection, foreign group, non-headman and missing-role negative cases are covered. The canonical late cancellation wire plus all EXCUSE terminal variants are validated against emitted schemas. Persisted budget/options, logical detail expiry and FREE→EXCUSED were verified while PRESENT and CANCELLED remain protected.

Current Mongo verification uses production `MongoOutboxStorage` with a task-owned collection, not the earlier in-memory capture. The two barrier scenarios read persisted outbox records, and a forced failure immediately after the production outbox insert proves rollback in this installed runtime. The PairWriteCoordinator PRESENT writer is a test-only simulation, retries the observed Mongo `TransientTransactionError` for four immediate attempts matching `StudentCheckinService.MAX_TRANSACTION_ATTEMPTS`, and is not claimed as full checkin integration.

PASS evidence: compile, test compilation, 49 focused tests and 15/15 current Mongo integration tests. `checks.md` and `evidence.md` record exact commands, counts and rejected intermediate results. The only remaining gate is a fresh independent Sol high recheck; transport/bot/proto/lifecycle are downstream and not PASS claims.
