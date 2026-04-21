# M12 Checklist

Атомарные задачи в порядке выполнения. Одна строка = 30 мин - 2 часа.

## Группа 1 — Gradle module split (~0.5д)

- [ ] Backup текущего `services/auth-service/build.gradle.kts` в
      `NOTES.md` (на случай rollback)
- [ ] Создать `services/auth-service/auth-api-contract/build.gradle.kts`:
      java-library plugin, spring-web + springdoc-openapi dependencies,
      БЕЗ Lombok
- [ ] Создать `services/auth-service/auth-app/build.gradle.kts`:
      Spring Boot app, `implementation project(":services:auth-service:auth-api-contract")`
- [ ] `settings.gradle.kts` — добавить `include` для обоих новых
      модулей, сохранить legacy auth-service include (удалить в конце)
- [ ] `services/auth-service/auth-app/src/` — git mv из старого
      `services/auth-service/src/`
- [ ] `services/auth-service/Dockerfile` — обновить путь к jar
      `auth-app/build/libs/auth-app-*.jar`
- [ ] `docker-compose.yml` — проверить build context auth-service
- [ ] `./gradlew :auth-api-contract:build` зелёный (пустой модуль)
- [ ] `./gradlew :auth-app:build` зелёный (полный src, старое
      поведение)
- [ ] Удалить старый `services/auth-service/build.gradle.kts` и
      соответствующий include
- [ ] Коммит `refactor(auth): split into auth-api-contract + auth-app Gradle modules (M12 Группа 1, 01 P0-1)`

## Группа 2 — DTO migration (~0.5д)

- [ ] `auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/`
      — создать package
- [ ] git mv `LoginRequest.java` в contract-module
- [ ] git mv `TokenResponse.java`
- [ ] git mv `OtpRequest.java`, `OtpVerifyRequest.java`,
      `OtpVerifyByCodeRequest.java`, `OtpCodeResponse.java`
- [ ] git mv `RefreshRequest.java`
- [ ] git mv `TmaAuthRequest.java`
- [ ] git mv `ChangePasswordRequest.java`
- [ ] git mv `WsTicketResponse.java`
- [ ] git mv `InternalIssueRequest.java`, `InternalIssueResponse.java`,
      `PublicKeyResponse.java`
- [ ] **НЕ переносить:** `ErrorResponse.java` — используется
      shared-web из M01
- [ ] Проверить что каждый DTO — Java `record` (конвертировать
      classes где возможно)
- [ ] Убрать Lombok annotations если есть (на всякий случай grep
      `@Data`, `@Builder` в contract-module)
- [ ] Fix imports в auth-app (все references к DTO — через новый
      package)
- [ ] `./gradlew :auth-app:compileJava` зелёный
- [ ] Коммит `refactor(auth): move DTO to auth-api-contract module (M12 Группа 2)`

## Группа 3 — AuthApi interface (~0.5д)

- [ ] `auth-api-contract/.../api/AuthApi.java` — public endpoints
      interface с `@RequestMapping("/auth")`
- [ ] Endpoints в AuthApi: login, logout, otp/request,
      otp/verify-by-code, refresh, change-password, tma/login
- [ ] Каждый endpoint: `@Operation(summary, description)`,
      `@ApiResponse(responseCode, description)`, `@RequestBody`
      description
- [ ] `@Schema(description, example)` на request DTO fields
      (pass-1 для public endpoints)
- [ ] `auth-api-contract/.../api/WsTicketApi.java` — ws-ticket
      endpoints
- [ ] `auth-api-contract/.../api/InternalAuthApi.java` — internal
      issuer + ws-ticket endpoints
- [ ] `InternalAuthApi` помечен `@Hidden` (springdoc скрывает из
      public swagger-ui)
- [ ] `./gradlew :auth-api-contract:build` зелёный
- [ ] Коммит `feat(auth): add AuthApi + WsTicketApi + InternalAuthApi interfaces (M12 Группа 3, P2-2/2)`

## Группа 4 — Controller refactor (~0.5д)

- [ ] `AuthController implements AuthApi` — добавить implements,
      убрать `@RequestMapping` с class level
- [ ] Убрать `@GetMapping`/`@PostMapping`/`@PutMapping` со всех
      методов AuthController (наследуются из interface)
- [ ] `WsTicketController implements WsTicketApi` — аналогично
- [ ] `InternalIssuerController implements InternalAuthApi`
- [ ] `InternalWsTicketController implements InternalAuthApi`
      (или отдельный InternalWsTicketApi если scope разный)
- [ ] Grep проверка: `@RequestMapping` / `@GetMapping` /
      `@PostMapping` отсутствуют в `auth-app/src/main/java/.../controller/`
- [ ] `AuthApiContractTest` — ArchUnit rule: все классы в
      `.controller` package должны `implement` что-то из
      `auth-api-contract...api` package
- [ ] `./gradlew :auth-app:test` зелёный (existing IT/unit без
      изменений)
- [ ] `./gradlew :auth-app:integrationTest` зелёный
- [ ] Коммит `refactor(auth): controllers implement contract interfaces (M12 Группа 4, 01 P0-1)`

## Группа 5 — Frontend regenerate + smoke (~0.5д)

- [ ] `docker compose up -d auth-service` — smoke что /v3/api-docs
      доступен
- [ ] `curl http://localhost:9090/v3/api-docs > /tmp/auth-spec.json`
      — dump for inspection
- [ ] Убедиться что spec содержит все public endpoints (login, otp,
      refresh, tma, change-password, ws-ticket)
- [ ] Internal endpoints НЕ в public spec (`@Hidden` работает)
- [ ] PWA `npm run generate:types` — regenerate auth types
- [ ] web-panel `ng run generate:types` (или аналогичный script)
- [ ] mini-app `npm run generate:types`
- [ ] Diff generated types — поля идентичны до/после (binary-compatible)
- [ ] Smoke: `docker compose up`, login в PWA → token получен
- [ ] Smoke: login в web-panel → admin dashboard загружается
- [ ] Smoke: logout clears cookie + invalidates refresh token
- [ ] Коммит `chore(frontend): regenerate auth types after M12 contract split`

## Группа 6 — Docs + cleanup (~0.25д)

- [ ] `CLAUDE.md` — раздел «Contract-first → Исключения»:
      - Убрать строку "auth-service — единственный нарушитель
        правила (01 P0-1), auth-api-contract отложен в v0.1 backlog
        (M09 D1, docs/future-ideas.md)"
      - Оставить только api-gateway исключение
- [ ] `docs/architecture.md` — auth-service раздел обновить:
      mention auth-api-contract + auth-app структуру; таблица
      сервисов показывает contract-first compliance
- [ ] `docs/future-ideas.md` — удалить раздел «Auth API contract-first
      refactor (v0.1)» (перенесено в M12)
- [ ] `docs/future-ideas.md` — удалить ошибочный раздел «P2-2/4
      @Schema на всех DTO + P2-2/3 swagger-request-validator CI
      (v0.1)» (эти пункты закрыты M11)
- [ ] `docs/milestones/README.md` — M12 статус ✅ + дата
- [ ] `CLAUDE.md` v0.0.0 Milestones table — M12 строка + статус ✅
- [ ] `CHANGELOG.md [Unreleased]` — M12 entry
- [ ] Коммит `docs(m12): Contract-first exception removed + future-ideas cleanup`

## Группа 7 — Audit + финализация (~0.25д)

- [ ] `./gradlew build` полный — всё зелёное
- [ ] `./gradlew integrationTest` — всё зелёное
- [ ] `code-reviewer` агент на M12 diff — фокус: binary-compatibility
      (DTO shape не изменился), правильные interfaces, OpenAPI
      полнота
- [ ] Hot-patches → отдельный коммит
- [ ] `PLAN.md` → Post-mortem секция (что сюрпризнуло, что
      отклонилось)
- [ ] Тег `v0.0.0-alpha.12` или `v0.0.0-rc.2` (в зависимости от
      порядка с M08-M11)
- [ ] Hand-off для release-candidate в `NEXT-SESSION.md`
- [ ] Коммит `docs(m12): закрытие milestone — post-mortem + CHANGELOG + hand-off`

---

_Если задача превращается в 6+ часов работы — разрежь её._
