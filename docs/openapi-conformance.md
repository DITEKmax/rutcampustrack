# OpenAPI ↔ runtime conformance (NEW-123)

**Статус:** ✅ реализовано M11 G3 (2026-04-24)
**Scope:** academic, schedule, attendance, notification

Гарантирует, что committed OpenAPI spec в `docs/openapi/{service}.json`
всегда соответствует тому, что runtime actually exposes на
`GET /api-docs`. Любое расхождение блокирует CI.

---

## Как это работает

1. **Snapshot baseline** — `docs/openapi/{service}.json` хранится в
   репозитории и является source of truth (одновременно для M07
   frontend types generation **и** M11 runtime drift-check).
2. **Per-service `OpenApiSnapshotIT`** (integration-test) загружает
   Spring context, GET'ает `/api-docs`, нормализует JSON (сортировка
   ключей, 2-space indent, убирает волатильные поля `servers[]`) и
   сравнивает с committed snapshot'ом.
3. **CI** запускает эти `*IT` в `java-integration-test` job —
   любое расхождение = red build.
4. **Frontend drift-guard** (`.github/workflows/openapi-drift.yml`,
   M07 NEW-84) поверх тех же spec'ов проверяет, что generated TS
   types в PWA/web-panel соответствуют committed OpenAPI.

## Когда обновлять snapshot

Новая OpenAPI spec коммитится **вместе с** backend DTO / controller
/ `@ApiResponse` / `@Schema` изменением.

### Шаг 1: обновить local snapshot

```bash
# Regenerate один сервис (быстрее):
./gradlew :services:academic-service:academic-app:integrationTest \
    --tests "*OpenApiSnapshotIT" \
    -Popenapi.snapshot.update=true

# Regenerate все 4 параллельно (медленнее, ~10 мин на локалке):
./gradlew :services:academic-service:academic-app:integrationTest \
    :services:schedule-service:schedule-app:integrationTest \
    :services:attendance-service:attendance-app:integrationTest \
    :services:notification-service:notification-app:integrationTest \
    --tests "*OpenApiSnapshotIT" \
    -Popenapi.snapshot.update=true
```

Флаг `-Popenapi.snapshot.update=true` проброшен как Gradle property,
которая в root `build.gradle.kts` копируется в JVM system property
forked test runner'а (см. passthrough блок в `integrationTest` task).

### Шаг 2: regenerate frontend TS types

```bash
cd frontends/pwa && npm run generate:types:offline
cd ../web-panel && npm run generate:types:offline
```

Оба команды читают обновлённые `docs/openapi/*.json` (без backend
running) и переписывают `src/api/generated/*.types.ts`.

### Шаг 3: commit всё вместе

```bash
git add docs/openapi/ \
    frontends/pwa/src/api/generated/ \
    frontends/web-panel/src/app/api/generated/
git commit -m "feat(api): <описание изменения контракта>"
```

## Что делать при red CI (drift detected)

### Вариант A: intended change

Запускаешь Шаги 1-3 из раздела «Когда обновлять» → commit →
push → CI зелёный.

### Вариант B: unintended regression

CI fail в `java-integration-test` с ошибкой в `OpenApiSnapshotIT`
означает, что твой DTO/controller change **случайно сломал**
обратно-совместимый OpenAPI формат (переименовал поле,
удалил endpoint, поменял required/nullable, etc).

1. Открой `services/{svc}/build/reports/integrationTest/` из CI
   artifact — там HTML с diff.
2. Если изменение break'нет frontend — откати. Если намеренно —
   обнови snapshot (Variant A).

Frontend drift fail (`.github/workflows/openapi-drift.yml`) —
забыл regenerate `*.types.ts`. Запусти `npm run generate:types:offline`
в PWA и web-panel, commit файлы.

## Архитектурные решения

### Почему per-service IT вместо `gradle openapi-dump + oasdiff`?

- **Faster feedback:** integration test runs уже есть в CI
  (~1-3 мин per service) — not adding a separate 5-min step.
- **Portable:** no external Go tool (oasdiff), no Docker pull in CI.
- **Single source of truth:** тот же Testcontainers + Spring
  context, что и остальные *IT.
- **Built-in update flag:** `-Popenapi.snapshot.update=true` — в
  отличие от ручного `curl /api-docs > docs/openapi/x.json`.

### Почему `docs/openapi/`, а не `docs/api-spec/`?

Единый baseline для M07 frontend drift + M11 runtime conformance.
Две папки — два источника истины с разным lag'ом; неизбежно
расходятся.

### Почему `FieldError` нужно регистрировать вручную?

`FieldError` класс в `shared-web-api`, springdoc controller-scan
не находит модуль (нет controller, который принимает FieldError
как request body). `ErrorResponse.fieldErrors: List<FieldError>`
— референс на незарегистрированную schema → openapi-typescript
ломается.

Решение — `GlobalErrorResponsesCustomizer.registerErrorResponseSchema`
вручную регистрирует `resolved.referencedSchemas` (включая
`FieldError`) в `components.schemas`. См. M11 G3 changes в
`services/shared/shared-web/.../GlobalErrorResponsesCustomizer.java`.

## Related

- **M07 frontend types generation** — `.github/workflows/openapi-drift.yml`
- **M11 G0/G1/G2** — `docs/milestones/M11-openapi-polish/`
- **M11 G3** — этот runbook + 4 `OpenApiSnapshotIT` files
- **api-error-conventions** — `docs/api-error-conventions.md`
