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
