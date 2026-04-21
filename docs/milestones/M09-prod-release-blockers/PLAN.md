# M09 — Prod Release Blockers (Фаза 3 Точечные P0)

**Статус:** ⬜ не начат
**Старт / финиш:** — / —
**Estimate:** 4-5 человеко-дней

---

## Scope

Закрывает остаток **Фазы 3 «Точечные P0»** из `99-executive-summary.md`,
который не попал в M01-M08. Минимум, необходимый для выхода v0.0.0 в прод.

**Исключено (уже покрыто):**
- `10 P0-3` CSP nginx web-panel + NEW-55 PWA vhost — **M07** scope
  (`Frontend Hardening (CSP, a11y, UX, openapi-typescript)`).
- `01 P0-1` создание `auth-api-contract` — **отложен в v0.1 backlog**
  (структурный refactor, не блокер прода; переносится в
  `docs/future-ideas.md`).

**Включено:**

- **01 P0-4** (Q-P0-4) — OTP через RabbitMQ event вместо HTTP-body +
  схема `otp.requested` (08 P0-2 cascade)
- **01 P0-5** (Q-P0-5) — `MessageDigest.isEqual` в `OtpService.verifyOtp`
- **04 P0-6** (Q-P0-6) — удалить `@PostConstruct cleanupOrphans` +
  `docs/admin-scripts.md` шаблон
- **12 P0-2** (Q-P0-2 landing) — deep-link `https://t.me/<bot_username>`
  в кнопке лендинга
- **14 P0-1** (Q-P0-1 tests) — unit + integration + contract-тесты для
  `attendance-service/latecheckin/`
- **14 P0-2** (Q-P0-2 tests) — pytest + Aiogram fake-updates для
  `notification-bot/handlers/` callback_query
- **NEW-33** — `docs/admin-scripts.md` (шаблоны разовых админ-задач)
- **NEW-51** — документация `<bot_username>` в `.env.prod.example`
- **NEW-52** — `event-schemas/late-checkin-{requested,approved,rejected}.json`
- **NEW-53** — `notification-bot/tests/integration/conftest.py` фикстуры

## Модули / изменения

- `services/auth-service/.../service/OtpService.java` — `MessageDigest.isEqual`
  вместо `equals`; `otp.requested` через RabbitMQ publisher; body
  `204 No Content` или `{"delivery": "telegram"}`
- `event-schemas/otp-requested.json` — новая JSON Schema с `event_version: 1`
- `services/notification-bot/app/consumers/otp_requested.py` — консюмер
  события + отправка кода в Telegram
- `services/attendance-service/.../latecheckin/` — удалить
  `@PostConstruct cleanupOrphans`; удалить gRPC-вызов `ListLessons` если
  больше нигде не используется
- `services/attendance-service/.../latecheckin/LateCheckinServiceTest.java` +
  `LateCheckinControllerIT.java` + `LateCheckinEventContractTest.java`
- `event-schemas/late-checkin-{requested,approved,rejected}.json`
- `services/notification-bot/tests/test_callback_{excuse,late_checkin,prefs}.py`
  + `tests/integration/test_full_flow.py` + `tests/integration/conftest.py`
- `frontends/landing/index.html` — `<a href="https://t.me/<bot_username>">`
  (или `VITE_TELEGRAM_BOT_USERNAME` env)
- `docs/admin-scripts.md` — новый runbook (cleanup orphans, backfill,
  recovery templates)
- `.env.prod.example` — добавить `TELEGRAM_BOT_USERNAME=` с комментарием

## Acceptance criteria

- [ ] `POST /auth/otp/request` возвращает `204 No Content`, тело не
      содержит `code`. Интеграционный тест проверяет отсутствие поля.
- [ ] `OtpService.verifyOtp` использует `MessageDigest.isEqual`;
      существующие тесты зелёные.
- [ ] RabbitMQ event `otp.requested` публикуется auth-service'ом и
      консюмится notification-bot'ом; contract-тест валидирует payload
      против `event-schemas/otp-requested.json`.
- [ ] `AttendanceService.cleanupOrphans` удалён; integration-тест
      «старт контейнера с недоступным schedule-service» проходит
      (нет mass-delete на старте).
- [ ] Кнопка «Открыть в Telegram» на лендинге открывает
      `https://t.me/<bot_username>` (smoke-check руками на dev).
- [ ] Coverage `latecheckin/` package ≥ 70% line (gate активируется
      селективно до общего coverage-gate в M08).
- [ ] Coverage `notification-bot/handlers/` ≥ 70% line (pytest-cov
      gate на этом пакете).
- [ ] 3 event-schemas (`late_checkin.*`) закоммичены и валидируют
      publisher в `LateCheckinService.{create,approve,reject}`.

## Dependencies

- **Блокируется:** M03a (Internal JWT — cascade на
  `02 P0-3/03 P0-3/04 P0-3 AUTO-RESOLVED`). ✅ готово.
- **Блокируется:** M02 (shared-outbox — нужен для надёжной публикации
  `otp.requested`). ✅ готово.
- **Блокирует:** v0.0.0 release tag.
- **Parallel safe:** M06 (Ops & Supply Chain), M07 (Frontend Hardening),
  M08 (Test Infra) — можно делать параллельно. M08 coverage-gate
  включается глобально после M09 (M09 включает его селективно для
  двух пакетов как pilot).

## Artifacts

- `docs/admin-scripts.md` — NEW-33 runbook
- `event-schemas/otp-requested.json` — 08 P0-2
- `event-schemas/late-checkin-requested.json` — NEW-52
- `event-schemas/late-checkin-approved.json` — NEW-52
- `event-schemas/late-checkin-rejected.json` — NEW-52
- `services/notification-bot/tests/integration/conftest.py` — NEW-53
- `.env.prod.example` обновление — NEW-51 (`TELEGRAM_BOT_USERNAME`)
- `docs/future-ideas.md` — раздел «Auth API contract-first refactor
  (v0.1)» с обоснованием откладывания 01 P0-1

---

_Никаких «why», «motivation», «background» — это в 99-executive-summary.md
и OWNER-ANSWERS.md (Q-P0-4..6, Q-P0-1/2 в 14, Q-P0-2 в 12). Здесь только
WHAT и DONE-критерии._
