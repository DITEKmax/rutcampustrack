# Current e58 finite CI reconciliation

Producer PR head: `e58d7921356c00ccc8ff95b17ef50507dcbb4d70`. All 30 executed jobs independently printed checkout `6ee8f269e0ff5e3035cf01a899ae70ce65006fd9`. All four runs were `pull_request`, attempt 1. PR 2 remained draft/open/unmerged, base main `3908fad9624f97aec8891fc79338ceb6b0616bf3`. This receipt establishes finite executed results; overall product acceptance is NOT_ASSESSED.

| Run | Actual conclusion | Qualification |
| --- | --- | --- |
| CI 38216670757 | FAILURE | Python formatting and Academic integration job failed; Academic unit and other Java jobs succeeded. |
| Coverage 38216670775 | FAILURE | Academic integration invocation failed; diff coverage skipped. Python and Vue coverage jobs succeeded. |
| Security 38216670738 | SUCCESS | Config Trivy, filesystem Trivy and Gitleaks executed; built-image Trivy skipped. |
| OpenAPI 38216670773 | SUCCESS | Generated types drift check executed at the verified merge checkout. |

Academic unit: 465 PASS, 0 failed/error/skipped, 46 XML reports. Academic integration: 445 PASS, 1 FAIL, 0 error/skipped, 66 XML reports. The sole failing method is `BotCredentialBrokerBridgeIT.actualProducerBrokerClaimAckRetryRestartAndDlqPreserveExactCards`. Both CI integration and Coverage native logs report `java.io.IOException`, caused by `java.io.EOFException`, at `BotCredentialBrokerBridgeIT.java:417`. Producer source SHA-256 `8660f1e49daa90dcfb4319ab9b4aa6634f2acf76021878c18bb09a38bfd1e05e` identifies `createOwnVhost` (declaration line 407, invocation 133, HttpClient.send line 417): Rabbit management provisioning failed before the seven bridge scenarios. Their execution is not established; this is not application/product-red evidence. Both current SubjectListDataIT methods passed, source SHA-256 `33a92858e46796645b55edb640c07fb803cbc479ad5abf9bbe4550688cfdd821`.

Auth: 140 unit and 169 integration PASS, no failures/errors/skips. All eight current AuthOtpFlowIT methods passed, including both previously corrected methods, source SHA-256 `8fe4478d887cc346770d2678ca8ad2aadf59ca3ca19016aca1f4af20002485eb`. Attendance: 315 unit/220 integration PASS. Notification: 125/62 PASS. Schedule: 272/205 PASS. Gateway: 168 unit PASS, 9 integration PASS and 3 SKIPPED ordinal-only cases in CompositeLoginKeyResolverIT. Existing skipped-case source SHA-256 `e1629a9f6be55269f7ab355b3a677dab9b61e00c7493b384df296d7741d723b3`. Unresolved ordinal-only identities remain explicit in the native accounting.

Ruff lint passed; formatting failed for 11 named files, with 99 already formatted; CI Python tests skipped. Exact public paths are in ruff-formatting.json. Coverage Python independently executed 415 PASS. Vue coverage executed 113 files/1028 tests PASS.

Chromium job succeeded with 9 scheduled cases: 8 clean PASS and 1 flaky, passing retry #1. The initial failure was `admin bootstrap requires explicit role selection in the Vue client`, `auth-token-lifecycle.spec.ts:50`, assertion `expect(locator).toBeVisible()` at line 59:79. Source SHA-256 `fda9b934deff0e4f4ad439bd13c2aabbb8d96101bbbe86343ed57988dae9a61a`. Isolated stack boot, synthetic teacher link and cleanup succeeded; failure-only reports/log uploads skipped. This qualifies the Vue/backend smoke seam, not all eleven client groups.

All 12 native safe artifact ZIP digests match GitHub metadata. Every XML declared/observed count is consistent; all current producer/head/event metadata matches. All 340 collector-referenced public source hashes were independently checked against producer e58. Safe metadata retains artifact IDs/digests and explicit job/step skips. No raw XML or raw logs were exported; signed URL metadata remains private and excluded. Prior CI731, ced, capacity correction and alias archives retain their exact previous hashes.

Automatic approval review rejected the raw Academic artifact download before execution, stating that this task was limited to safe receipts. Collection completed through safe collectors and narrow native log exception types; no raw download retry or workaround was performed.
