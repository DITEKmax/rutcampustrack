# FE lane packet — JS-STUDENT-01-r1

Base: `87784165874e2da6fc261abc1c01584e24624289` plus the exact allowlisted overlay
from `baseline.json`. Start only after contract export/hash is frozen.

Ownership: future FE developer only in `frontends/mobile-core/`,
`frontends/pwa-vue/`, `frontends/tma-vue/`. Contract owner alone changes generated
types, lockfiles, workspace/build config, fixtures and global task state.

Required behavior: render captured Today states from the packet, consume only r1
generated types, wait for server ACK, show countdown from `serverNow/retryAt`, keep
one query owner, and never queue check-in offline. PWA auth reuses memory access JWT
and HttpOnly refresh cookie; TMA auth posts signed init data and keeps tokens in
memory. The TMA host adapter owns narrow local types for Telegram's injected
`window.Telegram.WebApp` API; do not add the deprecated `@telegram-apps/sdk` chain.
Use local packet SVG assets; do not copy React Phosphor components. Use
Onest only after traceable font bytes/fallback is frozen.

PWA uses one IndexedDB/query owner for the server-scoped semester schedule and stable
read snapshot, partitioned by user. Offline Today composes schedule rows from it,
shows offline + `updatedAt`, never claims cached eligibility is fresh, and disables
check-in. Data stays visible after expiry until explicit logout/account switch,
which clears the partition; tokens are never persisted. TMA is online-only.

Acceptance/checks: strict TS/Vue build, lint, unit behavior, contract fixture
validation, keyboard/states/themes/root-font/responsive at packet width, PWA real SW
separate from mocks, TMA mock-host plus separately recorded real-host gap/evidence.
Do not change product contract, Figma, generated artifacts or shared config.
