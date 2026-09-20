# Scoped diff

Comparison is against the readonly frozen source snapshot
`.agent/student-role-02/requests-review-2-source`, revision
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

| Path | Delta versus frozen source |
|---|---:|
| `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java` | 95 insertions, 4 deletions |
| `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java` | 162 insertions, 2 deletions |

The service delta replaces the two decision call sites with a decision-only
retry helper, adds unknown-commit classification, and adds exact-match
read-only recovery. The test delta adds a post-success ACK fault injector,
EXCUSE and LATE_CHECKIN ambiguity cases, the pre-transaction transient case,
and terminal actor/outcome/comment mismatch assertions.

No other source, contract, config, lockfile, transport, or runtime path was
changed by this repair. The checkout remains dirty because it contains the
pre-existing frozen 32-file student-domain implementation and evidence; those
changes are preserved. This worktree has no repair commit.
