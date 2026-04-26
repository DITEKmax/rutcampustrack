# M03b Checklist

Порядок: auth-service cookie+ws-ticket endpoints → notification-web
handshake → frontend migration → logout lifecycle → hot-patches из
M03a post-mortem (KI-3/6/7/8) → документация → финал.

CSRF-инфраструктура удалена из scope (DECISIONS 2026-04-20:
same-origin + `SameSite=Strict`). Группа 5 переформатирована на
«reserved» с пустым content — для сохранения нумерации.

## Группа 1 — Discovery + архитектурные решения

- [x] Прочитать docs/milestones/M03a-internal-jwt-ratelimit/PLAN.md
  «Post-mortem» — Known Issues KI-1..KI-9 (какие попадают в M03b, какие
  в M04/M06)
- [x] Прочитать `02-Q-frontend-security` в OWNER-ANSWERS.md — Часть А
  (cookie) + Часть Б (clearAllClientState)
- [x] Прочитать 09-frontend-pwa.md, 10-frontend-web-panel.md отчёты
  (P0-1/2/4/5 в обоих)
- [x] DECISIONS.md: cookie Path — выбран `/api/auth` (из OWNER-ANSWERS)
- [x] DECISIONS.md: SameSite — выбран `Strict` (из OWNER-ANSWERS)
- [x] DECISIONS.md: CSRF token storage — NONE (same-origin + SameSite=Strict)
- [x] DECISIONS.md: ws-ticket storage — (c) `ws_ticket:<uuid>` +
  `ws_ticket_user:<userId>` Set, atomic consume via Lua
- [x] DECISIONS.md: deprecation path для `/auth/refresh-body` — 1
  milestone с `Deprecation:` header, удалить в M04/M05

## Группа 2 — Auth-service: cookie endpoints

- [x] `AuthController#login` — добавить `Set-Cookie` на successful login
  (cookie `rct_refresh` с refresh-token, HttpOnly+Secure+SameSite=Strict+
  Path=/api/auth+Max-Age=<refreshTtl>). Также OTP verify + TMA ставят
  cookie (для web-panel / PWA fallback).
- [x] `AuthController#refresh` — новый `@PostMapping("/refresh")` читает
  `@CookieValue("rct_refresh")`, возвращает new access + cookie rotate
- [x] `AuthController#refresh-body` — deprecated (оставляется на 1
  milestone с header `Deprecation: true` + `Sunset: <date>`)
- [x] `AuthController#logout` — в Set-Cookie пишет cookie с Max-Age=0
  и тем же Path=/api/auth (clear), revokes refresh в Redis как раньше.
  Поддерживает и cookie, и body (legacy TMA).
- [x] Unit/IT: `AuthIntegrationTest` расширить — cookie присутствует,
  `rct_refresh=...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth;
  Max-Age=N`. Refresh через cookie works. Logout clear. Deprecation header.
- [x] `docs/auth/auth-flow.md` scaffold с cookie flow diagram

## Группа 3 — Auth-service: ws-ticket endpoint

- [x] `WsTicketController` — `POST /auth/ws-ticket`, защищён access-JWT
  (не Internal JWT — surprise, NOTES 2026-04-20), body empty, response
  `{ticket, expiresAt}`
- [x] `WsTicketService` — generate UUID ticket + user-set index, Redis
  `ws_ticket:<uuid>` → `<userId>|<role>|<expiresEpoch>` TTL 30s +
  `ws_ticket_user:<userId>` Set TTL 60s. Method `consume(ticket)` —
  atomic GET+DEL+SREM через Lua-script.
- [x] `InternalWsTicketController` — `POST /internal/consume-ws-ticket`
  защищён shared-secret filter из M03a, возвращает `{userId, role,
  expiresAt}` или 404. Для notification-web handshake (Группа 4).
- [x] IT `WsTicketIT` (Testcontainers Redis): issue happy-path,
  без auth → 401/403, consume happy-path, double consume → 404,
  unknown ticket → 404, consume без internal-secret → 401 (6 тестов)
- [x] SecurityConfig — `/auth/ws-ticket` НЕ permit-all (дефолт
  `.anyRequest().authenticated()` + `JwtAuthenticationFilter`).
  `/internal/consume-ws-ticket` уже защищён `InternalIssuerSecretFilter`.

## Группа 4 — Notification-web: ticket handshake

- [x] Удалить старый `JwtHandshakeInterceptor` (читал external JWT из
  query) + `JwtHandshakeInterceptorTest`. WebSocketConfig переключён.
- [x] `TicketHandshakeInterceptor` — читает `query.ticket`, вызывает
  `WsTicketClient` (REST к auth-service `/internal/consume-ws-ticket`
  с `X-Internal-Issuer-Secret`), получает userId/role/groupId/isHeadman,
  кладёт в STOMP session attributes (совместимо с SubscriptionAuthInterceptor).
- [x] REST выбран (не gRPC): `POST /internal/consume-ws-ticket` с
  `X-Internal-Issuer-Secret` — переиспользует M03a pattern, проще
  unit-тестировать, не требует proto-контракта. NotificationConfig
  WsTicketProperties (auth-service-url + internal-issuer-secret).
- [x] Unit-тест `TicketHandshakeInterceptorTest` — valid ticket →
  sessionAttributes populated; missing ticket → 401; consumed/expired
  ticket → 401; SockJS-style path → ticket extracted; multi-param
  query → ticket picked (5 тестов, mockito).

## Группа 5 — (reserved — was CSRF, removed per DECISIONS 2026-04-20)

Группа удалена из scope M03b. Same-origin + `SameSite=Strict` закрывают
CSRF для v0.0.0 (DECISIONS 2026-04-20, подтверждение OWNER-ANSWERS
02-Q-frontend-security). Нумерация следующих групп сохранена для
целостности ссылок.

## Группа 6 — Frontend PWA: cookie+ticket миграция

- [x] `frontends/pwa/src/features/auth/AuthProvider.tsx` — breaking rewrite:
  login → POST с credentials → cookie auto-set → access в memory
  (React state + tokenRef). Refresh через POST без body (cookie auto-send,
  bootstrap на mount). Logout → POST + clearAllClientState.
- [x] `features/auth/clearAllClientState.ts` — helper: очищает
  localStorage/sessionStorage, удаляет runtime caches (headman-api-cache*),
  push unsubscribe + DELETE /api/notifications/push/subscribe.
- [x] `features/auth/wsTicket.ts` (+ использование в useStompCheckin,
  NotificationCenter) — `buildWsUrl()` pre-fetch'ит ticket через
  `POST /auth/ws-ticket` перед WebSocket connect. Замена `?token=<JWT>`
  на `?ticket=<uuid>`.
- [x] `shared/lib/axios.ts` — `withCredentials: true` ставится per-call
  в api.ts для `/auth/*`. Refresh interceptor использует body=null + cookie.
  `setRefreshTokenGetter` удалён (refresh больше не в JS памяти).
- [x] Удалён `localStorage.setItem('rct.auth.v1', ...)`. Migration helper
  в `AuthProvider` — удаляет legacy blob на mount.
- [x] Vitest: все 122 теста PWA прошли. AuthProvider.test (cookie flow,
  migration, logout), AuthProvider.isHeadman (JWT claim parsing),
  PWAHeadmanRole (bottom-nav role routing), useStompCheckin (ws-ticket
  factory). `npm run build` зелёный.

## Группа 7 — Frontend web-panel: миграция (Angular)

- [x] `core/auth/auth.service.ts` — breaking rewrite: access-only (refresh
  в cookie), `setTokens` legacy shim, migration удаляет rct.auth.v1.
  `bootstrap()` метод вызывается через `provideAppInitializer` в app.config.
- [x] `core/auth/clear-all-client-state.ts` — helper: localStorage.clear +
  sessionStorage.clear. Закрывает 10 P0-4.
- [x] `core/auth/auth.api.ts` + `core/auth/ws-ticket.ts` — cookie-based
  endpoints (`withCredentials: true`), `acquireWsTicket()`, `buildWsUrl()`.
- [x] Переведены 3 STOMP-сервиса (student-stomp, headman-stomp,
  notification-center) на ticket-based handshake. Component call-sites
  (`.connect(groupId)` без второго аргумента) обновлены.
- [x] `AuthInterceptor` — cookie-based refresh (`authApi.refresh()` без
  body, withCredentials). `setAccessToken` вместо `setTokens`.
- [x] Удалены `localStorage` refs для auth. Migration helper в
  AuthService constructor.
- [x] Vitest: 444/444 тестов зелёные (обновлены auth.service,
  auth.interceptor, login.component, student-stomp, student-excuses,
  student-late-checkin). `npm run build` зелёный.

## Группа 8 — Logout lifecycle (C0-5 полный)

- [x] `AuthController#logout` дополняется: удаляет ws-ticket'ы
  пользователя (Redis KEYS `ws_ticket:*` где userId matches — через
  отдельный index-set `ws_ticket_user:<userId>` → `Set<ticketId>`).
- [x] Push subscription отвязка — `DELETE /api/notifications/push/subscribe`
  существует (`PushApi#unsubscribe`), frontend PWA/web-panel вызывают
  через `clearAllClientState` (P0-5 закрыт в Группах 6/7).
- [x] Event `user.logged-out` отложен в M04 per DECISIONS 2026-04-20 —
  в M03b структурный лог `auth.logout userId=... revoked_tickets=...`
  покрывает audit-trail до появления event-infra.
- [x] IT `LogoutLifecycleIT` — POST /logout: refresh-token revoked,
  ws-tickets deleted (Redis keys `ws_ticket:*` + `ws_ticket_user:<id>`
  исчезли), cookie имеет Max-Age=0. Два теста: с Bearer и cookie-only.

## Группа 9 — KI-3/6/8 hot-patches из M03a post-mortem

- [x] KI-3: `InternalJwtIssuerClient.issueFor` проверяет
  `issuedToken.expiresAt()` < now+5s → invalidate cache + retry loader.
  Защита от clock drift. Новый тест `cachedToken_nearExpiry_triggersReIssue_KI3`.
- [x] KI-6: `LoginRateLimiter.recordFailure` — atomic `INCR + EXPIRE` через
  Lua-script (EXPIRE на первой попытке только). Убирает TTL-race =
  persistent key без expiry. LoginRateLimiterIT остаётся зелёным.
- [x] KI-8: Gateway `LoginBodyExtractionFilter` (GlobalFilter, order=-50) —
  читает JSON body POST `/api/auth/login`, извлекает `login`, ставит
  X-Login header в mutated request. `CompositeLoginKeyResolverIT`
  обновлён: клиент не шлёт X-Login (он бы strip'ался), composite
  rate-limit работает через body-extraction.

## Группа 10 — KI-7 bcrypt DoS mitigation

- [x] Вариант (a): `BcryptConcurrencyGuard` — Semaphore N=20 (fair) вокруг
  bcrypt invocations в `AuthService#login` и `#changePassword`. Запросы
  сверх лимита получают 429 `OtpRateLimitException` fail-fast (tryAcquire
  без timeout). Конфигурируется через `rct.auth.bcrypt.max-concurrent`.
- [x] IT `BcryptDoSMitigationIT`: 50 concurrent invalid-password с
  narrow guard (N=2) — часть получает 429 concurrency-limit, все 50
  завершаются за &lt; 30s без 5xx.

## Группа 11 — Tests + security audit

- [x] Expanded IT suite: `WsTicketIT` (6 тестов), `LogoutLifecycleIT`
  (2 теста), `BcryptDoSMitigationIT` (1 тест, 50 concurrent),
  `CompositeLoginKeyResolverIT` (3 теста). AuthFlowE2EIT — откладываем
  в M08 (Playwright).
- [x] bug-hunter subagent — 0 CRITICAL, 0 HIGH (real), 10 MEDIUM/LOW.
  Пофиксили HIGH-2 (atomic SADD+EXPIRE в WsTicketService).
- [x] security-auditor subagent — 24/24 checks PASS, 0 CRITICAL/HIGH,
  2 MEDIUM + 3 LOW. Пофиксили MEDIUM-1 (Bearer в DELETE push/subscribe
  при logout — cross-user push leak на shared-устройстве).
- [x] CRITICAL/HIGH — fix'ы в milestone. MEDIUM/LOW — в NOTES.md.

## Группа 12 — Документация + artifacts

- [x] `docs/auth/auth-flow.md` — полный lifecycle cookie+ticket+logout +
  диаграммы + endpoints + rate-limits + security properties.
- [x] `docs/architecture/architecture.md` → раздел «Auth flow (cookie + ws-ticket +
  logout lifecycle)» после «Internal JWT и rate-limiting».
- [x] `CHANGELOG.md [Unreleased]` → Added + Changed (breaking) + Fixed +
  Documentation секции для M03b.
- [x] `CLAUDE.md` — M03b статус ✅ — завершён 2026-04-20.
- [x] `docs/milestones/README.md` → M03b ✅ 2026-04-20.

## Группа 13 — Финал

- [x] Все acceptance criteria отмечены `[x]` (кроме E2E Playwright —
  deferred to M08).
- [x] `./gradlew build` + `npm run build` (PWA 122/122 + web-panel 444/444)
  зелёные в Группах 7, 10, 11.
- [x] Smoke: `AuthIntegrationTest.logout_viaCookie_returns204_andClearsCookie`
  + `LogoutLifecycleIT` покрывают login → refresh → logout flow. Real
  browser smoke отложен в M08 (Playwright).
- [x] Post-mortem в PLAN.md: commits, surprises, измерения, KI backlog,
  lessons.
- [x] Финальный коммит `chore(m03b): close cookie + ws-ticket + logout`.
- [x] `git tag v0.0.0-alpha.4` на финальном коммите (БЕЗ push — жду go).

---

_Если задача занимает > 4 часов — разрежь её прямо здесь и отметь._
