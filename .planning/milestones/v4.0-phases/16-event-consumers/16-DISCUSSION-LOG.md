# Phase 16: Event Consumers - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md -- this log preserves the alternatives considered.

**Date:** 2026-04-04
**Phase:** 16-event-consumers
**Areas discussed:** Auto-absent strategy, Cancellation semantics, Error handling & retry, lesson.started handling

---

## Auto-absent Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| gRPC GetLessonById | Call Schedule gRPC to get full lesson details. Event schema stays minimal. One extra gRPC call per lesson.closed event. | ✓ |
| Extend event payload | Add lesson_number, lesson_date to lesson.closed schema. Zero gRPC calls but requires Schedule Service changes. | |

**User's choice:** gRPC GetLessonById
**Notes:** Keeps event schema minimal, acceptable overhead for once-per-lesson event.

| Option | Description | Selected |
|--------|-------------|----------|
| bulkWrite with $setOnInsert | Single bulkWrite with UpdateOneModel per student, upsert=true, $setOnInsert. Atomic batch, race-safe, one round-trip. | ✓ |
| Individual upserts in loop | Loop over students, one mongoTemplate.upsert() per student. Simpler code but N round-trips. | |

**User's choice:** bulkWrite with $setOnInsert
**Notes:** Matches MARK-04 requirement exactly. Single round-trip preferred.

---

## Cancellation Semantics

| Option | Description | Selected |
|--------|-------------|----------|
| Update existing only | Only update docs that exist to status=cancelled. Students with no record have no record. Matches MARK-05 wording. | ✓ |
| Insert cancelled for all | Call GetGroupMembers, insert status=cancelled for every student. Creates explicit cancelled records. | |

**User's choice:** Update existing only
**Notes:** Matches MARK-05 exactly: "updates existing attendance documents".

| Option | Description | Selected |
|--------|-------------|----------|
| Overwrite to cancelled | lesson.cancelled updateMany sets status=cancelled regardless of current status. | ✓ |
| Skip already-processed | Only update docs still in original state. More complex, unclear benefit. | |

**User's choice:** Overwrite to cancelled
**Notes:** Cancelled lessons excluded from all statistics, so overwriting is correct.

---

## Error Handling & Retry

| Option | Description | Selected |
|--------|-------------|----------|
| Throw exception -> DLQ | Let exception propagate, Spring AMQP nacks, RabbitMQ routes to DLQ. Simple, reliable. | ✓ |
| Retry 3x with backoff | RetryTemplate with exponential backoff (1s, 2s, 4s) then DLQ. Handles transient failures. | |
| Nack with requeue | Reject and requeue for RabbitMQ to redeliver. Risk of infinite retry loop. | |

**User's choice:** Throw exception -> DLQ
**Notes:** Auto-absent is not time-critical (lesson already closed). Manual intervention via DLQ dashboard.

| Option | Description | Selected |
|--------|-------------|----------|
| Naturally idempotent | $setOnInsert + updateMany are inherently safe to replay. No extra tracking needed. | ✓ |
| event_id dedup tracking | Store processed event_ids in MongoDB. Skip if already seen. | |

**User's choice:** Naturally idempotent
**Notes:** All operations are inherently idempotent. Safe to replay from DLQ without dedup.

---

## lesson.started Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Log only, no-op | Keep stub with debug log. Attendance Service doesn't act on lesson.started. | ✓ |
| Remove stub entirely | Delete the lesson.started case from switch. Cleaner but may need re-adding. | |
| Pre-create empty docs | Create placeholder docs when lesson starts. Complicates $setOnInsert pattern. | |

**User's choice:** Log only, no-op
**Notes:** Notification services handle start reminders. Keep stub for future extensibility.

---

## Claude's Discretion

- Whether to extract event handler logic into a separate service class or keep in EventConsumer
- MongoTemplate vs. BulkOperations API for the bulkWrite
- Exact error logging format and DLQ message enrichment
- Test structure and scenario coverage

## Deferred Ideas

None -- discussion stayed within phase scope.
