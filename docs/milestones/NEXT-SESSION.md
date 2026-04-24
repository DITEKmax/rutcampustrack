# Промпт для следующей сессии — M12 G5 (продолжение)

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам
откроет нужные файлы и продолжит.

---

**M12 Auth Contract-first Refactor — в работе. G1+G2+G3+G4 ✅ закрыты
2026-04-24 (5 коммитов). Осталось G5+G6+G7. 61 коммит ahead origin/dev.
Push всё ещё отложен до явного `go`. Последний tag v0.0.0-alpha.12
(M11), M12 tag будет в G7.**

**Старт новой сессии — дословно:**

> Читаю `docs/milestones/NEXT-SESSION.md` →
> `docs/milestones/M12-auth-contract/{PLAN,CHECKLIST,NOTES}.md` →
> `CLAUDE.md`. Продолжаю **M12 с Группы 5**.
>
> Текущее состояние: structurally контракт уже закрыт в коде (G1-G4).
> Осталось внешняя валидация + docs + финализация.
>
> **G5 Frontend regenerate + smoke:**
> 1. Обновить OpenAPI snapshot через IT:
>    ```
>    ./gradlew :services:auth-service:auth-app:integrationTest \
>        --tests "*OpenApiSnapshotIT" -Popenapi.snapshot.update=true
>    ```
>    (если такой IT существует для auth — проверить; иначе создать
>    по pattern academic OpenApiSnapshotIT M11 G3).
> 2. `docs/openapi/auth.json` — обновится после шага 1. Diff
>    относительно M11 baseline: must be empty changes (binary-compat)
>    ИЛИ только добавления description/example (acceptable).
> 3. **Frontend regenerate** (если npm script есть):
>    - `frontends/pwa/`: `npm run generate:types` или аналогичный
>    - `frontends/web-panel/`: `ng run generate:types` (если настроен)
>    - `frontends/mini-app/`: `npm run generate:types`
>    Проверить `grep -r generate:types frontends/*/package.json`.
> 4. Diff generated types — поля идентичны (binary-compat).
>    Если diff содержит только JSDoc description — acceptable.
> 5. **Smoke** (если есть docker daemon):
>    `docker compose up -d auth-service`, `curl http://localhost:9090/v3/api-docs`,
>    проверить что public endpoints в spec + internal endpoints отсутствуют
>    (из-за `@Hidden`).
>    Smoke login flow в PWA + web-panel — отложить на отдельную UAT
>    сессию если нет браузера.
>
> **Deviation note если smoke невозможен** (нет docker/npm/browser):
> - Оставить G5 как "static validation only" — OpenApiSnapshotIT
>   + commit snapshot. Реальный frontend regenerate + runtime smoke —
>   в hand-off для QA/владельца.
>
> **G6 Docs cleanup:**
> 1. `CLAUDE.md` раздел «Contract-first → Исключения»:
>    - **Убрать** целиком блок «auth-service — временный нарушитель»
>      (M12 P0-1 закрыт).
>    - Оставить только «api-gateway — единственное постоянное
>      исключение (прокси)».
>    - Обновить структуру репозитория: `services/auth-service/auth-api-contract/`
>      + `services/auth-service/auth-app/` (вместо single-module).
> 2. `CLAUDE.md` — M12 строка в таблице v0.0.0 Milestones → ✅ + дата.
> 3. `docs/architecture.md` — auth-service раздел: mention
>    auth-api-contract + auth-app структуру.
> 4. `docs/future-ideas.md` — удалить раздел «Auth API contract-first
>    refactor (v0.1)» (перенесено и закрыто в M12).
>    Также удалить раздел «P2-2/4 @Schema на всех DTO + P2-2/3
>    swagger-request-validator CI (v0.1)» (ошибочный, закрыт M11).
> 5. `docs/milestones/README.md` — M12 статус ✅ + дата.
> 6. `CHANGELOG.md [Unreleased]` — M12 entry.
>
> **G7 Финализация:**
> 1. `./gradlew build` + `./gradlew integrationTest` — полный прогон.
>    Pre-existing flaky api-gateway RateLimitIT — игнорировать (M11 noted).
> 2. code-reviewer agent на diff `a902c16..HEAD` — focus:
>    binary-compatibility (DTO shape не изменился), правильные
>    interfaces, OpenAPI полнота (public endpoints видны,
>    internal скрыты), no mapping annotations в controllers.
> 3. Post-mortem секция в `docs/milestones/M12-auth-contract/PLAN.md`
>    (что сюрпризнуло: CRLF normalization, G1 fixup коммит, nested
>    records extraction).
> 4. Локальный tag `v0.0.0-alpha.13`.
> 5. Переписать `NEXT-SESSION.md` — либо на консолидацию v0.0.0 →
>    `v0.0.0`, либо на новый milestone если такой планируется.

Stop при сюрпризе → NOTES + спросить.

---

## Что сделано в текущей сессии (2026-04-24, M12 G1-G4)

### Коммиты (5 шт, 61 total ahead origin/dev)

| SHA | Группа | Описание |
|---|---|---|
| 9925376 | G1 | split services/auth-service → auth-api-contract + auth-app (80 файлов git mv) |
| beafc3e | G1 fixup | settings.gradle.kts + build.gradle.kts root + auth-app/build.gradle.kts + Dockerfile + CI workflows + scripts/verify-gateway-e2e.sh + EventSchemaValidator.java (не попали в прошлый add) |
| 315a317 | G2 | 12 DTO → auth-api-contract (100% rename, records без Lombok) |
| e3a4adf | G3 | 4 interface'а (AuthApi + WsTicketApi + InternalIssuerApi + InternalWsTicketApi) + 2 DTO extracted (ConsumeWsTicket*) |
| bf2de65 | G4 | Controllers implement + ArchUnit (AuthApiContractTest, 3 правила) + WsTicketIT import fix |

### Важные технические детали

- **auth-api-contract build.gradle.kts** добавлены `spring-security-core` +
  `jakarta.servlet-api` — нужно controllers передают `Authentication` +
  `HttpServletRequest` в signature interface'ов. Это отличает auth от
  academic/schedule (там авторизация через X-User-Id header + AOP).
- **Docker image name оставлен `auth-service`** (owner-default #1):
  - Build path в Dockerfile: `:services:auth-service:auth-app:bootJar`
  - Jar path: `services/auth-service/auth-app/build/libs/*.jar`
  - Compose/gateway/nginx НЕ тронуты.
- **Internal endpoints `@Hidden`** (owner-default #2):
  `InternalIssuerApi` + `InternalWsTicketApi` не появляются в public
  /v3/api-docs (springdoc скрывает `@Hidden` interface целиком).
- **Раздельные Internal API interfaces** (owner-default #3):
  не один `InternalAuthApi`, а `InternalIssuerApi` + `InternalWsTicketApi` —
  разные domain boundaries.
- **DTO: все 12 — records без Lombok** (owner-default #4).
  `ErrorResponse` остался в shared-web-api (M11).
- **OtpCodeResponse не существует** — ошибка в PLAN.md, фактически 12 DTO.
- **Nested records extracted:** `InternalWsTicketController.ConsumeRequest/Response`
  → `auth-api-contract/dto/ConsumeWsTicketRequest.java` +
  `ConsumeWsTicketResponse.java`. Binary-compat сохранён:
  `@JsonProperty("user_id"/"group_id"/"is_headman"/"expires_at")`.
- **EventSchemaValidator.java schemasDir()** — +1 уровень вверх после
  move (`services/auth-service/auth-app` → 3 уровня до repo root,
  как у academic/schedule/attendance).
- **AuthController refactor trick:** `private ResponseCookie issueRefreshCookie`
  и `private static String resolveClientIp` остались в класс-private,
  Spring MVC наследует mappings через `@Override` (не ломая signature).
- **refresh-body endpoint deprecated:** headers `Deprecation: true` +
  `Sunset: Mon, 01 Jun 2026 00:00:00 GMT` сохранены в override'е.

### Validation (full `:check`)

`./gradlew :services:auth-service:auth-app:check` = **BUILD SUCCESSFUL** за 2m 31s:
- **Unit**: 24 теста (включая 3 ArchUnit AuthApiContractTest)
- **Integration** (Testcontainers + real Postgres/Redis):
  AuthIT, AuthOtpFlowIT, OtpIT, TmaIT, WsTicketIT, InternalIssuerIT,
  LogoutLifecycleIT, BcryptDoSMitigationIT, ActuatorIT,
  LoginRateLimiterIT, SameSiteCookieContractIT, IntegrationTestNamingConventionIT
- **JaCoCo ratchet**: passed (60% floor)

Spring MVC routing через interface работает end-to-end — binary-compat доказан.

### Git state

```
bf2de65 refactor(auth): controllers implement contract interfaces (M12 Группа 4, 01 P0-1)
e3a4adf feat(auth): add AuthApi + WsTicketApi + InternalIssuerApi + InternalWsTicketApi (G3)
315a317 refactor(auth): move DTO to auth-api-contract module (G2)
beafc3e fixup(m12 G1): include infra files missed by previous commit
9925376 refactor(auth): split into auth-api-contract + auth-app Gradle modules (G1)
a902c16 docs(m11 G5): отметить Группу 5 ✅ — M11 OpenAPI Polish закрыт
```

Ahead origin/dev: **61 коммит** (push отложен до `go`).
Локальный working tree чистый, кроме untracked `.coverage` (generated).

### В процессе резолвинга

- **`.planning/ROADMAP.md` + `.planning/STATE.md`** — unmerged `UU`
  конфликты были разрешены через `git checkout --ours` (выбран
  `Updated upstream` против устаревшего Stashed changes). Staged,
  но без отдельного коммита — попадут в первый последующий коммит.
  **Не забыть в G6/G7 коммите их включить явно** (они до сих пор
  в индексе).

---

## Что осталось в M12 (G5+G6+G7)

### G5 Frontend regenerate + smoke (~0.5д)

Status: не начат. См. CHECKLIST.md → Группа 5.
Depends on: G1-G4 (done).

Основные шаги (из M11 reference):
1. OpenApiSnapshotIT для auth-service — **проверить существует ли**.
   M11 G3 создал IT для 4 сервисов (academic/schedule/attendance/notification),
   **auth-service также должен был** получить OpenApiSnapshotIT — по NEXT-SESSION.md
   G4 комментарию. Если отсутствует — создать по pattern.
2. Regenerate `docs/openapi/auth.json` через
   `-Popenapi.snapshot.update=true`.
3. Frontend types regenerate (если npm script есть).
4. Smoke (если docker/browser доступны).

### G6 Docs cleanup (~0.25д)

Status: не начат. См. CHECKLIST.md → Группа 6.

**Ключевое:** `CLAUDE.md` раздел Contract-first → убрать блок
«auth-service — временный нарушитель», оставить только api-gateway.

### G7 Финализация (~0.25д)

Status: не начат. См. CHECKLIST.md → Группа 7.

Full build + code-reviewer + post-mortem + tag `v0.0.0-alpha.13`.

---

## Правила (без изменений)

- **Русский язык** в отчётах / NOTES / ответах.
- **Ветка `dev`**. Push на `main`/`origin` — только с явного `go`.
- Не звать `gsd-*` агентов. `Explore` для «найти все X»,
  `code-reviewer` + `security-auditor` — только в G7.
- Surprise → NOTES.md + спросить до продолжения.
- Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — **ложные**,
  edit применяется (проверено повторно в этой сессии, 15+ случаев).
- Атомарные коммиты per группа.
- CRLF warnings от git add на Windows — **нормально**, не
  содержательная дельта (git autocrlf=true по умолчанию).

## Ожидающие явного `go` (не изменилось)

1. `git push origin dev` — **61 коммит** ahead (станет 64+ после G5-G7).
2. `git push origin --tags` — **11 tags** (alpha.1-12). После G7 — 12 tags.
3. После M12 → консолидация v0.0.0 → tag `v0.0.0`.
4. VPS migration Swagger basic-auth (см. M11 runbook).

## Рекомендация perf для G5-G7

- `./gradlew :services:auth-service:auth-app:integrationTest --tests "*OpenApiSnapshotIT" -Popenapi.snapshot.update=true`
  — создать snapshot. ~30s.
- Full build **только** в G7 финал — background с `run_in_background`,
  занимает ~12 мин на локалке.
- Docker smoke (G5 optional) — `docker compose up -d auth-service`
  (один сервис, не весь стек). Если docker daemon недоступен —
  пропустить и документировать в NOTES.

---

## История milestone'ов (архив)

M01-M08 ✅ (см. предыдущие версии NEXT-SESSION.md)
M09 Prod Release Blockers ✅ 2026-04-24 (tag `v0.0.0-alpha.10` локальный)
M10 Notification History ✅ 2026-04-24 (tag `v0.0.0-alpha.11` локальный)
M11 OpenAPI Polish ✅ 2026-04-24 (tag `v0.0.0-alpha.12` локальный, 55 коммитов)
**M12 Auth Contract-first Refactor 🚧 G1-G4 ✅ 2026-04-24 (5 коммитов);
G5-G7 pending следующей сессии.**

Dependency graph и полный roadmap — `docs/milestones/README.md`.
