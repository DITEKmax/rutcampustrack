**BLOCKED** для реализации foundation на `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. Архитектурное решение конечное, но требует одного integration writer для proto/migrations/events и зависимо от roster contract. Runtime N/A; файлов и внешнего состояния не менял.

Критичные findings:

- **CRITICAL — authoritative assignment отсутствует.**  
  `TeacherSubjectGroup.java:9-40` хранит только teacher/subject/group/semester, а `ScheduleItem.java:33-46`, `OneOffLesson.java:35-45` и `Lesson.java:34-44` не хранят assignment/type snapshot. `ScheduleGrpcServiceImpl.java:238-254` собирает пару из изменяемого `ScheduleItem`.  
  **Impact:** при двух преподавателях одного типа нельзя определить ведущего пары; изменение связи переписывает смысл истории.  
  **Reproduction:** создать два assignment одного subject/semester, затем ScheduleItem; `LessonResponse` не содержит assignment/type/teacher.

- **CRITICAL — R25 сейчас не реализуем.**  
  Источник требует одну транзакцию, source→target и перенос Homework (`backend-conflicts.md:329-354`). `LessonStatus.java:3-7` не имеет `TRANSFERRED`, `schedule.proto:56-77` не имеет lineage/assignment, Homework хранит natural tuple (`Homework.java:30-36`) и создаётся через tuple lookup (`HomeworkService.java:125-142`).  
  **Impact:** duplicate/concurrent transfer создаст неоднозначную историю или оставит Homework на старом слоте.  
  **Reproduction:** API transfer отсутствует; отмена+one-off разрывает операцию между сервисами.

- **HIGH — cancel/restore теряет историю и не создаёт пустое поколение.**  
  R7 требует invalidation без удаления и пустую сетку после restore (`backend-conflicts.md:158-163`). Сейчас cancel заменяет status записи на `CANCELLED` (`LessonEventService.java:135-142`), one-off cancellation физически удаляет записи (`:114-132`), restore очищает audit tuple и не публикует событие (`LessonService.java:144-156`).  
  **Impact:** исходная отметка утрачена; restore оставляет старую строку, а запоздалый close может снова материализовать старое поколение.  
  **Reproduction:** PRESENT → cancel → restore → close; unique `(lesson_id,user_id)` в `AttendanceDocument.java:23-28` не позволяет отдельное пустое поколение.

- **HIGH — существующие write paths обходят immutable assignment.**  
  `SubjectService.java:171-178` меняет type на месте; `AssignmentService.java:102-109` и `SubjectService.java:247-261` физически удаляют assignment.  
  **Impact:** прошлые пары теряют неизменный teacher/type contract.  
  **Reproduction:** создать пару, затем PUT subject type или удалить assignment; история продолжает резолвиться через изменяемые текущие строки.

- **HIGH — Schedule read неполон для статистики.**  
  `ScheduleGrpcServiceImpl.java:95-115` читает только recurring lessons активных templates; one-off и история неактивного template отсутствуют.  
  **Impact:** неверный знаменатель и пропуск one-off/source history.  
  **Reproduction:** создать one-off или деактивировать template и вызвать `GetLessonsByGroup`.

### Compact contract

1. **Goal**  
   Ввести group-scoped Subject, effective-dated Assignment и единый logical occurrence с immutable identity snapshot. Один logical occurrence считается один раз; transfer создаёт новую physical instance, сохраняя source history.

2. **Context/evidence**  
   R15 запрещает name identity (`backend-conflicts.md:212-225`). R26 требует отдельный assignment, несколько преподавателей одного типа и immutable past (`:358-403`). R25 задаёт atomic transfer (`:329-354`). R7 задаёт cancel/restore (`:158-163`). Текущие baselines: Academic V23, Schedule V16.

3. **Relevant scope**

   **Academic, sole writer:**

   - изменить `entity/Subject.java`, `entity/TeacherSubjectGroup.java`;
   - добавить `entity/SubjectLessonType.java`;
   - изменить `TeacherSubjectGroupRepository.java`, `SubjectService.java`, `SubjectAssembler.java`, `AssignmentService.java`, `AssignmentAssembler.java`, соответствующие controllers/API DTO;
   - изменить `AcademicGrpcServiceImpl.java`, `proto/academic.proto`;
   - зарезервировать `V24__subject_assignment_identity.sql` только на frozen integrated revision.

   **Schedule, sole writer:**

   - добавить `occurrence/entity/LessonOccurrence.java`, `LessonLifecycleEntry.java`, `LessonHomeworkBinding.java` и соответствующие repositories;
   - добавить `occurrence/OccurrenceService.java`;
   - изменить `Lesson.java`, `ScheduleItem.java`, `LessonService.java`, `LessonGenerationService.java`, `OneOffLessonService.java`, assemblers/repositories/controllers;
   - добавить `TransferLessonRequest.java`, `TransferLessonResponse.java`;
   - изменить `LessonResponse.java`, `schedule.proto`, `ScheduleGrpcServiceImpl.java`;
   - добавить `V17__occurrence_identity_lifecycle_binding.sql`.

   **Blocked shared consumers:**

   - Academic Homework: `Homework.java`, `HomeworkService.java`, `HomeworkAssembler.java`, `HomeworkRepository.java`, `HomeworkNotificationJob.java`, `AcademicGrpcServiceImpl.java`, затем `V25__homework_schedule_binding.sql`;
   - Attendance: `AttendanceDocument.java`, `LessonEventService.java`, event consumers/read ports;
   - `event-schemas/lesson.{transferred,restored,cancelled,closed}.json` и binding-confirmation schema.

4. **Required behavior**

   - `Subject.id` остаётся единственной discipline identity; rename не меняет ID, одинаковые имена не сливаются.
   - `subject_lesson_types(subject_id, lesson_type)` хранит 1–3 доступных типа.
   - Assignment хранит `lessonType`, immutable teacher/subject/group/semester и интервал `[validFrom, validUntilExclusive)`. Пересечение запрещено только для одинакового teacher+subject+group+semester+type; разные преподаватели одного типа разрешены.
   - Assignment не редактируется и не удаляется. Replacement закрывает прежний interval и создаёт новую строку. Все текущие delete/remove paths переводятся на close.
   - Schedule create принимает `assignmentId`; group/subject/semester/type/teacher сервер получает из Academic и проверяет interval. Клиентские дубли этих полей не являются authority.
   - `LessonOccurrence` — стабильный logical ID и immutable assignment snapshot. `Lesson` — physical instance. Recurring и one-off проходят через один occurrence contract; one-off больше не удаляется физически.
   - Transfer блокирует occurrence/current instance/bindings, проверяет future, expected revision и target slot; source становится `TRANSFERRED`, target создаётся один раз, current pointer переключается. Один request key с тем же payload возвращает прежний результат; другой payload или stale revision даёт typed conflict.
   - Schedule владеет только binding/location: `LessonHomeworkBinding(bindingId, occurrenceId, currentLessonId, homeworkId?, state, requestKey, revision)`. Content/completion остаются в Academic.
   - Homework create сначала резервирует immutable bindingId. Academic сохраняет content с unique requestKey/bindingId и через outbox подтверждает homeworkId. Успешный published response запрещён до `ACTIVE`; unknown outcome повторяется с тем же requestKey. Pending binding не удаляется автоматически и не приводит к удалению content.
   - Transfer обновляет все PENDING/ACTIVE binding rows в той же Schedule transaction. Homework reads/week/reminders batch-resolve binding; stale stored date/number не используются. Dependency failure даёт typed temporary unavailable.
   - Cancel в Schedule transaction архивирует binding и добавляет append-only lifecycle entry. Restore не разархивирует Homework автоматически.
   - Restore увеличивает `attendanceGeneration`, сохраняет cancellation history и публикует revisioned restored event. Attendance уникален по `(occurrenceId,userId,generation)`; прежний status сохраняется с invalidation metadata. Текущее поколение после restore пустое.
   - Все lifecycle events несут occurrence, assignment snapshot, generation и monotonic revision. Consumer применяет их по aggregate order; duplicate игнорируется, gap retry/buffer, stale не меняет новое поколение. Closed event несёт semester snapshot и не использует active-semester cache.
   - Occurrence read возвращает current instances для denominator ровно один раз и отдельную history; cancelled/transferred source исключаются, one-off включается.

5. **Constraints**

   - Long generated IDs, FK IDs, ports/outbox.
   - Transfer/cancel/restore авторизуются по trusted context и occurrence group; body group не доверяется.
   - Миграция Schedule/Homework должна abort на существующих строках без однозначного assignment/binding. Cross-database SQL backfill и выбор произвольного преподавателя запрещены.
   - Production migration/deploy не разрешены.
   - Roster остаётся gate: только ACTIVE STUDENT grants; terminal roles — own historical read-only; suspended — typed unresolved. Self-insert в rank запрещён.

6. **Existing patterns**

   - Schedule outbox уже пишется в той же DB transaction через `BEFORE_COMMIT` (`DomainEventListener.java:21-24,40-52`).
   - Генерация сейчас идемпотентна по template/date (`Lesson.java:20-21`), но физическое regenerate/delete (`LessonGenerationService.java:151-170,184-202`) должно быть заменено сохранением occurrence history.
   - Текущий Homework tuple read используется также week/reminders (`HomeworkRepository.java:11-17`, `HomeworkNotificationJob.java:107-125`), поэтому adapter scope обязателен целиком.

7. **Acceptance criteria**

   - Same-name subjects остаются разными; rename сохраняет grouping.
   - 1/2/3 types и 2+ teachers одного type создают разные assignments; каждая пара имеет ровно один assignment.
   - Replacement не меняет teacher/type старых occurrences.
   - Recurring и one-off имеют одинаковый read/lifecycle contract.
   - Duplicate/concurrent identical transfer создаёт один target; conflicting concurrent transfer возвращает conflict.
   - Source history сохраняется, target считается один раз, Homework location меняется атомарно.
   - Delayed Homework confirmation после transfer активирует binding уже на target.
   - Retroactive cancel сохраняет исходные marks; restore показывает generation+1 без marks; stale close/cancel не заселяет новое поколение.
   - Cancel archives Homework; restore не resurrects it.
   - Никаких nullable assignment/type fields, заполняемых только тестами.

8. **Verification**

   - Pure unit: interval boundaries, multiple teachers, state machine, request-key reuse, lifecycle revision/generation.
   - PostgreSQL IT: V24/V17/V25 clean migration; explicit abort on ambiguous legacy rows; FK/check/exclusion/partial unique constraints.
   - Concurrency IT: two identical and two conflicting transfer transactions; one target and one binding switch.
   - Proto compile + Academic↔Schedule contract tests.
   - Event tests: duplicate, reordered, gap, retry/DLQ; cancel→restore→stale close.
   - Cross-service runtime: recurring/one-off, 1/2/3 types, teacher replacement, late binding confirmation, transfer+Homework, cancel/archive/restore, Schedule outage fail-closed.
   - Attendance Postgres/Mongo integration with authoritative ACTIVE STUDENT roster. Heavy Gradle не запускался.

9. **Do not**

   Не начинать identity/lifecycle writer до единого ownership proto/migrations/events; не группировать по имени; не выбирать assignment автоматически; не удалять occurrence/marks/Homework; не считать async event заменой Schedule transaction; не заявлять Attendance/Statistics foundation завершённым после pure calculators.

Без shared writes сейчас безопасно выделяется только numerical slice:

- `attendance/report/studentprojection/AttendanceMetricCalculator.java`
- `attendance/report/studentprojection/StudentProjectionException.java`
- `attendance/report/studentprojection/OwnRankCalculator.java`

Он даёт независимую вычислительную ценность, но не снимает ни один identity/lifecycle blocker. После интеграции обязательна свежая независимая recheck затронутых Academic, Schedule, Homework и Attendance контрактов.
