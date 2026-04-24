# Промпт для следующей сессии — консолидация v0.0.0 GA

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам
откроет нужные файлы и продолжит.

---

**M12 Auth Contract-first Refactor ✅ ЗАВЕРШЁН 2026-04-24
(7 коммитов + 1 hot-patch = 8 коммитов, tag `v0.0.0-alpha.13` локально).
Все 12 milestone'ов v0.0.0 закрыты. 65 коммит ahead origin/dev.
Push всё ещё отложен до явного `go`.**

**Что дальше: консолидация v0.0.0 → GA tag `v0.0.0` + push на
origin + VPS deploy.**

**Старт новой сессии — дословно:**

> Читаю `docs/milestones/NEXT-SESSION.md` →
> `docs/milestones/README.md` → `CLAUDE.md` → `CHANGELOG.md`.
> Все 12 milestone'ов v0.0.0 закрыты (M01-M12 ✅).
>
> **Задача консолидации v0.0.0 GA:**
>
> 1. **Final audit проход.** Проверить:
>    - `./gradlew build` + `./gradlew integrationTest` зелёные
>      (pre-existing flaky api-gateway RateLimitIT — acceptable, M11 noted)
>    - Все `docs/milestones/M*/CHECKLIST.md` имеют все пункты `[x]`
>    - `CHANGELOG.md [Unreleased]` содержит entries для M09/M10/M11/M12
>    - `docs/milestones/README.md` таблица: M01-M12 все ✅
>
> 2. **Перенос `[Unreleased]` → `[0.0.0]` секция в CHANGELOG.**
>    Дата выпуска = сегодня; заголовок с comparison link:
>    `## [0.0.0] - 2026-04-XX` + compare `v9.0.0...v0.0.0`
>    (или что там был последний release).
>
> 3. **Version bump по модулям.** Проверить:
>    - `build.gradle.kts` root `version` → проверить что v0.0.0
>    - Per-module `version` (если переопределены) — consistent
>    - `frontends/*/package.json` — version bump на 0.0.0
>    - `package-lock.json` regen при необходимости
>    - `docker-compose.prod.yml` — image tags на `:v0.0.0`
>
> 4. **VPS deployment runbook финализация.**
>    - Читать `docs/prod-deploy-checklist.md` (из M09)
>    - Проверить что `runbooks/secret-rotation.md` готов
>    - Swagger basic-auth на prod (M11 runbook) — проверить что
>      `.env.prod` имеет SWAGGER_AUTH_USER/SWAGGER_AUTH_PASS
>
> 5. **Final tag `v0.0.0`.** После all-green:
>    ```
>    git tag -a v0.0.0 -m "RutCampusTrack v0.0.0 — Pre-release hardening complete (M01-M12)"
>    ```
>    Локально; push отложен до `go`.
>
> 6. **Review + push decision.** Когда владелец даст `go`:
>    ```
>    git push origin dev
>    git push origin --tags  # 13 tags: alpha.1-13 + v0.0.0
>    ```
>
> 7. **VPS migration.** После push:
>    - `docs/prod-deploy-checklist.md` step-by-step
>    - Проверить health checks всех 14 containers
>    - Prometheus/Alertmanager smoke
>    - Swagger UI basic-auth login
>    - Seed тесты (student/admin/teacher login через PWA + web-panel)

Stop при сюрпризе → спросить.

---

## Статус v0.0.0 (2026-04-24)

### Milestone'ы (все закрыты)

| # | Milestone | Tag | Дата |
|---|-----------|-----|------|
| M01 | Shared Foundations | — | ✅ 2026-04-19 |
| M02 | Reliable Eventing | `v0.0.0-alpha.2` | ✅ 2026-04-19 |
| M03a | Internal JWT + Rate-limit | `v0.0.0-alpha.3` | ✅ 2026-04-20 |
| M03b | Secure Boundaries Part B | `v0.0.0-alpha.4` | ✅ 2026-04-20 |
| M04 | Observability | `v0.0.0-alpha.5` | ✅ 2026-04-20 |
| M05 | Performance | `v0.0.0-alpha.6` | ✅ 2026-04-21 |
| M06 | Ops & Supply Chain | `v0.0.0-alpha.7` | ✅ 2026-04-21 |
| M07 | Frontend Hardening | `v0.0.0-alpha.8` | ✅ 2026-04-22 |
| M08 | Test Infrastructure | `v0.0.0-alpha.9` | ✅ 2026-04-23 |
| M09 | Prod Release Blockers | `v0.0.0-alpha.10` | ✅ 2026-04-24 |
| M10 | Notification History | `v0.0.0-alpha.11` | ✅ 2026-04-24 |
| M11 | OpenAPI Polish | `v0.0.0-alpha.12` | ✅ 2026-04-24 |
| **M12** | **Auth Contract-first Refactor** | **`v0.0.0-alpha.13`** | **✅ 2026-04-24** |

### M12 финальные детали

**8 коммитов (`a902c16..HEAD`):**

| SHA | Группа | Описание |
|---|---|---|
| 9925376 | G1 | Gradle split auth-service → auth-api-contract + auth-app (80 файлов) |
| beafc3e | G1 fixup | infra files (settings + root build + Dockerfile + CI + scripts) |
| 315a317 | G2 | 12 DTO → auth-api-contract (100% rename) |
| e3a4adf | G3 | 4 interface'а (AuthApi + WsTicketApi + 2 Internal @Hidden) |
| bf2de65 | G4 | controllers implement + ArchUnit (3 правила) |
| 8d80740 | G5 | OpenApiSnapshotIT + regenerate auth.json + frontend types |
| 323e08e | G6 | Docs cleanup (CLAUDE.md/future-ideas/architecture/README/CHANGELOG) |
| ad40c53 | G7 hot-patch | Academic snapshot regenerate (M11 G5 missed step, binary-compat only) |

**Code review verdict (code-reviewer agent на `a902c16..HEAD`):**
- 0 BLOCK, 0 HIGH, 7 MEDIUM/NOTES (3 pre-existing, 4 positive observations)
- Binary-compat confidence 9/10
- Вердикт: «Рефакторинг можно тегать v0.0.0-alpha.13»

**Hot-patch ad40c53 — M11 G5 missed regenerate:**
M11 G5 (`d00a68e`) добавил `@Schema(description, example)` на
`UserCreatedResponse.initialPassword`, но `docs/openapi/academic.json`
+ frontend academic.types.ts не были перегенерированы. OpenApiSnapshotIT
для academic ломался в M12 G7 full build. Фикс — regenerate snapshot
+ frontend types. Binary-compat сохранён (только description + example,
тип остался string). Не M12 regression.

**Contract-first правило закрыто полностью:**
Единственное постоянное исключение — `api-gateway` (прокси, собственного
REST API не публикует). Все 5 backend сервисов имеют
`*-api-contract` + `*-app` split.

### Git state

```
ad40c53 fix(openapi): regenerate academic snapshot after M11 G5 missed regenerate
323e08e docs(m12): Contract-first exception removed + future-ideas cleanup
8d80740 feat(auth): OpenApiSnapshotIT + regenerate openapi/auth.json + frontend types
bf2de65 refactor(auth): controllers implement contract interfaces
e3a4adf feat(auth): add AuthApi + WsTicketApi + InternalIssuerApi + InternalWsTicketApi
315a317 refactor(auth): move DTO to auth-api-contract module
beafc3e fixup(m12 G1): include infra files missed by previous commit
9925376 refactor(auth): split into auth-api-contract + auth-app Gradle modules
```

Ahead origin/dev: **65 коммитов** (push отложен до `go`).
Локальный working tree чистый, кроме untracked `.coverage` (generated).

---

## Ожидающие явного `go`

1. `git push origin dev` — **65 коммитов** ahead.
2. `git push origin --tags` — **12 tags** локально (alpha.1-12).
   После v0.0.0 GA будет 14 tags (alpha.1-13 + v0.0.0).
3. Final `v0.0.0` tag после консолидации (см. промпт выше, пункт 5).
4. VPS migration `docs/prod-deploy-checklist.md` (M09 + M11 runbook'и).

---

## Правила (без изменений)

- **Русский язык** в отчётах / NOTES / ответах.
- **Ветка `dev`**. Push на `main`/`origin` — только с явного `go`.
- Не звать `gsd-*` агентов. `code-reviewer` — на финал per milestone.
- Surprise → `docs/milestones/*/NOTES.md` + спросить до продолжения.
- Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — **ложные**,
  edit применяется (подтверждено в M11/M12 сессиях).
- Атомарные коммиты per группа.
- CRLF warnings на Windows — **нормально**, не содержательная дельта.

---

## История milestone'ов (архив)

M01-M08 ✅ (см. предыдущие версии NEXT-SESSION.md и git tags)
M09 Prod Release Blockers ✅ 2026-04-24 (tag `v0.0.0-alpha.10`)
M10 Notification History ✅ 2026-04-24 (tag `v0.0.0-alpha.11`)
M11 OpenAPI Polish ✅ 2026-04-24 (tag `v0.0.0-alpha.12`, 55 коммитов)
M12 Auth Contract-first Refactor ✅ 2026-04-24 (tag `v0.0.0-alpha.13`, 8 коммитов)

Dependency graph и полный roadmap — `docs/milestones/README.md`.
