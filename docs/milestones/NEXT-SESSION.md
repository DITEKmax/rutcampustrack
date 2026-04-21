# Промпт для следующей сессии

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Этого достаточно —
Opus сам откроет файлы и поймёт где мы остановились.

---

Продолжай работу над v0.0.0 milestones.

Контекст:
1. Архитектурный аудит зафиксирован в `docs/report-before-v0.0.0/`
   (16 отчётов + OWNER-ANSWERS.md 6400 строк + COVERAGE-AUDIT.md 354
   пункта + 99-executive-summary.md roadmap).
2. Рабочий процесс — lightweight milestones без GSD-orchestrator'а.
   Индекс: `docs/milestones/README.md`.
3. **M06 Ops & Supply Chain ✅ закрыт 2026-04-21**, tag `v0.0.0-alpha.7`.
   6 milestones закрыто (M01-M06). Осталось 3:
   M07 Frontend Hardening, M08 Test Infra, M09 Prod Release Blockers.

**Следующий milestone по dependency graph:** **M07 Frontend Hardening**
(зависит от M03b, который закрыт). ~10-12д. Scope — CSP self-host,
a11y, openapi-typescript, UX фиксы. Альтернатива — **M09 Prod Release
Blockers** (зависит от M02+M03a, оба закрыты), ~4-5д, точечные P0 из
Фазы 3. Parallel safe — можно делать M09 параллельно с M07.

**М06 defer'ы для M07/M09:**
- Redis cache hit/miss metrics через `RedisCacheMeterBinder` (MINOR).
- `/actuator/**` excluded from tracing (custom OTel Sampler bean).
- isHeadman principal-based userId (gRPC proto redesign, breaking).
- nginx / postgres / mongo / redis / rabbitmq digest-pin.
- rct-nginx 5-min background reload → `nginx -t` + Loki alerting.
- SBOM generation + cosign signing (M08).
- Trivy action sha-digest pin (M08).

M07 Scope (из `99-executive-summary.md` P1-C + Фаза 5 P2-7A/7B):
- CSP self-host лендинг-ресурсов (C0-6, 12 P0-1, 13 P0-4) — склонировать
  Fontshare/Google Fonts/unpkg/jsdelivr в `frontends/landing/dist/assets/
  vendor/`, CSP `'self'` без whitelist'а внешних CDN.
- openapi-typescript для PWA/web-panel (QC2) — типы из Swagger spec
  вместо ручных interface-копий.
- a11y audit: semantic HTML, axe-core в Playwright, jsx-a11y ESLint,
  prefers-reduced-motion (QC*), SMIL→CSS (QE3).
- UX фиксы: pull-to-refresh, useSwipeHandler, useDateNavigation,
  geolocation high-accuracy (P2-7A).
- QE4 landing meta-тегов (og/twitter/canonical/robots/JSON-LD).
- M06 defer'ы из списка выше (Redis cache metrics, /actuator/** tracing,
  nginx digest-pin, принципиально-нужные для frontend hardening).

Альтернативно: **M09 Prod Release Blockers** (~4-5д, последний фаза-3
P0 cleanup: OTP через RabbitMQ, MessageDigest, cleanupOrphans, landing
deep-link, latecheckin/bot tests). Parallel safe с M07.

Что делать:
1. Создай `docs/milestones/M07-frontend-hardening/` из шаблона
   `docs/milestones/_TEMPLATE/`: PLAN.md + CHECKLIST.md + NOTES.md +
   DECISIONS.md.
2. PLAN.md наполни из OWNER-ANSWERS «Frontend» секций (QC1..7, QE3/4) +
   M06 defer'ы (см. список выше) + `docs/report-before-v0.0.0/
   09-frontend-pwa.md`, `10-frontend-web-panel.md`, `12-frontend-
   landing.md`, `13-infra-docker-ci.md` CSP section.
3. Разбей на группы по ~3-4ч atomic (образец — M06 G1-G9).
4. Спроси owner'а перед стартом для подтверждения scope'а.
5. `git log --oneline -15` — последние коммиты (`v0.0.0-alpha.7` на
   последнем M06 коммите; 100+ коммитов ahead origin; tags `v0.0.0-
   alpha.2..7` локальные).
6. Проверь docker-compose: `docker compose ps`. Инфра из прошлых
   сессий уже поднята.

Правила (без изменений с M05):
- Русский язык в отчётах / NOTES / ответах.
- Не звать `gsd-*` агентов. `Explore` для «найти все X»,
  `bug-hunter` / `code-reviewer` / `security-auditor` — в Группе audit'а.
- Surprise → NOTES.md + спросить до продолжения.
- Micro-решение → DECISIONS.md (D1+).
- Закрыл пункт CHECKLIST → `[x]` через Edit.
- Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — ложные.
- Push на origin / создание PR — только с явного `go` пользователя.
- `CHANGELOG.md [Unreleased]` обновляй при значимых изменениях.

Когда milestone закрыт:
1. Все пункты CHECKLIST отмечены `[x]`.
2. Все acceptance criteria в PLAN.md пройдены (`./gradlew build`
   зелёный + integration + ArchUnit + CI-lint).
3. Post-mortem секция дописана в PLAN.md.
4. Статус в `docs/milestones/README.md` → ✅ готов.
5. Тег `git tag v0.0.0-alpha.8` на последнем коммите milestone'а
   (alpha.7 — M06, alpha.8 — M07, alpha.9 — M08, `v0.0.0` — после M09).
6. Сообщить owner'у финальный summary + ссылку на следующий
   milestone (M08 или M09).

Старт:
> Читаю README.md → 99-executive-summary.md → M05 post-mortem →
> 06-supply-chain-report.md. Через минуту скажу какие группы вижу
> в scope M06.

---

## M05 закрыт (2026-04-21) — итоговая запись

**Tag:** `v0.0.0-alpha.6` (локально, без push).
**Последний коммит M05:** `<финальный G10 commit hash>` (docs-закрытие).
**Closed commits M05:** от `83ed387` (G1) до финального G10 — всего
13 коммитов (8 feat/perf + 3 audit/refactor + 2 docs).

### Итоги по группам

| # | Группа | Коммит | Что сделано |
|---|--------|--------|-------------|
| 1 | Composite indexes + perf baseline | `83ed387` | Flyway V12/V17 + programmatic Mongo index + 3 `*PerformanceIT` |
| 2 | Preventive N+1 guard | `6802e7f` | ArchUnit `RepositoryNPlusOneGuardTest` + `LessonDetailsProjection` reference |
| 3 | Redis cache дополнения | `50fc576` | rbac/subject namespaces + `caching-strategy.md` |
| 4 | Batch endpoints | `e0f82de` | `POST /attendance/marks/batch` + PWA bulk-mark + `api-error-conventions.md` |
| 5 | Single-pass + SQL pagination | `c3bd518` | `ReportService` O(N) + `LessonService` `Pageable` + `future-ideas.md` |
| 6 | HikariCP tuning | `dbdc38c` | pool=20/idle=5/leak=60s + `HikariPoolExhaustion` alert + `connection-pool-tuning.md` |
| 7 | Push-subs retention + cleanup | `1aa5e75` | `last_seen` + `idx_last_seen` + weekly cleanup + `data-retention-policy.md` |
| 8 | gRPC metrics + parallel + ArchUnit | `3fae923` | `GrpcClientMetricsInterceptor` + parallel fan-out + `grpc-latency.json` |
| 9 | Audit hot-patches | `ba0b233` | 10 fixes (rbac afterCommit, markBatch, CallerRunsPolicy, re-check isHeadman, ...) |
| 9 DRY | Refactor | `ff3d6e3` | `GrpcDeadlineArchRules` + `AsyncGrpcUtils` (-74 LOC net) |
| 10 | Docs + closure | `<G10>` | CHANGELOG + CLAUDE.md + README + PLAN.md post-mortem + tag |

### Key deferrals → M06+

| Finding | Severity | → milestone |
|---------|----------|-------------|
| Redis Jackson `LaissezFaireSubTypeValidator` → whitelist | MEDIUM (supply-chain) | M06 |
| `isHeadman` rate-limit на gRPC | LOW (key-space DoS) | M06 |
| Redis cache hit/miss metrics (`@Aspect`) | MINOR | M06 |
| `GrpcClientMetricsInterceptor` Timer caching + startNs fix | LOW | M06 |
| `/actuator/**` excluded из tracing | — | M06 (M04 backlog) |
| Mozilla/Apple push endpoint masking | LOW | M07 (frontend hardening) |
| Mongo `$group` aggregation в ReportService | — | M07 (требует denorm `lesson_alive`) |
| Manual smoke-тест 30 concurrent HTTP (pool tuning) | — | M06/prod deploy |
| `CREATE INDEX CONCURRENTLY` policy docs | NIT | M06 |

### Действия, ожидающие `go` пользователя

1. `git push origin main` — 90+ коммитов локально ahead origin.
2. `git push origin --tags` — 5 tags (`v0.0.0-alpha.2..6`) локальные.
3. Старт M06 по новой сессии (см. «Промпт» выше).

### Source of truth для M05 (финальный)

- `docs/milestones/M05-performance/{PLAN,CHECKLIST,NOTES,DECISIONS}.md` —
  per-milestone artefacts (включая Post-mortem в PLAN.md).
- `docs/caching-strategy.md` (NEW-144) — M05 G3.
- `docs/performance-indexes.md` (NEW-141/142) — M05 G1.
- `docs/api-error-conventions.md` (NEW-145) — M05 G4.
- `docs/future-ideas.md` (NEW-146) — M05 G5 deferred.
- `docs/connection-pool-tuning.md` (NEW-147) — M05 G6.
- `docs/data-retention-policy.md` (NEW-148) — M05 G7.
- `docs/architecture.md` §11.1 — блок «Performance & Ops runbooks».

---

## Hand-off после M05 Группы 8 (2026-04-21)

**Состояние M05:** ⏳ **в работе.** 8/10 групп закрыто. Последний
коммит `3fae923`. Остались 9 (аудит) и 10 (docs + close).

**Важно для агентов аудита:** берите **свежий контекст** — предыдущая
сессия глубоко участвовала в реализации всех 8 групп, это создаст
предвзятость. Новая сессия должна прочитать только diff + актуальный
код, без истории разговоров.

### Итоги Группы 7 (commit `1aa5e75`)

**Cleanup push-subs + retention audit (NEW-148, D10).**

- Поле `last_seen: Instant` в `PushSubscriptionDocument` (MongoDB,
  не PostgreSQL — PLAN.md гипотеза «Flyway» была неверна, коллекция
  живёт в notification-web Mongo).
- `idx_last_seen` добавлен в `PushMongoConfig.initIndexes()` —
  programmatic, конвенция проекта для Mongo (аналог M05 G1
  `late_checkin_requests`).
- `WebPushDeliveryService.touchLastSeen(endpoints)` — bulk Mongo
  `$set` одной операцией на fanout (не N save'ов).
- `PushSubscriptionCleanupJob` — `@Scheduled(cron="0 0 3 * * SUN")` +
  `@SchedulerLock("cleanupStalePushSubs")` (shedlock-provider-mongo).
  Retention 90d configurable.
- `PushCleanupConfig` — `@EnableScheduling` + `@EnableSchedulerLock`
  + Mongo LockProvider + bootstrap backfill `last_seen = now` на
  `ApplicationReadyEvent` для pre-M05 подписок.
- Refresh-token TTL audit: ✅ `EX=604800` (7d) подтверждён в 4
  call-site'ах (AuthService.login:88, refresh:125, TmaService:78,
  OtpService:192). Фикс не требуется.
- `docs/data-retention-policy.md` (NEW-148) — 12-rows retention matrix.
- `PushSubscriptionCleanupJobIT` — 3 сценария (dead/fresh/boundary +
  legacy backfill), все зелёные через Testcontainer'ный Mongo.

### Итоги Группы 8 (commit `3fae923`)

**gRPC hot-path: metrics + parallel + ArchUnit deadline guard (NEW-149, D11).**

Scope уточнён после аудита callsite'ов — deadline уже везде (19
callsite'ов с `withDeadlineAfter(3s)`), `CheckinService.checkin`
делает **1** gRPC call (параллелить нечего, PLAN.md гипотеза неверна),
runtime-guard отклонён (сломал 15+ integration-тестов).

- `GrpcClientMetricsInterceptor` в `shared-observability` —
  ClientInterceptor, регистрирует `grpc.client.duration` Timer
  histogram с тегами `service` / `method` / `status` на каждый
  outgoing call. Per-app wrappers через `@GrpcGlobalClientInterceptor`
  в attendance, schedule, academic. **Без** `grpc-micrometer`
  dependency (не хотели supply-chain bloat).
- `GrpcParallelExecutorConfig` (attendance) — `grpcTaskExecutor` bean,
  `ThreadPoolTaskExecutor` core=2/max=8/queue=100, prefix
  `grpc-parallel-`.
- `LessonEventService.processLessonClosed` — параллельный fan-out
  `getLessonById` + `getGroupMembers` через
  `CompletableFuture.supplyAsync + grpcTaskExecutor + unwrap()`
  helper (разворачивает `CompletionException` → `RuntimeException`
  для существующего error handling).
- `MarkingService.markBatch` — N уникальных `getLessonById` + 1
  `getGroupMembers` fan-out параллельно. Добавлено поле
  `@Qualifier("grpcTaskExecutor") TaskExecutor grpcTaskExecutor` с
  `@RequiredArgsConstructor` (Lombok поддерживает qualifier-аннотации
  на final-полях).
- `ReportService.getLessonAttendance` — **не параллелится**,
  `getGroupMembers` требует `lesson.groupId` (dependency chain).
  Rollback imports сохранён в DECISIONS D11.
- ArchUnit `GrpcClientDeadlineTest` × 3 сервиса — byte-code scan
  через `method.getMethodCallsFromSelf()`, fail'ит на любой public
  метод `*GrpcClient` который вызывает `*BlockingStub` без
  `withDeadlineAfter`/`withDeadline` в том же методе. Sanity-verify
  пройден.
- `LessonEventServiceParallelTest` — mock gRPC clients с latency
  200ms каждый, ассертит wall-time < 350ms (parallel) vs > 400ms
  (sequential baseline). Существующие `LessonEventServiceTest` /
  `MarkingServiceTest` переведены на `SyncTaskExecutor` для
  детерминизма (correctness-тесты не должны вводить параллелизм).
- `infra/grafana/provisioning/dashboards/grpc-latency.json` — 5
  панелей: p50/p95/p99 histogram_quantile per `(service, method)`
  + error-rate (non-OK статусы) + active-methods stat.

### M05 Scope остался

| # | Группа | Est | Статус |
|---|--------|-----|--------|
| 1 | Composite indexes + perf baseline | ~3ч | ✅ |
| 2 | Preventive N+1 guard (NEW-143) | ~2ч | ✅ |
| 3 | Redis cache дополнения (D6, NEW-144) | ~4ч | ✅ |
| 4 | Batch endpoints (D7, D8, NEW-145) | ~4ч | ✅ |
| 5 | Single-pass accumulators + SQL pagination (D9, NEW-146) | ~3ч | ✅ |
| 6 | HikariCP tuning (NEW-147) | ~1.5ч | ✅ |
| 7 | Cleanup push-subs + retention audit (NEW-148, D10) | ~3ч | ✅ |
| 8 | gRPC metrics + parallel + deadline ArchUnit (NEW-149, D11) | ~5ч | ✅ |
| **9** | **Audit (bug-hunter + code-reviewer + security)** | **—** | **⬜ next** |
| 10 | Documentation + закрытие milestone | — | ⬜ |

### Группа 9 Scope (план для следующей сессии)

**Важно:** стартуй с **чистого контекста**. НЕ используй историю
предыдущей сессии для объективности аудита.

Чеклист (из CHECKLIST.md):

- [ ] Полный `./gradlew build` — всё зелёное (unit + integration +
      ArchUnit + CI-lint) на main (последний коммит `3fae923`).
- [ ] Спавни `bug-hunter` агент на diff M05 (8 коммитов от `83ed387`
      до `3fae923`). Фокус:
  - Race conditions в `@Cacheable rbac` (stale read после
    `patchUser → programmatic evict`). Double-check порядок
    `@CacheEvict` vs write в Redis.
  - Transactional boundaries для `MarkingService.markBatch`
    (pseudo-atomic, Mongo standalone без transactions).
  - Deadline propagation — что происходит в
    `CompletableFuture.supplyAsync` task'е если task в queue ждёт
    > deadline? Может cascade issue.
  - `PushSubscriptionCleanupJob.backfillOnStart` — idempotency при
    повторном старте после частичного backfill'а (Mongo падает
    между updates).
- [ ] Спавни `security-auditor` на:
  - `POST /attendance/marks/batch` input validation (`@Size(min=1,
    max=100)` на List — защита от memory exhaustion через large
    payload; IDor — headman отмечает чужую группу).
  - `@Cacheable("rbac", key="#userId + ':' + #groupId")` —
    sensitive данные в Redis keys (user IDs — PII?) + key collision
    edge cases (если `userId=1`, `groupId=2:3` — парсинг ломается?
    проверить user-controlled symbols).
  - Grafana dashboard JSON — нет ли утечки PII в labels
    `{service, method, status}`.
  - ShedLock коллекция `shedLock` — нет ли sensitive данных в
    lock-documents.
- [ ] Спавни `code-reviewer` на:
  - `docs/caching-strategy.md` + `docs/performance-indexes.md` +
    `docs/connection-pool-tuning.md` + `docs/data-retention-policy.md`
    — согласованность, ссылки, actionability.
  - `GrpcClientMetricsInterceptor` + `GrpcClientDeadlineTest` (× 3
    дубли — DRY?) — есть ли потенциал вынести ArchUnit rule в
    shared-observability как тест-fixture (одна base test class, 3
    empty extends).
  - Parallel refactor чистота — `unwrap` дублируется в
    `LessonEventService` и `MarkingService`, кандидат на вынос
    в shared helper.
- [ ] Hot-patches → отдельный коммит (не смешивать с Group 9 audit
      summary коммитом).
- [ ] Audit summary — короткая записка в NOTES.md (после commit'а
      с фиксами, если они будут) + отметка в CHECKLIST.

### Группа 10 Scope

- [ ] Финальный `./gradlew build` после audit fix'ов.
- [ ] `CHANGELOG.md [Unreleased]` — добавить секции для Групп 3-8
      (сейчас только 1 и 2 в changelog'е).
- [ ] `docs/architecture.md` — разделы «Caching layer» + «Batch
      operations» + обновлённый HikariCP sizing + gRPC
      observability.
- [ ] `CLAUDE.md` — статус M05 → ✅ + дата.
- [ ] `docs/milestones/README.md` — M05 ✅ + дата.
- [ ] `docs/milestones/M05-performance/PLAN.md` → Post-mortem секция.
- [ ] `git tag v0.0.0-alpha.6` (локально, без push).
- [ ] Hand-off для M06/M07/M08 в `NEXT-SESSION.md`.

### Source of truth для Групп 9-10

- `docs/milestones/M05-performance/{PLAN,CHECKLIST,NOTES,DECISIONS}.md`
  — per-milestone artefacts, актуальны.
- DECISIONS содержит D1-D11 — читай все 11 при аудите (контекст
  отступлений от PLAN.md).
- Git log: 8 perf/feat/docs коммитов M05 (`83ed387` → `3fae923`).
- `git diff 325d25d..HEAD -- services/` — полный diff M05
  (от tag `v0.0.0-alpha.5` до HEAD, только services без .planning).
- OWNER-ANSWERS не ссылается — все отступления уже в DECISIONS D1-D11.

### Последние коммиты

```
3fae923 perf(grpc): metrics + parallel fan-out + deadline ArchUnit (M05 Группа 8, NEW-149)
1aa5e75 perf(push): last_seen retention + weekly cleanup job (M05 Группа 7, NEW-148)
5c955f2 docs(m05): hand-off после Группы 6 — 6/10 групп закрыто
dbdc38c perf(infra): HikariCP tuning + pool-exhaustion alert (M05 Группа 6, NEW-147)
c3bd518 perf: single-pass accumulators + SQL pagination (M05 Группа 5, NEW-146)
e0f82de feat(api): POST /attendance/marks/batch + pwa bulk-mark (M05 Группа 4, NEW-145)
50fc576 feat(cache): rbac + subject namespaces + docs (M05 Группа 3, NEW-144)
9f11396 docs(m05): hand-off после Группы 2 — 2/10 групп закрыто
6802e7f feat(arch): preventive N+1 guard ArchUnit rule (M05 Группа 2, NEW-143)
83ed387 feat(perf): composite indexes + perf baseline (M05 Группа 1)
```

78+ коммитов локально ahead origin. Tags `v0.0.0-alpha.2..5` локальные.
Push отложен до конца v0.0.0.

### Состояние окружения

- **Docker-compose containers:** `rct-postgres-academic`,
  `rct-postgres-schedule`, `rct-mongo-attendance`, `rct-redis`,
  `rct-rabbitmq` — healthy (подняты в предыдущей сессии).
- **Все тесты зелёные:** attendance (180+), schedule (115+),
  academic (210+), notification (22+). ArchUnit × 4 сервиса все
  passing.

### Действия, ожидающие `go` пользователя

1. `git push origin main` — 78+ коммитов не на origin.
2. `git push origin --tags` — 4 tags локальные.
3. Старт Группы 9 по CHECKLIST M05 (в новой сессии).

---

## Hand-off после M05 Группы 6 (2026-04-20)

**Состояние M05:** ⏳ **в работе.** 6/10 групп закрыто. Последний
коммит `dbdc38c`. Остались 7, 8, 9, 10.

### Итоги Группы 3 (commit `50fc576`)

**Redis cache дополнения поверх существующей реализации (D6).**
Аудит показал что academic-service уже имеет `@EnableCaching` + Redis
`CacheManager` + 5 namespaces с ранних фаз — PLAN.md Caffeine scope
неактуален. D6 зафиксировал Redis как v0.0.0 решение.

- Namespaces `rbac` (TTL 1м) + `subject` (TTL 10м) добавлены в
  `CacheConfig`.
- `AcademicReadService.isHeadmanOf(userId, groupId)` —
  `@Cacheable("rbac")`. Переключает `AcademicGrpcServiceImpl.isHeadman`
  с «Not cached per D-02» на кешируемый hot-path.
- `@Cacheable("subject")` на `SubjectService.getSubject` +
  `@CacheEvict` на updateSubject/deleteSubject.
- Programmatic rbac eviction в `UserService.patchUser` /
  `transferStudent` (при смене `is_headman` / `group_id`).
- `docs/caching-strategy.md` (NEW-144) — TTL matrix (7 namespaces),
  invalidation, trade-offs, migration plan.
- `RbacCacheIT` — 4 integration-теста зелёные.

**Deferred:** Redis hit/miss counter'ы через Micrometer. Попытка
`MetricsCacheManagerDecorator` wrapping'а ломала namespace-specific
TTL в RedisCacheManager (reproducible regression). Зафиксировано
в NOTES для возврата через `@Aspect` подход (не blocking'ом для M05).

### Итоги Группы 4 (commit `e0f82de`)

**POST /attendance/marks/batch + PWA bulk-mark (D7, D8).** Закрывает
ядро P2-10/4 — headman отмечает N студентов одним POST-запросом.

- `MarkBatchItem`/`MarkBatchRequest`/`MarkBatchResponse` DTOs в
  `attendance-api-contract`. `@Size(min=1, max=100)` на items list.
- `MarkingService.markBatch` — pseudo-atomic validation-first (D7):
  все authz-check'и до любого Mongo write'а. 1 gRPC `getLesson` на
  уникальный lessonId + 1 gRPC `getGroupMembers` + N upsert
  (вместо N × 3 gRPC + N upsert для N single-mark вызовов). ~10×
  latency reduction на 30-student batch (~6000ms → ~500ms).
- `MarkingController.markBatch` — HATEOAS self-link, 5 unit-тестов
  зелёные (happy path + 4 failure scenarios).
- PWA: `useHeadmanMarkBatch` TanStack mutation, `handleBulkMark`
  переключён с for-loop await на один batch call.
- `docs/api-error-conventions.md` (NEW-145) — error schema RFC 7807,
  pseudo-atomic vs partial-success patterns.

**Deferred (D8):** `POST /academic/homeworks/batch` и web-panel
weekly-journal bulk-read — ROI низкий, не блокирует UX.

### Итоги Группы 5 (commit `c3bd518`)

**Single-pass accumulators + SQL pagination (D9, NEW-146).** Полный
Mongo `$group` pipeline заблокирован `ReportService.filterExistingLessons`
cross-service invariant'ом — правильное решение требует денормализации
`lesson_alive` флага (M06/M07 scope).

- `ReportService`: `getStudentStats` / `buildOverall` / `buildWeekly`
  переписаны на single-pass `for`-loop с int counter'ами вместо 3-4×
  `stream().filter().count()` на одном списке. O(N) вместо O(3-4N).
  `filterExistingLessons` invariant сохранён.
- `LessonService.getLessonsForGroup` — SQL `LIMIT/OFFSET` через Spring
  Data `Pageable` вместо in-memory `.subList(offset, end)`. Устраняет
  OOM-risk на 2000+ lessons/semester. Native query +
  `countQuery` в `LessonRepository.pageByScheduleItemIdInAndDateBetweenAndStatusIn`.
  Использует composite-индекс M05 G1.
- `docs/future-ideas.md` (NEW-146) — 3 варианта Mongo $group
  implementation + audit-checklist для PR-review.

### Итоги Группы 6 (commit `dbdc38c`)

**HikariCP tuning + pool-exhaustion alert (NEW-147).**

- `application.yml` academic/schedule: pool=20, idle=5, timeout=5s,
  idle-timeout=10m, max-lifetime=30m, leak-detection=60s.
  auth-service pool=10 (read-only login). attendance — no-op
  (MongoDB).
- `HikariPoolExhaustion` alert в `infra/prometheus/rules/service-health.yml`
  — `(active/max) > 0.80 for 5m`, warning, routed в Telegram
  через M04 Alertmanager.
- `docs/connection-pool-tuning.md` (NEW-147) — формула, текущие
  значения, триггеры пересмотра, smoke-тест процедура.

### M05 Scope остался

| # | Группа | Est | Статус |
|---|--------|-----|--------|
| 1 | Composite indexes + perf baseline | ~3ч | ✅ |
| 2 | Preventive N+1 guard (NEW-143) | ~2ч | ✅ |
| 3 | Redis cache дополнения (D6, NEW-144) | ~4ч | ✅ |
| 4 | Batch endpoints (D7, D8, NEW-145) | ~4ч | ✅ |
| 5 | Single-pass accumulators + SQL pagination (D9, NEW-146) | ~3ч | ✅ |
| 6 | HikariCP tuning (NEW-147) | ~1.5ч | ✅ |
| **7** | **Cleanup push-subs + retention audit (P2-10/7, NEW-148)** | **~3ч** | **⬜ next** |
| 8 | gRPC hot-path: parallel + deadlines + metrics (P2-10/8, NEW-149) | ~1д | ⬜ |
| 9 | Audit (bug-hunter + code-reviewer + security) | — | ⬜ |
| 10 | Documentation + закрытие milestone | — | ⬜ |

### Группа 7 Scope (предварительно — читай PLAN.md + OWNER-ANSWERS 3890-3940)

P2-10/7 Cleanup push-subs + retention audit:

- **Flyway миграция** на attendance_db (или где живёт
  `push_subscriptions` — уточнить в коде):
  `ALTER TABLE push_subscriptions ADD COLUMN last_seen TIMESTAMPTZ NOT NULL DEFAULT NOW()`.
- **`WebPushDeliveryService`** — update `last_seen = NOW()` на
  successful send. На `410 Gone` — delete (уже было до M05).
- **`@Scheduled(cron="0 0 3 * * SUN") @SchedulerLock(name="cleanupStalePushSubs")`**
  в notification-web: DELETE WHERE last_seen < NOW() - 90 days.
- **Audit auth-service Redis** `refresh:<hash>` key — подтвердить
  `EX=604800` (7d) через grep + optional тест. Если EX отсутствует
  — фикс.
- **`docs/data-retention-policy.md` (NEW-148)** — таблица «что →
  retention → mechanism»: push-subs 90д, refresh-tokens 7д, OTP 5м,
  attendance history accept, outbox 48ч (M02 cleanup).
- **Integration-тест:** seed dead subs (`last_seen=NOW()-100d`) +
  run scheduled → удалены.

### Отложенные пункты (контекст для будущих milestones)

**M06/M07 (cross-service infra):**
- Mongo `$group` pipeline в `ReportService` (NEW-146) —
  требует денормализации `lesson_alive` или materialized view.
- Redis cache hit/miss metrics через `@Aspect` или native Spring
  Boot 3.5+ `RedisCacheMeterBinder`.
- `POST /academic/homeworks/batch` (D8) — admin-импорт homework
  partial-success.
- `GET /attendance/reports/lessons?ids=...` bulk-read (D8) — если
  появится реальная p95 проблема на weekly-journal.

**Прод-deployment:**
- Manual smoke-тест 30 concurrent HTTP на dev (G6) — процедура
  есть в `docs/connection-pool-tuning.md`, ждёт `docker compose up`
  с полной инфраструктурой.

### Состояние окружения

- **Docker-compose containers:** `rct-postgres-academic`,
  `rct-postgres-schedule`, `rct-mongo-attendance`, `rct-redis`,
  `rct-rabbitmq` — **healthy** (запущены в этой сессии).
  Schemas мигрированы Flyway V1..V17 / V12. Seed применён
  (600 schedule_items, 12k lessons, 20 groups, 523 users,
  300 subjects, 1800 TSG, 1800 homeworks, 6000 late_checkin_requests).
- **Mongo admin:** `rct_user:rct_dev_pass` (roles: root@admin),
  connection string в seed-perf.js.

### Последние коммиты

```
dbdc38c perf(infra): HikariCP tuning + pool-exhaustion alert (M05 Группа 6, NEW-147)
c3bd518 perf: single-pass accumulators + SQL pagination (M05 Группа 5, NEW-146)
e0f82de feat(api): POST /attendance/marks/batch + pwa bulk-mark (M05 Группа 4, NEW-145)
50fc576 feat(cache): rbac + subject namespaces + docs (M05 Группа 3, NEW-144)
9f11396 docs(m05): hand-off после Группы 2 — 2/10 групп закрыто
6802e7f feat(arch): preventive N+1 guard ArchUnit rule (M05 Группа 2, NEW-143)
83ed387 feat(perf): composite indexes + perf baseline (M05 Группа 1)
```

76+ коммитов локально ahead origin. Tags `v0.0.0-alpha.2..5` локальные.
Push отложен до конца v0.0.0.

### Действия, ожидающие `go` пользователя

1. `git push origin main` — 76+ коммитов не на origin.
2. `git push origin --tags` — 4 tags локальные.
3. Старт Группы 7 по CHECKLIST M05.

### Source of truth для v0.0.0

- `docs/report-before-v0.0.0/99-executive-summary.md` — roadmap.
- `docs/report-before-v0.0.0/OWNER-ANSWERS.md` (строки **3890-3940**
  для P2-10/7 / Группа 7 Cleanup).
- `docs/report-before-v0.0.0/COVERAGE-AUDIT.md` — 354 пункта.
- `docs/milestones/README.md` — индекс milestones + статусы.
- `docs/milestones/M05-performance/{PLAN,CHECKLIST,NOTES,DECISIONS}.md`
  — per-milestone artefacts.
- `docs/caching-strategy.md` (NEW-144) — M05 G3.
- `docs/api-error-conventions.md` (NEW-145) — M05 G4.
- `docs/future-ideas.md` (NEW-146) — M05 G5 deferred.
- `docs/connection-pool-tuning.md` (NEW-147) — M05 G6.

---

## Hand-off после M05 Группы 2 (2026-04-20) — ИСТОРИЯ

### Итоги Группы 1 (commit `83ed387` + scope `ea7a390`)

**Composite indexes + perf baseline (P2-10/1).** Уточнения scope
зафиксированы в DECISIONS D1-D4.

- **schedule_db V12** — partial composite `idx_lessons_item_date ON
  lessons (schedule_item_id, date) WHERE status != 'cancelled'`. D1 —
  `lessons.group_id` не существует; композит на FK покрывает IN+BETWEEN.
- **academic_db V17** — `idx_tsg_group_semester` +
  `idx_hw_group_semester` на `(group_id, semester_id)`. D2 — таблицы
  `user_groups` нет; индексы на реальных hot queries
  `findByGroupIdAndSemesterId`.
- **attendance Mongo** — compound `lcr_group_status_created (group_id,
  status, created_at)` на `late_checkin_requests` через
  `MongoConfig.initIndexes()`. **Закрывает 04 P2-9:** COLLSCAN →
  IXSCAN, docsExamined 6000 → 120 (50× reduction), SORT ушёл.
- **Деферрено:** D3 (group_id, lesson_id) на Mongo `attendances` —
  нет hot query-потребителя. D4 `schedule_one_off_lessons` UNIQUE
  уже в V4.
- **Regression-guard tests:** `LessonPerformanceIT`,
  `AcademicPerformanceIT`, `LateCheckinPerformanceIT`. Best times:
  8 / 8 / 8 / 10 ms на лимите 50 ms.
- **Runbook:** `docs/performance-indexes.md` — EXPLAIN before/after
  по 4 hot queries, процесс добавления новых индексов.
- **Seed:** `docs/milestones/M05-performance/seed-perf.sql` +
  `seed-perf.js` — idempotent, id ≥ 900000. Применён к dev-БД в
  docker-compose.

### Итоги Группы 2 (commit `6802e7f`)

**Preventive N+1 guard (P2-10/2, NEW-143).** Системный аудит
Repository-слоя (Explore) показал: все JPA entity в schedule +
academic используют FK как Long, нет `@ManyToOne/@OneToMany`. N+1
невозможен by design. Scope переформулирован на preventive-only (D5).

- **ArchUnit `RepositoryNPlusOneGuardTest`** в
  `schedule/arch/` + `academic/arch/`. Две rule'а:
  1. `entitiesMustNotUseJpaRelations` — фиксирует v0.0.0 invariant.
  2. `repositoriesReturningCollectionsMustGuardNPlusOne` —
     активируется при появлении первой relation; требует Pageable /
     @EntityGraph / *Projection / JOIN FETCH.
- **Sanity-verify:** временный `@ManyToOne` в `Lesson` →
  `entitiesMustNotUseJpaRelations` failed с сообщением «Поле
  `Lesson.scheduleItemRelation` помечено JPA relation...» → edit
  откачен, build зелёный.
- **Reference projection:** `LessonDetailsProjection` +
  `LessonRepository.findLessonDetails` — native JOIN `lessons` +
  `schedule_items` в одном SELECT (10 полей), whitelist'ится ArchUnit.
- **Docs:** `architecture.md §11` — runbook «JPA convention: FK как
  Long, без entity relations (NEW-143)» с rationale, образцом
  `collect itemIds → findByIdIn` (`LessonService.massCancelLessons:137-142`),
  action-plan «когда relation всё-таки нужна».
- **Tests:** schedule 111/111, academic 201/201, attendance 158/158 ✅.

### M05 Scope остался

| # | Группа | Est | Статус |
|---|--------|-----|--------|
| 1 | Composite indexes + perf baseline | ~3ч | ✅ |
| 2 | Preventive N+1 guard (NEW-143) | ~2ч | ✅ |
| **3** | **Caffeine cache для справочников + RBAC (P2-10/3)** | **~1д** | **⬜ next** |
| 4 | Batch endpoints (P2-10/4) | ~1д | ⬜ |
| 5 | SQL-aggregate vs stream (P2-10/5) | ~1д | ⬜ |
| 6 | HikariCP tuning (P2-10/6) | ~2ч | ⬜ |
| 7 | Cleanup push-subs + retention (P2-10/7) | ~3ч | ⬜ |
| 8 | gRPC hot-path: parallel + deadlines + metrics (P2-10/8) | ~1д | ⬜ |
| 9 | Audit (bug-hunter + code-reviewer + security) | — | ⬜ |
| 10 | Documentation + закрытие milestone | — | ⬜ |

### Группа 3 Scope (предварительно — читай PLAN.md и OWNER-ANSWERS 3756-3810)

P2-10/3 Caffeine cache:

- Caffeine dep в shared-web (api) или per-сервис `CacheConfig`.
- Namespaces + TTL: `semester` (5м), `subject`/`group` (10м),
  `rbac` (1м).
- `@Cacheable` на:
  - `getActiveSemester()` в academic — часто зовётся.
  - `isHeadmanFor(userId, groupId)` — RBAC hotspot (hundred/min).
  - `getSubject(id)`, `getGroupById(id)` — справочники.
- `@CacheEvict` на write-side: `activateSemester`,
  `update/delete subject/group`, `changeHeadman`.
- `CaffeineCacheMetrics.monitor(meterRegistry, cache, name)` — gauges
  `cache.size`, `cache.gets{result=hit|miss}` в Grafana.
- Integration-тест: counter hits > misses после warm-up.
- `docs/caching-strategy.md` (NEW-144) — TTL matrix + invalidation
  triggers + migration-plan на Redis при multi-instance.

Ожидаемый выигрыш: снижение latency P2-10/8 (hot-path gRPC
`isHeadmanFor` per-request → cached per 60s).

### Состояние окружения

- Docker-compose containers: `rct-postgres-academic`,
  `rct-postgres-schedule`, `rct-mongo-attendance` — **healthy**.
  Schemas мигрированы Flyway V1..V17 / V12. Seed применён
  (600 schedule_items, 12k lessons, 20 groups, 523 users, 300 subjects,
  1800 TSG, 1800 homeworks, 6000 late_checkin_requests).
- Mongo admin user создан через localhost exception:
  `rct_user:rct_dev_pass` (roles: root@admin). В seed-perf.js указан
  connection string.

### Последние коммиты

```
6802e7f feat(arch): preventive N+1 guard ArchUnit rule (M05 Группа 2, NEW-143)
83ed387 feat(perf): composite indexes + perf baseline (M05 Группа 1)
ea7a390 docs(m05): уточнение scope Группы 1 после аудита схемы БД (D1-D4)
1d5a203 docs(m05): scaffold milestone + hand-off после M04
135d226 docs(m04): CHECKLIST отметка v0.0.0-alpha.5 tag (325d25d)
```

72+ коммитов локально ahead origin. Tags `v0.0.0-alpha.2..5` локальные.
Push отложен до конца v0.0.0.

### Действия, ожидающие `go` пользователя

1. `git push origin main` — 72+ коммитов не на origin.
2. `git push origin --tags` — 4 tags локальные.
3. Старт Группы 3 по CHECKLIST M05.

### Source of truth для v0.0.0

- `docs/report-before-v0.0.0/99-executive-summary.md` — roadmap.
- `docs/report-before-v0.0.0/OWNER-ANSWERS.md` (строки 3756-3810 для
  P2-10/3 / Группа 3 Caffeine).
- `docs/report-before-v0.0.0/COVERAGE-AUDIT.md` — 354 пункта.
- `docs/milestones/README.md` — индекс milestones + статусы.
- `docs/milestones/M05-performance/{PLAN,CHECKLIST,NOTES,DECISIONS}.md`
  — per-milestone artefacts.
- `docs/performance-indexes.md` — runbook M05 G1.
- `docs/architecture.md §11` — JPA convention runbook (M05 G2).
