# M12 — Auth API Contract-first Refactor

**Статус:** ⬜ не начат
**Старт / финиш:** — / —
**Estimate:** 2.5 человеко-дня

---

## Scope

Закрывает последний structural долг Contract-first правила из
`CLAUDE.md`: `auth-service` — единственный backend без
`*-api-contract` модуля. Создаёт `auth-api-contract`, переводит
`AuthController` на `implements AuthApi`, убирает исключение из
CLAUDE.md. После M12 **единственное исключение — `api-gateway`**
(прокси, собственного REST API не публикует).

**Binary-compatible refactor:** DTO shape не меняется, JSON payloads
остаются идентичными. Frontend ломаться не должен; regenerate
openapi-typescript даёт точно такие же типы, только с JSDoc
descriptions после M11.

Источники:
- `01-auth-service.md` P0-1 (auth-api-contract создание)
- `OWNER-ANSWERS.md` P2-2/2 (auth OpenAPI через AuthApi interface)
- `CLAUDE.md` раздел «Правила кодирования → Contract-first»
- `M11/PLAN.md` — `@Schema` policy как reference

**Включено:**

### Module split (01 P0-1)
- Создать `services/auth-service/auth-api-contract/` (java-library,
  **без Lombok**)
- Создать `services/auth-service/auth-app/` (Spring Boot app,
  переименование текущего single-module)
- `settings.gradle.kts` — register new submodules
- `build.gradle.kts` структура per submodule согласно pattern
  `academic-service/{academic-api-contract, academic-app}`
- Docker image name остаётся `auth-service` (минимум breaking на
  infra side)

### DTO migration
Существующие 14 DTO переносятся в contract-модуль как Java records
(где уже records — сохранить; где classes — конвертировать при
возможности):
- **Public endpoints (AuthController):** `LoginRequest`, `TokenResponse`,
  `OtpRequest`, `OtpVerifyRequest`, `OtpVerifyByCodeRequest`,
  `OtpCodeResponse`, `RefreshRequest`, `TmaAuthRequest`,
  `ChangePasswordRequest`
- **WS ticket:** `WsTicketResponse`
- **Internal (из InternalIssuerController, InternalWsTicketController)**:
  `InternalIssueRequest`, `InternalIssueResponse`, `PublicKeyResponse`
- **НЕ переносить:** `ErrorResponse` — используется shared-web из M01

### AuthApi interface
- `AuthApi.java` — `@RequestMapping("/auth")` + все endpoints
- `@Operation`, `@ApiResponse`, `@Schema` per endpoint (OpenAPI-first
  с первого коммита, **P2-2/2 closes automatically**)
- Separate interfaces если domain разделён:
  - `AuthApi` — login, otp, refresh, change-password, tma
  - `WsTicketApi` — ws-ticket endpoints
  - `InternalAuthApi` — internal issuer + internal ws-ticket
    (возможно оставить как internal без OpenAPI public docs)

### Controller refactor
- `AuthController implements AuthApi`; удалить все
  `@RequestMapping`/`@GetMapping`/`@PostMapping` из class/method level
  (наследуются из interface)
- `WsTicketController implements WsTicketApi`
- `InternalIssuerController implements InternalAuthApi`
- `InternalWsTicketController implements InternalAuthApi` (или
  отдельный interface)
- Бизнес-логика (service calls, exception throws) — остаётся на месте

### Internal endpoints policy
**Решение:** internal endpoints (InternalIssuer,
InternalWsTicket) **не публикуются в public OpenAPI**, но всё равно
имеют interface-based routing (consistency with pattern).
- Spring profile `local` — internal endpoints visible в /v3/api-docs
- Spring profile `prod` — nginx basic-auth + `@Hidden` аннотация
  на InternalAuthApi (springdoc скрывает из swagger-ui)

### Tests
- Все существующие IT/unit-тесты AuthController остаются зелёными
  **без изменений** (binary-compatible)
- Один новый тест: `AuthApiContractTest` — проверяет что
  `AuthController implements AuthApi` + mapping annotations ТОЛЬКО
  на interface (grep-based assertion или ArchUnit rule)
- Regenerate openapi-typescript в frontend'ах; integration smoke
  что login flow работает

### CLAUDE.md update
- Раздел «Правила кодирования → Contract-first → Исключения»:
  - **Убрать:** `auth-service` — единственный нарушитель правила
    (01 P0-1), `auth-api-contract` отложен в v0.1 backlog
  - **Оставить:** `api-gateway` — прокси, собственного REST API не
    публикует, `*-api-contract` не нужен (зафиксировано M09 D2)

### future-ideas.md cleanup
- Удалить раздел «Auth API contract-first refactor (v0.1)» —
  перенесено в M12 и закрыто
- Удалить раздел «P2-2/4 @Schema на всех DTO + P2-2/3 swagger-request-validator
  CI (v0.1)» — эти пункты закрываются в M11 (был ошибочно добавлен)

**Исключено (другие milestones):**
- `@Schema(description, example)` policy enforcement — **M11**
  (применяется ко всем contract-модулям, включая auth-api-contract
  после M12)
- OpenAPI conformance CI — **M11** (включит auth-service в scope
  после M12)
- Frontend functional migration (новые features на auth endpoints) —
  v0.1 (M12 только regenerate types, не меняет UI flows)

## Модули / изменения

### Gradle structure
- `settings.gradle.kts` — добавить:
  ```kotlin
  include("services:auth-service:auth-api-contract")
  include("services:auth-service:auth-app")
  ```
- `services/auth-service/auth-api-contract/build.gradle.kts` (NEW)
  — java-library plugin, без Lombok, dependency spring-web (для
  @RequestMapping) + springdoc-openapi (для @Operation/@Schema)
- `services/auth-service/auth-app/build.gradle.kts` — Spring Boot
  app, implementation project(":services:auth-service:auth-api-contract")
- Удалить старый `services/auth-service/build.gradle.kts` (single-module)
  или превратить в parent Gradle config

### auth-api-contract (новый модуль)
- `src/main/java/ru/rutcampustrack/auth/api/AuthApi.java`
- `src/main/java/ru/rutcampustrack/auth/api/WsTicketApi.java`
- `src/main/java/ru/rutcampustrack/auth/api/InternalAuthApi.java`
- `src/main/java/ru/rutcampustrack/auth/dto/` — все 13 DTO (кроме
  ErrorResponse)

### auth-app (переименованный auth-service)
- `src/main/java/ru/rutcampustrack/auth/controller/*.java` —
  `implements` added, mappings удалены
- `src/main/resources/application.yml` — без изменений
- `src/test/java/**` — IT/unit остаются без изменений (возможно,
  import'ы DTO меняют package на contract)

### Dockerfile
- `services/auth-service/Dockerfile` — обновить путь к jar (теперь
  из `auth-app/build/libs/`)
- `docker-compose.yml` + `docker-compose.prod.yml` — путь build
  context

### Frontend
- `frontends/{pwa,web-panel,mini-app}/src/api/generated/` —
  regenerate (не ручная правка)
- Smoke тест: login/logout/refresh flows

### Docs
- `CLAUDE.md` — Contract-first exception update (убрать auth-service)
- `docs/architecture.md` — обновить auth-service раздел, mention
  auth-api-contract
- `docs/future-ideas.md` — удалить auth-api-contract раздел
  + удалить ошибочный P2-2/3+/4 раздел

## Acceptance criteria

- [ ] `services/auth-service/auth-api-contract/` модуль существует,
      собирается через `./gradlew :auth-api-contract:build`
- [ ] `services/auth-service/auth-app/` модуль существует, собирается
- [ ] Все 13 DTO (без ErrorResponse) перенесены в contract-модуль
- [ ] `AuthApi`, `WsTicketApi`, `InternalAuthApi` interfaces созданы
      с полными OpenAPI аннотациями
- [ ] `AuthController implements AuthApi`; grep показывает что
      `@RequestMapping`/`@GetMapping`/`@PostMapping` отсутствуют
      в controller классе (только в interface)
- [ ] `WsTicketController`, `InternalIssuerController`,
      `InternalWsTicketController` — analogical refactor
- [ ] `AuthApiContractTest` (или ArchUnit rule) валидирует
      "mappings only on interface"
- [ ] Все существующие IT/unit-тесты auth-service зелёные без
      изменений бизнес-логики (import fixes допустимы)
- [ ] Docker build зелёный; `docker compose up auth-service` стартует
      healthy
- [ ] Frontend regenerate openapi-typescript успешен; login smoke в
      PWA + web-panel зелёный
- [ ] `/v3/api-docs` auth-service содержит все public endpoints с
      descriptions (после M11 @Schema policy applied)
- [ ] Internal endpoints hidden в prod swagger-ui через `@Hidden`
- [ ] `CLAUDE.md` Contract-first exception обновлён — `auth-service`
      убран, остался только `api-gateway`
- [ ] `docs/architecture.md` обновлён: auth-service описан с
      contract-модулем
- [ ] `docs/future-ideas.md` — auth-api-contract раздел удалён
      (перенесён в M12); ошибочный P2-2/3+/4 раздел удалён
- [ ] Post-mortem секция в PLAN.md, tag `v0.0.0-alpha.12` или
      `v0.0.0-rc.2`

## Dependencies

- **Блокируется:** M07 (openapi-typescript generator нужен для
  regenerate frontend types)
- **Блокируется:** M11 (@Schema policy + conformance CI — после M11
  scope расширяется на auth-service автоматически)
- **Блокирует:** v0.0.0 release tag — убирает последнее нарушение
  Contract-first правила; структурная чистота перед релизом
- **Parallel safe:** M08, M09, M10 (разные scope файлов). M11 →
  M12 sequentiality обязательна (M11 наполняет @Schema, M12
  переносит DTO с @Schema)

## Artifacts

- `services/auth-service/auth-api-contract/` — новый Gradle модуль
- `services/auth-service/auth-app/` — переименованный single-module
- `services/auth-service/auth-api-contract/.../api/AuthApi.java` +
  `WsTicketApi.java` + `InternalAuthApi.java`
- `services/auth-service/auth-api-contract/.../dto/*.java` — 13 DTO
- `CLAUDE.md` — Contract-first exception обновлён
- `docs/architecture.md` — auth-service раздел обновлён
- `docs/future-ideas.md` — cleanup (удалены M12-related разделы и
  ошибочный P2-2/3+/4)

---

_Никаких «why», «motivation», «background» — это в 99-executive-summary.md
и OWNER-ANSWERS.md (01 P0-1, P2-2/2). Здесь только WHAT и DONE._
