# M11 Checklist

Атомарные задачи в порядке выполнения. Одна строка = 30 мин - 2 часа.

## Группа 0 — Shared Web Starter + ErrorResponse унификация (NEW) ✅

### 0.1 Создание shared-web-api модуля (упрощено — см. NOTES G0.1)
- [x] Создать `services/shared/shared-web-api/build.gradle.kts`
      (чистый java-library, без Spring — только jakarta.validation-api,
      jackson-annotations, swagger-annotations)
- [x] Обновить `settings.gradle.kts`: добавить shared-web-api
- [x] Создать `InvalidParam.java` в shared-web-api (копия из shared-web,
      deprecated + aliases FieldError)
- [x] Создать `FieldError.java` как top-level record в shared-web-api
      (description + example + rejectedValue + message) — замена
      inner record'а из contract'ов
- [x] Создать унифицированный `ErrorResponse.java` в shared-web-api:
      10 полей (status/type/title/detail/instance/timestamp/traceId/
      fieldErrors/field/extras) + factory methods + backward-compat
      конструкторы (6-arg для auth, 7-arg для schedule/attendance,
      8-arg, 9-arg, 10-arg canonical)
- [x] @Schema на все поля (сохранить лучшие descriptions из 4 текущих
      версий)
- [x] Validators (StartBeforeEnd/DateRangeValid/ValidFile + их
      @interface) — **остаются в shared-web-starter**, они Spring-dependent

### 0.2 Refactor shared-web → shared-web-starter
- [x] ~~Переименовать директорию~~ — оставили `shared-web/` имя, но превратили в starter
- [x] Обновить `build.gradle.kts`: `api(project(":services:shared:shared-web-api"))`
- [x] Удалить дубли из shared-web-starter (ErrorResponse, InvalidParam,
      валидаторы — остались только Spring beans)
- [x] Обновить импорты в GlobalExceptionHandler, JacksonConfig,
      AdminActionAspect, SharedOpenApiCustomizer
- [x] Создать `SharedWebAutoConfiguration.java` (wrapper бин)
- [x] Создать `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- [x] Unit-тесты GlobalExceptionHandler/JacksonConfig/AdminActionAspect
      — адаптировать импорты

### 0.3 Удаление дублей ErrorResponse (3 contract'а)
- [x] `academic-api-contract`: добавить
      `api(project(":services:shared:shared-web-api"))`
- [x] Удалить `academic-api-contract/.../exception/ErrorResponse.java`
- [x] `schedule-api-contract`: добавить shared-web-api dependency
- [x] Удалить `schedule-api-contract/.../exception/ErrorResponse.java`
- [x] `attendance-api-contract`: добавить shared-web-api dependency
- [x] Удалить `attendance-api-contract/.../exception/ErrorResponse.java`
- [x] `notification-api-contract`: проверить/добавить shared-web-api

### 0.4 Refactor academic GlobalExceptionHandler
- [x] `academic-app/build.gradle.kts`: добавить
      `implementation(project(":services:shared:shared-web-starter"))`
- [x] Удалить из academic `GlobalExceptionHandler`: handleValidation,
      handleAccessDenied, handleNoHandler, handleNoResource,
      handleErrorResponse, handleResponseStatus, handleGeneral
- [x] Оставить domain: handleNotFound, handleBadRequest, handleConflict,
      handleDataIntegrityViolation, handleScheduleUnavailable
- [x] Добавить `@Order(Ordered.HIGHEST_PRECEDENCE)` на academic handler
- [x] Заменить импорт `ErrorResponse` на shared
- [x] Удалить PROBLEM_BASE constant — использовать `ErrorResponse.PROBLEM_BASE`
- [x] Обновить `academic/exception/GlobalExceptionHandlerTest.java`

### 0.5 Refactor schedule GlobalExceptionHandler
- [x] `schedule-app/build.gradle.kts`: добавить shared-web-starter
- [x] Удалить дубли handler'ов (validation, accessDenied, noHandler,
      noResource, errorResponse, responseStatus, general)
- [x] Оставить domain: handleNotFound, handleAcademicUnavailable,
      handleInvalidLessonState, handleDataIntegrity, handleConflict
- [x] `@Order(HIGHEST_PRECEDENCE)` + shared `ErrorResponse` импорт
- [x] Обновить `schedule/integration/SecuritySmokeIT.java` если
      формат 403 body отличается

### 0.6 Refactor attendance GlobalExceptionHandler
- [x] `attendance-app/build.gradle.kts`: добавить shared-web-starter
- [x] Удалить дубли handler'ов
- [x] Оставить domain: handleNotFound, handleBadRequest, handleConflict,
      handleDuplicateKey, handleGeofenceViolation, handleGeofenceBlocked,
      handleRateLimit, handleServiceUnavailable
- [x] `@Order(HIGHEST_PRECEDENCE)` + shared импорт

### 0.7 Refactor auth GlobalExceptionHandler
- [x] `auth-service/build.gradle.kts`: добавить shared-web-starter
- [x] Удалить `auth/dto/ErrorResponse.java`
- [x] Заменить импорт на shared `ErrorResponse`
- [x] Обновить формат body: добавить traceId + problem type URI
      (`PROBLEM_BASE + "unauthorized"` вместо `about:blank`)
- [x] Удалить дубли handler'ов (handleValidation, handleNoResource,
      handleNoHandler, handleErrorResponse, handleResponseStatus,
      handleGeneric)
- [x] Оставить domain: handleInvalidCredentials, handleTokenRefresh,
      handleOtpExpired, handleOtpRateLimit, handleTmaValidation
- [x] `@Order(HIGHEST_PRECEDENCE)` + shared импорт
- [x] Обновить тесты `auth/exception/*Test.java`

### 0.8 Refactor notification
- [x] `notification-app/NotificationWebApplication.java`: убрать
      `scanBasePackages = {..., "ru.rutcampustrack.shared.web"}`
      (Spring Boot подхватит через AutoConfiguration.imports)
- [x] `notification-app/build.gradle.kts`: обновить dependency
      shared-web → shared-web-starter
- [x] Проверить notification NotificationExceptionHandler на дубли

### 0.9 Финал Группы 0
- [x] `./gradlew build` зелёный (все 6 сервисов + 2 shared)
- [x] `./gradlew test` зелёный
- [x] Grep проверка: `record ErrorResponse` → ровно 1 hit
- [x] Grep проверка: `scanBasePackages.*shared\.web` → 0 hits
- [x] `docs/shared-modules-usage.md` обновлён (новая структура)
- [x] `docs/api-error-conventions.md` обновлён (единый format)
- [x] `CLAUDE.md` обновлён (структура shared-*)
- [x] Атомарные коммиты per-0.X (9 коммитов)

## Группа 1 — SharedOpenApiCustomizer наполнение (P2-2/1) ✅

- [x] `GlobalErrorResponsesCustomizer` implements OpenApiCustomizer
      в shared-web-starter
- [x] Config: список стандартных статусов (400/401/403/404/409/429/500)
- [x] Schema reference на shared `ErrorResponse` (прямая class reference,
      не $ref строкой — после Группы 0 единый класс)
- [x] Unit-test: customizer apply-ит responses на sample OpenAPI spec
- [x] Integration test в academic-service: `/v3/api-docs`
      содержит все 7 responses
- [x] Customizer подключён автоматически через
      `SharedWebAutoConfiguration` (Группа 0 обеспечивает)
- [x] `docs/api-error-conventions.md` — раздел «Global error responses»
      (NEW-122)

## Группа 2 — @Schema на DTO (P2-2/4) ✅

- [x] Audit: grep + список всех DTO records по 4 сервисам (report
      в NOTES.md)
- [x] academic-service: `@Schema` на Request DTO (pass 1)
- [x] academic-service: `@Schema` на Response DTO (pass 2)
- [x] academic-service: `@Schema` на embedded/nested DTO
- [x] schedule-service: аналогично pass 1-3
- [x] attendance-service: аналогично
- [x] notification-service: аналогично (после M10 merge)
- [x] Spot-check generated TS types (после re-generation в M07):
      JSDoc присутствует для выбранных 5 DTO
- [x] `docs/api-error-conventions.md` — раздел «@Schema policy»
      (NEW-124)

## Группа 3 — Conformance CI (P2-2/3)

- [ ] Audit `@ApiResponse(responseCode)` vs actual return types
      (grep + manual)
- [ ] Fix void mutations: `@ApiResponse("204")` + `ResponseEntity<Void>`
- [ ] Fix POST creates: `@ApiResponse("201")` + Location header
- [ ] `./gradlew openapi-dump` task per service (spring-doc maven plugin
      alt или custom)
- [ ] Commit initial snapshots `docs/api-spec/*.yaml`
- [ ] CI step `openapi-conformance`:
      bootRun → wait → dump → diff committed — fail on breaking
- [ ] Tool: `openapi-diff` (Azure) или `oasdiff`
- [ ] `docs/openapi-conformance.md` (NEW-123)

## Группа 4 — /swagger-ui prod protection (P2-2/6)

- [ ] nginx location block с basic-auth
- [ ] `infra/nginx/htpasswd/swagger.template` (пустой)
- [ ] Spring profile `local`: bypass basic-auth (через nginx
      profile differentiation)
- [ ] `.env.prod.example` — `SWAGGER_HTPASSWD=<htpasswd -nB swagger>`
- [ ] `docs/runbooks/swagger-prod-access.md` (NEW-125) — как
      получить доступ, ротация
- [ ] Smoke: prod curl без creds → 401, с creds → 200

## Группа 5 — Финализация

- [ ] `./gradlew build` зелёный
- [ ] `./gradlew integrationTest` зелёный
- [ ] CI `openapi-conformance` зелёный
- [ ] Post-mortem в PLAN.md
- [ ] Tag `v0.0.0-alpha.12`

---

_Если задача превращается в 6+ часов работы — разрежь её._
