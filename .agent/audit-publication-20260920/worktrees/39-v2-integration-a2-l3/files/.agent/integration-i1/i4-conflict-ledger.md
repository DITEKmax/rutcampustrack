# I4 conflict ledger

Baseline: `b8220ac92125a8afa37598b270aa4fab7aa1f470`  
Source manifest: `.agent/v2-map-read/source-manifest.json`  
Source manifest SHA256: `E16EEBA8BA3FBF0260E1BEE755D0CC45C859358CDFBE4F097CB8CB687371324C`

| Target path | Accepted source SHA256 | Composed target SHA256 | Decision |
| --- | --- | --- | --- |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java` | `C29987CCCB30ACA6C2DE2ECFA2DF2019A3CD4C9FC4AE6C3CF25591A8976A8876` | `E5849ED4538C250ED6C616CB783F8C0155F5E9C5EBD64ADD60D76DDDF4D07E64` | Kept A2 projection import, field, constructor dependency, handler and converters; added the M2 map dependency, three handlers, converters and safe error mapping. |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptor.java` | `3D7267482244397C457F5AD716B0AE575C08C82EDF525F01DAD253CDC5691FA5` | `09F12A55F148C8FE3432E44976C1C7702E48F755BC1DE1646D24825877F51B48` | Unioned A2 projection admission with M2's three map method registrations and retained homework registrations. |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptorTest.java` | `498758B8803E8E179150137447B1EA5D28757A71276F587CB0C7288D97D175A2` | `2C40B6FC2A1CF557FA83E59A61D86661B34719BE81F034F81A52B036961E0700` | Kept A2 projection/homework and negative assertions; added M2's map parameterized positive/negative cases and nullable missing-token fixture. |

The eight exact-copy non-overlap paths were transferred only after the source
manifest hash comparison. Their target hashes are recorded in
`i4-union-manifest.sha256`. `CampusMapReadModels.java` uses the manifest's
destination SHA `55AFEBC8…5EA2D` (the manifest separately records the external
candidate SHA `6FAB3B05…BD0A0`).

The ninth new path, `CampusMapGrpcReadTest.java`, was transferred from the
accepted source and then received only the constructor adaptation recorded
below; its source and final hashes are recorded in `docs/sources/manifest.yaml`
and the freeze above.

Allowed constructor adaptations:

- `StudentProjectionGrpcServiceTest.java`: appended a mock
  `CampusMapReadService` argument after the existing projection resolver;
  assertions and fixtures are unchanged. Final SHA256:
  `88B88E029C9E050F18B8B3476D07C4B450553EEB00F69E9C7D3F19765D4A5DF9`.
- `CampusMapGrpcReadTest.java`: appended a mock
  `StudentProjectionScopeService` argument before the map service; assertions
  and fixtures are unchanged. Final SHA256:
  `F9AC077159005EF780B01B106785832D0E0762AF3DA9A941AFCF779DF6970C5D`.
- The shared interceptor test helper supplies both dependency mocks for the
  composed `AcademicGrpcServiceImpl`; this is required by the same constructor
  union and leaves all test assertions intact.

No additional product path was required. Existing A2/L3/R5/I3 rows outside
the stated overlap and constructor adaptations were preserved; no source
worktree file or source evidence was written.
