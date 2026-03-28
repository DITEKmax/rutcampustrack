# RutCampusTrack

## Vision
Microservice attendance tracking system for RUT MIIT university. Production system + portfolio case with full microservice architecture.

## Core Problem
Replace three separate backends (Spring Boot web, Python FastAPI + Aiogram bot, Telegram Mini App) with a unified microservice architecture for tracking student attendance.

## Target Users
- **Students** (500-5000): geo-checkin, excuse tickets, homework tracker
- **Headmen** (student + is_headman): attendance marking, schedule, subjects, assistants
- **Teachers**: read-only journal, statistics, export
- **Admins**: user/group/semester management, dashboard

## Tech Stack
- **Backend**: Java 21, Spring Boot 3.4, Gradle Kotlin DSL (monorepo)
- **Databases**: PostgreSQL (academic_db, schedule_db), MongoDB (attendance_db), Redis (cache + auth)
- **Messaging**: RabbitMQ (fanout exchange), gRPC (inter-service)
- **Frontend**: React (Telegram Mini App), Angular (web panel), HTML/CSS (landing)
- **Bot**: Python Aiogram 3

## Architecture
5 services + API Gateway + 2 notification containers. Contract-first (api-contract + app modules). HATEOAS Level 3, RFC 7807, Flyway, enums as lowercase strings.

## Developer
Solo developer (Persik), lead developer and sysadmin. IntelliJ IDEA on Windows, deploy to VPS with Docker.

## Milestones

### Milestone 1: Auth Service + API Gateway (current)
**Goal**: Working authentication — user can login, get JWT, refresh token, reset password via OTP. Gateway validates JWT and routes requests.

**Scope**:
- Auth Service (port 9090): JWT RSA, login/refresh/logout, OTP flow, change-password, BCrypt, Redis, reads academic_db
- API Gateway (port 8080): JWT filter, route config, public routes, header injection (X-User-Id, X-User-Role, X-Group-Id, X-Is-Headman)
- Test seed data (V2 Flyway migration)

### Previous: Phase 0 (completed)
Scaffold, contracts, infrastructure. See docs/phase-0-report.md.

## Current State
Phase 1.1 complete (2026-03-29) — Auth Service Core: JWT RSA token generation/validation, login/refresh/logout endpoints, Redis refresh token storage, JwtAuthenticationFilter, Spring Security config, test seed data. Next: Phase 1.2 (OTP + Change Password).

Last updated: 2026-03-29
