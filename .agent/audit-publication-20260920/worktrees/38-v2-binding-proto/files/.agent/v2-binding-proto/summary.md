# L3 source milestone summary

Status: source implementation and bounded five-module compile gate complete;
fresh independent review and integration remain open.

Final product/test scope is five files: `proto/academic.proto`,
`proto/schedule.proto`, the two Schedule security tests, and
`mobile-bff/.../OpenApiSnapshotIT.java`. The source comparison passed at exit
0, product/test `git diff --check` passed, and the scoped services search found
no handwritten binding consumers. The proto change is the ownership
move/removal of the now-unused Academic import; test changes are the approved
current JWT fixture adaptations, including the UUID import and the mobile
9-argument token call. No factory, production or OpenAPI assertions changed.

The exclusive H1 compile/generate invocation exited 1 on the recorded Schedule
security test fixture mismatch before the other four modules ran. Main then
approved the two-file correction. The repeated H2 five-module union exited 1
after the corrected JWT errors disappeared: schedule `compileJava` was
`UP-TO-DATE`, then `compileTestJava` reported broad missing shared/schedule
classes and a report-file `AccessDeniedException`; the other four modules were
not reached. The retained raw log is
`.agent/v2-binding-proto/h2-gradle.log` (exit 1, 80952 bytes,
SHA256 `AA84DD5580E559793F461BD8C83EF401F2DC884F39F7FE117C533F508928D15A`).

The authorized read-only causal diagnostic found all six representative source
files and `.class` outputs, parsed all six outputs with `javap` at exit 0, and
found the expected shared/test-fixture dependency declarations in Gradle
configuration. `compileTestJava` temp output was empty while compileJava
incremental metadata existed. Representative artifacts were readable under the
current ACL. The exact effective test classpath or incremental-resolution cause
is therefore unknown from retained evidence; the report-file ACL failure is
separate. No Gradle retry, clean, deletion, ACL change, source fix or scope
extension was made. H2 is historical and its five-module gate remains
unverified/blocked, not PASS; the later H9 canonical union is the authoritative
current compile gate and passed all fifteen target tasks at exit 0.
Product runtime is N/A for this compile-only scope. Fresh Sol high review and later main integration remain
OPEN.

The owned files could not be staged for a commit: the explicit `git add`
returned exit 1 because the shared Git worktree metadata path could not create
`.git/worktrees/v2-binding-proto/index.lock` (`Permission denied`). This is a
real permission boundary; no bypass or escalation was used. The stable
worktree diff, source-check hash and retained H2 log hash remain available for
the main integrator.

Main later granted one exclusive H5 invocation with the local diagnostic init
script. H5 returned exit 1 after 46 seconds at
`:services:shared:shared-outbox:compileJava`, with 13 unrelated source errors
for `IdempotencyStore`/`MetricNames` and four warnings. The target five app
compileJava/compileTestJava tasks were not reached. Raw H5 log:
`.agent/v2-binding-proto/h5-gradle.log`, 16035 bytes, SHA256
`0D2F6F72E662557A05A19CCFD80BD0240EAB51735D737752A8F1045D470D7995`.

The init script retained six parseable reached dependency JavaCompile reports.
The shared-outbox report preserves a 51-entry classpath with readable
`shared-observability` and `shared-events` output directories at entries 2 and
3; the named class files existed and post-run `javap` exited 0 for both, plus
the corroborating shared-web-api `ErrorResponse`. Therefore H5 still does not
prove missing or unreadable artifacts. The exact compiler-process classpath or
argument state remains open; the problems-report AccessDeniedException is a
separate report-write failure. No retry, escalation, source/config/dependency
repair or ACL change was performed after the first H5 blocker; H5 is released.

Limitations: generated Java source was not hand-edited; generated outputs are
build artifacts and are not part of this checkpoint. No Docker/server/runtime,
producer lifecycle, handler migration, compatibility alias or reverse proto
import was added; unrelated baseline warnings/errors remain outside scope.

## Final authoritative checkpoint

H7 and H8 are historical bounded attempts with execution defects retained in
`evidence.md` and `checks.json`: H7 had a malformed document-renderer target
and stopped on the missing UUID import; H8 repeated that malformed target and
then exposed the mobile OpenAPI test's stale four-argument JWT call. The
authorized UUID-only and mobile-test corrections were made in the same owned
worktree. Their final hashes are:

- `proto/academic.proto` — `980C12514EB3E2B1E1C90A94207115CC70A2AC64458BAB4B99F14467016C45B9`
- `proto/schedule.proto` — `984538E5ED0414F06A9BB9CAE4AF2BC981CB50A32190EF5FA06B0858C1AAB0FB`
- `ScheduleUserContextFilterIT.java` — `9314F3404E0FC6FA702106F467A180E4296F32ED86375652881A976B29C0F3DA`
- `ScheduleUserContextFilterStrictModeIT.java` — `D2FCF168798DA4676D25557C6421E5A43400EA146D6836429BCBF3815066BCB1`
- `OpenApiSnapshotIT.java` — `3886D055A127DF7BA2DCDE605334926D25938963209FDF3BB3D1F0F0FD96C52C`

The canonical fifteen-task array is in
`.agent/v2-binding-proto/h8b-task-args.json` (final SHA256
`6E4BAFEDB487D6AE3FBA49C65E583CABEF884629972D1C0C3EC56C091C3BF064`; the
pre-F2 purpose-only hash was
`AFFF5C046A8769AFC5C80FEB0B3B28E611DC01E26917051DC75369C9C3E7A2CF`). Its
mechanical comparison against the frozen order and five real module roots
passed at exit 0. H9 reused that array once with
`--no-daemon --no-parallel --max-workers=1 --console=plain`; all five modules'
generateProto, compileJava and compileTestJava tasks completed. H9 returned
exit 0 with `BUILD SUCCESSFUL` in 2m09s (59 actionable: 30 executed, 29
up-to-date). Raw log: `.agent/v2-binding-proto/h9-gradle.log`, 25087 bytes,
SHA256 `A9098B6607B642A9AE9BA956A050ACFA8844C0EC5BE7912E7016C2992EB64787`.
Twenty-four H9 classpath reports parsed with zero capture errors; exact paths
and hashes are in `checks.json`. No runtime was run. Final Sol high review of
the stable five-file diff remains pending; main integration remains pending.
