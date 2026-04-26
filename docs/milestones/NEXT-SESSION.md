# Промпт для следующей сессии — M14 G5: aiohttp 3.10.11 → 3.13.3+ bump (CSO HIGH-07)

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам откроет
нужные файлы и продолжит.

---

**M14 G3 закрыт (commit `7e69067`). G4 (fail-fast secrets) DEFERRED в
pre-v0.1 — Spring Boot 3.x не имеет native fail-fast на unresolved
YAML placeholders. M14 пока НЕ push'нут на origin/dev — 8 локальных
коммитов на `dev`, upstream не получил.**

## Контекст M14 (читай это первым)

M14 = «Post-Audit Fixes» — закрытие блокеров first VPS deploy v0.0.0
из четырёх аудитов:
- `docs/milestones/M13-pre-deploy-hardening/G27-cso-comprehensive-audit.md` — 17 findings
- `docs/milestones/M13-pre-deploy-hardening/G26-test-audit-findings.md` — 11 (4 P1)
- `docs/milestones/M13-pre-deploy-hardening/G26-code-review-after-g25.md` — 15 (3 P1)
- `docs/milestones/M13-pre-deploy-hardening/G27-tech-debt-audit.md` — 23 (deferred)

**Триаж принцип:** только (a) ломает first deploy, (b) runtime guard от
operator mistake, (c) trivial cost / non-trivial impact, (d) CI gate
compromised без fix → попадает в M14. Остальное — `docs/future-ideas.md`
(pre-v0.1) или `docs/deferred-ideas.md` (v0.1+).

Полный план: `docs/milestones/M14-post-audit-fixes/PLAN.md`.
Чеклист: `docs/milestones/M14-post-audit-fixes/CHECKLIST.md`.
Заметки: `docs/milestones/M14-post-audit-fixes/NOTES.md`.

## Что уже сделано (G1-G4, 2026-04-26)

| Коммит | Что | Verification |
|--------|-----|--------------|
| `455029f` | M14 setup | docs only |
| `dc40929` | **G1: legacy headers strict by default (CSO CRIT-01)** | 4× SecurityIdorIT + 3× *StrictModeIT BUILD SUCCESSFUL |
| `5a1b175` | G1 docs followup | docs only |
| `a93859b` | **G2: SHA-pin appleboy/ssh-action (CSO CRIT-02)** — `0ff4204... # v1.2.5` | grep + pyyaml validate |
| `dc602a0` | G2 docs followup | docs only |
| `7e69067` | **G3: PKCS#8 + idempotent JWT key gen (CSO HIGH-05)** в deploy.yml:329-360 | dry-run head -1 = "BEGIN PRIVATE KEY" + JDK standalone clone parsing OK + PKCS#1 negative test fails as expected |
| `d20616d` | G3 docs followup | docs only |
| `<tbd>` | **G4 DEFERRED**: fail-fast secrets — Spring Boot 3.x не fail-fast на unresolved YAML placeholders (issues spring-boot#10463/#18816). Перенесено в pre-v0.1. Все YAML revert'нуты через git checkout. | docs only — full post-mortem в NOTES.md + future-ideas.md |

**G4 critical finding:** `${VAR:?msg}` это **bash syntax**, не Spring.
`${VAR}` без default Spring **тоже не fail-fast** — оставляет literal
`"${VAR}"` string. UAT через rebuild auth image + docker run без env
доказал это: `IllegalStateException: secret must be at least 32 bytes
(got 25)` — 25 = длина `"${INTERNAL_ISSUER_SECRET}"`. Mitigation в
v0.0.0: M13 G15 preflight script (primary) + per-property
`@PostConstruct validate()` где есть (secondary) + healthcheck unhealthy
30-60s (tertiary). G4 правильное решение — `ApplicationContextInitializer`
в `shared-web` либо bash entrypoint в Dockerfile, ~2-3ч работы. Detail
в `docs/future-ideas.md` § "CSO HIGH-06: fail-fast secrets через
ApplicationContextInitializer".

## Что делать в этой сессии

### Шаг 0 — verify state

```bash
cd C:/Users/maksd/IntelliJIDEA/rutcampustrack
git log --oneline -10
git status --short
```

**Ожидаем:**
- HEAD = `<G4 docs commit>` или `d20616d` (если G4 docs не отдельным коммитом)
- Working tree clean (или максимум `?? .gstack/`)
- 8 локальных коммитов M14 ещё не на origin

### Шаг 1 — выполнить Группу 5 (aiohttp bump)

Из `CHECKLIST.md`:

> ## Группа 5 — CSO HIGH-07: aiohttp bump (5 мин)
>
> - [ ] `services/notification-bot/requirements.txt` — `aiohttp>=3.13.3,<3.14` (было `3.10.11`)
> - [ ] Локальная проверка совместимости: `cd services/notification-bot && python -m venv .venv-test && .venv-test/Scripts/pip install -r requirements.txt` — должно установиться без conflict с `aiogram`
> - [ ] `pytest services/notification-bot/tests` — все зелёные после bump
> - [ ] Rebuild bot image: `docker build -t rct-notification-bot services/notification-bot/`
> - [ ] Commit: `chore(deps): aiohttp 3.10.11→3.13.3+ (M14 G5, CSO HIGH-07, 3 CVE)`

**Контекст из CSO audit (`G27-cso-comprehensive-audit.md` § HIGH-07):**

`services/notification-bot/requirements.txt` пинует `aiohttp==3.10.11`,
которая имеет 3 CVE:
- CVE-2024-XX (request smuggling)
- CVE-2024-YY (DoS через large headers)
- CVE-2024-ZZ (TLS bypass в client)

Fixed в `aiohttp>=3.13.3`. `aiogram` (Telegram bot framework) использует
aiohttp как peer dependency — нужно проверить compatibility constraint.

**Pre-flight:**
```bash
cd services/notification-bot && cat requirements.txt | grep -E "aiogram|aiohttp"
```

Aiogram 3.x официально поддерживает aiohttp ≥3.9. v3.13.3 should work.

### Шаг 2 — verification

После bump:
1. `pip install -r requirements.txt` локально — без conflict.
2. `pytest services/notification-bot/tests/` — все green (108 тестов).
3. Rebuild bot image — Dockerfile build OK.
4. Smoke test inside container: `python -c "import aiohttp; print(aiohttp.__version__)"` → 3.13.x.

Если pytest fails из-за aiohttp API changes — diagnose, либо patch
test, либо downgrade target до latest version, что pass'ит. Обычно
aiohttp 3.x stable, breaking changes минимальны.

### Шаг 3 — commit + переход к G6

Commit message:
```
chore(deps): aiohttp 3.10.11→3.13.3+ в notification-bot (M14 G5, CSO HIGH-07)

CSO comprehensive audit (G27) обнаружил HIGH-07: aiohttp 3.10.11 имеет
3 CVE (request smuggling / DoS / TLS bypass). Fixed в 3.13.3+.

Bump constraint: aiohttp>=3.13.3,<3.14 (compatible с aiogram 3.x).

Verification:
- pip install -r requirements.txt без conflict ✅
- pytest services/notification-bot/tests ~108 тестов green ✅
- docker build rct-notification-bot успешный ✅
- python -c "import aiohttp; print(aiohttp.__version__)" → 3.13.x ✅

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```

После — docs followup (CHECKLIST + NOTES update). Затем **не двигайся
к G6 без явного go от пользователя**.

### Если G5 завершён и есть оставшееся время — спроси про G6

G6 = «SHA-pin remaining actions в deploy/coverage/security» (~45 мин).
Самая длинная supply-chain группа в M14, но низкий риск (тот же
паттерн что G2). Если у пользователя меньше 1 часа — лучше
не начинать, оставить на отдельный сеанс.

## Полный список M14 групп (для context)

1. ✅ **G1** — legacy headers strict default (CSO CRIT-01) — done `dc40929`
2. ✅ **G2** — SHA-pin appleboy/ssh-action (CSO CRIT-02) — done `a93859b`
3. ✅ **G3** — PKCS#8 + idempotent JWT key gen (CSO HIGH-05) — done `7e69067`
4. ⛔ **G4 DEFERRED** — fail-fast secrets (CSO HIGH-06) → pre-v0.1 (Spring Boot ограничение)
5. **G5** — aiohttp 3.10.11 → 3.13.3+ bump (CSO HIGH-07) — **СЛЕДУЮЩАЯ**
6. **G6** — SHA-pin remaining actions в deploy/coverage/security (CSO HIGH-03/04 + MED-09)
7. **G7** — G26 test-audit P1 (false-pass Playwright tests) — самая длинная (~1-1.5ч)
8. **G8** — G26 code-review P1 (burstCapacity 600→60 + diagnostic test + DRY)
9. **G9** — UAT + tag `v0.0.0-alpha.16`

## Local state на момент hand-off (2026-04-26 evening)

```
Working tree: clean (только ?? .gstack/ — security report, gitignored)
Branch: dev (8 коммитов впереди origin/dev)
Last commit: <G4 docs commit — обновится в реальности>

Локальные коммиты M14 (НЕ push'нуты):
  <G4 docs>   docs(M14): G4 deferred — Spring Boot не fail-fast на unresolved YAML placeholders
  d20616d     docs(M14): отметить G3 done + rotate hand-off на G4
  7e69067     fix(ci): PKCS#8 + idempotent JWT key gen в deploy.yml (M14 G3, CSO HIGH-05)
  dc602a0     docs(M14): отметить G2 done + rotate hand-off на G3
  a93859b     fix(ci): SHA-pin appleboy/ssh-action against supply chain (M14 G2, CSO CRIT-02)
  5a1b175     docs(M14): отметить G1 done + зафиксировать application-test.yml asymmetry
  dc40929     fix(security): legacy headers strict by default (M14 G1, CSO CRIT-01)
  455029f     docs(M14): план + триаж 4 пост-M13 аудитов (M14 setup)
```

Push на origin/dev пока НЕ делать — пользователь решает когда (либо после
G9 + tag, либо если хочет промежуточный CI run для верификации).

## Pending decisions для new conversation

1. **Aiohttp version constraint.** В hand-off `>=3.13.3,<3.14`. Проверь
   через `pip index versions aiohttp` если можно — иначе ставь так и
   возложи на pip resolver.
2. **Test failures после bump.** Если pytest падает — приоритет: patch
   test (если API change в aiohttp), не downgrade aiohttp ниже 3.13.3
   (CVE patches там).
3. **Push на origin/dev.** По дефолту НЕ пушим.

## История milestone'ов (архив)

- M01-M08 ✅ (`v0.0.0-alpha.1..alpha.9`)
- M09-M12 ✅ 2026-04-24 (`alpha.10..alpha.13`)
- M13 Pre-Deploy Hardening ✅ 2026-04-25 (`v0.0.0-alpha.15`)
- **→ M14 Post-Audit Fixes** (текущий) — G1 ✅, G2 ✅, G3 ✅, G4 ⛔ deferred,
  G5-G9 pending. Tag `v0.0.0-alpha.16` после G9.

Roadmap: `docs/milestones/README.md`.
Aудиторские отчёты-источники: `docs/milestones/M13-pre-deploy-hardening/G2{6,7}-*.md`.
Trail отложенного: `docs/future-ideas.md` § Pre-v0.1 (включая G4
"CSO HIGH-06: fail-fast secrets через ApplicationContextInitializer") +
`docs/deferred-ideas.md` § v0.1+ tech debt.
