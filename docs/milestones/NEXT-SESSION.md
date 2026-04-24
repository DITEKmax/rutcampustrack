# Промпт для следующей сессии — M12 Auth Contract-first Refactor

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам
откроет нужные файлы и продолжит.

---

**M11 OpenAPI Polish ✅ закрыт 2026-04-24. 55 коммитов ahead origin/dev.
Push всё ещё отложен до явного `go`. Локальный tag `v0.0.0-alpha.12`
создан.**

**Старт новой сессии — дословно:**

> Читаю `docs/milestones/NEXT-SESSION.md` →
> `docs/milestones/M12-auth-contract/{PLAN,CHECKLIST,NOTES}.md` →
> `CLAUDE.md`. Начинаю **M12 Auth Contract-first Refactor** —
> единственное оставшееся structural исключение из Contract-first
> правила (`api-gateway` — permanent exception, proxy-only).
>
> **ВАЖНО:** M12 планирование в v0.0.0 scope, но фактическая
> реализация — **отложена в v0.1** (см. `docs/future-ideas.md` →
> «Auth API contract-first refactor»). В этой сессии:
> 1. Убедиться, что PLAN/CHECKLIST/NOTES для M12 актуальны
>    (owner-ответы собраны, dependencies выявлены).
> 2. Если owner даст go на реализацию раньше v0.1 — запускаем
>    Группы по порядку (Module split → DTO migration → Controller
>    refactor → Tests → Docs → Finalize).
> 3. Иначе — marking M12 as «planned in v0.0.0, scheduled v0.1»,
>    обновить roadmap статус.
>
> Если owner хочет сразу **implementation**, стартовые группы:
>
> **G1 Module split:**
> 1. `services/auth-service/auth-api-contract/` (java-library, без
>    Lombok) — build.gradle.kts по pattern academic-api-contract.
> 2. `services/auth-service/auth-app/` — переименование текущего
>    single-module, всё текущее `services/auth-service/src/` →
>    `services/auth-service/auth-app/src/`.
> 3. `settings.gradle.kts` — register новые submodules.
> 4. Docker image name = `auth-service` (минимизируем breaking
>    на infra side).
>
> **G2 DTO migration:**
> 1. Перенести Request DTO (records) в `auth-api-contract/.../dto/`.
> 2. Перенести Response classes (HATEOAS EntityModel) в contract.
> 3. `@Schema(description, example)` per DTO (M11 policy).
>
> **G3 Controller refactor:**
> 1. Создать `AuthApi` interface в `auth-api-contract/.../api/`.
> 2. `@RequestMapping` + `@PostMapping/@GetMapping` — только в
>    interface (см. CLAUDE.md правило).
> 3. `AuthController implements AuthApi` — убрать все mappings из
>    класса, оставить `@RestController` и `@Override`.
> 4. Аналогично для `WsTicketController`, `InternalWsTicketController`,
>    `InternalIssuerController`.
>
> **G4 Tests + Docs:**
> 1. OpenApiSnapshotIT для auth (pattern из M11 G3).
> 2. Regenerate `docs/openapi/auth.json` через IT с
>    `-Popenapi.snapshot.update=true`.
> 3. Regenerate PWA + web-panel TS types.
> 4. Обновить `CLAUDE.md` — убрать «auth-service временный
>    нарушитель» из Contract-first secций, заменить на
>    «api-gateway — единственное постоянное исключение».
>
> **G5 Финализация:**
> 1. `./gradlew build` + `./gradlew integrationTest` зелёные
>    (кроме pre-existing RateLimitIT flaky — документирован в M11).
> 2. Post-mortem в `PLAN.md`.
> 3. Tag `v0.0.0-alpha.13` (локальный).
> 4. Обновить NEXT-SESSION.md на следующий milestone или
>    consolidation.

Stop при сюрпризе → NOTES + спросить.

---

## M11 G0-G5 summary (что сделано — 55 коммитов)

### Группа 0 — Shared Web Starter Refactor (9 коммитов)
Унификация ErrorResponse: 5 дублей → 1 shared-web-api.
`@Order(HIGHEST_PRECEDENCE)` domain + `LOWEST_PRECEDENCE` shared.
AutoConfiguration.imports заменил scanBasePackages hack.

### Группа 1 — GlobalErrorResponsesCustomizer (2 коммита)
7 стандартных error responses (400/401/403/404/409/429/500)
автоматически на все endpoints. Application/problem+json.

### Группа 2 — @Schema на DTO (2 коммита)
155 аннотаций в 60 файлах через 3 agents параллельно.
**100%** DTO coverage (71/71 после G5.2 UserCreatedResponse fix).

### Группа 3 — Conformance CI (5 коммитов)
- Audit: 2 mismatch (HomeworkController.markComplete,
  AuthController.changePassword — оба 200→204 fixed).
- 4 `OpenApiSnapshotIT` per service (MockMvc + TestRestTemplate).
- Update-on-flag через `-Popenapi.snapshot.update=true` →
  `systemProperty` passthrough в root `build.gradle.kts`.
- `docs/openapi/*.json` единый baseline (M07 frontend + M11 runtime).
- Fix в `GlobalErrorResponsesCustomizer`:
  `resolved.referencedSchemas` регистрируется (FieldError schema).
- `docs/openapi-conformance.md` (NEW-123).

### Группа 4 — Swagger UI basic-auth (1 коммит)
- nginx уже имел `auth_basic` в `default.conf` — но `.htpasswd`
  file не был mount'ирован (pre-existing 500 error bug).
- Stateless env-to-file: `docker-compose.prod.yml:nginx.command`
  записывает `$SWAGGER_HTPASSWD` в `/etc/nginx/.htpasswd`.
- `.env.prod.example` (NEW) — template с `$$`-escape для apr1 hash.
- `docs/runbooks/swagger-prod-access.md` (NEW-125) — rotation 6 мес,
  failure mode, связь с secret-rotation.md.

### Группа 5 — Финализация (в процессе, эта сессия)
- @Schema coverage audit: 100% (71/71 DTO).
- Full IT run (academic/schedule/attendance/notification/auth зелёные,
  api-gateway RateLimitIT — pre-existing flaky, не M11 regression).
- Post-mortem в PLAN.md.
- Tag `v0.0.0-alpha.12` (локальный).

---

## Правила (без изменений)

- **Русский язык** в отчётах / NOTES / ответах.
- **Ветка `dev`**. Push на `main`/`origin` — только с явного `go`.
- Не звать `gsd-*` агентов. `Explore` для «найти все X»,
  `bug-hunter` + `security-auditor` — только в G-final.
- Surprise → NOTES.md + спросить до продолжения.
- Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — **ложные**,
  edit применяется.
- Атомарные коммиты per группа.

## M12 точки внимания

- **Binary-compatible**: DTO shape не должен меняться — frontend
  openapi-typescript генерирует идентичные типы после regen.
- **Без Lombok в auth-api-contract** (contract-first правило).
  Auth-app может оставить Lombok в entity.
- **Docker image name остаётся `auth-service`** — не переименовываем
  на `auth-app`, иначе нужны изменения в:
  - `docker-compose.prod.yml` (service name)
  - `deploy.yml` CI workflow (image tag)
  - `api-gateway` routes (`uri: http://auth-service:9090`)
  - nginx (если где-то ref'ается)
  Проще: Gradle subproject `auth-app` → Jar `auth-service.jar` →
  docker image name `ghcr.io/.../auth-service`.
- **Existing auth-app tests** в `services/auth-service/src/test/` —
  не переезжают, остаются в `auth-app/src/test/`.
- **DTO в `auth-api-contract`** получают @Schema как в M11 G2.
  Это задокументирует OTP/login/refresh flow для OpenAPI.
- **`AuthApi` interface split** — возможно 3-4 отдельных interfaces
  (public AuthApi, WsTicketApi, InternalIssuerApi, InternalWsTicketApi),
  как в academic-service pattern.
- **OpenApiSnapshotIT для auth** — скопировать из academic, +
  regen snapshot `docs/openapi/auth.json` (уже есть, обновить
  после M12 @Schema добавлений).

## Potential обновлений в других файлах после M12

- `CLAUDE.md` раздел «Contract-first»:
  - Убрать «auth-service — временный нарушитель»
  - Оставить «api-gateway — единственное постоянное исключение»
  - Обновить пример структуры репозитория (добавить auth-api-contract
    + auth-app)
- `.github/workflows/ci.yml` matrix — заменить `path: ':services:auth-service'`
  на `path: ':services:auth-service:auth-app'` (аналогично academic).

---

## История предыдущих milestone (архив)

M01-M08 ✅ (см. предыдущие версии NEXT-SESSION.md)
M09 Prod Release Blockers ✅ 2026-04-24 (tag `v0.0.0-alpha.10` локальный)
M10 Notification History ✅ 2026-04-24 (tag `v0.0.0-alpha.11` локальный)
**M11 OpenAPI Polish ✅ 2026-04-24 (tag `v0.0.0-alpha.12` локальный, 55 коммитов)**
M12 Auth Contract-first Refactor ⬜ (планирование в v0.0.0, реализация
в v0.1 — если owner не даст go раньше)

Dependency graph и полный roadmap — `docs/milestones/README.md`.

## Ожидающие явного `go`

1. `git push origin dev` — **55 коммитов** ahead (станет 60+ после M12
   если реализация).
2. `git push origin --tags` — **11 tags** (alpha.1-12).
3. После M12 → консолидация v0.0.0 → tag `v0.0.0`.
4. **VPS migration** (если deploy сейчас): заменить `.env.prod`
   `SWAGGER_PASSWORD=k9wHs9pkEv` на `SWAGGER_HTPASSWD=swagger:$$apr1$$/1wQbUj3$$ZDQBCO6u6D22hKDpzywoO0`,
   `docker compose up -d --force-recreate nginx` (см.
   `docs/runbooks/swagger-prod-access.md`).

## Рекомендация perf для M12

- `./gradlew :services:auth-service:auth-app:compileJava` быстрая
  проверка после каждого refactor'а (~10 сек).
- Full build **только** в конце каждой группы — в background с `&`
  (занимает ~12 мин на локалке).
- `./gradlew :services:auth-service:auth-app:integrationTest --tests "*OpenApiSnapshotIT" -Popenapi.snapshot.update=true`
  — пересоздать snapshot после DTO changes.
