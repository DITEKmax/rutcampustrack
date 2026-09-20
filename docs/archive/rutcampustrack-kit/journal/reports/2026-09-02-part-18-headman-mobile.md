# Part 18 — mobile headman: первый 390 px probe

Дата: 02.09.2026. Figma: `VgVjQYWILLG9AC7Eh12VMk`, page 25 `4996:142`.

## Ответы на вопросы владельца

1. **Нужна ли отдельная страница?** Да. Создана ровно одна страница
   `25 · Mobile · Headman`; отдельный файл не создавался.
2. **Как разложены кадры?** Семейства идут вертикально: 20 сверху, 21 ниже.
   Действия одного семейства 21 идут горизонтально:
   `roster-list → focus-deck → review-commit`.
3. **Собран ли остальной банк?** Нет. После четырёх кадров 390×844 работа
   остановлена на визуальную приёмку владельца.

## Разбор до записи

### 1. Что было непонятно

- В живом ПК-111 нет данных следующей пары. Для probe сохранён обязательный
  слот, но он честно показывает `После 19:30 · Пока нет данных`; точный fixture
  нужен от владельца или расписания.
- В документах нет batch/draft commit с base revision, idempotency и atomic
  result. Статический review поэтому не обещает реальную отправку или ACK.
- Не подтверждены effective permissions помощника старосты, payload причины
  для ручного `у`, снятие в «нет данных» и правило commit при остатке без
  статуса.

### 2. Противоречия живому файлу и поздним решениям

- Массовая отметка из ранней истории отменена поздним решением владельца.
  В probe нет bulk, Checkbox, предварительного массового статуса и silent
  commit.
- Поздний mobile-порядок требует list-first. ПК-таблица использована как
  источник функции и данных, но не раскладки.
- `MobileSyncStatus` не использован как обещание outbox/ACK: review остаётся
  precommit, слово «Синхронизировано» отсутствует.

### 3. Числовые расхождения документов и Figma

- Page 24 содержит 71 кадр 390×844 во всей странице, но 39 кадров в финальном
  принятом банке: `39 final + 32 ранних probe/fixtures = 71`. Это разные
  единицы выборки, не ошибка.
- Живой ПК-111 показывает `18/25` присутствующих и `3/25` уважительных;
  прямой поздний prompt задаёт mobile fixture `21/27` и `6 без статуса`.
  В probe использовано позднее прямое решение.
- Student-эталон показывает текущую пару `10:40–12:10`, а живой ПК-111
  старосты — `18:00–19:30`, «Информационные системы», `А-401`. После
  readback все четыре кадра сценария синхронизированы с headman-источником.
- Baseline pages 16–24: `6412 A`, `2190` прямых instances. Page 25 до записи
  отсутствовала.

### 4. Что уже существовало и переиспользовано

- Живая визуальная грамматика page 24: Onest, role-pill, solid current lesson,
  grid surface, bottom nav 358×76 с inset 16 px.
- `shared/MobileBottomNav`, `shared/AppButton`, status badges `+ / у / н / —`,
  Back, room/lesson metadata и системные nav-иконки.
- Семантические цвета, поверхности, радиусы и типографические стили библиотеки.

### 5. Каких данных или прав действительно нет

- Нет подтверждённого atomic draft commit и server receipt.
- Нет подтверждённого group/lesson revision contract для merge/conflict.
- Нет effective permission payload для старосты и помощника.
- Нет точной следующей пары в live fixture ПК-111.
- Не подтверждены remove mark, ручная причина `у` и commit с тремя оставшимися
  `Без статуса`.

### 6. Локальные блокеры

- Точная следующая пара блокирует только её данные на Today, не композицию.
- ACK/retry/offline блокируют только postcommit/error states, не precommit
  review.
- Права помощника блокируют read-only/forbidden state, не основной headman
  happy path.

## Функциональная карта

- Current lesson: `lessonId`, время, предмет, тип, аудитория, active state.
- Roster: 27 участников, status `+ / у / н / нет данных`, source/time/author,
  reason/ticket для `у`.
- Derived: `21/27`, `6 без статуса`, очереди `6 / 2 / 1`, local diff, undo,
  lesson/group revision.
- Today открывает журнал и очереди; roster фильтрует и раскрывает максимум одну
  строку; focus-deck ускоряет последовательные индивидуальные действия.
- `у` применяется только после причины; repeat-remove и swipe не показаны как
  единственный путь.
- Review группирует четыре diff, перечитывает `rev. 44 / 28`, показывает
  изменение состава и оставшиеся три пустых статуса.
- Production commit должен идти `explicit action → sending → server ACK`;
  «Синхронизировано» допустимо только после ACK.
- Базовое право — `mark_attendance`; `manage_excuses` относится к заявкам,
  а не к самой ручной отметке.

Документально подтверждены roster reads и одиночный `PUT` статуса. Atomic
batch/draft, revision, conflict payload, receipt и idempotency ещё не
подтверждены.

## Совет: варианты и критика

1. **Список с раскрытием строки — 12 тапов.** Понятен, но четыре исключения
   требуют лишних входов в строки.
2. **Список + focus deck — 9 тапов.** Сохраняет полный roster, снижает риск
   ошибки и ускоряет последовательную обработку. Выбран.
3. **Постоянный rail `+/у/н` — 8 тапов.** Быстрее на один тап, но перегружает
   27 строк и повышает вероятность случайной отметки.

Первичная атака критика нашла неоднозначный Undo и обещание «черновик
сохранён». Undo перенесён в карточку последнего изменения как цель 334×44 с
подписью «Отменить последнее»; review теперь говорит только «Черновик не
отправлен». Два других замечания сняты после сверки с прямым prompt: принятый
role-pill обязан повторять page 24, а одна раскрытая строка и focus-deck — два
явно разрешённых индивидуальных пути. Финальный verdict критика: blockers 0.

## Выбранное решение и tap budget

`Сегодня → roster → focus deck → review → commit`.

1. Открыть журнал.
2. Открыть focus deck.
3–6. Поставить четыре индивидуальных статуса.
7. Выбрать причину для `у`.
8. Перейти к review.
9. Явно отправить четыре изменения.

Auto-advance происходит только после явного локального выбора. Undo отменяет
последнее изменение и возвращает предыдущего студента. Silent commit нет.

## PWA / TMA ownership

| Поверхность | Класс | Back | Главное действие | Bottom nav | Keyboard / safe area |
|---|---|---|---|---|---|
| Today | root | browser/system | product CTA | product nav | nav скрывается при keyboard; `max(16px, env(...))` |
| PWA roster/deck/review | task/editor | один product Back | sticky product action | скрыт | action не перекрывает поле; inset `max(16px, env(...))` |
| TMA root | root | native Back/MainButton скрыты | product CTA | product nav | Telegram viewport + safe area |
| TMA task/editor | task/editor | Telegram BackButton | Telegram MainButton | скрыт | native MainButton и keyboard adapter |

В текущем каноническом probe визуализирован PWA standalone. TMA и keyboard
fixtures входят только в следующий шаг после приёмки.

## Что убрано с мобильного probe

- Desktop top-nav, две колонки, независимые pair pagers и activity-log.
- Полный editor домашнего задания, подробные заявки, статистика и история.
- Lesson block management и прошлые/будущие пары.
- Bulk, Checkbox, swipe-only, silent commit и offline auto-send.

Routes не удалены: перечисленное отложено до следующих семейств или состояний.

## Иконки и компоненты

Переиспользованы:

- `icon/today` `4161:143`, `icon/homework` `4162:143`;
- `icon/attendance` `4163:143`, `icon/profile` `4163:844554`;
- `icon/requests` `4164:143`, штатная ветка «Ещё» MobileBottomNav;
- `icon/room` `4565:3068`, `icon/lesson-type` `4570:143`;
- Back `1224:7531`;
- AttendanceStatusBadge variants `513:22046 / 22050 / 22054 / 22062`;
- `shared/MobileBottomNav` set `4175:403`.

Новых semantic icon masters: 0. Публичных компонентов: 0. Дельта закрытого
`COMPONENT_REGISTRY`: 0 строк.

## Состав и A кадров

| Кадр | Состав | A |
|---|---|---:|
| `20 · Сегодня / default` `4997:142` | role switch, current, queues, next-data state, homework action, bottom nav | 36 |
| `21 · Учёт / roster-list` `5001:216` | Back, context, 4 filters, viewport, 27 rows, 1 expanded row, sticky deck CTA | 163 |
| `21 · Учёт / focus-deck` `5003:298` | Back, draft, 1 student, 3 explicit actions, last change + Undo, review CTA | 26 |
| `21 · Учёт / review-commit` `5006:308` | Back, context, revision warning, 3 groups/4 diff, remaining 3, commit | 29 |

Page 25 — `257 A`: board `257 A`; family 20 `37 A`; family 21 `219 A`.
Board: `1322×1844`; экраны: `390×844`.

## Геометрические изменения внутри probe

- Today: screen 390×844 не менялся. Два неподтверждённых next-lesson metadata
  group удалены: `42 → 36 A`; вместо выдуманной пары показан data-gap state.
- Roster: геометрия и `163 A` не менялись; заменены только текущая пара,
  sources и времена `10:40…35:45 → 18:03…18:31`.
- Focus: last-change card `358×56 → 358×112`; Undo перемещён из header
  `88×44` в карточку как `334×44`; внекадровая устаревшая строка удалена,
  `27 → 26 A`.
- Review: radii визуально не изменились, но получили bindings
  `radius/2xl / xl / full`; дублирующая нижняя строка удалена,
  `30 → 29 A`.
- Общие masters: 0 тронутых, поэтому consumer count до/после не менялся.

## Токены, литералы и смысловые bindings

Проверены и сопоставлены:

- `color/surface/base`, `raised`, `float` — screen, dense cards, controls;
- `color/text/primary`, `secondary`, `muted` — иерархия текста;
- `color/accent/now`, `on-now`, `soft` — current lesson, selected nav и CTA;
- status present/excused/absent/none — символ + слово + форма;
- `radius/2xl` `VariableID:1504:37363` — screen 24 px;
- `radius/xl` `VariableID:12:17` — cards 16 px;
- `radius/full` `VariableID:12:18` — Back/CTA 999 px.

Локальные solid-fill literals: 0. Локальные radius literals: 0. Локальные
border literals: 0. Привязок не по смыслу: 0. Back master/screen stroke —
`2 / 2 px`; metadata master/screen stroke — `1 / 1 px`; metadata instance box
— 10×10 px. Непокрытых токеном мест внутри probe: 0; token requests и
временные token fallback: 0. Существующий открытый вопрос scope
`radius/2xl = ALL_SCOPES` этой партией не менялся.

## Закрывающий QA

- Выходы физических A за 390×844: 0.
- Дефекты clipping физических A: 0. Два развёрнутых Plugin API сигнала внутри
  масштабированных metadata instances не являются отдельными A; instance boxes
  10×10 и visual render чисты.
- Намеренный вертикальный overflow roster: content 1620 px внутри viewport
  358×552; overflow 1068 px, `clipsContent=true`.
- Прямоугольные подложки под rounded elements: 0.
- Text overflow: 0; случайные ellipsis: 0; Inter: 0; missing fonts: 0.
- Touch targets <44×44: 0. Back 44×44; Undo 334×44; sticky actions 358×48.
- Двойной Back: 0; двойная нижняя панель: 0; task bottom nav: 0.
- Root nav bottom inset: 16 px; последняя строка sticky CTA не перекрыта.
- Статусы только цветом: 0; bulk/Checkbox strings: 0.
- Shadows: 0; glow: 0; layer blur: 0; разрешённый nav background blur: 1.
- Roster: 27 ФИО, 18 source/time, 3 ticket/reason, 6 row states без статуса,
  одна раскрытая строка, три индивидуальных действия.
- Review: `rev. 43 / 27 → rev. 44 / 28`, четыре diff, три без статуса,
  commit 1, sync claims 0.
- Финальный независимый visual QA субагента: blockers 0.

Long-ФИО, 30 студентов, increased contrast, compact TMA, keyboard и platform
matrix не подменялись счётчиками: это прямо отложенные post-acceptance fixtures.

## Protected baseline до / после

| Page | A до/после | Instances до/после | Hash до/после |
|---|---:|---:|---|
| 16 auth | 890 / 890 | 390 / 390 | `455cd388 / 455cd388` |
| 17 student | 748 / 748 | 451 / 451 | `5bc52169 / 5bc52169` |
| 18 headman desktop | 836 / 836 | 528 / 528 | `0acf793c / 0acf793c` |
| 19 teacher | 363 / 363 | 231 / 231 | `96e171a8 / 96e171a8` |
| 20 admin | 286 / 286 | 176 / 176 | `401f7db5 / 401f7db5` |
| 21 icons | 125 / 125 | 0 / 0 | `54da91e4 / 54da91e4` |
| 22 mobile foundations | 229 / 229 | 12 / 12 | `dea5888a / dea5888a` |
| 23 mobile components | 796 / 796 | 112 / 112 | `a83b3f15 / a83b3f15` |
| 24 mobile student | 2139 / 2139 | 290 / 290 | `46382170 / 46382170` |

Итого: `6412 / 6412 A`, `2190 / 2190` instances; ожидаемая и фактическая
дельта pages 16–24 — `0 A`, `0` изменённых hashes, `0` геометрических
изменений.

## Renders

- `_work/renders/headman-mobile/20-today-default.png`
- `_work/renders/headman-mobile/21-roster-list.png`
- `_work/renders/headman-mobile/21-focus-deck.png`
- `_work/renders/headman-mobile/21-review-commit.png`
- `_work/renders/headman-mobile/probe-board.png`
- reference: `reference-student-today-live.png`, `reference-pc-111-live.png`.

## MCP / transport ledger

- Всего Figma MCP: 87 вызовов.
- `whoami 1`, `get_libraries 1`, `search_design_system 1`, `get_metadata 2`.
- `use_figma 67`: read-only 50, успешных mutation 16, безопасный script reject
  до записи 1.
- `get_screenshot 15`.
- `429: 0`; timeout: 0; Figma transport failures: 0; partial mutations: 0.
- Asset download failures: 1; короткий URL успешно повторён через curl с
  отключённой Schannel certificate verification.
- Один script reject искал строку `Г-А-401`; live tree хранит `А-401` плюс
  отдельную room-icon. Canvas не изменился, повтор был идемпотентным.

## Открытые вопросы владельцу

1. Дать точные данные следующей пары после 19:30 либо принять текущий data-gap
   state для первого probe.
2. Подтвердить atomic commit четырёх diff с base lesson/group revision,
   idempotency key, structured conflict и receipt.
3. Решить merge-policy при изменении состава `27 → 28`.
4. Подтвердить payload причины `у`, снятие отметки и последовательный Undo.
5. Подтвердить effective permissions старосты/помощника и commit при трёх
   оставшихся без статуса.

## Что не сделано и почему

- Не построены 360/430/320, PWA browser, TMA, keyboard, loading/error,
  offline/stale/conflict/ACK, long ФИО, 30 students, increased contrast —
  запрещены до приёмки первого 390 px probe.
- Не построены семейства 22–29 и общий «Ещё» — остановка после probe.
- Не собрана light theme; библиотека не опубликована.
- Не изменены common masters, tokens, Checkbox и page 24.
- Не изменён `COMPONENT_REGISTRY`; новых публичных masters нет.

## Изменённые файлы

- `_work/prompts/part-18-headman-mobile.md`: строки 1–107.
- `journal/state/part-18-headman-mobile-before-2026-09-02.json`: строки 1–25.
- `journal/state/part-18-headman-mobile-after-2026-09-02.json`: строки 1–51.
- `journal/reports/2026-09-02-part-18-headman-mobile.md`: строки 1–306.
- `design/figma-spec-mobile.md`: строки 399–420.
- `journal/DECISIONS.md`: строки 2352–2370.
- `journal/to-owner.md`: строки 786–804.

PNG-renders в `_work/renders/headman-mobile/` — бинарные файлы без номеров
строк: четыре кадра, общий board и два read-only reference render.

## ✏️ 02.09.2026 — canonical bank, checkpoint по просьбе владельца

### Ответ владельцу и граница остановки

Владелец подтвердил breadth-first порядок: сначала связный канонический банк
390×844 dark, затем совместная редактура. Во время сборки владелец попросил
завершить активную атомарную запись и поставить работу на паузу. Запись
`23 · Домашнее задание / create` завершена, независимо прочитана и
отрендерена; после неё новые продуктовые кадры не создавались.

### Совет и выбранный состав

Совет сравнил компактный, полный и минимально-полный варианты. Выбран банк из
21 нового кадра: root/detail/task/editor только там, где они нужны для
понимания перехода; root владеет bottom nav, PWA detail/task — одним Back и
при необходимости одним sticky action. Tap budget: root 1, detail 2,
editor/confirm 3, commit 4 касания от канонического входа.

Критика принята в части owner-fixture прав, разделения шаблона расписания и
конкретной пары, двух отдельных confirm заявок, отсутствия route/room search
на карте и отсутствия ложных ACK. Предложенный critic кадр
`28 / cancelled-result` не принят до решения противоречия: спека говорит
«отметки недействительны, но сохранены», `COMPONENT_REGISTRY` — «отметки
стираются». Этот пункт локально остановлен.

### Что записано в Figma

- Board `4996:143`: `1322×1844 → 1736×9908 px`, имя изменено на
  `M25 / Headman mobile canonical bank`.
- Existing row `screens/20`: `y 52 → 948 px`; `screens/21`:
  `y 948 → 1844 px`. Их внутренние экраны не менялись.
- Созданы пустые host-строки `screens/19`, `screens/22` … `screens/29` с
  шагом по Y `896 px`; максимум строки — `1632 px` (`4×390 + 3×24`).
- Создано пять новых продуктовых кадров:

| Кадр | A | Instances | Text | Результат QA |
|---|---:|---:|---:|---|
| `19 · Ещё / headman` `5033:316` | 33 | 1 | 24 | clean |
| `22 · Статистика / overview` `5035:350` | 39 | 1 | 27 | clean |
| `22 · Статистика / student-detail` `5036:384` | 47 | 1 | 29 | clean |
| `23 · Домашнее задание / actual` `5037:385` | 31 | 1 | 21 | clean после 44-px tab fix |
| `23 · Домашнее задание / create` `5039:419` | 22 | 1 | 14 | clean после text-overlap fix |

На паузе page 25: `438 A`, `46` instances, `226` text, `166` frames,
geometry hash `88270396`. Дельта от bank-before: `+181 A`, `+5` instances,
`+115` text, `+61` frames. Из `+181 A` пять экранов дают `172 A`, девять
host-строк — `9 A`.

### Визуальная проверка и исправления

- `23 / actual`: интерактивные tab-слои были 36 px; исправлены до 44 px,
  tab-container `44 → 52 px`, нижний контент сдвинут на 8 px.
- `23 / create`: metadata двух полей пересекалась со значением; высота
  `Пара` и `Ссылка` `72 → 80 px`, последующие блоки сдвинуты на 8/16 px.
  Текстовая колонка `Пара` ограничена 300 px для clearance chevron.
- Финальные суммарные проверки пяти новых кадров: screen overflow 0,
  clipping overflow 0, target `<44×44` 0, text overlap 0, ellipsis 0,
  missing fonts 0, Inter 0, shadows 0, blur вне glass-nav 0, unbound solid
  fills 0, double Back 0, double bottom bar 0. Nav/sticky inset — 16 px.

Renders:

- `_work/renders/headman-bank-19-more.png`;
- `_work/renders/headman-bank-22-overview.png`;
- `_work/renders/headman-bank-22-student.png`;
- `_work/renders/headman-bank-23-actual.png`;
- `_work/renders/headman-bank-23-create.png`.

### Переиспользование и токены

Переиспользованы `shared/MobileBottomNav` (`4175:353`), `icon/previous`
(`1224:7531`) и существующие nav-icons today/attendance/requests/more/profile.
Новых semantic icons и общих masters не создано.

Проверены и применены привязки `color/surface/base`, `raised`, `float`;
`color/text/primary`, `secondary`, `muted`; `color/accent/now`, `soft`,
`on-now`; status present/excused/absent; `radius/xl`, `full`, `2xl`.
Непривязанных solid-fill в новых кадрах — 0. Token-gap на построенных пяти
кадрах не найден; запросов на новые токены до приёмки не создавалось.

### Protected baseline

Счётчики pages 16–24 совпадают: `6412 / 6412 A`, `2190 / 2190` instances,
дельта `0 A`. Hash pages 17–24 совпадает. У page 16 при тех же `890 A` и
`390` instances обнаружено `455cd388 → 4ca4a8f4`. Ни один mutation этой
сессии не адресовал page 16; откат вслепую не выполнялся. При возобновлении
первое действие — отдельный read-only diff page 16 до любой записи.

### MCP / transport checkpoint

Продолжение после probe: 50 Figma-вызовов — `get_libraries 1`,
`search_design_system 1`, read-only `use_figma 31`, mutation `use_figma 9`,
`get_screenshot 8`. `429 0`, timeout 0, transport failures 0, partial
mutations 0. Две mutation-сессии завершились медленно через session wait, но
вернули полный результат; слепых повторов не было.

### Точка продолжения

1. Read-only diff page 16 относительно hash `455cd388`.
2. Следующий продуктовый кадр — `24 · Карта / default`, host `5032:319`.
3. Затем семейства 25–29 по уже созданным пустым строкам.
4. Не создавать `28 / cancelled-result`, пока владелец не разрешит конфликт
   хранения отметок после отмены пары.
5. Не строить size/platform/state matrix до общего просмотра банка.

### Изменённые файлы continuation-checkpoint

- `_work/prompts/part-18-headman-mobile.md`: строки 108–135.
- `journal/reports/2026-09-02-part-18-headman-mobile.md`: строки 308–421.
- `journal/DECISIONS.md`: строки 2372–2388.
- `journal/to-owner.md`: строки 806–816.
- `journal/state/part-18-headman-mobile-bank-pause-2026-09-02.json`:
  строки 1–91.

Пять PNG-renders в `_work/renders/` — бинарные файлы без номеров строк.
`design/figma-spec-mobile.md`, токены, реестр компонентов и specs в этом
продолжении не изменялись: банк ещё не прошёл общий просмотр владельца.

✏️ Уточнение диапазона после добавления этого списка: continuation-запись
отчёта занимает строки 308–427.

## ✏️ 02.09.2026 — canonical bank 19–29 завершён

### Ответы на вопросы

- Владелец подтвердил breadth-first порядок: собран весь связный 390×844
  dark-bank, затем работа остановлена для совместной визуальной редакции.
- Transient pause-hash page 16 не воспроизвёлся: два независимых closing-pass
  дали исходный 455cd388. Откат и любые writes в pages 16–24 не выполнялись.
- Противоречие об отмене пары локализовано: показаны detail и pre-submit
  confirm, но нет result-state и утверждения о физическом удалении отметок.
- Блокирующих всю партию вопросов не осталось; server gaps перечислены ниже
  и блокируют только production states или отдельные mutations.

### Функциональная карта и выбранная IA

| Семейство | Канонический маршрут | Обязательное | Не перенесено с desktop |
|---|---|---|---|
| 19 Ещё | root → раздел | Разделы, Управление, server badges | pin/reorder и выдуманные counters |
| 20 Сегодня | root | current/next, progress, revision, queues | каталог всех десяти разделов |
| 21 Учёт | roster → focus → review | 27 строк, +/у/н, undo, explicit commit | bulk, Checkbox, silent/offline commit |
| 22 Статистика | overview → student | две метрики, period, drilldown | ложный live export и fixture-as-data |
| 23 Домашнее | actual → create/editor | актуальное, архив, publish/save | неподтверждённое право только автора |
| 24 Карта | detail | корпус/этаж и честный empty | поиск, маршрут, pins и fake floor plan |
| 25 Группа | root → student → permissions | roster, sensitive gate, rights | add/edit student, reset password, export |
| 26 Предметы | root → editor | subject×type×teacher model | global suggestions, unsafe delete |
| 27 Конструктор | template → slot editor | day, parity, assignment, room | dated transfer и desktop grid |
| 28 Пары | agenda → detail → transfer/cancel | dated lesson, reason, pre-submit diff | bulk cancel и post-result claims |
| 29 Заявки | queues → detail → approve/reject | отдельные у/н, file, irreversible confirm | unified fake API, bulk и optimistic ACK |

Совет сравнил компактный, полный и минимально-полный банки. Выбран
минимально-полный: root плюс только необходимый detail/task/editor. Критика
отклонила постоянный status rail в журнале, desktop-grid конструктора,
единый смешанный список заявок и любой result с неподтверждённым ACK.

### Переходы и tap budget

- Журнал: Today → roster → focus, четыре отметки, причина у, review, commit —
  9 taps.
- Домашнее: More → ДЗ → создать/выбрать → save — 3 taps плюс ввод.
- Конструктор: More → builder → slot → subject/type/assignment → room → save —
  10 taps плюс ввод аудитории.
- Управление парой: agenda 1; detail 2; transfer submit 8; cancel confirm
  5 плюс ввод причины.
- Заявки: detail 2; approve confirm 4; reject confirm 5 плюс ввод.

Root владеет product bottom nav. PWA detail/task/editor владеет одним Back и
одним sticky action только для commit. TMA заменяет их Telegram
BackButton/MainButton. Keyboard скрывает nav; bottom inset равен 16 px плюс
safe area. Platform-adapter пока описан, но отдельными fixtures не построен.

### Состав и A каждого кадра

| Кадр | A |
|---|---:|
| 19 · Ещё / headman 5033:316 | 33 |
| 20 · Сегодня / default 4997:142 | 36 |
| 21 · Учёт / roster-list 5001:216 | 163 |
| 21 · Учёт / focus-deck 5003:298 | 26 |
| 21 · Учёт / review-commit 5006:308 | 29 |
| 22 · Статистика / overview 5035:350 | 39 |
| 22 · Статистика / student-detail 5036:384 | 47 |
| 23 · Домашнее задание / actual 5037:385 | 31 |
| 23 · Домашнее задание / create 5039:419 | 22 |
| 24 · Карта / default 5050:142 | 6 |
| 25 · Группа / root 5058:142 | 128 |
| 25 · Группа / student-detail 5061:142 | 14 |
| 25 · Группа / permissions 5063:142 | 26 |
| 26 · Предметы / root 5069:569 | 32 |
| 26 · Предметы / editor 5075:617 | 28 |
| 27 · Конструктор расписания / root 5085:652 | 79 |
| 27 · Конструктор расписания / slot-editor 5087:701 | 36 |
| 28 · Управление парами / agenda 5107:142 | 23 |
| 28 · Управление парами / active-detail 5121:142 | 12 |
| 28 · Управление парами / cancelled-detail 5124:142 | 16 |
| 28 · Управление парами / transfer 5131:142 | 20 |
| 28 · Управление парами / cancel-confirm 5135:142 | 15 |
| 29 · Заявки / queue 5145:142 | 29 |
| 29 · Заявки / detail 5152:142 | 20 |
| 29 · Заявки / approve-confirm 5155:142 | 18 |
| 29 · Заявки / reject-confirm 5157:142 | 20 |

Screen sum 948 A. Board и 11 family-hosts дают ещё 12 A; page 25 итого
960 A, 128 instances, 496 text, 336 frames, hash 46185e3e.

### Геометрия и защищённый контур

- Board 4996:143: 1322×1844 → 2150×9908 px относительно bank-before.
- Все продуктовые кадры: 390×844 px; один семейный ряд = одно вертикальное
  семейство, действия внутри ряда идут горизонтально с gap 24 px.
- Page 25: 257 → 960 A, дельта +703 A. От pause: 438 → 960 A, +522 A.
- Общие masters: 0 изменений; consumer counts до/после не менялись.
- Pages 16–24: 6412 / 6412 A, 2190 / 2190 instances; все девять geometry
  hashes совпадают. Ожидаемая и фактическая дельта: 0 A и 0 геометрических
  изменений.

### Компоненты, иконки и токены

Переиспользованы MobileBottomNav 4175:353, icon/previous 1224:7531,
существующий icon/next, StepPager, AppButton, TextInput и SelectField.
Bottom-nav повторно использует today, attendance, requests, more и profile.
Новых semantic icons и публичных masters создано 0.

Проверены semantic places и найдены существующие соответствия:
surface base/raised/float; text primary/secondary/muted; accent now/soft/
on-now; status present/excused/absent/none; radius xl/full/2xl; существующие
control states default/pressed/disabled/danger. Непривязанных solid fills,
случайных radius/border literals и привязок не по смыслу в финальных кадрах
0. Непокрытых semantic places 0; token requests 0; временных token fallback
0. Tokens и COMPONENT_REGISTRY не изменялись.

### Финальный QA числами

- 26/26 кадров имеют final readback и render; открытых blockers 0.
- Screen overflow 0; clipping overflow 0; text overlap 0; случайные ellipsis
  0; missing fonts 0; Inter 0.
- Touch targets меньше 44×44: 0. Double Back 0; double bottom bar 0;
  task-nav 0; root nav/sticky inset 16 px.
- Статусы только цветом 0; bulk/Checkbox 0; ложные saved/sync/ACK/outbox
  claims 0.
- Shadows 0; glow 0; layer blur вне разрешённого glass-nav 0.
- Density fixtures: roster 27 студентов; group 30 строк, девять полных и
  44 px десятой; subjects 5 карточек; builder 8 пар, четыре и край пятой;
  agenda 4 строки; tickets 2 независимые очереди.
- Long ФИО/предметы в построенных fixtures не переполняются. Dedicated stress
  long-name/320/contrast остаётся отдельным pass и не подменён этим числом.

### MCP и transport ledger

Независимый подсчёт по raw Codex JSONL после команды владельца продолжить:
145 Figma MCP calls — use_figma 106, search_design_system 8,
get_screenshot 31. За всю задачу raw transcript содержит 261 фактический
Figma-вызов: whoami 1, get_libraries 2, get_metadata 2,
search_design_system 10, use_figma 195, get_screenshot 51.

Эта единица — фактическая nested MCP invocation; она заменяет промежуточные
ручные attempt/session counters 87 и 50, которые нельзя складывать с raw
ledger. После возобновления: 429 0, timeout 0, transport failures 0,
partial mutations 0. Было 9 безопасных script/runtime rejects: precondition
карты, два read-only component-property запроса, invalid overflow enum,
unsupported setPluginData, неверная paint binding, invalidated nested handle,
строковая y-coordinate и Tabs variant read. Каждый mutation reject проверен
readback; слепых повторов и дублей 0.

### Renders

Индивидуальные PNG каждого нового кадра находятся в _work/renders/.
Обзорные полосы:

- _work/renders/headman-bank-28-family.png;
- _work/renders/headman-bank-29-family.png.

### Открытые вопросы и что не сделано

Server gaps: multi-diff attendance revision/idempotency; statistics aggregates;
homework linkage/audit; floor assets; sensitive permissions; subject atomic
save/delete; schedule slot/parity/revision; transfer/room/cancel cascade;
ticket revision, assistant rights и reject payload. Они не блокируют
визуальный обзор, но блокируют post-submit/offline/conflict fixtures.

Не построены 360/430/320, PWA browser, TMA, compact/expanded, keyboard,
loading/empty/error, offline/stale/conflict/ACK, increased contrast и light.
Не публиковалась библиотека; не менялись page 24, desktop, общие masters,
tokens, COMPONENT_REGISTRY, Checkbox и bulk. Следующий этап начинается
только после комментариев владельца.

### Изменённые файлы

- _work/prompts/part-18-headman-mobile.md: строки 137–149.
- design/figma-spec-mobile.md: строки 422–448.
- journal/DECISIONS.md: строки 2390–2410.
- journal/to-owner.md: строки 818–845.
- journal/state/part-18-headman-mobile-bank-after-2026-09-02.json: строки 1–77.
- journal/reports/2026-09-02-part-18-headman-mobile.md: строки 429–601.
- Два overview PNG в _work/renders/ — бинарные файлы без номеров строк.

✏️ Уточнение диапазона: финальная continuation-запись отчёта занимает строки
429–604, включая это уточнение; прежнее значение 429–601 выше неактуально.

✏️ Финальная граница после этой append-only поправки: строки 429–607 включительно.

✏️ Contact sheet: _work/renders/headman-bank-overview-390-dark.png; итоговый диапазон записи — 429–609.
