---
phase: 58-admin-bug-006-fixes
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/api/UserApi.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserController.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserRepository.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceTest.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserRepositorySearchTest.java
autonomous: true
requirements:
  - BUG-006-1
  - FR-1
  - NFR-1
user_setup: []
must_haves:
  truths:
    - "ADMIN sends GET /api/academic/users?search=иван and gets only users whose login/ФИО/telegramId contain 'иван' (case-insensitive)"
    - "search combines with role and status filters via AND"
    - "Empty search returns full list (backward compatible)"
  artifacts:
    - path: services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/api/UserApi.java
      provides: "listUsers(@RequestParam(required=false) String search, ...) signature"
    - path: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java
      provides: "Specification<User> matchesSearch(String q)"
    - path: services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserRepositorySearchTest.java
      provides: "@DataJpaTest coverage of case-insensitive LIKE + escape of %/_"
  key_links:
    - from: UserController.listUsers
      to: UserService.listUsers
      via: "passes search param"
      pattern: "search"
    - from: UserService.listUsers
      to: UserRepository.findAll(Specification, Pageable)
      via: "JpaSpecificationExecutor"
      pattern: "Specification\\.where\\(matchesSearch"
---

<objective>
Реализовать серверный поиск пользователей в academic-service per D-01..D-04. Frontend уже шлёт параметр `search` — backend должен начать его уважать. Case-insensitive LIKE по login / last_name / first_name / middle_name / telegram_id через JPA Specification API.

Purpose: закрывает BUG-006 п.1 и AC-2. Без этого admin видит все 8000 пользователей при любом вводе в поиск.
Output: расширенный `UserApi.listUsers()` контракт + Specification + тесты.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

## Coordination Notes

UserService.java также модифицируется Plan 02 (createUser) и Plan 03 (telegram validation). Конфликт по файлу возможен; executor должен rebase перед коммитом если Plan 01 выполняется последним из wave 1.

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/58-admin-bug-006-fixes/58-CONTEXT.md
@.planning/phases/58-admin-bug-006-fixes/58-RESEARCH.md
@CLAUDE.md
@services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/api/UserApi.java
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserRepository.java
@services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/User.java

<interfaces>
<!-- Существующий контракт UserApi — listUsers принимает role/status/page/size. -->
<!-- Будет расширен опциональным параметром search. Остальные подписи не меняются. -->
<!-- UserRepository extends JpaRepository — ДОБАВИТЬ JpaSpecificationExecutor<User>. -->
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: UserSpecifications + repository поддержка</name>
  <files>
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserSpecifications.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserRepository.java,
    services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserRepositorySearchTest.java
  </files>
  <behavior>
    - Test 1: search="Иван" → matches user с last_name="Иванов" (case-insensitive)
    - Test 2: search="STU0001" → matches login="student00001"
    - Test 3: search="12345" (telegramId) → matches user с telegramId=123456789
    - Test 4: search=null или "" → без ограничений (возвращает всех)
    - Test 5: LIKE-escape: search="50%" ищет буквально "50%" (проверить, что внутри экранируем `%` и `_`)
    - Test 6: search комбинируется с role=STUDENT через AND (matchesRole(STUDENT) already exists, если нет — добавить)
  </behavior>
  <action>
    1. Создать `UserSpecifications` utility class с public static методами:
       - `matchesSearch(String q)` — возвращает null если blank; иначе OR из `cb.like(cb.lower(root.get("login")), like)`, lower(lastName), lower(firstName), lower(middleName), `cb.like(cb.toString(root.get("telegramId")), like)`. Пере D-02 используем prepared-statement-safe `%` + экранирование спецсимволов `%` и `_` → escape-char `\\`, `cb.like(..., '\\\\')`.
       - `matchesRole(UserRole role)` — равенство, null → null.
       - `matchesStatus(UserStatus status)` — равенство, null → null.
    2. Добавить `JpaSpecificationExecutor<User>` в `UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User>`.
    3. Тест `UserRepositorySearchTest` (@DataJpaTest + Testcontainers или H2 — смотреть существующую конвенцию в academic-service tests). Сидировать 4 пользователя и прогнать 6 кейсов выше.
    Использовать JPA Specification API per D-02 (user decision). Не использовать Querydsl / native SQL (нет в проекте).
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test --tests "*UserRepositorySearchTest*"</automated>
  </verify>
  <done>Все 6 тестов зелёные, Specification корректно применяется, LIKE-escape работает.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Расширить UserApi контракт + UserService + тесты</name>
  <files>
    services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/api/UserApi.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserController.java,
    services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java,
    services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserServiceTest.java
  </files>
  <behavior>
    - Test 1: UserService.listUsers(search="иван", role=null, status=null, pageable) → вызывает repository.findAll(Specification, Pageable) с matchesSearch примененным
    - Test 2: search=null → matchesSearch возвращает null, Specification.where(null) не добавляет условие
    - Test 3: UserController принимает @RequestParam(required=false) String search и прокидывает в сервис
    - Test 4: HATEOAS PagedModel<EntityModel<UserResponse>> сохраняется
  </behavior>
  <action>
    1. В `UserApi.java` (contract) добавить в метод `listUsers` параметр `@RequestParam(required=false) @Parameter(description="Поиск по login/ФИО/telegramId (case-insensitive)") String search` ПЕРЕД role/status. Обновить `@Operation`. Маппинг остаётся в контракте (БЕЗ Lombok — это api-contract).
    2. `UserController implements UserApi` — добавить параметр в сигнатуру, проксить в `userService.listUsers(...)`.
    3. `UserService.listUsers(String search, UserRole role, UserStatus status, Pageable pageable)` — собрать `Specification<User> spec = Specification.where(UserSpecifications.matchesSearch(search)).and(matchesRole(role)).and(matchesStatus(status))`; `repo.findAll(spec, pageable)`. Убрать прежний `findByRoleAndStatus` вызов (если был). Пер D-01.
    4. `UserServiceTest` — добавить тест на search: моки repository (Mockito), capture Specification argument, проверить что результат .toPredicate(...) не-null при search="иван".
    5. Проверить pageable сортировку остаётся (default: last_name, first_name).
    НЕ ДОБАВЛЯТЬ фильтр по группе — per D-02 «отдельный фильтр позже», это out of scope.
    Документировать OpenAPI: пример query `?search=иван&role=STUDENT&page=0&size=20`.
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test --tests "*UserServiceTest*" --tests "*UserControllerTest*"</automated>
  </verify>
  <done>Контракт расширен без breaking changes (параметр опционален), сервис использует Specification, тесты зелёные.</done>
</task>

<task type="auto">
  <name>Task 3: Интеграционный тест поиска</name>
  <files>
    services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/user/UserSearchIntegrationTest.java
  </files>
  <action>
    Создать UserSearchIntegrationTest. Миграцию функциональных индексов lower() НЕ создавать — отложена до профайлинга per D-02.

    1. Создать @SpringBootTest + MockMvc тест `UserSearchIntegrationTest`:
       - Prepare 10 users через @Sql или JPA
       - GET /api/academic/users?search=иван → 200, содержимое только c матчами
       - GET /api/academic/users?search=ИВАН → тот же результат (case-insensitive)
       - GET /api/academic/users?search= → все 10
       - GET /api/academic/users?search=notexist → пусто
    2. Миграцию функциональных индексов (V8) НЕ создаём в этом плане. Per D-02, индексы добавляются только если профайлинг покажет, что p95 превышает 300ms на 8000 записях. Отложено до пост-UAT профайлинга.
  </action>
  <verify>
    <automated>./gradlew.bat :services:academic-service:academic-app:test --tests "*UserSearchIntegrationTest*"</automated>
  </verify>
  <done>Integration test зелёный, backend готов принимать search параметр end-to-end.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| admin browser → API Gateway → academic-service | ADMIN роль, JWT; параметр search — untrusted input |
| academic-service → PostgreSQL | prepared statements через JPA |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-58-01-01 | Tampering (SQL injection) | UserSpecifications.matchesSearch | mitigate | JPA Criteria API параметризует значения; дополнительно экранируем `%` и `_` через escape-char `\\` перед передачей в cb.like — пользователь не может расширить pattern |
| T-58-01-02 | Information Disclosure (enumeration) | GET /users?search | accept | endpoint защищён `@RequireRole({ADMIN})`; ADMIN уже имеет полный доступ ко всем пользователям — enumeration не даёт новой информации |
| T-58-01-03 | Denial of Service (slow LIKE) | UserRepository.findAll(Specification) | accept | при 8000 записей LIKE со %X% занимает <50ms без индекса (per NFR-1); если UAT покажет >300ms — добавить функциональные индексы на lower() (deferred to Plan 07) |
| T-58-01-04 | Elevation of Privilege | UserController.listUsers | mitigate | `@RequireRole({ADMIN})` аннотация сохранена из прежней реализации; интеграционный тест проверяет 403 для TEACHER/STUDENT |
| T-58-01-05 | Information Disclosure (telegramId) | UserSpecifications (toString на numeric) | accept | telegramId — не секрет, ADMIN уже видит его в UserResponse |
</threat_model>

<verification>
- `./gradlew.bat :services:academic-service:academic-app:test` — все тесты academic-service зелёные
- `curl -X GET 'http://localhost:8080/api/academic/users?search=ива' -H "Authorization: Bearer $ADMIN_JWT"` возвращает 200 и фильтрованный список
- Swagger UI (http://localhost:9091/swagger-ui.html) показывает обновлённое описание listUsers с параметром search
</verification>

<success_criteria>
- AC-2 выполнен: `users?search=иван` возвращает только матчи (case-insensitive, по login/ФИО/telegramId)
- Backward compatibility: без search работает как раньше
- JPA Specification реализация (D-02), а не native SQL
- LIKE-escape защищает от pattern injection (T-58-01-01)
</success_criteria>

<output>
Создать `.planning/phases/58-admin-bug-006-fixes/58-01-SUMMARY.md` с:
- что реализовано (Specification + контракт + тесты)
- какие файлы изменены
- UAT шаги: открыть /admin/users, ввести "ива" в поиск → видеть только Ивановых

## Commit message
`feat(academic-service): add server-side user search with JPA Specifications (BUG-006-1)`
</output>

## UAT Steps
1. Запустить backend: `./gradlew.bat :services:academic-service:academic-app:bootRun`
2. В web-panel открыть `/admin/users`, залогиненным админом
3. Ввести в поле поиска часть ФИО — увидеть только матчи
4. Ввести часть логина — увидеть матчи
5. Проверить комбинацию: search + фильтр роли — результат суженный
</output>
