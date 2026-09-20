# Requests transport evidence checkpoint

Date: 2026-09-08. Risk: S3. Branch: `codex/student-role-02-requests-transport`.
Baseline and current `HEAD`: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`;
the worktree is intentionally dirty and contains the bounded transport change.

## Scope

The implementation covers the accepted Requests domain adapters: Attendance
REST-facing gRPC and bot-secret boundary, typed gRPC errors, BFF Student API
DTO/controller/facade/client, event schemas/publishers/consumer, bounded
listener retry/DLQ policy, generated OpenAPI/protobuf/Python/TypeScript seams,
and focused tests. Existing foreign work in the same worktree is preserved.

## Criteria and evidence

| Criterion | Status | Evidence |
| --- | --- | --- |
| Typed Requests transport compiles | PASS | Attendance `compileJava`, exit 0. |
| Null StudentRequestService compatibility fallback is removed | PASS | `EventConsumer` requires the service; focused unit test injects the mock; EventConsumerTest exit 0. |
| Typed error mapping and validation boundaries | PASS | Focused `StudentRequestGrpcErrorsTest` is included in the Attendance test run; compile and unit suite exit 0. |
| Decision listener bounded retry and DLQ | PASS in isolated fixture | `RabbitDecisionRetryIT`: invalid trusted envelope reaches DLQ; transient decision fails exactly three calls then reaches DLQ; isolated Gradle run exit 0, 2 tests. |
| Existing EventConsumer integration fixture | PASS in isolation | Isolated `EventConsumerIT` run exit 0 after task-owned queue purge. |
| Neighboring integration regression | OPEN | Combined `EventConsumerIT` + `RabbitDecisionRetryIT` run exit 1: 11 tests, one `WantedButNotInvoked` at `RabbitDecisionRetryIT.java:93`; each class passes alone. See defect gate item 10. |
| Frontend/generated/bot seams | PASS | Frontend typecheck/lint/contract/type-generation checks, targeted client tests, protobuf generation, JSON parsing and bot `compileall` all exit 0. |
| Full ingress/Gateway/XFF and exact 2x10 MiB path | OPEN | Deferred until root supplies accepted dependency and ordered XFF/config baseline; no claim from direct fixtures. |

## Corrections recorded

The null Academic authority response now fails closed as access denied; stale
authenticated bot terminal conflicts are acknowledged without mutation; bot
decision comments use the shared 1000-character bound; the Attendance listener
uses three attempts with bounded backoff and reject-to-DLQ recovery. No old
lesson lifecycle behavior was changed to address the transport gate.

## Limitations

The broker `source` field is validated at the trusted broker boundary; this is
not a cryptographic producer-proof claim. Gateway/XFF, ingress byte limits,
full HTTP-to-gRPC runtime and Python pytest/ruff remain root-owned/open. The
available Java processes could not be attributed because process inspection was
permission-denied; no owned Gradle/Testcontainers session remains active.
