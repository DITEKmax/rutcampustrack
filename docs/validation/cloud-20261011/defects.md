# Defect ledger

Baseline3908fad9624f97aec8891fc79338ceb6b0616bf3; synthetic credentials only.

- SEC01 / L03 / JS-SYSTEM13 / P17: RULED_OUT. Actual QR advice safe400/no-store/no proof echo. Initial standalone fixture missed production advice; harness corrected, product unchanged.
- SEC02 / L04: DEFECT P2. Turkish default locale changes persisted ADMIN code and ACTIVE parsing/query; canonical expected-code assertions fail. Cause default-locale case conversion. No DB migration; author fixed in8a9d111d, focused runtimePASS and independent bounded reviewPASS; root integration next.
- SEC03 / TMA auth: DEFECT P2. Actual signed missing/non-numeric/overflow date500; negative/future timestamp accepted200. Cause unguarded parsing/one-sided freshness. D003 preserves valid reuse24h with live authority. Security writer correcting.
- SEC04 / JS-HEADMAN16/17 / MANAGE_HOMEWORK: DEFECT P2. Granted own-group assistant denied by actual signed RPC (false). Cause missing permission in closed RPC whitelist. Fresh grant/revoke/foreign boundaries must remain. Security writer correcting.

Evidence: receipts/security-diagnostics and safe bounded incoming author receipts. Unit baseline session62079 exit1 included fixture error; actual HTTP/RPC session88834 supplies distinguishing results.

- DEL01 / L02: DEFECT P2 confirmed baseline by SemesterCacheRequestOptionsIT3/3 relevant failures/no skips. Missed semester change rejects valid new-semester lesson; missed archive and Academic outage return stale old options. Actual cache+gRPCclient+Mongo/domain, controlled Academic seam; not whole JWT/AcademicDB path. Root full-stack matrix still required. L01 initial fixture header error is UNKNOWN, corrected real-broker red retry session62110 active.

- DEL02 / L01: DEFECT P1 real broker baseline confirmed. Committed adminHTTP/PGoutbox3 intents markedSENT despite blocked broker, exactpublisherconnection killed beforeunblock, queueabsenceassertPASS, senderfailureNone. Knowndefect sender/config reproduction validred5 session30612 terminal1; minimalconfirmedpublication correction warranted, routing/durableconsumer semantics distinct.
