# IN_PROGRESS — объединённая цель 17–18.10.2026

После resume читать [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[async-дополнение](TASK-ADDENDUM-ASYNC-20261017.md), этот файл и [resources](resources.md).
Q031/D037: TASK-CONTRACT/CURRENT — принятая сохранённая цель; отдельный platform Goal
не имитируется. [Матрица v1](selected-results-20261018.csv)/[веса](selected-results-20261018.meta.json),
[решения](decisions.md), [вопросы](questions.md), [evidence](attempts.ndjson),
[единственный рабочий HANDOFF](HANDOFF-20261017.md). Историю заново не исследовать.

## Revision и приёмка

Ветка codex/server-validation-20261011, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2),
main не merged. Published local=remote=PR head **3dc25ce7bddaded5f046233db39f732c05c382ea**.
Stage17 base753173688d633ca07991f4dd73bdcd2698f45d96; scope18 received base
e66b1711b3d93faee625b50a2e2f785020a0f43a. Stage diff265paths:54A/210M/1D.
U22 Alloy source10 опубликован3dc25ce7; независимое review755dbf7a принято, runtime pending.
Root integration: 54exactsource — U16cache/2Redis16 + API22/Python14 + U13Gateway/helper2
(CI общий). Source/review приняты; реальные2Redis/PGTLS/6OpenAPI/Gateway checks pending.
Чужие25untracked нетронуты.

Матрица **v1-КТ1 FROZEN: 12/25, вес61/123 =49.59% плана**, не production readiness.
Веса/состав не изменены. Условные integrations UNSELECTED отдельно от принятой оценки U23.
Прежние **9/9** функционально закрыты: DONE_CHANGED9, ALREADY_SATISFIED0;
это не завершение объединённой цели: final gates/U25 и расширенный scope остаются.

| ID | Статус | Доказательство / конкретный остаток |
| --- | --- | --- |
| A01 | DONE_CHANGED | Совместимый BCrypt/DI/credential lifecycle, realVue admin-create→login;12114507172/12114133029 +review. |
| A02 | DONE_CHANGED | Текстовые enum/JPA/JDBC/JPQL/REST/gRPC unknown/null/legacy;12114232912 +review, wire frozen. |
| A03 | DONE_CHANGED | DTO redaction/RS256/безопасные CI artifacts;Auth145unit+200IT0skip12114133029 +review. |
| A04 | DONE_CHANGED | RealSQL страницы/order/currentStudent:subject2/HW1 batch enrichment;12114232912 +review. |
| M01 | DONE_CHANGED | Gateway→BFF→TCPgRPC24spans;Rabbit retry/dedup10spans→Tempo/Loki/Grafana, privacy/context;12114439186 +review. |
| M02 | DONE_CHANGED | На9a actual2DOCX дали точные+2job/reportdeltas,8apps/3117series;12120205958+review85f1ca36. |
| M03 | DONE_CHANGED | Все426livepanelqueries/правдивыеidle/noevent/недостаточные30d + прежниеlogs↔traces;12120205958+review. |
| M04 | DONE_CHANGED | На9a обаcorrect18weekDOCX/navigation/lost202/reconnect/currentrights PASS12210ms + применимыеPG/broker/recovery proofs;review85f1ca36. |
| M05 | DONE_CHANGED | e66 actual2PG/Mongo/Redis rights+6NOPERM/exporters/Rabbit/pipeline/privateaccess/recreate;12118842862/SHAfb1a7985… +independent review. |

C01 actualPG oldstaleRED→afterCommit commit/rollback2GREEN12113673595;
C02 опубликованные93migrations/28controls PASS; C03 minimal runtime/migrator/monitor
права приняты M05. Это не новый U14 fact ownership. М05 не переоткрывать без причины.

## Следующие действия

1. СтарыеM02–M04/U07–09 приняты на9a: actual12120205958/SHAс9feaf41,
   checkout12119293739/treec51c8504, independent85f1ca36. Не повторять без причины.
   9a pushCI39082996242 завершён24/24SUCCESS, включая reconciliation/lifecycle и
   Playwright. Прежниеe66UNKNOWN сохранены; новый outcome их не переписывает.
2. Current3dc25ce7: pushCI39084041118/PRCI39084046254, Coverage39084046250,
   Security39084046264/OpenAPI39084046258 SUCCESS. Monitoring push/PR jobs SUCCESS;
   actualAlloy safeartifact/applicability review выполняется. U22 пока REVIEW, не PASS.
3. Параллельные bounded пакеты: Auth U11–13/U18 и собственный bot read RPC; Reports
   U20 меню/consumer, затем files/notifications/oneHAL/k6; Infra U16 Redis после U22;
   Data U16 cache Java, затем U14/U15 по независимо принятому плану одной PG transaction.
   Shared API/generated у Auth, configs/CI у Infra, новые migrations/owner commands у Data.
4. По готовности: checks→независимый review по риску→Root integration/push; final U25
   включает актуальные gates, применимость evidence и handoff с exact executable/docs SHA.

На предыдущемe66 CI21success/3failure: metadata UI отказал на защитном timeguard513
(предусловие fixture/environment); reconciliation previewOTHER доHTTP — причинаUNKNOWN
12119526328, cleanup/replay/restoration PASS. Ordinaryarchive/restore12119557045 PASS.
Барьеры не ослаблены, прошлый f8 samearchivePASS12118602956 сохранён отдельно.

## Owners, ресурсы и границы

Root soleGit/task docs. Новые detached sparse WT /tmp/rct-s18-{auth,infra,reports,data}
на9a; старые753WT/WIP,25чужих untracked, stand/volumes/keys сохранены.
Дети Sol6.1/high/forknone; actualUNKNOWN — действующее Cloud-исключение. Capacity7inclRoot,
CPU5/RAM16GiB/workspace floor4GiB. Разрешено до TWO независимых heavy lease с известным запасом; Auth compile20tests
закончил/reaped, Data cachecheck03PASS17focused+4guards; теперь U14/U15. Maxworkers1/fork1, каждый≤2.5GiBRSS/
512MiBnewTMP, разные caches/outputs, безDB/Redis. Актуальные leases/resources.md.
ReviewerReports U13helper541c239a принят, API7f363eba/cache8b09dee4 приняты.
ReviewerAuth проверяетactualAlloySAFE; Data+Infra реализуют U14/U15, не runtimePASS.
Продуктовых вопросов владельцу сейчас нет; технические gaps не превращать в новые требования.

D032–D037 действуют: WORD старосты, исходный syncHTTP crashUNKNOWN; SLO инженерные цели,
PG durable job/session authority/current grants сохранены. DEFERRED_LOCAL: будущий alert-бот,
offhost probe при deploy, realTelegram/push/install/Safari и прежние manual ограничения.
B013/B014/B015 FROZEN; no обходов. No main/merge/deploy/production grants/reset/delete
volumes/secrets/Angular/newweb/редизайн/projectdocs refactor/secondCLI.

U19 ALREADY_SATISFIED на9a:64IT/127unit+realPWA4+existing3Vitest actualPASS;
independent3f068303, no rewrittenproductlogic. RealTG/WebPushDEFERRED_OWNER.
