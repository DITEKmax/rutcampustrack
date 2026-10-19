# CURRENT — PR2 recheck correction N1/F08

Persisted Goal Q031/D037: **IN_PROGRESS**. Owner GO20.10.2026: fix N1+F08, currentrequiredgates, handoff and stop READY_FOR_INDEPENDENT_RECHECK. No externalreviewercontact/merge/deploy. [Contract](TASK-FIX-F01-F09-20261019.md), [matrix](fix-findings-F01-F09-20261019.csv), [historical handoff](HANDOFF-F01-F09-20261019.md).

Starting **7863d6bc771b28103c9db2f634a2d1c6886a16ea**

Published/executable **b3a16f0b8a1ab6fe0bf83faf27c1a18b2cce4c94**

Active recheck base/local/remote/PR26d6e74e (fourtaskdocs after testedb3, D009sourceequivalent); branch codex/server-validation-20261011/mainbase3908fad9, PR2 open/draft/unmerged. These fullheaders preserve oldfixlineage; exactnewstarting26d6 in ignoredstart.safe.json. Historical v3 **39/39,196/196 FROZEN** bytes/weights unchanged. External SOURCE_ONLY recheck accepts8/9oldfixes; F08NOT_FIXED andN1newP2blocker. Oldauthor9/9/internalAPPROVE does not replace that verdict; reviewer artifacts403 is access limitation, not discardedauthorproof.

| ID | Current author state | External previoussnapshot / applicable evidence | Next |
| --- | --- | --- | --- |
| F01 | PRESERVED | FIXED26d6/sourceb3: empty persistedfences valid, malformedstrict; Mongo/PG/coordinator evidence retained | N1 preserves thisbehavior |
| F02 | PRESERVED | FIXED: exactRELEASEDtombstone/reorder/race/tuple/no stalefences | Narrowdelta applicability |
| F03 | PRESERVED | FIXED: Academic authoritativeeligibility/pending fence/profilelocks, actual8IT retained | Unchanged scope |
| F04 | PRESERVED | FIXED: FULLtrustedADMINreset/publicdeny/expiry/revoke; actual13IT retained | Unchanged scope |
| F05 | PRESERVED | FIXED: actualproductionprincipals/recovery/minrights/freshrestore evidence retained | Unchanged scope |
| F06 | PRESERVED | FIXED:68mandatoryenv/validator/Compose refs/negatives | Unchanged scope |
| F07 | PRESERVED | FIXED: source/downstreamlabels underproductionsemantics | Unchanged scope |
| F08 | SOURCE_READY; targeted runtime PASS; internal review APPROVE_SCOPED | NOT_FIXED on previous snapshot; new correction awaits external recheck | 144 PASS; new source gates pending |
| F09 | PRESERVED | FIXED: domainmoved/frozenwire/4consumers/Schedule provenance | Unchanged scope |
| N1 | SOURCE_READY; internal review APPROVE_SCOPED | New P2 blocker; actual PG runtime pending | Empty delete → no invalid outbox/effect → real barrier READY, through ordinary CI |

**Checkpoint:** ROOT tracked/indexclean beforestart, foreign25NULsha e46eaa7a preserved. Oldsourceb3all5gatesSUCCESS. Atnewstart docs26 Security39337556968/OpenAPI39337556960SUCCESS, Coverage39337556948 SUCCESS; PRCI39337556953 still IN_PROGRESS at23:14UTC; no cancel/rerun/no nextpushuntilterminal. Nativewatch f_native_verification, onlymetadata≥180s.

**Cause→minimalpatch→proof:** N1producer unconditionalpublish -> guardonlyphysicalcount0 -> realSchedule PG participant+BEFORE_COMMIT listener/outbox/effect/barrier test; nonemptyevent andexactACK unblock preserved. LocalruntimeRED NOT_RUN, sourceconfirmed notruntimePASS. F08sharedclient perchatpublication context across invalidation/rolecard/send/fallback/ack, startup locks eachattempt notbackoff -> controlled-event exactinterleaving RED thenGREEN +actualkeyboard/Redis/nextsync/currentrole/errors/isolation. No warmup/deadline/schema/minItems/barrier/assertion weakening.

**Owners:** RootsoleGit/docs/sharedconfig/contracts/generated. n1_empty_event solewriter /tmp/rct-n1-20261020 (Schedule), f08_publication_race solewriter /tmp/rct-f08-20261020 (bot), bothdetachedsparsebase26d6. Independentinternalreview afterfrozenpatches, neverauthorfixes. Newhelpers explicitSol6.1/high/forknone, actualmetadataUNKNOWN; no Astra/Terra/children/secondcontrolplane.

**Resources:**16GiB/4CPU, memory~6.5GiBused, workspace~4.122GB <4GiBlocalDockerfloor, tmp~4.417GB. No localDocker/DB/pulls/Gradle/nativefullcompile (unchanged128MiBNO_FIT_PROOF). Lightauthorchecks CPU1/RSS512MiB/output8MiB, tmp>=4GiB/workspace>=3.75GiB; cacheddeps only; realPG normalisolatedCI. No cleanup/deletion/cacheprune/foreignWT/data changes.

**Constraints/questions:** B013/B014/B015FROZEN; Q034/D039/historical2cfD002UNKNOWN, builtimagesSKIPPEDnotPASS. Production/secrets/VPS/grants/backup/rotation/mainmerge/deploy/externalTelegram/manual andallpreviousdeferredtopics excluded. A1–A5unchanged withoutprovenimpact. No substantialownerdecision missing.

**Verified substep:** five source/test files integrated with exact author hashes, manifest r2; cached GJF1.25.2/AOSP and Ruff formatting PASS, lexical/AST equivalence to reviewed r1. F08 baseline deterministic RED4FAIL/1PASS; fixed144PASS on Python3.12.14/aiohttp3.14.4, Ruff PASS. N1 Java syntax parse and diff check PASS, not typecheck or PG runtime. Independent internal reviewer APPROVE_SCOPED, findings0; previous eight fixes preserved in this delta. New final-author acceptance remains0/2 until required runtime/gates; external correction acceptance remains PENDING.

**Next:** scoped hooks/parser checks; wait old CI naturally, then one coherent normal publish and new-source push/PRCI/Coverage/Security/OpenAPI. Require both extended Schedule SOURCE_METHOD cases with current test hash, actual PG empty/no-effect/archive READY and nonempty ACK/replay.  New source-ready/runtimeaccepted andexternalreviewPENDING separate. Evidence ignored .agent/evidence/cloud-20261011/current-wip/n1-f08-20261020/; authorproof /tmp/n1-proof-20261020, /tmp/f08-race-proof-20261020. Final exactsource/docsSHA+inventory/handoff via mainchat; stopREADY_FOR_INDEPENDENT_RECHECK, no waitingexternalreview.
