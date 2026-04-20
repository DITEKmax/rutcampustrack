# M05 Notes

Живой файл. Surprises, отклонения, измерения, технические долги.

---

## Pre-start snapshot (scaffold'ится при старте M05)

- M04 закрыт на `325d25d`, tag `v0.0.0-alpha.5` (локально без push).
- ~70+ коммитов ahead origin — push отложен до конца v0.0.0.
- M04 деферренные items (возможно пересечение с M05):
  - **`/actuator/**` исключить из tracing sampling** — M04 G11 backlog, уместно в M05 Группа 8 (gRPC instrumentation рядом).
  - **AlertPublisher extends AbstractEventPublisher** — M04 code-reviewer SHOULD #1. Легко зацепить при рефакторе repositories, но не scope M05. Держать в следующий milestone.
  - **Typed DTO для Alertmanager webhook** — M06 (не scope M05).

## Source of truth для M05

- `docs/report-before-v0.0.0/OWNER-ANSWERS.md` строки 3673-4028 (P2-10/1..8).
- `docs/report-before-v0.0.0/99-executive-summary.md` строка 117 (P2-10 summary).
- `docs/report-before-v0.0.0/COVERAGE-AUDIT.md` — пункты 02 P2-3..7, 03 P2-2/4/5/6/7/8/9, 04 P2-1/2/9, 05 P2-7, 09 P2-11, 10 P2-14.

## Открытые развилки для D1..DN

### 2026-04-20 — Расхождения между PLAN.md Группы 1 и фактической схемой БД

Перед seed-датасетом провёл аудит фактических схем — обнаружены 3
расхождения с текстом в `PLAN.md → Миграции Flyway` и CHECKLIST Группы
1. Прошу владельца подтвердить правку scope до коммитов.

**1. schedule_db — `lessons.group_id` колонки НЕ существует.**

- `V1__baseline.sql:27-36` — `lessons (id, schedule_item_id,
  date, status, is_geo_blocked, cancel_reason, ...)`. `group_id`
  доступен только JOIN'ом `lessons.schedule_item_id →
  schedule_items.group_id`.
- `LessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn`
  (`LessonService.java:230`) — hot query для week-journal. План говорит
  «`(group_id, date) WHERE status != 'cancelled'` на lessons» —
  невозможно без денормализации.

  **Варианты:**
  - **A.** Денормализовать `group_id` в `lessons` (миграция + trigger
    `BEFORE INSERT/UPDATE` или app-level copy при create). Index
    `(group_id, date) WHERE status != 'cancelled'` работает как
    задумано. Схема прирастает одной колонкой (~8 байт/lesson).
  - **B.** Оставить как есть, индексировать
    `lessons(schedule_item_id, date)` partial, дополнительно
    `schedule_items(group_id)` (уже есть `idx_si_group_semester`).
    Запрос остаётся как сейчас — `IN (itemIds)` + `BETWEEN`, planner
    использует composite на `(schedule_item_id, date)`.
  - **C.** Добавить индекс на `(date, status)` без group_id —
    selectivity даты узкая (неделя = 7 значений), но по группе не
    фильтрует.

  **Рекомендация:** B (без денормализации). Запрос уже работает
  через `schedule_item_id IN (...)`, planner быстро найдёт lessons по
  composite `(schedule_item_id, date)`. Денормализация group_id усложняет
  write-path (trigger/app-copy при перевыставлении schedule_item.group_id).

**2. schedule_db — unique `(group_id, lesson_number, date)` на
`schedule_one_off_lessons` уже есть.**

- `V4__one_off_lessons.sql:18` — `CONSTRAINT uq_one_off_slot UNIQUE
  (group_id, date, lesson_number)`. Дублировать не нужно. Пункт в
  CHECKLIST Группы 1 — закрыть как «уже есть, no-op».

**3. academic_db — таблицы `user_groups` НЕТ.**

- Связь user↔group живёт в `users.group_id`
  (V1:33 — `FK REFERENCES groups(id) ON DELETE SET NULL`).
- `student_group_history(user_id, group_id, joined_at, left_at)` —
  только история, без `semester_id`.
- `UserRepository.findByGroupId(groupId)` — hot query для get-group-
  members, **не фильтрует по semester**. Composite `(group_id,
  semester_id)` на `user_groups` бессмыслен — таблицы нет.
- Запросы, где `(group_id, semester_id)` реально совместные:
  - `TeacherSubjectGroupRepository.findByGroupIdAndSemesterId` (теперь
    без index — есть только `idx_tsg_group` + `idx_tsg_semester`).
  - `HomeworkRepository.findByGroupIdAndSemesterId` (есть
    `idx_hw_group_subject (group_id, subject_id)` — не подходит).

  **Варианты:**
  - **A.** Composite `teacher_subject_groups(group_id, semester_id)`
    и `homeworks(group_id, semester_id)` — два индекса вместо одного на
    несуществующий user_groups. Hot query для teacher-dashboard + для
    homework-list.
  - **B.** Оставить scope PLAN.md буквальным — тогда academic часть
    Группы 1 отпадает, P2-10/1 не закрывает academic.

  **Рекомендация:** A — индексы кладутся на реальные hot queries,
  покрывают оба случая из grep'а.

**Summary вариантов на утверждение:**

- **schedule:** composite `lessons(schedule_item_id, date)` partial
  `WHERE status != 'cancelled'`. `schedule_one_off_lessons` — no-op (уже
  UNIQUE).
- **attendance:** `(group_id, lesson_id)` + `(group_id, status,
  created_at DESC)` на коллекцию `attendances` через
  `AttendanceIndexInitializer` (Mongo — не Flyway).
  *Но ещё проверю:* late-checkin collection отдельная (`late_checkin_requests`)
  или те же `attendances`?
- **academic:** `teacher_subject_groups(group_id, semester_id)` +
  `homeworks(group_id, semester_id)` как замена несуществующему
  `user_groups(group_id, semester_id)`.

**Ожидаю подтверждения** перед Flyway миграциями и seed-датасетом.
PLAN.md не переписываю до `go` владельца.

## Правила работы (без изменений с M04)

- Русский в отчётах / NOTES / ответах.
- READ-BEFORE-EDIT hook-reminder'ы ложные после Read в сессии — игнорировать.
- Один CHECKLIST-группа = один logical коммит (`feat/fix/test/docs(<scope>): ... (M05 Группа N)`).
- Не звать `gsd-*` агентов. `Explore` для поиска, `bug-hunter`/`security-auditor`/`code-reviewer` в Группе 9.
- Surprise → NOTES.md + спросить до продолжения.
- Закрыл пункт CHECKLIST → `[x]` через Edit.
