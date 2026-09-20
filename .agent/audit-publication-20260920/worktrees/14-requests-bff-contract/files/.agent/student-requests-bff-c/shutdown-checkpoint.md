# STOPPED checkpoint

Дата снимка: 2026-09-08. Состояние сохранено по прямой команде владельца;
после этой записи разработка не продолжается без нового прямого задания.

## Exact state

- CWD: `C:\Users\maksd\.codex\worktrees\e31c\rutcampustrack\.agent\worktrees\requests-bff-contract`
- HEAD: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached).
- `git status --porcelain=v1 --untracked-files=all`: 83 dirty files.
- Dirty path set is exactly the 82 product paths in immutable
  `.agent/student-role-02/diff.json` (manifest SHA-256
  `4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4`) plus
  `.agent/student-requests-bff-c/packet.md`. The immutable manifest enumerates
  every inherited path and source SHA; current destination SHA is equal to that
  source SHA for the 77 paths outside the two accepted repairs and three WIP
  paths below.
- Accepted repair overrides (repair manifest SHA-256
  `7B64350F73272787BD383E89611B75BE10959B02B4A0342695E45C46769462F3`):
  - `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java`
    → `1FE05ED7FA57A2DC3CFA1EA77635B414D94B986D7DFFEAFC78EA544D3485FF32`.
  - `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/RabbitDecisionRetryIT.java`
    → `95BFF3CE808623BFCD370499410A33BFC3C38465AF9097FE3016F80D49E51EDA`.
- Current WIP destination SHA overrides (three owned production files):
  - `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java`
    → `6D4F1AAA69A2112358459041BD80C1917755476707E118E9EDE6F5DED0AF91C1`.
  - `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentRequestApiModels.java`
    → `651B83E0E7B629F2750EE51231915B9A55D072E3F04FB31EDD6C0D4864198286`.
  - `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java`
    → `4940C439B74F8A92B606D5B37DFC9660A6A68AEDA12F34D67B64E8F809768C17`.
- Own packet SHA-256 at the snapshot: `FB1E647932DA7FF934D7811E800F7919DE0D5B1CAAD633E7EA0E66666EC80FF2`.
- The inherited `services/notification-bot/uv.lock` remains present at source
  SHA `C59E3D361F8F175C3D661018029AEB9DF00761B74D70F79D6D1E3971FCC59082`;
  it was not edited or deleted.

## Ready versus incomplete

- Status: `STOPPED / WIP`, not READY.
- Complete before pause: applicable instructions read; compact nine-section
  packet written; immutable 82-path import completed; two accepted IT repairs
  applied; source/destination guarded import verification previously returned
  `IMPORT_VERIFY_PASS productPaths=82 repairs=2`.
- WIP production correction present: additive `INTERNAL_ERROR`, three explicit
  `Detail` `@JsonInclude(ALWAYS)` annotations, and bounded BFF translator/helper
  changes for generic internal versus dependency statuses.
- Incomplete: the two exact owned tests were not created; no focused Gradle
  checks, compile, runtime, review, evidence/checks/runtime artifacts, or final
  summary were produced. No OpenAPI/TS/proto/generated/config/global-handler
  follow-up was attempted.

## Last actual checks and exit codes

- `Get-Content -Raw docs/agent-workflow.md`, `services/AGENTS.md`,
  `tests/AGENTS.md`, and the two active skill files: read successfully, exit 0.
- Immutable manifest `Get-FileHash -Algorithm SHA256`: source diff manifest
  exit 0, SHA `4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4`;
  repair manifest exit 0, SHA
  `7B64350F73272787BD383E89611B75BE10959B02B4A0342695E45C46769462F3`.
- Guarded import verification (already run before the pause) exited 0 and
  returned `IMPORT_VERIFY_PASS productPaths=82 repairs=2`; `uvLockExists=True`.
- No Gradle, test, Testcontainers, service startup, external runtime, or heavy
  check was run. No runtime evidence exists; runtime is pending and out of scope
  until root grants GO/lease.

## Next specific step after a new owner command

Create only the two exact tests from the packet, inspect the five-file scoped
delta, then request/await root GO for:

` .\gradlew.bat :services:mobile-bff:mobile-bff-app:test --tests "ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClientErrorTest" --tests "ru.rutcampustrack.mobilebff.contract.StudentRequestDetailJsonTest" --console=plain `

Expected duration estimate for the shared lease: 20–60 seconds warm, 2–4 minutes
cold. Record actual exit/evidence in `.agent/student-requests-bff-c/checks.json`
only after that command is authorized.

## Owned processes

No dev server, watcher, test process, container, or external service was started
by this leaf. There was therefore nothing owned to stop; no process/data/volume
stop or deletion was performed.
