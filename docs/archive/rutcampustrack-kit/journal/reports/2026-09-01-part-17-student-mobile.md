# Part 17 — student mobile probe

Дата: 01.09.2026. Figma: `VgVjQYWILLG9AC7Eh12VMk`.
Поверхности: PWA browser, PWA standalone, Telegram Mini App.

## Ответы на вопросы

- Новые экраны размещены на отдельной page 24 с живым именем
  `24. Экраны — mobile student`, id `4379:429798`.
- На page 24 находятся screen frames и их аннотации. Публичные и приватные
  component masters остались на page 23 `23. Компоненты — mobile`.
- Foundation закрыт достаточно для student probe. Партия остановлена после
  полного probe и не перешла к остальному student backlog без приёмки.

## Разбор задания

Непонятное, противоречия и остановленные пункты:

- Путь `design/mobile/ux-guide.md` из промпта отсутствует; живой источник —
  `design/mobile/RutCampusTrack_PWA_TMA_UX_Guide.md`. Это записано владельцу,
  но сборку не блокировало.
- Старый design-skill запрещал gradients/glow на авторизованных экранах;
  более позднее прямое решение владельца разрешает их для PWA/TMA. Применено
  позднее решение только в mobile scope.
- Mass attendance запрещена. Checkbox исследован только концептуально и в
  Figma не менялся.
- После показа foundation-проб владелец разрешил glass nav; в student-партии
  новые variables/styles не создавались.
- Светлая тема, публикация, остальные student routes и следующие роли в эту
  остановку не входят.

Сверка чисел живого файла перед работой: pages 16–23 имели соответственно
890 / 748 / 836 / 363 / 286 / 73 / 229 / 223 A. Page 24 существовала пустой:
0 A. Единица — физический узел A без захода внутрь instances; PAGE не
считается.

## Функциональная карта

### S-01 «Сегодня»

Работа пользователя: увидеть текущую пару, понять доступность отметки,
отметиться одним действием и быстро прочитать остальные пары дня.

- В корне: дата/контекст, текущая пара, единственный CTA, вертикальное
  расписание и bottom nav.
- Прямой commit: CTA сразу открывает S-02 в `checking`; дополнительного
  промежуточного detail-шага нет.
- В строках расписания: время, предмет, аудитория/изменение и один status
  circle; действия отметки там нет.
- Скрыто до запроса: детали разрешения геолокации, проверка и server result.

### S-02 «Отметка»

Работа пользователя: дать разрешение при необходимости, дождаться серверного
решения и понять итог. PWA/TMA используют один product core; shell владеет
Back и основным действием.

Состояния component set: ready, geo-permission, checking, success, failed-escalated,
geo-denied, geo-blocked, already-marked, window-closed, offline,
server-error. Успех отображается только после server ACK; local outbox и
optimistic `+` отсутствуют.

`cancelled` не является стабильным экранным состоянием: отмена — Back/выход
и возврат в S-01. `already-marked` и `offline` проверены как variants на page
23; отдельные viewport-дубли на page 24 не создавались, потому что требуемый
probe matrix уже покрывает default/scrolled/pressed/loading/empty/error и
platform adapters.

## Рассмотренные решения

Выбран гибрид A+B: непрозрачная спокойная структура, illumination текущей
пары, gradient CTA и glass bottom nav. Telegram C остался shell-интеграцией,
а не третьей темой продукта.

Отклонено:

- сжатие desktop-таблицы — не соответствует мобильной работе;
- отдельные PWA и TMA product masters — создают две расходящиеся системы;
- CTA в каждой строке — дублирует главный путь и повышает риск ошибочной
  отметки;
- словесное «Был» рядом со status circle — дважды кодирует один статус;
- декоративные closed strokes на passive blocks — блок уже читается формой,
  материалом и расстоянием;
- offline queue / optimistic success — не подтверждены backend-контрактом;
- дополнительная blocks-page — не нужна для трёх student-мастеров.

## Касания

| Сценарий | Product taps | Системное действие |
|---|---:|---:|
| Сегодня → отметка при уже выданном permission | 1 | 0 |
| Сегодня → первый запрос геолокации | 1 | 1 permission confirmation |
| Deep link S-02 ready → отправка | 1 | 0 либо 1 permission confirmation |
| Просмотр уже принятой отметки | 0 | 0 |

## Что убрано и почему

- Отдельное действие в lesson row — чтобы одна пара имела один commit point.
- Подпись «Был» — status circle уже несёт состояние.
- Batch/Checkbox — прямой запрет владельца.
- Product Back и dock в TMA task — ими владеет host chrome.
- Product Back в PWA browser — им владеет браузер.
- Dock на task — task является leaf, основной action закреплён отдельно.
- Timestamp freshness и offline outbox — политики не приняты.
- Дублирующие тексты рядом с очевидным loading/error/status — форма должна
  объяснять состояние сама.

## Компоненты

Созданы на page 23:

| Master | Figma id | Варианты | Потребители до→после |
|---|---|---:|---:|
| `student/MobileTodayHero` | `4381:926` | 8 | 0→14, все новые на page 24 |
| `student/MobileTodaySchedule` | `4383:851` | 4 | 0→17: page 23 — 3, page 24 — 14 |
| `student/MobileCheckInTask` | `4392:691` | 11 | 0→17, все новые на page 24 |
| `__MobileLessonRow` private | `4387:539` | 3 | 0→3, все новые на page 23 |
| `__MobileCurrentLesson` private | `4397:688` | 4 | 0→4, все новые на page 23 |

`shared/MobileBottomNav` (`4175:403`) физические потребители: 8→22; новые
14 находятся только на page 24. Сам shared master в этой партии не менялся.

Числа устройства product masters:

- Hero: 62 A, 8 вариантов, 0 unbound solid fills, 0 raw gradients,
  13 связанных радиусов.
- Schedule: 17 A, 4 варианта, 0 unbound solid fills, 0 raw gradients,
  4 связанных радиуса.
- Task: 122 A, 11 вариантов, 0 unbound solid fills, 0 raw gradients,
  32 связанных радиуса.
- Lesson row: 17 A, 3 варианта, 0 unbound solid fills, 0 gradients,
  3 связанных радиуса.
- Current lesson: 25 A, 4 варианта, 0 unbound solid fills, 0 gradients,
  7 связанных радиусов.

Функциональные strokes сохранены только у error/secondary/disabled смыслов;
декоративных рамок passive surfaces нет. IMAGE-gradient CTA находится внутри
переиспользованного `AppButton` через утверждённый Paint Style, поэтому raw
gradient nodes в новых masters равны 0.

Доменный владелец действия не изменён: mobile CTA и sticky actions имеют имя
`action/AttendanceButton`, а `shared/AppButton` является только визуальным
master. Новый `MobileAttendanceButton` и второй check-in owner не создавались.
Приватный `__MobileCurrentLesson` вынесен на приёмку как часть probe, а не как
утверждённый публичный API.

## Иконки

Новых иконок не рисовалось. Переиспользованы календарь, задачи, учёт,
профиль, меню, Back и status circle из существующей библиотеки. На page 21:
14 icon components и 25 физических stroked VECTOR nodes; у всех 25
`strokeWeight = 1 px`. Page 21 сохранила 73 A.

Ранее обсуждавшиеся «23 контура» и закрывающие 25 VECTOR nodes — разные
проходы одной единицы: 23 была неполной выборкой, 25 — полный независимый
обход физических VECTOR nodes. Узлы page 21 этой партией не добавлялись.

Где длинная подпись заменена или сокращена иконкой:

- bottom nav использует однозначную пиктограмму и короткое слово под ней:
  «Сегодня», «Задания», «Учёт», «Ещё», «Профиль»; подпись не удалена, поэтому
  смысл не зависит только от иконки;
- принятая отметка показывается знаком `+` в существующем круглом
  `AttendanceStatusBadge` вместо дублирующего текста «Был»; доступное имя в
  контракте остаётся словесным;
- Back без подписи используется только в PWA standalone, где стрелка и
  положение однозначны; в TMA им владеет нативный host BackButton.

## Матрица кадров

Все числа ниже — прямой физический вес screen frame в A.

| № | Figma id | Кадр | Размер | A |
|---:|---|---|---:|---:|
| 1 | `4401:297` | S01 TMA stress | 320×640 | 4 |
| 2 | `4401:381` | S02 TMA ready deep link | 320×640 | 2 |
| 3 | `4401:400` | S01 PWA standalone | 360×800 | 4 |
| 4 | `4401:484` | S02 PWA standalone | 360×800 | 4 |
| 5 | `4399:164` | S01 PWA standalone | 390×844 | 4 |
| 6 | `4401:537` | S02 PWA standalone | 390×844 | 4 |
| 7 | `4401:586` | S01 PWA standalone | 430×932 | 4 |
| 8 | `4401:670` | S02 PWA standalone | 430×932 | 4 |
| 9 | `4404:628` | S01 PWA browser | 390×844 | 4 |
| 10 | `4404:712` | S02 PWA browser | 390×844 | 3 |
| 11 | `4404:747` | S01 TMA overview compact | 390×640 | 4 |
| 12 | `4404:831` | S01 TMA overview expanded | 390×844 | 4 |
| 13 | `4404:915` | S02 TMA task compact | 390×640 | 2 |
| 14 | `4404:932` | S02 TMA task expanded | 390×844 | 2 |
| 15 | `4405:900` | S01 scrolled | 390×844 | 4 |
| 16 | `4405:965` | S01 pressed | 390×844 | 4 |
| 17 | `4405:1072` | S01 loading | 390×844 | 4 |
| 18 | `4405:1137` | S01 empty/no current | 390×844 | 4 |
| 19 | `4405:1223` | S01 partial error | 390×844 | 4 |
| 20 | `4405:1325` | S01 offline cached | 390×844 | 4 |
| 21 | `4405:1435` | S02 geo-permission | 390×844 | 3 |
| 22 | `4406:1414` | S02 checking | 390×844 | 4 |
| 23 | `4406:1474` | S02 success | 390×844 | 3 |
| 24 | `4406:1510` | S02 failed-escalated | 390×844 | 3 |
| 25 | `4406:1546` | S02 geo-denied | 390×844 | 3 |
| 26 | `4406:1598` | S02 geo-blocked | 390×844 | 3 |
| 27 | `4406:1631` | S02 window-closed | 390×844 | 3 |
| 28 | `4406:1686` | S02 server-error | 390×844 | 4 |
| 29 | `4407:1633` | PWA keyboard recovery | 390×480 | 3 |
| 30 | `4407:1668` | TMA keyboard recovery | 390×480 | 2 |
| 31 | `4407:1685` | Solid glass fallback | 390×844 | 4 |

Сумма прямых probe-frame A с учётом корня каждого кадра — 108. Page 24 целиком — 220 A с учётом групп,
заголовков и аннотаций.

Состав каждого кадра сверху вниз задаётся следующими кодами; номер относится
к таблице выше:

- `R = Hero → Schedule → Dock`: 1, 3, 5, 7, 9, 11, 12, 15–20, 31;
- `T = Task`: 2, 13, 14, 30;
- `P = Back → Task → sticky AttendanceButton`: 4, 6, 8, 22, 28;
- `B = Task → sticky AttendanceButton`: 10;
- `D = Back → Task`: 21, 23–27, 29.

Так перечислены все 31 кадр ровно один раз. Кадры 29–31 — adapter/recovery/
fallback fixtures; остальные 28 — product screen/state compositions.

## Проверка кадров

Compact-проход выполнен по всем 31 кадрам. Detailed-проход выполнен 11
пакетами не больше трёх кадров.

| Проверка | Результат |
|---|---:|
| Probe frames | 31 |
| Direct raw children | 0 |
| Выходы за screen | 0 |
| Выходы за parent | 0 |
| Прямоугольные подложки под rounded | 0 |
| Переполнение текста | 0 |
| Лишние многоточия | 0 |
| Эффективные цели меньше 44×44 | 0 |
| Direct overlaps | 0 |
| Двойной bottom nav | 0 |
| Двойной Back | 0 |
| Literal Inter | 0 |
| Missing fonts | 0 |

В error retry визуальная кнопка 40 px покрыта отдельным прозрачным hit target
44×44: hero `4417:590`, schedule `4417:591`.

Compact-первое действие:

- stress 320: y=272 px, 264×52 px;
- TMA compact 390×640: y=224 px, 326×52 px.

Минимальный nav slot на 320: примерно 57,6×60 px. Stress-предмет длиной
47 знаков занимает 264×78 px, переносится по словам и не обрезается.
Контейнер CTA label центрирован точно: dx=0 px, dy=0 px. Смещение render bounds
глифа −0,61 px является оптической метрикой шрифта, а не геометрией label.

Визуальный просмотр всех четырёх групп обязателен и выполнен. Он нашёл один
дефект, которого не было в числовых счётчиках: шумный busy-gradient в sticky
action состояния checking. Instance заменён на disabled master, opacity 0,72;
повторный render принят.

## Variables, styles и литералы

Проверены 248 Figma variables: COLOR 157, FLOAT 89, STRING 2. В student-run
создано 0 variables и 0 styles. Проверены существующие mobile variables
`VariableID:4354:142`, `VariableID:4354:143`, Paint Style
`material/mobile-checkin/dark` и Effect Styles `shadow/raised`,
`shadow/overlay`, `shadow/sticky-x`, `material/glass/chrome/base`.

Styles до/после: Paint 1, Text 10, Effect 4, Grid 0. Token requests этой
партии: 0. Все product-master solid fills связаны; CTA использует принятый
image Paint Style. Literal radius 24 встречается только у 31 viewport fixture
root: это презентационные device/probe containers, а не reusable product
masters и не token contract. Все пять product component sets используют
semantic radius bindings.

Привязок «не по смыслу» в пяти новых наборах не найдено: unbound solid fills
и радиусы вне системы — 0. Новые root literal strokes — 0. Функциональные
strokes унаследованы только внутри существующих shared instances:
`ErrorState` в Hero/Schedule, три secondary `AppButton` в Task и disabled
`AppButton` в CurrentLesson. QA/probe roots имеют 0 strokes; decorative border
literals — 0.

## Дельта A и геометрии

| Page | До, A | После, A | ΔA |
|---:|---:|---:|---:|
| 16 | 890 | 890 | 0 |
| 17 | 748 | 748 | 0 |
| 18 | 836 | 836 | 0 |
| 19 | 363 | 363 | 0 |
| 20 | 286 | 286 | 0 |
| 21 | 73 | 73 | 0 |
| 22 | 229 | 229 | 0 |
| 23 | 223 | 488 | +265 |
| 24 | 0 | 220 | +220 |

Общая ΔA = +485. Замыкание web pages 16–20 = 0 A.

Page 23: существующий root `4168:142` изменён с 1340×3889 до 1340×8504 px:
Δwidth 0 px, Δheight +4615 px, прямые дети 6→10. Первые шесть foundation-
секций не перемещались; четыре новые секции добавлены ниже y=3889. В них
созданы пять component sets, у которых прежней геометрии не было.

Page 24: probe root отсутствовал и создан размером 2000×9246 px с шестью
прямыми секциями; пустая page выросла с 0 до 31 probe frames.

Для pages 16–20 сохранены A-счётчики, но отсутствуют before geometry hashes.
По журналу в их scope не выполнялись мутации и ΔA=0, однако это не доказывает
geometry delta. Корректный статус геометрии защищённых pages — «не измерена»;
baseline-gap записан владельцу и должен быть закрыт до следующей роли.

## Transport и recovery

Transport closed: 0. Failed writes: 0. Blind mutation retries: 0.
Uncommitted writes: 0.

Было восемь нетранспортных ошибок, все с readback либо atomic rollback без
неопределённого состояния: syntax — 3; API/precondition — 3; устаревший live
id — 1; font gate — 1. Мутации вслепую не повторялись.

## Машиночитаемые снимки и render

- Before: `journal/state/part-17-student-mobile-before-2026-09-01.json`.
- After: `journal/state/part-17-student-mobile-after-2026-09-01.json`.
- Fluid: `_work/screenshots/student-mobile/fluid-spine.png`.
- Shell: `_work/screenshots/student-mobile/shell-parity.png`.
- States: `_work/screenshots/student-mobile/state-board.png`.
- Adapters: `_work/screenshots/student-mobile/adapters.png`.

## Открытые вопросы владельцу

- Backend idempotency `(studentId, lessonId)` для повторов.
- Атомарность escalation и стабильный `requestId`.
- Допустимость `accuracy = null` из Telegram.
- Какие причины создают escalation.
- Минимальная Telegram version и LocationManager fallback.

Они не блокируют приёмку статического probe, но блокируют production-контракт
retry/escalation.

## Что не сделано и почему

- Остальные student screens — только после приёмки probe.
- Light theme — после приёмки dark.
- Headman/teacher/admin — строго после student.
- Checkbox repair и массовая отметка — запрещены текущим решением владельца.
- Новые variables/tokens/styles — не создавались в этой партии; сначала
  показывается probe.
- Публикация — выполняется только владельцем.
- Web pages 16–20 — вне mobile scope и не тронуты.

## Изменённые файлы

- `design/COMPONENT_REGISTRY.md:1284–1314`;
- `design/figma-spec-mobile.md:268–345`;
- `journal/DECISIONS.md:2236–2267`;
- `journal/to-owner.md:722–755`;
- `journal/state/part-17-student-mobile-before-2026-09-01.json:1–67`;
- `journal/state/part-17-student-mobile-after-2026-09-01.json:1–200`;
- этот отчёт: `journal/reports/2026-09-01-part-17-student-mobile.md:1–364`.

Бинарные render-артефакты без номеров строк:
`_work/screenshots/student-mobile/fluid-spine.png`, `_work/screenshots/student-mobile/shell-parity.png`,
`_work/screenshots/student-mobile/state-board.png`, `_work/screenshots/student-mobile/adapters.png`.
