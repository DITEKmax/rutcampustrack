# IN_PROGRESS — объединённая цель 17–18.10.2026

После resume читать [TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md),
[async-дополнение](TASK-ADDENDUM-ASYNC-20261017.md), этот файл и [resources](resources.md).
Q031/D037: TASK-CONTRACT/CURRENT — принятая сохранённая цель; отдельный platform Goal
не имитируется. [Матрица v1](selected-results-20261018.csv)/[веса](selected-results-20261018.meta.json),
[решения](decisions.md), [вопросы](questions.md), [evidence](attempts.ndjson),
[единственный рабочий HANDOFF](HANDOFF-20261017.md). Историю заново не исследовать.

## Revision и приёмка

Ветка codex/server-validation-20261011, [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2),
main не merged. Published local=remote=PR head **9a61873f5e3dc12eccce92a436b39fd38506306d**.
Stage17 base753173688d633ca07991f4dd73bdcd2698f45d96; scope18 received base
e66b1711b3d93faee625b50a2e2f785020a0f43a. Stage diff262paths:53A/209M/0D.
Root WIP: независимо принятые 10 файлов U22 Alloy + текущие task docs, не runtime PASS.

Матрица **v1-КТ1 FROZEN: 8/25, вес38/123 =30.89% плана**, не production readiness.
Веса/состав не изменены. Условные integrations UNSELECTED отдельно от принятой оценки U23.
Прежние **6/9** сохраняются отдельно: DONE_CHANGED6, ALREADY_SATISFIED0.

| ID | Статус | Доказательство / конкретный остаток |
| --- | --- | --- |
| A01 | DONE_CHANGED | Совместимый BCrypt/DI/credential lifecycle, realVue admin-create→login;12114507172/12114133029 +review. |
| A02 | DONE_CHANGED | Текстовые enum/JPA/JDBC/JPQL/REST/gRPC unknown/null/legacy;12114232912 +review, wire frozen. |
| A03 | DONE_CHANGED | DTO redaction/RS256/безопасные CI artifacts;Auth145unit+200IT0skip12114133029 +review. |
| A04 | DONE_CHANGED | RealSQL страницы/order/currentStudent:subject2/HW1 batch enrichment;12114232912 +review. |
| M01 | DONE_CHANGED | Gateway→BFF→TCPgRPC24spans;Rabbit retry/dedup10spans→Tempo/Loki/Grafana, privacy/context;12114439186 +review. |
| M02 | IN_PROGRESS | Stores/exporters/8apps/resource signals приняты; нужны actual report/job deltas после двух правильных DOCX. |
| M03 | IN_PROGRESS | Resource/30d queries/logs↔traces приняты; нужны retained JVM/Hikari/Tomcat/gRPC/Auth/report panel assertions после DOCX. |
| M04 | IN_PROGRESS | Broker8cases/receiver/samearchive DLQ-replay PASS. Наe66 first18weekDOCX correct, second lost202/reconnect ещё не принят; current rights/runtime pending9a. |
| M05 | DONE_CHANGED | e66 actual2PG/Mongo/Redis rights+6NOPERM/exporters/Rabbit/pipeline/privateaccess/recreate;12118842862/SHAfb1a7985… +independent review. |

C01 actualPG oldstaleRED→afterCommit commit/rollback2GREEN12113673595;
C02 опубликованные93migrations/28controls PASS; C03 minimal runtime/migrator/monitor
права приняты M05. Это не новый U14 fact ownership. М05 не переоткрывать без причины.

## Следующие действия

1. Текущий9a pushCI39082996242/PRCI39083002674/Coverage39083002654 выполняются;
   Security39083002628/OpenAPI39083002852 SUCCESS. Нужны второй DOCX/current rights
   и job/panel evidence. Reconnect3d631113 независимо принят: session200 + точный старый
   DOM отсоединён + новый home, затем журнал. Исторический timingUNKNOWN.
2. U22 source10paths прошёл review755dbf7a без findings и точно перенесён Root;
   обычный coherent push должен дать actual Alloy1.20.1 validate/pipeline/recreate/
   delivery/drop/privacy/resources. Source-only не закрывает U22.
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
CPU5/RAM16GiB/workspace floor4GiB. TWO независимых heavy lease: Auth CPU0 и Data CPU1, offlineJDK21,
maxworkers1/fork1, каждый≤2.5GiBRSS/512MiBnewTMP; разные caches/outputs, безDB/Redis.
ReviewerReports U22 source принят; ReviewerAuth U14 amended design принят, не runtime.
Продуктовых вопросов владельцу сейчас нет; технические gaps не превращать в новые требования.

D032–D037 действуют: WORD старосты, исходный syncHTTP crashUNKNOWN; SLO инженерные цели,
PG durable job/session authority/current grants сохранены. DEFERRED_LOCAL: будущий alert-бот,
offhost probe при deploy, realTelegram/push/install/Safari и прежние manual ограничения.
B013/B014/B015 FROZEN; no обходов. No main/merge/deploy/production grants/reset/delete
volumes/secrets/Angular/newweb/редизайн/projectdocs refactor/secondCLI.
