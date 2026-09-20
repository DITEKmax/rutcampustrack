# Paused source manifest

Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

Repair-owned changed paths at pause:

- `event-schemas/excuse.decided.json`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinEventPublisher.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestModels.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java`
- `.agent/student-role-02/requests-domain-review-repair/{checkpoint,checks,evidence,manifest}.md`

All other dirty paths are the preserved frozen implementation baseline or its prior evidence and are not modified by this repair turn.

## Resumed repair manifest — current scope

The paused list is superseded where this repair added focused tests. Repair-owned code/test paths are now:

- `event-schemas/excuse.decided.json` (frozen repair schema correction)
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinEventPublisher.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestModels.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestServiceAuthorizationTest.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinEventContractTest.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisherTest.java`
- `.agent/student-role-02/requests-domain-review-repair/{checkpoint,checks,evidence,manifest}.md`

The Mongo outbox collection used only by `StudentRequestDomainIT` is random-run task data and was created by Testcontainers; no existing runtime, volume or project data was cleaned. The test's final configuration uses production `MongoOutboxStorage` but does not modify `shared-outbox` source.

Final source hashes and accepted checks are in `evidence.md` and `checks.md`. `git diff --check` exits zero; the worktree stays deliberately dirty because it contains the preserved 30-file frozen implementation baseline plus this bounded repair. Independent recheck has not yet occurred.
