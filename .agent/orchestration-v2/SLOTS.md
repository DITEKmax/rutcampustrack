# SLOTS — PR3 implementation, 2026-10-24

Existing Goal GO; PR3/codex/web-vue-redesign only. Root+6child runtime ceiling.
All4 active developers explicitly gpt-6.1-sol/high/fork:none; runtime telemetry
not exposed. Root docs/Git integration, no product code; main has NO writer.

| Owner | Sole worktree | Paths / contracts | State |
|---|---|---|---|
| root | /workspace/rutcampustrack | common docs/map/PROGRESS/API-CHANGES/PR/integration | active |
| web_foundation | /workspace/rct-worktrees/web-foundation | web-vue, frontend package/lock/config, auth/router/shell/homework | implementation |
| theme_foundation | /workspace/rct-worktrees/theme-foundation | shared/foundation, mobile PCSS/SVG/theme, PWA/TMA App integration | implementation; neutral early commit |
| backend_device | /workspace/rct-worktrees/device-api | auth/device/Flyway/security/Gateway exact allows/OpenAPI/generated auth | implementation; heavy waiting |
| runtime_stand | /workspace/rct-worktrees/runtime-stand | harness/fixtures; /workspace/rct-pr3-runtime external scripts/evidence | exclusive HEAVY initially |

Baseline60346b26b (GO/readiness docs), source37a15451c. Product writers do not
edit root common docs. API contract direct coordination backend_device↔theme↔web.
Theme public shared/foundation/index.ts/base.pcss/tokens.pcss/mixins.pcss,
existing --rct-* aliases retained. Web uses vue-router child route records and
meta.requiredRoles; public auth/session types owner web foundation.

HEAVY runtime_stand only, max-workers2, measured16GiB/4CPU; backend waits lease.
One existing project rct-pr3-setup/network/private synthetic volumes; Gateway
127.0.0.1:18080, web14210, PWA15175/TMA15176. No parallel stand collision or fixed
container names. Core4JAR validated/reused; Schedule minimal build complete.
No global Docker prune/foreign data. Stand stays running for actual web checks.

All CI/Coverage/Security/OpenAPI PAUSED/manual only. No merge/deploy/production.
Readiness children completed; no children of leaf agents/hidden coordinator.
