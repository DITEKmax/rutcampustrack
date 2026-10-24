# PROGRESS — Vue web redesign

Существующий draft [PR3](https://github.com/DITEKmax/rutcampustrack/pull/3),
ветка codex/web-vue-redesign. Goal запущен после readiness, канонGOAL-CONTRACT.
Срез2026-10-24 05:44UTC. NativeGEOsource1aea; текущийUIlabel43b7; shippingimages
sourcea813 и их узкие receipts сохраняют собственные revisions. Fetch actual
remoteHEAD, не откатывать ветку к сохранённомуSHA.

**Implemented115/117 (98,29%). Locally verified111/117 (94,87%).**
Исходные117active неизменны. Кадры/темы/ширины/assertions не являются действиями.

| Роль | Active | Implemented | Locally verified |
|---|---:|---:|---:|
| AUTH |6|6/6 (100%)|6/6 (100%)|
| SHARED |15|15/15 (100%)|15/15 (100%)|
| STUDENT |16|16/16 (100%)|16/16 (100%)|
| HEADMAN |38|38/38 (100%)|38/38 (100%)|
| TEACHER |8|8/8 (100%)|8/8 (100%)|
| ADMIN |34|32/34 (94,12%)|28/34 (82,35%)|

Новый законченный сценарий: normalUI ownSTUDENT15, ACTIVE64, ONEPOST200→
PRESENT/STUDENT_GEO, authoritativeToday/attendance. Независимый DBpostguard:
64count0→1 ONLY15, foreign64marks0, protected53/own12+15dochashes/occ45r1/scope/
geofence неизменны, main13samehealthy. Nativebrowserprovider явноSIMULATED на
реальнонастроенную серверную точку кампуса; физическое присутствие не заявлено.
Ownsessionlogout204/context/browser закрыты. [Acceptance/receipts](../../validation/web-vue-pr3/checkpoints/native-geo-20261024/acceptance.json).
Одна подпись незавершённого периода исправлена43b7; отдельныйreadonlyrender
безstudentPOST подтверждает новуюUI. Types/ESLint/diff0, Rootindependentreview.

Ранее приняты nativeLate53→Headman12APPROVE→PRESENT/late_checkin и реальные
STOMP/REST изменения двух вкладок безrefresh/focusloss; current-viewdelegated
revoke, create/exportunknown-resultrecovery, helperCAS/names-onlyarchive lookup,
настоящиеPDF/HTML/XLSX черезRenderer/LibreOffice. [Roleacceptance](ROLE-ACCEPTANCE-20261024.md),
[117-action map](SCREEN-ACTION-MAP.csv), [safereceipts](../../validation/web-vue-pr3/checkpoints/roles-20261024/manifest.json).

Остаются6ADMINgates: accesswrite, PROMOTEexecution, semesterACTIVATE,
FULL/self-passwordunknown, wholemapfinaldelete, groupfinaldelete. Read/preview/
scheduling/cancel не засчитываются как положительные записи этихgates.
QPROD004 ждёт membership/grantpolicy; QENV003 exactown8securitycase, QENV004
scopedmapchecks/JAR и QENV0063historicalPROMOTEinputs ждут явныхответов после
automaticapprovaldenial. Команды не начались/не обойдены. PROMOTEpreview409:
completedSPRINGnull, legacy1typeNULL сохранён, SQLattempts0. [Точные вопросы](OPEN-QUESTIONS.md).
QENV005stop/privateDBsnapshot больше не требуется: naturalGEO выполнен безнего,
разрешение не получено, snapshotFALSE. Ownclock5infrahealthyidle; apps/14250/
dump/restore никогда не запускались. Main13 не останавливались.

Глобальная замена ещё не завершена: активныйAngular остаётся до замены
возможностей; outeredge/prodsource/legacycontrols/finalcombineddeliverygates
pending. Vuee2erootedge и строгийservedVueoracle source-reviewed; config0 и
8harnesscases PASS, неouterruntime. Четыреbrowserfixtures0d30/6ff исправлены
после2P2 иnarrowreviewPASS; дальнейший9filee7bbpacket наreview. Browserproof
дляэтихmigrationpackets не заявлен. [Shipping](SHIPPING-CHECKPOINT-20261024.md),
[Angular/WebPushinventory](ANGULAR-REMOVAL-INVENTORY.md).

[Runtimeгалереи](../../validation/web-vue-pr3/runtime-gallery/index.html) содержат
201originalframes: прежние200 сточнымиrevision/limits +текущийGEOreadonlyкадр.
[PortableHTML](../../validation/web-vue-pr3/runtime-gallery/PORTABLE.md) фиксирует
свойimagepin/count; обновляетсяпослепубликацииPNG. Reference/Figmaотдельно.
Mobile24sourcefamilies/140retainedsamples, narrow12P2recheckPASS; первыйhelper
cleanupExit1 отдельно, signedTelegram/externalPushdelivery не заявлены.

Main13healthy: Authf3/Academic950/Gateway3c02 иqualified603Schedule/Attendance/
BFF/Notification/Renderer+инфраструктура. ZIP87568617bytes/SHA/CRC/safepaths/
422entries388PNG+34 полностьюпроверен. MCP/ghAPI/Git доступны независимо.
FigmaMCPcontext/screenshot/originalauthbytes работают; actualpolicy7stilllacks
www.figma.com/CONNECT403 послеобещаниявладельца, повторногоразрешениянетребуется.
Триimagesa813/innerstaticWEB12-PWA6-TMA4 PASS — историческийexactsource, не новая
сборка43b7. VFSdiskfull/30ownedunusedcache recovery/первоначальныйPNG403 сохранены
вshippingreceipt; mainCIDs/images/mountsets/volumes/foreigncache не потеряны.

CI/Coverage/Security/OpenAPI **PAUSED**, workflow_dispatch, неPASS. Без main
writes/merge/deploy/production/firewall/globalDockercleanup. ExistingPWAWebPush
сохранён, webchannels исключены, studentownexport отложен, usersarchive/restore
безfinaldelete, teacherassignedgroup AND subject/currentgrants сохранены.
