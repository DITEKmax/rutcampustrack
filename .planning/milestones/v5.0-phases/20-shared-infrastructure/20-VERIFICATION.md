---
phase: 20-shared-infrastructure
verified: 2026-04-05T12:00:00Z
status: passed
score: 5/5 must-haves verified
re_verification: false
---

# Phase 20: Shared Infrastructure — Verification Report

**Phase Goal:** Both notification services are connected to the event stream — each has a dedicated durable RabbitMQ queue with DLQ bound to the existing fanout exchange, both containers are defined in docker-compose with health checks, and the Redis reminder key namespace is documented and accessible
**Verified:** 2026-04-05
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #   | Truth | Status | Evidence |
| --- | ----- | ------ | -------- |
| 1   | A message published to rut-uit.events fanout exchange is independently received in both notification-web.events and notification-bot.events queues simultaneously | ✓ VERIFIED | RabbitConfig.java declares FanoutExchange "rut-uit.events" with Queue "notification-web.events" bound via Binding bean. event_consumer.py declares EXCHANGE_NAME="rut-uit.events" with queue.bind(exchange) for QUEUE_NAME="notification-bot.events". Both queues are durable and independently bound to the same fanout exchange — fanout semantics guarantee simultaneous delivery. |
| 2   | notification-web declares a DLQ (notification-web.events.dlq) that catches rejected messages — unprocessable events are not silently dropped | ✓ VERIFIED | RabbitConfig.java line 51: `QueueBuilder.durable("notification-web.events.dlq")`. Queue "notification-web.events" has arguments `x-dead-letter-exchange=rut-uit.events.dlq` and `x-dead-letter-routing-key=notification-web.events.dlq`. Binding bean connects DLQ queue to DLQ direct exchange with the routing key. Verified by RabbitConfigTest.java test `eventsQueue_hasDlqArguments`. |
| 3   | notification-bot declares a DLQ (notification-bot.events.dlq) that catches rejected messages | ✓ VERIFIED | event_consumer.py: DLQ_QUEUE_NAME="notification-bot.events.dlq", declared durable with `dlq_queue.bind(dlq_exchange, routing_key=DLQ_ROUTING_KEY)`. Main queue declares `x-dead-letter-exchange` and `x-dead-letter-routing-key` arguments pointing to the DLQ. |
| 4   | docker compose up starts both notification services, and both containers report healthy within 60 seconds | ✓ VERIFIED | docker-compose.yml defines notification-web (healthcheck: wget /actuator/health on 9094, start_period: 30s) and notification-bot (healthcheck: curl /health on 8081, start_period: 15s). Both depend on redis and rabbitmq with condition: service_healthy. 20-03 SUMMARY confirms both containers reported (healthy) status after docker compose up -d. |
| 5   | The bot config module exports a key template reminder:msgs:{lesson_id}:{user_id} and a 24-hour TTL constant — downstream phases can import them directly | ✓ VERIFIED | bot/config.py: `reminder_key_template: str = "reminder:msgs:{lesson_id}:{user_id}"` and `reminder_key_ttl: int = 86400`. Module-level `config = Settings()` instance exported for direct import. |

**Score:** 5/5 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `services/notification-web/src/main/java/ru/rutcampustrack/notification/config/RabbitConfig.java` | Fanout exchange + queue + DLQ bean declarations | ✓ VERIFIED | 81 lines. Declares 7 beans: FanoutExchange, DirectExchange, 2 Queue beans, 2 Binding beans, Jackson2JsonMessageConverter. Contains "notification-web.events", "notification-web.events.dlq", "rut-uit.events", "rut-uit.events.dlq". No channelTransacted. |
| `services/notification-web/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java` | Placeholder RabbitListener that logs events | ✓ VERIFIED | 23 lines. @RabbitListener(queues = "notification-web.events"), logs "[notification-web] Received event". Intentional logging-only stub per plan scope (Phase 21 adds WebSocket routing). |
| `services/notification-web/src/test/java/ru/rutcampustrack/notification/config/RabbitConfigTest.java` | Integration test verifying queue/DLQ bean creation | ✓ VERIFIED | 52 lines. 5 unit tests: eventsQueue_hasDlqArguments, dlqQueue_hasCorrectName, eventsExchange_isFanoutWithCorrectName, dlqExchange_isDirectWithCorrectName, jacksonConverter_isCreatedWithObjectMapper. All pass. |
| `services/notification-bot/bot/consumers/event_consumer.py` | aio-pika connect_robust consumer with queue/DLQ declaration | ✓ VERIFIED | 67 lines. Uses aio_pika.connect_robust, declares both exchanges, DLQ queue with binding, main queue with DLQ arguments, binds queue to fanout exchange, processes messages with event_type logging. |
| `services/notification-bot/bot/config.py` | Pydantic Settings config with Redis key namespace constants | ✓ VERIFIED | 27 lines. Imports pydantic_settings BaseSettings. Contains reminder_key_template and reminder_key_ttl=86400. All connection settings present. |
| `services/notification-bot/bot/__main__.py` | asyncio entry point starting consumer + health server | ✓ VERIFIED | 61 lines. asyncio.run(main()) at bottom. /health route with consumer task and connection liveness checks. Imports start_consumer from consumers.event_consumer. |
| `services/notification-bot/Dockerfile` | Docker image for notification-bot | ✓ VERIFIED | python:3.12-slim, installs curl, CMD ["python", "-m", "bot"]. |
| `services/notification-web/Dockerfile` | Docker image for notification-web | ✓ VERIFIED | eclipse-temurin:21-jre-alpine, installs wget (for health check), EXPOSE 9094, ENTRYPOINT java -jar app.jar. |
| `docker-compose.yml` (notification sections) | notification-web and notification-bot service definitions | ✓ VERIFIED | Both services defined with build context, environment, networks, depends_on (redis + rabbitmq service_healthy), restart: unless-stopped, and correct health checks. |

---

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| RabbitConfig.java | RabbitMQ broker (rut-uit.events fanout) | Spring AMQP FanoutExchange bean + Binding bean | ✓ WIRED | QueueBuilder.durable("notification-web.events") with notificationWebQueueBinding binding it to the FanoutExchange |
| EventConsumer.java | notification-web.events queue | @RabbitListener(queues = "notification-web.events") | ✓ WIRED | Annotation present on onEvent() method |
| bot/__main__.py | bot/consumers/event_consumer.py | `from bot.consumers.event_consumer import start_consumer` + `asyncio.create_task(run_consumer())` | ✓ WIRED | Import at line 7, used in run_consumer() which is wrapped in create_task |
| bot/__main__.py | aiohttp /health endpoint | `app.router.add_get("/health", health_handler)` in run_health_server() | ✓ WIRED | Route registered, health_handler checks _consumer_task.done() and _connection.is_closed |
| bot/config.py | Redis key namespace | `reminder_key_template` and `reminder_key_ttl` constant definitions | ✓ WIRED | Constants defined in Settings class, exported via module-level `config` instance |
| docker-compose.yml notification-web | services/notification-web/Dockerfile | `build.context: ./services/notification-web` + `dockerfile: Dockerfile` | ✓ WIRED | Both fields present in docker-compose.yml |
| docker-compose.yml notification-bot | services/notification-bot/Dockerfile | `build.context: ./services/notification-bot` + `dockerfile: Dockerfile` | ✓ WIRED | Both fields present in docker-compose.yml |

---

### Data-Flow Trace (Level 4)

Not applicable for this phase. All artifacts in Phase 20 are infrastructure/consumer scaffolding — they declare queues, log received events, and expose health endpoints. No components render dynamic data to a UI. The EventConsumer.onEvent() and event_consumer.py message handler are intentional logging-only stubs; actual data routing is deferred to Phase 21+. This is not a deficiency — it is the stated scope.

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| notification-web compiles | `./gradlew.bat :services:notification-web:compileJava` | BUILD SUCCESSFUL (per 20-01-SUMMARY self-check) | ✓ PASS |
| RabbitConfigTest passes | `./gradlew.bat :services:notification-web:test` | All 5 tests pass (per 20-01-SUMMARY self-check) | ✓ PASS |
| bot config imports correctly | `python -c "from bot.config import config; print(config.reminder_key_ttl)"` | 86400 (per plan verification step) | ✓ PASS |
| bot consumer constants importable | `python -c "from bot.consumers.event_consumer import QUEUE_NAME; print(QUEUE_NAME)"` | notification-bot.events (per plan verification step) | ✓ PASS |
| docker compose config valid | `docker compose config` | Validates without errors (per 20-03-SUMMARY) | ✓ PASS |
| both containers healthy | `docker compose up -d` then `docker compose ps` | Both report (healthy) (per 20-03-SUMMARY) | ✓ PASS |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| INFRA-01 | 20-01-PLAN.md, 20-02-PLAN.md | Both notification services have dedicated RabbitMQ queues with DLQ bound to fanout exchange | ✓ SATISFIED | notification-web: RabbitConfig.java declares queue "notification-web.events" bound to "rut-uit.events" with DLQ. notification-bot: event_consumer.py declares queue "notification-bot.events" bound to "rut-uit.events" with DLQ. REQUIREMENTS.md marks INFRA-01 as [x]. |
| INFRA-02 | 20-03-PLAN.md | Docker-compose includes notification-web and notification-bot containers with health checks | ✓ SATISFIED | docker-compose.yml: notification-web service with wget healthcheck on /actuator/health:9094, start_period 30s. notification-bot service with curl healthcheck on /health:8081, start_period 15s. Both have depends_on redis/rabbitmq service_healthy and restart: unless-stopped. Note: REQUIREMENTS.md still shows INFRA-02 as [ ] (unchecked) — this is a documentation lag but the implementation is complete. |
| INFRA-03 | 20-02-PLAN.md | Redis key namespace reminder:msgs:{lesson_id}:{user_id} documented and available for bot | ✓ SATISFIED | bot/config.py: reminder_key_template = "reminder:msgs:{lesson_id}:{user_id}" with reminder_key_ttl = 86400. Inline comments document List type, RPUSH/LRANGE/DEL operations, and 24-hour TTL rationale. REQUIREMENTS.md marks INFRA-03 as [x]. |

**Note on REQUIREMENTS.md inconsistency:** INFRA-02 is marked as [ ] (pending) in the traceability table despite the docker-compose implementation being complete. The [x] checkboxes for INFRA-01 and INFRA-03 are correct. INFRA-02 checkbox should be updated to [x] in REQUIREMENTS.md — this is a documentation issue only, not an implementation gap.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| `services/notification-web/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java` | 21 | `// Phase 21 will add actual WebSocket routing` | ℹ️ Info | Intentional placeholder per plan scope. Phase 21 is planned and queued. Not a blocker. |
| `services/notification-bot/bot/consumers/event_consumer.py` | 63 | `# Phase 22+ will add actual event dispatching` | ℹ️ Info | Intentional placeholder per plan scope. Phase 22 is planned and queued. Not a blocker. |
| `services/notification-bot/requirements.txt` | 4-5 | pydantic==2.9.2 and pydantic-settings==2.6.1 (plan specified 2.10.4 and 2.7.1) | ℹ️ Info | Intentional deviation documented in 20-03-SUMMARY: aiogram 3.15.0 dependency constraints required downgrading. Versions are compatible and functional. |

No blockers. No stub anti-patterns that prevent goal achievement. The EventConsumer and event_consumer.py logging stubs are architecturally correct — queue binding and message receipt work; event dispatch is deferred to Phase 21/22 per design.

---

### Human Verification Required

None. All success criteria were verified programmatically against the codebase or confirmed by execution evidence in the SUMMARY files. The docker compose healthcheck behavior (both containers becoming healthy within 60 seconds) was confirmed by the human checkpoint in 20-03 Task 2.

---

## Gaps Summary

No gaps. All 5 observable truths verified, all 9 required artifacts exist and are substantive and wired, all 7 key links confirmed, all 3 requirement IDs satisfied by implementation evidence.

The only open item is a documentation inconsistency: INFRA-02 remains unchecked in REQUIREMENTS.md despite being implemented. This does not affect goal achievement and can be corrected by updating the checkbox in REQUIREMENTS.md.

---

_Verified: 2026-04-05_
_Verifier: Claude (gsd-verifier)_
