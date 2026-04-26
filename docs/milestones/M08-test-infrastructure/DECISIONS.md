# M08 Decisions

Micro-решения и отступления от PLAN.md. Одно решение — один пункт
с датой, контекстом, trade-off'ами.

---

## D1 — Playwright scope: skip mini-app (2026-04-22)

**Вопрос:** покрывать ли Telegram Mini App (`frontends/mini-app/`)
в Playwright E2E suite?

**Решение:** **Skip.** E2E через `tests/e2e/` покрывает только PWA +
web-panel + landing. Mini-app остаётся без E2E до миграции на PWA
baseline (см. future-ideas.md «Mini-app unification after M12»).

**Контекст:**
- 14 P2-12 уже помечен ACCEPT в COVERAGE-AUDIT.
- Mini-app не ready для прода (ручные interfaces, не покрыт openapi-ts
  drift-check, M07 осознанно его опустил).
- Эмулировать Telegram WebApp SDK + валидный `initData` для E2E —
  отдельная нестабильная зависимость.

**Trade-off:** при первом реальном deploy mini-app в Telegram мы
узнаем о проблемах через manual testing, а не через автоматизацию.
Принимаем — mini-app будет copy-adapt из PWA после M12, к тому
моменту PWA-тесты уже зрелые.

---

## D2 — k6 в CI: только scripts + docs, без CI job (2026-04-22)

**Вопрос:** прогонять ли k6 нагрузочные тесты автоматически в CI
(per-PR / nightly) или оставить manual прогон release-engineer'ом?

**Решение:** **Manual-only.** В `tests/load/` лежат 2 k6-скрипта
(`bulk-mark.js`, `geolocation-flood.js`) + `docs/performance/performance-baseline.md`
с первыми числами. Release-engineer прогоняет локально перед каждым
release tag. CI load-job НЕ создаётся.

**Контекст:**
- OWNER-ANSWERS P2-8/7 явно разрешает v0.0.0 ограничиться
  minimal-only.
- VPS/staging нестабилен до M09 (prod-deploy-checklist) — nightly
  load-job без stable dev-инстанса бесполезен.
- GitHub-hosted runners имеют плавающий CPU → недостоверные p95
  между runs → false positives.
- Per-PR блокирующий load-gate превращается в театр (все ставят
  `[skip-load]`, проверка мёртвая).

**Trade-off:** регрессии performance не детектируются автоматически
между релизами — ловим только если release-engineer не забыл прогнать.
Записываем в `future-ideas.md` «Full load-testing suite → v0.1»
(Gatling/JMeter + dedicated runner + nightly).

---

## D3 — Diff-coverage gate: warning → hard-fail (2026-04-22)

**Вопрос:** при внедрении `diff-cover ≥80%` на changed lines —
сразу hard-fail (red CI) или постепенно (warning → hard-fail)?

**Решение:** **Warning первый PR, hard-fail со второго.**
Первый coverage-gate PR имеет `|| echo "::warning::baseline PR"`
после `diff-cover --fail-under=80`. Следующий PR делает `diff-cover
--fail-under=80` строгим (exit non-zero ломает CI).

**Контекст:**
- OWNER-ANSWERS QD2 требует **gate активен**, не просто метрика
  в PR-комменте.
- Первый PR вводит infrastructure — сам не покрывает своё изменение
  (нет feature-кода).
- Legacy-модули имеют низкую coverage → любой touch тянет % вниз.

**Trade-off:** первый PR не блокируется, далее все изменения строгие.
`docs/testing/testing.md` FAQ: как посмотреть незакрытые строки, как
пометить legacy через `@ExcludeFromCoverage` / JaCoCo `excludes`.

---

## D4 — Cosign: keyless через OIDC Fulcio (2026-04-22)

**Вопрос:** подписывать GHCR-образы через keyless (OIDC → Fulcio
short-lived cert) или с private/public keypair?

**Решение:** **Keyless.** `sigstore/cosign-installer@{sha}` +
`cosign sign --yes ghcr.io/...` в CI. Verify на VPS:
```bash
cosign verify \
  --certificate-identity-regexp="https://github.com/maksd/rutcampustrack/.github/workflows/.*" \
  --certificate-oidc-issuer=https://token.actions.githubusercontent.com \
  ghcr.io/maksd/rutcampustrack/<service>:<tag>
```

**Контекст:**
- RutCampusTrack — публичный репо. Rekor-лог (transparency log)
  публикует commit SHA + workflow URL — всё и так публично.
- Нет `COSIGN_PRIVATE_KEY` в GH Secrets → zero-secret supply-chain.
- Каждая сборка получает уникальный short-lived cert (10 минут TTL)
  — revocation не нужна.
- Fulcio public instance — внешняя зависимость (sigstore.dev), но
  это стандарт для open-source supply-chain.

**Trade-off:** verify-команда сложнее (identity-regex vs `cosign.pub`
файл), но в runbook `docs/operations/runbooks/image-signing-verification.md`
это одна команда, копируемая как есть.

---

## D5 — Testcontainers reuse: везде кроме FlywayMigrationIT (2026-04-22)

**Вопрос:** включить ли `.withReuse(true)` для контейнеров в
`shared-test-containers`?

**Решение:** **Reuse везде, кроме FlywayMigrationIT.**
- `shared-test-containers` базовые контейнеры (PostgreSQL, Mongo,
  Redis, Rabbit) — `.withReuse(true)`.
- `FlywayMigrationIT` (Группа 3 M08) использует `.withReuse(false)`
  или отдельный non-shared контейнер (fresh БД обязательна для
  `freshInstallAppliesAllMigrations` template).
- Локальный dev включает reuse через `~/.testcontainers.properties`
  (`testcontainers.reuse.enable=true`), CI-runners не имеют этого
  файла — reuse неактивен, контейнеры создаются fresh каждый job.

**Контекст:**
- OWNER-ANSWERS QD1 + PLAN.md Группа 2 acceptance «CI time +15% max»
  — CI не использует reuse, scope не влияет.
- Локальный DX: ~5× speedup integration-тестов (Postgres startup
  ~8s → reuse skip).
- FlywayMigrationIT требует clean schema — reuse сохранит
  Flyway state между прогонами и скроет bugs в миграциях.

**Trade-off:** «грязное» state между тестами локально, если тест
забывает cleanup. Минимизируется через `@Sql(scripts="/cleanup.sql")`
или `mongo.drop()` в `@BeforeEach`. Документируем в
`docs/operations/runbooks/dev-setup.md` (создадим в Группе 2).

---

## D6 — G8 CSRF: заменить double-submit на SameSite contract (2026-04-22)

**Вопрос:** NEXT-SESSION/CHECKLIST требует `CsrfDoubleSubmitIT`, но M03b
DECISIONS (2026-04-20) явно отвергли double-submit token для v0.0.0.
В codebase нет CSRF-filter/X-CSRF-TOKEN header. Что тестировать?

**Решение:** вместо `CsrfDoubleSubmitIT` — **`SameSiteCookieContractIT`**
в auth-service. Покрывает реально применяемый CSRF-защитный контракт:

- cookie `rct_refresh` после login имеет `HttpOnly`, `Secure`, `SameSite=Strict`
- POST `/auth/refresh` без cookie → 401 (proof: cross-origin рефреш невозможен)
- POST `/auth/refresh` с cookie, БЕЗ `X-CSRF-TOKEN` header → 200
  (regression guard против случайного введения CSRF-header без
  frontend-interceptor — сломает логин в PWA/web-panel)
- `/auth/logout` clear-cookie с теми же атрибутами

**Контекст:**
- `AuthIT.java` (строки 93-95) частично покрывает атрибуты, но в
  happy-path. Нужен отдельный IT с фокусом на security contract.
- `X-CSRF-TOKEN` absence-тест — guard против регрессии: если кто-то в
  v1.0 решит включить double-submit, он должен **одновременно** обновить
  PWA + web-panel interceptor'ы. Тест падает → напоминание.
- CHECKLIST G8 обновляется: `CsrfDoubleSubmitIT` → `SameSiteCookieContractIT`.

**Trade-off:** не покрываем гипотетический double-submit механизм, но он
и не существует. Когда введут в v1.0 — новый IT добавляется в M09+.
Текущий тест фиксирует действительный контракт v0.0.0.
