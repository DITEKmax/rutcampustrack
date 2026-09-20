# Figma state ledger — часть 2 «Действия и формы»

Дата синхронизации: 07.08.2026  
Файл: `VgVjQYWILLG9AC7Eh12VMk`  
Страница: `2. Действия и формы` (`47:2`)  
Корневой frame: `Part 2 / Root` (`48:2`)

## Переменные

- `color/surface/inverse` — `VariableID:51:2`
- `color/surface/inverse-hover` — `VariableID:51:3`
- `color/surface/inverse-pressed` — `VariableID:51:4`
- `color/text/on-inverse` — `VariableID:51:5`
- `color/state/danger/fill-hover` — `VariableID:51:6`
- После синхронизации: Primitives 83, Semantic 69, Sizes 27, Typography 20, Motion 4; битых алиасов 0.

## Component sets партии

| Component set | Node ID | Варианты | Свойства | Статус |
|---|---:|---:|---|---|
| `shared/AppButton` | `70:38` | 28 | `type`, `size`, `state`, content booleans/text, icon swaps, `focus-ring`, `accessible-name` | готов |
| `shared/AppLink` | `54:37` | 4 | `state`, `label`, `external`, `focus-ring` | готов |
| `shared/Checkbox` | `71:86` | 4 | `state`, `checked`, `indeterminate`, `focus-ring`, `has-hint`, тексты | готов |
| `shared/Radio` | `73:89` | 4 | `state`, `checked`, `focus-ring`, `has-hint`, тексты | готов |
| `shared/FormField` | `55:18` | 2 | `state`, `label`, `required`, `has-hint`, `message`, `Control` SLOT | готов |
| `shared/TextInput` | `77:152` | 7 | `state`, `value`, leading/trailing booleans и swaps, `focus-ring` | готов |
| `shared/SelectField` | `80:329` | 8 | `state` | готов, `Popover` — каркас |
| `shared/PasswordField` | `81:208` | 2 | `revealed` | готов |
| `auth/PasswordPolicyHint` | `55:31` | 3 | `state`, `label` | готов |
| `shared/SearchField` | `83:220` | 2 | `has-value`, `result-count` | готов |
| `shared/FileUploadField` | `56:62` | 8 | `state`, `focus-ring` | каркас |
| `shared/DateRangePicker` | `57:81` | 8 | `mode`, `state` | каркас |

Отдельный component: `settings/ThemeSwitcher` — `84:174`, три вложенных экземпляра `Radio`.

## Вложенные зависимости и заглушки

- `AppLink`: `outline/new-window` — опубликованный instance, key `53a6df66d5119920db5b63a88e32236d101c1aa6`; semantic-цвет задаётся маской без detach.
- `FormField`: нативный SLOT `Control`; экземпляр конкретного поля не зашит.
- `SelectField`: 8 экземпляров `TextInput`; открытый вариант содержит подписанный каркас `Popover` и четыре состояния пункта.
- `PasswordField`: по одному `TextInput` и `AppButton ghost compact` в каждом варианте; опубликованные `eye` / `eye-off`, detach не используется.
- `SearchField`: два `TextInput`, в непустом варианте — `AppButton ghost compact` очистки и `result-count`.
- `ThemeSwitcher`: три экземпляра `Radio`, выбран вариант «Как в системе»; это radiogroup, не toggle.
- `FileUploadField`: действия собраны экземплярами `AppButton`; только `ProgressScale` остаётся подписанной заглушкой поздней части. `dashPattern` 4/4 остаётся литералом.
- `DateRangePicker`: поля дат собраны экземплярами `TextInput` с опубликованной иконкой `outline/date`; `Popover` и `MonthCalendar` остаются подписанными заглушками поздних частей.
- Busy-варианты не созданы; в описаниях зависимых компонентов записано: «busy не собран: решение открыто».

## Проверка

- 12 component sets + 1 component, 80 variants; detached instances 0.
- Все 13 секций страницы содержат по одному мастеру; корневой frame использует vertical Auto Layout, промежуток между секциями — 24.
- У локально созданных видимых solid fills/strokes нет цветовых литералов. Два старых литерала SLOT `FormField/Control` заменены на `color/surface/raised`.
- Все текстовые слои используют локальные text styles; missing fonts — 0.
- Проверены dark и light modes, длинные русские строки, открытый `SelectField`, `revealed` у пароля, условные части поиска и поля дат 144 px.
- После визуальной проверки исправлены: высота открытого `SelectField`, маски `eye` / `eye-off`, цвет значения `TextInput/error` и цветовые пути TextInput.
- `Checkbox` и `Radio`: контрол 16×16 центрирован в контейнере первой строки 16×20; текстовая колонка и корень растут по высоте при переносе и `hint`, поэтому контрол остаётся привязан к первой строке. У `Checkbox` галочка уменьшена до 12×12, `indeterminate` — 8×2 с `radius/full`. Проверено в dark/light, на одной и двух строках и с `hint`.
- `SelectField/open`: устранено смещение chevron при развороте. Контейнер сохранён в общей позиции 16×16 (`x=332`, `y=12`), маска `outline/chevron-down` разворачивается на 180° вокруг собственного центра. Проверено в dark/light; вложенные instances и каркас `Popover` не изменены.
- `PasswordPolicyHint/unmet`: маленький текстовый `×` заменён на центрированный векторный крестик 14×14 в том же слоте 14×16, что и галочка `met`; stroke связан с `color/state/danger/text`. Проверено в dark/light, свойства `label` и `state` сохранены.
- `DateRangePicker`: ширина полей дат увеличена с 128 до 144 px — ближайшей ступени существующей шкалы, вмещающей полную дату, внутренние отступы и календарь. Опубликованные календарные instances сохранены и сдвинуты в правый padding (`x=116`, размер 16×16); строки `range` центрируют разделитель (`y=8` при высоте 40). Проверено в dark/light; свойства `mode`/`state` и заглушки `Popover`/`MonthCalendar` не изменены.
- `TextInput/focus-ring`: по решению владельца непрерывное внешнее кольцо заменено четырьмя внутренними угловыми сегментами. Основная граница focused-поля не изменена. Каждый сегмент лежит внутри квадратного frame и построен между концентрическими дугами r=11,5 и r=13,5 относительно `radius/md=10`: зазор до границы 1,5 px, толщина 2 px. Все четыре сегмента получены симметричным отражением одного эталонного контура; исправлена обратная ориентация левого нижнего сегмента. Цвет связан с `color/border/focus`; boolean `focus-ring` и component properties сохранены. Проверено в dark/light на `TextInput`, `SelectField` и `DateRangePicker`; 24 экземпляра TextInput на странице сохранили связь с мастером, отсоединённых экземпляров нет. Решение расходится с формулировкой брендбука о сплошном контуре; запись в реестр расхождений не вносилась.
