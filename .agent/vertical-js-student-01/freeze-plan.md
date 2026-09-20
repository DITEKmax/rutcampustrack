# Freeze and overlay plan

1. Contract owner materializes Java-first BFF records/interfaces and the minimum app
   export seam in the contract worktree. No product domain handler is added there.
2. Export normalized OpenAPI with `info.version=JS-STUDENT-01-r1`; validate Problem
   Details, auth scopes, null/empty/time/ETag/idempotency semantics.
3. Generate TS once from exported OpenAPI and generate contract-derived success and
   Problem Details fixtures. Run a second generation and require no diff.
4. Hash the exact contract delta and allowlisted overlay. FE and BE worktrees must
   report the same HEAD, contract hash and overlay hash before writing.
5. Contract owner records consumer inventory for `CANCELLED/GEO_CONFIRMED`; exhaustive
   switches/tests must fail if a consumer is missed.
6. Assign non-overlapping runtime resources. BE tests use dynamic Testcontainers;
   integration gets one exclusive task compose project and remapped ports. Existing
   fixed `container_name` values require a task-owned override/config delta before
   claiming isolation.
7. Offline policy is resolved: cache the server-scoped semester read model per user;
   expose it after expiry until explicit logout/switch, label offline/updatedAt, and
   never cache tokens or queue mutations.
8. Local commits/branches/merges are authorized, but root serializes all Git
   mutations. After checks/review root creates local `master` from `main` and merges
   verified integration there. No push.

Proposed worktrees/branches, all from the same HEAD:

- `.agent/worktrees/js-student-01/contract` / `codex/js-student-01-contract`
- `.agent/worktrees/js-student-01/fe` / `codex/js-student-01-fe`
- `.agent/worktrees/js-student-01/be` / `codex/js-student-01-be`
- `.agent/worktrees/js-student-01/integration` / `codex/js-student-01-integration`
