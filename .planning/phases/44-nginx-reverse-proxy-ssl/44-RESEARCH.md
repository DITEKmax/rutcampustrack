# Phase 44: Nginx Reverse Proxy + SSL — Research

**Researched:** 2026-04-07
**Domain:** Nginx reverse proxy, Let's Encrypt SSL, Docker Compose networking
**Confidence:** HIGH

---

## Summary

Phase 44 adds a single nginx container that terminates SSL at the edge and routes all external traffic inward. Every backend service and frontend container is already on `private_net` with only `expose` (no host ports), except `api-gateway` which currently owns `ports: "80:8080"`. That binding must move to `expose` only, and nginx takes ownership of ports 80 and 443.

The primary technical challenge is the **certbot bootstrap sequence**: nginx needs a certificate to start its HTTPS block, but Let's Encrypt needs nginx already running HTTP to complete the ACME challenge. The standard solution is a 2-step first-deploy: start nginx with HTTP-only config, run certbot to issue the cert, then reload nginx with the full HTTPS config. Subsequent cert renewals use the webroot plugin (nginx stays up and serves `.well-known/acme-challenge/`).

The routing topology is straightforward: one `location /api/` block proxies to the Spring Cloud Gateway (`rct-api-gateway:8080`); four separate `location` blocks proxy to each frontend nginx container. The `/api/ws/` path needs special WebSocket upgrade headers since the notification-web service uses STOMP over SockJS.

**Primary recommendation:** Use the `nginx:1.27-alpine` + `certbot/certbot` image pair with shared volumes for certs and webroot challenge files. Keep all cert logic outside the nginx image — nginx only reads the cert files from the shared volume.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| NET-01 | Single nginx reverse proxy terminates SSL and routes to all backend services | Standard nginx `proxy_pass` to `rct-api-gateway:8080` for all `/api/*` traffic |
| NET-02 | Nginx routes to all 4 frontend containers by path | Four `location` blocks: `/`, `/mini-app/`, `/admin/`, `/app/` proxying to frontend containers on port 80 |
| NET-03 | Let's Encrypt SSL certificate issued via certbot standalone | `certbot certonly --webroot` pattern; 2-phase bootstrap for first deploy |
| NET-04 | Certbot auto-renewal configured (cron or container restart) | Sidecar container running `certbot renew` in a sleep loop every 12h |
| NET-05 | HTTP→HTTPS redirect for all traffic | Nginx server block on port 80 with `return 301 https://$host$request_uri` |
</phase_requirements>

---

## Standard Stack

### Core

| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| nginx | 1.27-alpine | SSL termination + reverse proxy | Same image used by all frontend containers in this project [VERIFIED: docker-compose.prod.yml] |
| certbot/certbot | latest (2.x) | ACME client for Let's Encrypt | Official Docker image, webroot plugin works with running nginx [CITED: hub.docker.com/r/certbot/certbot] |

### Supporting

| Component | Version | Purpose | When to Use |
|-----------|---------|---------|-------------|
| dhparam.pem (pre-generated) | — | Diffie-Hellman key exchange params | Must be generated before first nginx HTTPS start |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| nginx + certbot sidecar | Traefik | Traefik auto-renews but adds complexity; REQUIREMENTS.md explicitly lists "Traefik" as Out of Scope [VERIFIED: .planning/REQUIREMENTS.md] |
| nginx + certbot sidecar | JonasAlfredsson/docker-nginx-certbot | Pre-built combined image, but hides internals and harder to debug |
| webroot renewal | standalone renewal | Standalone requires stopping nginx during renewal; webroot does not |

**Installation (docker-compose service, no npm/pip):** Image pulls happen automatically via Docker.

---

## Architecture Patterns

### Recommended Project Structure

```
nginx/
├── nginx.conf               # Main nginx config (http block, include conf.d/*)
├── conf.d/
│   ├── http-only.conf       # Phase 1 bootstrap: HTTP + ACME challenge only
│   └── default.conf         # Phase 2 production: HTTP redirect + HTTPS block
├── certbot/
│   ├── conf/                # Let's Encrypt certs (mounted from certbot-conf volume)
│   └── www/                 # ACME webroot challenge files (certbot-www volume)
└── scripts/
    └── init-letsencrypt.sh  # One-time bootstrap script (run manually on VPS)
```

### Pattern 1: Two-Phase Certbot Bootstrap

**What:** Nginx cannot reference non-existent cert files. Solve by starting nginx with HTTP-only config, issuing the cert, then reloading nginx with the HTTPS config.

**When to use:** First deploy to a new VPS where no cert exists yet.

**Step-by-step:**
1. Start nginx with `conf.d/http-only.conf` only (no SSL directives). Nginx binds port 80, serves `.well-known/acme-challenge/` from the webroot volume.
2. Run `certbot certonly --webroot --webroot-path /var/www/certbot -d yourdomain.com`. Certbot writes challenge files; nginx serves them; Let's Encrypt validates; cert lands in `/etc/letsencrypt/live/yourdomain.com/`.
3. Swap nginx config to `conf.d/default.conf` (full HTTPS) and reload: `docker compose exec nginx nginx -s reload`.
4. Subsequent renewals are handled automatically by the certbot sidecar container.

[CITED: ecostack.dev/posts/nginx-lets-encrypt-certificate-https-docker-compose]

### Pattern 2: Nginx Config Structure (production)

```nginx
# Source: nginx.org/en/docs/http/websocket.html + community patterns
# conf.d/default.conf

# HTTP → HTTPS redirect (NET-05)
server {
    listen 80;
    server_name yourdomain.com;

    # ACME challenge — must remain on port 80 for renewals
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    # Redirect everything else to HTTPS
    location / {
        return 301 https://$host$request_uri;
    }
}

# HTTPS main server (NET-01, NET-02, NET-03)
server {
    listen 443 ssl;
    server_name yourdomain.com;

    ssl_certificate     /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;
    ssl_dhparam         /etc/nginx/dhparam.pem;

    # Modern TLS settings
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers on;

    # Security headers
    add_header Strict-Transport-Security "max-age=31536000" always;

    # --- API Gateway (all /api/* traffic) ---
    location /api/ {
        proxy_pass http://rct-api-gateway:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # --- WebSocket / STOMP (SockJS) for notification-web ---
    # SockJS uses paths like /api/ws/websocket, /api/ws/info, /api/ws/{server}/{session}/websocket
    # These are proxied through api-gateway → notification-web, but nginx must handle the upgrade
    location /api/ws/ {
        proxy_pass http://rct-api-gateway:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 86400s;
    }

    # --- PWA (root path) ---
    location / {
        proxy_pass http://rct-pwa-nginx:80;
        proxy_set_header Host $host;
    }

    # --- Landing ---
    location /landing/ {
        proxy_pass http://rct-landing-nginx:80/;
        proxy_set_header Host $host;
    }

    # --- Admin Web Panel ---
    location /admin/ {
        proxy_pass http://rct-web-panel-nginx:80/;
        proxy_set_header Host $host;
    }

    # --- Mini App ---
    location /mini-app/ {
        proxy_pass http://rct-mini-app-nginx:80/;
        proxy_set_header Host $host;
    }
}
```

**CRITICAL NOTE on frontend path routing:** The frontend containers serve assets at `/` (root). When nginx proxies `/admin/` → `http://rct-web-panel-nginx:80/`, it strips the prefix via the trailing slash on `proxy_pass`. However, the Angular/React apps must be built with the correct `base` path so their internal asset references resolve correctly. This is a **build-time** concern, not just nginx config. Verify frontend build configs before finalising path assignments.

[CITED: nginx.org documentation; VERIFIED: frontend nginx.conf files in codebase]

### Pattern 3: Auto-Renewal Sidecar

```yaml
# In docker-compose.prod.yml
certbot:
  image: certbot/certbot
  container_name: rct-certbot
  volumes:
    - certbot-conf:/etc/letsencrypt
    - certbot-www:/var/www/certbot
  entrypoint: "/bin/sh -c 'trap exit TERM; while :; do certbot renew --quiet; sleep 12h; done'"
  restart: unless-stopped
```

The `nginx` service reloads certs daily via a separate cron or by restarting nginx:
```yaml
# Add to nginx service to pick up renewed certs
command: "/bin/sh -c 'while :; do sleep 6h; nginx -s reload; done & nginx -g \"daemon off;\"'"
```
[CITED: github.com/wmnnd/nginx-certbot; ecostack.dev/posts/nginx-lets-encrypt-certificate-https-docker-compose]

### Anti-Patterns to Avoid

- **Putting SSL certs inside the nginx image:** Certs expire; image rebuild is not the renewal path. Always use shared volumes.
- **Using `ports:` on any service other than nginx:** DOCK-06 requires only ports 80/443 exposed. All backend `expose` only. [VERIFIED: REQUIREMENTS.md DOCK-06]
- **Keeping `ports: "80:8080"` on api-gateway:** This conflicts with nginx taking port 80. Must change to `expose: "8080"`.
- **Missing ACME challenge path in HTTP redirect block:** If the redirect applies to ALL port 80 traffic (including `/.well-known/`), Let's Encrypt renewal fails. The ACME `location` block must come BEFORE the redirect.
- **Omitting WebSocket upgrade headers for `/api/ws/`:** SockJS connections will silently downgrade or fail. `proxy_http_version 1.1` + `Upgrade`/`Connection` headers are mandatory.
- **Forgetting dhparam generation:** Nginx will refuse to start if `ssl_dhparam` points to a missing file. Generate with `openssl dhparam -out dhparam.pem 2048` before first start.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| ACME certificate issuance | Custom curl-based ACME client | `certbot/certbot` image | ACME protocol has nonces, rate limits, key pinning — dozens of edge cases |
| Certificate renewal scheduling | Cron job inside nginx container | certbot sidecar + nginx reload loop | Separates concerns; certbot handles retry logic and staging/prod switching |
| SSL cipher configuration | Custom cipher string | Mozilla SSL Config Generator recommendation (TLSv1.2+TLSv1.3) | Mozilla maintains this for security; custom strings go stale |

**Key insight:** Certbot handles rate limits, retry backoff, certificate chains, and OCSP stapling automatically. Custom renewal scripts consistently miss these edge cases.

---

## Common Pitfalls

### Pitfall 1: Chicken-and-Egg — nginx won't start without cert, certbot needs nginx running

**What goes wrong:** If `default.conf` references `ssl_certificate` on a file that doesn't exist, nginx exits immediately with a fatal error. Certbot then cannot run because nginx is not serving port 80.

**Why it happens:** Both services are co-dependent at first deploy.

**How to avoid:** Use a two-config approach. `http-only.conf` has no SSL directives. Swap to `default.conf` only after certbot successfully issues the cert. The `init-letsencrypt.sh` script automates this sequence.

**Warning signs:** `nginx: [emerg] cannot load certificate` in container logs on first deploy.

[CITED: community.letsencrypt.org + ecostack.dev pattern]

### Pitfall 2: Let's Encrypt rate limits during testing

**What goes wrong:** Let's Encrypt enforces 5 duplicate certificate issuances per week per domain. Repeated test runs on the same domain exhaust the quota.

**Why it happens:** Each `certbot certonly` call counts as a new issuance.

**How to avoid:** Use `--staging` flag during all testing and development. Switch to production endpoint only for the real deploy. Staging certs are not trusted by browsers but do not consume production quota.

**Warning signs:** `Error: too many certificates already issued` in certbot output.

### Pitfall 3: ACME location block shadowed by HTTP redirect

**What goes wrong:** `return 301 https://$host$request_uri` in the HTTP server block intercepts `.well-known/acme-challenge/` requests, returning 301 to Let's Encrypt's ACME server which does not follow redirects.

**Why it happens:** Order of `location` blocks in nginx: exact match, then longest prefix. `location /` catches everything including the ACME path if `/.well-known/` is not listed first.

**How to avoid:** Place `location /.well-known/acme-challenge/ { root /var/www/certbot; }` before the `location / { return 301 ... }` block.

**Warning signs:** Certbot says `Error: Failed authorization procedure. ... 404` for the challenge URL.

### Pitfall 4: WebSocket connections drop after 60 seconds of inactivity

**What goes wrong:** nginx default `proxy_read_timeout` is 60 seconds. Idle STOMP/SockJS connections are silently terminated by nginx. Browser reconnects, creates notification subscription storm.

**Why it happens:** nginx treats idle WebSocket connection as a hung upstream.

**How to avoid:** Set `proxy_read_timeout 86400s` on the `/api/ws/` location block. STOMP heartbeats (configured at 10s in `WebSocketConfig.java`) keep the connection alive within that window.

**Warning signs:** WebSocket disconnects every 60s in browser console; Spring logs show reconnect events.

[VERIFIED: WebSocketConfig.java — heartbeat configured at Spring default 10s; CITED: nginx.org/en/docs/http/websocket.html]

### Pitfall 5: Frontend SPA routing broken at sub-paths

**What goes wrong:** Proxying `/admin/` to `rct-web-panel-nginx:80/` works for the index page, but deep routes like `/admin/users` return 404 from the upstream nginx because the upstream container handles requests at `/users` (after prefix strip), not at `/admin/users`.

**Why it happens:** The frontend nginx containers currently have `try_files $uri $uri/ /index.html` at their root location `/`. When the reverse proxy strips `/admin/` and forwards `/users` to the upstream, the upstream sees `/users` and falls through to `/index.html` — that part works. But Angular's `base-href` and React's `basename` must match the public path prefix for internal navigation links and asset references to resolve correctly.

**How to avoid:** Before finalizing path assignments, verify each frontend's build configuration:
- Angular (web-panel): `ng build --base-href /admin/`
- React (pwa): `vite.config` `base: '/'` — if PWA stays at root this is fine
- React (mini-app): check if it's deployed at a sub-path or a separate domain
- Landing: static site, no SPA routing concern

**Warning signs:** Page loads on `/admin/` but clicking internal links returns 404; asset references return 404.

### Pitfall 6: api-gateway port 80 conflicts with nginx

**What goes wrong:** `docker-compose.prod.yml` currently has `ports: "80:8080"` on `api-gateway`. Adding nginx with `ports: "80:80"` causes "address already in use" at container start.

**Why it happens:** Only one process can bind a host port at a time.

**How to avoid:** Change `api-gateway` from `ports:` to `expose:` in `docker-compose.prod.yml`. Nginx becomes the sole owner of host ports 80 and 443.

**Warning signs:** `Error starting userland proxy: listen tcp4 0.0.0.0:80: bind: address already in use` when running `docker compose up`.

[VERIFIED: docker-compose.prod.yml line 110-111]

---

## Code Examples

### Docker Compose nginx + certbot services

```yaml
# Source: community patterns (wmnnd/nginx-certbot, ecostack.dev) — adapted for this project

nginx:
  image: nginx:1.27-alpine
  container_name: rct-nginx
  ports:
    - "80:80"
    - "443:443"
  volumes:
    - ./nginx/conf.d:/etc/nginx/conf.d:ro
    - ./nginx/dhparam.pem:/etc/nginx/dhparam.pem:ro
    - certbot-conf:/etc/letsencrypt:ro
    - certbot-www:/var/www/certbot:ro
  networks:
    - private_net
  depends_on:
    api-gateway:
      condition: service_healthy
  restart: unless-stopped
  # Reload nginx every 6h to pick up renewed certs
  command: "/bin/sh -c 'while :; do sleep 6h; nginx -s reload; done & nginx -g \"daemon off;\"'"

certbot:
  image: certbot/certbot
  container_name: rct-certbot
  volumes:
    - certbot-conf:/etc/letsencrypt
    - certbot-www:/var/www/certbot
  entrypoint: "/bin/sh -c 'trap exit TERM; while :; do certbot renew --quiet; sleep 12h; done'"
  restart: unless-stopped

# Add to volumes section:
# certbot-conf:
# certbot-www:
```

### api-gateway port change (REQUIRED)

```yaml
# Before (current docker-compose.prod.yml line 110-111):
#   ports:
#     - "80:8080"

# After:
  expose:
    - "8080"
```

### Bootstrap script skeleton

```bash
#!/bin/bash
# nginx/scripts/init-letsencrypt.sh
# Run ONCE on first deploy. Never run again (certbot sidecar handles renewals).

DOMAIN="yourdomain.com"
EMAIL="admin@yourdomain.com"

# Step 1: Start nginx with HTTP-only config (no SSL references)
docker compose up -d nginx

# Step 2: Issue certificate (staging first to test)
docker compose run --rm certbot certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --email "$EMAIL" \
  --agree-tos \
  --no-eff-email \
  --staging \
  -d "$DOMAIN"

# Step 3: If staging succeeds, re-run without --staging
# Then reload nginx to pick up the real cert
docker compose exec nginx nginx -s reload
```

### ACME challenge location block (critical order)

```nginx
# Source: nginx.org docs + Let's Encrypt community — MUST come before return 301
server {
    listen 80;
    server_name yourdomain.com;

    # FIRST: ACME challenge (Let's Encrypt renewal)
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    # SECOND: Everything else → HTTPS
    location / {
        return 301 https://$host$request_uri;
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| certbot standalone (stops nginx) | certbot webroot (nginx stays up) | ~2018 | Zero-downtime renewals |
| TLSv1.0/1.1 support | TLSv1.2 + TLSv1.3 only | 2020 (browser enforcement) | Must not configure TLSv1 or TLSv1.1 |
| HTTP/1.0 for proxied connections | `proxy_http_version 1.1` | Required for WebSocket | Default HTTP/1.0 breaks WebSocket upgrade |

**Deprecated/outdated:**
- `ssl_ciphers` with RC4 or 3DES: removed from modern configs; use Mozilla's recommended cipher string
- `certbot --standalone`: conflicts with running nginx; replaced by `--webroot` in always-on scenarios
- Separate renewal cron on the host OS: unnecessary with sidecar container approach

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Domain name is a single domain (not multiple subdomains) | Architecture Patterns | Multiple domains require separate certbot `-d` flags and nginx `server_name` entries |
| A2 | Frontend path assignments: PWA at `/`, web-panel at `/admin/`, mini-app at `/mini-app/`, landing at `/landing/` | Architecture Patterns | If frontends are built for different base paths, nginx routing must match or assets break |
| A3 | Frontends are currently built for `base: '/'` and will need `--base-href` rebuild if served at sub-paths | Pitfall 5 | Asset 404s and broken SPA navigation |
| A4 | VPS is accessible from the internet on ports 80 and 443 before certbot runs | Common Pitfalls | Certbot ACME challenge will fail if firewall blocks port 80 |

**A1 note:** The phases-plan.md mentions "Новый сервер, новый домен" (new server, new domain) — single domain assumed. [VERIFIED: docs/phases-plan.md]

---

## Open Questions

1. **Frontend path assignment — which app goes at root `/`?**
   - What we know: PWA is the primary mobile client used by students; landing is a static informational site.
   - What's unclear: Should PWA or landing be at root `/`? Should mini-app and web-panel be at sub-paths or subdomains?
   - Recommendation: PWA at root `/` (student-facing primary app), landing at `/`, admin at `/admin/`, mini-app stays separate (Telegram Mini App uses its own URL, not routed through nginx typically). Planner should confirm with user.

2. **Frontend base-href / basename reconfiguration**
   - What we know: All 4 frontend containers currently serve at `/` (their nginx.conf shows `root /usr/share/nginx/html` with `location /`).
   - What's unclear: Do Angular (web-panel) and React (pwa, mini-app) apps need rebuild with new base paths? If yes, this is a Phase 42 concern (Dockerfiles) not Phase 44.
   - Recommendation: If sub-path routing is used for frontends, ensure Phase 42 Dockerfiles pass the correct build-time base path argument (`--build-arg BASE_HREF=/admin/`). Otherwise route all frontends from their own subdomains — simpler but requires wildcard cert or multiple SAN entries.

3. **Domain name**
   - What we know: Not specified in any planning file; referred to as `yourdomain.com` placeholder.
   - What's unclear: Actual domain to use in nginx `server_name` and certbot `-d` flag.
   - Recommendation: Planner should add `DOMAIN` as a variable in `.env.prod` and reference it in the bootstrap script. Nginx config can use a placeholder that operators replace during deployment.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Docker | nginx + certbot containers | ✓ (dev machine) | present | — |
| Internet access on VPS port 80 | Let's Encrypt ACME HTTP-01 | Unknown (VPS not yet provisioned) | — | Cannot fallback — must be open |
| Valid domain pointing to VPS IP | certbot domain validation | Unknown | — | Cannot issue cert without DNS record |

**Missing dependencies with no fallback:**
- Valid domain with DNS A record pointing to the VPS public IP. Without this, Let's Encrypt HTTP-01 challenge always fails. This is an **operator prerequisite**, not a code task.
- Port 80 and 443 open in VPS firewall before certbot runs.

**Missing dependencies with fallback:**
- During development/testing: use `--staging` certbot flag to avoid rate limits and get self-signed-equivalent certs for config validation.

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Handled by auth-service behind gateway |
| V3 Session Management | no | Handled by JWT in auth-service |
| V4 Access Control | no | Handled by api-gateway JWT filter |
| V5 Input Validation | no | nginx does not parse request bodies |
| V6 Cryptography | yes | TLS 1.2+1.3 only; no custom crypto |

### Known Threat Patterns for Nginx SSL Termination

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Protocol downgrade (TLSv1.0/1.1) | Tampering | `ssl_protocols TLSv1.2 TLSv1.3` only |
| Missing HSTS | Tampering | `add_header Strict-Transport-Security "max-age=31536000" always` |
| Certificate not renewed (90-day expiry) | Denial of Service | Certbot sidecar with 12h renewal check loop |
| Internal services exposed via host ports | Elevation of Privilege | All services use `expose` not `ports`; only nginx has host port binding [VERIFIED: DOCK-06] |

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: docker-compose.prod.yml] — service topology, container names, port bindings, network name `private_net`
- [VERIFIED: services/api-gateway/src/main/resources/application.yml] — gateway routes, WebSocket/push paths
- [VERIFIED: services/notification-service/.../WebSocketConfig.java] — STOMP endpoint `/ws`, SockJS enabled, heartbeat 10s
- [VERIFIED: frontends/pwa/nginx.conf, frontends/mini-app/nginx.conf, frontends/web-panel/nginx.conf, frontends/landing/nginx.conf] — all frontends serve on port 80 at root `/`
- [CITED: nginx.org/en/docs/http/websocket.html] — WebSocket proxy headers
- [CITED: letsencrypt.org] — certbot webroot plugin, rate limits, 90-day cert lifetime

### Secondary (MEDIUM confidence)
- [CITED: ecostack.dev/posts/nginx-lets-encrypt-certificate-https-docker-compose] — 2-phase bootstrap pattern, docker compose architecture
- [CITED: github.com/wmnnd/nginx-certbot] — nginx reload loop + certbot sidecar entrypoint pattern

### Tertiary (LOW confidence)
- WebSearch results on SPA sub-path routing — framework-specific base path configuration needs verification against actual frontend build configs

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — nginx and certbot/certbot are the only sensible choices given project constraints
- Architecture patterns: HIGH — nginx WebSocket proxy, certbot webroot pattern, and 2-phase bootstrap are well-documented and verified
- Pitfalls: HIGH — all 6 pitfalls are directly derived from verified codebase state (port conflict, WebSocket timeout) or documented Let's Encrypt behavior
- Frontend path routing: MEDIUM — path assignments are assumed (A2, A3); depend on build-time base path configuration not yet verified

**Research date:** 2026-04-07
**Valid until:** 2026-05-07 (stable ecosystem — nginx and certbot APIs change slowly)
