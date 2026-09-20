# B1a → E stable handoff manifest

## Goal and state

This compact manifest hands the accepted B0 and SQL foundations, plus the
final B1a overlay, to E/root. It records stable hashes, focused checks,
runtime evidence, and the independent affected-scope review without claiming
the full A/C/B/E cutover.

- Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
- Risk: S3 (authentication/authorization, public contract, and mutation
  boundaries).
- Frozen B1a contract: `.agent/student-academic-b/b1a-session-bridge/contract.md`,
  SHA-256 `524F3E16E878075BC03D1E56D8CDCD188EA4A403168A29C758996E27570B3F62`.
- State: `B1A_SOURCE_EXPORT_RUNTIME_REVIEW_ACCEPTED_RELEASED`.
- Integrated A/C/B/E runtime and full-role acceptance: `OPEN`.

## Accepted predecessor foundations

### B0 exact25

The accepted exact25 source manifest is
`.agent/student-academic-b/union/resume-contracts-2026-09-09/file-sha256.md`,
SHA-256 `790081134DF7467362C844144345EF471B18B8F8D98A386A92DB448E2FE36097`,
4,944 bytes, with exactly 25 product paths. Its accepted focused runtime
artifact is
`.agent/student-academic-b/union/resume-contracts-2026-09-09/final-contract-repair-runtime-pass-2026-09-10.md`,
SHA-256 `8C9AC228AB60D854DCE5CAFB793EF07BD28B0F0728DA433A69DD4D4A7EC6E997`,
3,751 bytes. The recorded final verdict is
`AUTH13_SUBSET_PASS + B0_EXACT25_CONTRACT_SOURCE_PASS` (focused Auth/Map
runtime `11/11`).

Two accepted focused test paths are evidence-only B0 repair postimages and
are intentionally outside the exact25 source manifest; the other five B0
repair product rows are already present in exact25 and are deduplicated here.
Authority for the exact table7 postimages is
`.agent/student-academic-b/union/resume-contracts-2026-09-09/final-contract-repair-source-ready-2026-09-10.md`,
SHA-256 `10C7198D5DF9AC73B14368707A37681E2C082605F09E9C7517484DDB00F239DE`,
10,481 bytes.

| Accepted B0 focused test path (outside exact25) | Bytes | SHA-256 |
| --- | ---: | --- |
| `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/contract/AuthTokenDtoRedactionTest.java` | 7,332 | `F2F304840D1B2CEFA55E4FC90AD56F0875731624A602EEDE372842264601EEEE` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contract/StudentMapModelsWireContractTest.java` | 6,472 | `FEA79FABF1A245B82A45525D00A10297D55D1C06F3AE8C20385108152DFF2115` |

### Accepted SQL foundations

The accepted SQL foundation is
`.agent/student-academic-b/sql17-v26-coverage-repair/manifest.json`, current
SHA-256 `F726A92A1A5608EA1E8389D55C17CD2809CC63110D0BED07D5098158A4A741FC`,
6,556 bytes, status `SQL17_V26_EXACT4_ACCEPTED`, scope `RELEASED`, and runtime
aggregate `22/22` after the prior `25/25` baseline. This manifest and its
accepted SQL17/V26 exact4 rows supersede only the four older V17/V26 rows and
their migration-test evidence. The unchanged V24, V25, and Flyway rows below
remain preserved accepted SQL7 predecessors.

| SQL foundation product/test path | Pre bytes / SHA-256 | Accepted post bytes / SHA-256 |
| --- | ---: | ---: |
| `services/schedule-service/schedule-app/src/main/resources/db/migration/V17__student_occurrence_homework_binding.sql` | 18,760 / `8D02720F48B67E21C78373B214ADB414A2F435A8016C35987839D330B6042FCC` | 18,760 / `8D02720F48B67E21C78373B214ADB414A2F435A8016C35987839D330B6042FCC` |
| `services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql` | 17,109 / `21B3F522634BE7505FB73A613BF5BCB68C1584B7A1724BB45EBA5C767E4058A0` | 17,109 / `21B3F522634BE7505FB73A613BF5BCB68C1584B7A1724BB45EBA5C767E4058A0` |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/StudentOccurrenceMigrationIT.java` | 23,649 / `29433B97247AE23E8BFF9EA83AD94768DC80A329276E565AE3E3C5063F5928D2` | 28,608 / `633285777706AF26FEF3B847B172BA1805C41E6607B5170025F4DE6A02AF1A68` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/migration/StudentFoundationMigrationIT.java` | 51,464 / `8A39EC859D8CF2BA2699295A0D60B8B10FA6988B2AA0DB8BCD3E2434115D446B` | 58,295 / `14C68464CFBD29EDF20C87021D9353863C81E5B0044C193D09930FBBCEE7FB8B` |

### Preserved SQL7 predecessor rows

| Accepted SQL7 predecessor path (unchanged) | Current bytes | Current SHA-256 |
| --- | ---: | --- |
| `services/academic-service/academic-app/src/main/resources/db/migration/V24__auth_session_authority.sql` | 7,618 | `1D15418FA4869D1288B3FA25F688A237087294F360AEA6729F524DE2F5B67F90` |
| `services/academic-service/academic-app/src/main/resources/db/migration/V25__student_subject_homework_foundation.sql` | 8,987 | `52C1782848B5C333975F9162D56F544A053C12207018876BC6E1D5881B42DCA3` |
| `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/migration/FlywayMigrationIT.java` | 6,163 | `E608B6228872750870E0A4D66EA792AF7029148D544F936DB8B9BF3773D955A5` |

SQL7 provenance is `.agent/student-academic-b/sql7-review-pass-2026-09-09.md`,
SHA-256 `2634EF32E6B6DDD003A8547EAE837EB154AB54F4639ADE1AC7A39362EC543EB2`,
948 bytes, `PASS` with no findings, plus
`.agent/student-academic-b/sql7-runtime-pass-2026-09-09.md`, SHA-256
`BA8511323AEF075BF358445FA58EAF2545B178140A2D3965265AF31176BA2241`,
2,140 bytes, exit `0`, total `17/17`. The latest SQL17/V26 exact4 manifest
supersedes older evidence only for its four V17/V26 rows; these three SQL7
predecessor rows remain accepted and unchanged.

The accepted SQL17/V26 runtime rows are `StudentFoundationMigrationIT`
session `75913`, exit `0`, 15/0/0/0, and `StudentOccurrenceMigrationIT`
session `2390`, exit `0`, 7/0/0/0. The prior accepted Flyway result remains
3/0/0/0 and was not rerun in this handoff. The SQL foundation set is therefore
7 unique current accepted rows: four latest SQL17/V26 rows plus three
preserved SQL7 rows.

## Final B1a overlay

The frozen B1a overlay contains the following 31 unique current product,
test, export, and fixture paths. The final two rows are the affected
authorization/no-store filters added by the final review correction. No
evidence file is included in this table.

| Path | Bytes | SHA-256 |
| --- | ---: | --- |
| `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java` | 11,960 | `2DB65DA1E5BC4291CEA4AC774FA8B48E08B862C3C0BDE41E906157907374B94D` |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java` | 17,887 | `B7554255788D8BE9844E6BA7327831821AC4A10DF478C68D7018D4D29FFBA52A` |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentCheckinFacade.java` | 4,376 | `2BA6F7D4F7280FE7512824AC43DE176F7184522C8DE965509A962FB2CD2917F3` |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java` | 3,647 | `8A5AF1A9E0CC1F5E21C5D646A80698747CCB49EB292744E7716D4D21E51D7192` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentSessionProjectionTest.java` | 6,815 | `192F9524AB6CB1B478320464C9B5D00A38EC0E0A3A4B1FEC99D3A583B9015067` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/student/StudentQueryHomeworkTest.java` | 9,762 | `66222CB32D9603B64DEC3B627A6DC8B3D7C7E60FBDC24D193FCD7389B779C926` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java` | 33,121 | `B9BFCEC82ECC593F767CB79D12DC40133382BFC3E756552A19619CA11BCD4EEB` |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHttpGrpcAuthIT.java` | 23,165 | `16D5A6FD7EAF3A0CB2890B811151491C64A120CAA56ADCBC571229D907AC92C4` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptor.java` | 2,296 | `9E9D36ADB46F6D17C6A3580721B0F3832D7F0EA7E5772C39B7F3FBBE747285FE` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java` | 26,931 | `74F50DB48D1C894463DFAA584A7E408F1A361CC97525C2A48716A755A2C454D5` |
| `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java` | 5,558 | `6F858BCFACDE2D6B774F104867F0A05334B87600FB442435AEF0C1AFF75F13E2` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentHomeworkGrpcIdentityInterceptorTest.java` | 10,773 | `5880F499E6B6F47E8016D6D114F2A77D2825D240BDA957465031997F2BB4BFC0` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/grpc/StudentHomeworkTestIdentity.java` | 520 | `33268F18FADF0777789D132CB57CB5B7C743899DFAAAA683B74CA48C4E7AD981` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentServiceTest.java` | 6,491 | `8461A7FF88C33494D631465D7394751BCDAB72AD6A5465CA9931AEDFEDD53C4A` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java` | 16,204 | `F7F0DF78DA4FEDDB8E88E37106463B0498C43F8C36582215E3F0089FD0004B27` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterIT.java` | 4,677 | `DA1561E4DF683931463702979BD9714C3AE69EA209B1A5C8A712C8D0C10FE064` |
| `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/security/AcademicUserContextFilterStrictModeIT.java` | 2,394 | `48F12FCC4E49FADC808DCF3062271C18F1BC0BFE6756F6169FDC90A7A7381A6C` |
| `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java` | 14,507 | `65E22D76DEAE51C19B1D78E051C2B3984700EEC87039722B4B35E546D1E46F68` |
| `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinModels.java` | 2,193 | `4A7BD896F3BEE25F71A481BC40EAF0A4388CBED31A9FF1D6FABEC6656C9E51B3` |
| `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java` | 21,590 | `CA05ADB91F6D401AAE467205C3D40A226FB5E21529FCF846E89320F6CD29EBF9` |
| `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceTest.java` | 11,847 | `1C787B5CF811702CCD58CA659EA53AE634ECF6689DC1D2AC4E9DF4172B6FF5E0` |
| `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/StudentGrpcBoundaryTest.java` | 6,852 | `2D27F51941C937A8A3ECF05E16147C46C865BF9140A3A8DFBE58C6EC6DA1BA02` |
| `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentAttendanceSnapshotServiceTest.java` | 5,345 | `A608C69001F7931BBE6A0E8A11C93E34874AD94C0E17E1040F626BCFA16E2041` |
| `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/student/StudentCheckinTransactionIT.java` | 34,487 | `F1DA76C86401EDCCDB6DFA46027B0E839612DB72F385CF0692FEFEC103D413F3` |
| `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterIT.java` | 4,253 | `F282CB66CBAAED2FCEC32CE21F3D0E75D53D3BDFB80A40F7C8BC9F7294A720E7` |
| `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/security/AttendanceUserContextFilterStrictModeIT.java` | 2,023 | `001B16CDA5BED27A70F32C0636468922F1A5EA4C30F2118A432F31C0790FE746` |
| `docs/openapi/mobile-bff.json` | 35,516 | `80E484784A287C3A0468AD4F472469C49E242CB3025FC54BFCEBDD915DA4A53F` |
| `frontends/mobile-core/src/api/generated/mobile-bff.ts` | 26,279 | `D4C240E583B1CC7C430FBAB22B12EB96AE8AE82C04BA09481829361698F2A58A` |
| `frontends/mobile-core/fixtures/session.json` | 792 | `E0E7E4226BEFD1CF64626792690AA1493E4F0630EE63A586C0AA608234865C86` |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/security/MobileIdentityFilter.java` | 5,675 | `F9AAF4687541972E316FAE01ED268158856DD8886FFB8A6B6B1148BC1C50322A` |
| `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/HomeworkNoStoreFilter.java` | 2,392 | `0DDD839E170C6BDB62DF21CFD19E30B4371037DB82274A623880DF6BDC5A044F` |

The path-set guard found `31/31` existing paths and `31` unique paths with no
proto, SQL/migration, build/config, shared-security, or frontend-consumer
edit. The generated TypeScript row is the official generator output; no
manual generated-file edit is part of the overlay.

## Accepted behavior and review

The original B1a contract/evidence establishes canonical session projection,
read-only mutation rejection before validation/dependencies, terminal
Homework reads and signed-claim gRPC forwarding, attendance read/mutation
boundaries, and Java-first OpenAPI/TypeScript/fixture outputs. The final
affected review closes the four overlay defects: check-in `no-store`, matrix
matching in `MobileIdentityFilter`, STUDENT-only read-only role precedence,
and matrix matching in `HomeworkNoStoreFilter`.

Fresh independent review: `/root/b1a_final_affected_review`, model
`gpt-5.6-sol`, effort `high`, `fork=none`, read-only. Verdict: `PASS`; findings:
none at critical/high/medium severity. This review accepts the stable B1a
overlay only; full integrated A/C/B/E and full-role acceptance remain open.

## Checks and runtime evidence

The original B1a final evidence records these passing checks (all exit `0`):
BFF units `StudentSessionProjectionTest` 3/3 and `StudentQueryHomeworkTest`
8/8 (session `48044`); Academic units 8/8 and 7/7 (session `65134`);
Attendance units 6/6, 15/15, and 4/4 (session `65646`); BFF IT 8/8 and 13/13
(session `99648`); corrected Academic concurrency IT 6/6 (session `41241`);
Attendance IT 21/21, 6/6, and 3/3 (session `60872`); Java test compilation;
OpenAPI update (session `58919`) and snapshot (session `62239`); official
TypeScript generation and generation check; frontend contract `11/11` and
foundation typecheck; final scoped whitespace check and process guard.
The first shell-property and Academic fixture failures remain recorded as
historical corrections in the original evidence and are not relabeled.

Latest affected runtime sequence is retained exactly:

| Check | Session / result | Exit |
| --- | --- | ---: |
| Role-precedence compile | `6230`, `BUILD SUCCESSFUL`, 28 tasks / 2 executed | 0 |
| Combined Homework + Check-in IT before test-expectation correction | `65348`, 47 tests, Homework 19/1/0/0; Check-in 28/0/0/0; Homework active matrix assertion expected RPC 1, observed 2 | 1 |
| Exact4 compile after bounded correction | `33773`, `BUILD SUCCESSFUL`, 28 tasks / 2 executed | 0 |
| Homework-only IT after exact4 correction | `64634`, `BUILD SUCCESSFUL`, Homework XML 22/0/0/0 | 0 |

The failed `65348` run is retained as a defect record; the correction changed
only the test expectation to account for `getActiveSemester` plus
`setHomeworkCompletion`. The prior `Check-in 28/28` result remains valid.
Older unauthorized/audit-only sessions are not relabeled as acceptance.

Final B1a evidence is
`.agent/student-academic-b/b1a-session-bridge/implementation/b1a-readonly-ingress-repair-evidence-2026-09-11.md`,
SHA-256 `DF7142BA652BC3B45D0B3D99E4B30C8253FC26D918880633632B139848421A2A`,
43,638 bytes. Its final exact4 hashes are the two additional filter/test
rows plus the stable IdentityFilter, Homework IT, and Check-in IT rows in the
overlay table above. No new runtime was run for this manifest.

## Handoff verification performed for this manifest

The following read-only checks are required and recorded for the final
artifact; they do not run Gradle, npm, Docker, or product runtime:

| Check | Command/evidence | Exit |
| --- | --- | ---: |
| Predecessor SHA/bytes | `Get-FileHash`/`Get-Item` for the exact25 and B0 runtime artifacts | 0 |
| SQL manifest syntax/hash/completeness | `ConvertFrom-Json` plus whole-file SHA/bytes for `manifest.json`; 7 unique current accepted foundation rows (4 SQL17/V26 + 3 preserved SQL7) | 0 |
| B1a overlay SHA/bytes/path set | 31/31 present, 31 unique, all current hashes match | 0 |
| Disallowed-path guard | no `proto`, SQL/migration, build/config, shared-security, or frontend-consumer path in overlay | 0 |
| Manifest readback | required state, predecessor verdicts, latest sessions, review, and limitations present | 0 |
| Scoped whitespace | `git diff --check -- .agent/student-academic-b/b1a-session-bridge/implementation/b1a-stable-handoff-manifest-2026-09-11.md` | 0 |
| Process guard | `Get-Process -Name java,javaw,gradle,npm` zero-match normalization | 0 |

No deploy, production migration, data deletion, secret operation, ACL/cache
mutation, cleanup, stage, commit, or reset was performed. Foreign dirty and
untracked work remains preserved. Full A/C/B/E runtime and full-role review
are the next root/E scope decision.

## Artifact identity

- Final path: `.agent/student-academic-b/b1a-session-bridge/implementation/b1a-stable-handoff-manifest-2026-09-11.md`.
- Overlay path count: `31` unique paths.
- Whole-file bytes and SHA-256 are emitted by the post-write verification and
  handed to root so the artifact does not contain a self-referential hash.
