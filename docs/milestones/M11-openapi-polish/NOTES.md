# M11 Notes

Живой файл. Отклонения, измерения, surprises, вопросы к владельцу.

---

## Вопросы к owner'у до старта

1. **Conformance tool выбор** — `openapi-diff` (Azure, Java) vs
   `oasdiff` (Tufin, Go). Первый ближе к Spring экосистеме, второй
   быстрее и мощнее. **Default:** `oasdiff` (docker image, не
   требует Java installations в CI runners).

2. **DTO coverage threshold** — ≥80% @Schema coverage или 100%?
   **Default:** ≥80% — оставить низкоприоритетные embedded legacy DTO
   без `@Schema` (spot-check на quality, не на покрытие).

3. **/swagger-ui в staging** — оставляем basic-auth или open?
   **Default:** basic-auth везде кроме `local` profile (dev машины).

4. **Swagger password rotation cadence** — quarterly или раз в
   полгода? **Default:** раз в полгода + после каждого leave
   developer'а (связать с `secret-rotation.md` из M09).

## Ожидаемые surprises

- **P2-2/2 auth-service deferred** — если владелец передумает и
  захочет `auth-api-contract` раньше v0.1, scope M11 удвоится.
  Проверить в discussion.
- **Springdoc customizer ordering** — `GlobalErrorResponsesCustomizer`
  должен применяться ПЕРЕД per-endpoint overrides. Spring ordering
  через `@Order(Ordered.LOWEST_PRECEDENCE)` или explicit bean order.
- **@Schema в records** — Java 21 records не поддерживают все
  аннотации на компонентах напрямую, иногда нужен canonical
  constructor или accessor method annotation. Проверить на первом
  DTO.
- **openapi-typescript regeneration trigger** — M07 generator
  локальный, M11 обновление @Schema не запустит auto-regen. Нужен
  либо скрипт `npm run generate:types` в pre-commit, либо ручная
  regeneration в M11 финале.
- **CI conformance может быть flaky** — `bootRun` + wait requires
  stable startup. Testcontainers сделает надёжнее, но usually
  `/v3/api-docs` доступен сразу после `/actuator/health` UP.
- **ErrorResponse schema 9-field vs 8-field** — academic-service
  имеет `field` + `extras` (M01 DECISIONS), остальные 8 полей.
  Customizer должен использовать union или общий base.

## Связь с другими milestones

### С M01
- `SharedOpenApiCustomizer` bean-заглушка с
  `@ConditionalOnClass(OpenApiCustomizer.class)` — уже создана в M01.
  Здесь наполняем.

### С M07 Frontend Hardening
- **M07 Группа 3** QC2 генерирует TypeScript types из `/v3/api-docs`.
- **M11 наполнение @Schema** улучшает quality generated types
  (JSDoc с descriptions).
- **Порядок:** желательно M11 **после** M07 Группа 3 (generator
  работает), затем **regenerate** после M11 descriptions.

### С M10 Notification History
- `notification-api-contract` создаётся в M10 с первичными
  @Operation/@ApiResponse.
- **M11 финализирует @Schema** на DTO полях notification-api-contract
  (pass из Группы 2).

### С M08 Test Infrastructure
- **CI `openapi-conformance` step** — часть M11 scope.
- **Coverage gate** — не влияет (shared-web customizer покрыт unit-тестом).

## Deferred в v0.1

- **P2-2/2 auth-service OpenAPI** через `AuthApi` interface — вместе
  с 01 P0-1 (auth-api-contract refactor).
- **Auto-generated examples** через faker (сейчас examples ручные) —
  nice-to-have.
- **Documentation theme/branding** на /swagger-ui (custom CSS) — v0.1.

---
