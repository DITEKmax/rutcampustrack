# commentRequired producer-chain packet r1

Risk: S3. Role: fresh bounded implementation leaf, sole writer in target scope. Model/effort: gpt-5.6-luna/max. Target: C:\Users\maksd\.codex\worktrees\e31c\rutcampustrack\.agent\worktrees\requests-bff-contract. Frozen base revision: d3c31acb8cce53791a4981e5858a37d44fdc9a0e. Contract SHA-256: 51A1760E1EE4A14BC9622BE5A8351F1BFD1268DB6F70056BAE8D0C8070372A08. Full manifest SHA-256: A190D4C62F763E60E8BF0326BB7444D3EC161ACBFDB01198FDEB29724D7EC1F6. Source GO granted. Heavy lease B SQL7 was active; no heavy/runtime command was run.

## Goal

Expose the server-owned required-comment rule as a required boolean from the attendance domain through Attendance gRPC to BFF JSON. Import exactly the frozen P1 service and authorization test first, preserving the attachment reconciliation.

## Context / evidence

The frozen contract states that OTHER already requires a nonblank comment and existing validation rejects null, empty, and whitespace-only values. The implementation source worktree provides the accepted P1 attachment reconciliation. Target pre-import hashes, source hashes, absent3 and all implementation baselines were checked against the full manifest before writing.

## Relevant scope

Owned implementation paths are proto/attendance.proto; attendance StudentRequestModels.java, imported StudentRequestService.java, StudentRequestGrpcMapper.java, StudentRequestDomainIT.java, and new StudentRequestGrpcMapperTest.java; BFF StudentRequestApiModels.java, StudentRequestFacade.java, and new StudentRequestFacadeOptionsTest.java and StudentRequestOptionsJsonTest.java; plus .agent/student-requests-comment-required-c/r1/** evidence. The imported StudentRequestServiceAuthorizationTest.java is a dependency import and remains byte-exact.

## Required behavior

ReasonOption carries required boolean metadata. The domain options builder emits true for OTHER and false for every other allowed reason through the same commentRequired authority used by submission validation. Proto adds only bool comment_required = 3. The attendance mapper sets it explicitly. BFF exposes required non-null JSON commentRequired; the facade maps the proto boolean directly. Tests prove both values across domain, mapper, facade and Jackson JSON, while preserving strict blank rejection and nonblank OTHER acceptance.

## Constraints

Preserve P1 attachment reconciliation and P2 auth/error/wire behavior. Do not infer in BFF/UI. Do not edit authz, enums, eligibility, uploads, budgets, details, queues, configs, lockfiles, OpenAPI, generated TypeScript, client files, UI or generated Java. Do not edit the imported authorization test unless a concrete focused regression is necessary. Do not run Gradle, protoc/generateProto, compile, tests, Docker, Testcontainers or product runtime under the active heavy lease.

## Existing patterns

Domain options are built in StudentRequestService.optionsFor; transport projection is StudentRequestGrpcMapper.options; BFF projection is StudentRequestFacade.options. Existing backend tests use AssertJ/JUnit and existing BFF tests use Mockito with MobileAttendanceClient, MobileRequestContext and InternalJwtClaims(100L, "STUDENT", 10L, false).

## Acceptance criteria

The exact two-file dependency import matches source SHA before implementation and target post SHA. The domain emits OTHER=true and non-OTHER=false; the proto, mapper, facade and JSON preserve both values. Null, empty and whitespace OTHER comments remain rejected and nonblank OTHER remains accepted. Only owned paths are changed by this leaf. Old P1/P2 evidence remains immutable and the r1 manifest supersedes affected path hashes.

## Verification

Static/hash/diff guards only under this lease. Record every check with command, exit code, environment and evidence. Record the three contract runtime commands as NOT RUN until root obtains a separate heavy runtime GO. Root obtains a fresh independent Sol/high review after stable runtime.

## Do not

Do not copy notification-bot files, overwrite foreign work, change historical manifests, hand-edit generated contracts, claim live HTTP/gRPC runtime from static/unit evidence, or escalate to Terra without the recorded defect/complexity gate.
