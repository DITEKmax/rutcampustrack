# Checks — revision before commit `e583f05140c43311b547bbaeaa55c1a7bc3c9ce9`

Environment: Windows 11, Java 21.0.10, Gradle 8.12, CPython 3.12.13, Docker/Testcontainers Mongo 7.0. Elevated Gradle was required because the normal sandbox ACL cannot read existing shared project classes; the same command then compiled normally.

| Result | Command | Exit | Evidence |
|---|---|---:|---|
| PASS | `uv run ... pytest tests/test_callback_late_checkin.py tests/test_callback_excuse.py -v --override-ini="addopts="` | 0 | 24 passed; includes Telegram `123456789` → internal actor `42`, denied zero actor, and unchanged excuse flow. |
| PASS | `uv run --with ruff ruff check ...` and `ruff format --check ...` | 0 | Four modified Python files clean/formatted. |
| PASS | `python -m json.tool event-schemas/late_checkin.decision.json` | 0 | Schema parses. |
| PASS | `gradlew ...:test --tests LateCheckinServiceTest --tests LateCheckinEventContractTest --tests EventConsumerTest --tests StudentAttendanceSnapshotServiceTest --no-daemon` | 0 | 37 tests: EventConsumer 8, contract 5, service 21, snapshot 3; all failures/errors 0. |
| PASS | `gradlew ...:integrationTest --tests StudentCheckinTransactionIT --no-daemon` | 0 | Cached final invocation succeeded. The preceding real Testcontainers run has XML evidence: 20 tests, 0 failures/errors, including canonical actor, foreign group denial, and cancelled race no-op. |
| PASS | `git diff --check` | 0 | No whitespace errors. |

Runtime evidence: `StudentCheckinTransactionIT` started an isolated `mongo:7.0` replica set and ran the transaction suite. `botDecisionUsesCanonicalActorAndAuthoritativeGroupCheckInMongoTransaction` persisted actor `42`; `botDecisionForForeignGroupHeadmanMakesNoMongoWrites` left request, attendance, pair, and event publisher untouched; `staleApproveAfterGeoCancellationIsNoEffect` remained green.

Observed but not defects in this repair: the first focused pytest command without `--override-ini` reported all 24 selected tests passed, then exited 1 because the project-wide 50% coverage floor cannot be satisfied by a two-file subset. The original non-escalated Gradle run could not read `ErrorResponse` due sandbox ACL; no source change was made for it.
