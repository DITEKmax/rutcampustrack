# Shared-shell repair evidence

## Recorded defect gate

The fresh Sol review in `.agent/student-role-02/shared-shell-review-1.md`
provided the request, severity, reproduction, and impact for four defects. No
Terra escalation was requested: the correction is bounded to the existing
Luna-capable shared shell.

1. **HIGH — external stack mutations were invisible to `MobileShell`.** The
   shell invalidated `currentRoute` only from its own `navigate`/`goBack`
   handlers while `MobileNavigationStack` exposed a plain mutable array. The
   review probe observed shell `today` while the actual stack was
   `today/checkin`. `subscribe` now invalidates the shell for effective stack
   mutations without adding another route source of truth.
2. **HIGH — nested history had no owning root.** An initial
   `homework/detail` returned `null` on Back, and cross-root `today` →
   `homework/detail` returned to `today`. Initial, cross-root, and replaced
   nested routes now seed `[owningRoot, nested]`; nested Back returns the root,
   then stops there.
3. **MEDIUM — host replacement leaked lifecycle state.** The former mount-only
   subscriptions left host A active and never subscribed host B. `MobileShell`
   now stops the previous keyboard/Back subscriptions, resets keyboard state,
   updates Back visibility for the new host, and cleans the current host on
   unmount.
4. **MEDIUM — Attendance accessible name regressed.** The critical Figma
   original `.agent/student-role-02/design-context/4581-3062.txt:278` requires
   visible `Учёт` with accessible `Посещаемость`. `accessibleLabel` is now a
   typed item field and disabled reasons are appended to that name, producing
   `Посещаемость. Раздел пока недоступен` in Today.

The design source also defines `control/min-touch = 44` in
`docs/design/tokens-v2.json:1026`. The repair adds the existing-project alias
`--rct-control-min-touch: 2.75rem` in `src/styles/tokens.pcss` and uses it for
both dimensions of the shell Back target; no source token JSON or new product
token was created.

## New runtime evidence

`node --experimental-strip-types
.agent/student-role-02/shared-shell/mobile-shell-lifecycle-probe.mjs` exited 0.
It mounted the compiled Vue `MobileShell` and `MobileBottomNav` with a custom
renderer, exercised external `push`, external `replace`, host A→B keyboard and
Back callbacks, and unmounted the app. Its output included:

```json
{
  "initial":{"surface":"root","dockVisible":true,"keyboardVisible":false},
  "afterExternalPush":{"surface":"task","dockVisible":false,"keyboardVisible":false},
  "afterExternalReplace":{"surface":"root","dockVisible":true},
  "accessibleName":"Посещаемость. Раздел пока недоступен",
  "hostA":{"keyboardSubscriptions":1,"keyboardUnsubscriptions":1,"backSubscriptions":1,"backUnsubscriptions":1},
  "hostB":{"keyboardSubscriptions":1,"keyboardUnsubscriptions":1,"backSubscriptions":1,"backUnsubscriptions":1}
}
```

The probe also verified that a post-replacement host-A keyboard event and Back
event no longer affect the shell, host B Back returns `task` to `root`, host A
receives `setBackVisible(false)`, and host B receives the same cleanup on
unmount.

The existing Boolean-host probe
`.agent/student-role-02/shared-shell/mobile-shell-runtime-probe.mjs` exited 0
with `before=true`, `after=false`, `propType=Boolean`, and
`propDefault=undefined`; the earlier keyboard correction remains intact.

The existing fixture server at `http://localhost:5175/` was not restarted or
competed with. Full product/API runtime and genuine Telegram host behavior are
outside this repair; compiled component behavior is the applicable runtime
evidence.

## Scope and limitations

The worktree already contained the shared-shell foundation, Today extraction,
Today PCSS/index/package changes, and `.agent/student-role-02/shared-shell/**`
artifacts before this repair. They remain untouched as foreign/pre-existing
work. This repair adds only the listed shared-shell behavior, bounded tests,
the actual component probe, and this evidence directory. Full lint retains the
known unrelated `mobile-core/src/test-adapter/fixture-transport.test.ts:94`
`@typescript-eslint/no-this-alias` failure; no change was made to that file.
