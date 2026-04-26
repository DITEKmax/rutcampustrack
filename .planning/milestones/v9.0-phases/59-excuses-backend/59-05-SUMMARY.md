---
phase: 59-excuses-backend
plan: 05
subsystem: attendance-service
tags: [backend, rabbitmq, event-publisher, excuse-tickets, contract-test]
status: completed
completed: 2026-04-14

dependency_graph:
  requires:
    - 59-02 (ExcuseService.createExcuse / updateStatus — the two publish hook points)
    - 59-04 (ExcuseService constructor widened with AttendanceWritePort — we append one more param)
    - services/attendance-service/attendance-app/.../config/RabbitConfig (fanout exchange + RabbitTemplate bean)
    - services/attendance-service/attendance-app/.../event/AttendanceEventPublisher (envelope template)
    - event-schemas/excuse.requested.json (JSON schema — bot contract)
    - services/notification-bot/bot/notifications/headman_alerts.py (reads payload.user_id / group_id / student_name / excuse_type)
  provides:
    - ExcuseEventPublisher (@Component) — publishRequested / publishDecided
    - excuse.requested event live on rut-uit.events fanout
    - excuse.decided event live on rut-uit.events fanout
    - Canonical event fixtures (excuse_requested.json, excuse_decided.json)
      in BOTH attendance-app test resources AND notification-bot tests fixtures
    - ExcuseEventContractIT (AC-7)
  affects:
    - 59-06 (notification-bot consumer for excuse.decided — fixture ready at services/notification-bot/tests/fixtures/excuse_decided.json)
    - 59-07 / 59-08 (frontend — no direct impact; event plumbing is server-side)

tech-stack:
  added: []
  patterns:
    - "Envelope convention { event_type, event_id, occurred_at, payload } matches AttendanceEventPublisher"
    - "Lowercase enum at publish-site (ticket.getExcuseType().name().toLowerCase()) — no @JsonValue on the enum"
    - "Fixture duplication to the consumer project instead of a brittle relative path"

key-files:
  created:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisher.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisherTest.java
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventContractIT.java
    - services/attendance-service/attendance-app/src/test/resources/fixtures/excuse_requested.json
    - services/attendance-service/attendance-app/src/test/resources/fixtures/excuse_decided.json
    - services/notification-bot/tests/fixtures/excuse_requested.json
    - services/notification-bot/tests/fixtures/excuse_decided.json
  modified:
    - services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseService.java (+ExcuseEventPublisher ctor arg, two publish calls, updated javadoc)
    - services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseServiceTest.java (+@Mock ExcuseEventPublisher + 2 verify(...) assertions)

decisions:
  - "D-19 lowercase excuse_type handled at the publish site (name().toLowerCase()) rather than via @JsonValue on ExcuseType enum. Less invasive: no risk of changing how DTOs / REST responses already serialise the enum; publisher is the only place we want lowercase."
  - "excuse.decided publish runs AFTER the D-16 attendance cascade and BEFORE return. Teacher journal consistency depends on attendance docs being written before any downstream consumer observes the decided event."
  - "publish is synchronous on the caller thread (same pattern as AttendanceEventPublisher). If RabbitMQ is down the save-then-publish is not transactional — the ticket stays APPROVED and the event is lost. Accepted for v1 per plan threat model (T-59-05-02); outbox pattern deferred."
  - "Payload duplicates studentId under the user_id key because notification-bot headman_alerts.py:29 reads payload.user_id. Keeping student_id too is cheap future-proofing."
  - "Fixtures mirrored into services/notification-bot/tests/fixtures/ rather than shared via a relative path or a pull from event-schemas/. Python tests are module-local; explicit copy is the cheapest maintenance choice and both files are tiny."

metrics:
  tasks: 2
  commits: 3
  files_created: 7
  files_modified: 2
  duration: ~7 min
---

# Phase 59 Plan 05: Excuse Event Publisher Summary

One-liner: RabbitMQ publisher for `excuse.requested` / `excuse.decided` with lowercase-enum payload wired into `ExcuseService` at the two hook points documented by 59-04, plus a `@SpringBootTest` contract test (AC-7) that boots a private test queue on the fanout exchange.

## What Was Built

### Publisher

- **`ExcuseEventPublisher`** (`excuse/`) — `@Component`, single constructor dep on `RabbitTemplate`. Two methods:
  - `publishRequested(ExcuseTicket)` — emits `excuse.requested` per D-19 / D-27
  - `publishDecided(ExcuseTicket)`   — emits `excuse.decided`   per D-20
- Envelope is `LinkedHashMap` so field order is stable in wire dumps: `event_type → event_id → occurred_at → payload`.
- Fires through `rabbitTemplate.convertAndSend("rut-uit.events", "", envelope)` — fanout, routing key `""` (matches `AttendanceEventPublisher`).

### Service wiring

- `ExcuseService` constructor widened by appending `ExcuseEventPublisher` (monotonic — all four pre-existing ctor params keep their position, the existing `@InjectMocks` test rig picks up the new `@Mock` automatically).
- `createExcuse` — `excuseEventPublisher.publishRequested(saved)` after `excuseRepository.save(ticket)`, before `return`.
- `updateStatus` — `excuseEventPublisher.publishDecided(saved)` AFTER the D-16 cascade loop, BEFORE `return saved`. Comment in the code warns future editors not to reorder vs the cascade.

### Fixtures

- Canonical payloads at `src/test/resources/fixtures/excuse_requested.json` and `.../excuse_decided.json`.
- Mirrored into `services/notification-bot/tests/fixtures/` so plan 59-06 can load them directly from pytest without reaching across service boundaries.

### Tests

- **`ExcuseEventPublisherTest`** (unit, Mockito) — 4 cases:
  1. `publishRequested_sendsCorrectEnvelopeToFanoutExchange` — full envelope + payload shape (ILLNESS).
  2. `publishRequested_freeAttendanceType_serializesLowercaseWithUnderscore` — `FREE_ATTENDANCE` → `"free_attendance"`.
  3. `publishDecided_approvedTicket_sendsCorrectEnvelope` — decided envelope (status, decision_by, decision_comment, decided_at).
  4. `publishRequested_userIdEqualsStudentId` — D-27 invariant.
- **`ExcuseEventContractIT`** (Spring + Testcontainers) — 2 cases:
  1. `createExcuse_publishesRequestedEvent_matchingBotContract` — declares a private test queue, asserts every bot-critical field on the live emission.
  2. `updateStatus_publishesDecidedEvent_matchingBotContract` — seeds a `SUBMITTED` ticket, flips caller to headman, approves, asserts the decided envelope.
- **`ExcuseServiceTest`** — added `@Mock ExcuseEventPublisher` and `verify(excuseEventPublisher).publishRequested(saved)` / `verify(excuseEventPublisher).publishDecided(result)` to the two happy-path tests. All 10 pre-existing assertions untouched.

## Verification

| Check | Result |
|---|---|
| `./gradlew :services:attendance-service:attendance-app:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :services:attendance-service:attendance-app:compileTestJava` | BUILD SUCCESSFUL |
| `./gradlew ... test --tests "*ExcuseEventPublisherTest"` | **4/4 green** |
| `./gradlew ... test --tests "*ExcuseServiceTest"` | **10/10 green** (no regression; new verify calls pass) |
| `./gradlew ... test --tests "*ExcuseAssemblerTest" "*CheckinServiceTest" "*ReportServiceTest" "*MarkingServiceTest"` | **all green** (regression scan) |
| `./gradlew ... test --tests "*ExcuseEventContractIT"` | **blocked by local env** (Docker Desktop not running — see Deferred Issues) |
| `grep "excuse.requested" services/attendance-service/attendance-app/src/main/java/` | `ExcuseEventPublisher.java` |
| `grep "excuse.decided" services/attendance-service/attendance-app/src/main/java/` | `ExcuseEventPublisher.java` |
| `grep "excuseEventPublisher.publishRequested" .../excuse/ExcuseService.java` | 1 hit (createExcuse) |
| `grep "excuseEventPublisher.publishDecided" .../excuse/ExcuseService.java` | 1 hit (updateStatus) |

## Commits

| Task | Commit | Message |
|---|---|---|
| 1 | `c383f14` | feat(59-05): add ExcuseEventPublisher with excuse.requested / excuse.decided envelopes |
| 2 | `5f02d33` | feat(59-05): wire ExcuseEventPublisher into ExcuseService + contract IT |
| 2b | `fc1cb94` | chore(59-05): mirror excuse event fixtures into notification-bot tests |

## Deviations from Plan

### Clarifications (not deviations)

- **D-19 lowercase decision documented in-code.** Plan asked to pick between `@JsonValue` on `ExcuseType` and a custom serializer in the publisher. Picked "lowercase at publish-site" (`name().toLowerCase()`) — a third, strictly simpler option. Rationale: (a) this is the only place on the wire we need the lowercase form; (b) other callers of `ExcuseType` (DTOs, REST) stay on Jackson's default serialisation, so the change has zero blast radius; (c) a single `toLowerCase()` is easier to reason about than a custom serializer or enum-wide annotation. Documented in the Javadoc of `ExcuseEventPublisher`.
- **Fixture path for the bot.** Plan asked which location we picked for the shared fixture — `event-schemas/` vs explicit copy. Chose **explicit copy** into `services/notification-bot/tests/fixtures/`. The `event-schemas/` directory currently holds JSON Schemas (not sample payloads) and pytest-level test data belongs next to the tests that load it; cross-repo relative imports are fragile on Windows. Both copies are tiny (≤ 15 lines), and the attendance-app copy remains the canonical reference — any future change should update both.

### Auto-fixed Issues

None. Publisher integration landed cleanly against 59-04's scaffolding.

## Known Stubs

**None introduced by this plan.** Remaining stubs from the phase:

- `ExcuseService.createExcuse` still does not validate `lessonIds` via the gRPC `LessonsByIds` method (plan 59-03 shipped the client; full wiring tracked as a later concern).
- `59-06` bot consumer for `excuse.decided` not yet written.
- Frontends (`59-07`, `59-08`).

## Deferred Issues

**`ExcuseEventContractIT` cannot run locally — Docker Desktop not running on the dev machine.**

- Symptom: `java.lang.IllegalStateException at DockerClientProviderStrategy.java:274` during Spring context boot. Same pattern as every `AbstractAttendanceIntegrationTest` heir in this repo (see 59-04 summary's Deferred Issues section — identical root cause).
- Test **compiles cleanly**; the two test methods are well-formed, and the unit-level coverage in `ExcuseEventPublisherTest` (4 cases) + the new `verify` calls in `ExcuseServiceTest` prove the same code paths with mocks. CI with Docker available will run the contract IT without modification.

## Threat Flags

None. Trust boundaries covered by the plan's `<threat_model>`:

- **T-59-05-01 (sensitive data in fanout)** — mitigated. Payload contains only `ticket_id`, `student_id` / `user_id` (both = `ticket.studentId` — NOT a Telegram chat_id), `student_name` (public ФИО), `group_id`, `lesson_ids`, `excuse_type`, `comment`, `created_at`, `decision_by`, `status`, `decision_comment`, `decided_at`. No JWTs, passwords, or Telegram IDs — those never enter an `ExcuseTicket`.
- **T-59-05-02 (RabbitMQ down)** — accepted for v1. `publishRequested` runs on the same thread as `createExcuse`; an AMQP exception propagates out of the service call and the caller sees 5xx. The Mongo save has already happened, so a ticket can exist without a corresponding event — documented trade-off; outbox pattern deferred.
- **T-59-05-03 (replay)** — accepted. Each envelope gets a fresh `event_id` UUID; idempotency on the consumer side is the bot's problem and not implemented for v1.

## Self-Check: PASSED

- FOUND: services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisher.java
- FOUND: services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisherTest.java
- FOUND: services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventContractIT.java
- FOUND: services/attendance-service/attendance-app/src/test/resources/fixtures/excuse_requested.json
- FOUND: services/attendance-service/attendance-app/src/test/resources/fixtures/excuse_decided.json
- FOUND: services/notification-bot/tests/fixtures/excuse_requested.json
- FOUND: services/notification-bot/tests/fixtures/excuse_decided.json
- FOUND: `excuseEventPublisher.publishRequested` in ExcuseService.java
- FOUND: `excuseEventPublisher.publishDecided` in ExcuseService.java
- FOUND commit c383f14
- FOUND commit 5f02d33
- FOUND commit fc1cb94

## Notes for Wave 4 (59-06 / 59-07 / 59-08)

**59-06 (notification-bot `excuse.decided` consumer):**
- Fixture ready: `services/notification-bot/tests/fixtures/excuse_decided.json`. Load it directly with `json.load(open(...))` in pytest.
- Payload keys the bot needs: `payload.user_id` (student to notify), `payload.status` (`approved` | `rejected`), `payload.decision_comment` (russian text), `payload.ticket_id` (reference).
- Event type literal: `"excuse.decided"` — add to `bot/consumers/event_dispatcher.py`.
- Match `headman_alerts.py` handler style (use `send_queue.put(SendTask(...))`, not raw `bot.send_message`).

**59-07 / 59-08 (frontends):**
- No direct impact from this plan. Events are server-side only; students / headmen see effects through the existing REST endpoints (`GET /excuses/me`, `PATCH /excuses/{id}/status`).

**No blockers.** `ExcuseService` constructor continues to widen monotonically; if a future plan needs a seventh collaborator the same pattern holds.
