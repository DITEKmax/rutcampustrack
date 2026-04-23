# Промпт для следующей сессии

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Этого достаточно —
Opus сам откроет файлы и поймёт где мы остановились.

---

**M09 Prod Release Blockers в процессе. Группа 1 закрыта, Группа 2 — 5/7 пунктов.**
Остался **G2.6 AuthOtpFlowIT debug** + **G2.7 architecture.md** + финальный коммит Группы 2.
Затем Группа 3 (latecheckin тесты).

Локальных коммитов ahead origin: **~61** (25 pre-M08 + 27 M08 + 4 M09 G1 + 5 M09 G2 WIP).
Tags `v0.0.0-alpha.2..9` локальные. Push отложен до явного go.

**Старт следующей сессии — дословно:**

> Читаю NEXT-SESSION → CHECKLIST M09 → NOTES M09 → AuthOtpFlowIT (coммит
> `d4ca2ca`). **Сначала G2.6 — debug AuthOtpFlowIT**: почему
> `rabbitTemplate.receive` возвращает null. По гипотезам из NOTES:
> #1 (`@ConditionalOnBean` порядок) → #3 (Jackson exception) → #4 (type
> mismatch). Применяю fix, снимаю `@Disabled`, запускаю
> `./gradlew :services:auth-service:integrationTest --tests "AuthOtpFlowIT"`.
> Когда зелёный — **G2.7**: обновляю `docs/architecture.md` раздел
> OTP flow + финальный docs(m09) коммит Группы 2. Далее — Группа 3.

---

## M09 Группа 2 — состояние на 2026-04-23

### Что закоммичено (5 коммитов)

| Commit | Scope |
|--------|-------|
| `3d6dfd1` | feat(events): `event-schemas/otp.requested.json` + `OtpRequestedEvent.java` |
| `807b1f2` | feat(auth): `OtpService.requestOtp()` → void, 204 No Content, `OtpCodeResponse` удалён, OpenAPI + frontend types |
| `b851221` | feat(bot): `otp_requested.py` handler + `/login` рефактор + `OtpMessageTracker.{store_pending_user_msg, finalize_with_bot_msg}` |
| `70bd2db` | test(auth): `OtpRequestedContractTest` (3 теста valid/missing-code/non-6digit) + `EventSchemaValidator` |
| `d4ca2ca` | **wip**(auth): `AuthOtpFlowIT` — помечен `@Disabled`, причина в NOTES |

### Что работает (покрытие)

- `./gradlew :services:auth-service:test` — все unit + OtpIT + OtpServiceTest + OtpRequestedContractTest — ✅ зелёные.
- `cd services/notification-bot && py -m pytest tests/` — 165 тестов зелёные (161 + 4 новых `test_otp_requested.py`).
- Контракт: `OtpIT.otpRequest_withValidTelegramId_returns204NoBody` проверяет 204 body + Redis-код. Schema validates publisher envelope. Bot consumer payload unit-tested.

### G2.6 AuthOtpFlowIT — **НЕ работает**, debug в следующей сессии

**Файл:** `services/auth-service/src/test/java/ru/rutcampustrack/auth/integration/AuthOtpFlowIT.java`

**Симптомы:**
```
204 ассерт → ✅
Redis-код ассерт → ✅  (6-digit в otp:123456789)
message = rabbitTemplate.receive(TEST_QUEUE, 500) × 16 iter = null → ❌
```

В логах `DomainEventListener.onDomainEvent` **отсутствуют** — похоже `@EventListener` не срабатывает.

**Гипотезы для debug (по приоритету, см. NOTES.md § G2.6):**

1. **`@ConditionalOnBean(RabbitTemplate.class)`** на `DomainEventListener` — bean-graph порядок. `RabbitTemplate` создаётся в `RabbitConfig.@Bean` (`@ConditionalOnBean(ConnectionFactory.class)`). Spring может оценить условие `DomainEventListener` **до** регистрации `RabbitTemplate` → listener пропустит. **Fix:** заменить на `@ConditionalOnProperty(prefix="spring.rabbitmq", name="host")`.

2. **Fanout exchange не declared до publish** — проверить `amqpAdmin.getExchangeProperties` в @BeforeEach.

3. **Jackson serialization ошибка в `DomainEventListener.onDomainEvent`** — `catch(AmqpException)` не ловит `JsonMappingException`. Расширить до `Exception`. Set log level `org.springframework.amqp=DEBUG`.

4. **`@EventListener` type mismatch** — параметр `auth.event.DomainEvent` vs `shared.events.DomainEvent`. Попробовать сменить на shared type.

5. **Альтернатива:** заменить `rabbitTemplate.receive` polling на `SimpleMessageListenerContainer` + `MessageListener` — более надёжный consumer pattern для тестов.

**Файл теста сейчас:** 179 строк, `@Disabled` повешен, test queue имеет уникальное имя с `nanoTime()` suffix, polling 8s (16 × 500ms). testcontainers: Postgres + Redis + RabbitMQ с `reuse=true`. `@TestPropertySource(properties = {"spring.autoconfigure.exclude="})` override'ит application-test.yml (где Rabbit отключён по умолчанию — не ломать другие auth IT).

**Команда для запуска:**
```bash
cd /c/Users/maksd/IntelliJIDEA/rutcampustrack
export JAVA_HOME="C:\\Users\\maksd\\.jdks\\ms-21.0.9"
./gradlew.bat :services:auth-service:integrationTest --tests "ru.rutcampustrack.auth.integration.AuthOtpFlowIT"
```

Смотри HTML report: `services/auth-service/build/reports/integrationTest/classes/ru.rutcampustrack.auth.integration.AuthOtpFlowIT.html`.

### G2.7 осталось

- `docs/architecture.md` — раздел OTP flow. Проверить grep `otp|OTP` что уже есть; обновить «старую» диаграмму (HTTP body с code) на новую (event-driven через Rabbit).
- Финальный коммит: `docs(m09): close Группу 2 — OTP event flow` (или `feat(auth): OTP через RabbitMQ event (01 P0-4, 08 P0-2)` если G2.7 объединить с architecture doc).

### Deviations от CHECKLIST (зафиксированы в DECISIONS.md)

- **D4** — OtpRequestedPublisher как отдельный класс НЕ создан. Используется существующий `DomainEventListener` (fire-and-forget). Причина: OTP-код эфемерен в Redis, shared-outbox требует Postgres persistence → нарушает security-model.
- **Python contract-тест пропущен** — `jsonschema` не в bot deps. Покрытие: Java publisher-side validation + pytest consumer payload.

---

## M09 Группа 1 — закрыто (2026-04-23)

- `2996652` fix(auth): OtpService MessageDigest.isEqual + OtpServiceTest (4 теста)
- `ebed02b` fix(attendance): удалить startup orphan-cleanup + StartupOrphanCleanupRemovedIT
- `e751040` fix(landing): 4 CTA → `https://t.me/ruttrack_bot/ruttrack`
- `0c465f1` docs(m09): CHECKLIST tick + NOTES

**Отклонения:**
- `.env.prod.example TELEGRAM_BOT_USERNAME` — перенесён в Группу 7 (deep-link hardcoded).
- Smoke-check landing — перенесён на staging.

---

## M09 scope — оставшиеся группы (из PLAN.md)

| # | Группа | Суть | Статус |
|---|--------|------|--------|
| G1 | Quick wins | MessageDigest + cleanupOrphans delete + landing deep-link | ✅ |
| G2 | OTP через RabbitMQ | 08 P0-2: убрать code из HTTP body, publisher → bot consumer | ⏳ 5/7 |
| G3 | latecheckin тесты | 14 P0-1: unit + IT + jacoco 70% gate активация | ⬜ |
| G4 | bot handlers тесты | Pytest coverage для handlers/ → 70% pilot | ⬜ |
| G5 | Event unification | lesson.cancelled / excuse.{approved,rejected} publishers | ⬜ |
| G6 | Prod-deploy-checklist | 13 P0-3: runbook для VPS release | ⬜ |
| G7 | Secret rotation | JWT keys + DB passwords + GRPC_SECRET runbook | ⬜ |
| G8 | Resource limits | compose.prod CPU/mem limits per service | ⬜ |
| G9 | Финализация | build + check + tag v0.0.0-alpha.10 + post-mortem | ⬜ |

## Правила (без изменений с M05-M08)

- **Русский язык** в отчётах / NOTES / ответах.
- **Ветка `dev`**. Push на `main`/`origin` — только с явного `go`.
- Не звать `gsd-*` агентов. `Explore` для «найти все X»,
  `bug-hunter` / `code-reviewer` / `security-auditor` — в G9 audit.
- Surprise → NOTES.md + спросить до продолжения.
- Micro-решение → DECISIONS.md.
- Закрыл пункт CHECKLIST → `[x]` через Edit (commit hash в описании).
- **Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — ложные.**
- `CHANGELOG.md [Unreleased]` обновляй при значимых изменениях (G9).

## M08 хэндовер-факты для M09 (актуальны)

### Coverage gate активен
- `./gradlew check` запускает `jacocoTestCoverageVerification` для всех модулей.
- Per-module ratchet floor в root `build.gradle.kts`.
- `attendance-app/build.gradle.kts` — placeholder-rule для `latecheckin.*` 70% LINE с `isEnabled = false`. M09 G3 активирует.
- PWA vitest threshold ratchet 38% lines / 47% functions.
- pytest-cov 50% gate (actual 70.5%). M09 G4 добавит pytest-cov 70% pilot для `bot/handlers/`.

### Supply-chain активен
- Digest-pin 13 images в `docker-compose.prod.yml`.
- deploy.yml: `sbom-sign` + verify. Cosign verify в `docs/runbooks/image-signing-verification.md`.
- Trivy SHA-pin (v0.36.0).
- Renovate monthly digest-bump.

### Diff-cover hard-fail
- `.github/workflows/coverage.yml` — `exit 1` активен.
- PR с diff-cover < 80% на changed lines → red CI.

### M08 defer'ы явно ожидают M09
1. **Playwright CI job `e2e-tests`** — ждёт stable staging (после G6).
2. **`SecurityIdorIT`** — создаётся при расширении IDOR-защиты.
3. **`@MockitoBean` → in-process gRPC** — defer v0.1.
4. **k6 baseline** — release-engineer на VPS staging перед релизом.

## Действия, ожидающие `go` пользователя

1. `git push origin dev` — **~61 коммитов** ahead origin.
2. `git push origin --tags` — **8 tags** (`v0.0.0-alpha.2..9`) локальные.
3. Fix G2.6 → G2.7 → Группы 3-9 в новой сессии.

---

## История предыдущих milestone (архив)

M01 Shared Foundations ✅ 2026-04-19
M02 Reliable Eventing ✅ 2026-04-19
M03a Internal JWT + Rate-limit ✅ 2026-04-20
M03b Secure Boundaries Part B ✅ 2026-04-20
M04 Observability ✅ 2026-04-20
M05 Performance ✅ 2026-04-21
M06 Ops & Supply Chain ✅ 2026-04-21
M07 Frontend Hardening ✅ 2026-04-22 (tag `v0.0.0-alpha.8` локальный)
M08 Test Infrastructure ✅ 2026-04-23 (tag `v0.0.0-alpha.9` локальный)
**M09 Prod Release Blockers ⏳ Группы 1 ✅, Группа 2 5/7, Группы 3-9 ⬜**
M10 Notification History ⬜
M11 OpenAPI Polish ⬜
M12 Auth Contract-first Refactor ⬜

Dependency graph и полный roadmap — `docs/milestones/README.md`.

---

## M08 итог (tag `v0.0.0-alpha.9`)

**12/12 групп закрыто**, ~27 коммитов, календарно 2 дня (2026-04-22..23).

Подробности: `docs/milestones/M08-test-infrastructure/{PLAN,CHECKLIST,NOTES,DECISIONS}.md`.
