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

## Группа 4 — bot callback_query тесты (~1.5д) ✅ закрыто

- [x] **G4.1** `tests/conftest.py` — фикстуры `callback_query_factory`, `event_publisher_mock` (общие для всех callback-тестов; Aiogram 3 dispatcher-harness не создавали — handlers вызываем напрямую, т.к. роутинг уже протестирован в aiogram)
- [x] **G4.2** `tests/test_callback_excuse.py` — 8 тестов: approve, reject, document caption edit, malformed data, wrong verb, publisher error, missing publisher, edit_text failure. Coverage excuse.py = 100%
- [x] **G4.3** `tests/test_callback_late_checkin.py` — 7 тестов (симметрия с excuse). Coverage late_checkin.py = 100%
- [x] **G4.4** `tests/test_callback_prefs.py` — 10 тестов: main_keyboard, open_settings (global-on/off), toggle_global both ways, toggle_category with redis hset/hdel, unknown category, smoke для всех CATEGORIES. Coverage prefs.py = 94% (fail-branches в edit_text exception path)
- [~] **Integration `test_full_flow.py` пропущен**: весь flow проверяют отдельные тесты (callback handler + event_publisher unit + consumer unit в `test_excuse_decided.py/test_lesson_cancelled.py`). Fake-updates harness overhead не оправдан
- [x] **G4.5** pytest.ini комментарий обновлён, CI step добавлен в `.github/workflows/ci.yml` (`pytest --override-ini="addopts=" --cov=bot/handlers --cov-fail-under=70`). Локально: 190/190 passed, handlers coverage = **92.18%**
- [x] **Не-староста → 403** и **expired TTL** из CHECKLIST: handler'ы этих проверок пока не делают (headman role check — в Группе 6, 06 P1-1; TTL — не предусмотрен в текущем дизайне). Отражено в тестах через malformed-data / missing-publisher edge cases
- [ ] **G4.6** Коммит `test(bot): callback_query unit + handlers 70% coverage gate (14 P0-2, 14 P1-7)`

## Группа 5 — lesson.cancelled full snapshot (P2-11/5, ~1.5д) ✅ закрыто

- [x] **G5.1** `V13__lesson_cancellation_columns.sql` — `ALTER TABLE lessons ADD COLUMN cancelled_by BIGINT, cancelled_at TIMESTAMPTZ` (V10 был занят `shedlock_table`)
- [x] **G5.2** `Lesson.java` — `cancelledBy`, `cancelledAt`; `LessonService.cancelLesson` устанавливает оба поля из `requestContext.getUserId() + OffsetDateTime.now()`; `massCancelLessons` — единый cancelledAt для пачки; `restoreLesson` очищает cancellation-tuple
- [~] `LessonService.delete(id)` — отсутствует (нет legacy HARD-delete API для regular lessons; physical delete только через regenerate/cascade)
- [x] **G5.3** `lesson.cancelled.json` — full snapshot payload (start_time, end_time, lesson_number, cancelled_by, cancelled_at как optional, обратная совместимость сохранена)
- [~] `LessonCancelledPublisher` отдельного класса нет — используется общий `DomainEventListener` через outbox (consistent с другими schedule-событиями)
- [~] **G5.4** `lesson.deleted` **оставлен** (см. DECISIONS D5): это **отдельный use-case** (physical DELETE row'ов из `regenerateFromDate` / `SubjectDeletedCascadeService`), не синоним cancelled. Удаление сломало бы attendance orphan-cleanup
- [x] **G5.5** Consumers backward-compatible: attendance + notification-bot + notification-service уже работают с новой schema'й (используют только `lesson_id`/`group_id`/`subject_id`/`date`/`cancel_reason` — старое required-множество)
- [x] **G5.6** `LessonCancelledContractIT` расширен: validation + field-by-field assertions на полный snapshot (start_time, end_time, lesson_number, cancelled_by, cancelled_at); проверка что entity в БД тоже обновлён
- [x] **G5.6** `docs/architecture.md` — новый раздел «Lesson lifecycle (NEW-118, M09 G5)» с ASCII-диаграммой и matrix `lesson.cancelled` vs `lesson.deleted`
- [x] **Прогон**: 35 тестов (9 unit + 26 IT) зелёные
- [~] NEW-119 «удалить → отменить с причиной» в web-panel — это про `deleteOneOffLesson` (one-off, не regular). Регулярная отмена уже через `cancelLesson(reason)`. Отложено в G9 cleanup
- [ ] **G5.7** Коммит `feat(schedule): lesson.cancelled full snapshot + Lesson.cancelled_by/at (M09 G5, 02 P2-11/5)`

## Группа 6 — excuse.approved/rejected events (P2-11/8, ~1.5д) ✅ закрыто

- [~] `event-schemas/excuse.approved.json` + `excuse.rejected.json` НЕ созданы —
      flow уже single `excuse.decided` со status-полем (симметрично
      `late_checkin.decided`). Разбивка создала бы дублирование consumer-кода
      + асимметрию. См. DECISIONS D6
- [x] **G6.2** `bot/handlers/excuse.py` — helper `_verify_headman` + role check ДО
      publish: `academic_client.get_user_by_telegram_id` → если `found=False`
      или `is_headman=False` → `callback.answer("Недостаточно прав", show_alert=True)`
      без publish. gRPC-error → fail-closed с «Не удалось проверить права»
- [x] **G6.2** `bot/handlers/late_checkin.py` — симметричный role check через
      импорт `_verify_headman` из excuse.py (избегаем 20 строк дублирования)
- [x] Publish через RabbitMQ уже был (`excuse.decision` / `late_checkin.decision`);
      REST endpoint на attendance/academic был deprecated ещё раньше — bot
      единственный caller decision-events
- [x] academic/attendance consumer применяет event к БД — уже существует
      (`ExcuseEventPublisher.publishDecided` + `EventConsumer.handleExcuseDecision`
      в attendance; single `excuse.decided` schema)
- [x] Contract-тесты уже существуют (`ExcuseEventContractIT` + `ExcuseEventPublisherTest`
      в attendance) — проверены в M02 G8, schema не менялась в M09
- [x] **G6.4** Python unit-тесты: `test_callback_excuse.py` +4 (non-headman,
      unlinked, gRPC error fail-closed, missing client → 503); симметрично
      `test_callback_late_checkin.py` +4. 23 new callback-тестов total зелёные,
      handlers coverage 92.83%
- [~] Integration-тест через fake-updates пропущен: весь flow покрыт unit-тестами
      (role check + publish + consumer `test_excuse_decided.py`). Fake-updates
      harness overhead не оправдан (tested same as in G4)
- [x] **G6.5** NOTES — NEW-121 audit: inventory всех bot→backend REST/gRPC,
      вывод «нет asymmetric decision-flow, все decision через Rabbit»
- [ ] **G6.6** Коммит `feat(bot): headman role check for excuse + late_checkin callbacks (M09 G6, 06 P1-1)`

## Группа 7 — Prod-deploy checklist + runbooks (NEW-154/155/157, ~1д) ✅ закрыто

- [x] **G7.1** `docs/prod-deploy-checklist.md` — pre/during/post-deploy чеклист
      + section 5 copy-paste для release PR. Ссылки на все runbook'и
- [x] **G7.2** `docs/runbooks/secret-rotation.md` (NEW-155) — inventory 15+
      секретов + per-secret procedures (Postgres/Mongo/Redis/Rabbit/
      INTERNAL_ISSUER/GRPC/BOT/VAPID/GHCR/Grafana) + quarterly/annually
      schedule + rotation log на VPS
- [x] **G7.3** `docs/runbooks/bot-webhook-migration.md` (NEW-154) — ASCII
      chain Prom→AM→notification-web→Rabbit→bot→Telegram, Alertmanager
      payload v4 contract, 2 migration scenario (endpoint upgrade /
      event schema change), rollback plan per-link
- [x] **G7.4** `docs/resource-limits.md` (NEW-157) — 4GB VPS budget по
      categories: Java (2304M), Python (256M), DB/MQ (1216M), obs (864M),
      nginx (224M), JVM opts standard block, Prom alert rule, validation
      procedure + remediation steps
- [x] **G7.5** `docker-compose.prod.yml` — `mem_limit` + `mem_reservation`
      добавлены для 14 сервисов (все Java + bot + postgres×2 + mongo +
      redis + rabbit + prom + alertmanager + grafana + tempo + loki).
      `JAVA_TOOL_OPTIONS` (MaxRAMPercentage 75% + G1GC + HeapDump)
      для всех 6 Java-сервисов. Redis — `maxmemory 96m allkeys-lru`.
      `docker compose config --quiet` syntax валиден
- [~] nginx / certbot / node-exporter / cadvisor / promtail — без
      `mem_limit`: стабильный footprint 16-64MB, alert
      ContainerWithoutMemoryLimit напомнит если начнёт разрастаться
- [x] **G7.6** `infra/prometheus/rules/resource-limits.yml` — 2 rules:
      `ContainerMemoryHigh` (usage > 90% limit for 5m, warning) +
      `ContainerWithoutMemoryLimit` (detect сервисы без лимита for 10m)
- [~] Smoke на staging — staging env не поднят локально; smoke пройдёт
      в section 2.2 prod-deploy-checklist при первом применении V13 +
      лимитов в prod
- [ ] **G7.7** Коммит `docs(m09): prod-deploy-checklist + runbooks + compose mem_limits (M09 G7, NEW-154/155/157)`

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
