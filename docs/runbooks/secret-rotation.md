# Secret Rotation Runbook (NEW-155)

> Quarterly procedure: ротация всех секретов на VPS `/opt/rutcampustrack`.
> Минимум раз в 3 месяца + немедленно при подозрении на компрометацию.

## Инвентарь секретов

Все секреты хранятся в `/opt/rutcampustrack/.env.prod` (600, owner `root`)
и используются через `${VAR}` подстановку в `docker-compose.prod.yml`.

| Секрет | Куда инжектится | Downtime | Особенности |
|--------|-----------------|----------|-------------|
| `POSTGRES_ACADEMIC_PASSWORD` | academic-service + postgres-academic | 2-3 мин | Двусторонний — нужна SQL команда ALTER USER |
| `POSTGRES_SCHEDULE_PASSWORD` | schedule-service + postgres-schedule | 2-3 мин | То же |
| `MONGO_ROOT_PASSWORD` + `MONGO_USER/PASSWORD` | attendance-service + mongo | 3-5 мин | Через `db.updateUser()` в admin DB |
| `MONGO_NOTIFICATION_USER/PASSWORD` | notification-web + mongo | 2-3 мин | M10 D2 — отдельный credential на `notification_db` (PoLP) |
| `REDIS_PASSWORD` | auth/academic/bot + redis | 1-2 мин | `requirepass` в redis.conf + restart |
| `RABBITMQ_USER/PASSWORD` | все publisher/consumer + rabbit | 3-5 мин | `rabbitmqctl change_password` |
| `BOT_TOKEN` (Telegram) | notification-bot | 1 мин (только bot restart) | Генерируется через @BotFather |
| `TMA_BOT_TOKEN` | auth-service (TMA InitData verify) | 1 мин (только auth restart) | Тот же token что BOT_TOKEN — ротируются вместе |
| `INTERNAL_ISSUER_SECRET` | auth + gateway (token exchange) | 2 мин (auth + gateway rolling) | ≥32 байта, timing-safe compare; обе стороны одновременно |
| `GRPC_SECRET` | все gRPC client/server | 2-3 мин | Все Java-сервисы + bot одновременно |
| `ALERT_WEBHOOK_SECRET` / `BOT_ALERT_TOKEN` | alertmanager + bot `/internal/alert` | 1 мин | См. bot-webhook-migration.md |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` / `VAPID_SUBJECT` | notification-web | Невозможно без потери подписок | См. специальную процедуру ниже |
| `GRAFANA_PASSWORD` | grafana | 1 мин | admin login reset |
| `SWAGGER_HTPASSWD` | nginx (материализуется в /etc/nginx/.htpasswd) | 0 (только nginx restart) | M11 G4 NEW-125 — см. [swagger-prod-access.md](./swagger-prod-access.md), 6-month cadence |
| `CORS_ALLOWED_ORIGIN` / `MINI_APP_URL` | gateway / TMA | 0 (только при смене URL) | Не секрет, но часть `.env.prod` |
| `GHCR_TOKEN` (на VPS для image pull) | docker login | 0 | Вне compose — в `/root/.docker/config.json` |

## Общая процедура ротации

1. Подготовка нового секрета:
   - Для паролей: `openssl rand -base64 32`.
   - Для JWT/shared secrets (≥32 байта): `openssl rand -hex 32`.
   - Для VAPID: см. отдельный раздел.
2. Бэкап старого `.env.prod` → `.env.prod.backup-<YYYYMMDD>` (в
   `/root/secret-backups/`, 600, только root).
3. Обновить значение в `.env.prod`.
4. Выполнить specific steps per-secret (ниже).
5. `docker compose -f docker-compose.prod.yml --env-file .env.prod up
   -d --no-deps <service>` — restart затронутых сервисов.
6. Validation (ниже).
7. Через 24ч без инцидентов — удалить backup (`.env.prod.backup-*`).

---

## Per-secret steps

### PostgreSQL passwords (academic / schedule)

```bash
# 1. Новый пароль
NEW_PASS=$(openssl rand -base64 32)

# 2. Обновить в БД (пока старый ещё в .env.prod)
docker exec -it rct-postgres-academic psql -U rct_user -d academic_db \
  -c "ALTER USER rct_user WITH PASSWORD '$NEW_PASS';"

# 3. Обновить .env.prod — POSTGRES_ACADEMIC_PASSWORD="$NEW_PASS"
# 4. Restart academic-service (postgres сам не требует рестарта)
docker compose -f docker-compose.prod.yml --env-file .env.prod \
  up -d --no-deps academic-service
```

**Validation:** `docker logs rct-academic-service --tail 50 | grep
HikariPool` — `Started` без auth errors.

### MongoDB — attendance (MONGO_USER)

```bash
NEW_MONGO_PASS=$(openssl rand -base64 32)

# В mongo admin DB:
docker exec -it rct-mongo-attendance mongosh \
  -u $MONGO_ROOT_USER -p $MONGO_ROOT_PASSWORD --authenticationDatabase admin \
  --eval "db.getSiblingDB('admin').updateUser('${MONGO_USER}', {pwd: '$NEW_MONGO_PASS'});"

# Обновить .env.prod → MONGO_PASSWORD=$NEW_MONGO_PASS
# Restart:
docker compose up -d --no-deps attendance-service
```

### MongoDB — notification (MONGO_NOTIFICATION_USER, M10 D2)

Отдельный credential с правами только на `notification_db` (PoLP).
Ротация независима от `MONGO_USER`.

```bash
NEW_NOTIF_PASS=$(openssl rand -base64 32)

docker exec -it rct-mongo-attendance mongosh \
  -u $MONGO_ROOT_USER -p $MONGO_ROOT_PASSWORD --authenticationDatabase admin \
  --eval "db.getSiblingDB('admin').updateUser('${MONGO_NOTIFICATION_USER}', {pwd: '$NEW_NOTIF_PASS'});"

# Обновить .env.prod → MONGO_NOTIFICATION_PASSWORD=$NEW_NOTIF_PASS
# Restart:
docker compose up -d --no-deps notification-web
```

### Redis

```bash
NEW_REDIS_PASS=$(openssl rand -base64 32)

# Redis 7 поддерживает CONFIG SET requirepass, но его надо также в redis.conf
docker exec rct-redis redis-cli -a "$OLD_REDIS_PASSWORD" \
  CONFIG SET requirepass "$NEW_REDIS_PASS"

# Обновить .env.prod → REDIS_PASSWORD=$NEW_REDIS_PASS
# Если redis хранит данные в AOF/RDB — restart не требуется (CONFIG SET persists до рестарта; чтобы persist в redis.conf сделайте CONFIG REWRITE — однако мы монтируем конфиг из repo, поэтому обновите его и redeploy).

# Restart всех потребителей Redis:
docker compose up -d --no-deps auth-service academic-service \
  notification-web notification-bot redis
```

### RabbitMQ

```bash
NEW_RABBIT_PASS=$(openssl rand -base64 32)

# Сменить через rabbitmqctl:
docker exec rct-rabbitmq rabbitmqctl change_password "$RABBITMQ_USER" \
  "$NEW_RABBIT_PASS"

# Обновить .env.prod → RABBITMQ_PASSWORD=$NEW_RABBIT_PASS
# Restart всех producer/consumer (4 Java + 1 Python):
docker compose up -d --no-deps academic-service schedule-service \
  attendance-service notification-web notification-bot
```

### INTERNAL_ISSUER_SECRET (auth ↔ gateway token exchange)

**Critical:** auth и gateway должны стартовать ОДНОВРЕМЕННО с новым
секретом. Rolling с разными значениями = 30с downtime token exchange.

```bash
NEW_SECRET=$(openssl rand -hex 32)   # ≥64 hex-char = 32 bytes
# Обновить .env.prod → INTERNAL_ISSUER_SECRET=$NEW_SECRET
docker compose up -d --no-deps auth-service api-gateway
# Оба рестартуют одновременно, downtime ~15с (token exchange retry в gateway)
```

**Validation:** `docker logs rct-api-gateway | grep "internal-jwt"` —
нет `403 Forbidden` от auth-service.

### GRPC_SECRET

Все gRPC server'ы (academic/schedule/attendance) и client'ы (все
остальные + bot) используют один shared secret.

```bash
NEW_GRPC_SECRET=$(openssl rand -hex 32)
# Обновить .env.prod → GRPC_SECRET=$NEW_GRPC_SECRET
# ВСЕ Java-сервисы + bot одновременно:
docker compose up -d --no-deps auth-service academic-service \
  schedule-service attendance-service notification-web api-gateway \
  notification-bot
```

**Validation:** `docker logs rct-attendance-service --tail 100 | grep
"UNAUTHENTICATED\|GRPC"` — нет auth errors после 30с.

### BOT_TOKEN / TMA_BOT_TOKEN

Telegram token — идёт парой (bot использует для poll'инга, auth для TMA
InitData verify).

```bash
# 1. В @BotFather: /revoke → /token → получить новый
# 2. Обновить .env.prod → BOT_TOKEN=... и TMA_BOT_TOKEN=...
#    (всегда одно значение)
docker compose up -d --no-deps notification-bot auth-service
```

**Validation:**
- `docker logs rct-notification-bot --tail 50 | grep "getMe\|Aiogram"` — бот
  успешно аутентифицирован в Telegram.
- Тестовый `/login` → OTP приходит в Telegram.

### VAPID keys (Web Push — SPECIAL CASE)

**ВАЖНО:** ротация VAPID invalidates все существующие push-подписки
(`application_server_key` в subscription'е привязан к публичному VAPID).
После ротации пользователям PWA нужно перерегистрировать подписку
(service worker detect'ит VAPID mismatch и delete'ит cached subscription
при следующем загрузке PWA).

Делаем это ТОЛЬКО при подозрении на компрометацию private key. Штатная
quarterly ротация VAPID НЕ рекомендуется.

```bash
# 1. Сгенерировать новую пару:
docker exec rct-notification-web java -cp /app/lib/* \
  nl.martijndwars.webpush.cli.GenerateKeys
# Output: publicKey=BA..., privateKey=...

# 2. Обновить .env.prod:
#    VAPID_PUBLIC_KEY=...
#    VAPID_PRIVATE_KEY=...
#    VAPID_SUBJECT=mailto:admin@ruttrack.site  (не менять)
# 3. Restart notification-web
docker compose up -d --no-deps notification-web

# 4. Опубликовать banner в PWA (web-panel/pwa) — «Перезагрузите
#    страницу для восстановления push-уведомлений». В коде frontend
#    service worker увидит VAPID mismatch и заfetch'ит новый public key.
```

**Validation:** тестовая подписка с чистой сессии → push приходит.

### GHCR_TOKEN (на VPS для pull images)

Используется в `docker login ghcr.io`. Не в compose, а в
`/root/.docker/config.json`.

```bash
# 1. В GitHub → Settings → Developer settings → PAT → regenerate.
# 2. На VPS:
echo "$NEW_GHCR_TOKEN" | docker login ghcr.io -u ditekmax --password-stdin
```

### GRAFANA_PASSWORD

Admin password для Grafana UI.

```bash
NEW_GRAFANA_PASS=$(openssl rand -base64 24)
# Обновить .env.prod → GRAFANA_PASSWORD=$NEW_GRAFANA_PASS
docker compose up -d --no-deps grafana
# Зайти на https://ruttrack.site/grafana/ с admin / NEW — успех.
```

---

## Validation после всей ротации

- [ ] `docker compose ps` — все `rct-*` `healthy`.
- [ ] `https://ruttrack.site/actuator/health` → `UP`.
- [ ] Login flow (тестовые `student`/`teacher`/`admin`) — проходит.
- [ ] OTP flow через Telegram — получение кода + verify.
- [ ] Grafana Loki за последний час — нет `level=ERROR` про
      authentication / UNAUTHENTICATED / invalid credentials.
- [ ] Отменённый backup `.env.prod.backup-<YYYYMMDD>` через 24ч —
      shred + удалить.

## Schedule

- **Quarterly:** все perimeter-секреты (DB / Redis / Rabbit /
  INTERNAL_ISSUER / GRPC / ALERT_WEBHOOK / GRAFANA).
- **Annually:** BOT_TOKEN, GHCR_TOKEN.
- **On-demand:** VAPID (только при компрометации — ротация ломает все
  активные push-подписки).
- **Immediately on compromise:** любой уязвимый секрет + audit Loki за
  30 дней.

## History log

Ротации записываются в `/opt/rutcampustrack/rotation-log.txt` — строка
на ротацию: `<YYYY-MM-DD HH:MM> <secret-name> <rotated-by> <reason>`.
