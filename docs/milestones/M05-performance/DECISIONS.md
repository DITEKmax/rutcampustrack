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

## 2026-04-20 — D5: Группа 2 — урезанный scope (preventive-only)

**Выбрано:** пропустить `@EntityGraph` на list-endpoints и projection
для payload-оптимизации. Добавить только:

- ArchUnit rule NEW-143 (preventive, защита от будущего N+1);
- один reference projection-interface (whitelist в правиле);
- запись в `docs/architecture.md` о convention «FK as Long» v0.0.0.

**Почему:** OWNER-ANSWERS P2-10/2 (3714-3749) мотивирован ссылками на:

- «03 P2-4» — якобы N+1 в `LessonService.getLesson`. Реальный 03 P2-4
  = «existsBy мёртвый метод в OneOffLessonRepository».
- «02 P2-3» — якобы N+1 group-members. Реальный 02 P2-3 = Jackson RCE
  в Redis cache (M03b scope).

Системный аудит Repository-слоя (Explore-агент): **все JPA entity в
schedule + academic** используют FK как `Long`, без `@ManyToOne`/
`@OneToMany`. N+1 невозможен by architecture — buyer's choice в
проекте, зафиксированный в нескольких ранних фазах (1.x, 5-9).
`LessonService.massCancelLessons` (schedule-app:137-142) — образец
правильного паттерна (collect itemIds → single `findByIdIn`).

**Что добавлять с пустым эффектом — против принципа «Don't add
features, refactor beyond what the task requires» из CLAUDE.md:**

- `@EntityGraph(attributePaths={})` на методы где нечего graph'ить —
  no-op, visual noise, ухудшает читаемость.
- Projection interface для list-endpoint с 15 simple-columns —
  payload оптимизация на ~5-10% при текущих объёмах (23 users/group,
  25 lessons/week). ROI нулевой, ловушка при рефакторе (новое поле в
  entity не попадёт в projection, клиенты получат неполные данные).

**Что нужно оставить:**

- **ArchUnit NEW-143** — преимущество в **будущем**. Если завтра
  потребуется добавить `@ManyToOne` (например, `Lesson.scheduleItem`
  для denormalization optimization), rule **сразу** поймает
  repository-метод без `@EntityGraph`/Pageable/projection в PR. Это
  дешёвая preventive защита.
- **Один projection-interface как reference-pattern** — когда
  ArchUnit rule сработает на новый метод, разработчик увидит пример
  как правильно возвращать subset полей.
- **Docs convention** — явно запишем в `architecture.md`, что v0.0.0
  избегает JPA relations, и показать причины (прозрачный SQL,
  нет lazy surprises, лёгкое embrace microservices FK crossing DB
  boundary). Новый разработчик не будет на ровном месте добавлять
  `@ManyToOne`.

**Альтернативы отклонены:**
- Буквально следовать PLAN.md — visual шум без эффекта, противоречит
  CLAUDE.md.
- Полностью удалить Группу 2 — теряем ArchUnit NEW-143 (ценный
  guardrail).

**Estimate:** ~1-2 часа вместо ~1 дня.

## 2026-04-20 — D6: Redis вместо Caffeine для Группы 3 (отход от OWNER-ANSWERS)

**Выбрано:** оставить существующий Redis-кеш в academic-service и
**не** вводить Caffeine. Scope Группы 3 превращается в: добавить
недостающие namespaces (`rbac`, `subject`) + публичный метод
`isHeadmanFor(userId, groupId)` + `@CacheEvict` на соответствующих
write-side методах + Redis cache metrics биндинг + `docs/caching-strategy.md`.

**Контекст расхождения.** OWNER-ANSWERS 3756-3810 предписывает
Caffeine in-memory TTL+size для v0.0.0 («Single-instance ok»).
Audit перед стартом M05 Группы 3 (Explore-agent,
`NOTES.md → Группа 3 аудит`) показал, что в academic-service
**уже** реализован Spring `@EnableCaching` + Redis-backed
`CacheManager` с пятью namespaces (`groups`/`group_members`/`users`/
`active_semester`/`campus_geofence`), TTL-matrix, `@Cacheable` в
`AcademicReadService`, `@CacheEvict` на write-side, программатическое
eviction при смене `is_headman` флага (`UserService.patchUser:225-233`).
Это сделано в ранних фазах (phase 59/60, через D-01/D-02 этих фаз).

**Почему НЕ заменять на Caffeine:**

1. **Zero регрессионный риск.** Работающий кеш, integration-tests
   зелёные с прошлых фаз, eviction logic в `patchUser:225-233` хрупкий,
   сериализация `Semester`/`Group`/`User` через Hibernate6Module отлажена.
2. **Redis уже в прод-зависимостях.** Используется auth-service
   (OTP, refresh-tokens `refresh:<hash>`), notification-bot (reminders).
   Второй кеш-бэкенд (Caffeine) = 2 места для debug'а.
3. **Cross-instance консистентность бесплатно.** OWNER-ANSWERS 3800
   обещает миграцию на Redis при multi-instance v0.1 — это уже сделано.
   Caffeine потом всё равно пришлось бы переписывать назад.
4. **Мотивация OWNER-ANSWERS закрывается Redis'ом:**
   - 03 P2-7 (getActiveSemester) — ✅ `fetchActiveSemester` TTL 10м.
   - 02 P2-5 (Subject/Group cache без TTL → memory leak) — ✅ partial:
     `groups` TTL 5м. `subject` добавляется в M05.
   - 03 P2-6 / 04 P2-2 / 02 P2-7 (RBAC без кэша) — ⚠️ в M05 закрывается
     через новый `rbac` namespace + метод `isHeadmanFor`.
5. **Redis-single-instance contract.** В `docker-compose.prod.yml`
   единственный Redis-контейнер `rct-redis` — не distributed. Условие
   OWNER-ANSWERS «single-instance ok для v0.0.0» выполнено буквально.

**Почему НЕ гибрид L1 Caffeine + L2 Redis** (вариант C из NOTES):
Преждевременно при single-instance deploy. Sync-инвалидация двух
уровней — trap при `patchUser:225-233`-образной логике. Отложить до
появления > 2 инстансов в одном сервисе.

**Scope Группы 3 (финальный):**

| Пункт | Статус |
|-------|--------|
| Cache-impl: Spring `@EnableCaching` + Redis `CacheManager` | ✅ уже есть |
| Namespaces `groups`/`group_members`/`users`/`active_semester`/`campus_geofence` + TTL | ✅ уже есть |
| `@Cacheable` на `fetchGroup`/`fetchGroupMembers`/`fetchActiveSemester`/`fetchUserById`/`fetchCampusGeofence` | ✅ уже есть |
| `@CacheEvict` на UserService/GroupService/SemesterService write-side | ✅ уже есть |
| Программатическое eviction при `is_headman` смене | ✅ уже есть |
| **`rbac` namespace (TTL 1м) + `isHeadmanFor(userId, groupId)`** | ⬜ **добавляется в M05** |
| **`subject` namespace (TTL 10м) + `@Cacheable getSubject`** | ⬜ **добавляется в M05** |
| **`@CacheEvict("subject")` на updateSubject/deleteSubject** | ⬜ **добавляется в M05** |
| **`@CacheEvict("rbac")` при смене `is_headman`/`group_id` в patchUser** | ⬜ **добавляется в M05** |
| **Redis cache metrics через `MeterRegistry` (hit/miss counter)** | ⬜ **добавляется в M05** |
| **`docs/caching-strategy.md` (NEW-144)** | ⬜ **добавляется в M05** |
| **Integration-тест hit-rate на rbac cache** | ⬜ **добавляется в M05** |

**Обновление устаревшего решения:** `AcademicGrpcServiceImpl.isHeadman:127`
содержит комментарий «Not cached (per D-02)». D-02 принадлежит phase-60
scope (Subject cascade), не запрет на RBAC cache per se. M05 D6
переопределяет: isHeadman в academic-service **теперь кешируется**
через делегирование в `AcademicReadService.isHeadmanOf(userId, groupId)`
с `@Cacheable("rbac")`.

**Миграция на Caffeine в будущем** не предусмотрена. Если
прод-операции покажут, что Redis RTT (~0.5ms loopback docker network)
становится bottleneck'ом на hot-path — рассмотрим L1+L2. Сейчас
необоснованно.

## 2026-04-20 — D7: Batch mark endpoint — validation-first atomic semantics

**Выбрано:** `POST /attendance/marks/batch` — *pseudo-atomic* семантика
через validation-first подход:

1. Все pre-check'и (headman role, lesson/group match, each
   student-in-group) выполняются **перед** любым write'ом в Mongo.
2. Если любой pre-check падает — весь batch отклонён с HTTP 403/400,
   Mongo не тронут.
3. Если все pre-check'и прошли — выполняется N upsert'ов последовательно.
4. Один gRPC `getGroupMembers` + lesson fetch на весь batch (не N раз).

**Почему не настоящая Mongo transaction:** Mongo multi-document
transactions требуют replica set (v4.0+) или sharded cluster (v4.2+).
В `docker-compose.yml` attendance-service использует standalone Mongo
контейнер — transactions недоступны. Переход на replica set — M06 scope
(infra hardening), не M05.

**Почему pseudo-atomic достаточно:** из 3 типов ошибок в single-mark:

- **Authorization** (403) — детерминирован, одинаков для всех студентов
  группы. Если valid для одного — valid для всех. Pre-check один раз.
- **Lesson/group mismatch** (403) — lesson один на весь batch. Pre-check
  один раз.
- **Student not in group** (403) — gRPC `getGroupMembers` один раз,
  потом set-lookup per student.

Все остальные failure'ы (Mongo connection drop, disk full, ...) —
infrastructure-level и не типичны для business flow. В случае partial
failure (N/M marks saved) — следующий batch от headman перезапишет
upsert'ом (idempotent).

**Response schema:** `MarkBatchResponse { items: List<MarkResponse>,
processed: int }`. HTTP 200. Per-item error mid-batch из-за infra
(Mongo unavailable) — 500 общий, clients retry весь batch.

**Size limit:** `@Valid @Size(min=1, max=100) List<MarkBatchItem>`.
OWNER-ANSWERS 3826-3829 = 100; типичный batch = 25-30 студентов.

**Альтернативы отклонены:**
- **207 Multi-Status** (per-item success/failure) — incorrect semantics
  для headman-bulk, где expectation всё-или-ничего.
- **Настоящая Mongo transaction** — требует M06 replica-set setup,
  преждевременно.
- **Backend-side chunking с per-chunk atomic** — overcomplicated для
  30-item typical batch.

## 2026-04-20 — D8: Группа 4 — partial scope (attendance mark only)

**Выбрано:** в M05 Группе 4 сделать только **attendance `/marks/batch`**
и закрыть его полностью (backend + integration tests + PWA frontend +
docs). **Отложить** в backlog (`docs/future-ideas.md`):

- `POST /academic/homeworks/batch` (partial-success для admin-импорта) —
  не влияет на headman UX, admin-импорт редкий.
- `GET /attendance/reports/lessons?ids=...` — bulk-read для web-panel
  `HeadmanWeeklyJournal.loadWeek` (10 P2-14). Технически это `forkJoin`
  а не serial loop — browser уже параллелит N requests через HTTP/2
  multiplexing. Фактический выигрыш от bulk-read < 2× (против 10× для
  bulk-mark). ROI низкий, отложено.
- Web-panel adoption batch-mark — web-panel `headman-journal-grid`
  делает per-cell single mark, не bulk. Нет callsite для адаптации.

**Почему partial:** CLAUDE.md «Don't add features, refactor, or introduce
abstractions beyond what the task requires». Ядро P2-10/4 — headman
bulk-mark, 30 serial → 1 call, ~12× latency wins — закрыто. Остальные
items P2-10/4 — ROI ниже, не блокируют pre-release hardening.

**Estimate итого:** ~4-5ч (оценка PLAN.md ~1 день полный scope,
partial scope укладывается).

## 2026-04-20 — D9: Группа 5 — single-pass accumulators вместо SQL aggregate + LessonService SQL pagination

**Выбрано:**

1. **`ReportService` (attendance)** — переписать `buildOverall`,
   `buildWeekly`, `buildTopMissed`, `getStudentStats` на **single-pass
   accumulators** (один проход `for` с int counter'ами) вместо 3-4×
   `stream().filter().count()` над одним списком.
2. **`LessonService.getLessonsForGroup` (schedule)** — переписать
   in-memory pagination (`.stream().toList().subList(offset, end)`)
   на **SQL `LIMIT/OFFSET`** через `Pageable`. Это **реальный OOM**
   risk при 10k+ lessons.
3. **НЕ делать Mongo aggregation pipeline** для stats. Обоснование
   см. ниже.

**Почему НЕ Mongo aggregate для ReportService.**

`ReportService.filterExistingLessons` (:417-426) — defence-in-depth
фильтр через gRPC `scheduleGrpcClient.getLessonsByIds`: выкидывает
attendance docs с удалёнными уроками ПОСЛЕ загрузки из Mongo, до
агрегации. Invariant: stale docs (missed `lesson.deleted` event) не
должны попасть в отчёт.

Mongo `$group` pipeline не знает про alive-lessons в schedule-service
(кросс-сервис). Два пути сохранить invariant с Mongo aggregate:

- **(a) Денормализовать `lesson_alive` флаг в attendance docs** —
  требует cascade `lesson.deleted → attendance.mark_dead` события +
  migration. M06/M07 scope (infra hardening), не M05.
- **(b) Two-step: Mongo `$group` → post-filter by alive lessonIds**.
  Всё равно нужен full load + gRPC + пересчёт sum'ов. Выигрыш
  минимален, сложность aggregate pipeline возрастает.

Single-pass accumulators дают **real O(N) вместо O(3-4N)** —
простая правка, сохраняет `filterExistingLessons` invariant, не
требует архитектурных изменений. Для 10k attendance records в
семестре: ~40k stream iterations → ~10k (75% reduction).

**Почему SQL pagination ЕСТЬ в scope.**

`LessonService.getLessonsForGroup:212-246` загружает **все** lessons
группы за семестр (может быть 500-2000 rows), строит `List<LessonWithItem>`
в памяти и делает `subList(offset, end)`. Это:

- **OOM-рискованно** на v0.1+ объёмах (история семестров).
- **Неоптимально** на hot-path (web-panel headman-weekly-journal
  делает параллельные запросы через PageRequest).
- Нарушает principles OWNER-ANSWERS P2-10/5 напрямую.

**Scope Группы 5 (финальный):**

| Пункт | Action | Status |
|-------|--------|--------|
| ReportService.getStudentStats | Single-pass аккумуляторы в цикле `for` | ⬜ M05 |
| ReportService.buildOverall | То же (single-pass) | ⬜ M05 |
| ReportService.buildWeekly | Single-pass + Map accumulator | ⬜ M05 |
| ReportService.buildTopMissed | Оставить как есть (уже single-pass `toMap` с merge) | ✅ no-op |
| LessonService.getLessonsForGroup | SQL Pageable в Repository | ⬜ M05 |
| `AttendanceStatsService` / `ExcuseAnalyticsService` | Не существуют в коде — PLAN.md выдумка | ✅ no-op |
| `LessonService.findOneOffLessons` | Не существует — 03 P2-5 = другой hotspot | ✅ no-op |
| `docs/future-ideas.md` (NEW-146) | Mongo aggregation + `lesson_alive` denormalization | ⬜ M05 |
| Integration-тест correctness | Sanity-тесты что accumulator даёт те же числа | ⬜ M05 |

**Альтернативы отклонены:**
- **Full Mongo `$group` pipeline** — блокируется `filterExistingLessons`
  invariant'ом. Правильное решение — денормализация alive flag, но
  это M06/M07 scope.
- **Удалить `filterExistingLessons`** — теряется defence-in-depth
  guarantee, eventconsistency risk при broker downtime.

**Estimate:** ~3-4ч.
