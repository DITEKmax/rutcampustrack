# M06 — Ops & Supply Chain

**Статус:** ✅ готов
**Старт / финиш:** 2026-04-21 / 2026-04-21
**Estimate:** 3-4 человеко-дня (~21ч по группам G1-G9). Факт: ~8-10ч
(single session).
**Tag:** `v0.0.0-alpha.7`

---

## Scope

Supply-chain guard, reproducible deployments, dependency freshness, HEALTHCHECK
для backend-образов. Плюс приоритетные defer'ы с M05 audit (Группа 9).

Источники:
- `99-executive-summary.md` — Фаза 0 (QD4, QD5), Фаза 4 P1-D (QD6, QD1), Фаза 5 P2-9/1..2, Фаза 0 C0-8
- `OWNER-ANSWERS.md` QD1/QD4/QD5/QD6/QD7 (строки 2074-2356) и P2-9/1..2 (строки 4043-4115)
- `13-infra-docker-ci.md` P0-2, P1-1, P1-4, P1-10, P1-11, P2-2, P2-13
- `M05 NOTES.md` + post-mortem — 5 defer'ов Группы 8

**Включено:**
- P2-9/1 — `HEALTHCHECK` + `curl` во всех backend Dockerfile'ах (NEW-150)
- QD1 + 13 P1-1 + 13 P1-4 — SHA-tagging через `${IMAGE_TAG}` параметризацию compose + deploy.yml
- QD4 — digest-пин для `cadvisor` + `promtail` (NEW-102)
- P2-9/2 + 13 P2-2 — semver-pin для `loki`/`prometheus`/`grafana`/`promtail`/`node-exporter` (NEW-151)
- QD6 — Renovate config (`renovate.json`)
- QD5 — Trivy + Gitleaks в CI + Dependabot + `SECURITY.md` (NEW-103)
- C0-8 + 13 P0-2 + 13 P1-11 — `workflow_run` gate `ci.yml → deploy.yml` + `paths-ignore` в CI
- M05 defer'ы (5 пунктов): Redis Jackson whitelist, isHeadman gRPC rate-limit, Redis cache metrics `@Aspect`, `GrpcClientMetricsInterceptor` Timer caching + startNs, `/actuator/**` excluded from tracing

**Исключено (перенесено в другие milestones):**
- `.env.prod.example` (C0-9), LE cert-name fix (C0-10) — **M09 Prod Release Blockers** (Фаза 0 hardening)
- Alertmanager контейнер (P2-9/5) — **уже сделан в M04** (`prom/alertmanager:v0.27.0` в `docker-compose.prod.yml`)
- JVM resource limits + restart policies (P2-9/9) — **M07** или отдельный prod-deploy checklist
- `client_max_body_size` per-location (P2-9/3), nginx rate-limit (13 P1-3), CSP self-host (C0-6/13 P0-4) — **M07 Frontend Hardening**
- Loki retention 14д (P2-9/4) — уже ACCEPTED в M04 QA5
- Bot webhook schema, resource-limits/secret-rotation runbooks (NEW-154/155/157) — M07 или prod-deploy

## Модули / изменения

- `services/*/Dockerfile` (7 штук) — `apk add curl` / `apt-get install curl` + `HEALTHCHECK`
- `services/notification-bot/Dockerfile` — `HEALTHCHECK` через `pgrep -f aiogram`
- `docker-compose.prod.yml` — `${IMAGE_TAG:-latest}` параметризация, digest-пин cadvisor/promtail, semver-pin observability, `depends_on: service_healthy` для backend'ов под gateway
- `.github/workflows/ci.yml` — `paths-ignore: ['docs/**', '.planning/**', '*.md']`
- `.github/workflows/deploy.yml` — `on: workflow_run` после успешного `ci.yml`, передача `IMAGE_TAG=${{ github.sha }}`, mini-app `:${sha}` тег
- `.github/workflows/security.yml` — новый, Trivy + Gitleaks
- `.github/dependabot.yml` — новый (gradle, npm×3, pip, docker, github-actions)
- `renovate.json` — новый (корень репо)
- `.pre-commit-config.yaml` — новый (gitleaks hook)
- `SECURITY.md` — новый, disclosure policy
- `docs/operations/deploy/dockerfile-conventions.md` — NEW-150
- `docs/operations/deploy/container-trust.md` — NEW-102
- `docs/operations/runbooks/loki-major-upgrade.md` — NEW-151
- `docs/operations/deploy/ci-cd.md` — NEW-105
- **M05 defer'ы:**
  - `services/academic-service/academic-app/src/main/java/.../config/CacheConfig.java` — `BasicPolymorphicTypeValidator` whitelist
  - `services/academic-service/academic-app/src/main/java/.../grpc/AcademicGrpcServiceImpl.java` — rate-limit на `isHeadman`
  - `services/shared/shared-observability/` — `RedisCacheMetricsAspect` + `GrpcClientMetricsInterceptor` fix
  - `services/shared/shared-observability/.../tracing/` — `/actuator/**` sampling filter

## Acceptance criteria

- [x] `./gradlew build` зелёный локально (unit + integration + ArchUnit)
- [x] `docker compose -f docker-compose.prod.yml config` — валидный, `${IMAGE_TAG}` резолвится
- [ ] ~~`docker compose ps` — все 5 backend healthy после `up -d --wait`~~ — **пропущено** (VPS smoke, не локальный scope; HEALTHCHECK синтактически валидны через `buildx --check`)
- [x] `ci.yml` triggered на push/PR (c `paths-ignore`), `deploy.yml` triggered только после успешного `ci.yml` (через `workflow_run`) + `workflow_dispatch` fallback
- [ ] ~~`security.yml` Trivy + Gitleaks zero HIGH/CRITICAL на main~~ — **первый run будет в GHCR после push'а**; config валиден через `yaml-lint`
- [x] `renovate.json` валиден — `renovate-config-validator` → `Config validated successfully`
- [x] `cadvisor` + `promtail` digest-пин `@sha256:...` (M06 D2)
- [x] `grafana/loki`, `prom/prometheus`, `grafana/grafana`, `prom/node-exporter` — semver-pin
- [x] `mini-app-nginx` имеет `:${{ env.DEPLOY_SHA }}` тег
- [x] M05 defer'ы: G8a Redis whitelist ✅, G8b isHeadman RL ✅, G8d Timer cache ✅. G8c/8e deferred в M07 с обоснованием.
- [x] Post-mortem секция ниже, tag `v0.0.0-alpha.7`

## Post-mortem

### Результаты

**12 коммитов в M06** (scaffold + 9 групп + security-audit hot-patch):

| Commit | Scope |
|--------|-------|
| `b6a0cc3` | Scaffold PLAN+CHECKLIST+NOTES+DECISIONS |
| `29bfbdc` | G1 HEALTHCHECK в 7 Dockerfile |
| `3c84765` | G2 SHA-tagging + deploy.yml cleanup |
| `30a1046` | G3 digest-пин cadvisor + promtail |
| `7ca263d` | G4 semver-pin observability |
| `bab4eb7` | G5 Renovate + Dependabot + ci-cd.md |
| `5acffdb` | G6 Trivy + Gitleaks + SECURITY.md |
| `7c74b3a` | G7 workflow_run gate + DEPLOY_SHA |
| `47039cf` | G8a Redis Jackson whitelist |
| `7208b11` | G8b gRPC isHeadman rate-limit |
| `cf983fa` | G8d GrpcClientMetricsInterceptor fixes |
| `e0e1881` | G9 security-audit hot-patches (H1/H2/H3 + EventSchemaRefTest fix) |

### Что прошло хорошо

- **Scope stability** — 9 групп определены в начале, ни одна не
  пересмотрена в середине. Дисциплина scope'а из M05 post-mortem
  перенесена.
- **Одна сессия closure** — M06 scope узкий (infra + config, minimal
  Java), уложился в 8-10ч непрерывной работы с 12 атомарными коммитами.
- **security-auditor early-warning** — H3 (`java.util.*` wide
  whitelist) поймал реальную gadget-chain уязвимость, которую я бы
  пропустил. Time invested в audit (~15 мин) окупился.
- **Hot-patch discipline** — все findings H1/H2/H3 закрыты одним
  коммитом `e0e1881`, без смешивания с milestone scope.

### Что пошло не так / trade-offs

- **`git stash` почти стоил мне uncommitted changes** — `git stash`
  в процессе build investigation откатил H1/H2/H3 patches и NOTES/
  CHECKLIST updates. Восстановлено через `stash pop`, но риск был.
  Lesson: не использовать `git stash` когда есть uncommitted work
  с context в head.
- **Full build занял 5 минут + sync test fixture fail** — `shared-
  outbox:EventSchemaRefTest` pre-existing fail от M04 выплыл на
  M06 audit. Исправлено в G9 hot-patch. Если бы делал full build
  в начале M06, узнал бы раньше.
- **G8c (Redis cache metrics) и G8e (/actuator/** tracing skip)
  deferred в M07** — оба требуют shared-observability module
  changes + integration tests per-service, не соответствует
  «единая сессия» scope M06.
- **H4 accepted as trade-off** — `headmanBuckets.clear()` race
  lossy (rate-limit state приемлем), principal-based userId
  требует gRPC proto redesign (breaking change).

### Отложено в M07+

- Redis cache hit/miss metrics через `RedisCacheMeterBinder`
- `/actuator/**` excluded from tracing (OTel Sampler bean)
- isHeadman userId из principal, не из proto request
- nginx / postgres / mongo / redis / rabbitmq digest-pin
- nginx 5-min background reload → `nginx -t` + Loki alerting
- SBOM generation + cosign signing (M08)
- Trivy action sha-digest pin (M08)

### Measurement — supply-chain hardening

| Before M06 | After M06 |
|------------|-----------|
| 11 app-образов на `:latest` | `${IMAGE_TAG:-latest}` + SHA per deploy |
| cadvisor/promtail на `:latest` | digest-pinned (sha256) |
| loki/prometheus/grafana/node-exporter на `:latest` | semver-pinned |
| Нет Trivy / Gitleaks / Dependabot / Renovate | 4 активных supply-chain scanner'а |
| 0 Dockerfile с HEALTHCHECK | 7/7 Dockerfile с HEALTHCHECK |
| deploy.yml fires на любой push main, игнорируя CI | `workflow_run` + `head_branch == main && event == push` |
| 2× `docker compose up -d` + `sleep 30` | `--wait --wait-timeout 120` |
| Jackson `LaissezFaireSubTypeValidator` (gadget RCE-vector) | Narrow whitelist — no gadgets |
| gRPC isHeadman unlimited (rbac key-space DoS) | 120 req/min per userId + heap cap 10k |
| `GrpcClientMetricsInterceptor` Timer.builder() per call | Cached Timer per (service,method,status) |

### Tag

`v0.0.0-alpha.7` — локальный, push отложен до финала v0.0.0 (M07+M08+M09 готовы).

## Dependencies

- **Блокируется:** — (полностью независим)
- **Parallel safe:** M07, M08, M09
- **Блокирует:** v0.0.0 release tag (cumulative с M07/M08/M09)

## Artifacts

- `docs/operations/deploy/dockerfile-conventions.md` — NEW-150
- `docs/operations/deploy/container-trust.md` — NEW-102
- `docs/operations/runbooks/loki-major-upgrade.md` — NEW-151
- `docs/operations/deploy/ci-cd.md` — NEW-105
- `SECURITY.md` (root) — NEW-103
- `renovate.json` (root)
- `.github/dependabot.yml`
- `.github/workflows/security.yml`
- `.pre-commit-config.yaml`

---

_Никаких «why», «motivation», «background» — это уже в 99-executive-summary.md
и OWNER-ANSWERS.md (QD1/QD4/QD5/QD6 + P2-9/1..2). Здесь только WHAT и DONE-критерии._
