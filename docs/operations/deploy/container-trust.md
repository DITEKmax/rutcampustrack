# Container Trust Policy

Политика пина Docker-образов по supply-chain risk. Введено в M06
Группа 3 (OWNER-ANSWERS QD4 + P2-9/2, NEW-102).

## TL;DR

| Категория | Примеры | Pin-стратегия | Renovate |
|-----------|---------|---------------|----------|
| **Privileged / socket** | `cadvisor`, `promtail` | `tag@sha256:...` (multi-arch manifest digest) | ✅ digest bump |
| **Observability stack** | `loki`, `prometheus`, `grafana`, `alertmanager`, `tempo`, `node-exporter` | `tag` (semver) | ✅ minor auto-merge |
| **Infra (БД/брокер)** | `postgres`, `mongo`, `redis`, `rabbitmq` | `tag` (semver) | ✅ minor auto-merge (major — manual) |
| **Base images (Java/Python)** | `eclipse-temurin:21-jre-alpine`, `python:3.12-slim` | `tag` | ✅ patch auto-merge |
| **Application images (собственные)** | `ghcr.io/ditekmax/rutcampustrack/*` | `${IMAGE_TAG:-latest}` (SHA из deploy.yml) | — (built from source) |
| **Certbot** | `certbot/certbot` | `tag` | ✅ patch auto-merge |

## Почему именно такое разделение

### Digest-пин для cadvisor + promtail

Оба контейнера имеют **host-level access**:

- **cadvisor** (`docker-compose.prod.yml:401`):
  - `privileged: true`
  - `volumes: /:/rootfs:ro, /sys:/sys:ro, /var/run:/var/run:ro, /var/lib/docker:/var/lib/docker:ro`
  - Compromise → чтение секретов из других контейнеров через `/var/lib/docker`, запуск произвольных команд через `/sys`/cgroup.
- **promtail** (`docker-compose.prod.yml:527`):
  - `volumes: /var/run/docker.sock:/var/run/docker.sock:ro`
  - Compromise → enumerate containers (+env, +secrets), attach к запущенным.

Supply-chain инциденты последних лет (codecov, solarwinds, npm
packages) показывают: публичные реестры не гарантируют целостность
tag-манипуляций. Для этих двух образов `tag@sha256:` — минимальная
защита.

### Semver tag для остальных

Observability + БД не имеют privileged mounts. Compromised loki-image
→ украл логи (которые и так не содержат PII после `MaskingConverter`).
Compromised grafana → Admin UI, но Grafana в private-network, закрыт
basic-auth через корневой nginx.

### Почему application images через `${IMAGE_TAG}`

Собственные образы из `ghcr.io/ditekmax/rutcampustrack/*` строятся
в `deploy.yml:build-push` из исходников. SHA коммита и есть
«digest» в семантическом плане — не может быть подменён без
изменения git history.

Строго говоря, можно было pin'ить `ghcr.io/...@sha256:...`, но это
требует dynamic substitution в compose (pull → inspect → write). SHA-tag
достаточен, потому что:
1. `ghcr.io` tag перезаписывается только через `docker push` после
   GitHub Actions build.
2. Build-push использует `${{ secrets.GHCR_TOKEN }}` — compromised только
   через GitHub repo compromise.
3. GHCR retention хранит исторические образы по SHA даже если `:latest`
   перезапишется — rollback возможен.

## Формат digest

Использовать **manifest-list digest** (multi-arch), не platform-specific.

```bash
docker buildx imagetools inspect gcr.io/cadvisor/cadvisor:v0.49.1
# Name:      gcr.io/cadvisor/cadvisor:v0.49.1
# MediaType: application/vnd.docker.distribution.manifest.list.v2+json
# Digest:    sha256:3cde6faf0791ebf7b41d6f8ae7145466fed712ea6f252c935294d2608b1af388
#                    ^ эту используем в compose
```

**Не использовать** `docker manifest inspect ...` первый digest в
списке — это platform-specific (linux/amd64) и не работает на arm64
хостах.

## Bump-процесс

1. Renovate (QD6) создаёт PR с обновлённым digest.
2. Автор PR / ревьюер:
   - проверяет CHANGELOG образа по tag (`cadvisor v0.49.1 → v0.49.2`) —
     breaking changes, CVE-фиксы;
   - если minor+ — запускает integration-тест вручную (`docker compose
     up -d --wait`).
3. Merge → `deploy.yml` auto-deploy на main.
4. На VPS `docker compose pull` подтягивает новый digest → auto-restart
   cadvisor/promtail.

## Trivy gate

`.github/workflows/security.yml` (M06 Группа 6) запускает Trivy на
всех образах включая digest-pinned. HIGH/CRITICAL CVE блокирует PR и
триггерит hot-patch pin'а на следующую версию.

## Открытые вопросы

- **cadvisor forks на arm/arm64** — production на VPS amd64, digest из
  multi-arch manifest-list работает. Если однажды переедем на arm64
  (hosting.arm), проверить что все pin'ы multi-arch (не amd64 only).
- **GHCR retention** — `:latest` теги per-repo retention 30 дней default.
  Для rollback-сценария старые SHA образы должны остаться. Проверить в
  M06 или деффернуть в prod-deploy checklist.

## История изменений

| Дата | Образ | От | До | Причина |
|------|-------|-----|-----|---------|
| 2026-04-21 | cadvisor | `:latest` | `v0.49.1@sha256:3cde6faf...` | M06 QD4 initial pin |
| 2026-04-21 | promtail | `:latest` | `3.2.1@sha256:bf617e9d...` | M06 QD4 initial pin |
