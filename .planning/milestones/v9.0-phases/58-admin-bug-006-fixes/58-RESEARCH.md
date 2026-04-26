# Phase 58: Admin Functionality — BUG-006 Fixes — Research

**Researched:** 2026-04-14
**Domain:** Spring Boot 3.4 (Java 21) + Angular 20 — admin CRUD, JPA Specifications, Flyway migrations, scheduled jobs, RFC 7807 problem-details
**Confidence:** HIGH (все ключевые точки кода и схемы проверены при разведке в рамках сессии багфиксов 2026-04-14)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

См. секцию `<decisions>` в `58-CONTEXT.md` — D-01..D-32 зафиксированы пользователем.

Ключевые жёсткие ограничения:
- В БД пока только тестовая группа → миграцию `code → name` можно делать без сохранения данных.
- `initial_password` в БД остаётся (см. RETROSPECTIVE из Фазы B багфиксов).
- Telegram ID обязателен только для STUDENT (TEACHER/ADMIN остаются как есть).
- Curriculum levels: бакалавриат 4 курса, магистратура 2 курса, аспирантура 4 курса.
- Курс выпуска → группа архивируется автоматически.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

### Functional

**FR-1 (поиск)**: ADMIN на `/admin/users` вводит часть ФИО, логина или Telegram ID — система возвращает только подходящие записи (case-insensitive). Поиск работает в комбинации с фильтрами role/status.

**FR-2 (создание)**: ADMIN создаёт STUDENT с уже занятым Telegram ID — получает понятное сообщение «Telegram ID уже используется учётной записью X» (HTTP 409). Тоже для логина / employee_number / email. Generic 500 не должен возвращаться при unique-нарушениях.

**FR-3 (telegram required)**: ADMIN не может создать STUDENT без Telegram ID (валидация на backend и frontend). При `role=teacher` или `admin` поле остаётся опциональным.

**FR-4 (init password в списке)**: ADMIN на `/admin/users` видит для каждого пользователя начальный пароль в отдельной колонке, пока пользователь его не сменил. После смены — колонка для этой строки пуста. Кнопка «Скопировать».

**FR-5 (одно поле группы)**: Создание/редактирование группы — только одно поле «Название» формата `XXXx-NNN`. Backend хранит одно поле `name` (UNIQUE).

**FR-6 (автопромоция)**: За 2 недели до начала осеннего семестра все активные группы автоматически переходят на следующий курс. Группы выпускных курсов архивируются. Идемпотентно (повторный запуск не дублирует). Admin может триггернуть руками через `POST /api/academic/groups/promote`.

**FR-7 (валидация семестров)**:
- Нельзя создать семестр с `dateFrom` в прошлом.
- Нельзя создать семестр, пересекающийся датами с любым существующим (любого статуса).
- Нельзя редактировать семестр, у которого `dateTo < today` (завершён).

### Non-functional

**NFR-1 (производительность поиска)**: запрос с фильтром по 8000 пользователям возвращается за ≤300 мс p95.

**NFR-2 (миграция без даунтайма)**: V8/V9/V10 не используют долгие блокировки — `ALTER TABLE ADD COLUMN`, `DROP COLUMN`, `ADD CONSTRAINT` для маленьких таблиц допустимы.

**NFR-3 (атомарность)**: GroupPromotion в одной транзакции. Если падает на любой группе — rollback всех изменений.

**NFR-4 (security)**: новые эндпоинты `/groups/promote` и `/users?search=...` подчиняются существующим `@RequireRole({ADMIN})`.

### Acceptance criteria

- [ ] AC-1: backend возвращает 409 + ProblemDetail `{ "field": "telegram_id", "value": "..." }` при дубле уникального поля.
- [ ] AC-2: `users?search=иван` отдаёт только пользователей где login или ФИО содержат «иван» (case-insensitive).
- [ ] AC-3: frontend dialog показывает разные сообщения для каждого типа конфликта.
- [ ] AC-4: создание STUDENT без telegramId → 400 «Telegram ID обязателен для студента».
- [ ] AC-5: `users-page` показывает колонку «Начальный пароль» с кнопкой копирования, скрывается если все пусты.
- [ ] AC-6: группа имеет одно поле name, поле code удалено из контракта/entity/UI/тестов.
- [ ] AC-7: `POST /groups/promote` (admin) возвращает summary `{ promoted: N, archived: M }`.
- [ ] AC-8: scheduled job переводит тестовую группу `ИВТ11-001` → `ИВТ21-001` корректно (тест с заглушкой даты).
- [ ] AC-9: создание семестра в прошлом → 400. Пересечение → 409. Edit завершённого → 409.
- [ ] AC-10: миграции V8/V9/V10 проходят на пустой БД и на тест-сидах.

</phase_requirements>

---

<artifact_inventory>
## Existing Artefacts

### Backend (academic-service)

**Entity**:
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/User.java` — поля все есть, telegramId есть.
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Group.java` — текущая модель: name(128) + code(32 UNIQUE) + isActive + createdAt. **Удалить code, оставить name(32 UNIQUE).**
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Semester.java` — name + dateFrom + dateTo + isActive + firstWeekType.

**Контракт**:
- `services/academic-service/academic-api-contract/.../api/UserApi.java` — `listUsers()` БЕЗ search.
- `services/academic-service/academic-api-contract/.../api/GroupApi.java` — посмотреть и упростить под одно поле.
- `services/academic-service/academic-api-contract/.../api/SemesterApi.java` — посмотреть.
- `services/academic-service/academic-api-contract/.../dto/user/CreateUserRequest.java` — telegramId опционально, нужно условно required.

**Сервисы**:
- `services/academic-service/academic-app/.../user/UserService.java` — listUsers без search, createUser без явной валидации conflict.
- `services/academic-service/academic-app/.../group/GroupService.java` — `existsByCode`, нужно перепрофилировать.
- `services/academic-service/academic-app/.../semester/SemesterService.java` — `create` без проверок прошлого/пересечения.

**Глобальная обработка ошибок**:
- `services/academic-service/academic-app/.../exception/GlobalExceptionHandler.java` — нет `DataIntegrityViolationException`, всё валится в 500.

**Миграции** (`services/academic-service/academic-app/src/main/resources/db/migration/`):
- V1__baseline.sql — текущая схема groups (code UNIQUE), semesters (only one active).
- V2..V7 — последовательные апдейты (V7 = avatar из Фазы B багфиксов).
- **Нужны**: V8 (group code drop + name unique 32), V9 (groups.promoted_for_semester_id), V10 (semesters EXCLUDE no-overlap + btree_gist extension).

**Тестовые миграции** (mirrored):
- `services/auth-service/src/test/resources/db/migration/V1__baseline.sql` — содержит схему groups (см. RETROSPECTIVE — есть прецедент сворачивания миграций в V1 для тестов).
- Стратегия: добавить изменения схемы в V1 baseline (test-only), как делалось ранее для split-name и avatar_id.

### Frontend (web-panel)

- `frontends/web-panel/src/app/features/admin/users/users-page.component.ts` — search уже шлёт.
- `frontends/web-panel/src/app/features/admin/users/users-page.component.html` — нужна колонка «Начальный пароль».
- `frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.ts` — нужны разные сообщения по 409, telegram required validator при role=student.
- `frontends/web-panel/src/app/features/admin/groups/group-dialog/group-dialog.component.ts` — два поля → одно.
- `frontends/web-panel/src/app/features/admin/semesters/semester-dialog/semester-dialog.component.ts` — добавить минимальную дату, asyncValidator на overlap.
- `frontends/web-panel/src/app/features/admin/shared/types.ts` — типы UserResponse / GroupResponse — починить.
- `frontends/web-panel/src/app/features/admin/shared/admin-api.service.ts` — search параметр уже есть.

### Tests требующие правки

- `services/academic-service/academic-app/src/test/java/.../GroupServiceTest.java` — поле code.
- `frontends/web-panel/src/app/features/admin/groups/groups-page.component.spec.ts` — code в моках.
- (полный список — собрать через `grep -r "\.code\|getCode\|setCode" services/academic-service` и `grep -rn "code:" frontends/web-panel/src/app/features/admin`).

</artifact_inventory>

---

<implementation_notes>
## Implementation Notes

### Specification API для поиска (D-02)

```java
// UserSpecifications.java
public static Specification<User> matchesSearch(String q) {
    if (q == null || q.isBlank()) return null;
    String like = "%" + q.toLowerCase() + "%";
    return (root, cq, cb) -> cb.or(
        cb.like(cb.lower(root.get("login")), like),
        cb.like(cb.lower(root.get("lastName")), like),
        cb.like(cb.lower(root.get("firstName")), like),
        cb.like(cb.lower(root.get("middleName")), like),
        cb.like(cb.toString(root.get("telegramId")), like)
    );
}
```

`UserRepository extends JpaSpecificationExecutor<User>`. В сервисе — `Specification.where(matchesSearch(q)).and(matchesRole(role)).and(matchesStatus(status))`.

### Conflict handler (D-05)

```java
@ExceptionHandler(DataIntegrityViolationException.class)
ResponseEntity<ProblemDetail> handle(DataIntegrityViolationException e) {
    String constraint = extractConstraintName(e); // SQLState 23505
    String field = mapConstraintToField(constraint);
    var pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
        "Поле \"" + field + "\" уже используется другой учётной записью");
    pd.setProperty("field", field);
    return ResponseEntity.status(409).body(pd);
}
```

`mapConstraintToField`: `users_login_key→login`, `users_email_key→email`, `users_telegram_id_key→telegramId`, `users_employee_number_key→employeeNumber`, `groups_name_key→name` (после миграции V8). Список явно константный.

### Миграция V8 (slip group code → name)

```sql
-- V8__group_unify_name.sql
-- BUG-006: единое поле "Название группы" вместо name+code (формат XXXx-NNN).
-- В проде только тестовая группа — данных мало, миграция безопасна.

-- 1) Скопировать code в name там, где code заполнен.
UPDATE groups SET name = code WHERE code IS NOT NULL AND code <> '';

-- 2) Сжать длину и добавить уникальность.
ALTER TABLE groups
    ALTER COLUMN name TYPE VARCHAR(32),
    ADD CONSTRAINT groups_name_key UNIQUE (name);

-- 3) Удалить устаревший code.
ALTER TABLE groups DROP COLUMN code;
```

### Миграция V9 (promoted flag)

```sql
ALTER TABLE groups
    ADD COLUMN promoted_for_semester_id BIGINT REFERENCES semesters(id);
```

### Миграция V10 (EXCLUDE on semesters)

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE semesters
    ADD CONSTRAINT semesters_no_overlap
    EXCLUDE USING gist (daterange(date_from, date_to, '[]') WITH &&);
```

### Scheduled promotion (D-24)

```java
@Service
public class GroupPromotionService {
    private static final Pattern NAME_RE =
        Pattern.compile("^([A-ZА-ЯЁ]{2,4})([1-6])([1-3])-(\\d{3})$");

    private static final int MAX_COURSE_FOR_TYPE = Map.of(1, 4, 2, 2, 3, 4); // bachelor/master/postgrad

    @Scheduled(cron = "0 0 3 * * *", zone = "Europe/Moscow")
    @Transactional
    public PromotionSummary promoteAll() {
        Semester autumn = findUpcomingAutumn(LocalDate.now()).orElse(null);
        if (autumn == null) return PromotionSummary.skipped("no upcoming autumn");
        long days = ChronoUnit.DAYS.between(LocalDate.now(), autumn.getDateFrom());
        if (days > 14) return PromotionSummary.skipped("too early");

        var groups = groupRepository.findAllActiveAndNotPromotedFor(autumn.getId());
        int promoted = 0, archived = 0;
        for (Group g : groups) {
            // parse, increment course, archive if overflow
            ...
            g.setPromotedForSemesterId(autumn.getId());
            promoted++; // или archived++
        }
        return new PromotionSummary(promoted, archived);
    }
}
```

### Semester overlap validation (D-28)

В сервисе — простой запрос: `boolean exists = semesterRepository.existsByDatesOverlapping(from, to)`. Repository — native query на daterange. Дополнительно — БД constraint (D-31) ловит race condition.

</implementation_notes>

---

<execution_plan_seed>
## Suggested Plan Decomposition

Когда `/gsd-plan-phase` будет планировать — рекомендуемые планы:

- **58-01**: Backend поиск пользователей (D-01..D-04). Изолировано, низкий риск.
- **58-02**: Conflict-handler + frontend сообщения (D-05..D-07, D-12..D-14). Включает init password в таблице.
- **58-03**: Telegram ID required + frontend (D-08..D-11).
- **58-04**: Группы code→name миграция (D-15..D-21). Самый рискованный план.
- **58-05**: Семестры валидация + миграция EXCLUDE (D-28..D-32).
- **58-06**: Group promotion service + scheduled job (D-22..D-27). Зависит от 58-04 (формат имени).
- **58-07**: Финальная проверка, обновление phase-58-report.md.

Порядок: 58-01 → 58-02 → 58-03 → 58-04 → 58-05 → 58-06 → 58-07. Параллельно можно 58-01/02/03.

</execution_plan_seed>
