# URL Layout — RutCampusTrack Frontends

Decided: Phase 33 (v7.0 Infrastructure)

## Port Assignments

| Frontend    | Container port | Host port | Dev port | Technology    |
|-------------|---------------|-----------|----------|---------------|
| PWA         | 80            | 80        | 5173     | React + Vite  |
| Mini App    | 80            | 3000      | 5174     | React + Vite  |
| Web Panel   | 80            | 4200      | 4200     | Angular CLI   |
| Landing     | 80            | 8081      | n/a      | Static HTML   |

## Container Names

| Frontend    | Container name          | nginx config path                    |
|-------------|------------------------|--------------------------------------|
| PWA         | rct-pwa-nginx          | frontends/pwa/nginx.conf             |
| Mini App    | rct-mini-app-nginx     | frontends/mini-app/nginx.conf        |
| Web Panel   | rct-web-panel-nginx    | frontends/web-panel/nginx.conf       |
| Landing     | rct-landing-nginx      | frontends/landing/nginx.conf         |

## Gateway CORS Origins (dev)

- `http://localhost:5173` -- PWA dev (Vite)
- `http://localhost:80` -- PWA prod (pwa-nginx)
- `http://localhost:5174` -- Mini App dev (Vite)
- `http://localhost:3000` -- Mini App prod (mini-app-nginx)
- `http://localhost:4200` -- Web Panel dev/prod (Angular CLI / web-panel-nginx)

Landing does not make API calls -- no CORS origin needed.

## Notes

- All containers listen on port 80 internally; host port differs per container.
- Production domains TBD -- only localhost origins configured in Phase 33.
- Mini App Vite port (5174) is one above PWA (5173) to avoid conflict.
- Angular CLI defaults to 4200; web-panel-nginx uses the same port for consistency.
