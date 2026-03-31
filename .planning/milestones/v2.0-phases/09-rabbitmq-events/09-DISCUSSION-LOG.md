# Phase 9: RabbitMQ Events - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md -- this log preserves the alternatives considered.

**Date:** 2026-03-31
**Phase:** 09-rabbitmq-events
**Areas discussed:** Event schemas, Publishing wiring, Test strategy, Exchange config

---

## Event Schemas

### Payload Density

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal IDs | Just entity ID + timestamp + action context. Consumers call gRPC for full data. | ✓ |
| Rich snapshot | Full entity fields in payload. No callback needed but events grow large. | |
| Contextual minimal | IDs plus specific change context (e.g., change_type, user_id). | |

**User's choice:** Minimal IDs (Recommended)
**Notes:** Keeps events small, avoids stale snapshots. Consumers use gRPC for full data.

### Schema Files

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, create all | Create JSON Schema files following homework.published.json pattern. | ✓ |
| Skip schemas, use code only | Define event structure in Java classes only. | |

**User's choice:** Yes, create all (Recommended)
**Notes:** None

---

## Publishing Wiring

### Pipeline Structure

| Option | Description | Selected |
|--------|-------------|----------|
| Spring Events bridge | Service publishes ApplicationEvent, @TransactionalEventListener forwards to RabbitTemplate. | ✓ |
| Direct RabbitTemplate | Thin wrapper calling RabbitTemplate directly from services. | |
| AbstractAggregateRoot | Entities register events, auto-published on save(). | |

**User's choice:** Spring Events bridge (Recommended)
**Notes:** Clean separation -- services don't know about RabbitMQ, only domain events.

### Event Package Location

| Option | Description | Selected |
|--------|-------------|----------|
| academic-app event package | New ru.rutcampustrack.academic.event package with all event code. | ✓ |
| Split across domains | Event classes co-located with their domain packages. | |

**User's choice:** academic-app event package (Recommended)
**Notes:** None

---

## Test Strategy

### Testing Approach

| Option | Description | Selected |
|--------|-------------|----------|
| Testcontainers RabbitMQ | Real RabbitMQ via Testcontainers. Bind temp queue, assert messages. | ✓ |
| Mock RabbitTemplate | @MockBean on RabbitTemplate. Verify calls. Faster but no real serialization test. | |
| Both layers | Unit test listener + integration test for happy path. | |

**User's choice:** Testcontainers RabbitMQ (Recommended)
**Notes:** Consistent with Phase 8's Testcontainers Redis approach.

### Base Class

| Option | Description | Selected |
|--------|-------------|----------|
| New base class | AbstractAcademicEventIntegrationTest with PostgreSQL + RabbitMQ. No Redis. | ✓ |
| Extend cache base | Reuse AbstractAcademicCacheIntegrationTest, add RabbitMQ on top. | |

**User's choice:** New base class (Recommended)
**Notes:** Keeps container startup fast and concerns separated.

---

## Exchange Config

### Exchange and Format

| Option | Description | Selected |
|--------|-------------|----------|
| Durable fanout + JSON | Durable fanout exchange, Jackson2JsonMessageConverter. | ✓ |
| Non-durable + JSON | Non-durable exchange, messages lost on restart. | |
| Durable fanout + plain string | Manual ObjectMapper.writeValueAsString(). | |

**User's choice:** Durable fanout + JSON (Recommended)
**Notes:** None

### ObjectMapper

| Option | Description | Selected |
|--------|-------------|----------|
| Shared app ObjectMapper | Inject Spring-managed ObjectMapper (already has JavaTimeModule). | ✓ |
| Dedicated event ObjectMapper | Separate ObjectMapper for events only. | |

**User's choice:** Shared app ObjectMapper (Recommended)
**Notes:** No duplicate config needed.

---

## Claude's Discretion

- Base event class design (abstract class vs interface vs record)
- Exact field naming in event payload classes
- Shared DomainEventEnvelope wrapper vs individual message classes
- RabbitTemplate error handling strategy

## Deferred Ideas

None -- discussion stayed within phase scope
