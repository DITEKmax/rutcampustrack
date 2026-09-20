# Part 25 — точечные owner corrections Admin mobile

Дата: 05.09.2026. Figma: `design-system/ui-kit-rct`, file key
`VgVjQYWILLG9AC7Eh12VMk`.

## Ответы на вопросы

Прямых вопросов владельцу в этой партии не возникло. Все девять указаний
однозначно сопоставились с живыми узлами. Для пункта 7 принят явно заданный
контракт: у человека может быть несколько ролей; добавление доступно в том же
редакторе, а недоступное удаление остаётся видимым вместе с причиной. Для
пункта 9 создан отдельный семантический `icon/semester`, а не графический
вариант существующего `icon/date`.

## Разбор и стоп-ворота

- Новое сообщение владельца сохранено как отдельный Part 25 после закрытого
  Part 24. Оно не переоткрывает Teacher: эта партия затрагивает только Admin
  page 26 и два объявленных icon-card на page 21.
- Pause-checkpoint Part 24 описывал Admin как **535 A** / `0e40ad88`, тогда
  как два независимых стартовых чтения живого файла Part 25 устойчиво дали
  **532 A** / `091a502c`. Расхождение составляет **−3 A**; обе величины — одна
  и та же единица A страницы 26, PAGE исключён, внутрь instances обход не
  выполнялся. Admin-write был остановлен на повторную разведку. Все целевые
  frame/node id сохранились, геометрия board оставалась 908×9908 px, поэтому
  новая партия была перебазирована на устойчивый живой baseline; чужое
  состояние не откатывалось.
- Ссылки владельца `5901:472` и `5896:470` ведут в структуры option. Реальные
  editable carriers внутри них — `5901:475` и `5896:471`; исправлялся carrier,
  а не соседний текстовый child.
- «Прямоугольник» динамики трактуется как компактный semantic badge, не как
  новый публичный master. Общий `shared/MetricDelta` и его потребители не
  менялись.
- «Копировать нажатием на само поле» реализовано whole-field carrier для
  login/password. Вложенная иконка декоративна; production-событие принадлежит
  всему полю.
- Login и initial password отсутствуют в pre-ACK create-формах и появляются
  только в post-ACK receipt. Конкретные права администратора не выдуманы:
  макет показывает server-driven действие `Выбрать права`.
- Для календарей найден существующий `homework/MonthCalendar` `640:2`.
  Новый calendar master не создавался и общий master не менялся. Экранная
  адаптация 1,1 доводит day target 40×40 до 44×44; compact month arrows
  заменены локальными carriers 44×44.
- На page 21 подходящих семантических masters `copy` и `semester` не было.
  Создание двух новых иконок — прямое следствие пунктов 6 и 9 владельца и
  отдельное дополнение закрытого component registry.
- Блокирующих противоречий owner brief, живого файла и действующих документов
  после этой разведки: 0.

## Живой итог и устройство банка

Page 26: **532 → 668 A**, canonical geometry hash
`091a502c → f6e9fe24`. Board: **908×9908 → 908×11700 px**.

- было 19 product-кадров + 5 family labels = 24 direct children board;
- стало 23 product-кадра + 5 family labels = 28 direct children board;
- все 23 product-кадра имеют 390×844 px;
- 9 root-кадров: ровно один bottom dock, product Back = 0;
- 14 task/detail/editor/ACK-кадров: ровно один product Back, dock = 0;
- invalid ownership = 0.

Четыре новых экранных состояния:

| Кадр | A | Позиция, px | Навигация |
|---|---:|---:|---|
| `6014:592` · создать студента | 22 | 52, 4532 | Back |
| `6014:617` · создать администратора | 22 | 466, 4532 | Back |
| `6019:661` · добавить семестр, календарь начала открыт | 30 | 466, 9012 | Back |
| `6020:1258` · редактировать семестр, календарь окончания открыт | 30 | 52, 9908 | Back |

Сумма A всех 23 product-кадров — 662 A. Board + пять family labels дают
финальные 668 A страницы.

## Выполнение девяти правок

1. На `5646:142` у всех KPI category carrier занимает центрированную область
   карточки, value стоит справа сверху, а существующая динамика — ниже числа
   только стрелкой и числом. Старые пояснения периода удалены.
2. На `5901:452` применена та же анатомия. У activity-карточек `Всё время`
   суточной динамики нет; у верхних system-карточек сохранена доступная
   динамика.
3. Selected `Всё время` `5901:475` приведён к donor `5896:471`: 175×44 px,
   `radius/full`, `color/accent/now`, label `color/accent/on-now`.
   Unselected `Сегодня` имеет осознанный `radius/none`.
4. На user detail `5694:266` identity `5906:493` содержит только ФИО.
   В `5906:498` собраны роль, статус роли, статус аккаунта, табельный номер и
   Telegram ID. Секция `Данные для входа` содержит login/password рядом;
   carriers `6013:590` и `6013:591` по 175×76 px копируются целиком.
5. Существующий Teacher create `5908:486` сохранён. Добавлены самостоятельные
   Student и Admin examples. Student содержит ФИО, группу `ИКБО-01-24` и
   Telegram ID. Admin содержит ФИО, server-driven `Права доступа` и Telegram
   ID. Ни одна create-форма не принимает login/password.
6. На `5909:488` check, server acknowledgement и identity объединены в один
   success-carrier `5909:513` 358×132 px. Он целиком использует
   `state/success/bg + state/success/border`. Текст `Скопировать` удалён;
   login/password имеют icon-only copy controls `6016:600`, `6016:604`
   размером 44×44 px.
7. На `5910:490` показаны две роли. У Teacher remove `6017:606` недоступен,
   причина видима: `Нельзя удалить: есть активные занятия`. У Admin remove
   `6017:612` доступен. `Добавить роль` — `6017:614`. Сохранение и role
   commands не показаны optimistic: результат подтверждает сервер.
8. Добавлены два calendar-open state. Add показывает сентябрь 2026 и выбранное
   начало 01.09.2026; edit — январь 2027 и выбранное окончание 31.01.2027.
   В каждом состоянии 42 day carrier не меньше 44×44 px и два month-nav
   carrier 44×44 px. Активное поле и выбранная дата названы текстом, поэтому
   состояние не кодируется одним цветом.
9. В `Ещё → Семестры` instance `5730:457` свопнут с generic `icon/date` на
   новый semantic `icon/semester` `6008:152`.

## Геометрические изменения

Все значения — px с живого файла.

- Board height: 9908 → 11700; width осталась 908.
- KPI top category carrier: width 180 → 334; activity category carrier:
  width 230 → 334. Центр каждого итогового carrier x=179 совпадает с центром
  карточки x=179.
- KPI top value: x210/w136 → x244/w102; activity value:
  x250/w96 → x244/w102.
- Старый delta text-carrier шириной 334 заменён badges 33–61×18,
  x285–313 в зависимости от числа.
- Detail identity-card: height 96 → 72; data-card: 196 → 220;
  credential fields созданы как 175×76 в один ряд.
- Post-ACK mark 44×44 заменён единым success-block 358×132;
  copy controls — 44×44.
- MonthCalendar master номинально 320×336; screen instance после scale 1,1 —
  352×370,4. Day targets: 40×40 → 44×44. Локальные month controls: 44×44.
- Family `Группы`: label y4500→5396; три ряда экранов начинаются с
  y5428/6324/7220 вместо 4532/5428/6324.
- Family `Семестры`: label y7188→8084; list/add y7220→8116;
  edit y8116→9012.
- Family `Ещё`: label y8980→10772; More/Profile y9012→10804.
- Размер существующих product-кадров не менялся: 390×844. Переполнений за
  границы экрана после перестановки: 0.

## Masters и потребители

Consumer-count ниже — прямые физические instances, это не единица A.

| Master | До | После | Причина |
|---|---:|---:|---|
| `icon/copy` `6008:143` | 0 | 4 | detail login/password + post-ACK login/password |
| `icon/semester` `6008:152` | 0 | 1 | `Ещё → Семестры` |
| `homework/MonthCalendar` `640:2` | 25 | 27 | два calendar-open state |
| `icon/date` | 13 | 12 | один semantic swap на semester |
| `icon/previous` | 46 | 52 | четыре новых Back + два month-prev controls |
| `icon/next` | 80 | 82 | два month-next controls |
| `icon/chevron-down` | 256 | 260 | role/select affordances двух create examples |

У `icon/previous` и `icon/next` развёрнутые `getInstancesAsync()` references
дают соответственно 71 и 101; эти числа включают вложенные ссылки и единицей
A/прямым consumer-count не являются.

Shared-master geometry writes = 0. `MonthCalendar`, AppShell, AppHeader,
MobileBottomNav, fields, buttons и `shared/MetricDelta` не менялись. Поэтому
обязательный auth/student remeasure из правила shared-master write не
активировался; pages 16/17 всё равно проверены closing checksum.

Page 21 изменена с 142 A / `0ce38243` до 155 A / `524086c2`: объявленная
дельта +13 A состоит только из двух icon-card. Независимая проекция прежних
узлов после добавления остаётся ровно 142 A / `0ce38243`.

## Литералы, variables и смысловые привязки

Проверка выполнена отдельно от mutation-pass.

| Область | Master | Экранный QA |
|---|---|---|
| Новые icons | 16×16; все solid paints привязаны к `color/text/primary`; literal radius 0, literal border thickness 0 | 5 instances; unbound solid paints/strokes 0 |
| Selected period | общий master не менялся | 175×44; четыре угла `radius/full` = 999 px; fill `accent/now`, label `accent/on-now` |
| Success result | общий master не создавался | `success/bg`, `success/border`; border `hairline` = 1 px; radius token-bound, нового literal gap 0 |
| Calendar | `MonthCalendar` 320×336, DayNumber 40×40; без master write | scale 1,1; instance 352×370,4, days/month controls ≥44 px; новых literal radius/border gaps 0 |

- Product texts: 462; Onest: 462; Inter: 0; missing fonts: 0.
- Unbound local solid fills/strokes: 0; gradients: 0; visible effects: 0.
- Collapsed text: 0; outside-screen geometry: 0; touch targets <44 px: 0.
- Старые visible delta phrases `за сутки`, `к этому времени вчера`,
  `без изменений`: 0.
- Новые literal radius gaps: 0.
- Четыре прежних Profile rows `5737:5785`, `5737:5788`, `5737:5791`,
  `5737:5794` сохраняют inherited literal radius 18 px: точного semantic
  token нет, Part 25 их не менял.
- Проверка «привязки не по смыслу»: selected period использует accent state,
  success receipt — success state, icon glyphs — text-primary, semester —
  отдельный semantic master. Неверных смысловых bindings в изменённых узлах: 0.
- Созданных variables/tokens: 0.

## Защищённые страницы

Closing checksum сделан независимым обходом:

| Page | A | Hash | Результат |
|---:|---:|---|---|
| 16 | 890 | `4ca4a8f4` | два одинаковых чтения; совпадает с pause transient state, writes 0 |
| 17 | 748 | `5bc52169` | совпадает с checkpoint |
| 18 | 836 | `0acf793c` | совпадает с checkpoint |
| 19 | 363 | `96e171a8` | совпадает с checkpoint |
| 20 | 286 | `401f7db5` | совпадает с checkpoint |
| 21 | 155 | `524086c2` | только два объявленных icon-card; existing projection совпала |
| 22 | 229 | `dea5888a` | совпадает с checkpoint |
| 23 | 796 | `a83b3f15` | совпадает с checkpoint |
| 24 | 2139 | `d4743e24` | совпадает с checkpoint |
| 25 | 2611 | `74980519` | совпадает с pause checkpoint |
| 27 Teacher | 361 | `0e9802c5` | совпадает с closing Part 24 |

Page 16 имеет документированный исторический hash `455cd388`, но текущий
pause-checkpoint уже содержал `4ca4a8f4`. Два closing чтения подтвердили
именно текущий transient state; Part 25 page 16 не адресовал.

## Рендеры и независимые gates

- Свежие individual renders: 23/23, каждый файл непустой.
- Contact sheet: `_work/renders/part-25/admin/contact-sheet.png`.
- Component cards: `icon-copy-card.png`, `icon-semester-card.png`.
- Functional analyst: GO, 9/9 требований, false backend claims 0.
- Mobile architect: GO, mobile-architecture blockers 0.
- Critic/platform QA: GO, blockers 0, polish defects 0.

## Что не сделано и почему

- Библиотека не публиковалась: публикацию выполняет владелец.
- Shared masters не менялись: все screen corrections локальны; календарь
  переиспользован instance-level.
- Не создавались новые tokens/variables. Проверены semantic bindings; два
  отсутствовавших смысла закрыты icon components по прямому решению владельца.
- Четыре inherited Profile radius 18 px не подгонялись к соседнему token:
  точного token нет и решения на его создание не было.
- Не проектировались light, 320/360/430, keyboard и полные
  loading/empty/error/offline/stale/conflict states: они не входили в девять
  точечных правок.
- Не объявлены готовыми backend contracts permissions catalogue, credential
  TTL/security, role-command revision/conflict и semester overlap/save. Они
  отдельно записаны в `journal/to-owner.md`.
- Teacher в Part 25 не менялся; его page 27 только проверена checksum.
- `specs/` не редактировались: они принадлежат владельцу.

## Изменённые файлы

Финальные номера строк приведены отдельным line-index ниже.

- `design/COMPONENT_REGISTRY.md` — отдельное дополнение закрытого реестра;
- `design/figma-spec-mobile.md` — контракт Part 25;
- `journal/DECISIONS.md` — решение владельца;
- `journal/to-owner.md` — backend/runtime gaps;
- `_work/prompts/part-25-admin-mobile-owner-corrections-2026-09-05.md`;
- `journal/state/part-25-admin-mobile-owner-corrections-2026-09-05.json`;
- `journal/state/part-25-admin-mobile-owner-corrections-after-2026-09-05.json`;
- `_work/scripts/make-admin-part25-contact-sheet.py`;
- этот отчёт;
- 23 individual PNG, contact-sheet.png и две component-card PNG в
  `_work/renders/part-25/admin/` — binary, номера строк неприменимы.

## Финальный line-index

<!-- LINE_INDEX -->

- `design/COMPONENT_REGISTRY.md`: строки 1392–1408;
- `design/figma-spec-mobile.md`: строки 598–649;
- `journal/DECISIONS.md`: строки 2504–2530;
- `journal/to-owner.md`: строки 1030–1058;
- `_work/prompts/part-25-admin-mobile-owner-corrections-2026-09-05.md`:
  строки 1–34;
- `journal/state/part-25-admin-mobile-owner-corrections-2026-09-05.json`:
  строки 1–46;
- `journal/state/part-25-admin-mobile-owner-corrections-after-2026-09-05.json`:
  строки 1–107;
- `_work/scripts/make-admin-part25-contact-sheet.py`: строки 1–41;
- `journal/reports/2026-09-05-part-25-admin-mobile-owner-corrections.md`:
  стабильная ссылка на строку 1; самоссылочный номер последней строки не
  фиксируется;
- PNG и contact sheet: binary, номера строк неприменимы.
