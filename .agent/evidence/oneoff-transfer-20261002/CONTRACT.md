# JS-HEADMAN21 ONE_OFF transfer — frozen root ACK 2026-10-02

## Goal
Староста переносит разовую физическую пару одним действием, сохраняя occurrence, историю и связь с ДЗ/посещаемостью через existing exact transfer protocol.

## Context/evidence
Root лично открыл JS21 и LessonTransferWriter NULL-template rejection. Leaf opened readSource/lockSource/insertTarget/origin locking and original V17/V21/V23 guards, final V25 oneoff guards/projections. Canonical RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA. S3 auth/concurrency/schema. Ordinary switch from main56f3dc52 to codex/oneoff-transfer-1002; source6c6399e8 cherry-picked e1e415a4; finalP2source9317bbb1 cherry-picked60c0ff8b. Foreign tracked evidence preserved. No reset/clean.

## Relevant scope
Sole writer own admin-group-promotion-20260927. LessonTransferWriter; new V26__one_off_lesson_transfer.sql; new OneOffLessonTransferRequestedEvent; OneOffLessonRepository/current projection/Service/Controller/Assembler; one existing LessonTransferWriterIT method. V25/LessonService/OneOffControllerIT author scope untouched.

## Required behavior
Exact assignment fence→origin→occurrence→physical→bindings lock order, current/revision recheck. Target preserves origin XOR, assignment/teacher/scope, new generation same occurrence; no fabricated template. Only origin physical pointer changes; original creation placement/actor/timestamps remain immutable. V26 exact transfer prefixes precede V25 ONE_OFF restore-only branches; deferred authority requires exact source→target lineage/origin pointer. Existing pending homework edit refusal happens before operation. Canonical binding batches/receipts/ACK/forward recovery unchanged. Public create/replay/list use one current physical snapshot and physical date filtering. Restore after cancel of transferred current generation remains valid.

## Constraints
Target date uses existing assignment validity/caps; no silent teacher change. V25 final guard bodies preserved exactly after removing new transfer prefixes/declaration. Old recurring branch and v1 hash/payload/replays unchanged. No frontend/device/auth/gateway/proto changes; no other services edited; no heavy before independent review + root lease.

## Existing patterns
Existing durable transfer operation, setting, authorities, lifecycle, immutable batches/outbox and fixed participant receipt state machine. V25 physical oneoff create/cancel/restore. Spring Data native current projection. Programmatic createOneOffLesson origin entity return retained for existing author event tests; public createCurrentOneOffLesson reads physical projection.

## Acceptance criteria
Public create→two concurrent same-key public transfers→single exact accepted operation/generation; changed payload409/foreign actor403; pending transfer unavailable to nextlookup; origin creation tuple retained and current public list/replay uses moved physical slot; binding current target+same identity; v2 source/target exact sameoneoff; republish/replay and fixed ACK completion; stale source cancellation cannot mutate target; cancel/restore current target preserves generation/history. Cross-service marks actual application belongs combined consumer acceptance, not a fake ACK claim.

## Verification
git diff --check exit0. Mechanical SQL target columns/values28/28. Mechanical V25 two guard bodies preserved byte-for-byte excluding new prefixes/declaration. Planned only existing LessonTransferWriterIT.oneOffPublicTransferKeepsOriginAndBindingsAcrossRaceReplayAckAndRestore plus root-combined oneoff9methods/event2methods/previousblockednextlookup1method. No previous accepted recurring/lifecycle suite reruns. Heavy not launched. Independent source review required.

## Do not
Do not edit V25/current author source/tests; no cancel+recreate/newnaturalkey identity; no gate timeouts/bypass; no children, reset/clean/push/deploy.

## Root event delta
Root personally verified Attendance and Academic v1 parsers require positive schedule_item_id. Frozen additive v2: same lesson.transfer.requested eventtype/exchange/routing/ACK protocol, only ONE_OFF with source/target schedule_item_id:null and positive sameone_off_lesson_id; all existing exact physical tuple/batch/operation hash fields retained. New @EventVersion(2) class selected by immutable payload.source.one_off_lesson_id for initial/republish. Recurring v1 snapshot omits new key so old hashes and persisted replays remain unchanged. Dedicated author owns both consumer parsers/v2schema outside this worktree; no producer-only end-to-end claim.
