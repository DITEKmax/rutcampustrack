# M09 Checklist

Атомарные задачи в порядке выполнения. Отмечаются `[x]` после коммита.

## Группа 1 — Quick wins (~2ч)

- [ ] `OtpService.verifyOtp` — `String.equals` → `MessageDigest.isEqual(stored.getBytes(UTF_8), request.code().getBytes(UTF_8))` (01 P0-5, ~5 мин)
- [ ] Unit-тест `OtpServiceTest#verifyOtp_constantTimeCompare` — проверка что путь не ветвится по содержимому кода (mockito + assert)
- [ ] `AttendanceService.cleanupOrphans` + `@PostConstruct` удалены (04 P0-6)
- [ ] `AttendanceApplication` / `AttendanceIndexInitializer` — если gRPC-вызов `ListLessons` остался только для cleanup'а, удалить
- [ ] Integration-тест: старт attendance-service с недоступным schedule-service → контейнер up, существующие marks не удалены
- [ ] `frontends/landing/index.html` — `<a href="/login">` → `<a href="https://t.me/<bot_username>">` (12 P0-2)
- [ ] `.env.prod.example` — добавить `TELEGRAM_BOT_USERNAME=` + комментарий (NEW-51)
- [ ] Smoke-check на dev: клик по кнопке лендинга открывает Telegram
- [ ] Коммит `fix(m09): quick wins — MessageDigest + cleanupOrphans + landing deep-link`

## Группа 2 — OTP через RabbitMQ (~1.5д)

- [ ] `event-schemas/otp-requested.json` — JSON Schema {`event_version`, `occurred_at`, `trace_id`, `source`, `telegram_id`, `code`, `ttl_seconds`} (08 P0-2)
- [ ] `auth-app/.../event/OtpRequestedEvent.java` — record, extends `DomainEvent` (shared-events base из M01)
- [ ] `auth-app/.../event/OtpRequestedPublisher.java` — публикует в fanout exchange `otp.events` через shared-outbox (M02 OutboxStorage)
- [ ] `AuthController.requestOtp` — убрать `code` из response DTO; возвращает `204 No Content` (или `{"delivery": "telegram"}` если нужен feedback UI)
- [ ] `AuthApiResponse` / OpenAPI spec — обновить контракт; фронтенд (PWA/web-panel) перестаёт читать `code` из body
- [ ] `notification-bot/app/consumers/otp_requested.py` — aio_pika consumer с `event_version` проверкой; отправляет код через `Bot.send_message(telegram_id, code)`
- [ ] `notification-bot/app/consumers/__init__.py` + `main.py` — регистрация консюмера
- [ ] Contract-тест `OtpRequestedContractTest.java` — публишер → валидация против JSON Schema (networknt json-schema-validator из M02)
- [ ] Contract-тест Python `tests/contract/test_otp_requested_consumer.py` — consumer-side schema validation
- [ ] Integration-тест `AuthOtpFlowIT` — `POST /auth/otp/request` → body не содержит `code` + outbox row → RabbitMQ message → bot получил (testcontainer Rabbit)
- [ ] Обновить `docs/architecture.md` — диаграмма OTP flow (old: HTTP body / new: event-driven)
- [ ] Коммит `feat(auth): OTP через RabbitMQ event (01 P0-4, 08 P0-2)`

## Группа 3 — latecheckin тесты (~1.5д)

- [ ] `attendance-app/.../latecheckin/LateCheckinServiceTest.java` — unit-тесты: create, approve (роль старосты), reject, listByGroup, listByUser; edge cases (student != owner, already approved, lesson closed, not headman)
- [ ] `attendance-app/.../latecheckin/LateCheckinControllerIT.java` — MockMvc + Testcontainers Mongo, happy-path для каждого endpoint'а
- [ ] `event-schemas/late-checkin-requested.json` (NEW-52)
- [ ] `event-schemas/late-checkin-approved.json` (NEW-52)
- [ ] `event-schemas/late-checkin-rejected.json` (NEW-52)
- [ ] `LateCheckinEventContractTest.java` — 3 publisher-side contract-теста
- [ ] `build.gradle.kts` attendance-app — jacoco report `latecheckin/**` ≥ 70% line, fail build при нарушении
- [ ] Коммит `test(attendance): latecheckin unit + IT + contract (14 P0-1)`

## Группа 4 — bot callback_query тесты (~1.5д)

- [ ] `notification-bot/tests/integration/conftest.py` — фикстуры `dispatcher`, `bot`, `callback_query_factory` (Aiogram 3 test harness, NEW-53)
- [ ] `notification-bot/tests/test_callback_excuse.py` — unit: approve, reject, не-староста → 403, неверный callback_data
- [ ] `notification-bot/tests/test_callback_late_checkin.py` — unit: approve, reject, истёкший TTL
- [ ] `notification-bot/tests/test_callback_prefs.py` — unit: toggle каждой preference, сохранение в Redis
- [ ] `notification-bot/tests/integration/test_full_flow.py` — 3 сценария через fake-updates (excuse approve, late_checkin reject, prefs toggle)
- [ ] `pyproject.toml` / `pytest.ini` — `pytest-cov` gate `handlers/` ≥ 70%
- [ ] CI: добавить `pytest --cov=handlers --cov-fail-under=70` в workflow бота
- [ ] Коммит `test(bot): callback_query unit + integration (14 P0-2, 14 P1-7)`

## Группа 5 — Docs + cleanup (~0.5д)

- [ ] `docs/admin-scripts.md` — новый runbook (NEW-33): cleanup orphans (mongosh), backfill templates, recovery (3-5 скриптов)
- [ ] `docs/future-ideas.md` — раздел «Auth API contract-first refactor (v0.1)» с обоснованием отложения 01 P0-1
- [ ] `CLAUDE.md` — обновить «Правила кодирования → Contract-first»: уточнить что `api-gateway` не требует контракта (он прокси), и что `auth-service` получит `auth-api-contract` в v0.1
- [ ] `docs/milestones/README.md` — добавить строку M09 в таблицу milestones
- [ ] `CLAUDE.md` v0.0.0 Milestones table — добавить M09 строку
- [ ] `CHANGELOG.md [Unreleased]` — секция M09
- [ ] Коммит `docs(m09): admin-scripts + future-ideas + CLAUDE contract-first уточнение`

## Группа 6 — Audit (~0.5д)

- [ ] Полный `./gradlew build` + `pytest notification-bot/` — всё зелёное
- [ ] `security-auditor` агент на diff M09 — фокус: OTP event flow (кто читает код из Rabbit? TTL? DLQ на bot unavailable?), constant-time verify корректность, race при параллельном request OTP
- [ ] `bug-hunter` агент — фокус: outbox publisher retry для `otp.requested` (дубли → student получит 2 разных кода?), Aiogram fake-updates edge cases
- [ ] Hot-patches → отдельный коммит
- [ ] `PLAN.md` → Post-mortem секция
- [ ] `docs/milestones/README.md` → M09 ✅ + дата
- [ ] `CLAUDE.md` → M09 статус ✅
- [ ] Тег `git tag v0.0.0-alpha.{N+1}` (локально)
- [ ] Hand-off для release-candidate в `NEXT-SESSION.md`

---

_Если задача превращается в 6+ часов работы — разрежь её. Порядок групп
важен: Группа 1 безопасна для параллельного мёрджа (5 quick wins),
Группа 2 меняет API (breaking change для фронта — координировать с M07),
Группы 3-4 — тесты (независимы), Группы 5-6 — закрытие._
