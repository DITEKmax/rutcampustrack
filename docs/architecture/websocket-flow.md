# WebSocket / STOMP flow — RutCampusTrack

Документ описывает end-to-end жизненный цикл WebSocket-соединения от
браузера через nginx → API Gateway → notification-web, включая
ticket-based handshake (M03b), heartbeat (M13 G18) и reconnect стратегию.

История изменений в конце файла.

## Обзор архитектуры

```
┌─────────────┐      ┌────────────┐      ┌──────────────┐      ┌──────────────────┐
│  Browser    │ HTTP │   nginx    │ HTTP │ API Gateway  │ HTTP │ notification-web │
│ (PWA или    │ ───► │ (reverse-  │ ───► │ (Spring      │ ───► │ (STOMP broker,   │
│  web-panel) │      │  proxy)    │      │  Cloud GW)   │      │  /topic/*)       │
└─────────────┘      └────────────┘      └──────────────┘      └──────────────────┘
                          │                                           │
                          │  proxy_buffering off                      │
                          │  proxy_http_version 1.1                   │
                          │  Upgrade + Connection headers             │
                          │  proxy_read_timeout 86400s                │
                          │                                           │
                          │  HTTP Upgrade → WebSocket frames          │
                          │  ◄────────── STOMP heartbeat 10s/10s ────►│
```

**Endpoint:** `wss://ruttrack.site/api/ws/?ticket=<uuid>` (SockJS fallback
поддерживается через `/api/ws/<server-id>/<session-id>/...`).

**Broker destinations:**

- `/topic/group/{groupId}` — общие события группы (lesson.started, attendance.marked)
- `/topic/group/{groupId}/headman` — события только для старосты (excuse.created, late_checkin.created)
- `/topic/user/{userId}` — пользовательские нотификации (NotificationHistoryEvent)

## Lifecycle: handshake

### Шаг 1 — клиент получает ticket

Frontend (PWA / web-panel) **не отправляет JWT в URL** (security: URL в
referer / proxy logs). Вместо этого:

```
POST /api/auth/ws-ticket
Cookie: rct_access=<JWT>
→ 200 OK { "ticket": "<uuid>", "expires_in_seconds": 30 }
```

`WsTicketService` в auth-service генерирует UUID, кладёт в Redis с TTL
30 секунд, ключ `ws_ticket:<uuid>:<userId>`. Single-use.

### Шаг 2 — STOMP CONNECT с ticket в query

```js
const client = new Client({
  webSocketFactory: async () => {
    const ticket = await fetchWsTicket(); // GET /api/auth/ws-ticket
    return new SockJS(`/api/ws/?ticket=${ticket}`);
  },
  reconnectDelay: 1000,        // exponential backoff handled by stompjs internally
  heartbeatIncoming: 10_000,   // expect server frame every 10s
  heartbeatOutgoing: 10_000,   // send client frame every 10s (default stompjs)
});
```

`webSocketFactory` — async — каждый reconnect фетчит **новый** ticket.
Это ключевое: ticket'ы single-use, reconnect без ре-фетча → handshake fail.

### Шаг 3 — handshake interceptor

`TicketHandshakeInterceptor` в notification-web (`WebSocketConfig`):

1. Достаёт `?ticket=<uuid>` из query.
2. Вызывает `WsTicketClient.consume(ticket)` → REST к auth-service
   `POST /internal/consume-ws-ticket` (Internal JWT signed).
3. auth-service атомарно `GETDEL`-ает Redis-ключ → возвращает `userId`,
   `groups`, `is_headman`. Если ключ отсутствовал (expired / уже
   потреблён / fake) → 404.
4. На успех — userId+роль кладутся в `WebSocketSessionAttributes`,
   handshake продолжается. Иначе 401 — клиент видит ошибку и пробует
   снова через `webSocketFactory`.

### Шаг 4 — STOMP SUBSCRIBE

Клиент шлёт `SUBSCRIBE destination:/topic/group/123`.
`SubscriptionAuthInterceptor` валидирует:

1. Если destination — `/topic/group/{X}` → user должен иметь groupId X
   в списке групп.
2. Если `/topic/group/{X}/headman` → user должен быть headman группы X.
3. Если `/topic/user/{X}` → X == userId сессии (анти-IDOR).

Иначе — STOMP ERROR frame, подписка отклонена.

## Heartbeat (M13 G18)

**Раньше:** comment в `WebSocketConfig` утверждал "Default Spring heartbeat
(10s server, 10s client) — no custom tuning needed". Это **неверно**:
по умолчанию `enableSimpleBroker("/topic")` без явного `setHeartbeatValue`
+ `setTaskScheduler` → heartbeat **0/0 (off)**. Idle соединения за nginx
могут зависать как half-open (TCP keepalive не помогает на
application-layer задержках).

**После M13 G18:**

```java
config.enableSimpleBroker("/topic")
        .setHeartbeatValue(new long[]{10_000L, 10_000L})
        .setTaskScheduler(stompHeartbeatScheduler());
```

- **Server → client:** STOMP heartbeat frame каждые 10 сек.
- **Client → server:** клиент шлёт frame каждые 10 сек.
- **TaskScheduler:** dedicated `ThreadPoolTaskScheduler` (pool size 1,
  daemon threads, prefix `stomp-heartbeat-`). Без него `setHeartbeatValue`
  silently игнорируется Spring'ом.

**Frontend:** `@stomp/stompjs` по умолчанию `heartbeatIncoming = 10000`,
`heartbeatOutgoing = 10000` — симметрично.

**Мониторинг:** SockJS reconnect видим через DevTools → Network → WS.
В Spring Boot logs — ничего, heartbeat работает silent.

## nginx config (`nginx/conf.d/default.conf`)

```nginx
location /api/ws/ {
    proxy_pass http://rct-api-gateway:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 86400s;   # idle WS до 24 часов (heartbeat поддерживает)
    proxy_buffering off;          # M13 G18: фреймы клиенту немедленно
}
```

**Почему `proxy_buffering off`:** nginx по умолчанию буферизует response
chunks для оптимизации throughput. Для STOMP (особенно heartbeat
1-byte фрейма `\n`) это даёт задержку — buffer не наполнится и фрейм не
дойдёт. С `proxy_buffering off` каждый фрейм проксируется немедленно.

**Почему `proxy_read_timeout 86400s`:** idle WebSocket'ы в production
держатся часами (студент открыл вкладку и забыл). Heartbeat 10s/10s
гарантирует что соединение не считается idle на TCP-уровне — но если
heartbeat task scheduler упадёт, nginx должен дать достаточно времени
до явного close. 86400s = 24 часа — компромисс между «долго держим»
и «не даём навсегда зависшим соединениям копиться».

## Reconnect стратегия

**Frontend (PWA + web-panel):**

| Параметр | Значение | Источник |
|----------|----------|----------|
| `reconnectDelay` | 1000ms (linear, не exponential) | `useStompCheckin.ts`, `notification-center.service.ts` |
| Ticket re-fetch | каждый reconnect | `wsTicketFactory` async closure |
| Subscribe replay | автоматически (stompjs внутри) | — |

Тесты (regression guards):

- `frontends/pwa/src/features/checkin/__tests__/useStompCheckin.test.ts`
  — passes finite reconnectDelay 1-5000ms; ticket re-fetched на reconnect.
- `frontends/web-panel/src/app/core/notifications/notification-center.service.spec.ts`
  — exponential backoff config + reconnect lifecycle.

**Smoke test** (DevTools offline 30s → online → автоматический reconnect)
— **deferred в M13 G23 VPS dry-run** per owner-policy.

## Test coverage (M13)

| Test | Что покрывает |
|------|---------------|
| `WebSocketConfigTest.stompHeartbeatScheduler_isInitializedDaemonThread` | Bean exists, daemon, `stomp-heartbeat-` prefix. Catch'ит регрессию (если кто-то удалит TaskScheduler) |
| `StompIntegrationIT` | end-to-end CONNECT → SUBSCRIBE → MESSAGE через Testcontainers |
| `TicketHandshakeInterceptorTest` | ticket consume / 401 / replay protection |
| PWA `useStompCheckin.test.ts` (3 тестa) | reconnect mandatory, finite delay, ticket re-fetch |
| web-panel `notification-center.service.spec.ts` | reconnect lifecycle, exponential backoff config |

## Troubleshooting

### Соединение «зависает» после ~60 секунд

**Причина:** heartbeat выключен (M13 G18 fix не применён) → intermediate
proxy/firewall закрывает TCP idle-connection.
**Проверка:** в `WebSocketConfig.java` должен быть `setHeartbeatValue` +
`setTaskScheduler`. Bean `stompHeartbeatScheduler` должен существовать.

### 401 на каждый STOMP CONNECT

**Причина 1:** ticket уже потреблён (Redis SETDEL atomic) — frontend
вызывает `webSocketFactory` один раз на много CONNECT'ов.
**Fix:** убедиться что factory async + fetch'ит ticket внутри.

**Причина 2:** auth-service down или `/internal/consume-ws-ticket`
недоступен через Internal JWT (M03a).
**Проверка:** `curl -X POST http://auth-service:9090/internal/consume-ws-ticket -H "Authorization: Bearer <internal>"`.

### Heartbeat frames не идут через nginx

**Причина:** `proxy_buffering on` (default).
**Fix:** `proxy_buffering off` в `location /api/ws/` (M13 G18).

### Reconnect не работает после offline 30s

**Причина:** PWA SW/network listener не реагирует на online event.
**Проверка:** `useStompCheckin` должен слушать `window.addEventListener('online', ...)`.
Может быть устаревший ticket если `webSocketFactory` синхронный — должен быть `async`.

## Связанные документы

- `docs/auth/auth-flow.md` — JWT cookie flow, ws-ticket endpoint
- `docs/security/security-headers.md` — CSP с `connect-src 'self' wss:`
- `architecture.md` — общая схема сервисов
- `nginx/conf.d/default.conf` — production nginx config

## История изменений

- **M03b G4** (2026-04-20): ticket-based handshake (replaces legacy
  `JwtHandshakeInterceptor` который принимал raw JWT в URL).
- **M07 G3** (2026-04-22): unified STOMP в frontends — single source of
  truth `notification-center.service` / `useStompCheckin`.
- **M13 G18** (2026-04-25): heartbeat 10s/10s + dedicated TaskScheduler;
  nginx `proxy_buffering off`; doc создан.
