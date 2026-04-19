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

- [x] Grep `@Scheduled` по всем backend-сервисам — результат в NOTES 2026-04-19
- [x] N/A — academic-service: 0 `@Scheduled` методов (ShedLock infra добавим в Группу 3 для OutboxPublisherJob)
- [x] N/A — attendance-service: 0 `@Scheduled` методов (ShedLock infra добавим в Группу 3 для OutboxPublisherJob, Mongo-провайдер)
- [x] N/A — нет дополнительных методов для `@SchedulerLock` в academic/attendance
- [x] `PublicKeyConfig.refresh()` (api-gateway) → `@SuppressWarnings("SingleInstance")` + комментарий про per-instance key cache

## Группа 3 — Outbox решение (DECISIONS.md) + миграции

- [x] DECISIONS.md: shared-outbox vs copy-paste (NEW-6) — зафиксировано 2026-04-19 (вариант a)
- [x] `services/shared/shared-outbox` scaffold: build.gradle.kts + settings include + OutboxStatus / OutboxRecord / OutboxStorage API
- [x] Flyway `V16__academic_outbox.sql` в academic (PG, `academic_outbox` таблица + 2 partial index)
- [x] Flyway `V15__shedlock_table.sql` в academic (prerequisite для OutboxPublisherJob в Группе 4)
- [x] Flyway `V11__schedule_outbox.sql` в schedule (PG, `schedule_outbox` таблица + 2 partial index)
- [x] N/A — Mongo collection init для `attendance_outbox` отложен в Группу 4: Mongo auto-создаёт collection по `@Document`, индексы инициализируются через `ApplicationRunner` по existing pattern `AttendanceIndexInitializer`
- [x] Partial index `created_at WHERE status='pending'` для publisher-job performance (вместо composite `(status, created_at)` — partial даёт smaller index + лучший selectivity на pending)
- [x] Retention: migration comments + `idx_*_sent_cleanup` partial index для OutboxCleanupJob (sent>7d)

## Группа 4 — Outbox infrastructure (Entity + Repository + Publisher)

- [x] `OutboxEntity` — JPA `@MappedSuperclass` в shared-outbox. Подклассы будут в сервисах (Группа 5).
- [x] `JpaOutboxStorage<E extends OutboxEntity>` — EntityManager queries, параметризован типом сущности
- [x] `MongoOutboxStorage` — native MongoTemplate + Document (без shared `@Document` класса, коллекция — параметр конструктора). `ensureIndexes()` для hot-path'ов.
- [x] `OutboxEventSender` — functional interface (callback для транспорта). Сервис передаёт в конструктор PublisherJob.
- [x] `OutboxPublisherJob` — `@Scheduled(fixedDelayString="${...:5000}")` + `@SchedulerLock(name="outbox-publisher")` + `@Transactional`. `publishBatch()` выделен для unit-тестов.
- [x] Publisher: read batch (100) → sender.send() → markSent/markFailed в той же tx
- [x] Error handling: `markFailed` → `status=failed, retry_count++, last_error` (truncated до 2000 chars). Retry выполняется automatically — sender Exception откатывает tx, row остаётся pending, следующий tick подхватит.
- [x] `OutboxPublisherJobTest` — 6/6 зелёных (empty batch, all success, sender throws, partial failure, ordering, lock-name)
- [x] N/A — Integration-тесты JpaOutboxStorage / MongoOutboxStorage отложены в Группу 5 (там естественно покрыто через реальный сервисный context)

## Группа 5 — Refactor существующих publisher'ов на outbox

- [x] Explore `rabbitTemplate.convertAndSend`: academic/schedule — через DomainEventListener (AFTER_COMMIT), attendance — 3 direct publisher'а (AttendanceEventPublisher, ExcuseEventPublisher, LateCheckinEventPublisher)
- [x] academic: AcademicOutboxEntity + OutboxConfig (Storage всегда + Publisher `@Profile("!test")`), DomainEventListener — BEFORE_COMMIT → outbox.save. RabbitOutboxEventSender для прод-путь. Hibernate `@JdbcTypeCode(SqlTypes.JSON)` на payload для jsonb casting.
- [x] schedule: ScheduleOutboxEntity + такой же OutboxConfig + DomainEventListener BEFORE_COMMIT + RabbitOutboxEventSender
- [x] attendance: MongoOutboxStorage (ensureIndexes) + OutboxConfig (MongoLockProvider) + 3 publisher'а переписаны на outbox.save с ObjectMapper.writeValueAsString
- [x] Snake_case сохраняется — JSON сериализуется как раньше (payload как строка в outbox)
- [x] Integration-тесты переписаны: academic EventIntegrationTest + GroupRenameEventTest — flushOutbox() helper в AbstractAcademicEventIntegrationTest (+ TransactionTemplate вокруг). schedule LessonCancelEventTest + LessonStatusTransitionJobTest + OneOffLessonEventPublisherIT — смотрят outbox напрямую через `findPending`. attendance ExcuseEventContractIT + CheckinIntegrationTest + ExcuseEventPublisherTest — flushOutbox + парсинг raw JSON body.
- [x] OutboxTestConfig (@Profile("test")) в academic + attendance для OutboxPublisherJob bean в тестах (без @EnableScheduling).
- [x] Build зелёный: 185/185 academic + 94/94 schedule + 146/146 attendance + 6/6 shared-outbox unit = 431 теста.

## Группа 6 — Outbox cleanup + метрики

- [x] `OutboxCleanupJob` — `@Scheduled(cron="${...:0 0 3 * * *}")` + `@SchedulerLock(name="outbox-cleanup")` → удаляет SENT rows старше `rutcampustrack.outbox.retention-days:7`. Clock параметр для тестируемости.
- [x] `OutboxCleanupJobTest` (shared-outbox unit) — 4/4 зелёные (cutoff, zero deletes, invalid retention, lock-name)
- [x] `OutboxCleanupIntegrationTest` (academic) — 1/1 зелёный: pending не трогается, recent SENT (3d) остаётся, old SENT (10d) удаляется. Clock подменён через `@TestConfiguration` + `@Primary`.
- [x] Micrometer `outbox.lag` — gauge через `OutboxMetrics` (bean в Storage-config). Источник — `storage.countPending()`.
- [x] Micrometer `outbox.published.total` (counter) + `outbox.failed.total` (counter) — tag `event_type`. Инкрементится внутри `OutboxPublisherJob.publishBatch()`.
- [x] OutboxConfig.Publisher во всех 3 сервисах создаёт `@Bean OutboxCleanupJob` + PublisherJob теперь принимает `MeterRegistry`.
- [x] AbstractAttendanceIntegrationTest.@BeforeEach drainOutboxBeforeEach — чистит leftover pending между тестами (reused Mongo container).

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
