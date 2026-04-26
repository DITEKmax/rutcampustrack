---
phase: 58-admin-bug-006-fixes
plan: 09
type: execute
wave: 5
depends_on: [01, 02, 03, 04, 05, 06, 07, 08]
files_modified:
  - docs/phase-58-report.md
  - CLAUDE.md
  - .planning/ROADMAP.md
  - .planning/STATE.md
  - .planning/bug-reports/BUG-006-admin/report.md
autonomous: false
requirements:
  - BUG-006-1
  - BUG-006-2
  - BUG-006-3
  - BUG-006-4
  - BUG-006-5
  - BUG-006-6
  - BUG-006-7
  - AC-1
  - AC-2
  - AC-3
  - AC-4
  - AC-5
  - AC-6
  - AC-7
  - AC-8
  - AC-9
  - AC-10
user_setup: []
must_haves:
  truths:
    - "Все AC-1..AC-10 верифицированы в VERIFICATION.md"
    - "docs/phase-58-report.md обновлён со списком изменений, миграций, известных ограничений"
    - "CLAUDE.md содержит запись о Phase 58 Complete"
    - "BUG-006 report имеет секцию Resolution со ссылкой на фазу"
    - "Вся сборка зелёная: gradle build + npm test в web-panel"
    - "UAT прошёл (чекпоинт с человеком)"
  artifacts:
    - path: docs/phase-58-report.md
      provides: "Полный отчёт с diff, тестами, перформансом поиска, результатами миграций"
    - path: .planning/ROADMAP.md
      provides: "Phase 58 отмечена Complete"
  key_links:
    - from: docs/phase-58-report.md
      to: .planning/bug-reports/BUG-006-admin/report.md
      via: "Resolution section с датой и ссылкой"
      pattern: "Phase 58"
---

<objective>
Финальная верификация фазы: полная сборка, интеграционный E2E smoke, UAT чекпоинт, обновление документации и статуса в CLAUDE.md/ROADMAP.md/STATE.md.

Purpose: закрыть фазу по критерию AC-1..AC-10; отметить BUG-006 как Resolved.
Output: phase-58-report.md + обновлённые статусы.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/58-admin-bug-006-fixes/58-CONTEXT.md
@.planning/phases/58-admin-bug-006-fixes/58-RESEARCH.md
@.planning/phases/58-admin-bug-006-fixes/58-01-SUMMARY.md
@.planning/phases/58-admin-bug-006-fixes/58-02-SUMMARY.md
@.planning/phases/58-admin-bug-006-fixes/58-03-SUMMARY.md
@.planning/phases/58-admin-bug-006-fixes/58-04-SUMMARY.md
@.planning/phases/58-admin-bug-006-fixes/58-05-SUMMARY.md
@.planning/phases/58-admin-bug-006-fixes/58-06-SUMMARY.md
@.planning/ROADMAP.md
@CLAUDE.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Full build + integration smoke test</name>
  <files>.planning/phases/58-admin-bug-006-fixes/58-VERIFICATION.md</files>
  <action>
    1. Чистая сборка:
       ```
       docker compose down -v
       docker compose up -d
       ./gradlew.bat clean build
       ```
       Ожидается 0 failed tests.
    2. Frontend:
       ```
       cd frontends/web-panel && npm ci && npm test && npm run build
       ```
       Ожидается 0 failed tests (297+ tests зелёные).
    3. Flyway history check:
       ```
       docker compose exec postgres psql -U postgres -d academic_db -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"
       ```
       Ожидается V1..V10 все success=true.
    4. Пройти E2E smoke:
       - `./gradlew.bat bootRun` для всех сервисов (или docker-compose профиль)
       - curl healthchecks: /actuator/health всех сервисов OK
       - curl /api/academic/users?search=test → 200 с фильтром
       - curl POST /api/academic/users с дублем login → 409 с field
       - curl POST /api/academic/groups с "ivt11-001" (lowercase) → 400 validation
       - curl POST /api/academic/semesters с прошлой датой → 400
       - curl POST /api/academic/groups/promote?dryRun=true → 200 с PromotionSummary
    5. Записать результаты в `.planning/phases/58-admin-bug-006-fixes/58-VERIFICATION.md`.
    6. Если есть регрессия любого существующего теста — STOP, создать gap-closure план.
  </action>
  <verify>
    <automated>./gradlew.bat clean build && cd frontends/web-panel && npm test</automated>
  </verify>
  <done>Все билды зелёные; smoke passes; VERIFICATION.md обновлён.</done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <what-built>
    Полная реализация BUG-006 (все 7 пунктов):
    1. Серверный поиск пользователей по login/ФИО/telegramId
    2. Human-readable 409 ошибки для unique-нарушений (login/email/telegramId/employeeNumber)
    3. Init password колонка в /admin/users с кнопкой копирования
    4. Telegram ID required для STUDENT (backend + frontend validator)
    5. Группы — единое поле name (формат XXXx-NNN), V8 миграция
    6. Автопромоция групп за 14 дней до осеннего семестра, V9 миграция
    7. Валидация семестров (прошлое, overlap, completed), V10 миграция с btree_gist EXCLUDE
  </what-built>
  <how-to-verify>
    1. **Логин как admin** (admin / admin) → /admin/users
    2. **Поиск:** ввести часть ФИО → видна только matched строки
    3. **Init password column:** видна для не сменивших пароль, кнопка копирует
    4. **Создать STUDENT без telegramId** → error "Telegram ID обязателен"
    5. **Создать пользователя с занятым email** → error "Email уже зарегистрирован" (конкретное, не generic)
    6. **Перейти на /admin/groups** → Создать → одно поле name
    7. **Ввести "abc"** → error "Неверный формат"
    8. **Ввести "ИВТ11-001"** → создано
    9. **Перейти на /admin/semesters** → Создать → datepicker не даёт выбрать прошлую дату
    10. **Ввести даты, пересекающиеся с существующим** → красная ошибка до submit
    11. **Попытаться edit завершённый семестр** → форма disabled + warning
    12. **Manual promote:** `curl -X POST http://localhost:8080/api/academic/groups/promote?dryRun=true -H "Authorization: Bearer $ADMIN_JWT"` → JSON с {promoted, archived}
    13. **Regression check:** выполнить Golden Path из phase-57-report (создание пользователя, создание группы, создание семестра, login как student, отметка на паре) → работает
  </how-to-verify>
  <resume-signal>Введите "approved" если всё ок, или опишите найденные проблемы</resume-signal>
</task>

<task type="auto">
  <name>Task 3: docs/phase-58-report.md + статусы</name>
  <files>
    docs/phase-58-report.md,
    CLAUDE.md,
    .planning/ROADMAP.md,
    .planning/STATE.md,
    .planning/bug-reports/BUG-006-admin/report.md
  </files>
  <action>
    1. Создать `docs/phase-58-report.md` по шаблону других phase-отчётов (см. docs/phase-57-report.md):
       - **Цель:** закрыть BUG-006 (7 пунктов)
       - **Реализовано:** список по пунктам с ссылками на plans
       - **Миграции:** V8, V9, V10 — описать каждую
       - **Новые endpoint-ы:** GET /users?search, POST /groups/promote, GET /semesters/overlap
       - **Новые тесты:** перечислить (количество зелёных тестов before/after)
       - **Перформанс поиска:** замерять если есть данные
       - **Known limitations:**
         - Фильтр по группе в поиске пользователей не реализован (deferred)
         - Scheduled job не триггерит notification admin (future)
         - Manual override не рассылает нотификации (future)
       - **Риски:** отметить что promoted_for_semester_id может устареть при отмене семестра (admin flow для отмены семестра — отдельная фаза)
    2. Обновить `CLAUDE.md`:
       - В "Текущий статус" — `**v9.0**: ЗАВЕРШЕНА (включая Phase 58 BUG-006 Fixes)` (если Phase 58 финализирует v9.0) или добавить строку "Phase 58: ЗАВЕРШЕНА".
    3. Обновить `.planning/ROADMAP.md`:
       - Phase 58 таблица прогресса → Complete, дата
    4. Обновить `.planning/STATE.md`:
       - `stopped_at: Completed Phase 58`
       - `completed_phases: 9` (или actual)
       - `last_activity: 2026-04-?? -- Phase 58 complete`
    5. Обновить `.planning/bug-reports/BUG-006-admin/report.md` — добавить в конец:
       ```
       ## Resolution

       **Resolved:** 2026-04-?? в Phase 58 (docs/phase-58-report.md).
       Все 7 пунктов закрыты. См. acceptance criteria AC-1..AC-10.
       ```
  </action>
  <verify>
    <automated>grep -q "Phase 58" CLAUDE.md && grep -q "Resolution" .planning/bug-reports/BUG-006-admin/report.md && test -f docs/phase-58-report.md</automated>
  </verify>
  <done>Отчёт создан, статусы обновлены, bug report имеет Resolution.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| — (документация, нет runtime изменений) | — |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-58-07-01 | Information Disclosure | phase-58-report.md | accept | Публичный отчёт не содержит секретов; только описание функциональности |
| T-58-07-02 | Tampering (regression) | — | mitigate | Task 1 full build + Task 2 manual UAT ловит регрессию до close-out |
</threat_model>

<verification>
- `test -f docs/phase-58-report.md && test -f .planning/phases/58-admin-bug-006-fixes/58-VERIFICATION.md`
- `grep -q "Phase 58.*Complete\|ЗАВЕРШЕНА" .planning/ROADMAP.md`
- Все предыдущие 6 SUMMARY файлов существуют
</verification>

<success_criteria>
- Сборка зелёная (backend + frontend)
- UAT approved человеком
- Документация обновлена во всех 5 файлах
- BUG-006 отмечен как Resolved
</success_criteria>

<output>
Создать `.planning/phases/58-admin-bug-006-fixes/58-07-SUMMARY.md` с финальной сводкой и ссылками на 6 предыдущих SUMMARY.

## Commit message
`docs(phase-58): close BUG-006 — admin functionality fixes report + status updates`
</output>

## UAT Steps
(См. Task 2 checkpoint выше — это сам UAT.)
</output>
