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

---

## Production Path Routing (v9.0)

Decided: Phase 49-50 (v9.0). Nginx reverse proxy at `https://ruttrack.site`.

| Path | Served By | Requirement | Notes |
|------|-----------|-------------|-------|
| `/` | 301 redirect → `/login` | INFRA-v9-01 | Единая точка входа. Корень больше не отдаёт PWA. |
| `/login` | web-panel SPA (Angular, baseHref `/`) | AUTH-v9-01, INFRA-v9-04 | Login form для всех ролей. |
| `/admin/*` | web-panel SPA (lazy feature) | — | Роль ADMIN после login. |
| `/teacher/*` | web-panel SPA (lazy feature) | — | Роль TEACHER. |
| `/student/*` | web-panel SPA (lazy feature) | AUTH-v9-05 | Роль STUDENT (headman тоже проходит studentGuard). |
| `/headman/*` | web-panel SPA (lazy feature) | AUTH-v9-04 | STUDENT + is_headman=true (headmanGuard). |
| `/app/` | PWA (React + Vite, rct-pwa-nginx) | INFRA-v9-03 | Мобильный клиент RutTrack. |
| `/presentation/` | Landing (static HTML, rct-landing-nginx) | INFRA-v9-02, LAND-v9-01 | Описание проекта; доступ только по прямой ссылке. |
| `/api/*` | API Gateway (localhost:8080 внутри сети) | — | REST + gRPC (обратный прокси из backend). |
| `/api/ws` | Notification Web (notification-web:9094) | — | STOMP WebSocket endpoint. |

### Пост-login routing

| Роль | Landing dashboard | Guard |
|------|-------------------|-------|
| ADMIN | `/admin/dashboard` | roleGuard(['ADMIN']) |
| TEACHER | `/teacher/dashboard` | roleGuard(['TEACHER']) |
| STUDENT (is_headman=false) | `/student/dashboard` | studentGuard |
| STUDENT (is_headman=true) | `/headman/dashboard` | headmanGuard (+ passes studentGuard) |

Реализация: `AuthService.resolveDashboardFor()` — single source of truth (Phase 50).

### Deprecated paths

- `/landing/` — удалён в Phase 49 (INFRA-v9-02), перенесён в `/presentation/`.
- Корень `/` больше не отдаёт PWA — PWA теперь на `/app/` (INFRA-v9-01, INFRA-v9-03).
