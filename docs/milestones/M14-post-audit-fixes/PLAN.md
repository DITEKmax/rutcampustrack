# M14 — Post-Audit Fixes (4 аудита G26/G27)

**Статус:** ⏳ в работе
**Старт / финиш:** 2026-04-26 / —
**Estimate:** ~3-4 часа (один сеанс)

---

## Scope

Закрытие **только** P0/блокирующих находок из четырёх пост-M13 аудитов
(2026-04-26). Всё что не блокирует first VPS deploy и не имеет окна
эксплуатации между deploy и first user — отложено в `docs/archive/future-ideas.md`
(pre-v0.1) либо в backlog (v0.1+).

Источники:

- `docs/milestones/M13-pre-deploy-hardening/G27-cso-comprehensive-audit.md` — 17 findings (2 CRIT + 5 HIGH)
- `docs/milestones/M13-pre-deploy-hardening/G26-test-audit-findings.md` — 11 findings (4 P1)
- `docs/milestones/M13-pre-deploy-hardening/G26-code-review-after-g25.md` — 15 findings (3 P1)
- `docs/milestones/M13-pre-deploy-hardening/G27-tech-debt-audit.md` — 23 findings (P1 не блокеры → backlog)

Критерий включения в M14: либо (a) **ломает first deploy** (HIGH-05),
либо (b) **runtime guard против operator mistake** (HIGH-06), либо
(c) **trivial cost / non-trivial impact** (CRIT-01/02, HIGH-07), либо
(d) **CI gate compromised без fix** (G26 false-pass tests).

Покрывает 9 групп:

1. **CSO CRIT-01** — flip `legacy-headers-enabled` / `strip-legacy-headers` defaults на secure
2. **CSO CRIT-02** — SHA-pin `appleboy/ssh-action@v1` в `deploy.yml`
3. **CSO HIGH-05** — fix PKCS#1/PKCS#8 mismatch в `deploy.yml` JWT keys flow + first-deploy detection
4. **CSO HIGH-06** — fail-fast placeholders для critical secrets во всех services
5. **CSO HIGH-07** — bump `aiohttp` 3.10.11 → 3.13.3+ в notification-bot
6. **CSO HIGH-03/04** — SHA-pin remaining third-party + first-party actions в deploy.yml + coverage.yml
7. **G26 test-audit P1 (cat. A, B, E)** — fix false-pass Playwright tests
8. **G26 code-review P1 (F01-F03)** — `burstCapacity` prod fix + diagnostic test cleanup + DRY в `TEST_USERS`
9. **Финал** — UAT + tag `v0.0.0-alpha.16`

## Модули / изменения

### Backend config (Group 1, 4)

- `services/academic-service/academic-app/src/main/resources/application.yml` — `legacy-headers-enabled: false` default + fail-fast secrets
- `services/schedule-service/schedule-app/src/main/resources/application.yml` — same
- `services/attendance-service/attendance-app/src/main/resources/application.yml` — same
- `services/notification-service/notification-app/src/main/resources/application.yml` — same
- `services/api-gateway/src/main/resources/application.yml` — `strip-legacy-headers: true` default + `burstCapacity: 60` (rollback CI workaround)
- `services/auth-service/auth-app/src/main/resources/application.yml` — fail-fast: `INTERNAL_ISSUER_SECRET`, `REDIS_PASSWORD`, `SPRING_RABBITMQ_PASSWORD`, `POSTGRES_*_PASSWORD`, `GRPC_SECRET`

### CI/CD (Group 2, 3, 6)

- `.github/workflows/deploy.yml` — SHA-pin `appleboy/ssh-action@v1` + `actions/checkout@v4` + `docker/setup-buildx-action@v3` + `docker/login-action@v3` + `docker/build-push-action@v7` (×11 lines)
- `.github/workflows/deploy.yml` — first-deploy detection block перед `docker compose up`: regenerate JWT keys в правильном PKCS#8 формате, kid через `openssl rand -hex 4`
- `.github/workflows/coverage.yml` — SHA-pin `madrapps/jacoco-report@v1.7.1`, `davelosert/vitest-coverage-report-action@v2`, `MishaKav/pytest-coverage-comment@v1.1.52`, `marocchino/sticky-pull-request-comment@v2`; перенести `pull-requests:write`/`checks:write` из top-level в per-job
- `.env.prod.example` — комментарий «strict-mode invariant: НЕ переопределять `RUTCAMPUSTRACK_SECURITY_LEGACY_HEADERS_ENABLED` или `GATEWAY_STRIP_LEGACY_HEADERS`»

### Supply chain (Group 5)

- `services/notification-bot/requirements.txt` — `aiohttp>=3.13.3` (было 3.10.11)
- Rebuild bot image после bump'а

### E2E tests (Group 7)

- `tests/e2e/specs/role-student.spec.ts` — либо удалить тесты «cannot access /headman/*» / «/admin/*», либо ввести seed user `student_plain` с `is_headman=false` (выбор в NOTES)
- `tests/e2e/specs/role-teacher.spec.ts` — убрать `/teacher/schedule` из `paths` array (роут не существует)
- `tests/e2e/specs/role-teacher.spec.ts` — заменить `[data-testid="red-zone-badge"]` на семантический локатор (ARIA role + текст), либо добавить testid в шаблон
- `tests/e2e/specs/headman-mark.spec.ts` — fix `[data-testid="lesson-card"]` + `[data-testid="group-attendance-count"]`
- `tests/e2e/specs/student-excuse.spec.ts` — fix `[data-testid="lesson-picker-item"]` + `[data-testid="excuse-card"]`
- `tests/e2e/specs/admin-create-user.spec.ts` — fix `[data-testid="initial-password-display"]`
- `frontends/web-panel/src/app/features/**/*.html` — добавить недостающие `data-testid` (выбранный путь решается в NOTES)

### Code review P1 (Group 8)

- `services/api-gateway/src/main/resources/application.yml` — вернуть `burstCapacity: 60` на `auth-login` (purge CI workaround `600`)
- `tests/e2e/specs/auth.spec.ts` — удалить или перенести в `@diag` тест `diagnostic: direct POST /api/auth/login` с `console.log`
- `tests/e2e/fixtures/users.ts` — удалить дублирующую запись `headman` (идентична `student`); все callers `TEST_USERS.headman` → `TEST_USERS.student`

## Acceptance criteria

- [ ] **CRIT-01:** `curl` с `X-User-Id: 1, X-User-Role: ADMIN` напрямую в downstream container (минуя gateway) возвращает **401**, не 200. Проверено вручную в local docker-compose против всех 4 backend.
- [ ] **CRIT-02:** `grep -E "uses: .+@v\d+(\.\d+)?$" .github/workflows/deploy.yml` → пусто (все actions SHA-pinned).
- [ ] **HIGH-05:** Локальный test первого deploy: `docker volume rm rutcampustrack_jwt-keys && bash <SSH_script_first_deploy_block>` → keys в PKCS#8, auth-service стартует без exception в логах.
- [ ] **HIGH-06:** `docker compose up` БЕЗ `--env-file` → auth-service не стартует, в логе явный `INTERNAL_ISSUER_SECRET must be set`. Аналогично для остальных secrets.
- [ ] **HIGH-07:** `pip show aiohttp` в bot image возвращает `>= 3.13.3`. Bot startup logs без deprecation warnings от aiogram.
- [ ] **G26-tests:** `npx playwright test --grep @smoke` → все тесты RUN (нет SKIPPED по timeout на missing testid). `role-student` тесты или удалены, или используют correct seed user.
- [ ] **G26-code F01:** `application.yml` rate-limit на `/auth/login` → `burstCapacity=60`. Локальный CI пайплайн проходит (доказательство что 60 достаточно для PLAYWRIGHT_WORKERS=1 либо CI override env present).
- [ ] **All:** `./gradlew build` зелёный + `npx playwright test --grep @smoke` зелёный + `pytest services/notification-bot/tests` зелёный.
- [ ] Тег `v0.0.0-alpha.16` push'нут.

## Dependencies

- **Блокирует:** first VPS deploy v0.0.0 GA (без M14 — IDOR risk + first deploy упадёт на PKCS#1).
- **Блокируется:** M13 ✅ (tag `v0.0.0-alpha.15` уже стоит).
- **Parallel safe:** —

## Artifacts

- `.env.prod.example` — комментарий о strict-mode invariant.
- `docs/archive/future-ideas.md` — секция «Pre-v0.1 (post-M14)» с 8 MED + 2 TENT из CSO + P2 cleanup из G26.
- `docs/archive/deferred-ideas.md` — секция «v0.1+ tech debt» с 23 пунктами из G27 tech-debt audit.
- `CHANGELOG.md` — entry `[v0.0.0-alpha.16]` с CRIT/HIGH fixes.

---

_Не входит в M14 (отложено):_

- **G27-cso MED-08, MED-09, MED-10, MED-11, MED-12, MED-13, MED-14, MED-15, TENT-16, TENT-17** → `docs/archive/future-ideas.md` (pre-v0.1, post-deploy week 1-2).
- **G26 P2/P3 cleanup** (12 находок) → один cleanup PR после first deploy.
- **G27 tech-debt P1-P3** (23 находки) → `docs/archive/deferred-ideas.md` для v0.1+. F04/F05 — gate'нуты на real-user signal (Grafana latency / horizontal scale решение).
