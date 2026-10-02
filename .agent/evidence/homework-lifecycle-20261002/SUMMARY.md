# Academic homework lifecycle — final handoff

Risk S3, scope Academic/V45 + additive academic.proto only. Stable source701e6d0f; no Schedule writes after handoff3264cb7d. Proto092e5006 consumed separately by root/BFF. Root integrated full source chain main2ca6fc7e with exact Academic/proto diff empty. No main/push/deploy/production migration by leaf.

User result: authorized current same-group headman/assistant edits another publisher's homework and placement; identity/publisher/completion survive, exact receipts prevent repeated history/outbox. DATE create needs no lesson; Moscow next-day midnight read-only is immediate before terminal event. Archived group-readable history stays reachable. Original and legacy accepted CREATE replay remain stable after edits. Exact forward recovery/Abort CAS/ACK respects captured semester archive; terminal wins.

Source chain after proto: c8d83540, da05202b, a56ae677, 6bf69910, e4baf430, 86e386f9, 701e6d0f. Independent source and affected corrections PASS. Last fixture commits change test isolation only;701e6d0f canonical JSON fix preserves V45 exact SQL guard. No deleted files; obsolete EventIT update scenario moved to cohesive boundary, distinct producer coverage retained.

Checks: production/affected test compilation PASS; HomeworkServiceTest11PASS R1. PG continuation/PREPARE/revocation/old marker supersession PASS R2. Four criteria PASS R3: nonauthor permissions/identity/completion/receipts/history/outbox, Abort tombstone, DATE terminal-before-finalize, DATE create/replay. Legacy fingerprint/immutable create replay/effective midnight/read-only completion/history PASS R4. Session7643 terminalexit0/build1m03s/XML1test0fail. Failed attempts retained: R1 subject/type fixture, R2 overlapping semesters, R3 actual JSON date mismatch. No SQL guards disabled, no accepted criteria repeated. All containers fresh/nonreused stopped; final docker ps exit0 empty; Tomcat/JPA/Hikari shutdown complete. Heavy lease released. See checks.json, r1–r4 logs/start/exit/raw XML.

Limits: Academic boundary mocks Schedule RPC; combined network acceptance belongs to root G on frozen main. Frontend edit adapter deferred: old three-field PUT400 until key/revision supplied. Legacy fingerprint preserves accepted input, not unknowable original modified content. No new framework/globalstatus or broad suite. Foreign headman/auth/user-archive evidence intact.

## Exact source inventory (A created, M changed)
- M	proto/academic.proto
- M	services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/HomeworkApi.java
- M	services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/homework/CreateHomeworkRequest.java
- A	services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/homework/HomeworkHistoryResponse.java
- M	services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/homework/HomeworkResponse.java
- A	services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/homework/HomeworkSnapshot.java
- M	services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/homework/UpdateHomeworkRequest.java
- A	services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/enums/HomeworkBindingMode.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/entity/Homework.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkBindingArchivedEventConsumer.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkPublishedEvent.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkUpdatedEvent.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/ScheduleGrpcClient.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkAssembler.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkBindingTransferCoordinator.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkController.java
- A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkCreateIntent.java
- A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkEditCoordinator.java
- A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkEditHistory.java
- A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkEditOperation.java
- A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkEditPersistence.java
- A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkEditRecoveryJob.java
- A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkLifecycle.java
- A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkManagementAuthorization.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkPublicationPersistence.java
- A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkScopeValidator.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/AcademicSemesterArchiveBarrierTransaction.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/AcademicSemesterDeletionSnapshotReader.java
- M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterArchiveCoordinator.java
- A	services/academic-service/academic-app/src/main/resources/db/migration/V45__homework_edit_history.sql
- M	services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkBindingArchiveIT.java
- A	services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkEditLifecycleIT.java
- M	services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkServiceTest.java
- M	services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIT.java

