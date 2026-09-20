# Evidence r4

## Revision and environment

- Target: C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-bff-contract
- Revision: d3c31acb8cce53791a4981e5858a37d44fdc9a0e (detached, pre-dirty)
- Environment: Windows PowerShell; XML host DITEK-PK; date 2026-09-09
- Frozen contract SHA-256:
  51A1760E1EE4A14BC9622BE5A8351F1BFD1268DB6F70056BAE8D0C8070372A08

## Current producer-chain evidence

The exact current SHA-256 values for all 11 final paths are in
final-manifest.json. The guarded re-hash matched 11/11. r2 changed only the
accepted NotificationResolution model dependency; r3 changed only the
StudentRequestDomainIT BSON ObjectId lookup. The dependency-imported
authorization test remains byte-exact at
D1DDCDE648C3186914BA52846973497F08D82BBA6C198743A5254EC37E8B0B22.

## Union and scope evidence

Accepted P1 manifest anchor:
6D822BB002AA0AF2FE768855FA8EA2CA2986DE379A1D3424029B18662EFD9DEE.
Accepted P2 exact-five manifest anchor:
61C14D95E6E8EA61BCE791EC648218C39FE9771E62E355605A2BAAF313722B97.
The P1 service import is preserved in the final service path; the authorization
test import is exact. Accepted P2 paths remain preserved except the explicitly
superseded services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentRequestApiModels.java
producer DTO, whose final hash is recorded in the manifest. No change outside
the frozen relevant scope is claimed; pre-existing foreign changes remain
untouched.

## XML evidence

All seven XML files are fresh root-supplied Gradle results. suiteTimestamp is
the timestamp emitted by Gradle XML without a zone; fileLastWriteTime is the
local filesystem timestamp (+03:00).

| Name | XML path | Count | Failures/errors/skips | suiteTimestamp | fileLastWriteTime | SHA-256 |
|---|---|---:|---|---|---|---|
| mapper1 | services/attendance-service/attendance-app/build/test-results/test/TEST-ru.rutcampustrack.attendance.grpc.StudentRequestGrpcMapperTest.xml | 1 | 0/0/0 | 2026-09-09T16:04:41 | 2026-09-09T19:04:50.1614460+03:00 | C73A0204AADD73B1337E7C1F8423D671B049C7C5CE7EB946ED7391C9C156BFDB |
| auth18 | services/attendance-service/attendance-app/build/test-results/test/TEST-ru.rutcampustrack.attendance.studentrequest.StudentRequestServiceAuthorizationTest.xml | 18 | 0/0/0 | 2026-09-09T16:04:49 | 2026-09-09T19:04:50.1586866+03:00 | FFDE29855693C264328B1271507B943E5D2A16541E1F6EFF8849688363E29FBE |
| domain20 | services/attendance-service/attendance-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.attendance.studentrequest.StudentRequestDomainIT.xml | 20 | 0/0/0 | 2026-09-09T16:40:11 | 2026-09-09T19:40:35.2370352+03:00 | 2CDC7909FAF29BE5EF4B1D5ECE70A6A53635E0C857B0E39CA49441F3E8535661 |
| Detail3 | services/mobile-bff/mobile-bff-app/build/test-results/test/TEST-ru.rutcampustrack.mobilebff.contract.StudentRequestDetailJsonTest.xml | 3 | 0/0/0 | 2026-09-09T16:42:40 | 2026-09-09T19:42:46.4277785+03:00 | D7FFD8215A46ECA8CCEAA0AF85DD6F424FC7E8F5CBFA496AB1E71E098E6948EF |
| OptionsJson1 | services/mobile-bff/mobile-bff-app/build/test-results/test/TEST-ru.rutcampustrack.mobilebff.contract.StudentRequestOptionsJsonTest.xml | 1 | 0/0/0 | 2026-09-09T16:42:40 | 2026-09-09T19:42:46.4323846+03:00 | B5296F0DD3E30801892CFF9BE35641E2EC4F0065D6D0E4D0BCAC8814D3018323 |
| MobileAttendance23 | services/mobile-bff/mobile-bff-app/build/test-results/test/TEST-ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClientErrorTest.xml | 23 | 0/0/0 | 2026-09-09T16:42:41 | 2026-09-09T19:42:46.4395355+03:00 | C14086A80C6FBF54AF68CC7F0FACDC94CE81BFDEB0BA3A02A831A52E27AD4A31 |
| Facade1 | services/mobile-bff/mobile-bff-app/build/test-results/test/TEST-ru.rutcampustrack.mobilebff.student.StudentRequestFacadeOptionsTest.xml | 1 | 0/0/0 | 2026-09-09T16:42:45 | 2026-09-09T19:42:46.4303777+03:00 | DF5653CA237C60B6067A59053ABD1C5B4FA1B32D22D7782F7FE9D0065F37EB0E |

Aggregate: 67 tests, 0 failures, 0 errors, 0 skipped.
