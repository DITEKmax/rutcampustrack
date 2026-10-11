# RutCampusTrack — аудит современных мобильных интерфейсов

Дата аудита: 28.08.2026  
Область: PWA и Telegram Mini App как мобильные приложения; этот файл **не** является Telegram-специфической проработкой API.  
Ограничения: Figma не запускалась, кит не менялся. Ниже отдельно отмечены внешние находки, выводы и проектные предложения.

## 0. Короткий вывод

Для RutCampusTrack уместен современный и более живой мобильный язык, но не тотальный «glassmorphism». Лучший рабочий гибрид:

1. **Quiet Liquid** для каркаса: плавающая полупрозрачная навигация, контекстные панели и переходы-морфы.
2. **Expressive Bento** для главных и статистических экранов: крупный текущий объект, разные масштабы карточек, мягкие формы, фиолетовый градиент как функциональный акцент.
3. **Signal Grid** для журнала, пользователей и групп: плотные списки, ритм 4/8 px, паттерн расписания как слабая фактура, минимум стекла.
4. **Aurora Flow** только для загрузки, пустых состояний, онбординга и одного «живого» текущего объекта — не как фон каждого экрана.

Это не четыре визуальные темы. Рекомендуется собрать одну систему: Quiet Liquid как оболочка, Expressive Bento на обзорных экранах, Signal Grid на рабочих экранах, Aurora Flow — как редкий эмоциональный слой.

Крупные матрицы действительно не надо переносить на телефон. Но ограничиваться только «пара → список студентов» и «студент → карточка» тоже не обязательно. Для разных задач полезны разные проекции одних данных:

- «исключения → решение» для старосты;
- «один студент → один быстрый статус → следующий» для последовательной отметки;
- «список → раскрывающаяся линза» для смешанного просмотра и редактирования;
- «метрика → ранжированный список» для статистики;
- «сравнение до трёх» для ситуаций, где без сопоставления теряется смысл;
- «лента посещаемости» и «тепловая лента» для поиска паттернов во времени.

## 1. Как читать уровень доверия

- **A — высокая надёжность:** официальная HIG/design system, официальная техническая документация, описанный shipped-продукт, исследование с методикой/выборкой.
- **B — практический ориентир:** разбор практикующего UX-специалиста или исследовательской компании; полезен, но перенос в продукт требует проверки.
- **C — визуальная фактура:** Behance/Dribbble/Mobbin. Показывает композицию и настроение, но ничего не доказывает о понятности, конверсии и доступности.

Маркировка в тексте:

- **[Находка]** — подтверждено источником.
- **[Вывод]** — интерпретация для RutCampusTrack.
- **[Предложение]** — гипотеза, которую нужно прототипировать и тестировать.

## 2. Что дают актуальные системы и живые продукты

### 2.1. Expressive ≠ декоративный хаос

**[Находка, A]** Google создавал Material 3 Expressive три года: 46 исследований, более 18 000 участников, eye tracking, usability, опросы и эксперименты. Стратегическое применение цвета, размера, формы и containment помогало находить ключевые элементы до четырёх раз быстрее. Но эксперимент с хаотично разложенными обложками вместо знакомого списка ухудшил usability, а удаление текстовых подписей у действий тоже дало ухудшение. Источник: Google Design, *Better, Easier, Emotional UX*, май 2025 — <https://design.google/library/expressive-material-design-google-research>.

**[Вывод]** Необычная форма должна усиливать приоритет или связь, а не разрушать знакомую структуру списка, журнала или навигации. На рабочем экране старосты полезнее крупнее сделать текущий статус и кнопку завершения, чем превращать всех студентов в разноформатный masonry.

### 2.2. Стекло — отдельный интерактивный слой

**[Находка, A]** Apple в WWDC25 прямо ограничивает Liquid Glass навигацией и ключевыми контролами над контентом. Табличный контент не должен становиться стеклянным; «glass on glass» надо избегать. В Apple Maps плавающие стеклянные кнопки убираются, когда раскрывается sheet, чтобы не было двух стеклянных слоёв. Материал должен адаптироваться к контенту, повышенному контрасту, Reduce Transparency и Reduce Motion. Источники:

- Apple Design Team, *Meet Liquid Glass*, WWDC25 — <https://developer.apple.com/videos/play/wwdc2025/219/>
- Apple, *Build a UIKit app with the new design*, WWDC25 — <https://developer.apple.com/videos/play/wwdc2025/284/>
- Apple, *Get to know the new design system*, WWDC25 — <https://developer.apple.com/videos/play/wwdc2025/356/>

**[Находка, A]** В октябре 2025 Linear выпустил на iOS и Android собственный frosted-glass материал, новый нижний toolbar и глобальный Create Issue. В январе 2026 Linear разрешил переставлять пункты и закреплять проекты/документы в мобильной панели. Это shipped-продукт для плотной рабочей информации, а не концепт. Источники:

- Linear, *Mobile app redesign*, 16.10.2025 — <https://linear.app/changelog/2025-10-16-mobile-app-redesign>
- Linear, *Customize your navigation in Linear Mobile*, 22.01.2026 — <https://linear.app/changelog/2026-01-22-customize-your-navigation-in-linear-mobile>
- Linear, *Introducing Linear Mobile*, 19.09.2024 — <https://linear.app/changelog/2024-09-19-introducing-linear-mobile>

**[Вывод]** Стеклянная нижняя панель для RutCampusTrack жизнеспособна. Стеклянные карточки каждого студента — нет. Кастомизация панели может стать поздней функцией для старосты, когда появится статистика частот; в v1 лучше роль-ориентированные дефолты.

**[Находка, B]** Nielsen Norman Group отмечает, что glassmorphism создаёт глубину, но часто ломает контраст. Рекомендации: больше blur на сложном фоне, простой фон под стеклом, проверка контраста на всех участках, возможность Reduce Transparency/solid fallback. Megan Brown, 07.06.2024 — <https://www.nngroup.com/articles/glassmorphism/>.

### 2.3. Градиент должен объяснять направление или состояние

**[Находка, A]** Команда Gemini использует градиент не как обои, а как контекстный сигнал: концентрированный край указывает направление, рассеянный хвост показывает перенос энергии; движение всегда имеет начало и конец и объясняет «слушает / думает / синтезирует». Google Design, *Illustrating the Gemini App*, 2025 — <https://design.google/library/gemini-ai-visual-design>.

**[Вывод]** В RutCampusTrack градиент полезен для:

- текущей пары и ближайшего обязательного действия;
- перехода от сводки к деталям;
- состояния загрузки/синхронизации;
- подложки карты или пустого состояния.

Он не должен кодировать присутствие/уважительную/пропуск: для этого уже есть семантические зелёный, янтарный, красный и серый.

### 2.4. Радиусы должны быть системой, а не набором случайностей

**[Находка, A]** Apple в 2025 описывает три семейства: fixed radius, capsule, concentric. Для вложенных форм внутренний радиус получается из внешнего с учётом padding; это сохраняет «концентричность». Капсулы уместны для touch-friendly controls и акцентных действий; случайно «зажатые» или чрезмерно распахнутые углы ломают баланс. Источник: *Get to know the new design system* — <https://developer.apple.com/videos/play/wwdc2025/356/>.

**[Вывод]** Для мобильной ветки RutCampusTrack достаточно текущих `radius/md=10`, `lg=12`, `xl=16`, `2xl=24`, `full`. Новый 32 px токен нужен только если реальный макет sheet/hero не выглядит достаточно мягко; начинать с 24 px.

### 2.5. Морф полезен, когда сохраняет происхождение объекта

**[Находка, A]** Android Compose рекомендует shared-element transition для list→detail, когда заголовок/изображение/границы продолжаются на новом экране. Apple iOS 18 zoom transition заставляет нажатую ячейку морфировать в detail и остаётся interruptible. Материальный container transform применяется к list item→details и chip→floating card. Источники:

- Android, *Shared element transitions in Compose* — <https://developer.android.com/develop/ui/compose/animation/shared-elements>
- Apple, *Enhance your UI animations and transitions*, WWDC24 — <https://developer.apple.com/videos/play/wwdc2024/10145/>
- Material, *Building Transitions with Material Motion for Android*, 01.09.2020 — <https://m3.material.io/blog/android-material-motion>

**[Находка, A]** Для PWA same-document View Transitions стали достаточно зрелыми как progressive enhancement; неподдерживающий браузер просто получает обычный переход. Нужно уважать `prefers-reduced-motion`. Источники:

- MDN, *View Transition API*, обновлено 19.06.2026 — <https://developer.mozilla.org/en-US/docs/Web/API/View_Transition_API>
- WebKit, *Two lines of Cross-Document View Transitions…*, 21.05.2025 — <https://webkit.org/blog/16967/two-lines-of-cross-document-view-transitions-code-you-can-use-on-every-website-today/>
- web.dev, *View transitions for single page applications*, 27.08.2025 — <https://web.dev/learn/css/view-transitions-spas>

**[Вывод]** Лучшие кандидаты на морф:

- карточка текущей пары → журнал этой пары;
- строка студента → карточка студента;
- карточка тикета → решение;
- компактный фильтр → supporting sheet.

Не надо морфировать переход между несвязанными верхнеуровневыми вкладками: там достаточно короткого fade-through.

### 2.6. Мобильное приложение — не сжатый desktop

**[Находка, A]** Linear прямо называет мобильное приложение purpose-designed для “away from keyboard” workflows. Android 2026 рекомендует compact-экран как один pane: list→detail, feed или supporting pane; presentation может меняться между sheet и pane на большем размере. Источники:

- Linear Mobile — <https://linear.app/changelog/2024-09-19-introducing-linear-mobile>
- Android, *Common layouts*, обновлено 21.07.2026 — <https://developer.android.com/design/ui/mobile/guides/layout-and-content/common-layouts>
- Android, *Adapt layouts*, обновлено 21.07.2026 — <https://developer.android.com/design/ui/mobile/guides/layout-and-content/adapt-layout>

**[Вывод]** Для телефона проектируется не «экран 1920 в одну колонку», а отдельная единица работы: текущая пара, очередь исключений, один человек, один фильтр, один commit.

## 3. Геометрия: рекомендуемая мобильная шкала

Ниже — **[Предложение]**, согласованное с текущей шкалой кита и официальными mobile-гайдами. Это стартовые значения для прототипа, не универсальная истина.

**Основание:** Android для compact layout задаёт стандартный внешний margin 16 dp; Apple требует минимум 44×44 pt, Android обычно ориентируется на 48 dp. Источники:

- Android, *Content composition and structure*, 21.07.2026 — <https://developer.android.com/design/ui/mobile/guides/layout-and-content/content-structure>
- Apple HIG, *Buttons* — <https://developer.apple.com/design/human-interface-guidelines/buttons>
- проектный токен `control/min-touch=44`.

| Объект | Рекомендуемое значение | Комментарий |
|---|---:|---|
| Базовый ритм | 4 px | Уже совпадает с `space/1` |
| Горизонтальный page gutter | 16 px | 12 px только на 320–359 px и только для плотного списка |
| 4-колоночная сетка | 4 колонки, gap 8–12 px | Для dashboard/bento; рабочие списки могут быть одной колонкой |
| Внутренний padding карточки | 12 px compact; 16 px default; 20 px hero | Не применять 20 ко всем строкам |
| Gap между строками внутри блока | 8 px | Для одного смыслового списка |
| Gap между карточками | 12 px | 16 px для крупных dashboard-карт |
| Между смысловыми секциями | 24–32 px | Вместо лишних разделителей и подписей |
| Высота компактной строки | 48 px | Только имя + один показатель + действие |
| Высота обычной строки | 56 px | Базовый roster/users/groups |
| Высота богатой строки | 64–72 px | Две строки текста, статус, мини-график |
| Primary button | 52–56 px | Визуально крупнее, touch target не меньше 48 |
| Secondary button | 44–48 px | Не мельче min touch |
| Chip / segment | 32–36 px visual, target 44–48 px | Расширять hit box без увеличения графики |
| Icon button | 44–48 px target; icon 20–24 px | Круг или мягкий square |
| Bottom nav | 60–68 px + safe-area | Внутри item target не меньше 48 |
| Sticky filter rail | 48–56 px | Одной строкой; не стекать 2–3 липких бара |
| Sheet | top radius 24 px | Низ идёт в safe area; 28–32 только после теста |

### 3.1. Семантика радиусов

| Токен/форма | Использование | Не использовать |
|---|---|---|
| `radius/md` 10 | поля, compact controls, маленькие popover элементы | hero и sheet |
| `radius/lg` 12 | плотная строка/панель, таблицеподобный блок | плавающий nav |
| `radius/xl` 16 | обычные карточки и supporting blocks | всё без разбора |
| `radius/2xl` 24 | hero/current card, bottom sheet, крупный bento | каждая строка журнала |
| `radius/full` | CTA, chips, segmented control, nav selection | длинные data cards, где capsule раздувает пустоту |

Правило концентричности: `innerRadius = max(outerRadius − gap, semanticMinimum)`. Пример: внешний hero 24, padding 8 → внутренняя плашка 16; внешний sheet 24, вложенный блок при 12 px inset → 12.

### 3.2. Необычная форма с функцией

Допустимы только повторяемые формы с понятным смыслом:

- **Attached tab:** бейдж времени физически «пристыкован» к карточке текущей пары; заменяет отдельную подпись «сейчас».
- **Open edge:** у карточки, которая явно раскрывается вниз, нижние углы в collapsed-состоянии 12, в expanded — 24; форма визуально показывает продолжение.
- **Status notch:** небольшой выступ содержит единственный статус/счётчик исключений; весь выступ кликабелен вместе с карточкой.
- **Liquid selection capsule:** выбранный segment получает capsule, остальные остаются на общем спокойном контейнере.

Не применять случайный набор из облака, звезды, ромба и сквиркла внутри одного списка. Shape vocabulary — максимум 3 базовых семейства на весь продукт.

## 4. Материалы, градиенты, паттерны, elevation

### 4.1. Стекло

**[Предложение]** Стекло разрешить для трёх семейств:

1. `MobileNavGlass` — нижняя навигация.
2. `ContextActionDockGlass` — контекстная панель действия в leaf-flow.
3. `FloatingToolGlass` — управление картой, фильтр над скроллом, единичный popover.

Не использовать на:

- строках студентов;
- KPI-карточках пачкой;
- таблицеподобных реестрах;
- формах с длинным текстом;
- одном стеклянном sheet поверх другого стеклянного бара.

Стартовый диапазон для web-прототипа:

| Свойство | Dark | Light | Fallback |
|---|---:|---:|---|
| Fill | neutral-950, alpha 68–78% | neutral-0, alpha 72–84% | непрозрачный `surface/float` |
| `backdrop-filter` blur | 16–24 px | 14–20 px | без blur |
| Saturation | 110–125% | 105–115% | 100% |
| Border | neutral-100, 8–14% | neutral-900, 6–10% | `border/default` |
| Shadow | текущий `shadow/overlay` | текущий `shadow/overlay` | тот же |

**[Находка, A]** `backdrop-filter` — Baseline Newly available с сентября 2024, но старые устройства остаются; blur и drop-shadow дороже других filters. Источники: MDN, обновлено 20.04.2026 — <https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/backdrop-filter>; Android, *Images and graphics*, 21.07.2026 — <https://developer.android.com/design/ui/mobile/guides/layout-and-content/images-graphics>.

Проверки для стекла:

- contrast на самом светлом и самом тёмном участке подложки;
- `@supports(backdrop-filter)` и solid fallback;
- Reduce Transparency → solid surface;
- low-power/low-end режим → blur 0, без потери иерархии;
- не больше одного полноширинного blur-региона одновременно.

### 4.2. Фиолетовые градиенты из существующей гаммы

**[Предложение]** Не вводить новые hue. Собрать градиенты из текущих violet primitives.

| Семантика | Dark | Light | Применение |
|---|---|---|---|
| `gradient/accent/current` | violet-300 `#B79CFA` → violet-500 `#8557F5` | violet-600 `#6D28D9` → violet-800 `#46198C` | только текущая пара / главный commit |
| `gradient/ambient/violet` | radial violet-900 `#2F1160` → transparent | radial violet-100 `#E7DEFF` → transparent | фон hero, 10–18% visual presence |
| `gradient/progress/violet` | violet-400 → violet-600 | violet-500 → violet-700 | только непрерывный прогресс, не статус посещения |
| `gradient/skeleton/shear` | neutral-950 → neutral-900 → neutral-950 | neutral-100 → neutral-0 → neutral-100 | очень слабый shimmer |

Контраст текста проверять для всего градиента, а не одной точки. Если один цвет не выдерживает contrast, у градиента нет права быть фоном текста; сверху нужен непрозрачный scrim или градиент остаётся чисто декоративным слоем.

### 4.3. Паттерн

**[Предложение]** У продукта есть собственная визуальная метафора — сетка расписания и точки посещения. Это сильнее generic noise/blob.

- `Pattern/Timetable`: тонкая сетка 1 px на 24/32 px, opacity 3–5%; только hero, empty, onboarding.
- `Pattern/AttendanceDots`: редкий ритм из четырёх семантических форм; только иллюстрация/empty, не легенда без слов.
- `Pattern/Route`: тонкая направляющая линия для перехода корпус→этаж→аудитория.
- `Pattern/Noise`: максимум 1–2% для banding у больших градиентов; не должен быть виден как «грязь».

### 4.4. Elevation

Оставить три воспринимаемых уровня, иначе dark UI превращается в набор плавающих слоёв:

1. **Content:** base/raised, разделение тоном и border; без большой тени.
2. **Raised:** текущий `shadow/raised` для локально приподнятого объекта.
3. **Floating:** текущий `shadow/overlay` для nav/sheet/action dock.

Новый четвёртый уровень не нужен. Glow не является elevation; слабое фиолетовое рассеивание допустимо только у current-state и не должно дублировать тень.

## 5. Состояния и интерактивная обратная связь

### 5.1. Развести `current`, `selected`, `pressed`

| Состояние | Визуальный язык | Поведение |
|---|---|---|
| Current / «сейчас» | единственный фиолетовый gradient/hero, attached time tab, повышенный вес | объект определяется временем/системой |
| Selected | `accent/soft` + `border/accent` + check/маркер | выбор пользователя; может быть несколько |
| Pressed | state layer около 10%; scale 0.985 только у изолированной кнопки | 80–120 ms; строку списка не масштабировать |
| Focus-visible | 2 px `border/focus`, offset 2 px | не заменять hover/pressed |
| Loading | геометрия не меняется; label остаётся или меняется предсказуемо | блокировать повторный commit, но не весь экран |
| Success | symbol + слово + success color | фиолетовый не означает успех |
| Error | inline message рядом с объектом + retry | не только toast |
| Disabled | пониженная интенсивность + объяснение рядом | не оставлять «мёртвую» кнопку без причины |

**[Находка, A]** Material state layers задают ориентиры: hover 8%, focus 10%, pressed 10%, dragged 16%; это полезная стартовая логика для системности, не обязательное буквальное CSS-значение. Источник: <https://m3.material.io/foundations/interaction/states/state-layers>.

### 5.2. Motion

Использовать текущие `duration/fast=120`, `base=180`, `slow=260`. Запросить `duration/morph=320ms` только если переход card→detail на 260 ms теряет читаемость.

| Событие | Duration | Motion |
|---|---:|---|
| press/release | 80–120 ms | state layer, лёгкий scale |
| selection/status | 160–220 ms | color/shape interpolation |
| row expand | 220–280 ms | height + fade; один row за раз |
| card→detail | 280–340 ms | shared bounds/container transform |
| sheet | 300–380 ms | spring или decelerate; interruptible |
| top-level tab | 160–220 ms | fade-through, без пространственной фикции |

**[Находка, A]** Material даёт 200 ms для короткого локального перехода; старый, но практичный Material speed guide указывает около 300 ms для mobile transition и предупреждает, что более 400 ms часто ощущается медленно. Источники: <https://m3.material.io/styles/motion/easing-and-duration>; <https://m1.material.io/motion/duration-easing.html>.

Reduced Motion:

- убрать scale/zoom и упругость;
- оставить 100–160 ms crossfade и изменение цвета;
- не отключать обратную связь полностью.

### 5.3. Haptics

**[Находка, A]** Android: less is more; frequent события получают очень слабый feedback, submit сильнее toggle, predefined action constants предпочтительнее самодельных вибраций. Android, обновлено 26.02.2026 — <https://developer.android.com/develop/ui/views/haptics/haptics-principles>.

**[Предложение]** Для RutCampusTrack:

- лёгкий selection tick после изменения статуса студента;
- confirm только после успешного сохранения всей пары/домашнего задания;
- reject/error — один раз, не на каждую невалидную букву;
- haptic никогда не единственный носитель состояния;
- в PWA это progressive enhancement: отсутствие API не меняет UX.

## 6. Sticky regions, bottom navigation и contextual actions

### 6.1. Нижняя навигация

**[Находка, A]** Material рекомендует 3–5 top-level destinations; Apple называет tab bar persistent top-level navigation и в iOS 26 позволяет уменьшать его при scroll down и раскрывать обратно при обратном скролле. Источники: <https://m3.material.io/components/navigation-bar/guidelines>; <https://developer.apple.com/design/human-interface-guidelines/tab-bars>; WWDC25 SwiftUI — <https://developer.apple.com/videos/play/wwdc2025/323/>.

**[Предложение]** Для текущего решения на пять слотов:

- floating capsule, 8–12 px от боков, 8 px от safe area;
- все пять имеют короткую видимую подпись на 360–430 px; selected получает внутреннюю capsule/tonal fill;
- на long read-only экранах панель может уменьшаться до 48 px при scroll down, но подписи возвращаются при scroll up;
- на рабочем roster/journal панель не прячется сама: человек многократно меняет статусы и не должен ловить навигацию;
- персонализация порядка — возможная фаза 2 для старосты, по примеру Linear; сначала собрать telemetry.

### 6.2. Контекстное действие

Не ставить одновременно bottom nav + FAB + sticky submit. В leaf-flow применить один из режимов:

- **Browse mode:** bottom nav виден, contextual action находится в контенте.
- **Task mode:** bottom nav заменяется `ContextActionDock` с «Сохранить/Завершить» и вторичным действием; back остаётся наверху.
- **Keyboard mode:** nav скрыт; dock или submit поднимается над keyboard.

FAB уместен только для действительно глобального create на конкретной роли. У студента нет универсального «создать». У старосты «Отметить посещаемость» лучше как current lesson action, а не FAB на карте и настройках.

### 6.3. Sticky

- Максимум две sticky-зоны: app/context header и одна filter/action rail.
- Заголовок секции закреплять только если список длиннее viewport и без него теряется смысл.
- Sticky-фильтр при прокрутке может сжиматься из полной строки в capsule с текущим значением и счётчиком активных фильтров.
- На границе sticky и content применять один scroll-edge fade/blur, а не border + shadow + blur одновременно.
- У карты: floating controls исчезают, когда supporting sheet раскрывается выше medium detent — модель Apple Maps.

## 7. Progressive disclosure и логика раскрытия

### 7.1. Выбор паттерна

| Ситуация | Паттерн | Почему |
|---|---|---|
| 1–2 дополнительных поля, остаёмся в списке | inline lens / accordion | сохраняет контекст и scroll position |
| Много полей или редактирование | list→detail | compact screen = один pane |
| Фильтры, сортировка, краткая помощь | supporting bottom sheet | временная задача параллельно основному экрану |
| Многошаговый commit, keyboard | full-screen flow | sheet становится тесным и нестабильным |
| Быстрый выбор 1 из 3–5 | anchored popover/segment | не вырывает из контекста |
| Сравнение 2–3 объектов | comparison tray → detail sheet/page | сохраняет возможность сравнивать без матрицы |

**[Находка, B]** Accordions экономят место, но снижают discoverability и добавляют interaction cost; заголовок обязан ясно предсказывать содержимое. NN/g, 30.07.2023 — <https://www.nngroup.com/articles/accordions-on-desktop/>.

### 7.2. Правила inline lens

- раскрыт максимум один студент/тикет одновременно;
- collapsed row уже показывает имя, главный показатель и явный chevron/«Подробнее»;
- раскрытие происходит под строкой, заголовок остаётся на месте;
- повторный тап сворачивает;
- при раскрытии далеко внизу viewport мягко довести строку до видимой области, не прыгать в начало;
- важное действие не прятать в раскрытие, если оно нужно на каждой строке;
- screen reader получает `expanded/collapsed` и связь с panel.

### 7.3. Жесты

Свайп может ускорять, но не быть единственным путём. Apple требует alternatives to gestures; NN/g отмечает низкую обнаруживаемость hidden actions. Источники: <https://developer.apple.com/design/human-interface-guidelines/accessibility>; <https://www.nngroup.com/articles/contextual-menus-guidelines/>.

Для посещаемости:

- tap по видимому статусу — основной путь;
- swipe left/right — optional shortcut после первого использования;
- краткая micro-hint показывается в момент первой успешной отметки, а не отдельным onboarding из пяти coach marks;
- undo остаётся доступным 4–6 секунд.

## 8. Необычные, но удобные модели журнала и статистики

### 8.1. Почему нужны несколько проекций

**[Находка, B]** NN/g разделяет задачи таблицы: найти записи, сравнить, открыть/изменить одну строку, выполнить действия над записями. Таблица хороша для сравнения и паттернов, карточки — для отдельных объектов; нельзя заменять таблицу карточками без понимания задачи. Источник: Page Laubheimer, 03.04.2022 — <https://www.nngroup.com/articles/data-tables/>.

**[Находка, B]** В shipped Vanguard Mobile базовый экран показывает короткий список ключевых показателей, а DETAILED переключает на mobile-optimized table с сортировкой; это progressive disclosure, которое сохранило power-user путь. NN/g, 04.06.2023 — <https://www.nngroup.com/articles/vanguard-mobile-app/>.

**[Находка, B]** Nicholas Heal предлагает заменить разные табличные задачи разными моделями: list для перечня, graph для сравнения, wizard для последовательной обработки, dashboard для выводов, search/filter для поиска индикатора. UX Collective, 06.10.2020 — <https://uxdesign.cc/five-ways-to-replace-a-table-and-make-it-better-on-mobile-f9cc3daf8258>.

### 8.2. Модели для посещаемости старосты

#### A. Exception Inbox — «сначала то, что требует решения»

**Структура:** текущая пара → summary `24 студента / 3 требуют решения` → карточки только `нет гео`, `опоздание`, `уважительная`, `не отмечен` → «Все студенты» вторичным пунктом.

- Основная задача: разбирать отклонения, не перечитывать норму.
- Действия: Approve/Reject/Clarify прямо в карточке или в detail.
- Цена: не подходит для первичной отметки с нуля; нужен полный roster как соседний режим.
- Рекомендация: **высокая**, особенно после окончания окна check-in.

#### B. Focus Deck — «один студент, один статус, следующий»

**Структура:** progress `7/24` → одна большая карточка студента → четыре видимых status-actions → после выбора карточка уходит вверх, следующая приходит снизу. Внизу «Назад к списку».

- Основная задача: быстро пройти всю группу последовательно.
- Одно действие на студента; status rail остаётся в одном месте под большим пальцем.
- Можно поддержать «предыдущий» и undo; никакого hidden swipe как единственного управления.
- Цена: теряется обзор всей группы и сравнение; не подходит для хаотичного вызова фамилий.
- Рекомендация: **вариант режима**, включаемый кнопкой «Быстрая отметка», не default.

#### C. Roster Lens — «список с локальным раскрытием»

**Структура:** плотные строки 56 px; справа текущий status chip. Тап строки раскрывает 96–140 px panel с причиной, временем check-in, последними 3 занятиями и действиями. Другой row закрывает предыдущий.

- Сохраняет обзор и даёт деталь без отдельного экрана.
- Shared-bounds morph делает связь очевидной.
- Цена: вертикальные прыжки; необходимо сохранять anchor.
- Рекомендация: **базовый default** для смешанной проверки/редактирования.

#### D. Status Rail — «статус раскрывается из текущего статуса»

**Структура:** в resting row виден текущий статус. Тап превращает chip в горизонтальный rail из `✓ / Ув. / × / —` с подписями или компактными символами + accessible labels; после выбора rail схлопывается.

- Экономит ширину, но сохраняет понятный origin.
- Цена: два тапа вместо одного. Поэтому для Focus Deck все статусы видимы сразу, для обычного roster rail допустим.
- Рекомендация: тест A/B против всегда видимых трёх status-buttons.

#### E. Attendance Ribbon — «последние занятия как маленькая временная лента»

**Структура:** рядом с именем 6–10 сегментов последних занятий. Цвет + форма + tooltip/detail; тап ленты открывает history sheet по датам.

- Хорошо показывает повторяющиеся пропуски без матрицы.
- Нельзя заменять форму только цветом: present = check/circle, excused = clock/rounded square, absent = cross/diamond, none = hollow mark.
- Цена: это обзор, не место первичного редактирования; нужен legend по запросу.
- Рекомендация: в headman stats и student detail, не в главном marking mode.

### 8.3. Модели для статистики

#### F. Metric Spotlight — «метрика выбирает список»

**Структура:** sticky metric switch `Посещение / Пропуски / Уважительные / Динамика`; ниже список студентов, отсортированный по выбранной метрике; в строке одно число + microbar/sparkline.

- Это мобильный pivot: пользователь меняет ось, а не скроллит матрицу.
- Цена: нельзя одновременно сравнить все метрики.
- Рекомендация: **основной экран 113 mobile**.

#### G. Compare Tray — «сравнить максимум троих»

**Структура:** режим «Сравнить» → checkbox/plus на строках → bottom tray с аватарами/инициалами, максимум 3 → экран small multiples: одинаковые шкалы, одинаковый период.

- Возвращает настоящую side-by-side comparison без 20 колонок.
- Ограничение в 3 снижает память и ширину.
- Цена: дополнительный режим и выбор; не нужен большинству чтений.
- Рекомендация: power-user путь, не главный CTA.

#### H. Dual-axis Pivot — «Люди / Пары / Предметы»

**Структура:** один segmented control меняет единицу списка. `Люди` показывает student cards; `Пары` — занятия с распределением статусов; `Предметы` — агрегаты и тренд.

- Помогает ответить на разные вопросы без отдельных дублирующих экранов.
- Цена: три состояния экрана и необходимость сохранять filter/date range.
- Рекомендация: полезно старосте и преподавателю, но прототипировать названия — «Пары» и «Занятия» могут конкурировать.

#### I. Change Story — «что изменилось»

**Структура:** сверху 1–3 карточки инсайта: `Пропуски выросли на 6 п.п. за 2 недели`; под ней sparkline и ссылка «К студентам». Это серверный вывод или строго детерминированное правило, не декоративный AI-текст.

- Снимает с пользователя вычисление итога.
- Цена: нужны определения порога, периода и доверия к агрегации.
- Рекомендация: summary, никогда не единственный доступ к исходным данным.

#### J. Calendar Heat Ribbon — «время как непрерывная полоса»

**Структура:** вместо календарной матрицы — горизонтальная, snap-to-period лента недель; высота/интенсивность сегмента = доля пропусков; выбор недели обновляет список событий ниже.

- Хорошо для тренда и выбора периода одним пальцем.
- Цена: слабее для точного чтения; всегда показывать число и дату выбранного сегмента.
- Рекомендация: статистика и student detail, не административный реестр.

### 8.4. Если матрица всё-таки нужна

Иногда задача действительно сравнивать много рядов. Тогда не маскировать её карточками:

- только 2–4 видимые колонки;
- sticky header и первая колонка;
- крайняя колонка обрезана на 12–20 px как явная подсказка горизонтального скролла;
- control «Поля» для выбора колонок;
- compact/detail density;
- landscape как дополнительный, не обязательный путь;
- никаких «поверните устройство, чтобы продолжить».

Основание: NN/g, *Mobile Tables*, 17.09.2017 — <https://www.nngroup.com/articles/mobile-tables/>.

## 9. Keyboard, safe area, edge-to-edge

### 9.1. PWA layout contract

- Использовать `100dvh`, не фиксированный `100vh`; предусмотреть fallback.
- `viewport-fit=cover` только вместе с `env(safe-area-inset-*)`.
- Background/gradient может уходить под системные области; текст и controls — нет.
- Нижний padding scroll container = nav/dock height + safe-area + 12 px.
- При keyboard bottom nav скрывается; активное поле и submit остаются видимыми.
- Если поддержан VirtualKeyboard API, keyboard geometry — enhancement; базовый layout обязан работать без него.

Источники:

- web.dev, *App design*, 20.09.2024 — <https://web.dev/learn/pwa/app-design>
- MDN, *VirtualKeyboard API*, обновлено 06.11.2025 — <https://developer.mozilla.org/en-US/docs/Web/API/VirtualKeyboard_API>
- web.dev, *The large, small, and dynamic viewport units*, 29.11.2022 — <https://web.dev/blog/viewport-units>

### 9.2. Keyboard behavior

- Многострочный комментарий/домашнее задание: composer прикреплён к keyboard, контент скроллится отдельно.
- Нельзя оставлять sticky nav под клавиатурой и второй sticky submit над ней.
- При переходе next field не закрывать keyboard.
- Ошибка появляется возле поля и не выталкивает submit за viewport; заранее резервировать 20–24 px для helper/error, если ошибки часты.
- После submit keyboard закрывается только при успешном переходе или явном Done, не при каждом фоновой автосохранении.

Android подтверждает `adjustResize`/IME padding и необходимость держать controls над keyboard: <https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility>, <https://developer.android.com/develop/ui/compose/system/insets-ui>.

## 10. Loading, empty, error, offline

### 10.1. Loading

**[Находка, A]** M3 loading indicator предназначен для короткого ожидания примерно 200 ms–5 s. Источник: <https://m3.material.io/components/loading-indicator/guidelines>.

Рекомендации:

- <200 ms: не мелькать indicator.
- 200 ms–1.5 s: локальный indicator в нажатой кнопке/области.
- Структурная загрузка страницы: skeleton с точной будущей геометрией; не строить декоративный shimmer из десяти блоков.
- >5 s: объяснение, что загружается, retry/cancel или determinate progress, если можно посчитать.
- При background refresh не заменять cached content skeleton-ом: показать `Обновляем…` в компактной status capsule.

### 10.2. Empty

Три разных empty-state, не один generic:

1. **Первичная пустота:** «Домашних заданий пока нет» + что произойдёт дальше; без CTA, если студент ничего не может сделать.
2. **Пустой фильтр:** «По этим фильтрам ничего нет» + `Сбросить фильтры`.
3. **Успешно разобрано:** «Все исключения обработаны» + summary результата, а не грустная пустота.

Иллюстрация/pattern занимает не более 25–30% первого viewport и не выталкивает смысл/действие вниз.

### 10.3. Error

- Ошибка строки остаётся в строке; данные вокруг не исчезают.
- Optimistic attendance update: pending ring → успех; при fail вернуть прошлый статус, показать inline retry и сохранить фокус.
- Toast подходит как подтверждение, но не как единственный носитель ошибки.
- Full-screen error только если нет даже cached shell/content.

### 10.4. Offline

**[Находка, A]** Установленная PWA не должна показывать browser-default offline page; cached content и custom fallback обязательны для app-like ощущения. Источники: web.dev, *What makes a good PWA?*, 19.09.2024 — <https://web.dev/articles/pwa-checklist>; *Create an offline fallback page* — <https://web.dev/articles/offline-fallback-page>.

Рекомендации:

- persistent, но компактный chip `Офлайн · данные на 10:42`;
- cached расписание, домашка и последняя статистика остаются читаемыми;
- network-only действия остаются видимыми с пояснением, а не исчезают;
- локальные допустимые черновики получают `Ждёт отправки` и явную очередь;
- check-in/гео, если серверная валидация обязательна, не выдаёт ложный успех;
- reconnect обновляет только изменённые блоки, не сбрасывает scroll.

## 11. Четыре визуальных направления

### Direction 1 — Quiet Liquid (рекомендуемый каркас)

**Образ:** тёмный спокойный контент, над ним один тонкий текучий слой управления.

- base `neutral/1000`, raised `neutral/950`;
- glass nav/action dock;
- current hero: violet-300→500 в dark;
- обычные карточки 16, hero/sheet 24, controls capsule;
- тонкий ambient violet radial glow за hero;
- morph только связанных объектов.

Лучше всего: все роли, карта, текущая пара, homework publish.  
Плюсы: узнаваемость, современность, невысокий визуальный шум.  
Минусы: качественное стекло требует contrast/performance fallback.

### Direction 2 — Expressive Bento

**Образ:** разный масштаб карточек, один крупный ключевой action, мягкие формы и видимый ритм.

- 4-column grid;
- hero на 4 колонки, KPI 2+2, quick actions 1–2 колонки;
- 16/24 radii; одна attached-tab форма;
- gradient только у главной задачи;
- progress и selected states увеличены, а не просто окрашены.

Лучше всего: студент/главная, статистическая сводка, админ/главная.  
Плюсы: быстрый scan, эмоциональность; Google research поддерживает увеличение/containment ключевых действий.  
Минусы: в журнале превращается в длинный masonry и снижает плотность; туда не переносить.

### Direction 3 — Signal Grid

**Образ:** «цифровое табло кампуса»: строгий список + живой текущий сигнал.

- спокойные `surface/raised`, тонкие borders;
- 48/56 px rows;
- pattern timetable только в header/empty;
- status ribbon, microbars, цифры с tabular numerals;
- current row выделен не заливкой всего экрана, а attached time tab + side signal.

Лучше всего: посещаемость, группа, предметы, пользователи, тикеты.  
Плюсы: максимальная рабочая плотность, связь с существующим вебом.  
Минусы: без Quiet Liquid оболочки может выглядеть слишком утилитарно.

### Direction 4 — Aurora Flow

**Образ:** фиолетовая направленная «энергия», мягкие organic shapes, плавная реакция.

- animated gradient blob/mesh только за пустым состоянием, загрузкой или current task;
- organic shape никогда не является единственным affordance;
- inner activity для `синхронизируем`, движение от source к destination;
- Reduce Motion → static gradient/crossfade.

Лучше всего: загрузка маршрута, check-in, onboarding, empty/success, карта.  
Плюсы: запоминаемость и ощущение современного продукта.  
Минусы: высокий риск GPU/battery/contrast; нельзя делать общей подложкой всех data screens.

### Рекомендуемая сборка

- App shell: Quiet Liquid.
- Student/teacher home: 60% Quiet Liquid + 40% Expressive Bento.
- Headman/admin work screens: 70% Signal Grid + 30% Quiet Liquid.
- State illustrations: Aurora Flow, максимум один живой объект на экране.

## 12. Запросы на токены, а не произвольные значения

Пользователь разрешил слегка развить токены в той же фиолетовой гамме. Это надо оформить отдельным mobile decision/override, потому что текущий kit запрещает gradients/glow как общее правило. Кит не менять молча.

Минимальный пакет запросов:

1. `gradient/accent/current` — dark/light aliases из существующих violet primitives.
2. `gradient/ambient/violet` — radial/mesh recipe + max opacity.
3. `material/glass/nav/fill` — dark/light alpha surface.
4. `material/glass/nav/blur` — 20 px default, 0 fallback/reduced.
5. `material/glass/nav/border` — alpha border.
6. `material/glass/fallback` — solid `surface/float`.
7. `pattern/timetable/stroke` и `pattern/timetable/size` — если паттерн принят.
8. `duration/morph` 320 ms — только если `slow=260` недостаточно.
9. `shape/attached-tab` — component recipe, не primitive radius.

Не добавлять без теста:

- ещё 5 уровней тени;
- отдельный набор «mobile purple» вне текущей шкалы;
- десятки organic SVG shapes;
- radius 28/30/32 одновременно;
- gradient для каждого статуса.

## 13. Антипаттерны

1. **Glass everywhere:** glass cards + glass nav + glass sheet; теряется иерархия и contrast.
2. **Все карточки 24–32 px:** плотный журнал выглядит игрушечным и растягивается.
3. **Случайная асимметрия:** форма не повторяется и ничего не означает.
4. **Градиент как статус посещения:** ломает существующую семантику.
5. **Пять разных purple accents на одном экране:** «сейчас» перестаёт быть главным.
6. **Icon-only для незнакомых действий:** Google research показал падение usability без labels.
7. **Hidden swipe как основной путь:** действие не обнаруживается и конфликтует с системными gestures.
8. **Accordion у каждой строки с 10 полями:** список становится длиннее исходной таблицы.
9. **Carousel для важного:** последовательный доступ и низкая обнаруживаемость; не прятать обязательные данные.
10. **Bottom nav + FAB + sticky submit:** три конкурирующих нижних слоя.
11. **Blur над scrollable text:** постоянная перерисовка, шум и плохой contrast.
12. **Morph между несвязанными экранами:** создаёт ложную пространственную связь.
13. **Infinite scroll в реестре:** трудно вернуться, оценить объём и закончить задачу; лучше pagination/period chunks.
14. **Skeleton после уже показанного cache:** воспринимается как потеря данных.
15. **Empty illustration больше смысла:** CTA уезжает за первый viewport.
16. **Disabled без причины:** пользователь не понимает, что исправить.

## 14. Что проверить на прототипе

### Геометрия

- 320×568, 360×800, 390×844, 430×932.
- Dynamic Type/200% text, длинные ФИО, предметы, аудитории.
- Нижний safe area 0/20/34 px, landscape, split/foldable narrow pane.

### Контраст и эффекты

- glass на base, raised, карте, ярком gradient;
- Reduce Transparency, Increased Contrast, Reduce Motion;
- low-end Android: scroll 60 fps с nav blur;
- dark OLED smear: тонкие серые borders и purple gradient.

### Рабочие сценарии

- 24 студента: Roster Lens против Focus Deck; время и ошибки.
- 3 исключения: Exception Inbox против полного списка.
- Metric Spotlight: найти 3 худших результата.
- Compare Tray: сравнить 2 и 3 студентов.
- offline status change: понятно ли, что не сохранено.
- keyboard: publish homework и reject reason на 320 px.

### Метрики теста

- время до первого правильного действия;
- taps на одного студента и на завершение пары;
- число исправлений статуса;
- заметили ли users скрытые детали/filter state;
- scroll distance;
- субъективная уверенность после commit;
- GPU frame drops/long tasks на реальных клиентах.

## 15. Визуальные референсы C-уровня

Эти страницы использовать только для moodboard: композиция, сочетание фиолетового, мягкость, фактура. Не копировать interaction без проверки.

| Референс | Дата | Что смотреть | Что не считать доказанным |
|---|---:|---|---|
| Dribbble, *Query AI Study Dashboard* | 04.03.2026 | deep purple-blue gradient, soft glass, modular cards | удобство, contrast, shipped status |
| <https://dribbble.com/shots/27083917-Query-AI-Study-Dashboard-Snap-Learning-App-UI-UX-Design> | | | |
| Behance, *Glassy* | 26.11.2021 | light/dark glass mechanics; автор сам фиксирует перегрузку | mobile performance, реальная эксплуатация |
| <https://www.behance.net/gallery/132034949/Glassy> | | | |
| Behance, Mary Yurko, *Skillbox redesign* | 04.07.2021 | education + mobile + 3D/glass composition | соответствие сценариям RutCampusTrack |
| <https://www.behance.net/gallery/122483843/Skillbox-redesign> | | | |
| Dribbble, Paperpillar, *Glass UI Elements* | 18.10.2020 | violet palette, cards/charts | доступность и task success |
| <https://dribbble.com/shots/14407519-Glass-UI-Elements> | | | |
| Behance, *AI Gamified eLearning Mobile App* | 02.02.2026 | modular cards, emotional/seasonal layer | влияние «сезонности» на retention |
| <https://www.behance.net/gallery/243364259/AI-Gamified-eLearning-Mobile-App-Dashboard-UI-UX> | | | |

## 16. Главный source ledger

| Доверие | Источник | Автор/продукт | Дата | Что поддерживает |
|---|---|---|---:|---|
| A | <https://design.google/library/expressive-material-design-google-research> | Google Material Research | 05.2025 | expressive tactics, 46 studies/18k, benefits and failure cases |
| A | <https://developer.apple.com/videos/play/wwdc2025/219/> | Apple Design Team | 06.2025 | glass only navigation/control layer, avoid glass-on-glass |
| A | <https://developer.apple.com/videos/play/wwdc2025/356/> | Apple Design Team | 06.2025 | concentric radii, grouping, labels, sticky edge effects |
| A | <https://developer.apple.com/videos/play/wwdc2025/284/> | Apple UIKit / Apple Maps | 06.2025 | remove floating glass when sheet expands |
| A | <https://developer.apple.com/videos/play/wwdc2024/10145/> | Apple SwiftUI/UIKit | 06.2024 | interruptible zoom list→detail |
| A | <https://developer.android.com/develop/ui/compose/animation/shared-elements> | Android | current | shared element list→detail |
| A | <https://developer.android.com/design/ui/mobile/guides/layout-and-content/common-layouts> | Android | 21.07.2026 | list-detail/feed/supporting pane on compact screens |
| A | <https://developer.android.com/design/ui/mobile/guides/layout-and-content/content-structure> | Android | 21.07.2026 | 16 dp compact margins, containment, pinned content |
| A | <https://linear.app/changelog/2025-10-16-mobile-app-redesign> | Linear Mobile | 16.10.2025 | shipped custom frosted glass + bottom toolbar |
| A | <https://linear.app/changelog/2026-01-22-customize-your-navigation-in-linear-mobile> | Linear Mobile | 22.01.2026 | customizable mobile navigation/pins |
| A | <https://design.google/library/gemini-ai-visual-design> | Gemini design team | 2025 | directional gradients and intentional motion |
| B | <https://www.nngroup.com/articles/glassmorphism/> | Megan Brown, NN/g | 07.06.2024 | glass accessibility, contrast, blur |
| B | <https://www.nngroup.com/articles/data-tables/> | Page Laubheimer, NN/g | 03.04.2022 | table tasks; cards vs comparison |
| B | <https://www.nngroup.com/articles/mobile-tables/> | NN/g | 17.09.2017 | sticky header/column, scroll cues, select data |
| B | <https://www.nngroup.com/articles/vanguard-mobile-app/> | NN/g / Vanguard | 04.06.2023 | basic/detail progressive disclosure in shipped finance app |
| B | <https://uxdesign.cc/five-ways-to-replace-a-table-and-make-it-better-on-mobile-f9cc3daf8258> | Nicholas Heal | 06.10.2020 | list/graph/wizard/dashboard/search alternatives |
| B | <https://www.uxmatters.com/mt/archives/2020/07/designing-mobile-tables.php> | Steven Hoober | 06.07.2020 | preserve true table tasks; reduce columns/data |
| A | <https://web.dev/learn/pwa/app-design> | web.dev | 20.09.2024 | standalone app, safe areas, theme/motion |
| A | <https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/backdrop-filter> | MDN | 20.04.2026 | backdrop-filter support/fallback |
| A | <https://developer.mozilla.org/en-US/docs/Web/API/VirtualKeyboard_API> | MDN | 06.11.2025 | keyboard geometry enhancement |
| A | <https://web.dev/articles/pwa-checklist> | web.dev | 19.09.2024 | app-like offline fallback |

## 17. Передача главному агенту: решения, которые стоит вынести в итоговый документ

1. Указать, что запрет кита на gradients/glow не надо тихо нарушать: оформить **mobile visual evolution** и минимальный token request из §12.
2. Рекомендовать гибрид Quiet Liquid + Expressive Bento + Signal Grid; Aurora — редкий state layer.
3. Glass только nav/action/map controls; data cards остаются tonal/opaque.
4. В журнале показать как минимум три альтернативы: Roster Lens (default), Exception Inbox, Focus Deck.
5. В статистике: Metric Spotlight (default), Compare Tray до 3, Attendance/Heat Ribbon.
6. Развести `current`, `selected`, `pressed`; purple gradient принадлежит current/primary, а не всем состояниям.
7. Task mode заменяет bottom nav контекстным dock; нижние слои не стекать.
8. Card→detail morph — progressive enhancement и only when origin/destination связаны.
9. В спецификации макетов дать конкретные диапазоны spacing/radius из §3 и fallback для glass/motion/offline.
10. Для Figma-агента предусмотреть обязательные состояния: resting, pressed, selected, loading, success, inline error, empty-filter, empty-success, offline-cached, reduced transparency, reduced motion.

