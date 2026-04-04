# Phase 20: Shared Infrastructure - Context

**Gathered:** 2026-04-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Both notification services (notification-web Java, notification-bot Python) connected to the RabbitMQ event stream. Each has a dedicated durable queue with DLQ bound to the existing fanout exchange. Both containers defined in docker-compose with health checks. Redis reminder key namespace documented and accessible. Bot project skeleton with basic consumer.

</domain>

<decisions>
## Implementation Decisions

### Queue Naming and DLQ Strategy
- **D-01:** Queue names follow existing pattern: `notification-web.events`, `notification-bot.events` (consistent with `attendance-service.events`)
- **D-02:** DLQ queues: `notification-web.events.dlq`, `notification-bot.events.dlq` — manual replay only via RabbitMQ Management UI, no auto-retry TTL loops
- **D-03:** Each service declares its own RabbitConfig with exchange/queue/DLQ beans — copy the Attendance Service pattern per service, no shared RabbitMQ config module

### Docker Container Config
- **D-04:** notification-web health check: Spring Actuator `/actuator/health` (add `spring-boot-starter-actuator` dependency). Docker healthcheck: `curl http://localhost:9094/actuator/health`
- **D-05:** notification-bot health check: tiny aiohttp HTTP server on port 8081 with `/health` endpoint that checks RabbitMQ connection is alive. Docker healthcheck: `curl http://localhost:8081/health`
- **D-06:** Restart policy: `unless-stopped` for both containers

### Redis Key Namespace
- **D-07:** Reminder message IDs stored as Redis list: key `reminder:msgs:{lesson_id}:{user_id}`, operations RPUSH (add), LRANGE 0 -1 (read all), DEL (cleanup)
- **D-08:** TTL safety net: EXPIRE 86400 (24 hours) on each key. Normal cleanup via lesson.closed / attendance.marked events. TTL prevents permanent Redis leak if events are lost

### Bot Python Project Structure
- **D-09:** Modular package layout:
  ```
  services/notification-bot/
  ├── bot/
  │   ├── __init__.py
  │   ├── __main__.py           ← entry point
  │   ├── config.py             ← Pydantic Settings
  │   ├── handlers/             ← Telegram command handlers
  │   ├── consumers/            ← RabbitMQ event consumers
  │   ├── grpc_client/          ← Academic Service gRPC calls
  │   └── services/             ← send queue, reminder logic
  ├── requirements.txt
  ├── Dockerfile
  └── .env.example
  ```
- **D-10:** Config via pydantic-settings with `.env` file — type-safe, auto-validates
- **D-11:** grpcio pinned at 1.73.0 (compatible with protobuf 5.x, per STATE.md decision). Update requirements.txt from 1.69.0
- **D-12:** Phase 20 creates bot skeleton AND basic aio-pika consumer that connects, receives messages, and logs them — proves end-to-end queue binding

### Claude's Discretion
- Docker resource limits (memory, CPU) — not critical for dev compose
- Exact Dockerfile contents (multi-stage, base image choice)
- notification-web RabbitConfig bean naming convention (follow Attendance Service pattern)
- Bot __main__.py asyncio setup and signal handling

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### RabbitMQ Pattern (reference implementation)
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/RabbitConfig.java` — Complete fanout exchange + DLQ pattern to replicate
- `services/attendance-service/attendance-app/src/main/resources/application.yml` — RabbitMQ connection config

### Existing Service Configs
- `services/notification-web/src/main/resources/application.yml` — Current notification-web config (has RabbitMQ connection, needs queue config)
- `services/notification-web/build.gradle.kts` — Dependencies (has starter-amqp and starter-websocket)
- `services/notification-bot/requirements.txt` — Python dependencies (needs grpcio version bump)

### Event Schemas
- `event-schemas/` — All 7 JSON Schema files defining event envelope format and payloads

### Architecture
- `docs/architecture.md` — Service topology, communication matrix, Redis key namespaces
- `docs/phases-plan.md` §Фаза 6 — Notification Service detailed plan
- `docker-compose.yml` — Current infrastructure containers (add notification-web and notification-bot)

### Project Decisions
- `.planning/STATE.md` §Accumulated Context — grpcio version, aio-pika watchdog, Redis RPUSH decision

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RabbitConfig.java` (Attendance Service): Complete pattern with FanoutExchange, Queue, DLQ, Binding, Jackson2JsonMessageConverter — copy and adapt bean names
- `application.yml` (notification-web): Already has RabbitMQ connection config (host, port, credentials)
- `build.gradle.kts` (notification-web): Already has `spring-boot-starter-amqp` and `spring-boot-starter-websocket`

### Established Patterns
- RabbitMQ: Fanout exchange `rut-uit.events` (durable, non-auto-delete), declared by each consuming service independently
- DLQ: Direct exchange `rut-uit.events.dlq` with routing key `{service-name}.events.dlq`
- Jackson: Use Spring's auto-configured ObjectMapper (has JavaTimeModule), never create new ObjectMapper
- No `channelTransacted=true` on RabbitTemplate (causes message loss)

### Integration Points
- `docker-compose.yml`: Add notification-web and notification-bot containers with depends_on [redis, rabbitmq]
- RabbitMQ Management UI: Port 15672 already exposed in dev compose — use for DLQ replay

</code_context>

<specifics>
## Specific Ideas

No specific requirements — follow established patterns from Attendance Service.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 20-shared-infrastructure*
*Context gathered: 2026-04-05*
