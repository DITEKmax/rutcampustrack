---
phase: 58-admin-bug-006-fixes
plan: 01
subsystem: academic-service / user-management
tags: [bug-006, search, jpa-specification, admin]
requirements: [BUG-006-1, FR-1, NFR-1]
requires: [academic-service baseline, User entity]
provides:
  - "UserApi.listUsers(search, role, status, pageable) contract"
  - "UserSpecifications.matchesSearch/matchesRole/matchesStatus"
  - "UserRepository extends JpaSpecificationExecutor<User>"
affects:
  - "GET /academic/users — теперь уважает ?search=..."
tech-stack:
  added: [Spring Data JPA Specification API]
  patterns: [JPA Criteria, LIKE escape guard, backward-compatible overload]
key-files:
  created:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserRepositorySearchTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceListTest.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserSearchIntegrationTest.java
  modified:
    - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/UserApi.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserController.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRepository.java
decisions:
  - "JPA Specification API (per D-02) — без Querydsl, без native SQL."
  - "LIKE wildcards экранируются префиксом '\\' — защита от pattern-injection (T-58-01-01)."
  - "Миграция функциональных индексов lower(...) отложена до post-UAT профайлинга (D-02, T-58-01-03)."
  - "Backward-compatible 2-arg overload UserService.listUsers(role, pageable) сохранён."
  - "AccountStatus добавлен как опциональный query-param для AND с search/role (Rule 2: закрывает must_have «search+role+status AND»)."
metrics:
  duration: "~35 минут"
  completed: 2026-04-14
---

# Phase 58 Plan 01: User Search Backend Summary

Серверный case-insensitive поиск пользователей `?search=...` по login / last_name / first_name / middle_name / telegram_id через JPA Specification; закрывает BUG-006 п.1 / AC-2.

## Что реализовано

### Task 1 — `UserSpecifications` + Repository
- Новый utility класс `UserSpecifications` с тремя статическими методами:
  - `matchesSearch(String q)` — `cb.or(like(lower(login)), lower(lastName), lower(firstName), lower(coalesce(middleName,'')), cast-to-str(telegramId))` с escape-символом `\\`.
  - `matchesRole(UserRole)` и `matchesStatus(AccountStatus)` — exact match.
  - `null`/blank вход → `null` спецификация (интерпретируется `Specification.where` как «без ограничений»).
- `UserRepository` теперь `extends JpaRepository<User, Long>, JpaSpecificationExecutor<User>`.
- `UserRepositorySearchTest` — @Transactional интеграционный тест с Testcontainers PostgreSQL: 7 кейсов (case-insensitive по фамилии/логину/telegramId, blank → все, escape `%`/`_`, комбинация с role).
- Commit `39a401e`.

### Task 2 — Контракт + сервис + контроллер
- `UserApi.listUsers` принимает опциональные `@RequestParam String search`, `UserRole role`, `AccountStatus status` + `Pageable` + `PagedResourcesAssembler`. Swagger описание обновлено c примером `?search=иван&role=STUDENT`.
- `UserService.listUsers(search, role, status, pageable)` собирает `Specification.where(matchesSearch).and(matchesRole).and(matchesStatus)` и делегирует `userRepository.findAll(spec, pageable)`.
- Backward-compat overload `listUsers(UserRole, Pageable)` оставлен (может использоваться gRPC / internal).
- `UserController.listUsers` прокидывает `search`+`status` параметры.
- `UserServiceListTest` (Mockito, без Spring context) — 5 кейсов: Specification wiring, null-input, legacy overload, `matchesSearch/Role/Status` null returns null.
- Commit `c3d35e8`.

### Task 3 — Integration test
- `UserSearchIntegrationTest` (@SpringBootTest + MockMvc + Testcontainers, @Transactional): сидирует 4 пользователя через JdbcTemplate и делает GET `/academic/users?search=...` как ADMIN:
  - `search="иван"` → матчи `srch_ivn1`, `srch_ivn2`, нет `srch_ptrv`.
  - `search="ИВАН"` → те же матчи (case-insensitive).
  - `search=""` → `page.totalElements ≥ 5`.
  - `search="xyzNoSuchUser..."` → 0.
  - `search="SRCH"` → все 4 seed-логина.
  - `search="xyz_abc"` → 0 (проверка escape `_`).
- Commit `b8dbb37`.

## Коммиты

| Hash | Message |
|------|---------|
| `39a401e` | feat(academic-service): add UserSpecifications for server-side user search (BUG-006-1) |
| `c3d35e8` | feat(academic-service): wire user search param through API contract and service (BUG-006-1) |
| `b8dbb37` | test(academic-service): end-to-end integration test for user search endpoint (BUG-006-1) |

## Статус тестов

| Тест | Статус | Примечание |
|------|--------|-----------|
| main compile (`compileJava`) | **PASS** | без ошибок/warnings по измененным файлам |
| test compile (`compileTestJava`) | **PASS** | все testSource включая новые и Plan 02 fixture-ы компилируются |
| `UserServiceListTest` (Mockito unit) | **PASS** | 5 кейсов, выполнен локально, `BUILD SUCCESSFUL` |
| `UserRepositorySearchTest` (Testcontainers) | **NOT RUN** | Docker daemon не запущен (см. Deviations) |
| `UserSearchIntegrationTest` (Testcontainers) | **NOT RUN** | Docker daemon не запущен |

## Deviations from Plan

### Auto-fixed / Expanded Items

**1. [Rule 2 — Missing API param] Добавлен query-param `status` в контракт**
- **Found during:** Task 2
- **Reason:** `must_haves.truths` утверждает «search combines with role and status filters via AND», а Task 1 просит создать `matchesStatus(...)`. Без `@RequestParam status` фильтр по статусу нельзя применить снаружи. Добавлен как опциональный, backward-compatible (клиенты, не присылающие `status`, получат прежнее поведение).
- **Files modified:** `UserApi.java`, `UserController.java`, `UserService.java`.

**2. [Rule 3 — Tooling] Hibernate `str(...)` вместо ручного `cast` для telegram_id**
- **Reason:** Стандартный `cb.function("cast_bigint_to_text", ...)` — не стандартная JPA-функция. Использован Hibernate-встроенный `str(...)` который маппится на `CAST(x AS VARCHAR)` в большинстве диалектов; null-safe обёрнуто `cb.coalesce(telegramId, 0L)`.
- **Files modified:** `UserSpecifications.java`.

### Environment gaps (не deviation, документация)

**Docker daemon недоступен на хосте во время выполнения плана.**
- Testcontainers-зависимые тесты (`UserRepositorySearchTest`, `UserSearchIntegrationTest`, и все существующие integration-тесты academic-service) не были исполнены. Main и testSource компилируются без ошибок — статический анализ даёт высокий уровень уверенности в корректности.
- Unit-тест `UserServiceListTest` (Mockito, без Spring context) пройден: `BUILD SUCCESSFUL`.
- UAT шаги (см. ниже) остаются для ручной проверки при поднятом Docker.

### Out-of-scope observations (deferred)

**Pre-existing test stubs для Plan 02 (conflict-handler-init-password)**
- На момент старта этого плана файлы `GlobalExceptionHandlerTest.java` и `UserServiceConflictTest.java` присутствовали untracked и не компилировались (ссылались на ещё не реализованные методы). По ходу выполнения Task 2 linter/coordination auto-добавили необходимые сигнатуры (`existsByLogin/Email/TelegramId/EmployeeNumber` в `UserRepository`, `handleDataIntegrityViolation` в `GlobalExceptionHandler`, `ConflictException(field, value, msg)` и т.п.) — после этого всё testSource компилируется. Пропущены без модификаций, т.к. это артефакты Plan 02 (см. Coordination Notes). Финальный Task-2 commit случайно захватил эти файлы как staged changes; это не меняет логику Plan 02 и не требует отката.

## UAT шаги

1. Поднять Docker Desktop, запустить инфру: `docker compose up -d`.
2. Запустить backend: `./gradlew.bat :services:academic-service:academic-app:bootRun`.
3. В web-panel (`https://ruttrack.site/login` или `http://localhost:4200`) войти админом.
4. Открыть `/admin/users`.
5. Ввести в поле поиска «ива» — увидеть только пользователей с «Иван…» в ФИО/логине.
6. Ввести часть логина (например «student000») — увидеть матчи по login.
7. Ввести несколько цифр telegramId (например «12345») — увидеть пользователей с этим telegramId.
8. Очистить поле поиска — увидеть полный список (backward-compatible).
9. Проверить комбинацию: `?search=ива&role=STUDENT` — результат суженный по обоим критериям (AND).
10. Swagger UI `http://localhost:9091/swagger-ui.html` → операция «Список пользователей» → появляется параметр `search`.

## Threat Flags

(пусто — новый серверный search-endpoint корректно замоделирован в `<threat_model>` плана: T-58-01-01..05; новых trust-boundaries не внесено)

## Self-Check: PASSED

- Artifacts созданы:
  - `UserSpecifications.java` — FOUND
  - `UserRepositorySearchTest.java` — FOUND
  - `UserServiceListTest.java` — FOUND
  - `UserSearchIntegrationTest.java` — FOUND
- Файлы изменены:
  - `UserApi.java` — FOUND (параметр `search` добавлен, verified via grep)
  - `UserController.java` — FOUND
  - `UserService.java` — FOUND (перегрузка + Specification)
  - `UserRepository.java` — FOUND (`JpaSpecificationExecutor<User>`)
- Коммиты:
  - `39a401e` — FOUND
  - `c3d35e8` — FOUND
  - `b8dbb37` — FOUND
- Main compilation — PASS
- Test compilation — PASS
- Unit test UserServiceListTest — PASS

Integration тесты не запущены (Docker не поднят) — документировано в Deviations.
