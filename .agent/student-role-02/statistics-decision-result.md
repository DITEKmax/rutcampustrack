1. **Goal**

Заморозить минимальный контракт статистики студента для пяти финальных состояний Figma. Риск S2: знаменатель, переносы, отсутствующие отметки и идентичность многотипного предмета затрагивают Attendance, Academic и Schedule.

2. **Context/evidence**

**Факты:**

- `docs/architecture/reference-rutcampustrack-design/backend-conflicts.md:127–132` (Р-4): сервер всегда отдаёт четыре метрики и четыре количества.
- Там же `337–354` (Р-25): перенос — одна операция; исходная и новая пары остаются в истории. `358–369` (Р-26): assignment = группа × предмет × тип × семестр × преподаватель. `431–445` (Р-28): единый серверный расчёт, включая число учтённых пар.
- `docs/architecture/reference-rutcampustrack-design/backend-requests.md:263` (AT-18): cancelled исключаются, future дают «нет данных», one-off и transferred участвуют по правилам знаменателя.
- `docs/product/job-stories.md:194` (JS-SYSTEM-07): при закрытии пары отсутствующая отметка должна стать `ABSENT`.
- Более поздний `docs/product/reference-rutcampustrack-design/COMPONENT_REGISTRY.md:41–42` полностью отменяет `free_attendance`, поэтому старый `docs/product/job-stories.md:152` и текущий enum не задают продуктовую семантику.
- Figma: обзор — `.agent/student-role-02/design-context/4603-142.txt:19–84`; варианты 1/2/3 типов — `4798-142.txt:17–111`, `4603-848696.txt:31–165`, `4798-200.txt:32–165`, `4798-285.txt:32–210`. Проценты в макетах не согласуются с цветными слотами, поэтому это иллюстрации, не формульный oracle.
- Текущий `ReportService.getStudentStats` (`ReportService.java:171–233`), `buildOverall` (`291+`) и `buildWeekly` (`323+`) считают только сохранённые записи. `getStudentRecords` (`403+`) и `filterExistingLessons` (`443–451`) не восстанавливают пропущенные строки расписания.
- `Subject.java:14–30` хранит `id,name,type,groupId`; `TeacherSubjectGroup.java:11–30` не содержит типа. `proto/academic.proto:139–142` возвращает один `subject_type`; `proto/schedule.proto:56–69` не содержит типа, assignment или transfer lineage.

**Неопределённости:** текущий Schedule API не позволяет надёжно отличить `transferred-out` от итоговой пары; Academic `GetGroupMembers` использует текущий roster, а историческое членство отдельно не определено. Attendance не должен изобретать оба правила.

3. **Relevant scope**

- Attendance:
  - `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java`
  - `.../attendance/shared/port/AttendanceReadPort.java`
  - `.../attendance/grpc/AcademicGrpcClient.java`
  - `.../attendance/grpc/ScheduleGrpcClient.java`
  - `.../attendance/report/ReportController.java`
  - `services/attendance-service/attendance-api-contract/.../api/ReportApi.java`
  - DTO package `.../contract/dto/report`: заменить семантику `StudentStatsResponse`, `OverallStats`, `WeeklyStat`, `SubjectStats`; добавить detail DTO и общие `MetricSet`, `MetricValue`, `OwnRank`, `PeriodMetric`, `SubjectTypeStats`.
- Academic migration:
  - `Subject.java`, `TeacherSubjectGroup.java`, соответствующие repositories, `SubjectService`, `SubjectAssembler`;
  - `CreateSubjectRequest`, `UpdateSubjectRequest`, `SubjectResponse`, `SubjectType`;
  - `AcademicGrpcServiceImpl.java`, `proto/academic.proto`;
  - новая Flyway migration после `V17`.
- Schedule dependency:
  - `ScheduleItem.java`, `OneOffLesson.java`, `Lesson.java`, assemblers/repositories, `ScheduleGrpcServiceImpl.java`, `proto/schedule.proto`;
  - новая migration после `V14`.
- `proto/*`, generated types и migrations должны иметь одного integration writer.

4. **Required behavior**

Предлагаемый frozen contract:

- Для выбранного scope получить из Schedule логические вхождения активного семестра. `plannedLessons` содержит каждую non-cancelled пару один раз, включая one-off и итоговую точку переноса. `transferred-out` остаётся в исходной schedule history, но исключается из `plannedLessons`, `heldLessons` и статистического status strip.
- `heldLessons` — число non-cancelled логических пар со статусом `CLOSED`. `PLANNED`, `ACTIVE` и future не входят в знаменатель.
- Для каждой `CLOSED` пары: `PRESENT → +`, `ABSENT → н`, `EXCUSED → у`. Если сохранённой отметки нет, статистическая read projection считает `ABSENT` и фиксирует диагностическое нарушение инварианта. Она не пишет Attendance и не делает студента eligible для заявки: request flow по-прежнему требует реально сохранённый `ABSENT`.
- Сохранённый `FREE_ATTENDANCE` — unsupported legacy data. Агрегат завершается типизированной data-integrity ошибкой; статус не складывается с `EXCUSED` и не возвращается клиенту. Его удаление из enum/данных — отдельная миграция.
- При `H = heldLessons`: `present=P/H`, `presentOrExcused=(P+E)/H`, `excused=E/H`, `absent=A/H`; одновременно вернуть соответствующие counts. Инварианты: `P+E+A=H`, combined count=`P+E`. При `H=0` counts равны нулю, percents=`null`. Сервер округляет `BigDecimal` до scale 2, `HALF_UP`; rank сравнивает исходные дроби, не округлённые проценты.
- Semester series плотная, bucket-local, не cumulative. Точка содержит `weekIndex,dateFrom,dateTo,parity,state(DATA|NO_DATA|FUTURE),metrics`; границы и parity берутся из канонической semester/week модели. Для частично будущего интервала ответ также содержит `futureFromDate`.
- Overview: `overall`, `ownRank`, плотная `semesterSeries`, `subjects[]`.
- Subject detail: стабильные `subjectId/name`, `availableTypes`, `selectedTypes`, aggregate и series выбранных типов, а также карточки всех типов. Фильтр меняет график/selected aggregate; карточки остальных типов не исчезают. Status strip типа содержит по одному элементу на статистическое planned-вхождение: три статуса для held и `FUTURE` для ещё не закрытых.
- Типы упорядочены `LECTURE, PRACTICE, LAB`; default — все, пустой selection невалиден.
- Rank строится по точной доле `+`. Participant set — результат Academic `GetGroupMembers(ownGroupId)`, включая самого пользователя и участников без attendance records. Competition rank: `1 + count(strictlyBetter)`, то есть `1,2,2,4`. Участник с `H=0` не сравнивается; для самого пользователя position=`null`, available=`false`. В ответе только `{position,participantCount,available}`, без peer IDs, имён и процентов.
- Stable grouping: один `Subject.id` — group-scoped дисциплина, тип является измерением occurrence/assignment. Нельзя группировать по имени. Academic должен хранить `availableTypes` отдельно и добавить `lessonType` в `TeacherSubjectGroup`.
- Безопасная миграция старых Subjects: каждый существующий row сохраняет свой ID и получает singleton `availableTypes` из прежнего `type`; строки с одинаковым именем автоматически не объединяются. Связям и schedule rows тип переносится из старого Subject до удаления поля.
- Schedule occurrence должен отдавать `subjectId`, immutable `lessonType`, `assignmentId` и transfer lineage либо `logicalOccurrenceId`. Перенос копирует assignment/type; target учитывается один раз. Пока этих полей нет, exact transferred semantics остаётся блокирующей integration boundary.
- Для будущей Attendance day/week projection переиспользовать один pure `AttendanceMetricCalculator`, `SemesterPeriodGrid` и ключ `SubjectTypeKey(subjectId,lessonType)`; фильтры применяются перед калькулятором.

5. **Constraints**

Identity берётся только из trusted `RequestContext`; API не принимает клиентские `userId/groupId`. Расчёт серверный. Report domain использует ports и не импортирует `checkin`. Subject rename сохраняет ID; одинаковые названия остаются разными предметами. Shared proto/generated/migration ownership назначает root.

6. **Existing patterns/dependencies**

`AttendanceReadPort.java:22,32` уже даёт own-user и group/date reads. `LessonEventService.java:80–110` материализует `ABSENT/AUTO_SCHEDULER` при закрытии, поэтому read fallback повторяет инвариант без мутации. `AcademicGrpcServiceImpl.java:101` реализует `GetGroupMembers`; Attendance принимает его roster как канонический. `ReportDomainIsolationTest.java:10–23` закрепляет границу report → ports.

7. **Acceptance criteria**

- Zero denominator возвращает `null`, не `0%`.
- Cancelled и transfer-out не входят в counts; target transfer и one-off учитываются ровно один раз.
- Missing CLOSED даёт `н` только в read projection и не меняет eligibility/хранилище.
- `FREE_ATTENDANCE` вызывает явную unsupported-data ошибку.
- Проверены 1/2/3 типа, выбор subset, карточки всех типов, плотная future region.
- `1/3` округляется детерминированно; rank использует точную дробь и competition ties.
- Участник без records остаётся в `participantCount`; peer fields отсутствуют в сериализации.
- Rename сохраняет grouping; одинаковые имена с разными IDs не сливаются.
- Transfer test требует authoritative lineage и падает/блокируется при его отсутствии.

8. **Verification**

Consultation runtime: N/A, repository не изменялся; baseline `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

Developer checks: focused `ReportServiceTest` и `ReportDomainIsolationTest`; заменить прежние ожидания `0.0` и sparse record-based series (`ReportServiceTest.java:118,140,228,244,395`). Для migration — `SubjectSchemaIT`, `SubjectServiceIT`; затем Academic/Schedule proto compile и межсервисный integration test transfer/multitype. Product runtime нужен после интеграции API/BFF.

9. **Do not**

Не считать на клиенте; не группировать по имени; не оживлять `free_attendance`; не сохранять проекционный `ABSENT`; не использовать его для request eligibility; не считать source+target переноса дважды; не фильтровать rank по наличию records; не отдавать peer PII; не добавлять старый named ranking/export UI или compatibility layer. Полный role PASS этой консультацией не заявляется.
