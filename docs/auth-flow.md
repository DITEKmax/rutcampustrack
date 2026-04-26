# Auth flow (cookie + WebSocket ticket)

> **Статус:** M03b завершён (v0.0.0-alpha.4). Полный runbook cookie + ws-ticket
> + logout lifecycle + KI-3/6/7/8 hot-patches. Пересматривается на стыке
> с M04 (observability) и M07 (frontend hardening).

## Обзор

Аутентификация v0.0.0-alpha.4 использует двухтокенную схему с HttpOnly
cookie для refresh-token и in-memory хранения access-token в клиенте.
WebSocket handshake использует отдельный short-lived single-use ticket
вместо внешнего JWT в query (защита от утечки токена через логи/Referer).

```
┌─────────────┐       login / otp / tma       ┌─────────────────┐
│   Client    │ ─────────────────────────────▶│  auth-service   │
│ (PWA/panel) │                               │                 │
│             │ ◀─────────────────────────────│   /auth/login   │
│             │  {access, refresh, expiresIn} │                 │
│             │  + Set-Cookie rct_refresh     └─────────────────┘
│             │      (HttpOnly Secure Strict Path=/api/auth)
│             │
│             │    POST /auth/refresh          ┌─────────────────┐
│             │ ─ (cookie auto-sent) ────────▶│  auth-service   │
│             │                               │                 │
│             │ ◀─────────────────────────────│  /auth/refresh  │
│             │  new access + rotated cookie  └─────────────────┘
│             │
│             │   POST /auth/ws-ticket         ┌─────────────────┐
│             │ ── Authorization: Bearer ────▶│  auth-service   │
│             │                               │  WsTicketCtrl   │
│             │ ◀── {ticket:UUID, expiresAt}  └─────────────────┘
│             │                        │
│             │                        │ SETEX ws_ticket:<uuid> TTL=30s
│             │                        │ SADD ws_ticket_user:<uid> <uuid>  (Lua)
│             │                        ▼
│             │                      Redis (payload: uid|role|grp|hd|exp)
│             │
│             │   wss://.../ws?ticket=<uuid>  ┌────────────────────────┐
│             │ ─────────────────────────────▶│  notification-web      │
│             │                               │  TicketHandshakeIntr   │
│             │                               │      │ REST call       │
│             │                               │      ▼                 │
│             │                               │  /internal/consume-    │
│             │                               │  ws-ticket             │
│             │                               │  (X-Internal-Issuer-   │
│             │                               │   Secret header)       │
│             │                               │      │                 │
│             │                               │      ▼                 │
│             │                               │  EVAL Lua: GET + DEL   │
│             │                               │  + SREM                │
│             │                               └────────────────────────┘
│             │                                    │
│             │                                    ▼
│             │                               {uid, role, groupId, isHeadman}
│             │                                    → STOMP session attrs
│             │
│             │   POST /auth/logout            ┌─────────────────┐
│             │ ─ (cookie + Bearer opt) ─────▶│  auth-service   │
│             │                               │  AuthController │
│             │                               │  + wsTicketSvc  │
│             │ ◀─────────────────────────────│    .invalidate  │
│             │  204 + Set-Cookie Max-Age=0   │    AllFor(uid)  │
│             │                               └─────────────────┘
└─────────────┘
```

## Cookie контракт

| Атрибут | Значение | Причина |
|---------|----------|---------|
| Name | `rct_refresh` | Prefix `rct_` — не-namespaced, чтобы dev-tools легко grep'ать |
| HttpOnly | `true` | XSS-protection — JS не читает |
| Secure | `true` | только HTTPS (браузер не шлёт на HTTP) |
| SameSite | `Strict` | Cross-origin CSRF исключён (same-origin app `ruttrack.site`) |
| Path | `/api/auth` | Cookie видна на login/refresh/logout/ws-ticket — и только |
| Max-Age | `jwt.refresh-token-expiration` (604800 = 7 дней) | Совпадает с TTL refresh-JWT |

Reasoning: `OWNER-ANSWERS.md 02-Q-frontend-security` (2026-04-18) +
`docs/milestones/M03b-jwt-cookie-ws-ticket/DECISIONS.md` (2026-04-20).

Cookie фабрика: `services/auth-service/.../security/AuthCookies.java` —
единый `baseBuilder()` для issue и clear → attributes идентичны →
браузер корректно overwrite'ает.

## Endpoints (auth-service)

### `POST /auth/login` — login + Set-Cookie
**Permit-all** (Gateway + SecurityConfig).
Rate-limited на Gateway: 5 req/min per IP + 10 req/min per `(ip, login)`
(composite — см. KI-8).

**Request:** `LoginRequest {login, password}`.
**Response 200:** `TokenResponse {accessToken, refreshToken, expiresIn}` +
`Set-Cookie: rct_refresh=<token>; HttpOnly; Secure; SameSite=Strict;
Path=/api/auth; Max-Age=604800`.
**Response 401:** `InvalidCredentialsException`.
**Response 429:** `OtpRateLimitException` — composite `(ip, login)` >=5
failures в 60 мин, или `BcryptConcurrencyGuard` (N=20 permits) полон.

### `POST /auth/refresh` — cookie-based rotation
**Permit-all.** Rate-limited: 30 req/min per userId.

**Request:** без body. Требует cookie `rct_refresh`.
**Response 200:** новый `TokenResponse` + rotated `Set-Cookie`.
**Response 401:** cookie отсутствует / просрочена / revoked в Redis.

### `POST /auth/logout` — revoke + clear-cookie + invalidate ws-tickets
**Permit-all** (cookie-only logout работает без access-JWT).

**Request:** cookie `rct_refresh` ИЛИ body `{refreshToken}` (legacy TMA).
Опциональный `Authorization: Bearer <access>` — если есть, извлекается
`userId` для invalidate всех ws-ticket'ов пользователя.
**Response 204:** + `Set-Cookie: rct_refresh=; Max-Age=0; ...` для clear.
**Audit log:** `auth.logout userId=<n> revoked_tickets=<n> cookie_logout=<bool>`.

### `POST /auth/ws-ticket` — выдача single-use WebSocket ticket
**Требует Authentication** (Bearer access-JWT). Возвращает:
```json
{"ticket": "<uuid>", "expiresAt": "2026-04-20T12:30:45Z"}
```
TTL: 30 sec. Storage: `ws_ticket:<uuid>` (payload, TTL 30s) + user-set
`ws_ticket_user:<uid>` для batch-invalidate (TTL 60s, Lua-atomic SADD+EXPIRE).

### `POST /internal/consume-ws-ticket` — consume (для notification-web)
Защищён `X-Internal-Issuer-Secret` header (timing-safe check через
`MessageDigest.isEqual`). Выполняет Lua-атомарный GET + DEL + SREM.

**Request:** `{ticket: "<uuid>"}`.
**Response 200:** `{userId, role, groupId, isHeadman, expiresAt}`.
**Response 404:** ticket не найден / уже consume'нут / expired.

## Клиентская схема

### Login
```ts
// axios — apiClient (уже withCredentials для /auth/*)
const resp = await apiClient.post('/auth/login', {login, password});
setAccessToken(resp.data.accessToken);   // in-memory React state / Angular service
// refresh cookie — браузер сохранил автоматически, JS не видит.
```

### Refresh (automated через interceptor)
```ts
// Вызывается из axios/Angular interceptor на 401, bootstrap, или N секунд до expiry
const resp = await apiClient.post('/auth/refresh', null, {withCredentials: true});
setAccessToken(resp.data.accessToken);
```

### WebSocket handshake
```ts
// Для PWA:   features/auth/wsTicket.ts
// Для panel: core/auth/ws-ticket.ts
async function buildWsUrl(base: string): Promise<string> {
  const {data} = await apiClient.post('/auth/ws-ticket', null);
  // ?ticket=<uuid> вместо ?token=<JWT>
  return `${base}?ticket=${encodeURIComponent(data.ticket)}`;
}

const socket = new WebSocket(await buildWsUrl(WS_BASE));
```

### Logout
```ts
// 1. Backend revoke
await apiClient.post('/auth/logout', null, {withCredentials: true});
// 2. Local state clear (local/session storage, SW caches, push DELETE)
//    accessToken передаётся чтобы backend push-unsubscribe прошёл Gateway
await clearAllClientState(accessToken);
```

## Ws-ticket payload format

Pipe-separated (не JSON — упрощает Lua SREM без cjson):
```
<userId>|<role>|<groupId>|<isHeadman>|<expiresEpoch>
```

Пример: `42|STUDENT|7|true|1735689645`.
`groupId = 0` если null в access-JWT.

## Internal JWT token-exchange (M03a, используется тут)

Auth-service issues Internal JWT (RS256, 300s TTL) для downstream-сервисов
через `POST /internal/issue-internal-jwt` — вызывается Gateway'ем.
Auth-service сам — issuer Internal JWT, не consumer.

`/auth/ws-ticket` защищён access-JWT (RS256), не Internal JWT —
Gateway не strip'ает `Authorization` header на этом endpoint.

## Breaking changes для frontend (M03b)

- `localStorage['rct.auth.v1']` — **полностью удаляется**. Миграционный
  helper на mount удаляет legacy-blob.
- `POST /auth/refresh` теперь игнорирует body. Старый клиент, шлющий
  body — получит 401 (cookie нет, body не читается).
- `WebSocket(?token=<JWT>)` → `WebSocket(?ticket=<uuid>)` через
  pre-connect `POST /auth/ws-ticket`.
- `AuthProvider.setTokens()` → `.setAccessToken()` (refresh в cookie).

## Security properties

| Угроза | Защита | Источник |
|--------|--------|----------|
| XSS читает refresh token | HttpOnly cookie — JS не имеет доступа | C0-7 |
| MITM перехватывает cookie | Secure-флаг запрещает HTTP | C0-7 |
| Cross-origin CSRF на refresh | SameSite=Strict + same-origin `ruttrack.site` | C0-7 DECISIONS |
| Refresh token в nginx log | в header `Cookie:`, не в URL | C0-7 |
| Access token в nginx log | в `Authorization: Bearer`, nginx не логирует | — |
| Access token в WebSocket URL | заменён single-use ticket | M03b Группа 4 |
| Ticket replay | Lua-atomic GET+DEL+SREM, single-use TTL 30s | M03b Группа 3 |
| Logout не чистит device state | `clearAllClientState()` + `invalidateAllFor` | M03b Группа 8 |
| Concurrent bcrypt DoS | Semaphore N=20 fair + fail-fast 429 | M03b Группа 10 (KI-7) |
| Login rate-limit TTL race | Lua `INCR+EXPIRE` atomic | M03b Группа 9 (KI-6) |
| Composite rate-limit bypass | `LoginBodyExtractionFilter` ставит X-Login | M03b Группа 9 (KI-8) |
| Clock-drift → stale Internal JWT | `isAboutToExpire` retry при <5s до exp | M03b Группа 9 (KI-3) |

## Rate-limits (Gateway)

| Path | Ключ | Limit |
|------|-----|-------|
| `/api/auth/otp/request` | IP | 1 req/min |
| `/api/auth/otp/verify-by-code` | IP | 5 req/min |
| `/api/auth/login` | IP (первый фильтр) | 5 req/min |
| `/api/auth/login` | (IP, login) композитный | 10 req/min |
| `/api/auth/refresh` | userId | 30 req/min |
| `/api/attendance/check-in` | userId | 10 req/min |
| `/api/academic/**`, `/api/schedule/**`, `/api/attendance/**` | IP | 600 req/min |

## См. также

- `docs/report-before-v0.0.0/OWNER-ANSWERS.md` → `02-Q-frontend-security`
- `docs/milestones/M03b-jwt-cookie-ws-ticket/DECISIONS.md` (2026-04-20)
- `docs/milestones/M03b-jwt-cookie-ws-ticket/NOTES.md` — surprises + hand-off
- `docs/api/internal-jwt-spec.md` — Internal JWT spec (M03a)
- `docs/api/api-rate-limits.md` — rate-limit spec + operational playbook
