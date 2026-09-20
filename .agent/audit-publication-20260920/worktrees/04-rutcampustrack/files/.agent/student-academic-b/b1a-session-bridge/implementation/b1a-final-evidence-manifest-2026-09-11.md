# B1a session bridge — final evidence and manifest

## Scope and state

- Scope: frozen B1a session bridge, public read-only boundary, signed
  student Homework read/mutation boundary, attendance check-in boundary, and
  Java-first OpenAPI/frontend contract export.
- Risk: S3 (authentication/authorization, public contract, terminal reads and
  mutation boundaries).
- State: `SOURCE_EXPORT_CHECKS_COMPLETE_REVIEW_PENDING`.
- Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Frozen contract: `.agent/student-academic-b/b1a-session-bridge/contract.md`,
  SHA-256
  `524F3E16E878075BC03D1E56D8CDCD188EA4A403168A29C758996E27570B3F62`.
- Environment: Windows 11 host, Java `21.0.10` (Microsoft-13106404),
  Gradle `8.12`, Node `v24.14.0`, npm `11.9.0`.
- Lease: the coordinated heavy lease is released after the final process
  guard. No Java/Javaw/Gradle/npm process remained; unrelated Node processes
  were preserved.

## Acceptance criteria

1. `StudentSession` exposes required canonical `sessionId`, positive decimal
   `sessionVersion` and `rolesVersion`, and explicit `readOnly`, while
   preserving the existing session projection.
2. Public completion and check-in reject read-only identities with
   `ROLE_READ_ONLY` before parsing or dependencies; reads remain usable.
3. Homework gRPC authenticates GET and completion, binds GET to signed own
   claims, permits own terminal/read-only reads including the completed-today
   union, denies peer/group/wrong-role requests, and blocks read-only writes
   before repository access.
4. Attendance ingress and domain/replay boundaries reject read-only identities
   before geo/replay/repository/transaction/event work; snapshot reads remain
   available.
5. Java-first OpenAPI export, generated TypeScript, the explicitly authorized
   session fixture, contract tests, and foundation typecheck pass.

## Affected manifest

The bounded product/test paths are the following. The root-approved compile
adaptation is `MobileAttendanceClient.java`; the session fixture was added only
after root accepted its recorded baseline hash and exact four-field delta.

- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentCheckinFacade.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentSessionProjectionTest.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentQueryHomeworkTest.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptor.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptorTest.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentHomeworkTestIdentity.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentServiceTest.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterIT.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterStrictModeIT.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinModels.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceTest.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/StudentGrpcBoundaryTest.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentAttendanceSnapshotServiceTest.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentCheckinTransactionIT.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterIT.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterStrictModeIT.java`
- `docs/openapi/mobile-bff.json`
- `frontends/mobile-core/src/api/generated/mobile-bff.ts` (official generator)
- `frontends/mobile-core/fixtures/session.json` (root-approved fixture delta)

No proto, SQL/migration, build/config, shared-security, frontend consumer,
frontend manifest, or hand-edited generated file was added to this B1a delta.
Foreign tracked and untracked work in the shared checkout remains untouched.

## Checks and evidence

All Gradle commands used the repository wrapper with one worker, no daemon,
and no parallel execution. Passing Gradle test XML reports are under the
corresponding `build/test-results` directories.

| Check | Evidence | Exit |
| --- | --- | ---: |
| BFF unit selectors | session `48044`, 48 s: `StudentSessionProjectionTest` 3/0/0 and `StudentQueryHomeworkTest` 8/0/0 | 0 |
| Academic unit selectors | session `65134`, 58 s: `StudentHomeworkGrpcIdentityInterceptorTest` 8/0/0 and `HomeworkStudentServiceTest` 7/0/0 | 0 |
| Attendance unit selectors | session `65646`, 1 m 30 s: `AttendanceStudentGrpcServiceTest` 6/0/0, `StudentGrpcBoundaryTest` 15/0/0, `StudentAttendanceSnapshotServiceTest` 4/0/0 | 0 |
| BFF integration selectors | session `99648`, 1 m 29 s: `StudentHomeworkHttpGrpcIT` 8/0/0 and `StudentHttpGrpcAuthIT` 13/0/0 | 0 |
| Academic integration selectors | initial session `11651` exposed a test-fixture SQL trigger failure; the filter suites passed 6+3. After the recorded fixture correction, affected-only session `41241`, 1 m 49 s, `HomeworkStudentCompletionConcurrencyIT` 6/0/0 | 0 after correction |
| Attendance integration selectors | session `60872`, 1 m 29 s: `StudentCheckinTransactionIT` 21/0/0, `AttendanceUserContextFilterIT` 6/0/0, `AttendanceUserContextFilterStrictModeIT` 3/0/0 | 0 |
| Java test compilation | exact shared-security, BFF, Academic, and Attendance `compileTestJava` bundle | 0 |
| OpenAPI update | quoted `-Popenapi.snapshot.update=true` export, session `58919`, 58 s, `OpenApiSnapshotIT` 4/0/0 | 0 |
| OpenAPI snapshot check | same selector without update property, session `62239`, 37 s, `OpenApiSnapshotIT` 4/0/0 | 0 |
| Type generation | `npm run generate:types`; official generator completed | 0 |
| Type generation check | `npm run generate:types:check`; contract hash matched `80e484784a287c3a0468ad4f472469c49e242cb3025fc54bfcebdd915da4a53f` | 0 |
| Frontend contract tests | after root-approved fixture delta, `npm run test:contract`: 11/11 passed | 0 |
| Foundation typecheck | after the same fixture delta, `npm run typecheck:foundation` | 0 |
| Final whitespace check | `git diff --check` over the bounded paths; only Git LF/CRLF normalization warnings | 0 |
| Final process guard | `Get-Process -Name java,javaw,gradle,npm`: none | 0 |

The first export invocation passed an unquoted project property through
PowerShell and Gradle interpreted `.snapshot.update=true` as a task (session
`17498`, exit 1). The exact export was rerun with the property quoted; this
was a shell invocation correction and did not change source.

The first Academic integration attempt failed at the test fixture with
PostgreSQL `SQLSTATE P0001` (`subject N must retain at least one lesson type`),
before the requested behavior ran. The exact test fixture was corrected with
an atomic subject/type setup and the required V25 homework fields; the
affected-only rerun passed 6/6.

The first frontend contract/typecheck attempts reproduced the new required
field error for `session.json`. Root then accepted the fixture baseline
`936CBBE487DE1F0DA98824157C7494EBCA8A81B4164B4BE651C5F122386DB1C` and the
bounded delta. The fixture now has only the four explicit fields:
`sessionId`, `sessionVersion`, `rolesVersion`, and `readOnly=false`.

## Runtime evidence

The focused unit and integration suites provide the applicable runtime
evidence for B1a authorization, terminal Homework reads, read-only mutation
guards, session projection, and OpenAPI startup. No deployment, production
migration, data deletion, secret operation, or coordinated product cutover
was performed. Full A/C/B/E integrated runtime remains open.

## Output hashes

| Output | Bytes | SHA-256 |
| --- | ---: | --- |
| `docs/openapi/mobile-bff.json` | 35,516 | `80E484784A287C3A0468AD4F472469C49E242CB3025FC54BFCEBDD915DA4A53F` |
| `frontends/mobile-core/src/api/generated/mobile-bff.ts` | 26,279 | `D4C240E583B1CC7C430FBAB22B12EB96AE8AE82C04BA09481829361698F2A58A` |
| `frontends/mobile-core/fixtures/session.json` | 792 | `E0E7E4226BEFD1CF64626792690AA1493E4F0630EE63A586C0AA608234865C86` |

The fixture delta is exactly 4 added lines and 0 removed lines. The generated
TypeScript hash was produced by the official generator; no manual generated
file edit was made.

## Diff and limitations

- The final bounded diff check is clean for the B1a and root-approved fixture
  paths. The worktree remains pre-dirty by many other agents; it was not
  reset, cleaned, staged, or committed.
- The source/export checks are complete, but the required fresh independent
  Sol high review is still pending. This artifact does not claim integrated
  cutover, full B1, Auth13, or full-role acceptance.
- The frontend session fixture extension is the only scope decision added
  after the frozen contract. Any broader client import or manifest decision
  remains routed to root/E.
- No Terra escalation was used; no Terra gate exists.
