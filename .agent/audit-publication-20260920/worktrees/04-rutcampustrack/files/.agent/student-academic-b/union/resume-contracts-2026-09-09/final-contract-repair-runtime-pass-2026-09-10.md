# B0 final contract repair — runtime pass evidence — 2026-09-10

Captured: 2026-09-10 (Europe/Moscow). Base revision:
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`. State:
`B0_FINAL_CONTRACT_REPAIR_RUNTIME_PASS_REVIEW_ACTIVE`. Risk: S3. This is an
evidence-only continuation after root's conditional heavy runtime lease. Writer
and heavy lease are `RELEASED`; foreign dirty and untracked work remains
preserved.

## Scope and criteria

The seven already-frozen Auth/Map repair bytes were guarded before and after
root's focused checks. The exact25 product manifest remains unchanged at
`790081134DF7467362C844144345EF471B18B8F8D98A386A92DB448E2FE36097`.
Acceptance for this handoff is focused Auth/Map test success, postguard `7/7`,
scoped diffcheck `0`, and a clear process/heavy guard. This evidence does not
claim full B0, AUTH13, full-role acceptance, or independent Sol review PASS.

## Checks and runtime evidence

| Check | Command/evidence | Exit | Result |
| --- | --- | ---: | --- |
| Pre-runtime seven-file guard | Root guarded SHA/byte preguard for the four Auth DTOs, Auth focused test, Map model, and Map focused test | 0 | `7/7` postimage expectations present before runtime |
| Auth focused test | `./gradlew.bat :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.contract.AuthTokenDtoRedactionTest --no-daemon --no-parallel --max-workers=1 --console=plain --continue` | 0 | Session `15065`; `BUILD SUCCESSFUL` in 2m2s; 24 tasks/4 executed; auth API `compileJava` executed |
| Auth XML | `services/auth-service/auth-app/build/test-results/test/TEST-ru.rutcampustrack.auth.contract.AuthTokenDtoRedactionTest.xml` | 0 | SHA256 `4D48273027E46975C8524B404BA46B5A1C9F4DAF185A80DDA30692CBAF4D0341`, 963 bytes, mtime `2026-09-10T16:56:14.5825771Z`, tests 4, failures 0, errors 0, skipped 0 |
| Map focused test | `./gradlew.bat :services:mobile-bff:mobile-bff-app:test --tests ru.rutcampustrack.mobilebff.contract.StudentMapModelsWireContractTest --no-daemon --no-parallel --max-workers=1 --console=plain --continue` | 0 | Session `68500`; `BUILD SUCCESSFUL` in 52s; 39 tasks/4 executed; mobile-BFF API `compileJava` executed |
| Map XML | `services/mobile-bff/mobile-bff-app/build/test-results/test/TEST-ru.rutcampustrack.mobilebff.contract.StudentMapModelsWireContractTest.xml` | 0 | SHA256 `EDA40779190BB8F9E6EB8EB4D4E0FA4D957A7D677DE13077816893CB31B31930`, 1502 bytes, mtime `2026-09-10T16:57:15.9229939Z`, tests 7, failures 0, errors 0, skipped 0 |
| Focused total | Auth XML + Map XML | 0 | `11/11` tests passed; failures/errors/skipped `0/0/0` |
| Post-runtime seven-file guard | Root guarded SHA/byte postguard | 0 | `7/7`; repair bytes unchanged |
| Scoped diff check | Root `git diff --check` over the affected product scope | 0 | No product whitespace defect; evidence/status-only continuation |
| Process/heavy guard | Root process and lease guard | 0 | Clear; heavy ownership `RELEASED`; no pending session |

## Runtime and limitations

The two focused Gradle checks are the only runtime evidence in this handoff.
No protobuf generation, SQL, Docker, OpenAPI, TypeScript, service/product
runtime, deploy, migration, data, or secret operation occurred. The existing
exact25 manifest and all seven repair SHA/byte pairs remain unchanged. A fresh
independent Sol review with explicit Auth13 and B0 verdicts remains active and
is not pre-claimed by these test results.

## Diff and do not

This continuation adds only this evidence file and the matching current-status
entry. Do not edit product/test bytes, add manifest paths, rerun unrelated
checks, touch SQL/proto/generated/OpenAPI/TypeScript outputs, or claim full B0,
AUTH13, full-role, product-runtime, or review PASS. No Terra escalation gate is
recorded.
