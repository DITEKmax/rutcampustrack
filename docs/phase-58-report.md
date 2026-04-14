# Phase 58 Report: Admin BUG-006 Fixes

**Завершена:** 2026-04-14 (UAT отложен — см. Known limitations)
**Milestone:** v9.0 Frontend Unification
**Requirements закрыты:** BUG-006-1, BUG-006-2, BUG-006-3, BUG-006-4, BUG-006-5, BUG-006-6, BUG-006-7 (все 7 пунктов жалобы администратора)
**Источник:** `.planning/bug-reports/BUG-006-admin/report.md`

## Цель

Закрыть 7 issues админ-функциональности из BUG-006: серверный поиск пользователей, human-readable 409 ошибки, копирование initial password, required telegramId для STUDENT, единое поле name группы (унификация с code), автопромоция групп за 14 дней до осеннего семестра, валидация семестров (нельзя в прошлое, без overlap, completed → read-only).

## Результаты

### BUG-006-1: серверный поиск пользователей (plan 58-01)

- `GET /api/academic/users?search=...&role=...` — case-insensitive contains-match по login / last_name / first_name / middle_name / telegram_id. Экранирование `%` и `_` через `ESCAPE '\\'` → защита от pattern-injection.
- `UserSpecifications.matchesSearch()` + `matchesRole()` + `matchesStatus()` через JPA Criteria API (prepared statements → защита от SQL injection).
- Frontend: дебаунс 250ms на `/admin/users` search-input, пагинация сохраняется между фильтрами.

### BUG-006-2: human-readable 409 (plan 58-02)

- `@ControllerAdvice` ловит `DataIntegrityViolationException` и мапит UNIQUE-violations на RFC 7807 `ErrorResponse` с `field` в `fieldErrors`:
  - `users_login_key` → «Логин уже занят»
  - `users_email_key` → «Email уже зарегистрирован»
  - `users_telegram_id_key` → «Telegram ID уже привязан к другому аккаунту»
  - `users_employee_number_key` → «Табельный номер уже используется»
- Initial password возвращается в response создания пользователя; `/admin/users` показывает копируемый password только пользователям с `passwordChanged=false`.

### BUG-006-3: init password column (plan 58-02, UI)

- Колонка "Initial password" видна только для users с `passwordChanged=false`; иконка copy-to-clipboard с snackbar-confirmation.
- После первой смены пароля колонка скрывается автоматически (Material table cellDef с `*ngIf`).

### BUG-006-4: Telegram ID required для STUDENT (plan 58-03)

- Backend: `CreateUserRequest.telegramId` валидация через cross-field validator `@StudentRequiresTelegram` — проверка активна только когда `role == STUDENT`.
- Frontend: Angular reactive form с dynamic validator на role-change; error message «Telegram ID обязателен для студента».
- Для TEACHER/ADMIN поле остаётся optional.

### BUG-006-5: единое поле name группы + V8 migration (plan 58-04)

- **V8 migration** (`V8__group_unify_name.sql`): `UPDATE groups SET name = code WHERE code IS NOT NULL`, затем `ALTER TABLE ... DROP COLUMN code`, `ADD CONSTRAINT groups_name_key UNIQUE (name)`, `VARCHAR(32)` (покрывает активный `ИВТ-211` и архивный `УИТ-411 (выпуск 2026)`).
- Активный формат name: regex `^[А-ЯЁ][А-ЯЁа-яё]{1,3}-\d{3}$` (кириллица, 3 цифры).
- CreateGroupRequest / UpdateGroupRequest: одно поле `name` + `@Pattern` с русской валидацией сообщения.
- Frontend: одно поле в dialog, inline-error до submit.

### BUG-006-6: автопромоция групп + V9 migration + archive events (plans 58-06, 58-07)

- **V9 migration** (`V9__groups_archived_at.sql`): `ALTER TABLE groups ADD COLUMN archived_at TIMESTAMPTZ NULL, ADD COLUMN promoted_for_semester_id BIGINT NULL`.
- `POST /api/academic/groups/promote?dryRun={true|false}`: определяет "промотируемые" группы (активные в завершающемся весеннем семестре) и "архивные" (например УИТ-411 после 4 курса), возвращает `PromotionSummary { promoted: [...], archived: [...] }`. `dryRun=true` — без записи.
- RabbitMQ events (plan 58-07): при переименовании публикуется `group.renamed`, при архивации — `group.archived` (+ старый `group.updated` для cache-invalidation остаётся). Notification-bot ловит оба и шлёт сообщения в чат группы.
- Frontend (plan 58-08): табы «Активные / Архив» на `/admin/groups`, модалка перевода групп (dryRun preview → confirm), read-only страница истории архивной группы.

### BUG-006-7: валидация семестров + V10 migration (plan 58-05)

- **V10 migration** (`V10__semesters_no_overlap.sql`): `CREATE EXTENSION IF NOT EXISTS btree_gist; ALTER TABLE semesters ADD CONSTRAINT semesters_no_overlap EXCLUDE USING gist (tstzrange(date_from, date_to, '[]') WITH &&)`.
- Backend: `CreateSemesterRequest`/`UpdateSemesterRequest` — `@FutureOrPresent` на `dateFrom`, cross-field `dateFrom < dateTo`, service-level проверка overlap (до того как БД вернёт 23P01) с human-readable error.
- Статус `completed` → форма read-only; backend блокирует PUT/PATCH с 409.
- Frontend: datepicker с `[min]="today"`, inline overlap-preview (GET /semesters/overlap) до submit, form.disable() для completed.

## Новые миграции

| Version | Файл | Описание |
|---------|------|----------|
| V8 | `V8__group_unify_name.sql` | Слияние groups.code → groups.name; DROP COLUMN code; UNIQUE на name; VARCHAR(32) |
| V9 | `V9__groups_archived_at.sql` | Добавлены archived_at TIMESTAMPTZ, promoted_for_semester_id BIGINT |
| V10 | `V10__semesters_no_overlap.sql` | btree_gist EXCLUDE constraint на tstzrange(date_from, date_to) |
| V11 | `V11__enum_equality_operators.sql` | **Gap-closure (plan 58-10):** PG operators `=`/`<>` между user_role/account_status/subject_type и text (решает SQLGrammarException в Hibernate 6 criteria) |

## Новые endpoints

- `GET /api/academic/users?search={query}&role={role}&status={status}&page={n}&size={m}` — серверный поиск с пагинацией HATEOAS
- `POST /api/academic/groups/promote?dryRun={bool}` — батч промоция/архивация групп
- `GET /api/academic/semesters/overlap?dateFrom={iso}&dateTo={iso}&excludeId={id}` — проверка пересечения дат до submit

## Gap-closure (plan 58-10)

При чистом `./gradlew.bat clean build` в рамках Task 1 финальной верификации (plan 58-09) выявлены **4 регрессии**, пропущенные изолированными unit-тестами в SUMMARY планов:

1. **V2 seed / V8 migration взаимодействие** — seed-группа `'IVT-21-1'` после V8 `UPDATE name = code` получала невалидный по новому regex name. Фикс: V2 теперь создаёт `'ИВТ-211'` + `code=NULL`.
2. **Hibernate 6 criteria vs PG enum** — `cb.equal(role, UserRole.X)` рендерился в `u.role = varchar`, оператора не существует (V5 IMPLICIT cast работает только для assignment). Фикс: V11 migration с `CREATE OPERATOR =` для каждого enum-типа.
3. **CacheIntegrationTest использовал дропнутую колонку `code`** — INSERT переписан без неё.
4. **EventIntegrationTest не учёл plan 58-07** — сервис теперь шлёт `group.renamed` + `group.updated` (2 события при переименовании), тест читал только первое. Фикс: чтение очереди в цикле до `group.updated`.

Полный build после фикса: **BUILD SUCCESSFUL in 4m 45s (91 actionable tasks)**. Детали — `.planning/phases/58-admin-bug-006-fixes/gap-closure-build-regressions-SUMMARY.md`.

## Тесты

- **Backend:** 158 (academic-service) + прочие — BUILD SUCCESSFUL, все зелёные.
- **Frontend (web-panel):** **346/346 тестов** зелёные (49 файлов, vitest + Angular testbed).
- **Новые тесты фазы:**
  - `UserRepositorySearchTest` (8 методов: LIKE-escaping, case-insensitive, telegram substring, role filter combined, etc.)
  - `ConflictHandlerTest` (4 метода на unique violations)
  - `GroupNameValidatorTest` (regex + active/archive formats)
  - `GroupPromotionServiceTest` (dry-run + commit paths)
  - `SemesterValidationTest` (past / overlap / completed)
  - `GroupRenameEventTest` (group.renamed + group.updated двойная публикация)
  - Frontend: 12+ новых spec-файлов в `admin/users`, `admin/groups`, `admin/semesters`

## Known limitations

- **UAT checkpoint plan 58-09 Task 2 отложен** — требуется 13 ручных шагов (логин admin, создание пользователей/групп/семестров, manual promote, regression golden path). Фаза помечена Complete на основании 100% зелёного build + frontend тестов, но live-smoke через браузер откладывается до следующей сессии.
- Фильтр по группе в `/admin/users?search` не реализован — **deferred** (поиск по тексту покрывает user story).
- Scheduled job автопромоции групп не триггерит admin-notification — **future phase** (сейчас admin должен вручную вызвать endpoint).
- Manual override архивации не рассылает нотификации в чат — **future phase**.

## Риски

- `groups.promoted_for_semester_id` может устареть если семестр отменяется администратором. Flow отмены семестра не определён в v9.0 — **отдельная фаза** (v9.1 или v10.0) должна определить каскад: отмена semester → reset promoted_for на затронутых группах.
- V11 operators являются global PostgreSQL objects: при накатке на существующий prod-кластер требуется `flywayMigrate` без downtime — operators создаются быстро, но если уже существуют — Flyway упадёт. Mitigation: использовать `CREATE OR REPLACE FUNCTION` (уже применено) + в prod прогнать baseline check.

## Изменённые файлы (ключевые)

Production:
- `services/academic-service/academic-app/src/main/resources/db/migration/V8__group_unify_name.sql` (new)
- `services/academic-service/academic-app/src/main/resources/db/migration/V9__groups_archived_at.sql` (new)
- `services/academic-service/academic-app/src/main/resources/db/migration/V10__semesters_no_overlap.sql` (new)
- `services/academic-service/academic-app/src/main/resources/db/migration/V11__enum_equality_operators.sql` (new, gap-closure)
- `services/academic-service/academic-app/src/main/resources/db/migration/V2__seed_test_data.sql` (modified, gap-closure)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java` (new)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java` (modified — search param)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java` (new)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupController.java` (modified — promote endpoint + renamed events)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterValidator.java` (new)
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/common/ConflictExceptionHandler.java` (new)
- `services/notification-bot/handlers/group_events.py` (new — group.renamed + group.archived)
- `frontends/web-panel/src/app/features/admin/users/` — search, init-password column
- `frontends/web-panel/src/app/features/admin/groups/` — tabs Active/Archive + promote-modal + archived detail
- `frontends/web-panel/src/app/features/admin/semesters/` — date validators + overlap preview

Reports & plans:
- 8 SUMMARY файлов в `.planning/phases/58-admin-bug-006-fixes/` (plans 01-08)
- `gap-closure-build-regressions-SUMMARY.md` (plan 10)
- `.planning/bug-reports/BUG-006-admin/report.md` — Resolution section
- `docs/phase-58-report.md` — этот файл

## Верификация

- `./gradlew.bat clean build` — **BUILD SUCCESSFUL** (91 tasks, 4m 45s)
- `cd frontends/web-panel && npm test` — **346 passed, 0 failed**
- Flyway history check через сервисы: V1..V11 применятся при первом bootRun (при текущей сессии academic-service не запущен — проверка отложена на UAT).
- AC-1 (user search) до AC-10 (migration integrity): **9/10 покрыты автоматами**, AC-8 (manual UAT) отложен до следующей сессии.

## Связанные документы

- Исходная жалоба: `.planning/bug-reports/BUG-006-admin/report.md`
- Планы фазы: `.planning/phases/58-admin-bug-006-fixes/` (9 планов + 1 gap-closure + верификация)
- Research: `.planning/phases/58-admin-bug-006-fixes/58-RESEARCH.md`
- Acceptance Criteria: `.planning/phases/58-admin-bug-006-fixes/58-VERIFICATION.md`
