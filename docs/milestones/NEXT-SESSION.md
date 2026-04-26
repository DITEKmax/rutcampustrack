# Промпт для следующей сессии — M14 G7: G26 test-audit P1 (false-pass Playwright tests)

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам откроет
нужные файлы и продолжит.

---

**M14 G6 закрыт (commit `7fbd908`). 16 actions pinned на 40-hex SHA через
3 workflow files (deploy/coverage/security), 58 insertions / 42 deletions.
Bonus: top-level permissions в coverage.yml сужены до `contents: read`,
per-job least-privilege для PR-commenting jobs. M14 пока НЕ push'нут —
13 локальных коммитов на `dev`, upstream не получил.**

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

## Что уже сделано (G1-G6, 2026-04-26)

| Коммит | Что |
|--------|-----|
| `455029f` | M14 setup |
| `dc40929` + `5a1b175` | **G1**: legacy headers strict default (CSO CRIT-01) |
| `a93859b` + `dc602a0` | **G2**: SHA-pin appleboy/ssh-action (CSO CRIT-02) |
| `7e69067` + `d20616d` | **G3**: PKCS#8 + idempotent JWT key gen (CSO HIGH-05) |
| `d2daff7` | G4 v1 deferred docs (Spring Boot ограничение) |
| `bf915ec` + `44e2d7c` | **G4 v2**: RequiredSecretsValidator EnvironmentPostProcessor (CSO HIGH-06) |
| `607af81` + `56c802f` | **G5**: aiohttp 3.10.11→3.13.5 + aiogram 3.15.0→3.23.0 (CSO HIGH-07) |
| `7fbd908` | **G6**: SHA-pin 16 actions × 3 workflows + permissions least-privilege (CSO HIGH-03/04 + MED-09) |
| `<tbd>` | G6 docs followup |

**G6 surprise:** `marocchino/sticky-pull-request-comment` не имеет
floating `v2` ref — pin'ил на `v2.9.4`. Annotated tags (`gradle/actions`,
`gitleaks-action`, `codeql-action`) требуют extra round-trip через
`/git/tags/{sha}`. `gh` CLI всё ещё отсутствует, использовался
`curl + py`. CI.yml/openapi-drift.yml оставлены с floating tags
намеренно (вне scope hand-off).

## Что делать в этой сессии

### Шаг 0 — verify state

```bash
cd C:/Users/maksd/IntelliJIDEA/rutcampustrack
git log --oneline -15
git status --short
```

**Ожидаем:**
- HEAD = `<G6 docs commit>` или `7fbd908`
- Working tree clean (или максимум `?? .gstack/`)
- 13-14 локальных коммитов M14 ещё не на origin

### Шаг 1 — выполнить Группу 7 (G26 test-audit P1)

⚠️ **Самая длинная группа в M14** — 1-1.5 часа. Требует решения по
категории E (path A vs path B) до старта.

Из `CHECKLIST.md` (G7):

> ## Группа 7 — G26 test-audit P1 (1-1.5 ч)
>
> - [ ] `tests/e2e/specs/role-teacher.spec.ts:18` — убрать `/teacher/schedule` из `paths` array (категория B; роута нет в `TEACHER_ROUTES`)
> - [ ] **Решить в NOTES:** для категории E (`role-student.spec.ts`) — путь A (удалить тесты "cannot access /headman/*") или путь B (добавить seed `student_plain` с `is_headman=false`)
> - [ ] Реализовать выбранный путь по категории E
> - [ ] `tests/e2e/specs/role-teacher.spec.ts:34` — заменить `[data-testid="red-zone-badge"]` на ARIA role/text локатор; либо добавить `data-testid="red-zone-badge"` в `web-panel` шаблон (категория A)
> - [ ] `tests/e2e/specs/headman-mark.spec.ts:29,52` — fix `[data-testid="lesson-card"]` + `[data-testid="group-attendance-count"]`
> - [ ] `tests/e2e/specs/student-excuse.spec.ts:34,56` — fix `[data-testid="lesson-picker-item"]` + `[data-testid="excuse-card"]`
> - [ ] `tests/e2e/specs/admin-create-user.spec.ts:49` — fix `[data-testid="initial-password-display"]`
> - [ ] Если выбран путь template-testid: соответствующие изменения в `frontends/web-panel/**/*.html` + rebuild web-panel image
> - [ ] Запустить `npx playwright test --grep @smoke` локально → все тесты RUN (нет skip по timeout) и большинство pass
> - [ ] Commit: `fix(e2e): false-pass tests из G26 audit — testid + routes + seed user (M14 G7)`

**Категория E решение (preliminary lean toward A):**

NOTES.md уже фиксирует мою предварительную позицию: **path A
(удалить 2 теста)**. Reasoning:
- RBAC уже покрыт `SecurityIdorIT` на backend (M03b + M09).
- E2E тест дублирует backend-уровневую защиту, дополнительной ценности
  даёт мало vs path B cost (~30-45 мин Flyway seed migration в test profile).
- Path A cost: ~5 мин, минус 2 теста coverage.

Запиши окончательное решение в NOTES.md в начале G7 — когда дойдёшь до
тех 2 тестов в `role-student.spec.ts`. **Если pre-flight reading
показывает что тесты дают какое-то уникальное coverage — переключись на
path B и запиши обоснование.**

**Контекст из G26 test-audit (`G26-test-audit-findings.md`):**

5 категорий false-pass тестов:
- **A (testid mismatch):** локатор в spec не существует в template
  → `locator.waitFor()` timeout → тест skip с false-positive (Playwright
  retry'и маскируют).
- **B (route mismatch):** spec'ы тестируют `/teacher/schedule`, но
  TEACHER_ROUTES не содержит такой маршрут → 404 → false navigation
  pass.
- **E (RBAC negative test seed mismatch):** spec тестирует "student
  without is_headman cannot access /headman/*", но seed user `student`
  имеет `is_headman=true`. Тест де-facto проверяет другой scenario.

**Pre-flight перед началом G7:**
1. Прочитать `tests/e2e/fixtures/users.ts` — какие seed users есть.
2. Прочитать `frontends/web-panel/src/app/app.routes.ts` (или эквивалент
   роутера) — реальные маршруты vs spec предположения.
3. `grep -rn "data-testid" frontends/web-panel/src/app/` → существующие
   testid'ы; сопоставить с spec'ами.
4. `grep -rn "data-testid" tests/e2e/` → expected testid'ы из spec'ов.
5. Diff сопоставление — fix application (template add testid) или fix
   spec (use semantic locator)? Чеклист hand-off предполагает первичный
   approach — fix spec через semantic локатор (`getByRole`/`getByText`),
   template testid добавляется только если semantic невозможен.

### Шаг 2 — Verify

```bash
# должен проходить с поднятой инфраструктурой
docker compose ps  # 26+ healthy
npx playwright test --grep @smoke
```

Все тесты должны **RUN** (нет skip по timeout без явной причины).
**Большинство** pass — допустимо несколько fail если они docunented как
P2/P3 либо имеют known-issue с workaround.

### Шаг 3 — commit + переход к G8

После — docs followup (CHECKLIST + NOTES + rotate hand-off на G8).

### Если G7 завершён — переход к G8 (короткий)

G8 = «G26 code-review P1» — 30 мин:
- `auth-login` burstCapacity 600→60 (rollback CI workaround)
- удалить diagnostic test `direct POST /api/auth/login` из `auth.spec.ts`
- удалить duplicate `headman` user из `users.ts` fixtures, callers →
  `student`

Если у пользователя есть час — G7+G8 в одной сессии.

## Полный список M14 групп (для context)

1. ✅ **G1** — legacy headers strict default (CSO CRIT-01) — `dc40929`
2. ✅ **G2** — SHA-pin appleboy/ssh-action (CSO CRIT-02) — `a93859b`
3. ✅ **G3** — PKCS#8 + idempotent JWT key gen (CSO HIGH-05) — `7e69067`
4. ✅ **G4 v2** — RequiredSecretsValidator (CSO HIGH-06) — `bf915ec`
5. ✅ **G5** — aiohttp + aiogram bump (CSO HIGH-07) — `607af81`
6. ✅ **G6** — SHA-pin actions deploy/coverage/security (CSO HIGH-03/04 + MED-09) — `7fbd908`
7. **G7** — G26 test-audit P1 (false-pass Playwright tests) — **СЛЕДУЮЩАЯ**
8. **G8** — G26 code-review P1 (burstCapacity 600→60 + diagnostic test + DRY)
9. **G9** — UAT + tag `v0.0.0-alpha.16`

## Local state на момент hand-off (2026-04-26 evening)

```
Working tree: clean (только ?? .gstack/ — gitignored)
Branch: dev (13-14 коммитов впереди origin/dev)

Локальные коммиты M14 (НЕ push'нуты):
  <G6 docs>     docs(M14): G6 done — SHA-pin 16 actions + per-job permissions
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

Push на origin/dev пока НЕ делать — пользователь решает когда.

## Pending decisions для new conversation

1. **Категория E path A vs B.** Lean toward A (~5 мин, минус 2 теста)
   vs B (~30-45 мин, новый seed user через Flyway). Запиши финальное
   решение в NOTES.md в первые ~10 мин G7.
2. **template testid vs semantic locator.** Если semantic возможен
   (`getByRole('button', { name: 'Mark attendance' })`) — предпочесть
   semantic. Template testid только когда semantic невозможен.
3. **CI workflow SHA-pin sweep.** `ci.yml` + `openapi-drift.yml` имеют
   ~30 floating tags. Кандидат на отдельный sprint после M14 либо
   через Renovate `digest:pin`. Не блокирует v0.0.0-alpha.16.
4. **Push на origin/dev.** По дефолту НЕ пушим до G9.

## История milestone'ов (архив)

- M01-M08 ✅ (`v0.0.0-alpha.1..alpha.9`)
- M09-M12 ✅ 2026-04-24 (`alpha.10..alpha.13`)
- M13 Pre-Deploy Hardening ✅ 2026-04-25 (`v0.0.0-alpha.15`)
- **→ M14 Post-Audit Fixes** (текущий) — G1-G6 ✅, G7-G9 pending. Tag `v0.0.0-alpha.16` после G9.

Roadmap: `docs/milestones/README.md`.
Aудиторские отчёты: `docs/milestones/M13-pre-deploy-hardening/G2{6,7}-*.md`.
