# figma-spec-mobile.md

> ✏️ 01.09.2026. Дополнение к `design/figma-spec.md` для PWA и Telegram
> Mini App. Основная Figma-спека продолжает действовать во всём, что здесь
> не переопределено. Живой файл главнее этого документа в числах.

## 1. Граница продукта

PWA и Telegram Mini App — один mobile-продукт с общим продуктовым ядром и
разными оболочками. Mobile не является адаптивом desktop-экранов: из ПК
берутся работа, данные, действия и состояния, но не раскладка. Desktop-страницы
16–20 не являются mobile-мастерами и в mobile-партиях не меняются.

В файле принята последовательность страниц:

- `21. Иконки — системные` — единственные владельцы новых mobile-смыслов;
- `22. Основания — mobile` — shell, adapter zones, material probes;
- `23. Компоненты — mobile` — product masters и QA fixtures;
- далее по две страницы blocks/screens на роль в порядке student → headman →
  teacher → admin.

Figma пишет один последовательный сборщик. Анализ и критика могут идти
параллельно, запись — нет. После каждой мутации выполняется независимый
readback; при транспортном обрыве мутация не повторяется вслепую.

## 2. Mobile shell

`Mobile shell` — контракт композиции, не публичный продуктовый компонент и
не набор отдельных PWA/TMA-копий. Он разделён на восемь runtime-зон:

1. `hostChrome` — браузерный или Telegram chrome;
2. `primaryAction` — владелец основного действия;
3. `viewport` — dynamic/stable viewport;
4. `safeArea` — системные отступы;
5. `keyboard` — открытие, закрытие и восстановление layout;
6. `theme` — источник темы и её приоритет;
7. `storageOffline` — local draft, outbox, ACK и конфликт;
8. `filesLinks` — open/download/external и capability fallback.

В Figma foundation эти зоны показаны как спецификационные карточки и шесть
составных кадров, а не как восемь component variants. Runtime-параметры
(`safeAreaInset`, `contentSafeAreaInset`, `viewportStableHeight`, тема клиента)
не превращаются в фиксированные значения макета.

### 2.1. Матрица оболочек

| Режим | Back | Основное действие | Product dock |
|---|---|---|---|
| PWA browser · root | browser | product в контенте | показан |
| PWA standalone · task | product | product sticky | скрыт |
| TMA overview · compact | скрыт | product в контенте | показан |
| TMA overview · expanded | скрыт | product в контенте | показан |
| TMA task · compact | Telegram `BackButton` | Telegram `MainButton` | скрыт |
| TMA task · expanded | Telegram `BackButton` | Telegram `MainButton` | скрыт |

В каждом состоянии у Back не больше одного владельца. Внизу одновременно
существует только один слой действия или навигации. Telegram host controls
показываются в QA как аннотация контракта и не имитируются продуктовым
компонентом.

### 2.2. Клавиатура и safe area

- При открытой клавиатуре product dock скрыт.
- В PWA действие остаётся в пересчитанном продуктом viewport и не перекрывает
  поле или inline error.
- В TMA `MainButton` скрывается на время ввода и возвращается после blur/Back,
  когда пересчитаны stable viewport и insets.
- Значения safe area приходят от платформы; фиксированного нижнего inset в
  компоненте нет.

## 3. Публичные mobile-компоненты

### 3.1. `shared/MobileBottomNav`

Публичная ось только одна: `slots=3|4|5`. В коде число слотов выводится из
`items.length`; второй источник истины не создаётся. Активный пункт — данные
`activeId`, а не variant. `platform`, `role`, `theme`, `material`, `hidden` и
`keyboard` вариантами не становятся.

Контракт данных пункта: `id`, русская подпись, иконка, маршрут, необязательный
badge. Видимостью всей панели владеет shell: root/overview показывает,
detail/editor/task и keyboard-open скрывают.

Живая геометрия foundation: высота 76 px, внешний padding 8 px, gap 4 px.
При stress viewport 320 CSS px пять интерактивных областей имеют минимум
57,6 × 60 CSS px; все цели не меньше 44 × 44 CSS px, подписи не переносятся.

`__MobileBottomNavItem` — приватный helper. Матрица:
`selected=false|true` ×
`interaction=default|hover|pressed|focused`, всего 8 вариантов. Размер
варианта 64 × 60 px, вертикальный padding 4 px, горизонтальный 0, gap 4 px.
Прямое использование этого helper на экранах запрещено.

### 3.2. `shared/MobileSyncStatus`

Матрица:

- `presentation=inline|banner`;
- `state=local-draft|outbox|sending|synced|conflict`.

Всего 10 вариантов. `synced` показывается только после server ACK. Статус не
заменяет `OfflineState`, `ErrorState` или `LiveNotificationRegion`.
`stale`, `freshness`, `asOf`, `serverTime`, timestamp и `freshness-mark` не
добавляются до отдельного решения политики свежести.

Статус local/outbox не применяется к самой отметке посещаемости: mobile
attendance не имеет offline-очереди. Он допустим для комментариев, обращений,
файлов и других форм, где локальное сохранение действительно поддержано.

## 4. Иконки

Перед созданием ищется существующий владелец смысла в живом файле. Новый
мастер создаётся только для нового смысла, простой геометрией и без растра.
Foundation добавил 14 masters в боксе 16 × 16:

`today`, `homework`, `attendance`, `profile`, `requests`, `statistics`, `map`,
`overview`, `users`, `groups`, `local-draft`, `outbox`, `sync`, `conflict`.

Один смысл — один мастер. Иконка без подписи допустима только при однозначном
смысле; в bottom nav всегда остаётся короткая русская подпись.

## 5. Материалы PWA/TMA

Решение владельца отменяет общий запрет на gradients/glow только для mobile:
в PWA/TMA допустимы градиенты, glow, многослойные тени и несколько подсвеченных
областей на одном экране. Это не переносится на desktop и не означает
переиспользование auth-glass.

До выбора визуальной пробы действуют ворота:

- product masters и product screens используют существующие semantic variables
  и solid fallback;
- литеральные gradient/glow-значения живут только в явно подписанных QA-пробах;
- плотные данные, формы и графики остаются на непрозрачных solid-поверхностях;
- никакие QA-литералы не считаются token contract;
- новые variables, aliases, styles и ось `material` не создаются.

Три foundation-пробы:

- A — restrained solid, безопасный production fallback;
- B — layered illumination, QA-кандидат на выразительный mobile-язык;
- C — Telegram-native solid с явно показанным host chrome.

После выбора владельца запросы на токены выносятся одним пакетом. До выбора
значения не фиксируются в `tokens-v2.json` и не создаются в Figma.

## 6. Радиусы, привязки и известный долг

Разрешённые продуктовые радиусы mobile: 10 / 12 / 16 / 24 / full. В product
masters должны быть semantic bindings по смыслу; отсутствие литерала само по
себе не доказывает правильность привязки.

`radius/2xl` (`VariableID:1504:37363`) использован для 24 px, но в живом файле
имеет scope `ALL_SCOPES`. Foundation это не исправляет: изменение scope —
отдельный token/repair gate с проверкой всех потребителей.

## 7. Запреты и отдельные ворота

- Массовая отметка посещаемости запрещена во всех ролях и поверхностях.
  `POST /attendance/marks/batch`, «отметить всех», выбор строк для batch и
  модель «все присутствуют, указать исключения» не используются.
- Отметка посещаемости только индивидуальная, с server ACK; optimistic
  increment и offline queue отсутствуют.
- Два существующих владельца `shared/Checkbox` исследуются read-only.
  Foundation не выбирает канонический мастер, не merge/delete/swap и не
  перенаправляет потребителей. Ремонт требует отдельной партии.
- Светлая mobile-тема, ролевые экраны и публикация в foundation не входят.

## 8. QA-контракт кадра

Перед приёмкой каждого mobile-кадра численно проверяются:

- overflow относительно кадра и родителя по обеим осям;
- текстовые переполнения, лишние многоточия и переносы внутри слов;
- цели нажатия не меньше 44 × 44 CSS px;
- первый действенный элемент в compact TMA viewport;
- отсутствие двойного Back и двойной нижней панели;
- отсутствие перекрытия последней строки плавающими элементами;
- радиусы и прямоугольные подложки под скруглёнными слоями;
- привязки по смыслу, а не только наличие variable binding;
- русский текст и Onest, отсутствие литерального `Inter`.

QA fixtures не входят в product masters. После числового прохода обязателен
отдельный визуальный просмотр render: чистые счётчики не заменяют проверку
иерархии, смысла подписи и владельца действия.

## 9. Порядок следующей работы

После выбора material probe и отдельного решения token batch запускается
student probe «Сегодня → текущая пара → отметка». Он собирается на 360, 390,
430 и stress 320, для PWA browser/standalone и TMA overview/task, включая
default, scrolled, pressed, loading, empty, error и keyboard-open. После probe
партия останавливается на показ владельцу; остальные экраны роли до приёмки
не собираются.

## ✏️ 01.09.2026 — принятый mobile visual contract после foundation repair

Эта запись дополняет разделы 3–5 после просмотра владельцем живых проб.

1. Выбран гибрид A+B. A задаёт solid-иерархию и fallback; B даёт illumination
   текущей пары. C задаёт способ размещения внутри Telegram chrome, но не
   является отдельной темой продукта.
2. На тёмных mobile-экранах текущее состояние связано с color/accent/now.
   Для нижней навигации active surface = accent/now, active foreground =
   accent/on-now, inactive foreground = text/primary. Активная навигация
   может быть самым сильным акцентом экрана.
3. В MobileBottomNavItem иконка и подпись имеют общий горизонтальный центр.
   Все интерактивные области сохраняют минимум 44×44 CSS px.
4. Все 25 VECTOR-контуров 14 системных mobile-иконок page 21 имеют
   strokeWeight 1 px и binding border/hairline. Это позднее решение владельца
   заменяет foundation baseline 2 px.
5. Уже отмеченная пара показывает существующий круглый
   attendance/AttendanceStatusBadge 24×24 px без текста «Был». CTA отметки
   существует только в карточке текущей пары.
6. Dark CTA использует Paint Style material/mobile-checkin/dark:
   C:/Users/maksd/ruttrack/back-dark-clean.png, IMAGE/FILL. Надпись занимает
   всю область кнопки, центрирована по обеим осям и использует
   color/text/primary. C:/Users/maksd/ruttrack/back-light.png — только
   спецификация будущего light-варианта этой кнопки.
7. Эти растровые источники не применяются к hero, screen background или общей
   палитре. Probe B сохраняет отдельный подписанный QA-only gradient из двух
   linear fills; верхний SCREEN-слой использует literal accent/now.
8. Glass/liquid разрешён только для floating mobile chrome с solid fallback.
   До отдельного решения двух variables и Effect Style продуктовый nav
   остаётся solid. Auth-glass не является fallback и не переиспользуется.

Светлая тема, glass token batch и ролевые экраны этим repair не открыты
автоматически. Публикацию выполняет только владелец.

## ✏️ 01.09.2026 — glass-nav и отмена декоративных mobile-обводок

Эта запись отменяет последнюю фразу предыдущего раздела только в части glass
token batch: владелец прямо разрешил glassmorphism и принял borderless-подачу
контентных блоков. Светлая тема и ролевые экраны по-прежнему не открыты.

### Живой контракт `shared/MobileBottomNav`

- set `4175:403`, варианты `slots=3|4|5`, каждый 390×76 px;
- корень варианта не имеет fill, stroke и effects;
- первым ребёнком каждого варианта лежит абсолютный `glass-surface` 390×76,
  constraints `STRETCH/STRETCH`, radius `radius/2xl` = 24;
- fill связан с `color/surface/float`;
- opacity связан с
  `component/mobile-bottom-nav/glass-surface-opacity`: dark 78, light 90
  процентных пунктов Figma;
- Effect Style `material/glass/chrome/base`: `BACKGROUND_BLUR` 20 px через
  `component/mobile-bottom-nav/glass-background-blur` + `shadow/raised`;
- item instances остаются opacity 1; новая ось `material` не добавляется;
- восемь прямых потребителей только на pages 22–23; pages 16–20 не затронуты;
- 390 / 358 / 320 instances растягивают `glass-surface` ровно до своей ширины.

### Политика контентных поверхностей

Замкнутый декоративный stroke равен 0 у passive mobile card, current hero,
lesson/list row и внешней плоскости bottom nav. Тонкий `edge-gradient` также
не используется. Тень не дублирует рамку на каждом блоке: `shadow/raised`
остаётся только у реального elevation, цветная illumination — у текущего
смыслового акцента. Документационные рамки shell/viewport не являются
продуктовыми блоками и сохраняются.

Не удаляются focus-visible 2/2, границы полей/ошибок/secondary/dropzone,
auto-absent ring, внутренние separators и forced/increased-contrast borders.

Solid fallback nav: `surface/float`, opacity 1, blur 0, `shadow/raised`, stroke
0. Dark собирается и принимается первой; light хранится только режимом
переменных до отдельной партии.

## ✏️ 01.09.2026 — student probe: мастера и размещение

Эта запись дополняет §1 и для роли student фиксирует принятое владельцем
размещение без отдельной blocks-page.

- Публичные `student/MobileTodayHero`, `student/MobileTodaySchedule`,
  `student/MobileCheckInTask` и приватные `__MobileLessonRow`,
  `__MobileCurrentLesson` живут на page 23 `23. Компоненты — mobile`.
- Page 24 называется `24. Экраны — mobile student` и содержит продуктовые
  screen frames probe, их platform/state-варианты и аннотации. Component
  masters, helper masters и component QA-fixtures на page 24 не размещаются.
- Отдельная student blocks page и page 25 в этой партии не создаются. Решение
  не распространяется автоматически на следующие роли до приёмки student
  probe.

### `student/MobileTodayHero`

Компонент проверен на контентных ширинах 296 / 328 / 358 / 390 px для экранов
320 / 360 / 390 / 430 px. Порядок: короткий временной контекст → предмет →
время/аудитория → одно действие либо итоговая отметка.

- `default`: семантический preset `action/AttendanceButton`, визуально
  отрисованный существующим `shared/AppButton` с Paint Style
  `material/mobile-checkin/dark`; новый доменный владелец не создаётся;
- `pressed`: та же геометрия, нажатое состояние;
- `already-marked`: существующий круглый `AttendanceStatusBadge` 24×24 px,
  без текста «Был»;
- `loading`, `empty`, `error`, `offline`: один однозначный shared-state без
  дублирующего пояснения.

Hero использует `color/accent/now`, принятую illumination текущей пары и
borderless mobile-политику. CTA, статус и подпись состояния не дублируют друг
друга.

### `student/MobileTodaySchedule` и `__MobileLessonRow`

Расписание — вертикальный fluid-список, не таблица и не горизонтальный
registry. Строки проверены на ширинах 296 / 328 / 358 / 390 px, имеют
горизонтальный FILL, вертикальный HUG с минимумом 72 px и не обрезают
двухстрочный предмет.

Строка несёт время, предмет, аудиторию/изменение и один существующий
`AttendanceStatusBadge`. Past / future различаются яркостью и весом; цвет не
является единственным носителем. У строки нет отдельной кнопки check-in,
`LateCheckInButton`, Checkbox или batch-selection. Декоративный замкнутый
stroke равен 0; functional focus/increased-contrast border сохраняется.

### `student/MobileCheckInTask`

Один product core используется в PWA и TMA:

- PWA browser использует browser Back;
- PWA standalone показывает один product Back и один sticky action;
- TMA task использует Telegram `BackButton` и `MainButton`;
- product bottom nav и дублирующая inline CTA на task скрыты.

Из S-01 нажатие hero CTA является commit: S-02 открывается в `checking`, затем
показывает server result. Из deep link тот же route может открыться в `ready`,
а commit принадлежит sticky action / Telegram `MainButton`. Канонический
родитель обоих входов — S-01.

`success` показывается только после server ACK. `failed-escalated` означает,
что сервер создал заявку и вернул `requestId`; это не local outbox. Состояния
`geo-blocked`, `already-marked`, `window-closed` и `offline` не отправляют
blind retry. `MobileSyncStatus` для check-in не используется: у attendance
нет optimistic increment и offline queue.

В S-01/S-02 нет текстового ввода. Обязательный keyboard-open случай проверен
отдельными PWA/TMA recovery fixtures без фиктивного поля в check-in.

### Матрица probe

На page 24 собран 31 probe frame: 8 fluid, 6 shell, 14 state и 3 adapter.
Первые 28 проверяют продуктовые screen/state-композиции; последние 3 являются
adapter/recovery/fallback fixtures. Внутри каждого probe frame находятся
только instances. Партия dark-only и
останавливается на показе владельцу; остальные student routes и light в неё
не входят.

## ✏️ 02.09.2026 — финальная раскладка student mobile 10–19

Финальные семейства располагаются вертикально, а состояния одного семейства —
справа и затем на следующей строке. Их общая grid-подложка — единая
`color/surface/raised`, padding 12 px, horizontal/counter gap 24 px. Экраны
390×844 лежат в grid напрямую: отдельные fixture wrappers и screen borders
запрещены.

Root bottom nav имеет геометрию `x=16, y=752, 358×76`; нижний inset равен
16 px. На selected item фон `color/accent/now`, icon и label используют
`color/accent/on-now`.

Page-21 masters: `icon/room` `4565:3068`, `icon/lesson-type` `4570:143`,
`icon/time` `4570:149`, `icon/attendance-graph` `4570:155`. Masters остаются
16×16 px со stroke 1 px. Только экземпляры room/type/date/time внутри карточек
пар уменьшаются до 8×8 px и получают gap 4 px. Graph button, nav, ticket meta
и остальные потребители в это правило не входят. Status markers 8×8
ограничены type-карточками статистики.

`student/MobileAttendance` имеет состояния days, empty-day, absence-actions,
inline-request, graph-days, graph-weeks и subjects. `inline-request` — состояние
экрана 12, а не отдельный route; форма принадлежит существующему
`student/MobileAbsenceRequest`.

## ✏️ 02.09.2026 — closing readback student mobile

1. Прежнее правило 8×8 для lesson metadata отменено. Актуальный instance box
   room / lesson-type / date / time — 10×10 px, stroke 1 px, gap 4 px.
   Page-21 master box остаётся 16×16 px. Date glyph оптически занимает
   9,4×9,4 px; прозрачный wrapper допустим только как local geometry adapter.
2. Overview attendance/statistics показывает visible `+ / н / у`. Compact
   type-card values располагаются горизонтально в status-strip 314×18 px.
3. History strip имеет horizontal FILL от x=14 до x=328; сумма ширин
   segments и gaps не оставляет trailing empty area. Segment count равен
   общему количеству занятий, future state — neutral.
4. Homework `materials=none` скрывает material action, не создаёт пустой
   контейнер и сохраняет disclosure target 44×44 у правого края.
5. Overview subject disclosure использует target 44×44 и visible circle
   22×22; chevron остаётся stroke 2.
6. `request-sent` не содержит иконку и использует success-green. Ticket
   metadata не клипуется; selected-lesson bottom space равен 10 px.
7. Closing bank: 39 экранов 390×844; board 1920×12789. Screen overflow,
   clipped overflow, shadows и touch targets <44 равны 0.

## ✏️ 02.09.2026 — inline metadata заявок

В ticket-card и вложенных ticket-lesson на экранах открытых и архивных
заявок metadata повторяет эталон `4597:3584`: row 17 px, icon 10×10 px,
gap 4 px, text Onest Regular 13 px с `textAutoResize=WIDTH_AND_HEIGHT`.
Иконка имеет y=3,5 px внутри строки, текст y=0; центры совпадают с дельтой
0 px. Принудительная строка 20 px и text 12 px для этого паттерна запрещены.

## ✏️ 02.09.2026 — headman mobile: канонический list-first probe

Первый dark probe старосты живёт на page 25 `25 · Mobile · Headman` и
состоит из четырёх кадров 390×844: `20 / default`, `21 / roster-list`,
`21 / focus-deck`, `21 / review-commit`. Семейства располагаются вертикально,
а последовательные состояния одного семейства — горизонтально справа.

Roster является основным режимом: 27 строк, максимум одна раскрытая строка,
три индивидуальных действия `+ / у / н`. Focus deck — необязательный
ускоритель: один студент, три подписанные кнопки, auto-advance только после
явного выбора и всегда видимый Undo последнего изменения. Bulk, Checkbox,
swipe-only и silent commit запрещены.

Review — precommit-state task, а не модалка и не success. Он перечитывает
server/group revision, группирует весь local diff, показывает изменение
состава и оставшиеся строки без статуса. Фраза «Синхронизировано» появляется
только после server ACK; текущий precommit не обещает сохранение на сервере.

Today — root с собственным bottom nav. Roster, deck и review — task/editor без
bottom nav. PWA task владеет одним product Back и sticky action; TMA task
скрывает их и использует Telegram BackButton/MainButton. Реальный bottom inset
задаётся `max(16px, env(safe-area-inset-bottom))`; keyboard скрывает nav.

## ✏️ 02.09.2026 — headman mobile: canonical bank 19–29

Текущий dark-bank page 25 содержит 26 кадров 390×844 и остаётся кандидатом
на визуальную приёмку владельца. Он не создаёт новых публичных component
contracts. Семейства располагаются отдельными вертикальными рядами, а
root/detail/task/editor одного семейства — слева направо в порядке перехода.

Root-экраны 19, 20, 21-list, 22, 23, 25, 26, 27, 28-agenda и 29-queue
используют product bottom nav только там, где экран является верхним уровнем
IA. Detail/task/editor скрывают nav. В PWA у них один product Back; sticky
action появляется только у save/commit/confirm. В TMA эти два product-слоя
скрываются в пользу Telegram BackButton/MainButton. Двойные Back и нижние
панели запрещены.

Ролевые паттерны текущего банка:

- карта — detail с честным empty viewer, пока нет плана этажа;
- группа — search-first roster, student detail и отдельный permissions editor;
- предметы — whole-subject cards и один editor предмета с типами занятий;
- конструктор — day-first template с двумя явными parity-строками;
- управление парами — weekly agenda, active/cancelled detail и отдельные
  transfer/cancel tasks без post-submit claims;
- заявки — две независимые очереди у/н, lazy detail и два необратимых confirm.

Server-success, sync, revision receipt и offline outbox не показываются до
ACK. Отмена пары описывает посещаемость как недействительную, а не удалённую;
отдельный result-state не создаётся до согласованного cascade payload.

## ✏️ 02.09.2026 — headman mobile: ревизия владельца всего dark-bank

Эта запись отменяет для роли старосты прежний staged-маршрут
`roster → focus-deck → review → commit`. После явной правки владельца
индивидуальный статус выбирается справа в строке и сохраняется как отдельная
операция; focus deck, review, общий commit и bulk отсутствуют. Для `у`
обязателен выбор типа уважительной причины без комментария и файла; отметка
старосты принимается по умолчанию. Production-состояния pending/error/rollback
будут добавлены после согласования row revision и idempotency.

`20 · Сегодня` и `21 · Учёт` сохраняют два честных root-состояния с разными
selected bottom-nav items, но на канве входят в одну горизонтальную семью
`screens/20–21`. Today показывает календарь, переключатель пары, стандартную
lesson-card, три метрики `Присутствуют / В ожидании / успешных по гео`,
уважительные причины, домашнее задание, lock, выдачу ДЗ и журнал действий.
Переход в журнал выполняется нажатием на выбранную пару; отдельного CTA,
progress `21/27` и текста snapshot/revision в hero нет.

Статистика содержит рядом `Чистая посещаемость`, показатель с уважительными
причинами и посещаемость группы сегодня. Переключатель
`Индивидуально / По предметам` меняет нижний набор данных. Student detail
включает раскрываемые метрики и редактируемую историю прошедших пар; нажатие
на текущий круглый статус снимает его. Принятый graph donor `4595:422`
переиспользуется без изменения анатомии.

Homework create является последовательностью
`предмет → тип → календарь расписания/любая дата → название, полный текст,
ссылки → publish`. Subject editor хранит типы, количество пар и преподавателя
вертикальными metadata-строками; назначение преподавателя — отдельный task.
Schedule builder начинает с пустого дня без преждевременного деления на
недели; slot editor содержит parity, доступные/занятые слоты, subject, type,
TeacherReadout и room. Agenda имеет отдельный add-mode: свободные слоты
видимы зелёными только в этом режиме, cancelled visibility управляется
отдельно.

Requests root группируется по парам; имеются detail, разбиение затронутых пар,
approve/reject confirm и архивный пример. `Ещё` использует строки без нижних
описаний и с существующими иконками слева. Профиль и Настройки переиспользуют
принятые student-mobile композиции с ролевыми данными старосты.

Финальный canonical adapter этой ревизии — PWA standalone 390×844, dark.
Root владеет product bottom nav; PWA task — одним product Back и одним sticky
action, TMA task заменит их native BackButton/MainButton. Keyboard, TMA,
360/430/320, browser-mode, light и полная state-матрица следуют только после
визуальной приёмки владельцем.

## ✏️ 03.09.2026 — headman mobile: единая навигация, статусы и экранные состояния

Эта запись отменяет формулировку от 02.09.2026 о форме ручного `у` без
комментария и файла. Актуально: тип уважительной причины выбирается из
dropdown; комментарий и один файл доступны, но необязательны. Действие
старосты принимается по умолчанию, однако pre-ACK интерфейс не обещает
сохранение до ответа сервера.

Selected item любого `MobileBottomNav` использует `color/accent/now`, а все
его glyph и label — `color/accent/on-now`; белая иконка внутри выбранного
фиолетового item запрещена. Product Back в PWA task/detail — единый круг
44×44 px, radius 22 px, с `icon/previous` 16×16 px в координатах 14/14.
Направления без семантики Back используют системные `icon/previous`,
`icon/next` и `icon/chevron-down`; текстовые `‹ / ›` не применяются.

Metadata иконки внутри lesson-card и ticket lesson-context имеют box 10×10
px, stroke 1 px и gap 4 px. Оптическая проверка выполняется по центру cap-line
текста, а не по внешнему box исходного SVG. Розовая линия допускается только
как временный QA-guide и не остаётся в продуктовых кадрах.

Ручной источник отметки старосты различается формой: незалитый круг с
цветной обводкой и обязательная подпись `Вручную`. Залитый круг означает
обычный подтверждённый статус. Это правило одинаково для `+`, `у` и `н` и не
заменяет буквенный/текстовый accessible label.

Карта является root и сохраняет bottom nav. `Группа` и `Предметы` в текущей
headman IA открываются как detail из `Ещё`: у них один Back и нет bottom nav.
Expanded homework на `Сегодня` остаётся inline; под ним сохраняются три
раздельные цели 114×56 px: состояние пары, `Выдать ДЗ`, `Журнал`.

Текущий dark-bank содержит 45 product-кадров 390×844. Добавлены три состояния:
`20 / homework-expanded`, `26 / remove-teacher-confirm` и
`28 / transfer-calendar-open`. Они располагаются справа от исходного экрана
своего семейства; семейства по-прежнему идут вертикально.

## ✏️ 04.09.2026 — headman dark-bank: второй polish-pass

`shared/MobileBottomNav` использует один вложенный token-bound
`BACKGROUND_BLUR=25 px`; blur на корне компонента и локальный blur на root
экрана удалены. Selected item по-прежнему использует
`color/accent/now` + `color/accent/on-now` для glyph и label.

На продуктовых экранах headman однострочный текст внутри фиксированного
контрола, значения и статусные glyphs имеют `verticalAlign=MIDDLE`.
Многострочные объяснения, body домашнего задания, textarea и причины остаются
`TOP`. Оптическое выравнивание metadata-иконки проверяется после этого по
реальному glyph, а не по внешнему box.

В roster после выбора остаётся одна видимая цель статуса. Ручной источник
показывается цветной обводкой без заливки и без видимого слова `Вручную`;
обычный подтверждённый источник — заливкой. В empty-state видимы три
нейтральные цели `+ / у / н`. Повторный тап по выбранному статусу возвращает
empty-state. `source=headman` сохраняется для accessible name, аудита и
аналитики.

Semester-selector допускает full-card векторную сезонную иллюстрацию:
осенью — листья, весной — зелень и цветы. Заголовок и период лежат над
token-bound text-safe слоем; локальный hex, gradient и новый token не нужны.

Текущий dark-bank содержит 46 product-кадров 390×844: справа от осеннего
`22 · Статистика / individual-list` добавлено каноническое состояние
`22 · Статистика / spring-semester`. Иные ширины, platform adapters, light и
полная state-матрица остаются отдельной партией после визуальной приёмки.

## ✏️ 05.09.2026 — owner corrections Admin + Teacher mobile

Эта запись отменяет прежнюю раскладку, в которой роль повторялась на
нескольких экранах. Переключатель роли находится только на главном экране
роли; текущая роль в task выделяется `color/accent/now` и check, без текста
`Текущая роль`. Выбранный item нижней навигации использует
`color/accent/now` вместе с `color/accent/on-now` для label и glyph.

Admin dark-bank после owner corrections содержит 19 product-кадров 390×844.
Обзор получил мобильные периоды и пять desktop-equivalent показателей с
динамикой. Пользователи получили поиск, фильтры, icon-only reset, create,
post-ACK credential receipt, detail и edit. Группы получили фильтры, draft,
archive и expanded detail; семестры — add/edit task. Роль не дублируется за
пределами главного Обзора.

Teacher dark-bank содержит 9 product-кадров 390×844. На `Сегодня` остаётся
единственный role-switch trigger; отдельный H1 `Сегодня` удалён. Current,
next и lesson-context используют вертикальную metadata-анатомию с
семантическими room/type icons. Roster read-only: видимы только статусные
символы, и только `у` имеет 44 px disclosure в read-only detail заявки.

Detail заявки показывает автора и время подачи, старосту и время решения,
причину, комментарий, пары только по предмету текущего преподавателя и
вложение. Прямое решение владельца о показе того, кто принял заявку,
отменяет для этого Teacher projection прежнее ограничение не показывать
decision actor. Экран не содержит approve/reject или иных write-controls.

В Teacher Attendance прошедшие пары показывают долю `+` процентом. Обе
ветки Statistics используют правильный selected accent и четыре ряда
16 px status symbols: `+`, пара `+`/`у`, `у`, `н`; прежние текстовые labels
удалены. На карте остаётся один заголовок `Карта кампуса`, внешний CTA
удалён. Profile не содержит роль или второй вход в role switch.

PWA detail/task сохраняет один product Back и не имеет bottom nav. В TMA
adapter product Back заменяется Telegram BackButton; одновременно они не
показываются. Light, 320/360/430, keyboard и полная loading/empty/error/
offline/conflict матрица остаются следующими партиями.

## ✏️ 05.09.2026 — Part 25: Admin mobile owner corrections

Admin dark-bank расширен с 19 до 23 product-кадров 390×844: добавлены create
Student, create Admin, add-semester/start-calendar-open и
edit-semester/end-calendar-open. Board имеет 908×11700 px; 9 root-кадров
содержат dock, 14 task/detail/editor/ACK-кадров — один product Back без dock.

На двух `Обзор` KPI используют локальную mobile-анатомию, не меняющую
`shared/MetricDelta`: category занимает центрированную левую зону, value стоит
справа сверху, а существующая динамика — под ним в прямоугольном semantic
badge только со стрелкой и числом. Нулевая динамика отсутствует. Geo сохраняет
отдельный server value `N из M`. Activity `Всё время` не получает суточных
delta. Во Vue полное доступное имя по-прежнему называет период сравнения;
визуальная краткость не сокращает screen-reader copy.

Selected `Всё время` повторяет геометрию donor `Сегодня`: carrier 175×44,
`radius/full`, `color/accent/now` и `color/accent/on-now`. Это прямое решение
владельца локально отменяет для двух Admin Overview буквальное ограничение
одного accent-carrier: dock остаётся вторым navigation-level accent и не
смешивается с content period state.

User detail показывает в identity-card только ФИО. Роль, статус роли, статус
аккаунта, табельный номер и Telegram ID находятся в единой data-card. Секция
`Данные для входа` содержит рядом два самостоятельных whole-field copy
carrier 175×76: login и initial password; icon внутри декоративен. Пароль
явно ограничен состоянием «до смены пользователем» и не попадает в list-card.

Create user остаётся role-first и pre-ACK. В банке сохранён Teacher и добавлены
Student (`ФИО`, группа по отображаемому имени, Telegram ID) и Admin (`ФИО`,
server-driven каталог прав, Telegram ID). Формы не принимают login/password.
Единый post-ACK receipt объединяет check, server acknowledgement и identity в
`success/bg + success/border`; credential rows имеют отдельные 44×44
icon-only copy controls.

Edit user разделяет PATCH профиля и role commands. Секция `Роли` показывает
несколько ролей, отдельные `Добавить роль` и per-role `Удалить роль`. Backend
передаёт `canRemove` и `blockedReason`; blocked action остаётся видимым вместе
с причиной, а разрешённый remove требует confirmation и server ACK.
Optimistic add/remove не проектируется.

Оба calendar-open состояния переиспользуют instance
`homework/MonthCalendar` `640:2`; общий master не меняется. Screen-level scale
1,1 превращает 40×40 DayNumber в 44×44. Вложенные compact month controls
отключены штатными properties и заменены локальными carriers 44×44; между
ними стоит token-bound month label. Add выбирает 01.09.2026 в сентябре 2026,
edit — 31.01.2027 в январе 2027. Видимый context-copy называет активную и
выбранную дату, поэтому selected state не основан только на цвете.

`Ещё → Семестры` использует отдельный instance `icon/semester` `6008:152`.
Credential actions используют один `icon/copy` `6008:143`. Оба дополнения
закрытого component registry описаны отдельной датированной записью; прочие
shared masters и токены не менялись.

## ✏️ 05.09.2026 — Part 26: Teacher Profile role entry и единый role-gradient

Эта запись прямо отменяет решение Part 24 только в части второго entry point
для Teacher: на `39 · Профиль / default` `5850:585` снова есть строка
`Сменить роль`, ведущая в существующий task `5854:582`. Строка не является
вторым RoleSwitcher и не возвращает подпись роли в identity-card. Profile
остаётся root с dock; role task остаётся task с одним Back и без dock.

Teacher Profile использует тот же локальный account-row pattern, что Headman:
358×56 px, `icon/role-switch`, Onest SemiBold 15 и semantic surface/text/icon
bindings. Новая строка стоит первой при y171; остальные account rows находятся
на y239/307/375/443. Пять строк сохраняют принятый visual radius 18 px без
точного semantic token; близкий token не подставляется.

По прямому решению владельца три mobile role trigger `5810:145`, `5896:452`,
`5901:455` используют точный fill принятого donor `4997:144`: linear gradient
со stops `#3B1A7A` / `#5729A1` в позиции 0,52 / `#2E1F4D` и transform
`[[0.75, 0.2, 0.05], [-0.2, 0.75, 0.2]]`. Это локальное mobile-исключение
явно отменяет прежний solid fallback для этих carrier и допускает role
gradient рядом с current-content и selected-dock accents. Размеры, radius,
padding, тексты и иконки role trigger остаются ролевыми; shared master не
создаётся и не меняется. Литеральный donor fill пока не объявляется token
contract для Vue или светлой темы.
