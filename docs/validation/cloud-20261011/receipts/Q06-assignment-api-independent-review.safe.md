# Q06 independent frozen-source review

Verdict: **REVIEW_BLOCKED — 1 P1 and 1 P2**. Review concerns one new cross-service API fixture, not whole-product acceptance.

Reviewed immutable `git show 3367450387919ea268e2cc3bb0b67c805c4f7481:tests/e2e/fixtures/assignment-lifecycle.spec.ts`, 614 lines, SHA256 `71b7f4724642d9e140c3e9e3f74032c63378dd06f31fc464ba7bed11160130ae`; author base `fd864eb028f271537d24d7b6c065b553c56d6440`, accepted baseline `3908fad9624f97aec8891fc79338ceb6b0616bf3`. All fixture line references below belong to that frozen source; the author has switched branches and its current working file is not the review target. Requested/selected child Sol6.1/high; actual runtime metadata UNKNOWN.

## Blocking findings

1. **P1 — Shared teaching-clock advancement permanently changes other datasets.** Location: `/workspace/rct-worktrees/first3-spec-frame-20261013/tests/e2e/fixtures/assignment-lifecycle.spec.ts:339` (restoration at 402).

   `readTeachingInstant` requires the shared teaching instant to be 09:00 Moscow. Preparation changes it to `today T09:00Z`, which is **12:00 Moscow**, and waits up to 90 seconds for the normal lifecycle job. That job selects due lessons globally, without a fixture group/subject filter: `/workspace/rutcampustrack/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java:89`, `:133`, and `:144`; the underlying global queries are in `lesson/repository/LessonRepository.java:325` and `:340` under the same service source root.

   Concrete counterexample: an earlier MobileDataset has today's first lesson ACTIVE at 09:00. At 12:00 its 09:50 end plus five-minute grace has passed, so the job closes it and emits durable lifecycle effects while Q06 waits for its own lessons. An unrelated second-slot PLANNED lesson can also activate and close because its slot is 10:05–11:25. Restoring the exact clock bytes does not reopen these rows or retract their events. This is the same material isolation risk identified for Q05, even though Q06 advances only three hours.

   Minimal correction: leave the shared clock unchanged. Use the already-created own previous-study-day one-off lesson as genuinely CLOSED history, mark it through the real CAS API, and wait for its normal closure if necessary. Keep today's lesson-bound homework in the immutable homework snapshot without requiring today's lesson to become CLOSED or appear in the `pastOnly` journal. Limit `historyIds`/journal assertions to genuinely past CLOSED lessons. Remove the fixture's clock mutation/restoration path. No new product clock policy or DB status writes are necessary.

2. **P2 — Replacement does not exercise the required next-day effective boundary.** Location: `/workspace/rct-worktrees/first3-spec-frame-20261013/tests/e2e/fixtures/assignment-lifecycle.spec.ts:470`; its date is constructed at 288 and asserted again at 485/517.

   The assigned contract requires B→C replacement effective tomorrow, with B valid from today and a positive interval. `checkpoint.futureDate` is instead `studyDay(today + 2, +1)`. For a Monday teaching date the test replaces on Wednesday; for Friday it replaces on Monday. A result that delays replacement past tomorrow satisfies every current assertion, so the required tomorrow boundary is left unproved.

   Minimal correction: separate the replacement effective date from the deliberately later future lesson/homework date. Use `shift(checkpoint.today, 1)` for `effectiveFrom` and compare `sourceValidUntilExclusive`/the final B interval against that same date. Keep the physical lesson at `futureDate`, beyond the effective date; this preserves the existing DATE-versus-LESSON homework separation and B's current-day group access.

## Other reviewed boundaries

- Real pending closure uses the actual 503 `/assignment-closure-pending` type and `extras.assignmentId/requestKey`; identical saved DELETE URL and idempotency key are reused for the 204 completion and 204 replay. No fictional public closure operation/status API is introduced.
- Public replacement response/status fields match `AssignmentReplacementResponse`; B admission, retained future physical identity, rebind to B then C, COMMITTED receipt, replay operation/target and final physical no-effect comparison are coherent.
- The September 23 and October 6 decisions, T001 publication boundary and current `TeacherReadFacade.journal` support B's current active group-wide past read and former/foreign/future-only C 404 `OUT_OF_SCOPE`. The fixture does not impose a blanket own-past-lesson 403 policy.
- The fixture preserves snapshots of past physical IDs/provenance/revisions, real attendance marks/revisions and the two deliberately published LESSON homeworks including binding IDs/revisions. Assertions and explicit errors avoid raw credential-bearing bodies. It uses the existing Actor/MobileDataset/private-artifact/SafeReporter machinery.
- Own run/artifact/phase directories require 0700; marker/checkpoints require regular non-symlink 0600 files; checkpoints are limited to 256 KiB and created exclusively. In the reviewed implementation the public clock temporary is exclusive, explicitly chmod 0644 before atomic rename, and original clock bytes are restored in finally. Those file protections do not fix finding 1's durable service effects.

Verification was source-only: bounded reads of Root TASK-CONTRACT/CURRENT/decisions/questions/resources, AGENTS/RULES/workflow, the frozen fixture, current dated decisions and relevant service/DTO/fixture sources, plus the two assigned Q06 receipts. No Node, Java, Docker, browser, network, download, CI, product mutation or subagent execution. Only this requested report was created. Author-reported formatter/idempotency/check/selected-file TSC PASS is reused as a local-check receipt, not independently rerun here.

Actual cross-service Q06 runtime remains **NOT_RUN/UNKNOWN**. Existing CI65 named PostgreSQL seams remain finite reused evidence for internal APPLIED/operation identity; they are not proof of this new interrupted public API lifecycle. Root's future phase config, owned Schedule stop/start/readiness, timeout/cleanup and upload wiring were not yet written and were not reviewed. Recheck the two corrections and the concrete Root wiring before genuine execution; do not report whole Q06 PASS from this review. B013/B014 remained frozen, Q012 and Angular/web/production/secret scopes untouched.
