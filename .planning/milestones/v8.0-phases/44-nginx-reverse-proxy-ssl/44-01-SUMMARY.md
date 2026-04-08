---
phase: 44-nginx-reverse-proxy-ssl
plan: 01
subsystem: infrastructure
tags: [nginx, ssl, reverse-proxy, certbot, docker]
dependency_graph:
  requires: [docker-compose.prod.yml]
  provides: [nginx-config, ssl-termination, certbot-renewal]
  affects: [api-gateway, all-frontends, notification-web]
tech_stack:
  added: [nginx:1.27-alpine, certbot/certbot]
  patterns: [reverse-proxy, ssl-termination, certbot-sidecar, websocket-upgrade]
key_files:
  created:
    - nginx/nginx.conf
    - nginx/conf.d/http-only.conf
    - nginx/conf.d/default.conf
  modified:
    - docker-compose.prod.yml
    - .env.prod.example
decisions:
  - "WebSocket location /api/ws/ placed before generic /api/ for correct nginx matching"
  - "PWA location / is catch-all (last in server block)"
  - "Cert path uses directory name 'rutcampustrack' via certbot --cert-name flag"
  - "server_name _ used as placeholder — actual domain configured via certbot cert directory"
metrics:
  duration: 96s
  completed: "2026-04-07T20:14:50Z"
  tasks_completed: 2
  tasks_total: 2
  files_created: 3
  files_modified: 2
---

# Phase 44 Plan 01: Nginx Reverse Proxy + SSL Configuration Summary

Nginx reverse proxy with HTTPS termination, path-based routing to all 6 backends/frontends, WebSocket upgrade support, TLS hardening, and certbot sidecar for automatic certificate renewal.

## Tasks Completed

| # | Task | Commit | Key Changes |
|---|------|--------|-------------|
| 1 | Create nginx configuration files | e914118 | nginx.conf (main), http-only.conf (bootstrap), default.conf (production HTTPS) |
| 2 | Add nginx + certbot to docker-compose.prod.yml | e6719be | nginx/certbot services, api-gateway expose-only, certbot volumes, env vars |

## What Was Built

### Nginx Configuration (3 files)

**nginx/nginx.conf** — Main http block with `server_tokens off`, 1024 worker connections, 10m client body size, includes conf.d/*.conf.

**nginx/conf.d/http-only.conf** — Bootstrap HTTP-only config for first certbot certificate issuance. Serves ACME challenge and a "ready" message. Renamed to .bak after cert is issued.

**nginx/conf.d/default.conf** — Full production config with:
- HTTP-to-HTTPS redirect (301) with ACME challenge passthrough
- TLS hardening: TLSv1.2 + TLSv1.3 only, strong cipher suite, DH params
- Security headers: HSTS (1 year), X-Frame-Options, X-Content-Type-Options, Referrer-Policy
- 6 proxy_pass routes:
  1. `/api/ws/` -> rct-api-gateway:8080 (WebSocket upgrade, 86400s timeout)
  2. `/api/` -> rct-api-gateway:8080 (all API traffic)
  3. `/landing/` -> rct-landing-nginx:80
  4. `/admin/` -> rct-web-panel-nginx:80
  5. `/mini-app/` -> rct-mini-app-nginx:80
  6. `/` -> rct-pwa-nginx:80 (catch-all, last)

### Docker Compose Changes

- **api-gateway**: Changed `ports: "80:8080"` to `expose: "8080"` — nginx is now the sole port 80/443 owner
- **nginx service**: nginx:1.27-alpine, ports 80+443, mounts nginx config + certbot volumes, 6h reload loop, depends on api-gateway healthy
- **certbot service**: certbot/certbot, 12h renewal loop with `certbot renew --quiet`, shares certbot-conf and certbot-www volumes
- **volumes**: Added `certbot-conf:` and `certbot-www:` named volumes
- **.env.prod.example**: Added `DOMAIN` and `CERTBOT_EMAIL` variables

## Verification Results

| Check | Expected | Actual | Status |
|-------|----------|--------|--------|
| proxy_pass count in default.conf | 6 | 6 | PASS |
| ports: only in nginx service | 1 occurrence | 1 (line 336) | PASS |
| 80:8080 removed from api-gateway | 0 matches | 0 | PASS |
| ssl_protocols in default.conf | TLSv1.2 TLSv1.3 | TLSv1.2 TLSv1.3 | PASS |
| server_tokens in nginx.conf | off | off | PASS |
| DOMAIN in .env.prod.example | present | present | PASS |
| CERTBOT_EMAIL in .env.prod.example | present | present | PASS |

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None.

## Threat Mitigations Applied

| Threat ID | Mitigation | File |
|-----------|-----------|------|
| T-44-01 | TLSv1.2 + TLSv1.3 only, strong cipher suite | nginx/conf.d/default.conf |
| T-44-02 | HTTP-to-HTTPS 301 redirect, HSTS max-age=31536000 | nginx/conf.d/default.conf |
| T-44-03 | server_tokens off | nginx/nginx.conf |
| T-44-04 | X-Content-Type-Options, X-Frame-Options, Referrer-Policy headers | nginx/conf.d/default.conf |
| T-44-05 | Certbot 12h renewal loop + nginx 6h reload | docker-compose.prod.yml |
| T-44-06 | Only nginx has ports: binding; api-gateway uses expose: only | docker-compose.prod.yml |
| T-44-07 | X-Frame-Options: SAMEORIGIN | nginx/conf.d/default.conf |

## Self-Check: PASSED

All 4 files exist. Both commits (e914118, e6719be) verified in git log.
