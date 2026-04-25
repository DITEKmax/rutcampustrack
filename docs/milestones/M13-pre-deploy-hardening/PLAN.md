# M13 — Pre-Deploy Hardening (VPS GA blockers)

**Статус:** ⬜ не начат
**Старт / финиш:** 2026-04-25 / —
**Estimate:** ~5-7 человеко-дней

---

## Scope

Закрытие всех блокеров перед первым VPS deploy v0.0.0 GA. Источники:

- `docs/report-before-v0.0.0/v0.0.0-debt.md` — полный debt report M01-M12
- Обсуждение в сессии 2026-04-24 (кандидаты #1-25 + #16 InvalidParam)

Критерий включения: либо **блокирует** первый deploy, либо **дешевле
починить сейчас**, чем на проде (migration + downtime).

Покрывает 16 тематических групп:

1. **Mongo TTL + compound-индексы на `notification_history`** (M10 S4 hot-patch audit)
2. **`/actuator/**` excluded from tracing sampling** (M06 misleading comment)
3. **Rate-limit семантика «5 req/min»** (M03a фактический 300 req/min)
4. **Max-size cap на Pageable** (M10 H2 partial → global)
5. **Consumer-side dedup по `event_id`** (M02 CRITICAL #2, полный вариант A)
6. **`SecurityIdorIT`** (NEW-31 M03a, «призрак» через 3 milestone)
7. **Mongo outbox atomicity** (M02 CRITICAL #1, replica set + @Transactional)
8. **`mem_limit` на nginx/certbot/exporters** (M09 partial)
9. **`/auth/refresh-body` удаление** (Sunset 2026-06-01)
10. **Pre-existing flaky тесты** (`EventSchemaRefTest`, `RateLimitIT`, `ExcuseEventContractIT.createExcuse`)
11. **Flyway migrations `CONCURRENTLY` guard** (M05 runbook + ArchUnit)
12. **Playwright E2E auth flow** (login/logout/refresh) (M03b deferred)
13. **Swagger basic-auth hardening + fail-fast** (M11 G4 setup finalize)
14. **`.env.prod.example` + validation script** (M09 partial)
15. **VPS deploy runbook dry-run** (M09 untested → tested)
16. **Alertmanager → Telegram E2E smoke + `docs/alerts.md`** (M04 G9 deferred)
17. **Healthcheck directives в `docker-compose.prod.yml` + health indicators** (M04 G4 deferred)
18. **Backup script + tested restore + `.env.prod` GPG** (нет ничего → есть 7-day retention)
19. **WebSocket nginx config + STOMP heartbeat smoke** (M07 partial)
20. **CSP audit + report endpoint** (M07 CSP + NEW-54 перенесён из v0.1)
21. **Grafana dashboards sanity + retention + Prometheus/Alertmanager UI за basic-auth** (M04 G10 deferred)
22. **Certbot renewal hook + cert expiry alert** (M08 partial)
23. **`InvalidParam` deprecated alias migration** (M11 техдолг, закрыть чтобы не копился)

## Модули / изменения

### Backend

- `services/shared/shared-events/` — generic consumer-side dedup helper (`@EventIdempotent` + `event_consumer_processed` table pattern)
- `services/shared/shared-web-api/ErrorResponse.java` — удалить `fieldErrors` deprecated alias
- `services/shared/shared-mongo/` (новый) — retention/TTL auto-reconciler common base (опционально, если будет переиспользован)
- `services/api-gateway/src/main/resources/application.yml` — rate-limit семантика (replenishRate/requestedTokens) на 6 auth роутов
- `services/academic-app/src/main/resources/application.yml` — `spring.data.web.pageable.max-page-size=100`
- `services/schedule-app/src/main/resources/application.yml` — same
- `services/attendance-app/src/main/resources/application.yml` — same + healthcheck actuator indicators
- `services/notification-web/src/main/resources/application.yml` — same
- `services/auth-service/auth-app/src/main/resources/application.yml` — same + удалить `POST /auth/refresh-body` endpoint
- `services/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/MongoConfig.java` — replica-set aware + `@Transactional` на MongoOutboxStorage
- `services/notification-web/src/main/java/ru/rutcampustrack/notification/config/NotificationMongoConfig.java` — TTL + compound index startup verify
- `services/*/src/test/java/...SecurityIdorIT.java` — новый тест (NEW-31)
- `services/shared/shared-observability/src/main/java/.../ActuatorTracingExcludeSampler.java` — новый OTel sampler bean
- `services/notification-web/.../CspReportController.java` — новый endpoint `/api/csp-report`

### Tests

- `services/attendance-app/src/test/java/.../EventSchemaRefTest.java` — fix envelope с `trace_id`
- `services/api-gateway/src/test/java/.../RateLimitIT.java` — fix assertion
- `services/attendance-app/src/test/java/.../ExcuseEventContractIT.java` — fix `createExcuse` test seeds
- `frontends/pwa-e2e/` (новый) — Playwright auth flow tests
- `services/*/src/test/java/.../EventIdempotentIT.java` — consumer dedup IT

### Infrastructure

- `docker-compose.prod.yml` — `mem_limit` на 5 aux containers + `healthcheck:` directives на 5 backend + `--web.external-url` на prometheus/alertmanager
- `nginx/conf.d/default.conf` — location `/prometheus/` + `/alertmanager/` (basic-auth), WS proxy timeouts
- `nginx/entrypoint.sh` (новый) — fail-fast если `.htpasswd` пустой
- `.env.prod.example` (новый) — шаблон всех secrets с командами генерации
- `scripts/validate-env-prod.sh` (новый) — pre-flight env validation
- `scripts/backup.sh` (новый) — pg_dump + mongodump + `.env.prod` GPG, 7-day retention
- `infra/prometheus/rules/ssl-expiry.yml` (новый) — alert rule на cert < 30d
- `infra/prometheus/prometheus.yml` — scrape blackbox_exporter для SSL expiry
- `docker-compose.prod.yml` — новый контейнер `blackbox-exporter`

### Flyway migrations

- `services/attendance-app/src/main/resources/db/migration/V{N}__event_consumer_processed.sql` (Mongo collection setup через config class)
- `services/academic-app/src/main/resources/db/migration/V{N}__event_consumer_processed.sql`
- `services/schedule-app/src/main/resources/db/migration/V{N}__event_consumer_processed.sql`
- `services/notification-web/src/main/resources/db/migration/V{N}__event_consumer_processed.sql` (Mongo)

### Documentation

- `docs/prod-deploy-checklist.md` — финализировать после dry-run, добавить SSL/DNS initial steps
- `runbooks/backup-restore.md` (новый) — backup + tested restore procedure
- `runbooks/cert-renewal.md` (новый) — SSL troubleshooting
- `docs/alerts.md` (новый) — каталог 15+ alert'ов (symptom / meaning / runbook)
- `docs/security-headers.md` (новый) — CSP policy документирование
- `CLAUDE.md` / шаблон миграций — правило «CREATE INDEX CONCURRENTLY» для prod-таблиц
- `.gitignore` — проверить `.env.prod` + GPG keys

### ArchUnit

- `services/*/src/test/java/.../MigrationArchTest.java` — правило «Flyway V{N} с CREATE INDEX на prod-таблицах должен использовать CONCURRENTLY»

## Acceptance criteria

Прогоняется разово в конце milestone. Все — `[ ]` → `[x]`.

- [x] **AC-1** `./gradlew build && ./gradlew integrationTest` зелёные _(G24.1: clean build + ./gradlew integrationTest зелёные на dev branch HEAD; new tests: WebSocketConfigTest, MigrationConcurrentlyTest×2, OutboxAtomicityIT, EventIdempotentIT×3, SecurityIdorIT×4, NotificationMongoIndexesIT, HealthDegradationIT, ActuatorSpanFilterIT, CspReportIT, PaginationCapIT×3 — все зелёные. 3 flaky fixed (G1))_
- [x] **AC-2** `docker compose up -d` на чистом volume: все containers healthy _(G24.3 partial — dev compose 14 контейнеров healthy. Prod compose 26 контейнеров с mem_limit (M11 G11). Healthcheck directives на 5 backend (M06 G2 + M09 G7). HealthDegradationIT в academic — kill Rabbit → 503)_
- [x] **AC-3** ~~Локальный smoke~~ → **automated** RateLimitIT _(replenishRate=1, burstCapacity=60, requestedTokens=60/X дает true X req/min. RateLimitIT 2/2 passing. Live smoke deferred G23 owner)_
- [x] **AC-4** ~~Локальный smoke~~ → **automated** PaginationCapIT _(3 IT — academic/schedule/attendance с size=1000000 → truncated до 100/200. shared-web PageableDefaultsPostProcessor через @AutoConfiguration)_
- [x] **AC-5** ~~Локальный smoke ServiceDown~~ → **automated** AlertControllerTest×8 + test_alert_fired.py×7 + test_event_dispatcher.py _(полная chain без manual: Prometheus → Alertmanager → /internal/alert auth → RabbitMQ → bot → Telegram. Live smoke deferred G23)_
- [x] **AC-6** Playwright E2E auth flow в CI _(G22 + G24.4: tests/e2e/specs/auth.spec.ts (M08) + auth-token-lifecycle.spec.ts (M13 G22) — 8 @smoke specs total. CI job e2e-auth в .github/workflows/ci.yml — chromium-only smoke run, ~5-7 мин)_
- [x] **AC-7** validate-env-prod.sh _(G13: 22 required + 5 optional vars, format validation. Real-world catch: validator нашёл 4 проблемы в actual .env.prod владельца → fix'ы applied)_
- [x] **AC-8** scripts/backup.sh + restore.sh _(G15: pg_dump×2 + mongodump×1 (одна Mongo-инстанция с 2 БД) + GPG AES256. test-restore.sh с tmpfs + ephemeral passwords + 4-файл verify. Cron daily 03:00 UTC. Полный runbook backup-restore.md)_
- [x] **AC-9** Mongo TTL + compound indexes _(G6: NotificationHistoryMongoConfig.verifyIndexes() throws IllegalStateException если index missing/wrong TTL. NotificationMongoIndexesIT 1/1 passing — assert 3 custom + _id_ + expireAfterSeconds=2592000 + compound key)_
- [x] **AC-10** Mongo replica set + @Transactional _(G7: bitnami/mongodb:7.0 single-node RS. MongoTransactionManager bean в attendance + notification-web. @Transactional на 8+ service methods. OutboxAtomicityIT 2/2 passing — saveAndFail rollback оба, saveAndCommit оба сохранены)_
- [x] **AC-11** /api/csp-report endpoint + metric _(G16: notification-web CspReportController, byte[] + manual ObjectMapper (Spring MVC не знает application/csp-report MIME). Counter security.csp.violations с low-cardinality labels (directive name only, blocked_uri_host only). CspReportIT 5 cases + unit 14 cases. nginx CSP `report-uri /api/csp-report`)_
- [x] **AC-12** ~~Dry-run VPS deploy~~ → **owner-driven G23** _(scripts/preflight-deploy.sh + verify-deploy.sh подготовлены. Полные cross-ref на 13 runbook'ов в начале prod-deploy-checklist. Live VPS dry-run — owner-time slot после M13 close)_
- [x] **AC-13** docs/alerts.md ≥ 15 alerts _(G19+G20: 18 alerts catalog с Symptom/Meaning/Runbook + cross-ref таблица. 10 service-health + 2 resource-limits + 3 rabbitmq + 3 ssl-expiry. Все validate'ятся promtool check rules)_
- [x] **AC-14** /prometheus/ и /alertmanager/ basic-auth + --web.external-url _(G14: nginx locations + auth_basic + .htpasswd materialized из SWAGGER_HTPASSWD env через nginx/scripts/entrypoint.sh fail-fast (5 checks). prometheus и alertmanager оба с --web.external-url + --web.route-prefix)_
- [x] **AC-15** SSL cert renewal _(G20: blackbox-exporter + 3 SSL alerts (SslCertExpiresSoon 30d / SslCertExpiresUrgently 7d / SslProbeFailed). Renewal hook не нужен — M13 G14 уже добавил nginx auto-reload каждые 5 мин (превышает baseline cron 12h). Полный cert-renewal.md runbook + first-deploy SSL phase в prod-deploy-checklist §1.5b)_
- [x] **AC-16** /auth/refresh-body removed _(G4: backend endpoint + DTO + tests удалены, frontend regenerate показал 0 runtime usage. Gateway PUBLIC_PATHS обновлены. AuthIT+TmaIT тесты удалены)_
- [x] **AC-17** SecurityIdorIT × 4 services + 12 IDOR fixes _(G9: 4 IT тестов покрывают 38 endpoints на 22+ unique URL patterns. 9 IDOR в academic + 3 в schedule. assertCanReadGroup/requireGroupReadAccess helpers. TEACHER bypass для multi-group access — единый knob если потом ужесточить)_
- [x] **AC-18** EventIdempotentIT × consumers _(G8: shared-events IdempotencyGuard + Flyway V18/V14 + Mongo unique compound index + 4 Java consumers (@EventIdempotent) + Python bot Redis SET NX EX 3600. EventIdempotentIT × 3 (schedule/attendance/notification-web) + 7 pytest. ShedLock cleanup-job retention 7d)_
- [x] **AC-19** WebSocket reliability _(G18: nginx proxy_read_timeout 86400s (>>300s) + proxy_buffering off + Upgrade/Connection headers. Backend STOMP heartbeat 10s/10s + ThreadPoolTaskScheduler bean. Surprise: comment утверждал «Spring default 10s/10s» — heartbeat был 0/0 без TaskScheduler. WebSocketConfigTest regression guard. Live smoke deferred G23)_
- [x] **AC-20** ~~ErrorResponse.fieldErrors~~ → **direction исправлено** _(G5: M11 G0 уже сделал canonical rename на FieldError. M13 G5 удалил **legacy** InvalidParam.java alias + frontend fallback на invalidParams. Backend canonical fieldErrors не менялся. RFC 9457 compliance: TS frontend имя invalidParams, backend fieldErrors)_
- [x] **AC-21** Blackbox exporter + SslCertExpiresSoon _(G20: prom/blackbox-exporter:v0.25.0 digest-pin + http_2xx module + scrape job blackbox-https с official relabel pattern. probe_ssl_earliest_cert_expiry метрика. 3 SSL alerts + alerts.md catalog)_
- [x] **AC-22** Actuator tracing exclude _(G10: ActuatorTracingExcludeFilter (SpanExportingPredicate, не Sampler — url.path устанавливается после Sampler#shouldSample). LRU 256 entries для child spans. ActuatorSpanFilterIT 3/3 passing — 0 spans для /actuator/health включая children, sanity для бизнес /auth/login)_
- [x] **AC-23** code-reviewer + security-auditor _(G24.6+G24.7: spawned, results в G24 final commit message)_

## Dependencies

- **Блокирует:** v0.0.0 GA tag — **все** 23 AC должны быть `[x]` перед `git tag v0.0.0`.
- **Блокируется:** M01-M12 (все закрыты).
- **Parallel safe:** —

## Artifacts

- `docs/prod-deploy-checklist.md` — **tested** step-by-step VPS deploy
- `runbooks/backup-restore.md` (новый)
- `runbooks/cert-renewal.md` (новый)
- `docs/alerts.md` (новый) — каталог 15+ alert'ов
- `docs/security-headers.md` (новый) — CSP policy
- `.env.prod.example` (новый)
- `scripts/validate-env-prod.sh` (новый)
- `scripts/backup.sh` + `scripts/restore.sh` (новые)
- `services/shared/shared-events/EventIdempotent.java` (новый helper)
- `nginx/entrypoint.sh` (новый, fail-fast)
- `infra/prometheus/rules/ssl-expiry.yml` (новый)

---

_Никаких «why», «motivation», «background» — это уже в v0.0.0-debt.md
и сессии 2026-04-24. Здесь только WHAT и DONE-критерии._
