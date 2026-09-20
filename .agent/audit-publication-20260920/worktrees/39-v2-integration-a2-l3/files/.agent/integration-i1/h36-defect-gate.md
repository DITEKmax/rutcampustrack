# H36 I4 first-failure defect gate

Status: `H37_DIAGNOSTIC_PASS_H38_READY_NO_PRODUCT_CORRECTION`  
Date: 2026-09-15 (Europe/Moscow)  
Severity: `BLOCKER` for the H36 runtime gate  
Scope: I4 Academic combined five-unit selector only; no IT was started.

## Request/reference

The I4 packet requires a source-frozen 44-path union followed by the exact
five-unit Academic selector and, only after success, the two PostgreSQL IT
selectors. H36 was an exclusive parent-controlled lease. The frozen union and
corrected selectors are recorded in `i4-source-freeze.md` and
`i4-checks.json`.

## Reproduction

From the assigned target worktree, PowerShell ran exactly:

```powershell
.\gradlew.bat :services:academic-service:academic-app:test --tests ru.rutcampustrack.academic.map.CampusMapReadServiceTest --tests ru.rutcampustrack.academic.grpc.CampusMapGrpcReadTest --tests ru.rutcampustrack.academic.grpc.StudentHomeworkGrpcIdentityInterceptorTest --tests ru.rutcampustrack.academic.grpc.StudentProjectionGrpcServiceTest --tests ru.rutcampustrack.academic.studentprojection.StudentProjectionScopeServiceTest --no-daemon --no-parallel --max-workers=1 --no-problems-report
```

The command exited `1` at `:services:academic-service:academic-app:compileJava`
before any selected test class ran. Gradle reported `16 actionable tasks: 1
executed, 15 up-to-date`, then `100 errors`.

## New evidence

Complete raw evidence is preserved without source edits:

- stdout: `h36-unit.stdout.log`, SHA256
  `73EFC90DAA2C86D5FC734A3458EA8A1B1C4529482179EDC5024DC08FA07FB3DB`
- stderr: `h36-unit.stderr.log`, SHA256
  `89FBE4803EDCFBC37D0B570EFA76FB8ADA6C7EFC121FDE936D319D8C4933A199`
- context: `h36-unit.context.log`, SHA256
  `E29BDABCB2DBFFF2B6312125BC490B1C907BF387B2D3632F3F69B13CADC3D008`
- exit record: `h36-unit.exit.log`, SHA256
  `593CBC3069F98713CBCA24B225664884E3D02CF96A835AF9AA09D5262B2A4CA3`

The first compiler diagnostics report missing `ru.rutcampustrack.academic.entity`,
`ru.rutcampustrack.academic.repository`,
`ru.rutcampustrack.academic.studentprojection`, `ru.rutcampustrack.shared.security`,
and generated `AcademicGrpcServiceGrpc` symbols while compiling the composed
`AcademicGrpcServiceImpl.java` and map service. The files are physically
present: the target has 121 main Java sources, 230 generated Java sources, and
694 existing main class files. This supports an incremental/classpath context
hypothesis but does not prove its cause.

The only XML files present under `build/test-results/test` are three files with
timestamps `2026-09-15 00:11:17`, before this run began at `17:07:10`; they are
stale and excluded. No current XML was produced, so the nonzero-per-selected-
class requirement is unmet.

## H37 bounded diagnostic correction

Root authorized H37 with `require_escalated`, `login=false`, the same target
worktree, and no normal fallback. The own init script changed only the target
`compileJava` task's `options.incremental=false`, logged source/classpath
counts and toolchain presence, and used `--no-build-cache`. It did not change
product source, dependencies, caches, global configuration, or other tasks.

The exact H37 command exited `0`:

```powershell
.\gradlew.bat :services:academic-service:academic-app:compileJava --no-daemon --no-parallel --max-workers=1 --no-problems-report --no-build-cache --init-script .agent/integration-i1/h37-classpath.init.gradle
```

H37 logged `incremental=false`, `sourceCount=356`, `classpathEntries=153`,
`classpathMissing=0`, `toolchainLanguage=21`, and `compilerPresent=true`.
Raw evidence is preserved in `h37-classpath.stdout.log`,
`h37-classpath.stderr.log`, `h37-classpath.context.log`, and
`h37-classpath.exit.log`; their hashes and environment values are recorded in
`i4-checks.json`. This is new evidence that the bounded diagnostic context
compiles successfully. Because the context and incremental mode changed
together, it does not establish a sole cause for H36.

## Correction and decision gate

No product correction was performed. H37 is complete and H38 is preauthorized
under the same escalated context: run the original five-unit selector, then
`CampusMapReadRepositoryIT`, then `StudentProjectionQueryAdapterIT`, stopping
at the first actual failure. Terra escalation is not requested and remains
prohibited by the project rules.

## Preservation/cleanup

The target HEAD remains `b8220ac92125a8afa37598b270aa4fab7aa1f470`; the 44-row
union was rehashed after the failure with canonical SHA256
`28447CAB436CE49C2AADEC9103A4293E358D5BCBE569C9B61C85F7C655FABDA9` and zero
file mismatches. No source sibling, E worktree, product file, or foreign dirty
change was edited. The single-use Gradle daemon stopped; no Testcontainers or
IT resource was started.
