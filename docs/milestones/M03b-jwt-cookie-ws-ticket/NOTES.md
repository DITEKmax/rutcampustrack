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
