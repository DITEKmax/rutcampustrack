# CURRENT — F01–F09 independent PR2 corrective stage

Persisted Goal Q031/D037: corrective source/author verification **9/9**, current5gates **SUCCESS**. Delivery **READY_FOR_INDEPENDENT_RECHECK**; final internal source+evidence review APPROVE9/9, taskdoc-only publication under D009, external acceptance remains pending. Owner GO: fix all9, sufficient checks, then READY_FOR_INDEPENDENT_RECHECK via основной чат. External acceptance **0/9 PENDING**; no merge/deploy. [Contract](TASK-FIX-F01-F09-20261019.md), [9-row matrix](fix-findings-F01-F09-20261019.csv), [handoff](HANDOFF-F01-F09-20261019.md).

Branch codex/server-validation-20261011; starting fix7863d6bc, main/base3908fad9. Published source **0a7561f4dca03c0defd7f5c130bde6149cc8e071** (parent e5dad037), normal13path commit/push/readback exact. PR2 open/draft/unmerged; finalbody/readback distinguishes executable and documentation SHA. Tested PR checkout **fc529f1cf26bd77d70d769e00bc78d5ba818b773**, same tree48652d1498d4429f2c2216ed45107c2b61980ada as0a (safe12216301942).

Historical [v3](selected-results-20261018-v3.csv) **39/39,196/196 FROZEN**; unchanged bytes/weights, separate from newfix acceptance. Current author **9/9**, external **0/9**; source-only APPROVE is separate from runtime.

| ID | Author status | Applicable sufficient evidence / current boundary | External |
| --- | --- | --- | --- |
| F01 | AUTHOR_VERIFIED | e5: Mongo empty/malformed + PG + coordinatorHTTP14 PASS | PENDING |
| F02 | AUTHOR_VERIFIED | e5: PG/Mongo reordered/racing commands + exactcancel receipts PASS | PENDING |
| F03 | AUTHOR_VERIFIED | 0a: all8current-source realPG IT PASS from Coverage safe12219360081; lock/race/stale/pending/adoption, PRtree bound | PENDING |
| F04 | AUTHOR_VERIFIED | 0a:209PASS/13source-matched OTP/reset cases, safe12215988025; FULL/public/LIMITED/expiry/revoke PASS | PENDING |
| F05 | AUTHOR_VERIFIED | e5: actual3PG/Mongo/files/minrights PASS; 0a recovery safe12216507669 PASS; push/PR targethealth actualrestore PASS safe12217886628/12217649284 | PENDING |
| F06 | AUTHOR_VERIFIED | e5:68missing/fullCompose PASS; currentinputs source-identical | PENDING |
| F07 | AUTHOR_VERIFIED | e5: actual8apps/grpc identity/426queries PASS; 0a pipeline safe12216469767 PASS; push/PR full8apps safe12215899546/12217274054 PASS | PENDING |
| F08 | AUTHOR_VERIFIED | e5/0a: Python delivery/startup causal checks PASS; realTelegram deferred | PENDING |
| F09 | AUTHOR_VERIFIED | 0a:40SOURCE_METHOD cases/4consumers PASS safe12217270792/12215927576; PGproducer same-source proof | PENDING |

**Current0a gates:** pushCI39312334292 / PRCI39312349434 / Coverage39312349496 / Security39312349568 / OpenAPI39312349525 **allterminalSUCCESS**, attempt1, no cancel/rerun. BuiltimagesSKIPPED. Push/PR actual2DOCX/8apps/4deltas/426queries/6GatewayOpenAPI docs PASS; freshapplicationrestore seed/pending/admit/attachment PASS safe12217886628/12217649284; semester UI archive/restore+participants RELEASED/no fences+cleanup PASS safe12219355699/12218082208. Actualpush operatorCLI SOURCE_METHOD PASS safe12218627540, ordinarysuite bootstrapSKIP remains explicitlySKIP. Authstrictcounterexample/restoredgreen SUCCESS. F03all8realPGIT PASS (Coverage safe12219360081); bothordinaryAcademicIT SUCCESS.

**Source/documentation SHA:** tested executable0a7561f4; actualPRmergefc529f1c/tree48652d14. Final taskdoc-only commit SHA is reported in final PRbody/readback and finalchat; it does not rerun prior receipts. D009 applies only exact-owned taskdocs with all product/test/config/generated/build/gate inputs byte-identical.

**Source freeze:** combined-source-r7.safe.json:53source/config/test/tooling paths (49modified/4new), plus5taskdocs overallstage58paths. All53 exact hashes/internal independent sourceAPPROVE through scoped rechecks. Latest13path precommit/Gitleaks/whitespace PASS. Final checkpoint owns only CURRENT/HANDOFF/F-table and append-only attempts; no productdrift. Foreign25untracked rawNULsha e46eaa7a retained, oldWTs/results/data untouched. Cachecleanup0 thisfixstage; no deletions/reset/prune.

**Previouse5 results preserved:** all5 terminal withoutcancel: push/PR/CoverageFAIL; Security/OpenAPIPASS. Causal newfixture defects fixed: F04 missingUSERS_VIEW prerequisite; F03 two MANDATORY proxy stubbing exceptions + collateralMockito; F09 ERRORmustdeny. Strict Auth pins/negativeanchors updated, sameoracles/readers/restoration. Raw primary artifacts/closed summaries retained, not inGit.

**Other gate corrections:** source-proven UIpoll optionalbutton TOCTOU fixed onlyTimeoutError+count0, unchangedfinalUI/domainassertions. Mongo target anonymousping strengthened to auth+writablePrimary+collectionreads, samebootstrap/deadlines/freshguards;2closedenum failurefields. Apps outage carrier fixed6reasons/5services×3phases/<4096, primary/cleanup/assertions unchanged. Exact e5 apps predicate/Mongoqueryphase/UIexception remainUNKNOWN. Both realDOCX/4meterdeltas PASS; PRlateGatewayOpenAPI TimeoutError retained, no guessedbusinessfix. Currentfullgates allSUCCESS; those historicalcauses stayUNKNOWN.

**Owners:** Root soleGit/taskdocs/integrator. DeleteF01/F02 WT f01-f02-delete; eligibilityF03 WT f03-eligibility; resetF04 WT f04-reset; infraF05–F07+CI+Mongo/appsdiagnostics WT f05-f07-infra; botF08/F09+UIfixture WT f08-f09-bot. All under /workspace/rct-fix-worktrees, baseline7863, finalownedinventories frozen/current0a integrated. Assigned Sol6.1/high/forknone; actualmetadataUNKNOWN accepted. Reviewer fix_risk_review RO, noauthorfixes. Authors completed/available for actualnewfindings, no heavy processes; observer completed lastsnapshot. AstraEXHAUSTED/Terraforbidden; nootherchats/CLIcontrolplane.

**Resources:** localDocker below4GiB workspacefloor; native fullcompile NOT_RUN/NO_FIT_PROOF fixed128MiB outputs (disjoint envelope145896076B beforeclasses/jars). No localDB/pulls/heavy lease/reserve reduction. Actualcompile/PG/Mongo/browser/metrics performed ordinaryisolatedCI. 16GiB/CPU4 Cloud is notfutureVPS capacity.

**Constraints:** B013/B014/B015FROZEN; Q034/D039 oldCONNECT503/preparatoryPUT500 causesUNKNOWN unchanged. No production/VPS/deploy/realmigration/grants/secrets/backup/rotation/mainmerge. Telegram/push/install/Safari/ownerstand/alertbot/externalprobe DEFERRED_LOCAL/OWNER_MANUAL_PENDING. Noownerquestion pending.

**Canonical ignored evidence:** .agent/evidence/cloud-20261011/current-wip/fix-F01-F09-20261019/; current0a-primary foractualproof; e5-primary and e5-all-terminal.safe.json foroldresults; source/corrections/review/publish/hash inventories beside them. GitHubartifactIDs are in matrix/handoff. latest-checkpoint.safe.json updated; do notrereadhugehistory.

**Next:** designated independent recheck F01–F09 viaосновнойчат after exact taskdoc-only publication/readback. Internal source+evidence review APPROVE9/9; source/product/gate inputs frozen. External0/9PENDING; no merge/deploy.
