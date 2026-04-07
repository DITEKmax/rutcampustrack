---
phase: 42-multi-stage-dockerfiles
plan: "01"
subsystem: backend
tags: [docker, java, multi-stage-build, spring-boot, layered-jar]
dependency_graph:
  requires: []
  provides: [DOCK-01]
  affects: [services/api-gateway, services/auth-service, services/academic-service, services/schedule-service, services/attendance-service]
tech_stack:
  added: [eclipse-temurin:21-jdk-alpine, eclipse-temurin:21-jre-alpine]
  patterns: [multi-stage-docker-build, spring-boot-layered-jar, non-root-container]
key_files:
  created:
    - .dockerignore
    - services/api-gateway/Dockerfile
    - services/auth-service/Dockerfile
    - services/academic-service/academic-app/Dockerfile
    - services/schedule-service/schedule-app/Dockerfile
    - services/attendance-service/attendance-app/Dockerfile
  modified: []
key_decisions:
  - "3-stage pattern: builder (JDK+Gradle) → extractor (layertools) → runtime (JRE-only)"
  - "Multi-module services (academic, schedule, attendance) COPY proto/ and *-api-contract/ into builder"
  - "All runtime images use eclipse-temurin:21-jre-alpine with non-root 'app' user"
metrics:
  completed_date: "2026-04-07"
  tasks_completed: 2
  tasks_total: 2
  files_created: 6
  files_modified: 0
requirements: [DOCK-01]
---

# Phase 42 Plan 01: Root .dockerignore + Java Service Dockerfiles Summary

**One-liner:** Root .dockerignore and 5 multi-stage Dockerfiles for all Java services with layered JARs, JRE-only runtime, and non-root user.

## Tasks Completed

| Task | Name | Files |
|------|------|-------|
| 1 | .dockerignore + api-gateway + auth-service Dockerfiles | .dockerignore, services/api-gateway/Dockerfile, services/auth-service/Dockerfile |
| 2 | academic-app + schedule-app + attendance-app Dockerfiles | services/academic-service/academic-app/Dockerfile, services/schedule-service/schedule-app/Dockerfile, services/attendance-service/attendance-app/Dockerfile |

## What Was Built

**Root .dockerignore** excludes `.gradle`, `.env`, `node_modules`, `.git/`, `.planning/`, `*.key`, `*.pem` from Docker build context.

**5 Java Dockerfiles** follow identical 3-stage pattern:
- **Stage 1 (builder):** `eclipse-temurin:21-jdk-alpine`, Gradle build with `-x test --no-daemon`
- **Stage 2 (extractor):** `java -Djarmode=layertools -jar app.jar extract` splits JAR into layers
- **Stage 3 (runtime):** `eclipse-temurin:21-jre-alpine`, 4 COPY layers (dependencies, spring-boot-loader, snapshot-dependencies, application), non-root `app` user

Multi-module services additionally COPY `proto/` and `*-api-contract/` directories.

## Verification

All 5 `docker build` commands verified by human (manual builds):
- `docker build -f services/api-gateway/Dockerfile -t rct-gateway .` → PASS
- `docker build -f services/auth-service/Dockerfile -t rct-auth .` → PASS
- `docker build -f services/academic-service/academic-app/Dockerfile -t rct-academic .` → PASS
- `docker build -f services/schedule-service/schedule-app/Dockerfile -t rct-schedule .` → PASS
- `docker build -f services/attendance-service/attendance-app/Dockerfile -t rct-attendance .` → PASS

## Known Stubs

None.
