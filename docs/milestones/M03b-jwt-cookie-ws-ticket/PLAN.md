# M03b — JWT HttpOnly Cookie + WebSocket Ticket + Logout Lifecycle

**Статус:** ⏳ в работе
**Старт / финиш:** 2026-04-20 / —
**Estimate:** 8-12 человеко-дней

---

## Scope

Вторая половина Secure Boundaries. Зависит от M03a (Internal JWT из M03a
защищает новый `/auth/ws-ticket` endpoint). Главная цель — убрать JWT
из небезопасных мест (localStorage, WebSocket query-string) и завершить
logout lifecycle.

**Закрывает (сверка с `15-cross-cutting-issues.md` + `OWNER-ANSWERS.md`):**

- **Кластер C0-7** — JWT HttpOnly cookie + ws-ticket (09 P0-1/2, 10 P0-1/2,
  4 P0) — **самый дорогой кластер**. CSRF token НЕ реализуется
  (DECISIONS 2026-04-20, same-origin + `SameSite=Strict`).
- **Кластер C0-5** — logout lifecycle (09 P0-4/5, 10 P0-4, 3 P0):
  `clearAllClientState()` в PWA/web-panel + SW cache wipe + push
  subscription отвязка.
- **02-Q-frontend-security (Часть А)** — JWT HttpOnly cookie для refresh,
  access token в memory only. `localStorage['rct.auth.v1']` удаляется.
- **02-Q-frontend-security (Часть Б)** — `clearAllClientState()` helper
  вызывается в logout flow PWA/web-panel.
- **Breaking frontend migration** — `/auth/refresh` body → cookie,
  WebSocket token-in-query → ticket-in-query (short-lived).
- **NEW follow-ups из M03a post-mortem (KI-3, KI-6, KI-7, KI-8)** —
  token expiry check в Gateway cache, Redis TTL race fix, bcrypt DoS
  mitigation, Gateway CacheRequestBody для X-Login extraction.

**Не входит в M03b (отложено в другие milestones):**
- Metrics token-exchange cache / fallback counters (M04 Observability).
- Nginx `trusted-proxies` whitelist (M06 Ops).
- CSP nginx web-panel (Фаза 3, отдельный коммит).
- mTLS между сервисами (M06).

## Модули / изменения

### 1. Auth-service — cookie endpoints + ws-ticket

- `AuthController#refresh` — новый mode: читает refresh-token из cookie
  `rct_refresh`, пишет new refresh в cookie (`HttpOnly; Secure; SameSite=Strict;
  Path=/api/auth; Max-Age=<refresh-ttl>`), access в body JSON.
  Оставляет `refresh-body` endpoint на 1 milestone как deprecation-path.
- `POST /auth/ws-ticket` — **новый endpoint**. Требует Internal JWT (из
  M03a). Возвращает короткоживущий ticket (30 sec), хранится в Redis
  `ws_ticket:<uuid>` + индекс `ws_ticket_user:<userId>` (Set<uuid>) —
  см. DECISIONS 2026-04-20 WS-ticket storage.
- `POST /auth/logout` — в дополнение к revoke refresh: удаляет ticket'ы
  пользователя (SMEMBERS → DEL + DEL set), очищает cookie
  (`Max-Age=0; Path=/api/auth`), пушит событие logout (используется в
  M04 для audit).

### 2. Api-gateway — cookie forwarding

- Cookie `rct_refresh` автоматически ограничивается `Path=/api/auth` на
  стороне browser'а, Gateway просто forward'ит Set-Cookie header обратно
  клиенту и input cookie downstream.
- WebSocket routes `/api/ws/**` — добавляет ticket validation фильтр
  до notification-web (или notification-web сам валидирует через
  Internal JWT + ticket-check).
- CSRF НЕ реализуется (DECISIONS 2026-04-20: same-origin + SameSite=Strict).

### 3. Notification-web — WS handshake ticket

- `JwtHandshakeInterceptor` заменяется на `TicketHandshakeInterceptor`:
  читает query-param `?ticket=<value>`, проверяет в Redis ws_ticket,
  invalidates (single-use), извлекает userId → STOMP session.
- Внешний JWT больше НЕ в WebSocket query-string.

### 4. Frontend (PWA + web-panel) — breaking migration

- `localStorage['rct.auth.v1']` **полностью удалён** — access token хранится
  только в memory (`sessionStorage['rct.access.v1']` опционально для
  resilience between page reloads, но без refresh-token).
- `useAuth()` hook — новый lifecycle: mount → POST /auth/refresh (cookie
  автоматически отправляется browser'ом) → access в memory. Logout →
  POST /auth/logout → `clearAllClientState()`.
- `clearAllClientState()` — новый helper (PWA + web-panel):
  - `localStorage.clear()` / `sessionStorage.clear()`.
  - `serviceWorkerRegistration.unregister()` + `caches.delete()` (PWA).
  - `pushSubscription.unsubscribe()` + `DELETE /api/notifications/push/
    subscriptions/me` (PWA).
  - Reset Pinia/Redux/Zustand stores.
- WebSocket connect flow: `POST /auth/ws-ticket` → получить ticket →
  `new WebSocket("wss://.../ws?ticket=<short-lived>")` → ticket списывается.

### 5. Tests

- IT `JwtCookieRefreshIT` (auth-service, Testcontainers) — cookie set
  правильно (HttpOnly+Secure+SameSite=Strict+Path=/api/auth+Max-Age),
  reusable без body.
- IT `WsTicketIT` — ticket single-use, invalidation, expired → 401.
- IT `LogoutLifecycleIT` — cookie cleared + refresh-token revoked +
  ws-ticket'ы удалены.
- E2E `FrontendAuthFlowPlaywrightIT` (web-panel, deferred to M08) —
  login → refresh в фоне → logout → verify storage cleared + SW
  unregistered.

## Acceptance criteria

- [ ] `/auth/login` response ставит cookie `rct_refresh` (HttpOnly+Secure+
  SameSite=Strict+Path=/api/auth+Max-Age=refreshTtl), access в body JSON.
- [ ] `/auth/refresh` без body читает cookie → возвращает новый access +
  rotates cookie. Body-mode deprecated с warning header.
- [ ] `POST /auth/ws-ticket` с Internal JWT возвращает ticket. Ticket
  single-use, TTL 30 sec.
- [ ] WebSocket connect через ticket (не external JWT).
- [ ] Logout: cookie cleared + refresh invalidated + ws-tickets удалены +
  push subscription отвязана + `clearAllClientState()` отработал.
- [ ] Frontend: `localStorage['rct.auth.v1']` пуст после login/refresh.
  `git grep` не находит `localStorage.setItem.*rct.auth` (кроме migration
  helper на 1 релиз).
- [ ] E2E Playwright golden path admin/teacher/student/headman — работает
  на cookie+ticket (deferred to M08).
- [ ] `./gradlew build` зелёный + `npm run build` зелёный в PWA/web-panel.
- [ ] `docs/auth-flow.md` — новый runbook с диаграммой cookie+ticket flow.

## Dependencies

- **Блокирует:** M07 (Frontend Hardening) — openapi-typescript учитывает
  новые endpoints `/auth/ws-ticket`, `/auth/logout`. M04 (Observability) —
  метрики auth-flow (login-rate, refresh-rotation-rate, ws-ticket-rate).
- **Блокируется:** **M03a** (tag `v0.0.0-alpha.3`) — Internal JWT
  pre-requisite для защиты `/auth/ws-ticket`. **M01 (shared-web)** — RFC
  9457 error handling. **M02** (outbox) — logout event публикуется через
  outbox.
- **Parallel safe:** M04, M05, M06 (независимые по коду пути).

## Artifacts

- `services/auth-service/.../WsTicketController.java` + IT.
- `services/notification-service/.../security/TicketHandshakeInterceptor.java`.
- `frontends/pwa/src/lib/auth/clearAllClientState.ts`.
- `frontends/pwa/src/lib/auth/useAuth.ts` — breaking rewrite.
- `frontends/web-panel/src/app/core/auth/clear-all-client-state.service.ts`.
- `docs/auth-flow.md` — cookie+ticket lifecycle с диаграммой.
- `docs/architecture.md` раздел «Auth flow (cookie + ws-ticket)».
- `CHANGELOG.md [Unreleased]` — Breaking changes section.
- Tag `v0.0.0-alpha.4` на финальном коммите.

---

_Никаких «why», «motivation», «background» — это уже в
99-executive-summary.md и OWNER-ANSWERS.md. Здесь только WHAT и
DONE-критерии._

## Post-mortem

_Заполняется в конце milestone'а._
