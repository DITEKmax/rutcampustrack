# M03b Decisions

Micro-ADR формата «решение + причина» для выборов, которые НЕ покрыты
OWNER-ANSWERS.md. Каждая запись — 5-10 строк, не больше.

Не дублировать сюда:
- Решения из OWNER-ANSWERS.md (на них ссылаются через Q-ID / P2-N/M).
- Общие архитектурные принципы (они в `docs/architecture.md` / CLAUDE.md).
- Детали реализации (они в коде + DECISIONS не для how, а для why).

Дублировать сюда:
- Выборы между равнозначными опциями.
- Отклонения от типового подхода с пояснением.
- Trade-off'ы, которые будут актуальны через полгода.

---

## ОТКРЫТО — развилки к подтверждению до кодинга

Эти записи владелец подтверждает в Группе 1 (Discovery) до старта
кодинга. Когда решение принято — переоформить в обычный
`## YYYY-MM-DD —` блок.

### ОТКРЫТО — Cookie Path scope

**Варианты:**
- (a) `Path=/api/auth/refresh` — cookie отправляется ТОЛЬКО на refresh
  endpoint. Минимизирует surface для accidental CSRF.
- (b) `Path=/api/auth` — cookie на любой auth endpoint.
- (c) `Path=/` — cookie везде. Удобнее для frontend, но CSRF-атака
  может использовать любой mutating endpoint downstream.

**Рекомендация:** (a) `Path=/api/auth/refresh`. CSRF защита минимизирована
per-endpoint. Frontend не нуждается в cookie на других routes.

### ОТКРЫТО — SameSite attribute

**Варианты:**
- (a) `SameSite=Lax` — cookie отправляется на top-level navigation
  (например OAuth callback), не отправляется на cross-origin mutating.
- (b) `SameSite=Strict` — cookie НЕ отправляется на любую cross-origin
  request, даже top-level navigation.

**Рекомендация:** (a) `Lax`. Совместим с потенциальным OAuth flow,
достаточно защищает от CSRF при modern browsers. Для legacy browsers
(которых RUT MIIT целевой аудитории почти нет) добавить double-submit
CSRF token.

### ОТКРЫТО — CSRF mechanism

**Варианты:**
- (a) Double-submit cookie pattern: `rct_csrf` cookie (non-HttpOnly) +
  `X-CSRF-Token` header на mutating. Frontend читает cookie, добавляет
  в header. Сервер сравнивает.
- (b) Sync-token pattern: server генерирует token в session, отдаёт в
  response body, клиент хранит в memory, шлёт в header. Требует state
  на сервере (Redis session).
- (c) `SameSite=Strict` only, без CSRF token. Modern browsers достаточно
  защищают.

**Рекомендация:** (a) Double-submit. Stateless (Redis не нужен),
защищает от CSRF даже при SameSite=Lax (при cross-origin
top-navigation). Frontend overhead минимален (interceptor).

### ОТКРЫТО — WS-ticket storage key scheme

**Варианты:**
- (a) `ws_ticket:<opaque-uuid>` → value `{userId, role, expiresAt}`
  (JSON). Простой consume через GET+DEL.
- (b) `ws_ticket:<userId>:<jti>` → value `<ticket-blob>`. Позволяет
  быстро invalidate все tickets пользователя при logout через `KEYS
  ws_ticket:<userId>:*` (дорого при большом Redis).
- (c) Комбинация: `ws_ticket:<uuid>` → value + отдельный set
  `ws_ticket_user:<userId>` → `Set<uuid>`. Удобно для logout
  invalidation, сложнее в поддержке.

**Рекомендация:** (c) — нужно для logout lifecycle (Группа 8
invalidation всех user'а tickets). Atomic consume через Lua-script
(GET+DEL+SREM one shot).

### ОТКРЫТО — /auth/refresh-body deprecation timeline

**Варианты:**
- (a) Удалить в финальном коммите M03b (нет backward-compat).
- (b) Оставить 1 milestone (M04) с `Deprecation: true` header, удалить
  в M04/M05.
- (c) Оставить навсегда для hypothetical mobile native apps.

**Рекомендация:** (b). Даёт frontend'у 1 milestone переходный период.
Native mobile apps НЕ запланированы в v0.0.0-v1.0 — отсутствие
cookie-stack там не актуально.

---

_Формат записи (после подтверждения):_

## YYYY-MM-DD — Короткое название решения

**Выбрано:** X
**Отвергнуто:** Y, Z
**Причина:** одна-две фразы.
**Последствия:** что теперь иначе в будущем (опционально).
