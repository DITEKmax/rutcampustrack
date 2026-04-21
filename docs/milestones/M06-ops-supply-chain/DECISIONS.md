# M06 Decisions (Micro-ADR)

Фиксируй каждое решение которое не описано в OWNER-ANSWERS, но нужно
для реализации. Формат: `## YYYY-MM-DD — D{N}: короткий заголовок`,
дальше 3-10 строк: что выбрано, почему, альтернативы.

---

_Открытых развилок на старт M06 нет — scope зафиксирован в
OWNER-ANSWERS QD1/QD4/QD5/QD6 (строки 2074-2356) + P2-9/1..2
(строки 4043-4115). M05 defer'ы — из audit findings (Группа 8)._

---

## 2026-04-21 — D1: HEALTHCHECK через `wget`, без установки `curl`

**Выбрано:** в Dockerfile'ах 7 Java-сервисов —
`HEALTHCHECK CMD wget -qO- http://localhost:${PORT}/actuator/health || exit 1`,
**без** дополнительной установки `curl`.

**Почему:** `eclipse-temurin:21-jre-alpine` (runtime-образ всех 7
сервисов) уже содержит `wget` (busybox). OWNER-ANSWERS P2-9/1 (строка
4056) рекомендует `apk add --no-cache curl`, но это mostly для
удобства debug exec'ов (строка 4052). Использование `wget` даёт те же
функции в HEALTHCHECK без дополнительного слоя (~7MB + 20+ transitive
deps).

**Плюсы:**
- Меньше образ (не устанавливаем curl + OpenSSL userland).
- Последовательность: `docker-compose.prod.yml` уже использует
  `wget -qO- http://localhost:PORT/actuator/health` во всех 7
  healthcheck'ах — Dockerfile-директива совпадает 1:1.
- Меньше supply-chain-поверхность (trivy scan).

**Минусы:**
- `curl` удобнее для debug через `docker exec -it ... sh` (JSON
  parsing, `-v`, timing). Но для этого можно временно
  `apk add --no-cache curl` внутри `docker exec` сессии либо
  использовать bundled `java net.http` через script.

**Последствия:**
- Docker image без `curl` на ~7MB меньше.
- notification-bot остаётся с `curl` (python:3.12-slim, уже установлен
  в Dockerfile:3, health endpoint http://localhost:8081/health).
- `docs/dockerfile-conventions.md` (NEW-150) зафиксирует правило
  «HEALTHCHECK через wget для Java, curl только если он уже в base
  image».

**Альтернативы отклонены:**
- (a) `apk add --no-cache curl` — overkill для HEALTHCHECK.
- (b) `HEALTHCHECK` через `java -cp app:dependencies HealthCheck` —
  самописный класс на JarMode — overengineering.

---

## 2026-04-21 — D2: Digest-пин только для privileged/socket-containers

**Выбрано:** `cadvisor` и `promtail` — `tag@sha256:...` формат (manifest-
list digest через `docker buildx imagetools inspect`). Остальные
observability-образы (loki/prometheus/grafana/alertmanager/tempo) и
infra (postgres/mongo/redis/rabbitmq) — tag-only pin с Renovate.

**Почему:** OWNER-ANSWERS QD4 (строки 2186-2223):
- `cadvisor` — `privileged: true` + mounts `/:/rootfs:ro`, `/sys:/sys:ro`,
  `/var/run:/var/run:ro`, `/var/lib/docker:/var/lib/docker:ro`. Supply-
  chain compromise = полный контроль над host.
- `promtail` — mounts `/var/run/docker.sock:/var/run/docker.sock:ro`.
  Docker socket read-only, но всё равно может перечислить все контейнеры,
  env-переменные, получить реестры secrets. Compromise = доступ к docker
  daemon.

Для остальных supply-chain risk меньше (no privileged, no sensitive
mounts), pin по semver tag + Renovate auto-merge patch — достаточная
защита.

**Формат digest:** **manifest-list** (multi-arch) digest, не platform-
specific. Пример: `gcr.io/cadvisor/cadvisor:v0.49.1@sha256:3cde6faf...`.
`docker pull` разрешает правильный per-platform manifest автоматически;
используя platform-specific digest (`sha256:524779a2...` для amd64),
`docker pull` на arm64 host'е получит wrong image.

**Versions pinned сейчас:**
- `gcr.io/cadvisor/cadvisor:v0.49.1@sha256:3cde6faf0791ebf7b41d6f8ae7145466fed712ea6f252c935294d2608b1af388`
- `grafana/promtail:3.2.1@sha256:bf617e9d67e80247a59f717f9c1ad388d7d32dc0a1d29abd5799516d15e0a9b5`

**Bump-процесс:** Renovate (QD6, Группа 5) создаст PR с обновлённым
digest. Ручная ревизия CHANGELOG'а image'а. Merge → auto-deploy на
main.

**Альтернативы отклонены:**
- (a) Digest pin для **всех** образов — overhead в мелких bump'ах
  Grafana/Loki/Prometheus без compensating security value.
- (b) Replace cadvisor на prometheus node-exporter host-metrics — node-
  exporter не собирает per-container cpu/mem. OpenTelemetry collector
  hostmetrics receiver — v0.1 candidate.
- (c) Keep `:latest` + Trivy weekly scan — supply-chain race окно между
  pull и scan может 7 дней.

---

## 2026-04-21 — D3: `DEPLOY_SHA` env-variable вместо `github.sha` в workflow_run

**Выбрано:** `env: DEPLOY_SHA: ${{ github.event.workflow_run.head_sha || github.sha }}`
на top-level deploy.yml, все build/deploy steps используют `${{ env.DEPLOY_SHA }}`.

**Почему:** в GitHub Actions `workflow_run`-triggered workflow'е
`github.sha` = SHA of **default branch HEAD at workflow creation**,
а **НЕ** commit SHA который триггернул upstream CI workflow. Это
subtle bug: если второй commit успел попасть в main пока первый CI
ещё шёл, `github.sha` deploy.yml'а == commit #2, хотя build-push
должен тэгировать commit #1.

Корректный SHA — `github.event.workflow_run.head_sha`. Fallback
`|| github.sha` нужен для `workflow_dispatch` event (там
workflow_run undefined).

**Реализация:**
```yaml
env:
  DEPLOY_SHA: ${{ github.event.workflow_run.head_sha || github.sha }}
```

Все 11 `tags: ...:${{ github.sha }}` заменены на `${{ env.DEPLOY_SHA }}`.
`IMAGE_TAG` для VPS deploy — аналогично.

**Альтернативы отклонены:**
- (a) `workflow_call` + передача SHA через inputs — требует изменения
  ci.yml, увеличивает coupling.
- (b) Repository_dispatch event — усложняет trigger chain, теряет
  GitHub UI integration.
- (c) `workflow_run.head_sha` inline в каждом `tag` — duplication,
  легко забыть при добавлении нового сервиса.

**Риск:** `workflow_dispatch` без upstream CI — теоретически может
вернуть default branch SHA вместо конкретного. Addmitigation:
`workflow_dispatch.inputs.reason` обязателен — human review.
