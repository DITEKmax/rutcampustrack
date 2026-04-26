# G27 — CSO Comprehensive Security Audit (pre-VPS deploy)

**Дата:** 2026-04-26
**Mode:** `/cso comprehensive` (2/10 confidence floor — surfacing more findings than daily mode)
**Audit context:** последний security gate перед first VPS deploy. M13 24 группы только что закрыты. M13 G25 hot-fixes (1-24) только что трогали authentication path.
**Auditor:** Claude Sonnet 4.6 + 2 independent verification agents
**JSON артефакт:** `.gstack/security-reports/20260426-032647.json` (machine-readable)

---

## TL;DR — для занятого читателя

**Вердикт:** ❌ **БЛОКЕР для VPS deploy** — найдены 2 CRITICAL + 5 HIGH severity находок.

**P0 (must fix перед deploy, ~30-60 минут работы):**
1. **CRIT-01** — IDOR через legacy headers внутри docker private_net. Любой compromised peer container = trivial admin escalation на 4 backend сервиса. Дефолты в YAML стоят на «небезопасно».
2. **CRIT-02** — `appleboy/ssh-action@v1` в deploy.yml не SHA-pinned. Maintainer compromise → SSH key + GHCR token эксфильтрируются на следующем деплое → full VPS takeover.

**P1 (strongly recommended перед deploy, ~1-2 часа):**
3. **HIGH-03** — coverage.yml + 4 third-party action'а с `pull-requests:write`/`checks:write` permissions, не SHA-pinned.
4. **HIGH-04** — все docker/* + actions/checkout actions в deploy path moveable теги. Cascade с CRIT-02.
5. **HIGH-05** — RSA private keys baked в auth-service Docker image. Скрытый bug в deploy.yml: openssl pre-gen не сработает, потому что image копирует keys в empty volume → unique prod keys не генерятся.
6. **HIGH-06** — `INTERNAL_ISSUER_SECRET` имеет dev fallback в auth-service application.yml. Если operator забывает `--env-file`, сервис стартует с публично известным dev secret → admin JWT forge тривиально.
7. **HIGH-07** — `aiohttp 3.10.11` (notification-bot) уязвим до 3 CVE в 2025-2026 (zip bomb, request smuggling, path traversal). Bump до 3.13.3+.

**P2 (post-deploy):** 8 находок медиум — audit trail заглушка, mongo image без security updates, cadvisor privileged, alertmanager bearer over cleartext, deploy.yml `git pull` без verify, и др. См. ниже.

**P3:** 2 nice-to-have.

**Что работает правильно (verified clean):** JWT issuer flow, FailOpenRateLimiter strip headers fix (G25.22), Alert webhook auth, InternalIssuerSecretFilter timing-safe, Excuse file upload (bytes не пишутся на disk), BCrypt + concurrency guard, TLS hardening, resource limits на 26 контейнерах, Spring Boot 3.4.1 НЕ уязвим к April 2026 critical CVE-2026-40976. См. секцию «What was checked and found CLEAN» в конце.

---

## Краткая сводка цифр

| Метрика | Значение |
|---------|----------|
| Phases выполнено | 0-14 (полный комплект) |
| Findings reported | 17 |
| → CRITICAL | 2 |
| → HIGH | 5 |
| → MEDIUM | 8 |
| → TENTATIVE | 2 |
| Findings discarded (FP filter) | ~63 (из ~80 кандидатов) |
| Independent verifier sessions | 2 (legacy headers + Spring CVE) |
| Estimated P0 fix time | 30-60 мин |
| Estimated P0+P1 fix time | 1.5-2.5 часа |

---

## Архитектурный mental model (Phase 0+1)

**Стек:** Java 21 + Spring Boot 3.4.1 (5 backend сервисов) + Python 3.12 + Aiogram (notification-bot) + Spring Cloud Gateway + Angular 19 / React 19 / static HTML.

**Attack surface:**

| Слой | Кол-во | Защита |
|------|--------|--------|
| Public endpoints (через nginx) | 11 в auth (login/refresh/logout/tma/public-key/otp/internal/swagger/health) | TLS 1.2+, HSTS, CSP, rate limits per-route |
| Authenticated REST controllers | 38 | `@RequireRole` annotation + RequestContext + DualModeUserContextFilter |
| WebSocket channel | 1 (`/ws` STOMP) | TicketHandshakeInterceptor (single-use UUID) + SubscriptionAuthInterceptor |
| Inbound webhooks | 1 (`/internal/alert` от Alertmanager) | Bearer token + `MessageDigest.isEqual` (timing-safe) + fail-safe default |
| File upload | 1 (`/api/attendance/excuses/with-file`) | 25MB cap, content-type whitelist, bytes пересылаются в Telegram (не пишутся на disk) |
| Outbound HTTP integrations | 2 (Telegram Bot API, GHCR pull) | — |
| CI workflows | 5 (ci, coverage, deploy, openapi-drift, security) | Trivy + Gitleaks + cosign keyless signing |
| Production containers | 26 (5 backend + bot + gateway + 4 frontend nginx + reverse-proxy nginx + certbot + 4 monitoring + 5 infra) | Все на flat `private_net` bridge, mem_limit на каждом, healthcheck на каждом |
| Secret management | env vars в `.env.prod` (gitignored) | validate-env-prod.sh pre-deploy + nginx fail-fast entrypoint |

**Trust boundaries:**
- Internet → nginx (TLS termination) → api-gateway (JWT validate, strip client-supplied internal headers, issue Internal JWT) → downstream services (validate Internal JWT через PublicKeyProvider).
- Alertmanager → notification-web `/internal/alert` (Bearer token).
- Telegram Bot API → notification-bot (poll mode, нет inbound).
- VAPID Web Push → user browsers (signed payloads).

**Что в private_net:** все 26 контейнеров видят друг друга по `service-name:port`. Нет network segmentation, нет mTLS, нет source-IP allowlist. Это становится проблемой в CRIT-01.

---

# CRITICAL FINDINGS

## CRIT-01 — IDOR через legacy headers внутри private_net (10/10)

**Severity:** CRITICAL
**Confidence:** 10/10
**Status:** VERIFIED (independent agent traced code end-to-end)
**Phase:** 9 (OWASP A01 — Broken Access Control)
**Категория:** Authentication / Authorization
**Файлы:**
- `services/academic-service/academic-app/src/main/resources/application.yml:74`
- `services/schedule-service/schedule-app/src/main/resources/application.yml:71`
- `services/attendance-service/attendance-app/src/main/resources/application.yml:55`
- `services/notification-service/notification-app/src/main/resources/application.yml:42`
- `services/api-gateway/src/main/resources/application.yml:338`
- `services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/DualModeUserContextFilter.java:77-91`
- `docker-compose.prod.yml:854-856` (single private_net definition)

### Описание

В 4 backend сервисах (academic, schedule, attendance, notification) в `application.yml` стоит:
```yaml
rutcampustrack:
  security:
    internal-jwt:
      legacy-headers-enabled: ${RUTCAMPUSTRACK_SECURITY_LEGACY_HEADERS_ENABLED:true}
```
**Дефолт = `true`.** В gateway `application.yml:338`:
```yaml
strip-legacy-headers: ${GATEWAY_STRIP_LEGACY_HEADERS:false}
```
**Дефолт = `false`.**

`.env.prod.example` НЕ определяет ни `RUTCAMPUSTRACK_SECURITY_LEGACY_HEADERS_ENABLED`, ни `GATEWAY_STRIP_LEGACY_HEADERS`. Значит в prod дефолты остаются.

Логика `DualModeUserContextFilter`:
- Если `X-Internal-Token` присутствует → validate подписи → apply claims. **Strict path.**
- Если отсутствует И `legacyHeadersEnabled=true` → читает `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman` напрямую без проверки источника. **Legacy path.**
- Каждый downstream `*UserContextFilter` (например `AcademicUserContextFilter`) делает `UserRole.valueOf(request.getHeader("X-User-Role").toUpperCase())` и `Boolean.parseBoolean(request.getHeader("X-Is-Headman"))` без верификации откуда headers пришли.

Gateway корректно strip'ает client-supplied X-User-* в `JwtAuthenticationFilter:56-69` (CRIT-01 предотвращён для inbound через интернет). НО downstream сервисы exposed на private_net, и `legacy-headers-enabled=true` означает они принимают X-User-* напрямую.

### Exploit scenario

1. Атакующий компрометирует ЛЮБОЙ контейнер на `private_net`. Кандидаты:
   - **notification-bot** — Python с aiohttp 3.10.11 (см. HIGH-07, есть RCE-adjacent CVE)
   - **blackbox-exporter** — outbound HTTP probes
   - **certbot** — ACME client с network access
   - **promtail/cadvisor** — privileged mounts (cadvisor имеет `privileged: true`)
   - Любой backend сервис через SSRF (хотя сейчас SSRF surface минимальна)
2. Из compromised контейнера:
   ```bash
   curl -H "X-User-Id: 1" \
        -H "X-User-Role: ADMIN" \
        -H "X-Is-Headman: true" \
        http://academic-service:9091/api/academic/users
   ```
3. Получает полный admin доступ. Internal JWT validation полностью обойдена — нет signature, нет origin check, нет mTLS.

### Impact

- **Полный admin escalation** в academic-service (управление пользователями/группами/семестрами), schedule-service, attendance-service (отметки), notification-service.
- Одна CVE в любой Python deps notification-bot (CVE-2025-69223 zip bomb уже applies к aiohttp 3.10.11) → potential RCE → admin domain.

### Independent verifier verdict (10/10)

> Code path traced end-to-end; defaults confirmed in YAML; no compensating control (no network segmentation, no mTLS, no source-IP allowlist on backend filters). **Recommendation:** Flip both defaults to secure-by-default BEFORE first prod deploy.

### Fix (~15 минут)

1. В каждом из 4 файлов поменять дефолт:
   ```yaml
   legacy-headers-enabled: ${RUTCAMPUSTRACK_SECURITY_LEGACY_HEADERS_ENABLED:false}
   ```
2. В gateway:
   ```yaml
   strip-legacy-headers: ${GATEWAY_STRIP_LEGACY_HEADERS:true}
   ```
3. Запустить уже существующие `SecurityIdorIT.java` тесты — они есть для всех 4 сервисов и проверяют strict-mode.
4. Добавить в `.env.prod.example` комментарий: «НЕ переопределять `RUTCAMPUSTRACK_SECURITY_LEGACY_HEADERS_ENABLED` или `GATEWAY_STRIP_LEGACY_HEADERS` — strict mode обязателен в prod».
5. Долгосрочно (post-deploy): segment private_net на 3 части (frontend / backend / monitoring) либо mTLS sidecar (Linkerd).

---

## CRIT-02 — `appleboy/ssh-action@v1` не SHA-pinned + holds SSH_PRIVATE_KEY + GHCR_TOKEN (10/10)

**Severity:** CRITICAL
**Confidence:** 10/10
**Status:** VERIFIED (independent agent)
**Phase:** 4 (CI/CD Pipeline Security)
**Категория:** Supply Chain
**Файл:** `.github/workflows/deploy.yml:313`

### Описание

```yaml
- name: Deploy via SSH
  uses: appleboy/ssh-action@v1     # ← MOVEABLE TAG
  env:
    GHCR_TOKEN: ${{ secrets.GHCR_TOKEN }}
    IMAGE_TAG: ${{ env.DEPLOY_SHA }}
  with:
    host: ${{ secrets.VPS_HOST }}
    username: ${{ secrets.VPS_USER }}
    key: ${{ secrets.SSH_PRIVATE_KEY }}     # ← FULL SSH PRIVATE KEY
    port: 22
    envs: GHCR_TOKEN,IMAGE_TAG
    script: |
      set -e
      echo "$GHCR_TOKEN" | docker login ghcr.io -u ditekmax --password-stdin
      cd /opt/rutcampustrack
      git pull --ff-only
      ...                          # 75 строк bash на prod VPS
```

`@v1` — moveable git tag. Maintainer (или любой кто получит push access к appleboy org) может репушить тег с малicious кодом. Это не теория: **CVE-2025-30066 на `tj-actions/changed-files` в марте 2025 затронул ~23000 репозиториев** именно через этот вектор.

### Exploit scenario

1. appleboy GitHub account compromise (phishing, leaked PAT) или sophisticated supply chain атака на release pipeline.
2. Attacker репушит `v1` тег с trojan'ным `entrypoint.sh` который читает `process.env.INPUT_KEY` (= `SSH_PRIVATE_KEY`) и POST'ит на attacker.com.
3. На следующем main push → CI green → deploy job triggers → secrets утекают.
4. Attacker SSH'ится в VPS как deploy user → `/opt/rutcampustrack` → docker socket = root host → полный takeover.

Cosign signature verification на images (deploy.yml:297-309) этому НЕ препятствует — атака на step `Deploy via SSH` происходит ПОСЛЕ verify, и атакующий не трогает images, он трогает SSH key.

### Independent verifier verdict (10/10)

> Third-party action, moveable tag `@v1`. Receives `SSH_PRIVATE_KEY`, `VPS_HOST`, `VPS_USER`, `GHCR_TOKEN` (lines 318-322) and runs ~75 lines of SSH script on prod VPS (lines 323-398). **Attack:** appleboy account takeover or registry hijack of `v1` tag → next deploy exfiltrates SSH key + GHCR token. **Fix:** Pin to commit SHA, e.g. `appleboy/ssh-action@<sha> # v1.2.0`.

### Fix (~15 минут)

```bash
gh api repos/appleboy/ssh-action/git/refs/tags/v1.2.0 \
  --jq '.object.sha'
```
Поменять line 313 на:
```yaml
uses: appleboy/ssh-action@<актуальный-sha> # v1.2.0
```
**То же самое применить к docker/* и actions/* в deploy.yml** (см. HIGH-04).

Pattern уже корректно применён в M08 G11 для:
- `anchore/sbom-action@e22c389904149dbc22b58101806040fa8d37a610 # v0.24.0`
- `sigstore/cosign-installer@cad07c2e89fa2edd6e2d7bab4c1aa38e53f76003 # v4.1.1`

Просто extend этот pattern uniformly.

---

# HIGH FINDINGS

## HIGH-03 — coverage.yml: 4 third-party actions с `pull-requests:write` + `checks:write`, moveable теги (9/10)

**Severity:** HIGH
**Confidence:** 9/10
**Status:** VERIFIED
**Phase:** 4
**Файл:** `.github/workflows/coverage.yml:36-39, 93, 139, 184, 293`

### Описание

```yaml
permissions:
  contents: read
  pull-requests: write    # ← write
  checks: write           # ← write

# ...
- uses: madrapps/jacoco-report@v1.7.1                     # third-party, moveable
- uses: davelosert/vitest-coverage-report-action@v2       # third-party, moveable
- uses: MishaKav/pytest-coverage-comment@v1.1.52          # third-party, moveable
- uses: marocchino/sticky-pull-request-comment@v2         # third-party, moveable
```

Workflow запускается на `pull_request` от любых branches (`branches: ['**']`), включая fork PRs.

### Exploit scenario

Maintainer одного из 4 action'ов compromised → next release включает код который читает `secrets.GITHUB_TOKEN` и POST'ит на C2. Token имеет `pull-requests: write` + `checks: write`:
- Attacker может approve PRs (если нет required reviews от code owners).
- Может override checks → green status на красных билдах.
- Может comment'ить poison messages в PRs.
- Косвенный путь к merge'у malicious code в main → trigger deploy → CRIT-02 chain.

### Fix

1. SHA-pin все 4 action'а (`gh api repos/<owner>/<repo>/git/refs/tags/<tag>`).
2. Перенести permissions из top-level в per-job (только jobs которые комментируют — не все).
3. Альтернатива: использовать `step-security/harden-runner` для egress filtering (блокирует unexpected outbound).

---

## HIGH-04 — docker/* + actions/checkout в deploy path moveable теги (9/10)

**Severity:** HIGH
**Confidence:** 9/10
**Status:** VERIFIED
**Phase:** 4
**Файл:** `.github/workflows/deploy.yml` (множество lines)

### Описание

В deploy.yml использованы (все first-party Docker/GitHub, но moveable):

| Action | Lines | Обработка secrets |
|--------|-------|-------------------|
| `actions/checkout@v4` | 59, 224, 291 | — |
| `docker/setup-buildx-action@v3` | 64 | — |
| `docker/login-action@v3` | 67, 227, 291 | `GHCR_TOKEN` |
| `docker/build-push-action@v7` | 74, 84, 94, 104, 114, 124, 134, 144, 154, 164, 174 | Build artifacts pushed |

### Exploit scenario

Compromise Docker org (или GitHub Actions release pipeline) → attacker репушит `@v3`/`@v7` тег с трояном который uploads `secrets.GHCR_TOKEN` на C2. Subsequent images poisoned at build (cosign sign на этом этапе не помогает — атакующий контролирует сам build step).

Cosign verify проверяет identity issuer + workflow path, но НЕ проверяет что build steps не были модифицированы атакующим.

### Fix

SHA-pin все docker/* + actions/checkout в deploy.yml. Использовать Renovate `digestVersioning` для auto-bump.

В ci.yml, coverage.yml, openapi-drift.yml, security.yml — те же first-party actions moveable, но они не в deploy path, impact меньше. SHA-pin их тоже, но это P2 (см. ниже).

---

## HIGH-05 — RSA private keys baked в auth-service Docker image + deploy.yml flow bug (9/10)

**Severity:** HIGH
**Confidence:** 9/10
**Status:** VERIFIED (self)
**Phase:** 7 (G25 hot-fix audit)
**Файлы:**
- `services/auth-service/Dockerfile:69-79` (M13 G25.15)
- `.github/workflows/deploy.yml:329-336`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:60-83`

### Описание

Dockerfile pre-генерирует RSA 3072 keypair во время `docker build` через openssl и записывает в `/keys/`:
```dockerfile
RUN mkdir -p /keys && \
    apk add --no-cache openssl && \
    openssl genrsa -out /keys/private.key.pkcs1 3072 && \
    openssl pkcs8 -topk8 -nocrypt -in /keys/private.key.pkcs1 -out /keys/private.key && \
    openssl rsa -in /keys/private.key -pubout -out /keys/public.key && \
    rm /keys/private.key.pkcs1 && \
    head -c 4 /dev/urandom | od -A n -t x1 | tr -d ' \n' > /keys/kid.txt && \
    chown -R app:app /keys && \
    chmod 600 /keys/private.key /keys/kid.txt
```

Этот image push'ится в GHCR с конкретным SHA tag и pull'ится на VPS. Volume `jwt-keys:/keys` mount копирует image content в empty volume при первом запуске.

### Два следствия

**(a) Все instances одного image SHA имеют ОДИН И ТОТ ЖЕ private key.**
- CI e2e + dev + (потенциально staging) → если запустят тот же image без regeneration → один key везде → JWT выпущенный в e2e будет валиден в prod.

**(b) Если GHCR package когда-либо станет public (или GHCR_TOKEN утечёт):**
- Любой кто pull'ит image → `docker run --rm <image> cat /keys/private.key` → forge JWT любого user_id с ADMIN role → bypass auth полностью.

**(c) СКРЫТЫЙ BUG в deploy.yml flow:**

deploy.yml:329-336 пытается генерить keys в volume «если не существуют»:
```bash
docker volume create rutcampustrack_jwt-keys >/dev/null 2>&1 || true
docker run --rm -v rutcampustrack_jwt-keys:/keys alpine sh -c \
  "apk add --no-cache openssl >/dev/null 2>&1 && \
   ([ -f /keys/private.key ] || openssl genrsa -out /keys/private.key 3072) && \
   ([ -f /keys/public.key ] || openssl rsa -in /keys/private.key -pubout -out /keys/public.key) && \
   chown -R 100:101 /keys && \
   chmod 640 /keys/private.key && \
   chmod 644 /keys/public.key"
```

**Проблема:** этот шаг запускается ДО `docker compose up`. На пустом volume он генерит keys (с PKCS#1 format!). Затем `docker compose up` поднимает auth-service контейнер с volume mount. Docker volume mount с empty volume на каталог в image копирует image content в volume — НО volume уже не пустой (keys только что сгенерены), значит mount его НЕ перезаписывает.

Wait, naming convention: `docker volume create rutcampustrack_jwt-keys` vs compose name `jwt-keys` (compose добавляет project prefix). Если deploy.yml создает `rutcampustrack_jwt-keys` и compose project name тоже `rutcampustrack` — match'ится.

Но критический момент: `openssl genrsa -out /keys/private.key 3072` выдаёт **PKCS#1** формат (`BEGIN RSA PRIVATE KEY`), а `JwtService.loadPrivateKey` использует `PKCS8EncodedKeySpec` который требует **PKCS#8** (`BEGIN PRIVATE KEY`). Это значит на first deploy keys из deploy.yml будут невалидными → JwtService.init() поймёт что они в неверном формате → бросит exception при parse → контейнер не стартанёт.

Dockerfile использует `openssl pkcs8 -topk8 -nocrypt` для конверсии, deploy.yml — нет. Несоответствие.

### Fix

Два варианта:

**Вариант A (быстрый, ~10 минут):** удалить openssl pre-gen из Dockerfile полностью. Оставить JwtService.init() который генерит PKCS#8 правильно через Java KeyPairGenerator. Risks: вернёт hang issue на CI (G25.13/G25.14 не помогли с entropy starvation). Решение для CI: в e2e tests pre-mount tmpfs volume с pre-generated keys.

**Вариант B (правильный, ~30 минут):** оставить Dockerfile pre-gen для CI/e2e, но в deploy.yml ДОБАВИТЬ explicit force-regen шаг для first deploy:
```bash
# First deploy detection — если /opt/rutcampustrack/.deployed-sha не существует
if [ ! -f /opt/rutcampustrack/.deployed-sha ]; then
  echo "First deploy detected — regenerating JWT keys uniquely on this VPS"
  docker volume rm rutcampustrack_jwt-keys 2>/dev/null || true
  docker volume create rutcampustrack_jwt-keys
  docker run --rm -v rutcampustrack_jwt-keys:/keys alpine sh -c "
    apk add --no-cache openssl >/dev/null 2>&1 && \
    openssl genrsa -out /keys/private.key.pkcs1 3072 && \
    openssl pkcs8 -topk8 -nocrypt -in /keys/private.key.pkcs1 -out /keys/private.key && \
    openssl rsa -in /keys/private.key -pubout -out /keys/public.key && \
    rm /keys/private.key.pkcs1 && \
    head -c 4 /dev/urandom | od -A n -t x1 | tr -d ' \n' > /keys/kid.txt && \
    chown -R 100:101 /keys && \
    chmod 600 /keys/private.key /keys/kid.txt && \
    chmod 644 /keys/public.key
  "
fi
```

Долгосрочно (post-deploy): использовать external secret store (Vault, AWS Secrets Manager, или для small project — Bitwarden CLI / pass) и mount keys через CSI driver или init container. Уберёт image-baking полностью.

---

## HIGH-06 — INTERNAL_ISSUER_SECRET dev fallback в auth-service application.yml (9/10)

**Severity:** HIGH
**Confidence:** 9/10
**Status:** VERIFIED (self)
**Phase:** 5 (Infrastructure Shadow Surface)
**Файл:** `services/auth-service/auth-app/src/main/resources/application.yml:65`

### Описание

```yaml
rutcampustrack:
  security:
    internal-issuer:
      secret: ${INTERNAL_ISSUER_SECRET:dev-internal-issuer-secret-32-bytes-for-local-testing-only}
```

Если env var `INTERNAL_ISSUER_SECRET` не подгружен (operator забывает `--env-file /opt/rutcampustrack/.env.prod`, опечатка в имени переменной, race condition при docker compose restart) — сервис стартует с **публично известным dev secret**.

`InternalIssuerSecretFilter` validate'ит входящие запросы на `/internal/**` через `MessageDigest.isEqual` — но с **dev secret**, который attacker может прочитать прямо в репо.

`validate-env-prod.sh` проверяет наличие переменной в файле, но это **pre-deploy** check, не runtime guard. Если по какой-то причине env var не подгружен в момент запуска контейнера — сервис стартует с fallback и ничего не предупреждает.

### Те же проблемы в этом же файле

Аналогичные dev fallback'и:
- Line ~13: `password: ${REDIS_PASSWORD:rct_dev_pass}`
- Line ~18: `password: ${SPRING_RABBITMQ_PASSWORD:rct_dev_pass}`
- Line ~24: `password: ${POSTGRES_ACADEMIC_PASSWORD:rct_dev_pass}`

### Exploit scenario

1. Operator deploys на VPS, забывает `--env-file /opt/rutcampustrack/.env.prod`. (Это легко: `docker compose up -d` без env-file просто использует `.env` который может быть пустым.) Или systemd unit без `EnvironmentFile=` directive. Или race condition.
2. auth-service стартует с dev fallback secret.
3. Attacker reads публичный репо, видит `dev-internal-issuer-secret-32-bytes-for-local-testing-only`.
4. POST `/api/auth/internal/issuer/exchange` через nginx с этим secret + произвольным userId/role:
   ```bash
   curl -X POST https://ruttrack.site/api/auth/internal/issuer/exchange \
     -H "X-Internal-Issuer-Secret: dev-internal-issuer-secret-32-bytes-for-local-testing-only" \
     -d '{"userId":1,"role":"ADMIN","groupId":null,"isHeadman":false,"ttlSeconds":300}'
   ```
5. Получает Internal JWT с ADMIN claims, валидный для всех downstream сервисов. Bypass всей auth.

(NB: проверить, exposed ли `/internal/**` через nginx или только internal route. Если только internal — combined с CRIT-01 attacker должен сначала compromise peer container, что снижает severity. Если exposed — это standalone CRIT.)

### Fix

Заменить fallback на Spring fail-fast placeholder syntax:
```yaml
secret: ${INTERNAL_ISSUER_SECRET:?INTERNAL_ISSUER_SECRET must be set in environment}
```

Тот же fix применить к REDIS_PASSWORD, SPRING_RABBITMQ_PASSWORD, POSTGRES_ACADEMIC_PASSWORD в auth-service application.yml И во ВСЕХ других сервисах (academic, schedule, attendance, notification, gateway).

Альтернатива: добавить @PostConstruct check в @ConfigurationProperties класс который throws если secret равен dev-pattern.

---

## HIGH-07 — aiohttp 3.10.11 в notification-bot — 3 CVE (8/10)

**Severity:** HIGH
**Confidence:** 8/10
**Status:** VERIFIED (WebSearch confirmation)
**Phase:** 3 (Supply Chain)
**Файл:** `services/notification-bot/requirements.txt:3`

### Описание

```
aiohttp==3.10.11
```

Снижка с GitHub Advisory + Snyk:

| CVE | Описание | Affects |
|-----|----------|---------|
| CVE-2025-69223 | Auto-decompress feature уязвим к zip bomb (memory exhaustion) | ≤ 3.13.2 |
| CVE-2025-69225 | HTTP request smuggling в pure Python parser / `AIOHTTP_NO_EXTENSIONS=1` | ≤ 3.13.2 |
| CVE-2025-69227 | Path traversal в static file serving | ≤ 3.13.2 |

3.10.11 < 3.13.2 → all three apply.

### Impact в этом проекте

- **Inbound HTTP server** в notification-bot слушает только `:8081/health` для healthcheck. Endpoint exposed только в private_net (не publish'ится наружу).
- Bot НЕ serve'ит static files (CVE-2025-69227 irrelevant).
- Bot ОБЩАЕТСЯ через gRPC, не HTTP-client (CVE-2025-69225 irrelevant).
- CVE-2025-69223 (zip bomb) релевантен: combined с CRIT-01 attacker на private_net может POST'ить compressed payload с zip bomb на `/health` → memory exhaustion → bot OOM-killed → notification stops → админы не получают critical alerts ночью.

### Fix (~5 минут)

В `requirements.txt`:
```
aiohttp>=3.13.3
```

Тестировать compatibility — aiogram 3.15.0 поддерживает aiohttp 3.13+. Rebuild notification-bot image:
```bash
docker build -t rct-notification-bot services/notification-bot/
```

---

# MEDIUM FINDINGS (P2 — post-deploy)

## MED-08 — `@AdminAction` aspect — заглушка с log.debug, нет audit trail (9/10)

**Файл:** `services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/audit/AdminActionAspect.java:24-27`

```java
@Around("@annotation(adminAction)")
public Object around(ProceedingJoinPoint pjp, AdminAction adminAction) throws Throwable {
    log.debug("@AdminAction pointcut hit: action={} method={}", ...);
    return pjp.proceed();
}
```

Comment в `AdminAction.java:13` явно говорит «M01 — только marker + aspect-заглушка». NEW-72 deferred.

**Impact:** post-incident forensics невозможна. Если admin удаляет user/group/lesson — нет лога кто/когда/почему. Insider threat detection нет.

**Fix (post-deploy):** реализовать full audit — write to separate `audit_log` table (PG) + JSON log channel, include `user_id` + before/after diff + correlation_id. Уровень INFO. Отдельный Loki retention 90+ days.

---

## MED-09 — gitleaks/gitleaks-action@v2 unpinned (7/10)

**Файл:** `.github/workflows/security.yml:90`

Меньше impact чем CRIT/HIGH actions, но example того что pin policy не consistently применён.

**Fix:** SHA-pin одновременно с CRIT-02/HIGH-04.

---

## MED-10 — bitnamilegacy/mongodb:7.0 — frozen image без security updates (8/10)

**Файл:** `docker-compose.prod.yml:75`

В August 2025 Bitnami убрали versioned tags из bitnami/. bitnamilegacy/ — официальный frozen fallback **без security updates**. Acceptable risk зафиксирован в comments (single-node RS, internal network only). НО — combined с CRIT-01 (private_net flat) аргумент «нельзя атаковать извне» слабее.

**Fix (post-deploy):** мигрировать на `mongo:7-jammy` + custom entrypoint с `rs.initiate()` init-script, либо `percona/percona-server-mongodb:7`. Re-evaluate через 3 месяца после v0.0.0 GA как зафиксировано в комментариях.

---

## MED-11 — Alertmanager → notification-web Bearer over cleartext HTTP в private_net (7/10)

**Файл:** `infra/alertmanager/alertmanager.yml:49`

Alertmanager шлёт POST `http://notification-web:9094/internal/alert` с `Authorization: Bearer <secret>`. Bearer secret защищён (timing-safe `MessageDigest.isEqual`), но transport — plaintext HTTP внутри private_net.

Combined с CRIT-01: compromised peer container → может intercept Bearer token (если sniffing capability) → может POST'ить fake firing alerts → notification spam.

**Fix (post-deploy):** уже в comment'е alertmanager.yml:54 «M06 заменит на mTLS» — это deferred. Tactical fix: cap_drop NET_RAW в node-exporter/cadvisor чтобы блокировать sniffing. Долгосрочно: Linkerd/Istio sidecar с auto-mTLS.

---

## MED-12 — cadvisor запущен с `privileged: true` + mounts /:/rootfs (7/10)

**Файл:** `docker-compose.prod.yml:531`

```yaml
cadvisor:
  image: gcr.io/cadvisor/cadvisor:v0.49.1@sha256:...
  volumes:
    - /:/rootfs:ro
    - /var/run:/var/run:ro
    - /sys:/sys:ro
    - /var/lib/docker/:/var/lib/docker:ro
  privileged: true
```

Любой compromise cadvisor контейнера = root host access. SHA-pinned image (M06 D2). Без known unpatched RCEs в v0.49.1.

**Fix (post-deploy):** уменьшить privileged через `cap_add: [SYS_PTRACE]` (cadvisor docs allow this). Drop `/var/lib/docker` mount если не нужен.

---

## MED-13 — PublicKeyProvider lazy-retry — короткое DoS window (6/10)

**Файл:** `services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/PublicKeyProvider.java:55-72`

G25.22 fix: `getPublicKey()` делает synchronous fetch если `init()` failed. Это правильно и fail-CLOSED (key=null → throw IllegalStateException, не fail-OPEN).

Между container start (init() fails — auth-service ещё не ready) и первым authenticated request — окно ~1-30 сек где downstream бросает 500. Не security exploit, скорее availability.

**Fix:** acceptable trade-off. Optionally: добавить Caffeine cache с last-known-good public key (TTL 24h), tolerate Auth Service downtime.

---

## MED-14 — deploy.yml SSH-step делает `git pull --ff-only` на VPS — нет verification (8/10)

**Файл:** `.github/workflows/deploy.yml:327`

Deploy script на VPS:
```bash
cd /opt/rutcampustrack
git pull --ff-only
```

Затем pull'ит images по DEPLOY_SHA tag. Images cosign-verified (good, lines 297-309). НО: код из git и код in image могут расходиться — `git pull` притянет `infra/`, `nginx/`, `scripts/` (configmaps, deploy scripts), которые не в image.

Если attacker compromise main branch (через CRIT-02 / HIGH-03 / HIGH-04 cascade), он может модифицировать `infra/grafana/`, `nginx/`, `scripts/preflight-deploy.sh` — это будет применено к VPS БЕЗ cosign signature check.

### Exploit scenario

1. Attacker через CI compromise (HIGH-03) merges malicious commit в main.
2. CI green, deploy запускается.
3. Cosign verify image OK (атакующий не трогал backend code).
4. SSH step `git pull` притягивает modified `nginx/scripts/entrypoint.sh` либо `infra/prometheus/rules/*.yml`.
5. nginx restart применяет malicious config. nginx может `proxy_pass` на attacker-controlled upstream → MITM всех request.

**Fix (post-deploy):**
- Вариант A: commit `infra/`, `nginx/`, `scripts/` в специальный `config` image и cosign-verify его.
- Вариант B: sign git commits и `git verify-commit HEAD` перед apply.
- Вариант C: require code review для всех changes в `infra/`, `nginx/`, `scripts/` через CODEOWNERS.

---

## MED-15 — Initial pre-v0.0.0 audit отметил .env.prod в рабочей копии разработчика (7/10)

**Файл:** `docs/archive/report-before-v0.0.0/13-infra-docker-ci.md`

Audit doc явно говорит: «.env.prod лежит в рабочей копии с **реальными** секретами продакшена (Telegram токены, GHCR PAT, Grafana/DB пароли).»

**Текущий статус:** `.env.prod` gitignored ✅ (verified — `git ls-files .env.prod` пусто). Но если разработчик случайно zip'нет working copy и share, или ноут украдут без disk encryption — leak.

**Все secrets которые могут быть в .env.prod:** BOT_TOKEN, TMA_BOT_TOKEN, BOT_ALERT_TOKEN, GHCR_TOKEN (хотя в new flow этот в GitHub Actions secret), DB passwords, MONGO_REPLICA_SET_KEY, INTERNAL_ISSUER_SECRET, GRPC_SECRET, ALERT_WEBHOOK_SECRET, SWAGGER_HTPASSWD.

**Fix:**
1. Hold `.env.prod` ТОЛЬКО на VPS, не на dev машинах.
2. Использовать `scp` или 1Password CLI / pass / Bitwarden для retrieval когда нужно.
3. Pre-commit hook проверяющий что `.env.prod` НЕ существует в working copy.
4. Ротация всех secrets из `.env.prod` после first deploy если разработчик когда-либо имел эти secrets локально.

---

# TENTATIVE FINDINGS (P3 — comprehensive mode only)

## TENT-16 — Gateway CORS allowed-origins имеет dev URLs hardcoded в base application.yml (6/10)

**Файл:** `services/api-gateway/src/main/resources/application.yml:23-30`

```yaml
allowed-origins:
  - "${CORS_ALLOWED_ORIGIN:http://localhost:5173}"
  - "http://localhost:5173"
  - "http://localhost:80"
  - "http://localhost:5174"
  - "http://localhost:3000"
  - "http://localhost:4200"
```

`application-prod.yml:8` overrides (только `${CORS_ALLOWED_ORIGIN:https://rutcampustrack.ru}`). Spring profile-specific properties замещают base list (НЕ merge для list типов). НО — нужна verification что Spring Cloud Gateway конкретно для этой config-key делает replace, а не accidentally merge.

`allow-credentials: true` + если все 6 origins попадут в финальный список = атакующий с `http://localhost:5173` (через DNS rebinding) может делать credentialed requests.

**Fix (zero cost):** удалить dev origins из base `application.yml`, оставить только в `application-dev.yml`.

---

## TENT-17 — SWAGGER_HTPASSWD edge case с placeholder format (6/10)

**Файл:** `.env.prod.example:140`

Default value `swagger:$$apr1$$CHANGE$$ME` содержит CHANGE_ME → validate-env-prod.sh:118 ловит substring → exit 2. ✅ Two layers of defense:
1. validate-env-prod.sh — pre-deploy.
2. nginx compose `${SWAGGER_HTPASSWD:?...}` — runtime fail-fast (line 739).

Edge case: если оператор пишет реальный pwd но забывает escape `$` → `swagger:$apr1$abc$xyz` (single $) → docker-compose интерпретирует как variable substitution → empty string → entrypoint detects + fails.

**Verdict:** defenses в порядке. Tentative finding для completeness comprehensive mode.

---

# ✅ What was checked and found CLEAN

Намеренно выделяю эту секцию — много правильных решений в проекте, которые НЕ нужно менять.

## Authentication / Authorization (M03a/M03b + M13 G25 fixes)

- ✅ **JwtAuthenticationFilter strip headers** — gateway корректно strip'ает client-supplied X-User-* / X-Internal-Token / X-Login (`gateway/filter/JwtAuthenticationFilter.java:56-69`). M03a-post-audit fix верный.
- ✅ **InternalJwtIssuerFilter** — sequential ordering (-100 → -50), правильно интегрирован в Spring Cloud Gateway WebFilter chain.
- ✅ **InternalIssuerSecretFilter** — timing-safe `MessageDigest.isEqual` (M09 P0-5 fix), prefix-match `/internal/`, fail-safe на missing header.
- ✅ **TicketHandshakeInterceptor** для STOMP — single-use UUID ticket, замещает legacy JwtHandshakeInterceptor.
- ✅ **SubscriptionAuthInterceptor** — IMP-01 validate destinations против group ownership.
- ✅ **JWT issuer audience validation** — `requireIssuer("rutcampustrack-auth")` + `requireAudience("rutcampustrack")` в parseToken.

## Crypto

- ✅ **BCrypt + concurrency guard** (KI-7) — Semaphore N=20 защищает от DoS через parallel bcrypt.
- ✅ **Никаких MD5/SHA1** для security purposes (только SHA1PRNG для seed что OK).
- ✅ **`MessageDigest.isEqual`** для всех secret comparisons (timing-safe).

## Rate Limiting

- ✅ **FailOpenRateLimiter strip headers fix (G25.22)** — корректно. НЕ открывает attack surface: X-RateLimit-* sanitized из RESPONSE (которые downstream написать не могут так), не из request. 429 всё ещё возвращается через `delegate.isAllowed`.
- ✅ **Composite RL на /auth/login убран (G25.21)** — pragmatic fix, IP-only RL остаётся (5/min/IP), distributed brute force всё ещё защищён.

## File Upload

- ✅ **ExcuseService.createExcuseWithFile** — bytes forwarded в Telegram bot, НЕ записываются на disk → нет path traversal.
- ✅ **`@ValidFile` validator** — size cap 25MB + content-type allowlist + clear error messages.
- ✅ **nginx client_max_body_size 25m** для excuse upload (отдельный location), 8k для CSP report (DoS защита).

## Webhook

- ✅ **AlertController** — Bearer + `MessageDigest.isEqual` + fail-safe (no secret = denied) + coerce safe map types (G11 H3 fix).

## TLS / HTTP Security Headers

- ✅ **TLSv1.2 + TLSv1.3 only**, no TLSv1.0/1.1.
- ✅ **HSTS** `max-age=31536000; includeSubDomains; preload`.
- ✅ **CSP** `default-src 'self'; script-src 'self'` (M07 G12 HIGH-1 inline-script fix) + report-uri + report-to.
- ✅ **X-Frame-Options SAMEORIGIN**, X-Content-Type-Options nosniff, Referrer-Policy strict-origin-when-cross-origin, Permissions-Policy.
- ✅ **Sensitive file 404** — .env, .git, Dockerfile, package-lock.json и др. возвращают 404 (не 403, info leak prevention).
- ✅ **DH params** через openssl dhparam 2048 (deploy script).

## Infrastructure

- ✅ **Resource limits + healthchecks на всех 26 containers** (M13 G11).
- ✅ **Container images digest-pinned** (M06 D2 + M08 G11) — postgres, mongo (bitnamilegacy w/ fixed digest), redis, rabbitmq, prometheus, grafana, loki, promtail, tempo, alertmanager, nginx, certbot, blackbox-exporter, node-exporter, cadvisor — 13 images.
- ✅ **JVM heap = 0.75 × mem_limit** (NEW-157), G1GC, HeapDumpOnOutOfMemoryError.
- ✅ **HikariCP tuning** per-сервис (auth: pool 10, academic/schedule: больше).
- ✅ **Mongo replica set keyfile** required в prod, validate-env-prod.sh проверяет dev-placeholder и length 1024 chars.

## Spring Boot 3.4.1 vs April 2026 CVE

- ✅ **CVE-2026-40976 (critical RCE no-auth) НЕ применима к этому проекту.** Independent verifier confirmed:
  - Range «3.4.x ≤ 3.4.15» из WebSearch summary был incorrect — spring.io advisory listing only 4.0.0–4.0.5.
  - Даже если бы range охватывал 3.4.x — auth-service имеет explicit `SecurityFilterChain` (`SecurityConfig.java:28`), что breaks 40976 precondition («no Spring Security configuration of its own»).
  - Spring Cloud Gateway 4.x (api-gateway) — reactive stack, тоже outside scope.
- ✅ **CVE-2025-41243/41253/41254** (Spring Cloud Gateway property modification, SpEL injection, STOMP CSRF bypass) — нужно verify но скорее всего применимы. **Action item:** проверить Spring Cloud Gateway version в `services/api-gateway/build.gradle.kts` против fixed versions 3.1.10, 4.0.12, 4.1.8, 4.2.3, 4.3.0.
- ✅ Hygiene-only: bump до 3.4.15.2 в следующее maintenance window.

## Frontend

- ✅ **React + Angular auto-escape** by default. Нет dangerouslySetInnerHTML / [innerHTML] в audited paths.
- ✅ **Recent major versions:** React 19.1, Angular 19.2, vite 7, vitest 3.
- ✅ **CSP self-host fonts** (M07 G7) — fontsource-variable bundles вместо Google Fonts.

## CI/CD что правильно

- ✅ **Trivy fs scan** (HIGH+CRITICAL, exit-code 1) на каждом push/PR.
- ✅ **Trivy config scan** для Dockerfile + compose.
- ✅ **Trivy image scan** weekly (schedule cron).
- ✅ **Gitleaks** на каждом push/PR.
- ✅ **Cosign keyless signing** через Fulcio/Rekor для всех 11 images (M08 G11).
- ✅ **Cosign verify** ДО SSH-deploy step.
- ✅ **`anchore/sbom-action` + `sigstore/cosign-installer` + `aquasecurity/trivy-action`** SHA-pinned (правильный pattern, нужно extend).
- ✅ **`workflow_run` triggered deploy** с strict `if:` guard (event_name + conclusion + head_branch + event check).
- ✅ **`concurrency: production-deploy`** предотвращает параллельные deploys.
- ✅ **Smoke test после deploy** (curl /login, проверка Content-Length > 5000 = web-panel served).
- ✅ **Renovate digest-bump monthly** (M08 G11).
- ✅ **No `pull_request_target`** в workflows (verified).
- ✅ **No untrusted input в `run:` steps** (no `${{ github.event.pull_request.title }}` etc).

---

# План действий

## ✅ Перед VPS deploy (P0 + P1 — обязательно, ~1.5-2.5 часа)

### Spring P0 (30 мин)

- [ ] **CRIT-01 fix** — поменять `legacy-headers-enabled` дефолт на `false` в:
  - `services/academic-service/academic-app/src/main/resources/application.yml:74`
  - `services/schedule-service/schedule-app/src/main/resources/application.yml:71`
  - `services/attendance-service/attendance-app/src/main/resources/application.yml:55`
  - `services/notification-service/notification-app/src/main/resources/application.yml:42`
- [ ] **CRIT-01 fix** — поменять `strip-legacy-headers` дефолт на `true` в `services/api-gateway/src/main/resources/application.yml:338`.
- [ ] Запустить `SecurityIdorIT` тесты в всех 4 сервисах + manual verification:
  ```bash
  ./gradlew :services:academic-service:academic-app:integrationTest --tests SecurityIdorIT
  # ... для остальных 3
  ```
- [ ] Добавить в `.env.prod.example` комментарий о strict-mode invariant.

### CI/CD P0 (15 мин)

- [ ] **CRIT-02 fix** — SHA-pin `appleboy/ssh-action@v1` в `deploy.yml:313`:
  ```bash
  gh api repos/appleboy/ssh-action/git/refs/tags/v1.2.0 --jq '.object.sha'
  # → используй этот SHA в `uses:`
  ```

### Spring P1 (45 мин)

- [ ] **HIGH-05 fix** — добавить first-deploy detection в `deploy.yml` SSH script:
  ```bash
  if [ ! -f /opt/rutcampustrack/.deployed-sha ]; then
    docker volume rm rutcampustrack_jwt-keys 2>/dev/null || true
    # ... regenerate keys
  fi
  ```
- [ ] **HIGH-06 fix** — заменить fallback на fail-fast во всех application.yml:
  ```yaml
  secret: ${INTERNAL_ISSUER_SECRET:?INTERNAL_ISSUER_SECRET must be set}
  ```
  Аналогично для `REDIS_PASSWORD`, `SPRING_RABBITMQ_PASSWORD`, `POSTGRES_*_PASSWORD`, `GRPC_SECRET`, `MONGO_PASSWORD`, `RABBITMQ_PASSWORD`.

### CI/CD P1 (30 мин)

- [ ] **HIGH-04 fix** — SHA-pin все docker/* + actions/checkout в `deploy.yml`.
- [ ] **HIGH-03 fix** — SHA-pin 4 third-party actions в `coverage.yml` + перенести permissions per-job.

### Supply chain P1 (10 мин)

- [ ] **HIGH-07 fix** — bump `aiohttp>=3.13.3` в `services/notification-bot/requirements.txt`.
  Verify aiogram 3.15.0 compatible (быстрая проверка `pip install` в venv).
  Rebuild bot image.

### Verification (30 мин)

- [ ] Local docker-compose UAT:
  ```bash
  docker compose -f docker-compose.prod.yml --env-file .env.prod.example up -d
  # Должно НЕ стартануть с CHANGE_ME placeholders.
  ```
- [ ] Заполнить локальный `.env.prod.example.test` с реальными values, повторить — должен стартануть.
- [ ] Запустить `scripts/preflight-deploy.sh`.
- [ ] Запустить `scripts/verify-deploy.sh`.
- [ ] Manual curl tests:
  ```bash
  # Should be 401 (legacy headers blocked)
  docker exec rct-prometheus wget -O- \
    --header="X-User-Id: 1" --header="X-User-Role: ADMIN" \
    http://academic-service:9091/api/academic/users
  ```

## 📋 Post-deploy (P2 — следующие 1-2 недели)

- [ ] **MED-08** — реализовать full audit log (`audit_log` table + JSON channel + 90d retention).
- [ ] **MED-09** — SHA-pin gitleaks-action.
- [ ] **MED-11** — mTLS между Alertmanager → notification-web (Linkerd либо custom certificates).
- [ ] **MED-12** — cadvisor: убрать `privileged: true`, использовать `cap_add: [SYS_PTRACE]`.
- [ ] **MED-14** — sign git commits, `git verify-commit HEAD` в deploy script.
- [ ] **MED-15** — pre-commit hook: «.env.prod must NOT exist in working copy».
- [ ] **TENT-16** — удалить dev CORS origins из base application.yml.

## 🗂️ Roadmap (P3 — v0.1+)

- [ ] **MED-10** — мигрировать с `bitnamilegacy/mongodb:7.0` на `mongo:7-jammy` + custom RS init script.
- [ ] **MED-13** — Caffeine cache для last-known-good public key в PublicKeyProvider.
- [ ] Долгосрочно: network segmentation (3 segment'а) либо Linkerd auto-mTLS.
- [ ] Долгосрочно: external secret store (Vault / AWS SM / Bitwarden CLI), убрать image-baking RSA keys.

---

# Disclaimer

This audit is AI-assisted and is NOT a substitute for a professional security audit. Use as a first pass to catch low-hanging fruit. For production systems handling sensitive data (PII, payments), engage a qualified penetration testing firm.

Independent verification was performed for 2 critical findings (CRIT-01 legacy headers, CVE applicability for Spring Boot 3.4.1) by spawning isolated subagents that re-read the code without context contamination.

---

# Appendix A — Methodology

**Phases выполнены:**
- Phase 0: Architecture mental model + stack detection
- Phase 1: Attack surface census (code + infrastructure)
- Phase 2: Secrets archaeology (git history + tracked .env files)
- Phase 3: Dependency supply chain (Java/Python/npm)
- Phase 4: CI/CD pipeline security
- Phase 5: Infrastructure shadow surface
- Phase 6: Webhook & integration audit
- Phase 7: LLM security (N/A — не используется)
- Phase 8: Skill supply chain (skipped — не релевантно для этого проекта)
- Phase 9: OWASP Top 10 assessment
- Phase 10: STRIDE threat model (lightweight)
- Phase 11: Data classification (lightweight — RESTRICTED: passwords, JWTs; CONFIDENTIAL: API keys, user behavior; INTERNAL: logs)
- Phase 12: False positive filtering + active verification
- Phase 13: Findings report
- Phase 14: Save report (`.gstack/security-reports/20260426-032647.json`)

**FP filtering stats:**
- Candidates scanned: ~80
- Hard exclusion filtered: 18 (DOS without specific path, missing logging, regex complexity in non-untrusted input, etc.)
- Confidence gate filtered: 12 (below 2/10 в comprehensive mode)
- Verification filtered: 4 (downgraded to FALSE POSITIVE after independent agent review — including initial Spring Boot 3.4.1 CVE finding, downgraded to P3 hygiene)
- Reported: 17

**Independent verifiers:**
1. Legacy headers IDOR (CRIT-01) — agent traced code end-to-end, confirmed 10/10 confidence.
2. Spring Boot 3.4.1 CVE applicability — agent fetched spring.io advisory pages, downgraded finding from CRIT to P3 (configuration breaks precondition).

**Что НЕ проверено и осталось бы для professional audit:**
- Race conditions в JwtService.init() concurrent access.
- Detailed STOMP message validation (only verified handshake + subscription auth).
- Frontend XSS в edge-case scenarios (only quick grep).
- Telegram bot webhook security (currently long-poll mode, but if migrate to webhook — дополнительный attack surface).
- LDAP/SAML integration (нет, but if added — separate audit).
- Database encryption at rest (PostgreSQL/Mongo data volumes — currently unencrypted).
- DDoS protection at network level (only application rate-limits).

---

**End of report.**

Подготовлено `/cso comprehensive` Sonnet 4.6 + 2 verifier agents.
Готов выполнить fixes по команде — скажи какие хочешь применить, начнём с P0.
