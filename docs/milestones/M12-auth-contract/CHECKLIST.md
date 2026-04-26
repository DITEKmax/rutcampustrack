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

## Группа 5 — Frontend regenerate + smoke (~0.5д) ✅ 2026-04-24

- [x] OpenApiSnapshotIT создан по pattern academic M11 G3
      (`services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OpenApiSnapshotIT.java`)
- [x] Regenerate `docs/openapi/auth.json` через
      `./gradlew :services:auth-service:auth-app:integrationTest --tests "*OpenApiSnapshotIT" -Popenapi.snapshot.update=true`
      (BUILD SUCCESSFUL 1m 14s)
- [x] Spec содержит 11 public endpoints (login/logout/refresh/refresh-body/
      otp×3/public-key/tma/change-password/ws-ticket)
- [x] Internal endpoints НЕ в public spec (`@Hidden` verified — 0 occurrences
      of `/internal` в snapshot)
- [x] PWA `npm run generate:types:offline` — regenerate 4 types (изменился
      только `auth.types.ts`)
- [x] web-panel `npm run generate:types:offline` — idem
- [x] mini-app — **отдельного generate:types скрипта нет** (PLAN.md
      ошибочное упоминание); mini-app читает PWA's generated types через
      shared dependency
- [x] Diff generated types — public DTO shape идентичен; удаления только
      internal endpoints + inline DTO; добавления только JSDoc descriptions
      от M11 @Schema
- [~] Runtime smoke login/logout в браузере — **deviation**: покрыто
      Spring context boot + real Testcontainers postgres/redis в
      OpenApiSnapshotIT; browser UAT отложена для владельца (не блокер
      binary-compat gate). См. NOTES «Docker smoke — deviation»
- [ ] Коммит `chore(frontend): regenerate auth types after M12 contract split` (следующий шаг)

## Группа 6 — Docs + cleanup (~0.25д) ✅ 2026-04-24

- [x] `CLAUDE.md` — раздел «Contract-first → Исключения»: блок
      «auth-service — временный нарушитель» удалён; оставлено только
      `api-gateway` исключение (единственное)
- [x] `CLAUDE.md` — структура репозитория: auth-service теперь
      показывает `auth-api-contract/` + `auth-app/`
- [x] `CLAUDE.md` — v0.0.0 Milestones table: M11 ✅ 2026-04-24, M12
      ✅ 2026-04-24 (раньше M10 был отмечен, M11/M12 — новые)
- [x] `docs/architecture/architecture.md` — `### 3.2 Auth Service` дополнен строкой
      про Gradle-модули (M12) — auth-api-contract + auth-app split
- [x] `docs/future-ideas.md` — удалены разделы «Auth API contract-first
      refactor (v0.1)» + «Auth-service OpenAPI (P2-2/2, v0.1)»
      (перенесено и закрыто в M12). Раздел «P2-2/3+/4 @Schema
      + swagger-request-validator» уже отсутствует (удалён M11 G5).
- [x] `docs/milestones/README.md` — M10/M11/M12 ✅ 2026-04-24
- [x] `CHANGELOG.md [Unreleased]` — добавлены M12 + M11 + M10 entries
      (раньше был только M09)
- [ ] Коммит `docs(m12): Contract-first exception removed + future-ideas cleanup (следующий шаг)`

## Группа 7 — Audit + финализация (~0.25д) ✅ 2026-04-24

- [x] `./gradlew build` + `./gradlew integrationTest` зелёные (после
      hot-patch ad40c53 — pre-existing M11 G5 missed regenerate)
- [x] `code-reviewer` агент на `a902c16..HEAD`:
      0 BLOCK, 0 HIGH, 7 MEDIUM/NOTES (3 pre-existing, 4 positive
      observations), binary-compat confidence 9/10
- [x] Hot-patch `ad40c53` — academic snapshot regenerate (M11 G5
      missed regenerate, UserCreatedResponse.initialPassword
      @Schema description+example). Binary-compat only.
- [x] `PLAN.md` → Post-mortem секция (коммиты, surprises, deviations,
      deferred, code review verdict)
- [x] Тег `v0.0.0-alpha.13` локально
- [x] `NEXT-SESSION.md` переписан на консолидацию v0.0.0 → GA
- [ ] Коммит `docs(m12): закрытие milestone — post-mortem + hand-off` (следующий шаг)

---

_Если задача превращается в 6+ часов работы — разрежь её._
