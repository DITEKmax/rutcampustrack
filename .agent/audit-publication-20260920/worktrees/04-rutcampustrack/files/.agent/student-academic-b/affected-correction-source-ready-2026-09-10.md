# B0 correction source-ready handoff — 2026-09-10

Base revision: `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.
State: `B0_CORRECTION_SOURCE_READY_WAIT_A_RELEASE`.
Risk: S3. This is an evidence/status handoff after source-only corrections;
active child0 is finished, heavy work is `WAIT_A_RELEASE`, and no root session is
pending. Foreign dirty and untracked work remains preserved.

## Scope and criteria

The reviewed FAIL families are fixed in source only: the Campus Map proto/model
wire corrections and the three auth token DTO text redactions. The auth focused
test checks three distinct synthetic sentinels, literal `<redacted>` text,
exact `accessToken`/`internalToken` JSON properties and values, and deserialization
round-trips. The map focused test guards its wire/null/enum boundary. No runtime
PASS, full B0, full-role, or B1 claim is made.

## Accepted current product and test hashes

These values were recomputed read-only after the source corrections:

| Path | SHA256 | Bytes |
| --- | --- | ---: |
| `proto/academic.proto` | `1A9FB1B357AC394AFC44906FE6888756DE1B1329E729F6BEB695B25534DB4B45` | 10670 |
| `StudentMapModels.java` | `62831E2419009CD60361E60F2A139492D07DD54B4A91DBD095BC69B9AA63C6E2` | 5153 |
| `AuthAdmissionRequest.java` | `A874654D1E410590F43E59B11318F80C6C35A32AC9D0A32D940A53665A089452` | 625 |
| `AuthAdmissionResponse.java` | `FA6559334DA8072F248033528040B4210A7792F58EAE8EE159E26F7801F802A7` | 2780 |
| `SelectActiveRoleResponse.java` | `613957796DCA67F271ECB876D45791CECB7719C5DDE07A96C7D06893553777F2` | 1072 |
| `StudentMapModelsWireContractTest.java` | `06C041BBE4E72D534E39FC45742D14DC290180A8318BE5FCEF12A208A0BFC459` | 4678 |
| `AuthTokenDtoRedactionTest.java` | `B9F1CDFC799116D9D24B5BBFD62DD583FCA9E51E7269A3E4E1CDFF6722714649` | 4668 |

Evidence hashes: `file-sha256.md` `CD6E6AA3F9D179899E4A61868D81B9D11820C97C0D650841B34798DEB42890E6`
(4948 bytes); `map-proto-correction.md`
`2EF78A1AA77CA0BE4E0A7A7A5215878D4FB857284F167ADCC56B0EC22D25EA7B`
(4288 bytes); `map-java-correction.md`
`7F5F20D2FE21C269F0A4FF5ECAD05B5DCAD54FD38997A8DB720124E334AED1D1`
(3528 bytes); `auth-token-redaction.md`
`3722C40A5EED3A3903B1EEBBA2AE4DB2EC512155AB11629DFA70F39A3755DCCB`
(5672 bytes).

## Review, runtime, and limitations

The source correction is ready, but runtime verification and an independent
post-correction recheck are pending. Accepted SQL7 is not rerun. Docker is
irrelevant to this affected command, and no SQL is run. No product runtime,
deploy, migration, data operation, or secret operation is authorized by this
handoff.

Before a heavy run, root must guard the exact hashes above, confirm no
`java`/`javaw`/`gradle` processes and no pending session, and obtain explicit
`A_RELEASE`/GO. The pre-run inventory had no such processes and no `gradle`
command; `gradlew.bat` is present. A release is still pending.

## Proposed affected heavy command after explicit GO/A_RELEASE

```powershell
.\\gradlew.bat :services:academic-service:academic-app:generateProto :services:auth-service:auth-api-contract:compileJava :services:mobile-bff:mobile-bff-api-contract:compileJava :services:auth-service:auth-app:test :services:mobile-bff:mobile-bff-app:test --tests ru.rutcampustrack.auth.contract.AuthTokenDtoRedactionTest --tests ru.rutcampustrack.mobilebff.contract.StudentMapModelsWireContractTest --no-daemon --no-parallel --max-workers=1 --console=plain --continue
```

The two global `--tests` filters intentionally provide one matching class to
each Test task. If Gradle CLI semantics cause a no-match failure on a task,
stop and report it; apply no workaround and do not change the command.

After runtime PASS, require a fresh Sol full exact25 plus both new focused tests
review, with an explicit Auth13 subset recheck. No B1 or full-role claim is
made. Current-status preappend SHA256 was
`A9FD59DCC3F69DA08065264097B485811F4A129B94801ACBC97478B7AF6CB069`
(3023 bytes); postappend SHA256 is `F1A48672B51E858BA94F491CA05D112588315B03FF40FA6A0A41D8A9FFF58D4D`
(`5634` bytes).