# Goal
Correct actual NP02 accepted OTP delivery loss: provider HTTP500 returns normally and consumer records completed plus ACK with zero delivery/DLQ.
# Context/evidence
RestorefullTASK-CONTRACT/CURRENT/decisions/questions/resources; baseline3908/frozennotification-provider e093/root e587. Actualprobe90021 realRabbitRedis/productionconsumer+dispatcher/aiogramlocalHTTP500: attempt1/success0/dedupcompletedtrue/mainACK1/DLQ0, meaningfulcounterexample. Earlier62839infraKeyError recordedseparately correctednewharnessonly. Soleauthor security_diagnostics Sol6.1/high actualUNKNOWN.
# Relevant scope/writer
Explicit narrow PRODUCT extension ONLY services/notification-bot/bot/notifications/otp_requested.py provider-failure propagation and own assignedNEWtest/harness files or necessary EXISTING OTP/provider tests with justifiedspec oracle. Other bot/consumer/queue production read-only until concrete additionalfinding/rootextension. No Academic/proto/API/config/locks/exportWS/Clock changes; NP01transport stillproposalonly.
# Required behavior
Do not complete/dedup/ACK an undelivered event as success. Preserve current consumer's accepted persistent claim/release/transaction/DLQ behavior, safe malformedpurpose/code and expirycleanup rules. Propagate a safe bounded error without OTP/resetchallenge/message body/providerexception string or traceback cause. Existing logging onlypurpose/type remains safe; avoid catchingthenreturn or fullprovidererrorlogs. Use current provider/DLQ contract; do not invent new automaticretry count/backoff/SLA. If acceptedsource mandates additional provider retry, identify exactsource and proposedexisting mechanism instead of treatingmanualrecovery as automaticretry.
# Constraints
NOTIFY3 retainedmaxworker1 alongsideEXPORTS1max1, isolatedRabbitRedis/providerephemeralports/labels. No actualTelegram/privatebot; synthetic values stay outrootreceipts/logs/artifacts. Save redactualcounts/sourcehash/commands andafteractualsamefailedprovider durableDLQ/claimrelease/noncompletion. Observe successful laterredelivery withsameevent afterlocalproviderrecovery anddurableeffect/completion/dedup, withcleanup/repeatlimits. Never print originalsecret-bearing brokerpayload.
# Existing patterns
Consumer catchesbaseexception, releasesclaim andpreservesdurableDLQ; genericbusinessdelivery existingbounded queue remains unchanged. Root solecommoncontract/docs/integrator.
# Acceptance criteria
Meaningfulredalreadymeasured; exactminimalcorrection, actualfailedprovider nowretained/DLQ andnotcompleted, providerrecover+controlledknownownedredelivery succeeds andduplicateeffectprevented. Review checks logging/privacy/assertions andqualifiedautoRetryboundaries. Frozenstablecommit+safeimmutablepacket forfreshreview beforeintegration.
# Verification
Actual source/argv/data/outcome/exit/hash receipts; no mocks claimrealprovider/loss. RelevantexistingOTP tests andparser/lint sufficient; no blanketfullauth/notificationPASS.
# Do not
No no-store/grants/initialpassword productrule/protocol edits, no secretpayload artifacts, no security/tls/tests/gate weakening, no productionbackup/deploy/main operations, no ownreviewfix/children.
