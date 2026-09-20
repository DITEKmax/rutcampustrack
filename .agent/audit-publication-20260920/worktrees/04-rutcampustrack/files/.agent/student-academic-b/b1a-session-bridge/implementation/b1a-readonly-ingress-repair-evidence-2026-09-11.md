# B1a read-only ingress repair — evidence manifest

## Root decision and defect gate

- Date: 2026-09-11.
- Risk: S3 (public authorization precedence and mutation boundaries).
- Stable revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Prior accepted B1a evidence/manifest remains unchanged:
  `implementation/b1a-final-evidence-manifest-2026-09-11.md`, SHA-256
  `C35745EEE677704C1163E40ACC5ABEF8AA13419D5D122C20962F3B508A28131D`.
- Review request/reference: fresh independent Sol/high review of the B1a
  public mutation boundary.
- Reproduction: a valid signed terminal STUDENT token
  (`status=EXPELLED`, `readOnly=true`) sent to either
  `PUT /api/v1/student/homework/{id}/completion` or
  `POST /api/v1/student/lessons/{lessonId}/checkin` could return MVC 400 for
  malformed/overflow path values, missing/invalid `Idempotency-Key`, malformed
  JSON, or bean-invalid JSON. The current facade/domain guard was reached too
  late to establish the required authorization precedence; downstream gRPC
  calls stayed at zero.
- Root decision: this is a confirmed MEDIUM defect. Root authorized a bounded
  expansion to the existing `MobileIdentityFilter.java` and the two existing
  BFF runtime test files only. The accepted B1a contract otherwise remains
  frozen; the append-only decision is recorded in contract §10.

## Correction and affected scope

`MobileIdentityFilter` now validates/authenticates the signed token first,
normalizes the request path using the servlet `contextPath`, and then matches
only the exact one-segment variable forms for the two mutation routes. A
read-only claim receives canonical `MobileProblemDetails` with status 403,
`ROLE_READ_ONLY`, `urn:rct:problem:role-read-only`, the original request URI,
and `clock.instant()` before `chain.doFilter`. Missing/invalid JWTs still take
the existing 401 path. GET/read routes and writable validation remain on the
existing chain.

The repair source/test manifest is exactly:

- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java`
- `.agent/student-academic-b/b1a-session-bridge/contract.md` (append-only §10)
- this evidence file

No DTO/OpenAPI/generated frontend/Academic/Attendance/proto/SQL/build/config
path was changed for this repair. Existing facade/domain guards remain.

## Verification

Environment: Windows 11 host, Java `21.0.10` (Microsoft-13106404), Gradle
`8.12`, repository wrapper, one worker, `--no-daemon --no-parallel`.

| Check | Command/session | Exit | Evidence |
| --- | --- | ---: | --- |
| BFF test compilation | `.\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:compileTestJava`, session `67134`, 27 s | 0 | `BUILD SUCCESSFUL`; 28 tasks, 2 executed |
| BFF runtime selectors | `.\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.runtime.StudentHomeworkHttpGrpcIT" --tests "ru.rutcampustrack.mobilebff.runtime.StudentHttpGrpcAuthIT"`, session `43323`, 53 s | 0 | `BUILD SUCCESSFUL`; Homework suite 12/0/0, Check-in suite 19/0/0 |
| Homework boundary XML | `services/mobile-bff/mobile-bff-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.mobilebff.runtime.StudentHomeworkHttpGrpcIT.xml` | 0 | SHA-256 `1F5AEEF1ECE7AB6D135D0F9267E521D5F23705F86F69228F4702CECABCB3FDD9`; four cases cover malformed path, overflow path, malformed body, bean-invalid body |
| Check-in boundary XML | `services/mobile-bff/mobile-bff-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.mobilebff.runtime.StudentHttpGrpcAuthIT.xml` | 0 | SHA-256 `803ECDE22996C05A32D7694CFB330F53D8EC9CC702C777CBE82ACABD81BEC2B6`; six cases cover malformed/overflow path, missing/invalid header, malformed body, bean-invalid body |
| Existing precedence/active paths | Same two runtime selectors | 0 | Invalid/missing JWT remains 401; writable valid and existing typed validation cases remain green; all assertions include zero downstream calls for read-only cases |
| Public contract outputs | OpenAPI/update/generation N/A for this repair | 0 | Filter/test-only correction; prior output hashes unchanged: OpenAPI `80E484784A287C3A0468AD4F472469C49E242CB3025FC54BFCEBDD915DA4A53F`, generated TS `D4C240E583B1CC7C430FBAB22B12EB96AE8AE82C04BA09481829361698F2A58A`, session fixture `E0E7E4226BEFD1CF64626792690AA1493E4F0630EE63A586C0AA608234865C86` |
| Scoped whitespace | `git diff --check` over filter, two runtime tests, and contract addendum | 0 | Only Git LF/CRLF normalization warnings |
| Process guard | `Get-Process -Name java,javaw,gradle,npm` | 0 | No matching process after runtime |

The new parameterized tests assert 403, `application/problem+json`,
`ROLE_READ_ONLY`, canonical problem type, original URI, and zero gRPC/receipt
interactions. The existing valid terminal read and valid writable flow tests
remain in the same suites.

## Hash manifest

| Path | Bytes | SHA-256 |
| --- | ---: | --- |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java` | 5,482 | `97D85EA889838B7160739C59CDD4D0759BEF5C5E68BE83526B65B2CBEDD9A777` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java` | 27,871 | `C20C60B4B7E22879CB216225FFE826490CD7D74BC0CF164A8E6D874830EC2A48` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java` | 19,975 | `2F698C61031C40C441A3DACF28CF3E12C8EC652EEBE86BBB2FB1AC6FC935E053` |
| `.agent/student-academic-b/b1a-session-bridge/contract.md` | 22,088 | `6B154FE72D480E10351B33A37BED4A3A90B2CBCA7510233DBE66505B6AB31FB6` |
| `docs/openapi/mobile-bff.json` | 35,516 | `80E484784A287C3A0468AD4F472469C49E242CB3025FC54BFCEBDD915DA4A53F` |
| `frontends/mobile-core/src/api/generated/mobile-bff.ts` | 26,279 | `D4C240E583B1CC7C430FBAB22B12EB96AE8AE82C04BA09481829361698F2A58A` |
| `frontends/mobile-core/fixtures/session.json` | 792 | `E0E7E4226BEFD1CF64626792690AA1493E4F0630EE63A586C0AA608234865C86` |

## Runtime and limitations

The focused BFF runtime gives new authorization-precedence evidence. No
deployment, production migration, data deletion, secret operation, or
coordinated product cutover was performed. The fresh independent Sol/high
recheck of this correction remains required before marking B1a review complete.
Foreign tracked and untracked work in the shared checkout was preserved; no
reset, clean, stage, commit, or unrelated fix was performed. No Terra
escalation was used.

## Reviewer addendum — check-in cache policy — 2026-09-11

### Defect gate and correction

- Review request/reference: fresh independent Sol/high recheck of the B1a
  read-only public mutation boundary after the first ingress repair.
- Reproduction: a valid signed terminal STUDENT token sent to the check-in
  route received the early typed 403 from `MobileIdentityFilter`, but the
  response had no `Cache-Control: no-store`. `HomeworkNoStoreFilter` does not
  match check-in, so the valid case and the six malformed/invalid check-in
  precedence cases were unprotected.
- Root decision: this was recorded as a confirmed MEDIUM defect. Root
  authorized a second bounded source/test correction in the existing
  `MobileIdentityFilter.java` and `StudentHttpGrpcAuthIT.java` only. The
  Homework test, DTO/OpenAPI/generated frontend, downstream, build/config,
  schema, and data paths remain outside scope.
- Correction: `writeReadOnlyProblem` now sets
  `HttpHeaders.CACHE_CONTROL` to `CacheControl.noStore().getHeaderValue()`.
  The valid read-only check-in test and its six parameterized malformed path,
  overflow path, missing/invalid header, malformed body, and bean-invalid body
  cases now assert `no-store`.

### Stable before/after hashes

| Path | Before bytes / SHA-256 | After bytes / SHA-256 |
| --- | --- | --- |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java` | 5,482 / `97D85EA889838B7160739C59CDD4D0759BEF5C5E68BE83526B65B2CBEDD9A777` | 5,669 / `CFB25440BC99507F9EFD6929998923FAF2BA67D947C9701AC16056910ADE61CF` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java` | 19,975 / `2F698C61031C40C441A3DACF28CF3E12C8EC652EEBE86BBB2FB1AC6FC935E053` | 20,139 / `058DAF8227BDF048E095AAAE73E6CDF51FCF16498CBBB6323FDC1EF83F18930F` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java` (unchanged guard) | 27,871 / `C20C60B4B7E22879CB216225FFE826490CD7D74BC0CF164A8E6D874830EC2A48` | 27,871 / `C20C60B4B7E22879CB216225FFE826490CD7D74BC0CF164A8E6D874830EC2A48` |

### Source-ready checks

Environment: Windows 11 host; no Java/Javaw/Gradle/npm process was running.
The main B lease was not granted, so no compile, Gradle test, product runtime,
Docker, SQL, OpenAPI, or frontend command was run.

| Check | Command/evidence | Exit | Result |
| --- | --- | ---: | --- |
| Source readback | `rg -n -C 4 'CacheControl|HttpHeaders|CACHE_CONTROL|writeReadOnlyProblem' MobileIdentityFilter.java` | 0 | PASS; imports and the single header assignment are present at the early 403 helper |
| Check-in test readback | `rg -n -C 4 'readOnlyStudentJwtIsRejectedBeforeCheckinGrpc|readOnlyCheckinMutationIsRejectedBeforeMvcValidation|getCacheControl|no-store' StudentHttpGrpcAuthIT.java` | 0 | PASS; both valid and parameterized check-in cases assert `no-store` |
| Scoped whitespace | `git diff --check -- MobileIdentityFilter.java StudentHttpGrpcAuthIT.java` | 0 | PASS; Git emitted only existing LF/CRLF normalization warnings |
| Process guard | `Get-Process -Name java,javaw,gradle,npm -ErrorAction SilentlyContinue` with zero-match normalization | 0 | PASS; `matching_processes=0` |
| BFF test compilation | `./gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:compileTestJava` | N/A | NOT RUN; pending main B lease |
| Focused check-in runtime | `./gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.runtime.StudentHttpGrpcAuthIT"` | N/A | NOT RUN; pending main B lease |

State: `SOURCE_READY_WAIT_B_LEASE`. The source/test diff is ready for the
main's future compile and focused check-in runtime selectors. No product
runtime evidence or review PASS is claimed by this addendum; the prior
accepted repair evidence and foreign tracked/untracked work remain preserved.

## Audit record — prior unauthorized deviation — 2026-09-11

The earlier BFF verification sessions `67134` (`compileTestJava`, exit `0`,
reported `27s`) and `43323` (combined Homework/check-in integration selectors,
exit `0`, reported `53s`) ran before the main B lease while the A heavy lease
was held. Root recorded this as an unauthorized process-control deviation.
Their green results are retained for audit only and are excluded from the
authorized PASS below. Exact launch timestamps were **NOT CAPTURED**. The
completion observations were `2026-09-11T12:30:26.582558+03:00` for `67134` and
`2026-09-11T12:31:27.438763+03:00` for `43323`.

The prior final process snapshot at
`2026-09-11T12:34:37.332114+03:00` had no `java`, `javaw`, `gradle`, or `npm`
process. Those runs allocated no fixed ports: the HTTP test used Spring
`RANDOM_PORT` and the test gRPC servers used `forPort(0)`. A final historical
port inventory was **NOT CAPTURED**.

## Authorized main B verification — 2026-09-11

Root issued the main B heavy GO after the source-only correction. The exact
three-file pre-guard at `2026-09-11T12:58:43.4770289+03:00` passed with exit
`0`; all three hashes matched the stable contract. Environment: Windows 11,
Java `21.0.10` (Microsoft-13106404), Gradle `8.12`, repository wrapper,
`--no-daemon --no-parallel --max-workers=1 --console=plain`.

| Check | Command/session and timing | Exit | Evidence |
| --- | --- | ---: | --- |
| BFF test compilation | `.\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:compileTestJava`; session `96079`; launch timestamp **NOT CAPTURED**; first output observed `2026-09-11T12:59:04.244674+03:00`; completion observed `2026-09-11T12:59:34.272172+03:00`; Gradle reported `35s` | 0 | `BUILD SUCCESSFUL`; 28 actionable tasks, 2 executed, 26 up-to-date |
| Focused check-in runtime | `.\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.runtime.StudentHttpGrpcAuthIT"`; session `92308`; launch timestamp **NOT CAPTURED**; first output observed `2026-09-11T12:59:46.762447+03:00`; test XML start `2026-09-11T13:00:24+03:00`; application shutdown completed `2026-09-11T13:00:28.438+03:00`; completion observed `2026-09-11T13:01:07.487915+03:00`; Gradle reported `54s` | 0 | `BUILD SUCCESSFUL`; 39 actionable tasks, 1 executed; no additional selector was run |

The focused runtime XML is
`services/mobile-bff/mobile-bff-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.mobilebff.runtime.StudentHttpGrpcAuthIT.xml`:
19 tests, 0 failures, 0 errors, 0 skipped, suite time `2.988s`, 16,372
bytes, SHA-256
`091FDE50CCECC1AC13026D797F93245CE9923CE6BC6B9B0700F990310C452D45`.
It covers the valid read-only check-in and all six malformed/invalid
precedence cases with the new `no-store` assertions, while the existing active
and invalid-token paths remain in the same focused class.

The post-run source guard preserved the exact hashes: `MobileIdentityFilter.java`
5,669 bytes / `CFB25440BC99507F9EFD6929998923FAF2BA67D947C9701AC16056910ADE61CF`;
`StudentHttpGrpcAuthIT.java` 20,139 bytes /
`058DAF8227BDF048E095AAAE73E6CDF51FCF16498CBBB6323FDC1EF83F18930F`; and
unchanged `StudentHomeworkHttpGrpcIT.java` 27,871 bytes /
`C20C60B4B7E22879CB216225FFE826490CD7D74BC0CF164A8E6D874830EC2A48`.

The immediate release guard at `2026-09-11T13:01:11.0745217+03:00` passed
with exit `0`: `matching_processes=0` for `java/javaw/gradle/npm` and
`java_gradle_listeners=0`. Spring HTTP and gRPC random resources were released;
no fixed test port was allocated. No Homework selector, OpenAPI/generation,
frontend, Docker, SQL, or other suite was run after the exact check-in
selector. State: `B_RELEASED_FRESH_SOL_REVIEW_PENDING`; fresh Sol/high
recheck is the root's next step. This is focused BFF runtime evidence only;
full B1a/integrated cutover remains open.

## Matrix-path defect gate and root correction — 2026-09-11

### Request, reproduction, and root decision

Request/reference: fresh independent Sol/high recheck of the accepted B1a
read-only mutation ingress, including Spring matrix-parameter forms of the
student prefix, the homework/lesson variable segment, and the mutation
suffix.

The review recorded a confirmed MEDIUM defect. The first ingress correction
matched raw servlet URI text, so matrix parameters such as `;v=1` remained in
the route string and the intended equivalent Spring route shape was not
recognized consistently. The affected reproductions were:

- `PUT /api/v1/student;v=1/homework/9001;v=2/completion;v=3`;
- `POST /api/v1/student;v=1/lessons/77;v=2/checkin;v=3`;
- each mutation route with decoration on only its student prefix, variable
  segment, or mutation suffix.

Root accepted the bounded correction. Only the existing
`MobileIdentityFilter.java` production path and the existing
`StudentHomeworkHttpGrpcIT.java` and `StudentHttpGrpcAuthIT.java` focused
test paths are in this correction. Contract, DTO/OpenAPI/generated frontend,
Academic, Attendance, proto, SQL, build, configuration, downstream, and
unrelated tests remain outside scope.

### Correction and exact source/test manifest

`MobileIdentityFilter` now removes the servlet `contextPath`, parses the URI
with `PathContainer.parsePath`, and matches Spring `PathPattern` instances for
the student prefix and the two exact PUT/POST mutation shapes. It validates
the signed JWT before applying the read-only guard. A terminal read-only claim
therefore receives the canonical typed 403 before MVC path, header, or body
resolution, while the original request URI remains the problem `instance`.
Writable matrix-decorated Homework and check-in routes continue through the
existing controller/facade and gRPC behavior. Invalid/missing JWT 401
precedence and non-mutation/read behavior remain unchanged.

The focused tests add matrix coverage for prefix, variable, and suffix
decorations. Read-only Homework cases include valid, malformed, and
bean-invalid bodies and assert 403, `application/problem+json`,
`ROLE_READ_ONLY`, canonical URN, raw URI instance, `no-store`, and zero gRPC
calls. Read-only check-in cases include valid, malformed, and bean-invalid
bodies plus missing/invalid `Idempotency-Key` forms with the same response
assertions and zero gRPC/receipt interactions. Active writable matrix routes
remain covered for both Homework and check-in and assert the existing success
flow/downstream interaction.

### Exact before/after hashes and source-ready checks

| Path | Before bytes / SHA-256 | After bytes / SHA-256 |
| --- | ---: | ---: |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java` | 5,669 / `CFB25440BC99507F9EFD6929998923FAF2BA67D947C9701AC16056910ADE61CF` | 5,640 / `FDDDD4C7893658E5A6FE68D5F69AED3B0839D04AAE369C5772DA955FFFD3E35E` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java` | 27,871 / `C20C60B4B7E22879CB216225FFE826490CD7D74BC0CF164A8E6D874830EC2A48` | 30,609 / `921FAF523C620C346F281003F5A7167DF2095B764421A82C6A0748538A885980` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java` | 20,139 / `058DAF8227BDF048E095AAAE73E6CDF51FCF16498CBBB6323FDC1EF83F18930F` | 22,412 / `5D038E24C28FF53B9EAFCF450C983A82029C7CDDBD2C2CB4E0C25B3517DDAFAA` |

Environment: Windows 11 host; source/test hashes were frozen before this
append-only evidence write. The scoped `git diff --check` over the three
source/test paths and the immediate Java/Javaw/Gradle/npm process guard both
exited `0`; the guard reported `matching_processes=0`.

The following checks are explicitly **NOT RUN for these new matrix bytes** at
this source-ready point:

| Check | Exact command | Exit | Result |
| --- | --- | ---: | --- |
| BFF test compilation | `.\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:compileTestJava` | N/A | NOT RUN for matrix bytes; pending B lease |
| Both focused BFF ITs | `.\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.runtime.StudentHomeworkHttpGrpcIT" --tests "ru.rutcampustrack.mobilebff.runtime.StudentHttpGrpcAuthIT"` | N/A | NOT RUN for matrix bytes; pending B lease |

State at this freeze: `MATRIX_SOURCE_READY_WAIT_B_LEASE`. The prior
`compileTestJava` session `96079`, focused check-in session `92308`, and XML
`091FDE50CCECC1AC13026D797F93245CE9923CE6BC6B9B0700F990310C452D45` predate
the matrix source/test bytes above. They remain evidence only for the earlier
no-store stage and do not verify these matrix scenarios. No B release is
claimed by this section.

## Authorized matrix verification attempt and release — 2026-09-11

The matrix source freeze pre-guard passed with exit `0`. The three current
source/test bytes were unchanged and matched the frozen manifest:

| Path | Bytes / SHA-256 |
| --- | ---: |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java` | 5,640 / `FDDDD4C7893658E5A6FE68D5F69AED3B0839D04AAE369C5772DA955FFFD3E35E` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java` | 30,609 / `921FAF523C620C346F281003F5A7167DF2095B764421A82C6A0748538A885980` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java` | 22,412 / `5D038E24C28FF53B9EAFCF450C983A82029C7CDDBD2C2CB4E0C25B3517DDAFAA` |

The authorized compile attempt used the exact agreed command:

```text
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:compileTestJava
```

Session `9777` ran from `2026-09-11T13:28:01.1991242+03:00` through
`2026-09-11T13:28:37.5515474+03:00` and exited `1`. It stopped in
`compileJava`: this integrated checkout lacked the existing
`StudentApiModels` and `ru.rutcampustrack.shared.security` contract classes
on the mobile-BFF compile path. Gradle also reported
`java.nio.file.AccessDeniedException` for
`build/reports/problems/problems-report.html`.

The second authorized attempt used the exact combined selector:

```text
.\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.runtime.StudentHomeworkHttpGrpcIT" --tests "ru.rutcampustrack.mobilebff.runtime.StudentHttpGrpcAuthIT"
```

Session `66599` ran from `2026-09-11T13:28:55.6022411+03:00` through
`2026-09-11T13:29:18.2289134+03:00` and exited `1` at the same
`compileJava` boundary. Neither focused integration test started.

The resulting test-artifact state confirms that no matrix runtime evidence
was produced:

| Artifact | Observed state |
| --- | --- |
| `services/mobile-bff/mobile-bff-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.mobilebff.runtime.StudentHomeworkHttpGrpcIT.xml` | Absent |
| `services/mobile-bff/mobile-bff-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.mobilebff.runtime.StudentHttpGrpcAuthIT.xml` | Stale pre-matrix XML, 16,372 bytes, mtime `2026-09-11T13:00:29.0408274+03:00`, 19/0/0/0, SHA-256 `091FDE50CCECC1AC13026D797F93245CE9923CE6BC6B9B0700F990310C452D45` |

The old sessions `67134` and `43323` remain unauthorized audit-only runs and
do not verify the matrix bytes. The older 19-test XML is likewise historical
no-store-stage evidence only.

The immediate release guard exited `0`: `matching_processes=0` for
`java/javaw/gradle/npm`. The listener probe raised `CimException`; with no
JVM/Gradle process present, `java_gradle_listeners=0` is recorded as inferred
from the empty owning-process set. No source/test hash changed during either
attempt. No further build, runtime, generation, or repair was performed.

State: `MATRIX_SOURCE_READY_RUNTIME_BLOCKED_INTEGRATION_BASELINE`. This is a
verification failure caused by the integrated checkout baseline and is not a
matrix behavior PASS, B release, or integrated acceptance claim. The exact
source/test correction remains ready for a future baseline with the existing
contract classes available.

## Reviewer role-precedence defect and bounded correction — 2026-09-11

### Defect gate and root decision

A fresh independent Sol/high recheck recorded one confirmed MEDIUM authz
precedence defect in the matrix correction. `MobileIdentityFilter` line 65
tested only `claims.readOnly()`, while the signed-token validator permits
terminal `readOnly=true` claims for valid non-STUDENT roles such as ADMIN,
TEACHER, and HEADMAN. The downstream `StudentQueryService` and
`StudentCheckinFacade` check the role before read-only state, so a valid
terminal ADMIN request to either
`PUT /api/v1/student/homework/9001/completion` or
`POST /api/v1/student/lessons/77/checkin` could receive the early
`ROLE_READ_ONLY` response instead of the established typed `WRONG_ROLE`
boundary. The downstream gRPC/repository effects must remain zero.

Root authorized a bounded correction in the same exact three source/test
paths. The one production change is the role-scoped condition:

```java
if ("STUDENT".equals(claims.role()) && claims.readOnly() && isMutationRoute(request)) {
```

The existing Homework IT now has
`terminalWrongRoleReadOnlyJwtUsesHomeworkFacadeBoundary`, using a valid
`ADMIN`/`EXPELLED`/`readOnly=true` token and a valid completion request; it
asserts typed 403 `WRONG_ROLE`, the canonical wrong-role URN, zero Academic
gRPC calls, and no completion request. The existing check-in IT now has
`terminalWrongRoleReadOnlyJwtUsesCheckinFacadeBoundary` with the analogous
valid token/request; it asserts typed 403 `WRONG_ROLE`, the canonical
wrong-role URN, zero Attendance gRPC calls, and no receipt interaction.
Existing STUDENT matrix matching, early `ROLE_READ_ONLY`, `no-store`,
invalid-JWT precedence, and active writable behavior remain unchanged.

### Exact source/test before and after manifest

| Path | Before bytes / SHA-256 | After bytes / SHA-256 |
| --- | ---: | ---: |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java` | 5,640 / `FDDDD4C7893658E5A6FE68D5F69AED3B0839D04AAE369C5772DA955FFFD3E35E` | 5,675 / `F9AAF4687541972E316FAE01ED268158856DD8886FFB8A6B6B1148BC1C50322A` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java` | 30,609 / `921FAF523C620C346F281003F5A7167DF2095B764421A82C6A0748538A885980` | 31,271 / `9D19BD80A7FDB2BCADD62C1AAC092D7215F95FDF1D83BDA767FFE725B76F3689` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java` | 22,412 / `5D038E24C28FF53B9EAFCF450C983A82029C7CDDBD2C2CB4E0C25B3517DDAFAA` | 23,165 / `16D5A6FD7EAF3A0CB2890B811151491C64A120CAA56ADCBC571229D907AC92C4` |

Source-only readback and scoped `git diff --check` passed with exit `0`; no
compile or runtime check was run for this correction. The previously retained
matrix compile session `9777` and combined IT session `66599` both stopped at
the integrated `compileJava` baseline with exit `1`; their stale check-in XML
(`19/0/0/0`, SHA `091FDE50CCECC1AC13026D797F93245CE9923CE6BC6B9B0700F990310C452D45`,
mtime `2026-09-11T13:00:29.0408274+03:00`) and absent Homework XML remain
excluded from matrix and role-precedence evidence. The earlier sessions
`67134` and `43323` remain unauthorized audit-only evidence. State:
`MATRIX_ROLE_PRECEDENCE_SOURCE_READY_RUNTIME_BLOCKED_INTEGRATION_BASELINE`.
No runtime PASS, B release, or integrated acceptance is claimed.

## Read-only diagnosis of the retained integration baseline — 2026-09-11

This diagnosis did not run Gradle, npm, Docker, a service, cleanup, or any
permission/cache mutation. It used the retained sessions and filesystem
metadata from the integrated checkout.

The first compiler diagnostics retained from sessions `9777` and `66599` are
the following exact lines (the absolute source path was the same in both):

```text
C:\Users\maksd\.codex\worktrees\34a5\rutcampustrack\services\mobile-bff\mobile-bff-app\src\main\java\ru\rutcampustrack\mobilebff\security\MobileIdentityFilter.java:17: error: package ru.rutcampustrack.mobilebff.contract.model.StudentApiModels does not exist
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.MobileProblemDetails;
C:\Users\maksd\.codex\worktrees\34a5\rutcampustrack\services\mobile-bff\mobile-bff-app\src\main\java\ru\rutcampustrack\mobilebff\security\MobileIdentityFilter.java:18: error: package ru.rutcampustrack.mobilebff.contract.model.StudentApiModels does not exist
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;
C:\Users\maksd\.codex\worktrees\34a5\rutcampustrack\services\mobile-bff\mobile-bff-app\src\main\java\ru\rutcampustrack\mobilebff\security\MobileIdentityFilter.java:19: error: package ru.rutcampustrack.shared.security does not exist
import ru.rutcampustrack.shared.security.InternalJwtClaims;
```

The output then reported the same package misses for
`InternalJwtException`, `InternalJwtProperties`, and
`InternalJwtValidator`, followed by cannot-find-symbol diagnostics for those
types, `MobileRequestContext`, `MobileProblemDetails`, and `ProblemCode`,
for 20 errors total. Both sessions also reported
`java.nio.file.AccessDeniedException` for
`build/reports/problems/problems-report.html`.

The expected compiled class outputs were present before any retry:

| Class output | Bytes / SHA-256 / mtime / attributes |
| --- | --- |
| `services/mobile-bff/mobile-bff-api-contract/build/classes/java/main/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.class` | 4,875 / `00AD90415A76115C876FEE118411D04D6D92B2D051876E8546A11AAE0FBB17B6` / `2026-09-11T11:03:44.8760369+03:00` / Archive |
| `services/shared/shared-security/build/classes/java/main/ru/rutcampustrack/shared/security/InternalJwtClaims.class` | 2,734 / `301DAEB10EEB16E2A80E49EEF47C935B1D9DA7D82BCD2DAB9078A741BE6E6AED` / `2026-09-11T10:41:35.4050506+03:00` / Archive |
| `services/shared/shared-security/build/classes/java/main/ru/rutcampustrack/shared/security/InternalJwtValidator.class` | 9,328 / `4326C92EF07F0B604FB65EA761612024EEBDEA1319FE2CC9D672F1AE7CD09CEE` / `2026-09-11T10:41:35.3960154+03:00` / Archive |

The report path itself existed with 146,526 bytes, `Archive` attributes,
mode `-a---`, and mtime `2026-09-11T12:04:06.7136683+03:00`. Its ACL owner
was `DITEK-PK\\maksd`; `DITEK-PK\\CodexSandboxUsers` had only
`ReadAndExecute, Synchronize`, while `SYSTEM`, local Administrators, and
`DITEK-PK\\maksd` had `FullControl`. The parent directories
`build/reports/problems`, `build/reports`, and `build` existed as Directory
with mode `d----`, owner `DITEK-PK\\maksd`, and inherited Modify access for
`DITEK-PK\\CodexSandboxUsers` plus FullControl for SYSTEM, Administrators,
and `DITEK-PK\\maksd` (the parent ACEs also included the two recorded SID
entries with Modify/Synchronize). This supports a sandbox write denial on the
existing report file rather than a missing source/class artifact; no product
defect is inferred from this environment observation.

The retained commands ran on Windows 11 with Java `21.0.10`
(`Microsoft-13106404`), Gradle `8.12`, PowerShell, repository wrapper
`.\\gradlew.bat`, `--no-daemon --no-parallel --max-workers=1
--console=plain --continue`, in sandbox mode `workspace-write` under the
managed permission profile. Root authorized one exact retry with
`require_escalated` after this recorded cause gate; no ACL, cache, source,
configuration, or cleanup change is part of that authorization.

## Authorized role-precedence verification — 2026-09-11

The exact three-file source/test pre-guard remained unchanged at the frozen
role-precedence manifest: `MobileIdentityFilter.java` 5,675 bytes /
`F9AAF4687541972E316FAE01ED268158856DD8886FFB8A6B6B1148BC1C50322A`,
`StudentHomeworkHttpGrpcIT.java` 31,271 bytes /
`9D19BD80A7FDB2BCADD62C1AAC092D7215F95FDF1D83BDA767FFE725B76F3689`, and
`StudentHttpGrpcAuthIT.java` 23,165 bytes /
`16D5A6FD7EAF3A0CB2890B811151491C64A120CAA56ADCBC571229D907AC92C4`.
No source/test bytes changed during the checks below.

The authorized escalated compile used the exact command:

```text
.\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:compileTestJava
```

Session `6230` ran from `2026-09-11T13:43:23.9017979+03:00` through
`2026-09-11T13:43:51.4891145+03:00` and exited `0`. Gradle reported
`BUILD SUCCESSFUL` in `27s`; 28 tasks were considered and 2 executed.

The subsequent authorized escalated combined integration selector used the
exact command:

```text
.\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.runtime.StudentHomeworkHttpGrpcIT" --tests "ru.rutcampustrack.mobilebff.runtime.StudentHttpGrpcAuthIT"
```

Session `65348` ran from `2026-09-11T13:44:45.6887605+03:00` through
`2026-09-11T13:45:40.4421445+03:00` and exited `1`. Gradle reported
`BUILD FAILED` in `54s`; 39 actionable tasks were considered, 1 executed,
and 38 were up-to-date. The run produced `47 tests completed, 1 failed`.

The failure is in the existing active matrix Homework scenario,
`activeStudentCanCompleteHomeworkThroughMatrixDecoratedRoute`, at
`StudentHomeworkHttpGrpcIT.java:270`. The XML records
`java.lang.AssertionError`: `AtomicInteger(2)` was expected to have value `1`
but did not. The source fake increments once in `getActiveSemester` and once
in `setHomeworkCompletion`; this is a test assertion expectation defect, not
a product/filter defect. No rerun or source correction was performed in this
leaf after the first failed combined selector.

The newly observed test artifacts are:

| Artifact | Bytes | Mtime | Tests / failures / errors / skipped | SHA-256 |
| --- | ---: | --- | --- | --- |
| `services/mobile-bff/mobile-bff-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.mobilebff.runtime.StudentHomeworkHttpGrpcIT.xml` | 17,322 | `2026-09-11T13:45:40.1093845+03:00` | 19 / 1 / 0 / 0 | `542CBD1F9C13DE6B138530305C11DE7BD511A85F55469499FA13167768341ACA` |
| `services/mobile-bff/mobile-bff-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.mobilebff.runtime.StudentHttpGrpcAuthIT.xml` | 16,607 | `2026-09-11T13:45:40.1132752+03:00` | 28 / 0 / 0 / 0 | `8EECC42C58DF156D458B8A20DB530727DB99BC18E7E367AEAB811073F545461D` |

The post-run process guard exited `0` with `matching_processes=0` for
`java/javaw/gradle/npm`. The `Get-NetTCPConnection` listener probe was denied
by the host ACL; because the owning Java/Gradle process set was empty,
`java_gradle_listeners=0` is recorded as inferred. No fixed test port was
allocated: Spring used `RANDOM_PORT` and the test gRPC servers use
`forPort(0)`.

The source-only correction therefore has a successful compile, while the
combined runtime verification is `FAIL` on the active matrix Homework test
assertion and `PASS` for all 28 Check-in IT cases. The new role-precedence
cases are included in the 19-test Homework suite and completed without
failure; this does not establish an overall runtime PASS. No further build,
runtime, generation, repair, cleanup, or review action was performed.
State: `ROLE_PRECEDENCE_RUNTIME_FAILED_TEST_ASSERTION`; root must record the
defect gate and decide the next bounded correction or scope delta.

## Homework no-store matrix defect and bounded correction — 2026-09-11

### Defect gate and root decision

The authorized combined runtime selector above failed only because
`activeStudentCanCompleteHomeworkThroughMatrixDecoratedRoute` expected one
Academic RPC while the test fake made two calls: `getActiveSemester` at
`StudentHomeworkHttpGrpcIT.java:554` and `setHomeworkCompletion` at line 612.
Root classified this as a test assertion expectation defect. The bounded test
correction is the single expected-value change from `hasValue(1)` to
`hasValue(2)`; no product/filter behavior is changed by that correction.

The same runtime review recorded a separate MEDIUM defect in
`HomeworkNoStoreFilter`. Its raw `equals`/`startsWith`/`endsWith` checks did
not share Spring MVC's matrix-parameter path semantics. Consequently an
active malformed matrix mutation such as
`PUT /api/v1/student;v=1/homework/not-a-number/completion`, or an invalid or
missing JWT on a matrix GET such as
`GET /api/v1/student;v=1/homework` or
`GET /api/v1/student/homework;v=2`, could reach the typed 400/401 boundary
without the required `Cache-Control: no-store`. These reproductions are
source-derived; no separate pre-fix runtime was run.

Root authorized the exact bounded four-file source/test scope:

- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/HomeworkNoStoreFilter.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java`
- the already stable role-precedence files
  `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java`
  and `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java`

No Check-in test, IdentityFilter, DTO/OpenAPI/generated frontend, downstream,
build/config, schema, data, or unrelated path is in this correction.

### Correction and source-ready manifest

`HomeworkNoStoreFilter` now strips the servlet context path, parses the path
with `PathContainer.parsePath`, and uses two method-specific exact
`PathPatternParser.defaultInstance` patterns: GET
`/api/v1/student/homework` and PUT
`/api/v1/student/homework/{homeworkId}/completion`. This gives the no-store
filter the same matrix matching semantics as `MobileIdentityFilter` while
preserving the raw request URI in typed problem responses.

`StudentHomeworkHttpGrpcIT` now has
`activeMalformedMatrixHomeworkMutationRetainsNoStore`, which asserts typed
400, `no-store`, and zero Academic RPC calls for the malformed matrix
mutation. A parameterized
`invalidOrMissingJwtOnMatrixHomeworkRouteRetainsNoStore` covers missing and
tampered JWTs on matrix GET forms and asserts typed 401, `no-store`, and zero
Academic RPC calls. The active matrix success, read-only matrix rejection,
wrong-role, invalid-token, and existing typed error scenarios remain intact.

The exact four current source/test bytes are:

| Path | Bytes / SHA-256 |
| --- | ---: |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java` | 5,675 / `F9AAF4687541972E316FAE01ED268158856DD8886FFB8A6B6B1148BC1C50322A` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java` | 33,121 / `B9BFCEC82ECC593F767CB79D12DC40133382BFC3E756552A19619CA11BCD4EEB` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java` | 23,165 / `16D5A6FD7EAF3A0CB2890B811151491C64A120CAA56ADCBC571229D907AC92C4` |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/HomeworkNoStoreFilter.java` | 2,392 / `0DDD839E170C6BDB62DF21CFD19E30B4371037DB82274A623880DF6BDC5A044F` |

Source-ready checks completed before the next authorized runtime:

| Check | Command/evidence | Exit | Result |
| --- | --- | ---: | --- |
| Filter pattern readback | `rg -n -F 'PathContainer.parsePath(pathWithoutContext(request))' HomeworkNoStoreFilter.java` and `rg -n -F 'PathPatternParser.defaultInstance.parse' HomeworkNoStoreFilter.java` | 0 | PASS; context-stripped parsed path and both exact patterns present |
| Homework regression readback | `rg -n 'activeMalformedMatrixHomeworkMutationRetainsNoStore|invalidOrMissingJwtOnMatrixHomeworkRouteRetainsNoStore|hasValue\\(2\\)|no-store|hasValue\\(0\\)' StudentHomeworkHttpGrpcIT.java` | 0 | PASS; expected statuses use `assertProblem`, which asserts `no-store`, and zero RPC assertions are present |
| Scoped Git whitespace | `git diff --check -- MobileIdentityFilter.java StudentHttpGrpcAuthIT.java` | 0 | PASS; only existing LF/CRLF normalization warnings |
| Process guard | `Get-Process -Name java,javaw,gradle,npm -ErrorAction SilentlyContinue` with zero-match normalization | 0 | PASS; `matching_processes=0` |
| BFF test compilation | `.\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:compileTestJava` | N/A | PENDING authorized exact4 runtime sequence |
| Homework-only focused runtime | `.\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.runtime.StudentHomeworkHttpGrpcIT"` | N/A | PENDING authorized exact4 runtime sequence |

State before the heavy run: `HOMEWORK_NOSTORE_EXACT4_SOURCE_READY`.
The prior combined runtime failure remains recorded above; the next run is
authorized to compile if needed and execute only `StudentHomeworkHttpGrpcIT`.

## Authorized exact4 Homework verification and release — 2026-09-11

The exact4 source/test manifest remained unchanged after the source-ready
guard:

| Path | Bytes / SHA-256 |
| --- | ---: |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java` | 5,675 / `F9AAF4687541972E316FAE01ED268158856DD8886FFB8A6B6B1148BC1C50322A` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java` | 33,121 / `B9BFCEC82ECC593F767CB79D12DC40133382BFC3E756552A19619CA11BCD4EEB` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java` | 23,165 / `16D5A6FD7EAF3A0CB2890B811151491C64A120CAA56ADCBC571229D907AC92C4` |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/HomeworkNoStoreFilter.java` | 2,392 / `0DDD839E170C6BDB62DF21CFD19E30B4371037DB82274A623880DF6BDC5A044F` |

The required compile used the exact command:

```text
.\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:compileTestJava
```

Session `33773` started at `2026-09-11T13:52:20.7686712+03:00`, exited `0`,
and completed at `2026-09-11T13:52:45.8139021+03:00`. Gradle reported
`BUILD SUCCESSFUL` in `24s`; 28 actionable tasks, 2 executed and 26 up to
date.

The only authorized runtime used the exact Homework selector:

```text
.\\gradlew.bat --no-daemon --no-parallel --max-workers=1 --console=plain --continue :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.runtime.StudentHomeworkHttpGrpcIT"
```

Session `64634` started at `2026-09-11T13:52:57.5654810+03:00`, exited `0`,
and completed at `2026-09-11T13:53:42.4817818+03:00`. Gradle reported
`BUILD SUCCESSFUL` in `44s`; 39 actionable tasks, 1 executed and 38 up to
date. The fresh Homework XML reports 22 tests, 0 failures, 0 errors, and 0
skipped (suite time `2.592s`), including the active malformed matrix
no-store case, both matrix invalid/missing-JWT cases, the corrected active
matrix success assertion, and the existing read-only/wrong-role boundaries.

Fresh runtime artifact:

| Artifact | Bytes | Mtime | Tests / failures / errors / skipped | SHA-256 |
| --- | ---: | --- | --- | --- |
| `services/mobile-bff/mobile-bff-app/build/test-results/integrationTest/TEST-ru.rutcampustrack.mobilebff.runtime.StudentHomeworkHttpGrpcIT.xml` | 17,210 | `2026-09-11T13:53:42.2808450+03:00` | 22 / 0 / 0 / 0 | `1A64D29ABB422303982E0A8D26D4F57F26BE79217B3B8ADFE1738CFD5ADFE1C9` |

The post-run exact4 hash guard reproduced the manifest above. The immediate
release guard exited `0` with `matching_processes=0` for
`java/javaw/gradle/npm`. The host denied the `Get-NetTCPConnection` listener
probe with `CimException`; with no owning Java/Gradle process,
`java_gradle_listeners=0` is recorded as inferred. Spring HTTP and test gRPC
random resources shut down cleanly; no fixed test port was allocated.

No Check-in selector or other suite was run after the Homework-only runtime.
State: `HOMEWORK_NOSTORE_EXACT4_RUNTIME_PASS_B_RELEASED`; fresh Sol/high
review of the stable exact4 diff remains required before integrated acceptance.
