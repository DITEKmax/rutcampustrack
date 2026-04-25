# Backup / Restore Runbook (M13 G15)

> Daily backup всех 3 БД (postgres-academic, postgres-schedule,
> mongo-attendance) + GPG-encrypted `.env.prod`. Retention 7 дней.
> Tested restore через `scripts/test-restore.sh` (запускать раз в квартал).

## Что бэкапится

| Источник | Файл в `/opt/backups/YYYY-MM-DD/` | Содержимое |
|----------|-----------------------------------|------------|
| `rct-postgres-academic` / `academic_db` | `academic.sql.gz` | Plain SQL dump (pg_dump) + gzip |
| `rct-postgres-schedule` / `schedule_db` | `schedule.sql.gz` | То же |
| `rct-mongo-attendance` (оба DB: `attendance_db` + `notification_db`) | `mongo.archive.gz` | BSON archive (mongodump --archive) + gzip |
| `/opt/rutcampustrack/.env.prod` | `env.prod.gpg` | GPG symmetric AES256, passphrase из password manager |

**НЕ бэкапится (ephemeral state):**
- Redis (auth OTP storage, bot reminder msg IDs) — восстанавливается автоматически при запросе пользователя.
- RabbitMQ (messages) — durable queues пересыпаются consumer'ами, недоставленные события восстанавливаются из outbox'а.
- Prometheus/Loki/Tempo TSDB (observability, 14d retention) — не критично для DR, потеря приемлема.
- Docker volumes nginx/certbot (SSL certs) — certbot переиздаёт автоматически.

## First-time setup на VPS

### 1. GPG passphrase file

GPG шифрует `.env.prod` с symmetric passphrase. Passphrase храним в
password manager (Bitwarden / 1Password) — single source of truth,
**не в git**, **не в коде**.

```bash
# Генерация (если пароль ещё не создан):
openssl rand -base64 32
# → скопировать в password manager как "RutCampusTrack — backup GPG passphrase"

# Записать на VPS в отдельный файл:
sudo tee /opt/rutcampustrack/.backup-passphrase >/dev/null <<EOF
<paste passphrase here, single line, no trailing newline if possible>
EOF
sudo chmod 600 /opt/rutcampustrack/.backup-passphrase
sudo chown root:root /opt/rutcampustrack/.backup-passphrase
```

**КРИТИЧНО:** без этого passphrase восстановить `.env.prod` из backup
НЕВОЗМОЖНО. Потеря passphrase = восстановление `.env.prod` вручную
(все 22 secrets заново: bot tokens rotate, DB passwords rotate,
VAPID rotate с потерей push-подписок).

### 2. GPG binary

```bash
sudo apt-get install -y gnupg
gpg --version   # 2.2+ ок
```

### 3. Cron job

```bash
sudo cp /opt/rutcampustrack/infra/cron/rutcampustrack-backup /etc/cron.d/rutcampustrack-backup
sudo chmod 644 /etc/cron.d/rutcampustrack-backup
sudo systemctl restart cron   # reload cron.d
# Verify:
sudo ls -la /etc/cron.d/rutcampustrack-backup
sudo grep CRON /var/log/syslog | tail -5   # проверка что cron подхватил
```

### 4. Log rotation

`backup.sh` логи идут в `/var/log/rutcampustrack-backup.log`. Без
ротации файл растёт бесконечно — настроить logrotate:

```bash
sudo tee /etc/logrotate.d/rutcampustrack-backup >/dev/null <<'EOF'
/var/log/rutcampustrack-backup.log {
    weekly
    rotate 8
    compress
    delaycompress
    missingok
    notifempty
    create 640 root adm
}
EOF
sudo logrotate -d /etc/logrotate.d/rutcampustrack-backup   # dry-run verify
```

### 5. Backup directory

```bash
sudo mkdir -p /opt/backups
sudo chmod 700 /opt/backups   # only root
sudo chown root:root /opt/backups
```

### 6. Smoke test

```bash
# Вручную запустить первый backup, проверить что все 4 файла созданы:
sudo /opt/rutcampustrack/scripts/backup.sh /opt/rutcampustrack/.env.prod
sudo ls -la /opt/backups/$(date -u +%Y-%m-%d)/
# Ожидается: academic.sql.gz + schedule.sql.gz + mongo.archive.gz + env.prod.gpg
```

## Daily automation

Cron 03:00 UTC (06:00 MSK) — вне учебного расписания.

Проверка после 24 часов:
```bash
ls /opt/backups/             # должно быть 1-2 директории
tail -50 /var/log/rutcampustrack-backup.log   # последний запуск ok?
```

**Retention:** `backup.sh` сам удаляет директории старше 7 дней.
Сверять: `find /opt/backups -mtime +8 -type d` должен быть пустым.

## Manual restore (в prod)

**Используется при DR** — прод-данные повреждены / случайное удаление / миграция.

```bash
# 1. Выбрать дату из /opt/backups/
ls /opt/backups/

# 2. Остановить downstream-сервисы (они держат connections)
docker compose -f docker-compose.prod.yml stop \
    auth-service academic-service schedule-service \
    attendance-service notification-web notification-bot api-gateway

# 3. Восстановить
sudo /opt/rutcampustrack/scripts/restore.sh 2026-04-25 --target=prod --confirm-prod

# 4. Restart downstream-сервисов
docker compose -f docker-compose.prod.yml start \
    auth-service academic-service schedule-service \
    attendance-service notification-web notification-bot api-gateway

# 5. Smoke test
./scripts/smoke-prod.sh https://ruttrack.site
```

**Опционально, если и `.env.prod` компрометирован** — расшифровать
backup'нутый:
```bash
sudo /opt/rutcampustrack/scripts/restore.sh 2026-04-25 \
    --target=prod --confirm-prod --with-env
# → /opt/backups/2026-04-25/env.prod.decrypted
sudo cp /opt/backups/2026-04-25/env.prod.decrypted /opt/rutcampustrack/.env.prod
sudo chmod 600 /opt/rutcampustrack/.env.prod
```

## Tested restore (quarterly)

**Цель:** убедиться, что backup'ы рабочие. Восстановим в изолированный
test-compose, сравним row count с prod'ом.

```bash
./scripts/test-restore.sh 2026-04-25
# → script поднимает rct-test-postgres-academic / rct-test-postgres-schedule /
#   rct-test-mongo-attendance, restore'ит последний backup, сравнивает row count.
#   Teardown в конце.
```

**Пройдено = row count совпадает с prod.** Первый квартал — раз в
**ЧАС** (burn-in), дальше раз в квартал.

**Записать результат в NOTES.md milestone'а / changelog** — иначе
забывается.

## Disaster recovery (VPS потерян)

**Сценарий:** VPS удалён провайдером / диск умер / взлом + wiped.
Цель — поднять стек на свежем VPS с актуальным backup'ом.

**Предпосылки:**
- Backup'ы дублируются **offsite** (VPS provider snapshot) — owner
  decision в debt #19. В M13 G15 offsite копирование НЕ автоматизируется
  (deferred в v0.1 backup-to-S3 job).
- Passphrase GPG в password manager (Bitwarden/1Password) — accessible
  с любой машины.

**Шаги:**

1. Получить последний backup из VPS provider snapshot (attach старый
   диск как secondary volume на новый VPS).
2. Скопировать `/opt/backups/{last}/` на новый VPS.
3. Поставить passphrase file: `echo "<passphrase>" > /opt/rutcampustrack/.backup-passphrase && chmod 600`.
4. `./scripts/restore.sh {last} --target=prod --confirm-prod --with-env` —
   restore всех 3 БД + decrypt `.env.prod` → `env.prod.decrypted`.
5. `cp env.prod.decrypted /opt/rutcampustrack/.env.prod && chmod 600`.
6. `docker compose -f docker-compose.prod.yml up -d` — стек поднимается
   с восстановленными данными.
7. Smoke test (`scripts/smoke-prod.sh`) + визуальный UAT (login → /admin → lessons).
8. DNS A-record на новый VPS (если IP изменился).
9. Certbot — `certbot renew --force-renewal` (новый VPS = новые certs).

**Estimated downtime DR:** 30-45 минут (из них 10 минут — ожидание
provider snapshot mount, 15 минут — DNS TTL, 10-15 минут — restore +
smoke).

## Troubleshooting

### `gpg: decryption failed: Bad session key`

Неверный passphrase в `/opt/rutcampustrack/.backup-passphrase`. Взять
из password manager заново. **Trailing newline** в файле — типичная
причина (`echo "..."` добавляет `\n`; лучше `printf '%s' "..." > file`).

### `pg_restore` failed `database already exists`

`restore.sh` делает `DROP DATABASE IF EXISTS + CREATE DATABASE` через
`postgres` (template1) connection — если падает, проверить, что никто
другой не держит connection на target DB:
```bash
docker exec rct-postgres-academic psql -U rct_user -d postgres \
    -c "SELECT pid, usename, application_name FROM pg_stat_activity WHERE datname='academic_db';"
```

### `mongorestore` failed `authentication failed`

`MONGO_ROOT_PASSWORD` в target `.env` не совпадает с тем, что в
running `rct-mongo-attendance`. Проверить:
```bash
docker exec rct-mongo-attendance mongosh --quiet --eval \
    "db.adminCommand({ping:1})" \
    -u root -p "$(grep MONGO_ROOT_PASSWORD /opt/rutcampustrack/.env.prod | cut -d= -f2)"
```

### `backup.sh` logs пустые / cron не запускает

Проверить, что cron подхватил:
```bash
sudo systemctl status cron
sudo tail -20 /var/log/syslog | grep -i cron
```

Проверить права:
```bash
ls -la /etc/cron.d/rutcampustrack-backup   # -rw-r--r-- root root
ls -la /opt/rutcampustrack/scripts/backup.sh   # -rwxr-xr-x
```

### Backup size растёт аномально

Обычный размер на v0.0.0 GA (empty DB):
- `academic.sql.gz` ~ 50 KB
- `schedule.sql.gz` ~ 30 KB
- `mongo.archive.gz` ~ 10 KB

Через год production — ожидается:
- postgres-academic ~ 10 MB (users + groups + semesters)
- postgres-schedule ~ 100 MB (lessons 1 year × ~500 per semester)
- mongo ~ 500 MB (attendances ~ 10k per day × 180 учебных дней + notifications TTL 30d)

При **>1 GB total** — reconsider retention / offsite-storage urgency.

## Deferred в v0.1

- **Offsite backup** (S3/Backblaze B2 rclone job). Сейчас единственный
  слой — VPS-local + provider snapshot. Acceptable для v0.0.0 GA
  (single-instance VPS, low-stakes data).
- **Backup integrity check** автоматический — сейчас только manual
  через `test-restore.sh`.
- **Prometheus metrics** — экспорт last-backup-age через textfile
  collector + alert `BackupMissing` если > 26h.
