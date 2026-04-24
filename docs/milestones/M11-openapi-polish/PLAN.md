# M11 — OpenAPI Polish + Shared Web Starter Refactor

**Статус:** ⬜ не начат
**Старт / финиш:** — / —
**Estimate:** 4-5 человеко-дней (исходный 3 + Группа 0 ~1-2 дня)

---

## Scope

Наполнение bean-заглушки `SharedOpenApiCustomizer` (M01), полная
спецификация @Schema на DTO, nginx basic-auth на prod /swagger-ui,
conformance-checking спецификации с runtime + **Группа 0: унификация
`ErrorResponse` и refactor `shared-web` в идиоматический Spring Boot
starter** (устранение M01 архитектурного долга).

Закрывает P2-2/1, /3, /4, /6 из `OWNER-ANSWERS.md` + D2 (NEW-34
«shared-web как java-library»).

Источники:
- `99-executive-summary.md` — Фаза 4 P1-B (OpenAPI quality)
- `OWNER-ANSWERS.md` P2-2/1..6 (строки 2692-2880)
- `M01/PLAN.md:222` — bean-заглушка + defer «наполнить в M6»
- `M01/DECISIONS.md` D2 — NEW-34 shared-web как java-library (refactor
  в M11 Группа 0 после решения owner'а)
- `15-cross-cutting-issues.md` — QC-05/06 OpenAPI рассинхрон
- `M07/PLAN.md` — QC2 openapi-typescript (работает точнее после M11
  @Schema descriptions)

**Включено:**

### Группа 0 — Shared Web Starter + ErrorResponse унификация (NEW)

Legacy-устранение. До M11 в codebase **5 разных `ErrorResponse`**:
- `shared-web`: 10 полей (`invalidParams` + `field` + `extras`), RFC 9457
- `academic-api-contract`: 9 полей (`fieldErrors` + `field` + `extras`), RFC 7807
- `schedule-api-contract`: 7 полей (`fieldErrors`), RFC 7807
- `attendance-api-contract`: 7 полей (`fieldErrors`), RFC 7807
- `auth/dto`: 6 полей (без `fieldErrors` вообще), `about:blank` type

`shared-web` является `java-library` (NEW-34), но используется как
starter-like (scanBasePackages в notification-app). 3 сервиса
дублируют handler'ы (`handleValidation`, `handleAccessDenied`,
`handleNoHandler`, `handleGeneral`, `handleNoResource`,
`handleErrorResponse`, `handleResponseStatus`) с идентичной логикой,
отличающейся только ссылкой на локальный `ErrorResponse`.

**Архитектурные принципы (как в Netflix / Square / Spring Cloud):**
- Разделение `shared-web-api` (чистые типы, java-library) +
  `shared-web-starter` (Spring Boot auto-config beans)
- Auto-configuration через
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  (Spring Boot 3 idiom) — вместо `scanBasePackages` hack'а
- Single source of truth для `ErrorResponse` — один класс в
  `shared-web-api`, без дублей
- `@Order(Ordered.HIGHEST_PRECEDENCE)` для per-service domain handlers
  (academic: `ConflictException`, attendance: `GeofenceViolationException`),
  `@Order(Ordered.LOWEST_PRECEDENCE)` для shared MVC/generic handlers

**Задачи:**

1. **Split `shared-web` → 2 модуля:**
   - `shared-web-api` (NEW): `ErrorResponse`, `FieldError`, `InvalidParam`
     (deprecated → alias для `FieldError`), validation аннотации
     (`StartBeforeEnd`, `DateRangeValid`, `ValidFile`). `java-library`,
     без Spring. Зависит от `jakarta.validation-api`, `jackson-annotations`,
     `swagger-annotations`.
   - `shared-web-starter` (RENAME из `shared-web`):
     `GlobalExceptionHandler`, `JacksonConfig`, `SharedOpenApiCustomizer`,
     `AdminActionAspect`. Зависит `api(project(":shared-web-api"))`.
     Добавляет `SharedWebAutoConfiguration` + `AutoConfiguration.imports`.

2. **Унификация `ErrorResponse`:**
   - 10-field superset (RFC 9457) в `shared-web-api`
   - Переименовать `invalidParams` → `fieldErrors` (согласовано с
     frontend через существующий M07 generated types — ломать frontend
     не нужно)
   - `FieldError` — top-level record (вынесен из inner)
   - `InvalidParam` — deprecated alias (`extends FieldError` невозможно
     для record, поэтому deprecate + migration path в v0.1)
   - 9-arg + 8-arg + 7-arg backward-compat конструкторы

3. **Удалить 4 дубля:**
   - `academic-api-contract/.../ErrorResponse.java` + FieldError
   - `schedule-api-contract/.../ErrorResponse.java` + FieldError
   - `attendance-api-contract/.../ErrorResponse.java` + FieldError
   - `auth/dto/ErrorResponse.java` (полная переделка на shared format)

4. **3 `*-api-contract` получают `api(project(":shared-web-api"))`**
   (transitive для consumer'ов; frontend openapi-typescript
   увидит `$ref` на shared схему)

5. **Подключить `shared-web-starter` как `implementation` в 5 сервисах:**
   - academic-app, schedule-app, attendance-app, auth-service,
     notification-app
   - Удалить `scanBasePackages` из `NotificationWebApplication`
   - `@ConditionalOnWebApplication(type = SERVLET)` защищает gateway
     (WebFlux) от накрытия

6. **Refactor `GlobalExceptionHandler` per-service:**
   - Удалить дубль-handler'ы (handleValidation, handleAccessDenied,
     handleNoHandler, handleNoResource, handleErrorResponse,
     handleResponseStatus, handleGeneral) — shared берёт на себя
   - Оставить только domain exceptions:
     - academic: `ResourceNotFoundException`, `ConflictException`,
       `BadRequestException`, `DataIntegrityViolationException`
       (constraint → field mapping), `ScheduleServiceUnavailableException`
     - schedule: `ResourceNotFoundException`,
       `AcademicServiceUnavailableException`,
       `InvalidLessonStateException`, `DataIntegrityViolationException`,
       `ConflictException`
     - attendance: `ResourceNotFoundException`, `BadRequestException`,
       `ConflictException`, `DuplicateKeyException`,
       `GeofenceViolationException`, `GeofenceBlockedException`,
       `RateLimitException`, `ScheduleServiceUnavailableException`,
       `AcademicServiceUnavailableException`
     - auth: `InvalidCredentialsException`, `TokenRefreshException`,
       `OtpExpiredException`, `OtpRateLimitException`,
       `TmaValidationException`
   - `@Order(Ordered.HIGHEST_PRECEDENCE)` на domain handler
   - `@Order(Ordered.LOWEST_PRECEDENCE)` на shared handler (уже есть)

7. **Миграция тестов:**
   - `academic/GlobalExceptionHandlerTest` — адаптировать под новый scope
     (domain exceptions only)
   - `schedule/SecuritySmokeIT` — 403 format может отличаться (shared
     вместо per-service)
   - `attendance/NotificationErrorHandlingIT`-аналог — если есть
   - Frontend unit-тесты парсинга `ErrorResponse` — проверить поля

8. **Обновить docs:**
   - `docs/shared-modules-usage.md` — новая структура (api + starter)
   - `docs/api-error-conventions.md` — единый `ErrorResponse` формат
   - `CLAUDE.md` — shared-web → shared-web-api + shared-web-starter

### SharedOpenApiCustomizer наполнение (P2-2/1)
- `GlobalErrorResponsesCustomizer` implements OpenApiCustomizer
- Автоматически добавляет `@ApiResponse` для стандартных статусов
  на все endpoints:
  - 400 Bad Request (validation) — `application/problem+json`
  - 401 Unauthorized (missing/invalid JWT)
  - 403 Forbidden (role/IDOR)
  - 404 Not Found
  - 409 Conflict (optimistic locking, duplicate)
  - 429 Too Many Requests (rate-limit)
  - 500 Internal Server Error
- `content = @Content(mediaType = "application/problem+json",
    schema = @Schema(implementation = ErrorResponse.class))` — прямая
  class reference (после Группы 0, единый shared класс)
- Per-endpoint override всё ещё работает (customizer — defaults)
- NEW-122 — «Global error responses» раздел в `api-error-conventions.md`

### @Schema на DTO полях (P2-2/4)
- Audit всех ~60-80 DTO records + response classes в 4 сервисах
- `@Schema(description = "...", example = "...")` на каждое поле
- Priority pass:
  1. Request DTO (input для API consumers — критично)
  2. Response DTO верхнего уровня
  3. Embedded DTO (nested records)
- `ErrorResponse` + `FieldError` — уже с @Schema (Группа 0 сохраняет
  академический + schedule + attendance descriptions через слияние
  лучших примеров)
- Generated TypeScript types (QC2 M07) автоматически станут точнее —
  descriptions/examples попадут в JSDoc
- NEW-124 — ADR «@Schema policy» в `docs/api-error-conventions.md`

### OpenAPI ↔ runtime conformance (P2-2/3)
- Audit `@ApiResponse(responseCode = "200")` vs actual controller
  responses:
  - Void mutations должны быть `204 No Content`, не `200`
  - `ResponseEntity<Void>` return → `@ApiResponse("204")`
  - POST creates → `@ApiResponse("201")` + `Location` header
- CI check (blocking gate):
  `atlassian/swagger-request-validator` или
  `openapi-diff` против `/v3/api-docs` runtime dump
- Smoke: `./gradlew openapi-dump` → compare с committed spec
- NEW-123 — `docs/openapi-conformance.md` runbook

### /swagger-ui protection в prod (P2-2/6)
- nginx basic-auth location block для `/swagger-ui/**` + `/v3/api-docs`
- `htpasswd` file: один user `swagger` с `$SWAGGER_PASSWORD` from env
- Dev/local env — open (без basic-auth, Spring profile-based toggle)
- Prod `.env.prod.example` — добавить `SWAGGER_HTPASSWD=...` (пароль
  генерится при deploy через `htpasswd -c`)
- NEW-125 — `docs/runbooks/swagger-prod-access.md`: как получить
  доступ к prod swagger (password rotation процедура)

**Исключено (другие milestones / backlog):**
- **P2-2/2** auth-service OpenAPI через `AuthApi` interface —
  **future-ideas v0.1** (связан с `auth-api-contract` refactor,
  01 P0-1 отложен в v0.1). Группа 0 **унифицирует `ErrorResponse`**,
  но не создаёт `auth-api-contract` модуль — это отдельная работа M12.
- **openapi-typescript generation** — в **M07 Группа 3** (генерация +
  локальный drift-check)
- **openapi-typescript CI blocking gate** — **M08 Группа 10** (coverage
  + drift в CI)

## Модули / изменения

### shared-web-api (NEW, Группа 0)
- `services/shared/shared-web-api/build.gradle.kts` — чистый java-library
- `services/shared/shared-web-api/.../exception/ErrorResponse.java`
  (унифицированный superset)
- `services/shared/shared-web-api/.../exception/FieldError.java`
- `services/shared/shared-web-api/.../exception/InvalidParam.java`
  (deprecated alias)
- `services/shared/shared-web-api/.../validation/*.java` (переехали
  из shared-web)

### shared-web-starter (RENAME из shared-web, Группа 0)
- `services/shared/shared-web-starter/.../autoconfigure/SharedWebAutoConfiguration.java`
  (NEW)
- `services/shared/shared-web-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  (NEW)
- `services/shared/shared-web-starter/.../config/SharedOpenApiCustomizer.java`
  (наполнение в Группе 1)
- `services/shared/shared-web-starter/.../exception/GlobalExceptionHandler.java`
  (existing, без изменений)
- `services/shared/shared-web-starter/.../config/JacksonConfig.java`
- `services/shared/shared-web-starter/.../audit/AdminActionAspect.java`
- Test: `GlobalErrorResponsesCustomizerTest` + integration через
  academic-service sample (Группа 1)

### Per-service contracts (Группа 0 — удаление дублей)
- `services/academic-service/academic-api-contract/build.gradle.kts` —
  добавить `api(project(":services:shared:shared-web-api"))`
- `services/academic-service/academic-api-contract/.../exception/ErrorResponse.java`
  — УДАЛИТЬ
- `services/academic-service/academic-api-contract/.../exception/FieldError.java`
  (если был вынесен) — УДАЛИТЬ
- Аналогично schedule-api-contract, attendance-api-contract
- `services/notification-service/notification-api-contract` — добавить
  зависимость на shared-web-api (если M10 не сделал уже)

### Per-service apps (Группа 0 — handler refactor)
- `services/academic-service/academic-app/.../exception/GlobalExceptionHandler.java`
  — удалить shared handlers (handleValidation, handleAccessDenied,
  handleNoHandler, handleNoResource, handleErrorResponse,
  handleResponseStatus, handleGeneral), добавить `@Order(HIGHEST_PRECEDENCE)`,
  заменить импорт `ErrorResponse` на shared
- `services/academic-service/academic-app/build.gradle.kts` — добавить
  `implementation(project(":services:shared:shared-web-starter"))`
- Аналогично schedule-app, attendance-app
- `services/auth-service/.../exception/GlobalExceptionHandler.java`
  — унификация ErrorResponse formatting (traceId, problem type URI)
- `services/auth-service/.../dto/ErrorResponse.java` — УДАЛИТЬ
- `services/auth-service/build.gradle.kts` — добавить shared-web-starter
- `services/notification-service/notification-app/.../NotificationWebApplication.java`
  — убрать `scanBasePackages` hack

### Per-service DTO (Группа 2 — @Schema audit)
- `services/academic-service/academic-api-contract/.../dto/*.java` —
  `@Schema` per field
- `services/schedule-service/schedule-api-contract/.../dto/*.java`
- `services/attendance-service/attendance-api-contract/.../dto/*.java`
- `services/notification-service/notification-api-contract/.../dto/*.java`
  (создан в M10, `@Schema` здесь финализируется)
- auth-service — **skip** (P2-2/2 → v0.1, `auth-api-contract` нет)

### Per-service controllers (Группа 3)
- Audit `@ApiResponse(responseCode)` vs actual return types
- Fix void mutations → 204

### infra/nginx (Группа 4)
- `infra/nginx/nginx.conf` — new location block:
  ```
  location ~ ^/(swagger-ui|v3/api-docs) {
      auth_basic "Swagger UI";
      auth_basic_user_file /etc/nginx/htpasswd/swagger;
      proxy_pass http://api-gateway:8080;
  }
  ```
- `infra/nginx/htpasswd/swagger.template` — пустой template файл

### CI (Группа 3)
- `.github/workflows/ci.yml` — новый step `openapi-conformance`

### Docs
- `docs/shared-modules-usage.md` — обновить (Группа 0)
- `docs/api-error-conventions.md` — расширение: «Global error responses»
  + «@Schema policy» + единый ErrorResponse (NEW-122, NEW-124)
- `docs/openapi-conformance.md` (NEW-123) — runbook
- `docs/runbooks/swagger-prod-access.md` (NEW-125)
- `docs/api-spec/` (NEW directory) — committed OpenAPI snapshots
- `CLAUDE.md` — структура shared-* модулей

### .env.prod.example
- `SWAGGER_HTPASSWD` — новая переменная

### settings.gradle.kts
- Добавить `include("services:shared:shared-web-api")`
- Переименовать `include("services:shared:shared-web")` →
  `include("services:shared:shared-web-starter")`

## Acceptance criteria

**Группа 0:**
- [ ] Единственный `ErrorResponse` класс в repo (grep `record ErrorResponse`
      → ровно 1 hit в `shared-web-api`)
- [ ] 5 сервисов (academic/schedule/attendance/auth/notification)
      используют shared `ErrorResponse` и shared `GlobalExceptionHandler`
- [ ] Все существующие `*IT` зелёные после refactor'а
- [ ] Frontend типы (`PWA + web-panel`) продолжают парсить error body
      без breaking changes (поле `fieldErrors` сохранено)
- [ ] `shared-web-starter` через `META-INF/.../AutoConfiguration.imports`
      — нет `scanBasePackages = {..., "ru.rutcampustrack.shared.web"}`
      ни в одном `*Application.java`

**Группа 1-4 (оригинал):**
- [ ] `GlobalErrorResponsesCustomizer` автоматически добавляет 7
      стандартных `@ApiResponse` (400/401/403/404/409/429/500) на все
      endpoints всех 5 сервисов
- [ ] `/v3/api-docs` каждого сервиса содержит стандартные error
      responses для всех endpoints (unit-check через
      `MockMvc /v3/api-docs`)
- [ ] ≥80% DTO полей имеют `@Schema(description, example)`
- [ ] Generated TypeScript types (M07 QC2) содержат JSDoc с
      descriptions/examples — spot check 3-5 DTO
- [ ] Void mutations возвращают 204 (а не 200); integration tests
      обновлены
- [ ] CI step `openapi-conformance` проходит
- [ ] nginx basic-auth защищает `/swagger-ui` + `/v3/api-docs` в
      prod; smoke curl без creds → 401
- [ ] Dev (Spring profile `local`) — без basic-auth работает
- [ ] `docs/api-error-conventions.md` обновлён
- [ ] `docs/openapi-conformance.md` + `docs/runbooks/swagger-prod-access.md`
- [ ] `docs/api-spec/` содержит committed snapshot per service
- [ ] Post-mortem секция в PLAN.md, tag `v0.0.0-alpha.12`

## Dependencies

- **Блокируется:** M01 (bean-заглушка ✅, NEW-34 decision ✅),
  M10 (notification-api-contract ✅)
- **Блокирует:** v0.0.0 release (CSP-style protection для swagger
  в prod — security-blocker)
- **Parallel safe:** M08, M09. Желательно **после M07** (openapi-ts
  в M07 будет регенерирован с новыми descriptions после M11).
- **Упрощает:** M12 (auth-api-contract refactor — ErrorResponse уже
  унифицирован, остаётся только AuthApi interface extract)

## Artifacts

- `services/shared/shared-web-api/` (NEW модуль)
- `services/shared/shared-web-starter/` (переименован из shared-web)
- `services/shared/shared-web-starter/.../autoconfigure/SharedWebAutoConfiguration.java`
- `services/shared/shared-web-starter/.../config/SharedOpenApiCustomizer.java`
  (наполнение)
- `services/shared/shared-web-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `infra/nginx/nginx.conf` — swagger basic-auth block
- `infra/nginx/htpasswd/swagger.template`
- `docs/api-spec/{academic,schedule,attendance,notification}.yaml`
- `docs/api-error-conventions.md` — расширение
- `docs/openapi-conformance.md` (NEW-123)
- `docs/runbooks/swagger-prod-access.md` (NEW-125)
- `.env.prod.example` — новая переменная `SWAGGER_HTPASSWD`

---

_Никаких «why», «motivation», «background» — это уже в 99-executive-summary.md
и OWNER-ANSWERS.md (P2-2/1..6) + NOTES.md (S1 surprise). Здесь только
WHAT и DONE-критерии._
