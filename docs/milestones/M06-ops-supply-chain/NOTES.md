# M06 Notes

Живой файл. Отклонения, измерения, surprises, вопросы, технические долги.

---

## 2026-04-21 — старт milestone'а

- M05 закрыт 2026-04-21, tag `v0.0.0-alpha.6`.
- Последний коммит: `e03e74b docs(m05): закрытие milestone`.
- Инфра поднята (rct-postgres-academic/schedule, rct-mongo-attendance, rct-redis, rct-rabbitmq — healthy).
- Scope М06 согласован — 9 групп по образцу M05 G1-G10.

### Уточнения scope vs 99-executive-summary

- **Alertmanager (P2-9/5) уже сделан** — нашёл `prom/alertmanager:v0.27.0` в `docker-compose.prod.yml` (M04 реализация). Из scope M06 убран.
- **Tempo 2.3.1 уже пиннутый** (M04) — не трогаем.
- **C0-9 `.env.prod.example` + C0-10 LE cert-name** — перекатились в **M09 Prod Release Blockers** (Фаза 0 hardening в 99-executive).
- **P2-9/9 JVM resource limits** — отложено в M07 или prod-deploy checklist (требует VPS smoke).
- **P2-9/3 nginx body-size, P1-3 rate-limit nginx, C0-6 CSP self-host** — **M07 Frontend Hardening**.

### M05 defer'ы (Группа 8, 5 пунктов)

Перенесены из M05 post-mortem. Все из audit findings (bug-hunter/security-auditor):

1. Redis Jackson `LaissezFaireSubTypeValidator` → `BasicPolymorphicTypeValidator` whitelist `ru.rutcampustrack.*` — security MEDIUM.
2. `isHeadman` gRPC rate-limit — security LOW (key-space DoS).
3. Redis cache hit/miss metrics через `@Aspect` — MINOR (deferred в M05 из-за namespace-TTL регрессии).
4. `GrpcClientMetricsInterceptor` Timer caching + `startNs` в `start()` — LOW (bug-hunter 5.1+5.3).
5. `/actuator/**` excluded from tracing sampling — M04 backlog.

### Группа 9 audit — security hot-patches

security-auditor нашёл 5 HIGH + 7 MEDIUM. Hot-patches применены:

- **H1 deploy.yml strict guards** — `if:` добавил explicit
  `head_branch == main` + `event == push` + required `commit_sha`
  input для `workflow_dispatch` (fork-PR bypass закрыт).
- **H2 concurrency: production-deploy** — serialize deploys, избегаем
  параллельный `openssl genrsa` race, который invalidated бы active
  JWT sessions.
- **H3 CacheConfig whitelist narrow** — заменил `java.util.*` на
  explicit collection types (`ArrayList`, `HashMap`, `Optional`, и т.д.),
  убрал `java.lang.*` (final types не нужны в whitelist). Block'ирует
  gadget-chains через `ServiceLoader$LazyIterator`, `PriorityQueue`,
  `FutureTask`.

Accepted as trade-off (НЕ fix'им сейчас):

- **H4 headmanBuckets.clear() race** — `clear()` thread-safe,
  race между `size()` и `clear()` lossy (rate-limit state —
  reset quota приемлем). Principal-based userId source —
  **M07 scope** (требует gRPC proto redesign: `isHeadman` должен
  читать userId из `UserContextFilter`-populated principal,
  не из request body; это breaking change gRPC contract).
- **H5 rct-nginx 5-min background reload** — pre-existing pattern
  (Phase 50.1 safety-net, не внесено M06). Отдельный M07/M09
  hardening.

### Pre-existing test failure unrelated к M06

`shared-outbox:EventSchemaRefTest.lessonStarted_validPayload_passesValidation()`
fail'ит т.к. M04 Группа 6 (commit `08fbd1f`) добавил required
`event_version`/`trace_id`/`source` в envelope schema, а test payload'ы
не были обновлены. G9 hot-patch — добавил три поля в обе test fixture'ы
(`valid` + `invalidLessonNumber_9`), fixed.

### Группа 8c — deferred в M07/v0.1

Redis cache hit/miss metrics через `@Aspect` — MINOR finding:

- Напрямую `@Aspect` на `@Cacheable` не даёт информации hit/miss
  (это определяется в Spring's CacheInterceptor внутри advice
  chain).
- `MetricsCacheManagerDecorator` wrapping ломает namespace-TTL
  (M05 G3 regression).
- Правильный fix — `RedisCacheWriter` customization или Spring Boot
  3.4+ `RedisCacheMeterBinder`, требует hook'и и integration-тест
  на namespace-TTL preservation.

**Действие:** deferred без каких-либо изменений кода. TODO в M07 или
v0.1 when observability в prod станет критичной.

### Группа 8e — deferred в M07

`/actuator/**` excluded from tracing sampling — M04 backlog, оказалось
сложнее чем M06-scope:

- Spring Boot 3.4 + OpenTelemetry не имеет out-of-box property
  `management.tracing.paths-to-skip`.
- Варианты:
  - Custom `Sampler` bean (OTel level) с проверкой span attributes
    `url.path` / `http.route`.
  - `ObservationRegistryCustomizer` с `observationPredicate`
    (требует shared-observability dep на `micrometer-observation`).
  - `WebFilter` early-end spans — хрупко, может порвать span-chain
    для downstream calls.
- Правильный fix требует shared-observability модуль changes +
  integration-тесты per-service.
- Текущая comment в `application.yml:108` вводит в заблуждение
  («Health-check запросы исключены из sampling»), но реально не
  исключены — spam в Tempo продолжается.

**Действие:** перенесено в M07 (или отдельную Phase до v0.0.0),
`application.yml` comment поправлен в G8e commit — честно отражает
status quo.

### Группа 1 — сделано

- **Surprise:** `docker-compose.prod.yml` уже содержал полноценные
  healthcheck'и на всех 7 сервисах через `wget -qO-` (M04). Dockerfile-
  директивы **добавлены поверх** — дублирование намеренное (метаданные
  живут с образом, `docker run` без compose тоже работает).
- **Решение D1:** `wget` вместо `curl` — alpine-jre уже имеет busybox
  wget, curl добавил бы ~7MB без compensating value.
- **notification-bot** — `curl` уже установлен в Dockerfile:3 (apt-get),
  health endpoint реальный: `bot/__main__.py:38-54` (HTTP server на
  port 8081 проверяет watchdog + polling tasks).
- **Smoke full-build отменён** — `docker build auth-service` провисел
  40+ минут без cache (gradle + transitive deps), сбросил. Вместо
  этого использовал `docker buildx build --check` для 7 Dockerfile'ов —
  все synтактически валидны (`Check complete, no warnings found`).
- `docs/dockerfile-conventions.md` (NEW-150) содержит полную
  таблицу start-period + endpoint per service.

---
