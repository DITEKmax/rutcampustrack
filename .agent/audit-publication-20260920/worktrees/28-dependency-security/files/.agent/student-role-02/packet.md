# Dependency security compact contract

Date: 2026-09-07
Risk: S3
Baseline: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`
Worktree/branch: `codex/student-role-02-dependency-security`

## Goal

Upgrade the assigned backend dependency baseline and close the recorded HIGH
vulnerability findings while keeping application behavior, existing API
contracts, gateway routing, and test fixtures stable.

## Context/evidence

The first covered post-build scan of the eight backend Boot JARs used Trivy
0.74.0 and found 4 HIGH and 0 CRITICAL records: async-http-client 2.12.4,
jose4j 0.7.9, and docx4j-core 11.4.11. The Spring Boot 3.5/Springdoc runtime
incompatibility was separately reproduced and closed in
`dependency-security-defect-gate.md`. The exact scan and correction history is
in `runtime/vulnerability-correction-rescan.json`.

## Relevant scope

The frozen scope is the 26 already modified manifest/config paths listed by
`runtime/git-scope-diff.json`: root Gradle policy/catalog, seven application
and API-contract Gradle groups, the API gateway Gradle/YAML migration, and
shared module Gradle pins. The only second-scan correction is in the existing
notification and attendance manifest files.

## Required behavior

- Keep Spring Boot `3.5.16`, Spring Cloud `2025.0.3`, Springdoc `2.8.9`, gRPC
  `1.82.4`, and protobuf `3.25.8` as resolved by the frozen contract.
- Keep root patch properties for Netty `4.1.137.Final`, Tomcat `10.1.59`, Rabbit
  AMQP `5.33.1`, and PostgreSQL `42.7.12`.
- Resolve notification web-push runtime to async-http-client and
  async-http-client-netty-utils `2.15.0`, and jose4j `0.9.6`.
- Resolve attendance docx-stamper runtime to docx4j provider/core `11.5.14`.
- Preserve the existing starter variants, routes, public schemas, and runtime
  test behavior.

## Constraints

One writer operates in this worktree. Do not edit business Java/Kotlin source,
tests, snapshots, generated contracts, coverage thresholds, lockfiles, or
other worktrees. Do not send external push messages; the WebPush test uses a
Mockito PushService mock. Keep scan input read-only and use the fixed Trivy
image digest.

## Existing patterns

Use the version catalog for shared version families, root Spring dependency
management properties for cross-module patch alignment, and per-application
Gradle constraints for a transitive runtime that must be aligned without
changing the upstream feature dependency.

## Acceptance criteria

1. `runtime/git-scope-diff.json` reports exactly 26 expected code paths and
   `git diff --check` exits 0.
2. Post-correction dependencyInsight resolves all four corrected coordinates
   to their target versions; the notification runtime report contains the
   aligned web-push dependencies.
3. The focused Springdoc, WebPush, DOCX renderer, and template smoke checks
   pass with exit code 0.
4. All eight backend `bootJar` tasks pass and hashes are recorded.
5. A covered Trivy rootfs scan sees all eight root JARs and 1,141 package
   records; the HIGH/CRITICAL severity gate exits 0 with 0 HIGH and 0 CRITICAL.
6. Existing unrelated full-check failures remain recorded as limitations and
   cause no scope expansion.

## Verification

See `runtime/checks.json`, `runtime/bootjar-hashes-post-correction.json`,
`runtime/vulnerability-correction-rescan.json`, the Trivy reports under
`runtime/trivy-reports/`, and the focused test logs under `runtime/`.

## Do not

Do not redesign dependency management, regenerate OpenAPI snapshots, repair
unrelated gRPC/coverage/snapshot failures, escalate to Terra, commit, deploy,
or change production data.
