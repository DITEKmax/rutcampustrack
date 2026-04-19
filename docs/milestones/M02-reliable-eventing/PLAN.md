# M02 — Reliable Eventing

**Статус:** ⬜ не начат
**Старт / финиш:** — / —
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

- [ ] `docker-compose down rabbitmq && docker-compose up rabbitmq` в середине
      интеграционного теста → события не теряются (outbox retries после
      восстановления Rabbit).
- [ ] Запуск двух инстансов `schedule-app` параллельно (smoke-тест) →
      `OutboxPublisherJob` выполняется на одном, не двойная публикация.
- [ ] Contract-тест `LessonStartedContractIT` валидирует payload
      `lesson.started` против `event-schemas/lesson.started.json`.
- [ ] `event-schemas/_common.json` с `$defs` создана, 14+ схем reference'ят.
- [ ] ArchUnit тест падает, если добавить `@Scheduled` метод без
      `@SchedulerLock` (негативный case).
- [ ] Outbox retention: `OutboxCleanupJob` удаляет `sent` rows старше 7д
      (integration-тест через `@MockBean Clock`).
- [ ] `./gradlew build` зелёный для всех 5 сервисов + 4 shared.
- [ ] Метрика `outbox_lag` (unsent events) экспортируется в Prometheus
      (connect to NEW-7 + интеграция с Micrometer). Не дашборд — только
      metric.

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

_Заполняется в конце milestone'а (измерения, surprises, что пошло не
по плану, что надо исправить в следующих milestones)._
