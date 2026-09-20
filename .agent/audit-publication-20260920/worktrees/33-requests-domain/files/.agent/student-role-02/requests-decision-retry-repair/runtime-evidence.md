# Runtime evidence

The accepted runtime is the real Mongo replica-set integration task, not a
mock or in-memory outbox. `StudentRequestDomainIT` starts a unique
Testcontainers `mongo:7.0` replica set (`setName='docker-rs'`, observed
topology `REPLICA_SET_PRIMARY`) and uses the production
`MongoTransactionManager` and `MongoOutboxStorage` wiring. Its task-owned
outbox collection is queried after the decision futures complete.

Fresh XML evidence:

- timestamp: `2026-09-07T14:28:23`
- suite: `StudentRequestDomainIT`
- result: `tests=19`, `skipped=0`, `failures=0`, `errors=0`
- observed container image: `mongo:7.0`
- observed Mongo topology: `REPLICA_SET_PRIMARY`, `setName='docker-rs'`

The relevant runtime cases include both post-commit ambiguity recoveries,
pre-transaction transient retry, terminal mismatch protection, one terminal
event per decision, attendance `EXCUSED`/`PRESENT` assertions, and persisted
event-schema validation. The same run retained the prior domain invariants:
PRESENT priority, cancellation/decision race serialization, budget and
attachment scenarios, and durable outbox rollback.

Testcontainers removed its task container. The post-run Docker filter returned
no rows. Two Java processes were visible in a later host process read, but
ownership could not be established; they were not stopped so concurrent work
was not disturbed. No shared container, volume, port or existing data was
cleaned.
