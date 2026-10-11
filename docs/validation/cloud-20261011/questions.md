# Questions

No product questions identified before source preflight.

## Q001 — PWA offline при недоступной проверке сессии

Source: docs/implementation/release-surface-matrix.md:14–16; parallel-development.md; current pwa-vue App.vue snapshot restore. No later expiry policy found in dated decisions.
State: ANSWERED — owner chose A; D002, 2026-10-11T19:32:24.395754+00:00; dependent scope offline reload/expiry/user boundary only, online auth and TMA independent.
Choices: A read-only cached schedule/homework of last verified student scope until online validation (recommended, current behavior); B hide after known server session expiry; C require online reauthentication on every restart. Silence does not select a rule.
Additional boundary: no offline commands, attachments or role switching; revocation only becomes observable after connectivity returns.

## Q002 — Повторное использование Telegram initData

Source: current docs/testing/testing.md:80 + TmaIT/current TmaService allow replay in configured 86400-second window; archived report-before-v0.0.0/01-auth-service.md says single-use. telegram_official research correctly leaves TTL/reuse to product.
State: ANSWERED — owner chose A; D003, 2026-10-11T19:36:07.448654+00:00. Scope: TMA replay/reopen/reauth admission only. Malformed, tampered, expired/future proof validation and live binding/grants checks remain independent.
Options: A preserve reusable correctly signed initData within current 24-hour age window, issue only after fresh authority validation (recommended compatibility); B single-use requires coordinated mobile reconnect rule; C bind repeats to one idempotent session exchange, requires new contract.

## Q003 — Текстовая легенда иконок карты

Sources: canonical JS-STUDENT-24 requires textual icon legend; backend-requests.md MAP-06 defers common PlanIcon catalogue/PlanLegend filling to a separate session; wireframe105 leaves filling unresolved. No accepted icon dataset found.
State: ANSWERED — owner chose A; D004, 2026-10-11T19:38:56.703052+00:00. Only legend catalogue/filling and its mobile acceptance depend on answer; map upload/sanitize/read/download/history remain independent.
Choices: A retain explicit separate-session deferral until accepted icon meanings are provided (recommended compatibility); B include now with owner-provided accepted icon set/meanings. Do not invent legend meanings.

## Q004 — отсутствующий rutcampustrack-design
Source: owner TASK-CONTRACT section4 requires current rutcampustrack-design before UI correction; frontends/AGENTS.md:19 repeats it. Executor skills catalogue empty; cloud catalogue and searched repo/handoff/.agents do not provide requested skill. Current accepted design references, existing Vue components/tokens and README are available; no UI correction has been performed.
State: RESOLVED — supplied archive verified116/116 manifestfiles, safe paths/CRC PASS, all3projectskills read; rutcampustrack-design available beforeUI; no fallback design-source permission selected; dependent only actual necessary UI correction, not formatter/harness/server/client diagnostic work.
Choices: A apply existing accepted repo design sources + frontends/AGENTS/tokens to preserve current behavior until skill becomes available (recommended); B defer UI corrections requiring skill until its exact accessible location is supplied. No redesign/Figma writes in either branch.

## Q005 — гео после ручной отметки «н»
Source fork: docs/architecture/reference-rutcampustrack-design/backend-conflicts.md §3:541–555 allows successful in-window geo to replace HEADMAN manual ABSENT; its manifest status is copy-archive/decision metadata null. Current StudentCheckinService rejects before geofence, StudentAttendanceSnapshotService disables button; existing tests assert rejection. Canonical manual-appeal prohibition concerns past lessons, not unequivocally this live-geo case; no later dated override found.
State: ANSWERED B — 2026-10-11T20:12:30.349768+00:00; D005 preserves current geo prohibition; only accepted geo/automatic-failure vs manualabsence behavior and dependent server/clienttests blocked.
Choices: A valid in-window geolocation may replace manual «н» with «+», failed geo follows accepted automatic request rules (recommended, agreed-policy text); B preserve geo denial after manual «н», only accepted appeal route applies. Existing clocks/cutoffs/petition quotas/guards unchanged. Silence does not choose.
