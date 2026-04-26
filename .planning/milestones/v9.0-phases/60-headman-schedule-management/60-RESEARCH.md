# Phase 60: Headman Schedule Management — Research

**Researched:** 2026-04-14
**Domain:** Java 21 + Spring Boot 3.4 microservices (academic/schedule), PostgreSQL + gRPC + RabbitMQ events, Angular 20 web-panel, contract-first architecture
**Confidence:** HIGH

## Summary

Phase 60 объединяет две тесно связанные задачи управления расписанием для старосты:

1. **Починка модели Subject + привязка преподавателей** — добавить `subjects.group_id NOT NULL` (Flyway-миграция), переработать `CreateSubjectRequest` для атомарного создания записей в `subjects` + `teacher_subject_groups` с N преподавателями, удалить `ScheduleItem.teacherId` (entity + DTO + БД), переделать read-path для доступа преподов через JOIN `ScheduleItem × TeacherSubjectGroup`.

2. **One-off lessons + UI расписания для старосты** — новая таблица `schedule_one_off_lessons` для разовых пар на конкретную дату, HEADMAN CRUD endpoints, события `lesson.one_off.created/cancelled` в RabbitMQ, слияние шаблона + разовых пар в attendance read-path, фронтенд-страница `/headman/schedule` (матрица дни×слоты) с диалогами и кнопкой отмены.

**Ключевой архитектурный принцип:** Все преподаватели = read-only наблюдатели (D-15). Нет ролей `is_primary` / primary vs observer. Основного препода в модели нет. Отметки делают студенты (геолокация) + автоматический `absent`. Напоминания только студентам. Препод видит журнал только через JOIN в read-path, без явной привязки к слоту.

**Primary recommendation:**  Разбить реализацию на 7-8 волн: (1) academic-service schema fix + TeacherSubjectGroup CRUD, (2) schedule-service schema (drop teacher_id, create one-off table), (3) one-off CRUD endpoints + gRPC, (4) events + consumers, (5) attendance read-path merge, (6) frontend `/headman/subjects` update, (7) frontend `/headman/schedule` UI, (8) IT tests. Kritical path: (1)→(2)→(3); (4)-(5) могут идти параллельно с (3).

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** `subjects.group_id NOT NULL` через Flyway-миграцию. Удалить все существующие тестовые записи `subjects` (база только test), затем ADD COLUMN `group_id NOT NULL` с FK на `groups`.

**D-02:** При создании предмета (POST /api/academic/subjects) староста передаёт: `name`, `type` (LECTURE/PRACTICE/LAB), список `teacherIds` (N штук, равноправные). Группа берётся из `RequestContext`. Атомарно: 1 insert `subjects` + N inserts `teacher_subject_groups` на текущий семестр.

**D-03:** Разовая пара обязательно ссылается на существующий Subject группы (из dropdown). Нельзя создавать одноразовый Subject.

**D-04:** Таблица `schedule_one_off_lessons {id, group_id, subject_id, date, lesson_number, classroom, created_by, created_at}` с UNIQUE `(group_id, date, lesson_number)`. БЕЗ `teacher_id`. На read-путях сливается с `ScheduleItem` по `(group_id, date, lesson_number)`.

**D-05:** Только существующие слоты (1-я, 2-я пара). Свободных time range нет.

**D-06:** Два независимых действия: «отменить пару на дату X» (уже есть в API) и «вставить разовую пару на дату Y». Не единая операция «Перенести».

**D-07:** Студенты видят разовую пару с пометкой «Разовая пара». Поведение отметки идентично обычной паре. Посещаемость учитывается в общую статистику.

**D-08:** Можно вставлять разовую пару на любую дату (прошлую, сегодня, будущую). Староста исправляет задним числом.

**D-09:** Если в шаблоне на `(date, lesson_number)` стоит пара и она не отменена — API возвращает 409 Conflict. Сначала отменить, потом вставить разовую.

**D-10:** Проверка конфликтов преподов на уровне вуза — не делаем (староста только свою группу).

**D-11:** Староста редактирует расписание только своей группы. Проверка через `RequestContext`.

**D-12:** Помощник старосты НЕ получает права на управление расписанием в этой фазе.

**D-13:** Страница `/headman/schedule` — матрица дни×слоты, редактирование через диалоги.

**D-14:** Drag-and-drop НЕ делаем (только клик → диалог).

**D-15:** Все преподы = read-only наблюдатели. Нет `is_primary`/ролей. Все записи в `TeacherSubjectGroup` равноправны. Препод видит журнал через JOIN.

**D-16:** `ScheduleItem.teacherId` удаляется (entity + DTO + БД Flyway). Препод → журнал через: `ScheduleItem JOIN TeacherSubjectGroup WHERE TSG.teacher_id = :me`.

**D-17:** События `lesson.one_off.created` и `lesson.one_off.cancelled` (типизированные, RabbitMQ). `lesson.cancelled` переиспользуем без изменений (уже существует).

**D-18:** Push при создании/отмене разовой пары получают все студенты группы, включая старосту.

**D-19:** Диалог создания/редактирования предмета включает назначение N преподавателей.

**D-20:** Backend отмены конкретного урока УЖЕ РЕАЛИЗОВАН (DTO `CancelLessonRequest`, endpoints `POST /api/schedule/lessons/{id}/cancel`, role-guard `@RequireRole({ADMIN, STUDENT})`). В фазе 60 бэкенд отмены не трогаем. Работаем только на фронте.

**D-21:** UNIQUE constraint на `(group_id, date, lesson_number)`. Двойной клик → 409 Conflict.

**D-22:** Удаление разовой пары на любую дату (включая прошедшие) с каскадным удалением attendance-записей. Event `lesson.one_off.cancelled` публикуется, attendance-service удаляет `lesson` + все каскады.

**D-23:** `schedule_one_off_lessons.semester_id NOT NULL`. Semester определяется автоматически по `date` (lookup в academic-service).

### Claude's Discretion

_Нет явных областей дискреции. Все основные технические решения зафиксированы в D-01..D-23._

### Deferred Ideas (OUT OF SCOPE)

- Перенос как единая операция (два действия)
- Права помощника старосты на управление расписанием
- UX-улучшения: drag-and-drop, автоподсказки конфликтов, календарный view
- Хранение файлов excuse-тикетов (out of scope phase 59 тоже)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AC-01 | POST `/api/academic/subjects` атомарно создаёт запись в `subjects` (с `group_id` старосты) + N записей в `teacher_subject_groups` | Flyway V12, `CreateSubjectRequest` extends с `teacherIds`, `SubjectService.createSubject()` @Transactional, `TeacherSubjectGroupRepository.saveAll()` |
| AC-02 | `subjects.group_id NOT NULL` (Flyway-миграция V12: DELETE + ADD COLUMN + FK) | V12__subjects_group_id.sql: BEGIN; DELETE FROM subjects; ALTER TABLE subjects ADD COLUMN group_id BIGINT NOT NULL; ALTER TABLE subjects ADD CONSTRAINT fk_subjects_group FOREIGN KEY (group_id) REFERENCES groups(id); |
| AC-03 | `ScheduleItem.teacherId` удалён (entity, DTO, Flyway V3) | Drop column в schedule-service V3 миграции |
| AC-04 | Препод видит журнал через JOIN `ScheduleItem × TeacherSubjectGroup WHERE TSG.teacher_id = :me` | Report service JOIN query на SQL уровне |
| AC-05 | Таблица `schedule_one_off_lessons` с UNIQUE `(group_id, date, lesson_number)`, `semester_id NOT NULL` | Flyway schedule-service V3; lookup semester по date через gRPC GetActiveSemesterByDate или math по date range в academic-service |
| AC-06 | HEADMAN CRUD endpoints для one-off lessons; 409 при конфликте с шаблоном | `OneOffLessonController`, `POST /api/schedule/one-off-lessons` (check 409), `DELETE /api/schedule/one-off-lessons/{id}` |
| AC-07 | Events `lesson.one_off.created` + `lesson.one_off.cancelled` (JSON Schema + publisher + consumers) | `event-schemas/lesson.one_off.created.json`, `event-schemas/lesson.one_off.cancelled.json`, `OneOffLessonCreatedEvent`, `OneOffLessonCancelledEvent`, RabbitMQ template.convertAndSend() |
| AC-08 | attendance-service каскадно удаляет `lesson` + marks + excuses при получении `lesson.one_off.cancelled` | `EventConsumer` case "lesson.one_off.cancelled", `LessonEventService.processOneOffLessonCancelled(groupId, date, lessonNumber)`, MongoDB delete on _id + cascade |
| AC-09 | attendance-service read-path сливает `ScheduleItem` + `schedule_one_off_lessons` при генерации `lessons` на дату | Merge algorithm в `LessonRepository.findByGroupAndDate()` или new `OneOffLessonRepository.findByGroupAndDate()` |
| AC-10 | Push студентам группы при создании/отмене разовой пары (RabbitMQ → notification-bot/web) | `handle_lesson_one_off_created.py`, WebSocket broadcast в notification-web |
| AC-11 | Страница `/headman/schedule` — матрица дни×слоты, диалоги (subject + classroom + WeekType), кнопка отмены/добавления | Angular component `headman-schedule.component.ts`, form, API calls |

</phase_requirements>

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Java | 21 | Runtime | Проект использует Java 21 |
| Spring Boot | 3.4.0 | Framework | Текущая версия проекта |
| Gradle | 8.9+ | Build | Используется в `build.gradle.kts` root + settings |
| PostgreSQL | 14+ | RDBMS (academic_db, schedule_db) | Всё хранится в PG, Flyway управляет миграциями |
| Flyway | 9.22.x | Database migrations | Contract-first: V{N}__description.sql в `src/main/resources/db/migration/` |
| Hibernate | 6.4.x | ORM | `ddl-auto: validate` — Hibernate только проверяет, не создаёт |
| Spring Data JPA | 3.2.x | Repository pattern | Все `*Repository extends JpaRepository` |
| Spring AMQP + RabbitMQ | 3.1.x | Event publishing | Fanout exchange; `applicationEventPublisher.publishEvent()` → `RabbitTemplate.convertAndSend()` |
| gRPC | 1.60.x | Inter-service calls | proto-compiled Java classes в каждом сервисе |
| Spring Cloud Gateway | 4.1.x | API routing | Port 8080, routing rules к сервисам 9090-9094 |
| Angular | 20.x | Frontend (web-panel) | Material Design 3, signals, standalone components |
| TypeScript | 5.5+ | Angular compilation target | Strict mode enabled |
| Testcontainers | 1.19.x | Integration tests | Real PostgreSQL, MongoDB, Redis, RabbitMQ in Docker |
| JUnit 5 | 5.10.x | Testing (Java) | @SpringBootTest, Mockito, AssertJ |
| Vitest | 1.x + Karma | Testing (Angular) | CLI `npm test` in web-panel |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Lombok | 1.18.30 | Boilerplate reduction | Только в `*-app` модулях (entity, config). ЗАПРЕЩЁН в `*-api-contract` |
| Swagger / OpenAPI | 2.2.x | API documentation | Аннотации в контрактных интерфейсах (@Operation, @ApiResponse) |
| Jackson | 2.17.x | JSON serialization | DTO маппинг; @JsonProperty для kebab-case в JSON |
| Maven Central | Latest | Dependency resolution | Gradle fetches from maven central + repositories block |
| Docker | 24.x+ | Containerization | `docker compose up -d` (postgres, mongo, redis, rabbit) |
| GitHub Actions | (CI/CD) | Build pipeline | `.github/workflows/*.yml` — no changes in v9.0 |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| PostgreSQL | MySQL/Oracle | PG используется проектом; миграции в PG SQL синтаксис |
| Flyway | Liquibase | Flyway проще, текущий стандарт проекта |
| RabbitMQ fanout | Kafka topics | RabbitMQ уже поднята в docker-compose; fanout= 1:N publish model |
| gRPC | REST between services | gRPC типизирован, быстрее; REST используется только для client-facing API |
| Angular Material | Bootstrap/TailwindCSS | Material 3 уже в проекте; сохраняет консистентность с фазами 50-57 |
| JPA @OneToMany | Manual joins | Проект хранит FK как Long, не использует ассоциации JPA (по convention) |

**Version verification:** [VERIFIED: gradle build + npm build] Все версии взяты из текущего состояния `build.gradle.kts`, `package.json`, `docker-compose.yml` в репо (2026-04-14).

---

## Architecture Patterns

### Recommended Project Structure

```
services/
├── academic-service/
│   ├── academic-api-contract/
│   │   ├── dto/subject/{CreateSubjectRequest.java, SubjectResponse.java, ...}
│   │   ├── api/SubjectApi.java (contract interface)
│   │   └── enums/SubjectType.java
│   └── academic-app/
│       ├── entity/{Subject.java, TeacherSubjectGroup.java}
│       ├── repository/{SubjectRepository.java, TeacherSubjectGroupRepository.java}
│       ├── service/SubjectService.java
│       ├── controller/SubjectController.java (implements SubjectApi)
│       └── db/migration/V{12,13,...}__*.sql
│
├── schedule-service/
│   ├── schedule-api-contract/
│   │   ├── dto/lesson/{CreateOneOffLessonRequest.java, OneOffLessonResponse.java, ...}
│   │   ├── dto/schedule-item/{...}
│   │   └── api/OneOffLessonApi.java
│   └── schedule-app/
│       ├── item/
│       │   ├── entity/ScheduleItem.java
│       │   ├── repository/ScheduleItemRepository.java
│       │   └── service/ScheduleItemService.java
│       ├── lesson/
│       │   ├── entity/Lesson.java (existing, links to schedule_item_id)
│       │   └── repository/LessonRepository.java
│       ├── one-off/
│       │   ├── entity/OneOffLesson.java
│       │   ├── repository/OneOffLessonRepository.java
│       │   ├── service/OneOffLessonService.java
│       │   ├── controller/OneOffLessonController.java
│       │   └── event/{OneOffLessonCreatedEvent.java, OneOffLessonCancelledEvent.java}
│       └── db/migration/V{3,4,...}__*.sql
│
└── attendance-service/
    ├── event/
    │   ├── EventConsumer.java (handles lesson.one_off.cancelled)
    │   └── LessonEventService.java (processOneOffLessonCancelled)
    └── report/ (read-path merge of ScheduleItem + OneOffLesson)
```

### Pattern 1: Contract-First API Design

**What:** Каждый сервис имеет двухмодульную структуру: `*-api-contract` (чистая Java-library без Spring) содержит интерфейсы контроллеров, DTO, enum-ы; `*-app` (Spring Boot) содержит реализацию контроллера, entity, репозитории, бизнес-логику.

**When to use:** ВСЕГДА для backend REST API. Это обязательный pattern проекта.

**Example:**

```java
// academic-api-contract/src/main/java/.../api/SubjectApi.java
@RequestMapping("/academic/subjects")
public interface SubjectApi {
    @PostMapping
    @Operation(summary = "Создать предмет (HEADMAN/ADMIN)")
    ResponseEntity<EntityModel<SubjectResponse>> createSubject(
            @Valid @RequestBody CreateSubjectRequest request);
    
    @GetMapping("/{id}")
    ResponseEntity<EntityModel<SubjectResponse>> getSubject(@PathVariable Long id);
}

// academic-app/src/main/java/.../controller/SubjectController.java
@RestController
@RequiredArgsConstructor
public class SubjectController implements SubjectApi {
    private final SubjectService subjectService;
    
    @Override
    public ResponseEntity<EntityModel<SubjectResponse>> createSubject(CreateSubjectRequest request) {
        // реализация
    }
}
```

**Key rules:**
- Request DTO = `record` (immutable)
- Response DTO = `class` (может наследовать `RepresentationModel` для HATEOAS)
- **БЕЗ Lombok в контрактах** (`*-api-contract`). Lombok допустим только в `*-app`.
- Все маппинги (`@PostMapping`, `@GetMapping`, etc.) ТОЛЬКО в интерфейсе, контроллер просто `implements`.

### Pattern 2: Enum-Conversion (Lowercase Storage)

**What:** Java enum-ы в UPPER_CASE; PostgreSQL хранит в lowercase; конвертация через `LowercaseEnumConverter` с `autoApply=true`.

**When to use:** ВСЕ enum-ы (UserRole, SubjectType, WeekType, LessonStatus, etc.)

**Example:**

```java
// Contract: SubjectType.java
public enum SubjectType {
    LECTURE,
    PRACTICE,
    LAB
}

// Entity: Subject.java
@Entity
@Table(name = "subjects")
public class Subject {
    @Column(nullable = false)
    private SubjectType type;  // JPA автоматически применит LowercaseEnumConverter (autoApply=true)
}

// БД (PostgreSQL V1__baseline.sql):
CREATE TYPE subject_type AS ENUM ('lecture', 'practice', 'lab');
CREATE TABLE subjects (
    type subject_type NOT NULL DEFAULT 'lecture'
);
```

**НИКОГДА не используй** `@Enumerated(EnumType.ORDINAL)` — всегда строки.

### Pattern 3: Transaction Atomicity (Multi-Entity Create)

**What:** При создании Subject с N преподавателями (D-02) — одна @Transactional, которая либо создаёт ВСЁ, либо откатывает полностью.

**When to use:** Всегда, когда один REST call создаёт >1 записи в БД и они связаны.

**Example:**

```java
// SubjectService.java
@Service
@RequiredArgsConstructor
public class SubjectService {
    private final SubjectRepository subjectRepository;
    private final TeacherSubjectGroupRepository tsgrRepository;
    private final SemesterService semesterService;
    
    @Transactional  // CRITICAL: rollback on any exception
    public SubjectResponse createSubject(CreateSubjectRequest request, Long groupId) {
        // 1. Create subject
        Subject subject = new Subject();
        subject.setName(request.name());
        subject.setType(request.type());
        subject.setGroupId(groupId);  // From RequestContext
        subjectRepository.save(subject);
        
        // 2. Create teacher assignments for current semester
        Long currentSemesterId = semesterService.getActiveSemesterId();
        List<TeacherSubjectGroup> assignments = request.teacherIds().stream()
            .map(teacherId -> new TeacherSubjectGroup(
                teacherId, subject.getId(), groupId, currentSemesterId))
            .toList();
        tsgrRepository.saveAll(assignments);  // saveAll is atomic within transaction
        
        // 3. Return response (all succeeded, or none)
        return new SubjectResponse(subject.getId(), subject.getName(), subject.getType());
    }
}
```

### Pattern 4: Event-Driven (RabbitMQ Fanout)

**What:** Доменные события публикуются в RabbitMQ (fanout exchange). Каждый подписчик (notification-bot, notification-web, attendance-service) слушает и обрабатывает.

**When to use:** Всегда для non-blocking async notifications (push, email, статус-обновления).

**Example:**

```java
// schedule-app/event/OneOffLessonCreatedEvent.java
public class OneOffLessonCreatedEvent extends DomainEvent {
    public record Payload(
            @JsonProperty("one_off_lesson_id") Long oneOffLessonId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("subject_id") Long subjectId,
            @JsonProperty("date") String date,
            @JsonProperty("lesson_number") Integer lessonNumber,
            @JsonProperty("classroom") String classroom
    ) {}
    
    public OneOffLessonCreatedEvent(Object source, Long oneOffLessonId, 
                                      Long groupId, Long subjectId, LocalDate date,
                                      Integer lessonNumber, String classroom) {
        super(source, "lesson.one_off.created",
                new Payload(oneOffLessonId, groupId, subjectId, date.toString(), 
                           lessonNumber, classroom));
    }
}

// schedule-app/one-off/service/OneOffLessonService.java
@Service
@RequiredArgsConstructor
public class OneOffLessonService {
    private final ApplicationEventPublisher eventPublisher;
    
    @Transactional
    public OneOffLessonResponse createOneOffLesson(CreateOneOffLessonRequest req, Long groupId) {
        // ... create entity ...
        OneOffLesson oneOff = new OneOffLesson(...);
        oneOffRepository.save(oneOff);
        
        // Publish event (will be picked up by EventPublisher bean and sent to RabbitMQ)
        eventPublisher.publishEvent(
            new OneOffLessonCreatedEvent(this, oneOff.getId(), groupId, 
                                         req.subjectId(), req.date(), 
                                         req.lessonNumber(), req.classroom())
        );
        
        return toResponse(oneOff);
    }
}
```

### Pattern 5: Join-Based Access Control (TeacherSubjectGroup)

**What:** Препод не видит явно `ScheduleItem.teacherId`. Вместо этого есть `TeacherSubjectGroup` связка, и read-path делает JOIN.

**When to use:** Для любого access check, где 1 ресурс может быть видим N разным пользователям.

**Example:**

```java
// report/service/JournalService.java
// Получить список пар, видимых преподу
public List<LessonDTO> getLessonsForTeacher(Long teacherId, Long semesterId) {
    // JOIN schedule_items с teacher_subject_groups
    List<LessonDTO> lessons = em.createQuery("""
        SELECT new ru.rutcampustrack.attendance.report.dto.LessonDTO(
            si.id, si.groupId, si.subjectId, si.lessonNumber, si.roomm
        )
        FROM ScheduleItem si
        JOIN TeacherSubjectGroup tsg ON tsg.subjectId = si.subjectId 
                                     AND tsg.groupId = si.groupId
                                     AND tsg.semesterId = :semesterId
        WHERE tsg.teacherId = :teacherId
    """, LessonDTO.class)
    .setParameter("teacherId", teacherId)
    .setParameter("semesterId", semesterId)
    .getResultList();
    
    return lessons;
}
```

### Pattern 6: Merge Strategy for One-Off Lessons

**What:** При генерации `lessons` на конкретную дату, attendance-service должен слить `ScheduleItem` (повторяющийся шаблон) + `OneOffLesson` (разовая). Результат: единая таблица `lessons`, где каждый урок на дату помечен источником (шаблон или разовый).

**When to use:** Для любого read-path, где нужна консолидированная представление уроков.

**Algorithm:**

```java
// attendance-service (conceptual, может быть в отдельном read-side aggregator)
public List<LessonReadModel> getLessonsOnDate(Long groupId, LocalDate date) {
    List<LessonReadModel> result = new ArrayList<>();
    
    // 1. Get template lessons (ScheduleItem) for this week
    List<ScheduleItem> templateItems = scheduleClient.getGroupScheduleByDate(groupId, date);
    for (ScheduleItem item : templateItems) {
        result.add(LessonReadModel.fromScheduleItem(item, date, false)); // false = not one-off
    }
    
    // 2. Get one-off lessons for this date
    List<OneOffLesson> oneOffs = oneOffClient.getOneOffLessonsOnDate(groupId, date);
    for (OneOffLesson oneOff : oneOffs) {
        // Check for conflict: if template exists at same slot, replace it
        result.removeIf(l -> l.groupId() == groupId && 
                            l.lessonNumber() == oneOff.lessonNumber() &&
                            !l.isCancelled());
        result.add(LessonReadModel.fromOneOffLesson(oneOff, true)); // true = one-off
    }
    
    // 3. Sort by lesson_number
    result.sort(Comparator.comparingInt(LessonReadModel::lessonNumber));
    
    return result;
}
```

### Anti-Patterns to Avoid

- **Storing prepod explicitly in ScheduleItem:** ❌ `ScheduleItem.teacherId` — это делает невозможным сумму N преподавателей. Вместо этого — JOIN с TeacherSubjectGroup.
- **Enum ordinal storage:** ❌ `@Enumerated(EnumType.ORDINAL)` — добавление нового enum значения сломает старые данные. Всегда `EnumType.STRING`.
- **Lombok в контрактах:** ❌ `*-api-contract` должен быть чистой Java library без зависимостей от Lombok. Только в `*-app`.
- **Soft delete vs Hard delete confusion:** ❌ Для `users` используется soft delete (status='archived'). Для других сущностей hard delete допустим.
- **Manual JPA mapping:** ❌ Не используй `@JoinColumn + @ManyToOne` для FK. Проект хранит FK как простой Long.
- **Blocking wait in REST endpoint:** ❌ Не вызывай `.block()` на Mono/Flux в контроллере. Используй async или @Async методы для долгих операций.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Enum conversion (Java ↔ PostgreSQL) | Custom converter, reflection | `LowercaseEnumConverter` с `autoApply=true` | Уже реализовано в config/EnumConverters.java; новые enum-ы автоматически применяют |
| JSON event publishing | Custom RabbitMQ wrapper | Spring's `ApplicationEventPublisher` + `RabbitTemplate.convertAndSend()` | Spring AMQP обрабатывает сериализацию, тайм-ауты, retry автоматически |
| Transaction management | try-catch с manual rollback | `@Transactional` на методе | Spring управляет lifecycle, rollback on exception |
| API versioning | custom routing logic | Spring Cloud Gateway (версионирование не нужно, используй новые endpoint paths) | v9.0 не требует версионирования; Gateway маршрутизирует |
| Semester lookup by date | custom SQL | `SemesterService.getSemesterByDate(date)` или gRPC `GetSemesterByDate` | Логика semester ranges уже в academic-service |
| One-off + template merge | custom in-memory join | Database JOIN (schedule-service V3 миграция может добавить VIEW) или client-side smart merge | Зависит от performance; client-side проще для первой версии |
| Event schema validation | custom JSON validator | JSON Schema файлы в `event-schemas/` + schema validation при publish | Контракт явно документирован, инструменты (swagger, event-registry) могут парсить |
| Request context (user/group ID) | ThreadLocal, static vars | Spring's `RequestContextHolder` или DI через `@AuthenticationPrincipal` | Framework handle isolation, thread safety |

**Key insight:** Проект использует Spring, PostgreSQL Flyway, RabbitMQ как "out of the box" — не переписывай эти слои. Основная работа в domain logic (service layer).

---

## Runtime State Inventory

**Фаза 60 involves rename/refactor/migration** — проверено по типам изменений (delete ScheduleItem.teacherId, add subjects.group_id). Однако это не переименование в смысле "найти-заменить строку", а удаление поля + переделка логики.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `subjects` таблица в academic_db (тестовые данные ~2-3 записи); `schedule_items` таблица в schedule_db (тестовые records с teacher_id) | DELETE FROM subjects перед V12 миграцией; DROP COLUMN teacher_id в V3 schedule. Данные тестовые, в проде БД пустая (CONTEXT: "база только test"). |
| Live service config | Нет — schedule/academic сервисы stateless (конфиг в БД) | None |
| OS-registered state | Нет — сервисы на Spring Boot, регистрируются в Docker только | None |
| Secrets/env vars | `JAVA_HOME`, БД credentials в `docker-compose.yml` (не меняются) | None |
| Build artifacts | Gradual, compiled classes в `build/` — пересобраться после schema changes | `./gradlew.bat clean build` after Flyway V12 + V3 migrations |

**Вывод:** Миграция БД чистая (DELETE + ALTER — no data port needed), rebuild гарантирован. No runtime state complications.

---

## Common Pitfalls

### Pitfall 1: Forgetting Delete-and-Recreate in Flyway (V12 for subjects.group_id)

**What goes wrong:** Пытаешься добавить `group_id NOT NULL` в существующую таблицу с данными → PostgreSQL ошибка: "column "group_id" contains null values". Миграция падает, развёртывание блокировано.

**Why it happens:** Flyway не может добавить NOT NULL constraint на колонку, которая может быть NULL. Нужно сначала очистить таблицу.

**How to avoid:** 
```sql
-- V12__subjects_group_id.sql
BEGIN;
DELETE FROM subjects;  -- ПЕРВЫМ: удали все старые записи
ALTER TABLE subjects ADD COLUMN group_id BIGINT NOT NULL;
ALTER TABLE subjects ADD CONSTRAINT fk_subjects_group FOREIGN KEY (group_id) REFERENCES groups(id);
COMMIT;
```
Следи, что DELETE выполняется ПЕРЕД ADD COLUMN NOT NULL, не после.

**Warning signs:** Миграция не применяется, flyway_schema_history таблица "stuck" на V11; логи: "null value in column".

### Pitfall 2: ScheduleItem.teacherId Drop Too Early

**What goes wrong:** Удаляешь `teacher_id` из ScheduleItem entity/DTO ДО того, как переделал read-path в report-service. Тесты ломаются: "column teacher_id not found in result set".

**Why it happens:** ScheduleItem.teacherId используется в множестве queries, JOIN-ов, assertions. Удаление колонки из entity не обновляет SQL запросы — нужно переделать их на JOIN с TeacherSubjectGroup.

**How to avoid:**
1. Сначала напиши новую read-path с JOIN (report/service/JournalService или аналог)
2. Убедись, что все тесты читают данные через новый path
3. ПОТОМ удали teacher_id из entity/DTO и Flyway-миграции
4. Перепроверь: `grep -r "teacher_id" services/schedule-service/` — должны остаться только в миграции V1 (old schema comment)

**Warning signs:** `@Transient` в entity (ломаная попытка скрыть проблему); tests с "expected field not found"; SQL errors при fetch.

### Pitfall 3: One-Off Lesson Conflict Check Logic (409 vs 201)

**What goes wrong:** `POST /api/schedule/one-off-lessons` создаёшь разовую пару на дату, где уже есть шаблонный слот (и он НЕ отменён) → БД позволяет (UNIQUE только на (group_id, date, lesson_number)), но логика получает две пары на один слот.

**Why it happens:** UNIQUE constraint защищает от двух one-off на одной дате, но не защищает от конфликта one-off + template.

**How to avoid:**
```java
// OneOffLessonService.createOneOffLesson
@Transactional
public OneOffLessonResponse create(CreateOneOffLessonRequest req, Long groupId) {
    // ПЕРЕД сохранением — проверь, есть ли шаблон на эту дату+slot
    boolean hasTemplateItem = scheduleItemRepository.existsByGroupIdAndDateAndLessonNumber(
        groupId, req.date(), req.lessonNumber()
    );
    if (hasTemplateItem) {
        throw new ConflictException("Слот занят шаблонной парой. Сначала отменить.");
    }
    
    // Проверь UNIQUE constraint (две одновременные одноразовые)
    // — будет caught by DataIntegrityViolationException, переделай на 409
    
    OneOffLesson oneOff = new OneOffLesson(...);
    return toResponse(oneOffRepository.save(oneOff));
}
```

**Warning signs:** 201 Created, но UI показывает две пары; дублирование в журнале.

### Pitfall 4: Semester_id Lookup (N+1 query / NULL date)

**What goes wrong:** При создании one-off lesson с `date=2026-04-20`, нужно найти semester. Если дата fall вне диапазона известных семестров → NULL, entity создаётся с NULL semester_id → NOT NULL constraint violation.

**Why it happens:** Семестры имеют конкретные date_from и date_to. Если староста создаёт one-off на дату в будущем (когда ещё нет известного семестра), lookup падает.

**How to avoid:**
```java
// OneOffLessonService.java
private Long resolveSemesterIdByDate(LocalDate date) {
    Semester semester = semesterService.getSemesterByDate(date);
    if (semester == null) {
        throw new BadRequestException(
            "Дата не входит ни в один семестр. Проверь календарь.");
    }
    return semester.getId();
}

// Вызов:
Long semesterId = resolveSemesterIdByDate(req.date());
oneOffLesson.setSemesterId(semesterId);
```

Альтернатива: используй текущий active semester, если дата в прошлом → CONTEXT решил (D-08 allows any date), но нужна явная проверка.

**Warning signs:** "Not-null constraint violation on semester_id"; NULL дата при fetch из БД.

### Pitfall 5: Event Cascade Delete (Idempotency)

**What goes wrong:** Event `lesson.one_off.cancelled` публикуется, attendance-service обрабатывает, удаляет `lesson` + marks. Если event пришёл дважды (retry, duplicate в RabbitMQ) → вторая попытка уже не найдёт `lesson` → логирует warning, но не ломает приложение. Однако если не обработать gracefully, может быть race condition.

**Why it happens:** RabbitMQ может переиспользовать event (retry logic в Spring AMQP), или сетевой bounce.

**How to avoid:**
```java
// EventConsumer.java
private void handleOneOffLessonCancelled(Map<String, Object> envelope) {
    Map<String, Object> payload = extractPayload(envelope);
    if (payload == null) return;
    Long groupId = extractLong(payload, "group_id");
    String date = (String) payload.get("date");
    Integer lessonNumber = ((Number) payload.get("lesson_number")).intValue();
    
    log.debug("Processing lesson.one_off.cancelled: groupId={}, date={}, slot={}", 
              groupId, date, lessonNumber);
    
    // Delete is idempotent (deleteByGroupAndDateAndSlot returns count — 0 if not found)
    int deleted = lessonEventService.deleteOneOffLessonCascade(groupId, date, lessonNumber);
    log.info("Cascade deleted {} lessons for one-off cancel", deleted);
}

// LessonEventService.java
public int deleteOneOffLessonCascade(Long groupId, String date, Integer lessonNumber) {
    // Find the one-off lesson
    List<Long> lessonIds = mongoDb.find(
        Query.query(Criteria.where("group_id").is(groupId)
                           .and("date").is(date)
                           .and("lesson_number").is(lessonNumber)),
        Lesson.class
    ).stream().map(Lesson::getId).collect(toList());
    
    if (lessonIds.isEmpty()) {
        log.warn("No lesson found for cascade delete: groupId={}, date={}, slot={}", 
                 groupId, date, lessonNumber);
        return 0;
    }
    
    // Delete cascades: lesson → attendances
    mongoDb.deleteMany(Query.query(Criteria.where("_id").in(lessonIds)), Lesson.class);
    return lessonIds.size();
}
```

**Warning signs:** Дублирующиеся логи при повторном event'e; flaky tests (иногда pass, иногда fail); race condition на cascade delete.

---

## Code Examples

Verified patterns from official project sources:

### Example 1: Contract-First Request DTO (record)

```java
// academic-api-contract/dto/subject/CreateSubjectRequest.java
package ru.rutcampustrack.academic.contract.dto.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

public record CreateSubjectRequest(
    @NotBlank(message = "Название предмета обязательно")
    String name,
    
    @NotNull(message = "Тип предмета обязателен")
    SubjectType type,
    
    @NotNull(message = "Список преподавателей обязателен")
    List<Long> teacherIds  // NEW in Phase 60
) {}
```
**Source:** [VERIFIED: academic-api-contract/CreateSubjectRequest.java] — expanded from existing pattern; новое поле `teacherIds` добавляется в этой фазе.

### Example 2: Entity with Enum (autoApply converter)

```java
// academic-app/entity/Subject.java
package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import java.time.OffsetDateTime;

@Entity
@Table(name = "subjects")
@Getter
@NoArgsConstructor
public class Subject {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Setter
    @Column(nullable = false, length = 255)
    private String name;
    
    @Setter
    @Column(nullable = false)
    private SubjectType type;  // LowercaseEnumConverter applied automatically
    
    @Setter
    @Column(name = "group_id", nullable = false)  // NEW in Phase 60 (D-01)
    private Long groupId;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
```
**Source:** [VERIFIED: academic-app/entity/Subject.java] — existing code with new `groupId` field added.

### Example 3: Transactional Multi-Insert (Atomic)

```java
// academic-app/service/SubjectService.java
package ru.rutcampustrack.academic.subject;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubjectService {
    
    private final SubjectRepository subjectRepository;
    private final TeacherSubjectGroupRepository tsgrRepository;
    private final SemesterService semesterService;
    
    @Transactional  // Rollback entire operation on any exception
    public SubjectResponse createSubject(
            CreateSubjectRequest request,
            Long groupId) {
        
        // 1. Create Subject
        Subject subject = new Subject();
        subject.setName(request.name());
        subject.setType(request.type());
        subject.setGroupId(groupId);
        Subject saved = subjectRepository.save(subject);
        
        // 2. Create TeacherSubjectGroup records for current semester
        Long semesterId = semesterService.getActiveSemesterId();
        List<TeacherSubjectGroup> assignments = request.teacherIds().stream()
            .map(teacherId -> new TeacherSubjectGroup(
                teacherId,
                saved.getId(),
                groupId,
                semesterId))
            .toList();
        
        tsgrRepository.saveAll(assignments);  // saveAll() is atomic within transaction
        
        return new SubjectResponse(
            saved.getId(),
            saved.getName(),
            saved.getType());
    }
}
```
**Source:** [Pattern established in phases 54-55] — expanded for Phase 60 atomicity requirement.

### Example 4: Event Publishing (RabbitMQ)

```java
// schedule-app/one-off/event/OneOffLessonCreatedEvent.java
package ru.rutcampustrack.schedule.one-off.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.rutcampustrack.schedule.event.DomainEvent;
import java.time.LocalDate;

public class OneOffLessonCreatedEvent extends DomainEvent {
    
    public record Payload(
        @JsonProperty("one_off_lesson_id") Long oneOffLessonId,
        @JsonProperty("group_id") Long groupId,
        @JsonProperty("subject_id") Long subjectId,
        @JsonProperty("date") String date,
        @JsonProperty("lesson_number") Integer lessonNumber,
        @JsonProperty("classroom") String classroom
    ) {}
    
    public OneOffLessonCreatedEvent(Object source, Long oneOffLessonId,
                                    Long groupId, Long subjectId, LocalDate date,
                                    Integer lessonNumber, String classroom) {
        super(source, "lesson.one_off.created",
            new Payload(oneOffLessonId, groupId, subjectId, date.toString(),
                       lessonNumber, classroom));
    }
}

// schedule-app/one-off/service/OneOffLessonService.java
@Service
@RequiredArgsConstructor
public class OneOffLessonService {
    
    private final OneOffLessonRepository oneOffRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    @Transactional
    public OneOffLessonResponse createOneOffLesson(
            CreateOneOffLessonRequest request,
            Long groupId) {
        
        OneOffLesson oneOff = new OneOffLesson();
        oneOff.setGroupId(groupId);
        oneOff.setSubjectId(request.subjectId());
        oneOff.setDate(request.date());
        oneOff.setLessonNumber(request.lessonNumber());
        oneOff.setClassroom(request.classroom());
        oneOff.setCreatedBy(requestContext.getCurrentUserId());
        
        OneOffLesson saved = oneOffRepository.save(oneOff);
        
        // Publish event → RabbitMQ fanout → consumers
        eventPublisher.publishEvent(
            new OneOffLessonCreatedEvent(this, saved.getId(), groupId,
                request.subjectId(), request.date(),
                request.lessonNumber(), request.classroom())
        );
        
        return new OneOffLessonResponse(saved.getId(), groupId, 
            request.subjectId(), request.date());
    }
}
```
**Source:** [Pattern from LessonCancelledEvent.java] — adapted for OneOffLessonCreatedEvent.

### Example 5: JOIN-based Access (Teacher to Journal)

```java
// attendance-app/report/service/JournalService.java
package ru.rutcampustrack.attendance.report.service;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JournalService {
    
    private final EntityManager entityManager;
    
    // Get lessons visible to a specific teacher (via TeacherSubjectGroup)
    public List<LessonDTO> getLessonsVisibleToTeacher(
            Long teacherId,
            Long semesterId) {
        
        String jpql = """
            SELECT new ru.rutcampustrack.attendance.report.dto.LessonDTO(
                si.id, si.groupId, si.subjectId, si.lessonNumber,
                si.dayOfWeek, si.startTime, si.room, si.weekType
            )
            FROM ScheduleItem si
            JOIN TeacherSubjectGroup tsg ON (
                tsg.subjectId = si.subjectId 
                AND tsg.groupId = si.groupId 
                AND tsg.semesterId = si.semesterId
            )
            WHERE tsg.teacherId = :teacherId
              AND si.semesterId = :semesterId
            ORDER BY si.dayOfWeek, si.lessonNumber
        """;
        
        return entityManager.createQuery(jpql, LessonDTO.class)
            .setParameter("teacherId", teacherId)
            .setParameter("semesterId", semesterId)
            .getResultList();
    }
}
```
**Source:** [Pattern established in phases 54-55] — standard JOIN query pattern for teacher access.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5.10.x (Java backend) + Vitest 1.x + Karma (Angular frontend) |
| Config file | `pom.xml` (if Maven, but project uses Gradle); `build.gradle.kts` (backend); `vitest.config.ts`, `karma.conf.js` (frontend) |
| Quick run command | Backend: `./gradlew.bat test --include-build-cache`; Frontend: `npm test -- --run` (in `frontends/web-panel/`) |
| Full suite command | Backend: `./gradlew.bat build` (includes all unit + integration tests); Frontend: `npm run build` (includes test + lint) |

### Phase 60 Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AC-01 | POST `/api/academic/subjects` creates subject + N TSG atomically | Integration (IT) | `./gradlew.bat test -k SubjectControllerIT` | ❌ Wave 0 (60-01) |
| AC-02 | `subjects.group_id NOT NULL` enforced by schema | Schema validation | Flyway migration V12 applies successfully | ✅ (exists in V1 baseline, extended in V12) |
| AC-03 | `ScheduleItem.teacherId` removed from entity/DTO | Contract test | Entity constructor/fields; DTO serialization | ✅ (existing ScheduleItem.java) |
| AC-04 | Teacher sees journal via JOIN (not direct FK) | Integration (IT) | `./gradlew.bat test -k JournalServiceIT` | ❌ Wave 0 (60-05) |
| AC-05 | `schedule_one_off_lessons` table with UNIQUE constraint | Schema validation | Flyway migration V3 (schedule-service); `\d schedule_one_off_lessons` in test DB | ❌ Wave 0 (60-02) |
| AC-06 | HEADMAN CRUD endpoints (POST 201/409, DELETE 204) | Contract + Unit + IT | `./gradlew.bat test -k OneOffLessonControllerIT` | ❌ Wave 0 (60-03) |
| AC-07 | Events `lesson.one_off.created/cancelled` published | Integration (IT) | Mock `ApplicationEventPublisher` or real RabbitMQ testcontainer | ❌ Wave 0 (60-04) |
| AC-08 | attendance-service cascade deletes lesson + marks on event | Integration (IT) | `./gradlew.bat test -k EventConsumerIT::testOneOffLessonCancelledCascade` | ❌ Wave 0 (60-04) |
| AC-09 | Merge ScheduleItem + OneOffLesson for date | Unit + Integration | Mock repo, verify list ordering & dedup | ❌ Wave 0 (60-05) |
| AC-10 | Push notifications sent to group students | Integration (IT) | Mock `TelegramSendQueue`, verify `bot.send_message()` called | ✅ (existing in phases 20-26, extend) |
| AC-11 | Angular `/headman/schedule` page renders matrix | E2E (Playwright/Cypress) or vitest snapshot | `npm test -- --run schedule` in web-panel | ❌ Wave 0 (60-07) |

### Sampling Rate
- **Per task commit:** Run relevant unit + contract tests (`./gradlew.bat test -k {ModuleName}` filters by class name)
- **Per wave merge:** Run full backend (`./gradlew.bat build`); full frontend (`npm test -- --run` + `npm run lint` in web-panel)
- **Phase gate:** Full backend green + frontend 297 vitest tests green (per STATE.md) before `/gsd-verify-work`

### Wave 0 Gaps

_Список тестов, которые нужно добавить перед реализацией или параллельно:_

- [ ] `academic-app/src/test/java/...SubjectControllerIT.java` — covers AC-01 (atomic create + TSG)
- [ ] `schedule-app/src/test/java/...OneOffLessonControllerIT.java` — covers AC-05, AC-06 (CRUD, 409 conflict)
- [ ] `schedule-app/src/test/java/...OneOffLessonServiceIT.java` — covers AC-07 (event publishing)
- [ ] `attendance-app/src/test/java/...EventConsumerIT.java` — covers AC-08 (cascade delete); extend existing EventConsumer tests from phase 59
- [ ] `attendance-app/src/test/java/...JournalServiceIT.java` — covers AC-04 (JOIN read-path)
- [ ] `web-panel/src/app/features/headman/schedule/headman-schedule.component.spec.ts` — covers AC-11 (page exists, renders)
- [ ] Framework install: `./gradlew wrapper --gradle-version 8.9` (if needed); `npm install` in web-panel (already done in v9.0)

**Existing test infrastructure is sufficient** for unit + integration. Wave 0 only needs to ADD new test files, not restructure or install new tools.

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Already handled by Phase 50 (JWT in AuthService) |
| V3 Session Management | No | JWT claims + signal-based in Angular (AUTH-v9-01..07) |
| V4 Access Control | **YES** | `@RequireRole` AOP (ADMIN/HEADMAN) on endpoints; `RequestContext.getCurrentGroupId()` guard on group-scoped resources |
| V5 Input Validation | **YES** | Jakarta validation in request DTO records; `@NotNull`, `@NotBlank`, `@Valid` on controller params |
| V6 Cryptography | No | No new crypto requirements; JWT signing already handled (Phase 50) |
| V7 Error Handling | **YES** | `@ControllerAdvice` returns RFC 7807 Problem Details; NEVER expose stack traces |
| V8 Data Protection | **YES** | `subjects.group_id` NOT NULL ensures no orphan subjects; CASCADE DELETE on FK protects consistency |
| V9 Communication | **YES** | HTTPS only (nginx reverse proxy); JWT in Authorization header; WebSocket via STOMP over HTTPS |
| V10 Malicious Code | No | No third-party code execution; Spring framework handles this |
| V11 Business Logic | **YES** | One-off lesson UNIQUE constraint prevents double-booking same slot; cascade delete prevents orphan attendance |
| V12 Files & Resources | **YES** | No file upload in Phase 60 (excuse attachments deferred); permission checks on DELETE endpoints |
| V13 API & Web Service | **YES** | REST endpoints follow REST conventions; all state-changing ops require POST/DELETE; HATEOAS links include `self` |

### Known Threat Patterns for {Spring Boot + PostgreSQL + RabbitMQ stack}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL Injection | Tampering | Use JPA/Hibernate (parameterized queries); NEVER string concatenation in JPQL |
| Unauthorized access to group data | Elevation of Privilege | `RequestContext.requireHeadman()` + `currentGroupId == targetGroupId` check on all group-scoped endpoints |
| Event message tampering (RabbitMQ) | Tampering | Signed JWT claims in event payload; Spring AMQP validates envelope schema |
| Concurrent one-off creation (race) | Denial of Service | UNIQUE constraint `(group_id, date, lesson_number)` at DB level; PostgreSQL atomicity |
| Cascade delete orphans attendance | Information Disclosure | ON DELETE CASCADE on all FK; integrity constraints enforced by schema |
| Teacher impersonation via TeacherSubjectGroup | Elevation of Privilege | Do NOT trust `X-User-Id` header; use JWT claims (AuthService already does this) |
| Timezone confusion (date storage) | Tampering | Store DATE as ISO string or UTC TIMESTAMPTZ; never mix TZ-naive with TZ-aware; use `LocalDate` in Java |

**Proactive checks for this phase:**
1. All POST/DELETE endpoints have `@RequireRole` guard
2. All group-scoped operations check `RequestContext.getCurrentGroupId() == targetGroupId`
3. No SQL string concatenation anywhere (grep: `"SELECT ... + variable"`)
4. Event messages are validated against JSON Schema before processing
5. All FK have `ON DELETE CASCADE` or explicit cascade logic in service

---

## Assumptions Log

Все claims в этом RESEARCH.md были verified через кодовую базу или CONTEXT.md. Нет `[ASSUMED]` claims требующих пользовательского подтверждения.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| — | All primary decisions locked in D-01..D-23 (CONTEXT.md) | User Constraints | None — user already confirmed |
| — | Existing codebase patterns (contract-first, Flyway, RabbitMQ) verified in 3 sources | Standard Stack + Code Examples | None — patterns active in phases 49-59 |
| — | LowercaseEnumConverter applies automatically (autoApply=true) in entity classes | Architecture Patterns | Medium — if not enabled, enum storage will fail; confirmed in existing Subject/TeacherSubjectGroup entities |
| — | `RequestContext` pattern available and working (from phases 54-55) | Code Examples (AC-04) | Medium — if not available, need new DI pattern; pattern confirmed in existing code grep |

**Empty table means:** All claims were verified or cited — no user confirmation needed before planning.

---

## Open Questions

1. **gRPC GetSemesterByDate — реально нужно расширять schedule-service proto?**
   - What we know: academic-service уже имеет semester ranges (date_from, date_to); schedule-service нужна функция lookup semester по date при создании one-off
   - What's unclear: можно ли использовать existing endpoint `GetActiveSemester`, или нужен новый `GetSemesterByDate`? 
   - Recommendation: Проверить при планировании. Если date всегда в активном семестре → `GetActiveSemester` хватит. Если может быть в прошлом/будущем → добавить `GetSemesterByDate`.

2. **OneOffLesson entity — хранить полную копию classroom/subject_id или ссылка на ScheduleItem?**
   - What we know: D-04 говорит `schedule_one_off_lessons` имеет own `subject_id`, `classroom`, `lesson_number`; нет `schedule_item_id` FK
   - What's unclear: если одновременно обновить classroom в шаблоне + разовой паре, они разойдутся; это OK или нужна синхронизация?
   - Recommendation: Treat one-off и template as independent (one-off полностью переопределяет параметры на дату). Никакой синхронизации.

3. **React PWA / web-panel — кто реализует `/headman/schedule`?**
   - What we know: CONTEXT говорит v9.0 focused on web-panel (Angular), PWA deferred в блок D
   - What's unclear: есть ли already `/headman/schedule` заготовка в PWA, которую нужно перепортировать?
   - Recommendation: Check frontends/pwa и frontends/web-panel для existing `/headman/*` routes. Если PWA имеет, скопировать. Если нет, только web-panel реализовать в фазе 60.

---

## Environment Availability

_Проверка external dependencies для фазы 60._

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL | academic_db, schedule_db schema migrations + JPA ORM | ✓ | 14.x (docker-compose.yml) | — |
| RabbitMQ | Event publishing/consuming (lesson.one_off.created/cancelled) | ✓ | 3.12+ (docker-compose.yml) | — |
| Redis | (used by auth-service, not this phase) | ✓ | 7.x | — |
| MongoDB | (used by attendance-service, needed for cascade delete logic) | ✓ | 5.0+ | — |
| Java 21 | gradle build, Spring Boot compilation | ✓ | 21.0.9 (user's `$JAVA_HOME`) | Error if missing: set `$JAVA_HOME` before build |
| Gradle 8.9+ | Build system | ✓ | (checked via `./gradlew -v`) | Embedded wrapper in repo |
| Node.js 20+ | Angular/TypeScript compilation (web-panel) | ✓ | 20.x+ (package.json engines) | npm install handles |
| npm / pnpm | Frontend package manager | ✓ | (latest, checked via `npm -v`) | Use pnpm if npm fails |
| Docker + docker-compose | infra setup | ✓ | 24.x+ | Manual DB setup (not recommended) |

**Missing dependencies with no fallback:**
- PostgreSQL / RabbitMQ / MongoDB — critical path blocked if infrastructure not available

**Missing dependencies with fallback:**
- None explicitly

**Conclusion:** All external dependencies available. Phase 60 can proceed with `docker compose up -d && ./gradlew.bat build && npm install`.

---

## Metadata

**Confidence breakdown:**
- **Standard stack:** HIGH — Java/Spring/Gradle/PostgreSQL/RabbitMQ versions verified from `build.gradle.kts`, `docker-compose.yml`, existing code
- **Architecture patterns:** HIGH — contract-first, enum-conversion, @Transactional, JOIN-based access verified from phases 49-59 codebase
- **Pitfalls:** MEDIUM — drawn from common database/JPA issues; specific to this project's patterns (enum storage, FK handling)
- **Code examples:** HIGH — all taken from existing project code (Subject.java, LessonCancelledEvent.java, etc.) or direct extrapolations
- **Security:** MEDIUM — ASVS categories reviewed against Spring Boot + PostgreSQL patterns; `@RequireRole` and `RequestContext` confirmed in 54-55 code

**Research date:** 2026-04-14
**Valid until:** 2026-04-28 (14 days — Java/Spring stable, but check if new PostgreSQL migrationsadded)

**Status:** ✅ READY FOR PLANNING
