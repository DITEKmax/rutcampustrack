# Промпт для следующей сессии — M14 G9: UAT + tag `v0.0.0-alpha.16` (финальная группа M14)

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам откроет
нужные файлы и продолжит.

---

**M14 G8 закрыт (commit `c09b002`). 3 finding'а из G26 code-review P1
закрыты в одном commit'е (~30 мин). Production-safe burstCapacity=60
default + e2e env override 600. Diagnostic test removed. TEST_USERS.headman
дубликат удалён + 4 callers обновлены. M14 пока НЕ push'нут — 19
локальных коммитов на `dev`.**

## Контекст M14 (читай это первым)

M14 = «Post-Audit Fixes» — закрытие блокеров first VPS deploy v0.0.0
из четырёх аудитов. **G9 — финальная группа M14: UAT + tag.**

Полный план: `docs/milestones/M14-post-audit-fixes/PLAN.md`.
Чеклист: `docs/milestones/M14-post-audit-fixes/CHECKLIST.md`.
Заметки: `docs/milestones/M14-post-audit-fixes/NOTES.md`.

## Что уже сделано (G1-G8, 2026-04-26)

| Коммит | Что |
|--------|-----|
| `455029f` | M14 setup |
| `dc40929` + `5a1b175` | **G1**: legacy headers strict default (CSO CRIT-01) |
| `a93859b` + `dc602a0` | **G2**: SHA-pin appleboy/ssh-action (CSO CRIT-02) |
| `7e69067` + `d20616d` | **G3**: PKCS#8 + idempotent JWT key gen (CSO HIGH-05) |
| `d2daff7` | G4 v1 deferred docs (Spring Boot ограничение) |
| `bf915ec` + `44e2d7c` | **G4 v2**: RequiredSecretsValidator EnvironmentPostProcessor (CSO HIGH-06) |
| `607af81` + `56c802f` | **G5**: aiohttp 3.10.11→3.13.5 + aiogram 3.15.0→3.23.0 (CSO HIGH-07) |
| `7fbd908` + `18175fc` | **G6**: SHA-pin 16 actions × 3 workflows + permissions least-privilege (CSO HIGH-03/04 + MED-09) |
| `f24f22f` + `4b79340` | **G7**: G26 false-pass spec fixes + headman-mark.spec.ts skip + 3 testid additions |
| `11e6a13` | **G7 corrective**: headman-mark by-design out of scope (PWA owns flow), удалён v0.1 backlog |
| `c09b002` | **G8**: burstCapacity prod default + diagnostic removal + DRY users (G26 F01-F03) |
| `<tbd>` | G8 docs followup |

## Что делать в этой сессии

### Шаг 0 — verify state

```bash
cd C:/Users/maksd/IntelliJIDEA/rutcampustrack
git log --oneline -22
git status --short
```

**Ожидаем:**
- HEAD = `<G8 docs commit>` или `c09b002`
- Working tree clean (или максимум `?? .gstack/`, `?? tests/e2e/test-results/`, `?? tests/e2e/fixtures/test-excuse.pdf`)
- 19-20 локальных коммитов M14 ещё не на origin

### Шаг 1 — выполнить Группу 9 (UAT + tag) — финальная M14

⚠️ **Длинная группа** — ~30-60 мин (зависит от скорости local builds).

Из `CHECKLIST.md` (G9):

> ## Группа 9 — UAT + tag (30 мин)
>
> - [ ] Полный local pipeline: `./gradlew build` → ожидаем зелёный
> - [ ] Local docker-compose smoke: `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d` → все 26 контейнеров healthy
> - [ ] Local Playwright `@smoke` → зелёный
> - [ ] Local pytest `services/notification-bot/tests` → зелёный
> - [ ] Local web-panel + pwa unit tests → зелёные (`npm run test` в каждом)
> - [ ] `scripts/preflight-deploy.sh` → зелёный
> - [ ] Push `dev` → ожидать green CI
> - [ ] Tag `v0.0.0-alpha.16` с message со списком CRIT/HIGH fixes (CRIT-01, CRIT-02, HIGH-03, HIGH-04, HIGH-05, HIGH-06, HIGH-07 + G26 P1)
> - [ ] `git push origin v0.0.0-alpha.16`
> - [ ] Обновить `CLAUDE.md` § «Текущий статус» — добавить `M14: завершён 2026-04-26`
> - [ ] Обновить `docs/milestones/README.md` — добавить строку M14 в таблицу
> - [ ] Финальный commit: `docs: M14 finalised, alpha.16 tagged (post-audit fixes)`

**Pre-flight перед началом G9:**

1. **Set JAVA_HOME** (Windows): `$env:JAVA_HOME = "C:\Users\maksd\.jdks\ms-21.0.9"` (через PowerShell tool)
2. **Verify .env.prod существует**: для local docker compose с prod profile
3. **Free port 5432, 8080, 9094** etc — проверить нет ли висящих контейнеров (`docker compose ps`)

### Шаг 2 — UAT priority order (если время ограничено)

Если 30-60 мин не хватает, минимальный set для tag'а:

1. **Critical (must)**: `./gradlew build` (~5-7 мин) + `pytest notification-bot` (~30 сек)
2. **Important**: `npm test` для PWA + web-panel (~2-3 мин каждый)
3. **Nice-to-have, можно отложить**: full docker compose smoke + Playwright @smoke
   (требует ~15-30 мин setup + run, можно делать на CI вместо local)

Если local docker compose smoke недоступен (port collision, .env.prod
missing) — **полагайся на CI** после push'а. Tag создавай только
после green CI.

### Шаг 3 — burstCapacity risk verification

После G8 production default `burstCapacity=60`. Если Playwright @smoke
падает с 429 на /auth/login → **НЕ возвращать 600 в production**.
Альтернативы:
- Снизить `workers: 1` в playwright.config.ts CI ветке
- Verify что docker-compose.e2e.yml действительно подхватывает
  `AUTH_LOGIN_BURST_CAPACITY: "600"` (debug через `docker compose
  exec api-gateway env | grep AUTH_LOGIN`)

### Шаг 4 — tag message format

Tag annotated с big message:

```
v0.0.0-alpha.16 — Post-audit fixes (M14)

Closes findings from 4 audits (2026-04-26):
- CSO comprehensive: CRIT-01, CRIT-02, HIGH-03, HIGH-04, HIGH-05, HIGH-06, HIGH-07
- CSO MED-09 (gitleaks SHA-pin)
- G26 test-audit P1 (false-pass spec fixes)
- G26 code-review P1 (burstCapacity prod default + diagnostic + DRY)

Functional changes:
- Legacy headers strict by default (4 services + gateway)
- SHA-pin 17 GitHub Actions across 3 workflows
- PKCS#8 + idempotent JWT key generation в deploy.yml
- RequiredSecretsValidator (EnvironmentPostProcessor fail-fast)
- aiohttp 3.10.11→3.13.5 + aiogram 3.15.0→3.23.0
- gateway burstCapacity: production default 60 (e2e override 600)

E2E test cleanup:
- 5 false-pass spec fixes (testid + routes)
- 1 spec skip permanent (headman bulk-mark — by design out of scope для web-panel, PWA owns flow)
- diagnostic test removed
- TEST_USERS.headman дубликат удалён

Total: 19+ commits, 8 functional groups (G1-G8) + UAT+tag (G9).
```

### Шаг 5 — финальный docs commit

После tag push'а:
- `CLAUDE.md` § «Текущий статус» → новая строка M14
- `docs/milestones/README.md` → таблица milestone'ов с M14 row
- Удалить `docs/milestones/NEXT-SESSION.md` или поставить плашку
  «M14 закрыт, следующее — M15 после first VPS deploy + observability»

## Полный список M14 групп (для context)

1. ✅ **G1** — legacy headers strict default (CSO CRIT-01) — `dc40929`
2. ✅ **G2** — SHA-pin appleboy/ssh-action (CSO CRIT-02) — `a93859b`
3. ✅ **G3** — PKCS#8 + idempotent JWT key gen (CSO HIGH-05) — `7e69067`
4. ✅ **G4 v2** — RequiredSecretsValidator (CSO HIGH-06) — `bf915ec`
5. ✅ **G5** — aiohttp + aiogram bump (CSO HIGH-07) — `607af81`
6. ✅ **G6** — SHA-pin actions deploy/coverage/security (CSO HIGH-03/04 + MED-09) — `7fbd908`
7. ✅ **G7** — G26 false-pass tests + corrective bulk-mark out-of-scope — `f24f22f` + `11e6a13`
8. ✅ **G8** — G26 code-review P1 (burstCapacity 60 + diagnostic + DRY) — `c09b002`
9. **G9** — UAT + tag `v0.0.0-alpha.16` — **СЛЕДУЮЩАЯ (финальная)**

## Local state на момент hand-off (2026-04-26 evening)

```
Working tree: clean (только ?? .gstack/, возможно ?? tests/e2e/test-results/)
Branch: dev (19-20 коммитов впереди origin/dev)

Локальные коммиты M14 (НЕ push'нуты):
  <G8 docs>     docs(M14): G8 done — burstCapacity prod default + DRY
  c09b002       fix(gateway,e2e): burstCapacity prod default + diagnostic test removal + DRY users fixture (M14 G8, G26 F01-F03)
  11e6a13       fix(e2e,docs): headman-mark.spec.ts skip — by-design out of scope для web-panel (M14 G7 corrective)
  4b79340       docs(M14): G7 done — false-pass tests + headman bulk-mark v0.1 backlog + rotate hand-off на G8
  f24f22f       fix(e2e): G26 false-pass tests — testid + routes + skip forward-written + path A для seed mismatch (M14 G7)
  18175fc       docs(M14): G6 done — SHA-pin 16 actions + permissions least-privilege + rotate hand-off на G7
  7fbd908       fix(ci): SHA-pin third-party + first-party actions в deploy/coverage/security (M14 G6, CSO HIGH-03/04 + MED-09)
  56c802f       docs(M14): G5 done — aiohttp + aiogram bump
  607af81       chore(deps): aiohttp 3.10.11→3.13.5 + aiogram 3.15.0→3.23.0 (M14 G5, CSO HIGH-07)
  44e2d7c       docs(M14): G4 v2 done — RequiredSecretsValidator
  bf915ec       fix(security): RequiredSecretsValidator (M14 G4 v2, CSO HIGH-06)
  d2daff7       docs(M14): G4 deferred [SUPERSEDED by G4 v2]
  d20616d       docs(M14): G3 done
  7e69067       fix(ci): PKCS#8 + idempotent JWT key gen (M14 G3, CSO HIGH-05)
  dc602a0       docs(M14): G2 done
  a93859b       fix(ci): SHA-pin appleboy/ssh-action (M14 G2, CSO CRIT-02)
  5a1b175       docs(M14): G1 done
  dc40929       fix(security): legacy headers strict by default (M14 G1, CSO CRIT-01)
  455029f       docs(M14): план + триаж 4 пост-M13 аудитов
```

## Pending decisions для new conversation

1. **Local docker compose smoke** — если port collision / .env.prod
   missing → положись на CI после push, не блокируй tag.
2. **`burstCapacity=60` risk** — если Playwright @smoke падает с 429
   на /auth/login → **НЕ rollback на 600 в production**, debug через
   снижение workers либо verify e2e env override.
3. **Push timing** — push после `<G8 docs commit>`, ожидание green CI,
   затем tag. Не tag'ать до green CI.

## История milestone'ов (архив)

- M01-M08 ✅ (`v0.0.0-alpha.1..alpha.9`)
- M09-M12 ✅ 2026-04-24 (`alpha.10..alpha.13`)
- M13 Pre-Deploy Hardening ✅ 2026-04-25 (`v0.0.0-alpha.15`)
- **→ M14 Post-Audit Fixes** (текущий) — G1-G8 ✅, G9 pending. Tag `v0.0.0-alpha.16` после G9.

После закрытия M14 — first VPS deploy v0.0.0. Затем M15 «Post-Deploy
Cleanup» при необходимости (Pre-v0.1 sweep из `docs/future-ideas.md`).

Roadmap: `docs/milestones/README.md`.
Aудиторские отчёты: `docs/milestones/M13-pre-deploy-hardening/G2{6,7}-*.md`.
