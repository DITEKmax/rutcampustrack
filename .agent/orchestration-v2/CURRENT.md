# CURRENT — PRODUCT STOP 2026-10-04

Явный STOP владельца. До GO не запускать новые scopes/builds/checks/stands. Root не пишет продуктовый код. Новые задачи после GO — gpt-6.1-sol/high, fork_turns=none; отдельное runtime metadata недоступно. RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA. Main integration готового заранее разрешена, push не задан.

## Checkpoint
Main a4816865, baseline b2853288; 15 уникальных source/test/config/tooling файлов (4A/11M/0D). WebPush ed56a8e7, Telegram config0be9b4ab/ticketb4fa8bd8, recovery19efa354/af939a97/a7eb6112/9605cc00, emptyHAL87abd1bd, trustedTLS172e1aac, restorePGTZ4996ef72, nativecompare3a92af21, exacttestedge a4816865 интегрированы. Inventory и полный остаток: checkpoints/2026-10-04-connected-stop/REPORT.md и FILES.tsv.

Отдельный WIP e39786b0: branch codex/webpush-connected-1003, worktree .agent/worktrees/v2-runtime-build; 7 existing files, role buttons Student/Headman home и Back. Navigation5PASS/diff-checkPASS; typecheck unresolved sockjs-client/vite types окружения. Source-ready, trackedclean; review/typecheck/mainintegration/build/UI остаются после GO. НЕ объявлять кнопку исправленной на работающем стенде.

## Actual acceptance
Владелец подтвердил Telegram welcome + два разных переименования и отдельное системное WebPush. PWA пустая HAL-история исправлена и UI принята. Signed TMA login/student bootstrap, Today/HW/statistics/attendance/profile200, rolePUT200; студент работает по ответу владельца. Schedule и fullclose/reopen отдельно не подтверждены. Headman public404 — student-only test edge; local PWA Today/journal без404, fiveformats/weeks loaded; no filedownload. Полная TMA всех ролей не принята. ws-ticket409 рядом с200 при сменах роли наблюдался, причина не установлена.

Реальное application recovery20:23:28Z PASS8.47s: исходный pending transfer COMPLETED после freshDB/MQ, outboxsent3/participantreceipts2/HWhistory1/pastmark1/Studenthistory2; ONEexactreplay stable. Nativeverify19:45:07Z PASS49.37s +alteredCHECKnegative/scratch0, archivesnotreplay. Initialrestoreexit1(TZ/equivalentDDL) и helperHEADMANhistory401 доPOST сохранены как failures обвязки. ИстёкшийJWT не revocationproof, DBfixture не realobjectstore/offsiteproof.
ACK .agent/evidence/telegram-recovery-retry-1003/application-recovery-final-ack.json SHAafa9c2c083be8a699b0669f7f1ec21aa66a4655b34a4ed0606f7ccd49ae69b49.

## Actual STOP
Все8 child agents completed. 9 exact own apps/Bot/Nginx gracefully stopped21:30:15.109Z; все26containers retained, source/targetDB6 +freshRabbit/Redis running. ACK .agent/evidence/telegram-recovery-retry-1003/user-stop-final-ack.json SHADD7A6490196965969FEE3AD719A149AB5A8FBF4242C72D66588DDF293058AB3B. Нет cleanup/down/rm/prune/foreignkill/push. Все networks/keys/bundle retained.
Public/menu closed21:13:35Z, menuBefore readbackMATCH21:13:34Z, exactprocesscheck21:20:09Z remaining0. Publicdeadline21:20Z expired; неreopen без нового конкретного разрешения. Пользовательский browser оставлен.

Run20261003-184420960-xp9jpqsx; frozen sourcefcd12e005ce93ee1d1c2d001148989ce15c1d7a7; artifactmanifest5a48edc5979836b903168f1e60c2905af30f10cc0f39e67d88e7e0e51834456d. Privatebackup/capture C:/Users/maksd/AppData/Local/Temp/rct-application-recovery-1003-20261003-184420960-xp9jpqsx; manifestSHA4470fd73b40fb513fd3d347e4ef1120d3fef4ae8b3e23c9be0036a5088434586, captureSHA3c63cce8dfa46b7580395283c4aec7859271e488127834804c5e199944bde3d4.
DBtmpfs! Не останавливать; restartDocker потеряет liveDB, долговременный checkpoint — nativebundle/capture/code/artifacts. Secrets только внеGit, не читать/печатать. Scopedownharness credential leak ранее очищен6files/knownvaluescan0, rawenv/log/config не открывать/wholeenvexport запрещён. Чужие dirtyAGENTS/RULES/harness сохранены.

## Next after GO
1. e39786b0: fresh independent review +native typecheck в существующем полном окружении → scopedintegration → onefrontendbuild/boundedroleBackUI.
2. Долгоживущий локальный teststand с persistentvolumes/всеми ролями/сохранёнными подключениями; reuse8JAR/bundles. Не повторять принятые provider/restore без нового риска.
3. Отдельный frontend/Figma и полный локальный manual; полный TMA требует отдельного согласованного all-role connection, текущий testedge толькоstudent.
4. Offsite/RPO/RTO/productiondomain/secrets/alerts/load/migrations/deploy — отдельный этап владельца.
Ориентиры прежнего смешанного состава: А95/Б95/В92/Г92/Д92/Е92/Ж70; functional≈90%, диапазон85–95, неaccepted/total и неproductionready. Метрикиmetrics/packages.csv; unknowntime не выдумывать.
