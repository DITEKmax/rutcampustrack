# Промпт для следующей сессии — M14 G4: fail-fast secrets во всех services (CSO HIGH-06)

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам откроет
нужные файлы и продолжит.

---

**M14 G3 закрыт (commit `7e69067` + docs followup в той же сессии).
Сегодня (2026-04-26) сделаны три группы подряд: G1 (legacy headers),
G2 (SHA-pin appleboy/ssh-action), G3 (PKCS#8 в deploy.yml). M14 пока
НЕ push'нут на origin/dev — семь локальных коммитов на `dev`,
upstream не получил.**

## Контекст M14 (читай это первым)

M14 = «Post-Audit Fixes» — закрытие блокеров first VPS deploy v0.0.0
из четырёх аудитов:
- `docs/milestones/M13-pre-deploy-hardening/G27-cso-comprehensive-audit.md` — 17 findings (2 CRIT + 5 HIGH + 8 MED + 2 TENT)
- `docs/milestones/M13-pre-deploy-hardening/G26-test-audit-findings.md` — 11 (4 P1)
- `docs/milestones/M13-pre-deploy-hardening/G26-code-review-after-g25.md` — 15 (3 P1)
- `docs/milestones/M13-pre-deploy-hardening/G27-tech-debt-audit.md` — 23 (отложено в `docs/deferred-ideas.md`)

**Триаж принцип:** только (a) ломает first deploy, (b) runtime guard от
operator mistake, (c) trivial cost / non-trivial impact, (d) CI gate
compromised без fix → попадает в M14. Остальное — `docs/future-ideas.md`
(pre-v0.1) или `docs/deferred-ideas.md` (v0.1+).

Полный план: `docs/milestones/M14-post-audit-fixes/PLAN.md`.
Чеклист: `docs/milestones/M14-post-audit-fixes/CHECKLIST.md`.
Заметки: `docs/milestones/M14-post-audit-fixes/NOTES.md`.

## Что уже сделано (G1-G3, 2026-04-26)

| Коммит | Что | Verification |
|--------|-----|--------------|
| `455029f` | M14 setup (PLAN/CHECKLIST/NOTES + 4 audit reports + future/deferred-ideas) | docs only |
| `dc40929` | **G1: legacy headers strict by default (CSO CRIT-01)** — 5 application.yml flips + .env.prod.example invariant block + notification SecurityIdorIT inline-property | 4× SecurityIdorIT + 3× *UserContextFilterStrictModeIT BUILD SUCCESSFUL (5m33s) |
| `5a1b175` | G1 docs followup: CHECKLIST tick + NOTES surprise (application-test.yml asymmetry) | docs only |
| `a93859b` | **G2: SHA-pin appleboy/ssh-action (CSO CRIT-02)** — `appleboy/ssh-action@0ff4204... # v1.2.5` в deploy.yml:313 | grep ровно 1 вхождение, pyyaml safe_load OK |
| `dc602a0` | G2 docs followup: CHECKLIST tick + NOTES surprise (версия v1.2.0→v1.2.5) + NEXT-SESSION rotate | docs only |
| `7e69067` | **G3: PKCS#8 + idempotent JWT key gen (CSO HIGH-05)** — explicit `genrsa → pkcs8 -topk8 -nocrypt → rsa -pubout` + atomic idempotency guard + kid.txt + chmod 600 в deploy.yml:329-360 | dry-run head -1 = "BEGIN PRIVATE KEY" + md5sum identical на повторе + JDK 21 standalone clone парсит без exception + negative test (PKCS#1 → InvalidKeySpecException) |
| `<тбd>` | G3 docs followup (CHECKLIST + NOTES surprises + NEXT-SESSION на G4) — закоммичен вместе с G3 functional | docs only |

**G3 surprises (зафиксировано в NOTES):**
1. **OpenSSL 3.x уже выдаёт PKCS#8 by default** — `bare openssl genrsa`
   на `alpine:latest` (OpenSSL 3.5.6) → `BEGIN PRIVATE KEY`. На
   `alpine:3.13` (OpenSSL 1.1.x) → `BEGIN RSA PRIVATE KEY`. Поведение
   изменилось в OpenSSL 3.0. CSO HIGH-05 finding концептуально
   правильный, но **на современном deploy не воспроизводится** —
   implicit зависит от alpine major. Fix всё равно нужен: explicit
   `pkcs8 -topk8` снимает implicit dependency.
2. **Race-edge в старой idempotency guard** — `[ -f X ] || gen X`
   chained раздельно для priv/pub имел edge: corrupt file → не регенит.
   Новый `if [ ! -f priv ]; then gen-all` атомарен.
3. **Git Bash MSYS path conversion** ломает docker absolute paths;
   workaround: `MSYS_NO_PATHCONV=1` или PowerShell tool.
4. **Standalone JDK clone** для verification — приём для будущих
   GSD audits: 40 строк Java, парсят ключ через те же KeyFactory API
   что production. Без необходимости поднимать compose.

## Что делать в этой сессии

### Шаг 0 — verify state

```bash
cd C:/Users/maksd/IntelliJIDEA/rutcampustrack
git log --oneline -10
git status --short
```

**Ожидаем:**
- HEAD = `<docs followup G3>` или `7e69067` (если followup ещё не отдельным коммитом)
- Working tree clean (или максимум `?? .gstack/`)
- 7 локальных коммитов M14 ещё не на origin

Если состояние другое — проверь не было ли push'а вручную и не работает
ли кто-то ещё параллельно.

### Шаг 1 — выполнить Группу 4 (fail-fast secrets во всех services)

Из `CHECKLIST.md`:

> ## Группа 4 — CSO HIGH-06: fail-fast secrets во всех services (20 мин)
>
> - [ ] auth-service application.yml — fail-fast: INTERNAL_ISSUER_SECRET, REDIS_PASSWORD, SPRING_RABBITMQ_PASSWORD, POSTGRES_AUTH_PASSWORD
> - [ ] academic-service — POSTGRES_ACADEMIC_PASSWORD, REDIS_PASSWORD, SPRING_RABBITMQ_PASSWORD, GRPC_SECRET
> - [ ] schedule-service — POSTGRES_SCHEDULE_PASSWORD, SPRING_RABBITMQ_PASSWORD, GRPC_SECRET
> - [ ] attendance-service — MONGO_PASSWORD, SPRING_RABBITMQ_PASSWORD, GRPC_SECRET
> - [ ] notification-service — MONGO_PASSWORD, SPRING_RABBITMQ_PASSWORD, ALERT_WEBHOOK_SECRET
> - [ ] api-gateway — fail-fast где есть dev fallback'и
> - [ ] Local UAT: `docker compose up -d` БЕЗ `--env-file` → ожидаем что auth-service exit'нется с явной ошибкой `INTERNAL_ISSUER_SECRET must be set`
> - [ ] Запустить `./gradlew :services:auth-service:auth-app:test` — проверить, что test-profile не падает
> - [ ] Commit: `fix(security): fail-fast на critical secrets во всех services (M14 G4, CSO HIGH-06)`

**Контекст из CSO audit (`G27-cso-comprehensive-audit.md` § HIGH-06):**

Spring Boot syntax:
- **dev fallback:** `${MY_VAR:dev-default}` — если MY_VAR не задан, используется `dev-default`. ОПАСНО для prod: deploy без `--env-file` поднимется на dev secrets, JWT signed предсказуемым ключом, downstream services accept'ят токены.
- **fail-fast:** `${MY_VAR:?MY_VAR must be set in environment}` — если MY_VAR не задан, Spring Boot **падает на старте** с явной ошибкой. Operator получает immediate signal что забыл env-file.

**Где искать dev fallback'и:** грепнуть `:?[^}]*` (NEGATIVE pattern — ищем
where placeholder есть `:` без `?`):

```bash
grep -rn '\${[A-Z_]\+:[^?][^}]*}' services/*/src/main/resources/application.yml
```

Это покажет все `${VAR:default}` без `?`. Не все из них — secrets:
например, `${SERVER_PORT:8080}` это OK (port не секрет). Сосредоточиться
на:
- *PASSWORD, *SECRET, *TOKEN, *KEY (по naming convention из M03a/M06)
- INTERNAL_ISSUER_SECRET, GRPC_SECRET, ALERT_WEBHOOK_SECRET

**ВАЖНО — test profile не должен падать:** `application-test.yml` в каждом
сервисе уже задаёт test values (или имеет explicit overrides). Если
fail-fast в `application.yml` препятствует загрузке test profile, нужно
либо добавить test value в `application-test.yml`, либо сделать
test-specific bean override. Запустить `./gradlew :services:auth-service:auth-app:test`
после изменений — проверить.

**Local UAT (mandatory):**

```bash
# Без env-file (negative case): должен упасть с явной ошибкой
docker compose -f docker-compose.prod.yml up auth-service 2>&1 | head -30
# Ожидаем: "INTERNAL_ISSUER_SECRET must be set in environment" (или похожее)
# НЕ должно: silent boot с dev secret

# С env-file (positive case): должен подняться
docker compose -f docker-compose.prod.yml --env-file .env.prod.example up -d auth-service
docker logs rct-auth-service | tail -20
# Ожидаем: standard startup logs, healthcheck green
docker compose -f docker-compose.prod.yml down
```

Если `.env.prod.example` не имеет всех нужных values — добавить их там
(test placeholders OK, главное чтоб env-file был complete).

### Шаг 2 — commit + переход к G5

Commit message:
```
fix(security): fail-fast на critical secrets во всех services (M14 G4, CSO HIGH-06)

CSO comprehensive audit (G27) обнаружил HIGH-06: 6 services имели dev
fallback'и в форме `${SECRET:dev-...}` — deploy без `--env-file` поднимется
с предсказуемыми credentials, JWT signed dev-key, downstream accept'ит.
Operator не получает immediate signal что забыл env.

Fix: replace `${SECRET:dev-default}` → `${SECRET:?SECRET must be set in environment}`
в:
- auth-service: INTERNAL_ISSUER_SECRET, REDIS_PASSWORD, SPRING_RABBITMQ_PASSWORD, POSTGRES_AUTH_PASSWORD
- academic-service: POSTGRES_ACADEMIC_PASSWORD, REDIS_PASSWORD, SPRING_RABBITMQ_PASSWORD, GRPC_SECRET
- schedule-service: POSTGRES_SCHEDULE_PASSWORD, SPRING_RABBITMQ_PASSWORD, GRPC_SECRET
- attendance-service: MONGO_PASSWORD, SPRING_RABBITMQ_PASSWORD, GRPC_SECRET
- notification-service: MONGO_PASSWORD, SPRING_RABBITMQ_PASSWORD, ALERT_WEBHOOK_SECRET
- api-gateway: <актуальный список>

Test profile не затронут — `application-test.yml` имеет explicit test
values для всех this secrets (verified: ./gradlew :auth-app:test green).

Local UAT: `docker compose -f docker-compose.prod.yml up auth-service`
БЕЗ env-file → "INTERNAL_ISSUER_SECRET must be set" (immediate fail).
С `--env-file .env.prod.example` → green startup.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```

После commit'а — обнови CHECKLIST.md (G4 [x] + commit hash + UAT
results) одним followup-commit'ом. Затем **не двигайся к G5 без явного
go от пользователя**.

### Если G4 завершён и есть оставшееся время — спроси про G5

G5 = «aiohttp 3.10.11 → 3.13.3+ bump в notification-bot» (~5 мин).
Самая короткая в M14, требует только pip resolve + pytest. Если
пользователь говорит «go g5» сразу после G4 — нормально продолжить.

## Полный список M14 групп (для context)

Из `CHECKLIST.md`:

1. ✅ **G1** — legacy headers strict default (CSO CRIT-01) — done `dc40929`
2. ✅ **G2** — SHA-pin appleboy/ssh-action (CSO CRIT-02) — done `a93859b`
3. ✅ **G3** — PKCS#8 + idempotent JWT key gen (CSO HIGH-05) — done `7e69067`
4. **G4** — fail-fast secrets во всех services (CSO HIGH-06) — **СЛЕДУЮЩАЯ**
5. **G5** — aiohttp 3.10.11 → 3.13.3+ bump (CSO HIGH-07)
6. **G6** — SHA-pin remaining actions в deploy/coverage/security (CSO HIGH-03/04 + MED-09)
7. **G7** — G26 test-audit P1 (false-pass Playwright tests) — самая длинная (~1-1.5ч)
8. **G8** — G26 code-review P1 (burstCapacity 600→60 + diagnostic test + DRY)
9. **G9** — UAT + tag `v0.0.0-alpha.16`

Каждая группа = отдельный commit (или пара: functional + docs
followup). Порядок: G1→G9 sequentially. **НЕ запускать в параллель** —
нет независимости между группами.

## Local state на момент hand-off (2026-04-26 evening)

```
Working tree: clean (только ?? .gstack/ — security report, gitignored)
Branch: dev (7 коммитов впереди origin/dev)
Last commit: <docs followup G3 — обновится в реальности>

Локальные коммиты M14 (НЕ push'нуты):
  <docs followup G3>  docs(M14): G3 done — PKCS#8 + idempotent JWT key gen
  7e69067             fix(ci): PKCS#8 + idempotent JWT key gen в deploy.yml (M14 G3, CSO HIGH-05)
  dc602a0             docs(M14): отметить G2 done + rotate hand-off на G3
  a93859b             fix(ci): SHA-pin appleboy/ssh-action against supply chain (M14 G2, CSO CRIT-02)
  5a1b175             docs(M14): отметить G1 done + зафиксировать application-test.yml asymmetry
  dc40929             fix(security): legacy headers strict by default (M14 G1, CSO CRIT-01)
  455029f             docs(M14): план + триаж 4 пост-M13 аудитов (M14 setup)
```

Push на origin/dev пока НЕ делать — пользователь решает когда (либо после
G9 + tag, либо если хочет промежуточный CI run для верификации). Если
пользователь скажет push — push без force, обычный `git push origin dev`.

## Pending decisions для new conversation

1. **api-gateway scope.** Грепнуть `services/api-gateway/.../application.yml`
   на dev fallback'и до начала G4 — может оказаться что там нечего fail-fast'ить
   (gateway forward'ит запросы и не имеет database/secret bind'ов кроме
   Internal JWT validation которая уже covered downstream).
2. **`.env.prod.example` completeness.** Если fail-fast выявит missing
   var в example — добавить test placeholder (всё равно файл commit'ится
   в repo, не реальный secret).
3. **Push на origin/dev.** По дефолту НЕ пушим. Жди явного указания.

## История milestone'ов (архив)

- M01-M08 ✅ (`v0.0.0-alpha.1..alpha.9`)
- M09-M12 ✅ 2026-04-24 (`alpha.10..alpha.13`)
- M13 Pre-Deploy Hardening ✅ 2026-04-25 (`v0.0.0-alpha.15` после G25 hot-fix marathon)
- **→ M14 Post-Audit Fixes** (текущий) — G1 ✅, G2 ✅, G3 ✅, G4-G9 pending. Tag `v0.0.0-alpha.16` после G9.

Roadmap: `docs/milestones/README.md`.
Aудиторские отчёты-источники: `docs/milestones/M13-pre-deploy-hardening/G2{6,7}-*.md`.
Trail отложенного: `docs/future-ideas.md` § Pre-v0.1 + `docs/deferred-ideas.md` § v0.1+ tech debt.
