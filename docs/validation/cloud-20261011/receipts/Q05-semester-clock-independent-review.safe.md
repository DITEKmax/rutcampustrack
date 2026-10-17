# Q05 semester educational clock — независимое review

Вердикт: **REQUEST_CHANGES**, одно замечание P2.

Проверена revision `2952edc5653e56e002a2fcc9268b089d8520ea24`, единственный parent `10ab49dbe1e768642ab1c480e6f26f30a871874e`, ровно три назначенных пути. SHA256 совпадают с packet: SemesterService `68dc45a4521b9b64933fedcf223a996a155a70c8422f4f21fd228cdff5f3f1cb`, SemesterActivationJob `9faa45afd3b475f39fb427e46b2d7e7ffb22e34355f6d393db03c29ca635a1aa`, SemesterServiceTest `59d2d32d6b336692f5f2e95fdbbe60009219e2863fa8d28fe384df8249e03770`. Requested/selected Sol6.1/high; actual metadata UNKNOWN.

## F1 — P2: нормализация Clock в конструкторе замораживает существующий изменяемый test clock

Место: `/workspace/rct-worktrees/recovery/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java:92`; аналогичное преобразование в `SemesterActivationJob.java:43`.

`this.educationalClock = clock.withZone(...)` теперь выполняется только при создании bean. В существующем `/workspace/rct-worktrees/recovery/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIT.java:94` есть `@Primary MutableEventClock`: начальный instant относится к 2027-04-15, а `withZone` на строках 107–108 возвращает `Clock.fixed(current, zone)`. Через неизменённый ClockConfig этот же clock попадает в default EducationalTime. Поэтому новый SemesterService сохраняет неподвижный снимок 2027-04-15 и больше не видит `eventClock.setDate(...)`.

Конкретное следствие: existing `activateSemester_publishesSemesterArchivedEvent` устанавливает 2028-04-15/2028-10-15 (строки 452/465), но активации обоих семестров попадут в ветку будущего диапазона вместо переключения active и outbox; assertion на строках 467–468 не получит ожидаемое архивное событие. Аналогично `concurrentActivationOfSameSemester_archivesPreviousSemesterOnce` устанавливает даты 2038 года (528/535), но не получит ожидаемый active B. До изменения `activateLocked` вызывал `clock.withZone(...)` при каждой операции и видел обновлённый instant. Это конкретная регрессия совместимости существующей fixture, а не установленный дефект production clock. Новая проверка использует только Clock.fixed и её не выявляет.

Минимальная correction в назначенном scope: хранить исходный выбранный educational Clock и вычислять московскую дату при каждой операции, например через `LocalDate.now(educationalClock.withZone(MOSCOW))`, либо `LocalDate.ofInstant(educationalClock.instant(), MOSCOW)`. Применить одинаково ко всем пяти day guards в SemesterService и выборке due IDs в Job. Не заменять обычный Clock и не ослаблять existing EventIT. Для адресной recheck достаточно исправленного clock usage и влияния на указанные два existing EventIT; повторное полное review не требуется. Native воспроизведение reviewer не запускал; цепочка установлена из исходников.

## Остальная проверенная область

- Полностью прочитаны Root TASK-CONTRACT SHA `62afda54233d3f27e894d0108c6d12a99a217f11aa686a33550739a93d6c75ed`, CURRENT, decisions, questions, resources, применимые AGENTS/RULES/workflow. T011 не вводит новой продуктовой политики; применены принятые Moscow educational dates и решения об архивном расширении/restore.
- Все календарные проверки трёхфайлового diff переключены на выбранное EducationalTime; Spring ObjectProvider учитывает primary override и имеет существующий fallback. Старые public constructors сохранены. ClockConfig SHA `2402b9d2b69a57692ee24b2735e114276d7af59a529418302cc5e2a046c37947` действительно неизменён. `createdAt = OffsetDateTime.now()` не затронут; новых связей security/audit с учебным clock в diff нет.
- Unit проверяет конкретные create/update/restore отказы и отсутствие изменения restore version/transition; activation действительно меняет active/scheduled/version и повтор не повышает version. Spring context закрывается, исходный default TimeZone восстанавливается в finally. Независимых блокирующих замечаний к этим assertions нет.
- Root `Q05-semester-clock-red-before.safe.json` и scoped author outcome показывают meaningful old RED: compile PASS, один assertion failure из-за отсутствующего BadRequest, errors=0; save возвращал candidate, поэтому это не NPE/infra. Три более ранних infrastructure отказа отдельно сохранены. GREEN receipt сообщает 16 unit, 0 failures/errors/skips, scoped format/compile PASS, все owned процессы reaped; reviewer эти запуски не повторял.
- Открыты repository due-ID predicate, обе выбранные existing SemesterArchiveStateIT проверки с replay/concurrency и expired/archive/restore, действующий FileClock/ClockInitializer. FileClock.withZone сохраняет чтение файла, так что отдельной проблемы его живого обновления не найдено. PG evidence на момент чтения NOT_RUN/PENDING; источник approval не означает actual API/PG/client PASS.

Продуктовые исходники, Root docs, сохранённый untracked `semester-lifecycle.spec.ts` не изменялись и fixture не включалась в review. Java/Node/Docker/network/download/subagents не запускались. B013/B014/Q012 не затрагивались. Единственный созданный файл — этот отчёт.

## Адресная recheck F1 — итоговый вердикт

**APPROVE_SOURCE_LOCAL** для `0f7fd4f6391489161d00a5f31b0d75b92390ff83` (parent `2952edc5653e56e002a2fcc9268b089d8520ea24`). **F1 закрыто**; исходный REQUEST_CHANGES выше относится к предыдущей revision. Новых блокирующих замечаний в correction и её влиянии не найдено. Полное review не повторялось.

Финальные SHA256 непосредственно сверены:

- SemesterService.java: `615d0534156e7c4e3ca219d164d503b4601a5d0c0d35aeb368ac88b1e7f685f9`.
- SemesterActivationJob.java: `bd160f75da6b62e7eb3395d8299dd86c8dde1f2aaef679c71c0594e31feb3cd5`.
- SemesterServiceTest.java: `74fa6ba7d0a8ca5f2497f6f4ab43087674e581bfd7f934c4e530bc69ddc2d7be`.

В Service:93 и Job:44 теперь сохраняется исходный selected Clock. Все пять вычислений московского дня в Service:165/201/265/310/505 и Job:53 используют `LocalDate.ofInstant(educationalClock.instant(), MOSCOW)` при операции. Constructor больше не вызывает `withZone`, поэтому существующий MutableEventClock сохраняет живое чтение времени после `setDate`. Преобразование instant в Moscow не зависит от зоны самого Clock или JVM и не меняет provider/fallback/public constructors.

Дополненный тот же unit-метод воспроизводит EventIT-style clock с `withZone`, возвращающим фиксированный снимок. Beans создаются до изменения AtomicReference; первый job tick оставляет будущий target scheduled/inactive, затем advance учебного дня приводит к active=true/scheduled=false/version=1, следующий replay сохраняет version=1. Date-sensitive repository answer не возвращает target при неверном дне; lenient ancillary stubs позволяют ошибке проявиться в наблюдаемом состоянии, а не unused-stub exception. Восстановление TimeZone в finally и закрытие context сохранены.

Просмотрены `outcome.safe.json#q05_clock_correction.F1_red/F1_green`: BAD2952 RED даёт ровно 1 assertion failure, 0 errors, ожидаемое due.active=false; XML SHA `b0522a85e8a637d8848f35902fdb99e9f12bd032152f4a9ee0f06f38d414c984`. Исправленный GREEN: 16 tests, 0 failures/errors/skips, mutable control executed, native exit0, 34.211s, все owned процессы reaped. Это отдельная meaningful sensitivity F1; исходный Moscow create RED сохраняется. Reviewer native не запускал.

ClockConfig остаётся SHA `2402b9d2b69a57692ee24b2735e114276d7af59a529418302cc5e2a046c37947`; untracked Q05 fixture остаётся SHA `17150d662d06e3e4758d32a27a8d6c95d39d8e0f71b39cea49ddeaf458bcda55`. Трёхфайловый inventory сохранён, tracked worktree clean. Общий security Clock, audit createdAt, API/proto/schema не изменены correction.

Ограничение: actual EventIT с RabbitMQ reviewer/author здесь не запускали; compatibility F1 закрыта чтением кода и эквивалентным unit RED/GREEN. Две выбранные PostgreSQL проверки пока NOT_RUN/HELD; SOURCE_LOCAL approval не означает PG/API/client PASS и позволяет отдельно продолжить уже согласованную проверку этих сценариев. Root docs/продукт/fixture reviewer не изменял; изменён только этот отчёт.
