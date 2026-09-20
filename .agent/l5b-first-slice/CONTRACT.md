# L5B first dependency and assignment-close boundary

## Goal
S3 planning handoff on accepted 13e5fd1985b798bbb61bcc85969e6a74b1fc6837. First implementation should complete service identity/admission needed by assignment closing. Do not enable a partial close operation. This is a root-freeze proposal, not a code assignment.

## Context/evidence
NEXT-L5B-START.md authorizes lead-only planning. RULES SHA B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A. Accepted design .agent/lessons-l5b-design/PROPOSAL.md SHA836D567E86F868102819DACD283ECFFA0AC068B308C5A5643BC44A3C7109E0A0, root acceptance/addenda and CORRECTION-H74-2026-09-20.md apply. Current deadlock claim is withdrawn; no lock-order fix follows from that old claim. Public mass cancellation already removed at13e5fd.

Originals read in clean .agent/worktrees/v2-runtime-build-r3 (no writes): AssignmentService.java:107–117 and SubjectService.java:276–291 still authorize exact own assignment/path and throw AssignmentClosureNotReadyException; proto/academic.proto:55 exposes GetAssignmentsByIds, no prepared-close read; proto/schedule.proto:8–43 has existing reads/history/bindings, no close-cap/receipt RPC. Academic ScheduleGrpcClient currently exposes resolveLesson/countSubjectReferences with deadlines; Schedule AcademicGrpcClient is existing transport wrapper. Academic V25 defines assignment immutable identity/validity; latest Academic migration V26. Schedule V17 defines assignment/occurrence/physical snapshots and retained history; latest Schedule migration V17. Migration numbers below are intentionally not allocated before root freeze.

## Relevant scope
Lead owns only this planning directory; no scout required for known originals. Access owns credential mechanism/admission recommendation, root owns shared freeze. Recommended first code scope is the bounded service-auth dependency selected with Access, not simultaneous Academic ledger + Schedule producer rewrite. Later first complete close feature spans Academic ledger/coordinator and Schedule fence/receipt/physical-writer gate; it cannot truthfully fit only one REST handler.

## Required behavior
First auth dependency: distinguish authenticated Academic and Schedule service principals from user JWT; fail closed for absent/invalid/wrong-service credentials and denied full RPC method before domain access. Preserve existing unrelated caller behavior by an explicit method-scoped rollout, not blanket activation that silently breaks all existing clients. Credential format/trust provisioning/allowlist must be frozen from Access recommendation; no optional shared-secret identity shortcut. Do not expose a functional new domain mutation merely to demonstrate auth.

Future close protocol boundaries (accepted v3 §§4.1–4.2):

- Academic owns a durable assignment_close_operations ledger: server operationId, actorId/requestKey, canonical payloadHash, exact assignmentId and tuple {groupId, subjectId, semesterId, teacherId, lessonType, validFrom}, requested exclusive D, target service, state, receipt and result. Unique actor/key; same key/different payload conflicts; at most one nonterminal operation per assignment. D must satisfy validFrom < D <= current effective end. Public actor comes from authenticated context, never JSON.
- Both current close routes delegate to one coordinator only at full activation. PREPARE authorization/path/validity/pending checks and durable insert occur in one Academic transaction. RPC runs after commit, outside DB locks. A new narrow GetPreparedAssignmentCloseOperation RPC returns persisted exact authorization to Schedule; arbitrary client tuple is not authority.
- Schedule InstallAssignmentCloseCap receives operationId/assignmentId/D/hash. Before local locks it authenticates caller and validates persisted Academic operation including intended audience and exact tuple. assignment_fences PK assignmentId retains immutable tuple and concrete monotonic cap/revision. Immutable close receipts bind operationId/hash/requestedD/acceptedCap/fenceRevision or terminal retained-reference rejection. Same request returns the recorded result; mismatched replay conflicts.
- Under the common fence lock, a fresh READ COMMITTED query considers ALL retained physical lessons at date >= D, including cancelled/transferred/superseded rows. No status/current filter. Cap change and receipt are atomic. A database insertion guard and every physical creator use the same fence protocol; V17 protection remains enabled. Missing provenance cannot be guessed or backfilled to pass.
- Academic FINALIZE rechecks exact operation/receipt/identity under the accepted ordered lock protocol, shortens end without reopening, and commits result. Timeout/lost response remains pending, never false rejection. Root policy A permits authenticated recovery of the exact previously authorized operation after user revocation; no new scope/new operation. No automatic compensation that widens cap.

## Constraints
Bells and university-vs-semester study days are not inputs to service credentials, immutable close ledger, exclusive calendar date D, retained-reference query or replay. These dependencies can proceed now. One-off creation/time resolution and ambiguous past-restore/calendar behavior remain outside this slice. No existing user authority expansion, migrations rewrite, history deletion, full lifecycle/event rollout or half-enabled close endpoint.

## Existing patterns
Reuse existing gRPC interceptors/client wrappers, method-admission tests and deadlines identified by Access. Domain tests later reuse accepted real-PG fixtures/V25/V17 safeguards. Do not revive the withdrawn FOR UPDATE deadlock diagnosis: future new lock edges need their own actual proof. CountSubjectReferences is a deletion precheck, not assignment-close fencing or atomic permission.

## Acceptance criteria
First auth slice has real positive caller admission and negative absent/invalid/wrong audience/wrong service/wrong method checks; rejection occurs before domain reads/writes. Existing selected method behavior remains explicit. Public close still returns typed409 and creates no pending ledger/cap side effects. Root freezes credentials and exact files before developer dispatch.

First complete close activation later additionally requires ledger/idempotency/receipt persistence, all-writer plus DB fence, concurrent creator-vs-cap real-PG proof, all-retained-reference refusal, duplicate/hash conflict, crash/lost-response recovery, no-reopen after revocation, and exact-path/foreign-group denials. Ledger-only or cap-only work is a dependency, not a completed close feature.

## Verification
Planning only: critical original reads, no runtime/build/repeated accepted tests. First auth implementation should run targeted real interceptor/channel positive/negative tests plus independent full review. Domain stage later gets root-owned PG/concurrency and cross-service tests at stable diff. Access recommendation supplies exact credential-specific cases before freeze; no broad audit prerequisite.

## Do not
No product changes, new scout, new runtime, secret reads, proto tags or migration numbers assigned here, speculative auth framework, code/test reruns on accepted117 cases, or statement that closing is enabled. No automatic next stage after this handoff.
