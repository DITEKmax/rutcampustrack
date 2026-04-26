# nginx Configuration Runbook (NEW-152)

Конфигурация reverse-proxy nginx для `https://ruttrack.site`. Источник
правды — `nginx/nginx.conf` + `nginx/conf.d/default.conf`. Этот документ
описывает инварианты, которые нужно удерживать при правках.

## Limits

### `client_max_body_size`

| Scope | Value | Обоснование |
|-------|-------|-------------|
| `http { }` (global) | **2m** | DoS защита. Большинство REST endpoint'ов — JSON sub-kb, ни одно нормальное поле не требует 2MB. |
| `location /api/attendance/excuses/with-file` | **25m** | Multipart excuse-тикет (студент прикладывает справку). Backend Spring Boot enforces 20MB (`spring.servlet.multipart.max-file-size=20MB`), 25m nginx = 20MB payload + multipart boundary overhead. |

**Прецеденция nginx:** более длинный prefix-match побеждает generic.
Значит `/api/attendance/excuses/with-file` matchit'ся раньше, чем
`/api/`, и наследует 25m вместо 2m. Остальные `/api/*` — 2m.

**Rationale против 12m global (pre-M07 G11):**
- 12m — legacy default, ставили «чтобы ничего не падало»;
- любой malicious POST на `/api/academic/users` (JSON ~200 байт) мог
  заливать 12MB body → OOM на Jackson parsing;
- 2m для JSON более чем достаточно (самый большой bulk —
  `POST /attendance/marks/batch` на 100 студентов ≈ 5KB).

**Deferred:** аватары — сейчас JSON PATCH с `avatarId` (preset id из
`preset-avatars.ts`). Если в v0.1 появится upload file-based аватар,
добавить `location = /api/academic/users/me/avatar { client_max_body_size 5m }`.

### Security headers

HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, CSP,
Permissions-Policy — все в `default.conf` server 443 block. **НЕ
перекрывать** `proxy_hide_header` на upstream — inner web-panel nginx
управляет cache-control для SPA bundles.

### CSP

`default-src 'self'` — strict. Self-host всех CDN ресурсов landing'а
сделан в M07 G1. Новые external URL'ы требуют review (см. `docs/meta/contributing.md`).

## Upstream routing

| Path | Upstream | Purpose |
|------|----------|---------|
| `= /` | 301 → `/login` | INFRA-v9-01 |
| `/login`, `/admin/*`, `/teacher/*`, `/student/*`, `/headman/*` | `rct-web-panel-nginx` | web-panel SPA (Angular) |
| `/app/*` | `rct-pwa-nginx` | PWA (React + Vite) |
| `/presentation/*` | `rct-landing-nginx` | Landing (static HTML) |
| `/mini-app/*` | `rct-mini-app-nginx` | Telegram Mini App |
| `/api/ws/` | `rct-api-gateway:8080` | WebSocket/STOMP (upgrade headers) |
| `/api/*` (excluding `/api/ws/` и `/api/attendance/excuses/with-file`) | `rct-api-gateway:8080` | REST (2m body limit) |
| `/api/attendance/excuses/with-file` | `rct-api-gateway:8080` | Multipart upload (25m body limit) |
| `/swagger-ui*`, `/v3/api-docs`, `/openapi/`, `/grafana/` | `rct-api-gateway:8080` / `rct-grafana:3000` | Auth via basic-auth (`.htpasswd`) |

## Reload checklist

При изменении `nginx.conf` / `conf.d/*.conf`:

1. **Syntax check** локально: `docker compose exec nginx nginx -t`.
2. **Reload без рестарта:** `docker compose exec nginx nginx -s reload`.
   Graceful — existing connections продолжают работать, новые идут на
   новый config. Runtime ≤ 1s.
3. **Smoke test (vps):** `curl -I https://ruttrack.site/` → 301
   `Location: /login`; `curl -sSf -o /dev/null -w "%{http_code}"
   https://ruttrack.site/api/health` → 200.
4. **CSP test:** открыть `/presentation/` в DevTools → Console, нет
   CSP violations.

## Troubleshooting

### 413 Request Entity Too Large

Обычно — body > `client_max_body_size` для данной location. Проверить:
- Какой endpoint: nginx log `access.log` покажет `$request`.
- Какой лимит применяется: nginx matchit наиболее специфичный prefix.
  Temporary debug: `echo $client_max_body_size` не работает в runtime;
  поставить `add_header X-Body-Limit "2m" always;` в нужном location.

### WebSocket не подключается (101 → timeout)

- Проверить `proxy_read_timeout 86400s` в `/api/ws/` block.
- `Connection "upgrade"` + `Upgrade $http_upgrade` headers.
- Backend notification-web / api-gateway `@EnableWebSocket` конфиг.

### Сертификат истёк

- Certbot renew — cron в `docker-compose.prod.yml` (M06 G1 HEALTHCHECK
  + deploy runbook).
- `http-only.conf` — fallback для re-issuance (переименовать из
  `http-only.conf.bak` при проблемах с HTTPS).

## History

- M06 G1: HEALTHCHECK в Dockerfile + digest-pin observability.
- M07 G11: `client_max_body_size` per-location refactor. Global 12m → 2m,
  excuse with-file 25m per-location. NEW-152 runbook (этот файл).

---

_Для более глубокого контекста инфраструктуры см. `docs/operations/deploy/ci-cd.md` и
`docs/operations/deploy/container-trust.md`._
