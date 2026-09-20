# B0 final contract repair — source-ready evidence — 2026-09-10

Captured: 2026-09-10 (Europe/Moscow). Base revision:
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. State:
`B0_FINAL_CONTRACT_REPAIR_SOURCE_READY_RUNTIME_GO_GRANTED`. Risk: S3. Writer:
fresh bounded sole writer for this exact evidence/status scope; lease:
`RELEASED`. Foreign dirty and untracked work in the shared checkout remains
preserved.

## Goal

Freeze the seven already-applied Auth/Map repair bytes into the existing exact25
manifest and a reviewable source-ready handoff. This continuation changes only
the manifest and evidence/status markdown; the seven product/test files remain
immutable. Root owns the conditional heavy verification after this release.

## Context and evidence

The last independent Sol review reported exactly two MEDIUM contract-source
findings, recorded by the STOP checkpoint at
`.agent/student-academic-b/stop-2026-09-10.md`: Auth DTO canonical-value
validation was incomplete for UUID, `activeRole`, `roles`, and `groupId`; the
Map DTO wire boundary allowed swapped slots, wrong MIME, and arbitrary SHA-256
text. Root then read back the seven bounded source/test corrections and reported
scoped `git diff --check` exit `0`. The STOP arrived before this manifest/status
refresh and before any new compile, focused test, or independent re-review.

The Auth correction now keeps session identifiers canonical lowercase UUID
strings, positive decimal IDs/versions, uppercase role/status wire values,
optional positive-decimal `groupId`, and immutable unique full role grants. The
focused test covers malformed UUIDs, lowercase/duplicate role values, invalid
group IDs, and the existing token redaction/JSON round trips.

The Map correction binds the `png` and `svg` plan fields to their matching
formats, derives the exact MIME (`image/png` or `image/svg+xml`), accepts only a
lowercase 64-hex SHA-256 for ready assets, and keeps lower-case enum/id and
non-ready null/zero invariants. The focused test covers ready/non-ready JSON,
enum values, MIME, SHA, and format-specific slots.

## Relevant scope

The frozen product manifest remains exactly 25 paths in the same order. Five of
those source rows changed hash/byte values: `CurrentSessionResponse.java`,
`RoleGrantResponse.java`, `AuthSessionSummary.java`,
`AuthAdmissionResponse.java`, and `StudentMapModels.java`. The two focused test
files are semantic evidence for the repairs and remain outside the frozen
product exact25 manifest; their post-hashes are recorded below without adding
manifest paths.

Evidence mutations are limited to this file, the existing
`union/resume-contracts-2026-09-09/file-sha256.md`, and
`.agent/student-academic-b/current-status.md`.

## Required behavior

- Auth DTOs reject non-canonical UUIDs and invalid positive-decimal or uppercase
  wire values at construction while preserving record accessors and JSON token
  properties.
- `CurrentSessionResponse.roles` is non-null, copied, and unique by role;
  nullable `activeRole` is an uppercase wire value; nullable `groupId` remains a
  positive decimal string where present.
- Map `Plan` fields enforce their declared format slot; ready slots require the
  exact format MIME and lowercase 64-hex SHA-256; non-ready slots retain null
  `id`/`sha256` and zero bytes.
- The exact25 manifest keeps its existing path set and order. Only the five
  matching source rows are refreshed; focused test hashes stay evidence-only.

## Constraints

No product source or test edits, Gradle/test/protoc/generator/Docker/SQL/runtime
execution, OpenAPI or TypeScript change, generated output, commit, stage,
reset, clean, or external write is allowed in this leaf. Existing evidence and
foreign WIP are preserved. No product, contract, or scope redesign is made.

## Existing patterns

The manifest keeps its existing one-line SHA256/path/byte format and exact25
ordering. Evidence follows the existing dated B0 markdown pattern with explicit
scope, criteria, checks, runtime status, diff, limitations, and proposed root
commands. The correction descriptions defer to the frozen union contract and
the two focused executable specifications.

## Acceptance criteria

- The manifest data-row count is `25`; every listed path exists and hashes to its
  listed SHA/byte pair; path order is unchanged.
- The five refreshed manifest rows equal the postimages in the table below.
- All seven repaired source/test postimages equal the table below.
- Root's post-repair source readback and scoped `git diff --check` remain exit
  `0`; this evidence refresh introduces no product diff.
- This handoff claims source readiness only. AUTH13, B0, full-role acceptance,
  runtime PASS, and independent post-correction Sol PASS remain unclaimed.

## Verification

| Check | Command/evidence | Exit | Result |
| --- | --- | ---: | --- |
| Base revision | `git rev-parse HEAD` | 0 | `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` in Windows PowerShell |
| Seven postimage guard | Guarded PowerShell `Get-FileHash -Algorithm SHA256` plus `Get-Item.Length` over the four Auth DTOs, Auth focused test, Map model, and Map focused test | 0 | All seven current SHA/byte pairs match the table below |
| Exact25 manifest guard | Guarded row count/path readback over `file-sha256.md` | 0 | `25/25` rows; no test paths; five source rows refreshed only |
| Full source hash guard | Guarded SHA/byte comparison for all 25 current product paths against `file-sha256.md` | 0 | `25/25` current files match |
| Root source readback and scoped diff check | STOP checkpoint root readback; `git diff --check` over the affected product scope | 0 | All seven corrections present; no whitespace errors reported |
| Local post-freeze diff check | `git diff --check -- <exact25 product paths>` | 0 | Git emitted only the existing LF-to-CRLF normalization warning |
| Manifest postimage | `Get-FileHash -Algorithm SHA256 file-sha256.md` | 0 | `790081134DF7467362C844144345EF471B18B8F8D98A386A92DB448E2FE36097` (4944 bytes) |

### Seven repair pre/post hashes

| Path | Pre-repair SHA256 (bytes) | Current post-repair SHA256 (bytes) |
| --- | --- | --- |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/CurrentSessionResponse.java` | `0625B281C5C1FC0338B85FFB7E498DF10D04A50DD546B736F37D89D61D898F71` (2180) | `DA0899902573AB79D06C6713E44222322B96290F441DD7C4B913D809E965E9A1` (2930) |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/RoleGrantResponse.java` | `6602957D56501FD0AED542858C2B3ACBD77A96F735CBD29034D4CD1637EB1F0C` (1410) | `216D5BCE545AB120EECA05E14FB22E58085B407EB4CA9DBBDAB83DF076DABA24` (1507) |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthSessionSummary.java` | `718F6A069210F8E1242A0E9BC12CF752AC35506727EBFC749C339E8A65E4271A` (1834) | `38813017B0E10E4F6C9C1398EC8EB5D6546C0352151DA6E78362587B70BBD572` (2023) |
| `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthAdmissionResponse.java` | `FA6559334DA8072F248033528040B4210A7792F58EAE8EE159E26F7801F802A7` (2780) | `1841B46A85ADB96212A9D167A9253A9CB9002021C96C6CEB0954DAD8C3E45912` (2969) |
| `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/contract/AuthTokenDtoRedactionTest.java` | `B9F1CDFC799116D9D24B5BBFD62DD583FCA9E51E7269A3E4E1CDFF6722714649` (4668) | `F2F304840D1B2CEFA55E4FC90AD56F0875731624A602EEDE372842264601EEEE` (7332) |
| `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentMapModels.java` | `62831E2419009CD60361E60F2A139492D07DD54B4A91DBD095BC69B9AA63C6E2` (5153) | `CB6AD4EB2FFE3E48CB74FC8183EEA50AD290F467D9226CB88F09B9D1881991B8` (5879) |
| `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contract/StudentMapModelsWireContractTest.java` | `06C041BBE4E72D534E39FC45742D14DC290180A8318BE5FCEF12A208A0BFC459` (4678) | `FEA79FABF1A245B82A45525D00A10297D55D1C06F3AE8C20385108152DFF2115` (6472) |

## Runtime evidence

No Gradle, focused test, protoc, generator, Docker, SQL, service, or product
runtime was run after the seven repairs in this resumed manifest/evidence
continuation. Runtime evidence for this leaf is therefore `N/A`. Historical
runtime records remain preserved in the earlier `.agent` evidence files and are
not used here to claim post-STOP acceptance. Root may run the exact conditional
commands below after this `RELEASED` handoff.

## Diff and limitations

- The scoped diff from this continuation is manifest metadata plus this new
  evidence and the required current-status entry. Product/test bytes are
  unchanged and foreign dirty/untracked work is preserved.
- The exact25 manifest intentionally contains product contract paths only; the
  two focused test hashes above provide semantic evidence without changing its
  count or order.
- Static hash/readback and root's recorded diffcheck do not prove compilation,
  focused test execution, generated output, runtime wiring, AUTH13, B0, or
  independent Sol re-review. Those remain root gates.
- No WARN/ERROR was tied to a requested product change, so no additional code
  mutation was justified. No Terra escalation gate is recorded: no new defect
  or complexity boundary was found in this evidence-only scope.

## Do not

Do not add manifest paths, reorder the exact25 set, edit the seven repaired
source/test files, rerun heavy commands in this leaf, regenerate outputs, touch
SQL/OpenAPI/TypeScript/configuration, claim runtime or review PASS, or redesign
the Auth/Map contract.

## Proposed exact root heavy commands

Run only under root's conditional GO, with the prescribed serialized flags:

```powershell
.\gradlew.bat :services:auth-service:auth-api-contract:compileJava :services:mobile-bff:mobile-bff-api-contract:compileJava --no-daemon --no-parallel --max-workers=1 --console=plain --continue
```

```powershell
.\gradlew.bat :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.contract.AuthTokenDtoRedactionTest --no-daemon --no-parallel --max-workers=1 --console=plain --continue
```

```powershell
.\gradlew.bat :services:mobile-bff:mobile-bff-app:test --tests ru.rutcampustrack.mobilebff.contract.StudentMapModelsWireContractTest --no-daemon --no-parallel --max-workers=1 --console=plain --continue
```

If a selector produces a no-match failure, root must stop and record that
result; no workaround or command redesign is authorized by this handoff.
