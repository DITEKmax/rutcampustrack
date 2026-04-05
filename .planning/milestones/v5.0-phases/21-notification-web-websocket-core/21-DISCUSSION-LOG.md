# Phase 21: Notification Web — WebSocket Core - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-05
**Phase:** 21-notification-web-websocket-core
**Areas discussed:** JWT auth at handshake, STOMP topic structure, WebSocket message format, Session & connection lifecycle

---

## JWT Auth at Handshake

### Q1: How should notification-web get the JWT public key?

| Option | Description | Selected |
|--------|-------------|----------|
| Shared file/env var | Same as API Gateway — public key mounted as file or env var. Simple, no network calls. | ✓ |
| JWKS endpoint | Auth Service exposes /.well-known/jwks.json. More standard but adds runtime dependency. | |
| Embedded in config | Public key hardcoded in application.yml. Simplest but requires redeploy on key rotation. | |

**User's choice:** Shared file/env var (Recommended)
**Notes:** Follows existing API Gateway pattern (PublicKeyConfig.java).

### Q2: How should the client pass the JWT token?

| Option | Description | Selected |
|--------|-------------|----------|
| Query parameter ?token=xxx | Standard for SockJS/STOMP. Browser WebSocket API doesn't support custom headers. | ✓ |
| STOMP CONNECT frame header | More secure (not in URL logs) but connection unauthenticated until CONNECT. | |
| Both supported | Accept from either location. More flexible. | |

**User's choice:** Query parameter ?token=xxx (Recommended)
**Notes:** None

---

## STOMP Topic Structure

### Q3: How should topics be structured for group routing?

| Option | Description | Selected |
|--------|-------------|----------|
| /topic/group/{groupId} | Single topic per group. All events go here. Client subscribes to one topic. | ✓ |
| /topic/group/{groupId}/{eventType} | Separate topic per event type per group. More granular but many subscriptions. | |
| /topic/group/{groupId} + /user/queue/private | Group events on shared topic. Headman-only via user-specific queue. | |

**User's choice:** /topic/group/{groupId} (Recommended)
**Notes:** None

### Q4: How should headman-only events be delivered?

| Option | Description | Selected |
|--------|-------------|----------|
| Server-side filter on group topic | All events go to group topic. Server checks is_headman before sending. | ✓ |
| Separate headman topic | /topic/group/{id}/headman. Requires subscription-level auth. | |
| User-specific queue (convertAndSendToUser) | Personal queue per headman. Cleanest separation but more complex. | |

**User's choice:** Server-side filter on group topic (Recommended)
**Notes:** None

---

## WebSocket Message Format

### Q5: What should the pushed message payload look like?

| Option | Description | Selected |
|--------|-------------|----------|
| Forward event payload as-is | Send RabbitMQ event payload directly. No enrichment. Stateless. | ✓ |
| Enriched DTO with names | Resolve IDs to names via gRPC. Adds latency and dependency. | |
| Minimal notification | Type + key IDs only. Client fetches via REST. Extra round-trip. | |

**User's choice:** Forward event payload as-is (Recommended)
**Notes:** notification-web stays stateless, no gRPC dependencies.

---

## Session & Connection Lifecycle

### Q6: Concurrent session limit per user?

| Option | Description | Selected |
|--------|-------------|----------|
| No limit | Multiple tabs/devices all receive events. Simple. Scale is fine for ~5000 users. | ✓ |
| 1 session per user | New connection kicks old. Breaks multi-tab. | |
| Max 3 sessions | Reasonable cap but requires tracking. | |

**User's choice:** No limit (Recommended)
**Notes:** None

### Q7: STOMP endpoint transport?

| Option | Description | Selected |
|--------|-------------|----------|
| SockJS fallback | withSockJS() on endpoint /ws. Fallback for blocked WebSocket. | ✓ |
| Raw WebSocket only | No SockJS. Lighter but some proxies may block. | |

**User's choice:** SockJS fallback (Recommended)
**Notes:** None

---

## Claude's Discretion

- WebSocketConfig class structure and bean naming
- GroupSessionRegistry implementation details
- EventConsumer routing implementation
- Error handling, test strategy

## Deferred Ideas

None — discussion stayed within phase scope.
