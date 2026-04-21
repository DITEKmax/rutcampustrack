# M11 — OpenAPI Polish

**Статус:** ⬜ не начат
**Старт / финиш:** — / —
**Estimate:** 3 человеко-дня

---

## Scope

Наполнение bean-заглушки `SharedOpenApiCustomizer` (M01), полная
спецификация @Schema на DTO, nginx basic-auth на prod /swagger-ui,
conformance-checking спецификации с runtime. Закрывает P2-2/1, /3, /4, /6
из `OWNER-ANSWERS.md`.

Источники:
- `99-executive-summary.md` — Фаза 4 P1-B (OpenAPI quality)
- `OWNER-ANSWERS.md` P2-2/1..6 (строки 2692-2880)
- `M01/PLAN.md:222` — bean-заглушка + defer «наполнить в M6»
- `15-cross-cutting-issues.md` — QC-05/06 OpenAPI рассинхрон
- `M07/PLAN.md` — QC2 openapi-typescript (работает точнее после M11
  @Schema descriptions)

**Включено:**

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
    schema = @Schema(implementation = ErrorResponse.class))`
- Per-endpoint override всё ещё работает (customizer — defaults)
- NEW-122 — «Global error responses» раздел в `api-error-conventions.md`

### @Schema на DTO полях (P2-2/4)
- Audit всех ~60-80 DTO records + response classes в 4 сервисах
- `@Schema(description = "...", example = "...")` на каждое поле
- Priority pass:
  1. Request DTO (input для API consumers — критично)
  2. Response DTO верхнего уровня
  3. Embedded DTO (nested records)
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
  01 P0-1 отложен в v0.1)
- **openapi-typescript generation** — в **M07 Группа 3** (генерация +
  локальный drift-check)
- **openapi-typescript CI blocking gate** — **M08 Группа 10** (coverage
  + drift в CI)

## Модули / изменения

### shared-web
- `services/shared/shared-web/.../openapi/GlobalErrorResponsesCustomizer.java`
  (NEW — наполнение заглушки из M01)
- `services/shared/shared-web/.../openapi/OpenApiConstants.java` —
  media types, schema references
- Test: `GlobalErrorResponsesCustomizerTest` — smoke + интеграция в
  academic-service (sample)

### Per-service DTO
- `services/academic-service/academic-api-contract/.../dto/*.java` —
  `@Schema` per field
- `services/schedule-service/schedule-api-contract/.../dto/*.java` —
  аналогично
- `services/attendance-service/attendance-api-contract/.../dto/*.java`
- `services/notification-service/notification-api-contract/.../dto/*.java`
  (создан в M10, `@Schema` здесь финализируется)
- auth-service — **skip** (P2-2/2 → v0.1, `auth-api-contract` нет)

### Per-service controllers
- Audit `@ApiResponse(responseCode)` vs actual return types
- Fix void mutations → 204

### infra/nginx
- `infra/nginx/nginx.conf` — new location block:
  ```
  location ~ ^/(swagger-ui|v3/api-docs) {
      auth_basic "Swagger UI";
      auth_basic_user_file /etc/nginx/htpasswd/swagger;
      proxy_pass http://api-gateway:8080;
  }
  ```
- `infra/nginx/htpasswd/swagger.template` — пустой template файл

### CI
- `.github/workflows/ci.yml` — новый step `openapi-conformance`:
  1. `./gradlew bootRun &` (all services)
  2. Wait for /actuator/health
  3. `curl /v3/api-docs > actual-spec.json`
  4. Compare с committed `docs/api-spec/{service}.yaml` через
     `openapi-diff` (breaking changes → fail)

### Docs
- `docs/api-error-conventions.md` — расширение: «Global error responses»
  + «@Schema policy» (NEW-122, NEW-124)
- `docs/openapi-conformance.md` (NEW-123) — runbook
- `docs/runbooks/swagger-prod-access.md` (NEW-125)
- `docs/api-spec/` (NEW directory) — committed OpenAPI snapshots для
  conformance check

### .env.prod.example
- `SWAGGER_HTPASSWD` — новая переменная

## Acceptance criteria

- [ ] `GlobalErrorResponsesCustomizer` автоматически добавляет 7
      стандартных `@ApiResponse` (400/401/403/404/409/429/500) на все
      endpoints всех 4 сервисов (academic/schedule/attendance/notification)
- [ ] `/v3/api-docs` каждого сервиса содержит стандартные error
      responses для всех endpoints (unit-check через
      `MockMvc /v3/api-docs`)
- [ ] ≥80% DTO полей имеют `@Schema(description, example)` — критерий
      audit через grep + ручной spot-check
- [ ] Generated TypeScript types (M07 QC2) содержат JSDoc с
      descriptions/examples — spot check 3-5 DTO
- [ ] Void mutations возвращают 204 (а не 200); integration tests
      обновлены
- [ ] CI step `openapi-conformance` проходит: runtime
      `/v3/api-docs` ↔ committed spec — в sync
- [ ] nginx basic-auth защищает `/swagger-ui` + `/v3/api-docs` в
      prod; smoke curl без creds → 401
- [ ] Dev (Spring profile `local`) — без basic-auth работает
- [ ] `docs/api-error-conventions.md` обновлён
- [ ] `docs/openapi-conformance.md` + `docs/runbooks/swagger-prod-access.md`
- [ ] `docs/api-spec/` содержит committed snapshot per service
- [ ] Post-mortem секция в PLAN.md, tag `v0.0.0-alpha.11`

## Dependencies

- **Блокируется:** M01 (bean-заглушка ✅), M10 (notification-api-contract
  ожидается к моменту старта M11 — тогда все 4 contract-модуля
  имеют полные @Schema)
- **Блокирует:** v0.0.0 release (CSP-style protection для swagger
  в prod — security-blocker)
- **Parallel safe:** M08, M09. Желательно **после M07** (openapi-ts
  в M07 будет регенерирован с новыми descriptions после M11).

## Artifacts

- `services/shared/shared-web/.../openapi/GlobalErrorResponsesCustomizer.java`
- `infra/nginx/nginx.conf` — swagger basic-auth block
- `infra/nginx/htpasswd/swagger.template`
- `docs/api-spec/{academic,schedule,attendance,notification}.yaml` —
  committed OpenAPI snapshots
- `docs/api-error-conventions.md` — расширение
- `docs/openapi-conformance.md` (NEW-123)
- `docs/runbooks/swagger-prod-access.md` (NEW-125)
- `.env.prod.example` — новая переменная `SWAGGER_HTPASSWD`

---

_Никаких «why», «motivation», «background» — это уже в 99-executive-summary.md
и OWNER-ANSWERS.md (P2-2/1..6). Здесь только WHAT и DONE-критерии._
