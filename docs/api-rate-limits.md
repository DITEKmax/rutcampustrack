# API Rate Limits (NEW-11)

**Статус:** реализован в M03a (tag `v0.0.0-alpha.3`).

Документирует лимиты запросов, применяемые Gateway и auth-service на
уровне отдельных endpoint'ов. Применимо к всем клиентам RutTrack
(PWA `/app`, web-panel `/admin|/teacher|/student|/headman`, Telegram
Mini-App `/tma`, сторонние интеграции через `/api/*`).

## Семантика токен-бакета

Spring Cloud Gateway `RedisRateLimiter` использует token-bucket:

- **`replenishRate`** (tokens/sec) — restore rate, сколько токенов
  восполняется в секунду.
- **`burstCapacity`** (tokens) — размер бакета, максимум запросов
  подряд до начала отказов.
- **`requestedTokens`** — сколько токенов списывается за запрос
  (всегда `1` в текущей конфигурации).

**Практическое следствие:** число в таблице ниже — это `burstCapacity`.
Клиент может сделать N запросов подряд, затем rate-limit throttle'ит
его до `replenishRate` запросов/сек. Через 60 сек полного бездействия
бакет снова полон.

## Таблица лимитов (Gateway)

| Endpoint                              | Ключ                 | burst / replenish | Комментарий                      |
|---------------------------------------|----------------------|-------------------|----------------------------------|
| `POST /api/auth/otp/request`          | IP                   | 1 / 1s            | SMS-cost guard                   |
| `POST /api/auth/otp/verify-by-code`   | IP                   | 5 / 5s            |                                  |
| `POST /api/auth/login`                | IP (фильтр 1)        | 5 / 5s            | Hard IP-ceiling против distributed brute |
| `POST /api/auth/login`                | IP+login composite   | 10 / 10s          | X-Login header, fallback на IP при отсутствии |
| `POST /api/auth/refresh[-body]`       | userId (X-User-Id)   | 30 / 30s          | Rotation abuse guard             |
| `POST /api/attendance/check-in`       | userId               | 10 / 10s          | Multi-mark prevention            |
| `* /api/academic/**`                  | IP                   | 600 / 600s        | DDoS guard                       |
| `* /api/schedule/**`                  | IP                   | 600 / 600s        | DDoS guard                       |
| `* /api/attendance/**`                | IP                   | 600 / 600s        | DDoS guard                       |
| `* /api/push/**`                      | IP                   | 600 / 600s        | DDoS guard                       |

**IP извлекается:** `X-Forwarded-For` (первый в списке) → `RemoteAddr` →
`"unknown"`. Gateway всегда за nginx/cloudflare в prod, поэтому XFF
присутствует.

### Поведение при превышении

- **HTTP 429 Too Many Requests.**
- **`Content-Type: application/problem+json`** (RFC 7807 Problem Details).
- **Body:**
  ```json
  {
    "type": "https://ruttrack.site/problems/rate-limit-exceeded",
    "title": "Too Many Requests",
    "status": 429,
    "detail": "Request rate limit exceeded. Retry later."
  }
  ```
- **`Retry-After: 60`** — секунды (если downstream не переопределил).
- **`X-RateLimit-Remaining`, `X-RateLimit-Replenish-Rate`,
  `X-RateLimit-Burst-Capacity`** — информационные headers от
  `RedisRateLimiter`.

### Fail-open при недоступности Redis

Если Redis отвечает с ошибкой (`RedisConnectionFailureException`,
`QueryTimeoutException`, Lettuce timeouts/refusals),
`FailOpenRateLimiter` пропускает запрос с `allowed=true` и header
`X-RateLimit-FailOpen: true` + WARN-лог. Это предотвращает DoS-of-our-
own-service сценарий, но означает что в момент Redis-outage лимиты
не действуют.

**Мониторинг:** Prometheus метрика `rate_limiter_failopen_total` (M04)
должна алертить SRE при >0 events/5min.

## Auth-service `LoginRateLimiter` (отдельно от Gateway)

Прогрессивная блокировка **после неудачных попыток пароля** с
композитным ключом `(ip, login)` (M03a Группа 11):

| Попыток неудачных    | Блок                |
|----------------------|---------------------|
| 5                    | 5 минут             |
| 10                   | 30 минут            |
| 20                   | 2 часа              |

**Окно счёта:** 60 минут. TTL на блокирующем ключе = длина блока.

**Redis keys:**
- `login_attempts:<ip>:<login>` — счётчик failed-попыток.
- `login_blocked:<ip>:<login>` — маркер блокировки с TTL.

**Поведение при блокировке:** HTTP 429 с telling-фразой
«Account temporarily locked. Try again in N minutes».

**Ключевое отличие от Gateway RL:** `LoginRateLimiter` срабатывает
ТОЛЬКО на неудачных паролях. Успешный login очищает счётчик —
атакующий тратит ещё одну попытку, легитимный пользователь не
испытывает friction. Композитный ключ `(ip, login)` защищает от
**DoS-by-rate-limit**: атакующий с одного IP не может залочить чужой
аккаунт.

## Рекомендации для клиентов

### Retry с экспоненциальным backoff

```typescript
async function rateLimitedFetch(url: string, options: RequestInit): Promise<Response> {
  for (let attempt = 0; attempt < 4; attempt++) {
    const response = await fetch(url, options);
    if (response.status !== 429) return response;

    const retryAfter = parseInt(response.headers.get('Retry-After') ?? '1', 10);
    const backoff = Math.min(retryAfter * 1000 * Math.pow(2, attempt), 60_000);
    await new Promise(r => setTimeout(r, backoff));
  }
  throw new Error('Rate limit persistent, giving up');
}
```

### Обязательный `X-Login` для login endpoint

Для корректной работы composite IP+login ключа PWA / web-panel шлют
`X-Login` header на `POST /api/auth/login`, дублируя поле из body:

```typescript
fetch('/api/auth/login', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-Login': formData.login,  // ← дубликат для composite RL-key
  },
  body: JSON.stringify({ login: formData.login, password: formData.password }),
});
```

Без `X-Login` composite-filter fallback'ится на IP-ключ — защита
работает, но соседи по WiFi с тем же IP будут влиять на rate-limit
друг друга.

### Локальное дебоунсирование check-in

`/api/attendance/check-in` имеет лимит 10/sec per user. Клиентский код
должен debounce кнопку отметки (min 500ms между кликами) и не
автоматически ретраить при успешном 200.

### Геоотметка: batch / single-shot

PWA геоотметка шлёт ОДИН check-in при fresh GPS fix. Не ретраить
без явного пользовательского действия — повторный click на кнопку.

## Monitoring (roadmap M04)

- `rate_limiter_denied_total{route, reason}` — counter 429 по route/cause.
- `rate_limiter_failopen_total` — Redis-outage counter (алерт на >0 / 5 min).
- `rate_limiter_latency_seconds{route}` — Lua-script latency p95 (алерт
  >50ms).
- Grafana dashboard: visual top-10 IP по denied, trend login-блокировок.

## Rollback / tuning

Лимиты настраиваются в `services/api-gateway/src/main/resources/application.yml`
в секции `routes[].filters[].args`. Изменения требуют redeploy Gateway.

Для временного disable лимита на конкретном endpoint — удалить соответствующий
`RequestRateLimiter` filter из route и сделать rolling restart Gateway.
Redis-ключи с префиксом `request_rate_limiter.*` можно удалять безопасно —
это просто reset бакетов.
