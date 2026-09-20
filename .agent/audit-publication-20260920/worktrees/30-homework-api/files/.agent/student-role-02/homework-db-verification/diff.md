# Scoped diff

The leaf-owned scope is limited to:

- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java`
- `.agent/student-role-02/homework-db-verification/*`

The Academic file adds a real PostgreSQL concurrency/authz integration test.
Its only follow-up edits were fixture corrections driven by reproduced schema
errors: omit the removed `groups.code` column and keep generated logins within
the existing 32-character bound.

The BFF file adds a focused random-port HTTP to local-gRPC boundary test. The
evidence files record the contract, runtime results, failed fixture
reproductions, checks and handoff state.

The shared worktree also contains the parent API/UI changes and other
untracked product/test files. They predate or belong to the parent scope and
were preserved. In particular, `docs/openapi/mobile-bff.json` has existing
CRLF trailing-whitespace findings; whole-worktree `git diff --check` therefore
returns 2, while the assigned test/evidence scope passes its scoped check.

No commit, merge, reset, migration, dependency, lockfile, config, generated
file or production-code edit was made by this verification leaf.
