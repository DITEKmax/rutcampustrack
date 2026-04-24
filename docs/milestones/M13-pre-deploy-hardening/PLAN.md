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

- [ ] **AC-1** `./gradlew build && ./gradlew integrationTest` зелёные (включая 3 fixed flaky + все новые IT). 0 failures, 0 errors.
- [ ] **AC-2** `docker compose up -d` на чистом volume: все 14 containers healthy (из `docker compose ps`) в течение 90 секунд. `healthcheck:` directives активны на 5 backend.
- [ ] **AC-3** Локальный smoke: `curl -H 'Origin: ...' http://localhost/api/auth/login` × 7 → шестой и седьмой получают 429 (rate-limit 5/min работает корректно, не 300/min).
- [ ] **AC-4** Локальный smoke: `curl 'http://localhost/api/academic/users?size=1000000'` → `size` truncated до 100 или 400 Bad Request.
- [ ] **AC-5** Локальный smoke: `docker stop rct-auth-service` → в течение 90 секунд Telegram чат получает alert `ServiceDown: auth-service`.
- [ ] **AC-6** Локальный smoke: PWA/web-panel login через Playwright E2E — login/logout/refresh cycle зелёный в CI.
- [ ] **AC-7** `scripts/validate-env-prod.sh` на правильном `.env.prod` → exit 0; на broken (missing JWT_SECRET / short password) → exit 1 с явным сообщением.
- [ ] **AC-8** `scripts/backup.sh` → созданы 4 `.gz` + 1 `.env.prod.gpg` в `/opt/backups/`. `restore.sh $date` восстанавливает в отдельный Postgres/Mongo, row count matches.
- [ ] **AC-9** `db.notification_history.getIndexes()` показывает TTL 30d + compound `(user_id, created_at)` **после** первого старта notification-web на чистой Mongo.
- [ ] **AC-10** Attendance markBatch через Mongo replica set + `@Transactional` — при принудительном kill публикатора между `save` и `publish` outbox запись и attendance записи остаются consistent.
- [ ] **AC-11** `/api/csp-report` принимает `application/csp-report` JSON → логируется structured + metric `csp_violations_total` incrementится. CSP header `Content-Security-Policy` содержит `report-uri /api/csp-report`.
- [ ] **AC-12** Dry-run VPS deploy runbook'а локально (чистая Ubuntu VM или fresh docker-compose) → все 14 шагов проходят без правок. Runbook обновлён реальными выводами команд.
- [ ] **AC-13** `docs/alerts.md` содержит 15+ alert'ов (совпадает с `infra/prometheus/rules/*.yml`), каждый имеет `## Symptom / ## Meaning / ## Runbook` секции.
- [ ] **AC-14** `/prometheus/` и `/alertmanager/` открываются через nginx с basic-auth (`.htpasswd`), внутренние link'и работают через prefix (`--web.external-url`).
- [ ] **AC-15** `openssl s_client` на `ruttrack.site:443` → cert valid, `docker exec rct-nginx nginx -s reload` через certbot `--deploy-hook` триггерится после renewal (локально симулируется через dummy cert).
- [ ] **AC-16** `grep -r "refresh-body" frontends/` → 0 usage. Endpoint удалён из `auth-service`, 410 Gone не нужен.
- [ ] **AC-17** `SecurityIdorIT` покрывает ≥ 10 endpoint'ов с `{userId}/{groupId}/{lessonId}` в URL. Student A получает 403 на endpoint'ах Student B / Group B / Lesson B.
- [ ] **AC-18** `EventIdempotentIT` — при двойной доставке одного `event_id` consumer обрабатывает только один раз (проверено для academic/schedule/attendance/notification-web + bot).
- [ ] **AC-19** WebSocket smoke: Chrome DevTools offline → 30s → online → STOMP reconnect'ится автоматически. nginx `proxy_read_timeout` ≥ 300s на `/ws` location.
- [ ] **AC-20** `ErrorResponse.fieldErrors` удалён из backend + frontend fallback'и. Все endpoint'ы возвращают `invalidParams` (RFC 9457 standard).
- [ ] **AC-21** Blackbox exporter scrape'ит `https://ruttrack.site` → metric `probe_ssl_earliest_cert_expiry` доступна в Prometheus. Alert rule `SslCertExpiresSoon` при `< 30d`.
- [ ] **AC-22** `ActuatorTracingExcludeSampler` работает — `/actuator/*` спаны не попадают в Tempo (проверено через trace query).
- [ ] **AC-23** code-reviewer agent на финальном diff: 0 BLOCK, 0 HIGH.

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
