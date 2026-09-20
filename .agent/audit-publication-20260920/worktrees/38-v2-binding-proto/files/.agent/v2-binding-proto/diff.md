# Stable source diff

Base: `b8220ac92125a8afa37598b270aa4fab7aa1f470`.

The proto product diff is limited to two proto files (`0` additions/`53` deletions in
Academic and `51` additions/`0` deletions in Schedule):

- `proto/academic.proto`: remove `import "schedule.proto";`; remove the three
  binding RPC declarations from `AcademicGrpcService`; remove
  `HomeworkBindingState` and the five binding messages.
- `proto/schedule.proto`: add the same three RPC declarations to
  `ScheduleGrpcService`; add the same enum/messages in package
  `rutcampustrack.schedule` with unchanged declaration text, field numbers,
  wire types, optionality and enum values.

The moved response retains
`rutcampustrack.schedule.LessonInfo current_lesson = 3`; request/response
collection fields remain `occurrence_ids = 1` and `bindings = 1`.

The separately approved H1 correction changes only these two existing Schedule
security tests:

- `ScheduleUserContextFilterIT.java`: replace removed `expiredToken` and
  `invalidSignature` calls with complete `buildToken` calls, and update two
  valid token calls to the current nine-argument fixture API.
- `ScheduleUserContextFilterStrictModeIT.java`: update its valid token call to
  the current nine-argument fixture API.

Assertions, endpoint paths, expiry/signature semantics, and legacy-header
precedence remain unchanged. Shared JWT factory and production code are not in
the correction diff. After H8 exposed one additional existing baseline call,
main authorized exactly one third test-file correction:

- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contractexport/OpenApiSnapshotIT.java`:
  add `java.util.UUID` and replace the stale four-argument token helper call
  with the current nine-argument API using fixed session UUID
  `88888888-8888-4888-8888-888888888888`, versions `1L`, `"STUDENT"`,
  `"ACTIVE"`, group `10L`, headman false and read-only false. User `100L` and
  all OpenAPI assertions remain unchanged.

The final product/test diff is therefore five files: the two protos and these
three approved tests. No generated source, factory, production code,
dependencies, configuration or OpenAPI assertion changed.

Stable commands:

```text
git diff --check -- proto/academic.proto proto/schedule.proto
git diff --unified=0 -- proto/academic.proto proto/schedule.proto
git diff --check -- services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilterIT.java services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilterStrictModeIT.java services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contractexport/OpenApiSnapshotIT.java
```

The exact proto and three-test diff has no unrelated hunk. H9 reused the
mechanically verified fifteen-task array in
`.agent/v2-binding-proto/h8b-task-args.json` and returned exit 0 for all five
modules' generateProto, compileJava and compileTestJava tasks. Its raw log is
`.agent/v2-binding-proto/h9-gradle.log` (SHA256
`A9098B6607B642A9AE9BA956A050ACFA8844C0EC5BE7912E7016C2992EB64787`); 24
classpath reports parsed with zero capture errors. The local metadata
`AGENTS.md` pointer and `.agent/v2-binding-proto/*` evidence are separate from
the five-file product/test diff and are owned by this worktree.
