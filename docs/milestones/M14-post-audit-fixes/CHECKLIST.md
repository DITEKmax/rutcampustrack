# M14 Checklist

Атомарные задачи в порядке выполнения. Одна строка = ~15-45 минут.
Отмечаются `[x]` после коммита.

Порядок: сначала backend security defaults (минимальные изменения,
максимальный impact), потом supply chain, потом тесты, потом финал.

## Группа 1 — CSO CRIT-01: legacy headers strict default ✅ (commit `dc40929`)

- [x] `services/academic-service/academic-app/src/main/resources/application.yml` — `legacy-headers-enabled: ${RUTCAMPUSTRACK_SECURITY_LEGACY_HEADERS_ENABLED:false}`
- [x] `services/schedule-service/schedule-app/src/main/resources/application.yml` — same
- [x] `services/attendance-service/attendance-app/src/main/resources/application.yml` — same
- [x] `services/notification-service/notification-app/src/main/resources/application.yml` — same
- [x] `services/api-gateway/src/main/resources/application.yml` — `strip-legacy-headers: ${GATEWAY_STRIP_LEGACY_HEADERS:true}`
- [x] `.env.prod.example` — добавить комментарий о strict-mode invariant
- [x] Запустить `SecurityIdorIT` во всех 4 сервисах + `*UserContextFilterStrictModeIT` × 3 — BUILD SUCCESSFUL (5m33s)
- [x] ~~Manual UAT через docker-compose~~ — заменено на `*StrictModeIT` тесты, которые напрямую покрывают exploit scenario CRIT-01 (X-User-Role=ADMIN → ждём 401, получаем 401). academic/schedule/attendance × 3 тест-метода каждый. Notification IDOR тоже зелёный с legacy-mode override.
- [x] **Решение по notification-app:** добавлен inline-property в `SecurityIdorIT` (`@SpringBootTest properties`) с `legacy-headers-enabled=true` — у остальных 3 сервисов `application-test.yml` уже выставлял это (M03a artifact), у notification — не было.
- [x] Commit: `fix(security): legacy headers strict by default (M14 G1, CSO CRIT-01)` — `dc40929`

## Группа 2 — CSO CRIT-02: SHA-pin appleboy/ssh-action ✅ (commit `a93859b`)

- [x] Pre-flight `curl .../releases/latest` → `v1.2.5` (актуальнее чем `v1.2.0` из hand-off)
- [x] `curl .../git/refs/tags/v1.2.5` → SHA `0ff4204d59e8e51228ff73bce53f80d53301dee2` (type=commit, lightweight tag)
- [x] `.github/workflows/deploy.yml:313` — `appleboy/ssh-action@0ff4204d59e8e51228ff73bce53f80d53301dee2 # v1.2.5`
- [x] Verify: `grep` ровно 1 вхождение в SHA-формате; `pyyaml safe_load` парсит без ошибок (actionlint не установлен)
- [x] Commit: `fix(ci): SHA-pin appleboy/ssh-action against supply chain (M14 G2, CSO CRIT-02)` — `a93859b`

## Группа 3 — CSO HIGH-05: PKCS#8 + idempotent JWT key gen ✅ (commit `7e69067`)

- [x] Reference: `services/auth-service/Dockerfile:69-79` (M13 G25.15) — 3-step pipeline (genrsa → pkcs8 -topk8 -nocrypt → rsa -pubout)
- [x] `.github/workflows/deploy.yml:329-360` — explicit PKCS#8 conversion + atomic idempotency через `if [ ! -f /keys/private.key ]; then ... fi`
- [x] **Решено: first-deploy detection через filesystem (`[ ! -f ]`), НЕ через `.deployed-sha` marker.** Filesystem check проще + надёжнее (Linux atomic file existence) и matches JwtService.init() pattern. Маркер `.deployed-sha` уже пишется отдельно (deploy.yml:366) для observability — не дублируем.
- [x] kid через `head -c 4 /dev/urandom | od -A n -t x1 | tr -d ' \n' > /keys/kid.txt` (тот же pipeline что Dockerfile, не `openssl rand -hex 4`)
- [x] chmod / chown matching Dockerfile: `chown -R 100:101 /keys && chmod 600 /keys/private.key /keys/kid.txt && chmod 644 /keys/public.key`
- [x] Local dry-run на чистом docker volume: `head -1 /keys/private.key` = `-----BEGIN PRIVATE KEY-----` (PKCS#8) ✅
- [x] **Бонус-verification**: md5sum ключей → второй запуск pipeline даёт ИДЕНТИЧНЫЕ hash → atomic idempotent ✅
- [x] **Бонус-verification**: JDK 21 standalone clone JwtService.loadPrivateKey/loadPublicKey парсит keys без exception ✅
- [x] **Бонус-negative test**: тот же clone на PKCS#1 ключе из alpine:3.13 (OpenSSL 1.x) → `InvalidKeySpecException "Unable to decode key"` ✅
- [x] Commit: `fix(ci): PKCS#8 + idempotent JWT key gen в deploy.yml (M14 G3, CSO HIGH-05)` — `7e69067`

## Группа 4 — CSO HIGH-06: fail-fast secrets ✅ v2 (commit `bf915ec`)

**История:** v1 attempt (~1ч) провалился — Spring Boot 3.x не fail-fast
на unresolved YAML placeholders ни через `${VAR:?msg}` (bash syntax), ни
через `${VAR}` без default (issues spring-boot#10463 / #18816, placeholder
остаётся literal `"${VAR}"` string). v1 откатан полностью.

**v2 solution:** новый `RequiredSecretsValidator` через
`EnvironmentPostProcessor` в `shared-web/autoconfigure/`, регистрируется
через `META-INF/spring.factories`. Срабатывает на самом раннем
`ApplicationEnvironmentPreparedEvent` — ДО bean creation, ДО Tomcat init,
ДО Spring banner. Profile-aware skip (`test`/`local` → no-op). Per-service
opt-in через `rutcampustrack.security.required-env-vars` CSV property.

- [x] Inventory: 21 dev fallback secret'ов в 6 application.yml
- [x] v1 attempt: `${VAR:?msg}` (bash) → не сработало → откат
- [x] v1 attempt: `${VAR}` (Spring без default) → тоже не сработало → откат
- [x] **v2 design:** EnvironmentPostProcessor вместо placeholder syntax трюков. Profile-aware. Per-service opt-in.
- [x] `services/shared/shared-web/src/main/java/.../autoconfigure/RequiredSecretsValidator.java` — 95 строк, slf4j + Spring API
- [x] `services/shared/shared-web/src/main/resources/META-INF/spring.factories` — registration
- [x] `services/shared/shared-web/src/test/java/.../RequiredSecretsValidatorTest.java` — 9 unit tests (missing/blank/all-present/test-skip/local-skip/no-opt-in/blank-opt-in/multiple-missing/CSV-whitespace) — BUILD SUCCESSFUL
- [x] api-gateway: `implementation(":services:shared:shared-web")` (новая dependency, ~200KB transitive overhead за spring-security-core; SharedWebAutoConfiguration сам не активируется в WebFlux, но EnvironmentPostProcessor работает независимо)
- [x] Per-service `rutcampustrack.security.required-env-vars` в 6 application.yml (gateway:2, auth:5, academic:4, schedule:3, attendance:4, notification:4 secrets)
- [x] Полный gradle test 7 modules (shared-web + 5 backend + gateway) — BUILD SUCCESSFUL за 4m51s, 81 actionable tasks
- [x] **UAT positive 1**: `docker run` БЕЗ env vars → IllegalStateException на самом старте (EnvironmentPostProcessorApplicationListener), ДО Spring banner, с явным actionable сообщением
- [x] **UAT positive 2**: `docker run` с 3 из 5 env vars → IllegalStateException указывает точно те 2 missing (per-variable accuracy)
- [x] **UAT positive 3**: `docker run` со всеми 5 env vars → validator passes, fail дальше на JwtService.init (orthogonal — отсутствие mount /keys)
- [x] Commit: `fix(security): RequiredSecretsValidator — fail-fast на missing critical secrets (M14 G4 v2, CSO HIGH-06)` — `bf915ec`

## Группа 5 — CSO HIGH-07: aiohttp bump ✅ (commit `607af81`)

**Surprise при pre-flight:** aiogram 3.15.0 пинует `aiohttp<3.11`,
поэтому одновременно нужен bump aiogram. Проверка PyPI показала, что
**aiogram 3.23.0** — минимальная версия, разрешающая `aiohttp<3.14`.
Conservative выбор vs latest 3.27 (8 minor versions vs 12).

- [x] Pre-flight: `curl pypi.org/pypi/aiohttp/json` → latest 3.13.5; `aiogram 3.15.0` requires `aiohttp<3.11`; `aiogram 3.23.0+` requires `aiohttp<3.14` ✅
- [x] `requirements.txt` — bump aiogram 3.15.0→3.23.0 + aiohttp 3.10.11→3.13.5
- [x] Локальная проверка: `pip install -r requirements.txt` в чистом venv → no conflicts ✅
- [x] `pytest services/notification-bot/tests/` → 205 passed in 29.29s, coverage 77.19% (порог 50%) ✅
- [x] Rebuild bot image: `docker build` → SUCCESS, 22s ✅
- [x] Smoke check inside container: `python -c "import aiohttp, aiogram; print(versions)"` → `aiohttp=3.13.5 aiogram=3.23.0` ✅
- [x] Commit: `chore(deps): aiohttp 3.10.11→3.13.5 + aiogram 3.15.0→3.23.0 (M14 G5, CSO HIGH-07)` — `607af81`

## Группа 6 — CSO HIGH-03/04: SHA-pin remaining actions ✅ (commit `7fbd908`)

**Pre-flight surprises:**
1. `gh` CLI отсутствует в bash PATH (известно с G2). Использован `curl`
   на REST API `git/refs/tags/{tag}` + Python (через `py`) для join SHA→tag.
2. `marocchino/sticky-pull-request-comment` НЕ имеет floating `v2` ref —
   только конкретные `v2.x.y`. Запpinили на latest `v2.9.4`.
3. **Annotated tags** требуют доп. resolve через `git/tags/{sha}`: gradle/actions
   v6 (50e97c2c), gitleaks v2 (ff981064), codeql-action v3 (ce64ddcb).
   Lightweight tags — sha напрямую.

**Pin'нутые actions с конкретными версиями:**

| Action | Tag | SHA |
|---|---|---|
| actions/checkout | v4.3.1 | 34e114876b0b11c390a56381ad16ebd13914f8d5 |
| actions/setup-java | v4.8.0 | c1e323688fd81a25caa38c78aa6df2d33d3e20d9 |
| actions/setup-node | v4.4.0 | 49933ea5288caeca8642d1e84afbd3f7d6820020 |
| actions/setup-python | v5.6.0 | a26af69be951a213d495a4c3e4e4022e16d87065 |
| actions/upload-artifact | v4.6.2 | ea165f8d65b6e75b540449e92b4886f43607fa02 |
| actions/download-artifact | v4.3.0 | d3f86a106a0bac45b974a628896c90dbdf5c8093 |
| docker/setup-buildx-action | v3.12.0 | 8d2750c68a42422c14e847fe6c8ac0403b4cbd6f |
| docker/login-action | v3.7.0 | c94ce9fb468520275223c153574b00df6fe4bcc9 |
| docker/build-push-action | v7.1.0 | bcafcacb16a39f128d818304e6c9c0c18556b85f |
| gradle/actions/setup-gradle | v6.1.0 | 50e97c2cd7a37755bbfafc9c5b7cafaece252f6e |
| madrapps/jacoco-report | v1.7.1 | 7c362aca34caf958e7b1c03464bd8781db9f8da7 |
| davelosert/vitest-coverage-report-action | v2 | 3c50566c523e04813df28de8f7c48dd97d663f1c |
| MishaKav/pytest-coverage-comment | v1.1.52 | fa1c641d7e3fa1d98ed95d5f658ccd638b774628 |
| marocchino/sticky-pull-request-comment | v2.9.4 | 773744901bac0e8cbb5a0dc842800d45e9b2b405 |
| gitleaks/gitleaks-action | v2 | ff98106e4c7b2bc287b24eaf42907196329070c7 |
| github/codeql-action/upload-sarif | v3 | ce64ddcb0d8d890d2df4a9d1c04ff297367dea2a |

- [x] Собрать SHAs через `curl + py` (gh CLI отсутствует) для 16 actions
- [x] `.github/workflows/deploy.yml` — заменить все first-party actions на SHA-pinned (16 occurrences: 2× checkout, 1× buildx, 3× login, 11× build-push)
- [x] `.github/workflows/coverage.yml` — заменить 14 actions на SHA-pinned (4× checkout, 1× setup-java, 1× setup-node, 2× setup-python, 3× upload-artifact, 1× download-artifact, 1× setup-gradle, 1× jacoco-report, 1× vitest-coverage, 1× MishaKav, 1× marocchino)
- [x] `.github/workflows/coverage.yml` — top-level `pull-requests: write` + `checks: write` сужены до `contents: read`. Per-job permissions добавлены в java-coverage (full PR+checks), frontend/python-coverage (PR only), diff-cover (PR only)
- [x] `.github/workflows/security.yml` — SHA-pin 3× checkout, codeql-action upload-sarif (annotated tag resolved), gitleaks-action (MED-09), docker/login-action
- [x] Verify: `grep -rE "uses: [^@]+@v[0-9]" .github/workflows/{deploy,coverage,security}.yml` → пусто
- [x] YAML safe_load для всех 3 файлов → OK
- [x] Commit: `fix(ci): SHA-pin third-party + first-party actions в deploy/coverage/security (M14 G6, CSO HIGH-03/04 + MED-09)` — `7fbd908`

**Out of scope G6 (намеренно):** `ci.yml` + `openapi-drift.yml` оставлены с floating tags — hand-off purposefully ограничил список workflow'ов до deploy/coverage/security. Pin для них либо в отдельной группе после M14, либо через Renovate `digest:pin` sweep.

## Группа 7 — G26 test-audit P1 ✅ (commit `f24f22f`)

**Pre-flight surprises:**

1. **`headman-mark.spec.ts` — by-design out of scope для web-panel.**
   Spec тестирует UI которого нет в web-panel (нет lesson-card listings,
   нет BottomSheet «Отметить всех», нет group-attendance-count stat).
   Backend готов (`MarkingApi.batchMark` + DTO) и **используется PWA**
   (`frontends/pwa/src/features/schedule/HeadmanLessonSheet.tsx` →
   `useHeadmanMarkBatch`). Owner decision: web-panel НЕ получает
   bulk-mark UI — староста делает массовую отметку через PWA
   (мобильный flow). Web-panel остаётся desktop-инструментом для
   журналов и ручных action'ов. Spec помечен `test.describe.skip`
   permanent (не «временный TODO»).

2. **Категория E решение — path A** (удалить 2 теста). Reasoning:
   seed `student` имеет `is_headman=true`, тесты `cannot access /headman/*`
   технически invalid; RBAC уже покрыт backend SecurityIdorIT × 4
   сервисов + WebPanel route guards в Karma unit. E2E дублирование
   избыточно. Path B (Flyway seed `student_plain`) overhead не
   оправдан без real signal о gap.

3. **`student-excuse.spec.ts` имел несколько UI mismatches** — MatSelect
   vs native `<select>`, отсутствующий `test-excuse.pdf` fixture,
   неверный success indicator (toast vs dialog close + table row).
   Поправил все, плюс `beforeAll` генерирует 10MB PDF runtime.

4. **`admin-create-user.spec.ts` mismatch** — close button «Готово»,
   spec ждал `/закрыть|ок/i`. Расширил regex.

### Spec fixes (категория A, B, E)

- [x] `tests/e2e/specs/role-teacher.spec.ts:18` `/teacher/schedule` →
  `/teacher/journal` (B)
- [x] `tests/e2e/specs/role-teacher.spec.ts:31` тест `red-zone-badge`
  удалён (UI feature не реализован)
- [x] `tests/e2e/specs/role-student.spec.ts` 2 теста удалены —
  path A для категории E
- [x] `tests/e2e/specs/admin-create-user.spec.ts:49` close button
  regex расширен `/готово|закрыть|ок/i`
- [x] `tests/e2e/specs/student-excuse.spec.ts` — selectOption →
  click+option, file fixture generated в beforeAll, success indicator
  fixed, MatSelect alignment, headman approve scoped через
  excuse-card → button «Одобрить»
- [x] `tests/e2e/specs/headman-mark.spec.ts` — `test.describe.skip`
  permanent с rationale «web-panel не получает bulk-mark UI by design»
  (PWA владеет flow через `HeadmanLessonSheet.tsx`)

### Template testid additions (где semantic невозможен)

- [x] `frontends/web-panel/src/app/features/student/excuses/excuse-form-dialog/excuse-form-dialog.component.html` — `data-testid="lesson-picker-item"`
- [x] `frontends/web-panel/src/app/features/headman/excuses/headman-excuses.component.ts` — `data-testid="excuse-card"`
- [x] `frontends/web-panel/src/app/features/admin/users/user-dialog/user-dialog.component.html` — `data-testid="initial-password-display"`

### Verification

- [x] `npx playwright test --list` парсит все 9 spec'ов без TypeScript
  ошибок (потребовал npm install в `tests/e2e/`, добавил
  `package-lock.json` для CI reproducibility)
- [x] `role-student.spec.ts` показывает 1 тест (was 3) после path A
- [x] `headman-mark.spec.ts` тесты в listing с `[skipped]` пометкой,
  не выполняются (правильное поведение `test.describe.skip`)
- [x] 3 testid'а verified `grep` по templates
- [ ] **Полный e2e run отложен на G9** — требует docker compose up +
  web-panel rebuild (~15-30 мин setup). Будет в финальной UAT.

- [x] Commit: `fix(e2e): G26 false-pass tests — testid + routes + skip forward-written + path A для seed mismatch (M14 G7)` — `f24f22f`
- [x] Corrective patch: `fix(e2e): headman-mark.spec.ts skip — by-design out of scope для web-panel (M14 G7 follow-up)` — после discovery что bulk-mark **уже работает в PWA**, не нужен v0.1 backlog для web-panel.

### Out of scope G7 (by-design, не deferred)

- Headman bulk-mark UI в **web-panel** — by-design не делается. PWA
  (`frontends/pwa/src/features/schedule/HeadmanLessonSheet.tsx`)
  владеет этим flow, web-panel остаётся desktop tool для журналов и
  ручных action'ов.
- Polish red-zone-badge stat — feature не запланирована, без явного
  user request в v0.1+ не делать.

## Группа 8 — G26 code-review P1 ✅ (commit `c09b002`)

**3 finding'а закрыты в одном commit'е (~30 мин):**

### F01 — auth-login burstCapacity: production default 60

- [x] `services/api-gateway/src/main/resources/application.yml:122`:
  `burstCapacity: 600` → `${AUTH_LOGIN_BURST_CAPACITY:60}` (env override)
- [x] `docker-compose.e2e.yml`: добавил `AUTH_LOGIN_BURST_CAPACITY: "600"`
  в gateway env — override активен только в e2e compose, prod default 60
- [x] **Workers оставил workers=2 для CI** — e2e compose всё ещё имеет
  600 burst (через override), параллельные smoke не блокированы.
  Mitigation если flaky: снизить до workers=1 в G9.

**Production guarantee:** brute-force защита 5/мин/IP per CSO threat
model восстановлена (60 / requestedTokens=12 = 5 запросов залпом).
Workaround на 600 ослаблял защиту в 10× на каждый prod deploy с M13 G25.22.

### F02 — diagnostic test removed

- [x] `tests/e2e/specs/auth.spec.ts` — удалён `diagnostic: direct POST
  /api/auth/login` (был в lines 19-38). console.log шумел в CI логах,
  expect 200/401 неявно покрыты `student login → dashboard visible`.
- [x] Бонус: -12 токенов из auth-login burst в каждом parallel run.

### F03 — TEST_USERS.headman дубликат удалён (DRY)

- [x] `tests/e2e/fixtures/users.ts`: запись `headman` (1-в-1 копия
  `student`) удалена.
- [x] 4 caller'а обновлены: role-headman (1), headman-mark (2 — skipped
  но обновлено для consistency), student-excuse (1 — комментарий).
- [x] `grep -rn "TEST_USERS.headman" tests/e2e/` — только упоминания
  в комментариях.

### Verification

- [x] `npx playwright test --list` — 10 hits для auth.spec.ts (было 12,
  -2 за удаление diagnostic × 2 проекта). Все 9 spec'ов парсятся.
- [x] YAML safe_load для `application.yml` + `docker-compose.e2e.yml` → OK
- [ ] **Полный smoke run отложен на G9** — требует docker compose up.
  Risk: `burstCapacity=60` для prod может не хватить при наплыве. Если
  Grafana покажет 429 spikes на /auth/login — добавить
  `AUTH_LOGIN_BURST_CAPACITY=120` в `.env.prod` (override без code change).

- [x] Commit: `fix(gateway,e2e): burstCapacity prod default + diagnostic test removal + DRY users fixture (M14 G8, G26 F01-F03)` — `c09b002`

## Группа 9 — UAT + tag (30 мин)

- [ ] Полный local pipeline: `./gradlew build` → ожидаем зелёный
- [ ] Local docker-compose smoke: `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d` → все 26 контейнеров healthy
- [ ] Local Playwright `@smoke` → зелёный
- [ ] Local pytest `services/notification-bot/tests` → зелёный
- [ ] Local web-panel + pwa unit tests → зелёные (`npm run test` в каждом)
- [ ] `scripts/preflight-deploy.sh` → зелёный
- [ ] Push `dev` → ожидать green CI
- [ ] Tag `v0.0.0-alpha.16` с message со списком CRIT/HIGH fixes (CRIT-01, CRIT-02, HIGH-03, HIGH-04, HIGH-05, HIGH-06, HIGH-07 + G26 P1)
- [ ] `git push origin v0.0.0-alpha.16`
- [ ] Обновить `CLAUDE.md` § «Текущий статус» — добавить `M14: завершён 2026-04-26`
- [ ] Обновить `docs/milestones/README.md` — добавить строку M14 в таблицу
- [ ] Финальный commit: `docs: M14 finalised, alpha.16 tagged (post-audit fixes)`

---

_Если тест с категории E (role-student) показывает что path B (новый
seed user) требует Flyway migration на seed data — выделить в отдельную
задачу M14.7.B и оценить cost vs path A (удалить тест). Решение в NOTES._
