# Part 21 — Headman mobile polish, pass 2

Дата: 04.09.2026. Figma: `VgVjQYWILLG9AC7Eh12VMk`. Страница: `25 · Mobile · Headman` (`4996:142`).

## Ответы на вопросы

- Да, выполнена вся переданная партия правок, а не только первые пункты.
- Navbar теперь имеет единственный token-bound `BACKGROUND_BLUR 25 px`; двойного эффекта нет.
- `Align middle` применён ко всем однострочным controls/values/glyphs headman, но не к многострочному prose и textarea.
- Ручная отметка различается outline-кругом без видимого слова `Вручную`; source остаётся в данных и журнале действий.
- Добавлено отдельное весеннее состояние статистики с full-card иллюстрацией и защищённой зоной текста.
- Блокирующих вопросов для статического 390 px dark-bank не осталось. Следующая platform/width/state/light партия не начиналась.

## Разбор до записи

- Непонятное локализовано: команда `Align middle` относится к однострочным labels/values/glyphs в фиксированных контейнерах. Многострочные body, helper, textarea, причины и описания сохраняют `TOP`, иначе нарушается чтение.
- Новое решение владельца отменяет старое требование видимой подписи `Вручную`. `source=headman` остаётся в данных и accessible name, а на плотном экране источник кодируется незалитым статус-кругом.
- Navbar имел два одновременных blur-носителя: вложенный token-bound `20 px` и внешний screen-local `24 px` на одном / `25 px` на пятнадцати экземплярах. Цель — один token-bound blur `25 px`.
- Живой baseline page 25: `2481 A`, `315 INSTANCE`, `1165 TEXT`, `823 FRAME`; прошлое закрытие: `2487 A`, `321 INSTANCE`, `1165 TEXT`, `823 FRAME`. Дельта до этой партии равна `−6 A`, все шесть — INSTANCE; она принята как входное состояние и не восстанавливается вслепую.
- Inclusive typography audit: `1258 TextNode`, из них `1195 TOP`, `63 CENTER`; эти inclusive-узлы не являются единицей A.
- Новых данных/прав не требуется. Табельные номера в макете — демонстрационные значения; production должен читать их из преподавательского профиля.

## Consumer-аудит общего navbar

`shared/MobileBottomNav` (`4175:403`) имеет `68` живых экземпляров: `44` на mobile student, `16` на mobile headman, `6` на mobile foundations, `2` на mobile components, `0` на auth. Владелец явно установил новое значение `25 px`; поэтому разрешено изменить существующий blur-токен без изменения anatomy мастера. Ожидаемая дельта A и геометрии у consumers — ноль; визуальная дельта student/foundations/components — только `20→25 px`.

## Совет до сборки

Функциональный аналитик подтвердил: удаление видимого `Вручную` не меняет API, `status` или индивидуальный commit; source, author/time и revision сохраняются. Мобильный архитектор предложил локальный token-driven pass, без новых мастеров и literals. Критик запретил массовое центрирование paragraph/textarea и двойной blur; эти замечания приняты. Позднее прямое решение владельца о единственном видимом выбранном статусе имеет приоритет: повторный тап снимает статус и возвращает три выбора.

## Baseline

Машинный снимок до записи: `journal/state/part-21-headman-mobile-polish-2-before-2026-09-04.json`.

## Функциональная карта и граница контракта

- `Сегодня`: календарь → выбранная пара → краткие метрики → уважительные причины → ДЗ → состояние пары и вторичные действия.
- `Учёт`: 27 индивидуальных строк; пустая строка предлагает `+ / у / н`, выбранная показывает только текущий статус. Bulk, Checkbox и общий commit отсутствуют.
- `Статистика`: semester-selector → общие метрики → индивидуальный / предметный срез → история прошедших пар. Осень и весна являются состояниями одного selector, а не новыми route.
- `Предметы`: список → editor типов занятий → поиск и назначение преподавателя → отдельный confirm удаления.
- `Конструктор`: выбор доступного slot; доступность читается зелёной заливкой, занятые slots disabled.
- `Управление парами`: transfer показывает `Было → Стало` до отправки.
- `Заявки`: четыре фильтра одной высоты, выбранный `Все` имеет повышенный typographic emphasis.

Все изменения этой партии presentation-only, кроме изменения уже существующего blur-токена `20→25 px`, прямо заданного владельцем. API, права, route и commit/revision contract не менялись. Для ручной отметки обязательные данные остаются: status, `source=headman`, actor/time, reason/ticket для `у` и row revision.

## Варианты, критика и выбранное решение

1. Массово поставить `MIDDLE` каждому TextNode. Отклонено: paragraph, helper и textarea теряют естественный верхний якорь.
2. Центрировать только однострочный текст фиксированных controls/rows/glyphs. Выбрано: исправляет оптику и сохраняет чтение prose.
3. Исправлять каждую иконку локальным y-offset без изменения текстового alignment. Отклонено как симптоматическое и нестабильное решение.

Для navbar вариант с локальными blur на 17 экранах отклонён. Выбран один nested token-bound carrier в общем компоненте; перед изменением выполнен consumer-аудит. Для seasonal selector маленькая угловая иллюстрация и full-card art без scrim отклонены; выбран full-card vector с token-bound text-safe слоем.

Независимый критик дважды остановил закрытие: сначала из-за невидимого outline у Анны и недостаточного spring contrast, затем из-за двухстрочного placeholder преподавателя. Все три дефекта исправлены; повторный visual pass — `GO`.

## Tap budget

- `Сегодня → журнал`: 1 тап по выбранной паре.
- Поставить `+` или `н` в пустой строке: 1 тап; снять выбранный статус: 1 повторный тап.
- Поставить `у`: 1 тап по статусу + 1 обязательный выбор причины; комментарий и файл остаются необязательными.
- Переключить semester state: 2 тапа — раскрыть selector и выбрать семестр.
- Фильтр заявок или выбор slot: 1 тап.

Партия не добавила дополнительного route, промежуточного review или silent commit.

## Что изменено

### Navbar и типографика

- token `component/mobile-bottom-nav/glass-background-blur`: `20→25 px` в dark/light;
- удалены `16` screen-local root blur и `3` root blur у вариантов общего мастера; сохранены `69` nested token-bound carriers по всем consumers;
- consumers после добавления spring-state: student `44`, headman `17`, foundations `6`, mobile components `2`, auth `0`;
- в исходных 1 258 inclusive TextNode переведены в `MIDDLE` 1 175 однострочных узлов; 20 осмысленных `TOP` сохранены. Новый spring-state добавил 44 текста: 39 `CENTER`, 5 `TOP`.

### Сегодня и Учёт

- три метрики `72→36 px`;
- past excuse card `104→72 px`; её высота теперь соответствует одной строке;
- past homework: `ДЗ нет`; удалены лишние summary и chevron (`−2 A`);
- состояние прошлой пары: `Была заблокирована`;
- все 27 time icons заменены чистыми instances существующего `icon/time`, итоговый box `10×10 px`;
- roster: 6 empty rows × 3 нейтральные цели + 21 selected rows × 1 цель = 39 видимых status targets;
- 7 ручных статусов используют обводку `2 px` и соответствующий status/fill token для ring и glyph; заливки нет;
- видимое `Вручную` удалено из roster и history. Две подписи `Вручную · Староста` намеренно сохранены в action log: там это actor/source события, а не дублирование status marker.

### Статистика

- autumn selector перестроен в full-card leaves: 24 vector descendants, 16 цветных token-bound paints, unbound paints `0`;
- добавлен `22 · Статистика / spring-semester`: 38 art descendants, 33 цветных token-bound paints, unbound `0`;
- spring text-safe `248×82 px`, opacity `72%`; title и dates связаны с `color/text/primary`;
- status symbols нормализованы по весу и центру; проценты используют основной читаемый текст;
- 16 числовых ячеек предметной таблицы имеют центр по колонкам; отклонений `0`;
- visible `Вручную` в student history: `0`.

### Предметы, конструктор, перенос и заявки

- assignment cards в editor: `98→112 px`, нижний inset `16 px`, межкарточный gap `12 px`;
- кафедра заменена табельным номером; fixture Кузнецова согласован как `10482` в search и confirm;
- search copy: `ФИО или табельный номер`; слой `250×18 px`, одна строка внутри поля `358×44 px`;
- selected teacher row: `accent/now`; carrier check: `accent/soft`; glyph: `text/on-fill-strong`;
- доступные 3-й и 4-й slots имеют одну semantic green fill и без outline, как прямо указал владелец;
- transfer: `Было` использует `danger/bg`; блоки `72 / 44 / 72 px`, интервалы `8 / 8 px`;
- request filters: `92 / 68 / 68 / 106 px`, высота всех `44 px`; выбранный `Все 6` — Onest SemiBold 14.

## Что удалено с мобильного

- дублирующие root blur на навигации;
- видимая подпись источника `Вручную` из status rows/history;
- лишние summary и переход из empty homework прошедшей пары;
- прежняя маленькая сезонная декорация selector заменена полноформатной, а не продублирована.

Функции, маршруты, права и данные не удалялись. Action log сохраняет текстовый source, потому что это содержание события.

## Иконки и иллюстрации

Переиспользованы существующие `icon/time`, `icon/previous`, `icon/next`, `icon/chevron-down`, metadata-icons и буквенные status glyphs. Новых semantic icon masters и изменений registry нет. Autumn leaves и spring flowers — локальные token-bound vector illustrations состояния, не иконки и не публичные компоненты.

## Состав и A каждого кадра

Все `46` product frames имеют `390×844 px`. A ниже включает корневой Frame кадра; внутрь instances счётчик не заходит.

| ID | Кадр | A |
|---|---|---:|
| `5033:316` | `19 · Ещё / headman` | 40 |
| `5271:967` | `19 · Профиль / headman` | 25 |
| `5273:1016` | `19 · Настройки / headman` | 39 |
| `4997:142` | `20 · Сегодня / default` | 70 |
| `5185:1050` | `20 · Сегодня / other-lesson` | 66 |
| `5332:1149` | `20 · Сегодня / homework-expanded` | 60 |
| `5001:216` | `21 · Учёт / roster-list` | 396 |
| `5186:1100` | `21 · Учёт / reason-picker` | 31 |
| `5189:1104` | `21 · Учёт / excuse-ticket` | 22 |
| `5192:1111` | `21 · Учёт / action-log` | 46 |
| `5035:350` | `22 · Статистика / individual-list` | 111 |
| `5036:384` | `22 · Статистика / individual-expanded` | 81 |
| `5204:1123` | `22 · Статистика / by-subject` | 81 |
| `5206:1157` | `22 · Статистика / student-history` | 92 |
| `5208:1181` | `22 · Статистика / status-cleared` | 53 |
| `5573:142` | `22 · Статистика / spring-semester` | 125 |
| `5037:385` | `23 · Домашнее задание / calendar` | 54 |
| `5039:419` | `23 · Домашнее задание / detail` | 16 |
| `5216:1180` | `23 · Домашнее задание / create-subject` | 22 |
| `5218:1180` | `23 · Домашнее задание / create-type` | 27 |
| `5220:1180` | `23 · Домашнее задание / create-calendar-schedule` | 98 |
| `5222:1180` | `23 · Домашнее задание / create-calendar-any` | 98 |
| `5224:1180` | `23 · Домашнее задание / create-content` | 23 |
| `5050:142` | `24 · Карта / default` | 5 |
| `5058:142` | `25 · Группа / root` | 131 |
| `5061:142` | `25 · Группа / student-detail` | 14 |
| `5063:142` | `25 · Группа / permissions` | 26 |
| `5069:569` | `26 · Предметы / root` | 53 |
| `5075:617` | `26 · Предметы / editor` | 34 |
| `5231:1151` | `26 · Предметы / add-teacher` | 48 |
| `5407:181` | `26 · Предметы / remove-teacher-confirm` | 16 |
| `5085:652` | `27 · Конструктор расписания / root-empty` | 24 |
| `5087:701` | `27 · Конструктор расписания / slot-editor` | 49 |
| `5238:1049` | `27 · Конструктор расписания / root-filled` | 30 |
| `5107:142` | `28 · Управление парами / agenda` | 60 |
| `5121:142` | `28 · Управление парами / active-detail` | 25 |
| `5124:142` | `28 · Управление парами / cancelled-detail` | 17 |
| `5131:142` | `28 · Управление парами / transfer` | 31 |
| `5135:142` | `28 · Управление парами / cancel-confirm` | 13 |
| `5244:1041` | `28 · Управление парами / agenda-add-mode` | 49 |
| `5427:276` | `28 · Управление парами / transfer-calendar-open` | 103 |
| `5145:142` | `29 · Заявки / queue-by-lesson` | 61 |
| `5152:142` | `29 · Заявки / detail` | 27 |
| `5155:142` | `29 · Заявки / approve-confirm` | 46 |
| `5157:142` | `29 · Заявки / reject-confirm` | 17 |
| `5265:919` | `29 · Заявки / archive` | 45 |

## Геометрическая дельта и baseline

Page 25 до: `2481 A / 315 instances / 1165 text / 823 frames`, hash `edd8c9c8`.
После: `2611 A / 320 instances / 1204 text / 847 frames`, hash `1bd23f45`.
Дельта: `+130 A / +5 instances / +39 text / +24 frames`.

Разложение `+130 A`: autumn art `+11`, past homework `−2`, history labels `−4`, новое spring-state `+125`. Последующие alignment, paint, inset и text-safe изменения A не меняли.

Защищённые страницы 16–24 после независимого прохода совпали с baseline по A и geometry hash:

| Страница | A | Hash | ΔA | Δ geometry |
|---|---:|---|---:|---:|
| 16 Auth | 890 | `5f3b7c58` | 0 | 0 |
| 17 Student | 748 | `30f94299` | 0 | 0 |
| 18 Headman desktop | 836 | `40612138` | 0 | 0 |
| 19 Teacher | 363 | `f0e9fb9c` | 0 | 0 |
| 20 Admin | 286 | `37cd232d` | 0 | 0 |
| 21 Icons | 136 | `6c352d57` | 0 | 0 |
| 22 Mobile foundations | 229 | `4424014e` | 0 | 0 |
| 23 Mobile components | 796 | `ef903629` | 0 | 0 |
| 24 Mobile student | 2 139 | `139a1be0` | 0 | 0 |

Ожидаемая и фактическая дельта страниц 16–24: `0 A / 0 geometry`. По прямому решению владельца есть осознанная visual-effect дельта у 44 student, 6 foundation и 2 component consumers: единичный blur `20→25 px`; anatomy и геометрия не изменились.

Machine snapshots:

- `journal/state/part-21-headman-mobile-polish-2-before-2026-09-04.json`;
- `journal/state/part-21-headman-mobile-polish-2-after-2026-09-04.json`.

## Независимый QA числами

- product frames: `46`; неверный размер: `0`;
- direct root overflow: `0`; визуальных clipping-дефектов на family renders: `0`;
- `17` bottom nav + `29` Back = `46` взаимоисключающих экранных owners; двойной Back/nav: `0`; Back меньше `44×44`: `0`;
- nested nav blur на page25: `17/17`, radius `25`, variable-bound `17/17`; root blur `0`; по всем consumers `69/69` nested и `0` root;
- selected nav с белым glyph/label на `accent/now`: `0`;
- roster rows: `27`; selected `21`; empty `6`; visible status targets `39`; manual outline `7`; дефектных outline `0`; bad time icon `0`;
- метрики Today: `36 / 36 / 36 px`; past excuse `72 px`;
- spring text-safe: `248×82 px`, opacity `72%`; title/date привязаны к `color/text/primary`;
- числовых table cells: `16`; неверных центров: `0`;
- search placeholder: `250×18 px`, одна строка, выход из поля: `0`;
- request targets: `92 / 68 / 68 / 106 × 44 px`;
- transfer gaps: `8 / 8 px`; overlap: `0`;
- `Inter`: `0`; missing fonts: `0`; shadows: `0`; layer blur: `0`; glow: `0`;
- случайных визуальных ellipsis: `0`. Три inherited `textTruncation=ENDING` в карте/поиске содержат полностью видимые короткие строки и не обрезаются;
- статусов, читаемых только цветом: `0`; glyph и форма сохранены;
- новых локальных hex paints: `0`; unbound цветных paints сезонной графики: `0`.

Финальный visual QA просмотрел roster, всю статистику и subjects в original resolution. Последний независимый вердикт critic: `GO`.

## MCP / transport ledger

- `whoami`: `1` раз в начале;
- `use_figma`: `92` последовательных вызова: `28` mutation-intent и `64` read/audit;
- committed atomic mutations: `25`; rejected до записи из-за font/runtime: `3`; независимых post-mutation readback: `25`;
- `get_screenshot`: `19` render-вызовов;
- `429`: `0`; HTTP timeout/504: `0`;
- `Transport closed`: `6`: пять mutation-вызовов оказались полностью committed по отдельному readback, один тяжёлый closing-read был повторён меньшими bounded-pass;
- runtime/read errors: `5`; partial mutation: `0`;
- все Figma-вызовы выполнялись последовательно, дневной/скользящий budget не превышен.

## Проверка токенов и fallback

Проверены и применены существующие соответствия: `surface/base` и
`surface/raised` (`10:3/10:4`), `text/primary` и `text/secondary` (`10:6/10:7`),
`accent/now` и `accent/on-now` (`10:17/10:20`), success (`10:27/10:28/10:30/10:31`),
danger (`10:37/10:38/10:40`), present (`10:47–10:50`), excused (`10:51–10:54`),
absent (`10:55–10:58`) и существующий blur variable `4354:143`.

Мест без подходящего semantic token в изменённых product paints: `0`.
Запросов на новые токены: `0`; временных fallback: `0`. Изменено значение уже
существующего blur-токена по прямому решению владельца.

## Рендеры

- `family-20-21-day-attendance.png` — Сегодня, журнал и связанные состояния;
- `21-roster-list-final.png` — финальная проверка 27 строк и manual outline;
- `family-22-statistics-final.png` — осень, весна, индивидуальный/предметный срез и история;
- `family-26-subjects-final.png` — список, editor, поиск/назначение и confirm удаления;
- `family-27-builder.png` — свободные/занятые slots;
- `family-28-management.png` — управление и transfer;
- `family-29-requests.png` — очереди и фильтры заявок.

Все файлы находятся в `_work/renders/part-21-headman-mobile-polish-2/`.

## Открытые вопросы

До production-state matrix остаются локальные contract gaps:

1. roster/history должны возвращать `source`, actor/time и row revision для всех статусов;
2. нужны idempotency, ETag/revision, structured conflict и операция снятия в `no-status`;
3. для `у` нужны справочник причин, optional comment/file, ограничения файла, ticket linkage и auto-accept receipt;
4. не подтверждены effective permissions и ACK lock/unlock, просмотр ticket и event API журнала действий;
5. табельные номера текущего fixture демонстрационные; production получает canonical value из teacher profile/search.

Эти вопросы не блокируют текущую статическую визуальную валидацию, но блокируют соответствующие pending/error/offline/conflict состояния.

## Что не сделано и почему

- PWA browser/standalone и TMA overview/task;
- 360×800, 430×932 и stress 320;
- keyboard, loading/empty/error, scrolled/pressed;
- offline/stale/conflict, long data, 30 students, increased contrast;
- light theme и публикация библиотеки.

Они намеренно не начаты до комментариев владельца по текущему 390 px dark-bank. Общие masters, кроме явно разрешённого исправления единственного blur-carrier, не менялись; Checkbox и bulk не трогались.

## Изменённые файлы

- `_work/prompts/part-21-headman-mobile-polish-2.md:1–47` — сохранён prompt партии;
- `journal/state/part-21-headman-mobile-polish-2-before-2026-09-04.json:1–32` — машинный baseline;
- `journal/state/part-21-headman-mobile-polish-2-after-2026-09-04.json:1–96` — closing snapshot и QA;
- `design/brandbook-v2.md:1001–1024` — актуальные mobile glass, alignment, source и seasonal-art правила;
- `design/figma-spec-mobile.md:531–558` — append-only спецификация второго polish-pass;
- `design/tokens-v2.json:1156–1162` — существующий blur-token `20→25 px` и датированное пояснение;
- `journal/DECISIONS.md:2458–2477` — решение владельца 04.09.2026;
- `journal/to-owner.md:914–932` — оставшиеся contract gaps;
- `journal/reports/2026-09-04-part-21-headman-mobile-polish-2.md:1–291` — этот отчёт.

Бинарные render-артефакты обновлены в `_work/renders/part-21-headman-mobile-polish-2/`; строковых номеров у PNG нет.
