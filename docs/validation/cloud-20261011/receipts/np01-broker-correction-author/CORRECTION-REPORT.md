# NP01 broker source corrections

Frozen correction `cb778b155cab29c02abf294ba2093cf9149bed05` modifies only the two new broker harness files from original `9f7b0a1aed69def20efa7c336c108af0073b568e`. Original source, findings and plan snapshots are preserved. Old24 domain/test bytes and prior65 DOMAIN1 archive are exact.

BR-T01 captures separate old and NEW foreign (chat,message) references and full text. A harmless source-extracted helper counterexample reproduces the old oracle accepting wrong NEW deletion and demonstrates the corrected oracle rejecting deletion or text changes with a positive exact-card control. Native provider sensitivity remains proposed, not run.

BR-G01 clears Java-created supervisor/keytool environments and explicitly maps own synthetic inputs. Worker inherits only 11 required NP01 inputs plus nine fixed/runtime own fields. Pure fake-parent counterexamples omit unrelated credentials and JDK/Python override channels; no real parent values are read.

BR-G02 registers only a worker whose actual parent is the owned supervisor and captures its ProcessHandle exit future at start. Supervisor handles TERM once, kills/waits/reaps its own worker while alive, and reports terminal callback. Java first sends TERM and waits, then if necessary forces only owned workers and awaits terminal state before parent force. Unsettled cleanup fails explicitly with own PID metadata. Actual signals/reaping remain NOT_RUN.

BR-R01 binds every declared PG/Rabbit/Redis exposed port to 127.0.0.1 with ephemeral port0 and disables publish-all. Each container must pass actual inspection of all bindings immediately after start. No Docker inspection/start has run in this correction phase.

Seven observable cases retain the distinct released-claim restart and add held-processing-lease crash. The latter requires actual default300s, initial PTTL270000..300000, original processing/unacked/PENDING/exactowncard before provider effect, owned SIGKILL+reap, fresh worker with >=two native redeliveries and unchanged ACK, natural lease expiry then exact delete/SQLCLEANED/Rediscompleted/ACK/emptyqueue. Exact old/NEW foreign cards survive. The supervisor is bounded to600s; toolpolls≤60s. No lease shortening or supervisor Redis mutation.

Actual pure oracle/env counterexamples, Python AST, 12 static source controls and scoped source hooks passed. Four relevant hooks PASS; YAML/JSON no-files SKIPPED. All Java compile/link, Docker/network, native broker/worker/provider and full Academic IT checks are NOT_RUN pending independent recheck and separate runtime lease. AuthHTTP and external Telegram remain outside this harness. See seven-case plan for resource, synthetic data, clocks, cleanup and proposed controlled provider counterexample.
