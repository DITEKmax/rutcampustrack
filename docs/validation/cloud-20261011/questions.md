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

## Q006 — Колонки и аудит выгрузки списка пользователей
Source: canonical JS-ADMIN-13:253–259 explicitly accepts filtered five-format user/group exports but leaves exported columns/audit unresolved; ПК132:202. EA07 source-only current absence; fulluserimplementation dependent only exactcolumn/auditbranch.
State: ANSWERED B — owner includes Telegram ID for FULL admin and audit fact; D006. No passwords/recovery links; LIMITED ordinary fields only.
Choices: A ordinary registry columns (ФИО/login/roles and statuses/group/staff number/binding state), no Telegram identifier/password/recovery links; audit actor/time/kind/row count without file content (recommended). B same plus Telegram ID for FULL admin; same audit. Ownerfreeform exactschema also possible. Initial/reset-password display exception alone does not grant export.
Dependent scopes: admin-user exportmodel/PIInegativecontrols/audit; FULLgroup/headmansubject/existingexportverification independent.

## Q007 — LIMITED bulk-export authority
Source: 2026-10-08-limited-administrators and explicit12grantcatalogue contain USERS_VIEW/GROUPS_VIEW but no explicit acceptedexportrule; EA08 FULLgroup independent.
State: ANSWERED A — current USERS_VIEW/GROUPS_VIEW permit corresponding ordinary registry export; D007, no new permission code.
Choices: A respective ordinary registry export follows live USERS_VIEW/GROUPS_VIEW and same allowed filters/fields (recommended consistentaccess, no credentialgrant). B registry export FULL-only; LIMITED read grants do not allow export. No newgrantrename/code withoutownerdecision.
Dependent scopes: LIMITEDuser/group exportboundary; FULLgroupimplementation/serverdiagnosis independent.

## Q008 — PNG validation resource budget

Source: accepted MAP04/image upload compressed limit10MiB, no explicit decoded/work/dimension limit found; new independentreview MVR03 sourcecounterexample PNG1×1billion rows/2GB decoded can fitcompressedlimit anddrive1billionvalidationloops. Currentexisting8KiBmemory bound notCPUbound. Classification SOURCE risk, actual expensivecandidate deliberatelyNOT_RUN, no latency/browserFAILclaim.
State: PENDING owner; required only exactnewPNGprocessing-budget policy, MVR01/02SVG corrections/client/server/exportruntime independent.
Choices: A permit operationalguard 128MiB decoded scanline bytes and1million scanline validations, reject overbudgetPNG with clear4xx (recommended initialsafeceiling, preservewide100000×1 fixture). B permitdifferent explicitnumericlimits supplied byowner. C retainonly10MiBcompressedlimit, require alternative strictlybounded validation design without rejecting other acceptedvalidPNG; additionaldesignwork thenactualresourceproof required. No answer selectsnewrule.
Dependent: MVR03 correctedPNGresourceguard/overbudgetboundary; no canvassizeproductrestriction inferred from silence.

2026-10-11T22:34:30.054529+00:00: Q008 owner requested explanation, supplied response is notdecision. Uploadcap10MiB unchanged; clarified128MiB isdecodedworkbudget/notfileorresidentmemory, examples4000×4000≈64MB. Nativequestionreasked A128MiB/1Mrows(recommended), B64MiB/1Mrows, Cdefer. StillPENDING; silence grantsneitherlimit.

2026-10-11T22:36:00.514745+00:00: Q008 ANSWERED A onclarifiedquestion: «Разрешить предел 128 МиБ (рекомендую)». D008 accepteddecoded134217728bytes and1000000validatedrows includingAdam7passes, compressedfilelimit10MiB unchanged. Earlierclarificationwasnotdecision. All Q001–Q008 resolved.
