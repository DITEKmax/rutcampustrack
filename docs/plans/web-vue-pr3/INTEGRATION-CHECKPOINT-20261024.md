# Integration checkpoint — 2026-10-24 Europe/Moscow

Existing Goal GO; PR3/codex/web-vue-redesign. Source through root176f6614f,
last pushed feddd8f03. Newer source is integrated locally, not yet pushed.
This is an intermediate checkpoint, not completion of the full redesign.

## Accepted actions and source packages

Implemented26/117(22.22%), locallyverified26/117(22.22%): AUTH6, SHARED10,
STUDENT homework browse/complete2, TEACHER8. No denominator change. Day/week/month,
previous/next/today, full text/link/new context, native keyboard and root20
browser checks now close the browse runtime gate. After independent baseline
review root found that hover/focus blocked live updates indefinitely; f0eb2bda
removes this blocker while preserving writes. Actual separate headman publication
POST201 produced real STOMP MESSAGE and appeared in student UI with existing
focused checkbox DOM retained. Earlier hover-deferral receipt is historical.

| Packet | Integrated author revisions | Current gate |
|---|---|---|
| AUTH6 and shared profile/settings | 2ee6a85e, ec7cae095, 2dae67d32; QR compat86a2be459/46519ed30 | Accepted after actual gates and af00 post-await/history queue corrections; independent recheck PASS |
| TEACHER8 | ecdc2cec1, 210c98202, 8401bd1b9 | Accepted; real read/export/AND restrictions, populated ranges/drill/dates/pagination PASS; final840 independent recheck PASS |
| HEADMAN38 | 124334c54, f0a762523, a6b16f3bd, 1f9/e94, f1089333b, 16b619970, 18f800412 | Earlier4P2 closed; later corrections/helper CAS/runtime decision/recovery gates remain |
| ADMIN34 | 72233959d, dc899, 8b93ef99a, e8f866dd8, ae9b77306 | Actual owned CRUD/lifecycle/map publication/download/exports PASS; filter rollout, lookup, final map/group deletion and specific security gate remain |

Remaining source packages do not inflate accepted counts until sufficient runtime and
independent review. Complete source inventory is available in the corresponding
Git commit diff; root does not author product files. Existing Angular is retained
until accepted features replace it; removal/build wiring gate remains open.

## Real stand and immutable artifacts

13 healthy containers in rct-pr3-setup, own network/volumes and synthetic data.
Auth/Academic/Gateway/MobileBFF/Schedule/Attendance/Notification/Renderer use real
PostgreSQL/Redis/RabbitMQ/MongoDB. Renderer includes LibreOffice/Poppler/fonts;
actual teacher journal and headman roster/journal PDFs, users HTML, groups XLSX,
attendance PDF jobs/tickets/download bytes and representative text were checked.
No external production services or fabricated exports.

Gateway18080; web14210, headman14220, admin14230, teacher14240; PWA15175/TMA15176.
Exact localhost/127 origins for HTTP and Notification SockJS were configured in
external stand configuration. Real STOMP SUBSCRIBE/MESSAGE confirmed; no wildcard
origins, CSRF relaxation or independent sockets per role. Today API200 is not
acceptance of the full new Today screen. Full mobile state matrix and actual
signed Telegram admission remain unverified.

Group filtering f3c03bd81+6289ab528 and async selector correction b32d47b0e passed
independent review. Root verified zero services/proto delta to integrated source,
checked bytes/SHA256 and copied immutable0444 artifacts before author rebuild:

| Service | Source | SHA256 |
|---|---|---|
| Auth | f3c03bd81 | 32bda499a73a5368d64126fbcba49e378d5e81ea349a4b20f7f671c1357c4035 |
| Academic | b32d47b0e | 4d75e1bd90bbcceed0114a64ae3f9de169ccf4c8ec4d008e332c871f99531d0a |

Actual coordinated rollout13healthy and real group4 filtered async HTML export PASS.
Receipt external `/workspace/rct-pr3-runtime/group-filter-rollout-receipt-20261024.json`.
Brief exec transport disconnect recovered with bounded root probe; rollout was
already fully recorded, no restart/repetition. Metadata external
`/workspace/rct-pr3-runtime/group-filter-rollout-approved-20261024.json`.
Authf3/Academicb32 are deployed in this isolated stand; CAS candidate is not. Gateway has
no source delta and does not need another build. Never mount mutable build/libs.
One HEAVY lease: backend assistant CAS checks/snapshot/JAR; light frontend work
may continue. 16GiB/4CPU, previousOOM0; no full suite or workflow dispatch.

## Owned synthetic fixture boundaries

| Actor/resource | Owner and restriction |
|---|---|
| Baseline admin1/teacher2/headman3 | Preserve credentials/profile/Telegram binding and global semester2; headman3 has no Telegram binding |
| student4/5/6, teacher7 | Runtime original fixtures;6 reserved headman marks/recovery,5 transfer history; preserve teacher assignment negative case |
| user8 | Admin-created student group1; frozen pending Q-ENV-003, no FULL/security test without owner answer |
| user9/group3 | Admin own headman/group lifecycle fixture; do not reuse |
| user10 | Runtime own OTP/recovery actor; verified identity through actual201/readback; private carrier/code never public |
| user11 | Web own profile/avatar/password/sessions actor; security tests confined to this account |
| user12/group4/subject4/lesson53 | Dedicated proof/drill actor; preserve populated teacher drill and lesson53; subject5 already deleted by actual proof flow |
| user13 | Admin own lifecycle, archive/restore leaves zero grants; do not reuse |
| user14 | Runtime fresh student group4, headman request-decision fixture; preserve self-review guard |
| user15 | Runtime fresh student group4, new student-web author own fixture |
| map9091/floor1 | Admin own versions retained for upcoming all-version final deletion proof; do not delete before backend fix |
| semesters3/4 | Admin own inactive fixtures; semester3 final completion verified, semester4 restore operation reconciles exact existing ID; global active2 preserved |

No broad OTP queue interception. Login/reset positive tests read only the exact
owned challenge Redis carrier after identity proof. Subject delete uses exact
own outbox challenge. Credentials, OTPs, generated passwords, leases and download
capabilities remain private and are excluded from public evidence/gallery.
Self-review of own request is forbidden; pending positive decision must use a
fresh different synthetic student rather than weaken that guard.

## Evidence, review and remaining work

[Immutable review manifest](../../validation/web-vue-pr3/review/manifest.json)
preserves initial findings and rechecks with original path and SHA256. New
headman, teacher and group-filter reports are included byte-for-byte.
[Public runtime projections](../../validation/web-vue-pr3/checkpoints/integration-20261024/manifest.json)
contain only source/checks/errors/method/path/status, with hashes of original
local receipts. They are labeled projections, not claimed byte-exact originals.
[Teacher14runtime samples](../../validation/web-vue-pr3/runtime-gallery/teacher-20261024/index.html)
are included with unchanged PNG bytes and exact revisions.
Actual screenshots retain their own frontend/backend revision; older busy/error
captures are not relabeled as the current accepted state. Existing public
[HTML gallery](../../validation/web-vue-pr3/runtime-gallery/index.html) remains
available; further role galleries await corrected stable capture selection. Width/theme combinations are visual samples, not separate tests/actions.

Q-PROD-004 member/grant policy blocks only final group cascade. Q-ENV-003 blocks
only the reviewable owned8 FULL/self-regeneration uncertainty test after automatic
approval rejection; script is materialized but unexecuted. Network www.figma.com
promise is not yet applied at last probe; original auth image is nevertheless
retrieved via authorized read-only image bytes, with exact hash/CRC provenance.
Shared helper conditional CAS and USERS_VIEW names-only archived-group lookup
are compatible endpoint changes in progress. Final map deletion must purge all
versions/DB BYTEA assets while preserving high-water version identity and receipts;
current ABSENT publication is explicitly insufficient.

Next: close bounded review findings, source-matched rollout and actual UI filter/
permission checks; expand remaining STUDENT scenarios with real backend; finish
accepted admin/headman actions and full mobile theme matrix; remove active Angular
only after replacement, preserve Vue wiring. Four CI workflows remain PAUSED/manual,
not PASS. No merge/deploy/production/main writes or global Docker cleanup.
