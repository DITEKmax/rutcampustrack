# M03b — JWT HttpOnly Cookie + WebSocket Ticket + Logout Lifecycle

**Статус:** ✅ готов (tag `v0.0.0-alpha.4`)
**Старт / финиш:** 2026-04-20 / 2026-04-20
**Estimate:** 8-12 человеко-дней (факт: ~2 сессии)

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

- [x] `/auth/login` response ставит cookie `rct_refresh` (HttpOnly+Secure+
  SameSite=Strict+Path=/api/auth+Max-Age=refreshTtl), access в body JSON.
  `AuthIntegrationTest.login_setsRefreshCookie_withStrictAttributes`.
- [x] `/auth/refresh` без body читает cookie → возвращает новый access +
  rotates cookie. `/auth/refresh-body` deprecated с `Deprecation: true` +
  `Sunset: Mon, 01 Jun 2026 00:00:00 GMT`.
- [x] `POST /auth/ws-ticket` с access-JWT (не Internal JWT — surprise в
  NOTES) возвращает ticket. Ticket single-use (Lua-atomic), TTL 30 sec.
  `WsTicketIT` покрывает 6 сценариев.
- [x] WebSocket connect через ticket (не external JWT).
  `TicketHandshakeInterceptor` заменяет `JwtHandshakeInterceptor`.
- [x] Logout: cookie cleared (Max-Age=0) + refresh invalidated + ws-tickets
  удалены (atomic `invalidateAllFor`) + push subscription отвязана
  (`DELETE /api/notifications/push/subscribe` с Bearer) + `clearAllClientState`
  отработал. `LogoutLifecycleIT` 2 сценария (Bearer / cookie-only).
- [x] Frontend: `localStorage['rct.auth.v1']` чистится миграционным
  helper'ом на mount. Grep: `localStorage.setItem.*rct.auth` не находит
  в source code (только тест-mock).
- [ ] E2E Playwright golden path admin/teacher/student/headman —
  **deferred to M08** (Test Infrastructure).
- [x] `./gradlew build` зелёный + `npm run build` зелёный (PWA 122/122,
  web-panel 444/444 + `npm run build` OK).
- [x] `docs/auth/auth-flow.md` — полный runbook cookie+ticket+logout.

**CSRF infrastructure удалена из scope** (DECISIONS 2026-04-20) — acceptance
criterion «mutating request без X-CSRF-Token → 403» удалён, same-origin +
`SameSite=Strict` достаточно.

**KI-3/6/7/8 hot-patches из M03a post-mortem** — закрыты в Группах 9-10:
- KI-3: `InternalJwtIssuerClient` clock-drift protection
- KI-6: `LoginRateLimiter` atomic INCR+EXPIRE
- KI-7: `BcryptConcurrencyGuard` Semaphore N=20
- KI-8: Gateway `LoginBodyExtractionFilter` для composite rate-limit

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
- `docs/auth/auth-flow.md` — cookie+ticket lifecycle с диаграммой.
- `docs/architecture/architecture.md` раздел «Auth flow (cookie + ws-ticket)».
- `CHANGELOG.md [Unreleased]` — Breaking changes section.
- Tag `v0.0.0-alpha.4` на финальном коммите.

---

_Никаких «why», «motivation», «background» — это уже в
99-executive-summary.md и OWNER-ANSWERS.md. Здесь только WHAT и
DONE-критерии._

## Post-mortem

### Коммиты milestone'а (13)

1. `081d3b0` chore: scaffold M03b + hand-off
2. `d3a03df` docs(m03b): 5 decisions + CSRF removed (Группа 1)
3. `835b79b` feat(auth): cookie-based refresh (Группа 2)
4. `afe1928` feat(auth): ws-ticket endpoint + Lua consume (Группа 3)
5. `7c4fa6d` feat(notification): WS ticket handshake (Группа 4)
6. `bc8fb3e` feat(pwa): cookie + ws-ticket + clearAllClientState (Группа 6)
7. `16915bc` feat(web-panel): cookie + ws-ticket + clearAllClientState (Группа 7)
8. `b1dd975` docs(m03b): hand-off для следующей сессии
9. `b1fbfcc` feat(auth): logout lifecycle — ws-ticket invalidation (Группа 8)
10. `9286809` feat(gateway,auth): KI-3/6/8 hot-patches (Группа 9)
11. `dff9ea1` feat(auth): KI-7 bcrypt DoS Semaphore (Группа 10)
12. `acf989b` fix(auth,pwa): bug-hunter HIGH-2 + security MEDIUM-1 (Группа 11)
13. `140d7d4` docs(m03b): auth-flow runbook + architecture + CHANGELOG (Группа 12)
14. _финальный_ — закрытие milestone + tag `v0.0.0-alpha.4`

Группа 5 удалена per DECISIONS 2026-04-20 (CSRF не нужен).

### Surprises

1. **`/auth/refresh-body` уже существовал** — упростило Группу 2
   (просто добавили `Deprecation`+`Sunset` header'ы, новый endpoint не
   создавали).
2. **`/auth/ws-ticket` защищён access-JWT, не Internal JWT** — план
   ошибочно ссылался на shared-security; реальность: auth-service —
   issuer Internal JWT, не consumer, поэтому использует стандартный
   `JwtAuthenticationFilter` (RS256).
3. **Ticket payload нуждается в `group_id` + `is_headman`** —
   `SubscriptionAuthInterceptor` требует их в session attributes. Payload
   расширен с `uid|role|exp` до `uid|role|grp|hd|exp`.
4. **`/auth/logout` был authenticated-only** — hand-off предполагал
   permitAll, реальность нет. Добавлен в permitAll auth-service +
   Gateway PUBLIC_PATHS для поддержки cookie-only logout когда access
   истёк.
5. **bug-hunter обнаружил HIGH-2**: `WsTicketService.issue()` SADD+EXPIRE
   был неатомарным (тот же класс бага, что KI-6 в LoginRateLimiter).
   Фикс: Lua-script.
6. **security-auditor обнаружил MEDIUM-1**: PWA `clearAllClientState`
   вызывал DELETE push/subscribe без Bearer → 401 на Gateway →
   subscription не удалялась → cross-user push leak на shared-устройстве.
   Фикс: передаём accessToken до обнуления.

### Измерения

- **Scope vs estimate:** 13 групп (план: 13, одна удалена, одна reserved).
  Factual time < estimate — большая часть уже была спроектирована в
  OWNER-ANSWERS + 99-executive-summary.md.
- **Тесты:** auth-service +16 (WsTicketIT +6, LogoutLifecycleIT +2,
  BcryptDoSMitigationIT +1, обновлённые). api-gateway +2
  (CompositeLoginKeyResolverIT +1 KI-8, InternalJwtIssuerClientTest +1
  KI-3). PWA 122/122, web-panel 444/444 — зелёные.
- **0 CRITICAL/HIGH** (real blockers) от bug-hunter + security-auditor.
  24/24 security-checks PASS.

### Known Issues → backlog M04/M06

Из bug-hunter + security-auditor (отложено в M04/M06):

- `InternalWsTicketController.consume` — добавить `@Valid` для контракта.
- `LoginBodyExtractionFilter` > 4K body → `DataBufferLimitException` →
  500, нужен `onErrorResume` → 413.
- `InternalJwtIssuerClient` clock-skew cache — concurrent swap race
  (функционально ok, оптимизация).
- `TicketHandshakeInterceptor.extractTicket` — URL-decoding для
  non-UUID ticket'ов (если формат изменится).
- `WsTicketController.extractRole` — fallback на `"UNKNOWN"` вместо
  403 при отсутствии ROLE_*.
- `AuthCookies` — нет `Domain=` атрибута (задокументировать для
  будущих поддоменов).
- `/api/auth/change-password` + `/api/auth/ws-ticket` не rate-limited.
- `invalidateAllFor` + concurrent `issue` race → orphan ticket на 30s.
- **Event `user.logged-out` через shared-outbox** — отложено в M04
  (нужна event-infra в auth-service, structured log покрывает audit
  до появления OTel/Tempo).

### Lessons

- **«Hand-off предположения» проверять.** Hand-off блок сказал
  `/auth/logout` в permit-all — не было. Экономит сессию если проверить
  до изменений.
- **Одна и та же race-проблема чинится в разных местах** — KI-6 Lua
  INCR+EXPIRE в LoginRateLimiter и HIGH-2 Lua SADD+EXPIRE в WsTicketService.
  Паттерн: любой TTL'овый ключ + отдельная команда expire = race.
  Добавить check-list при code-review на все места с `expire()`.
- **bug-hunter + security-auditor параллельно** на финальный diff —
  дешёвый способ поймать остатки. Оба subagent'а нашли по одному real
  bug, который одиночный agent пропустил бы.
- **Semaphore fair=true** важно для DoS guard — без fairness тред с
  большей частотой запросов может занять permit первым. Чуть дороже
  (~5% overhead), но корректнее.
