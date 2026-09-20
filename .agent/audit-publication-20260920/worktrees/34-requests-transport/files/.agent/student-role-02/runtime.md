# Runtime evidence checkpoint

The only product runtime exercised for this scope is the task-owned
Testcontainers Rabbit integration fixture. No persistent service, gateway or
fixed port was started by this handoff.

## Rabbit decision transport

Command (worktree root):

```text
.\gradlew.bat :services:attendance-service:attendance-app:integrationTest --tests "*RabbitDecisionRetryIT" --rerun-tasks --console=plain
```

Exit code: `0`. The test report contains 2 tests with 0 failures and 0 errors.
The invalid trusted envelope is rejected to the configured DLQ. The transient
decision authority failure invokes the listener three times under the bounded
100 ms initial / 500 ms capped backoff and then reaches the DLQ. Testcontainers
uses dynamic task-owned resources and shuts them down with the test context.

The neighboring combined command was also run:

```text
.\gradlew.bat :services:attendance-service:attendance-app:integrationTest --tests "*EventConsumerIT" --tests "*RabbitDecisionRetryIT" --rerun-tasks --console=plain
```

Exit code: `1`; 11 tests ran and
`RabbitDecisionRetryIT.transientDecisionFailure_retriesThreeTimesThenDeadLetters`
failed at line 93 with `WantedButNotInvoked`. The two classes pass in isolated
runs. This unresolved cross-class fixture contamination is recorded in defect
gate item 10 and is intentionally left for root's next bounded continuation.

## Runtime not applicable / open

Product HTTP-to-gRPC, Gateway/XFF and exact ingress byte-limit runtime are not
claimed here. They require the root-owned dependency/config baseline. Python
pytest/ruff runtime checks were unavailable: `python -m pytest --version`
returned exit `1` (`No module named pytest`), and `ruff --version` returned exit
`1` (command not found).
