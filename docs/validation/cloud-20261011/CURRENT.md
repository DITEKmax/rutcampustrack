# IN_PROGRESS — объединённая цель 17–18.10.2026

Восстановление: [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[async](TASK-ADDENDUM-ASYNC-20261017.md), этот файл и [resources](resources.md).
Q031/D037: TASK-CONTRACT/CURRENT — принятая сохранённая цель ACTIVE; отдельного API
platform Goal нет. [Матрица](selected-results-20261018.csv)/[веса](selected-results-20261018.meta.json),
[решения](decisions.md), [вопросы](questions.md), [evidence](attempts.ndjson),
[единственный HANDOFF](HANDOFF-20261017.md). Историю заново не исследовать.

## Revision / критерии

codex/server-validation-20261011, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2),
main не merged. Последний подтверждённый local/remote/PR head **f2f8046a03ac3198c1bfa405a10606ad98b5678d**.
На62c опубликован HAL producer/consumer+browser, restore-file IT, четыре canonical
OpenAPI+AuthTS, Notification native identity/CI pin и два k6 scripts/portable40controls.
Наf2 опубликован пакет относительно62c: U14 ownership63 source +8contract/diagnostic/CI
paths =71, taskdocs6; reviewer1f9f21eb и адресные2d54d0a2/37f0ffd0 приняты.
Его новая исполняемая revision определяется коммитом пакета, а не старым runtime903.
Stage17 base753173688d633ca07991f4dd73bdcd2698f45d96; received18 basee66b1711b3d93faee625b50a2e2f785020a0f43a.
Чужие25untracked, старыеWT/WIP/stand/volumes/keys сохранены.

Матрица **v1-КТ1 FROZEN: 21/25, вес97/123=78.86% принятого плана**, DONE_CHANGED18,
ALREADY_SATISFIED3. Не production readiness. Состав/веса не менялись.
U16 принят на903: actual2Redis/PG/cachefallback/ACL/caps/exporters +independent7b580917.
U20 принят на903: actualPG→TLS→Python TODAY/WEEK/currentroles/menu +independentdfed6ec6.
Новый U14 initial-password/Auth transport этими результатами не проверен.
U19/U22 прежние actual9a/3dc notification/Alloy proof применимы, не переоткрывать.
U12 DONE_CHANGED@62c:200IT/148unit/генератор+TS PASS/review9f691350;
U13 ожидает all6canonical comparison; U17 DONE_CHANGED на62c: actual248IT/
exact855fac4b restorefilebytes+grantdeny PASS/reviewe11ae909;
U21 DONE_CHANGED@62c: actual4/4browser/size1+HALnextGET200, reviewf104287a; fivefiles unchanged throughf2. U24 DONE_CHANGED: actualportable40controls
и независимое reviewbe3a402a/f5025c2a; k6/HTTP/load NOT_RUN, capacity не заявляется.
U14/U15 и итоговый U25 открыты. Продуктовых вопросов владельцу сейчас нет.

Прежние **9/9 функционально DONE_CHANGED9, ALREADY_SATISFIED0**, это не завершение
расширенной цели. Исторические6/9 сохранены в контракте/истории.

| ID | Статус | Достаточное evidence |
| --- | --- | --- |
| A01 | DONE_CHANGED | BCrypt/DI/credential lifecycle +realVue create→login12114507172/12114133029/review. |
| A02 | DONE_CHANGED | Текстовые enum/JPA/JDBC/REST/gRPC unknown/null12114232912/review; wire frozen. |
| A03 | DONE_CHANGED | Redaction/RS256/CI privacy,145unit+200IT12114133029/review. |
| A04 | DONE_CHANGED | RealSQL currentpage/order/student scope: subject2/HW1 enrichment12114232912/review. |
| M01 | DONE_CHANGED | Gateway→BFF→gRPC24spans; Rabbit retry/dedup10spans→Tempo/Loki12114439186/review. |
| M02 | DONE_CHANGED | 9a2DOCX exact+2job/reportdeltas/8apps/3117series12120205958/review85f1ca36. |
| M03 | DONE_CHANGED | 426livequeries/idle/noevent/insufficient30d +logs↔traces12120205958/review. |
| M04 | DONE_CHANGED | 9a correct2x18weekDOCX/navigation/lost202/reconnect/currentrights +PG/broker/recovery proofs/review85f1ca36. |
| M05 | DONE_CHANGED | e66PG/Mongo/Redis rights/exporters/Rabbit/privateaccess/recreate12118842862/review. |

C01 actualPG oldstaleRED→afterCommit commit/rollback2GREEN12113673595;
C02 published93migrations/28controlsPASS; C03 runtime/migrator/monitor rights M05.
Это не новое владение фактами U14. M05 без новой причины не повторять.

## Ближайший исполнимый остаток

1. Root опубликовал62c:16source+5docs/scopedhooks6PASS; local=remote=PRhead.
   Новый пакетf2:71source+6docs опубликован, scopedhooks6PASS;
   PRCI39089909475: Auth/Academic failed SOURCE_ADMISSION доordinaryIT; Infra проверяет
   stale sourcepins/restoration: coherent19scalar fix6669f96d approved1f895e43,
   Root integratedONECIpath/preparingnormalpublish. Coverage39089909428 running; Security39089909436 и
   OpenAPI39089909433 SUCCESS. Vue/Python/NotificationIT/BFFIT/GatewayIT SUCCESS.
   Новый U14 actualruntime/финальные gates ещё pending.
   U21 assembler2PASS, client same-testRED2FAIL→GREEN25PASS/0skip/typechecks/review682d4610;
   U17 compile94s/review0352fe25. Actual newpaginatedbrowser пока NOT_RUN; новый MongoU17 теперь actualPASS.
   Четыре canonical взяты из strictnative actual903 candidates; BFF74ITPASS unchanged.
   Notification только DisplayName removal +exactsourcepin: нужен НОВЫЙ SOURCE_METHOD,
   старыйORDINAL_ONLY не повышать в статусе. Новый62c PR+push доказал exactSOURCE_METHOD
   4a4f/frame93/63PASS+1soleFAIL; officialcandidates identicald0933929. Canonical
   Notification теперь обновлён этими байтами; matchingcomparison ждётnormalCI.
   Gates/assertions не ослаблены.
2. На903 Security39085664150/OpenAPI39085664187 SUCCESS; CI39085664270 и
   Coverage39085664148 FAIL. Четыре snapshot drift установлены; Notification identity
   отдельно, lifecyclebrowser failures адресно изучает ReviewerReports. PushCI39085658708
   учитывается отдельно. Auth148unit/BFF74IT PASS; Gateway11PASS/3existingSKIP не PASS skips.
3. U14: Data41source NEWV62/closedSQL/minimalroles/atomicPG create +realORM2operations;
   reviewR2–5 исправлены, compile02PASS77s, реальныйPG/upgrade NOT_RUN.
   R1 после исправления: чтение initialpassword только AuthprivateTLS с отдельным Bot→Auth
   credential; Academic старый read закрыт, manage/HW сохранены. Auth7/Python7/Infra8
   приняты independent1f9f21eb: R1–5 и generatorhex/base64url закрыты по исходникам.
   SQL authority не объявлять независимой проверкой JWT/полной изоляцией при компрометации сервиса.
4. U14f2 обычныйCI fresh/upgrade/ORM/TLS/broker/restart вочереди.
   62c PRCI39088416035/Coverage39088416031/Security39088416045 queued;
   OpenAPI39088416017 SUCCESS. 903 async/7Gateway PASS; transferPENDING timeout,
   reconciliationpreviewOTHER/cleanupFAILED и NotificationCoverage35non-snapshotFAIL
   остаются causeUNKNOWN; Новое минимальное наблюдение подготовлено: finiteNotificationcausecodes и
   original/firstcleanup browsercodes/status, privacy/passcontrols приняты.
   ПричинастарогоFAILостаётсяUNKNOWN, без blindrerun/таймаутослабления.
   62c browser: D002 REFRESH_UNOBSERVED/sessiontimeout и role ADMIN_UI FAIL causeUNKNOWN;
   U21 ownHWcase PASS независимо. Reports предложил только finite request/substage witness.
   U25 finalchangedriskreview+актуальные CI/Coverage/Security/OpenAPI+exactSHA handoff.

## Owners / ресурсы / ограничения

Root soleGit/taskdocs. DetachedWT /tmp/rct-s18-{auth,infra,reports,data}, их base9a,
readonly crossinputs только явные hashguarded. Auth — API/generated/AuthTLS+2Java bridges;
Data — V62/commands/entities/repositories/ORM/principals; Infra — config/CI/bootstrap;
Reports — Python7 и files/HAL tests; reviewers независимы.
Sol6.1/high/forknone, actualUNKNOWN — принятое Cloud-исключение. Nativecapacity7inclRoot.
CPU0 AuthGradlehome/CPU1 DataGradlehome раздельны; compile09/02 PASS/reaped, no runtime.
Максимум TWO heavy только при реальной вместимости; workers1/fork1/≤2.5GiBRSS,
≤512MiBnewTMP/floor4GiB. Лёгкие no-network проверки отдельно. Ресурсы вresources.md.

D032–D037 действуют. DEFERRED_OWNER: realTelegram/push/install/Safari, alert-бот,
offhostprobe при deploy и прежняя manual приёмка. Исходный syncDOCX crashcause UNKNOWN.
B013/B014/B015 FROZEN, no equivalents. No main/merge/deploy/production grants/reset/delete
volumes/secrets/Angular/newweb/редизайн/projectdocs refactor/secondCLI.
