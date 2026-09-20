# B0 correction runtime pass — 2026-09-10

Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
State: `B0_CORRECTION_RUNTIME_PASS_REVIEW_PENDING`.
Risk: S3. The explicit `A_RELEASE`/GO heavy bundle completed. Heavy lease is
`RELEASED`; active review is next; current command/session is none and no root
session is pending. Foreign dirty and untracked work remains preserved.

## Exact HEAVY GO bundle and results

1. Session `67148` — exit `0`, `BUILD SUCCESSFUL` in 48s, 9 tasks/2 executed:

```powershell
.\gradlew.bat :services:academic-service:academic-app:generateProto :services:schedule-service:schedule-app:generateProto --no-daemon --no-parallel --max-workers=1 --console=plain
```

2. Session `3617` — exit `0`, `BUILD SUCCESSFUL` in 1m19s, 24 tasks/7
executed:

```powershell
.\gradlew.bat :services:auth-service:auth-app:test --tests 'ru.rutcampustrack.auth.contract.AuthTokenDtoRedactionTest' --no-daemon --no-parallel --max-workers=1 --console=plain
```

Fresh XML: `services/auth-service/auth-app/build/test-results/test/TEST-ru.rutcampustrack.auth.contract.AuthTokenDtoRedactionTest.xml`, SHA256
`A9237D7409B8B545E026D4D030CC1899CF10E0FCCC6A8E41AA90A5EC5CEC4E46`, 815
bytes, mtime `2026-09-09T21:47:05.7131033Z`, tests 3, failures 0, errors 0,
skipped 0.

3. Session `83350` — exit `0`, `BUILD SUCCESSFUL` in 1m52s, 39 tasks/19
executed:

```powershell
.\gradlew.bat :services:mobile-bff:mobile-bff-app:test --tests 'ru.rutcampustrack.mobilebff.contract.StudentMapModelsWireContractTest' --no-daemon --no-parallel --max-workers=1 --console=plain
```

Fresh XML: `services/mobile-bff/mobile-bff-app/build/test-results/test/TEST-ru.rutcampustrack.mobilebff.contract.StudentMapModelsWireContractTest.xml`, SHA256
`4087EA9C1F2027F126B7B34A03BDAD0736CFFC2C1FC02BFEE2ED7B1E2578BA4D`, 1013
bytes, mtime `2026-09-09T21:49:11.5630942Z`, tests 4, failures 0, errors 0,
skipped 0.

Total focused result: 7/7 tests passed. The mobile build emitted 15 existing
deprecation warnings under the attendance dependency; they had no failures and
were unrelated to this correction scope.

## Post-run source/test guard

The exact source/test hashes remained the source-ready values. `proto/schedule.proto`
was also unchanged:

| Path | SHA256 | Bytes |
| --- | --- | ---: |
| `proto/academic.proto` | `1A9FB1B357AC394AFC44906FE6888756DE1B1329E729F6BEB695B25534DB4B45` | 10670 |
| `proto/schedule.proto` | `44FFABEB7965D5935D481E1628C3C1DD590CA15E1323E09E2E82376A22ACE858` | 5836 |
| `StudentMapModels.java` | `62831E2419009CD60361E60F2A139492D07DD54B4A91DBD095BC69B9AA63C6E2` | 5153 |
| `AuthAdmissionRequest.java` | `A874654D1E410590F43E59B11318F80C6C35A32AC9D0A32D940A53665A089452` | 625 |
| `AuthAdmissionResponse.java` | `FA6559334DA8072F248033528040B4210A7792F58EAE8EE159E26F7801F802A7` | 2780 |
| `SelectActiveRoleResponse.java` | `613957796DCA67F271ECB876D45791CECB7719C5DDE07A96C7D06893553777F2` | 1072 |
| `StudentMapModelsWireContractTest.java` | `06C041BBE4E72D534E39FC45742D14DC290180A8318BE5FCEF12A208A0BFC459` | 4678 |
| `AuthTokenDtoRedactionTest.java` | `B9F1CDFC799116D9D24B5BBFD62DD583FCA9E51E7269A3E4E1CDFF6722714649` | 4668 |

The post guard observed no `java`, `javaw`, or `gradle` process; `gradle` command
was absent and `gradlew.bat` was present. Result: `PROCESS_GUARD_CLEAR_RELEASED`.

## Verification, runtime, and limitations

| Check | Exit | Evidence |
| --- | ---: | --- |
| Academic/schedule proto generation | 0 | Session 67148, `BUILD SUCCESSFUL`, 48s |
| Auth redaction focused test | 0 | Session 3617, XML 3/0/0/0 above |
| StudentMap wire focused test | 0 | Session 83350, XML 4/0/0/0 above |
| Post source/test hash guard | 0 | Exact values above; unchanged from source-ready handoff |
| Process/session guard | 0 | `PROCESS_GUARD_CLEAR_RELEASED`; no pending root session |

No SQL, Docker, TypeScript, OpenAPI, service/product runtime, deploy,
migration, data, or secret operation occurred. Accepted SQL7 was not rerun.
The focused Gradle checks provide runtime evidence for the changed semantic
boundaries; they do not claim product runtime or full B0/full-role acceptance.

Fresh independent Sol full exact25 plus both focused tests review remains
required, including an explicit Auth13 subset recheck. No B1 or full-role claim
is made. The two global `--tests` filters intentionally gave one matching class
to each Test task; because all three tasks exited 0, no no-match workaround was
used. No Terra escalation gate was opened.