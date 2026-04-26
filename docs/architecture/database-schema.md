# RUT-UIT v2 — Каркас базы данных

## Соглашения

- Все enum-значения хранятся в PostgreSQL как **строки в нижнем регистре** (`@Convert(converter = LowercaseEnumConverter.class)`)
- Первичные ключи — `BIGSERIAL` (Long в Java)
- Все таблицы имеют `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
- Мягкое удаление: пользователи не удаляются физически, меняется `status`
- Миграции через Flyway (PostgreSQL), MongoDB — гибкая схема без миграций
- ORM: Spring Data JPA (PostgreSQL), Spring Data MongoDB (MongoDB)
- Все временные метки — `TIMESTAMP WITH TIME ZONE` (UTC)

---

## PostgreSQL: academic_db (Academic Service)

### Таблица: users

Все роли в одной таблице. Староста — это студент с флагом `is_headman`.

```sql
CREATE TYPE user_role AS ENUM ('admin', 'teacher', 'student');
CREATE TYPE account_status AS ENUM ('active', 'expelled', 'suspended', 'archived');

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    login               VARCHAR(32) NOT NULL UNIQUE,          -- student00001, teacher00001
    password_hash       VARCHAR(255),                         -- BCrypt, NULL для Telegram-only
    display_name        VARCHAR(255) NOT NULL,
    email               VARCHAR(255) UNIQUE,
    phone               VARCHAR(20),
    telegram_id         BIGINT UNIQUE,                        -- NULL до привязки
    telegram_username   VARCHAR(64),
    employee_number     VARCHAR(32) UNIQUE,                   -- табельный номер (только для teacher)
    role                user_role NOT NULL DEFAULT 'student',
    status              account_status NOT NULL DEFAULT 'active',
    is_headman          BOOLEAN NOT NULL DEFAULT FALSE,
    group_id            BIGINT REFERENCES groups(id) ON DELETE SET NULL,
    initial_password    VARCHAR(128),                         -- открытый пароль до первой смены, потом NULL
    password_changed    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_group ON users(group_id);
CREATE INDEX idx_users_telegram ON users(telegram_id);
CREATE INDEX idx_users_employee_number ON users(employee_number);
```

### Таблица: student_group_history

История переводов, отчислений, зачислений.

```sql
CREATE TABLE student_group_history (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    group_id    BIGINT NOT NULL REFERENCES groups(id),
    joined_at   DATE NOT NULL,
    left_at     DATE,                             -- NULL = текущая группа
    reason      VARCHAR(255),                     -- 'transfer', 'enroll', 'expel', 'academic_leave'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sgh_user ON student_group_history(user_id);
```

### Таблица: password_reset_tokens

```sql
CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,     -- SHA-256 от токена
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,                      -- NULL = не использован
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Таблица: semesters

```sql
CREATE TABLE semesters (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,            -- 'Осенний семестр 2025'
    date_from   DATE NOT NULL,
    date_to     DATE NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Только один активный семестр одновременно
    CONSTRAINT only_one_active_semester
        EXCLUDE USING btree (is_active WITH =) WHERE (is_active = TRUE)
);
```

### Таблица: groups

```sql
CREATE TABLE groups (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,            -- 'ИВТ-21-1'
    code        VARCHAR(32) UNIQUE,               -- уникальный код
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Таблица: subjects

Предметы с типом. Название может повторяться (разные группы — разные записи через `teacher_subject_groups`).

```sql
CREATE TYPE subject_type AS ENUM ('lecture', 'practice', 'lab');

CREATE TABLE subjects (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,            -- 'Математический анализ'
    type        subject_type NOT NULL DEFAULT 'lecture',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Таблица: teacher_subject_groups

Привязка преподаватель–предмет–группа. Потоковые лекции: один преподаватель привязан к одному предмету сразу в нескольких группах — отдельные записи.

```sql
CREATE TABLE teacher_subject_groups (
    id          BIGSERIAL PRIMARY KEY,
    teacher_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject_id  BIGINT NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    group_id    BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    semester_id BIGINT NOT NULL REFERENCES semesters(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (teacher_id, subject_id, group_id, semester_id)
);

CREATE INDEX idx_tsg_teacher ON teacher_subject_groups(teacher_id);
CREATE INDEX idx_tsg_group ON teacher_subject_groups(group_id);
CREATE INDEX idx_tsg_semester ON teacher_subject_groups(semester_id);
```

### Таблица: headman_assistants

Помощники старосты с гранулярными правами.

```sql
CREATE TABLE headman_assistants (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    student_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permissions     VARCHAR(64)[] NOT NULL,        -- ['mark_attendance', 'manage_excuses']
    assigned_by     BIGINT NOT NULL REFERENCES users(id),  -- кто назначил (headman)
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ,

    UNIQUE (group_id, student_id)
);
```

### Таблица: campus_settings

Геофенс кампуса. Одна строка на весь вуз.

```sql
CREATE TABLE campus_settings (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL DEFAULT 'Главный кампус',
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    radius_m    INTEGER NOT NULL DEFAULT 200,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Таблица: homeworks

Домашние задания. Привязаны к группе и предмету.

```sql
CREATE TABLE homeworks (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    subject_id      BIGINT NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    semester_id     BIGINT NOT NULL REFERENCES semesters(id) ON DELETE CASCADE,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,                          -- текст задания
    link            VARCHAR(1000),                 -- ссылка на облако/git/чат
    published_by    BIGINT NOT NULL REFERENCES users(id),  -- headman или assistant
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hw_group_subject ON homeworks(group_id, subject_id);
```

---

## PostgreSQL: schedule_db (Schedule Service)

### Таблица: schedule_items

Шаблон расписания (повторяющийся еженедельно).

```sql
CREATE TYPE week_type AS ENUM ('all', 'odd', 'even');

CREATE TABLE schedule_items (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT NOT NULL,               -- FK логический (другая БД)
    subject_id      BIGINT NOT NULL,               -- FK логический
    teacher_id      BIGINT NOT NULL,               -- FK логический
    semester_id     BIGINT NOT NULL,               -- FK логический
    day_of_week     SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 5),  -- 0=Пн, 5=Сб
    lesson_number   SMALLINT NOT NULL CHECK (lesson_number BETWEEN 1 AND 8),
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    week_type       week_type NOT NULL DEFAULT 'all',
    room            VARCHAR(64),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Одна пара в один слот (с учётом чётности)
    UNIQUE (group_id, day_of_week, lesson_number, week_type, semester_id)
);

CREATE INDEX idx_si_group_semester ON schedule_items(group_id, semester_id);
```

**Важно**: `group_id`, `subject_id`, `teacher_id`, `semester_id` — это NOT FK в SQL-смысле, потому что они живут в другой БД (`academic_db`). Целостность проверяется на уровне приложения через gRPC-вызовы к Academic Service.

### Таблица: lessons

Конкретные экземпляры пар на дату. Генерируются автоматически из `schedule_items`.

```sql
CREATE TYPE lesson_status AS ENUM ('planned', 'active', 'closed', 'cancelled');

CREATE TABLE lessons (
    id                  BIGSERIAL PRIMARY KEY,
    schedule_item_id    BIGINT NOT NULL REFERENCES schedule_items(id) ON DELETE CASCADE,
    date                DATE NOT NULL,
    status              lesson_status NOT NULL DEFAULT 'planned',
    is_geo_blocked      BOOLEAN NOT NULL DEFAULT FALSE,    -- староста заблокировал геоотметку
    cancel_reason       VARCHAR(512),                      -- причина отмены
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at           TIMESTAMPTZ,

    UNIQUE (schedule_item_id, date)
);

CREATE INDEX idx_lessons_date ON lessons(date);
CREATE INDEX idx_lessons_status ON lessons(status) WHERE status IN ('planned', 'active');
```

---

## MongoDB: attendance_db (Attendance Service)

### Deployment: replica set rs0 (M13 G7)

MongoDB деплоится как **single-node replica set** (`rs0`) — это
требование для multi-document transactions (Spring Data MongoDB
`MongoTransactionManager`). Standalone Mongo **не поддерживает**
transactions и вернёт `MongoCommandException: Transaction numbers
are only allowed on a replica set member or mongos`.

- **Image:** `bitnami/mongodb:7.0` — declarative RS setup через
  `MONGODB_REPLICA_SET_MODE=primary` + `MONGODB_REPLICA_SET_NAME=rs0`
  + `MONGODB_REPLICA_SET_KEY` (shared keyFile генерится из env).
- **Users:** создаются через Bitnami `MONGODB_EXTRA_USERNAMES/PASSWORDS/DATABASES`
  (заменяет legacy `infra/mongo/init-mongo.js` M10 D2).
- **Spring URI:** `?replicaSet=rs0&authSource=admin` (обязателен
  query-param для правильного routing'а).
- **Один физический инстанс**, две logical DB (`attendance_db`,
  `notification_db`). Хост — `mongo-attendance` (имя legacy от M05,
  контейнер физически shared).

**Transactions usage (M13 G7 — closes M02 CRITICAL #1):**
- `attendance-app`: `ExcuseService.createExcuse/createExcuseWithFile/updateStatus/applyDecisionFromBot`,
  `CheckinService.checkin`, `MarkingService.markAttendance/markBatch`,
  `LateCheckinService.createRequest/applyDecisionFromWeb/applyDecision` — все обёрнуты
  в `@Transactional`. Domain-save + `outboxStorage.save` атомарны.
- `notification-web`: `MongoTransactionManager` зарегистрирован
  proactively (future-proof consumer-path'ы).

**Runbook:** `docs/operations/runbooks/mongo-indexes-verify.md` — проверка
индексов + TTL после deploy (M13 G6 fail-fast).

**Rollback (если Bitnami image не подойдёт):** сохранить mongo:7 image
tag в backup, revert `docker-compose*.yml` на legacy `MONGO_INITDB_*`
env + init-mongo.js, retire `?replicaSet=rs0` из URI + убрать
`MongoTransactionManager` bean. Data format совместим с официальным
`mongo:7` — dump/restore не требуется.

### Коллекция: attendances

```javascript
{
  _id: ObjectId,

  // Ключ уникальности
  lesson_id:      NumberLong,     // PK из PostgreSQL schedule_db.lessons.id
  user_id:        NumberLong,     // PK из PostgreSQL academic_db.users.id

  // Денормализованные данные (для запросов без JOIN через gRPC)
  semester_id:    NumberLong,
  group_id:       NumberLong,
  subject_id:     NumberLong,
  teacher_id:     NumberLong,
  lesson_date:    ISODate,
  lesson_number:  NumberInt,      // 1–8

  // Статус посещения
  status:         String,         // "present" | "absent" | "excused" | "free_attendance"

  // Уважительная причина (только при status = "excused")
  excuse: {
    type:         String,         // "illness" | "summons" | "university_order" | "exemption" | "other"
    note:         String,         // произвольный комментарий
    confirmed_by: NumberLong,     // user_id старосты, подтвердившего (NULL = pending)
    confirmed_at: ISODate,
    is_confirmed: Boolean         // true = подтверждено, false = отклонено, null = pending
  },                              // null, если статус не excused

  // Геолокация отметки (только при student_geo)
  checkin_location: {
    lat:                    Double,
    lng:                    Double,
    accuracy_m:             Double,
    distance_from_campus_m: Double
  },                              // null, если отметка не по геолокации

  // Источник отметки
  marked_by:      String,         // "student_geo" | "headman" | "auto_scheduler" | "teacher_override" | "late_checkin"

  // Запрос "забыл отметиться" (только при marked_by = "late_checkin")
  late_checkin_request: {
    requested_at:   ISODate,
    confirmed_by:   NumberLong,   // user_id старосты
    confirmed_at:   ISODate,
    is_confirmed:   Boolean       // true/false/null
  },

  created_at:     ISODate,
  updated_at:     ISODate
}
```

### Индексы MongoDB

```javascript
// Уникальность — главная гарантия идемпотентности
db.attendances.createIndex(
  { lesson_id: 1, user_id: 1 },
  { unique: true }
);

// Отчёт студента за семестр (сортировка по дате)
db.attendances.createIndex(
  { user_id: 1, semester_id: 1, lesson_date: -1 }
);

// Отчёт группы по предмету (журнал старосты/преподавателя)
db.attendances.createIndex(
  { group_id: 1, semester_id: 1, subject_id: 1 }
);

// Все отметки конкретной пары
db.attendances.createIndex(
  { lesson_id: 1 }
);

// Pending excuse запросы (для старосты)
db.attendances.createIndex(
  { "excuse.is_confirmed": 1, group_id: 1 }
);

// Pending late_checkin запросы
db.attendances.createIndex(
  { "late_checkin_request.is_confirmed": 1, group_id: 1 }
);
```

---

## MongoDB: notification_db (Notification Web — M10)

### Коллекция: notification_history

Персистентная история user-facing уведомлений (M10 / NEW-166). Каждый
документ — snapshot события на момент persist (immutable после save).
Broadcast events (lesson.*) в коллекцию НЕ попадают — для них хватает
живого STOMP push (см. M10 D6 в `docs/milestones/M10-notification-history/DECISIONS.md`).

```javascript
{
  _id:       ObjectId,
  user_id:   NumberLong,   // адресат уведомления (academic_db.users.id)
  type:      String,       // NotificationType — 11 значений UPPER_CASE
                           //   EXCUSE_REQUESTED | EXCUSE_APPROVED | EXCUSE_REJECTED
                           //   LATE_CHECKIN_REQUESTED | LATE_CHECKIN_APPROVED | LATE_CHECKIN_REJECTED
                           //   LESSON_STARTED | LESSON_CLOSED | LESSON_CANCELLED | LESSON_REMINDER
                           //   ATTENDANCE_RED_ZONE
  payload:   { ... },      // denormalized snapshot event payload
  sent_at:   ISODate,      // когда событие persisted (server clock)
  read_at:   ISODate,      // null = непрочитанное
  trace_id:  String        // MDC traceId для связи с логами/трейсами
}
```

### Индексы notification_history

Создаются программно через `NotificationHistoryMongoConfig.@PostConstruct`
(не Flyway — MongoDB; pattern одинаковый с `PushMongoConfig`):

```javascript
// Список уведомлений per user, отсортированный DESC (pagination)
db.notification_history.createIndex(
  { user_id: 1, sent_at: -1 },
  { name: "idx_user_sent_desc" }
);

// Unread badge count (фильтр read_at:null)
db.notification_history.createIndex(
  { user_id: 1, read_at: 1 },
  { name: "idx_user_read" }
);

// TTL retention 30 дней (env NOTIFICATION_HISTORY_TTL_DAYS)
db.notification_history.createIndex(
  { sent_at: 1 },
  { name: "ttl_sent_at", expireAfterSeconds: 2592000 }
);
```

**TTL caveat:** Mongo TTL изменяется только через `collMod` после
создания индекса. Изменение env var НЕ перезаписывает существующий
индекс на работающем volume (отложено в `docs/archive/future-ideas.md` —
«Notification retention collMod auto-reconciler», v0.1).

### Mongo user — separation of concerns (M10 D2)

Один Mongo инстанс (`mongo-attendance` контейнер), две logical БД,
два user'а (Principle of Least Privilege):

| User | Права | Создаётся в |
|------|-------|-------------|
| `MONGO_USER` (default `rct_attendance_user`) | readWrite + dbAdmin на `attendance_db` | Bitnami `MONGODB_EXTRA_*` env (M13 G7) |
| `MONGO_NOTIFICATION_USER` (default `rct_notification_user`) | readWrite + dbAdmin на `notification_db` | Bitnami `MONGODB_EXTRA_*` env (M13 G7) |

Compromise одного credential'а не даёт доступа к данным другого
сервиса. Rotation runbook — `docs/operations/runbooks/secret-rotation.md`.

---

## Redis (Auth Service + Academic Service кэш)

### Auth Service — эфемерные данные

```
otp:{telegram_id}                 → "481927"           TTL: 120 сек
otp_attempts:{telegram_id}        → "2"                TTL: 300 сек (макс. 3 попытки)
otp_sent:{telegram_id}            → "true"             TTL: 60 сек (блок повторной отправки)
jwt:public_key                    → "<PEM>"            TTL: 3600 сек
refresh:{user_id}:{jti}           → "valid"            TTL: 7 дней
```

### Academic Service — кэш справочников

```
group:{group_id}:info             → JSON               TTL: 10 мин
group:{group_id}:members          → JSON               TTL: 5 мин
teacher:{teacher_id}:subjects     → JSON               TTL: 10 мин
semester:active                   → JSON               TTL: 30 мин
campus:geofence                   → JSON               TTL: 60 мин
```

### Attendance Service — rate limiting и дедупликация

```
rate:checkin:{user_id}            → counter            TTL: 60 сек (макс. 3 попытки/мин)
checkin:lock:{lesson_id}:{user_id} → "1"               TTL: 5 сек (дедупликация двойного клика)
```

---

## Java Enum-ы (все хранятся как lowercase строки)

```java
public enum UserRole {
    ADMIN, TEACHER, STUDENT;
}

public enum AccountStatus {
    ACTIVE, EXPELLED, SUSPENDED, ARCHIVED;
}

public enum SubjectType {
    LECTURE, PRACTICE, LAB;
}

public enum WeekType {
    ALL, ODD, EVEN;
}

public enum LessonStatus {
    PLANNED, ACTIVE, CLOSED, CANCELLED;
}

public enum AttendanceStatus {
    PRESENT, ABSENT, EXCUSED, FREE_ATTENDANCE;
}

public enum ExcuseType {
    ILLNESS, SUMMONS, UNIVERSITY_ORDER, EXEMPTION, OTHER;
}

public enum AttendanceSource {
    STUDENT_GEO, HEADMAN, AUTO_SCHEDULER, TEACHER_OVERRIDE, LATE_CHECKIN;
}

public enum AssistantPermission {
    MARK_ATTENDANCE, MANAGE_EXCUSES, MANAGE_HOMEWORK, CANCEL_LESSONS, VIEW_STATS;
}

// notification-api-contract (M10 / NEW-167)
public enum NotificationType {
    EXCUSE_REQUESTED, EXCUSE_APPROVED, EXCUSE_REJECTED,
    LATE_CHECKIN_REQUESTED, LATE_CHECKIN_APPROVED, LATE_CHECKIN_REJECTED,
    LESSON_STARTED, LESSON_CLOSED, LESSON_CANCELLED, LESSON_REMINDER,
    ATTENDANCE_RED_ZONE;
}
```

### JPA Lowercase Converter (общий для всех enum-ов)

```java
@Converter
public class LowercaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> enumClass;

    public LowercaseEnumConverter(Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Enum.valueOf(enumClass, dbData.toUpperCase());
    }
}
```

---

## Принадлежность данных сервисам

| Таблица / Коллекция | БД | Сервис-владелец |
|---|---|---|
| `users` | academic_db | Academic Service |
| `student_group_history` | academic_db | Academic Service |
| `password_reset_tokens` | academic_db | Academic Service |
| `semesters` | academic_db | Academic Service |
| `groups` | academic_db | Academic Service |
| `subjects` | academic_db | Academic Service |
| `teacher_subject_groups` | academic_db | Academic Service |
| `headman_assistants` | academic_db | Academic Service |
| `campus_settings` | academic_db | Academic Service |
| `homeworks` | academic_db | Academic Service |
| `schedule_items` | schedule_db | Schedule Service |
| `lessons` | schedule_db | Schedule Service |
| `attendances` (коллекция) | attendance_db (MongoDB) | Attendance Service |
| `notification_history` (коллекция) | notification_db (MongoDB) | Notification Web (M10) |
| `push_subscriptions` (коллекция) | notification_db (MongoDB) | Notification Web |
| Redis все ключи | Redis | Auth + Academic + Attendance |

---

## Что можно безболезненно менять позже

- Добавление новых полей в любую таблицу (Flyway миграция)
- Новые значения в enum-ы (добавляем в Java enum + ALTER TYPE в PostgreSQL)
- Новые индексы в MongoDB (без даунтайма)
- Новые Redis-ключи (просто начинаем использовать)
- Новые типы excuse, новые permissions для помощников

## Что дорого менять позже

- Тип первичного ключа (Long → UUID) — затрагивает все FK и MongoDB
- Разделение таблиц между базами данных (например, перенос `homeworks` из academic_db в отдельную БД)
- Переименование коллекции MongoDB (нужна миграция данных)
- Структура `excuse` / `late_checkin_request` в MongoDB — меняет все запросы агрегации
