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
- [x] `docs/auth-flow.md` scaffold с cookie flow diagram

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

- [ ] `core/auth/auth.service.ts` — breaking rewrite: те же принципы
  (cookie+memory)
- [ ] `core/auth/clear-all-client-state.service.ts` — Angular-версия
  helper'а (sessionStorage clear + reset NgRx/Signals state)
- [ ] `AuthInterceptor` — `withCredentials: true` на `/api/auth/**`
  requests (HttpClient по умолчанию НЕ шлёт cookie)
- [ ] Удалить `localStorage` refs для auth. Migration helper на старте.
- [ ] Jasmine/Vitest: auth flow tests

## Группа 8 — Logout lifecycle (C0-5 полный)

- [ ] `AuthController#logout` дополняется: удаляет ws-ticket'ы
  пользователя (Redis KEYS `ws_ticket:*` где userId matches — через
  отдельный index-set `ws_ticket_user:<userId>` → `Set<ticketId>`).
- [ ] Push subscription отвязка — `DELETE /api/notifications/push/
  subscriptions/me` уже существует? Проверить, в frontend logout handler
  обязательно вызвать.
- [ ] Event `user.logged-out` через shared-outbox (academic или auth —
  где ownership session) — для M04 audit.
- [ ] IT `LogoutLifecycleIT` — после POST /logout: refresh-token в
  Redis deleted, ws-tickets deleted, push DELETE called, cookie has
  Max-Age=0

## Группа 9 — KI-3/6/8 hot-patches из M03a post-mortem

- [ ] KI-3: `InternalJwtIssuerClient.issueFor` проверяет
  `issuedToken.expiresAt().isBefore(Instant.now().plusSeconds(5))` → если
  да, invalidate + retry loader (защита от clock drift edge case)
- [ ] KI-6: `LoginRateLimiter.recordFailure` атомарно через Lua-script
  `INCR + EXPIRE NX` или `SET ... EX 3600 NX` перед INCR (защита от
  network blip = persistent key)
- [ ] KI-8: Gateway `LoginBodyExtractionFilter` на `/api/auth/login` —
  `CacheRequestBody` + парсит JSON body, ставит X-Login header в mutated
  request (composite rate-limit теперь реально работает). IT тест
  `CompositeLoginKeyResolverIT` обновить: проверить что IP-A+alice
  5 раз → 6-й IP-A+bob проходит (разные login-buckets)

## Группа 10 — KI-7 bcrypt DoS mitigation

- [ ] Варианты (DECISIONS): (a) Bucket4j semaphore N=20 на
  AuthService#login, (b) Distributed lock `(ip, login)` перед bcrypt,
  (c) Отложить в M05 Performance с proper benchmark
- [ ] Реализация + IT: 50 concurrent invalid-password requests не
  blow up CPU (MeasuredLatencyIT)

## Группа 11 — Tests + security audit

- [ ] Expanded IT suite: JwtCookieRefreshIT, WsTicketIT, LogoutLifecycleIT,
  AuthFlowE2EIT (mock-based, Playwright в M08)
- [ ] bug-hunter subagent на полный diff
- [ ] security-auditor subagent (C0-5 + C0-7 — высокая цена бага)
- [ ] Все CRITICAL/HIGH — fix в milestone, MEDIUM/LOW — в NOTES как KI

## Группа 12 — Документация + artifacts

- [ ] `docs/auth-flow.md` (NEW, полный lifecycle cookie+ticket с диаграммами)
- [ ] `docs/architecture.md` → раздел «Auth flow (cookie + ws-ticket)»
  после «Internal JWT и rate-limiting»
- [ ] `CHANGELOG.md [Unreleased]` → BREAKING CHANGES + Added/Changed/Fixed
- [ ] `CLAUDE.md` — M03b статус ✅ + обновить раздел архитектуры
- [ ] `docs/milestones/README.md` → M03b ✅

## Группа 13 — Финал

- [ ] Все acceptance criteria отмечены `[x]`
- [ ] `./gradlew build` + `npm run build` (PWA + web-panel) зелёные
- [ ] Smoke: mock end-to-end login → refresh → logout → verify storage
  cleared. Manual + записать в NOTES
- [ ] Post-mortem в PLAN.md: commits, surprises, lessons, KI для M04/M06
- [ ] Финальный коммит `chore(m03b): close cookie + ws-ticket + logout`
- [ ] `git tag v0.0.0-alpha.4` на финальном коммите (БЕЗ push — жду go)

---

_Если задача занимает > 4 часов — разрежь её прямо здесь и отметь._
