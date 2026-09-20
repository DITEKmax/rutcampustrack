# Bounded correction decision

Date: 2026-09-08. Owner: root. Scope: only the two owned integration-test
classes in this worktree.

The fresh pre-fix reproduction showed two cached Spring contexts consuming the
same fixed Rabbit queue from one static broker. The later context's
`StudentRequestService` mock therefore did not own the decision message. Root
accepted a vhost boundary as the smallest test-only correction: each owned test
class registers a unique Rabbit vhost after the inherited static broker starts,
then overrides only `spring.rabbitmq.virtual-host` through its own
`@DynamicPropertySource`. The existing queue, exchange, routing key, DLQ,
listener annotation, retry policy, and assertions remain unchanged.

Both classes set vhost permissions through `rabbitmqctl` before their Spring
context is created, purge their own fixed queue/DLQ in `@BeforeEach`, and log the
container ID, mapped AMQP port and vhost. This keeps the class-local listener
and mock in the same Rabbit namespace while retaining the production topology.

The planned queue-renaming approach in the pre-fix note was superseded by this
root decision before the post-fix run. No production or shared-fixture change
was authorized.
