# M15 — First VPS Deploy (retrospective)

**Статус:** ✅ готов (закрыт по факту в проде)
**Старт / финиш:** 2026-04-26 / 2026-04-27
**Тег:** `v0.0.0-alpha.16` (M14) → 4 hotfix-коммита поверх
**Тип:** retrospective short — milestone-папка создана пост-фактум,
оригинальная работа велась прямыми коммитами без PLAN/CHECKLIST.

---

## Что произошло

Первый продакшен-деплой на VPS. M14 (`v0.0.0-alpha.16`) закрыл блокеры
четырёх аудитов и был помечен как готовый к деплою. При попытке выкатить
обнаружился ряд проблем, не воспроизводимых на dev-машине: они
закрывались прямо во время сессии деплоя четырьмя hotfix-коммитами.

После четвёртого hotfix'а 26/26 контейнеров `healthy`, HTTPS работает,
SPA отдаётся, алерты ходят в Telegram.

## Hotfix-коммиты (in chronological order)

### `b8cf106` — fix(security): suppress 3 transitive CVEs + 4 frontend Dockerfile DS-0002

**Trigger:** Trivy scan в `security.yml` падал и блокировал деплой.

**Что сделано:**
- `.trivyignore` — 3 CVE в transitive-зависимостях (всё unreachable от внешнего ввода):
  - `CVE-2025-66020` (valibot ReDoS) через `@telegram-apps/sdk-react`
  - `CVE-2025-4565` + `CVE-2026-0994` (protobuf DoS) через `grpcio-tools`
- `.trivyignore.yaml` — DS-0002 (root user в nginx) для 4 frontend
  Dockerfile'ов (landing/mini-app/pwa/web-panel-nginx) — все
  bridge-network only, проксируются через main `rct-nginx`.

**M16 follow-up:** убрать suppressions через bumps/migrations
(см. M16 CHECKLIST → security suppressions cleanup).

### `c7b2b93` — fix(deploy): cosign verify identity-regexp case-insensitive

**Trigger:** `cosign verify` step в `deploy.yml` падал с exit 12.

**Причина:** GitHub username в cosign cert subject сохраняет оригинальный
регистр (`DITEKmax`), а regex использовал lowercase (`ditekmax`) → mismatch.

**Fix:** `(?i)` flag (Go RE2 case-insensitive) в `identity-regexp`. GitHub
usernames регистронезависимы на уровне платформы — функционально
эквивалентно.

### `c3ff148` — fix(M15): hotfixes from first VPS deploy

Три отдельные проблемы в одном коммите:

**1. auth-service env regression**
`INTERNAL_ISSUER_SECRET` отсутствовал в env block auth-service
(M14 G9 fix покрыл только notification-web). `RequiredSecretsValidator`
(M14 G4 v2) останавливал boot. Добавлено в `docker-compose.prod.yml`.

**2. OOM exit 137 на 3 Java-сервисах**
`auth-service` / `notification-web` / `api-gateway` крутились на
`mem_limit: 256m`. Реальное потребление: heap 75% (192m) + non-heap
(metaspace, OTel, Reactor Netty / Spring WebFlux / STOMP / RabbitMQ NIO,
HikariCP, Caffeine, Mongo driver) пробивало 256m → SIGKILL → restart loop.

`api-gateway`: 27 рестартов перед фиксом, last memory 249/256MiB (97%).

**Fix:** `mem_limit: 256m → 384m`, `mem_reservation: 192m → 256m` для
этих трёх. `academic`/`schedule`/`attendance` уже были на 512m, не
тронуты.

**3. alertmanager config error**
`start time cannot be equal or greater than end time` на
`quiet-hours-msk`. Alertmanager v0.27 не поддерживает `time_intervals`
пересекающие полночь UTC одной записью.

**Fix:** split на два sub-interval'а — `19:00→23:59` + `00:00→05:00`
(те же 10 часов quiet window).

### `8d7c168` — fix(nginx,csp): allow Angular inline onload for inlineCritical CSS lazy-load

**Trigger:** после успешного деплоя `/login` отдавал голый Angular SPA
без стилей (Times New Roman, no layout).

**Причина:** Angular `@angular/build:application` с дефолтным
`inlineCritical: true` генерит:
```html
<link rel="stylesheet" media="print" onload="this.media='all'">
```

Inline `onload` event handler блокировался CSP `script-src 'self'` →
CSS не активировалась → fallback на browser-default fonts.

**Fix:** `'unsafe-hashes'` + точный sha256 хеш value атрибута onload
(`sha256-MhtPZXr7+LpJUY5qtMutB+qWfQtMaPccfe7QXtCcEYc=`).
`'unsafe-hashes'` разрешает event handlers только для конкретных хешей
— не открывает `'unsafe-inline'` для всего `script-src`.

**M16 follow-up:** `inlineCritical: false` в `angular.json` уберёт
onload вообще — long-term правильнее. Сейчас CSP-фикс через
`nginx -s reload` мгновенный.

## Конфигурация в проде

VPS layout: `/opt/rutcampustrack/` + `docker-compose.prod.yml` + `.env.prod`.
26 контейнеров (после OOM bump'ов): 5 backend services + gateway +
2 notification + 4 frontend nginx + main nginx + PG×2 + Mongo + Redis +
RabbitMQ + Prometheus + Alertmanager + Grafana + Tempo + Loki + Promtail
+ cadvisor + node-exporter + blackbox-exporter.

URL: https://ruttrack.site
Cert: Let's Encrypt (renewal автоматический через certbot).

## Артефакты milestone

- 4 коммита (`b8cf106`, `c7b2b93`, `c3ff148`, `8d7c168`)
- M15 → M16 backlog в `docs/archive/future-ideas.md` § «M16 Cleanup Backlog»
- `.trivyignore` + `.trivyignore.yaml` (suppression-tracking)
- `CHANGELOG.md` v0.0.0-alpha.16 (тег M14, hotfix'ы поверх не тегнуты)

## Lessons learned → M16 / M17

См. `NOTES.md`.
