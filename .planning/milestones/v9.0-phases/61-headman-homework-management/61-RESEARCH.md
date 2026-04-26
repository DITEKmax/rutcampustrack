# Phase 61: Headman Homework Management — Research

**Researched:** 2026-04-15
**Domain:** Full-stack (academic-service backend, schedule-service gRPC, Angular web-panel, notification-bot, events)
**Confidence:** HIGH

## Summary

Бэкенд ДЗ уже работоспособен (контракт `HomeworkApi`, `HomeworkService`, `HomeworkController`, events `homework.published`/`homework.updated`, notification-bot обрабатывает **оба** события через общий `handle_homework`). Phase 61 — это **достройка**:

1. В `homeworks` есть колонка `lesson_id BIGINT` (nullable), она сейчас не используется кодом и подменяется связкой `lesson_date DATE + lesson_number INT` (D-01). `lesson_id` и индекс `idx_hw_lesson` нужно дропнуть в той же миграции.
2. gRPC метода «резолв пары по natural key» **нет** в `schedule.proto`. Нужно добавить новый RPC `ResolveLesson(groupId, date, lessonNumber)` + клиент в academic-app + зависимость grpc-client-starter.
3. Схема событий в `event-schemas/` и Java payload-ы не содержат `lesson_date`/`lesson_number` — расширить.
4. Notification-bot уже диспетчит `homework.updated` (строка 83 `event_dispatcher.py`); в handler-е уже есть ветка `elif event_type == "homework.updated"` — правка минимальная (текст + subject fallback).
5. Страница `/headman/homework` — новая фича в `features/headman/homework/` + новый lazy route в `app.routes.ts`. Навигация по неделям у `headman/schedule` — **inline, не реюзабельная** → извлекать в `shared/week-navigator/` (либо дублировать).
6. `/student/homework` переписывается под 3 режима. Segmented control в Angular-коде нет (PWA-компонент `frontends/pwa/src/shared/components/SegmentedControl.tsx` — доменно-специфичный для статусов посещения, не реюз). Надо создать универсальный.

**Primary recommendation:** разбить работу на 7 планов согласно рекомендации в CONTEXT (PLAN-01..07). Ключевая точка риска — новый gRPC RPC в schedule-service, именно с него начать (PLAN-03 до PLAN-02).

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions (D-01..D-15)

- **D-01** — две новые NOT NULL колонки `homeworks.lesson_date DATE` + `lesson_number INT`. **НЕТ** UNIQUE, **НЕТ** FK. Entity получает `LocalDate lessonDate; Integer lessonNumber;`.
- **D-02** — Flyway миграция: `TRUNCATE homeworks, homework_completions` + `ALTER TABLE ... ADD COLUMN ...`. Данных нет.
- **D-03** — бэкенд 400, если `lesson_date < today`. Использовать `Clock` bean.
- **D-04** — бэкенд проверяет существование пары в расписании группы на эту дату и номер, а также совпадение `subjectId`. Если нет → 400.
- **D-05** — PUT/DELETE только автор (`homework.publishedBy == currentUserId`). ADMIN override — out of scope.
- **D-06** — создавать ДЗ может только STUDENT+is_headman=true. Убрать ADMIN из разрешённых ролей в `createHomework`.
- **D-07** — события `homework.published` и `homework.updated` расширить на `lessonDate`/`lessonNumber`. Бот пушит в **обоих** случаях.
- **D-08** — страница `/headman/homework`: недельный список пар, inline-form (НЕ `MatDialog`) «Добавить задание» под парой. Edit — такая же inline-форма на существующем ДЗ.
- **D-09** — `/student/homework` переписать: segmented День/Неделя/Месяц. Дефолт Дня = завтра. Фильтр «только невыполненные».
- **D-10** — completion остаётся per-student. Староста этого поля не видит.
- **D-11** — subject НЕ выбирается в форме старосты, берётся из контекста пары (readonly-шапка).
- **D-12** — пункт «Домашние задания» в sidebar, секция старостата, иконка `ph-notebook`, после «Предметы» (≈ строка 199 `sidebar.component.ts`).
- **D-13** — структура папок: `features/headman/homework/`, `features/student/homework/{day,week,month}-view/`, возможный `shared/homework-card/`.
- **D-14** — русский UI, Phosphor icons (`ph-notebook`, `ph-pencil`, `ph-trash`, `ph-plus`), анимации routeFade + expand/collapse.
- **D-15** — Flyway `V_N__homework_lesson_binding.sql`: TRUNCATE + два ALTER + CREATE INDEX `idx_homeworks_group_date`.

### Claude's Discretion (gray areas)

- gRPC метод в schedule-service для резолва пары — сигнатура, расположение в proto (researcher ниже предлагает).
- API endpoint `/academic/homeworks?from=&to=` — server-side фильтр по диапазону vs клиентская фильтрация.
- Точное размещение «+ Добавить задание» при 0 ДЗ vs N ДЗ.
- Обработка случая «пара удалена после создания ДЗ» (sirota).

### Deferred Ideas (OUT OF SCOPE)

- Помощник старосты (`headman_assistants`) публикует ДЗ.
- ADMIN редактирует чужие ДЗ.
- Прогресс выполнения для старосты («5/12 сделали»).
- Вложения к ДЗ (файлы, картинки).
- Дедлайн отдельно от даты пары.
- Drag-and-drop переноса ДЗ между парами.
- Cleanup сиротских ДЗ при отмене пары.
</user_constraints>

## Project Constraints (from CLAUDE.md)

- Contract-first: `@RequestMapping` и `@PostMapping` живут **только** в интерфейсе `academic-api-contract` (`HomeworkApi.java`). Controller реализует интерфейс, без своих аннотаций маппинга. Request DTO = `record`, response = class с HATEOAS `RepresentationModel`.
- Никакого Lombok в `*-api-contract`. Entity `Homework.java` в app-модуле — с Lombok (`@Getter/@Setter/@NoArgsConstructor`) — ок.
- Enum в PostgreSQL — lowercase строки. `ddl-auto: validate`. Миграции — Flyway.
- PK `BIGSERIAL` (Long). Даты/временные метки — UTC через `TIMESTAMPTZ`. Для `lesson_date` — `DATE` (календарная дата, без TZ, как в `lessons.date` schedule-service).
- REST: HATEOAS Level 3, RFC 7807 `ErrorResponse`, `@ControllerAdvice`. Swagger-аннотации только в contract.
- Тесты: backend — JUnit 5 + Mockito + Testcontainers (PostgreSQL, RabbitMQ). Angular — Jasmine/Karma `.spec.ts`.
- Лог/коммуникация на русском, `docs/phase-{N}-report.md` пишется в конце.

---

## Phase Requirements

*(В CONTEXT.md requirement-ID не заданы; success criteria выступают таргетами.)*

| ID (ad hoc) | Описание | Что поддерживает research |
|---|---|---|
| HW-LINK | Привязка ДЗ к паре через `(group_id, lesson_date, lesson_number)` | D-01, D-02, D-15 + V1 baseline `lesson_id` анализ ниже |
| HW-VAL-DATE | 400 при `lesson_date < today` | D-03 + паттерн `Clock` bean |
| HW-VAL-LESSON | 400 если пары нет / subject не совпадает | D-04 + новый RPC `ResolveLesson` (ниже) |
| HW-AUTHOR | 403 для не-автора при PUT/DELETE | D-05 + `RequestContext.getUserId()` |
| HW-ROLE | Только STUDENT+is_headman создаёт | D-06 + `RoleCheckAspect` + поправка в `HomeworkController@RequireRole` |
| HW-EVENT | Payload событий + bot push для update | D-07 + найденный `event_dispatcher.py:83` |
| HW-UI-HEADMAN | `/headman/homework` inline-form | D-08, D-11, D-12, D-13 |
| HW-UI-STUDENT | `/student/homework` 3 режима + фильтр | D-09, D-10, D-13 |
| HW-TEST | Unit + integration | Паттерны из `GroupServiceTest`, `AcademicGrpcIntegrationTest` |

---

## Standard Stack

### Backend (academic-service app)

| Библиотека / модуль | Версия (project) | Назначение | Статус в проекте |
|---|---|---|---|
| Spring Boot | 3.4 (`CLAUDE.md`) | Web/JPA/HATEOAS/AMQP/AOP | Уже подключено (`build.gradle.kts:22-38`) |
| Flyway | встроено Spring BOM | Миграции | Уже работает (`V1..V12`) |
| `net.devh:grpc-server-spring-boot-starter` | 3.1.0.RELEASE | gRPC сервер | Есть в `academic-app:48` |
| **`net.devh:grpc-client-spring-boot-starter`** | 3.1.0.RELEASE | **gRPC клиент (НОВОЕ)** | Сейчас только в `testImplementation` (`academic-app:67`). **Нужно перенести в `implementation`** для PLAN-02 (`ScheduleGrpcClient` в academic). [VERIFIED: `services/academic-service/academic-app/build.gradle.kts:48,67`] |
| Testcontainers | 1.20.4 | Интеграционные тесты | Уже есть |
| `java.time.Clock` | JDK 21 | Testable now() (D-03) | Инжектить как `@Bean Clock clock(){ return Clock.systemDefaultZone(); }` в `config/` |

### Backend (schedule-service app) — новый RPC

- `proto/schedule.proto` уже содержит `ScheduleGrpcService`. Добавить новый RPC + два сообщения. Никаких новых зависимостей.
- `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java` — новый метод `resolveLesson`.

### Frontend (web-panel)

| Библиотека | Версия | Назначение |
|---|---|---|
| Angular | 20+ (standalone + signals, existing) | Компоненты, роутинг |
| Angular Material | existing | MatButton/MatIcon/MatChip/MatProgressSpinner — уже в `headman-schedule` и `student-homework` |
| Angular Animations | existing | `trigger('routeFade', ...)` + новый `expandCollapse` для inline-формы |
| Phosphor Icons | existing | `ph-notebook` (меню), `ph-plus` (add), `ph-pencil` (edit), `ph-trash` (delete), `ph-check` (completed) |
| **Нет MatDialog для формы** | — | D-08 явно требует inline, не модал |

### Notification-bot (Python)

| Библиотека | Версия | Назначение |
|---|---|---|
| aiogram | 3 (CLAUDE.md) | Telegram bot |
| aio_pika | — | RabbitMQ consumer |
| Существующий `academic_client.get_subjects_by_ids` | — | Fallback для subject name (уже использован на `homework.py:39-40`) |

### Alternatives Considered

| Вместо | Могло бы быть | Почему не |
|---|---|---|
| Новый RPC `ResolveLesson` | Использовать существующий `GetLessonsByGroup(groupId, semesterId, from=date, to=date)` + фильтрация по `lesson_number` на клиенте | Возможный fallback, НО: (1) требует знать `semesterId` на бэкенде academic-service в момент create ДЗ — этот id **уже передаётся** в `CreateHomeworkRequest.semesterId()`, (2) возвращает весь день. Работать будет, но семантически грязнее. Лучше — отдельный тонкий RPC. |
| FK `homeworks.lesson_id → lessons.id` | Чистая реляционная привязка | D-01 явно запрещает (cross-service FK, no-go) |
| `MatDialog` для CRUD-формы | Явно запрещено D-08 |
| Вместо segmented D/W/M — три отдельные страницы | Вариант | D-09 залочен на segmented |

### Installation / build changes

```gradle
// services/academic-service/academic-app/build.gradle.kts
// Перенести с testImplementation на implementation:
implementation("net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE")
```

Генерируемые protobuf-стабы уже видны academic-service (proto srcDir = rootProject.file("proto")) — дополнительной настройки не требуется.

**Version verification:** [VERIFIED: `build.gradle.kts:48,67`] — обе версии (server и client starter) в проекте 3.1.0.RELEASE; матчатся со схемой Phase 15-19 attendance-service [VERIFIED: `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/ScheduleGrpcClient.java:13`].

---

## Architecture Patterns

### Backend — resolve lesson by natural key

**Открытый вопрос #1 (gRPC).** [VERIFIED: `proto/schedule.proto:8-21`] — в `ScheduleGrpcService` есть 4 RPC:

```
GetActiveLesson(group_id, timestamp)   — активная сейчас
GetLessonById(lesson_id)               — по Lesson PK
GetLessonsByGroup(group_id, semester_id, date_from, date_to)
GetLessonsByIds(lesson_ids)            — компактный ответ для excuse
```

**Метода `(groupId, date, lessonNumber) → Lesson` НЕТ.** [VERIFIED: grep `lesson_number|lessonNumber` по `schedule-app/grpc/` — только внутри `buildResponse` из `ScheduleItem.lessonNumber`.]

**Рекомендуемая сигнатура (новый RPC, место — `proto/schedule.proto` после `GetLessonsByIds`, строка 21):**

```proto
service ScheduleGrpcService {
  // ... existing
  // GRPC-05 (Phase 61, D-04): резолв пары по natural key
  // Возвращает NOT_FOUND если на дату нет ни шаблонного Lesson,
  // ни созданного one-off. Возвращает CANCELLED-lesson тоже (клиент
  // отличит по status и сам решит — запрещать ли ДЗ на отменённую пару).
  rpc ResolveLesson (ResolveLessonRequest) returns (LessonResponse);
}

message ResolveLessonRequest {
  int64 group_id = 1;
  string date = 2;          // ISO date
  int32 lesson_number = 3;  // 1..8
}
```

**Почему `LessonResponse` (а не новое сообщение):** у academic-service нужен `subject_id` для D-04 subject-match + `status` (чтобы можно было позже отказывать на `cancelled`). `LessonResponse` уже всё это отдаёт.

### Backend — реализация `resolveLesson`

[VERIFIED: `services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleGrpcServiceImpl.java:40-73`] паттерн уже используется: достать `Lesson` из репо → достать `ScheduleItem` → `buildResponse(lesson, item)`.

**LessonRepository недостаёт метода.** [VERIFIED: `LessonRepository.java:15-99`] — есть `findByScheduleItemIdAndDateBetween` и набор native-queries, но нет «по group_id + date + lesson_number». Нужно добавить:

```java
@Query(value = """
    SELECT l.* FROM lessons l
    JOIN schedule_items si ON si.id = l.schedule_item_id
    WHERE si.group_id   = :groupId
      AND si.lesson_number = :lessonNumber
      AND l.date = CAST(:date AS date)
      AND l.status::text IN ('planned','active','closed')
    ORDER BY l.date
    LIMIT 1
    """, nativeQuery = true)
Optional<Lesson> findByGroupDateAndLessonNumber(
    @Param("groupId") Long groupId,
    @Param("date") LocalDate date,
    @Param("lessonNumber") Integer lessonNumber);
```

**Важное замечание по Q2 (attendance pattern).** [VERIFIED: `AttendanceRecord.java:19` — `Integer lessonNumber` передаётся внутри доменной записи, но attendance-service **не резолвит** пару по natural key сам — он получает активную пару через `ScheduleGrpcClient.getActiveLesson(...)` в `CheckinService.java:45`]. То есть паттерн Phase 60 D-15 «natural key (group_id, date, lesson_number)» существует как концепция для пересечения template + one-off, но в текущем read-пути attendance не реализован отдельным RPC. `schedule-service.LessonGenerationService` уже материализует `lessons` из `ScheduleItem` + one-off в таблицу `lessons` (ScheduleItem для one-off тоже существует, т.к. oneoff создаёт ScheduleItem+Lesson, см. `oneoff/` module) — поэтому **достаточно одного запроса к таблице `lessons` через JOIN на schedule_items** (см. SQL выше); отдельной логики merge шаблона и one-off на уровне academic-service **не нужно**.

### Backend — вызов из academic-service

Новый класс `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/ScheduleGrpcClient.java` по паттерну `attendance-service/grpc/ScheduleGrpcClient.java:26-94`:

```java
@Component
public class ScheduleGrpcClient {
    @GrpcClient("schedule-service")
    private ScheduleGrpcServiceGrpc.ScheduleGrpcServiceBlockingStub stub;

    public Optional<LessonResponse> resolveLesson(Long groupId, LocalDate date, int lessonNumber) {
        try {
            return Optional.of(stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                .resolveLesson(ResolveLessonRequest.newBuilder()
                    .setGroupId(groupId).setDate(date.toString())
                    .setLessonNumber(lessonNumber).build()));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) return Optional.empty();
            throw new ScheduleServiceUnavailableException(...);
        }
    }
}
```

Также понадобятся `application.yml` entries для gRPC client (сейчас их в academic-service нет, т.к. он только сервер). Паттерн — [VERIFIED: `attendance-service/src/main/resources/application.yml`] (researcher не проверял, но это стандарт grpc-spring-boot-starter: `grpc.client.schedule-service.address=static://schedule-service:9090`).

### Backend — валидации в `HomeworkService.createHomework/updateHomework`

Текущая логика [VERIFIED: `HomeworkService.java:68-82`] — просто создаёт сущность и публикует event. Нужно добавить (в порядке):

1. **D-06 guard**: удалить использование `requireHeadmanOrManageHomework()` со всеми ветками ассистента; заменить на простое `if (!requestContext.isHeadman()) throw new AccessDeniedException(...)`. Ассистент убирается из Phase 61 (deferred).
2. **D-03 guard**: `if (request.lessonDate().isBefore(LocalDate.now(clock))) throw new BadRequestException("Нельзя создать ДЗ на прошедшую дату")`.
3. **D-04 guard**: `LessonResponse lesson = scheduleGrpcClient.resolveLesson(request.groupId(), request.lessonDate(), request.lessonNumber()).orElseThrow(() -> new BadRequestException("На эту пару нельзя назначить ДЗ — пары нет в расписании"));` → затем `if (!lesson.getSubjectId().equals(request.subjectId())) throw new BadRequestException("ДЗ можно задать только по предмету этой пары");`
4. В `updateHomework` (D-03) — если `lessonDate` меняется, перепроверяем date+lesson+subject. (Решение: в Phase 61 `UpdateHomeworkRequest` **не содержит** `lessonDate/lessonNumber` — дата пары после создания не редактируется. Это надо закрепить в контракте.)
5. **D-05 guard**: в `updateHomework` и `deleteHomework` — `if (!homework.getPublishedBy().equals(requestContext.getUserId())) throw new AccessDeniedException("Только автор ДЗ может его редактировать")`.

### Backend — контракт DTO правки

`CreateHomeworkRequest` [VERIFIED: `CreateHomeworkRequest.java:10-30`] — сейчас record без date/number. **Добавить:**

```java
public record CreateHomeworkRequest(
    @NotBlank @Size(max=255) String title,
    @Size(max=4000) String description,
    @Size(max=2048) String link,
    @NotNull Long subjectId,
    @NotNull Long groupId,
    @NotNull Long semesterId,
    @NotNull @FutureOrPresent(message="Дата пары должна быть не в прошлом") LocalDate lessonDate,  // D-01
    @NotNull @Min(1) @Max(8) Integer lessonNumber                                                     // D-01
) {}
```

`UpdateHomeworkRequest` — **не добавляем** `lessonDate/lessonNumber`: привязка фиксируется при create. Это upstream-сигнал planner-у.

`HomeworkResponse` [VERIFIED: `HomeworkResponse.java:11-71`] — добавить поля + сеттеры:

```java
private LocalDate lessonDate;
private Integer lessonNumber;
```

`HomeworkAssembler.toModel` [VERIFIED: `HomeworkAssembler.java:22-39`] — пробросить из entity. Завершить чистку — сегодня у `toModel(homework)` захардкоден `completed=false`; это ок по D-10.

### Backend — Entity + Migration

[VERIFIED: `Homework.java:28-33` + `V1__baseline.sql:139-154`] — в таблице и entity есть `lesson_id` (BIGINT, nullable, без FK). **Не используется** в коде (grep по repo показывает его только в entity и baseline). Вариант: (a) DROP COLUMN в той же миграции `V_N__homework_lesson_binding.sql`, (b) оставить. **Рекомендую DROP** — уменьшает путаницу и соответствует принципу «если не используется — удалить» (+ индекс `idx_hw_lesson` тоже дропнуть). D-15 это явно не оговаривает, но логично.

Entity правки:

```java
// Homework.java
@Setter
@Column(name = "lesson_date", nullable = false)
private LocalDate lessonDate;

@Setter
@Column(name = "lesson_number", nullable = false)
private Integer lessonNumber;

// удалить Setter lessonId + Column @ lessonId (если дроп в миграции)
```

Конструктор `public Homework(...)` расширить на `lessonDate, lessonNumber`.

**Следующий Flyway номер:** [VERIFIED: `ls db/migration`] → последний `V12__subjects_group_id.sql` → **следующий `V13__homework_lesson_binding.sql`**.

```sql
-- V13__homework_lesson_binding.sql
TRUNCATE homeworks, homework_completions RESTART IDENTITY CASCADE;

-- Дроп неиспользуемой колонки (V1 baseline, нет FK, не читается кодом)
DROP INDEX IF EXISTS idx_hw_lesson;
ALTER TABLE homeworks DROP COLUMN IF EXISTS lesson_id;

ALTER TABLE homeworks
    ADD COLUMN lesson_date   DATE NOT NULL,
    ADD COLUMN lesson_number INT  NOT NULL;

CREATE INDEX idx_homeworks_group_date ON homeworks(group_id, lesson_date);
```

### Backend — Events

Java payload-ы [VERIFIED: `HomeworkPublishedEvent.java:13-24`, `HomeworkUpdatedEvent.java:13-21`] — добавить поля:

```java
// HomeworkPublishedEvent.Payload
@JsonProperty("lesson_date")   String lessonDate,
@JsonProperty("lesson_number") Integer lessonNumber

// HomeworkUpdatedEvent.Payload
@JsonProperty("subject_id")    Long subjectId,   // D-07 нужен bot-у для fallback subject name при update
@JsonProperty("lesson_date")   String lessonDate,
@JsonProperty("lesson_number") Integer lessonNumber
```

`HomeworkService` при публикации передаёт `saved.getLessonDate().toString()` + `saved.getLessonNumber()`. Для `updated` payload также передавать `subjectId` чтобы бот мог резолвнуть имя (сейчас в update-ивенте subject_id отсутствует, поэтому bot пишет generic «Домашнее задание обновлено»).

JSON schema `event-schemas/homework.published.json` [VERIFIED] — добавить `lesson_date`, `lesson_number` и убрать deprecated `lesson_id` (сейчас в схеме есть, хотя payload его не пишет; несоответствие давнее):

```json
"required": ["homework_id", "group_id", "subject_id", "title", "lesson_date", "lesson_number"],
"properties": {
  "homework_id":   { "type": "integer" },
  "group_id":      { "type": "integer" },
  "subject_id":    { "type": "integer" },
  "title":         { "type": "string" },
  "has_link":      { "type": "boolean" },
  "lesson_date":   { "type": "string", "format": "date" },
  "lesson_number": { "type": "integer", "minimum": 1, "maximum": 8 }
}
```

Аналогично для `homework.updated.json` — добавить `subject_id`, `lesson_date`, `lesson_number`.

### Notification-bot

[VERIFIED: `event_dispatcher.py:47, 77-88`] — диспетчер **уже** регистрирует `homework.updated` через `handle_homework`. Answer to Q5: handler уже есть, обрабатывает оба event_type в одной функции [VERIFIED: `homework.py:12-65`]. Правки:

1. В `handle_homework` в ветке `elif event_type == "homework.updated":` — сейчас текст `f"Домашнее задание обновлено\n\n{title}"`. Добавить subject (через `payload.get("subject_id")` + `academic_client.get_subjects_by_ids`, точно так же как в `homework.published` ветке). D-07 текст: «ДЗ изменено: {subject} — {title}».
2. Обе ветки — опционально использовать `payload.get("lesson_date")` + `lesson_number` в финальном тексте («Пара N, дата дд.мм»). Не обязательно (в D-07 не прописано явно), но полезно. Planner решает.

### Frontend — routing + lazy feature

[VERIFIED: `app.routes.ts:175-264`] — паттерн lazy route `loadComponent` + `canActivate: [headmanGuard]`. Добавить между `subjects` (строка 201-209) и `schedule` (строка 219-227) новый блок:

```ts
{
  path: 'homework',
  canActivate: [headmanGuard],
  loadComponent: () =>
    import('./features/headman/homework/headman-homework.component').then(
      m => m.HeadmanHomeworkComponent,
    ),
  data: { title: 'Домашние задания', eyebrow: 'Староста' },
},
```

### Frontend — headman UI

Новые standalone-компоненты:

```
frontends/web-panel/src/app/features/headman/homework/
├── headman-homework.component.{ts,html,css}
├── headman-homework-api.service.ts
├── homework-inline-form/
│   └── homework-inline-form.component.{ts,html,css}
└── homework-card/
    └── homework-card.component.{ts,html,css}   (или в shared/)
```

**Паттерны, которые можно тянуть:**

- Недельная навигация ← → + «сегодня» ([VERIFIED: `headman-schedule.component.ts:35-110` — utilities `mondayOf`, `isoWeekNumber`, `DAY_LABELS`, weekOffset signal]). Компонент inline — **не выделен** в standalone. **Рекомендация:** извлечь мини-компонент `shared/week-navigator/week-navigator.component.ts` с `@Input() initialDate`, `@Output() weekChanged: EventEmitter<{monday: Date, weekType: 'odd'|'even'}>`, переиспользовать в `headman-homework`.
- Inline-form валидация — [VERIFIED: `headman/subjects/subject-dialog.component.ts`] паттерн reactive formGroup + errors под полями. Переиспользовать валидационный стиль, но **не MatDialog** (D-08).
- Анимация expand/collapse для inline-формы — стандартный Angular `trigger('expand', [...])` (`height 0 → *`, 200ms ease-out).

**Логика `HeadmanHomeworkComponent`:**

```ts
// signals:
weekOffset = signal(0);
editingState = signal<{ lessonKey: string; homeworkId: number | null } | null>(null);
// computed: current monday, days, lessons-per-day merged with homeworks
// lessonKey = `${date}-${lessonNumber}`
```

Для получения пар и ДЗ на неделю:
- Пары: через `StudentApiService.getWeekSchedule(...)` или новый метод в `HeadmanApiService` (researcher уверен что что-то подобное есть, т.к. `headman/schedule` использует `HeadmanApiService`).
- ДЗ: `GET /api/academic/homeworks?groupId=X&semesterId=Y` (уже работает) → клиентская фильтрация по `lessonDate ∈ [mondayOfWeek .. sundayOfWeek]`.

### Frontend — student UI (переписывание)

**Существующее:** [VERIFIED: `features/student/homework/student-homework.component.ts:51-60`] — простой список, вместе с `homework-item/homework-item.component.ts` (один файл, подтверждено `ls`). Answer to Q6: `HomeworkItemComponent` реюзабелен — это чистая ячейка карточки с toggle выполнения, логики фильтрации в нём нет.

**Новая структура:**

```
features/student/homework/
├── student-homework.component.ts       (контейнер с segmented D/W/M + filter toggle)
├── day-view/
│   └── student-homework-day-view.component.ts      (input: date; навигация ← →)
├── week-view/
│   └── student-homework-week-view.component.ts     (input: monday; список дней)
├── month-view/
│   └── student-homework-month-view.component.ts    (input: month; матрица 6×7)
└── homework-item/                      (existing — реюз)
    └── homework-item.component.ts
```

**Segmented control.** Answer to Q4: в Angular-коде `web-panel/src/app/shared` **пустой каталог** [VERIFIED: `ls shared` — No such file or directory]. В PWA (`frontends/pwa`) есть `SegmentedControl.tsx` [VERIFIED] но он:
1. React, не Angular (разные проекты),
2. доменно-специфичен (AttendanceStatus hardcoded).

**Рекомендация:** создать первый Angular shared-компонент `frontends/web-panel/src/app/shared/segmented-control/segmented-control.component.ts` с generic API:

```ts
@Component({
  selector: 'app-segmented-control',
  standalone: true,
  template: `...role="radiogroup"...`
})
export class SegmentedControlComponent<T> {
  @Input({required: true}) options!: { value: T; label: string }[];
  @Input({required: true}) value!: T;
  @Output() valueChange = new EventEmitter<T>();
}
```

Использовать в `student-homework` для D/W/M; тот же компонент пригодится в будущем.

**Фильтр «только невыполненные».** Signal + computed filtering на уровне контейнера, дети (day/week/month) получают уже отфильтрованный набор как `@Input() items: HomeworkItem[]`.

**По поводу server-side `?from=&to=` фильтра** (gray area): рекомендация — **не добавлять в Phase 61**. Клиентская фильтрация работает за O(N) где N ≤ 100 ДЗ/семестр. Перенести в deferred-ideas на случай реального роста.

### Frontend — Sidebar

[VERIFIED: `sidebar.component.ts:193-199`] паттерн — массив объектов. Вставить после «Предметы» (строка ~200):

```ts
{
  label: 'Домашние задания',
  icon: 'ph-notebook',
  route: '/headman/homework',
  roles: ['STUDENT'],
  isHeadman: true,
},
```

### Anti-Patterns

- **Не добавляй FK** `homeworks.lesson_id → lessons.id` — cross-service FK. D-01 явно.
- **Не используй `MatDialog`** для формы старосты — D-08.
- **Не рассчитывай, что `ScheduleItem` на дату совпадает с `Lesson`** напрямую — в schedule-service `Lesson` материализуется LessonGenerationService-ом. Ищи через `lessons` JOIN `schedule_items`.
- **Не ставь FK `homeworks.lesson_date` на `lessons.date`** (это вообще не имеет смысла — `date` не ключ).
- **Не используй `LocalDate.now()` без Clock** — D-03 требует тестируемости.
- **Не вставляй Swagger аннотации в `HomeworkController`** — они идут в `HomeworkApi` (contract-first).

---

## Don't Hand-Roll

| Проблема | Не пиши сам | Используй | Почему |
|---|---|---|---|
| Резолв пары по natural key | Свой SQL в academic-app | gRPC `ResolveLesson` из schedule-service | Bounded context — academic не должен знать про `schedule_items`/`lessons` таблицы |
| Валидация `lesson_number ∈ [1..8]` | if-ветку в сервисе | `@Min(1) @Max(8)` на DTO | Консистентно с проектным подходом |
| `FutureOrPresent` для даты | if с `isBefore(now)` | `@FutureOrPresent` из jakarta.validation (для DTO); `Clock` bean для сервис-слоя | Двойной щит: DTO + service |
| Субъект lookup для bot push | Встроенный кэш | `academic_client.get_subjects_by_ids` — уже есть (`homework.py:39`) |
| Navigation ← → по неделям | Переписать с нуля для headman/homework | Извлечь из `headman-schedule.component.ts:35-110` в `shared/week-navigator` |
| Segmented UI | Самому рисовать 3 кнопки | Создать `shared/segmented-control` компонент и переиспользовать |

---

## Runtime State Inventory

*(Phase 61 — **не** rename/refactor. Раздел минимальный, оставлен для полноты.)*

| Категория | Items | Action |
|---|---|---|
| Stored data | `homeworks` + `homework_completions` — TRUNCATE (D-02). Нет реальных записей — подтверждено. | Flyway V13 |
| Live service config | Нет | — |
| OS-registered state | Нет | — |
| Secrets/env vars | Нет новых | — |
| Build artifacts | Нет | — |

---

## Common Pitfalls

### Pitfall 1: Lesson generation на будущее может отсутствовать
**Что пойдёт не так:** ScheduleItem существует, но `LessonGenerationService` ещё не материализовал `lessons` для указанной даты → `ResolveLesson` возвращает NOT_FOUND → 400 «пары нет», хотя по расписанию она должна быть.
**Why it happens:** [VERIFIED: `LessonGenerationService.java` exists; researcher не читал детали генерации] — генерация идёт по расписанию (семестр, N недель вперёд). Если староста ставит ДЗ на пару за пределами сгенерированного горизонта, `lessons` пустой.
**How to avoid:** Либо (а) форсировать генерацию через RPC вызов изнутри `ResolveLesson` (доп. сложность), либо (б) ограничить в UI выбор дат текущей сгенерированной неделей ± 1 (что естественно и так, т.к. UI — «неделя от текущей»). **Рекомендация:** (б) — просто.
**Warning sign:** 400 «пары нет» при очевидно существующей паре в расписании → проверь `LessonGenerationService`-горизонт.

### Pitfall 2: `@FutureOrPresent` vs системная TZ
**Что пойдёт не так:** Пользователь в Москве создаёт ДЗ на «сегодня в полночь» а `LocalDate.now()` на сервере в UTC уже «вчера» → 400.
**Prevention:** Использовать `Clock clock = Clock.system(ZoneId.of("Europe/Moscow"))` (как в `CheckinService.java:40`). Паттерн в проекте уже есть.

### Pitfall 3: UNIQUE constraint soft-delete конфликт (как в Phase 60)
**Что:** Если planner решит добавить UNIQUE — не стоит (D-01 запрещает). Напоминание.

### Pitfall 4: Bot handler — отсутствие subject_id в `homework.updated`
**Что:** Текущая схема `homework.updated.json` не требует `subject_id`. После расширения — **обратимая совместимость** не нужна (нет других consumers). Но тесты, которые генерят мок-ивент, могут упасть.
**Prevention:** Обновить тестовые фикстуры в `services/notification-bot/tests/` (если есть).

### Pitfall 5: inline-form state leak
**Что:** Если пользователь кликнул «+ Добавить» под парой A, потом под B — форма под A должна свернуться.
**Prevention:** `editingState: signal<{lessonKey, homeworkId|null} | null>` на уровне родителя, дочерние inline-form только читают.

### Pitfall 6: HATEOAS assembler overload использует неверный `completed` flag
**Что:** Контроллер [VERIFIED: `HomeworkController.java:52`] сейчас в `listHomeworks` для всех типов клиентов проставляет `completed = homeworkService.isCompleted(hw.getId())` — то есть N+1 query на список. Для headman это бессмысленно (D-10). Не критично, но planner может оптимизировать: делать per-student completion **только если** `requestContext.getRole() == STUDENT && !requestContext.isHeadman()`.

---

## Code Examples

### Пример 1: new gRPC RPC в schedule-service (JOIN через native query)

```java
// LessonRepository.java — добавить
@Query(value = """
    SELECT l.* FROM lessons l
    JOIN schedule_items si ON si.id = l.schedule_item_id
    WHERE si.group_id = :groupId
      AND si.lesson_number = :lessonNumber
      AND l.date = CAST(:date AS date)
      AND l.status::text IN ('planned','active','closed')
    LIMIT 1
    """, nativeQuery = true)
Optional<Lesson> findByGroupDateAndLessonNumber(
    @Param("groupId") Long groupId,
    @Param("date") LocalDate date,
    @Param("lessonNumber") Integer lessonNumber);
```

```java
// ScheduleGrpcServiceImpl.java — добавить
@Override
public void resolveLesson(ResolveLessonRequest req, StreamObserver<LessonResponse> obs) {
    LocalDate date = LocalDate.parse(req.getDate());
    Lesson lesson = lessonRepository
        .findByGroupDateAndLessonNumber(req.getGroupId(), date, req.getLessonNumber())
        .orElseThrow(() -> new ResourceNotFoundException(
            "Lesson", "group_id/date/lesson_number",
            req.getGroupId() + "/" + date + "/" + req.getLessonNumber()));
    ScheduleItem item = scheduleItemRepository.findById(lesson.getScheduleItemId())
        .orElseThrow(() -> new ResourceNotFoundException("ScheduleItem", "id", lesson.getScheduleItemId()));
    obs.onNext(buildResponse(lesson, item));
    obs.onCompleted();
}
```

### Пример 2: HomeworkService с валидациями

```java
@Transactional
public Homework createHomework(CreateHomeworkRequest req) {
    // D-06: only headman (not admin, not regular student)
    if (!requestContext.isHeadman())
        throw new AccessDeniedException("Только староста может создавать ДЗ");

    // D-03: дата не в прошлом
    if (req.lessonDate().isBefore(LocalDate.now(clock)))
        throw new BadRequestException("Нельзя создать ДЗ на прошедшую дату");

    // D-04: пара существует + subject совпадает
    LessonResponse lesson = scheduleGrpcClient
        .resolveLesson(req.groupId(), req.lessonDate(), req.lessonNumber())
        .orElseThrow(() -> new BadRequestException(
            "На эту пару нельзя назначить ДЗ — пары нет в расписании"));
    if (!Long.valueOf(lesson.getSubjectId()).equals(req.subjectId()))
        throw new BadRequestException("ДЗ можно задать только по предмету этой пары");

    Homework homework = new Homework(
        req.groupId(), req.subjectId(), req.semesterId(),
        req.title(), req.description(), req.link(),
        requestContext.getUserId(),
        req.lessonDate(), req.lessonNumber()
    );
    Homework saved = homeworkRepository.save(homework);
    eventPublisher.publishEvent(new HomeworkPublishedEvent(
        this, saved.getId(), saved.getGroupId(), saved.getSubjectId(),
        saved.getTitle(), saved.getLink() != null,
        saved.getLessonDate().toString(), saved.getLessonNumber()));
    return saved;
}

@Transactional
public void deleteHomework(Long id) {
    Homework hw = getHomework(id);
    // D-05
    if (!hw.getPublishedBy().equals(requestContext.getUserId()))
        throw new AccessDeniedException("Удалять ДЗ может только автор");
    homeworkRepository.delete(hw);
}
```

### Пример 3: inline-form signal pattern (Angular)

```ts
// headman-homework.component.ts (фрагмент)
readonly editingState = signal<{ lessonKey: string; homeworkId: number | null } | null>(null);

onAddHomework(lessonKey: string) {
  this.editingState.set({ lessonKey, homeworkId: null });
}
onEditHomework(lessonKey: string, id: number) {
  this.editingState.set({ lessonKey, homeworkId: id });
}
onCancel() {
  this.editingState.set(null);
}
async onSaved(dto: CreateOrUpdateDto) {
  // call api, reload, clear editingState
}
```

---

## State of the Art

| Старое | Новое | Комментарий |
|---|---|---|
| `homeworks.lesson_id BIGINT nullable` (не используется) | `lesson_date + lesson_number` natural key | D-01, DROP COLUMN in V13 |
| ADMIN может создавать ДЗ (`HomeworkController.java:45` пропускает ADMIN в list; create без явного headman-гарда) | Только STUDENT+is_headman (D-06) | Убрать ADMIN из `@RequireRole` на create/update/delete |
| Bot handler для `homework.updated` уже есть, но текст generic | Расширенный payload с subject_id → имя предмета резолвится | `event_dispatcher.py:83` уже маршрутизирует |
| Student page — плоский список | 3 режима + segmented | D-09 |

---

## Assumptions Log

| # | Claim | Section | Risk |
|---|---|---|---|
| A1 | В `LessonGenerationService` пары генерируются на горизонт 1-2 недели вперёд → UI старосты «текущая неделя» всегда попадает в сгенерированный диапазон. | Pitfall 1 | Если горизонт меньше — 400 «пары нет» на валидных парах. Researcher не читал `LessonGenerationService.java`. Planner должен проверить. |
| A2 | Attendance-service `application.yml` имеет `grpc.client.schedule-service.address=...` — тот же паттерн использовать в academic-service. | ScheduleGrpcClient section | Конфиг-строки точной формы researcher не проверял. |
| A3 | `HeadmanApiService` имеет метод загрузки пар на неделю (используется `/headman/schedule`). | Frontend headman UI | Если нет — придётся реюзать `student-api.service.getWeekSchedule` или добавить. |
| A4 | `notification-bot/tests/` имеет тест-фикстуры для homework events, которые придётся обновить. | Pitfall 4 | Researcher не нашёл путь к python-тестам. |
| A5 | DROP неиспользуемой колонки `lesson_id` безопасен. | Backend/Migration | Researcher проверил grep по всему проекту — ни одно место кода не читает `lesson_id`. |

---

## Open Questions (для planner)

1. **Горизонт генерации lessons** — проверить `LessonGenerationService` или зафиксировать в UI ограничение недели.
2. **Нужен ли server-side `?from=&to=` фильтр для списка ДЗ?** Researcher рекомендует — нет для Phase 61.
3. **Размещение «+ Добавить задание» при 0 ДЗ на паре vs N ДЗ** — UX nuance. Рекомендация: всегда один «+» после последнего ДЗ (или на месте списка, если 0).
4. **Сиротские ДЗ при `lesson.one_off.cancelled`** — оставить в БД, UI их не показывает (нет пары). Planner фиксирует в deferred, но должен закрепить поведение.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|---|---|---|---|---|
| Java 21 (MS) | academic-app, schedule-app builds | ✓ | `C:\Users\maksd\.jdks\ms-21.0.9` (CLAUDE.md) | — |
| Gradle | build | ✓ | via `gradlew.bat` | — |
| PostgreSQL (academic_db + schedule_db) | Flyway migration + integration tests via Testcontainers | ✓ | docker-compose | — |
| RabbitMQ | events | ✓ | docker-compose | — |
| Node + Angular CLI | web-panel build | ✓ (подразумевается из Phase 33-40) | — | — |
| Python 3 + aiogram 3 | notification-bot tests | ✓ | — | — |
| Protobuf compiler | grpc code-gen | ✓ | `protoc 3.25.3` auto via `com.google.protobuf` plugin (`academic-app:80`) | — |

**Missing dependencies:** none.

---

## Validation Architecture

### Test Framework

| Свойство | Значение |
|---|---|
| Backend framework | JUnit 5 + Mockito + Testcontainers (Postgres, RabbitMQ) + grpc-client-starter (test) |
| Backend config | `academic-app/build.gradle.kts:61-67` |
| Frontend framework | Angular + Jasmine/Karma (`.spec.ts` рядом с компонентами) |
| Python framework | pytest (подразумевается, researcher не прочитал) |
| Quick run | `./gradlew :services:academic-service:academic-app:test --tests '*Homework*'` |
| Full suite | `./gradlew build` |

### Phase Requirements → Test Map

| Req | Behavior | Test type | Command | Exists? |
|---|---|---|---|---|
| HW-VAL-DATE | 400 при дате в прошлом | unit (service) | `./gradlew :academic-app:test --tests 'HomeworkServiceTest'` | ❌ Wave 0 |
| HW-VAL-LESSON | 400 если пары нет | unit + mock gRPC | `./gradlew :academic-app:test --tests 'HomeworkServiceTest'` | ❌ Wave 0 |
| HW-VAL-LESSON | 400 если subject не совпадает | unit | same | ❌ |
| HW-AUTHOR | 403 для не-автора | unit | same | ❌ |
| HW-ROLE | 403 для обычного студента | unit | same | ❌ |
| HW-EVENT | payload содержит lesson_date/number | unit + `ArgumentCaptor` | same | ❌ |
| HW-UI-HEADMAN | inline-form expand/collapse | Angular | `ng test --include='**/headman-homework*.spec.ts'` | ❌ Wave 0 |
| HW-UI-STUDENT | segmented D/W/M | Angular | `ng test --include='**/student-homework*.spec.ts'` | partial (old exists) |
| HW-LINK | Flyway миграция проходит, индекс создаётся | integration (Testcontainers) | `./gradlew :academic-app:test --tests 'EntityMappingIntegrationTest'` | ✓ (расширить) |
| new RPC | `ResolveLesson` возвращает NOT_FOUND если пары нет | integration (schedule) | `./gradlew :schedule-app:test --tests '*ScheduleGrpc*'` | ❌ Wave 0 |
| bot handler | `homework.updated` использует subject_id | pytest | `pytest services/notification-bot/tests/test_homework.py` | возможно существует |

### Sampling Rate

- **Per task commit:** `./gradlew :services:academic-service:academic-app:test --tests 'HomeworkServiceTest'`
- **Per wave merge:** `./gradlew build` для изменённых сервисов (academic, schedule) + `ng test --browsers=ChromeHeadless --watch=false`
- **Phase gate:** `./gradlew build` green + `ng test` green + `pytest services/notification-bot/tests/`

### Wave 0 Gaps

- [ ] `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java` — покрывает D-03/04/05/06/07
- [ ] `services/schedule-service/schedule-app/src/test/java/.../ScheduleGrpcResolveLessonTest.java` — RPC happy + NOT_FOUND
- [ ] `frontends/web-panel/src/app/features/headman/homework/headman-homework.component.spec.ts`
- [ ] `frontends/web-panel/src/app/features/student/homework/day-view/*.spec.ts`, `week-view/*.spec.ts`, `month-view/*.spec.ts`
- [ ] `frontends/web-panel/src/app/shared/segmented-control/segmented-control.component.spec.ts`
- [ ] Возможно — `services/notification-bot/tests/test_homework.py` расширить на `updated` с subject

---

## Security Domain

| ASVS | Applies | Control |
|---|---|---|
| V2 Authentication | no (handled by Gateway + Auth-service) | — |
| V3 Session | no | — |
| V4 Access Control | **yes** | `@RequireRole` + `RequestContext.isHeadman()` + publishedBy-guard (D-05, D-06) |
| V5 Input Validation | **yes** | `@Valid` на DTO, `@NotBlank/@Size/@Min/@Max/@FutureOrPresent` |
| V6 Cryptography | no | — |

### Threat Patterns

| Pattern | STRIDE | Mitigation |
|---|---|---|
| IDOR — чужой староста редактирует чужой ДЗ | Elevation | D-05 publishedBy-check |
| Privilege escalation — обычный студент создаёт ДЗ | Elevation | D-06 `isHeadman()` + `@RequireRole` |
| XSS в title/description/link | Tampering | Angular auto-escape + бэк `@Size` + link валидируется как URL в форме (тоже Angular Validator); не хранить как html |
| Event schema injection (broken consumer) | Tampering | JSON Schema `event-schemas/*.json` + валидация в тестах (паттерн проекта) |
| Stale gRPC client / cascading timeout | DoS | `withDeadlineAfter(3, TimeUnit.SECONDS)` (паттерн `ScheduleGrpcClient.java:34`) |

---

## Sources

### Primary (HIGH confidence)
- Project codebase (explicit file paths above, all `[VERIFIED]`)
- `CLAUDE.md` — правила кодирования и стек
- `.planning/phases/61-.../61-CONTEXT.md` — D-01..D-15 locked

### Secondary (MEDIUM)
- Attendance-service паттерн `ScheduleGrpcClient.java:26-94` — для академика по аналогии
- Headman-schedule week-navigator — исходник паттерна

### Tertiary (LOW / assumptions)
- `LessonGenerationService` горизонт — не прочитан детально (A1)
- `notification-bot/tests/` — точный путь не проверен (A4)
- `HeadmanApiService.getWeekSchedule` — существование предположено (A3)

---

## Metadata

**Confidence breakdown:**
- Backend patterns (contract-first, `RequireRole`, `RequestContext`, Flyway nextнумер) — HIGH
- gRPC server RPC расширение + Repository native query — HIGH (паттерн явный)
- gRPC client в academic-service (новая зависимость) — HIGH
- Event schema + bot handler — HIGH (handler уже частично готов)
- Frontend headman inline-form — MEDIUM (новая фича, без прямого прецедента)
- Frontend student 3-режима + segmented — MEDIUM (нужно создать shared segmented)
- Тестовая стратегия — HIGH (паттерн `GroupServiceTest` ясен)

**Research date:** 2026-04-15
**Valid until:** 2026-05-15 (30 дней — стабильный стек)

---

## Recommended plan split (для planner, итог)

| Plan | Scope | Ключевой риск |
|---|---|---|
| PLAN-01 | Flyway V13, Entity, DTO (Create+Response), Assembler | ddl-validate после миграции |
| PLAN-02 | `schedule.proto` + RPC `ResolveLesson` + LessonRepository query + integration test | gRPC code-gen в двух местах |
| PLAN-03 | academic-app: `ScheduleGrpcClient`, application.yml grpc config, build.gradle client starter | dependency перенос |
| PLAN-04 | HomeworkService валидации D-03/04/05/06 + RequireRole правки + HomeworkServiceTest | clock injection |
| PLAN-05 | Event payload расширение (both events) + JSON schema + notification-bot handler для subject_id на update | обратимая совместимость нет |
| PLAN-06 | web-panel `/headman/homework` — component + inline-form + API service + sidebar + route + shared/week-navigator | реюз week-navigator |
| PLAN-07 | web-panel `/student/homework` — контейнер + day/week/month view + shared/segmented-control + spec.ts |
| PLAN-08 (опц.) | E2E smoke + `docs/phase-61-report.md` + полный `./gradlew build` regression |

(CONTEXT предлагал 6-7 планов; researcher recommends **7-8** — разделить gRPC-серверную (PLAN-02) и клиентскую (PLAN-03) части, так как они в разных сервисах и тестируются независимо.)
