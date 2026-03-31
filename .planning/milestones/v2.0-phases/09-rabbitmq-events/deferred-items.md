# Deferred Items - Phase 09 RabbitMQ Events

## Pre-existing test failures from Plan 01 (out of scope for Plan 02)

**Issue:** When Plan 01 added `DomainEventListener` (requires `ConnectionFactory`/`RabbitTemplate`),
the existing integration test base classes (`AbstractAcademicIntegrationTest`) that exclude
`RabbitAutoConfiguration` now fail to load their Spring context. This breaks:

- `EntityMappingIntegrationTest` (extends AbstractAcademicIntegrationTest)
- `RestApiIntegrationTest` (extends AbstractAcademicIntegrationTest)
- `AcademicGrpcIntegrationTest` (extends AbstractAcademicIntegrationTest)
- `CacheIntegrationTest` (extends AbstractAcademicCacheIntegrationTest -- excludes RabbitMQ)

**Root cause:** `DomainEventListener` has `@Component` and injects `RabbitTemplate` via constructor.
When `RabbitAutoConfiguration` is excluded, no `ConnectionFactory` bean is created, and
`RabbitConfig.rabbitTemplate()` fails with `UnsatisfiedDependencyException`.

**Fix needed:** Add `@ConditionalOnBean(ConnectionFactory.class)` to `RabbitConfig` and
`DomainEventListener`, OR add a mock `ConnectionFactory` bean for non-AMQP test contexts,
OR restructure test base classes to conditionally exclude RabbitMQ.

**Recommended fix:** Add `@ConditionalOnBean(ConnectionFactory.class)` to `DomainEventListener`
so it doesn't try to register when `ConnectionFactory` is not available (i.e., when
`RabbitAutoConfiguration` is excluded).

**Discovered during:** Plan 02 verification (full test suite run)
**Scope:** Pre-existing failure from Plan 01 -- out of scope for Plan 02 execution
