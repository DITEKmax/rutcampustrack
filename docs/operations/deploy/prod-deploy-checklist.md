# Production Deploy Checklist

> Оглавление: что делаем и смотрим ПЕРЕД, ВО ВРЕМЯ и ПОСЛЕ каждого
> prod-релиза. Цель — поймать типовые проблемы до того, как их заметит
> староста в группе.

**Scope:** VPS `/opt/rutcampustrack` (см.
[reference_vps_deploy](../README.md)), docker-compose.prod.yml,
`rct-*` containers, network `rutcampustrack_private_net`.

Связанные runbook'и:
- [secret-rotation.md](runbooks/secret-rotation.md) — quarterly ротация
  секретов (NEW-155).
- [bot-webhook-migration.md](runbooks/bot-webhook-migration.md) —
  Alertmanager → bot `/internal/alert` (NEW-154).
- [image-signing-verification.md](runbooks/image-signing-verification.md) —
  cosign verify на pull.
- [migration-testing.md](runbooks/migration-testing.md) — Flyway dry-run
  через MigrationIT.
- [resource-limits.md](resource-limits.md) — 4GB VPS memory budget
  (NEW-157).
- [backup-restore.md](runbooks/backup-restore.md) — backup + tested
  restore + GPG (M13 G15).
- [cert-renewal.md](runbooks/cert-renewal.md) — TLS auto-renewal +
  troubleshooting + first-deploy SSL (M13 G20).
- [mongo-indexes-verify.md](runbooks/mongo-indexes-verify.md) —
  notification_history TTL + compound indexes (M13 G6).
- [swagger-prod-access.md](runbooks/swagger-prod-access.md) — basic-auth
  для /swagger-ui + /prometheus + /alertmanager (M11 G4 + M13 G14).
- [alerts.md](alerts.md) — каталог 18 alert'ов с Symptom/Meaning/Runbook
  (M04 → M13 G19/G20).
- [security-headers.md](security-headers.md) — CSP / HSTS / report-uri
  (M07 + M13 G16).
- [websocket-flow.md](websocket-flow.md) — STOMP heartbeat + reconnect
  (M03b + M13 G18).
- [api-rate-limits.md](api-rate-limits.md) — rate-limit semantics
  (M03a + M13 G2).

---

## 1. Pre-deploy (T-30min)

### 1.0. Environment secrets (M13 G13) — first deploy / rotation

**При первом deploy** на свежий VPS:

- [ ] `cp .env.prod.example .env.prod` — создать локальный prod env файл.
- [ ] Сгенерировать все 22 required secrets — список и команды в самом
      `.env.prod.example` (в комментариях каждой переменной).
- [ ] `chmod 600 .env.prod` — restrict access (только owner read/write).
- [ ] `./scripts/validate-env-prod.sh .env.prod` — exit 0 (все 22 vars
      присутствуют, форматы корректны). Любой `✗ FAIL` — блокер deploy.
- [ ] `scp .env.prod root@<vps>:/opt/rutcampustrack/.env.prod` —
      доставить на VPS. **НЕ** через git (gitignored).
- [ ] На VPS повторить `./scripts/validate-env-prod.sh /opt/rutcampustrack/.env.prod`
      — sanity-check после copy.
- [ ] `./scripts/validate-grafana-dashboards.sh` (M13 G17) — exit 0
      (все 6 dashboards валидны: business-kpis, docker-monitoring,
      grpc-latency, logs-overview, node-exporter, springboot-apm).
      Любой `✗ FAIL` (broken JSON / missing uid / 0 panels) — блокер.
- [ ] Backup `.env.prod` в password manager (Bitwarden/1Password) —
      single source of truth.
- [ ] **GPG passphrase** для backup-шифрования `.env.prod` (M13 G15) —
      сгенерировать `openssl rand -base64 32`, сохранить в password
      manager, записать в `/opt/rutcampustrack/.backup-passphrase` (chmod 600).
      Подробнее — [backup-restore.md §First-time setup](runbooks/backup-restore.md).

**При rotation** (раз в 6 мес или при подозрении на leak):

- [ ] См. [secret-rotation.md](runbooks/secret-rotation.md) — порядок
      ротации с минимальным downtime (rolling per-service).
- [ ] После rotation — `validate-env-prod.sh` снова на старом и новом VPS env.

**Обязательные новые secrets (при upgrade со старого .env.prod):**
- `MONGODB_REPLICA_SET_KEY` (M13 G7) — без него `mongo-attendance` не стартует.
- `INTERNAL_ISSUER_SECRET` (M03a) — без него gateway → auth token-exchange падает.
- `ALERT_WEBHOOK_SECRET` (M04 G9) — без него Alertmanager → notification-web webhook 401.

### 1.1. Git + CI state
- [ ] Ветка `main` — `git fetch && git status` чистый.
- [ ] Последний коммит в `main` имеет зелёный CI (все workflows:
      `ci.yml`, `coverage.yml`, `security.yml`, `openapi-drift.yml`).
- [ ] Тег релиза создан: `git tag -a v0.x.y -m "..."` (формат `v<major>.<minor>.<patch>`).
- [ ] `CHANGELOG.md` обновлён — новый блок под тегом, `[Unreleased]` перемещено.

### 1.2. Flyway dry-run
- [ ] Локально или на staging: `./gradlew :services:academic-service:academic-app:test --tests MigrationIT`
      (и аналогично для schedule). Все миграции проходят c clean БД +
      с prod-snapshot'ом (если есть).
- [ ] Нет новых `@Disabled` миграций.
- [ ] Если миграция ALTER TABLE с long-running DDL (например, CREATE
      INDEX на большой таблице) — отметить в release note + прикинуть
      downtime.

### 1.3. Event schemas
- [ ] `./gradlew eventSchemaCoverageCheck` — все publisher'ы имеют
      соответствующие JSON schemas.
- [ ] Новые события в `event-schemas/` — consumer'ы обновлены и
      deployed в том же релизе.

### 1.4. Image signing + digest pins
- [ ] `deploy.yml` step `sbom-sign` прошёл (cosign keyless). См.
      [image-signing-verification.md](runbooks/image-signing-verification.md).
- [ ] `docker-compose.prod.yml` digest-pins не менялись без Renovate PR
      (проверить `git diff HEAD~ -- docker-compose.prod.yml | grep sha256`).
- [ ] Trivy security scan зелёный (или accepted exceptions документированы).

### 1.5. Backup (снэпшоты прод-данных) — M13 G15

**Автоматизировано через `/etc/cron.d/rutcampustrack-backup` (03:00 UTC daily).**
Полный setup + DR процедура — [backup-restore.md](runbooks/backup-restore.md).

**Первый deploy:**
- [ ] GPG passphrase сгенерирован (`openssl rand -base64 32`) и сохранён
      в password manager (Bitwarden/1Password) как «RutCampusTrack — backup GPG passphrase».
- [ ] `/opt/rutcampustrack/.backup-passphrase` создан на VPS, `chmod 600`, owner `root`.
- [ ] `gnupg` установлен: `sudo apt-get install -y gnupg`.
- [ ] Cron: `sudo cp infra/cron/rutcampustrack-backup /etc/cron.d/ && sudo systemctl restart cron`.
- [ ] Logrotate: `/etc/logrotate.d/rutcampustrack-backup` настроен (см. runbook).
- [ ] `/opt/backups` создан, `chmod 700`, owner `root`.
- [ ] Smoke-test: `sudo scripts/backup.sh` → все 4 файла в `/opt/backups/$(date -u +%Y-%m-%d)/`.
- [ ] VAPID-ключи на VPS в `/opt/rutcampustrack/.env.prod` —
      скопированы в менеджер секретов (1Password / Bitwarden).

**Перед каждым deploy (pre-flight backup):**
- [ ] `sudo /opt/rutcampustrack/scripts/backup.sh` — manual backup перед
      migration, на случай rollback (см. §4.2).
- [ ] Verify: `ls /opt/backups/$(date -u +%Y-%m-%d)/` → 4 файла.

### 1.5b. SSL / DNS (только first deploy на чистый VPS) — M13 G20

Только при **первом** deploy. Для subsequent deploys cert уже выпущен,
auto-renew работает (см. `runbooks/cert-renewal.md`).

- [ ] DNS A-record `ruttrack.site` → `<VPS_PUBLIC_IP>` создан.
      Verify: `dig +short ruttrack.site` возвращает IP.
- [ ] HTTP-only phase: закомментировать `server { listen 443 ssl; ... }`
      block в `nginx/conf.d/default.conf` временно. Оставить только
      :80 server с `/.well-known/acme-challenge/` location.
      80→443 redirect отключить (пока нет cert, redirect ломает ACME).
- [ ] `docker compose -f docker-compose.prod.yml up -d nginx`.
- [ ] Smoke ACME endpoint: `curl http://ruttrack.site/.well-known/acme-challenge/test`
      → 404 (не connection refused). Если refused — firewall/DNS issue.
- [ ] Получить cert через certbot (one-shot):
      ```bash
      docker compose -f docker-compose.prod.yml run --rm \
        -v /opt/rutcampustrack/certbot-conf:/etc/letsencrypt \
        -v /opt/rutcampustrack/certbot-www:/var/www/certbot \
        certbot/certbot:latest certonly --webroot -w /var/www/certbot \
        -d ruttrack.site \
        --email <admin@example.com> --agree-tos --no-eff-email
      ```
- [ ] `git checkout nginx/conf.d/default.conf` — restore полный config.
- [ ] `docker compose -f docker-compose.prod.yml up -d` (full stack).
- [ ] Smoke HTTPS: `curl -I https://ruttrack.site/` → 200/301.
      `openssl s_client -connect ruttrack.site:443 -servername ruttrack.site
      < /dev/null 2>&1 | grep "Verify return code"` → `0 (ok)`.
- [ ] Verify auto-renew loop: `docker logs rct-certbot --tail 5` →
      `Cert not yet due for renewal` либо empty (success). Полный
      runbook: `docs/operations/runbooks/cert-renewal.md`.

### 1.6. Communication
- [ ] Release window уточнён — не пересекается с парами
      (8:00–20:00 MSK — деплоим в ночь или выходные).
- [ ] Админы группы предупреждены о возможном 2-3 мин downtime
      (`rct-*` rolling restart).

### 1.7. Pre-flight diagnostics (M13 G23)

Aggregator script `scripts/preflight-deploy.sh` собирает все pre-flight
проверки в один entrypoint — env validation + Grafana dashboards
structure + Prometheus rules + compose syntax + critical files +
backup infra. Запусти **перед** `docker compose up -d`:

- [ ] `./scripts/preflight-deploy.sh /opt/rutcampustrack/.env.prod`
      — exit 0. Любой `✗ FAIL` блокирует deploy.

---

## 2. During deploy (T-0 .. T+15min)

### 2.1. Pull + migrate
- [ ] SSH на VPS: `cd /opt/rutcampustrack && git fetch && git checkout
      v0.x.y`.
- [ ] `docker compose -f docker-compose.prod.yml --env-file .env.prod
      pull` — все images новой версии стянуты.
- [ ] `docker compose -f docker-compose.prod.yml --env-file .env.prod
      up -d --no-deps auth-service academic-service schedule-service
      attendance-service` — Java-сервисы перезапускаются. Flyway
      мигрирует БД при старте academic/schedule/attendance.
- [ ] `docker compose -f docker-compose.prod.yml --env-file .env.prod
      up -d --no-deps notification-web notification-bot api-gateway
      pwa-nginx mini-app-nginx web-panel-nginx landing-nginx` —
      остальные контейнеры.

### 2.2. Healthchecks
- [ ] `docker compose ps` — все `rct-*` контейнеры status=`healthy`
      (не `starting`, не `unhealthy`).
- [ ] `curl -s https://ruttrack.site/actuator/health | jq .status` →
      `"UP"`. Gateway health агрегирует downstream'ы.
- [ ] Service-level health (internal): 
      `docker exec rct-api-gateway wget -qO- http://auth-service:9090/actuator/health`.

### 2.3. Alertmanager silence (если надо)
- [ ] Для ожидаемых рестартов: `amtool silence add --duration=10m
      severity=critical alertname=ServiceDown`.
- [ ] После deploy — `amtool silence expire <id>` или дождаться
      автоистечения.

### 2.4. Post-deploy contract verification (M13 G23)

Aggregator `scripts/verify-deploy.sh` проверяет M13-specific контракты
после `docker compose up -d` (≥60 сек на boot):

- container health (≥14 healthy)
- public HTTPS reachable (/login, /api/health)
- security headers (HSTS, CSP с report-uri, X-Frame-Options)
- /api/csp-report endpoint accepts violations (M13 G16)
- /prometheus + /alertmanager защищены basic-auth (M13 G14)
- 18 alert rules loaded в Prometheus (с creds — `SWAGGER_USER`/`PASS`)
- WebSocket /api/ws/ Upgrade headers (M13 G18)
- Mongo TTL index на notification_history (M13 G6)

- [ ] `./scripts/verify-deploy.sh https://ruttrack.site` — exit 0.
- [ ] Дополнительно app-level: `./scripts/smoke-prod.sh https://ruttrack.site
      student student_test_pass` (M08 G7) — login/schedule/logout.

---

## 3. Post-deploy (T+15min .. T+1h)

### 3.1. Smoke tests
- [ ] **Login flow**: открыть `https://ruttrack.site/login`, войти
      тестовым `student` / `teacher` / `admin`. Каждый попадает в свой
      `/student/*` / `/teacher/*` / `/admin/*`.
- [ ] **OTP flow**: `/login` через Telegram bot → код приходит в личку
      → verify → JWT pair получен.
- [ ] **Geo-checkin**: с тестового student-аккаунта открыть PWA на
      локации кампуса, отметиться на active lesson.
- [ ] **Excuse**: создать тикет, headman видит inline-кнопку в личке
      бота, нажимает Approve → attendance переходит в `excused`.

### 3.2. Business metrics
- [ ] Grafana dashboard `Business KPIs` — `otp_request_total`,
      `attendance_marked_total`, `login_attempts_total` не упали в 0.
- [ ] `outbox.lag.seconds` < 30s (все 3 сервиса). Если выше — rabbit
      bottleneck или publisher reconnect cycle.

### 3.3. Logs
- [ ] Grafana Loki — `{service=~"rct-.*"} |= "level":"ERROR"` за
      последние 15 мин. Error baseline — не выше чем до deploy.
- [ ] Новых unresolved stacktrace'ов нет (только ожидаемые gRPC
      reconnect'ы первые 10s после restart).

### 3.4. Tracing
- [ ] Grafana Tempo: ручной отмеченный checkin → full trace
      (gateway → attendance → schedule gRPC → rabbit publish → bot).
      Нет broken spans.

---

## 4. Rollback (если что-то пошло не так)

### 4.1. Application rollback
- Git: `git checkout <previous-tag>`.
- Compose: `docker compose -f docker-compose.prod.yml --env-file
  .env.prod up -d` — pull предыдущих images + restart.

### 4.2. DB rollback
- **Forward-compatible migration** (добавление nullable колонок,
  новые таблицы): rollback приложения без undo schema — backward
  совместимо.
- **Breaking migration** (DROP COLUMN, NOT NULL на existing): restore
  из backup через `scripts/restore.sh` (M13 G15).
  ```bash
  # Остановить downstream-сервисы (держат connections)
  docker compose -f docker-compose.prod.yml stop \
      auth-service academic-service schedule-service \
      attendance-service notification-web notification-bot api-gateway

  # Восстановить ВСЕ БД из последнего pre-deploy backup (§1.5)
  sudo /opt/rutcampustrack/scripts/restore.sh $(date -u +%Y-%m-%d) \
      --target=prod --confirm-prod

  # Поднять обратно (image уже откатан в §4.1)
  docker compose -f docker-compose.prod.yml start \
      auth-service academic-service schedule-service \
      attendance-service notification-web notification-bot api-gateway
  ```
  **Это destructive** — user-данные между backup и rollback потеряются.
  Всегда предпочитаем forward-only migrations. Подробнее —
  [backup-restore.md](runbooks/backup-restore.md).

### 4.3. Communication (если критично)
- Telegram-канал «RUT Track Status» (если создан) — сообщение о
  incident + ETA.
- Telegram бот `/status` возвращает «⚠️ Сервис недоступен» если
  healthcheck gateway failed.

---

## 5. T+2 weeks — performance audit (M13 G21)

После 2 недель prod traffic'а — accumulated `pg_stat_statements` даёт
реалистичный workload picture. Цель: catch'ить slow queries которые в
load-test'ах не проявились.

- [ ] `pg_stat_statements` включён (default в Postgres 14+):
      ```sql
      SHOW shared_preload_libraries;  -- должен включать pg_stat_statements
      ```
      Если нет — добавь в `docker-compose.prod.yml` postgres command
      `-c shared_preload_libraries=pg_stat_statements` + restart.

- [ ] Top-10 slow queries по mean time (academic_db):
      ```bash
      docker exec rct-pg-academic psql -U $POSTGRES_ACADEMIC_USER -d academic_db -c \
        "SELECT query, calls, mean_exec_time, total_exec_time
         FROM pg_stat_statements
         WHERE query NOT LIKE '%pg_stat%'
         ORDER BY mean_exec_time DESC LIMIT 10;"
      ```
      Аналогично для schedule_db.

- [ ] Для каждой query > 100ms mean time — `EXPLAIN ANALYZE`.
      Если видим Seq Scan на больших таблицах (users / lessons /
      homeworks / schedule_items) — нужен index. Создавай **только
      через CREATE INDEX CONCURRENTLY** (см. CLAUDE.md «База данных»).

- [ ] Проверь `MigrationConcurrentlyTest` в academic-app + schedule-app —
      зелёный. Каждая новая миграция с CREATE INDEX автоматически
      проверяется на CONCURRENTLY (M13 G21).

- [ ] HikariPoolExhaustion / HighRequestLatency alerts (M13 G19) за
      последние 2 недели — если fire'или, сильно correlate'нут с
      slow queries из top-10. Fix accordingly.

- [ ] Reset `pg_stat_statements` после fix'а:
      ```sql
      SELECT pg_stat_statements_reset();
      ```
      Чтобы следующий audit cycle не учитывал старые данные.

---

## 6. Checklist summary (copy-paste для release PR)

```markdown
## Deploy v0.x.y

### Pre-deploy
- [ ] main зелёный, tag создан, CHANGELOG обновлён
- [ ] MigrationIT passed locally + staging
- [ ] eventSchemaCoverageCheck passed
- [ ] cosign verify на images
- [ ] Backups academic/schedule/attendance сделаны
- [ ] Release window вне пар

### During
- [ ] docker compose pull / up -d
- [ ] Все containers healthy
- [ ] Gateway healthcheck UP

### Post-deploy
- [ ] Login smoke (student/teacher/admin)
- [ ] OTP flow
- [ ] Geo-checkin
- [ ] Excuse approve
- [ ] Grafana Business KPIs ≠ 0
- [ ] Loki error baseline ok
- [ ] Outbox lag < 30s
```
