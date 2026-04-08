---
phase: 44-nginx-reverse-proxy-ssl
verified: 2026-04-07T21:30:00Z
status: human_needed
score: 5/5
human_verification:
  - test: "Deploy to VPS with valid domain and run init-letsencrypt.sh"
    expected: "Browser shows padlock icon on https://DOMAIN, all routes resolve correctly"
    why_human: "Requires real DNS, real Let's Encrypt ACME challenge, and running containers to verify end-to-end SSL"
  - test: "Verify WebSocket connection stays alive beyond 60 seconds"
    expected: "STOMP/SockJS connection at /api/ws/ remains active without disconnects"
    why_human: "Requires running notification-web service and real browser WebSocket connection"
  - test: "Verify frontend SPA routing works at sub-paths (/admin/, /mini-app/, /landing/)"
    expected: "Deep links like /admin/users load correctly without 404 errors"
    why_human: "Depends on frontend build configs (base-href) which are a build-time concern outside this phase"
---

# Phase 44: Nginx Reverse Proxy + SSL Verification Report

**Phase Goal:** A single nginx container terminates SSL and routes all external traffic to the correct backend service or frontend container
**Verified:** 2026-04-07T21:30:00Z
**Status:** human_needed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | All HTTP traffic on port 80 redirects to HTTPS (301) | VERIFIED | `nginx/conf.d/default.conf:15` has `return 301 https://$host$request_uri` in port 80 server block, ACME challenge location placed before redirect (line 10) |
| 2 | HTTPS requests on port 443 are routed to correct backend by path prefix | VERIFIED | `default.conf` has 6 proxy_pass directives: `/api/ws/` and `/api/` to rct-api-gateway:8080, `/landing/` to rct-landing-nginx:80, `/admin/` to rct-web-panel-nginx:80, `/mini-app/` to rct-mini-app-nginx:80, `/` to rct-pwa-nginx:80 |
| 3 | HTTPS requests route to all 4 frontend containers by path | VERIFIED | PWA at `/` (catch-all, last), landing at `/landing/`, admin at `/admin/`, mini-app at `/mini-app/` -- all with proxy_pass to correct container names |
| 4 | A valid Let's Encrypt certificate can be installed via bootstrap script | VERIFIED | `nginx/scripts/init-letsencrypt.sh` implements full 2-phase flow: staging cert, user confirmation, production cert with --force-renewal, config swap, nginx reload |
| 5 | Certbot auto-renewal runs on schedule without manual intervention | VERIFIED | `docker-compose.prod.yml:361` certbot entrypoint runs `certbot renew --quiet; sleep 12h` in loop; nginx command (line 351) runs `nginx -s reload` every 6h |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `nginx/nginx.conf` | Main nginx http block config | VERIFIED | 27 lines; has `server_tokens off`, `include /etc/nginx/conf.d/*.conf`, `client_max_body_size 10m`, `worker_processes auto` |
| `nginx/conf.d/http-only.conf` | Bootstrap HTTP-only config for first certbot run | VERIFIED | 17 lines; serves ACME challenge at `/.well-known/acme-challenge/`, no SSL references, returns 200 ready message |
| `nginx/conf.d/default.conf` | Production HTTPS + redirect config with all routing | VERIFIED | 87 lines; 6 proxy_pass routes, TLS 1.2+1.3, strong ciphers, HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, WebSocket upgrade with 86400s timeout |
| `nginx/scripts/init-letsencrypt.sh` | One-time bootstrap script for first SSL cert issuance | VERIFIED | 101 lines; `set -euo pipefail`, reads .env.prod, generates dhparam, staging then production cert, config swap, nginx reload |
| `docker-compose.prod.yml` (nginx service) | nginx + certbot services, api-gateway expose-only | VERIFIED | nginx service at lines 333-351 with ports 80+443, certbot at 353-362 with 12h renewal, api-gateway uses `expose: "8080"` (line 110-111) |
| `.env.prod.example` | DOMAIN and CERTBOT_EMAIL vars | VERIFIED | Contains `DOMAIN=yourdomain.com` and `CERTBOT_EMAIL=admin@yourdomain.com` |
| `.gitignore` | nginx generated files excluded | VERIFIED | Contains `nginx/dhparam.pem`, `nginx/conf.d/*.bak`, `nginx/conf.d/*.disabled` |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `nginx/conf.d/default.conf` | rct-api-gateway:8080 | proxy_pass in /api/ location | WIRED | Lines 43 and 56 both proxy to `http://rct-api-gateway:8080` |
| `docker-compose.prod.yml` | nginx/conf.d | volume mount | WIRED | Line 341: `./nginx/conf.d:/etc/nginx/conf.d:ro` |
| `docker-compose.prod.yml` | nginx/nginx.conf | volume mount | WIRED | Line 340: `./nginx/nginx.conf:/etc/nginx/nginx.conf:ro` |
| `docker-compose.prod.yml` | certbot volumes | shared named volumes | WIRED | nginx has `certbot-conf:/etc/letsencrypt:ro` and `certbot-www:/var/www/certbot:ro`; certbot has same volumes without :ro |
| `init-letsencrypt.sh` | docker-compose.prod.yml | docker compose commands | WIRED | Script uses `docker compose -f docker-compose.prod.yml` for up, run, exec operations |

### Data-Flow Trace (Level 4)

Not applicable -- infrastructure/config phase with no dynamic data rendering.

### Behavioral Spot-Checks

Step 7b: SKIPPED (infrastructure config files -- no runnable entry points without deployed VPS and DNS)

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| NET-01 | 44-01 | SSL termination at nginx edge, TLS 1.2+1.3 only, strong ciphers | SATISFIED | `default.conf`: `ssl_protocols TLSv1.2 TLSv1.3`, strong cipher suite, `server_tokens off` in nginx.conf |
| NET-02 | 44-01 | Path-based routing to all backend and frontend services | SATISFIED | 6 proxy_pass routes covering api-gateway + 4 frontends |
| NET-03 | 44-02 | Certbot bootstrap script for first cert issuance | SATISFIED | `init-letsencrypt.sh` with 2-phase flow (staging then production) |
| NET-04 | 44-01 | Certbot auto-renewal configured | SATISFIED | certbot sidecar with `certbot renew --quiet; sleep 12h` loop; nginx reloads every 6h |
| NET-05 | 44-01 | HTTP-to-HTTPS redirect on port 80 | SATISFIED | `return 301 https://$host$request_uri` in port 80 server block, ACME challenge preserved |

### Threat Mitigations

| Threat ID | Category | Status | Evidence |
|-----------|----------|--------|----------|
| T-44-01 | TLS protocol downgrade | MITIGATED | `ssl_protocols TLSv1.2 TLSv1.3` + strong cipher suite in default.conf |
| T-44-02 | HTTP downgrade | MITIGATED | 301 redirect + HSTS max-age=31536000 with includeSubDomains |
| T-44-03 | Server version disclosure | MITIGATED | `server_tokens off` in nginx.conf |
| T-44-04 | Response header security | MITIGATED | X-Content-Type-Options: nosniff, X-Frame-Options: SAMEORIGIN, Referrer-Policy: strict-origin-when-cross-origin |
| T-44-05 | Certificate expiry | MITIGATED | Certbot 12h renewal + nginx 6h reload |
| T-44-06 | Internal service ports exposed | MITIGATED | Only nginx has `ports:` (line 336); all other services use `expose:` |
| T-44-07 | Clickjacking | MITIGATED | X-Frame-Options: SAMEORIGIN header |
| T-44-08 | Let's Encrypt rate limits | MITIGATED | init-letsencrypt.sh uses --staging first |
| T-44-09 | dhparam.pem in git | MITIGATED | .gitignore entry for nginx/dhparam.pem |
| T-44-10 | Script runs as root | ACCEPTED | Manual operator execution with visibility |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | - | - | - | No anti-patterns detected |

### Human Verification Required

### 1. End-to-End SSL on Real VPS

**Test:** Deploy to VPS with valid domain, run `./nginx/scripts/init-letsencrypt.sh`, then visit `https://DOMAIN` in browser
**Expected:** Browser shows padlock icon, certificate is valid, issued by Let's Encrypt
**Why human:** Requires real DNS A record, internet-accessible VPS with ports 80/443 open, and real ACME challenge completion

### 2. WebSocket Connection Persistence

**Test:** Open PWA in browser, navigate to a page using WebSocket notifications, leave idle for > 60 seconds
**Expected:** STOMP/SockJS connection at `/api/ws/` remains active without 60s disconnects (proxy_read_timeout 86400s should prevent this)
**Why human:** Requires running notification-web service and real browser WebSocket connection to verify nginx does not prematurely close the connection

### 3. Frontend SPA Sub-Path Routing

**Test:** Navigate to `/admin/users`, `/mini-app/some-route`, `/landing/` directly via URL bar
**Expected:** Angular/React apps load correctly at sub-paths without 404 errors on deep links or broken assets
**Why human:** Depends on frontend build-time `base-href`/`basename` configuration which is outside this phase's scope but affects whether the nginx routing actually works for SPA navigation

### Gaps Summary

No gaps found. All 5 observable truths are verified against the actual codebase. All 7 artifacts exist with correct, substantive content. All key links are wired. All 5 NET requirements are satisfied. All 10 threat mitigations are in place. No anti-patterns detected.

Three items require human verification on a real deployed environment: end-to-end SSL with real cert, WebSocket persistence, and SPA sub-path routing. These cannot be verified programmatically as they require real DNS, running services, and browser interaction.

---

_Verified: 2026-04-07T21:30:00Z_
_Verifier: Claude (gsd-verifier)_
