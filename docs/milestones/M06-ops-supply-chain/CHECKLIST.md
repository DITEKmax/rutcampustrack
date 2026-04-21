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
- [x] Commit: `30a1046 feat(ops): digest-пин cadvisor + promtail (M06 Группа 3, QD4, NEW-102)`

## Группа 4 — Observability semver pins (P2-9/2, 13 P2-2) — ~1.5ч ✅

- [x] `docker-compose.prod.yml` — `grafana/loki:3.2.1` (matches promtail 3.2.1)
- [x] `docker-compose.prod.yml` — `prom/prometheus:v2.55.1`
- [x] `docker-compose.prod.yml` — `grafana/grafana:11.3.1`
- [x] `docker-compose.prod.yml` — `prom/node-exporter:v1.8.2`
- [x] `docs/runbooks/loki-major-upgrade.md` — NEW-151 (expand-contract schema procedure)
- [x] Smoke: `compose config --quiet` exit=0; нет `:latest` в prod compose кроме infra tag'ов (postgres:16, mongo:7, и т.д.)
- [x] Commit: `7ca263d feat(ops): semver-pin observability images (M06 Группа 4, P2-9/2, NEW-151)`

## Группа 5 — Renovate + Dependabot (QD4 + QD6 + NEW-105) — ~2.5ч ✅

- [x] `renovate.json` в корне — `extends: config:recommended`, auto-merge patch/pin/digest, manual minor/major, groupings (Spring Boot, Angular, React, TanStack), schedule after 22:00 MSK, loki major manual rule, cadvisor/promtail digest auto-merge
- [x] `.github/dependabot.yml` — gradle, npm × 3 (pwa/web-panel/mini-app), pip (bot), docker, github-actions (security-only)
- [x] `renovate-config-validator` — `Config validated successfully`
- [x] `docs/ci-cd.md` — NEW-105 (полный CI/CD flow: ci.yml + deploy.yml + security.yml + Renovate + Dependabot + rollback procedure)
- [x] Commit: `bab4eb7 feat(ops): Renovate + Dependabot + ci-cd.md (M06 Группа 5, QD6, NEW-105)`

## Группа 6 — Trivy + Gitleaks + SECURITY.md (QD5, NEW-103) — ~3ч ✅

- [x] `.github/workflows/security.yml` — 4 job'а: trivy-repo (fs scan + SARIF → Security tab), trivy-config (Dockerfile + compose), gitleaks (secrets), trivy-images (matrix × 11 GHCR образов, только schedule)
- [x] Gitleaks через `gitleaks/gitleaks-action@v2` — push + PR
- [x] `.pre-commit-config.yaml` — gitleaks + check-yaml/json/large-files/eof/trailing-ws
- [x] `SECURITY.md` в корне — disclosure policy с email + Telegram fallback, 24h/7d/30d/90d timeline
- [x] Smoke: `npx yaml-lint` на 3 YAML → `YAML Lint successful`
- [x] Commit: `5acffdb feat(ops): Trivy + Gitleaks CI + SECURITY.md (M06 Группа 6, QD5, NEW-103)`

## Группа 7 — CI↔deploy gate + path filters (C0-8, 13 P0-2, 13 P1-11) — ~1.5ч ✅

- [x] `.github/workflows/ci.yml` — `paths-ignore: [docs/**, .planning/**, *.md, SECURITY.md, renovate.json, .pre-commit-config.yaml]`
- [x] `.github/workflows/deploy.yml` — `on: workflow_run: [CI]: types: [completed]: branches: [main]` + `if: workflow_run.conclusion == 'success'` + `workflow_dispatch` fallback (emergency)
- [x] `DEPLOY_SHA` env (D3) — правильный SHA из `workflow_run.head_sha`, fallback `github.sha` для workflow_dispatch
- [x] Все 11 build-push tags + IMAGE_TAG в SSH используют `${{ env.DEPLOY_SHA }}`
- [x] `docs/ci-cd.md` — раздел branch protection (уже был в G5)
- [x] Smoke: `yaml-lint ci.yml deploy.yml` → `YAML Lint successful`
- [x] Commit: `7c74b3a feat(ci): workflow_run gate + DEPLOY_SHA (M06 Группа 7, C0-8, 13 P0-2/11)`

## Группа 8 — M05 defer'ы — ~4ч (3 done, 2 deferred в M07)

### 8a. Redis Jackson whitelist (M05 security #3, ~45м) ✅

- [x] `CacheConfig.java` — `BasicPolymorphicTypeValidator.builder().allowIfSubType("ru.rutcampustrack.").allowIfSubType("java.util./time/lang/math.")` вместо `LaissezFaireSubTypeValidator`
- [x] Smoke: `RbacCacheIT` зелёный; `compileJava` successful
- [x] Commit: `47039cf fix(security): Redis Jackson whitelist validator (M06 G8a, M05 defer)`

### 8b. isHeadman gRPC rate-limit (M05 security #5, ~1ч) ✅

- [x] Token bucket в `AcademicGrpcServiceImpl.isHeadman` — 120 calls/мин per userId, lock-free CAS, RL_MAX_BUCKETS=10k cap на heap
- [x] Integration test: `isHeadman_rateLimitExceeded_throwsResourceExhausted` — 120 calls пройдут, 121-й → RESOURCE_EXHAUSTED
- [x] Commit: `7208b11 fix(security): gRPC isHeadman rate-limit (M06 G8b, M05 defer)`

### 8c. Redis cache metrics `@Aspect` (M05 minor, ~1ч) ⏸ deferred в M07

- [x] Deferred — `@Aspect` на `@Cacheable` не даёт hit/miss (определяется внутри advice chain), `MetricsCacheManagerDecorator` ломает namespace-TTL. Правильный fix — `RedisCacheMeterBinder` Spring Boot 3.4+. Записано в NOTES

### 8d. GrpcClientMetricsInterceptor Timer cache + startNs (bug-hunter 5.1+5.3, ~45м) ✅

- [x] `timerCache` ConcurrentHashMap keyed by (service|method|status) — O(1) lookup вместо `Timer.builder()` per call
- [x] `startNs = System.nanoTime()` перенесён внутрь `start()` listener (correct call-duration semantics)
- [x] `shared-observability:test` зелёный
- [x] Commit: `cf983fa fix(observability): GrpcClientMetricsInterceptor Timer cache + startNs (M06 G8d)`

### 8e. /actuator/** excluded from tracing (M04 backlog, ~30м) ⏸ deferred в M07

- [x] Deferred — требует custom OTel `Sampler` bean или `ObservationRegistryCustomizer` + integration test per-service. M06-scope не соответствует
- [x] Auth-service `application.yml` comment исправлен — не обещает того чего нет (включено в `cf983fa` commit)

**Группа 8 итог:** 3/5 imlemented, 2/5 deferred с обоснованием в NOTES. Commits: `47039cf`, `7208b11`, `cf983fa`.

## Группа 9 — Audit + docs close — ~3ч ✅

- [x] `./gradlew build` финальный — зелёный (после G9 hot-patch EventSchemaRefTest)
- [x] `docker compose -f docker-compose.prod.yml config` — валидный, `${IMAGE_TAG}` резолвится
- [x] `security-auditor` агент на diff M06 (b6a0cc3..cf983fa) — 5 HIGH + 7 MEDIUM findings
- [x] Hot-patches в `e0e1881`: H1 (deploy.yml strict guards) + H2 (concurrency) + H3 (CacheConfig narrow whitelist) + EventSchemaRefTest fix
- [x] H4 (headmanBuckets + principal userId) + H5 (nginx 5-min reload) deferred с обоснованием в NOTES
- [x] MEDIUM findings (M1-M7) deferred в M07/M08 с обоснованием
- [x] `CHANGELOG.md` `[Unreleased]` — M06 entries (Added + Fixed security + Deferred)
- [x] `docs/milestones/M06-ops-supply-chain/PLAN.md` — Post-mortem секция
- [x] `docs/milestones/README.md` — M06 → ✅ 2026-04-21
- [x] `CLAUDE.md` — статус M06 → ✅ 2026-04-21
- [x] `docs/milestones/NEXT-SESSION.md` — hand-off для M07
- [ ] `git tag v0.0.0-alpha.7` — после closure commit
- [ ] Commit: `docs(m06): закрытие milestone — post-mortem + CHANGELOG + hand-off`

---

_Если задача превращается в 6+ часов работы — разрежь её. Если группа
превращается в 30+ задач — вынеси в отдельный milestone._
