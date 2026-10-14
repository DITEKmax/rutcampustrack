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
State: ANSWERED A — D008; required only exactnewPNGprocessing-budget policy, MVR01/02SVG corrections/client/server/exportruntime independent.
Choices: A permit operationalguard 128MiB decoded scanline bytes and1million scanline validations, reject overbudgetPNG with clear4xx (recommended initialsafeceiling, preservewide100000×1 fixture). B permitdifferent explicitnumericlimits supplied byowner. C retainonly10MiBcompressedlimit, require alternative strictlybounded validation design without rejecting other acceptedvalidPNG; additionaldesignwork thenactualresourceproof required. No answer selectsnewrule.
Dependent: MVR03 correctedPNGresourceguard/overbudgetboundary; no canvassizeproductrestriction inferred from silence.

2026-10-11T22:34:30.054529+00:00: Q008 owner requested explanation, supplied response is notdecision. Uploadcap10MiB unchanged; clarified128MiB isdecodedworkbudget/notfileorresidentmemory, examples4000×4000≈64MB. Nativequestionreasked A128MiB/1Mrows(recommended), B64MiB/1Mrows, Cdefer. StillPENDING; silence grantsneitherlimit.

2026-10-11T22:36:00.514745+00:00: Q008 ANSWERED A onclarifiedquestion: «Разрешить предел 128 МиБ (рекомендую)». D008 accepteddecoded134217728bytes and1000000validatedrows includingAdam7passes, compressedfilelimit10MiB unchanged. Earlierclarificationwasnotdecision. All Q001–Q008 resolved.

## Q009 — Final receipt-only commit versus exact-revision CI

State: ANSWERED A — D009, raised after actual published636 checkpoint; dependent ONLY finalreceipt/strictDONE gate, all diagnostic/implementation/format/runtime/review work independent.
Sources: TASK-CONTRACT §7F/§8 explicitly says docs-only paths-ignore leaves exact-revision criterion a question/blocker until concrete decision; current .github/workflows/ci.yml, coverage/security workflows retain existing triggers. Final current CI receipts/handoff must be saved; their later docs-only commit produces a new SHA with ignored gates. No artificial product change or trigger change authorized by silence.
Choices: A allow final docs/evidence-only commit with exact product/config/test source byte identity to the last all-three-green revision, root documentation/parser/security gates, and explicit producer/current SHA distinction (recommended); B explicitly allow safe manual workflow_dispatch for the three existing gates on the final designated-branch SHA, existing checks/Deploy unchanged, actual meaningful task execution required. No choice until owneranswer. A changes only final receipt evidence policy, not product acceptance; B needs soleconfig implementation/review before run. Preserve exact final local/remote/PR equality and draftmain under either.

2026-10-12T00:14:21.446201+00:00: Q009 ANSWERED A: «Разрешить финальный коммит документов с доказанным совпадением исходников с проверенной revision; явно сохранить оба SHA и CI-квитанции (рекомендую)». D009 permits only finaldoc/evidence source-byte identity exception, all product/gates/scenarios/reviews remain. AllQ001–Q009resolved.

## Q010 — Platform approval blocks isolated synthetic stand startup

State: ANSWERED / PLATFORM_APPROVAL_ACCEPTED; same exact owned Docker caller accepted after explicit D012, stand launched and safely stopped. Only future startup needs fresh resource/ownership bindings; no open Q010 authorization blocker. Existing TASK §5 explicitly authorizes syntheticruntime, devchecks/dependencies. Automaticapproval nevertheless rejected exactDocker6 start citing sharedstate/container/network/possibleDBmigrations and insufficient trustedauthorization. No process/data change, no workaround. Recommended: explicitapproval exactownedproject startup with retainedsynthetic5vol/nativeMongo/noreset/bootstrap, no production/deploy. CI-source qualification/integration/review continue independently.

2026-10-12T23:15:49.431973+00:00: Q010 ANSWERED affirmative «Подтверждаю этот изолированный тестовый запуск»; D012 explicitsameownedDocker6+8syntheticprocesses/noreset/bootstrap/prod/deploy. Platformapprovalstillrequired; no alternativebypass.

## B013 — platform content rejection of T008 rights sensitivity preparation

State: BLOCKED_PLATFORM, no new product question. Author turn rejected for possible cybersecurity risk during authorized one-rule mutation Source preparation. Existing real PostgreSQL rights test PASS; sensitivity proof remains open. Do not retry via rephrasing, another agent/model or launcher. Continue independent integration/CI/client/auth test work. Exact displayed recommendation: Daybreak access before retry; no claim that owner confirmation alone removes platform control.

## Q011 — platform approval rejects concrete Node checking package

State: ANSWERED / SAME_CALLER_PLATFORM_APPROVAL_ACCEPTED / LOCAL_CHECKS_COMPLETE. Source: actualautomaticreview functions cell99 refused beforeexec: operation unrelated to user ZIP upload; delegatedauthorization insufficient; no alternatelauncher/indirectbypass. Owner TASK sections4/5 already authorizes checks/formatting, but currentapprovaldidnotacceptdelegatedcontext. Scope: exact Notification ownWT1fd, artifact-privacy.ts + existingprivacy.control.spec.ts, Sourceprivacyreview371bf; cachedPrettierapply/check, existingreal-mobileTSC, unchangedbrowserfreeprivacychecker59fff andoneRAMbaseline8ac counterexample; no Java/Docker/browser/network/server/prod/deploy/credentials. Choices: A explicittrustedOwnerconfirm theseexactlocalchecks (recommended); B deferonlythesechecks/retainSourceWIP whileindependentCAPordinaryCI/matrixworkcontinues. Missinganswer blocksNodechecks/integration ofthispackage/currentHeadmanafterdiagnostic only. Rejectionreceipt: receipts/first3-spec-frame-node-approval-rejection.safe.json. No effects/privatecachelinks/processes; Rootmustnotretryviaanotherlauncher.

2026-10-13T20:29:47.870838+00:00: Q011 ANSWERED affirmative «Подтверждаю эти конкретные локальные проверки». D014 confirms SAMEalreadyreviewed scopedNodecaller afteractualrejection; new Rootfreshgrant andautomaticapproval stillrequired. No effects/bypass/changedproductrule.

2026-10-13T20:34:12.877350+00:00: Q011/D014 SAMEfunctionsaction automaticapproval ACCEPTED; Node4055 started, formatterapply0. Originalrejectionpreserved; no alternativecaller/bypass. Exacttype/control/sensitivity/cleanup results pending.

## B014 — 2026-10-14 Gateway login IP-limit checking turn content rejection

PLATFORM_BLOCKED/FROZEN. After Source preparation of one current5/IP realTCP witness, the author turn for fresh authorizednative checking and a future relevant sensitivity proposal failed with possible cybersecurity risk; displayed recommendation Daybreak access. Exact rejected suboperation UNKNOWN; no model/agent/rephrase/launcher/ordinaryCI equivalent bypass. This is not missing owner authorization/new quota. Source WIP retained; independent attachment/Q01/currentproof work continues. Native effects checked from own savedstate before dependentwork.

## Q012 — Standalone removal of current group headman

PENDING_OWNER, dependent ONLY Q04 standalone admin PATCH isHeadman=false/no replacement. Existing current runtime/test permits it; existing later wireframe133:310–325 says explicit removal forbidden/replace only but manifest marks source candidate copy-canonical/owner-date null, earlier samefile77/209 conflicts, referenced backend-conflicts293 is copy-archive. Accepted JS-ADMIN-07 requires transfer to new headman but does not decide standalone removal; no dated override found. Do not call existingbehavior DEFECT or no-remove accepted solelyfrom archive.
Choices: A only replacement (recommended current later UI specification); B allow standalone removal, revoke old helper authority and move group to draft (existing legacy server behavior). All replacement/foreignscope/revocation/restore/current journal/recovery checks independent. Need explicit owner answer before dependent product/test changes; silence grants neither rule.


## Q013 — 2026-10-14T17:24:57.385466+00:00 — native PWA/Telegram/Web Push acceptance capability

PENDING_OWNER / resource/evidence choice, not authorization for production or private bot. Sources: canonical JS-STUDENT-08:771,09:837,11:841; JS-SYSTEM-11/12:911–917 in docs/research/reference-rutcampustrack-design/knowledge/job-stories.md; existing finite E14-I1/H2. Cloud has synthetic Telegram host + real server and ordinary browser/SW checks, but no assigned native Telegram test binding, real WebPush subscription or device/OS installation evidence. Browser mocks do not prove these effects.
Choices: A assign separate synthetic Telegram/WebPush/native-device test capability through supported environment bindings (no credentials in chat, recommended complete original acceptance); B owner performs the finite native checks on the reviewed revision and supplies dated safe receipt of observed result/device/revision; C explicit owner defers only these native/provider criteria to a separate acceptance session (changes current release/contract scope, not presumed). Dependent only native Telegram launch/deeplink/Back/download, actual background WebPush target/private-boundary and OS install/standalone. Real backend/PWA browser/unit/CI/formatting/review continue independently. No answer means no scope exclusion, no production/private bot access.

2026-10-14T17:27:58.313140+00:00: Q013 ANSWERED / D015. Owner exactanswer: «я проверю это сам на локальном тестовом стенде пока просто логику и код проверь что он согласован корректен и должнн работать в различных ситуациях». Native Telegram/WebPush/OS installation transferred to owner local test stand; Cloud retains code/logic and available synthetic/realbrowser/server checks. Native status OWNER_MANUAL_PENDING, notPASS; no Cloudwaiting for those capabilities, no new production/privatebot/merge authority.

2026-10-14T17:34:08.654748+00:00: Q012 ANSWERED / D016. Owner exactanswer: «Разрешить только замену на другого старосту (рекомендация по позднему макету)». Reject standalone admin isHeadman=false/no replacement; retain accepted replacement with oldauthority revocation. Specific branch only, unrelated automaticsecuritycleanup/archive/restore rules not silently changed. No unresolved ownerproductquestion remains; Q013native OWNER_MANUAL_PENDING separate.


## Q014 — 2026-10-14T17:39:18.577055+00:00 — accepted temporary PWA install icon

PENDING_OWNER. JS-STUDENT-09 needs installicon and D015 retains correctcode. Actualmanifest hasnoicons. Appliedrutcampustrack-design points BRAND_DIRECTION.md121: ownerwilldrawlogo/brandlater, currentbrandarea placeholder. Existing green-blue R landing/dist/favicon.svg islegacy/candidate, notauthoritativecurrenticon; Angularunchanged. Choices A owneracceptsexistinglandingR byteexact as temporaryinstallmark (recommended minimalexistingasset reuse/no newdesign); B ownerretainsbranddefer andwillprovideownacceptedicon/implementitlocally, explicitlydeferonlythisasset/nativeinstallprerequisite. DependentonlyPWAmanifest/icon/indexasset Sourcefix; allotherCloudworkcontinues. Noanswer grantsnewbranding.

2026-10-14T17:41:54.427369+00:00: Q014 ANSWERED / D017. Owner exactanswer: «Использовать существующую R как временную иконку (рекомендую)». Use exactexisting R assetSHA85223ceb39a2ebb25dcc77ddb5287ea27b982c2ce5d2cf444a6e3507e573b9e8 temporarily, no newdesign/Angular/Figma; eventualbrandowner later. No open ownerproductquestion.

## B015 — S1 ordinal identity projection rejected

PLATFORM_APPROVAL_BLOCKED/FROZEN for the proposed identity/declaration loosening only. c62_policy_method_proof strict artifact reader stopped at SOURCE_METHOD/status assertion; attempted diagnostic projection retaining ORDINAL_ONLY was rejected by automatic approval because it could create false security evidence. No new owner authorization question or claim of failed product security test; Source-only assessment and other independent accepted work continue. Do not retry an equivalent caller/schema/agent/model/CI/rephrasing. Existing exact named S2 fifteen PASS remain valid; further ordinal/aggregate variant paused. Exact rejection and safe native state: receipts/current-4065-real-push-readback.safe.json#next_reviewed_integrations.S1_ordinal_identity_projection_approval_rejection.

## Q015 — Кто может удалить актуальное домашнее задание

PENDING_OWNER; source JS-HEADMAN-17:598, canonical docs/research/reference-rutcampustrack-design/knowledge/job-stories.md SHA256 87af4670d5d8f996d940de9a56815cacc4befeaf90e5ea406a60871329bd4981. Editing non-author homework is explicitly accepted for current HEADMAN/MANAGE_HOMEWORK, but extension to DELETE is explicitly unresolved. Own-author HEADMAN DELETE/archive/history remain independentlyaccepted. Choice A (recommended consistency with group management): current HEADMAN or helper MANAGE_HOMEWORK may delete any active homework of theirown group, authorship informational; currentauthority+foreignscope/terminalguard+history remain. Choice B: only author with currentpublish/managementauthority may delete; nonauthor denied. Dependent ONLY nonauthor DELETE permission/tests/UI; nochange untilanswer. OwnDELETE/API/browserarchive Sourceplan continues; no newpermission requestforalreadyauthorizedwork.
