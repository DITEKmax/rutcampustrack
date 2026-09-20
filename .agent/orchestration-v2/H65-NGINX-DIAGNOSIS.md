# H65 root Nginx diagnosis — 2026-09-19
## Goal
S2 bounded actual startup configuration diagnostic for S3 H62 failure. Prove/refute missing upstream hypothesis with one pinned Nginx container, not full service rerun.
## Context/evidence
H62 all sixbackendsready then empty edgeIP; no startuplog captured. Retained exact generated config references api-gateway plus absent landing-nginx/pwa-nginx/mini-app-nginx. Root read originals and converter. All H62resources removed; H64 Lessons currentlyheavy, H65 must wait actualrelease.
## Relevant scope
Root soleexecutor/evidence owner orchestration-v2/evidence/h65-*; readonly mounts H62 run20260919-190530008-e9xjq42a/nginx/nginx.conf anddefault.conf. Oneunique container labelled rct.runtime-owner=student-requests-edge-diagnostic and unique run. Existing pinned nginx digest65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10 only.
## Required behavior
After H64release create unique --network none --pull=never --add-host api-gateway:127.0.0.1, noports, readonly configmounts, entrypointnginx -t. Capture createID/startattach stdout/stderr/exit. No ephemeral TLS keys are available after H62cleanup; interpret earliest error accurately and never claim positiveTLSconfigvalidation. Expected unresolvedotherupstream failure is diagnostic evidence, not productPASS.
## Constraints
RULES B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A. Exclusive H65only afterrelease; exactrequire_escalated/loginfalse. No source/config/key changes, networkcreation, secrets, foreigncleanup, pulls, production operations.
## Existing patterns
ExactID/ownerlabels check before removal; try/finally and verifyabsence. Preserve raw bothstreams and expectednonzero process. Use existing generatedconfig bytes and digest so no permissive alternative config.
## Acceptance criteria
Actual bounded diagnostic records concrete earliest nginx error or success, owncontainercleanup verifiedabsent, unchangedsource/config. Confirmedcause/limitations determine nextsourcecorrection packet; no speculativefix or IPcheckweakening.
## Verification
Record revision426a15b6, currentrunner2B47, configSHA, command,identity,exit,raw/cleanup. Root inspectactualerror, then separatelyauthorize repair/review/runtime. This alone does not close H62/I1/I2.
## Do not
No allservice rerun, newharness framework, TLS bypass for product/runtime, ambientcredentials, arbitraryhostaliases for missingUIservices, deploy/push/mainmerge or concurrentH64Docker.

## H66 diagnosis amendment
H65 actualnginx-t exit1 firsterror unknown directive BOM+worker_processes atline1; ownedcontainercleanupverified. Root now authorizes H66 sameonecontainerdiagnostic with separate rootowned evidence/h66-nginx-config copies, stripping ONLY initialUTF8BOM bytes from H62 original generated files, originals immutable. Exactbyteprefix and remainingbyteequality recorded. Networknone/noports/onlyapi-gateway loopback alias; missingTLSkeys remain deliberate limitation. This is diagnosticartifact preparation, not product/runnerconfigfix. Confirm next actualerror before repair; no otherheavy currently. Preserve H65/evidence; sameownedcleanupguards.
