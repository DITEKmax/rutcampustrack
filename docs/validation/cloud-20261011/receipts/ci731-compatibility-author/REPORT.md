# CI731 accepted-v2 and fixture compatibility source corrections

Frozen source `ff43303e59f424f999ce7d02e2a68a48bbaebdc1` modifies exactly six authorized files atop frozen broker `cb778b155cab29c02abf294ba2093cf9149bed05`. Broker two Git blobs and original20/65 sealed archives remain byteexact. No product behavior, schema, migration, config, generator, lock or gate changes.

Current remote CI731 reported two AuthOtpFlowIT expectations of payload size1 against accepted v2 actual4 and292 Academic fixture inventory exceptions before reset/test execution. Original safe diagnostics are retained here; they are the before evidence.

AuthOtpFlowIT replaces only those two expectation blocks. Each now checks version2, exact four-field metadata set, original expected student UID, positive credential/binding generations and current recipient against the persisted user snapshot. Existing schema, known Telegram ID and absence-of-password/reset/code checks remain. Reset also checks changed-password/reset-ticket values with a boolean assertion that prints no value. Every other byte of this Auth test, including purpose, race, atomicity, session revoke, broker restart/retry and history assertions, is unchanged. New expectations have not compiled or run yet.

AcademicFixtureResetListener adds only explicit V57 `bot_credential_messages` to the closed TABLES inventory. All fresh-owned-DB checks, inventory mismatch denial, fixed TRUNCATE list without CASCADE, fixture transaction/rollback and Redis ownership checks remain byteexact. Reset execution is not yet verified after this change.

Ruff corrections are four sorted import blocks and three wrapped lines in already-owned Python files. Cached Ruff0.11.13 scoped check native0. Four Python nonimport ASTs and import multisets match their originals; config/rules unchanged. Six cached source hooks native0: four PASS, YAML/JSON no-files SKIPPED.

Six relevant producer/contract/legacy proof sources are byteexact frozen aa713. Historical DOMAIN1 13unit/22PG/71Python PASS remains preserved for its tested inputs, including accepted v2 producer/rollback and explicit v1 compatibility. This is source provenance and finite-proof disposition, not a new current full-suite runtime result. New Java expectation/reset behavior requires fresh review and actual full current suites. Broker, AuthHTTP and external Telegram qualification is unchanged by this packet.
