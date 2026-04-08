---
phase: 46-github-actions-deploy
verified: 2026-04-08T00:00:00Z
status: human_needed
score: 4/4 must-haves verified
human_verification:
  - test: "Push a commit to main and observe the Actions run"
    expected: "build-push job completes with all 11 images pushed to ghcr.io/maksd/rutcampustrack/*; deploy job SSHes to VPS and containers restart"
    why_human: "Requires live GitHub Actions execution and a configured VPS — cannot be validated from local filesystem"
  - test: "Confirm 3 GitHub Secrets are configured: VPS_HOST, VPS_USER, SSH_PRIVATE_KEY"
    expected: "All 3 secrets present under Settings > Secrets and variables > Actions"
    why_human: "GitHub UI operation — not stored in repository files. Required for workflow to run without error (Task 2 from PLAN, checkpoint:human-action)"
---

# Phase 46: GitHub Actions Deploy Workflow — Verification Report

**Phase Goal:** Create GitHub Actions deploy workflow that builds all 11 service images, pushes them to GHCR, and deploys to VPS via SSH on every merge to main.
**Verified:** 2026-04-08
**Status:** human_needed — all automated checks PASS; live execution requires GitHub Secrets configuration (Task 2)
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | A merge to main triggers the deploy workflow | VERIFIED | `on: push: branches: [main]` — no `pull_request` or other triggers present |
| 2 | All 11 service images are pushed to ghcr.io/maksd/rutcampustrack/* | VERIFIED | Exactly 11 `docker/build-push-action@v7` steps counted; all 11 tags match `docker-compose.prod.yml` exactly |
| 3 | After image push, workflow SSHes to VPS and runs docker compose pull + up -d | VERIFIED | `deploy` job uses `appleboy/ssh-action@v1`; script runs `docker compose -f docker-compose.prod.yml pull` then `up -d --remove-orphans` |
| 4 | No secrets are hardcoded in the workflow file | VERIFIED | All sensitive values use `${{ secrets.* }}` syntax; grep for IPs/passwords/tokens returned only secret references |

**Score:** 4/4 truths verified

---

## Requirement Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|---------|
| CI-05 | Build images and push to GHCR using GITHUB_TOKEN with packages:write | SATISFIED | `permissions: packages: write` at job level; `docker/login-action@v3` uses `password: ${{ secrets.GITHUB_TOKEN }}`; 11 push steps present |
| CI-06 | Deploy to VPS via SSH after image push | SATISFIED | `appleboy/ssh-action@v1` in `deploy` job; `needs: build-push` ordering enforced; SSH script uses `docker compose` v2 |
| CI-07 | Secrets referenced via GitHub Secrets, not hardcoded | SATISFIED (automated) / PENDING (human) | Workflow references `secrets.VPS_HOST`, `secrets.VPS_USER`, `secrets.SSH_PRIVATE_KEY` — no hardcoded values. Actual secret values must be configured in GitHub UI (Task 2) |

---

## Detailed Verification Items

### 1. File Existence and YAML Structure

| Item | Status | Evidence |
|------|--------|---------|
| `.github/workflows/deploy.yml` exists | PASS | File present at path |
| File is valid YAML (no syntax errors) | PASS | Parsed successfully; structure confirms well-formed YAML |

### 2. Trigger Configuration

| Item | Status | Evidence |
|------|--------|---------|
| Trigger is `push: branches: [main]` | PASS | Lines 3-5: `on: push: branches: [main]` |
| No `pull_request` trigger | PASS | grep for `pull_request` returned no output |
| No `workflow_dispatch` or other triggers | PASS | Only `push` trigger present |

### 3. build-push Job Permissions

| Item | Status | Evidence |
|------|--------|---------|
| `permissions: packages: write` present | PASS | Lines 11-13: `permissions: contents: read, packages: write` at job level |
| `docker/login-action@v3` with `registry: ghcr.io` | PASS | Lines 20-25: login step with `registry: ghcr.io`, `username: ${{ github.actor }}`, `password: ${{ secrets.GITHUB_TOKEN }}` |
| `docker/setup-buildx-action@v3` present | PASS | Lines 17-18 |

### 4. Image Build Steps — Count and Tag Matching

Exactly 11 `docker/build-push-action@v7` steps confirmed by `grep -c`. All 11 image tags cross-checked against `docker-compose.prod.yml`:

| Image Tag | In deploy.yml | In docker-compose.prod.yml | Match |
|-----------|--------------|---------------------------|-------|
| `ghcr.io/maksd/rutcampustrack/api-gateway:latest` | PASS | PASS | PASS |
| `ghcr.io/maksd/rutcampustrack/auth-service:latest` | PASS | PASS | PASS |
| `ghcr.io/maksd/rutcampustrack/academic-service:latest` | PASS | PASS | PASS |
| `ghcr.io/maksd/rutcampustrack/schedule-service:latest` | PASS | PASS | PASS |
| `ghcr.io/maksd/rutcampustrack/attendance-service:latest` | PASS | PASS | PASS |
| `ghcr.io/maksd/rutcampustrack/notification-web:latest` | PASS | PASS | PASS |
| `ghcr.io/maksd/rutcampustrack/notification-bot:latest` | PASS | PASS | PASS |
| `ghcr.io/maksd/rutcampustrack/pwa-nginx:latest` | PASS | PASS | PASS |
| `ghcr.io/maksd/rutcampustrack/mini-app-nginx:latest` | PASS | PASS | PASS |
| `ghcr.io/maksd/rutcampustrack/web-panel-nginx:latest` | PASS | PASS | PASS |
| `ghcr.io/maksd/rutcampustrack/landing-nginx:latest` | PASS | PASS | PASS |

### 5. Build Contexts

| Service | Context in deploy.yml | Context in docker-compose.prod.yml | Match |
|---------|-----------------------|-----------------------------------|-------|
| api-gateway | `.` | `.` | PASS |
| auth-service | `.` | `.` | PASS |
| academic-service | `.` | `.` | PASS |
| schedule-service | `.` | `.` | PASS |
| attendance-service | `.` | `.` | PASS |
| notification-web | `.` | `.` | PASS |
| notification-bot | `services/notification-bot` | `./services/notification-bot` | PASS (equivalent — leading `./` is optional) |
| pwa | `frontends/pwa` | `./frontends/pwa` | PASS (equivalent) |
| mini-app | `frontends/mini-app` | `./frontends/mini-app` | PASS (equivalent) |
| web-panel | `frontends/web-panel` | `./frontends/web-panel` | PASS (equivalent) |
| landing | `frontends/landing` | `./frontends/landing` | PASS (equivalent) |

Note: `./` prefix and no prefix are semantically identical for Docker build context paths.

### 6. deploy Job Wiring

| Item | Status | Evidence |
|------|--------|---------|
| `needs: build-push` present | PASS | Line 117 |
| Uses `appleboy/ssh-action@v1` | PASS | Line 121 |
| `host: ${{ secrets.VPS_HOST }}` | PASS | Line 123 |
| `username: ${{ secrets.VPS_USER }}` | PASS | Line 124 |
| `key: ${{ secrets.SSH_PRIVATE_KEY }}` | PASS | Line 125 |
| `port: 22` | PASS | Line 126 |

### 7. SSH Script

| Item | Status | Evidence |
|------|--------|---------|
| `cd /opt/rutcampustrack` | PASS | Line 128 |
| `docker compose -f docker-compose.prod.yml pull` | PASS | Line 129 — v2 plugin syntax |
| `docker compose -f docker-compose.prod.yml up -d --remove-orphans` | PASS | Line 130 |
| No `docker-compose` (v1 binary) used | PASS | grep for `docker-compose ` returned no output |

### 8. Secret Hygiene

| Item | Status | Evidence |
|------|--------|---------|
| No hardcoded IP addresses | PASS | No `192.`, `10.`, `172.` patterns found |
| No hardcoded passwords or tokens | PASS | All credential lines use `${{ secrets.* }}` |
| GITHUB_TOKEN used for GHCR auth | PASS | `password: ${{ secrets.GITHUB_TOKEN }}` — auto-provided by Actions runner |
| No secret echoed in SSH script | PASS | Script contains only `cd`, `docker compose pull`, `docker compose up` |

---

## Anti-Patterns Found

None. No hardcoded secrets, no v1 `docker-compose` binary usage, no placeholder steps, no TODO comments.

---

## Human Verification Required

### 1. Live Workflow Execution

**Test:** Push any commit to `main` branch and watch the Actions tab on GitHub.
**Expected:** `build-push` job completes successfully with all 11 images visible under `ghcr.io/maksd/rutcampustrack/*`; `deploy` job then connects to VPS and reports successful pull and container restart.
**Why human:** Requires a live GitHub Actions runner, real GHCR push, and a reachable VPS. Cannot be verified from local filesystem inspection.

### 2. GitHub Secrets Configuration (Task 2 — Checkpoint: human-action)

**Test:** Go to GitHub repo → Settings → Secrets and variables → Actions → verify presence of:
- `VPS_HOST`
- `VPS_USER`
- `SSH_PRIVATE_KEY`

**Expected:** All 3 secrets are configured. VPS is reachable via SSH with the key. `/opt/rutcampustrack` directory exists on VPS with `.env.prod` populated.
**Why human:** GitHub Secrets are configured through the GitHub UI and are not accessible from the repository filesystem. This was explicitly scoped as a `checkpoint:human-action` task in the PLAN and is a prerequisite for CI-07 to be fully satisfied at runtime.

---

## Summary

The workflow file `.github/workflows/deploy.yml` is complete and correct. All 16 structural verification points pass:

- Correct trigger (push to main only)
- Correct permissions (packages:write at job level)
- Exactly 11 build-push steps with all image tags matching `docker-compose.prod.yml`
- All build contexts match (Java services use repo root `.`, notification-bot uses `services/notification-bot`, frontends use `frontends/<name>`)
- deploy job depends on build-push
- SSH deploy uses appleboy/ssh-action@v1 with all 3 secret references
- SSH script uses docker compose v2 plugin syntax
- No hardcoded secrets anywhere

The only remaining action is human Task 2: configuring the 3 GitHub Secrets (`VPS_HOST`, `VPS_USER`, `SSH_PRIVATE_KEY`) in the repository settings before the first deploy can run.

---

_Verified: 2026-04-08_
_Verifier: Claude (gsd-verifier)_
