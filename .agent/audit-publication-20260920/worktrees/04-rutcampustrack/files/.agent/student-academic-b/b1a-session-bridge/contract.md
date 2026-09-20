# B1a session bridge — compact execution contract

## 1. Goal

- **State:** `WAIT_A_SHARED_IDENTITY_IMPLEMENTATION_ACCEPTED` (the exact A
  signature is frozen; A implementation and focused acceptance are in
  progress).
- **Risk:** S3 (auth/authz, public contract, terminal reads and mutation
  boundaries).
- **Owner/model:** B1a bounded Java/BFF developer, `gpt-5.6-luna`, `max`;
  current mutation is this contract artifact only.

Deliver the minimal B1a server-session bridge for the accepted E1
login/Today/Homework path. The delivery excludes full B1/map, E2, WS, profile,
and cutover work.

## 2. Context/evidence

- Frozen owner/root source:
  `C:/Users/maksd/.codex/worktrees/1267/rutcampustrack/.agent/student-role-orchestrator/session-bridge-b1a-2026-09-10.md`.
- Accepted A producer packet:
  `C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/.agent/student-auth-a/admission-producer/packet.md`,
  SHA-256 `BE5A0F8B503D0683A6FA436077BC39A21B74B9B57ECC06BEFB1874AB4BFAE4EF`.
- Frozen A record is exactly
  `InternalJwtClaims(long userId, UUID sessionId, long sessionVersion, long rolesVersion, String role, String status, Long groupId, boolean isHeadman, boolean readOnly)`.
  JWT claim names are string `sub`/`sid`/`sv`/`rv`/`group_id` plus
  `role`/`status`, with booleans `is_headman`/`readOnly`. Internal admission
  excludes `SUSPENDED`; a terminal identity has `readOnly=true`.
- A packet §6 lists consumer impact. B consumes only this stable exact record;
  no compatibility constructor or silent claim default is permitted.
- Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` (detached HEAD).
- Root-declared current Auth13/B0 accepted manifest:
  `790081134DF7467362C844144345EF471B18B8F8D98A386A92DB448E2FE36097`.
- The checkout is massively pre-dirty. The local observational reading of
  `frontends/mobile-core/fixtures/manifest.json` is `AB57D82151AE89AAF8B6EDFD299307C49D075EAC18BF02979BD1923D8EB06EB1`,
  1149 bytes. This difference is non-authorizing; root must re-freeze B scope
  after A's handoff.
- A owns
  `services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/InternalJwtClaims.java`,
  `InternalJwtValidator.java`,
  `services/shared/shared-security/src/testFixtures/java/ru/rutcampustrack/shared/security/InternalJwtTestFactory.java`,
  and their focused shared tests. A must hand off checked identity
  `sub`/`sid`/`sv`/`rv`, role/status/group, `isHeadman`, and `readOnly` together
  with exact signatures, tests, and hashes. No B product writer starts while
  A implementation and focused acceptance are still in progress.
- Current pre-A product-candidate observations are hashes/bytes only; they are
  not ownership or authorization. Root rehashes them after A:

  | Candidate | Bytes | SHA-256 (pre-A observation) |
  | --- | ---: | --- |
  | `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java` | 11321 | `A563B2D9497A284B671580F151B0A95AC485BCA0716FA5E85ADE9960F865AFB4` |
  | `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java` | 17239 | `8427C9F876B586D87BE675D079282D6A3B377202844ACF68113D775A75A5BB26` |
  | `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentCheckinFacade.java` | 4155 | `AFA81725AA4EADBACC63433EB217BE991A81DFA956CD8BD545D96B1CACA08FC7` |
  | `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptor.java` | 2112 | `6B88450D1A7CAF7F54C2C1CDCD41F8A89D956F4D342FC5902C112E47D55AEB3B` |
  | `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java` | 25426 | `2B6AE50091315A1473D16FA52DC8749E7602975A934B7D2980B5DAD0A21AEF48` |
  | `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java` | 5380 | `32897B42552FC0B0E226C78E6EA24E559CB8BC9DAF1C30BB7DDCBAC4122C996B` |
  | `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java` | 14256 | `24B43A4F8EE6CB2FC79F51391250F8A36F1F74A01C183D984FADCAE55E6DE4E3` |
  | `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinModels.java` | 2149 | `F3EB03BF9647F816B4BD0A7C94F093E4DDA6E254DB10FCF11DF15367535DE987` |
  | `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java` | 21343 | `EBA42859611FE31BC4FE700EF43341B63A1DAD43B6CB1909C6CEDED6EF9C6C6C` |

## 3. Relevant scope

The only B1a Java product-candidate paths are:

- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentCheckinFacade.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptor.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinModels.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java`

The exact test candidates are:

- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentSessionProjectionTest.java` (new),
  `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentQueryHomeworkTest.java`,
  `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java`,
  `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java`,
  `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contractexport/OpenApiSnapshotIT.java`.
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptorTest.java`,
  `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentServiceTest.java`,
  `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java`,
  `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterIT.java`,
  `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterStrictModeIT.java`.
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceTest.java`,
  `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/StudentGrpcBoundaryTest.java`,
  `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentAttendanceSnapshotServiceTest.java`,
  `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentCheckinTransactionIT.java`,
  `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterIT.java`,
  `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterStrictModeIT.java`.

The paths above are exact and do not authorize any additional path. The four
`*UserContextFilter*IT` files are the sole later compile-only test adaptations
explicitly added by A packet §6; they may pass full explicit frozen claims to
`validToken`, with no assertion or behavior weakening. This contract is the
sole current mutation; all foreign dirty and untracked work remains
preserved.

## 4. Required behavior

### Public BFF session

- Add to `StudentApiModels.SessionResponse` required JSON fields
  `sessionId`, `sessionVersion`, `rolesVersion`, and `readOnly`.
- `sessionId` is a canonical lowercase UUID string. `sessionVersion` and
  `rolesVersion` are positive canonical decimal strings; tests include a value
  above the JavaScript safe-integer limit. The schema has these fields in
  `requiredProperties` and declares the required `format`/`pattern` checks.
- `StudentQueryService.session()` obtains these four values only from
  validated claims and preserves `user`, `activeRole`, `group`, `semester`,
  `capabilities`, `serverNow`, and `_links`.
- Auth `CurrentSession` remains a separate bootstrap contract. There is no
  client-side identity stitch.

### Public HTTP read-only boundary

- Add `ProblemCode.ROLE_READ_ONLY`.
- `StudentQueryService.setHomeworkCompletion` and
  `StudentCheckinFacade.checkin` reject a read-only identity with HTTP 403 and
  `ROLE_READ_ONLY` before parsing input or touching a dependency/RPC.
- Reads, including session, Today, and Homework, remain usable for a
  read-only identity.

### Homework downstream boundary

- `StudentHomeworkGrpcIdentityInterceptor` authenticates both
  `GetHomeworksForWeek` and `SetHomeworkCompletion`; unrelated RPCs are
  unchanged.
- `AcademicGrpcServiceImpl.getHomeworksForWeek` binds request student/group
  to the signed claims before lookup. It uses the existing
  `UserRepository.findByIdIncludingArchived` narrowly for the exact own user,
  permits active and terminal/read-only STUDENT own reads, including the
  completed-today union, and rejects peer, group, and wrong-role requests.
  Do not remove a global `SQLRestriction` or change generic
  `GetUserById` semantics.
- `HomeworkStudentService.setCompletion` rejects `claims.readOnly` before
  any repository access or mutation.
- Existing `MobileAcademicClient` already forwards the token. Change it only
  for a compile-only adaptation forced by the stable A API and approved by
  root.

### Check-in downstream boundary

- `AttendanceStudentGrpcServiceImpl.checkin` rejects read-only before geo
  parsing, replay lookup, or dependencies. Internal attendance gRPC uses the
  existing `PERMISSION_DENIED`/`OUT_OF_SCOPE` status in this bounded stage;
  there is no proto edit.
- `StudentCheckinModels.Identity` adds explicit `readOnly`; the mapping copies
  `claims.readOnly`.
- `StudentCheckinService.checkin` rejects read-only before receipt,
  repository, transaction, or events. `StudentCheckinService.replay`, when
  called independently, also rejects read-only before receipt lookup. Both
  valid geo variants follow this boundary.
- Snapshot/Today reads ignore `readOnly` and remain allowed. Public HTTP uses
  the exact `ROLE_READ_ONLY` code.

## 5. Constraints

- B remains blocked until A's implementation and focused acceptance hand off
  stable exact shared identity signatures/tests/hashes; root then rehashes this
  B scope before any product spawn. B never adds compatibility constructors,
  default `sid`/version/status/`readOnly` values, or an insecure fallback.
- Constructor-only fixture fallout may stay within the exact test list in
  Section 3. The four A §6 filter test adaptations are compile-only and must
  supply full explicit frozen claims to `validToken`; their assertions and
  behavior remain unchanged. Unexpected compile fallout outside this list
  blocks scope expansion and requires a root decision.
- Schedule, notification, gateway, and auth custom builders and other
  `validToken` callsites remain outside B1a as an explicit residual
  owner-routed consumer delta; production compile is added only if root finds
  it necessary after the exact B scope is rehashed.
- Do not edit A-owned shared-security files, proto, SQL/migrations,
  repositories, build/lock/config files, unrelated APIs/controllers, or
  generated files. Do not globally remove archived filtering or broaden role
  access. Do not replace the signed claim source with caller-supplied
  identity.
- Preserve accepted routes, data ownership, source grants, and active paths.
  No deployment, production migration, data deletion, backup operation,
  firewall change, secret operation, or product cutover is authorized.
- Keep this B1a work separate from full B1/map, E2, WS, profile, and the final
  coordinated A/C/B/E cutover. Any product, contract, or scope decision needed
  by the implementation is recorded as a required delta for root rather than
  used to redesign the contract.

## 6. Existing patterns

- External transport is REST and internal transport is checked JWT over gRPC;
  authorization stays at ingress and in the domain service.
- B owns Java-first public contract projection and OpenAPI export. The
  existing `CurrentSession` bootstrap and BFF `StudentSession` are separate
  contracts, and existing `MobileAcademicClient` token forwarding is retained.
- Existing authoritative user/group reads remain the source for preserved
  display fields. Narrow archived-aware own-user lookup is the pattern for
  terminal student reads; it is not a global repository policy change.
- Read-only enforcement is duplicated at public facade, gRPC ingress, and
  domain mutation boundaries so a UI flag cannot become authorization.

## 7. Acceptance criteria

- The gate is closed while state is
  `WAIT_A_SHARED_IDENTITY_IMPLEMENTATION_ACCEPTED`; it opens only after A's
  implementation and focused acceptance handoff plus root re-freeze. No B
  product writer starts before that gate.
- Session wire output contains exactly the four required fields with canonical
  UUID/decimal serialization, a large decimal test value, schema
  requiredness, and all preserved fields.
- Read-only session/Today/Homework GET paths succeed; completion and check-in
  return 403 `ROLE_READ_ONLY` before parsing, RPC, replay, repository,
  transaction, or event work. Existing active paths stay green.
- Signed-context Homework own terminal reads succeed through the narrow
  archived-aware lookup, including completed-today union; peer, group, and
  wrong-role requests are denied; read-only completion performs no repository
  work.
- Check-in ingress and domain read-only cases are denied for both geo variants
  without replay/repository/transaction/event effects; snapshots and Today
  remain readable.
- B1a Java source plus the later Java-first export can be marked **source/export
  PASS** only after its applicable checks and fresh Sol high independent review
  pass. Integrated cutover and coordinated runtime remain **OPEN** until the
  later A/C/B/E runtime. Full occurrences/roster/map/E2/WS lifecycle remains
  separately **OPEN**.

## 8. Verification

All Gradle commands below are exact proposals, **NOT RUN** in this leaf, and
require a heavy runtime lease only after the identity gate is READY. Expected
exit code for a passing command is `0`; a skipped or unrun command is not
PASS. Run them with Java 21 and the repository wrapper in the stated checkout,
recording revision, command, exit code, environment, and evidence.

**Unit selectors — proposed / NOT RUN:**

```text
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:test --tests "ru.rutcampustrack.mobilebff.student.StudentSessionProjectionTest" --tests "ru.rutcampustrack.mobilebff.student.StudentQueryHomeworkTest"
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:academic-service:academic-app:test --tests "ru.rutcampustrack.academic.grpc.StudentHomeworkGrpcIdentityInterceptorTest" --tests "ru.rutcampustrack.academic.homework.HomeworkStudentServiceTest"
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:attendance-service:attendance-app:test --tests "ru.rutcampustrack.attendance.grpc.AttendanceStudentGrpcServiceTest" --tests "ru.rutcampustrack.attendance.grpc.StudentGrpcBoundaryTest" --tests "ru.rutcampustrack.attendance.student.StudentAttendanceSnapshotServiceTest"
```

**Integration selectors — proposed / NOT RUN:**

```text
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.runtime.StudentHomeworkHttpGrpcIT" --tests "ru.rutcampustrack.mobilebff.runtime.StudentHttpGrpcAuthIT"
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:academic-service:academic-app:integrationTest --tests "ru.rutcampustrack.academic.homework.HomeworkStudentCompletionConcurrencyIT" --tests "ru.rutcampustrack.academic.security.AcademicUserContextFilterIT" --tests "ru.rutcampustrack.academic.security.AcademicUserContextFilterStrictModeIT"
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:attendance-service:attendance-app:integrationTest --tests "ru.rutcampustrack.attendance.student.StudentCheckinTransactionIT" --tests "ru.rutcampustrack.attendance.security.AttendanceUserContextFilterIT" --tests "ru.rutcampustrack.attendance.security.AttendanceUserContextFilterStrictModeIT"
```

**Export selectors — proposed / NOT RUN:**

```text
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue -Popenapi.snapshot.update=true :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.contractexport.OpenApiSnapshotIT"
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.contractexport.OpenApiSnapshotIT"
```

The first export command updates `docs/openapi/mobile-bff.json` through the
exact root project property `-Popenapi.snapshot.update=true`; the second is
the snapshot check. The later frontend commands are listed below and are also
**NOT RUN** here.

**Later sequential export sole-writer stage — proposed / NOT RUN:**

After Java source checks and the OpenAPI snapshot check, the export writer
updates only `docs/openapi/mobile-bff.json` with the first command above, then
from workdir `frontends` runs exactly:

```text
npm run generate:types
npm run generate:types:check
npm run test:contract
npm run typecheck:foundation
```

`npm run generate:types` may write only
`frontends/mobile-core/src/api/generated/mobile-bff.ts`. `frontends/mobile-core/src/api/types.ts`
and `frontends/mobile-core/src/api/student-client.ts` are read/check only;
their consumer import/use belongs to E.

Leaf-only artifact checks after the final write are readback, scoped
`git diff --check -- .agent/student-academic-b/b1a-session-bridge/contract.md`,
and SHA-256/byte measurement. Their passing exit code is `0`; they do not run
Gradle, npm, services, Docker, SQL, or product runtime. Runtime evidence for
this docs-only leaf is `N/A`. A stable Java plus generated diff requires a
fresh independent Sol high review; the later coordinated A/C/B/E runtime is
the only runtime evidence for the integrated path.

## 9. Do not

- Do not clear the A gate from DTO or wording checks, start a B product writer
  before stable A identity signatures/tests/hashes, or claim that pre-dirty
  worktree bytes are an authorization baseline.
- Do not add compatibility identity defaults, insecure fallbacks, client-side
  stitching, global SQLRestriction changes, generic user lookup changes, or
  proto edits. Do not hand-edit generated TypeScript or widen the exact path
  list without a recorded root scope decision.
- Do not mark integrated cutover/runtime, Auth13, full B1, map, E2, WS, or
  full-role acceptance PASS from these source/export checks. Do not repeat
  accepted unrelated checks.
- Do not escalate to Terra for S3 alone. Terra is admissible only after a
  recorded defect/complexity gate containing request/reference,
  reproduction, new evidence, correction, bounded scope, and root decision;
  the correction then needs independent recheck.

## 10. Root-authorized repair addendum — 2026-09-11

The accepted sections above remain frozen. A fresh independent Sol/high review
recorded a confirmed MEDIUM defect: with a valid signed terminal STUDENT token
(`status=EXPELLED`, `readOnly=true`),
`PUT /api/v1/student/homework/{id}/completion` and
`POST /api/v1/student/lessons/{lessonId}/checkin` could reach MVC path,
header, or body resolution before the existing facade guard. Malformed or
overflow path values, a missing or invalid `Idempotency-Key`, malformed JSON,
and bean-invalid JSON therefore returned typed 400 instead of the required
typed 403 `ROLE_READ_ONLY`; the downstream gRPC call count remained zero.

Root accepted a bounded scope expansion for this finding. The only additional
production path is
`services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java`.
The only additional focused test paths are the existing
`StudentHomeworkHttpGrpcIT.java` and `StudentHttpGrpcAuthIT.java`. The repair
may append only B1a evidence under this directory. No public DTO, OpenAPI,
generated frontend, Academic, Attendance, proto, SQL, build, or configuration
path is expanded.

The correction validates the signed JWT first, then uses context-path-aware
exact route-shape matching for only the two PUT/POST mutation paths. A
read-only claim receives `403 application/problem+json` with canonical
`MobileProblemDetails`, `ROLE_READ_ONLY`, request URI, and the task clock
timestamp before `chain.doFilter`; invalid or missing JWTs retain 401
precedence. Existing facade/domain guards and writable validation remain.

Repair verification is recorded in
`implementation/b1a-readonly-ingress-repair-evidence-2026-09-11.md`.

The same fresh reviewer recheck recorded a second confirmed MEDIUM defect in
the repaired check-in ingress: the early 403 emitted by
`MobileIdentityFilter.writeReadOnlyProblem` had no `Cache-Control: no-store`
header. `HomeworkNoStoreFilter` covers the Homework routes only, so the valid
read-only check-in case and all six malformed/invalid check-in precedence cases
could return a cacheable problem response. Root authorized a second bounded
source correction in the existing `MobileIdentityFilter.java` and assertions
in the existing `StudentHttpGrpcAuthIT.java`; `StudentHomeworkHttpGrpcIT.java`
and all other paths remain unchanged.

The correction sets `HttpHeaders.CACHE_CONTROL` to
`CacheControl.noStore().getHeaderValue()` in `writeReadOnlyProblem`. The two
check-in test paths assert `no-store` for the valid read-only rejection and all
six parameterized malformed path, overflow path, missing/invalid header,
malformed body, and bean-invalid body cases. Source-ready hashes before and
after this second correction are recorded in the append-only evidence below:
`implementation/b1a-readonly-ingress-repair-evidence-2026-09-11.md`.
Compilation and focused runtime verification remain NOT RUN pending the main
B lease; no runtime PASS is claimed by this addendum.

Root subsequently granted the main B heavy GO. The authorized verification and
release guard are recorded in the append-only repair evidence. The exact
`compileTestJava` command (session `96079`) and exact
`StudentHttpGrpcAuthIT` integration selector (session `92308`) both passed with
exit `0`; the focused XML is 19/0/0/0. B is now `RELEASED` for fresh Sol/high
recheck. No additional suite, Homework selector, OpenAPI/generation,
frontend, Docker, SQL, or integrated cutover claim is added by this update.

### 10.1 Root-authorized matrix-path correction — 2026-09-11

The fresh matrix-path review recorded a confirmed MEDIUM defect in the first
ingress repair. Matrix parameters on the student prefix, the homework/lesson
variable segment, or the mutation suffix were part of the raw servlet URI, so
raw prefix/suffix route checks did not establish the required read-only
authorization precedence for all equivalent Spring routes. The affected
examples are:

- `PUT /api/v1/student;v=1/homework/9001;v=2/completion;v=3`;
- `POST /api/v1/student;v=1/lessons/77;v=2/checkin;v=3`;
- the same two routes with the matrix decoration on only the student prefix,
  only the variable segment, or only the mutation suffix.

Root accepted the bounded correction decision. The production correction is
limited to the existing
`services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java`.
The focused scenarios are limited to the existing
`StudentHomeworkHttpGrpcIT.java` and `StudentHttpGrpcAuthIT.java` files. No
DTO, OpenAPI, generated frontend, Academic, Attendance, proto, SQL, build,
configuration, downstream, or unrelated test path is expanded.

`MobileIdentityFilter` now uses `PathContainer.parsePath` after removing the
servlet context path and Spring `PathPattern` instances for
`/api/v1/student/**`, the exact Homework completion route, and the exact
check-in route. The signed JWT is still validated first; terminal read-only
claims receive the existing typed 403 before MVC path/header/body resolution,
with the original request URI retained. Writable matrix-decorated routes are
allowed to continue into the existing controller/facade validation and gRPC
flow. The existing invalid/missing JWT 401 precedence and non-mutation/read
behavior remain in scope.

### Matrix source freeze and verification state

The exact source/test before and after hashes for this correction are recorded
in `implementation/b1a-readonly-ingress-repair-evidence-2026-09-11.md`:

| Path | Before bytes / SHA-256 | After bytes / SHA-256 |
| --- | ---: | ---: |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java` | 5,669 / `CFB25440BC99507F9EFD6929998923FAF2BA67D947C9701AC16056910ADE61CF` | 5,640 / `FDDDD4C7893658E5A6FE68D5F69AED3B0839D04AAE369C5772DA955FFFD3E35E` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java` | 27,871 / `C20C60B4B7E22879CB216225FFE826490CD7D74BC0CF164A8E6D874830EC2A48` | 30,609 / `921FAF523C620C346F281003F5A7167DF2095B764421A82C6A0748538A885980` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java` | 20,139 / `058DAF8227BDF048E095AAAE73E6CDF51FCF16498CBBB6323FDC1EF83F18930F` | 22,412 / `5D038E24C28FF53B9EAFCF450C983A82029C7CDDBD2C2CB4E0C25B3517DDAFAA` |

The source-only checks for this matrix byte freeze are `git diff --check`
over the three source/test paths and a Java/Gradle/npm process guard; both
must exit `0`. The exact `compileTestJava` check and the combined focused
integration selector for both existing IT classes are required, but are
explicitly **NOT RUN for these new matrix bytes** at this point. The
source-ready state is `MATRIX_SOURCE_READY_WAIT_B_LEASE`; it is not a runtime
PASS or a B release. The prior sessions `96079` and `92308`, and XML
`091FDE50CCECC1AC13026D797F93245CE9923CE6BC6B9B0700F990310C452D45`, predate
these matrix bytes and remain evidence only for the earlier no-store stage.
