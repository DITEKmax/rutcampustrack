# Part 19 — Headman mobile: полная ревизия dark-bank

Дата: 02.09.2026  
Figma: `VgVjQYWILLG9AC7Eh12VMk`  
Страница: `25 · Mobile · Headman`, `4996:142`  
Статус: все прямые визуальные правки владельца выполнены; банк остановлен для
визуальной валидации.

## Ответы на вопросы

1. Все ли пункты правок владельца сделаны? **Да.** Обновлены Today/Учёт,
   Статистика, Домашнее задание, Предметы, Конструктор расписания,
   Управление парами, Заявки, Ещё, Профиль и Настройки. Карта и Группа
   сохранены без функционального расширения, так как новых правок к ним не
   было.
2. Можно ли валидировать всю картину и переходы? **Да.** На page 25 находится
   42 кадра 390×844: семейства идут вертикально, состояния и переходы одного
   семейства — горизонтально.
3. Изменялись ли защищённые страницы или общие мастера? **Нет.** Pages 16–24:
   `0 A` и `0 geometry changes`; общие masters, tokens и library publication
   не затрагивались.

## Разбор до записи

### Что было непонятно

- серверные определения `В ожидании` и знаменателя `успешных по гео`;
- row revision/idempotency для немедленного сохранения посещаемости;
- payload и ticket-связь auto-accepted `у`;
- право и endpoint lock/unlock пары;
- агрегаты статистики и границы редактирования истории;
- assignment ID / TeacherReadout, transfer/cancel cascade и ticket receipt.

Эти вопросы блокируют production states отдельных действий, но не статический
dark-bank для визуальной приёмки.

### Что противоречило прежним решениям

Комментарий владельца отменил ранее принятый probe
`roster → focus-deck → review → commit`: focus/review и общий commit удалены,
статус ставится индивидуально в строке. Решение записано явно в
`journal/DECISIONS.md`; массовая отметка и Checkbox не возвращались.

### Числа документов и живого файла

- baseline page 25: `960 A`, `128 instances`, `496 text`, `336 frames`;
- baseline board: `2150×9908 px`, `11` family-hosts, `26` экранов;
- final page 25: `2215 A`, `166 instances`, `1176 text`, `737 frames`;
- final board: `2978×9012 px`, `10` family-hosts, `42` экрана.

Расхождение объясняется единицами: число экранов — физические кадры
390×844; `A` — все физические узлы без входа внутрь instances. Attendance-host
удалён как отдельный ряд и объединён с Today в `screens/20–21`, поэтому
family-hosts стало на один меньше при росте screen count.

### Что переиспользовано

- визуальный язык и mobile anatomy страницы 24 Student;
- student Profile и Settings;
- принятые bottom nav, Back, sticky actions и role switcher;
- standard lesson metadata и существующие semantic icons;
- точный graph donor `4595:422`;
- существующие color, type, spacing, radius и status variables.

## Совет: функциональные карты

| Семья | Данные | Действия и права | Состояния / зависимости |
|---|---|---|---|
| 19 Ещё | routes, роль, профиль | открыть раздел, профиль, настройки | server role/permissions |
| 20–21 День и Учёт | дата, пары, roster, метрики, причины, ДЗ, события | сменить день/пару; поставить `+ / у / н`; lock; открыть ticket/ДЗ/log | row saving/error/stale; `у` требует reason |
| 22 Статистика | pure, with excuses, today, student/subject aggregates, history | lens/mode, раскрытие студента, inline status clear/change | aggregate contract, edit right, stale |
| 23 ДЗ | предмет, тип, допустимые даты, title/body/links | create/edit/publish | schedule dates, arbitrary-date flag, validation |
| 24 Карта | корпус, этаж, floor asset | read-only выбор | honest empty без floor asset |
| 25 Группа | 30 members, роли, permissions | поиск, student detail, permissions editor | sensitive-data/effective rights |
| 26 Предметы | subject, type, lesson count, teacher assignment | add teacher, save | teacher search, assignment ID/revision |
| 27 Конструктор | weekday, parity, slot, assignment, room | add first/second-week/new lesson | slot dictionary, parity mapping, conflicts |
| 28 Пары | dated agenda, parity, active/cancelled lesson | add-mode, room change, transfer, cancel | revision, conflict, cascade |
| 29 Заявки | queues, lesson groups, reason, attachment, archive | detail, approve/reject | fresh applicable count, irreversible receipt |

## Варианты и критика

Архитектор предложил три IA:

1. два root (`Сегодня` и `Учёт`) в одной day-context семье — выбран;
2. один длинный day workspace — отклонён из-за неоднозначного selected nav и
   слишком длинного root;
3. task-first decomposition — отклонён из-за 60+ кадров и потери общей
   картины.

Независимый critic сначала дал NO-GO. Исправлены находки, не конфликтующие с
комментарием владельца: подпись geo%, `27 активных из 30`, неподтверждённое
удаление предмета, отсутствующий TeacherReadout, необратимость reject и
последний видимый перенос заголовка статистики. Не применены предложения,
противоречащие прямому решению владельца: возврат focus/review, удаление
reason-picker, слияние двух root-route, изменение точного graph donor,
возврат удалённого warning/cascade-текста и отказ от lesson breakdown заявки.

## Выбранное решение

Два честных root сохраняют понятное владение навигацией, но используют общий
контекст даты и выбранной пары. На канве это один горизонтальный ряд
`screens/20–21`. Остальные семейства следуют схеме
`root → context/state → detail → editor/task`. Это даёт владельцу полную карту
переходов без размножения platform/state cross-product до визуальной приёмки.

### Tap budget

- Today → выбранная пара → индивидуальный статус: `2` тапа;
- из Учёта → индивидуальный статус: `1` тап;
- статус `у` с выбором причины: `3 / 2` тапа соответственно;
- календарь → другой день: `2` тапа;
- соседняя пара стрелкой: `1` тап;
- уважительная причина → ticket: `1` тап;
- Stats root → student detail: `1` тап после входа в раздел;
- Requests queue → detail → approve: `3` тапа после входа;
- Requests queue → detail → reject: `3` тапа плюс ввод причины.

## Что удалено с mobile

- CTA «Открыть журнал», progress `21/27` и snapshot/revision в hero Today;
- focus deck, review-commit, общий draft/commit и attendance undo-draft;
- duplicated status summary на roster;
- «Нужно внимание» в статистике;
- комментарий и файл при ручном `у`;
- неподтверждённый «Удалить предмет»;
- redundant warnings и вторичные consequence-блоки, которые владелец попросил
  убрать;
- current-snapshot block в Request detail;
- нижние описания строк в `Ещё`;
- bulk actions и Checkbox.

## Иконки

Новых semantic icons создано: `0`. Переиспользованы существующие значения
системы: Back/chevron, Calendar, Attendance/status circles, Tickets/message,
More, Profile, Search, Time, Room, Lesson type, Homework/clipboard,
History/undo, Lock/state, Teacher/person, Subject/list и external-link.
Один смысл не переиспользовался для другого; metadata-глифы сохраняют
принятый паттерн 10×10 px, stroke 1 px, gap 4 px.

## Состав и A каждого кадра

| Семья | Кадр | A |
|---|---|---:|
| 19 | Ещё / headman | 40 |
| 19 | Профиль / headman | 25 |
| 19 | Настройки / headman | 39 |
| 20–21 | Сегодня / default | 74 |
| 20–21 | Учёт / roster-list | 404 |
| 20–21 | Сегодня / other-lesson | 74 |
| 20–21 | Учёт / reason-picker | 31 |
| 20–21 | Учёт / excuse-ticket | 33 |
| 20–21 | Учёт / action-log | 46 |
| 22 | Статистика / individual-list | 49 |
| 22 | Статистика / individual-expanded | 57 |
| 22 | Статистика / by-subject | 69 |
| 22 | Статистика / student-history | 102 |
| 22 | Статистика / status-cleared | 46 |
| 23 | Домашнее задание / calendar | 51 |
| 23 | Домашнее задание / detail | 16 |
| 23 | Домашнее задание / create-subject | 22 |
| 23 | Домашнее задание / create-type | 27 |
| 23 | Домашнее задание / create-calendar-schedule | 97 |
| 23 | Домашнее задание / create-calendar-any | 97 |
| 23 | Домашнее задание / create-content | 26 |
| 24 | Карта / default | 6 |
| 25 | Группа / root | 128 |
| 25 | Группа / student-detail | 14 |
| 25 | Группа / permissions | 26 |
| 26 | Предметы / root | 52 |
| 26 | Предметы / editor | 34 |
| 26 | Предметы / add-teacher | 48 |
| 27 | Конструктор / root-empty | 25 |
| 27 | Конструктор / slot-editor | 49 |
| 27 | Конструктор / root-filled | 30 |
| 28 | Управление парами / agenda | 60 |
| 28 | Управление парами / active-detail | 25 |
| 28 | Управление парами / cancelled-detail | 17 |
| 28 | Управление парами / transfer | 31 |
| 28 | Управление парами / cancel-confirm | 16 |
| 28 | Управление парами / agenda-add-mode | 45 |
| 29 | Заявки / queue-by-lesson | 54 |
| 29 | Заявки / detail | 27 |
| 29 | Заявки / approve-confirm | 31 |
| 29 | Заявки / reject-confirm | 17 |
| 29 | Заявки / archive | 40 |

Family host A: `105 / 663 / 324 / 337 / 7 / 169 / 135 / 105 / 195 /
170`; total page 25 — `2215 A`.

## Геометрическая дельта

- page 25: `960 → 2215 A`, дельта `+1255 A`;
- instances: `128 → 166`, дельта `+38`;
- text: `496 → 1176`, дельта `+680`;
- frames: `336 → 737`, дельта `+401`;
- board: `2150×9908 → 2978×9012 px` (`+828 px` по ширине,
  `−896 px` по высоте);
- family-hosts: `11 → 10`; screens: `26 → 42`;
- финальный geometry hash page 25: `e7dcd894`.

Общие masters: `0` тронутых; consumer count до/после неприменим. Геометрия
pages 16–24 совпала с baseline по независимому обходу.

## QA числами

- экраны: `42`;
- Inter: `0`; missing fonts: `0`;
- shadows: `0`; disallowed glow/blur: `0`;
- visible ellipsis: `0`; direct overflow: `0`;
- targets `<44×44`: `0`;
- double Back / double bottom panel: `0`;
- bottom inset violations: `0`;
- статусы, читаемые только цветом: `0`;
- защищённые pages 16–24: `6412 A`, `2190 instances`, delta `0 A`,
  geometry changes `0`.

Ручной render-pass поймал один перенос, не найденный машинным QA:
`Группа · 27 активных из 30`. После исправления heading 212×20 px, поле
поиска начинается через 8 px; повторный render чистый. Deep clip в roster и
Group соответствует намеренному list viewport; прямого выхода ребёнка за
clipping-parent нет.

## Токены и литералы

Проверены места и найденные соответствия:

- фон и поверхности: существующие `surface/base`, `surface/raised`,
  `surface/float`;
- текст: `text/primary`, `secondary`, `muted`, `disabled`;
- границы: `border/default`, `border/strong`;
- акцент: `accent/now`, `accent/soft`, `on-accent`;
- semantic status: существующие success/warning/danger и attendance
  `+ / у / н / no-status`;
- типографика: Onest; spacing/radius — существующая мобильная шкала.

Token requests: `0`; временные fallback и новые local hex не вводились.
Общие masters не менялись, поэтому пара `master literals / QA literals`
остаётся `0 / 0` для этой партии; привязок «не по смыслу» в изменённых
кадрах closing pass не нашёл.

## Platform ownership

- root PWA/TMA: product bottom nav; native Telegram Back/MainButton скрыты;
- PWA detail/task/editor: один product Back, один sticky action при
  единственном save/publish/confirm;
- TMA detail/task/editor: product Back/sticky скрываются, владельцы — native
  BackButton/MainButton;
- keyboard: bottom nav скрывается, focused field/CTA поднимаются над visual
  viewport;
- safe area: top platform inset и bottom `max(16px,
  env(safe-area-inset-bottom))`.

Текущие 42 кадра показывают canonical PWA standalone 390×844; TMA и keyboard
не выдаются за уже доказанные render-states.

## MCP и transport ledger

С момента сообщения владельца с правками:

- `whoami`: `0` повторов; один вызов был выполнен в начале общей headman
  сессии;
- `use_figma`: `147` вызовов — `56` mutation attempts и `91` read/readback;
- `get_screenshot`: `60` последовательных рендеров;
- `429`: `0`;
- timeout / transport closed: `7`; каждый закрыт отдельным readback,
  повтор вслепую не выполнялся;
- safe atomic runtime reject: `1`, записанных узлов `0`;
- partial commits: `0`.

## Baseline до и после

- before: `journal/state/part-19-headman-mobile-revision-before-2026-09-02.json`;
- after: `journal/state/part-19-headman-mobile-revision-after-2026-09-02.json`;
- expected protected delta: `0 A / 0 geometry changes`;
- actual protected delta: `0 A / 0 geometry changes`.

## Рендеры

- `part-19-contact-19-21.png`;
- `part-19-contact-22.png`;
- `part-19-contact-23.png`;
- `part-19-contact-24-25.png`;
- `part-19-contact-26-27.png`;
- `part-19-contact-28.png`;
- `part-19-contact-29.png`.

Отдельные PNG каждого изменённого кадра находятся в `_work/renders/` с
префиксом `part-19-`.

## Открытые вопросы

Открыты только production contracts: row revision/idempotency и rollback,
семантика geo/waiting, payload auto-accepted `у`, lock/log APIs, статистические
агрегаты, homework linkage, assignment/slot contracts, transfer/cancel cascade
и ticket receipt. Полный список append-only записан в `journal/to-owner.md`.

## Что не сделано и почему

- 360/430/320, PWA browser, TMA, keyboard, loading/empty/error,
  offline/stale/conflict, long-data, increased contrast и light theme —
  отложены до визуальной приёмки 390-dark банка;
- post-submit success/ACK screens — нет подтверждённых server contracts;
- общие masters, tokens и library publication — не были разрешены и не
  требовались для текущих screen-level решений;
- массовая отметка и Checkbox — запрещены владельцем.

## Изменённые файлы

- `_work/prompts/part-19-headman-mobile-revision.md:1` — сохранённый prompt;
- `design/figma-spec-mobile.md:450` — актуальные mobile-правила ревизии;
- `journal/DECISIONS.md:2412` — отмена прежнего staged attendance decision;
- `journal/to-owner.md:847` — обновлённый пакет contract-вопросов;
- `journal/state/part-19-headman-mobile-revision-before-2026-09-02.json:1` —
  baseline;
- `journal/state/part-19-headman-mobile-revision-after-2026-09-02.json:1` —
  closing snapshot;
- `journal/reports/2026-09-02-part-19-headman-mobile-revision.md:1` — этот
  отчёт;
- `_work/renders/part-19-*.png` — отдельные кадры и contact sheets; номера
  строк неприменимы к PNG.

`design/COMPONENT_REGISTRY.md`, `design/brandbook-v2.md` и
`design/tokens-v2.json` не изменялись.
