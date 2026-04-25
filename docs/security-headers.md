# Security Headers — RutCampusTrack

Summary security headers, выставляемых nginx reverse-proxy на всех
HTTPS ответах (`nginx/conf.d/default.conf` — HTTPS server block).

## Обзор

| Header | Значение | Цель |
|--------|----------|------|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` | Принудительный HTTPS на 1 год (HSTS). Препятствует downgrade-атакам. |
| `X-Frame-Options` | `SAMEORIGIN` | Запрет на iframe-embedding с чужих доменов (clickjacking). |
| `X-Content-Type-Options` | `nosniff` | Запрет MIME-sniffing (защита от XSS через file upload). |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Referrer не отсылается на HTTP target'ы и cross-origin GET. |
| `Content-Security-Policy` | (см. ниже) | Белый список origin'ов для resources; XSS mitigation. |
| `Report-To` | (см. ниже) | Reporting API groups для modern browsers. |
| `Permissions-Policy` | `geolocation=(self), camera=(), microphone=()` | Geolocation только на самом origin, camera/mic запрещены. |

## Content-Security-Policy detail

```
default-src 'self';
script-src 'self';
style-src 'self' 'unsafe-inline';
img-src 'self' data:;
connect-src 'self' wss:;
font-src 'self';
report-uri /api/csp-report;
report-to csp-endpoint;
```

### Почему такие директивы

| Директива | Обоснование |
|-----------|-------------|
| `default-src 'self'` | По умолчанию всё с собственного origin. |
| `script-src 'self'` | M07 G12 HIGH-1: все inline scripts вынесены в `assets/js/`. Без `unsafe-inline` / `unsafe-eval`. |
| `style-src 'self' 'unsafe-inline'` | Angular Material + PWA Vite dev-mode генерируют inline styles. Отказ сломает UI. Mitigated CSP Level 2 nonce не применим к внешним библиотекам без refactor. |
| `img-src 'self' data:` | `data:` для small avatars / icon embed (QR-коды, inline-кодированные base64 PNG). |
| `connect-src 'self' wss:` | REST + STOMP WebSocket (nginx `/api/ws/` location). |
| `font-src 'self'` | Шрифты самохостятся (M07 G4 self-host для privacy). |
| `report-uri /api/csp-report` | Legacy reporting (Firefox, Safari, Chrome < 97). M13 G16. |
| `report-to csp-endpoint` | Modern Reporting API (Chrome 97+). Require сопутствующий `Report-To` header с group definition. |

### Исключения (per-location CSP override)

`/grafana/` — `Content-Security-Policy: ""` (empty, т.е. disabled).
Grafana iframe'ы требуют inline scripts + eval для query editors. Без
disable'а — панели сломаются. Access limited basic-auth, low exposure.

## Report-To header (Reporting API)

```json
{
  "group": "csp-endpoint",
  "max_age": 10886400,
  "endpoints": [
    {"url": "/api/csp-report"}
  ]
}
```

- `max_age`: 126 дней (10886400 sec) — браузер кэширует endpoint config.
- Не указано `include_subdomains` — reports только с основного origin.

## CSP report endpoint

`POST /api/csp-report` — notification-web service (M13 G16).

**Роутинг:**
- Browser → nginx `:443/api/csp-report`.
- nginx → gateway `/api/csp-report` (StripPrefix=1).
- gateway → notification-web `/csp-report`.

**Authentication:** публичный endpoint. Browsers не носят Internal JWT.
`/csp-report` добавлен в `NotificationUserContextFilter.isExcludedPath()`
и `JwtAuthenticationFilter.PUBLIC_PATHS`.

**Rate-limit:** gateway `RequestRateLimiter` 1 tok/sec + burst 60 per-IP.
Flood защита — malicious сайт с iframe может спамить browser'а CSP-нарушениями.

**Content-Type handling:**
| MIME | Формат | Клиент |
|------|--------|--------|
| `application/csp-report` | `{"csp-report": {"violated-directive": ..., "blocked-uri": ...}}` | Firefox, Safari, Chrome < 97 |
| `application/reports+json` | `[{"type": "csp-violation", "body": {"effectiveDirective": ..., "blockedURL": ...}}, ...]` | Chrome 97+ (Reporting API) |
| `application/json` | либо top-level kebab-case, либо с `csp-report` wrapper'ом | Прокси, переписавшие Content-Type |

**Observability:**
- Counter `security_csp_violations_total{directive, blocked_uri_host}` —
  Prometheus через `/actuator/prometheus`.
- Tags low-cardinality:
  - `directive` — только имя (без source list).
  - `blocked_uri_host` — только host (без path / query), lowercase.
  - Special values (`inline`, `eval`, `data:...`) — обрезаются до 32 chars.
- Structured log (WARN level) — visible в Loki / Grafana.

**Response:** всегда `204 No Content` (или `415` на совсем неподдерживаемый
Content-Type). Браузер не ждёт ответа, но 2xx избегает DevTools warnings
«CSP report failed to deliver».

## Мониторинг CSP violations в production

### Grafana query

```
sum by (directive, blocked_uri_host) (
  rate(security_csp_violations_total[5m])
)
```

### Alert candidates (deferred в future milestone)

- **CspViolationSpike**: `rate(security_csp_violations_total[5m]) > 10`
  — сломали CSP в новом deploy, fix ASAP.
- **CspViolationsOnSensitiveDirective**: filter по
  `directive="script-src"` — вероятный attempted XSS.

Пока (v0.0.0 GA) — только observation через Loki structured logs + manual
Grafana query. Alerts добавляются в v0.1 по реальному pattern'у violations.

## Что НЕ включено (deferred)

- **SRI (Subresource Integrity):** hashes для всех `<script src>` и
  `<link href>`. Сейчас не нужно т.к. `script-src 'self'` + self-host
  шрифты — нет третьих party scripts.
- **COOP / COEP:** Cross-Origin-Opener-Policy + Cross-Origin-Embedder-Policy.
  Нужно если включим `SharedArrayBuffer` / Spectre-mitigations. Сейчас
  не требуется для текущего функционала.
- **nonce-based CSP:** замена `style-src 'unsafe-inline'` на
  `style-src 'nonce-<random>'`. Требует refactor'а Angular/PWA builds
  для injection nonce в compiled CSS. Deferred в v0.1 после CSP-audit
  в production.

## Runbook: CSP violation triage

1. Получен alert / замечен spike в Grafana → открыть Loki query:
   `{service="notification-web"} |= "CSP violation"`.
2. Группировать по `directive` + `blockedUri` — определить pattern.
3. Классификация:
   - **Legitimate resource** (новый CDN, добавленный разработчиком) →
     обновить CSP в `nginx/conf.d/default.conf` → deploy.
   - **Malicious injection** (unknown domain, spam) → XSS investigation,
     проверить user inputs, применить input sanitization в backend.
   - **Browser extension** (Honey, AdBlock) → false positive, ignore.

## История изменений

| Дата | Что | Ref |
|------|-----|-----|
| 2026-04-22 | CSP self-host шрифтов + inline scripts вынесены в assets/js/ | M07 G4 + G12 HIGH-1 |
| 2026-04-25 | Добавлен `report-uri` + `report-to`, `/api/csp-report` endpoint + counter | M13 G16 |
