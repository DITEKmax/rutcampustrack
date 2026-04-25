# Промпт для следующей сессии — M13 G25 (продолжение): проверить CI #131, fix остатки, tag

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Opus сам
откроет нужные файлы и продолжит.

---

**M13 G25 в процессе. На origin/dev 6 коммитов G25.1..G25.6,
последний `ed40d36`. CI run #131 (`24933795015`) был запущен в конце
прошлой сессии — статус нужно проверить в этой.**

## Текущая позиция (на конец сессии 2026-04-25)

**4 коммита G25 push'нуты, 2 итерации fix'ов выполнены:**

| Коммит | Группа | Что сделано | CI status |
|--------|--------|-------------|-----------|
| `981f2b1` | G25.1 | ruff format compliance (9 .py файлов) | ✅ green в #129+ |
| `b373b3e` | G25.2 | watchdog mock signature `idempotency_guard=None` | ✅ green в #129+ |
| `be71ed1` | G25.3 | docker-compose.e2e.yml + self-signed TLS + CI integration | ❌ #129 (compile fail) |
| `346c147` | G25.4 | CHANGELOG + e2e-testing.md docs | ✅ no CI gate |
| `549e9dc` | G25.5 | `COPY services/shared` в 6 backend Dockerfiles | ❌ #130 (next blocker) |
| `ed40d36` | G25.6 | `gitOutput()` try/catch IOException | 🟡 #131 (running, проверить!) |

## Стратегия: двухфазная (per моё решение прошлой сессии)
1. Push без tag → ждём CI зелёный.
2. Когда CI зелёный → tag `v0.0.0-alpha.15` с message "M13 G25 ✅ CI green".
3. Если красный → G25.7+ fix cycle.

## Что делать в этой сессии (по порядку)

### Шаг 1 — проверить CI #131 status

```bash
curl -s "https://api.github.com/repos/DITEKmax/rutcampustrack/actions/runs/24933795015/jobs?per_page=30" | py -c "
import json, sys
from datetime import datetime
data = json.load(sys.stdin)
print(f'{\"job\":<55} {\"status\":<12} {\"conclusion\":<12} duration')
print('-' * 95)
for j in data.get('jobs', []):
    started, completed = j.get('started_at'), j.get('completed_at')
    if started and completed:
        s = datetime.fromisoformat(started.replace('Z','+00:00'))
        c = datetime.fromisoformat(completed.replace('Z','+00:00'))
        dur = f'{int((c-s).total_seconds())}s'
    elif started:
        s = datetime.fromisoformat(started.replace('Z','+00:00'))
        dur = f'~{int((datetime.now(s.tzinfo)-s).total_seconds())}s (running)'
    else: dur = '-'
    print(f'{j[\"name\"][:55]:<55} {j[\"status\"]:<12} {j.get(\"conclusion\") or \"-\":<10} {dur}')
"
```

URL: https://github.com/DITEKmax/rutcampustrack/actions/runs/24933795015

### Шаг 2 — два сценария

**Сценарий A: CI #131 зелёный (е2е-auth тоже success)**

Tag и финал G25:
```bash
git fetch origin
git checkout dev
git pull origin dev
git tag -a v0.0.0-alpha.15 -m "M13 G25 ✅ — CI green: ruff + watchdog + e2e compose + Docker shared modules + git fallback"
git push origin v0.0.0-alpha.15
```

Затем переходим к Шагу 3 оригинального плана (VPS dry-run).

**Сценарий B: e2e-auth fail с новой ошибкой**

Анализ — что упало:

1. **Если compile error** в каком-то новом сервисе (academic/schedule/
   attendance/notification/auth) — посмотри какой transitive dep
   missing. G25.5 покрыл shared modules, но возможно есть ещё какой-то
   COPY missing (например `proto/` для notification-app — проверь).

2. **Если runtime error** (контейнер не стартует — Spring Boot fail):
   - Скачать docker logs из CI artifacts (https://github.com/DITEKmax/rutcampustrack/actions/runs/24933795015 → Artifacts).
   - Типичные блокеры:
     - **JWT keys не сгенерены** — auth-service первый стартует, должен записать `/keys/public.key` в named volume `jwt-keys`. Проверь auth-service logs.
     - **Mongo replica set не init'ится** в эфемерном container — Bitnami `MONGODB_REPLICA_SET_MODE=primary` обычно работает, но requires `start_period: 60s` (уже стоит). Если timeout — увеличить до 90s.
     - **gRPC fail-fast** — если `GRPC_SECRET` пустой/wrong, academic/schedule/attendance не стартуют. Проверь `tests/e2e/.env.ci`.
     - **Spring property missing** — какой-то env var из `.env.prod` обязательный, но не положили в `.env.ci`. Compare `.env.ci` ↔ `.env.prod.example`.

3. **Если Playwright fail** (компоуз green, тесты не проходят):
   - **T1 cookie test**: `Secure` flag требует HTTPS — мы это решили self-signed. Проверь что Playwright `ignoreHTTPSErrors: true` работает (config line 40).
   - **T2 admin redirect**: требует seed users (`admin`/`admin_test_pass` в `V2__seed_test_data.sql`). Если миграция не применилась — fail.
   - **T5 STOMP reconnect**: нужен notification-web + работающий WebSocket через nginx. nginx config `/api/ws/` уже proxy_pass.

4. **Если timeout 30 min** — стек слишком долго билдится. Возможно нужно
   добавить registry cache для Docker layers (`docker/build-push-action`
   с `cache-from: type=gha`).

### Шаг 3 — fix cycle (G25.7+)

Каждый fix = новый commit `(M13 G25.N)`, push, наблюдение CI. Tag
только когда **всё зелёное**.

### Шаг 4 — после CI зелёный → tag → продолжить оригинальный план

После `v0.0.0-alpha.15` ✅:
- **Шаг 3 (оригинальный)**: Live VPS dry-run по `docs/prod-deploy-checklist.md`
  (owner-driven). Findings → M13 NOTES.md G23 секция.
- **Шаг 4 (оригинальный)**: Tag `v0.0.0` GA + bump version в root
  `build.gradle.kts` + `frontends/*/package.json` на `0.0.0`. Push tag.

## Контекст недавно решённых блокеров

### G25.5 — shared modules COPY (compile fail)

**Проблема**: api-gateway compile упал в Docker:
```
error: package ru.rutcampustrack.shared.observability does not exist
```
6 backend Dockerfile'ов копировали только свой api-contract+app, но
не shared modules. Активировалось только в G25.3 — раньше backend
сервисы никогда не build'ились через Docker в CI.

**Fix**: одна строка `COPY services/shared services/shared` в каждом
из 6 Dockerfile'ов.

### G25.6 — generateGitProperties IOException (runtime fail на gradle)

**Проблема**: `ProcessBuilder("git").start()` выбрасывает IOException
в Docker (нет git binary, `.git/` исключён через `.dockerignore`).
`gitOutput()` имел fallback `"unknown"` для exit!=0, но IOException
происходит ДО `proc.exitValue()`.

**Fix**: try/catch IOException в `build.gradle.kts:300-308`. git.properties
генерируется с `"unknown"` значениями для Docker builds.

## Local state на момент hand-off

```
Working tree: clean
Branch: dev (synced with origin)
Last commit: ed40d36 fix(build): generateGitProperties не падает при отсутствии git binary (M13 G25.6)
```

Никаких uncommitted изменений. CI ожидает результата на `ed40d36`.

## Pending decisions для new conversation

1. **Если CI #131 зелёный** — какие smoke specs прошли? Если `auth-token-lifecycle` все 5 T1-T5 прошли — отлично. Если `auth.spec.ts` (старый smoke) тоже зелёный — двойная гарантия.

2. **Если CI #131 красный с runtime error** — нужно ли локальный smoke prep'ить полный stack (15+ мин cold build), или сразу анализ docker logs из CI artifacts?

## История milestone'ов (архив)

M01-M08 ✅ (см. git tags `v0.0.0-alpha.1..alpha.9`).
M09-M12 ✅ 2026-04-24 (`alpha.10..alpha.13`).
M13 Pre-Deploy Hardening ✅ 2026-04-25 (`v0.0.0-alpha.14`)
**→ re-open Группа 25** (CI hot-fixes + e2e infra) → ожидает `v0.0.0-alpha.15`.

G25 sub-progress:
- G25.1 ✅ ruff format
- G25.2 ✅ watchdog mock fix
- G25.3 ✅ docker-compose.e2e.yml created
- G25.4 ✅ CHANGELOG + docs
- G25.5 ✅ Dockerfile shared modules COPY
- G25.6 ✅ generateGitProperties IOException fix
- 🟡 CI #131 (`ed40d36`) — нужно проверить в этой сессии

Debt report (источник scope M13) — `docs/report-before-v0.0.0/v0.0.0-debt.md`.
Dependency graph и полный roadmap — `docs/milestones/README.md`.
