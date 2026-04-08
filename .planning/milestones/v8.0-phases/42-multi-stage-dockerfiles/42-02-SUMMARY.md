---
phase: 42-multi-stage-dockerfiles
plan: "02"
subsystem: notification
tags: [docker, java, python, multi-stage-build, grpcio]
dependency_graph:
  requires: []
  provides: [DOCK-02, DOCK-03]
  affects: [services/notification-service/notification-app, services/notification-bot]
tech_stack:
  added: [python:3.12-slim]
  patterns: [multi-stage-docker-build, non-root-container]
key_files:
  created:
    - services/notification-bot/.dockerignore
  modified:
    - services/notification-service/notification-app/Dockerfile
    - services/notification-bot/Dockerfile
key_decisions:
  - "notification-web follows same 3-stage pattern as other Java services"
  - "notification-bot uses python:3.12-slim (not Alpine) to avoid grpcio compilation issues"
  - "Bot runs as non-root 'botuser', .dockerignore excludes tests/"
metrics:
  completed_date: "2026-04-07"
  tasks_completed: 2
  tasks_total: 2
  files_created: 1
  files_modified: 2
requirements: [DOCK-02, DOCK-03]
---

# Phase 42 Plan 02: Notification Dockerfiles Summary

**One-liner:** notification-web upgraded to multi-stage Dockerfile; notification-bot hardened with python:3.12-slim, non-root user, and .dockerignore.

## Tasks Completed

| Task | Name | Files |
|------|------|-------|
| 1 | Upgrade notification-web to multi-stage Dockerfile | services/notification-service/notification-app/Dockerfile |
| 2 | Harden notification-bot Dockerfile | services/notification-bot/Dockerfile, services/notification-bot/.dockerignore |

## What Was Built

**notification-web:** Same 3-stage pattern as other Java services (builder → extractor → runtime). Includes COPY for `proto/` and `notification-api-contract/`. Runs as non-root `app` user.

**notification-bot:** Uses `python:3.12-slim` base (grpcio requires glibc, Alpine has musl). Non-root `botuser`. `.dockerignore` excludes `tests/`, `__pycache__/`, `*.pyc`.

## Verification

Docker builds verified by human:
- `docker build -f services/notification-service/notification-app/Dockerfile -t rct-notification-web .` → PASS
- `docker build -t rct-bot services/notification-bot/` → PASS
- `docker run --rm rct-bot python -c "import grpc; print('ok')"` → PASS

## Known Stubs

None.
