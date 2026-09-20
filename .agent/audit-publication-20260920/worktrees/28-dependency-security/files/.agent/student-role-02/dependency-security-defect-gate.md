# Dependency security defect gate — Springdoc/Boot 3.5

Date: 2026-09-07 18:15 MSK  
Baseline: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`  
Worktree: `codex/student-role-02-dependency-security`

## Request and bounded scope

The accepted dependency contract upgrades the application line to Spring Boot
`3.5.16` while preserving the existing Springdoc/OpenAPI integration. The
dependency writer owns the Gradle manifests that declare Springdoc. A runtime
compatibility defect appeared in that owned dependency graph; no business
source or API contract scope is opened.

## Reproduction

Command from the frozen worktree:

```text
.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests "ru.rutcampustrack.academic.events.GroupUpdatedContractIT" --no-daemon --console=plain --stacktrace
```

Exit code: `1` (2026-09-07; Windows, Java 21, Gradle 8.12). The one selected
test fails during Spring context initialization.

## New evidence

The failure report and selected-test output contain this causal chain:

```text
java.lang.NoSuchMethodError:
  'boolean org.springframework.boot.autoconfigure.hateoas.HateoasProperties.getUseHalAsDefaultJsonMediaType()'
  at org.springdoc.core.providers.HateoasHalProvider.isHalEnabled(HateoasHalProvider.java:81)
```

Resolved `testRuntimeClasspath` before correction:

| component | resolved version | evidence |
|---|---:|---|
| `org.springframework.boot:spring-boot-autoconfigure` | `3.5.16` | Gradle dependency report |
| `org.springdoc:springdoc-openapi-starter-common` | `2.8.6` | Gradle dependency report |
| `org.springframework.hateoas:spring-hateoas` | `2.5.3` | Gradle dependency report |

`javap` confirms the binary mismatch. Spring Boot `3.5.16` exposes
`HateoasProperties.isUseHalAsDefaultJsonMediaType()`, while the Springdoc
`2.8.6` bytecode invokes `getUseHalAsDefaultJsonMediaType()` from
`HateoasHalProvider.isHalEnabled`.

The failure is reproducible independently of the full suite: the focused
command ran one test and failed before test logic. The full clean check also
stopped in Academic integration tests with the same root cause after unrelated
build tasks completed.

## Minimal correction proposal

Upgrade the single Springdoc version family used by the existing MVC/WebFlux
starters and shared common alias from `2.8.6` to the first released version
whose Spring Boot 3.5 fix is recorded upstream, `2.8.9` (Springdoc issue
`#3007`). Keep the existing starter variants and OpenAPI routes/configuration;
do not change application source or public contracts. Verify the focused test,
all affected checks, resolved graph, and artifact scan after the correction.

Primary references opened by root:

- https://github.com/springdoc/springdoc-openapi/issues/3005
- https://github.com/springdoc/springdoc-openapi/releases/tag/v2.8.9

## Correction and recheck

Root accepted the bounded dependency-only correction at the existing manifest
scope. The catalog version and all seven existing MVC/WebFlux starter
declarations now use `2.8.9`; starter variants and application configuration
are unchanged.

Post-correction command:

```text
.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests "ru.rutcampustrack.academic.events.GroupUpdatedContractIT" --no-daemon --console=plain --stacktrace
```

Exit code: `0` (2026-09-07 18:14:41 MSK). The selected test started and shut
down the Academic application successfully. The resolved Springdoc common jar
(`2.8.9`) now uses reflection with `isUseHalAsDefaultJsonMediaType` first and
retains the older getter as a fallback; `javap` shows both method names and no
direct failing invocation. Boot `3.5.16` continues to expose the `is...`
accessor.

The gate is closed for this reproduced incompatibility. Broader checks and
security rescan remain required by the parent dependency contract.
