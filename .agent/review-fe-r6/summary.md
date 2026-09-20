# Repair summary

## Scope and diff

`f699a71d` is the standalone F7 unblock commit: default native fetch is invoked through `globalThis`, while injected fetch keeps its prior receiver. The remaining R6 diff adds `CheckinCommandRecovery` in mobile-core, wires PWA and TMA through it, retains exact `{ lessonId, command, key }` only while retry outcome is ambiguous, maps PWA geolocation error codes through the existing mapper, and protects both shell handlers from an offline direct call.

Changed implementation files: `mobile-core/src/api/student-client.ts`, `mobile-core/src/domain/checkin.ts`, `pwa-vue/src/App.vue`, and `tma-vue/src/App.vue`; targeted tests are adjacent domain/fixture tests. No generated contracts, lockfiles, configuration, persistent storage, roles, UI styles, or transport contract changed.

## Limitations and handoff

The recovery state deliberately ends with the component lifetime. It is not an offline queue, does not survive reload, and does not bridge a different account/session. The fixture transport cannot simulate an ACK dropped after a committed server mutation, so that precise flow is proved by the focused shared-domain simulation. Sandbox-only esbuild permission failures were reproduced and then the same commands passed with elevated filesystem access; no code was changed for that environment issue.

Root should integrate the two commits in order (`f699a71d`, then the forthcoming R6 commit) and obtain the required fresh Sol recheck against the stable integrated diff.
