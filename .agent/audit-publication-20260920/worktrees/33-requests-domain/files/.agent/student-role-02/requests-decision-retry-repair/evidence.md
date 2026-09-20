# Evidence

## Frozen defect and reproduction

The frozen baseline is revision `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
Before correction, `StudentRequestService.java` was SHA-256
`CA5CA3D9F7598DEBFBEE82B18B1B7A8E7E6C9D8E0E7536F75AF7ADCB87D9856E` and
`StudentRequestDomainIT.java` was
`BBD408F9E9A0D7F8EF6B51EC806BCE8D2C90FEBB0783C4BAD90C47090D95A91A`.

The test-only `FaultInjectingTransactionTemplate` wrapped the real
`MongoTransactionManager`, let `super.execute(action)` complete, and then
threw a `MongoException` carrying
`UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL`. The pre-repair command was:

```text
.\gradlew.bat --no-daemon :services:attendance-service:attendance-app:integrationTest --rerun-tasks --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestDomainIT
```

It exited `1`. The fresh XML at that point had timestamp
`2026-09-07T14:15:24`, `tests=18`, `skipped=0`, `failures=2`, `errors=0`.
Both EXCUSE and LATE_CHECKIN ambiguity scenarios reached the old terminal
state conflict after the whole-body retry, although the terminal document and
event had already been committed.

## Correction

`decideExcuse` (`StudentRequestService.java:1043`) and
`decideLateCheckin` (`:1088`) now use `executeDecisionWithRetry` (`:1689`). On
an unknown commit result, the helper performs a read-only, caller-specific
recovery. `recoverExcuseDecision` (`:1137`) and
`recoverLateCheckinDecision` (`:1156`) revalidate authenticated student role,
group/headman authority and the self-decision guard before comparing the
persisted terminal status and decision actor. EXCUSE also compares
`normalizeComment` output. A non-matching terminal state returns to the
existing retry path and then raises the existing conflict; it is never treated
as a replay. A failure inside the recovery read is propagated by the current
helper and is not claimed as generally recoverable by this evidence.

Current source SHA-256 values are:

- `StudentRequestService.java`:
  `5095C2A63EDF7E6561F6C08E2071C97270489A5C1CDA1B73EF64507B3132A662`
- `StudentRequestDomainIT.java`:
  `968F4A4967D0AD2FDB0055C647D709F6A0A020B59DDB3396C64007E57A150DE6`

The ambiguity tests use the production outbox wiring and validate the
persisted event against the event schema. They assert one transaction
execution, the expected `EXCUSED`/`PRESENT` attendance result, and one
terminal event. The transient test injects its failure before transaction
execution and confirms the ordinary retry succeeds.
