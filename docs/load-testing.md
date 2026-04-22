# Load testing — runbook (NEW-163)

k6 нагрузочные тесты для RutCampusTrack.

## Зачем

**k6 baseline** — фиксирует числа производительности hot-path
endpoint'ов ПОСЛЕ M05 оптимизаций. Release-engineer прогоняет перед
каждым release-tag'ом и сравнивает с `docs/performance-baseline.md`:
нет регрессии → proceed, есть регрессия → investigate.

**v0.0.0 scope (M08 D2):** manual-only прогон локально против
`docker compose up`. CI-integration **не делается** — OWNER-ANSWERS
P2-8/7 разрешает minimal для v0.0.0, VPS/staging stable только
после M09. Nightly CI job через Gatling/JMeter → v0.1 (см.
`docs/future-ideas.md`).

## Install

### Windows

```powershell
winget install k6.k6
# Или через Chocolatey:
choco install k6
```

### Mac

```bash
brew install k6
```

### Linux

```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | \
  sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

Проверить: `k6 version` (> 0.50.0).

## Запуск

### Local (docker compose up)

```bash
# 1. Infra
docker compose up -d

# 2. Java services (M07 G3 скрипт — запускает 5 сервисов bootRun'ом)
bash scripts/m07-g3-launch-services.sh

# 3. Подождать /actuator/health
for p in 9090 9091 9092 9093 8080; do
  curl -s http://localhost:$p/actuator/health
done

# 4. Run
k6 run tests/load/bulk-mark.js
k6 run tests/load/geolocation-flood.js

# 5. Stop
bash scripts/m07-g3-stop-services.sh
```

### Prod/staging

```bash
k6 run \
  -e BASE_URL=https://ruttrack.site \
  -e HEADMAN_LOGIN=headman_real_user \
  -e HEADMAN_PASSWORD="$HEADMAN_PW" \
  tests/load/bulk-mark.js
```

⚠️ **НЕ запускать против prod** без согласования — 10 VU × 2 min
bulk-mark создаёт реальные attendance записи в БД. Staging ok,
prod — только если backend в maintenance-mode.

## Scripts

### bulk-mark.js

**Scenario:** 10 старост параллельно отмечают 30 студентов батчами.

**Config:**
- 10 VU × 2 min
- `thresholds: p(95)<500`, `rate<0.01`
- Setup: login + get active lesson

**Endpoint:** `POST /api/attendance/marks/batch`

**Что ловит:** регрессии M05 G4 batch endpoint (partial success,
validation-first pseudo-atomic), gRPC fan-out latency (M05 G8
parallel), HikariCP pool exhaustion (M05 G6 — pool=20).

### geolocation-flood.js

**Scenario:** 50 студентов одновременно делают checkin в начале пары.

**Config:**
- 50 VU × 30s
- `thresholds: p(95)<1000`, `rate<0.05` (толще из-за rate-limit'ов
  CHKN-07 — 3/min per userId генерируют ожидаемые 429)
- Random GPS jitter ±50m вокруг кампуса

**Endpoint:** `POST /api/attendance/checkin`

**Что ловит:** регрессии CheckinService hot-path (Redis dedup
CHKN-06, Geofence validation CHKN-01, gRPC getActiveLesson).

## Baseline reference

`docs/performance-baseline.md` содержит числа после первого прогона
(M08 Группа 7). При любом prod-release:

1. Прогнать оба скрипта локально.
2. Сравнить `avg/p(95)/p(99)` с baseline.
3. Если регрессия > 20% — открыть issue, исследовать.
4. Если улучшение — обновить baseline.md + commit.

## Интерпретация output'а

```
  scenarios: (100.00%) 1 scenario, 10 max VUs, 2m30s max duration

running (2m00.0s), 00/10 VUs, 1200 complete and 0 interrupted iterations

     ✓ login 200
     ✓ schedule 200
     ✓ batch 200/207
     ✓ response has marks array

     checks.........................: 100.00% 4800 out of 4800
     data_received..................: 15 MB   125 kB/s
     data_sent......................: 8.2 MB  68 kB/s
     http_req_duration..............: avg=320ms p(95)=480ms p(99)=890ms
     http_req_failed................: 0.20%
     iterations.....................: 1200    10.0/s

✓ http_req_duration p(95) < 500ms
✓ http_req_failed rate < 1%
```

**Green check ✓** у thresholds — baseline держится. **Red ✗** —
регрессия.

## Troubleshooting

- **`error connecting: i/o timeout`** — backend не запущен, проверить
  `docker compose ps` + `m07-g3-launch-services.sh`.
- **`login failed: 401`** — logins не соответствуют seed'у. Проверить
  `V2__seed_test_data.sql` в academic.
- **`No active lesson for today`** — seed не генерирует lesson'ы на
  текущую дату. Временно закомментировать bulk-mark'у filter — она
  skip'нётся но test не упадёт.
- **Очень низкий VUs rate** — Docker Desktop CPU throttling. Закрыть
  background apps или запустить prod-подобный test на VPS (M09).

## Связанные документы

- `docs/future-ideas.md` — Full load suite (Gatling/JMeter) → v0.1
- `docs/performance-baseline.md` — baseline numbers
- `docs/connection-pool-tuning.md` (NEW-147) — HikariCP config
  (M05 G6)
- `docs/api-error-conventions.md` (NEW-145) — 200/207 vs partial
  success semantics (M05 G4)
