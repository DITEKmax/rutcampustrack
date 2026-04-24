# Промпт для следующей сессии — M11 OpenAPI Polish

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам откроет
нужные файлы и продолжит.

---

**M10 Notification History ✅ закрыт 2026-04-24, tag `v0.0.0-alpha.11`
(локальный). 11 коммитов M10 + 2 hot-patch'а (`d6c0f14..58fd69f`).**

Локальных коммитов ahead origin: **30** (за всю v0.0.0). Tags
`v0.0.0-alpha.2..11` локальные. Push всё ещё отложен до явного `go`.

**Старт новой сессии — дословно:**

> Читаю `docs/milestones/NEXT-SESSION.md` →
> `docs/milestones/M11-openapi-polish/{PLAN,CHECKLIST,NOTES}.md` →
> `CLAUDE.md`. Запускаю **M11 OpenAPI Polish**:
>
> **G1 SharedOpenApiCustomizer наполнение:**
> 1. Создать `services/shared/shared-web/.../openapi/GlobalErrorResponsesCustomizer.java`
>    implements `OpenApiCustomizer`. Автоматически добавлять 7
>    стандартных `@ApiResponse` (400/401/403/404/409/429/500) с
>    `application/problem+json` + `ErrorResponse` schema на все
>    endpoints.
> 2. Per-endpoint override должен работать (customizer — defaults).
> 3. Unit `GlobalErrorResponsesCustomizerTest` — smoke + интеграция в
>    academic-service sample.
>
> **G2 @Schema на DTO полях:**
> 1. Audit ~60-80 DTO records / response classes в 4 сервисах
>    (academic/schedule/attendance/notification — auth-service skip,
>    P2-2/2 deferred в v0.1).
> 2. Priority pass: Request DTO → top-level Response → embedded.
> 3. `@Schema(description, example)` на каждое поле.
> 4. notification-api-contract — DTO уже создан в M10, здесь
>    финализируется @Schema.
>
> **G3 OpenAPI ↔ runtime conformance:**
> 1. Audit `@ApiResponse(responseCode = "200")` vs реальные controller
>    returns. `ResponseEntity<Void>` → 204; POST creates → 201 +
>    Location.
> 2. CI check: `atlassian/swagger-request-validator` или `openapi-diff`
>    runtime `/v3/api-docs` против committed `docs/api-spec/`.
> 3. Smoke: `./gradlew openapi-dump`.
> 4. NEW-123 → `docs/openapi-conformance.md` runbook.
>
> **G4 /swagger-ui basic-auth в prod:**
> 1. `infra/nginx/nginx.conf` — location block для
>    `/swagger-ui/**` + `/v3/api-docs` с `auth_basic`.
> 2. `infra/nginx/htpasswd/swagger.template` (пустой template).
> 3. `.env.prod.example` — `SWAGGER_HTPASSWD`.
> 4. NEW-125 → `docs/runbooks/swagger-prod-access.md` (rotation).
>
> **G5 Финализация:**
> 1. `./gradlew :services:notification-app:test` + аналог 3 сервисов —
>    зелёные.
> 2. Audit покрытия DTO ≥80% через grep.
> 3. Post-mortem в PLAN.md.
> 4. `git tag v0.0.0-alpha.12 -m "M11 OpenAPI Polish закрыт"`.
> 5. Обновить `NEXT-SESSION.md` на M12 Auth Contract-first.

Stop при сюрпризе → NOTES + спросить.

---

## M10 G1-G9 summary (что сделано)

| Группа | Commit | Scope |
|--------|--------|-------|
| G1 | `d6c0f14` | Mongo init-script (оба user'а PoLP D2), compose×2, secret-rotation runbook, future-ideas collMod |
| G2 | `8746e66` | notification-api-contract: NotificationType enum, NotificationHistoryDto class, UnreadCountDto record, NotificationApi interface |
| G3 | `cc4b05b` | NotificationHistoryDocument + 3 индекса (TTL env-driven) + Repository + Rabbit history queue+DLQ + Consumer (маппер 9 events, broadcast skip, error isolation) + 12 unit + IT |
| G4+G5 | `1624346` | CaffeineConfig (unread-count 30s) + NotificationHistoryService (@Cacheable/@CacheEvict + invalidate) + NotificationController (4 endpoints) + gateway route + 11 tests |
| G6 | `e6b3c34` | PWA: notificationsApi (HATEOAS parser) + useNotificationHistory TanStack hooks + NotificationCenter invalidate на STOMP + markAllRead best-effort + 5 tests |
| G7 | `4615e23` | web-panel: notification-history.api (Signal-based) + integration в NotificationCenterService + 5 tests |
| G8 | `c23614f` | docs(architecture/database-schema/data-retention/CLAUDE.md) |
| G9 hot-patches | `3d3eec6` + `4929d5b` | S4 explicit createCollection + ApplicationReadyEvent index bootstrap; H1 excuse.decided REJECTED через payload.status; H2 Pageable max-size cap |
| G9 final | `58fd69f` | CHECKLIST G9 ticks + Post-mortem + S5 docker-compose context defer |

## M10 Lessons learned (применяй в M11)

1. **Mongo @PostConstruct + ensureIndex silently no-op на пустой
   коллекции в Mongo 7.** Pattern: explicit `createCollection IF NOT
   EXISTS` + `ApplicationReadyEvent` (не `@PostConstruct`).
   Retroactive audit для PushMongoConfig — в N9-bundle.
2. **Decision events (`*.decided`) с binary payload — обязательны
   payload-aware маппинги.** Юнит-тест rejected path с самого
   начала.
3. **Pageable cap (`max-page-size`) — глобальный prep-step.** В M11
   audit на каждый PagedModel-возвращающий endpoint.
4. **Hot-patch verification через Testcontainers IT, не docker-compose
   smoke.** Когда image stale (S5 в M10), smoke даёт false confidence.
5. **bug-hunter + security-auditor запускать оба, не «или».** Разные
   модели угроз дают непересекающееся покрытие — H1 (юридически
   значимый bug) обнаружил bug-hunter, не security.

## Правила (без изменений)

- **Русский язык** в отчётах / NOTES / ответах.
- **Ветка `dev`**. Push на `main`/`origin` — только с явного `go`.
- Не звать `gsd-*` агентов. `Explore` для «найти все X»,
  `bug-hunter` + `security-auditor` — только в G-final.
- Surprise → NOTES.md + спросить до продолжения.
- Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — **ложные**, edit применяется.
- Атомарные коммиты per группа.

## M11 точки внимания

**G1 SharedOpenApiCustomizer:**
- `shared-web` уже содержит `SharedOpenApiCustomizer` заглушку
  (M01). Найти через `Grep "OpenApiCustomizer"` и наполнить.
- 4 сервиса (academic/schedule/attendance/notification) — уже
  intergrated через `shared-web` зависимость → автоматический pickup.
- Per-endpoint `@ApiResponses` НЕ перезаписывается customizer'ом
  (springdoc по умолчанию merge'ит).

**G2 @Schema:**
- ~60-80 DTO — много, но pattern одинаковый. Можно автогенерировать
  заглушки `@Schema(description = "TODO")` через скрипт + ручное
  заполнение в priority-pass.
- notification-api-contract DTO (M10) уже Class HATEOAS extends
  `RepresentationModel` — schema annotation на самих полях.

**G3 conformance:**
- `openapi-diff` — потенциально noisy, нужны конфигурируемые exclusions.
- Smoke `openapi-dump` — Spring Boot dev tool, поищи в build.gradle
  springdoc dependency.
- CI gate — рекомендую сначала **non-blocking** (warning), потом
  blocking после стабилизации specs.

**G4 nginx basic-auth:**
- В prod `infra/nginx/nginx.conf` уже существует с другими locations
  — добавить блок без ломки routing.
- `htpasswd` файл должен быть mounted, не committed.

**G5 потенциальные сюрпризы:**
- `springdoc-openapi-starter-webmvc-ui` версия — должна быть
  совместима с Spring Boot 3.4.1. Проверить `gradle/libs.versions.toml`.
- Generated TypeScript (M07 QC2) — после M11 пересборка openapi-typescript
  → новые JSDoc с descriptions. Не lock-step с M11, но affected.

---

## История предыдущих milestone (архив)

M01-M08 ✅ (см. предыдущие версии NEXT-SESSION.md)
M09 Prod Release Blockers ✅ 2026-04-24 (tag `v0.0.0-alpha.10` локальный)
**M10 Notification History ✅ 2026-04-24 (tag `v0.0.0-alpha.11` локальный)**
**M11 OpenAPI Polish ⬜ — эта сессия**
M12 Auth Contract-first Refactor ⬜ (планирование в v0.0.0; реализация v0.1)

Dependency graph и полный roadmap — `docs/milestones/README.md`.

## Ожидающие явного `go`

1. `git push origin dev` — **30 коммитов** ahead (станет ~35+ после M11).
2. `git push origin --tags` — **10 tags**, станет 11 после M11 tag.
3. После M11 → **M12 Auth Contract-first Refactor** (планирование в
   v0.0.0, реализация в v0.1). См.
   `docs/future-ideas.md` → «Auth API contract-first refactor».
