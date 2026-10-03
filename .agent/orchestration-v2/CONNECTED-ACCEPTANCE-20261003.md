# Подключения Telegram / WebPush — общий критерий приёмки

GO владельца 2026-10-03. Продуктовая база a7676b2f, main b2853288. Секрет тестового бота хранится вне Git; в документ не включается. Назначения Sol6.1 high. Риск S3 для внешнего входа/прав/изоляции владельца. Один config/runtime writer, один общий стенд; неизменённые JAR и PWA/TMA сборки переиспользуются.

## Известное принятое

Адресная сверка backend_a_d_closure_1003 не выявила нового server-only дефекта в сохранённых критериях А–Д. Основание: BACKEND-STAGE-20261001.md, checkpoints/2026-10-03-stop-after-a767/REPORT.md и исходное evidence. Это не новая полная приёмка всех историй и не повышение смешанных процентов.

## Остаток этой связки

| Критерий | Наблюдаемый результат | Статус |
|---|---|---|
| Bot identity | Предоставленный токен соответствует указанному тестовому боту; webhook отсутствует | PASS getMe/getWebhookInfo, без вывода секрета |
| Bot startup | Единственный polling consumer отвечает владельцу на /start через настоящий Academic | PASS: exact owned bot health200, владелец получил welcome student/group через Academic |
| Account linkage | Привязка через разрешённый продуктовый API | PASS: exact private owner nonce, admin PATCH student3 и GET readback; Telegram ID только вне Git |
| TMA auth | Настоящий Telegram initData открывает только связанный аккаунт, данные сохраняются | Пока не принято: actual public502 до Nginx; TLS correction765/main172e1aac и source review PASS; actual malformed request теперь Auth400, настоящий reopen ещё не выполнен |
| Telegram delivery | Разрешённое серверное действие создаёт событие; пользователь фактически получает соответствующее сообщение | PASS bounded: admin API group1 rename ТСТ-211 и ТСП-211, обе разные receipt подтверждены владельцем; это не все типы событий |
| WebPush provider | AES128GCM; HTTP 2xx учитывается как принятие провайдером, 404/410 удаляет истёкшую подписку, отказы не обновляют last_seen | Код 178c9c6e/main ed56a8e7; source review и адресные crypto/status checks PASS; реальная доставка через браузерный provider подтверждена отдельным системным уведомлением владельца |
| Browser delivery | Production PWA подписывается через браузер; доменное событие доставлено настоящим push provider и показано браузером | PASS bounded: owner Chrome/Edge production PWA subscription enabled + ТСП-211 отдельное системное уведомление явно подтверждено владельцем; синтетический SW event не использовался |
| Owner boundary | Выход снимает привязку; следующему аккаунту не показываются чужие уведомления | Реальный путь этой связки ещё не принят; неизменённые прошлые проверки не повторять без причины |

WebPush остаётся best effort по принятому D08: новый код не обещает durable retry или exactly once на внешнем провайдере. Telegram provider delivery также не объявляется exactly once.

Обновление source/checks: WebPush mained56, адресные checks PASS; Telegram main0be9/b4fa, independent affected recheck PASS, Node5 + Java14 handshake checks PASS. Исправлены первоначальные findings временного edge: правильный SockJS protocol, header-ticket без credential query в Nginx, guard всего Git root. Telegram и системный WebPush имеют реальные receipt владельца; настоящий TMA login ещё не принят. Recovery helper main9605 и independent affected recheck PASS; это не доказательство application recovery.

## Границы ресурсов

Первый общий стенд run 20261003-175332447-dxjaofba (source3412/manifest591ca2/runner592825) завершён по manual timeout, cleanup PASS: 15 собственных контейнеров удалены. Public edge закрыт; прежнее меню владельца восстановлено и проверено. Результаты фактической доставки сохранены. Native checkpoint не создан: после прямого разрешения владельца ownership guard уже обнаружил удалённый source, до чтения credentials, transfer POST или backup. Это ошибка планирования времени, не результат проверки восстановления.

Следующий bootstrap — frozen R2 fcd12e005ce93ee1d1c2d001148989ce15c1d7a7, manifest5a48edc5979836b903168f1e60c2905af30f10cc0f39e67d88e7e0e51834456d. Одна frontend build 28,3 с; восемь JAR и bot image переиспользованы. Public подключение пока отложено. R2 — sole config/app/network owner; R3 — native/fixture subset после actual READY и preservation ACK. Контроллер нового собственного запуска должен передать ресурсы до первой mutation; восстановление выполняется в ограниченном окне с явной cleanup responsibility.

Source backend JAR3412 не пересобирались. Edge TLS correction765/main172 отдельно от backend artifact identity: Agent servername localhost, CA/rejectUnauthorized сохранены; independent affected review PASS; actual Auth400 вместо502. Empty HAL page parser fix sourcecd8/main87abd1bd (2 files) принят по targeted existing test и включён в новую canonical frontend build; browser readback новой версии ещё впереди. Встроенный браузер permission pending не объявляется provider defect.

Ж: первый READY_FIXTURE и его immutable request/history сохранены в R3 evidence; живые данные того запуска удалены штатным cleanup. Original UUID bdbfe2d3-baae-4c17-bcef-ea8a746674cc не переносится в новую БД как будто операция уже отправлена. Новая fixture создаётся после preservation ACK, затем сразу pending POST → quiesce → native backup/capture вне Git. Direct human approval call_VYztQwtV3C4pCkULynCWDqxk получен для узких трёх generated DB credentials и собственного локального сценария. Whole environment export не разрешён и не выполнен.

До target switch согласуется фактическая Mongo конфигурация: source использует rs0 без root-auth bootstrap, существующий disposable restore compose — standalone с root-auth, приложения требуют rs0. Несоответствие нельзя скрыть ослаблением acceptance или считать восстановлением приложения после одного native restore. Fresh RabbitMQ также обязателен; прежний broker не выдаётся за восстановление потерянных очередей.

Полная клиентская приёмка/Figma, offsite/production и deploy остаются отдельно. После фактических receipt ориентир Е85→90; А95/Б95/В92/Г92/Д92/Ж55 без изменения. Это инженерные ориентиры ±5–10 п.п., не accepted/total production percentage и не повышение от числа тестов.

## Native checkpoint и восстановление source2

Run20261003-184420960-xp9jpqsx preserved доfixture; sourcecontroller stopped,9writers и oldRabbitMQ остановлены после одного transfer202/PENDING. Bundle/capture sealed19:09:16UTC внеGit:4470fd73b40fb513fd3d347e4ef1120d3fef4ae8b3e23c9be0036a5088434586 /3c63cce8dfa46b7580395283c4aec7859271e488127834804c5e199944bde3d4. Originaloperation00ab2cf8-8ff7-4064-b177-28287c9c11d8, requestb8134109-35e4-4d0e-bfc1-deaecbe28860; past/history/currenthomework frozen. Source originals retained; target3 labeled/tmpfs/noports. TargetMongo authenticatedrs0primaryPASS.

Originalnative restoreexit1 сохранён: targettimezoneUTC vs sourceEurope/Moscow и эквивалентное переформатирование schemaPG. Targetconnection timezone исправлена, без editsrows/source. Main4996ef72 добавляетTZEurope/Moscow двум PG environments test-restore profile. Main3a92af21/sourceb829 изменяет scripts/recovery.py: PG-native isolated guarded schema reference, exactrows/sequences/Mongo/files. Новая командаverify-restored --quiesced не повторяет архивы. StrictactualPASS19:45:07UTC,49,37s; alteredCHECKnegative отвергнут; scratchleftovers0. Independent source reviewPASS. ACK71ce85fba775a4ece1d88e09c9aa666647685c7b022e6fed87a5ee170a2d0162 явно VERIFIED_AFTER_CONFIG_CORRECTION, originalrestoreexit1/archiveReplayfalse сохранены. Это nativevalidationPASS, applicationterminal/exactreplay пока pending.

Boundedlease20:15UTC: R2 sole freshMQ/network/appstart, R3 sole existingapplicationhelper. FreshMQ created/healthy; emptyqueue CLIflagexit64 остановил connect доDB/apps switches. Partialledger сохранён; correction/resume отдельно по exactcreatedID, без второгоbroker/ослабленияfreshness. Source DBs/apps retained, nopublic. Владелец вернулся; следующий humanstep только после healthyactualcores и готового TMA origin/menu. Old/live rootJWT истекли19:14UTC: дальнейший401 не выдаётся за restore sessionrevocationproof.

## Принятое application recovery20:23:28UTC

R2 guardedconnect PASS9,43s; sixcore health PASS111s. Реальный route502 после restart диагностирован как stale Nginx GatewayIP140→137, exactownedreload вернулAuthpublickey200 с TLSverification. InitialFAIL/metadata сохранены. FreshMQ6b96c3e2… distinctfromsource; restoredtarget3 exactIDs из nativeACK. Notification64b тот жеID/image/env/signingidentity, stoppedIP136collision с BFF исправлена на availablefixed139 и aliases; actualhealth20:22:35PASS, proxyreload.

R3 firsthelper401 был ошибкой invocation: actorHEADMAN использовали также для STUDENThistory; sourcefixture требует отдельный STUDENT context. Actual actorstatus200COMPLETED и terminalsubsetPASS; readonlyhistoryHEADMAN401 подтвердил причину. NewSTUDENTlogin separateSID, HEADMANsession/file не изменялись. Первый helper завершился доexactPOST, failure сохранён. Corrected existinghelper PASS20:23:28UTC8,47s, exit0:

- Исходный operation00ab2cf8-8ff7-4064-b177-28287c9c11d8 после backup/потери oldbroker завершён реальными Academic/Attendance consumers.
- Originalpendingoutbox отправлен, immutableoperation/batches/canonicalgenerations и обе participantreceipts согласованы.
- ДЗ следует целевомузанятию, прошлыеотметки и StudentAPIhistory совпадают с sealedcheckpoint.
- Exact originalacceptedPOST выполнен ровноодинраз; operationresult/businessstate/schedule/history послеreplay неизменны, дублей нет.

Private application-result.json SHA3197d364c781b3b26b4129ee78fea06fe627406e8e5c3141ccc36ba45dae8c0e; correctedlog SHAec30a1e60ad1a7259ee87b87928951d711fb885ddf7f89b2ac7b20a47d7eff8a. Rows/credentials не копироватьвGit. NativeoriginalFAIL/strictlaterPASS сохранены, expiredJWT401 не считаются revocationproof.

Root knownowner→seedstudent3/group1 снова привязан API PATCH/GET, no rawTelegramID output; firstadmin502 записанотдельно. ActualPWAlogin и emptyHALhistory/settings UI теперьPASS, pwa-empty-history.jpg в .agent/evidence/telegram-recovery-retry-1003. GenuineTMAlogin покаpending; providerdeliveryreceipt не повторяется. Lease продлёнroot до20:45UTC, publicTTL<=1800/menurollbackowned. Sourceorig/backup retaineduntilcoherentcheckpoint.

Инженерный ориентир Ж55→70 за actualapplicationrecovery; А95/Б95/В92/Г92/Д92/Е90 безизменения. Общийfunctional≈90%, ориентир±5–10п.п.; offsite/RPO/RTO/production/deploy/Figma/полныйклиент остаются отдельно. Не вычислено по числу тестов и не означает productionready.

## Superseding 2026-10-03 20:50 UTC — TMA role bootstrap gap
Actual signed Telegram auth200 at20:41:59Z. Owner screenshot20:42:11Z shows RoleSwitch offline; public cleanup20:43:59Z cannot cause this earlier screenshot. Full student owner/role UI NOT accepted. Root and fresh R1 bounded developer independently opened sources: student bootstrap reads GET /api/academic/assistants/me/permissions, excluded by temporary test edge; 404 reaches fail-closed bootstrap offline. Edge also admits POST active-role but actual Auth/TMA contract uses PUT. Scope is temporary test gateway contract correction, not product auth bypass or Figma redesign. R2 sole writer two edge files; R1 read-only flow confirmation, no frontend changes/tests needed. Await actual sanitized route metadata; preserve distinction source cause vs runtime status evidence.
Public closed; owner menu restored/readbackMATCH, CF/edge/watchers stopped. Final sanitized ACK .agent/evidence/telegram-recovery-retry-1003/tma-final-ack.json SHADC1E0C68A5B28B24588BB11D40EBB30AEEBF4178652BE16467FDB3804649FC79. Local24 exact own resources + native backup retained in tma-final-local-resource-ledger.json. Local diagnostic/source lease extended21:15UTC; public permission20:45UTC EXPIRED, cannot reopen without new owner approval. Do not repeat accepted real WebPush/Telegram receipts or native/application recovery. Main3a92af21. Е remains90 until student bootstrap accepted; no full TMA readiness claim from auth200.

20:58 UTC: edge correction maina4816865 (sourceffd348106b11ed544151054e202df39c885c010d), scripts/telegram-test-edge.mjs +existing .test.mjs only,11insertions/1deletion. Exact ownpermissionsGET protected, rolePUT/POSTdeny; otheracademic/headman/admin/passwordroutes remainclosed. Existing6edgechecksPASS1,19s aftersandboxEACCES/approvedlocalretry; fresh independentSol6.1 reviewPASS, no repeats. ActualTMAbootstrapretryNOT_RUN; oldpublicclosed. New ownerpublicapprovalrequestedдо21:20UTC/00:20МСК4окт, NOANSWERyet. R2 prepares successorhelperpins/localledgerread-only until approval and rootGO; no build/restore/providerrepeat. Localpreparationlease21:25UTC, no backup/data cleanup. Rootwillnotcount auth200 as fullstudentbootstrap or raiseЕuntil bounded UIaccepted.

Owner direct approval call_PbtSV7JwJv7MJDUYDzOFCy3s received: «Да, разрешаю до 00:20 МСК», harddeadline2026-10-03T21:20:00Z (4Octlocal). New public window same explicit TelegramID/name/signedinitData +syntheticstudent scope; noadmin/passwordAPI/DB/token. R2 prepares successorhelpers with exactedgeSHA932254/newnames/currentexactGW6cacc49 andNotif9e2297; unchangedVue/JAR/DB/MQ/Auth reused. Beforelaunchrootaffectedreview required. Newactualpublic notstarted. OldconnectB56 hashmatchesreview, but lacksrunnerSHApreASTguard; prior summarybothguarded overstates—successor must explicitlyguard592 BEFORE ASTload. Maina4816865 sourcePASS, UIacceptancepending; noreadinessraise.

Actual newpublicstarted2026-10-03T21:05:32Z after independent successorreviewPASS A97/F29/F55 and directapprovalcall_PbtSV7JwJv7MJDUYDzOFCy3s. Rootsupervisor3956/startTicks639266583318245510; ownCF16424/edge25244, exactorigin https://duration-brad-recipe-acceptable.trycloudflare.com/mini-app/, harddeadline21:20Z/00:20МСК4Oct. Edge932/Vue6e9 no rebuild; menuNOTREADY until R2exacttwoGW/Notifcloneshealth/Nginxreload/routeprobe. Botuntouched. R2 solecontainerconnectionowner; root soleCF/menuexecution. Originaldata/nativebackup retained; no provider/recoveryrecheck. Public closes with shared close-public-bootstrap-retry.signal orharddeadline; menuBefore private successorrollback/restorationreadback required.

ActualbootstrapretryREADY21:08:43Z: newGatewayf32ab430330ec62e632a98662d7cf53fe5fb98dcca237f5f28296a54f56cb884/IP137, newNotification8531f6c7a0bbcf11f2a8ffb7d30506d03497ea944ebdbad8015f6e35ad42e5a1/IP139; retained2oldapps stopped/disconnected. Sameimages/envexceptCORS, Botuntouched. Nginx-t/reload/healthPASS; Vue200/emptyTMA400/permissionsGET+rolePUTwithoutBearer401/rolePOST+adminpassword404. public-route-probe-bootstrap-retry.json; sourceF29runtimepinchecked. RootmenuhelperF55PID28556/startTicks639266585651064079 actualREADY/privateBeforecaptured, rollbackarmed. Ownerrequestedstudent/schedule/profile/close-reopen (realUIpending), no fullHEADMAN/APIclaim. Maina481 inventoryproduct-files-main-a481.tsv still15uniquefiles (4A/11M), not count repeated modifications as newfiles.

## Финальный STOP 2026-10-04
Main a4816865. ActualstudentTMAbootstrap/profile/Today/HW/stats/attendance200,rolePUT200, ownerstudentworks; schedule/fullclose-reopennotconfirmed. Headmanpublic404 student-onlyedge; localPWAHeadmanToday/journalno404,fiveformats/weeksloaded,noactualdownload. Headerrolebutton coherentWIPe39786b0/7files/nav5PASS; typecheckunresolved, review/integration/UIafterGO.
Publicclosed21:13:35Z/menuBeforeMATCH21:13:34Z, exactpublicprocessremaining0check21:20:09Z. All8agentscompleted;9appsgracefullystopped21:30:15Z/all26containersretained/DB6+freshMQ+Redisrunning. Backup/capture/keys retainedoutsideGit. user-stop-final-ack.json SHADD7A6490196965969FEE3AD719A149AB5A8FBF4242C72D66588DDF293058AB3B.
Checkpoint checkpoints/2026-10-04-connected-stop/REPORT.md/FILES.tsv. Approximate mixedscope A95/B95/V92/G92/D92/E92/Zh70,functional90(range85-95),notproductionaccepted. Restore/providerpassednotrepeatwithoutnewrisk; frontend/Figma/fullmanual/productionseparate. No new tasks untilownerGO.
