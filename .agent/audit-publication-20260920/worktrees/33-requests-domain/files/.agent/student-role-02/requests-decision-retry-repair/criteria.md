# Acceptance criteria

The frozen repair packet requires the following bounded behavior.

| Criterion | Result | Evidence |
|---|---|---|
| EXCUSE decision whose commit ACK is lost returns the persisted terminal detail | PASS | `StudentRequestDomainIT.ambiguousExcuseCommitRecoversPersistedDecisionWithoutDuplicateEvent` |
| EXCUSE recovery requires the same authorized headman actor, requested outcome and normalized comment | PASS | recovery path at `StudentRequestService.java:1137`; mismatch coverage in `terminalDecisionDoesNotReplayForAnotherActorOutcomeOrComment` |
| LATE_CHECKIN decision whose commit ACK is lost returns the persisted terminal detail | PASS | `StudentRequestDomainIT.ambiguousLateCheckinCommitRecoversPersistedDecisionWithoutDuplicateEvent` |
| LATE_CHECKIN recovery requires the same authorized actor and requested outcome | PASS | recovery path at `StudentRequestService.java:1156`; mismatch coverage in the same IT |
| Recovery does not run a second transaction, attendance write or terminal event publication | PASS | transaction execution counter is `1`; each ambiguity test asserts the resulting attendance and exactly one persisted event |
| A transient failure before transaction execution still retries normally | PASS | `uncommittedTransientDecisionFailureStillRetriesOnce` |
| Opposite outcome, another actor and different EXCUSE comment do not replay a terminal decision | PASS | `terminalDecisionDoesNotReplayForAnotherActorOutcomeOrComment`; one event remains |
| Existing authorization, self-decision, cancellation, PRESENT priority and generic retry behavior remain covered | PASS | focused authorization test plus the 19-case real-Mongo domain run |
| Scope remains limited to the frozen service and focused domain test | PASS | `diff.md`, source comparison against frozen snapshot |
