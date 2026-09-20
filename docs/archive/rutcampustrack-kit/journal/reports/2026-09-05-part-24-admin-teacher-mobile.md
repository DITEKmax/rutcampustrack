# Part 24 — owner corrections Admin + Teacher mobile

Дата: 05.09.2026. Figma: design-system/ui-kit-rct,
file key VgVjQYWILLG9AC7Eh12VMk.

## Ответы на вопросы

Новых вопросов владельцу в продолжении не было. Работа возобновлена из
checkpoint, Admin закрыт первым, затем без ожидания приёмки выполнен Teacher.
Прямая формулировка владельца «кто когда принял подал» трактуется как отмена
старого запрета на decision actor именно в авторизованной Teacher projection:
финальный read-only detail показывает имя и время решения старосты.

## Разбор и стоп-ворота

- Teacher baseline совпал с checkpoint: 300 A, geometry hash 5d35f2ee,
  board 908×4532 px, 8 product-кадров 390×844 px и 5 family labels.
- Owner назвал tab-контейнеры 5825:147 и 5838:379. Неверная заливка жила в
  их selected children 5825:148 и 5838:382; исправлены именно children.
- Публичный AttendanceStatusBadge имеет размеры 20 px и 24 px, но не 16 px.
  Для прямого owner-требования Stats созданы только локальные экранные круги
  16 px на существующих status tokens; новый master/variant/token не добавлен.
- Четыре оставшиеся Profile rows имеют literal radius 18 px. Точного
  semantic token нет; этот пункт не подгонялся к radius 16/24 и записан как
  отдельный gap.
- После Admin GO появились устойчивые внешние изменения pages 25 и 26.
  Ни один Teacher write не адресовал эти страницы; общий мастер не менялся,
  чужая работа не откатывалась.

## Admin: независимый аудит и critic gate

Admin был закрыт до начала Teacher. Gate-snapshot:

- page 26: 535 A, hash fda108f8;
- board 908×9908 px;
- 19 product-кадров + 5 labels;
- 9 root с bottom nav, 10 task/detail с product Back;
- 19/19 кадров 390×844 px;
- bad selected-nav paints 0;
- overflow, unintended rendered ellipsis, collapsed text, missing fonts,
  Inter, gradients, unbound local solid paints и touch-targets меньше 44 px —
  по 0;
- shared-master writes, новые tokens/components/icons и publication — 0.

Critic verdict для этого snapshot: Admin Gate GO.

Позже live Admin изменился извне до 534 A / f4bc99a5. Это не приписывается
Teacher-партии и не возвращалось к gate-snapshot.

## Teacher: итог

Финальный банк содержит 9 product-кадров и 5 labels. Page 27:
300 → 361 A, geometry hash 5d35f2ee → 0e9802c5. Board остался
908×4532 px. Все кадры 390×844 px.

| Кадр | A | Позиция px | Навигация |
|---|---:|---:|---|
| 30 · Сегодня / default | 40 | 52, 52 | bottom nav |
| 31 · Пара / roster | 109 | 466, 52 | Back |
| 31 · Посещаемость / lessons | 32 | 52, 948 | bottom nav |
| 31 · Заявка / уважительная причина / read-only | 26 | 466, 948 | Back |
| 32 · Статистика / по студентам | 56 | 52, 1844 | bottom nav |
| 32 · Статистика / по моим группам | 52 | 466, 1844 | bottom nav |
| 34 · Карта / default | 5 | 52, 2740 | bottom nav |
| 39 · Профиль / default | 21 | 52, 3636 | bottom nav |
| 39 · Профиль / смена роли | 14 | 466, 3636 | Back |

Итого: 6 root с nav и без Back; 3 detail/task с Back и без nav. Invalid
ownership: 0. MainButton на этих PWA-кадрах отсутствует.

### Сегодня и roster

- Отдельный H1 «Сегодня» удалён; role-switch trigger один, 144×44 px,
  расположен сверху.
- Current и next получили вертикальные строки группы, аудитории и типа с
  существующими room/type icons. Current использует accent/now и
  accent/on-now для текста, glyph fill и stroke.
- Roster context переработан тем же паттерном.
- Heading теперь «Посещаемость», справа 78 % +; «· 27 студентов» удалено.
- Все 27 строк read-only. Лейблы статусов скрыты; видны только символы.
  Шесть строк со статусом у имеют отдельную цель 60×44 px и disclosure;
  у остальных статусов ticket arrow = 0.

### Заявка

Добавлен новый read-only detail 5986:5399. Он показывает:

- автора Алина Николаева и время подачи;
- решение старосты Мария Соколова и время решения;
- причину и комментарий;
- только две пары предмета «Основы программирования»;
- файл Справка.pdf.

У detail один Back, bottom nav 0, approve/reject/decision controls 0.
Production обязан отдавать этот набор server-side с Teacher authorization;
клиентская фильтрация полного ticket недопустима.

### Посещаемость, статистика, карта и профиль

- Role badge удалён с Attendance. Три завершённые пары показывают 78/81/74 %
  присутствий со знаком + вместо количества студентов.
- В обеих Statistics selected fill заменён
  accent/soft → accent/now, selected label — на accent/on-now.
- Старые 16 metric-label texts удалены. Их заменяют 20 status circles 16×16:
  +, пара +/у, у, н на present/excused/absent tokens.
- В group statistics удалён блок subject unavailable; type filter перемещён
  y196→120, section y256→180, cards y288→212 и y460→384.
- Map содержит один visible «Карта кампуса», role и внешний CTA удалены.
- Profile не содержит role subtitle или второго «Сменить роль». Четыре
  оставшиеся строки подняты на y171/239/307/375.
- В role task current Teacher использует accent/now + check; текст
  «Текущая роль» удалён.

## Геометрические изменения

Все значения ниже — px с live file.

- Today role carrier: x214/y6/144×32 → x107/y0/144×44.
- Today pager: y64 → 56.
- Current lesson: y120/h156 → y112/h168.
- Current section title: y292 → 296.
- Next lesson: y324/h132 → y328/h168.
- Assignments title/cards: y480/512/580 → 512/544/612.
- Roster context: h128 → 156.
- Roster heading: y220 → 248; добавлен rate x246/y250/128×20.
- Roster viewport: y252/h576 → y280/h548; intentional vertical continuation
  остаётся внутри clipsContent viewport.
- Labeled status widths 117–136×24 заменены 24×24 symbols; только у использует
  wrapper 60×44.
- Attendance rate: x14/w300/left → x234/w110/right.
- Stats text labels 210×20 → single circle 16×16 или pair wrapper 36×16.
- Group Stats filter 358×64 удалён; нижние блоки подняты на 76 px.
- Map title width 120 → 220 при тексте «Карта кампуса»; role и AppLink скрыты.
- Profile name поднят внутри identity card на y10; после удаления role entry
  четыре settings rows подняты на 68 px.
- Role current text width 174 → 294; check 16×16 добавлен в x326/y24.
- Новый ticket frame: x466/y948/390×844; board size не изменился.

## Независимый QA

- overflow 0; collapsed text 0; rendered ellipsis 0;
- missing fonts 0; Inter 0; продуктовые тексты Onest;
- gradients 0;
- эффекты вне шести canonical nav BACKGROUND_BLUR — 0;
- unbound local solid fills/strokes — 0;
- touch targets меньше 44 px — 0;
- каждый из шести nav имеет ровно один selected item; bad selected glyph/label
  paints — 0;
- 92 radius-carriers привязаны к tokens;
- literal radii: только четыре Profile rows по 18 px;
- неправильных смысловых привязок в изменённых узлах не найдено:
  selected tabs используют 10:17/10:20, status circles — 10:47/51/55 и
  on-fill пары, current lesson/check — 10:20.

Ни один общий master не тронут, поэтому consumer before/after delta для
изменённых masters равна нулю: изменённых masters нет. Переиспользованы
существующие role chevron, room, lesson-type, next, check и status variants.

## Защищённые страницы

Pages 16–24 совпали с checkpoint по A и geometry hash:

| Page | A | Hash |
|---:|---:|---|
| 16 | 890 | 455cd388 |
| 17 | 748 | 5bc52169 |
| 18 | 836 | 0acf793c |
| 19 | 363 | 96e171a8 |
| 20 | 286 | 401f7db5 |
| 21 | 142 | 0ce38243 |
| 22 | 229 | dea5888a |
| 23 | 796 | a83b3f15 |
| 24 | 2139 | d4743e24 |

Page 25 независимо и устойчиво читалась как 2552 A / f954b76d против
checkpoint 2611 A / 74980519. Admin после своего GO независимо и устойчиво
читался как 534 A / f4bc99a5 против gate 535 A / fda108f8. Оба расхождения
классифицированы как параллельные внешние изменения: mutation ledger Teacher
содержит только page 27, shared-master writes = 0.

## Рендеры и verdict

Девять актуальных PNG и contact sheet:
_work/renders/part-24/teacher/.

Functional analyst: GO. Mobile architect: GO. Critic: Teacher Gate GO.

## Что не сделано и почему

- Не публиковалось: публикацию выполняет владелец.
- Не создавались tokens, components, icons или новые public variants: все
  нужные смыслы переиспользованы; 16 px Stats circles являются локальной
  прямой owner-правкой.
- Не исправлялись четыре radius 18: отсутствует точный token и нет решения
  владельца на master/token change.
- Не откатывались внешние изменения pages 25/26.
- Не собирались light, 320/360/430, browser/TMA adapters, keyboard и полная
  loading/empty/error/offline/conflict матрица: это следующий согласуемый
  объём.
- Не объявлялись готовыми отсутствующие Teacher-scoped read endpoints,
  aggregates, ticket projection, role-switch ACK/cache invalidation.
- Specs в specs/ не редактировались: они принадлежат владельцу; отмена старого
  ограничения decision actor записана в journal и figma-spec-mobile.

## Изменённые файлы

Номера строк фиксируются финальным line-index после записи:

- design/figma-spec-mobile.md;
- journal/DECISIONS.md;
- journal/to-owner.md;
- journal/state/part-24-admin-teacher-mobile-after-2026-09-05.json;
- journal/reports/2026-09-05-part-24-admin-teacher-mobile.md;
- _work/scripts/make-teacher-part24-contact-sheet.py;
- _work/renders/part-24/teacher/01–09 PNG и contact-sheet.png
  (binary, строки неприменимы).

## Финальный line-index

- design/figma-spec-mobile.md: новая запись начинается со строки 560;
- journal/DECISIONS.md: новая запись начинается со строки 2479;
- journal/to-owner.md: новая запись начинается со строки 995;
- journal/state/part-24-admin-teacher-mobile-after-2026-09-05.json: строки 1–142;
- journal/reports/2026-09-05-part-24-admin-teacher-mobile.md: строки 1–222;
- _work/scripts/make-teacher-part24-contact-sheet.py: строки 1–41;
- PNG/contact-sheet: binary, номера строк неприменимы.

✏️ 05.09.2026 — correction: две диапазонные строки выше были посчитаны до
финального сохранения и отменяются. Фактические диапазоны:
state JSON — строки 1–118; этот report — строки 1–230. Остальные стартовые
строки и диапазон script 1–41 верны.

✏️ 05.09.2026 — final correction: диапазон report 1–230 выше также
отменяется; для самого этого append-only файла фиксируется стабильная ссылка
на строку 1 без самоссылочного номера последней строки.

## ✏️ 05.09.2026 — closing live-delta correction

После записи отчёта последний read-only checksum снова подтвердил Teacher
без изменений: 361 A / 0e9802c5. Admin продолжил независимо двигаться и
изменился с промежуточных 534 A / f4bc99a5 до 531 A / 4d893590.
Следовательно, прежнее значение Admin в разделе «Защищённые страницы»
остаётся честным промежуточным снимком, но не последним live read.

Последовательность Admin 535/fda108f8 → 534/217e4d76 →
534/f4bc99a5 → 531/4d893590 при неизменном Teacher и нулевых shared-master
writes зафиксирована отдельно в
journal/state/part-24-admin-teacher-mobile-external-delta-2026-09-05.json.
Страница владельца не откатывалась и больше не опрашивалась.

Дополнение к списку изменённых файлов: state внешней дельты
journal/state/part-24-admin-teacher-mobile-external-delta-2026-09-05.json
начинается со строки 1.
