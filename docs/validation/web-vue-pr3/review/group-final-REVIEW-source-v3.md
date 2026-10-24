# GROUP FINAL DELETE — независимый SOURCEv3 review

Вердикт: **FAIL / нужны corrections**. Подтверждены два P1 и один P2. Это source review, не результат компиляции или выполнения сценариев.

## Неизменяемая граница review

- Base: `10d8b908ae9801b22018881389f21ef5c0188583`.
- Только `/workspace/rct-pr3-runtime/group-final-delete-20261024/review-source-v3/source/` и неизменённые зависимости из `git show` указанного base.
- Patch SHA-256: `227d393ba685aea8d620e8cc1bc1106901e1fff911ec0180800ae3a1c3139898`.
- SHA256SUMS SHA-256: `a712783de2276aa34682bc7c0bd91b7d14a206d9f536f1602cfab0995dc42873`.
- Проверка `sha256sum -c SHA256SUMS`: все 40 файлов совпали.
- Изменяемый worktree автора и SOURCEv1/v2 не использованы для оценки актуального кода. Более поздние compile corrections этим отчётом не проверены.
- Gradle, Docker, БД, API, сеть, CI и runtime не запускались; секреты не читались. Изменён только этот отчёт.

## F1 — P1: восстановление PREPARING удаляет группу после отзыва полномочий или подтверждения

Основное место: `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/groupdeletion/GroupDeletionCoordinator.java:50–57`; связанные места: `GroupDeletionStore.java:194–204`, `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthGroupDeletionParticipant.java:97–109`.

**Trigger.** ADMIN успешно подтверждает пароль; операция сохранена в PREPARING. Недоступность участника откладывает завершение. До следующей попытки у инициатора отзывают ADMIN/GROUPS_MANAGE/DATA_DELETE, закрывают исходную сессию, меняют credentials или истекает 60-секундный receipt. Затем участник становится доступен.

**Доказанная цепочка.** Планировщик `resume()` вызывает `advance()` без HTTP actor context. В PREPARING выполняются PREPARE, SEAL и `store.beginCommit(id)`. Последний проверяет только локальный digest. `proof_receipt` сохранён при accept, но не входит в `Saved` и больше нигде не проверяется. Команда участникам содержит лишь actor ID, без исходной session identity и receipt. Auth participant на SEAL/COMMIT проверяет состояние и digest; `actor_id` только сравнивается с сохранённым ID, а текущие права/сессия/credential generation не читаются. В комментарии Auth participant admission возложен на coordinator, но coordinator его не выполняет.

**Эффект.** После восстановления scheduler переводит операцию в durable COMMITTING и удаляет grants/расписание/посещаемость/группу от имени уже лишённого полномочий инициатора. Отказ доступа к HTTP status не защищает scheduled recovery. Это нарушает требование актуальной actor authority до необратимого решения commit.

**Минимальное исправление.** Сохранить и передавать исходную session identity и точный bound step-up receipt; непосредственно перед durable COMMITTING проверять через Auth актуальную сессию, роль/permissions, credential/profile generations и receipt. До решения commit отрицательный admission должен вести в durable RELEASING; недоступность authority оставляет PREPARING. После durable COMMITTING продолжать только идемпотентное завершение, без повторной отмены уже принятого решения. Не восстанавливать JWT из сохранённых claims.

## F2 — P1: CASCADE удаляет threshold другой группы вне preview и manifest

Основное место: `services/academic-service/academic-app/src/main/resources/db/academic-migration/V14__final_group_deletion.sql:37,70–80`; удаление parent: `GroupDeletionStore.java:256–282`.

**Trigger, достижимый существующим API.** Староста группы A вызывает существующий `PUT /academic/thresholds/subject?subjectId=<предмет группы B>`. В неизменённом base `threshold/ThresholdService.java:71–83` берёт group A из текущего context и переданный subject ID, не проверяя принадлежность предмета группе. Базовая схема разрешает такую строку: `V3__academic_domain_baseline.sql:94–101` содержит независимые FK `group_id -> groups` и `subject_id -> subjects`, оба с ON DELETE CASCADE. Затем ADMIN подтверждает окончательное удаление группы B.

**Доказанная цепочка.** Новый manifest выбирает thresholds только `WHERE group_id=B`; строка `(group_id=A, subject_id=subjectB)` не показана и не разрешена immutable manifest. При удалении subjectB срабатывает сохранённый ON DELETE CASCADE. Новый group guard берёт ненулевой `group_id=A` и не проверяет group предмета; активной deletion operation для A нет, поэтому DELETE проходит. Существующий `guard_academic_subject_write` проверяет только `subject_deletion_operations`, а новый group protocol туда не пишет. Прочие triggers на thresholds в актуальном migration chain эту комбинацию не запрещают.

**Эффект.** Настройка группы A исчезает при удалении B, хотя её нет в подтверждённых counts/digest и данные других групп должны сохраняться. Этот finding не требует предположения о ручной порче БД: комбинацию создаёт текущий endpoint.

**Минимальное исправление.** До принятия необратимого решения проверять замкнутость target manifest относительно каскадных зависимостей и отклонять cross-group references с сохранением всех данных. В точном deletion context не допускать каскадный DELETE строки вне разрешённого manifest; нельзя просто включить чужую строку в удаление B. Предотвращение новых несогласованных threshold references полезно, но не заменяет защиту уже существующих строк. Глобальные и чужие настройки должны остаться.

## F3 — P2: cancellation/no-row lesson обходят Mongo group fence и не входят в manifest

Основное место: `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/groupdeletion/AttendanceGroupDeletionParticipant.java:158–165`; связанные места: `GroupDeletionMongoWrites.java:76–89,106–121`, `student/PairWriteCoordinator.java:128–153`.

**Trigger.** У удаляемой группы есть lesson из сохранённого Schedule manifest, но в Attendance ещё нет `lesson:<id>` pair fence. После PREPARE/SEAL приходит существующее `lesson.cancelled` для этого урока. Тот же пропуск manifest присутствует и для cancellation markers, созданных до preview.

**Доказанная цепочка.** Неизменённый `event/LessonEventService.java:445–483` обрабатывает cancellation с `groupId=null`, создаёт pair fence и cancellation marker. `PairWriteCoordinator` вызывает group fence лишь при ненулевом group ID и для нового pair записывает `group_id=null`. Базовый `event/LessonCancellationMarker.java` вообще не содержит group ID: только lesson ID, semester ID и markedAt. Новый Mongo aspect извлекает только `group_id`; из lesson ID принадлежность не разрешает, а пустой набор групп пропускает mutation. Participant уже имеет точный список lesson/semester от Schedule, но не создаёт по нему prepare fence/mapping и при snapshot всех collections использует исключительно `group_id=target`. Поэтому marker и новый pair не изменяют digest; они проходят PREPARED/SEALED и остаются после COMPLETED. Выборка `lesson_cancellation_markers` по group ID также всегда пропускает обычные markers старого обработчика.

**Эффект.** Заявленная транзакционная group fence не покрывает реальный mutation path без group ID; numeric preview/manifest не учитывают эти строки target lesson и завершённое удаление их не стирает. Это не требование удалять opaque operation receipts: речь о двух business collections, включённых самим новым `COLLECTIONS` в удаляемый inventory. Committed lesson tombstones защищают более поздний replay, но не закрывают описанную запись между PREPARE и COMMIT.

**Минимальное исправление.** При PREPARE транзакционно установить точное lesson→group/semester mapping/fence из Schedule manifest, включая уроки без Attendance rows; разрешать group для lesson-only writes через этот mapping. Включать связанные markers и pair rows по lesson identity в snapshot/digest/delete, с проверкой отсутствия противоречащего чужого scope. Неизвестный/неразрешимый scope в protected mutation должен отклоняться, а не пропускаться с пустым набором fences.

## Остальные проверенные границы и ограничения

В SOURCEv3 видны необходимые части выбранного протокола: legacy proofless DELETE закрыт 409; initial password confirmation относится к GROUP_DELETE и проверяет текущий ADMIN password/permissions; RPC ограничены directed Academic service identity; immutable operations/participant identity, RELEASE tombstones и retries сохранены. Auth удаляет только grants target group и очищает selected role contexts только связанных с ними sessions; account state, credentials и global roles version код не меняет. Academic profile и его CAS revision сохранены. Schedule manifest охватывает все semesters target group. COMMIT RPC и catalog catch-up выполняются вне локальной транзакции; unavailable catalog не позволяет локальный COMPLETED. Эти наблюдения не устраняют F1–F3 и не являются runtime PASS.

Расширение SQL guards и deferrable FK связано с необходимостью удалить запечатанные business rows через существующие lifecycle constraints. SOURCEv3 сохраняет CASCADE/SET NULL и INITIALLY DEFERRED семантику, меняет RESTRICT на NO ACTION для локального deferred удаления; исходный замеченный ранее сбой семантики FK здесь не повторяется. Изменение infra callback ограничено добавлением `group_deletion_participants` в Schedule allowlist. Broad PUBLIC EXECUTE/новых широких privilege grants в изменении не обнаружено. Однако manifest должен учитывать каскадную замкнутость, как показывает F2; source inspection не доказывает успешное применение динамической migration DDL.

Подготовленные meaningful tests: Academic HTTP IT использует реальные HTTP, Auth и PostgreSQL, но Schedule/Attendance ветки `GroupDeletionParticipants` подменены transport doubles; отдельный Schedule IT вызывает реальный participant с PostgreSQL, отдельный Attendance IT — participant/MongoTemplate aspect с Mongo replica transactions. Это разделённые границы, не полный живой четырёхсервисный путь и не реальная доставка delayed events. Ни один из тестов в этом review не запускался; snapshot фиксирует compileRuns=0, testsRun=0, runtimeRuns=0. Проверка credential/authority revoke в PREPARING, cross-group cascade и реального cancellation path без rows необходима именно для трёх описанных дефектов, а не ради роста test count.

Findings переданы Root и sole writer при подтверждении. После сохранения отчёта reviewer STOP; продуктовых изменений, процессов и ресурсов не создано.
