# M09 — Prod Release Blockers (Фаза 3 Точечные P0 + event unification)

**Статус:** ⬜ не начат
**Старт / финиш:** — / —
**Estimate:** 7-8 человеко-дней

---

## Scope

Закрывает остаток **Фазы 3 «Точечные P0»** из `99-executive-summary.md`,
подвисшие события P2-11/5 и P2-11/8 из M02 defer'ов, и создаёт
`docs/prod-deploy-checklist.md` с сопутствующими runbook'ами
(NEW-154, NEW-155, NEW-157). Минимум, необходимый для выхода v0.0.0
в прод.

**Исключено (уже покрыто):**
- `10 P0-3` CSP nginx web-panel + NEW-55 PWA vhost — **M07** scope.
- `01 P0-1` создание `auth-api-contract` — **отложен в v0.1 backlog**
  (переносится в `docs/future-ideas.md` как Группа 6 M09).
- `nginx/postgres/mongo/redis/rabbitmq digest-pin` — **M08** Группа 11.

**Включено:**

### Точечные P0 (исходный scope M09)
- **01 P0-4** (Q-P0-4) — OTP через RabbitMQ event вместо HTTP-body +
  схема `otp.requested` (08 P0-2 cascade)
- **01 P0-5** (Q-P0-5) — `MessageDigest.isEqual` в `OtpService.verifyOtp`
- **04 P0-6** (Q-P0-6) — удалить `@PostConstruct cleanupOrphans` +
  `docs/admin-scripts.md` шаблон
- **12 P0-2** (Q-P0-2 landing) — deep-link `https://t.me/<bot_username>`
- **14 P0-1** (Q-P0-1 tests) — unit + integration + contract-тесты для
  `attendance-service/latecheckin/`
- **14 P0-2** (Q-P0-2 tests) — pytest + Aiogram fake-updates для
  `notification-bot/handlers/` callback_query

### Event unification (из M02 defer'ов)
- **02 P2-11/5** — `lesson.cancelled` full snapshot event:
  - Миграция `V{N}__lesson_cancellation_columns.sql`: добавить
    `cancelled_by BIGINT` + `cancelled_at TIMESTAMPTZ` в `lessons`
    (verified: текущая schema имеет `cancel_reason`, `status='cancelled'`
    в enum, но `cancelled_by`/`cancelled_at` отсутствуют)
  - `LessonService.cancel(id, reason, userId)` устанавливает оба поля
  - Удаление `lesson.deleted` event + publisher/consumer кода
  - `event-schemas/lesson.cancelled.json` — full snapshot payload
    (lesson_id, group_id, subject_id, date, start_time, end_time,
    lesson_number, reason, cancelled_at, cancelled_by +
    event_version/trace_id/occurred_at)
  - Consumer update: attendance-service + notification-web подписываются
    на `lesson.cancelled`
  - 03 P1-9 AUTO-RESOLVED через этот пункт
  - NEW-118 — `docs/architecture.md` раздел «Lesson lifecycle»
  - NEW-119 — UI в web-panel: «удалить» → «отменить с причиной»
    (интеграция с QC4 ConfirmWithReasonDialog из M07)
- **02 P2-11/8** — `excuse.approved/rejected` events (унификация
  с late_checkin pattern):
  - `event-schemas/excuse.approved.json` + `excuse.rejected.json`
    с full snapshot (excuse_id, student_id, group_id, lesson_ids,
    decided_by, reason, decided_at + event_version/trace_id/occurred_at)
  - Bot handler мигрирует с REST excuse-decision call на event publish
    (aio-pika)
  - academic/attendance consumer применяет event и публикует
    downstream `excuse.decided` если нужно
  - **06 P1-1 fix** — bot handler перед publish проверяет `is_headman`
    роль через `academic_client.get_user_by_telegram_id`; student/
    невалидный role → `callback.answer("Недостаточно прав",
    show_alert=True)` без publish
  - Contract-тесты (pattern QD3)
  - NEW-121 — audit asymmetric flow (bot publishes through REST vs event)

### Supporting artifacts (из Точечных P0)
- **NEW-33** — `docs/admin-scripts.md` (шаблоны разовых админ-задач)
- **NEW-51** — документация `<bot_username>` в `.env.prod.example`
- **NEW-52** — `event-schemas/late-checkin-{requested,approved,rejected}.json`
- **NEW-53** — `notification-bot/tests/integration/conftest.py` фикстуры

### Prod-deploy checklist (из M06 defer'ов)
- **`docs/prod-deploy-checklist.md`** (NEW) — оглавление со списком
  runbook'ов перед каждым деплоем
- **NEW-155** `docs/runbooks/secret-rotation.md` — quarterly
  процедура: rotate POSTGRES_*, BOT_TOKEN, GHCR_TOKEN, VAPID_PRIVATE_KEY,
  JWT_SECRET; downtime ~2 мин per rolling restart
- **NEW-154** `docs/runbooks/bot-webhook-migration.md` —
  `/internal/alert` endpoint migration на Alertmanager payload schema
  (`{status, receiver, alerts[], groupLabels, commonLabels, externalURL}`),
  связан с QA4+NEW-62
- **NEW-157** `docs/resource-limits.md` — VPS 4GB memory budget
  per-service, `-XX:MaxRAMPercentage=75.0`, `-XX:InitialRAMPercentage=50.0`,
  `restart: unless-stopped`, Prometheus alert
  `container_memory_usage_bytes / container_spec_memory_limit_bytes > 0.9 for 5m`
- **13 P2-9/9** — `docker-compose.prod.yml` обновление с memory limits
  per-service (academic/schedule/attendance 512M, notification-web/
  gateway/bot 256M, auth 256M)

### Future-ideas backfill
- **01 P0-1** `auth-api-contract` refactor раздел — в `docs/future-ideas.md`
- **P2-2/2** auth-service OpenAPI — связан с 01 P0-1, тоже v0.1

## Модули / изменения

### Auth & OTP
- `services/auth-service/.../service/OtpService.java` — `MessageDigest.isEqual`
  вместо `equals`; `otp.requested` через RabbitMQ publisher; response
  `204 No Content`
- `event-schemas/otp-requested.json` — новая JSON Schema
- `services/notification-bot/app/consumers/otp_requested.py` — консюмер
  + отправка кода в Telegram

### Attendance & late_checkin
- `services/attendance-service/.../latecheckin/` — удалить
  `@PostConstruct cleanupOrphans`; удалить gRPC-вызов `ListLessons` если
  больше нигде не используется
- `services/attendance-service/.../latecheckin/LateCheckinServiceTest.java`
  + `LateCheckinControllerIT.java` + `LateCheckinEventContractTest.java`
- `event-schemas/late-checkin-{requested,approved,rejected}.json`
- `services/notification-bot/tests/test_callback_{excuse,late_checkin,prefs}.py`
  + `tests/integration/test_full_flow.py` + `tests/integration/conftest.py`

### Lesson cancellation (P2-11/5)
- `services/schedule-service/schedule-app/src/main/resources/db/migration/
  V{N}__lesson_cancellation_columns.sql` — ADD COLUMN cancelled_by,
  cancelled_at
- `services/schedule-service/.../service/LessonService.java` —
  `cancel(id, reason, userId)` семантика
- `services/schedule-service/.../domain/Lesson.java` — entity поля
- `event-schemas/lesson.cancelled.json` — NEW (full snapshot)
- Удалить `event-schemas/lesson.deleted.json` + publisher кода
- Consumer adapters: attendance + notification-web

### Excuse events unification (P2-11/8)
- `event-schemas/excuse.approved.json` + `excuse.rejected.json`
- `services/notification-bot/app/handlers/excuse_callback.py` —
  role check + event publish (вместо REST)
- academic/attendance consumer adapters

### Landing deep-link
- `frontends/landing/index.html` — `<a href="https://t.me/<bot_username>">`
  (через `VITE_TELEGRAM_BOT_USERNAME` env)

### Docs
- `docs/admin-scripts.md` — NEW-33 runbook
- `docs/prod-deploy-checklist.md` — NEW (оглавление с пре-деплой checklist)
- `docs/runbooks/secret-rotation.md` — NEW-155
- `docs/runbooks/bot-webhook-migration.md` — NEW-154
- `docs/resource-limits.md` — NEW-157
- `docs/architecture.md` — раздел «Lesson lifecycle» (NEW-118)
- `docs/future-ideas.md` — разделы «Auth API contract-first (v0.1)»
  + «P2-2/2 auth OpenAPI (v0.1)»

### Infra
- `.env.prod.example` — `TELEGRAM_BOT_USERNAME`, `SWAGGER_HTPASSWD`
  (если ещё нет)
- `docker-compose.prod.yml` — `mem_limit` per-service + JVM opts +
  `restart: unless-stopped`

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
- [ ] Coverage `latecheckin/` package ≥ 70% line (pilot gate; M08
      сохраняет как stricter override).
- [ ] Coverage `notification-bot/handlers/` ≥ 70% line.
- [ ] 3 event-schemas (`late_checkin.*`) закоммичены и валидируют
      publisher в `LateCheckinService.{create,approve,reject}`.
- [ ] Миграция lesson add columns применена; `LessonService.cancel`
      устанавливает `cancelled_by`/`cancelled_at`; event
      `lesson.cancelled` публикуется с full snapshot.
- [ ] `lesson.deleted` event и код удалены полностью (grep empty).
- [ ] `excuse.approved` / `excuse.rejected` events публикуются
      бот-handler'ом; REST excuse-decision удалён; contract-тесты
      валидируют schemas.
- [ ] Bot excuse callback проверяет `is_headman` перед publish
      (06 P1-1 fix); unit-тест с student-role → rejected.
- [ ] `docs/prod-deploy-checklist.md` — оглавление + checklist
      перед каждым деплоем.
- [ ] `docs/runbooks/secret-rotation.md` — quarterly procedure с
      пошаговым списком.
- [ ] `docs/runbooks/bot-webhook-migration.md` — Alertmanager payload
      schema + `/internal/alert` migration.
- [ ] `docs/resource-limits.md` — memory budget таблица +
      Prometheus alert rule.
- [ ] `docker-compose.prod.yml` имеет `mem_limit` per-service +
      `restart: unless-stopped`; JVM opts `-XX:MaxRAMPercentage=75.0`.
- [ ] `docs/future-ideas.md` содержит разделы про auth-api-contract +
      P2-2/2.
- [ ] Post-mortem секция в PLAN.md, tag `v0.0.0` или `v0.0.0-rc.1`.

## Dependencies

- **Блокируется:** M03a (Internal JWT cascade). ✅ готово.
- **Блокируется:** M02 (shared-outbox для публикации otp.requested,
  lesson.cancelled, excuse.*). ✅ готово.
- **Блокирует:** v0.0.0 release tag — финальный блокер.
- **Parallel safe:** M06 ✅, M07, M08, M10, M11. Coverage pilot 70%
  для latecheckin/ + handlers/ активируется до M08 global gate;
  M08 сохраняет как stricter override.

## Artifacts

- `docs/admin-scripts.md` — NEW-33 runbook
- `docs/prod-deploy-checklist.md` — NEW (оглавление)
- `docs/runbooks/secret-rotation.md` — NEW-155
- `docs/runbooks/bot-webhook-migration.md` — NEW-154
- `docs/resource-limits.md` — NEW-157
- `docs/architecture.md` — «Lesson lifecycle» раздел (NEW-118)
- `event-schemas/otp-requested.json` — 08 P0-2
- `event-schemas/late-checkin-{requested,approved,rejected}.json` — NEW-52
- `event-schemas/lesson.cancelled.json` — P2-11/5
- `event-schemas/excuse.{approved,rejected}.json` — P2-11/8
- `services/notification-bot/tests/integration/conftest.py` — NEW-53
- `.env.prod.example` обновление — NEW-51, SWAGGER_HTPASSWD
- `docker-compose.prod.yml` — mem_limit + JVM opts per-service
- `docs/future-ideas.md` — «Auth API contract-first (v0.1)» +
  «P2-2/2 auth OpenAPI (v0.1)» разделы

---

_Никаких «why», «motivation», «background» — это в 99-executive-summary.md
и OWNER-ANSWERS.md (Q-P0-4..6, Q-P0-1/2 в 14, Q-P0-2 в 12, P2-11/5 2600-2628,
P2-11/8 2657-2682, NEW-154/155/157 4346-4445). Здесь только WHAT и DONE._
