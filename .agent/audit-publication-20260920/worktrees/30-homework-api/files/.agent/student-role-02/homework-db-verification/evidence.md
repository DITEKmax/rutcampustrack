# Runtime evidence

Revision `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` on
`codex/student-role-02-homework-api` was checked in the assigned worktree.
The environment was Windows 11 amd64, OpenJDK 21.0.10, Gradle 8.12, Docker
Desktop 28.5.2 and Testcontainers 1.20.4.

The Academic test started a fresh `postgres:16-alpine` container on a random
mapped port. The runtime reported PostgreSQL 16.13 and applied all 23 Flyway
migrations. Its three tests passed. Eight independent Spring service calls
were released together for each desired state. `completed=true` left exactly
one row for the target student and preserved the other student's row;
`completed=false` removed exactly the target row and preserved the other row.
A claim for a foreign group was rejected before a completion write.

The first Academic run reproduced two fixture-only schema defects. The first
inserted removed `groups.code` and failed with `column "code" of relation
"groups" does not exist`; the second used a UUID-derived login longer than
`users.login varchar(32)` and failed with `value too long`. The fixture was
corrected to use the current `groups(name,is_active)` schema and a bounded
login suffix. No production file, migration, dependency, configuration or
generated file was changed for these corrections. The final exact Academic
command exited 0 with 3/3 tests passed.

The BFF test started Spring Boot on a random HTTP port and a bounded local
gRPC fake that validates the forwarded signed JWT. Its final run exited 0
with 5/5 tests passed. The positive test observed GET and PUT responses with
`Cache-Control: no-store`, claims-derived student/group identity, desired
completion state and ignored spoofed actor headers. Negative tests covered
missing and tampered tokens, wrong role, malformed id/date/body and gRPC
not-found/permission/unavailable mappings. The test records only claims and a
token digest; no token or secret is included in this evidence.

The first forced BFF rerun had 5/5 green test cases in the XML but Gradle
returned exit 1 while moving a shared `build/reports/problems/problems-report.html`
and raised `FileAlreadyExistsException`. Repeating the same test with the
documented `--no-problems-report` flag returned exit 0. This is recorded as an
environment/reporting issue, not a product defect.

`OpenApiSnapshotIT` ran without the update flag and exited 0 with 4/4 tests;
the checked-in Java-first `docs/openapi/mobile-bff.json` matched the runtime
document.

Startup logs included an existing Academic auth public-key connection error,
Mockito's dynamic-agent warning, and the BFF's optional Mongo localhost
connection warning. The scoped tests still completed their assertions and the
warnings were not linked to this verification request, so they did not cause
code changes.
