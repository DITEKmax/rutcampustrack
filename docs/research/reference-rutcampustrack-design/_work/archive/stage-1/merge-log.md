# merge-log.md — что сделано при слиянии решений K1–K5 в реестр

> Строки компонентов взяты **дословно** из разделов «Строки для реестра» файлов `decisions-K1…K5.md`.
> Скрипт только размещал их: находил старую строку по имени компонента и подставлял новую.
> Ни одно описание не переформулировано.


## Сводка

| Показатель | Значение |
|---|---|
| Строк компонентов было | 243 |
| Строк компонентов стало | 226 |
| Операций всего | 84 |
| Таблиц в файле | 53 |
| Строк с расхождением числа колонок | **0** |
| Живых ссылок на выведенные имена | **0** (все оставшиеся — в пояснениях «заменяет / поглощает») |
| Размер | 263 КБ (было 254) |

## Добавлена — 6

| Компонент / место | Источник решения |
|---|---|
| `FilterBar` | K1 — новый |
| `StepPager` | K1 — новый примитив |
| `DataTable` | K2 — новый примитив |
| `MetricValue` | K4 — новый примитив |
| `DefinitionList` | K4 — новый |
| `TimetableGrid` | K3 — новый примитив |

## Заменена — 28

| Компонент / место | Источник решения |
|---|---|
| `Tabs` | K1 |
| `DatePager` | K1 |
| `Tooltip` | K5 |
| `AttachmentList` | K5 |
| `StatCard` | K4 |
| `MetricDelta` | K4 |
| `LessonCard` | K3 (владение -> domain schedule) |
| `AttendanceStatusBadge` | K4 |
| `MonthCalendar` | K3 (поглощает DeadlineCalendar) |
| `HomeworkList` | K3 + K5 |
| `SubjectStatsTable` | K2 — переименован в StatsBySubjectTable |
| `AttendanceStatusPicker` | K4 |
| `LessonAttendanceCounter` | K4 |
| `LessonPager` | K1 (обёртка StepPager) |
| `GroupMembersTable` | K2 |
| `MemberRoleBadge` | K4 |
| `SubjectsTable` | K2 — переименован в SubjectLessonTypeTable |
| `SubjectProgressCell` | K4 |
| `LessonTypePicker` | K3 |
| `ScheduleBuilderGrid` | K3 (обёртка TimetableGrid) |
| `ScheduleSlotCell` | K3 |
| `ScheduleSlotCard` | K3 |
| `LessonManagementGrid` | K3 (обёртка TimetableGrid) |
| `LessonManagementCell` | K3 |
| `MyAssignmentsTable` | K2 |
| `UsersTable` | K2 |
| `UserRoleStatusList` | K4 |
| `GroupStatusCell` | K4 — заменён EntityStatusCell |

## Удалена — 24

| Компонент / место | Источник решения |
|---|---|
| `ContextBar` | K4 — заменён DefinitionList variant="inline" |
| `PeriodTabs` | K1 — поглощён Tabs |
| `ViewModeTabs` | K1 — поглощён Tabs |
| `HomeworkScopeTabs` | K1 — поглощён Tabs |
| `LessonTypeToggleGroup` | K3 — поглощён LessonTypePicker |
| `TicketScopeTabs` | K5 — поглощён Tabs (K1) |
| `ChartScopeTabs` | K1 — поглощён Tabs |
| `HomeworkFilterBar` | K1 — поглощён FilterBar |
| `TicketFilterBar` | K1 — поглощён FilterBar |
| `ReturnToCurrentWeekButton` | K1 — поглощён пропом showCurrentReset |
| `HeadmanHomeworkList` | K3 — поглощён HomeworkList / HomeworkItem |
| `DeadlineCalendar` | K3 — поглощён MonthCalendar |
| `ExcuseTypeBreakdown` | K4 — заменён DefinitionList variant="stack" |
| `AttendanceJournalWeekTable` | K5 — поглощён AttendanceJournalGrid lessonScope="week" |
| `StatsScopeTabs` | K1 — поглощён Tabs |
| `TeacherJournalTable` | K2 — слит в AttendanceJournalGrid |
| `LessonAttendanceList` | K2 — слит в LessonAttendanceRoster |
| `TeacherStatsTable` | K2 — разделён по оси: StatsByStudentTable + StatsByGroupTable |
| `GroupScopeTabs` | K1 — поглощён Tabs |
| `UserFilterBar` | K1 — поглощён FilterBar |
| `GroupFilterBar` | K1 — поглощён FilterBar |
| `SemesterStatusFilter` | K1 — поглощён FilterBar |
| `SystemMetricList` | K4 — заменён DefinitionList variant="stack" |
| `SemesterStatusCell` | K4 — заменён EntityStatusCell |

## Перенесена — 1

| Компонент / место | Источник решения |
|---|---|
| `ExcuseReasonPopover` | K5 — из student в новый блок attendance |

## В новый блок attendance — 6

| Компонент / место | Источник решения |
|---|---|
| `AttendanceJournalGrid (из AttendanceJournalSubjectTable + TeacherJournalTable)` | K2 / K5 |
| `LessonAttendanceRoster (из GroupAttendanceRoster + LessonAttendanceList)` | K2 / K5 |
| `StatsByStudentTable (из GroupStatsTable + TeacherStatsTable scope=students)` | K2 / K5 |
| `StatsByGroupTable (из TeacherStatsTable scope=groups)` | K2 / K5 |
| `ExcuseReasonPopover (перенесён из student)` | K2 / K5 |
| `ExcuseTicketPanel (перенесён из headman)` | K2 / K5 |

## Добавлен раздел — 1

| Компонент / место | Источник решения |
|---|---|
| §5 → attendance | K2 + K5 — сквозной блок для headman и teacher |

## Правка текста — 16

| Компонент / место | Источник решения |
|---|---|
| `ManualAttendancePanel: ContextBar → DefinitionList (K4)` |  |
| `WeekJournalPanel: ContextBar → DefinitionList (K4)` |  |
| `SubjectJournalPanel: LessonTypeToggleGroup → LessonTypePicker (K3)` |  |
| `SystemActivityPanel: PeriodTabs → Tabs (K1)` |  |
| `StatsDetailPanel: SubjectStatsTable → StatsBySubjectTable (K2)` |  |
| `FilterBar: LessonTypeToggleGroup → LessonTypePicker (K3)` |  |
| §1: перечень вариантов дополнен (K2, K4) |  |
| §1: четыре новых соглашения (K1, K2, K4, K5) |  |
| §9: закрыт вопрос о ContextBar |  |
| §9: закрыт вопрос об обёртках Tabs |  |
| §9: закрыт вопрос GroupStatsTable/SubjectStatsTable |  |
| §9: закрыт вопрос о списках дз |  |
| §9: закрыт вопрос о ReturnToCurrentWeekButton |  |
| §9: закрыт вопрос TeacherStatsTable/GroupStatsTable |  |
| §11: помечено исполнение переименования ExcuseTicketDialog |  |
| §12.1: счёт экранов 30 → 27 |  |

## Переписан раздел — 1

| Компонент / место | Источник решения |
|---|---|
| §12.4 + новый §12.5 | K1–K5 |

## Добавлена строка — 1

| Компонент / место | Источник решения |
|---|---|
| §10 журнал изменений — этап 1.5 | K1–K5 |

## Что проверено автоматически

- **Уникальность цели.** Каждая замена искала ровно одну строку с этим именем. Если бы имя нашлось дважды или ни разу — скрипт останавливался с ошибкой, а не угадывал. Так и вышло один раз: `EntityStatusCell` пришёл в пятиколоночном формате §4, а вставлялся в четырёхколоночную таблицу admin — пришлось конвертировать явно.
- **Число колонок.** Перед каждой заменой сверялось число ячеек старой и новой строки. По всему файлу — 53 таблицы, расхождений нет.
- **Остаточные имена.** Все 33 выведенных имени проверены на живые ссылки в §4 и §5. Оставшиеся упоминания — только внутри пояснений вида «заменяет `ContextBar`», «поглощён `ReturnToCurrentWeekButton`», «Сведён из `TeacherJournalTable`». В §10 (журнал изменений) старые имена оставлены намеренно: это история, её переписывать нельзя.

## Что сделано сверх подстановки строк

Эти правки не были готовыми строками — их пришлось собрать из текста решений:

1. **Заведён раздел §5 → attendance** (сквозной для headman и teacher). K2 писал строки «в блок attendance», предполагая, что он есть, — его не было. Туда переехали шесть компонентов: `AttendanceJournalGrid`, `LessonAttendanceRoster`, `StatsByStudentTable`, `StatsByGroupTable`, `ExcuseReasonPopover` (из student), `ExcuseTicketPanel` (из headman).
2. **Склеены две строки-дополнения.** K5 давал `AttendanceJournalGrid` и `HomeworkList` пометкой «строка K2/K3 — дополнить». Текст дополнения приклеен к ячейке «Назначение» базовой строки.
3. **`DataTable` переведён в формат §4.** K2 отдал его в четырёхколоночном виде, а §4 пятиколоночный. Ключевые данные и семантические пропы извлечены из его же описания, ничего не дописано.
4. **Перекрёстные ссылки.** Шесть компонентов вне скоупов ссылались на удалённые имена: `ManualAttendancePanel` и `WeekJournalPanel` на `ContextBar`, `SubjectJournalPanel` на `LessonTypeToggleGroup`, `SystemActivityPanel` на `PeriodTabs`, `StatsDetailPanel` на `SubjectStatsTable`, и сама новая строка `FilterBar` на `LessonTypeToggleGroup`.
5. **§12.1 — счёт экранов.** Было «30 экранов», стало «27 уникальных» с пояснением, откуда расхождение.
6. **§12.4 переписан** из списка задач в таблицу итогов, добавлен **§12.5** с тем, что осталось открытым.

## Что НЕ сделано и ждёт решения

- **Спеки экранов не тронуты.** Реестр теперь называет компоненты новыми именами, а 26 спек — старыми. Список правок — в `spec-edits.md`, задача 2 плана.
- **Фильтры в таблицах.** Решение «фильтровать в таблице по всем полям» переворачивает правило спеки 132 и сузит `FilterBar`. Строка `FilterBar` в реестре пока описывает прежнее разделение — правится после задачи 3.
- **Вопросы чатов K1–K5** (около 35 штук) в реестр не вносились: часть требует продуктового решения.
- **`useRovingGrid`.** Клавиатурный режим крупной сетки описан независимо у `DataTable` и `TimetableGrid` — возможно, это один composable под двумя именами. Записано в §12.5.
