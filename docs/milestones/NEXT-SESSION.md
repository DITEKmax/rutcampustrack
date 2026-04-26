# Промпт для следующей сессии — M14 G8: G26 code-review P1 (burstCapacity + diagnostic + DRY)

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам откроет
нужные файлы и продолжит.

---

**M14 G7 закрыт (commit `f24f22f`). 7 false-pass spec'ов починены, 1 spec
помечен `test.describe.skip` с переносом в v0.1 backlog (headman bulk-mark
UI не реализован, backend готов). 3 testid'а добавлены в web-panel
templates. Path A для категории E применён. Polный e2e run отложен
на G9. M14 пока НЕ push'нут — 15 локальных коммитов на `dev`.**

## Контекст M14 (читай это первым)

M14 = «Post-Audit Fixes» — закрытие блокеров first VPS deploy v0.0.0
из четырёх аудитов:
- `docs/milestones/M13-pre-deploy-hardening/G27-cso-comprehensive-audit.md` — 17 findings
- `docs/milestones/M13-pre-deploy-hardening/G26-test-audit-findings.md` — 11 (4 P1)
- `docs/milestones/M13-pre-deploy-hardening/G26-code-review-after-g25.md` — 15 (3 P1)
- `docs/milestones/M13-pre-deploy-hardening/G27-tech-debt-audit.md` — 23 (deferred)

Полный план: `docs/milestones/M14-post-audit-fixes/PLAN.md`.
Чеклист: `docs/milestones/M14-post-audit-fixes/CHECKLIST.md`.
Заметки: `docs/milestones/M14-post-audit-fixes/NOTES.md`.

## Что уже сделано (G1-G7, 2026-04-26)

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
| `f24f22f` | **G7**: G26 false-pass spec fixes + headman-mark.spec.ts skip + future-ideas v0.1 entry |
| `<tbd>` | G7 docs followup |

**G7 главное открытие:** ~50% false-pass spec'ов из G26 audit'а написаны
**forward** — UI никогда не был реализован (`headman-mark.spec.ts`,
`red-zone-badge`). Backend для bulk-mark готов, но web-panel UI требует
~6-10 ч feature work с UX review. Перенесено в `docs/future-ideas.md`
§ «v0.1 — Headman bulk-mark UI». Path A (удалить 2 теста) применён для
категории E (seed `student` имеет `is_headman=true` → тесты invalid).

## Что делать в этой сессии

### Шаг 0 — verify state

```bash
cd C:/Users/maksd/IntelliJIDEA/rutcampustrack
git log --oneline -17
git status --short
```

**Ожидаем:**
- HEAD = `<G7 docs commit>` или `f24f22f`
- Working tree clean (или максимум `?? .gstack/`, `?? tests/e2e/fixtures/test-excuse.pdf` — последний gitignored либо генерится в beforeAll)
- 15-16 локальных коммитов M14 ещё не на origin

### Шаг 1 — выполнить Группу 8 (G26 code-review P1)

⚠️ **Короткая группа** — ~30 минут. 3 sub-task'а.

Из `CHECKLIST.md` (G8):

> ## Группа 8 — G26 code-review P1 (30 мин)
>
> - [ ] `services/api-gateway/src/main/resources/application.yml:122` — вернуть `burstCapacity: 60` на `auth-login` (rollback CI workaround `600`)
> - [ ] Если CI требует override — задать в `docker-compose.e2e.yml` через env (`AUTH_LOGIN_BURST_CAPACITY=600`) и Spring `${AUTH_LOGIN_BURST_CAPACITY:60}` в YAML, либо добавить `PLAYWRIGHT_WORKERS=1` в `playwright.config.ts` для CI profile
> - [ ] `tests/e2e/specs/auth.spec.ts:19-38` — удалить тест `diagnostic: direct POST /api/auth/login` с `console.log` (либо переместить в отдельный `@diag` файл, отключённый в CI grep)
> - [ ] `tests/e2e/fixtures/users.ts:50-55` — удалить запись `headman` из `TEST_USERS`; все callers `TEST_USERS.headman` → `TEST_USERS.student` (`grep -rn "TEST_USERS.headman" tests/e2e/` сначала)
> - [ ] Запустить `npx playwright test --grep @smoke` локально → должно проходить с `burstCapacity=60`
> - [ ] Commit: `fix(gateway,e2e): burstCapacity prod default + diagnostic test removal + DRY users fixture (M14 G8, G26 F01-F03)`

**Контекст из G26 code-review (`G26-code-review-after-g25.md`):**

- **F01:** `auth-login` rate-limit поднят до 600 для CI (workaround
  flaky tests). Production default должен быть 60. Если CI красное —
  правильное решение `PLAYWRIGHT_WORKERS=1` (sequential), не bumping
  rate limit.
- **F02:** diagnostic test с `console.log` остался от debug session.
  Шумит в CI logs, не testит invariant — лишь печатает status code.
- **F03:** `TEST_USERS.headman` дублирует `TEST_USERS.student`
  (`student` УЖЕ has `is_headman=true`). DRY нарушен; забыть update
  одной записи легко. Особое внимание — после G7 `student-excuse.spec.ts`
  использует `TEST_USERS.student` для headman flow (правильно). Проверь
  что callers `TEST_USERS.headman` → `TEST_USERS.student` без semantic
  изменений.

**Pre-flight перед началом G8:**
1. `cat services/api-gateway/src/main/resources/application.yml | grep -A 5 auth-login` — увидеть текущий burstCapacity (должно быть 600 как CI workaround)
2. `grep -n "TEST_USERS.headman" tests/e2e/` — какие spec'и использовали headman fixture
3. Read `tests/e2e/specs/auth.spec.ts:19-38` — diagnostic test полностью

### Шаг 2 — Verify

```bash
# Проверить что burstCapacity=60 не ломает smoke tests локально.
docker compose ps  # backend ready?
npx playwright test --grep @smoke --project=chromium
```

Если smoke падают — НЕ возвращать burstCapacity=600. Лучше добавить
`PLAYWRIGHT_WORKERS=1` либо `--workers=1` в smoke run.

### Шаг 3 — commit + переход к G9

После — docs followup (CHECKLIST + NOTES + rotate hand-off на G9).

### G9 (финальная группа M14)

G9 = «UAT + tag `v0.0.0-alpha.16`» — ~30 мин:
- Полный gradle build + smoke + pytest + frontend unit
- preflight-deploy.sh
- Push origin/dev + ожидание green CI
- Tag `v0.0.0-alpha.16` со списком CRIT/HIGH fixes
- Update `CLAUDE.md` § «Текущий статус» + `docs/milestones/README.md`
- Финальный docs commit

Если у пользователя есть час — G8+G9 в одной сессии.

## Полный список M14 групп (для context)

1. ✅ **G1** — legacy headers strict default (CSO CRIT-01) — `dc40929`
2. ✅ **G2** — SHA-pin appleboy/ssh-action (CSO CRIT-02) — `a93859b`
3. ✅ **G3** — PKCS#8 + idempotent JWT key gen (CSO HIGH-05) — `7e69067`
4. ✅ **G4 v2** — RequiredSecretsValidator (CSO HIGH-06) — `bf915ec`
5. ✅ **G5** — aiohttp + aiogram bump (CSO HIGH-07) — `607af81`
6. ✅ **G6** — SHA-pin actions deploy/coverage/security (CSO HIGH-03/04 + MED-09) — `7fbd908`
7. ✅ **G7** — G26 false-pass tests + headman-mark skip (M14 G7) — `f24f22f`
8. **G8** — G26 code-review P1 (burstCapacity 600→60 + diagnostic + DRY) — **СЛЕДУЮЩАЯ**
9. **G9** — UAT + tag `v0.0.0-alpha.16`

## Local state на момент hand-off (2026-04-26 evening)

```
Working tree: clean (только ?? .gstack/ — gitignored)
Branch: dev (15-16 коммитов впереди origin/dev)

Локальные коммиты M14 (НЕ push'нуты):
  <G7 docs>     docs(M14): G7 done — false-pass tests + v0.1 backlog
  f24f22f       fix(e2e): G26 false-pass tests — testid + routes + skip forward-written + path A для seed mismatch (M14 G7)
  18175fc       docs(M14): G6 done — SHA-pin 16 actions + per-job permissions
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

Push на origin/dev пока НЕ делать — пользователь решает когда (G9 default).

## Pending decisions для new conversation

1. **`burstCapacity` 600→60 — risk smoke flaky.** Если smoke падают
   при 60 — НЕ возвращать 600 (это CI workaround). Альтернативы:
   - `PLAYWRIGHT_WORKERS=1` в `playwright.config.ts` для CI
   - per-env override через `${AUTH_LOGIN_BURST_CAPACITY:60}` + CI env
   - retry + delay в `loginAs` fixture
2. **`auth.spec.ts:19-38` diagnostic** — удалить или перенести в
   `@diag` tag с CI exclude? Чеклист говорит "удалить либо
   переместить". Default — удалить (нет invariant verification).
3. **`TEST_USERS.headman` callers** — после G7 уже минимизировано,
   но проверь grep чтобы не было silent semantic regression.
4. **Push на origin/dev.** По дефолту НЕ пушим до G9.

## История milestone'ов (архив)

- M01-M08 ✅ (`v0.0.0-alpha.1..alpha.9`)
- M09-M12 ✅ 2026-04-24 (`alpha.10..alpha.13`)
- M13 Pre-Deploy Hardening ✅ 2026-04-25 (`v0.0.0-alpha.15`)
- **→ M14 Post-Audit Fixes** (текущий) — G1-G7 ✅, G8-G9 pending. Tag `v0.0.0-alpha.16` после G9.

Roadmap: `docs/milestones/README.md`.
Aудиторские отчёты: `docs/milestones/M13-pre-deploy-hardening/G2{6,7}-*.md`.
