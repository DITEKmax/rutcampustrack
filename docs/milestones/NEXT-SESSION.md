# Промпт для следующей сессии — M14 G6: SHA-pin remaining actions (CSO HIGH-03/04 + MED-09)

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам откроет
нужные файлы и продолжит.

---

**M14 G5 закрыт (commit `607af81`). Surprise: aiogram 3.15.0 пинует
`aiohttp<3.11`, поэтому одновременно bump aiogram → 3.23.0 (минимальная
для `aiohttp<3.14`). 205 pytest passed, coverage 77%. M14 пока НЕ
push'нут — 11 локальных коммитов на `dev`, upstream не получил.**

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

## Что уже сделано (G1-G5, 2026-04-26)

| Коммит | Что |
|--------|-----|
| `455029f` | M14 setup |
| `dc40929` + `5a1b175` | **G1**: legacy headers strict default (CSO CRIT-01) |
| `a93859b` + `dc602a0` | **G2**: SHA-pin appleboy/ssh-action (CSO CRIT-02) |
| `7e69067` + `d20616d` | **G3**: PKCS#8 + idempotent JWT key gen (CSO HIGH-05) |
| `d2daff7` | G4 v1 deferred docs (Spring Boot ограничение) |
| `bf915ec` + `44e2d7c` | **G4 v2**: RequiredSecretsValidator EnvironmentPostProcessor (CSO HIGH-06) |
| `607af81` | **G5**: aiohttp 3.10.11→3.13.5 + aiogram 3.15.0→3.23.0 (CSO HIGH-07) |
| `<tbd>` | G5 docs followup |

**G5 surprise:** aiogram peer dependency на aiohttp `<3.11` блокировал
прямой aiohttp bump. Решение через одновременный aiogram bump до 3.23.0
(минимальная разрешающая `aiohttp<3.14`). Conservative выбор vs latest
3.27 — 8 minor versions vs 12 минимизирует API breakage. Pytest 205
passed, coverage 77.19%. Урок: для Python dep bumps всегда pre-flight
PyPI JSON для transitive constraints.

## Что делать в этой сессии

### Шаг 0 — verify state

```bash
cd C:/Users/maksd/IntelliJIDEA/rutcampustrack
git log --oneline -13
git status --short
```

**Ожидаем:**
- HEAD = `<G5 docs commit>` или `607af81`
- Working tree clean (или максимум `?? .gstack/`)
- 11-12 локальных коммитов M14 ещё не на origin

### Шаг 1 — выполнить Группу 6 (SHA-pin remaining actions)

⚠️ **Самая длинная supply-chain группа в M14** — ~45 минут.

Из `CHECKLIST.md`:

> ## Группа 6 — CSO HIGH-03/04: SHA-pin remaining actions (45 мин)
>
> - [ ] Собрать SHAs одним `gh api` batch'ем для:
>   - `actions/checkout@v4`
>   - `docker/setup-buildx-action@v3`
>   - `docker/login-action@v3`
>   - `docker/build-push-action@v7`
>   - `madrapps/jacoco-report@v1.7.1`
>   - `davelosert/vitest-coverage-report-action@v2`
>   - `MishaKav/pytest-coverage-comment@v1.1.52`
>   - `marocchino/sticky-pull-request-comment@v2`
>   - `gitleaks/gitleaks-action@v2`
> - [ ] `.github/workflows/deploy.yml` — заменить все 4 first-party actions на SHA-pinned
> - [ ] `.github/workflows/coverage.yml` — заменить 4 third-party actions на SHA-pinned
> - [ ] `.github/workflows/coverage.yml` — перенести `permissions: { pull-requests: write, checks: write }` из top-level в per-job
> - [ ] `.github/workflows/security.yml` — SHA-pin gitleaks-action (MED-09 заодно)
> - [ ] Verify: `grep -rE "uses: [^@]+@v\d+(\.\d+)?$" .github/workflows/*.yml` → пусто
> - [ ] Commit: `fix(ci): SHA-pin third-party + first-party actions в deploy/coverage/security (M14 G6, CSO HIGH-03/04 + MED-09)`

**Контекст из CSO audit (`G27-cso-comprehensive-audit.md` § HIGH-03/04):**

В G2 уже был pin'нут `appleboy/ssh-action` — самый критичный (SSH key
к VPS). Остальные actions имеют разный risk:

- **HIGH-03**: third-party actions с GHCR_TOKEN access → image push hijack
- **HIGH-04**: first-party `actions/*` и `docker/*` — у Docker/GitHub
  reputation хорошая, но maintainer compromise всё равно реален
- **MED-09**: `gitleaks-action` — ниже priority но в том же sweep

**Pre-flight `gh` CLI отсутствует в bash PATH** (сюрприз из G2).
Использовать `curl https://api.github.com/repos/{owner}/{repo}/git/refs/tags/{tag}`:
```bash
curl -s https://api.github.com/repos/actions/checkout/git/refs/tags/v4 | grep -E '"(sha|type)"'
```

Если type = `commit` → ref напрямую = commit SHA. Если type = `tag`
(annotated tag) → ещё один шаг через `git/tags/{tag-sha}/object/sha`.

**Pattern для replace** (как в G2):
```yaml
uses: actions/checkout@<40-hex-sha> # v4.2.2
```
Комментарий с конкретной версией нужен для Renovate/Dependabot
auto-update.

### Шаг 2 — Verify

```bash
grep -rE "uses: [^@]+@v[0-9]" .github/workflows/*.yml
```

Должно быть пусто (все pin'нуты на SHA). Если что-то остаётся — это
либо action из ASW/`actions/` где admins имеют hard guarantee
(GitHub-controlled), либо просто пропущено.

YAML validate:
```bash
py -c "import yaml; [yaml.safe_load(open(f,encoding='utf-8')) for f in ['.github/workflows/deploy.yml','.github/workflows/coverage.yml','.github/workflows/security.yml']]; print('OK')"
```

### Шаг 3 — commit + переход к G7

После — docs followup. Затем **не двигайся к G7 без явного go от
пользователя**.

### Если G6 завершён — спроси про G7

G7 = «G26 test-audit P1 (false-pass Playwright tests)» (~1-1.5ч). Самая
длинная в M14. Требует решения по категории E (path A удалить тесты vs
path B добавить seed user). Если у пользователя меньше 1.5 часов —
оставить на отдельный сеанс.

## Полный список M14 групп (для context)

1. ✅ **G1** — legacy headers strict default (CSO CRIT-01) — `dc40929`
2. ✅ **G2** — SHA-pin appleboy/ssh-action (CSO CRIT-02) — `a93859b`
3. ✅ **G3** — PKCS#8 + idempotent JWT key gen (CSO HIGH-05) — `7e69067`
4. ✅ **G4 v2** — RequiredSecretsValidator (CSO HIGH-06) — `bf915ec`
5. ✅ **G5** — aiohttp + aiogram bump (CSO HIGH-07) — `607af81`
6. **G6** — SHA-pin remaining actions (CSO HIGH-03/04 + MED-09) — **СЛЕДУЮЩАЯ**
7. **G7** — G26 test-audit P1 (false-pass Playwright tests) — самая длинная
8. **G8** — G26 code-review P1 (burstCapacity 600→60 + diagnostic test + DRY)
9. **G9** — UAT + tag `v0.0.0-alpha.16`

## Local state на момент hand-off (2026-04-26 evening)

```
Working tree: clean (только ?? .gstack/ — gitignored)
Branch: dev (11-12 коммитов впереди origin/dev)

Локальные коммиты M14 (НЕ push'нуты):
  <G5 docs>     docs(M14): G5 done — aiohttp + aiogram bump
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

Push на origin/dev пока НЕ делать — пользователь решает когда.

## Pending decisions для new conversation

1. **First-party vs third-party priority.** В hand-off указано
   pin'ить и first-party (actions/checkout etc) и third-party. Если у
   пользователя tight budget — third-party критичнее (audit-приоритет).
2. **Renovate config update.** Если bot не auto-bump'ает digest — добавить
   `digest:pin: true` в renovate.json. Сначала commit pin'ы, посмотреть
   что Renovate понимает в next sweep.
3. **Push на origin/dev.** По дефолту НЕ пушим.

## История milestone'ов (архив)

- M01-M08 ✅ (`v0.0.0-alpha.1..alpha.9`)
- M09-M12 ✅ 2026-04-24 (`alpha.10..alpha.13`)
- M13 Pre-Deploy Hardening ✅ 2026-04-25 (`v0.0.0-alpha.15`)
- **→ M14 Post-Audit Fixes** (текущий) — G1-G5 ✅, G6-G9 pending. Tag `v0.0.0-alpha.16` после G9.

Roadmap: `docs/milestones/README.md`.
Aудиторские отчёты: `docs/milestones/M13-pre-deploy-hardening/G2{6,7}-*.md`.
