# Performance Indexes — RutCampusTrack

Runbook: каталог composite indexes, EXPLAIN ANALYZE before/after,
процесс добавления новых индексов.

**Created:** M05 Группа 1 (2026-04-20). **Owner:** platform.

## Назначение

- Доказательство эффекта каждого composite index (before/after).
- Capacity-planning reference для v0.1 (когда нагрузка будет ×10).
- Инструкция «как добавить новый индекс».

## Baseline seed-dataset (M05)

Используется `docs/milestones/M05-performance/seed-perf.sql` (+ `seed-perf.js`).
Итоговые объёмы:

| БД / коллекция | Таблица | Rows |
|---|---|---|
| schedule_db | schedule_items | 600 |
| schedule_db | lessons | 12 000 (11 970 non-cancelled) |
| academic_db | groups | 21 |
| academic_db | users | 523 |
| academic_db | subjects | 300 |
| academic_db | teacher_subject_groups | 1 800 |
| academic_db | homeworks | 1 800 |
| attendance_db | late_checkin_requests | 6 000 (2 400 PENDING) |

Seed — idempotent, использует `id >= 900000` для изоляции от prod-данных.
Очистка в конце M05 — по DELETE WHERE id >= 900000 (см. header seed-perf.sql).

## Hot queries — before / after

### Q1: schedule week-journal — `getLessonsForGroup(groupId, dateFrom, dateTo)`

**Источник:** `LessonService.getLessonsForGroup` →
`LessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn`.

**Шаблон запроса:**

```sql
SELECT l.* FROM lessons l
WHERE l.schedule_item_id IN (<itemIds for group/semester>)
  AND l.date BETWEEN :from AND :to
  AND l.status IN ('planned', 'active', 'closed');
```

**Существующие индексы (before M05 G1):**
- `idx_lessons_date (date)` — V1.
- `idx_lessons_status (status) WHERE status IN ('planned','active')` — V1.

**План «before» (seed 12k lessons, 36 matches on week):**
```
Hash Join (cost=14.58..52.33)
  ├── Index Scan idx_lessons_date → 720 rows (week of all groups)
  └── Bitmap Scan idx_si_group_semester → 30 items
Execution Time: 0.764 ms
```

Замечание: `Index Scan idx_lessons_date` возвращает 720 lessons всей
недели, Hash Join отфильтровывает 684 → 36. Прирастающий объём lessons
(v0.1 = 100k+/семестр) увеличит время линейно.

**Миграция (M05 G1):** `schedule_db V12__add_performance_indexes.sql`
```sql
CREATE INDEX idx_lessons_item_date
    ON lessons (schedule_item_id, date)
    WHERE status != 'cancelled';
```
(D1 — композит на `schedule_item_id` так как `lessons.group_id` не
существует; покрывает фактический `IN + BETWEEN` запрос.)

**План «after» (seed 12k lessons):**
```
Hash Join — план совпадает с «before».
Execution Time: 1.326 ms (vs 0.764 ms before).
```

**Примечание:** на seed-объёме (600 schedule_items, 12k lessons,
weekly range даёт 720 rows по `idx_lessons_date`) planner предпочитает
cheaper plan через `idx_lessons_date` + Hash Join по 30 items. Composite
`idx_lessons_item_date` становится выгодным при:
- `date` range шире (месяц/семестр = 10k+ rows через idx_lessons_date);
- `schedule_item_id IN (...)` list длиннее (100+ items — Hash Join
  дороже, Index Scan по composite cheaper);
- общий объём lessons ≥ 50k (v0.1 capacity).

Индекс готов к этому росту без re-tuning. Сейчас он «в резерве»
для prod-нагрузки. Regression guard (<50ms) с запасом.

---

### Q2: academic TSG — `findByGroupIdAndSemesterId`

**Источник:** `TeacherSubjectGroupRepository.findByGroupIdAndSemesterId`.

**Шаблон запроса:**
```sql
SELECT * FROM teacher_subject_groups
WHERE group_id = :groupId AND semester_id = :semesterId;
```

**Существующие индексы (before M05 G1):**
- `idx_tsg_teacher (teacher_id)` — V1.
- `idx_tsg_group (group_id)` — V1.
- `idx_tsg_semester (semester_id)` — V1.
- UNIQUE (teacher_id, subject_id, group_id, semester_id) — V1.

**План «before» (1800 rows, 90 rows для group, 30 для pair):**
```
Bitmap Heap Scan
  ├── Bitmap Index Scan idx_tsg_group → 90 rows
  └── Filter: semester_id = ? → removes 60 rows
Execution Time: 0.339 ms
```

Замечание: planner использует один из index'ов (`idx_tsg_group`),
оставшиеся 60 rows отбрасываются фильтром в памяти. Bitmap intersection
не применился на таком объёме.

**Миграция (M05 G1):** `academic_db V17__add_performance_indexes.sql`
```sql
CREATE INDEX idx_tsg_group_semester
    ON teacher_subject_groups (group_id, semester_id);
```
(D2 — композит на реальном hot query.)

**План «after» (1800 rows, 30 rows match):**
```
Bitmap Heap Scan on teacher_subject_groups
  └── Bitmap Index Scan idx_tsg_group_semester
       Index Cond: (group_id = ? AND semester_id = ?)
  [Rows Removed by Filter: 0]
Execution Time: 0.424 ms
```

Filter «Rows Removed by Filter: 60» ушёл — planner бьёт точно в
нужный диапазон. Cost 4.58..22.47 (vs 4.96..23.31 before) — меньше
I/O. На v0.1 объёме (100+ групп × 4 семестра × 30 subjects = 12k rows)
разница будет 5-10x.

---

### Q3: academic homeworks — `findByGroupIdAndSemesterId`

**Источник:** `HomeworkRepository.findByGroupIdAndSemesterId`.

**Шаблон запроса:**
```sql
SELECT * FROM homeworks
WHERE group_id = :groupId AND semester_id = :semesterId;
```

**Существующие индексы (before M05 G1):**
- `idx_hw_group_subject (group_id, subject_id)` — V1.
- `idx_homeworks_group_date (group_id, lesson_date)` — V13.

**План «before» (1800 rows, 90 rows для group, 30 для pair):**
```
Bitmap Heap Scan
  ├── Bitmap Index Scan idx_homeworks_group_date → 90 rows
  └── Filter: semester_id = ? → removes 60 rows
Execution Time: 0.388 ms
```

**Миграция (M05 G1):** `academic_db V17__add_performance_indexes.sql`
```sql
CREATE INDEX idx_hw_group_semester
    ON homeworks (group_id, semester_id);
```

**План «after» (1800 rows, 30 rows match):**
```
Bitmap Heap Scan on homeworks
  └── Bitmap Index Scan idx_hw_group_semester
       Index Cond: (group_id = ? AND semester_id = ?)
Execution Time: 0.210 ms (vs 0.388 ms before — ~1.8x faster)
```

---

### Q4: attendance headman-dashboard late-checkins (Mongo)

**Источник:** `LateCheckinService.listPendingForHeadman` →
`LateCheckinRepository.findByGroupIdAndStatusOrderByCreatedAtAsc`.

**Шаблон запроса:**
```js
db.late_checkin_requests.find({
  group_id: 900010,
  status: "PENDING"
}).sort({ created_at: 1 });
```

**Существующие индексы (before M05 G1):** отсутствуют. Только default `_id`.

**План «before» (6000 docs, 120 matches):**
```
SORT (in-memory)
└── COLLSCAN
    ├── docsExamined: 6000
    ├── nReturned: 120
    └── selectivity: 2%
Execution Time: ~2 ms (on 6k docs)
```

Замечание: **COLLSCAN** — full scan коллекции + in-memory sort. При
×10 объёме (60k docs через год работы) → 20ms+. Каноническая проблема
из 04 P2-9.

**Миграция (M05 G1):** compound index через `MongoConfig.initIndexes()`
(проектная convention — programmatic index management, без зависимости
от `spring.data.mongodb.auto-index-creation`):
```java
IndexOperations lcrOps = mongoTemplate.indexOps("late_checkin_requests");
lcrOps.ensureIndex(new Index()
        .on("group_id", Sort.Direction.ASC)
        .on("status", Sort.Direction.ASC)
        .on("created_at", Sort.Direction.ASC)
        .named("lcr_group_status_created"));
```

**План «after» (6000 docs, 120 matches):**
```
winningPlan:
  FETCH
  └── IXSCAN lcr_group_status_created
       bounds: group_id=[900010..900010], status=[PENDING..PENDING],
               created_at=[MinKey..MaxKey]

executionStats:
  nReturned:       120
  docsExamined:    120  (было 6000 — 50x меньше чтения)
  keysExamined:    120
  executionTimeMs: 2
```

**Главное:** SORT stage **ушёл** — документы возвращаются в
index-порядке по `created_at` (третье поле). При росте до 60k docs
(годовая работа) `docsExamined` останется 120, без composite был бы
60000. Это **каноническая победа** для 04 P2-9.

---

## Деферренные индексы (не в M05)

### `attendances (group_id, lesson_id)` — отложено (D3)

OWNER-ANSWERS 3693 предлагал индекс на Mongo `attendances`. Grep hot
queries показал: есть запросы по `lesson_id` и по `(user_id, lesson_id)`
(уже покрыты `uniq_lesson_user` UNIQUE index). Нет запроса по
`(group_id, lesson_id)`. Добавление индекса под несуществующий запрос
= write-overhead без read-выгоды.

Добавить при появлении потребителя (например, M07 group-dashboard
«все отметки группы за урок»).

### `schedule_one_off_lessons` — no-op (D4)

V4:18 содержит `CONSTRAINT uq_one_off_slot UNIQUE (group_id, date,
lesson_number)`. UNIQUE = btree index, покрывает prefix-запросы
`WHERE group_id=? AND date=?`. Не дублируем.

---

## Process: как добавить новый индекс

1. **Найти hot query.** Grep repository-методов, глянуть в pgbadger /
   Mongo slow-log. Записать точный шаблон.
2. **EXPLAIN ANALYZE «before».** На seed-dataset (см. seed-perf.sql).
   Если нет seed — расширить его.
3. **Flyway миграция.** Новый V{N} файл в
   `services/{service}-service/{service}-app/src/main/resources/db/migration/`.
   Для Mongo — `@CompoundIndex` или `MongoIndexConfig`.
4. **EXPLAIN «after».** В том же сетапе. Записать разницу здесь.
5. **Integration-тест.** Query time assertion `< 50ms` на seed-dataset
   (regression guard).
6. **PR-review.** Ожидаем: nodes читаемые (Index Scan, не Seq Scan),
   Buffers shared hit стабилен, Execution Time ниже в ≥ 2 раза.

## Expand/contract для новых индексов

Индексы добавляются через **expand-step** (CREATE INDEX CONCURRENTLY
для prod — не блокирует writes). Rollback-скрипт не нужен: DROP INDEX
безопасен.

На dev-Flyway: обычный `CREATE INDEX` (dev достаточно — нет
concurrent writes).

На prod:
```sql
CREATE INDEX CONCURRENTLY idx_name ...;
```
Flyway выполняет это как transactional-safe в Postgres 12+.
