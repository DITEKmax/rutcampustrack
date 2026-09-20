# L3 evidence

## Scope and revisions

- Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-binding-proto`
- Branch: `codex/v2-binding-proto`
- Base and current source revision before checkpoint: `b8220ac92125a8afa37598b270aa4fab7aa1f470`
- Rules SHA256: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`
- Product scope at freeze: `proto/academic.proto`, `proto/schedule.proto`.
- Approved H1 correction scope: only
  `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilterIT.java`
  and `ScheduleUserContextFilterStrictModeIT.java`.

## Baseline source evidence

At base `b8220ac9`, `academic.proto` contained the three Homework binding RPCs
at lines 56, 59 and 62 and the enum/messages at lines 277–317. The binding
response used `rutcampustrack.schedule.LessonInfo current_lesson = 3`.
`schedule.proto` already owned `LessonInfo` in package
`rutcampustrack.schedule` with Java package
`ru.rutcampustrack.schedule.grpc`.

Baseline SHA256:

- `proto/academic.proto`: `406436542CBD50E588ABA80FD9821EF4D2FA88C680FC85038705BCAE4322257E`
- `proto/schedule.proto`: `6B73603A5E62887065A09FC00C1F4C5523CD3B5E3C0B002DBEEDE1A237C71364`

The case-insensitive search
`rg -n -i --glob '!**/build/**' --glob '!**/.gradle/**' 'ReserveHomeworkBinding|ConfirmHomeworkBinding|GetHomeworkBindings|HomeworkBindingState|HomeworkBindingResponse|HomeworkBindingsRequest|HomeworkBindingsResponse' services`
returned no matches (exit code 1, expected no-match result). No handwritten
consumer or handler reference was found in `services`.

## Source evidence after change

`academic.proto` has no `schedule.proto` import, binding RPC, or binding
declaration. `schedule.proto` has each binding RPC exactly once and all six
binding declarations exactly once. The moved blocks compare equal to their
base source blocks; fields/tags/optional markers/wire types and enum values are
unchanged. The qualified `current_lesson` field, `occurrence_ids = 1`, and
`bindings = 1` checks pass. Unrelated significant source lines in both proto
files compare equal to base.

Post-change SHA256:

- `proto/academic.proto`: `980C12514EB3E2B1E1C90A94207115CC70A2AC64458BAB4B99F14467016C45B9`
- `proto/schedule.proto`: `984538E5ED0414F06A9BB9CAE4AF2BC981CB50A32190EF5FA06B0858C1AAB0FB`

## Heavy check evidence

Main granted exclusive H1 for one bounded invocation. The command ran with
`--no-daemon --max-workers=2 --console=plain` and no Docker/server/runtime.
`schedule-app:generateProto` and `schedule-app:compileJava` completed, then
`schedule-app:compileTestJava` failed with exit code 1 before the other four
consumer modules ran. The failure reproduced at:

- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilterIT.java:40,47,53,71`
- `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilterStrictModeIT.java:42`

The tests call missing `InternalJwtTestFactory.expiredToken`/
`invalidSignature` methods and a four-argument `validToken`; the available
fixture signature requires nine arguments at
`services/shared/shared-security/src/testFixtures/java/ru/rutcampustrack/shared/security/InternalJwtTestFactory.java:40–50`.
These files were outside the original L3 scope and were unchanged in H1. Main
then recorded the scoped correction gate in the updated LESSONS-L3 addendum.
Both approved tests now use the current complete JWT API: the IT builds an
expired token with the current signer and a valid-expiry token signed by an
independent factory, while valid/strict/legacy precedence assertions keep their
original endpoints and expected status behavior. No shared factory, production
source or other test file changed.

Gradle also emitted unrelated warnings about missing Jackson annotation class
metadata and deprecated Mongo `ensureIndex`; they are not connected to the
proto request and did not change code.

After the two-file correction, main granted exclusive H2 for one repeated
five-module union with the same command. H2 exit code was 1. The raw log is
retained at
`.agent/v2-binding-proto/h2-gradle.log` (80,952 bytes,
`AA84DD5580E559793F461BD8C83EF401F2DC884F39F7FE117C533F508928D15A`).
Schedule `generateProto` and `compileJava` were UP-TO-DATE; corrected JWT
security tests no longer appear in compiler errors. Schedule
`compileTestJava` then failed on unrelated missing shared/schedule classes
(including `GrpcDeadlineArchRules`, `IntegrationTestNamingRule`,
`OutboxStorage`, `LessonService`, repositories and related types; 100 compiler
errors) and a Gradle `AccessDeniedException` for
`build/reports/problems/problems-report.html`. The four remaining consumer
modules were not reached. Main's addendum requires reporting this new
unrelated failure before any scope extension, so no further correction or heavy
execution was performed.

## H2 causal diagnostic (read-only)

The H2 representative artifact inspection was run after H2 exited and did not
invoke Gradle, clean, deletion, ACL changes or source changes. On Windows
PowerShell, one `Get-Item` command over the following source/output pairs
returned exit code 0. Every listed source and `.class` path exists and has a
non-zero length:

| Representative | source bytes | class bytes |
| --- | ---: | ---: |
| `shared-observability/GrpcDeadlineArchRules` | 4730 | 3180 |
| `shared-test-containers/IntegrationTestNamingRule` | 5069 | 3216 |
| `shared-outbox/OutboxRecord` | 1132 | 1991 |
| `shared-outbox/OutboxStorage` | 4614 | 688 |
| `schedule-app/LessonService` | 15027 | 15990 |
| `schedule-app/LessonRepository` | 14320 | 8212 |

The six output classes were each loaded and parsed with `javap` using their
module output directory as the classpath; all six commands returned exit code
0. This proves readable, structurally valid representative class artifacts,
including the classes named in H2 compiler errors. The class-file ACL query
also returned exit code 0 for the representative outputs and showed inherited
`DITEK-PK\\CodexSandboxUsers` allow access (`Modify, Synchronize`), so an ACL
read block was not observed.

The read-only build configuration query
`rg -n -i -g 'build.gradle.kts' 'shared-outbox|shared-observability|shared-test-containers|testFixtures|sourceSets|proto' services/schedule-service/schedule-app services/shared/shared-observability services/shared/shared-test-containers services/shared/shared-outbox`
returned exit code 0. It shows `schedule-app` declares
`implementation(project(":services:shared:shared-outbox"))`,
`implementation(project(":services:shared:shared-observability"))`, and
`testImplementation(testFixtures(...shared-observability))` and
`testImplementation(testFixtures(...shared-test-containers))`; the shared
modules apply the `java-test-fixtures` plugin. It also shows the schedule
`sourceSets` proto directory is the repository root `proto` directory. The
source declaration search returned exit code 0 for all six representative
symbols.

The read-only output-state query returned exit code 0: schedule
`build/tmp/compileTestJava` exists but has zero children, while
`build/tmp/compileJava/previous-compilation-data.bin` exists and is 174187
bytes. Together with H2's `compileJava UP-TO-DATE`, this is evidence that the
effective test compile classpath or incremental state may have been stale or
mismatched, but it does not identify the exact Gradle resolution cause. No
effective `javac` classpath or argument file was retained in the empty
`compileTestJava` temp directory, and policy forbids a diagnostic rebuild or a
retry with extra Gradle logging.

H2's retained log confirms `schedule-app:generateProto` and `compileJava` were
`UP-TO-DATE` (lines 13 and 22), followed by `compileTestJava` (line 38) and
failure (line 441). The corrected JWT test names and old factory calls do not
occur in the log. The H2 class errors therefore remain a separate broad
compile-time resolution failure after the H1 fixture correction. The raw log
also records an `AccessDeniedException` while writing
`build/reports/problems/problems-report.html` (line 859). A read-only ACL query
returned exit code 0 and showed inherited
`DITEK-PK\\CodexSandboxUsers` `ReadAndExecute, Synchronize` on that report;
this explains the report-write failure, but does not explain the representative
package-resolution errors.

Diagnostic conclusion: for all six representatives, a physically missing
artifact and an unreadable artifact are ruled out. The relevant dependency and
proto source-set declarations are present. The exact effective test classpath
or incremental-resolution defect is unknown from retained evidence; no source,
dependency, configuration or ACL correction is authorized in this scope. H2
remains `FAIL_UNRELATED_AFTER_CORRECTION`, not a five-module PASS, and no
runtime/test PASS is inferred.

## Checkpoint staging limitation

An explicit staging attempt over only the owned files and metadata command
`git add -- AGENTS.md proto\\academic.proto proto\\schedule.proto services\\schedule-service\\schedule-app\\src\\test\\java\\ru\\rutcampustrack\\schedule\\security\\ScheduleUserContextFilterIT.java services\\schedule-service\\schedule-app\\src\\test\\java\\ru\\rutcampustrack\\schedule\\security\\ScheduleUserContextFilterStrictModeIT.java .agent\\v2-binding-proto`
returned exit code 1 with Git's
`Unable to create .../.git/worktrees/v2-binding-proto/index.lock:
Permission denied`. The target worktree files remain available for review and
were not altered by this failed attempt. No escalation, ACL bypass or alternate
Git metadata path was used; the real worktree permission boundary is reported
as a limitation.

## H5 classpath diagnostic plan and static review

Main granted H5 exclusively after H4 release. The only new owned file is
`.agent/v2-binding-proto/h5-classpath.init.gradle`. Its static review command
`rg -n 'h5TargetTaskOrder|compileTestJava|compileJava|options.incremental|classpathOrderPreserved|classpath.each|sourceDirs|mainOutput|targetedDirectoryEnumeration|h5RepresentativeRelativePaths|h5RepresentativePackageRelativePaths|h5DirectoryEnumerationLimit|h5DirectorySampleLimit|h5DirectoryEntryLimit|DirectoryStream|newDirectoryStream|ErrorResponse|H5_CLASSPATH_REPORT' .agent\\v2-binding-proto\\h5-classpath.init.gradle`
returned exit code 0. The script hash at plan time is
`8DCA42C0EC1B28D68B241A5F9CDFEF5E959BA6A03B84087DCFBCE7C2474543E9`.

The script attaches `doFirst` capture to every `JavaCompile` task reached by
the requested union. It disables `options.incremental` for the invocation,
records Java and test compile task paths, iterates each effective classpath
without sorting, records path/existence/readability/isDirectory and file size,
records source directories plus main/task output directories, checks the six
H2 representative class paths plus the shared-web-api `ErrorResponse.class` in
at most 64 classpath directories, and performs bounded `DirectoryStream`
enumeration on their package parent directories (up to 32 entries with an
8-entry sample per parent, closing each stream). Target
five-module reports use fixed order prefixes for compileJava and
compileTestJava; dependency JavaCompile tasks reached by the same graph use a
separate reached-task filename. Reports are written to the owned
`.agent/v2-binding-proto/h5-classpath-*.log` files.

The single planned command, with no separate inspection invocation, is:

`& .\\gradlew.bat --no-daemon --max-workers=2 --console=plain --no-build-cache --rerun-tasks --init-script .\\.agent\\v2-binding-proto\\h5-classpath.init.gradle :services:schedule-service:schedule-app:generateProto :services:schedule-service:schedule-app:compileJava :services:schedule-service:schedule-app:compileTestJava :services:academic-service:academic-app:generateProto :services:academic-service:academic-app:compileJava :services:academic-service:academic-app:compileTestJava :services:attendance-service:attendance-app:generateProto :services:attendance-service:attendance-app:compileJava :services:attendance-service:attendance-app:compileTestJava :services:mobile-bff:mobile-bff-app:generateProto :services:mobile-bff:mobile-bff-app:compileJava :services:mobile-bff:mobile-bff-app:compileTestJava :services:document-renderer-service:document-renderer-app:generateProto :services:document-renderer-service:document-renderer-app:compileJava :services:document-renderer-service:document-renderer-app:compileTestJava 2>&1 | Tee-Object -FilePath '.agent\\v2-binding-proto\\h5-gradle.log'; $h5Exit=$LASTEXITCODE; Write-Output ('H5_EXIT=' + $h5Exit); exit $h5Exit`

The planned raw Gradle log path is
`.agent/v2-binding-proto/h5-gradle.log`; per-task classpath reports use the
same metadata directory. Before execution, H5 was queued only; no Gradle,
protoc, test, clean, deletion, ACL or build-cache command was run for H5.

## H5 execution and reached classpath evidence

The planned command was run exactly once under the exclusive H5 lease with
`--no-daemon --max-workers=2 --console=plain --no-build-cache --rerun-tasks`
and the local init script. It returned exit code 1 after 46 seconds. The raw
log is `.agent/v2-binding-proto/h5-gradle.log`, 16035 bytes, SHA256
`0D2F6F72E662557A05A19CCFD80BD0240EAB51735D737752A8F1045D470D7995`.
The log records `H5_EXIT=1`, `12 actionable tasks: 12 executed`, and
`BUILD FAILED`.

The first source blocker was
`:services:shared:shared-outbox:compileJava` (raw log line 34). It reported 13
errors and four warnings: missing `ru.rutcampustrack.shared.events.IdempotencyStore`,
missing `ru.rutcampustrack.shared.observability.MetricNames`, and dependent
override/symbol errors in the shared-outbox sources. The first dependency
compile failure stopped the requested graph before any terminal five-module
`compileJava` or `compileTestJava` task. Reached JavaCompile report files were
written and parseable (the report-JSON read command returned exit code 0):

- `h5-classpath-reached-_services_shared_shared-web-api_compileJava.log` (2732 bytes)
- `h5-classpath-reached-_services_schedule-service_schedule-api-contract_compileJava.log` (16853 bytes)
- `h5-classpath-reached-_services_shared_shared-events_compileJava.log` (6968 bytes)
- `h5-classpath-reached-_services_shared_shared-logback_compileJava.log` (4631 bytes)
- `h5-classpath-reached-_services_shared_shared-observability_compileJava.log` (9709 bytes)
- `h5-classpath-reached-_services_shared_shared-outbox_compileJava.log` (37651 bytes)

Their SHA256 values, in the same order, are
`71DE4B706614403D5ADCB291CA4A9393B17250B285913506D244DCED4E9994B9`,
`03BF3B2C5257F292B612575FC62FAD44D2437F97BD9D4A1F1568325545CA58F4`,
`4349EB50FD7FC2F854E5B5B6D92AA2F75A8B143485B54E32C8D16490A18E18FC`,
`11BF5EF1DE9D6C7468C8CC774AB85006D32A5E5707FA775118F666A2206F57AA`,
`338234DD47403CF003C474D3A3ED9117B6AD9404FAF41A09735F71BD80F8B77A`,
`7424FB4C05CAB99D5488362DE98AF9F83B4682D47B6C956EE427988EB7A95F64`.

The `shared-outbox:compileJava` report has 51 classpath entries and zero
capture errors. In the captured original order, `classpath[2]` is
`services/shared/shared-observability/build/classes/java/main` and
`classpath[3]` is `services/shared/shared-events/build/classes/java/main`;
both entries were recorded as existing, readable directories. Their relevant
class files also existed before the outbox report capture:
`IdempotencyStore.class` was 338 bytes at UTC 20:08:59.828, and
`MetricNames.class` was 1080 bytes at UTC 20:09:00.648; the outbox report was
captured at UTC 20:09:00.886. Post-run direct `javap` reads of
`IdempotencyStore`, `MetricNames` and the corroborating
`shared-web-api/ErrorResponse` each returned exit code 0. The latter class was
8431 bytes and confirms the H4 ErrorResponse artifact is readable in this
worktree, although attendance-api-contract was not reached by H5.

This gives a concrete partial causal result: the captured Gradle
`compileJava.classpath` property contained readable shared output directories
with the named class artifacts, yet javac still reported those packages as
missing. The exact compiler-process classpath/argument state is not retained,
so a source bug, missing artifact and unreadable artifact are not established
as the cause. The init script removed incremental compilation and the command
disabled build cache and reran tasks, but no second invocation or extra Gradle
logging is authorized. Target terminal app classpaths remain OPEN because the
graph stopped at shared-outbox.

The same raw log separately records
`AccessDeniedException` for
`build/reports/problems/problems-report.html` (line 171). A post-H5 read-only
ACL query returned exit code 0 and showed inherited
`DITEK-PK\\CodexSandboxUsers` `ReadAndExecute, Synchronize`; this is a report
overwrite failure and remains separate from the readable classpath entries.

Post-H5 source/evidence hashes remained stable: `academic.proto`
`980C12514EB3E2B1E1C90A94207115CC70A2AC64458BAB4B99F14467016C45B9`,
`schedule.proto`
`984538E5ED0414F06A9BB9CAE4AF2BC981CB50A32190EF5FA06B0858C1AAB0FB`,
the two approved test files
`9314F3404E0FC6FA702106F467A180E4296F32ED86375652881A976B29C0F3DA` and
`0CE2D8920B6A0EE748E3A37FF9E71BD0BA490E2E7E5842F5F16DEFFD61B65C59`,
`source-check.ps1`
`1278F2F536E5B0073C913031FBD71076C173E768A7542BEFEB5DDEA851730C6C`, and
the executed init script
`8DCA42C0EC1B28D68B241A5F9CDFEF5E959BA6A03B84087DCFBCE7C2474543E9`.
Per main's H5 gate, no source/dependency/config/ACL repair, escalation or
further heavy execution was performed after this first unrelated blocker.

## H6 one-task reproducer plan

Main granted H6 exclusively after H5 release for one bounded
`:services:shared:shared-outbox:compileJava` reproducer. To preserve H5, the
H5 init script and six H5 reports remain unchanged. A private copy,
`.agent/v2-binding-proto/h6-classpath.init.gradle`, uses the same incremental
off, unsorted classpath, output/source and bounded directory enumeration hooks
with only `h6-*` report names and schema. Its static requirement scan returned
exit code 0; H6 script SHA256 is
`80BDC99416480BA8F60307706EB6704711B5CC06A356AD3688C3765EA1808E85`.
H5 script SHA256 remains
`8DCA42C0EC1B28D68B241A5F9CDFEF5E959BA6A03B84087DCFBCE7C2474543E9`, and a
pre-run report count confirmed zero `h6-classpath-*.log` files.

The exact single H6 command is:

`& .\\gradlew.bat --no-daemon --max-workers=1 --console=plain --no-build-cache --rerun-tasks --init-script .\\.agent\\v2-binding-proto\\h6-classpath.init.gradle :services:shared:shared-outbox:compileJava 2>&1 | Tee-Object -FilePath '.agent\\v2-binding-proto\\h6-gradle.log'; $h6Exit=$LASTEXITCODE; Write-Output ('H6_EXIT=' + $h6Exit); exit $h6Exit`

The H6 raw log path is `.agent/v2-binding-proto/h6-gradle.log`; per-task
reports use `.agent/v2-binding-proto/h6-classpath-*.log`, so no H5 log/report
will be overwritten. The known problems-report permission failure authorizes
only a narrow escalated execution of this exact command if required. No H6
execution occurred before this plan was recorded.

## H6 execution and environment comparison

The exact H6 command ran once with the narrow escalated execution permission.
It returned exit code 0 after 33 seconds with `BUILD SUCCESSFUL`, `H6_EXIT=0`,
and `3 actionable tasks: 3 executed`. The raw log is
`.agent/v2-binding-proto/h6-gradle.log`, 3113 bytes, SHA256
`31AF95897C1BD931C378347122618F7C1E9E76D37CB650FBE10EA5D080F1F9E8`.
The H6 log reports no `AccessDeniedException` and shows the problems report was
available.

The reached task order was exactly
`:services:shared:shared-events:compileJava`,
`:services:shared:shared-observability:compileJava`, then
`:services:shared:shared-outbox:compileJava`. Fresh reports, all parseable by
the read-only JSON check at exit code 0, are:

- `h6-classpath-reached-_services_shared_shared-events_compileJava.log` (6786 bytes, SHA256 `AC3E802DEA0CD86B2CFD400FDAA2A9C0A546ADE465AEEF2870CFA030B944690B`)
- `h6-classpath-reached-_services_shared_shared-observability_compileJava.log` (9429 bytes, SHA256 `77B43FD394ADD0493519D736490576F15C3B1FFF2BC8EE165ABD5F16B7A981C6`)
- `h6-classpath-reached-_services_shared_shared-outbox_compileJava.log` (36965 bytes, SHA256 `51CF067F9B3ACCF268E71D26C0C22E234B91FB3A2BBE5F934B747A6E200A778B`)

The H6 outbox report has 51 classpath entries, zero capture errors and
`incrementalDisabled=true`. Its original order keeps `classpath[2]` at the
shared-observability main output and `classpath[3]` at the shared-events main
output; both are existing, readable directories. The same report records
successful bounded `DirectoryStream` results for existing representative
package directories. H6 therefore reproduces the source classpath property
with the missing-package sources and compiles successfully.

The read-only H5/H6 comparison returned exit code 0. Both outbox reports have
51 entries; all non-cache entries, including CP[2] and CP[3], are identical at
the same indexes (`NonCacheClasspathDifferences=0`). The 49 differences are
external dependency jar roots: H5 used
`C:\\Users\\CodexSandboxOffline\\.gradle`, while H6 used
`C:\\Users\\maksd\\.gradle`. H5 used normal sandbox execution and
`--max-workers=2` in the five-module union; H6 used narrow escalated
execution, `--max-workers=1` and the one-task reproducer. This narrows the
failure to execution/cache/task-topology context, but does not isolate
permissions as the sole cause because cache root and worker topology also
changed. The normal read-only environment snapshot returned exit code 0 with
`whoami=ditek-pk\\codexsandboxoffline`, Java 21.0.10 at
`C:\\Users\\maksd\\.jdks\\ms-21.0.10`; H5 report/log owners were
`DITEK-PK\\CodexSandboxOffline`, while H6 report/log owners were
`DITEK-PK\\maksd`. No secrets were inspected.

H6 is a bounded reproducer result only. It does not turn H2's five-module
union into PASS and did not verify any target app classpath; H6 stopped after
the single shared-outbox task. No source, dependency, build configuration or
ACL repair, clean, deletion, further Gradle run or runtime execution followed
H6. The H6 lease was released.

## H7 execution and first test-source blocker

After the explicit H6 release, H7 ran once under the same narrow escalated
context with `--no-daemon --no-parallel --max-workers=1 --console=plain` and
the private H7 init script. H7 intentionally omitted `--rerun-tasks` and
`--no-build-cache` so existing up-to-date outputs were used. The exact
invocation was:

`& .\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --init-script .\\.agent\\v2-binding-proto\\h7-classpath.init.gradle :services:schedule-service:schedule-app:generateProto :services:schedule-service:schedule-app:compileJava :services:schedule-service:schedule-app:compileTestJava :services:academic-service:academic-app:generateProto :services:academic-service:academic-app:compileJava :services:academic-service:academic-app:compileTestJava :services:attendance-service:attendance-app:generateProto :services:attendance-service:attendance-app:compileJava :services:attendance-service:attendance-app:compileTestJava :services:mobile-bff:mobile-bff-app:generateProto :services:mobile-bff:mobile-bff-app:compileJava :services:mobile-bff:mobile-bff-app:compileTestJava :services:document-renderer-service:compileJava :services:document-renderer-service:document-renderer-app:compileTestJava 2>&1 | Tee-Object -FilePath '.agent\\v2-binding-proto\\h7-gradle.log'; $h7Exit=$LASTEXITCODE; Write-Output ('H7_EXIT=' + $h7Exit); exit $h7Exit`

The malformed final task path is retained as an execution defect. H7 returned
exit code 1 after 51 seconds; the raw log is
`.agent/v2-binding-proto/h7-gradle.log`, 9623 bytes, SHA256
`A1B4462476EA7FCBCB61AEBB65374631BC1FEDB3C7035CB9E37288E2C858F622`.
Despite the malformed final argument, Gradle reached schedule, academic,
attendance and mobile target generate/compileJava/compileTestJava tasks; the
document-renderer app targets were not reached. The first source error was
the approved strict security test's missing `java.util.UUID` import at line
20; the H7 log contains the two resulting `cannot find symbol` errors.
No H7 retry was made for this malformed invocation.

## UUID correction and H8 execution

The explicit bounded correction added only `import java.util.UUID;` to
`ScheduleUserContextFilterStrictModeIT.java`, preserving its assertions and
factory-call semantics. The source check and mobile-file diff check both
returned exit code 0. The post-correction strict-test SHA256 is
`D2FCF168798DA4676D25557C6421E5A43400EA146D6836429BCBF3815066BCB1`;
proto hashes and the other approved test hash stayed unchanged.

H8 then ran once with the same narrow flags and a fresh H8 report namespace,
but its final document-renderer argument repeated the H7 task-path typo
(`:services:document-renderer-service:compileJava` instead of the app path).
The exact malformed invocation was:

`& .\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --init-script .\\.agent\\v2-binding-proto\\h8-classpath.init.gradle :services:schedule-service:schedule-app:generateProto :services:schedule-service:schedule-app:compileJava :services:schedule-service:schedule-app:compileTestJava :services:academic-service:academic-app:generateProto :services:academic-service:academic-app:compileJava :services:academic-service:academic-app:compileTestJava :services:attendance-service:attendance-app:generateProto :services:attendance-service:attendance-app:compileJava :services:attendance-service:attendance-app:compileTestJava :services:mobile-bff:mobile-bff-app:generateProto :services:mobile-bff:mobile-bff-app:compileJava :services:mobile-bff:mobile-bff-app:compileTestJava :services:document-renderer-service:compileJava :services:document-renderer-service:document-renderer-app:compileTestJava 2>&1 | Tee-Object -FilePath '.agent\\v2-binding-proto\\h8-gradle.log'; $h8Exit=$LASTEXITCODE; Write-Output ('H8_EXIT=' + $h8Exit); exit $h8Exit`

H8 returned exit code 1 after 1 minute 57 seconds with 51 actionable tasks
(41 executed, 10 up-to-date). The raw log is
`.agent/v2-binding-proto/h8-gradle.log`, 25032 bytes, SHA256
`06603355987F6FAFA38EC16F7BF2414D48762F098E37C9E935C071BEF1407425`.
Schedule, academic, attendance and mobile target compileJava/compileTestJava
tasks reached; document-renderer targets did not. The first new compiler
error after the UUID correction was the unrelated baseline call in
`services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contractexport/OpenApiSnapshotIT.java:228`:
`JWT.validToken(100L, "STUDENT", 10L, false)` supplies four arguments while
the current API requires nine. H8 was released without retrying this new
source defect.

## Canonical task verification and mobile correction

Per the explicit H8b/H9 gate, `.agent/v2-binding-proto/h8b-task-args.json`
stores the canonical ordered fifteen-target array. The read-only mechanical
comparison returned exit code 0: it found 15 canonical tasks and 15 tasks in
the frozen H5 command, exact order equality, zero duplicate roots, valid
one-to-one mapping to the five module roots, and `build.gradle.kts` present
for every root. The final metadata file hash is
`6E4BAFEDB487D6AE3FBA49C65E583CABEF884629972D1C0C3EC56C091C3BF064`;
the pre-F2 purpose-only hash was
`AFFF5C046A8769AFC5C80FEB0B3B28E611DC01E26917051DC75369C9C3E7A2CF`.

The additional authorized correction changed only
`OpenApiSnapshotIT.java`: it added `java.util.UUID` and replaced the stale
four-argument helper call with a nine-argument token using fixed session UUID
`88888888-8888-4888-8888-888888888888`, session/roles versions `1L`,
`"STUDENT"`, `"ACTIVE"`, group `10L`, headman `false` and read-only
`false`. User id `100L` and all assertions were preserved. The resulting
SHA256 is `3886D055A127DF7BA2DCDE605334926D25938963209FDF3BB3D1F0F0FD96C52C`.
No factory, production, OpenAPI assertion or other file was changed for this
correction.

## H9 five-module union

The exact H9 command loaded `tasks` from the mechanically verified JSON array
with PowerShell `@h9Tasks`; it did not hand-type the fifteen task arguments:

`$h9Canonical = Get-Content '.agent\\v2-binding-proto\\h8b-task-args.json' -Raw | ConvertFrom-Json; $h9Tasks = @($h9Canonical.tasks); if ($h9Tasks.Count -ne 15) { Write-Output ('H9_CANONICAL_TASK_COUNT=' + $h9Tasks.Count); exit 2 }; & .\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --init-script .\\.agent\\v2-binding-proto\\h9-classpath.init.gradle @h9Tasks 2>&1 | Tee-Object -FilePath '.agent\\v2-binding-proto\\h9-gradle.log'; $h9Exit=$LASTEXITCODE; Write-Output ('H9_CANONICAL_TASK_COUNT=' + $h9Tasks.Count); Write-Output ('H9_EXIT=' + $h9Exit); exit $h9Exit`

H9 returned exit code 0 after 2 minutes 9 seconds with `BUILD SUCCESSFUL`,
`H9_CANONICAL_TASK_COUNT=15`, and `59 actionable tasks: 30 executed, 29
up-to-date`. All fifteen requested target tasks reached, including the
document-renderer app's generateProto, compileJava and compileTestJava. The
raw log is `.agent/v2-binding-proto/h9-gradle.log`, 25087 bytes, SHA256
`A9098B6607B642A9AE9BA956A050ACFA8844C0EC5BE7912E7016C2992EB64787`.

The H9 init captured 24 reached/target reports; the read-only JSON parse
returned exit code 0, with zero capture errors and all ten target reports
present. Target report hashes are:

- `h9-classpath-target-01-_services_schedule-service_schedule-app_compileJava.log` (119318 bytes, SHA256 `284DD03F8327AC80C93F3A808860325F0BC6202E332E612D609F6BBD92BE664D`)
- `h9-classpath-target-02-_services_schedule-service_schedule-app_compileTestJava.log` (177889 bytes, SHA256 `ED7EAE9A1FC0BC97B2CD2F40905E7D161D067FA986A8447F43F40C277EBB953B`)
- `h9-classpath-target-03-_services_academic-service_academic-app_compileJava.log` (123184 bytes, SHA256 `FF2605AB2F27133D028D4690C10EC63812FA3201945CF8660A82B4947479F412`)
- `h9-classpath-target-04-_services_academic-service_academic-app_compileTestJava.log` (180810 bytes, SHA256 `7DA20941E2AA7D41E97736124FB92F76A26D6A5548EA55A724640EBB6339A24D`)
- `h9-classpath-target-05-_services_attendance-service_attendance-app_compileJava.log` (127481 bytes, SHA256 `C3081CCBBC3AB123EA905F816714BBDE2864CB122C21DFF6EF5CEC2E72A41B54`)
- `h9-classpath-target-06-_services_attendance-service_attendance-app_compileTestJava.log` (185609 bytes, SHA256 `3F9D7A9C3594493CB9EACFD9A42591A50C1DC4C292904AB6651FE53BD24F5901`)
- `h9-classpath-target-07-_services_mobile-bff_mobile-bff-app_compileJava.log` (59726 bytes, SHA256 `56E64F912967B006628CE04A57CCA16C5ED017663C2EF7253801D0C305614F50`)
- `h9-classpath-target-08-_services_mobile-bff_mobile-bff-app_compileTestJava.log` (94816 bytes, SHA256 `AA130EFF7C808FE2A6EC0F3709C3ADEAF17690F0D2FF9F9FB7F285E709C97C30`)
- `h9-classpath-target-09-_services_document-renderer-service_document-renderer-app_compileJava.log` (62478 bytes, SHA256 `5250D72485DAB202E1AF19550288509C4CE758B7CDA6FF53692E1CC08EC7542D`)
- `h9-classpath-target-10-_services_document-renderer-service_document-renderer-app_compileTestJava.log` (87893 bytes, SHA256 `3520B449017F7ECAE51052F7CF56796971FDB4AF503AED7424F3FBD8BDADCFE1`)

The fourteen reached dependency report names and all 24 report hashes are
listed in `checks.json`; the corresponding target reports have 143, 193, 153,
203, 165, 216, 88, 122, 75 and 102 classpath entries in target order, with
`incrementalDisabled=true` and the original classpath order retained. H9
used normal existing caches under the same narrow approved context and no
`--rerun-tasks`, `--no-build-cache`, clean, deletion, ACL change or runtime.
The H9 lease was released after exit 0.

## Runtime and review

Product runtime is N/A for this declaration-only checkpoint. H1, the
correction gate, H2, H5, H6, H7, H8 and H9 used compile/generate only; no
server, Docker or integration runtime was run. H9 completed the five-module
generateProto, compileJava and compileTestJava gate; no runtime execution is
implied by this compile evidence. Independent Sol high full diff review of the
final scoped diff remains OPEN.
