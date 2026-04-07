# Phase 46: GitHub Actions Deploy - Research

**Researched:** 2026-04-08
**Domain:** GitHub Actions, GHCR (GitHub Container Registry), SSH deployment, Docker Compose production deploy
**Confidence:** HIGH

## Summary

Phase 46 adds a deploy workflow (`.github/workflows/deploy.yml`) triggered on merge to `main`. The workflow has two sequential stages: (1) build all 11 service images and push them to GHCR using `docker/build-push-action@v7`, and (2) SSH into the VPS and run `docker compose pull && docker compose up -d` using `appleboy/ssh-action@v1`.

The docker-compose.prod.yml already contains the correct GHCR image names (`ghcr.io/maksd/rutcampustrack/<service>:latest`) for all 11 service containers. The workflow must authenticate to GHCR using `GITHUB_TOKEN` with `packages: write` permission — no Personal Access Token needed. SSH deploy uses the private half of an SSH key pair added to the VPS's `authorized_keys`; the private key is stored as a GitHub Secret.

The critical architectural insight is that the deploy workflow must NOT rebuild images from source on the VPS — instead it pushes pre-built images to GHCR then pulls them on the VPS. This is the GHCR pattern (portfolio-ready, images are inspectable at `ghcr.io/maksd`). The VPS needs only Docker + docker compose installed; the heavy build work runs on GitHub Actions runners.

**Primary recommendation:** Single `.github/workflows/deploy.yml` triggered on `push: branches: [main]`. One job (`build-push`) builds and pushes all 11 images with a matrix strategy (or sequential steps for simplicity). A second job (`deploy`) depends on `build-push`, SSHes into VPS, and runs the compose update commands.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CI-05 | GitHub Actions deploy workflow pushes images to GHCR on merge to main | `docker/build-push-action@v7` + `docker/login-action@v3` + `GITHUB_TOKEN` with `packages: write`; trigger: `on: push: branches: [main]` |
| CI-06 | GitHub Actions deploy workflow deploys to VPS via SSH after image push | `appleboy/ssh-action@v1` with `host`, `username`, `key` secrets; script: `cd /opt/rutcampustrack && docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d` |
| CI-07 | GitHub Secrets store all sensitive values (RSA keys base64-encoded, DB passwords, SSH key, bot token) | GitHub Secrets UI: SSH_PRIVATE_KEY (raw PEM or base64), VPS_HOST, VPS_USER, plus env var secrets matching .env.prod.example |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `docker/login-action` | v3 (v3.5.0 latest) | Authenticate to GHCR | Official Docker action; handles GHCR auth via GITHUB_TOKEN |
| `docker/build-push-action` | v7 (v7.0.0, released 2025-03-05) | Build + push Docker images | Official Docker action; supports buildx, multi-platform, cache |
| `docker/setup-buildx-action` | v3 | Enable Docker Buildx for build-push-action | Required by `docker/build-push-action@v7` for cache/multi-platform |
| `appleboy/ssh-action` | v1 (v1.2.5 latest) | Execute commands on remote VPS via SSH | De-facto standard for SSH-based GitHub Actions deployments |
| `actions/checkout` | v4 | Check out repository | Already used in CI workflow |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `docker/metadata-action` | v5 | Generate image tags and labels | Optional but recommended for semver tagging; not strictly needed for `:latest`-only workflow |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `appleboy/ssh-action` | Raw `ssh` command via `webfactory/ssh-agent` | appleboy abstracts key loading; raw SSH requires more setup but is more transparent |
| GITHUB_TOKEN for GHCR push | Personal Access Token (PAT) | GITHUB_TOKEN is preferred — no rotation needed, scoped to repo, sufficient for repo-owned packages |
| Sequential build steps | Matrix strategy per service | Matrix parallelizes but adds complexity; for 11 images with large Gradle builds, sequential is simpler and avoids runner quota issues |

## Architecture Patterns

### Recommended Workflow Structure
```
.github/
└── workflows/
    ├── ci.yml        # existing — builds/tests on push to any branch
    └── deploy.yml    # new — pushes to GHCR + deploys on push to main
```

**Key design decision:** Deploy workflow is a SEPARATE file from CI. This is standard practice — CI runs on every branch; deploy runs only on `main`. The deploy workflow should `needs: [build-push]` to ensure images are in GHCR before SSHing to VPS.

### Pattern 1: GHCR Authentication
**What:** Log in to `ghcr.io` using `GITHUB_TOKEN`. No secret configuration needed beyond the permission block.
**When to use:** Any job that pushes to GHCR.

```yaml
# Source: docs.github.com/en/packages/managing-github-packages-using-github-actions-workflows
permissions:
  contents: read
  packages: write

jobs:
  build-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
```

### Pattern 2: Build and Push Multiple Images (Sequential Steps)
**What:** Build each of the 11 service images in sequential steps within a single job. Each step uses `docker/build-push-action@v7` with the appropriate `context` and `file` pointing at the service Dockerfile.
**When to use:** When all images need to be pushed before the deploy step can run. Sequential is simpler than matrix for this project.

```yaml
# Source: github.com/docker/build-push-action
      - name: Build and push api-gateway
        uses: docker/build-push-action@v7
        with:
          context: .
          file: services/api-gateway/Dockerfile
          push: true
          tags: ghcr.io/maksd/rutcampustrack/api-gateway:latest

      - name: Build and push auth-service
        uses: docker/build-push-action@v7
        with:
          context: .
          file: services/auth-service/Dockerfile
          push: true
          tags: ghcr.io/maksd/rutcampustrack/auth-service:latest

      # ... repeated for all 11 services
```

**Key detail — Gradle services build context:** All Java service Dockerfiles copy `gradlew`, `gradle/`, root `build.gradle.kts`, and `settings.gradle.kts`, so the build context MUST be the repo root (`.`), not the service subdirectory. This matches how docker-compose.prod.yml already has `context: .` for Java services. The Python notification-bot and frontends have self-contained Dockerfiles with their own context — the Dockerfile paths in docker-compose.prod.yml are the reference.

**Key detail — notification-bot context:** `context: ./services/notification-bot` (not repo root) — its Dockerfile uses `COPY . .` relative to the bot directory.

**Key detail — frontend contexts:** Each frontend Dockerfile is `context: ./frontends/<name>`.

### Pattern 3: SSH Deploy Job
**What:** After all images are pushed, SSH into VPS and run docker compose commands to pull latest images and restart services.
**When to use:** After the build-push job completes successfully.

```yaml
  deploy:
    name: Deploy to VPS
    needs: build-push
    runs-on: ubuntu-latest
    steps:
      - name: Deploy via SSH
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          port: 22
          script: |
            cd /opt/rutcampustrack
            docker compose -f docker-compose.prod.yml pull
            docker compose -f docker-compose.prod.yml up -d --remove-orphans
```

**Key detail — `--remove-orphans`:** Removes containers for services no longer defined in compose file. Safe to include; prevents stale containers accumulating.

**Key detail — VPS directory:** The VPS needs the repo cloned (or at minimum `docker-compose.prod.yml` and `.env.prod` present) at `/opt/rutcampustrack` (or similar path). The workflow does NOT git pull on the VPS — it only pulls pre-built images. The compose file and env file are NOT deployed by the workflow; they must be placed on the VPS manually during initial VPS setup.

**Key detail — docker compose login on VPS:** The VPS must be logged in to GHCR to pull private packages. Either: (a) pre-login the VPS once with `docker login ghcr.io -u maksd -p <PAT>` during setup, or (b) pass the GITHUB_TOKEN to the SSH script and login within the script. Option (b) is cleaner for CI:

```yaml
          script: |
            echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
            cd /opt/rutcampustrack
            docker compose -f docker-compose.prod.yml pull
            docker compose -f docker-compose.prod.yml up -d --remove-orphans
```

However, passing `secrets.GITHUB_TOKEN` into an SSH script that runs on the VPS exposes it to the VPS environment. A safer approach: pre-authorize the VPS with a PAT that has `read:packages` scope, and store it as `GHCR_PAT` secret. The deploy script then runs `echo "$GHCR_PAT" | docker login ghcr.io -u maksd --password-stdin`. [ASSUMED: GHCR packages on this personal account are private by default]

**Simpler approach:** Make GHCR packages public (GitHub: Package Settings → Change visibility → Public). Then `docker compose pull` works without authentication on the VPS. This is acceptable for a portfolio project. [ASSUMED: owner accepts public images for portfolio visibility]

### Pattern 4: GitHub Secrets Required
**What:** All sensitive values must be stored as GitHub Secrets (Settings → Secrets and variables → Actions).

| Secret Name | Value | How to Generate |
|------------|-------|----------------|
| `VPS_HOST` | VPS IP or domain | From hosting provider |
| `VPS_USER` | SSH username (e.g., `ubuntu`, `deploy`) | VPS user with docker access |
| `SSH_PRIVATE_KEY` | Private key PEM content | `ssh-keygen -t ed25519` or RSA 4096 |
| `POSTGRES_ACADEMIC_PASSWORD` | DB password | Random strong password |
| `POSTGRES_SCHEDULE_PASSWORD` | DB password | Random strong password |
| `RABBITMQ_USER` | RabbitMQ username | e.g., `rct_user` |
| `RABBITMQ_PASSWORD` | RabbitMQ password | Random strong password |
| `BOT_TOKEN` | Telegram bot token | From @BotFather |
| `TMA_BOT_TOKEN` | Telegram mini-app token | From @BotFather |
| `VAPID_PUBLIC_KEY` | Web Push VAPID public key | `npx web-push generate-vapid-keys` |
| `VAPID_PRIVATE_KEY` | Web Push VAPID private key | same command |
| `VAPID_SUBJECT` | mailto for VAPID | e.g., `mailto:admin@domain.com` |
| `MINI_APP_URL` | Mini app Telegram URL | e.g., `https://t.me/BotName/app` |

**RSA keys note from CLAUDE.md (STATE.md):** "RSA keys base64-encoded" — for RSA keys (used as JWT keys by auth-service), store as base64-encoded secret. The JWT private/public key volume (`jwt-keys`) is populated at container start. The auth-service container reads from `/keys/private.key` and `/keys/public.key`. The approach must be documented: either keys are generated on the VPS once (and stored as a Docker volume, persisted), or injected via environment variable decoded at startup. Looking at docker-compose.prod.yml, auth-service uses `JWT_KEY_DIR: /keys` and a named volume `jwt-keys:`. This means the keys are stored in a Docker volume on the VPS — generated once during initial setup, not managed by GitHub Actions. [VERIFIED: from docker-compose.prod.yml lines 143-148]

**Conclusion on RSA/JWT keys:** The JWT keys live in a Docker volume on the VPS. CI-07's "RSA keys base64-encoded" requirement likely refers to storing them as GitHub Secrets for an optional injection mechanism (or for VPS bootstrap). Since the volume approach is already wired in docker-compose.prod.yml, the GitHub Actions workflow does NOT need to inject JWT keys — they're generated once on the VPS. This requirement may be partially out of scope for the workflow itself, or refers to the `.env.prod` bootstrap documentation.

### Pattern 5: Workflow Trigger
**What:** Deploy only runs on push to `main` — not on PR or other branches.

```yaml
on:
  push:
    branches: [main]
```

**Should deploy wait for CI?** Best practice: add `workflow_run` trigger to wait for CI to pass, OR use `needs` across workflows. However, the simplest pattern is: deploy workflow is separate and only runs on `main`, while CI runs on `push: branches: ['**']`. Since PRs are typically merged after CI passes (branch protection), this is acceptable. [ASSUMED: branch protection is configured to require CI checks before merge to main]

### Anti-Patterns to Avoid
- **Building images on the VPS:** Defeats the purpose of GHCR. The VPS should only pull pre-built images.
- **Using `docker-compose up --build`:** Same issue — rebuilds from source. Always use `pull` then `up -d`.
- **Storing SSH private key without `\n` line breaks:** GitHub Secrets store the key correctly if pasted as-is (with newlines). appleboy/ssh-action handles the multi-line PEM value correctly.
- **Using `appleboy/ssh-action@master`:** Use a pinned version (`v1`) instead of `master` to avoid unexpected behavior from upstream changes.
- **Overwriting the VPS `.env.prod` file from CI:** The `.env.prod` on the VPS should be set up manually. CI should NOT write secrets to the VPS filesystem — secrets injection should use Docker environment variables passed at runtime, not file drops.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| GHCR authentication | Manual `docker login` step with base64 PAT | `docker/login-action@v3` with `GITHUB_TOKEN` | Official action handles token refresh, multi-registry, error cases |
| Image tagging | `echo "ghcr.io/..."` string interpolation | `docker/build-push-action@v7` `tags:` parameter | Action handles digest attestation, label generation |
| SSH key file management | `echo "$SSH_KEY" > ~/.ssh/id_rsa && chmod 600` | `appleboy/ssh-action@v1` | Action handles key loading, known_hosts, and cleanup atomically |
| Docker Buildx setup | Manual `docker buildx create` | `docker/setup-buildx-action@v3` | Required by build-push-action@v7; action configures builder correctly |

**Key insight:** The Docker suite of actions (`login`, `setup-buildx`, `build-push`) are designed to work together. Mixing them with manual commands leads to caching failures and auth token issues.

## Runtime State Inventory

> SKIPPED — this is a greenfield phase adding new workflow files. No rename/refactor involved.

## Common Pitfalls

### Pitfall 1: GHCR push denied despite `packages: write`
**What goes wrong:** `docker/login-action` succeeds but `docker/build-push-action` fails with "denied: permission_denied"
**Why it happens:** If a package with the same name was previously published from a different repository (or the repo was recreated), the package's access control may not be linked to the current repo. Also possible: the `permissions:` block is missing from the job (not just the workflow level).
**How to avoid:** Add `permissions: packages: write` at the **job level** (not just workflow level) for the build-push job. First publish creates the package linked to the repo.
**Warning signs:** Error message says "denied" or "permission_denied" after successful login

### Pitfall 2: Java service Docker builds fail on GitHub Actions runner (OOM or slow)
**What goes wrong:** `docker/build-push-action` for Java services fails or takes >30 minutes because Gradle runs inside Docker without BuildKit cache
**Why it happens:** Each image build runs `./gradlew :service:bootJar --no-daemon` inside the Docker builder container. Without Gradle cache mounts, each build downloads all dependencies fresh.
**How to avoid:** Use BuildKit cache mounts in Dockerfiles:
```dockerfile
RUN --mount=type=cache,target=/root/.gradle ./gradlew :services:api-gateway:bootJar --no-daemon -x test
```
Alternatively, accept slow cold builds (each full build is ~3-5 min per Java service = ~35 min total for 7 Java images). For Phase 46 scope, slow but correct is acceptable.
**Warning signs:** Build step takes >20 minutes with repeated "Downloading..." output

### Pitfall 3: SSH action fails with "Host key verification failed"
**What goes wrong:** `appleboy/ssh-action` exits with host key verification error
**Why it happens:** The VPS's host key is not in `known_hosts` on the GitHub Actions runner
**How to avoid:** appleboy/ssh-action has a `use_insecure_cipher: true` option to skip verification (not recommended for production) OR add the host fingerprint via the `fingerprint` input parameter. For portfolio/VPS: the `ssh-action` by default uses `-o StrictHostKeyChecking=no` in its SSH invocation, so this is typically NOT an issue. Verify by running the workflow once.
**Warning signs:** Error output contains "Host key verification failed" or "REMOTE HOST IDENTIFICATION HAS CHANGED"

### Pitfall 4: `docker compose pull` fails on VPS due to GHCR authentication
**What goes wrong:** The VPS pulls images but gets "unauthorized" from GHCR
**Why it happens:** GHCR packages are private by default; the VPS has no credentials
**How to avoid:** Option A — Make all packages public (simplest, fine for portfolio). Option B — Pre-login the VPS to GHCR with a PAT that has `read:packages`. Option C — Pass login command within the SSH script (GITHUB_TOKEN expires but is valid during workflow run).
**Warning signs:** `docker compose pull` output shows "Error response from daemon: unauthorized: unauthenticated"

### Pitfall 5: VPS has wrong `docker-compose.prod.yml` version
**What goes wrong:** The VPS runs an outdated compose file that references old service configurations
**Why it happens:** Compose file changes are made in git but not synced to VPS
**How to avoid:** The deploy script should either: (a) git pull on VPS before compose commands (requires git installed and SSH key for repo), or (b) use `appleboy/scp-action` to copy the compose file to VPS before the SSH deploy step. Option (b) is cleaner.
**Warning signs:** New services don't start, or old environment variables are used

### Pitfall 6: notification-bot Dockerfile context vs. build context mismatch
**What goes wrong:** `docker/build-push-action` for notification-bot fails because Dockerfile references paths relative to `./services/notification-bot/` but context is set to repo root
**Why it happens:** notification-bot Dockerfile uses `COPY requirements.txt .` which expects the Dockerfile's directory to be the build context
**How to avoid:** Set `context: services/notification-bot` and `file: services/notification-bot/Dockerfile` for the notification-bot build step (matching docker-compose.prod.yml's build config)
**Warning signs:** "COPY failed: file not found" for requirements.txt during Docker build

### Pitfall 7: Frontend build context vs. Dockerfile paths
**What goes wrong:** Same as Pitfall 6 for frontend services (pwa, mini-app, web-panel, landing)
**Why it happens:** Frontend Dockerfiles use `COPY package.json .` relative to their own directory
**How to avoid:** Set `context: frontends/<name>` and `file: frontends/<name>/Dockerfile` for each frontend build step
**Warning signs:** "COPY failed" for `package.json` or `package-lock.json`

## Code Examples

### Complete deploy.yml Skeleton
```yaml
# Source: docs.github.com/en/packages/managing-github-packages-using-github-actions-workflows
#         github.com/appleboy/ssh-action
name: Deploy

on:
  push:
    branches: [main]

jobs:
  build-push:
    name: Build and Push to GHCR
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      # Java services — context is repo root (Dockerfiles reference root gradlew)
      - name: Build and push api-gateway
        uses: docker/build-push-action@v7
        with:
          context: .
          file: services/api-gateway/Dockerfile
          push: true
          tags: ghcr.io/maksd/rutcampustrack/api-gateway:latest

      - name: Build and push auth-service
        uses: docker/build-push-action@v7
        with:
          context: .
          file: services/auth-service/Dockerfile
          push: true
          tags: ghcr.io/maksd/rutcampustrack/auth-service:latest

      - name: Build and push academic-service
        uses: docker/build-push-action@v7
        with:
          context: .
          file: services/academic-service/academic-app/Dockerfile
          push: true
          tags: ghcr.io/maksd/rutcampustrack/academic-service:latest

      - name: Build and push schedule-service
        uses: docker/build-push-action@v7
        with:
          context: .
          file: services/schedule-service/schedule-app/Dockerfile
          push: true
          tags: ghcr.io/maksd/rutcampustrack/schedule-service:latest

      - name: Build and push attendance-service
        uses: docker/build-push-action@v7
        with:
          context: .
          file: services/attendance-service/attendance-app/Dockerfile
          push: true
          tags: ghcr.io/maksd/rutcampustrack/attendance-service:latest

      - name: Build and push notification-web
        uses: docker/build-push-action@v7
        with:
          context: .
          file: services/notification-service/notification-app/Dockerfile
          push: true
          tags: ghcr.io/maksd/rutcampustrack/notification-web:latest

      # Python service — context is the bot directory
      - name: Build and push notification-bot
        uses: docker/build-push-action@v7
        with:
          context: services/notification-bot
          file: services/notification-bot/Dockerfile
          push: true
          tags: ghcr.io/maksd/rutcampustrack/notification-bot:latest

      # Frontend services — context is each frontend directory
      - name: Build and push pwa
        uses: docker/build-push-action@v7
        with:
          context: frontends/pwa
          file: frontends/pwa/Dockerfile
          push: true
          tags: ghcr.io/maksd/rutcampustrack/pwa-nginx:latest

      - name: Build and push mini-app
        uses: docker/build-push-action@v7
        with:
          context: frontends/mini-app
          file: frontends/mini-app/Dockerfile
          push: true
          tags: ghcr.io/maksd/rutcampustrack/mini-app-nginx:latest

      - name: Build and push web-panel
        uses: docker/build-push-action@v7
        with:
          context: frontends/web-panel
          file: frontends/web-panel/Dockerfile
          push: true
          tags: ghcr.io/maksd/rutcampustrack/web-panel-nginx:latest

      - name: Build and push landing
        uses: docker/build-push-action@v7
        with:
          context: frontends/landing
          file: frontends/landing/Dockerfile
          push: true
          tags: ghcr.io/maksd/rutcampustrack/landing-nginx:latest

  deploy:
    name: Deploy to VPS
    needs: build-push
    runs-on: ubuntu-latest

    steps:
      - name: Deploy via SSH
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          port: 22
          script: |
            cd /opt/rutcampustrack
            docker compose -f docker-compose.prod.yml pull
            docker compose -f docker-compose.prod.yml up -d --remove-orphans
```

### VPS Initial Setup Checklist (not part of workflow code — for documentation)
```bash
# On the VPS (run once manually):
sudo apt-get install -y docker.io docker-compose-plugin
sudo usermod -aG docker $USER   # allow deploy user to run docker without sudo

# Clone repo and create .env.prod:
git clone https://github.com/maksd/rutcampustrack.git /opt/rutcampustrack
cd /opt/rutcampustrack
cp .env.prod.example .env.prod
# Edit .env.prod with real values

# If GHCR packages are private, login once:
docker login ghcr.io -u maksd -p <PAT_WITH_READ_PACKAGES>

# JWT keys — generate once, stored in Docker named volume:
# (auth-service container generates them on first start if volume is empty,
#  or they must be placed in /var/lib/docker/volumes/rutcampustrack_jwt-keys/_data/)
```

### SSH Key Generation for GitHub Secrets
```bash
# Generate ED25519 key pair (modern, fast, recommended over RSA for SSH):
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/github_actions_deploy

# Add public key to VPS:
ssh-copy-id -i ~/.ssh/github_actions_deploy.pub deploy@<VPS_IP>

# Store private key content as GitHub Secret SSH_PRIVATE_KEY:
cat ~/.ssh/github_actions_deploy
# Copy entire output including -----BEGIN OPENSSH PRIVATE KEY----- header/footer
```

## Complete Service Image Map

All 11 images that must be built and pushed, with their contexts and Dockerfiles:

| Image Name | Context | Dockerfile Path |
|-----------|---------|----------------|
| `api-gateway` | `.` | `services/api-gateway/Dockerfile` |
| `auth-service` | `.` | `services/auth-service/Dockerfile` |
| `academic-service` | `.` | `services/academic-service/academic-app/Dockerfile` |
| `schedule-service` | `.` | `services/schedule-service/schedule-app/Dockerfile` |
| `attendance-service` | `.` | `services/attendance-service/attendance-app/Dockerfile` |
| `notification-web` | `.` | `services/notification-service/notification-app/Dockerfile` |
| `notification-bot` | `services/notification-bot` | `services/notification-bot/Dockerfile` |
| `pwa-nginx` | `frontends/pwa` | `frontends/pwa/Dockerfile` |
| `mini-app-nginx` | `frontends/mini-app` | `frontends/mini-app/Dockerfile` |
| `web-panel-nginx` | `frontends/web-panel` | `frontends/web-panel/Dockerfile` |
| `landing-nginx` | `frontends/landing` | `frontends/landing/Dockerfile` |

[VERIFIED: from docker-compose.prod.yml build config and Dockerfile locations in repo]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `docker/build-push-action@v4/v5/v6` | `v7` (Node 24 runtime, ESM, 2025-03-05) | March 2025 | Newer runtime; breaking: removed deprecated env vars |
| `docker/login-action@v2` | `v3` (v3.5.0) | 2024 | Node 20 runtime |
| `appleboy/ssh-action@master` | `@v1` (v1.2.5) | Ongoing | Pin to stable release, not moving target |
| Build images on VPS (`docker compose up --build`) | Push to GHCR then pull | 2022+ | Faster deploys, immutable artifacts, portfolio visibility |

**Deprecated/outdated:**
- `docker/build-push-action@v4`: Uses Node 16 runtime which is EOL on GitHub Actions as of 2025
- Using `docker-compose` (v1, standalone binary): Use `docker compose` (v2, plugin) — `docker compose` is the current standard; `docker-compose` is deprecated

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | GHCR packages are private by default; VPS needs authentication to pull | Pattern 3 SSH Deploy | If packages are set to public, no VPS auth needed — simpler setup |
| A2 | JWT keys are generated/stored in Docker volume on VPS on first start; GitHub Actions does NOT need to inject them | Pattern 4 GitHub Secrets | If auth-service requires pre-placed keys, initial VPS setup steps must include key generation and volume population |
| A3 | Branch protection on `main` requires CI checks before merge, so deploy workflow running on `main` implies CI has already passed | Pattern 5 Workflow Trigger | If no branch protection, a broken commit on main could trigger deploy and break VPS |
| A4 | appleboy/ssh-action@v1 uses `-o StrictHostKeyChecking=no` by default, avoiding known_hosts setup | Pitfall 3 | If host key verification is enforced, `fingerprint` input parameter must be added |
| A5 | GitHub Actions ubuntu-latest runner has enough RAM/CPU to build all 11 Docker images sequentially within the 6-hour job timeout | Common Pitfalls | If sequential build takes >6 hours, split into parallel jobs or use GitHub Actions larger runners |
| A6 | `docker compose -f docker-compose.prod.yml` (the compose plugin, not docker-compose binary) is available on the VPS | Pattern 3 SSH Deploy | Install: `sudo apt install docker-compose-plugin` |

## Open Questions

1. **Are GHCR packages set to public or private?**
   - What we know: Default is private; docker-compose.prod.yml uses `ghcr.io/maksd/...` image references
   - What's unclear: Whether the owner wants public images (simpler VPS deploy) or private (more secure)
   - Recommendation: For a portfolio project, make packages public — simplifies VPS pull auth and makes images inspectable

2. **What is the VPS deploy directory path?**
   - What we know: STATE.md says "VPS user/SSH setup needs research during planning"
   - What's unclear: VPS IP, username, path where docker-compose.prod.yml lives
   - Recommendation: Assume `/opt/rutcampustrack`; document this in VPS setup guide (Phase 48 README)

3. **Does the deploy workflow need to sync docker-compose.prod.yml to VPS?**
   - What we know: The compose file is in git; VPS has it after initial clone
   - What's unclear: Whether compose file changes between deploys need to be pushed to VPS
   - Recommendation: Add `appleboy/scp-action` step to copy `docker-compose.prod.yml` and `.env.prod.example` to VPS before the SSH deploy. This ensures compose file is always current. The `.env.prod` file (with real secrets) must NOT be overwritten.

4. **BuildKit cache for Gradle in Docker builds?**
   - What we know: Each Java Dockerfile runs `./gradlew :service:bootJar` inside Docker build; no BuildKit cache mounts in current Dockerfiles
   - What's unclear: Whether build times will be acceptable (~5 min × 7 Java images = ~35 min per deploy)
   - Recommendation: Accept slow builds for Phase 46. If slow, add `--mount=type=cache,target=/root/.gradle` to RUN directives in Dockerfiles (Phase 42 content, deferred).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker Engine | Build images on runner | ✓ (ubuntu-latest) | Pre-installed | — |
| Docker Buildx | `docker/setup-buildx-action@v3` | ✓ (installed by action) | Latest | — |
| SSH access to VPS | deploy job | [ASSUMED] | — | No fallback — VPS must be accessible |
| `docker compose` on VPS | pull + up commands | [ASSUMED] | v2 plugin | Install: `apt install docker-compose-plugin` |
| GHCR package namespace | push images | ✓ (github.com/maksd) | — | — |

**Missing dependencies with no fallback:**
- VPS with Docker installed and SSH accessible — this is a setup prerequisite, not a code issue

## Validation Architecture

> `workflow.nyquist_validation` not set in config.json — treating as enabled.

### Test Framework

This phase creates a GitHub Actions workflow file. "Tests" are workflow runs themselves.

| Property | Value |
|----------|-------|
| Framework | GitHub Actions workflow execution |
| Config file | `.github/workflows/deploy.yml` (to be created) |
| Quick run command | Push a commit to `main` and observe workflow run |
| Full suite command | Same — only one workflow |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CI-05 | All 11 images pushed to GHCR on merge to main | smoke (workflow run) | Merge commit to main; verify packages at `github.com/maksd?tab=packages` | ❌ Wave 0 — workflow file to be created |
| CI-06 | VPS containers updated after workflow | smoke (SSH + manual verify) | After workflow: `ssh vps "docker compose -f /opt/rutcampustrack/docker-compose.prod.yml ps"` | ❌ Wave 0 — requires VPS setup |
| CI-07 | GitHub Secrets configured | configuration (manual) | Settings → Secrets → verify list | ❌ Wave 0 — manual secret setup |

### Wave 0 Gaps
- [ ] `.github/workflows/deploy.yml` — main deliverable of this phase
- [ ] GitHub Secrets must be configured in repository settings (manual step; documented in PLAN)
- [ ] VPS must have Docker, docker-compose-plugin, and repo cloned at `/opt/rutcampustrack` (documented as prerequisite)
- [ ] If GHCR packages are private: VPS must be authenticated to GHCR

*(No existing test infrastructure applies to workflow validation — this is inherently a deploy-time verification)*

## Security Domain

> No `security_enforcement` key in config.json — treating as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | SSH key auth for VPS; GITHUB_TOKEN for GHCR — no passwords in workflow |
| V3 Session Management | no | — |
| V4 Access Control | yes | `permissions: packages: write` scoped to deploy job only; SSH key principle of least privilege |
| V5 Input Validation | no | Workflow has no user-provided input fields |
| V6 Cryptography | yes | ED25519 SSH keys (preferred over RSA-2048); GITHUB_TOKEN rotation is automatic |

### Known Threat Patterns for GitHub Actions Deploy Workflows

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SSH private key leakage via log output | Information Disclosure | Never echo SSH key; GitHub Actions masks secrets in logs automatically |
| Malicious PR triggering deploy (if workflow_run used) | Elevation of Privilege | Trigger on `push: branches: [main]` (not `pull_request`) — deploy only runs after merge |
| GHCR image tampering between push and pull | Tampering | Use `:latest` tag (acceptable for single-VPS portfolio); production would use digest pinning |
| VPS credentials stored insecurely | Information Disclosure | Store only in GitHub Secrets, never in workflow file or `.env.prod.example` |
| Secrets leaking via `run:` step logging | Information Disclosure | Never `echo ${{ secrets.X }}` in run steps; use secrets in `with:` inputs only |

## Sources

### Primary (HIGH confidence)
- [docs.github.com — Publishing packages with GitHub Actions](https://docs.github.com/en/packages/managing-github-packages-using-github-actions-workflows/publishing-and-installing-a-package-with-github-actions) — permissions block, GITHUB_TOKEN pattern for GHCR
- [github.com/appleboy/ssh-action releases](https://github.com/appleboy/ssh-action/releases) — v1.2.5 is latest; v1 tag is stable
- [github.com/docker/build-push-action releases](https://github.com/docker/build-push-action/releases) — v7.0.0 released 2025-03-05 is latest
- [github.com/docker/login-action releases](https://github.com/docker/login-action/releases/tag/v3.5.0) — v3.5.0 is latest
- Project codebase [VERIFIED]: `docker-compose.prod.yml` (all 11 image names, build contexts, Dockerfile paths), `.env.prod.example` (all secret names), `.github/workflows/ci.yml` (existing CI pattern to extend)

### Secondary (MEDIUM confidence)
- [docs.servicestack.net/ssh-docker-compose-deploment](https://docs.servicestack.net/ssh-docker-compose-deploment) — SSH deploy with docker compose pull pattern; appleboy/ssh-action usage example
- [docs.github.com — Working with the Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry) — GHCR visibility defaults (private by default)

### Tertiary (LOW confidence)
- [notes.kodekloud.com — Workflow Login and Push to GHCR](https://notes.kodekloud.com/docs/GitHub-Actions/Continuous-Integration-with-GitHub-Actions/Workflow-Login-and-Push-to-GHCR/page) — example workflow with `packages: write`; versions cited (v2.2.0, v4) are outdated — use v3 and v7 respectively

## Project Constraints (from CLAUDE.md)

The following CLAUDE.md directives apply to this phase:

- **Monorepo structure:** All 11 service images must be built and pushed; contexts differ per service type (root for Java, subdirectory for bot/frontends)
- **python:3.12-slim for notification-bot:** Dockerfile already uses this base image [VERIFIED]
- **No Lombok in contract modules:** No impact on Docker/CI layer
- **Docker compose:** Use `docker compose` (v2 plugin) not `docker-compose` (v1 binary) in SSH deploy scripts

## Metadata

**Confidence breakdown:**
- Standard stack (action versions): HIGH — verified via GitHub releases pages
- GHCR push pattern (login-action + build-push-action): HIGH — official GitHub docs
- SSH deploy pattern (appleboy/ssh-action): HIGH — official action repo verified, v1.2.5 confirmed
- Build context mapping (11 services): HIGH — verified against docker-compose.prod.yml and Dockerfile locations
- VPS setup prerequisites: MEDIUM — depends on actual VPS configuration (not yet provisioned)
- GHCR package visibility behavior: MEDIUM — documented as private-by-default, but owner can change

**Research date:** 2026-04-08
**Valid until:** 2026-07-08 (stable GitHub Actions action versions; appleboy/ssh-action stable API)
