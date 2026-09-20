# Runtime evidence

Revision `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`, branch
`codex/student-role-02-dependency-checks`, Windows 11, Java `21.0.10`, Gradle
`8.12`, Node `v24.14.0`, npm `11.9.0`.

| Boundary | Command/result | Evidence |
| --- | --- | --- |
| Docker/Testcontainers preflight | `docker ps --format ...`, exit `0`, zero containers | Engine available through approved elevated tool context |
| OpenAPI no-update gate | six Java integration suites, exit `0`, 9 tests, 0 failures/errors/skipped | `snapshot-diff.md`; six service test reports under `services/*/build/test-results/integrationTest/` |
| Homework producer | `:academic-service:academic-app:integrationTest --tests ...HomeworkNotificationContractIT`, exit `0`, 2 tests | `TEST-...HomeworkNotificationContractIT.xml` |
| Shared event guard | `:shared:shared-events:test --tests ...EventSchemaCoverageTest`, exit `0`, 50 tests | `TEST-...EventSchemaCoverageTest.xml` |
| BFF HTTP to signed gRPC | `:mobile-bff:mobile-bff-app:integrationTest` with `StudentHttpGrpcAuthIT` and `StudentHomeworkHttpGrpcIT`, exit `0`, 19 tests | both BFF XML reports; `grpc.server.port=0` is test-only |
| Renderer | full renderer `test` + `jacocoTestReport` + `jacocoTestCoverageVerification`, exit `0`, 10 tests | JaCoCo XML and HTML report |
| Renderer coverage | line `100/133 = 75.19%`; class `6/8 = 75%` | generated protobuf/gRPC class files are absent; handwritten `DocumentRendererGrpcServiceImpl` is present in XML/HTML |
| Push compatibility | notification test with actual web-push 5.1.2 `PushService.send`, exit `0`, 1 test | loopback `HttpServer` only, response `201`, encryption/signing/request headers asserted |
| Mobile generated contract | `npm run generate:types`, then `npm run generate:types:check`, exits `[0,0]` | generated hash in `snapshot-diff.md` |

Final no-update XML report hashes (captured immediately after the last combined
run):

```text
academic   01FCD4C719616F503F5CFA28163403E273D55959ADA6646E3CE6970C02AF081E
attendance 4F0B130FEF1022A129481C318A4EC65F87002B05CC4574A9BABB02F860988264
auth       9F54917F02BCD6CAE82011CA97E3730B85DA70DCF35C7BF040CDE0084D14150E
mobile-bff FCF9E9B58BB3DCFF4AAA2028C7CE3DFB82E553654982C6AB2F479F727B56D025
notification ACC5FD037F29DF0E6D316C63601C81AD302E452D91E8DB365B882732E0BF7FBD
schedule   12465DAA6BD52CF8A18585D3EDC9EEFFC4A4F21971F8D716943AD2F7422B7170
```

The first post-resume renderer command intentionally selected only
`OfficeDocumentConverterTest`; it reproduced the gate at `45%` and exited `1`
because the report lacked coverage for the other handwritten renderer classes.
The correction was to run the full renderer test task; that command exited `0`
without changing source or floors. This diagnostic is part of `checks.json`.

The dependency writer's immutable handoff remains preserved under the separate
dependency-security worktree. Its pinned rootfs scan covers eight rebuilt JARs
and 1,141 Java package records: the HIGH/CRITICAL gate exited `0` with zero
HIGH and zero CRITICAL; the complete report exited `1` as expected because 57
MEDIUM records remain. Source/staged JAR hashes are 8/8 equal. Evidence:

```text
C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/dependency-security/.agent/student-role-02/runtime/checks.json
C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/dependency-security/.agent/student-role-02/runtime/vulnerability-correction-rescan.json
C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/dependency-security/.agent/student-role-02/runtime/bootjar-hashes-post-correction.json
```

No production deployment, external notification, real push endpoint, secret,
database volume, backup or running application was touched. Task-owned Docker
containers are absent after the runs; a Gradle background daemon may remain as
an ordinary build cache process.
