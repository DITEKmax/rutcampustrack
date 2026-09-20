# Scope diff and limits

Base revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached nested
worktree, intentionally dirty from the frozen product import).

The only product paths owned and changed for this task are:

1. `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java`
2. `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/RabbitDecisionRetryIT.java`

Against the assigned git baseline, the first file is a 45-line insertion and
the second is a 139-line new file after restoring its fixed
`attendance-service.events.dlq` constant. Relative to the old released source
baseline, the actual repair delta is `EventConsumerIT +39/-0` and
`RabbitDecisionRetryIT +40/-1`; both bases are kept separate in
`repair-manifest.json`. Current file hashes are:

```text
EventConsumerIT.java       1FE05ED7FA57A2DC3CFA1EA77635B414D94B986D7DFFEAFC78EA544D3485FF32
RabbitDecisionRetryIT.java 95BFF3CE808623BFCD370499410A33BFC3C38465AF9097FE3016F80D49E51EDA
```

The implementation adds class-unique vhosts, `rabbitmqctl` setup and
`@DynamicPropertySource` overrides, plus runtime identity assertions and
per-vhost queue/DLQ cleanup. It does not change the production listener,
queue/exchange/DLQ names, converter, retry count, backoff, event payloads or
assertions. The `.agent/student-requests-isolation-c/` directory contains the
contract, import manifest, pre/post-fix evidence, checks and this summary.

The checkout also contains foreign product modifications imported from the
frozen source. They remain untouched and are intentionally excluded from this
scope diff. No commit, reset, cleanup or merge was performed.

Limits: this proves the combined two-class integration boundary and the four
always-on checks selected by the Gradle task. It does not claim full Requests
transport acceptance, production deployment, migration, or independent Docker
label visibility after teardown.
