# cd3 current native receipts

Producer `cd3f237acffd4f0b9dd2a74babb2ee822376f0cb`, actual checkout **`314f67ff03438ce16351dccd58b66dfa4f957737`** independently printed by all31 executed jobs. All four workflows were pull_request/attempt1 and completed.

| Workflow | Actual result | Executed qualification |
| --- | --- | --- |
| CI38219224186 | FAILURE | Only Python formatting failed:11 files would be reformatted/99 already formatted; Ruff lint passed, Python CI tests skipped. All Java jobs succeeded. |
| Coverage38219224167 | SUCCESS | All9 jobs including Academic integration invocation and diff coverage executed. |
| Security38219224175 | SUCCESS | Config/filesystem Trivy and Gitleaks executed; built-image Trivy conditionally skipped. |
| OpenAPI38219224180 | SUCCESS | Generated types drift check executed at actual verified checkout. |

Twelve approved safe native artifacts have verified archive digests and consistent XML declared/observed counts. Academic465 unit/446 integration PASS; Auth140/169 PASS; Attendance315/220, Notification125/62 and Schedule272/205 PASS. Gateway168 unit PASS and9 integration PASS plus3 existing ordinal-only CompositeLoginKeyResolverIT skips. Total native Java safe cases2596PASS/0FAIL/0ERROR/3SKIP/0NOT_EXECUTED. This is finite execution evidence, not whole-product acceptance.

**All seven NP01 broker scenarios qualify as current finite PASS.** Artifact11734066681 digest `8a902dc2fb76a28159951a48952ee25e2b0eef95b830155f3c5f8f73cb4e0078` contains actual SOURCE_METHOD PASS for `BotCredentialBrokerBridgeIT.actualProducerBrokerClaimAckRetryRestartAndDlqPreserveExactCards`, XMLordinal9/suite1/case1. Exact Java producer source SHA-256 `bf44a550a966709fb266131bb1639c3ddb501f0b292df9c9c8b09eef3ed3e18a`; Python supervisor/worker source `7f2942cfc098b5ecfb0aea910429b582563a642d4356599f001e0d76875da9f6`. Both were independently reopened, together with critical actual consumer/Redis claim code. Java requires child exit0, qualified/failures0, two actual worker restarts, reaped workers, natural held lease expiry, real components and final zero own pending cleanup. Python executes every required phase sequentially and returns qualified only after those assertions and cleanup succeed. This conclusion uses source plus exact native outcome; a test name or green build alone would not suffice.

| Existing scenario ID | Proven required observable |
| --- | --- |
| NP01-BR-01 | Actual direct Auth credential transaction, metadata-only v2 outbox and publisher confirms; real Rabbit consumer cleanup/Redis completed/queue drained/ACK after zero PG pending and cleared initial. |
| NP01-BR-02 | Same produced event redelivery ACKs with delete/edit/notice effects unchanged. |
| NP01-BR-03 | Real unlinked reset retains original UID, omits recipient; exact old AND newly created foreign card keys/text preserved, own credential removed, no redirected notice. |
| NP01-BR-04 | Accepted edit/lost reply remains uncertain; refused deletion retains durable pending, actual retry/redelivery and unchanged ACK, no completed marker or unsafe sanitize. |
| NP01-BR-05 | Actual own worker killed/reaped after verified real claim release; original broker/Redis/PG/provider retain work; restarted worker completes cleanup then ACK. |
| NP01-BR-07 | Actual default300s held processing lease survives worker death; at least two redeliveries without premature ACK; natural expiry followed by exact cleanup/completed/drain/ACK while both foreign cards remain. |
| NP01-BR-06 | Version991 negative reaches actual DLQ/x-death rejected provenance; claim absent/not completed; NEW foreign card retained; inspected DLQ message acknowledged. |

The held-lease assertions independently require defaultTTL300, `270000 < initialPTTL <= 300000`, monotonic elapsed `>= initialPTTL-2000` and at least2 redeliveries. The supervisor observes actual Redis and never writes/releases/completes or shortens that held claim. While its exact processing token remains, ACK stays unchanged, pending stays positive and exact held/NEW foreign cards remain. After natural expiry the final observed ACK requires completed marker, zero ready/unacked and zero pending/outbox_pending; exact held card and credential marker are gone. Actual old and NEW foreign cards are checked throughout by exact key AND text, so another card containing the same marker cannot satisfy the negative.

Successful testcase stdout and individual duration are absent from safe collector/job logs: exact initialPTTL, elapsed, checks count and testcase duration remain UNKNOWN. Their required bounds and conditions are established by the exact passing assertions. Whole Gradle build elapsed was13m1s in CI and11m18s in Coverage; those are not individual test durations. Native setup records Ubuntu24.04 runner image, runner2.337.0, JDK21.0.12+1 and CPython3.12.15. Coverage independently succeeded through integrationTest, but the named XML outcome retained here is from CI's safe collector.

**Do not repeat the normal seven-scenario local broker run solely to replace earlier interrupted attempts.** This exact current native proof supplies it. Independent meaningful BR-T01/earlyACK sensitivity remains separate unless an exact accepted control already proves it; a relevant source/input change can justify recheck. The provider is an actual persistent loopback Telegram API emulator, not external Telegram. The producer is real JDBC Auth domain/outbox, not public AuthHTTP. Academic production components run with real PG and native TLSgRPC; this is not the complete Boot application/client stand. No whole server/client family or final DONE is inferred.

Chromium job114711154522 ran9 scheduled smoke cases, all9 cleanPASS in11.6s, no retry/flaky attempts; isolated stack boot, teacher link and cleanup succeeded. It proves the existing Vue/backend auth smoke seam, not all11 client groups. Python Coverage415PASS; Vue Coverage113files/1028testsPASS. Current named native outcomes also reconfirm all37 methods behind the prior23 source-qualified finite proposals, with unchanged test source hashes and original mode/limits retained. All three SubjectList/AcademicGrpc current finite methods passed. Comparing retained safe source hashes identifies only the BrokerBridge fixture source change from e58; only8 critical files were read/hashed, avoiding a repeated340-source scan.

Fresh PR2 readback was open/draft/unmerged with base main3908 and head `0753aec1fadea48e87e459552b9630f35170d2f9`, merge candidate `1b3c17e6a1a88f4bfe2779ab8472cfd8d5f27ac5`. These runs executed cd3/314f, not that later head or upcoming formatter source. D009 does not turn this failed CI into final green evidence. D010 keeps the Angular subtree untouched.

No raw artifact/XML/log export or retry after the earlier approval rejection. Only approved safe collectors and narrow native fields were used; own private download URL metadata was600 and removed after downloading. No local runtime, signals, children, product/source/shared-doc writes or CI reruns. Prior e58 safe archive and server qualification safe archive retain their exact hashes.
