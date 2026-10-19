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

## Q016 — Session bootstrap response contract

PENDING_OWNER; raised 2026-10-15T03:44:43.459902+00:00. Source fork: retained JS-SYSTEM-14 canonical knowledge/job-stories.md SHA87af4670d5d8f996d940de9a56815cacc4befeaf90e5ea406a60871329bd4981:874–876 explicitly requires tokens/profile/roles/active semester together rather than multiple client requests; archive-copy manifest, no confirmed owner date/contract revision; registry preparation/current_accepted_text repeats it with decision_status=new. Current public AuthApi.login/OpenAPI TokenResponse has only accessToken/refreshToken/expiresIn; current ready PWA/TMA obtain profile/session context separately. Existing successful client/session/semester data proofs do not show a combined response. No observed runtime fault and no API change inferred.
Choices: A preserve current successful login plus separate context requests for this Cloud acceptance (recommended compatibility with current published clients/API); B make one accepted login/bootstrap response include profile/roles/active-semester context now, implementing compatible existing-client support and current checks. Only this response-assembly criterion depends on the answer; authentication, live grants, session security and other scenarios continue. No future-web or new product authority implied by silence.

## Q017 — Cardinality of an EXCUSE request

PENDING_OWNER. Canonical knowledge/job-stories.md SHA87af4670…:795–799 explicitly retains one indivisible EXCUSE ticket for several selected lessons plus date range/inline eligibility. Later wire107 SHA366df74d…:44–46 replaces shared range/picker with one Pair SelectField, while explicit13.08 cancellation:82/85 names only manual “н”, not EXCUSE “у”. Manual request single-pair is settled by closedR2 and is not reopened. Current ready mobile EXCUSE form selects one pair; backend aggregate carrier exists. Source fork affects ONLY EXCUSE multi-selection/client cardinality/proof, not reason catalog, ordinary submission/cancel/archive/quota/auth. Choices A preserve several selected lessons in one indivisible EXCUSE ticket (recommended per explicit canonical acceptance); B one EXCUSE ticket per lesson, accept later one-pair layout for both types. No implicit owner choice or wholefamilyexclusion.

Q017 correction 2026-10-15T06:42:32.069409+00:00: Root directly reopened ExcuseRequestScreen.vue:90–108/250–278 and requests-controller:341–342. Full requests flow already supports lessonIds[] and multiple checkbox selection; Today contextual route is single. Earlier question premise currentformonepair was incorrect and was corrected in user commentary. Existing EXCUSE aggregate is implemented; pending fork concerns accepted future layout/cardinality/client proof only, no newimplementation claim. All other work independent.


## Q018 — PWA/TMA selected theme versus fixed dark appearance

PENDING_OWNER; raised 2026-10-15T08:54:03.161500+00:00 through mainasync. Current aa37 nativeAuthA349 FAILat440 afterselectinglight; actualcode PWAApp168/TMA112 lockedMode dark, sharedtheme143resolvesdark whilepreservingpreference; Appearance34rendersresolvedTheme. Existingthemeunits65/160matchdeliberatelock. Canonical JS-STUDENT-25:831requires3choices; mobile-spec343 dark-only describesnarrowearlystudentprobe, no acceptedgeneralwholemobiledeferral found. Oct4/5codecomments aren't ownerproductauthority.
Choices A apply selectedlight/dark/system now (recommendedcanonicalexpectedbehavior); B retainfixedDark andexplicitlyexplainlimitationinsettings. DependentONLYappearancebehavior/itsvisualoracle, no newweb/Angular/design/Figma. Password/security/admin/otherclientworkcontinues; silence neitherselectsrule norpermitsweakeningvisualassertion.


2026-10-15T09:29:29.263706+00:00: Q018 ANSWERED / D018: staged theme infrastructure, one existing screen may demonstrate light for owner validation; other mobile screens remain dark until validation. No all-screen light redesign; affected AuthA visual oracle must match new scope.
2026-10-15T09:29:29.263706+00:00: Q017 ANSWERED / D019: several selected lessons in one indivisible EXCUSE ticket and one decision. Existing full-form multi-select preserved; actual multi-lesson client proof still required.


2026-10-15T09:31:50.668456+00:00: Q016 ANSWERED / D020: tokens at login followed by authoritative context loading, operations unavailable until required context, live server rights remain. Combined response not required in current acceptance.

2026-10-15T09:33:00.546712+00:00: Q016 engineering recommendation explained and explicitly accepted by owner; D020 above. Token issuance and context assembly remain separate; no new endpoint.


## Q019 — Cloud WebKit offline capability

2026-10-15T09:35:38.308736+00:00: PENDING_OWNER. Exactbb W1directcachecontrol11960653133 fails353ms at offline reload3666 after exactonlinecontroller/cache/200/fromSW/document positives. This reproduces outside productionSW, but exactnativecause remainsUNKNOWN and no actualSafari verdict follows. Options: A accept genuineChromiumoffline/reconnect+WebKitonline inCloud and ownerexecutesSafarioffline locally (recommended finiteavailablecapability); B keepSafariofflineCloudBLOCKED until suitablecapability. Dependent onlyW1engineacceptancescope/requiredCIselection; no skip/change untilanswer. Existingproductionoffline tests and historicalFAIL retained; other workcontinues.

2026-10-15T09:36:45.637550+00:00: Q019 ANSWERED / D021: genuineChromiumoffline+WebKitonline accepted inCloud; actualSafarioffline transferredowner local OWNER_MANUAL_PENDING/notPASS. RetainoriginalFAIL/control/testSources, updateonlydependentCIengine selection. No unrelatedgate/assertion weakened.

2026-10-15T10:24:21.207843+00:00: Q015 ANSWERED / D022: current headman/helper effectiveMANAGE_HOMEWORK may delete any current own-group homework regardless of author. Preserve creator/history/audit/currentrights/revision/terminal guards; no purge/foreign scope. No open owner product question remains.


## Q020 — ADMIN semester/group average attendance report acceptance scope

2026-10-15T11:04:24.730111+00:00: PENDING_OWNER. Source design:knowledge/job-stories.md exactcopy/archive SHA87af4670d5d8f996d940de9a56815cacc4befeaf90e5ea406a60871329bd4981:247 retains average attendance bygroup/semester;249 cancels redzone only;251 explicitly leaves sixth ADMIN Statistics screen undecided. Wire131 SHA9ff9734dc83af8473ba8f30aad593b2358ed4ca05cb905a98b3ffc6d28c7451f:27/287 no carrier among131–135. No accepted API carrier/current repeal found in relevant currentmap/delta/decisions; source-omission doesnotcancel story. D006 explicitly doesnotadd ADMINstats screen. Choices A defer ONLY distinctADMINgroup-semester attendance report/export carrier to a separate accepted product/design packet (recommended); B include it now and select minimal ADMIN carrier/contract before implementation. Existing student/headman/teacher statistics, ADMIN activity/dashboard, exports and all independent task work continue. No speculative sixth screen/newendpoint/implicitexclusion. Dependent ONLY JS-ADMIN-12 and corresponding administration report/export carrier. JS-ADMIN-14 externaloperatorrecovery is settledseparateboundary, not thisquestion.


## Q021 — authenticated QR transfer to another mobile device

2026-10-15T11:35:27.239840+00:00: PENDING_OWNER. Canonical JS-STUDENT-25:825 SHA87af4670d5d8f996d940de9a56815cacc4befeaf90e5ea406a60871329bd4981 includes mobile-device binding byQR. Exact wire108 SHAcd95a9641447ee8d6c6afa6291a0fa09722584a1e611693d91984335d2d3a0c2:57 explicitly DeviceLinkQr fromalready-authorizedsession toseconddevice, NOT guestQrLogin;73 marksAPI/TTL/status/reissue unresolved. OwnerOct2decision SHA65b7bcd35de77f4903f5eb3eeb7b90d7f3db78b7258a1107b9dd761713a0727b:7 defersQRloginclientUI and:9 saysLOGIN package doesnotautomaticallyexpand device-link. No exactacceptedDeviceLink protocol/deferral found. Choices A defer ONLY QR transfer/binding a second device to separate product/security/clientdesign packet (recommended), preserveordinarypasswordlogin/currentserverQRlogin/sessionrules; B include device-link now with explicitaccepted issuance/confirmation/expiry/sessionprotocol before implementation. OnlythisJS25DeviceLinkclause dependsanswer; readonlydelegatedrights/profile/password/history/currentQRserver andindependentwork continue. No implicitUI/API/speculativetoken-transfer implementation; no D015native transfer assumption.


## Q022 — Lesson measure for teacher cross-group summary

2026-10-15T11:52:59.499701+00:00: PENDING_OWNER. Canonical JS-TEACHER06/14 and wire1234.3 say all MY pairs per group; accepted2026-09-23/Oct6 explicitly broaden group history reading, and existing selectedgroup stats intentionally count historicalother-teacher lessons. Read authority and measure are different; no explicitnewunscopedGROUPSmeasure found. Choices A allpastnoncancelled readable lessons in each currentauthorized group (recommended consistency with existingselectedstats/fullhistory); B onlylessons physicallyassigned tothisteacher. Onlynewall-own-groups comparison/itsmeasure dependsanswer; existingselectedreads/exports, ownscope/currentgrantchecks, journal/day/profile/nativeCI continue. No subject-namecrossgroupidentity/clientfanoutaverage/newendpoint inferred.

2026-10-15T13:05:58.831909+00:00: Q022 ANSWERED / D023: Teacher attendance is scoped by current group and subject. Exact answer and scope: docs/product/decisions/2026-10-15-teacher-attendance-current-subject-scope.md.

2026-10-15T13:05:58.831909+00:00: Q021 ANSWERED / D024: QR approves browser-web login; mobile admission remains separate. Exact answer and scope: docs/product/decisions/2026-10-15-qr-web-login-and-mobile-admission.md.

2026-10-15T13:05:58.831909+00:00: Q020 ANSWERED / D025: Include the administrator group-semester attendance report now. Exact answer and scope: docs/product/decisions/2026-10-15-admin-attendance-report-in-current-scope.md.


## Q023 — ADMIN attendance report screen and contents

PENDING_OWNER 2026-10-15T13:07:53.604981+00:00. D025 includes JS-ADMIN-12 now but requires separate agreement. Proposed current PWA/TMA dashboard entry→read-only «Посещаемость групп»: semester selector, group search/sort, group/attendance%/counted lessons/+absent+excused counts, existing accepted calculation excludes canceled lessons; no-data distinct from0%; existing five-format export; no canceled redzone/threshold. Choices accept proposed screen/contents (recommended) or owner changes. Only this new report carrier/model depends; other work continues.

## Q024 — ADMIN attendance report access

PENDING_OWNER 2026-10-15T13:07:53.604981+00:00. Proposed FULL and current LIMITED DASHBOARD_VIEW, server current read/export/download checks/revoke denial, group totals only/no student personal data; alternative FULL only. No new grant code or silent permission expansion. Depends only newD025 report.

2026-10-15T13:10:09.499816+00:00: Q023 ANSWERED / D026: Administrator group attendance screen and report contents. Canonical docs/product/decisions/2026-10-15-admin-attendance-report-screen.md.

2026-10-15T13:10:09.499816+00:00: Q024 ANSWERED / D027: Administrator group attendance report authorization. Canonical docs/product/decisions/2026-10-15-admin-attendance-report-access.md.


## Q025 — Full subject deletion and archived semester dependencies

PENDING_OWNER 2026-10-15T16:07:45.810298+00:00. Accepted JS-HEADMAN-36 canonical87af:556–558 requires irreversible subject+attendance+homework deletion with password+botcode. Current Subject group_id has no semester_id, so one subject can span multiple/archived semesters; existing subject.deleted cascade expressly rejects retained canonical history/writable-semester violation. Semester-delete approval cannot authorize archive purge by analogy. Choices A (recommended history-safe): reject full subject deletion if it has closed/archived-semester dependencies; otherwise delete all accepted current subject data after password+purpose-specificbotOTP. B explicitly include closed/archived subject data in irreversible cascade with clear scope warning/confirmation. Only full subject deletion/archive branch blocked; all unrelated work continues. No production deletion or accepted history rule change before answer.

## Q026 — Full subject deletion and indivisible mixed-subject EXCUSE ticket

PENDING_OWNER 2026-10-15T16:07:45.810298+00:00. D019 preserves one indivisible EXCUSE ticket/decision; current validateLessonIds permits multiple subjects in one group+semester. Accepted JS-HEADMAN36 says all subject-related data, but does not decide removing one subject from a ticket covering others. Choices A (recommended conservative): reject full subject deletion while such a mixed-subject ticket exists. B preserve ticket/history and other subjects' coverage, remove only deleted subject's lessons from that ticket with an explicit history record; original decision remains one operation, no silent split/purge of other subject data. Only this destructive mixed-ticket branch depends. Do not silently delete whole mixed ticket or assume accepted cascade semantics.


## Q027 — Stored incremental attendance aggregates versus authoritative calculation

PENDING_OWNER 2026-10-15T19:30:06.114053+00:00. Canonical JS-SYSTEM-16:894–899 (87af) requires server-owned percentages/counts and separately says aggregates must be stored/incremented rather than recomputed on reads. Existing real PG/Mongo/API proofs establish correct authoritative server calculations/null/zero/cancel/history scope, but not persistent incremental counters. Confirm whether to retain current authoritative calculation for current acceptance and defer new incremental-storage architecture until measured need (recommended), or implement incremental storage now with transactional/replay/correction/history/reconciliation rules. Only new storage strategy depends; independent compatible server-owned journal-percentage gap correction proceeds. No silent exclusion/newarchitecture/latencySLA.

2026-10-15T19:59:41.591452+00:00: Q027 ANSWERED / D028. Owner exact answer: «Сохранить расчёт при запросе; отдельные счётчики отложить (рекомендую)». Retain authoritative request calculation; defer only stored incremental counters. Server-owned journal metric correction and all remaining accepted correctness/security/data checks continue. Canonical docs/product/decisions/2026-10-15-attendance-aggregate-storage-deferral.md.

2026-10-15T20:08:47.288627+00:00: Q025/Q026 still PENDING_OWNER; re-presented the same two product forks while owner replied Q027. No new scope/question IDs or inferred consent; only full-subject-delete archived/mixed-ticket branches depend.

2026-10-15T21:25:11.147945+00:00: Q025 ANSWERED / D029: semester-local new subject/lesson IDs; no cross-semester subject identity or inferred archive purge. Q026 ANSWERED / D030: preserve mixed ticket, remove only deleted subject lessons with history. Exact answers and retained constraints: docs/product/decisions/2026-10-15-subject-deletion-semester-and-mixed-excuse.md. Dependent implementation is now unblocked at the product-policy level; Source contract/schema planning and real acceptance remain.


## Q028 — новый этап: точка входа 18-недельного WORD

PENDING_OWNER. Текущие разобранные Headman/Teacher DOCX идут синхронно HTTP→Java XML/ZIP renderer, без Rabbit/job. Это не причина исторического сбоя. Уточнить роль/кнопку, где запросили WORD: староста/журнал, преподаватель/статистика, администратор/группы либо точный другой путь. Зависит выбор исходного воспроизведения/пользовательского ожидания; существующие 18week fixture, безопасное boundedrecovery и genuineRabbitDLQ проверки продолжаются. Автоматический retry/poisonisolation уже приняты, повторно не спрашиваются.

## Q029 — новый этап: числовые цели полезных операций

PENDING_OWNER. Existingrunbook задаёт businesssampling100% и quiet22–08MSK; полезные eligible/good/error/slow/SLO не заданы. Предложение для согласования: allowed validoperations без неожиданного5xx/terminalfailure ≥99.9% за30days; interactive p95≤2s, длительные отчёты отдельно по фактическому измерению; expectedvalidation/authz4xx не servicefailure. Альтернатива: на этом этапе принять честные технические alerts, формальныеSLO согласовать послеbaseline. Зависимы только нормативныеSLO/пороги, telemetry/formulafixtures/ошибки/DLQ/recovery/панели идутнезависимо. Учебныецифры не выдавать за ужеaccepted.

## Q030 — новый этап: независимый probe потери host

PENDING_OWNER/ENV. В текущей схеме Blackbox на тойже машине; полногоhostloss такойнаблюдатель не доказывает. Нужен выделенный внешний syntheticprobe/endpoint либо явное решение владельца о переносе только внешнегоhostprobe в локальную приёмку. Это отдельно от ужеDEFERRED_LOCALalertbot, не исключать автоматически. Зависит только полноценный offhosthostloss detection; существующий мониторингсервисов, alerts→testreceiver и все9source/runtime пакеты продолжаются. Не запрашивать реальныеtokens/chat/secrets.


2026-10-17T18:59:09.922619+00:00: Q028ANSWERED/D032 headmanjournal; Q029ANSWERED/D033 engineeringSLOdesign delegatedRoot; Q030ANSWERED/D034 externalhostprobe deploymentbacklog/DEFERRED_LOCAL, notCloudPASS. No pendingownerquestions frominitialnewstage.

## Q031 — 18.10.2026: persisted Goal surface

2026-10-18T00:17:53.194691+00:00: PENDING_OWNER (workflowmetadata only). SourceD036 requires actualGoalupdate via exposedmechanism; ALL_TOOLS hasno Goalread/update/create API. Existing17filegoal remainsIN_PROGRESS6/9, extended18contract/matrix saved; notfalsecomplete. Options: use TASK-CONTRACT/CURRENT as persistedextendedGoal afteractualoldcriteria (recommended), or ownerupdatesseparateappUIGoal. NotrepeatcodeGO, no budgetchange. OnlyplatformGoalmetadata transition depends; currentoldcriteria/independentKT1/authorisedreversiblework continue. Asked inmainCloudchat via штатныйasyncinput.

2026-10-18T00:20:24.607420+00:00: Q031ANSWERED/D037: TASK-CONTRACT/CURRENT как persistedGoal; платформенныйGoal неимитируется. Зависимыйworkflowвопросзакрыт, кодовыйscope/oldcriteria продолжаются.


## Q032 — 18.10.2026: точный offline compile / Gradle loopback IPC

ANSWERED_OWNER. Initial block:  Prepared seven-module compileJava+compileTestJava, frozen source662bb37e61533295626b99877f90ed3b860a0118f18dca361fe8dd1cd3576c05 in /tmp/rct-s19-auth-runtime, failed before any tasks: ServerSocketChannel.open/TcpIncomingConnector.accept EPERM; independent127.0.0.1:0 bind errno1. Auto-review rejected exact additional network.enabled=true request: “This repeats the same failed offline compile without new facts while enabling broad network access, contrary to the explicit offline/no-download constraint; only a narrowly justified loopback allowance was contemplated.” Schema has no loopback-only flag. Requested new direct authorization for SAME prepared offline/no-download/no-tests/noDocker/noServer/noProduction/noSecrets invocation, CPU1/RSS2.5GiB/TMPfloor4GiB. Exact command/safeguards in existing ignored current-wip/integration-compile/environment-block.safe.json; compile2 NOT_STARTED, processes0/leaseRELEASED. No alternate caller/launcher/CI used to bypass rejection. Only dependent local compile waits; Group/FE/source integration continue.

2026-10-18T12:52:59.738490+00:00: Q032 direct answer: «Подтверждаю этот конкретный запуск». Same frozen662bb37e run2 admitted by auto-review and executed exactly offline; FAIL only missing io.sentry:sentry:8.61.0, no app compile result. Exit1/60.33s/RSS787660800B, no remaining processes. Existing Apps cache supplied four exact JAR/POM/module/descriptor files (1,282,623B) to private cache without network; Gradle resolution after import NOT_RUN. Next coherent source compile is separate changed-source verification, not Q032 replay/permission bypass.

## Q033 — 18.10.2026: initial administrator для fresh split БД

ANSWERED_OWNER/D038, 2026-10-18T16:41:33.681661+00:00: «Подготовить одноразовый bootstrap в коде (рекомендую)». Контекст: новые active migrationchains не создают sampleactors, canonical usercreate требует существующего liveadmin; oldbrowserTEST_USERS ожидает исторический V2. Code-only operator bootstrap согласован; реальные accounts/secrets/deploy остаются вне scope. Disposable synthetic baseline выполняется независимо; новый CLI — отдельный ограниченный контракт/веса до implementation, без повторного вопроса владельцу.


## Q034 — historical CONNECT/Mark500 cause evidence versus current functional acceptance

State: ANSWERED — D039. Existing last ownerGO required actual causes of D9 Academic→Schedule CONNECT/503 and preparatory HEADMAN attendance PUT500. Old35078 safe primaries retain closed source/stage/status/cause or HTTP outcome, but not the underlying exception/commit outcome. Current0de paired original D9v10 and both bot paths passed; new passive/closed diagnostics and5source-method helper tests are verified, no speculative reconnect/write retry was introduced. Missing historical observations cannot be reconstructed from these successes.

Question in main Cloud chat: accept explicitly preserved historical cause UNKNOWN as an exception, allowing U25 closure ONLY after all final green gates and independent review, with diagnostics retained for a future natural failure; or keep U25 open until actual causes are established. Recommendation: first option, with no old-error fixed/environmental claim and no new broad rerun only to seek a failure. This is a proposed acceptance exception, not inferred approval. At ask time the pending answer blocked only final closure. It was subsequently answered by D039 below; current final gates and independent review are now accepted.

2026-10-19T16:26:53.338607+00:00: Q034 ANSWERED exact: «Принять ограничение после зелёных gates и review (рекомендую)». Owner accepts preserved historical CONNECT/503 and Mark500 underlying UNKNOWN as an explicit diagnostic limitation ONLY after all final green gates and independent review. No old-failure fixed/environmental claim; new bounded diagnostics retained for future natural failure. Gates/review/othercriteria and FROZEN unchanged.
