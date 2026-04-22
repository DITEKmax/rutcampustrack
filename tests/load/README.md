# tests/load — k6 scripts

Main runbook: `docs/load-testing.md` (NEW-163).

## Quick reference

```bash
# Install k6 (one-time)
# Windows: winget install k6.k6
# Mac:     brew install k6
# Linux:   https://grafana.com/docs/k6/latest/set-up/install-k6/

# Run (local docker-compose required)
k6 run tests/load/bulk-mark.js
k6 run tests/load/geolocation-flood.js
```

Baseline numbers: `docs/performance-baseline.md`.

## Scripts

- `bulk-mark.js` — 10 VU × 2 min, POST /api/attendance/marks/batch
  (30 students per call), threshold p95<500ms.
- `geolocation-flood.js` — 50 VU × 30s, POST /api/attendance/checkin
  с случайным GPS jitter, threshold p95<1000ms.

## Что проверить в output'е

```
✓ batch 200/207
✗ batch 200/207
  ↳  99% — 11880 / 12000

http_req_duration.............: avg=320ms   min=45ms    med=280ms
                                p(90)=420ms p(95)=480ms p(99)=890ms

http_req_failed...............: 0.20% 24 / 12000
```

`avg` / `p(95)` / `p(99)` — копировать в performance-baseline.md.
`http_req_failed` < threshold — pass, иначе — investigate.

Если `✗ batch 200/207` > expected — смотреть `logs/attendance.log`
на backend (Stack trace / auth failures).
