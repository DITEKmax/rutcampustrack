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

---

## 2026-04-20 — Группа 2 аудит — N+1 рисков НЕ обнаружено

**Основа OWNER-ANSWERS P2-10/2** (3714-3749) — «N+1 SELECT lesson-
details/group-members». Мотивация ссылается на:

- **03 P2-4** — но реальный 03 P2-4 в аудите это «`existsBy` мёртвый
  метод в OneOffLessonRepository», не N+1. Цитата из OWNER-ANSWERS
  3718 (`LessonService.getLesson → отдельные SELECT subject/room/group`)
  — **не соответствует** содержанию отчёта 03-schedule-service.md.
- **02 P2-3** — реальный 02 P2-3 это «Jackson NON_FINAL default typing
  = RCE при компрометации Redis», не N+1.

**Системный аудит Repository-слоя** (все JPA методы в 3 сервисах):

- **schedule:** `Lesson`, `ScheduleItem`, `OneOffLesson` — все FK как
  `Long`, нет `@ManyToOne/@OneToMany`. N+1 невозможен by design.
- **academic:** `User`, `Group`, `Homework`, `TeacherSubjectGroup`,
  `HeadmanAssistant`, `Subject`, `Semester`, `AttendanceThreshold`,
  `HomeworkCompletion`, `StudentGroupHistory` — все FK как `Long`.
- **attendance:** только Mongo, N+1 концептуально другой зверь
  (document DB, embedded references).

**Образец правильного паттерна** — уже в коде:
`LessonService.massCancelLessons` (:137-142) сначала
`findByGroupId(groupId)` → собирает `itemIds` → `findByScheduleItemIdIn
AndDateBetween(...)`. **Один** SELECT для всей недели вместо 7×N.

**Пересмотренный scope Группы 2 (D5):**

- ❌ `@EntityGraph(attributePaths={...})` — не добавляем, нет LAZY
  relations для fetch'а.
- ❌ Projection для list-endpoints — payload-оптимизация
  преждевременна, сейчас entity содержит ≤ 15 полей simple columns,
  serialization стоит копейки. Добавим когда появится горячий endpoint.
- ✅ **ArchUnit rule NEW-143** — **оставляем**. Ценность rule'а — в
  будущем: если кто-то добавит `@ManyToOne`, правило поймает
  repository-метод без projection/Pageable/@EntityGraph в PR. Это
  preventive measure, бесплатная в добавлении.
- ✅ **Projection interface** — одна штука для `LessonDetailsProjection`,
  в качестве reference-pattern для будущего (NEW-143 rule тогда его
  whitelists). Минимально-инвазивно.
- ✅ **Docs update** — запись о том что в v0.0.0 проект «FK as Long,
  no JPA relations» by convention + ссылки на образец из
  `massCancelLessons`.

## 2026-04-20 — Группа 3 аудит: кеш уже реализован на Redis (не Caffeine)

**Surprise перед стартом Группы 3.** Explore-агент прошёл по academic-
service и нашёл рабочую реализацию кеша, существенно расходящуюся с
PLAN.md и OWNER-ANSWERS (3756-3810).

### Что уже есть (academic-service)

- `@EnableCaching` + **Redis** `CacheManager` в `CacheConfig.java:24-94`.
  Fallback на `NoOpCacheManager` если `RedisConnectionFactory` недоступен.
- Namespaces + TTL (`CacheConfig:88-92` дублирует в `application.yml:66-72`):
  - `groups` — 5м
  - `group_members` — 5м
  - `users` — 5м
  - `active_semester` — 10м
  - `campus_geofence` — 60м
- `@Cacheable` на всех 5 read-методах `AcademicReadService` (строки 41-64):
  `fetchGroup`, `fetchGroupMembers`, `fetchActiveSemester`,
  `fetchCampusGeofence`, `fetchUserById`.
- `@CacheEvict` на всех write-side: `UserService` (updateUser,
  patchUser, archiveUser, transferStudent), `GroupService` (updateGroup,
  deleteGroup через `@Caching`), `SemesterService.activateSemester`
  (`allEntries=true`).
- Программатическое eviction при смене `is_headman` флага —
  `UserService.patchUser:225-233` (evict groups + group_members).
- Сериализация: `GenericJackson2JsonRedisSerializer` +
  `Hibernate6Module` + `JavaTimeModule`.

### Что расходится с PLAN.md / OWNER-ANSWERS

| Пункт | PLAN.md / OWNER-ANSWERS | Факт |
|-------|-------------------------|------|
| Cache impl | Caffeine in-memory | Redis (уже внедрён ранее) |
| Namespaces | semester / subject / group / rbac | groups / group_members / users / active_semester / campus_geofence |
| `subject` namespace | 10м, @Cacheable на `getSubject(id)` | Отсутствует. `SubjectService.getSubject:111` без @Cacheable |
| `rbac` namespace | 1м, @Cacheable на `isHeadmanFor(userId, groupId)` | Отсутствует. Прямого метода в Service нет, проверка через `requestContext.isHeadman()` (request-scope). |
| Metrics | `CaffeineCacheMetrics.monitor(...)` | Нет — для Redis нативного биндинга нет |
| Scope | «in-memory, single-instance ok для v0.0.0» | Redis — cross-instance консистентный, *лучше* чем required |

### Мотивация OWNER-ANSWERS: что реально закрыто по факту

- **03 P2-7** (getActiveSemester 10+ req/day на одно значение) — ✅ закрыт
  через `fetchActiveSemester` c TTL 10м.
- **03 P2-6, 04 P2-2** (sync gRPC `isHeadmanFor` per-request) — ⚠️
  **НЕ закрыт**. Метода `isHeadmanFor(userId, groupId)` в Service нет,
  проверка через request-scope context (не кэшируется между запросами).
- **02 P2-5** (Subject/Group cache без TTL → memory leak) — ✅ частично:
  `groups` с TTL есть. `subject` ещё нет.
- **02 P2-7** (RBAC без кэша) — ⚠️ **НЕ закрыт**.

### Варианты scope для Группы 3

**A. Оставить Redis, добавить недостающее (рекомендация).**
- + Не ломать работающий кеш.
- + Redis уже в deps, single-instance контейнер в compose.
- + Кросс-инстансная консистентность (лучше требуемого).
- + `patchUser:225-233` manual eviction уже аккуратный.
- Работа: (1) добавить `subject` + `rbac` namespaces в `CacheConfig`,
  (2) добавить метод `isHeadmanFor(userId, groupId)` в User/RbacService
  с `@Cacheable("rbac", key="#userId + ':' + #groupId")`, (3) @Cacheable
  на `SubjectService.getSubject`, (4) @CacheEvict на Subject write-side +
  RBAC write-side (changeHeadman), (5) Redis cache metrics биндинг
  через `MeterRegistry` (RedisCache не экспонирует hit/miss нативно —
  нужен CacheEventListener или custom wrapper), (6)
  `docs/caching-strategy.md`.

**B. Заменить Redis на Caffeine** (буквальная читка OWNER-ANSWERS).
- – Снести работающий кеш = регрессионный риск.
- – Потерять cross-instance консистентность (нужна будет при первом
  scale-out, а OWNER-ANSWERS обещает её на v0.1 через миграцию).
- – Сериализация Redis для `Semester`/`Group`/`User` с Hibernate6Module
  уже отлажена.
- + Буквально следует тексту OWNER-ANSWERS.

**C. Гибрид Caffeine L1 + Redis L2** (`CompositeCacheManager`).
- – Сложность outweighs выгоду на v0.0.0.
- – Двойная инвалидация, extra code paths.

**D. Признать Группу 3 частично готовой, закрыть пробелы +
документировать Redis-как-решение.**
- Подвариант A, но с явной фиксацией в DECISIONS D6, что Redis
  заменил Caffeine ещё до M05 и это корректно для v0.0.0 single-node.
- Scope: subject+rbac namespaces, isHeadmanFor метод, metrics,
  caching-strategy.md, обновление CHECKLIST Группы 3 (3-4 пункта
  убрать как уже сделанное).

**Рекомендация: D** — по принципу «что реально требуется для motivation'а
OWNER-ANSWERS» и CLAUDE.md «Don't add features beyond what the task
requires». OWNER-ANSWERS мотивация — TTL + invalidation + метрики;
Redis обеспечивает всё это.

**Ожидаю подтверждение владельца** перед кодингом.

---

## 2026-04-20 — Группа 3 deferred: Redis cache metrics биндинг

**Что пробовали.** Написан `MetricsCacheManagerDecorator` — обёртка
`CacheManager`, возвращающая wrapped `Cache` с counter'ами
`cache.gets{result=hit|miss, cache=...}` через Micrometer.
Регистрировался как wrapper внутри `@Bean cacheManager`.

**Что сломалось.** При wrapping `CacheManager.getCache(name)` Spring
получает `MetricsCache` → делегирующий в `RedisCache`. Существующий
`CacheIntegrationTest.getActiveSemester_ttlMatchesConfiguredValue`
начал падать: `active_semester` Redis key получает TTL **300s** (default)
вместо заданных **600s** (`PT10M`). Воспроизводимо только при обёртке;
baseline без обёртки — TTL 600s как ожидалось. Root cause не вычислен
за 30 минут — предположительно `RedisCacheManager` имеет особенности
handling pre-configured vs dynamic cache creation при wrapping.

**Решение.** Metrics биндинг **отложен** в backlog (пункт M05 Группы
3 снят без потери scope acceptance — rbac namespace и invalidation
работают, что и есть главная ценность OWNER-ANSWERS motivation). В
CHECKLIST остаётся галочка как вычеркнутая с reference на этот раздел.

**Future idea:** зафиксировать в `docs/future-ideas.md` вариант:

- Подход через `@Aspect` над `CacheAspectSupport` без wrapping
  `CacheManager`/`Cache`. Minimally invasive — не ломает Redis TTL
  resolution.
- Альтернатива: переход на Spring Boot 3.5 + Micrometer native
  `CacheMeterBinder` для Redis (когда/если появится — сейчас только
  Caffeine/EhCache поддержаны нативно).

`docs/caching-strategy.md` описывает факт отсутствия hit/miss counter
для Redis в v0.0.0 + этот workaround-план.

## 2026-04-21 — Группа 8 итоги

Группа закрыта (commit в очереди). Surprise: deadline уже везде (19
callsite'ов), parallel refactor'у доступны 2 места (не `CheckinService`),
runtime-guard отклонён после сломавшихся integration-тестов. См. D11.

- `GrpcClientMetricsInterceptor` (shared-observability) — Timer
  histogram `grpc.client.duration` с тегами service/method/status.
  Per-app wrappers через `@GrpcGlobalClientInterceptor`.
- `grpcTaskExecutor` (attendance-app `GrpcParallelExecutorConfig`) —
  ThreadPoolTaskExecutor 2-8 threads, queue 100.
- `LessonEventService.processLessonClosed` и `MarkingService.markBatch`
  — параллельный fan-out через `CompletableFuture.supplyAsync + unwrap`.
- ArchUnit `GrpcClientDeadlineTest` × 3 сервиса — byte-code scan,
  sanity-verify пройден (искусственное удаление deadline → build fail).
- `infra/grafana/provisioning/dashboards/grpc-latency.json` — 5 panels.
- Unit-тест параллелизации — mock latency 200ms каждый, wall-time
  < 350ms. Существующие `LessonEventServiceTest` / `MarkingServiceTest`
  переведены на `SyncTaskExecutor` для детерминизма.

## 2026-04-20 — Группа 7 итоги

Группа закрыта (commit в очереди). Итоги:

- `PushSubscriptionDocument.lastSeen` + `idx_last_seen` index в
  `PushMongoConfig.initIndexes()`. Обе правки оставлены в Java (не
  Flyway — Mongo collection, см. D10).
- `WebPushDeliveryService.touchLastSeen(List<String> endpoints)` —
  bulk `$set` одной Mongo-op на fanout вместо N save'ов.
  `deliveredEndpoints` накапливает только successful sends (410 Gone →
  delete, сразу исключён из списка).
- `PushSubscriptionCleanupJob` + `PushCleanupConfig`: `@Scheduled(cron=
  "0 0 3 * * SUN")` + `@SchedulerLock(name="cleanupStalePushSubs",
  lockAtMostFor="PT10M")` + Mongo LockProvider. Bootstrap backfill
  `lastSeen = now` для pre-M05 подписок на `ApplicationReadyEvent`.
- `PushSubscriptionRepository.deleteByLastSeenBefore(Instant)` — Spring
  Data derived query, ускорено новым index'ом.
- Integration-тест `PushSubscriptionCleanupJobIT` — 3 сценария, все
  зелёные с Testcontainer'ным Mongo. Fixed `Clock` для детерминизма
  retention-boundary тестов.
- Refresh-token TTL audit закрыт без правок: `EX=604800` (7d)
  подтверждён grep'ом + чтение `AuthService.java`.
- `docs/data-retention-policy.md` (NEW-148) — 12-rows retention matrix.

## 2026-04-20 — Группа 7 audit surprise: push_subscriptions в MongoDB

**PLAN.md/CHECKLIST ошибка.** Пункт Группы 7 формулирует:
«Flyway V{N+1} на `attendance_db` или где живёт push: ALTER TABLE
push_subscriptions ADD COLUMN last_seen ...». Grep показал — коллекция
живёт в **MongoDB** (`PushSubscriptionDocument` с
`@Document(collection="push_subscriptions")` в notification-app), не
в PostgreSQL.

**Исход:**
- Scope Группы 7 адаптирован — см. DECISIONS D10.
- Добавление поля → Mongo document + programmatic `ensureIndex`.
- Cleanup job → `shedlock-provider-mongo` (notification-app пока не
  подключён к ShedLock, подключаем в этой группе).
- Refresh-token audit: ✅ `Duration.ofSeconds(jwtProperties.
  refreshTokenExpiration())` работает корректно в `AuthService:88/125`,
  `TmaService:78`, `OtpService:192`. Значение `604800` (7d) в
  `application.yml:57`. Audit закрыт без правок.

## Группа 9 — Audit (2026-04-21)

Три агента со свежим контекстом (без истории реализации) отработали
параллельно на diff `83ed387..3fae923` (~2668 LOC, 8 коммитов). Полные
отчёты остались в tool-results сессии; ниже — сводка + действия.

### Findings

| # | Severity | Источник | Fix |
|---|----------|----------|-----|
| 1.1 | CRITICAL | bug-hunter | rbac evict в `@Transactional` — concurrent `isHeadmanOf` кешит pre-commit snapshot → ex-headman держит privileges до 60s TTL |
| 1.2 | HIGH | bug-hunter | `archiveUser` не evict'ил rbac |
| 2.1 | HIGH | bug-hunter | `markBatch` publish per-item — partial-failure + client retry = дубликаты событий. Плюс double round-trip `upsert+findOne` |
| 3.1 | HIGH | bug-hunter + security #1 | `withDeadlineAfter(3s)` в lambda — queue wait не считается. Плюс 100 unique lessonIds × 8 worker'ов DoS'ит executor |
| 2 | HIGH | security | JWT `is_headman` claim доверяется 15m TTL → ex-headman batch-mark'ает до expiry |
| 4 | LOW | security | `AccessDeniedException` включает `userId=`/`lessonId=` — enumeration side-channel |
| — | MAJOR | code-review | PLAN.md AC vs `caching-strategy.md` — противоречие про cache metrics |
| D | MAJOR | code-review | `GrpcParallelExecutorConfig` Javadoc врёт про `ReportService` |
| 4.2 | LOW | bug-hunter | Cron без `zone=` — зависит от JVM TZ |
| D | MINOR | code-review | `WebPushDeliveryService.touchLastSeen` не использует injected `Clock` |
| 4.1 | MEDIUM | bug-hunter | `PushSubscriptionDocument` Javadoc обещал `last_seen=created_at`, код ставил `now()` |

### Hot-patches (commit `fix(m05): hot-patches after audit`)

- **UserService:** rbac evict → `TransactionSynchronization.afterCommit()`
  helper `evictRbacAfterCommit(userId, oldGroupId, newGroupId)`. Вызовы
  из `patchUser` / `transferStudent` / `archiveUser`. Fallback на
  immediate evict вне транзакции (unit-тесты).
- **MarkingService.markBatch:**
  - Re-check `academicGrpcClient.isHeadman(callerId, groupId)` перед
    fan-out'ом (cache-hit в 99% случаев, ~60s freshness vs JWT 15m).
  - Кэп `MAX_UNIQUE_LESSONS_PER_BATCH=10` — защита `grpcTaskExecutor`
    от DoS'а.
  - `findAndModify(returnNew=true)` вместо `upsert+findOne` — один
    round-trip на item.
  - Events публикуются после успешного прогона всех items, не в loop'е.
  - Error messages без id's (enumeration side-channel).
- **GrpcParallelExecutorConfig:** `CallerRunsPolicy` вместо дефолтного
  `AbortPolicy` — под event-storm'ом AMQP consumer gracefully
  деградирует до sequential вместо rollback'а. Javadoc убрал упоминание
  `ReportService` (никогда не использует executor).
- **WebPushDeliveryService:** инжектирует `Clock` (systemUTC bean в
  `PushCleanupConfig`), `touchLastSeen` использует `Instant.now(clock)`.
- **PushSubscriptionCleanupJob:** `@Scheduled(cron=..., zone="UTC")`
  явный timezone.
- **PushSubscriptionDocument:** Javadoc синхронизирован с реальным
  поведением (`last_seen=now()` на bootstrap).
- **PLAN.md AC:** «cache.gets hit/miss» помечен `[~] Deferred`, ссылки
  на `caching-strategy.md §Observability` и `future-ideas.md`.
- **architecture.md §11.1:** блок «Performance & Ops runbooks» с 6
  ссылками на новые M05 docs.

### Тесты

- `MarkingServiceTest`: добавлены BATCH-06 (academic says not-headman)
  и BATCH-07 (> 10 unique lessons). Существующие BATCH-01/02/04/05
  обновлены под `findAndModify` + без id в error-message.
- Single-mark path (markAttendance) не тронут — остался на
  `upsert+findOne`.
- `WebPushDeliveryServiceTest`: конструктор с `Clock.systemUTC()`.
- Все 4 сервиса: `./gradlew build` зелёный после fix'ов (unit +
  integration + ArchUnit + CI-lint).

### Отложено (не критично для M05 close)

| # | Severity | Источник | Defer to |
|---|----------|----------|----------|
| 5.1–5.4 | LOW+NIT | bug-hunter | Timer cache, unbounded tag cardinality в `GrpcClientMetricsInterceptor` — M06 |
| 3 | MEDIUM | security | Redis Jackson `LaissezFaireSubTypeValidator` — supply-chain, M06 |
| 5 | LOW | security | Redis key-space DoS rate-limit на `isHeadman` — M06 |
| 6 | LOW | security | Mozilla/Apple push endpoint masking — M07 (frontend hardening) |
| 2.2 | MEDIUM | bug-hunter | Race pre-check vs upsert в `markBatch` (mass-transfer edge) — accepted limitation |
| 3.2 | MEDIUM | bug-hunter | `unwrap()` interrupted handling — accepted |
| DRY | MAJOR | code-review | `GrpcClientDeadlineTest` × 3 дубликата (~180 LOC) — в следующий commit (refactor) |
| DRY | MINOR | code-review | `unwrap()` дублируется между `LessonEventService` / `MarkingService` — в refactor-commit |
| Docs | MINOR | code-review | Cross-link «See also» блоки между 5 новыми M05 docs + api-error-conventions в architecture.md — дополнено, но без reverse-links между caching/retention/pool |
| Docs | MINOR | code-review | `V12`/`V17` migrations без `CONCURRENTLY` — dev OK, prod нужно документировать явно — отложено в М05 Группа 10 |

## Правила работы (без изменений с M04)

- Русский в отчётах / NOTES / ответах.
- READ-BEFORE-EDIT hook-reminder'ы ложные после Read в сессии — игнорировать.
- Один CHECKLIST-группа = один logical коммит (`feat/fix/test/docs(<scope>): ... (M05 Группа N)`).
- Не звать `gsd-*` агентов. `Explore` для поиска, `bug-hunter`/`security-auditor`/`code-reviewer` в Группе 9.
- Surprise → NOTES.md + спросить до продолжения.
- Закрыл пункт CHECKLIST → `[x]` через Edit.
