# B0 compact contracts — source-ready evidence

Captured: 2026-09-09 after root readback PASS and final academic proto EOF normalization. Base revision: 8002b9ea4356b10779c5bb9a6d99746d32d78ae2.
Frozen contract SHA256: C9C4DC2C4BE2229AFF3CEDB27E0E43634329EBA5AC83CE42A889E34C33DAFC74.
Implementation packet SHA256: 85387CEDC6B11A03ECB7301C10E5D437798A6DD1FBFC61A83ABB6E4DE4513D6F.
Addendum SHA256: FF05B4ED18F0E230B0543D756F49262956F08938A4980A7DD7067161B4CA450B.
Risk: S3. Writer: fresh bounded B0 implementation leaf, sole writer for this exact scope.

## Scope

The changed product set is exactly 25 paths: proto/academic.proto, proto/schedule.proto, 13 Auth API/DTO declarations, two StudentMap API/model declarations, four academic assignment/homework declarations, and four schedule transfer/lifecycle declarations. The complete path and SHA256 list is in file-sha256.md.

Evidence/status files are confined to this resume directory, except the required project current-status update. Foreign dirty and untracked work remains in place.

## Required behavior and acceptance criteria

- Academic proto preserves the accepted homework-completion delta and adds only the finite role, assignment, homework-binding, and own-user ledger. Existing fields/RPCs and tags remain; completed_at=10 is preserved. HomeworkBindingResponse field 4 is optional int64 homework_id.
- Schedule proto preserves reserved LessonResponse teacher_id tag/name 5 and adds the finite occurrence-history RPC/messages, LessonResponse fields 16..25, and LessonInfo fields 7..14.
- Auth declarations expose the frozen session, role, history, password-policy/change, and internal-admission boundaries. Public IDs, versions, and revisions are decimal strings; session sid is UUID-shaped.
- Map declarations expose the frozen manifest/floor-plan/asset/open boundaries. Optional width, height, and viewBox use property-level NON_NULL handling; StudentMapApi open returns HTTP 204, so StudentMapOpenAck/OpenAck is absent.
- Academic and schedule declarations use the frozen Java 21 record/interface boundary and exact enum values. No B1 behavior is wired.
- Only the frozen 25 product paths were written by this leaf; generated output, build files, SQL, controllers, services, wiring, OpenAPI snapshots, TypeScript, and runtime code are outside scope.

## Evidence

- Baseline guard in baseline-guard.md records exit 0 for HEAD, target inventory, foreign worktree, owned preimage status, and both proto preimage hashes.
- Root final readback reported exact25 present, HomeworkId=1, WrongHomework=0, EnumImport=1, OpenAck=0, CurrentSessionUuid=1, and Java/javaw/gradle unavailable/absent.
- Final corrected file hashes confirmed by root: proto/academic.proto 2B6206897E7145D3867D7DBA669765843BEFBA44966A4D4EC6110142100B7DBC; HomeworkPublicationPendingResponse.java 523017E5F74AC1E136FA1EF7650198DC2D132804EBCC6929E5A6123ECA41A6EA; StudentMapModels.java AF239F6357A1F182B09454B8F63830B2EA087DE83680287FD1E16247A1E64280.
- All 25 current file hashes and byte lengths are recorded in file-sha256.md.

## Checks

| Check | Command or evidence | Exit | Environment/result |
| --- | --- | ---: | --- |
| Baseline revision | git rev-parse HEAD | 0 | Windows PowerShell; base 8002b9ea4356b10779c5bb9a6d99746d32d78ae2 |
| Pre-mutation guard | guarded Test-Path/Get-FileHash loop over reserved targets | 0 | Both proto preimages present; all 23 Java targets absent before write |
| Post-write exact target hashing | guarded Test-Path plus Get-FileHash for the exact 25 paths | 0 | Windows PowerShell; file-sha256.md |
| Root correction readback | root scoped probe | 0 | exact25 and all five correction probes PASS |
| Contract compilation | .\gradlew.bat :services:auth-service:auth-api-contract:compileJava :services:academic-service:academic-api-contract:compileJava :services:schedule-service:schedule-api-contract:compileJava :services:mobile-bff:mobile-bff-api-contract:compileJava | PENDING | Separate root heavy lease; not run by this leaf |
| Proto generation | .\gradlew.bat :services:academic-service:academic-app:generateProto :services:schedule-service:schedule-app:generateProto | PENDING | Separate root heavy lease; not run by this leaf |
| Migration integration | root-owned V24/V25/V26/V17 migration checks | PENDING | Outside this declaration-only source freeze |

## Runtime evidence

Product runtime is N/A for this declaration-only source freeze. Gradle, protoc, migrations, and product runtime were not invoked by this leaf. No deploy, migration, data deletion, secret rotation, or external write occurred.

## Diff and limitations

- Owned product diff is limited to proto/academic.proto, proto/schedule.proto, and the 23 listed Java declarations. The source guard also records preservation of the pre-existing academic completion delta and foreign dirty/untracked work.
- No generated protobuf output or OpenAPI snapshots were regenerated. Compile/protoc/runtime behavior and independent Sol review remain pending root's next lease.
- The declaration-only Java shape follows the frozen packet; root/Sol must verify the public names and endpoint response choices against the critical originals during heavy checks.
- No product, contract, or scope redesign was made; no Terra escalation gate was opened.

## Proposed exact root heavy commands

Run after root's separate heavy lease:

    .\gradlew.bat :services:auth-service:auth-api-contract:compileJava :services:academic-service:academic-api-contract:compileJava :services:schedule-service:schedule-api-contract:compileJava :services:mobile-bff:mobile-bff-api-contract:compileJava
    .\gradlew.bat :services:academic-service:academic-app:generateProto :services:schedule-service:schedule-app:generateProto