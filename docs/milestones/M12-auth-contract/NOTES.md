# M12 Notes

Живой файл. Отклонения, измерения, surprises, вопросы к владельцу.

---

## Вопросы к owner'у до старта

1. **Docker image naming:** `auth-service` → `auth-app` или оставить
   `auth-service`? Pattern в проекте: image name = service name
   (не app-module). **Default:** оставить `auth-service` image name;
   поменять только build path внутри Dockerfile.

2. **Internal endpoints policy:** показывать в public `/v3/api-docs`
   или полностью скрыть?
   **Default:** `@Hidden` на `InternalAuthApi` interface — endpoints
   работают, но не появляются в swagger-ui и openapi spec (public
   frontend'ы о них не знают).

3. **Separate interface per internal controller?**
   `InternalIssuerController` и `InternalWsTicketController` — два
   разных internal flow. Варианты:
   - (a) Один `InternalAuthApi` interface покрывает оба
   - (b) Разделить на `InternalIssuerApi` + `InternalWsTicketApi`
   **Default:** (b) — разделить для чистоты границ.

4. **DTO records vs classes:** текущие 14 DTO — часть records,
   часть classes (с Lombok). Переводить все в records?
   **Default:** да, все request DTO → Java records; response DTO
   если используется HATEOAS `RepresentationModel` — оставляем как
   class без Lombok (см. правило в CLAUDE.md).

## Ожидаемые surprises

- **Lombok в WsTicketResponse** — может быть `@Data`/`@Builder`.
  После переноса в contract-модуль (БЕЗ Lombok) нужно руками
  написать getters/setters или перевести в record.
- **Package renaming сломает import'ы в 30+ файлах.**
  IntelliJ автоматически обновит, но IT-тесты (строковые package
  references в YAML/properties) могут требовать ручного grep'а.
- **Hibernate/Jackson ObjectMapper annotations на DTO** — если есть
  `@JsonProperty`/`@JsonIgnore`, нужно сохранить при переносе.
- **Tests с `@MockitoBean` на controller** могут использовать DTO
  classes не через contract package — потребуется fix imports.
- **`@Valid` annotations** остаются на method signatures (в controller,
  не в interface) — Spring Validation работает на runtime level.
- **OpenAPI duplicate paths** — если interface имеет @RequestMapping
  и controller тоже (accidental не удалили) — Spring выдаст
  "Ambiguous mapping" на startup. Проверить через `./gradlew bootRun`.

## Связь с другими milestones

### С M07 Frontend Hardening
- M07 Группа 3 создаёт openapi-typescript generator.
- **M12 regenerate** triggered вручную (или через pre-commit hook
  если M07 его добавит).
- **Порядок:** M07 → M12. Без M07 generator'а frontend migration
  в M12 придётся делать руками.

### С M10 Notification History
- Независим. M10 создаёт `notification-api-contract` (pattern reference),
  M12 создаёт `auth-api-contract` — параллельно.

### С M11 OpenAPI Polish
- **M11 → M12 обязательный порядок.**
- M11 определяет `@Schema` policy (description + example на DTO
  fields), `GlobalErrorResponsesCustomizer` применяет стандартные
  error responses ко всем endpoints.
- **M12 применяет M11 policy к auth-api-contract** сразу при
  создании (не потом retrofit).
- После M12 conformance CI gate (M11) расширяется на auth-service.

### С M08 Test Infrastructure
- AuthApiContractTest (ArchUnit rule) — использует ArchUnit framework
  из M02/M05/M08.
- IT тесты остаются зелёными после M12 (binary-compatible).
- Coverage gate (M08) для `auth-app` остаётся 60% (не pilot 70%).

## Binary-compatibility checklist

Критерии «binary-compatible refactor»:

- [ ] JSON payload каждого endpoint идентичен до/после (diff через
      postman collection или actual curl)
- [ ] HTTP status codes не изменились
- [ ] Error response shape (ErrorResponse RFC 9457) не изменился
- [ ] Content-Type headers не изменились (application/json для
      request, application/problem+json для errors)
- [ ] Endpoint URLs идентичны (/auth/login, /auth/otp/request, etc.)
- [ ] `@Valid` validation rules (constraints на DTO fields)
      перенесены вместе с DTO

## Deferred в v0.1+

- **Separate JWT issuer module** (если захотим extract JWT logic
  в shared-security pattern) — v0.1, не блокер.
- **OAuth2/OIDC migration** — v0.2+, другой scope.
- **Refresh-token rotation** с хранением fingerprint — v0.1+.

## Post-migration smoke checklist

Перед закрытием milestone:

- [ ] Login flow: `POST /auth/login` с валидным user → 200 + token
- [ ] Login flow invalid: `POST /auth/login` с неверным паролем → 401
      + RFC 9457 ErrorResponse
- [ ] OTP flow: `POST /auth/otp/request` → 204 (после M09)
- [ ] TMA flow: `POST /auth/tma/login` с корректным HMAC → 200 + token
- [ ] Refresh: `POST /auth/refresh` → 200 + new token
- [ ] Change-password: `PATCH /auth/password` → 204
- [ ] WS ticket: `POST /auth/ws-ticket` → 200 + ticket
- [ ] Internal issuer: `POST /internal/auth/issue` (с правильным
      internal JWT) → 200
- [ ] `/v3/api-docs` содержит public endpoints, НЕ содержит internal
- [ ] Frontend generated types регенерированы и login работает в
      PWA + web-panel

---

## 2026-04-24 — G5 Frontend regenerate + snapshot (closed)

### OpenApiSnapshotIT создан

- Auth-service не имел OpenApiSnapshotIT (в M11 G3 покрыты только
  academic/schedule/attendance/notification). Создан по pattern
  academic IT: `services/auth-service/auth-app/src/test/java/
  ru/rutcampustrack/auth/integration/OpenApiSnapshotIT.java`.
  SNAPSHOT_PATH = `docs/openapi/auth.json`, snapshot CWD = auth-app/.
- Regenerate: `./gradlew :services:auth-service:auth-app:integrationTest
  --tests "*OpenApiSnapshotIT" -Popenapi.snapshot.update=true` — BUILD
  SUCCESSFUL 1m 14s.

### docs/openapi/auth.json — новый baseline

- **Старый baseline** (до M12) — ручная конкатенация, включала
  `/internal/issue-internal-jwt` + `/internal/consume-ws-ticket` как
  public endpoints + Internal tags. `tags[0]` = Authentication, далее
  Internal + Internal Issuer.
- **Новый baseline** (runtime springdoc) — internal endpoints отсутствуют
  (`@Hidden` на InternalIssuerApi + InternalWsTicketApi). Остаются
  11 public endpoints:
  - `/auth/login`, `/auth/logout`, `/auth/refresh`, `/auth/refresh-body`
  - `/auth/otp/request`, `/auth/otp/verify`, `/auth/otp/verify-by-code`
  - `/auth/public-key`, `/auth/tma`, `/auth/change-password`
  - `/auth/ws-ticket`
- Diff: 1121 insertions, 540 deletions. **Не** binary-breaking:
  удаления = internal + старое описание без @Schema/@Operation;
  добавления = M11 @Schema descriptions + @Operation summaries
  + RFC 9457 error response references.

### Frontend regenerate (offline)

- `cd frontends/pwa && npm run generate:types:offline` → пересобраны
  все 4 `*.types.ts` из `docs/openapi/*.json`. Изменился только
  `auth.types.ts` (academic/schedule/attendance spec не изменились,
  generated types identical).
- То же самое для `frontends/web-panel`.
- **mini-app** (по PLAN.md предполагался): отдельного `generate:types`
  script нет — mini-app использует auth через PWA types (shared
  dependency). Regenerate не требуется.
- Binary-compat проверка: **deletions** в auth.types.ts — только
  `/internal/*` endpoints + inline `ConsumeRequest/ConsumeResponse`
  DTO (которые теперь Hidden внутри internal controller'а).
  **Additions** — только JSDoc descriptions/examples от M11 @Schema
  policy. **Public endpoint DTO shape не изменился.**

### Docker smoke — deviation

- OpenApiSnapshotIT уже покрывает runtime smoke: Spring context
  boot + real Testcontainers postgres/redis + HTTP GET /api-docs.
  Internal endpoints verifiably скрыты (0 occurrences of
  `"/internal` в snapshot).
- Полный `docker compose up -d auth-service` + login flow в
  PWA/web-panel — требует browser session + seeded DB;
  откладывается на отдельную UAT сессию владельца.
  **Не блокер** для M12 binary-compat gate.

---

## 2026-04-24 — Старт M12

### Owner-ответы (defaults подтверждены)

1. Docker image name: оставить `auth-service` — build path в Dockerfile
   обновится, image name не меняется.
2. Internal endpoints: `@Hidden` на `InternalIssuerApi` +
   `InternalWsTicketApi` — internal не появляется в public /v3/api-docs.
3. Раздельные interfaces: `InternalIssuerApi` + `InternalWsTicketApi`
   (не один `InternalAuthApi`) — consistency с academic-service pattern.
4. DTO: request → Java `record`, response → class без Lombok.

### Отклонения от PLAN.md

- PLAN.md упоминает `OtpCodeResponse` — **такого DTO нет в проекте**.
  Фактические 12 DTO (не 13): Login/Token/Otp×3/Refresh/Tma/ChangePwd/
  WsTicket/InternalIssue×2/PublicKey. `OtpCodeResponse` — ошибка копирования.

### Backup `services/auth-service/build.gradle.kts` (на случай rollback G1)

```kotlin
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "ru.rutcampustrack"
version = "0.1.0"

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    implementation(project(":services:shared:shared-observability"))
    implementation(project(":services:shared:shared-events"))
    implementation(project(":services:shared:shared-web"))
    implementation(project(":services:shared:shared-logback"))

    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.flywaydb:flyway-core")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation(testFixtures(project(":services:shared:shared-test-containers")))
    testImplementation(libs.json.schema.validator)
}
```

### Пути, которые должны обновиться в G1

| Файл | Было | Стало |
|---|---|---|
| `settings.gradle.kts` | `include("services:auth-service")` | +`:auth-api-contract` + `:auth-app` |
| `services/auth-service/build.gradle.kts` | single-module | удалён |
| `services/auth-service/Dockerfile` | `:services:auth-service:bootJar`, `build/libs` | `:services:auth-service:auth-app:bootJar`, `auth-app/build/libs` |
| `build.gradle.kts:254` | `services/auth-service/src/main/resources` | `.../auth-app/src/main/resources` |
| `build.gradle.kts:293` | `services/auth-service` | `services/auth-service/auth-app` |
| `.github/workflows/ci.yml:34,85` | `:services:auth-service` | `:services:auth-service:auth-app` |
| `.github/workflows/coverage.yml:52-54` | `:services:auth-service` + report path | `:services:auth-service:auth-app` + `auth-app/build/...` |
| `scripts/verify-gateway-e2e.sh:8,35` | `:services:auth-service:bootRun` | `:services:auth-service:auth-app:bootRun` |
| `docker-compose.prod.yml:140` | `services/auth-service/Dockerfile` | без изменений (Dockerfile остаётся на том же месте) |
| `.github/workflows/deploy.yml:87` | `services/auth-service/Dockerfile` | без изменений |

---
