# Swagger UI Prod Access (NEW-125)

**Статус:** ✅ M11 G4 (2026-04-24) — basic-auth защищает `/swagger-ui*`,
`/v3/api-docs`, `/openapi/` в prod (`https://ruttrack.site`).
Связано с [secret-rotation.md](./secret-rotation.md).

---

## Зачем закрыто

Swagger UI выставляет **полную структуру API** всем endpoints
(request/response schemas, rate-limits, auth hints). В prod это:

1. **Reconnaissance vector** — злоумышленник видит список всех методов
   без brute-force.
2. **Versioning leak** — springdoc включает server identification
   (`SharedOpenApiCustomizer` → `/v3/api-docs` содержит URL paths
   и version tags).
3. **Swagger-UI XSS surface** — историческая серия CVE (последняя
   2024 CVE-2024-45801 — DOM XSS через spec URL).

Закрыто через nginx basic-auth до `rct-api-gateway:8080` upstream.

## Учётные данные

Формат env: `SWAGGER_HTPASSWD=swagger:<hash>`.

Хранится в `/opt/rutcampustrack/.env.prod` (600, owner root).
На старте nginx-контейнера материализуется в `/etc/nginx/.htpasswd`
(см. `docker-compose.prod.yml:nginx.command`).

**Login:** `swagger` (hardcoded в runbook'е; меняется только через
редактирование htpasswd формата — left side от `:`).
**Password:** не хранится в plain виде — только hash.

## Как получить доступ (dev / team member)

1. Запросить **plain password** у администратора (вручную через
   secure channel — Signal/PGP). Не через Telegram, не через email.
2. Использовать `swagger:<plain_password>` в HTTP Basic Auth header
   (браузер показывает dialog автоматически):
   ```bash
   curl -u swagger:<plain_password> https://ruttrack.site/swagger-ui.html
   # 200 OK → HTML со swagger-ui
   curl https://ruttrack.site/swagger-ui.html
   # 401 Unauthorized
   ```
3. Браузер: открыть `https://ruttrack.site/swagger-ui.html` → при
   первом запросе покажет native auth dialog.

Web-panel / PWA **не** используют `/swagger-ui` — все клиенты
читают OpenAPI через `docs/openapi/*.json` (generated TS types).
Basic-auth не влияет на пользовательский трафик.

## Ротация (раз в 6 месяцев)

Связана с общим [secret-rotation.md](./secret-rotation.md), но имеет
специфику — это **не** ротация сервисного секрета (сервисы не
используют `SWAGGER_HTPASSWD`). Downtime отсутствует.

1. **Сгенерировать новый hash** (bcrypt предпочтительнее apr1):
   ```bash
   # Bcrypt (apache2-utils):
   htpasswd -nB swagger
   # → swagger:$2y$05$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

   # apr1 fallback (openssl only — если apache2-utils недоступен):
   PASS=$(openssl rand -base64 12); \
       echo "Plain: $PASS"; \
       printf "swagger:"; openssl passwd -apr1 "$PASS"
   # → Plain: <16 chars>
   # → swagger:$apr1$xxxxxxxx$xxxxxxxxxxxxxxxxxxxxxx
   ```
2. **Обновить `.env.prod`:**
   ```bash
   ssh ruttrack-vps
   cd /opt/rutcampustrack
   cp .env.prod .env.prod.backup-$(date +%Y%m%d)  # 600, root only
   $EDITOR .env.prod   # заменить SWAGGER_HTPASSWD=...
   ```
   **ВАЖНО:** в `.env.prod` каждый `$` в hash'е должен быть удвоен
   (`$$`). docker-compose интерполирует `${VAR}` в YAML — без escape
   `$apr1` превращается в undefined var. Пример преобразования:
   ```
   Генератор вывод:   swagger:$apr1$AbCd$XyZ...
   В .env.prod:       swagger:$$apr1$$AbCd$$XyZ...
   В runtime env:     swagger:$apr1$AbCd$XyZ... (декодируется обратно)
   ```
3. **Перезапустить nginx** (nginx-only, не затрагивает API):
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env.prod \
       up -d --no-deps --force-recreate nginx
   ```
   Новый `.htpasswd` создаётся при старте container'а из env.
4. **Smoke-test:**
   ```bash
   # 401 без creds
   curl -s -o /dev/null -w "%{http_code}\n" \
       https://ruttrack.site/swagger-ui.html
   # → 401

   # 200 с новыми creds
   curl -s -o /dev/null -w "%{http_code}\n" \
       -u "swagger:$PASS" \
       https://ruttrack.site/swagger-ui.html
   # → 200

   # Старый password → 401
   curl -s -o /dev/null -w "%{http_code}\n" \
       -u "swagger:<старый_password>" \
       https://ruttrack.site/swagger-ui.html
   # → 401
   ```
5. **Обновить передачу команде:** отправить новый plain password
   через secure channel (Signal/PGP) всем, у кого был доступ.
6. **Удалить backup `.env.prod.backup-*` через 7 дней** (после
   подтверждения что никто не залогинен старым).

## При компрометации

Немедленная ротация (не ждать 6-месячного окна):
1. Шаги 1-4 из «Ротация» — новый hash + restart nginx.
2. `docker logs rct-nginx --since 24h | grep "swagger-ui" | \
       awk '{print $1}' | sort -u` — список IP, обращавшихся к
   swagger-ui за сутки. Если есть подозрительные — проверить
   `docker logs rct-api-gateway` на unusual pattern.
3. Уведомить команду в Signal canal.

## Failure mode: забыли пароль

Единственный recovery — сгенерировать новый через шаг «Ротация».
Plain password нигде не хранится (только hash).

## Связь с другими runbook'ами

- **[secret-rotation.md](./secret-rotation.md)** — общий процесс
  ротации всех секретов. SWAGGER_HTPASSWD можно ротировать
  параллельно с quarterly rotation (совместимо — downtime 0).
- **[openapi-conformance.md](../openapi-conformance.md)** — как
  работает backing OpenAPI spec, которую защищает swagger-ui.
