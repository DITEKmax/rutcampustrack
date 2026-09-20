# Evidence index

Scope: S3 bounded test isolation. Baseline/import revision:
`d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

Frozen source manifest `.agent/student-role-02/diff.json` has SHA256
`4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4`.
The pre-edit source hash guard reported `source_entries=82 source_bad=0`; the
pre-edit destination guard reported `destination_entries=82 destination_bad=0`.
Exactly the 82 non-`.agent` product paths were copied; nine evidence paths were
excluded. Details are in `import-manifest.json`. The two owned IT files are
expected to differ from that import after the bounded correction; the original
import guard remains recorded as a baseline integrity check.

The immutable pre-fix JUnit XMLs are under `evidence/pre-fix/`; the post-fix
reports copied before later inspection are under `evidence/post-fix/`. Their
SHA256 values are recorded in `runtime-post-fix.md` and `checks.json`.

The pre-fix combined runtime exited `1` with 11 tests and one
`RabbitDecisionRetryIT` failure at line 93. Its stack shows the intended
decision envelope reaching the other cached listener context and real
`StudentRequestService`, ending in `ResourceNotFoundException`; the Rabbit
context mock had zero calls. The correction gives each owned test class a
task-unique Rabbit vhost while preserving the production queue/exchange/DLQ
literals and listener retry policy. Both inherited static fixtures still use the
same Testcontainers Rabbit process, but their Spring AMQP connections and
listeners are in separate vhosts.

Post-fix runtime evidence is PASS: 6 JUnit XML files contain 11 tests,
0 failures, 0 errors and 0 skipped. The target classes contain 7 tests
(`EventConsumerIT` 5, `RabbitDecisionRetryIT` 2); four always-on architecture /
report checks account for the remaining four. The Rabbit report records the
same container ID and mapped AMQP port with distinct connections:

- `amqp://guest@127.0.0.1:58806/rct_requests_isolation_event_consumer`;
- `amqp://guest@127.0.0.1:58806/rct_requests_isolation_rabbit_decision_retry`.

Both class logs show `consumerQueue=attendance-service.events` on their own
vhost. The transient case records the configured `simulated dependency outage`
stack and passed `verify(times(3))`; both DLQ assertions passed their generated
event-ID/body checks. The malformed case also passed its `x-death` assertion.
EventConsumerIT passed all five lesson closed/cancelled Mongo cases, with its
listener logs showing the expected processed/updated counts.

Source locations supporting the boundary explanation and correction:

- `integration/AbstractAttendanceIntegrationTest.java:37,93-108`: one static
  reusable fixture and dynamic shared broker/database properties;
- `event/EventConsumer.java:42`: literal shared
  `attendance-service.events` listener;
- `integration/RabbitDecisionRetryIT.java:31-46,55-108,117-139`: DLQ literal,
  unique vhost registration, runtime identity assertions, cleanup and retry/DLQ
  assertions;
- `integration/EventConsumerIT.java:36-68`: separate vhost registration,
  runtime identity assertions and queue cleanup.

No production code, generated contract, config, lockfile or shared fixture was
changed for this isolation scope. The full repository remains intentionally
dirty with foreign product work imported from the frozen source; `scope-diff.md`
lists the two owned product files and the `.agent` evidence only.

Known limitations: Testcontainers teardown removes the owned containers before
post-run Docker inspection, so Docker labels were not observable afterward;
`getLabels()` in the test log is `{}` and is not treated as ownership proof.
The post-fix report proves the vhost connection and queue boundary during the
run. Full Requests transport acceptance and production deployment are outside
this scope.
