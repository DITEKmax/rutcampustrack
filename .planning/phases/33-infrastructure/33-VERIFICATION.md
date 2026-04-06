---
phase: 33-infrastructure
verified: 2026-04-06T19:15:00Z
status: passed
score: 15/15 must-haves verified
gaps: []
deferred: []
---

# Phase 33: Infrastructure Verification Report

**Phase Goal:** Nginx scaffolding for Mini App / Web Panel / Landing containers, URL layout documentation, and Gateway CORS + PUBLIC_PATHS expansion for new frontend origins
**Verified:** 2026-04-06T19:15:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | docs/url-layout.md exists with port assignments for all 4 frontends | VERIFIED | File at docs/url-layout.md; contains PWA:80, Mini App:3000, Web Panel:4200, Landing:8081, all 4 container names |
| 2 | frontends/mini-app/nginx.conf has SPA try_files fallback | VERIFIED | `try_files $uri $uri/ /index.html;` on line 13 |
| 3 | frontends/web-panel/nginx.conf has SPA try_files fallback + eot/ttf | VERIFIED | `try_files $uri $uri/ /index.html;` on line 14; eot/ttf in asset regex on line 8 |
| 4 | frontends/landing/nginx.conf has try_files with =404 | VERIFIED | `try_files $uri $uri/ =404;` on line 13 |
| 5 | frontends/mini-app/dist/index.html contains "Mini App" | VERIFIED | `<title>Mini App — placeholder</title>` and `<h1>Mini App placeholder</h1>` |
| 6 | frontends/web-panel/dist/index.html contains "Web Panel" | VERIFIED | `<title>Web Panel — placeholder</title>` and `<h1>Web Panel placeholder</h1>` |
| 7 | frontends/landing/dist/index.html contains "Landing" | VERIFIED | `<title>Landing — placeholder</title>` and `<h1>Landing placeholder</h1>` |
| 8 | docker-compose.yml has mini-app-nginx, web-panel-nginx, landing-nginx services | VERIFIED | All three services present with `container_name: rct-{name}-nginx` |
| 9 | docker-compose.yml has correct port mappings: 3000:80, 4200:80, 8081:80 | VERIFIED | mini-app-nginx→3000:80, web-panel-nginx→4200:80, landing-nginx→8081:80 |
| 10 | docker-compose.yml volume mounts reference correct nginx.conf paths | VERIFIED | Each service mounts `./frontends/{name}/dist:/usr/share/nginx/html:ro` and `./frontends/{name}/nginx.conf:/etc/nginx/conf.d/default.conf:ro` |
| 11 | application.yml has exactly 5 CORS origins (5173, 80, 5174, 3000, 4200) | VERIFIED | Lines 15-19 of application.yml list all 5 explicit localhost origins |
| 12 | No wildcard in CORS allowed-origins | VERIFIED | `allowed-origins` section contains only 5 explicit `http://localhost:PORT` entries; `allowed-headers: "*"` is header wildcarding only, unrelated to origins |
| 13 | JwtAuthenticationFilter.java has /api/auth/tma in PUBLIC_PATHS | VERIFIED | Line 36: `"/api/auth/tma"` in PUBLIC_PATHS Set |
| 14 | JwtAuthenticationFilter.java has /api/auth/refresh-body in PUBLIC_PATHS | VERIFIED | Line 37: `"/api/auth/refresh-body"` in PUBLIC_PATHS Set |
| 15 | Original 3 PUBLIC_PATHS entries still present | VERIFIED | Lines 33-35: /api/auth/login, /api/auth/refresh, /api/auth/public-key all present |

**Score:** 15/15 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `docs/url-layout.md` | Port assignments for 4 frontends | VERIFIED | Contains all 4 ports, dev ports, container names, CORS origins table |
| `frontends/mini-app/nginx.conf` | React SPA nginx config with try_files | VERIFIED | 15 lines, proper SPA fallback, asset cache headers |
| `frontends/web-panel/nginx.conf` | Angular SPA nginx config with try_files + eot/ttf | VERIFIED | 16 lines, SPA fallback, eot/ttf in asset regex |
| `frontends/landing/nginx.conf` | Static HTML nginx config with =404 | VERIFIED | 15 lines, =404 instead of SPA fallback |
| `frontends/mini-app/dist/index.html` | Placeholder HTML (Phase 36 target) | VERIFIED | Intentional placeholder with explicit label |
| `frontends/web-panel/dist/index.html` | Placeholder HTML (Phase 38 target) | VERIFIED | Intentional placeholder with explicit label |
| `frontends/landing/dist/index.html` | Placeholder HTML (Phase 35 target) | VERIFIED | Intentional placeholder with explicit label |
| `services/api-gateway/src/main/resources/application.yml` | 5 CORS origins, no wildcard | VERIFIED | 5 explicit localhost origins, no `*` in allowed-origins |
| `services/api-gateway/.../JwtAuthenticationFilter.java` | 5 PUBLIC_PATHS entries | VERIFIED | Set.of with exactly 5 entries |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| mini-app-nginx service | frontends/mini-app/dist/ | volume mount :ro | WIRED | `./frontends/mini-app/dist:/usr/share/nginx/html:ro` |
| mini-app-nginx service | frontends/mini-app/nginx.conf | volume mount :ro | WIRED | `./frontends/mini-app/nginx.conf:/etc/nginx/conf.d/default.conf:ro` |
| web-panel-nginx service | frontends/web-panel/dist/ | volume mount :ro | WIRED | `./frontends/web-panel/dist:/usr/share/nginx/html:ro` |
| landing-nginx service | frontends/landing/dist/ | volume mount :ro | WIRED | `./frontends/landing/dist:/usr/share/nginx/html:ro` |
| Gateway CORS | Mini App dev (5174) | allowed-origins list | WIRED | `http://localhost:5174` in application.yml |
| Gateway CORS | Mini App prod (3000) | allowed-origins list | WIRED | `http://localhost:3000` in application.yml |
| Gateway CORS | Web Panel (4200) | allowed-origins list | WIRED | `http://localhost:4200` in application.yml |
| JWT filter | /api/auth/tma | PUBLIC_PATHS Set.of() | WIRED | Path bypasses JWT validation in isPublicRoute() |
| JWT filter | /api/auth/refresh-body | PUBLIC_PATHS Set.of() | WIRED | Path bypasses JWT validation in isPublicRoute() |

### Data-Flow Trace (Level 4)

Not applicable — this phase delivers static infrastructure scaffolding (nginx configs, docker-compose service definitions, gateway config). No dynamic data rendering.

### Behavioral Spot-Checks

| Behavior | Artifact | Result | Status |
|----------|----------|--------|--------|
| All 3 commits exist in git history | 946aa3e, 30b83b2, fe9a425 | All 3 confirmed present with correct commit messages | PASS |
| nginx.conf files are non-empty and structurally valid | 3x nginx.conf | Each file 15-16 lines, valid server block with listen, root, index, location directives | PASS |
| CORS 5-origin list complete | application.yml lines 15-19 | All 5 origins present, none wildcard | PASS |
| PUBLIC_PATHS 5-entry set complete | JwtAuthenticationFilter.java lines 33-38 | All 5 entries present | PASS |

### Requirements Coverage

| Requirement | Source | Description | Status | Evidence |
|-------------|--------|-------------|--------|----------|
| INFRA-01 (v7.0) | 33-VALIDATION.md | URL layout doc and port reference | SATISFIED | docs/url-layout.md with all 4 ports, container names, CORS origins |
| INFRA-02 (v7.0) | 33-VALIDATION.md | nginx containers for Mini App, Web Panel, Landing | SATISFIED | 3 service blocks in docker-compose.yml, each with nginx:1.27-alpine |
| INFRA-03 (v7.0) | 33-VALIDATION.md | docker-compose services for all 3 nginx containers | SATISFIED | mini-app-nginx, web-panel-nginx, landing-nginx all present |
| INFRA-04 (v7.0) | 33-VALIDATION.md | Gateway CORS with explicit origin list, no wildcard | SATISFIED | 5 explicit localhost origins, no `*` in allowed-origins |
| INFRA-05 (v7.0) | 33-VALIDATION.md | PUBLIC_PATHS whitelist with /api/auth/tma and /api/auth/refresh-body | SATISFIED | Both paths in PUBLIC_PATHS Set.of() |

Note: INFRA-01 through INFRA-05 here refer to v7.0 phase-internal requirement labels as documented in 33-VALIDATION.md, not the v6.0 REQUIREMENTS.md identifiers (which belong to Phases 20 and 28).

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| frontends/mini-app/dist/index.html | Placeholder HTML | Info | Intentional — Phase 36 replaces with real Mini App build |
| frontends/web-panel/dist/index.html | Placeholder HTML | Info | Intentional — Phase 38 replaces with real Web Panel build |
| frontends/landing/dist/index.html | Placeholder HTML | Info | Intentional — Phase 35 replaces with real Landing build |

The three placeholder HTML files are classified as Info (not blockers) because they are explicitly documented as intentional in both the SUMMARY.md "Known Stubs" section and the placeholder text itself. The nginx containers require a file at the root to serve — these placeholders serve that function until the real frontend builds are generated in later phases.

### Human Verification Required

None.

### Gaps Summary

No gaps. All 15 must-haves verified. Phase 33 goal achieved:
- Three nginx containers scaffolded with correct SPA/static routing semantics
- URL layout documented with all 4 frontends, ports, and container names
- Gateway CORS expanded to 5 explicit origins covering new frontends
- PUBLIC_PATHS expanded with 2 new auth endpoints for Phase 34 Mini App auth
- All 3 commits confirmed in git history (946aa3e, 30b83b2, fe9a425)
- Gateway build confirmed successful (documented in 33-02-SUMMARY.md self-check)

---

_Verified: 2026-04-06T19:15:00Z_
_Verifier: Claude (gsd-verifier)_
