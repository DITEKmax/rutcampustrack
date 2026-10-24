# SLOTS — PR3 implementation, 2026-10-24 Europe/Moscow

Root+6children runtime ceiling. All assigned children gpt-6.1-sol/high/fork:none;
telemetry unavailable; no silent fallback. No leaf children. Main has no writer.

| Owner | Worktree | Sole ownership | Current work |
|---|---|---|---|
| root | /workspace/rutcampustrack | docs/map/progress/API/PR/Git integration; no product code | checkpoint/gates/push |
| web_foundation | /workspace/rct-worktrees/web-foundation | commonweb/auth/shared/student-homework/config/lock/app registry; new student/** transferred to student author | notifications bell/history/10s overlay; AUTH/shared review |
| headman_web | /workspace/rct-worktrees/headman-web | features/headman/**, routes | 38 actions; runtime/review corrections |
| admin_web | /workspace/rct-worktrees/admin-web | features/admin/**, routes | 34 actions; group filter/history/lookup |
| teacher_web | /workspace/rct-worktrees/teacher-web | features/student/**, studentRoutes (new /workspace/rct-worktrees/student-web); teacher scope frozen | remaining14 student actions |
| backend_device | /workspace/rct-worktrees/device-api | Auth/Academic/proto/OpenAPI/generated contracts | assistant CAS, GroupOptions lookup, map cascade assessment |
| runtime_stand | /workspace/rct-worktrees/runtime-stand | tests/e2e/infra/pr3-*; external stand/harness/fixtures | inactive; next immutable rollout when slot free |
| device_api_review | readonly frozen Git/evidence | independent auth/shared packet review | active |
| foundation_review | readonly frozen Git/evidence | independent teacher narrow recheck | report210 done; next corrected delta |
| theme_foundation | /workspace/rct-worktrees/theme-foundation | shared foundation/mobile theme | inactive; integrated; tiny deltas review pending |

Temporary explicit compatibility ownership: headman author owns manual
HeadmanGroupApi.updatePermissions optional expectedPermissions + related tests,
and existing shared HeadmanGroupScreen confirmed.before CAS adapter only.
Admin author owns manual names-only GroupOptions includeArchived/archived adapter.
Web author owns shared QR profile/session codecs + PWA/TMA QR labels/predicate,
and admin_reset Headman initial-password origin compatibility. Generated types
stay backend owner. Common tokens/config/lock/registry remain web/theme owners.

createWebTransport identity guards before/after fetch AND after decode required.
useWebLiveRefresh uses existing shared STOMP bus; no per-feature sockets.
Common ConfirmDialog/styles owned web author. Writers never edit shared registry.

One HEAVY lease currently backend_device CAS scoped checks/JAR. Light browser/
types may parallelize within16GiB/4CPU; OOM0 at previous observation. Runtime
owner coordinates rollouts, never mount mutable author build output. Separate
ports and synthetic owned fixture scopes; do not reuse baseline/shared users.
No global Docker cleanup, production, main writes or workflow dispatch.
