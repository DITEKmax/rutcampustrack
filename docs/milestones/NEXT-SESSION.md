# Промпт для следующей сессии

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Этого достаточно —
Opus сам откроет файлы и поймёт где мы остановились.

---

**M08 Test Infrastructure ЗАВЕРШЁН (2026-04-23, tag `v0.0.0-alpha.9`).**
Начинаем **M09 Prod Release Blockers** — Фаза 3 из `99-executive-summary.md`.
План — `docs/milestones/M09-prod-release-blockers/PLAN.md`.

Локальных коммитов ahead origin: **~52** (25 pre-M08 + 27 M08). Tags
`v0.0.0-alpha.2..9` локальные. Push отложен до явного go.

**Старт следующей сессии — дословно:**

> Читаю NEXT-SESSION → CHECKLIST M09 → PLAN M09. Стартую с **Группы 1
> (Quick wins, ~2ч)**: `MessageDigest.isEqual` в OtpService,
> удаление `AttendanceService.cleanupOrphans` + `@PostConstruct`,
> landing deep-link кнопки `/login` → `t.me/<bot>`. Атомарные коммиты
> по каждому fix.

---

## M09 scope (из PLAN.md)

**Группы** (9 групп, ~7-8 человеко-дней estimate):

| # | Группа | Суть |
|---|--------|------|
| G1 | Quick wins | MessageDigest + cleanupOrphans delete + landing deep-link |
| G2 | OTP через RabbitMQ | 08 P0-2: убрать `code` из HTTP body, publisher → bot consumer |
| G3 | latecheckin тесты | 14 P0-1: unit + IT + jacoco 70% gate активация |
| G4 | bot handlers тесты | Pytest coverage для handlers/ → 70% pilot |
| G5 | Event unification | lesson.cancelled / excuse.{approved,rejected} publishers |
| G6 | Prod-deploy-checklist | 13 P0-3: runbook для VPS release |
| G7 | Secret rotation | JWT keys + DB passwords + GRPC_SECRET runbook |
| G8 | Resource limits | compose.prod CPU/mem limits per service |
| G9 | Финализация | build + check + tag v0.0.0-alpha.10 + post-mortem |

**Правила (без изменений с M05-M08):**

- **Русский язык** в отчётах / NOTES / ответах.
- **Ветка `dev`**. Push на `main`/`origin` — только с явного `go`.
- Не звать `gsd-*` агентов. `Explore` для «найти все X»,
  `bug-hunter` / `code-reviewer` / `security-auditor` — в G9 audit.
- Surprise → NOTES.md + спросить до продолжения.
- Micro-решение → DECISIONS.md.
- Закрыл пункт CHECKLIST → `[x]` через Edit (commit hash в описании).
- **Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — ложные.**
- `CHANGELOG.md [Unreleased]` обновляй при значимых изменениях (G9).

## M08 хэндовер-факты для M09

### Coverage gate активен

- `./gradlew check` запускает `jacocoTestCoverageVerification`
  для всех модулей с тестами.
- **Per-module ratchet floor** в root `build.gradle.kts` —
  coverage не может упасть ниже текущего floor, но M09 в Группе 3
  **должна поднять attendance-app ratchet floor** с 14% до
  реального значения после латчекин-тестов (ожидание: 25-30% после
  LateCheckinServiceTest + LateCheckinControllerIT).
- `attendance-app/build.gradle.kts` — placeholder-rule для
  `latecheckin.*` 70% LINE с `isEnabled = false`. M09 G3 включает:
  `isEnabled = true` после добавления тестов.
- PWA vitest threshold ratchet 38% lines / 47% functions. M09+
  поднимает по мере добавления тестов.
- Web-panel vitest 50% target (actual 78.1%).
- pytest-cov 50% gate (actual 70.5%). M09 G4 добавит отдельный CI
  step `pytest --cov=bot/handlers --cov-fail-under=70` для pilot.

### Supply-chain активен

- Digest-pin 13 images в `docker-compose.prod.yml`.
- deploy.yml: `sbom-sign` job + verify перед SSH. Без подписи — red.
- Cosign verify команда в `docs/runbooks/image-signing-verification.md`.
- Trivy SHA-pin (v0.36.0).
- Renovate monthly digest-bump rule + `pinDigests: true` для GH Actions.

### Diff-cover hard-fail

- `.github/workflows/coverage.yml` — `exit 1` активен.
- PR с diff-cover < 80% на changed lines → red CI.
- Первый M09 PR будет проверкой.

### M08 defer'ы явно ожидают M09

1. **Playwright CI job `e2e-tests`** — требует stable staging.
   Добавить в deploy.yml после G6 (prod-deploy-checklist) стабилизирует
   VPS.
2. **`SecurityIdorIT`** — файл не существует (NEW-31 M03a забытый).
   M09 создаёт при расширении ролевой IDOR-защиты.
3. **`@MockitoBean` → in-process gRPC** — defer v0.1, не М09.
4. **k6 baseline** — release-engineer на VPS staging перед релизом.

## Действия, ожидающие `go` пользователя

1. `git push origin dev` — **~52 коммитов** ahead origin.
2. `git push origin --tags` — **8 tags** (`v0.0.0-alpha.2..9`) локальные.
3. Старт Группы 1 M09 в новой сессии.

---

## История предыдущих milestone (архив)

M01 Shared Foundations ✅ 2026-04-19
M02 Reliable Eventing ✅ 2026-04-19
M03a Internal JWT + Rate-limit ✅ 2026-04-20
M03b Secure Boundaries Part B ✅ 2026-04-20
M04 Observability ✅ 2026-04-20
M05 Performance ✅ 2026-04-21
M06 Ops & Supply Chain ✅ 2026-04-21
M07 Frontend Hardening ✅ 2026-04-22 (tag `v0.0.0-alpha.8` локальный)
**M08 Test Infrastructure ✅ 2026-04-23 (tag `v0.0.0-alpha.9` локальный).**
**M09 Prod Release Blockers ⏳ не начат, продолжать с G1.**
M10 Notification History ⬜
M11 OpenAPI Polish ⬜
M12 Auth Contract-first Refactor ⬜

Dependency graph и полный roadmap — `docs/milestones/README.md`.

---

## M08 итог (tag `v0.0.0-alpha.9`)

**12/12 групп закрыто**, ~27 коммитов, календарно 2 дня (2026-04-22..23).

| Группа | Commit | Ключевое |
|--------|--------|----------|
| G1 Testing conventions | 8 commits | 31 rename + Gradle split + ArchUnit |
| G2 Testcontainers hybrid | `68a9ecb` | Reuse × 8 + 41-mock audit |
| G3 Flyway MigrationIT | `3781edf` | 3 templates × 2 services |
| G4 Golden + Clock | `f61537b`+`3a38fc1` | 22+12 golden cases + Clock DI |
| G5 Playwright E2E | `5191098` | 8 specs + axe + smoke-prod |
| G6 Frontend unit | `6df30a6` | P0-4 regression guards |
| G7 Load tests | `4730dec` | k6 scripts + baseline шаблон |
| G8 Security contracts | `bef5c0a` | GrpcSecretFailFast + TmaIT + SameSite |
| G9 Event + WS contract | `b2ae934` | 40 events + STOMP + reconnect |
| G10 Coverage gate | `3de786b` | JaCoCo + Vitest + pytest-cov + diff-cover |
| G11 Supply chain | `2c17327` | SBOM + cosign keyless + digest-pin 13 images |
| G12 Финализация | `bb7b20b` | Ratchet gate + hard-fail + tag alpha.9 |

Подробности: `docs/milestones/M08-test-infrastructure/{PLAN,CHECKLIST,NOTES,DECISIONS}.md`.
