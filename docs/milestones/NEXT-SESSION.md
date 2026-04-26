# Промпт для следующей сессии — M14 G3: PKCS#8 + first-deploy detection в deploy.yml (CSO HIGH-05)

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам откроет
нужные файлы и продолжит.

---

**M14 G2 закрыт (commit `a93859b` + docs followup в той же сессии).
Сегодня (2026-04-26) после G1 утром сделана G2 — SHA-pin
`appleboy/ssh-action@0ff4204d... # v1.2.5`. Работа над M14 продолжается
с G3. M14 пока НЕ push'нут на origin/dev — пять локальных коммитов
(`455029f`, `dc40929`, `5a1b175`, `a93859b`, plus docs followup G2)
лежат на ветке `dev`, upstream не получил.**

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

## Что уже сделано (G1-G2, 2026-04-26)

| Коммит | Что | Verification |
|--------|-----|--------------|
| `455029f` | M14 setup (PLAN/CHECKLIST/NOTES + 4 audit reports + future/deferred-ideas) | docs only |
| `dc40929` | **G1: legacy headers strict by default (CSO CRIT-01)** — 5 application.yml flips + .env.prod.example invariant block + notification SecurityIdorIT inline-property | 4× SecurityIdorIT + 3× *UserContextFilterStrictModeIT BUILD SUCCESSFUL (5m33s) |
| `5a1b175` | G1 docs followup: CHECKLIST tick + NOTES surprise (application-test.yml asymmetry) | docs only |
| `a93859b` | **G2: SHA-pin appleboy/ssh-action (CSO CRIT-02)** — `appleboy/ssh-action@0ff4204d59e8e51228ff73bce53f80d53301dee2 # v1.2.5` в deploy.yml:313 | grep ровно 1 вхождение, pyyaml safe_load OK |
| `<tbd>` | G2 docs followup (CHECKLIST tick + NOTES surprise о версии) — закоммичен в той же сессии что G2 functional | docs only |

**G2 surprise (зафиксировано в NOTES):** hand-off зафиксировал target
`v1.2.0`, pre-flight `curl /releases/latest` показал `v1.2.5` — между
M14 PLAN'ом и его выполнением maintainer выпустил три patch-релиза.
Использована актуальная `v1.2.5` чтобы Renovate не bump'нул сразу
после merge'а. Tag оказался lightweight (ref → commit напрямую), без
дополнительного шага дереференса через `/git/tags/`. `gh` CLI отсутствовал
в bash PATH — использован `curl https://api.github.com/...`.

## Что делать в этой сессии

### Шаг 0 — verify state

```bash
cd C:/Users/maksd/IntelliJIDEA/rutcampustrack
git log --oneline -7
git status --short
```

**Ожидаем:**
- HEAD = `<docs followup G2>` или `a93859b` (если followup ещё не отдельным коммитом)
- Working tree clean (или максимум `?? .gstack/`)
- 5 локальных коммитов M14 ещё не на origin

Если состояние другое — проверь не было ли push'а вручную и не работает
ли кто-то ещё параллельно.

### Шаг 1 — выполнить Группу 3 (PKCS#8 + first-deploy detection)

⚠️ **Это самая сложная и рискованная группа в M14** — требует local
dry-run против пустого docker volume. Если до конца сессии меньше
~45 минут — лучше **не начинать G3** и подождать отдельного сеанса.

Из `CHECKLIST.md`:

> ## Группа 3 — CSO HIGH-05: PKCS#8 fix в deploy.yml + first-deploy detection (30 мин)
>
> - [ ] Прочитать `services/auth-service/Dockerfile:69-79` (M13 G25.15 reference flow) — скопировать correct openssl pipeline
> - [ ] `.github/workflows/deploy.yml` — найти block с `openssl genrsa` и заменить на pipeline `genrsa -> pkcs8 -topk8 -nocrypt -> rsa -pubout` (см. PLAN.md HIGH-05 fix variant B)
> - [ ] Добавить first-deploy detection: `if [ ! -f /opt/rutcampustrack/.deployed-sha ]; then ... fi` block, который удаляет volume и pre-fills уникальные keys
> - [ ] kid через `openssl rand -hex 4 > /keys/kid.txt` (вместо `head -c 4 | od | tr` pipeline)
> - [ ] chmod / chown matching Dockerfile (`chown -R 100:101 /keys && chmod 600 /keys/private.key /keys/kid.txt && chmod 644 /keys/public.key`)
> - [ ] Local dry-run на чистом docker volume (см. шаг ниже)
> - [ ] Поднять auth-service против этого volume локально, проверить отсутствие exception в логах при `JwtService.init()`
> - [ ] Commit: `fix(ci): PKCS#8 + first-deploy regen в deploy.yml (M14 G3, CSO HIGH-05)`

**Контекст из CSO audit (`G27-cso-comprehensive-audit.md` § HIGH-05):**

`.github/workflows/deploy.yml` (block после `appleboy/ssh-action`)
содержит inline bash для регенерации JWT keys на VPS, который генерирует
ключ в **PKCS#1** формате (`openssl genrsa -out /keys/private.key 3072`)
без последующего конвертации в PKCS#8. `JwtService` ожидает
`-----BEGIN PRIVATE KEY-----` (PKCS#8), а получит `-----BEGIN RSA PRIVATE KEY-----`
(PKCS#1) → exception на boot → first deploy упадёт.

Reference correct flow в `services/auth-service/Dockerfile:69-79` (был
исправлен в M13 G25.15) — три-шаговый pipeline:
```
openssl genrsa -out /tmp/private-pkcs1.pem 3072
openssl pkcs8 -topk8 -nocrypt -in /tmp/private-pkcs1.pem -out /keys/private.key
openssl rsa -in /keys/private.key -pubout -out /keys/public.key
rm /tmp/private-pkcs1.pem
```

**First-deploy detection** нужен потому что named volume
`rutcampustrack_jwt-keys` сохраняется между deploys — повторная
регенерация на каждом deploy перезатрёт ключи и invalidate'ит все
issued JWT (юзеры разлогинятся при каждом deploy). Решение — генерация
**только если** `.deployed-sha` маркер отсутствует:

```bash
if [ ! -f /opt/rutcampustrack/.deployed-sha ]; then
  echo "First deploy detected — regenerating JWT keys"
  docker volume rm rutcampustrack_jwt-keys 2>/dev/null || true
  docker volume create rutcampustrack_jwt-keys >/dev/null
  docker run --rm -v rutcampustrack_jwt-keys:/keys alpine/openssl:latest sh -c '
    set -e
    apk add --no-cache openssl >/dev/null 2>&1 || true
    openssl genrsa -out /tmp/pk1.pem 3072
    openssl pkcs8 -topk8 -nocrypt -in /tmp/pk1.pem -out /keys/private.key
    openssl rsa -in /keys/private.key -pubout -out /keys/public.key
    openssl rand -hex 4 > /keys/kid.txt
    chown -R 100:101 /keys
    chmod 600 /keys/private.key /keys/kid.txt
    chmod 644 /keys/public.key
    rm /tmp/pk1.pem
  '
  echo "${IMAGE_TAG}" > /opt/rutcampustrack/.deployed-sha
fi
```

(Точную форму смотреть в `PLAN.md` HIGH-05 fix variant B — там может
быть детали shell escape'инга для GitHub Actions YAML.)

⚠️ **alpine/openssl image vs alpine + apk add**: проверь что image
`alpine/openssl` существует и доступен на ghcr/dockerhub. Если нет —
`alpine:latest` + `apk add openssl` будет работать, но добавит
~15-20s к first deploy. Renovate должен успеть auto-bump.

### Шаг 2 — Local dry-run (mandatory)

Это **обязательно** перед commit'ом — manual проверка что openssl
pipeline даёт PKCS#8:

```bash
# clean test volume
docker volume rm test-jwt-keys 2>/dev/null || true
docker volume create test-jwt-keys

# inline проверочный скрипт (тот же pipeline что войдёт в deploy.yml)
docker run --rm -v test-jwt-keys:/keys alpine:latest sh -c '
  apk add --no-cache openssl >/dev/null 2>&1
  openssl genrsa -out /tmp/pk1.pem 3072
  openssl pkcs8 -topk8 -nocrypt -in /tmp/pk1.pem -out /keys/private.key
  openssl rsa -in /keys/private.key -pubout -out /keys/public.key
  openssl rand -hex 4 > /keys/kid.txt
  rm /tmp/pk1.pem
'

# verification
docker run --rm -v test-jwt-keys:/keys alpine:latest head -1 /keys/private.key
# должно быть: -----BEGIN PRIVATE KEY-----
# НЕ: -----BEGIN RSA PRIVATE KEY-----

docker run --rm -v test-jwt-keys:/keys alpine:latest cat /keys/kid.txt
# должно быть: <8 hex символов>

docker volume rm test-jwt-keys
```

Если `-----BEGIN RSA PRIVATE KEY-----` — pipeline сломан, не коммить
до fix'а.

### Шаг 3 — auth-service local boot test

После того как pipeline даёт правильный PKCS#8, надо проверить что
JwtService действительно стартует против этого volume:

```bash
docker volume rm rutcampustrack_jwt-keys 2>/dev/null
docker volume create rutcampustrack_jwt-keys
# повторить openssl pipeline в этот volume

# поднять auth-service в isolation (нужен redis минимум)
docker compose up -d redis-auth
docker compose up auth-service
# в логах ожидаем: "RSA key pair loaded from /keys" (или эквивалент)
# НЕ должно быть: "Failed to parse private key" / "InvalidKeySpecException"
```

Если падает — diagnose, fix, repeat. Не коммить полу-рабочий вариант.

### Шаг 4 — commit

Commit message:
```
fix(ci): PKCS#8 + first-deploy regen в deploy.yml (M14 G3, CSO HIGH-05)

CSO comprehensive audit (G27) обнаружил HIGH-05: deploy.yml inline
bash блок генерировал JWT private key в PKCS#1 формате (`openssl genrsa`
без последующего pkcs8 -topk8), а JwtService ожидает PKCS#8 → first
deploy упал бы с InvalidKeySpecException.

Fix:
1. Three-step pipeline matching auth-service Dockerfile:69-79 (M13 G25.15
   reference): genrsa → pkcs8 -topk8 -nocrypt → rsa -pubout
2. First-deploy detection через .deployed-sha marker — keys генерируются
   ТОЛЬКО при первом deploy, чтобы повторные deploy не invalidate'или
   все issued JWT
3. kid через `openssl rand -hex 4` (вместо хрупкого `head -c 4 /dev/urandom | od | tr`)
4. chmod / chown matching auth-app uid/gid (100:101)

Local dry-run: openssl pipeline → /keys/private.key начинается с
"-----BEGIN PRIVATE KEY-----" (PKCS#8). Auth-service против этого
volume стартует без exception в JwtService.init().

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```

После commit'а — обнови CHECKLIST.md (G3 [x] + commit hash + dry-run
results) одним followup-commit'ом. Затем **не двигайся к G4 без явного
go от пользователя**.

### Если G3 завершён и есть оставшееся время — спроси про G4

G4 = «CSO HIGH-06: fail-fast secrets во всех services» (~20 мин).
Менее рискованная чем G3, но требует UAT (запуск compose без env-file
чтобы убедиться что fail-fast действительно срабатывает). Может занять
больше 20 мин если что-то flaky на Spring Boot side.

## Полный список M14 групп (для context)

Из `CHECKLIST.md`:

1. ✅ **G1** — legacy headers strict default (CSO CRIT-01) — done `dc40929`
2. ✅ **G2** — SHA-pin appleboy/ssh-action (CSO CRIT-02) — done `a93859b`
3. **G3** — PKCS#8 + first-deploy detection в deploy.yml (CSO HIGH-05) — **СЛЕДУЮЩАЯ**, самая сложная
4. **G4** — fail-fast secrets во всех services (CSO HIGH-06)
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
Branch: dev (5 коммитов впереди origin/dev)
Last commit: <docs followup G2 — обновится в реальности>

Локальные коммиты M14 (НЕ push'нуты):
  <docs followup G2>  docs(M14): G2 done — SHA-pin appleboy/ssh-action
  a93859b             fix(ci): SHA-pin appleboy/ssh-action against supply chain (M14 G2, CSO CRIT-02)
  5a1b175             docs(M14): отметить G1 done + зафиксировать application-test.yml asymmetry
  dc40929             fix(security): legacy headers strict by default (M14 G1, CSO CRIT-01)
  455039f             docs(M14): план + триаж 4 пост-M13 аудитов (M14 setup)
```

Push на origin/dev пока НЕ делать — пользователь решает когда (либо после
G9 + tag, либо если хочет промежуточный CI run для верификации). Если
пользователь скажет push — push без force, обычный `git push origin dev`.

## Pending decisions для new conversation

1. **Image для openssl pipeline.** `alpine/openssl` если существует —
   читабельнее. Если нет — `alpine:latest` + `apk add openssl`
   (проверенный pattern, +15-20s к first deploy).
2. **Push на origin/dev.** По дефолту НЕ пушим. Жди явного указания
   пользователя.
3. **Скорость прохода.** Если пользователь говорит «go g3», после
   успешного closure G3 спроси «G4 продолжать или пауза?» — G3 уже
   завершён, и психологически логично сделать перерыв перед другой
   группой.
4. **Local dry-run требует Docker Desktop running.** Если пользователь
   говорит что Docker не запущен — попроси его поднять Docker Desktop
   ДО начала G3 (либо отложить G3 на сеанс когда Docker доступен).

## История milestone'ов (архив)

- M01-M08 ✅ (`v0.0.0-alpha.1..alpha.9`)
- M09-M12 ✅ 2026-04-24 (`alpha.10..alpha.13`)
- M13 Pre-Deploy Hardening ✅ 2026-04-25 (`v0.0.0-alpha.15` после G25 hot-fix marathon)
- **→ M14 Post-Audit Fixes** (текущий) — G1 ✅, G2 ✅, G3-G9 pending. Tag `v0.0.0-alpha.16` после G9.

Roadmap: `docs/milestones/README.md`.
Aудиторские отчёты-источники: `docs/milestones/M13-pre-deploy-hardening/G2{6,7}-*.md`.
Trail отложенного: `docs/future-ideas.md` § Pre-v0.1 + `docs/deferred-ideas.md` § v0.1+ tech debt.
