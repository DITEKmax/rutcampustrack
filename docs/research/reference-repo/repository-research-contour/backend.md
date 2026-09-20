# Backend practices

Status: `verified / independent audit PASS`  
Work unit: `WU-030-BACKEND`  
Source anchor: `REF-SAMPLES@1288bb81051c332f53fc26fc561ae3f16d1063e53f93d92dcef5c5ff2afc55ae`  
Collected by: `backend_conventions_analyst`  
Canonicalized by: `main`

## Snapshot and coverage

The exact approved scope covers the gateway source/tests/spec surface, six representative layered services, two intentionally thin services, common technical packages, protobuf source, and approved backend guidance. `REF-SAMPLES` is an unversioned export, so historical claims remain unknown and locators use the snapshot fingerprint with null ref/commit fields.

`TARGET-CONTEXT` only constrains adaptation. Its approved files require a REST BFF over internal gRPC, RFC 9457, contract-first boundaries, and stable server-side pagination; they do not prove reference practices. Generated contracts, migrations, build/vendor output, secrets, quarantine paths, and product semantics were excluded. No code or tests were executed.

## Service/module boundaries and dependency direction

### Observation

Six assigned roots repeat an `Api / Application / Domain / Domain.Data` split, while two assigned counterexamples remain single-project (`E-BE-000001`).

The names do not imply strict hexagonal isolation: sampled `Application` projects reference persistence and generated contracts directly, and at least two `Api` projects reference persistence (`E-BE-000002`). `Domain.Data -> Domain` preserves partial inward discipline.

### Inference

Inference (medium confidence): project count may reasonably follow service complexity, but the snapshot does not prove that design intent. Recommendation: transfer capability ownership and explicit composition, not four project names or a ports/adapters claim that the dependency graph contradicts.

## Layers, use cases, adapters, and shared code

Sampled gRPC services are thin adapters: map requests, dispatch mediator messages, and propagate cancellation. Five composition roots register handlers by assembly (`E-BE-000003`). Dispatch is stronger evidence than the inconsistent command/query naming.

Shared packages centralize hosting, EF, logging, storage, and text mechanics, and six layered services reuse their helpers (`E-BE-000006`). A thin proxy counterexample does not consume the full stack. Share narrow technical mechanics; keep use-case, transport, and product policies capability-owned.

## API, BFF, DTO, command, query, and event naming

The gateway is an explicit composition root: a base schema is extended by feature queries/mutations, subscriptions, and loaders (`E-BE-000004`). This supports composition at a BFF boundary, not copying GraphQL or source vocabulary. One loader still issues one downstream request per key.

Gateway models are explicitly mapped to downstream requests (`E-BE-000005`). The counterexample is one approximately 4,690-line mapper with roughly 325 methods: mapping boundaries transfer, cross-feature concentration does not.

Protobuf source is canonical generation input and generated output is physically separated (`E-BE-000007`), yet generated types leak into Application. Request/response naming is common; pagination vocabulary and compatibility markers are only candidates (`E-BE-000008`). Target names should be consistent and application-owned; generated gRPC types remain in adapters, and the public target boundary remains REST/OpenAPI.

## Validation, errors, mapping, and compatibility

Three transport boundaries centralize exception translation for gRPC, GraphQL, and HTTP (`E-BE-000010`). Exact behavior is inconsistent: a generic gRPC catch exposes exception messages, one HTTP middleware has questionable mappings, and no systematic validator implementation was found in assigned paths.

Adopt central transport-specific mapping with stable codes. Adapt the public REST edge to RFC 9457 and add endpoint/application validation plus domain invariant checks. Assigned protobuf packages do not prove compatibility discipline (`E-BE-000008`).

## Persistence, transactions, and messaging

Handlers in six layered roots inject concrete `DbContext` types; no repository/unit-of-work source files were found in those roots (`E-BE-000009`). This is pragmatic EF use, not an isolation rule. No explicit transaction orchestration was found in the sampled paths; that absence has narrower confidence.

A shared EF interceptor publishes change-tracker events after save, but no atomic outbox or representative service adoption was found (`E-BE-000014`). Commit can precede publication failure, and persistence-shaped events couple schemas and data. Treat this as an isolated avoid candidate. Messaging remains deferred until a concrete asynchronous-consistency need justifies versioned events, outbox delivery, and idempotent consumers.

## Authentication and authorization placement

Authentication is isolated in a service; gateway middleware/interceptors perform token and permission decisions at the public composition boundary (`E-BE-000011`). Production-only interceptor registration is a counterexample. Transfer the boundary, not roles or permissions: enforcement must remain invariant across environments.

## Configuration, resilience, and observability

Correlation identifiers propagate through headers, logging context, and downstream gRPC metadata (`E-BE-000012`). The same package broadly serializes payloads with a short exclusion list. Adopt correlation metadata; avoid generic body logging and use structured allowlists/redaction.

One shared helper applies four retries for `Unavailable` at default method scope without visible idempotency classification (`E-BE-000013`). Target retries must be method-aware, deadline-bounded, and paired with idempotency/deduplication for writes.

Shared helpers expose gRPC and health endpoints and are reused (`E-BE-000015`). Weak raw configuration and swallowed migration failures are counterexamples. Target configuration should be typed/validated; schema readiness should fail fast or be deployment-controlled.

## Testing and quality gates

The assigned gateway tests contain one active schema snapshot and one commented test (`E-BE-000016`). Contract snapshots are useful but cannot establish behavioral coverage. Service tests outside the assignment were not inspected, so no collection-wide absence claim is made.

## BFF-facing boundary for RutCampusTrack

The reference supports explicit edge composition (`E-BE-000004`, `E-BE-000005`), while approved target context requires:

- public REST/OpenAPI at the BFF and internal gRPC;
- BFF-owned composition and transport DTO mapping without domain rules or screen semantics in services;
- RFC 9457 errors;
- server-side filter, deterministic sort, pagination, and explicit totals;
- authentication at the public edge and trusted identity propagation.

These are adaptation constraints, not evidence records.

## Transfer candidates

| Practice | Mode | Evidence IDs | Preconditions | Risks |
|---|---|---|---|---|
| Capability boundaries; thin adapters; explicit handlers | adopt | `E-BE-000001`, `E-BE-000003` | clear ownership | over-splitting simple services |
| Narrow shared technical packages | adopt | `E-BE-000006`, `E-BE-000012`, `E-BE-000015` | stable ownership/versioning | centralized risky defaults |
| Contract source canonical; generated output adapter-only | adapt | `E-BE-000007`, `E-BE-000002` | adapter mapping | generated DTO leakage |
| Central error conversion with stable codes | adopt | `E-BE-000010` | explicit taxonomy/redaction | internal-message exposure |
| REST BFF composition with feature-local mapping | adapt | `E-BE-000004`, `E-BE-000005` | approved REST edge | N+1 and giant mapper |
| Direct EF behind application ports where isolation matters | adapt | `E-BE-000009` | testability need | unnecessary abstraction |
| Typed config, readiness, controlled migrations | adapt | `E-BE-000015` | operational ownership | startup drift |
| Method-aware retries, deadlines, idempotency | adapt | `E-BE-000013` | operation classification | duplicate effects |
| Contract snapshots plus behavior tests | adapt | `E-BE-000016` | stable schemas | snapshot-only confidence |
| Generic post-commit change events without outbox | avoid | `E-BE-000014` | none | loss, duplication, data leakage |
| Generic request/response body logging | avoid | `E-BE-000012` | none | sensitive-data exposure |
| Environment-dependent authorization | avoid | `E-BE-000011` | none | security divergence |
| Messaging architecture | defer | `E-BE-000014` | concrete async need | premature complexity |

## Counterexamples and unknowns

- Snapshot-only provenance cannot identify legacy versus current intent.
- Thin services disprove a universal four-project rule (`E-BE-000001`).
- Direct dependencies disprove strict hexagonal isolation (`E-BE-000002`).
- Command/query, pagination, and compatibility naming are inconsistent (`E-BE-000003`, `E-BE-000008`).
- Loader, mapper, payload-logging, retry, migration, and event-publication implementations contain avoid/adapt signals (`E-BE-000004`, `E-BE-000005`, `E-BE-000012`–`E-BE-000015`).
- Runtime DI, database behavior, retries, migrations, and tests were not executed.
- Transaction isolation, downstream idempotency, compatibility CI, and production observability remain unproven.

## Evidence index

| Evidence ID | Primary locator | Count | Confidence |
|---|---|---:|---|
| `E-BE-000001` | `authentication-service-master/src` | 6 | high |
| `E-BE-000002` | `authentication-service-master/src/AuthService.Application/AuthService.Application.csproj:14` | 5 | high |
| `E-BE-000003` | `projectdocuments-master/src/DocumentationService.Api/Services/DocumentGrpcService.cs:10` | 4 | high |
| `E-BE-000004` | `api-gateway-master/src/APIGateway/GraphQLConfig.cs:25` | 17 | high |
| `E-BE-000005` | `api-gateway-master/src/APIGateway/Schema/Mapper.cs:149` | 2 | high |
| `E-BE-000006` | `common-master/src/VSM.Common.Api/Services.cs:29` | 6 | high |
| `E-BE-000007` | `contracts-master/VSM.Contracts.csproj:24` | 1 | high |
| `E-BE-000008` | `contracts-master/VSM.Contracts/news/news.proto:105` | 1 | medium candidate |
| `E-BE-000009` | `projectdocuments-master/src/DocumentationService.Application` | 6 | high |
| `E-BE-000010` | `common-master/src/VSM.Common.Logging/Middlewares/RpcErrorInterceptor.cs:19` | 3 | high |
| `E-BE-000011` | `authentication-service-master/src/AuthService.Api/Program.cs:26` | 2 | medium |
| `E-BE-000012` | `common-master/src/VSM.Common.Logging/Middlewares/LoggingMiddleware.cs:16` | 1 | high |
| `E-BE-000013` | `common-master/src/VSM.Common.Api/Services.cs:85` | 1 | medium candidate |
| `E-BE-000014` | `common-master/src/VSM.Common.EF/KafkaSaveChangesInterceptor.cs:15` | 1 | low candidate |
| `E-BE-000015` | `common-master/src/VSM.Common.Api/ApplicationBuilder.cs:48` | 6 | high |
| `E-BE-000016` | `api-gateway-master/tests/APIGateway.Tests/ApiTests.cs:13` | 1 | high candidate |

All 16 records were independently verified by `evidence_auditor` at `2026-08-30T21:37:32.0237643+03:00`.
