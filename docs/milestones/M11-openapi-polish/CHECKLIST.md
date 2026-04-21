# M11 Checklist

Атомарные задачи в порядке выполнения. Одна строка = 30 мин - 2 часа.

## Группа 1 — SharedOpenApiCustomizer наполнение (P2-2/1)

- [ ] `GlobalErrorResponsesCustomizer` implements OpenApiCustomizer
      в shared-web
- [ ] Config: список стандартных статусов (400/401/403/404/409/429/500)
- [ ] Schema reference на shared `ErrorResponse` с media-type
      `application/problem+json`
- [ ] Unit-test: customizer apply-ит responses на sample OpenAPI spec
- [ ] Integration test в academic-service: `/v3/api-docs`
      содержит все 7 responses
- [ ] Подключить customizer во все 4 сервиса (auto via spring-boot
      `@ConditionalOnClass`)
- [ ] `docs/api-error-conventions.md` — раздел «Global error responses»
      (NEW-122)

## Группа 2 — @Schema на DTO (P2-2/4)

- [ ] Audit: grep + список всех DTO records по 4 сервисам (report
      в NOTES.md)
- [ ] academic-service: `@Schema` на Request DTO (pass 1)
- [ ] academic-service: `@Schema` на Response DTO (pass 2)
- [ ] academic-service: `@Schema` на embedded/nested DTO
- [ ] schedule-service: аналогично pass 1-3
- [ ] attendance-service: аналогично
- [ ] notification-service: аналогично (после M10 merge)
- [ ] Spot-check generated TS types (после re-generation в M07):
      JSDoc присутствует для выбранных 5 DTO
- [ ] `docs/api-error-conventions.md` — раздел «@Schema policy»
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
- [ ] Tag `v0.0.0-alpha.11`

---

_Если задача превращается в 6+ часов работы — разрежь её._
