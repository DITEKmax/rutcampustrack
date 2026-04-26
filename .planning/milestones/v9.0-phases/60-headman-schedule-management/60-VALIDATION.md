---
phase: 60
slug: headman-schedule-management
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-14
---

# Phase 60 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> See `60-RESEARCH.md` § "Validation Architecture" for the full AC→test mapping.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (Java)** | JUnit 5 + Mockito + Spring Boot Test + Testcontainers (PostgreSQL / RabbitMQ / MongoDB) |
| **Framework (Python bot)** | pytest 7.x + pytest-asyncio |
| **Framework (Angular web-panel)** | Karma/Jasmine (existing), Playwright для e2e при необходимости |
| **Config files** | `build.gradle.kts`, `services/*/build.gradle.kts`, `services/notification-bot/pyproject.toml`, `frontends/web-panel/angular.json` |
| **Quick run command (backend module)** | `./gradlew.bat :services:<module>:test --tests <class>` |
| **Full Java suite** | `./gradlew.bat build` |
| **Full Python suite** | `cd services/notification-bot && pytest -q` |
| **Full Angular suite** | `cd frontends/web-panel && npm run test -- --watch=false` |
| **Estimated runtime** | backend module: ~30s; bot: ~20s; angular: ~60s; full build: ~6-8 min |

---

## Sampling Rate

- **After every task commit:** Run module-scoped tests (`./gradlew.bat :services:<module>:test`, `pytest <file>`, `ng test --include=<spec>`).
- **After every plan wave:** Run full module suite (`./gradlew.bat :services:<module>:check`, `pytest`, `npm run test`).
- **Before `/gsd-verify-work`:** `./gradlew.bat build` + `pytest` + `ng test --watch=false` — все зелёные.
- **Max feedback latency:** 60 секунд на задаче (модульный тест), 10 минут на волне (полный suite).

---

## Per-Task Verification Map

> Пер-задачная карта будет детализирована планировщиком (`gsd-planner`) после разбиения
> на подпланы 60-01..60-NN. Ниже — каркас mapping из 11 AC в ROADMAP Phase 60 → ожидаемый
> уровень теста. Каждый AC **обязательно** имеет хотя бы один automated test.

| AC | Behavior | Test Type | Component | Suggested test artifact |
|----|----------|-----------|-----------|-------------------------|
| AC-01 | Атомарное создание subject + N TSG | IT (@SpringBootTest + Testcontainers) | academic-app | `SubjectServiceIT.createSubject_withNTeachers_createsAllAtomically` + rollback variant |
| AC-02 | `subjects.group_id NOT NULL` + FK | Flyway migration IT | academic-app | `SubjectSchemaIT.groupIdIsNotNull_andHasForeignKey` |
| AC-03 | `ScheduleItem.teacherId` удалён, read через JOIN | unit + IT | schedule-app | `ScheduleItemEntityTest` (no teacherId field) + `TeacherJournalQueryIT.joinViaTSG` |
| AC-04 | Таблица `schedule_one_off_lessons` + UNIQUE | Flyway IT | schedule-app | `OneOffLessonSchemaIT.uniqueConstraint_preventsDuplicates` |
| AC-05 | POST/DELETE one-off CRUD + 409 | IT (@SpringBootTest + MockMvc) | schedule-app | `OneOffLessonControllerIT.create_whenSlotOccupied_returns409` + `delete_pastDate_succeeds` |
| AC-06 | События `lesson.one_off.created/cancelled` опубликованы | contract test (RabbitMQ Testcontainer) | schedule-app + event-schemas | `OneOffLessonEventPublisherIT` + JSON Schema validator |
| AC-07 | attendance-service каскадно удаляет при cancel | IT (Testcontainers Mongo + Rabbit) | attendance-app | `OneOffLessonCancelledConsumerIT.cascadeDeletesLessonAndMarks` |
| AC-08 | Read-path merge `ScheduleItem + one-off` | unit + IT | attendance-app | `LessonGenerationServiceTest.mergesTemplateAndOneOff` |
| AC-09 | Push студентам при create/cancel one-off | unit (bot handler) | notification-bot | `test_one_off_created_handler.py` + `test_one_off_cancelled_handler.py` |
| AC-10 | `/headman/schedule` матрица + диалоги | component (Jasmine/Karma) + optional Playwright | web-panel | `headman-schedule.component.spec.ts` + `one-off-dialog.component.spec.ts` |
| AC-11 | Guard `groupId` проверка (403 foreign group) | IT (MockMvc) | schedule-app | `OneOffLessonSecurityIT.foreignGroup_returns403` |

*Plan-level Per-Task table будет заполнен планировщиком при разбиении на подпланы
(каждая задача 60-XX-YY получит строку в таблице со Status колонкой).*

---

## Wave 0 Requirements

- [ ] `services/academic-service/academic-app/src/test/java/.../SubjectServiceIT.java` — new test class с Testcontainers PostgreSQL
- [ ] `services/schedule-service/schedule-app/src/test/java/.../OneOffLessonControllerIT.java` — new test class
- [ ] `services/schedule-service/schedule-app/src/test/java/.../OneOffLessonEventPublisherIT.java` — RabbitMQ Testcontainer
- [ ] `services/attendance-service/attendance-app/src/test/java/.../OneOffLessonCancelledConsumerIT.java` — new consumer IT
- [ ] `services/notification-bot/tests/test_one_off_created_handler.py` — pytest fixture для one-off push
- [ ] `services/notification-bot/tests/test_one_off_cancelled_handler.py`
- [ ] `frontends/web-panel/src/app/features/headman/schedule/**/*.spec.ts` — Karma specs (headman-schedule.component, one-off-dialog, schedule-edit-dialog)
- [ ] `event-schemas/lesson.one_off.created.json` + `lesson.one_off.cancelled.json` — новые JSON Schema (валидируются в contract-тестах)
- [ ] Flyway-миграции (academic V??, schedule V??) — проверяются автоматически Testcontainers поднятием схемы

*Framework install:* не требуется — Testcontainers, pytest, Karma уже настроены в проекте.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Реальная push-нотификация в Telegram на телефоне | AC-09 | Требует живого Telegram-клиента и настоящего бота | 1) Запустить `docker compose up -d`, 2) Авторизоваться в Telegram как student из группы старосты, 3) Как HEADMAN вызвать `POST /api/schedule/one-off-lessons`, 4) Подтвердить получение сообщения в чате. |
| Визуальная проверка матрицы `/headman/schedule` | AC-10 | Адаптивная сетка + стили — автоматизировать через Playwright дорого для 1 страницы | Открыть `https://ruttrack.site/headman/schedule` в браузере, проверить матрицу 6 дней × 8 слотов, клик по ячейке открывает диалог. |
| Реалтайм WebSocket push в PWA при создании one-off | AC-06, AC-09 | Требует одновременной работы двух клиентов (HEADMAN + STUDENT PWA) | 1) Открыть PWA как student, 2) HEADMAN создаёт one-off через API, 3) STUDENT PWA должен обновиться без refresh. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies (заполняется планировщиком)
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags (`--watch=false` обязательно для ng test)
- [ ] Feedback latency < 60s per task
- [ ] `nyquist_compliant: true` set in frontmatter после планирования

**Approval:** pending
