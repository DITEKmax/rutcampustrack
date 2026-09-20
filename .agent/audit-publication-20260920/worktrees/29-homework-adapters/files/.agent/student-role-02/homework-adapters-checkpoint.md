# Homework adapters — checkpoint

## Goal

S3 bounded scope: connect the accepted shared Homework feature to the PWA and
TMA shells with authenticated session ownership, Today↔Homework navigation,
generation guards, and PWA read-only offline schedule/homework behavior. The
binding frozen contract is the root artifact
`.agent/student-role-02/homework-adapters-packet.md` (SHA-256
`3437F0E81058C0524B1C6CE6C13662850150A8E1E7AC5BCFED27D3251591769E`).

## Context / evidence

- Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/homework-adapters`.
- Branch: `codex/student-role-02-homework-adapters`.
- Frozen baseline: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`.
- Accepted shared Homework source was imported before adapter edits. The
  imported manifest has SHA-256
  `6F0293C15D1FB8481813181532A12C719290E8E19E93EA232F9D2E878F3151ED`, 25
  files, and source/destination hash verification PASS.
- Current source shows the adapter gap remains: both shell `App.vue` files
  still own a single Today screen and do not yet load `StudentSession` or
  mount a per-generation Homework feature owner.
- No product runtime was started. Listener probe found no listeners on
  ports 5191, 5192, 5173, 5174, 8000, or 8080. Observed `node` processes are
  Codex CUA runtimes; they are not this worktree's product servers and were
  left untouched.

## Relevant scope

Owned paths are the PWA/TMA shell adapters and focused tests, the allowed
Today/offline/session helper boundaries in `frontends/mobile-core`, and this
worktree's `.agent/student-role-02` evidence. Backend, public API, generated
types, `StudentApi`, package manifests/lockfiles, configs, main checkout, and
other worktrees remain outside this scope.

## Required behavior

The remaining implementation must load and guard `StudentSession` before
feature queries, bind API retry/refresh and query/mutation/cache writes to the
captured generation, reset feature state on logout or identity changes, and
connect accepted Today↔Homework routes through one dock. PWA may read cached
schedule/homework while offline and must prevent every mutation; TMA remains
online. Snapshot writes must await commit, be scope-partitioned, and cannot be
resurrected by a late old-generation write. Host listeners and external-link
callbacks must be cleaned up on unmount.

## Constraints / do not

No children, commits, main merge, deploy, production operation, token/initData
persistence, attendance outbox, broad refactor, fake role/capability grants,
or mock-only API acceptance. Preserve foreign work byte-for-byte and keep the
accepted 25-file source separate from later adapter edits. Genuine Telegram
runtime remains a separately reported gate when unavailable.

## Acceptance criteria

Both shells must navigate and exercise real Homework GET/PUT desired-state
completion/reversal with recoverable unknown/failed ACK; old-owner delayed
GET/PUT/refresh/geolocation results must not affect a new owner, including a
same-homework-ID switch; explicit PWA logout must leave no reusable former
owner snapshot; warm PWA offline reload must be read-only and truthful about
cache age/scope; online recovery must reconcile authoritative state without
replaying commands; Today check-in, ACK recovery, fixture labeling, theme,
responsive/font behavior, and host cleanup must remain intact.

## Verification / checks

Environment: PowerShell, Node `v24.14.0`, npm `11.9.0`, no `node_modules` in
the worktree. Recorded checks:

- `git status --short --branch` — exit `0`; branch and bounded dirty scope
  reported below.
- `git diff --check` — exit `0`; no whitespace errors in tracked diff. Git
  emitted only line-ending/ignore warnings.
- `node --version` — exit `0`, `v24.14.0`.
- `npm --version` — exit `0`, `11.9.0`.
- Frontend typecheck, focused tests, lint, builds, browser/runtime, service
  worker offline, real BFF→gRPC→DB, and independent Sol review — **not run**;
  implementation is incomplete and dependencies are absent. Exit code is
  therefore `N/A`, not a PASS.

## Runtime evidence

No product runtime, browser fixture, real Telegram host, backend scenario, or
port was started by this checkpoint. There is no runtime PASS to carry forward.

## Current diff / hashes

Every path below is in this worktree's bounded scope. Hashes are SHA-256 of
the current working-tree bytes at checkpoint time.

```text
.agent/student-role-02/homework-adapters/imported-source-manifest.json 6F0293C15D1FB8481813181532A12C719290E8E19E93EA232F9D2E878F3151ED
.agent/student-role-02/homework-adapters/pre-edit-baseline.json C0CE5E36608487457891C4A1770EDBFF614E5CCAC898CCD033E831D098D82EF0
frontends/mobile-core/src/assets/homework-check.svg 4D07219CF9D1257201BBE71F2099C1E7BFF39BCDAD45A5EB040995FCF19E8574
frontends/mobile-core/src/assets/homework-direction.svg 6DC517AD12797EE54C49F6AF5FB7E3B3779142B0F311D987130FFD31C1A95659
frontends/mobile-core/src/assets/homework-expand-chevron.svg F80A91942498382B9C7371556918947FDCD97DD27A26E6648AA659CF1B95A103
frontends/mobile-core/src/assets/homework-expand.svg 6BEDC61327EADE0DCB64591C8E2887403028DC4CA451F3F084249DD9650653A4
frontends/mobile-core/src/assets/homework-external-link.svg E5AA676AC9B9E3F3F4B09FDFA9F59165E82DE77D5AE48165A0E98EAF32D1562C
frontends/mobile-core/src/assets/homework-handle-completed.svg 9AAEBE81842869F51C78B79809B050E21D4C900B7FD3F0B372EE9433B6D30596
frontends/mobile-core/src/assets/homework-handle.svg F608B1DF37C17DBF232A62F0F5187EFCC6CE91A97821164BAD6BD6CF8C653069
frontends/mobile-core/src/assets/homework-return-today-chevron.svg 875A907178F83A0CDC08544D89DA3CFBE6C3B05762A49A7F1FDA15C15BE9C0DA
frontends/mobile-core/src/assets/homework-return-today.svg B63BCCB9B3A30CD426E1CB5C9D55A58A040F01B75EE2C845B95F56B0361062F6
frontends/mobile-core/src/domain/homework.test.ts 80F72B1B17166C72144AC605ACAB4ECD8FF0ECEF610B7CEE9640E9A8C3E282DC
frontends/mobile-core/src/domain/homework.ts F3E0D2C338403D7AAC91B0993F455CB8C2175A212C385F23CD8F3905CEE57F89
frontends/mobile-core/src/features/homework/homework-focus.test.ts 368E0C6ADA746982E73C334A29C61DCF96235E0807B0EA6A01934D8CEA7662A6
frontends/mobile-core/src/features/homework/homework-focus.ts BD4D6DA67685797538B647BAC2C62900EDD9068D761095ECA3B810D5C0A77D68
frontends/mobile-core/src/features/homework/homework-screen.pcss 870171CDD058D9223E7B20A3AB6D30815723D851666FB53D23FABB1DF61EC9ED
frontends/mobile-core/src/features/homework/HomeworkScreen.vue E11B919E80EE7B2EB3420BAE52175A959A9CD5B48C8C854E6B8429C393115DE5
frontends/mobile-core/src/features/homework/use-homework.test.ts 90771B83548B60A1C9DCD0114ED2AFA69CF65DA6447C741FB170B5B9051522A9
frontends/mobile-core/src/features/homework/use-homework.ts 9F98E959D690659611C7F62BD87401DB2DC7A357C9E66E9AACF1ED14ED860329
frontends/mobile-core/src/features/today/today-screen.pcss 60AE83BFD7552FBA27C51FD102B99B61FE954C9CFB50E07C8691D495D6FB5DF1
frontends/mobile-core/src/features/today/TodayScreen.vue 20CDDAFD0CB4936176F1F12B095D3FB760128C8F718E6EDEBC56482988AC4776
frontends/mobile-core/src/features/today/use-today.ts EBB9E2CAC502CF647053C9E94448D5A9071018E6ED8953D57720D1CCE5702E01
frontends/mobile-core/src/index.ts 62DD2AB4E00586E0366D03FA59B07423AEC7EDD79E851ED94E8EC7812A634260
frontends/mobile-core/src/offline/semester-snapshot.ts 8B400DE624913DD7AA083235E73DC3C79E5BE321652AB6CAAE7F666078B44601
frontends/mobile-core/src/shared/components/mobile-bottom-nav.pcss 6783200B72A5D1FE3EB91327A8D1A06AC0357B7A63D63199253F542890189A7D
frontends/mobile-core/src/shared/components/MobileBottomNav.vue A278BE55B251142B9799E24793AC319F76D1C5C308125C912CBBFA8AF7964BDC
frontends/mobile-core/src/shared/mobile-navigation-items.ts 337BE73E5701042E0BC2D63A4CB3E48BC0E60477C6AD70143D29B43CB0A1829E
frontends/mobile-core/src/shared/session-owner.ts 9D290A014F3347C534338E8D27202FB7E68D0F11D734DD5E9935DA4E6052B7F1
frontends/mobile-core/src/shared/theme.test.ts 13749ED961B179D215588B427F21940E1B6E5CC6B6A25A554B08C3A5EB09BF88
frontends/mobile-core/src/shared/theme.ts 18A7849A2D1299ADDA0659889A1F97A66EB3A5BA54EE754BDBB6A9B376AB39B3
frontends/mobile-core/src/styles/tokens.pcss 1FF851ABB57898465952F7B30BFAF2627A71577BF63C4474201032CFAF231974
frontends/mobile-core/src/test-adapter/fixture-transport.ts A2C0FF2C227604D19968E24AC5513D171F2617ADD036E49A4B9287D4AEB31E1D
frontends/pwa-vue/src/auth.ts 9650182FA4BE31D7BA4434605BAFA3966DB7730A47AD067843CE69AD1BE0BD11
frontends/pwa-vue/src/pwa-host.ts B60835798DEB2CFDA25D7AF21FF32CAB16462ADDF90A1F879A636F5F41341C3D
frontends/tma-vue/src/tma-session.ts FE16171649A44E14FE179395EE4BD75A11F185D7A0822029586E3C4CED43C54D
```

Tracked diff is 10 modified files; imported/added source is intentionally
uncommitted. No commit was made.

## Limitations / remaining work

The shell wiring, authenticated feature components, logout/switch cleanup,
PWA snapshot integration, TMA host lifecycle integration, focused adapter
tests, typecheck/lint/build/runtime evidence, and fresh independent review
remain. Resume from this worktree and branch, re-read the frozen root packet,
verify the listed hashes before editing, then continue only these paths. Do
not reset, clean, or overwrite the worktree; preserve every listed file and
recheck ownership before any write.

Checkpoint captured after `git diff --check` exit `0`; no running product
session or product port remains to clean up.
