# Аудит тестов — два списка для решения владельца

**B2 COMPLETE / PROPOSALS_ONLY.** Предлагаю 94 адресных групп на удаление и 169 на переписывание. Это группы методов/cases/assertions, **не количество файлов**, не выполненные изменения и не процент готовности. Продукт, тесты и обязательные gates в рамках аудита не менялись. Реализация предложений ждёт решения владельца.

База аудита и проверенный executable source: `47daac1bb3d48eb2a912689a66d95b938798c284`; [PR #2](https://github.com/DITEKmax/rutcampustrack/pull/2) draft/open/unmerged. Requested `gpt-6.1-sol/ultra`; фактические runtime metadata UNKNOWN. Все ссылки на исходники закреплены на этой revision. 15 непересекающихся scoped read-only обзоров, root-интеграция и отдельная проверка крупных удалений:20 решений/73 crossread source files. Это не designated внешнее APPROVE.

Полностью прочитано966 текущих файлов тестового кода/support/runner/dev-fixtures,306016 строк; hash/range ledger сверён повторно. Методы/parameter declarations учтены статически, suites/mutations ради аудита не запускались. Конкретные контрпримеры ниже — рассуждение по исходникам, если не указано ранее выполненное proof. Архивы и непрочитанные ресурсы отделены в конце. UI copy/layout assertions отличаются от необходимых API/status/authz/privacy/wire contracts.

## Предлагаю удалить

Удаление ограничено названными методами/cases/assertions. Весь файл предлагается удалить только при явном указании и учёте всех его тестов. Указанный сохраняемый тест должен остаться; переписываемый counterpart сохраняет полезный oracle до его переноса. Не удалять только ради зелёного CI.

### Academic: предметы, задания, семестры и доменные правила

#### AD-D01

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentAuthorityContractTest.java:61](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentAuthorityContractTest.java#L61) — метод: createLocksSemesterBeforeOverlapCheckAndInsert; проверяет: ; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentAuthorityContractTest.java:97](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentAuthorityContractTest.java#L97) — метод: overlappingSameTeacherTypeIsRejectedWithoutInsert; проверяет: .

**Сейчас:** Mockito InOrder semester.findByIdForUpdate → findOverlapping → saveAndFlush; prepareAdoption called. A stubbed overlap throws ConflictException and forbids save/saveAndFlush.

**Проблема:** Фиксирует конкретный repository protocol, а не транзакцию. Наличие и реальную сериализацию тех же путей уже проверяют PG/MVC tests; последние два no-save verify сами по себе не доказывают rollback. Перенос overlap enforcement в exclusion constraint либо замена saveAndFlush на эквивалентную persistence-операцию ломает unit при прежних 201/409 и корректных rows.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentAuthorityIT.java:138](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentAuthorityIT.java#L138) — метод: sameTeacherSameTypeOverlapRaceCommitsOneAndReturnsOne409; проверяет: 201+409 and one committed assignment; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentAuthorityIT.java:194](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentAuthorityIT.java#L194) — метод: assignTeacherAndAddTeacherUseOrderedLocks; проверяет: real locked rows, two successful distinct assignments; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterAssignmentAuthorityIT.java:115](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterAssignmentAuthorityIT.java#L115) — метод: semesterDateMutationAndAssignmentCreateRaceIsSerialized; проверяет: only consistent date/edit winner outcomes

**Предложение:** DELETE

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentAuthority.java:73](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentAuthority.java#L73) — symbol: create/createWithLockedSemester; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentAuthority.java:214](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentAuthority.java#L214) — symbol: overlap guard and PREPARED insert

#### AD-D02

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentClosureContractTest.java:51](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentClosureContractTest.java#L51) — метод: deleteAssignmentDelegatesExactKeyAndDateWithoutRowDeletion; проверяет: .

**Граница:** весь указанный файл; все его тесты учтены.

**Сейчас:** close(id,null,null,date,key) exact mock invocation; no repository delete/save.

**Проблема:** Весь файл — forwarding echo. Не проверяет реальную durable closure, key conflict или сохранение истории; surviving HTTP/PG assertions это проверяют. Вынести forwarding в command DTO или другой collaborator без изменения DELETE результата — этот test падает.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentReplacementIT.java:59](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentReplacementIT.java#L59) — метод: standaloneTerminalCloseKeepsHistoryAndAllowsNewSameTeacherAdmission; проверяет: CLOSED row retained, original end retained, changed-key-payload date conflicts; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityIT.java:350](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityIT.java#L350) — метод: closureRoutesReturnTyped409AndLeaveAssignmentHistoryAndOutboxUnchanged; проверяет: exact accepted key/date across standalone and tuple routes; no partial history/outbox change

**Предложение:** DELETE

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentService.java:133](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentService.java#L133) — symbol: removeAssignment

#### AD-D03

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupArchivalServiceTest.java:82](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupArchivalServiceTest.java#L82) — метод: buildArchivedName_composesSuffix; проверяет: .

**Сейчас:** buildArchivedName("УИТ-411",2026) equals the one archived display name.

**Проблема:** Тот же exact input/output уже утверждён через archive(), который вызывает buildArchivedName. Отдельного branch/boundary нет. Inlining/removing the helper breaks only this direct helper test while archive result remains correct.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupArchivalServiceTest.java:41](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupArchivalServiceTest.java#L41) — метод: archive_setsSuffixAndFlagsAndTimestamp; проверяет: same УИТ-411/2026 expected name plus active/timestamp state

**Предложение:** DELETE

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupArchivalService.java:77](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupArchivalService.java#L77) — symbol: archive uses buildArchivedName; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupArchivalService.java:89](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupArchivalService.java#L89) — symbol: buildArchivedName

#### AD-D04

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupPromotionServiceTest.java:442](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupPromotionServiceTest.java#L442) — метод: execute_renameChain_bachelor1to4_resolvesOrderingNoCollision; проверяет: .

**Сейчас:** Four in-memory Group objects get next-course names and the fourth is archived. Repository flush is mocked.

**Проблема:** Заявленный UNIQUE/order collision не наблюдаем: любой порядок mutation пройдёт mock flush. Реальная collision/history проверка существует; pure preview/planning units остаются. Ошибочно выполнять promotions в ascending order: mock unit продолжит PASS; PG chain test обнаружит collision.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupPromotionCycleIT.java:545](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupPromotionCycleIT.java#L545) — метод: massPromotionAdjacentCoursesFlushesInVacatedNameOrderAndRetainsHistory; проверяет: same four-course chain through HTTP, unique DB names/IDs, ARCHIVE ledger and unchanged history

**Предложение:** DELETE

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java:532](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java#L532) — symbol: archive before promotions; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java:555](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java#L555) — symbol: reverse ordered promotions plus flush

#### AD-D05

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupServiceTest.java:532](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupServiceTest.java#L532) — метод: groupEntity_hasNoCodeField; проверяет: .

**Сейчас:** Reflection demands a declared name field and absence of a declared code field.

**Проблема:** Проверяет private representation, не DB mapping и не serialized API. Добавление @Transient internal code/cache или хранение name в embedded value object без изменения поведения запрещено тестом. Add private @Transient String code as an internal cache: all user-visible names and schema remain identical, reflection test fails.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupRegistryIT.java:75](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupRegistryIT.java#L75) — метод: registryCreateCoverageStatusCountsAndLegacyDurationAreServerDerived; проверяет: observable canonical name/code/status with persisted coverage; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupPromotionCycleIT.java:545](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupPromotionCycleIT.java#L545) — метод: massPromotionAdjacentCoursesFlushesInVacatedNameOrderAndRetainsHistory; проверяет: persisted canonical names/identity Absence of one declared Java member provides no external contract guarantee; it neither proves serialized JSON nor database column removal.

**Предложение:** DELETE

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java:36](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java#L36) — symbol: name mapping and split canonical fields

#### AD-D06

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityContractTest.java:101](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityContractTest.java#L101) — метод: nonEmptyLegacyTeacherIdsAreRejectedBeforeAnyWrite; проверяет: ; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityContractTest.java:111](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityContractTest.java#L111) — метод: addTeacherUsesHeadmanGroupAndFullEffectiveTuple; проверяет: ; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityContractTest.java:139](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityContractTest.java#L139) — метод: subjectWithAssignmentHitsClosureFenceWithoutScheduleOrDeleteWrites; проверяет: ; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityContractTest.java:153](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityContractTest.java#L153) — метод: tupleDeletePassesExactPathToDurableClosureWithoutDeletion; проверяет: .

**Сейчас:** Legacy teacherIds refusal/no mock interactions; addTeacher returns preconfigured Assignment for exact tuple; closure fence and no delete/count call; exact tuple close forwarding.

**Проблема:** Четыре mock/wiring cases дублируют проверенный публичный outcome. addTeacher success зависит от точного signature стаба; tuple delete — только forwarding. Единственный create-lock case этого файла НЕ удалять: AD-R01. Introduce a command record/rename a lock helper; HTTP payload/assignment identity stays correct, but exact collaborator stubs/verify fail.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityIT.java:161](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityIT.java#L161) — метод: legacyTeacherIdsAreRejectedAtomically; проверяет: legacy body HTTP400 and zero subjects/assignments; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentAuthorityIT.java:76](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/assignment/AssignmentAuthorityIT.java#L76) — метод: addTeacherPersistsFullIdentityAndRestKeepsNullEffectiveEnd; проверяет: full HTTP and DB teacher/subject/group/semester/type/date tuple; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityIT.java:350](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityIT.java#L350) — метод: closureRoutesReturnTyped409AndLeaveAssignmentHistoryAndOutboxUnchanged; проверяет: referenced subject closure-not-ready, immutable snapshot and cross-route key/date mismatch; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectServiceIT.java:144](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectServiceIT.java#L144) — метод: addTeacher_and_removeTeacher; проверяет: tuple DELETE NoContent, row retained, exact requested cutoff and hidden closed projection

**Предложение:** DELETE

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java:115](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java#L115) — symbol: legacy rejection; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java:273](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java#L273) — symbol: deleteSubject fence; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java:293](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java#L293) — symbol: addTeacher; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java:311](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java#L311) — symbol: removeTeacher

#### AD-D07

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterAssignmentLockContractTest.java:40](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterAssignmentLockContractTest.java#L40) — метод: referencedSemesterCannotChangeDatesAfterRowLock; проверяет: .

**Граница:** весь указанный файл; все его тесты учтены.

**Сейчас:** Sets TransactionSynchronizationManager actual-transaction flag by hand; mocks row read and assignment existence, expects ConflictException wording and no save. InOrder verifies only one invocation.

**Проблема:** Флаг не открывает транзакцию; one-step InOrder не устанавливает порядок относительно existsBySemesterId. Existing actual PG date guard/race tests prove the intended behavior. Replace row-lock repository implementation by equivalent atomic SQL; user outcome remains correct while this protocol test fails.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterAssignmentAuthorityIT.java:47](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterAssignmentAuthorityIT.java#L47) — метод: referencedSemesterDateChangeIsRejectedButNameOnlyUpdateKeepsEligibility; проверяет: HTTP409, original name/date unchanged, eligible name-only request succeeds; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterAssignmentAuthorityIT.java:115](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterAssignmentAuthorityIT.java#L115) — метод: semesterDateMutationAndAssignmentCreateRaceIsSerialized; проверяет: real two-transaction consistent outcomes

**Предложение:** DELETE

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java:149](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java#L149) — symbol: locked updateSemester; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java:193](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java#L193) — symbol: assignment date immutability guard

#### AD-D08

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java:627](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java#L627) — метод: updateHomework_succeeds_forAuthorizedNonAuthor; проверяет: ; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java:645](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java#L645) — метод: deleteHomework_succeeds_forAuthorizedNonAuthor; проверяет: .

**Сейчас:** Mocked repository returns a mutable Homework; expect title/publishedBy or ARCHIVED state, repository save and publisher calls.

**Проблема:** Конструктор unit не устанавливает editCoordinator/publicationPersistence. Production source прямо отмечает эти paths как fixture-only compatibility; тест не доказывает live edit/delete protocol. Non-author risk already covered through current production beans. Delete fixture-only update/archive compatibility branch after all tests use the production coordinator: real HTTP/DB behavior stays correct; these units break.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkEditLifecycleIT.java:203](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkEditLifecycleIT.java#L203) — метод: nonAuthorEditsKeepCompletionReplayHistoryAndDurableUpdatedOutbox; проверяет: non-author actual coordinator path; creator, completion, history, replay and updated outbox retained; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkEditLifecycleIT.java:1187](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkEditLifecycleIT.java#L1187) — метод: nonAuthorAssistantDeletePreservesCreatorHistoryAndActualActorOutboxOnReplay; проверяет: actual publication archive path; creator/completion/history retained and actor outbox append once

**Предложение:** DELETE

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java:925](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java#L925) — symbol: live editCoordinator versus fixture-only fallback; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java:1029](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java#L1029) — symbol: fixture-only archive branch

#### AD-D09

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentServiceTest.java:83](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentServiceTest.java#L83) — метод: completeIsIdempotentAndReturnsTheObservedState; проверяет: ; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentServiceTest.java:101](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentServiceTest.java#L101) — метод: uncompleteIsIdempotentAndDeletesOnlyTheCurrentStudentRow; проверяет: .

**Сейчас:** Completion repository always returns one canned row/timestamp; expects desired-state result plus exact insert/read/delete mock calls. Uncomplete invokes the service only once.

**Проблема:** Mock repository supplies the idempotency being claimed; no unique-key insert or durable current-student scope effect is exercised. Exact repeated DB outcomes are already covered. Implement complete as a single UPSERT RETURNING instead of insert then read; behavior stays idempotent, mock counts fail.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java:291](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java#L291) — метод: repeatedCompleteKeepsTimestampUndoClearsItAndRedoGetsFreshTimestamp; проверяет: stable repeat timestamp, undo null, redo fresh; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java:233](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java#L233) — метод: concurrentCompleteCommandsCreateExactlyOneTargetRowAndPreserveOtherStudent; проверяет: 8 commands one target row plus other student retained; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java:250](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java#L250) — метод: concurrentUncompleteCommandsDeleteExactlyTheTargetRowAndPreserveOtherStudent; проверяет: 8 commands only target deleted

**Предложение:** DELETE

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java:61](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java#L61) — symbol: transactional desired-state command; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java:109](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java#L109) — symbol: insert/read or scoped delete

#### AD-D10

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapAdminServiceTest.java:81](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapAdminServiceTest.java#L81) — метод: firstPngUploadPublishesAnExplicitAbsentSvgSlotAtomically; проверяет: .

**Сейчас:** Mocks floor/current plan/revision/plan/asset/slots and expects result version1, PNG ready/SVG absent plus insertSlot/publishPlan/pointFloorAt calls.

**Проблема:** Атомарность невозможно доказать mock repository. Result is reconstructed from canned findSlots; exact slot/pointer choreography repeats existing real PG upload/readback history evidence. Batch INSERT plan slots or combine publish/pointer SQL: same version/formats/readback but verify choreography fails.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadRepositoryIT.java:949](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadRepositoryIT.java#L949) — метод: oneFormUploadCreatesOrReplacesWithReadbackAndRetainedFormatsAndHistory; проверяет: first version1+SVG absent; retained PNG bytes, replacement version2 and historical readback; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadRepositoryIT.java:1082](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadRepositoryIT.java#L1082) — метод: oneFormFailureAfterCurrentPointerWriteRollsBackNewInventoryAndExistingHistory; проверяет: real transaction rollback after pointer write

**Предложение:** DELETE

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java:258](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java#L258) — symbol: uploadVersion; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java:277](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java#L277) — symbol: publishValidatedVersion

#### AD-D11

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapUsageServiceTest.java:123](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapUsageServiceTest.java#L123) — метод: adminQueriesDelegateToTheSameAggregateRepository; проверяет: .

**Сейчас:** Stubs repository all-time map/total to3; asserts service returns3 and calls those repository methods.

**Проблема:** Тест повторяет две trivial forwarding lines; same chosen value serves as setup and expectation. Никакого SQL aggregation, filtering, privacy or consistency oracle нет. Rename or consolidate aggregate repository methods while preserving returned counts: only mock echo fails.

**Что остаётся:** Self-confirming delegation echo only; no independently specified aggregate/data property or distinct risk is tested.

**Предложение:** DELETE

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageService.java:88](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageService.java#L88) — symbol: allTimeFloorCounts; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageService.java:93](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageService.java#L93) — symbol: allTimeTotal

#### AD-D12

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadServiceTest.java:186](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadServiceTest.java#L186) — метод: suspendedStudentIdentityIsDeniedBeforeMapLookup; проверяет: ; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadServiceTest.java:825](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadServiceTest.java#L825) — метод: readOnlyTerminalClaimIsAllowedWhenArchivedAwareUserExists; проверяет: .

**Сейчас:** Standalone SUSPENDED regular denial and ARCHIVED read-only success with archived-aware User lookup.

**Проблема:** Те же claims and fixtures уже есть в statusCases; matrix additionally verifies no user/map interaction for denial and catalog+user lookup for allowed status. Preserve all10 matrix rows. No semantic rewrite needed: remove two repeated invocations while retaining exact SUSPENDED(false) and ARCHIVED(true) matrix rows.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadServiceTest.java:217](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadServiceTest.java#L217) — метод: signedStudentStatusMatrixAllowsOnlyActiveRegularAndTerminalReadOnly; проверяет: statusCases at963: SUSPENDED regular is denied (row973) and ARCHIVED read-only is allowed (row968); same claims helper, stronger interactions

**Предложение:** DELETE

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapReadService.java:257](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapReadService.java#L257) — symbol: authorize; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapReadService.java:289](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapReadService.java#L289) — symbol: allowedStatus

### Academic: identity, eligibility и authority

#### AA-D01

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/AcademicAssignmentGrpcContractTest.java:31](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/AcademicAssignmentGrpcContractTest.java#L31) — метод: assignmentsByIdsReturnsIdentityAndEffectiveEnd.

**Сейчас:** Прямой вызов gRPC-адаптера возвращает assignment ID и конец null-интервала как semester.dateTo+1.

**Проблема:** Тот же успешный null-end путь и более полный tuple уже проверяет существующий gRPC IT на созданной durable записи; mock repository не добавляет отдельного риска.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/AcademicAssignmentGrpcIT.java:77](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/AcademicAssignmentGrpcIT.java#L77) — метод: assignmentsByIdsReturnRetainedIdentityAndConcreteEffectiveEnd

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java:637](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java#L637)

#### AA-D02

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/AcademicAssignmentGrpcContractTest.java:80](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/AcademicAssignmentGrpcContractTest.java#L80) — метод: teacherSubjectsReturnsOnlyCurrentEffectiveAssignmentsWithConcreteEnd.

**Сейчас:** Выбирается одно текущее назначение, исключаются будущее/завершившееся; наружу идут lecture, validFrom и конкретный конец семестра.

**Проблема:** Это тот же набор дат/типизации и тот же adapter, который существующий IT проверяет с реальной записью плюс отзывом TEACHER grant. Отдельного риска mock setup не добавляет.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/AcademicAssignmentGrpcIT.java:218](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/AcademicAssignmentGrpcIT.java#L218) — метод: teacherSubjectsExcludeFutureAndExpiredAssignmentsAndRequireActiveGrant

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java:527](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java#L527)

#### AA-D03

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentProjectionGrpcServiceTest.java:252](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentProjectionGrpcServiceTest.java#L252) — метод: pastClosedHistoryDivergenceMapsToUnavailableWithInconsistentSource.

**Сейчас:** StudentProjectionException.inconsistent(message) отображается в UNAVAILABLE и INCONSISTENT_SOURCE.

**Проблема:** Историю этот тест не исполняет: resolver mocked и бросает ту же фабрику исключения, что соседний тест; различается только невыводимый message. Существующий сосед дополнительно проверяет ровно один detail.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentProjectionGrpcServiceTest.java:208](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentProjectionGrpcServiceTest.java#L208) — метод: inconsistentAuthorityMapsToUnavailableWithStableCode; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeServiceTest.java:259](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeServiceTest.java#L259) — метод: pastUntransferredClosedHistoryIsInconsistentBeforeRoster

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionException.java:61](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionException.java#L61); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionGrpcErrors.java:19](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionGrpcErrors.java#L19); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionGrpcErrors.java:67](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionGrpcErrors.java#L67)

#### AA-D04

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityRecoveryArchiveGuardTest.java:34](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityRecoveryArchiveGuardTest.java#L34) — метод: foreignGuardAppearingDuringUnchangedAggregateStillDeniesAdmission.

**Сейчас:** Синтетический second query=true после unchanged aggregate приводит к UNAVAILABLE.

**Проблема:** Существующий RC/PG IT устанавливает реальный чужой AUTH_PREPARED guard во время actual aggregate, доказывает unchanged digest и нераскрытие. Mock false,true и inOrder повторяют этот риск без DB visibility.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityRecoveryArchiveGuardIT.java:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityRecoveryArchiveGuardIT.java#L37) — метод: actualForeignPreparedGuardWinsAtRcFinalReadEvenWhenArchiveDigestDoesNotChange

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/JdbcAcademicIdentityOperationStore.java:1075](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/JdbcAcademicIdentityOperationStore.java#L1075)

#### AA-D05

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java:89](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java#L89) — метод: listUsers_withSearch_delegatesToSpecificationFindAll; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java:109](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java#L109) — метод: listUsers_allParamsNull_stillCallsSpecificationFindAll; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java:122](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java#L122) — метод: listUsers_legacyOverload_delegatesToFullListUsers.

**Сейчас:** Соответственно: возвращается тот же Page из stub и передан non-null Specification; null-параметры выбирают findAll(Specification,Pageable); legacy overload выбирает тот же repository overload.

**Проблема:** Ни один тест не исполняет predicate и не проверяет search/role/status/page результат. Удаление любого фильтра сохраняет PASS. Поведенчески эквивалентный специализированный query или findAll(Pageable) для пустого фильтра ломает проверки. Полезная самостоятельная гарантия отсутствует; речь только об этих трех методах.

**Что остаётся:** reason: Нет полезной самостоятельной гарантии результата; не заявляется точная эквивалентность NULL/legacy поведения соседним IT.; relatedRetainedEvidence: [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserSearchIT.java:255](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserSearchIT.java#L255) — метод: search_caseInsensitive_filtersByLastName; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserSearchIT.java:279](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserSearchIT.java#L279) — метод: search_blank_returnsAllUsers; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserRepositorySearchIT.java:171](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserRepositorySearchIT.java#L171) — метод: matchesSearch_combinedWithRoleViaAnd

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java:246](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java#L246); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java:323](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java#L323)

### Academic: endpoint/DB/архитектурные контракты

#### AI-D01 — Удалить весь OpenApiErrorResponsesIT: три проверки substring слабее существующего структурного oracle

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/OpenApiErrorResponsesIT.java:23](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/OpenApiErrorResponsesIT.java#L23) — метод: apiDocsContainsErrorResponseSchema; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/OpenApiErrorResponsesIT.java:38](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/OpenApiErrorResponsesIT.java#L38) — метод: apiDocsContainsAllSevenStandardErrors; cases: 7 status strings; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/OpenApiErrorResponsesIT.java:53](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/OpenApiErrorResponsesIT.java#L53) — метод: apiDocsUsesProblemJsonMediaType.

**Сейчас:** Три реальных GET /api-docs; ищут ErrorResponse/traceId/fieldErrors, семь status strings и media/ref в произвольных местах всего JSON.

**Проблема:** Не устанавливают связь status→operation→media→schema; контент любого одного endpoint удовлетворяет каждый поиск. Уже есть сравнение всего parsed JSON с committed baseline. Учитываются ВСЕ 3 теста; support-only helper нет. Удалить403 только из DELETE /academic/users/{id}: substring suite останется зелёным из-за других операций; apiDocsMatchesSnapshot отвергнет изменение конкретного узла.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/OpenApiSnapshotIT.java:47](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/OpenApiSnapshotIT.java#L47) — метод: apiDocsMatchesSnapshot; cases: parsed equality after removing servers; [services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizerTest.java:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizerTest.java#L37) — метод: addsAllSevenErrorResponsesWhenEmpty; [services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizerTest.java:52](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizerTest.java#L52) — метод: addedResponsesHaveProblemJsonContent; [services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizerTest.java:144](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizerTest.java#L144) — метод: appliesToAllPathsAndMethods

**Предложение:** Удалить только этот61-line test file после сохранения OpenApiSnapshotIT и shared customizer unit. Snapshot/API/schema/security/error behavior не удалять; никаких CI exclusions.

**Проверенный контракт:** [services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizer.java:110](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizer.java#L110) — метод: applyToAllOperations; [services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizer.java:146](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizer.java#L146) — метод: mergeErrorResponse

#### AI-D02 — Удалить только contextLoads/null-bean assertions, сохранить остальные ORM/DB cases

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EntityMappingIT.java:59](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EntityMappingIT.java#L59) — метод: contextLoads_allEntitiesValidateAgainstSchema.

**Сейчас:** Проверяет десять @Autowired repositories на non-null после запуска контекста.

**Проблема:** Обязательная injection и Hibernate validate происходят до любого метода этого SpringBootTest. Удаление метода не удаляет поля injection или шесть остальных методов класса. Переименовать колонку так, что Hibernate validate не проходит: сохранённый AcademicGrpcIT и любой другой тест той же Spring-конфигурации не стартует. Десять null assertions не добавят отдельного сигнала.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicGrpcIT.java:783](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicGrpcIT.java#L783) — метод: getUserById_validId_returnsUserInfo; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicReadBatchIT.java:216](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicReadBatchIT.java#L216) — метод: assignmentLifecycleRoundTripsJpaAndNativeQueriesUsingExistingTextCodes

**Предложение:** Удалить один метод и @Order(1), не весь EntityMappingIT. Не удалять archived restriction, PG array, unique-active-semester или credential allocation proofs.

**Проверенный контракт:** [services/academic-service/academic-app/src/test/resources/application-test.yml:4](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/resources/application-test.yml#L4); [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicIntegrationTest.java:15](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AbstractAcademicIntegrationTest.java#L15); [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicIdentityIntegrationFixture.java:469](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicIdentityIntegrationFixture.java#L469) — метод: properties

#### AI-D03 — Удалить только 50 случайных повторов blank-middle-name: все три семантических входа уже deterministic golden

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/golden/DisplayNamePropertyTest.java:62](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/golden/DisplayNamePropertyTest.java#L62) — метод: noMiddleNameIfBlank; cases: @RepeatedTest(50): random choice null / empty / three spaces.

**Сейчас:** Анна/Петрова + случайный null/empty/spaces middleName дают exact ФИО без trailing space.

**Проблема:** Повторяет три ветви golden fixture без расширения входного пространства. Случайность не гарантирует каждую ветвь отдельного запуска. Само правило формата ФИО полезно; его сохраняет golden. Всегда добавлять пробел для null/empty/blank middleName: deterministic golden rows2–4 сразу падают независимо от random choice.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/golden/DisplayNameGoldenTest.java:55](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/golden/DisplayNameGoldenTest.java#L55) — метод: computesExpectedDisplayName; cases: resource rows2(null),3(empty),4(spaces); [services/academic-service/academic-app/src/test/resources/golden/display-name.json:10](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/resources/golden/display-name.json#L10) — метод: null/empty/whitespace oracle rows

**Предложение:** Удалить один repeated method; сохранить весь DisplayNameGoldenTest, все12 semantic rows, property lastNameAlwaysFirst и остальные дешёвые unit proofs. Не заменять формат ФИО на проверку UI wording.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/User.java:48](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/User.java#L48) — метод: getDisplayName

#### AI-D04 — Удалить dormant repository guard при сохранении действующего no-JPA-relations инварианта

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/arch/RepositoryNPlusOneGuardTest.java:49](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/arch/RepositoryNPlusOneGuardTest.java#L49) — метод: repositoriesReturningCollectionsMustGuardNPlusOne.

**Сейчас:** Смотрит Collection return type, наличие relation entity в пакете repository owner, затем наличие Pageable/annotation/string join.

**Проблема:** Первое правило того же класса запрещает ВСЕ такие JPA relation fields. При принятом текущем инварианте второе не добавляет состояния отказа. Дополнительно сканирует package repository, а domain entity живут в другом package; scalar-FK loop N+1 оно не ловит. Добавление @ManyToOne в entity уже отвергнет entitiesMustNotUseJpaRelations; добавление findById loop без relations не отвергнет второе правило. Наличие annotation не доказывает реальный SQL budget.

**Что остаётся:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/arch/RepositoryNPlusOneGuardTest.java:39](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/arch/RepositoryNPlusOneGuardTest.java#L39) — метод: entitiesMustNotUseJpaRelations; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicReadBatchIT.java:68](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicReadBatchIT.java#L68) — метод: subjectPageUsesTwoEnrichmentQueriesAndPreservesOrderScopeAndPublication; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicReadBatchIT.java:124](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicReadBatchIT.java#L124) — метод: homeworkPageUsesOneCompletionQueryForCurrentActorIncludingEmptyPage

**Предложение:** Удалить только второй @ArchTest и его exclusively-used helpers beGuardedIfReturnsCollectionAndElementHasRelations/anyEntityHasRelations/isGuarded; оставить hasAnyRelation и первый guard. Если владелец отдельно разрешит relations, сначала создать настоящий guard/SQL proof для новой архитектуры. Schedule counterpart не дублирует Academic scoping и не является основанием удалить первый guard.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/User.java:18](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/User.java#L18); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/AssignmentRepository.java:14](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/AssignmentRepository.java#L14); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectAssembler.java:46](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectAssembler.java#L46) — метод: toResponsePage

### Attendance: запись, события и операции

#### AO-D01

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonGenerationMergeTest.java:44](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonGenerationMergeTest.java#L44) — метод: scheduleGrpcClient_getLessonById_returnsLessonResponseShape.

**Граница:** весь указанный файл; все его тесты учтены.

**Сейчас:** Настраивает mock ScheduleGrpcClient.getLessonById(42), вызывает этот же mock и сравнивает пять полей заранее заданного protobuf-ответа; LessonEventService не вызывается.

**Проблема:** Проверка останется зелёной, если реальный ScheduleGrpcClient вернёт другое занятие или из production-кода исчезнет обработка разового занятия: ни один production-метод здесь не выполняется.

**Что остаётся:** Уникальной production-гарантии нет: вызывается только сам mock; замещающий тест для этого метода не требуется.

**Предложение:** DELETE всего services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonGenerationMergeTest.java, включая scheduleGrpcClient_getLessonById_returnsLessonResponseShape и неиспользуемую фикстуру @InjectMocks. Сохраняемый эквивалент не требуется: production-гарантия этим тестом не проверяется.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/ScheduleGrpcClient.java:58](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/ScheduleGrpcClient.java#L58); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java:103](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java#L103)

#### AO-D03

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/RabbitConsumerIT.java:39](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/RabbitConsumerIT.java#L39) — метод: publishToFanoutExchange_doesNotThrow.

**Сейчас:** Вызывает RabbitTemplate.convertAndSend без проверки получения сообщения consumer-ом, привязки очереди, confirms, ACK или DLQ.

**Проблема:** Публикация в exchange без attendance-привязки тоже может завершиться без исключения; текущая проверка этого не заметит. Сохраняемый тест требует фактического получения события и трёх записей в Mongo.

**Что остаётся:** location: [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java:177](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java#L177) — метод: lessonClosed_noExistingRecords_createsAbsentForAllStudents; assertions: publishEvent137-138 sends to the same rut-uit.events exchange; await187-204 requires three real Mongo rows with ABSENT/AUTO_SCHEDULER and expected group, subject, semester, slot/date.

**Предложение:** DELETE только publishToFanoutExchange_doesNotThrow. Сохранить services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java::lessonClosed_noExistingRecords_createsAbsentForAllStudents: публикация в тот же rut-uit.events должна создать три реальные записи ABSENT/AUTO_SCHEDULER с ожидаемыми group/subject/semester/slot/date. Две самостоятельные проверки существования именованных очередей оставить.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java:63](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java#L63)

#### AO-D04

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/marking/MarkingServiceTest.java:354](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/marking/MarkingServiceTest.java#L354) — метод: markAttendance_secondMark_upsertsNotDuplicates.

**Сейчас:** Подставляет два готовых результата findOne с одинаковым fixture-id, проверяет два вызова upsert и сравнивает возвращённые тестом объекты.

**Проблема:** Неверный фильтр студента или дубли в реальном хранилище не меняют подставленные id/status. При этом эквивалентная замена upsert на атомарный findAndModify ломает проверку вызовов.

**Что остаётся:** location: [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MarkingIT.java:267](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MarkingIT.java#L267) — метод: mark_secondMark_updatesStatusNotDuplicate; assertions: Two HTTP marks PRESENT then ABSENT270-276; real Mongo size1 and final ABSENT279-281.; location: [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MarkingIT.java:324](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MarkingIT.java#L324) — метод: mark_secondMark_preservesImmutableFields; assertions: Reads original physical \_id330-339, issues second mark342, checks same physical \_id354 and group/subject358-359.

**Предложение:** DELETE только markAttendance_secondMark_upsertsNotDuplicates. Сохранить services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MarkingIT.java::mark_secondMark_updatesStatusNotDuplicate, где два HTTP-запроса оставляют одну реальную запись с итоговым ABSENT, и services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MarkingIT.java::mark_secondMark_preservesImmutableFields, где сохраняются физический \_id, группа и предмет. Уникальные clock/authz/CAS/batch-проверки не удалять.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java:231](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java#L231); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java:274](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java#L274)

#### AO-D05

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonEventServiceTest.java:85](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonEventServiceTest.java#L85) — метод: processLessonClosed_callsBulkUpsertForEachStudent.

**Сейчас:** Для трёх участников исторического состава проверяет только три вызова bulk.upsert(any, any) и один execute на mock Mongo.

**Проблема:** Ошибочные фильтры или статусы проходят any-проверки; эквивалентная пакетная запись через другой API меняет число вызовов и даёт ложное падение при том же результате Mongo.

**Что остаётся:** location: [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java:177](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java#L177) — метод: lessonClosed_noExistingRecords_createsAbsentForAllStudents; assertions: Same three-member dated roster fixture141-169 and three actual stored ABSENT/AUTO_SCHEDULER rows192-203 with canonical scope.

**Предложение:** DELETE только processLessonClosed_callsBulkUpsertForEachStudent. Сохранить services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java::lessonClosed_noExistingRecords_createsAbsentForAllStudents: тот же датированный состав из трёх студентов даёт три реальные записи ABSENT/AUTO_SCHEDULER с каноническими полями; также сохранить services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java::lateInitialMembershipBackfillsOnlyDatedTargetAfterCommittedCloseAndReplaysAfterSeal. Оставить проверки отказа при отсутствующем семестре/зависимости и отдельные проверки отмены; не использовать AO-D06 как взаимное обоснование удаления.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java:252](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java#L252)

#### AO-D06

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonEventServiceParallelTest.java:47](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonEventServiceParallelTest.java#L47) — метод: processLessonClosed_usesCanonicalDatedRoster.

**Граница:** весь указанный файл; все его тесты учтены.

**Сейчас:** Единственный тест вызывает LessonEventService с mock Mongo и проверяет аргументы датированного запроса состава; SyncTaskExecutor не создаёт параллельного исполнения.

**Проблема:** Выделение или переименование адаптера состава без изменения исторических записей ломает прямую проверку collaborator-а. Нового наблюдаемого результата или проверки параллелизма файл не добавляет; неверный датированный состав проверяется сохраняемыми интеграционными сценариями.

**Что остаётся:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java:177](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java#L177) — метод: lessonClosed_noExistingRecords_createsAbsentForAllStudents; гарантия: Настоящая доставка и три Mongo ABSENT rows с canonical dated scope.; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java:736](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java#L736) — метод: lateInitialMembershipBackfillsOnlyDatedTargetAfterCommittedCloseAndReplaysAfterSeal; гарантия: Нестандартный semester, dated roster, committed close и APPLIED receipt.; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonEventServiceTest.java:135](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonEventServiceTest.java#L135) — метод: processLessonClosed_emptyGroup_skipsWithoutBulkOps; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonEventServiceTest.java:166](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonEventServiceTest.java#L166) — метод: processLessonClosed_missingSemesterId_failsClosed

**Предложение:** DELETE всего services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonEventServiceParallelTest.java с processLessonClosed_usesCanonicalDatedRoster. Сохранить services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java::lessonClosed_noExistingRecords_createsAbsentForAllStudents и services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java::lateInitialMembershipBackfillsOnlyDatedTargetAfterCommittedCloseAndReplaysAfterSeal (:735–787) как независимое подтверждение датированного состава и фактического семестра, отличного от значения по умолчанию. Сохранить services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/LessonEventServiceTest.java::processLessonClosed_emptyGroup_skipsWithoutBulkOps (:135) и guard отсутствующего семестра (:166). Диапазон :85–128 того же файла — исходное статическое подтверждение, а не обязательный survivor: метод отдельно предложен к удалению в AO-D05. Не обосновывать оба удаления только друг другом.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java:110](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java#L110)

### Attendance/Requests: отчёты, отметки и обращения

#### AR-D01 — Удалить только копирующий подстановку sample method; inventory сохранить

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyTemplateSmokeTest.java:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyTemplateSmokeTest.java#L37) — метод: sampleDataFillsAllPlaceholdersAndKeepsXmlValid.

**Сейчас:** Метод сам выполняет replace/escapeXml над template, затем проверяет полученный собственным алгоритмом XML; production renderer не вызывается.

**Проблема:** Ошибка в production replacement/escaping не наблюдается. Безопасный рефакторинг имён placeholder затронет этот дублирующий алгоритм. Первый метод файла НЕ предлагается удалить: он пока единственный проверяет полноту 6 дат и 30 типов. Удаление production escaping не ломает sample, который использует собственный escapeXml. Это статический контрпример, не выполненная mutation.

**Что остаётся:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/DocxRendererTest.java:29](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/DocxRendererTest.java#L29) — метод: renderSingleWeekReplacesEveryPlaceholder; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/DocxRendererTest.java:74](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/DocxRendererTest.java#L74) — метод: renderFullSemesterPreservesEveryWeekRowAndTemplateResource У sample нет уникальной production-гарантии. DocxRendererTest проверяет настоящий renderer, но НЕ объявляется полной заменой inventory: для него AR-R11 ниже. Остальная часть файла и helper для inventory сохраняются.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/DocxRenderer.java:40](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/DocxRenderer.java#L40) — метод: render; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/DocxRenderer.java:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/DocxRenderer.java#L50) — метод: renderDocumentXml

#### AR-D02 — Удалить дублирующий MVC-only PDF happy path

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/ReportControllerMvcTest.java:150](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/ReportControllerMvcTest.java#L150) — метод: exportCurrentPdf_returnsPdfContentType; cases: weekStart=2026-04-27,format=pdf.

**Сейчас:** Mock service возвращает два заданных byte; проверяются200,application/pdf,расширение Content-Disposition и те же bytes.

**Проблема:** Все полезные assertions уже выполняет более полный существующий тест того же endpoint и controller method с настоящим сервисом/данными. Внутренняя переработка controller→service seam требует обновлять второй комплект mock setup, не добавляя наблюдаемую гарантию PDF response.

**Что остаётся:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportControllerIT.java:279](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportControllerIT.java#L279) — метод: exportCurrentPdf_convertsSingleWeekDocx Сохраняющий тест проходит Spring MockMvc→настоящий weekly service→Mongo lookup→настоящий DOCX renderer→mock converter и проверяет тот же200/MIME/.pdf/bytes. Это сильнее slice по данному риску, но не socket HTTP и не реальная PDF conversion; так и учитывать.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportController.java:183](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportController.java#L183) — метод: exportHeadmanWeeklyCurrent

#### AR-D03 — Удалить отдельную alias equality проверку publisher

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisherTest.java:263](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisherTest.java#L263) — метод: publishRequested_userIdEqualsStudentId; cases: student42,OTHER.

**Сейчас:** Один requested JSON payload сравнивает user_id со student_id.

**Проблема:** Тот же инвариант уже проверяется сильнее: оба wire поля равны независимому ожидаемому student100 в полном publisher test и реальном outbox/Rabbit contract. Дополнительный OTHER не открывает отдельную alias ветку: buildRequestedPayload кладёт оба поля прямо из ticket.getStudentId() до enum mapping.

**Что остаётся:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisherTest.java:55](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisherTest.java#L55) — метод: publishRequested_sendsCorrectEnvelopeToOutbox; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventContractIT.java:128](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventContractIT.java#L128) — метод: createExcuse_publishesRequestedEvent_matchingBotContract

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisher.java:129](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisher.java#L129) — метод: buildRequestedPayload; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisher.java:133](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisher.java#L133)

#### AR-D04 — Удалить два дублирующих pending-list unit метода

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinServiceTest.java:225](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinServiceTest.java#L225) — метод: listPendingForHeadman_happyPath_returnsGroupRequests; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinServiceTest.java:243](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinServiceTest.java#L243) — метод: listPendingForHeadman_notHeadman_throwsAccessDenied.

**Сейчас:** Первый возвращает canned repository list для exact derived query; второй проверяет AccessDeniedException при false headman/default missing assistant grant.

**Проблема:** Реальная Mongo endpoint-проверка вызывает именно этот service method и проверяет own-group row против foreign-group row; отдельный endpoint case проверяет403. Замена derived repository метода на equivalent Mongo aggregation ломает stub happy path, хотя endpoint сохраняет правильные данные.

**Что остаётся:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinControllerIT.java:147](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinControllerIT.java#L147) — метод: listPending_asHeadman_returnsOnlyGroupPending; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinControllerIT.java:176](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinControllerIT.java#L176) — метод: listPending_asNonHeadman_returns403 Не удалять null-group case line251, точный30day cutoff, assistant/actor guards или decision paths. Legacy decision route отличается от canonical HTTP adapter и не объявляется дублем.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinController.java:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinController.java#L50) — метод: listPending; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinService.java:196](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinService.java#L196) — метод: listPendingForHeadman

### Auth/Gateway: сессии, admission и transport

#### AG-D01

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthIT.java:79](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthIT.java#L79) — метод: login_setsRefreshCookie_withStrictAttributes.

**Сейчас:** POST /auth/login student/password: HTTP 200 и refresh-cookie HttpOnly,Secure,SameSite=Strict,Path=/api/auth; Max-Age=604800.

**Проблема:** Полный дубль тех же входа, endpoint, cookie и пяти проверок; второй метод не добавляет отдельного состояния или риска.

**Что остаётся:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SameSiteCookieContractIT.java:48](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SameSiteCookieContractIT.java#L48) — метод: loginCookie_carriesFullAttributeSet

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java:43](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java#L43); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/security/AuthCookies.java:18](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/security/AuthCookies.java#L18)

#### AG-D02

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthIT.java:141](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthIT.java#L141) — метод: refresh_withoutCookie_returns401.

**Сейчас:** POST /auth/refresh без Cookie и без body возвращает 401.

**Проблема:** Точно тот же HTTP-вход и статус уже проверяются surviving методом.

**Что остаётся:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SameSiteCookieContractIT.java:70](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SameSiteCookieContractIT.java#L70) — метод: refresh_withoutCookie_returns401

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java:51](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java#L51)

#### AG-D03

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SameSiteCookieContractIT.java:80](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SameSiteCookieContractIT.java#L80) — метод: refresh_withCookie_withoutCsrfHeader_returns200.

**Сейчас:** Login student/password, затем POST /auth/refresh только с Cookie, без X-CSRF-TOKEN: HTTP 200, accessToken непустой.

**Проблема:** Полный полезный риск отсутствие CSRF-header покрыт surviving запросом, который также не задаёт этот header и проверяет новый refresh token/cookie.

**Что остаётся:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthIT.java:102](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthIT.java#L102) — метод: refresh_viaCookie_returnsNewTokenPair_andRotatesCookie

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java:40](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java#L40); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java:51](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java#L51)

#### AG-D04

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OtpIT.java:119](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OtpIT.java#L119) — метод: otpRequest_withValidTelegramId_returns204NoBody.

**Сейчас:** POST /auth/otp/request того же 123456789:204/null body и Redis otp:<id> непустой длины 6.

**Проблема:** Полный полезный результат уже проверяется surviving HTTP+Redis+Rabbit сценарием с тем же AbstractIntegrationTest и SQL binding; там ещё проверено совпадение delivered code.

**Что остаётся:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthOtpFlowIT.java:182](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/AuthOtpFlowIT.java#L182) — метод: requestOtp_204Body_redisHasCode_rabbitReceivesEvent

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java:64](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java#L64); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java:161](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java#L161)

#### AG-D05

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/WsTicketIT.java:199](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/WsTicketIT.java#L199) — метод: consumeTicket_twice_secondReturns404.

**Сейчас:** После успешного consume valid issued ticket повторный POST /internal/consume-ws-ticket получает 404.

**Проблема:** Surviving real HTTP race имеет ровно один 200, один 404 и ещё один последовательный 404 после завершения победителя. В удаляемом методе нет отдельной identity/session проверки.

**Что остаётся:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/WsTicketConcurrencyBoundaryIT.java:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/WsTicketConcurrencyBoundaryIT.java#L37) — метод: concurrentActualHttpConsumersHaveExactlyOneWinnerAndBurnTicket

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/WsTicketService.java:125](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/WsTicketService.java#L125)

#### AG-D06

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionLifecycleServiceTest.java:101](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionLifecycleServiceTest.java#L101) — метод: roleSwitchIsAtomicIdempotentAndVersionBound.

**Сейчас:** Обёртка передаёт role request fake-store, который сам выбирает teacher, увеличивает SV и возвращает conflict для staleSV.

**Проблема:** Убрать реальный JDBC CAS/event idempotency: тест останется зелёным, потому что реализация находится в AtomicSessionDouble.selectRole833..865. Производственный сервис 96..114 только формирует port command.

**Что остаётся:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java:303](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java#L303) — метод: roleSelectionIsOwnVersionedAndSameRoleIsIdempotentWithoutEvent; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SessionAuthFlowIT.java:118](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SessionAuthFlowIT.java#L118) — метод: passwordLoginSelectsStudentAndRoleSwitchIssuesTeacherBoundAccess

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionLifecycleService.java:96](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionLifecycleService.java#L96); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java:265](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java#L265)

#### AG-D07

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionLifecycleServiceTest.java:353](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionLifecycleServiceTest.java#L353) — метод: currentLogoutIsDurableAndIdempotent.

**Сейчас:** Fake-store помечает session revoked, возвращает alreadyRevoked при retry и не добавляет второй logout event.

**Проблема:** Тестовая реализация revokeCurrent898..919 сама содержит idempotency. Поломка реального durable revoke/event commit теста не касается.

**Что остаётся:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java:927](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java#L927) — метод: revokeCurrentIsDurableIdempotentAndDeniesLaterSnapshot; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SessionAuthFlowIT.java:101](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SessionAuthFlowIT.java#L101) — метод: bootstrapBearerOnlyLogoutAndLogoutAllAreAllowedByTheExactScope

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionLifecycleService.java:128](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionLifecycleService.java#L128); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java:582](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java#L582)

#### AG-D08

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionLifecycleServiceTest.java:40](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/SessionLifecycleServiceTest.java#L40) — метод: createRejectsCallerSnapshotWhenAuthoritativeVersionOrGrantsChanged.

**Сейчас:** Fake rolesVersion/grants против caller snapshot → stale и отсутствие fake session/events.

**Проблема:** Версию и grant equality проверяет сам AtomicSessionDouble.createSession792..816. Production service не читает authoritative версию; поломка реального compare-and-check остаётся зелёной.

**Что остаётся:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java:219](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java#L219) — метод: staleAuthoritativeGrantVersionRejectsSessionAndLoginEvent; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java:172](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java#L172) — метод: foreignSessionAndGrantHintsCannotCrossOwner

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionLifecycleService.java:42](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionLifecycleService.java#L42); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java:247](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java#L247)

#### AG-D09

**Где:** [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/CompositeLoginKeyResolverIT.java:106](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/CompositeLoginKeyResolverIT.java#L106) — метод: compositeKeyIsolatesByIp; [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/CompositeLoginKeyResolverIT.java:139](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/CompositeLoginKeyResolverIT.java#L139) — метод: compositeIsolatesByLogin_sameIp_KI8; [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/CompositeLoginKeyResolverIT.java:174](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/CompositeLoginKeyResolverIT.java#L174) — метод: differentLogins_sameIp_bothLimitedByIp.

**Сейчас:** Class @Disabled37..41; ни один из 3 методов не даёт текущей выполняемой гарантии. Первые 2 отправляют 5+1, не исчерпывают composite 10 и второй меняет одновременно login/IP.

**Проблема:** Текущий login route имеет только ipKeyResolver; composite 10 отсутствует в application.yml194..232. Даже гипотетический запуск первых 2 не различит отсутствующий composite. Третий заявляет общую IP bucket, но class никогда не выполняется и не имеет trusted-edge fixture текущей политики.

**Что остаётся:** [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RedisRateLimiterConfigTest.java:109](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RedisRateLimiterConfigTest.java#L109) — метод: ipLoginResolver_compositeKey; [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RedisRateLimiterConfigTest.java:124](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RedisRateLimiterConfigTest.java#L124) — метод: ipLoginResolver_differentIpsNotCollapsed; [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RateLimitIT.java:199](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RateLimitIT.java#L199) — метод: sixthRequest_returns429; [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RateLimitIT.java:243](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RateLimitIT.java#L243) — метод: differentIps_separateBuckets

**Проверенный контракт:** [services/api-gateway/src/main/resources/application.yml:194](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/main/resources/application.yml#L194); [services/api-gateway/src/main/java/ru/rutcampustrack/gateway/ratelimit/RedisRateLimiterConfig.java:57](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/main/java/ru/rutcampustrack/gateway/ratelimit/RedisRateLimiterConfig.java#L57)

#### AG-D10

**Где:** [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java:467](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java#L467) — метод: fractionalDateFixturePreservesNonzeroFractionAtWallSecondBoundary; cases: iat/0; iat/1; iat/999; exp/0; exp/1; exp/999.

**Сейчас:** Шесть fixture случаев: iat/exp × millis0/1/999. Raw signed JWT с fractional time получает 401 и не достигает chain; дополнительная assertion проверяет fraction fixture.

**Проблема:** fractionalNumericDate587 всегда берёт getEpochSecond()+0.125, поэтому millis не влияют на токен. Для каждого поля все три cases повторяют raw payload существующего non-whole issued-at/non-whole expiration case из MethodSource. Если helper начнёт отдавать integral date, эти surviving negative cases уже упадут, поскольку baseline иначе валиден; whole-second positive control также сохранён.

**Что остаётся:** [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java:461](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java#L461) — метод: signedInvalidAccessClaims_areRejected; cases: non-whole issued-at (provider540..543); non-whole expiration (provider549..552); [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java:488](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java#L488) — метод: legacyWholeWallSecondFixtureSerializesAsIntegralAndIsCorrectlyForwarded; cases: iat; exp

**Проверенный контракт:** [services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java:229](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java#L229)

#### AG-D11

**Где:** [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java:453](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java#L453) — метод: invalidRole_returns401.

**Сейчас:** validAccessClaims с единственной заменой role=OWNER → assertUnauthorized(signedRawToken),401 и zero chain calls.

**Проблема:** MethodSource unknown role571 делает ту же замену OWNER в том же validAccessClaims и вызывает тот же signedRawToken/assertUnauthorized. Отдельных wire/state/risk assertions нет.

**Что остаётся:** [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java:461](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilterTest.java#L461) — метод: signedInvalidAccessClaims_areRejected; cases: unknown role (provider571)

**Проверенный контракт:** [services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java:210](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java#L210)

### Shared/Notification/BFF/renderer

#### BS-D01

**Где:** [services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/csp/CspReportIT.java:168](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/csp/CspReportIT.java#L168).

**Сейчас:** POST /csp-report без JWT/legacy headers; проверяется только 204.

**Проблема:** Возврат 204 без parsing тоже удовлетворяет этому методу; отдельного oracle для default-src нет.

**Что остаётся:** [services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/csp/CspReportIT.java:83](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/csp/CspReportIT.java#L83) — метод: legacyFormat; coverage: Тот же no-JWT маршрут/status плюс counter/tags.; [services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/csp/CspReportIT.java:110](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/csp/CspReportIT.java#L110) — метод: modernReportsApi; coverage: No-JWT modern report batch.

**Предложение:** Удалить только noAuthRequired. Существующий legacyFormat выполняет тот же unauthenticated application/csp-report POST и проверяет 204, прирост counter и теги. modernReportsApi дополнительно сохраняет публичный modern MIME route.

**Cases/граница:** название: noAuthRequired; строка: 168

**Проверенный контракт:** [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/security/NotificationUserContextFilter.java:73](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/security/NotificationUserContextFilter.java#L73); [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/csp/CspReportController.java:92](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/csp/CspReportController.java#L92)

#### BS-D02

**Где:** [services/shared/shared-events/src/test/java/ru/rutcampustrack/shared/events/IdempotencyGuardTest.java:35](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-events/src/test/java/ru/rutcampustrack/shared/events/IdempotencyGuardTest.java#L35).

**Сейчас:** Свежий InMemoryStore, UUID object в lesson.started envelope, первый claim academic == true.

**Проблема:** Метод не добавляет состояние или assertion к первой половине retained duplicateCall.

**Что остаётся:** [services/shared/shared-events/src/test/java/ru/rutcampustrack/shared/events/IdempotencyGuardTest.java:43](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-events/src/test/java/ru/rutcampustrack/shared/events/IdempotencyGuardTest.java#L43) — метод: tryClaim_duplicateCall_returnsFalse; coverage: С того же пустого store и same consumer/type/UUID representation сначала assert true, затем assert false.

**Предложение:** Удалить только tryClaim_firstCall_returnsTrue.

**Cases/граница:** название: tryClaim_firstCall_returnsTrue; строка: 35

**Проверенный контракт:** [services/shared/shared-events/src/main/java/ru/rutcampustrack/shared/events/IdempotencyGuard.java:56](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-events/src/main/java/ru/rutcampustrack/shared/events/IdempotencyGuard.java#L56)

#### BS-D03

**Где:** [services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/audit/AdminActionAspectTest.java:160](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/audit/AdminActionAspectTest.java#L160).

**Сейчас:** target.fail() через тот же Spring proxy бросает IllegalStateException("boom").

**Проблема:** Те же action/fixture/exception assertions уже содержатся в retained exception audit test.

**Что остаётся:** [services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/audit/AdminActionAspectTest.java:136](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/audit/AdminActionAspectTest.java#L136) — метод: aspectWritesAuditEntryOnException; coverage: Идентичный target.fail/assert class/message + persisted failed audit entry; storage reset одинаков.

**Предложение:** Удалить только aspectPropagatesExceptions.

**Cases/граница:** название: aspectPropagatesExceptions; строка: 160

**Проверенный контракт:** [services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/audit/AdminActionAspect.java:46](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/audit/AdminActionAspect.java#L46)

#### BS-D04

**Где:** [services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizerTest.java:133](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizerTest.java#L133).

**Сейчас:** new OpenAPI(); customizer.customise(); components.schemas содержит ErrorResponse.

**Проблема:** Полностью совпадает с подмножеством assertions retained customizerHandlesEmptyOpenApi.

**Что остаётся:** [services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizerTest.java:23](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web/src/test/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizerTest.java#L23) — метод: customizerHandlesEmptyOpenApi; coverage: Тот же пустой OpenAPI и customize; schemas key плюс components != null.

**Предложение:** Удалить только registersErrorResponseSchemaInComponents.

**Cases/граница:** название: registersErrorResponseSchemaInComponents; строка: 133

**Проверенный контракт:** [services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizer.java:72](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizer.java#L72); [services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizer.java:84](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/config/GlobalErrorResponsesCustomizer.java#L84)

### Schedule

#### SCH-D01 — Удалить тесты собственной копии parityOf

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/golden/WeekParityPropertyTest.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/golden/WeekParityPropertyTest.java) — граница: WHOLE_FILE; тесты: метод: parityFlipsEveryWeek; строка: 38; annotationLine: 36; declarationKind: RepeatedTest; метод: parityStableWithinWeek; строка: 67; annotationLine: 65; declarationKind: RepeatedTest; метод: parityDeterministic; строка: 89; annotationLine: 87; declarationKind: Test.

**Сейчас:** Три метода вызывают только private parityOf(date), WeekFields.ISO и enum literals; @RepeatedTest(100) не вызывает production.

**Проблема:** Поменять production AcademicWeekPolicy/AcademicWeekCalendar на противоположную чётность: все три метода останутся зелёными. Даже перестановка ODD/EVEN в local helper сохраняет проверяемые свойства.

**Что остаётся:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/recurring/RecurringDateCalculatorTest.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/recurring/RecurringDateCalculatorTest.java) — тесты: метод: parityUsesIsoWeekWithoutSemesterRelativeRewrite; строка: 54; annotationLine: 53; declarationKind: Test; метод: bothGeneratorsPreserveIsoYearBoundaryIncludingWeek53; строка: 71; annotationLine: 70; declarationKind: Test; preserves: Настоящие compute paths и независимые ожидаемые даты; W53->W1 сохраняется; [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/lesson/LessonGenerationServiceTest.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/lesson/LessonGenerationServiceTest.java) — тесты: метод: computeLessonDates_oddTemplate_firstWeekOdd_returnsOddWeekDates; строка: 94; annotationLine: 93; declarationKind: Test; метод: computeLessonDates_evenTemplate_firstWeekOdd_returnsEvenWeekDates; строка: 115; annotationLine: 114; declarationKind: Test; preserves: Настоящий generator считает точные даты Самостоятельной гарантии production нет: проверяется написанный в тесте helper и java.time. Сохранить реальные дешёвые calendar/generator units; SCH-R01 возвращает golden fixture production sensitivity.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/week/AcademicWeekPolicy.java:29](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/week/AcademicWeekPolicy.java#L29) — контракт: Production calendar mapping; [services/shared/shared-web-api/src/main/java/ru/rutcampustrack/shared/web/time/AcademicWeekCalendar.java:33](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web-api/src/main/java/ru/rutcampustrack/shared/web/time/AcademicWeekCalendar.java#L33) — контракт: Production ISO parity

#### SCH-D02 — Удалить отдельный context/repository-isNotNull smoke

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/EntityMappingIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/EntityMappingIT.java) — граница: WHOLE_FILE; тесты: метод: contextLoads_entitiesValidateAgainstSchema; строка: 26; annotationLine: 25; declarationKind: Test.

**Сейчас:** После запуска общего Spring/PG harness проверяется только ненулевое autowiring двух repository.

**Проблема:** Неправильная enum conversion или потерянный UNIQUE не проверяются этими isNotNull. Любая ошибка старта того же context уже падает в сохранённых реальных API/PG тестах.

**Что остаётся:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ActuatorIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ActuatorIT.java) — тесты: метод: healthEndpointReturnsUp; строка: 16; annotationLine: 15; declarationKind: Test; preserves: Идентичный base Spring/PG/Flyway/ddl-auto context БЕЗ AcademicGrpcClient override, actual HTTP health; [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonGenerationIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonGenerationIT.java) — тесты: метод: createGeneratesLessons; строка: 181; annotationLine: 180; declarationKind: Test; preserves: Тот же AbstractScheduleIntegrationTest/ddl-auto validate, реальные insert/select Lesson; [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonApiIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonApiIT.java) — тесты: метод: cancelLesson_planned_success; строка: 650; annotationLine: 649; declarationKind: Test; preserves: Тот же context и оба repository; наблюдаемый HTTP/MVC результат и сохранённая строка Комментарий приписывает smoke проверку UNIQUE/converters, но сами assertions её не доказывают. Никакой отдельной fixture/profile/schema ветки у этого файла нет.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/resources/application.yml:31](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/resources/application.yml#L31) — контракт: Production Hibernate validation; test resource тоже ddl-auto validate

#### SCH-D03 — Удалить outbox-exists дубль отмены

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonCancelEventIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonCancelEventIT.java) — граница: WHOLE_FILE; тесты: метод: publishesCancelledEventOnCancel; строка: 73; annotationLine: 72; declarationKind: Test.

**Сейчас:** ADMIN отменяет real-PG recurring PLANNED lesson; единственный assertion — pending содержит lesson.cancelled.

**Проблема:** При неправильном lesson_id/actor/date/schema этот метод зелёный, а сохранённый contract test проверяет эти значения. Исходное создание fixture vs writer не меняет ветку LessonService.cancelLesson по occurrence_id+schedule_item_id.

**Что остаётся:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/events/LessonCancelledContractIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/events/LessonCancelledContractIT.java) — тесты: метод: cancelLesson_publishesValidLessonCancelledEvent; строка: 58; annotationLine: 57; declarationKind: Test; preserves: Тот же admin + planned recurring cancel, presence, JSON schema, полный snapshot и committed CANCELLED/actor/time

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java:305](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java#L305) — контракт: Одинаковая canonical cancel ветка и публикация события

#### SCH-D04 — Удалить reflection sanity списка private slot fields

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/item/ScheduleItemEntityTest.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/item/ScheduleItemEntityTest.java) — граница: DECLARATIONS; тесты: метод: scheduleItem_stillHasCoreSlotFields; строка: 32; annotationLine: 31; declarationKind: Test.

**Сейчас:** getDeclaredFields() должен содержать десять имён private полей.

**Проблема:** Перенос groupId и других mapped fields в @MappedSuperclass или rename private storage fields с теми же @Column/public API ломает этот тест при неизменном поведении.

**Что остаётся:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/RecurringScheduleItemIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/RecurringScheduleItemIT.java) — тесты: метод: endpointPersistsSnapshotsAndPastSemesterDates; строка: 159; annotationLine: 158; declarationKind: Test; preserves: Настоящий endpoint создаёт PG snapshot и точные slot times; [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleViewIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleViewIT.java) — тесты: метод: responseContainsAllFields; строка: 111; annotationLine: 110; declarationKind: Test; preserves: Сохранённые public JSON slot fields Самостоятельная гарантия — Java field spelling/placement. Первый noTeacherId не входит в DELETE: D-16 требует SCH-R14.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/entity/ScheduleItem.java:28](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/entity/ScheduleItem.java#L28) — контракт: JPA columns slot identity

#### SCH-D05 — Удалить coordinator mismatch-group mock дубль

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemCoordinatorTest.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemCoordinatorTest.java) — граница: WHOLE_FILE; тесты: метод: activeGroupResponseForAnotherIdIsRejectedBeforeLocalWriter; строка: 32; annotationLine: 31; declarationKind: Test.

**Сейчас:** Remote validateGroup возвращает active group с чужим ID; ожидается RecurringProtocolConflictException и never-calls downstream mocks.

**Проблема:** Извлечение coordinator orchestration или предварительное независимое чтение semester ломает never-call assertions, хотя чужая authority отклоняется и локальные эффекты отсутствуют.

**Что остаётся:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/RecurringScheduleItemIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/RecurringScheduleItemIT.java) — тесты: метод: activeMismatchedGroupAuthorityHasZeroLocalEffects; строка: 325; annotationLine: 324; declarationKind: Test; preserves: Та же inconsistent active group authority через valid MVC create:409 и реальный PG schedule_items/fences=0 Значимая причина отказа и отсутствие writes уже проверяются реальным counterpart. Порядок независимых mock calls не добавляет пользовательский риск.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemCoordinator.java:66](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemCoordinator.java#L66) — контракт: Точный mismatch group authority guard

#### SCH-D06 — Удалить неактивный repository N+1 subrule

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/arch/RepositoryNPlusOneGuardTest.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/arch/RepositoryNPlusOneGuardTest.java) — граница: DECLARATIONS; тесты: метод: repositoriesReturningCollectionsMustGuardNPlusOne; строка: 89; annotationLine: 88; declarationKind: ArchTest.

**Сейчас:** Collection-returning repository methods разрешены, пока anyEntityHasRelations(method.owner) не найдёт entity в package repository.

**Проблема:** Добавить relation в item.entity: owner package item.repository не содержит эту entity, subrule остаётся зелёным. Pageable или произвольный native JOIN также допускаются без доказательства N+1 safety.

**Что остаётся:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/arch/RepositoryNPlusOneGuardTest.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/arch/RepositoryNPlusOneGuardTest.java) — тесты: метод: entitiesMustNotUseJpaRelations; строка: 68; annotationLine: 67; declarationKind: ArchTest; preserves: Явный действующий запрет JPA relations сохранён отдельно Subrule гарантированно не активируется при сохранённом первом no-relations invariant и ищет relation в неверном package. Удалить только этот @ArchTest и используемые исключительно им beGuardedIfReturnsCollectionAndElementHasRelations/anyEntityHasRelations/isGuarded; shared hasAnyRelation оставить.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/repository/ScheduleItemRepository.java:1](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/repository/ScheduleItemRepository.java#L1) — контракт: Repository лежит в отдельном item.repository package; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/entity/ScheduleItem.java:1](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/entity/ScheduleItem.java#L1) — контракт: Entity лежит в item.entity package

#### SCH-D07 — Удалить student-view status-only дубль

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleViewIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleViewIT.java) — граница: DECLARATIONS; тесты: метод: anyRoleCanView_student; строка: 85; annotationLine: 84; declarationKind: Test.

**Сейчас:** Те же fixture, GET, dates и student/group headers; assertion только200.

**Проблема:** Любой body regression проходит в status-only методе; responseContainsAllFields выполняет идентичный запрос и200 плюс публичные поля.

**Что остаётся:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleViewIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleViewIT.java) — тесты: метод: responseContainsAllFields; строка: 111; annotationLine: 110; declarationKind: Test; preserves: Идентичный input и200, body assertions строже

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java:550](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java#L550) — контракт: Одинаковый group read path

#### SCH-D08 — Удалить restore delegation/call-shape test

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/recurring/RecurringLifecycleGateTest.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/recurring/RecurringLifecycleGateTest.java) — граница: DECLARATIONS; тесты: метод: canonicalRestoreUsesTransactionalLifecycleWriter; строка: 99; annotationLine: 98; declarationKind: Test.

**Сейчас:** Hand-created service с mocks: verify restore, EntityManager.refresh, never repository.save/eventPublisher. Ни транзакции, ни persisted результата.

**Проблема:** Замена refresh на безопасный повторный read/DTO или объединение writer слоя ломает тест, хотя persisted status/history/response остаются корректными.

**Что остаётся:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonApiIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonApiIT.java) — тесты: метод: restoreLesson_cancelled_success; строка: 748; annotationLine: 747; declarationKind: Test; метод: cancelThenRestore_archivesExactHomeworkBindingAndKeepsCanonicalHistory; строка: 775; annotationLine: 774; declarationKind: Test; preserves: Наблюдаемый restore + PG cancellation tuple/current pointer/revisions/history/binding state; [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/AssignmentReplacementServiceIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/AssignmentReplacementServiceIT.java) — тесты: метод: cancelledOccurrenceRestoresThroughExactReplacementAndConflictRollsBack; строка: 463; annotationLine: 462; declarationKind: Test; preserves: Другой assignment: new generation, exact history, conflict rollback, canonical query Имя обещает transactional writer, но direct mocks не доказывают транзакционность. Сохранённые tests проверяют те же полезные результаты и реальную atomicity.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java:370](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java#L370) — контракт: Canonical restore и load текущего physical result

#### SCH-D09 — Удалить второй fresh full migration cycle

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java) — граница: DECLARATIONS; тесты: метод: freshFullChainMigratesValidatesAndRepeatsWithoutWork; строка: 60; annotationLine: 59; declarationKind: Test.

**Сейчас:** Отдельная пустая schema: migrate success/current=max/pending empty, validate и второй migrate0.

**Проблема:** Это та же fresh schema и classpath:db/migration цепочка без отдельной исходной версии или данных; дефект цепочки уже ловится сохранёнными methods.

**Что остаётся:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/FlywayMigrationIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/FlywayMigrationIT.java) — тесты: метод: freshInstallAppliesAllMigrations; строка: 55; annotationLine: 54; declarationKind: Test; метод: checksumConsistencyAfterFullMigrate; строка: 187; annotationLine: 186; declarationKind: Test; preserves: В сумме сохраняют success, positive executed, pending empty, max version, validate, repeated migrate0; дополнительно transfer tables

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/resources/db/migration/V32\_\_subject_deletion_participant.sql:206](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/resources/db/migration/V32__subject_deletion_participant.sql#L206) — контракт: Общая migration chain содержит V32 guard addition

#### SCH-D10 — Удалить два ложных regeneration кейса и fixture-bypass smoke

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonGenerationIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonGenerationIT.java) — граница: DECLARATIONS; тесты: метод: updateNonScheduleFieldsNoReGeneration; строка: 264; annotationLine: 263; declarationKind: Test; метод: reGenerationPreservesNonPlannedLessons; строка: 296; annotationLine: 295; declarationKind: Test; метод: directDbSaveDoesNotTriggerGeneration; строка: 343; annotationLine: 342; declarationKind: Test.

**Сейчас:** Два PUT не содержат обязательного Idempotency-Key/observed revision и ожидают400; затем считают старые строки. Третий сохраняет fixture repository напрямую и проверяет отсутствие generation.

**Проблема:** Полностью сломанная обработка accepted room/parity update остаётся зелёной: первые два запроса прекращаются на обязательном HTTP header. Repository smoke не вызывает product operation. Контракт уже использует durable transfer/history, а не прежнюю room-in-place/no-regeneration схему.

**Что остаётся:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiIT.java) — тесты: метод: updateTemplate_requiresReplayAndPreviewContract; строка: 229; annotationLine: 228; declarationKind: Test; preserves: Отсутствующий replay/preview contract ->400 и unchanged template; [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/RecurringScheduleItemIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/RecurringScheduleItemIT.java) — тесты: метод: futureRoomUsesDurableTransferAndReplayKeepsAcceptedSnapshot; строка: 1200; annotationLine: 1199; declarationKind: Test; метод: parityRemovalArchivesFutureHomeworkAndRestoresAsNewPhysicalGeneration; строка: 1274; annotationLine: 1273; declarationKind: Test; метод: manuallyCancelledFutureLessonIsNeverResumedByTemplateReturn; строка: 1884; annotationLine: 1883; declarationKind: Test; preserves: Accepted updates, retained past/started physical snapshots, future transfer, manual cancelled exception и current generation/history; [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/RecurringScheduleItemIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/RecurringScheduleItemIT.java) — тесты: метод: stalePreviewAndDeniedAuthorityHaveNoEffects; строка: 1523; annotationLine: 1522; declarationKind: Test; preserves: Rejected command не меняет template/lesson count/replay directDbSaveDoesNotTriggerGeneration проверяет способ подготовки fixtures; пользовательской операции/production generation не вызывает. Два других не подтверждают заявленную генерацию; их реальный missing-header risk и lifecycle/history results сохранены указанными counterparts. updateScheduleFieldsReGenerates вынесен в SCH-R06.

**Проверенный контракт:** [services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/ScheduleItemApi.java:78](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/ScheduleItemApi.java#L78) — контракт: Обязательный Idempotency-Key; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemController.java:90](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemController.java#L90) — контракт: Current canonical update endpoint; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemLifecycleWriter.java:762](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemLifecycleWriter.java#L762) — контракт: Slot identity immutable; room/parity разрешены

#### SCH-D11 — Удалить column-name count рядом с реальным ShedLock probe

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ShedLockSmokeIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ShedLockSmokeIT.java) — граница: DECLARATIONS; тесты: метод: shedlockTable_existsWithExpectedColumns; строка: 87; annotationLine: 86; declarationKind: Test.

**Сейчас:** information_schema содержит четыре имени столбцов shedlock (без schema/type проверки).

**Проблема:** Неправильные типы/поведение lock всё равно даютcount4; настоящие два JdbcTemplateLockProvider проходят acquire/contention/min-hold на той же мигрированной schema.

**Что остаётся:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ShedLockSmokeIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ShedLockSmokeIT.java) — тесты: метод: twoInstances_onlyOneHoldsLock; строка: 60; annotationLine: 59; declarationKind: Test; preserves: Два real JDBC provider используют migrated table для lock acquire и contention; SCH-R02 переключает factory на production Никакого отдельного column type/nullability/index риска этот count не проверяет; нужный schema compatibility already exercised by provider integration.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/resources/db/migration/V10\_\_shedlock_table.sql:5](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/resources/db/migration/V10__shedlock_table.sql#L5) — контракт: Четыре реальные provider columns

### Angular и frontend runner/config

#### D-A01

**Где:** [frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.spec.ts:29](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.spec.ts#L29) — метод: should create; сейчас: expect(component).toBeTruthy().

**Сейчас:** expect(component).toBeTruthy()

**Проблема:** Конструирование того же реального компонента через тот же TestBed и те же providers уже обязательно выполняется в сохраняемом сценарии. Smoke не имеет отдельной смены inputs, platform или detectChanges. Ошибка DI/конструктора уже прервёт общий beforeEach сохраняемого теста.

**Что остаётся:** [frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.spec.ts:56](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.spec.ts#L56) — метод: on success, stats signal is set with response data; сейчас: ; сохранённая гарантия: То же createComponent в beforeEach; дополнительно загрузка и stats/loading/error.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.ts:31](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.ts#L31)

#### D-A02

**Где:** [frontends/web-panel/src/app/features/admin/groups/groups-page.component.spec.ts:92](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/groups/groups-page.component.spec.ts#L92) — метод: should create; сейчас: expect(component).toBeTruthy().

**Сейчас:** expect(component).toBeTruthy()

**Проблема:** Конструирование того же реального компонента через тот же TestBed и те же providers уже обязательно выполняется в сохраняемом сценарии. Smoke не имеет отдельной смены inputs, platform или detectChanges. Удаление отдельного smoke сохраняет тот же путь DI/конструктора в beforeEach.

**Что остаётся:** [frontends/web-panel/src/app/features/admin/groups/groups-page.component.spec.ts:96](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/groups/groups-page.component.spec.ts#L96) — метод: ngOnInit загружает активные группы по умолчанию (status=ACTIVE); сейчас: ; сохранённая гарантия: То же createComponent; загрузка ACTIVE групп и студентов.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/admin/groups/groups-page.component.ts:54](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/groups/groups-page.component.ts#L54)

#### D-A03

**Где:** [frontends/web-panel/src/app/features/admin/semesters/semesters-page.component.spec.ts:41](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/semesters/semesters-page.component.spec.ts#L41) — метод: should create; сейчас: expect(component).toBeTruthy().

**Сейчас:** expect(component).toBeTruthy()

**Проблема:** Конструирование того же реального компонента через тот же TestBed и те же providers уже обязательно выполняется в сохраняемом сценарии. Smoke не имеет отдельной смены inputs, platform или detectChanges. DI/constructor failure уже ломает beforeEach и survivor.

**Что остаётся:** [frontends/web-panel/src/app/features/admin/semesters/semesters-page.component.spec.ts:45](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/semesters/semesters-page.component.spec.ts#L45) — метод: ngOnInit loads semesters; сейчас: ; сохранённая гарантия: То же createComponent и HTTP-загрузка семестров.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/admin/semesters/semesters-page.component.ts:30](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/semesters/semesters-page.component.ts#L30)

#### D-A04

**Где:** [frontends/web-panel/src/app/features/admin/users/users-page.component.spec.ts:74](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/users/users-page.component.spec.ts#L74) — метод: should create; сейчас: truthy + flushInitialRequests().

**Сейчас:** truthy + flushInitialRequests()

**Проблема:** Конструирование того же реального компонента через тот же TestBed и те же providers уже обязательно выполняется в сохраняемом сценарии. Smoke не имеет отдельной смены inputs, platform или detectChanges. Survivor использует тот же beforeEach, включая fixture.detectChanges и оба initial HTTP.

**Что остаётся:** [frontends/web-panel/src/app/features/admin/users/users-page.component.spec.ts:79](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/users/users-page.component.spec.ts#L79) — метод: ngOnInit triggers loadUsers and listGroups calls; сейчас: ; сохранённая гарантия: Те же construction/template/init запросы; дополнительно методы GET и загруженные данные.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/admin/users/users-page.component.ts:47](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/users/users-page.component.ts#L47)

#### D-A05

**Где:** [frontends/web-panel/src/app/features/headman/late-checkin/headman-late-checkin.component.spec.ts:48](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/late-checkin/headman-late-checkin.component.spec.ts#L48) — метод: shows empty state on 404 graceful degradation; сейчас: 404 заявлен в названии; фактически mock getGroupLateCheckins: () => of([]), затем тот же empty text..

**Сейчас:** 404 заявлен в названии; фактически mock getGroupLateCheckins: () => of([]), затем тот же empty text.

**Проблема:** Точная копия соседнего сценария: одинаковые providers, Auth/STOMP и результат of([]). Никакой 404 или recovery-ветки здесь нет. Если реальная обработка 404 сломается, оба сценария продолжат получать готовый пустой массив; удаляемый не даёт отдельной гарантии 404.

**Что остаётся:** [frontends/web-panel/src/app/features/headman/late-checkin/headman-late-checkin.component.spec.ts:40](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/late-checkin/headman-late-checkin.component.spec.ts#L40) — метод: shows empty state when backend returns no pending requests; сейчас: ; сохранённая гарантия: Тот же смонтированный компонент, те же providers, of([]), тот же empty-state oracle.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/headman/late-checkin/headman-late-checkin.component.ts:288](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/late-checkin/headman-late-checkin.component.ts#L288)

#### D-A06

**Где:** [frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.spec.ts:147](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.spec.ts#L147) — метод: creates without errors (smoke test); сейчас: setup(); expect(component).toBeTruthy().

**Сейчас:** setup(); expect(component).toBeTruthy()

**Проблема:** Конструирование того же реального компонента через тот же TestBed и те же providers уже обязательно выполняется в сохраняемом сценарии. Smoke не имеет отдельной смены inputs, platform или detectChanges. setup() в обоих случаях делает createComponent и detectChanges с теми же default mock responses.

**Что остаётся:** [frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.spec.ts:152](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.spec.ts#L152) — метод: loadSchedule calls getGroupScheduleItems with (groupId, activeSemesterId) and populates matrix; сейчас: ; сохранённая гарантия: Те же DI/template/init + матрица расписания из ответа.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.ts:297](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.ts#L297)

#### D-A07

**Где:** [frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.spec.ts:55](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.spec.ts#L55) — метод: matches the UI-SPEC GPS denied copy; сейчас: GPS_DENIED_MESSAGE === полный скопированный литерал.

**Сейчас:** GPS_DENIED_MESSAGE === полный скопированный литерал

**Проблема:** Изолированный oracle редакционного текста константы; владелец явно не требует точного copy. Смысл dispatch по Geolocation error сохраняется отдельно. Безопасная редактура сообщения заставит менять тест вместе с константой. Самостоятельной проверки поведения нет.

**Что остаётся:** [frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.spec.ts:63](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.spec.ts#L63) — метод: maps permission denied to permission copy; сейчас: ; сохранённая гарантия: mapGeolocationError(permission denied) выбирает GPS_DENIED_MESSAGE; (44:291) сохраняет показ сообщения после реального клика/GPS-denied callback.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.ts:33](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/checkin/checkin-error-mapper.ts#L33)

#### D-A08

**Где:** [frontends/web-panel/src/app/features/student/checkin/student-checkin.component.spec.ts:239](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/checkin/student-checkin.component.spec.ts#L239) — метод: on 2xx success the CTA swaps to confirmation badge; сейчас: GPS success + setup ACTIVE + клик + flush present + getByText confirmation.

**Сейчас:** GPS success + setup ACTIVE + клик + flush present + getByText confirmation

**Проблема:** Точный поднабор уже сохраняемого пользовательского сценария с теми же GPS, lesson, POST response и итоговым DOM oracle. Название про замену CTA не добавляет assertion отсутствия кнопки. Удаление перехода в confirmed ломает и survivor; дополнительного риска у этого кейса нет.

**Что остаётся:** [frontends/web-panel/src/app/features/student/checkin/student-checkin.component.spec.ts:202](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/checkin/student-checkin.component.spec.ts#L202) — метод: POSTs to /api/attendance/checkin with GPS coords on button click; сейчас: ; сохранённая гарантия: Те же mount/GPS/click/response/confirmation + явные POST method/body и GPS options.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts:250](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/checkin/student-checkin.component.ts#L250)

#### D-A09

**Где:** [frontends/web-panel/src/app/features/student/homework/month-view/student-homework-month-view.component.spec.ts:53](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/homework/month-view/student-homework-month-view.component.spec.ts#L53) — метод: matrix has exactly 42 cells (6 weeks × 7 days); сейчас: component.cells.length === 42.

**Сейчас:** component.cells.length === 42

**Проблема:** Фиксирует внутреннюю форму календарной раскладки 6×7. Отдельной гарантии доступной даты/заданий/события не добавляет. Заменить публичный getter cells на внутреннюю генерацию, сохранив weeks, те же42 отрисованные gridcell и выбор даты: внутренний .cells oracle сломается, сохраняемый mounted case60 продолжит проверять тот же полезный результат.

**Что остаётся:** [frontends/web-panel/src/app/features/student/homework/month-view/student-homework-month-view.component.spec.ts:60](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/homework/month-view/student-homework-month-view.component.spec.ts#L60) — метод: click on a cell emits dateSelected with the cell date; сейчас: ; сохранённая гарантия: Тот же month/items и mount; уже проверяются 42 реально отрисованные gridcell и клик. R-A11 усиливает значение события.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/student/homework/month-view/student-homework-month-view.component.ts:56](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/homework/month-view/student-homework-month-view.component.ts#L56)

#### D-A10

**Где:** [frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.spec.ts:89](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.spec.ts#L89) — метод: applies lesson-row--active class for ACTIVE lessons; сейчас: querySelector(.lesson-row--active) и .lesson-row\_\_dot.

**Сейчас:** querySelector(.lesson-row--active) и .lesson-row\_\_dot

**Проблема:** Только CSS hooks и декоративная точка; meaningful status/action/aria кейсы файла остаются. Безопасное переименование CSS-класса или замена декоративного dot ломает тест при том же доступном статусе и действии.

**Что остаётся:** Отдельной полезной гарантии нет: проверяется только косметическая деталь либо буквальная копия статического текста. Действие/права/данные/доступность этим oracle не проверяются.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.ts:32](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/schedule/lesson-row/lesson-row.component.ts#L32)

#### D-A11

**Где:** [frontends/web-panel/src/app/features/student/schedule/student-schedule.component.spec.ts:134](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/schedule/student-schedule.component.spec.ts#L134) — метод: renders 4 skeleton rows while the request is pending; сейчас: ровно 4 .schedule-skeleton во время pending HTTP.

**Сейчас:** ровно 4 .schedule-skeleton во время pending HTTP

**Проблема:** Число декоративных skeleton не является пользовательским контрактом. aria-busy/roles/actions этот тест не проверяет. Три или пять skeleton с тем же aria-busy и последующим расписанием дают ложное падение.

**Что остаётся:** Отдельной полезной гарантии нет: проверяется только косметическая деталь либо буквальная копия статического текста. Действие/права/данные/доступность этим oracle не проверяются.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts:75](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/schedule/student-schedule.component.ts#L75)

#### D-A12

**Где:** [frontends/web-panel/src/app/features/teacher/journal/journal-grid/journal-grid.component.spec.ts:30](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/journal/journal-grid/journal-grid.component.spec.ts#L30) — метод: creates the component; сейчас: render(mockJournal); expect(component).toBeTruthy().

**Сейчас:** render(mockJournal); expect(component).toBeTruthy()

**Проблема:** Конструирование того же реального компонента через тот же TestBed и те же providers уже обязательно выполняется в сохраняемом сценарии. Smoke не имеет отдельной смены inputs, platform или detectChanges. Тот же render с тем же journalData уже обязателен у survivor.

**Что остаётся:** [frontends/web-panel/src/app/features/teacher/journal/journal-grid/journal-grid.component.spec.ts:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/journal/journal-grid/journal-grid.component.spec.ts#L37) — метод: columns() signal produces correct column IDs from 2 dates x 2 lessons = 4 columns; сейчас: ; сохранённая гарантия: Тот же полный mount с mockJournal; проверка всех комбинаций date/lesson колонок.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/teacher/journal/journal-grid/journal-grid.component.ts:183](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/journal/journal-grid/journal-grid.component.ts#L183)

#### D-A13

**Где:** [frontends/web-panel/src/app/features/teacher/journal/journal-page.component.spec.ts:102](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/journal/journal-page.component.spec.ts#L102) — метод: creates the component; сейчас: createComponent + truthy.

**Сейчас:** createComponent + truthy

**Проблема:** Та же DI/конструкция уже покрыта более сильным mount/init с теми же default providers. У smoke нет detectChanges, которого не было бы у survivor. DI/конструкторная ошибка остановит survivor; дополнительной template гарантии в smoke нет.

**Что остаётся:** [frontends/web-panel/src/app/features/teacher/journal/journal-page.component.spec.ts:123](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/journal/journal-page.component.spec.ts#L123) — метод: ngOnInit loads assignments, groups, subjects, and sets default semester range; сейчас: ; сохранённая гарантия: Тот же createComponent плюс detectChanges, assignments/groups/subjects/semester defaults.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/teacher/journal/journal-page.component.ts:64](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/journal/journal-page.component.ts#L64)

#### D-A14

**Где:** [frontends/web-panel/src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.spec.ts:12](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.spec.ts#L12) — метод: creates the component; сейчас: valid numeric inputs + detectChanges + truthy.

**Сейчас:** valid numeric inputs + detectChanges + truthy

**Проблема:** Сохраняемый numeric-render кейс монтирует ту же компоненту с теми же типами inputs; production template интерполирует числа без отдельных веток для 10/85. Сбой compile/DI/template уже остановит сохранённый mount с числами 24/75.

**Что остаётся:** [frontends/web-panel/src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.spec.ts:21](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.spec.ts#L21) — метод: renders the total lessons number; сейчас: ; сохранённая гарантия: То же compileComponents/createComponent/detectChanges; дополнительно отрисовка количества занятий.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.ts:63](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.ts#L63)

#### D-A15

**Где:** [frontends/web-panel/src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.spec.ts:41](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.spec.ts#L41) — метод: renders "Всего занятий" label; сейчас: textContent contains статический label «Всего занятий».

**Сейчас:** textContent contains статический label «Всего занятий»

**Проблема:** Чистый oracle редакционного copy без операции, данных или доступной связи label/value. Редактура заголовка при неизменных числах и aria-live создаёт ненужное падение.

**Что остаётся:** Отдельной полезной гарантии нет: проверяется только косметическая деталь либо буквальная копия статического текста. Действие/права/данные/доступность этим oracle не проверяются.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.ts:8](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/overall-stat-card/overall-stat-card.component.ts#L8)

#### D-A16

**Где:** [frontends/web-panel/src/app/features/teacher/stats/stats-page.component.spec.ts:36](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/stats-page.component.spec.ts#L36) — метод: creates the component; сейчас: createComponent + detectChanges + truthy.

**Сейчас:** createComponent + detectChanges + truthy

**Проблема:** Конструирование того же реального компонента через тот же TestBed и те же providers уже обязательно выполняется в сохраняемом сценарии. Smoke не имеет отдельной смены inputs, platform или detectChanges. Survivor выполняет ровно тот же mount с тем же пустым API fixture.

**Что остаётся:** [frontends/web-panel/src/app/features/teacher/stats/stats-page.component.spec.ts:68](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/stats-page.component.spec.ts#L68) — метод: loads assignments, groups, subjects and semesters on init; сейчас: ; сохранённая гарантия: Тот же createComponent/detectChanges/init и четыре запроса.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/teacher/stats/stats-page.component.ts:63](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/stats-page.component.ts#L63)

#### D-A17

**Где:** [frontends/web-panel/src/app/features/teacher/stats/stats-page.component.spec.ts:43](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/stats-page.component.spec.ts#L43) — метод: renders "Статистика посещаемости" heading; сейчас: h1.textContent contains фиксированный заголовок.

**Сейчас:** h1.textContent contains фиксированный заголовок

**Проблема:** Проверка точного редакционного заголовка, который владелец не просит охранять. Это не выбор по accessible name для запуска действия. Редактура заголовка при сохранении назначения/работы страницы ломает тест.

**Что остаётся:** Отдельной полезной гарантии нет: проверяется только косметическая деталь либо буквальная копия статического текста. Действие/права/данные/доступность этим oracle не проверяются.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/teacher/stats/stats-page.component.ts:39](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/stats-page.component.ts#L39)

#### D-A18

**Где:** [frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.spec.ts:18](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.spec.ts#L18) — метод: creates the component; сейчас: sampleData + subjectName + detectChanges + truthy.

**Сейчас:** sampleData + subjectName + detectChanges + truthy

**Проблема:** Та же chartData и тот же mount уже выполняются в canvas-сценарии; смена непустого subjectName не создаёт отдельной ветки production template. Сбой DI/Chart directive/template остановит и survivor.

**Что остаётся:** [frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.spec.ts:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.spec.ts#L37) — метод: contains a canvas element for the chart; сейчас: ; сохранённая гарантия: Тот же compile/create/detectChanges с sampleData и canvas. Имя отдельно проверяет27.

**Cases/граница:** TEST_METHOD_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.ts:67](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.ts#L67)

### Mobile: feature clients и компоненты

#### MCF-D001

**Где:** [frontends/mobile-core/src/features/headman-journal/headman-journal-client.test.ts:26](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/headman-journal/headman-journal-client.test.ts#L26) — тесты: uses the shared Moscow calendar date at the UTC day boundary; cases: 2026-09-20T21:30Z -> 2026-09-21; 2026-09-21T20:59:59.999Z -> 2026-09-21.

**Сейчас:** Calls only imported moscowDate; no HeadmanJournalApi exercised.

**Проблема:** The same global civil-date rule is independently exercised immediately before/at Moscow midnight in the surviving domain test. Repeating it here cannot detect a journal caller that stops using Moscow dates.

**Что остаётся:** [frontends/mobile-core/src/domain/homework.test.ts:101](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/homework.test.ts#L101) — тест: uses serverNow timezone around Moscow midnight and does not infer from the boolean; covers: Direct moscowDate assertions at 20:59:59Z and 21:00:00Z cover both same-day and next-day outcomes; same shared function, no journal-specific behavior lost.

**Предложение:** Delete only this method and its now-unused moscowDate import. Keep all journal transport/status/session tests.

**Cases/граница:** 2026-09-20T21:30Z -> 2026-09-21; 2026-09-21T20:59:59.999Z -> 2026-09-21 uses the shared Moscow calendar date at the UTC day boundary

**Проверенный контракт:** frontends/mobile-core/src/domain/homework.ts:96

#### MCF-D002

**Где:** [frontends/mobile-core/src/features/requests/attachment-validation.test.ts:83](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/requests/attachment-validation.test.ts#L83) — тесты: describes the concordance rule in the validation feedback; cases: fixed JPEG/PNG/PDF metadata.

**Сейчас:** Checks two fixed text fragments returned by requestAttachmentFormatHint.

**Проблема:** Changing explanatory wording while preserving accepted/rejected MIME+extension pairs fails this test. It does not inspect a user action or metadata-dependent acceptance.

**Что остаётся:** [frontends/mobile-core/src/features/requests/attachment-validation.test.ts:10](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/requests/attachment-validation.test.ts#L10) — тест: accepts only MIME and extension combinations supported by the server; covers: Useful acceptance/rejection concordance rule survives.; [frontends/mobile-core/src/features/requests/requests-controller.test.ts:737](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/requests/requests-controller.test.ts#L737) — тест: requires the MIME and extension pair to agree before submission; covers: Controller submission rejects mismatched pairs. Exact feedback wording itself has no separate production guarantee in this audit.

**Предложение:** Delete only this wording method and unused hint import. Do not remove MIME/extension, Unicode stripping, truncation or controller validation tests.

**Cases/граница:** fixed JPEG/PNG/PDF metadata describes the concordance rule in the validation feedback

**Проверенный контракт:** frontends/mobile-core/src/features/requests/attachment-validation.ts:128

#### MCF-D003

**Где:** [frontends/mobile-core/src/features/attendance/attendance-periods.test.ts:68](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/attendance/attendance-periods.test.ts#L68) — тесты: keeps QA weekly weights and semester totals consistent with daily counts; cases: all synthetic fixture weeks; graph-mode review scenarios.

**Сейчас:** Reaggregates dev fixture days/weeks and repeats count/percent arithmetic against other generated fixture fields.

**Проблема:** Breaking a production server aggregate or replacing graphPeriods with a constant would leave this method green: it calls only attendanceFixture/createAttendanceReviewScenarios and test-local sums. Changing the synthetic review story can fail it without changing user behavior.

**Что остаётся:** [frontends/mobile-core/src/features/attendance/attendance-periods.test.ts:61](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/attendance/attendance-periods.test.ts#L61) — тест: uses server weighted weekly metrics in Monday–Sunday buckets; covers: The actual client guarantee is to preserve supplied metrics, not calculate server aggregates.; noProductionGuarantee: The deleted method proves fixture coherence only. No production aggregation code is exercised; no equivalence to backend aggregate coverage is claimed.

**Предложение:** Delete this method only. Keep schema conformance and production projection/period/state units. This proposal removes synthetic arithmetic maintenance; it does not assert a backend calculation is covered.

**Cases/граница:** all synthetic fixture weeks; graph-mode review scenarios keeps QA weekly weights and semester totals consistent with daily counts

**Проверенный контракт:** frontends/mobile-core/src/dev/attendance-fixtures.ts:25; frontends/mobile-core/src/dev/attendance-fixtures.ts:86; frontends/mobile-core/src/dev/attendance-fixtures.ts:124

### Mobile shared/PWA/TMA и dev-fixtures

#### MS-D01

**Где:** [frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts:737](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts#L737) — метод: keeps the same command and idempotency key after a lost ACK and reconnect; до строки: 762.

**Сейчас:** Creates CheckinCommandRecovery directly, fails one send, retries it, and compares two command/key objects. StudentFeatureOwner is never mounted in this method.

**Проблема:** Removing the real owner retry wiring or recreating its recovery object on reconnect still leaves this alleged owner-lifecycle test green; it never calls owner code.

**Что остаётся:** [frontends/mobile-core/src/domain/checkin-recovery.test.ts:20](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/checkin-recovery.test.ts#L20) — метод: replays the exact logical command after a response is lost and single-flights concurrent acquisition; до строки: 46; [frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts:1862](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts#L1862) — метод: retains the mounted owner uncertain check-in through explicit connection retry; до строки: 1924 Domain case covers the entire same command/key replay behavior and also acquisition single-flight. The mounted case independently keeps the actual owner, explicit-retry/no-auto-replay and acquisition-once risk. Different initial states are not being equated: the deleted method never had a mounted initial state.

**Предложение:** DELETE only method737–762. Keep both named survivors and all ownership/idempotency guards.

**Cases/граница:** method

**Проверенный контракт:** [frontends/mobile-core/src/domain/checkin.ts:22](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/checkin.ts#L22) — метод: CheckinCommandRecovery.execute; [frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue:1981](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue#L1981) — метод: checkin

#### MS-D02

**Где:** [frontends/mobile-core/src/shared/navigation.test.ts:24](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/navigation.test.ts#L24) — метод: keeps the owning root for a cross-feature deep link without an explicit entry; до строки: 31.

**Сейчас:** Pushes a profile detail from today without preserveHistory; expects owning root/detail, Back to owning root, then stop.

**Проблема:** This repeats the same generic transition as the Node test. Both execute the identical root-mismatch branch; swapping profile detail for homework detail changes no behavior selected by production.

**Что остаётся:** [frontends/mobile-core/tests/navigation.test.mjs:32](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/tests/navigation.test.mjs#L32) — метод: cross-root nested navigation starts the owning root history; до строки: 43 Same initial today root, cross-root detail push without preserveHistory, owning-root seeding, first Back destination and second Back stop are asserted. Production push compares only route.root and preserveHistory, with no profile/homework branch. Keep the preserveHistory/back-guard, initial nested, replace, notification and shell visibility tests: those use different operations/risks.

**Предложение:** DELETE only shared navigation method24–31; retain the existing Node runner (frontends/package.json test:contract).

**Cases/граница:** method

**Проверенный контракт:** [frontends/mobile-core/src/shared/navigation.ts:137](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/navigation.ts#L137) — метод: push; [frontends/mobile-core/src/shared/navigation.ts:165](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/navigation.ts#L165) — метод: back

#### MS-D03

**Где:** [frontends/mobile-core/src/api/student-client.test.ts:47](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/student-client.test.ts#L47) — метод: propagates a 304 body-read rejection; до строки: 57; [frontends/mobile-core/src/api/student-client.test.ts:25](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/student-client.test.ts#L25) — метод: arrayBuffer called once / json not called; [frontends/mobile-core/src/api/student-client.test.ts:44](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/student-client.test.ts#L44) — метод: JSON parser called once.

**Сейчас:** Spies on the particular Response methods; the third method replaces arrayBuffer on a bodyless 304 Response with an artificial rejected promise.

**Проблема:** A semantically equivalent 200 decoder based on response.text()+JSON.parse, or a 304 path that recognizes a bodyless response and returns null+ETag, fails these implementation oracles while preserving wire status/data. The injected 304 arrayBuffer rejection is not an actual body delivered by the constructed Response.

**Что остаётся:** [frontends/mobile-core/src/api/student-client.test.ts:9](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/student-client.test.ts#L9) — метод: 304 result remains null schedule plus ETag; до строки: 27; [frontends/mobile-core/src/api/student-client.test.ts:29](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/student-client.test.ts#L29) — метод: 200 result remains decoded schedule plus ETag; до строки: 45 Keep the public 304/200 results, exact status distinctions, ETag and decoded data. No accepted product guarantee was found for which Response reader must be invoked or for artificial arrayBuffer failure on a null 304 body. If a separate explicit transport-drain requirement is identified, retain that requirement as a stream/resource boundary instead of a method spy.

**Предложение:** DELETE method47–57 and only reader-call assertions25–26/44 plus now-unused local spies; rename remaining methods to their result contracts. Do not change production or status handling.

**Cases/граница:** method-and-assertions

**Проверенный контракт:** [frontends/mobile-core/src/api/student-client.ts:127](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/student-client.ts#L127) — метод: getSemesterSchedule; [frontends/mobile-core/src/api/student-client.ts:329](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/student-client.ts#L329) — метод: json

#### MS-D04

**Где:** [frontends/mobile-core/src/test-adapter/fixture-transport.test.ts:133](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/test-adapter/fixture-transport.test.ts#L133) — метод: injectedFetch.mock.contexts[0] equals injectedApi.

**Сейчас:** Requires the injected fetch callback to receive StudentApi as JavaScript this.

**Проблема:** Capturing the injected function in a closure, binding it, or calling it as a local function preserves every request/response but changes this and fails the test.

**Что остаётся:** [frontends/mobile-core/src/test-adapter/fixture-transport.test.ts:117](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/test-adapter/fixture-transport.test.ts#L117) — метод: default global receiver Illegal invocation regression and injected result; до строки: 134 Keep the default-fetch illegal-invocation test and its public decoded result, and keep the injected result/call count131–132. Only the injected function receiver is an unsupported internal contract.

**Предложение:** DELETE only line133.

**Cases/граница:** assertion

**Проверенный контракт:** [frontends/mobile-core/src/api/student-client.ts:115](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/student-client.ts#L115) — метод: constructor; [frontends/mobile-core/src/api/student-client.ts:313](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/student-client.ts#L313) — метод: requestResponse fetch invocation

#### MS-D05

**Где:** [frontends/mobile-core/src/shared/mobile-navigation-items.test.ts:28](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/mobile-navigation-items.test.ts#L28) — метод: exact headman dock labels.

**Сейчас:** Compares the entire authored Russian label array.

**Проблема:** A copy-only rename of a dock caption fails despite identical root IDs, destinations and availability.

**Что остаётся:** [frontends/mobile-core/src/shared/mobile-navigation-items.test.ts:19](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/mobile-navigation-items.test.ts#L19) — метод: five canonical root IDs remain checked; до строки: 29; [frontends/mobile-core/src/shared/mobile-navigation-items.test.ts:8](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/mobile-navigation-items.test.ts#L8) — метод: More availability remains checked; до строки: 15 Keep exact route/root policy and availability. This recommendation does not alter role/menu semantics or accessibility; it removes only the full authored-copy oracle.

**Предложение:** DELETE only label-array assertion28.

**Cases/граница:** assertion

**Проверенный контракт:** [frontends/mobile-core/src/shared/mobile-navigation-items.ts:91](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/mobile-navigation-items.ts#L91) — метод: createHeadmanNavigationItems

#### MS-D06

**Где:** [frontends/tma-vue/src/report-download-adapter.test.ts:447](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/src/report-download-adapter.test.ts#L447) — метод: Object.keys(request.headmanStats); до строки: 453.

**Сейчас:** Builds a request literal in the test, validates it, then compares the keys of that same unchanged literal with a literal list.

**Проблема:** The assertion stays green if a real selector mapper begins adding a forbidden group/page or drops the selected semester; no production mapper or transport produced the inspected object. It also locks insertion order of test-authored keys.

**Что остаётся:** [frontends/tma-vue/src/report-download-adapter.test.ts:446](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/src/report-download-adapter.test.ts#L446) — метод: validate the legacy optional-fields request; [frontends/tma-vue/src/report-download-adapter.test.ts:456](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/src/report-download-adapter.test.ts#L456) — метод: maps current stats slice and strips absent filter values; до строки: 484; [frontends/mobile-core/src/shared/report-download-selector.test.ts:164](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/report-download-selector.test.ts#L164) — метод: sends selected semester for HEADMAN_STATS and TREND; до строки: 224 Retain validation at446 and actual mapper/wire cases. Current production explicitly accepts optional semesterId, so the old method title claiming absence of semester is stale and must not become an exclusion requirement.

**Предложение:** DELETE only assertion447–453; describe remaining positive validation as legacy optional selector acceptance. No field/authority/status rules change.

**Cases/граница:** assertion

**Проверенный контракт:** [frontends/mobile-core/src/shared/report-download-client.ts:679](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/report-download-client.ts#L679) — метод: validateTicketRequest; [frontends/mobile-core/src/shared/report-download-client.ts:737](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/report-download-client.ts#L737) — метод: HEADMAN_STATS allowed fields; [frontends/mobile-core/src/shared/report-download-client.ts:867](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/report-download-client.ts#L867) — метод: HEADMAN_STATS validation

#### MS-D07

**Где:** [frontends/mobile-core/src/domain/checkin.test.ts:125](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/checkin.test.ts#L125) — метод: pending request label; [frontends/mobile-core/src/domain/checkin.test.ts:139](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/checkin.test.ts#L139) — метод: pending reason label; [frontends/mobile-core/src/domain/homework.test.ts:96](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/homework.test.ts#L96) — метод: group labels; [frontends/mobile-core/src/domain/homework.test.ts:113](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/homework.test.ts#L113) — метод: Today formatted caption; [frontends/mobile-core/src/domain/homework.test.ts:114](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/homework.test.ts#L114) — метод: Tomorrow formatted caption; [frontends/pwa-vue/src/PwaUpdateGate.test.ts:167](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/PwaUpdateGate.test.ts#L167) — метод: updating caption; [frontends/pwa-vue/src/PwaUpdateGate.test.ts:209](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/PwaUpdateGate.test.ts#L209) — метод: retry caption.

**Сейчас:** Exact static wording/date-caption formatting is asserted in addition to state, identity, clock or disabled-control assertions.

**Проблема:** Rewording pending/update/date captions while preserving pending state, Moscow dates and update gate changes these results.

**Что остаётся:** [frontends/mobile-core/src/domain/checkin.test.ts:124](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/checkin.test.ts#L124) — метод: isPendingCheckin true; [frontends/mobile-core/src/domain/checkin.test.ts:138](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/checkin.test.ts#L138) — метод: isPendingCheckin without request projection; [frontends/mobile-core/src/domain/homework.test.ts:97](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/homework.test.ts#L97) — метод: group item identities/deduplication; [frontends/mobile-core/src/domain/homework.test.ts:101](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/homework.test.ts#L101) — метод: Moscow midnight/completion-clock cases; [frontends/pwa-vue/src/PwaUpdateGate.test.ts:156](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/PwaUpdateGate.test.ts#L156) — метод: activation/gate/disabled/retry cleanup cases The two pending cases keep their distinct initial states and direct pending assertions. Homework keeps Moscow date and completion-time rules, exact group member identities/order, calendar/range guards. PWA keeps inert/dialog, disabled, activation-message wire event, timeout, reload and listener cleanup. Do not delete numeric countdown proof atcheckin:88–91 without replacing it with remainingSeconds(...)=60.

**Предложение:** DELETE only the seven listed redundant static-copy assertions. Preserve text used as an action selector or injected server/owner data marker.

**Cases/граница:** redundant-authored-copy-assertions

**Проверенный контракт:** [frontends/mobile-core/src/domain/checkin.ts:144](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/checkin.ts#L144) — метод: isPendingCheckin / eligibilityLabel; [frontends/mobile-core/src/domain/homework.ts:140](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/homework.ts#L140) — метод: formatHomeworkDate; [frontends/mobile-core/src/domain/homework.ts:196](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/homework.ts#L196) — метод: groupHomework; [frontends/pwa-vue/src/PwaUpdateGate.vue:219](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/PwaUpdateGate.vue#L219) — метод: disabled and aria-busy update button

### Telegram/Python

#### TB-D01 — Повторная проверка имени предмета в отмене пары

**Где:** [services/notification-bot/tests/test_lesson_cancelled.py:189](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_lesson_cancelled.py#L189) — методы: название: test_lesson_cancelled_resolves_subject_name; строка: 189; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** Один member, успешно разрешённое имя предмета; исполнение первой coroutine_factory; substring имени в send_message.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Замена текста произвольного имени на синоним в fixture не меняет ветвь. Безобидный перенос/параметризация общего happy path заставляет поддерживать второй практически одинаковый сценарий.

**Что остаётся:** [services/notification-bot/tests/test_lesson_cancelled.py:81](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_lesson_cancelled.py#L81) — метод: test_lesson_cancelled_message_contains_subject_name_and_date; сохранённый риск: Тот же один связанный студент, тот же resolved subject lookup, та же фабрика доставки, проверка subject substring плюс date. Иные строки subject/date не являются другой ветвью. Отдельной полезной гарантии нет. Ни этот метод, ни counterpart не доказывают активную central dispatch: это отдельно исправляется TB-R01.

**Предложение:** Удалить только метод :189–218. Сохранить :81 test_lesson_cancelled_message_contains_subject_name_and_date и перенести его полезный oracle при TB-R01: фактический subject и date должны остаться в доставленном сообщении.

**Cases/граница:** ONE_METHOD

**Проверенный контракт:** [services/notification-bot/bot/notifications/lesson_cancelled.py:34](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/lesson_cancelled.py#L34) — why: Один путь lookup→message_options для обеих fixtures

#### TB-D02 — Слабый дубль положительного JWT TTL

**Где:** [services/notification-bot/tests/test_jwt_redis_client.py:67](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_jwt_redis_client.py#L67) — методы: название: test_ttl_set; строка: 67; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** save(expires_in=900,defaultTTL604800) и ttl(key)>0.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Безобидное объединение same-state TTL cases сокращает повтор fixtures. Все полезные условия >0 уже следуют из существующего >604790 в том же состоянии.

**Что остаётся:** [services/notification-bot/tests/test_jwt_redis_client.py:78](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_jwt_redis_client.py#L78) — метод: test_ttl_uses_max_of_expires_in_and_default; сохранённый риск: Одинаковые expires_in900/default604800, известный положительный telegram ID; ttl>604790 сильнее ttl>0. UID333/444 не порог и не другая auth/state ветвь. Нет отдельной гарантии.

**Предложение:** Удалить только метод :67–74. test_ttl_uses_max_of_expires_in_and_default :78 остаётся; дополнение верхней границы в TB-R07 также не отменяет положительный TTL.

**Cases/граница:** ONE_METHOD

**Проверенный контракт:** [services/notification-bot/bot/services/jwt_redis_client.py:39](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/services/jwt_redis_client.py#L39) — why: Один max(expires_in,default) путь

#### TB-D03 — No-raise для неизвестного события уже присутствует в safe-log сценарии

**Где:** [services/notification-bot/tests/test_event_dispatcher.py:91](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_event_dispatcher.py#L91) — методы: название: test_dispatch_unknown_event_type_does_not_raise; строка: 91; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** Вызвать dispatch неизвестного event_type и завершиться без исключения.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Безобидное изменение логирования/объединение unknown fixtures требует поддерживать два одинаковых входа. Любое исключение при unknown также уронит surviving test.

**Что остаётся:** [services/notification-bot/tests/test_event_dispatcher.py:98](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_event_dispatcher.py#L98) — метод: test_dispatch_unknown_event_type_logs_debug; сохранённый риск: Тот же unknown branch public dispatch, не перехватывает исключения; дополнительно проверяет безопасный debug. Различные unknown строки не отдельные initial states. Нет отдельной гарантии.

**Предложение:** Удалить только :91–94, оставить :98–104, включая запрет копирования непроверенного event_type в лог.

**Cases/граница:** ONE_METHOD

**Проверенный контракт:** [services/notification-bot/bot/consumers/event_dispatcher.py:472](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/consumers/event_dispatcher.py#L472) — why: Неизвестный тип возвращает None с безопасным debug

#### TB-D04 — Частичный дубль common-menu контракта

**Где:** [services/notification-bot/tests/test_callback_prefs.py:35](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_callback_prefs.py#L35) — методы: название: test_main_keyboard_contains_settings_and_login; строка: 35; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** В default keyboard присутствуют Settings и Login.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Изменение расположения рядов/однократная правка меню не должна требовать отдельной неполной проверки двух кнопок. Если пропадёт одна из них, surviving exact common4 test упадёт.

**Что остаётся:** [services/notification-bot/tests/test_role_menu_reads.py:101](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_role_menu_reads.py#L101) — метод: test_menu_is_union_of_current_grants_with_common_four_first_once; сохранённый риск: Включая пустые roles: точный flatten common4 [Credentials,Links,Login,Settings], отсутствие extras, порядок и уникальность; все полезные условия удаляемого subset. Нет отдельной гарантии. Не предлагается удалять common4/role/security tests.

**Предложение:** Удалить только :35–38. Сохранить exact common4, first-once, role union/no extra actions в test_role_menu_reads.py:101; это принятый продуктовый контракт.

**Cases/граница:** ONE_METHOD

**Проверенный контракт:** [services/notification-bot/bot/handlers/prefs.py:67](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/handlers/prefs.py#L67) — why: Общий builder меню; [services/notification-bot/bot/services/bot_menu.py:114](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/services/bot_menu.py#L114) — why: Фильтрация current grants

### E2E: пользовательские сценарии

#### E2E-D001

**Где:** [tests/e2e/specs/headman-mark.spec.ts:33](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/headman-mark.spec.ts#L33) — до строки: 68.

**Сейчас:** describe.skip постоянно отключает оба сценария файла; первый описывает явно исключённый из web-panel scope bulk-mark UI, второй зависит от изменений предыдущего случая и ищет только counter element.

**Проблема:** Активной гарантии нет. Снятие skip приведёт к проверке отсутствующих lesson-card/bottom-sheet/counter и зависимости от другого теста; в инвентаре остаются две вводящие в заблуждение декларации.

**Что остаётся:** No active production guarantee is removed. Git history retains the old scenario. Current PWA individual-mark, capability and history cases are distinct guarantees and are not asserted to replace bulk marking or a WebSocket counter.

**Предложение:** DELETE всего tests/e2e/specs/headman-mark.spec.ts с кейсами «headman marks all students present via batch endpoint» и «headman can review marks and see WebSocket live-update». Сохраняемый эквивалент не требуется, поскольку оба кейса пропущены во всех projects. Старый сценарий остаётся в Git history; действующие PWA/TMA individual-mark, capability и history кейсы оставить, не объявляя их заменой bulk/WebSocket гарантии. Gates и timeouts не менять.

**Cases/граница:** headman marks all students present via batch endpoint; headman can review marks and see WebSocket live-update whole file; both permanently skipped declarations

**Проверенный контракт:** frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.ts:41-58 (template editor and one-off creation); frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.ts:84-176 (actual page controls); frontends/web-panel/src/app/features/headman/dashboard/headman-dashboard.component.ts:21-30 (read-only dashboard purpose)

#### E2E-D002

**Где:** [tests/e2e/specs/cloud-accepted-auth-security.spec.ts:823](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/cloud-accepted-auth-security.spec.ts#L823); [tests/e2e/specs/cloud-accepted-auth-security.spec.ts:824](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/cloud-accepted-auth-security.spec.ts#L824); [tests/e2e/specs/cloud-accepted-auth-security.spec.ts:832](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/cloud-accepted-auth-security.spec.ts#L832); [tests/e2e/specs/cloud-accepted-auth-security.spec.ts:839](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/cloud-accepted-auth-security.spec.ts#L839).

**Сейчас:** Три RGB assertions фиксируют точный цвет поверхности, отдельная sentence assertion — информационную формулировку; выбор preview theme и его поведение проверяются соседними assertions.

**Проблема:** Доступный светлый цвет с другим RGB или перефразированная preview-подсказка ломают password/session-revocation сценарий при неизменных действиях и поведении темы.

**Что остаётся:** Same test: lines 821-822,825,828,830-831,833-838,840-845 preserve selected preview state, persisted choice, media response, global-dark boundary and return state; lines 854-909 preserve password change/old bearer rejection. The owner does not require an exact RGB or the note's wording. Retain all theme behavior and accessibility checks; no whole appearance scenario deletion is proposed.

**Предложение:** DELETE только четыре указанных cosmetic assertions. Сохранить tests/e2e/specs/cloud-accepted-auth-security.spec.ts::«own profile security changes password and replacement login rejects old authority @cloud-accepted»: :821–822/825/828/830–831/833–838/840–845 проверяют выбранный preview, persistence, media response, global-dark и возврат; :854–909 — смену пароля и отказ старому bearer. Точный RGB/текст не требуют отдельного survivor; все theme/a11y проверки, восстановление page/media и реальные login/HTTP/DB пути остаются.

**Cases/граница:** own profile security changes password and replacement login rejects old authority @cloud-accepted four cosmetic assertions only

**Проверенный контракт:** frontends/mobile-core/src/features/profile/AppearanceScreen.vue:22-39 (preview controller deliberately has no document target); frontends/mobile-core/src/features/profile/AppearanceScreen.vue:54-57 (resolved preview theme); frontends/mobile-core/src/features/profile/AppearanceScreen.vue:98-113 (informational preview note); frontends/mobile-core/src/features/profile/profile-screen.pcss:25 (semantic surface token)

#### E2E-D003

**Где:** [tests/e2e/specs/mobile-real-server.spec.ts:1741](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/mobile-real-server.spec.ts#L1741).

**Сейчас:** Требует, чтобы readonly-marker ожидающей автоматической неявки был именно SPAN; соседние assertions связывают его с точным pending ticket и проверяют доступное состояние.

**Проблема:** Замена пассивного SPAN на равнозначный неинтерактивный элемент ломает кейс без изменения autoAbsent, pending, accessible label или полномочий записи.

**Что остаётся:** Same test: lines 1729-1735 verify real journal status, autoAbsent, pendingTicket and exact ticketId; lines 1736-1740 verify accessible state and visible pending explanation; line 1742 excludes action buttons. Earlier lines 1179-1188 deny teacher PUT and read back unchanged report. A particular passive HTML tag is not an independent production guarantee. Keep readonly/action denial and actual data checks.

**Предложение:** DELETE только tag assertion. Сохранить tests/e2e/specs/mobile-real-server.spec.ts::«HEADMAN marks the real roster and TEACHER reads without acquiring write permission»: :1729–1735 проверяют реальные journal status/autoAbsent/pendingTicket/ticketId, :1736–1740 — accessible state и объяснение, :1742 исключает action buttons; :1179–1188 запрещают teacher PUT и подтверждают неизменный report. Отдельный survivor для имени пассивного HTML-тега не требуется; seed, browser, active-lesson и foreign-attachment пути сохраняются.

**Cases/граница:** HEADMAN marks the real roster and TEACHER reads without acquiring write permission one DOM tag assertion

**Проверенный контракт:** frontends/mobile-core/src/features/teacher/TeacherJournalTable.vue:162-179 (passive pending marker); services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/TeacherApiModels.java:83-94 (status/pendingTicket/autoAbsent/ticketId data contract)

#### E2E-D004

**Где:** [tests/e2e/specs/mobile-real-server.spec.ts:5306](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/mobile-real-server.spec.ts#L5306) — до строки: 5309.

**Сейчас:** JSON.stringify equality повторяет непосредственно предшествующий isDeepStrictEqual для тех же lesson/roster и дополнительно фиксирует порядок вставки свойств объекта.

**Проблема:** Serializer/DTO может безопасно переставить именованные JSON-поля: значения и порядок roster-массива сохранятся, structural equality пройдёт, string equality упадёт. Soft structural failure уже делает Playwright-кейс неуспешным.

**Что остаётся:** Same test: lines 5297-5305 isDeepStrictEqual checks every lesson/roster field and roster array order; lines 5285-5292 reject both old bearers/refresh proofs; lines 5310-5311 retain closed browser owner. Object key serialization order is not a user data guarantee. This proposal does not remove ordered array comparisons or change soft/hard assertion policy elsewhere.

**Предложение:** DELETE только дублирующее serialized-object equality. Сохранить tests/e2e/specs/mobile-real-server.spec.ts::«SUSPENDED STUDENT retains active TEACHER access until UI logout-all revokes both sessions»: :5297–5305 сверяют все lesson/roster значения и порядок массива, :5285–5292 отвергают оба старых bearer/refresh proof, :5310–5311 сохраняют closed browser owner. Отдельный survivor для порядка ключей не нужен; независимые cookie jars, новый реальный teacher read и остальные ordered array/soft-hard правила оставить.

**Cases/граница:** SUSPENDED STUDENT retains active TEACHER access until UI logout-all revokes both sessions one duplicate serialized-object equality assertion

**Проверенный контракт:** services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/TeacherApiModels.java:80-81 (named lesson/roster fields); services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherApiController.java:68-72 (actual typed read response)

#### E2E-D005

**Где:** [tests/e2e/specs/role-headman.spec.ts:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/role-headman.spec.ts#L37) — до строки: 43.

**Сейчас:** После loginAs(TEST_USERS.student) проверяет URL /student/schedule и /student/stats; другой существующий кейс с тем же аккаунтом/helper уже проверяет оба пути, /student/excuses и axe каждого экрана.

**Проблема:** Одинаковый grant общего STUDENT+headman аккаунта и допуск browser routes проверяются дважды без отдельного состояния, действия или платформы; поломку каждого fallback-пути уже обнаружит survivor.

**Что остаётся:** tests/e2e/specs/role-student.spec.ts:14-24, can access student-only paths: identical beforeEach account/helper and both schedule/stats paths, plus excuses and axe.; tests/e2e/fixtures/auth.ts:12-21: identical loginAs navigation/login/landing behavior.; tests/e2e/playwright.config.ts:51-63: both files share Chromium/WebKit projects and both are selected by mobile-chrome testMatch. Exact survivor identified above; its assertions currently include every guarantee of the removed case. E2E-R002 strengthens that survivor afterward, without restoring a duplicate.

**Предложение:** DELETE только «can also access student paths (fallback)». Сохранить tests/e2e/specs/role-student.spec.ts::«can access student-only paths» (:14–24): тот же loginAs из tests/e2e/fixtures/auth.ts:12–21, свежий page/context, оба URL плюс excuses/axe. tests/e2e/playwright.config.ts:51–63 задаёт обоим файлам те же Chromium/WebKit/mobile-chrome projects. HEADMAN-only и denial кейсы оставить; E2E-R002 усиливает существующий survivor без восстановления дубля.

**Cases/граница:** can also access student paths (fallback) whole duplicate fallback test case only

**Проверенный контракт:** frontends/web-panel/src/app/core/auth/student.guard.ts:10-13 (STUDENT admission includes headman account); frontends/web-panel/src/app/features/student/student.routes.ts:17-22,41-46 (same routes)

### E2E: fixtures и controls

#### EF-D02

**Где:** [tests/e2e/fixtures/semester-lifecycle-ui.control.mjs:152](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/semester-lifecycle-ui.control.mjs#L152) — methodsCases: checkUi L74–153; final internal assertion sequence L152..

**Сейчас:** Последние три вызова model expect обязаны идти count → icon → enabled-action.

**Проблема:** В реальном caller L2228–2240 поменять местами ожидание иконки и enabled action. Все три постусловия, exact owned semester и race handling сохранятся, но L152 control упадёт.

**Что остаётся:** [tests/e2e/fixtures/semester-lifecycle-ui.control.mjs:145](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/semester-lifecycle-ui.control.mjs#L145) — сохранённая гарантия: observedVisible → детерминированное удаление transient card → resume → await checking L145–151 сохраняет настоящую race sensitivity; [tests/e2e/fixtures/semester-lifecycle-ui.control.mjs:182](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/semester-lifecycle-ui.control.mjs#L182) — сохранённая гарантия: восемь negative cases ownCount0/2, wrong icon/action, disabled, click error, persistent click timeout, wrong restore postcondition L182–192 требуют все внешние terminal assertions Порядок независимых terminal assertions не является наблюдаемым контрактом. Нужны все постусловия после controlled transient-card removal, а не порядок вызова model expect.

**Предложение:** 1positive+15negative terminal controls,4positive+8negative UI controls; final replay path/key atL67–71; deterministic actual removal handshake.

**Cases/граница:** checkUi L74–153; final internal assertion sequence L152. Только assert.deepEqual(actions.slice(-3), [owned-count,owned-icon,owned-action]); не удалять observable postconditions или ordering handshake.

**Проверенный контракт:** [tests/e2e/fixtures/semester-lifecycle.spec.ts:2202](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/semester-lifecycle.spec.ts#L2202) — контракт: actual transient status card continuation and owned terminal UI; [tests/e2e/fixtures/semester-lifecycle.spec.ts:2228](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/semester-lifecycle.spec.ts#L2228) — контракт: все три owned terminal UI postconditions; [frontends/mobile-core/src/features/admin-semester/AdminSemesterScreen.vue:1374](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/admin-semester/AdminSemesterScreen.vue#L1374) — контракт: settlement подтверждает current state/list, затем удаляет pending command

## Предлагаю переписать

Проверять наблюдаемый результат при том же согласованном поведении. Для endpoint/transaction/authz использовать настоящий HTTP и изолированные PostgreSQL/Mongo с migrations, когда их семантика существенна; дешёвые независимые parser/state/clock/generation units сохраняются. Предложения не разрешают менять продуктовый контракт или gates.

### Academic: предметы, задания, семестры и доменные правила

#### AD-R01

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityContractTest.java:74](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectAssignmentAuthorityContractTest.java#L74) — метод: createLocksEveryReferencedSemesterBeforeSubjectWriteAndCarriesFullTuple; проверяет: .

**Сейчас:** One mocked semester lock before subject.save; exact createInitialAssignmentsForNewSubject argument map/list.

**Проблема:** Значимый risk initial-subject admission versus semester mutation привязан к внутреннему call order. Не доказано, что настоящий PG writer сериализуется; existing initial-assignment tests prove rows, not this exact race. A different equivalent lock/atomic admission strategy fails InOrder. Conversely a nonlocking repository implementation can pass every mock assertion.

**Переписать так:** REWRITE Concurrent initial subject creation and semester-date change must never commit an assignment outside the final semester bounds or leave partial subject/type/assignment rows.

**Граница и сохранение:** Signed MockMvc POST subject and PUT semester, two transactions in isolated PostgreSQL; schedule adoption is a controlled remote seam. Accept either legitimate serialized winner and assert exact committed DB bounds/rows.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java:125](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java#L125) — symbol: initial semester locks precede subject write; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentAuthority.java:115](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentAuthority.java#L115) — symbol: atomic initial batch; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java:193](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java#L193) — symbol: referenced dates guard

#### AD-R02

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupServiceTest.java:521](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupServiceTest.java#L521) — метод: groupResponse_hasNoCodeField; проверяет: .

**Сейчас:** GroupResponse.getName() then getDeclaredField("code") must throw.

**Проблема:** Private field absence does not prove JSON shape: a getCode()/@JsonProperty serializer can leak code without a field, while an ignored private code field harmlessly breaks test. Add @JsonIgnore private internal code: wire remains stable but test fails. Add computed public getCode(): test may pass while JSON changes.

**Переписать так:** REWRITE Serialized public group response includes the expected name and excludes obsolete code property; canonical additive split fields remain allowed.

**Граница и сохранение:** Configured Jackson serialization unit or existing actual GET group response JSON; no DB necessary for serializer-only contract. Assert JSON members, not reflection.

**Проверенный контракт:** [services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/GroupResponse.java:13](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/GroupResponse.java#L13) — symbol: public response DTO/getters

#### AD-R03

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupRosterSnapshotTest.java:35](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/group/GroupRosterSnapshotTest.java#L35) — метод: guardRosterAndLocalRoleLabelsResolveBeforeSameRepeatableReadSnapshotEnds; проверяет: .

**Сейчас:** Fake AbstractPlatformTransactionManager activates Spring metadata; callbacks append actor/group/roster/labels in exact order; checks returned response.

**Проблема:** Empty doBegin/doCommit has no PG snapshot. The test accurately proves proxy annotation metadata, but exact steps list overfits orchestration and cannot establish the coherent roster/read-fence snapshot risk. Reorder two independent fence checks or batch role-label assembly within one valid snapshot: exact steps fails. A changed DB isolation implementation can remain invisible to fake manager.

**Переписать так:** REWRITE A concurrent committed membership/role transition must not make one roster response mix old guard scope with new members/labels; a later read sees the committed coherent state.

**Граница и сохранение:** Keep cheap annotation metadata check if useful, but move snapshot-behavior claim to isolated PostgreSQL with a writer between guard and roster reads; observe actual endpoint/result. Do not weaken existing pending-group fail-closed method.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java:494](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java#L494) — symbol: read-only REPEATABLE_READ roster+fence+role-label assembly

#### AD-R04

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java:662](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java#L662) — метод: updateHomework_succeeds_whenAuthor; проверяет: ; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java:699](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java#L699) — метод: deleteHomework_succeeds_whenAuthor; проверяет: .

**Сейчас:** Update verifies title/description/link and exact save/event calls; delete archives in-memory object and forbids delete(), using null production coordinator/persistence.

**Проблема:** Полезные content fields/creator-retention assertions привязаны к source-compatible fixture-only branch. Нельзя просто удалить: title/description/link together are not all asserted by the inspected surviving production scenario. Refactor/remove fixture compatibility path while current edit coordinator behaves identically: tests fail; conversely break production coordinator field mapping while fixture branch remains intact: these tests can pass.

**Переписать так:** REWRITE Real authorized creator changes title/description/link, increments revision, preserves creator/completion/history, then archives without physical content deletion or duplicate action events.

**Граница и сохранение:** One signed HTTP/isolated-PG lifecycle scenario using actual HomeworkEditCoordinator and HomeworkPublicationPersistence; Schedule remote transport may remain a controlled seam. Preserve every listed field/creator assertion.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java:925](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java#L925) — symbol: editCoordinator path/fallback; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java:1029](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java#L1029) — symbol: fixture-only archive versus live publication path

#### AD-R05

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkMigrationIT.java:38](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkMigrationIT.java#L38) — метод: lessonNumberColumnSupportsAcceptedDateOnlyMode; проверяет: .

**Сейчас:** information_schema nullable=YES plus pg_get_constraintdef text contains binding_mode, DATE, lesson_number IS NULL and LESSON.

**Проблема:** Строки constraint definition не доказывают семантику: выражение может включать все substrings и всё равно принимать недопустимую комбинацию (including SQL CHECK null semantics). Equivalent normalized/renamed constraint also makes introspection brittle. Rewrite CHECK with logically equivalent expression/constraint name: test breaks although valid DATE/LESSON rows behave identically. Keep the same words in an OR TRUE check: text can pass.

**Переписать так:** REWRITE DATE accepts no lesson number; LESSON accepts the permitted numbered range; invalid mode/number combinations are rejected by actual DB without partial writes.

**Граница и сохранение:** Isolated PostgreSQL direct inserts using an otherwise-valid owned homework fixture. Assert specific constraint/SQLSTATE for each invalid combination; keep historical column/index markers until their separate requirement is decided.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/resources/db/migration/V45\_\_homework_edit_history.sql:1](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/resources/db/migration/V45__homework_edit_history.sql#L1) — symbol: nullable lesson_number and binding-mode CHECK; [services/academic-service/academic-app/src/main/resources/db/migration/V13\_\_homework_lesson_binding.sql:12](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/resources/db/migration/V13__homework_lesson_binding.sql#L12) — symbol: original date/number binding

#### AD-R06

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectSchemaIT.java:40](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectSchemaIT.java#L40) — метод: groupIdIsNotNull; проверяет: ; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectSchemaIT.java:51](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectSchemaIT.java#L51) — метод: groupIdHasForeignKey; проверяет: ; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectSchemaIT.java:63](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/subject/SubjectSchemaIT.java#L63) — метод: findByGroupId_returnsOnlyThatGroupSubjects; проверяет: .

**Сейчас:** Two incomplete-target JPA inserts expect any DataIntegrityViolationException; third creates two subjects in the same group and allMatches that group without an explicit foreign-group control row.

**Проблема:** NOT NULL may be enforced by Hibernate before SQL and therefore does not prove DB constraint. Generic integrity exception does not identify expected group constraint. The filter test has no deliberate competing group; a missing filter can pass on an all-own fixture. Current entity otherwise supplies required fields; no current unrelated-column failure is asserted. Remove DB NOT NULL while leaving JPA nullable=false: unit can still reject before PG. Remove query filtering in a fixture containing only own rows: allMatch still passes.

**Переписать так:** REWRITE Real DB refuses null group and nonexistent group for the intended reason; group query includes its exact owned rows and excludes a specifically seeded foreign-group row.

**Граница и сохранение:** Isolated PostgreSQL direct SQL for null/FK cases (SQLSTATE23502+column group_id;23503+fk_subjects_group), separate transactions for failed writes; repository query fixture with two groups and distinct subject IDs.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/resources/db/migration/V12\_\_subjects_group_id.sql:13](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/resources/db/migration/V12__subjects_group_id.sql#L13) — symbol: group_id NOT NULL and fk_subjects_group; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Subject.java:31](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Subject.java#L31) — symbol: JPA group nullable=false; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/SubjectRepository.java:30](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/SubjectRepository.java#L30) — symbol: findByGroupId

#### AD-R07

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterServiceTest.java:163](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/semester/SemesterServiceTest.java#L163) — метод: createAndUpdate_typedSemesterNamesUseTheExplicitAcademicYear; проверяет: .

**Сейчас:** Fixed 2026-09-01..2027-01-31 autumn then2027 spring naming/classification; class constructor injects Clock.system(Europe/Moscow).

**Проблема:** Calendar time is an unowned input to a naming test. After2027-01-31 the initial create range is completed and create rejects before any naming assertion; its fixed existing autumn would also be completed for update. Run unchanged source on2027-02-01: the first create reaches completed-range guard; no production regression is needed.

**Переписать так:** REWRITE Explicit academic year and semester type deterministically generate autumn/spring labels and classification without client-name authority.

**Граница и сохранение:** Cheap service unit with Clock.fixed at a stated2026 date and all fixture dates derived/consistent. No DB/new endpoint test needed for this naming rule; keep dedicated Moscow/time guards.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java:118](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java#L118) — symbol: create date validation/naming; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java:166](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java#L166) — symbol: completed-semester date guard

#### AD-R08

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkPaginationLinkTest.java:83](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkPaginationLinkTest.java#L83) — метод: directReadsKeepPaginationWithoutAThreadBoundServletRequest; проверяет: .

**Сейчас:** Asserts complete self/next URL strings including query-parameter order.

**Проблема:** Query order is not the routing/filter/pagination contract. Companion method already parses URI+query for precise semantic assertions. Canonicalize query parameters alphabetically: same relative path/filter/page links, literal equality fails.

**Переписать так:** REWRITE Without RequestContextHolder, self/next remain public relative URLs with exactly group11,semester22,archived=true,page0/1,size2 and no authority leak.

**Граница и сохранение:** Keep cheap real Spring link-generation unit; parse URI and query maps as in companion test. DB N/A.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkController.java:82](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkController.java#L82) — symbol: publicPageLink relative URI with canonical page filters

### Academic: identity, eligibility и authority

#### AA-R01

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/AcademicAssignmentGrpcContractTest.java:146](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/AcademicAssignmentGrpcContractTest.java#L146) — метод: lifecycleRpcKeepsFrozenTextCodesAfterDomainTyping.

**Сейчас:** Для каждого AssignmentLifecycle ожидаемый targetLifecycleState равен state.getCode(); lessonType=lecture.

**Проблема:** Oracle использует тот же production getCode(), что adapter. Если ACTIVE.getCode() станет active, wire compatibility нарушена, тест останется PASS.

**Переписать так:** observable: Frozen wire literals ACTIVE, PREPARED, CLOSED; lesson_type=lecture.; граница: Оставить компактный adapter unit с real proto и независимой таблицей (AssignmentLifecycle.ACTIVE,"ACTIVE"), (PREPARED,"PREPARED"), (CLOSED,"CLOSED"); не получать expected через name()/getCode().; БД: DB не нужна: риск здесь именно enum→wire; реальные assignment IT сохранить.; negativeControl: Статически: изменение только одного getCode() должно нарушить literal expectation.

**Cases/граница:** ACTIVE -> ACTIVE; PREPARED -> PREPARED; CLOSED -> CLOSED

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentLifecycle.java:6](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentLifecycle.java#L6); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java:845](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java#L845)

#### AA-R02

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/CampusMapGrpcReadTest.java:69](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/CampusMapGrpcReadTest.java#L69) — метод: cancelledServerStreamStopsBeforeCompletion.

**Сейчас:** При последовательности isCancelled=false,false,false,true отправляется ровно один chunk и нет onCompleted/onError.

**Проблема:** Отмена привязана к четвертому внутреннему poll. Добавление безвредной проверки cancellation до readAsset/после чтения меняет число chunks и ломает тест при сохранном контракте отмены.

**Переписать так:** observable: После принятия первого chunk клиент отменяет stream; новых chunks и terminal emission нет.; граница: Observer/fake содержит AtomicBoolean cancelled; onNext первого chunk выставляет его true. isCancelled читает состояние, а не thenReturn по счетчику вызовов. Сохранить chunk-bound/offset отдельный тест.; БД: Не нужна: serialization/stream-control adapter unit; content port можно подменить.; negativeControl: Игнорирование cancellation в цикле должно дать дополнительные chunks и провал.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java:1253](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java#L1253); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java:1296](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java#L1296)

#### AA-R03

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L50) — метод: start (fixture; все 17 @Test методов класса); [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:186](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L186) — метод: lostAcceptedAndCompletionAcknowledgementsResolveSameJobAndOneAudit; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:221](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L221) — метод: simultaneousClaimsAcrossWorkersNeverExceedTwoGlobalLeases; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:250](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L250) — метод: retryBackoffIsFiveThenTenAndThirdAbandonedLeaseTerminatesSameJob; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:278](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L278) — метод: staleLeaseCannotPublishAfterCrashReclaimAndLocalRevocationBlocksReady; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:297](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L297) — метод: personalBoundAndExpiryReclamationUseOnlyAcademicQueue; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:313](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L313) — метод: outboxFailureRollsBackArtifactAuditAndCommittedMetricThenSameLeaseRetries; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:356](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L356) — метод: globalBacklogIsBoundedAcrossDifferentUsersAndSessions; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:377](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L377) — метод: currentGroupArchiveAndAmbiguousTeacherSemesterDenyLocalReportAuthority; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:412](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L412) — метод: delegatedStatsPermissionIsFreshAndNeverAuthorizesGroupComposition; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:455](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L455) — метод: absentActiveSemesterIsFrozenAndCannotSilentlyChangeTheQueuedSelector; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:487](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L487) — метод: teacherReadyArtifactRequiresEveryCapturedCurrentAssignmentAfterScopeShrinks; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:529](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L529) — метод: peerPendingIdentityOperationBlocksRegistryArtifactUntilPublicationCompletes; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:552](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L552) — метод: availabilityGuardsDenyArtifactReadByExactGroupActorAndWholeRegistryUntilRemoved; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:604](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L604) — метод: retainedAvailabilityGuardPreventsReadyPublicationAndSameLeaseCompletesAfterRelease; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:639](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L639) — метод: reconnectedServiceLoadsSameArtifactAndCleanupPreservesOtherLiveWork; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:660](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L660) — метод: shortRetentionCannotCreateInvisibleRetryAndStateRejectsUnknownOrOrdinal; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java:679](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/AcademicExportJobsIT.java#L679) — метод: ownerMetricsSeparateDurableBackoffAndDueWorkWithoutIdentityLabels.

**Сейчас:** Настоящие PG transactions, advisory locks, lease/CAS, rollback artifact/audit/outbox и локальная report authority; очередь создана V2.

**Проблема:** Остальные 12 таблиц вручную урезаны: academic_profiles без имен/status constraints; eligibility без PK/FK/status guards; operations только id+phase. Это позволяет fixture и SQL проходить при несовместимости с полным migrated schema и скрывает transaction/constraint взаимодействия. Не считать текущий suite полным owner-schema acceptance.

**Переписать так:** observable: Те же 17 гарантий durable state/audit/outbox/lease/recovery на настоящей owner schema.; граница: Оставить repository/LocalReportAuthority DB component boundary, заменить ручной CREATE TABLE полным Flyway academic-migration в собственном disposable Academic PG и валидными synthetic rows; runtime principal отделить от migrator. Сохранить все rollback/fence/limits/retry assertions; ассистентский port mock допустим для отдельной композиционной ветки.; БД: Изолированный PG, полный текущий migration chain; не добавлять HTTP к чистому repository CAS suite.; negativeControl: Несовместимое обязательное поле/constraint в owner migration должно выявляться подготовкой или записью fixture, а не обходиться handcrafted table.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/ExportJobRepository.java:40](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/ExportJobRepository.java#L40); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/ExportJobRepository.java:124](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/ExportJobRepository.java#L124); [services/academic-service/academic-app/src/main/resources/db/academic-migration/V1\_\_academic_identity_foundation.sql:4](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/resources/db/academic-migration/V1__academic_identity_foundation.sql#L4); [services/academic-service/academic-app/src/main/resources/db/academic-migration/V1\_\_academic_identity_foundation.sql:25](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/resources/db/academic-migration/V1__academic_identity_foundation.sql#L25); [services/academic-service/academic-app/src/main/resources/db/academic-migration/V2\_\_academic_export_jobs.sql:1](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/resources/db/academic-migration/V2__academic_export_jobs.sql#L1)

#### AA-R04

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainEventsTest.java:26](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainEventsTest.java#L26) — метод: archivingHeadmanRetainsPersonalRevocationsWithAcceptedActorAndOneHelperEvent.

**Сейчас:** Два personal events REVOKED с accepted actor, один helper event/один DashboardInvalidated и receipt revision=3.

**Проблема:** JdbcTemplate answer выбирает строки по SQL contains и сам делает activeHelper=false на UPDATE headman_assistants; таким образом сохранность/дедупликация revoked state заданы двойником, не проверены. Alias/CTE/refactor SQL ломает setup, а WHERE/affected-row ошибка может остаться незамеченной.

**Переписать так:** observable: Архив headman закрывает membership, helper становится inactive, один helper revoke + один headman revoke с actor=99, committed revision ровно следующий; dashboard invalidation не дублируется.; граница: Real DomainWriter в transaction на migrated Academic PG с реальными helper/headman/history rows; event collector/listener можно оставить для application event contract, durable outbox проверить там, где заявляется outbox. Не навязывать порядок helper/headman, если порядок не часть event contract.; БД: Собственный migrated Academic PG; реальный Auth нужен только при переносе проверки на целый archive endpoint.; relatedKeep: [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/notification/NotificationHeadmanArchiveIT.java:31](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/notification/NotificationHeadmanArchiveIT.java#L31) — метод: userHeadmanCascadesEmitOneRealAssistantRevocation; negativeControl: Убрать UPDATE assistant или неверно ограничить WHERE: persisted helper остается active и проверка падает.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java:276](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java#L276); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java:339](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java#L339); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java:557](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java#L557)

#### AA-R05

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityMembershipTransitionsTest.java:30](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityMembershipTransitionsTest.java#L30) — метод: keepsSuspendedMembershipAndOnlyReopensClosedTerminalHistory; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityMembershipTransitionsTest.java:73](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityMembershipTransitionsTest.java#L73) — метод: assigningSameHeadmanKeepsExistingAssistants; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityMembershipTransitionsTest.java:116](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityMembershipTransitionsTest.java#L116) — метод: profilePatchWithUnchangedArchivedStudentDoesNotStartReassignment; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityMembershipTransitionsTest.java:168](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityMembershipTransitionsTest.java#L168) — метод: explicitArchivedStudentReassignmentStillOpensNewDatedMembership.

**Сейчас:** Число SQL UPDATE history/INSERT, позиции SQL args date/reason, отсутствие SQL assistant/history при no-op, receipt revisions.

**Проблема:** Большой SQL-dispatch fake содержит альтернативную модель rows и всегда update=1. Проверки statements.startsWith/contains и вставочных аргументов привязаны к форме SQL: объединение UPDATE в CTE или безопасный SELECT помощников ломает тест; неверный WHERE/дата в persisted intervals не доказывается.

**Переписать так:** observable: Фактические интервалы student_group_history, их IDs/dates, активность assistants и профильные значения после команды; сохранность существующих history IDs для suspension/no-op, ровно один новый interval для reactivation/reassignment.; граница: Преобразовать в DomainWriter DB component tests с real migrations/transaction; сохранить четыре CSV cases ACTIVE→SUSPENDED(open=true,unchanged), SUSPENDED→ACTIVE(open=true,unchanged), SUSPENDED→EXPELLED(open=true,close), EXPELLED→ACTIVE(open=false,new interval), и три отдельных метода no-op/profile/reassignment. Проверять persisted history, а не число запросов.; БД: Изолированный migrated Academic PG; prepared Auth protocol input остается controlled proto, поскольку проверяется local apply.; negativeControl: No-op touch/close history или закрытие не того user_id должны менять сравниваемые durable rows и падать.

**Cases/граница:** ACTIVE,SUSPENDED,true,0,0; SUSPENDED,ACTIVE,true,0,0; SUSPENDED,EXPELLED,true,1,0; EXPELLED,ACTIVE,false,0,1

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java:225](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java#L225); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java:465](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java#L465); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java:488](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java#L488)

#### AA-R06

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityProfileProjectionTest.java:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityProfileProjectionTest.java#L37) — метод: existingProfileCommandReadsOnlySelectedColumnsAndPreservesScalarStatus.

**Сейчас:** Directory SUSPENDED сохраняется в profile command при ACTIVE Auth grant; expected academic revision=2 и desired ACTIVE grant.

**Проблема:** Тест сам парсит SELECT до буквального FROM academic_profiles и split(",") для CachedRowSet. Alias, qualified column, CTE или проекция выражения сохраняют JDBC результат, но ломают искусственный SQL parser. Риск пропущенной колонки нужно доказывать настоящим ResultSet.

**Переписать так:** observable: Производимый command сохраняет directory_status=SUSPENDED и ACTIVE grant при profile edit; target revision остается2.; граница: Заменить только SQL-parsing JdbcTemplate fake реальным JDBC над migrated Academic PG; controlled AuthIdentityDataClient и capture AcceptedCommand допустимы для отдельного command-producer seam. Можно вместо capture использовать полный profile HTTP flow с обоими owner DB.; БД: Собственный Academic PG + migrations; full endpoint вариант требует real Auth/TLS и двух owner DB.; negativeControl: Если реальный SELECT не содержит directory_status, RowMapper падает на driver ResultSet без собственного parser.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java:635](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java#L635); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java:1061](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java#L1061)

#### AA-R07

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/RedisHeadmanRateLimiterTest.java:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/RedisHeadmanRateLimiterTest.java#L50) — метод: firstCallSetsExpire; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/RedisHeadmanRateLimiterTest.java:62](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/RedisHeadmanRateLimiterTest.java#L62) — метод: subsequentCallSkipsExpire; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/RedisHeadmanRateLimiterTest.java:99](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/RedisHeadmanRateLimiterTest.java#L99) — метод: differentUserIdsDifferentKeys.

**Сейчас:** Проверяются вызовы INCR по rl:headman:<user>: и EXPIRE только на первом increment.

**Проблема:** Эти проверки закрепляют Redis primitives/key layout. Переход к атомарному Lua или другой приватной key layout сохраняет общий quota/expiry/user-isolation контракт, но ломает все три теста. Mock не доказывает TTL и общую квоту двух instances.

**Переписать так:** observable: Два limiter instances делят лимит одного пользователя; другой пользователь имеет независимую квоту; window bounded и subsequent calls не продлевают его бесконечно.; граница: Один компактный real-Redis component сценарий с отдельным namespace/database и bounded window control. Проверять разрешенные/отклоненные вызовы и expiry поведения; Redis command sequence не фиксировать. Сохранить atLimitAllowed/overLimitRejected и fail-open+failure counter как полезные маленькие branch units.; БД: Отдельный disposable Redis; PG/HTTP не требуются.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/RedisHeadmanRateLimiter.java:65](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/RedisHeadmanRateLimiter.java#L65)

#### AA-R08

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterIT.java:106](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterIT.java#L106) — метод: validInternalToken_passesFilter_notReturning401; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterIT.java:115](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterIT.java#L115) — метод: validInternalToken_takesPrecedenceOverLegacyHeaders; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterIT.java:128](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterIT.java#L128) — метод: legacyHeaders_stillAccepted_whileDualModeOn.

**Сейчас:** На GET /academic/users итоговый status любой, кроме401.

**Проблема:** 404/403/500/503 удовлетворяют oracle. Token precedence также не доказывается: игнорирование signed ADMIN в пользу legacy STUDENT может дать403 и PASS.

**Переписать так:** observable: Signed canonical ADMIN успешно получает безопасную admin page; конфликтующие legacy поля не меняют signed actor. Legacy parsing в dual mode доставляет свой tuple downstream без выдачи signed claims.; граница: Два signed positive метода через real HTTP текущего RANDOM_PORT app + migrated split fixture/real Auth, expect200 и корректную страницу/authorized result. Legacy-only mapping проверить отдельным filter unit/controlled downstream chain с точными userId/role/group/headman и отсутствием signed claims; не требовать privileged admin endpoint200 от неподписанного legacy tuple.; БД: Real endpoint: два isolated owner PG с migrations и нужные Auth/session layers. Pure legacy parser/filter unit: DB не нужна.; preserve: Все invalid/expired/wrong-signature401 cases оставить; strict-mode legacy/no-header denial не ослаблять.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java:124](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java#L124); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java:137](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java#L137); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java:168](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java#L168)

#### AA-R09

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterStrictModeIT.java:54](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterStrictModeIT.java#L54) — метод: validInternalToken_strictMode_passes.

**Сейчас:** Strict-mode valid token дает любой status кроме401.

**Проблема:** Always404/500 или downstream authority outage дают PASS; принятие и нормальный результат не доказаны.

**Переписать так:** observable: Настоящий signed canonical ADMIN в strict mode получает200 и ожидаемый protected response; legacy/no-headers остаются401.; граница: Real HTTP на RANDOM_PORT с настоящим фильтром, Auth/session и текущей safe page response.; БД: Существующий isolated split fixture + migrations; никаких weakening security toggles.; negativeControl: Контролируемый отказ route/controller либо подмена success на500 должен приводить к FAIL.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java:137](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java#L137)

#### AA-R10

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/ReportDownloadTicketValidationTest.java:29](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/exportjobs/ReportDownloadTicketValidationTest.java#L29) — метод: rejectedOpaqueTicketIsNotEchoedByOwnerProblemResponse.

**Сейчас:** Standalone controller/advice с mock service→NOT_FOUND возвращает404, no-store, masked request instance без ticket sentinel.

**Проблема:** Без AcademicUserContextFilter/actual routing тест не проверяет bearer-free bypass exact43-char path и реальный owner redemption. Включение security filter или servlet mapping может вернуть другой body/echo sentinel, пока standalone setup остается PASS.

**Переписать так:** observable: Real anonymous GET по synthetic invalid opaque ticket возвращает owner404 problem, no-store и не содержит ticket ни в body/headers.; граница: HTTP→actual filter→controller→ExportJobService/repository на isolated migrated Academic PG; real Auth capability layer нужен при добавлении valid/revoked-ticket controls. Сохранить selector/hash/parser units этого файла.; БД: Изолированный migrated PG и необходимые owner layers; не дергать production/external provider.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java:76](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java#L76); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/AcademicReportTicketController.java:58](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/AcademicReportTicketController.java#L58); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/ExportJobExceptionAdvice.java:42](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/ExportJobExceptionAdvice.java#L42)

#### AA-R11

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/export/AdminRegistryExportsIT.java:323](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/export/AdminRegistryExportsIT.java#L323) — метод: reportPurposeUsersRenderKeepsCurrentFullLimitedAndRevocationBoundaries.

**Сейчас:** FULL report-purpose token не дает обычный registry read; exact lease render допускается; downgrade/revoke запрещают дальнейший render и LIMITED исключает telegram ID.

**Проблема:** MockMvc вручную задает servletPath у render calls. Тест сам конструирует ровно тот path, по которому filter выбирает privileged report branch; servlet/container context-path и normal routing не входят в доказательство.

**Переписать так:** observable: Те же purpose/lease/current authority/privacy результаты через настоящий URI/socket, без ручной подстановки servletPath.; граница: Real HTTP на уже запускаемом RANDOM_PORT app с real report filter, jobs repository, Auth capability service; сохранить FULL/LIMITED/revocation последовательность и exact/wrong lease controls. Не расширять этот scoped test до external document provider.; БД: Существующий owned split PG+full migrations/real Auth TLS fixture; RegistryDocumentClient double допустим только для незатрагиваемого external conversion.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java:69](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java#L69); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java:82](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/security/AcademicUserContextFilter.java#L82); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/AcademicReportRenderController.java:44](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/AcademicReportRenderController.java#L44)

#### AA-R12

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionQueryAdapterIT.java:87](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionQueryAdapterIT.java#L87) — метод: findAssignmentsBindsSemesterGroupsAndBoundsInDeclaredOrder.

**Сейчас:** Возвращаются два assignments двух выбранных групп в ожидаемом порядке.

**Проблема:** Все записи имеют один semester, обе группы выбраны, обе даты внутри диапазона. Потеря semester/group/date WHERE predicates может вернуть те же два rows и пройти. Название заявляет bounds, но отрицательных controls нет.

**Переписать так:** observable: Ровно подходящие IDs; другой semester/foreign group/полностью непересекающиеся интервалы исключены, half-open границы корректны.; граница: Оставить migrated-PG adapter component test. Добавить minimal excluded rows: same group other semester, nonselected group, valid_from==requested end, valid_until==requested start; положительные rows перекрывают каждую границу и null-end. Проверять результат, не declared SQL arg order.; БД: Текущий isolated Academic PG + полный migrations; controlled Auth ports здесь не участвуют.; negativeControl: Удаление каждого WHERE predicate должно добавлять соответствующий специально посеянный excluded ID.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/JdbcStudentProjectionQueryAdapter.java:53](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/JdbcStudentProjectionQueryAdapter.java#L53); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/JdbcStudentProjectionQueryAdapter.java:319](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/JdbcStudentProjectionQueryAdapter.java#L319)

#### AA-R13

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserRepositorySearchIT.java:186](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserRepositorySearchIT.java#L186) — метод: escapeLike_escapesUnderscoreAndPercentAndBackslash.

**Сейчас:** Private helper возвращает конкретную backslash-escaped строку для abc,50%,a_b,x\y.

**Проблема:** Согласованная смена ESCAPE_CHAR и LIKE encoding сохраняет буквальный поиск, но ломает oracle. Helper strings не доказывают, что реальная SQL LIKE clause использует тот же escape character.

**Переписать так:** observable: Поиск по литеральным %, \_, backslash и обычному тексту возвращает только правильный stored row, без wildcard expansion.; граница: В существующем migrated-PG repository suite заменить helper assertion параметризованными public matchesSearch→findAll результатами. Для % уже есть matchesSearch_escapesLikeWildcard; сохранить/переиспользовать его exact literal+wildcard controls, добавить a_b vs aXb и x\y vs xy. Не требовать конкретного escape encoding.; БД: Существующий isolated PG/migrations; HTTP не требуется для риска JPA/LIKE binding.; preserve: Чистые public enum/selector/login parsers не относятся к этому предложению.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java:25](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java#L25); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java#L37); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java:135](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java#L135)

#### AA-R14

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceConflictTest.java:95](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceConflictTest.java#L95) — метод: noConflictWhenNoExistingValues.

**Сейчас:** Actual Auth prepare доходит до synthetic INSERT interception; captured initial length/hash BC cost; mocked initialCredentials передается assembler; Academic User не хранит credential fields.

**Проблема:** Запись не исполняется: helper actualAuthCredentialInsert бросает sentinel до jdbc update. Схемный default password_changed=false только заявлен; explicit insertion этого же false нарушит doesNotContain(password_changed). Перестановка columns/args безопасно ломает produced[2]/[3]. После этого response enrichment снова задан mock.

**Переписать так:** observable: Успешный create возвращает разрешенную initial credential, в Auth persisted hash ее проверяет, password_changed=false, и Academic owner не содержит/не раскрывает credential storage.; граница: Заменить только этот success test настоящим HTTP create через existing isolated split fixture + Auth TLS/JDBC и public response. Проверять persisted Auth fields/credential matching и Academic ownership, не SQL text/column order. Существующие duplicate-outcome/input command unit tests сохранить.; БД: Два isolated owner PG с migrations и actual Auth identity operation; no provider/browser needed.; negativeControl: Явно дефектный password_changed default или отсутствие initial_password в durable row должно давать FAIL.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthIdentityOperationStore.java:514](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthIdentityOperationStore.java#L514); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthIdentityOperationStore.java:553](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthIdentityOperationStore.java#L553)

#### AA-R15

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java:132](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java#L132) — метод: matchesSearch_nullOrBlank_returnsNullSpec; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java:139](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java#L139) — метод: matchesRoleAndStatus_null_returnsNullSpec.

**Сейчас:** Factories возвращают Java null для absent/blank filters.

**Проблема:** Java null является выбранным представлением unrestricted predicate. Specification.unrestricted()/conjunction сохраняет query behavior и ломает оба теста; не проверяется ни один row.

**Переписать так:** observable: Null/empty/whitespace search и absent role/status не отфильтровывают fixture rows.; граница: Объединить эти cases с существующим UserRepositorySearchIT.matchesSearch_blank_returnsAll на real migrated PG; добавить whitespace и независимые null role/status cases и точный набор controlled IDs. Не утверждать null identity.; БД: Существующий isolated Academic PG/migrations; полноценный HTTP не нужен для predicate factories.; relatedKeep: [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserRepositorySearchIT.java:135](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserRepositorySearchIT.java#L135) — метод: matchesSearch_blank_returnsAll

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java#L37); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java:116](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java#L116); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java:124](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java#L124)

#### AA-R16

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java:152](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java#L152) — метод: archivedRoleStatusRemainsFilterableAsReadOnlyHistory.

**Сейчас:** TEACHER + ARCHIVED направляются в конкретный method с lowercase teacher/archived; stub всегда возвращает пустую страницу.

**Проблема:** Пустая page означает, что фактическая видимость archived role history, restored account и isolation от других roles/status не проверены. Альтернативный query с теми же rows ломает выбранный repository method assertion.

**Переписать так:** observable: Запрошенные archived TEACHER grants доступны для archived и restored accounts, другие statuses/roles исключены; count/pagination правильны.; граница: Расширить существующий UserSearchIT.archivedRegistryFilterIncludesArchivedAccountsAndRestoredRoleHistory нужной teacher role case и заменить этот forwarding unit; real HTTP через app/Auth layers, сравнить IDs/counts. Не удалять, пока teacher case не создан и проверен.; БД: Текущий isolated split PG+full migrations/real Auth fixture.; relatedKeep: [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserSearchIT.java:148](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserSearchIT.java#L148) — метод: archivedRegistryFilterIncludesArchivedAccountsAndRestoredRoleHistory

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java:268](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java#L268)

#### AA-R17

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/notification/NotificationAudienceSnapshotTest.java:60](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/notification/NotificationAudienceSnapshotTest.java#L60) — метод: batchesWholeAudienceAfterCatchupAndChecksDomainAgain; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/notification/NotificationAudienceSnapshotTest.java:116](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/notification/NotificationAudienceSnapshotTest.java#L116) — метод: unavailableSecondBatchNeverReturnsTheFirstBatch.

**Сейчас:** 201 recipient читаются batches exactly200,1; size1 batch outage отвергает целый результат; coherent recapture остается.

**Проблема:** Protocol ограничивает batch максимум200, а не требует packing exactly200. Безопасный batch-size100 ломает success oracle и перестает запускать size1 failure condition. Это привязка к текущему partitioning.

**Переписать так:** observable: Все201 UID входят в результат ровно один раз; каждый nonempty Auth request≤200; сбой любого последующего batch не возвращает частичный audience; local/catalog rechecks сохраняются.; граница: Оставить focused authority unit с recording port. Capture UID batches, проверять bound+union/no duplicates; failure задать номером выбранного вызова после хотя бы одного успешного response, а не ids.size()==1. Не ослаблять epoch/fence/version/no partial success checks.; БД: Не нужна для этого batching/composition unit; existing real NotificationAudienceIT сохранить.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/notification/NotificationAudienceAuthority.java:45](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/notification/NotificationAudienceAuthority.java#L45); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identitycatalog/AuthIdentityDataClient.java:51](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identitycatalog/AuthIdentityDataClient.java#L51)

#### AA-R18

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/GrpcExceptionAdviceTest.java:61](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/GrpcExceptionAdviceTest.java#L61) — метод: actualIdentityFencesRemainUnavailableThroughFrameworkAdvice.

**Сейчас:** Реальные fence exceptions проходят framework advice как sanitized UNAVAILABLE, и resolver обязан выбрать handleInternal(Exception).

**Проблема:** Последняя reflection-проверка на exact method лишняя: выделение dedicated StatusRuntimeException handler с теми же code/safe description/cause/trailers нарушит тест при сохранном публичном результате.

**Переписать так:** observable: Через реальный advice dispatcher все три fence scopes дают UNAVAILABLE, safe description, no cause/no trailers; INTERNAL policy для lookalikes остается.; граница: Сохранить real GrpcAdvice dispatcher и все response assertions; убрать только resolver.resolveMethodWithInstance(...)=getMethod(handleInternal...) на test lines79–81. Не удалять privacy/status cases.; БД: Не нужна: отдельный framework exception-adaptation seam.; negativeControl: Неправильная handler priority, ведущая к INTERNAL, по-прежнему падает на response.getCode().

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/GrpcExceptionAdvice.java:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/GrpcExceptionAdvice.java#L37)

### Academic: endpoint/DB/архитектурные контракты

#### AI-R01 — Legacy wrong-confirmation400 не достигает текущей semester deletion proof

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIT.java:608](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIT.java#L608) — метод: testSemesterDeleteWithWrongConfirmation_returns400.

**Сейчас:** Создаёт semester, DELETE с {confirmation:"wrong name"}; ждёт только400 и $.status400.

**Проблема:** Текущий DTO имеет обязательные previewDigest/password, API требует Idempotency-Key. Legacy field не проверяет ни password proof, ни binding свежего preview. Сигнал может целиком быть missing-header/body-validation. Полностью убрать вызов Auth confirmation в SemesterDeletionService: этот запрос всё равно получит400 до service. Статически это проходящий контрпример, не выполненная mutation.

**Переписать так:** Переписать один сценарий на реальный HTTP RANDOM_PORT с signed актуальным ADMIN, настоящим previewDigest, UUID Idempotency-Key и неверным текущим паролем; ожидаемый current contract403 от Auth. Сравнить committed semester, jobs/receipts/outbox до/после, отсутствие irreversible intent; отдельно malformed request400 оставить как validation case, если нужен. Нужны изолированные real PG обеих authority с реальными migrations, Auth critical Redis и реальный Auth internal HTTP confirmation handler; успешный Auth proof не mock. Для отрицательного password шага корректные remote preview snapshots можно фиксировать на границе, без заявления о remote deletion semantics. Не возвращать legacy confirmation-name product API.

**Проверенный контракт:** [services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/semester/DeleteSemesterRequest.java:12](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/semester/DeleteSemesterRequest.java#L12); [services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/SemesterApi.java:91](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/SemesterApi.java#L91) — метод: deleteSemester; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterDeletionService.java:52](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterDeletionService.java#L52) — метод: confirm; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/AuthSemesterDeletionClient.java:47](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/AuthSemesterDeletionClient.java#L47) — метод: confirm

#### AI-R02 — Retention count не различает old/recent SENT rows

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/OutboxCleanupIT.java:53](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/OutboxCleanupIT.java#L53) — метод: cleanup_deletesSentOlderThanRetention_keepsRecent; cases: old SENT10d; recent SENT3d; PENDING0d.

**Сейчас:** После cleanup проверяет deleted==1 и единственную pending запись. SENT строки больше не читает.

**Проблема:** Удаление recent вместо old сохраняет оба assertions. oldRec/recentRec берутся по позиции findPending с createdAt-only ordering, поэтому ещё и связь fixture label→id не явная. Инвертировать sentAt < cutoff на sentAt > cutoff, при данных fixture будет удалена recent SENT и вернутся ровно1 deleted/1 pending: текущие assertions этого не отличают.

**Переписать так:** Сохранить fixed Clock и real PG migrated storage; привязать IDs через eventType, после committed cleanup проверить old отсутствует, recent и pending сохранены с исходным payload/status/sentAt. Добавить ровно-at-cutoff retained vs cutoff-minus-small-unit deleted и second cleanup0 как граничный/idempotency oracle. HTTP не нужен: это scheduled DB retention, минимальный слой real job+storage+PG. Изолированный owned fixture и очистка только своих строк.

**Проверенный контракт:** [services/shared/shared-outbox/src/main/java/ru/rutcampustrack/shared/outbox/OutboxCleanupJob.java:59](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-outbox/src/main/java/ru/rutcampustrack/shared/outbox/OutboxCleanupJob.java#L59) — метод: runCleanup; [services/shared/shared-outbox/src/main/java/ru/rutcampustrack/shared/outbox/jpa/JpaOutboxStorage.java:133](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-outbox/src/main/java/ru/rutcampustrack/shared/outbox/jpa/JpaOutboxStorage.java#L133) — метод: deleteSentBefore

#### AI-R03 — Наличие Redis key не доказывает cache hit или отсутствие DB reread

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java:281](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java#L281) — метод: getGroup_secondCall_servedFromCache; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java:405](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java#L405) — метод: getActiveSemester_secondCall_servedFromCache; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java:420](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java#L420) — метод: getCampusGeofence_secondCall_servedFromCache; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java:435](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java#L435) — метод: getUserById_secondCall_servedFromCache; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RbacCacheIT.java:82](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RbacCacheIT.java#L82) — метод: isHeadman_secondCall_servedFromRbacCache; cases: positive; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RbacCacheIT.java:173](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RbacCacheIT.java#L173) — метод: isHeadman_negativeResult_alsoCached; cases: negative.

**Сейчас:** Вызывает два read и проверяет key exists; user/rbac ещё сравнивают два неизменившихся ответа.

**Проблема:** Система, которая всегда читает PG и заново записывает тот же Redis key, проходит. Comments про repository spies не соответствуют текущим actual annotations: таких spies здесь нет. User gRPC намеренно обходит profile cache. Заменить @Cacheable на write-through always-load: ответы и keys совпадут, лишний DB load незаметен.

**Переписать так:** На существующих real PG+cache Redis+migrations сохранить public gRPC cases и ответы, но измерять actual SQL delta: cold read loads known row, warm read возвращает те же данные без SQL load. Допустим узкий read-through callback counter для cache component, не repository verify. Profile caching проверять через actual cached Spring proxy; current-authority gRPC проверять отдельно как uncached. Для rbac оставить и positive, и negative caching. Не объединять Auth critical Redis с cache Redis; не ослаблять live authority checks.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java:55](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java#L55) — метод: fetchGroup; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java:85](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java#L85) — метод: fetchActiveSemester; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java:92](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java#L92) — метод: fetchCampusGeofence; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java:99](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java#L99) — метод: fetchUserById; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java:123](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java#L123) — метод: isHeadmanOf; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/CacheConfig.java:173](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/config/CacheConfig.java#L173) — метод: cacheManager

#### AI-R04 — Cache eviction tests должны проверять свежие поля после реальной мутации

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java:482](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java#L482) — метод: activateSemester_invalidatesActiveSemesterCache; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java:521](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/CacheIT.java#L521) — метод: archiveUser_invalidatesUsersCache.

**Сейчас:** Ключ появляется, исчезает после service mutation и появляется снова. Activation использует уже активный semester ID; финальные DTO поля не проверяются.

**Проблема:** Закрепляет eviction implementation, но не доказывает данные, которые получает следующий читатель. Activation no-op не создаёт отличимого before/after значения. Evict удаляет ключ, а repopulation берёт устаревший snapshot/неверную row: ключевой oracle проходит. Backend может вернуть ACTIVE profile после archive.

**Переписать так:** Через реальный mutation HTTP и реальные PG/Redis, под controlled instructional Clock: primeA, активировать отличимый dueB, следующий read==B; архивировать ранее прочитанный profile, следующий cached profile status==ARCHIVED и сохранённый ID. Проверить unrelated cache/control row остаётся корректной. Ключи оставить диагностикой namespace/TTL, не единственным итогом. Сохранить отдельные roster authority bypass и rollback guarantees.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java:293](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java#L293) — метод: activateSemester; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java:85](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java#L85) — метод: fetchActiveSemester; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityCacheInvalidator.java:54](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityCacheInvalidator.java#L54) — метод: afterCommit; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java:99](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicReadService.java#L99) — метод: fetchUserById

#### AI-R05 — Fastest-of-five50ms на1800rows не надёжен как index regression guard

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicPerformanceIT.java:184](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicPerformanceIT.java#L184) — метод: tsgFindByGroupAndSemesterStaysUnder50ms; cases: warmup + five trials, min <50ms; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicPerformanceIT.java:214](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicPerformanceIT.java#L214) — метод: homeworksFindByGroupAndSemesterStaysUnder50ms; cases: warmup + five trials, min <50ms.

**Сейчас:** Для каждого repository query ждёт nonempty и best-of-five <50ms.

**Проблема:** Небольшой seq scan может быть очень быстрым без индекса; шумный runner может замедлить корректный запрос. Nonempty не подтверждает точные scope/count. Это не проверка действительного HTTP latency. Отсутствие composite index при1800rows не обязано увеличить минимальный warm-cache trial выше50ms; один быстрый trial маскирует четыре медленных. Это статический sensitivity gap, отсутствие индекса не исполнялось.

**Переписать так:** Оставить real migrated PG и точный production query. Сначала усилить точным set/count и foreign group/semester control. Для index-risk использовать репрезентативную кардинальность и нормализованный EXPLAIN/BUFFERS or measured work budget, допускающий equivalent covering index; timed latency проверять на контролируемом perf stand. До принятого чувствительного replacement не убирать существующий CI gate/50ms лишь ради зелёного результата. Никаких index DROP в текущем аудите.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/AssignmentRepository.java:16](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/AssignmentRepository.java#L16) — метод: findByGroupIdAndSemesterId; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/HomeworkRepository.java:30](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/HomeworkRepository.java#L30) — метод: findByGroupIdAndSemesterId; [services/academic-service/academic-app/src/main/resources/db/academic-migration/V3\_\_academic_domain_baseline.sql:424](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/resources/db/academic-migration/V3__academic_domain_baseline.sql#L424); [services/academic-service/academic-app/src/main/resources/db/academic-migration/V3\_\_academic_domain_baseline.sql:720](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/resources/db/academic-migration/V3__academic_domain_baseline.sql#L720)

#### AI-R06 — Три repository getters не проверяют most-specific resolution

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EntityMappingIT.java:234](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EntityMappingIT.java#L234) — метод: attendanceThreshold_threeLevel_mostSpecificWins; cases: global70/group80/subject90; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIT.java:684](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/RestApiIT.java#L684) — метод: testThresholdResolution_mostSpecificWins; cases: global50/group70 only.

**Сейчас:** EntityMapping читает каждый seeded threshold отдельным exact getter. RestApi действительно вызывает resolver для group/global, но без subject и default.

**Проблема:** Утеря subject priority в resolver никак не влияет на три exact getters и существующий HTTP group/global. Удалить subject-level ветвь resolveThreshold или переставить её после group: оба текущих случая сохранят assertions.

**Переписать так:** Перенести полезный risk из mapping getter method в существующий real HTTP threshold scenario на isolated real migrated PG. Явные четыре case: subject90 overrides group80/global70; missing subject→group80; missing group→global70; no thresholds→default0/null id. Проверять level/value/id и независимость другой group/subject пары, не порядок repository calls. После принятого replacement удалить redundant getter-only method. Дешёвую pure resolution unit можно сохранять для отдельных сложных правил, которых здесь нет.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/threshold/ThresholdService.java:87](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/threshold/ThresholdService.java#L87) — метод: resolveThreshold; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/threshold/ThresholdController.java:56](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/threshold/ThresholdController.java#L56) — метод: resolveThreshold; [services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/ThresholdApi.java:72](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/ThresholdApi.java#L72) — метод: resolveThreshold

#### AI-R07 — Строгое число enrichment queries привязано к текущей реализации

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicReadBatchIT.java:68](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicReadBatchIT.java#L68) — метод: subjectPageUsesTwoEnrichmentQueriesAndPreservesOrderScopeAndPublication; cases: page sizes1/4; empty; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicReadBatchIT.java:124](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/AcademicReadBatchIT.java#L124) — метод: homeworkPageUsesOneCompletionQueryForCurrentActorIncludingEmptyPage; cases: page sizes1/4; empty; two actors.

**Сейчас:** Хорошие реальные SQL/PG data checks; дополнительно exact2 statements (и0/1 для empty).

**Проблема:** Поведенческий anti-N+1 риск связан с ростом SQL по размеру страницы. Эквивалентное объединение enrichment в один SQL ломает exact2, хотя результат и производительность улучшились. Текущий прямой controller вызов не подтверждает HTTP auth/binding. Заменить две enrichment queries одной projection query с теми же данными: hasExactly2 отклонит эквивалентный refactor.

**Переписать так:** Сохранить data cases, state filtering, actor isolation и real PG. SQL budget выражать bounded independent-of-page-size (не растёт на sizes1/4/больше), допускающий меньше запросов; empty не должен грузить completion/enrichment. Один существующий endpoint case провести реальным HTTP с selected actor; узкий SQL-growth proof может остаться component/PG для изоляции счётчика. Не удалять lifecycleRoundTrips третьим методом файла.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectAssembler.java:46](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectAssembler.java#L46) — метод: toResponsePage; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkController.java:54](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkController.java#L54) — метод: listHomeworks; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java:910](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java#L910) — метод: completedHomeworkIds

#### AI-R08 — Page size metadata не доказывает ограничение actual result set

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/PaginationCapIT.java:25](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/PaginationCapIT.java#L25) — метод: listUsers_sizeExceedsMax_truncatedToShared100; cases: size1000000/page0; tiny canonical3users.

**Сейчас:** Ждёт200 и $.page.size<=100, без количества/идентичностей rows.

**Проблема:** Ответ может иметь capped metadata, но вернуть неограниченный body. Канонический маленький dataset никогда не превысит cap, даже при сломанном ограничении данных. Захардкодить page.size100 при сериализации и вернуть все users: текущие assertions не отличат.

**Переписать так:** Переписать этот endpoint case на real HTTP RANDOM_PORT и isolated migrated PG с >100 известных Auth/Academic users. Проверить exact metadata size100, <=100 distinct authorized rows, totalElements и следующую страницу без пересечения. Preserve signed role/security/data scoping. Не добавлять DB work solely ради coverage: один чувствительный cap case вместо нескольких metadata-only smoke.

**Проверенный контракт:** [services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/autoconfigure/PageableDefaultsPostProcessor.java:28](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/autoconfigure/PageableDefaultsPostProcessor.java#L28)

#### AI-R09 — Actuator substring/index отрицательный список допускает неожиданный exposed endpoint

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/ActuatorIT.java:16](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/ActuatorIT.java#L16) — метод: healthEndpointReturnsUp; [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/ActuatorIT.java:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/ActuatorIT.java#L50) — метод: actuatorIndexShowsOnlyHealthAndInfo.

**Сейчас:** Реальный HTTP; body содержит UP; index содержит health/info и не содержит лишь env/beans/heapdump.

**Проблема:** Не читает top-level health.status и не проверяет весь набор exposed links. Имя onlyHealthAndInfo уже не отражает production include health,info,prometheus. Добавить /actuator/mappings к exposure: index test всё ещё пройдёт, если прежние три запрещённые ссылки отсутствуют. Текст BACKUP в body также удовлетворяет containsUP.

**Переписать так:** Сохранить real HTTP. Парсить /status==UP; index \_links.keySet сравнить с актуально принятым public management surface (health/info/prometheus plus self/path template links), не с устаревшим именем теста. Точные env/beans/heapdump404 отдельные security probes сохранить: отсутствие ссылки не доказывает запрет прямого URL. Реальная own-PG boot необходима как сейчас; Rabbit-health outage coverage не считать дублем здорового ordinary профиля.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/resources/application.yml:157](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/resources/application.yml#L157)

#### AI-R10 — Concurrency proof привязан к тексту SQL, хотя важна блокировка нужной группы

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/UserArchiveLifecycleIT.java:693](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/UserArchiveLifecycleIT.java#L693) — метод: archiveWaitsForHelperWriterThenRevokesItsCommittedAuthorityWithoutFkDeadlock.

**Сейчас:** Полезный PG wait→release→archive/revoke outcome; pg_stat_activity predicate требует exact query string SELECT id FROM groups WHERE id=$1 FOR UPDATE.

**Проблема:** SQL alias/whitespace/equivalent projection ломают proof при той же реальной row-lock semantics. Остальной сценарий действительно проверяет deadlock/authority outcome и должен остаться. Переписать SELECT id FROM groups... как SELECT g.id FROM groups g... с тем же FOR UPDATE: строковый predicate не совпадёт, хотя pg_blocking_pids/результат те же.

**Переписать так:** Оставить real PG, реальные Auth actions, два controlled workers/barriers, факт blocked PID exact владельцем, завершение без deadlock, revoke/old-session denial after restore. Установить table/lock ownership через pg_locks relation + pg_blocking_pids либо привязать backend PID к известному fixture transaction; убрать только literal query equality. Не ослаблять доказательство факта ожидания до Future.notDone или sleep.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/JdbcAcademicIdentityOperationStore.java:52](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/JdbcAcademicIdentityOperationStore.java#L52) — метод: accept; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/JdbcAcademicIdentityOperationStore.java:91](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/JdbcAcademicIdentityOperationStore.java#L91)

#### AI-R11 — Rename event positive case не проверяет new_name и полный обещанный event pair

**Где:** [services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/GroupRenameEventIT.java:77](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/GroupRenameEventIT.java#L77) — метод: updateGroupWithNewName_publishesGroupRenamedEvent.

**Сейчас:** Реальные PG/outbox/Rabbit, UUID/occurred_at/group_id у rename; собирает до двух distinct types, в финале требует только renamed.

**Проблема:** DTO new_name — данные для потребителя; отсутствует exact value check. Комментарий обещает renamed+updated, но в positive case updated не assert. Независимый EventIT проверяет updated, однако не связывает оба payload с одной принятой rename mutation. Producer публикует group.renamed с прежним/пустым new_name или без group.updated при rename: этот метод останется зелёным.

**Переписать так:** Запускать rename через real HTTP с реальным выбранным ADMIN; сохранить isolated migrated PG+owned Rabbit+actual outbox. По correlation/group/event IDs проверить ровно один renamed с persisted normalized new_name и правильным actor, один updated для того же group независимо от порядка. Сохранить negative same-name case и его no-rename guarantee; payload name здесь business data, не UI copy. Удалять EventIT updateGroup case допустимо только после отдельной сверки всех его UUID/envelope/group guarantees в replacement; сейчас не предлагать удаление.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/GroupRenamedEvent.java:16](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/GroupRenamedEvent.java#L16) — метод: Payload; [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEventListener.java:43](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEventListener.java#L43) — метод: onDomainEvent

### Attendance: запись, события и операции

#### AO-R01

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterIT.java:84](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterIT.java#L84) — метод: validInternalToken_passesFilter; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterIT.java:95](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterIT.java#L95) — метод: legacyHeaders_dualMode_accepted; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterIT.java:107](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterIT.java#L107) — метод: internalToken_precedenceOverLegacy; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterStrictModeIT.java:39](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterStrictModeIT.java#L39) — метод: validInternalToken_strictMode_passes.

**Сейчас:** Четыре положительных сценария и проверка приоритета internal token над legacy headers требуют лишь HTTP-статус, отличный от 401.

**Проблема:** 403, 404 и 500 засчитываются как успех. Если фильтр выберет legacy TEACHER вместо подписанного пользователя или потеряет user/group, отказ нижнего слоя тоже удовлетворит текущую проверку.

**Переписать так:** REWRITE через настоящий HTTP-сервер на случайном порту, реальный security filter/advice/report/read adapter и подписанный синтетический token. В изолированной Mongo replica set с production-конвертерами/индексами создать различимые собственные и чужие записи; требовать 200 и точные id/тело собственных записей. Конфликтующие legacy headers должны давать тот же результат подписанного пользователя без чужих данных. Academic/Schedule допустимо контролировать на границе. Уникальная БД и собственные строки на тест; сохранить strict 401 для отсутствующего/невалидного token и dual-mode fallback.

**Граница и сохранение:** Real HTTP + actual security filter/advice/report/read adapter Isolated replica-set Mongo with production converter/index initialization; controlled Academic/Schedule seam sufficient for this local identity contract Unique database/test-owned rows; no shared cleanup or external accounts.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilter.java:31](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilter.java#L31); [services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/DualModeUserContextFilter.java:63](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/DualModeUserContextFilter.java#L63); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java:788](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java#L788)

#### AO-R02

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceTest.java:167](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceTest.java#L167) — метод: readOnlyIdentityIsRejectedBeforeGeoParsingAndDependencies.

**Сейчас:** Поставщик параметров создаёт команды с координатами и unavailable, но каждый вызов отправляет StudentCheckinCommand.getDefaultInstance(); оба запуска проверяют один payload без geo.

**Проблема:** Если read-only доступ будет отвергаться только для команды по умолчанию, а корректные coordinates/unavailable начнут приниматься, обе текущие итерации останутся зелёными.

**Переписать так:** REWRITE минимально на уровне публичного gRPC handler: передавать фактический параметр command в checkin(command, observer). Для каждого payload требовать PERMISSION_DENIED + OUT_OF_SCOPE и отсутствие вызовов зависимостей/доменных эффектов; отдельную default/malformed-команду сохранить, если проверяется порядок отказов. Свежие mock и claims на итерацию, БД для этого guard не нужна. Реальные Mongo-инварианты read-only в StudentCheckinTransactionIT оставить.

**Граница и сохранение:** Public gRPC handler unit; optional existing transport test can carry same cases None for the guard; retain real-Mongo read-only invariants in StudentCheckinTransactionIT Fresh mocks and claims per invocation.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java:172](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java#L172)

#### AO-R03

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/OneOffLessonCancelledConsumerIT.java:119](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/OneOffLessonCancelledConsumerIT.java#L119) — метод: cancellation_idempotent; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java:412](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java#L412) — метод: lessonCancelled_noDocs_noError; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java:487](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java#L487) — метод: lessonCancelled_beforeClose_preventsRetrospectiveMaterialization.

**Сейчас:** Асинхронные отрицательные сценарии ожидают уже пустую attendance-коллекцию; helper повторной one-off отмены создаёт новый event_id. В сценарии отмена→закрытие ожидается marker отмены, но не завершение следующего close.

**Проблема:** Остановленный перед повторной доставкой listener, ошибочная обработка или DLQ всё ещё оставляют уже подходящее пустое состояние. Разные event_id не проверяют точный replay того же сообщения.

**Переписать так:** REWRITE с настоящим Rabbit listener и Mongo fence/receipt/outbox в отдельной replica-set БД и vhost/очередях. Для точного replay использовать тот же envelope/event_id; разные id проверять отдельно при необходимости. Дождаться устойчивого receipt первого эффекта, затем существующего доказанного барьера доставки того же worker/prefetch; требовать один неизменный effect receipt, отсутствие новых доменных/чужих изменений и целевого DLQ. Если наблюдается существующий effect-ACK outbox, сверять реальные ACK каждой доставки, не ожидать два уникальных receipt одного event_id и не добавлять observer framework/ACK counter. Для close после отмены сначала дождаться собственного терминального receipt close; фиксированные sleeps не использовать.

**Граница и сохранение:** Actual Rabbit listener + real Mongo fence/receipt/outbox services Isolated replica-set Mongo with production indexes/converters Dedicated vhost/queues and database; unique event IDs; observe only target receipt/ACK/DLQ.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java:122](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java#L122); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java:210](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java#L210); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java:371](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java#L371); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java:444](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java#L444)

#### AO-R06

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MarkingIT.java:301](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/MarkingIT.java#L301) — метод: mark_successfulMark_publishesAttendanceMarkedEvent.

**Сейчас:** После отметки через MockMvc проверяет вызов spy publishMarked(doc, subject) и несколько полей переданного документа.

**Проблема:** Если publishMarked станет no-op либо запишет неверный outbox envelope, вызов spy всё равно пройдёт. Вызов publisher не доказывает сохранение/сериализацию события и его связь с committed-отметкой.

**Переписать так:** REWRITE через настоящий HTTP, подписанную headman identity, реальные marking/transaction/outbox publisher/storage и изолированную Mongo replica set с production-инициализацией. После 200 сверять точную сохранённую отметку и один соответствующий attendance.marked envelope со схемой, id/status/source. Для обещания broker delivery пропустить его через реальный sender и наблюдать consumer/ACK; иначе назвать проверяемый результат transactional event intent. Уникальные БД/actor/lesson, выборка только собственного события; scheduling контролировать без подмены publisher. Сохранить denial/no-write сценарии.

**Граница и сохранение:** Real HTTP + actual marking/transaction/outbox; controlled upstream authority seam Isolated Mongo replica set with actual indexes/converters Unique database/actor/lesson; inspect matching event only; publisher scheduling controlled without replacing publisher.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java:274](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java#L274); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/AttendanceEventPublisher.java:87](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/AttendanceEventPublisher.java#L87)

#### AO-R08

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/marking/MarkingServiceTest.java:430](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/marking/MarkingServiceTest.java#L430) — метод: markBatch_threeStudentsOneLesson_upsertsAllPublishesEach.

**Сейчас:** Возвращает три заготовленных документа из mock findAndModify, сравнивает их id/status и точное число вызовов getLesson/getMembers/findAndModify/publisher.

**Проблема:** Любые query/update проходят any-match: запись всех элементов по одному неверному фильтру не меняет заготовленные ответы. Эквивалентная bulk/repository-реализация ломает счётчик вызовов; атомарность batch и событий не доказана.

**Переписать так:** REWRITE happy path через реальный batch HTTP endpoint, MarkingService/transaction/Mongo/outbox и контролируемые авторитетные upstream-границы. В отдельной replica-set БД с production-конвертерами/индексами проверить точные три (lesson,user,status), стабильные identity при replay и соответствующие устойчивые event intents. Ошибка посреди batch после реальной записи на собственной storage-границе должна оставить domain/history/outbox без изменений. Уникальные lesson/users; сохранить узкие authz, DoS fan-out limit и clock unit-тесты, не ослабляя защищённые controls.

**Граница и сохранение:** Real batch HTTP + full local transactional domain + actual outbox Isolated replica-set Mongo with production converters/indexes Unique database/lesson/users; failure injected at owned storage seam after real write, never relaxing protected controls.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java:615](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java#L615); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java:797](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java#L797)

#### AO-R10

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentCheckinTransactionIT.java:1107](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentCheckinTransactionIT.java#L1107) — метод: concurrentGeoAndApproveConvergeWithoutMixedProvenance; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentCheckinTransactionIT.java:1228](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentCheckinTransactionIT.java#L1228) — метод: concurrentGeoAndManualAbsenceNeverLeaveStaleGeoOverwrite; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentCheckinTransactionIT.java:1572](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentCheckinTransactionIT.java#L1572) — метод: runConcurrent.

**Сейчас:** Race helper принимает любую DataAccessException или TransactionException как допустимый проигрыш writer-а, затем проверяет состояние победителя; Future.get не ограничен временем.

**Проблема:** Постоянная ошибка mapping/query/configuration одного writer-а маскируется под гонку. Например, manual mark всегда падает до записи, geo остаётся PRESENT, а manual-absence сценарий принимает такой исход; обработка обеих команд не доказана.

**Переписать так:** REWRITE в существующем реальном Mongo transactional race без дополнительного HTTP-слоя. Допускать только явно обоснованный помеченный Mongo112 conflict либо отдельно обоснованный точный доменный отказ; завершать оба future с конечными сроками. После победителя повторить ту же проигравшую команду и проверить линейризуемый итог, неизменные receipt/provenance и отсутствие лишних history/outbox. Использовать уже имеющиеся awaitTransactionOrWriteConflict/retryLabelledWriteConflict:1538–1565. Собственные команды и ограниченные barriers; замороженные Mongo controls не расширять и не ослаблять, требуемое их изменение оставить вопросом owner/root.

**Граница и сохранение:** Existing actual Mongo transactional race; no additional HTTP layer needed to prove local serialization Existing isolated replica-set Mongo with actual fences/receipts Owned concurrent commands; bounded barriers/deadlines, no broad catch or arbitrary error acceptance.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java:179](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java#L179); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java:172](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java#L172)

#### AO-R04

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/EventIdempotentIT.java:64](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/event/EventIdempotentIT.java#L64) — метод: duplicateDelivery_isDedupped.

**Сейчас:** Трижды публикует semester.archived, считает одну уникальную claim-строку и использует двухсекундный pollDelay, не наблюдая устойчивый эффект handler-а.

**Проблема:** Повторное выполнение handler-а после проигнорированного tryClaim=false оставляет одну claim-строку; зависшая вторая доставка тоже проходит проверку количества claims.

**Переписать так:** REWRITE с настоящим broker/listener и Mongo guard в отдельной replica-set БД с production unique consumer/event index. По уникальным correlation id дождаться барьера обработки и одного эффекта handler-а либо одного неизменного доменного receipt с replay ACK. Конкретный образец: services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/RabbitDecisionRetryIT.java::transientFailureRecoversTheOriginalDeliveryAndDuplicateHasNoSecondEffect (:251–285), где более поздний отдельный запрос служит барьером до подсчёта эффектов. Контролируемая доменная граница допустима для dispatch guard; отдельный vhost, без предположений по прошедшему времени. differentEventIds_areIndependent оставить.

**Граница и сохранение:** Actual listener + actual Mongo guard; controlled domain seam is legitimate for guard dispatch Isolated replica-set Mongo with production unique consumer/event index Dedicated broker vhost and unique correlation IDs; no elapsed-time assumption.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java:113](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java#L113); [services/shared/shared-outbox/src/main/java/ru/rutcampustrack/shared/outbox/mongo/MongoIdempotencyStore.java:76](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-outbox/src/main/java/ru/rutcampustrack/shared/outbox/mongo/MongoIdempotencyStore.java#L76)

#### AO-R05

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcProjectionTest.java:49](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcProjectionTest.java#L49) — метод: explanationWireKeepsFullCommentAndOptionalPresenceForDayAndHistory.

**Сейчас:** Через reflection ищет private перегрузки toProto по имени/сигнатуре и вызывает их для проверки комментария и optional presence.

**Проблема:** Выделение mapper-а или встраивание mapping в публичный handler без изменения protobuf-ответа ломает reflective lookup. Полный комментарий и optional presence полезны, но private-разбиение не является их гарантией.

**Переписать так:** REWRITE на уровне публичного getStudentAttendanceProjection с аутентифицированным Context, управляемой projection-service границей и локальным observer. Подать полный OTHER comment, explanation истории, legacy отсутствие explanation и отменённое занятие; сериализовать и прочитать protobuf bytes, сверить полный comment и hasComment/hasCancellationReason/hasExplanation. БД не нужна; детерминированные фикстуры и существующая optional authority-outage проверка сохраняются.

**Граница и сохранение:** Public handler/proto unit; projection service controlled seam None Deterministic fixture and local observer; preserve existing optional authority-outage test.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java:101](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java#L101); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java:576](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java#L576); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java:670](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java#L670)

#### AO-R07

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/LateCheckinPerformanceIT.java:101](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/LateCheckinPerformanceIT.java#L101) — метод: lateCheckinQueryStaysUnder50ms.

**Сейчас:** На 6000 строках проверяет имя индекса, прогревает запрос и требует непустой результат и лучшее из пяти времён менее 50 мс.

**Проблема:** COLLSCAN по небольшому прогретому набору может уложиться в 50 мс, а корректный индексированный запрос под нагрузкой runner-а — не уложиться. Непустой результат не доказывает группу, статус или порядок.

**Переписать так:** REWRITE на уровне настоящего Mongo repository с production-конвертерами/индексами и ограниченным синтетическим набором целевых/чужих групп и статусов. Сверять точные id, запрошенные group/status и createdAt по возрастанию; через explain эквивалентного запроса без hint проверить нужный индекс, отсутствие blocking sort и обоснованную границу examined rows. Свежая отдельная replica-set БД; HTTP не нужен для проверки плана. Время оставить диагностикой, пока owner не задаст performance budget и среду.

**Граница и сохранение:** Actual Mongo repository + query execution/explain; HTTP adds no index-planner evidence Fresh isolated replica-set Mongo with production initialization Dedicated database with bounded synthetic cardinality; no host-speed gate.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java:101](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java#L101); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinRepository.java:24](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinRepository.java#L24)

#### AO-R09

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/OutboxAtomicityIT.java:51](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/OutboxAtomicityIT.java#L51) — метод: saveExcuseAndOutbox_thenThrow_bothRolledBack; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/OutboxAtomicityIT.java:84](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/OutboxAtomicityIT.java#L84) — метод: saveExcuseAndOutbox_withoutThrow_bothCommitted.

**Сейчас:** Тестовый @Transactional TestTxService вручную сохраняет repository и вызывает настоящий ExcuseEventPublisher; проверяются rollback/commit domain/outbox/audit без вызова production ExcuseService.createExcuse.

**Проблема:** Атомарность даёт аннотация тестовой фикстуры. Удаление @Transactional из production createExcuse не меняет результат этих двух тестов.

**Переписать так:** REWRITE положительный и отрицательный сценарии через реальный HTTP и настоящий proxied production excuse service/transaction/outbox в собственной Mongo replica set с production-инициализацией и контролируемой gRPC authority-границей. После реальной вставки outbox вызвать ошибку на собственной storage-границе; сверить отсутствие частичных domain/outbox/history и восстановление той же команды. Не оборачивать product call тестовой транзакцией. Если нужен только infrastructure smoke, сохранить его с узким названием без заявления об атомарности production-операции.

**Граница и сохранение:** Real HTTP + actual proxied excuse service/transaction/outbox Isolated replica-set Mongo with production init Own database/rows and controlled gRPC authority; no test-only transaction wrapper around product call.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseService.java:148](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseService.java#L148)

#### AO-R11

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/ReportIT.java:494](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/ReportIT.java#L494) — метод: getStudentRecords_filteredBySubject; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/ReportIT.java:525](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/ReportIT.java#L525) — метод: getStudentRecords_allSubjects.

**Сейчас:** Создаёт записи одного студента текущего семестра и проверяет только длину массива 1 или 2 в ответах с subject-фильтром и без него.

**Проблема:** Другой предмет или чужая запись той же мощности проходят count-only проверку: например, filtered-запрос всегда возвращает запись другого предмета.

**Переписать так:** REWRITE существующие endpoint-сценарии через настоящий HTTP на случайном порту, подписанного студента и реальные ReportService/read adapter/security. В отдельной Mongo с production-конвертерами/индексами создать собственные выбранный/другой предмет, чужого студента и исторический семестр; Schedule/Academic чтения контролировать на границе. Сверять точные наборы lessonId/subjectId/date/status/source/symbol и неизменность DB/outbox. Уникальные identity на случай, без глобального count и mock-копии. Учитывать сохранённую WIP25 foreign/data работу; это только предложение.

**Граница и сохранение:** Real HTTP + actual ReportService/read adapter/security Isolated Mongo with production converters/indexes; controlled Schedule/Academic reads Unique per-case identities and database; compare selected records, no global-count reliance.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java:788](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java#L788)

#### AO-R12

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/subjectdeletion/ExcuseSubjectCoverageTest.java:25](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/subjectdeletion/ExcuseSubjectCoverageTest.java#L25) — метод: preservesOneDecisionOtherCoverageAndOriginalEvidenceOnMixedTicket; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/subjectdeletion/ExcuseSubjectCoverageTest.java:103](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/subjectdeletion/ExcuseSubjectCoverageTest.java#L103) — метод: mongoMappingKeepsRawOriginalArraysWhileDomainReadsUseEffectiveCoverage.

**Сейчас:** Вместе с полезными subtraction/history/mapping случаями требует совпадения ссылок original snapshot/selection, читает private-поля через reflection и сверяет лишь размеры исходных массивов.

**Проблема:** Защитное глубокое копирование immutable evidence или переименование backing field сохраняет значения BSON, но ломает identity/reflection проверки. Одинаковый размер также не доказывает сохранность содержимого evidence.

**Переписать так:** REWRITE только private reference/field assertions, сохранив все бизнес-сценарии: сравнивать полные значения исходных BSON lesson_snapshots и decision_selection, effective coverage и history до/после round-trip настоящего MappingMongoConverter. Минимум — pure domain и production mapping/converters, детерминированный синтетический ticket, без БД. Существующий AttendanceSubjectDeletionIT с изолированной реальной Mongo оставить; различия original/effective/history не терять.

**Граница и сохранение:** Pure domain + actual production mapping/converters; existing real Mongo IT retained None for focused unit; retain isolated Mongo in deletion IT Deterministic synthetic ticket; never discard original/effective history distinctions.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/subjectdeletion/ExcuseSubjectCoverage.java:32](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/subjectdeletion/ExcuseSubjectCoverage.java#L32)

#### AO-R13

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/events/AttendanceMarkedContractIT.java:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/events/AttendanceMarkedContractIT.java#L37) — метод: retiredLegacyCheckin_doesNotPublishAttendanceMarkedEvent; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/CheckinIT.java:53](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/CheckinIT.java#L53) — метод: legacyCheckin_returns410AndPreservesManualAbsenceWithoutSideEffects.

**Сейчас:** Два сценария отправляют одинаковый legacy check-in и требуют 410, retired problem type и отсутствие устойчивого события, но один начинается с пустой attendance-коллекции, другой — с HEADMAN/ABSENT.

**Проблема:** Есть дублирование setup/request/assertions, но EMPTY и SEEDED — разные риски. Ошибочный допуск legacy check-in только без существующей отметки оставит seeded CheckinIT зелёным и будет найден empty-сценарием.

**Переписать так:** REWRITE как один параметризованный real-HTTP retirement-тест EMPTY/SEEDED manual absence с настоящими compatibility controller/service/security/advice, отдельной Mongo replica set, реальными outbox storage и Redis namespace/instance. Для каждого состояния сохранить 410/problem type, отсутствие новых mark/receipt/pair/Redis/outbox и неизменные поля seeded-записи; внешние upstream не нужны. AttendanceMarkedContractIT удалять только после переноса обоих состояний и всех проверок в CheckinIT: это условная консолидация, не немедленный DELETE.

**Граница и сохранение:** Actual random-port HTTP + real compatibility controller/service/security/advice Isolated replica-set Mongo and Redis, actual outbox storage; no external upstream needed Own database/Redis namespace or dedicated instance; exact pre/post state for each parameter.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/CheckinController.java:34](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/CheckinController.java#L34); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/CheckinService.java:22](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/CheckinService.java#L22)

### Attendance/Requests: отчёты, отметки и обращения

#### AR-R01 — No-write checks должны наблюдать фактическую запись посещаемости

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinServiceTest.java:349](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinServiceTest.java#L349) — метод: applyDecisionFromWeb_reject_doesNotTouchAttendance; assertionLine: 370; cases: approved=false; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinServiceTest.java:410](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinServiceTest.java#L410) — метод: applyDecision_alreadyDecided_isNoOp; assertionLine: 418; cases: stored=APPROVED,incoming approved=false.

**Сейчас:** Оба запрещают attendanceWritePort.mark(5args), но фактический writer использует markWithLesson(10args).

**Проблема:** Нужный no-write/idempotency риск остаётся, однако эти never не проверяют вызываемый production writer и не доказывают persisted state. Если в reject/stale branch ошибочно вызвать markWithLesson без repository.save/event, оба negative assertions продолжат проходить. Это статический counterexample, не выполненная mutation.

**Переписать так:** Для reject: request=REJECTED и ровно один decided outbox, attendance document/bytes/timestamps остаются прежними. Для stale terminal delivery: request,attendance,audit,outbox остаются идентичными. Не заменять проверку writer вызовом другого overload.

**Граница и сохранение:** Тот же legacy service+настоящий AttendanceWritePortImpl и Mongo transaction; брокер не нужен для неизменности Mongo outbox. Самый малый unit fix — запретить все write-port interactions, но его не объявлять transaction proof. Изолированный Mongo replica set, production converters/index startup, уникальная DB/очистка только своих fixture. HTTP не нужен для legacy applyDecisionFromWeb: текущий HTTP decide вызывает другой canonical service.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinService.java:302](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinService.java#L302) — метод: applyDecision; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinService.java:326](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinService.java#L326) — метод: applyAuthorizedDecision; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinService.java:388](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinService.java#L388)

#### AR-R02 — Убрать test-owned копию индексов из доказательства concurrency

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java:393](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java#L393) — метод: setUp; assertionLine: 396; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java:3644](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java#L3644) — метод: ensureIndexes; cases: sixConcurrentManualLateSubmissionsChargeExactlyFive; concurrentSameExcuseKeyCreatesOneTicketAndOneReceipt; overlappingExcusePackagesCannotBothActivate; concurrentBudgetIncreasesAndConsumptionPreserveEveryChargeAndAudit.

**Сейчас:** После drop своей DB fixture вручную создаёт пять нужных индексов; DataMongoTest не импортирует production MongoConfig.

**Проблема:** Отличные реальные race/receipt/budget тесты используют схему, которую они сами скопировали. Потеря production uniqueness initializer не обязана сломать эти тесты. Удалить uniq_student_request_receipt/uniq_student_late_budget из production startup — fixture всё равно добавит их и race tests могут оставаться зелёными.

**Переписать так:** Сохранив все business scenarios, поднимать production converters+transaction manager+Mongo index initializer после reset своей DB. Проверять фактическое отклонение конкурирующей записи и единичный receipt/budget/outbox; не добавлять отдельную копию списка как замену behavior.

**Граница и сохранение:** Настоящий изолированный Mongo domain integration. Для пользовательской приёмки selected request/decision/export flows нужен отдельный настоящий HTTP путь с нужными слоями; переезд всех343 исходных методов в HTTP не предлагается. Уже существующая уникальная RUN_ID DB/container сохраняется. MongoIndexIT:119 узко прочитан: он проверяет имена старых attendances/lcr indexes и не сохраняет здесь полный request receipt/budget startup риск.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java:60](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java#L60) — метод: initIndexes; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java:143](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java#L143); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java:152](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java#L152)

#### AR-R03 — Retention cleanup — Mongo predicate вместо эмуляции Query JSON

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/EducationalRetentionClockIsolationTest.java:74](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/EducationalRetentionClockIsolationTest.java#L74) — метод: actualRetentionCleanupClearsOnlyRealExpiredBytes; cases: teachingNow=2025-10-12T06:00:00Z; teachingNow=2030-10-12T06:00:00Z; realNow=2026-10-11T22:00:00Z or2028-10-11T22:00:00Z; fixtureCouplingLines: 131; 138; 140; 142.

**Сейчас:** Fake mongo.find извлекает query.getQueryObject().expires_at.$lte, утверждает cutoff=realNow и сам фильтрует один mutable row; fake transaction исполняет callback(null).

**Проблема:** Это привязка к внутренней форме Query и повторение нужного Mongo predicate; не подтверждает реальные state conversions, selection и transaction cleanup. Эквивалентная Query через $and/$expr ломает fixture. Ошибка production predicate state/owner может остаться невидимой, поскольку fake сам выбирает только ACTIVE document.

**Переписать так:** В двух teaching-date cases сохранить реальные retained+expired Mongo rows, запустить реальный cleanup при фиксированном retention clock, reread: очищены только expired bytes,state=EXPIRED,expiredAt=realNow; retained bytes и archive fence неизменны. Первые два дешёвых download unit оставить.

**Граница и сохранение:** Один реальный Mongo cleanup integration; не HTTP, поскольку это служебная cleanup operation. Существующий domain suite уже сохраняет DB expiry/fence риск, новая комбинация нужна лишь для независимых часов. Уникальная Mongo DB+production converters/index startup; оба clock injection явные. Текущее frozen Mongo окружение не запускалось; изменение harness отдельно от предложения.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java:192](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java#L192); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java:310](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java#L310); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java:886](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java#L886) — метод: expireAttachments

#### AR-R04 — Privacy ownRank проверять на фактическом wire ответе

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/studentprojection/OwnRankCalculatorTest.java:184](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/studentprojection/OwnRankCalculatorTest.java#L184) — метод: rankSummaryHasNoPeerPayloadFields; cases: exact record component names position,participantCount,available.

**Сейчас:** Reflection перечисляет имена компонентов внутреннего OwnRankCalculator.Rank.

**Проблема:** Это проверяет Java representation, а public projection использует другой Rank плюс protobuf mapper/BFF DTO. Сохранённая форма внутреннего record не доказывает отсутствие peer данных в ответе. Добавление безопасного diagnostic внутреннего компонента ломает test при неизменном wire. Утечка peer данных в другом field public projection не меняет Rank.class и останется невидимой.

**Переписать так:** На собственном attendance response проверять ownRank fields/nullable position и отсутствие уникальных peer-id/name/reason/attachment markers в actual serialized response; отдельно сохранять разрешённую ranking page, где имена cohort легитимны. Все восемь algorithm tests оставить.

**Граница и сохранение:** Минимум public gRPC handler serialization без private reflection. При утверждении полноценной пользовательской privacy — настоящий authenticated BFF HTTP→Attendance gRPC→isolated Mongo и нужный Academic authority слой; DTO-only mock не объявлять такой приёмкой. Для data selection использовать уникальные own/foreign Mongo fixtures с production startup; если проверяется Academic DB authority, соответствующий isolated PG с migrations, не canned own scope.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/OwnRankCalculator.java:32](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/OwnRankCalculator.java#L32) — метод: Rank; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java:544](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java#L544) — метод: toProto; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java:726](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java#L726) — метод: toProto; [proto/attendance.proto:294](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/proto/attendance.proto#L294) — метод: StudentAttendanceOwnRank; [services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java:485](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java#L485) — метод: StudentAttendanceOwnRank

#### AR-R05 — DOCX геометрию не фиксировать raw XML literal

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/DocxRendererTest.java:29](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/DocxRendererTest.java#L29) — метод: renderSingleWeekReplacesEveryPlaceholder; assertionLine: 44; cases: singleweek.

**Сейчас:** Помимо хорошего реального DOCX data test требует exact w:gridCol/w:tcW560 и отсутствия340.

**Проблема:** Числовая геометрия и способ записи XML не являются сами наблюдаемыми данными отчёта. Сейчас assertions отражают production string replacement. Эквивалентные namespace prefixes/attribute order/updated fitted template или другой подход к fitting сохраняют данные и читаемость, но raw literals ломаются.

**Переписать так:** Оставить настоящий ZIP/XML, отсутствие placeholders, все значения и package resources. Убрать exact560 serialization. Если отдельно принят запрет переноса номеров/одна неделя на страницу, проверять этот конкретный rendered результат при разрешённой приёмке, не width constant.

**Граница и сохранение:** Чистый real renderer unit достаточен для data/package. Никакого DB или нового browser/native-runtime запуска для этого аудита. DB N/A. Frozen rejected Chromium/native-conversion paths не переоткрывать; визуальный page-fit здесь UNKNOWN.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/DocxRenderer.java:35](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/DocxRenderer.java#L35); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/DocxRenderer.java:160](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/DocxRenderer.java#L160) — метод: fitLibreOfficeGeometry

#### AR-R06 — Статистика120строк — semantic cells вместо фиксированных offsets

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanStatsRendererTest.java:24](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanStatsRendererTest.java#L24) — метод: largeSelectedStatisticsKeepsContextEveryRowAndSuppliedMetrics; assertionLine: 78; cases: 120students; supplied present7/28=26.3%; last0/0; name=SUM(A1:A2).

**Сейчас:** Exact cantSplit count, duplicated raw escaped names count, dimension A1:W139,ySplit19,A20 и literal B20 numeric XML.

**Проблема:** Смешаны полезные completeness/numeric/formula/escaping guarantees и выбор текущего оформления, offsets и raw serialization. Один дополнительный context/header row, другой line-wrapping text run или перестановка таблиц ломают test при тех же120студентах и metrics.

**Переписать так:** Разбирать реальные Word/worksheet namespace-aware XML и HTML; находить logical header+student row, сравнивать supplied metrics/data и все120students. Проверять numeric cells как числа/типы, literal formula-like name как text и отсутствие formulas. Не фиксировать row19/20,width,cantSplit serialization.

**Граница и сохранение:** Pure actual renderer test. Не менять правильный supplied26.3% на пересчитанное7/28; renderer обязан сохранять supplied value. DB N/A.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsTabularRenderer.java:199](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsTabularRenderer.java#L199) — метод: sheetXml; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsDocxRenderer.java:155](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsDocxRenderer.java#L155)

#### AR-R07 — PNG dimensions не доказывают полноту22 недель

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanStatsTrendRendererTest.java:45](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanStatsTrendRendererTest.java#L45) — метод: pngKeepsAllSemesterWeeksInBoundedTableAtFixedChartWidth; assertionLine: 56; cases: 22week semester trend.

**Сейчас:** ImageIO decode,signature,MIME,size плюс exact1080×1148.

**Проблема:** Exact width/height копируют layout constants и формулу; проверка не читает ни одного из22period labels/values и потому не доказывает название теста. Безопасная смена row height или chart padding ломает размеры. Пропуск последних labels при сохранённом canvas оставляет test зелёным.

**Переписать так:** Сохранить actual PNG decode/signature/MIME and20MiB bound, убрать pixel constants. Полноту periods/numerators/denominators проверять как данные в реальном HTML export; если22видимые строки PNG — отдельный принятый критерий, проверить готовый rendered artifact при разрешённой приёмке и честно не выдавать canvas size за доказательство.

**Граница и сохранение:** Pure encoder test для формата; полнота визуального PNG UNKNOWN в этом source-only аудите. Не добавлять тяжелую snapshot/OCR кампанию. DB N/A; текущие frozen runtime controls сохраняются.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsTrendRenderer.java:34](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsTrendRenderer.java#L34); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsTrendRenderer.java:58](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsTrendRenderer.java#L58) — метод: renderPng; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsTrendRenderer.java:136](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsTrendRenderer.java#L136) — метод: pngHeight

#### AR-R08 — Weekly HTML/XLSX — реальные marks вместо CSS/colors

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportServiceTest.java:334](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportServiceTest.java#L334) — метод: htmlAndXlsxKeepTheSameEscapedMatrixAndReadableFrozenHeaders; cases: oneWeekModel Alpha<script>,PRESENT.

**Сейчас:** Проверяет escaped name, CSS border-collapse/overflow-x, widths34/24, exact frozen ySplit7,style index1 и fill color FFEAF1F7.

**Проблема:** Часть test проверяет оформление/конкретную генерацию, а правильный attendance mark в этих direct HTML/XLSX вообще не сопоставляется. Вынесенный stylesheet/новый color/style index/metadata row ломают test без data regression; PRESENT→ABSENT cell может не затронуть ни один текущий assert.

**Переписать так:** В реальных HTML/XLSX найти именованную строку и колонку lesson по semantic header, проверить Alpha<script> как literal text и+ для lesson100, без незаданных marks. Сохранить valid XML и при нужде frozen header относительно найденной header row, не magic ySplit/color/width.

**Граница и сохранение:** Actual tabular renderer unit с tiny explicit model достаточно; business dated-roster rules остаются в service test. DB N/A для этого formatting/encoding method.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyTabularRenderer.java:24](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyTabularRenderer.java#L24) — метод: renderHtml; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyTabularRenderer.java:120](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyTabularRenderer.java#L120)

#### AR-R09 — Teacher exports — numeric/wire контракт без XLSX addresses/CSS

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportServiceTest.java:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportServiceTest.java#L50) — метод: htmlExportReadsAllJournalBatchesAndLeavesFutureMarksBlank; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportServiceTest.java:77](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportServiceTest.java#L77) — метод: xlsxHasFrozenHeadingsAndNumericMetricCells; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/teacher/TeacherStatsExportServiceTest.java:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/teacher/TeacherStatsExportServiceTest.java#L37) — метод: exportsBothServerOrderedSummaryScopesInAllFormatsWithValidXmlAndNoZeroPercent; cases: STUDENTS/GROUPS × docx/pdf/png/html/xlsx.

**Сейчас:** Требует overflow:auto,width30/14/18, x/y split6/10, literal C7/F7 markup и layout-dependent header counts среди полезных all-batches,blank future,numeric metrics,MIME/no-data проверок.

**Проблема:** Позиции и raw styles создают refactor brittleness, не сохраняя дополнительно server data/authz риск. Добавление context row или equivalent number-format/style serialization ломает C7/F7; width/CSS refactor не меняет exported metric. Корректная batching50+50+1 вместо100+1 не должна ломать полноту; лимит каждого upstream batch100 остаётся.

**Переписать так:** Parse actual HTML/XLSX by header/date/student and compare all requested lessons, future blank and correct metrics as numeric types. Keep XML validity, MIME/filename, no0/0 percent, scope metadata/hash. Для batching assert каждый batch≤100 и union exact lesson IDs once, не конкретную partition; даты/статусы domain text остаются.

**Граница и сохранение:** Actual export service/native renderers with explicit remote converter seam sufficient for encoding; all auth/scope queries сохраняются отдельно, не объявлять mock converter настоящим PDF/PNG acceptance. DB N/A для pure export encoding. User-path acceptance требует real HTTP+actual Mongo+authority when semantics selected; не заменять им дешёвые format cases.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java:139](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java#L139) — метод: export; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java:685](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java#L685) — метод: xlsx; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java:1007](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java#L1007) — метод: render; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherStatsExportService.java:75](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherStatsExportService.java#L75) — метод: export; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherStatsTabularRenderer.java:145](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherStatsTabularRenderer.java#L145)

#### AR-R10 — DOCX matrix проверять по данным независимо от шести колонок

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRendererTest.java:43](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRendererTest.java#L43) — метод: subjectJournalKeepsEveryLessonAndStudentAcrossSixColumnSegments; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRendererTest.java:91](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRendererTest.java#L91) — метод: eighteenWeekSemesterKeepsThirtyStudentsAllThirtySixLessonsAndTotals; [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRendererTest.java:139](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRendererTest.java#L139) — метод: weeklyBatchKeepsCallerWeekBoundariesAndAddsASectionForEachModel.

**Сейчас:** Exact3/7tables,6segments,8/4cells,table index6 summary,pagebreak2/3 и повторение каждого имени7раз.

**Проблема:** Полезные реальные DOCX данные/blank future/summaries спрятаны за предполагаемой текущей пагинацией и fixed table offsets. Семь lesson columns на segment вместо6, другой summary placement или несколько runs на text сохраняют все36×30cells и totals, но текущие structural assertions ломаются.

**Переписать так:** Namespace-aware XML: collect logical matrix columns by lesson/date/type heading and rows by student display names across however many segments, compare every expected cell/blank and supplied summary. Keep36lessons,30students,noID leak,placeholder-free valid DOCX, caller week boundaries/order and invalid-kind exception. Не требовать fixed6groups/pagebreak count.

**Граница и сохранение:** Pure actual DOCX renderer test; никакого DB/API для package/data projection. DB N/A; current frozen browser/converter execution remains untouched.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRenderer.java:41](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRenderer.java#L41); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRenderer.java:139](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRenderer.java#L139) — метод: matrixTables; [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRenderer.java:158](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRenderer.java#L158) — метод: matrixTable

#### AR-R11 — Сохранить полноту дат/типов DOCX без привязки к внутренним placeholder

**Где:** [services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyTemplateSmokeTest.java:29](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyTemplateSmokeTest.java#L29) — метод: templateContainsEveryExpectedPlaceholderAsPlainText.

**Сейчас:** Inventory ожидает все 6 date*\* и 30 type*\* placeholders в plain-text template.

**Проблема:** Переименование placeholder с согласованным renderer ломает тест, но полностью удалять его сейчас нельзя: реальные renderer-тесты сверяют только две даты и одинаковые LK, не каждый тип/день. Убрать date_tue либо type_tue_2 из template: inventory заметит пропажу; оставшиеся renderer assertions могут пройти. Это source-based counterexample, не mutation run.

**Переписать так:** Вызвать настоящий renderer на шести различных допустимых датах и типах для всех существующих 6 дней × 5 пар; прочитать semantic DOCX cells и проверить каждую дату и тип по независимым sentinel inputs. Сохранить XML validity/no-unfilled-placeholder и настоящее escaping в существующих renderer-тестах. До замены inventory KEEP.

**Граница и сохранение:** Дешёвый isolated renderer unit с настоящим template, без БД/сети. Уточнение: main caller старого DocxRenderer не найден; это сохранение legacy-artifact гарантий, не утверждение текущей production-регрессии и не разрешение retirement.

**Проверенный контракт:** [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/DocxRenderer.java:40](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/DocxRenderer.java#L40) — метод: render

### Auth/Gateway: сессии, admission и transport

#### AG-R01

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/BcryptDoSMitigationIT.java:54](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/BcryptDoSMitigationIT.java#L54) — метод: concurrentInvalidPassword_rejectsWithoutCpuBlowup.

**Сейчас:** 50 concurrent wrong passwords одного student, хотя комментарий говорит разные logins; хотя бы один 429, остальные 401/429, общий 30s.

**Проблема:** При отключённом bcrypt guard LoginRateLimiter.checkBlocked109/recordFailure134 может дать 429 после ошибок того же(ip,student). Ни одна assertion не измеряет число одновременно выполняемых BCrypt работ; PASS не доказывает guard.

**Переписать так:** Использовать 3 разных synthetic существующих login и штатный guard capacity 2. Test-only PasswordEncoder decorator держит две реальные matches на latch, считает максимальную одновременность и делегирует реальному BCrypt; третий настоящий HTTP login получает 429 пока первые 2 ещё заняты, после release запросы завершаются 401. Проверить max<=2 и последующее восстановление permit после failure. Существующие isolated Boot+PG+Redis; bounded cleanup, без 50-thread timing oracle и без ослабления production settings.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/AuthService.java:106](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/AuthService.java#L106); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/BcryptConcurrencyGuard.java:46](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/BcryptConcurrencyGuard.java#L46)

#### AG-R02

**Где:** [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerIT.java:223](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerIT.java#L223) — метод: invalidSignature_returns401_downstreamNotCalled.

**Сейчас:** Rogue-key JWT получает 401, не вызывает admit/downstream.

**Проблема:** Rogue fixture230..239 также не содержит token_use,iat,sid,sv,rv,status,is_headman,readOnly. Если подпись перестанет проверяться, validateAccessClaims всё равно отвергнет отсутствующие claims.

**Переписать так:** Создать один полностью валидный signed access payload через существующий signExternalJwt helper и менять только signing key. Сначала positive control тем же payload/trusted key проходит; rogue key →401 INVALID_SESSION/no-store, zero admit/downstream calls. Реальный Gateway HTTP + существующий WireMock transport witness достаточен: DB authority здесь не доказывается.

**Проверенный контракт:** [services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java:187](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java#L187); [services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java:202](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java#L202)

#### AG-R03

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilterPurposeTest.java:120](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilterPurposeTest.java#L120) — метод: internalTokenAndMalformedAccessClaimsAreRejected.

**Сейчас:** Internal token и три malformed access forms дают null authentication.

**Проблема:** signedAccessToken371..387 для missing purpose/bad subject/bad role всегда лишён всех обязательных session claims. Исправление/поломка targeted subject/role проверки не меняет отказ по sid/sv/rv и другим отсутствующим полям.

**Переписать так:** Для каждого malformed access case брать один полностью валидный session-bound payload с positive control и менять ровно purpose, subject либо role. Проверить typed HTTP 401/INVALID_SESSION, chain не вызван и context очищен; оставить cheap isolated filter/parser boundary, без PG для этих token syntax cases. Internal-purpose отрицательный case остаётся отдельным.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilter.java:78](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilter.java#L78); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:239](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java#L239)

#### AG-R04

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OtpIT.java:158](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OtpIT.java#L158) — метод: otpVerify_withWrongCode_returns401.

**Сейчас:** Запрошенный случайный OTP сравнивается с жёстким 000000 и ожидает 401.

**Проблема:** Случайно выданный 000000 легален; один из 1e6 запросов превращает отрицательный тест в корректный вход и ложный FAIL. Запрос OTP также не проверяет successful precondition.

**Переписать так:** После HTTP 204 прочитать текущий owned Redis proof и выбрать гарантированно другое шестизначное значение; проверить 401 и отсутствие новой session/otp.verified. Existing HTTP+isolated PG/Redis fixture; никаких замен production random/TTL.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java:165](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java#L165)

#### AG-R05

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java:377](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java#L377) — метод: rolesVersionAndSessionVersionChangesProduceStaleDenial.

**Сейчас:** Первый отказ после RV/group change; затем role switch/SV change и второй stale denial.

**Проблема:** Второй запрос всё ещё несёт старую RV. Удаление SV comparison останется зелёным, поскольку старый RV mismatch продолжает отклонять.

**Переписать так:** Два независимых fixtures с positive live control: RV-only change при том же SV; SV-only role switch при полностью актуальной RV. Для второго подписать старый SV с текущей RV до переключения и доказать единственное отличие. Проверять typed SESSION_STATE_STALE и отсутствие internal token через actual /internal/auth/admit HTTP на имеющемся isolated Boot/PG fixture; сохранить отдельные lower-level clock units.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionAdmissionService.java:136](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/SessionAdmissionService.java#L136); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilter.java:176](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilter.java#L176)

#### AG-R06

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SubjectDeletionProofHttpIT.java:206](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/SubjectDeletionProofHttpIT.java#L206) — метод: wrongPasswordForeignScopeBindingAndRevocationCannotAuthorizeDeletion.

**Сейчас:** Wrong password и foreign scope отказывают; затем telegram binding сменён, proof отказан; после этого headman suspended и тот же proof снова отказан.

**Проблема:** Последний отказ может быть целиком вызван уже изменённым binding/version, поэтому сломанный role-revocation predicate не обнаружится.

**Переписать так:** Отдельный fresh proof/actor для binding-only и role-only отказа, с успешной read/verify precondition до изменения. Через действующий private HTTP путь проверить конкретный согласованный отказ и отсутствие receipt/consumed_at/otp.verified/session delta. Существующие migrated Auth PG/Redis; credential comparisons только boolean in RAM.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/SubjectDeletionProofService.java:163](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/SubjectDeletionProofService.java#L163); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/SubjectDeletionProofService.java:353](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/SubjectDeletionProofService.java#L353)

#### AG-R07

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java:252](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java#L252) — метод: snapshotWaitsForHeldGrantWriterLockAndReturnsWhollyNewTuple; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java:674](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java#L674) — метод: selectedRevokeFencesActorVersionsRevocationAndHeldGrantWriter; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java:1438](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthorityIT.java#L1438) — метод: concurrentResetIssueAndCompletionSerializeOnUserAndRejectSupersededTicket.

**Сейчас:** Настоящий PG wait доказывается helper awaitUserLockContention1791 regex по тексту SQL from auth_identities/where id=/for update.

**Проблема:** Эквивалентный SQL с public.auth_identities, alias u.id или изменённым WHERE порядком ломает observer. Чужой parallel waiter с подходящим SQL может выполнить helper, пока свой contender ещё не дошёл до lock.

**Переписать так:** Оставить real PG/latches и конечные tuple/outcome assertions. В каждом owned worker внутри существующего TransactionTemplate получить pg_backend_pid() перед настоящим adapter call; затем наблюдать pg_blocking_pids конкретного waiter и exact blocker PID. Adapter использует ту же bound connection/transaction. Убрать regexp SQL и совпадение с любым посторонним waiter; production seams и новый framework не нужны.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java:527](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java#L527); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java:619](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/jdbc/JdbcSessionAuthority.java#L619)

#### AG-R08

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/authority/AuthReportCapabilityServiceTest.java:76](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/authority/AuthReportCapabilityServiceTest.java#L76) — метод: issueTenAndRedeemTwentyLimitsRemainPerSessionAndTicket.

**Сейчас:** Одной session выдано 10 tickets,11-й отказан; один ticket redeemed20 раз,21-й отказан. MemoryStore.allowIssue375 игнорирует digest и держит global int.

**Проблема:** Ошибка production scope key/session isolation или неатомарный Lua counter не влияет на fake; глобальный counter именно в double подтверждает только single-session threshold, а имя обещает isolation.

**Переписать так:** Оставить clock/threshold units, а этот per-scope сценарий перенести на настоящий RedisReportDownloadTicketStore: две реальные session identities в isolated migrated Auth PG,10 выдач каждой независимо, два tickets с независимыми 20 redemptions, concurrent boundary имеет точное число winners, окно имеет TTL и после отказа не увеличивается. Минимум real service+PG/Redis для counter semantics; отдельную HTTP owner/private-RPC authorization гарантию не приписывать unit-store test.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthReportCapabilityService.java:165](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthReportCapabilityService.java#L165); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/RedisReportDownloadTicketStore.java:18](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/RedisReportDownloadTicketStore.java#L18)

#### AG-R09

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/authority/AuthReportCapabilityServiceTest.java:116](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/authority/AuthReportCapabilityServiceTest.java#L116) — метод: renderAuthorityIsBoundToPlanDigestJobAndLeaseWithoutChangingTicketBinding.

**Сейчас:** Ответ равен mocked purpose-bound-synthetic, verify(jwt).generateAcademicReportToken получает digest/job/lease/30s.

**Проблема:** Реальный signer может забыть report_lease/report_hash либо подписать token_use=internal; mock всё равно вернёт ожидаемую строку и verify пройдёт.

**Переписать так:** Использовать настоящий JwtService с synthetic temp key-dir; независимо parse/verify JJWT и проверить token_use=academic_report, report_kind/hash/job/lease/not_after, SID/SV/RV,expiration<=30s/session. Ordinary internal parser должен отклонить этот purpose; сохранить негативный отсутствующий lease request. Это cheap signed-token boundary, DB не требуется для signing property; live admission отдельно покрывается PG integration.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthReportCapabilityService.java:148](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthReportCapabilityService.java#L148); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:394](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java#L394)

#### AG-R10

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/OtpServiceTest.java:142](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/OtpServiceTest.java#L142) — метод: verifyOtp_constantTimeCompare_sourceAssertion.

**Сейчас:** Весь source содержит MessageDigest.isEqual и Lua names/fragments, не содержит две конкретные запрещённые строки.

**Проблема:** Неиспользуемый helper/comment с MessageDigest.isEqual сохраняет PASS при unsafe comparison на реальном пути; reindent Lua или вынесение approved helper ломает PASS при том же поведении.

**Переписать так:** Разделить независимые риски. Atomic reverse/single-use доказывать в существующем OtpIT реальным Redis: forward жив/reverse отсутствует; reverse принадлежит другому UID; direct/by-code race → ровно одна session/event и обе индексные proof записи consumed. Constant-time остаётся обязательным crypto review invariant; lexical existence заменить сфокусированным call-path review/static guard (доступен существующий ArchUnit), проверяющим оба реальных verify пути к approved JDK comparison и явно обозначенным структурным пределом. Functional HTTP/Redis assertions НЕ доказывают constant-time; security control не удалять и не ослаблять.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java:41](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java#L41); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java:165](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java#L165); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java:213](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java#L213); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java:360](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java#L360)

#### AG-R11

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/controller/AuthSessionControllerTest.java:178](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/controller/AuthSessionControllerTest.java#L178) — метод: successfulLogoutAllAndPasswordChangeInvalidateTicketsAfterDurableService; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/controller/AuthSessionControllerTest.java:197](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/controller/AuthSessionControllerTest.java#L197) — метод: committedChangesKeep204AndClearCookieWhenRedisCleanupFails.

**Сейчас:** Mockito inOrder service-before-ticket cleanup; stubs объявлены committed, при mocked cleanup failure HTTP 204/cookie clear.

**Проблема:** Service мог потерять transaction/revoke commit, но mock returns success; порядок verify не даёт durable after-commit outcome. Безопасный перенос cleanup в afterCommit callback может также нарушить interaction shape.

**Переписать так:** Сохранить дешёвые status/error mapping tests, доказательство aftercommit перенести в существующий real Boot HTTP+PG+Redis flow: сделать только owned ws_ticket_user key wrong type либо отключить cleanup dependency после login; logout/logout-all/change-password→204+clear/no-store, PG revocation/new hash/event committed, old bearer/refresh и ticket admission отказаны, control-session другого user остаётся live. Наблюдать конечный результат вместо exact call sequence.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthSessionController.java:113](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthSessionController.java#L113); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthSessionController.java:169](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthSessionController.java#L169)

#### AG-R12

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java:24](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java#L24) — метод: infoEndpointNotPublic; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java:31](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java#L31) — метод: envEndpointNotExposed; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java:38](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java#L38) — метод: beansEndpointNotExposed; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java:45](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java#L45) — метод: heapdumpEndpointNotExposed; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java:52](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java#L52) — метод: actuatorIndexNotPublic; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java:16](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/ActuatorIT.java#L16) — метод: healthEndpointReturnsUp.

**Сейчас:** Каждый anonymous actuator request даёт любой status>=400.

**Проблема:** HTTP 500/503 считается PASS. Если /env включить, но оставить authenticated, anonymous отказ останется PASS, хотя имя test обещает endpoint not exposed.

**Переписать так:** Через имеющийся real HTTP fixture закрепить точный текущий security denial для anonymous info/index; для неэкспонируемых env/beans/heapdump добавить валидный authenticated bearer и ожидать 404, отсутствие secret data/body dump. Разделить auth gate и exposure. Не менять продуктовые security/exposure настройки ради теста; health root status проверять JSON $.status вместо общего containsUP.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java:38](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java#L38); [services/auth-service/auth-app/src/main/resources/application.yml:137](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/resources/application.yml#L137)

#### AG-R13

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OpenApiSnapshotIT.java:46](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OpenApiSnapshotIT.java#L46) — метод: apiDocsMatchesSnapshot.

**Сейчас:** HTTP body /api-docs нормализован лишь удалением servers и строково равен committed JSON; /internal/ paths отсутствуют.

**Проблема:** Перестановка JSON object fields/безопасная смена summary description ломает string equality при идентичном wire. В fetch нет explicit HTTP 200/OpenAPI root assertion, update branch может принять неправильный document shape.

**Переписать так:** Перед сравнением HTTP 200+OpenAPI document shape. Сравнивать parsed semantic objects для paths/methods/status/parameters/request/response schema/formats/security/required/additionalProperties и hidden internal invariant. Игнорировать только подтверждённую документационную metadata и object-key order; не ослаблять wire/schema/security. Snapshot update не должен обходить shape/security checks. Существующий real HTTP+PG fixture, новый framework не нужен.

**Проверенный контракт:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OpenApiSnapshotIT.java:71](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OpenApiSnapshotIT.java#L71); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java:59](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/SecurityConfig.java#L59)

#### AG-R14

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilterPurposeTest.java:53](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilterPurposeTest.java#L53) — метод: setUp; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/JwtTokenPurposeTest.java:54](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/JwtTokenPurposeTest.java#L54) — метод: setUp; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java:1172](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java#L1172) — метод: createJwtService; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java:1190](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java#L1190) — метод: publicKey.

**Сейчас:** Известные synthetic ключи задаются reflection в privateKey/publicKey/keyId; internal admission ещё читает private publicKey обратно.

**Проблема:** Безопасное переименование private field или изменение key-holder ломает fixtures до проверки token contract. Они обходят публичный key loading boundary.

**Переписать так:** Передавать JwtProperties с собственным temporary key-dir, записать synthetic fixture PEM и kid штатными именами, вызвать public init(); сохранить KeyPair publicKey в fixture для independent verification. Все existing parser/purpose/raw NumericDate cases оставить; init не обращается к Redis до ApplicationReadyEvent, полный Boot/DB не требуется.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:69](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java#L69); [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:74](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java#L74)

#### AG-R15

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/TmaIT.java:55](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/TmaIT.java#L55) — метод: tma_withValidInitData_returnsTokenPair; fixtureMethod: buildValidInitDataWithAuthDate:243; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/TmaServiceTest.java:71](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/TmaServiceTest.java#L71) — метод: acceptedPastWindowIncludesExactBoundaryAndRejectsNextSecond; fixtureMethod: signed:105.

**Сейчас:** Fixture заново реализует sorted data-check-string, two-stage HMAC, encoding тем же алгоритмом, что production.

**Проблема:** Общая ошибка протокольной интерпретации в двух копиях (например одинаковая ошибка key/data или canonicalisation при последующей правке обеих копий) не имеет внешнего known-answer anchor. State/date tests при этом полезны.

**Переписать так:** Сохранить clock boundary / replay / fresh grant / HMAC negative cases. Добавить независимый reviewed fixed synthetic known-answer initData + expected HMAC vector по Telegram specification, включая ordering / URL-encoded reserved characters, затем bit-flip negative от этого fixed vector. Generator оставить для state variation, но не использовать как единственный protocol oracle. Без external Telegram acceptance / new network; N2 остаётся PENDING.

**Проверенный контракт:** [services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/TmaService.java:116](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/TmaService.java#L116)

#### AG-R16

**Где:** [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/contract/AuthTokenDtoRedactionTest.java:76](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/contract/AuthTokenDtoRedactionTest.java#L76) — метод: authAdmissionRequestRedactsTokenButKeepsJsonWire; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/contract/AuthTokenDtoRedactionTest.java:90](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/contract/AuthTokenDtoRedactionTest.java#L90) — метод: authAdmissionResponseRedactsTokenButKeepsJsonWire; [services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/contract/AuthTokenDtoRedactionTest.java:116](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/contract/AuthTokenDtoRedactionTest.java#L116) — метод: selectActiveRoleResponseRedactsTokenButKeepsJsonWire.

**Сейчас:** Секреты отсутствуют в toString; JSON содержит исходный token и round-trip его сохраняет. Дополнительно helper213 требует точный class/field prefix, helper223 — компактную JSON подстроку.

**Проблема:** Безопасный переход record → class или иной полезный формат toString с тем же редактированием секрета ломает startsWith. Семантически тот же JSON с пробелами может нарушить raw substring, хотя ниже уже проверяется parsed property.

**Переписать так:** Оставить sentinel exclusion, redaction guarantee и parsed JSON property/round-trip. Удалить exact toString prefix и дублирующую raw JSON substring assertion, не удаляя ни одного secret/wire case. Это pure DTO privacy/serialization boundary: PG/HTTP не нужны.

**Проверенный контракт:** [services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthAdmissionRequest.java:17](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthAdmissionRequest.java#L17); [services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthAdmissionResponse.java:68](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthAdmissionResponse.java#L68); [services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/SelectActiveRoleResponse.java:25](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/SelectActiveRoleResponse.java#L25)

### Shared/Notification/BFF/renderer

#### BS-R01

**Где:** [services/shared/shared-outbox/src/test/java/ru/rutcampustrack/shared/outbox/OutboxPublisherJobTest.java:194](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-outbox/src/test/java/ru/rutcampustrack/shared/outbox/OutboxPublisherJobTest.java#L194).

**Сейчас:** sendOrder sender игнорирует type/payload и добавляет callOrder.incrementAndGet(); budget sender добавляет rows.get(published.size()).id().

**Проблема:** Отправка c,b,a по-прежнему даёт [1,2,3] в sendOrder. Budget witness сам записывает ожидаемый prefix, даже если sender получил другой carrier; markSent assertions частично сохраняют budget risk, но не доказывают identity отправленного события.

**Переписать так:** Оставить дешёвый unit с injected monotonic clock. Дать каждой строке уникальный payload/event_id, записывать фактические (type,payload) из sender и сравнить с фиксированным FIFO oracle; проверить markSent только для фактически подтверждённого prefix, remainder pending. Не вычислять expected из номера вызова. DB commit/rollback доказывать существующим соответствующим service IT, а не этим unit.

**Граница и сохранение:** FIFO identity/order; elapsed budget; confirmed prefix; retryable remainder; commit metrics separate from broker acceptance

**Cases/граница:** название: sendOrder_matchesReadOrder; строка: 194; название: elapsedBatchBudgetCommitsOnlyTheConfirmedFifoPrefixAndLeavesRemainderPending; строка: 32

**Проверенный контракт:** [services/shared/shared-outbox/src/main/java/ru/rutcampustrack/shared/outbox/OutboxPublisherJob.java:102](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-outbox/src/main/java/ru/rutcampustrack/shared/outbox/OutboxPublisherJob.java#L102); [services/shared/shared-outbox/src/main/java/ru/rutcampustrack/shared/outbox/OutboxPublisherJob.java:117](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-outbox/src/main/java/ru/rutcampustrack/shared/outbox/OutboxPublisherJob.java#L117)

#### BS-R02

**Где:** [services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/PushSubscriptionRepositoryTest.java:20](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/PushSubscriptionRepositoryTest.java#L20).

**Граница:** весь указанный файл; все его тесты учтены.

**Сейчас:** Первый метод проверяет наличие метода initIndexes, второй наличие его @PostConstruct; оба не смотрят индекс.

**Проблема:** Пустой initIndexes с той же аннотацией оставит оба green; безопасное переименование lifecycle method сломает оба при неизменных индексах.

**Переписать так:** Заменить оба метода проверкой production startup/index initialization в изолированной реальной Mongo: listIndexes key order/options; same(user,endpoint) duplicate rejected, другой user с тем же endpoint допустим, group index присутствует. Дополнительно не потерять last_seen/reminder indexes, если тест станет whole-config acceptance. Только нужный Mongo layer.

**Граница и сохранение:** unique user/endpoint ownership; group index existence

**Cases/граница:** название: pushMongoConfig_hasUniqUserEndpointIndex; строка: 20; название: pushMongoConfig_hasGroupIdIndex; строка: 33

**Проверенный контракт:** [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/PushMongoConfig.java:24](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/PushMongoConfig.java#L24)

#### BS-R03

**Где:** [services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/security/SecurityIdorIT.java:113](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/security/SecurityIdorIT.java#L113).

**Сейчас:** Seed содержит одну own и одну foreign unread запись; list проверяет лишь totalElements=1, count лишь count=1, foreign mutation лишь status403.

**Проблема:** Ошибочная фильтрация, возвращающая единственную запись B вместо A, удовлетворит list/count. Обновление чужой записи с последующим 403 не поймает status-only mutation test.

**Переписать так:** Сохранить signed identity + real Mongo; вызвать actual HTTP endpoint. Seed разные own/foreign counts, сохранить ids, проверить exact own IDs/no foreign payload, count при нескольких разных unread/read состояниях; после forbidden write проверить foreign read_at и собственную историю в БД. Nonexistent403 сохранить отдельно.

**Граница и сохранение:** foreign data isolation; signed identity; non-disclosing 403; no unauthorized write; unread count scoped to owner

**Cases/граница:** название: list_returnsOnlyOwnNotifications; строка: 113; название: unreadCount_returnsOnlyOwnUnreadCount; строка: 136; название: markAsRead_otherUsersNotification_returns403; строка: 122

**Проверенный контракт:** [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationController.java:39](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationController.java#L39); [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationController.java:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationController.java#L50); [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryRepository.java:14](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryRepository.java#L14); [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryRepository.java:23](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryRepository.java#L23)

#### BS-R04

**Где:** [services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/ws/StompIntegrationIT.java:189](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/ws/StompIntegrationIT.java#L189).

**Сейчас:** Negative handshake принимает ExecutionException или TimeoutException; positive ждёт фиксированные 300ms перед broadcast.

**Проблема:** Неотвечающий сервер даёт TimeoutException и проходит auth negative. На медленном scheduler subscription ещё не установлена через 300ms, корректная доставка ложно красная.

**Переписать так:** Для missing/invalid ticket assert фактический HTTP Upgrade denial 401/handshake cause, с рабочим same-server valid control; timeout считать failure. Для group positive синхронизировать receipt/subscription readiness либо ограниченным повтором положительного сообщения, как existing PrivateWsTransportIT positive/control. Сохранить реальный socket/broker, закрывать client/resources в finally.

**Граница и сохранение:** missing ticket denied; consumed/invalid ticket denied; own group message delivered; actual WS transport

**Cases/граница:** название: missingTicketRejectsHandshake; строка: 189; название: invalidTicketRejectsHandshake; строка: 206; название: validTicketSubscribesAndReceivesBroadcast; строка: 118

**Проверенный контракт:** [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java#L50); [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java:61](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java#L61)

#### BS-R05

**Где:** [services/document-renderer-service/document-renderer-app/src/test/java/ru/rutcampustrack/documentrenderer/render/OfficeDocumentConverterTest.java:24](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/document-renderer-service/document-renderer-app/src/test/java/ru/rutcampustrack/documentrenderer/render/OfficeDocumentConverterTest.java#L24).

**Сейчас:** Payload + cleanup и containsExactly полного argv soffice в фиксированном порядке.

**Проблема:** Перестановка независимых --headless/--nologo/--nofirststartwizard при том же корректном PDF ломает test.

**Переписать так:** Сохранить cheap process seam, assertions результата и cleanup. Проверять семантически обязательные command/input/output/options без порядка независимых флагов. Реальную читаемость PDF, Cyrillic, pages, PNG и отсутствие временных файлов продолжает NativeIT; не удалять validation/failure/parser units.

**Граница и сохранение:** renderer output; task isolation/cleanup; correct native conversion contract

**Cases/граница:** название: convertToPdfReturnsRendererOutputAndCleansTaskDirectory; строка: 24

**Проверенный контракт:** [services/document-renderer-service/document-renderer-app/src/main/java/ru/rutcampustrack/documentrenderer/render/OfficeDocumentConverter.java:37](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/document-renderer-service/document-renderer-app/src/main/java/ru/rutcampustrack/documentrenderer/render/OfficeDocumentConverter.java#L37)

#### BS-R06

**Где:** [services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentSessionProjectionTest.java:124](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentSessionProjectionTest.java#L124).

**Сейчас:** getDeclaredMethod("history"/"lesson"), setAccessible и вызов private mapper, затем DTO/JSON assertions.

**Проблема:** Переименование private mapper при неизменном public response краснит. Если attendanceResponse перестанет включать explanation, private helper test остаётся green.

**Переписать так:** Подать projection с day lesson и subject/type history через MobileAttendanceClient seam, вызвать service.attendance либо signed HTTP boundary и проверить итоговый response/JSON. Дешёвый service boundary достаточен для pure projection; Mongo здесь не требуется.

**Граница и сохранение:** full long comment; history id remains separate; optional legacy explanation absent; cancellation reason vs comment; public JSON null/absence

**Cases/граница:** название: attendanceExplanationMapsNestedOptionalDataAndFullCommentWithoutJoiningIds; строка: 124

**Проверенный контракт:** [services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java:175](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java#L175); [services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java:582](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java#L582); [services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java:648](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java#L648); [services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java:702](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java#L702)

#### BS-R07

**Где:** [services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/map/MapQueryFacadeTest.java:35](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/map/MapQueryFacadeTest.java#L35).

**Сейчас:** facade.manifest return игнорируется; только verify academic.manifest(7/0).

**Проблема:** facade может вызвать academic.manifest и затем вернуть null/неверный revision: оба метода green; настоящий controller result.revision упадёт.

**Переписать так:** Сохранить teacher/headman admission + cheap ETag parsing unit, но assert возвращённого ManifestResult. Для публичной cache semantics связать с existing actual HTTP+gRPC harness: 200 manifest/ETag/private-no-cache, matching ETag→304 без body, teacher/headman scope. Admin rejection сохранить отдельно.

**Граница и сохранение:** teacher map read; headman student scope; ETag revision forwarding; public cache/status/body

**Cases/граница:** название: activeTeacherCanUseManifestAndRevisionEtagIsForwarded; строка: 35; название: headmanUsesStudentRepresentationAndStudentScopeStillApplies; строка: 49

**Проверенный контракт:** [services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/map/MapQueryFacade.java:24](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/map/MapQueryFacade.java#L24); [services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/map/MapApiController.java:30](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/map/MapApiController.java#L30)

#### BS-R08

**Где:** [services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/exception/NotificationErrorHandlingIT.java:144](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/exception/NotificationErrorHandlingIT.java#L144).

**Сейчас:** Ручной RequestContext role=TEACHER в legacy-enabled suite якобы вызывает forbidden DELETE /push/subscribe.

**Проблема:** Текущий production допускает TEACHER/ADMIN/STUDENT и требует coherent signed selected-role. Тест закрепляет старый запрет, а ручной context не содержит signed fields; это не повод возвращать teacher ban.

**Переписать так:** Заменить forbidden fixture принятой запрещённой операцией (foreign notification read) с signed request/isolated Mongo, либо оставить локальный handler unit с явной domain AccessDeniedException seam. Проверить 403/problem type/content/title. Validation400/malformed400/405 методы сохранить; убрать ненужную инфраструктуру только после сохранения их границ, не менять product roles.

**Граница и сохранение:** local domain exception→403 problem contract; current accepted teacher/admin enrollment; signed admission

**Cases/граница:** название: accessDeniedReturns403; строка: 144

**Проверенный контракт:** [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/PushController.java:83](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/PushController.java#L83); [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/security/RequestContext.java:78](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/security/RequestContext.java#L78); [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/security/RoleCheckAspect.java:26](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/security/RoleCheckAspect.java#L26)

#### BS-R09

**Где:** [services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/config/WebSocketConfigTest.java:27](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/config/WebSocketConfigTest.java#L27).

**Сейчас:** Создаёт scheduler напрямую; проверяет executor != null и точный thread prefix. Daemon flag/heartbeat wiring не проверяются.

**Проблема:** Удаление setTaskScheduler/setHeartbeatValue из configureMessageBroker оставляет test green; изменение diagnostic prefix ложно краснит.

**Переписать так:** Проверять production broker wiring: STOMP CONNECTED heart-beat 10000,10000 и подключённый рабочий scheduler. Допустим cheap broker/config boundary с controllable scheduler; если проверять wire idle heartbeat — bounded observable frame, без SLA на scheduling. Prefix не самостоятельный contract.

**Граница и сохранение:** heartbeat negotiation/work; idle transport recovery; scheduler cleanup

**Cases/граница:** название: stompHeartbeatScheduler_isInitializedDaemonThread; строка: 27

**Проверенный контракт:** [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java:78](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java#L78); [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java:94](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java#L94)

#### BS-R10

**Где:** [services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumerIT.java:177](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumerIT.java#L177).

**Сейчас:** В replay path verify MongoTemplate.exists с exact event_id/consumer_id Query и never tryClaim; рядом полноценные persisted/read-state/privacy assertions.

**Проблема:** Эквивалентный committed replay lookup через findOne или иной safe atomic path ломает тест при том же history/read state. Просто убрать spy без замены delivery witness ослабит доказательство обработки replay.

**Переписать так:** Сохранить три разные initial/event состояния. Заменить exists/query-call oracle наблюдаемым фактом обработки replay/ACK и invariant БД: ровно прежние recipients/payload/read_at/claim, DLQ пуст, поздние recipients не добавились. У EventIdempotentIT уже есть реальный ACK/replay witness, переиспользовать принцип адресно, не сливать разные риски.

**Граница и сохранение:** lookup failure rollback; immutable historical audience; read state retained; sanitized homework; one-off cancellation; broker replay processed

**Cases/граница:** название: groupFailureRollsBackClaimAndCommittedReplayKeepsReadState; строка: 177; название: homeworkUpdatedUsesDatedAudienceSanitizedSnapshotAndIdempotentReplay; строка: 295; название: oneOffCancellationKeepsDatedRecipientHistoryAndReadStateOnReplay; строка: 397

**Проверенный контракт:** [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumer.java:32](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumer.java#L32); [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumer.java:45](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumer.java#L45)

#### BS-R11

**Где:** [services/shared/shared-observability/src/testFixtures/java/ru/rutcampustrack/shared/observability/GrpcDeadlineArchRules.java:58](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-observability/src/testFixtures/java/ru/rutcampustrack/shared/observability/GrpcDeadlineArchRules.java#L58).

**Сейчас:** Имя вызова withDeadlineAfter/withDeadline в том же public \*GrpcClient методе; getters без параметров пропущены.

**Проблема:** stub.withDeadlineAfter(...); return stub.rpc(...) проходит presence rule, хотя immutable stub с deadline не используется. getActiveSemester() пропускается isAccessor вообще. Вынесение корректного deadlined stub в helper даёт ложный red.

**Переписать так:** Не удалять invariant bounded downstream calls. Убрать blanket getter exemption; поддержать принятую delegation/helper форму и проверять deadline на фактическом ClientCall/CallOptions либо controlled nonresponding gRPC dependency, включая getter RPC. Добавить oracle sensitivity к ignored immutable return и valid helper delegation; текущая static name presence не является timeout proof.

**Граница и сохранение:** every actual gRPC call bounded; getter RPC included; safe helper refactor

**Cases/граница:** название: invokeWithDeadline; строка: 58; название: isAccessor; строка: 103

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/AcademicGrpcClient.java:84](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/AcademicGrpcClient.java#L84); [services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClient.java:273](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AcademicGrpcClient.java#L273)

#### BS-R12

**Где:** [services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/grpc/ServiceIdentityServerInterceptorTest.java:317](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/grpc/ServiceIdentityServerInterceptorTest.java#L317).

**Сейчас:** В headers отправлено "bad", но status description проверяется только на отсутствие другого ACADEMIC_TOKEN; credential.toString valid-token check полезен.

**Проблема:** reject.description="Invalid token: bad" утечёт фактический input и пройдёт doesNotContain(ACADEMIC_TOKEN).

**Переписать так:** Передать уникальный synthetic malformed marker и отдельный canonical-but-invalid token, assert отсутствие именно переданных значений в status/exception/log capture; сохранить credential.toString check и UNAUTHENTICATED/no-handler assertions.

**Граница и сохранение:** credential privacy; safe denial description; no token emission/handler access

**Cases/граница:** название: tokenNeverAppearsInCredentialStringOrStatusDescription; строка: 317

**Проверенный контракт:** [services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/grpc/ServiceIdentityServerInterceptor.java:81](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/grpc/ServiceIdentityServerInterceptor.java#L81); [services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/grpc/ServiceIdentityServerInterceptor.java:116](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/grpc/ServiceIdentityServerInterceptor.java#L116); [services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/grpc/ServiceIdentityServerInterceptor.java:138](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/grpc/ServiceIdentityServerInterceptor.java#L138)

#### BS-R13

**Где:** [services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/PushSubscriptionCleanupJobIT.java:110](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/PushSubscriptionCleanupJobIT.java#L110).

**Сейчас:** Запись названа boundary, но last_seen=cutoff−1s; после cleanup коллекция пуста.

**Проблема:** Регрессия strict before (<) в inclusive <= не обнаружится: точного равенства в fixture нет.

**Переписать так:** В том же real Mongo/fixed clock fixture сохранить записи cutoff−1s, ровно cutoff, cutoff+1s и future. Удаляется только before cutoff, остальные IDs остаются. Сохранить legacy missing-last_seen backfill отдельным тестом.

**Граница и сохранение:** retention boundary; fresh/future subscription retained; backfill history

**Cases/граница:** название: cleanup_onExactBoundary_treats90daysAsEligible; строка: 110

**Проверенный контракт:** [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/PushSubscriptionCleanupJob.java:63](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/PushSubscriptionCleanupJob.java#L63); [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/PushSubscriptionCleanupJob.java:67](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/PushSubscriptionCleanupJob.java#L67)

#### BS-R14

**Где:** [services/shared/shared-test-containers/src/testFixtures/java/ru/rutcampustrack/shared/testcontainers/ContainerTestBase.java:45](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/shared/shared-test-containers/src/testFixtures/java/ru/rutcampustrack/shared/testcontainers/ContainerTestBase.java#L45).

**Сейчас:** Eager POSTGRES/MONGO/REDIS/RABBIT startup; все withReuse(true), PG test_db, Mongo default replica URL; consumers удаляют whole collections и purge fixed queues.

**Проблема:** Параллельный/повторный suite, подключённый к тому же reused Mongo/queue, может удалить synthetic state соседа, скрыть duplicate или создать ложный fail. Изоляции test worktree/JVM недостаточно; это статический риск, наблюдённая гонка не заявлена.

**Переписать так:** Оставить actual DB/broker semantics. Назначать owned per-suite Mongo/PG database/schema и queue namespace либо private non-reused required containers; поднимать только необходимые зависимости. Cleanup ограничить своим namespace; lifecycle owner должен завершать ресурсы. Не расширять infrastructure campaign и не запускать ничего в этом read-only аудите.

**Граница и сохранение:** isolated real persistence/migrations; actual Rabbit ACK/DLQ; foreign data untouched; repeatable tests

**Cases/граница:** название: static initializer; строка: 61; название: registerProperties; строка: 69

**Проверенный контракт:** [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumer.java:28](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumer.java#L28); [services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryRepository.java:11](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryRepository.java#L11)

### Schedule

#### SCH-R01 — Подключить golden parity fixture к production calendar

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/golden/WeekParityGoldenTest.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/golden/WeekParityGoldenTest.java) — граница: DECLARATIONS; тесты: метод: mapsIsoWeekToExpectedWeekType; строка: 57; annotationLine: 54; declarationKind: ParameterizedTest.

**Сейчас:** actualWeekType вычисляется test-local ternary в строке68; golden dates сверяются с java.time.

**Проблема:** Развернуть AcademicWeekPolicy ODD/EVEN — golden test останется зелёным.

**Переписать так:** Для каждой неизменной golden row вызвать AcademicWeekPolicy.forDate(date)/weekType(date); сверить isoWeek и expectedWeekType. Не вычислять actual через локальный %2. Сохранить W53/year/leap fixtures и дешёвые реальные calculator units.

**Граница и сохранение:** Pure production calendar unit; DB/HTTP не нужны.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/week/AcademicWeekPolicy.java:29](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/week/AcademicWeekPolicy.java#L29) — контракт: Единый production weekType/forDate; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringDateCalculator.java:31](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringDateCalculator.java#L31) — контракт: Настоящий generator использует policy

#### SCH-R02 — Проверять production LockProvider и profile behavior в реальном Spring context

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/config/SchedulingConfigStructureTest.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/config/SchedulingConfigStructureTest.java) — граница: DECLARATIONS; тесты: метод: schedulingConfig_declaresLockProviderBean; строка: 29; annotationLine: 27; declarationKind: Test; метод: schedulingConfig_isProfileGuarded; строка: 46; annotationLine: 44; declarationKind: Test; [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ShedLockSmokeIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ShedLockSmokeIT.java) — граница: DECLARATIONS; тесты: метод: twoInstances_onlyOneHoldsLock; строка: 60; annotationLine: 59; declarationKind: Test.

**Сейчас:** Reflection требует @Bean в конкретном классе и exact @Profile("!test"); PG probe сам собирает JdbcTemplateLockProvider, игнорируя production factory.

**Проблема:** Вынести @Bean в импортируемый config — ложный fail. Оставить аннотации и сломать provider factory/usingDbTime/qualifier — оба reflective метода и ручной PG factory probe останутся зелёными.

**Переписать так:** На существующем изолированном PG+Flyway поднять два минимальных Spring contexts с production scheduling configuration/DataSource, взять фактические LockProvider beans и сохранить acquire/contention/minimum-hold assertions. Test profile проверить по фактическому отсутствию активированных cron tasks/provider configuration, без exact annotation/class location. Не расширять до всего сервиса.

**Граница и сохранение:** Existing AnnotationConfigApplicationContext + existing isolated PostgreSQL migration harness; без новой framework dependency.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/config/SchedulingConfig.java:18](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/config/SchedulingConfig.java#L18) — контракт: Production @Profile, EnableScheduling/Lock и real DataSource provider; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java:80](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java#L80) — контракт: Потребитель scheduler lock

#### SCH-R03 — Убрать зависимость race rendezvous от exact SQL spelling

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/HomeworkBindingServiceIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/HomeworkBindingServiceIT.java) — граница: DECLARATIONS; тесты: метод: batchReadAndMoveWithReversedOriginIdsCompleteUnderConcurrentLocks; строка: 1463; annotationLine: 1462; declarationKind: Test.

**Сейчас:** JdbcTemplate subclass pauses только при sql.equals("SELECT id FROM schedule_items WHERE id = ? FOR UPDATE"); latches плюс real PG locks проверяют reversed-origin read/move.

**Проблема:** Добавить alias/whitespace или bulk ordered lock query с теми же guarantees: rendezvous не срабатывает, тест падает по timeout.

**Переписать так:** Оставить reversed occurrence/origin IDs, реальные concurrent transactions, bounded futures и final placement. Координировать отдельной принадлежащей тесту transaction, удерживающей high origin, и pg_blocking_pids/backend PID: reader должен удержать lower origin до ожидания higher, writer должен ждать reader; затем release holder и проверить оба результата/unchanged receipt rules. Не matching SQL source text.

**Граница и сохранение:** Real isolated PG with production services; existing transaction/JDBC tools, no mock result oracle.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/homework/HomeworkBindingService.java:325](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/homework/HomeworkBindingService.java#L325) — контракт: Batch read obtains origin/current/binding locks; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/homework/HomeworkBindingService.java:444](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/homework/HomeworkBindingService.java#L444) — контракт: Sorted origin locking; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/homework/HomeworkPlacementService.java:575](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/homework/HomeworkPlacementService.java#L575) — контракт: Move использует тот же lock graph

#### SCH-R04 — Измерять актуальный journal endpoint/query

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonPerformanceIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonPerformanceIT.java) — граница: DECLARATIONS; тесты: метод: weekJournalQueryStaysUnder50ms; строка: 145; annotationLine: 144; declarationKind: Test.

**Сейчас:** Сидит12k lessons, но меряет findByScheduleItemIdInAndDateBetweenAndStatusIn без production callers; best-of-five<50ms и только nonempty.

**Проблема:** Сломать actual pageByGroupId... filtering/current pointer/countQuery или сделать его N+1/slow: этот test не вызывает этот путь и зелёный. Один быстрый run маскирует остальные медленные.

**Переписать так:** Использовать тот же synthetic data volume для actual HTTP group journal request (или minimal actual service+repository если проверяется SQL budget), asserted exact IDs/page totals/status/scope включая one-off/retained history. Зафиксировать одинаковые Moscow dates для seed и request. Доказать bounded query count/paging и выбранный operational latency criterion на одном обозначенном окружении; не считать min из пяти доказательством p95. Не вводить новый load framework.

**Граница и сохранение:** Actual HTTP + existing PG/Flyway for endpoint claim; outbound Academic/Rabbit controlled as in existing harness.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java:550](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java#L550) — контракт: Current user journal paging path; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java:146](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java#L146) — контракт: Old unpaged query; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java:220](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java#L220) — контракт: Actual group+current/history SQL paging

#### SCH-R05 — Уточнить cap oracle и наполнить страницу

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/PaginationCapIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/PaginationCapIT.java) — граница: DECLARATIONS; тесты: метод: getLessons_sizeExceedsMax_truncatedToServiceCap; строка: 27; annotationLine: 26; declarationKind: Test.

**Сейчас:** Пустая/неизвестная dataset, assertion только page.size<=200.

**Проблема:** Игнорировать size и всегда использовать default50, возвращатьsize0/1: тест зелёный, сервис теряет заявленный cap/paging behavior.

**Переписать так:** Synthetic own group with >200 matching lessons: actual oversized HTTP request должен вернуть page.size=200, ровно200 корректных уникальных IDs, правильныйtotal и следующий page без overlap; normal small requested size должен сохраняться. Реальный scope/status filters оставить.

**Граница и сохранение:** Actual HTTP + isolated PG/Flyway; existing harness/reset, no new framework.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/resources/application.yml:11](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/resources/application.yml#L11) — контракт: Service max200/default50; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java:550](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java#L550) — контракт: Actual Pageable passed to PG query

#### SCH-R06 — Заменить ложный day-regeneration test на accepted-envelope identity rejection

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonGenerationIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/LessonGenerationIT.java) — граница: DECLARATIONS; тесты: метод: updateScheduleFieldsReGenerates; строка: 219; annotationLine: 218; declarationKind: Test.

**Сейчас:** PUT c изменённым weekday не имеет обязательного Idempotency-Key и expectedRevision, ожидает400 и три старые даты.

**Проблема:** Удалить validateUpdate immutable-day check: тест останется зелёным из-за missing-header400. Его название обещает поведение, которое текущий контракт запрещает.

**Переписать так:** Fixed educational clock/semester: создать серию, получить current preview revision, отправить valid Idempotency-Key + observed revision с изменённым day. Assert409 и exact unchanged template/physical history/current pointers/outbox/replay. Назвать по immutable slot identity. Существующее accepted room/parity coverage36 сохранить; не реализовывать старую day-regeneration функцию.

**Граница и сохранение:** Actual HTTP + existing real PG/Flyway and controlled Academic authority.

**Проверенный контракт:** [services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/ScheduleItemApi.java:78](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/ScheduleItemApi.java#L78) — контракт: PUT envelope; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemLifecycleWriter.java:762](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemLifecycleWriter.java#L762) — контракт: Subject/day/number/time immutable ->Conflict

#### SCH-R07 — Уточнить legacy authorization gate вместо arbitrary exceptions

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/item/ScheduleItemSecurityTest.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/item/ScheduleItemSecurityTest.java) — граница: DECLARATIONS; тесты: метод: requireHeadmanForGroup_headmanOfCorrectGroup_allowed; строка: 84; annotationLine: 83; declarationKind: Test; метод: requireHeadmanForGroup_headmanOfWrongGroup_throws403; строка: 105; annotationLine: 104; declarationKind: Test; метод: requireHeadmanForGroup_notHeadman_throws403; строка: 122; annotationLine: 121; declarationKind: Test; метод: requireHeadmanForGroup_adminBypasses; строка: 138; annotationLine: 137; declarationKind: Test.

**Сейчас:** Own-headman positive считает любой non-AccessDenied exception успехом; admin ловит и глотает любой RuntimeException. Негативы привязаны к отсутствию конкретных downstream calls.

**Проблема:** Сбой до/после guard с IllegalStateException/NPE может датьgreen allow test. Удаление обязательного fail-closed legacy gate с неожиданным RuntimeException не обнаружится.

**Переписать так:** Оставить дешёвый scoped unit: valid DTO canonical times; разрешённый headman/admin должен получить exact RecurringLifecycleNotReadyException из legacy gate, запрещённый caller — exact AccessDeniedException. Assert нет writes/published effects, без swallowing и оптимизационных never-read/call-order требований. Не выдавать этот compatibility gate за успешное создание current endpoint.

**Граница и сохранение:** Pure production service guard unit with controlled authority; HTTP counterpart already exists and need not be duplicated.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java:60](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java#L60) — контракт: Compatibility constructor и намеренный legacy RecurringLifecycleNotReady gate; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java:76](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java#L76) — контракт: Local+authoritative headman authorization

#### SCH-R08 — Проверять JWT identity/flags/precedence наблюдаемыми разрешениями

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilterIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilterIT.java) — граница: DECLARATIONS; тесты: метод: validInternalToken_passesFilter; строка: 93; annotationLine: 92; declarationKind: Test; метод: signedHeadmanTokenUsesStudentDomainRoleAndKeepsIdentityFlags; строка: 102; annotationLine: 101; declarationKind: Test; метод: legacyHeaders_dualMode_accepted; строка: 112; annotationLine: 111; declarationKind: Test; метод: internalToken_precedenceOverLegacy; строка: 123; annotationLine: 122; declarationKind: Test; [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilterStrictModeIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilterStrictModeIT.java) — граница: DECLARATIONS; тесты: метод: validInternalToken_strictMode_passes; строка: 43; annotationLine: 42; declarationKind: Test.

**Сейчас:** Все пять positive assertions толькоstatus!=401; HEADMAN flags и token precedence не наблюдаются.

**Проблема:** Возвращать403/500 после валидной подписи или всегда выбирать legacy identity при обеих headers: !=401 останетсяgreen. HEADMAN мог потерять headman/group flags.

**Переписать так:** Использовать actual HTTP endpoint с валидными params и exact200/body для allow; signed STUDENT groupA + conflicting legacy groupB/admin должен иметь только groupA data и403 на чужой scope. Signed HEADMAN выполняет разрешённую canonical mutation с actor/group persisted и отказывает при authoritative isHeadman=false. Strict mode positive также должен дать разрешённый result, сохранив existing malformed/expired/wrong-signer/no-header/legacy exact401 controls. Не добавлять product debug/echo endpoint.

**Граница и сохранение:** Actual HTTP + existing real PG/Flyway synthetic own/foreign rows, actual filter/aspect and signed test factory; Academic authority stub allowed.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilter.java:30](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilter.java#L30) — контракт: JWT claims ->user/role/group/headman and legacy application; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java:550](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java#L550) — контракт: Observable own/foreign group read access

#### SCH-R09 — Проверять ошибочные gRPC ответы через зарегистрированный transport/advice

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcResolveLessonIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcResolveLessonIT.java) — граница: DECLARATIONS; тесты: метод: resolveLesson_throwsNotFound_whenGroupMismatch; строка: 89; annotationLine: 88; declarationKind: Test; метод: resolveLesson_throwsNotFound_whenLessonCancelled; строка: 108; annotationLine: 107; declarationKind: Test; метод: resolveLesson_throwsNotFound_whenDateHasNoLesson; строка: 127; annotationLine: 126; declarationKind: Test; [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImplIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImplIT.java) — граница: DECLARATIONS; тесты: метод: getActiveLesson_noActiveLessons_throwsResourceNotFoundException; строка: 94; annotationLine: 93; declarationKind: Test; метод: getLessonById_nonExistentId_throwsResourceNotFoundException; строка: 170; annotationLine: 169; declarationKind: Test; метод: getLessonsByGroup_dateFromAfterDateTo_throwsIllegalArgumentException; строка: 213; annotationLine: 212; declarationKind: Test.

**Сейчас:** Direct Java calls ловят ResourceNotFoundException/IllegalArgumentException, gRPC server в общем test profile disabled(port=-1).

**Проблема:** Удалить/сломать @GrpcAdvice mapping или регистрацию service/interceptor: direct exception tests не меняются, клиент вместо NOT_FOUND/INVALID_ARGUMENT получаетUNKNOWN/INTERNAL.

**Переписать так:** Через существующие grpc-java/generated stubs и локальный зарегистрированный server/advice/interceptor отправить те же requests: assert Status.NOT_FOUND или INVALID_ARGUMENT, никаких успешных payloads. Оставить реальные PG fixtures и cancelled/foreign/absent различия; actual signed/directed synthetic identity только где endpoint contract требует. Другие быстрые selection units не требуется переносить blanket.

**Граница и сохранение:** Actual local gRPC transport + registered production advice/interceptors + isolated PG migrations; existing dependencies only.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/GrpcExceptionAdvice.java:12](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/GrpcExceptionAdvice.java#L12) — контракт: Actual error -> Status contract; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java:150](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java#L150) — контракт: Notfound reads; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java:195](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java#L195) — контракт: Invalid date range; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java:331](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java#L331) — контракт: Canonical resolve

#### SCH-R10 — Сделать gRPC selection oracle чувствительным к неверной сортировке/фильтрам

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImplIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImplIT.java) — граница: DECLARATIONS; тесты: метод: getActiveLesson_multipleActiveLessons_returnsFirstByLessonNumber; строка: 110; annotationLine: 109; declarationKind: Test; метод: getLessonsByGroup_happyPath_returnsAllMatchingLessons; строка: 185; annotationLine: 184; declarationKind: Test.

**Сейчас:** В active tie fixture lesson#1 создан раньше#2; group positive имеет только3 own rows и assertion hasSize3.

**Проблема:** Заменить ORDER BY lesson_number наORDER BY id — tie testgreen. Убратьgroup/semester/range фильтр из group query при отсутствии примесей — group positivegreen.

**Переписать так:** Вставить #2 раньше#1 и assert#1 с большимID. Для group query добавить foreign group, foreign semester, outside-range и superseded generation, assert exact expected IDs/statuses/occurrence uniqueness, включая boundary dates; не толькоsize. Для tie не требовать физического time overlap: production выбирает active по дате.

**Граница и сохранение:** Current direct gRPC service + real PG sufficiently proves selection; wire error registration handled separately SCH-R09.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java:308](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java#L308) — контракт: Active order by number,id (timestamp используется только какdate); [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java:199](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java#L199) — контракт: Group semester/date/status selection; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java:205](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java#L205) — контракт: Canonical group query

#### SCH-R11 — Проверять возвращённые IDs/status, а не только количество filtered rows

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleViewIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleViewIT.java) — граница: DECLARATIONS; тесты: метод: defaultStatusFilter_excludesCancelled; строка: 136; annotationLine: 135; declarationKind: Test; метод: explicitStatusFilter_includesCancelled; строка: 151; annotationLine: 150; declarationKind: Test; [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/integration/ScheduleItemApiIT.java) — граница: DECLARATIONS; тесты: метод: listTemplates_returnsActiveOnly; строка: 275; annotationLine: 274; declarationKind: Test.

**Сейчас:** Status tests ожидают только2/3 rows, active-template listing толькоtotalElements2; IDs/status/active flags не проверены.

**Проблема:** В default отдаватьplanned+cancelled вместоplanned+active: всё ещё2. В listing отдавать одинactive+inactive вместо двухactive: total2 остаётсяgreen.

**Переписать так:** Сохранить existing inexpensive fixtures и дополнить expected exact IDs/statuses; returned templates должны быть именно двумяactive IDs, inactive ID отсутствует. Добавить только необходимую own/foreign fixture если scope утверждается; отдельный второй слой для same predicate не нужен.

**Граница и сохранение:** Existing MVC+real PG adequate; can share actual HTTP endpoint fixture where adopted without new framework.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java:559](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java#L559) — контракт: Default vs explicit status predicate; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java:121](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java#L121) — контракт: Active-only templates repo query

#### SCH-R12 — Заменить string surgery trigger body на upgraded PG behavior

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/FlywayMigrationIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/FlywayMigrationIT.java) — граница: DECLARATIONS; тесты: метод: v31ToV32KeepsExactPlacementReceiptGuardAndOldMigrationChecksums; строка: 240; annotationLine: 239; declarationKind: Test.

**Сейчас:** Удаляется exact inserted IF line из pg_get_functiondef после V32, затем весьtext сравнивается сbefore.

**Проблема:** Эквивалентный формат/имя local variable/migration insertion layout ломаетtest при тех же guards. Неисправный helper schedule_subject_delete_binding_authorized может принимать чужуюtuple при одинаковомtext основнойfunction, а testgreen.

**Переписать так:** Сохранить old migration checksums, new table, migrate-once/replay0 checks. На populatedV31 DATE/LESSON binding fixture после real V32 upgrade выполнить valid exact edit/ACK и отрицательную matrix actor/hash/scope/target/revision/no-receipt/clear-pending, проверяя rows unchanged после отказа; exact subject deletion допускает только своюtuple, чужуюdenies. Проверку rollback exact function restoration в другом test оставить — там exact rollbackstate является целью.

**Граница и сохранение:** Existing isolated-schema real PostgreSQL/Flyway migration test; no rewrite of applied migrations/product guards.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/resources/db/migration/V32\_\_subject_deletion_participant.sql:206](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/resources/db/migration/V32__subject_deletion_participant.sql#L206) — контракт: Production adds subject-deletion exception before old placement guards; [services/schedule-service/schedule-app/src/main/resources/db/migration/V24\_\_homework_edit_placement.sql:132](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/resources/db/migration/V24__homework_edit_placement.sql#L132) — контракт: Old immutable publication/exact edit receipt/ACK guards

#### SCH-R13 — Зафиксировать точные catch-up events и повторный tick

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJobIT.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJobIT.java) — граница: DECLARATIONS; тесты: метод: catchesUpMissedTransitionsOnRestart; строка: 153; annotationLine: 152; declarationKind: Test.

**Сейчас:** Три overdue PLANNED становятсяCLOSED; started/closed events проверяются как>=3.

**Проблема:** Публиковать каждое started/closed событиедважды с новымиevent_id: итогstateCLOSED и>=3 проходят, downstream увидитduplicate effects.

**Переписать так:** Assert ровно по одному started/closed накаждый из трёх конкретныхlesson IDs, schema/correlation где уже helper. Второй tick неизменногоclock не добавляетevents; rows/revisions остаются terminal. Сохранить before-grace/future/cancelled controls и actual PG transaction.

**Граница и сохранение:** Existing real PG job+fixed Clock; outbound bus may remain controlled because проверяется durable local outbox.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java:86](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java#L86) — контракт: One started+closed per transition and retry state selection

#### SCH-R14 — Сохранить D-16 как semantic teacher authority/read contract

**Где:** [services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/item/ScheduleItemEntityTest.java](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/item/ScheduleItemEntityTest.java) — граница: DECLARATIONS; тесты: метод: scheduleItem_hasNoTeacherIdField; строка: 20; annotationLine: 19; declarationKind: Test.

**Сейчас:** Reflection проверяет отсутствие ровно поляteacherId, а не эффективного teacher и исторических physical snapshots.

**Проблема:** Добавить stale teacher cache под другимименем или сломать gRPC teacher mapping: noTeacherId зелёный. Перенос field metadata при неизменной teacher authority может дать ложныйfail.

**Переписать так:** Перенести смыслfirst reflection test в уже существующий replacement PG cycle: после сменыassignment получить gRPC GetLessonById для moved current physical и retained historical source; assert assignedTeacherId701/700 и assignment/current/generation, без чтенияteacher изстарогоslot. Затем убрать reflection assertion. Приватное имя не являетсяконтрактом; D-16 и immutable history не ослаблять. Не создавать второйparallel replacementfixture.

**Граница и сохранение:** Existing real PG replacement/service flow plus actual public gRPC result; use existing dependencies/harness, no product change.

**Проверенный контракт:** [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/entity/ScheduleItem.java:32](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/entity/ScheduleItem.java#L32) — контракт: Slot references assignment; D-16 documented no teacher binding onslot; [services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java:580](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java#L580) — контракт: Public response берёт assignment и assignedTeacher из physical lesson

### Angular и frontend runner/config

#### R-A01

**Где:** [frontends/web-panel/src/app/core/auth/auth.interceptor.spec.ts:79](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.interceptor.spec.ts#L79) — метод: on 401, calls refresh and retries original request with new token; сейчас: Authorization на retry сравнивается с NEW_ACCESS_TOKEN, который побайтно равен ACCESS_TOKEN.; [frontends/web-panel/src/app/core/auth/auth.interceptor.spec.ts:123](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.interceptor.spec.ts#L123) — метод: queues concurrent requests during refresh and replays all on success; сейчас: Два concurrent запроса: только results.length===2; токены replay не проверены..

**Сейчас:** Authorization на retry сравнивается с NEW_ACCESS_TOKEN, который побайтно равен ACCESS_TOKEN.; Два concurrent запроса: только results.length===2; токены replay не проверены.

**Проблема:** Fixture не различает старый и новый access token; concurrent case дополнительно не проверяет token handoff каждому ожидающему запросу. В retry оставить исходный Bearer; NEW_ACCESS_TOKEN совпадает со старым. Оба текущих сценария могут пройти.

**Переписать так:** Оставить дешёвый HttpTestingController unit. Выдать разные JWT с той же личностью (например разный exp/jti); после refresh проверить новый Bearer в каждом replay, AuthService.accessToken и конкретные результаты обоих consumers. Сохранить один refresh, cookie/body и concurrent semantics.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/core/auth/auth.interceptor.ts:47](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.interceptor.ts#L47); [frontends/web-panel/src/app/core/auth/auth.interceptor.ts:64](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.interceptor.ts#L64)

#### R-A02

**Где:** [frontends/web-panel/src/app/core/auth/clear-all-client-state.spec.ts:25](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/clear-all-client-state.spec.ts#L25) — метод: clears localStorage; сейчас: Общий spy Storage.prototype.clear был вызван хотя бы раз.; [frontends/web-panel/src/app/core/auth/clear-all-client-state.spec.ts:31](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/clear-all-client-state.spec.ts#L31) — метод: does not throw if localStorage disabled; сейчас: Тот же shared prototype заставляет обе storage бросать; проверяется только resolves..

**Сейчас:** Общий spy Storage.prototype.clear был вызван хотя бы раз.; Тот же shared prototype заставляет обе storage бросать; проверяется только resolves.

**Проблема:** Регрессия закрывает cross-user leakage, но oracle не читает ни localStorage, ни sessionStorage. Общий prototype не различает их. Удалить sessionStorage.clear() и оставить localStorage.clear(): оба теста останутся зелёными. Это сохраняет данные предыдущего пользователя в sessionStorage.

**Переписать так:** Посеять синтетические разные ключи в обе storage; вызвать настоящий clearAllClientState и проверить отсутствие обоих ключей. Отдельно запретить clear только одной storage и убедиться, что другая всё равно очищена и Promise завершён; сохранить graceful fallback. Никаких реальных данных.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/core/auth/clear-all-client-state.ts:10](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/clear-all-client-state.ts#L10); [frontends/web-panel/src/app/core/auth/auth.service.ts:120](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.service.ts#L120)

#### R-A03

**Где:** [frontends/web-panel/src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.spec.ts:41](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.spec.ts#L41) — метод: confirm button disabled when input is empty; сейчас: confirmInput задаётся; тест сам вычисляет .trim() !== MOCK_SEMESTER.name.; [frontends/web-panel/src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.spec.ts:46](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.spec.ts#L46) — метод: confirm button disabled when input does not match semester name; сейчас: Та же самостоятельная строковая формула для wrong name.; [frontends/web-panel/src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.spec.ts:51](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.spec.ts#L51) — метод: confirm button enabled when input exactly matches semester name; сейчас: Та же самостоятельная формула для exact name..

**Сейчас:** confirmInput задаётся; тест сам вычисляет .trim() !== MOCK_SEMESTER.name.; Та же самостоятельная строковая формула для wrong name.; Та же самостоятельная формула для exact name.

**Проблема:** Тест воспроизводит выражение disabled вне шаблона. Реальное свойство кнопки и её действие не наблюдаются, хотя fixture уже смонтирован. Удалить [disabled] или сделать его false в production template: все три сравнения строк сохранят результат.

**Переписать так:** Возвратить fixture из setup; ввести empty/wrong/exact имя через input, дождаться ngModel и проверить настоящую кнопку disabled/enabled. Для enabled нажать её и получить trimmed confirmation; для disabled не должно быть close. Не добавлять запрет в production confirm() ради теста: текущая UI-граница — disabled.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.ts:20](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.ts#L20); [frontends/web-panel/src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.ts:26](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.ts#L26); [frontends/web-panel/src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.ts:36](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/semesters/delete-semester-dialog/delete-semester-dialog.component.ts#L36)

#### R-A04

**Где:** [frontends/web-panel/src/app/features/admin/semesters/semesters-page.component.spec.ts:58](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/semesters/semesters-page.component.spec.ts#L58) — метод: delete button should be disabled for active semesters; сейчас: Находит active semester в ответе и проверяет, что fixture.active===true..

**Сейчас:** Находит active semester в ответе и проверяет, что fixture.active===true.

**Проблема:** Полезный запрет удаления активного семестра заменён проверкой входного fixture. Убрать disabled из кнопки и guard if(sem.active) return из deleteSemester: текущая проверка active останется зелёной.

**Переписать так:** Смонтировать таблицу с active и inactive семестрами, прочитать кнопку active по роли/name и проверить disabled. Проверить настоящий deleteSemester(active) без dialog/DELETE как отдельную дешёвую границу уже существующего guard. Для inactive пройти диалог/confirmation до ожидаемого DELETE body существующим HttpTestingController.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/admin/semesters/semesters-page.component.html:71](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/semesters/semesters-page.component.html#L71); [frontends/web-panel/src/app/features/admin/semesters/semesters-page.component.ts:109](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/semesters/semesters-page.component.ts#L109)

#### R-A05

**Где:** [frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.spec.ts:324](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.spec.ts#L324) — метод: isHeadman checkbox visible only when role is student; сейчас: patch role TEACHER/STUDENT и затем читает те же form values; checkbox DOM не прочитан..

**Сейчас:** patch role TEACHER/STUDENT и затем читает те же form values; checkbox DOM не прочитан.

**Проблема:** Проверка видимости isHeadman по роли не касается production @if или formControlName. Сделать checkbox всегда видимым либо удалить его из template: form.patchValue assertions продолжат проходить.

**Переписать так:** Сохранить реальный UserDialogComponent, рендерить create-режим, менять роль через настоящий select и проверять checkbox по role/name: STUDENT видит, TEACHER/ADMIN не видят. Минимум проверять fixture.detectChanges после role transition; не изменять форму/продукт ради удобства.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.html:132](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.html#L132); [frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts:75](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts#L75)

#### R-A06

**Где:** [frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.spec.ts:137](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.spec.ts#L137) — метод: maps 409 on approve to a friendly message (own ticket / already decided); сейчас: Через as any вызывает private errorMessage(err, action), затем regexp по результату..

**Сейчас:** Через as any вызывает private errorMessage(err, action), затем regexp по результату.

**Проблема:** Копия приватной реализации не доказывает, что error callback настоящего approve/reject выводит ошибку и снимает busy. Удалить error handler из approve либо заменить его пустым callback: приватный mapper всё ещё возвращает правильную строку. Переименование private helper при том же UI даёт ложное падение.

**Переписать так:** В существующем mounted fixture вернуть из approveExcuse/rejectExcuse Subject или throwError с 409; запустить реальный approve/reject действием UI. Наблюдать уведомление с семантикой конфликта, сброшенный busy и отсутствие ложного перемещения заявки в решённые. Использовать существующий RxJS/Angular harness, не закреплять имя private helper и точную прозу.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts:514](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts#L514); [frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts:541](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts#L541); [frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts:572](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts#L572)

#### R-A07

**Где:** [frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.spec.ts:108](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.spec.ts#L108) — метод: setStatus is a no-op for cancelled cells; сейчас: setStatus(cancelled cell, present), затем только snackBar.open не вызван. В комментарии прямо допускается запись..

**Сейчас:** setStatus(cancelled cell, present), затем только snackBar.open не вызван. В комментарии прямо допускается запись.

**Проблема:** Название обещает no-op отменённой ячейки, но тест разрешает отправку markAttendance и не смотрит UI-границу. Текущий контракт запрета реализован отсутствием кнопок в cancelled cell. Появятся активные кнопки в cancelled cell и будет отправлен markAttendance; при успешном API snackBar не вызывается, oracle зелёный.

**Переписать так:** Проверить конкретную cancelled строку в смонтированной матрице: нет quick-status buttons; клик по доступным элементам cancelled cell не отправляет markAttendance. В active строке настоящим кликом отправить корректные lessonId/userId/status. Не вводить новый programmatic guard в продукт только ради названия старого теста.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts:218](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts#L218); [frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts:362](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts#L362)

#### R-A08

**Где:** [frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.spec.ts:72](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.spec.ts#L72) — метод: sorts students by surname (Russian locale); сейчас: Входные students уже Иванов, Петров; выход ожидается в том же порядке.; [frontends/web-panel/src/app/features/headman/subjects/subject-dialog.component.spec.ts:167](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/subjects/subject-dialog.component.spec.ts#L167) — метод: loads and sorts teachers on init; сейчас: Входные teachers уже Иванов, Петров, Сидоров; проверяются крайние ids..

**Сейчас:** Входные students уже Иванов, Петров; выход ожидается в том же порядке.; Входные teachers уже Иванов, Петров, Сидоров; проверяются крайние ids.

**Проблема:** Oracle сортировки не различает сортировку и сохранение входного порядка. Не нужно проверять конкретный sort/localeCompare call. Удалить production sort: обе уже отсортированные fixture дадут тот же результат.

**Переписать так:** В существующих кейсах переставить входные записи в обратный/перемешанный порядок; ожидать независимый порядок реальных имён/идентификаторов. Для journal сохранить привязку records к каждому user. Оставить дешёвый unit/component уровень; этот пункт имеет смысл при сохранении уже заявленного требования порядка.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts:274](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/journal/headman-journal-grid/headman-journal-grid.component.ts#L274); [frontends/web-panel/src/app/features/headman/subjects/subject-dialog.component.ts:160](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/subjects/subject-dialog.component.ts#L160)

#### R-A09

**Где:** [frontends/web-panel/src/app/features/headman/shared/headman-api.service.spec.ts:410](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/shared/headman-api.service.spec.ts#L410) — метод: calls GET /api/schedule/groups/{groupId}/lessons with today dateFrom and dateTo params; сейчас: expected today = new Date().toISOString().split('T')[0], то же выражение в service..

**Сейчас:** expected today = new Date().toISOString().split('T')[0], то же выражение в service.

**Проблема:** Oracle скопирован из реализации и закрепляет UTC-день. Принятый контекст требует Europe/Moscow для учебных дат; в том же Angular расписании есть настоящий Moscow formatter. При 2026-10-11T21:05:00Z в Москве уже 12 октября, но implementation и тест выберут 11 октября. Никакой runner timezone это не исправляет.

**Переписать так:** Зафиксировать абсолютный clock на двух сторонах 21:00Z и ожидать литеральные московские dateFrom/dateTo, не вычислять oracle той же формулой. Для 21:05Z ожидать 2026-10-12. Это вскрывает статически видимое несоответствие текущего service; продукт в этом аудите не исправлять.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts:60](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts#L60); [frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.ts:317](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/schedule/headman-schedule.component.ts#L317); [.agent/orchestration-v2/CURRENT.md:3](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/.agent/orchestration-v2/CURRENT.md#L3)

#### R-A10

**Где:** [frontends/web-panel/src/app/features/login/login.component.spec.ts:91](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/login/login.component.spec.ts#L91) — метод: on valid submit, calls AuthApi.login with { login, password }; сейчас: Пользовательский submit заканчивается mock AuthApi.login call.; [frontends/web-panel/src/app/features/login/login.component.spec.ts:121](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/login/login.component.spec.ts#L121) — метод: on successful login, calls AuthService.setTokens and navigates to /teacher/dashboard for TEACHER role; сейчас: Проверяет mock setter и router; currentUser уже возвращает заранее teacher.; [frontends/web-panel/src/app/features/login/login.component.spec.ts:149](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/login/login.component.spec.ts#L149) — метод: on successful login with ADMIN role, navigates to /admin/dashboard; сейчас: ADMIN currentUser задан заранее, mock resolveDashboardFor содержит копию алгоритма.; [frontends/web-panel/src/app/features/login/login.component.spec.ts:176](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/login/login.component.spec.ts#L176) — метод: on successful login with plain STUDENT role, navigates to /student/dashboard; сейчас: STUDENT currentUser задан заранее; тот же copied resolver.; [frontends/web-panel/src/app/features/login/login.component.spec.ts:203](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/login/login.component.spec.ts#L203) — метод: on successful login with headman STUDENT role, navigates to /headman/dashboard; сейчас: Headman currentUser задан заранее; тот же copied resolver..

**Сейчас:** Пользовательский submit заканчивается mock AuthApi.login call.; Проверяет mock setter и router; currentUser уже возвращает заранее teacher.; ADMIN currentUser задан заранее, mock resolveDashboardFor содержит копию алгоритма.; STUDENT currentUser задан заранее; тот же copied resolver.; Headman currentUser задан заранее; тот же copied resolver.

**Проблема:** Существенная граница login response → настоящий session/claims → target разрезана готовым currentUser и копией resolveDashboardFor. В role кейсах кроме teacher даже setter не проверен. Cookies wire здесь тоже заменён готовым mock. Для admin/student/headman удалить setAccessToken: готовый mock.currentUser и copied resolver всё ещё ведут на ожидаемый dashboard. Рассогласование DTO/claims/real resolver не видно.

**Переписать так:** Оставить существующий render/userEvent. Использовать настоящие AuthService и AuthApi плюс HttpTestingController: клик → POST login с правильными credentials и withCredentials → flush token-only response → настоящий currentUser/accessToken и правильный route по role/is_headman/group_id. Не утверждать, что HttpTestingController доказывает сохранение браузером HttpOnly cookie. Сохранить разные роли, не объединять их как дубли.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/login/login.component.ts:44](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/login/login.component.ts#L44); [frontends/web-panel/src/app/core/auth/auth.service.ts:30](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.service.ts#L30); [frontends/web-panel/src/app/core/auth/auth.service.ts:133](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.service.ts#L133); [frontends/web-panel/src/app/core/auth/auth.api.ts:16](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.api.ts#L16)

#### R-A11

**Где:** [frontends/web-panel/src/app/features/student/homework/month-view/student-homework-month-view.component.spec.ts:60](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/homework/month-view/student-homework-month-view.component.spec.ts#L60) — метод: click on a cell emits dateSelected with the cell date; сейчас: Клик buttons[0]; только emitted.length===1, дата события не проверена..

**Сейчас:** Клик buttons[0]; только emitted.length===1, дата события не проверена.

**Проблема:** Для календаря полезен выбранный день; единственное событие произвольной даты даёт ложный успех. В onCellClick всегда эмитить this.month или today: число событий остаётся 1.

**Переписать так:** Выбрать конкретную видимую ячейку известного апреля 2026 и проверить полный emitted date (год/месяц/день), независимо от component.cells. Например первая ячейка этого месяца-календаря — 2026-03-30, либо выбрать однозначный текущий день. Сохранить настоящий click и доступную gridcell semantics; не фиксировать CSS раскладку.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/student/homework/month-view/student-homework-month-view.component.ts:105](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/homework/month-view/student-homework-month-view.component.ts#L105); [frontends/web-panel/src/app/features/student/homework/month-view/student-homework-month-view.component.html:43](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/homework/month-view/student-homework-month-view.component.html#L43)

#### R-A12

**Где:** [frontends/web-panel/src/app/features/student/shared/student-stomp.service.spec.ts:110](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/shared/student-stomp.service.spec.ts#L110) — метод: connect() с другим groupId переустанавливает подписку; сейчас: connect(5); connect(6); expect(eventsSubject.observed).toBe(true)..

**Сейчас:** connect(5); connect(6); expect(eventsSubject.observed).toBe(true).

**Проблема:** observed говорит лишь хотя бы об одной подписке. Он не доказывает освобождение старой подписки и отсутствие удвоенных событий/badge. Удалить this.disconnect() на смене groupId: две подписки остаются observed=true, текущий тест зелёный.

**Переписать так:** Через ту же настоящую service и Subject после connect(5)→connect(6) выпустить один attendance.marked и проверить ровно один payload в marked$/onAnyEvent$ и один badge increment; после disconnect больше ничего. Не требовать здесь фильтрации group_id: production контракт делегирует её NotificationCenter.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts:38](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts#L38); [frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts:65](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts#L65); [frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts:89](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/student/shared/student-stomp.service.ts#L89)

#### R-A13

**Где:** [frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.spec.ts:47](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.spec.ts#L47) — метод: builds barChartData with 3 datasets on ngOnChanges; сейчас: barChartData.datasets.length===3; labels равны именам; значения серий не проверяются..

**Сейчас:** barChartData.datasets.length===3; labels равны именам; значения серий не проверяются.

**Проблема:** Каркас графика может быть правильным при потерянных либо перепутанных значениях посещаемости. Все три data сделать нулями или absent отдать в present: остаются три datasets и правильные labels.

**Переписать так:** Сохранить дешёвый unit реального ngOnChanges; на существующем sampleData проверить по семантике каждой серии независимые массивы present=[5,7], excused=[1,1], absent=[2,0] и их связь с именами. Для mounted части достаточно существующего chart directive boundary, без браузерного screenshot/layout oracle.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.ts:75](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/subject-chart/subject-chart.component.ts#L75)

#### R-A14

**Где:** [frontends/web-panel/src/app/features/teacher/stats/stats-page.component.spec.ts:104](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/stats-page.component.spec.ts#L104) — метод: derives subjects from teacher assignments and loads the first subject when group changes; сейчас: Задаёт assignments напрямую; onGroupChange; проверяет subjects, selectedSubjectId и mock getJournal call..

**Сейчас:** Задаёт assignments напрямую; onGroupChange; проверяет subjects, selectedSubjectId и mock getJournal call.

**Проблема:** Это единственный page flow до getJournal, но он заканчивается вызовом mock. Сохранённые отдельные chartDataMap.set tests не доказывают, что ответ journal попадёт в сводку и график страницы. Удалить next-обработчик, который заполняет chartDataMap/overallStats: вызов getJournal и текущие assertions остаются.

**Переписать так:** Загрузить assignments/groups/subjects обычным ngOnInit, выбрать группу реальным UI действием и доставить journal с различающимися student records. Проверить результат в смонтированных summary/chart inputs/доступных числах: уникальные занятия, percentage и series по ученику. Достаточен существующий Angular/RxJS harness; вычисления ниже уже имеют полезные units, их не переписывать в E2E.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/teacher/stats/stats-page.component.ts:148](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/stats-page.component.ts#L148); [frontends/web-panel/src/app/features/teacher/stats/stats-page.component.html:103](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/stats-page.component.html#L103)

#### R-A15

**Где:** [frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts:159](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/layout/sidebar/sidebar.component.spec.ts#L159) — метод: logout button calls authService.logout; сейчас: После render вызывается component.logout() напрямую; проверяется mock authService.logout..

**Сейчас:** После render вызывается component.logout() напрямую; проверяется mock authService.logout.

**Проблема:** Тест называется logout button, однако не касается click binding. Делегирование сервиса уже отдельно проверяется в core, но реальное пользовательское действие здесь не покрыто. Удалить (click)="logout()" из кнопки: прямой вызов метода и mock-call сохранят успех.

**Переписать так:** В существующем mounted Sidebar нажать настоящую кнопку по role/name «Выйти» и проверить вызов service.logout с нужными AuthApi/Router. При желании объединить с настоящим AuthService и синтетическими storage state, но для этой отдельной wiring-гарантии достаточно реального клика; не заменять core privacy R-A02.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts:318](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts#L318); [frontends/web-panel/src/app/layout/sidebar/sidebar.component.html:135](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/layout/sidebar/sidebar.component.html#L135)

#### R-A16

**Где:** [frontends/web-panel/src/app/core/auth/auth.guard.spec.ts:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.guard.spec.ts#L50) — метод: returns UrlTree to /login when isAuthenticated() is false; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/auth.guard.spec.ts:88](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.guard.spec.ts#L88) — метод: roleGuard([ADMIN]) redirects to /teacher/dashboard when user.role is TEACHER; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/auth.guard.spec.ts:97](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.guard.spec.ts#L97) — метод: roleGuard([TEACHER]) redirects to /admin/dashboard when user.role is ADMIN; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/auth.guard.spec.ts:106](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.guard.spec.ts#L106) — метод: redirects to /login when user is null (not authenticated); сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/auth.guard.spec.ts:115](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.guard.spec.ts#L115) — метод: roleGuard([ADMIN]) redirects plain STUDENT to /student/dashboard via resolveDashboardFor; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/auth.guard.spec.ts:124](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.guard.spec.ts#L124) — метод: roleGuard([ADMIN]) redirects headman to /headman/dashboard via resolveDashboardFor; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/auth.guard.spec.ts:133](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.guard.spec.ts#L133) — метод: roleGuard([TEACHER]) redirects plain STUDENT to /student/dashboard via resolveDashboardFor; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/guest.guard.spec.ts:47](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/guest.guard.spec.ts#L47) — метод: redirects authenticated ADMIN to /admin/dashboard; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/guest.guard.spec.ts:56](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/guest.guard.spec.ts#L56) — метод: redirects authenticated TEACHER to /teacher/dashboard; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/guest.guard.spec.ts:65](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/guest.guard.spec.ts#L65) — метод: redirects authenticated plain STUDENT to /student/dashboard; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/guest.guard.spec.ts:74](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/guest.guard.spec.ts#L74) — метод: redirects authenticated headman to /headman/dashboard; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/headman.guard.spec.ts:48](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/headman.guard.spec.ts#L48) — метод: redirects plain STUDENT (isHeadman=false) to /student/dashboard; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/headman.guard.spec.ts:57](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/headman.guard.spec.ts#L57) — метод: redirects TEACHER to /teacher/dashboard; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/headman.guard.spec.ts:66](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/headman.guard.spec.ts#L66) — метод: redirects ADMIN to /admin/dashboard; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/headman.guard.spec.ts:75](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/headman.guard.spec.ts#L75) — метод: redirects unauthenticated user to /login; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/student.guard.spec.ts:56](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/student.guard.spec.ts#L56) — метод: redirects TEACHER to /teacher/dashboard via resolveDashboardFor; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/student.guard.spec.ts:65](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/student.guard.spec.ts#L65) — метод: redirects ADMIN to /admin/dashboard via resolveDashboardFor; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; [frontends/web-panel/src/app/core/auth/student.guard.spec.ts:74](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/student.guard.spec.ts#L74) — метод: redirects unauthenticated user to /login; сейчас: expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен..

**Сейчас:** expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.; expect(result).not.toBe(true) + проверка router.createUrlTree mock-call; сам возвращённый UrlTree не проверен.

**Проблема:** Во всех redirect случаях проверяется факт построения URL, а не возвращённое guard решение. Разные entrypoints/роли сохраняются как независимые сценарии. В каждой redirect ветке вызвать createUrlTree с правильным URL, затем return false: навигация будет отменена без redirect, все текущие отрицательные assertions пройдут.

**Переписать так:** Оставить дешёвые guard units и реальные AuthService/JWT fixtures. Вернуть из mock.createUrlTree отдельный sentinel UrlTree и требовать result===sentinel; либо настоящий Router и serializeUrl(result). Сохранить true cases и все разные role/unauthenticated inputs.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/core/auth/auth.guard.ts:11](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/auth.guard.ts#L11); [frontends/web-panel/src/app/core/auth/role.guard.ts:13](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/role.guard.ts#L13); [frontends/web-panel/src/app/core/auth/guest.guard.ts:11](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/guest.guard.ts#L11); [frontends/web-panel/src/app/core/auth/headman.guard.ts:11](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/headman.guard.ts#L11); [frontends/web-panel/src/app/core/auth/student.guard.ts:11](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/core/auth/student.guard.ts#L11)

#### R-A17

**Где:** [frontends/web-panel/src/app/features/teacher/stats/stats-utils.spec.ts:16](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/stats-utils.spec.ts#L16) — метод: returns correct counts per student with 2 students, 3 records each; сейчас: У обоих students по present=1/absent=1/excused=1; проверяются только эти одинаковые counts, не связка name→counts..

**Сейчас:** У обоих students по present=1/absent=1/excused=1; проверяются только эти одинаковые counts, не связка name→counts.

**Проблема:** Многопользовательский fixture не различает ошибочную атрибуцию статистики между учащимися. Для второго студента использовать records первого, сохранив имя второго: оба ожидаемых counts совпадают и тест остаётся зелёным.

**Переписать так:** В этом же дешёвом parser/calculation unit дать ученикам различающиеся counts и проверить каждый объект name+present+absent+excused целиком. Сохранить separate free_attendance/cancelled/empty/math units. Не копировать производственный loop в oracle: ожидаемые малые числа задать явно.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/teacher/stats/stats-utils.ts:15](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/teacher/stats/stats-utils.ts#L15)

#### R-A18

**Где:** [frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.spec.ts:96](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.spec.ts#L96) — метод: splits tickets into pending (submitted) and resolved sections (D-24); сейчас: Текст обоих студентов есть; approveCount>=1 по всему DOM. Отсутствие действий у resolved не проверено..

**Сейчас:** Текст обоих студентов есть; approveCount>=1 по всему DOM. Отсутствие действий у resolved не проверено.

**Проблема:** Сценарий обещает pending/resolved разделение и отсутствие действий у решённых заявок, но глобальный count разрешает лишние кнопки. Добавить approve/reject controls также в resolved article: все имена и approveCount>=1 сохраняются.

**Переписать так:** В том же fixture с submitted и approved ограничить assertions конкретной карточкой/секцией: pending имеет обе action кнопки, resolved не имеет ни approve, ни reject. Наблюдать доступные controls, а не только заголовки/copy; сохранять разные ticket/student identities.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts:107](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts#L107); [frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts:190](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts#L190)

#### R-A19

**Где:** [frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.spec.ts:111](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.spec.ts#L111) — метод: approve() calls approveExcuse and reloads list (AC-10 approve); сейчас: approveExcuse mock-call и getGroupExcuses.mock.calls>=2.; [frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.spec.ts:128](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.spec.ts#L128) — метод: reject() with non-empty comment calls rejectExcuse and reloads (AC-10 reject); сейчас: rejectExcuse mock-call и getGroupExcuses.mock.calls>=2.; [frontends/web-panel/src/app/features/headman/homework/headman-homework.component.spec.ts:153](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/homework/headman-homework.component.spec.ts#L153) — метод: onSaved clears editingState and reloads (homework list refetched); сейчас: onSaved clears editingState и list.mock.calls растёт..

**Сейчас:** approveExcuse mock-call и getGroupExcuses.mock.calls>=2.; rejectExcuse mock-call и getGroupExcuses.mock.calls>=2.; onSaved clears editingState и list.mock.calls растёт.

**Проблема:** Вызов refetch не равен обновлению пользовательских данных. Фиксируется способ refresh; данные после сохранения/решения не проверены. После операции вызвать API и проигнорировать его ответ: call counts растут, но старая pending заявка/старое ДЗ остаются на экране. Кэш с локальным корректным обновлением, напротив, может уменьшить calls и получить ложное падение.

**Переписать так:** Оставить command payload assertions как границу отправки, вернуть из последующего чтения изменённую запись и проверить DOM/публичный результат: решённая заявка ушла из pending; сохранённое ДЗ показано, форма закрыта. Запускать реальное действие/событие form saved; не фиксировать точное число/порядок служебных вызовов без отдельного требования к batching.

**Cases/граница:** TEST_METHODS_ONLY

**Проверенный контракт:** [frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts:514](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts#L514); [frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts:541](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts#L541); [frontends/web-panel/src/app/features/headman/homework/headman-homework.component.ts:539](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/web-panel/src/app/features/headman/homework/headman-homework.component.ts#L539)

### Mobile: feature clients и компоненты

#### MCF-R001

**Где:** [frontends/mobile-core/src/features/admin-groups/admin-groups-client.test.ts:15](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/admin-groups/admin-groups-client.test.ts#L15) — тесты: uses the same nine programme mappings across filters, metadata and paginated results; orders the complete registry before slicing pages and combines course with real programme predicates; cases: nine exported adminGroupProgramTypes; size1 pages0/1; programType desc + currentCourse asc, size3 pages0/1; course3 EXTRAMURAL BACHELOR.

**Сейчас:** Real AdminGroupsClient calls an in-memory fixture that uses the same exported programme catalogue and implements server filtering/sorting/slicing.

**Проблема:** A wrong digit->programme table shared by the fixture and test stays green. A broken real server sort/filter stays green because the fixture computes the expected result. Reordering synthetic fixture rows can fail exact [9,9,8]/[8,7,7] expectations.

**Переписать так:** At the frontend boundary replace fixture sorting/filtering with a recording fetcher returning fixed sentinel pages. Assert query status/course/studyMode/educationLevel and repeated sort precedence/page/size, and that server row order/counts survive unchanged. For catalogue mapping use an independent explicit nine-tuple expectation, not the same exported table as input+oracle. Keep server algorithm coverage in existing GroupRegistryIT; no new DB/browser framework needed.

**Cases/граница:** nine exported adminGroupProgramTypes; size1 pages0/1; programType desc + currentCourse asc, size3 pages0/1; course3 EXTRAMURAL BACHELOR uses the same nine programme mappings across filters, metadata and paginated results; orders the complete registry before slicing pages and combines course with real programme predicates

**Проверенный контракт:** frontends/mobile-core/src/features/admin-groups/admin-groups-client.ts:14; frontends/mobile-core/src/features/admin-groups/admin-groups-client.ts:239; frontends/mobile-core/src/dev/admin-groups-fixtures.ts:82; frontends/mobile-core/src/dev/admin-groups-fixtures.ts:198

#### MCF-R002

**Где:** [frontends/mobile-core/src/features/headman-journal/headman-journal-operation.test.ts:32](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/headman-journal/headman-journal-operation.test.ts#L32) — тесты: does not publish a late destructive ACK into the newly selected lesson; cases: one pending ACK; lesson30->31, generation1->2.

**Сейчас:** Creates a local run() that awaits a test promise and calls journalScopeCurrent to mutate a local visibleMark.

**Проблема:** Remove the mutationCurrent guard from production clearEntry after await api.clear: this method still passes because the test reimplements the guard itself.

**Переписать так:** Use the already present real-SFC custom renderer in the same file. Begin clearing a specific marked row with deferred clear; switch selected lesson/owner, complete the old ACK and assert the new lesson mark, pending state and errors remain current and no old report is accepted. Keep cheap journalScopeCurrent true/false cases at lines20/23. No backend DB/browser is required for this controller ownership race.

**Cases/граница:** one pending ACK; lesson30->31, generation1->2 does not publish a late destructive ACK into the newly selected lesson

**Проверенный контракт:** frontends/mobile-core/src/features/headman-journal/HeadmanJournalScreen.vue:118; frontends/mobile-core/src/features/headman-journal/HeadmanJournalScreen.vue:2028; frontends/mobile-core/src/features/headman-journal/headman-journal-operation.ts:9

#### MCF-R003

**Где:** [frontends/mobile-core/src/features/notifications/notifications-preferences-host.test.ts:40](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/notifications/notifications-preferences-host.test.ts#L40) — тесты: keeps old rendering pair plus dirty21 on incoming compatible32 until explicit reload; retains confirmed state and dirty draft after mismatched catalog/prefs and blocks PUT; rejects an initial mismatched pair without rendering a partial settings state; makes no second PUT for a cross-set409 and retains old OFF/color/mute; allows one explicit $name same-set reapply and never unmutes; keeps dirty draft during a503 retry with no automatic PUT; discards a late profile pair when context changes before resolution; %s refuses catalog/draft mismatch before accessing a missing entry; %s presents server order/labels and actual32 OFF/colors; %s shows catalog-change state even if a prior same-set conflict remains; cases: same-set legacy21/current32; child NotificationSettings/NotificationColors; cross-set21->32, initial/reload mismatch,409,503,context switch.

**Сейчас:** Compiles SFC script, replaces onMounted/onBeforeUnmount with no-ops, returns setup internals and mutates draft refs/calls private load/save/reapply methods; child assertions inspect computed groups/disabled/catalogChanged.

**Проблема:** Renaming private draft/preferences refs fails tests despite identical behavior. Removing @save or @update:draft in NotificationsScreen template leaves all these setup-only checks green. Disposing the screen does not exercise actual lifecycle cleanup.

**Переписать так:** Rewrite the host suite at mounted NotificationsScreen + real settings/colors boundary, using existing Vue renderer and recording/deferred NotificationsV2Api. Interact through rendered settings/color/reload/reapply controls; assert rendered enabled/blocked state, preserved selected OFF/colors, exact one/two explicit PUTs with original/new CAS revision and omitted mute, no automatic PUT on503, and no private data after context change/unmount. Child-only cases may mount each real child and observe emits/rendered controls. Keep parser and draft units in notification-preferences-draft/notifications-v2-client. No actual backend/provider is needed. Whole-file rewrite must preserve all ten named declarations and their parameter sets; no whole-file deletion.

**Cases/граница:** same-set legacy21/current32; child NotificationSettings/NotificationColors; cross-set21->32, initial/reload mismatch,409,503,context switch keeps old rendering pair plus dirty21 on incoming compatible32 until explicit reload; retains confirmed state and dirty draft after mismatched catalog/prefs and blocks PUT; rejects an initial mismatched pair without rendering a partial settings state; makes no second PUT for a cross-set409 and retains old OFF/color/mute; allows one explicit $name same-set reapply and never unmutes; keeps dirty draft during a503 retry with no automatic PUT; discards a late profile pair when context changes before resolution; %s refuses catalog/draft mismatch before accessing a missing entry; %s presents server order/labels and actual32 OFF/colors; %s shows catalog-change state even if a prior same-set conflict remains

**Проверенный контракт:** frontends/mobile-core/src/features/notifications/NotificationsScreen.vue:139; frontends/mobile-core/src/features/notifications/NotificationsScreen.vue:192; frontends/mobile-core/src/features/notifications/NotificationsScreen.vue:380; frontends/mobile-core/src/features/notifications/NotificationsScreen.vue:534; frontends/mobile-core/src/features/notifications/NotificationSettings.vue:76; frontends/mobile-core/src/features/notifications/NotificationColors.vue:83

#### MCF-R004

**Где:** [frontends/mobile-core/src/features/profile/MoreScreen.test.ts:7](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/profile/MoreScreen.test.ts#L7) — тесты: renders Statistics and Requests as actionable while Map remains unavailable; cases: default mapEnabled=false, theme dark.

**Сейчас:** SSR contains three labels, exactly one disabled button anywhere and any aria-disabled=true.

**Проблема:** Disable Statistics instead of Map while leaving one disabled count, or remove onNavigate callback invocation: the test remains green.

**Переписать так:** Mount the actual component with onNavigate recorder. Locate each named route control, verify Statistics/Requests enabled and clicks emit the matching route; Map disabled and cannot invoke navigation (including direct guard invocation if using custom renderer). Check optional mapEnabled=true only if required to preserve existing Map gate; no count of unrelated buttons or icon/layout oracle. Existing Vue renderer suffices; no DB/browser necessary.

**Cases/граница:** default mapEnabled=false, theme dark renders Statistics and Requests as actionable while Map remains unavailable

**Проверенный контракт:** frontends/mobile-core/src/features/profile/MoreScreen.vue:42; frontends/mobile-core/src/features/profile/MoreScreen.vue:86; frontends/mobile-core/src/features/profile/MoreScreen.vue:116

#### MCF-R005

**Где:** [frontends/mobile-core/src/features/homework/use-homework.test.ts:316](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/homework/use-homework.test.ts#L316) — тесты: drops a failed retry intent when the authenticated identity changes; cases: first save rejects; second retry succeeds; account switch afterward.

**Сейчас:** Successfully retries and consumes the failed intent before changing scope, then asserts retry is absent.

**Проблема:** Deleting only the scope watcher retryCommands.clear leaves this method green, because successful retry already deletes the command at production line452. The claimed cross-account retained-failure scenario never exists at switch time.

**Переписать так:** Keep the useful same-owner successful retry/one-time consumption check. For the claimed watcher cleanup, start a separate failed command under owner A, switch A->B and await nextTick without attempting retry, then return B->A and await nextTick; retry of the old homework ID must be null and no new API send may occur. This observes actual forgetting across an ownership cycle and catches removal of retryCommands.clear; surviving line346 exercises the distinct same-tick guard by actively retrying under B, which itself deletes the stale command. Existing Vue/QueryClient harness is sufficient, no browser/DB needed.

**Cases/граница:** first save rejects; second retry succeeds; account switch afterward drops a failed retry intent when the authenticated identity changes

**Проверенный контракт:** frontends/mobile-core/src/features/homework/use-homework.ts:487; frontends/mobile-core/src/features/homework/use-homework.ts:560; frontends/mobile-core/src/features/homework/use-homework.ts:450

#### MCF-R006

**Где:** [frontends/mobile-core/src/features/statistics/statistics-chart-geometry.test.ts:26](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/statistics/statistics-chart-geometry.test.ts#L26) — тесты: connects DATA across omitted no-lesson days without deriving new metrics; uses authoritative present and combined rounded percentages without denominator recalculation; uses the server combined boundary when individual thirds sum differently; draws true zero, separates explicit unknown gaps and never replaces null by zero; keeps a known group line when the personal week is future and uncounted; cases: dates01/04; weighted33.3/50; thirds33.33/66.67; group0/null/75; personal FUTURE and group25.

**Сейчас:** Useful metric-source/gap rules are encoded as exact 284x144 SVG coordinates and complete path serialization at lines31-36,66-68,81,192-193,217.

**Проблема:** Scale viewBox proportionally or change equivalent SVG path formatting without changing server-percent position, line gaps or future/group state: these exact strings fail. All data guard behavior must remain tested.

**Переписать так:** Retain cheap pure geometry tests but replace fixed pixel/string fragments with invariant cases: same authoritative percentages plus wildly different counts must produce the same geometry; changing combined percent independently must change only its boundary; omitted days connect, explicit null/future break appropriate runs; group line remains nonempty while personal is empty; finite coordinates and temporal ordering. Test mathematical relationships, not copied 284/144 serialization. Existing same-point table tests remain independently useful accessibility/data coverage.

**Cases/граница:** dates01/04; weighted33.3/50; thirds33.33/66.67; group0/null/75; personal FUTURE and group25 connects DATA across omitted no-lesson days without deriving new metrics; uses authoritative present and combined rounded percentages without denominator recalculation; uses the server combined boundary when individual thirds sum differently; draws true zero, separates explicit unknown gaps and never replaces null by zero; keeps a known group line when the personal week is future and uncounted

**Проверенный контракт:** frontends/mobile-core/src/features/statistics/statistics-chart-geometry.ts:4; frontends/mobile-core/src/features/statistics/statistics-chart-geometry.ts:86; frontends/mobile-core/src/features/statistics/statistics-chart-geometry.ts:115

#### MCF-R007

**Где:** [frontends/mobile-core/src/features/headman-stats/headman-stats-trend-axis.test.ts:214](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/headman-stats/headman-stats-trend-axis.test.ts#L214) — тесты: keeps anchored endpoint labels separate on the actual166px plot at root20; cases: plotWidth166,labelWidth64; test-local endpoint text widths50.5688/53 and unused interior62.3563.

**Сейчас:** Calls axis helper, then independently reconstructs boxes with hardcoded font widths/anchors; never mounts or measures actual chart/root font.

**Проблема:** Change actual SFC text-anchor to middle, break measure()/ResizeObserver/font handling or change root font: this alleged actual-plot proof stays green because all boxes are calculated in the test.

**Переписать так:** Reduce this to a cheap axis-budget/domain unit with explicit input and no claim of actual root20 text layout; discard locally duplicated anchor/box arithmetic. Keep genuine elapsed date spacing and endpoint tests at25/97/161/175/185. If actual measured text overlap is separately required, replace an existing relevant browser chart case with real element measurements at enlarged root; do not add a new browser suite or treat arithmetic as browser evidence.

**Cases/граница:** plotWidth166,labelWidth64; test-local endpoint text widths50.5688/53 and unused interior62.3563 keeps anchored endpoint labels separate on the actual166px plot at root20

**Проверенный контракт:** frontends/mobile-core/src/features/headman-stats/HeadmanStatsTrendChart.vue:37; frontends/mobile-core/src/features/headman-stats/HeadmanStatsTrendChart.vue:65; frontends/mobile-core/src/features/headman-stats/HeadmanStatsTrendChart.vue:226; frontends/mobile-core/src/features/headman-stats/headman-stats-trend-axis.ts:77

#### MCF-R008

**Где:** [frontends/mobile-core/src/features/statistics/use-statistics.test.ts:291](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/statistics/use-statistics.test.ts#L291) — тесты: aborts old owner requests and never displays their late rows after UID/SID change; cases: old ranking pending; user1/session->user3/new-session; gcTime300000.

**Сейчас:** Checks abort/UI ownership correctly, but privacy cache cleanup oracle identifies old queries by private q.queryKey[11] and [2] at313.

**Проблема:** Move ranking/user fields within an otherwise valid query key: predicate can match nothing and cache absence passes vacuously even if old query remains. Harmless key representation refactor may therefore weaken the test rather than prove cleanup.

**Переписать так:** Keep abort and no-late-data assertions. Capture the old pending ranking Query object/hash before switch (assert exactly one matching request was created without indexing private tuple positions); after switch assert that captured object is removed from the cache and old settlement never repopulates its data. QueryClient is already real and isolated; no new framework/DB/browser needed.

**Cases/граница:** old ranking pending; user1/session->user3/new-session; gcTime300000 aborts old owner requests and never displays their late rows after UID/SID change

**Проверенный контракт:** frontends/mobile-core/src/features/statistics/use-statistics.ts:95; frontends/mobile-core/src/features/statistics/use-statistics.ts:336; frontends/mobile-core/src/features/statistics/use-statistics.ts:370

### Mobile shared/PWA/TMA и dev-fixtures

#### MS-R01

**Где:** [frontends/pwa-vue/src/bootstrap-policy.test.ts:170](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/bootstrap-policy.test.ts#L170) — метод: sole recovery call AST guard; [frontends/pwa-vue/src/bootstrap-policy.test.ts:175](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/bootstrap-policy.test.ts#L175) — метод: recognizer positive/negative controls; [frontends/pwa-vue/src/bootstrap-policy.test.ts:311](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/bootstrap-policy.test.ts#L311) — метод: old busy guard negative control; [frontends/pwa-vue/src/bootstrap-policy.test.ts:323](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/bootstrap-policy.test.ts#L323) — метод: resolve/reject online queue; [frontends/pwa-vue/src/bootstrap-policy.test.ts:346](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/bootstrap-policy.test.ts#L346) — метод: coalesced demand; [frontends/pwa-vue/src/bootstrap-policy.test.ts:364](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/bootstrap-policy.test.ts#L364) — метод: seven cancellation reasons; [frontends/pwa-vue/src/bootstrap-policy.test.ts:390](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/bootstrap-policy.test.ts#L390) — метод: authority reconciliation; [frontends/pwa-vue/src/bootstrap-policy.test.ts:409](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/bootstrap-policy.test.ts#L409) — метод: ordinary failure does not reconnect.

**Сейчас:** hasGuardedBootstrapRecovery(9–109) recognizes exact function/variable names, conjunct order, single call and const-await shape. reconnectControl(207–308) extracts names/prelude/finally but replaces actual admission try/catch with test-written offline/readOnly state changes.

**Проблема:** Reordering equivalent && predicates, renaming bootstrap or extracting the recovery branch breaks valid code. Conversely, deleting real cold-start/offline fail-closed handling or publishing authority early inside the replaced try body is invisible to the partial-body harness.

**Переписать так:** Mount actual App with the existing Vue custom renderer and QueryClient; adapt fetch, browser events and snapshot storage boundaries, retaining real auth/coherence/bootstrap handlers. Drive offline/online and explicit retry through the rendered/public event boundary. Assert admitted owner/data/readOnly and which real HTTP route was sent, with deferred current and candidate responses.

**Граница и сохранение:** Existing Vitest/Vue custom renderer and injectable fetch/storage adapters; no new framework, no production extraction just to make tests convenient. Existing reconnect-recovery.test.ts complete-handler + real-auth tests remain KEEP during replacement. Fresh App/QueryClient/synthetic storage per case; unmount, clear timers/listeners and settle deferred responses. Keep frozen B013/14/15 and rejected Mongo/Chromium equivalents unchanged. Keep six cheap classifier methods111–168. Preserve all current queue/cancellation/reconciliation cases and meaningful negative-control sensitivity. recognizer175 is NOT an independent DELETE: retire it only together with obsolete AST oracle after equivalent fail-closed behavior/negative controls exist. old-busy control311 remains until its sensitivity is preserved. No blind refresh POST, no early authority/readOnly release, both late current responses fenced, retained owner/pending command/idempotency key.

**Проверенный контракт:** [frontends/pwa-vue/src/App.vue:1617](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/App.vue#L1617) — метод: bootstrap; [frontends/pwa-vue/src/App.vue:1676](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/App.vue#L1676) — метод: real catch and NETWORK classification; [frontends/pwa-vue/src/App.vue:1727](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/App.vue#L1727) — метод: finally queue; [frontends/pwa-vue/src/App.vue:1751](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/App.vue#L1751) — метод: current-only retry; [frontends/pwa-vue/src/App.vue:2099](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/App.vue#L2099) — метод: goOffline/goOnline; [frontends/pwa-vue/src/bootstrap-policy.ts:9](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/bootstrap-policy.ts#L9) — метод: isOfflineBootstrapRecoveryError

#### MS-R02

**Где:** [frontends/mobile-core/src/shared/components/role-form-lifecycle.test.ts:248](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/role-form-lifecycle.test.ts#L248) — метод: fits final wrapping when restoring page scrollbar; до строки: 260; [frontends/mobile-core/src/shared/components/role-form-lifecycle.test.ts:261](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/role-form-lifecycle.test.ts#L261) — метод: width/font/deletion measurement; до строки: 301.

**Сейчас:** Fake TestElement scrollHeight/clientHeight/contentHeight (30–36) manufacture dimensions; assertions require exact188/212/236/96px heights. CSS layout is never evaluated.

**Проблема:** A stylesheet max-height/overflow or box-sizing regression can clip actual text while the fake still reports style-derived clientHeight >= invented scrollHeight. Conversely a harmless min-height token/font metric change fails exact px assertions.

**Переписать так:** Use the existing Playwright/browser path for an actual request comment textarea: restore long text, cause width change with a page scrollbar, finish fonts/change root font, delete content; assert text retained, no clipping (actual scrollHeight <= clientHeight within border tolerance), keyboard editing and ability to continue the request. Avoid exact CSS pixel snapshots.

**Граница и сохранение:** Existing browser framework and actual form/textarea DOM with synthetic data; no new framework or product CSS change. This is a recommendation only; no browser/Chromium operation is authorized or executed in this audit. Own isolated browser page/context; no real account/files/PII and no raw HTML/DOM artifacts. Frozen proofs remain untouched. Keep role overlay methods199 and217 (focus wrap, Escape, restoration, pre-nextTick unmount) and cheap resize teardown/no-late-callback checks294–300. Current tests are not redundant with those accessibility cases; do not delete before replacement.

**Проверенный контракт:** [frontends/mobile-core/src/features/requests/AutoGrowTextarea.vue:15](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/requests/AutoGrowTextarea.vue#L15) — метод: resize and final wrapping; [frontends/mobile-core/src/features/requests/AutoGrowTextarea.vue:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/requests/AutoGrowTextarea.vue#L50) — метод: watch/mount/ResizeObserver/fonts; [frontends/mobile-core/src/features/requests/AutoGrowTextarea.vue:71](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/requests/AutoGrowTextarea.vue#L71) — метод: dispose; [frontends/mobile-core/src/features/requests/auto-grow-textarea.pcss:3](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/requests/auto-grow-textarea.pcss#L3) — метод: actual textarea sizing/overflow

#### MS-R03

**Где:** [frontends/mobile-core/tests/contract.test.mjs:49](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/tests/contract.test.mjs#L49) — метод: homework timestamps stay required nullable in OpenAPI and generated TypeScript; до строки: 89.

**Сейчас:** Compares exact inline OpenAPI property shape and slices generated TS using exact schema-name and indentation delimiters, then regex-matches completedAt: string | null;.

**Проблема:** A semantically equivalent generated union null | string or harmless indentation/schema composition change fails. A matching string inside a comment in the slice can satisfy the regex without TypeScript proving the property contract.

**Переписать так:** Keep the Node test and existing schema validator. For both StudentHomeworkItem and StudentHomeworkCompletion, validate real date/null examples and missing completedAt rejection through the schema boundary. Check the generated property semantically with the already available TypeScript checker (required, string|null, undefined rejected), not emitted whitespace.

**Граница и сохранение:** Existing Node runner + installed TypeScript compiler/schema validator; no new framework, generator/schema/product changes. Keep canonical generated-source hash test15 and fixture conformance11 as separate useful artifact/wire contracts. Both schema names, requiredness, nullable null, real timestamp; preserve actual DTO/fixture conformance.

**Проверенный контракт:** [frontends/mobile-core/scripts/generate-types.mjs:13](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/scripts/generate-types.mjs#L13) — метод: OpenAPI AST generation; [frontends/mobile-core/scripts/generate-types.mjs:25](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/scripts/generate-types.mjs#L25) — метод: existing canonical drift check; [frontends/mobile-core/scripts/validate-fixtures.mjs:23](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/scripts/validate-fixtures.mjs#L23) — метод: schema validation; [frontends/mobile-core/scripts/validate-fixtures.mjs:71](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/scripts/validate-fixtures.mjs#L71) — метод: required property rejection

#### MS-R04

**Где:** [frontends/mobile-core/src/api/student-client.test.ts:94](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/student-client.test.ts#L94) — метод: keeps binary downloads authenticated and validates visible idempotency keys before fetch; до строки: 115.

**Сейчас:** Captures only fetch count and Accept; returns a successful synthetic response regardless of Authorization. The test title promises authenticated download but never inspects its authentication.

**Проблема:** downloadRequestAttachment could switch to direct fetch with Accept only and omit Bearer credentials: the Blob/Accept assertions still pass. Multipart auth assertion61 covers another method and cannot protect a binary bypass.

**Переписать так:** At the current real StudentApi/injected-fetch boundary capture the attachment request URL, method, Authorization and credentials; assert the current bearer and include credentials plus returned Blob/Accept. Retain pre-fetch invalid idempotency-key rejection.

**Граница и сохранение:** Same Vitest test and real RequestInit/Response boundary; no backend/network or new framework required. Invalid visible key rejects before any fetch; binary request remains authenticated and returns bytes.

**Проверенный контракт:** [frontends/mobile-core/src/api/student-client.ts:290](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/student-client.ts#L290) — метод: downloadRequestAttachment; [frontends/mobile-core/src/api/student-client.ts:307](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/student-client.ts#L307) — метод: requestResponse auth header and include credentials

#### MS-R05

**Где:** [frontends/mobile-core/src/shared/profile-owner-status.test.ts:20](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/profile-owner-status.test.ts#L20) — метод: blocks offline page actions, including history load-more, before invoking network; до строки: 32.

**Сейчас:** Defines tryLoadMore in the test itself and checks its local counter after consulting the pure boolean helper. No actual profile owner action or network port executes.

**Проблема:** Removing the offline check from StudentFeatureOwner.loadMoreProfile leaves this test green because its handwritten tryLoadMore still performs the guard.

**Переписать так:** Keep a cheap direct true/false helper assertion. Replace only the handwritten tryLoadMore/counter with an existing-renderer StudentFeatureOwner profile/history path using real ProfileState/screens and a deferred ProfilePort: go offline, activate load-more/retry, assert no port request and retained/stale rows; reconnect then explicit action can load the next page.

**Граница и сохранение:** Existing Vue renderer/QueryClient/ProfilePort seams; no new framework or product action changes. Fresh owner/port; unmount and settle late responses so one case cannot publish into another. Offline page actions and history pagination must fail closed; online action remains allowed. Existing stale route cases10 remain distinct.

**Проверенный контракт:** [frontends/mobile-core/src/shared/profile-owner-status.ts:20](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/profile-owner-status.ts#L20) — метод: pure canRunProfileNetworkAction; [frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue:1297](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue#L1297) — метод: loadProfileSnapshot; [frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue:1303](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue#L1303) — метод: openProfileArea; [frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue:1324](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue#L1324) — метод: loadMoreProfile

#### MS-R06

**Где:** [frontends/mobile-core/src/shared/components/export-button.test.ts:126](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/export-button.test.ts#L126) — метод: queued/preparing/browser status; до строки: 165; [frontends/mobile-core/src/shared/components/export-button.test.ts:167](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/export-button.test.ts#L167) — метод: filter-change stale completion; до строки: 190; [frontends/mobile-core/src/shared/components/export-button.test.ts:192](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/export-button.test.ts#L192) — метод: grant-loss stale completion; до строки: 216.

**Сейчас:** Real Vue component is mounted, but progress/success is asserted with authored phrases; stale completion is rejected only by absence of a particular Telegram phrase at188/214.

**Проблема:** Copy-only status edits fail the valid happy-path test. A stale completion that writes a differently worded status can evade negative substring assertions.

**Переписать так:** Use the existing renderer to inspect aria-busy, disabled actions, role=status, progress values1/2 and completion status presence without exact prose. After scope/grant invalidation and late accepted completion, assert no terminal status/progress is published for the retired operation; retain false current guard and disabled grant action.

**Граница и сохранение:** Same real ExportButton and current renderer/port seam; no product data attributes, browser or new framework. Button text used by click(root,label) is a selector and remains allowed. Server catalogue labels144 are injected data and should still pass through unchanged. Queue/preparing visible; captured server formats/filters; browser handoff; filter change and grant loss remain separate risks. Sensitivity grant-gap fixture11–15 retained.

**Проверенный контракт:** [frontends/mobile-core/src/shared/components/ExportButton.vue:101](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/ExportButton.vue#L101) — метод: isCurrent and disabled/grant guard; [frontends/mobile-core/src/shared/components/ExportButton.vue:123](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/ExportButton.vue#L123) — метод: result status publication; [frontends/mobile-core/src/shared/components/ExportButton.vue:210](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/ExportButton.vue#L210) — метод: role=status/progress; [frontends/mobile-core/src/shared/components/ExportButton.vue:227](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/ExportButton.vue#L227) — метод: role=alert/status

#### MS-R07

**Где:** [frontends/mobile-core/src/api/map-client.test.ts:38](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/map-client.test.ts#L38) — метод: invalid binary error wording; [frontends/mobile-core/src/shared/async-export.test.ts:371](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/async-export.test.ts#L371) — метод: expired durable result wording; [frontends/mobile-core/src/shared/async-export.test.ts:396](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/async-export.test.ts#L396) — метод: malformed body wording; [frontends/mobile-core/src/shared/async-export.test.ts:478](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/async-export.test.ts#L478) — метод: host timeout wording; [frontends/mobile-core/src/shared/components/attachment-pdf-bytes.test.ts:12](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/attachment-pdf-bytes.test.ts#L12) — метод: oversized error wording; [frontends/mobile-core/src/shared/components/attachment-pdf-bytes.test.ts:28](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/attachment-pdf-bytes.test.ts#L28) — метод: unknown-size error wording; [frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts:1535](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/StudentFeatureOwner.test.ts#L1535) — метод: generic local mutation message; до строки: 1551; [frontends/pwa-vue/src/features/push/push-controller.test.ts:301](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/features/push/push-controller.test.ts#L301) — метод: missing VAPID wording; [frontends/pwa-vue/src/auth-client.test.ts:374](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/auth-client.test.ts#L374) — метод: generic own-profile wording; [frontends/pwa-vue/src/auth-client.test.ts:416](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/auth-client.test.ts#L416) — метод: invalid proof wording; [frontends/pwa-vue/src/auth-client.test.ts:452](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/auth-client.test.ts#L452) — метод: invalid preview wording; [frontends/tma-vue/src/report-download-adapter.test.ts:161](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/src/report-download-adapter.test.ts#L161) — метод: six HTTP status messages; до строки: 178; [frontends/tma-vue/src/report-download-adapter.test.ts:295](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/src/report-download-adapter.test.ts#L295) — метод: range-validation messages; до строки: 310; [frontends/tma-vue/src/report-download-adapter.test.ts:320](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/src/report-download-adapter.test.ts#L320) — метод: recovery notice wording; [frontends/tma-vue/src/report-download-adapter.test.ts:341](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/src/report-download-adapter.test.ts#L341) — метод: recovery notice wording; [frontends/tma-vue/src/tma-auth.test.ts:89](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/src/tma-auth.test.ts#L89) — метод: authentication wording; [frontends/tma-vue/src/tma-auth.test.ts:177](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/src/tma-auth.test.ts#L177) — метод: unconfirmed revoke wording; [frontends/tma-vue/src/tma-auth.test.ts:197](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/src/tma-auth.test.ts#L197) — метод: network revoke wording; [frontends/mobile-core/src/domain/checkin.test.ts:81](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/checkin.test.ts#L81) — метод: cooldown authored caption; [frontends/mobile-core/src/domain/checkin.test.ts:84](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/checkin.test.ts#L84) — метод: retry available caption; [frontends/mobile-core/src/domain/checkin.test.ts:88](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/checkin.test.ts#L88) — метод: numeric server countdown wrapped in display formatter; [frontends/mobile-core/src/shared/components/teacher-context-retry.test.ts:278](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/teacher-context-retry.test.ts#L278) — метод: completed journal empty caption; [frontends/mobile-core/src/shared/components/teacher-context-retry.test.ts:297](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/teacher-context-retry.test.ts#L297) — метод: catalogue error caption; [frontends/mobile-core/src/shared/components/teacher-context-retry.test.ts:310](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/teacher-context-retry.test.ts#L310) — метод: recovery no-error caption; [frontends/mobile-core/src/shared/components/teacher-context-retry.test.ts:338](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/teacher-context-retry.test.ts#L338) — метод: obsolete catalogue error caption; [frontends/mobile-core/src/shared/components/teacher-context-retry.test.ts:363](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/teacher-context-retry.test.ts#L363) — метод: replacement no-error caption.

**Сейчас:** Rejection/status/state tests are coupled to exact authored message literals or prose fragments. Their useful failure, retry, idempotency and privacy outcomes are separate.

**Проблема:** Rewording the same error class/status without changing behavior fails these assertions. Removing message checks wholesale would also lose a few no-secret/generic-error guarantees, so retain those semantically.

**Переписать так:** Preserve rejects/status/error class where present (export expiry410, range RangeError, auth/status, timeout type), pending-command/retained-file/no-extra-send outcomes and nonempty notification/alert presence. Where only generic Error exists keep rejection and absence of synthetic secret/proof/input markers instead of exact prose. Do not add new product error codes for tests. For checkin:68 retain direct ACK COOLDOWN state and assert remainingSeconds(...)=60; drop eligibility prose/countdown formatting only. For teacher catalogue recovery retain current group/subject data and HTTP requests and locate the retry action as a user selector, using existing error/empty state semantics rather than exact caption.

**Граница и сохранение:** The same real parser/client/renderer and current mocks/ports; test-only assertion rewrite, no new framework. No blanket deletion of error methods. Keep injected owner/server text passthrough (e.g. StudentFeatureOwner:1216, authoritative week label, profile displayName, catalogue labels and password recovery426 message input); these are data identity/wire contracts, not authored-copy oracles.

**Проверенный контракт:** [frontends/mobile-core/src/api/map-client.ts:113](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/api/map-client.ts#L113) — метод: invalid file validation; [frontends/mobile-core/src/shared/report-download-client.ts:295](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/report-download-client.ts#L295) — метод: typed ticket/response errors; [frontends/mobile-core/src/shared/report-download-client.ts:1012](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/report-download-client.ts#L1012) — метод: HTTP message mapping; [frontends/tma-vue/src/report-download-adapter.ts:62](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/src/report-download-adapter.ts#L62) — метод: typed host deadline error; [frontends/mobile-core/src/shared/components/attachment-pdf-bytes.ts:2](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/attachment-pdf-bytes.ts#L2) — метод: bounded bytes error; [frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue:1163](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue#L1163) — метод: local request error; [frontends/mobile-core/src/features/requests/requests-controller.ts:897](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/requests/requests-controller.ts#L897) — метод: ambiguous command state; [frontends/pwa-vue/src/features/push/push-controller.ts:27](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/features/push/push-controller.ts#L27) — метод: VAPID validation; [frontends/mobile-core/src/features/profile/browser-login-approval.ts:28](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/profile/browser-login-approval.ts#L28) — метод: strict invalid proof; [frontends/mobile-core/src/features/teacher/teacher-stats-route.ts:54](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/teacher/teacher-stats-route.ts#L54) — метод: recovery notice; [frontends/mobile-core/src/features/teacher/teacher-stats-route.ts:110](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/teacher/teacher-stats-route.ts#L110) — метод: range parser; [frontends/tma-vue/src/tma-auth.ts:24](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/src/tma-auth.ts#L24) — метод: typed admission error; [frontends/mobile-core/src/domain/checkin.ts:97](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/domain/checkin.ts#L97) — метод: numeric remainingSeconds; [frontends/mobile-core/src/features/teacher/TeacherStatsScreen.vue:1001](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/teacher/TeacherStatsScreen.vue#L1001) — метод: catalogue error warning and retry action; [frontends/mobile-core/src/features/teacher/TeacherJournalScreen.vue:559](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/teacher/TeacherJournalScreen.vue#L559) — метод: error/empty journal state; [frontends/mobile-core/src/features/profile/profile-types.ts:143](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/profile/profile-types.ts#L143) — метод: foreign/malformed own profile rejection without payload; [frontends/tma-vue/src/tma-session.ts:442](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/src/tma-session.ts#L442) — метод: clean current-logout errors

### Telegram/Python

#### TB-R01 — 73 legacy notification методов не доказывают активный central business путь; после D01 остаются72

**Где:** [services/notification-bot/tests/test_attendance_marked.py:63](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_attendance_marked.py#L63) — методы: название: test_attendance_marked_headman_present_identifies_mark_and_author; строка: 63; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_attendance_marked_present_deletes_messages_and_clears_redis; строка: 90; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_attendance_marked_absent_deletes_messages; строка: 113; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_attendance_marked_excused_deletes_messages; строка: 131; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_attendance_marked_free_attendance_deletes_messages; строка: 150; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_attendance_marked_present_silently_ignores_telegram_bad_request; строка: 169; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_attendance_marked_present_no_messages_is_noop; строка: 202; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_attendance_marked_missing_user_id_returns_early; строка: 221; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_attendance_marked_student_not_in_group_members_is_noop; строка: 250; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_excuse_decided.py:42](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L42) — методы: название: test_approved_event_sends_ru_message_to_student; строка: 42; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_rejected_event_sends_ru_message_with_comment; строка: 72; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_rejected_event_without_comment_does_not_invent_a_reason; строка: 102; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_missing_user_id_skips_send_gracefully; строка: 130; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_unknown_status_skips_send; строка: 151; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_student_without_telegram_id_skips_send; строка: 171; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_partial_approval_escapes_facts_hides_identifiers_and_reports_display_limit; строка: 226; cases: вид: STATIC_PARAMETER_MATRIX; expandedRuntimeCount: NOT_COLLECTED; declarationLineRanges: 191; 225; parameterDeclarations: [services/notification-bot/tests/test_excuse_decided.py:191](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L191) — source: @pytest.mark.parametrize(; [services/notification-bot/tests/test_excuse_decided.py:192](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L192) — source: ("extra", "expected", "absent"),; [services/notification-bot/tests/test_excuse_decided.py:193](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L193) — source: [; [services/notification-bot/tests/test_excuse_decided.py:194](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L194) — source: (; [services/notification-bot/tests/test_excuse_decided.py:195](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L195) — source: {; [services/notification-bot/tests/test_excuse_decided.py:196](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L196) — source: "excluded_lessons": [; [services/notification-bot/tests/test_excuse_decided.py:197](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L197) — source: {"lesson_id": 12, "date": "2026-10-07", "lesson_number": 3, "subject_name": "<b>Математика</b>"}; [services/notification-bot/tests/test_excuse_decided.py:198](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L198) — source: ]; [services/notification-bot/tests/test_excuse_decided.py:199](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L199) — source: },; [services/notification-bot/tests/test_excuse_decided.py:200](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L200) — source: ["частично", "07.10.2026", "Пара №3", "&lt;b&gt;Математика&lt;/b&gt;"],; [services/notification-bot/tests/test_excuse_decided.py:201](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L201) — source: ["<b>Математика</b>"],; [services/notification-bot/tests/test_excuse_decided.py:202](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L202) — source: ),; [services/notification-bot/tests/test_excuse_decided.py:203](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L203) — source: ({"excluded_lesson_ids": [12]}, ["частично", "Исключено занятий: 1"], ["Пара ID 12"]),; [services/notification-bot/tests/test_excuse_decided.py:204](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L204) — source: ({"excluded_occurrence_ids": ["102"]}, ["частично", "Исключено занятий: 1"], ["102"]),; [services/notification-bot/tests/test_excuse_decided.py:205](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L205) — source: (; [services/notification-bot/tests/test_excuse_decided.py:206](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L206) — source: {; [services/notification-bot/tests/test_excuse_decided.py:207](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L207) — source: "excluded_lessons": [; [services/notification-bot/tests/test_excuse_decided.py:208](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L208) — source: {"lesson_id": 12, "date": "bad", "lesson_number": "bad"},; [services/notification-bot/tests/test_excuse_decided.py:209](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L209) — source: None,; [services/notification-bot/tests/test_excuse_decided.py:210](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L210) — source: True,; [services/notification-bot/tests/test_excuse_decided.py:211](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L211) — source: {"lesson_id": 15, "subject_name": "Hidden fourth"},; [services/notification-bot/tests/test_excuse_decided.py:212](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L212) — source: 16,; [services/notification-bot/tests/test_excuse_decided.py:213](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L213) — source: ]; [services/notification-bot/tests/test_excuse_decided.py:214](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L214) — source: },; [services/notification-bot/tests/test_excuse_decided.py:215](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L215) — source: ["частично", "Исключено занятий: 5", "Ещё исключено: 2"],; [services/notification-bot/tests/test_excuse_decided.py:216](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L216) — source: ["Пара ID 12", "Hidden fourth", "bad"],; [services/notification-bot/tests/test_excuse_decided.py:217](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L217) — source: ),; [services/notification-bot/tests/test_excuse_decided.py:218](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L218) — source: (; [services/notification-bot/tests/test_excuse_decided.py:219](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L219) — source: {"excluded_lessons": "invalid", "excluded_lesson_ids": [12]},; [services/notification-bot/tests/test_excuse_decided.py:220](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L220) — source: ["частично", "Исключено занятий: 1"],; [services/notification-bot/tests/test_excuse_decided.py:221](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L221) — source: ["invalid", "Пара ID 12"],; [services/notification-bot/tests/test_excuse_decided.py:222](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L222) — source: ),; [services/notification-bot/tests/test_excuse_decided.py:223](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L223) — source: ({"excluded_lessons": []}, ["Уважительная причина одобрена"], ["частично", "Исключённые пары"]),; [services/notification-bot/tests/test_excuse_decided.py:224](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L224) — source: ],; [services/notification-bot/tests/test_excuse_decided.py:225](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L225) — source: ); название: test_late_decision_does_not_treat_excluded_metadata_as_partial; строка: 262; cases: вид: STATIC_PARAMETER_MATRIX; expandedRuntimeCount: NOT_COLLECTED; declarationLineRanges: 254; 261; parameterDeclarations: [services/notification-bot/tests/test_excuse_decided.py:254](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L254) — source: @pytest.mark.parametrize(; [services/notification-bot/tests/test_excuse_decided.py:255](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L255) — source: ("status", "reason", "expected"),; [services/notification-bot/tests/test_excuse_decided.py:256](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L256) — source: [; [services/notification-bot/tests/test_excuse_decided.py:257](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L257) — source: ("approved", "present_priority", "уже стоит отметка присутствия"),; [services/notification-bot/tests/test_excuse_decided.py:258](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L258) — source: ("rejected", "", "отклонена"),; [services/notification-bot/tests/test_excuse_decided.py:259](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L259) — source: ("cancelled", "geo_confirmed", "Присутствие подтверждено по геолокации"),; [services/notification-bot/tests/test_excuse_decided.py:260](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L260) — source: ],; [services/notification-bot/tests/test_excuse_decided.py:261](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_excuse_decided.py#L261) — source: ); [services/notification-bot/tests/test_group_archived.py:30](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_group_archived.py#L30) — методы: название: test_group_archived_sends_to_students_with_telegram_id; строка: 30; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_group_archived_message_contains_graduation_text_and_name; строка: 64; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_group_archived_missing_group_id_returns_early; строка: 97; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_group_renamed.py:30](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_group_renamed.py#L30) — методы: название: test_group_renamed_sends_to_students_with_telegram_id; строка: 30; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_group_renamed_message_contains_new_name; строка: 68; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_group_renamed_missing_group_id_returns_early; строка: 101; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_group_renamed_grpc_failure_still_notifies_with_fallback; строка: 125; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_headman_alerts.py:117](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_headman_alerts.py#L117) — методы: название: test_excuse_requested_sends_only_to_headman; строка: 117; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_excuse_requested_text_contains_student_name_and_excuse_type; строка: 150; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_late_checkin_requested_sends_only_to_headman; строка: 189; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_late_checkin_text_contains_canonical_student_name; строка: 221; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_late_checkin_text_contains_subject_and_lesson_number; строка: 260; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_headman_alert_student_name_from_canonical_request_fixture; строка: 303; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_headman_alert_no_headman_with_telegram_skips_send; строка: 341; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_accepted_reason_from_canonical_rpc_reaches_headman_queue; строка: 381; cases: вид: STATIC_PARAMETER_MATRIX; expandedRuntimeCount: NOT_COLLECTED; declarationLineRanges: 374; 380; parameterDeclarations: [services/notification-bot/tests/test_headman_alerts.py:374](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_headman_alerts.py#L374) — source: @pytest.mark.parametrize(; [services/notification-bot/tests/test_headman_alerts.py:375](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_headman_alerts.py#L375) — source: ("reason", "label"),; [services/notification-bot/tests/test_headman_alerts.py:376](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_headman_alerts.py#L376) — source: [; [services/notification-bot/tests/test_headman_alerts.py:377](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_headman_alerts.py#L377) — source: (pb.STUDENT_EXCUSE_REASON_UNIVERSITY_ORDER, "По приказу вуза или иному документу"),; [services/notification-bot/tests/test_headman_alerts.py:378](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_headman_alerts.py#L378) — source: (pb.STUDENT_EXCUSE_REASON_SUMMONS, "Повестка от военкомата"),; [services/notification-bot/tests/test_headman_alerts.py:379](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_headman_alerts.py#L379) — source: ],; [services/notification-bot/tests/test_headman_alerts.py:380](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_headman_alerts.py#L380) — source: ); [services/notification-bot/tests/test_homework_notifications.py:80](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_homework_notifications.py#L80) — методы: название: test_homework_published_sends_to_students_with_telegram_id; строка: 80; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_homework_published_text_contains_subject_and_title; строка: 115; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_homework_updated_sends_to_all_students_with_telegram_id; строка: 158; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_homework_updated_text_contains_subject_and_title; строка: 193; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_homework_updated_subject_fallback_on_grpc_error; строка: 243; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_homework_published_subject_fallback_on_grpc_error; строка: 279; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_homework_due_reminder_sends_direct_to_target_user; строка: 314; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_homework_weekly_digest_formats_grouped_items; строка: 371; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_lesson_blocked.py:46](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_lesson_blocked.py#L46) — методы: название: test_lesson_blocked_sends_to_students_with_telegram_id; строка: 46; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_blocked_message_contains_subject_date_and_reason; строка: 84; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_blocked_skips_missing_payload_fields; строка: 119; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_lesson_cancelled.py:40](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_lesson_cancelled.py#L40) — методы: название: test_lesson_cancelled_sends_to_students_with_telegram_id; строка: 40; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_cancelled_message_contains_subject_name_and_date; строка: 81; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_cancelled_with_cancel_reason; строка: 116; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_cancelled_without_cancel_reason; строка: 150; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_cancelled_fallback_subject_name_on_grpc_error; строка: 222; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_cancelled_skips_missing_payload_fields; строка: 255; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_lesson_closed.py:55](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_lesson_closed.py#L55) — методы: название: test_lesson_closed_deletes_all_message_ids_and_clears_redis; строка: 55; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_closed_skips_students_without_telegram_id; строка: 87; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_closed_silently_ignores_telegram_bad_request; строка: 110; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_closed_skips_students_with_empty_message_ids; строка: 138; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_closed_returns_early_on_missing_payload_fields; строка: 163; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_lesson_reminder.py:29](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_lesson_reminder.py#L29) — методы: название: test_lesson_reminder_sends_to_unmarked_students_even_without_start_message_key; строка: 29; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_reminder_skips_marked_students; строка: 72; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_reminder_returns_early_on_missing_required_fields; строка: 113; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_lesson_started.py:49](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_lesson_started.py#L49) — методы: название: test_lesson_started_sends_to_students_with_telegram_id; строка: 49; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_started_sends_plain_chat_message_without_web_app; строка: 100; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_started_adds_mini_app_checkin_web_app_url; строка: 146; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_started_stores_message_id_in_redis; строка: 195; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_started_resolves_subject_name; строка: 241; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_started_fallback_subject_name_on_grpc_error; строка: 283; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_lesson_started_skips_missing_payload_fields; строка: 324; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_one_off_cancelled_handler.py:41](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_one_off_cancelled_handler.py#L41) — методы: название: test_sends_cancellation_to_all; строка: 41; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_message_contains_date_and_subject; строка: 78; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_fallback_subject_on_grpc_error; строка: 113; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_skips_missing_payload_fields; строка: 146; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_one_off_created_handler.py:44](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_one_off_created_handler.py#L44) — методы: название: test_sends_to_all_group_students; строка: 44; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_empty_group_no_error; строка: 90; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_skips_students_without_telegram_id; строка: 112; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_skips_missing_payload_fields; строка: 143; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** Прямые вызовы старых handle\_\* с get_current_group_members / get_group_members; queue.put часто только собирает SendTask, затем тест вручную вызывает coroutine_factory. Проверяются отдельные адресаты, тексты/ссылки, очистка reminder keys и request display. Полный method inventory, включая каждую param matrix, приложен к targets и coverage.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Удалить/сломать ветку reminder cleanup в audience.handle_business:460 либо приватную фильтрацию :388–393, либо chat routing :425; перечисленные прямые legacy tests продолжат исполнять другие functions. В count-only fanout случаях можно адресовать все N фабрик последнему chat_id: количество задач/вызовов сохранится. test_headman_alerts.py:11–46 копирует event student_name/lesson data в canonical RPC fixture: fallback production к event metadata даёт тот же ожидаемый текст.

**Переписать так:** Переписать перечисленные сценарии через настоящий EventDispatcher.prepare→dispatch, настоящую TelegramSendQueue.staged/batch.wait, действительные protobuf ответы authority и synthetic sender. Подменять только Academic/Attendance authority и Telegram transport; не \_handlers и не handle_business. У каждого recipient уникальные user/chat/message IDs; сравнивать полный набор реально доставленных (chat_id,event fact), отсутствие чужого chat и правильный receipt/cleanup. Redis брать существующий fake_redis для lifecycle; реальная БД/брокер здесь не нужны. Для forged event vs canonical RPC давать РАЗНЫЕ marker значения. При malformed/inactive inputs сохранять fail-closed/no provider результат текущего пути, а не старую форму silent-return. Для absent/excused/free_attendance/present сохранять отдельные статусы. Не переносить старую обязанность раскрывать decision_comment: активный private business путь его исключает; сохранить outcome/partial approval/HTML/privacy. Старые subject lookup/fallback fixtures не объявлять текущим routing контрактом; предмет/дата — факты accepted event snapshot.

**Граница и сохранение:** Полного surviving counterpart для ВСЕХ полезных reminder/status/recipient/partial/idempotent-cleanup рисков нет. Отличные состояния не объявляются дублями.

**Cases/граница:** EXPLICIT_72_METHODS_IN_13_FILES_NOT_BLANKET_DELETE

**Проверенный контракт:** [services/notification-bot/bot/consumers/event_dispatcher.py:458](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/consumers/event_dispatcher.py#L458) — why: Все BUSINESS_EVENTS направляются в handle_business ДО lookup \_handlers; [services/notification-bot/bot/services/notification_prefs.py:14](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/services/notification_prefs.py#L14) — why: Closed business event set; [services/notification-bot/bot/notifications/audience.py:298](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/audience.py#L298) — why: Активный business flow; [services/notification-bot/bot/notifications/audience.py:388](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/audience.py#L388) — why: Private fields whitelist; [services/notification-bot/bot/notifications/audience.py:425](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/audience.py#L425) — why: Фактическая доставка/receipt/tracker; [services/notification-bot/bot/notifications/audience.py:564](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/audience.py#L564) — why: Актуальная очистка reminder artifacts; [services/notification-bot/bot/notifications/presentation.py:108](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/presentation.py#L108) — why: Текущий renderer; [proto/academic.proto:103](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/proto/academic.proto#L103) — why: Authority audience request/recipient contract

#### TB-R02 — Routing suite подменяет и active handler, и legacy registry одним mock

**Где:** [services/notification-bot/tests/test_event_dispatcher.py:39](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_event_dispatcher.py#L39) — методы: название: test_dispatch_routes_lesson_started; строка: 39; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_dispatch_routes_lesson_reminder; строка: 52; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_dispatch_routes_lesson_cancelled; строка: 65; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_dispatch_routes_lesson_blocked; строка: 78; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_dispatch_routes_lesson_closed; строка: 128; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_dispatch_routes_attendance_marked; строка: 141; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_dispatcher_has_all_event_types; строка: 157; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_cancelled_late_checkin_closes_actions_without_false_rejection; строка: 188; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_dispatch_routes_otp_verified; строка: 246; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_dispatch_routes_group_renamed; строка: 255; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_dispatch_routes_group_archived; строка: 266; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_pending_delivery.py:370](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_pending_delivery.py#L370) — методы: название: test_tracked_reply_rechecks_authorization_after_edit; строка: 370; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_warmed_roster_replay_materializes_current_binding_before_ack; строка: 417; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_notification_presentation.py:54](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_notification_presentation.py#L54) — методы: название: test_requested_summary_uses_canonical_context_once_without_private_documents; строка: 54; cases: вид: STATIC_PARAMETER_MATRIX; expandedRuntimeCount: NOT_COLLECTED; declarationLineRanges: 47; 53; parameterDeclarations: [services/notification-bot/tests/test_notification_presentation.py:47](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_notification_presentation.py#L47) — source: @pytest.mark.parametrize(; [services/notification-bot/tests/test_notification_presentation.py:48](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_notification_presentation.py#L48) — source: "event_type, request_kind, request_field, callback_prefix",; [services/notification-bot/tests/test_notification_presentation.py:49](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_notification_presentation.py#L49) — source: [; [services/notification-bot/tests/test_notification_presentation.py:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_notification_presentation.py#L50) — source: ("excuse.requested", pb.STUDENT_REQUEST_KIND_EXCUSE, "ticket_id", "ex"),; [services/notification-bot/tests/test_notification_presentation.py:51](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_notification_presentation.py#L51) — source: ("late_checkin.requested", pb.STUDENT_REQUEST_KIND_LATE_CHECKIN, "request_id", "lcr"),; [services/notification-bot/tests/test_notification_presentation.py:52](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_notification_presentation.py#L52) — source: ],; [services/notification-bot/tests/test_notification_presentation.py:53](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_notification_presentation.py#L53) — source: ); название: test_multiple_legacy_cards_emit_one_decision_with_fresh_authority; строка: 158; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_durable_preferences_delivery.py:114](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_durable_preferences_delivery.py#L114) — методы: название: test_headman_tracker_records_original_user_for_later_exact_binding; строка: 114; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** Для business routes monkeypatch audience.handle_business=mock и \_handlers[event]=тот же mock. Registry equality смотрит лишь22 legacy keys. Несколько связанных regressions вызывают private \_handle/\_close или вручную legacy handler→task.on_sent.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Заменить public dispatch business body на no-op/misroute после boundary либо убрать поддержку нового business event, отсутствующего в legacy registry: registry equality и direct legacy tests могут остаться зелёными. route test с override не обнаружит ошибочный реальный \_handlers binding. Безобидное удаление unreachable legacy registry entries сломает equality при сохранении public business поведения.

**Переписать так:** Объединить относящиеся к одному риску route и legacy regressions с TB-R01 на public prepare/dispatch и наблюдаемые delivery effects. Для OTP оставить настоящий dispatch→реальный otp_verified handler→tracker deletion. Для registry использовать независимый список принятых event meanings и реальные результаты admission/no disclosure, а не exact private dict. Для cancelled late_checkin сохранить cancelled/geo_confirmed вместо false rejection; для rebound оставлять исторический chat холодным и доставлять только freshly bound chat; для multiple tracked cards удалять ровно собственные callback-markup и выдавать один owner terminal факт. Прежний recheck-after-edit сценарий перенести на следующие актуальные provider actions/следующую карточку: после revocation нельзя продолжать действия; legacy headman reply не навязывать текущему пути.

**Граница и сохранение:** Базовый routing сам по себе не повод нового suite, но существующие regressions содержат отдельные binding/privacy/terminal-history риски; их нельзя просто убрать.

**Cases/граница:** EXPLICIT_METHODS

**Проверенный контракт:** [services/notification-bot/bot/consumers/event_dispatcher.py:74](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/consumers/event_dispatcher.py#L74) — why: Legacy registry; [services/notification-bot/bot/consumers/event_dispatcher.py:429](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/consumers/event_dispatcher.py#L429) — why: Настоящий prepare и dispatch; [services/notification-bot/bot/notifications/audience.py:449](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/audience.py#L449) — why: Terminal cleanup active path; [services/notification-bot/bot/notifications/audience.py:464](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/audience.py#L464) — why: Текущая очередь request cleanup

#### TB-R03 — Token-bucket и retry-after tests не наблюдают время ограничения

**Где:** [services/notification-bot/tests/test_send_queue.py:96](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_send_queue.py#L96) — методы: название: test_rate_limit_token_bucket; строка: 96; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_429_retry_after; строка: 134; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** sleep заменён на немедленный AsyncMock; записаны \_tokens=\_MAX_TOKENS; после35 задач проверяются total_sent35 и константа30. FakeRetryAfter проверяет лишь число попыток2.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Полностью обойти \_consume_token или ждать0 вместо retry_after=1: оба теста сохранят текущие asserted counts. Изменить внутренние поля limiter без изменения реальной скорости: тест станет красным.

**Переписать так:** Сохранить дешёвые units. Управлять monotonic и локальным sleeper детерминированно; sender записывает время попыток. Доказать burst<=30 в начальный момент и отсутствие31-й попытки до refill, затем корректное разрешение остатка. Для429 задавать отличающийся retry_after (например7 против backoff1), проверять что до7 второй provider attempt невозможен, после7 возможен; не подменять весь asyncio.sleep. Публичный batch.wait и SENT/FAILED outcome вместо обязательного exact private поля. Реальный Telegram не нужен.

**Граница и сохранение:** Реальный limiting/retry-after риск иначе остаётся без oracle.

**Проверенный контракт:** [services/notification-bot/bot/services/send_queue.py:213](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/services/send_queue.py#L213) — why: Refill на monotonic и ожидание; [services/notification-bot/bot/services/send_queue.py:225](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/services/send_queue.py#L225) — why: Retry/backoff/fresh eligibility

#### TB-R04 — Prefs callback заявляет server snapshot, но проверяет лишь наличие edit

**Где:** [services/notification-bot/tests/test_callback_prefs.py:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_callback_prefs.py#L50) — методы: название: test_callback_passes_stable_identity_and_uses_server_snapshot; строка: 50; cases: вид: STATIC_PARAMETER_MATRIX; expandedRuntimeCount: NOT_COLLECTED; declarationLineRanges: 41; 49; parameterDeclarations: [services/notification-bot/tests/test_callback_prefs.py:41](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_callback_prefs.py#L41) — source: @pytest.mark.parametrize(; [services/notification-bot/tests/test_callback_prefs.py:42](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_callback_prefs.py#L42) — source: "handler,data,operation,fields",; [services/notification-bot/tests/test_callback_prefs.py:43](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_callback_prefs.py#L43) — source: [; [services/notification-bot/tests/test_callback_prefs.py:44](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_callback_prefs.py#L44) — source: (cb_toggle_global, "prefs:global:toggle", "TOGGLE_GLOBAL", {}),; [services/notification-bot/tests/test_callback_prefs.py:45](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_callback_prefs.py#L45) — source: (cb_toggle_category, "prefs:cat:homework", "TOGGLE_CATEGORY", {"category": "homework"}),; [services/notification-bot/tests/test_callback_prefs.py:46](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_callback_prefs.py#L46) — source: (cb_mute_day, "prefs:mute:day", "MUTE_FOR", {"durationSeconds": 86400}),; [services/notification-bot/tests/test_callback_prefs.py:47](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_callback_prefs.py#L47) — source: (cb_mute_clear, "prefs:mute:clear", "CLEAR_MUTE", {}),; [services/notification-bot/tests/test_callback_prefs.py:48](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_callback_prefs.py#L48) — source: ],; [services/notification-bot/tests/test_callback_prefs.py:49](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_callback_prefs.py#L49) — source: ).

**Сейчас:** Сохраняет stable callback identity/operation body, get_snapshot не вызывается; edit_text/answer вызваны по1. Returned snapshot content не проверяется.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: После успешной command игнорировать returned global_enabled/categories/muted_until и всегда показывать enabled default: test по-прежнему зелёный. При этом ложный успех изменяет пользовательское durable preference состояние.

**Переписать так:** Сохранить4 operation cases и exact stable request key/body. Вернуть различимые snapshots: globalOff, homeworkOff и mute active; synthetic callback sender перехватывает markup и verdict. Проверять category callback_data/доступность CLEAR_MUTE и семантическое OFF/pause, совпадающее с серверным ответом. Точную пунктуацию/emoji не фиксировать. Существующие outage/unknown-category no mutation остаются.

**Cases/граница:** ONE_PARAMETERIZED_METHOD_4_DECLARED_ROWS

**Проверенный контракт:** [services/notification-bot/bot/handlers/prefs.py:115](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/handlers/prefs.py#L115) — why: Menu из returned snapshot; [services/notification-bot/bot/handlers/prefs.py:178](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/handlers/prefs.py#L178) — why: Атомарная command и её результат

#### TB-R05 — Login errors проверяют устаревший tracker.store и нетипичную message fixture

**Где:** [services/notification-bot/tests/test_login_handler.py:56](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_login_handler.py#L56) — методы: название: test_login_saves_pending_msg_and_requests_otp; строка: 56; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_login_rate_limited; строка: 80; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_login_account_not_found; строка: 97; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_login_service_unavailable; строка: 114; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** MagicMock message sender12345/chat99 без type; success проверяет store_pending_user_msg и request_otp. Error cases429/401/503 проверяют словесный ответ и tracker.store.assert_not_called, хотя production вызывает store_pending_user_msg.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Добавить лишнее ошибочное изменение pending namespace на error path или изменить реальный pending write: assertion неиспользуемого store не заметит. В success разрешена фикстура, невозможная для обычного собственного private login (sender/chat различны), и проверяется лишь mock wiring.

**Переписать так:** Использовать реальный aiogram private Message с совпадающими sender/chat и OtpMessageTracker на fake_redis; Auth заменить минимальным HTTP adapter/fake для204/429/401/503. В success проверить сохранение только своей pending записи и последующий otp.requested→bot message→cleanup связь. В отказах проверить отсутствие code delivery и сохранность чужих/иной-purpose записей; не вводить без owner решения новое правило удаления своей pending записи — сейчас она пишется до Auth. Ошибочные no-call проверки tracker.store убрать; категории исхода сохранить, wording сделать вторичным.

**Cases/граница:** FOUR_METHODS

**Проверенный контракт:** [services/notification-bot/bot/handlers/login.py:31](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/handlers/login.py#L31) — why: Store pending перед Auth request; [services/notification-bot/bot/services/otp_message_tracker.py:64](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/services/otp_message_tracker.py#L64) — why: Фактический storage key и payload; [services/notification-bot/bot/notifications/otp_requested.py:122](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/otp_requested.py#L122) — why: Finalize request-message связь

#### TB-R06 — Academic cache expiration/invalidation считает RPC, но не свежесть возвращённых members

**Где:** [services/notification-bot/tests/test_academic_client.py:48](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_academic_client.py#L48) — методы: название: test_cache_hit; строка: 48; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_cache_expired; строка: 65; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_cache_invalidate; строка: 88; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** Один и тот же response.students до/после; только GetGroupMembers call_count1/2. Private \_cache/\_stub и **new** заданы вручную.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: После expiry/invalidate выполнить второй RPC, но вернуть старый список; call_count2 сохранится, stale binding/roster будет пропущен. Безобидная замена внутреннего cache representation потребует фикстуру переписать.

**Переписать так:** Оставить дешёвые clock/cache units. RPC adapter возвращает A до clock boundary и B после, отдельно другая group C. Проверять наблюдаемый A→A доTTL; A→B послеTTL/явного invalidate, независимость другой группы. Count оставить только как экономию RPC при cache hit, не как главный oracle. Текущий get_current_group_members не называть доказанным через cached getter.

**Cases/граница:** THREE_METHODS

**Проверенный контракт:** [services/notification-bot/bot/grpc_client/academic_client.py:59](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/grpc_client/academic_client.py#L59) — why: Обычный cache; [services/notification-bot/bot/grpc_client/academic_client.py:74](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/grpc_client/academic_client.py#L74) — why: Authorization roster намеренно bypass cache

#### TB-R07 — TTL наличие нельзя выдавать за expiry и точную retention

**Где:** [services/notification-bot/tests/test_idempotency_guard.py:81](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_idempotency_guard.py#L81) — методы: название: test_ttl_expires; строка: 81; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_redis_client.py:53](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_redis_client.py#L53) — методы: название: test_ttl_set; строка: 53; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; [services/notification-bot/tests/test_jwt_redis_client.py:78](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_jwt_redis_client.py#L78) — методы: название: test_ttl_uses_max_of_expires_in_and_default; строка: 78; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** Guard test читает ttl сразу послеclaim, не дожидаясь expiry; Reminder ttl только>0; JWT short expires проверяет лишь ttl>604790 без верхней границы.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Guard игнорирует повторное приобретение после истечения, Reminder ставитTTL1 вместо86400, JWT ставитTTL100лет: существующие assertions не исключают эти результаты. Название ttl_expires обещает сценарий, которого в теле нет.

**Переписать так:** Сохранить cheap state/time units. Guard unit честно назвать test_processing_lease_has_configured_ttl и проверять bounded TTL; natural300s crash expiry уже сохраняется в broker bridge. Для Reminder требовать значение в разумном допуске вокруг настроенного TTL и независимость keys. Для JWT добавить верхнюю границу и существующим параметризованным case проверить expires_in>default и<=0; не копировать max из реализации в expected. Если нужна отдельная локальная expiry проверка, использовать управляемые часы существующего fake Redis, без real sleep/нового framework.

**Граница и сохранение:** TTL/lease units полезны; не предлагается снимать их либо заменять дешёвое покрытие тяжёлым.

**Cases/граница:** THREE_METHODS

**Проверенный контракт:** [services/notification-bot/bot/services/idempotency_guard.py:63](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/services/idempotency_guard.py#L63) — why: Short owned claim; [services/notification-bot/bot/services/redis_client.py:36](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/services/redis_client.py#L36) — why: Configured reminder expiry; [services/notification-bot/bot/services/jwt_redis_client.py:39](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/services/jwt_redis_client.py#L39) — why: max(expires_in,defaultTTL)

#### TB-R08 — Поддельный broker моделирует требуемую атомарность вместо наблюдения Rabbit

**Где:** [services/notification-bot/tests/test_prepared_delivery.py:157](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_prepared_delivery.py#L157) — методы: название: test_atomic_prepare_publish_ack_commit_preserves_facts_and_minimal_roles; строка: 157; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_commit_unavailable_retains_original_without_partial_replacement; строка: 173; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_unknown_prepare_authority_commits_requeue_without_provider_or_publish; строка: 188; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_prepared_projection_requires_private_queue_provenance; строка: 202; cases: вид: STATIC_PARAMETER_MATRIX; expandedRuntimeCount: NOT_COLLECTED; declarationLineRanges: 201; 201; parameterDeclarations: [services/notification-bot/tests/test_prepared_delivery.py:201](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_prepared_delivery.py#L201) — source: @pytest.mark.parametrize("private_transport", [False, True]); название: test_abort_after_publish_staging_rolls_back_raw_and_prepared_without_commit; строка: 219; cases: вид: STATIC_PARAMETER_MATRIX; expandedRuntimeCount: NOT_COLLECTED; declarationLineRanges: 218; 218; parameterDeclarations: [services/notification-bot/tests/test_prepared_delivery.py:218](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_prepared_delivery.py#L218) — source: @pytest.mark.parametrize("abort", [TimeoutError(), asyncio.CancelledError(), RuntimeError("synthetic drain abort")]); название: test_abort_during_automatic_ack_after_staging_never_commits_projection; строка: 231; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** broker():38–154 сам ведёт pending operations; commit переносит их в published/acked, rollback очищает, close ставит requeued. Tests сверяют exact operations prefixes и этим же fake выведенное состояние.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Реальный adapter случайно публикует projection в отдельном channel/transaction, но fake exchange всё равно попадает в state.pending: fake 'atomic' assertion остаётся зелёным. Безобидный дополнительный select/служебная операция ломает exact order prefix, хотя не меняет durable исход.

**Переписать так:** Оставить компактные fast protocol units provenance/error classification (включая False/True private_transport и3abort типа), убрав требования полного внутреннего call order. Одну имеющуюся synthetic broker acceptance границу расширять/переиспользовать только для ещё непокрытого raw→prepared TX: после разрыва на publish/ACK/commit независимо прочитать broker и доказать ровно один durable исход (original либо minimal projection), отсутствие completed/ACK при неопределённом исходе и успешный controlled replay. Это тот случай, где требуется настоящий broker; реальная БД и Telegram не нужны. Не заявлять, что password-change broker bridge уже доказывает prepared projection: в его worker нет dispatcher.prepare использования в этом сценарии.

**Граница и сохранение:** Mock model полезен для локального protocol control flow, но не является доказательством Rabbit atomicity. Защищённые/FROZEN gates не ослаблять; если новый scope затрагивает их — QUESTION, не изменение.

**Cases/граница:** SIX_METHODS_PLUS_BROKER_MODEL_HELPER

**Проверенный контракт:** [services/notification-bot/bot/consumers/event_consumer.py:161](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/consumers/event_consumer.py#L161) — why: Persistent same-queue prepared projection; [services/notification-bot/bot/consumers/event_consumer.py:249](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/consumers/event_consumer.py#L249) — why: Broker select/commit/rollback/close boundary

#### TB-R09 — Fresh authority fixtures привязаны к порядковому номеру RPC

**Где:** [services/notification-bot/tests/test_notification_audience.py:191](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_notification_audience.py#L191) — методы: название: test_revoked_helper_and_late_new_role_or_user_cannot_join; строка: 191; cases: вид: STATIC_PARAMETER_MATRIX; expandedRuntimeCount: NOT_COLLECTED; declarationLineRanges: 190; 190; parameterDeclarations: [services/notification-bot/tests/test_notification_audience.py:190](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_notification_audience.py#L190) — source: @pytest.mark.parametrize("live", [{}, {1: ["TEACHER"], 2: ["HEADMAN"]}]); название: test_privileged_role_loss_uses_remaining_saved_live_role_once; строка: 205; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** academic iter(audiences) выдаёт3 одинаковых grant snapshot и затем live revocation; удерживается \_consume_token.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Добавление полезного дополнительного authority read до provider либо реорганизация admissions сдвинет sequence, хотя контракт не изменён. Удаление нецелевой проверки тоже может просто сдвинуть revocation к другому этапу: тест доказывает число вызовов вместо заданной временной границы.

**Переписать так:** Дать authority mutable current_roles, prepare сохранить исходный snapshot. Поднять Event именно при входе в rate wait, после него изменить roles и отпустить provider. Проверять: новые user/role не присоединяются; отозванный recipient не получает факт; dual-role с оставшимся saved/live role получает один факт. Дополнительный read не должен менять fixture. Сохранить closed roles/unknown response/type/privacy/N2 matrices.

**Cases/граница:** TWO_METHODS

**Проверенный контракт:** [services/notification-bot/bot/notifications/audience.py:326](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/audience.py#L326) — why: Initial intersection; [services/notification-bot/bot/notifications/audience.py:361](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/audience.py#L361) — why: Fresh authority перед attempt; [services/notification-bot/bot/services/send_queue.py:235](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/services/send_queue.py#L235) — why: Все waits перед auth check

#### TB-R10 — OTP cleanup helper отменяет все задачи чужого event loop

**Где:** [services/notification-bot/tests/test_otp_requested.py:33](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_otp_requested.py#L33) — helper: \_drain_tasks; методы: название: test_handle_otp_requested_sends_code_and_finalizes_tracker; строка: 42; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_handle_password_reset_otp_includes_fragment_link_and_server_metadata; строка: 66; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_handle_otp_requested_custom_ttl_rounds_to_minutes; строка: 118; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_subject_delete_is_challenge_scoped_without_login_or_reset_link; строка: 158; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** asyncio.all_tasks minus current отменяется целиком, затем gather тех же глобальных tasks.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Добавить независимый heartbeat/fixture task SDK, либо тестировать обработку двух собственных OTP одновременно: helper молча отменит задачу, которой не владеет. Тест может стать зелёным потому, что cleanup/retry задача оборвана, а не завершила нужный lifecycle.

**Переписать так:** Во fixture захватывать только задачи, создаваемые данным production cleanup scheduling seam, и отменять/await именно их в finally; отдельный owned-task sentinel должен пережить cleanup. Для срока жизни управлять локальным sleeper на границе expiry и проверять реальные tracker entries/provider deletes по purpose, без asyncio.all_tasks и без глобального monkeypatch всего sleep.

**Граница и сохранение:** OTP login/reset/subject-delete purpose/privacy/code delivery tests сохраняются; только владение фоновыми задачами исправляется.

**Cases/граница:** ONE_HELPER_FOUR_CALLERS

**Проверенный контракт:** [services/notification-bot/bot/notifications/otp_requested.py:123](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/otp_requested.py#L123) — why: Production создаёт одну owned expiry cleanup task; [services/notification-bot/bot/notifications/otp_requested.py:133](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/otp_requested.py#L133) — why: Cleanup sleep/lifecycle

#### TB-R11 — Actual tracker test содержит собственную реализацию pop/delete

**Где:** [services/notification-bot/tests/test_otp_verified.py:121](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_otp_verified.py#L121) — методы: название: test_actual_tracker_keeps_new_challenge_login_and_reset_after_old_cleanup; строка: 121; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** Fake pipe.get только сохраняет selected key; fake pipe.execute сам делает stored.pop(selected[-1]) и возвращает фиктивный delete=1. pipe.delete вызов не влияет на результат.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Удалить pipe.delete(key) из production OtpMessageTracker.pop: stub.execute всё равно удалит key, повтор cleanup будет считаться no-op и test пройдёт. Это конкретное копирование ожидаемого алгоритма в fake.

**Переписать так:** Использовать уже доступный fake_redis и реальный OtpMessageTracker: сохранить old/new subject challenge, login/reset, выполнить два cleanup old, проверить ровно один deleteMessage и наличие остальных трёх записей. После первого pop old key реально отсутствует. Это проверяет key selection/idempotency без переписывания Redis algorithm; реальный Redis нужен только при отдельном доказательстве concurrent transaction atomicity.

**Cases/граница:** ONE_METHOD

**Проверенный контракт:** [services/notification-bot/bot/services/otp_message_tracker.py:147](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/services/otp_message_tracker.py#L147) — why: Настоящая pipeline GET+DELETE; [services/notification-bot/bot/services/otp_message_tracker.py:53](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/services/otp_message_tracker.py#L53) — why: Четыре независимых purpose namespaces

#### TB-R12 — Failure log test требует сырые IDs и exception wording без production sanitizer

**Где:** [services/notification-bot/tests/test_send_queue.py:203](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_send_queue.py#L203) — методы: название: test_failed_send_logs_exception_summary; строка: 203; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** caplog в неподготовленной logging конфигурации требует RuntimeError:permanent failure, user_id10,chat_id555,categoryreminders.

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Безопасное улучшение sanitizer/redaction вызовет false red. Если случайная provider exception содержит OTP/token/URL, тест по этому шаблону поощряет их копирование; существующий benign marker не проверяет это.

**Переписать так:** Проверять JSON, прошедший настоящий setup_observability formatter, с непредсказуемым synthetic marker в exception/token/url. Требовать безопасную category/error type/exhausted metric и отсутствие raw marker/user/chat. Не фиксировать полную фразу лога. Сохранить отдельные send success/exhaustion counters и SDK/privacy tests; не отключать логирование.

**Cases/граница:** ONE_METHOD

**Проверенный контракт:** [services/notification-bot/bot/services/send_queue.py:260](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/services/send_queue.py#L260) — why: Failure diagnostics; [services/notification-bot/bot/observability.py:183](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/observability.py#L183) — why: Production setup/sanitizer boundary; фактический exporter/log coverage уже существует

#### TB-R13 — Alert format tests фиксируют emoji, а truncation не достигает Telegram wire bound

**Где:** [services/notification-bot/tests/test_alert_fired.py:27](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/tests/test_alert_fired.py#L27) — методы: название: test_format_message_critical; строка: 27; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_format_message_resolved; строка: 45; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED; название: test_format_message_truncates_long_description; строка: 58; cases: вид: SINGLE_TEST_DECLARATION; runtimeCount: NOT_COLLECTED.

**Сейчас:** critical/resolved требуют glyph palette; long-description test допускает любой len<5000 и literal [truncated].

**Проблема:** вид: STATIC_REASONING_NOT_EXECUTED; описание: Изменить harmless glyph theme — test красный. Поднять description cap до4500: сообщение может превысить4096, но len<5000 с [truncated] по-прежнему зелёный. Потеря явного resolved status не обнаруживается, если оставлены нужные glyphs.

**Переписать так:** Проверять фактические severity/status/name/facts для firing/resolved, escaped untrusted values и общий sendMessage text<=4096. Дать длинные summary/name/HTML-sensitive description у реального formatter→synthetic sender; окончание должно оставлять валидный escaped текст, без потери значения severity/status. Цветные emoji и точный truncation suffix не самостоятельный oracle. Парсер admin IDs, admin audience, HTML escaping и no-admin suppression оставить.

**Cases/граница:** THREE_METHODS

**Проверенный контракт:** [services/notification-bot/bot/notifications/alert_fired.py:52](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/alert_fired.py#L52) — why: 4096 Telegram limit и description budget; [services/notification-bot/bot/notifications/alert_fired.py:65](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/alert_fired.py#L65) — why: Status/severity/user-supplied HTML render; [services/notification-bot/bot/notifications/alert_fired.py:110](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/notification-bot/bot/notifications/alert_fired.py#L110) — why: Реальный sendMessage parse_modeHTML

### E2E: пользовательские сценарии

#### E2E-R001

**Где:** [tests/e2e/specs/student-excuse.spec.ts:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/student-excuse.spec.ts#L50) — до строки: 124.

**Сейчас:** Заполняет legacy-форму и выбирает 10 MiB PDF, затем находит submit; headman одобряет первую pending-карточку, а финал принимает любой approved archive text без связи с созданным ticket или bytes.

**Проблема:** Legacy API wrapper превращает upload 404 в успех. Чужой pending-ticket и уже существующая approved-запись могут дать зелёный результат; submit существует ещё до успеха и не доказывает закрытие диалога.

**Переписать так:** REWRITE, если owner сохраняет legacy web-panel гарантию: реальная browser-форма и граница 10 MiB, обычные API существующего синтетического стенда и настоящая мигрированная БД. Изолировать собственных student/headman и допустимое собственное занятие, задать уникальные comment/file marker; дождаться реального успешного multipart POST и получить точный created ID из ответа либо scoped before/after списка. Проверить закрытие диалога, именно эту pending-запись, decision request к этому ID и approved того же ID после reload студента. По поддерживаемому actual attachment path сверить bytes/size, если поверхность обещает persistence/forwarding; input/toast этого не доказывают. Vue-кейсы — образец, не полная замена Angular/размера; product fixes не предлагаются.

**Граница и сохранение:** Retain real browser form and 10MiB ingress boundary if still in supported web-panel scope. Use existing synthetic stand and ordinary APIs, actual migrated DB; no service.verify/mocks as endpoint proof. Current Vue request/attachment cases are useful patterns but do not silently replace this Angular/platform/size promise.

**Cases/граница:** student submits excuse with 10MB PDF, headman approves whole legacy upload/approval case

**Проверенный контракт:** frontends/web-panel/src/app/features/student/shared/student-api.service.ts:205-220 (multipart endpoint and 404-as-success); frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.ts:200-220 (close on wrapper success); frontends/web-panel/src/app/features/student/excuses/student-excuses.component.ts:208-234 (list reload after dialog); frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts:354-358,514-524 (pending/resolved split and ticket-specific decision)

#### E2E-R002

**Где:** [tests/e2e/specs/role-admin.spec.ts:25](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/role-admin.spec.ts#L25) — до строки: 31; [tests/e2e/specs/role-headman.spec.ts:23](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/role-headman.spec.ts#L23) — до строки: 35; [tests/e2e/specs/role-headman.spec.ts:45](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/role-headman.spec.ts#L45) — до строки: 48; [tests/e2e/specs/role-student.spec.ts:18](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/role-student.spec.ts#L18) — до строки: 24; [tests/e2e/specs/role-teacher.spec.ts:17](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/role-teacher.spec.ts#L17) — до строки: 25.

**Сейчас:** Allowed-route loops сверяют URL и axe(main), но не mount нужной feature. HEADMAN denial в названии обещает ADMIN и TEACHER, однако посещает только ADMIN.

**Проблема:** Уже задан /headman/late-checkins вместо реального /headman/late-checkin: authenticated wildcard может показать доступную NotFound на том же URL и пройти URL+axe. Поломка TEACHER guard в HEADMAN negative не выполняется.

**Переписать так:** REWRITE в существующем Angular browser route-guard слое с теми же Chromium/WebKit/mobile projects и реальными HTTP/данными стенда. Для каждого исходного intended route использовать канонический путь, требовать видимую main/control нужной feature и завершённые scoped data либо поддерживаемое empty state, явно отвергать NotFound/error shell; axe оставить на том же экране. HEADMAN должен посетить /admin/users и /teacher/journal, вернуться на точный собственный dashboard без target screen/data. Сохранить отдельные ADMIN, TEACHER и STUDENT+headman identity; seed student не считать обычным non-headman. Не заменять browser mount/guard backend-тестами и не удалять a11y.

**Граница и сохранение:** Retain Angular browser route-guard integration and existing Chromium/WebKit/mobile projects. Preserve distinct ADMIN, TEACHER and STUDENT+headman identities; seed student is not a plain non-headman student. Do not claim backend security tests replace browser mount/guard behavior or delete a11y scans.

**Cases/граница:** can access admin-only paths; can access headman-only paths; cannot access /admin/_ or /teacher/_; can access student-only paths; can access teacher-only paths legacy allowed-route loops and incomplete HEADMAN forbidden-route case

**Проверенный контракт:** frontends/web-panel/src/app/features/headman/headman.routes.ts:85-90 (actual singular late-checkin route); frontends/web-panel/src/app/app.routes.ts:58-65 (authenticated wildcard); frontends/web-panel/src/app/features/not-found/not-found.component.ts:19-37 (rendered 404); frontends/web-panel/src/app/core/auth/role.guard.ts:12-18; frontends/web-panel/src/app/core/auth/auth.service.ts:133-138 (exact role landing); frontends/web-panel/src/app/core/auth/student.guard.ts:10-13; frontends/web-panel/src/app/core/auth/headman.guard.ts:10-13

#### E2E-R003

**Где:** [tests/e2e/specs/mobile-attendance-requests.spec.ts:365](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/mobile-attendance-requests.spec.ts#L365) — до строки: 388.

**Сейчас:** MutationObserver запоминает совпадение aria-busy с точной русской loading-надписью submit; повторную активацию во время pending и число POST он не проверяет.

**Проблема:** Перефразирование loading или доступный spinner дают ложное падение. Разрешённая повторная отправка всё равно может удовлетворить aria-busy/тексту и последующей успешной проверке одного клика.

**Переписать так:** REWRITE на browser dependency-границе: после forwarding/fetch настоящего собственного upload удержать реальный response. Во время ожидания проверить busy и disabled submit, попробовать соответствующую keyboard/form реактивацию и потребовать ровно один POST; затем отпустить response и сохранить точные request/attachment readback и cancellation assertions. Реальные HTTP/БД остаются oracle, одна собственная заявка на существующем стенде. Release и route cleanup — в finally в прежнем конечном budget; wording-dependent observer убрать без изменения timeout/retry/product. Abort-before-delivery и ambiguous-delivery controls сохранить.

**Граница и сохранение:** One owned request on existing synthetic stand; browser needed for DOM pending/action behavior, real HTTP/DB remains the success oracle. Gate release and route cleanup in finally with existing finite budget; no timeout/retry relaxation or product change.

**Cases/граница:** exhausted manual budget still permits a failed-geo AUTO request that the headman approves transient submitting-state observer

**Проверенный контракт:** frontends/mobile-core/src/features/requests/ExcuseRequestScreen.vue:125-137 (canSubmit guards pending/ambiguous state); frontends/mobile-core/src/features/requests/ExcuseRequestScreen.vue:155-160 (guarded emit); frontends/mobile-core/src/features/requests/ExcuseRequestScreen.vue:211-215,413-418 (busy form and disabled submit)

#### E2E-R004

**Где:** [tests/e2e/specs/admin-create-user.spec.ts:24](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/admin-create-user.spec.ts#L24) — до строки: 61.

**Сейчас:** Пытается вручную задать login, выбрать role через native selectOption, взять первую группу и принять любой непустой password-like текст длиной восемь; credential не связан с response и created identity.

**Проблема:** Нынешняя Angular-форма генерирует login, использует Material mat-select и требует Telegram ID для STUDENT, поэтому setup не достигает корректного create. После исправления setup посторонний восьмисимвольный placeholder всё ещё мог бы пройти.

**Переписать так:** REWRITE, если owner сохраняет web-panel credential creation: реальный UI, auth/academic HTTP и мигрированная БД, собственные user/group и уникальная синтетическая Telegram identity. Заполнить существующие обязательные controls, выбрать STUDENT через Material option; дождаться POST 201, сохранить user ID/generated login и приватно сравнить видимый credential с response как boolean. В отдельном browser context этим exact credential проверить вход того же пользователя, затем точную собственную строку; axe оставить. Если немедленное закрытие диалога не даёт обещанной видимости credential, сохранить это failing requirement без API-only подмены или product fix. При необходимости архивировать только собственного user поддерживаемым API; общий seed не менять. Vue-кейс не полная автоматическая замена.

**Граница и сохранение:** Owned synthetic user/group; real UI plus actual auth/academic HTTP and migrated DB. Archive only the owned user through an existing supported API if needed; no shared seed mutation. Current Vue creation case at mobile-real-server:4609 onward has distinct platform/account-archive coverage and is not automatically a complete replacement.

**Cases/граница:** admin creates STUDENT, initial_password visible stale legacy create-user scenario

**Проверенный контракт:** frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.html:63-95 (Material role/group and generated login); frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.html:111-125 (required student Telegram identity); frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.html:15-25 (credential fields); frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts:134-160 (real create request and current close behavior)

#### E2E-R005

**Где:** [tests/e2e/specs/cloud-accepted-registry-export.spec.ts:1578](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/cloud-accepted-registry-export.spec.ts#L1578) — до строки: 1619.

**Сейчас:** Локальный parser проверяет одну text-only таблицу, точные columns, две упорядоченные отфильтрованные группы и отсутствие secrets/чужих данных, но принимает лишь фиксированную запись тегов без attributes и межсекционных пробелов.

**Проблема:** Безопасный scope у header, class у table или форматирующие переводы строк ломают cells/sections при неизменных декодированных значениях, порядке строк и privacy.

**Переписать так:** REWRITE только parser semantics: ограниченный downloaded HTML читать существующим browser DOMParser, требовать ровно одну таблицу и точную thead/body row/cell структуру, сверять decoded values и порядок с собственными expected records. Разрешить безопасные presentation/a11y attributes и форматирование; отвергать неожиданные data-bearing nodes, active content, лишние rows/columns и sensitive values в любом месте output. Оставить полный selector, реальную HTTP/DB job→ready→ticket→download цепочку, nonmatch/secret exclusions и meaningful malformed/extra-field negative controls. Новый parser framework/product serialization не нужны; native PDF/PNG и oracle-sensitivity controls остаются KEEP.

**Граница и сохранение:** Continue same real HTTP/DB export and browser download; only parser semantics change. Do not delete the boundary or replace it with token-presence checks. No new parser framework or product serialization changes.

**Cases/граница:** FULL admin applies group filters sorting and reset before downloading filtered HTML @cloud-accepted local group HTML boundary parser, preserve privacy/data controls

**Проверенный контракт:** services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/export/RegistryTableRenderer.java:13-22 (explicit safe projection); services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/export/RegistryTableRenderer.java:30-50 (HTML serialization); tests/e2e/specs/cloud-accepted-helper-capabilities.spec.ts:127-140,270-283 (existing DOMParser pattern; distinct roster guarantee)

### E2E: fixtures и controls

#### EF-R01

**Где:** [tests/e2e/fixtures/admin-activity-response.control.mjs:13](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/admin-activity-response.control.mjs#L13) — ranges: 13; 30; 58; 72; 190; 211; methodsCases: between/factory; actual source fragments initial/oracle/sequence; controlPage.response uses ObservedResponse.prototype/\_initializer/\_channel..

**Сейчас:** Из caller читается строковый фрагмент между exact comments/declarations; TypeScript текст компилируется new Function, ответ создаётся через внутренние поля Playwright Response.

**Проблема:** Безвредно вынести completedActivity/assertActivity в shared fixture или переименовать assertActivity — L16–28 не найдут text boundary. Замена внутреннего устройства Response при обновлении Playwright ломает L198–208, даже если публичные status/json/request и реальный E2E flow сохранены.

**Переписать так:** После cancelled headers выбирается завершённый owned GET для правильных period/bucket; его status/JSON и реальные значения всех metric cards проходят один и тот же oracle; missing replacement сохраняет исходный deadline.

**Граница и сохранение:** Вынести только существующие E2E completedActivity(page,period) и activity assertion в обычный side-effect-free fixture export, который использует live caller и существующий control. Импортировать через уже используемый в check-admission-retry.mjs Playwright transform, не импортировать spec registration. Fake completed request/response должны реализовать только реально используемые public status/json/url/request/response, без vendor prototype/private fields. Это test-helper boundary, не копия приложения. Существующий browser-free synthetic control, без новых fixtures фреймворка; provider/native/production acceptance не имитируется. baseline stable/red на всех3 фазах; fixed green на всех3 фазах; 13 существующих malformed/status/period/bucket/5metrics/fraction/delta/state/button negatives; no replacement TimeoutError, zero abortedBodyReads, original deadlines; live E2E oracle остаётся отдельно с настоящими DOM/network/services; control не объявляется browser proof

**Cases/граница:** between/factory; actual source fragments initial/oracle/sequence; controlPage.response uses ObservedResponse.prototype/\_initializer/\_channel.

**Проверенный контракт:** [tests/e2e/specs/cloud-accepted-admin-grants.spec.ts:241](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/cloud-accepted-admin-grants.spec.ts#L241); [tests/e2e/specs/cloud-accepted-admin-grants.spec.ts:338](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/cloud-accepted-admin-grants.spec.ts#L338); [tests/e2e/specs/cloud-accepted-admin-grants.spec.ts:401](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/cloud-accepted-admin-grants.spec.ts#L401); [frontends/mobile-core/src/features/admin-dashboard/AdminDashboardScreen.vue:99](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/admin-dashboard/AdminDashboardScreen.vue#L99); [frontends/mobile-core/src/features/admin-dashboard/admin-dashboard-activity-view.ts:39](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/admin-dashboard/admin-dashboard-activity-view.ts#L39); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardActivityService.java:67](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardActivityService.java#L67); [tests/e2e/check-admission-retry.mjs:10](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/check-admission-retry.mjs#L10)

#### EF-R02

**Где:** [tests/e2e/fixtures/semester-lifecycle-ui.control.mjs:6](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/semester-lifecycle-ui.control.mjs#L6) — ranges: 6; 32; 132; 144; methodsCases: between; compiled terminal; checkTerminal; checkUi..

**Сейчас:** Exact source slicing до return settled и перед async function conflict; replace точного TypeScript let status: Row = {}; и previousActive!.name; new Function исполняет текст.

**Проблема:** Переименовать локальную status в currentStatus, убрать несущественный non-null assertion или вынести archiveFromUi postconditions в функцию. Наблюдаемый archive/restore и all negative sensitivity сохраняются, но extraction/replace L14–30/L134 падает до проверки.

**Переписать так:** Exact semester/action/operation identity, all3participant receipts, inactive/unscheduled settled authority, exact idempotent replay, then correct owned UI card after transient continuation disappears.

**Граница и сохранение:** Обычные fixture exports только для существующего terminal observer и continuation/owned-card postconditions, с явными page/screen/ownedCard/action/budget arguments. Live semester scenario и browser-free control вызывают одни функции напрямую через существующий Playwright TS loading path; spec source больше не режется/не переписывается как строка. Сохранить current real API/DB tests; не подменять их models. Только existing E2E helper/control; no full-file scenario deletion, no fresh framework, no loosened ownership/deadline. Все15 terminal negatives L158–172 и positiveL155; Все4positive+8negative UI cases L177–193; controlled visibility-read-before-removal handshake L85–104,L145–151; missing/duplicate owned cards, wrong archive/restore icon/action, disabled action и genuine click error/persistent timeout всё ещё отвергаются; Source-method/status/private identity projection и frozen diagnostics не менять

**Cases/граница:** between; compiled terminal; checkTerminal; checkUi.

**Проверенный контракт:** [tests/e2e/fixtures/semester-lifecycle.spec.ts:307](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/semester-lifecycle.spec.ts#L307); [tests/e2e/fixtures/semester-lifecycle.spec.ts:2193](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/semester-lifecycle.spec.ts#L2193); [tests/e2e/fixtures/semester-lifecycle.spec.ts:2202](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/semester-lifecycle.spec.ts#L2202); [frontends/mobile-core/src/features/admin-semester/AdminSemesterScreen.vue:1250](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/admin-semester/AdminSemesterScreen.vue#L1250); [frontends/mobile-core/src/features/admin-semester/AdminSemesterScreen.vue:1359](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/mobile-core/src/features/admin-semester/AdminSemesterScreen.vue#L1359); [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterArchiveService.java:49](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterArchiveService.java#L49); [.github/workflows/ci.yml:3413](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/.github/workflows/ci.yml#L3413)

#### EF-R03

**Где:** [tests/e2e/fixtures/cloud-accepted-support.ts:265](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/cloud-accepted-support.ts#L265) — ranges: 254; 278; 289; 305; methodsCases: registryHtmlBoundary; FULL/LIMITED ordinaryRegistryExport callers L101. Узко связанный действительный counterpart groupHtmlBoundary L1571 в registry spec: не удалять group case..

**Сейчас:** Regex требует голые lower-case table/th/td tags и exact adjacent thead/tr/tbody serialization, затем exact safe columns/one row/values/nonmatching absence.

**Проблема:** В RegistryTableRenderer.html L40 добавить class к table или перенос строки между thead и tbody. Экспортируемые колонки, данные, FULL/LIMITED redaction, browser download и авторизация неизменны; tableShape становится false. Реальный второй groupHtmlBoundary L1583–1585 повторяет тот же форматный риск.

**Переписать так:** Из действительно скачанных HTML bytes извлекаются ровно одна таблица и ожидаемые заголовки/строки/значения; LIMITED не содержит Telegram ID, секреты и невыбранные аккаунты отсутствуют; group export сохраняет свои distinct filter/order/columns.

**Граница и сохранение:** В existing browser context использовать инертный DOMParser через page.evaluate для семантического чтения таблицы и вернуть только прежние closed booleans. Можно оставить text-only cells и запрет active elements; позволить attrs и whitespace между tags. Общий узкий reader в существующем fixture используется user и group callers с разными expected columns/rows. Не добавлять HTML parser dependency и не монтировать документ/не запускать его scripts. Downloaded data остаются RAM-only; reports содержат closed booleans. Это устранение привязки data oracle к serializer markup, не новый cosmetic/layout тест. ровно1таблица, однозначные header/body, точное количество и порядок колонок/строк; exact expected safe values, filtered nonmatch absent; extra/missing/duplicate row/column rejected; FULL vs LIMITED Telegram ID and actual credential absence assertions callerL120–123; existing owner/job/ticket/live revocation/session-state 409 vs current403 unaffected; group own nonmatching row/filter/sort and no login/Telegram/secret checks сохраняются, это отдельный риск

**Cases/граница:** registryHtmlBoundary; FULL/LIMITED ordinaryRegistryExport callers L101. Узко связанный действительный counterpart groupHtmlBoundary L1571 в registry spec: не удалять group case.

**Проверенный контракт:** [services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/export/RegistryTableRenderer.java:30](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/export/RegistryTableRenderer.java#L30); [tests/e2e/specs/cloud-accepted-registry-export.spec.ts:99](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/cloud-accepted-registry-export.spec.ts#L99); [tests/e2e/specs/cloud-accepted-registry-export.spec.ts:115](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/cloud-accepted-registry-export.spec.ts#L115); [tests/e2e/specs/cloud-accepted-registry-export.spec.ts:1571](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/specs/cloud-accepted-registry-export.spec.ts#L1571)

### CI, диагностические controls и scripts

#### INFRA-R01

**Где:** [scripts/smoke-prod.sh:50](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/scripts/smoke-prod.sh#L50).

**Сейчас:** Substring status=UP; grep refresh_token; grep access_token; any successful curl for schedule; logout has || true and unconditional OK.

**Проблема:** Текущий действующий login возвращает accessToken и cookie rct_refresh, поэтому прежние имена дают ложный отказ даже при правильном сервере. Независимо от этого после успешной auth admission Step3 принимает HTTP200 с пустым/посторонним телом, а Step4 при HTTP500 печатает OK: это недостаточные поздние oracle, не утверждение фактически выполненного полного PASS на текущем сервере. Изменение JSON whitespace также ломает grep.

**Переписать так:** Переписать четыре существующих шага на разбор JSON/cookie и точные статусы действующего API. Использовать заранее выделенную synthetic учётную запись и известную собственную группу/пару; подтвердить ID/дату в HAL-ответе; требовать logout204 и отказ повторного доступа прежней сессией. Ошибка и непрочитанный ответ сохраняют FAIL; безопасный вывод без тел/токенов/паролей. Секреты через private input, не argv.

**Граница и сохранение:** Реальный HTTP через Gateway к выделенному stand с действующими Auth/Schedule и их изолированными PostgreSQL+migrations; прямой DB oracle для этих smoke шагов не нужен. verify-gateway-e2e.sh не доказывает logout/содержимое расписания; local-stand-acceptance.mjs проверяет другой localhost lifecycle и также не является полным counterpart.

**Cases/граница:** health UP; login token and refresh cookie; owned schedule read; session logout

**Проверенный контракт:** services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/TokenResponse.java:6; services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/security/AuthCookies.java:13; services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthSessionController.java:113; services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/api/LessonApi.java:159

#### INFRA-R02

**Где:** [scripts/verify-gateway-e2e.sh:85](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/scripts/verify-gateway-e2e.sh#L85).

**Сейчас:** HTTP_CODE != 401 означает JWT accepted.

**Проблема:** HTTP500,404,403 и curl transport status000 дают PASS Test3. Проверка не доказывает, что подписанный пользователь достиг разрешённой операции.

**Переписать так:** Требовать точный успешный статус и ожидаемый результат разрешённого read для известного synthetic actor; отдельно сохранить no-JWT401, public-key200/PEM и login200/current token. Для forbidden actor проверять именно403 как отдельный отрицательный случай. Разбирать JSON, не печатать BODY на ошибке; ограничить curl timeout.

**Граница и сохранение:** Существующий реальный HTTP Gateway→Auth admission→Academic read на изолированных реальных сервисах/PG+migrations; без запуска чего-либо в рамках аудита. В scope нет проверенного exact counterpart сразу для login, public key, authorized routed read и no-token denial. Остальные три Test1/2/4 сохраняют самостоятельный смысл.

**Cases/граница:** known authorized protected read; unauthenticated denial

**Проверенный контракт:** services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilter.java:96; services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilter.java:113

#### INFRA-R03

**Где:** [build.gradle.kts:229](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/build.gradle.kts#L229).

**Сейчас:** Построчный regex ru.rutcampustrack...: DEBUG после удаления комментария.

**Проблема:** Валидный YAML ru.rutcampustrack: "DEBUG" включает DEBUG, но regex не совпадает из-за кавычки. Вложенное представление logging.level и эквивалентные YAML-формы также не являются прочитанной эффективной конфигурацией.

**Переписать так:** Сохранить существующий security gate, заменить текстовый matcher чтением logging.level из default/prod YAML через уже имеющийся Spring YAML/config loader и нормализацией уровней. Отрицательные synthetic варианты должны включать quoted DEBUG, dotted/nested ключ, комментарий и dev-only exemption. Ни логирующий product код, ни допустимые production уровни не менять.

**Граница и сохранение:** Конфигурационный parser/effective-property boundary в существующем JVM test/build окружении; HTTP и БД здесь не добавляют смысла. Проверка защищает утечку через DEBUG; удаление без замены теряет гарантию. verifyLogbackJsonInAllServices проверяет другое.

**Cases/граница:** default/prod logging package level; dev exemption

**Проверенный контракт:** services/api-gateway/src/main/resources/application.yml:590; services/auth-service/auth-app/src/main/resources/application.yml:133

#### INFRA-R04

**Где:** [build.gradle.kts:280](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/build.gradle.kts#L280).

**Сейчас:** configFile.readText().contains("shared/logback-base.xml").

**Проблема:** Удаление действительного include с сохранением его текста в XML-комментарии проходит gate, хотя JSON/masking не подключены. Наличие include само по себе не доказывает, что root/appender не переопределён немаскированным выводом.

**Переписать так:** Проверять действительный XML include, исключая комментарии/внешние entities, и загрузку effective Logback configuration существующим Logback test API. На synthetic marker подтвердить JSON stdout и маскирование msg. Сохранить проверку всех перечисленных service configs; не менять production ради теста.

**Граница и сохранение:** Реальный logging/configuration boundary в JVM с существующими Logback dependencies; сервисы и БД не нужны. Тест shared MaskingJsonProvider не доказывает, что конкретный сервис действительно подключил его. Это wiring с риском приватности, не косметика. Проверенный counterpart services/shared/shared-logback/src/test/java/ru/rutcampustrack/shared/logback/LogbackBaseIntegrationTest.java:24/37 строит конфигурацию программно и не загружает service XML.

**Cases/граница:** effective shared JSON+masking configuration for each listed service

**Проверенный контракт:** services/api-gateway/src/main/resources/logback-spring.xml:11; services/shared/shared-logback/src/main/resources/shared/logback-base.xml:18; services/shared/shared-logback/src/main/resources/shared/logback-base.xml:41

#### INFRA-R05

**Где:** [scripts/preflight-deploy.sh:73](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/scripts/preflight-deploy.sh#L73).

**Сейчас:** Некоторые обязательные по собственному описанию проверки пропускаются с warn; OVERALL_FAIL остаётся0, после чего сообщается all checks passed.

**Проблема:** Нет Docker: preflight не проверяет ни promtool rules, ни Compose config, но при успехе остальных шагов завершает PASS. Post-deploy может закончить PASS без проверки контейнеров, rules и TTL при отсутствующих prerequisites.

**Переписать так:** Сохранить все действующие обязательные проверки. Явно фиксировать incomplete prerequisites и не выдавать общий PASS, когда обязательный результат не наблюдён. Если существует отдельный разрешённый limited режим, его итог должен быть INCOMPLETE/SKIPPED с точным списком; это не замена полного gate. Не добавлять автоматический deploy или чтение реальных secrets.

**Граница и сохранение:** Существующие validators и bounded HTTP/native status consumers на назначенном target; synthetic checks runner branches без сервисов для missing-tool веток достаточны. CI syntax/monitoring gates проверяют source/isolated runtime; они не доказывают состояние конкретного deploy target. Оба агрегатора сохраняются.

**Cases/граница:** dashboard validator absent; Docker/promtool unavailable; rules/TTL not observed

**Проверенный контракт:** scripts/preflight-deploy.sh:16; scripts/preflight-deploy.sh:166; scripts/verify-deploy.sh:3; scripts/verify-deploy.sh:250

#### INFRA-R06

**Где:** [.github/workflows/deploy.yml:457](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/.github/workflows/deploy.yml#L457).

**Сейчас:** Успех означает size_download >=5000; HTTP status/application identity не проверены.

**Проблема:** Большая HTML500 страница или другой SPA размером6000байт проходит. Корректно оптимизированный web-panel index менее5000байт ошибочно падает, хотя routing правилен.

**Переписать так:** Заменить byte-size oracle на точный200/MIME и принадлежность entrypoint/asset ожидаемому собранному web-panel artifact/revision. Проверять доступность принадлежащего ему asset и его MIME; mismatch/error/wrong SPA остаются FAIL. Использовать существующий HTTP smoke, не вводить новый browser framework и не менять release scope.

**Граница и сохранение:** Реальный GET через текущий nginx к известному artifact; DB не нужна для проверки маршрута. Будущий deploy/runtime запуск требует отдельного решения; здесь только source proposal. Другие route status checks не доказывают конкретный served app; весь Deploy workflow не является тестовым дублем и не предлагается к удалению.

**Cases/граница:** correct served application at /login; wrong SPA/error response rejected

**Проверенный контракт:** nginx/conf.d/default.conf:352; nginx/conf.d/default.conf:364; nginx/conf.d/default.conf:370

#### INFRA-R07

**Где:** [tests/harness/test_ci_test_outcomes.py:1042](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/harness/test_ci_test_outcomes.py#L1042).

**Сейчас:** Input/expected category пары берутся из той же COLLECTOR.\* mapping, которой пользуется проверяемая реализация.

**Проблема:** Если mapping28000 ошибочно станет MISSING_DATABASE вместо AUTHORIZATION_FAILED, test_academic_jdbc_native_sql_states... возьмёт эту же новую expected category и останется green. Остальные literal-slot tests проверяют native сообщения, а не этот SQLSTATE. Это не изменение identity/status S1.

**Переписать так:** Оставить parser unit controls и все privacy/overflow/source/scope negatives. Заменить expected mapping на независимые literal vectors утверждённых native names/SQLSTATE/category; отдельно сравнить допустимый закрытый набор с контрактом. Не получать expected из production mapping и не менять collector admission/status.

**Граница и сохранение:** Actual collector functions + synthetic XML/stack fixture, без JVM/HTTP/DB: проверяется parser/закрытая проекция, не поведение PostgreSQL или продукта. Эти helper tests защищают реальные закрытые enums, caps и приватность. Их нужно усилить, не удалять.

**Cases/граница:** SQLSTATE→closed category; native exception→closed diagnostic code; privacy, cap and overflow

**Проверенный контракт:** scripts/collect-ci-test-outcomes.py:191; scripts/collect-ci-test-outcomes.py:611; scripts/collect-ci-test-outcomes.py:647

#### INFRA-R08

**Где:** [infra/prometheus/tests/check_delivery.py:193](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/infra/prometheus/tests/check_delivery.py#L193).

**Сейчас:** Инъекция только двух alerts одного service; проверка inhibitedBy и отсутствия любой HealthCheckDown delivery.

**Проблема:** Удаление service из inhibit equal делает правило слишком широким. Текущие два same-service alerts всё ещё подавляются как ожидается; отсутствие сигнала unrelated сервиса остаётся незамеченным.

**Переписать так:** В том же существующем isolated native Alertmanager/receiver сценарии добавить HealthCheckDown с другим service при том же environment и потребовать его authenticated delivery; исходный same-service alert должен остаться inhibited. Сравнивать точные synthetic labels; firing/resolved/dedup и auth checks сохранить.

**Граница и сохранение:** Actual Alertmanager + existing loopback authenticated receiver; PostgreSQL/Mongo/новый stand не нужны. PromQL formula samples не проверяют Alertmanager inhibition. check_pipeline проверяет внешний auth/mutation boundary, не эту отрицательную доставку.

**Cases/граница:** same service inhibited; different service remains deliverable

**Проверенный контракт:** infra/alertmanager/alertmanager.yml:44; infra/alertmanager/alertmanager.yml:50

#### INFRA-R09

**Где:** [scripts/test-recovery-failure-boundaries.py:370](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/scripts/test-recovery-failure-boundaries.py#L370).

**Сейчас:** Точное упорядоченное равенство inspections/queries дополнительно к writes=[], refusal и immutable bundle hashes.

**Проблема:** Безопасная перестановка уже разрешённых preflight inspections (Mongo перед Schedule) сохраняет запрет любых restore writes и label admission before queries, но ломает exact list order.

**Переписать так:** Переписать только assertion порядка на проверку нужного набора/кратности inspected target identities и существенных phase boundaries. Оставить writes=[], bundle hashes, foreign-label→queries=[] и все freshness failures. Порядок не ослаблять там, где он запрещает target access до identity admission.

**Граница и сохранение:** Existing real recovery function + controlled native-command seam and real temporary filesystem; native restore/ACL proof остаётся отдельным test-recovery.py. Ни один counterpart не сохраняет все отказные границы этого метода. Удалять метод/файл нельзя; предлагается только устойчивый oracle.

**Cases/граница:** auth/schedule/nonempty Mongo refusal; foreign label before target queries; no native restore writes; bundle unchanged

**Проверенный контракт:** scripts/recovery.py:693; scripts/recovery.py:705; scripts/recovery.py:722

## Сохраняем

- API JSON/schema/status и security contracts, права на свои/чужие данные, migration/persistence/history, CAS, outbox/receipts/ACK/DLQ, command idempotency и privacy — по защищаемому поведению. Конкретные wire literals, стабильные protobuf numbers и предметные boundary values не объявлены косметикой.
- Session/generation/late-response/revoke, retained-owner/pending-command guards, запрет blind refresh и лишних role PUT; N2 и прежние10FIXED не пересматриваются аудитом.
- Реальные parser/state/clock/retry units и meaningful oracle-sensitivity negative controls. QR redaction bad-advice control KEEP; bootstrap recognizer negative cases KEEP до согласованной замены вместе с positive oracle; historical activity source-custody SHA pins KEEP, а не «бесполезные hashes».
- Empty/seeded legacy retirement cases Attendance сохраняются оба при консолидации. У malformed Rabbit control уникальный event_type assertion сначала переносится в сильный case; отдельное удаление без этого не предложено.
- `ScheduleItemEntityTest.noTeacherId` не удаляется как reflection: его authority-инвариант переносится в реальный read/provenance contract (SCH-R14). Academic `EntityMappingIT` — удалить только contextLoads, остальные6методов сохранить. DOCX inventory6дат/30типов KEEP до AR-R11, удалить только копию алгоритма AR-D01.

## Охват и неопределённости

Inventory v2 SHA256 `0bf770b8a3b18c793bbb81a3b6c55856bc1c413d89aa189c7d7a49c3c6dcffd8`; v1→v2 добавила24 текущих support-файла через runner/import discovery, без расширения продукта/gates.966FULL/0PARTIAL/0UNREAD относятся только к выбранному текущему коду, не ко всем assets/resources/archive. Ниже число declarations, а не расширенных parameter/runtime cases; helpers/config с0cases учтены отдельно в per-file ledger.

| Область | Файлов FULL | Строк | Прочитанные статические test/Arch declarations |
| ------- | ----------: | ----: | ---------------------------------------------- |

| infra-scripts-harness-ci | 41 | 23461 | 101;34runner/support files с0test methods |
| angular-frontend-config | 76 | 10676 | 527 |
| mobile-core-features | 89 | 26277 | 703 |
| mobile-core-shared | 31 | 7377 | 358 совместно shared/PWA/TMA; dev-support0 (см. ниже) |
| pwa-tma | 18 | 5403 | 358 совместно shared/PWA/TMA; dev-support0 (см. ниже) |
| academic-domain | 57 | 25333 | 378 |
| academic-authority | 79 | 21773 | 425 |
| academic-integration | 45 | 17671 | 166 |
| auth-gateway | 82 | 26574 | 487 JUnit +4 Arch |
| attendance-reports-requests | 45 | 20231 | 343 JUnit +1 Arch |
| attendance-operations | 63 | 16176 | 287 |
| backend-shared-notification-bff-renderer | 130 | 22979 | 548 |
| telegram-bot | 56 | 13329 | 371 def;67 parameter declarations |
| schedule | 62 | 20050 | 274 JUnit +5 Arch |
| e2e-fixtures-runners | 50 | 21327 | исполняемые scenario/control runners; статические случаи в ledger |
| e2e-specs | 22 | 20127 | 77 |
| mobile-dev-fixture-support | 20 | 7252 | 0;20активных imported dev-fixture/support files |

358 shared/PWA/TMA declarations посчитаны ОДИН раз на31shared+18PWA/TMA+20dev-support файлах;50parameter declarations/178source table rows внутри них. Автоматические coarse counts исправлены по чтению: mobile features703, не707RegExp matches; Schedule279, не277coarse; E2E77, не163text matches; bot371, не372coarse. Их сумма не объявляется runtime test count или readiness процентом. [Пофайловый ledger966FULL](test-audit-read-ledger-20261020.csv) содержит область, строки и проверенный SHA256 каждого файла; method/case matrices сохранены в отдельных safe audit notes.

37 дополнительных resources/config/nonexecutables инвентаризированы отдельно; чтение адресное: Academic3, Auth6, Schedule2, bot2 FULL, другие — по crossread ledger; все37прочитанными не объявляются.7899 archival/process paths, dependencies/generated/assets отделены и не представлены как новый аудит каждого архивного файла. Production/VPS/owner data/secrets/artifacts не читались. Статический аудит не доказывает runtime/mutation чувствительность каждого предложения.

Спорные крупные удаления проверены независимо:20решений —14AGREE_DELETE,3MODIFY_TO_REWRITE,3KEEP. Это не внешнее PR APPROVE. Root применил narrowing AR-D01 и исключил EF-D01 из DELETE; первоначальные author reports сохранены с отдельным disposition override. Взаимное обоснование удаляемых mock methods не используется: для AO-D06 реальные survivors EventConsumerIT:177/736 и остающиеся guards, а не также удаляемый AO-D05.

B013/T008 one-rule mutation, B014/Gateway5-IP и B015/S1 SOURCE_METHOD/status/identity loosening FROZEN; rejected Mongo/Chromium equivalents не выполнялись. Вопросы о protected controls остаются QUESTION, а не предложением ослабить/переисполнить их. Для Mongo structural-health control INFRA вопрос о недостающем runtime proof, не автоматическое удаление. Внешние manual/Telegram/provider/VPS/deploy и Angular retirement не входят в аудит.

## Контрольная точка A и пакет для внешнего recheck

Attendance942: unit417PASS, integration251PASS/1FAILED/1SKIPPED, execution failure до JaCoCo, не недостаток coverage%. Старый producer не сохранил native exception/category и допустимую identity; root causeUNKNOWN. Новый47:417unit+252ITPASS/0fail/error/skip, закрытый artifact12283778712/digestcd8b392d… проверен; failure diagnostic branch НЕ исполнялась. Safe collector добавляет конечные категории,55syntheticcontrolsPASS, internal reviewAPPROVE; старый failure этим не объяснён и не объявлен исправленным.

Перенос: нынешняя own operation COMPLETED/Academic+Attendance APPLIED с matching tuple/receipts в обоих runs подтверждена ПОСЛЕ step/case cleanup, PER_DB_ONLY/MongoNOT_QUERIED. Это квалификация reader, не state-at-deadline или RCA старого PENDING/10s; product fix не обоснован. Missing old first native exception и deadline participant/receipt vector остаются недоступны у прежнего producer; frozen/privacy границы не обходились.

На47 attempt1 все ordinary gates SUCCESS: pushCI39463561966/25jobs, PRCI39463575909/25jobs, Coverage39463576254/10jobs, Security39463576121/3jobs, OpenAPI39463576026. Built-image scan SKIPPED.20строгих safe primariesPASS; обаN2active+suspended10, ROLE7, worker3, helper6, lesson4, privacy10, PWA/TMAtypecheck/build, оба main semesterRESTORE/COMPLETED/all3RELEASED/primaryFailurefalse. Cleanup отдельноCOMPLETED/clock+disposePASS, не замена основного сценария. Source47terminal SHA256 `32ed702c3034388ca50b41ee1eb9ef8004f0d61f7b0b90e421802a7281ecee63`.

[Единый handoff N2/lifecycle/diagnostics](HANDOFF-N2-RECOVERY-20261020.md) связывает source/docs SHA, baseline/diff, executed proof, gates/evidence и UNKNOWN. Исторические39/39,196/196FROZEN и10/11externalFIXED отдельно; N2/new executable delta designated externalPENDING. Никаких новых product findings/readiness% из количества audit-кандидатов.

Итог: **READY_FOR_INDEPENDENT_RECHECK** для опубликованного fix-пакета; **AUDIT_PROPOSALS_AWAIT_OWNER_DECISION** для двух списков. Ничего из предложенных удалений/переписываний не выполнено. Reviewer вызывается главным чатом отдельно; merge/deploy запрещены.

## Вопросы и ограничения для будущего решения

Ни один из этих пунктов не разрешает сейчас удаление, weakening или повтор protected runtime. Ответ для завершения read-only аудита не требуется; если владелец выберет реализацию, границу нужно сохранить явно.

### AD-Q01

[services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadRepositoryIT.java:2028](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadRepositoryIT.java#L2028) — id: AD-Q01; question: JdbcObservation identifies metadata/content queries by exact SELECT projection substrings. Alias/projection order refactor may break observer despite preserved bounded-before-BYTEA behavior. If this belongs to frozen B013/B014/B015 control, require owner decision before any observer rewrite; do not weaken or rerun control.; предложение: QUESTION_ONLY; keep: metadata validation before actual content materialization and SQL octet_length bound remain essential.

### AG-Q01

id: AG-Q01; action: QUESTION_PROTECTED; targets: [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/config/UpstreamHttpClientConfigurationTest.java:25](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/config/UpstreamHttpClientConfigurationTest.java#L25) — метод: positiveEntryExpiresWithinFiveSecondsAndCanResolveTheReplacementAddress; сейчас: Из настроенного HttpClient берётся resolver provider; тест строит новый DefaultDnsCache и исполняет захваченный eviction callback. Проверяет max TTL <=5 секунд, min/negative TTL0 и сохранение response timeout11 секунд.; контрпример: Это проверка настройки TTL и поведения Netty cache. Реальный native HttpClient resolver, регистрация customizer и reconnect после смены адреса в ней не участвуют.; productionReferences: [services/api-gateway/src/main/java/ru/rutcampustrack/gateway/config/UpstreamHttpClientConfiguration.java:12](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/main/java/ru/rutcampustrack/gateway/config/UpstreamHttpClientConfiguration.java#L12); verification: STATIC_ONLY; runtime/mutation NOT_RUN; proposedObservableResultAndBoundary: Только если позднее отдельно разрешено: native TCP DNS/upstream replacement proof на собственном loopback с реальным Gateway/resolver и ответом replacement в пределах принятого окна 5 секунд. Дешёвый policy unit может сохраниться; production TTL не менять.; limits: B014: Gateway5/IP native TCP sensitivity turn rejected, точная sub-operation UNKNOWN. Только вопрос о границе доказательства; не удалять, не ослаблять и не запускать эквивалент.

### AG-Q02

id: AG-Q02; action: QUESTION_PROTECTED; targets: [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/clientip/TrustedClientIpFilterTest.java:90](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/clientip/TrustedClientIpFilterTest.java#L90) — метод: orderRunsBeforeAuthAndRequestsFilters; [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/LoginBodyExtractionFilterTest.java:142](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/LoginBodyExtractionFilterTest.java#L142) — метод: orderIsAfterJwtAndBeforeInternalIssuer; [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/PwaVersionPolicyFilterTest.java:171](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/PwaVersionPolicyFilterTest.java#L171) — метод: orderRunsBeforeJwtAuth; [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/StudentRequestsNoStoreFilterTest.java:54](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/StudentRequestsNoStoreFilterTest.java#L54) — метод: orderIsBeforeAuthentication; [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RateLimitProblemDetailsFilterTest.java:127](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/ratelimit/RateLimitProblemDetailsFilterTest.java#L127) — метод: orderValue; [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/UpstreamConnectionFailureFilterTest.java:158](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/UpstreamConnectionFailureFilterTest.java#L158) — метод: onlyTheLateRoutingChainIsWrapped; [services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilterTest.java:234](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/test/java/ru/rutcampustrack/gateway/security/InternalJwtIssuerFilterTest.java#L234) — метод: filterOrder_isAfterJwtAuth; сейчас: getOrder равен фиксированному числу либо сравнён с literal -100/Netty ORDER.; контрпример: Совместное смещение orders с сохранением pipeline ломает числовые assertions. Изменение другого filter order может нарушить нужное отношение при PASS. Это не HTTP доказательство ранней privacy/security/body-limit/error-rewriting гарантии.; productionReferences: [services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java:456](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java#L456); [services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/LoginBodyExtractionFilter.java:139](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/LoginBodyExtractionFilter.java#L139); [services/api-gateway/src/main/java/ru/rutcampustrack/gateway/clientip/TrustedClientIpFilter.java:113](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/services/api-gateway/src/main/java/ru/rutcampustrack/gateway/clientip/TrustedClientIpFilter.java#L113); verification: STATIC_ONLY; runtime/mutation NOT_RUN; proposedObservableResultAndBoundary: Предпочитать native HTTP через зарегистрированный pipeline: spoofed identity/peer headers, invalid JWT с oversize login, early401/429 с no-store/correlation, поздний upstream connection failure. Существующие RateLimitIT122 и InternalJwtIssuerIT185 сохраняются как разные endpoint states; числа order не являются самостоятельной пользовательской гарантией.; limits: IP-related часть B014 защищена: точный rejected scope UNKNOWN. Никаких удалений, повторных запусков или ослабления в этом аудите.

### Shared/Notification/BFF/renderer

topic: Protected approvals; статус: QUESTION/NOT_RUN; note: B013 T008 rights-sensitivity; B014 Gateway5/IP native checking/sensitivity; B015 S1 SOURCE_METHOD/status→ordinal and declaration loosening, frozen rejected Mongo/Chromium equivalents сохранены. Никакой повторной mutation/ослабления/исполнения.

### Shared/Notification/BFF/renderer

topic: Historical failure causes; статус: UNKNOWN; note: Статический аудит не устанавливает исторические причины CI и не меняет old10 FIXED или N2 external PENDING; external APPROVE не заявляется.

### MS-Q01

id: MS-Q01; location: [frontends/pwa-vue/src/reconnect-recovery.test.ts:31](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/pwa-vue/src/reconnect-recovery.test.ts#L31) — метод: exact production handler name extraction; note: Full actual handlers + real auth tests are valuable and remain KEEP, especially N2 late current/candidate responses. Function-name changes still break this harness. Do not retire these author-hosted controls while external N2 is PENDING; root can consolidate only after a complete behavior-preserving replacement and preserved negative controls.

### MS-Q02

id: MS-Q02; location: [frontends/tma-vue/tests/tma-entry.test.mjs:18](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/frontends/tma-vue/tests/tma-entry.test.mjs#L18) — метод: literal main-module script tag; note: Attribute reordering of the same real entry script breaks this source guard. It also does not prove SDK network timing. KEEP the official URL/bootstrap-order contract; a later behavior/semantic-carrier rewrite must preserve it. No browser/external-SDK proof was executed and no frozen Chromium equivalent proposed.

### E2E: fixtures и controls

[tests/e2e/fixtures/http500-diagnostic-contract.py:347](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/http500-diagnostic-contract.py#L347) — question: Строковые CI command/order checks хрупки к harmless shell refactor, но связаны с source/privacy admission. Не предлагается ослабление frozen guards; отдельная owner-reconciliation нужна при реальном изменении carrier.

### E2E: fixtures и controls

[tests/e2e/fixtures/artifact-privacy.ts:140](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/artifact-privacy.ts#L140) — question: Pinned special source frames/line numbers хрупки к refactor, но это ограниченное untrusted source-location projection; frozen Source-method/identity routing не переписывать.

### E2E: fixtures и controls

[tests/e2e/fixtures/semester-lifecycle.spec.ts:5554](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/semester-lifecycle.spec.ts#L5554) — question: Полная OTP container identity сравнивается raw JSON order, тогда как owned-container-identity нормализует maps/mount order. Protected identity concern: только QUESTION, никакой автоматической deletion/loosening.

### E2E: fixtures и controls

[tests/e2e/fixtures/lesson-transfer-diagnostic.control.py:218](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/tests/e2e/fixtures/lesson-transfer-diagnostic.control.py#L218) — question: FakePG snapshot позволяет wrong SQL join остаться green, поскольку возвращает готовые facts. В данном файле заявлено только control/projection NOT_RUN, actual PG qualification отдельно; не требовать новый DB suite как косметическое расширение аудита.

### INFRA-Q01

[scripts/test-recovery-failure-boundaries.py:186](https://github.com/DITEKmax/rutcampustrack/blob/47daac1bb3d48eb2a912689a66d95b938798c284/scripts/test-recovery-failure-boundaries.py#L186) — id: INFRA-Q01; control: test_fresh_mongo_health_contract_preserves_bootstrap_and_every_deadline; observation: JS проверяется наличием строк auth/isWritablePrimary/getCollectionInfos и отсутствием rs.initiate. Добавление раннего quit(0) перед этими строками не обнаруживается данным source oracle; тест branch extraction сравнивает результат с тем же helper.; productionRefs: scripts/recovery.py:92; .github/workflows/ci.yml:6790; ownerQuestion: При отдельном будущем решении по замороженной Mongo-ветке допустим ли bounded реальный failure-matrix этой health command с сохранением bootstrap/deadlines/auth/primary/read guarantees? До решения сохранить текущие контроли; source-only gap не считать native PASS.; freeze: Задание явно замораживает ранее отвергнутые Mongo equivalents; не запускать и не переносить через другой caller/schema/model/ordinaryCI.; action: QUESTION_ONLY_NO_IMPLEMENTATION_NO_EXECUTION
