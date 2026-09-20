# LESSONS-L3 frozen binding proto ownership correction
Risk S3 contract boundary; main owns freeze. RULES B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A plus CURRENT amendment.
## Goal
Move Homework binding declarations to owning Schedule service; no lifecycle implementation claim.
## Context/evidence
Root and lessons opened E proto/V17; L2 exact scoped Java search finds no handwritten binding references. E b8220ac92125a8afa37598b270aa4fab7aa1f470. Lessons final nine-section proto proposal accepted in full with this path/lease addendum.
## Relevant scope
Fresh isolated .agent/worktrees/v2-binding-proto branch codex/v2-binding-proto from E. Luna sole writer ONLY proto/academic.proto and proto/schedule.proto plus local .agent/v2-binding-proto evidence and AGENTS pointer. A2 academic.proto separate isolated patch; later one integrator merges.
## Required behavior
Move ReserveHomeworkBinding/ConfirmHomeworkBinding/GetHomeworkBindings and HomeworkBindingState plus five associated messages to ScheduleGrpcService/schedule namespace. Preserve all field names/numbers/types/optionality/enum values and current_lesson tag3 Schedule LessonInfo. Remove Academic schedule import only with zero remaining refs. No reverse cycle, no compatibility second owner. Preserve unrelated user/map/projection/contracts.
## Constraints
No handwritten consumer migration currently evidenced; unexpected references require bounded scope extension. No generated hand edits, no Gradle/build/runtime before lease, no config/dependency changes. Sandbox unchanged, source work only.
## Existing patterns
Five modules generate shared proto independently: schedule-app, academic-app, attendance-app, mobile-bff-app, document-renderer-app. No standalone proto Gradle project. Preserve Java multiple-files namespace patterns.
## Acceptance criteria
RPCs owned only by Schedule, generated Schedule binding namespace, exact wire field/enum parity, same LessonInfo, no cycle, all five modules compile under later lease. Producer lifecycle still OPEN.
## Verification
Source field/enum/service comparison and exact 2-file diff; scoped reference search. Request heavy lane for five modules generateProto/compileJava/compileTestJava using exact paths derived from build. Fresh Sol high independent diff review. Runtime N/A declaration-only; no false test PASS.
## Do not
No V17/entity/create DTO/events/lifecycle/content/UI edits, no parity fix, no source imports wholesale, no children/Terra/full access/push/deploy/main merge. Preserve others.

## L3 scoped compile-blocker correction 2026-09-13
H1: Schedule generateProto and compileJava PASS, compileTestJava FAIL (5 factory-call errors), later modules NOT_RUN. Root opened unchanged baseline tests and current shared InternalJwtTestFactory: old expiredToken/invalidSignature removed, validToken now 9 arguments; buildToken supports complete claims/expiry/signer. Permit SAME assigned L3 Luna writer two additional files only: schedule-app/src/test/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilterIT.java and ScheduleUserContextFilterStrictModeIT.java. Adapt calls to complete current fixture API preserving expiration/signature/role/legacy-header assertions. Use buildToken for valid complete expired token and independently signed token; do not alter production/shared factory or weaken assertions. Record baseline defect separately from proto change. After source correction freeze, H2 permits repeat five-module compilation; further unrelated errors report before scope extension. No Docker/IT runtime. Fresh full diff review after corrections, not just proto-only review.
