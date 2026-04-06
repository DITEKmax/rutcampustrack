# Phase 33: Infrastructure — Research

**Researched:** 2026-04-06
**Domain:** nginx, Docker Compose, Spring Cloud Gateway CORS, URL layout
**Confidence:** HIGH

---

## Summary

Phase 33 is pure infrastructure scaffolding with no novel technology. The codebase already has a working precedent for every deliverable: Phase 28 added the PWA nginx container and configured Gateway CORS — this phase repeats the same pattern three times (Mini App, Web Panel, Landing) and makes two targeted edits to the JWT filter's `PUBLIC_PATHS`.

The three nginx containers are identical in structure to the existing `pwa-nginx` container. The CORS expansion is a YAML list append. The `PUBLIC_PATHS` change is a two-line Java Set modification. The main planning decision is the URL layout — which port numbers each container exposes — and that decision must be captured in a decision document so Phases 35-40 can reference it without ambiguity.

No new libraries, no new frameworks. Research confirms that all required patterns are already validated in the project and no dependency investigation is needed.

**Primary recommendation:** Mirror Phase 28's exact pattern for nginx + docker-compose. Decide URL layout first (task 1), then propagate the ports into nginx configs, docker-compose, and the Gateway CORS allowed-origins list.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFRA-01 | URL layout decided and documented for all frontends (PWA, Mini App, Web Panel, Landing) | Decision document with port assignments; no routing conflicts if each frontend uses a unique host port |
| INFRA-02 | nginx configs scaffolded for Mini App, Web Panel, and Landing containers | Mirrors `frontends/pwa/nginx.conf` — SPA fallback for Mini App, same for Web Panel, static-only for Landing |
| INFRA-03 | docker-compose updated with Mini App, Web Panel, and Landing nginx containers | Mirrors existing `pwa-nginx` service block; placeholder `dist/` dirs or inline HTML needed |
| INFRA-04 | Gateway CORS expanded with Mini App and Web Panel dev/prod origins | Append to `allowed-origins` list in `application.yml`; Mini App dev port TBD from INFRA-01 |
| INFRA-05 | Gateway PUBLIC_PATHS updated with `/api/auth/tma` and `/api/auth/refresh-body` | Two-line change to `JwtAuthenticationFilter.PUBLIC_PATHS` Set |
</phase_requirements>

---

## Project Constraints (from CLAUDE.md)

- Java 21 + Spring Boot 3.4 — applies to Gateway changes
- No Lombok in `*-api-contract` modules — not relevant here (JwtAuthenticationFilter is in `api-gateway`, not a contract module)
- All REST via Gateway, prefix `/api/{service}/...` — TMA auth endpoints follow `/api/auth/tma` and `/api/auth/refresh-body`
- Docker Compose for all containers; network `private_net` (bridge) used by all services
- nginx image: `nginx:1.27-alpine` (current project standard, see `pwa-nginx`)
- No Flyway/DB changes in this phase
- `ddl-auto: validate` — not relevant

---

## Standard Stack

### Core (all already in use in this project)

| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| nginx | 1.27-alpine | Static file serving + SPA fallback | Already used for PWA; same image |
| Docker Compose | 3.9 (project version) | Container orchestration | Already in use; same file |
| Spring Cloud Gateway `globalcors` | 4.x (Spring Boot 3.4) | CORS configuration | Already configured; YAML-only change |
| `JwtAuthenticationFilter` | project-local | PUBLIC_PATHS whitelist | Already exists; Set.of() modification |

**No new dependencies to install.** All changes are configuration and Java Set edits.

---

## Architecture Patterns

### Existing Pattern: PWA nginx container (Phase 28)

This is the canonical template for all three new containers.

```yaml
# docker-compose.yml — existing pwa-nginx (verbatim from codebase)
pwa-nginx:
  image: nginx:1.27-alpine
  container_name: rct-pwa-nginx
  ports:
    - "80:80"
  volumes:
    - ./frontends/pwa/dist:/usr/share/nginx/html:ro
    - ./frontends/pwa/nginx.conf:/etc/nginx/conf.d/default.conf:ro
  networks:
    - private_net
  restart: unless-stopped
```

```nginx
# frontends/pwa/nginx.conf — existing (verbatim from codebase)
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location ~* ^/(sw\.js|index\.html)$ {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        add_header Pragma "no-cache";
        expires 0;
    }

    location ~* \.(js|css|png|jpg|jpeg|svg|ico|woff|woff2)$ {
        add_header Cache-Control "public, max-age=31536000, immutable";
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

### Pattern 1: URL Layout (INFRA-01 decision)

**What:** Assign unique host ports to each frontend to avoid conflicts. All containers listen on port 80 internally; host port differs per container.

**Recommended port assignment** [ASSUMED — no locked decision exists yet]:

| Frontend | Container port | Host port | Dev Vite port |
|----------|---------------|-----------|---------------|
| PWA (existing) | 80 | 80 | 5173 |
| Mini App | 80 | 3000 | 5174 |
| Web Panel | 80 | 4200 | 4200 (Angular default) |
| Landing | 80 | 8081 | n/a (static) |

These are conventional defaults — PWA is already on 80, Angular CLI defaults to 4200, 3000 is a common React dev port. The planner should confirm or adjust these assignments in the decision document (INFRA-01 deliverable).

### Pattern 2: nginx config variants

**Mini App** (React SPA — identical to PWA except no `sw.js` special case):
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location ~* \.(js|css|png|jpg|jpeg|svg|ico|woff|woff2)$ {
        add_header Cache-Control "public, max-age=31536000, immutable";
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

**Web Panel** (Angular SPA — same SPA fallback pattern):
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location ~* \.(js|css|png|jpg|jpeg|svg|ico|woff|woff2|eot|ttf)$ {
        add_header Cache-Control "public, max-age=31536000, immutable";
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

**Landing** (static HTML — no SPA fallback needed, simpler config):
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location ~* \.(js|css|png|jpg|jpeg|svg|ico|woff|woff2)$ {
        add_header Cache-Control "public, max-age=31536000, immutable";
    }

    location / {
        try_files $uri $uri/ =404;
    }
}
```

### Pattern 3: Gateway CORS expansion (INFRA-04)

Current state — `allowed-origins` in `application.yml`:
```yaml
allowed-origins:
  - "http://localhost:5173"   # PWA dev
  - "http://localhost:80"     # PWA prod
```

After expansion (ports depend on INFRA-01 decision):
```yaml
allowed-origins:
  - "http://localhost:5173"   # PWA dev
  - "http://localhost:80"     # PWA prod (pwa-nginx)
  - "http://localhost:5174"   # Mini App dev (Vite)
  - "http://localhost:3000"   # Mini App prod (mini-app-nginx)
  - "http://localhost:4200"   # Web Panel dev (Angular CLI) + prod (web-panel-nginx)
```

**Key constraint from STATE.md:** "Phase 33 must add new origins without breaking existing CORS" — the OPTIONS bypass before `isPublicRoute` is already in place (validated in Phase 28). The YAML append is safe because `DedupeResponseHeader` filter is already configured.

### Pattern 4: PUBLIC_PATHS expansion (INFRA-05)

Current state in `JwtAuthenticationFilter.java`:
```java
private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/public-key"
);
```

After expansion:
```java
private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/public-key",
        "/api/auth/tma",
        "/api/auth/refresh-body"
);
```

**Note on HTTP method:** The success criterion says `GET /api/auth/tma` and `GET /api/auth/refresh-body`, but Phase 34 will implement these as `POST` (they send request bodies). The `PUBLIC_PATHS` check is path-only and method-agnostic, so the path whitelisting works regardless of method. The planner should be aware that Phase 34 will actually use POST, but the filter change is correct either way.

### Pattern 5: Placeholder HTML for docker-compose smoke test

Each new nginx container needs a placeholder `index.html` so `docker compose up` can verify it starts and serves. Use a minimal file in each frontend directory:

```html
<!DOCTYPE html>
<html lang="ru">
<head><meta charset="UTF-8"><title>Mini App — placeholder</title></head>
<body><h1>Mini App placeholder</h1></body>
</html>
```

Two options for where to put it:
1. `frontends/mini-app/dist/index.html` — mirrors PWA pattern (volume mounts `dist/`)
2. Use a Docker `COPY` in a Dockerfile — more complex, not needed for scaffold

**Recommendation:** Option 1 (commit a `dist/index.html` placeholder). The volume mount approach is already established by `pwa-nginx`. Phases 36/38/35 will replace `dist/` with the real build output.

### Anti-Patterns to Avoid

- **Hardcoding production domain names** in `allowed-origins` at this stage — production URLs are unknown; add only dev localhost origins now, production origins can be added via env vars or later phases.
- **Using `allowed-origin-patterns`** instead of `allowed-origins` — wildcards bypass the security intent and break `allow-credentials: true` per CORS spec. [VERIFIED: Spring Cloud Gateway docs require exact origins when `allow-credentials: true`]
- **Forgetting `networks: private_net`** on the new containers — they must be on the same network as the Gateway if they ever need to reach backend services.
- **Not creating placeholder `dist/` directories** — Docker volume mounts fail silently or produce 403 if the host path doesn't exist.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SPA client-side routing | Custom nginx `rewrite` rules | `try_files $uri $uri/ /index.html` | Already established pattern; rewrites are complex and fragile |
| CORS headers in nginx | `add_header Access-Control-Allow-Origin` in nginx | Spring Cloud Gateway `globalcors` | Single source of truth for CORS; nginx CORS would duplicate and conflict |
| JWT bypass in nginx | nginx `auth_request` or custom logic | `PUBLIC_PATHS` in `JwtAuthenticationFilter` | Gateway is the auth enforcement point; nginx is a static server only |

---

## Common Pitfalls

### Pitfall 1: `allow-credentials: true` breaks with wildcard origins
**What goes wrong:** Adding `"*"` to `allowed-origins` causes browsers to reject the response when `allow-credentials: true` is set (CORS spec requirement).
**Why it happens:** The CORS spec forbids credential-sharing with wildcard origins.
**How to avoid:** Always use explicit origins (exact `http://host:port` strings). [VERIFIED: MDN CORS documentation]
**Warning signs:** Browser console shows "credentialed requests cannot use wildcard" error.

### Pitfall 2: Missing `restart: unless-stopped` on nginx containers
**What goes wrong:** After VPS restart, only infrastructure containers (Postgres, Redis) come back up; nginx containers stay down.
**Why it happens:** `restart: unless-stopped` is not the Docker default.
**How to avoid:** Copy from `pwa-nginx` service which already has this.

### Pitfall 3: nginx volume mount path doesn't exist on host
**What goes wrong:** Docker creates the host path as a directory with root ownership, nginx cannot write index.html, serves 403.
**Why it happens:** Docker auto-creates missing bind-mount paths but they're owned by root.
**How to avoid:** Create `frontends/mini-app/dist/`, `frontends/web-panel/dist/`, `frontends/landing/dist/` with placeholder `index.html` files before `docker compose up`.

### Pitfall 4: Telegram Mini App origin is `https://` (not `http://`)
**What goes wrong:** In production, the Mini App is served from Telegram's CDN over HTTPS. CORS allowed-origin must match the actual scheme.
**Why it happens:** Dev origins are `http://localhost:...`, but TMA served from Telegram is `https://...`.
**How to avoid:** For now (Phase 33 is dev scaffold only), localhost HTTP origins are sufficient. The production origin will be known once the Mini App is deployed to Telegram — defer prod origin to Phase 36/37.
**Warning signs:** OPTIONS preflight returns mismatched origin in `Access-Control-Allow-Origin`.

### Pitfall 5: PUBLIC_PATHS uses `Set.of()` — method matters but path check is method-agnostic
**What goes wrong:** Success criterion says `GET /api/auth/tma` but Phase 34 will implement `POST /api/auth/tma`. The filter does path-only matching, so this is not an actual bug — but a misleading success criterion.
**Why it happens:** The SC was written before the HTTP method was confirmed.
**How to avoid:** The path whitelist change is correct regardless. Note in plans that the SC's mention of `GET` is a placeholder; the actual method is `POST`.

---

## Code Examples

### Existing CORS config (verbatim from codebase — source of truth)

```yaml
# services/api-gateway/src/main/resources/application.yml
spring:
  cloud:
    gateway:
      globalcors:
        add-to-simple-url-handler-mapping: true
        cors-configurations:
          '[/**]':
            allowed-origins:
              - "http://localhost:5173"
              - "http://localhost:80"
            allowed-methods:
              - GET
              - POST
              - PUT
              - PATCH
              - DELETE
              - OPTIONS
            allowed-headers: "*"
            allow-credentials: true
            max-age: 3600
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE
```

### Existing JwtAuthenticationFilter pattern (verbatim)

The OPTIONS bypass and PUBLIC_PATHS check are already in place:
```java
// Option bypass — line 51 (VERIFIED in codebase)
if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
    return chain.filter(exchange);
}

// PUBLIC_PATHS — lines 32-36 (VERIFIED in codebase)
private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/public-key"
);
```

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker / docker compose | INFRA-02, INFRA-03 | [ASSUMED: available — used throughout project] | — | — |
| nginx:1.27-alpine image | INFRA-02, INFRA-03 | ✓ (already pulled for pwa-nginx) | 1.27-alpine | — |
| Port 3000 | mini-app-nginx | [ASSUMED: free on dev machine] | — | Use 3001 |
| Port 4200 | web-panel-nginx | [ASSUMED: free on dev machine] | — | Use 4201 |
| Port 8081 | landing-nginx | [ASSUMED: free on dev machine] | — | Use 8082 |

**Missing dependencies with no fallback:** None identified.

**Missing dependencies with fallback:** Port conflicts — if any chosen port is in use, simply pick the next available port.

---

## Validation Architecture

`workflow.nyquist_validation` is absent from `.planning/config.json` — treated as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | vitest (PWA frontend) + manual curl/browser verification (infra) |
| Config file | `frontends/pwa/vitest.config.ts` (existing) |
| Quick run command | `curl -I http://localhost:PORT/` |
| Full suite command | `docker compose up -d && curl tests` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | Notes |
|--------|----------|-----------|-------------------|-------|
| INFRA-01 | URL layout document exists | manual | read file | Document review |
| INFRA-02 | nginx configs exist and serve placeholder | smoke | `curl -s http://localhost:3000/` | After `docker compose up` |
| INFRA-03 | docker compose starts 3 new containers | smoke | `docker compose ps` | Check `rct-mini-app-nginx`, `rct-web-panel-nginx`, `rct-landing-nginx` Up |
| INFRA-04 | OPTIONS preflight returns correct ACAO header | smoke | `curl -i -X OPTIONS -H "Origin: http://localhost:5174" http://localhost:8080/api/auth/login` | Verify `Access-Control-Allow-Origin: http://localhost:5174` |
| INFRA-05 | Unauthenticated POST to /api/auth/tma returns not-401 | smoke | `curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/auth/tma` | Expect 404 or 405 (route exists in Phase 34), not 401 |

### Wave 0 Gaps

None — this phase has no automated test files to create. All verification is via smoke curl commands and docker compose status. No vitest tests needed for nginx/docker-compose/YAML changes.

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — |
| V3 Session Management | no | — |
| V4 Access Control | yes (INFRA-05) | PUBLIC_PATHS whitelist — only exact paths whitelisted, not prefixes |
| V5 Input Validation | no | — |
| V6 Cryptography | no | — |

### Known Threat Patterns for nginx + CORS

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| CORS wildcard bypass | Spoofing | Use explicit origin list, never `"*"` with credentials |
| Path traversal via nginx | Tampering | `root` directive + `try_files` — no `alias` directives |
| Over-broad PUBLIC_PATHS | Elevation of Privilege | Whitelist exact paths only; `refresh-body` must be POST-only in Phase 34 to prevent GET abuse |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Port 3000 → mini-app-nginx, 4200 → web-panel-nginx, 8081 → landing-nginx | URL Layout | Port conflict on dev machine; trivial to adjust |
| A2 | Mini App dev Vite port is 5174 (5173 taken by PWA) | CORS origins | Wrong origin whitelisted; Phase 36 will confirm actual port |
| A3 | Production TMA origin deferred to Phase 36/37 | CORS origins | No prod impact at Phase 33 scope; scaffold only |
| A4 | Docker is available and running on dev machine | Environment | Blocks all INFRA-02/03 work; known to be working (used in all previous phases) |

---

## Open Questions

1. **What are the exact host ports for the three new containers?**
   - What we know: PWA uses 80; other ports are unassigned
   - What's unclear: Chosen port numbers (3000/4200/8081 are assumptions)
   - Recommendation: Decide in the INFRA-01 decision document as task 1 of the plan; all subsequent tasks reference that document

2. **Does the Mini App dev origin differ from Vite default?**
   - What we know: PWA uses Vite on 5173; Mini App will also use Vite
   - What's unclear: Mini App Vite config port (Phase 36 work)
   - Recommendation: Use 5174 as placeholder in CORS config; Phase 36 can adjust if needed

---

## Sources

### Primary (HIGH confidence)
- Codebase: `services/api-gateway/src/main/resources/application.yml` — current CORS config (VERIFIED)
- Codebase: `services/api-gateway/src/main/java/.../JwtAuthenticationFilter.java` — PUBLIC_PATHS (VERIFIED)
- Codebase: `docker-compose.yml` — existing container patterns (VERIFIED)
- Codebase: `frontends/pwa/nginx.conf` — nginx pattern to replicate (VERIFIED)
- Codebase: `.planning/STATE.md` — "OPTIONS bypass before isPublicRoute established; Phase 33 must add origins without breaking" (VERIFIED)

### Secondary (MEDIUM confidence)
- CORS spec: credentials + wildcard forbidden — [CITED: MDN Web Docs CORS]
- Spring Cloud Gateway globalcors `allow-credentials` + exact origins requirement — [CITED: Spring Cloud Gateway reference docs]

### Tertiary (LOW confidence)
- None

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all components already in use in this project
- Architecture: HIGH — direct replication of Phase 28 patterns
- Pitfalls: HIGH — documented from actual Phase 28 implementation history
- URL layout port assignments: LOW — assumed, must be decided in INFRA-01 document

**Research date:** 2026-04-06
**Valid until:** Stable — nginx/docker-compose patterns do not change rapidly. CORS spec is permanent.
