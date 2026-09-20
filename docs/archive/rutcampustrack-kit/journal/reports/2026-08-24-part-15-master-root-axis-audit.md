# Аудит HUG-регресса мастерных корней после Plugin API resize

Дата: 24.08.2026. Figma: `design-system/ui-kit-rct`. Режим Figma — только
чтение; мастерные узлы и продуктовые кадры не менялись.

## Ответы на вопросы

1. **Область регресса:** проверено 979 `COMPONENT`-корней на страницах частей
   1–15. Технически фиксированную ось высоты имеют 154 корня. После исключения
   140 конструктивно фиксированных контролов, навигации, календарных/табличных
   ячеек и слотов остаётся **14 ошибочно FIXED-корней**.
2. **Разбивка:** headman-блоки — 0; student-блоки — 12; общие/domain-мастера
   частей 1–10 — 2; teacher/admin/auth-блоки — 0.
3. **HomeworkToday:** root трёх веток исправен — `overflowDirection=NONE`,
   `clipsContent=false`. VERTICAL находится во вложенном экземпляре общей
   ветки `homework/HomeworkList groupBy=none, order=lesson-order,
   scroll=inner`; исходный мастер этой ветки также VERTICAL.
4. **Потребители HomeworkList на student:** весь набор имеет 12 прямых
   физических потребителей в student-блоках и 18 видимых вхождений на 48
   student-кадрах. Конкретная дефектная ветка `lesson-order/inner` имеет
   **0** student-потребителей; её 3 прямых физических потребителя находятся
   только в трёх ветках `block/HomeworkToday`.

## Метод и единицы

Сначала одним обходом получены все 979 component-root. Для вертикального
auto-layout высотной осью считался `primaryAxisSizingMode`; для
горизонтального — `counterAxisSizingMode`. Кандидат попадал в технический
список при `FIXED` на высотной оси.

Из 154 кандидатов исключены размеры, которые являются конструкцией:
`AppButton`, `TextInput`, `SelectField`, `PasswordField`, элементы навигации,
`AppHeader`, status-mark/picker, календарные ячейки, `LessonCard variant=cell`,
`ScheduleSlotCell`, `CodeInput` и аналогичные контролы. Их контракты задают
фиксированную высоту, поэтому HUG там не требуется.

Оставшиеся 14 проверены по правилу блоков «фиксированная ширина и HUG по
высоте», по соседним веткам того же набора и по истории поздних resize.
Число 14 — физические мастерные корни, не A и не число развёрнутых ссылок.

## 14 ошибочно FIXED-корней

### Блоки headman — 0

В 78 component-root страницы «12. Блоки — headman» фиксированной высотной
оси у корней нет. Это подтверждает только состояние после ремонта 18/41 и не
распространяется на вложенные общие мастера.

### Блоки student — 12

| Набор | Ветка | ID | Высотная ось | Размер |
|---|---|---|---|---:|
| `block/CurrentLesson` | width=1920 | `890:4` | primary FIXED | 1208×350 |
| `block/CurrentLesson` | width=1440 | `890:501` | primary FIXED | 909.33×350 |
| `block/CurrentLesson` | width=1280 | `890:847` | primary FIXED | 596×350 |
| `block/SubjectStat` | width=1920 | `894:189` | primary FIXED | 592×350 |
| `block/SubjectStat` | width=1440 | `894:313` | primary FIXED | 442.67×350 |
| `block/SubjectStat` | width=1280 | `894:403` | primary FIXED | 596×350 |
| `block/TrendPanel` | my-week, width=1920 | `895:253` | counter FIXED | 1824×406 |
| `block/TrendPanel` | my-week, width=1440 | `895:926` | counter FIXED | 1376×406 |
| `block/TrendPanel` | my-week, width=1280 | `895:1351` | counter FIXED | 1216×406 |
| `block/SubjectsBreakdown` | width=1440 | `981:10201` | primary FIXED | 1376×622 |
| `block/SubjectsBreakdown` | width=1280 | `981:10604` | primary FIXED | 1216×622 |
| `block/DeviceLink` | width=1280 | `1006:20160` | primary FIXED | 794.67×348 |

Асимметрия подтверждает регресс у трёх последних наборов: `TrendPanel
my-semester` остаётся HUG во всех ширинах; `SubjectsBreakdown width=1920` —
HUG; `DeviceLink width=1920|1440` — HUG. У `CurrentLesson` и `SubjectStat`
FIXED стоят сразу во всех ширинах, но это всё равно нарушает общий контракт
корня блока HUG по высоте.

### Общие/domain-мастера — 2

| Мастер | Ветка | ID | Высотная ось | Размер | Основание |
|---|---|---|---|---:|---|
| `shared/FileUploadField` | state=idle | `56:6` | primary FIXED | 360×268 | остальные 7 состояний этого набора HUG; фиксированная idle-высота документом не задана |
| `headman/LessonActionsPanel` | standalone | `717:6872` | primary FIXED | 760×100 | панель выросла 64→100 через поздний resize; размер не является конструктивным исключением |

`LessonActionsPanel` физически лежит в общей компонентной части 8, поэтому в
разбивке относится к общим/domain-мастерам, хотя доменное имя headman.

### Teacher / admin / auth — 0

Страницы блоков 13, 14 и 15 содержат соответственно 21, 51 и 11
component-root; нарушений высотной оси по этому правилу нет.

## Механизм

`SceneNode.resize(width, height)` после вызова устанавливает
`primaryAxisSizingMode=FIXED` и `counterAxisSizingMode=FIXED`. Если код сначала
выставил HUG, а затем поменял размер, HUG молча теряется. Пиксельная высота при
этом может остаться правильной, поэтому обычный замер не обнаруживает дефект.

Это механизм, из-за которого исправленный `TrendPanel · 113` вернулся в
FIXED после поздней перестройки графика. Правило и обязательный closing-check
записаны в части 15 `figma-spec`.

## HomeworkToday и HomeworkList

| Узел | Ветки | overflow | clips | Потребители |
|---|---:|---|---|---|
| `block/HomeworkToday` root | 3 | NONE | false | 2 QA + 1 продуктовый block-instance |
| вложенный `HomeworkList lesson-order/inner` | 3 instance | VERTICAL | true | по одному в каждой ширине HomeworkToday |
| source `HomeworkList lesson-order/inner` | 1 variant | VERTICAL | true | 3 прямых физических, все headman; student 0 |

Весь набор `homework/HomeworkList` содержит 12 вариантов и 23 прямых
физических экземпляра: student-блоки 12, headman-блоки 9, компонентная QA 2.
На странице 17 видны 18 вхождений: 6 на 101 и 12 на двух состояниях 104.

Отдельная находка: шесть student-кадров 101 используют **другую** ветку
`groupBy=none, order=completion-last, scroll=inner`, также с VERTICAL. Она не
будет затронута точечным ремонтом `lesson-order/inner` и не входит в принятые
18/41. Страница 17 остаётся нетронутой; вопрос записан владельцу отдельно.

После разрешённого будущего ремонта `lesson-order/inner` всё равно требуется
перезамер всех 48 student-кадров, несмотря на 0 её student-потребителей.

## Геометрия и защищённые страницы

- Figma-записей: 0.
- Изменение геометрии мастерных корней: 0 px.
- Страницы 16 и 17: только чтение; смещений 0.
- HomeworkToday и 14 найденных FIXED-корней не исправлялись.
- Публикация не выполнялась.

## Что не сделано и почему

- 14 найденных корней не переведены в HUG: владелец потребовал сначала
  показать число.
- HomeworkToday/HomeworkList не исправлены по той же последовательности.
- Student-вариант `completion-last/inner` не менялся: страницы 16 и 17
  защищены и он не входит в headman-объём 18/41.

## Изменённые файлы

- `design/figma-spec.md:9770` — датированное правило осей после resize.
- `journal/DECISIONS.md:685` — решение об аудите и принятый объём HomeworkToday.
- `journal/to-owner.md:214` — отдельный хвост student HomeworkList.
- `journal/state/headman-adaptive-repair-2026-08-24.json:1` — состояние партии.
- `journal/reports/2026-08-24-part-15-master-root-axis-audit.md:1` — этот отчёт.
