# Dependency security handoff summary

Status: implementation and verification complete within the frozen S3 scope.

The 26-path dependency/configuration diff keeps the accepted Spring Boot 3.5,
Spring Cloud, Springdoc, gRPC/protobuf, root patch, and gateway migration
contract. The recorded second-scan correction is limited to four runtime
coordinates: async-http-client 2.15.0, async-http-client-netty-utils 2.15.0,
jose4j 0.9.6, and docx4j provider/core 11.5.14. No business code, tests,
snapshots, generated types, coverage settings, or other worktrees were edited.

Evidence of the stable source scope is in
`runtime/source-manifest-post-correction.json`, `runtime/git-scope-diff.json`,
and `runtime/diff-stat-post-correction.txt`. The code diff is 26 files, 478
insertions and 408 deletions; the large gateway YAML count is the required
indentation/migration change already in the contract.

Checks:

- Four post-correction dependencyInsight checks: exit 0, target versions
  selected; outputs are under `runtime/dependency-insight/`.
- Focused Springdoc contract, WebPush, DOCX renderer, and template smoke tests:
  exit 0; logs are under `runtime/`.
- Eight Boot JAR tasks: exit 0; hashes are in
  `runtime/bootjar-hashes-post-correction.json`.
- Covered Trivy rootfs scan: all eight root JARs, 1,141 Java package records,
  0 HIGH, 0 CRITICAL, 57 MEDIUM; the unfiltered command exits 1 because
  MEDIUM findings are present.
- Trivy HIGH/CRITICAL gate: exit 0 with all eight root JARs, 1,141 package
  records, and 0 vulnerability records. Scanner is fixed Trivy 0.74.0 digest
  `sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969`,
  network `none`, task-local cached DBs, read-only input.
- `git diff --check`: exit 0; scope comparison: 26/26 exact.

The malformed/empty filesystem invocation is retained as invalid coverage;
`rootfs` is the accepted post-build Java scan. The full-check failure index
remains open for root: five OpenAPI snapshot drifts, Mobile BFF gRPC bind,
document-renderer coverage, and shared-events coverage whitelist. This
handoff does not claim those unrelated checks as PASS.

No Terra escalation or commit was used. Root can review the frozen source
manifest, stable diff evidence, checks, runtime logs, and reports, then route
the independent review and any unrelated open findings separately.

