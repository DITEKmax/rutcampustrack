# Fresh compact packet — guarded d3 source recovery

Captured: 2026-09-08; bounded implementation leaf; assigned model/effort: `gpt-5.6-luna` / `max`.
Working tree: `C:\Users\maksd\.codex\worktrees\34a5\rutcampustrack`.
Base `HEAD`: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

## 1. Goal

Recover the exact 39 non-overlapping paths from immutable Git revision
`d3c31acb8cce53791a4981e5858a37d44fdc9a0e` into the current checkout, with
per-path guards and byte-exact verification. This is source import only; it
does not implement the subsequent SQL/proto/DTO/features.

## 2. Context / evidence

The frozen B0 union contract requires a 42-path d3 layer before later contract
work. The recorded source-layer defect says the earlier import copied only 81
snapshot entries and skipped d3. Frozen sources read before this packet:

- `union/contract.md`, SHA256
  `C9C4DC2C4BE2229AFF3CEDB27E0E43634329EBA5AC83CE42A889E34C33DAFC74`;
- `union/paths.json`, SHA256
  `14219D88304A419E4FBDBB4FC62BBCCC190D34A3E78ED79EB6E331B6987E40EC`;
- `union/source-layer-gate.md`, SHA256
  `EAF987DAC8675AD3CE685C58D071C60A0FAEBC7CD389CB7EC78D558BF354E121`;
- `union/shutdown-checkpoint.md`, SHA256
  `F7B1FB66761A9877AA9FBCD8C1894A74E5320D6D80ECEE98A0A581FC1E4CB0EA0`.

Fresh read-only derivation: `git diff-tree --no-commit-id --name-status -r`
reported 42 paths (`22 A`, `20 M`). Comparing those paths to
`paths.json.imports` reported exactly three overlaps; the accepted source
scope is therefore `39 = 21 A + 18 M`. Existing dirty WIP, numeric evidence,
81 imported destinations and the nested UI worktree are foreign state and are
preserved.

## 3. Relevant scope

Sole product write scope is the following 39 d3 paths, in the d3 status order:

```text
frontends/mobile-core/fixtures/homework-completion-completed.json
frontends/mobile-core/fixtures/homework-completion-undone.json
frontends/mobile-core/fixtures/homework-feed.json
frontends/mobile-core/fixtures/manifest.json
frontends/mobile-core/src/api/student-client.ts
frontends/mobile-core/src/api/types.ts
frontends/mobile-core/src/features/today/TodayScreen.vue
frontends/mobile-core/src/features/today/today-screen.pcss
frontends/mobile-core/src/index.ts
frontends/mobile-core/src/shared/components/MobileBottomNav.vue
frontends/mobile-core/src/shared/components/MobileShell.vue
frontends/mobile-core/src/shared/components/mobile-bottom-nav.pcss
frontends/mobile-core/src/shared/components/mobile-shell.pcss
frontends/mobile-core/src/shared/host.ts
frontends/mobile-core/src/shared/navigation.ts
frontends/mobile-core/src/shared/shell-contract.ts
frontends/mobile-core/src/styles/tokens.pcss
frontends/mobile-core/src/test-adapter/fixture-transport.test.ts
frontends/mobile-core/src/test-adapter/homework-fixtures.ts
frontends/mobile-core/tests/contract.test.mjs
frontends/mobile-core/tests/navigation.test.mjs
proto/academic.proto
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentity.java
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptor.java
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java
services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/HomeworkCompletionRepository.java
services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptorTest.java
services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java
services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentServiceTest.java
services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/StudentApi.java
services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java
services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAcademicClient.java
services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java
services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/HomeworkJsonConfiguration.java
services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/HomeworkNoStoreFilter.java
services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentApiController.java
services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java
services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentQueryHomeworkTest.java
```

Excluded only as dependency winners, byte-preserved:
`docs/openapi/mobile-bff.json`,
`frontends/mobile-core/src/api/generated/mobile-bff.ts`, and
`services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java`.
Evidence writes are restricted to this new `resume-recovery/` directory.

## 4. Required behavior

For each modified destination, require the clean `8002` preimage before write;
for each added destination, require absence. Capture raw destination SHA before
mutation and recheck it immediately before mutation. Resolve the d3 Git blob
object ID and byte count, then write the exact blob bytes without newline,
encoding, or BOM conversion. Keep the 81 snapshot destinations and three
dependency winners unchanged. `proto/academic.proto` receives only its exact
d3 bytes; no additive B0 changes are made here. Record the import as a late
guarded recovery and never claim original sequence ordering.

## 5. Constraints

One writer in this checkout; preserve all foreign WIP and untracked paths.
Use no commit, stage, reset, clean, rollback, runtime, Gradle, npm, container,
SQL, DTO, feature, UI, deploy, migration, secret, or external-message action.
Do not edit frozen union files or historical evidence. A guard mismatch blocks
only that path and is reported to root before any overwrite; no Terra
escalation is requested.

## 6. Existing patterns

The d3 commit is the immutable source authority. `paths.json.imports` is the
81-entry dependency snapshot authority and is read-only for this task. Use Git
object IDs plus SHA256 of raw bytes for provenance. Apply the project
`rct-source-resolution` and `rct-verification` routing: source hierarchy and
evidence are explicit, and every check records revision, command, exit code,
environment, and artifact.

## 7. Acceptance criteria

All 39 destinations equal their exact d3 blobs; all 21 additions were absent
before import; all 18 modifications passed the 8002 preimage guard. All 81
snapshot destination raw hashes and the three overlap winner hashes remain
unchanged. Effective source layer is exactly 39 d3 paths plus 3 preserved
dependency winners. No unexpected dirty/untracked path changes occur outside
the 39 product paths and this evidence directory. The frozen manifest includes
status, source Git blob, bytes, raw SHA before/after, and final verification.

## 8. Verification

Run mechanical read-only guards before mutation and immediately before each
write, then verify all 39 bytes, 81 snapshot hashes, three overlap hashes,
effective 42 membership, and full scope status including untracked files.
Record every actual command and exit code in `checks.json`, with a per-path
manifest and runtime status. Product runtime is `N/A` because this is source
recovery only; do not rerun previously passed product checks.

## 9. Do not

Do not import mutable live files, overwrite any unexpected destination, modify
the 81 snapshots or 3 overlaps, touch SQL/proto augmentation/DTO/UI, alter
existing evidence, create children, run product runtime, claim B0/full-role
PASS, or continue writing after the recovery evidence is frozen for root.
