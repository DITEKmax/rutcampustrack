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

## Surprises обнаруженные в G1 старте (2026-04-24)

### S1 — shared-web подключён только в notification-app

**Факт:** из 4 сервисов с REST API (academic / schedule / attendance /
notification) только `notification-app` имеет
`implementation(project(":services:shared:shared-web"))` в build.gradle.kts
и `scanBasePackages = {..., "ru.rutcampustrack.shared.web"}` в
`@SpringBootApplication`.

`academic-app`, `schedule-app`, `attendance-app`:
- НЕ зависят от shared-web модуля
- Имеют **собственный** `GlobalExceptionHandler` в `*.exception`
- Имеют **собственный** `ErrorResponse` record в `*-api-contract`
  (academic 9-arg, schedule 7-arg, attendance 7-arg)

**Последствие для M11 G1:**
План предполагает «подключить customizer во все 4 сервиса (auto via
spring-boot `@ConditionalOnClass`)» — но автомата НЕТ:
1. shared-web — `java-library`, без auto-configuration
2. classpath не содержит `OpenApiCustomizer` пока shared-web нет
3. component scan не сканирует `ru.rutcampustrack.shared.web`

**Варианты решения (требуют owner go):**

A) **Добавить shared-web dependency + scan в 3 сервиса**
   (academic/schedule/attendance). Минимум: `compileOnly` +
   scan. Плюсы: M11 G1 закрывается за 1 commit, single source of truth для
   Customizer. Минусы: shared-web `ErrorResponse` (RFC 9457, 10 полей,
   `invalidParams`+`field`+`extras`) ≠ contract `ErrorResponse` (RFC 7807,
   7-9 полей, `fieldErrors`); customizer ссылается на `ErrorResponse` —
   придётся выбрать одну (или ссылаться по generic name).

B) **Дублировать customizer в каждом `*-app`**, ссылаясь на
   соответствующий contract `ErrorResponse`. Минусы: drift между
   сервисами, scope M11 удваивается, M01 заглушка остаётся unused
   (только в notification).

C) **Контракт-агностичный customizer в shared-web**: ссылаться на
   `$ref` по строковому имени `ErrorResponse` (в каждом сервисе оно
   присутствует — JsonInclude/SwaggerSchema автоматически зарегистрируют
   при сканировании Controller'ов). Это **рекомендуемый default** —
   minimal blast radius, M01 заглушка наполняется без изменения 3
   сервисов кроме `+1 dependency` каждый.

D) **AutoConfiguration в shared-web**: `META-INF/spring/`...
   `AutoConfiguration.imports`. Тогда `implementation` достаточно, scan
   не нужен. Согласуется с принципом «java-library, не starter» —
   нарушение, но minor (только для customizer).

**Рекомендация:** **C + D** — добавить shared-web как `implementation`
в 3 сервиса + переоформить `SharedOpenApiCustomizer` как
`@AutoConfiguration` (новый файл
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`),
customizer использует `$ref: "#/components/schemas/ErrorResponse"` без
прямого class reference. Springdoc подхватит локальный
`ru.rutcampustrack.{service}.contract.exception.ErrorResponse` через
обычный controller scan.

**ВОПРОС OWNER'У:** какой вариант (A/B/C/D или комбо)? Default — C+D.

### РЕШЕНИЕ OWNER'А (2026-04-24): E — полная унификация + starter split

Owner: "хочу как в больших компаниях, без дублирования".

**Выбран вариант E (расширение C+D):**
1. Split `shared-web` на 2 модуля:
   - `shared-web-api` — чистые DTO типы (`ErrorResponse`, `FieldError`,
     `InvalidParam`). Java-library без Spring.
   - `shared-web-starter` — Spring Boot beans + `@AutoConfiguration`.
2. Единый `ErrorResponse` (10 полей, RFC 9457 superset) — удаляем 5
   дублей (4 contract'а + auth/dto)
3. Per-service `GlobalExceptionHandler` остаётся, но только для domain
   exceptions (`@Order(HIGHEST_PRECEDENCE)`). Shared handler берёт
   catch-all MVC exceptions.
4. `notification-app` убирает `scanBasePackages` hack — заменяет на
   AutoConfiguration.imports.

**Scope M11 расширен до 4-5 дней**, Группа 0 добавлена в CHECKLIST.

### G0.1 старт — сюрпризы валидаторов

**Факт:** `StartBeforeEndValidator`, `DateRangeValidator`,
`ValidFileValidator` используют Spring (`BeanWrapper`,
`PropertyAccessorFactory`, `MultipartFile`). Чистый `shared-web-api`
java-library без Spring не может содержать эти validators.

**Проверка:** `grep @StartBeforeEnd|@DateRangeValid|@ValidFile` в
`services/**/*.java` → 0 production consumers. Только тесты в
`shared-web/src/test/`. Dead code в M01, но не scope M11.

**Решение:** validators + их аннотации **остаются в
shared-web-starter** (они всё равно Spring-зависимые). В
shared-web-api только 3 класса: `ErrorResponse`, `FieldError`,
`InvalidParam`. AdminAction / AdminActionAspect / JacksonConfig тоже
Spring — остаются в starter. Это упрощает G0.1 с 8 задач до 3.

**Обновление чеклиста:** перенос validation — **skip**, записано как
follow-up в v0.1.

### S2 — auth-service имеет свой `org.springdoc:...-webmvc-ui` (без shared-web)

`auth-service/build.gradle.kts:35` — есть springdoc, но shared-web нет
(M01 не подключал, см. S1). M11 PLAN явно skip'ает auth-service для
@Schema audit (P2-2/2 → v0.1), но customizer от shared-web ему тоже не
прилетит. Acceptable — auth-service в любом случае идёт в M12 refactor.

### S3 — `api-gateway` использует `springdoc-openapi-starter-webflux-ui`

Gateway (WebFlux) ≠ остальные (WebMVC). Gateway не публикует свои
endpoints (только proxy). Customizer от shared-web (WebMVC-only) на
gateway не применится — это OK, M11 scope для backend services.

## Deferred в v0.1

- **P2-2/2 auth-service OpenAPI** через `AuthApi` interface — вместе
  с 01 P0-1 (auth-api-contract refactor).
- **Auto-generated examples** через faker (сейчас examples ручные) —
  nice-to-have.
- **Documentation theme/branding** на /swagger-ui (custom CSS) — v0.1.

---
