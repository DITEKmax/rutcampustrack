# Phase 14: gRPC Server - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-03
**Phase:** 14-grpc-server
**Areas discussed:** GetActiveLesson semantics, Error handling strategy, Server starter dependency

---

## GetActiveLesson Semantics

### No active lesson response

| Option | Description | Selected |
|--------|-------------|----------|
| Return NOT_FOUND status | gRPC NOT_FOUND with description — caller handles absence explicitly. Matches academic-service pattern. | ✓ |
| Return empty LessonResponse | Default proto message with all zeroes/empty strings. Caller checks id==0. | |

**User's choice:** Return NOT_FOUND status (Recommended)
**Notes:** None

### Overlapping lessons

| Option | Description | Selected |
|--------|-------------|----------|
| Return first by lesson_number | ORDER BY lesson_number ASC LIMIT 1 — deterministic, simple. Overlaps are rare. | ✓ |
| Return NOT_FOUND / error | Treat overlap as data error, force resolution. | |
| Return any (non-deterministic) | Just pick one — overlaps shouldn't happen. | |

**User's choice:** Return first by lesson_number (Recommended)
**Notes:** None

---

## Error Handling Strategy

### Error handling structure

| Option | Description | Selected |
|--------|-------------|----------|
| Port GrpcExceptionAdvice | Copy academic-service pattern: NOT_FOUND, INVALID_ARGUMENT, INTERNAL. | ✓ |
| Inline error handling | Handle errors directly in each RPC method with try/catch. | |

**User's choice:** Port GrpcExceptionAdvice (Recommended)
**Notes:** None

### Invalid date range validation

| Option | Description | Selected |
|--------|-------------|----------|
| INVALID_ARGUMENT | Return gRPC INVALID_ARGUMENT. Validate before querying. | ✓ |
| Return empty list | Silently return no results for impossible ranges. | |

**User's choice:** INVALID_ARGUMENT (Recommended)
**Notes:** None

---

## Server Starter Dependency

### Dual client+server setup

| Option | Description | Selected |
|--------|-------------|----------|
| Just add server starter | Add grpc-server-spring-boot-starter alongside existing client. Both coexist fine. | ✓ |
| Replace with combined starter | Use grpc-spring-boot-starter that includes both client and server. | |

**User's choice:** Just add server starter (Recommended)
**Notes:** None

### Testing approach

| Option | Description | Selected |
|--------|-------------|----------|
| Direct service call | Test impl methods directly with mock StreamObserver. Same pattern as academic-service. | ✓ |
| In-process gRPC channel | Start gRPC server in test, connect via in-process channel. Full stack test. | |

**User's choice:** Direct service call (Recommended)
**Notes:** None

---

## Claude's Discretion

- ScheduleReadService vs direct repository access
- Repository query design for GetActiveLesson
- Repository query for GetLessonsByGroup (JOIN + date range filter)
- Timestamp parsing strategy
- Test data helpers

## Deferred Ideas

None
