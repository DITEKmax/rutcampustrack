# Independent current db177 CI receipt reconciliation

Verdict: APPROVE_FINITE_CURRENT_RUN_RECEIPTS. This is an existing-pipeline evidence reconciliation, not new execution, source approval of the other author's unfinished diagnostic package, or full product/client readiness.

Observed current local and PR2 head: db177beeb9660f5a17d7fadf660e024e28f9f4b9, OPEN/DRAFT/main unmerged, baseline3908. Four current workflow runs are completed SUCCESS on that exact head, event pull_request, attempt1:

- CI38308143277: https://github.com/DITEKmax/rutcampustrack/actions/runs/38308143277
- Coverage38308143127: https://github.com/DITEKmax/rutcampustrack/actions/runs/38308143127
- Security38308143200: https://github.com/DITEKmax/rutcampustrack/actions/runs/38308143200
- OpenAPI38308143148: https://github.com/DITEKmax/rutcampustrack/actions/runs/38308143148

Complete first-page API job collections match total_count: CI18, Coverage9, Security4, OpenAPI1 =32 jobs;31 SUCCESS,1 conditional Trivy built-images SKIPPED. All job APIhead_sha values are db177. Job APIhead is not actual checked-out merge evidence. The two downloaded current CI SAFE integration artifacts directly record checkout6930e93ee38ce2c1db256a9e7debc8d019f158d7, distinct from PRhead. Git commit metadata independently binds merge parents3908/db177; its tree313bd79864065012f0084d814f1eed7d8f6b4ace equals the db177 tree. Actual checkout for other jobs is not directly recorded by the inspected SAFE artifacts and is not invented from APIhead metadata.

Academic artifact11773868097: original ZIP21411B/SHA bd9a35b9fdc2a4ed7a04d83d6998282b147c51b0bd4b817c135400683bde175e matches GitHub digest; one safe-outcomes.json263894B/CRC valid. Schema1 RESULTS/acceptance NOT_ASSESSED; serviceacademic/integrationTest/CI38308143277/attempt1/pull_request/testStepOutcome success. Native446 PASSED/0 FAILED/ERROR/SKIPPED/NOT_EXECUTED. Exact unchanged AcademicGrpcIT.enumDatabaseAndHttpBoundaryRemainCanonicalUnderTurkishLocale is unique ordinal28/1/12 PASSED, sourceSHA dd0c466926911d99879fcdb59dd94641ca810bb714992d8fda7bc470b409010d.

Schedule artifact11773546666: original ZIP10770B/SHA df85df750afe09fd0f0c0d383a529dad1e2e34d7401331cc9b5b73924757b5c8 matches GitHub digest; one safe-outcomes.json125324B/CRC valid. Same current head/actual checkout/task/run/event/attempt provenance. Native205 PASSED/0 FAILED/ERROR/SKIPPED/NOT_EXECUTED. Exact RecurringScheduleItemIT.capNarrowingCommitIsFreshBeforeBlockedPhysicalInsert is unique ordinal23/1/7 PASSED, sourceSHA155e101972daeec228def4dd5b89e1f405a21f24fbf6e1909e612e6418e74723.

Closed SAFE schema keys/status counts/target identities/source mappings and suite count consistency were checked. No raw XML or logs, failure messages, private bodies/ENV/keys were downloaded or read. The SAFE xml array contains only ordinal/hash/count metadata. The two original SAFE ZIP bytes are preserved. Temporary download links are not retained in these receipts.

Target current CI jobs115007066488 Academic and115007066485 Schedule step9 are SUCCESS; step11/12 SAFE collection/upload SUCCESS. Their raw report upload10 SKIP is not a native test skip. Schedule conditional Academic-bridge setup7/8 SKIP is expected matrix scope. Coverage Schedule115007065513 step8 and Academic115007065827 step8 SUCCESS, JaCoCo summary/upload SUCCESS; Diff coverage actual6 SUCCESS. Coverage artifacts contain JaCoCo, not SAFE JUnit case outcomes, so the exact CAP case within Coverage is NOT_SEPARATELY_SAFE_RECORDED. No coverage case count/actual checkout is fabricated from gate success. Native case skips were checked only for the two requested SAFE suites; this does not assert zero skipped cases across all other Java suites.

Source96→db177 diff is exact99 task-document/evidence paths, no executable/product/build/gate changes. Git blobs of target tests, production49db, existing collector and four workflows are byte-identical. D011/TASK75ee has unchanged previously-read full88529B contract prefixe7 plus the newly read process-steering append; decisions/questions/resources/current were restored. D011 changes no product/security/gate criterion.

Historical96 Schedule Coverage38302629088 FAIL remains preserved as3e3a/08d4: underlying captured Throwable/SQLState/stage UNKNOWN and source-only H1 lock-cycle hypothesis unclosed. The new current CI CAP PASS and current Coverage gate SUCCESS do not identify the historical cause, label it flaky, prove a fixture correction, or permit expectation/timeout/lock weakening. The distinct sole-author four-path diagnostic WIP was neither read nor changed.

No source changes, workflow triggers/reruns, tests, Gradle/JVM/Docker/Node/browser/stand or resource grants were started. Only read-only GitHub metadata/SAFE download and harmless schema/Git inspection occurred; own compact safe outputs only. Requested/selected Sol6.1/xhigh, actual UNKNOWN. Full client/matrix acceptance, old three client causes and final DONE/D009 remain separate. A later product/test/gate head invalidates using these as its direct current-run receipts; retain exact producer/checkout identities when qualifying unchanged documentation-only evidence.
