# Part 17 — итоговая ревизия student mobile 10–19

Дата: 02.09.2026. Figma file: `VgVjQYWILLG9AC7Eh12VMk`.
Страница: `24. Экраны — mobile student` `4379:429798`.
Финальная доска: `4572:3062`.

## Ответы на вопросы и замечания владельца

1. «Пропускаю по уважительной» не удалена и не оставлена подписью. На 10 это
   кнопка `4585:848231`, на 12 — `4593:848632`; обе 272×44 px, radius 22 px,
   neutral fill, `border/default` 1 px. Соседние «Забыл отметиться» имеют ту же
   геометрию и материал.
2. После выбора уважительной причины на 12 форма раскрывается inline под
   выбранной парой: новый state `4710:232`. Это не новый route и не экран 11.
3. Только metadata-иконки внутри карточек пар уменьшены до 8×8 px с gap 4 px.
   Graph buttons, nav, ticket meta и другие иконки не затронуты.
4. Status markers 8×8 применены только в type-карточках статистики.
5. Bottom nav приподнят: `x=16, y=752, 358×76`, нижний inset 16 px.
6. Под экранами одна непрерывная `surface/raised`; отдельных borders и fixture
   wrappers нет.
7. Верхние screen titles сохранены, кроме прямо отменённого «Сегодня» на 10.
   Role pill на 10 центрирован и использует градиент; current lesson использует
   solid `accent/now`.

## Разбор партии

- Поздние комментарии владельца отменяют прежний банк из 13 кадров в части
  отдельных экранов 11 и 15.
- Числа живого файла после сборки: 32 финальных кадра, а не прежние 13.
- Page-21 masters остаются 16×16 px со stroke 1 px. Правило 8×8 относится к
  конечным экземплярам внутри lesson cards, поэтому числа 16 и 8 описывают
  разные единицы и не противоречат друг другу.
- Пять выходов type-pill за верхнюю грань родителя оставлены намеренно: это
  требуемая компоновка, где линия карточки проходит посередине pill.
- `student/MobileHomeworkDetail` `4455:823` физически существует, но имеет
  0 consumers. Он записан как legacy/unconsumed и не удалён молча.

## Финальная карта экранов

| Семейство | Состояния | Количество |
|---|---|---:|
| 10 | default, geo failure, marked, absence actions, excuse form, pending | 6 |
| 12 | days, empty day, absence actions, graph days, graph weeks, subjects, inline request | 7 |
| 13 | confirmation form | 1 |
| 14 | feed, expanded inline, completed multiple | 3 |
| 16 | overview, subject expanded | 2 |
| 17 | campus map | 1 |
| 18 | open, archive, new request, excuse form, missed check-in | 5 |
| 19 | more, profile, role, appearance, security, sessions, history | 7 |
| **Итого** | screen 11 = 0; screen 15 = 0 | **32** |

## A по каждому финальному кадру

Единица A: физический узел дерева без входа внутрь instance; PAGE исключён.

| ID | Кадр | A | Instances A |
|---|---|---:|---:|
| `4581:3062` | 10 / default | 63 | 10 |
| `4585:218` | 10 / geo failure | 62 | 10 |
| `4585:848020` | 10 / marked | 63 | 10 |
| `4585:848126` | 10 / absence actions | 53 | 8 |
| `4588:527` | 10 / excuse form | 43 | 8 |
| `4588:848409` | 10 / pending | 53 | 9 |
| `4593:142` | 12 / days | 92 | 8 |
| `4593:848365` | 12 / empty day | 49 | 2 |
| `4593:848496` | 12 / absence actions | 82 | 6 |
| `4595:293` | 12 / graph days | 53 | 2 |
| `4595:848430` | 12 / graph weeks | 53 | 2 |
| `4596:365` | 12 / subjects | 96 | 2 |
| `4710:232` | 12 / inline request | 32 | 6 |
| `4597:3574` | 13 / form | 32 | 7 |
| `4601:142` | 14 / feed | 27 | 5 |
| `4601:848562` | 14 / expanded inline | 20 | 3 |
| `4601:848636` | 14 / completed multiple | 27 | 5 |
| `4603:142` | 16 / overview | 51 | 1 |
| `4603:848696` | 16 / subject expanded | 66 | 1 |
| `4603:848832` | 17 / campus map | 2 | 2 |
| `4610:142` | 18 / open | 46 | 9 |
| `4610:848846` | 18 / archive | 45 | 9 |
| `4610:848937` | 18 / new request | 17 | 1 |
| `4610:849007` | 18 / excuse form | 42 | 10 |
| `4610:849072` | 18 / missed check-in | 29 | 4 |
| `4611:142` | 19 / more | 12 | 4 |
| `4611:848928` | 19 / profile | 19 | 1 |
| `4611:849055` | 19 / role | 16 | 1 |
| `4614:276` | 19 / appearance | 38 | 1 |
| `4615:326` | 19 / security | 59 | 14 |
| `4618:535` | 19 / sessions | 17 | 1 |
| `4618:849228` | 19 / account history | 24 | 1 |

Сумма содержимого 32 screen frames: **1383 A**, из них **163 instance A**.

## Что изменилось геометрически

- Root bottom nav: прежнее прилегание к нижней границе заменено inset 16 px;
  финальная геометрия 23 root-кадров — `x16 / y752 / 358×76`.
- Lesson metadata instances: 16×16 → 8×8 px; соседний gap 8 → 4 px. Финальный
  scope — 66 экземпляров, 66/66 прошли readback; inner vector stroke 1 px.
- Type statistics cards: 342×100 px; три status rows собраны в strip 314×18 px,
  девять markers имеют 8×8 px.
- Period controls графиков и интерактивные сегменты доведены до высоты 44 px.
- Inline request 12 добавлен как самостоятельный статичный state 390×844;
  submit заканчивается на y683, nav начинается на y752.
- Общие grids: padding 12 px, horizontal/counter gap 24 px, radius 24 px.

Page-21: 73 → 89 A из-за четырёх новых системных icon-компонентов. Page-22:
229 → 229 A. Page-23: 772 → 784 A. Page-24: 306 → 1742 A.

## Закрывающие проверки

- 32/32 screen frames имеют 390×844 px.
- Выходы за screen bounds: **0** по обеим осям.
- Выходы за parent: **5**, все — намеренные type-pill `y=-14` на type cards;
  иных выходов 0.
- Touch targets ниже 44 px среди действий, tabs, Back, selects, submit,
  toggles и triggers: **0**.
- Root nav: **23/23 PASS**; ровно один selected item, background
  `accent/now`, label+icon `accent/on-now`.
- Detail без nav: **9/9 PASS**; двойного nav и двойного Back нет.
- Missing fonts: **0**. Literal `Inter`: **0**. Используется Onest.
- Запрещённые видимые тексты: **0**: нет «Сейчас · до», «аудитория изменена»,
  «Не был», «Просрочено», «Открыть корпус в картах», ticket number и Telegram
  tag. Воскресенье отсутствует.
- Long names и многострочные lesson titles визуально не обрезаны; screen
  overflow равен 0.
- Subjects percentages: 63/12/25, 60/20/20, 67/17/16; каждая тройка = 100%.
- Future history segments после прошедших занятий используют neutral token.
- Визуальный render выполнен после числового аудита.

## Защищённые web pages 16–20

| Page | A | Instances | v2 hash | Дельта |
|---|---:|---:|---|---:|
| 16 auth | 890 | 390 | `35aaff08` | 0 |
| 17 student | 748 | 451 | `5145ee80` | 0 |
| 18 headman | 836 | 528 | `dff7e6d6` | 0 |
| 19 teacher | 363 | 231 | `80a981a7` | 0 |
| 20 admin | 286 | 176 | `2b7e73ac` | 0 |

Baseline совпал 5/5. Сумма — 3123 A, 1776 instances. Геометрическая дельта
и замыкание web pages 16–20: **0**. Исторический `v2` использовал две
сериализации; обе восстановлены и повторены точно, а старый geometryHash также
совпал 5/5 независимым проходом.

## Материалы, иконки и токены

- Page-21 masters: `icon/room` `4565:3068`, `icon/lesson-type` `4570:143`,
  `icon/time` `4570:149`, `icon/attendance-graph` `4570:155`; 16×16 px,
  stroke 1 px.
- Проверены semantic colors `surface/raised`, `surface/container`,
  `accent/now`, `accent/on-now`, `border/default`, status colors; новые
  variables не создавались.
- Variables: 248 = 157 COLOR + 89 FLOAT + 2 STRING.
- Styles: paint 1, text 10, effect 4, grid 0 — без дельты.

## Что убрано и почему

- Screen 11: отдельная отметка дублировала CTA на 10.
- Screen 15: detail задания перенесён в inline-state 14.
- Top title «Сегодня» на 10: освобождает header для центрированной role pill.
- Sunday: учебных пар в этом представлении нет.
- Поясняющие копии статусов, ticket numbers, внешний map link и Telegram tag:
  не несут нужной студенту работы.
- Screen borders и fixture wrappers: границы показывает непрерывная canvas
  backing, а не декоративная обводка каждого телефона.

## Transport/error ledger

- 1 font preflight Onest остановил запись до изменения текста; readback
  подтвердил отсутствие частичной мутации, затем шрифт был загружен явно.
- 1 preflight `Clean FileUploadField source missing` остановил создание нового
  state до первой мутации; повтор выполнен после выбора существующего local
  source.
- 1 read-only аудит использовал недоступный `findAncestor`; дизайн не менялся,
  обход заменён совместимой функцией родителей.
- Создание inline-state один раз выполнялось дольше обычного и завершилось
  успешно; слепого повтора и дубля экрана не было.
- Незавершённых транспортных отказов и неопределённых мутаций: **0**.

## Машиночитаемые снимки и renders

- Before: `journal/state/part-17-student-mobile-revision-before-2026-09-01.json`.
- After: `journal/state/part-17-student-mobile-revision-after-2026-09-02.json`.
- Ledger: `_work/student-mobile-revision-ledger-2026-09-02.json`.
- Family 12: `_work/figma-family12-after-inline.png`.
- Full final board: `_work/student-mobile-revision-final-board.png`.

## Что не сделано и почему

- Light theme: выполняется после приёмки dark.
- Публикация: принадлежит владельцу.
- Checkbox repair: массовая отметка запрещена; Checkbox только исследуется.
- Новые токены: не создавались до отдельного решения владельца.
- Физический legacy-master `student/MobileHomeworkDetail`: не удалён без
  отдельного разрешения; consumers = 0.

## Открытый вопрос владельцу

Для geo-failure backend должен вернуть стабильные `requestId`,
`requestStatus` и `retryAt`/`retryAfterSeconds`, чтобы пятиминутный cooldown
восстанавливался после повторного открытия PWA/TMA. Вопрос записан в
`journal/to-owner.md`.

## Изменённые файлы

- `_work/prompts/part-17-student-mobile-revision-2026-09-02.md:1–35` — сохранённый prompt партии.
- `_work/student-mobile-revision-ledger-2026-09-02.json:1–48` — финальный ledger.
- `journal/state/part-17-student-mobile-revision-after-2026-09-02.json:1–60` — after snapshot.
- `journal/DECISIONS.md:2285–2306` — отмена отдельных маршрутов 11/15 и финальные mobile-решения.
- `journal/to-owner.md:772–778` — backend-контракт retry/cooldown.
- `design/figma-spec-mobile.md:347–369` — геометрия grid, nav и micro-icons.
- `design/COMPONENT_REGISTRY.md:1343–1357` — дополнение закрытого реестра; legacy detail назван явно.
- `journal/reports/2026-09-02-part-17-student-mobile-revision.md:1–213` — этот отчёт.

Бинарные renders без номеров строк: `_work/figma-family12-after-inline.png`,
`_work/student-mobile-revision-final-board.png`.

## ✏️ 02.09.2026 — closing delta после комментариев владельца

### Ответы на уточнения

- Metadata-иконки изменены с 16×16 на 10×10 px; промежуточное решение 8×8
  отменено. Gap 4 px, внешний и внутренний stroke 1 px.
- History strips растянуты на всю внутреннюю ширину и совпадают с количеством
  запланированных пар.
- Overview `+ / н / у` возвращены; type-card values расположены в ряд.
- Homework disclosure отделён от «Материалов» и стоит справа; добавлен экран
  без material action.
- `Запрос отправлен` теперь без иконки и зелёный. Иконки открытых/архивных
  заявок разжаты из clip и оптически центрированы. Два нижних отступа 3 px
  заменены на 10 px.

### Живые числа после записи

- Page 24: **2139 A**, 290 instances, v2 hash `b751c0e9`.
- Final board: **1839 A**, 186 instances, 1920×12789 px.
- Screens: **39**, **1812 A**, 186 instances; семейства 10/12/13/14/16/17/18/19
  = **6/8/2/5/5/1/5/7**. Экранов 11 и 15 — 0.
- Metadata: **81/81 PASS** — room 25, lesson-type 34, date 11, time 11;
  10×10 px, gap 4 px, stroke 1 px. Date glyph 9,4×9,4 px.
- Root nav: **21/21 PASS**, x16 y752 358×76, bottom inset 16, selected radius
  16; icon+label black. Detail без nav — 18; double Back/nav — 0.
- History: **10/10 PASS**, x14…328, segment count = total lessons.
- Statistics type rows: **8/8 PASS**, 314×18 px, три items в одной строке.
  Overview percentages 72/18/10 = 100%.
- Homework: 5 screen states, 9 action rows; action width 326, disclosure
  x=282, target 44×44. `materials=none` — `4922:343`.
- Latest correction: request-sent children 1, icon count 0, success alias
  `VariableID:10:47`; ticket metadata **16/16 PASS**; selected-lesson bottom
  space **10/10 px**.
- Geometry: screen overflow 0, clipped overflow 0, small targets 0, shadows
  0, layer blur 0. Missing fonts 0, literal Inter 0, literal ellipsis 0.
- Variables: 248 = 157 COLOR + 89 FLOAT + 2 STRING. Styles 1/10/4/0.

### Геометрическая дельта

От предыдущего readback этой же партии: page 24 +49 A и +3 instances; board
+50 A, +3 instances и +868 px по высоте; screen bank +49 A и +3 instances;
экранов 38→39. Family 14: 904→1772 px; последующие family wrappers сдвинуты
Auto Layout на +868 px. Selected lessons 94→101 px. Metrics 43→48 px.
Homework action 186→326 px в stale state; statistics summary-actions
186→330 px; visible disclosure 44→22 px при сохранённом target 44×44.

Desktop pages 16–20 повторно не обходились по прямому указанию владельца;
все мутации closing delta ограничены page 24. Ранее записанный closure этой
партии остаётся историческим baseline, а не новым измерением.

### Transport/error ledger closing delta

- Две попытки изменить внутренний relative-transform/constraints экземпляра
  остановлены Figma; readback подтвердил полный rollback.
- Один selector-pass выбрал 0 узлов из-за переэкранирования; это нулевая
  запись, после точного selector покадровый проход завершён.
- Один read-only closing audit остановился на `ReferenceError`; мутаций не
  выполнял, заменён mobile-only проходом.
- Один instance rebound 10→12 px найден независимым readback и стабилизирован
  прозрачным 10×10 wrapper вокруг существующего component instance.
- Неопределённых или частично записанных мутаций: 0.

### Новые renders и snapshots

- State: `journal/state/part-17-student-mobile-revision-final-2026-09-02.json`.
- Prompt: `_work/prompts/part-17-student-mobile-latest-corrections-2026-09-02.md`.
- Renders: `_work/renders/latest-today.png`,
  `_work/renders/latest-attendance-types.png`, `_work/renders/latest-reason.png`,
  `_work/renders/latest-homework-far.png`,
  `_work/renders/latest-homework-no-material.png`,
  `_work/renders/latest-stat-overview.png`, `_work/renders/latest-stat-types.png`,
  `_work/renders/latest-request-sent.png`,
  `_work/renders/latest-tickets-aligned.png`,
  `_work/renders/latest-application-form-spacing.png`.

### Что не сделано

Light theme, публикация, Checkbox mutation, массовая отметка и новые tokens
не выполнялись. Публичный registry новым компонентом не расширялся.

## ✏️ 02.09.2026 — closing delta: выравнивание metadata заявок

### Разбор и решение

- Живой эталон `4597:3584`: row 17 px, text 13 px / auto-height,
  icon 10×10 px при y=3,5 px, gap 4 px.
- Живые заявки `4610:295` и аналоги: row 20 px, text 12 px с
  `textAutoResize=NONE`, icon 10×10 px при y=5 px.
- Причина визуального смещения — различная типографика строки, а не размер
  или stroke иконки. Общие icon masters не изменялись.

### Запись и readback

- Исправлены 16/16 metadata rows: 8 на открытых и 8 на архивных заявках.
- После записи: row 17 px, text 13 px / `WIDTH_AND_HEIGHT`, icon 10×10 px,
  gap 4 px, center delta 0 px, clipping 0.
- Геометрия open ticket-card 432→408 px; две lesson-card 113→104 px каждая.
  Геометрия archive ticket-card 403→379 px; две lesson-card 113→104 px.
  Ширины не менялись.
- Экран `4610:142`: 50 A, 9 физических instances, overflow 0.
  Экран `4610:848846`: 49 A, 9 физических instances, overflow 0.
- Final board: 1839 A, 186 физических instances, 1920×12789 px; дельта A = 0.
- Визуальный readback выполнен на обоих кадрах. Первый archive screenshot
  пришёл из кеша до актуализации, повторный render показал все строки;
  неопределённых и частичных мутаций 0.

### Renders

- `_work/renders/latest-tickets-open-icons.png`
- `_work/renders/latest-tickets-archive-icons-2.png`

### Что не сделано

Desktop pages, общие masters, токены, light theme, публикация, массовая
отметка и Checkbox не затрагивались. Дополнения закрытого component registry
нет, потому что публичный компонент не создавался и не менялся.
