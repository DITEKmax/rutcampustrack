# Homework completion-date delta — frozen root contract

07.09.2026, resumed by owner. S3. Base8002b9ea4356b10779c5bb9a6d99746d32d78ae2 plus verified24file homework-api pause snapshot. Fresh Luna max sole writer, no children.

## Goal
Make final «Выполнено сегодня» truthful after reload and completing assignments whose lesson date differs from today, through minimal server-authoritative Homework API delta before UI.

## Context/evidence
Root reopened final4601-142.txt (Sep1 programming/Sep2 networks),4601-848636-refreshed-2026-09-07.txt (both completed today), HomeworkCompletion entity/repository, HomeworkStudentService, academic.proto and BFF query. Accepted consultant semantic inference in homework-heading-decision-result.md: completed heading refers to action date Europe/Moscow. This adds detail to earlier boolean-only homework contract; chronological date feed remains. Previous four review repairs stay intact and await independent review.

## Relevant scope
Sole writer C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/homework-api. Own Academic homework entity/repository/service/gRPC and focused tests, proto/academic.proto, Homework BFF client/query/controller/model/API contract/tests, canonical OpenAPI and generated mobile-core types/client/fixtures. Do not edit shared-shell/index/UI/root configs/lockfiles/other services. Preserve all existing changes; no commit. Own worktree .agent/student-role-02/homework-date-delta evidence.

## Required behavior
Add nullable completedAt server timestamp (Java Instant/OffsetDateTime consistent current conventions; protobuf optional string ISO8601) to HomeworkInfo, public HomeworkItem and PUT completion response. completed false implies null; true has actual stored timestamp. PUT captures state+timestamp atomically from same service transaction; repeated true preserves timestamp; false removes completion; later true sets fresh timestamp. Do not synthesize timestamp at BFF/client.

GET keeps effective bounded from/to and existing sort. Current feed (effective from<=MoscowDate(serverNow)<=to) returns union of own current-semester/group lessonDate[from,to] assignments plus own assignments completed during that current Moscow day; unique id. Historical-only and future-only explicit ranges retain date-range semantics, no unrelated today union. Internal RPC extension must opt in explicitly so existing callers of getHomeworksForWeek keep range behavior. BFF captures serverNow once and uses that same instant for Moscow union-day request and response; internal service validates scope and day range, no student-supplied identity. Use half-open Moscow midnight interval [start,nextStart). Group assignment/semester authz enforced in repository/service, foreign completions never leak.

UI downstream contract only: today-completed group uses timestamp date==response serverNow Moscow date, other items stay lessonDate groups. No UI changes here. Existing server time response remains authoritative; timestamp parse/absence contradictions must not produce fake successful boolean+null data.

## Constraints
Java-first OpenAPI/generated TS. No new dependencies/migration/clock override/global config. Avoid broad query that loads all students/semesters. Use existing immutable TIMESTAMPTZ source and PostgreSQL transaction pattern. Preserve no-store success/errors, strictBoolean, overflow400, missingSemester503 and authz protections. You are not alone; requests writer owns a different checkout. No other person's edits reverted.

## Existing patterns
HomeworkCompletionRepository INSERT ON CONFLICT DO NOTHING, HomeworkStudentService @Transactional, internalJWT identity interceptor, BFF Clock/MOSCOW, previous PostgreSQL and HTTP/local-gRPC test fixtures. Existing completed row type OffsetDateTime. Use same frozen artifact/toolchain flows for export and TS generation.

## Acceptance criteria
Actual tests for old/future lesson completed today returned in current feed once; yesterday completion excluded from today union but preserved in its historical lesson group; explicit historical/future-only ranges unchanged; reload same timestamp; repeatedtrue same timestamp; undo null and removes out-of-range union item; redo fresh timestamp; Moscow midnight; foreign student/group/semester excluded; concurrent repeat remains one row. Four prior repairs regressions remain green.

## Verification
Read applicable services/frontends/tests AGENTS and rct-verification. Meaningful Academic PostgreSQL IT + BFF HTTP/local-gRPC boundary timestamp tests; existing affected unit/auth regressions; Java-first OpenAPI update/no-update, generated TS drift/typecheck/lint/contractfixtures, bootJars and diffcheck. Record revision/command/exit/env/counts/limitations, manifest and summary. Gradle --no-daemon with required escalation for known absolute-classpath sandbox issue. Unique runtime resources. Fresh Sol high review after stable delta includes four repairs; not this writer's responsibility.

## Do not
No main integration/commits/deploy/data deletion/Figma edits/UI work/compatibility layer/invented client history/boolean-only fake timestamp. Report bounded defects before unrelated fixes. Continue until this delta verified, not whole-role acceptance.
