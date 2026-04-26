# Internal JWT Specification (NEW-3)

**Статус:** реализован в M03a (tag `v0.0.0-alpha.3`).
**Zero Trust Level 2** — downstream-сервисы больше не доверяют сетевой
границе и `X-User-*` заголовкам; каждый запрос валидируется подписью.

## Назначение

Устранить trust boundary между API Gateway и downstream-сервисами
(academic / schedule / attendance / notification-web). До M03a Gateway
декодировал внешний JWT и передавал claims через plain-text headers
`X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman` — любой клиент
с доступом к private-net (скомпрометированный sidecar, port-forward)
мог выдать себя за ADMIN простой подменой заголовка.

После M03a Gateway получает подписанный **Internal JWT** от auth-service
(token exchange, RFC 8693 pattern) и передаёт его в заголовке
`X-Internal-Token`. Каждый downstream валидирует подпись публичным
ключом auth-service.

## Формат токена

**Алгоритм:** RS256 (RSA-2048, SHA-256). Ключевая пара — та же, что у
существующих внешних JWT в auth-service (`keyDir` → `private.key` /
`public.key` / `kid.txt`).

**Header:**
```
{"alg":"RS256","typ":"JWT","kid":"<kid-from-auth-service>"}
```

**Claims:**

| claim         | тип     | значение                         | описание                           |
|---------------|---------|----------------------------------|------------------------------------|
| `sub`         | string  | userId (Long как строка)         | subject — владелец контекста       |
| `iss`         | string  | `"rutcampustrack-auth"`          | issuer — константа                 |
| `aud`         | string  | `"rutcampustrack-internal"`      | audience — константа, отличается от внешнего JWT (`"rutcampustrack"`) |
| `iat`         | number  | Unix timestamp (сек)             | issued-at                          |
| `exp`         | number  | Unix timestamp (сек)             | expiration — `iat + 300` (5 мин)   |
| `role`        | string  | `ADMIN`/`TEACHER`/`STUDENT`      | доменная роль пользователя         |
| `group_id`    | number  | Long или отсутствует             | ID группы (для STUDENT)            |
| `is_headman`  | boolean | `true`/`false`                   | флаг старосты                      |

**TTL:** 5 минут (auth-service `rutcampustrack.security.internal-issuer.token-ttl-seconds`).
Gateway кэширует токен на 4 минуты (safety margin 60 сек до истечения).

**Header name:** `X-Internal-Token: <compact-JWT>`. Выбран вместо
`Authorization: Internal <jwt>` (см. DECISIONS 2026-04-19) для
(1) изоляции от клиентского `Authorization: Bearer`, (2) ясности в
логах (internal-only trust boundary), (3) простоты downstream-парсинга.

## Ключи

- **Приватный ключ хранится ТОЛЬКО в auth-service.** Gateway и downstream
  никогда его не видят. Это снижает blast-radius компрометации Gateway
  или downstream-instance'ов (они не могут выпустить Internal JWT).
- **Публичный ключ пуллится через `GET /auth/public-key`** — всеми
  downstream-сервисами через `shared-security/PublicKeyProvider`
  (RestClient, `@Scheduled(fixedRate=1h)` refresh, `AtomicReference`).
- **Ротация ключа:** единственная точка — auth-service (`keyDir` replace +
  restart). После ротации все downstream refresh'нут public key в
  течение часа. Gateway также refresh'нет через `PublicKeyConfig`. Клиенты
  с кэшированным Internal JWT подписанным старым ключом получат 401
  через ~5 минут (TTL истечёт), retry с новым Issue-вызовом даст
  свежий подписанный новым ключом токен.
- **Алгоритм смены:** graceful — auth-service поддерживает одновременно
  новый и старый kid'ы 1 час. Ротация pre-scheduled на low-traffic час.

## Token Exchange Flow (a3 pattern)

```
Client                Gateway              auth-service        downstream
  │                     │                     │                    │
  │  GET /api/X         │                     │                    │
  │  Authorization:     │                     │                    │
  │  Bearer <ext-JWT>   │                     │                    │
  ├────────────────────▶│                     │                    │
  │                     │ validateSignature(ext-JWT)               │
  │                     │ (via PublicKeyConfig /auth/public-key)   │
  │                     │                     │                    │
  │                     │ Caffeine cache miss →                    │
  │                     │ POST /internal/issue-internal-jwt        │
  │                     │ X-Internal-Issuer-Secret: <secret>       │
  │                     │ {userId, role, groupId, isHeadman}       │
  │                     ├────────────────────▶│                    │
  │                     │                     │ timing-safe secret │
  │                     │                     │ compare            │
  │                     │                     │ signInternal(...)  │
  │                     │◀────────────────────┤                    │
  │                     │ {token, expiresAt}  │                    │
  │                     │                     │                    │
  │                     │ cache.put(userId, role → token)          │
  │                     │                     │                    │
  │                     │ X-Internal-Token: <signed-jwt>           │
  │                     ├─────────────────────────────────────────▶│
  │                     │                     │  validateSignature │
  │                     │                     │  (via PublicKeyProvider) │
  │                     │                     │  applyInternalJwt(claims)│
  │                     │                     │                    │
  │                     │◀─────────────────────────────────────────┤
  │◀────────────────────┤                     │                    │
```

**Cache hit:** Gateway возвращает токен из Caffeine без network-hop
(TTL 240 сек < auth-service TTL 300 сек, 60 сек safety margin).

**Cache miss / expiry:** +1 hop к auth-service (~2-5ms в private-net).

**Cache key:** `(userId, role)` — role change (admin меняет роль
пользователя) автоматически инвалидирует старую запись. Logout
invalidation — через Redis pub/sub в M03b.

## Dual-mode и Strict-mode

Чтобы не ломать downstream одним breaking commit'ом, вводится
двухэтапный rollout (DECISIONS 2026-04-19):

### Dual-mode (M03a deploy, default)

- Gateway **отправляет** `X-Internal-Token` ПЛЮС legacy `X-User-*`.
- Downstream `DualModeUserContextFilter` принимает **любой** из двух:
  приоритет Internal JWT (валидная подпись → claims); fallback —
  legacy `X-User-Id/Role/Group-Id/Is-Headman` (если
  `legacy-headers-enabled=true`).
- Это позволяет rolling deploy Gateway и downstream в любом порядке.

### Strict-mode (M03a финальный commit, v0.0.0-alpha.3)

- Gateway `strip-legacy-headers=true` → удаляет `X-User-*` перед proxy.
- Downstream `legacy-headers-enabled=false` → отклоняет запросы без
  Internal JWT с 401.
- Переключение одним коммитом (обе env vars) после UAT golden path.

**Переменные окружения для переключения:**
```bash
# В .env.prod:
GATEWAY_STRIP_LEGACY_HEADERS=true
RUTCAMPUSTRACK_SECURITY_LEGACY_HEADERS_ENABLED=false
```

## Downstream-валидация

Библиотека `services/shared/shared-security/` содержит:

| компонент                        | ответственность                                                 |
|----------------------------------|----------------------------------------------------------------|
| `InternalJwtValidator`           | парсит `X-Internal-Token`, валидирует signature/iss/aud/exp, возвращает `InternalJwtClaims` |
| `PublicKeyProvider`              | RestClient-puller с `/auth/public-key`, `AtomicReference<PublicKey>`, `@Scheduled` refresh 1h |
| `DualModeUserContextFilter`      | abstract — выбирает между Internal JWT и legacy X-User-*; hooks `applyInternalJwt(claims)` и `applyLegacyHeaders(request)` для сервис-специфичного `RequestContext` |
| `InternalJwtProperties`          | `rutcampustrack.security.internal-jwt.*` (URL, clock-skew, refresh-minutes, legacy-headers-enabled, expected-issuer/audience) |
| `InternalJwtException`           | runtime exception → 401                                         |
| `InternalJwtTestFactory` (testFixtures) | RSA keypair in-memory, методы `validToken/expiredToken/wrongSignature/wrongIssuer/wrongAudience/missingRole` |

Каждый из 4 downstream-сервисов (academic/schedule/attendance/notification)
имеет собственный `{Service}UserContextFilter extends DualModeUserContextFilter`
+ `InternalJwtConfig @Configuration` (регистрирует `PublicKeyProvider` +
`InternalJwtValidator` бины).

## Миграционный путь

1. **M02 (завершён)** — shared-outbox + contract-тесты.
2. **M03a Группы 1-2** — shared-security scaffold (validator библиотека).
3. **M03a Группа 3** — auth-service token-exchange endpoint
   `POST /internal/issue-internal-jwt` + `X-Internal-Issuer-Secret`.
4. **M03a Группа 4** — Gateway `InternalJwtIssuerClient` + `InternalJwtIssuerFilter`.
5. **M03a Группы 5-8** — downstream миграция (academic/schedule/attendance/notification).
6. **M03a Группы 9-13** — rate-limiting в Gateway + composite login key + contract IT.
7. **M03a Группа 14** — strict-mode toggle (infrastructure).
8. **M03a Группа 16** — UAT golden path + финальный commit переключает prod на strict.
9. **M03b** — JWT HttpOnly cookie + ws-ticket + logout lifecycle (зависит от M03a).

## Security properties

- **No key material in Gateway** — grep `api-gateway/src/main` не находит
  `PrivateKey`, `signWith`, `JWT_PRIVATE_KEY_PEM`. Защита от
  компрометации Gateway-host'а.
- **Timing-safe secret comparison** — auth-service `InternalIssuerSecretFilter`
  использует `MessageDigest.isEqual()` для проверки
  `X-Internal-Issuer-Secret`.
- **Fail-fast bootstrap** — оба сервиса требуют `INTERNAL_ISSUER_SECRET`
  env var (MIN 32 bytes) на старте; пустой/короткий секрет = crash на
  `@PostConstruct` вместо silent insecure bootstrap.
- **Short-lived tokens** — TTL 5 мин ограничивает окно replay-атаки.
- **Audience/issuer check** — downstream отвергает токены с
  `aud != rutcampustrack-internal` (напр. переданный внешний JWT).
- **Clock skew tolerance** — 30 секунд (`clock-skew-seconds`), работает
  при расхождении NTP между контейнерами.

## Roadmap — не входит в M03a

- **M03b** — JWT HttpOnly cookie для refresh + ws-ticket endpoint +
  clearAllClientState() в PWA/web-panel + logout invalidation cache через
  Redis pub/sub.
- **M06** — Vault/STS миграция (a3-shared-secret → a3-mTLS) — без
  переписывания кэширующей логики в Gateway.
- **M04** — Prometheus метрики token-exchange cache hit-rate, latency p95.
