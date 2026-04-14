---
phase: 59
slug: excuses-backend
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-14
---

# Phase 59 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Подробное обоснование см. в `59-RESEARCH.md` § "Validation Architecture".

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (backend)** | JUnit 5 + Mockito + Testcontainers (MongoDB, RabbitMQ) |
| **Framework (bot)** | pytest 7.x + pytest-asyncio |
| **Framework (frontend)** | Vitest + Angular Testing Module |
| **Config files** | `services/attendance-service/attendance-app/build.gradle.kts`, `services/notification-bot/pyproject.toml`, `frontends/web-panel/vitest.config.ts` |
| **Quick run (backend)** | `./gradlew.bat :services:attendance-service:attendance-app:test --tests "*Excuse*"` |
| **Quick run (bot)** | `cd services/notification-bot && pytest tests/test_excuse_decided.py -q` |
| **Quick run (frontend)** | `cd frontends/web-panel && npx vitest run --testNamePattern "excuse"` |
| **Full suite command** | `./gradlew.bat build && cd services/notification-bot && pytest && cd ../../frontends/web-panel && npm test` |
| **Estimated runtime (quick)** | ~60 сек |
| **Estimated runtime (full)** | ~10 мин |

---

## Sampling Rate

- **After every task commit:** Run relevant quick command (backend/bot/frontend по месту правок).
- **After every plan wave:** Run full suite для затронутого сервиса.
- **Before `/gsd-verify-work`:** Полный suite зелёный + все 12 AC покрыты.
- **Max feedback latency:** 60 сек на quick-run.

---

## Per-Task Verification Map

> Заполняется планировщиком (gsd-planner) после декомпозиции задач. Ожидаемая структура:

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 59-XX-YY | XX | W | FR-N / NFR-N | T-59-XX | {behavior} | unit/IT/contract/e2e | `{command}` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements (создание тестовой инфраструктуры)

### Backend (attendance-service)
- [ ] `services/attendance-service/attendance-app/src/test/java/.../excuse/ExcuseServiceTest.java` — unit тесты service (FR-1, FR-5, FR-7)
- [ ] `.../excuse/ExcuseAssemblerTest.java` — HATEOAS маппинг
- [ ] `.../excuse/ExcuseEventPublisherTest.java` — проверка payload и routing key
- [ ] `.../excuse/ExcuseControllerIT.java` — @SpringBootTest + Testcontainers (AC-1..AC-6)
- [ ] `.../excuse/ExcuseEventContractIT.java` — публикация события + JSON Schema (AC-7)
- [ ] `services/schedule-service/schedule-app/src/test/.../LessonsByIdsGrpcIT.java` — gRPC (AC-11)
- [ ] фикстуры: `src/test/resources/fixtures/excuse_requested.json`, `excuse_decided.json`

### Bot (notification-bot)
- [ ] `services/notification-bot/tests/test_excuse_decided.py` — consumer для `excuse.decided` (AC-8)
- [ ] `services/notification-bot/tests/fixtures/excuse_decided.json` — канонический payload
- [ ] (проверить существующий `test_excuse_requested.py` — обновить фикстуру при изменении контракта)

### Frontend (web-panel)
- [ ] `frontends/web-panel/src/app/features/student/excuses/excuses.component.spec.ts` — ExcuseType dropdown + submit (AC-9)
- [ ] `frontends/web-panel/src/app/features/headman/excuses/excuses.component.spec.ts` — approve/reject flow (AC-10)
- [ ] MSW/HttpTestingController mocks для `/api/attendance/excuses`

---

## Manual-Only Verifications (UAT)

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Студент создаёт тикет и видит его в списке | AC-9 / FR-3 | UI interaction, визуальная проверка dropdown и статуса | `/student/excuses` → выбрать причину → submit → тикет отображается `pending` |
| Староста одобряет pending тикет | AC-10 / FR-4, FR-5 | Проверка end-to-end связки UI→API→каскад→Telegram | `/headman/excuses` → approve → студент получает сообщение в бот |
| Telegram алерт старосте о новом тикете | FR-2 | Внешний канал (Telegram), нельзя автоматизировать | Создать тикет → староста получает push в Telegram |
| Telegram уведомление студенту о решении | FR-6 | Внешний канал | Approve/reject → студент получает сообщение |

---

## Nyquist Sampling — ключевые оси (см. RESEARCH § Validation Architecture)

1. **Input domain**: role × ownership × lessonIds validity × ExcuseType × comment length × headman flag.
2. **Boundary**: один активный тикет на lessonId (duplicate → 409); комментарий 1000 vs 1001 символов; пустой lessonIds.
3. **Failure modes**: RabbitMQ down, Mongo write после publish, gRPC timeout, race на дубликат, повторный approve.
4. **Invariants**: атомарность cascade, visibility isolation, event↔DB consistency (outbox/tx), запрет self-approve.
5. **Enum coverage**: все значения `ExcuseType` участвуют хотя бы в одном тесте; маппинг `ExcuseType → AttendanceStatus` покрыт 100%.
6. **Coverage gate**: branch coverage ≥ 80% для cascade-логики (ExcuseService#approve).

---

## Validation Sign-Off

- [ ] Все tasks в PLAN.md имеют `<automated>` verify или ссылку на Wave 0
- [ ] Sampling continuity: нет 3 подряд задач без automated verify
- [ ] Wave 0 закрывает все MISSING ссылки
- [ ] Нет watch-mode флагов
- [ ] Feedback latency < 60с для quick-run
- [ ] `nyquist_compliant: true` в frontmatter

**Approval:** pending
