# M08 Notes

Живой файл. Отклонения, измерения, surprises, вопросы к владельцу.

---

## Вопросы к owner'у до старта

1. **Playwright scope для mini-app** — запускаем ли Playwright на
   Telegram Mini App (сложнее — нужен mock TMA initData + emulator)?
   Мой default: skip (14 P2-12 ACCEPT), проверяем только PWA + web-panel +
   landing.
2. **k6 in CI vs manual** — OWNER-ANSWERS (P2-8/7) разрешает minimal-only
   в v0.0.0 (manual прогон перед релизом). CI-интеграцию отложили в v0.1.
   Подтверждение: delivery k6 scripts + docs, но без CI job.
3. **Diff-coverage threshold 80% — strict or warning?** — при первом
   внедрении возможны many-files PR с coverage dip. Warning (comment)
   или hard-fail (red CI)?
4. **Cosign: keyless vs with key?** — keyless через OIDC требует
   Fulcio, проще в настройке; with key требует `COSIGN_PRIVATE_KEY` в
   GHCR secrets. Рекомендую keyless для v0.0.0.

## Ожидаемые surprises

- **`@MockitoBean` audit может показать > 36 мест** — текущий счёт из
  аудита (QD4). Если после grep окажется 50+, нужно принять правило
  «high-value only» вместо всех.
- **Testcontainers reuse flaky на Windows dev** — `.withReuse(true)`
  требует Docker Desktop + specific networking. Проверить что
  CI Linux runners работают без issues.
- **Playwright timing issues на WebSocket-тестах** — headman-mark.spec
  требует «WebSocket event доставлен». Использовать `page.waitForEvent`
  + explicit timeouts, не `setTimeout`.
- **jqwik + Spring @Property + Gradle** — jqwik использует JUnit 5
  `@ParameterizedTest` альтернативу, может конфликтовать с
  `spring-boot-starter-test`. Проверить первый тест до массового
  применения.
- **k6 на Windows dev** — `k6 run` требует установки k6, нельзя через
  npm. `docs/load-testing.md` должен включить Windows install steps
  (Chocolatey/winget).
- **Coverage gate может фейлить первый PR** — baseline устанавливается
  после первого прогона. Иметь план: «PR-1 вводит gate с baseline из
  текущей coverage; PR-2+ только строже».

## Deferred в M08 (accumulated defer'ы)

### Из M01 Post-mortem
- **ContainerTestBase static lifecycle revision** — текущий `static`
  без `.stop()` через Ryuk. Dev должен `~/.testcontainers.properties`.
  M08 задача: документировать в `migration-testing.md` + `dev-setup.md`.
- **`@EnabledIfDockerAvailable`** — для smoke-тестов без Docker (локально).

### Из M03b
- **E2E Playwright admin/teacher/student/headman** — в Группе 5 M08
  как role-based golden paths.

### Из M06 Post-mortem
- **SBOM + cosign signing** — Группа 11 M08.
- **Trivy action sha-digest pin** — Группа 11 M08.
- **nginx/postgres/mongo/redis/rabbitmq digest-pin** — Группа 11 M08.

### Из M07 PLAN
- **Playwright e2e + axe-core automation** — Группа 5 M08.
- **openapi-typescript CI drift-gate** — **в M07 Группе 3** создана
  локальная генерация; **blocking gate в CI** — здесь НЕ делаем
  повторно (M07 уже setup), только проверяем что gate запускается
  в coverage CI job.
- **SBOM + cosign** — дубль из M06, покрыт Группой 11.

### Из 14-tests-audit.md
- **14 P1-1** Gateway contract tests — частично в M03a `SecurityIdorIT`,
  полный setup (compose Gateway+auth+academic) — опциональная часть
  Группы 8.
- **14 P1-2** OTP brute-force тесты — частично в M03a rate-limit;
  10 POST → 11th 429 — добавить в `OtpIntegrationTest`.
- **14 P1-3** Coverage gate — Группа 10 M08.
- **14 P1-4** Logout-lifecycle frontend tests — Группа 6 M08.
- **14 P1-5** Contract-тесты всех 14+ events — Группа 9 M08.
- **14 P1-6** gRPC proto contract-тесты — опциональная часть Группы 9.
- **14 P1-7** Mini-app TMA initData test — ACCEPT (mini-app not ready).
- **14 P1-8** CI gate на тесты — **закрыто M06 G7** (workflow_run).
- **14 P1-9** WebSocket lifecycle — Группа 9 M08.

### Из OWNER-ANSWERS P2-8/7
- **Full load suite (JMeter/Gatling)** — v0.1 future-ideas.md.

## Связь с M09 pilot coverage

- M09 ставит selective gate `latecheckin/` 70% + `handlers/` 70%
  **до** M08 global gate.
- M08 сохраняет M09 pilot gates как **stricter override** (module-level
  вместо global).
- Gradle: для pilot модулей `jacocoTestCoverageVerification` имеет
  `minimum = 0.70`, для остальных — `minimum = 0.60`.
- pytest-cov для bot: `--cov-fail-under=70` для `handlers/`,
  `--cov-fail-under=50` для остального.

## Baseline metrics (после первого прогона)

Соберём для сравнения в post-mortem:

- Java coverage (per-module): [TBD]
- TS coverage (pwa / web-panel): [TBD]
- Python coverage (bot / handlers): [TBD]
- CI total time: [TBD] (baseline до M08 = ?)
- E2E time (Playwright): [TBD]
- k6 baselines: [TBD — bulk-mark p95, geolocation-flood p95]

---
