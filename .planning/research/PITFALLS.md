# Pitfalls Research

**Domain:** CI/CD, Docker Production Deployment, SSL, Monitoring, API Docs — Spring Boot 3.4 Gradle Monorepo
**Researched:** 2026-04-07
**Confidence:** HIGH (verified against official Spring Boot docs, Docker docs, Let's Encrypt community, and multiple post-mortems)

---

## Critical Pitfalls

### Pitfall 1: Actuator Endpoint Exposure Through the API Gateway

**What goes wrong:**
Services configure `management.endpoints.web.exposure.include=*` during development. When the API Gateway route table also proxies `/actuator/**`, every `/actuator/env`, `/actuator/heapdump`, and `/actuator/beans` endpoint becomes publicly accessible. The `env` endpoint exposes all Spring `Environment` properties — including database passwords, Redis credentials, and bot tokens stored as env vars. The `heapdump` endpoint returns a raw JVM heap dump containing plaintext secrets in memory. Real-world cases (documented as recently as April 2026) resulted in full database schema and credential exfiltration via an open `/actuator/env` endpoint.

**Why it happens:**
Developers enable wide actuator exposure for local debugging and carry those settings to production without restricting them. They assume the Gateway JWT filter protects internal endpoints but forget that `/actuator/health` is a common public route exemption — and that some gateway configs exempt the entire `/actuator/**` prefix.

**How to avoid:**
- On every Java service, set `management.endpoints.web.exposure.include=health,info` in the production profile only. Never include `env`, `heapdump`, `threaddump`, or `beans` for publicly reachable services.
- Bind the management server to a non-routable address: `management.server.port=9099` and `management.server.address=127.0.0.1` so actuator is accessible only inside the Docker network, not mapped to the host.
- In the API Gateway route configuration, explicitly never route `/actuator/**` paths from internal services outward. The Gateway's own `/actuator/health` for load balancer checks is fine.
- Use a production Spring profile (`application-prod.yml`) that overrides the dev actuator settings.

**Warning signs:**
- `curl https://yourdomain.com/api/auth/actuator/env` returns JSON with property sources
- Actuator endpoints are not configured on a separate `management.server.port`
- Dev and prod `application.yml` share the same `management.endpoints.web.exposure.include` line

**Phase to address:** Spring Boot Actuator phase

---

### Pitfall 2: Certbot Chicken-and-Egg Bootstrap Failure

**What goes wrong:**
The nginx config references `ssl_certificate` and `ssl_certificate_key` paths that do not exist yet. nginx refuses to start because cert files are missing. Certbot standalone mode needs port 80 free, but nginx is already holding it. Neither service starts successfully — the first deploy hangs entirely.

**Why it happens:**
Developers write the final production `nginx.conf` (with HTTPS blocks) before running initial certificate issuance. The `docker-compose.prod.yml` brings everything up simultaneously; nginx fails on startup, certbot cannot reach its challenge responder, and the entire stack is broken on first boot.

**How to avoid:**
- Use a two-phase bootstrap. Phase 1: start nginx with an HTTP-only config (no `ssl_certificate` lines), run `certbot certonly --webroot` serving `/.well-known/acme-challenge/` through an nginx `location` block. Phase 2: swap in the full HTTPS config after certs exist, reload nginx.
- Commit a `nginx.conf.bootstrap` alongside the final `nginx.conf` with instructions for first-deploy usage.
- Certbot volumes must be mounted `:rw` (not `:ro`). A read-only mount silently prevents cert writing — certbot exits 0 but writes nothing.
- Never use `certbot certonly --standalone` if nginx is running on port 80. Use `--webroot` instead, pointing to the nginx-served challenge directory.

**Warning signs:**
- nginx container exits immediately on first `docker compose up` with "cannot load certificate"
- Certbot container log shows "Connection refused" for the ACME challenge
- Port 80 is not exposed in the compose file while using standalone mode

**Phase to address:** SSL/nginx phase — must be the first infra task before anything else runs in production

---

### Pitfall 3: Gradle Monorepo Rebuilds Every Service on Every Push

**What goes wrong:**
A single GitHub Actions workflow runs `./gradlew build` at the repo root. Every push — even a one-line landing page HTML change — triggers a full build of all 5 Java services, each with Testcontainers integration tests that pull Docker images. CI takes 25-30 minutes. Developers stop waiting for green before merging, defeating the purpose of CI.

**Why it happens:**
"Build everything always" is the simple default. Selective builds in Gradle monorepos require path-based filtering in GitHub Actions (`on.push.paths`) paired with correct inter-module dependency awareness. This is non-trivial so it gets skipped.

**How to avoid:**
- Use `on.push.paths` in each service-specific workflow to trigger only when files under `services/{service-name}/**`, `proto/**`, or root `build.gradle.kts` change.
- Add `gradle/actions/setup-gradle` with `cache-read-only: false` to persist the Gradle wrapper and dependency cache across runs. The official `gradle/actions` v3+ supports GitHub Actions cache natively.
- Separate CI into two job tiers: (a) per-service jobs, path-filtered, run on every push; (b) full integration run only on PRs to `main`.
- Use a GitHub Actions matrix to run per-service builds in parallel, not serially.

**Warning signs:**
- CI always takes 25+ minutes regardless of what changed
- Changing only `frontends/landing/index.html` triggers all Java service tests
- No `paths:` filter in any workflow's `on.push` trigger

**Phase to address:** GitHub Actions CI phase

---

### Pitfall 4: Docker Layer Cache Completely Unused in GitHub Actions

**What goes wrong:**
Dockerfiles follow best practices (copy `package.json` before source, copy fat JAR last), but GitHub Actions runners are ephemeral — each run starts on a fresh VM with zero Docker layer cache. Without a persistent cache backend, every build redownloads the base JRE image and all dependencies. A monorepo with 5 Java services produces 5 × 150MB image pushes on every deploy, even when only one service changed. Build-plus-push time exceeds 15 minutes per run.

**Why it happens:**
Docker layer caching works transparently in local development. Developers assume it works the same in CI. The GitHub Actions runner's local daemon cache is evicted between runs.

**How to avoid:**
- Use `docker/build-push-action` with `cache-from: type=gha` and `cache-to: type=gha,mode=max`. This stores layer cache in GitHub Actions Cache between runs.
- Scope the cache key per service: include the service name in the cache key (e.g., `docker-auth-service-${{ hashFiles('services/auth-service/**') }}`). Without scoping, multiple service build jobs overwrite each other's cache.
- Be aware of the 10GB GitHub Actions Cache limit per repository. With 5 Java services + Python bot + 4 frontend nginx images, the cache may be evicted. Monitor cache size in the Actions tab.
- For Spring Boot services specifically, use layered JARs (see Pitfall 9) so that the dependency layers are large but stable — only the thin application layer changes per build.

**Warning signs:**
- Docker build logs show "CACHED" for 0 layers on every CI run
- Image push always transfers 150MB+ per service regardless of change size
- No `cache-from` or `cache-to` in any `docker/build-push-action` step

**Phase to address:** GitHub Actions CI phase (Docker build jobs)

---

### Pitfall 5: Multiline RSA / SSH Private Key Corruption in GitHub Secrets

**What goes wrong:**
The RSA private key for JWT signing (or the SSH deploy key) is pasted into a GitHub Secret. GitHub can silently corrupt multiline values by wrapping lines. The secret is written to a file in the workflow but the key is malformed — missing line breaks or containing extra whitespace. Spring Boot fails to load the key on startup with a cryptic "Cannot load private key" error. SSH deploy connections fail with "invalid format." The secret looks correct in the GitHub UI but is corrupted.

**Why it happens:**
GitHub Secrets UI can corrupt multiline values. Additionally, environment variable interpolation strips trailing newlines, which PEM keys require. RSA keys in PEM format are strictly line-length sensitive.

**How to avoid:**
- Store the RSA private key as a base64-encoded single-line secret: `base64 -w 0 private.key` produces a single line → store that → decode in workflow: `echo "${{ secrets.RSA_PRIVATE_KEY_B64 }}" | base64 -d > private.key`.
- For SSH deploy keys, use the `webfactory/ssh-agent` action which handles PEM format and multiline keys correctly rather than manually writing key files.
- Never store keys with a passphrase if they must be used non-interactively in CI.
- Validate key integrity in CI after decoding: `openssl rsa -in private.key -check -noout` for RSA keys; `ssh-keygen -y -f deploy_key` for SSH keys. Fail the workflow immediately if the key is malformed rather than letting it fail silently at runtime.

**Warning signs:**
- Spring Boot startup log: "Cannot load private key" or "DerValue: not enough data"
- SSH step fails with "invalid format" or "no supported authentication methods"
- `openssl rsa -in key.pem -check` reports errors in workflow logs after decoding

**Phase to address:** GitHub Actions CI phase — secrets setup step, done before any deploy workflow runs

---

### Pitfall 6: docker-compose.prod.yml Leaks Dev Config or Exposes Database Ports

**What goes wrong:**
The production compose file is created by copying `docker-compose.yml` and editing it. Dev config bleeds into prod: database ports are exposed to the host (`0.0.0.0:5432->5432/tcp`, `6379->6379/tcp`, `27017->27017/tcp`, `15672->15672/tcp` for RabbitMQ management UI). Any attacker who knows the server IP can connect directly to PostgreSQL, Redis, MongoDB, and RabbitMQ without any authentication bypass — they only need to know the service password. Additionally, `SPRING_PROFILES_ACTIVE=dev` may remain set, enabling development-only endpoints.

**Why it happens:**
The dev compose file has all ports exposed for local developer tooling. These are copied to prod without audit. The developer thinks "the firewall will protect it" but VPS firewalls are often misconfigured or disabled.

**How to avoid:**
- In `docker-compose.prod.yml`, remove ALL host port mappings for databases (PostgreSQL, MongoDB, Redis, RabbitMQ). These services communicate over the internal Docker bridge network — they need no host port exposure.
- The only host port mappings in prod are: nginx on 80 and 443. The API Gateway (8080) should be accessible only from nginx's Docker network, not from the host.
- Set `SPRING_PROFILES_ACTIVE=prod` explicitly in every Java service's environment block in the prod compose file.
- Add `restart: unless-stopped` and memory limits to every service.
- Keep a `.env.example` committed with all variable names but no values. The `.env` file on the VPS is created manually from this template and is never committed. Verify with `git ls-files .env` — must return nothing.

**Warning signs:**
- `docker compose -f docker-compose.prod.yml ps` shows `0.0.0.0:5432->5432/tcp` in the output
- `docker exec <auth-service> env | grep SPRING_PROFILES` returns `dev` or nothing
- `.env` appears in `git status` as a tracked file

**Phase to address:** Docker prod compose phase

---

### Pitfall 7: Swagger/OpenAPI Aggregation CORS Failure at the Gateway Level

**What goes wrong:**
Each microservice exposes its OpenAPI spec at `/v3/api-docs`. The API Gateway proxies these paths. The Swagger UI (served from the Gateway at `/swagger-ui.html`) fetches individual service specs by calling their proxied paths. Browsers block these requests because the `servers` field in the OpenAPI JSON points to `http://auth-service:9090` (the internal Docker hostname) instead of `https://yourdomain.com`. The Swagger UI shows "Fetch error: Possible cross-origin (CORS) issue?" for every service spec.

**Why it happens:**
SpringDoc generates `server` URLs based on the request's `Host` header at the service level. When the Gateway fetches the spec internally (Docker network), the host is the container name and port, not the public domain. This internally-generated spec is then served to the browser with wrong server URLs.

**How to avoid:**
- In each microservice's `application-prod.yml`, set `springdoc.server.url=https://yourdomain.com` (or inject via env var) so generated specs always reference the public gateway URL with the correct path prefix.
- In the Gateway, configure a `RewritePath` filter for `/v3/api-docs/{segment}` routes that strips the internal path prefix before passing to downstream services.
- Add `springdoc.api-docs.groups.enabled=true` in the Gateway and declare one `GroupedOpenApi` bean per downstream service, using `pathsToMatch` to proxy the correct service spec URL.
- For production, consider restricting Swagger UI access to a trusted IP range or removing it entirely — the internal API architecture should not be publicly browsable.

**Warning signs:**
- Swagger UI loads but all "Try it out" requests fail
- Browser dev tools show CORS errors on `/v3/api-docs/...` requests
- The `servers` array in any service's OpenAPI JSON shows `localhost`, a Docker container hostname, or an internal port number

**Phase to address:** API documentation phase

---

### Pitfall 8: Nginx Reload Not Triggered After Certificate Renewal

**What goes wrong:**
Certbot auto-renewal succeeds (new cert written to the mounted volume) but nginx continues serving the old certificate until the container is manually restarted. Users see certificate expiry warnings 30-60 days after initial launch. The system appears healthy (certbot cron is green) but TLS is actually broken from the browser's perspective.

**Why it happens:**
nginx holds the certificate in memory from startup. Writing new cert files to the volume does not cause nginx to reload. The certbot renewal deploy hook (`--deploy-hook`) must explicitly trigger nginx reload, but this is routinely omitted from first-pass setups.

**How to avoid:**
- Add a `--deploy-hook` to the certbot renewal command. If certbot and nginx share a container: `certbot renew --deploy-hook "nginx -s reload"`. If they are separate containers: `certbot renew --deploy-hook "docker exec nginx-container nginx -s reload"`.
- For separate containers, the certbot container needs the Docker socket (`/var/run/docker.sock`) mounted to execute docker commands — or use a cron job on the VPS host.
- Add a VPS cron job: `0 0,12 * * * certbot renew --quiet && docker exec rutcampustrack-nginx-1 nginx -s reload`.
- Test before going live: `certbot renew --dry-run`. Verify the deploy hook runs successfully in the dry-run output.

**Warning signs:**
- Certificate expiry date does not advance after 60 days despite certbot cron running
- Let's Encrypt sends "your certificate will expire soon" email warning
- `openssl s_client -connect yourdomain.com:443 | grep notAfter` shows the original issuance date

**Phase to address:** SSL/nginx phase

---

### Pitfall 9: Spring Boot Fat JAR Layer Cache Busted on Every Build

**What goes wrong:**
The Dockerfile copies the assembled fat JAR directly: `COPY build/libs/app.jar app.jar`. Every code change — even a one-line comment fix — produces a new JAR with a different checksum. Docker invalidates the entire layer. The image rebuild copies ~150MB of dependencies on every push. On a VPS with limited upload bandwidth, image pushes take 3-5 minutes per service, per deploy.

**Why it happens:**
The fat JAR bundles application code and all dependencies into a single file. Docker cannot distinguish "only app code changed" from "a dependency version changed" — the whole file changes.

**How to avoid:**
- Enable Spring Boot's layered JAR feature. In `build.gradle.kts`: `tasks.bootJar { layered { enabled = true } }`. In the multi-stage Dockerfile, extract layers explicitly:
  ```
  RUN java -Djarmode=layertools -jar app.jar extract
  COPY --from=builder dependencies/ ./
  COPY --from=builder spring-boot-loader/ ./
  COPY --from=builder snapshot-dependencies/ ./
  COPY --from=builder application/ ./
  ```
- The layer order is critical: `dependencies/` (rarely changes, always cached) → `spring-boot-loader/` (almost never changes) → `snapshot-dependencies/` (changes occasionally) → `application/` (changes every build but is tiny — only compiled classes and resources).
- This approach means only the `application/` layer (~1-5MB) is pushed on each deploy, not the full 150MB JAR. Dependency layers are served from Docker's layer cache.

**Warning signs:**
- `docker build` log shows no "CACHED" lines for the layer that copies the JAR
- Image push always transfers 150MB+ per service regardless of change size
- `docker image history <image>` shows one massive layer for the entire JAR

**Phase to address:** Docker Dockerfiles phase (Java services)

---

### Pitfall 10: Vite Build-Time Env Vars Hardcoded into Frontend Images

**What goes wrong:**
The PWA and Mini App are built with `VITE_API_BASE_URL=http://localhost:8080` from the local `.env`. The built nginx-serving container is deployed to the VPS. All API calls fail because `localhost:8080` does not exist on the user's device. The error is silent at image build time — the build succeeds, the container starts, but every API call 404s or connection-refuses.

**Why it happens:**
Vite replaces `import.meta.env.VITE_*` at build time, not runtime. The built JavaScript bundle contains the literal string value. Runtime environment variables passed to the nginx container have no effect on already-built JS.

**How to avoid:**
- Use relative API paths (`/api`) in the Vite app config instead of absolute URLs. nginx's `proxy_pass` directive then handles routing to the gateway. This avoids the env var problem entirely — the frontend always calls its own origin regardless of where it is deployed.
- If absolute URLs are needed, pass Vite build args through Docker `--build-arg`: declare `ARG VITE_API_BASE_URL` in the Dockerfile build stage, set `ENV VITE_API_BASE_URL=$VITE_API_BASE_URL`, then `RUN npm run build`. In `docker-compose.prod.yml`, pass `build.args.VITE_API_BASE_URL: "${VITE_API_BASE_URL}"`.
- Document clearly in `.env.example` that `VITE_API_BASE_URL` is a **build-time** variable requiring image rebuild if changed, not a runtime override.
- For Angular, `environment.prod.ts` is the build-time config. Use relative `/api` paths there too.

**Warning signs:**
- Browser network tab shows requests to `localhost:8080` or `localhost:9090` in production
- `grep -r "localhost" pwa/dist/assets/*.js` returns matches after a production build
- API calls work in local Docker but fail on the VPS without any code changes

**Phase to address:** Docker Dockerfiles phase (frontend services)

---

### Pitfall 11: Python Bot Using Alpine — Silent Build Failures for grpcio/aiohttp

**What goes wrong:**
The notification-bot Dockerfile uses `python:3.11-alpine` to reduce image size. `grpcio` and `aiohttp` (dependencies of aiogram 3 + aio-pika) do not have pre-built wheels for Alpine's musl libc. The image build succeeds locally (wheels cached from previous builds) but fails on clean CI runners or the VPS — either with a 20-minute compile attempt (if gcc is present) or an immediate failure (if it is not). In the worst case, the build silently produces an image that crashes at import time.

**Why it happens:**
Alpine uses musl instead of glibc. Many Python packages with C extensions distribute only glibc-linked wheels on PyPI. When no compatible wheel exists, pip falls back to source compilation. Per the project's `KEY DECISIONS`, the notification-bot already uses `grpcio==1.73.0` and `protobuf==6.31.0` — these are C-extension packages that will fail on Alpine without explicit toolchain setup.

**How to avoid:**
- Use `python:3.11-slim` (Debian-based glibc) for the notification-bot. The image is ~25MB larger than Alpine but avoids all musl compatibility issues and all pinned C-extension dependencies install from binary wheels instantly.
- If Alpine is strictly required, add `apk add --no-cache gcc musl-dev libffi-dev python3-dev` in the build stage and use a multi-stage build to drop the compiler in the final image.
- Verify the exact Docker build succeeds on a fresh CI runner (not just locally) before marking the Dockerfile complete.

**Warning signs:**
- Build log contains "error: command 'gcc' failed" or "No matching distribution found for grpcio"
- Docker build takes 15+ minutes for the Python bot (compiling from source)
- Bot container crashes immediately after start with `ImportError` on `grpc._cython`

**Phase to address:** Docker Dockerfiles phase (Python bot service)

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Single CI job builds all services | Simple one-file workflow | 25-min builds for every trivial change; developers bypass CI | MVP launch only — replace within first week |
| `management.endpoints.web.exposure.include=*` in prod | Full actuator visibility | DB credentials and heap dumps publicly readable | Never in production — use `application-prod.yml` override |
| Self-signed cert instead of Let's Encrypt | Zero bootstrap complexity | Browsers block the site; Telegram Mini App and Web Push require HTTPS | Only during private staging on a VPN |
| Use `docker-compose.yml` directly in prod | One file to maintain | Dev ports exposed, no restart policies, wrong profiles | Never — prod and dev configs must be separate files |
| Fat JAR Docker COPY (no layered JARs) | Simple Dockerfile | 150MB push per service per deploy; slow CI | Never — layered JARs are a one-time 30-min setup |
| `springdoc.swagger-ui.enabled=true` with no auth or IP restriction | Easy API browsing | Internal API architecture publicly accessible | Dev environment only |
| Python base image `alpine` without toolchain | Smaller image | Build failures or 20-min compiles for grpcio, aiohttp | Only if verified to work on a clean runner |
| Store `.env` with real credentials in the repository | Easy sharing | Credentials in git history; very hard to fully remove | Never |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Let's Encrypt + nginx | Start nginx with final SSL config before cert exists | Bootstrap with HTTP-only nginx config; issue cert; swap to HTTPS config |
| Let's Encrypt renewal | Trust certbot cron alone to keep TLS working | Add `--deploy-hook "nginx -s reload"` to every renewal command |
| GitHub Actions + RSA/SSH key | Paste raw PEM into GitHub Secret (line-wrap corruption) | Base64-encode the key; decode with `base64 -d` in workflow; validate with `openssl`/`ssh-keygen` |
| SpringDoc + Gateway | Each service generates OpenAPI spec with internal `server` URL | Set `SPRINGDOC_SERVER_URL` env var to public gateway domain in prod profile |
| Vite + Docker build | Assume runtime env vars affect VITE_ prefixed variables | Pass `--build-arg` at `docker build` time; prefer relative `/api` paths |
| Gradle + GitHub Actions cache | Default `actions/cache` key covers entire repo | Scope Gradle cache key per service using `hashFiles('services/X/**')` |
| Docker layer cache + monorepo matrix | Multiple service workflows share same GHA cache key | Prefix cache key with `matrix.service` to prevent cache collisions |
| RabbitMQ management UI | Port 15672 mapped to host in prod compose | Remove host port mapping; access via SSH tunnel on demand |
| MongoDB/Redis/PostgreSQL | All DB ports mapped to host in prod | Internal Docker network only; zero host port mappings in prod |
| Spring Boot Actuator | Actuator routed through public API Gateway | Never add `actuator/**` to Gateway's public route table; bind management port to `127.0.0.1` |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Fat JAR Docker layers | 5-min image push per service on every deploy | Use Spring Boot layered JARs + split `COPY` layers in Dockerfile | From day 1; compounds with each service added |
| All CI jobs in serial | 40-min CI wall time for 5 services | Use GitHub Actions matrix for parallel per-service builds | From day 1 with more than 1 service |
| Gradle dependency re-download on every CI run | 8-12 min cold builds even with no code changes | `gradle/actions/setup-gradle` with persistent GHA cache | Without cache every cold runner downloads ~500MB |
| `npm install` / `pnpm install` without lockfile cache | 5-min frontend CI for trivial changes | `actions/cache` keyed on `package-lock.json` or `pnpm-lock.yaml` hash | Without cache, every install is fully uncached |
| Docker image push of all 5 services on every commit | 20-min deploy pipeline regardless of change | Path-filtered workflows trigger only the changed service's deploy | From first over-broad workflow trigger |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| `management.endpoints.web.exposure.include=*` in prod | Heap dump and env properties publicly readable — full credential exfiltration | Prod profile limits to `health,info`; management port bound to `127.0.0.1` |
| Actuator routed through public API Gateway | Same credential exfiltration risk at scale | Never add `/actuator/**` to Gateway's public route table |
| DB ports exposed to host in prod compose | Direct DB access if server IP is reachable | Remove all DB host port mappings in `docker-compose.prod.yml` |
| RSA private key stored as plain multiline text in GitHub Secret | Key corruption OR key compromise if repo/secret is exposed | Base64-encode; document key rotation procedure |
| Bot token committed in `docker-compose.yml` | Anyone with repo read access has bot token; bot can be hijacked | Use `.env` file on VPS; reference as `${BOT_TOKEN}` in compose; never commit |
| Swagger UI publicly accessible in prod without IP restriction | Internal API structure and all endpoints disclosed | Restrict to admin IP range or disable `springdoc.swagger-ui.enabled` in prod entirely |
| SSH deploy user with `sudo` privileges | An attacker who gets the deploy key can escalate to root | Create a `deploy` user with no sudo, `nologin` shell, authorized only for `docker compose` commands |
| `certbot --force-renewal` in daily cron | Rate limit hit (5 certificates per domain per week); Let's Encrypt blocks future renewals | Run `certbot renew` (not `--force-renewal`) twice daily; it only renews if cert expires within 30 days |

---

## "Looks Done But Isn't" Checklist

- [ ] **Actuator security:** `curl https://yourdomain.com/api/auth/actuator/env` returns 404, not JSON
- [ ] **Management port isolation:** `docker exec auth-service-1 ss -tlnp | grep 9099` shows management port bound to `127.0.0.1` only
- [ ] **Database ports:** `docker compose -f docker-compose.prod.yml ps` shows no host bindings for PostgreSQL (5432), Redis (6379), MongoDB (27017), or RabbitMQ (5672/15672)
- [ ] **SPRING_PROFILES_ACTIVE:** `docker exec auth-service-1 env | grep SPRING_PROFILES` returns `prod`
- [ ] **Certbot bootstrap:** `certbot renew --dry-run` succeeds on the VPS without stopping nginx
- [ ] **Nginx cert reload:** After `certbot renew`, `openssl s_client -connect yourdomain.com:443 2>/dev/null | grep notAfter` shows the new expiry date
- [ ] **Swagger server URL:** The `servers[0].url` in each service's OpenAPI JSON points to `https://yourdomain.com`, not `localhost` or a Docker container hostname
- [ ] **Vite API base:** `grep -r "localhost" pwa/dist/assets/*.js` returns nothing
- [ ] **Docker layer cache:** Second build with same dependencies shows "CACHED" for the `dependencies/` layer in build logs
- [ ] **GitHub Actions cache keys:** Different services use distinct cache keys (verify `matrix.service` or equivalent in cache key definition)
- [ ] **SSH deploy key:** The deploy user on the VPS cannot `sudo` and cannot modify `~/.ssh/authorized_keys`
- [ ] **Restart policy:** All prod services have `restart: unless-stopped` in `docker-compose.prod.yml`
- [ ] **RSA key integrity:** CI step that decodes the RSA key runs `openssl rsa -check -noout` and fails the workflow if the key is malformed

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Actuator exposed and credentials accessed | HIGH | Rotate all DB passwords, Redis password, bot token, RSA keys immediately; redeploy all services; audit access logs for exfiltration |
| Certbot bootstrap failure on first deploy | LOW | Stop nginx container; run `certbot certonly --standalone` with port 80 free; restart nginx with SSL config |
| Corrupt SSH/RSA key in GitHub Secret | LOW | Re-encode as base64 with `base64 -w 0`; update GitHub Secret; re-run workflow |
| `.env` committed with real credentials | HIGH | Rotate all credentials immediately; `git filter-repo` to remove from history; force-push; rotate any GitHub Actions secrets referencing those values |
| Fat JAR deploys are too slow | LOW | Add `tasks.bootJar { layered { enabled = true } }` to `build.gradle.kts`; update Dockerfiles with split COPY layers; rebuild images |
| Swagger CORS failures | LOW | Set `SPRINGDOC_SERVER_URL` env var in each service; redeploy affected services |
| Certbot cert not renewing silently | MEDIUM | Add `--deploy-hook` to renewal command; run `certbot renew --force-renewal` to immediate-renew; verify nginx serves new cert |
| Python bot Alpine build failure on CI | LOW | Switch base image to `python:3.11-slim` in bot Dockerfile; rebuild |
| Vite localhost hardcoded in prod bundle | LOW | Update Vite config to use relative `/api` paths; rebuild frontend images; redeploy nginx containers |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Actuator endpoint exposure | Spring Boot Actuator phase | `curl https://domain/api/auth/actuator/env` returns 404 |
| Certbot bootstrap chicken-and-egg | SSL/nginx phase — must be first infra task | Site loads over HTTPS with valid cert before any other prod task |
| Gradle monorepo rebuilds everything | GitHub Actions CI phase | Changing only `services/auth-service/` triggers only auth-service workflow |
| Docker layer cache unused in CI | GitHub Actions CI phase (Docker build jobs) | Second CI run with no dependency changes shows "CACHED" for dependency layers |
| Multiline RSA/SSH key corruption | GitHub Actions CI phase — secrets setup | `openssl rsa -check` step succeeds; Spring Boot starts without key errors |
| docker-compose.prod.yml leaks dev config | Docker prod compose phase | No DB host ports visible; `SPRING_PROFILES_ACTIVE=prod` confirmed |
| Swagger CORS at Gateway | API documentation phase | Swagger UI Try-It-Out executes requests without CORS errors |
| Nginx cert not reloaded after renewal | SSL/nginx phase | `certbot renew --dry-run` log shows deploy-hook executed |
| Spring Boot fat JAR layer cache bust | Docker Dockerfiles phase (Java services) | Second build with same deps shows "CACHED" for `dependencies/` layer |
| Vite env vars hardcoded at build time | Docker Dockerfiles phase (frontend) | `grep localhost pwa/dist/assets/*.js` returns nothing |
| Python bot Alpine build failure | Docker Dockerfiles phase (Python bot) | Fresh CI runner builds notification-bot image in under 5 minutes |

---

## Sources

- Wiz Blog — Spring Boot Actuator Misconfigurations: https://www.wiz.io/blog/spring-boot-actuator-misconfigurations
- SYSCREST — Securing Spring Boot Actuator (2025): https://www.syscrest.com/2025/02/securing-spring-boot-actuator/
- Trend Micro — Misconfigured Actuator to credential exfiltration (2026): https://www.trendmicro.com/en_us/research/26/c/from-misconfigured-spring-boot-actuator-to-sharepoint-exfiltrati.html
- Spring Boot Docs — Actuator endpoints: https://docs.spring.io/spring-boot/reference/actuator/endpoints.html
- Baeldung — Integrate OpenAPI With Spring Cloud Gateway: https://www.baeldung.com/spring-cloud-gateway-integrate-openapi
- Spring Cloud Gateway — CORS configuration: https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/
- Docker Docs — Multi-stage builds: https://docs.docker.com/get-started/docker-concepts/building-images/multi-stage-builds/
- Spring Boot Docs — Efficient Container Images (layered JARs): https://docs.spring.io/spring-boot/reference/packaging/container-images/efficient-images.html
- Python Speed — Alpine makes Python Docker builds 50x slower: https://pythonspeed.com/articles/alpine-docker-python/
- Docker Docs — GitHub Actions cache for Docker: https://docs.docker.com/build/ci/github-actions/cache/
- Blacksmith — Docker Layer Caching in GitHub Actions guide: https://www.blacksmith.sh/blog/cache-is-king-a-guide-for-docker-layer-caching-in-github-actions
- Docker Docs — Compose environment variable best practices: https://docs.docker.com/compose/how-tos/environment-variables/best-practices/
- Let's Encrypt Community — nginx certbot ACME challenge port conflict: https://community.letsencrypt.org/t/ssl-lets-encrypt-nginx-docker-compose/220544
- wmnnd/nginx-certbot bootstrap pattern: https://github.com/wmnnd/nginx-certbot
- webfactory/ssh-agent GitHub Action: https://github.com/webfactory/ssh-agent
- GitHub Community — multiline secrets in GitHub Actions: https://github.com/orgs/community/discussions/142004
- Certbot user guide (deploy hooks): https://eff-certbot.readthedocs.io/en/stable/using.html
- Vite Docker production deployment guide: https://www.buildwithmatija.com/blog/production-react-vite-docker-deployment
- Medium/Engineering Playbook — actuator in production incident (2026): https://medium.com/engineering-playbook/i-enabled-spring-boot-actuator-in-production-50be66aef9d2

---
*Pitfalls research for: CI/CD, Docker Production Deployment, SSL, Monitoring, API Docs — Spring Boot 3.4 Gradle Monorepo*
*Researched: 2026-04-07*
