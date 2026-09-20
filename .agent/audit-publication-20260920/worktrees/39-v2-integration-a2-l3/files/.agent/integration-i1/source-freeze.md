# I1 source freeze

Frozen base and source revisions:

- E/base: `b8220ac92125a8afa37598b270aa4fab7aa1f470`.
- A2 source: `codex/v2-access-scope` at E, source worktree unchanged at freeze.
- L3 source: `codex/v2-binding-proto` at E, source worktree unchanged at freeze.
- Target: `codex/v2-integration-a2-l3` at E before the union.

The target path and branch were absent before creation. Root checkout was dirty
with unrelated tracked and untracked work; it was not used as a source and was
not modified. The source worktrees were read-only inputs. A2's 14 product rows
matched `.agent/access-a2/manifest.sha256` exactly, including canonical manifest
SHA256 `0A033E8E07EC57AD6219844512D0232C666BC71385F3A844D24F4C28C13A7923`.
L3's five accepted product rows were hash-checked against its current source
worktree; its two proto post-change hashes match the L3 evidence.

## Exact source rows

The following A2 rows were copied exactly, except `proto/academic.proto`, which
is a deliberate composition and is recorded separately below:

| Path | A2 source SHA256 |
| --- | --- |
| `services/academic-service/academic-app/build.gradle.kts` | `0DAB34508BE348798C1848A3D8D0A0C0C31BB7E923C0B9D66DC3E34D28FD4DF2` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java` | `E7C73C72DB01F14FBAC89DE7DDC083498DB1B8F8DAD7E73CDFFF50C71E1E72D4` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptor.java` | `ABD2FD0A1C8DF359DFA30C50D94D1587A6029B47472088F97135CF2601FC0CD9` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/JdbcStudentProjectionQueryAdapter.java` | `61E8AC56B2E934D13AECD0844E40606DF64384A6089C4F2EF96FC39D9A13D504` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionException.java` | `57FD0A2C1A1336FF3272029A1AEF5D305AEFD145AAFA92BA5A841B0E9E8D7F2F` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionGrpcErrors.java` | `68F243B3D885EE83E75929C2E8265A060631BE07CCC068EAB1FC8CE0845DE777` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionQuery.java` | `0B4364B835A84590D8C52DC2E581D1AA71FB96626084E4AD0A49CDF32C266F35` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScope.java` | `254CFDD585D7903CAA9C6E1B99C13F140380BA0EFC708C4E67065A07FA6B0E06` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeService.java` | `3C38A1805664C2339E6E64C02870AEAA79EB1A8376CCDE1BF25DF2C4ABEC2774` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptorTest.java` | `8BAF484F84265F1B8E6F27641DDEC1D400EDD42D7B72755FFAF0700F86B501BA` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentProjectionGrpcServiceTest.java` | `8AC3F337367249CF36D5655D72FD204A0A742F2852CF773174EEB1CF72C5146C` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionQueryAdapterIT.java` | `C30FC24412AA557FCC2736F49D367ED25CC1BA339710AD47B46D70FDE8659318` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeServiceTest.java` | `FC985AA7310ADC039B87F08C1964936874F37ED465CA545915542BED40796BFD` |

The following four L3 rows were copied exactly:

| Path | L3 source SHA256 |
| --- | --- |
| `proto/schedule.proto` | `984538E5ED0414F06A9BB9CAE4AF2BC981CB50A32190EF5FA06B0858C1AAB0FB` |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilterIT.java` | `9314F3404E0FC6FA702106F467A180E4296F32ED86375652881A976B29C0F3DA` |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilterStrictModeIT.java` | `D2FCF168798DA4676D25557C6421E5A43400EA146D6836429BCBF3815066BCB1` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contractexport/OpenApiSnapshotIT.java` | `3886D055A127DF7BA2DCDE605334926D25938963209FDF3BB3D1F0F0FD96C52C` |

## Deliberate proto composition

`proto/academic.proto` is the only overlapping path. Its E/base SHA256 from
the L3 source evidence is
`406436542CBD50E588ABA80FD9821EF4D2FA88C680FC85038705BCAE4322257E`.
The accepted A2 source SHA256 is
`BF7C37FEFD9253F347B4DA9C07696C5ACBDD45DD6B4E3378103B32CACCB9966C`; the
accepted L3 source SHA256 is
`980C12514EB3E2B1E1C90A94207115CC70A2AC64458BAB4B99F14467016C45B9`.
The target composed SHA256 is
`2EF807ACD1E3A31057A553B01EAFE466ABA87BBC16D62B5D4C30C165C9CBCD32`.
The canonical 18-path union manifest is recorded at
`.agent/integration-i1/union-manifest.sha256` with SHA256
`EB831B86E3E61559B4B9B3755AD36F69F582039CB6D460F88B003544E210DC85`.

Composition preserves every A2 resolver declaration, tag and reserved field,
while applying only L3's approved removal of `schedule.proto` import, the three
Academic binding RPC declarations and the binding enum/messages. The identical
binding declarations/messages are present once in Schedule with qualified
`rutcampustrack.schedule.LessonInfo current_lesson = 3`.

No source AGENTS files, source evidence, generated output, lockfile, migration,
cache or unrelated product path was imported.
