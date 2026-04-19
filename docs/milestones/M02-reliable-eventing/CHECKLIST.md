# M02 Checklist

Порядок важен — ShedLock готовит почву для publisher-job, outbox использует
ShedLock, contract-тесты валидируют уже работающий flow.

## Группа 1 — ShedLock в schedule-service (canonical)

- [x] `schedule-app/build.gradle.kts` → `shedlock-spring` + `shedlock-provider-jdbc-template` (версия — через `libs.versions.toml`)
- [x] Flyway `V10__shedlock_table.sql` в schedule → таблица `shedlock(name PK, lock_until, locked_at, locked_by)`
- [x] `SchedulingConfig` → `@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")` + `LockProvider` bean (JdbcTemplate, usingDbTime)
- [x] N/A — `LessonGenerationService.regenerateUpcoming()` не существует (см. NOTES 2026-04-19)
- [x] N/A — `OneOffLessonReconciler.reconcile()` не существует (см. NOTES 2026-04-19)
- [x] `@SchedulerLock(name="lesson-status-transition", lockAtMostFor=PT2M, lockAtLeastFor=PT10S)` на `LessonStatusTransitionJob.runTransitions()`
- [x] Smoke IT `ShedLockSmokeIntegrationTest`: 2 провайдера → только первый держит lock, second.lock() empty; lockAtLeastFor семантика

## Группа 2 — ShedLock в academic + attendance (NEW-28 аудит)

- [ ] Explore subagent: найти все `@Scheduled` в academic/attendance
- [ ] `academic-app/build.gradle.kts` + Flyway shedlock-table (academic_db)
- [ ] `attendance-app/build.gradle.kts` + `shedlock-provider-mongo` (не jdbc)
- [ ] Добавить `@SchedulerLock` на найденные `@Scheduled` методы
- [ ] Для методов, которые НЕ должны scale (single-instance by design) — явный `@SuppressWarnings("SingleInstance")` + комментарий

## Группа 3 — Outbox решение (DECISIONS.md) + миграции

- [ ] DECISIONS.md: shared-outbox vs copy-paste (NEW-6) — зафиксировать выбор перед кодом
- [ ] Flyway `V{N}__outbox.sql` в academic (PG, `academic_outbox` таблица)
- [ ] Flyway `V{N}__outbox.sql` в schedule (PG, `schedule_outbox` таблица)
- [ ] Mongo collection init / Flyway-Mongo для attendance (`attendance_outbox`)
- [ ] Индекс `(status, created_at)` для publisher-job performance
- [ ] Retention: документировать в migration comments «cleanup job drops sent>7d»

## Группа 4 — Outbox infrastructure (Entity + Repository + Publisher)

- [ ] `OutboxEntity` (JPA для PG) / `OutboxDocument` (Mongo для attendance)
- [ ] `OutboxRepository` + `findPendingBatch(limit)` запрос
- [ ] `OutboxPublisherJob` — `@Scheduled(fixedDelay=5000)` + `@SchedulerLock(name="outbox-publisher")` + `@Transactional`
- [ ] Publisher: read batch → send через `RabbitTemplate` → mark sent в той же tx
- [ ] Error handling: `status=failed`, `retry_count++`, `last_error` (exponential backoff в публикации не делаем в M02 — fixed 5s pull)
- [ ] Unit-тесты `OutboxPublisherJobTest` (mock RabbitTemplate)

## Группа 5 — Refactor существующих publisher'ов на outbox

- [ ] Explore: найти все места прямой публикации в Rabbit (`rabbitTemplate.convertAndSend` / `AmqpTemplate.send`) в 3 сервисах
- [ ] `EventPublisher.publish(event)` — переписать: вместо Rabbit → запись в outbox в той же `@Transactional`
- [ ] Проверить что сериализация event в JSON payload сохраняет snake_case (через shared-events `DomainEvent`)
- [ ] Integration-тест «kill Rabbit → тест event сохраняется в outbox → restart Rabbit → outbox publishes → consumer receives»

## Группа 6 — Outbox cleanup + метрики

- [ ] `OutboxCleanupJob` — `@Scheduled(cron="0 0 3 * * *")` + `@SchedulerLock` → удаляет `sent` rows старше 7д
- [ ] Integration-тест cleanup с `@MockBean Clock` (подменяем время)
- [ ] Micrometer метрика `outbox.lag` = COUNT pending rows (gauge, публикуется в Prometheus)
- [ ] Метрика `outbox.published.total` (counter, labels: service, event_type)

## Группа 7 — Event schemas (P2-11/7)

- [ ] `event-schemas/_common.json` — master с `$defs`: lessonNumber (min:1), traceId (string), eventVersion (integer const:1), occurredAt (date-time)
- [ ] Explore: перечислить все существующие `event-schemas/*.json`
- [ ] Каждая schema — refactor на `{"$ref":"_common.json#/$defs/..."}` для общих полей
- [ ] Unit-тест: validator загружается и понимает `$ref` (networknt json-schema-validator)

## Группа 8 — Contract-тесты events (C1-5)

- [ ] `shared-events` + `shared-test-containers`: `EventSchemaValidator` helper (поддержка `$ref` через FileSystemSchemaLoader)
- [ ] `AbstractEventContractIT` — базовый test-класс: publish event → read from outbox → validate against schema
- [ ] По одному contract-тесту per event-type в каждом сервисе (lesson.started, lesson.closed, attendance.marked, и т.д.) — минимум 5 шт в M02
- [ ] Test matrix: publish через `EventPublisher` → ждёт до OutboxPublisherJob → reads from Rabbit sink → validates schema

## Группа 9 — ArchUnit rule (NEW-28)

- [ ] ArchUnit тест в каждом backend-сервисе: `methodsThat().areAnnotatedWith(Scheduled.class).should().beAnnotatedWith(SchedulerLock.class).orShould().beAnnotatedWith(SuppressWarnings.class, "SingleInstance")`
- [ ] Негативный тест: добавить тестовый `@Scheduled` без lock → ArchUnit fail

## Группа 10 — Документация

- [ ] `docs/event-schemas.md` (NEW-48) — создать с versioning policy + `$defs` разделом
- [ ] `docs/architecture.md` → раздел «Reliable eventing»: диаграмма outbox flow (listener → outbox tx → publisher job → Rabbit → consumer)
- [ ] `CHANGELOG.md` → `[Unreleased]` → `### Added` (ShedLock, outbox, contract-тесты) + `### Changed` (publisher-flow через outbox)
- [ ] `CLAUDE.md` → статус M02 ✅ + упомянуть outbox в архитектуре (если уместно)

## Группа 11 — Финал

- [ ] Acceptance criteria из PLAN.md прогнаны разово
- [ ] `./gradlew build` зелёный полностью (5 сервисов + 4 shared)
- [ ] Optional: `bug-hunter` subagent на diff milestone'а (один вызов)
- [ ] Финальный коммит `chore(m02): bug-hunter fixes + post-mortem + close`
- [ ] Post-mortem в PLAN.md
- [ ] M02 → ✅ в `docs/milestones/README.md` + CLAUDE.md
- [ ] `git tag v0.0.0-alpha.2` на последнем коммите (если по workflow)

---

_Если задача занимает > 4 часов — разрежь её прямо здесь и отметь._
