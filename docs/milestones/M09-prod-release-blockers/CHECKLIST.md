# M09 Checklist

Атомарные задачи в порядке выполнения. Отмечаются `[x]` после коммита.

## Группа 1 — Quick wins (~2ч) ✅ 2026-04-23

- [x] `OtpService.verifyOtp` — `String.equals` → `MessageDigest.isEqual(stored.getBytes(UTF_8), request.code().getBytes(UTF_8))` (01 P0-5, ~5 мин) — `2996652`
- [x] Unit-тест `OtpServiceTest#verifyOtp_constantTimeCompare` — source-structural guard + 3 поведенческих теста — `2996652`
- [x] `AttendanceService.cleanupOrphans` + `@PostConstruct` удалены (04 P0-6) — `ebed02b`
- [x] `AttendanceApplication` / `AttendanceIndexInitializer` — `ScheduleGrpcClient` зависимость убрана из bean — `ebed02b`
- [x] Integration-тест: старт attendance-service с недоступным schedule-service — `StartupOrphanCleanupRemovedIT` — `ebed02b`
- [x] `frontends/landing/dist/index.html` — 4 CTA переведены на `https://t.me/ruttrack_bot/ruttrack` (12 P0-2) — `e751040`
- [~] `.env.prod.example TELEGRAM_BOT_USERNAME` (NEW-51) — **перенесено в Группу 7** (deep-link hardcoded в landing, env не требуется в G1)
- [~] Smoke-check на dev: клик по кнопке лендинга — **перенесено на staging** в Группе 7 (локального landing dev-окружения нет)
- [x] 3 атомарных коммита (вместо одного): `2996652` + `ebed02b` + `e751040`

## Группа 2 — OTP через RabbitMQ (~1.5д) ✅ закрыто

- [x] `event-schemas/otp.requested.json` — JSON Schema `{event_version, occurred_at, trace_id, source, telegram_id, code, ttl_seconds}` (08 P0-2) — `3d6dfd1`
- [x] `auth-service/.../event/OtpRequestedEvent.java` — class extends `auth.event.DomainEvent` (shared-events envelope, Payload record) — `3d6dfd1`
- [~] `OtpRequestedPublisher` **не создан** — используется существующий `DomainEventListener` (DECISIONS D4: OTP эфемерен в Redis, shared-outbox ломает security-model) — `807b1f2`
- [x] `AuthController.requestOtp` — `204 No Content`, `OtpCodeResponse` DTO удалён, `OtpService.requestOtp` сигнатура `String → void` — `807b1f2`
- [x] `docs/openapi/auth.json` обновлён + `frontends/{pwa,web-panel}/.../generated/auth.types.ts` регенерированы через `npm run generate:types:offline` — `807b1f2`
- [x] `notification-bot/bot/notifications/otp_requested.py` — handler + регистрация в `event_dispatcher.py` (с `event_type: "otp.requested"`) — `b851221`
- [x] `/login` handler рефакторен: `store_pending_user_msg` → `auth.request_otp()` (204); ответ пользователю отправляет consumer — `b851221`
- [x] `AuthHttpClient.request_otp`: `str → None`; `OtpMessageTracker` + `store_pending_user_msg`/`finalize_with_bot_msg` — `b851221`
- [x] Contract-тест `OtpRequestedContractTest.java` — publisher → JSON Schema validation (3 теста: valid/missing-code/non-6digit); добавлен `EventSchemaValidator` (auth-scope) — `70bd2db`
- [~] Python contract-тест **пропущен** — `jsonschema` не в bot deps, overhead добавления не оправдан: `tests/test_otp_requested.py` (4 теста) покрывает consumer payload поведение; Java publisher-side уже валидирует envelope через networknt
- [x] **G2.6** — `AuthOtpFlowIT` зелёный — `@Disabled` снят; root cause: `@ConditionalOnBean(ConnectionFactory.class)` на user `@Configuration` оценивался до autoconfig и давал false, поэтому наш JSON `RabbitTemplate` не создавался, а default-autoconfig с `SimpleMessageConverter` ломал JSON-контракт. Fix: убран `@ConditionalOnBean`, listener регистрируется `@Bean`'ом в `RabbitConfig`, `application-test.yml` больше не исключает `RabbitAutoConfiguration`. Все 84 auth-теста (test+integrationTest) зелёные.
- [x] **G2.7** — `docs/architecture.md` раздел 3.2 обновлён: endpoint `/auth/otp/request` → 204, добавлена секция «OTP flow (M09 G2 · 08 P0-2, event-driven)» с ASCII-диаграммой; раздел 3.5 bot обновлён — consumer `otp.requested`/`otp.verified`
- [ ] **G2 финальный коммит** — `feat(auth): AuthOtpFlowIT + architecture.md OTP flow (M09 G2.6+G2.7)` — закрывает группу

**Стабильное покрытие на 2026-04-23 (без G2.6):**
- OtpIT (integrationTest): `otpRequest_withValidTelegramId_returns204NoBody` — 204 + Redis code
- OtpRequestedContractTest (test): 3 теста — schema валидность publisher envelope
- pytest `tests/test_otp_requested.py`: 4 теста — consumer payload
- pytest `tests/test_event_dispatcher.py`: `otp.requested` в handler registry
- pytest `tests/test_login_handler.py`: переписан под новую семантику
- **Всего: 161 + 4 = 165 pytest зелёные; все auth `test` задачи зелёные**

## Группа 3 — latecheckin тесты (~1.5д) ✅ закрыто

- [x] **G3.1** `LateCheckinServiceTest.java` — 16 unit-тестов (createRequest happy + 5 edge; listPendingForHeadman 3 пути; applyDecisionFromWeb approve/reject/guards/notFound; applyDecision idempotent/notFound)
- [x] **G3.2** `LateCheckinControllerIT.java` — 5 MockMvc + Testcontainers (POST create → 201; GET pending headman vs не-headman → 200/403; POST decision approve → 200 + attendance marked; non-headman → 403)
- [~] **G3.3** event-schemas уже существуют (`late_checkin.requested.json`, `late_checkin.decided.json`, `late_checkin.decision.json`) — CHECKLIST изначально использовал имена `approved/rejected`, в коде это единое событие `decided` со status=approved|rejected
- [x] **G3.4** `LateCheckinEventContractTest.java` — 3 publisher-side теста (requested + decided.approved + decided.rejected) против JSON Schema через `EventSchemaValidator`. Unit-тест с in-memory `CapturingOutbox` — без Spring context / testcontainers
- [x] **G3.5** `build.gradle.kts` attendance-app — `isEnabled=false` снят, `latecheckin/**` 70% LINE gate активен, `./gradlew :services:attendance-service:attendance-app:check` зелёный
- [ ] **G3.6** Коммит `test(attendance): latecheckin unit + IT + contract + jacoco 70% gate (14 P0-1)`

## Группа 4 — bot callback_query тесты (~1.5д)

- [ ] `notification-bot/tests/integration/conftest.py` — фикстуры `dispatcher`, `bot`, `callback_query_factory` (Aiogram 3 test harness, NEW-53)
- [ ] `notification-bot/tests/test_callback_excuse.py` — unit: approve, reject, не-староста → 403, неверный callback_data
- [ ] `notification-bot/tests/test_callback_late_checkin.py` — unit: approve, reject, истёкший TTL
- [ ] `notification-bot/tests/test_callback_prefs.py` — unit: toggle каждой preference, сохранение в Redis
- [ ] `notification-bot/tests/integration/test_full_flow.py` — 3 сценария через fake-updates (excuse approve, late_checkin reject, prefs toggle)
- [ ] `pyproject.toml` / `pytest.ini` — `pytest-cov` gate `handlers/` ≥ 70%
- [ ] CI: добавить `pytest --cov=handlers --cov-fail-under=70` в workflow бота
- [ ] Коммит `test(bot): callback_query unit + integration (14 P0-2, 14 P1-7)`

## Группа 5 — lesson.cancelled full snapshot (P2-11/5, ~1.5д)

- [ ] Миграция `schedule-service/.../V{N}__lesson_cancellation_columns.sql`:
      `ALTER TABLE lessons ADD COLUMN cancelled_by BIGINT,
       ADD COLUMN cancelled_at TIMESTAMPTZ`
- [ ] `Lesson.java` entity — добавить поля `cancelledBy`, `cancelledAt`
- [ ] `LessonService.cancel(id, reason, userId)` — устанавливает оба
      поля + статус `cancelled`
- [ ] Удалить `LessonService.delete(id)` (если есть) или deprecate с
      delegation к `cancel(id, "legacy-delete", systemUserId)`
- [ ] `event-schemas/lesson.cancelled.json` — full snapshot payload
      (lesson_id, group_id, subject_id, date, start_time, end_time,
      lesson_number, reason, cancelled_by, cancelled_at +
      event_version/trace_id/occurred_at)
- [ ] `LessonCancelledPublisher` — публикует через shared-outbox
- [ ] Удалить `event-schemas/lesson.deleted.json` + publisher кода
- [ ] Consumer update: `attendance-service/.../LessonCancelledConsumer`
      подписан на новое событие
- [ ] Consumer update: `notification-web/.../LessonCancelledConsumer`
- [ ] Contract-тест `LessonCancelledContractIT` (publisher-side
      schema validation)
- [ ] Integration-тест: create lesson → cancel(reason, userId) →
      assert lesson.status='cancelled', cancelled_by и cancelled_at
      заполнены, event published
- [ ] Grep проверка: `lesson.deleted` и `LessonDeletedEvent` удалены
      из кода
- [ ] `docs/architecture.md` — раздел «Lesson lifecycle» (NEW-118):
      диаграмма planned → active → closed; planned/active → cancelled
- [ ] Коммит `feat(schedule): lesson.cancelled full snapshot + удаление lesson.deleted (02 P2-11/5)`

## Группа 6 — excuse.approved/rejected events (P2-11/8, ~1.5д)

- [ ] `event-schemas/excuse.approved.json` — full snapshot
      (excuse_id, student_id, group_id, lesson_ids, decided_by,
      reason, decided_at + event_version/trace_id/occurred_at)
- [ ] `event-schemas/excuse.rejected.json` — аналогично
- [ ] `notification-bot/app/handlers/excuse_callback.py` — перед
      publish проверка `is_headman` через
      `academic_client.get_user_by_telegram_id` → если не headman:
      `callback.answer("Недостаточно прав", show_alert=True)` без publish
      (закрывает 06 P1-1)
- [ ] `notification-bot/app/handlers/excuse_callback.py` — publish
      события через aio_pika (вместо REST call на academic-service)
- [ ] Если существовал REST endpoint `POST /api/excuse/{id}/decision` —
      deprecate/удалить после подтверждения что bot единственный caller
- [ ] academic/attendance consumer: `ExcuseDecisionConsumer` применяет
      event к БД (excuse status + attendance marks)
- [ ] Contract-тесты (pattern QD3) для обоих событий
- [ ] Python unit-тест: student-role → callback.answer + НЕ publish
- [ ] Python unit-тест: headman-role → publish event с корректным payload
- [ ] Integration-тест через fake-updates: headman approves → event in
      Rabbit → academic DB updated
- [ ] Audit записать в NOTES.md: какие ещё asymmetric flows (bot publishes
      through REST vs event) существуют (NEW-121)
- [ ] Коммит `feat(bot): excuse.approved/rejected events + headman role check (02 P2-11/8, 06 P1-1)`

## Группа 7 — Prod-deploy checklist + runbooks (NEW-154/155/157, ~1д)

- [ ] `docs/prod-deploy-checklist.md` — оглавление: pre-deploy checks,
      during-deploy monitoring, post-deploy validation; ссылки на
      конкретные runbook'и
- [ ] `docs/runbooks/secret-rotation.md` (NEW-155): quarterly
      procedure — POSTGRES_*, BOT_TOKEN, GHCR_TOKEN, VAPID_PRIVATE_KEY,
      JWT_SECRET; пошаговые инструкции per-secret + downtime window
      + validation steps
- [ ] `docs/runbooks/bot-webhook-migration.md` (NEW-154):
      Alertmanager payload schema (`{status, receiver, alerts[], ...}`);
      `/internal/alert` migration steps; rollback plan
- [ ] `docs/resource-limits.md` (NEW-157): VPS 4GB budget таблица
      per-service (academic/schedule/attendance 512M, notification-web/
      gateway/bot 256M, auth 256M); JVM opts per-service; Prometheus
      alert rule (container_memory_usage_bytes /
      container_spec_memory_limit_bytes > 0.9 for 5m)
- [ ] `docker-compose.prod.yml` — добавить `mem_limit`,
      `mem_reservation`, `restart: unless-stopped` per-service
- [ ] `docker-compose.prod.yml` — JVM opts
      `-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0`
      per Java service
- [ ] Prometheus alert rule commit в `infra/prometheus/alerts/` или
      аналог
- [ ] Smoke на staging (если есть): мем-limits применяются, сервисы
      стартуют без OOMKilled
- [ ] Коммит `docs(m09): prod-deploy-checklist + secret-rotation + resource-limits (NEW-154/155/157)`

## Группа 8 — Docs + cleanup (~0.5д)

- [ ] `docs/admin-scripts.md` — новый runbook (NEW-33): cleanup orphans (mongosh), backfill templates, recovery (3-5 скриптов)
- [ ] `docs/future-ideas.md` — раздел «Auth API contract-first refactor (v0.1)» с обоснованием отложения 01 P0-1
- [ ] `docs/future-ideas.md` — раздел «P2-2/2 auth-service OpenAPI (v0.1)»
      связанный с auth-api-contract refactor
- [ ] `CLAUDE.md` — обновить «Правила кодирования → Contract-first»: уточнить что `api-gateway` не требует контракта (он прокси), и что `auth-service` получит `auth-api-contract` в v0.1
- [ ] `docs/milestones/README.md` — статус M09 → ✅ + дата
- [ ] `CLAUDE.md` v0.0.0 Milestones table — статус M09 + упомянуть
      P2-11/5 / P2-11/8 в описании
- [ ] `CHANGELOG.md [Unreleased]` — секция M09 с полным scope
- [ ] Коммит `docs(m09): admin-scripts + future-ideas + CLAUDE contract-first уточнение`

## Группа 9 — Audit (~0.5д)

- [ ] Полный `./gradlew build` + `pytest notification-bot/` — всё зелёное
- [ ] `security-auditor` агент на diff M09 — фокус: OTP event flow (кто читает код из Rabbit? TTL? DLQ на bot unavailable?), constant-time verify корректность, race при параллельном request OTP, headman role check coverage
- [ ] `bug-hunter` агент — фокус: outbox publisher retry для `otp.requested`/`lesson.cancelled`/`excuse.*` (дубли → пользователь получит 2 разных кода?), Aiogram fake-updates edge cases, lesson.deleted grep на orphan references
- [ ] Hot-patches → отдельный коммит
- [ ] `PLAN.md` → Post-mortem секция
- [ ] `docs/milestones/README.md` → M09 ✅ + дата
- [ ] `CLAUDE.md` → M09 статус ✅
- [ ] Тег `git tag v0.0.0` или `v0.0.0-rc.1` (локально)
- [ ] Hand-off для release-candidate в `NEXT-SESSION.md`

---

_Если задача превращается в 6+ часов работы — разрежь её. Порядок групп
важен: Группа 1 безопасна для параллельного мёрджа (5 quick wins),
Группа 2 меняет API (breaking change для фронта — координировать с M07),
Группы 3-4 — тесты (независимы), Группы 5-6 — закрытие._
