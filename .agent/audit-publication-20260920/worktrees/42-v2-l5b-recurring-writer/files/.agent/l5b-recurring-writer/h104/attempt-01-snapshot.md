# H104 snapshot attempt 01

- Packet: `L5B-RECURRING-GENERATION.md`
  SHA256 `27E659D0318BC84EA363A4630D7CAD9EEFA2368B5E2ADE6EC94896AA61FBB0BE`.
- Command requested by the packet:
  `gradlew.bat :services:schedule-service:schedule-app:integrationTest --tests '*OpenApiSnapshotIT' -Popenapi.snapshot.update=true --no-daemon --no-parallel --max-workers=1 --no-problems-report`.
- Host start: `2026-09-20T18:35:47.2710821+03:00`.
- Host end: `2026-09-20T18:35:58.0404821+03:00`.
- Exit code: `1`.
- Result: Gradle parsed `.snapshot.update=true` as a task and stopped before
  executing the integration task. No snapshot or generator step ran.
- Correction: invoke the same command through `cmd.exe` so the dotted `-P`
  property reaches Gradle as one argument. This is a wrapper invocation fix;
  no product or dependency change was made.
