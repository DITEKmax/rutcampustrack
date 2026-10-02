# Maps lifecycle — scoped evidence

Writer: d_maps_finish_1002, GPT-6.1 Sol high. Risk S3 (authorization, atomic final deletion and concurrency).
Worktree: C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/maps-delivery-20260921.
Base: ab6888c0; source freeze: a4de00fa (b6951108 product + ebb2c371 race regression + a4de00fa mechanical client import/EOF).
RULES: C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA.

Goal: ADMIN edits building/floor code and label; ADMIN sees deletion consequences, confirms current password and permanently deletes one floor with all its versions, assets and floor demand; empty buildings can then be removed.
Context/evidence: accepted publication, replacement, access and per-opening idempotency reused. Owner 2026-10-02 explicitly chose final deletion, superseding retained published-history deletion guards for this operation only. docs/wireframes/admin/135-admin-map.md requires password and preview, disappearance of the floor for three reader roles.
Relevant scope: Academic map API/DTO, map package, V43 and three existing map test files. Auth confirmation endpoint owned by separate author B; exact contract below.
Required behavior: unchanged immutable floor identity during updates; duplicate code conflict; floor preview counts all versions/assets/bytes, demand and remaining floors; live password/session proof on every deletion request and retry; digest and operation UUID bound to author/type/id; one PG transaction deletes all target rows and writes completed receipt. Failure leaves published plan and bytes intact. Other floors survive, including assets with equal content hashes. Populated building cannot be deleted by a hidden cascade.
Constraints: backend only, no UI/shared protobuf/semester/Auth changes, no production data deletes, no push/deploy/main integration performed.
Existing patterns: same JDBC repositories, RequireRole ADMIN, validated RequestContext, existing Auth confirmation issuer header and RestTemplate deadlines, @Transactional JDBC proxy in existing PostgreSQL test harness.
Acceptance criteria: update preserves current version; duplicate 409; stale preview 409; nonADMIN/password/session failures cannot mutate; successful final deletion removes all floor-related content; exact receipt replay succeeds only for original actor/target/digest; fault rollback retains graph; delete and replacement serialize without resurrection; blocked publisher observes committed current version.
Verification: git diff ab6888c0 --check PASS. Run 20261002-123419 at a4de00fa, session 53481: Academic compileJava/compileTestJava and selected CampusMapAdminServiceTest/CampusMapUsageServiceTest/CampusMapReadServiceTest plus four named CampusMapReadRepositoryIT methods PASS, exit 0, 1m37s. Fresh XML: 77 selected unit cases + four PostgreSQL cases, zero failure/error/skipped. These counts identify executed evidence and are not readiness metrics. PostgreSQL 16 Testcontainer migrated through V43; fault cleanup, stale preview, another-floor isolation, replay actor binding and actual lock waits exercised. No production data was used.
Log: gradle-20261002-123419.log + .exit-code.txt. Fresh XML archive: xml-20261002-123419; structured results: checks-20261002-123419.json. Post-run read-only docker ps -a --filter label=org.testcontainers=true returned EMPTY; no owned processes remain and heavy lease released to root.
Independent source review PASS confirmed by root for ebb2c371; a4de00fa mechanical normalization acknowledged. Runtime passed without code corrections. HTTP through gateway + real Auth endpoint and main integration remain root acceptance tasks; source-ready, not independently declared end-to-end DONE.

Executed command (JDK C:/Users/maksd/.jdks/ms-21.0.10):
gradlew.bat :services:academic-service:academic-app:compileJava :services:academic-service:academic-app:compileTestJava :services:academic-service:academic-app:test --tests '*CampusMapAdminServiceTest' --tests '*CampusMapUsageServiceTest' --tests '*CampusMapReadServiceTest' :services:academic-service:academic-app:integrationTest --tests '*CampusMapReadRepositoryIT.adminLifecycleDeletesAllVersionsAndUsageWithBoundReplayAndPreservesAnotherFloor' --tests '*CampusMapReadRepositoryIT.stalePreviewAndMidDeleteFailureLeavePublishedPlanAndBytesIntact' --tests '*CampusMapReadRepositoryIT.concurrentReplacementSerializesBeforeFinalDeletionAndCannotResurrectFloor' --tests '*CampusMapReadRepositoryIT.concurrentPublishWaiterSeesCommittedCurrentPlanAndAdvancesVersion' --no-daemon --no-parallel --max-workers=1 --no-problems-report --system-prop=org.gradle.java.compile-classpath-packaging=true

No blocking findings remain in the frozen source scope. Next: integrate scoped commits alongside Auth B proof endpoint, then one shared server HTTP acceptance (ADMIN update -> preview -> password-confirmed delete; student/headman/teacher get 404 for removed floor and continue reading another floor).
Do not: regenerate contracts, change accepted view idempotency, broaden audit/suite, touch unrelated WIP or external resources.

Auth contract: POST /internal/auth/confirm-map-deletion, X-Internal-Issuer-Secret; JSON {internalToken,password,targetType:FLOOR|BUILDING,targetId:positiveLong,operationId:UUID,previewDigest:64hex}. 204 success; 400/401/403/429/503 failure. Academic owns operation/preview binding; Auth verifies current ADMIN session and password. No credential/ticket persistence.

Lock order: catalog advisory transaction lock (1381253965,1) -> floor FOR UPDATE -> fresh snapshot -> cleanup/revision/receipt. Reader manifests/plans/assets use coherent repeatable-read snapshots. Floor openings hold KEY SHARE before inserting intent rows; final deletion waits before counting and deleting. All files are currently BYTEA rows owned by immutable plan versions, with matching composite foreign keys; there is no external content-addressed shared blob store to clean asynchronously. Hash equality does not imply shared row identity.

Old WIP preserved before updated no-stash instruction arrived: Git stash 236a2ba7e39c121598accef861fdaeb1e57bba2e, message maps-old-wip-preserved-20261002, from this worktree at 1daa5b03. Not applied or dropped; no further stash/reset/clean.

Product/tests inventory below; no removed files.
M	services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/CampusMapAdminApi.java
M	services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/map/CampusMapAdminModels.java
A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/AuthMapDeletionClient.java
M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminController.java
M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminRepository.java
M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java
A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapDeletionRepository.java
A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapDeletionService.java
A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapDeletionTransaction.java
M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapReadService.java
M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageRepository.java
M	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageService.java
A	services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/MapDeletionDependencyUnavailableException.java
A	services/academic-service/academic-app/src/main/resources/db/migration/V43__campus_map_final_deletion.sql
M	services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapAdminServiceTest.java
M	services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadRepositoryIT.java
M	services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapUsageServiceTest.java
