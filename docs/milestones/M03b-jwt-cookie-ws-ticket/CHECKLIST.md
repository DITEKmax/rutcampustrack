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

- [ ] `AuthController#login` — добавить `Set-Cookie` на successful login
  (cookie `rct_refresh` с refresh-token, HttpOnly+Secure+SameSite=Strict+
  Path=/api/auth+Max-Age=<refreshTtl>)
- [ ] `AuthController#refresh` — новый `@PostMapping("/refresh")` читает
  `@CookieValue("rct_refresh")`, возвращает new access + cookie rotate
- [ ] `AuthController#refresh-body` — deprecated (оставляется на 1
  milestone с header `Deprecation: true`)
- [ ] `AuthController#logout` — в Set-Cookie пишет cookie с Max-Age=0
  и тем же Path=/api/auth (clear), revokes refresh в Redis как раньше
- [ ] Unit/IT: `AuthIntegrationTest` расширить — cookie присутствует,
  `rct_refresh=...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth;
  Max-Age=N`. Refresh через cookie works.
- [ ] `docs/auth-flow.md` scaffold с cookie flow diagram

## Группа 3 — Auth-service: ws-ticket endpoint

- [ ] `WsTicketController` — `POST /auth/ws-ticket`, secured Internal
  JWT (shared-security), body empty, response `{ticket, expiresAt}`
- [ ] `WsTicketService` — generate UUID ticket, store Redis
  `ws_ticket:<ticket>` → `<userId>:<role>` TTL 30s. Method `consume(ticket)`
  atomically GET+DEL.
- [ ] IT `WsTicketIT` (Testcontainers Redis): generate + consume happy
  path; consume twice → 2nd 404; expired (waitFor 31s) → 404
- [ ] SecurityConfig — `/auth/ws-ticket` permit-all на уровне Spring
  Security, но требует Internal JWT через `DualModeUserContextFilter`

## Группа 4 — Notification-web: ticket handshake

- [ ] Удалить старый `JwtHandshakeInterceptor` (читал external JWT из
  query), заменить на `TicketHandshakeInterceptor`
- [ ] `TicketHandshakeInterceptor` — читает `query.ticket`, вызывает
  `WsTicketServiceClient` (gRPC или REST к auth-service), получает
  userId/role, кладёт в STOMP session attributes
- [ ] gRPC vs REST — решение в DECISIONS. Для single-use consume —
  простой REST `POST /internal/consume-ws-ticket` с Internal-Issuer-Secret
- [ ] IT `WsHandshakeIT` (Testcontainers) — valid ticket → handshake 101;
  invalid/used → 401

## Группа 5 — (reserved — was CSRF, removed per DECISIONS 2026-04-20)

Группа удалена из scope M03b. Same-origin + `SameSite=Strict` закрывают
CSRF для v0.0.0 (DECISIONS 2026-04-20, подтверждение OWNER-ANSWERS
02-Q-frontend-security). Нумерация следующих групп сохранена для
целостности ссылок.

## Группа 6 — Frontend PWA: cookie+ticket миграция

- [ ] `frontends/pwa/src/lib/auth/useAuth.ts` — breaking rewrite: login
  через POST body → cookie auto-set → access в memory state (Zustand).
  Refresh через POST без body (cookie auto-send). Logout → POST +
  clearAllClientState.
- [ ] `clearAllClientState.ts` — helper функция: очищает
  localStorage/sessionStorage, unregister SW, caches.delete all, push
  unsubscribe + DELETE /api/notifications/push/subscriptions/me
- [ ] `WebSocketClient` — перед connect: POST /auth/ws-ticket → получает
  ticket → `new WebSocket(..?ticket=${t})`. Без query-JWT.
- [ ] axios/fetch interceptor — `credentials: 'include'` на всех
  `/api/auth/**` requests (чтобы браузер слал cookie)
- [ ] Удалить `localStorage.setItem('rct.auth.v1', ...)` везде. Migration
  helper на старте: если видит старый ключ → удалить + redirect to login
- [ ] Vitest: useAuth flow, clearAllClientState interaction с SW
  (mocked)

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
