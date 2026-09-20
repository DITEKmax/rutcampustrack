# Runtime evidence

Revision under test: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` plus the 42 accepted working-tree paths. Environment: Windows PowerShell, Node `v24.14.0`, npm `11.9.0`, Java `21.0.10`, Gradle `8.12`, Docker `28.5.2`. Test containers and ports were task-owned; no production data or secrets were used.

`StudentHomeworkHttpGrpcIT` ran with the BFF on a random HTTP port and a task-owned in-process Academic gRPC server. Its XML reports 7 tests, 0 failures, 0 errors and 0 skipped. The scenarios cover signed student JWT forwarding, spoofed identity header rejection, GET/PUT completion timestamps and null undo state, historical-range handling, typed invalid input/dependency mappings and `no-store` responses.

`HomeworkStudentCompletionConcurrencyIT` ran the Academic Spring service against a fresh PostgreSQL Testcontainers database. Its XML reports 6 tests, 0 failures, 0 errors and 0 skipped. The scenarios cover concurrent desired-state completion, concurrent undo, foreign-group rejection, idempotent timestamp preservation, redo timestamp freshness, and the completed-today date union without yesterday leakage.

Focused unit XMLs also pass: `HomeworkStudentServiceTest` 6/6, `StudentHomeworkGrpcIdentityInterceptorTest` 2/2, and `StudentQueryHomeworkTest` 6/6. Frontend contract/navigation runtime tests report 11/11.

This integration gate does not claim a browser screenshot flow, real Telegram host, full student role, full security/dependency gate, requests domain, or main integration. Those remain downstream/root gates.
