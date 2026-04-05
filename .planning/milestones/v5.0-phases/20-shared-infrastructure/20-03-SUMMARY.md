---
phase: 20-shared-infrastructure
plan: "03"
subsystem: docker-compose
tags: [docker, docker-compose, notification-web, notification-bot, health-check]
dependency_graph:
  requires: [notification-web RabbitMQ queue binding, notification-bot aio-pika consumer]
  provides: [notification-web container, notification-bot container, docker-compose integration]
  affects: [docker-compose.yml, services/notification-web/Dockerfile]
tech_stack:
  - Docker Compose
  - eclipse-temurin:21-jre-alpine
  - python:3.12-slim
---

## Summary

Added notification-web and notification-bot service definitions to docker-compose.yml with full health check, dependency, and restart configuration. Created notification-web Dockerfile using eclipse-temurin:21-jre-alpine.

## Tasks

| # | Task | Status | Commit |
|---|------|--------|--------|
| 1 | Add notification containers to docker-compose and create notification-web Dockerfile | ✓ Complete | `50ec3df` |
| 2 | Verify docker-compose configuration (human checkpoint) | ✓ Approved | — |

## Key Files

### Created
- `services/notification-web/Dockerfile` — eclipse-temurin:21-jre-alpine with wget for health checks

### Modified
- `docker-compose.yml` — Added notification-web (port 9094, actuator health) and notification-bot (port 8081, curl health) service definitions

## Decisions

- Used `wget` for notification-web health check (alpine JRE lacks curl)
- Used `curl` for notification-bot health check (Dockerfile installs curl explicitly)
- `start_period: 30s` for notification-web (Spring Boot startup), `15s` for notification-bot (Python starts faster)
- Both services depend on redis and rabbitmq with `condition: service_healthy`

## Deviations

- **requirements.txt dependency fixes**: aiogram 3.15.0 constraints required downgrading aiohttp (3.11.11→3.10.11) and pydantic (2.10.4→2.9.2), and bumping protobuf (5.29.3→6.30.2) for grpcio-tools 1.73.0 compatibility. Fixed in separate commits on main.

## Verification

- `docker compose config` — validates without errors
- `docker compose up -d` — both containers start and report `(healthy)` status
- `docker compose down` — clean shutdown
