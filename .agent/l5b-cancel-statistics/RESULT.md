# L5B: отмена ретроспективных занятий и статистика

Дата: 2026-09-20. Scope S2, read-only source inspection; runtime/tests NOT_RUN.
База: `3d4115f3a4c4ddba473689e27ac1a0efb519a202`, worktree `.agent/worktrees/v2-l5b-service-identity`; lead подтвердил HEAD и отсутствие tracked changes.
Contract: `.agent/orchestration-v2/L5B-CANCEL-STATISTICS-SCOUT.md`.
RULES SHA-256: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
Scout: fresh `gpt-5.6-luna` / `max`, read-only, без детей; завершён и освобождён. Прочитаны 5 основных исходников, использованы 5 существующих карт, найдены 3 тестовых файла. Дополнительный поиск остановлен по указанию главного.

## Вывод и граница решения

Индивидуальная отмена уже допускает прошедшее CLOSED-занятие и сохраняет физическую строку Schedule в CANCELLED. Однако ретроспективное создание нельзя объявить готовым для статистики только на основании этого поведения. Legacy ReportService считает сохранённые AttendanceRecord: CLOSED-занятие без документа посещаемости не входит в знаменатель. Автоматическое создание ABSENT текущим обработчиком закрытия использует нынешний состав группы и активный семестр, что не доказывает корректность исторических отметок.

Главный запросил у владельца отдельную политику исторических отметок: ручное заполнение неотмеченных занятий либо трактовка отсутствующей отметки как пропуска с корректным историческим составом. Начальный статус ретроспективных занятий и материализация отметок этим исследованием не утверждены. Исправления не назначены.

## Точные источники

Пути ниже относительно указанного worktree.

- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java`: `requireHeadmanForGroup` 58–70, `cancelLesson` 100–133, `restoreLesson` 137–155. ADMIN либо HEADMAN с проверкой группы Academic; отмена PLANNED/ACTIVE/CLOSED, сохранение строки и cancellation audit, событие LessonCancelledEvent.
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java`: 45–110. Started — no-op; closed/cancelled/deleted делегируются LessonEventService.
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java`: closed 61–106 создаёт ABSENT/AUTO_SCHEDULER для текущего roster/semester; normal cancel 135–143 меняет только существующие документы на CANCELLED. One-off cancel 124–133 и deleted 152+ удаляют документы. Lead лично открыл границы closed/cancelled/deleted; главный независимо прочитал критичные методы.
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java`: 59–88. Просроченный PLANNED может получить Started и Closed за один tick.
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java`: `getStudentStats` 171–233, `buildOverall` 291+, `buildWeekly` 323+, `filterExistingLessons` 443–451. Расчёт по AttendanceRecord, фильтр существующих Schedule ID, исключение CANCELLED. Отдельная гарантия исключения позднего ABSENT после отмены этим read-only исследованием не доказана.

Существующая B2-карта описывает собственные отметки семестра, не физические занятия без отметок. Это направление контракта, а не новое runtime-доказательство и не основание считать отсутствующую отметку уже учтённой. По существующей карте reader `ScheduleGrpcServiceImpl.getLessonsByIds:146` / `resolveLesson:197` опирается на текущие поля ScheduleItem: новые snapshot-колонки сами по себе не переключают reader. Эти два утверждения перенесены из карт; дополнительное чтение прекращено, их реализация здесь не принимается.

## Узкие регрессионные проверки для следующего пакета

Существующие именованные проверки, без запуска:

- `ReportServiceTest.stats_cancelledExcluded` 120–135; `stats_allCancelled` 246–255.
- `ReportServiceTest.lessonAttendance_missingStudentShowsAbsent` 265+ — не доказательство знаменателя для отсутствующих документов.
- Schedule `SecurityIdorIT.cancelLesson_foreignGroup_returns403` 150–156.
- Schedule `LessonCancelEventIT.cancelLesson_publishesCancelledEvent` 81–87.

В просмотренной области не найдены адресные проверки CLOSED без AttendanceRecord в знаменателе и порядка retrospective Started/Closed → cancel → late ABSENT. Это ограниченный результат поиска, не заявление об отсутствии тестов во всём репозитории.

## Ограничения для Lessons

Использовать индивидуальный retained cancel и `lesson.cancelled`; не подменять их destructive `lesson.deleted` / `lesson.one_off.cancelled`. Не считать current-roster auto-ABSENT корректной исторической политикой. Не объявлять CLOSED без materialized marks готовым для legacy statistics. Initial status/events, исторический roster и read projection согласовать с главным после ответа владельца. Нет разрешения менять события, схему, авторизацию или восстановить mass-cancel.

Код, данные, существующие evidence и другие направления не изменялись. Никаких Gradle/Docker/runtime/check reruns. Готовность всей статистики не заявляется.

## Решение владельца 2026-09-20 после завершения scout

Ответ: «Автоматом ставится всем \"н\"». Он заменяет отмеченное выше ожидание выбора между ручным заполнением и автоматическим пропуском. Остальные source gaps сохраняются: нужен исторический состав группы, семестр физического занятия и защита от порядка close/cancel. Главный поручил только bounded repair contract; код не разрешён. См. `REPAIR-CONTRACT.md`. Scout остаётся освобождённым, новый поиск не назначен.
