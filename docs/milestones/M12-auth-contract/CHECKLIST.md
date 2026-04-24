# M12 Checklist

Атомарные задачи в порядке выполнения. Одна строка = 30 мин - 2 часа.

## Группа 1 — Gradle module split (~0.5д) ✅ 2026-04-24 (commit 9925376)

- [x] Backup текущего `services/auth-service/build.gradle.kts` в
      `NOTES.md` (на случай rollback)
- [x] Создать `services/auth-service/auth-api-contract/build.gradle.kts`:
      java-library plugin, spring-web + springdoc-openapi dependencies,
      БЕЗ Lombok
- [x] Создать `services/auth-service/auth-app/build.gradle.kts`:
      Spring Boot app, `implementation project(":services:auth-service:auth-api-contract")`
- [x] `settings.gradle.kts` — 2 новых include, legacy удалён одной операцией
- [x] `services/auth-service/auth-app/src/` — git mv (79 файлов, 100% rename)
- [x] `services/auth-service/Dockerfile` — обновить путь к jar
      `auth-app/build/libs/*.jar` + `:services:auth-service:auth-app:bootJar`
- [x] `docker-compose.yml` — проверить build context auth-service
      (без изменений — Dockerfile остаётся на прежнем месте)
- [x] `./gradlew :auth-api-contract:build` зелёный (NO-SOURCE stub)
- [x] `./gradlew :auth-app:test` зелёный (24 unit-теста)
- [x] Удалить старый `services/auth-service/build.gradle.kts`
- [x] CI/coverage.yml/scripts пути обновлены
- [x] EventSchemaValidator.java — +1 уровень вверх для schemasDir()
- [x] Коммит `refactor(auth): split into auth-api-contract + auth-app Gradle modules (M12 Группа 1, 01 P0-1)`

## Группа 2 — DTO migration (~0.5д) ✅ 2026-04-24 (commit 315a317)

- [x] `auth-api-contract/.../auth/dto/` — создан через git mv
- [x] git mv 12 DTO (Login/Token/Otp×3/Refresh/Tma/ChangePwd/WsTicket/
      InternalIssue×2/PublicKey) — 100% rename
- [x] `ErrorResponse` — не переносим, shared-web-api из M01
- [x] Все 12 DTO — уже Java `record` без Lombok, конвертация не нужна
- [x] Package `ru.rutcampustrack.auth.dto` не изменился — imports работают через contract dependency
- [x] `./gradlew :auth-app:compileJava` + `:test` зелёные (24 unit)
- [x] Коммит `refactor(auth): move DTO to auth-api-contract module (M12 Группа 2)`
- **Отклонение:** PLAN.md упоминает OtpCodeResponse — такого DTO нет (12, не 13).

## Группа 3 — AuthApi interface (~0.5д) ✅ 2026-04-24 (commit e3a4adf)

- [x] `AuthApi.java` — 10 public endpoints (`@RequestMapping("/auth")`):
      login, refresh, logout, public-key, otp/request, otp/verify,
      otp/verify-by-code, tma, refresh-body, change-password.
      Константа REFRESH_COOKIE_NAME = "rct_refresh" (copy from AuthCookies).
- [x] Каждый endpoint: `@Operation` + `@ApiResponse` (2-3 на endpoint)
- [x] `WsTicketApi.java` — POST /auth/ws-ticket (bearer JWT guarded)
- [x] `InternalIssuerApi.java` — POST /internal/issue-internal-jwt (`@Hidden`)
- [x] `InternalWsTicketApi.java` — POST /internal/consume-ws-ticket (`@Hidden`)
- [x] Раздельные Internal API (owner-default #3, не один `InternalAuthApi`)
- [x] Extracted 2 DTO (nested → standalone): ConsumeWsTicketRequest,
      ConsumeWsTicketResponse (binary-compat snake_case JsonProperty сохранён)
- [x] auth-api-contract build.gradle.kts + spring-security-core + jakarta.servlet-api
      (controllers передают Authentication + HttpServletRequest в signature interface)
- [x] `./gradlew :auth-api-contract:build` + `:auth-app:compileJava` зелёные
- [x] Коммит `feat(auth): add AuthApi + WsTicketApi + InternalIssuerApi + InternalWsTicketApi interfaces (M12 Группа 3, P2-2/2)`
- **@Schema на DTO fields** — отложено (pass-2 в G5/G7 через OpenApiSnapshotIT)

## Группа 4 — Controller refactor (~0.5д) ✅ 2026-04-24 (commit bf2de65)

- [x] AuthController implements AuthApi
- [x] Убраны `@RequestMapping` + `@Tag` с class-level
- [x] Убраны `@GetMapping`/`@PostMapping` + `@Operation`/`@ApiResponse` со всех методов
- [x] `@Valid`, `@RequestBody`, `@CookieValue` — тоже убраны с controller (наследуются от interface)
- [x] WsTicketController implements WsTicketApi
- [x] InternalIssuerController implements InternalIssuerApi
- [x] InternalWsTicketController implements InternalWsTicketApi
      (nested records ConsumeRequest/ConsumeResponse удалены — заменены на DTO из contract)
- [x] WsTicketIT import fix: `ConsumeRequest`/`ConsumeResponse` → `ConsumeWsTicketRequest`/`Response`
- [x] AuthApiContractTest (ArchUnit, 3 правила):
      - `@RequestMapping` отсутствует на class-level в `..controller..`
      - `@GetMapping`/`@PostMapping`/`@Put`/`@Patch`/`@Delete`/`@RequestMapping` на method-level
        отсутствуют в `..controller..`
      - `@RestController` implement'ит один из 4 Api interfaces
- [x] archunit.junit5 добавлен в auth-app testImplementation
- [x] `./gradlew :services:auth-service:auth-app:check` зелёный (2m 31s):
      unit (24+3) + integration (Testcontainers) + JaCoCo 60% gate
- [x] Коммит `refactor(auth): controllers implement contract interfaces (M12 Группа 4, 01 P0-1)`

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
