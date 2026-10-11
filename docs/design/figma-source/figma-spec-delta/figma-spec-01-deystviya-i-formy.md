# figma-spec-01-действия-и-формы.md

> Проход 01 спецификации компонентов RutCampusTrack.
> Основания, схема имён, правила Auto Layout и полный набор состояний —
> `figma-spec-00-основания.md`. Значения — `brandbook-v2.md` и `tokens-v2.json`.
> Состав компонентов — `COMPONENT_REGISTRY.md` v1.0.

**В этом проходе:** `AppButton`, `AppLink`, `Checkbox`, `Radio`, `FormField`,
`SelectField`, `PasswordField`, `PasswordPolicyHint`, `SearchField`,
`FileUploadField`, `DateRangePicker`, `ThemeSwitcher`.

`Checkbox` и `Radio` отдельными строками в реестре не заведены, но требуются
им прямо: `FilterBar` — «`fieldset` с чекбоксами, не список с галочками»,
`TableColumnFilter` — то же, `ThemeSwitcher` — «группа радиокнопок в `fieldset`,
не тумблер». Это атомы под существующие компоненты, а не новые компоненты
реестра.

---

## 1. `shared/AppButton`

Реестр: кнопка действия, пропы `variant`, `busy`, `disabled`. Применяется везде.

### 1.1. Анатомия

| Часть | Обязательность | Что это |
|---|---|---|
| контейнер | обязательна | Auto Layout горизонтальный, выравнивание по центру |
| `icon-leading` | опционально (`has-icon-leading`) | instance swap, бокс `icon/size/sm` |
| `label` | обязательна, кроме `has-label=false` | text property, стиль `dense-strong` |
| `icon-trailing` | опционально (`has-icon-trailing`) | instance swap, бокс `icon/size/sm` |
| `counter` | опционально (`has-counter`) | число в кнопке, стиль `micro`, приглушено на ступень |
| `ring` | опционально (`focus-ring`) | кольцо фокуса, слой поверх, не влияет на раскладку |
| `busy-indicator` | опционально (`busy`) | заменяет `icon-leading`, ширина кнопки не меняется |

### 1.2. Оси

```
type  = primary | secondary | ghost | danger
size  = regular | compact
state = default | hover | pressed | disabled
```

**Матрица неполная намеренно.** `danger` существует только в `regular`: необратимое
действие живёт в `ConfirmDialog` и в `DestructiveActionPair`, внутрь таблицы оно
не попадает. Итого 3 × 2 × 4 + 1 × 1 × 4 = **28 вариантов**.

Потолок в 24 из §3.3 оснований пробит осознанно, и вот чем заплачено, чтобы
не пробить сильнее:

- **`focused` вынесен в boolean `focus-ring`**, а не в ось. Фокус сосуществует
  с наведением и нажатием — как значение оси он потребовал бы удвоения матрицы
  и всё равно не описал бы «наведён и сфокусирован одновременно».
- **`busy` вынесен в boolean.** Это не состояние кнопки, а признак идущей
  операции: сочетается с `disabled`.
- `error` у кнопки не бывает: ошибка отправки живёт в `FormError`, а не на кнопке.

Разделение на два компонента (`AppButton` и `AppButtonCompact`) рассмотрено
и отклонено: это синоним, а не вариант — правило реестра §1.

### 1.3. Размеры

| | `regular` | `compact` |
|---|---|---|
| зачем | основной размер: формы, шапки, панели, диалоги | панели действий внутри таблиц и строк, где ряд обязан совпасть по высоте со строкой |
| высота | `control/height` | `row-height/compact` |
| padding по горизонтали | `space/4` | `space/3` |
| padding по вертикали | 0 (высота фиксирована) | 0 |
| gap между частями | `space/2` | `space/2` |
| кегль | `dense-strong` | `micro` |
| радиус | `radius/md` | `radius/sm` |
| граница | `border/hairline` | `border/hairline` |
| иконка | `icon/size/sm` | `icon/size/sm` |
| min-width | `space/16` | нет |
| max-width | нет | нет |

**`has-label=false` (кнопка-иконка):** ширина равна высоте, padding `space/2`,
радиус `radius/full`. Обязательна `aria-label` — в Figma это text property
`accessible-name`, не отображаемый слой.

### 1.4. Состояния

**`type=primary`**

| Состояние | Тёмная | Светлая |
|---|---|---|
| default | заливка `color/text/primary`, текст `color/surface/base` | то же (инверсия работает в обеих) |
| hover | заливка на ступень светлее — `color/surface/raised` не годится, берётся `color/text/primary` при `state=hover` со ступенью 0 шкалы | заливка `color/neutral/800` через семантический `color/text/primary` варианта hover |
| pressed | заливка на ступень в обратную сторону, **без сдвига и без масштабирования** | то же |
| disabled | `color/disabled/surface`, текст `color/disabled/text`, граница `color/disabled/border` | то же |

> Инверсная кнопка — единственное место, где заливка берётся из текстовой группы
> токенов. Это следствие приёма «инверсия как сильнейший нейтральный способ
> выделения» из мудборда. Отдельный токен не заводится: пары значений уже есть.

**`type=secondary`**

| Состояние | Заливка | Граница | Текст |
|---|---|---|---|
| default | прозрачная | `color/border/default` | `color/text/primary` |
| hover | `color/surface/raised` | `color/border/strong` | `color/text/primary` |
| pressed | `color/surface/float` | `color/border/strong` | `color/text/primary` |
| disabled | `color/disabled/surface` | `color/disabled/border` | `color/disabled/text` |

**`type=ghost`** — то же, что `secondary`, но граница прозрачная во всех
состояниях, кроме `disabled` (там её тоже нет). Заливка появляется только
на `hover` и `pressed`.

**`type=danger`**

| Состояние | Заливка | Текст |
|---|---|---|
| default | `color/state/danger/fill` | `color/state/danger/on-fill` |
| hover | `color/state/danger/border` | `color/state/danger/on-fill` |
| pressed | `color/state/danger/fill` | `color/state/danger/on-fill` |
| disabled | `color/disabled/surface` | `color/disabled/text` |

**`focus-ring=true`** — во всех типах и состояниях: кольцо `color/border/focus`,
толщина `focus/width`, отступ `focus/offset` наружу. Кольцо не меняет габарит
кнопки в Auto Layout.

**`busy=true`** — индикатор на месте `icon-leading`, текст остаётся видимым,
ширина не меняется. Кнопка недоступна, но визуально не `disabled`: пользователь
должен понимать, что операция идёт, а не что действие запрещено.

### 1.5. Переполнение

Кнопка растёт по ширине под текст — обрезки нет. Причина: подпись кнопки называет
действие, обрезанное действие непонятно. Если подпись не помещается в раскладку,
правится раскладка, а не кнопка.

Перенос на вторую строку запрещён: высота фиксирована токеном.

### 1.6. Слоты

`icon-leading` и `icon-trailing` принимают только иконку. Ничего другого внутрь
кнопки не вкладывается.

---

## 2. `shared/AppLink`

Реестр: навигационная ссылка, настоящий `<a>`. Внешняя — с видимым доменом.
Скачивание готового ассета — тоже ссылка, не кнопка.

### 2.1. Анатомия

| Часть | Обязательность |
|---|---|
| `label` | обязательна, text property |
| `icon-external` | опционально (`external`), `icon/size/sm`, после подписи |
| подчёркивание | обязательно, постоянное |

### 2.2. Оси и размеры

```
state = default | hover | pressed | disabled
```

Размерных вариантов нет. Ссылка наследует кегль окружающего текста — это часть
абзаца, а не самостоятельный контрол. `focus-ring` — boolean.

### 2.3. Состояния

| Состояние | Текст | Подчёркивание |
|---|---|---|
| default | `color/text/primary` | `border/hairline` |
| hover | `color/text/primary` | `border/emphasis` |
| pressed | `color/text/secondary` | `border/emphasis` |
| disabled | `color/disabled/text` | без подчёркивания |

**Ссылка не красится синим.** Синий занят «выбрано», а цвет как единственный
различитель ссылки отклонён мудбордом. Носитель — подчёркивание.

### 2.4. Переполнение

Внутри абзаца переносится по словам, подчёркивание идёт по всем строкам.
Отдельно стоящая ссылка с длинным именем файла обрезается многоточием в конце,
полное имя — в доступном имени.

---

## 3. `shared/Checkbox` · `shared/Radio`

Атомы под `FilterBar`, `TableColumnFilter`, `ThemeSwitcher`, `LessonPicker`.

### 3.1. Анатомия

| Часть | Обязательность |
|---|---|
| `box` | обязательна, `icon/size/sm`, радиус `radius/sm` у чекбокса и `radius/full` у радио |
| `mark` | видима при `checked` и `indeterminate` |
| `label` | обязательна, стиль `dense` |
| `hint` | опционально, стиль `caption`, `color/text/muted`, под подписью |
| `ring` | boolean `focus-ring` |

Auto Layout горизонтальный, выравнивание по верху бокса и первой строки подписи —
не по центру: при двухстрочной подписи центрирование уводит бокс в середину.

### 3.2. Оси

```
state = default | hover | pressed | disabled
```
плюс boolean `checked`, `indeterminate` (только у чекбокса), `focus-ring`.

`indeterminate` нужен `TableColumnFilter` при частичном выборе значений.

Размерный вариант один. Второй размер потребовал бы второй ступени иконки,
а перечни фильтров везде набраны одним кеглем.

### 3.3. Состояния

| Состояние | Бокс не отмечен | Бокс отмечен |
|---|---|---|
| default | граница `color/border/default` | заливка `color/accent/now`, метка `color/accent/on-now` |
| hover | граница `color/border/strong` | заливка `color/accent/now-hover` |
| pressed | заливка `color/surface/raised` | заливка `color/accent/now-hover` |
| disabled | `color/disabled/border`, подпись `color/disabled/text` | `color/disabled/surface`, метка `color/disabled/text` |

> Отмеченный чекбокс — единственное место вне «сейчас», где используется
> акцентный цвет. Это не нарушает правило одного выделенного объекта: чекбокс —
> состояние контрола, а не выделенный объект экрана. Если на проверке
> сочетание «отмеченные фильтры + текущая пара» окажется спорным, отмеченный
> чекбокс уходит на `color/selected/border` — правка одного места.

### 3.4. Переполнение

Подпись переносится по словам, бокс остаётся на первой строке. Обрезки нет:
в перечне фильтров обрезанное значение неотличимо от другого значения.

---

## 4. `shared/FormField`

Реестр: label + контрол + подсказка + ошибка, связка `for`/`id`, ошибка через
`aria-describedby`.

**Это обёртка, а не поле.** Сам контрол приходит слотом.

### 4.1. Анатомия

| Часть | Обязательность | Стиль |
|---|---|---|
| `label` | обязательна | `caption`, `color/text/secondary` |
| `required-mark` | опционально (`required`) | символ `*`, `color/text/secondary` |
| `control` | обязательна, instance swap | слот |
| `hint` | опционально (`has-hint`) | `caption`, `color/text/muted` |
| `error` | опционально (`error`) | `caption`, `color/state/danger/text` |

Auto Layout вертикальный, gap `space/1`, ширина `fill`, высота `hug`.
Подпись **над полем** — приём мудборда для ввода со свободными значениями.

**Обязательность помечается символом, не цветом.**

`hint` и `error` не показываются одновременно: при ошибке подсказка заменяется.
Иначе блок прыгает по высоте.

### 4.2. Оси

```
state = default | error
```
плюс boolean `required`, `has-hint`.

Остальные состояния принадлежат контролу внутри, а не обёртке. Это разделение
обязательное: иначе состояния придётся дублировать в двух местах и они разойдутся.

### 4.3. Слоты

`control` принимает: `TextInput`, `SelectField`, `PasswordField`, `SearchField`,
`FileUploadField`, `DateRangePicker`, группу `Checkbox` или `Radio`.

---

## 5. Поле ввода — `shared/TextInput`

Базовый контрол под `FormField`. В реестре отдельной строкой не значится, но
`FormField` без него не собирается.

### 5.1. Анатомия

| Часть | Обязательность |
|---|---|
| контейнер | обязательна |
| `icon-leading` | опционально |
| `value` / `placeholder` | обязательна, `body` |
| `action-trailing` | опционально: очистка, показ пароля, календарь |
| `ring` | boolean |

### 5.2. Размер

Один. Высота `control/height`, padding `space/3`, радиус `radius/md`,
граница `border/hairline`, ширина `fill`.

Второго размера нет: поля живут только в формах и в панелях фильтров, где ряд
задаётся `control/height`. Плотность таблицы на поля не распространяется —
правка строки на месте использует ту же высоту, что и строка `regular`.

### 5.3. Состояния

| Состояние | Заливка | Граница | Текст |
|---|---|---|---|
| default | `color/surface/raised` | `color/border/default` | `color/text/primary` |
| hover | `color/surface/raised` | `color/border/strong` | `color/text/primary` |
| focused | `color/surface/raised` | `color/border/accent` + кольцо | `color/text/primary` |
| filled | как default | как default | `color/text/primary` |
| placeholder | как default | как default | `color/text/muted` |
| disabled | `color/disabled/surface` | `color/disabled/border` | `color/disabled/text` |
| error | `color/surface/raised` | `color/state/danger/border` | `color/text/primary` |
| busy | как default | как default | + индикатор в `action-trailing` |

`pressed` у поля нет: нажатие переводит в `focused`, промежуточного состояния
не существует.

**Данные только на чтение показываются текстом, а не `disabled`-полем** —
правило реестра §6.2. Поэтому `readonly` вариантом не заводится: если поле
нередактируемо, на экране стоит `DefinitionList`, а не `TextInput`.

### 5.4. Переполнение

Значение длиннее поля скроллится внутри поля по горизонтали, поле не растёт.
Ширина поля задаётся ожидаемой длиной значения — приём мудборда: поле под
номер группы уже поля под ФИО.

---

## 6. `shared/SelectField`

Реестр: выпадающий список поверх `FormField`, нативная семантика, `disabled` /
`loading`, пояснение к недоступному значению.

### 6.1. Анатомия

Контейнер `TextInput` + `icon-trailing` (шеврон, `icon/size/sm`) + список.

Список — экземпляр `Popover` (проход 02), внутри пункты:

| Часть пункта | Обязательность |
|---|---|
| `label` | обязательна, `dense` |
| `hint` | опционально — **причина недоступности**, `caption`, `color/text/muted` |
| `check` | видима при `selected` |

### 6.2. Состояния поля

Те же, что у `TextInput`, плюс `open`: граница `color/border/accent`, шеврон
повёрнут, `duration/fast`.

### 6.3. Состояния пункта

| Состояние | Заливка | Текст |
|---|---|---|
| default | прозрачная | `color/text/primary` |
| hover | `color/surface/raised` | `color/text/primary` |
| selected | `color/selected/fill` | `color/selected/text` |
| disabled | прозрачная | `color/disabled/text` + причина текстом |

**Недоступное значение не скрывается** — оно видно с объяснением. Это сквозное
правило реестра.

### 6.4. Переполнение

Значение в закрытом поле обрезается многоточием, полное — в `Tooltip`.
Пункт списка переносится на две строки, дальше обрезается.

---

## 7. `shared/PasswordField`

Реестр: поле пароля с показом и скрытием. **Вставка и показ разрешены.**

Экземпляр `TextInput` с `action-trailing` = кнопка-иконка `AppButton`
(`type=ghost`, `has-label=false`, `size=compact`). Состояния наследуются.

Собственная ось: boolean `revealed`. Иконка и доступное имя кнопки меняются
вместе с ним.

---

## 8. `auth/PasswordPolicyHint`

Реестр: требования к паролю со статусом выполнения, доступны **до** ввода.

### 8.1. Анатомия

Вертикальный список правил, gap `space/1`. Правило:

| Часть | Стиль |
|---|---|
| `status-icon` | `icon/size/sm` |
| `label` | `caption` |

### 8.2. Состояния правила

| Состояние | Иконка | Текст |
|---|---|---|
| `pending` (ещё не введено) | нейтральная точка, `color/text/muted` | `color/text/secondary` |
| `met` | галочка, `color/state/success/text` | `color/text/secondary` |
| `unmet` (введено и не подходит) | символ, `color/state/danger/text` | `color/text/secondary` |

Три состояния, а не два: до ввода правило не «нарушено», оно просто не проверено.
Красный список при пустом поле — обвинение до действия.

Цвет не единственный носитель: иконки разной формы.

---

## 9. `shared/SearchField`

Реестр: полнотекстовый поиск над списком, задержка ввода, очистка, объявление
количества найденного.

Экземпляр `TextInput`: `icon-leading` — лупа, `action-trailing` — кнопка очистки
(`AppButton` `ghost`, `has-label=false`), видима при непустом значении.

Собственная часть: `result-count`, стиль `caption`, `color/text/muted`, под полем.
Показывается только при непустом запросе.

Размер один, ширина `fill` в своей ячейке раскладки.

---

## 10. `shared/FileUploadField`

Реестр: выбор, прогресс, превью, удаление, отмена. Ограничения показываются
**до** выбора. Ошибки различимы текстом: размер · формат · сеть · отказ сервера ·
отклонено санитизацией. Загрузка обязана работать с клавиатуры.

### 10.1. Анатомия

| Часть | Обязательность |
|---|---|
| `dropzone` | обязательна |
| `limits` | обязательна — размер, форматы, количество, **до выбора** |
| `browse-button` | обязательна, `AppButton` `secondary` `compact` |
| `file-list` | видима при непустом наборе |
| `ring` | boolean |

Элемент списка: иконка типа · имя · вес · прогресс · кнопка отмены или удаления.

### 10.2. Носитель смысла

**Граница `dropzone` — пунктирная всегда.** Пунктир означает «свободно,
принимает» (§9 брендбука). Штриховкой зона приёма не заливается: штриховка
означает «занято».

Штрих 4px, пропуск 4px, толщина `border/hairline`, цвет `color/border/default`.

### 10.3. Состояния

| Состояние | Граница | Заливка | Что видно |
|---|---|---|---|
| `idle` | пунктир `color/border/default` | прозрачная | ограничения + кнопка |
| `hover` | пунктир `color/border/strong` | `color/surface/raised` | то же |
| `dragover` | пунктир `color/border/accent` | `color/accent/soft` | «отпустите файл» |
| `uploading` | сплошная `color/border/default` | `color/surface/raised` | прогресс + отмена |
| `processing` | сплошная `color/border/default` | `color/surface/raised` | «файл обрабатывается» |
| `done` | сплошная `color/border/default` | прозрачная | список файлов |
| `error` | сплошная `color/state/danger/border` | `color/state/danger/bg` | **причина текстом** |
| `disabled` | пунктир `color/disabled/border` | `color/disabled/surface` | причина недоступности |

`processing` и `done` различаются обязательно: «нет данных» ≠ «готово», иначе
администратор грузит тот же файл повторно.

Пять причин ошибки — пять разных текстов, один визуал. Различие несёт текст,
а не цвет.

### 10.4. Переполнение

Имя файла обрезается **в середине**, а не в конце: расширение обязано остаться
видимым. Вес и кнопка действия не сжимаются.

### 10.5. Слоты и вложенность

Внутрь ложатся `AppButton` (обзор, отмена, удаление) и шкала заполнения
в применении «процесс» (проход 04). Ничего больше.

---

## 11. `shared/DateRangePicker`

Реестр: выбор одной даты или произвольного диапазона в границах семестра.
**Не синоним `DatePager`.** На 134 границами не ограничен.

### 11.1. Анатомия

| Часть | Обязательность |
|---|---|
| `field-from` | обязательна, `TextInput` с `action-trailing` = иконка календаря |
| `field-to` | видима при `mode=range` |
| `calendar` | `MonthCalendar` в `Popover` (проходы 05 и 02) |

Auto Layout горизонтальный, gap `space/3`. Оба поля фиксированной ширины —
это единственное место в формах, где ширина фиксирована: дата имеет известную
длину `02.12.2025`.

### 11.2. Оси

```
mode  = single | range
state = default | focused | disabled | error
```

`hover` — на полях внутри, у обёртки его нет. `pressed` нет: обёртка не нажимается.

### 11.3. Переполнение

Не бывает: формат даты фиксированной ширины. Пустое значение — плейсхолдер
формата `дд.мм.гггг`, а не пустое поле.

---

## 12. `settings/ThemeSwitcher`

Реестр: светлая · тёмная · **как в системе** (дефолт). Группа радиокнопок
в `fieldset`, не тумблер — значений три.

Экземпляр группы `Radio`, вертикальный, gap `space/2`, с `legend`.
Собственных состояний нет: всё принадлежит `Radio`.

Отдельным компонентом остаётся потому, что реестр называет его отдельно и потому,
что состав из трёх значений — часть контракта, а не данные.

---

## 13. Сводка по вариантам

| Компонент | Осей | Вариантов | Boolean-свойства |
|---|---|---|---|
| `AppButton` | 3 | 28 | `focus-ring`, `busy`, `has-label`, `has-icon-leading`, `has-icon-trailing`, `has-counter` |
| `AppLink` | 1 | 4 | `focus-ring`, `external` |
| `Checkbox` | 1 | 4 | `checked`, `indeterminate`, `focus-ring`, `has-hint` |
| `Radio` | 1 | 4 | `checked`, `focus-ring`, `has-hint` |
| `FormField` | 1 | 2 | `required`, `has-hint` |
| `TextInput` | 1 | 8 | `focus-ring`, `has-icon-leading`, `has-action-trailing` |
| `SelectField` | 1 | 9 | наследует `TextInput` |
| `PasswordField` | — | — | `revealed` + наследование |
| `PasswordPolicyHint` | 1 | 3 (на правило) | — |
| `SearchField` | — | — | `has-value` + наследование |
| `FileUploadField` | 1 | 8 | `focus-ring` |
| `DateRangePicker` | 2 | 8 | — |
| `ThemeSwitcher` | — | — | — |

---

## 14. Нужен токен

Накопительный список; здесь — то, чего не хватило на этом проходе.

| Токен | Значение | Почему |
|---|---|---|
| `icon/size/sm` | 16px | заведён на проходе 00, используется здесь во всех контролах |
| `icon/size/md` | 20px | заведён на проходе 00 |
| `icon/size/lg` | 24px | заведён на проходе 00 |

Новых на этом проходе не понадобилось.

---

## 15. Что осталось открытым

1. **Заливка отмеченного чекбокса.** Сейчас `color/accent/now`. Формально это
   не нарушает правило одного выделенного объекта, но на экране с активными
   фильтрами и текущей парой одновременно надо посмотреть глазами. Запасной
   вариант — `color/selected/border`, правка одного места.
2. **Инверсная заливка основной кнопки** берёт значение из текстовой группы
   токенов. Работает в обеих темах, отдельного токена не потребовала, но это
   единственное такое место в системе — если появится второе, стоит завести
   `color/fill/inverse`.
3. **`TextInput` не значится в реестре отдельной строкой.** Заведён здесь как
   атом под `FormField`, `SelectField`, `PasswordField`, `SearchField`,
   `DateRangePicker`. Если реестр решит назвать его явно — имя и владение
   берутся оттуда.
