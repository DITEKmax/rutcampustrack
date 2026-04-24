# Промпт для следующей сессии — M11 финиш (G3 + G4 + G5)

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам откроет
нужные файлы и продолжит.

---

**M11 Группа 0 (Shared Web Starter Refactor) + G1 + G2 ✅ закрыты
2026-04-24. 46 коммитов ahead origin/dev. Push всё ещё отложен до явного `go`.**

**Старт новой сессии — дословно:**

> Читаю `docs/milestones/NEXT-SESSION.md` →
> `docs/milestones/M11-openapi-polish/{PLAN,CHECKLIST,NOTES}.md` →
> `CLAUDE.md`. Продолжаю **M11 OpenAPI Polish** — остались G3, G4, G5.
>
> **G3 OpenAPI ↔ runtime conformance (P2-2/3):**
> 1. Audit `@ApiResponse(responseCode)` vs actual controller returns:
>    - `ResponseEntity<Void>` для DELETE/PUT/POST mutations → 204
>      (сейчас ошибочно 200 у некоторых)
>    - POST creates → 201 + Location header
>    - Поиск: `grep "ResponseEntity<Void>"` + ручная проверка методов
> 2. `./gradlew openapi-dump` gradle task per service (springdoc
>    maven plugin аналог или через MockMvc `/api-docs` dump в `build/openapi/*.yaml`).
>    В Spring Boot 3 + springdoc 2.8.6 работает через `ForkedRuntime`.
>    Альтернатива проще: integration test с MockMvc получает
>    `/api-docs` → сохраняет в `docs/api-spec/{service}.yaml`.
> 3. Commit initial snapshots `docs/api-spec/{academic,schedule,attendance,notification}.yaml`.
> 4. CI step `openapi-conformance` в `.github/workflows/ci.yml`:
>    bootRun → wait health → curl `/api-docs` → oasdiff vs committed
>    → fail on breaking changes.
>    Default tool — `oasdiff` (Tufin, Go docker image).
> 5. NEW-123 → `docs/openapi-conformance.md` runbook.
>
> **G4 /swagger-ui basic-auth в prod (P2-2/6):**
> 1. `infra/nginx/nginx.conf` (prod вариант) — location block:
>    ```
>    location ~ ^/(swagger-ui|v3/api-docs|api-docs) {
>        auth_basic "Swagger UI";
>        auth_basic_user_file /etc/nginx/htpasswd/swagger;
>        proxy_pass http://api-gateway:8080;
>    }
>    ```
>    **ВАЖНО:** academic-service использует `springdoc.api-docs.path: /api-docs`
>    (не дефолт `/v3/api-docs`) — проверь все 4 сервиса, унифицируй regex.
> 2. `infra/nginx/htpasswd/swagger.template` (пустой, mounted volume).
> 3. `.env.prod.example` — `SWAGGER_HTPASSWD=<htpasswd -nB swagger>`.
> 4. `docs/runbooks/swagger-prod-access.md` (NEW-125): как получить доступ
>    (оператор выполняет `htpasswd -nB swagger` локально, вставляет в
>    `.env.prod`, `docker compose up -d nginx` — secret rotation раз в
>    полгода, привязать к `docs/runbooks/secret-rotation.md` из M09).
> 5. Spring profile `local`: bypass через отдельный nginx compose-override
>    (или просто `-dev` variant nginx.conf без auth_basic block).
> 6. Smoke: `curl https://ruttrack.site/swagger-ui/` без creds → 401;
>    с `-u swagger:pass` → 200.
>
> **G5 Финализация M11:**
> 1. `./gradlew build` + `./gradlew integrationTest` зелёные.
> 2. Audit покрытия DTO через grep: ожидается ≥80% полей с @Schema —
>    Acceptance уже достигнут в G2 (89% файлов). Для оставшихся ~8
>    Response classes (UserResponse, UserCreatedResponse, AssignmentResponse
>    и т.п. с только class-level @Schema, без per-getter) — проверить,
>    что springdoc auto-infers schema из getter'ов + types. Если да —
>    закрыть как acceptable. Если нет — добавить per-getter в
>    critical DTO.
> 3. Post-mortem раздел в `docs/milestones/M11-openapi-polish/PLAN.md`
>    (что пошло по плану, что нет, lessons learned — формат как в
>    M10 PLAN.md post-mortem).
> 4. `git tag v0.0.0-alpha.12 -m "M11 OpenAPI Polish закрыт"` (локальный).
> 5. Обновить `NEXT-SESSION.md` на M12 Auth Contract-first Refactor.

Stop при сюрпризе → NOTES + спросить.

---

## M11 G0-G2 summary (что сделано — 46 коммитов)

### Группа 0 — Shared Web Starter Refactor (9 коммитов)

| Группа | Commit | Scope |
|--------|--------|-------|
| G0.1 | `8cf3817` | shared-web-api модуль: унифицированный ErrorResponse (10 полей RFC 9457) + FieldError + InvalidParam + 10 unit-тестов |
| G0.2 | `5d4e26b` | shared-web → Spring Boot starter с @AutoConfiguration (AutoConfiguration.imports заменил scanBasePackages hack) |
| G0.3 | `86f3ecf` | 3 дубля ErrorResponse в academic/schedule/attendance-api-contract удалены |
| G0.4 | `0962d47` | academic GlobalExceptionHandler: -7 Spring MVC handler'ов, только domain + @Order(HIGHEST_PRECEDENCE) |
| G0.5 | `4da3487` | schedule handler: аналогично, SecuritySmokeIT passes |
| G0.6 | `221b1cb` | attendance handler: аналогично, Geofence/RateLimit/DuplicateKey сохранены |
| G0.7 | `c3e45a6` | auth handler + auth/dto/ErrorResponse удалён (6-field about:blank → shared 10-field RFC 9457) |
| G0.8 | `69cbdd4` | notification: убран scanBasePackages hack |
| G0.9 | `bab10d6` | spring-security-core compileOnly → api (fix NoClassDefFoundError), NotificationErrorHandlingIT invalidParams → fieldErrors, docs (CLAUDE.md, api-error-conventions.md, shared-modules-usage.md) |

### G1 — SharedOpenApiCustomizer наполнение (2 коммита)

| Группа | Commit | Scope |
|--------|--------|-------|
| G1 | `8233f88` | GlobalErrorResponsesCustomizer: 7 стандартных error responses (400/401/403/404/409/429/500) + 8 unit + 3 IT в academic-app |
| G1 docs | `66b9a48` | docs/api-error-conventions.md NEW-122 раздел «Global error responses» |

### G2 — @Schema на DTO (2 коммита)

| Группа | Commit | Scope |
|--------|--------|-------|
| G2 partial | `b2404b8` | academic/user DTO — 6 файлов (шаблон для остальных) |
| G2 full | `de1f1dd` | +155 @Schema в 60 файлах через 3 агентов параллельно (academic/schedule/attendance+notification) |

### Ключевые архитектурные решения (ссылка на NOTES.md)

1. **Один ErrorResponse во всём backend** (было 5 дублей)
2. **Spring Boot 3 idiomatic AutoConfiguration** — без scanBasePackages hack
3. **RFC 9457** во всех сервисах (было: RFC 7807 в 4 + about:blank в auth)
4. **MDC traceId** во всех response body (раньше null в 4 сервисах)
5. **invalidParams → fieldErrors** (унификация с frontend)
6. **`@Order(HIGHEST_PRECEDENCE)` domain + `LOWEST_PRECEDENCE` shared** — правильный Spring pattern
7. **spring-security-core `api(...)`** в shared-web — обязательно для AccessDenied handler resolution

## M11 Lessons learned (применяй в G3-G5 и M12)

1. **Spring @ExceptionHandler resolve'ит класс при bean creation.**
   `@ExceptionHandler(XxxException.class)` + `compileOnly` → NoClassDefFoundError
   в runtime для сервисов без классa. Правило: любой класс в @ExceptionHandler
   должен быть `api(...)` в shared module, не `compileOnly`.
2. **Breaking changes в shared DTO требуют frontend regen.**
   invalidParams → fieldErrors сломало только NotificationErrorHandlingIT
   (frontend уже использовал fieldErrors). Всегда проверять IT тесты
   которые парсят response body.
3. **`scanBasePackages` = code smell в Spring Boot 3.** Правильно —
   `META-INF/spring/AutoConfiguration.imports`. Unblock pattern для
   следующих shared-* модулей.
4. **Agent delegation для механической работы окупается.** 155
   @Schema в 60 файлах × 3 agents параллельно = ~10 min общего времени
   vs 2+ часа в main context.
5. **Response classes extends RepresentationModel — springdoc
   auto-infers из getter'ов + validation.** Class-level @Schema
   достаточно для baseline — per-getter optional.

## Правила (без изменений)

- **Русский язык** в отчётах / NOTES / ответах.
- **Ветка `dev`**. Push на `main`/`origin` — только с явного `go`.
- Не звать `gsd-*` агентов. `Explore` для «найти все X»,
  `bug-hunter` + `security-auditor` — только в G-final.
- Surprise → NOTES.md + спросить до продолжения.
- Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — **ложные**, edit применяется.
- Атомарные коммиты per группа.

## G3 точки внимания

- **springdoc.api-docs.path** — у academic `/api-docs`, у остальных
  может быть `/v3/api-docs` (дефолт). Проверить все `application.yml`:
  `grep -rn "api-docs" services/*/src/main/resources/`.
- **openapi-diff vs oasdiff**: оба в docker. oasdiff быстрее и
  configurable exclusions (через JSON rules). openapi-diff
  (Azure) — более строгий default.
- **CI baseline** — сначала **non-blocking** (warning), потом
  blocking после 2-3 недель стабилизации спеки.
- **M10 notification-api-contract уже имеет @Schema** из Группы 0.2 —
  в G3 conformance snapshot будет включать notification.

## G4 точки внимания

- **nginx на prod** — только **один** инстанс (`infra/nginx/nginx.conf`).
  Не все сервисы deployed behind nginx: academic/schedule/attendance
  выставляются через api-gateway proxy. Swagger-UI на api-gateway
  или на каждом сервисе? Проверь VPS /opt/rutcampustrack compose.
- **Password rotation** — связать с `docs/runbooks/secret-rotation.md`
  (M09), добавить SWAGGER_HTPASSWD в ротацию раз в полгода.
- **Dev compose** — не должен требовать basic-auth. Используй
  отдельный `nginx.dev.conf` mount (или Spring profile differentiation,
  что сложнее — не делай).

## G5 потенциальные сюрпризы

- **Void mutations audit в G3** может найти 5-10 ошибочных `@ApiResponse("200")`
  на `ResponseEntity<Void>`. Fix — 1 строка в контракте, но 5-10
  файлов.
- **POST creates без Location** — springdoc сам не добавит Location
  header в response; требует `@ApiResponse(headers = ...)`. Только
  где строго по HTTP семантике — audit endpoint'ов, не все POST
  творят resource (некоторые — operation triggers типа `/users/{id}/archive`).

---

## История предыдущих milestone (архив)

M01-M08 ✅ (см. предыдущие версии NEXT-SESSION.md)
M09 Prod Release Blockers ✅ 2026-04-24 (tag `v0.0.0-alpha.10` локальный)
M10 Notification History ✅ 2026-04-24 (tag `v0.0.0-alpha.11` локальный)
**M11 OpenAPI Polish — G0 + G1 + G2 ✅ 2026-04-24, G3+G4+G5 в эту сессию**
M12 Auth Contract-first Refactor ⬜ (планирование в v0.0.0, реализация в v0.1)

Dependency graph и полный roadmap — `docs/milestones/README.md`.

## Ожидающие явного `go`

1. `git push origin dev` — **46 коммитов** ahead (станет 50+ после M11 G3-G5).
2. `git push origin --tags` — **10 tags**, станет 11 после M11 tag
   `v0.0.0-alpha.12`.
3. После M11 → **M12 Auth Contract-first Refactor** (планирование в
   v0.0.0, реализация в v0.1). См.
   `docs/future-ideas.md` → «Auth API contract-first refactor».

## Рекомендация perf для следующей сессии

**Full gradlew.bat build** в проекте занимает **~12 минут** (academic
+ schedule + attendance integrationTest — по ~2-3 мин каждый,
Testcontainers). Во избежание ожидания — использовать:

- `./gradlew.bat compileJava compileTestJava` — быстрая проверка
  синтаксиса после refactor'а (30 сек)
- `./gradlew.bat :services:shared:shared-web:test` — только unit
  (20 сек)
- `./gradlew.bat :services:academic-service:academic-app:test
  --tests "ClassName"` — конкретный IT (~1-2 мин)
- Полный `build` — один раз в конце группы, в background с `&`
