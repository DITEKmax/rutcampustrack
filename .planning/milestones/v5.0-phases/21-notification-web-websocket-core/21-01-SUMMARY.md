---
phase: 21-notification-web-websocket-core
plan: 01
subsystem: notification-web
tags: [websocket, stomp, jwt, handshake-interceptor, sockjs]
dependency_graph:
  requires: []
  provides: [websocket-endpoint, jwt-handshake-auth]
  affects: [notification-web]
tech_stack:
  added: [io.jsonwebtoken:jjwt-api:0.12.6, io.jsonwebtoken:jjwt-impl:0.12.6, io.jsonwebtoken:jjwt-jackson:0.12.6]
  patterns: [HandshakeInterceptor, @EnableWebSocketMessageBroker, SockJS fallback, simple broker]
key_files:
  created:
    - services/notification-web/src/main/java/ru/rutcampustrack/notification/config/JwtHandshakeInterceptor.java
    - services/notification-web/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java
    - services/notification-web/src/test/java/ru/rutcampustrack/notification/config/JwtHandshakeInterceptorTest.java
  modified:
    - services/notification-web/build.gradle.kts
    - services/notification-web/src/main/resources/application.yml
decisions:
  - "File-based RSA public key loading (@PostConstruct reads from notification.jwt.public-key-path)"
  - "HandshakeInterceptor over ChannelInterceptor — rejects at HTTP Upgrade, before WebSocket session opens"
  - "No setApplicationDestinationPrefixes — service never receives messages from clients"
  - "setAllowedOriginPatterns(*) for web panel CORS — can be tightened for production"
metrics:
  duration: ~15min
  completed: 2026-04-05
  tasks: 3
  files: 5
---

# Phase 21 Plan 01: WebSocket Infrastructure and JWT Auth Summary

STOMP WebSocket endpoint /ws with SockJS fallback and JWT authentication via HandshakeInterceptor, using RSA public key loaded from file at startup.

## What Was Built

WebSocket infrastructure foundation for notification-web: a STOMP endpoint that validates JWT tokens at the HTTP Upgrade level (before any WebSocket session is created), extracts user claims into session attributes, and configures a simple in-memory broker on /topic for group-based event delivery. Plan 02 builds routing logic on top of this foundation.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add jjwt deps and JWT config | b18b525 | build.gradle.kts, application.yml |
| 2 (TDD RED) | Add failing tests for JwtHandshakeInterceptor | faed80a | JwtHandshakeInterceptorTest.java |
| 2 (TDD GREEN) | Implement JwtHandshakeInterceptor | 1074d89 | JwtHandshakeInterceptor.java |
| 3 | Create WebSocketConfig | e4dfa99 | WebSocketConfig.java |

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None — no UI rendering stubs or placeholder data sources.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: cors_wildcard | WebSocketConfig.java | setAllowedOriginPatterns("*") allows any origin — acceptable for development but should be tightened to specific web panel origin in production |
| threat_flag: jwt_no_revalidation | JwtHandshakeInterceptor.java | Per D-03, JWT is validated only at handshake — a client with a revoked token continues receiving events until disconnect. Intentional design decision. |

## Self-Check: PASSED

- [x] services/notification-web/src/main/java/ru/rutcampustrack/notification/config/JwtHandshakeInterceptor.java — FOUND
- [x] services/notification-web/src/main/java/ru/rutcampustrack/notification/config/WebSocketConfig.java — FOUND
- [x] services/notification-web/src/test/java/ru/rutcampustrack/notification/config/JwtHandshakeInterceptorTest.java — FOUND
- [x] Commit b18b525 — FOUND
- [x] Commit faed80a — FOUND
- [x] Commit 1074d89 — FOUND
- [x] Commit e4dfa99 — FOUND
- [x] All tests pass: `./gradlew :services:notification-web:test` — BUILD SUCCESSFUL
