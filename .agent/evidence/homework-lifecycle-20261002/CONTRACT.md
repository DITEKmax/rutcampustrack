# В — homework lifecycle, S3, architecture proposal / DTO freeze

## Goal
Староста / действующий помощник с manage_homework создаёт ДЗ на пару или день, изменяет чужое ДЗ своей группы вместе с placement, читает историю; студент сохраняет выполненность.

## Context/evidence
Canonical RULES A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA; owner 2026-10-02 DATE archive at next-day 00:00 Europe/Moscow. JS-HEADMAN-16/17/28/29 supersede old author-only code. Academic/Schedule/proto baseline WT67a3d364 matches maina5974af8. Foreign dirty evidence preserved.

## Relevant scope
Sole writer /root/v_homework_lifecycle_1002, assigned map-usage-delivery-20260922 WT. Academic homework DTO/API/domain/persistence/migration V45; Schedule binding service/grpc/lifecycle predicates/migration V24; proto/schedule.proto and necessary generated consumers. Root integrates main / owns shared docs. No children.

## Required behavior
Root freezes API: Create bindingMode defaults LESSON, lessonDate required, lessonNumber 1..8 for LESSON / null for DATE. Update optional bindingMode/lessonDate/lessonNumber; absent placement fields retain current. ALL edits require requestKey + expectedRevision; legacy three-field PUT returns 400. Response bindingMode + revision. History GET /academic/homeworks/{id}/history pageable revision,id ASC, actor/time/before/after, permitted group readers including archived. 400 invalid placement; 403 permission/group; 409 stale/archive/key-payload mismatch. Client adapter update belongs to next frontend stage.

## Constraints
Academic binding_id/actor_id/create request_key/payload_hash remain immutable; V28 identity guard retained. Schedule stable binding_id is publication identity; current placement is authoritative in Schedule, Academic stores its projection. V24 guard authorizes placement transitions only from durable receipt, retains terminal rules. Immutable CREATE intent: Schedule original_occurrence_id, original_binding_mode, original_date/number (plus immutable scope); Academic create_intent snapshot of full original request, persisted/backfilled before first edit. Schedule Reserve retry compares original intent, not current occurrence. Academic replay validates create_intent, returns same homework with current placement/state. Hash never rewritten. Old retry after archive returns conflict and never recreates.

## Existing patterns
HomeworkPublicationPersistence REQUIRES_NEW; semester → binding lock in Academic; Schedule semester → sorted origins → sorted occurrence/physical → binding → receipt lock graph. HomeworkUpdatedEvent uses existing BEFORE_COMMIT durable outbox, same event schema/routing key homework.updated, no Notification changes.

## Acceptance criteria
Non-author same-group authorized success; denied/foreign/revoked no mutation. DATE requires no lesson. Edit preserves ID/completion, one history/event for exact retry. Linked transfer follows; DATE unaffected. Inclusive Moscow deadline terminal read-only. Archived history accessible to group readers, teacher arbitrary-all denied.

## Verification
No Gradle/Docker before root lease. Planned compileJava/compileTestJava Academic + Schedule, one cohesive PostgreSQL/integration boundary for changed authz/history/replay/placement/terminal rules, independent review before heavy. Runtime not run yet. Git/reads exit0, attempted WT owner decision read exit1 (decision resides canonical root and subsequently read exit0). No warning-driven changes.

## Do not
No fake lesson, mutable binding setter / dropped identity guard, dual authority, UI/Figma/cache/Attendance/auth/Notification, deploy/push/main changes, production migration, protected configs, full old suite.

### Architecture review required before binding migration
Schedule move commits immutable result receipt + per-binding APPLIED_AWAITING_ACK gate atomically. Transfer checks gate before operation/mutation and returns retriable conflict without ERROR receipt. Academic finalizes projection/content/history/outbox once, then exact ACK releases gate; durable recovery continues stored forward result (no arbitrary rollback). Archive PREPARE captures admitted pending edits and permits only exact continuation/ACK; terminal archive wins. Retry exhaustion never silently clears gate. Academic accepted manual-placement receipt explicitly supersedes old APPLIED transfer marker while retaining immutable transfer history/receipts; PENDING publication marker drains or refuses, never bypasses identity. Recovery/ACK uses existing directed academic→schedule credential, no persisted user JWT. Binding migration waits targeted review recheck.

DATE inventory: binding.semester_id is authoritative in ScheduleSemesterArchiveBarrierTransaction firstPendingBinding/readBinding/final deletion (currently occurrence joins), nullable occurrence in RPC/archive event tuple, move-gate checks in PREPARE/DELETE readiness, admitted drain then terminal action, deletion snapshot/digest owns DATE bindings and pending receipts. DATE deadline uses selected day +1 at 00:00 Europe/Moscow. Directed Get/Continue/ACK accepts only persisted operationID+actor/key/hash+scope/outcome, never new Move/arbitrary scope. Current rights checked before first acceptance; admitted continuation survives revoked/expired user JWT.

Root crash safeguard: missing Get alone cannot cancel PREPARED (delayed Move may still arrive). Directed AbortUnaccepted installs exact NOT_ACCEPTED tombstone under the same actor/request key lock as Move, bound to Academic admitted operation/hash/scope. Existing APPLIED wins and returns stored result; tombstone wins and all delayed Move attempts return terminal conflict. Academic records cancellation only after that durable CAS proof. No placement rollback / no APPLIED gate clearing on exhaustion.

All edits require client key/revision. New-key no-op records receipt without history/event. Same key/hash returns accepted snapshot after current authority/terminal check; changed payload →409. No derived edit keys (A→B→A). New records persist ORIGINAL_CREATE intent. Legacy untouched keeps accepted sameRequest comparison; first new edit atomically freezes its previously accepted scope/content/slot fingerprint as LEGACY_ACCEPTED_REPLAY. Never infer unknown original content or brute-force revision; original payload_hash unchanged. This preserves accepted legacy retries, not unknowable original pre-edit requests. Events add binding_mode DATE|LESSON, lesson_number nullable; bot consumer separately owned.
