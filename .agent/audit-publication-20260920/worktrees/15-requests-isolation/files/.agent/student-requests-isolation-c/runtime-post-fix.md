# Post-fix runtime evidence

Date: 2026-09-08. Worktree: assigned nested `requests-isolation` worktree.
The pre-edit imported baseline was `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

## Command and result

Command (PowerShell environment override was process-scoped):

```text
$env:TESTCONTAINERS_REUSE_ENABLE='false'; .\gradlew.bat :services:attendance-service:attendance-app:integrationTest --tests "*EventConsumerIT" --tests "*RabbitDecisionRetryIT" --no-parallel --max-workers=1 --console=plain
```

The first sandbox attempt exited `1` before test configuration because Gradle
could not create `.gradle/8.12/fileHashes/fileHashes.lock`. The authorized
escalated rerun used the exact command above, session `78933`, and exited `0`:
`BUILD SUCCESSFUL in 1m 29s`; `37 actionable tasks: 2 executed, 35
up-to-date`.

JUnit XML reports were copied immediately after completion to
`evidence/post-fix/`:

| Report | SHA256 | Tests | Failed | Errors | Skipped |
| --- | --- | ---: | ---: | ---: | ---: |
| `EventConsumerIT.xml` | `6D9338EB32BAB2C546B74459AF96F5F7E207FE6E38725A427C103E82D27D38FB` | 5 | 0 | 0 | 0 |
| `RabbitDecisionRetryIT.xml` | `B91811DE49E07DB89BE137B48D20361C898BD238688109969A53DEE579CD5104` | 2 | 0 | 0 | 0 |

The focused Gradle task also reported four always-on architecture/report XMLs:
`GrpcClientDeadlineTest`, `IntegrationTestNamingConventionIT`,
`ScheduledMustHaveSchedulerLockTest` and `ReportDomainIsolationTest`. Their
four tests were green, giving 6 XML files, 11 tests, 0 failures, 0 errors and
0 skipped. The target classes account for 7 tests.

## Runtime boundary evidence

Testcontainers runtime logs recorded Ryuk `1f511a25f1e0800e6cbc5d7ad32e9fb53cb0a3762df72a2d0e845378b14d679b`, Mongo
`a6140527d59f5fed03bad71f0665bbd3db1a4d43ce992daccd13ddae1618d61b`, Rabbit
`766a38d464472141744eebb934507339d53d6926966e38d87f5944551ab308b8` and Redis
`46f9b55755308c66bdc715f3af1a4761329e2f8383507a393ac6b237f4b44663`. Rabbit
mapped AMQP port was `58806`.

The two cached Spring contexts connected to separate vhosts on that same Rabbit
container and port:

```text
amqp://guest@127.0.0.1:58806/rct_requests_isolation_event_consumer
amqp://guest@127.0.0.1:58806/rct_requests_isolation_rabbit_decision_retry
```

The listener logs in each report show `consumerQueue=attendance-service.events`
inside its class-local vhost. The Rabbit report includes the transient
`simulated dependency outage` stack, retry exhaustion and reject-to-DLQ path;
the test's `verify(times(3))` and event-ID body assertions passed. The malformed
case passed its `x-death`, event-ID and event-type assertions. EventConsumerIT's
five Mongo cases passed with listener logs for the expected `lesson.closed` and
`lesson.cancelled` processing counts.

## Teardown and ownership

After session `78933`, `docker ps --no-trunc` exited `0` with no running
containers. The owned Testcontainers were stopped/removed by the non-reuse run;
the inventory still contained only unrelated historical stopped/created
containers, which were preserved. The lease was explicitly released to root.

The test's `getLabels()` output was `{}` and is not used as ownership proof.
Because teardown removed the containers before post-run inspection, Docker labels
and live container metadata were not independently observable afterward. No
foreign process, container, volume, queue or database was removed.
