-- Clean Academic domain baseline. Historical db/migration files remain unchanged.
-- Retains domain constraints/guards only; every identity FK targets the assigned Academic profile.
-- No Auth credential, session, admission-grant, challenge or account table is created here.

-- Retained domain source: V1__baseline.sql SHA256 24182109ffedf9a8103d23d2ce908858a66123174d24873f8cc5097943b08882
-- V1__baseline.sql
-- Academic Service — начальная схема

CREATE TYPE user_role AS ENUM ('admin', 'teacher', 'student');
CREATE TYPE account_status AS ENUM ('active', 'expelled', 'suspended', 'archived');
CREATE TYPE subject_type AS ENUM ('lecture', 'practice', 'lab');

-- Groups (создаётся первой, т.к. users ссылается на неё)
CREATE TABLE groups (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    code        VARCHAR(32) UNIQUE,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Student group history
CREATE TABLE student_group_history (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES academic_profiles(id),
    group_id    BIGINT NOT NULL REFERENCES groups(id),
    joined_at   DATE NOT NULL,
    left_at     DATE,
    reason      VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sgh_user ON student_group_history(user_id);

-- Semesters
CREATE TABLE semesters (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    date_from   DATE NOT NULL,
    date_to     DATE NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT only_one_active_semester
        EXCLUDE USING btree (is_active WITH =) WHERE (is_active = TRUE)
);

-- Subjects
CREATE TABLE subjects (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    type        subject_type NOT NULL DEFAULT 'lecture',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Teacher-Subject-Group assignments
CREATE TABLE teacher_subject_groups (
    id          BIGSERIAL PRIMARY KEY,
    teacher_id  BIGINT NOT NULL REFERENCES academic_profiles(id) ON DELETE CASCADE,
    subject_id  BIGINT NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    group_id    BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    semester_id BIGINT NOT NULL REFERENCES semesters(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (teacher_id, subject_id, group_id, semester_id)
);

CREATE INDEX idx_tsg_teacher ON teacher_subject_groups(teacher_id);
CREATE INDEX idx_tsg_group ON teacher_subject_groups(group_id);
CREATE INDEX idx_tsg_semester ON teacher_subject_groups(semester_id);

-- Headman assistants
CREATE TABLE headman_assistants (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    student_id      BIGINT NOT NULL REFERENCES academic_profiles(id) ON DELETE CASCADE,
    permissions     VARCHAR(64)[] NOT NULL,
    assigned_by     BIGINT NOT NULL REFERENCES academic_profiles(id),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ,
    UNIQUE (group_id, student_id)
);

-- Campus settings
CREATE TABLE campus_settings (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL DEFAULT 'Главный кампус',
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    radius_m    INTEGER NOT NULL DEFAULT 200,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Attendance thresholds (red zone)
CREATE TABLE attendance_thresholds (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT REFERENCES groups(id) ON DELETE CASCADE,
    subject_id      BIGINT REFERENCES subjects(id) ON DELETE CASCADE,
    threshold_pct   INTEGER NOT NULL CHECK (threshold_pct BETWEEN 0 AND 100),
    set_by          BIGINT NOT NULL REFERENCES academic_profiles(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, subject_id)
);

-- Homeworks
CREATE TABLE homeworks (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    subject_id      BIGINT NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    semester_id     BIGINT NOT NULL REFERENCES semesters(id) ON DELETE CASCADE,
    lesson_id       BIGINT,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    link            VARCHAR(1000),
    published_by    BIGINT NOT NULL REFERENCES academic_profiles(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hw_group_subject ON homeworks(group_id, subject_id);
CREATE INDEX idx_hw_lesson ON homeworks(lesson_id);

-- Homework completions (personal tracker)
CREATE TABLE homework_completions (
    id              BIGSERIAL PRIMARY KEY,
    homework_id     BIGINT NOT NULL REFERENCES homeworks(id) ON DELETE CASCADE,
    student_id      BIGINT NOT NULL REFERENCES academic_profiles(id) ON DELETE CASCADE,
    completed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (homework_id, student_id)
);

-- Retained domain source: V4__campus_settings_bigserial.sql SHA256 6e1d2c790543a6f155d7e71e101ea8f3d418b776c3c0b8311892059cdf484998
-- V4__campus_settings_bigserial.sql
-- Fix campus_settings.id from SERIAL (INT4) to BIGSERIAL (INT8) to match CLAUDE.md convention
-- (BIGSERIAL / Long in Java). V1 incorrectly used SERIAL.
-- Converts the column type and re-creates the sequence as bigint.
ALTER TABLE campus_settings ALTER COLUMN id TYPE BIGINT;
ALTER SEQUENCE campus_settings_id_seq AS BIGINT;

-- Retained domain source: V5__add_enum_casts.sql SHA256 b0cacf1ae03fe9a9149703d3f36ebfcf6fc33457949598c504fa0c9253bce9ba
-- V5__add_enum_casts.sql
-- Add implicit casts from varchar/text to PostgreSQL enum types so that
-- JPA AttributeConverter (which binds enums as character varying) works correctly.

CREATE CAST (varchar AS user_role)    WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS account_status) WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS subject_type)  WITH INOUT AS IMPLICIT;

-- Retained domain source: V6__add_semester_first_week_type.sql SHA256 a3eb16dad4f27737cbe95ec04d224c6f2b6d3508ba4307e2392dac045c078dc5
-- V6__add_semester_first_week_type.sql
-- Adds first_week_type to semesters for week parity anchoring (LSSN-02)
-- Uses ('odd', 'even') only — 'all' is a template concept, not a semester property

CREATE TYPE week_type AS ENUM ('odd', 'even');

-- Implicit cast so JPA (binding varchar) can write to the week_type column (same pattern as V5)
CREATE CAST (varchar AS week_type) WITH INOUT AS IMPLICIT;

ALTER TABLE semesters
    ADD COLUMN first_week_type week_type NOT NULL DEFAULT 'odd';

-- Retained domain source: V8__group_unify_name.sql SHA256 f08c7c8c2cc2d05c54969b846e456db87eb5563591972eccb2e208c688e64644
-- V8__group_unify_name.sql
-- BUG-006 п.5: слить поля name+code в одно поле name.
--
-- Активный формат имени группы: ^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}$ (кириллица, 3 цифры)
-- Архивный формат (устанавливается сервисом архивации, см. план 06):
--   <active> (выпуск YYYY) — например 'УИТ-411 (выпуск 2026)'.
-- VARCHAR(32) покрывает и активный (макс 8), и архивный (макс ~22) форматы.
--
-- Миграция идемпотентна в пределах своей версии: column code дропается, данные переносятся.
-- В проде только тестовая группа — потери данных невозможны.

-- 1) Перенос значений code → name там, где code заполнен.
UPDATE groups
   SET name = code
 WHERE code IS NOT NULL
   AND code <> '';

-- 2) Схема name: VARCHAR(32) UNIQUE (ранее VARCHAR(128) NOT NULL без UNIQUE).
ALTER TABLE groups
    ALTER COLUMN name TYPE VARCHAR(32);

ALTER TABLE groups
    ADD CONSTRAINT groups_name_key UNIQUE (name);

-- 3) Удалить колонку code (вместе с её UNIQUE constraint — drops каскадно).
ALTER TABLE groups
    DROP COLUMN code;

-- Retained domain source: V9__groups_archived_at.sql SHA256 5c957c6468992bdeda2598c654c99d7e659a4c7ecc739e92225857da14ae834f
-- V9__groups_archived_at.sql
-- BUG-006 п.6 / план 58-06: добавить временную метку архивации группы.
--
-- Сервис архивации (GroupArchivalService) ставит is_active=false и
-- archived_at=now() одновременно, чтобы отличать естественный выпуск
-- от любого другого деактивирования.
--
-- Индекс на is_active (partial, только активные) ускоряет listGroups
-- при фильтре status=active и подсчёт active-групп в dashboard.

ALTER TABLE groups
    ADD COLUMN archived_at TIMESTAMPTZ NULL;

CREATE INDEX idx_groups_is_active ON groups(is_active) WHERE is_active = true;

-- Retained domain source: V10__semesters_no_overlap.sql SHA256 9a7172f1b0706799c4cd671f11a426dea7709753c2c1e1e2c7f0a3c8dbdf08d2
-- V10__semesters_no_overlap.sql
-- BUG-006 п.7 (Plan 58-05): атомарная защита от пересечения семестров.
--
-- EXCLUDE USING gist (daterange(date_from, date_to, '[]') WITH &&) запрещает
-- вставку строки, у которой диапазон [date_from, date_to] пересекается с уже
-- существующим. Требует extension btree_gist для индексной поддержки.
--
-- Service-level check в SemesterService.create/update выдаёт human-readable 409
-- (ConflictException), а DB-constraint — защищает от race condition (T-58-05-01).

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE semesters
    ADD CONSTRAINT semesters_no_overlap
    EXCLUDE USING gist (daterange(date_from, date_to, '[]') WITH &&);

-- Retained domain source: V11__enum_equality_operators.sql SHA256 4b2d7126096518c507806cf85ebb4310c75bb195d3cabb1485fed74f4e851e2e
-- V11__enum_equality_operators.sql
-- BUG-006 gap-closure (plan 58-10).
--
-- Проблема: Hibernate 6 при criteria `cb.equal(root.get("role"), UserRole.STUDENT)`
-- рендерит SQL `u.role = ?` с bind-параметром как varchar (работает JPA-конвертер).
-- PostgreSQL не имеет оператора `user_role = varchar` — V5 IMPLICIT cast работает
-- только для assignment, не для operators. SQLGrammarException: operator does not exist.
--
-- Решение: добавить полноценные `=` и `<>` операторы между enum и text для всех
-- кастомных enum-типов проекта. Использует встроенные casts из V5.

-- ---------- user_role ----------
CREATE OR REPLACE FUNCTION user_role_eq_text(user_role, text) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT $1::text = $2 $$;

CREATE OPERATOR = (
    LEFTARG = user_role, RIGHTARG = text,
    PROCEDURE = user_role_eq_text,
    COMMUTATOR = =, NEGATOR = <>,
    RESTRICT = eqsel, JOIN = eqjoinsel
);

CREATE OR REPLACE FUNCTION user_role_ne_text(user_role, text) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT $1::text <> $2 $$;

CREATE OPERATOR <> (
    LEFTARG = user_role, RIGHTARG = text,
    PROCEDURE = user_role_ne_text,
    COMMUTATOR = <>, NEGATOR = =,
    RESTRICT = neqsel, JOIN = neqjoinsel
);

-- ---------- account_status ----------
CREATE OR REPLACE FUNCTION account_status_eq_text(account_status, text) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT $1::text = $2 $$;

CREATE OPERATOR = (
    LEFTARG = account_status, RIGHTARG = text,
    PROCEDURE = account_status_eq_text,
    COMMUTATOR = =, NEGATOR = <>,
    RESTRICT = eqsel, JOIN = eqjoinsel
);

CREATE OR REPLACE FUNCTION account_status_ne_text(account_status, text) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT $1::text <> $2 $$;

CREATE OPERATOR <> (
    LEFTARG = account_status, RIGHTARG = text,
    PROCEDURE = account_status_ne_text,
    COMMUTATOR = <>, NEGATOR = =,
    RESTRICT = neqsel, JOIN = neqjoinsel
);

-- ---------- subject_type ----------
CREATE OR REPLACE FUNCTION subject_type_eq_text(subject_type, text) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT $1::text = $2 $$;

CREATE OPERATOR = (
    LEFTARG = subject_type, RIGHTARG = text,
    PROCEDURE = subject_type_eq_text,
    COMMUTATOR = =, NEGATOR = <>,
    RESTRICT = eqsel, JOIN = eqjoinsel
);

CREATE OR REPLACE FUNCTION subject_type_ne_text(subject_type, text) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT AS $$ SELECT $1::text <> $2 $$;

CREATE OPERATOR <> (
    LEFTARG = subject_type, RIGHTARG = text,
    PROCEDURE = subject_type_ne_text,
    COMMUTATOR = <>, NEGATOR = =,
    RESTRICT = neqsel, JOIN = neqjoinsel
);

-- Retained domain source: V12__subjects_group_id.sql SHA256 61528728c77dff54a60d38446e4161081c0cf99269911b5e194d582b603c694b
-- V12__subjects_group_id.sql
-- Phase 60-01 / D-01: предмет принадлежит группе. Добавляем subjects.group_id NOT NULL
-- с FK на groups(id). Тестовые данные удаляются, т.к. в проде таблицы subjects и
-- teacher_subject_groups пока не использовались и ссылочных данных в других модулях нет.
BEGIN;

-- Шаг 1: очистка зависимых данных (FK teacher_subject_groups.subject_id → subjects.id)
DELETE FROM teacher_subject_groups;
DELETE FROM subjects;

-- Шаг 2: добавить колонку group_id NOT NULL + FK на groups(id)
ALTER TABLE subjects
    ADD COLUMN group_id BIGINT NOT NULL;

ALTER TABLE subjects
    ADD CONSTRAINT fk_subjects_group
        FOREIGN KEY (group_id) REFERENCES groups(id);

CREATE INDEX idx_subjects_group ON subjects(group_id);

COMMIT;

-- Retained domain source: V13__homework_lesson_binding.sql SHA256 b1b06fc2f930abd5434790141ac60756dcc8b8920e878f5ab2bc4db3d91e7ee0
-- Phase 61 / D-01, D-02, D-15: привязка ДЗ к конкретной паре через natural key.
-- Колонка lesson_id в V1 была зарезервирована, но так и не использовалась кодом.
-- Переходим на (group_id, lesson_date, lesson_number) — как attendance-service (Phase 60 D-15).
-- Данных в homeworks/homework_completions нет — безопасный TRUNCATE.

TRUNCATE homeworks, homework_completions RESTART IDENTITY CASCADE;

-- Сброс старой привязки по surrogate id
DROP INDEX IF EXISTS idx_hw_lesson;
ALTER TABLE homeworks DROP COLUMN IF EXISTS lesson_id;

-- Новая привязка: дата + номер пары (NOT NULL по D-01)
ALTER TABLE homeworks
    ADD COLUMN lesson_date   DATE NOT NULL,
    ADD COLUMN lesson_number INT  NOT NULL;

-- Индекс для недельных выборок ДЗ по группе (D-15)
CREATE INDEX idx_homeworks_group_date ON homeworks(group_id, lesson_date);

-- Retained domain source: V14__update_campus_coordinates.sql SHA256 c55381c1e5246eed283c4bdbe14c9c4a08b8f76ad47e25ee36a6a04ccb856a3b
-- V14__update_campus_coordinates.sql
-- Update campus coordinates to the actual RUT MIIT main campus (ул. Образцова 9, Москва).
-- The V2 seed shipped approximate coordinates (55.7699, 37.7039) that were off by ~8 km,
-- causing legitimate check-ins to be rejected as "out of zone".
-- Production DBs have V2 already applied, so edits to V2 do not reach them — this migration
-- patches existing rows idempotently.
UPDATE campus_settings
SET lat = 55.788204,
    lng = 37.606762
WHERE name = 'Main Campus'
  AND lat = 55.7699
  AND lng = 37.7039;

-- Retained domain source: V15__shedlock_table.sql SHA256 4219986e3eebe6b064c3163b26d932813105a1bb9a15a25c2ad1bf98e6ad1baf
-- M02 Группа 3 — ShedLock table для distributed scheduler locks в academic_db.
-- Требуется для OutboxPublisherJob / OutboxCleanupJob (Группы 4 и 6) —
-- первые @Scheduled методы в academic-service.
-- Документация: https://github.com/lukas-krecan/ShedLock#jdbctemplate
CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- Retained domain source: V16__academic_outbox.sql SHA256 6d528bc095d06941653fc1602156d0c4b7e8c4269479fc2b2c97b9cfc9ef7af6
-- M02 Группа 3 — Reliable eventing outbox для academic-service.
-- Listener'ы записывают события сюда В ТОЙ ЖЕ @Transactional что и доменная
-- операция. OutboxPublisherJob (@Scheduled + @SchedulerLock) читает pending
-- rows, шлёт в Rabbit, помечает sent. OutboxCleanupJob (cron 3am) удаляет
-- sent rows старше 7д (NEW-7 retention).

CREATE TABLE academic_outbox
(
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(128) NOT NULL,
    payload     JSONB        NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'pending',
    retry_count INTEGER      NOT NULL DEFAULT 0,
    last_error  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ,

    CONSTRAINT academic_outbox_status_chk
        CHECK (status IN ('pending', 'sent', 'failed'))
);

CREATE INDEX idx_academic_outbox_pending
    ON academic_outbox (created_at)
    WHERE status = 'pending';

CREATE INDEX idx_academic_outbox_sent_cleanup
    ON academic_outbox (sent_at)
    WHERE status = 'sent';

-- Retained domain source: V17__add_performance_indexes.sql SHA256 7840a525d1daafa75cc270d75c24b27d80e9ddfe6d197e572221d64ac7ffb8b3
-- M05 Группа 1 (P2-10/1, DECISION D2).
--
-- Composite indexes на реальные hot queries с парой (group_id, semester_id):
--
--   1. TeacherSubjectGroupRepository.findByGroupIdAndSemesterId
--      — teacher-dashboard «все мои группы в семестре».
--      До M05: используется idx_tsg_group + in-memory filter по semester
--      (2/3 rows отбрасывается на 4 семестрах).
--
--   2. HomeworkRepository.findByGroupIdAndSemesterId
--      — homework-list «все ДЗ группы в семестре».
--      До M05: используется idx_homeworks_group_date (V13, для
--      недельных выборок), но planner падает на filter по semester.
--
-- Почему НЕ композит на users (group_id, semester_id):
--   таблица user_groups (из OWNER-ANSWERS 3695) не существует;
--   users.group_id уже покрыт idx_users_group (V1:41); UserRepository.
--   findByGroupId не фильтрует по semester. См. DECISIONS.md D2.
--
-- EXPLAIN baseline — см. docs/performance-indexes.md, Q2 и Q3.

CREATE INDEX IF NOT EXISTS idx_tsg_group_semester
    ON teacher_subject_groups (group_id, semester_id);

CREATE INDEX IF NOT EXISTS idx_hw_group_semester
    ON homeworks (group_id, semester_id);

-- Retained domain source: V18__event_consumer_processed.sql SHA256 e1950f9ff8c904e2ff1839457ce002ec61bbffed921cc7bdd22e62ad3ae1b797
-- M13 G8 — consumer-side dedup по event_id (M02 CRITICAL #2).
-- RabbitMQ at-least-once → один и тот же event может прийти повторно.
-- Перед обработкой consumer пишет (consumer_id, event_id) сюда; UNIQUE
-- constraint на пару даёт атомарный insert-or-fail в JpaIdempotencyStore.
-- Cleanup-job раз в сутки удаляет записи старше 7 дней (Rabbit retention).

CREATE TABLE event_consumer_processed
(
    consumer_id  VARCHAR(64) NOT NULL,
    event_id     UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (consumer_id, event_id)
);

CREATE INDEX idx_ecp_cleanup
    ON event_consumer_processed (processed_at);

-- Retained domain source: V19__audit_log.sql SHA256 d71dcd72ef4062ef3698ba50555ac66294e1d1e8d4b4ecfd0a682a7c08508a8b
-- M16 G6 — audit log table для @AdminAction aspect.
--
-- Таблица фиксирует ADMIN-действия (archive user, create group, etc.) с
-- attribution: кто, когда, какой ресурс. До v1 не пишем before/after diff
-- (deep cloning + JSON diff = отдельная задача), но добавили JSONB extras
-- для будущего расширения без миграции схемы.
--
-- Хранится в academic_db потому что academic — единственный сервис с
-- ADMIN-actions (М01 архитектура). Если в future schedule/attendance
-- понадобятся свои @AdminAction, они предоставят свой AuditLogStorage
-- impl с собственной таблицей в своей БД (см. DECISIONS § D3 SPI pattern).
--
-- Indexes — отдельная миграция V20 с CONCURRENTLY (см. CLAUDE.md).

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT,
    action          VARCHAR(64) NOT NULL,
    target_type     VARCHAR(64),
    target_id       BIGINT,
    correlation_id  VARCHAR(64),
    extras          JSONB,
    succeeded       BOOLEAN NOT NULL DEFAULT TRUE,
    error_message   VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE audit_log IS
    'M16 G6 — ADMIN-action audit. user_id NULL допустим (system-action / unauthenticated path). '
    'Retention: TBD (пока без cleanup job, при appearance disk pressure добавим). '
    'See docs/operations/runbooks/audit-log.md.';

-- Retained domain source: V20__audit_log_indexes.sql SHA256 aa925882102f76b4974720f4336c9d72452d80b57ddb44192ab621fab3e06da5
-- M16 G6 — индексы для audit_log. ИСПРАВЛЕНО (M16 G6 hotfix #3):
-- CREATE INDEX CONCURRENTLY вешал Flyway 10.20.1 + Postgres pg_advisory_lock
-- (issue #3961). На пустой таблице (V19 только что её создал, 0 строк)
-- plain CREATE INDEX берёт write lock на 0 строк = **мгновенно**,
-- без downtime. CONCURRENTLY нужен только на заполненных prod-таблицах.
--
-- Будущие индексы на заполненной audit_log (когда понадобятся) — отдельной
-- миграцией V{N+1} с CONCURRENTLY + V{N+1}.sql.conf executeInTransaction=false.
--
-- MigrationConcurrentlyTest baseline cutoff bumped до 20 — V20
-- grandfathered как «empty-table initial indexes».

CREATE INDEX IF NOT EXISTS idx_audit_log_user_created
    ON audit_log (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_action_created
    ON audit_log (action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_correlation
    ON audit_log (correlation_id);

-- Retained domain source: V21__user_name_icu_collation.sql SHA256 6a109dd2df5cba16af71016a51b87e65320569faf1db8f174bbeffd7995752cf
-- Cyrillic-aware ICU collation for user name sorting (BUG: students appeared
-- in heap-scan order, "Ё" landed at end with default C-locale).
-- Used by UserRepository ORDER BY ... COLLATE "ru_icu" — see
-- findByGroupId, findByRole, findByGroupIdAndRole.
--
-- ICU is bundled with postgres:16 Debian image. CREATE COLLATION is
-- DDL-but-transactional, so no -- ## marker needed.

CREATE COLLATION IF NOT EXISTS ru_icu (provider = icu, locale = 'ru-RU');

-- Retained domain source: V22__user_name_sort_index.sql SHA256 877f8554d7b9caf424f181ef65e9f0055d2d9df88d4270dc59beeb7570a11988
-- Composite index supporting ORDER BY last_name, first_name, middle_name with
-- ICU collation (V21). Partial — archived users excluded since
-- @SQLRestriction("status <> 'archived'") on User entity already filters them.
--
-- CONCURRENTLY НЕ используется по той же причине, что и в V20 (audit_log):
-- Flyway 10.20.1 (Spring Boot 3.4.1 BOM) имеет регрессию issue #3961 —
-- CREATE INDEX CONCURRENTLY вешает pg_advisory_lock и Spring context никогда
-- не запускается, что валит CI/E2E (контейнер unhealthy через 3 минуты).
--
-- Это ПРИЕМЛЕМО: в актуальном prod (v0.0.0-alpha.16, ~5-30 пользователей на
-- группу × ~10 групп = ≤300 строк в users) plain CREATE INDEX берёт write
-- lock на ~миллисекунды. Когда users перерастёт ~10K строк и блокировка
-- начнёт быть заметной — выпустить V{N}__reindex_users_concurrently.sql
-- после апгрейда Flyway до версии без issue #3961 (≥10.21).
--
-- См. M16 G6 hotfix #3 (2026-04-27) — там тот же подход применён к V20.

CREATE INDEX IF NOT EXISTS idx_users_lastname_icu
    ON academic_profiles (
        last_name COLLATE "ru_icu",
        first_name COLLATE "ru_icu",
        middle_name COLLATE "ru_icu"
    )
    WHERE archived_at IS NULL;

-- Retained domain source: V23__homework_notifications.sql SHA256 ea5e5b94f789f7cef8640739235d4470f213108ebac0e8f017df513d640f4835
ALTER TABLE homeworks
    ADD COLUMN due_reminder_sent_at TIMESTAMPTZ NULL;

CREATE TABLE homework_weekly_digest_runs (
    id          BIGSERIAL PRIMARY KEY,
    group_id    BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    week_start  DATE NOT NULL,
    sent_at     TIMESTAMPTZ NOT NULL,
    UNIQUE (group_id, week_start)
);

-- Retained domain source: V25__student_subject_homework_foundation.sql SHA256 52c1782848b5c333975f9162d56f544a053c12207018876bc6e1d5881b42dca3
-- B0 foundation: subject lesson types, effective-dated assignments and the
-- academic half of a reserved homework binding.
-- Flyway owns the transaction; the source gate is deliberately before DDL.

DO $$
BEGIN
    -- teacher_subject_groups is the former assignment shape. It has no
    -- validity/type provenance, so no row can be safely inferred.
    IF EXISTS (SELECT 1 FROM teacher_subject_groups) THEN
        RAISE EXCEPTION
            'V25 cannot migrate legacy teacher_subject_groups without validity provenance';
    END IF;
    IF EXISTS (SELECT 1 FROM homeworks) THEN
        RAISE EXCEPTION
            'V25 cannot migrate legacy homeworks without a durable binding';
    END IF;
END
$$;

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE subject_lesson_types
(
    subject_id  BIGINT      NOT NULL,
    lesson_type subject_type NOT NULL,

    CONSTRAINT subject_lesson_types_pk
        PRIMARY KEY (subject_id, lesson_type),
    CONSTRAINT subject_lesson_types_subject_fk
        FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT subject_lesson_types_type_chk
        CHECK (lesson_type IN ('lecture', 'practice', 'lab'))
);

-- Seed only the explicit legacy subject.type value. Names, groups and schedule
-- rows are not provenance for an additional type.
INSERT INTO subject_lesson_types (subject_id, lesson_type)
SELECT id, type
FROM subjects;

ALTER TABLE subjects
    ADD CONSTRAINT subjects_id_group_uq UNIQUE (id, group_id);

CREATE OR REPLACE FUNCTION enforce_subject_lesson_type_parent()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    affected_subject_id BIGINT;
BEGIN
    affected_subject_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.id ELSE NEW.id END;

    -- A row lock on the parent serializes concurrent deferred checks for the
    -- same subject. Two transactions deleting different types cannot both pass
    -- the final EXISTS check and leave a subject with zero types.
    PERFORM 1
    FROM subjects
    WHERE id = affected_subject_id
    FOR UPDATE;

    IF FOUND AND NOT EXISTS (
        SELECT 1
        FROM subject_lesson_types
        WHERE subject_id = affected_subject_id
    ) THEN
        RAISE EXCEPTION 'subject % must retain at least one lesson type', affected_subject_id;
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END
$$;

CREATE OR REPLACE FUNCTION enforce_subject_lesson_type_child()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    affected_subject_id BIGINT;
BEGIN
    affected_subject_id := CASE
        WHEN TG_OP = 'DELETE' THEN OLD.subject_id
        ELSE NEW.subject_id
    END;

    PERFORM 1
    FROM subjects
    WHERE id = affected_subject_id
    FOR UPDATE;

    IF FOUND AND NOT EXISTS (
        SELECT 1
        FROM subject_lesson_types
        WHERE subject_id = affected_subject_id
    ) THEN
        RAISE EXCEPTION 'subject % must retain at least one lesson type', affected_subject_id;
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END
$$;

CREATE OR REPLACE FUNCTION protect_subject_lesson_type_subject()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.subject_id IS DISTINCT FROM OLD.subject_id THEN
        RAISE EXCEPTION 'subject_lesson_types.subject_id is immutable';
    END IF;
    RETURN NEW;
END
$$;

CREATE CONSTRAINT TRIGGER subjects_lesson_type_minimum_trg
    AFTER INSERT OR UPDATE ON subjects
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION enforce_subject_lesson_type_parent();

CREATE CONSTRAINT TRIGGER subject_lesson_types_minimum_trg
    AFTER INSERT OR UPDATE OR DELETE ON subject_lesson_types
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION enforce_subject_lesson_type_child();

CREATE TRIGGER subject_lesson_types_subject_guard_trg
    BEFORE UPDATE ON subject_lesson_types
    FOR EACH ROW
    EXECUTE FUNCTION protect_subject_lesson_type_subject();

CREATE TABLE assignments
(
    id                      BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    teacher_id              BIGINT       NOT NULL,
    subject_id              BIGINT       NOT NULL,
    group_id                BIGINT       NOT NULL,
    semester_id             BIGINT       NOT NULL,
    lesson_type             subject_type NOT NULL,
    valid_from              DATE         NOT NULL,
    valid_until_exclusive   DATE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT assignments_id_positive_chk
        CHECK (id > 0),
    CONSTRAINT assignments_teacher_fk
        FOREIGN KEY (teacher_id) REFERENCES academic_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT assignments_subject_group_fk
        FOREIGN KEY (subject_id, group_id)
        REFERENCES subjects (id, group_id) ON DELETE RESTRICT,
    CONSTRAINT assignments_semester_fk
        FOREIGN KEY (semester_id) REFERENCES semesters (id) ON DELETE RESTRICT,
    CONSTRAINT assignments_subject_type_fk
        FOREIGN KEY (subject_id, lesson_type)
        REFERENCES subject_lesson_types (subject_id, lesson_type) ON DELETE RESTRICT,
    CONSTRAINT assignments_validity_chk
        CHECK (valid_until_exclusive IS NULL OR valid_until_exclusive > valid_from),
    CONSTRAINT assignments_identity_uq
        UNIQUE (id, subject_id, group_id, semester_id, lesson_type),
    CONSTRAINT assignments_no_same_teacher_overlap
        EXCLUDE USING gist
        (
            teacher_id WITH =,
            subject_id WITH =,
            group_id WITH =,
            semester_id WITH =,
            lesson_type WITH =,
            daterange(valid_from, valid_until_exclusive, '[)') WITH &&
        )
);

CREATE INDEX idx_assignments_group_semester
    ON assignments (group_id, semester_id, valid_from);

CREATE INDEX idx_assignments_teacher_effective
    ON assignments (teacher_id, semester_id, valid_from);

CREATE OR REPLACE FUNCTION protect_assignment_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'assignments are closed, never deleted';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.teacher_id IS DISTINCT FROM OLD.teacher_id
       OR NEW.subject_id IS DISTINCT FROM OLD.subject_id
       OR NEW.group_id IS DISTINCT FROM OLD.group_id
       OR NEW.semester_id IS DISTINCT FROM OLD.semester_id
       OR NEW.lesson_type IS DISTINCT FROM OLD.lesson_type
       OR NEW.valid_from IS DISTINCT FROM OLD.valid_from
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'assignment identity is immutable';
    END IF;

    IF OLD.valid_until_exclusive IS NOT NULL
       AND (NEW.valid_until_exclusive IS NULL
            OR NEW.valid_until_exclusive > OLD.valid_until_exclusive) THEN
        RAISE EXCEPTION 'closed assignment validity cannot be extended or reopened';
    END IF;
    IF NEW.valid_until_exclusive IS NOT NULL
       AND NEW.valid_until_exclusive <= NEW.valid_from THEN
        RAISE EXCEPTION 'assignment close must be after valid_from';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER assignments_identity_guard_trg
    BEFORE UPDATE OR DELETE ON assignments
    FOR EACH ROW
    EXECUTE FUNCTION protect_assignment_identity();

-- Academic stores content and the immutable binding identity received from
-- Schedule. Legacy date/number columns remain for the coordinated B1 cutover.
ALTER TABLE homeworks
    ADD COLUMN binding_id       BIGINT      NOT NULL,
    ADD COLUMN actor_id         BIGINT      NOT NULL,
    ADD COLUMN request_key      UUID        NOT NULL,
    ADD COLUMN payload_hash     BYTEA       NOT NULL,
    ADD COLUMN publication_state VARCHAR(16) NOT NULL DEFAULT 'PENDING';

ALTER TABLE homeworks
    ADD CONSTRAINT homeworks_binding_positive_chk
        CHECK (binding_id > 0),
    ADD CONSTRAINT homeworks_actor_fk
        FOREIGN KEY (actor_id) REFERENCES academic_profiles(id) ON DELETE RESTRICT,
    ADD CONSTRAINT homeworks_payload_hash_chk
        CHECK (octet_length(payload_hash) = 32),
    ADD CONSTRAINT homeworks_publication_state_chk
        CHECK (publication_state IN ('PENDING', 'ACTIVE')),
    ADD CONSTRAINT homeworks_binding_uq
        UNIQUE (binding_id),
    ADD CONSTRAINT homeworks_actor_request_uq
        UNIQUE (actor_id, request_key);

CREATE OR REPLACE FUNCTION protect_homework_binding_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.binding_id IS DISTINCT FROM OLD.binding_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash THEN
        RAISE EXCEPTION 'homework binding identity is immutable';
    END IF;
    IF OLD.publication_state = 'ACTIVE' AND NEW.publication_state <> 'ACTIVE' THEN
        RAISE EXCEPTION 'active homework publication cannot be reverted';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER homeworks_binding_identity_guard_trg
    BEFORE UPDATE ON homeworks
    FOR EACH ROW
    EXECUTE FUNCTION protect_homework_binding_identity();

-- Retained domain source: V26__campus_map.sql SHA256 21b3f522634be7505fb73a613bf5bcb68c1584b7a1724bb45eba5c767e4058a0
-- B0 campus map foundation. The catalog is server-owned and deliberately has
-- no seed inventory, provider, HTTP or configuration wiring in this migration.
-- Flyway owns the transaction.

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

CREATE TABLE campus_map_catalog_revision
(
    id                        BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    revision                  BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    schema_version            INTEGER NOT NULL DEFAULT 1,
    validation_policy_version INTEGER NOT NULL DEFAULT 1,
    is_current                BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT campus_map_catalog_revision_id_positive_chk CHECK (id > 0),
    CONSTRAINT campus_map_catalog_revision_positive_chk CHECK (revision > 0),
    CONSTRAINT campus_map_catalog_revision_schema_positive_chk CHECK (schema_version > 0),
    CONSTRAINT campus_map_catalog_revision_policy_positive_chk
        CHECK (validation_policy_version > 0),
    CONSTRAINT campus_map_catalog_revision_revision_uq UNIQUE (revision)
);

CREATE UNIQUE INDEX uq_campus_map_catalog_current
    ON campus_map_catalog_revision (is_current)
    WHERE is_current;

CREATE TABLE campus_map_building
(
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    code           VARCHAR(64) NOT NULL,
    label          VARCHAR(255) NOT NULL,
    display_order  INTEGER NOT NULL CHECK (display_order >= 0),
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT campus_map_building_id_positive_chk CHECK (id > 0),
    CONSTRAINT campus_map_building_code_uq UNIQUE (code)
);

CREATE TABLE campus_map_floor
(
    id                 BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    building_id        BIGINT NOT NULL,
    code               VARCHAR(64) NOT NULL,
    label              VARCHAR(255) NOT NULL,
    display_order      INTEGER NOT NULL CHECK (display_order >= 0),
    current_version_id BIGINT,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT campus_map_floor_building_fk
        FOREIGN KEY (building_id) REFERENCES campus_map_building (id) ON DELETE RESTRICT,
    CONSTRAINT campus_map_floor_id_positive_chk CHECK (id > 0),
    CONSTRAINT campus_map_floor_building_code_uq UNIQUE (building_id, code),
    CONSTRAINT campus_map_floor_id_building_uq UNIQUE (id, building_id)
);

CREATE TABLE campus_map_plan_version
(
    id                  BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    floor_id            BIGINT NOT NULL,
    version             BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    catalog_revision_id BIGINT,
    label               VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,

    CONSTRAINT campus_map_plan_version_id_positive_chk CHECK (id > 0),
    CONSTRAINT campus_map_plan_version_positive_chk CHECK (version > 0),
    CONSTRAINT campus_map_plan_version_floor_fk
        FOREIGN KEY (floor_id) REFERENCES campus_map_floor (id) ON DELETE RESTRICT,
    CONSTRAINT campus_map_plan_version_catalog_fk
        FOREIGN KEY (catalog_revision_id)
        REFERENCES campus_map_catalog_revision (id) ON DELETE RESTRICT,
    CONSTRAINT campus_map_plan_version_floor_version_uq UNIQUE (floor_id, version),
    CONSTRAINT campus_map_plan_version_id_floor_uq UNIQUE (id, floor_id)
);

CREATE OR REPLACE FUNCTION protect_campus_map_plan_version()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    -- A publication update and a draft graph edit both serialize on this row.
    PERFORM 1
    FROM campus_map_plan_version
    WHERE id = OLD.id
    FOR UPDATE;

    IF TG_OP = 'DELETE' THEN
        IF OLD.published_at IS NOT NULL THEN
            RAISE EXCEPTION 'published campus map plan version is immutable';
        END IF;
        RETURN OLD;
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.floor_id IS DISTINCT FROM OLD.floor_id
       OR NEW.version IS DISTINCT FROM OLD.version
       OR NEW.catalog_revision_id IS DISTINCT FROM OLD.catalog_revision_id THEN
        RAISE EXCEPTION 'campus map plan version identity is immutable';
    END IF;

    IF OLD.published_at IS NOT NULL THEN
        IF NEW.published_at IS DISTINCT FROM OLD.published_at
           OR NEW.label IS DISTINCT FROM OLD.label
           OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
            RAISE EXCEPTION 'published campus map plan version is immutable';
        END IF;
    END IF;

    RETURN NEW;
END
$$;

CREATE TRIGGER campus_map_plan_version_identity_guard_trg
    BEFORE UPDATE OR DELETE ON campus_map_plan_version
    FOR EACH ROW
    EXECUTE FUNCTION protect_campus_map_plan_version();

ALTER TABLE campus_map_floor
    ADD CONSTRAINT campus_map_floor_current_version_fk
        FOREIGN KEY (current_version_id, id)
        REFERENCES campus_map_plan_version (id, floor_id)
        ON DELETE RESTRICT
        DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE campus_map_asset
(
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    plan_version_id BIGINT NOT NULL,
    format          VARCHAR(8) NOT NULL,
    content_type    VARCHAR(64) NOT NULL,
    content         BYTEA NOT NULL,
    bytes           BIGINT NOT NULL,
    sha256          BYTEA NOT NULL,
    width           INTEGER,
    height          INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT campus_map_asset_id_positive_chk CHECK (id > 0),
    CONSTRAINT campus_map_asset_version_fk
        FOREIGN KEY (plan_version_id) REFERENCES campus_map_plan_version (id) ON DELETE RESTRICT,
    CONSTRAINT campus_map_asset_format_chk CHECK (format IN ('PNG', 'SVG')),
    CONSTRAINT campus_map_asset_content_type_chk
        CHECK ((format = 'PNG' AND content_type = 'image/png')
            OR (format = 'SVG' AND content_type = 'image/svg+xml')),
    CONSTRAINT campus_map_asset_bytes_chk
        CHECK (bytes > 0 AND bytes <= 10485760 AND octet_length(content) = bytes),
    CONSTRAINT campus_map_asset_sha256_chk
        CHECK (octet_length(sha256) = 32 AND sha256 = public.digest(content, 'sha256')),
    CONSTRAINT campus_map_asset_dimensions_chk
        CHECK ((width IS NULL OR width > 0) AND (height IS NULL OR height > 0)),
    CONSTRAINT campus_map_asset_identity_uq UNIQUE (id, plan_version_id, format)
);

CREATE TABLE campus_map_plan_format
(
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    plan_version_id BIGINT NOT NULL,
    format          VARCHAR(8) NOT NULL,
    state           VARCHAR(16) NOT NULL,
    content_type    VARCHAR(64) NOT NULL,
    asset_id        BIGINT,
    bytes           BIGINT NOT NULL DEFAULT 0,
    sha256          BYTEA,
    width           INTEGER,
    height          INTEGER,
    view_box        DOUBLE PRECISION[],
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT campus_map_plan_format_id_positive_chk CHECK (id > 0),
    CONSTRAINT campus_map_plan_format_version_fk
        FOREIGN KEY (plan_version_id) REFERENCES campus_map_plan_version (id) ON DELETE RESTRICT,
    CONSTRAINT campus_map_plan_format_format_chk CHECK (format IN ('PNG', 'SVG')),
    CONSTRAINT campus_map_plan_format_state_chk
        CHECK (state IN ('ABSENT', 'PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT campus_map_plan_format_content_type_chk
        CHECK ((format = 'PNG' AND content_type = 'image/png')
            OR (format = 'SVG' AND content_type = 'image/svg+xml')),
    CONSTRAINT campus_map_plan_format_bytes_chk
        CHECK (bytes >= 0 AND bytes <= 10485760),
    CONSTRAINT campus_map_plan_format_sha256_chk
        CHECK (sha256 IS NULL OR octet_length(sha256) = 32),
    CONSTRAINT campus_map_plan_format_dimensions_chk
        CHECK ((width IS NULL OR width > 0) AND (height IS NULL OR height > 0)),
    CONSTRAINT campus_map_plan_format_view_box_chk
        CHECK (view_box IS NULL OR cardinality(view_box) = 4),
    CONSTRAINT campus_map_plan_format_ready_state_chk
        CHECK (
            (state = 'READY'
             AND asset_id IS NOT NULL
             AND bytes > 0
             AND sha256 IS NOT NULL)
            OR
            (state <> 'READY'
             AND asset_id IS NULL
             AND bytes = 0
             AND sha256 IS NULL)
        ),
    CONSTRAINT campus_map_plan_format_version_format_uq UNIQUE (plan_version_id, format)
);

ALTER TABLE campus_map_plan_format
    ADD CONSTRAINT campus_map_plan_format_asset_fk
        FOREIGN KEY (asset_id, plan_version_id, format)
        REFERENCES campus_map_asset (id, plan_version_id, format)
        ON DELETE RESTRICT;

CREATE OR REPLACE FUNCTION validate_campus_map_ready_asset()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    asset_record       campus_map_asset%ROWTYPE;
    plan_version_id    BIGINT;
    plan_published_at  TIMESTAMPTZ;
BEGIN
    plan_version_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.plan_version_id ELSE NEW.plan_version_id END;

    SELECT published_at INTO plan_published_at
    FROM campus_map_plan_version
    WHERE id = plan_version_id
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'campus map plan version % does not exist', plan_version_id;
    END IF;

    IF TG_OP = 'UPDATE'
       AND (NEW.plan_version_id IS DISTINCT FROM OLD.plan_version_id
            OR NEW.format IS DISTINCT FROM OLD.format) THEN
        RAISE EXCEPTION 'campus map plan format identity is immutable';
    END IF;
    IF plan_published_at IS NOT NULL THEN
        RAISE EXCEPTION 'published campus map plan format is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    IF NEW.state = 'READY' THEN
        SELECT asset.* INTO asset_record
        FROM campus_map_asset AS asset
        WHERE asset.id = NEW.asset_id
          AND asset.plan_version_id = NEW.plan_version_id
          AND asset.format = NEW.format;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'ready campus map format requires a matching immutable asset';
        END IF;
        IF asset_record.bytes <> NEW.bytes
           OR asset_record.sha256 IS DISTINCT FROM NEW.sha256
           OR asset_record.content_type <> NEW.content_type
           OR asset_record.width IS DISTINCT FROM NEW.width
           OR asset_record.height IS DISTINCT FROM NEW.height THEN
            RAISE EXCEPTION 'ready campus map format metadata does not match its asset';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER campus_map_plan_format_asset_guard_trg
    BEFORE INSERT OR UPDATE OR DELETE ON campus_map_plan_format
    FOR EACH ROW
    EXECUTE FUNCTION validate_campus_map_ready_asset();

CREATE OR REPLACE FUNCTION protect_campus_map_asset()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'campus map assets are immutable and retained';
END
$$;

CREATE TRIGGER campus_map_asset_immutable_trg
    BEFORE UPDATE OR DELETE ON campus_map_asset
    FOR EACH ROW
    EXECUTE FUNCTION protect_campus_map_asset();

CREATE INDEX idx_campus_map_floor_order
    ON campus_map_floor (building_id, display_order, id);

CREATE INDEX idx_campus_map_building_order
    ON campus_map_building (display_order, id);

CREATE TABLE campus_map_floor_daily_demand
(
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    floor_id    BIGINT NOT NULL,
    utc_day     DATE NOT NULL,
    open_count  BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT campus_map_floor_daily_demand_id_positive_chk CHECK (id > 0),
    CONSTRAINT campus_map_floor_daily_demand_count_chk CHECK (open_count >= 0),
    CONSTRAINT campus_map_floor_daily_demand_floor_fk
        FOREIGN KEY (floor_id) REFERENCES campus_map_floor (id) ON DELETE RESTRICT,
    CONSTRAINT campus_map_floor_daily_demand_floor_day_uq UNIQUE (floor_id, utc_day)
);

CREATE TABLE campus_map_open_intent
(
    owner_hmac       BYTEA NOT NULL,
    intent_id        UUID NOT NULL,
    building_id      BIGINT NOT NULL,
    floor_id         BIGINT NOT NULL,
    payload_hash     BYTEA NOT NULL,
    accepted_utc_day DATE NOT NULL,
    accepted_at      TIMESTAMPTZ NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,

    CONSTRAINT campus_map_open_intent_pk PRIMARY KEY (owner_hmac, intent_id),
    CONSTRAINT campus_map_open_intent_owner_hmac_chk CHECK (octet_length(owner_hmac) = 32),
    CONSTRAINT campus_map_open_intent_payload_hash_chk CHECK (octet_length(payload_hash) = 32),
    CONSTRAINT campus_map_open_intent_expiry_chk
        CHECK (expires_at = accepted_at + INTERVAL '48 hours'),
    CONSTRAINT campus_map_open_intent_floor_building_fk
        FOREIGN KEY (floor_id, building_id)
        REFERENCES campus_map_floor (id, building_id) ON DELETE RESTRICT,
    CONSTRAINT campus_map_open_intent_utc_day_chk
        CHECK (accepted_utc_day = (accepted_at AT TIME ZONE 'UTC')::date),
    CONSTRAINT campus_map_open_intent_dedupe_identity_uq
        UNIQUE (owner_hmac, intent_id, floor_id, accepted_utc_day)
);

CREATE OR REPLACE FUNCTION protect_campus_map_open_intent()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.owner_hmac IS DISTINCT FROM OLD.owner_hmac
           OR NEW.intent_id IS DISTINCT FROM OLD.intent_id
           OR NEW.building_id IS DISTINCT FROM OLD.building_id
           OR NEW.floor_id IS DISTINCT FROM OLD.floor_id
           OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash
           OR NEW.accepted_utc_day IS DISTINCT FROM OLD.accepted_utc_day
           OR NEW.accepted_at IS DISTINCT FROM OLD.accepted_at
           OR NEW.expires_at IS DISTINCT FROM OLD.expires_at THEN
            RAISE EXCEPTION 'campus map open intent identity is immutable';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.expires_at > CURRENT_TIMESTAMP THEN
        RAISE EXCEPTION 'campus map open intent cannot be removed before expiry';
    END IF;
    RETURN OLD;
END
$$;

CREATE TRIGGER campus_map_open_intent_guard_trg
    BEFORE UPDATE OR DELETE ON campus_map_open_intent
    FOR EACH ROW
    EXECUTE FUNCTION protect_campus_map_open_intent();

CREATE TABLE campus_map_floor_demand_dedupe
(
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    owner_hmac     BYTEA NOT NULL,
    floor_id       BIGINT NOT NULL,
    utc_day        DATE NOT NULL,
    intent_id      UUID NOT NULL,
    accepted_at    TIMESTAMPTZ NOT NULL,

    CONSTRAINT campus_map_floor_demand_dedupe_id_positive_chk CHECK (id > 0),
    CONSTRAINT campus_map_floor_demand_dedupe_owner_hmac_chk
        CHECK (octet_length(owner_hmac) = 32),
    CONSTRAINT campus_map_floor_demand_dedupe_floor_fk
        FOREIGN KEY (floor_id) REFERENCES campus_map_floor (id) ON DELETE RESTRICT,
    CONSTRAINT campus_map_floor_demand_dedupe_owner_floor_day_uq
        UNIQUE (owner_hmac, floor_id, utc_day),
    CONSTRAINT campus_map_floor_demand_dedupe_owner_intent_uq
        UNIQUE (owner_hmac, intent_id)
);

CREATE OR REPLACE FUNCTION increment_campus_map_floor_demand()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    intent_record campus_map_open_intent%ROWTYPE;
BEGIN
    SELECT * INTO intent_record
    FROM campus_map_open_intent
    WHERE owner_hmac = NEW.owner_hmac
      AND intent_id = NEW.intent_id
    FOR UPDATE;

    IF NOT FOUND
       OR intent_record.floor_id <> NEW.floor_id
       OR intent_record.accepted_utc_day <> NEW.utc_day
       OR intent_record.accepted_at IS DISTINCT FROM NEW.accepted_at THEN
        RAISE EXCEPTION 'campus map demand dedupe does not match its open intent';
    END IF;

    -- This trigger runs only for an actual INSERT. A conflicting retry does
    -- not create a dedupe row and therefore cannot inflate the aggregate.
    INSERT INTO campus_map_floor_daily_demand (floor_id, utc_day, open_count)
    VALUES (NEW.floor_id, NEW.utc_day, 1)
    ON CONFLICT (floor_id, utc_day)
    DO UPDATE SET open_count = campus_map_floor_daily_demand.open_count + 1,
                  updated_at = NOW();
    RETURN NEW;
END
$$;

CREATE TRIGGER campus_map_floor_demand_dedupe_count_trg
    AFTER INSERT ON campus_map_floor_demand_dedupe
    FOR EACH ROW
    EXECUTE FUNCTION increment_campus_map_floor_demand();

CREATE OR REPLACE FUNCTION protect_campus_map_demand_dedupe()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'campus map demand dedupe is immutable';
    END IF;

    -- Retain the daily key through the end of its UTC day plus 48 hours,
    -- independent of the particular intent acceptance time.
    IF (OLD.utc_day::timestamp + INTERVAL '3 days')
       > (CURRENT_TIMESTAMP AT TIME ZONE 'UTC') THEN
        RAISE EXCEPTION 'campus map demand dedupe cannot be removed before UTC retention';
    END IF;
    RETURN OLD;
END
$$;

CREATE TRIGGER campus_map_floor_demand_dedupe_immutable_trg
    BEFORE UPDATE OR DELETE ON campus_map_floor_demand_dedupe
    FOR EACH ROW
    EXECUTE FUNCTION protect_campus_map_demand_dedupe();

-- Retained domain source: V27__historical_membership_coverage.sql SHA256 23f772d86e6d09ca860c4e81e796c48fe42d11911f1f59457ee357162e103758
-- Managed historical membership coverage.  Existing groups are intentionally
-- left unmarked; an empty legacy roster is not proof of completeness.
CREATE TABLE group_history_coverage (
    group_id       BIGINT PRIMARY KEY REFERENCES groups(id) ON DELETE RESTRICT,
    coverage_from  DATE NOT NULL,
    writer_version VARCHAR(32) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_history_coverage_range_chk CHECK (coverage_from IS NOT NULL),
    CONSTRAINT group_history_coverage_writer_chk CHECK (writer_version <> '')
);

CREATE INDEX idx_sgh_group_user_joined
    ON student_group_history (group_id, user_id, joined_at, id);

-- Retained domain source: V28__homework_terminal_archive.sql SHA256 0a14754a2f3817836e4ddd6132af18ad0eaf3bd73c1d7a53f4ef2eba9d54ac7a
ALTER TABLE homeworks
    DROP CONSTRAINT homeworks_publication_state_chk;

ALTER TABLE homeworks
    ADD CONSTRAINT homeworks_publication_state_chk
        CHECK (publication_state IN ('PENDING', 'ACTIVE', 'ARCHIVED'));

-- Publication archive is a terminal state.  The V25 identity trigger
-- protected the old two-state contract by rejecting every ACTIVE -> non-
-- ACTIVE update; replace that transition guard so the coordinated terminal
-- archive can commit while ARCHIVED can never be resurrected.
CREATE OR REPLACE FUNCTION protect_homework_binding_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.binding_id IS DISTINCT FROM OLD.binding_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash THEN
        RAISE EXCEPTION 'homework binding identity is immutable';
    END IF;
    IF OLD.publication_state = 'ACTIVE'
       AND NEW.publication_state NOT IN ('ACTIVE', 'ARCHIVED') THEN
        RAISE EXCEPTION 'active homework publication cannot be reverted to pending';
    END IF;
    IF OLD.publication_state = 'ARCHIVED' AND NEW.publication_state <> 'ARCHIVED' THEN
        RAISE EXCEPTION 'archived homework publication cannot be reopened';
    END IF;
    RETURN NEW;
END
$$;

-- Retained domain source: V30__campus_map_distinct_openings.sql SHA256 40cc432982ec4db1aa73fba3814b1500a00d9741ad91739dffee3f6d301150ce
-- Count every distinct logical floor opening.  V26's owner/floor/day key
-- suppressed a second opening by the same actor; retry idempotency remains
-- protected by the owner/intent key and the immutable open-intent row.
ALTER TABLE campus_map_floor_demand_dedupe
    DROP CONSTRAINT campus_map_floor_demand_dedupe_owner_floor_day_uq;

-- Retained domain source: V31__admin_group_registry.sql SHA256 664c59cce0fb5f4233bbe34d935deabe964f914f613a63fa16bb716ec783fc48
-- ADMIN group registry: split code parts and retain the training-duration
-- provenance needed by the registry. Existing rows remain readable; values
-- are derived only from their canonical legacy name when that derivation is
-- unambiguous, and otherwise stay explicitly LEGACY_UNKNOWN.
ALTER TABLE groups
    ADD COLUMN alphabetic_code VARCHAR(4),
    ADD COLUMN numeric_code VARCHAR(3),
    ADD COLUMN current_course INTEGER,
    ADD COLUMN training_duration_years INTEGER,
    ADD COLUMN duration_status VARCHAR(24) NOT NULL DEFAULT 'LEGACY_UNKNOWN';

-- Reset first so invalid/contradictory legacy names stay explicitly unknown.
-- The predicate below is deliberately the same rule used by the Java fallback:
-- bachelor courses 1..4 and master courses 1..2 are derivable; every other
-- legacy combination remains nullable/LEGACY_UNKNOWN.
UPDATE groups
SET alphabetic_code = NULL,
    numeric_code = NULL,
    current_course = NULL,
    training_duration_years = NULL,
    duration_status = 'LEGACY_UNKNOWN';

UPDATE groups
SET alphabetic_code = substring(name FROM '^([А-ЯЁ][А-ЯЁа-яё]{1,3})-'),
    numeric_code = substring(name FROM '-(\d{3})'),
    current_course = substring(name FROM '-(\d)')::INTEGER,
    training_duration_years = CASE substring(name FROM '-\d(\d)\d')
        WHEN '1' THEN 4
        WHEN '7' THEN 2
    END,
    duration_status = 'KNOWN'
WHERE name ~ '^([А-ЯЁ][А-ЯЁа-яё]{1,3})-([1-4]1\d|[1-2]7\d)( \(выпуск \d{4}\))?$';

ALTER TABLE groups
    ADD CONSTRAINT groups_alphabetic_code_chk
        CHECK (alphabetic_code IS NULL OR alphabetic_code ~ '^[А-ЯЁ][А-ЯЁа-яё]{1,3}$'),
    ADD CONSTRAINT groups_numeric_code_chk
        CHECK (numeric_code IS NULL OR numeric_code ~ '^\d{3}$'),
    ADD CONSTRAINT groups_current_course_chk
        CHECK (current_course IS NULL OR current_course BETWEEN 1 AND 9),
    ADD CONSTRAINT groups_training_duration_chk
        CHECK (training_duration_years IS NULL OR training_duration_years >= 1),
    ADD CONSTRAINT groups_duration_status_chk
        CHECK (duration_status IN ('KNOWN', 'LEGACY_UNKNOWN'));

CREATE UNIQUE INDEX groups_code_pair_uq
    ON groups (alphabetic_code, numeric_code)
    WHERE is_active
      AND alphabetic_code IS NOT NULL AND numeric_code IS NOT NULL;

CREATE INDEX idx_groups_registry_code
    ON groups (alphabetic_code, numeric_code, id);

-- Retained domain source: V32__headman_assistant_active_history.sql SHA256 36267651f174250a3d226112c431701618e28a6ad523315707cf66ad37af19ba
-- Keep revoked assistant assignments as history while allowing a fresh active
-- assignment for the same student and group.
ALTER TABLE headman_assistants
    DROP CONSTRAINT IF EXISTS headman_assistants_group_id_student_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS headman_assistants_active_group_student_uq
    ON headman_assistants (group_id, student_id)
    WHERE is_active;

-- Retained domain source: V34__assignment_replacement_operation.sql SHA256 75903f42ed44a3dacb10a3cf37352790844d7223e07c123e61262452cd2f1767
-- S3 teacher replacement: durable Academic operation and replacement-only close.
-- Existing assignment identity remains immutable; only the effective end and
-- lifecycle state may change through the exact durable operation.

ALTER TABLE assignments
    ADD COLUMN lifecycle_state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE assignments
    ADD CONSTRAINT assignments_lifecycle_state_chk
        CHECK (lifecycle_state IN ('ACTIVE', 'PREPARED'));

    ALTER TABLE assignments
    DROP CONSTRAINT IF EXISTS assignments_validity_chk,
    ADD CONSTRAINT assignments_validity_chk
        CHECK (valid_until_exclusive IS NULL OR valid_until_exclusive >= valid_from);

CREATE INDEX idx_assignments_lifecycle_group
    ON assignments (group_id, semester_id, lifecycle_state, valid_from);

CREATE TABLE assignment_replacement_operations
(
    operation_id              UUID PRIMARY KEY,
    actor_id                  BIGINT       NOT NULL,
    request_key               UUID         NOT NULL,
    payload_hash              BYTEA        NOT NULL,
    source_assignment_id      BIGINT       NOT NULL,
    target_assignment_id      BIGINT       NOT NULL,
    effective_from            DATE         NOT NULL,
    source_valid_until        DATE,
    target_valid_until        DATE,
    state                     VARCHAR(16)  NOT NULL,
    schedule_receipt_state    VARCHAR(16),
    schedule_moved_count      BIGINT       NOT NULL DEFAULT 0,
    schedule_skipped_count    BIGINT       NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT assignment_replacement_operation_actor_chk CHECK (actor_id > 0),
    CONSTRAINT assignment_replacement_operation_hash_chk CHECK (octet_length(payload_hash) = 32),
    CONSTRAINT assignment_replacement_operation_source_chk CHECK (source_assignment_id > 0),
    CONSTRAINT assignment_replacement_operation_target_chk CHECK (target_assignment_id > 0),
    CONSTRAINT assignment_replacement_operation_state_chk
        CHECK (state IN ('PREPARED', 'APPLIED', 'COMMITTED')),
    CONSTRAINT assignment_replacement_operation_source_target_chk
        CHECK (source_assignment_id <> target_assignment_id),
    CONSTRAINT assignment_replacement_operation_actor_key_uq
        UNIQUE (actor_id, request_key)
);

CREATE INDEX idx_assignment_replacement_source
    ON assignment_replacement_operations (source_assignment_id, state, effective_from);

CREATE OR REPLACE FUNCTION protect_assignment_replacement_operation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'assignment replacement operations are immutable history';
    END IF;

    IF NEW.operation_id IS DISTINCT FROM OLD.operation_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash
       OR NEW.source_assignment_id IS DISTINCT FROM OLD.source_assignment_id
       OR NEW.target_assignment_id IS DISTINCT FROM OLD.target_assignment_id
       OR NEW.effective_from IS DISTINCT FROM OLD.effective_from
       OR NEW.source_valid_until IS DISTINCT FROM OLD.source_valid_until
       OR NEW.target_valid_until IS DISTINCT FROM OLD.target_valid_until
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'assignment replacement operation identity is immutable';
    END IF;

    IF NEW.state IS DISTINCT FROM OLD.state THEN
        IF NOT ((OLD.state = 'PREPARED' AND NEW.state = 'APPLIED')
             OR (OLD.state = 'APPLIED' AND NEW.state = 'COMMITTED')) THEN
            RAISE EXCEPTION 'invalid assignment replacement operation transition';
        END IF;
        IF OLD.state = 'PREPARED'
           AND (NEW.schedule_receipt_state IS DISTINCT FROM 'APPLIED'
                OR NEW.schedule_moved_count <> 0
                OR NEW.schedule_skipped_count <> 0) THEN
            RAISE EXCEPTION 'Academic APPLIED barrier must have an empty Schedule receipt';
        END IF;
        IF OLD.state = 'APPLIED'
           AND (OLD.schedule_receipt_state IS DISTINCT FROM 'APPLIED'
                OR NEW.schedule_receipt_state IS DISTINCT FROM 'COMMITTED') THEN
            RAISE EXCEPTION 'Academic commit must follow its exact applied barrier receipt';
        END IF;
    ELSIF NEW.schedule_receipt_state IS DISTINCT FROM OLD.schedule_receipt_state
       OR NEW.schedule_moved_count IS DISTINCT FROM OLD.schedule_moved_count
       OR NEW.schedule_skipped_count IS DISTINCT FROM OLD.schedule_skipped_count THEN
        RAISE EXCEPTION 'Schedule receipt can change only with an operation transition';
    END IF;

    IF NEW.schedule_moved_count < OLD.schedule_moved_count
       OR NEW.schedule_skipped_count < OLD.schedule_skipped_count THEN
        RAISE EXCEPTION 'Schedule receipt counts cannot move backwards';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION validate_assignment_replacement_operation_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.state <> 'PREPARED'
       OR NEW.schedule_receipt_state IS NOT NULL
       OR NEW.schedule_moved_count <> 0
       OR NEW.schedule_skipped_count <> 0 THEN
        RAISE EXCEPTION 'replacement operations must start in PREPARED without a Schedule receipt';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER assignment_replacement_operation_insert_guard_trg
    BEFORE INSERT ON assignment_replacement_operations
    FOR EACH ROW EXECUTE FUNCTION validate_assignment_replacement_operation_insert();

CREATE TRIGGER assignment_replacement_operation_guard_trg
    BEFORE UPDATE OR DELETE ON assignment_replacement_operations
    FOR EACH ROW EXECUTE FUNCTION protect_assignment_replacement_operation();

CREATE OR REPLACE FUNCTION validate_assignment_insert_interval()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.valid_until_exclusive IS NOT NULL
       AND NEW.valid_until_exclusive <= NEW.valid_from THEN
        RAISE EXCEPTION 'new assignments require a positive effective interval';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER assignments_insert_interval_guard_trg
    BEFORE INSERT ON assignments
    FOR EACH ROW EXECUTE FUNCTION validate_assignment_insert_interval();

CREATE OR REPLACE FUNCTION protect_assignment_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    operation_id_value UUID;
    operation_source  BIGINT;
    operation_target  BIGINT;
    operation_date    DATE;
    operation_state   VARCHAR(16);
    operation_found   BOOLEAN := FALSE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'assignments are closed, never deleted';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.teacher_id IS DISTINCT FROM OLD.teacher_id
       OR NEW.subject_id IS DISTINCT FROM OLD.subject_id
       OR NEW.group_id IS DISTINCT FROM OLD.group_id
       OR NEW.semester_id IS DISTINCT FROM OLD.semester_id
       OR NEW.lesson_type IS DISTINCT FROM OLD.lesson_type
       OR NEW.valid_from IS DISTINCT FROM OLD.valid_from
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'assignment identity is immutable';
    END IF;

    IF NEW.valid_until_exclusive IS NOT NULL
       AND NEW.valid_until_exclusive < NEW.valid_from THEN
        RAISE EXCEPTION 'assignment close must not precede valid_from';
    END IF;

    operation_id_value := NULLIF(current_setting('rutcampustrack.assignment_replacement_operation_id', true), '')::UUID;
    IF operation_id_value IS NOT NULL THEN
        SELECT source_assignment_id, target_assignment_id, effective_from, state
          INTO operation_source, operation_target, operation_date, operation_state
          FROM assignment_replacement_operations
         WHERE operation_id = operation_id_value
         FOR UPDATE;
        operation_found := FOUND;
        IF NOT operation_found THEN
            RAISE EXCEPTION 'assignment replacement operation does not exist';
        END IF;
    END IF;

    IF NEW.valid_until_exclusive IS DISTINCT FROM OLD.valid_until_exclusive THEN
        IF (operation_id_value IS NOT NULL
            AND operation_found
            AND operation_source = OLD.id
            AND operation_date = NEW.valid_until_exclusive
            AND operation_state = 'APPLIED'
            AND (OLD.valid_until_exclusive IS NULL
                 OR NEW.valid_until_exclusive < OLD.valid_until_exclusive)) IS NOT TRUE THEN
            RAISE EXCEPTION 'assignment end can only narrow to the exact APPLIED replacement date';
        END IF;
    END IF;

    IF NEW.lifecycle_state IS DISTINCT FROM OLD.lifecycle_state
       AND (operation_id_value IS NOT NULL
            AND operation_found
            AND operation_target = OLD.id
            AND operation_state = 'APPLIED'
            AND OLD.lifecycle_state = 'PREPARED'
            AND NEW.lifecycle_state = 'ACTIVE') IS NOT TRUE THEN
        RAISE EXCEPTION 'only PREPARED-to-ACTIVE is allowed for the exact APPLIED replacement target';
    END IF;

    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS assignments_identity_guard_trg ON assignments;
CREATE TRIGGER assignments_identity_guard_trg
    BEFORE UPDATE OR DELETE ON assignments
    FOR EACH ROW
    EXECUTE FUNCTION protect_assignment_identity();

-- Retained domain source: V35__semester_type.sql SHA256 43280a2b43f733f0b5f719e3b43baa8733ffa9169d99b4957da25ff191de1cf0
ALTER TABLE semesters
    ADD COLUMN semester_type VARCHAR(8);

ALTER TABLE semesters
    ADD COLUMN academic_year INTEGER;

ALTER TABLE semesters
    ADD CONSTRAINT semesters_semester_type_check
        CHECK (semester_type IS NULL OR semester_type IN ('AUTUMN', 'SPRING'));

ALTER TABLE semesters
    ADD CONSTRAINT semesters_academic_year_check
        CHECK (
            (semester_type IS NULL AND academic_year IS NULL)
            OR (semester_type IS NOT NULL AND academic_year IS NOT NULL AND academic_year BETWEEN 1 AND 9998)
        );

-- Retained domain source: V36__group_promotion_cycle_record.sql SHA256 820122792ba33fdcbe5cc3fb57cc53e37f9ce79436eb8bbdfd7afb2711e3f987
CREATE TABLE group_promotion_cycle_record
(
    id                BIGSERIAL PRIMARY KEY,
    cycle_semester_id BIGINT       NOT NULL,
    group_id          BIGINT       NOT NULL,
    action            VARCHAR(12)  NOT NULL,
    from_name         VARCHAR(32)  NOT NULL,
    to_name           VARCHAR(32),
    student_count     BIGINT       NOT NULL,
    processed_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT group_promotion_cycle_action_chk
        CHECK (action IN ('PROMOTE', 'ARCHIVE')),
    CONSTRAINT group_promotion_cycle_student_count_chk
        CHECK (student_count >= 0),
    CONSTRAINT group_promotion_cycle_semester_group_uq
        UNIQUE (cycle_semester_id, group_id)
);

-- Retained domain source: V37__homework_binding_terminal_archive.sql SHA256 a658745c90da1a2408644283271a08219c377313067c5d5c900e3ba2261d3ead
CREATE TABLE homework_binding_archives
(
    binding_id BIGINT      NOT NULL,
    actor_id   BIGINT      NOT NULL,
    request_key UUID       NOT NULL,
    homework_id BIGINT,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT homework_binding_archives_pk
        PRIMARY KEY (binding_id),
    CONSTRAINT homework_binding_archives_binding_positive_chk
        CHECK (binding_id > 0),
    CONSTRAINT homework_binding_archives_actor_positive_chk
        CHECK (actor_id > 0),
    CONSTRAINT homework_binding_archives_homework_positive_chk
        CHECK (homework_id IS NULL OR homework_id > 0),
    CONSTRAINT homework_binding_archives_actor_fk
        FOREIGN KEY (actor_id) REFERENCES academic_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT homework_binding_archives_homework_fk
        FOREIGN KEY (homework_id) REFERENCES homeworks (id) ON DELETE RESTRICT,
    CONSTRAINT homework_binding_archives_homework_uq
        UNIQUE (homework_id)
);

-- A terminal marker may acquire the exact Academic content id when content is
-- persisted after cancellation. Its binding/author/request identity is fixed.
CREATE OR REPLACE FUNCTION protect_homework_binding_archive_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.binding_id IS DISTINCT FROM OLD.binding_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.archived_at IS DISTINCT FROM OLD.archived_at THEN
        RAISE EXCEPTION 'homework binding archive identity is immutable';
    END IF;
    IF OLD.homework_id IS NOT NULL
       AND NEW.homework_id IS DISTINCT FROM OLD.homework_id THEN
        RAISE EXCEPTION 'homework binding archive content identity is immutable';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER homework_binding_archives_identity_guard_trg
    BEFORE UPDATE ON homework_binding_archives
    FOR EACH ROW
    EXECUTE FUNCTION protect_homework_binding_archive_identity();

-- Retained domain source: V38__durable_lesson_transfer_participant.sql SHA256 e99ce62ae29420de1fbe76731b42acb058c02932256555fcd85f317a23950bed
-- Academic's bounded participant receipt, immutable history and pending-publication marker.
CREATE TABLE lesson_transfer_receipts
(
    operation_id      UUID        NOT NULL,
    batch_index       INTEGER     NOT NULL,
    batch_count       INTEGER     NOT NULL,
    batch_size        SMALLINT    NOT NULL,
    operation_hash    BYTEA       NOT NULL,
    batch_hash        BYTEA       NOT NULL,
    source_lesson_id  BIGINT      NOT NULL,
    target_lesson_id  BIGINT      NOT NULL,
    result            VARCHAR(16) NOT NULL,
    error_code        VARCHAR(40),
    received_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (operation_id, batch_index),
    CONSTRAINT lesson_transfer_receipt_batch_bounds_chk
        CHECK (batch_count BETWEEN 1 AND 2147483647
               AND batch_index >= 0 AND batch_index < batch_count),
    CONSTRAINT lesson_transfer_receipt_batch_size_chk CHECK (batch_size BETWEEN 0 AND 64),
    CONSTRAINT lesson_transfer_receipt_hash_chk
        CHECK (octet_length(operation_hash) = 32 AND octet_length(batch_hash) = 32),
    CONSTRAINT lesson_transfer_receipt_ids_chk
        CHECK (source_lesson_id > 0 AND target_lesson_id > 0 AND source_lesson_id <> target_lesson_id),
    CONSTRAINT lesson_transfer_receipt_result_chk CHECK (result IN ('APPLIED', 'ERROR')),
    CONSTRAINT lesson_transfer_receipt_error_chk CHECK (
        (result = 'ERROR' AND error_code IN (
            'TARGET_DATA_CONFLICT', 'SOURCE_STATE_CONFLICT', 'SCOPE_MISMATCH',
            'INVALID_SNAPSHOT', 'DEPENDENCY_UNAVAILABLE', 'ARCHIVED_SEMESTER'))
        OR (result = 'APPLIED' AND error_code IS NULL)),
    CONSTRAINT lesson_transfer_receipt_empty_batch_chk CHECK (
        batch_size > 0 OR (batch_count = 1 AND batch_index = 0))
);

CREATE TABLE homework_binding_transfer_markers
(
    binding_id             BIGINT      PRIMARY KEY,
    actor_id               BIGINT      NOT NULL,
    request_key            UUID        NOT NULL,
    binding_payload_hash   BYTEA       NOT NULL,
    homework_id            BIGINT,
    operation_id           UUID        NOT NULL,
    operation_hash         BYTEA       NOT NULL,
    occurrence_id          BIGINT      NOT NULL,
    group_id               BIGINT      NOT NULL,
    subject_id             BIGINT      NOT NULL,
    semester_id            BIGINT      NOT NULL,
    source_lesson_id       BIGINT      NOT NULL,
    target_lesson_id       BIGINT      NOT NULL,
    source_date            DATE        NOT NULL,
    source_lesson_number   INTEGER     NOT NULL,
    target_date            DATE        NOT NULL,
    target_lesson_number   INTEGER     NOT NULL,
    state                  VARCHAR(16) NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT homework_transfer_marker_ids_chk CHECK (
        binding_id > 0 AND actor_id > 0 AND occurrence_id > 0
        AND group_id > 0 AND subject_id > 0 AND semester_id > 0
        AND source_lesson_id > 0 AND target_lesson_id > 0
        AND source_lesson_id <> target_lesson_id),
    CONSTRAINT homework_transfer_marker_hash_chk CHECK (
        octet_length(binding_payload_hash) = 32 AND octet_length(operation_hash) = 32),
    CONSTRAINT homework_transfer_marker_slot_chk CHECK (
        source_lesson_number BETWEEN 1 AND 8 AND target_lesson_number BETWEEN 1 AND 8),
    CONSTRAINT homework_transfer_marker_state_chk CHECK (state IN ('PENDING', 'APPLIED')),
    CONSTRAINT homework_transfer_marker_homework_chk CHECK (
        (state = 'PENDING' AND homework_id IS NULL)
        OR (state = 'APPLIED' AND homework_id IS NOT NULL))
);

CREATE INDEX idx_homework_transfer_marker_operation
    ON homework_binding_transfer_markers (operation_id, binding_id);

CREATE TABLE homework_binding_transfer_history
(
    operation_id           UUID        NOT NULL,
    binding_id             BIGINT      NOT NULL,
    operation_hash         BYTEA       NOT NULL,
    binding_payload_hash   BYTEA       NOT NULL,
    binding_revision       BIGINT      NOT NULL,
    actor_id               BIGINT      NOT NULL,
    request_key            UUID        NOT NULL,
    homework_id            BIGINT,
    occurrence_id          BIGINT      NOT NULL,
    group_id               BIGINT      NOT NULL,
    subject_id             BIGINT      NOT NULL,
    semester_id            BIGINT      NOT NULL,
    source_lesson_id       BIGINT      NOT NULL,
    target_lesson_id       BIGINT      NOT NULL,
    source_date            DATE        NOT NULL,
    source_lesson_number   INTEGER     NOT NULL,
    target_date            DATE        NOT NULL,
    target_lesson_number   INTEGER     NOT NULL,
    result_state           VARCHAR(24) NOT NULL,
    applied_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (operation_id, binding_id),
    CONSTRAINT homework_transfer_history_ids_chk CHECK (
        binding_id > 0 AND actor_id > 0 AND binding_revision > 0 AND occurrence_id > 0
        AND group_id > 0 AND subject_id > 0 AND semester_id > 0
        AND source_lesson_id > 0 AND target_lesson_id > 0
        AND source_lesson_id <> target_lesson_id),
    CONSTRAINT homework_transfer_history_hash_chk CHECK (
        octet_length(operation_hash) = 32 AND octet_length(binding_payload_hash) = 32),
    CONSTRAINT homework_transfer_history_state_chk
        CHECK (result_state IN ('MOVED', 'PENDING_PUBLICATION'))
);

CREATE OR REPLACE FUNCTION reject_academic_lesson_transfer_history_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'lesson transfer receipts and binding history are append-only';
END
$$;

CREATE TRIGGER lesson_transfer_receipt_append_only_trg
    BEFORE UPDATE OR DELETE ON lesson_transfer_receipts
    FOR EACH ROW EXECUTE FUNCTION reject_academic_lesson_transfer_history_mutation();
CREATE TRIGGER homework_transfer_history_append_only_trg
    BEFORE UPDATE OR DELETE ON homework_binding_transfer_history
    FOR EACH ROW EXECUTE FUNCTION reject_academic_lesson_transfer_history_mutation();

-- Retained domain source: V39__semester_archive_state.sql SHA256 4153c721c564b4bda6cf74350404abb32e7b08663b14fdb72303acb7dc1bad2f
ALTER TABLE semesters
    ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN archive_transition VARCHAR(10) NOT NULL DEFAULT 'NONE',
    ADD COLUMN state_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT semesters_archive_transition_valid
        CHECK (archive_transition IN ('NONE', 'ARCHIVING', 'RESTORING')),
    ADD CONSTRAINT semesters_archive_state_consistent
        CHECK (
            (is_archived AND archive_transition IN ('NONE', 'RESTORING'))
            OR (NOT is_archived AND archive_transition IN ('NONE', 'ARCHIVING'))
        ),
    ADD CONSTRAINT semesters_state_version_nonnegative
        CHECK (state_version >= 0),
    ADD CONSTRAINT semesters_write_blocked_inactive
        CHECK (NOT is_active OR (NOT is_archived AND archive_transition = 'NONE'));

-- Retained domain source: V40__semester_archive_operations.sql SHA256 dc2fc19f2ab877b1883226b4ea33955dc1569eaf7a4d924d177146662ac39d47
ALTER TABLE semesters
    ADD COLUMN archive_release_pending BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT semesters_archive_release_pending_consistent
        CHECK (NOT archive_release_pending OR (NOT is_active AND NOT is_archived AND archive_transition = 'NONE'));

CREATE TABLE semester_archive_operations (
    operation_id UUID PRIMARY KEY,
    idempotency_key UUID NOT NULL UNIQUE,
    actor_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    action VARCHAR(8) NOT NULL,
    operation_state VARCHAR(10) NOT NULL,
    retryable BOOLEAN NOT NULL,
    state_version BIGINT NOT NULL,
    transition VARCHAR(10) NOT NULL,
    is_active BOOLEAN NOT NULL,
    is_archived BOOLEAN NOT NULL,
    release_pending BOOLEAN NOT NULL,
    academic_status VARCHAR(20) NOT NULL,
    schedule_status VARCHAR(20) NOT NULL,
    attendance_status VARCHAR(20) NOT NULL,
    blocking_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT semester_archive_operations_action_valid
        CHECK (action IN ('ARCHIVE', 'RESTORE')),
    CONSTRAINT semester_archive_operations_state_valid
        CHECK (operation_state IN ('PENDING', 'COMPLETED', 'ERROR')),
    CONSTRAINT semester_archive_operations_transition_valid
        CHECK (transition IN ('NONE', 'ARCHIVING', 'RESTORING')),
    CONSTRAINT semester_archive_operations_academic_status_valid
        CHECK (academic_status IN ('NOT_STARTED', 'PENDING', 'READY', 'PREPARED_RESTORE', 'RELEASE_PENDING', 'RELEASED')),
    CONSTRAINT semester_archive_operations_schedule_status_valid
        CHECK (schedule_status IN ('NOT_STARTED', 'PENDING', 'READY', 'PREPARED_RESTORE', 'RELEASE_PENDING', 'RELEASED')),
    CONSTRAINT semester_archive_operations_attendance_status_valid
        CHECK (attendance_status IN ('NOT_STARTED', 'PENDING', 'READY', 'PREPARED_RESTORE', 'RELEASE_PENDING', 'RELEASED')),
    CONSTRAINT semester_archive_operations_state_version_nonnegative
        CHECK (state_version >= 0)
);

CREATE INDEX semester_archive_operations_semester_created
    ON semester_archive_operations (semester_id, created_at DESC, operation_id DESC);

CREATE UNIQUE INDEX semester_archive_operations_one_live_per_semester
    ON semester_archive_operations (semester_id)
    WHERE operation_state IN ('PENDING', 'ERROR');

-- The local Academic fence is installed in the same transaction as the
-- authoritative ARCHIVING/RESTORING transition. It is independent of the
-- operation history row so later operation replay cannot replace its epoch.
CREATE TABLE academic_semester_archive_barriers (
    semester_id BIGINT PRIMARY KEY,
    operation_id UUID NOT NULL,
    state_version BIGINT NOT NULL,
    participant_state VARCHAR(20) NOT NULL,
    blocking_reason TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT academic_semester_archive_barrier_state_valid
        CHECK (participant_state IN ('PENDING', 'READY', 'PREPARED_RESTORE', 'RELEASED')),
    CONSTRAINT academic_semester_archive_barrier_version_nonnegative
        CHECK (state_version >= 0)
);

-- These exact request identities are captured only from committed PENDING
-- publications/transfer markers while PREPARE_ARCHIVE acquires the same
-- semester lock used by every fenced business write.
CREATE TABLE academic_semester_archive_publication_admissions (
    operation_id UUID NOT NULL,
    state_version BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    request_key UUID NOT NULL,
    payload_hash BYTEA,
    source_event_id UUID,
    admitted_homework_id BIGINT,
    consumed_at TIMESTAMPTZ,
    PRIMARY KEY (operation_id, state_version, binding_id),
    CONSTRAINT academic_semester_archive_admission_hash_valid
        CHECK (payload_hash IS NULL OR octet_length(payload_hash) = 32),
    CONSTRAINT academic_semester_archive_admission_ids_valid
        CHECK (semester_id > 0 AND binding_id > 0 AND actor_id > 0 AND state_version >= 0)
);

CREATE INDEX academic_semester_archive_admission_lookup
    ON academic_semester_archive_publication_admissions
       (semester_id, binding_id, request_key, state_version);

-- Transfer source identity and archive-terminal identity are separate. A
-- cancellation may follow an already-applied transfer batch and must retain
-- both proofs without allowing a later publication to consume the tombstone.
ALTER TABLE academic_semester_archive_publication_admissions
    ADD COLUMN resolution_state VARCHAR(24) NOT NULL DEFAULT 'ADMITTED',
    ADD COLUMN terminal_event_id UUID,
    ADD COLUMN schedule_occurrence_id BIGINT,
    ADD COLUMN schedule_revision BIGINT,
    ADD CONSTRAINT academic_semester_archive_admission_resolution_valid
        CHECK (resolution_state IN ('ADMITTED', 'CANCEL_REQUESTED', 'CANCELLED_UNPUBLISHED')),
    ADD CONSTRAINT academic_semester_archive_admission_resolution_consistent
        CHECK ((resolution_state = 'ADMITTED' AND terminal_event_id IS NULL)
            OR (resolution_state = 'CANCEL_REQUESTED'
                AND payload_hash IS NOT NULL
                AND admitted_homework_id IS NULL AND consumed_at IS NULL
                AND schedule_occurrence_id IS NOT NULL AND schedule_revision IS NOT NULL)
            OR (resolution_state = 'CANCELLED_UNPUBLISHED'
                AND admitted_homework_id IS NULL AND consumed_at IS NOT NULL
                AND payload_hash IS NOT NULL
                AND terminal_event_id IS NOT NULL
                AND schedule_occurrence_id IS NOT NULL AND schedule_revision IS NOT NULL)),
    ADD CONSTRAINT academic_semester_archive_admission_schedule_identity
        CHECK ((schedule_occurrence_id IS NULL AND schedule_revision IS NULL)
            OR (schedule_occurrence_id > 0 AND schedule_revision > 0));

CREATE UNIQUE INDEX academic_semester_archive_terminal_event_uq
    ON academic_semester_archive_publication_admissions (terminal_event_id)
    WHERE terminal_event_id IS NOT NULL;

CREATE OR REPLACE FUNCTION protect_academic_semester_archive_admission()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.operation_id IS DISTINCT FROM OLD.operation_id
       OR NEW.state_version IS DISTINCT FROM OLD.state_version
       OR NEW.semester_id IS DISTINCT FROM OLD.semester_id
       OR NEW.binding_id IS DISTINCT FROM OLD.binding_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR (OLD.payload_hash IS NOT NULL AND NEW.payload_hash IS DISTINCT FROM OLD.payload_hash)
       OR (OLD.source_event_id IS NOT NULL AND NEW.source_event_id IS DISTINCT FROM OLD.source_event_id)
       OR (OLD.admitted_homework_id IS NOT NULL
           AND NEW.admitted_homework_id IS DISTINCT FROM OLD.admitted_homework_id)
       OR (OLD.schedule_occurrence_id IS NOT NULL
           AND NEW.schedule_occurrence_id IS DISTINCT FROM OLD.schedule_occurrence_id)
       OR (OLD.schedule_revision IS NOT NULL
           AND NEW.schedule_revision IS DISTINCT FROM OLD.schedule_revision)
       OR (OLD.terminal_event_id IS NOT NULL
           AND NEW.terminal_event_id IS DISTINCT FROM OLD.terminal_event_id)
       OR (OLD.consumed_at IS NOT NULL AND NEW.consumed_at IS DISTINCT FROM OLD.consumed_at)
       OR NOT (
           NEW.resolution_state = OLD.resolution_state
           OR (OLD.resolution_state = 'ADMITTED' AND NEW.resolution_state = 'CANCEL_REQUESTED')
           OR (OLD.resolution_state = 'CANCEL_REQUESTED' AND NEW.resolution_state = 'CANCELLED_UNPUBLISHED')) THEN
        RAISE EXCEPTION 'Academic archive publication admission is immutable or terminal';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER academic_semester_archive_admission_identity
    BEFORE UPDATE ON academic_semester_archive_publication_admissions
    FOR EACH ROW EXECUTE FUNCTION protect_academic_semester_archive_admission();

-- Pin a null-content transfer marker/history row to the exact immutable batch
-- receipt. Legacy rows remain nullable and therefore cannot be treated as
-- proven-complete by archive reconciliation.
ALTER TABLE lesson_transfer_receipts
    ADD COLUMN source_event_id UUID;
CREATE UNIQUE INDEX lesson_transfer_receipts_source_event_uq
    ON lesson_transfer_receipts (source_event_id)
    WHERE source_event_id IS NOT NULL;

ALTER TABLE homework_binding_transfer_markers
    ALTER COLUMN state TYPE VARCHAR(32),
    ADD COLUMN source_event_id UUID,
    ADD COLUMN batch_index INTEGER,
    DROP CONSTRAINT homework_transfer_marker_state_chk,
    DROP CONSTRAINT homework_transfer_marker_homework_chk,
    ADD CONSTRAINT homework_transfer_marker_source_batch_consistent
        CHECK ((source_event_id IS NULL AND batch_index IS NULL)
            OR (source_event_id IS NOT NULL AND batch_index IS NOT NULL AND batch_index >= 0)),
    ADD CONSTRAINT homework_transfer_marker_state_chk
        CHECK (state IN ('PENDING', 'APPLIED', 'CANCELLED_UNPUBLISHED')),
    ADD CONSTRAINT homework_transfer_marker_homework_chk
        CHECK ((state = 'PENDING' AND homework_id IS NULL)
            OR (state = 'APPLIED' AND homework_id IS NOT NULL)
            OR (state = 'CANCELLED_UNPUBLISHED' AND homework_id IS NULL));

ALTER TABLE homework_binding_transfer_history
    ADD COLUMN source_event_id UUID,
    ADD COLUMN batch_index INTEGER,
    ADD CONSTRAINT homework_transfer_history_source_batch_consistent
        CHECK ((source_event_id IS NULL AND batch_index IS NULL)
            OR (source_event_id IS NOT NULL AND batch_index IS NOT NULL AND batch_index >= 0));

CREATE TABLE academic_semester_archive_effect_receipts (
    source_event_id UUID PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    semester_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    payload_hash BYTEA NOT NULL,
    state VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    acknowledgement_event_id UUID NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at TIMESTAMPTZ,
    CONSTRAINT academic_semester_archive_effect_event_valid
        CHECK (event_type = 'homework.binding.archived'),
    CONSTRAINT academic_semester_archive_effect_hash_valid
        CHECK (semester_id > 0 AND binding_id > 0 AND octet_length(payload_hash) = 32),
    CONSTRAINT academic_semester_archive_effect_state_valid
        CHECK ((state = 'PENDING' AND applied_at IS NULL)
            OR (state = 'APPLIED' AND applied_at IS NOT NULL))
);

ALTER TABLE homework_binding_archives
    ADD COLUMN semester_id BIGINT,
    ADD COLUMN source_event_id UUID,
    ADD CONSTRAINT homework_binding_archives_semester_positive_chk
        CHECK (semester_id IS NULL OR semester_id > 0);
UPDATE homework_binding_archives marker
   SET semester_id = homework.semester_id
  FROM homeworks homework
 WHERE marker.homework_id = homework.id AND marker.semester_id IS NULL;

CREATE OR REPLACE FUNCTION protect_homework_binding_archive_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.binding_id IS DISTINCT FROM OLD.binding_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.archived_at IS DISTINCT FROM OLD.archived_at THEN
        RAISE EXCEPTION 'homework binding archive identity is immutable';
    END IF;
    IF OLD.homework_id IS NOT NULL
       AND NEW.homework_id IS DISTINCT FROM OLD.homework_id THEN
        RAISE EXCEPTION 'homework binding archive content identity is immutable';
    END IF;
    IF OLD.semester_id IS NOT NULL AND NEW.semester_id IS DISTINCT FROM OLD.semester_id THEN
        RAISE EXCEPTION 'homework binding archive semester scope is immutable';
    END IF;
    IF OLD.source_event_id IS NOT NULL AND NEW.source_event_id IS DISTINCT FROM OLD.source_event_id THEN
        RAISE EXCEPTION 'homework binding archive source event is immutable';
    END IF;
    IF NEW.source_event_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM academic_semester_archive_effect_receipts receipt
             WHERE receipt.source_event_id = NEW.source_event_id
               AND receipt.event_type = 'homework.binding.archived'
               AND receipt.semester_id = NEW.semester_id
               AND receipt.binding_id = NEW.binding_id
               AND receipt.state IN ('PENDING', 'APPLIED')) THEN
        RAISE EXCEPTION 'homework binding archive source event has no exact effect receipt';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION guard_academic_semester_archive_business_write()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    old_row JSONB;
    new_row JSONB;
    old_semester_id BIGINT;
    new_semester_id BIGINT;
    target_semester_id BIGINT;
    barrier_state VARCHAR(20);
    authority_transition VARCHAR(10);
    authority_version BIGINT;
    authority_archived BOOLEAN;
    authority_release_pending BOOLEAN;
    authority_active BOOLEAN;
    authority_blocked BOOLEAN;
    archive_event_id UUID;
    request_key_text TEXT;
    transfer_event_id UUID;
    transfer_operation_id UUID;
    transfer_batch_index INTEGER;
    transfer_binding_id BIGINT;
    transfer_semester_id BIGINT;
    transfer_operation_hash TEXT;
    transfer_batch_hash TEXT;
    transfer_binding_hash TEXT;
    admitted BOOLEAN := FALSE;
    admitted_count INTEGER := 0;
    event_admitted BOOLEAN := FALSE;
BEGIN
    IF TG_OP <> 'INSERT' THEN old_row := to_jsonb(OLD); END IF;
    IF TG_OP <> 'DELETE' THEN new_row := to_jsonb(NEW); END IF;

    IF TG_TABLE_NAME = 'homework_completions' THEN
        IF old_row IS NOT NULL THEN
            SELECT semester_id INTO old_semester_id FROM homeworks
             WHERE id = (old_row ->> 'homework_id')::BIGINT;
        END IF;
        IF new_row IS NOT NULL THEN
            SELECT semester_id INTO new_semester_id FROM homeworks
             WHERE id = (new_row ->> 'homework_id')::BIGINT;
        END IF;
    ELSE
        old_semester_id := nullif(old_row ->> 'semester_id', '')::BIGINT;
        new_semester_id := nullif(new_row ->> 'semester_id', '')::BIGINT;
    END IF;

    IF old_semester_id IS NULL AND new_semester_id IS NULL THEN
        IF EXISTS (SELECT 1 FROM academic_semester_archive_barriers
                    WHERE participant_state IN ('PENDING', 'READY', 'PREPARED_RESTORE')) THEN
            RAISE EXCEPTION 'Academic archive fence cannot resolve the affected semester'
                USING ERRCODE = '55000';
        END IF;
        IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
        RETURN NEW;
    END IF;

    archive_event_id := nullif(current_setting('rutcampustrack.archive_effect_event_id', TRUE), '')::UUID;
    request_key_text := nullif(current_setting('rutcampustrack.archive_publication_request_key', TRUE), '');
    transfer_event_id := nullif(current_setting('rutcampustrack.archive_transfer_event_id', TRUE), '')::UUID;
    transfer_operation_id := nullif(current_setting('rutcampustrack.archive_transfer_operation_id', TRUE), '')::UUID;
    transfer_batch_index := nullif(current_setting('rutcampustrack.archive_transfer_batch_index', TRUE), '')::INTEGER;
    transfer_binding_id := nullif(current_setting('rutcampustrack.archive_transfer_binding_id', TRUE), '')::BIGINT;
    transfer_semester_id := nullif(current_setting('rutcampustrack.archive_transfer_semester_id', TRUE), '')::BIGINT;
    transfer_operation_hash := nullif(current_setting('rutcampustrack.archive_transfer_operation_hash', TRUE), '');
    transfer_batch_hash := nullif(current_setting('rutcampustrack.archive_transfer_batch_hash', TRUE), '');
    transfer_binding_hash := nullif(current_setting('rutcampustrack.archive_transfer_binding_hash', TRUE), '');

    FOR target_semester_id IN
        SELECT DISTINCT candidate
          FROM unnest(ARRAY[old_semester_id, new_semester_id]) AS candidate
         WHERE candidate IS NOT NULL
         ORDER BY candidate
    LOOP
        admitted := FALSE;
        admitted_count := 0;
        event_admitted := FALSE;
        PERFORM pg_advisory_xact_lock(5452097, (target_semester_id % 2147483647)::INTEGER);
        SELECT participant_state INTO barrier_state
          FROM academic_semester_archive_barriers
         WHERE semester_id = target_semester_id;
        SELECT is_active, is_archived, archive_transition, archive_release_pending, state_version
          INTO authority_active, authority_archived, authority_transition,
               authority_release_pending, authority_version
          FROM semesters WHERE id = target_semester_id;
        authority_blocked := NOT FOUND OR authority_archived OR authority_release_pending
                OR authority_transition <> 'NONE';

        IF barrier_state IN ('PENDING', 'READY', 'PREPARED_RESTORE') OR authority_blocked THEN
            IF TG_TABLE_NAME = 'homeworks' THEN
                IF barrier_state = 'PENDING'
                    AND authority_transition = 'ARCHIVING'
                    AND authority_version = (SELECT state_version FROM academic_semester_archive_barriers
                                              WHERE semester_id = target_semester_id)
                    AND request_key_text IS NOT NULL THEN
                IF TG_OP = 'INSERT' AND NEW.publication_state = 'PENDING'
                        AND NEW.semester_id = target_semester_id
                        AND NEW.request_key::TEXT = request_key_text THEN
                    UPDATE academic_semester_archive_publication_admissions
                       SET admitted_homework_id = NEW.id
                     WHERE operation_id = (SELECT operation_id FROM academic_semester_archive_barriers
                                             WHERE semester_id = target_semester_id)
                       AND state_version = authority_version
                       AND semester_id = target_semester_id
                       AND binding_id = NEW.binding_id AND actor_id = NEW.actor_id
                       AND request_key = NEW.request_key AND payload_hash = NEW.payload_hash
                       AND resolution_state = 'ADMITTED'
                       AND admitted_homework_id IS NULL AND consumed_at IS NULL;
                    GET DIAGNOSTICS admitted_count = ROW_COUNT;
                    admitted := admitted_count = 1;
                ELSIF TG_OP = 'INSERT' AND NEW.publication_state = 'ARCHIVED'
                        AND NEW.semester_id = target_semester_id
                        AND NEW.request_key::TEXT = request_key_text THEN
                    UPDATE academic_semester_archive_publication_admissions admission
                       SET admitted_homework_id = NEW.id, consumed_at = now()
                     WHERE admission.operation_id = (SELECT operation_id
                         FROM academic_semester_archive_barriers WHERE semester_id = target_semester_id)
                       AND admission.state_version = authority_version
                       AND admission.semester_id = target_semester_id
                       AND admission.binding_id = NEW.binding_id AND admission.actor_id = NEW.actor_id
                       AND admission.request_key = NEW.request_key
                       AND admission.payload_hash = NEW.payload_hash
                       AND admission.admitted_homework_id IS NULL AND admission.consumed_at IS NULL
                       AND EXISTS (
                           SELECT 1 FROM homework_binding_archives marker
                           JOIN academic_semester_archive_effect_receipts receipt
                             ON receipt.source_event_id = marker.source_event_id
                          WHERE marker.binding_id = NEW.binding_id AND marker.actor_id = NEW.actor_id
                            AND marker.request_key = NEW.request_key
                            AND marker.semester_id = target_semester_id
                            AND marker.source_event_id IS NOT NULL AND receipt.result = 'APPLIED'
                            AND receipt.semester_id = target_semester_id
                            AND receipt.binding_id = NEW.binding_id);
                    GET DIAGNOSTICS admitted_count = ROW_COUNT;
                    admitted := admitted_count = 1;
                ELSIF TG_OP = 'UPDATE' AND OLD.publication_state = 'PENDING'
                        AND NEW.publication_state = 'ACTIVE'
                        AND NEW.id = OLD.id AND NEW.semester_id = OLD.semester_id
                        AND NEW.binding_id = OLD.binding_id AND NEW.actor_id = OLD.actor_id
                        AND NEW.request_key = OLD.request_key AND NEW.payload_hash = OLD.payload_hash
                        AND NEW.request_key::TEXT = request_key_text THEN
                    UPDATE academic_semester_archive_publication_admissions
                       SET consumed_at = now()
                     WHERE operation_id = (SELECT operation_id FROM academic_semester_archive_barriers
                                             WHERE semester_id = target_semester_id)
                       AND state_version = authority_version
                       AND semester_id = target_semester_id
                       AND binding_id = NEW.binding_id AND actor_id = NEW.actor_id
                       AND request_key = NEW.request_key AND payload_hash = NEW.payload_hash
                       AND resolution_state = 'ADMITTED'
                       AND admitted_homework_id = NEW.id AND consumed_at IS NULL;
                    GET DIAGNOSTICS admitted_count = ROW_COUNT;
                    admitted := admitted_count = 1;
                END IF;
                IF admitted THEN CONTINUE; END IF;
                END IF;
            END IF;

            IF TG_TABLE_NAME = 'homeworks' THEN
                IF barrier_state = 'PENDING'
                    AND authority_transition = 'ARCHIVING'
                    AND TG_OP = 'UPDATE'
                    AND NEW.publication_state = 'ARCHIVED'
                    AND OLD.publication_state IN ('PENDING', 'ACTIVE')
                    AND NEW.semester_id = OLD.semester_id
                    AND archive_event_id IS NOT NULL THEN
                SELECT EXISTS (
                    SELECT 1 FROM academic_semester_archive_effect_receipts receipt
                     WHERE receipt.source_event_id = archive_event_id
                       AND receipt.event_type = 'homework.binding.archived'
                       AND receipt.semester_id = target_semester_id
                       AND receipt.binding_id = OLD.binding_id
                       AND receipt.state = 'PENDING') INTO event_admitted;
                IF event_admitted THEN
                    UPDATE academic_semester_archive_publication_admissions
                       SET consumed_at = now()
                     WHERE operation_id = (SELECT operation_id FROM academic_semester_archive_barriers
                                             WHERE semester_id = target_semester_id)
                       AND state_version = authority_version
                       AND semester_id = target_semester_id
                       AND binding_id = OLD.binding_id AND admitted_homework_id = OLD.id
                       AND consumed_at IS NULL;
                    CONTINUE;
                END IF;
                END IF;
            END IF;

            IF TG_TABLE_NAME = 'homework_binding_archives'
                    AND TG_OP IN ('INSERT', 'UPDATE')
                    AND coalesce(new_semester_id, old_semester_id) = target_semester_id THEN
                SELECT EXISTS (
                    SELECT 1 FROM academic_semester_archive_effect_receipts receipt
                     WHERE receipt.source_event_id = archive_event_id
                       AND receipt.event_type = 'homework.binding.archived'
                       AND receipt.semester_id = target_semester_id
                       AND receipt.binding_id = (coalesce(new_row, old_row) ->> 'binding_id')::BIGINT
                       AND receipt.state = 'PENDING'
                       AND barrier_state = 'PENDING'
                       AND authority_transition = 'ARCHIVING') INTO event_admitted;
                IF NOT event_admitted AND TG_OP <> 'DELETE'
                        AND NEW.homework_id IS NOT NULL THEN
                    SELECT EXISTS (
                        SELECT 1 FROM academic_semester_archive_publication_admissions admission
                         WHERE admission.operation_id = (SELECT operation_id
                             FROM academic_semester_archive_barriers WHERE semester_id = target_semester_id)
                           AND admission.state_version = authority_version
                           AND admission.semester_id = target_semester_id
                           AND admission.binding_id = NEW.binding_id
                           AND admission.actor_id = NEW.actor_id
                           AND admission.request_key = NEW.request_key
                           AND admission.admitted_homework_id = NEW.homework_id
                           AND admission.consumed_at IS NOT NULL
                    ) INTO event_admitted;
                END IF;
                IF event_admitted THEN CONTINUE; END IF;
            END IF;

            IF TG_TABLE_NAME = 'homework_binding_transfer_markers' THEN
                IF TG_OP = 'UPDATE'
                    AND OLD.state = 'PENDING'
                    AND NEW.state = 'CANCELLED_UNPUBLISHED'
                    AND OLD.homework_id IS NULL AND NEW.homework_id IS NULL
                    AND NEW.binding_id = OLD.binding_id
                    AND NEW.actor_id = OLD.actor_id
                    AND NEW.request_key = OLD.request_key
                    AND NEW.binding_payload_hash = OLD.binding_payload_hash
                    AND NEW.source_event_id = OLD.source_event_id
                    AND NEW.batch_index = OLD.batch_index
                    AND NEW.operation_id = OLD.operation_id
                    AND NEW.operation_hash = OLD.operation_hash
                    AND NEW.semester_id = target_semester_id
                    AND archive_event_id IS NOT NULL
                    AND barrier_state = 'PENDING'
                    AND authority_transition = 'ARCHIVING'
                    AND EXISTS (
                        SELECT 1 FROM academic_semester_archive_effect_receipts receipt
                         WHERE receipt.source_event_id = archive_event_id
                           AND receipt.event_type = 'homework.binding.archived'
                           AND receipt.semester_id = target_semester_id
                           AND receipt.binding_id = NEW.binding_id
                           AND receipt.state = 'PENDING')
                    AND EXISTS (
                        SELECT 1 FROM academic_semester_archive_publication_admissions admission
                         WHERE admission.operation_id = (SELECT operation_id
                             FROM academic_semester_archive_barriers WHERE semester_id = target_semester_id)
                           AND admission.state_version = authority_version
                           AND admission.semester_id = target_semester_id
                           AND admission.binding_id = NEW.binding_id
                           AND admission.actor_id = NEW.actor_id
                           AND admission.request_key = NEW.request_key
                           AND admission.payload_hash = NEW.binding_payload_hash
                           AND admission.source_event_id = OLD.source_event_id
                           AND admission.terminal_event_id = archive_event_id
                           AND admission.resolution_state = 'CANCEL_REQUESTED') THEN
                    RETURN NEW;
                END IF;
            END IF;

            IF barrier_state = 'PENDING' AND authority_transition = 'ARCHIVING'
                    AND transfer_event_id IS NOT NULL
                    AND transfer_operation_id IS NOT NULL
                    AND transfer_binding_id IS NOT NULL
                    AND transfer_semester_id = target_semester_id
                    AND transfer_binding_id = (coalesce(new_row, old_row) ->> 'binding_id')::BIGINT
                    AND transfer_operation_hash ~ '^[0-9a-f]{64}$'
                    AND transfer_batch_hash ~ '^[0-9a-f]{64}$'
                    AND transfer_binding_hash ~ '^[0-9a-f]{64}$' THEN
                IF TG_TABLE_NAME = 'homeworks' THEN
                    IF TG_OP = 'UPDATE'
                        AND old_semester_id = target_semester_id
                        AND new_semester_id = target_semester_id
                        AND NEW.id = OLD.id AND NEW.binding_id = OLD.binding_id
                        AND NEW.actor_id = OLD.actor_id AND NEW.request_key = OLD.request_key
                        AND NEW.payload_hash = OLD.payload_hash
                        AND NEW.publication_state = OLD.publication_state
                        AND OLD.binding_id = transfer_binding_id THEN
                        RETURN NEW;
                    END IF;
                END IF;
                IF TG_TABLE_NAME = 'homework_binding_transfer_markers' THEN
                    IF new_semester_id = target_semester_id
                        AND NEW.operation_id = transfer_operation_id
                        AND NEW.source_event_id = transfer_event_id
                        AND NEW.batch_index = transfer_batch_index
                        AND NEW.binding_id = transfer_binding_id
                        AND encode(NEW.operation_hash, 'hex') = transfer_operation_hash
                        AND encode(NEW.binding_payload_hash, 'hex') = transfer_binding_hash
                        AND (TG_OP = 'INSERT' OR (OLD.semester_id = NEW.semester_id
                             AND OLD.binding_id = NEW.binding_id)) THEN
                        RETURN NEW;
                    END IF;
                END IF;
            END IF;

            RAISE EXCEPTION 'Academic semester % is fenced by archive barrier', target_semester_id
                USING ERRCODE = '55000';
        END IF;
    END LOOP;

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER homeworks_semester_archive_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homeworks
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_archive_business_write();
CREATE TRIGGER homework_completions_semester_archive_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_completions
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_archive_business_write();
CREATE TRIGGER homework_binding_archives_semester_archive_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_binding_archives
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_archive_business_write();
CREATE TRIGGER homework_binding_transfer_markers_semester_archive_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_binding_transfer_markers
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_archive_business_write();

CREATE OR REPLACE FUNCTION retain_unproven_archive_effect_outbox_rows()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    -- Academic has no remote-receipt table for Schedule. Local application is
    -- not evidence that the consumer received the ACK, so retain it for
    -- at-least-once relay/recovery instead of inferring delivery from state.
    IF OLD.event_type = 'semester.archive.effect.ack' THEN RETURN NULL; END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER academic_archive_effect_ack_retention
    BEFORE DELETE ON academic_outbox
    FOR EACH ROW EXECUTE FUNCTION retain_unproven_archive_effect_outbox_rows();

-- Retained domain source: V41__final_semester_deletion.sql SHA256 ba02cb8c291604c14b8712e6d78f68175bd9bd8dfe00bd9c199cb2eee55a7078
ALTER TABLE semesters
    ADD COLUMN deletion_phase VARCHAR(16),
    ADD COLUMN transition_operation_id UUID;

ALTER TABLE semesters
    DROP CONSTRAINT semesters_archive_transition_valid,
    DROP CONSTRAINT semesters_archive_state_consistent,
    DROP CONSTRAINT semesters_write_blocked_inactive,
    ADD CONSTRAINT semesters_archive_transition_valid
        CHECK (archive_transition IN ('NONE', 'ARCHIVING', 'RESTORING', 'DELETING')),
    ADD CONSTRAINT semesters_archive_state_consistent
        CHECK ((is_archived AND archive_transition IN ('NONE', 'RESTORING', 'DELETING'))
            OR (NOT is_archived AND archive_transition IN ('NONE', 'ARCHIVING', 'DELETING'))),
    ADD CONSTRAINT semesters_write_blocked_inactive
        CHECK (NOT is_active OR (NOT is_archived AND archive_transition = 'NONE'
                                 AND NOT archive_release_pending)),
    ADD CONSTRAINT semesters_deletion_authority_consistent
        CHECK ((archive_transition = 'DELETING'
                AND deletion_phase IN ('PREPARING', 'RELEASING', 'DELETING')
                AND transition_operation_id IS NOT NULL AND NOT is_active
                AND NOT archive_release_pending)
            OR (archive_transition <> 'DELETING'
                AND deletion_phase IS NULL AND transition_operation_id IS NULL));

ALTER TABLE semester_archive_operations
    ADD COLUMN delete_phase VARCHAR(16),
    ADD COLUMN prior_state VARCHAR(12),
    ADD COLUMN semester_name VARCHAR(128),
    ADD COLUMN original_state_version BIGINT,
    ADD COLUMN preview_digest VARCHAR(64),
    ADD COLUMN academic_participant_digest VARCHAR(64),
    ADD COLUMN schedule_participant_digest VARCHAR(64),
    ADD COLUMN attendance_participant_digest VARCHAR(64),
    ADD COLUMN schedule_templates_count BIGINT,
    ADD COLUMN one_off_lessons_count BIGINT,
    ADD COLUMN lessons_count BIGINT,
    ADD COLUMN assignments_count BIGINT,
    ADD COLUMN homeworks_count BIGINT,
    ADD COLUMN attendance_marks_count BIGINT,
    ADD COLUMN student_requests_count BIGINT,
    ADD COLUMN prepare_expires_at TIMESTAMPTZ,
    ADD COLUMN irreversible_intent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN academic_sealed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN schedule_sealed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN attendance_sealed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN cancel_reason VARCHAR(32),
    DROP CONSTRAINT semester_archive_operations_action_valid,
    DROP CONSTRAINT semester_archive_operations_transition_valid,
    DROP CONSTRAINT semester_archive_operations_academic_status_valid,
    DROP CONSTRAINT semester_archive_operations_schedule_status_valid,
    DROP CONSTRAINT semester_archive_operations_attendance_status_valid,
    ADD CONSTRAINT semester_archive_operations_action_valid
        CHECK (action IN ('ARCHIVE', 'RESTORE', 'DELETE')),
    ADD CONSTRAINT semester_archive_operations_transition_valid
        CHECK (transition IN ('NONE', 'ARCHIVING', 'RESTORING', 'DELETING')),
    ADD CONSTRAINT semester_archive_operations_academic_status_valid
        CHECK (academic_status IN ('NOT_STARTED', 'PENDING', 'READY', 'PREPARED_RESTORE',
                                   'RELEASE_PENDING', 'RELEASED', 'DELETED')),
    ADD CONSTRAINT semester_archive_operations_schedule_status_valid
        CHECK (schedule_status IN ('NOT_STARTED', 'PENDING', 'READY', 'PREPARED_RESTORE',
                                   'RELEASE_PENDING', 'RELEASED', 'DELETED')),
    ADD CONSTRAINT semester_archive_operations_attendance_status_valid
        CHECK (attendance_status IN ('NOT_STARTED', 'PENDING', 'READY', 'PREPARED_RESTORE',
                                     'RELEASE_PENDING', 'RELEASED', 'DELETED')),
    ADD CONSTRAINT semester_archive_delete_shape_valid
        CHECK ((action <> 'DELETE' AND delete_phase IS NULL AND NOT irreversible_intent)
            OR (action = 'DELETE' AND delete_phase IN
                ('PREPARING', 'RELEASING', 'DELETING', 'COMPLETED', 'CANCELLED')
                AND prior_state IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')
                AND semester_name IS NOT NULL AND original_state_version >= 0
                AND preview_digest ~ '^[0-9a-f]{64}$'
                AND academic_participant_digest ~ '^[0-9a-f]{64}$'
                AND schedule_participant_digest ~ '^[0-9a-f]{64}$'
                AND attendance_participant_digest ~ '^[0-9a-f]{64}$'
                AND schedule_templates_count >= 0 AND one_off_lessons_count >= 0
                AND lessons_count >= 0 AND assignments_count >= 0 AND homeworks_count >= 0
                AND attendance_marks_count >= 0 AND student_requests_count >= 0
                AND (irreversible_intent = (delete_phase IN ('DELETING', 'COMPLETED')))));

ALTER TABLE academic_semester_archive_barriers
    DROP CONSTRAINT academic_semester_archive_barrier_state_valid,
    ADD CONSTRAINT academic_semester_archive_barrier_state_valid
        CHECK (participant_state IN ('PENDING', 'READY', 'PREPARED_RESTORE', 'RELEASED',
                                     'DELETE_PREPARING', 'DELETE_SEALED', 'DELETED'));

CREATE OR REPLACE FUNCTION academic_semester_delete_authorized(target_semester_id BIGINT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT target_semester_id > 0
       AND nullif(current_setting('rutcampustrack.semester_delete_operation_id', TRUE), '')::UUID
            = operation.operation_id
       AND nullif(current_setting('rutcampustrack.semester_delete_state_version', TRUE), '')::BIGINT
            = operation.state_version
       AND nullif(current_setting('rutcampustrack.semester_delete_academic_digest', TRUE), '')
            = operation.academic_participant_digest
       AND operation.action = 'DELETE'
       AND operation.semester_id = target_semester_id
       AND operation.delete_phase = 'DELETING'
       AND operation.irreversible_intent
       AND operation.academic_status = 'DELETED'
       AND operation.schedule_status = 'DELETED'
       AND operation.attendance_status = 'DELETED'
       AND barrier.operation_id = operation.operation_id
       AND barrier.semester_id = operation.semester_id
       AND barrier.state_version = operation.state_version
       AND barrier.participant_state IN ('DELETE_SEALED', 'DELETED')
      FROM semester_archive_operations operation
      JOIN academic_semester_archive_barriers barrier
        ON barrier.operation_id = operation.operation_id
       AND barrier.semester_id = operation.semester_id
       AND barrier.state_version = operation.state_version
     WHERE operation.operation_id =
           nullif(current_setting('rutcampustrack.semester_delete_operation_id', TRUE), '')::UUID
       AND operation.semester_id = target_semester_id
$$;

-- An exemption is usable only while the exact durable delete epoch remains unsealed.
CREATE OR REPLACE FUNCTION academic_semester_delete_preparing(target_semester_id BIGINT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS (
        SELECT 1 FROM semesters semester
        JOIN academic_semester_archive_barriers barrier ON barrier.semester_id = semester.id
        JOIN semester_archive_operations operation ON operation.operation_id = barrier.operation_id
         AND operation.semester_id = barrier.semester_id AND operation.state_version = barrier.state_version
        WHERE semester.id = target_semester_id AND semester.archive_transition = 'DELETING'
          AND semester.deletion_phase = 'PREPARING' AND NOT semester.is_active
          AND NOT semester.archive_release_pending
          AND semester.transition_operation_id = barrier.operation_id
          AND semester.state_version = barrier.state_version
          AND barrier.participant_state = 'DELETE_PREPARING'
          AND operation.action = 'DELETE' AND operation.delete_phase = 'PREPARING'
          AND NOT operation.irreversible_intent
    )
$$;

CREATE OR REPLACE FUNCTION guard_academic_semester_deletion_write()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    old_semester_id BIGINT;
    new_semester_id BIGINT;
    affected BIGINT;
    current_transition VARCHAR(10);
    current_archived BOOLEAN;
    barrier_state VARCHAR(20);
    authorized BOOLEAN;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        IF TG_TABLE_NAME = 'homework_completions' THEN
            SELECT semester_id INTO old_semester_id FROM homeworks WHERE id = OLD.homework_id;
        ELSE
            old_semester_id := OLD.semester_id;
        END IF;
    END IF;
    IF TG_OP <> 'DELETE' THEN
        IF TG_TABLE_NAME = 'homework_completions' THEN
            SELECT semester_id INTO new_semester_id FROM homeworks WHERE id = NEW.homework_id;
        ELSE
            new_semester_id := NEW.semester_id;
        END IF;
    END IF;

    FOR affected IN
        SELECT DISTINCT value FROM unnest(ARRAY[old_semester_id, new_semester_id]) AS value
         WHERE value IS NOT NULL ORDER BY value
    LOOP
        PERFORM pg_advisory_xact_lock(5452097, (affected % 2147483647)::INTEGER);
        authorized := TG_OP = 'DELETE' AND academic_semester_delete_authorized(affected);
        IF TG_TABLE_NAME = 'homework_binding_archives' THEN
        IF TG_OP = 'UPDATE' AND OLD.homework_id IS NOT NULL AND NEW.homework_id IS NULL
           AND OLD.binding_id = NEW.binding_id AND OLD.actor_id = NEW.actor_id
           AND OLD.request_key = NEW.request_key AND OLD.archived_at = NEW.archived_at
           AND OLD.semester_id = NEW.semester_id AND OLD.source_event_id IS NOT DISTINCT FROM NEW.source_event_id
           AND academic_semester_delete_authorized(affected) THEN
            authorized := TRUE;
        END IF;
        END IF;
        IF authorized THEN
            CONTINUE;
        END IF;

        SELECT archive_transition, is_archived INTO current_transition, current_archived
          FROM semesters WHERE id = affected;
        SELECT participant_state INTO barrier_state
          FROM academic_semester_archive_barriers WHERE semester_id = affected;
        IF TG_TABLE_NAME IN ('homeworks', 'homework_completions', 'homework_binding_archives')
           AND academic_semester_delete_preparing(affected) THEN
            -- These tables also run guard_academic_semester_archive_business_write,
            -- which admits only an exact captured publication or trusted effect/transfer.
            CONTINUE;
        END IF;
        IF current_transition = 'DELETING' OR current_archived
           OR barrier_state IN ('DELETE_PREPARING', 'DELETE_SEALED', 'DELETED') THEN
            RAISE EXCEPTION 'Academic semester % is fenced by final deletion', affected
                USING ERRCODE = '55000';
        END IF;
    END LOOP;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER a_assignments_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON assignments
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_deletion_write();
CREATE TRIGGER a_teacher_subject_groups_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON teacher_subject_groups
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_deletion_write();
CREATE TRIGGER a_homeworks_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homeworks
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_deletion_write();
CREATE TRIGGER a_homework_completions_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_completions
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_deletion_write();
CREATE TRIGGER a_homework_binding_archives_semester_deletion_fence
    BEFORE UPDATE OR DELETE ON homework_binding_archives
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_deletion_write();

CREATE OR REPLACE FUNCTION protect_assignment_identity()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF academic_semester_delete_authorized(OLD.semester_id) THEN RETURN OLD; END IF;
        RAISE EXCEPTION 'assignments are closed, never deleted';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.teacher_id IS DISTINCT FROM OLD.teacher_id
       OR NEW.subject_id IS DISTINCT FROM OLD.subject_id
       OR NEW.group_id IS DISTINCT FROM OLD.group_id
       OR NEW.semester_id IS DISTINCT FROM OLD.semester_id
       OR NEW.lesson_type IS DISTINCT FROM OLD.lesson_type
       OR NEW.valid_from IS DISTINCT FROM OLD.valid_from
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'assignment identity is immutable';
    END IF;
    IF OLD.valid_until_exclusive IS NOT NULL
       AND (NEW.valid_until_exclusive IS NULL OR NEW.valid_until_exclusive > OLD.valid_until_exclusive) THEN
        RAISE EXCEPTION 'closed assignment validity cannot be extended or reopened';
    END IF;
    IF NEW.valid_until_exclusive IS NOT NULL AND NEW.valid_until_exclusive <= NEW.valid_from THEN
        RAISE EXCEPTION 'assignment close must be after valid_from';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION protect_homework_binding_archive_identity()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.binding_id IS DISTINCT FROM OLD.binding_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.archived_at IS DISTINCT FROM OLD.archived_at THEN
        RAISE EXCEPTION 'homework binding archive identity is immutable';
    END IF;
    IF OLD.homework_id IS NOT NULL AND NEW.homework_id IS DISTINCT FROM OLD.homework_id
       AND NOT (NEW.homework_id IS NULL AND academic_semester_delete_authorized(OLD.semester_id)) THEN
        RAISE EXCEPTION 'homework binding archive content identity is immutable';
    END IF;
    IF OLD.semester_id IS NOT NULL AND NEW.semester_id IS DISTINCT FROM OLD.semester_id THEN
        RAISE EXCEPTION 'homework binding archive semester scope is immutable';
    END IF;
    IF OLD.source_event_id IS NOT NULL AND NEW.source_event_id IS DISTINCT FROM OLD.source_event_id THEN
        RAISE EXCEPTION 'homework binding archive source event is immutable';
    END IF;
    IF NEW.source_event_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM academic_semester_archive_effect_receipts receipt
             WHERE receipt.source_event_id = NEW.source_event_id
               AND receipt.event_type = 'homework.binding.archived'
               AND receipt.semester_id = NEW.semester_id
               AND receipt.binding_id = NEW.binding_id
               AND receipt.state IN ('PENDING', 'APPLIED')) THEN
        RAISE EXCEPTION 'homework binding archive source event has no exact effect receipt';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION guard_academic_semester_delete_authority()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND OLD.archive_transition = 'NONE' AND OLD.deletion_phase IS NULL
       AND OLD.transition_operation_id IS NULL AND NOT OLD.archive_release_pending
       AND NEW.archive_transition = 'DELETING' AND NEW.deletion_phase = 'PREPARING'
       AND NEW.state_version = OLD.state_version + 1
       AND NOT NEW.is_active AND NEW.is_archived = OLD.is_archived
       AND NOT NEW.archive_release_pending
       AND (to_jsonb(NEW) - ARRAY['is_active', 'archive_transition', 'deletion_phase',
                'transition_operation_id', 'state_version'])
         = (to_jsonb(OLD) - ARRAY['is_active', 'archive_transition', 'deletion_phase',
                'transition_operation_id', 'state_version'])
       AND EXISTS (
           SELECT 1 FROM semester_archive_operations operation
           JOIN academic_semester_archive_barriers barrier ON barrier.operation_id = operation.operation_id
            AND barrier.semester_id = operation.semester_id AND barrier.state_version = operation.state_version
           WHERE operation.operation_id = NEW.transition_operation_id
             AND operation.semester_id = OLD.id AND operation.action = 'DELETE'
             AND operation.original_state_version = OLD.state_version
             AND operation.state_version = NEW.state_version AND operation.delete_phase = 'PREPARING'
             AND operation.prior_state = CASE WHEN OLD.is_archived THEN 'ARCHIVED'
                 WHEN OLD.is_active THEN 'ACTIVE' ELSE 'INACTIVE' END
             AND NOT operation.irreversible_intent AND barrier.participant_state = 'DELETE_PREPARING') THEN
        PERFORM pg_advisory_xact_lock(5452097, (OLD.id % 2147483647)::INTEGER);
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE'
       AND OLD.archive_transition = 'DELETING' AND NEW.archive_transition = 'DELETING'
       AND OLD.deletion_phase = 'PREPARING' AND NEW.deletion_phase IN ('RELEASING', 'DELETING')
       AND (to_jsonb(NEW) - 'deletion_phase') = (to_jsonb(OLD) - 'deletion_phase')
       AND EXISTS (
           SELECT 1 FROM semester_archive_operations operation
           JOIN academic_semester_archive_barriers barrier ON barrier.operation_id = operation.operation_id
            AND barrier.semester_id = operation.semester_id AND barrier.state_version = operation.state_version
           WHERE operation.operation_id = OLD.transition_operation_id
             AND operation.semester_id = OLD.id AND operation.action = 'DELETE'
             AND operation.state_version = OLD.state_version AND operation.delete_phase = NEW.deletion_phase
             AND ((NEW.deletion_phase = 'RELEASING' AND NOT operation.irreversible_intent
                   AND barrier.participant_state IN ('DELETE_PREPARING', 'DELETE_SEALED'))
               OR (NEW.deletion_phase = 'DELETING' AND operation.irreversible_intent
                   AND operation.academic_sealed AND operation.schedule_sealed AND operation.attendance_sealed
                   AND operation.academic_status = 'READY' AND operation.schedule_status = 'READY'
                   AND operation.attendance_status = 'READY' AND barrier.participant_state = 'DELETE_SEALED'))) THEN
        PERFORM pg_advisory_xact_lock(5452097, (OLD.id % 2147483647)::INTEGER);
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE'
       AND OLD.archive_transition = 'DELETING'
       AND OLD.deletion_phase = 'RELEASING'
       AND NEW.archive_transition = 'NONE'
       AND NEW.deletion_phase IS NULL AND NEW.transition_operation_id IS NULL
       AND NEW.state_version = OLD.state_version + 1
       AND NEW.is_active = (SELECT prior_state = 'ACTIVE'
                              FROM semester_archive_operations
                             WHERE operation_id = OLD.transition_operation_id
                               AND semester_id = OLD.id AND action = 'DELETE'
                               AND state_version = OLD.state_version
                               AND delete_phase = 'RELEASING' AND NOT irreversible_intent
                               AND academic_status = 'RELEASED' AND schedule_status = 'RELEASED'
                               AND attendance_status = 'RELEASED')
       AND NEW.is_archived = (SELECT prior_state = 'ARCHIVED'
                                FROM semester_archive_operations
                               WHERE operation_id = OLD.transition_operation_id
                                 AND semester_id = OLD.id AND action = 'DELETE'
                                 AND state_version = OLD.state_version
                                 AND delete_phase = 'RELEASING' AND NOT irreversible_intent
                                 AND academic_status = 'RELEASED' AND schedule_status = 'RELEASED'
                                 AND attendance_status = 'RELEASED')
       AND NOT NEW.archive_release_pending
       AND NEW.name IS NOT DISTINCT FROM OLD.name
       AND NEW.date_from IS NOT DISTINCT FROM OLD.date_from
       AND NEW.date_to IS NOT DISTINCT FROM OLD.date_to
       AND NEW.first_week_type IS NOT DISTINCT FROM OLD.first_week_type
       AND NEW.semester_type IS NOT DISTINCT FROM OLD.semester_type
       AND NEW.academic_year IS NOT DISTINCT FROM OLD.academic_year
       AND EXISTS (SELECT 1 FROM semester_archive_operations operation
                    JOIN academic_semester_archive_barriers barrier
                      ON barrier.operation_id = operation.operation_id
                     AND barrier.semester_id = operation.semester_id
                     AND barrier.state_version = operation.state_version
                   WHERE operation.operation_id = OLD.transition_operation_id
                     AND operation.semester_id = OLD.id AND operation.action = 'DELETE'
                     AND operation.state_version = OLD.state_version
                     AND operation.delete_phase = 'RELEASING' AND NOT operation.irreversible_intent
                     AND operation.academic_status = 'RELEASED'
                     AND operation.schedule_status = 'RELEASED'
                     AND operation.attendance_status = 'RELEASED'
                     AND barrier.participant_state = 'RELEASED') THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE'
       AND OLD.archive_transition = 'DELETING'
       AND NEW.archive_transition = 'NONE'
       AND NEW.deletion_phase IS NULL AND NEW.transition_operation_id IS NULL
       AND NOT NEW.is_active AND NOT NEW.is_archived
       AND academic_semester_delete_authorized(OLD.id) THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'DELETE'
       AND academic_semester_delete_authorized(OLD.id)
       AND NOT EXISTS (SELECT 1 FROM assignments WHERE semester_id = OLD.id)
       AND NOT EXISTS (SELECT 1 FROM homeworks WHERE semester_id = OLD.id) THEN
        RETURN OLD;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.archive_transition = 'DELETING' OR NEW.archive_transition = 'DELETING'
           OR OLD.deletion_phase IS NOT NULL OR NEW.deletion_phase IS NOT NULL
           OR OLD.transition_operation_id IS NOT NULL OR NEW.transition_operation_id IS NOT NULL THEN
            RAISE EXCEPTION 'Academic semester deletion authority is immutable outside its exact operation'
                USING ERRCODE = '55000';
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Academic semester cannot be deleted without all exact participant receipts'
        USING ERRCODE = '55000';
END
$$;

CREATE TRIGGER semesters_deletion_authority_guard
    BEFORE UPDATE OR DELETE ON semesters
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_delete_authority();

CREATE OR REPLACE FUNCTION guard_academic_semester_archive_business_write()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    old_row JSONB;
    new_row JSONB;
    old_semester_id BIGINT;
    new_semester_id BIGINT;
    target_semester_id BIGINT;
    barrier_state VARCHAR(20);
    authority_transition VARCHAR(10);
    authority_version BIGINT;
    authority_archived BOOLEAN;
    authority_release_pending BOOLEAN;
    authority_active BOOLEAN;
    authority_blocked BOOLEAN;
    archive_event_id UUID;
    request_key_text TEXT;
    transfer_event_id UUID;
    transfer_operation_id UUID;
    transfer_batch_index INTEGER;
    transfer_binding_id BIGINT;
    transfer_semester_id BIGINT;
    transfer_operation_hash TEXT;
    transfer_batch_hash TEXT;
    transfer_binding_hash TEXT;
    admitted BOOLEAN := FALSE;
    admitted_count INTEGER := 0;
    event_admitted BOOLEAN := FALSE;
BEGIN
    IF TG_OP <> 'INSERT' THEN old_row := to_jsonb(OLD); END IF;
    IF TG_OP <> 'DELETE' THEN new_row := to_jsonb(NEW); END IF;

    IF TG_TABLE_NAME = 'homework_completions' THEN
        IF old_row IS NOT NULL THEN
            SELECT semester_id INTO old_semester_id FROM homeworks
             WHERE id = (old_row ->> 'homework_id')::BIGINT;
        END IF;
        IF new_row IS NOT NULL THEN
            SELECT semester_id INTO new_semester_id FROM homeworks
             WHERE id = (new_row ->> 'homework_id')::BIGINT;
        END IF;
    ELSE
        old_semester_id := nullif(old_row ->> 'semester_id', '')::BIGINT;
        new_semester_id := nullif(new_row ->> 'semester_id', '')::BIGINT;
    END IF;

    IF old_semester_id IS NULL AND new_semester_id IS NULL THEN
        IF EXISTS (SELECT 1 FROM academic_semester_archive_barriers
                    WHERE participant_state IN ('PENDING', 'READY', 'PREPARED_RESTORE', 'DELETE_PREPARING', 'DELETE_SEALED', 'DELETED')) THEN
            RAISE EXCEPTION 'Academic archive fence cannot resolve the affected semester'
                USING ERRCODE = '55000';
        END IF;
        IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
        RETURN NEW;
    END IF;

    archive_event_id := nullif(current_setting('rutcampustrack.archive_effect_event_id', TRUE), '')::UUID;
    request_key_text := nullif(current_setting('rutcampustrack.archive_publication_request_key', TRUE), '');
    transfer_event_id := nullif(current_setting('rutcampustrack.archive_transfer_event_id', TRUE), '')::UUID;
    transfer_operation_id := nullif(current_setting('rutcampustrack.archive_transfer_operation_id', TRUE), '')::UUID;
    transfer_batch_index := nullif(current_setting('rutcampustrack.archive_transfer_batch_index', TRUE), '')::INTEGER;
    transfer_binding_id := nullif(current_setting('rutcampustrack.archive_transfer_binding_id', TRUE), '')::BIGINT;
    transfer_semester_id := nullif(current_setting('rutcampustrack.archive_transfer_semester_id', TRUE), '')::BIGINT;
    transfer_operation_hash := nullif(current_setting('rutcampustrack.archive_transfer_operation_hash', TRUE), '');
    transfer_batch_hash := nullif(current_setting('rutcampustrack.archive_transfer_batch_hash', TRUE), '');
    transfer_binding_hash := nullif(current_setting('rutcampustrack.archive_transfer_binding_hash', TRUE), '');

    FOR target_semester_id IN
        SELECT DISTINCT candidate
          FROM unnest(ARRAY[old_semester_id, new_semester_id]) AS candidate
         WHERE candidate IS NOT NULL
         ORDER BY candidate
    LOOP
        admitted := FALSE;
        admitted_count := 0;
        event_admitted := FALSE;
        PERFORM pg_advisory_xact_lock(5452097, (target_semester_id % 2147483647)::INTEGER);
        SELECT participant_state INTO barrier_state
          FROM academic_semester_archive_barriers
         WHERE semester_id = target_semester_id;
        SELECT is_active, is_archived, archive_transition, archive_release_pending, state_version
          INTO authority_active, authority_archived, authority_transition,
               authority_release_pending, authority_version
          FROM semesters WHERE id = target_semester_id;
        authority_blocked := NOT FOUND OR authority_archived OR authority_release_pending
                OR authority_transition <> 'NONE';

        IF TG_OP = 'DELETE' AND academic_semester_delete_authorized(target_semester_id) THEN
            CONTINUE;
        END IF;
        IF TG_TABLE_NAME = 'homework_binding_archives' THEN
        IF TG_OP = 'UPDATE' AND OLD.homework_id IS NOT NULL AND NEW.homework_id IS NULL
           AND (to_jsonb(NEW) - 'homework_id') = (to_jsonb(OLD) - 'homework_id')
           AND academic_semester_delete_authorized(target_semester_id) THEN
            CONTINUE;
        END IF;
        END IF;

        IF barrier_state IN ('PENDING', 'READY', 'PREPARED_RESTORE', 'DELETE_PREPARING', 'DELETE_SEALED', 'DELETED') OR authority_blocked THEN
            IF TG_TABLE_NAME = 'homeworks' THEN
                IF ((barrier_state = 'PENDING' AND authority_transition = 'ARCHIVING')
                    OR academic_semester_delete_preparing(target_semester_id))
                    AND authority_version = (SELECT state_version FROM academic_semester_archive_barriers
                                              WHERE semester_id = target_semester_id)
                    AND request_key_text IS NOT NULL THEN
                IF TG_OP = 'INSERT' AND NEW.publication_state = 'PENDING'
                        AND NEW.semester_id = target_semester_id
                        AND NEW.request_key::TEXT = request_key_text THEN
                    UPDATE academic_semester_archive_publication_admissions
                       SET admitted_homework_id = NEW.id
                     WHERE operation_id = (SELECT operation_id FROM academic_semester_archive_barriers
                                             WHERE semester_id = target_semester_id)
                       AND state_version = authority_version
                       AND semester_id = target_semester_id
                       AND binding_id = NEW.binding_id AND actor_id = NEW.actor_id
                       AND request_key = NEW.request_key AND payload_hash = NEW.payload_hash
                       AND resolution_state = 'ADMITTED'
                       AND admitted_homework_id IS NULL AND consumed_at IS NULL;
                    GET DIAGNOSTICS admitted_count = ROW_COUNT;
                    admitted := admitted_count = 1;
                ELSIF TG_OP = 'INSERT' AND NEW.publication_state = 'ARCHIVED'
                        AND NEW.semester_id = target_semester_id
                        AND NEW.request_key::TEXT = request_key_text THEN
                    UPDATE academic_semester_archive_publication_admissions admission
                       SET admitted_homework_id = NEW.id, consumed_at = now()
                     WHERE admission.operation_id = (SELECT operation_id
                         FROM academic_semester_archive_barriers WHERE semester_id = target_semester_id)
                       AND admission.state_version = authority_version
                       AND admission.semester_id = target_semester_id
                       AND admission.binding_id = NEW.binding_id AND admission.actor_id = NEW.actor_id
                       AND admission.request_key = NEW.request_key
                       AND admission.payload_hash = NEW.payload_hash
                       AND admission.admitted_homework_id IS NULL AND admission.consumed_at IS NULL
                       AND EXISTS (
                           SELECT 1 FROM homework_binding_archives marker
                           JOIN academic_semester_archive_effect_receipts receipt
                             ON receipt.source_event_id = marker.source_event_id
                          WHERE marker.binding_id = NEW.binding_id AND marker.actor_id = NEW.actor_id
                            AND marker.request_key = NEW.request_key
                            AND marker.semester_id = target_semester_id
                            AND marker.source_event_id IS NOT NULL AND receipt.result = 'APPLIED'
                            AND receipt.semester_id = target_semester_id
                            AND receipt.binding_id = NEW.binding_id);
                    GET DIAGNOSTICS admitted_count = ROW_COUNT;
                    admitted := admitted_count = 1;
                ELSIF TG_OP = 'UPDATE' AND OLD.publication_state = 'PENDING'
                        AND NEW.publication_state = 'ACTIVE'
                        AND NEW.id = OLD.id AND NEW.semester_id = OLD.semester_id
                        AND NEW.binding_id = OLD.binding_id AND NEW.actor_id = OLD.actor_id
                        AND NEW.request_key = OLD.request_key AND NEW.payload_hash = OLD.payload_hash
                        AND NEW.request_key::TEXT = request_key_text THEN
                    UPDATE academic_semester_archive_publication_admissions
                       SET consumed_at = now()
                     WHERE operation_id = (SELECT operation_id FROM academic_semester_archive_barriers
                                             WHERE semester_id = target_semester_id)
                       AND state_version = authority_version
                       AND semester_id = target_semester_id
                       AND binding_id = NEW.binding_id AND actor_id = NEW.actor_id
                       AND request_key = NEW.request_key AND payload_hash = NEW.payload_hash
                       AND resolution_state = 'ADMITTED'
                       AND admitted_homework_id = NEW.id AND consumed_at IS NULL;
                    GET DIAGNOSTICS admitted_count = ROW_COUNT;
                    admitted := admitted_count = 1;
                END IF;
                IF admitted THEN CONTINUE; END IF;
                END IF;
            END IF;

            IF TG_TABLE_NAME = 'homeworks' THEN
                IF ((barrier_state = 'PENDING' AND authority_transition = 'ARCHIVING')
                    OR academic_semester_delete_preparing(target_semester_id))
                    AND TG_OP = 'UPDATE'
                    AND NEW.publication_state = 'ARCHIVED'
                    AND OLD.publication_state IN ('PENDING', 'ACTIVE')
                    AND NEW.semester_id = OLD.semester_id
                    AND archive_event_id IS NOT NULL THEN
                SELECT EXISTS (
                    SELECT 1 FROM academic_semester_archive_effect_receipts receipt
                     WHERE receipt.source_event_id = archive_event_id
                       AND receipt.event_type = 'homework.binding.archived'
                       AND receipt.semester_id = target_semester_id
                       AND receipt.binding_id = OLD.binding_id
                       AND receipt.state = 'PENDING') INTO event_admitted;
                IF event_admitted THEN
                    UPDATE academic_semester_archive_publication_admissions
                       SET consumed_at = now()
                     WHERE operation_id = (SELECT operation_id FROM academic_semester_archive_barriers
                                             WHERE semester_id = target_semester_id)
                       AND state_version = authority_version
                       AND semester_id = target_semester_id
                       AND binding_id = OLD.binding_id AND admitted_homework_id = OLD.id
                       AND consumed_at IS NULL;
                    CONTINUE;
                END IF;
                END IF;
            END IF;

            IF TG_TABLE_NAME = 'homework_binding_archives'
                    AND TG_OP IN ('INSERT', 'UPDATE')
                    AND coalesce(new_semester_id, old_semester_id) = target_semester_id THEN
                SELECT EXISTS (
                    SELECT 1 FROM academic_semester_archive_effect_receipts receipt
                     WHERE receipt.source_event_id = archive_event_id
                       AND receipt.event_type = 'homework.binding.archived'
                       AND receipt.semester_id = target_semester_id
                       AND receipt.binding_id = (coalesce(new_row, old_row) ->> 'binding_id')::BIGINT
                       AND receipt.state = 'PENDING'
                       AND ((barrier_state = 'PENDING' AND authority_transition = 'ARCHIVING')
                    OR academic_semester_delete_preparing(target_semester_id))) INTO event_admitted;
                IF NOT event_admitted AND TG_OP <> 'DELETE'
                        AND NEW.homework_id IS NOT NULL THEN
                    SELECT EXISTS (
                        SELECT 1 FROM academic_semester_archive_publication_admissions admission
                         WHERE admission.operation_id = (SELECT operation_id
                             FROM academic_semester_archive_barriers WHERE semester_id = target_semester_id)
                           AND admission.state_version = authority_version
                           AND admission.semester_id = target_semester_id
                           AND admission.binding_id = NEW.binding_id
                           AND admission.actor_id = NEW.actor_id
                           AND admission.request_key = NEW.request_key
                           AND admission.admitted_homework_id = NEW.homework_id
                           AND admission.consumed_at IS NOT NULL
                    ) INTO event_admitted;
                END IF;
                IF event_admitted THEN CONTINUE; END IF;
            END IF;

            IF TG_TABLE_NAME = 'homework_binding_transfer_markers' THEN
                IF TG_OP = 'UPDATE'
                    AND OLD.state = 'PENDING'
                    AND NEW.state = 'CANCELLED_UNPUBLISHED'
                    AND OLD.homework_id IS NULL AND NEW.homework_id IS NULL
                    AND NEW.binding_id = OLD.binding_id
                    AND NEW.actor_id = OLD.actor_id
                    AND NEW.request_key = OLD.request_key
                    AND NEW.binding_payload_hash = OLD.binding_payload_hash
                    AND NEW.source_event_id = OLD.source_event_id
                    AND NEW.batch_index = OLD.batch_index
                    AND NEW.operation_id = OLD.operation_id
                    AND NEW.operation_hash = OLD.operation_hash
                    AND NEW.semester_id = target_semester_id
                    AND archive_event_id IS NOT NULL
                    AND ((barrier_state = 'PENDING' AND authority_transition = 'ARCHIVING')
                    OR academic_semester_delete_preparing(target_semester_id))
                    AND EXISTS (
                        SELECT 1 FROM academic_semester_archive_effect_receipts receipt
                         WHERE receipt.source_event_id = archive_event_id
                           AND receipt.event_type = 'homework.binding.archived'
                           AND receipt.semester_id = target_semester_id
                           AND receipt.binding_id = NEW.binding_id
                           AND receipt.state = 'PENDING')
                    AND EXISTS (
                        SELECT 1 FROM academic_semester_archive_publication_admissions admission
                         WHERE admission.operation_id = (SELECT operation_id
                             FROM academic_semester_archive_barriers WHERE semester_id = target_semester_id)
                           AND admission.state_version = authority_version
                           AND admission.semester_id = target_semester_id
                           AND admission.binding_id = NEW.binding_id
                           AND admission.actor_id = NEW.actor_id
                           AND admission.request_key = NEW.request_key
                           AND admission.payload_hash = NEW.binding_payload_hash
                           AND admission.source_event_id = OLD.source_event_id
                           AND admission.terminal_event_id = archive_event_id
                           AND admission.resolution_state = 'CANCEL_REQUESTED') THEN
                    RETURN NEW;
                END IF;
            END IF;

            IF ((barrier_state = 'PENDING' AND authority_transition = 'ARCHIVING')
                    OR academic_semester_delete_preparing(target_semester_id))
                    AND transfer_event_id IS NOT NULL
                    AND transfer_operation_id IS NOT NULL
                    AND transfer_binding_id IS NOT NULL
                    AND transfer_semester_id = target_semester_id
                    AND transfer_binding_id = (coalesce(new_row, old_row) ->> 'binding_id')::BIGINT
                    AND transfer_operation_hash ~ '^[0-9a-f]{64}$'
                    AND transfer_batch_hash ~ '^[0-9a-f]{64}$'
                    AND transfer_binding_hash ~ '^[0-9a-f]{64}$' THEN
                IF TG_TABLE_NAME = 'homeworks' THEN
                    IF TG_OP = 'UPDATE'
                        AND old_semester_id = target_semester_id
                        AND new_semester_id = target_semester_id
                        AND NEW.id = OLD.id AND NEW.binding_id = OLD.binding_id
                        AND NEW.actor_id = OLD.actor_id AND NEW.request_key = OLD.request_key
                        AND NEW.payload_hash = OLD.payload_hash
                        AND NEW.publication_state = OLD.publication_state
                        AND OLD.binding_id = transfer_binding_id THEN
                        RETURN NEW;
                    END IF;
                END IF;
                IF TG_TABLE_NAME = 'homework_binding_transfer_markers' THEN
                    IF new_semester_id = target_semester_id
                        AND NEW.operation_id = transfer_operation_id
                        AND NEW.source_event_id = transfer_event_id
                        AND NEW.batch_index = transfer_batch_index
                        AND NEW.binding_id = transfer_binding_id
                        AND encode(NEW.operation_hash, 'hex') = transfer_operation_hash
                        AND encode(NEW.binding_payload_hash, 'hex') = transfer_binding_hash
                        AND (TG_OP = 'INSERT' OR (OLD.semester_id = NEW.semester_id
                             AND OLD.binding_id = NEW.binding_id)) THEN
                        RETURN NEW;
                    END IF;
                END IF;
            END IF;

            RAISE EXCEPTION 'Academic semester % is fenced by archive barrier', target_semester_id
                USING ERRCODE = '55000';
        END IF;
    END LOOP;

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;


-- Retained domain source: V43__campus_map_final_deletion.sql SHA256 17d02257e71c6d749dc17e86ada7b2962fdf71a79ac80a7b7c8ef09000102b0f
-- Owner decision 2026-10-02: final deletion of a floor scheme and all its versions/files.
-- No delete runs here. Commands remain password-confirmed, preview-bound and atomic.
CREATE TABLE campus_map_deletion_receipt (
    operation_id UUID PRIMARY KEY,
    owner_id BIGINT NOT NULL CHECK (owner_id > 0),
    target_type VARCHAR(16) NOT NULL CHECK (target_type IN ('FLOOR', 'BUILDING')),
    target_id BIGINT NOT NULL CHECK (target_id > 0),
    preview_digest VARCHAR(64) NOT NULL CHECK (preview_digest ~ '^[0-9a-f]{64}$'),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE FUNCTION campus_map_final_delete_allowed(target_floor_id BIGINT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT COALESCE(current_setting('rutcampustrack.map_delete_floor_id', true) = target_floor_id::text, FALSE)
       AND EXISTS (SELECT 1 FROM campus_map_floor
                   WHERE id = target_floor_id AND current_version_id IS NULL)
$$;


CREATE OR REPLACE FUNCTION protect_campus_map_asset()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND campus_map_final_delete_allowed((SELECT floor_id FROM campus_map_plan_version WHERE id = OLD.plan_version_id)) THEN
        RETURN OLD;
    END IF;

    RAISE EXCEPTION 'campus map assets are immutable and retained';
END
$$;

CREATE OR REPLACE FUNCTION protect_campus_map_open_intent()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND campus_map_final_delete_allowed(OLD.floor_id) THEN
        RETURN OLD;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF NEW.owner_hmac IS DISTINCT FROM OLD.owner_hmac
           OR NEW.intent_id IS DISTINCT FROM OLD.intent_id
           OR NEW.building_id IS DISTINCT FROM OLD.building_id
           OR NEW.floor_id IS DISTINCT FROM OLD.floor_id
           OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash
           OR NEW.accepted_utc_day IS DISTINCT FROM OLD.accepted_utc_day
           OR NEW.accepted_at IS DISTINCT FROM OLD.accepted_at
           OR NEW.expires_at IS DISTINCT FROM OLD.expires_at THEN
            RAISE EXCEPTION 'campus map open intent identity is immutable';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.expires_at > CURRENT_TIMESTAMP THEN
        RAISE EXCEPTION 'campus map open intent cannot be removed before expiry';
    END IF;
    RETURN OLD;
END
$$;

CREATE OR REPLACE FUNCTION validate_campus_map_ready_asset()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    asset_record       campus_map_asset%ROWTYPE;
    plan_version_id    BIGINT;
    plan_published_at  TIMESTAMPTZ;
BEGIN
    IF TG_OP = 'DELETE' AND campus_map_final_delete_allowed((SELECT floor_id FROM campus_map_plan_version WHERE id = OLD.plan_version_id)) THEN
        RETURN OLD;
    END IF;

    plan_version_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.plan_version_id ELSE NEW.plan_version_id END;

    SELECT published_at INTO plan_published_at
    FROM campus_map_plan_version
    WHERE id = plan_version_id
    FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'campus map plan version % does not exist', plan_version_id;
    END IF;

    IF TG_OP = 'UPDATE'
       AND (NEW.plan_version_id IS DISTINCT FROM OLD.plan_version_id
            OR NEW.format IS DISTINCT FROM OLD.format) THEN
        RAISE EXCEPTION 'campus map plan format identity is immutable';
    END IF;
    IF plan_published_at IS NOT NULL THEN
        RAISE EXCEPTION 'published campus map plan format is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    IF NEW.state = 'READY' THEN
        SELECT asset.* INTO asset_record
        FROM campus_map_asset AS asset
        WHERE asset.id = NEW.asset_id
          AND asset.plan_version_id = NEW.plan_version_id
          AND asset.format = NEW.format;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'ready campus map format requires a matching immutable asset';
        END IF;
        IF asset_record.bytes <> NEW.bytes
           OR asset_record.sha256 IS DISTINCT FROM NEW.sha256
           OR asset_record.content_type <> NEW.content_type
           OR asset_record.width IS DISTINCT FROM NEW.width
           OR asset_record.height IS DISTINCT FROM NEW.height THEN
            RAISE EXCEPTION 'ready campus map format metadata does not match its asset';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION protect_campus_map_demand_dedupe()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND campus_map_final_delete_allowed(OLD.floor_id) THEN
        RETURN OLD;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'campus map demand dedupe is immutable';
    END IF;

    -- Retain the daily key through the end of its UTC day plus 48 hours,
    -- independent of the particular intent acceptance time.
    IF (OLD.utc_day::timestamp + INTERVAL '3 days')
       > (CURRENT_TIMESTAMP AT TIME ZONE 'UTC') THEN
        RAISE EXCEPTION 'campus map demand dedupe cannot be removed before UTC retention';
    END IF;
    RETURN OLD;
END
$$;

CREATE OR REPLACE FUNCTION protect_campus_map_plan_version()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND campus_map_final_delete_allowed(OLD.floor_id) THEN
        RETURN OLD;
    END IF;

    -- A publication update and a draft graph edit both serialize on this row.
    PERFORM 1
    FROM campus_map_plan_version
    WHERE id = OLD.id
    FOR UPDATE;

    IF TG_OP = 'DELETE' THEN
        IF OLD.published_at IS NOT NULL THEN
            RAISE EXCEPTION 'published campus map plan version is immutable';
        END IF;
        RETURN OLD;
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.floor_id IS DISTINCT FROM OLD.floor_id
       OR NEW.version IS DISTINCT FROM OLD.version
       OR NEW.catalog_revision_id IS DISTINCT FROM OLD.catalog_revision_id THEN
        RAISE EXCEPTION 'campus map plan version identity is immutable';
    END IF;

    IF OLD.published_at IS NOT NULL THEN
        IF NEW.published_at IS DISTINCT FROM OLD.published_at
           OR NEW.label IS DISTINCT FROM OLD.label
           OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
            RAISE EXCEPTION 'published campus map plan version is immutable';
        END IF;
    END IF;

    RETURN NEW;
END
$$;

-- Retained domain source: V44__user_archive_receipts.sql SHA256 c42fdbe1c35a2b0d919cfdb725958b489526808b437be126d779b34cb22c5f2d
-- No credential or former authority snapshot: restore never reactivates grants.
CREATE TABLE user_archive_preview (
    preview_digest VARCHAR(64) PRIMARY KEY CHECK (preview_digest ~ '^[0-9a-f]{64}$'),
    owner_id BIGINT NOT NULL REFERENCES academic_profiles(id),
    target_id BIGINT NOT NULL REFERENCES academic_profiles(id),
    academic_digest VARCHAR(64) NOT NULL,
    requires_password BOOLEAN NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX user_archive_preview_expiry ON user_archive_preview(expires_at);
CREATE TABLE user_archive_receipt (
    operation_id UUID PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES academic_profiles(id),
    target_id BIGINT NOT NULL REFERENCES academic_profiles(id),
    action VARCHAR(16) NOT NULL CHECK (action IN ('ARCHIVE', 'RESTORE')),
    preview_digest VARCHAR(64),
    requires_password BOOLEAN NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((action = 'ARCHIVE' AND preview_digest IS NOT NULL AND preview_digest ~ '^[0-9a-f]{64}$')
        OR (action = 'RESTORE' AND preview_digest IS NULL AND NOT requires_password))
);

-- Durable identity APPLY owns these receipts in the same transaction as its effects.
ALTER TABLE user_archive_receipt ADD CONSTRAINT user_archive_receipt_owner_operation_fk
    FOREIGN KEY(operation_id,owner_id) REFERENCES academic_identity_operations(operation_id,actor_id)
    DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE user_archive_receipt ADD CONSTRAINT user_archive_receipt_target_operation_fk
    FOREIGN KEY(operation_id,target_id) REFERENCES academic_identity_operation_targets(operation_id,user_id)
    DEFERRABLE INITIALLY DEFERRED;
CREATE FUNCTION academic_archive_receipt_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ARCHIVE_RECEIPT_IMMUTABLE' USING ERRCODE='55000';
END $$;
CREATE TRIGGER user_archive_receipt_immutable BEFORE UPDATE OR DELETE ON user_archive_receipt
    FOR EACH ROW EXECUTE FUNCTION academic_archive_receipt_immutable();

-- Retained domain source: V45__homework_edit_history.sql SHA256 e84f97ed2e4c0fbf714b607761fc977cf104bde7e65d8f4ddedac9b4627c48ba
ALTER TABLE homeworks ALTER COLUMN lesson_number DROP NOT NULL,
    ADD COLUMN binding_mode VARCHAR(8) NOT NULL DEFAULT 'LESSON',
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    ADD COLUMN create_intent JSONB,
    ADD COLUMN create_intent_kind VARCHAR(32),
    ADD CONSTRAINT homeworks_binding_mode_chk CHECK (
        (binding_mode = 'LESSON' AND lesson_number BETWEEN 1 AND 8)
        OR (binding_mode = 'DATE' AND lesson_number IS NULL)),
    ADD CONSTRAINT homeworks_create_intent_chk CHECK (
        (create_intent IS NULL AND create_intent_kind IS NULL)
        OR (create_intent IS NOT NULL AND create_intent_kind IN ('ORIGINAL_CREATE', 'LEGACY_ACCEPTED_REPLAY')));

CREATE TABLE homework_edit_receipts (
    homework_id BIGINT NOT NULL REFERENCES homeworks(id) ON DELETE CASCADE,
    semester_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    request_key UUID NOT NULL,
    command_hash BYTEA NOT NULL CHECK (octet_length(command_hash) = 32),
    result_revision BIGINT NOT NULL CHECK (result_revision > 0),
    result_snapshot JSONB NOT NULL,
    PRIMARY KEY (homework_id, actor_id, request_key)
);
CREATE TABLE homework_edit_history (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    homework_id BIGINT NOT NULL REFERENCES homeworks(id) ON DELETE CASCADE,
    semester_id BIGINT NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    actor_id BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    action VARCHAR(32) NOT NULL,
    before_snapshot JSONB NOT NULL,
    after_snapshot JSONB NOT NULL,
    UNIQUE (homework_id, revision)
);
CREATE TABLE homework_edit_operations (
    operation_id UUID PRIMARY KEY,
    homework_id BIGINT NOT NULL REFERENCES homeworks(id) ON DELETE CASCADE,
    binding_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    request_key UUID NOT NULL,
    command_hash BYTEA NOT NULL CHECK (octet_length(command_hash) = 32),
    group_id BIGINT NOT NULL, subject_id BIGINT NOT NULL, semester_id BIGINT NOT NULL,
    expected_revision BIGINT NOT NULL,
    expected_binding_revision BIGINT NOT NULL,
    request JSONB NOT NULL,
    before_snapshot JSONB NOT NULL,
    desired_snapshot JSONB NOT NULL,
    target_occurrence_id BIGINT,
    target_lesson_revision BIGINT,
    state VARCHAR(16) NOT NULL DEFAULT 'PREPARED' CHECK (state IN ('PREPARED', 'FINALIZED', 'ACKNOWLEDGED', 'CANCELLED')),
    outcome VARCHAR(24) CHECK (outcome IN ('APPLIED', 'TERMINAL_ABORTED', 'NOT_ACCEPTED')),
    result_snapshot JSONB,
    result_revision BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finalized_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_failure VARCHAR(80),
    UNIQUE (homework_id, actor_id, request_key),
    CONSTRAINT homework_edit_outcome_consistent CHECK (
        (state = 'PREPARED' AND outcome IS NULL AND result_snapshot IS NULL AND result_revision IS NULL AND finalized_at IS NULL)
        OR (state = 'CANCELLED' AND outcome = 'NOT_ACCEPTED' AND result_snapshot IS NULL AND result_revision IS NULL AND finalized_at IS NOT NULL)
        OR (state IN ('FINALIZED','ACKNOWLEDGED') AND outcome IN ('APPLIED','TERMINAL_ABORTED')
            AND result_snapshot IS NOT NULL AND result_revision > 0 AND finalized_at IS NOT NULL))
);
CREATE UNIQUE INDEX homework_one_pending_edit ON homework_edit_operations(homework_id)
    WHERE state IN ('PREPARED', 'FINALIZED');
CREATE TABLE homework_edit_archive_admissions (
    archive_operation_id UUID NOT NULL,
    state_version BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    edit_operation_id UUID NOT NULL REFERENCES homework_edit_operations(operation_id) ON DELETE CASCADE,
    command_hash BYTEA NOT NULL,
    PRIMARY KEY (archive_operation_id, edit_operation_id)
);
ALTER TABLE homework_binding_transfer_markers ADD COLUMN superseded_by_edit_operation_id UUID
    REFERENCES homework_edit_operations(operation_id) ON DELETE RESTRICT;
ALTER TABLE academic_semester_archive_publication_admissions
    DROP CONSTRAINT academic_semester_archive_admission_schedule_identity,
    ADD CONSTRAINT academic_semester_archive_admission_schedule_identity CHECK (
        (schedule_occurrence_id IS NULL AND schedule_revision IS NULL)
        OR (schedule_occurrence_id >= 0 AND schedule_revision > 0));

CREATE OR REPLACE FUNCTION academic_homework_edit_epoch_admitted(edit_id UUID, semester BIGINT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS (SELECT 1 FROM homework_edit_archive_admissions a
        JOIN homework_edit_operations e ON e.operation_id = a.edit_operation_id AND e.command_hash = a.command_hash
        JOIN academic_semester_archive_barriers b ON b.semester_id = a.semester_id
          AND b.operation_id = a.archive_operation_id AND b.state_version = a.state_version
        JOIN semesters s ON s.id = a.semester_id AND s.state_version = a.state_version
        WHERE e.operation_id = edit_id AND e.semester_id = semester AND a.semester_id = semester
          AND ((b.participant_state = 'PENDING' AND s.archive_transition = 'ARCHIVING')
             OR (b.participant_state = 'DELETE_PREPARING' AND academic_semester_delete_preparing(semester))))
$$;

CREATE OR REPLACE FUNCTION guard_homework_edit_business_write()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE row_value JSONB; semester BIGINT; edit_id UUID; blocked BOOLEAN; transfer_id UUID;
BEGIN
    row_value := CASE WHEN TG_OP = 'DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END;
    semester := (row_value ->> 'semester_id')::BIGINT;
    PERFORM pg_advisory_xact_lock(5452097, (semester % 2147483647)::INTEGER);
    IF TG_OP = 'DELETE' AND academic_semester_delete_authorized(semester) THEN RETURN OLD; END IF;
    SELECT NOT EXISTS (SELECT 1 FROM semesters s WHERE s.id = semester AND NOT s.is_archived
            AND s.archive_transition = 'NONE' AND NOT s.archive_release_pending)
        OR EXISTS (SELECT 1 FROM academic_semester_archive_barriers b WHERE b.semester_id = semester
            AND b.participant_state IN ('PENDING','READY','PREPARED_RESTORE','DELETE_PREPARING','DELETE_SEALED','DELETED')) INTO blocked;
    IF NOT blocked THEN
        IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
        RETURN NEW;
    END IF;
    edit_id := CASE WHEN TG_TABLE_NAME = 'homework_edit_operations' THEN (row_value ->> 'operation_id')::UUID
        ELSE nullif(current_setting('rutcampustrack.homework_edit_operation_id', TRUE), '')::UUID END;
    IF academic_homework_edit_epoch_admitted(edit_id, semester) THEN
        IF TG_TABLE_NAME = 'homework_edit_operations' AND TG_OP = 'UPDATE' THEN RETURN NEW; END IF;
        IF TG_OP = 'INSERT' AND EXISTS (SELECT 1 FROM homework_edit_operations e
            WHERE e.operation_id = edit_id AND e.state = 'FINALIZED' AND e.outcome = 'APPLIED'
              AND e.homework_id = (row_value ->> 'homework_id')::BIGINT
              AND e.actor_id = (row_value ->> 'actor_id')::BIGINT
              AND e.result_revision = coalesce((row_value ->> 'result_revision')::BIGINT, (row_value ->> 'revision')::BIGINT)
              AND e.result_snapshot = coalesce(row_value -> 'result_snapshot', row_value -> 'after_snapshot')) THEN RETURN NEW; END IF;
    END IF;
    transfer_id := nullif(current_setting('rutcampustrack.archive_transfer_operation_id', TRUE), '')::UUID;
    IF TG_TABLE_NAME = 'homework_edit_history' AND TG_OP = 'INSERT' AND row_value ->> 'action' = 'LESSON_TRANSFERRED'
       AND transfer_id IS NOT NULL AND nullif(current_setting('rutcampustrack.archive_transfer_semester_id', TRUE), '')::BIGINT = semester
       AND EXISTS (SELECT 1 FROM homeworks h WHERE h.id = (row_value ->> 'homework_id')::BIGINT AND h.semester_id = semester
           AND h.revision = (row_value ->> 'revision')::BIGINT AND h.binding_id = nullif(current_setting('rutcampustrack.archive_transfer_binding_id', TRUE), '')::BIGINT
           AND row_value -> 'after_snapshot' = jsonb_build_object('title', h.title, 'description', h.description, 'link', h.link,
               'bindingMode', h.binding_mode, 'lessonDate', h.lesson_date, 'lessonNumber', h.lesson_number)) THEN RETURN NEW; END IF;
    RAISE EXCEPTION 'homework edit is fenced by semester archive' USING ERRCODE = '55000';
END $$;
CREATE TRIGGER homework_edit_receipt_fence BEFORE INSERT OR UPDATE OR DELETE ON homework_edit_receipts
    FOR EACH ROW EXECUTE FUNCTION guard_homework_edit_business_write();
CREATE TRIGGER homework_edit_history_fence BEFORE INSERT OR UPDATE OR DELETE ON homework_edit_history
    FOR EACH ROW EXECUTE FUNCTION guard_homework_edit_business_write();
CREATE TRIGGER homework_edit_command_fence BEFORE INSERT OR UPDATE OR DELETE ON homework_edit_operations
    FOR EACH ROW EXECUTE FUNCTION guard_homework_edit_business_write();

CREATE OR REPLACE FUNCTION protect_homework_transfer_edit_supersession()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.superseded_by_edit_operation_id IS NOT DISTINCT FROM OLD.superseded_by_edit_operation_id THEN RETURN NEW; END IF;
    IF NEW.superseded_by_edit_operation_id IS NULL AND academic_semester_delete_authorized(OLD.semester_id)
       AND (to_jsonb(NEW) - 'superseded_by_edit_operation_id') = (to_jsonb(OLD) - 'superseded_by_edit_operation_id') THEN RETURN NEW; END IF;
    IF NEW.superseded_by_edit_operation_id IS NULL AND NEW.operation_id IS DISTINCT FROM OLD.operation_id
       AND NEW.source_event_id IS DISTINCT FROM OLD.source_event_id THEN RETURN NEW; END IF;
    IF OLD.superseded_by_edit_operation_id IS NULL AND OLD.state = 'APPLIED'
       AND (to_jsonb(NEW) - 'superseded_by_edit_operation_id') = (to_jsonb(OLD) - 'superseded_by_edit_operation_id')
       AND EXISTS (SELECT 1 FROM homework_edit_operations e WHERE e.operation_id = NEW.superseded_by_edit_operation_id
           AND e.state = 'FINALIZED' AND e.outcome = 'APPLIED' AND e.binding_id = OLD.binding_id
           AND e.homework_id = OLD.homework_id AND e.group_id = OLD.group_id AND e.subject_id = OLD.subject_id
           AND e.semester_id = OLD.semester_id) THEN RETURN NEW; END IF;
    RAISE EXCEPTION 'transfer supersession requires the exact finalized homework edit';
END $$;
CREATE TRIGGER homework_transfer_edit_supersession BEFORE UPDATE ON homework_binding_transfer_markers
    FOR EACH ROW EXECUTE FUNCTION protect_homework_transfer_edit_supersession();

CREATE OR REPLACE FUNCTION retain_homework_edit_evidence()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND academic_semester_delete_authorized(OLD.semester_id) THEN RETURN OLD; END IF;
    RAISE EXCEPTION 'homework edit evidence is append-only';
END $$;
CREATE TRIGGER homework_history_append_only BEFORE UPDATE OR DELETE ON homework_edit_history
    FOR EACH ROW EXECUTE FUNCTION retain_homework_edit_evidence();
CREATE TRIGGER homework_edit_receipt_append_only BEFORE UPDATE OR DELETE ON homework_edit_receipts
    FOR EACH ROW EXECUTE FUNCTION retain_homework_edit_evidence();
CREATE TRIGGER homework_edit_admission_append_only BEFORE UPDATE OR DELETE ON homework_edit_archive_admissions
    FOR EACH ROW EXECUTE FUNCTION retain_homework_edit_evidence();

CREATE OR REPLACE FUNCTION protect_homework_edit_operation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND academic_semester_delete_authorized(OLD.semester_id) THEN RETURN OLD; END IF;
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'homework edit operation is retained'; END IF;
    IF (to_jsonb(NEW) - ARRAY['state','outcome','result_snapshot','result_revision','finalized_at','attempts','next_attempt_at','last_failure'])
       IS DISTINCT FROM
       (to_jsonb(OLD) - ARRAY['state','outcome','result_snapshot','result_revision','finalized_at','attempts','next_attempt_at','last_failure']) THEN
        RAISE EXCEPTION 'homework edit command identity is immutable';
    END IF;
    IF OLD.state IN ('ACKNOWLEDGED', 'CANCELLED') THEN RAISE EXCEPTION 'homework edit outcome is terminal'; END IF;
    IF OLD.state = 'FINALIZED' AND (NEW.state NOT IN ('FINALIZED', 'ACKNOWLEDGED')
       OR NEW.outcome IS DISTINCT FROM OLD.outcome OR NEW.result_snapshot IS DISTINCT FROM OLD.result_snapshot
       OR NEW.result_revision IS DISTINCT FROM OLD.result_revision OR NEW.finalized_at IS DISTINCT FROM OLD.finalized_at) THEN
        RAISE EXCEPTION 'finalized homework edit result is immutable';
    END IF;
    IF OLD.state = 'PREPARED' AND NEW.state NOT IN ('PREPARED', 'FINALIZED', 'CANCELLED') THEN
        RAISE EXCEPTION 'invalid homework edit transition';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER homework_edit_operation_guard BEFORE UPDATE OR DELETE ON homework_edit_operations
    FOR EACH ROW EXECUTE FUNCTION protect_homework_edit_operation();

-- Retain the original immutable create/binding identity; freeze legacy accepted replay once.
CREATE OR REPLACE FUNCTION protect_homework_binding_identity()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.binding_id IS DISTINCT FROM OLD.binding_id OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash
       OR NEW.published_by IS DISTINCT FROM OLD.published_by OR NEW.group_id IS DISTINCT FROM OLD.group_id
       OR NEW.subject_id IS DISTINCT FROM OLD.subject_id OR NEW.semester_id IS DISTINCT FROM OLD.semester_id THEN
        RAISE EXCEPTION 'homework publication identity is immutable';
    END IF;
    IF OLD.create_intent IS NOT NULL AND (NEW.create_intent IS DISTINCT FROM OLD.create_intent
       OR NEW.create_intent_kind IS DISTINCT FROM OLD.create_intent_kind) THEN
        RAISE EXCEPTION 'homework accepted create replay intent is immutable';
    END IF;
    IF OLD.create_intent IS NULL AND NEW.create_intent IS NOT NULL THEN
        IF NEW.create_intent_kind <> 'LEGACY_ACCEPTED_REPLAY'
           OR NEW.create_intent IS DISTINCT FROM jsonb_build_object(
                'groupId', OLD.group_id, 'subjectId', OLD.subject_id, 'semesterId', OLD.semester_id,
                'title', OLD.title, 'description', OLD.description, 'link', OLD.link,
                'bindingMode', OLD.binding_mode, 'lessonDate', OLD.lesson_date, 'lessonNumber', OLD.lesson_number)
           OR NOT EXISTS (SELECT 1 FROM homework_edit_receipts receipt
                WHERE receipt.homework_id = OLD.id AND receipt.result_revision = NEW.revision) THEN
            RAISE EXCEPTION 'legacy accepted replay requires its exact pre-edit fingerprint and receipt';
        END IF;
    END IF;
    IF OLD.publication_state = 'ACTIVE' AND NEW.publication_state NOT IN ('ACTIVE', 'ARCHIVED') THEN
        RAISE EXCEPTION 'active homework publication cannot be reverted to pending';
    END IF;
    IF OLD.publication_state = 'ARCHIVED' AND (NEW.publication_state <> 'ARCHIVED'
       OR NEW.title IS DISTINCT FROM OLD.title OR NEW.description IS DISTINCT FROM OLD.description
       OR NEW.link IS DISTINCT FROM OLD.link OR NEW.lesson_date IS DISTINCT FROM OLD.lesson_date
       OR NEW.lesson_number IS DISTINCT FROM OLD.lesson_number OR NEW.binding_mode IS DISTINCT FROM OLD.binding_mode
       OR NEW.revision IS DISTINCT FROM OLD.revision) THEN
        RAISE EXCEPTION 'archived homework is terminal and read-only';
    END IF;
    IF NEW.revision < OLD.revision THEN RAISE EXCEPTION 'homework revision cannot move backwards'; END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION academic_homework_edit_continuation_admitted(old_row JSONB, new_row JSONB, semester BIGINT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS (SELECT 1 FROM homework_edit_operations edit
        JOIN homework_edit_archive_admissions admitted ON admitted.edit_operation_id = edit.operation_id
        JOIN academic_semester_archive_barriers barrier
          ON barrier.operation_id = admitted.archive_operation_id AND barrier.state_version = admitted.state_version
         AND barrier.semester_id = admitted.semester_id
        WHERE edit.operation_id = nullif(current_setting('rutcampustrack.homework_edit_operation_id', TRUE), '')::UUID
          AND edit.homework_id = (old_row ->> 'id')::BIGINT AND edit.semester_id = semester
          AND edit.command_hash = admitted.command_hash AND edit.state = 'FINALIZED' AND edit.outcome = 'APPLIED'
          AND academic_homework_edit_epoch_admitted(edit.operation_id, semester)
          AND barrier.participant_state IN ('PENDING', 'DELETE_PREPARING')
          AND old_row ->> 'publication_state' = 'ACTIVE' AND new_row ->> 'publication_state' = 'ACTIVE'
          AND (old_row ->> 'revision')::BIGINT = edit.expected_revision
          AND (new_row ->> 'revision')::BIGINT = edit.result_revision
          AND edit.result_snapshot = jsonb_build_object('title', new_row -> 'title', 'description', new_row -> 'description',
              'link', new_row -> 'link', 'bindingMode', new_row -> 'binding_mode',
              'lessonDate', new_row -> 'lesson_date', 'lessonNumber', new_row -> 'lesson_number')
          AND (old_row - ARRAY['title','description','link','lesson_date','lesson_number','binding_mode','revision',
                              'updated_at','due_reminder_sent_at','create_intent','create_intent_kind'])
              = (new_row - ARRAY['title','description','link','lesson_date','lesson_number','binding_mode','revision',
                                'updated_at','due_reminder_sent_at','create_intent','create_intent_kind']))
$$;

DO $$ DECLARE fn TEXT; updated TEXT;
BEGIN
    SELECT pg_get_functiondef('guard_academic_semester_archive_business_write()'::regprocedure) INTO fn;
    updated := replace(fn,
        'IF barrier_state IN (''PENDING'', ''READY'', ''PREPARED_RESTORE'', ''DELETE_PREPARING'', ''DELETE_SEALED'', ''DELETED'') OR authority_blocked THEN',
        'IF TG_TABLE_NAME = ''homeworks'' AND TG_OP = ''UPDATE''
            AND academic_homework_edit_continuation_admitted(old_row, new_row, target_semester_id) THEN CONTINUE; END IF;
         IF TG_TABLE_NAME = ''homework_binding_transfer_markers'' AND TG_OP = ''UPDATE''
            AND (new_row - ''superseded_by_edit_operation_id'') = (old_row - ''superseded_by_edit_operation_id'')
            AND ((academic_semester_delete_authorized(target_semester_id) AND new_row ->> ''superseded_by_edit_operation_id'' IS NULL)
              OR EXISTS (SELECT 1 FROM homework_edit_operations e
                  WHERE e.operation_id = (new_row ->> ''superseded_by_edit_operation_id'')::UUID
                    AND e.binding_id = (old_row ->> ''binding_id'')::BIGINT AND e.state = ''FINALIZED'' AND e.outcome = ''APPLIED''
                    AND academic_homework_edit_epoch_admitted(e.operation_id, target_semester_id))) THEN CONTINUE; END IF;
         IF barrier_state IN (''PENDING'', ''READY'', ''PREPARED_RESTORE'', ''DELETE_PREPARING'', ''DELETE_SEALED'', ''DELETED'') OR authority_blocked THEN');
    IF updated = fn THEN RAISE EXCEPTION 'V45 Academic archive guard anchor changed'; END IF;
    EXECUTE updated;
END $$;

-- Retained domain source: V47__homework_placement_identity.sql SHA256 b1bc1c6e75a6bc8380e14bbd1226fe2bf030fa3d35ddfb7e1efb2097a47f8bcd
-- Nullable legacy unknowns: only actual accepted Schedule snapshots populate these identifiers.
ALTER TABLE homeworks
    ADD COLUMN placement_occurrence_id BIGINT,
    ADD COLUMN placement_lesson_id BIGINT,
    ADD COLUMN placement_binding_revision BIGINT,
    ADD CONSTRAINT homeworks_placement_identity_chk CHECK (
        (placement_binding_revision IS NULL AND placement_occurrence_id IS NULL AND placement_lesson_id IS NULL)
        OR (placement_binding_revision IS NOT NULL AND placement_binding_revision > 0 AND (
            (placement_occurrence_id IS NULL AND placement_lesson_id IS NULL)
            OR (binding_mode = 'LESSON' AND placement_occurrence_id IS NOT NULL AND placement_lesson_id IS NOT NULL
                AND placement_occurrence_id > 0 AND placement_lesson_id > 0))));

ALTER TABLE homework_edit_operations ADD COLUMN result_placement_identity JSONB;

DO $$ DECLARE fn TEXT; updated TEXT;
BEGIN
    SELECT pg_get_functiondef('protect_homework_edit_operation()'::regprocedure) INTO fn;
    updated := replace(fn, '''last_failure'']', '''last_failure'',''result_placement_identity'']');
    updated := replace(updated, 'OR NEW.result_revision IS DISTINCT FROM OLD.result_revision',
        'OR NEW.result_placement_identity IS DISTINCT FROM OLD.result_placement_identity
         OR NEW.result_revision IS DISTINCT FROM OLD.result_revision');
    IF updated = fn THEN RAISE EXCEPTION 'V47 edit result identity guard anchor changed'; END IF;
    EXECUTE updated;
END $$;

CREATE OR REPLACE FUNCTION protect_homework_placement_identity()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.placement_binding_revision IS NOT NULL
       AND (NEW.placement_binding_revision IS NULL OR NEW.placement_binding_revision < OLD.placement_binding_revision) THEN
        RAISE EXCEPTION 'homework placement revision cannot move backwards';
    END IF;
    IF NEW.placement_binding_revision = OLD.placement_binding_revision
       AND (NEW.placement_occurrence_id IS DISTINCT FROM OLD.placement_occurrence_id
            OR NEW.placement_lesson_id IS DISTINCT FROM OLD.placement_lesson_id) THEN
        RAISE EXCEPTION 'homework placement revision has immutable identity';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER homework_placement_identity_guard BEFORE UPDATE ON homeworks
    FOR EACH ROW EXECUTE FUNCTION protect_homework_placement_identity();

-- Accepted edit continuation already proves content/scope/revision from its frozen operation.
-- Permit only its authoritative placement identity alongside those exact admitted content fields.
DO $$ DECLARE fn TEXT; updated TEXT;
BEGIN
    SELECT pg_get_functiondef('academic_homework_edit_continuation_admitted(jsonb,jsonb,bigint)'::regprocedure) INTO fn;
    updated := replace(fn, '''create_intent'',''create_intent_kind'']',
        '''create_intent'',''create_intent_kind'',''placement_occurrence_id'',''placement_lesson_id'',''placement_binding_revision'']');
    updated := replace(updated, 'AND edit.result_snapshot = jsonb_build_object',
        'AND ((edit.result_placement_identity IS NULL
                AND old_row -> ''placement_occurrence_id'' = new_row -> ''placement_occurrence_id''
                AND old_row -> ''placement_lesson_id'' = new_row -> ''placement_lesson_id''
                AND old_row -> ''placement_binding_revision'' = new_row -> ''placement_binding_revision'')
            OR edit.result_placement_identity = jsonb_build_object(
                ''placement_occurrence_id'', new_row -> ''placement_occurrence_id'',
                ''placement_lesson_id'', new_row -> ''placement_lesson_id'',
                ''placement_binding_revision'', new_row -> ''placement_binding_revision''))
          AND edit.result_snapshot = jsonb_build_object');
    IF updated = fn THEN RAISE EXCEPTION 'V47 accepted edit continuation anchor changed'; END IF;
    EXECUTE updated;
END $$;

-- Retained domain source: V48__homework_lesson_type.sql SHA256 7375e598eb957762da9748c2f41fa6f032433675abcee1fa4b26b70ecbad29e0
ALTER TABLE homeworks ADD COLUMN lesson_type VARCHAR(16) CHECK (lesson_type IN ('LECTURE', 'PRACTICE', 'LAB'));

-- Keep exact admitted snapshots for legacy null type; compare and fence the new field when known.
DO $$ DECLARE fn TEXT; updated TEXT;
BEGIN
    SELECT pg_get_functiondef('protect_homework_binding_identity()'::regprocedure) INTO fn;
    updated := replace(fn, 'OR NEW.revision IS DISTINCT FROM OLD.revision)',
        'OR NEW.lesson_type IS DISTINCT FROM OLD.lesson_type OR NEW.revision IS DISTINCT FROM OLD.revision)');
    IF updated = fn THEN RAISE EXCEPTION 'V48 terminal homework guard anchor changed'; END IF;
    EXECUTE updated;

    SELECT pg_get_functiondef('academic_homework_edit_continuation_admitted(jsonb,jsonb,bigint)'::regprocedure) INTO fn;
    updated := replace(fn, '''lessonNumber'', new_row -> ''lesson_number'')',
        '''lessonNumber'', new_row -> ''lesson_number'') || CASE WHEN new_row ->> ''lesson_type'' IS NULL THEN ''{}''::jsonb ELSE jsonb_build_object(''lessonType'', new_row -> ''lesson_type'') END');
    updated := replace(updated, '''binding_mode'',''revision'',', '''binding_mode'',''lesson_type'',''revision'',');
    IF updated = fn THEN RAISE EXCEPTION 'V48 edit continuation anchor changed'; END IF;
    EXECUTE updated;

    SELECT pg_get_functiondef('guard_homework_edit_business_write()'::regprocedure) INTO fn;
    updated := replace(fn, '''lessonNumber'', h.lesson_number))',
        '''lessonNumber'', h.lesson_number) || CASE WHEN h.lesson_type IS NULL THEN ''{}''::jsonb ELSE jsonb_build_object(''lessonType'', h.lesson_type) END)');
    IF updated = fn THEN RAISE EXCEPTION 'V48 transfer history anchor changed'; END IF;
    EXECUTE updated;
END $$;

-- Retained domain source: V49__assignment_closure_unassigned.sql SHA256 5df25176ec3ccc27547c136cc5218a761f70e407da5c89186d0c0797c2dc366f
-- Directed closure/adoption use the same durable protocol, with explicit absent identities.
ALTER TABLE assignment_replacement_operations
    ADD COLUMN operation_kind VARCHAR(24) NOT NULL DEFAULT 'REPLACE',
    ADD COLUMN requested_cutoff DATE,
    ALTER COLUMN source_assignment_id DROP NOT NULL,
    ALTER COLUMN target_assignment_id DROP NOT NULL,
    DROP CONSTRAINT assignment_replacement_operation_source_chk,
    DROP CONSTRAINT assignment_replacement_operation_target_chk,
    DROP CONSTRAINT assignment_replacement_operation_source_target_chk;

ALTER TABLE assignment_replacement_operations ADD CONSTRAINT assignment_lifecycle_tuple_chk CHECK (
    (operation_kind = 'REPLACE' AND source_assignment_id IS NOT NULL AND target_assignment_id IS NOT NULL
       AND source_assignment_id > 0 AND target_assignment_id > 0
       AND source_assignment_id <> target_assignment_id AND requested_cutoff IS NULL)
 OR (operation_kind = 'CLOSE_UNASSIGN' AND source_assignment_id IS NOT NULL AND source_assignment_id > 0 AND target_assignment_id IS NULL
       AND requested_cutoff IS NOT NULL AND source_valid_until IS NOT NULL)
 OR (operation_kind = 'ADOPT_UNASSIGNED' AND source_assignment_id IS NULL AND target_assignment_id IS NOT NULL AND target_assignment_id > 0
       AND requested_cutoff IS NOT NULL AND requested_cutoff = effective_from AND source_valid_until IS NOT NULL)
);
CREATE UNIQUE INDEX assignment_adoption_target_uq ON assignment_replacement_operations(target_assignment_id)
 WHERE operation_kind = 'ADOPT_UNASSIGNED';

ALTER TABLE assignments DROP CONSTRAINT assignments_lifecycle_state_chk,
 ADD CONSTRAINT assignments_lifecycle_state_chk CHECK (lifecycle_state IN ('ACTIVE', 'PREPARED', 'CLOSED'));
-- A terminal close before valid_from retains its positive historical interval,
-- but it no longer reserves that teacher's future admission interval.
ALTER TABLE assignments DROP CONSTRAINT assignments_no_same_teacher_overlap,
 ADD CONSTRAINT assignments_no_same_teacher_overlap EXCLUDE USING gist
 (teacher_id WITH =,subject_id WITH =,group_id WITH =,semester_id WITH =,
  lesson_type WITH =,daterange(valid_from,valid_until_exclusive,'[)') WITH &&)
 WHERE (lifecycle_state <> 'CLOSED');

CREATE FUNCTION protect_assignment_lifecycle_discriminator() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP = 'UPDATE' AND (NEW.operation_kind IS DISTINCT FROM OLD.operation_kind
       OR NEW.requested_cutoff IS DISTINCT FROM OLD.requested_cutoff) THEN
   RAISE EXCEPTION 'assignment lifecycle discriminator is immutable';
 END IF;
 IF NEW.operation_kind = 'ADOPT_UNASSIGNED' AND NOT EXISTS (
   SELECT 1 FROM assignments target WHERE target.id = NEW.target_assignment_id
    AND target.valid_from = NEW.effective_from
    AND (target.valid_until_exclusive = NEW.target_valid_until OR target.valid_until_exclusive IS NULL)
    AND target.lifecycle_state IN ('PREPARED', 'ACTIVE')) THEN
   RAISE EXCEPTION 'adoption requires its exact admitted target';
 END IF;
 IF NEW.operation_kind = 'CLOSE_UNASSIGN' AND NOT EXISTS (
   SELECT 1 FROM assignments source WHERE source.id = NEW.source_assignment_id
    AND NEW.effective_from = GREATEST(NEW.requested_cutoff, source.valid_from)) THEN
   RAISE EXCEPTION 'closure requires its exact clamped source date';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER assignment_lifecycle_discriminator_guard BEFORE INSERT OR UPDATE
 ON assignment_replacement_operations FOR EACH ROW EXECUTE FUNCTION protect_assignment_lifecycle_discriminator();

-- Preserve final-semester deletion authority and immutable assignment identity.
CREATE OR REPLACE FUNCTION protect_assignment_identity() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE operation assignment_replacement_operations%ROWTYPE;
        operation_id_value UUID;
BEGIN
 IF TG_OP = 'DELETE' THEN
   IF academic_semester_delete_authorized(OLD.semester_id) THEN RETURN OLD; END IF;
   RAISE EXCEPTION 'assignments are closed, never deleted';
 END IF;
 IF NEW.id IS DISTINCT FROM OLD.id OR NEW.teacher_id IS DISTINCT FROM OLD.teacher_id
   OR NEW.subject_id IS DISTINCT FROM OLD.subject_id OR NEW.group_id IS DISTINCT FROM OLD.group_id
   OR NEW.semester_id IS DISTINCT FROM OLD.semester_id OR NEW.lesson_type IS DISTINCT FROM OLD.lesson_type
   OR NEW.valid_from IS DISTINCT FROM OLD.valid_from OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
   RAISE EXCEPTION 'assignment identity is immutable';
 END IF;
 IF OLD.valid_until_exclusive IS NOT NULL AND (NEW.valid_until_exclusive IS NULL
       OR NEW.valid_until_exclusive > OLD.valid_until_exclusive) THEN
   RAISE EXCEPTION 'closed assignment validity cannot be extended or reopened';
 END IF;
 IF NEW.valid_until_exclusive IS NOT NULL AND NEW.valid_until_exclusive <= NEW.valid_from THEN
   RAISE EXCEPTION 'assignment requires a positive retained interval';
 END IF;
 IF NEW.valid_until_exclusive IS DISTINCT FROM OLD.valid_until_exclusive
       OR NEW.lifecycle_state IS DISTINCT FROM OLD.lifecycle_state THEN
   operation_id_value := NULLIF(current_setting('rutcampustrack.assignment_replacement_operation_id', true), '')::UUID;
   SELECT * INTO operation FROM assignment_replacement_operations WHERE operation_id = operation_id_value FOR UPDATE;
   IF NOT FOUND OR operation.state <> 'APPLIED' THEN
     RAISE EXCEPTION 'assignment change requires an exact applied lifecycle operation';
   END IF;
   IF NEW.valid_until_exclusive IS DISTINCT FROM OLD.valid_until_exclusive AND NOT (
       operation.operation_kind IN ('REPLACE', 'CLOSE_UNASSIGN') AND operation.source_assignment_id = OLD.id
       AND NEW.valid_until_exclusive = operation.effective_from
       AND (OLD.valid_until_exclusive IS NULL OR NEW.valid_until_exclusive < OLD.valid_until_exclusive)) THEN
     RAISE EXCEPTION 'assignment end does not match its applied operation';
   END IF;
   IF NEW.lifecycle_state IS DISTINCT FROM OLD.lifecycle_state AND NOT (
       (operation.operation_kind IN ('REPLACE', 'ADOPT_UNASSIGNED') AND operation.target_assignment_id = OLD.id
          AND OLD.lifecycle_state = 'PREPARED' AND NEW.lifecycle_state = 'ACTIVE')
       OR (operation.operation_kind = 'CLOSE_UNASSIGN' AND operation.source_assignment_id = OLD.id
          AND OLD.lifecycle_state = 'ACTIVE' AND NEW.lifecycle_state = 'CLOSED'
          AND ((operation.requested_cutoff <= OLD.valid_from AND NEW.valid_until_exclusive IS NOT DISTINCT FROM OLD.valid_until_exclusive)
            OR (operation.requested_cutoff > OLD.valid_from AND NEW.valid_until_exclusive=operation.effective_from)))) THEN
     RAISE EXCEPTION 'assignment lifecycle does not match its applied operation';
   END IF;
 END IF;
 RETURN NEW;
END $$;

-- Retained domain source: V50__homework_action_actor_roles.sql SHA256 74fe9269be790aa0a401da3ad96b83ad41f8c63b1bddd54617c29642d78fafdc
-- Only newly admitted commands receive an authorization snapshot. Legacy rows stay unknown.
ALTER TABLE homeworks ADD COLUMN create_actor_role VARCHAR(32),
    ADD COLUMN archive_action_actor_id BIGINT,
    ADD COLUMN archive_action_actor_role VARCHAR(32),
    ADD CONSTRAINT homework_create_actor_role_known CHECK (create_actor_role IN ('HEADMAN', 'ASSISTANT')),
    ADD CONSTRAINT homework_archive_actor_snapshot CHECK (
        (archive_action_actor_id IS NULL AND archive_action_actor_role IS NULL)
        OR (archive_action_actor_id IS NOT NULL AND archive_action_actor_id > 0
            AND archive_action_actor_role IS NOT NULL AND archive_action_actor_role IN ('HEADMAN', 'ASSISTANT')));

ALTER TABLE homework_edit_operations ADD COLUMN actor_role VARCHAR(32)
    CHECK (actor_role IN ('HEADMAN', 'ASSISTANT'));
-- V45's whole-command identity guard already makes the added operation column immutable.

CREATE FUNCTION protect_homework_action_actor_roles()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.create_actor_role IS DISTINCT FROM OLD.create_actor_role THEN
        RAISE EXCEPTION 'homework creation authority snapshot is immutable';
    END IF;
    IF OLD.archive_action_actor_id IS NOT NULL
       AND (NEW.archive_action_actor_id IS DISTINCT FROM OLD.archive_action_actor_id
            OR NEW.archive_action_actor_role IS DISTINCT FROM OLD.archive_action_actor_role) THEN
        RAISE EXCEPTION 'homework manual archive authority snapshot is immutable';
    END IF;
    IF OLD.publication_state = 'ARCHIVED' AND OLD.archive_action_actor_id IS NULL
       AND NEW.archive_action_actor_id IS NOT NULL THEN
        RAISE EXCEPTION 'terminal homework cannot receive a new manual archive author';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER homework_action_actor_roles_guard BEFORE UPDATE ON homeworks
    FOR EACH ROW EXECUTE FUNCTION protect_homework_action_actor_roles();

-- Retained domain source: V52__admin_dashboard_activity.sql SHA256 55145893778e066cade4d036acc1dd9f166073beb0053327209d3b5ca5543969
-- Keep the established UTC lifetime aggregate unchanged. Moscow calendar
-- history starts at this durable boundary; old UTC days are never re-labelled.
CREATE TABLE campus_map_activity_coverage (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    captured_from TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE campus_map_floor_hourly_activity (
    floor_id BIGINT NOT NULL REFERENCES campus_map_floor(id) ON DELETE CASCADE,
    hour_start TIMESTAMPTZ NOT NULL,
    open_count BIGINT NOT NULL CHECK (open_count > 0),
    PRIMARY KEY (floor_id, hour_start)
);
CREATE INDEX campus_map_activity_hour ON campus_map_floor_hourly_activity(hour_start);
-- Raw rows already have bounded 48-hour retention. Avoid locking that existing
-- table for an optional index: opening writes intent->dedupe while cleanup
-- writes dedupe->intent. No intent DDL lock may be held while waiting on dedupe.

CREATE FUNCTION increment_campus_map_hourly_activity() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    -- The existing guarded dedupe INSERT is the single acceptance authority.
    -- UTC hours align with Europe/Moscow hours. Retries never fire this trigger.
    INSERT INTO campus_map_floor_hourly_activity(floor_id, hour_start, open_count)
    VALUES (NEW.floor_id, date_trunc('hour', NEW.accepted_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC', 1)
    ON CONFLICT (floor_id, hour_start)
    DO UPDATE SET open_count = campus_map_floor_hourly_activity.open_count + 1;
    RETURN NEW;
END $$;
CREATE TRIGGER campus_map_hourly_activity_count
    AFTER INSERT ON campus_map_floor_demand_dedupe
    FOR EACH ROW EXECUTE FUNCTION increment_campus_map_hourly_activity();

-- CREATE TRIGGER serializes accepted dedupe inserts and keeps its table lock
-- until commit. Establish coverage only after it has waited for old writers;
-- transaction-start time would incorrectly claim the preceding wait interval.
INSERT INTO campus_map_activity_coverage (singleton) VALUES (TRUE);

-- Retained domain source: V53__semester_promotion_cycle_cutoff.sql SHA256 0020274cae5b52784faefebffc13fb69829e4f27ebd3d3df161ffbfdd937a2d4
-- Preserve the original completed spring promotion cutoff before an archived
-- semester metadata extension. Existing rows retain their current date_to fallback.
ALTER TABLE semesters ADD COLUMN promotion_cycle_date_to DATE;

-- Retained domain source: V54__scheduled_semester_activation_and_map_target_receipt.sql SHA256 7670564617d5c27684cb134441337cf7e90c82dab81d924c202eb518f1f9f611
ALTER TABLE semesters ADD COLUMN activation_scheduled boolean NOT NULL DEFAULT false;
CREATE INDEX semesters_scheduled_activation_due ON semesters (date_from, id)
    WHERE activation_scheduled = true;

ALTER TABLE campus_map_deletion_receipt
    ADD COLUMN action_kind varchar(16) NOT NULL DEFAULT 'INVENTORY',
    ADD COLUMN building_code varchar(64),
    ADD COLUMN floor_code varchar(64),
    ADD COLUMN format varchar(3),
    ADD CONSTRAINT campus_map_receipt_action CHECK (
        (action_kind = 'INVENTORY' AND building_code IS NULL AND floor_code IS NULL AND format IS NULL)
        OR (action_kind = 'PLAN_CLEAR' AND target_type = 'FLOOR'
            AND building_code IS NOT NULL AND building_code ~ '^[1-9][0-9]*$'
            AND floor_code IS NOT NULL AND floor_code ~ '^[1-9][0-9]*$'
            AND (format IS NULL OR format IN ('png', 'svg')))
    );

-- Retained domain source: V55__group_promotion_notification_cycle.sql SHA256 473e541e65ff4354236c9dbc5681f29fe31f175c44a648c3209449275157573a
-- One administrative summary for the first durably applied automatic batch.
-- This marker survives outbox retention; it is not a claim that all groups in
-- the cycle succeeded. Later eligible batches still write their group facts.
CREATE TABLE group_promotion_notification_cycle (
    cycle_semester_id BIGINT PRIMARY KEY REFERENCES semesters(id) ON DELETE CASCADE,
    promoted BIGINT NOT NULL CHECK (promoted >= 0),
    graduated BIGINT NOT NULL CHECK (graduated >= 0),
    skipped BIGINT NOT NULL CHECK (skipped >= 0),
    completed_at TIMESTAMPTZ NOT NULL
);

-- Retained domain source: V58__dashboard_scale_observations.sql SHA256 e80c9ef0186e83eecd843d6de01a12505cd6ee7d71d223192a6df432b1fce664
-- No historical seed/backfill: the first observation starts prospective coverage.
-- CHANGE is inserted in the same transaction as canonical count-changing mutations.
-- SNAPSHOT is a count read under the shared final-commit fence, on its actual capture day.
CREATE TABLE dashboard_scale_observations (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('CHANGE', 'SNAPSHOT')),
    observed_at TIMESTAMPTZ NOT NULL,
    settled_at TIMESTAMPTZ,
    snapshot_day DATE,
    total_students BIGINT,
    total_teachers BIGINT,
    total_groups BIGINT,
    CHECK (
        (kind = 'CHANGE' AND snapshot_day IS NULL
            AND total_students IS NULL AND total_teachers IS NULL AND total_groups IS NULL
            AND (settled_at IS NULL OR settled_at >= observed_at))
        OR
        (kind = 'SNAPSHOT' AND snapshot_day IS NOT NULL AND settled_at IS NULL
            AND snapshot_day = (observed_at AT TIME ZONE 'Europe/Moscow')::date
            AND total_students IS NOT NULL AND total_students >= 0
            AND total_teachers IS NOT NULL AND total_teachers >= 0
            AND total_groups IS NOT NULL AND total_groups >= 0)
    )
);

CREATE UNIQUE INDEX dashboard_scale_snapshot_day
    ON dashboard_scale_observations(snapshot_day) WHERE kind = 'SNAPSHOT';
CREATE INDEX dashboard_scale_change_interval
    ON dashboard_scale_observations(observed_at, settled_at) WHERE kind = 'CHANGE';

-- Retained domain source: V60__subject_semester_and_deletion.sql SHA256 606724dbde1e1cd827160f315292b7694c7d754e4c6bce1f43c06a30b96303fc
-- Legacy scope is deliberately not inferred or repaired.
ALTER TABLE subjects
    ADD COLUMN semester_id bigint REFERENCES semesters (id);

CREATE TABLE subject_deletion_operations (
    operation_id uuid PRIMARY KEY,
    idempotency_key uuid NOT NULL UNIQUE,
    subject_id bigint NOT NULL CHECK (subject_id > 0),
    semester_id bigint NOT NULL CHECK (semester_id > 0),
    group_id bigint NOT NULL CHECK (group_id > 0),
    actor_id bigint NOT NULL CHECK (actor_id > 0),
    session_id uuid NOT NULL,
    confirmation_hash varchar(64) NOT NULL CHECK (confirmation_hash ~ '^[0-9a-f]{64}$'),
    preview_digest varchar(64) NOT NULL CHECK (preview_digest ~ '^[0-9a-f]{64}$'),
    academic_digest varchar(64) NOT NULL,
    academic_manifest jsonb NOT NULL CHECK (jsonb_typeof(academic_manifest) = 'array'),
    schedule_digest varchar(64) NOT NULL,
    attendance_digest varchar(64) NOT NULL,
    manifest bytea NOT NULL,
    phase varchar(16) NOT NULL CHECK (phase IN ('PREPARING', 'SEALED', 'COMMITTING', 'COMPLETED', 'RELEASING', 'CANCELLED')),
    schedule_state varchar(16) NOT NULL DEFAULT 'PENDING',
    attendance_state varchar(16) NOT NULL DEFAULT 'PENDING',
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    last_failure varchar(32),
    CHECK (schedule_state IN ('PENDING', 'PREPARED', 'SEALED', 'DELETED', 'RELEASED')),
    CHECK (attendance_state IN ('PENDING', 'PREPARED', 'SEALED', 'DELETED', 'RELEASED'))
);

CREATE UNIQUE INDEX subject_deletion_one_current ON subject_deletion_operations (subject_id)
WHERE
    phase NOT IN ('COMPLETED', 'CANCELLED');

CREATE FUNCTION guard_subject_semester_identity ()
    RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.semester_id IS DISTINCT FROM OLD.semester_id THEN
        RAISE EXCEPTION 'subject semester identity is immutable';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER subject_semester_identity
    BEFORE INSERT OR UPDATE ON subjects
    FOR EACH ROW
    EXECUTE FUNCTION guard_subject_semester_identity ();

-- An immutable admission receipt is owned locally; challenge/OTP/attempts remain in Auth.
CREATE TABLE subject_deletion_admission_receipts (
    receipt_id uuid PRIMARY KEY,
    operation_id uuid NOT NULL UNIQUE REFERENCES subject_deletion_operations(operation_id)
        DEFERRABLE INITIALLY DEFERRED,
    actor_id bigint NOT NULL CHECK (actor_id>0),
    session_id uuid NOT NULL,
    subject_id bigint NOT NULL CHECK (subject_id>0),
    semester_id bigint NOT NULL CHECK (semester_id>0),
    group_id bigint NOT NULL CHECK (group_id>0),
    preview_digest varchar(64) NOT NULL CHECK (preview_digest ~ '^[0-9a-f]{64}$'),
    checked_at timestamptz NOT NULL,
    UNIQUE(receipt_id,operation_id,actor_id,session_id,subject_id,semester_id,group_id,preview_digest)
);
ALTER TABLE subject_deletion_operations
    ADD COLUMN proof_receipt_id uuid NOT NULL UNIQUE,
    ADD CONSTRAINT subject_deletion_exact_admission_receipt FOREIGN KEY
        (proof_receipt_id,operation_id,actor_id,session_id,subject_id,semester_id,group_id,preview_digest)
        REFERENCES subject_deletion_admission_receipts
        (receipt_id,operation_id,actor_id,session_id,subject_id,semester_id,group_id,preview_digest)
        DEFERRABLE INITIALLY DEFERRED;

-- A receipt records one exact remote Auth admission, never permission for another command.
-- The application still rechecks the Auth receipt outside its DB transaction on each retry.
CREATE FUNCTION protect_subject_deletion_admission_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'subject deletion admission receipts are immutable';
END
$$;
CREATE TRIGGER subject_deletion_admission_receipt_immutable
    BEFORE UPDATE OR DELETE ON subject_deletion_admission_receipts
    FOR EACH ROW EXECUTE FUNCTION protect_subject_deletion_admission_receipt();

CREATE FUNCTION academic_subject_delete_row_authorized (kind text, old_row jsonb)
    RETURNS boolean
    LANGUAGE SQL
    STABLE
    AS $$
    SELECT
        EXISTS (
            SELECT
                1
            FROM
                subject_deletion_operations operation
            WHERE
                operation.operation_id = NULLIF (current_setting('rutcampustrack.subject_delete_operation_id', TRUE), '')::uuid
                AND operation.phase = 'COMMITTING'
                AND operation.schedule_state = 'DELETED'
                AND operation.attendance_state = 'DELETED'
                AND operation.academic_manifest @> jsonb_build_array(jsonb_build_object('kind', kind, 'row_hash', encode(public.digest(convert_to(old_row::text, 'UTF8'), 'sha256'), 'hex'))))
$$;

CREATE FUNCTION guard_academic_subject_write ()
    RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
DECLARE
    old_row jsonb;
    new_row jsonb;
    row_data jsonb;
    target_subject bigint;
    target_semester bigint;
    operation_id_value uuid;
    phase_value text;
    bound_semester bigint;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        old_row = to_jsonb (OLD);
    END IF;
    IF TG_OP <> 'DELETE' THEN
        new_row = to_jsonb (NEW);
    END IF;
    FOR row_data IN
    SELECT
        *
    FROM
        unnest(ARRAY[old_row, new_row])
    WHERE
        unnest IS NOT NULL LOOP
            target_subject = NULLIF (row_data ->> 'subject_id', '')::bigint;
            -- Global/group-only thresholds have no subject lifecycle identity.
            IF TG_TABLE_NAME = 'attendance_thresholds' AND target_subject IS NULL THEN
                CONTINUE;
            END IF;
            IF TG_TABLE_NAME = 'subjects' THEN
                target_subject = (row_data ->> 'id')::bigint;
            END IF;
            IF target_subject IS NULL AND row_data ->> 'homework_id' IS NOT NULL THEN
                SELECT
                    subject_id INTO target_subject
                FROM
                    homeworks
                WHERE
                    id = (row_data ->> 'homework_id')::bigint;
            END IF;
            IF target_subject IS NULL AND TG_TABLE_NAME = 'homework_edit_archive_admissions' THEN
                SELECT
                    subject_id INTO target_subject
                FROM
                    homework_edit_operations
                WHERE
                    operation_id = (row_data ->> 'edit_operation_id')::uuid;
            END IF;
            IF target_subject IS NULL THEN
                SELECT
                    operation.subject_id INTO target_subject
                FROM
                    subject_deletion_operations operation
                WHERE
                    operation.phase NOT IN ('COMPLETED', 'CANCELLED')
                    AND operation.academic_manifest @> jsonb_build_array(jsonb_build_object('kind', TG_TABLE_NAME, 'row_hash', encode(public.digest(convert_to(row_data::text, 'UTF8'), 'sha256'), 'hex')));
            END IF;
            IF target_subject IS NULL THEN
                CONTINUE;
            END IF;
            PERFORM
                1
            FROM
                subjects
            WHERE
                id = target_subject
            FOR UPDATE;
            SELECT
                semester_id INTO bound_semester
            FROM
                subjects
            WHERE
                id = target_subject;
            -- Preserve ordinary writes to unresolved legacy rows without inferring a binding.
            -- The destructive API independently refuses every NULL/ambiguous legacy subject.
            IF TG_TABLE_NAME IN ('assignments', 'teacher_subject_groups', 'homeworks') AND TG_OP <> 'DELETE' AND bound_semester IS NOT NULL AND bound_semester IS DISTINCT FROM (new_row ->> 'semester_id')::bigint THEN
                RAISE EXCEPTION 'subject belongs to a different semester';
            END IF;
            SELECT
                operation_id,
                phase INTO operation_id_value,
                phase_value
            FROM
                subject_deletion_operations
            WHERE
                subject_id = target_subject
                AND phase NOT IN ('COMPLETED', 'CANCELLED');
            IF operation_id_value IS NOT NULL AND NOT (phase_value = 'COMMITTING' AND operation_id_value = NULLIF (current_setting('rutcampustrack.subject_delete_operation_id', TRUE), '')::uuid AND (TG_OP = 'DELETE' AND academic_subject_delete_row_authorized (TG_TABLE_NAME, old_row) OR TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'homework_binding_archives' AND new_row ->> 'homework_id' IS NULL AND (new_row - 'homework_id') = (old_row - 'homework_id') AND academic_subject_delete_row_authorized (TG_TABLE_NAME, old_row) OR TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'homework_binding_transfer_markers' AND new_row ->> 'superseded_by_edit_operation_id' IS NULL AND (new_row - 'superseded_by_edit_operation_id') = (old_row - 'superseded_by_edit_operation_id') AND academic_subject_delete_row_authorized (TG_TABLE_NAME, old_row))) THEN
                RAISE EXCEPTION 'subject is fenced for deletion';
            END IF;
        END LOOP;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON subjects
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON subject_lesson_types
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON assignments
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON teacher_subject_groups
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON attendance_thresholds
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homeworks
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_completions
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_edit_history
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_edit_receipts
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_edit_operations
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_edit_archive_admissions
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_binding_archives
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_binding_transfer_markers
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

-- Preserve the complete current guards, including V47 edit identities and V49 lifecycle.
-- Only the exact sealed row receives an additional deletion/neutralization admission.
DO $$
DECLARE
    definition text;
    updated text;
    anchor integer;
    function_name text;
    admission text;
BEGIN
    FOREACH function_name IN ARRAY ARRAY['protect_assignment_identity', 'retain_homework_edit_evidence', 'protect_homework_edit_operation', 'protect_homework_binding_archive_identity', 'protect_homework_transfer_edit_supersession'] LOOP
        SELECT
            pg_get_functiondef((function_name || '()')::regprocEDURE) INTO definition;
        IF function_name IN ('protect_assignment_identity', 'retain_homework_edit_evidence', 'protect_homework_edit_operation') THEN
            admission := E'    IF TG_OP = ''DELETE'' AND academic_subject_delete_row_authorized(TG_TABLE_NAME,to_jsonb(OLD)) THEN RETURN OLD; END IF;\n';
        ELSIF function_name = 'protect_homework_binding_archive_identity' THEN
            admission := E'    IF TG_OP = ''UPDATE'' AND NEW.homework_id IS NULL AND (to_jsonb(NEW)-''homework_id'')=(to_jsonb(OLD)-''homework_id'') AND academic_subject_delete_row_authorized(TG_TABLE_NAME,to_jsonb(OLD)) THEN RETURN NEW; END IF;\n';
        ELSE
            admission := E'    IF TG_OP = ''UPDATE'' AND NEW.superseded_by_edit_operation_id IS NULL AND (to_jsonb(NEW)-''superseded_by_edit_operation_id'')=(to_jsonb(OLD)-''superseded_by_edit_operation_id'') AND academic_subject_delete_row_authorized(TG_TABLE_NAME,to_jsonb(OLD)) THEN RETURN NEW; END IF;\n';
        END IF;
        anchor := strpos(definition, E'BEGIN\n');
        IF anchor = 0 THEN
            RAISE EXCEPTION 'subject deletion guard admission anchor changed';
        END IF;
        updated := overlay(definition PLACING E'BEGIN\n' || admission FROM anchor FOR 6);
        IF updated = definition THEN
            RAISE EXCEPTION 'subject deletion guard admission not installed';
        END IF;
        EXECUTE updated;
    END LOOP;
END
$$;

-- The semester row lock orders this check with the subject command's acceptance transaction.
-- No archive/activation may begin in the gap between irreversible intent and remote receipts.
CREATE FUNCTION guard_subject_delete_semester_transition ()
    RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF EXISTS (
        SELECT
            1
        FROM
            subject_deletion_operations operation
        WHERE
            operation.semester_id = OLD.id
            AND operation.phase NOT IN ('COMPLETED', 'CANCELLED'))
        AND (TG_OP = 'DELETE'
            OR (to_jsonb (OLD) - ARRAY['name']) IS DISTINCT FROM (to_jsonb (NEW) - ARRAY['name'])) THEN
        RAISE EXCEPTION 'accepted subject deletion blocks semester transition'
        USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER a0_subject_delete_semester_transition
    BEFORE UPDATE OR DELETE ON semesters
    FOR EACH ROW
    EXECUTE FUNCTION guard_subject_delete_semester_transition ();

-- Retained domain source: V61__initial_membership_attendance_backfill.sql SHA256 ee03bf403fa6fdc1369a360f61f8ef8d0f278b4be728f8c330767467491a2978
-- Control-plane replay identities deliberately have no business-row FK or TTL.
-- A completed semester/subject deletion must retain the exact ACK identity.
CREATE TABLE academic_initial_membership_backfills (
    event_id UUID PRIMARY KEY,
    history_id BIGINT NOT NULL UNIQUE CHECK (history_id > 0),
    student_id BIGINT NOT NULL CHECK (student_id > 0),
    group_id BIGINT NOT NULL CHECK (group_id > 0),
    semester_id BIGINT NOT NULL CHECK (semester_id > 0),
    joined_on DATE NOT NULL,
    semester_ends_on DATE NOT NULL CHECK (semester_ends_on >= joined_on),
    payload_hash BYTEA NOT NULL CHECK (octet_length(payload_hash) = 32),
    state VARCHAR(7) NOT NULL CHECK (state IN ('PENDING', 'APPLIED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_at TIMESTAMPTZ,
    CONSTRAINT initial_membership_backfill_applied_state CHECK (
        (state = 'PENDING' AND applied_at IS NULL) OR
        (state = 'APPLIED' AND applied_at IS NOT NULL)
    )
);
CREATE INDEX academic_initial_membership_backfills_pending
    ON academic_initial_membership_backfills (semester_id) WHERE state = 'PENDING';

-- Own profile/group revisions and referential constraints for the operation coordinator.
ALTER TABLE groups ADD COLUMN academic_revision bigint NOT NULL DEFAULT 1 CHECK (academic_revision>0);
ALTER TABLE academic_role_eligibility ADD CONSTRAINT academic_eligibility_group_fk
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE RESTRICT;
ALTER TABLE academic_identity_operation_groups ADD CONSTRAINT academic_operation_group_fk
    FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE RESTRICT;
