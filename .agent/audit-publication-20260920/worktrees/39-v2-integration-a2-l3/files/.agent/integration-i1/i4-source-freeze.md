# I4 source freeze — accepted map reader over I3

Date: 2026-09-15 (Europe/Moscow)  
Target: `codex/v2-integration-a2-l3`  
Base: `b8220ac92125a8afa37598b270aa4fab7aa1f470`  
Rules SHA256: `B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`

## Frozen source and union

The accepted read-only source manifest is
`.agent/worktrees/v2-map-read/.agent/v2-map-read/source-manifest.json` with
SHA256
`E16EEBA8BA3FBF0260E1BEE755D0CC45C859358CDFBE4F097CB8CB687371324C`.
Every listed file and model destination matched its manifest SHA before the
transfer. The source worktree remains unchanged by I4. The accepted I3
35-path union with SHA256
`604CD4D36A70710B7C1790805DA70721B97741AB7CA0924E46BAF3850F614C6A` was
preserved and expanded by the nine non-overlap map paths.

The exact 44 sorted `path=UPPER_SHA256` rows are frozen in
`i4-union-manifest.sha256`. They are UTF-8, LF-joined, and have no trailing
LF. The canonical SHA256 is
`28447CAB436CE49C2AADEC9103A4293E358D5BCBE569C9B61C85F7C655FABDA9`.
The expected arithmetic is `35 + 12 - 3 = 44`; recomputation found zero
file-hash mismatches.

The eight newly present exact-copy paths are:

- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapFormat.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapFormatState.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapReadException.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapReadModels.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapReadRepository.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapReadService.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadServiceTest.java`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/map/CampusMapReadRepositoryIT.java`

The ninth newly present path is a constructor-only adaptation of the accepted
map test source:

- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/CampusMapGrpcReadTest.java`
  source SHA256 `15A92266A8851B9D3CE0E97A10BA73C3693B5E9F609A58CDB27CD7354D6567F9`,
  final SHA256 `F9AC077159005EF780B01B106785832D0E0762AF3DA9A941AFCF779DF6970C5D`;
  only the required `StudentProjectionScopeService` constructor mock was
  appended and assertions/fixtures were preserved.

The three overlap final hashes are separately composed, not source-copy
claims:

- `AcademicGrpcServiceImpl.java`: `E5849ED4538C250ED6C616CB783F8C0155F5E9C5EBD64ADD60D76DDDF4D07E64`
- `StudentHomeworkGrpcIdentityInterceptor.java`: `09F12A55F148C8FE3432E44976C1C7702E48F755BC1DE1646D24825877F51B48`
- `StudentHomeworkGrpcIdentityInterceptorTest.java`: `2C40B6FC2A1CF557FA83E59A61D86661B34719BE81F034F81A52B036961E0700`

The two constructor-only test adaptations are recorded in
`i4-conflict-ledger.md`; both preserve all assertions. The shared interceptor
helper's two dependency mocks are also recorded there.

## Exact follow-up selectors

These are proposed for the parent-controlled H36 scoped execution. They were
not run during this source-only lease.

```powershell
.\gradlew.bat :services:academic-service:academic-app:test --tests ru.rutcampustrack.academic.map.CampusMapReadServiceTest --tests ru.rutcampustrack.academic.grpc.CampusMapGrpcReadTest --tests ru.rutcampustrack.academic.grpc.StudentHomeworkGrpcIdentityInterceptorTest --tests ru.rutcampustrack.academic.grpc.StudentProjectionGrpcServiceTest --tests ru.rutcampustrack.academic.studentprojection.StudentProjectionScopeServiceTest --no-daemon --no-parallel --max-workers=1 --no-problems-report
.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.map.CampusMapReadRepositoryIT --no-daemon --no-parallel --max-workers=1 --no-problems-report
.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests ru.rutcampustrack.academic.studentprojection.StudentProjectionQueryAdapterIT --no-daemon --no-parallel --max-workers=1 --no-problems-report
```

The first selector is the combined Academic unit set. The second and third
selectors are the two PostgreSQL integration tests. H36 must capture complete
stdout/stderr, command context, exit codes, nonzero JUnit XML and cleanup;
stop at the first actual failure. Historical M2/M3 evidence (98 unit cases,
6 repository IT cases, 104 total) remains historical and is not an I4 PASS.

## Preserved accepted evidence and limits

The accepted OpenAPI snapshot SHA256 remains
`D4F97E0476CD681A9460247D9F904C7901241269B73221A097EC6BE45132CDA0`, the
generated TypeScript SHA256 remains
`5D697D0D0615BB5BB93F0C4735B090D3420112B7B97F4FA6C3598CC31B2CA66E`, and
the composed `OpenApiSnapshotIT` remains
`D0D715C22A77CBADFE61177454428B606A5F0476359C2B8FD879573A4A221C25`.
No Gradle, Docker, npm, network, snapshot generation, or runtime check was
performed in I4. No producer/data/full-server or deployment claim is made.
