# SLOTS — PR3 implementation, 2026-10-24

Existing Goal GO, PR3/codex/web-vue-redesign only. Root+6children runtime ceiling;
all assigned agents explicit gpt-6.1-sol/high/fork:none, telemetry unavailable.
Root sole common-docs/Git integrator, no product code; main has NO writer.

| Owner | Worktree | Sole product ownership | State |
|---|---|---|---|
| root | /workspace/rutcampustrack | docs/map/PROGRESS/APIchanges/PR/integration | active |
| web_foundation | /workspace/rct-worktrees/web-foundation | web common/auth/shell/shared/student; frontend package/lock/config; app/features.ts registry | continuing AUTH/SHARED/STUDENT |
| headman_web | /workspace/rct-worktrees/headman-web | web-vue/src/features/headman/**; headmanRoutes | HEADMAN38; first source package ready |
| admin_web | /workspace/rct-worktrees/admin-web | web-vue/src/features/admin/**; adminRoutes | ADMIN34 |
| teacher_web | /workspace/rct-worktrees/teacher-web | web-vue/src/features/teacher/**; teacherRoutes | TEACHER8 |
| backend_device | /workspace/rct-worktrees/device-api | Auth/Academic/proto/OpenAPI/generated API; durable compatible endpoint gaps | regeneration52eb8520 review-ready; groupId assessment |
| theme_foundation | /workspace/rct-worktrees/theme-foundation | shared/foundation; mobile theme/PCSS/SVG; PWA/TMA theme integration | integrated; available corrections |
| runtime_stand | /workspace/rct-worktrees/runtime-stand | tests/e2e/infra/pr3-*; external /workspace/rct-pr3-runtime | CORS ready; HEAVY renderer |
| device_api_review | independent read-only | frozen source/deltas and sufficient independent checks | device and webcab+a2ef PASS; next auth regen |
| foundation_review | independent read-only | neutral/mobile theme review | 6de+00fc PASS; later small deltas pending |

Inactive/complete owners do not reserve runtime sampling slots. Do not add children
from leaf agents. No silent model fallback. First accepted root072edf183;
new role worktrees based on it, sole feature paths; root integrates commits,
web owner registers routes after receiving dependency commit. Common live refresh
API belongs web owner; no independent duplicated STOMP sessions per feature.

Temporary explicit web_foundation compatibility ownership: mobile-core headman-group-client.ts
origin union/decoder accepts admin_reset; no generated-file ownership transfer.

One HEAVY at a time; now runtime_stand renderer, backend no Gradle.16GiB/4CPU,
file cache reclaimable, OOM0. Light isolated types/build/browser may parallelize.
Same rct-pr3-setup/owned private network/named synthetic volumes. Gateway18080;
14210main,14220headman,14230admin,14240teacher exactlocalhost/127CORS verified;
15175PWA/15176TMA. One runtime owner applies JARrollouts from immutable copies
only after source accepted. No global Docker prune or foreign sessions/data.
All workflows remain PAUSED/manual; no merge/deploy/production/main writes.
