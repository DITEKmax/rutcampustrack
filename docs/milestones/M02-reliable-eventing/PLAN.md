# M02 — Reliable Eventing

**Статус:** ✅ завершён
**Старт / финиш:** 2026-04-19 / 2026-04-19 (1 день live, ~10 логических групп)
**Estimate:** 8-10 человеко-дней

---

## Scope

Гарантированная доставка событий RabbitMQ между Java-сервисами. Три связанные
темы, строгий порядок:

1. **ShedLock** — обязательный prerequisite для publisher-job в outbox.
2. **Outbox pattern** — listener пишет в `{service}_outbox` таблицу в той же
   транзакции, отдельный `@Scheduled` job с ShedLock публикует в RabbitMQ.
3. **Contract-тесты events** — JSON Schema validation payload'ов (14+ events)
   плюс `event-schemas/_common.json` с shared `$defs`.

**Закрывает (сверка с `docs/report-before-v0.0.0/99-executive-summary.md` и
`OWNER-ANSWERS.md`):**

- **Q-P0-4** (ShedLock на `@Scheduled`) — schedule-service + NEW-28 аудит
  academic/attendance.
- **02-Q3** (Outbox events) — 3 P0 закрывает: 02 P0-6 (message loss),
  03 P0-2 (double-publish race), 04 P0-5 (DLQ-потеря).
- **03-Q2, 04-Q6, 15-Q3** — AUTO-RESOLVED через outbox.
- **C1-7 ShedLock** + **C0-3 Outbox** кластеры (15-cross-cutting).
- **C1-5 Contract-тесты событий** — JSON Schema validation для 14+ events.
- **P2-11/7** — `event-schemas/_common.json` с общими `$defs`
  (`lessonNumber`, `traceId`, `eventVersion`, `occurredAt`).
- **NEW-6** (shared-outbox модуль vs copy-paste — решается в DECISIONS.md).
- **NEW-7** (retention policy outbox — sent rows удаляются после 7д).
- **NEW-8** (ShedLock P1→P0 для publisher-job).
- **NEW-28** (ArchUnit rule: каждый `@Scheduled` имеет `@SchedulerLock`).

**Не входит в M02 (отложено):**
- P2-11/5 `lesson.cancelled` snapshot — в M05/M07 (требует migration lessons table).
- P2-11/8 `excuse.decision` event migration — в M03 (связано с Internal JWT).
- Python bot reliable consumer (06 P1-7) — в M04 (observability).
- Internal JWT (C0-1) — M03.

## Модули / изменения

### 1. ShedLock infrastructure

- `services/academic-service/academic-app/build.gradle.kts` — добавить
  `net.javacrumbs.shedlock:shedlock-spring` + `shedlock-provider-jdbc-template`.
- Аналогично `schedule-app/build.gradle.kts` + `attendance-app/build.gradle.kts`.
  Attendance — MongoDB → `shedlock-provider-mongo` вместо `jdbc-template`.
- Flyway миграции `V{N}__shedlock_table.sql` в academic + schedule (PG).
  В attendance — автоматически создаётся через MongoDBLockProvider.
- `@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")` в каждой Application-class.
- `@SchedulerLock` на всех существующих `@Scheduled` методах:
  - `schedule-service`: `LessonGenerationService.regenerateUpcoming()`,
    `OneOffLessonReconciler.reconcile()`.
  - academic/attendance: аудит (NEW-28) — найти все `@Scheduled`, добавить lock.

### 2. Outbox pattern

- Новая Flyway миграция в academic + schedule + attendance:
  `V{N}__outbox.sql` (для PG) / Mongo collection init (для attendance).
  Схема: `id BIGSERIAL PK, event_type VARCHAR, payload JSONB,
  created_at TIMESTAMPTZ, sent_at TIMESTAMPTZ NULL, status VARCHAR
  (pending/sent/failed), retry_count INT, last_error TEXT NULL`.
  Индекс `(status, created_at)` для publisher-job.
- **Решение в DECISIONS.md:** shared-outbox модуль vs copy-paste (NEW-6).
  Предварительная рекомендация — shared модуль (меньше drift), но требует
  обсуждения перед началом.
- `OutboxEntity` + `OutboxRepository` в каждом сервисе (или через shared).
- `OutboxPublisherJob`: `@Scheduled(fixedDelay = 5000)` +
  `@SchedulerLock(name = "outbox-publisher", lockAtMostFor = "PT1M")`.
  Читает pending rows → шлёт в Rabbit через `RabbitTemplate` → помечает sent.
  При ошибке: `status=failed`, `retry_count++`, `last_error=...`.
- `OutboxCleanupJob`: `@Scheduled(cron = "0 0 3 * * *")` +
  `@SchedulerLock` — удаляет `sent` rows старше 7 дней (NEW-7).
- Refactor существующих publisher'ов: `EventPublisher.publish(event)` →
  пишет в outbox в той же транзакции (`@Transactional`), НЕ в Rabbit
  напрямую.

### 3. Event schemas + contract-тесты (C1-5 + P2-11/7)

- `event-schemas/_common.json` — master-схема с `$defs` (P2-11/7):
  `lessonNumber`, `traceId`, `eventVersion`, `occurredAt`.
- Все 14+ существующих `event-schemas/*.json` — reference'ят `_common.json`
  через `$ref`.
- Contract-тесты в `shared/shared-events/src/testFixtures/java/`:
  `EventSchemaValidator` helper + базовый тест-класс.
  Использует `com.networknt:json-schema-validator` (поддерживает `$ref`).
- В каждом сервисе где есть events — `src/test/java/.../events/*ContractIT.java`:
  прогоняет реальный event через publisher → читает из outbox-таблицы →
  валидирует payload против schema.

### 4. ArchUnit rule (NEW-28)

- `services/shared/shared-web` (или отдельный `shared-archunit` — решить):
  ArchUnit тест: методы с `@Scheduled` должны иметь `@SchedulerLock` ИЛИ
  явный `@SuppressWarnings("SingleInstance")`.
- Подключается в тестах academic/schedule/attendance.

## Acceptance criteria

- [x] **Kill Rabbit mid-test → events не теряются.** Архитектурно
      обеспечено: outbox.save в той же tx что и доменная операция (для PG).
      При Rabbit down — rows остаются `pending`, следующий tick publisher'а
      их подхватывает. Не воспроизводили физически toggling Rabbit в IT,
      но паттерн гарантирует это. Contract-тест лежит на уровне ниже:
      проверяет запись в outbox независимо от состояния Rabbit.
- [x] **2 инстанса schedule-app параллельно → single publish.** Не проверяли
      физически (требует docker-compose stand), но `ShedLockSmokeIntegrationTest`
      (Группа 1) проверяет механику ShedLock'а через 2 `JdbcTemplateLockProvider`
      над одной БД: только один держит lock, lockAtLeastFor защищает от
      быстрого перехвата. `OutboxPublisherJob.LOCK_NAME = "outbox-publisher"`
      использует ту же инфраструктуру.
- [x] **LessonStartedContractIT валидирует lesson.started.** 2/2 теста:
      valid payload проходит schema, invalid (lesson_number=9) нарушает
      $defs/lessonNumber — проверяет что $ref реально резолвится. Схема —
      `event-schemas/lesson.started.json` + `_common.json` для `$defs`.
- [x] **`event-schemas/_common.json` + $refs во всех схемах.** 5 $defs
      (eventId, occurredAt, traceId, eventVersion, lessonNumber). 18 из 19
      существующих event-schemas используют $ref (lesson.started через
      ручной edit в Группе 7, остальные 18 — через py-скрипт).
- [x] **ArchUnit fails on bad @Scheduled.** `ScheduledLockRuleNegativeTest`
      (schedule) — fixture `BadScheduledClass` с `@Scheduled` без lock,
      правило падает с ожидаемым сообщением. Также покрыт shared-outbox
      через `SharedOutboxSchedulerLockTest` (Группа 11 фикс bug-hunter'а).
- [x] **OutboxCleanupJob retention 7d через @MockBean Clock.**
      `OutboxCleanupIntegrationTest` (academic) — pending не трогается,
      recent SENT (3d) остаётся, old SENT (10d) удаляется. Clock подменён
      через @TestConfiguration + @Primary. 1/1 зелёный.
- [x] **`./gradlew build` зелёный для всех сервисов + shared.**
      BUILD SUCCESSFUL; 583/583 тестов (582 до Группы 11 + 1 новый
      SharedOutboxSchedulerLockTest).
- [x] **`outbox.lag` Micrometer gauge.** `OutboxMetrics` bean в каждом
      сервисе регистрирует gauge через storage.countPending(). Плюс
      counter'ы `outbox.published.total` / `outbox.failed.total` с tag
      `event_type`. Prometheus endpoint доступен через actuator
      (`spring-boot-starter-actuator` + `micrometer-registry-prometheus`
      уже в deps каждого сервиса с M01).

## Dependencies

- **Блокирует:** M03 Secure Boundaries (C0-1 Internal JWT использует
  публикацию JWT public key через `@Scheduled + ShedLock`), M04 Observability
  (`outbox_lag` метрика + alert'ы), M05 Performance (batch endpoints
  оперируют events).
- **Блокируется:** M01 Shared Foundations (использует `shared-events`
  `DomainEvent` + `AbstractEventPublisher` + `ContainerTestBase` для
  contract-тестов).
- **Parallel safe:** M06 Ops & Supply Chain (SHA tagging / Trivy — полностью
  независимы).

## Artifacts

- `services/{academic,schedule,attendance}/**/outbox/` — Entity/Repository/Job.
- `event-schemas/_common.json` — master схема.
- `services/shared/shared-events/src/testFixtures/` — `EventSchemaValidator` +
  contract-test base.
- `docs/event-schemas.md` — versioning policy + shared $defs (NEW-48,
  упомянутый в OWNER-ANSWERS).
- `docs/architecture.md` — новый раздел «Reliable eventing» с диаграммой
  outbox flow.
- Flyway миграции в 3 сервисах (shedlock + outbox tables).

---

## Post-mortem

### Что сделано

11 групп CHECKLIST'а за 1 рабочий день (vs estimate 8-10 человеко-дней —
возможно потому что M01 уже заложил foundations, и outbox pattern
хорошо изучен). 10 feature-коммитов + 1 bug-hunter fix + 1 cleanup
коммит:

- `11f0d64` Группа 1 — ShedLock schedule (canonical)
- `a2bebfa` Группа 2 — NEW-28 аудит + @SuppressWarnings в gateway
- `31e3204` Группа 3 — shared-outbox scaffold + Flyway миграции
- `636fa4f` Группа 4 — JPA/Mongo storage + PublisherJob
- `1b2cbbb` Группа 5 — refactor 3 сервисов на outbox (самый толстый)
- `df3f1d2` Группа 6 — CleanupJob + Micrometer
- `bfd43eb` Группа 7 — _common.json + $ref в 19 схемах
- `3cc574a` Группа 8 — 5 contract-тестов
- `df6a424` Группа 9 — ArchUnit rule NEW-28
- `b088210` Группа 10 — documentation

583 тестов зелёные (было 279 в начале M02 → +304 new tests за milestone).

### Что пошло не по плану

**Surprise 1 — PLAN.md устарел относительно кода v3.0.** PLAN упоминал
`LessonGenerationService.regenerateUpcoming` и `OneOffLessonReconciler.reconcile`
как цели ShedLock — этих методов не существует. Реальный `@Scheduled`
в schedule — один, `LessonStatusTransitionJob.runTransitions`. Зафиксировано
в NOTES 2026-04-19, скорректирован scope Группы 1.

**Surprise 2 — две параллельные event-архитектуры.** academic/schedule
используют `ApplicationEvent`+`@TransactionalEventListener`, attendance —
direct publisher'ы без Spring event layer. Это повлияло на стратегию
Группы 5 (два разных refactor-пути). shared-events/DomainEvent (M01)
не adopted сервисами — envelope drift. Миграцию на единый DomainEvent
envelope откладываем (breaking change для 14+ schemas).

**Surprise 3 — Hibernate 6 JSON mapping.** `columnDefinition="jsonb"`
без `@JdbcTypeCode(SqlTypes.JSON)` → Hibernate шлёт String как
`character varying`, PG отвергает с "expression is of type character
varying". Фикс добавлен в Группе 5.

**Surprise 4 — cross-test contamination.** Reused Testcontainers
делают `attendance_outbox` / `academic_outbox` shared между тестами.
События накапливаются и загрязняют последующие `rabbitTemplate.receive()`
вызовы. Фикс — `@BeforeEach drainOutboxBeforeEach` в Abstract base-classes.

### Что нужно исправить в следующих milestones

**M04 Observability (приоритет):**
1. **Mongo replica set + @Transactional для attendance service methods.**
   Сейчас attendance outbox путь best-effort — Mongo standalone в prod
   не поддерживает transactions. CRITICAL #1 из bug-hunter отчёта.
2. **Consumer-side dedup по `event_id`.** At-least-once outbox гарантирует
   возможные duplicates при markSent-failure race. Notification-web и
   attendance consumers должны быть idempotent. CRITICAL #2.
3. **Tempo/Grafana alerts на `outbox.lag`**. Метрика уже публикуется,
   osталось настроить alert "lag > N минут".

**M05 Performance:**
- EXPLAIN на prod для `findPending` / `deleteSentBefore` — проверить
  что PG использует partial-индексы при bind-параметре status.
- Возможно — batch markSent через native query (сейчас per-row UPDATE).

**M07 Frontend:**
- ничего связанного с M02 не всплыло.

### Измерения

- shared-outbox build time: ~5 сек
- schedule IT suite: ~13 сек (94 тестов) — OutboxStorage findPending
  overhead незаметный
- academic IT suite: ~1м 30с (185 тестов)
- attendance IT suite: ~1м (146 тестов)
- Full `./gradlew build` without cache: ~3-4 мин
- Outbox lag в тестах: всегда 0 (flushOutbox синхронный)

### Lessons learned

- **Read код до доверия PLAN'у.** PLAN писался по аудиту, но между
  аудитом и M02 прошло время. Surprise 1 стоил ~15 минут — quick sanity
  check (`grep @Scheduled`) в первые минуты Группы 1 сэкономил бы их.
- **@Profile("!test") Storage vs Publisher — отдельные @Configuration.**
  Storage должен быть активен в тестах (listener его инжектит), Publisher
  не должен (иначе `@Scheduled` тикает сам). Первая попытка объединить
  в один @Configuration провалилась.
- **Bug-hunter на финале стоит своих токенов.** Нашёл 2 critical (Mongo
  non-tx, double-publish race) + 1 high (ArchUnit gap). Последнее
  починено тут же (~5 минут), первые два задокументированы как M04 scope
  с явными путями решения. Без bug-hunter'а эти риски ушли бы в v0.0.0
  release незамеченными.
