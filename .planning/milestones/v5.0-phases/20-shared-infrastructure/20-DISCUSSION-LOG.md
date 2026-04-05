# Phase 20: Shared Infrastructure - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-05
**Phase:** 20-shared-infrastructure
**Areas discussed:** Queue naming and DLQ strategy, Docker container config, Redis key namespace, Bot Python project structure

---

## Queue Naming and DLQ Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Follow existing pattern | notification-web.events, notification-bot.events — consistent with attendance-service.events | ✓ |
| Shorter names | notif-web.events, notif-bot.events — more compact but breaks naming pattern | |

**User's choice:** Follow existing pattern
**Notes:** Consistency with attendance-service.events naming

---

| Option | Description | Selected |
|--------|-------------|----------|
| Manual replay only | Dead letters sit in DLQ until manually replayed via RabbitMQ Management UI | ✓ |
| Auto-retry with TTL | DLQ messages auto-republish after N seconds via x-message-ttl loop | |
| You decide | Claude picks the approach | |

**User's choice:** Manual replay only

---

| Option | Description | Selected |
|--------|-------------|----------|
| Copy pattern per service | Each service declares its own exchange/queue/DLQ beans. Zero coupling | ✓ |
| Shared RabbitMQ config library | Extract common config to shared module | |

**User's choice:** Copy pattern per service

---

## Docker Container Config

| Option | Description | Selected |
|--------|-------------|----------|
| Spring Actuator /actuator/health | Add spring-boot-starter-actuator, auto includes RabbitMQ check | ✓ |
| Custom /health endpoint | Minimal health endpoint without Actuator | |
| You decide | Claude picks | |

**User's choice:** Spring Actuator /actuator/health

---

| Option | Description | Selected |
|--------|-------------|----------|
| Tiny HTTP health endpoint | aiohttp on port 8081 with /health checking RabbitMQ connection | ✓ |
| Process check only | Docker pgrep check — simple but doesn't detect stuck consumers | |
| File-based heartbeat | Write timestamp to /tmp/heartbeat every 30s | |

**User's choice:** Tiny HTTP health endpoint on port 8081

---

| Option | Description | Selected |
|--------|-------------|----------|
| unless-stopped | Restart on crash but not when manually stopped | ✓ |
| always | Always restart regardless | |
| on-failure with max retries | restart: on-failure, max_retries: 5 | |

**User's choice:** unless-stopped

---

## Redis Key Namespace

| Option | Description | Selected |
|--------|-------------|----------|
| reminder:msgs:{lesson_id}:{user_id} | As documented in REQUIREMENTS.md (INFRA-03). RPUSH list | ✓ |
| reminder:{lesson_id}:{user_id}:msgs | Alternative nesting, entity-first | |
| bot:reminder:{lesson_id}:{user_id} | Service-prefixed namespace | |

**User's choice:** reminder:msgs:{lesson_id}:{user_id}

---

| Option | Description | Selected |
|--------|-------------|----------|
| 24-hour TTL safety net | EXPIRE 86400 on each key. Normal cleanup via events. TTL prevents leaks | ✓ |
| No TTL, rely on event cleanup | Keys only deleted on events. Risk: permanent Redis leak | |
| You decide | Claude picks | |

**User's choice:** 24-hour TTL safety net

---

## Bot Python Project Structure

| Option | Description | Selected |
|--------|-------------|----------|
| Modular package | bot/ with submodules: handlers/, consumers/, grpc_client/, services/, config.py, __main__.py | ✓ |
| Flat structure | All files in root: main.py, handlers.py, consumer.py, etc. | |
| You decide | Claude picks | |

**User's choice:** Modular package

---

| Option | Description | Selected |
|--------|-------------|----------|
| Pydantic Settings | Type-safe, auto-validates, uses .env file | ✓ |
| Plain os.environ | No validation or type safety | |
| You decide | Claude picks | |

**User's choice:** Pydantic Settings

---

| Option | Description | Selected |
|--------|-------------|----------|
| 1.73.0 as per STATE.md | Compatible with protobuf 5.x, aligns with project decision | ✓ |
| Keep 1.69.0 | Currently in requirements.txt, doesn't match decision | |

**User's choice:** grpcio 1.73.0

---

| Option | Description | Selected |
|--------|-------------|----------|
| Skeleton + basic consumer | Directory structure, config, Dockerfile, AND minimal aio-pika consumer | ✓ |
| Skeleton only | Directory structure, config, Dockerfile. Consumer deferred to Phase 22 | |
| You decide | Claude picks | |

**User's choice:** Skeleton + basic consumer

---

## Claude's Discretion

- Docker resource limits (memory, CPU)
- Exact Dockerfile contents (multi-stage, base image)
- notification-web RabbitConfig bean naming
- Bot __main__.py asyncio setup and signal handling

## Deferred Ideas

None — discussion stayed within phase scope.
