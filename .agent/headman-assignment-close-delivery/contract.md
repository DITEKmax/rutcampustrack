# Смена преподавателя с переносом непринятых занятий — compact technical contract

Дата: 2026-09-23  
Baseline: `31686aa3c03848a5797d115bd82b31665b574601`  
Риск: S3  
Статус: contract-only before implementation; product source diff `0`.

## 1. Goal

Староста своей группы выполняет одну идемпотентную операцию смены преподавателя
с исключающей датой `D`. Каноническая Academic assignment нового преподавателя
начинается в `D`, исходная заканчивается в `D`, а история замены сохраняется.
Каждое ещё не начатое занятие от `D` получает новую assignment provenance и
нового преподавателя. В текущей схеме признаком уже начатого/проведённого
занятия является физический статус: только `PLANNED` можно перепривязать;
`ACTIVE`, `CLOSED`, `CANCELLED` и уже `TRANSFERRED` остаются на исходной
provenance и записываются как пропущенные причины. Дата сама по себе не
превращает `ACTIVE`/`CLOSED` в блокирующую ошибку и не останавливает перенос
остальных `PLANNED` строк.

Перепривязка сохраняет `lesson_id`, `occurrence_id`, current pointers,
homework binding IDs и attendance lesson IDs. Прошлые attendance/marks/homework
и lifecycle history не переписываются, физические строки не клонируются и не
удаляются. Повтор того же запроса возвращает исходную операцию и её per-entity
receipt.

## 2. Context / evidence

- Принятое решение владельца записано root в
  `docs/product/decisions/2026-09-23-teacher-replacement-and-group-attendance.md`:
  все ещё не проведённые занятия от `D` переходят новому преподавателю,
  проведённые занятия и история сохраняются. Read-authority всей группы для
  активных назначений преподавателей принадлежит другому owner scope и здесь
  не меняется.
- Р-26 (`docs/architecture/reference-rutcampustrack-design/backend-conflicts.md:358-389`)
  требует отдельный assignment ID для group × subject × type × semester ×
  teacher и период действия; прошлое assignment не редактируется.
- Academic V25
  (`services/academic-service/academic-app/src/main/resources/db/migration/V25__student_subject_homework_foundation.sql:129-209`)
  делает identity, `valid_from`, `created_at` immutable и не разрешает снять
  или расширить установленный `valid_until_exclusive`; смена создаёт новый
  assignment.
- Schedule V17/V18 имеют immutable occurrence/physical snapshots, запрет
  physical DELETE и triggers, которые сейчас не допускают изменение
  `schedule_item_id`, assignment или teacher. V18 `cap_until_exclusive` сейчас
  одновременно используется как insert cap и retained-row boundary; для
  замены нужны отдельные creation и retention cap.
- `ScheduleGrpcServiceImpl`/`lessonInfo` отдают assignment и teacher из
  физического `Lesson`; template используется как fallback для slot-полей.
  Поэтому сохранение occurrence/lesson IDs совместимо с downstream.
- `HomeworkBindingService` и Attendance downstream индексируют стабильные
  occurrence/lesson IDs. При сохранении этих IDs не нужен Mongo move, pointer
  migration или новый cross-service event consumer.
- `AssignmentService.removeAssignment` и `SubjectService.removeTeacher` сейчас
  fail-closed typed `409`; они не становятся вторым writer-ом. Отдельный
  `SubjectService.addTeacher` lock inversion остаётся вне этого пакета.

## 3. Relevant scope

### Academic

- `AssignmentService`, `AssignmentController`, `AssignmentAuthority`,
  `AssignmentRepository` и один canonical replacement coordinator; любой
  совместимый subject-teacher entry point делегирует в него.
- Следующая additive migration после V25: immutable
  `assignment_replacement_operations` с operation UUID, actor/request key,
  canonical payload hash, source/target IDs, `D`, state, Schedule receipt и
  timestamps. Новый assignment проходит `PREPARED` → `COMMITTED`; обычные
  reads/authority не выдают `PREPARED` как активное назначение.
- Additive `POST /academic/assignments/{sourceAssignmentId}/replace` с
  `{replacementTeacherId,effectiveFrom,requestKey}` и
  `GET /academic/assignments/replacements/{operationId}`. Старый close/delete
  путь остаётся совместимым adapter-ом и не пишет собственную дату конца.
- Existing directed Academic↔Schedule identity client/RPC surface, без
  `teacher_reads.proto` и teacher statistics.

### Schedule

- Replacement `prepare/apply/commit` coordinator рядом с
  `RecurringScheduleItemWriter` и `RecurringAssignmentAuthority`; additive
  Schedule migration следующей версии, без редактирования V17/V18.
- Durable Schedule operation/receipt and append-only per-source/date rebind
  ledger: operation, source/target assignment, source/target schedule item,
  occurrence and lesson IDs, date, expected revision, before/after
  assignment/teacher snapshots, result (`MOVED`, `SKIPPED_CANCELLED`,
  `SKIPPED_ACTIVE`, `SKIPPED_CLOSED`, `SKIPPED_ALREADY_STARTED`) and
  timestamps. Unique operation + source occurrence/date makes retry safe.
- `schedule_assignment_fences.creation_cap_until_exclusive` (or equivalent)
  отделяет запрет новых source rows `>=D` от retained
  `cap_until_exclusive`. Existing `RecurringScheduleItemWriter` uses creation
  cap for generation and retained cap for validating old snapshots.
- Per-source-template target clone 1:1 with the same slot/time/week/room/group/
  subject/semester tuple and target assignment. The mapping records the source
  template's original `is_active`; only clones of active source templates are
  activated at commit. Inactive history templates and any competing active slot
  remain untouched.
- Existing eligible `PLANNED` rows with `date >= D` move atomically to the
  target template by updating occurrence and physical lesson snapshots while
  retaining both IDs/current pointers. Target generation excludes every
  source/date already represented in the ledger, so skipped cancellations and
  held rows cannot be resurrected and moved rows cannot be duplicated.
- No HomeworkBinding schema or Attendance data move: origin/current pointers
  and lesson IDs stay unchanged. Attendance/statistics read authority remains
  with its other owner. No new orchestration/event framework.

### Shared client/UI

- Existing `frontends/mobile-core` headman-subjects/assignment client and
  screen: one replace action, typed pending/409/reconciliation state, status
  refetch after reload, and no optimistic teacher success. PWA/TMA navigation
  only where the current shared feature already exposes the action; no global
  App/session rewrite.

Out of scope: `teacher_reads.proto`/BFF statistics, Assistant/GroupHeadman,
one-off lessons, hard-delete, migration of past documents, broad restore or
regeneration redesign, generated files, lockfiles, production migration,
deployment, push, unrelated UI.

## 4. Required behavior

### 4.1 Academic operation

1. The route validates authenticated headman scope, exact source assignment and
   group, active replacement TEACHER grant, same subject/type/semester, and
   `source.valid_from <= D <= source.valid_until_exclusive`. `D` is exactly the
   target `valid_from`; same-day `D == source.valid_from` is valid only for this
   exact immutable replacement operation. No date normalization or silently
   different effective date is allowed. Same source cannot have two pending operations; a reused
   request key with another payload is a conflict.
2. One Academic transaction locks the semester first, then source assignment;
   it re-reads the tuple under lock and creates the target assignment as
   `PREPARED` with `valid_from=D` and the source end boundary. It records the
   operation/hash and commits before calling Schedule. No gRPC call occurs under
   Academic row locks.
3. Schedule installs a durable barrier only: it binds the exact operation/hash,
   installs the sorted fence pair, narrows the source creation cap to `D`, and
   returns an `APPLIED` receipt with zero counts. It does not clone templates or
   rebind physical rows. After that receipt, one Academic transaction locks the
   semester then assignments in ascending ID order, validates the tuple, marks
   the operation `APPLIED`, closes the source at exactly `D`, and activates the
   target. No gRPC call occurs under those locks.
4. Schedule commit fetches Academic's exact `APPLIED` authority outside local
   locks and accepts it only with source end `D` and target lifecycle `ACTIVE`.
   Under one local transaction it locks the same sorted fence pair, clones only
   recurring templates, records fresh date dispositions, rebinds eligible
   `PLANNED` occurrence/lesson snapshots, and switches active source templates
   off before enabling only their originally active target clones. It stores
   the final counts and `COMMITTED` receipt. Academic then commits its operation
   from that exact receipt in another lock-bounded transaction. A failed phase
   remains recoverable by the same operation/hash; it never claims success early.
5. Status reads expose `PREPARED`, `APPLIED`, `COMMITTED` and reconciliation
   failure with the original receipt. Ordinary recurring writes to a pending
   target fail closed until Schedule reaches `COMMITTED`; Academic rejects a
   nested replacement while the target operation remains open.

### 4.2 Schedule apply/reconcile

1. Schedule re-reads the immutable prepared tuple from Academic and ignores
   actor-supplied group/student/teacher/date fields. It validates
   `target.valid_from == D`, source/target group-subject-semester-type equality,
   and the exact source assignment ID before changing anything. Equality is
   required even when `D` is the same day as an already accepted boundary.
2. Schedule install uses one local `READ COMMITTED` transaction to insert its
   immutable operation tuple, lock source/target fences in sorted assignment ID
   order, install target fence provenance and narrow only source
   `creation_cap_until_exclusive` to `D`. It never narrows retained
   `cap_until_exclusive` and performs no template or occurrence writes. No gRPC
   is called while locks are held.
3. After Academic activates the exact target, Schedule commit fetches and checks
   the complete authority tuple before starting a local transaction. That
   transaction locks the operation and the same sorted fence pair, then source
   templates, target-template mappings and source occurrence/current-lesson
   rows in stable IDs. Pending-target recurring creation and raw inserts fail
   closed until the transaction commits the operation.
4. During that transaction, clone source templates without generating duplicate
   physical rows, recording each source template's prior active state. Disable
   active source slots before activating only matching clones whose source was
   active. Source templates are not updated to point at the target assignment.
5. For each source occurrence whose current lesson has status `PLANNED`, date
   `>=D`, and expected source revision, the writer updates in one guarded unit:
   `lesson_occurrences.schedule_item_id/assignment_id/assigned_teacher_id`
   and the matching `lessons.schedule_item_id/assignment_id/
   assigned_teacher_id` snapshot. It increments the operation revision,
   appends one before/after history row, and preserves occurrence/lesson IDs,
   current lesson pointer and all lifecycle rows. A second attempt sees the
   ledger and is a no-op receipt.
6. `ACTIVE`, `CLOSED`, `CANCELLED` and `TRANSFERRED` current lessons are
   retained on the source template and receive `SKIPPED_ACTIVE`,
   `SKIPPED_CLOSED`, `SKIPPED_CANCELLED` or `SKIPPED_ALREADY_STARTED` history
   as applicable. They do not produce a total-operation `409`, do not block
   other planned rows, and are never resurrected or rewritten. A target
   generation pass must consult these durable skips/occupied dates before
   inserting a missing target row.
7. The database guard uses a transaction-local operation selector only as a
   lookup key. It is not permission. Each forward trigger verifies the locked
   ledger's exact source/target tuple, APPLIED operation state, expected revision,
   `PLANNED` status and `date >= D`; missing, stale, or forged context fails
   closed. Ordinary recurring writers cannot update immutable provenance.
8. A lost response returns the same operation/receipt by request key and
   payload hash. No source reopen, old-row delete, hidden physical clone,
   `TRANSFERRED` mutation, Homework move or Attendance move is used for replay.

### 4.3 UI, failure and observability

- The PWA/TMA form remains pending through `PREPARED`/`APPLIED`, shows typed
  conflict or reconciliation failure, and reports success only after
  `COMMITTED` plus refetch of source/target assignment and schedule state.
  Reloading the page reads the durable operation, not local optimistic state.
- A failed or unknown apply does not close the form or claim a teacher change.
  The exact operation ID/request key is available for recovery and support
  logs, while secrets and service tokens are never logged.
- Directed Academic/Schedule credentials use the existing separate configured
  identities and TLS inputs; blank/noncanonical credentials fail closed with
  `UNAUTHENTICATED`. No secret is read or printed in this WT.

## 5. Constraints and root freeze items

- Do not edit V17/V18/V25 in place. The forward migration may replace trigger
  functions only through additive versioning and must preserve ordinary
  immutable behavior outside the exact operation ledger guard.
- Do not update source template assignment provenance: cloning a target
  template is required so historical/cancelled rows continue to resolve to
  their original template. Only clones of source templates active at commit
  are activated; inactive templates remain inactive.
- `D` must equal the target assignment `valid_from` byte-for-byte in Academic,
  Schedule receipt and rebind ledger. Any mismatch is a typed conflict with no
  partial side effect. Equality at the source start or end boundary is accepted
  only inside this exact immutable replacement operation; the forward CHECK and
  replacement trigger allow `valid_until_exclusive >= valid_from` for that
  operation. Ordinary recurring create still requires a strict positive
  interval and never reopens or extends an assignment.
- Do not acquire user/group locks in this path and do not add a global
  user→group or group→user order. Assignment replacement uses semester,
  assignment, fence, template, occurrence/lesson locks only.
- Do not call gRPC under row locks. Reuse existing outbox/idempotency/fence
  patterns; no second orchestration framework and no cross-service event
  consumer is introduced.
- Root freeze items before code: exact additive RPC request/receipt shape,
  whether Schedule rebind is applied before Academic finalization or at the
  final reconcile boundary (read visibility must remain coherent while target
  is PREPARED), and the precise target-template missing-date generation hook.
  These are technical protocol choices, not new product questions.
- `SubjectService.addTeacher` lock inversion is recorded for another bounded
  task and is not changed here.

## 6. Existing patterns

- Academic `AssignmentAuthority.lockSemester/createWithLockedSemester`, V25
  native end update and current assignment read filters.
- Schedule `RecurringScheduleItemWriter.lockOrInstallFence`, PostgreSQL
  `FOR UPDATE`, `RecurringAssignmentAuthority`, V17 occurrence pointers,
  append-only lifecycle and `schedule_recurring_create_replay`.
- `ScheduleGrpcServiceImpl` physical snapshot response path and
  `ScheduleItemRepository.findByGroupId` (all templates) are the read
  compatibility basis for source history plus target future template.
- `HomeworkBindingService` origin/current lesson joins remain untouched because
  the rebind preserves both referenced IDs.
- Existing after-commit/cache invalidation and typed `409` exception mapping;
  no generic lifecycle framework.

## 7. Acceptance criteria

- An authorized headman sends one request for source assignment and `D`; after
  commit/refetch, source ends at `D`, target is active from exactly `D`, and
  durable operation/rebind history is visible after reload.
- Existing future `PLANNED` rows from `D` keep the same occurrence/lesson IDs,
  receive target schedule-item/assignment/teacher snapshots, and remain usable
  by existing Homework and Attendance references. Past rows and marks are
  unchanged.
- Existing future `ACTIVE`, `CLOSED`, `CANCELLED` or `TRANSFERRED` rows remain
  source history, do not block planned rows, do not duplicate a slot and do not
  become planned again. Missing dates and cancellation/restore races obey the
  durable skip ledger.
- Repeating the exact request after prepare/apply/finalize timeout returns the
  same receipt and changes no row twice. Concurrent create/cancel/replace has
  one serialized winner or a typed conflict, with no partial target/source
  provenance.
- Non-headman, foreign group, inactive teacher, wrong subject/type/semester,
  stale revision, forged internal tuple and `D != target.valid_from` are denied
  with no durable side effect.
- PWA/TMA waits for committed/refetched state and never displays a local
  optimistic teacher replacement as success.
- Group-wide teacher attendance read remains the other owner’s acceptance; this
  package only supplies durable assignment/rebind facts.

## 8. Verification and current evidence

Current contract-only evidence:

- `git status --short --branch` in the assigned WT: exit `0`; branch
  `codex/headman-assignment-close-20260923`, baseline
  `31686aa3c03848a5797d115bd82b31665b574601`; product source diff `0`; only
  `.agent/headman-assignment-close-delivery/contract.md` is untracked.
- Read-only source probes: exit `0` for `ScheduleGrpcServiceImpl` response
  fields, `LessonRepository`, `ScheduleItem`/`Lesson` entities,
  `RecurringScheduleItemWriter`, `HomeworkBindingService`, V17/V18 trigger
  definitions and repository status queries. These confirm physical snapshot
  reads and the current immutable guards described above.
- No product edits, Gradle/Docker/frontend checks, runtime or production data
  access were performed. HEAVY was not used; no PASS is claimed.

After root freezes the technical edges, use only risk-directed checks:

1. Academic IT: prepare/finalize/replay, exact `D`, target visibility,
   authorization, concurrent candidate winner and source interval/history.
2. Schedule real PostgreSQL/Testcontainers IT: target-template provenance,
   creation-vs-retention caps, planned snapshot rebind with stable IDs,
   cancelled/active/closed/legacy transferred skips, missing-date generation,
   cancel/replace race and request replay. Assert before/after ledger and no
   duplicate slot/occurrence.
3. Existing Homework/Attendance reference checks proving same occurrence/lesson
   IDs and unchanged historical documents; no Mongo move test or new consumer
   suite.
4. One affected mobile-core typecheck and existing client reconciliation path;
   no new Vue framework and no full suite. Runtime evidence must use real APIs,
   persisted rows and reload/refetch.

## 9. Do not

Не клонировать physical occurrence/lesson rows, не менять past/held snapshots,
не использовать `TRANSFERRED` как замену, не переносить Homework/Attendance
документы, не ослаблять immutable triggers без exact operation ledger guard,
не менять `teacher_reads.proto`, BFF statistics, Assistant/GroupHeadman,
global App/session, lockfiles, generated files, чужие worktrees, MAIN,
production data, deployment или push; не читать и не печатать secrets; не
объявлять пакет готовым до независимого review, targeted checks и runtime
evidence.
