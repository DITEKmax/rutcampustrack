# M03b Notes

Живой файл. Пиши сюда:
- **Отклонения от плана:** «решил сделать X вместо Y, потому что...»
- **Измерения:** «p95 latency до: 450ms, после: 120ms»
- **Surprises:** «обнаружил, что cookie Path=/ уже stat'ится nginx'ом»
- **Вопросы к владельцу:** «CSRF double-submit vs SameSite-only — что выбрать?»
- **Технические долги:** «оставил TODO про X — закрою в M{X}»

Не пиши:
- Общие описания модулей (это в PLAN.md).
- WHY-обоснования (это в OWNER-ANSWERS.md и 99-executive-summary.md).
- Пошаговые инструкции (это в CHECKLIST.md).

---

## 2026-04-20 — Resolution: выбран вариант A из OWNER-ANSWERS

Владелец подтвердил выравнивание с OWNER-ANSWERS 02-Q-frontend-security
(2026-04-18):

- Cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=604800`
- CSRF token НЕ нужен (same-origin покрывает)

**Последствия для PLAN.md / CHECKLIST.md:**
- Группа 5 (CSRF infrastructure) — удаляется полностью
- PLAN.md раздел «5. CSRF infrastructure» — удаляется
- Acceptance criterion "CSRF: mutating request без X-CSRF-Token → 403" — удаляется
- Axios/Angular interceptor для CSRF — не пишем
- Scope M03b уменьшается на ~1 день

**Conditional follow-up (НЕ в scope M03b):** если в v1.0+ появится
второй origin (отдельный домен для backend / OAuth callback) —
переключаемся на SameSite=Lax + добавляем double-submit CSRF token.
Зафиксировать в Post-mortem как conditional future work.

---

## 2026-04-20 — Surprise: `/auth/refresh-body` уже существует

В `AuthController.java:127` уже есть endpoint `POST /auth/refresh-body`
(введён ранее для TMA / Mini App клиентов, которые не могут использовать
HttpOnly cookies). Это УПРОЩАЕТ scope Группы 2:

- `/auth/refresh` (тело → cookie-based) — breaking change: меняем
  контракт (читает cookie, пишет cookie, body игнорируется).
- `/auth/refresh-body` (сохраняется) — добавляем header `Deprecation:`
  + `Sunset:` per DECISIONS 2026-04-20 (удаление в M04/M05).
  Никакого нового endpoint создавать не нужно.

TMA/Mini App может либо остаться на `refresh-body` (legacy path), либо
мигрировать на cookie-flow — решение для M07 Frontend Hardening.

## 2026-04-20 — Surprise: /auth/ws-ticket защищён access-JWT, не Internal JWT

В PLAN/CHECKLIST сказано «secured Internal JWT (shared-security)». Это
ошибка плана. Реальность:

- Auth-service — **issuer** Internal JWT, не downstream-consumer.
- Auth-service не использует shared-security (DualModeUserContextFilter).
- Gateway валидирует внешний access-JWT и НЕ strip'ает `Authorization`
  header — он попадает в auth-service, который парсит его своим
  `JwtAuthenticationFilter` (стандартный RS256).

**Решение:** `/auth/ws-ticket` защищается access-JWT через
`.anyRequest().authenticated()` (дефолт SecurityConfig). `userId`
берётся из `Authentication.getName()` (как в `AuthController#changePassword`).
Internal JWT здесь не нужен — фронт делает запрос с `Authorization:
Bearer <access_token>`.

Это упрощает реализацию — никаких shared-security dependencies в
auth-service. Gateway просто не strip'ает путь из PUBLIC_PATHS,
поэтому он проверит JWT прежде чем пустить на auth-service.

## 2026-04-20 — Surprise: ticket должен нести group_id + is_headman

`SubscriptionAuthInterceptor` в notification-service требует
`group_id` и `is_headman` в session attributes. Изначальный WsTicketService
payload = `userId|role|expiresEpoch` — недостаточно.

Расширяю payload на `userId|role|groupId|isHeadman|expiresEpoch` (5 полей
pipe-separated). `groupId=0` если null. `InternalWsTicketController.ConsumeResponse`
расширяется на `{userId, role, groupId, isHeadman, expiresAt}`.

WsTicketController парсит access-JWT через JwtService.parseToken() чтобы
извлечь group_id/is_headman (они есть в access-JWT по M03a, проверено).

Обратная совместимость: не требуется, endpoint добавлен в M03b.

## 2026-04-20 — Surprise: /auth/logout был authenticated-only, добавлен в permitAll

Hand-off блок предполагал что `/auth/logout` уже в permit-all (и
JwtAuthenticationFilter всё равно парсит Bearer если есть). Реальность:
SecurityConfig авторизовал `/auth/logout` через `.anyRequest().authenticated()`,
и Gateway тоже требовал Bearer (не в PUBLIC_PATHS).

Edge-case: access истёк, refresh в cookie — клиент не может logout без
предварительного refresh → revoke refresh-token в Redis не происходит.

**Решение:** `/auth/logout` добавлен в permitAll обеих (auth-service
SecurityConfig + Gateway JwtAuthenticationFilter PUBLIC_PATHS). Опционально
Bearer — если есть, JwtAuthenticationFilter выставит Authentication и
we invalidate ws-ticket'ы; если нет (cookie-only) — пропускаем invalidation
(ticket'ы TTL 30s).

Безопасность: logout — идемпотентная операция на переданном refresh-token.
Злоумышленник может логаутнуть валидный cookie, но для этого уже нужен
cookie (CSRF закрывается SameSite=Strict, same-origin).

LogoutLifecycleIT покрывает оба пути (с Bearer / cookie-only).

## 2026-04-20 — Hand-off для следующей сессии (конец дня)

**Прогресс M03b: 6 из 13 групп закрыто + Группа 5 reserved.**

| Группа | Коммит | Статус |
|--------|--------|--------|
| 1 — Discovery + 5 decisions | `d3a03df` | ✅ |
| 2 — Auth-service cookie endpoints | `835b79b` | ✅ |
| 3 — WS-ticket endpoint + Lua consume | `afe1928` | ✅ |
| 4 — notification-service ticket handshake | `7c4fa6d` | ✅ |
| 5 — (reserved — CSRF удалён per DECISIONS 2026-04-20) | — | ✅ |
| 6 — PWA cookie+ws-ticket migration | `bc8fb3e` | ✅ |
| 7 — web-panel cookie+ws-ticket migration | `16915bc` | ✅ |
| 8 — Logout lifecycle (C0-5 полный) | — | ⏳ следующая |
| 9 — KI-3/6/8 hot-patches из M03a | — | ⬜ |
| 10 — KI-7 bcrypt DoS mitigation | — | ⬜ |
| 11 — Expanded IT + bug-hunter + security-auditor | — | ⬜ |
| 12 — docs + CHANGELOG + architecture | — | ⬜ |
| 13 — финал + tag v0.0.0-alpha.4 | — | ⬜ |

**Все тесты зелёные после Группы 7:**
- auth-service: полный test suite прошёл (AuthIntegrationTest +
  WsTicketIT + все остальные).
- notification-service: полный test suite прошёл
  (TicketHandshakeInterceptorTest + остальные).
- PWA: 122/122 vitest зелёные, `npm run build` зелёный.
- web-panel: 444/444 vitest зелёные, `npm run build` зелёный.

**Критичный контекст для Группы 8 (logout lifecycle):**

Смотри CHECKLIST.md Группа 8 — 4 пункта:
1. `AuthController#logout` дополнить: `wsTicketService.invalidateAllFor(userId)`
   из access-token Authentication перед revoke refresh. Метод уже
   существует в `WsTicketService` (Группа 3).
2. Push subscription отвязка — `DELETE /api/notifications/push/subscribe`
   (web-panel Группа 7 уже вызывает через clearAllClientState, проверить
   что endpoint существует в notification-service PushController).
3. Event `user.logged-out` через shared-outbox — owner решит что важно:
   можно отложить в M04 (там observability/events properly).
4. IT `LogoutLifecycleIT` — cookie cleared + refresh invalidated +
   ws-tickets invalidated + (если есть) push отвязан.

**Нюанс:** сейчас `AuthController#logout` НЕ knows userId если в body
передан refresh-token (парсит jti, но не userId explicitly). Через
access-JWT `Authentication` в SecurityContext мы можем получить userId,
но `/auth/logout` в SecurityConfig — в permit-all (JwtAuthenticationFilter
всё равно выставит auth если Bearer есть; при anon — userId нет).
Стратегия: если access-JWT есть → вызываем `invalidateAllFor(userId)`;
если нет (чистый cookie logout) → пропускаем invalidation (ticket'ы
истекут через 30s естественно). Не идеально, но OK для v0.0.0 — alpha.

**Что важно не забыть:**
- Тег `v0.0.0-alpha.4` ставится ТОЛЬКО в Группе 13, на финальном
  коммите milestone'а. БЕЗ push (жду «go»).
- ИЗМЕНЕНИЕ SCOPE: Группа 5 (CSRF) удалена per DECISIONS 2026-04-20.
- Фронтенд WSсервисы уже переключены на ticket — back-compat не нужен.

**Последние коммиты M03b (git log --oneline -7):**

- `16915bc` feat(web-panel): cookie-based refresh + WS ticket + clearAllClientState (M03b Группа 7)
- `bc8fb3e` feat(pwa): cookie-based refresh + WS ticket + clearAllClientState (M03b Группа 6)
- `7c4fa6d` feat(notification): WS ticket handshake replaces raw-JWT in query (M03b Группа 4)
- `afe1928` feat(auth): ws-ticket endpoint + atomic consume via Lua (M03b Группа 3)
- `835b79b` feat(auth): cookie-based refresh — HttpOnly+Secure+SameSite=Strict (M03b Группа 2)
- `d3a03df` docs(m03b): start milestone — 5 decisions + CSRF removed per OWNER-ANSWERS (M03b Группа 1)
- `081d3b0` docs(m03b): scaffold M03b + hand-off для следующей сессии

---

## Backlog из M03a post-mortem (для рассмотрения в Группах 9-10)

Известные issues, которые попадут в M03b или будут документированы как
«не в scope M03b → в M04/M06»:

- **KI-3 (MEDIUM):** `InternalJwtIssuerClient` не проверяет
  `issuedToken.expiresAt()` перед возвратом из кэша — при clock drift
  возможен expired token (окно ≤60s). Фикс в Группе 9.
- **KI-6 (MEDIUM):** `LoginRateLimiter` Redis TTL race `INCR+EXPIRE` —
  network blip = persistent key без expiry. Фикс в Группе 9 (Lua-script
  или `SET ... EX N NX`).
- **KI-7 (MEDIUM):** bcrypt DoS через concurrent invalid-password до
  `checkBlocked` triggers. Фикс в Группе 10 (semaphore или distributed
  lock).
- **KI-8 (MEDIUM):** Composite rate-limit композитный `(ip, login)`
  неэффективен без Gateway CacheRequestBody extraction X-Login из тела.
  Фикс в Группе 9 (LoginBodyExtractionFilter).

Не в scope M03b (откладываются):
- **KI-1** X-Forwarded-For spoofing — M06 (nginx + Gateway trusted-proxies).
- **KI-2** Dual-mode silent fallback без метрики — M04 (observability).
- **KI-4** PublicKeyProvider readiness probe — M04.
- **KI-5** FailOpenRateLimiter whitelist сужение — hot-patch или M04.
- **KI-9** INTERNAL_ISSUER_SECRET plaintext → mTLS — M06.
