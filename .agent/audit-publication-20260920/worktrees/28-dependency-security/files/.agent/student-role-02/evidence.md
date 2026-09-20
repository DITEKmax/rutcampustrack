# Dependency security evidence

## Revision and ownership

- Baseline: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`
- Branch: `codex/student-role-02-dependency-security`
- Working tree: isolated dependency-security worktree.
- Code diff: exactly 26 expected paths; no test, snapshot, generated contract,
  or business source path is changed. The machine-readable proof is
  `runtime/git-scope-diff.json`.
- Task evidence is kept under `.agent/student-role-02/`; no commit was created.

## Implemented dependency behavior

The original frozen contract updates the backend dependency family and gateway
configuration. The second-scan correction adds only these bounded manifest
changes:

- Notification constraints align `org.asynchttpclient:async-http-client` and
  `org.asynchttpclient:async-http-client-netty-utils` to `2.15.0` while keeping
  `nl.martijndwars:web-push:5.1.2`.
- Notification raises `org.bitbucket.b_c:jose4j` from `0.7.9` to `0.9.6`.
- Attendance raises `org.docx4j:docx4j-JAXB-ReferenceImpl` to `11.5.14`, which
  selects `docx4j-core:11.5.14` against docx-stamper's older request.

`runtime/dependency-insight/*-post-correction.txt` and
`runtime/notification-runtime-dependencies-post-correction.txt` show the
resolved graph and each `BUILD SUCCESSFUL` result.

## Defect gate

The Spring Boot 3.5/Springdoc binary mismatch was reproduced as a
`NoSuchMethodError`, recorded with request, reproduction, evidence, correction,
and recheck in `dependency-security-defect-gate.md`. The bounded Springdoc
correction to `2.8.9` passed the focused Academic contract test.

## Runtime evidence

- `notification-webpush-focused-escalated.log`: focused
  `WebPushDeliveryServiceTest` passed (`BUILD SUCCESSFUL`, exit 0). Its
  `PushService` is a Mockito mock and endpoints are examples, so no external
  notification was sent.
- `attendance-docx-focused-escalated.log`: focused `DocxRendererTest` and
  `HeadmanWeeklyTemplateSmokeTest` passed (`BUILD SUCCESSFUL`, exit 0).
- `bootjars-post-correction-escalated.log`: all eight backend `bootJar` tasks
  passed (`BUILD SUCCESSFUL`, 77 tasks, exit 0).
- `bootjar-hashes-post-correction.json`: SHA-256 and byte counts for all eight
  rebuilt jars.

## Security scan evidence

Scanner: `aquasec/trivy@sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969`
(Trivy 0.74.0), Windows host/Java 21, network `none`, read-only eight-JAR
input, task-local cached vulnerability and Java databases.

The first covered baseline report (`trivy-reports/backend-rootfs-all-2026-09-07.json`)
had 1,137 package records and 4 HIGH findings (0 CRITICAL). After the bounded
correction, the covered rootfs report has all 8 root JARs, 1,141 package
records, 0 HIGH, 0 CRITICAL, and 57 MEDIUM records. The final severity-gated
rootfs report filters to HIGH/CRITICAL and exits 0 with all 8 root JARs and 0
vulnerability records. Report hashes, IDs, package counts, and vulnerability
IDs are in `runtime/vulnerability-correction-rescan.json`.

A first retry used `filesystem` and returned an empty result with zero detected
language-specific files; that report is retained as invalid-coverage evidence.
Trivy's documented post-build Java mode is `rootfs`; the corrected rootfs run
is the accepted scan. The invalid invocation is not counted as a security pass.

## Limitations and preserved findings

The earlier full clean check had eight unrelated failure groups: five OpenAPI
snapshot semantic/format drifts, the Mobile BFF gRPC test-only bind failure,
document-renderer JaCoCo coverage below its existing threshold, and a
shared-events coverage whitelist gap. Root's semantic comparison and the
preserved XML/log artifacts remain available; snapshots, tests, thresholds,
and domain code were not changed. The focused dependency/runtime checks and
post-correction bootjar/security gate are independent of those failures.

No Terra escalation was used: the recorded issue was corrected within the
bounded Luna implementation scope, with no repeat-only failure gate.
