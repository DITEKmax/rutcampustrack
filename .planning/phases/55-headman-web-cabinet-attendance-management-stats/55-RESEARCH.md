# Phase 55: Headman Web Cabinet — Attendance Management + Stats — Research

**Researched:** 2026-04-09
**Domain:** Angular 18 + Spring Boot 3.4 — headman journal grid, optimistic UI, attendance marking, threshold inline edit
**Confidence:** HIGH (все ключевые факты верифицированы по исходному коду в репозитории)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Добавить поле `lessonId` в `JournalCell` DTO (`attendance-api-contract`) — стандартный конструктор, без Lombok.
- **D-02:** `JournalCell` — контрактный модуль, никакого Lombok. Поле добавляется обычным расширением конструктора.
- **D-03:** Создать `HeadmanJournalGridComponent` (новый компонент, не модифицировать `JournalGridComponent` учителя). Ячейка кликабельна, каждый клик немедленно вызывает `markAttendance`. Оптимистичный UI: обновить локально, откатить при ошибке со снекбаром.
- **D-04:** Страница журнала старосты — только выпадающий список предметов (без группы). Поток: init → `getGroupMembers()` для получения `groupId` → при выборе предмета → `getJournal(groupId, subjectId, dateFrom, dateTo)`. Диапазон дат по умолчанию: первый день текущего месяца — сегодня.
- **D-05:** Добавить `getJournal` и `markAttendance` в `HeadmanApiService`. `getJournal` → `GET /api/attendance/reports/journal`. `markAttendance` → `PUT /api/attendance/lessons/{lessonId}/students/{userId}` с телом `{ status }`.
- **D-06:** Страницы excuses и late-checkin: `catchError(() => of(null))` при ошибке, проверка null в шаблоне.
- **D-07:** Текст пустого состояния: "Функция находится в разработке. Заявки появятся здесь автоматически." CSS-класс `.page-empty`.
- **D-08:** Компоненты-оболочки: `HeadmanExcusesComponent` и `HeadmanLateCheckinComponent`, никаких диалогов.
- **D-09:** `HeadmanStatsComponent` — загружает журнал для ВСЕХ предметов группы, вычисляет процент посещаемости из статусов ячеек. `forkJoin` для параллельных загрузок по предметам. Нет отдельного эндпоинта статистики — всё из данных журнала.
- **D-10:** Инлайн-редактирование порога: `MatInput type=number`, `resolveThreshold(groupId, subjectId)` + `setSubjectThreshold(subjectId, minPercentage)` в `HeadmanApiService`. Обновить сигнал без перезагрузки страницы.
- **D-11:** Добавить `resolveThreshold` и `setSubjectThreshold` в `HeadmanApiService`. `resolveThreshold` → `GET /api/academic/thresholds/resolve?groupId=X&subjectId=Y`. `setSubjectThreshold` → `PUT /api/academic/thresholds/subject?subjectId=Y` с телом `{ minPercentage }`.
- **D-12:** Добавить 4 пункта навигации в массив `allNavItems` в `sidebar.component.ts` после записи `/headman/subjects`.
- **D-13:** Добавить 4 ленивых дочерних маршрута в блок headman в `app.routes.ts` перед `redirectTo: 'dashboard'`.
- **D-14:** Backend: расширить тесты `ReportService` — проверить сериализацию `lessonId` в JSON-ответе.
- **D-15:** Angular: spec-файлы для `HeadmanJournalPageComponent`, `HeadmanStatsComponent`, `HeadmanExcusesComponent`, `HeadmanLateCheckinComponent`. Все 40 существующих vitest-spec должны пройти.

### Claude's Discretion

- Порядок циклического переключения статусов в ячейке журнала (рекомендуется карта `NEXT_STATUS`).
- Выбор диапазона дат: два отдельных поля ввода (как в журнале учителя).
- Индикатор загрузки при переключении предмета: `MatProgressBar` вверху карточки.
- Точные названия иконок Phosphor для новых пунктов сайдбара.
- Окно семестра для загрузки статистики: первый день текущего семестра или последние 30 дней при недоступности эндпоинта семестра.

### Deferred Ideas (OUT OF SCOPE)

- Реальное одобрение/отклонение тикетов о пропусках (требует реализации бэкенд-эндпоинта).
- Реальное одобрение/отклонение запросов поздней отметки (требует реализации бэкенд-эндпоинта).
- Массовые действия в журнале (отметить всех отсутствующими / присутствующими).
- PWA-режим старосты — Фаза 56.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HEAD-WEB-05 | `/headman/journal` — фильтр по предмету → матрица студенты×пары, массовое выставление статусов посещаемости | D-01..05, паттерны JournalGridComponent учителя, MarkingApi, JournalCell расширение |
| HEAD-WEB-06 | `/headman/excuses` — список тикетов о пропусках с действиями одобрить/отклонить | D-06..08, паттерн degradation из HeadmanDashboardComponent |
| HEAD-WEB-07 | `/headman/late-checkin` — список запросов поздней отметки с действиями | D-06..08, паттерн degradation |
| HEAD-WEB-08 | `/headman/stats` — статистика посещаемости группы с настраиваемым порогом красной зоны по предмету | D-09..11, ThresholdApi, forkJoin + inline MatInput |
</phase_requirements>

---

## Summary

Фаза 55 — это чисто фронтенд-расширение (Angular) с одним небольшим изменением бэкенда. Бэкенд-изменение: добавить поле `lessonId` в `JournalCell` Java-класс и передать его из `ReportService.getJournal()`. Это единственное изменение в Java-коде — данные уже доступны через `AttendanceRecord.lessonId()`, просто не маппятся в ответ.

Основная работа на фронтенде: (1) `HeadmanJournalGridComponent` с кликабельными ячейками и оптимистичным UI; (2) `HeadmanJournalPageComponent` с выбором предмета и диапазона дат; (3) тонкие оболочки для excuses/late-checkin с graceful degradation; (4) `HeadmanStatsComponent` с вычислением процентов из данных журнала и инлайн-редактированием порога; (5) регистрация 4 маршрутов и 4 пунктов сайдбара.

Все необходимые бэкенд-эндпоинты уже реализованы: `PUT /attendance/lessons/{lessonId}/students/{userId}`, `GET /attendance/reports/journal`, `GET /academic/thresholds/resolve`, `PUT /academic/thresholds/subject`. Ограничение: `MarkingService.ALLOWED_STATUSES` исключает `CANCELLED` — его нельзя выставить через API; отображать `cancelled` в ячейках журнала можно, но не отправлять на сервер.

**Основная рекомендация:** Начинать с бэкенд-фикса (D-01 + D-02), затем фронтенд-слои в порядке зависимостей: расширение типов → HeadmanApiService → компоненты страниц → маршруты + сайдбар → тесты.

---

## Standard Stack

### Core
| Библиотека | Версия | Назначение | Почему стандарт |
|-----------|--------|------------|-----------------|
| Angular CDK Table | уже в проекте | Матрица журнала | Уже используется в `JournalGridComponent` учителя [VERIFIED: исходный код] |
| Angular CDK ScrollingModule | уже в проекте | Виртуальный скролл | Уже используется в `JournalGridComponent` учителя [VERIFIED: исходный код] |
| Angular Material | уже в проекте | MatSelect, MatProgressBar, MatSnackBar, MatInput | Стандарт проекта [VERIFIED: исходный код] |
| RxJS forkJoin | уже в проекте | Параллельная загрузка журналов по предметам | Уже используется в `HeadmanDashboardComponent` [VERIFIED: исходный код] |
| RxJS catchError + of | уже в проекте | Graceful degradation | Паттерн из `HeadmanDashboardComponent` [VERIFIED: исходный код] |

### Поддерживающие
| Библиотека | Версия | Назначение | Когда использовать |
|-----------|--------|------------|-------------------|
| Phosphor Icons | уже в проекте | Иконки сайдбара | Для новых 4 пунктов навигации [VERIFIED: исходный код] |
| @testing-library/angular | уже в проекте | render() в spec-файлах | Тесты компонентов Angular [VERIFIED: spec-файлы] |

---

## Architecture Patterns

### Рекомендуемая структура новых файлов
```
frontends/web-panel/src/app/features/headman/
├── journal/
│   ├── headman-journal-page.component.ts     # страница с MatSelect предмета + диапазон дат
│   ├── headman-journal-page.component.html   # template для страницы
│   ├── headman-journal-page.component.spec.ts
│   └── headman-journal-grid/
│       ├── headman-journal-grid.component.ts  # CdkTable + кликабельные ячейки
│       └── headman-journal-grid.component.spec.ts
├── excuses/
│   ├── headman-excuses.component.ts           # тонкая оболочка, graceful degradation
│   └── headman-excuses.component.spec.ts
├── late-checkin/
│   ├── headman-late-checkin.component.ts      # тонкая оболочка, graceful degradation
│   └── headman-late-checkin.component.spec.ts
└── stats/
    ├── headman-stats.component.ts             # forkJoin журналы + инлайн-порог
    └── headman-stats.component.spec.ts
```

### Паттерн 1: Расширение JournalCell без Lombok (D-01 + D-02)
**Что:** Добавить `Long lessonId` в конструктор `JournalCell` без Lombok.
**Когда:** Контрактный модуль — никакого Lombok.
**Пример:**
```java
// Source: VERIFIED — services/attendance-service/attendance-api-contract/.../JournalCell.java
public class JournalCell {
    private final Long lessonId;  // НОВОЕ ПОЛЕ — добавить первым или последним
    private final String date;
    private final Integer lessonNumber;
    private final String status;
    private final String symbol;

    public JournalCell(Long lessonId, String date, Integer lessonNumber, String status, String symbol) {
        this.lessonId = lessonId;
        this.date = date;
        this.lessonNumber = lessonNumber;
        this.status = status;
        this.symbol = symbol;
    }

    public Long getLessonId() { return lessonId; }
    // ... остальные геттеры без изменений
}
```

Соответствующее изменение в `ReportService.getJournal()` (строка 134):
```java
// Source: VERIFIED — ReportService.java строки 133-138
.map(r -> new JournalCell(
    r.lessonId(),       // НОВЫЙ параметр
    r.lessonDate().toString(),
    r.lessonNumber(),
    r.status().name().toLowerCase(),
    statusSymbol(r.status())
))
```

### Паттерн 2: Кликабельная ячейка журнала с оптимистичным UI
**Что:** Ячейка хранит текущий статус в локальном signal, при клике переключает на следующий, немедленно вызывает PUT, откатывает при ошибке.
**Когда:** `HeadmanJournalGridComponent` — каждый клик = дискретное серверное действие.
**Пример:**
```typescript
// Source: ASSUMED — основан на паттернах оптимистичного UI из CONTEXT.md D-03
// и существующей структуре JournalGridComponent

const NEXT_STATUS: Record<AttendanceStatus, AttendanceStatus> = {
  absent: 'present',
  present: 'excused',
  excused: 'free_attendance',
  free_attendance: 'absent',  // cancelled пропускаем — нельзя отправить на сервер
  cancelled: 'absent',        // из cancelled -> absent (разрешено API)
};

// В компоненте ячейки:
onCellClick(cell: HeadmanJournalCell, row: JournalStudentRow): void {
  const prevStatus = cell.status;
  const nextStatus = NEXT_STATUS[prevStatus];
  
  // Оптимистичное обновление
  cell.status = nextStatus;
  cell.symbol = STATUS_SYMBOLS[nextStatus];
  
  this.headmanApi.markAttendance(cell.lessonId!, row.userId, nextStatus)
    .pipe(catchError(() => {
      // Откат
      cell.status = prevStatus;
      cell.symbol = STATUS_SYMBOLS[prevStatus];
      this.snackBar.open('Не удалось обновить статус', undefined, { duration: 4000 });
      return of(null);
    }))
    .subscribe();
}
```

**ВАЖНО:** `CANCELLED` нельзя отправить через `markAttendance` — `MarkingService.ALLOWED_STATUSES` содержит только `{PRESENT, ABSENT, EXCUSED, FREE_ATTENDANCE}`. Поэтому `cancelled` в NEXT_STATUS должен переходить в `absent`, а не циклиться через `cancelled`. [VERIFIED: MarkingService.java строки 47-52]

### Паттерн 3: Graceful Degradation (D-06, D-07, D-08)
**Что:** `catchError(() => of(null))` → проверка null в шаблоне → пустое состояние.
**Когда:** `HeadmanExcusesComponent` и `HeadmanLateCheckinComponent`.
**Пример:**
```typescript
// Source: VERIFIED — HeadmanDashboardComponent (существующий паттерн)
this.headmanApi.getPendingExcuses()
  .pipe(catchError(() => of(null)))
  .subscribe(data => {
    this.excuses.set(data); // null при ошибке
  });
```
```html
@if (excuses() === null) {
  <div class="page-empty">
    <i class="ph ph-clock"></i>
    Функция находится в разработке. Заявки появятся здесь автоматически.
  </div>
}
```

### Паттерн 4: forkJoin для Stats (D-09)
**Что:** Загрузить список предметов, затем параллельно загрузить журнал для каждого предмета через `forkJoin`.
**Когда:** `HeadmanStatsComponent`.
**Пример:**
```typescript
// Source: ASSUMED — основан на forkJoin паттерне из HeadmanDashboardComponent [VERIFIED]
// и описании D-09 из CONTEXT.md

const subjects = this.subjects(); // уже загружены через listSubjects()
const journalRequests = subjects.map(s =>
  this.headmanApi.getJournal(groupId, s.id, semesterStart, today)
    .pipe(catchError(() => of(null)))
);

forkJoin(journalRequests).subscribe(journalResults => {
  const stats = subjects.map((s, i) => {
    const journal = journalResults[i];
    const rate = journal ? computeAttendanceRate(journal) : null;
    return { subject: s, rate, threshold: null }; // threshold загружается отдельно
  });
  this.statsRows.set(stats);
});
```

### Паттерн 5: Инлайн-редактирование порога (D-10, D-11)
**Что:** `MatInput type=number` с blur/enter → вызов `setSubjectThreshold`, обновление локального сигнала.
**Когда:** `HeadmanStatsComponent`.
**Пример:**
```typescript
// Source: ASSUMED — основан на описании D-10 из CONTEXT.md
onThresholdBlur(subjectId: number, event: Event): void {
  const value = Number((event.target as HTMLInputElement).value);
  if (isNaN(value) || value < 0 || value > 100) return;
  
  this.headmanApi.setSubjectThreshold(subjectId, value).subscribe({
    next: () => {
      // Обновить сигнал для этого предмета
      this.thresholds.update(map => ({ ...map, [subjectId]: value }));
    },
    error: () => {
      this.snackBar.open('Не удалось сохранить порог', undefined, { duration: 4000 });
    }
  });
}
```

### Паттерн 6: TypeScript-тип для HeadmanJournalCell
**Что:** Тип ячейки журнала старосты — расширяет `JournalCell` учителя добавлением `lessonId`.
**Почему отдельный тип:** Ячейки журнала учителя не нуждаются в `lessonId`, поэтому тип учителя не трогаем. Согласно CONTEXT.md D-01, в `teacher/journal/types.ts` добавляется `lessonId?: number` (опционально, без нарушения обратной совместимости).

```typescript
// Source: ASSUMED — рекомендуется вынести в headman/journal/types.ts
// Или расширить teacher types.ts добавив необязательный lessonId (D-01 подход)
export interface HeadmanJournalCell extends JournalCell {
  lessonId: number; // обязателен для headman, optional в JournalCell учителя
}
```

### Anti-Patterns — Чего избегать
- **Модификация `JournalGridComponent` учителя:** Не изменять. Создать отдельный `HeadmanJournalGridComponent` (D-03).
- **`CANCELLED` статус через API:** Нельзя отправлять — будет `400 Bad Request` от `MarkingService`. Из `cancelled` переходить в `absent`. [VERIFIED: MarkingService.java]
- **`@Enumerated(EnumType.ORDINAL)`:** Запрещено правилами CLAUDE.md — использовать только строки.
- **Lombok в контрактном модуле:** `JournalCell` в `*-api-contract` — без Lombok [VERIFIED: CLAUDE.md].
- **Полная перезагрузка при сохранении порога:** Обновлять только signal для конкретного предмета (D-10).

---

## Don't Hand-Roll

| Проблема | Не строить | Использовать вместо | Почему |
|---------|-----------|---------------------|--------|
| Матрица журнала с горизонтальным скроллом | Кастомная таблица | CdkTable + CdkVirtualScrollViewport | Уже реализовано в `JournalGridComponent` — копировать структуру [VERIFIED] |
| Параллельные HTTP-запросы | Последовательные subscribe | `forkJoin` | Уже используется в `HeadmanDashboardComponent` [VERIFIED] |
| Обработка 404 как пустого состояния | try/catch | `catchError(() => of(null))` | Паттерн установлен в `HeadmanDashboardComponent` [VERIFIED] |
| HTTP Bearer token | Ручные заголовки | `authInterceptor` (уже работает глобально) | Документировано в `HeadmanApiService` [VERIFIED] |

---

## Common Pitfalls

### Pitfall 1: CANCELLED не проходит через MarkingApi
**Что пойдёт не так:** Клик на ячейку с `cancelled` → попытка отправить `{ status: 'cancelled' }` → `400 Bad Request` от `MarkingService` (только `PRESENT/ABSENT/EXCUSED/FREE_ATTENDANCE` разрешены).
**Почему:** `MarkingService.ALLOWED_STATUSES = Set.of(PRESENT, ABSENT, EXCUSED, FREE_ATTENDANCE)` — `CANCELLED` намеренно исключён как системный статус.
**Как избежать:** В карте `NEXT_STATUS` пропустить `cancelled` как переходный статус — из `cancelled` переходить сразу в `absent`.
**Признак проблемы:** `400 Bad Request` в консоли при клике на ячейку с `--`.

### Pitfall 2: lessonId отсутствует в ответе до D-01
**Что пойдёт не так:** `HeadmanJournalGridComponent` получает `journal.students[n].records[m].lessonId === undefined` → PUT отправляется на `PUT /api/attendance/lessons/undefined/students/X` → `404`.
**Почему:** `JournalCell` Java-класс не имеет поля `lessonId` — это подтверждено анализом кода (4 поля: date, lessonNumber, status, symbol).
**Как избежать:** Реализовать D-01 первым делом, до написания Angular-компонентов.
**Признак проблемы:** `lessonId` равен `undefined` в полученных данных журнала.

### Pitfall 3: forkJoin в Stats может перегрузить бэкенд при большом числе предметов
**Что пойдёт не так:** Если у группы 10+ предметов, `forkJoin` запускает 10+ параллельных GET-запросов на один и тот же `attendance-service`.
**Почему:** D-09 требует параллельных загрузок журнала по предметам.
**Как избежать:** Ограничить диапазон дат (текущий семестр или последние 60 дней). Если `activeSemester` не доступен легко — использовать последние 90 дней как fallback. Добавить `catchError(() => of(null))` на каждый запрос внутри forkJoin.
**Признак проблемы:** Долгое время загрузки страницы статистики или ошибки тайм-аута.

### Pitfall 4: ThresholdApi.setSubjectThreshold требует query param, не path param
**Что пойдёт не так:** Angular-код отправляет `PUT /api/academic/thresholds/subject/42` (path param) → `404`.
**Почему:** Контракт `ThresholdApi` использует `@RequestParam Long subjectId` — это query param: `PUT /api/academic/thresholds/subject?subjectId=42` с телом `{ minPercentage }`.
**Как избежать:** В `HeadmanApiService.setSubjectThreshold` использовать `HttpParams.set('subjectId', id)`.
**Признак проблемы:** `404 Not Found` при сохранении порога.

### Pitfall 5: Оба Angular-типа JournalCell должны быть согласованы
**Что пойдёт не так:** Добавление `lessonId` в `teacher/journal/types.ts` без аннотации `?` (optional) сломает тесты `JournalGridComponent` учителя (mock-данные без `lessonId`).
**Почему:** `journal-grid.component.spec.ts` создаёт mock-объекты `JournalCell` без `lessonId`.
**Как избежать:** Добавить `lessonId?: number` (optional) в `JournalCell` в `teacher/journal/types.ts`. В типе `HeadmanJournalCell` поле обязательно: `lessonId: number`.
**Признак проблемы:** Ошибки TypeScript в `journal-grid.component.spec.ts` после изменения типа.

---

## Code Examples

Проверенные паттерны из исходного кода:

### Текущий вызов getJournal (JournalApiService учителя)
```typescript
// Source: VERIFIED — frontends/web-panel/src/app/features/teacher/journal/journal-api.service.ts
getJournal(groupId: number, subjectId: number, dateFrom: string, dateTo: string): Observable<JournalResponse> {
  const params = new HttpParams()
    .set('groupId', groupId)
    .set('subjectId', subjectId)
    .set('dateFrom', dateFrom)
    .set('dateTo', dateTo);
  return this.http.get<JournalResponse>('/api/attendance/reports/journal', { params });
}
```
HeadmanApiService добавляет идентичный метод, направленный на тот же эндпоинт.

### ThresholdApi — точные сигнатуры эндпоинтов
```java
// Source: VERIFIED — academic-api-contract/src/.../ThresholdApi.java

// Resolve (GET с query params):
@GetMapping("/resolve")
ResponseEntity<EntityModel<ResolvedThresholdResponse>> resolveThreshold(
    @RequestParam(required = false) Long groupId,
    @RequestParam(required = false) Long subjectId);

// Set subject threshold (PUT с query param + body):
@PutMapping("/subject")
ResponseEntity<EntityModel<ThresholdResponse>> setSubjectThreshold(
    @Valid @RequestBody SetThresholdRequest request,  // { minPercentage: Integer }
    @RequestParam Long subjectId);
```

### MarkingApi — точная сигнатура эндпоинта
```java
// Source: VERIFIED — attendance-api-contract/src/.../MarkingApi.java
@PutMapping("/lessons/{lessonId}/students/{userId}")
ResponseEntity<EntityModel<MarkResponse>> mark(
    @PathVariable Long lessonId,
    @PathVariable Long userId,
    @Valid @RequestBody MarkRequest request); // { status: AttendanceStatus }
```

### Маршруты headman (текущее состояние — 3 маршрута)
```typescript
// Source: VERIFIED — frontends/web-panel/src/app/app.routes.ts строки 169-203
{
  path: 'headman',
  canActivate: [headmanGuard],
  data: { eyebrow: 'Староста' },
  children: [
    { path: 'dashboard', ... },
    { path: 'group', ... },
    { path: 'subjects', ... },
    // INSERT 4 new routes HERE, перед:
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  ],
}
```

### Паттерн инициализации дат журнала (из JournalPageComponent учителя)
```typescript
// Source: VERIFIED — frontends/web-panel/src/app/features/teacher/journal/journal-page.component.ts
ngOnInit(): void {
  const now = new Date();
  this.dateFrom.set(
    new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10),
  );
  this.dateTo.set(now.toISOString().slice(0, 10));
}
```

---

## Validation Architecture

### Test Framework
| Свойство | Значение |
|----------|---------|
| Фреймворк | Vitest + @testing-library/angular |
| Конфиг | `frontends/web-panel/vitest.config.ts` |
| Команда быстрого запуска | `npx vitest run --reporter=verbose` (из `frontends/web-panel/`) |
| Полный набор | `npx vitest run` |
| Backend (JUnit) | `./gradlew :services:attendance-service:attendance-app:test` |

### Карта требований → тесты
| ID | Поведение | Тип теста | Команда | Файл существует? |
|----|-----------|-----------|---------|-----------------|
| HEAD-WEB-05 | `HeadmanJournalPageComponent` рендерит grid после выбора предмета | unit | `npx vitest run src/app/features/headman/journal/` | ❌ Wave 0 |
| HEAD-WEB-05 | Клик по ячейке вызывает `markAttendance` с правильным lessonId | unit | (тот же файл) | ❌ Wave 0 |
| HEAD-WEB-05 | При ошибке PUT откат статуса и снекбар | unit | (тот же файл) | ❌ Wave 0 |
| HEAD-WEB-05 | JournalCell Java сериализует `lessonId` в JSON | integration | `./gradlew :services:attendance-service:attendance-app:test --tests "*.ReportServiceTest"` | ✅ расширить |
| HEAD-WEB-06 | `HeadmanExcusesComponent` показывает empty-state при 404 | unit | `npx vitest run src/app/features/headman/excuses/` | ❌ Wave 0 |
| HEAD-WEB-07 | `HeadmanLateCheckinComponent` показывает empty-state при 404 | unit | `npx vitest run src/app/features/headman/late-checkin/` | ❌ Wave 0 |
| HEAD-WEB-08 | `HeadmanStatsComponent` вычисляет процент посещаемости из данных журнала | unit | `npx vitest run src/app/features/headman/stats/` | ❌ Wave 0 |
| HEAD-WEB-08 | Изменение порога вызывает `setSubjectThreshold` и обновляет signal | unit | (тот же файл) | ❌ Wave 0 |
| Регрессия | Все 40 существующих spec файлов проходят | unit | `npx vitest run` | ✅ существуют |
| Регрессия | Тесты `JournalGridComponent` учителя проходят без изменений | unit | `npx vitest run src/app/features/teacher/journal/` | ✅ существуют |

### Wave 0 Gaps
- [ ] `src/app/features/headman/journal/headman-journal-page.component.spec.ts` — охватывает HEAD-WEB-05 (рендер, выбор предмета, клик ячейки, оптимистичный UI)
- [ ] `src/app/features/headman/excuses/headman-excuses.component.spec.ts` — охватывает HEAD-WEB-06 (empty-state на 404)
- [ ] `src/app/features/headman/late-checkin/headman-late-checkin.component.spec.ts` — охватывает HEAD-WEB-07 (empty-state на 404)
- [ ] `src/app/features/headman/stats/headman-stats.component.spec.ts` — охватывает HEAD-WEB-08 (вычисление процентов, инлайн-порог)
- Расширить `ReportServiceTest.java` — добавить тест: `JournalCell` содержит `lessonId` в ответе getJournal

---

## Project Constraints (from CLAUDE.md)

| Ограничение | Применимость в Фазе 55 |
|-------------|----------------------|
| Contract-first: маппинги только в интерфейсе | Не актуально — `MarkingApi`/`ThresholdApi`/`ReportApi` уже определены и не изменяются |
| Без Lombok в `*-api-contract` | КРИТИЧНО для `JournalCell` — только обычный конструктор + геттеры |
| Request DTO = Java record, Response DTO = класс | `JournalCell` — класс (не record) т.к. является полем ответа — без изменений |
| `@Enumerated(EnumType.ORDINAL)` запрещено | Не применимо (MongoDB, не PostgreSQL) |
| Soft delete (status=archived) | Не применимо для этой фазы |
| HATEOAS Level 3 | Бэкенд-ответы уже возвращают `EntityModel` — Angular просто читает поля |
| `@ControllerAdvice` централизованно | Angular использует `catchError` — соответствует |
| PUT = полное обновление, PATCH = частичное | `PUT /lessons/{lessonId}/students/{userId}` — полное обновление статуса — соответствует |

---

## State of the Art

| Старый подход | Текущий подход | Применимость |
|--------------|----------------|-------------|
| Отдельные компоненты-журналы для каждой роли | Переиспользовать структуру CdkTable из учителя, создать headman-версию | Фаза 55: D-03 |
| Save-all кнопка для batch-обновления | Каждый клик = дискретный PUT (нет очереди) | Фаза 55: D-03 |
| Отдельный stats-эндпоинт | Вычислять статистику из данных journal-эндпоинта | Фаза 55: D-09 |

---

## Assumptions Log

| # | Утверждение | Раздел | Риск при ошибке |
|---|------------|--------|-----------------|
| A1 | Семестровое окно для stats: если `activeSemester` недоступен из `HeadmanApiService` — использовать последние 90 дней | Architecture Pattern 4 | Слишком широкий диапазон вызовет медленную загрузку; слишком узкий покажет неполную статистику |
| A2 | `CANCELLED → absent` как переход в NEXT_STATUS — компромисс UX | Architecture Pattern 2 | Если product owner хочет иной порядок — изменить только карту `NEXT_STATUS` |
| A3 | `HeadmanJournalCell` — отдельный тип с `lessonId: number` (обязателен) vs добавить `lessonId?: number` в тип учителя | Architecture Pattern 6 | Незначительно — оба подхода работают; отдельный тип чище |

---

## Open Questions

1. **Семестровое окно для Stats**
   - Что знаем: `HeadmanApiService` не имеет метода `getActiveSemester()`; академическая служба может его предоставить, но не подключена
   - Что неясно: есть ли доступный эндпоинт семестра для Angular headman-кабинета
   - Рекомендация: использовать первый день текущего академического года (1 сентября или 1 февраля) как fallback вычислением на стороне клиента; альтернативно — последние 90 дней. Это решение на усмотрение Claude (D-15 в CONTEXT.md).

2. **Пустое состояние Stats при нулевых данных журнала**
   - Что знаем: `forkJoin` при пустом списке предметов вернёт пустой массив
   - Что неясно: как выглядит UX при 0 предметах у группы
   - Рекомендация: показывать сообщение "У вашей группы нет предметов" и ссылку на `/headman/subjects`.

---

## Environment Availability

Step 2.6: SKIPPED (нет внешних зависимостей — фаза включает только изменения кода/конфигурации в Angular и Java; все инфраструктурные зависимости уже запущены).

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: исходный код] `services/attendance-service/attendance-api-contract/…/JournalCell.java` — подтверждено: нет поля `lessonId`
- [VERIFIED: исходный код] `services/attendance-service/attendance-api-contract/…/MarkingApi.java` — точная сигнатура PUT эндпоинта
- [VERIFIED: исходный код] `services/academic-service/academic-api-contract/…/ThresholdApi.java` — `@RequestParam Long subjectId` подтверждён
- [VERIFIED: исходный код] `services/attendance-service/attendance-app/…/MarkingService.java` — `ALLOWED_STATUSES` без `CANCELLED` подтверждён
- [VERIFIED: исходный код] `services/attendance-service/attendance-app/…/ReportService.java` — `r.lessonId()` доступен из `AttendanceRecord`, не маппится
- [VERIFIED: исходный код] `services/attendance-service/attendance-app/…/shared/port/AttendanceRecord.java` — содержит `lessonId` как первое поле
- [VERIFIED: исходный код] `frontends/web-panel/src/app/features/headman/shared/headman-api.service.ts` — 13 методов, нет методов Phase 55
- [VERIFIED: исходный код] `frontends/web-panel/src/app/features/teacher/journal/journal-grid/journal-grid.component.ts` — CdkTable + signals паттерн
- [VERIFIED: исходный код] `frontends/web-panel/src/app/app.routes.ts` — headman блок с 3 маршрутами (нужно добавить 4)
- [VERIFIED: исходный код] `frontends/web-panel/src/app/layout/sidebar/sidebar.component.ts` — `allNavItems` с 3 headman пунктами
- [VERIFIED: исходный код] `frontends/web-panel/src/app/features/headman/dashboard/headman-dashboard.component.ts` — forkJoin + catchError паттерн

### Secondary (MEDIUM confidence)
- [VERIFIED: spec-файлы] 40 spec-файлов в `frontends/web-panel/src` подтверждены — vitest + @testing-library/angular

### Tertiary (LOW confidence)
- [ASSUMED] Логика вычисления посещаемости в `HeadmanStatsComponent` (derive from journal cells)

---

## Metadata

**Разбивка по confidence:**
- Бэкенд-изменения: HIGH — полностью верифицировано по исходному коду
- Angular-паттерны (сайдбар, маршруты, headmanApi): HIGH — верифицировано по исходному коду
- Реализация HeadmanJournalGrid (оптимистичный UI, NEXT_STATUS): MEDIUM — паттерн задан в CONTEXT.md, детали реализации ASSUMED
- HeadmanStats (forkJoin + вычисление): MEDIUM — подход задан в CONTEXT.md, конкретный код ASSUMED

**Дата исследования:** 2026-04-09
**Действителен до:** 2026-05-09 (стабильный стек)
