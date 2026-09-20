# commentRequired producer chain — frozen implementation contract

Date: 2026-09-09. Risk: S3. Target worktree: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-bff-contract`, detached revision `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.

## Goal

Expose the existing server-owned required-comment rule as an explicit required boolean through domain → Attendance gRPC → BFF JSON. This contract also creates a safe accepted-union baseline by importing exactly the two frozen P1 attachment files before implementation.

## Context / evidence

The independent UI R4 review is `.agent/student-gateway-c/reviews/ui-r4-sol-final-2026-09-08.md`, SHA-256 `3C4D553047A877114393EEABD32E15D35DEBAD1D66F2C22075ADB8384B3F679F`. Canonical wireframe SHA-256 is `366DF74DF00CCAF779BD67A0864D8DED0F0AEFF0A3AC96A9CA8BBAAB2093B633`; it requires server-provided `commentRequired`. Current `StudentRequestService` already rejects null, empty, or whitespace-only comments for `OTHER`, so no product decision is missing.

The target BFF worktree contains accepted P2 but its `StudentRequestService.java` SHA `7AD59234BD0A45FBF8A3AAD9D60971F2558021398C4D018348786D4BE421DEBC` predates accepted P1 attachment correction. Before implementation, copy exactly two frozen files from `requests-notification-authority`: service SHA `B962498813AD3CC4E77EDBFE81E5E82E3013CFE1CF7B0490762880D23FCD3960` and authorization test SHA `D1DDCDE648C3186914BA52846973497F08D82BBA6C198743A5254EC37E8B0B22`. Verify source before and target after. Bot work in that source worktree owns only four notification-bot files and must not be touched.

Accepted P2 exact-five manifest remains historical and immutable: SHA `61C14D95E6E8EA61BCE791EC648218C39FE9771E62E355605A2BAAF313722B97`. P1 final manifest remains historical and immutable: SHA `6D822BB002AA0AF2FE768855FA8EA2CA2986DE379A1D3424029B18662EFD9DEE`.

## Relevant scope

After the exact two-file dependency import, one fresh sole writer may edit only:

- `proto/attendance.proto`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestModels.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/StudentRequestGrpcMapper.java`
- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentRequestApiModels.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentRequestFacade.java`
- existing `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java`
- existing dependency-imported `StudentRequestServiceAuthorizationTest.java` only if a focused regression assertion is necessary; otherwise preserve exact imported bytes
- new `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/StudentRequestGrpcMapperTest.java`
- new `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentRequestFacadeOptionsTest.java`
- new `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contract/StudentRequestOptionsJsonTest.java`
- task evidence under `.agent/student-requests-comment-required-c/r1/**`.

The attached full manifest records every before hash and absent new path.

## Required behavior

Domain `ReasonOption` has a required boolean. The options builder sets `true` for `OTHER` and `false` for every other currently allowed reason, using the same domain authority as existing submission validation. Proto `StudentRequestReasonOption` adds only `bool comment_required = 3`; tag 3 is free in the old source, BFF, P1, and isolation frozen copies. The Attendance mapper sets the field explicitly. BFF `ReasonOption` exposes required non-null JSON `commentRequired` and the facade maps the proto boolean directly. Tests must prove both true and false survive each boundary. Existing OTHER validation must still reject null/empty/whitespace and accept nonblank comment.

## Constraints

Preserve P1 attachment reconciliation and its accepted test bytes. Preserve all P2 error precedence and wire guards except the explicitly superseded `StudentRequestApiModels.java` hash. Do not infer the rule in BFF or UI from the reason code. Do not edit reason enums, authz, lesson eligibility, uploads, budget, details, queue behavior, config, lockfiles, OpenAPI, generated TypeScript, client files, or UI. Generated protobuf Java is build output and must not be edited manually. Canonical OpenAPI/TS generation is deferred to the B1 single integration writer.

## Existing patterns

Attendance and BFF Gradle modules both generate Java from root `proto` with protobuf plugin, protoc 3.25.3 and grpc-java 1.63.0. Domain options are built in `StudentRequestService.options`; Attendance projection is `StudentRequestGrpcMapper.options`; BFF projection is `StudentRequestFacade.options`. BFF tests use Mockito with `MobileAttendanceClient` and `MobileRequestContext`; a STUDENT `InternalJwtClaims(100L, "STUDENT", 10L, false)` fixture is an existing pattern. REST schema generation is Java annotations → OpenAPI snapshot → TS generation, handled later by its owner.

## Acceptance criteria

The exact P1 two-file dependency import matches source SHA before implementation. Domain returns `OTHER=true` and at least one non-OTHER=false. Mapper, proto, facade, Jackson JSON, and required schema preserve both values. Existing blank and whitespace rejection stays green; nonblank `OTHER` stays accepted. P1 authorization/attachment inventory regressions and P2 23+3 tests remain green. A new repair manifest explicitly supersedes only the newly affected hashes and records the dependency import; old P1/P2 evidence is unchanged.

## Verification

No command runs without the global heavy lease. `StudentRequestDomainIT` is in the repository `integrationTest` source selection because root build excludes `*IT` from `test` and includes it in `integrationTest`.

After separate runtime GO, use minimal commands from the target worktree:

1. `.\gradlew.bat :services:attendance-service:attendance-app:test --tests ru.rutcampustrack.attendance.grpc.StudentRequestGrpcMapperTest --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestServiceAuthorizationTest --no-daemon --no-parallel --max-workers=1 --console=plain --rerun-tasks`
2. `.\gradlew.bat :services:attendance-service:attendance-app:integrationTest --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestDomainIT --no-daemon --no-parallel --max-workers=1 --console=plain --rerun-tasks`
3. `.\gradlew.bat :services:mobile-bff:mobile-bff-app:test --tests ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClientErrorTest --tests ru.rutcampustrack.mobilebff.contract.StudentRequestDetailJsonTest --tests ru.rutcampustrack.mobilebff.student.StudentRequestFacadeOptionsTest --tests ru.rutcampustrack.mobilebff.contract.StudentRequestOptionsJsonTest --no-daemon --no-parallel --max-workers=1 --console=plain --rerun-tasks`

Record command, exit, fresh XML counts/hashes, environment and lease release. Run a new union guard against source82, accepted repair2, dependency-imported P1, accepted P2, and this repair manifest. Obtain a fresh independent Sol/high review after stable runtime.

## Do not

Do not write backend source before explicit source GO. Do not start Gradle/Docker/Testcontainers before a separate heavy runtime GO. Do not copy any notification-bot file, overwrite current bot work, modify historical manifests, manually edit generated contracts, regenerate OpenAPI/TS over known drift, weaken OTHER validation, or claim live HTTP/gRPC runtime from unit/serialization tests.