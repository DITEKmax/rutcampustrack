---
phase: 59
slug: excuses-backend
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-14
finalized: 2026-04-14
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
| 59-01-01 | 01 | 1 | FR-1 (ExcuseTicket model) | T-59-01 | Mongo enum storage lowercase; D-11 uniqueness query | unit | `./gradlew ... --tests "*ExcuseRepositoryTest"` | ✅ | ✅ |
| 59-02-01 | 02 | 2 | FR-1, FR-5, FR-7 / AC-1..AC-6 | T-59-02-01..04 | D-10..D-18 service rules | unit | `./gradlew ... --tests "*ExcuseServiceTest"` | ✅ | ✅ |
| 59-02-02 | 02 | 2 | FR-1 (HATEOAS) | — | Assembler lowercases status | unit | `./gradlew ... --tests "*ExcuseAssemblerTest"` | ✅ | ✅ |
| 59-03-01 | 03 | 2 | AC-11, FR-7 (D-25) | — | gRPC GetLessonsByIds batch + orphan tolerance | IT | `./gradlew :services:schedule-service:schedule-app:test --tests "*LessonsByIdsGrpcIT"` | ✅ | ✅ |
| 59-04-01 | 04 | 3 | FR-5 / AC-5 (D-16 cascade) | T-59-04-01..03 | approve → EXCUSED/FREE_ATTENDANCE upsert | IT | `./gradlew ... --tests "*ExcuseServiceApproveIT"` | ✅ | ✅ |
| 59-05-01 | 05 | 3 | AC-7, FR-2 (D-19/D-20) | T-59-05-01..03 | event envelope + lowercase enum | unit+IT | `./gradlew ... --tests "*ExcuseEventPublisherTest" "*ExcuseEventContractIT"` | ✅ | ✅ |
| 59-06-01 | 06 | 4 | AC-8, FR-6 (D-28) | T-59-06-01..03 | bot consumer for `excuse.decided` | pytest | `pytest tests/test_excuse_decided.py` | ✅ | ✅ |
| 59-07-01 | 07 | 4 | AC-9, FR-3 (D-21, D-22) | T-59-07-01..02 | ExcuseType dropdown + live /excuses/me | vitest | `npx vitest run src/app/features/student/excuses/` | ✅ | ✅ |
| 59-08-01 | 08 | 4 | AC-10, FR-4, FR-5 (D-23, D-24) | T-59-08-01..03 | approve/reject with required comment | vitest | `npx vitest run src/app/features/headman/excuses/` | ✅ | ✅ |
| 59-09-01 | 09 | 5 | D-25 | — | validateLessonIds in createExcuse | unit (via service) | `./gradlew ... --tests "*ExcuseServiceTest"` | ✅ | ✅ |
| 59-09-02 | 09 | 5 | AC-1..AC-6, AC-12 | T-59-02-01..04 | full REST → Mongo → Rabbit happy + rejection paths | IT | `./gradlew ... --tests "*ExcuseControllerIT"` | ✅ | ✅ (7/7) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements (создание тестовой инфраструктуры)

### Backend (attendance-service)
- [x] `services/attendance-service/attendance-app/src/test/java/.../excuse/ExcuseServiceTest.java` — unit тесты service (FR-1, FR-5, FR-7) — 10 кейсов
- [x] `.../excuse/ExcuseAssemblerTest.java` — HATEOAS маппинг
- [x] `.../excuse/ExcuseEventPublisherTest.java` — проверка payload и routing key — 4 кейса
- [x] `.../excuse/ExcuseControllerIT.java` — @SpringBootTest + Testcontainers (AC-1..AC-6) — 7 кейсов зелёные
- [x] `.../excuse/ExcuseEventContractIT.java` — публикация события (AC-7) — 2 кейса
- [x] `.../excuse/ExcuseServiceApproveIT.java` — D-16 cascade (AC-5) — 4 кейса
- [x] `services/schedule-service/schedule-app/src/test/.../LessonsByIdsGrpcIT.java` — gRPC (AC-11) — 3 кейса
- [x] фикстуры: `src/test/resources/fixtures/excuse_requested.json`, `excuse_decided.json`

### Bot (notification-bot)
- [x] `services/notification-bot/tests/test_excuse_decided.py` — consumer для `excuse.decided` (AC-8) — 6 кейсов
- [x] `services/notification-bot/tests/fixtures/excuse_decided.json` — канонический payload
- [x] `test_event_dispatcher.py` обновлён — `excuse.decided` добавлен в registry

### Frontend (web-panel)
- [x] `frontends/web-panel/src/app/features/student/excuses/student-excuses.component.spec.ts` — ExcuseType dropdown + submit (AC-9) — 10 кейсов
- [x] `frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.spec.ts` — approve/reject flow (AC-10) — 8 кейсов

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

- [x] Все tasks в PLAN.md имеют `<automated>` verify или ссылку на Wave 0
- [x] Sampling continuity: нет 3 подряд задач без automated verify
- [x] Wave 0 закрывает все MISSING ссылки
- [x] Нет watch-mode флагов
- [x] Feedback latency < 60с для quick-run
- [x] `nyquist_compliant: true` в frontmatter

**Approval:** approved (2026-04-14, plan 59-09)
