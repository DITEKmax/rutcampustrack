# M06 — Ops & Supply Chain

**Статус:** ⏳ в работе
**Старт / финиш:** 2026-04-21 / —
**Estimate:** 3-4 человеко-дня (~21ч по группам G1-G9)

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
- `docs/dockerfile-conventions.md` — NEW-150
- `docs/infra/container-trust.md` — NEW-102
- `docs/runbooks/loki-major-upgrade.md` — NEW-151
- `docs/ci-cd.md` — NEW-105
- **M05 defer'ы:**
  - `services/academic-service/academic-app/src/main/java/.../config/CacheConfig.java` — `BasicPolymorphicTypeValidator` whitelist
  - `services/academic-service/academic-app/src/main/java/.../grpc/AcademicGrpcServiceImpl.java` — rate-limit на `isHeadman`
  - `services/shared/shared-observability/` — `RedisCacheMetricsAspect` + `GrpcClientMetricsInterceptor` fix
  - `services/shared/shared-observability/.../tracing/` — `/actuator/**` sampling filter

## Acceptance criteria

- [ ] `./gradlew build` зелёный локально и в CI (unit + integration + ArchUnit)
- [ ] `docker compose -f docker-compose.prod.yml config` — валидный, `${IMAGE_TAG}` резолвится
- [ ] `docker compose ps` — все 5 backend контейнеров в статусе `healthy` после `up -d --wait` (локальный smoke)
- [ ] `ci.yml` triggered на push/PR, `deploy.yml` triggered только после успешного `ci.yml` (через `workflow_run`)
- [ ] `.github/workflows/security.yml` — Trivy + Gitleaks zero HIGH/CRITICAL на main
- [ ] `renovate.json` валиден (прогнан через `npx --package renovate -- renovate-config-validator`)
- [ ] `cadvisor` + `promtail` в `docker-compose.prod.yml` — digest-пин `@sha256:...`
- [ ] `grafana/loki`, `prom/prometheus`, `grafana/grafana`, `grafana/promtail`, `prom/node-exporter` — semver-pin, не `:latest`
- [ ] `mini-app-nginx` имеет `:${{ github.sha }}` тег в `deploy.yml`
- [ ] M05 defer'ы: Redis whitelist применён, gRPC `isHeadman` rate-limit работает, Timer cache'иется, `/actuator/**` не трейсится
- [ ] Post-mortem секция в этом PLAN.md, tag `v0.0.0-alpha.7`

## Dependencies

- **Блокируется:** — (полностью независим)
- **Parallel safe:** M07, M08, M09
- **Блокирует:** v0.0.0 release tag (cumulative с M07/M08/M09)

## Artifacts

- `docs/dockerfile-conventions.md` — NEW-150
- `docs/infra/container-trust.md` — NEW-102
- `docs/runbooks/loki-major-upgrade.md` — NEW-151
- `docs/ci-cd.md` — NEW-105
- `SECURITY.md` (root) — NEW-103
- `renovate.json` (root)
- `.github/dependabot.yml`
- `.github/workflows/security.yml`
- `.pre-commit-config.yaml`

---

_Никаких «why», «motivation», «background» — это уже в 99-executive-summary.md
и OWNER-ANSWERS.md (QD1/QD4/QD5/QD6 + P2-9/1..2). Здесь только WHAT и DONE-критерии._
