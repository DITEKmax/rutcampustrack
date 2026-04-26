# Промпт для следующей сессии — M15: First VPS Deploy `v0.0.0-alpha.16`

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Это **deploy session**,
не feature work — Claude помогает оператору пройти deploy checklist шаг
за шагом, ловит ошибки в выводе команд, объясняет что произошло, помогает
с rollback при необходимости.

---

**Контекст:** M14 «Post-Audit Fixes» закрыт 2026-04-26. Tag
`v0.0.0-alpha.16` создан и запушен на `origin/dev`. CI зелёный
(`a4b4cea` + `d46b1d6`). Готов к first VPS deploy v0.0.0.

**Это первый deploy на чистый VPS** — не routine release. Включает
SSL bootstrap, secret generation, JWT keypair generation, Flyway
initial migration, healthcheck verification на всех 26 контейнерах.

Главный документ: `docs/prod-deploy-checklist.md` (372 строки, секции
1.0-6). Этот промпт — **wrapper** который добавляет M14-specific
context + Claude assistance protocol.

## Pre-flight перед началом сессии

### 0.1 — verify clean state

```bash
cd C:/Users/maksd/IntelliJIDEA/rutcampustrack
git log --oneline -3
git status --short
git tag -l "v0.0.0-alpha.16"
```

Ожидаем:
- HEAD = `d46b1d6` (M14 finalised commit)
- Working tree clean (только `?? .gstack/`)
- `v0.0.0-alpha.16` tag есть

### 0.2 — verify VPS access

Это **operator action** — Claude не имеет SSH доступа к VPS. Оператор
должен подтвердить:
- [ ] SSH key загружен (`~/.ssh/id_rsa` либо аналог), `ssh ditekmax@<vps-ip>` работает
- [ ] sudo доступ на VPS
- [ ] DNS `ruttrack.site` указывает на VPS IP (`dig ruttrack.site +short`)
- [ ] Порты 80/443 открыты в firewall VPS

Если что-то не готово — **остановись здесь** и попроси оператора
подготовить инфраструктуру.

### 0.3 — what's new in M14 (что Claude должен знать про deploy)

M14 ввёл несколько изменений, которые **впервые срабатывают на этом
deploy** — нет previous-deploy опыта:

1. **G1 — Strict Internal JWT mode (CSO CRIT-01).**
   `RUTCAMPUSTRACK_SECURITY_LEGACY_HEADERS_ENABLED=false` — default. Если
   оператор вручную выставит `true` в `.env.prod` (для legacy debug),
   будет **regression к pre-M14 vulnerability**. Claude должен заметить
   это и предупредить.

2. **G3 — JWT keypair generation в deploy.yml (CSO HIGH-05).**
   Pipeline на VPS `openssl genrsa | pkcs8 -topk8 | rsa -pubout` →
   PKCS#8 keypair в `rutcampustrack_jwt-keys` volume. **First deploy**:
   volume пустой → keys генерятся. **Subsequent deploys**: idempotent
   skip через `[ ! -f /keys/private.key ]` filesystem guard. Если
   keys генерируются повторно — invalidate всех issued JWT (см.
   `docs/runbooks/secret-rotation.md`).

3. **G4 v2 — RequiredSecretsValidator fail-fast.**
   Container exit-1 на boot если **любой** required env var не set
   (см. application.yml `rutcampustrack.security.required-env-vars`
   per-service). Symptom: `IllegalStateException: M14 G4 (CSO HIGH-06):
   required environment variables are not set: [VAR_X, VAR_Y]` в logs
   ДО Spring banner. Все 22 секрета должны быть set в `.env.prod`.

4. **G6 — SHA-pinned actions** в deploy.yml (CSO HIGH-03/04 + MED-09).
   Если deploy workflow упадёт на step `Build and push *` — это
   возможный rebase action под compromised SHA (или Renovate digest
   bump через PR). Не fix через `latest` — investigate.

5. **G8 — burstCapacity production default 60.**
   gateway `auth-login` route: `burstCapacity=60` (5 req/min/IP per CSO
   threat model). Если post-deploy smoke получает 429 на /api/auth/login
   при первых нескольких запросах — это либо real brute-force (good
   signal), либо **multiple operators** одновременно тестируют login
   (false-positive). Wait 12 sec → token replenishes.

6. **G9 — notification-web compose env fix.**
   `RABBITMQ_PASSWORD` + `INTERNAL_ISSUER_SECRET` теперь явно в
   notification-web env block. Если на CI/prod notification-web упадёт
   с "required environment variables not set" → проверить что
   `.env.prod` содержит эти переменные с непустыми значениями.

## Deploy timeline (T-30min → T+1h)

Следуй `docs/prod-deploy-checklist.md` секции 1-3. Я (Claude) помогаю с:

- **парсинг output команд** — если `docker compose ps` показывает
  unhealthy контейнер, объяснить какой именно health check failed +
  правильный runbook
- **обнаружение проблем рано** — пример: после deploy.yml gradle build
  step есть `BUILD FAILED in 1m 17s` → не запускать `git tag`
- **rollback decision** — если после deploy 2+ сервисов unhealthy
  через 15 мин, рекомендовать rollback к previous tag (для first
  deploy — нет previous tag → emergency `docker compose down` +
  troubleshoot)

### T-30min: Pre-deploy (секция 1 в checklist'е)

#### 1.0 Environment secrets — **first deploy**

⚠️ **Critical first-time action.**

```bash
# Локально на dev машине:
cd C:/Users/maksd/IntelliJIDEA/rutcampustrack
cat .env.prod.example   # 28 переменных

# Скопировать на VPS:
scp .env.prod.example ditekmax@<vps-ip>:/opt/rutcampustrack/.env.prod
ssh ditekmax@<vps-ip>
cd /opt/rutcampustrack
chmod 600 .env.prod
```

Затем на VPS — заполнить все `CHANGE_ME` placeholder'ы. Comments в
`.env.prod.example` содержат точные команды для каждого секрета:

- `openssl rand -base64 24` для passwords
- `openssl rand -base64 48` для MONGODB_REPLICA_SET_KEY (только
  base64-алфавит, без `_` или `-` — в .env.prod.example есть warning)
- VAPID ключи через `npx web-push generate-vapid-keys`
- `BOT_TOKEN` / `TMA_BOT_TOKEN` / `BOT_ALERT_TOKEN` — из @BotFather
  на Telegram (3 разных bot'а: main + Mini App + alert receiver)

**Claude check after secret fill:**
```bash
# На VPS, после заполнения .env.prod
grep -c "CHANGE_ME" /opt/rutcampustrack/.env.prod
```
Должно быть `0`. Если больше 0 — какие-то секреты не заполнены, deploy
упадёт с RequiredSecretsValidator (M14 G4 v2).

#### 1.0a M14-specific secrets verify

Per M14 G4 v2 inventory (см. application.yml каждого сервиса):

| Service | Required env vars |
|---------|-------------------|
| api-gateway | `REDIS_PASSWORD`, `INTERNAL_ISSUER_SECRET` |
| auth-service | `REDIS_PASSWORD`, `SPRING_RABBITMQ_PASSWORD`, `POSTGRES_ACADEMIC_PASSWORD`, `INTERNAL_ISSUER_SECRET`, `TMA_BOT_TOKEN` |
| academic-service | `POSTGRES_ACADEMIC_PASSWORD`, `REDIS_PASSWORD`, `RABBITMQ_PASSWORD`, `GRPC_SECRET` |
| schedule-service | `POSTGRES_SCHEDULE_PASSWORD`, `RABBITMQ_PASSWORD`, `GRPC_SECRET` |
| attendance-service | `SPRING_DATA_MONGODB_URI`, `REDIS_PASSWORD`, `RABBITMQ_PASSWORD`, `GRPC_SECRET` |
| notification-web | `SPRING_DATA_MONGODB_URI`, `RABBITMQ_PASSWORD`, `INTERNAL_ISSUER_SECRET`, `ALERT_WEBHOOK_SECRET` |

`SPRING_DATA_MONGODB_URI` — composes из `MONGO_USER`/`MONGO_PASSWORD`
(attendance) либо `MONGO_NOTIFICATION_USER`/`MONGO_NOTIFICATION_PASSWORD`
(notification). docker-compose.prod.yml формирует URI inline — оператор
не задаёт `SPRING_DATA_MONGODB_URI` вручную.

#### 1.5b SSL / DNS bootstrap — **first deploy**

См. `docs/runbooks/cert-renewal.md`. Включает:
- `nginx/dhparam.pem` генерация (deploy.yml авто-генерит при first deploy)
- Let's Encrypt через certbot (один раз на чистом VPS)
- TLS auto-renewal cron

⚠️ **DNS должен быть set до certbot run** иначе ACME challenge
упадёт.

### T-0: During deploy (секция 2)

Используем GitHub Actions workflow `deploy.yml` (срабатывает на push в
`main` через `workflow_run` trigger из CI workflow). **`v0.0.0-alpha.16`
сейчас на `dev`** — нужно сначала **merge dev → main**:

```bash
# Локально:
git checkout main
git pull origin main
git merge dev --no-ff -m "Merge dev → main для v0.0.0-alpha.16 first VPS deploy"
git push origin main
```

После push на main:
1. CI workflow запустится (build + test + Trivy + gitleaks)
2. По green CI — `deploy.yml` workflow запустится через workflow_run
3. Deploy steps: build-push (11 images → GHCR) + sbom-sign (cosign keyless) + deploy (SSH к VPS, pull + compose up)

**Monitoring deploy:**
```bash
# Проверить статус через GitHub API (gh CLI отсутствует)
curl -s "https://api.github.com/repos/DITEKmax/rutcampustrack/actions/runs?branch=main&per_page=4" -o ci.json && py -c "import json; r=json.load(open('ci.json',encoding='utf-8'))['workflow_runs']; [print(f\"{x['name'][:25]:25s} | {x['status']:11s} | {x.get('conclusion') or 'in_progress':12s} | {x['head_sha'][:7]}\") for x in r[:6]]"
```

Либо открыть https://github.com/DITEKmax/rutcampustrack/actions в браузере.

#### 2.1 Deploy verify on VPS

После успешного deploy workflow:

```bash
ssh ditekmax@<vps-ip>
cd /opt/rutcampustrack
docker compose -f docker-compose.prod.yml ps
```

Ожидаем все 26 контейнеров (см. `docs/url-layout.md` для inventory)
со статусом `(healthy)`. Если какой-то `unhealthy` — Claude помогает
парсить logs.

#### 2.4 Post-deploy contract verification (M13 G23)

```bash
cd /opt/rutcampustrack
bash scripts/verify-deploy.sh
```

Скрипт проверяет:
- /actuator/health на всех 5 backend сервисах
- /api/auth/login + /api/academic/users smoke
- STOMP WebSocket connect + ticket consume
- Telegram bot webhook (если bot deployed)

### T+15min .. T+1h: Post-deploy (секция 3)

#### 3.1 Smoke tests

```bash
# Из любого места с интернетом (не обязательно VPS):
curl -fsSL https://ruttrack.site/login -o /dev/null && echo "LOGIN OK"
curl -fsSL https://ruttrack.site/api/auth/health && echo
curl -fsSL https://ruttrack.site/api/academic/health && echo
```

Все 200. Если 502/503 — gateway не routes к downstream → проверить
nginx logs + `docker compose logs api-gateway`.

#### 3.2 Business metrics (Grafana)

Открыть https://ruttrack.site/grafana (basic-auth из `GRAFANA_PASSWORD`
в `.env.prod`). Dashboards:
- "Business KPIs" (M04 + M13 G19) — request rate, error rate, latency p99
- "Container Resources" (M09 G7) — memory/CPU per container
- "JVM Heap" — каждый Java сервис

Первые 30 минут после deploy — smoke baseline. Не паникуй если
attendance.marked counter = 0 (никто пока не отметился). Critical:
**no 5xx spikes** на gateway.

#### 3.3 Alert silence verify

Открыть https://ruttrack.site/alertmanager (basic-auth). Должны быть
0 firing alerts если deploy успешный. Если firing — открыть
`docs/alerts.md`, найти runbook для конкретного alert'а.

### T+2 weeks: Performance audit

См. `docs/prod-deploy-checklist.md` секция 5. Проверки:
- query latency p99 на academic/schedule/attendance
- Mongo collection sizes + index usage (`db.notification_history.stats()`)
- Postgres slow query log (`pg_stat_statements`)
- Container memory utilization (mem_limit usage)

Если что-то выше threshold — Claude помогает open issue / спланировать
M16 «Performance Tuning».

## Rollback playbook (секция 4)

⚠️ **First deploy — нет previous tag для rollback.**

Если deploy провалился:
1. **Не откатывать compose.prod.yml** — он на новой версии image
2. **Останавливать stack:** `docker compose -f docker-compose.prod.yml down`
3. **Investigate:** logs всех unhealthy → root cause
4. **Fix locally:** code change → push → re-deploy

**Что НЕ делать:**
- НЕ удалять `rutcampustrack_jwt-keys` volume (обнулит JWT issuer keys
  → все issued tokens invalid после redeploy)
- НЕ удалять PostgreSQL/MongoDB volumes без backup
- НЕ force-push на `main` (нарушает `deploy.yml` workflow_run trigger)

## Что Claude НЕ делает в этой сессии

- НЕ запускает SSH команды на VPS (нет credentials, безопасность)
- НЕ изменяет `.env.prod` — только operator
- НЕ запускает destructive операции (compose down -v, docker volume rm)
  без явного operator confirmation
- НЕ делает merge dev→main без operator command (это release decision)

## Что Claude АКТИВНО делает

- ✅ Parse output команд от operator + объяснение проблем
- ✅ Cross-reference между чек-листом и runbook'ами
- ✅ Suggest specific runbook section если symptom matches
- ✅ Capture deploy session timeline в нового документа
  `docs/milestones/M15-first-vps-deploy/NOTES.md` для post-mortem
- ✅ После deploy success — обновить
  `docs/milestones/M15-first-vps-deploy/CHECKLIST.md` с зелёными
  галочками и любыми deviations от plan
- ✅ Если incident — Claude помогает root-cause через logs + предлагает
  hot-fix PR

## Deliverables после успешного deploy

1. **`docs/milestones/M15-first-vps-deploy/NOTES.md`** — таймлайн
   deploy сессии: что сделали, где были surprises, что заняло больше
   времени чем ожидали, какие manual steps нужно автоматизировать в
   M16+.
2. **`docs/milestones/M15-first-vps-deploy/CHECKLIST.md`** — копия
   `docs/prod-deploy-checklist.md` с галочками + любыми deviations.
3. **Возможный hot-fix commit** если deploy выявит prod-only bug,
   который не виден в e2e (rare, но возможно — например DNS issue,
   SSL chain mismatch, VPS firewall).
4. **Update `docs/milestones/README.md`** — новая строка M15 в таблице.
5. **Rotate `docs/milestones/NEXT-SESSION.md`** — на M16 либо
   "Post-Deploy Cleanup" depending on real-user signal.

## Если что-то идёт не так в deploy

**Symptom: контейнер `rct-X` exit-1 в первые 30 секунд**

Probable causes:
1. **RequiredSecretsValidator (M14 G4 v2)** — env var missing.
   Symptom: `java.lang.IllegalStateException: M14 G4 (CSO HIGH-06)`
   в logs. Fix: добавить missing env var в `.env.prod`, restart compose.
2. **Bind mount missing** — например `/keys/public.key` не существует.
   Fix: проверить `rutcampustrack_jwt-keys` volume, если empty —
   запустить deploy.yml снова (idempotent JWT keygen из M14 G3).
3. **DB connection** — Postgres/Mongo unhealthy → backend container
   зацикливается на reconnect.

**Symptom: gateway 502 на /api/***

1. Downstream service unhealthy
2. nginx → gateway connectivity (network rutcampustrack_private_net)
3. burstCapacity=60 (M14 G8) — 429 после 5 запросов; норма для smoke
   с одним worker, но если 502 — это другая проблема

**Symptom: SSL handshake fails**

См. `docs/runbooks/cert-renewal.md` § "First-deploy SSL bootstrap".
Возможно DNS не propagated либо certbot rate limit.

**Symptom: Telegram bot не отвечает на /start**

См. `docs/runbooks/bot-webhook-migration.md`. Probable: webhook URL не
set либо BOT_TOKEN неверный.

## История milestone'ов (архив)

- M01-M08 ✅ (`v0.0.0-alpha.1..alpha.9`)
- M09-M12 ✅ 2026-04-24 (`alpha.10..alpha.13`)
- M13 Pre-Deploy Hardening ✅ 2026-04-25 (`v0.0.0-alpha.15`)
- M14 Post-Audit Fixes ✅ 2026-04-26 (`v0.0.0-alpha.16`)
- **→ M15 First VPS Deploy** (текущий) — operator action, Claude assists
- → M16 (TBD) Post-Deploy Cleanup при необходимости

## Reference docs

- `docs/prod-deploy-checklist.md` — **главный чек-лист** (372 строки)
- `docs/runbooks/secret-rotation.md` — quarterly rotation
- `docs/runbooks/cert-renewal.md` — TLS bootstrap + renewal
- `docs/runbooks/bot-webhook-migration.md` — Telegram webhook
- `docs/runbooks/image-signing-verification.md` — cosign verify
- `docs/runbooks/migration-testing.md` — Flyway dry-run
- `docs/runbooks/backup-restore.md` — backup + GPG
- `docs/runbooks/mongo-indexes-verify.md` — TTL + compound indexes
- `docs/alerts.md` — каталог 18 alert'ов
- `docs/url-layout.md` — production URL routing (web-panel + PWA + API)
- `docs/architecture.md` — сервисы, ports, dependencies

## Финальная team checklist для оператора (TL;DR)

Перед началом deploy session:

- [ ] VPS доступен через SSH, sudo work, DNS `ruttrack.site` указывает на VPS
- [ ] `.env.prod` создан на VPS, все 22 secrets заполнены, `chmod 600`
- [ ] `git checkout main && git merge dev` готов к выполнению
- [ ] Внутренний канал коммуникации (если есть пользователи) — уведомлены о deploy
- [ ] 1-2 часа свободного времени — first deploy не stop-and-go

Поехали 🚀
