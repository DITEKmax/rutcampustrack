# M06 Checklist

Атомарные задачи в порядке выполнения. Одна строка = одна единица работы
(~30 мин - 2 часа). Отмечаются `[x]` после коммита.

## Группа 1 — Dockerfile HEALTHCHECK (P2-9/1, NEW-150) — ~2ч ✅

- [x] Аудит 7 Dockerfile'ов: 6 Java на alpine-jre (`wget` уже есть), bot на debian-slim (`curl` уже есть). D1: без доп. установки `curl`, используем `wget` в Java
- [x] `auth-service/Dockerfile` — HEALTHCHECK wget /actuator/health
- [x] `academic-service/academic-app/Dockerfile` — HEALTHCHECK wget /actuator/health
- [x] `schedule-service/schedule-app/Dockerfile` — HEALTHCHECK wget /actuator/health
- [x] `attendance-service/attendance-app/Dockerfile` — HEALTHCHECK wget /actuator/health
- [x] `notification-service/notification-app/Dockerfile` — HEALTHCHECK wget /actuator/health/liveness
- [x] `api-gateway/Dockerfile` — HEALTHCHECK wget /actuator/health
- [x] `notification-bot/Dockerfile` — HEALTHCHECK curl /health (bot/__main__.py:38 реальный endpoint)
- [x] `docker-compose.prod.yml` — уже имеет `depends_on: service_healthy` matrix (M04), нет изменений
- [x] `docs/dockerfile-conventions.md` — NEW-150 runbook
- [x] Smoke: `docker buildx build --check` × 7 Dockerfile, `Check complete, no warnings found` × 7
- [x] Commit: `29bfbdc feat(ops): HEALTHCHECK в 7 Dockerfile (M06 Группа 1, P2-9/1, NEW-150)`

## Группа 2 — SHA-tagging + IMAGE_TAG параметризация (QD1, 13 P1-1/4) — ~2ч ✅

- [x] `docker-compose.prod.yml` — `image: ghcr.io/.../X:${IMAGE_TAG:-latest}` × 11 образов приложения
- [x] `deploy.yml` — передача `IMAGE_TAG=${{ github.sha }}` в SSH env (+ `.deployed-sha` marker)
- [x] `deploy.yml` mini-app build — добавить `:${{ github.sha }}` к тегам (13 P1-4)
- [x] `deploy.yml` — убрать дублирующий `up -d` после `sleep 30` (13 P1-2), заменён на `--wait --wait-timeout 120`
- [x] Smoke: `IMAGE_TAG=abc123 docker compose config` — резолвится в 11 образах; `config --quiet` exit=0
- [x] Commit: `3c84765 feat(ops): SHA-tagging + deploy.yml cleanup (M06 Группа 2, QD1, 13 P1-1/2/4)`

## Группа 3 — Digest-пин cadvisor + promtail (QD4, NEW-102) — ~1.5ч ✅

- [x] `docker buildx imagetools inspect` → cadvisor v0.49.1 `sha256:3cde6faf...`
- [x] `docker buildx imagetools inspect` → promtail 3.2.1 `sha256:bf617e9d...`
- [x] `docker-compose.prod.yml` — `cadvisor:v0.49.1@sha256:...` + `promtail:3.2.1@sha256:...`
- [x] `docs/infra/container-trust.md` — NEW-102 policy doc
- [x] Smoke: `docker compose config --quiet` exit=0
- [ ] Commit: `feat(ops): digest-пин cadvisor + promtail (M06 Группа 3, QD4, NEW-102)`

## Группа 4 — Observability semver pins (P2-9/2, 13 P2-2) — ~1.5ч

- [ ] `docker-compose.prod.yml` — `grafana/loki:3.2.1` (не `:latest`)
- [ ] `docker-compose.prod.yml` — `prom/prometheus:v2.55.1`
- [ ] `docker-compose.prod.yml` — `grafana/grafana:11.3.1`
- [ ] `docker-compose.prod.yml` — `prom/node-exporter:v1.8.2`
- [ ] `docs/runbooks/loki-major-upgrade.md` — NEW-151
- [ ] Commit: `feat(ops): semver-pin observability images (M06 Группа 4, P2-9/2, NEW-151)`

## Группа 5 — Renovate + Dependabot (QD4 + QD6 + NEW-105) — ~2.5ч

- [ ] `renovate.json` в корне — `extends: config:recommended`, `packageRules` для patch auto-merge, `schedule`, groupings (Spring Boot, Angular), `timezone`
- [ ] `.github/dependabot.yml` — gradle, npm × 3, pip (bot), docker, github-actions (security-only)
- [ ] Validate: `npx --package renovate -- renovate-config-validator`
- [ ] `docs/ci-cd.md` — NEW-105 (GitHub Actions + Renovate + Dependabot + Trivy + deploy flow)
- [ ] Commit: `feat(ops): Renovate + Dependabot config (M06 Группа 5, QD4/QD6, NEW-105)`

## Группа 6 — Trivy + Gitleaks + SECURITY.md (QD5, NEW-103) — ~3ч

- [ ] `.github/workflows/security.yml` — trivy-action (repo + image) fail on HIGH/CRITICAL + weekly cron
- [ ] `.github/workflows/security.yml` — gitleaks-action на push/PR
- [ ] `.pre-commit-config.yaml` — gitleaks hook
- [ ] `SECURITY.md` в корне — responsible disclosure, contact email/Telegram
- [ ] Smoke локально: `docker run aquasec/trivy fs .` + `docker run zricethezav/gitleaks detect --source=.`
- [ ] Commit: `feat(ops): Trivy + Gitleaks CI + SECURITY.md (M06 Группа 6, QD5, NEW-103)`

## Группа 7 — CI↔deploy gate + path filters (C0-8, 13 P0-2, 13 P1-11) — ~1.5ч

- [ ] `.github/workflows/ci.yml` — `paths-ignore: ['docs/**', '.planning/**', '*.md']`
- [ ] `.github/workflows/deploy.yml` — `on: workflow_run: workflows: [CI]: types: [completed]: branches: [main]` + `if: github.event.workflow_run.conclusion == 'success'`
- [ ] `docs/ci-cd.md` — раздел «branch protection setup» (manual GitHub UI steps)
- [ ] Commit: `feat(ci): workflow_run gate + paths-ignore (M06 Группа 7, C0-8, 13 P0-2/11)`

## Группа 8 — M05 defer'ы — ~4ч

### 8a. Redis Jackson whitelist (M05 security #3, ~45м)

- [ ] `CacheConfig.java` — `BasicPolymorphicTypeValidator.builder().allowIfSubType("ru.rutcampustrack.")` вместо `LaissezFaireSubTypeValidator`
- [ ] Smoke: `RbacCacheIT` + `@Cacheable("subject")` IT зелёные

### 8b. isHeadman gRPC rate-limit (M05 security #5, ~1ч)

- [ ] Решить где — на edge Gateway или в `AcademicGrpcServiceImpl`. Если Gateway — ссылка на M03a rate-limit Redis. Если gRPC-уровень — token bucket через `Bucket4j` per `userId+groupId`
- [ ] Implement + unit-тест

### 8c. Redis cache metrics `@Aspect` (M05 minor, ~1ч)

- [ ] `shared-observability/RedisCacheMetricsAspect` — `@Around("@annotation(Cacheable)")` + `Timer` histogram с тегом `cache`
- [ ] Alternate path: `RedisCacheManager` builder-hook `.withCacheConfiguration(..., CacheStatistics.Simple)`
- [ ] Проверить не ломает namespace-specific TTL (регрессия из M05 G3)

### 8d. GrpcClientMetricsInterceptor Timer cache + startNs (bug-hunter 5.1+5.3, ~45м)

- [ ] `GrpcClientMetricsInterceptor` — `ConcurrentHashMap<MethodDescriptor, Timer>` вместо `meterRegistry.timer()` per call
- [ ] `start()` — захватывать `startNs` в закрывающем scope (сейчас — глобальное поле, data race)
- [ ] Unit-тест на concurrent calls

### 8e. /actuator/** excluded from tracing (M04 backlog, ~30м)

- [ ] `SpanFilter` / `Sampler` customizer — skip spans для uri match `/actuator/**`
- [ ] Проверить Tempo UI: прошёл 1 `/actuator/health` → не появилась span

- [ ] Commit: `fix(defer): M05 hot-patches — Redis whitelist + gRPC RL + Timer cache + actuator tracing (M06 Группа 8)`

## Группа 9 — Audit + docs close — ~3ч

- [ ] `./gradlew build` финальный — зелёный
- [ ] `docker compose -f docker-compose.prod.yml config` — валидный
- [ ] `Explore` / `bug-hunter` на diff M06 (от `e03e74b` до финального G8 commit)
- [ ] `security-auditor` на новый `security.yml`, `renovate.json`, `Dockerfile` HEALTHCHECK
- [ ] Hot-patches если найдутся — отдельным commit
- [ ] `CHANGELOG.md` `[Unreleased]` — M06 entries (Added + Changed + Security)
- [ ] `docs/milestones/M06-ops-supply-chain/PLAN.md` — Post-mortem секция
- [ ] `docs/milestones/README.md` — M06 → ✅ + дата
- [ ] `CLAUDE.md` — статус M06 → ✅ + дата
- [ ] `docs/milestones/NEXT-SESSION.md` — hand-off для M07
- [ ] `git tag v0.0.0-alpha.7` на финальном commit'е M06
- [ ] Commit: `docs(m06): закрытие milestone — post-mortem + CHANGELOG + hand-off`

---

_Если задача превращается в 6+ часов работы — разрежь её. Если группа
превращается в 30+ задач — вынеси в отдельный milestone._
