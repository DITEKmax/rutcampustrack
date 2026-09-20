# Goal

2026-09-20 implementation handoff: главный разрешил код по новому каноническому девятисекционному пакету `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/L5B-HISTORICAL-ATTENDANCE-IMPLEMENT.md`, SHA `2ABEAC042818EEF4AE98F40B979CA776D162D98F27A513A95CBAF8E3A801437D`. Он заменяет pending/NO CODE GO положения этого подготовительного документа. Все решения владельца приняты: initial cohort с начала текущего семестра, переводы по фактической Moscow DATE, auto-Н. Явный coverage marker только новых групп, managed atomic history и fail-closed для неоднозначных старых групп — утверждённая граница. Fresh Luna max developer `/root/l5b_historical_attendance_writer`, solewriter worktree `.agent/worktrees/v2-l5b-historical-attendance`, base `3d4115f3a4c4ddba473689e27ac1a0efb519a202`; Academic/proto/Attendance, без Schedule. Проверки/generation только по root lease. Текст ниже сохранён как история подготовки, канонический IMPLEMENT имеет приоритет.

S3 bounded repair: материализовать автоматическое «н» для исторических recurring-занятий по решению владельца 2026-09-20 «Автоматом ставится всем \"н\"», сохранив явные отметки и исключение отменённых занятий из учёта при повторной доставке и любом порядке close/cancel. Это подготовленный contract, не разрешение на реализацию.

# Context/evidence

Канонический base: `3d4115f3a4c4ddba473689e27ac1a0efb519a202`, `.agent/worktrees/v2-l5b-service-identity`. Source-only evidence: `RESULT.md` в этой папке; runtime/tests NOT_RUN. Главный координирует новый writer Lessons и историческую membership authority Access отдельно.

Критичный оригинал, повторно открытый lead: `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java:61–106,135–143`. Сейчас close параллельно читает lesson и текущих group members, использует activeSemester, затем upsert по `(lesson_id,user_id)` с `$setOnInsert(ABSENT/AUTO_SCHEDULER)`. Cancel меняет только уже существующие документы на CANCELLED. Поэтому cancel до первого close не препятствует последующей вставке ABSENT, а current roster/active semester не являются историческим источником. ReportService считает сохранённые AttendanceRecord; один CLOSED без materialization не закрывает требование статистики.

Access завершил source contract `.agent/l5b-historical-roster/CONTRACT.md`, лично прочитан lead, SHA `13A1209804FDA9CB1B18E6D81EA1684F4FE0BE1B73EB76AF9376575E2487EA46`. Его source map SHA `4532E1DC3FE3A3EB76219D8F3AB463B53A3F162EEDDE3744C08D7A449161A61C` сообщён Access, отдельно здесь не пересчитан. `joined_at`/`left_at` — DATE; membership predicate `joined_at <= D AND (left_at IS NULL OR D < left_at)`. Transfer date исключена из старой группы и включена в новую; zero-length interval пуст. `created_at` — audit, не membership authority. Существующий transfer использует server-default `LocalDate.now()`, не доказанный Moscow-zone instant.

Подтверждённый Access readiness gap: createUser не пишет начальный history; transfer допускает отсутствие origin interval; current grants/V24 trigger не создают dated eligibility, completeness marker отсутствует. Ни пустой, ни непустой SELECT не доказывают полное покрытие. Главный запросил у владельца только правило даты начального членства; само решение auto-Н уже принято и не переоткрывается. Нельзя вывести пропущенную историю из current group/account-created/semester-start без отдельного решения.

# Relevant scope

Предлагаемый минимальный Attendance scope: `LessonEventService`, durable per-lesson cancellation marker и узкие тесты исторического close, идемпотентности, сохранения явных отметок и close/cancel; также точечный фильтр канонического статуса в `ReportService.filterExistingLessons`. `EventConsumer` меняется только если согласованный контракт требует передачи уже существующего event metadata. Клиенты Schedule/Academic и shared proto/generated types — только после отдельного root freeze и назначения одного владельца; не копировать независимые DTO.

Временная собственность lead: только `.agent/l5b-cancel-statistics/`. Product worktree/assigned developer ещё не выделены. Lessons владеет producer initial status/events; Access — доказательством исторического состава. В scope не входят destructive one-off/delete handlers, новые пользовательские операции или широкая переработка ReportService.

# Required behavior

1. Для закрываемого физического занятия canonical `GetLessonById` задаёт lesson/group/subject/date/startTime/semester/status. Не подменять его active semester или текущим mutable parent. Event group сверяется с authoritative lesson scope. Date-specific roster разрешается только после получения snapshot; прежний параллельный вызов current roster не сохранять ради оптимизации.
2. Выбирать студентов, относящихся к этой группе на дату занятия, через утверждённый Access источник с точными интервальными границами. Текущий roster не fallback. Переведённый позднее студент не теряет историческую отметку; вступивший позднее не получает чужой исторический пропуск.
   Предложение root/Access для совместимости, до окончательного freeze: paired optional request `as_of_date=2` и `semester_id=3`; response echoes date/semester в tags 2/3. Attendance обязательно проверяет наличие и точное совпадение echoes. Старый Academic, проигнорировавший request fields, должен приводить к ошибке, а не к использованию current roster. Undated callers сохраняют прежний контракт. Типы и итоговые names/tags утверждает главный; DATE/half-open/same-day semantics и completeness подтверждает Access.
3. Материализация создаёт только отсутствующий `(lesson_id,user_id)` документ с ABSENT/AUTO_SCHEDULER. Повторный close не создаёт дубль и не меняет существующую явную отметку, её автора/источник/audit; уже отменённое состояние не превращается в ABSENT. Сохраняется принятая естественная уникальность и идемпотентный механизм upsert.
4. Обычная индивидуальная отмена сохраняет физическую историю и использует `lesson.cancelled`. После обработки событий отменённое занятие исключено из числителя и знаменателя независимо от close→cancel, cancel→close, повторов и конкурентного interleaving. Cancel, обработанный при отсутствии документов, не должен теряться для позднего close.
5. Предложенный главным минимальный механизм (ещё не writer freeze): durable terminal cancellation marker по physical lesson ID записывается до cancel `updateMulti`; повторная отмена идемпотентна. Close проверяет marker до materialization и повторно после upsert; при наличии применяет CANCELLED к документам занятия. При уже существующем marker close не создаёт новые ABSENT и может повторно обеспечить CANCELLED для существующих документов. Close/cancel подтверждают обработку только после обязательных post-check/update; сбой сохраняет возможность retry. Маркер нельзя удалять или обходить TTL/cache; уникальность lesson ID обязательна. Это сходимость через durable marker + post-check/retry, не общая Mongo-транзакция двух коллекций. Отчётный status guard ниже закрывает видимость отменённого занятия до сходимости документов.
6. Недоступный/неполный historical roster, отсутствующий semester snapshot и несовпадающий lesson scope не превращаются в успешную пустую материализацию или current-roster fallback. Ошибка проходит существующий retry/DLQ путь. Валидный пустой roster должен быть отличим от ошибки чтения.
   По Access contract unknown/incomplete coverage требует явного typed failure (предложен FAILED_PRECONDITION), malformed input INVALID_ARGUMENT, unknown group/semester NOT_FOUND, operational failure UNAVAILABLE. Подтверждённый complete scope допускает пустой success. Новое enrollment/history должно записываться атомарно с реальным изменением членства; точная effective date и критерий coverage ожидают root/owner freeze. Dated query не использует groupId-only current cache и не фильтрует исторический состав по текущим user status/group/grants. Display fields не становятся authority для eligibility.
7. Lessons гарантирует доставляемое событие материализации для каждого требуемого исторического занятия при согласованном initial status. Не фиксировать ни молчаливый CLOSED без эффекта, ни historical Started/Closed side effects до общего freeze. Восстановление отмены сохраняет существующую policy и не добавляет автоматического восстановления отметок этим scope.
8. `ReportService.filterExistingLessons` исключает канонические Schedule CANCELLED/TRANSFERRED по существующему status field `LessonInfo` (field 14), независимо от позднего ABSENT. Явный пустой результат после валидных excluded statuses допустим; неизвестный/неполный/некорректный ответ не превращается в правдоподобный пустой успешный отчёт. Canonical status должен быть актуален для этого guard; возможный stale cache не считается гарантией. Новый event schema не нужен.
9. Терминальный marker корректен только при запрете повторного открытия того же physical lesson ID. Главный планирует gate legacy restore для canonical и новую physical generation при восстановлении; этот gate — обязательная интеграционная зависимость до приёмки, не предположение о уже реализованном поведении.

# Constraints

RULES: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`, SHA `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`. No code GO; Terra NEVER, no children, one writer per isolated worktree, root slots/heavy queue. Будущий developer — назначенный Luna max, важное независимое review — Sol high. Сохранять чужие изменения; no push/deploy/main merge/secrets. Новые контракты и миграции требуют root allocation; это не разрешение production migration.

# Existing patterns

Существующий `$setOnInsert` сохраняет уже выставленные отметки и должен остаться основой default materialization. Это не запрет существующей отмене пометить документы CANCELLED: explicit marks не перезаписываются автоматическим ABSENT. Ordinary cancellation retained; destructive `lesson.deleted`/`lesson.one_off.cancelled` не замена. Existing ReportService filters CANCELLED у Attendance, но исключение позднего ABSENT требует canonical Schedule status guard. Использовать существующие event consumer, gRPC clients, уникальность документа и retry/DLQ, не вводить второй materialization pipeline.

# Acceptance criteria

- Историческое занятие получает ABSENT для точного roster на lesson date и правильный lesson semester; current-only участники не затронуты.
- Existing explicit marks/audit не перезаписаны close/default materialization; duplicate close не увеличивает число документов.
- Close/cancel в обоих порядках и конкурентной гонке дают одинаковое итоговое исключение отменённого занятия, включая исходно пустую Attendance collection.
- После корректной materialization неотменённое historical CLOSED участвует в legacy denominator; после cancel не участвует ни в числителе, ни в знаменателе. Недостающие данные authority не скрыты PASS.
- Source diff ограничен root-approved paths; новая producer/consumer связка проверена вместе, не только isolated mocks. Полная готовность статистики за пределами этого scope не заявляется.

# Verification

План для будущего root-owned batch, сейчас ничего не запускать:

- Узкий handler regression: исторический перевод из/в группу, другое значение active semester, existing PRESENT/EXCUSED/ручной ABSENT, duplicate close.
- Persisted-state regression в существующем Mongo harness: cancel→close на пустой коллекции; close→cancel; duplicate events; принудительное interleaving cancel между первым marker read и upsert. Проверять также сбой после durable marker до cancel update и после close upsert до post-check с успешным retry. Проверять документы и статистический результат, не только вызовы mock.
- Report guard: поздний ABSENT при canonical CANCELLED/TRANSFERRED исключён; malformed/unknown response не выдаёт успешный пустой отчёт. Интеграционный gate запрещает reuse отменённого physical ID; отдельный новый ID не наследует marker старого.
- Existing `ReportServiceTest.stats_cancelledExcluded` / `stats_allCancelled` плюс точный historical CLOSED→materialization→cancel denominator case. Сохранять существующие IDOR и cancel-event checks.
- Root объединяет producer создания ретроспективных занятий, historical roster и Attendance effects в один применимый integration/runtime gate; фиксирует revision/commands/exit/environment/evidence. Один fresh whole-batch Sol review после исправлений.

# Do not

Не начинать код до root freeze исторического roster API, snapshot fields, producer event semantics, durable close/cancel стратегии и restore gate. Не создавать новых scouts и не дублировать Access исследование. Не менять mark policy, не считать нынешний roster историческим, не скрывать пропущенную материализацию, не удалять историю, не возвращать mass-cancel, не ослаблять проверки ради PASS. Не переносить SQL fixture успех на role-driven retrospective creation.
