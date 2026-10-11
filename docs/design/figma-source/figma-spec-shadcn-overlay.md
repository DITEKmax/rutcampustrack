# Накладка на figma-spec — переход на shadcn-vue

> Читается **вместе с** `figma-spec.md`, не вместо него. Где накладка и спека расходятся, выигрывает накладка: спека писалась до решения брать библиотеку.
>
> Смысл один: часть анатомии из спеки больше не нужно проектировать — она уже есть в shadcn-vue и вылизана. Наша работа сужается до перекраски и до доменных компонентов, которых нет ни в одной библиотеке.

---

## 1. Что происходит со спекой

Из 168 компонентов реестра примерно тридцать — базовые контролы, которые есть в shadcn-vue готовыми. Их разделы в `figma-spec.md` (анатомия, размерные варианты, внутренние отступы, матрицы состояний) **становятся справочными**: сверяться с ними можно, проектировать по ним заново — нет.

Остальное остаётся в силе полностью. Доменные компоненты, блоки, сборка экранов, поведение трёх ширин — библиотека этого не знает.

**Что не меняется вообще:** реестр, брендбук, токены, `BRAND_DIRECTION`. Список компонентов по-прежнему закрыт реестром — библиотека не даёт права заводить новые.

---

## 2. Берём из shadcn-vue

Ставить через MCP, оборачивать тонко, перекрашивать нашими токенами.

| Наш компонент | Компонент shadcn-vue | Что учесть |
|---|---|---|
| `AppButton` | Button, Button Group | наши варианты и `busy` — надстройка |
| `TextInput` | Input | восемь состояний из реестра поверх |
| `FormField` | Field, Label, Form | подписи, подсказки, ошибки — их слоты |
| `SelectField` | Select, Native Select | |
| `PasswordField` | Input + Input Group | показ/скрытие — их слот действия |
| `SearchField` | Input Group | |
| `OtpForm` | Input OTP, Pin Input | |
| `Popover` | Popover | вложенные поповеры запрещены (см. §4) |
| `Tooltip` | Tooltip | |
| `ConfirmDialog` | Alert Dialog | **единственная** модалка продукта |
| `Tabs` | Tabs | |
| `Pagination` | Pagination | |
| `DataTable` | Data Table | только серверный режим, см. §4 |
| `TableColumnFilter` | Popover + Checkbox / Combobox | |
| `DateRangePicker` | Date Picker, Range Calendar | |
| `MonthCalendar` | Calendar | русская локаль, неделя с понедельника |
| `FileUploadField`, `AttachmentList` | Attachment | зона загрузки — пунктир, не штриховка |
| `ThemeSwitcher` | Toggle Group | |
| `RowActionsMenu`, `ProfileMenu` | Dropdown Menu | |
| `MainNav` | Navigation Menu | |
| `ProgressScale` | Progress | три наших применения — надстройка |
| `LoadingState` | Skeleton, Spinner | скелетон **не мерцает**, см. §4 |
| `EmptyState`, `ErrorState`, `ForbiddenState`, `OfflineState` | Empty, Alert | четыре вида пустоты не сливать |
| `AttendanceStatusBadge`, `TicketStatusBadge`, `MemberRoleBadge`, `EntityStatusCell` | Badge | форма несёт «это статус» |
| `DefinitionList` | Item | оба варианта, inline и stack |
| внутренние прокрутки | Scroll Area | ростер, рейтинг |
| разделители | Separator | |

Полезное сверх реестра: **Sidebar** — если боковое меню всё-таки делаем; **Kbd** — если появятся горячие клавиши. Оба вне текущего состава, заводить только решением владельца.

---

## 3. Остаётся нашим

Ни одна библиотека этого не даст. Разделы `figma-spec.md` по ним — единственный источник, читать целиком.

**Посещаемость:** `AttendanceJournalGrid`, `LessonAttendanceRoster`, `AttendanceStatusPicker`, `AttendanceWeekTable`, `AttendanceDayTable`, `AttendanceSubjectTable`, `AttendanceButton`, `LateCheckInButton`, `LessonAttendanceCounter`, `ManualAttendancePanel`, `LessonBlockToggle`.

**Время и расписание:** `TimetableGrid` и обе обёртки, `ScheduleSlotCell`, `ScheduleSlotCard`, `ScheduleSlotForm`, `WeekParityPicker`, `LessonManagementCell`, `LessonActionsPanel`, `LessonCard`, `ScheduleList`, `DatePager`, `StepPager`, `LessonPager`, `WeekContextPager`.

**Статистика:** `StatsByStudentTable`, `StatsByGroupTable`, `StatsBySubjectTable`, `StatCard`, `MetricValue`, `MetricDelta`, `AttendanceTrendChart`, `ChartDataTable`, `AttendanceRankingList`, `StatsDetailPanel`.

**Заявки:** `TicketCard`, `TicketForm`, `TicketRejectForm`, `ExcuseTicketPanel`, `ExcuseReasonPopover`, `LessonPicker`.

**Учебные сущности:** `GroupMembersTable`, `UsersTable`, `GroupsTable`, `SemestersTable`, `SubjectLessonTypeTable`, `FloorPlansTable` и все формы создания — это `DataTable` в основе, но состав колонок, исключения Р-16 и раскрытия строк наши.

**Карта:** `FloorPlanViewer`, `BuildingFloorPicker`, `PlanLegend`.

**Каркас и роли:** `AppShell`, `AuthLayout`, `AppHeader`, `RoleSwitcher`, `PermissionList`, `InitialPasswordCell`, `TelegramIdCell`, `DisputeBlockCell`, `DangerousActionChallenge`, `DestructiveActionPair`.

Полоса текущей пары — надстройка над Progress, но правила §4.1 `BRAND_DIRECTION` наши целиком: только на главной, шаг не меньше пяти минут, последние пять минут словом «заканчивается», посекундного отсчёта нет.

---

## 4. Ловушки библиотеки

Шесть мест, где готовый компонент прямо противоречит направлению. Это самое важное в файле.

**Toast и Sonner — не использовать вовсе.** Всплывающее уведомление, исчезающее само, запрещено: ошибка — состояние объекта, а не сообщение об инциденте, и она единственное, что не исчезает само. `LiveNotificationRegion` строится как область в разметке, а не как тост.

**Sheet, Drawer и Dialog — только `ConfirmDialog`.** Наложений в продукте нет, раскрытие происходит сдвигом в разметке. Боковую панель и шторку не заводить, даже когда они удобнее.

**Hover Card — не использовать.** Это наложение, и оно требует наведения там, где наведение запрещено.

**Поповер в поповере запрещён.** Второго уровня наложения не существует. `TableColumnFilter` внутри `RowActionsMenu` собирается по привычке — не собирать.

**Data Table: только серверный режим.** Отбор, порядок и пагинация серверные везде, включая наборы, приходящие целиком (Р-17). Клиентские сортировка и фильтрация TanStack не включаются. У `GroupMembersTable` фильтров и сортировки нет вовсе, у `SemestersTable` фильтр только по статусу и сортировки нет, у колонки «Почему черновик» фильтра нет — три исключения Р-16.

**Skeleton не мерцает.** Мерцание — анимация, при выключенном движении её пришлось бы убрать, и состояние осталось бы без носителя. Носитель — форма: скелетон таблицы выглядит таблицей, скелетон сетки — сеткой.

Отдельно: **hover у компонентов библиотеки включён по умолчанию.** В плотных сетках его нет — кроме крест-подсветки в журнале. При установке каждого табличного компонента hover снимается явно.

---

## 5. Как ложатся токены

Главное правило: **не переписывать переменные shadcn-vue значениями, а связать их с нашими семантическими токенами.** Тогда правка остаётся в одном месте, а компоненты библиотеки обновляются без конфликта.

Порядок:

1. Наши три слоя объявляются первыми — примитивы, семантические с двумя темами, компонентные.
2. Переменные темы shadcn-vue объявляются **после** и получают значения ссылкой на наши семантические:

```css
--background:        var(--color-surface-base);
--foreground:        var(--color-text-primary);
--card:              var(--color-surface-raised);
--popover:           var(--color-surface-float);
--primary:           var(--color-accent-now);
--primary-foreground:var(--color-accent-on-now);
--muted-foreground:  var(--color-text-muted);
--border:            var(--color-border-default);
--input:             var(--color-border-default);
--ring:              var(--color-border-focus);
--destructive:       var(--color-state-danger-fill);
--radius:            var(--radius-md);
```

3. Тёмная и светлая темы переключаются **одним** механизмом — нашим атрибутом на корне. Второго переключателя не заводить: у shadcn-vue свой класс тёмной темы, его надо привязать к нашему, а не держать параллельно.

Два места, где нужна осторожность:

- `--primary` у shadcn-vue красит все основные кнопки. У нас акцент — это «сейчас», и он один на экран. Значит основная кнопка **не** берёт `accent-now`: ей нужен нейтральный сильный токен, иначе правило одного выделенного объекта ломается на каждом экране с кнопкой. Проверить это в первой же партии.
- shadcn-vue на Tailwind v4 работает в OKLCH. Наши значения в hex. Конвертировать один раз при объявлении примитивов, а не по месту.

---

## 6. Что делать с `figma-spec.md`

Не удалять и не переписывать. Дописать в его начало пометку: «часть 01 и разделы по базовым контролам перекрыты `figma-spec-shadcn-overlay.md`». Обоснования решений там остаются ценными — они объясняют, почему компонент устроен так, а не иначе, и это пригодится при перекраске.

Библиотека в Figma при этом сжимается: базовые контролы живут в коде и в Figma не дублируются. В Figma остаются доменные компоненты, блоки и экраны — то, что удобнее собирать глазами.

---

## 7. Проверка первой партии

- [ ] Ни один компонент библиотеки не оставлен с её дефолтными переменными
- [ ] Тема переключается одним механизмом, не двумя
- [ ] Основная кнопка не использует `accent-now`
- [ ] Hover снят у табличных компонентов
- [ ] Toast, Sheet, Drawer, Hover Card не установлены
- [ ] Data Table настроен на серверный режим
- [ ] Ни одного компонента вне реестра
