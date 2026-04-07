---
phase: 46-github-actions-deploy
plan: "01"
status: complete
committed_sha: 07bd414
files_created:
  - .github/workflows/deploy.yml
files_modified: []
requirements_satisfied:
  - CI-05
  - CI-06
  - CI-07
one_liner: "GitHub Actions deploy workflow: 11 GHCR image pushes + SSH deploy to VPS on merge to main"
key_decisions:
  - "Sequential build steps (not matrix) — simpler for 11 images; no parallelism benefit worth the complexity"
  - "Job-level permissions block for packages:write — follows CI-05 requirement and GitHub best-practice least-privilege"
  - "context: . for all Java services — Gradle build requires repo root to access gradlew, settings.gradle.kts, all subprojects"
  - "No GITHUB_TOKEN forwarded to VPS — VPS authenticates to GHCR separately (one-time manual login or public packages)"
tech_stack:
  added:
    - docker/login-action@v3
    - docker/build-push-action@v7
    - docker/setup-buildx-action@v3
    - appleboy/ssh-action@v1
  patterns:
    - GHCR push via GITHUB_TOKEN with packages:write permission
    - SSH deploy via appleboy/ssh-action with PEM key secret
    - Two-job pipeline with needs dependency (build-push -> deploy)
---

# Phase 46 Plan 01: GitHub Actions Deploy Workflow Summary

## Objective

Create `.github/workflows/deploy.yml` that automatically builds all 11 service images, pushes them to GHCR, and deploys to the VPS via SSH on every merge to `main`.

## What Was Built

A two-job GitHub Actions workflow at `.github/workflows/deploy.yml`:

**Job 1: `build-push`** (Build and Push to GHCR)
- Triggers on `push: branches: [main]` only
- Job-level `permissions: contents: read, packages: write`
- Uses `docker/setup-buildx-action@v3` + `docker/login-action@v3` to authenticate to `ghcr.io` via `GITHUB_TOKEN`
- 11 sequential `docker/build-push-action@v7` steps, each pushing to `ghcr.io/maksd/rutcampustrack/<service>:latest`
- Correct build contexts: `.` for Java services (Gradle monorepo root), `services/notification-bot` for the Python bot, `frontends/<name>` for each frontend

**Job 2: `deploy`** (Deploy to VPS)
- `needs: build-push` — runs only after all 11 images are pushed
- Uses `appleboy/ssh-action@v1` with three secrets: `VPS_HOST`, `VPS_USER`, `SSH_PRIVATE_KEY`
- SSH script: `cd /opt/rutcampustrack && docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d --remove-orphans`
- Uses `docker compose` (v2 plugin syntax), not `docker-compose` (v1)

## Tasks Completed

| Task | Name | Status | Commit |
|------|------|--------|--------|
| 1 | Create deploy.yml workflow with GHCR push and SSH deploy | Complete | 07bd414 |
| 2 | Configure GitHub Secrets in repository settings (CI-07) | Checkpoint: human-action — requires manual secret configuration in GitHub UI |

## Verification Results

All 16 automated checks passed:

- `packages: write` present in job permissions block
- `docker/build-push-action@v7` used for all steps
- `appleboy/ssh-action@v1` present in deploy job
- Exactly 11 `build-push-action@v7` steps
- Trigger: `push: branches: [main]`
- `docker/login-action@v3` with `registry: ghcr.io`
- `docker/setup-buildx-action@v3` present
- All 11 image tags match docker-compose.prod.yml exactly
- `notification-bot` uses `context: services/notification-bot`
- All 4 frontends use `context: frontends/<name>`
- `needs: build-push` on deploy job
- All 3 secret references present (`VPS_HOST`, `VPS_USER`, `SSH_PRIVATE_KEY`)
- SSH script uses `docker compose` v2 syntax (not `docker-compose`)
- `--remove-orphans` flag present
- No hardcoded secrets detected

## Commit Log

- `07bd414` — feat(46-01): add GitHub Actions deploy workflow with GHCR push and SSH deploy

## Checkpoint: Task 2 (Human Action Required)

Task 2 is a `checkpoint:human-action` — it requires the repository owner to configure 3 GitHub Secrets before the workflow can run successfully:

1. `VPS_HOST` — VPS IP address or domain (from hosting provider)
2. `VPS_USER` — SSH username with docker group membership (e.g. `ubuntu`)
3. `SSH_PRIVATE_KEY` — Full PEM content of an ED25519 key (generate with `ssh-keygen -t ed25519 -C "github-actions-deploy"`; add public key to VPS `~/.ssh/authorized_keys`)

Location: GitHub repo → Settings → Secrets and variables → Actions → New repository secret

VPS one-time prerequisites:
- Docker + docker-compose-plugin installed
- Deploy user in docker group
- Repo cloned at `/opt/rutcampustrack`
- `.env.prod` file populated from `.env.prod.example`
- GHCR login (if packages are private): `docker login ghcr.io -u maksd -p <PAT>`

## Deviations from Plan

None — plan executed exactly as written.

## Threat Flags

No new threat surface introduced beyond what is already documented in the plan's threat model (T-46-01 through T-46-06). All mitigations are applied:
- Trigger is `push: branches: [main]` (not `pull_request`) — prevents PRs from triggering deploy (T-46-02)
- SSH script contains only `cd`, `docker compose pull`, `docker compose up` — no secret echoing (T-46-04)
- Secrets referenced via `${{ secrets.* }}` only — none hardcoded (T-46-01)

## Self-Check: PASSED

- `.github/workflows/deploy.yml` exists: FOUND
- Commit `07bd414` exists: FOUND
