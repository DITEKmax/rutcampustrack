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

## Группа 4 — CSO HIGH-06: fail-fast secrets во всех services (20 мин)

- [ ] `services/auth-service/auth-app/src/main/resources/application.yml` — заменить `${INTERNAL_ISSUER_SECRET:dev-...}` на `${INTERNAL_ISSUER_SECRET:?INTERNAL_ISSUER_SECRET must be set in environment}`
- [ ] `services/auth-service/auth-app/src/main/resources/application.yml` — то же для `REDIS_PASSWORD`, `SPRING_RABBITMQ_PASSWORD`, `POSTGRES_AUTH_PASSWORD`
- [ ] `services/academic-service/academic-app/src/main/resources/application.yml` — fail-fast: `POSTGRES_ACADEMIC_PASSWORD`, `REDIS_PASSWORD`, `SPRING_RABBITMQ_PASSWORD`, `GRPC_SECRET`
- [ ] `services/schedule-service/schedule-app/src/main/resources/application.yml` — fail-fast: `POSTGRES_SCHEDULE_PASSWORD`, `SPRING_RABBITMQ_PASSWORD`, `GRPC_SECRET`
- [ ] `services/attendance-service/attendance-app/src/main/resources/application.yml` — fail-fast: `MONGO_PASSWORD`, `SPRING_RABBITMQ_PASSWORD`, `GRPC_SECRET`
- [ ] `services/notification-service/notification-app/src/main/resources/application.yml` — fail-fast: `MONGO_PASSWORD`, `SPRING_RABBITMQ_PASSWORD`, `ALERT_WEBHOOK_SECRET`
- [ ] `services/api-gateway/src/main/resources/application.yml` — fail-fast где есть dev fallback'и
- [ ] Local UAT: `docker compose up -d` БЕЗ `--env-file` → ожидаем что auth-service exit'нется с явной ошибкой `INTERNAL_ISSUER_SECRET must be set`. Затем с `--env-file .env.prod.example` (заполнен test values) → стартует.
- [ ] Запустить `./gradlew :services:auth-service:auth-app:test` — проверить, что test-profile не падает (Spring test использует `application-test.yml` или explicit env)
- [ ] Commit: `fix(security): fail-fast на critical secrets во всех services (M14 G4, CSO HIGH-06)`

## Группа 5 — CSO HIGH-07: aiohttp bump (5 мин)

- [ ] `services/notification-bot/requirements.txt` — `aiohttp>=3.13.3,<3.14` (было `3.10.11`)
- [ ] Локальная проверка совместимости: `cd services/notification-bot && python -m venv .venv-test && .venv-test/Scripts/pip install -r requirements.txt` (Windows-вариант) — должно установиться без conflict с `aiogram`
- [ ] `pytest services/notification-bot/tests` — все зелёные после bump
- [ ] Rebuild bot image: `docker build -t rct-notification-bot services/notification-bot/`
- [ ] Commit: `chore(deps): aiohttp 3.10.11→3.13.3+ (M14 G5, CSO HIGH-07, 3 CVE)`

## Группа 6 — CSO HIGH-03/04: SHA-pin remaining actions (45 мин)

- [ ] Собрать SHAs одним `gh api` batch'ем для:
  - `actions/checkout@v4`
  - `docker/setup-buildx-action@v3`
  - `docker/login-action@v3`
  - `docker/build-push-action@v7`
  - `madrapps/jacoco-report@v1.7.1`
  - `davelosert/vitest-coverage-report-action@v2`
  - `MishaKav/pytest-coverage-comment@v1.1.52`
  - `marocchino/sticky-pull-request-comment@v2`
  - `gitleaks/gitleaks-action@v2`
- [ ] `.github/workflows/deploy.yml` — заменить все 4 first-party actions на SHA-pinned
- [ ] `.github/workflows/coverage.yml` — заменить 4 third-party actions на SHA-pinned
- [ ] `.github/workflows/coverage.yml` — перенести `permissions: { pull-requests: write, checks: write }` из top-level в per-job (только jobs которые комментируют PR)
- [ ] `.github/workflows/security.yml` — SHA-pin gitleaks-action (MED-09 заодно, ~5 мин трудозатрат)
- [ ] Verify: `grep -rE "uses: [^@]+@v\d+(\.\d+)?$" .github/workflows/deploy.yml .github/workflows/coverage.yml` → пусто
- [ ] Commit: `fix(ci): SHA-pin third-party + first-party actions в deploy/coverage/security (M14 G6, CSO HIGH-03/04 + MED-09)`

## Группа 7 — G26 test-audit P1 (1-1.5 ч)

- [ ] `tests/e2e/specs/role-teacher.spec.ts:18` — убрать `/teacher/schedule` из `paths` array (категория B; роута нет в `TEACHER_ROUTES`)
- [ ] **Решить в NOTES:** для категории E (`role-student.spec.ts`) — путь A (удалить тесты "cannot access /headman/*") или путь B (добавить seed `student_plain` с `is_headman=false`). Записать выбор + rationale.
- [ ] Реализовать выбранный путь по категории E
- [ ] `tests/e2e/specs/role-teacher.spec.ts:34` — заменить `[data-testid="red-zone-badge"]` на ARIA role/text локатор; либо добавить `data-testid="red-zone-badge"` в `web-panel` шаблон (категория A)
- [ ] `tests/e2e/specs/headman-mark.spec.ts:29,52` — fix `[data-testid="lesson-card"]` + `[data-testid="group-attendance-count"]` (либо semantic, либо template testid)
- [ ] `tests/e2e/specs/student-excuse.spec.ts:34,56` — fix `[data-testid="lesson-picker-item"]` + `[data-testid="excuse-card"]`
- [ ] `tests/e2e/specs/admin-create-user.spec.ts:49` — fix `[data-testid="initial-password-display"]`
- [ ] Если выбран путь template-testid: соответствующие изменения в `frontends/web-panel/**/*.html` (минимально 4-5 локаторов) + rebuild web-panel image
- [ ] Запустить `npx playwright test --grep @smoke` локально (с поднятой docker-compose инфраструктурой) → все тесты должны RUN (нет skip по timeout) и большинство pass
- [ ] Commit: `fix(e2e): false-pass tests из G26 audit — testid + routes + seed user (M14 G7)`

## Группа 8 — G26 code-review P1 (30 мин)

- [ ] `services/api-gateway/src/main/resources/application.yml:122` — вернуть `burstCapacity: 60` на `auth-login` (rollback CI workaround `600`)
- [ ] Если CI требует override — задать в `docker-compose.e2e.yml` через env (`AUTH_LOGIN_BURST_CAPACITY=600`) и Spring `${AUTH_LOGIN_BURST_CAPACITY:60}` в YAML, либо добавить `PLAYWRIGHT_WORKERS=1` в `playwright.config.ts` для CI profile
- [ ] `tests/e2e/specs/auth.spec.ts:19-38` — удалить тест `diagnostic: direct POST /api/auth/login` с `console.log` (либо переместить в отдельный `@diag` файл, отключённый в CI grep)
- [ ] `tests/e2e/fixtures/users.ts:50-55` — удалить запись `headman` из `TEST_USERS`; все callers `TEST_USERS.headman` → `TEST_USERS.student` (`grep -rn "TEST_USERS.headman" tests/e2e/` сначала)
- [ ] Запустить `npx playwright test --grep @smoke` локально → должно проходить с `burstCapacity=60`
- [ ] Commit: `fix(gateway,e2e): burstCapacity prod default + diagnostic test removal + DRY users fixture (M14 G8, G26 F01-F03)`

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
