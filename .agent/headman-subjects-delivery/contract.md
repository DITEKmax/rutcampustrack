# Headman subjects and teacher assignments — compact contract

## Goal

Староста своей группы создаёт предмет, выбирает один или несколько типов занятий,
находит действующего преподавателя по табельному номеру и добавляет назначение
для конкретного типа. После перезагрузки список и реальные subject/assignment ID
остаются доступными конструктору расписания в PWA и TMA.

## Context / evidence

- Принятая семантика `docs/wireframes/headman/116-1-headman-subjects.md` требует
  измерение `subject × lessonType × teacher`, несколько преподавателей на один
  тип и доступ только старосте.
- Academic V25 уже хранит immutable assignment snapshots с `semester_id`,
  `lesson_type`, датами и typed `AssignmentClosureNotReadyException` для закрытия.
- `SubjectService` и `AssignmentService` уже дают transaction/auth/group guards,
  `AssignmentAuthority` сериализует semester lock и проверяет active TEACHER.
- Текущий `GET /academic/users/teachers` не удовлетворяет §4 поиска: он без
  фильтра и пагинации. Нужен узкий серверный query/page маршрут с теми же
  безопасными полями `id/fullName/employeeNumber`, опирающийся на active
  TEACHER grant; старый endpoint сохраняется для совместимости.

## Relevant scope

- Academic Subject/Assignment contract DTO/client-facing mapping only where the
  existing response is insufficient for the shared feature.
- New `frontends/mobile-core/src/features/headman-subjects/` client, screen and
  PCSS; exports from mobile-core.
- Minimal generation-bound API creation and headman screen wiring in
  `frontends/pwa-vue` and `frontends/tma-vue`.
- Existing schedule API remains the consumer of persisted assignment IDs.

## Required behavior

- Requests use the authenticated headman context; supplied group IDs are checked
  by the canonical Academic services.
- Create uses the active semester returned by the existing semester list and sends
  initial `(teacher, semester, lessonType, validity)` assignments. The UI does not
  invent teacher IDs or trust a display-only search result.
- Teacher search is backed by a real paginated server query by FIO fragment or
  employee number. The client renders only returned active TEACHER candidates
  and sends the selected server ID/employee number through the canonical
  assignment endpoint.
- Subject reload reads server subject responses and renders assignment names/type
  pairs; every mutation refetches before showing success.
- Offline/read-only and stale generation/session errors fail closed. Assistants,
  ordinary students and teachers cannot use write methods.

## Constraints

- Do not change Academic proto, AssistantService, GroupHeadmanAssignmentService,
  schedule lifecycle or deletion/password-confirmation flows.
- The product wants teacher replacement, but the current close endpoint remains
  a typed `409`: Academic has only the future close gRPC identity scaffolding,
  while schedule owns recurring assignment fences and no cross-service close
  operation is wired. This package therefore exposes add and reports replacement
  as a technical remaining criterion; inventing a local close would break
  immutable assignment history and schedule references.
- No global subject catalog, hard delete, cascade, migration of legacy rows,
  lockfile/dependency or React mass migration.

## Existing patterns

- Generation-bound mobile-core clients use `accessTokenFor`, `refreshFor`,
  `assertCurrent` and typed API errors like HeadmanGroup/HeadmanSchedule.
- Shared Vue screens use props for API/group/offline/readOnly, token-backed PCSS,
  abort/invalidation guards and explicit loading/error/notice state.
- PWA/TMA are thin adapters; their auth/App changes must be small caller hunks
  and preserve existing packaging/auth/session behavior.

## Acceptance criteria

1. A valid headman can create a subject with selected lesson types and initial
   type-specific teacher assignments; API returns real IDs.
2. A valid headman can add another active teacher to an existing subject/type;
   two teachers on one type remain visible after reload.
3. Teacher lookup by employee number resolves real active teachers and cannot
   assign a non-teacher, archived teacher or another group's subject.
4. The same subject/assignment IDs appear in the shared screen after reload and
   are consumable by the existing schedule constructor.
5. Assistant, stale generation and offline writes produce a visible failure and
   no false-success UI state; closure remains the documented typed `409`.
6. Search is server-filtered and paginated; the UI never loads an unbounded
   teacher directory or accepts a display-only candidate.

## Verification

- Light source/type checks for mobile-core and the changed Vue adapters.
- One targeted Academic integration check for create/add, type-specific multiple
  teachers, active TEACHER-grant lookup and safe persisted response; the granted
  run is recorded in `summary.md`.
- Runtime/live API evidence is pending the separate runtime/build handoff.

## Do not

Не менять MAIN, не запускать production data/migrations, не публиковать, не
добавлять новую инфраструктуру для teacher replacement и не называть пакет
полностью готовым до backend checks и Sol review.
