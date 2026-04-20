# M05 Decisions (Micro-ADR)

Фиксируй каждое решение которое не описано в OWNER-ANSWERS, но нужно
для реализации. Формат: `## YYYY-MM-DD — короткий заголовок`,
дальше 3-10 строк: что выбрано, почему, альтернативы.

---

_Открытых развилок на старт M05 нет — все решения для P2-10/1..8 уже
зафиксированы в OWNER-ANSWERS.md (строки 3673-4028). Если появится
micro-решение — записать как `## YYYY-MM-DD — D1`.

---

## 2026-04-20 — D1: schedule composite index на `(schedule_item_id, date)` вместо `(group_id, date)`

**Выбрано:** `CREATE INDEX idx_lessons_item_date ON lessons
(schedule_item_id, date) WHERE status != 'cancelled';`

**Почему:** в `lessons` нет колонки `group_id` (проверено V1..V11
migrations). OWNER-ANSWERS 3686-3688 формулирует index как
`(group_id, date)`, но это абстрактная рекомендация — реальный hot
query `LessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn`
использует `WHERE schedule_item_id IN (...) AND date BETWEEN ... AND
status IN (...)`. Composite `(schedule_item_id, date)` точно
соответствует этой форме — planner делает bitmap index scan без
fallback'а на sequential scan.

**Альтернативы отклонены:**
- Денормализация `lessons.group_id` (+trigger/app-copy sync) —
  усложняет write-path, ловушки при ALTER на продакшен-таблице,
  преждевременно без analytics-кейса.
- Индекс только `(date, status)` — не покрывает `schedule_item_id`
  фильтр (группа).

**Ожидаемый эффект:** запрос getWeekLessons сейчас делает filter по
`idx_lessons_date` (всего неделя = узкая выборка, но +seq-filter по
`schedule_item_id IN`); после index — direct bitmap scan через
composite. Проверим EXPLAIN ANALYZE до/после на seed 10k.

**Масштабируемость:** при 100k+ lessons/семестр composite остаётся O(log
n) search. Если в v0.1+ появится прямой `WHERE group_id` (analytics) —
тогда отдельная миграция с денормализацией.

## 2026-04-20 — D2: academic composite на `teacher_subject_groups` + `homeworks` вместо несуществующего `user_groups`

**Выбрано:**
- `CREATE INDEX idx_tsg_group_semester ON teacher_subject_groups
  (group_id, semester_id);`
- `CREATE INDEX idx_hw_group_semester ON homeworks (group_id,
  semester_id);`

**Почему:** OWNER-ANSWERS 3695 говорит `(group_id, semester_id) на
user_groups`, но таблицы `user_groups` не существует — связь
user↔group живёт в `users.group_id` (V1:33, уже `idx_users_group`).
Реальные hot queries с парой `(groupId, semesterId)`:

- `TeacherSubjectGroupRepository.findByGroupIdAndSemesterId`
  (`TeacherSubjectGroupRepository.java:9`) — teacher dashboard.
  Текущие индексы `idx_tsg_group` + `idx_tsg_semester` раздельно —
  planner делает bitmap intersection или выбирает один.
- `HomeworkRepository.findByGroupIdAndSemesterId`
  (`HomeworkRepository.java:10`) — homework list. Есть
  `idx_hw_group_subject (group_id, subject_id)` — частично полезен
  (`group_id` первый), но не оптимизирует `semester_id` фильтр.

Это уточнение точно следует духу OWNER-ANSWERS (composite по паре
`(group_id, semester_id)`), но на **существующих** таблицах, где
запросы этим двум полям реально адресуются.

**Альтернативы отклонены:**
- Буквально создать `user_groups` pivot — выходит за scope P2-10/1,
  требует data migration из `students_group_history`.
- Оставить только `findByGroupId` в `users` без нового индекса —
  уже есть `idx_users_group` (V1:41).

**Масштабируемость:** teacher-dashboard + homework-list — high-read
endpoints. Composite снимает нагрузку когда семестров > 1 (на v1.0+
история ≥ 6 семестров → selectivity semester 1/6).

## 2026-04-20 — D3: отложить `(group_id, lesson_id)` на Mongo `attendances`

**Выбрано:** **не добавлять** индекс в M05. Зафиксировать в
`docs/future-ideas.md` (NEW-146) пункт «add when demand».

**Почему:** OWNER-ANSWERS 3693-3694 предлагает `db.attendances.createIndex({
group_id: 1, lesson_id: 1 })`, но grep по hot queries
`AttendanceRepository` показывает:

- `findByLessonId(lessonId)` — покрывается `uniq_lesson_user` index
  (prefix match).
- `findByUserIdAndLessonId(userId, lessonId)` — покрывается
  `uniq_lesson_user` (полный).
- Нет запроса по `(group_id, lesson_id)` в прямом виде.

Добавлять индекс под несуществующий запрос = write-overhead без
read-выгоды. `late_checkin_requests (group_id, status, created_at)`
(04 P2-9) — **остаётся в scope**, там запрос проверен и bottleneck'у
соответствует.

**Если появится потребитель** (например, в M07 dashboard «все отметки
группы за урок»): миграция тривиальная, ~30 секунд в `MongoConfig`.

**Масштабируемость:** меньше индексов = быстрее writes. MongoDB write
amplification = O(indexes). Добавляем только когда оправдано read-side.

## 2026-04-20 — D4: `schedule_one_off_lessons` — no-op, уже есть UNIQUE в V4

**Выбрано:** не добавлять `idx_oneoff_dedup`. Зафиксировать в
`performance-indexes.md` ссылку на V4.

**Почему:** `schedule_one_off_lessons` V4:18 содержит `CONSTRAINT
uq_one_off_slot UNIQUE (group_id, date, lesson_number)`. UNIQUE
constraint в PostgreSQL реализован как btree index — покрывает prefix-
matches `WHERE group_id=? AND date=?` (hot query из
`OneOffLessonRepository.findByGroupIdAndDate*`). Добавлять второй
index с теми же колонками в другом порядке — не имеет смысла,
потребителей нет.
