# M16 — Post-Deploy Hardening

**Статус:** ⏳ в работе
**Старт / финиш:** 2026-04-27 / —
**Estimate:** ~7-9 человеко-дней (9 групп)

---

## Scope

Закрытие критичного и серьёзного тех.долга, проявившегося после first
VPS deploy (M15). Скоп зафиксирован совместно с владельцем — пять
блокеров (🔴) + четыре улучшения готовности к scale (🟡). Magic-link и
мелочь (🟢) — отложены, остаются в `docs/archive/future-ideas.md`.

Источники:

- `docs/archive/future-ideas.md` § «OTP hardening bundle (v0.1)» — HIGH.1, HIGH.2
- `docs/archive/future-ideas.md` § «M16 Cleanup Backlog» — OTel, Loki, nginx DNS
- `docs/archive/future-ideas.md` § «Pre-v0.1 (post-M14)» — MED-08, MED-11, MED-12
- `docs/archive/future-ideas.md` § «G27 tech-debt» F05 — headman rate-limit
- `docs/milestones/M15-first-vps-deploy/NOTES.md` — lessons learned

Покрывает 9 групп в порядке impact:

1. **G1 — OTel exporter wrong port** (4317 → 4318) — distributed tracing сломан в проде, шумит ERROR в логи
2. **G2 — Event dispatcher idempotency** — дубли `lesson.cancelled`/`lesson.started`/прочих в боте при retry публикации
3. **G3 — OTP brute-force counter** — `verifyOtpByCode` без счётчика попыток (HIGH SA-H1)
4. **G4 — Loki `InstancesCount <= 0`** — спорадическая потеря логов, диагностика и фикс
5. **G5 — nginx DNS race** — 502 после каждого compose restart upstream'а
6. **G6 — `@AdminAction` audit log** — реальный handler вместо `log.debug` (MED-08)
7. **G7 — headman rate-limit Redis (300 req/min)** — JVM heap → Redis, лимит повышен (старосты быстро bulk-mark'ают)
8. **G8 — mTLS Alertmanager → notification-web** — Bearer over plaintext HTTP (MED-11)
9. **G9 — cadvisor de-privileged** — `privileged: true` → `cap_add: [SYS_PTRACE]` (MED-12)

## Модули / изменения

### G1 — OTel port fix

- `services/auth-service/auth-app/src/main/resources/application.yml` — endpoint `4317` → `4318`
- `services/academic-service/academic-app/src/main/resources/application.yml` — то же
- `services/schedule-service/schedule-app/src/main/resources/application.yml` — то же
- `services/attendance-service/attendance-app/src/main/resources/application.yml` — то же
- `services/notification-service/notification-app/src/main/resources/application.yml` — то же
- `services/api-gateway/src/main/resources/application.yml` — то же
- `docker-compose.yml` — `OTEL_EXPORTER_OTLP_ENDPOINT` env var правится в N местах
- `docker-compose.prod.yml` — то же

### G2 — Event dispatcher idempotency

- `services/notification-bot/bot/consumers/event_dispatcher.py` — `dispatch()` обогащается Redis `SET NX PX` на `event_processed:<event_id>` TTL 24h
- `services/notification-bot/bot/observability.py` — метрика `event_duplicate_total{event_type}`
- `services/notification-bot/tests/test_event_dispatcher.py` — regression-тест на duplicate-delivery

### G3 — OTP brute-force counter

- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java` — `verifyOtpByCode`: counter `otp_verify_by_code_miss:<ip>` Redis, лимит 20/5min → `429`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java` — pass HttpServletRequest для resolve IP
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/ObservabilityConfig.java` — counter `otp_verify_by_code_counter{status=mismatch|success|throttled}`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/service/OtpServiceTest.java` — unit на throttle
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OtpIT.java` — IT на 20-я попытка → 429
- `infra/prometheus/alerts.yml` — `OtpBruteForceSuspect` rule

### G4 — Loki diagnose

- `infra/loki/loki-config.yml` — проверка `ingester.lifecycler.ring.replication_factor`, `kvstore.store`
- (возможно) косвенно решается G1 — Tempo трейсы перестают идти в Loki по ошибке порта
- Решение фиксируется в `DECISIONS.md` после диагностики

### G5 — nginx DNS race

- `nginx/conf.d/default.conf` — `resolver 127.0.0.11 valid=10s ipv6=off;` на верх каждого `server { }` блока + замена `proxy_pass http://rct-X:N;` на переменную `set $upstream "rct-X:N"; proxy_pass http://$upstream;` для всех internal upstream'ов
- `scripts/verify-deploy.sh` — добавить retry loop для `/login` 502 (eventually consistent после nginx reload)

### G6 — @AdminAction audit log

- `services/academic-service/academic-app/src/main/resources/db/migration/V{N}__audit_log.sql` — таблица (PG)
- `services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/audit/AdminActionAspect.java` — реальный handler (`log.debug` → запись в БД через инжектируемый `AuditLogStorage` SPI)
- `services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/audit/AuditLogStorage.java` — новый interface (SPI, чтобы shared-модуль не знал про academic_db)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/audit/JdbcAuditLogStorage.java` — реализация под academic_db
- `services/academic-service/academic-app/src/main/java/...` — разметка `@AdminAction("user.archive")` и аналогичных на ~15 ADMIN-методах (UserController, GroupController, SemesterController, SubjectController, ThresholdController)

### G7 — headman rate-limit Redis (300 req/min)

- `services/academic-service/academic-app/src/main/java/.../HeadmanRateLimitFilter.java` (или аналогичный текущий компонент) — `ConcurrentHashMap` → Redis `INCR rl:headman:{userId}:{minute}` + `EXPIRE 65`
- Лимит **120 → 300 req/min** (старосты быстро ставят отметки группе)
- `services/academic-service/academic-app/src/test/.../HeadmanRateLimitIT.java` — IT через Testcontainers Redis

### G8 — mTLS Alertmanager → notification-web

- Решение пути в `DECISIONS.md` (Linkerd / nginx mTLS-proxy / cap_drop NET_RAW only)
- `infra/alertmanager/alertmanager.yml` — клиентский cert config (если nginx-путь)
- `nginx/conf.d/internal.conf` (новый) — internal mTLS proxy listening на 9094 → notification-web (если nginx-путь)
- `docker-compose.prod.yml` — cap_drop NET_RAW для cadvisor + node-exporter (defense in depth, делается в любом случае)

### G9 — cadvisor de-privileged

- `docker-compose.prod.yml` — cadvisor: `privileged: true` → `cap_add: [SYS_PTRACE]`
- (по результату аудита метрик) — убрать или оставить mount `/var/lib/docker:ro`

## Acceptance criteria

- [ ] **G1 verify:** в логах backend ни одного `Connection reset` от OTel exporter за 1 час; в Grafana Tempo появляется свежий trace с `trace_id` из MDC текущего request'а
- [ ] **G2 verify:** при искусственно вызванном повторе публикации (mock `markSent` падение) бот обрабатывает событие 1 раз; метрика `event_duplicate_total` инкрементится; regression-тест зелёный
- [ ] **G3 verify:** 21-я подряд `verifyOtpByCode` с одного IP за 5 мин возвращает `429`; алерт `OtpBruteForceSuspect` срабатывает в Prometheus testground; счётчик `otp_verify_by_code_counter{status=throttled}` > 0
- [ ] **G4 verify:** за 24 часа после фикса в логах Loki 0 (или < 5) записей `InstancesCount <= 0`; объяснение причины зафиксировано в `DECISIONS.md`
- [ ] **G5 verify:** после `docker compose restart grafana` (или другого upstream) `/grafana/` отдаёт 200/302 в течение 15 секунд без ручного `nginx restart`; то же для всех upstream'ов из `default.conf`
- [ ] **G6 verify:** запись в `audit_log` появляется при каждом ADMIN-action (smoke: archive user → row с `user_id`, `action='user.archive'`, `before/after diff`, `correlation_id`); 15 ADMIN-методов размечены `@AdminAction`
- [ ] **G7 verify:** староста делает 300 запросов за минуту → 200; 301-й → 429; счётчик в Redis виден через `redis-cli GET rl:headman:<id>:<minute>`; IT зелёный
- [ ] **G8 verify:** Alertmanager → notification-web использует TLS (curl `--cacert` works, без `--cacert` падает); `cap_drop: NET_RAW` применён к cadvisor + node-exporter
- [ ] **G9 verify:** cadvisor работает без `privileged: true`; все метрики `container_*` присутствуют в Prometheus (sample query в `NOTES.md`)
- [ ] **Финал:** smoke по `scripts/verify-deploy.sh` на VPS — все 26+ контейнеров healthy после redeploy; `CHANGELOG.md` обновлён; tag `v0.1.0-rc.1` (или `v0.0.0-alpha.17` если решим оставаться в alpha)

## Dependencies

- **Блокирует:** v0.1.0 GA — без G1/G2/G3 проектируемая reliability profile не достигнута
- **Блокируется:** ничем — все группы ортогональны кроме потенциальной связи G1 → G4 (диагностика Loki может быть упрощена после OTel фикса)
- **Parallel safe:** G1+G5 (infra-only, не трогают backend код), G3+G7 (auth-service vs academic-service), G6+G8 (shared-web aspect vs alertmanager)

## Artifacts

Что остаётся после milestone в репо:

- `docs/operations/runbooks/audit-log.md` — новый runbook на чтение `audit_log` при инциденте
- `docs/operations/runbooks/loki-troubleshooting.md` — обновляется по результату G4
- `docs/operations/monitoring/alerts.md` — новые правила `OtpBruteForceSuspect`, `EventDuplicateRateHigh`
- `docs/architecture/event-schemas.md` — раздел про idempotency-guarantee dispatcher'а
- `docs/security/SECURITY-AUDIT.md` — обновление по закрытым HIGH SA-H1, SA-H2

## Out of scope (явно)

Эти пункты обсуждались при планировании M16 и **сознательно отложены**:

- **Magic-link** для первого входа (~3-4 дня) — owner accept'нул plaintext `initial_password` в M1; делать когда появится compliance-driver или scale.
- **🟢 мелочь** — Landing `var → const`, jqwik, gRPC in-process, sparklines, Tech-debt P2/P3 — остаётся в `future-ideas.md`.
- **Suppressions cleanup** из M15 (CVE bumps valibot/protobuf, DS-0002 nginx-unprivileged) — отложено в `future-ideas.md` § «M16 Cleanup Backlog» (при первом удобном касании frontend).

---

_Никаких «why», «motivation», «background» — это в `future-ideas.md` и
M15 `NOTES.md`. Здесь только WHAT и DONE-критерии._
