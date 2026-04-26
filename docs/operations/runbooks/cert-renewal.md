# Runbook: TLS-cert renewal & troubleshooting

Полный playbook для Let's Encrypt TLS-сертификатов на VPS. Покрывает
first-deploy SSL setup, automated renewal flow, и troubleshooting когда
auto-renew сломался.

## Архитектура

```
┌──────────────────┐      ┌──────────────┐      ┌──────────────────┐
│ certbot          │      │ certbot-conf │      │ nginx            │
│ (sleep 12h loop) │ ───► │ volume       │ ◄─── │ (reload каждые   │
│ certbot renew    │      │ /etc/letsen- │      │  5 мин — auto    │
│ --quiet          │      │ crypt        │      │  pickup renewed  │
└──────────────────┘      └──────────────┘      │  cert)           │
                                                  └──────────────────┘
                                ▲
                                │ HTTP-01 challenge
                                │ /.well-known/acme-challenge/*
                                │
                         certbot-www volume → nginx :80 location
```

**Ключевые свойства:**

- `certbot` контейнер на background loop: `while :; do certbot renew --quiet; sleep 12h; done`.
- nginx auto-reload каждые 5 минут (M13 G14 entrypoint safety-net) →
  renewed cert подхватывается без manual `nginx -s reload`.
- Метрика `probe_ssl_earliest_cert_expiry` экспортируется blackbox-exporter
  (M13 G20). Alert'ы `SslCertExpiresSoon` (30d), `SslCertExpiresUrgently`
  (7d critical), `SslProbeFailed` (probe error).
- Init `nginx/conf.d/default.conf` location `/.well-known/acme-challenge/`
  на :80 server block — **должна быть до** HTTP→HTTPS redirect (Pitfall 3).

## First-deploy SSL setup

При первом deploy на чистый VPS у `nginx:443 ssl_certificate` указывает
на ещё не существующий cert. Нужен 2-phase deploy:

### Шаг 1 — DNS A-record

```
ruttrack.site.    A    <VPS_PUBLIC_IP>
```

Подожди propagation: `dig +short ruttrack.site`. Должен вернуть IP.

### Шаг 2 — HTTP-only deploy

Закомментируй `server { listen 443 ssl; ... }` block в
`nginx/conf.d/default.conf` временно. Оставь только HTTP server с
`/.well-known/acme-challenge/` location и redirect 80→443 **отключён**
(пока cert нет, redirect ломает ACME challenge).

```bash
ssh root@<vps>
cd /opt/rutcampustrack
docker compose -f docker-compose.prod.yml up -d nginx
```

Smoke: `curl http://ruttrack.site/.well-known/acme-challenge/test` →
404 (не connection refused).

### Шаг 3 — обтенение cert через certbot

```bash
docker compose -f docker-compose.prod.yml run --rm \
  -v /opt/rutcampustrack/certbot-conf:/etc/letsencrypt \
  -v /opt/rutcampustrack/certbot-www:/var/www/certbot \
  certbot/certbot:latest certonly --webroot -w /var/www/certbot \
  -d ruttrack.site \
  --email <admin@example.com> --agree-tos --no-eff-email
```

Ожидаем:
```
Successfully received certificate.
Certificate is saved at: /etc/letsencrypt/live/ruttrack.site/fullchain.pem
Key is saved at:         /etc/letsencrypt/live/ruttrack.site/privkey.pem
```

### Шаг 4 — раскомментировать HTTPS block + restart

Restore полный `nginx/conf.d/default.conf` (HTTP+HTTPS, 80→443 redirect):

```bash
docker compose -f docker-compose.prod.yml up -d
```

Smoke: `curl -I https://ruttrack.site/` → `200 OK` либо `301`. SSL
cert valid: `openssl s_client -connect ruttrack.site:443 -servername
ruttrack.site < /dev/null 2>&1 | grep "Verify return code"` → `0 (ok)`.

### Шаг 5 — verify auto-renew loop

```bash
docker logs rct-certbot --tail 20
```

Должен показать `Cert not yet due for renewal` (cert на 90 дней,
renewal в 60 при истечении 30 дней).

## Automated renewal flow

`certbot` контейнер каждые 12h вызывает `certbot renew --quiet`. Если
cert на ≤30 дней — Let's Encrypt выдаёт новый. Если >30 дней — exit 0
без действий. nginx auto-reload каждые 5 минут (`( while :; do sleep 5m;
nginx -s reload 2>/dev/null || true; done ) &` в `nginx/scripts/entrypoint.sh:57`)
— renewed cert загружается в memory автоматически.

**Verify auto-renew работает:**

```bash
# Текущий cert + истечение
docker exec rct-certbot certbot certificates

# Force renewal без ожидания 60d threshold (для smoke test)
docker exec rct-certbot certbot renew --dry-run

# Manual force-renew (НЕ для нормального flow)
docker exec rct-certbot certbot renew --force-renewal
```

`--dry-run` использует Let's Encrypt **staging** server — не считается
в rate-limit (5/week prod limit).

## Метрика для observability

`probe_ssl_earliest_cert_expiry` — Unix timestamp expiration. Через
blackbox-exporter (`infra/blackbox/blackbox.yml`, M13 G20):

```promql
# Дней до expiration
(probe_ssl_earliest_cert_expiry - time()) / 86400
```

Grafana panel либо ad-hoc query.

## Troubleshooting

### Cert не auto-renew — alert SslCertExpiresSoon fire'нул

1. **Проверь certbot контейнер up и не in crashloop:**
   ```bash
   docker compose ps certbot
   ```

2. **Logs:** `docker logs rct-certbot --tail 100`. Ищи:
   - `Cert not yet due for renewal` — норма (cert ещё свеж).
   - `Failed to renew certificate ruttrack.site` — error follow.
   - `Connection refused` — ACME challenge не доходит.
   - `urn:ietf:params:acme:error:rateLimited` — Let's Encrypt rate-limit.

3. **Force renew:**
   ```bash
   docker exec rct-certbot certbot renew --force-renewal
   ```

4. **Verify ACME challenge endpoint:**
   ```bash
   curl http://ruttrack.site/.well-known/acme-challenge/test
   ```
   Должен вернуть **404**, не connection refused. Если refused —
   nginx :80 не слушает либо firewall блокирует.

5. **Manual renewal через docker run** (если certbot контейнер сломан):
   ```bash
   docker run --rm \
     -v /opt/rutcampustrack/certbot-conf:/etc/letsencrypt \
     -v /opt/rutcampustrack/certbot-www:/var/www/certbot \
     certbot/certbot:latest renew --webroot -w /var/www/certbot
   ```

### nginx не подхватил renewed cert (cert обновился, но HTTPS отдаёт старый)

1. **Force reload manually:**
   ```bash
   docker exec rct-nginx nginx -s reload
   ```

2. **Verify cert mounted:**
   ```bash
   docker exec rct-nginx ls -la /etc/letsencrypt/live/ruttrack.site/
   ```
   `fullchain.pem` mtime должен совпадать с certbot renew time.

3. **Если volume не sync'нулся:** `docker compose restart nginx` (брутальный путь).
   Auto-reload loop в entrypoint должен срабатывать каждые 5 мин — если
   не помогает, проверь entrypoint logs:
   `docker logs rct-nginx --tail 50 | grep -E "reload|signal"`.

### Let's Encrypt rate-limit (5 certs / week / domain)

**Симптом:** `urn:ietf:params:acme:error:rateLimited` в certbot logs.

**Root cause:** обычно — много force-renewal'ов подряд (debugging),
либо cert удалён+пересоздан несколько раз.

**Fix:**
1. Подождать сброса rate-limit (rolling 7-day window).
2. Использовать staging server для тестов: certbot `--server
   https://acme-staging-v02.api.letsencrypt.org/directory`.
3. Backup: temp self-signed cert чтобы не потерять HTTPS:
   ```bash
   openssl req -x509 -newkey rsa:2048 -nodes \
     -keyout /opt/rutcampustrack/certbot-conf/live/ruttrack.site/privkey.pem \
     -out /opt/rutcampustrack/certbot-conf/live/ruttrack.site/fullchain.pem \
     -days 7 -subj "/CN=ruttrack.site"
   ```
   Браузер покажет cert warning, но HTTPS работает. После rate-limit
   reset — re-issue Let's Encrypt cert.

### SslProbeFailed alert

Probe success == 0 ≥ 10 мин. Diagnosis:

1. **HTTPS endpoint down?** — параллельный alert `ServiceDown{job="nginx"}`.
2. **TLS handshake fail:**
   ```bash
   openssl s_client -connect ruttrack.site:443 -servername ruttrack.site
   ```
   Ищи `Verify return code != 0`, `unable to get local issuer cert`,
   `certificate has expired`.
3. **DNS issue:**
   ```bash
   dig +short ruttrack.site
   ```
   IP должен быть VPS public. Если изменился — DNS provider misconfig.
4. **Firewall:** SSH на VPS, `iptables -L -n` или `ufw status` — :443
   открыт.

## Cron-based reload альтернатива

Hand-off NOTES упоминал «cron-based `nginx -s reload` каждые 12h как
simpler fallback». Это **уже implemented** через nginx entrypoint
background loop каждые 5 минут (M13 G14 `nginx/scripts/entrypoint.sh:57`).
Превышает 12h baseline → отдельный certbot deploy-hook не нужен.

Альтернативы deploy-hook'у которые **не используются**:

| Подход | Почему не используется |
|--------|------------------------|
| `--deploy-hook 'docker exec rct-nginx nginx -s reload'` | Требует mount `docker.sock` в certbot — security risk (full host control). |
| Sidecar `inotifywait` на certbot-conf volume | Усложняет stack ради 5-min latency benefit. |
| Cron на host (вне Docker) | Нарушает «всё в compose» principle. |
| **5-min reload loop в nginx entrypoint** | ✅ Текущее решение (M13 G14). |

Trade-off: до 5 мин between cert renewal и nginx pickup. Acceptable
для cert renewal (cert не expires в 5-min window после renewal).

## Связанные документы

- `docs/operations/deploy/prod-deploy-checklist.md` — full VPS deploy steps
- `docs/operations/monitoring/alerts.md` — `SslCertExpiresSoon`, `SslCertExpiresUrgently`,
  `SslProbeFailed` (M13 G20)
- `infra/blackbox/blackbox.yml` — blackbox-exporter probe config
- `nginx/scripts/entrypoint.sh` — nginx fail-fast + reload loop

## История изменений

- **v1.0** (2026-04-25, M13 G20): создан runbook. Покрывает first-deploy
  SSL, automated renewal flow, troubleshooting (5 scenarios).
