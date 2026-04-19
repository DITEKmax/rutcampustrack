# Auth flow (cookie + WebSocket ticket)

> **Статус:** M03b in progress. Этот документ дополняется по мере
> закрытия групп CHECKLIST (cookie → ws-ticket → logout lifecycle).
> Окончательный runbook — после Группы 12.

## Обзор

Аутентификация v0.0.0-alpha.4+ использует двухтокенную схему с
HttpOnly cookie для refresh и in-memory хранения access token в клиенте.
WebSocket handshake использует отдельный short-lived ticket вместо
внешнего JWT (защита от утечки токена через логи/Referer).

```
┌─────────────┐       login/otp       ┌─────────────────┐
│   Client    │ ─────────────────────▶│  auth-service   │
│ (PWA/panel) │                       │                 │
│             │ ◀─────────────────────│   /auth/login   │
│             │  access in body       └─────────────────┘
│             │  + Set-Cookie rct_refresh (HttpOnly Secure Strict)
│             │
│             │    POST /auth/refresh  ┌─────────────────┐
│             │ ─ (cookie auto) ─────▶│  auth-service   │
│             │                       │                 │
│             │ ◀─────────────────────│  /auth/refresh  │
│             │  new access + cookie  └─────────────────┘
│             │
│             │   POST /auth/ws-ticket (Internal JWT)  [M03b Группа 3]
│             │ ─────────────────────▶ auth-service
│             │ ◀── {ticket, expiresAt: 30s}
│             │
│             │   wss://...?ticket=<uuid>    [notification-web]
│             │ ─────────────────────▶ TicketHandshakeInterceptor
│             │                       → GET+DEL Redis → STOMP session
└─────────────┘
```

## Cookie контракт

| Атрибут | Значение | Причина |
|---------|----------|---------|
| Name | `rct_refresh` | |
| HttpOnly | true | XSS-protection — JS не читает |
| Secure | true | только HTTPS |
| SameSite | `Strict` | cross-origin CSRF исключён (same-origin app) |
| Path | `/api/auth` | cookie видна login/refresh/logout, и только |
| Max-Age | `jwt.refresh-token-expiration` (604800 = 7 дней) | совпадает с TTL токена |

Reasoning: OWNER-ANSWERS.md `02-Q-frontend-security` (2026-04-18) +
M03b DECISIONS.md (2026-04-20).

## Endpoints (auth-service)

### `POST /auth/login` — login + Set-Cookie

**Request:** `LoginRequest {login, password}`.
**Response 200:** `TokenResponse {accessToken, refreshToken, expiresIn}`
+ `Set-Cookie: rct_refresh=<token>; HttpOnly; Secure; SameSite=Strict;
Path=/api/auth; Max-Age=604800`.

### `POST /auth/refresh` — cookie-based rotation

**Request:** без body. Требует cookie `rct_refresh`.
**Response 200:** новый `TokenResponse` + rotated `Set-Cookie`.
**Response 401:** cookie отсутствует/просрочена/отозвана.

### `POST /auth/refresh-body` — **DEPRECATED** (M04/M05 removal)

Legacy endpoint для TMA/Mini App клиентов, которые не могут использовать
HttpOnly cookie. Возвращает заголовки `Deprecation: true` + `Sunset:
Mon, 01 Jun 2026 00:00:00 GMT`. Планируется удаление в M04 или M05.

### `POST /auth/logout` — revoke + clear-cookie

Revoke refresh token (из cookie или body для legacy) + ставит
`Set-Cookie: rct_refresh=; Max-Age=0; Path=/api/auth` для очистки.

### `POST /auth/ws-ticket` — **TODO в Группе 3 M03b**

Защищённый Internal JWT endpoint (shared-security из M03a). Выпускает
short-lived (30 sec, single-use) ticket для WebSocket handshake.

## Клиентская схема

### Login

```ts
// axios / fetch — credentials: 'include' обязательно
const resp = await apiClient.post('/auth/login', {login, password});
setAccessToken(resp.data.accessToken);   // в React state / Angular service
// refresh cookie — браузер сохранил автоматически, JS не видит.
```

### Refresh

```ts
// На 401 или за N секунд до истечения:
const resp = await apiClient.post('/auth/refresh', null, {
    withCredentials: true    // Angular HttpClient
    // credentials: 'include' для fetch
});
setAccessToken(resp.data.accessToken);
```

### Logout

```ts
await apiClient.post('/auth/logout', null, { withCredentials: true });
clearAllClientState();   // M03b Группа 8 — очистка SW cache, push, stores
```

## Breaking change для frontend (M03b)

- `localStorage['rct.auth.v1']` — **полностью удаляется**. Миграционный
  helper на старте: если видит ключ → `localStorage.removeItem` +
  redirect на login.
- `/auth/refresh` теперь игнорирует body. Старый клиент, шлющий
  body — получит 401 (cookie нет, body не читается).
- `SockJS('/api/ws?token=...')` → `new WebSocket('/api/ws?ticket=<uuid>')`
  через pre-connect `POST /auth/ws-ticket`.

## Security properties

| Угроза | Защита |
|--------|--------|
| XSS читает refresh token | HttpOnly cookie — JS не имеет доступа |
| MITM перехватывает cookie | Secure-флаг запрещает HTTP, все traffic за TLS |
| Cross-origin CSRF на refresh | SameSite=Strict + same-origin deployment |
| Refresh token в nginx log | cookie в header `Cookie:`, не в URL |
| Access token в nginx log | в `Authorization: Bearer`, nginx его не логирует |
| Access token в WebSocket URL | заменяется single-use ticket (Группа 3) |
| Logout не чистит device state | `clearAllClientState()` (Группа 8) |

## См. также

- `docs/report-before-v0.0.0/OWNER-ANSWERS.md` → `02-Q-frontend-security`
- `docs/milestones/M03b-jwt-cookie-ws-ticket/DECISIONS.md` (2026-04-20)
- `docs/internal-jwt-spec.md` — Internal JWT (M03a, защищает ws-ticket)
