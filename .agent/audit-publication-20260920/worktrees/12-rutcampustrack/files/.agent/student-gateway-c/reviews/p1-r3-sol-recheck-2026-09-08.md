# P1 attachment reconciliation — fresh Sol recheck R3

Date: 2026-09-08. Risk: S3. Result: **PASS**. No HIGH or MEDIUM
functional, authorization, data or contract findings were found in the exact-two
correction.

## Scope and behavior

- Revision: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.
- `StudentRequestService.java` SHA-256
  `B962498813AD3CC4E77EDBFE81E5E82E3013CFE1CF7B0490762880D23FCD3960`.
- `StudentRequestServiceAuthorizationTest.java` SHA-256
  `D1DDCDE648C3186914BA52846973497F08D82BBA6C198743A5254EC37E8B0B22`.
- The notification-only path validates null, blank and duplicate IDs, missing
  and extra inventory, and `requestId`/`ownerStudentId` binding. A mismatch throws
  `BadRequestException` before `NotificationResolution`. A complete match uses
  current stored metadata in repository order; empty embedded and stored
  inventories produce an empty list.
- The public student `toDetail` path remains separate and did not receive this
  notification-specific strict reconciliation.
- The focused tests materially cover embedded `[A,B]` against stored `[A]`, a
  complete match with current metadata/order, zero inventory, malformed and
  duplicate entries, wrong request/owner binding and extra stored entries. All
  eighteen methods are present in the current XML.

## Evidence checked

- Prior review SHA-256
  `67F540C9BBD485E618D3DE590AB8A96590B4A0A4E8CD626B0B515874AA387C7A`;
  the immutable owner source independently confirmed explicit attachment failure
  before enqueue.
- Fresh root session `54171`: exit 0, `BUILD SUCCESSFUL` in 1m53s, 37 tasks
  executed. Exact Gradle client process bounds were not captured. Daemon and XML
  times remain separate evidence facts.
- Fresh XML totals: `8 + 18 + 2 = 28`, zero failures, errors and skips.
  `AttendanceRequestBotGrpcServiceTest` SHA-256
  `62C4AA6A91DF3348C5ABF1897BC6461F588D7285827822E8517BF781965C9108`;
  `StudentRequestServiceAuthorizationTest` SHA-256
  `A0B0DDF3D5516EF0457EF9B25D94488495C93B097485FBE184F5D4793FA0A222`;
  `StudentRequestGrpcErrorsTest` SHA-256
  `FD2659C3639AA591911C78CE19F647B1D05C0C10F89DE77B88D7A3043A689435`.
- Daemon creation/last-write interval was approximately
  `21:42:50–21:44:39 +03`; source/test timestamps preceded compiled classes and
  current XML.
- Read-only guard: 113 status rows matched 106 inherited rows plus seven R3
  evidence rows; target trailing-whitespace scan was clean. Scoped
  `git diff --check` exited 0. Because exact-two files are imported and untracked,
  direct source scanning and frozen hashes are the authoritative content guard.

## Limits

The reviewer did not run Gradle or runtime. Rabbit, Telegram, Mongo and live
product runtime were outside this focused unit recheck and are not claimed.
Author `checks.md` and `runtime-evidence.md` still described the pre-GO state at
review time; the fresh run was independently verified from current build
artifacts and must be added as an evidence-only final addendum.

The reviewer task was requested as `gpt-5.6-sol/high`; its own result did not
state independently exposed runtime metadata.
