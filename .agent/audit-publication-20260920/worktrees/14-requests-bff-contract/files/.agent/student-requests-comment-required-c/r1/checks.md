# Checks r1

Environment: Windows PowerShell in C:\Users\maksd\.codex\worktrees\e31c\rutcampustrack\.agent\worktrees\requests-bff-contract; frozen revision d3c31acb8cce53791a4981e5858a37d44fdc9a0e; current checkout is detached and pre-dirty. Heavy lease B SQL7 was active. No Gradle/runtime process was started.

| Check | Command / evidence | Exit |
|---|---|---:|
| Manifest baseline and absent3 guard | Guarded read/hash check against comment-required-producer-chain-manifest-2026-09-09.json; 8/8 baseline hashes matched and all 3 new paths were ABSENT before write. | 0 |
| Dependency source/target hash guard | Get-FileHash -Algorithm SHA256 for both source files and target before/after import; source/post matched B962498813AD3CC4E77EDBFE81E5E82E3013CFE1CF7B0490762880D23FCD3960 and D1DDCDE648C3186914BA52846973497F08D82BBA6C198743A5254EC37E8B0B22. | 0 |
| Contract shape/static guard | Guarded PowerShell assertions for proto tag, domain authority/helper, mapper explicit setter, BFF required field/direct map, required tests, and deferred OpenAPI/TS/client absence. Output: 17 PASS assertions. | 0 |
| Formatting guard | git diff --check -- proto/attendance.proto; output diff-check-pass (Git warning only about LF→CRLF normalization). | 0 |
| Authorization byte-exact guard | Get-FileHash -Algorithm SHA256 target authorization test; output D1DDCDE648C3186914BA52846973497F08D82BBA6C198743A5254EC37E8B0B22. | 0 |
| Changed-path/hash manifest | changed-path-manifest.json records all 11 owned paths, before/after or ABSENT/post hashes, dependency import kind, and immutable P1/P2 supersession. | 0 |
| r1 evidence consistency guard | ConvertFrom-Json on changed-path-manifest.json, source/target re-hash, immutable P1/P2 hash check, new-test whitespace scan and DomainIT annotation indentation; output all PASS. | 0 |
| Post-hash manifest guard | Re-hashed every changedPaths.afterSha256 entry in changed-path-manifest.json; output 11 PASS lines and manifest-post-hash-guard-pass. | 0 |
| Runtime command 1 | .\\gradlew.bat :services:attendance-service:attendance-app:test --tests ru.rutcampustrack.attendance.grpc.StudentRequestGrpcMapperTest --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestServiceAuthorizationTest --no-daemon --no-parallel --max-workers=1 --console=plain --rerun-tasks | NOT RUN (N/A) |
| Runtime command 2 | .\\gradlew.bat :services:attendance-service:attendance-app:integrationTest --tests ru.rutcampustrack.attendance.studentrequest.StudentRequestDomainIT --no-daemon --no-parallel --max-workers=1 --console=plain --rerun-tasks | NOT RUN (N/A) |
| Runtime command 3 | .\\gradlew.bat :services:mobile-bff:mobile-bff-app:test --tests ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClientErrorTest --tests ru.rutcampustrack.mobilebff.contract.StudentRequestDetailJsonTest --tests ru.rutcampustrack.mobilebff.student.StudentRequestFacadeOptionsTest --tests ru.rutcampustrack.mobilebff.contract.StudentRequestOptionsJsonTest --no-daemon --no-parallel --max-workers=1 --console=plain --rerun-tasks | NOT RUN (N/A) |

Skipped runtime checks are not PASS. Root must obtain separate heavy runtime GO, run exact commands, capture exit codes/XML hashes/environment, then obtain fresh Sol/high review.
