# L5B — предложение протокола v2, 2026-09-19

Статус: DRAFT для главного и независимого architecture review. Риск S3. Это не решение владельца, не implementation packet и не разрешение включить закрытие. Автор документа — lessons lead; ownership только `.agent/lessons-l5b-design/`. Код, proto, миграции и runtime не изменялись.

v1 сохранена без изменений в `v1/PROPOSAL.md` (D23AD9349E390F3A854C672592C53C43A7B62AE2F9C8CC84203E3220E8CFA57E), вместе с её sources/checks. `v1/root-review-correction-request.md` — точный пакет главного с результатом FULL FAIL2, а не сырой transcript reviewer. v2 исправляет HIGH B2 read protocol и MED lock graph; независимая полная повторная проверка ещё не выполнена.

## 1. Goal

Спроектировать Schedule-local durable cap для назначения и единый writer физических занятий, чтобы успешное закрытие Academic на исключающей дате D гарантировало отсутствие сохранённых физических ссылок с датой >= D и невозможность их поздней вставки. Одновременно определить occurrence/current physical identity, cancel/transfer/restore, replay и зависимости Homework/Attendance/клиентов. Закрытие остаётся typed `assignment-closure-not-ready`, пока весь протокол не реализован и не проверен.

## 2. Context/evidence

Авторитет: канонический `LESSONS-L5.md` v3, решение о принятии L5A и `LESSONS-L5B-DESIGN-PREP.md` в исходном `.agent/orchestration-v2/`. RULES SHA256 `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`. Эти документы выше legacy comments/тестов. CURRENT — изменяемый статус, не immutable evidence.

Две раздельные базы: принятая интеграция Schedule `426a15b6b42e816deaa3ca5c50437e0964aaf85e` в `v2-runtime-build-r2`; принятый Academic L5A commit `f59b3b9951c971bde42265ae67df8754dc594a58`, parent `b8220ac92125a8afa37598b270aa4fab7aa1f470`, в `v2-assignment-authority`. Перед реализацией назначенный интегратор должен составить и заморозить объединённую базу по точному inventory, сохранив Access/Maps/Homework/handler изменения. Предложение не утверждает, что L5A уже находится в r2, и не разрешает whole-worktree import.

Критичные оригиналы прочитаны лично; навигация и SHA256 — в [sources.json](./sources.json). Подтверждённые разрывы:

- V17 требует assignment/occurrence/physical snapshots, запрещает DELETE lessons и изменение physical identity; lifecycle entries append-only. Генератор сейчас собирает только scheduleItemId/date/status/geo/createdAt и удаляет planned/cancelled перед regeneration.
- Template CRUD вызывает этот генератор. One-off create сохраняет только родительскую запись и event, delete физически удаляет родителя; найденные Spring event forwarding paths не материализуют physical occurrence.
- Restore меняет старую physical row на PLANNED и очищает audit-tuple; это не реализация принятого требования новой пустой генерации. Startup reconciliation повторяет legacy delete/regenerate, marker записывается отдельно от regeneration.
- gRPC строит group/subject/time из текущего template и пропускает physical rows без него. Proto уже имеет assignment/occurrence/generation/revision, history/binding RPC, но это не доказательство работающих handlers.
- `DomainEventListener` фактически пишет outbox BEFORE_COMMIT, несмотря на устаревшие AFTER_COMMIT comments в callers. Сохраняем этот transport/persistence pattern.
- `SubjectDeletedCascadeService.cascade`, `LessonEventService.processLessonsDeleted` и `processOneOffLessonCancelled` содержат destructive paths. Отмена one-off удаляет Attendance по natural key, что несовместимо с новой сохранённой physical history и повторным использованием слота.
- Homework create пока использует ResolveLesson по natural key и legacy constructor вместо durable binding protocol. E-документы подтверждают PWA/TMA/offline adapter, а не готовый Schedule producer.

Источник B найден и прочитан: `C:/Users/maksd/.codex/worktrees/34a5/rutcampustrack/.agent/student-academic-b/foundation-decision-result.md`, SHA256 `00B22EC98BA8117E92E4F145DEBA5D1B2FEDC2B59D41B14644F5184A280FDDE2`. Совместимые решения: cancel архивирует bindings/Homework, restore их не возвращает, transfer переносит Homework через current pointer. Канонический `docs/architecture/reference-rutcampustrack-design/backend-conflicts.md`, SHA256 `701EBFB7C91906F1EF57B2D6ED5B54202A38475A1E246C3172FADB1AB6646DD8`: R7 разрешает ретроактивную отмену, инвалидирует без удаления marks, исключает cancelled из знаменателя и задаёт пустую сетку restore; R25 разрешает только будущий перенос в рамках того же assignment с новой датой/временем/аудиторией и переносом Homework. Поздний HANDOFF B2 отменяет старое требование B о предварительной Mongo migration/all writers/backfill для current reads. Решение о статистике при переводе студента между группами не относится к переносу занятия.

## 3. Relevant scope и операция/роль

Все пути ниже относительно будущей frozen integration baseline; existing symbols перечислены в sources.json. Новые имена — предложения, без назначенных migration/tag numbers.

| Операция | Actor / authority | Обязательная интеграция |
|---|---|---|
| Academic close по assignmentId либо subject/teacher + exact assignmentId | Существующий L5A STUDENT + isHeadman, trusted own group; без расширения ADMIN | Оба route → один close coordinator, durable request key и явная D |
| Template create/update/deactivate | Текущий Schedule headman группы или ADMIN; роль не меняется автоматически по Academic comments | ScheduleItemService + assignmentId + единый physical writer |
| One-off create/cancel | Текущий Schedule headman группы или ADMIN | OneOffLessonService → origin + occurrence + physical row в одной tx; cancel вместо удаления |
| Cancel/mass cancel | Текущая Schedule policy, actor и reason | Occurrence lock, expected revision, lifecycle audit, current pointer; без удаления Attendance |
| Transfer | Только явно авторизованный оператор своей группы по frozen API | TransferLessonRequest replay key/revision; новая physical row, прежняя остаётся TRANSFERRED |
| Restore | Текущая разрешённая Schedule role; конечный статус прошлого занятия требует источника | Новая generation/physical ID, пустые marks; cap/slot check, Homework остаётся архивным |
| Regenerate/startup reconciliation | Внутренний job с operation/run identity | Тот же writer/locks/cap; diff желаемых occurrence, не DELETE/повторное оживление cancellation |
| planned→active→closed, geo/block/reminder jobs | Existing system/user authority | Occurrence revision/current check; физическую identity не меняют, не оживляют superseded rows |
| Homework reserve/confirm/read | Existing authenticated boundary + проверка actor/scope | Schedule binding ownership, Academic content; operation replay, pointer/revision validation |
| Teacher/student current reads B2 | Caller-specific live authorization | Schedule current IDs → authorized marks batch → map → pointer/generation/revision recheck; один retry, затем 503 |
| History reads | Отдельная caller/scope authorization | Explicit retained IDs/generations; historical assignment identity не даёт live teacher permission |

Предлагаемая область реализации: Schedule item/oneoff/lesson services, entities/repositories/assemblers, generation/reconciler/status jobs, gRPC handlers/client и новая fence/receipt persistence; Academic close coordinator/client/operation persistence; минимальные API/proto extensions; versioned lifecycle events и явный Attendance consumer cutover; Homework binding writer/read integration; отдельная последующая клиентская адаптация. Общие proto/generated/migrations/outbox schema имеют одного назначенного writer. Это scope proposal, не параллельные назначения.

## 4. Required behavior — предлагаемый протокол

### 4.1. Schedule-local cap и доказательство сериализации

Новая долговечная строка `assignment_fences` с PK assignmentId хранит immutable tuple {group, subject, semester, teacher, lessonType, validFrom}, concrete `capUntilExclusive`, monotonically increasing fenceRevision. Строка не удаляется и cap никогда не увеличивается; infinity/null sentinel не нужен. Первичное effective end берётся из Academic GetAssignmentsByIds (L5A concrete end); identity не вычисляется по TSG/teacher list/template name.

Каждый physical create, transfer target, restore generation, template generation, regeneration и reconciliation:

1. До локальных DB locks получить authenticated Academic identity/validity и проверить actor scope. При недоступности или неполном batch — отказ без создания, не cached optimistic success. GetTeacherSubjects не подходит для future/historical producer validation.
2. В Schedule tx обеспечить строку fence через insert-on-conflict; существующую identity только сверить. Никогда не перезаписывать меньший cap более старым/широким remote end. Creator не сужает существующий cap произвольным refresh: меньший remote end при более широком local cap требует сверки durable close receipt/recovery либо явного отказа как protocol divergence. Любое реальное уменьшение cap проходит тот же locked ref-check/receipt protocol, а не обход через upsert. Initial bootstrap при уже существующих canonical physical rows также проверяет их совместимость, иначе abort.
3. Взять FOR UPDATE на fence. Далее origin rows, occurrence rows, physical rows — в фиксированном порядке; batch сортирует assignmentId, затем origin/occurrence IDs. Все writers, включая cancel/jobs, соблюдают этот порядок, без обратного occurrence→fence lock.
4. Под lock повторно проверить final physical date: validFrom <= date < min(local cap, validated Academic effective end), immutable tuple, current/revision и slot uniqueness. Remote response до cap install не является разрешением обойти этот recheck.
5. Создать/изменить origin/occurrence/physical/current pointer/lifecycle entry/outbox в одной tx. Проверка unique physical slot идёт через имеющиеся DB constraints; предварительный count не заменяет constraint/lock. Rollback сохраняет прежний pointer/history.

Cap install берёт ТОТ ЖЕ fence lock, затем отдельным свежим SELECT проверяет **все сохранённые physical lessons** assignmentId с `date >= D`, без фильтра по status/current/generation. При наличии — conflict с exact IDs/count, без сужения cap. Иначе cap := min(cap,D), revision увеличивается только при изменении, receipt записывается в той же tx.

Isolation proposal: READ COMMITTED; запрос refs выполняется после полученного lock отдельным statement, чтобы увидеть commit предыдущего creator. Нельзя читать refs до lock и использовать этот snapshot после ожидания. Альтернатива SERIALIZABLE требует полноценного retry всей tx; не подменять одним stale snapshot REPEATABLE READ. Physical insert guard в БД должен также брать/проверять ту же fence row (defence against future repository/native writers), а не полагаться только на список service callers. Fence setup не отключает V17 guards.

Доказательство двух исходов: creator получил lock первым → cap installer видит его committed physical date и при date>=D возвращает conflict; installer первым → stale creator после ожидания видит cap D и не вставляет date>=D. Для date<D обе операции допустимы. Lock обеспечивается PostgreSQL, а не ShedLock, JVM mutex или сетевым count.

Строгое следствие канона: CANCELLED/TRANSFERRED physical row >=D тоже блокирует close. Отмена либо перенос будущего занятия назад не стирают эту ссылку. Если требуется другая policy, нужна явная правка канона владельцем до реализации; здесь она не вводится.

### 4.2. Academic → Schedule, durable operation и replay

Предлагаем Academic `assignment_close_operations`: server operationId, unique(actorId, requestKey), canonical payload hash, exact assignment identity/D, state, fence receipt, result. На assignment допускается одна незавершённая close operation; другой key/payload получает conflict. Один key с другим payload — conflict, а не новая попытка. D обязательна, validFrom < D <= current effective end; расширение периода запрещено V25 и coordinator.

Последовательность без распределённой DB transaction:

1. **Academic PREPARE tx:** авторизовать оба REST route и exact path relation; взять новый общий порядок из 4.2.1 (он требует cutover существующих callers), перечитать validity, записать PREPARED operation с immutable actor/tuple/D/hash. Commit и отпустить DB locks до любого RPC.
2. **Schedule install RPC:** принимает operationId, assignmentId, D, payloadHash; не доверяет произвольным caller actor/tuple. Schedule по существующему authenticated inter-service каналу читает подтверждённую PREPARED operation из Academic (новый узкий RPC) до локальных locks; сверяет target service, exact identity/D/hash. Затем выполняет 4.1 и атомарно пишет immutable receipt `(operationId, hash, requestedD, acceptedCap, fenceRevision)` либо terminal reference rejection.
3. **Academic FINALIZE tx:** authenticated receipt для того же operation/hash, повторные ordered locks, exact assignment identity. Сохранить end, который не превышает D/уже установленного меньшего end, и COMMITTED operation/result в одной tx. Не возвращать successful close до этого commit. Outbox события final close — в этой же tx, если они включены в согласованный event contract.
4. **Recovery:** повтор Install с тем же operation/hash возвращает сохранённый receipt; GetCloseOperation возвращает состояние для того же авторизованного scope. PREPARED timeout/потеря ответа не отменяют cap. Internal recovery worker дочитывает receipt и завершает ту же operation. Новый close не обходит pending operation. Старый successful replay возвращает original operation outcome + актуальный ресурс без повторного расширяющего UPDATE.

Состояния: PREPARED → COMMITTED; подтверждённый ref conflict до установки cap → REJECTED. FENCE_CONFIRMED можно хранить как recovery checkpoint, но его потеря не меняет Schedule receipt. Transport failure остаётся pending/unknown с retryable status, не falsely REJECTED. Deadline после Schedule commit, Academic restart или lost final response разрешаются replay. Если Academic finalize не может завершиться, cap остаётся более строгим; safe-unavailable состояние видимо оператору, автоматического reopen/компенсации нет.

Auth gate: current Schedule GrpcAuthInterceptor проверяет общий secret только если он настроен; это не caller-specific principal. Fail-closed credentials и allowlist caller→RPC→scope обязательны для InstallAssignmentCloseCap, GetPreparedAssignmentCloseOperation, close status/replay, ReserveHomeworkBinding, ConfirmHomeworkBinding, GetHomeworkBindings и GetOccurrenceHistory. Отсутствующий secret/principal, чужой service, foreign group/binding/occurrence и неавторизованный actor отклоняются до domain reads/writes; UUID/key не разрешение. Academic-owned prepared operation дополнительно проверяется на cap пути. Binding reserve/confirm сверяют persisted actor, payload hash, allowed group/occurrence и publication transition; history отдельно проверяет право видеть retained данные, а не только current доступ. Batch не возвращает чужие записи. Существующий транспорт сохраняется; конкретные credentials/identity и матрицу callers утверждает главный до реализации этих handlers. Replay не доверяет actorId из внешнего JSON. Policy отзыва прав после PREPARE — раздел 5; cap не откатывается.

### 4.2.1. Полный порядок Academic locks и обязательный cutover

Фактический граф L5A отличается от v1: `addTeacher` берёт subject FOR UPDATE → semester FOR UPDATE; `assignTeacher/AssignmentAuthority.create` берёт semester, затем INSERT assignment получает FK KEY SHARE на users, subjects, semesters и subject_lesson_types. `createSubject` сначала блокирует отсортированные semesters, затем создаёт свой subject/types/assignments. V25 deferred parent/child minimum-type triggers получают subject FOR UPDATE при flush/commit; child FK также получает parent KEY SHARE. Поэтому явного порядка только для close недостаточно. Конкретный цикл v1: close удерживает semester и ждёт subject; addTeacher удерживает subject и ждёт тот же semester. Это дефект предложения; текущий L5A close отключён typed409. Главный отдельно подтвердил текущий assignTeacher↔addTeacher цикл статическим разбором immediate IDENTITY INSERT/FK и фактического Hibernate dialect: это зафиксировано в LESSONS-L5A-LOCK-ORDER-FIX.md и исправляется отдельным автором после f59. Runtime deadlock пока не воспроизводился; данное предложение не заявляет исправление принятого checkpoint.

Предлагаемый единый порядок для затронутых Academic транзакций: **groups (ID ↑, KEY SHARE) → users (ID ↑, KEY SHARE) → semesters (ID ↑, FOR UPDATE) → subjects (ID ↑, FOR UPDATE) → subject_lesson_types (subjectId/type ↑) → assignments (ID ↑, FOR UPDATE) → close operation → outbox**. Пропуск ненужного уровня допустим; возврат к ранее пропущенному уровню после нижнего lock запрещён. Existing parent IDs собираются read-only до tx, затем под locks перечитываются и сверяются; изменение набора означает rollback/restart с полным новым набором, не дозахват в обратном порядке. Subject для create новый и невидим другим tx до commit; его INSERT/типовые child writes выполняются только после всех нужных existing parent locks. Users/groups нужны из-за FK, даже если бизнес-проверка ранее была plain SELECT. Для parent, который эта tx меняет/удаляет, брать сразу FOR UPDATE, без позднего upgrade. Не полагаться на порядок Hibernate flush или порядок FK triggers.

| Caller/путь | Фактические дополнительные edges | Обязательное изменение перед close cutover и revalidation |
|---|---|---|
| SubjectService.createSubject | semester → новый subject; subject→group FK; types→subject deferred FOR UPDATE; assignment→user/subject/semester/type FK | До save собрать group, actor/teachers и все semester IDs; locks по общему порядку; перечитать own group, grants, dates/types и overlaps; затем новый subject/types, затем assignments |
| SubjectService.addTeacher | subject→semester, затем assignment FK | Убрать ранний lockSubject; после read-only discovery взять group/users→semester→subject→type; заново own group, teacher grant, semester interval, type membership и overlap; затем save |
| AssignmentService.assignTeacher / AssignmentAuthority.create / createWithLockedSemester | semester→неявный subject/user/type lock при save | Один общий ordered-lock entrypoint; createWithLockedSemester не доступен как обход subject/parent locks; после locks повторить tuple/grant/dates/type/overlap проверки. createSubject передаёт проверенный lock context нового subject |
| SubjectService.updateSubject | subject→type rows→deferred subject FOR UPDATE | Group/users если требуются для writes — до subject; subject перед любыми type writes, история assignments перечитывается под lock. Не брать semester после subject; если он нужен, restart через общий порядок |
| SemesterService.createSemester/updateSemester | INSERT/exclusion index либо semester FOR UPDATE; exists assignments — plain SELECT | Create не берёт subject/type/assignment locks; update держит semester, повторяет date/assignment checks после lock и не блокирует дочерние assignments. Exclusion violation → typed conflict; bounded whole-tx retry для serialization/deadlock, не частичный save |
| SemesterService.activateSemester | bulk deactivate locks активные rows, затем target; порядок сейчас не гарантирован | Вместо unordered bulk сначала sorted FOR UPDATE всех существующих semesters, затем reread active/target и update в этом порядке; если snapshot набора изменился — rollback/retry. Все affected semesters захватить до mutations/outbox, не брать subject |
| SemesterService.deleteSemester | DELETE row lock и FK checks/cascade legacy children | Сначала semester FOR UPDATE, заново confirmation и retained assignment refs. При refs — conflict. Не каскадировать assignments/history; legacy child dependencies — отдельный explicit guard до DELETE, без child-first блокировок |
| SubjectService.deleteSubject | subject FOR UPDATE→Schedule count RPC; type deletion→parent deferred lock; FK descendants | Read-only remote precheck вне tx. Затем group/subject lock и повторная проверка версии/own group/отсутствия любых assignments и локальных refs. Любая assignment reference блокирует delete. Благодаря обязательному assignment provenance новый Schedule producer не может создать ref на такой subject без Academic assignment; legacy/import bypass запрещён. Если эта предпосылка не доказана на frozen baseline, delete остаётся disabled, count не делает его безопасным. Parent перед child cleanup; никаких RPC под lock |
| Оба close routes: AssignmentService.removeAssignment и SubjectService.removeTeacher | Сейчас typed409; v1 добавляла конфликтующий порядок | Оба идут в один coordinator: discovery→общие parent/semester/subject/type/assignment locks→повтор actor/path/tuple/D/current end/pending проверки→operation. FINALIZE тем же порядком и fresh receipt/identity check |
| Close recovery/status mutation | Возможный operation-first→assignment обратный edge | Worker выбирает candidate без held row lock, RPC вне tx; затем полный общий порядок, operation lock последним; повторяет exact hash/state/receipt. Не держать SKIP LOCKED operation row во время захвата родителей/RPC |

При ordered prelocks FK возвращается к уже удерживаемому parent, deferred type trigger — к уже удерживаемому subject, поэтому они не добавляют ожидания в обратном направлении. Overlap exclusion assignment INSERT сериализуется общим semester/subject lock. Для многопредметного batch сначала все semesters, затем все subjects, а не цикл «semester→subject» по элементам. Post-lock reads — свежие READ COMMITTED statements, не stale managed entities.

Этот граф включает FK users.group_id→groups, subjects.group_id→groups и V25 parents. Совместимость Access user/group deletion/update, legacy FK cascades и Homework writers должна быть проверена их владельцами при общей integration freeze: операция, затрагивающая несколько уровней, обязана соблюдать тот же порядок или быть guarded/disabled. Это явный межкомандный gate, а не утверждение, что весь Academic уже переведён. Никакого RPC под locks ни в одном из перечисленных путей. Все changes в таблице входят в будущий integration contract; в текущем design scope код не меняется.

### 4.3. Physical lifecycle и snapshots

- Initial recurring: unique(scheduleItemId, occurrenceDate) + generation=1; one-off: unique(oneOffId). В одной tx создать occurrence, physical snapshot, pointer и CREATED entry. All positive IDs и assignment tuple server-derived. Состояние/time вычисляются согласованным Clock; backdated semantics — policy gate.
- Transfer: только будущее занятие и будущий target, тот же assignment/semester/validity, новая дата/время/аудитория (R25). Lock fence→origin→occurrence/current row, expectedRevision, target cap/slot. Старую physical row пометить TRANSFERRED, вставить новую с тем же occurrence и generation, переключить pointer, увеличить revision, записать source/target entry/replay в одной tx. Все PENDING/ACTIVE Homework bindings переключаются на новый current physical в этой же tx; Academic content остаётся тем же, чтение batch-resolves Schedule binding. Old date/time/room immutable; marks не копируются по natural key. Same key/hash возвращает те же IDs; stale revision не создаёт второй target.
- Cancel, включая задним числом (R7): сохраняет physical ID/snapshot и audit; increments revision. Marks инвалидируются для current расчёта, сохраняются без удаления; cancelled исключён из знаменателя. PENDING/ACTIVE bindings архивируются, Homework уходит в архив через authoritative binding state. Из included slot выходит только эта physical row. Replay не стирает reason/actor. Mass cancel использует тот же writer и sorted locks.
- Restore: cancelled physical остаётся в истории; новая physical row generation+1 и новый ID, expected revision, cap/slot validation. Новая сетка marks пустая, старые marks/history сохранены. Pointer переключается атомарно. Homework/bindings остаются ARCHIVED и не воскрешаются (B/R7); новая публикация требует нового binding. Конечный статус восстановленного прошлого занятия остаётся отдельным source gap; нельзя автоматически назначать PLANNED по legacy коду.
- Regeneration: вычислить desired origin dates из неизменного assignment interval и template revision; unchanged occurrence — no-op. Изменение time/room/slot представлять lifecycle transition с новым physical snapshot, не mutation старой row. Исчезнувшие даты отменять с audit, новые создавать через fence. Cancelled вручную occurrence не оживлять генератором. Смена assignment/subject/type не мутирует existing occurrence: нужен новый origin/version, старый retained.
- Startup reconciliation: никаких DELETE planned/cancelled. Долговечный versioned run/item progress, idempotent operations и per-item transaction/lock; marker success только после успешного завершения всех item checkpoints. Crash после commit до marker даёт no-op replay; два инстанса не создают duplicates. При Academic outage job не создаёт rows и не помечает завершение. Не превращать restart в guessed backfill.
- Status/reminder/block jobs работают только с current physical и expected revision под тем же порядком locks; события используют immutable snapshot, не текущий template. Late job не активирует CANCELLED/TRANSFERRED/superseded row.

Read mapping: LessonResponse/Info заполняются physical tuple/date/time/room, occurrence pointer/current и согласованной revision. Parent template не источник historical identity и не условие существования one-off. GetOccurrenceHistory возвращает все physical IDs/generations и append-only entries только после отдельной history authorization. Natural-key ResolveLesson разрешает только текущий included slot; retained IDs читаются явно по ID/history. Existing proto поля 16–25 и 7–14 использовать без renumbering; semantics `valid_from/valid_until` надо заморозить (не путать с assignment validity). Неполная identity — ошибка, не zero fields/silent skip.

### 4.4. B2 — обязательный current read protocol

Это отдельная реализуемая read задача, без prerequisite Mongo migration, изменения всех Attendance writers или backfill. Existing AttendanceDocument уже хранит `lesson_id` и unique `(lesson_id,user_id)`; новое поле generation в Mongo не требуется для такого join. Schedule остаётся authority current physical identity.

1. Проверить actor и запросить у Schedule авторизованный current набор для exact group/query: physical IDs + occurrenceId/generation/revision/current pointer, status и immutable snapshots. Проверенная пустая выборка отличается от timeout, missing dependency/identity или неполного batch: последние — typed 503, не пустой roster.
2. Получить **authorized marks batch строго по этим physical IDs**, с разрешённым user/group scope. Не читать по natural key и не подмешивать marks старых IDs. Успешный ответ с отсутствующими marks означает пустые marks только для verified current IDs; отказ/неполный транспортный ответ не означает отсутствие marks.
3. Сопоставить по exact physical ID; occurrence/generation берутся из проверенного Schedule ответа, а не угадываются по Mongo/natural key. Current marks/metrics projection исключает cancelled/transferred; denominator считает current included occurrence один раз. Журнал при этом сохраняет отдельную cancelled колонку с причиной (R7), без зачёта invalidated marks; не удалять её из UI под предлогом current фильтра.
4. После mapping повторно проверить у Schedule pointer **и generation, и revision**, включая состав выборки и current/status, для того же scope. Revalidation читает свежее состояние, не исходный cache. Для диапазона нужна проверка membership token/revision всего набора либо повтор всей исходной выборки: recheck только прежних IDs не обнаружит новый/moved-in occurrence.
5. Если что-либо изменилось — отбросить весь candidate response и **один раз повторить с шага 1**, включая marks batch. При второй несовместимости вернуть **503**, не частичный/stale success; dependency outage также 503. Не делать бесконечный retry. Успешный ответ согласован на момент второй Schedule проверки; протокол не обещает блокировать будущие изменения после ответа.

Historical reads — отдельный endpoint/mode с отдельной authorization retained IDs/generations, без скрытого fallback из current. После transfer/restore между двумя Schedule reads старый marks batch никогда не выдаётся как current; restore новый physical ID означает пустую сетку, даже если old marks сохранились. B2 read может поставляться до Mongo migration; **активация lifecycle producers** отдельно требует retention-safe consumer cutover из раздела 6, чтобы legacy delete consumers не уничтожили audit. Эти два gate не объединять.

## 5. Constraints, подтверждённые решения и открытая сверка

Подтверждено каноном: durable cap перед Academic end; same local lock для всех creates; refs>=D block; immutable retained identities; no unsafe reopen/guessed backfill; L5A close сейчас disabled; clients/L5B обязательны для полного DONE.

Предлагаемые engineering decisions для root freeze: fence/operation/receipt tables и lock ordering выше; READ COMMITTED post-lock ref query; DB insert guard; no RPC under DB row locks; one pending close per assignment; strict all-retained refs; origin/version replacement для mutable template identity; versioned outbox lifecycle + replay.

Разделение принятого канона, пробелов источников и engineering freeze (это не список вопросов пользователю):

1. **Принято, повторное подтверждение не нужно:** strict retained refs означает, что close раньше последней даже отменённой/перенесённой physical даты невозможен. Это consequence канонического решения; такие refs не исключаются.
2. **Принято R7/R25/B/HANDOFF:** retroactive cancel, marks invalidated/retained, cancelled вне знаменателя, restore пустой generation без Homework resurrection, transfer только future/same assignment с Homework. Cross-semester transfer запрещён immutable assignment/semester boundary. Открыт только конечный статус восстановленного прошлого занятия; не выводить его из legacy PLANNED.
3. **Source resolution перед root freeze:** точный template cutover date, room-only change, assignment replacement и сохранение ручных cancellations при template edits. B source теперь найден; он не разрешает автоматически удалять/сопоставлять по имени/слоту.
4. **Root security/engineering freeze:** revocation после PREPARE — recovery завершает ранее принятую operation либо удерживает strict cap до operator resolution. Сначала сверить общую политику revocation; любой вариант сохраняет no-reopen. Это не автоматический вопрос пользователю.
5. **Принято Homework:** transfer переключает PENDING/ACTIVE bindings атомарно с occurrence; cancel архивирует; restore не reopening ARCHIVED. Reads batch-resolve binding/current identity, не доверяют отстающему Academic pointer. Engineering freeze нужен для transport/retry/retention rollout, не для повторного вопроса о resurrection.
6. **Межкомандный engineering contract:** B2 double-read/one retry/503 задан в 4.4 без Mongo prerequisite. Lifecycle consumer сохраняет original marks/audit и исключает invalidated из current; детали event version/dedup/rollout согласуются с attendance lead. Marks не переносить на новую generation, второй authority не добавлять.
7. **Пробел карты источников:** текущий one-off request не несёт start/end. До API change найти и проверить существующий authoritative time-source/accepted design; отсутствие поля не разрешает выдумать timetable. Semantics Schedule `valid_from/valid_until` также замораживает главный по существующим consumers.
8. **Root engineering freeze:** fail-closed service identity и caller/scope matrix для cap, binding writes/reads, history и replay; event schema/rollout; общий Academic lock cutover 4.2.1. Общий configured secret не доказывает уникальную identity. Эти технические решения не требуют автоматического user question.

Пробелы передаются главному для source resolution/engineering freeze. Лишь если после адресной сверки действительно отсутствует существенное продуктовое решение, главный формулирует вопрос владельцу. Новых user questions это предложение не создаёт. Доступного memory/ownership gateway в callable tools не найдено; ownership основан на явном root packet/GO и непересекающейся новой папке. Source-transfer manifest вне этой папки не менялся.

## 6. Existing patterns и минимальные contract changes

| Boundary | Минимальный предлагаемый change | Необходимое сохранение |
|---|---|---|
| Schedule REST create template/one-off | required assignmentId; exact desired date/time source; replay key на creating operation | group/subject/semester/teacher/type сверяются/выводятся из assignment, не inferred TSG |
| Schedule lifecycle REST | expectedRevision + requestKey для cancel/restore/mass/updated template; существующий TransferLessonRequest переиспользовать | existing IDs/tag/types, typed conflict и stale revision без destructive retry |
| Academic REST close | existing explicit D и exact assignment path + durable request key/status contract | L5A authorization; до gate прежний typed409 |
| Schedule gRPC | новый InstallAssignmentCloseCap + read/replay status/receipt; существующие history/binding RPC реализовать | старые field numbers/reserved tags не менять; CountSubjectReferences остаётся только query |
| Academic gRPC | узкий GetPreparedAssignmentCloseOperation; existing GetAssignmentsByIds concrete interval | operation author/tuple из Academic; nonpositive/missing IDs fail closed |
| Schedule DB | fence+cap operation receipts, origin/replay metadata, physical validity snapshot при выбранной semantics | V17 history constraints/unique included slot; только новая forward migration |
| Academic DB/callers | close operation ledger/pending uniqueness; общий ordered parent→semester→subject→type→assignment protocol 4.2.1 | V25 immutable identity/monotonic end и FK/type guards; existing addTeacher order требует correction |
| B2 current reads | verified Schedule IDs→authorized marks→map→revalidate pointer/generation/revision/membership; один retry→503 | Existing Mongo lesson_id пригоден; без prerequisite migration/all writers/backfill; history отдельно authorized |
| Events/Attendance | versioned lifecycle envelope с eventId, occurrenceId, physicalId, generation, revision, action, actor/time, snapshot | existing BEFORE_COMMIT outbox/Rabbit retry+dedup; no lesson.deleted/natural-key-delete for retained lifecycle |
| Homework | Schedule reserve → Academic PENDING content → confirm → ACTIVE with exact binding identity; retries same operation | V25 binding identity, V17 no archived reopen; no duplicate content on timeout |
| Clients | exact assignment/type/date/IDs, pending close status, expected revision/key, unavailable operations, explicit conflict refresh | accepted E session/owner/offline boundaries; no offline mutation replay/force-delete retry |

Event schema/version, exchange/routing/consumer queue, dedup key, retry/DLQ and rollout order должны стать отдельной таблицей frozen implementation contract после открытия конфигурации соответствующим владельцем. Здесь не назначаются выдуманные routing keys. Пока legacy destructive consumer остаётся доступен для старых queued events, новый lifecycle не активировать; idempotent consumer должен сверять physical identity/revision и сохранять audit, включая stale events после reuse слота.

## 7. Acceptance criteria — обязательная runtime matrix будущей реализации

Это план проверок, не выполненные тесты. Использовать реальные authorized producers/PG и межсервисный fault injection; SQL fixture лишь для setup/инвариантов.

| Сценарий | Ожидаемый наблюдаемый результат |
|---|---|
| Headman creates assignment → recurring/one-off → teacher/student reads | exact assignment/occurrence/physical tuple, positive generation/revision; без TSG |
| Foreign group/non-headman/bad internal credentials/forged operation | отказ, нулевые domain/fence/operation побочные изменения для unauthorized request |
| Creator date=D получает fence первым | creator commit; close409; Academic end прежний |
| Installer получает fence первым; creator держал старый Academic response | close cap durable; creator date>=D409, без physical/outbox rows |
| Creator date=D-1 и close D | оба commit, cap/end D |
| Retained cancelled/transferred row>=D | close409, snapshot/history неизменны |
| Two concurrent close operations same/different key/D | same key/hash один receipt/result; conflict/more restrictive monotonic order, никогда extend |
| Network loss после cap commit / до Academic finalize / lost final response | replay той же operation, no reopen/duplicate; pending visible until exact final result |
| Concurrent template update/generation/one-off/restore/reconcile vs cap | каждый creation path проходит один fence; ни одного stale physical >=D |
| Transfer competing slot / expectedRevision stale | один accepted target либо conflict, rollback source/pointer/outbox целиком |
| Cancel→restore, включая прошлую отмену | old physical+marks retained/invalidated, cancelled вне denominator; new generation/ID/current pointer, marks empty; Homework остаётся архивным |
| B2: transfer/restore после первого Schedule read, до marks/recheck | candidate целиком отброшен; один полный retry возвращает только новый current ID/generation/marks |
| B2: повторная mutation на retry; missing Schedule/marks dependency | 503, не stale result/пустой roster; authorized empty marks различимы с outage |
| B2: moved-in/new occurrence меняет состав диапазона | membership revalidation обнаруживает изменение даже вне исходного списка IDs |
| B2 до Mongo migration/backfill/all writer changes | current join по existing lesson_id корректен; history не подмешивается; lifecycle activation имеет отдельный retention gate |
| PG barrier: оба close routes/recovery vs addTeacher/assignTeacher/createSubject | actual entrypoints соблюдают semester→subject, FK/deferred parent locks не создают обратный wait; pending/revalidation сохраняют authority |
| PG barrier: semester activation/update/delete и subject type/delete vs create/close | sorted multi-row locks, post-lock checks; no RPC under locks; refs/history guards не ослаблены |
| Binding/history RPC: missing credentials/wrong caller/foreign group и replay | fail closed до чужих данных/мутаций; key/UUID не заменяет authority |
| Restart/multi-instance reconciliation / crash before marker | idempotent current IDs, cancellation retained, no history DELETE, marker not false success |
| Template/subject rename/change after lesson creation | historical physical fields стабильны, one-off не зависит от recurring parent |
| Replay/out-of-order lifecycle events / stale old natural-key cancel | старые marks не удалены, новый slot/physical не затронут; consumer revision/dedup |
| Homework reserve/confirm timeout + simultaneous transfer/restore | exact binding/current identity, no duplicate content; archived не reopening |
| Academic unavailable/current grant suspended | live teacher access denied; new write fail closed; authorized retained historical reads по agreed policy |
| Migration on legacy ambiguous rows | guard fail с evidence; никаких guessed maps/deletes/disable triggers |

До activation также нужны tests всех producers через DB fence guard, негативное доказательство при намеренно stale remote snapshot, DTO/OpenAPI/proto generation check, независимый S3 review полного task diff и реальный integrated headman→Schedule→Attendance/Homework→student flow. H64/H69/H70 доказывают L5A, не эту матрицу.

## 8. Verification и миграционный порядок

Текущая проверка: только read-only original source inspection + mechanical JSON/path/hash/document checks. Runtime N/A для design. Команды/exit/context фиксируются в checks.json; sources.json содержит точные версии/пути/hash, а не доказательство product behavior.

Предлагаемый порядок последующего freeze: (1) root решает раздел 5 и auth/event contracts; (2) single integrator фиксирует baseline с accepted L5A exact46 и текущей интеграцией; (3) fresh independent architecture review этого протокола; (4) bounded implementation packet, migration design/guard и единственный writer общих contracts; (5) local clean isolated DB tests полной matrix; (6) consumer/client cutover и integrated runtime; (7) только затем activation отдельным решением root/владельца по принятым правилам.

V17/V25 existing migrations не редактировать. Новые forward migrations должны проверить provenance и целостность уже канонических rows. Для `assignment_fences` допустима только проверенная identity из Academic с точным assignmentId и retained snapshots; обнаруженное противоречие — abort/reconciliation decision, не min/max догадка из текущего template. Legacy ambiguous rows не backfill автоматически и не удалять. Возможность выключить новые writes при rollback сохраняет cap/history; rollback не включает reopened assignment или удаление новых durable ledgers. Production migration/deploy здесь не разрешены.

## 9. Do not

Не активировать close по count-only RPC. Не держать Academic locks во время RPC, не делать Schedule→Academic callback под fence lock. Не пропускать cap для restore/startup/import/native writer. Не расширять cap из stale snapshot. Не объявлять cancellation освобождением retained refs. Не удалять physical/history/Attendance по natural key. Не угадывать assignment/type/time/provenance. Не переиспользовать old physical ID для новой empty generation. Не переносить целиком E или менять чужие shared orchestration документы. Не считать этот draft принятым ADR либо runtime PASS.
