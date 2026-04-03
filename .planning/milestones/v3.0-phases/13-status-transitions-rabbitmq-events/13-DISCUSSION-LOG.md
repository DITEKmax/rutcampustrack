# Phase 13: Status Transitions + RabbitMQ Events - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-02
**Phase:** 13-status-transitions-rabbitmq-events
**Areas discussed:** Cron timing & batch strategy, Missed transitions recovery, Event publishing scope, Timezone handling

---

## Cron timing & batch strategy

### Q1: How should the cron handle transitions within a single tick?

| Option | Description | Selected |
|--------|-------------|----------|
| Single method, two queries | One @Scheduled method runs every minute. First: planned->active. Second: active->closed. Both in one transaction. | ✓ |
| Two separate cron methods | Separate @Scheduled methods for activate and close, each with own schedule. | |
| You decide | Claude picks the approach based on codebase patterns | |

**User's choice:** Single method, two queries (Recommended)

### Q2: Should cron use bulk UPDATE queries or load entities?

| Option | Description | Selected |
|--------|-------------|----------|
| Load entities + save | Fetch lessons by criteria, set status in Java, saveAll(). Needed for ApplicationEvent publishing per lesson. | ✓ |
| Bulk UPDATE + separate event query | Native SQL UPDATE, then SELECT affected IDs to publish events. Faster but two queries. | |
| You decide | Claude picks based on data volume and event requirements | |

**User's choice:** Load entities + save (Recommended)

### Q3: If a cron run takes longer than 1 minute, should the next tick overlap?

| Option | Description | Selected |
|--------|-------------|----------|
| fixedDelay, not cron | @Scheduled(fixedDelay=60000) — waits 60s AFTER previous run finishes. No overlap. | ✓ |
| Cron + @SchedulerLock | Cron expression + ShedLock for multi-instance. More complex. | |
| You decide | Claude picks based on deployment model | |

**User's choice:** fixedDelay, not cron (Recommended)

---

## Missed transitions recovery

### Q1: How should the service catch up on missed transitions after downtime?

| Option | Description | Selected |
|--------|-------------|----------|
| Same cron handles it naturally | Cron query is time-based (<= now), so after restart ALL past-due lessons match. No separate job. | ✓ |
| Separate startup catch-up job | @PostConstruct or ApplicationReadyEvent listener runs dedicated catch-up scan. | |
| You decide | Claude picks simplest approach for CRON-03 | |

**User's choice:** Same cron handles it naturally (Recommended)

### Q2: Should there be a lookback limit?

| Option | Description | Selected |
|--------|-------------|----------|
| No limit — transition everything past-due | Any lesson with incorrect status gets caught up regardless of age. | ✓ |
| Limit to last 24 hours | Only catch up lessons from last 24h. Older ones stay in incorrect state. | |
| You decide | Claude picks based on expected scale | |

**User's choice:** No limit (Recommended)

---

## Event publishing scope

### Q1: Where should lesson.cancelled event be published?

| Option | Description | Selected |
|--------|-------------|----------|
| Publish in LessonService.cancel() | User-initiated action, publish where it happens. Same pattern as academic-service. | ✓ |
| Publish in cron only | Deferred to next cron tick. Adds latency. | |
| You decide | Claude follows existing patterns | |

**User's choice:** Publish in LessonService.cancel() (Recommended)

### Q2: Same exchange or separate?

| Option | Description | Selected |
|--------|-------------|----------|
| Same exchange: rut-uit.events | All services publish to one fanout exchange. Single topology. | ✓ |
| Separate exchange: rut-uit.schedule.events | Dedicated exchange per service. More isolation. | |
| You decide | Claude picks based on architecture docs | |

**User's choice:** Same exchange: rut-uit.events (Recommended)

### Q3: Full payload or minimal?

| Option | Description | Selected |
|--------|-------------|----------|
| Full payload per schema | Include all fields from event-schemas/*.json. Fat event pattern. | ✓ |
| Minimal payload + consumer queries back | Only lesson_id and group_id. Consumers call gRPC for details. | |
| You decide | Claude follows event-schemas as-is | |

**User's choice:** Full payload per schema (Recommended)

---

## Timezone handling

### Q1: How to build a comparable timestamp for cron?

| Option | Description | Selected |
|--------|-------------|----------|
| Combine date + time in Moscow TZ | ZonedDateTime.of(lesson.date, scheduleItem.startTime, ZoneId.of("Europe/Moscow")). Compare with ZonedDateTime.now(clock). | ✓ |
| Convert to Instant and compare | Build ZonedDateTime, convert to Instant. Same result, extra step. | |
| You decide | Claude picks cleanest approach | |

**User's choice:** Combine date + time in Moscow TZ (Recommended)

### Q2: How should the Clock be provided for testability?

| Option | Description | Selected |
|--------|-------------|----------|
| ClockConfig bean with Moscow zone | @Bean Clock.system(ZoneId.of("Europe/Moscow")). Tests inject Clock.fixed(). | ✓ |
| ZonedDateTime.now(zone) directly | No Clock bean. Tests mock static methods. | |
| You decide | Claude picks based on existing config | |

**User's choice:** ClockConfig bean with Moscow zone (Recommended)

---

## Claude's Discretion

- Package placement for LessonStatusTransitionJob
- DomainEvent subclass design
- RabbitConfig for schedule-service
- Repository query design (JPQL vs native SQL)
- Integration test strategy for cron
- Logging and closedAt population

## Deferred Ideas

None — discussion stayed within phase scope
