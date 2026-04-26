# Phase 61 — Headman Homework Management: CONTEXT

**Phase goal:** Доделать фичу «Домашние задания»: привязать ДЗ к конкретной паре (дата + номер пары), дать старосте UI для CRUD на странице `/headman/homework`, переписать студенческую `/student/homework` с тремя режимами просмотра (день / неделя / месяц), отправлять push при create и update.

**Milestone:** v9.0 Frontend Unification
**Domain:** Full-stack (academic-service backend + Angular web-panel frontend + events)
**Created:** 2026-04-15

---

## Canonical refs

Документы и артефакты, которые downstream-агенты (researcher, planner) обязаны читать:

- `.planning/ROADMAP.md` § Phase 61 entry (будет обновлён planner-ом)
- `.planning/REQUIREMENTS.md` — общие требования v9.0 (HEAD-WEB-*, STU-WEB-*)
- `docs/job-stories.md` — job stories для старосты и студента
- `docs/database-schema.md` — текущая схема `homeworks`/`homework_completions`
- `docs/deferred-ideas.md` — идеи, вынесенные за скоуп (помощник старосты, админ-модерация)
- `CLAUDE.md` — правила кодирования (contract-first, lowercase enums, Flyway, HATEOAS)
- Бэкенд (academic-service):
  - `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/HomeworkApi.java`
  - `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/homework/` (DTO: Create/Update/Response)
  - `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Homework.java`
  - `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/HomeworkCompletion.java`
  - `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java`
  - `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkController.java`
  - `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkPublishedEvent.java`
  - `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkUpdatedEvent.java`
- Frontend (web-panel):
  - `frontends/web-panel/src/app/features/student/homework/` — текущая студенческая страница (будет переписана)
  - `frontends/web-panel/src/app/features/student/shared/student-api.service.ts` — методы `getHomeworks/markHomeworkComplete/unmarkHomeworkComplete`
  - `frontends/web-panel/src/app/features/student/shared/student-schedule.types.ts` — `HomeworkItem` тип
  - `frontends/web-panel/src/app/features/headman/subjects/subject-dialog.component.ts` — паттерн модального диалога CRUD (реюз стиля/валидации)
  - `frontends/web-panel/src/app/features/headman/schedule/` — паттерн матрицы/навигации по неделям (референс для UX)
  - `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts` — секция "Старостат" (добавить пункт «Домашние задания»)
- Расписание (для резолва пары по дате+номеру):
  - `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/` — ScheduleItem + schedule_one_off_lessons
  - Attendance-service read-path (merge шаблона + one-off) — Phase 60, частично отложен на 60-09
- События:
  - `event-schemas/` — JSON Schema для homework events (расширить payload полями `lessonDate`, `lessonNumber`)
  - `services/notification-bot/bot/notifications/homework.py` — handler для push (расширить на `homework.updated`)

---

## Scope boundary

### In scope (Phase 61)

1. **Backend (academic-service):** расширить модель `Homework` полями `lesson_date DATE NOT NULL` + `lesson_number INT NOT NULL` (Flyway миграция); валидация на уровне сервиса
2. **Backend:** `publishedBy` guard для update/delete (только автор)
3. **Backend:** проверка существования пары в `(group_id, date, lesson_number)` — запрос в schedule-service (gRPC) или внутренняя логика; subject пары должен совпадать с `subjectId` ДЗ
4. **Backend:** ивенты `homework.published`/`homework.updated` расширить payload на `lessonDate/lessonNumber`; бот шлёт push на обе ивента
5. **Frontend (headman):** новая страница `/headman/homework` — недельный список пар с привязанными ДЗ, модал добавления ДЗ под выбранной парой, edit/delete своих
6. **Frontend (student):** переписать `/student/homework` — три режима просмотра: **день** (только выбранный день, дефолт — завтра) / **неделя** (список с датами, вертикальная прокрутка) / **месяц** (матрица). Навигация между периодами. Фильтр «выполнено / не выполнено» per-period.
7. **Frontend (student):** per-student отметка выполнения (уже есть) сохранить как есть
8. **Sidebar:** добавить пункт «Домашние задания» в секцию старостата
9. **Tests:** Java unit/integration для HomeworkService (новые валидации, guard), Angular unit-тесты для новых компонентов

### Out of scope — Deferred Ideas

- **Помощник старосты (`headman_assistants`) публикует ДЗ** — см. `docs/deferred-ideas.md`
- **ADMIN редактирует чужие ДЗ** — см. `docs/deferred-ideas.md`
- **Прогресс выполнения для старосты** («5/12 сделали») — староста в Phase 61 не видит completion stats
- **Вложения к ДЗ** (файлы, картинки) — только text/link
- **Дедлайн отдельно от даты пары** — привязка только к паре, сама пара служит дедлайном
- **Drag-and-drop переноса ДЗ между парами** — не нужен

---

## Decisions (locked)

### D-01 — Привязка ДЗ к конкретному календарному дню + номеру пары
Добавить в `homeworks` две новые колонки:
- `lesson_date DATE NOT NULL`
- `lesson_number INT NOT NULL` (1..N слот дня)

**НЕ ставим UNIQUE** на `(group_id, lesson_date, lesson_number)` — на одну пару можно вешать N заданий.
**НЕ добавляем FK** на `schedule_items` или `schedule_one_off_lessons` — пара резолвится по natural key `(group_id, lesson_date, lesson_number)`, как в attendance-service (Phase 60, D-15).

**Why:** staroста может на одну пару задать несколько отдельных задач; пара может быть как шаблонной (`ScheduleItem`), так и разовой (`schedule_one_off_lessons`), и при отмене одной из них ДЗ естественно исчезает из недельного вида (cascade через `lesson.one_off.cancelled` или отсутствие шаблона на эту дату).
**How to apply:** Entity `Homework` получает `private LocalDate lessonDate; private Integer lessonNumber;`. DTO `CreateHomeworkRequest` получает `@NotNull LocalDate lessonDate, @NotNull @Min(1) Integer lessonNumber`. `HomeworkResponse` эти поля тоже отдаёт.

### D-02 — Миграция: TRUNCATE существующих ДЗ
Таблицы `homeworks` и `homework_completions` сейчас пустые (реальных записей нет, Phase 52 был недавно). Flyway миграция = `TRUNCATE` + `ALTER TABLE homeworks ADD COLUMN lesson_date DATE NOT NULL, ADD COLUMN lesson_number INT NOT NULL`.

**Why:** простой путь без бэкфила; безопасно, так как данных нет. Подтверждено пользователем.
**How to apply:** Один Flyway-скрипт `V_N__homework_lesson_binding.sql` внутри academic-service. Никакого data-migration кода.

### D-03 — Валидация «дата ≥ today»
Бэкенд отклоняет создание ДЗ с `lesson_date < today` (400 Bad Request). При update тоже проверяется, если `lesson_date` меняется.

**Why:** бессмысленно ставить ДЗ на прошедшую пару; соответствует ожиданию «задание на будущее».
**How to apply:** `HomeworkService.createHomework` — `if (request.lessonDate().isBefore(LocalDate.now(clock))) throw new BadRequestException(...)`. Использовать `Clock` bean (тестируемость).

### D-04 — Валидация существования пары + совпадения предмета
Бэкенд проверяет, что пара `(group_id, lesson_date, lesson_number)` **существует** в расписании группы на указанную дату:
- либо шаблонный `ScheduleItem` с подходящим `day_of_week` + `week_type` + `lesson_number` для семестра/группы
- либо `schedule_one_off_lessons` на эту дату

Если пары нет → 400 Bad Request («На эту пару нельзя назначить ДЗ — пары нет в расписании»).

Дополнительно: `subjectId` в запросе должен совпадать с `subject_id` найденной пары. Иначе → 400 («ДЗ можно задать только по предмету этой пары»).

**Why:** исключает ДЗ «в пустоту» и ДЗ по чужому предмету (подтверждено в discuss: «в пустоту нельзя», «я задаю на конкретную пару»).
**How to apply:** Нужен gRPC-метод в schedule-service для резолва `(groupId, date, lessonNumber) → {lessonExists, subjectId}`. Если метода нет — добавить в этой фазе. `HomeworkService` вызывает его перед сохранением.

### D-05 — Guard: только автор редактирует/удаляет
`HomeworkService.updateHomework` и `deleteHomework` проверяют `homework.publishedBy == currentUserId`. Если не совпадает → 403 Forbidden. ADMIN **НЕ** получает исключения (в Phase 61 админ не участвует в ДЗ).

**Why:** TimeTree-подобное поведение: автор контента — единственный владелец. Подтверждено пользователем.
**How to apply:** В сервисе использовать `RequestContext.getUserId()` (паттерн из Phase 60). Admin override вынесен в deferred-ideas.

### D-06 — Роль создателя: только HEADMAN
Phase 61: только сам староста (роль STUDENT + is_headman=true) может создавать ДЗ. Помощник старосты — **не может** (см. deferred-ideas). Admin — **не может**.

**Why:** MVP; права помощнику — отдельная фаза.
**How to apply:** Spring Security role-check на `@PreAuthorize("hasRole('HEADMAN')")` + guard в сервисе на `is_headman=true`. (В текущей реализации уже `HEADMAN/ADMIN` — убрать ADMIN из createHomework.)

### D-07 — События: push при create И update
- `homework.published` — уже существует, пушит студентам группы. Расширить payload на `lessonDate`, `lessonNumber` (и subject name через gRPC-fallback в bot-е, как в Phase 60).
- `homework.updated` — уже существует, бот **сейчас не обрабатывает**. Добавить handler в `services/notification-bot/bot/notifications/homework.py` + mapping в `event_dispatcher.py`. Текст push: «ДЗ изменено: {subject} — {title}».

**Why:** «уведомления и при создании, и при редактировании» (user).
**How to apply:** event-schemas JSON Schema обновить, HomeworkService publisher расширить, notification-bot handler расширить, Java notification-service event consumer (если слушает) — расширить.

### D-08 — UI старосты: страница `/headman/homework`, недельный список пар
Layout:
- Вверху — переключатель недель (стрелки ← →, текущая неделя по умолчанию, «сегодня» кнопка — как в `/headman/schedule`)
- Список дней (Пн-Сб) с датой. Под каждым днём — список пар этого дня (из schedule + one-off), сортировка по `lesson_number`
- Под каждой парой: карточки уже опубликованных ДЗ (title + description + link + icons edit/delete)
- После последнего ДЗ пары (или если ДЗ нет) — кнопка **«+ Добавить задание»**

Клик «+ Добавить задание» → **раскрывается inline-панель** (НЕ модальный диалог) сразу под этой парой или под последним ДЗ этой пары, с полями:
- `title` (required, max 255)
- `description` (optional, textarea, max 4000)
- `link` (optional, max 2048)
- кнопки **Сохранить** / **Отмена**

**Клик edit на своём ДЗ** → аналогичная inline-панель с заполненными полями.

**Why:** «нажимаю кнопку раскрывается поле снизу от этой пары или от предыдущего задания к этой паре» (user). Быстрее, чем модал, когда нужно добавить несколько подряд. Подтверждено пользователем.
**How to apply:** Не используем `MatDialog`. Inline-форма = отдельный standalone-компонент `HomeworkInlineFormComponent` с `@Input() lessonContext`, `@Output() saved/cancelled`. Родитель (`HeadmanHomeworkComponent`) управляет тем, какая пара/ДЗ сейчас в режиме редактирования через сигнал `editingState: signal<{lessonKey, homeworkId|null} | null>`.

### D-09 — UI студента: три режима просмотра (переписываем `/student/homework`)
Существующий плоский список заменяется на страницу с переключателем режимов:

1. **День** — одна дата (дефолт: завтра). Навигация ← →. Показывает пары этого дня с привязанными ДЗ. Пустое состояние: «На этот день заданий нет».
2. **Неделя** — вертикальный список по дням текущей недели, каждый день — заголовок + пары + ДЗ. Как у старосты, только read-only + чекбокс «выполнено» per-ДЗ.
3. **Месяц** — матрица: дни месяца, в ячейке = счётчик ДЗ (и индикатор «невыполненные»). Клик по ячейке → переход в режим «День» на эту дату.

Переключатель режимов — segmented control вверху (День / Неделя / Месяц).
Фильтр «только невыполненные» — toggle, применяется во всех трёх режимах.

**Why:** пользователь явно попросил тройку, «на месяц матрица, на неделю список, на день только завтра, листать можно».
**How to apply:** Переписать `StudentHomeworkComponent` — сделать его контейнером с дочерними `StudentHomeworkDayViewComponent`, `StudentHomeworkWeekViewComponent`, `StudentHomeworkMonthViewComponent`. Общий API-слой (уже есть `getHomeworks(groupId, semesterId)`) — просто фильтровать по диапазону дат клиентски (или расширить бэк на `?from&to` — решит planner после research).

### D-10 — Студент: completion остаётся per-student
Логика markComplete/unmarkComplete — без изменений. Поле `completed` на `HomeworkResponse` — уже per-student. Староста `completed` не видит (на странице `/headman/homework` этого поля вообще нет — D-08).

**Why:** E в discuss: «староста не видит выполнение».
**How to apply:** В DTO для headman-ответа можно либо отдать `completed=null`, либо просто игнорировать поле в UI. Проще — переиспользовать существующий DTO, staroста его игнорирует.

### D-11 — Subject в диалоге создания — автоподстановка из пары
Субъект определяется парой (D-04 проверяет совпадение). В UI старосты subject **не выбирается** явно — он уже известен из контекста пары, на которой нажата кнопка «+». Отображается в форме как readonly-шапка: «Предмет: {subjectName}».

**Why:** B в discuss — «как корректнее». Гибрид, стартуем с привязки из контекста пары. Расширяемо: позже можно добавить «ручной выбор» через subject → date → lesson, если понадобится.
**How to apply:** `HomeworkInlineFormComponent` принимает `@Input() subjectId, @Input() subjectName, @Input() lessonDate, @Input() lessonNumber` из контекста пары. Пользователь эти поля не редактирует — только title/description/link.

### D-12 — Sidebar: пункт «Домашние задания» в секции старостата
Добавить в `sidebar.component.ts` после «Предметы» (строка ~199):

```ts
{
  label: 'Домашние задания',
  icon: 'ph-notebook',
  route: '/headman/homework',
  roles: ['STUDENT'],
  isHeadman: true,
}
```

**Why:** единообразие с уже существующим пунктом у студента.
**How to apply:** Одна правка в sidebar + регистрация route в headman feature module.

### D-13 — Структура папок (Angular)
- `frontends/web-panel/src/app/features/headman/homework/` — новая фича старосты
  - `headman-homework.component.ts/html/css` — страница со списком недель
  - `homework-inline-form/homework-inline-form.component.ts/html/css` — форма добавления/редактирования
  - `homework-card/homework-card.component.ts/html/css` — карточка отображения ДЗ (reused для student?)
  - `headman-homework-api.service.ts` — create/update/delete методы (или расширить существующий `student-api.service`)
- `frontends/web-panel/src/app/features/student/homework/` — переписываем существующую
  - `student-homework.component.ts` — контейнер с segmented control
  - `day-view/`, `week-view/`, `month-view/` — под-компоненты
  - `homework-card/` — возможно shared с headman через `shared/homework-card/`

**Why:** follow Angular standalone + feature-folder pattern (как в других headman-фичах).
**How to apply:** planner решит точную структуру, но зафиксировано: headman и student — отдельные features, карточка ДЗ может быть в `shared/` если есть переиспользование.

### D-14 — Язык, стиль, анимации
- Весь UI-текст на русском (как в остальном приложении)
- Phosphor icons (`ph-notebook`, `ph-pencil`, `ph-trash`, `ph-plus`)
- Анимации: routeFade + expand/collapse для inline-формы (как в других компонентах student/*)
- Валидация inline: показывать ошибки под полями (как в `subject-dialog`)

**Why:** consistency с v9.0 дизайн-системой (`docs/design-decisions.md`).

### D-15 — Flyway migration plan
Один скрипт `V_N__homework_lesson_binding.sql` в `services/academic-service/academic-app/src/main/resources/db/migration/`:
1. `TRUNCATE homeworks, homework_completions RESTART IDENTITY CASCADE;`
2. `ALTER TABLE homeworks ADD COLUMN lesson_date DATE NOT NULL;`
3. `ALTER TABLE homeworks ADD COLUMN lesson_number INT NOT NULL;`
4. `CREATE INDEX idx_homeworks_group_date ON homeworks(group_id, lesson_date);`

Индекс — для быстрых запросов недельного списка по группе.

---

## Known gray areas (Claude's discretion — planner decides)

- **gRPC-метод в schedule-service** для `resolveLesson(groupId, date, lessonNumber)` — точная сигнатура, proto-файл, где декларировать (existing vs new `.proto`). Planner/researcher разбирается с существующим schedule.proto.
- **API эндпоинт `/academic/homeworks?from=&to=`** для узких диапазонов (день/неделя/месяц) — клиентская фильтрация работает, но при росте числа ДЗ потребуется server-side. Planner решит: добавить сразу или отложить.
- **Точное размещение «кнопки + Добавить задание»** если на паре 0 ДЗ vs N ДЗ — UI nuance, определит planner/UI-spec.
- **Обработка случая «пара удалена после создания ДЗ»** (one-off cancelled) — ДЗ остаётся в БД, но исчезает из недельного списка (т.к. пара больше не резолвится). Чистим ли мы такие «сиротские» ДЗ? Рекомендация: оставить, студент увидит в списке «всех» ДЗ (если planner решит добавить такую вьюху) — но в day/week/month не будет показан. Отдельное решение — на уровне planner.

---

## Open questions for research (gsd-phase-researcher)

1. Существует ли в schedule-service gRPC-метод для резолва пары по `(groupId, date, lessonNumber)`? Если нет — spec для нового метода.
2. Как attendance-service сейчас резолвит пару на дату (Phase 60 D-15 natural key) — использовать тот же подход в academic-service?
3. Паттерн навигации по неделям в `/headman/schedule` (Phase 60) — реюзнуть компонент week-navigator?
4. Есть ли готовый компонент segmented control (D-15 в Phase 56)? Можно ли реюзнуть для переключателя День/Неделя/Месяц?
5. Как notification-bot/web обрабатывают `HomeworkUpdatedEvent` сейчас (и обрабатывают ли)?
6. Как выглядит существующий `HomeworkCardComponent` у студента — сколько там логики, насколько легко вынести в shared?

---

## Success criteria (for verifier)

1. `homeworks` таблица имеет NOT NULL колонки `lesson_date`, `lesson_number`; индекс `(group_id, lesson_date)` существует
2. POST `/api/academic/homeworks` с валидной датой и номером пары в расписании группы + совпадающим subjectId → 201; без пары / несовпадающий subject → 400
3. POST с `lesson_date < today` → 400
4. PUT/DELETE от НЕ автора → 403
5. На одну пару `(group_id, date, lesson_number)` успешно создаются ≥2 ДЗ подряд
6. `homework.published` и `homework.updated` events содержат `lessonDate`, `lessonNumber`; notification-bot шлёт push в оба случая
7. Страница `/headman/homework` — недельный список пар, под каждой парой inline-форма «Добавить задание», edit/delete работают для своих ДЗ
8. Страница `/student/homework` — переключатель День/Неделя/Месяц, навигация между периодами, фильтр «только невыполненные», markComplete работает
9. Sidebar содержит «Домашние задания» в секции старостата (только при `is_headman=true`)
10. Все существующие тесты проходят; новые unit/integration-тесты покрывают D-03, D-04, D-05, D-07

---

## Next steps

1. `/clear`
2. `/gsd-plan-phase 61` — researcher + planner создадут `61-RESEARCH.md` и N PLAN.md файлов

**Рекомендованное число планов:** 6-7
- PLAN-01: academic-service — entity/DTO/migration для lesson binding
- PLAN-02: academic-service — валидации (дата, пара существует, subject match) + гарды (publishedBy)
- PLAN-03: schedule-service — gRPC resolveLesson (если нужен новый метод)
- PLAN-04: events — обновление JSON Schema + notification-bot handler для `homework.updated`
- PLAN-05: web-panel — страница `/headman/homework` + inline-форма + sidebar
- PLAN-06: web-panel — переписать `/student/homework` с тремя режимами + фильтром
- PLAN-07: E2E + phase-61-report.md + full build regression
