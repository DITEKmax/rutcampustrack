# Pre-fix runtime evidence

Date: 2026-09-08; revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`;
worktree: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-isolation`.

## Reproduction

Command (scoped environment override; no persistent Testcontainers config
change):

```text
$env:TESTCONTAINERS_REUSE_ENABLE='false'
.\gradlew.bat :services:attendance-service:attendance-app:integrationTest --tests "*EventConsumerIT" --tests "*RabbitDecisionRetryIT" --no-parallel --max-workers=1 --console=plain
```

The first sandbox attempt exited `1` before test configuration because Gradle
could not create `.gradle/8.12/fileHashes`; this is recorded as infrastructure
failure. The same exact command was then run with required command-scoped
escalation. It exited `1` after 2m23s: `11 tests completed, 1 failed`.

Failed class/test: `RabbitDecisionRetryIT.transientDecisionFailure_retriesThreeTimesThenDeadLetters()`;
source assertion `RabbitDecisionRetryIT.java:93`; failure type
`WantedButNotInvoked`, zero interactions with the `StudentRequestService` mock.

Immutable reports copied before any later check:

- `evidence/pre-fix/EventConsumerIT.xml`, SHA256
  `28A2BE6E6F26B035E2BDC8075CBE673057D08FD301B3FEAA3CC069290F21291C`;
- `evidence/pre-fix/RabbitDecisionRetryIT.xml`, SHA256
  `9A3B7BBF91EFE257D6BC189153BFA2E2EF198E1A164257375B5939CC277343C8`.

The Rabbit XML reports two tests with one failure and includes the listener
stack. It shows `ResourceNotFoundException: LateCheckinRequest с id=507f1f77bcf86cd799439011 не найден`
from `StudentRequestService.decideLateCheckinFromBot`, followed by the normal
three-attempt retry and reject-to-DLQ path. The configured test mock therefore
did not receive the message; the test only observed the shared DLQ.

## Runtime ownership / lifecycle

Before and after the run, `docker ps --no-trunc` returned no running
containers. `docker ps -a` contained only older unrelated stopped containers;
no Testcontainers container remained after the run. The command-scoped
`TESTCONTAINERS_REUSE_ENABLE=false` was used and no global properties were
read or edited. Docker emitted a permission warning for the user config and a
daemon access warning for a network inventory attempt; no Docker state was
changed. Container IDs/labels/ports were unavailable after teardown and are a
known evidence limitation for this initial run.

## Cause evidence

`AbstractAttendanceIntegrationTest` starts static reusable Mongo/Rabbit/Redis
containers and every `EventConsumer` has a literal
`@RabbitListener(queues = "attendance-service.events")`. The combined JVM
caches the `EventConsumerIT` context and then loads a distinct
`RabbitDecisionRetryIT` context because the latter adds
`@MockitoBean StudentRequestService`. Both listener containers consume the same
fixed queue. The failed message was consumed by the earlier context, whose
listener held the real service, while the Rabbit test verified a mock belonging
to the later context. This explains both the ResourceNotFoundException in the
XML and the zero mock interactions at line 93.

## Planned bounded correction

Keep `EventConsumerIT` on the application default queue for its fanout tests.
In `RabbitDecisionRetryIT` only, move the application listener container to a
task-unique queue with its own DLQ before the first publish, declare the matching
dead-letter arguments, and publish directly to that queue's default-exchange
routing key. This gives the Rabbit test one active consumer/context boundary
without touching production listener code, retry policy, assertions, or the
shared fixture base. The test will assert its container queue identity and
matching event ID; a focused combined rerun is required after root grants the
next runtime window.
