# CURRENT — PRODUCT GO, 2026-10-03

Фаза: функциональная PWA/TMA интеграция. Figma/redesign, настоящий Telegram/provider и deploy отдельно; offsite backup/RPO/RTO до deploy. Цель НЕ достигнута, GO сохраняется. Root sole main/shared-docs writer; main переносы разрешены, push/deploy отдельно.
RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA. Все текущие дочерние задания Sol6.1 high; отдельная runtime metadata недоступна. Foreign dirty/protected WIP сохранять, no reset/stash/clean.

## Интеграция и принятый результат
Main cce2ce24; current product freeze a93f4265a2ad397d60d3758ea57116b8616916d0. Roster backend4a2a9114/5c674c2a, UIb43ad9dc/a0821a8c; ONE_OFFca8461f5/4d2edd73/evidence4fe996f0; Homeworkab27316f/6230f48a/evidence2ac4fba7/d1745515. Independent affected reviews/scoped checks PASS. Details source evidence directories retained.
9450d6fe (source3ec63094) adds ONLY safe static operation/status logging to MobileAcademicClient; evidencea93f4265. Does not fix proven rootcause by itself. Mapping/auth/deadlines unchanged, root inspected complete diff.
Real PWA runR2 accepted bounded:
- HEADMAN creates ONE_OFF2026-10-05 09:00–10:30 №1 Локальная-101 assignment1; server canonical list, fullreload then TodayOct05 confirms persisted physical lesson.
- HEADMAN DATE homeworkOct06 subject1 create/edit; server list and historyVersion2/actor3; fullreload→STUDENT→Задания sees exact updated description. Same synthetic account different role, not separate person.
- Login/role switch STUDENT→HEADMAN→STUDENT and role/session restoration after reload worked.
Roster formats5/currentstudents loaded; DOCX UI says passed to browser. CUA download wait exceeded30s/resetkernel; actual saved file UNCONFIRMED. Do not repeat identical wait or call server broken from this alone.
Evidence: .agent/evidence/mobile-functional-acceptance-20261002/browser-observation.md and runtime-r2/results.json. Two of three bounded flows accepted, not whole-app readiness percentage.

## Concrete remaining blockers / next work
Student Today reproducibly showed «Attendance Service временно недоступен» on login and return to STUDENT; did not block homework. Need bounded diagnosis actual MobileAttendanceClient request/code; no blanket rebuild/retest.
R1 Academic bootstrap503 did not recur in R2; safe logger caught0 errors. CauseUNKNOWN, do not claim fixed by logger. Watch when reproducing actual dependency failures, no blind repeat.
Roster physical download confirmation remains; TMA actual bot/initData/provider delivery not tested. No Figma changes in this stage.

## Build/runtime lease
Runtimeholder .agent/worktrees/v2-runtime-build-r2 exacta93f4265; clean after build. BFF-only42119exit0/35s, other7JAR/PWA10/TMA6 hashes unchanged. Manifest runtime-r2/build-manifest.json SHA43d733ce63b444ef9674e643e081928adf563a31d8ed0514367ff235f2d4013d. BFF SHA1e1f5774fbbb2429e673d1c48bc4b60e39a40f1ddd4e252c5c72e71ce126b008.
R1 handle34823 exit1 intentionalUNACCEPTED; all14ownedcontainers/network/Java absent cleanupPASS, preserved evidence commitcce2ce24.
R2 owner e_homework_date_bot_1002, actualhandle18540, run20261002-205620261-kmyrcsnj. RootUI finished; same18540 terminal712b65 exit0, c23c94 cleanupPASS:14ownedcontainers/network/keys absent, JavaEMPTY, holderclean. Heavy released. WholeappNOT_ACCEPTED; boundedPASS only forONE_OFF/DATE. Owner polls same handle, no restart on missing root session. Evidence sole writer E runtime-r2; root browser-observation/CURRENT/SLOTS/metrics.
TLS mkcert existing3paths, browser opens withoutwarning; no trust changes. Public tracked synthetic fixture credential used, no realprovidersecrets. CUA IAB2/tab1 retained; aftercompaction rewriteDocumentation.
New seed via API only: group1/student3/teacher2 reused; semester2 dateSep19..Nov28, subject1, assignment1. Data disposable owned stand only. No production changes.

## Established procedure
One shared stand/heavy lease, parallel frontend/service small environments only when useful. KnownGradleJDK21 ms-21.0.10, packagingclasspath flag, no-daemon/no-parallel/maxworkers1. No fullsuite or loggerunitwiring tests. Fewer evidence files: results.json+summary plus mandatory outputs sufficient. All product authors/reviewers idle except current runtime cleanup owner.
Prior backend accepted scope in BACKEND-STAGE-20261001.md; durableprefs81848929, botpending069d0e0c, ONE_OFF0a4e982d, recoveryb1db7f28, runbook770b34c6/offsideb7fa9059. No repeated unchanged acceptance. Native recovery stopped containers/volumes retained, never remove as generic cleanup.

