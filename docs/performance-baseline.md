# Performance baseline

Числа с первых прогонов k6 load-scripts из `tests/load/`. Обновляется
при каждом prod-release (пропорционально улучшениям).

⚠️ **TBD** — baseline ещё не зафиксирован реальными числами.

**Статус M08 G12 (2026-04-23):** локальный docker-стек в Windows dev
не подходит для reproducible baseline (jitter от Docker Desktop
virtualization + WSL I/O overhead). M08 DECISION D2 требует manual
прогона release-engineer'ом на stable staging перед релизом.
Первый запись будет сделан release-engineer'ом перед созданием
`v0.0.0` tag против prod-like VPS staging.

k6-скрипты и инфраструктура готовы — см. `docs/load-testing.md`.

## Как обновлять

1. `k6 run tests/load/bulk-mark.js` + `k6 run tests/load/geolocation-flood.js`
   локально против docker-compose окружения с полным seed'ом.
2. Скопировать `avg / p(95) / p(99) / failed rate / iterations` в
   таблицы ниже.
3. Зафиксировать environment метаданные (machine specs, Docker
   Desktop version, JDK).
4. Commit с message: `perf: refresh baseline after <release-tag>`.

## bulk-mark.js — POST /api/attendance/marks/batch

### Run history

| Date | Release | avg | p(95) | p(99) | failed rate | iter / 2min | Note |
|------|---------|-----|-------|-------|-------------|-------------|------|
| TBD  | v0.0.0-alpha.9 | — | — | — | — | — | первый прогон M08 |

### Thresholds (из bulk-mark.js)

- `http_req_duration p(95) < 500ms`
- `http_req_failed rate < 0.01`

### Environment

- Machine: [TBD — dev machine specs]
- Docker: [TBD]
- JDK: Microsoft Build 21.0.9
- docker-compose: postgres:16, mongo:7, redis:7-alpine, rabbitmq:3.13

---

## geolocation-flood.js — POST /api/attendance/checkin

### Run history

| Date | Release | avg | p(95) | p(99) | failed rate | iter / 30s | Note |
|------|---------|-----|-------|-------|-------------|------------|------|
| TBD  | v0.0.0-alpha.9 | — | — | — | — | — | первый прогон M08 |

### Thresholds (из geolocation-flood.js)

- `http_req_duration p(95) < 1000ms`
- `http_req_failed rate < 0.05` (ожидаемые 429 rate-limit'ы)

---

## M05 optimization deltas — что планируется зафиксировать

Предыдущие improvements в M05 оптимизациях (должны быть отражены
в baseline):

| Endpoint | Before M05 | After M05 | Delta |
|----------|------------|-----------|-------|
| `POST /attendance/marks/batch` (30 students) | ~6000ms | ~500ms | 10× (M05 G4) |
| `POST /attendance/checkin` | — | — | TBD |
| `GET /schedule/lessons?group=X&date=Y` | — | — | TBD |
| `GET /attendance/reports/journal` | — | — | TBD |

M05 G1 — composite indexes, M05 G4 — batch endpoints, M05 G5 —
single-pass + SQL pagination, M05 G6 — HikariCP pool 20, M05 G8 —
gRPC parallel fan-out + deadline ArchUnit.

## Regressions log

Место для записи если какой-то release ухудшил perf metrics — как
caution tale для будущего refactor'а.

| Date | Release | Endpoint | Delta | Reason | Fixed in |
|------|---------|----------|-------|--------|----------|
| —    | —       | —        | —     | —      | —        |

## Связанные документы

- `docs/load-testing.md` (NEW-163) — как запускать
- `tests/load/README.md` — quick reference для скриптов
- `docs/future-ideas.md` — Full load suite v0.1
